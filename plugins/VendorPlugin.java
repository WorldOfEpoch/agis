package atavism.agis.plugins;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import atavism.msgsys.Message;
import atavism.msgsys.MessageTypeFilter;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.Hook;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.objects.Template;
import atavism.server.plugins.BillingClient;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.util.Log;
import atavism.server.util.Logger;

public class VendorPlugin extends EnginePlugin {
    private static HashMap<String, Integer> abilityList = new HashMap<String, Integer>();
    
    public VendorPlugin() {
        super("Vendor");
        setPluginType("Vendor");
    }
    
    private static final Logger log = new Logger("VendorPlugin");

    public void onActivate() {
        super.onActivate();
        //register message hooks
        registerHooks();
        //subscribe to vendor messages
        MessageTypeFilter filter = new MessageTypeFilter();
        filter.addType(VendorClient.MSG_TYPE_ITEM_PURCHASE);
        filter.addType(VendorClient.MSG_TYPE_VENDOR_GET_BALANCE);
        filter.addType(VendorClient.MSG_TYPE_VENDOR_INFO_REQ);
        Engine.getAgent().createSubscription(filter, this);

        this.registerPluginNamespace(VendorClient.NAMESPACE, new VendorSubObjectHook());

        if (Log.loggingDebug)
            log.debug("VendorPlugin activated");
    }

    public void registerHooks() {
        getHookManager().addHook(VendorClient.MSG_TYPE_ITEM_PURCHASE, new HandleItemPurchase()); 
        getHookManager().addHook(VendorClient.MSG_TYPE_VENDOR_GET_BALANCE, new HandleVendorBalanceRequest());
        getHookManager().addHook(VendorClient.MSG_TYPE_VENDOR_INFO_REQ, new HandleVendorInfoRequest());
    }
    
    public class VendorSubObjectHook extends GenerateSubObjectHook {
        public VendorSubObjectHook() { super(VendorPlugin.this); }

        public SubObjData generateSubObject(Template template, Namespace namespace, OID masterOid) {
            return createVendorSubObject(template, namespace, masterOid);
        }
    }
    
    public class HandleVendorBalanceRequest implements Hook {

        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage message = (ExtensionMessage) msg;
            //if (UseVirtualCurrency()){
                VendorClient.sendBalanceUpdate(message.getSubject(), 
                    BillingClient.getTokenBalance(message.getSubject()));
            //}else{
            //	VendorClient.sendBalanceUpdate(message.getSubject(), 
            //            AgisInventoryClient.GetCurrencyBalance(message.getSubject()));
            //}
            return true;
        }
        
    }
    
    public class HandleItemPurchase implements Hook {
        public boolean processMessage(Message msg, int flags){
            ExtensionMessage purchaseMsg = (ExtensionMessage)msg;
            
            String itemName = (String)purchaseMsg.getProperty("itemName");
            String itemType = purchaseMsg.getProperty("itemType").toString();
            Integer itemCount = 0;
            if (purchaseMsg.getProperty("itemCount") instanceof Integer) {
                itemCount = (Integer)purchaseMsg.getProperty("itemCount");
            } else {
                itemCount =  Integer.parseInt((String)purchaseMsg.getProperty("itemCount"));
            }
            
            if (itemType.equals("ability")) {
                purchaseAbility(purchaseMsg.getSubject(), itemName);               
            }
//            else{                
//                return PurchaseItems(purchaseMsg.getSubject(), purchaseMsg.getTarget(), itemName, itemCount);
//            }
            return true;
        }
    }
    
    public class HandleVendorInfoRequest implements Hook {
        public boolean processMessage(Message msg, int flags){
            ExtensionMessage infoMsg = (ExtensionMessage)msg;
            
            if(infoMsg.getProperty("itemType").equals("ability")){
                return VendorClient.sendVendorInfoMessage(infoMsg.getSubject(), 
                        getAbilities());
            }
//            else{
//                return VendorClient.SendVendorInfoMessage(infoMsg.getSubject(), 
//                        GetVendorItems((Long)infoMsg.getProperty("target")));
//            }
            return true;
        }
    }    
    
    public static LinkedList<HashMap<String, Serializable>> getVendorItems(OID vendorOid){
     // pull vendor info and return to client
        //VendorObject vendor = (VendorObject)EntityManager.getEntityByNamespace(vendorOid, VendorClient.NAMESPACE);
        //List<String> itemList = vendor.getVendorItems();
        LinkedList<HashMap<String, Serializable>> returnList = new LinkedList<HashMap<String, Serializable>>();            
        /*for (String item : itemList){
            Map<String, Serializable> props = ObjectManagerClient.getTemplate(item).getSubMap(InventoryClient.ITEM_NAMESPACE);
            HashMap<String, Serializable> itemInfo = new HashMap<String, Serializable>();
            itemInfo.put("item_name", item);
            itemInfo.put(InventoryClient.TEMPL_ICON, props.get(InventoryClient.TEMPL_ICON));
            itemInfo.put(InventoryClient.TEMPL_VALUE, props.get(InventoryClient.TEMPL_VALUE));
            returnList.add(itemInfo);
        }*/
        
        return returnList;
    }
    
    public void purchaseAbility(OID playerOid, String itemName) {
        //Check cost
        //Check balance
        //if sufficient balance, then add ability to player and decr cost from player balance
        synchronized(VendorPlugin.abilityList){
            int cost = VendorPlugin.abilityList.get(itemName);
            Float balance = BillingClient.getTokenBalance(playerOid);
            if (balance >= cost) {
                //Add ability to player
                CombatClient.addAbility(itemName, playerOid);
                //Process purchase, decrement tokens
                /*VirtualItemPurchaseInfo itemInfo = new VirtualItemPurchaseInfo();
                itemInfo.setItemDescription(itemName);
                itemInfo.setItemId(0);
                // FIXME: The VirtualItemPurchaseInfo should be changed to take an OID
                itemInfo.setPlayerOid(playerOid.toLong());
                itemInfo.setQuantity(1);
                VirtualItemPurchaseInfo[] items = new VirtualItemPurchaseInfo[1];
                items[0] = itemInfo;
                Float newBalance = BillingClient.decrementTokenBalance(playerOid, -(new Float(cost)), items);
                VendorClient.sendBalanceUpdate(playerOid, newBalance);*/
            }
        }
        
    }

    public HashMap<String,HashMap<String, Serializable>> getAbilities() {
        HashMap<String,HashMap<String, Serializable>> returnList = new HashMap<String,HashMap<String, Serializable>>();
        
        for(String abilityName : abilityList.keySet()){
            Log.debug("VendorPlugin.GetAbilities - processing ability " + abilityName);
            HashMap<String, String> ability = CombatClient.getAbilityInfo(abilityName);
            if(ability == null)
                continue;
            
            HashMap<String, Serializable> abilityInfo = new HashMap<String, Serializable>();
            abilityInfo.put("name", ability.get("name"));
            abilityInfo.put("icon", ability.get("icon"));
            abilityInfo.put("description", ability.get("name"));
            abilityInfo.put("cost", abilityList.get(abilityName));
            returnList.put(ability.get("name"), abilityInfo);
        }
        Log.debug("VendorPlugin.GetAbilities - returning ability list " + returnList.toString());
        return returnList;
    }

    public static boolean purchaseItems(OID playerOid, OID vendorOid, int itemID, Integer itemCount){
     // need to check that the purchase is valid, so pull the item template
        Template itemTemplate = ObjectManagerClient.getTemplate(itemID, ObjectManagerPlugin.ITEM_TEMPLATE);
        if (itemTemplate == null){
            log.error(itemID + " is not a valid item template id.");
            return false;
        }
        Map<String, Serializable> props = itemTemplate.getSubMap(Namespace.AGISITEM);
        
        Float itemValue = (Float)props.get(InventoryClient.TEMPL_VALUE);        
        // first make sure the vendor has this item to purchase
        /*VendorObject vendor = (VendorObject)EntityManager.getEntityByNamespace(vendorOid, VendorClient.NAMESPACE);
        if (!vendor.getVendorItems().contains(itemName)){
            log.warn(itemName + " is not valid for this vendor: " + vendorOid);
            return false;
        }*/
        
        Float balance = 0F;
        
        //if (UseVirtualCurrency()){
            balance = BillingClient.getTokenBalance(playerOid);
        //} else {
        //	balance = AgisInventoryClient.GetCurrencyBalance(playerOid);
        //}
        Boolean rv = false;
        if (Log.loggingInfo)
            Log.info("purchaseItems: " + itemID + " : " + itemCount + " : " + itemValue + " : " + balance);
        OID itemOid = null;
        if (itemValue * itemCount <= balance) {            
            // everything is valid so create the objects
            for (int i = 0; i < itemCount; i++) {
               if (Log.loggingDebug)
                   Log.debug("CreateItemSubObjCommand: templ=" + itemID + ", generating object");
               
               // Bag bagOid = (Bag)EnginePlugin.getObjectProperty(playerOid, InventoryClient.NAMESPACE,InventoryPlugin.INVENTORY_PROP_BAG_KEY);

               Template overrideTemplate = new Template();
               overrideTemplate.put(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT, true) ;

               itemOid = ObjectManagerClient.generateObject(itemID, ObjectManagerPlugin.ITEM_TEMPLATE, overrideTemplate);
               rv = InventoryClient.addItem(playerOid, playerOid, playerOid, itemOid);
               
               if (Log.loggingDebug)
                   Log.debug("CreateItemSubObjCommand: createitem: oid=" + itemOid + ", adding to inventory");
               rv = InventoryClient.addItem(playerOid, playerOid, playerOid, itemOid);
               if (Log.loggingDebug)
                   Log.debug("CommandPlugin: createitem: oid=" + itemOid + ", added, rv=" + rv);
               ChatClient.sendObjChatMsg(playerOid, 0, "Added item to inventory: " + itemID);           
            }
            
            Float newBalance = 0F;
            
            //if (useVirtualCurrency()){
                // now update the billing setup
                /*if (itemCount > 0){
                    VirtualItemPurchaseInfo purchasedItem = new VirtualItemPurchaseInfo();
                    // FIXME: The VirtualItemPurchaseInfo should be changed to take an OID
                    purchasedItem.setItemId(itemOid.toLong());
                    purchasedItem.setItemDescription("");
                    purchasedItem.setQuantity(itemCount);                        
                    newBalance = BillingClient.decrementTokenBalance(playerOid, -(itemValue * itemCount),
                            new VirtualItemPurchaseInfo[]{purchasedItem});
                }*/
            //} else {
            //    newBalance = AgisInventoryClient.UpdateCurrencyBalance(playerOid, -(itemValue * itemCount));
            //}
            VendorClient.sendBalanceUpdate(playerOid, newBalance);
        } else {
            rv = false;
        }
        
        return rv;
    }
    
    public static SubObjData createVendorSubObject(Template template, Namespace namespace, OID masterOid)
    {
        if (Log.loggingDebug)
            log.debug("GenerateSubObjectHook: masterOid=" + masterOid
                      + ", template=" + template);

        if (masterOid == null) {
            log.error("GenerateSubObjectHook: no master oid");
            return null;
        }

        if (Log.loggingDebug)
            log.debug("GenerateSubObjectHook: masterOid="+masterOid+", template="+template);

        Map<String, Serializable> props = template.getSubMap(VendorClient.NAMESPACE);

        if (props == null) {
            Log.warn("GenerateSubObjectHook: no props in ns "
                     + VendorClient.NAMESPACE);
            return null;
        }

        // generate the subobject
        /*VendorObject tinfo = new VendorObject(masterOid);
        tinfo.setName(template.getName());

        // copy properties from template to object
        for (Map.Entry<String, Serializable> entry : props.entrySet()) {
            String key = entry.getKey();
            Serializable value = entry.getValue();
            if (!key.startsWith(":")) {
                tinfo.setProperty(key, value);
            }
        }
        
        // since this is a vendor, convert the items list
        if (tinfo.getProperty(VendorClient.TMPL_VENDOR_ITEMS) != null){
            tinfo.setVendorItems((String)tinfo.getProperty(VendorClient.TMPL_VENDOR_ITEMS));
        }

        if (Log.loggingDebug)
            log.debug("GenerateSubObjectHook: created entity " + tinfo);*/

        // register the entity
        //EntityManager.registerEntityByNamespace(tinfo, VendorClient.NAMESPACE);

        //send a response message
        return new SubObjData();
    }
    
    private static Float tokenLowMark = 0F;
    
    public static void setLowTokenBalanceValue(Float lowMark) {
        tokenLowMark = lowMark;
    }
    
    public static Float getLowTokenBalanceValue() {
        return tokenLowMark;
    }
    
    private static Boolean virtualCurrency = Boolean.FALSE;
    
    public static void allowVirtualCurrency(Boolean allowed) {
        virtualCurrency = allowed;
    }
    
    public static Boolean useVirtualCurrency() { return virtualCurrency; } 
    
    private static String vendorUrl = null;
    
    public static void setVendorUrl(String url){
        //TODO: add regex for double checking valid url
        vendorUrl = url;
    }
    
    public static String getVendorUrl() { return vendorUrl; }
    
    public static void registerAbility(String abilityName, int tokenCost) {
        abilityList.put(abilityName, tokenCost);
    }
}
