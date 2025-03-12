package atavism.agis.plugins;

import java.io.Serializable;
import java.util.HashMap;

import atavism.msgsys.MessageType;
import atavism.server.engine.Engine;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

public class VendorClient {
    public static Namespace NAMESPACE = null;
    
    public static final MessageType MSG_TYPE_ITEM_PURCHASE      = MessageType.intern("ao.ITEM_PURCHASE");
    public static final MessageType MSG_TYPE_VENDOR_INFO_REQ    = MessageType.intern("ao.VENDOR_INFO_REQ");
    public static final MessageType MSG_TYPE_VENDOR_INFO        = MessageType.intern("ao.VENDOR_INFO");
    public static final MessageType MSG_TYPE_VENDOR_BALANCE     = MessageType.intern("ao.VENDOR_BALANCE");
    public static final MessageType MSG_TYPE_VENDOR_GET_BALANCE = MessageType.intern("ao.VENDOR_GET_BALANCE");
    
    public static final String EXTMSG_TYPE_ITEM_PURCHASE        = "ao.ITEM_PURCHASE";
    public static final String EXTMSG_TYPE_VENDOR_INFO          = "ao.VENDOR_INFO";
    public static final String EXTMSG_TYPE_VENDOR_BALANCE       = "ao.VENDOR_BALANCE";
    
    public static final String TMPL_IS_VENDOR                   = "isVendor";
    public static final String TMPL_VENDOR_ITEMS                = "items";
    
    public static boolean sendBalanceUpdate(OID playerOid, Float balance)
    {
        TargetedExtensionMessage clientBalanceMsg = new TargetedExtensionMessage();
        clientBalanceMsg.setExtensionType(VendorClient.EXTMSG_TYPE_VENDOR_BALANCE);
        clientBalanceMsg.setSubject(playerOid);
        clientBalanceMsg.setTarget(playerOid);
        if (balance == null){
            balance = 0F;
        }
        clientBalanceMsg.setProperty("balance", balance);
        if (VendorPlugin.useVirtualCurrency() && balance < VendorPlugin.getLowTokenBalanceValue()){
            clientBalanceMsg.setProperty("lowbalance", Boolean.TRUE);
        } else {
            clientBalanceMsg.setProperty("lowbalance", Boolean.FALSE);
        }
        int subs = Engine.getAgent().sendBroadcast(clientBalanceMsg);
        Log.debug("Sending balance message to " + subs + " number of subscribers...");
        return true;
    }
    
    public static boolean sendVendorInfoMessage(OID playerOid, HashMap<String,HashMap<String, Serializable>> itemList) 
    {       
        TargetedExtensionMessage returnMsg = new TargetedExtensionMessage();
        returnMsg.setExtensionType(VendorClient.EXTMSG_TYPE_VENDOR_INFO);
        returnMsg.setSubject(playerOid);
        returnMsg.setTarget(playerOid);
        returnMsg.setProperty("itemList", itemList);
        // send the vendorurl, if null then itemList will be bypassed for 
        // displaying the url.
        returnMsg.setProperty("vendorurl", VendorPlugin.getVendorUrl());
        returnMsg.setProperty("usevcurrency", VendorPlugin.useVirtualCurrency());
        Engine.getAgent().sendBroadcast(returnMsg);
        return true;
    }
}
