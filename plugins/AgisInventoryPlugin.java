package atavism.agis.plugins;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.io.*;
import java.sql.ResultSet;
import java.sql.Statement;

import atavism.msgsys.*;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.messages.PropertyMessage;
import atavism.server.messages.SubscriptionManager;
import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.plugins.InstanceClient.InstanceInfo;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.ObjectInfo;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;
import atavism.server.engine.*;
import atavism.server.util.*;
import atavism.agis.objects.*;
import atavism.agis.objects.Currency;
import atavism.agis.plugins.AgisInventoryClient.CheckComponentMessage;
import atavism.agis.plugins.AgisInventoryClient.DeletePlayerShopMessage;
import atavism.agis.plugins.AgisInventoryClient.EquippedItemUsedMessage;
import atavism.agis.plugins.AgisInventoryClient.SendBankInventoryMessage;
import atavism.agis.plugins.AgisInventoryClient.StartPlayerShopMessage;
import atavism.agis.plugins.AgisInventoryClient.equipItemMessage;
import atavism.agis.plugins.AgisInventoryClient.getEquipItemListMessage;
import atavism.agis.plugins.AgisInventoryClient.getItemMessage;
import atavism.agis.plugins.AgisInventoryClient.unequipItemMessage;
import atavism.agis.plugins.ChatClient.TargetedComReqMessage;
import atavism.agis.plugins.GroupClient.GroupInfo;
import atavism.agis.plugins.BonusClient.BonusesUpdateMessage;
import atavism.agis.plugins.BonusClient.GlobalEventBonusesUpdateMessage;
import atavism.agis.util.EquipHelper;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.agis.util.RequirementCheckResult;
import atavism.agis.util.RequirementChecker;
import atavism.agis.core.*;
import atavism.agis.database.*;

/**
 * The AgisInventoryPlugin is the manager of Inventory for players and mobs and all
 * related areas such as Trading, Looting, and Mail.  The Plugin creates and works with
 * the Inventory Sub Object, which contains the players inventory (their bags, mailbox
 * and eventually their bank).
 * 
 * The Plugin is set up with Hooks for many Inventory related messages and loads in the 
 * players inventory sub object to get the information requested or make the changes 
 * needed.
 * 
 * @author Andrew Harrison
 *
 */
public class AgisInventoryPlugin extends InventoryPlugin {

    
    HashMap<OID, ScheduledFuture> tasks = new HashMap<OID, ScheduledFuture>();

    
    /**
     * Run on activation, this function sets up the Hooks and filters for the messages
     * the plugin deals with.
     */
    public void onActivate() {
        SubscriptionManager.start(AgisInventoryClient.MSG_TYPE_GET_PET_INVENTORY);
        super.onActivate();
        getHookManager().addHook(BonusClient.MSG_TYPE_BONUSES_UPDATE, new BonusesUpdateHook());
        getHookManager().addHook(BonusClient.MSG_TYPE_GLOBAL_EVENT_UPDATE, new GlobalEventBonusesUpdateHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_COUNT_GENERIC_ITEM,new CountGenericItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GET_ITEMS_WITH_PARAM,new GetItemsWithParamHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_SET_ITEM_PROPERTY,new SetItemProperty());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GET_SETS_EQUIPED, new GetSetsFromEquiped());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_ENCHANTING_DETAIL, new EnchantingDetaile());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_ENCHANT, new Enchanting());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GET_ITEM,new GetItem());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_SET_ITEM_COUNT, new AlterItemCount());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GENERATE_ITEM_AS, new GenerateItemAs());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GENERATE_ITEM_RESPONSE_OID, new GenerateItemResponseOid());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GET_ITEM_PROPERTY, new GetItemProperty());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_SOCKETING_DETAIL, new SocketingDetaile());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_SOCKETING_RESET_DETAIL, new SocketingResetDetaile());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_SOCKETING_RESET, new SocketingReset());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_INSERT_TO_SOCKET, new InsertItemToSocket());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_AGIS_INV_FIND, new AgisFindItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_TRADE_START_REQ, new TradeStartReqHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_TRADE_START_REQ_RESPONSE, new TradeStartReqResponseHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_TRADE_OFFER_REQ, new TradeOfferReqHook());
        getHookManager().addHook(WorldManagerClient.MSG_TYPE_DESPAWNED, new DespawnedHook());
        getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_REMOVE_GENERIC_ITEM, new RemoveGenericItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_REMOVE_SPECIFIC_ITEM, new RemoveSpecificItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GET_SPECIFIC_ITEM_DATA, new GetSpecificItemDataHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GET_GENERIC_ITEM_DATA, new GetGenericItemDataHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GENERATE_ITEM, new GenerateItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GENERATE_ITEM_NEEDS_RESPONSE, new GenerateItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_PLACE_BAG, new PlaceBagHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_MOVE_BAG, new MoveBagHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_REMOVE_BAG, new RemoveBagHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_MOVE_ITEM, new MoveItemHook(Engine.getPersistenceManager()));
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_LOOT_ITEM, new LootItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_LOOT_ROLL, new LootRollHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_LOOT_ALL, new LootAllHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_LOOT_ALL_F, new MultiLootAllHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_LOOT_CHEST, new LootChestHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GENERATE_LOOT, new GenerateLootHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GENERATE_LOOT_EFFECT, new GenerateLootEffectHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GET_LOOT_LIST, new GetLootListHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GET_MERCHANT_LIST, new GetMerchantListHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_PURCHASE_ITEM, new PurchaseItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_SELL_ITEM, new SellItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_PICKUP_ITEM, new PickupItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_EQUIPPED_ITEM_USED, new EquippedItemUsedHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_REPAIR_ITEMS, new RepairItemsHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GET_BANK_ITEMS, new SendBankInventoryHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_OPEN_STORAGE, new OpenStorageHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GET_STORAGE_CONTENTS, new GetStorageContentsHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_STORE_ITEM_IN_BANK, new StoreItemInBankHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_RETRIEVE_ITEM_FROM_BANK, new RetrieveItemInBankHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_CREATE_STORAGE, new CreateStorageHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_SEND_STORAGE_ITEMS_AS_MAIL, new MailStorageItemsToUserHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_SEND_INV_UPDATE, new SendInventoryUpdateHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GET_MAIL, new GetMailHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_MAIL_READ, new MailReadHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_MAIL_TAKE_ITEM, new TakeMailItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_RETURN_MAIL, new ReturnMailHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_DELETE_MAIL, new DeleteMailHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_SEND_MAIL, new SendMailHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_SEND_PURCHASE_MAIL, new SendPurchaseMailHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_CHECK_CURRENCY, new CheckCurrencyHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_ALTER_CURRENCY, new AlterCurrencyHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_CHECK_CURRENCY_OFFLINE, new CheckCurrencyOfflineHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_ALTER_CURRENCY_OFFLINE, new AlterCurrencyOfflineHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_ALTER_ITEM_COUNT, new AlterItemCountHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_USE_ACCOUNT_ITEM, new UseAccountItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GET_ITEM_STORE, new GetStoreItemsHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_PURCHASE_STORE_ITEM, new PurchaseStoreItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_EQUIP_ITEM_IN_SLOT, new EquipItemInSlotHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_EQUIP_ITEM_IN_PET_SLOT, new EquipItemInPetSlotHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_DOES_INVENTORY_HAS_SUFFICIENT_SPACE, new DoesInventoryHasSufficientSpaceHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_EDIT_LOCK, new EditLockHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_LOCKPICK, new LockpickHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_ACTIVATE_CHEST, new ActivateChestHook());

        getHookManager().addHook(AgisMobClient.MSG_TYPE_CATEGORY_UPDATED, new CategoryUpdatedHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_RELOAD_ITEMS, new ReloadItemsHook());

        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_RETURNBOOLEAN_CHECK_COMPONENTS, new CheckComponentHook());

        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_UPDATE_STORAGE, new UpdateStorageHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_LOOT_GROUND_ITEM, new LootGroundItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_DROP_GROUND_ITEM, new DropGroundItemHook());

        // Hook to process login/logout messages
        getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
        getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
        //PlayerShops
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_START_PLAYER_SHOP, new StartPlayerShopHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_START_SHOP, new StartShopHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_STOP_SHOP, new StopShopHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_OPEN_PLAYER_SHOP, new GetPlayerShopItemsHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_CANCEL_SHOP, new CancelPlayerShopHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_PLAYER_SHOP_BUY, new PurchasePlayerShopItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_DELETE_PLAYER_SHOP, new DeletePlayerShopHook());
        
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GET_EQUIP_ITEMS, new GetEquipItemsHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_EQUIP_ITEM, new EquipItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_UNEQUIP_ITEM, new UnequipItemHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_DRAW_WEAPON, new DrawWeaponHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_DRAW_WEAPON_CLIENT, new ClientDrawWeaponHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_SWITCH_WEAPON, new SwitchWeaponHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_GENERATE_LOOT_FROM_LOOT_TABLE, new GenerateLootFromLootTableHook());
        getHookManager().addHook(AgisInventoryClient.MSG_TYPE_INVENTORY_GENERATE_GROUND_LOOT, new GenerateGroundLootHook());


        try {
            // Filters for messages that sends a response back.
            MessageTypeFilter filterNeedsResponse = new MessageTypeFilter();
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_COUNT_GENERIC_ITEM);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_GET_SETS_EQUIPED);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_GENERATE_ITEM_RESPONSE_OID);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_AGIS_INV_FIND);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_GET_ITEM_PROPERTY);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_GET_SKINS);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_GET_ACCOUNT_ITEM_COUNT);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_GET_GENERIC_ITEM_DATA);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_RETURNBOOLEAN_CHECK_COMPONENTS);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_CHECK_CURRENCY);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_CHECK_CURRENCY_OFFLINE);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_PURCHASE_ITEM);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_GENERATE_ITEM_NEEDS_RESPONSE);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_CREATE_STORAGE);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_GET_STORAGE_CONTENTS);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_DOES_INVENTORY_HAS_SUFFICIENT_SPACE);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_SET_ITEM_PROPERTY);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_GENERATE_ITEM_AS);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_SET_ITEM_COUNT);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_GET_ITEMS_WITH_PARAM);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_REMOVE_GENERIC_ITEM);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_REMOVE_SPECIFIC_ITEM);
            filterNeedsResponse.addType(AgisInventoryClient.MSG_TYPE_DRAW_WEAPON);

                    /* Long sub = */ Engine.getAgent().createSubscription(filterNeedsResponse, this, MessageAgent.RESPONDER);

            // Filters for messages that don't send a response.
            MessageTypeFilter filterNoResponse = new MessageTypeFilter();
            filterNoResponse.addType(BonusClient.MSG_TYPE_BONUSES_UPDATE);
            filterNoResponse.addType(BonusClient.MSG_TYPE_GLOBAL_EVENT_UPDATE);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_INSERT_TO_SOCKET);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_SOCKETING_DETAIL);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_SOCKETING_RESET);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_SOCKETING_RESET_DETAIL);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_ENCHANTING_DETAIL);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_ENCHANT);
            
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_UPDATE_STORAGE);
                
            
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_TRADE_START_REQ);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_TRADE_START_REQ_RESPONSE);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_TRADE_OFFER_REQ);
            filterNoResponse.addType(WorldManagerClient.MSG_TYPE_DESPAWNED);
            filterNoResponse.addType(WorldManagerClient.MSG_TYPE_SPAWNED);
        //  filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_REMOVE_GENERIC_ITEM);
        //  filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_REMOVE_SPECIFIC_ITEM);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_GET_SPECIFIC_ITEM_DATA);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_GENERATE_ITEM);
            
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_PLACE_BAG);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_MOVE_BAG);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_REMOVE_BAG);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_MOVE_ITEM);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_LOOT_ROLL);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_LOOT_ITEM);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_LOOT_ALL);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_LOOT_ALL_F);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_LOOT_GROUND_ITEM);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_DROP_GROUND_ITEM);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_LOOT_CHEST);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_INVENTORY_GENERATE_GROUND_LOOT);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_GENERATE_LOOT);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_GET_LOOT_LIST);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_GENERATE_LOOT_EFFECT);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_GET_MERCHANT_LIST);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_SELL_ITEM);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_PICKUP_ITEM);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_EQUIPPED_ITEM_USED);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_REPAIR_ITEMS);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_GET_BANK_ITEMS);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_OPEN_STORAGE);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_STORE_ITEM_IN_BANK);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_RETRIEVE_ITEM_FROM_BANK);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_SEND_STORAGE_ITEMS_AS_MAIL);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_SEND_INV_UPDATE);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_GET_MAIL);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_MAIL_READ);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_MAIL_TAKE_ITEM);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_RETURN_MAIL);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_DELETE_MAIL);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_SEND_MAIL);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_SEND_PURCHASE_MAIL);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_ALTER_CURRENCY);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_ALTER_CURRENCY_OFFLINE);
                //filterNoResponse.addType(AgisMobClient.MSG_TYPE_CATEGORY_UPDATED);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_RELOAD_ITEMS);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_PURCHASE_SKIN);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_SET_WEAPON);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_SET_SKIN_COLOUR);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_ALTER_ITEM_COUNT);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_USE_ACCOUNT_ITEM);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_GET_ITEM_STORE);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_PURCHASE_STORE_ITEM);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_EQUIP_ITEM_IN_SLOT);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_EQUIP_ITEM_IN_PET_SLOT);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_EDIT_LOCK);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_LOCKPICK);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_ACTIVATE_CHEST);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_START_PLAYER_SHOP);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_START_SHOP);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_STOP_SHOP);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_OPEN_PLAYER_SHOP);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_CANCEL_SHOP);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_PLAYER_SHOP_BUY);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_DELETE_PLAYER_SHOP);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_GENERATE_LOOT_FROM_LOOT_TABLE);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_DRAW_WEAPON_CLIENT);
            filterNoResponse.addType(AgisInventoryClient.MSG_TYPE_SWITCH_WEAPON);
            /* Long sub = */ Engine.getAgent().createSubscription(filterNoResponse, this);
            
            // Create responder subscription
            MessageTypeFilter filter2 = new MessageTypeFilter();
            filter2.addType(LoginMessage.MSG_TYPE_LOGIN);
            filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
            filter2.addType(AgisInventoryClient.MSG_TYPE_GET_ITEM);
            filter2.addType(AgisInventoryClient.MSG_TYPE_GET_EQUIP_ITEMS);
            filter2.addType(AgisInventoryClient.MSG_TYPE_EQUIP_ITEM);
            filter2.addType(AgisInventoryClient.MSG_TYPE_UNEQUIP_ITEM);
               Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);

            registerUnloadHook(CombatClient.INSTANCE_NAMESPACE, new InstanceUnloadHook());

        } catch (Exception e) {
            throw new AORuntimeException("activate failed", e);
        }
        
        loadData();
      
        // Setup connection to admin database
        aDB = new AccountDatabase(true);
        authDB = new AuthDatabase();
    }

    @Override
    protected boolean reloadPlayerInventory(OID objOid) {
        Lock lock = getObjectLockManager().getLock(objOid);
        lock.lock();
        try {
            InventoryInfo iInfo = getInventoryInfo(objOid);
                unloadInventory(iInfo);
                loadInventory(iInfo);
                for (OID bagOid : iInfo.getBags()) {
                    Bag bag = getBag(bagOid);
                    Log.debug("BAG: bag slots: " + bag.getNumSlots());
                }
            Log.debug("reloadPlayerInventory for player " + objOid + " complete");
        }
        finally {
            lock.unlock();
        }
        sendBagInvUpdate(objOid);

        return false;
    }


    void loadData() {
    	  
        // Load settings
        ContentDatabase cDB = new ContentDatabase(false);
        String bagCount = cDB.loadGameSetting("PLAYER_BAG_COUNT");
        if (bagCount != null)
            INVENTORY_BAG_COUNT = Integer.parseInt(bagCount);
        log.debug("Game Setting INVENTORY_BAG_COUNT is set to "+INVENTORY_BAG_COUNT);
        
           String firstBagSize = cDB.loadGameSetting("PLAYER_DEFAULT_BAG_SIZE");
        if (firstBagSize != null)
            INVENTORY_FIRST_BAG_SIZE = Integer.parseInt(firstBagSize);
        log.debug("Game Setting INVENTORY_FIRST_BAG_SIZE is set to "+INVENTORY_FIRST_BAG_SIZE);
        
           String sellFactor = cDB.loadGameSetting("SELL_FACTOR");
        if (sellFactor != null)
            SELL_FACTOR = Float.parseFloat(sellFactor);
        log.debug("Game Setting SELL_FACTOR is set to "+SELL_FACTOR);
        
          String useFlatRepairRate = cDB.loadGameSetting("USE_FLAT_REPAIR_RATE");
        if (useFlatRepairRate != null)
            USE_FLAT_REPAIR_RATE = Boolean.parseBoolean(useFlatRepairRate);
        log.debug("Game Setting USE_FLAT_REPAIR_RATE is set to "+USE_FLAT_REPAIR_RATE);
       
        String flatRepairRate = cDB.loadGameSetting("FLAT_REPAIR_RATE");
        if (flatRepairRate != null)
            FLAT_REPAIR_RATE  = Integer.parseInt(flatRepairRate);
        log.debug("Game Setting FLAT_REPAIR_RATE is set to "+FLAT_REPAIR_RATE );
        
        String flatRepairRateGrade = cDB.loadGameSetting("FLAT_REPAIR_RATE_GRADE_MODIFIER");
        if (flatRepairRateGrade != null)
            FLAT_REPAIR_RATE_GRADE_MODIFIER = Integer.parseInt(flatRepairRateGrade);
        log.debug("Game Setting FLAT_REPAIR_RATE_GRADE_MODIFIER is set to "+FLAT_REPAIR_RATE_GRADE_MODIFIER);
    
        
        
           String repairFactor = cDB.loadGameSetting("REPAIR_RATE");
        if (repairFactor != null)
            REPAIR_RATE = Float.parseFloat(repairFactor);
        log.debug("Game Setting REPAIR_RATE is set to "+REPAIR_RATE);
        
           String corpseDuration = cDB.loadGameSetting("PLAYER_CORPSE_LOOT_DURATION");
        if (corpseDuration != null)
            PLAYER_CORPSE_LOOT_DURATION = Integer.parseInt(corpseDuration);
        log.debug("Game Setting PLAYER_CORPSE_LOOT_DURATION is set to "+PLAYER_CORPSE_LOOT_DURATION);
        
           String corpseSafeDuration = cDB.loadGameSetting("PLAYER_CORPSE_SAFE_LOOT_DURATION");
        if (corpseSafeDuration != null)
            PLAYER_CORPSE_SAFE_LOOT_DURATION = Integer.parseInt(corpseSafeDuration);
        log.debug("Game Setting PLAYER_CORPSE_SAFE_LOOT_DURATION is set to "+PLAYER_CORPSE_SAFE_LOOT_DURATION);
        
          String playerCorpseMobTemplate = cDB.loadGameSetting("PLAYER_CORPSE_MOB_TEMPLATE");
        if (playerCorpseMobTemplate != null)
            PLAYER_CORPSE_MOB_TEMPLATE = Integer.parseInt(playerCorpseMobTemplate);
        log.debug("Game Setting PLAYER_CORPSE_MOB_TEMPLATE is set to "+PLAYER_CORPSE_MOB_TEMPLATE);
        
          String playerCorpseDropsEquipment = cDB.loadGameSetting("PLAYER_CORPSE_DROPS_EQUIPMENT");
        if (playerCorpseDropsEquipment != null)
            PLAYER_CORPSE_DROPS_EQUIPMENT = Boolean.parseBoolean(playerCorpseDropsEquipment);
        log.debug("Game Setting PLAYER_CORPSE_DROPS_EQUIPMENT is set to "+PLAYER_CORPSE_DROPS_EQUIPMENT);
        
          
        String bankExtraBagsCount = cDB.loadGameSetting("BANK_EXTRA_BAGS_COUNT");
        if (bankExtraBagsCount != null)
            BANK_EXTRA_BAGS_COUNT = Integer.parseInt(bankExtraBagsCount);
        log.debug("Game Setting BANK_EXTRA_BAGS_COUNT is set to "+BANK_EXTRA_BAGS_COUNT);
        
              String bankSlotsCount = cDB.loadGameSetting("BANK_SLOTS_COUNT");
        if (bankSlotsCount != null)
            BANK_SLOTS_COUNT = Integer.parseInt(bankSlotsCount);
        log.debug("Game Setting BANK_SLOTS_COUNT is set to "+BANK_SLOTS_COUNT);
        
              
        String socket_failed_clear = cDB.loadGameSetting("SOCKET_FAILED_CLEAR");
        if (socket_failed_clear != null)
            SOCKET_FAILED_CLEAR = Boolean.parseBoolean(socket_failed_clear);
        log.debug("Game Setting SOCKET_FAILED_CLEAR is set to "+SOCKET_FAILED_CLEAR);
        
              
        String socket_chance = cDB.loadGameSetting("SOCKET_CHANCE");
        if (socket_chance != null)
            SOCKET_CHANCE = Float.parseFloat(socket_chance);
        log.debug("Game Setting SOCKET_CHANCE is set to "+SOCKET_CHANCE);
        
              
        String socket_create_time = cDB.loadGameSetting("SOCKET_CREATE_TIME");
        if (socket_create_time != null)
            SOCKET_CREATE_TIME = Integer.parseInt(socket_create_time);
        log.debug("Game Setting SOCKET_CREATE_TIME is set to "+SOCKET_CREATE_TIME);
        
             
        String socket_price_base = cDB.loadGameSetting("SOCKET_PRICE_BASE");
        if (socket_price_base != null)
            SOCKET_PRICE_BASE = Integer.parseInt(socket_price_base);
        log.debug("Game Setting SOCKET_PRICE_BASE is set to "+SOCKET_PRICE_BASE);
        
             String socket_price_per_grade = cDB.loadGameSetting("SOCKET_PRICE_PER_GRADE");
        if (socket_price_per_grade != null)
            SOCKET_PRICE_PER_GRADE = Integer.parseInt(socket_price_per_grade);
        log.debug("Game Setting SOCKET_PRICE_PER_GRADE is set to "+SOCKET_PRICE_PER_GRADE);
        
             String socket_price_currency = cDB.loadGameSetting("SOCKET_PRICE_CURRENCY");
        if (socket_price_currency != null)
            SOCKET_PRICE_CURRENCY = Integer.parseInt(socket_price_currency);
        log.debug("Game Setting SOCKET_PRICE_CURRENCY is set to "+SOCKET_PRICE_CURRENCY);
        
              //Socketing Reset
        
        
        String socket_reset_time = cDB.loadGameSetting("SOCKET_RESET_TIME");
        if (socket_reset_time != null)
            SOCKET_RESET_TIME = Integer.parseInt(socket_reset_time);
        log.debug("Game Setting SOCKET_RESET_TIME is set to "+SOCKET_RESET_TIME);
        
        String socket_reset_chance = cDB.loadGameSetting("SOCKET_RESET_CHANCE");
        if (socket_reset_chance != null)
            SOCKET_RESET_CHANCE = Float.parseFloat(socket_reset_chance);
        log.debug("Game Setting SOCKET_RESET_CHANCE is set to "+SOCKET_RESET_CHANCE);
        
    
        String socket_reset_price_base = cDB.loadGameSetting("SOCKET_RESET_PRICE_BASE");
        if (socket_reset_price_base != null)
            SOCKET_RESET_PRICE_BASE = Integer.parseInt(socket_reset_price_base);
        log.debug("Game Setting SOCKET_RESET_PRICE_BASE is set to "+SOCKET_RESET_PRICE_BASE);
        
        String socket_reset_price_per_grade = cDB.loadGameSetting("SOCKET_RESET_PRICE_PER_GRADE");
        if (socket_reset_price_per_grade != null)
            SOCKET_RESET_PRICE_PER_GRADE = Integer.parseInt(socket_reset_price_per_grade);
        log.debug("Game Setting SOCKET_RESET_PRICE_PER_GRADE is set to "+SOCKET_RESET_PRICE_PER_GRADE);
        
        String socket_reset_price_currency = cDB.loadGameSetting("SOCKET_RESET_PRICE_CURRENCY");
        if (socket_reset_price_currency != null)
            SOCKET_RESET_PRICE_CURRENCY = Integer.parseInt(socket_reset_price_currency);
        log.debug("Game Setting SOCKET_RESET_PRICE_CURRENCY is set to "+SOCKET_RESET_PRICE_CURRENCY);
        
        String enchanting_time = cDB.loadGameSetting("ENCHANTING_TIME");
        if (enchanting_time != null)
             ENCHANTING_TIME = Integer.parseInt(enchanting_time);
        log.debug("Game Setting ENCHANTING_TIME is set to "+ENCHANTING_TIME);
        
        
        String durability_loss_chance_from_attack = cDB.loadGameSetting("DURABILITY_LOSS_CHANCE_FROM_ATTACK");
        if (durability_loss_chance_from_attack != null) 
            DURABILITY_LOSS_CHANCE_FROM_ATTACK = Integer.parseInt(durability_loss_chance_from_attack);
        log.debug("Game Setting DURABILITY_LOSS_CHANCE_FROM_ATTACK is set to "+DURABILITY_LOSS_CHANCE_FROM_ATTACK);
        
        String durability_loss_chance_from_defend = cDB.loadGameSetting("DURABILITY_LOSS_CHANCE_FROM_DEFEND");
        if (durability_loss_chance_from_defend != null) 
            DURABILITY_LOSS_CHANCE_FROM_DEFEND = Integer.parseInt(durability_loss_chance_from_defend);
        log.debug("Game Setting DURABILITY_LOSS_CHANCE_FROM_DEFEND is set to "+DURABILITY_LOSS_CHANCE_FROM_DEFEND);
        
        String durability_loss_chance_from_gather = cDB.loadGameSetting("DURABILITY_LOSS_CHANCE_FROM_GATHER");
        if (durability_loss_chance_from_gather != null) 
            DURABILITY_LOSS_CHANCE_FROM_GATHER = Integer.parseInt(durability_loss_chance_from_gather);
        log.debug("Game Setting DURABILITY_LOSS_CHANCE_FROM_GATHER is set to "+DURABILITY_LOSS_CHANCE_FROM_GATHER);
        
        String durability_loss_chance_from_craft = cDB.loadGameSetting("DURABILITY_LOSS_CHANCE_FROM_CRAFT");
        if (durability_loss_chance_from_craft != null) 
            DURABILITY_LOSS_CHANCE_FROM_CRAFT = Integer.parseInt(durability_loss_chance_from_craft);
        log.debug("Game Setting DURABILITY_LOSS_CHANCE_FROM_CRAFT is set to "+DURABILITY_LOSS_CHANCE_FROM_CRAFT);

        String durability_destroy_broken_items = cDB.loadGameSetting("DURABILITY_DESTROY_BROKEN_ITEMS");
        if (durability_destroy_broken_items != null) 
            DURABILITY_DESTROY_BROKEN_ITEMS = Boolean.parseBoolean(durability_destroy_broken_items);
        log.debug("Game Setting DURABILITY_DESTROY_BROKEN_ITEMS is set to "+DURABILITY_DESTROY_BROKEN_ITEMS);
        String give_quest_items_to_all_in_group = cDB.loadGameSetting("GIVE_QUEST_ITEMS_TO_ALL_IN_GROUP");
        if (give_quest_items_to_all_in_group != null) 
            GIVE_QUEST_ITEMS_TO_ALL_IN_GROUP = Boolean.parseBoolean(give_quest_items_to_all_in_group);
        log.debug("Game Setting DURABILITY_DESTROY_BROKEN_ITEMS is set to "+GIVE_QUEST_ITEMS_TO_ALL_IN_GROUP);

         String loot_dice_timeout = cDB.loadGameSetting("LOOT_DICE_TIMEOUT");
         if (loot_dice_timeout != null) 
             LOOT_DICE_TIMEOUT = Integer.parseInt(loot_dice_timeout);
        log.debug("Game Setting LOOT_DICE_TIMEOUT is set to "+LOOT_DICE_TIMEOUT);
    
        String buildingcorpseDuration = cDB.loadGameSetting("BUILDING_CORPSE_LOOT_DURATION");
        if (buildingcorpseDuration != null)
        BUILDING_CORPSE_LOOT_DURATION = Integer.parseInt(buildingcorpseDuration);
        log.debug("Game Setting BUILDING_CORPSE_LOOT_DURATION is set to "+BUILDING_CORPSE_LOOT_DURATION);
        
     
        
          String buildingCorpseMobTemplate = cDB.loadGameSetting("BUILDING_CORPSE_MOB_TEMPLATE");
        if (buildingCorpseMobTemplate != null)
            BUILDING_CORPSE_MOB_TEMPLATE = Integer.parseInt(buildingCorpseMobTemplate);
        log.debug("Game Setting BUILDING_CORPSE_MOB_TEMPLATE is set to "+BUILDING_CORPSE_MOB_TEMPLATE);
        
        
        String loot_distance = cDB.loadGameSetting("LOOT_DISTANCE");
        if (loot_distance != null)
            LOOT_DISTANCE = Float.parseFloat(loot_distance);

        
        String quest_check_equip = cDB.loadGameSetting("QUEST_CHECK_EQUIPED_ITEMS");
        if (quest_check_equip != null) 
        	QUEST_CHECK_EQUIPED_ITEMS = Boolean.parseBoolean(quest_check_equip);
        log.debug("Game Setting QUEST_CHECK_EQUIPED_ITEMS is set to "+QUEST_CHECK_EQUIPED_ITEMS);
        
        String store_Bound_Item_in_Bank = cDB.loadGameSetting("STORE_BOUND_ITEM_IN_BANK");
        if (store_Bound_Item_in_Bank != null) 
        	STORE_BOUND_ITEM_IN_BANK = Boolean.parseBoolean(store_Bound_Item_in_Bank);
        log.debug("Game Setting STORE_BOUND_ITEM_IN_BANK is set to "+STORE_BOUND_ITEM_IN_BANK);

        String inventory_loot_on_ground = cDB.loadGameSetting("INVENTORY_LOOT_ON_GROUND");
        if (inventory_loot_on_ground != null)
            INVENTORY_LOOT_ON_GROUND = Boolean.parseBoolean(inventory_loot_on_ground);
        log.debug("Game Setting INVENTORY_LOOT_ON_GROUND is set to "+INVENTORY_LOOT_ON_GROUND);
        String inventory_loot_on_ground_timeout = cDB.loadGameSetting("INVENTORY_LOOT_ON_GROUND_TIMEOUT");
        if (inventory_loot_on_ground_timeout != null)
            INVENTORY_LOOT_ON_GROUND_TIMEOUT = Integer.parseInt(inventory_loot_on_ground_timeout);
        log.debug("Game Setting INVENTORY_LOOT_ON_GROUND_TIMEOUT is set to "+INVENTORY_LOOT_ON_GROUND_TIMEOUT);

        String inventory_loot_on_ground_interval_timeout = cDB.loadGameSetting("INVENTORY_LOOT_ON_GROUND_TIMEOUT_INTERVAL");
        if (inventory_loot_on_ground_interval_timeout != null)
            INVENTORY_LOOT_ON_GROUND_TIMEOUT_INTERVAL = Integer.parseInt(inventory_loot_on_ground_interval_timeout);
        log.debug("Game Setting INVENTORY_LOOT_ON_GROUND_TIMEOUT_INTERVAL is set to "+INVENTORY_LOOT_ON_GROUND_TIMEOUT_INTERVAL);

        String inventory_loot_on_ground_max_dostance = cDB.loadGameSetting("INVENTORY_LOOT_ON_GROUND_MAX_DISTANCE");
        if (inventory_loot_on_ground_max_dostance != null)
            INVENTORY_LOOT_ON_GROUND_MAX_DISTANCE = Integer.parseInt(inventory_loot_on_ground_max_dostance);
        log.debug("Game Setting INVENTORY_LOOT_ON_GROUND_MAX_DISTANCE is set to "+INVENTORY_LOOT_ON_GROUND_MAX_DISTANCE);

        INVENTORY_LOOT_ON_GROUND_LOGOUT_QUALITY_MAIL = cDB.loadGameSetting("INVENTORY_LOOT_ON_GROUND_LOGOUT_QUALITY_MAIL");
        log.debug("Game Setting INVENTORY_LOOT_ON_GROUND_LOGOUT_QUALITY_MAIL is set to "+INVENTORY_LOOT_ON_GROUND_LOGOUT_QUALITY_MAIL);

        String _MAIL_LIFE_DAYS = cDB.loadGameSetting("MAIL_LIFE_DAYS");
        if (_MAIL_LIFE_DAYS != null)
            MAIL_LIFE_DAYS = Integer.parseInt(_MAIL_LIFE_DAYS);
        log.debug("Game Setting MAIL_LIFE_DAYS is set to "+MAIL_LIFE_DAYS);

        String _MAIL_COD_LIFE_DAYS = cDB.loadGameSetting("MAIL_COD_LIFE_DAYS");
        if (_MAIL_COD_LIFE_DAYS != null)
            MAIL_COD_LIFE_DAYS = Integer.parseInt(_MAIL_COD_LIFE_DAYS);
        log.debug("Game Setting MAIL_COD_LIFE_DAYS is set to "+MAIL_COD_LIFE_DAYS);

        cDB.loadEditorOptions();
        
        // Load items from the database
        ItemDatabase iDB = new ItemDatabase(false);
        iDB.LoadSlotsDefinition();
        iDB.LoadSlotsSets();
        iDB.LoadSlotsProfileDefinition();

        loadItemsSetsFromDatabase(iDB);
        loadItemsFromDatabase(iDB);
        loadItemEnchantProfileFromDatabase(iDB);
        loadItemQualitySettingsFromDatabase(iDB);
        loadVipLevelFromDatabase(iDB);
       // iDB.close();
        
        MobDatabase mDB = new MobDatabase(false);
        loadLootTables(cDB);
        loadCurrencies(mDB);
        if(!Engine.isAIO()) {
            HashMap<Integer, PetProfile> petprofiles = mDB.LoadPetProfiles();
            for (int pp: petprofiles.keySet()) {
                Agis.PetProfile.register(pp, petprofiles.get(pp));
                if (Log.loggingDebug)
                    Log.debug("MOB: loaded PetProfile: [" + petprofiles.get(pp).getName() + "]");
            }
        }
       // mDB.close();
        if(INVENTORY_LOOT_ON_GROUND) {
            GroundItemTimer timer = new GroundItemTimer();
            ScheduledFuture<?> sf = Engine.getExecutor().scheduleAtFixedRate(timer, INVENTORY_LOOT_ON_GROUND_TIMEOUT_INTERVAL, INVENTORY_LOOT_ON_GROUND_TIMEOUT_INTERVAL , TimeUnit.SECONDS);
        }
    }


    class InstanceUnloadHook implements UnloadHook {
        public void onUnload(Entity entity) {
            try {
                ConcurrentHashMap<OID, ConcurrentHashMap<OID, ItemOnGroundData>> data = itemsOnGround.remove(entity.getOid());
                data.forEach((k, v) -> {
                    ArrayList<OID> generatedItems = new ArrayList<OID>();
                    for (ItemOnGroundData iogd : v.values()) {
                        Template tmpl = ObjectManagerPlugin.getTemplate(iogd.getTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
                        Integer itemGrade = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "itemGrade");
                        String[] grades = INVENTORY_LOOT_ON_GROUND_LOGOUT_QUALITY_MAIL.split(";");
                        boolean found = false;
                        for (String grade : grades) {
                            if (itemGrade == Integer.parseInt(grade)) {
                                found = true;
                            }
                        }
                        if (found) {
                            if (iogd.getItemOID() == null) {
                                OID itemOid = generateItem(iogd.getTemplateId(), null);
                                AgisItem item = getAgisItem(itemOid);
                                item.setStackSize(iogd.getStack());
                                ObjectManagerClient.saveObject(itemOid);
                                generatedItems.add(itemOid);
                            }
                        }

                    }
                    if (generatedItems.size() > 0) {
                        Log.debug("MAIL: creating mail");

                        Mail m = new Mail(-1, false, k, "", null, "System", "", "", -1, 0, generatedItems, 1, false);
                        aDB.addNewMail(m);
                    }

                });
            } catch (Exception E) {

            }
        }
    }
    
    public void loadItemsSetsFromDatabase(ItemDatabase iDB) {
        HashMap<Integer, ItemSetProfile> profiles = iDB.loadItemSets();
        for (Integer i : profiles.keySet()) {
            Log.debug("loadItemsSetsFromDatabase: reloading ItemSetProfile i:"+i);
            ObjectManagerClient.registerItemSetProfile(profiles.get(i));
            }
    }
    
    
    /**
     * Load Enchant Profiles
     * @param iDB
     */
    public void loadItemEnchantProfileFromDatabase(ItemDatabase iDB) {
        HashMap<Integer, EnchantProfile> profiles = iDB.loadItemEnchantProfiles();
        for (Integer i : profiles.keySet()) {
            Log.debug("loadItemEnchantProfileFromDatabase: reloading EnchantProfile i:"+i);
            ObjectManagerClient.registerEnchantProfile(profiles.get(i));
            }
    }
    /**
     * Loads Quality Settings for enchant:
     * percentage modification cost
     * percentage modification chance
     * @param iDB
     */
    
    public void loadItemQualitySettingsFromDatabase(ItemDatabase iDB) {
        HashMap<Integer, QualityInfo> qualitys = iDB.loadItemQualitySettings();
        for (Integer i : qualitys.keySet()) {
            Log.debug("loadItemQualitySettingsFromDatabase: reloading QualityInfo i:"+i);
            ObjectManagerClient.registerQualityInfo(qualitys.get(i));
            }
    }
  
    
    /**
     * Loads in item templates from the database and registers them.
     * @param iDB
     */
    public void loadItemsFromDatabase(ItemDatabase iDB) {
        ArrayList<Template> items = iDB.loadItemTemplates(storeItems);
        for (Template tmpl: items)
            ObjectManagerClient.registerTemplate(tmpl);
    }
    
    /**
     * Load Vip  Levels
     * @param iDB
     */
    public void loadVipLevelFromDatabase(ItemDatabase iDB) {
        HashMap<Integer, VipLevel> levels = iDB.loadVipLevels();
        for (Integer i : levels.keySet()) {
            Log.debug("loadVipLevelFromDatabase: reloading VipLevel i:"+i);
            ObjectManagerClient.registerVipLevel(levels.get(i));
            }
    }
    
    
  
    protected void ReloadTemplates(Message msg) {
		Log.debug("AgisInventoryPlugin ReloadTemplates Start");
			loadData();
			
			Entity[] objects = EntityManager.getAllEntitiesByNamespace(Namespace.AGISITEM);
			for(Entity e : objects){
				AgisItem ai = (AgisItem)e;
				DevModeReloadParamsOfItems(ai);
			}
			Log.debug("AgisInventoryPlugin ReloadTemplates End");
	}
    
    AgisItem DevModeReloadParamsOfItems(AgisItem ai) {
    	Template itemTemplate = ObjectManagerPlugin.getTemplate(ai.getTemplateID(), ObjectManagerPlugin.ITEM_TEMPLATE);
    	if(Log.loggingDebug)
			Log.debug("DevModeReloadParamsOfItems: templateID:"+ai.getTemplateID()+" itemTemplate:"+itemTemplate);
		
		ai.setName(itemTemplate.getName());
		Map<String, Serializable> objMgrProps = itemTemplate.getSubMap(InventoryClient.ITEM_NAMESPACE);
		if (objMgrProps != null) {
			for (Map.Entry<String, Serializable> entry : objMgrProps.entrySet()) {
				if(Log.loggingDebug)Log.debug("DevModeReloadParamsOfItems "+entry.getKey());
                if(entry.getKey().equals("sockets")) {
                    if(ai.getProperty(entry.getKey())==null) {
                        ai.setProperty(entry.getKey(), entry.getValue());
                    } else{
                        HashMap<Integer,SocketInfo> sockets = (HashMap<Integer,SocketInfo>) ai.getProperty(entry.getKey());
                        HashMap<Integer,SocketInfo> itemSockets = (HashMap<Integer,SocketInfo>) entry.getValue();
                        for(int sid : itemSockets.keySet()){
                            if(sockets.get(sid) == null){
                                if(Log.loggingDebug)Log.debug("DevModeReloadParamsOfItems socket "+sid+" is null");
                                sockets.put(sid, itemSockets.get(sid));
                            }else if(!sockets.get(sid).GetType().equals(itemSockets.get(sid).GetType())){
                                if(Log.loggingDebug)Log.debug("DevModeReloadParamsOfItems socket "+sid+" Type "+sockets.get(sid).GetType()+" != "+itemSockets.get(sid).GetType());
                                sockets.put(sid, itemSockets.get(sid));
                            }
                        }
                        if(sockets.size()>itemSockets.size()){
                            int c = sockets.size();
                            for(int i = itemSockets.size();i < c;i++) {
                                if(Log.loggingDebug)Log.debug("DevModeReloadParamsOfItems socket "+i+" remove");
                                sockets.remove(i);
                            }
                        }
                        ai.setProperty(entry.getKey(), sockets);
                    }
                } else if (!entry.getKey().startsWith(":") && !entry.getKey().equals("enchantLevel") && !entry.getKey().equals("sockets") ) {
					if(Log.loggingDebug)Log.debug("DevModeReloadParamsOfItems change value for key "+entry.getKey()+" from "+ai.getProperty(entry.getKey())+" to "+entry.getValue());
					ai.setProperty(entry.getKey(), entry.getValue());
				}
			}
		}
		
		
	//	ai.setPersistenceFlag(persistent);
	//	ai.setProperty(InventoryPlugin.INVENTORY_PROP_BACKREF_KEY, OID.fromLong(inv_backref));
	//	ai.setProperty("enchantLevel", enchantLevel);
	//	ai.setProperty("durability", durability);
	//	ai.setTemplateID(templateID);
	//	ai.setStackSize(stackSize);
		HashMap<String, Integer> enchantStats = new HashMap<String, Integer>((HashMap<String, Integer>)ai.getProperty("bonusStats"));
		String itemType = (String) ai.getProperty("itemType");
		if (itemType.equals("Weapon")) {
			int damage = (Integer) ai.getProperty("damage");
	    	int damageMax = (Integer) ai.getProperty("damageMax");
	    	if (damageMax < damage) damageMax = damage;
	    	enchantStats.put("dmg-base", damage);
	    	enchantStats.put("dmg-max", damageMax);
		}
		for (String statName: enchantStats.keySet()) {
			int value = enchantStats.get(statName);
			if(Log.loggingDebug)
				Log.debug("DevModeReloadParamsOfItems: item:"+ai+" base stat: " + statName + " by: " + value);
		}

		if (ai.getIntProperty("enchantLevel") > 0) {
			EnchantProfile ep = ObjectManagerPlugin.getEnchantProfile((int) ai.getProperty("enchantProfileId"));
			if (ep.GetLevels().containsKey(ai.getIntProperty("enchantLevel"))) {
				for (int e = 1; e <= ai.getIntProperty("enchantLevel"); e++) {
					enchantStats.replace("gearScore",enchantStats.get("gearScore")+ep.GetLevels().get(e).GetGearScoreValue()+(int)(enchantStats.get("gearScore") * ep.GetLevels().get(e).GetGearScoreValuePercentage()));
					if (ep.GetLevels().get(e).GetAllStats()) {
						for (String stat : enchantStats.keySet()) {
							if (ep.GetLevels().get(e).GetPercentage()) {
								enchantStats.replace(stat, enchantStats.get(stat) + (int)(enchantStats.get(stat) * ep.GetLevels().get(e).GetStatValue()));
							} else {
								enchantStats.replace(stat, enchantStats.get(stat) + (int)ep.GetLevels().get(e).GetStatValue());
							}
						}
					} else {
						HashMap<String, EnchantStat> enchLevStats = ep.GetLevels().get(e).GetStats();
						for (String stat : enchLevStats.keySet()) {
							if (ep.GetLevels().get(e).GetAddNotExist() && !enchantStats.containsKey(stat)) {
								enchantStats.put(stat, enchLevStats.get(stat).GetValue() +  (int)(enchLevStats.get(stat).GetValue() * enchLevStats.get(stat).GetValuePercentage()));
							} else if (enchantStats.containsKey(stat)) {
								enchantStats.replace(stat, enchantStats.get(stat) + enchLevStats.get(stat).GetValue()
										+  (int)((enchantStats.get(stat) + enchLevStats.get(stat).GetValue()) * enchLevStats.get(stat).GetValuePercentage()));
							}/* else if (!ep.GetLevels().get(e).GetAddNotExist() && !enchantStats.containsKey(stat)&&(stat.equals("dmg-base")||stat.equals("dmg-base"))) {
							
								enchantStats.replace(stat, enchantStats.get(stat) + enchLevStats.get(stat).GetValue()
										+ (enchantStats.get(stat) + enchLevStats.get(stat).GetValue()) * enchLevStats.get(stat).GetValuePercentage());
							}*/
						}
					}
				}
			}
		}
		for (String statName: enchantStats.keySet()) {
			int value = enchantStats.get(statName);
			if(Log.loggingDebug)
				Log.debug("DevModeReloadParamsOfItems: item:"+ai+" bnase + enchant  stat: " + statName + " by: " + value);
		}
		ai.setProperty("enchantStats", enchantStats);
		HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) ai.getProperty("sockets");
		Set<Integer> keys = itemSockets.keySet();
        
		if(Log.loggingDebug)
			Log.debug("DevModeReloadParamsOfItems: templateID:"+ai.getTemplateID()+" return AgisItem object: "+ai);
		return ai;
    }

    class SwitchWeaponHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage message = (ExtensionMessage) msg;
            OID playerOid = message.getSubject();
            String _set = (String)message.getProperty("set");
            if( Log.loggingDebug)Log.debug("SwitchWeaponHook: playerOid: " + playerOid + " _switch=" + _set);
            InventoryInfo iInfo = getInventoryInfo(playerOid);
            SlotsSet ss = AgisInventoryPlugin.slotsSets.get(_set);
            if( Log.loggingDebug)Log.debug("SwitchWeaponHook: playerOid: " + playerOid +" "+ AgisInventoryPlugin.slotsSets.keySet()+" ss="+ss);
            if(ss==null){
                Log.warn("SwitchWeaponHook: playerOid: " + playerOid + " _switch=" + _set+" SlotsSet definition is null");
                return true;
            }
            int race = -1;
            int aspect = -1;
            try {
                race = (Integer) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "race");
                aspect = (Integer) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, "aspect");
            }catch (Exception e){

            }
            ArrayList<AgisEquipSlot> slotList = ss.getSlotsForRaceClass(race,aspect);
            if(slotList.size()==0){
                if( Log.loggingDebug) Log.debug("SwitchWeaponHook: playerOid: " + playerOid + " _switch=" + _set+" SlotsSet no slot definition for race "+race+" and class "+aspect);
                return true;
            }
            if(iInfo.getItemSetSelected().equals(_set)){
                if( Log.loggingDebug)Log.debug("SwitchWeaponHook: playerOid: " + playerOid + " _switch=" + _set +" is equal "+iInfo.getItemSetSelected()+" break");
                return true;
            }
            if (iInfo != null) {
                ArrayList<OID> items = new ArrayList<OID>();
                EquipMap equipMap = getEquipMap(playerOid);

                OID equpBagOid = iInfo.getEquipmentItemBag();
                OID newSetBagOid = iInfo.getEquippedItemsSetBag(_set);
                OID oldSetBagOid = iInfo.getEquippedItemsSetBag(iInfo.getItemSetSelected());

                Bag equipBag = getBag(equpBagOid);
                Bag newSetBag = getBag(newSetBagOid);
                Bag oldSetBag = getBag(oldSetBagOid);
                OID[] eitems = equipBag.getItemsList();
                if( Log.loggingDebug)log.debug("SwitchWeaponHook: | equip="+Arrays.toString(eitems));
                for (AgisEquipSlot slot : slotList) {
                    AgisEquipSlot set_slot = AgisEquipSlot.getSlotByName("Set_"+_set + "_" + slot.getName());
                    OID itemOid = equipMap.get(slot);
                    AgisItem item = getAgisItem(itemOid);

                    OID itemOidToSwitch2 = equipBag.getItem(set_slot.getId());
                    OID itemOidToSwitch = newSetBag.getItem(slot.getId());
                    if(itemOidToSwitch==null && itemOidToSwitch2!=null)
                        itemOidToSwitch = itemOidToSwitch2;
                    AgisItem itemToSwitch = getAgisItem(itemOidToSwitch);

                    if( Log.loggingDebug)Log.debug("SwitchWeaponHook: slot="+slot+" itemOid="+itemOid+" item="+(item!=null?"not null":"null")+" itemOidToSwitch="+itemOidToSwitch+" itemToSwitch="+(itemToSwitch!=null?"not null":"null")+" set_slot="+set_slot);
                    eitems = equipBag.getItemsList();
                    if( Log.loggingDebug) log.debug("SwitchWeaponHook: || equip="+Arrays.toString(eitems));
                    if(itemOidToSwitch == null){
                        if( Log.loggingDebug)Log.debug("SwitchWeaponHook: slot="+slot+" removeItem slot id="+slot.getId());
                        equipBag.removeItem(slot.getId());
                        equipMap.remove(slot);
                          //  removeEquippedItem(playerOid,itemOid, slot);
                    }else {
                        if( Log.loggingDebug)Log.debug("SwitchWeaponHook: slot="+slot+" placeEquippedItem itemOidToSwitch="+itemOidToSwitch);
                        equipBag.removeItem(slot.getId());
                        placeEquippedItem(playerOid, itemOidToSwitch, slot, -1);
                        equipMap.put(slot, itemOidToSwitch);
                    }
                    eitems = equipBag.getItemsList();
                    if( Log.loggingDebug)log.debug("SwitchWeaponHook: ||| equip="+Arrays.toString(eitems));
                    String displayVal = null;
                    if (itemToSwitch != null)
                        displayVal = (String) itemToSwitch.getProperty("displayVal");
                    HashMap<Integer, Integer> itemSetslist = new HashMap<Integer, Integer>();
                    itemSetslist = getEquipSetsInfo(iInfo.getOid());
                    if(itemToSwitch != null || item != null) {
                        AgisInventoryClient.itemEquipStatusChanged(playerOid, itemToSwitch, item, slot.toString(), itemSetslist);
                    }
                    EquipHelper.updateDisplay(playerOid, displayVal, slot);
                    iInfo.setItemSetSelected(_set);
                }
                eitems = equipBag.getItemsList();
                if( Log.loggingDebug)log.debug("SwitchWeaponHook: |V equip="+Arrays.toString(eitems));
                if( Log.loggingDebug)Log.debug("SwitchWeaponHook: sendEquippedInvUpdate");
                sendInvUpdate(playerOid);
                //sendEquippedInvUpdate(playerOid);
//
//                for (AgisEquipSlot slot : slots) {
//                    OID itemOid = equipMap.get(slot);
//                    if (itemOid != null) {
//                        items.add(itemOid);
//                    }
//                }
//                ArrayList<String> effects = new ArrayList<>();
//                int effectTime = 0;
//                if (items.size() == 0) {
//                    //Log.error("No items to Draw");
//                    return true;
//                }



//                for (OID itemOid : items) {
//                    AgisItem item = getAgisItem(itemOid);
//                    if (item != null) {
//                        String e = (String) item.getProperty("holsteringWeaponEffect");
//                        int eT = (int) item.getProperty("holsteringWeaponTime");
//                        if (draw) {
//                            e = (String) item.getProperty("drawWeaponEffect");
//                            eT = (int) item.getProperty("drawWeaponTime");
//                        }
//                        if (effectTime < eT)
//                            effectTime = eT;
//                        effects.add(e);
//                    } else {
//                        Log.debug("ClientDrawWeaponHook: playerOid: " + playerOid + " item " + itemOid + " is null");
//                    }
//                }
//                EnginePlugin.setObjectProperty(playerOid, Namespace.COMBAT, "weaponIsDrawing", true);



            } else {
                if( Log.loggingDebug)Log.debug("SwitchWeaponHook: playerOid: " + playerOid + " InventoryInfo is null");
            }
            if( Log.loggingDebug)Log.debug("SwitchWeaponHook: playerOid: " + playerOid + " End");
            return true;
        }
    }

    class ClientDrawWeaponHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage message = (ExtensionMessage) msg;
            OID playerOid = message.getSubject();
            boolean draw = (boolean) message.getProperty("draw");
            if (Log.loggingDebug)
                log.debug("ClientDrawWeaponHook: playerOid: " + playerOid + " draw=" + draw);
            InventoryInfo iInfo = getInventoryInfo(playerOid);

            if (iInfo != null) {
                EquipMap equipMap = getEquipMap(playerOid);
                List<AgisEquipSlot> slots = AgisEquipSlot.getSlotByType("Weapon");
                ArrayList<OID> items = new ArrayList<OID>();
                for (AgisEquipSlot slot : slots) {
                    OID itemOid = equipMap.get(slot);
                    if (itemOid != null) {
                        items.add(itemOid);
                    }
                }
                ArrayList<String> effects = new ArrayList<>();
                int effectTime = 0;
                if (items.size() == 0) {
                    Log.warn("No items to Draw");
                    return true;
                }
                for (OID itemOid : items) {
                    AgisItem item = getAgisItem(itemOid);
                    if (item != null) {
                        HashMap<String,DrawWeaponInfo> drawData = (HashMap<String,DrawWeaponInfo>) item.getProperty("drawData");
                        if (Log.loggingDebug)
                            log.debug("ClientDrawWeaponHook "+drawData+" item "+item.getName());
                        AgisEquipSlot slot = equipMap.getSlot(itemOid);
                        if (Log.loggingDebug)
                            log.debug("ClientDrawWeaponHook  slot="+slot);
                        if(slot!=null) {
                            DrawWeaponInfo dwi = drawData.get(slot.getName());
                            if (Log.loggingDebug)
                                log.debug("ClientDrawWeaponHook  dwi=" + dwi);

                            if (dwi != null) {
                                String e = dwi.GetHolsteringWeaponEffect();
                                int eT = dwi.GetHolsteringWeaponTime();
                                if (draw) {
                                    e = dwi.GetDrawWeaponEffect();
                                    eT = dwi.GetDrawWeaponTime();
                                }


//                        String e = (String) item.getProperty("holsteringWeaponEffect");
//                        int eT = (int) item.getProperty("holsteringWeaponTime");
//                        if (draw) {
//                            e = (String) item.getProperty("drawWeaponEffect");
//                            eT = (int) item.getProperty("drawWeaponTime");
//                        }
                                if (effectTime < eT)
                                    effectTime = eT;
                                effects.add(e);
                            } else {
                                if (Log.loggingDebug)
                                    log.debug("ClientDrawWeaponHook  dwi is null");
                            }
                        }
                    } else {
                        if (Log.loggingDebug)
                            log.debug("ClientDrawWeaponHook: playerOid: " + playerOid + " item " + itemOid + " is null");
                    }
                }
                EnginePlugin.setObjectProperty(playerOid, Namespace.COMBAT, "weaponIsDrawing", true);

                if (draw) {
                    DrawWeaponTask dwt = new DrawWeaponTask(playerOid);
                    ScheduledFuture<?> schedule = Engine.getExecutor().schedule(dwt, effectTime, TimeUnit.MILLISECONDS);
                } else {
                    HolsteringWeaponTask dwt = new HolsteringWeaponTask(playerOid);
                    ScheduledFuture<?> schedule = Engine.getExecutor().schedule(dwt, effectTime, TimeUnit.MILLISECONDS);
                }
                if (Log.loggingDebug)
                    log.debug("ClientDrawWeaponHook: item is null, effect=" + effects + " effectTime=" + effectTime);
                for (String effect : effects) {
                    CoordinatedEffect cE = new CoordinatedEffect(effect);
                    cE.sendSourceOid(true);
                    cE.sendTargetOid(true);
                    cE.putArgument("time", effectTime);
                    cE.invoke(playerOid, playerOid);
                }

            } else {
                if (Log.loggingDebug)
                    log.debug("ClientDrawWeaponHook: playerOid: " + playerOid + " InventoryInfo is null");
            }
            if (Log.loggingDebug)
                log.debug("ClientDrawWeaponHook: playerOid: " + playerOid + " End");
            return true;
        }
    }

    public class HolsteringWeaponTask implements Runnable {
        OID playerOid;
        public HolsteringWeaponTask(OID playerOid ) {
            this.playerOid = playerOid;
        }

        @Override
        public void run() {
            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("weaponIsDrawn",false);
            props.put("weaponIsDrawing",false);
            EnginePlugin.setObjectProperties(playerOid, CombatClient.NAMESPACE, props);
        }
    }
    public class DrawWeaponTask implements Runnable {
        OID playerOid;
        public DrawWeaponTask(OID playerOid ) {
            this.playerOid = playerOid;
        }

        @Override
        public void run() {
            if (Log.loggingDebug)
                log.debug("DrawWeaponTask: playerOid: " + playerOid +" start");
            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("weaponIsDrawn",true);
            props.put("weaponIsDrawing",false);
            EnginePlugin.setObjectProperties(playerOid, CombatClient.NAMESPACE, props);
            if (Log.loggingDebug)
                log.debug("DrawWeaponTask: playerOid: " + playerOid + " end" );
        }
    }
    class DrawWeaponHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.DrawWeaponMessage message = (AgisInventoryClient.DrawWeaponMessage) msg;
            OID playerOid = message.getSubject();
            ArrayList<String> weaponReq = message.getWeaponReq();
            HashMap<String, Serializable> responseData = new HashMap<String, Serializable>();
            Log.debug("DrawWeaponHook: playerOid: " + playerOid + " " + weaponReq);
            InventoryInfo iInfo = getInventoryInfo(playerOid);
           // EnginePlugin.setObjectProperty(playerOid, Namespace.COMBAT, "weaponIsDrawing", true);
            if (iInfo != null) {
                EquipMap equipMap = getEquipMap(playerOid);
                List<AgisEquipSlot> slots = AgisEquipSlot.getSlotByType("Weapon");
                ArrayList<OID> items = new ArrayList<OID>();
                for (AgisEquipSlot slot : slots) {
                    OID itemOid = equipMap.get(slot);
                    if (itemOid != null) {
                        items.add(itemOid);
                    }
                }
                ArrayList<String> effects = new ArrayList<>();
                int effectTime = 0;
                if (items.size() == 0) {
                    Log.warn("No items to Draw");
                    responseData.put("error", "No items to Draw");
                    Engine.getAgent().sendObjectResponse(message, responseData);
                    return true;
                }
                for (OID itemOid : items) {
                    AgisItem item = getAgisItem(itemOid);
                    if (item != null) {
                        HashMap<String,DrawWeaponInfo> drawData = (HashMap<String,DrawWeaponInfo>) item.getProperty("drawData");
                        AgisEquipSlot slot = equipMap.getSlot(itemOid);
                        if(slot!=null) {
                            DrawWeaponInfo dwi = drawData.get(slot.getName());
                            if (dwi != null) {
                                String e = dwi.GetDrawWeaponEffect();
                                int eT = dwi.GetDrawWeaponTime();
                                if (effectTime < eT)
                                    effectTime = eT;
                                effects.add(e);
                            }
                        }
                    } else {
                        Log.debug("ClientDrawWeaponHook: playerOid: " + playerOid + " item " + itemOid + " is null");
                    }
                }
                EnginePlugin.setObjectProperty(playerOid, Namespace.COMBAT, "weaponIsDrawing", true);

                //  if (draw) {
                responseData.put("time", (long)effectTime);
                DrawWeaponTask dwt = new DrawWeaponTask(playerOid);
                ScheduledFuture<?> schedule = Engine.getExecutor().schedule(dwt, effectTime, TimeUnit.MILLISECONDS);
//                } else {
//                    HolsteringWeaponTask dwt = new HolsteringWeaponTask(playerOid);
//                    ScheduledFuture<?> schedule = Engine.getExecutor().schedule(dwt, effectTime, TimeUnit.MILLISECONDS);
//                }
                if (Log.loggingDebug)
                    log.debug("ClientDrawWeaponHook: effect=" + effects + " effectTime=" + effectTime);
                for (String effect : effects) {
                    CoordinatedEffect cE = new CoordinatedEffect(effect);
                    cE.sendSourceOid(true);
                    cE.sendTargetOid(true);
                    cE.putArgument("time", effectTime);
                    cE.invoke(playerOid, playerOid);
                }
                Engine.getAgent().sendObjectResponse(message, responseData);
            } else {
                responseData.put("error", "InventoryInfo is null");
                if (Log.loggingDebug)
                    log.debug("DrawWeaponHook: playerOid: " + playerOid + " InventoryInfo is null");
                Engine.getAgent().sendObjectResponse(message, responseData);
            }
            if (Log.loggingDebug)
                log.debug("DrawWeaponHook: playerOid: " + playerOid + " End");
            return true;
        }
    }




    /**
     * Hook to Update Player Bonuses 
     *
     */
    class EquipItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	equipItemMessage message = (equipItemMessage) msg;
            OID playerOid = message.getSubject();
            OID itemOid = (OID)message.getProperty("itemOid");
            if (Log.loggingDebug)
                log.debug("EquipItemHook: playerOid: " + playerOid +" "+itemOid);
            InventoryInfo iInfo = getInventoryInfo(playerOid);
            if(iInfo!=null) {
            	AgisItem item = getAgisItem(itemOid);
                if (item == null) {
                    Log.warn("EquipItemInSlotHook: item is null, oid=" + itemOid);
                    Engine.getAgent().sendBooleanResponse(message, false);
                    return true;
                }
            	boolean rv = equipItem(item, playerOid, true);
            	Engine.getAgent().sendBooleanResponse(message, rv);
            } else {
                if (Log.loggingDebug)
                    log.debug("EquipItemHook: playerOid: " + playerOid+" InventoryInfo is null");
                Engine.getAgent().sendBooleanResponse(message, false);
            }
            if (Log.loggingDebug)
                log.debug("EquipItemHook: playerOid: " + playerOid+" End");
            return true;
        }
    }
    
    
    
    /**
     * Hook to Update Player Bonuses 
     *
     */
    class UnequipItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	unequipItemMessage message = (unequipItemMessage) msg;
            OID playerOid = message.getSubject();
            OID itemOid = (OID)message.getProperty("itemOid");
            Log.debug("UnequipItemHook: playerOid: " + playerOid +" "+itemOid);
            InventoryInfo iInfo = getInventoryInfo(playerOid);
            if(iInfo!=null) {
            	AgisItem item = getAgisItem(itemOid);
                if (item == null) {
                    Log.warn("UnequipItemHook: item is null, oid=" + itemOid);
                    Engine.getAgent().sendBooleanResponse(message, false);
                    return true;
                }
            	boolean rv = unequipItem(item, playerOid, false);
            	Engine.getAgent().sendBooleanResponse(message, rv);
            } else {
                Log.debug("UnequipItemHook: playerOid: " + playerOid+" InventoryInfo is null");
                Engine.getAgent().sendBooleanResponse(message, false);
            }
            Log.debug("UnequipItemHook: playerOid: " + playerOid+" End");
            return true;
        }
    }
    
    /**
     * Hook to Update Global Events Bonuses 
     *
     */
    
    class GlobalEventBonusesUpdateHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            GlobalEventBonusesUpdateMessage message = (GlobalEventBonusesUpdateMessage) msg;
            //OID playerOid = message.getSubject();
            Log.debug("GlobalEventBonusesUpdateHook: "+message.getBonuses());
            globalEventBonusesArray.clear();
            globalEventBonusesArray.putAll(message.getBonuses());

            
            Log.debug("GlobalEventBonusesUpdateHook:  End");
            return true;
        }
    }
    
    /**
     * Hook to Update Player Bonuses 
     *
     */
    class BonusesUpdateHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            BonusesUpdateMessage message = (BonusesUpdateMessage) msg;
            OID playerOid = message.getSubject();
            Log.debug("BonusesUpdateHook: playerOid: " + playerOid +" "+message.getBonuses());
            InventoryInfo iInfo = getInventoryInfo(playerOid);
            if(iInfo!=null)
                iInfo.setBonuses(message.getBonuses());
            else
                Log.debug("BonusesUpdateHook: playerOid: " + playerOid+" InventoryInfo is null");
            Log.debug("BonusesUpdateHook: playerOid: " + playerOid+" End");
            return true;
        }
    }
    
    class GetEquipItemsHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            getEquipItemListMessage message = (getEquipItemListMessage) msg;
            OID playerOid = message.getSubject();
            Log.debug("GetEquipItemsHook: playerOid: " + playerOid );
            InventoryInfo iInfo = getInventoryInfo(playerOid);
            if (iInfo != null) {
                OID equipBag = iInfo.getEquipmentItemBag();
                if (equipBag != null) {
                    Bag bag = getBag(equipBag);
                    ArrayList<OID> _list = bag.getItemsListArray();
                    HashMap<OID,AgisItem> list = new HashMap<OID,AgisItem>();
                    for(OID oid : _list) {
                        if(oid!=null) {
                            AgisItem ai = getAgisItem(oid);
                            list.put(oid, ai);
                        }
                    }
                    Engine.getAgent().sendObjectResponse(message, list);
                } else {
                    Engine.getAgent().sendObjectResponse(message, new HashMap<OID,AgisItem>());
                }
            } else
                Log.debug("GetEquipItemsHook: playerOid: " + playerOid + " InventoryInfo is null");
            Log.debug("GetEquipItemsHook: playerOid: " + playerOid + " End");
            return true;
        }
    }
    
    // Log the logout information and send a response
    class GetItem implements Hook {
        public boolean processMessage(Message msg, int flags) {
            getItemMessage message = (getItemMessage) msg;
            Log.debug("GetItem: keys: "+message.getPropertyMapRef().keySet());
            Log.debug("GetItem: prop:  "+message.getPropertyMapRef().get("itemOid"));
              OID itemOid = (OID)message.getPropertyMapRef().get("itemOid");
            Log.debug("GetItem: ItemOid: " + itemOid);
            AgisItem item = AgisInventoryPlugin.getAgisItem(itemOid);
            Engine.getAgent().sendObjectResponse(message, item);
            Log.debug("GetItem:  finished ItemOid: " + itemOid);
            return true;
        }
    }
    
    class AlterItemCount implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage message = (ExtensionMessage) msg;
            OID playerOid = message.getSubject();
            Log.debug("AlterItemCount: keys: "+message.getPropertyMapRef().keySet());
            Log.debug("AlterItemCount: prop:  "+message.getPropertyMapRef().values());
            OID itemOid = (OID)message.getPropertyMapRef().get("itemOid");
            int count = (int)message.getPropertyMapRef().get("count");
            Log.debug("AlterItemCount: playerOid:"+playerOid+" ItemOid: " + itemOid+" count:"+count);
            AgisItem item = AgisInventoryPlugin.getAgisItem(itemOid);
            Log.debug("AlterItemCount: playerOid:"+playerOid+" ItemOid: " + itemOid+" stack:"+item.getStackSize());
               item.alterStackSize(playerOid, count);
               Log.debug("AlterItemCount: after playerOid:"+playerOid+" ItemOid: " + itemOid+" stack:"+item.getStackSize());
               //  AgisInventoryClient.itemAcquiredStatusChange(playerOid, item, true);
            ObjectManagerClient.saveObject(itemOid);
            Engine.getPersistenceManager().setDirty(item);
            Engine.getAgent().sendBooleanResponse(message, true);
            Log.debug("AlterItemCount:  finished ItemOid: " + itemOid);
            return true;
        }
    }
    

    class SetItemProperty implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage message = (ExtensionMessage) msg;
            OID playerOid = message.getSubject();
            Log.debug("SetItemProperty: keys: "+message.getPropertyMapRef().keySet());
            Log.debug("SetItemProperty: prop:  "+message.getPropertyMapRef().values());
            OID itemOid = (OID)message.getPropertyMapRef().get("itemOid");
            String param = (String)message.getPropertyMapRef().get("param");
            Log.debug("SetItemProperty: ItemOid: " + itemOid);
            AgisItem item = AgisInventoryPlugin.getAgisItem(itemOid);
            item.setProperty(param, message.getPropertyMapRef().get("Object"));
            AgisInventoryClient.itemAcquiredStatusChange(playerOid, item, true);
            //ObjectManagerClient.saveObject(itemOid);
            Engine.getPersistenceManager().setDirty(item);
            Engine.getAgent().sendBooleanResponse(message, true);
            Log.debug("SetItemProperty:  finished ItemOid: " + itemOid);
            return true;
        }
    }
    
    class GetItemsWithParamHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage message = (ExtensionMessage)msg;
            log.debug("GetItemsWithParamHook");
            OID mobOid = message.getSubject();
            int templateId = (Integer) message.getProperty("templateId");
            int enchantLevel = (Integer) message.getProperty("enchantLevel");
            String sockets = (String) message.getProperty("sockets");
            String itemGroup = (String) message.getProperty("itemGroup");
                
            String itemString = "";
            if (itemGroup.length() == 0) {
                itemString = templateId + "";
                if (enchantLevel > 0)
                    itemString += "_E" + enchantLevel;
                itemString += sockets;
            } else {
                itemString = itemGroup;
            }
            //Log.debug("ITEM: got move item message with containerId: " + containerId + " and slot: " + slotId + " for item: " + itemOid);
            HashMap<String,ArrayList<OID>> groupedItems = new HashMap<String,ArrayList<OID>>();
            HashMap<OID,Integer> ItemsCount = new HashMap<OID,Integer>();
            
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            if (iInfo == null ) {
                log.debug("GetItemsWithParamHook:  InventoryInfo is null");
                return true;
            }
            OID[] subBags = iInfo.getBags();
            for (OID bag : subBags) {
                Bag subBag = getBag(bag);
                OID[] itemsInBag = subBag.getItemsList();
                log.debug("GetItemsWithParamHook:  subBagOid:" + bag + " subBags:" + Arrays.toString(subBags) + " itemsInBag:" + Arrays.toString(itemsInBag));
                for (OID ite : itemsInBag) {
                    AgisItem Item = getAgisItem(ite);
                    log.debug("GetItemsWithParamHook: ite:"+ite+" Item :"+Item);
                        if (Item != null) {
                        String unicItem = Item.getTemplateID() + "";
                        if ((int) Item.getProperty("enchantLevel") > 0)
                            unicItem += "_E" + Item.getProperty("enchantLevel");
                        HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) Item.getProperty("sockets");
                        ArrayList<Long> socketItems = new ArrayList<Long>();
                        for (Integer sId : itemSockets.keySet()) {
                            if (itemSockets.get(sId).GetItemOid() != null) {
                                socketItems.add(itemSockets.get(sId).GetItemOid().toLong());
                            }
                        }
                        //Collections.sort(socketItems);
                        for (Long l : socketItems) {
                            unicItem += "_S" + l;
                        }
                        ItemsCount.put(ite, Item.getStackSize());
                        if (groupedItems.containsKey(unicItem)) {
                            groupedItems.get(unicItem).add(ite);
                        } else {
                            ArrayList<OID> list = new ArrayList<OID>();
                            list.add(ite);
                            groupedItems.put(unicItem, list);
                        }
                    }else {
                        log.debug("GetItemsWithParamHook:Item is null");
                    }
                }
            }
            log.debug("GetItemsWithParamHook: groupedItems:" + groupedItems + " ItemsCount:" + ItemsCount);
            // if (groupedItems.containsKey(itemString))
            Engine.getAgent().sendObjectResponse(message, groupedItems.get(itemString));

            return true;
        }
    }
   
    /**
     * Loads the items from the database in again. Not tested recently.
     * @author Andrew Harrison
     *
     */
    class ReloadItemsHook implements Hook {
        public boolean processMessage(atavism.msgsys.Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage)msg;
            Log.debug("RELOAD: reloading items from database");
            ItemDatabase iDB = new ItemDatabase(false);
            loadItemsSetsFromDatabase(iDB);
            loadItemsFromDatabase(iDB);
            loadItemEnchantProfileFromDatabase(iDB);
            loadItemQualitySettingsFromDatabase(iDB);
            iDB.close();
            OID objOid = eMsg.getSubject();
            ChatClient.sendObjChatMsg(objOid, 2, "Reloading items from the database.");
            return true;
        }
    }
    
    /**
     * Loads in the Loot Tables from the database and registers them
     * in the manager.
     * @param cDB
     */
    private void loadLootTables(ContentDatabase cDB) {
        HashMap<Integer, LootTable> lootTables = cDB.loadLootTables(-1);
        for (LootTable lTbl: lootTables.values()) {
            Agis.LootTableManager.register(lTbl.getID(), lTbl);
            Log.debug("LOOT: loaded loot Table: [" + lTbl.getName() + "]");
        }
    }
    
    /**
     * Loads in Currencies from the Database and registers them
     * in the manager.
     * @param mDB
     */
    private void loadCurrencies(MobDatabase mDB) {
        ArrayList<Currency> currencies = mDB.loadCurrencies(-1);
        for (Currency currency: currencies) {
            Agis.CurrencyManager.register(currency.getCurrencyID(), currency);
            Log.debug("CURRENCY: currency Table: [" + currency.getCurrencyID() + ":" + currency.getCurrencyName() + "]");
        }
    }

    /**
     * Returns the AgisItem Entity for the specified OID.
     * 
     * @param oid
     * @return
     */
    public static AgisItem getAgisItem(OID oid) {
        return getAgisItem(oid, true);
    }

    /**
     * Returns the AgisItem Entity for the specified OID.
     * @param oid
     * @param onFaliedLoad
     * @return
     */
    public static AgisItem getAgisItem(OID oid, boolean onFaliedLoad) {
        AgisItem ai = (AgisItem) EntityManager.getEntityByNamespace(oid, Namespace.AGISITEM);
        if (oid != null && ai == null && onFaliedLoad) {
            if (ObjectManagerClient.loadObject(oid) == null) {
                return null;
            } else {
                ai = (AgisItem) EntityManager.getEntityByNamespace(oid, Namespace.AGISITEM);
            }
        }
        return ai;

    }
    
    /**
     * Registers the AgisItem in the Item Namespace. Used when creating
     * the AgisItem SubObject.
     * @param item
     */
    public static void registerAgisItem(AgisItem item) {
        EntityManager.registerEntityByNamespace(item, Namespace.AGISITEM);
    }
    
    /**
     * Returns the InventoryInfo Entity for the specified OID. The InventoryInfo
     * contains the information about a player/mobs inventory such as what bags
     * and items they have.
     * @param oid
     * @return
     */
    public static InventoryInfo getInventoryInfo(OID oid) {
        return (InventoryInfo)EntityManager.getEntityByNamespace(oid, Namespace.BAG);
    }

    /**
     * Return property from Entity  for the specified OID stored Local EntityManager
     * @param oid
     * @param ns
     * @param key
     * @return
     */
    Serializable getLocalObjectProperty(OID oid, Namespace ns, String key){
        Entity e = EntityManager.getEntityByNamespace(oid, ns);
        if (e == null) {
            return null;
        }
        return e.getProperty(key);
    }

    /**
     * Set property for Entity for the specified OID stored Local EntityManager
     * @param oid
     * @param ns
     * @param key
     * @return
     */
    boolean setLocalObjectProperty(OID oid, Namespace ns, String key, Serializable value){
        Entity e = EntityManager.getEntityByNamespace(oid, ns);
        if (e == null) {
            return false;
        }
        e.setProperty(key,value);
        return true;
    }

    /**
     * Registers the InventoryInfo in the Item Namespace. Used when creating
     * the InventoryInfo SubObject for a player/mob.
     * @param iInfo
     */
    public static void registerInventoryInfo(InventoryInfo iInfo) {
        EntityManager.registerEntityByNamespace(iInfo, Namespace.BAG);
    }
    
    /**
     * Returns the Bag Entity for the specified OID.
     * @param oid
     * @return
     */
    public static Bag getBag(OID oid) {
        Bag b = (Bag) EntityManager.getEntityByNamespace(oid, Namespace.BAG);
        if (oid != null && b == null) {
            b = (Bag) Engine.getDatabase().loadEntity(oid, Namespace.BAG);
            if (b != null) {
                registerBag(b);
            }
        }
        return b;
    }

    /**
     * Registers the Bag Entity in the Bag Namespace. Used when a new
     * Bag is being created.
     * @param bag
     */
    public static void registerBag(Bag bag) {
        EntityManager.registerEntityByNamespace(bag, Namespace.BAG);
    }
    
    /**
     * Creates the InventoryInfo for the for the specified player/mob. 
     * Takes in a template as well, applying the relevant properties from
     * the template to the InventoryInfo.
     * returns the bag, or null on failure
     */
    public SubObjData createInvSubObj(OID mobOid, Template template) {
        //long startTime = System.currentTimeMillis();
        InventoryInfo iInfo = new InventoryInfo(mobOid);
        iInfo.setName(template.getName());
        iInfo.setCurrentCategory(1);
        Map<String, Serializable> props = template.getSubMap(Namespace.BAG);
        if (props == null) {
            Log.warn("createInvSubObj: no props in ns " + Namespace.BAG);
            return null;
        }

        Boolean persistent = (Boolean) template.get(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT);
        if (persistent == null)
            persistent = false;

        iInfo.setPersistenceFlag(persistent);

        // copy properties from template to object
        for (Map.Entry<String, Serializable> entry : props.entrySet()) {
            String key = entry.getKey();
            Serializable value = entry.getValue();
            if (!key.startsWith(":")) {
                iInfo.setProperty(key, value);
            }
        }
        //log.error("createInvSubObj: I Name="+template.getName()+" time="+(System.currentTimeMillis() - startTime)+"ms");
        
        // register the entity
        registerInventoryInfo(iInfo);
        //log.error("createInvSubObj: II Name="+template.getName()+" time="+(System.currentTimeMillis() - startTime)+"ms");

        // Create the bags
        if(Log.loggingDebug) Log.debug("INV: template name: " + template.getName());
        if (template.getSubMap(InventoryClient.NAMESPACE).containsKey("isPlayer")) {
            Log.debug("INV: creating bags for player");
            createBags(iInfo, mobOid, true);

            int race = -1;
            int aspect = -1;
            try {
                race = (Integer) template.getSubMap(InventoryClient.NAMESPACE).get(":race");
                aspect = (Integer) template.getSubMap(InventoryClient.NAMESPACE).get(":aspect");
            } catch (Exception e) {

            }
            List<String> sets_name = new ArrayList<>();
            for (String s : AgisInventoryPlugin.slotsSets.keySet()) {
                SlotsSet ss = AgisInventoryPlugin.slotsSets.get(s);
                if (Log.loggingDebug)
                    Log.debug("createInvSubObj: playerOid: " + mobOid + " Slot sets " + AgisInventoryPlugin.slotsSets.keySet() + " ss=" + ss);
                if (ss != null) {
                    ArrayList<AgisEquipSlot> setSlotList = ss.getSlotsForRaceClass(race, aspect);
                    if (setSlotList.size() > 0) {
                        if (iInfo.getItemSetSelected() == null || (iInfo.getItemSetSelected() != null && iInfo.getItemSetSelected().isEmpty())) {
                            iInfo.setItemSetSelected(s);
                        }
                        sets_name.add(s);
                    }
                }
            }
            for (String set : sets_name) {
                if (iInfo.getEquippedItemsSetBag(set) == null) {
                    Bag subBag = createSubBag(iInfo.getOid(), Bag.BAG_USE_TYPE_EQUIPPED, EQUIP_SLOTS_COUNT ,"Set_"+sets_name);
                    if (subBag != null) {
                        iInfo.addEquippedItemsSetBag(set, subBag.getOid());
                    }
                }
            }
        } else {
            if(Log.loggingDebug)  Log.debug("INV: creating bags for mob");
            if (template.getSubMap(InventoryClient.NAMESPACE).containsKey("pet")){
                if(Log.loggingDebug) Log.debug("INV: creating bags for pet ");
                Serializable _petOwner = template.getSubMap(InventoryClient.NAMESPACE).get("petOwner");
                if(Log.loggingDebug)Log.debug("INV: creating bags for pet owner "+_petOwner);
                Serializable _petProfile = template.getSubMap(InventoryClient.NAMESPACE).get("petProfile");
                if(Log.loggingDebug) Log.debug("INV: creating bags for pet Profile "+_petProfile);
                OID petOwner = (OID) _petOwner;
                int petProfile = (int) _petProfile;
                InventoryInfo oInfo = getInventoryInfo(petOwner);
                oInfo.getPetInventory().computeIfAbsent(petProfile,__->{
                    PetInventoryInfo pInfo = new PetInventoryInfo(petOwner, petProfile);
                    createPetEquipment(pInfo);
                    Engine.getPersistenceManager().setDirty(oInfo);
                    pInfo.activate();
                    return pInfo;
                }).addPet(mobOid);

                updatePetList(oInfo);

            }else{
                if(Log.loggingDebug) Log.debug("INV: creating bags for not pet ");
            }
            createBags(iInfo, mobOid, false);
        }
        //log.error("createInvSubObj: III Name="+template.getName()+" time="+(System.currentTimeMillis() - startTime)+"ms");

        // Add any currencies the user doesn't know about yet
        for (int currencyID : Agis.CurrencyManager.keyList()) {
            if (!iInfo.getCurrentCurrencies().containsKey(currencyID)) {
                iInfo.addCurrency(currencyID, 0l);
            }
        }
        //log.error("createInvSubObj: IV Name="+template.getName()+" time="+(System.currentTimeMillis() - startTime)+"ms");

        if (persistent) {
            Engine.getPersistenceManager().persistEntity(iInfo);
        }
        if(Log.loggingDebug) log.debug("InventoryInfo: loaded for ply="+mobOid);

        String invItems = (String) props.get(InventoryClient.TEMPL_ITEMS);
        return new SubObjData(Namespace.WORLD_MANAGER, new CreateInventoryHook(mobOid, invItems));
     }

public static void updatePetList(OID ownerOid) {
    InventoryInfo iInfo = getInventoryInfo(ownerOid);
    updatePetList(iInfo);
}

   public static void updatePetList(InventoryInfo iInfo) {
         Map<String, Serializable> props = new HashMap<String, Serializable>();
         props.put("ext_msg_subtype", "petListUpdate");
         int p = 0;
         for (PetInventoryInfo pii : iInfo.getPetInventory().values()) {
             int o = 0;
             props.put("p" + p, pii.getPetProfile());
             for (OID oid : pii.getPets()) {
                 props.put("p" + p + "m" + o, oid.toLong());
                 o++;
             }
             props.put("p" + p + "num", o);
             p++;
         }
         props.put("num", p);

         if(Log.loggingDebug)Log.debug("updatePetList:  "+props);

         WorldManagerClient.TargetedExtensionMessage msg = new WorldManagerClient.TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, iInfo.getOid(), iInfo.getOid(), false, props);
         Engine.getAgent().sendBroadcast(msg);
     }

    /**
     * Creates a new set of Bags and inventory to be placed in an existing
     * InventoryInfo.
     * @param iInfo
     */
    protected void createPetEquipment(PetInventoryInfo iInfo  ) {
        if (Log.loggingDebug)
            log.debug("createPetEquipment: creating sub bag, owner=" + iInfo.getOwnerOid() );    iInfo.setEquipMap(new EquipMap());
        // Create an equipped items bag

        Bag subBag = createSubBag(null, Bag.BAG_USE_TYPE_EQUIPPED, EQUIP_SLOTS_COUNT,"MainEquip");
        if (subBag == null) {
            return;
        }
        iInfo.setEquipmentItemBag(subBag.getOid());
        AtavismDatabase atDb = new AtavismDatabase();
        atDb.savePlayerPetEquip(iInfo.getOwnerOid(), iInfo.getPetProfile(), subBag.getOid());
    }

    /**
     * Creates a new set of Bags and inventory to be placed in an existing 
     * InventoryInfo.
     * @param iInfo
     * @param mobOid
     */
    protected void createBags(InventoryInfo iInfo, OID mobOid, boolean isPlayer) {
        OID[] bags;
        if (isPlayer) {
            bags = new OID[INVENTORY_BAG_COUNT];
        } else {
            bags = new OID[1];
        }
        // create the first sub bag separately
        Bag subBag = createSubBag(iInfo.getOid(), Bag.BAG_USE_TYPE_STANDARD, INVENTORY_FIRST_BAG_SIZE, "Bag");
        if (subBag == null) {
            return;
        }
        bags[0] = subBag.getOid();
        if (isPlayer) {
            for (int subBagNum = 1; subBagNum < INVENTORY_BAG_COUNT; subBagNum++) {
                if (Log.loggingDebug)
                    log.debug("createInvSubObj: creating sub bag, moboid=" + mobOid + ", bag pos=" + subBagNum);
                subBag = createSubBag(iInfo.getOid(), Bag.BAG_USE_TYPE_STANDARD, INVENTORY_OTHER_BAG_SIZE,"Bag");
                if (subBag == null) {
                    return;
                }
                bags[subBagNum] = subBag.getOid();
            }
        }
        iInfo.setBags(bags);
        
        // Create an equipped items bag
        if (Log.loggingDebug)
            log.debug("createInvSubObj: creating sub bag, moboid=" + mobOid
                      + ", bag pos=" + INVENTORY_BAG_COUNT);
        subBag = createSubBag(iInfo.getOid(), Bag.BAG_USE_TYPE_EQUIPPED, EQUIP_SLOTS_COUNT,"MainEquip");
        if (subBag == null) {
            return;
        }
        iInfo.setEquipmentItemBag(subBag.getOid());



        // Create bank storage bags
        if (isPlayer && BANK_SLOTS_COUNT > 0) {
            subBag = createSubBag(iInfo.getOid(), Bag.BAG_USE_TYPE_BANK, BANK_SLOTS_COUNT,bankBagKey + "0");
            if (subBag == null) {
                return;
            }
            iInfo.addStorageBag(bankBagKey + "0", subBag.getOid());
        }
    }
    
    /**
     * Creates a Bag that sits inside another Bag. A message isn't sent to the
     * worldmgr to set the forward reference.
     * @param ownerOid
     * @param numSlots
     * @param useType
     * @return
     */
    private Bag createSubBag(OID ownerOid, int useType, int numSlots, String name) {
        // create the bag
        Bag bag = new Bag();
        bag.setOid(Engine.getOIDManager().getNextOid());
        bag.applySettings(numSlots, useType);
        bag.setStorageName(name);

        // set back reference to the owner
        bag.setProperty(InventoryPlugin.INVENTORY_PROP_BACKREF_KEY, ownerOid);

        // set the forward reference on the parentBag
        //parentBag.putItem(parentBagSlotNum, bag.getOid());

        // bind the bag locally
        registerBag(bag);
        SubjectFilter wmFilter = new SubjectFilter(bag.getOid());
        wmFilter.addType(EnginePlugin.MSG_TYPE_SET_PROPERTY);
        wmFilter.addType(EnginePlugin.MSG_TYPE_GET_PROPERTY);
        /* Long sub = */ //Engine.getAgent().createSubscription(wmFilter, AgisInventoryPlugin.this);
        
        return bag;
    }
    
    /**
     * Hook for the CreateInventoryMessage. Calls createInventory with the
     * list of items to be given to the player/mob. Run by the createInvSubObject
     * method. 
     * @author Andrew Harrison
     *
     */
    protected class CreateInventoryHook implements Hook {
        public CreateInventoryHook(OID masterOid, String invItems) {
            this.masterOid = masterOid;
            this.invItems = invItems;
        }
        protected OID masterOid;
        protected String invItems;

        public boolean processMessage(Message msg, int flags) {
            if (Log.loggingDebug)
                log.debug("CreateInventoryHook.processMessage: masterOid=" + masterOid + " invItems=" + invItems);
            InventoryInfo iInfo = getInventoryInfo(masterOid);

            if (invItems == null)
                return true;
            if (invItems.equals("")) {
                return true;
            }
            createInventoryItems(masterOid, iInfo, invItems);
            return true;
        }
    }
    
    /**
     * Generates the items requested and puts them in the players/mobs inventory.
     * Called when first creating an Inventory SubObject.
     * @param masterOid
     * @param iInfo
     * @param invItems A String with numbers representing the templateIDs of the items to be generated split by semicolons.
     */
	@SuppressWarnings({ "unchecked" })
    protected void createInventoryItems(OID masterOid, InventoryInfo iInfo, String invItems) {
        log.debug("createInventoryItems: invItems=" + invItems);
		LinkedList<String> recipes = new LinkedList<String>();
		  try {
			recipes = (LinkedList<String>)EnginePlugin.getObjectProperty(masterOid, WorldManagerClient.NAMESPACE, "recipes");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		  if(recipes==null)
			  recipes = new LinkedList<String>();
        for (String itemName : invItems.split(";")) {
            boolean equip = false;
            itemName = itemName.trim();
            if (itemName.isEmpty())
                continue;
            if (itemName.startsWith("*")) {
                itemName = itemName.substring(1);
                equip = true;
            }
            int itemID = 0;
            int count = 1;
            log.debug("createInventoryItems: itemName=" + itemName);

            String[] itemdata = itemName.split("\\|");
            log.debug("createInventoryItems: itemdata=" + itemdata + " " + Arrays.toString(itemdata));
            itemID = Integer.parseInt(itemdata[0]);
            try {
                log.debug("createInventoryItems: itemdata[0]=" + itemdata[0] + " itemdata[1]=" + itemdata[1]);
                count = Integer.parseInt(itemdata[1]);
            } catch (NumberFormatException e) {
                log.debug("createInventoryItems: no | and count in string set default count 1");
            }

            log.debug("createInventoryItems: itemID=" + itemID + " count=" + count);

            if (Log.loggingDebug)
                log.debug("CreateInventoryHook.processMessage: creating item=" + itemName + " equip=" + equip);
            Template itemTemplate = new Template();
            itemTemplate.put(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT, iInfo.getPersistenceFlag());
            Template tmpl = ObjectManagerPlugin.getTemplate(itemID, ObjectManagerPlugin.ITEM_TEMPLATE);

            int stackLimit = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "stackLimit");
            ArrayList<AcquireHook> acquireHook = (ArrayList<AcquireHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_ACQUIRE_HOOK);
            boolean curr=false;
                if (acquireHook != null) {
                for (AcquireHook ah : acquireHook) {
					if (Log.loggingDebug)
						log.debug("CreateInventoryHook.processMessage: class = "+ah.getClass());
                    if (ah.getClass().equals(CurrencyItemAcquireHook.class)) {
                        CurrencyItemAcquireHook ciah = (CurrencyItemAcquireHook) ah;
						if (Log.loggingDebug)
							log.debug("CreateInventoryHook.processMessage: CurrencyItemAcquireHook");
                        int currencyId = ciah.getCurrencyID();
                        AgisInventoryClient.alterCurrency(masterOid, currencyId, count);
                        curr=true;
                    }
                }
            }
                
			ArrayList<ActivateHook> activateHook = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
			if (activateHook != null) {
				for (ActivateHook ah : activateHook) {
					if (Log.loggingDebug)
						log.debug("CreateInventoryHook.processMessage: class = "+ah.getClass());
					if (ah.getClass().equals(RecipeItemActivateHook.class)) {
						RecipeItemActivateHook riah = (RecipeItemActivateHook) ah;
						if (Log.loggingDebug)
							log.debug("CreateInventoryHook.processMessage: RecipeItemActivateHook");
						if (equip) {
							if (!recipes.contains("" + riah.getRecipeID())) {
								recipes.add("" + riah.getRecipeID());
								curr = true;
							}
						}
					}
				}
			}
			
			
			
			if (Log.loggingDebug)
				log.debug("CreateInventoryHook.processMessage: recipes="+recipes);
            
            if(!curr){
                if (Log.loggingDebug)
                    log.debug("CreateInventoryHook.processMessage: generating item=" + itemID);
                if (stackLimit > count) {
                    OID itemOid = ObjectManagerClient.generateObject(itemID, ObjectManagerPlugin.ITEM_TEMPLATE, itemTemplate);
                    AgisItem item = getAgisItem(itemOid);
                    item.setStackSize(count);
                    if (Log.loggingDebug)
                        log.debug("CreateInventoryHook.processMessage: created item=" + itemOid);
                    addItem(masterOid, iInfo.getOid(), itemOid);
                    if (Log.loggingDebug)
                        log.debug("CreateInventoryHook.processMessage: added item to inv=" + itemOid);
                    if (equip) {
                        // AgisItem item = getAgisItem(itemOid);
                        equipItem(item, masterOid, false);
                    }
                } else {
                    int countUsed = 0;
                    int counttogenerate = count / stackLimit;
                    if (count % stackLimit > 0)
                        counttogenerate++;
                    if (Log.loggingDebug)
                        log.debug("CreateInventoryHook count=" + count + " stackLimit=" + stackLimit + " counttogenerate=" + counttogenerate);
                    for (int i = 0; i < counttogenerate; i++) {
                        if (Log.loggingDebug)
                            log.debug("CreateInventoryHook i=" + i + " count=" + count + " stackLimit=" + stackLimit + " counttogenerate=" + counttogenerate + " countUsed=" + countUsed);

                        OID itemOid = ObjectManagerClient.generateObject(itemID, ObjectManagerPlugin.ITEM_TEMPLATE, itemTemplate);
                        AgisItem item = getAgisItem(itemOid);
                        if (count - countUsed < stackLimit) {
                            item.setStackSize(count - countUsed);
                        } else {
                            item.setStackSize(stackLimit);
                            countUsed += stackLimit;
                        }
                        if (Log.loggingDebug)
                            log.debug("CreateInventoryHook.processMessage: created item=" + itemOid);
                        addItem(masterOid, iInfo.getOid(), itemOid);
                        if (Log.loggingDebug)
                            log.debug("CreateInventoryHook.processMessage: added item to inv=" + itemOid);
                        if (equip) {
                            // AgisItem item = getAgisItem(itemOid);
                            equipItem(item, masterOid, false);
                        }

                    }

                }
            }
        }
		if (recipes!=null && recipes.size() > 0) {
			EnginePlugin.setObjectProperty(masterOid, WorldManagerClient.NAMESPACE, "recipes", recipes);
		}
        sendInvUpdate(masterOid);
    }
    
    /**
     * Handles the CategoryUpdatedMessage to indicate the player has changed world category.
     * This is used to change to or generate new data (Inventory in this case) to be used in the new
     * category
     * 
     * Not really used at the moment. Will need updated.
     * @author Andrew Harrison
     *
     */
    public class CategoryUpdatedHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisMobClient.categoryUpdatedMessage pMsg = (AgisMobClient.categoryUpdatedMessage) msg;
            OID playerOid = pMsg.getSubject();
            int category = (Integer) pMsg.getProperty("category");
            Log.debug("CATEGORY: updating category for player " + playerOid + " and category: " + category);
            Lock lock = getObjectLockManager().getLock(playerOid);
            lock.lock();
            try {
                InventoryInfo iInfo = getInventoryInfo(playerOid);
                unequipOldItems(iInfo);
                saveInventory(iInfo, Namespace.BAG);
                unloadInventory(iInfo);
                if (iInfo.categoryUpdated(category)) {
                    createBags(iInfo, playerOid, true);
                    if (WorldManagerClient.getObjectInfo(playerOid).objType == ObjectTypes.player) {
                        //int race = (Integer)EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, "race");
                        //int aspect = (Integer)EnginePlugin.getObjectProperty(mobOid, CombatClient.NAMESPACE, "aspect");
                        //String startingItems = getStartingItems(race, aspect);
                        createInventoryItems(playerOid, iInfo, "");
                    }
                } else {
                    loadInventory(iInfo);
                    for (OID bagOid : iInfo.getBags()) {
                        Bag bag = getBag(bagOid);
                        Log.debug("BAG: bag slots: " + bag.getNumSlots());
                    }
                }
                equipNewItems(iInfo);
                Log.debug("CATEGORY: updating category for player " + playerOid + " complete");
            }
            finally {
                lock.unlock();
            }
            sendBagInvUpdate(playerOid);
            return true;
        }
        
        private void unequipOldItems(InventoryInfo iInfo) {
            log.dumpStack("unequipOldItems");
            OID bagOid = iInfo.getEquipmentItemBag();
            Bag equipBag = getBag(bagOid);
            for (OID itemOID : equipBag.getItemsList()) {
                if (itemOID == null)
                    continue;
                AgisItem item = getAgisItem(itemOID);

                lock.lock();
                try {
                    // where is this currently equipped
                    EquipMap equipMap = getEquipMap(iInfo.getOid());
                    AgisEquipSlot slot = equipMap.getSlot(item.getMasterOid());
                    if (slot == null) {
                        // item is not currently equipped
                        Log.warn("AgisInventoryPlugin.unequipItem: item not equipped: item=" + item);
                        continue;
                    }
                    // remove the item from the map
                    equipMap.remove(slot);
                    if (Log.loggingDebug)
                        log.debug("AgisInventoryPlugin.unequipItem: removed DC for item:" + item); 
                } finally {
                    lock.unlock();
                }
                HashMap<Integer, Integer> list = new HashMap<Integer, Integer>();
                list = getEquipSetsInfo(iInfo.getOid());
                AgisInventoryClient.itemEquipStatusChanged(iInfo.getOid(), null, item, item.getPrimarySlot().toString(), list);
                EquipHelper.updateDisplay(iInfo.getOid(), null, item.getPrimarySlot());
            }
        }
        
        private void equipNewItems(InventoryInfo iInfo) {
            log.dumpStack("equipNewItems");
            
            OID bagOid = iInfo.getEquipmentItemBag();
            Bag equipBag = getBag(bagOid);
            for (OID itemOID : equipBag.getItemsList()) {
                if (itemOID == null)
                    continue;
                AgisItem item = getAgisItem(itemOID);

                // get the primary slot for the item
                AgisEquipSlot slot = item.getPrimarySlot();
                if (slot == null) {
                    Log.warn("AgisInventoryPlugin: slot is null for item: " + item);
                    continue;
                }

                EquipMap equipMap;
                lock.lock();
                try {
                    equipMap = getEquipMap(iInfo.getOid());
                    // place object in slot
                    equipMap.put(slot, item.getMasterOid());
                } finally {
                    lock.unlock();
                }
                
                HashMap<Integer, Integer> list = new HashMap<Integer, Integer>();
                list = getEquipSetsInfo(iInfo.getOid());
                AgisInventoryClient.itemEquipStatusChanged(iInfo.getOid(), item, null, item.getPrimarySlot().toString(), list);
                String displayVal = (String) item.getProperty("displayVal");
                
                String unicItem = item.getTemplateID() + "";
                if ((int) item.getProperty("enchantLevel") > 0)
                    unicItem += ";E" + item.getProperty("enchantLevel");

                HashMap<String, Integer> bstats = (HashMap) item.getProperty("bonusStats");
                for (String statName : bstats.keySet()) {
                    unicItem += ";B" + statName + "|" + bstats.get(statName);
                }

                if (item.getProperty("enchantStats") != null) {
                    HashMap<String, Integer> estats = (HashMap) item.getProperty("enchantStats");
                    for (String statName : estats.keySet()) {
                        if (bstats.containsKey(statName)) {
                            if (estats.get(statName) - bstats.get(statName) != 0) {
                                unicItem += ";T" + statName + "|" + (estats.get(statName) - bstats.get(statName));
                            }
                        } else {
                            unicItem += ";T" + statName + "|" + estats.get(statName);
                        }
                    }
                }
                
                HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) item.getProperty("sockets");
                ArrayList<String> socketItems = new ArrayList<String>();
                for (Integer sId : itemSockets.keySet()) {
                    if (itemSockets.get(sId).GetItemOid() != null) {
                        AgisItem sitem = getAgisItem(itemSockets.get(sId).GetItemOid());
                        socketItems.add(itemSockets.get(sId).GetType() + "|" + sId + "|" + sitem.getTemplateID());
                    }
                }
                // Collections.sort(socketItems);
                for (String l : socketItems) {
                    unicItem += ";S" + l;
                }
            
            
                EquipHelper.updateDisplay(iInfo.getOid(), displayVal, item.getPrimarySlot(),unicItem);
            }
        }
    }

    /**
     * Handles the RemoveOrFindItemMessage, returning the OID of the item
     * found in the EquipSlot of the player/mob specified.
     *
     */
    class AgisFindItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            InventoryClient.RemoveOrFindItemMessage findMsg = (InventoryClient.RemoveOrFindItemMessage)msg;
            OID mobOid = findMsg.getSubject();
            String method = findMsg.getMethod();

            log.debug("AgisFindItemHook: got message");
            if (method.equals(AgisInventoryClient.INV_METHOD_SLOT)) {
                AgisEquipSlot slot = (AgisEquipSlot)findMsg.getPayload();
                OID resultOid = findItem(mobOid, slot);
                Engine.getAgent().sendOIDResponse(findMsg, resultOid);
            }else if (method.equals(AgisInventoryClient.INV_METHOD_AMMO_TYPE)) { 
                AgisEquipSlot slot = (AgisEquipSlot)findMsg.getPayload();
                OID resultOid = findItem(mobOid, slot);
                Integer ammoType = 0;
                log.debug("FindItemAmmoType resultOid:"+resultOid);
                if (resultOid != null) {
                    AgisItem resultItemObj = AgisInventoryPlugin.getAgisItem(resultOid);
                    log.debug("FindItemAmmoType resultItemObj:"+resultItemObj);
                
                    if (resultItemObj != null)
                        ammoType = (Integer) resultItemObj.getProperty(AgisItem.AMMO_TYPE);
                        log.debug("FindItemAmmoType ammoType:"+ammoType);
                    }
                Engine.getAgent().sendIntegerResponse(findMsg, ammoType);
                
            } else if (method.equals(AgisInventoryClient.INV_METHOD_TYPE)) { 
                // Search through players inventory for an item matching the specified type
            }
            else if (method.equals(AgisInventoryClient.INV_METHOD_WEAPON_TYPE)) {
                // Search through players inventory for a weapon matching the specified type
            }
            else {
                Log.error("AgisFindItemHook: unknown method=" + method);
            }
            return true;
        }
    }

    /**
     * Calls the sendEquippedInvUpdate and the sendBagInvUpdate functions.
     */
    protected void sendInvUpdate(OID mobOid) {
        // Now send the equipped message as well
//      Log.error("sendInvUpdate: sendEquippedInvUpdate bafore");
//      Log.error("sendInvUpdate: sendEquippedInvUpdate after ");
//      Log.error("sendInvUpdate: sendBagInvUpdate before ");
        sendBagInvUpdate(mobOid);
        sendEquippedInvUpdate(mobOid);
//      Log.error("sendInvUpdate: sendBagInvUpdate after");
    }
    
    /**
     * Handles the SendInventoryUpdateMessage, calling the sendBagInvUpdate function
     * for the specified player.
     * @author Andrew Harrison
     *
     */
    class SendInventoryUpdateHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.SendInventoryUpdateMessage findMsg = (AgisInventoryClient.SendInventoryUpdateMessage)msg;
            OID mobOid = findMsg.getSubject();
            sendBagInvUpdate(mobOid);
            sendEquippedInvUpdate(mobOid);
            return true;
        }
    }
    
    /**
     * Sends down the Bags and Items found in the players Inventory.
     * @param mobOid
     */
    protected void sendBagInvUpdate(OID mobOid) {
        if (Log.loggingDebug)
            log.debug("sendBagInvUpdate mobOid=" + mobOid);
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "BagInventoryUpdate");
        HashMap<Integer, Integer> itemCounts = new HashMap<Integer, Integer>();

        int numItems = 0;
        // int numBags = 0;
        InventoryInfo iInfo = getInventoryInfo(mobOid);
        if (iInfo == null)
            return;
        OID[] items = iInfo.getBags();
        float bonusModp = 0;
        if (iInfo.getBonuses().containsKey("SellFactor")) {
            bonusModp = iInfo.getBonuses().get("SellFactor").GetValuePercentage();
        }
        if(globalEventBonusesArray.containsKey("SellFactor")) {
            bonusModp += globalEventBonusesArray.get("SellFactor").GetValuePercentage();
        }
        props.put("SellFactor", SELL_FACTOR + (SELL_FACTOR * bonusModp / 100f));

        props.put("numBags", items.length);
        HashMap<Integer, Integer> itemSets = getEquipSetsInfo(mobOid);
        for (int bagPos = 0; bagPos < items.length; bagPos++) {
            OID subBagOid = items[bagPos];
            if (subBagOid == null) {
                // log.error("sendBagInvUpdate: sub bag oid is null");
            	/* props.put("bag_" + bagPos + "ID", 0);
                 props.put("bag_" + bagPos + "Name", "");
                 props.put("bag_" + bagPos + "NumSlots", 0);*/
                continue;
            }
            Bag subBag = getBag(subBagOid);
            if (subBag == null) {
                // log.error("sendBagInvUpdate: sub bag obj is null");
                props.put("bag_" + bagPos + "ID", 0);
                props.put("bag_" + bagPos + "Name", "");
                props.put("bag_" + bagPos + "TId", -1);
                props.put("bag_" + bagPos + "NumSlots", 0);
                continue;
            }
            // sendInvUpdateHelper(invUpdateMsg, pos, subBag);
            props.put("bag_" + bagPos + "ID", subBag.getItemTemplateID());
            props.put("bag_" + bagPos + "Name", subBag.getName());
            props.put("bag_" + bagPos + "TId", subBag.getItemTemplateID());
            props.put("bag_" + bagPos + "NumSlots", subBag.getNumSlots());
            OID[] itemsInBag = subBag.getItemsList();
            for (int itemPos = 0; itemPos < itemsInBag.length; itemPos++) {
                boolean itemExists = true;
                // get the item
                OID oid = itemsInBag[itemPos];
                AgisItem item = null;
                if (Log.loggingDebug)
                    log.debug("sendInvUpdateHelper: bagPos=" + bagPos + ", bagPos=" + itemPos + " itemOid=" + oid);
                if (oid == null) {

                    itemExists = false;
                } else {
                    item = getAgisItem(oid);
                    if (item == null) {
                        Log.warn("sendBagInvUpdate: item is null, oid=" + oid);
                        itemExists = false;
                    }
                }
                if (Log.loggingDebug)
                    log.debug("sendInvUpdateHelper: bagPos=" + bagPos + ", bagPos=" + itemPos + " itemOid=" + oid + " item=" + item + " itemExists=" + itemExists);
                if (itemExists) {
                    if (Log.loggingDebug)
                        log.debug("sendInvUpdateHelper: adding bagNum=" + bagPos + ", bagPos=" + itemPos + ", itemOid=" + oid + ", itemName=" + item.getName() + ",icon=" + item.getIcon());

                //  props.put("item_" + numItems + "icon", item.getIcon());
                //  props.put("item_" + numItems + "icon2", item.getIcon2());
                //  props.put("item_" + numItems + "itemGrade", (int)item.getProperty("itemGrade"));
                //  props.put("item_" + numItems + "itemType", (String)item.getProperty("itemType"));
                //  props.put("item_" + numItems + "subType", (String)item.getProperty("subType"));
                //  props.put("item_" + numItems + "slot", (String) item.getProperty("slot"));
                           
                    props.put("item_" + numItems + "TemplateID", item.getTemplateID());
                    props.put("item_" + numItems + "Name", item.getName());
                    props.put("item_" + numItems + "BaseName", item.getProperty("baseName"));
                    
                    //props.put("item_" + numItems + "Description", description);
                    //props.put("item_" + numItems + "Icon", item.getIcon());
                    props.put("item_" + numItems + "Id", item.getOid());
                    props.put("item_" + numItems + "Count", item.getStackSize());
                    props.put("item_" + numItems + "BagNum", bagPos);
                    props.put("item_" + numItems + "SlotNum", itemPos);
                    props.put("item_" + numItems + "Bound", item.isPlayerBound());
                    if (item.getProperty("energyCost") != null) {
                        props.put("item_" + numItems + "EnergyCost", item.getProperty("energyCost"));
                    } else {
                        props.put("item_" + numItems + "EnergyCost", 0);
                    }
                    if (item.getProperty("maxDurability") != null) {
                        props.put("item_" + numItems + "Durability", item.getProperty("durability"));
                        props.put("item_" + numItems + "MaxDurability", item.getProperty("maxDurability"));
                    } else {
                        props.put("item_" + numItems + "MaxDurability", 0);
                    }
                    if (item.getProperty("resistanceStats") != null) {
                        int numResist = 0;
                        HashMap<String, Integer> resistances = (HashMap) item.getProperty("resistanceStats");
                        for (String resistance: resistances.keySet()) {
                            props.put("item_" + numItems + "Resist_" + numResist + "Name", resistance);
                            props.put("item_" + numItems + "Resist_" + numResist + "Value", resistances.get(resistance));
                            numResist++;
                        }
                        props.put("item_" + numItems + "NumResistances", numResist);
                    } else {
                        props.put("item_" + numItems + "NumResistances", 0);
                    }
                    if (item.getProperty("bonusStats") != null) {
                        int numStats = 0;
                        HashMap<String, Integer> stats = (HashMap) item.getProperty("bonusStats");
                        for (String statName: stats.keySet()) {
                            props.put("item_" + numItems + "Stat_" + numStats + "Name", statName);
                            props.put("item_" + numItems + "Stat_" + numStats + "Value", stats.get(statName));
                            numStats++;
                        }
                        props.put("item_" + numItems + "NumStats", numStats);
                    } else {
                        props.put("item_" + numItems + "NumStats", 0);
                    }
                    // If it is a weapon, add damage/speed stats
                    if (item.getItemType().equals("Weapon")) {
                        props.put("item_" + numItems + "Delay", item.getProperty("delay"));
                        props.put("item_" + numItems + "DamageType", item.getProperty("damageType"));
                        props.put("item_" + numItems + "DamageValue", item.getProperty("damage"));
                        props.put("item_" + numItems + "DamageValueMax", item.getProperty("damageMax"));
                          }
                    props.put("item_" + numItems + "ActionBarAllowed", item.getProperty("actionBarAllowed"));
                    int enchantLevel = (int) item.getProperty("enchantLevel");
                    props.put("item_" + numItems + "ELevel", enchantLevel);
                    
                    if (item.getProperty("enchantStats") != null) {
                        int numStats = 0;
                        HashMap<String, Integer> stats = (HashMap) item.getProperty("enchantStats");
                        HashMap<String, Integer> bstats = (HashMap) item.getProperty("bonusStats");
                        for (String statName : stats.keySet()) {
                            if (bstats.containsKey(statName)) {
                                if(Log.loggingDebug)    
                                    log.debug(item.getName()+" "+statName+" "+stats.get(statName)+" "+bstats.get(statName)+" "+(stats.get(statName) - bstats.get(statName))+" ?");
                                if (stats.get(statName) - bstats.get(statName) != 0) {
                                    props.put("item_" + numItems + "EStat_" + numStats + "Name", statName);
                                    props.put("item_" + numItems + "EStat_" + numStats + "Value", stats.get(statName) - bstats.get(statName));
                                    if(Log.loggingDebug)    
                                        log.debug(item.getName()+" "+statName+" "+(stats.get(statName) - bstats.get(statName)));
                                    numStats++;
                                }
                            } else {
                                props.put("item_" + numItems + "EStat_" + numStats + "Name", statName);
                                props.put("item_" + numItems + "EStat_" + numStats + "Value", stats.get(statName));
                            if(Log.loggingDebug)    
                                log.debug(item.getName()+" "+statName+" "+(stats.get(statName) )+" |");
                                numStats++;
                                
                            }
                        }
                        props.put("item_" + numItems + "NumEStats", numStats);
                    } else {
                        props.put("item_" + numItems + "NumEStats", 0);
                    }
                    if (item.getProperty("sockets") != null) {
                        int numSocket = 0;
                        HashMap<Integer, SocketInfo> sockets = (HashMap<Integer, SocketInfo>) item.getProperty("sockets");
                        for (Integer socket : sockets.keySet()) {
                            if (sockets.get(socket).GetItemOid() != null) {
                                AgisItem itemSoc = getAgisItem(sockets.get(socket).GetItemOid());
                                if (itemSoc != null) {
                                    props.put("item_" + numItems + "socket_" + socket + "Item", itemSoc.getTemplateID());
                                    props.put("item_" + numItems + "socket_" + socket + "ItemOid", itemSoc.getOid().toLong());
                                } else {
                                    props.put("item_" + numItems + "socket_" + socket + "Item", -1);
                                    props.put("item_" + numItems + "socket_" + socket + "ItemOid", 0L);
                                }
                            } else {
                                props.put("item_" + numItems + "socket_" + socket + "Item", -1);
                                props.put("item_" + numItems + "socket_" + socket + "ItemOid", 0L);
                            }
                            props.put("item_" + numItems + "socket_" + socket + "Type", sockets.get(socket).GetType());
                            props.put("item_" + numItems + "socket_" + socket + "Id", socket);
                            numSocket++;
                        }
                        props.put("item_" + numItems + "NumSocket", numSocket);
                    } else {
                        props.put("item_" + numItems + "NumSocket", 0);
                    }
                    int setid = (int) item.getIntProperty("item_set");
                    if (setid>0) {
                        if (itemSets.containsKey(setid)) {
                            props.put("item_" + numItems + "NumOfSet", itemSets.get(setid));
                        }else {
                            props.put("item_" + numItems + "NumOfSet", 0);
                        }
                    }else {
                        props.put("item_" + numItems + "NumOfSet", 0);
                    }
                    
                    //Added for Enchant Effects and Abilities
                    if (enchantLevel > 0) 
                    {
                    	if (item.getProperty("enchantProfileId") != null) 
                    	{
                    		EnchantProfile ep = ObjectManagerPlugin.getEnchantProfile((int) item.getProperty("enchantProfileId"));
                			if (ep != null && ep.GetLevels().containsKey(enchantLevel)) 
                			{	
                				int numEffects = 0;
                				int numAbilities = 0;
                				for (int e = 1; e <= enchantLevel; e++) 
                				{
                					EnchantProfileLevel enchantProfile = ep.GetLevels().get(e);
        							for (Integer ability : enchantProfile.GetAbilities()) 
        							{
        								props.put("item_" + numItems + "EAbility_" + numAbilities + "Value", ability);
        								numAbilities++;
        							}   
        							for (Integer effect : enchantProfile.GetEffects()) 
        							{
        								props.put("item_" + numItems + "EEffect_" + numEffects + "Value", effect);
        								numEffects++;
        							}
                				}
                				props.put("item_" + numItems + "NumEAbilities", numAbilities);	
                				props.put("item_" + numItems + "NumEEffects", numEffects);
                			}                			
                    	}
            		} 
                    numItems++;
                    // Update the item counts map
                    if (itemCounts.containsKey(item.getTemplateID())) {
                        itemCounts.put(item.getTemplateID(), itemCounts.get(item.getTemplateID()) + item.getStackSize());
                    } else {
                        itemCounts.put(item.getTemplateID(), item.getStackSize());
                    }
                }
            }
        }

        props.put("numItems", numItems);
        if(Log.loggingDebug)
            log.debug("SendBagInvUpdate mobOid="+mobOid+" " + props);
        
		if (QUEST_CHECK_EQUIPED_ITEMS) {
			OID subBagOid = iInfo.getEquipmentItemBag();
			if (subBagOid == null) {
				log.error("SendBagInvUpdate: equip sub bag oid is null");
			} else {
				Bag subBag = getBag(subBagOid);
				if (subBag == null) {
					log.error("SendBagInvUpdate: equip sub bag obj is null");
				} else {
					for (int pos = 0; pos < subBag.getNumSlots(); pos++) {
						OID oid = subBag.getItem(pos);
						if (oid == null) {
							continue;
						}
						AgisItem item = getAgisItem(oid);
						if (item == null) {
							Log.warn("SendBagInvUpdate: item is null, oid=" + oid);
							continue;
						}
						if (Log.loggingDebug)
							log.debug("SendBagInvUpdate: " + ", itemOid=" + oid + ", itemName=" + item.getName() + ",icon=" + item.getIcon());
						if (itemCounts.containsKey(item.getTemplateID())) {
							itemCounts.put(item.getTemplateID(), itemCounts.get(item.getTemplateID()) + item.getStackSize());
						} else {
							itemCounts.put(item.getTemplateID(), item.getStackSize());
						}
					}
				}
			}
		}

        TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, mobOid, mobOid, false, props);
        Engine.getAgent().sendBroadcast(msg);
        // Also send an invUpdate message for the quest state etc.
        AgisInventoryClient.QuestItemsListMessage invUpdateMsg = new AgisInventoryClient.QuestItemsListMessage(mobOid, itemCounts);
        Engine.getAgent().sendBroadcast(invUpdateMsg);
        if(Log.loggingDebug)
            log.debug("SendBagInvUpdate message has been sent");
        // Send currencies
        ExtendedCombatMessages.sendCurrencies(mobOid, iInfo.getCurrentCurrencies());
    }
    
    /**
     * Sends the equipped inventory bag information to the player.
     * @param mobOid
     */
    protected void sendEquippedInvUpdate(OID mobOid) {
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "EquippedInventoryUpdate");
        
        // go through each bag and place contents into inv update msg
        InventoryInfo iInfo = getInventoryInfo(mobOid);
        if (iInfo == null)
            return;
        OID subBagOid = iInfo.getEquipmentItemBag();
        if (subBagOid == null) {
            log.error("sendEquippedInvUpdate: sub bag oid is null for "+mobOid);
        }
        Bag subBag = getBag(subBagOid);
        if (subBag == null) {
            log.error("sendEquippedInvUpdate: sub bag obj is null for "+mobOid);
        }
//        OID[] eitems = subBag.getItemsList();
//        log.error("sendEquippedInvUpdate: equip="+Arrays.toString(eitems));
        String setSelected = iInfo.getItemSetSelected();
        props.put("setSelected", setSelected != null ? setSelected : "");
        props.put("numSlots", subBag.getNumSlots());
        HashMap<Integer, Integer> itemSets = getEquipSetsInfo(mobOid);
        for (int pos = 0; pos < subBag.getNumSlots(); pos++) {
            if( Log.loggingDebug)log.debug("sendEquippedInvUpdate: slot="+pos);
            // get the item
            OID oid = subBag.getItem(pos);
            if (oid == null) {
                log.warn("sendEquippedInvUpdate:  item oid  "+oid+" is null");
                props.put("item_" + pos + "Name", "");
                continue;
            }
            AgisItem item = getAgisItem(oid);
            if (item == null) {
                Log.warn("sendEquippedInvUpdate: item is null, oid=" + oid);
                props.put("item_" + pos + "Name", "");
                continue;
            }
            if (Log.loggingDebug)
                log.debug("sendEquippedInvUpdate: " + ", itemOid=" + oid + ", itemName=" + item.getName() + ",icon=" + item.getIcon());
            props.put("item_" + pos + "TemplateID", item.getTemplateID());
            
            props.put("item_" + pos + "Name", item.getName());
            props.put("item_" + pos + "BaseName", item.getProperty("baseName"));
            props.put("item_" + pos + "Id", item.getOid());
            props.put("item_" + pos + "Count", item.getStackSize());
            AgisEquipSlot slot = AgisEquipSlot.getSlotById(pos);
            props.put("item_" + pos + "Slot", slot != null ? slot.getName() : "");
            props.put("item_" + pos + "Bound", item.isPlayerBound());

            if (Log.loggingDebug)
                log.debug("sendEquippedInvUpdate: " + ", itemOid=" + oid + ", pos="+pos+" itemName=" + item.getName() + ",slot=" + (slot != null ? slot.getName() : " is null"));
            if (item.getProperty("energyCost") != null) {
                props.put("item_" + pos + "EnergyCost", item.getProperty("energyCost"));
            } else {
                props.put("item_" + pos + "EnergyCost", 0);
            }
            if (item.getProperty("maxDurability") != null) {
                props.put("item_" + pos + "Durability", item.getProperty("durability"));
                props.put("item_" + pos + "MaxDurability", item.getProperty("maxDurability"));
            } else {
                props.put("item_" + pos + "MaxDurability", 0);
            }
            if (item.getProperty("resistanceStats") != null) {
                int numResist = 0;
                HashMap<String, Integer> resistances = (HashMap) item.getProperty("resistanceStats");
                for (String resistance: resistances.keySet()) {
                    props.put("item_" + pos + "Resist_" + numResist + "Name", resistance);
                    props.put("item_" + pos + "Resist_" + numResist + "Value", resistances.get(resistance));
                    numResist++;
                }
                props.put("item_" + pos + "NumResistances", numResist);
            } else {
                props.put("item_" + pos + "NumResistances", 0);
            }
            if (item.getProperty("bonusStats") != null) {
                int numStats = 0;
                HashMap<String, Integer> stats = (HashMap) item.getProperty("bonusStats");
                for (String statName: stats.keySet()) {
                    props.put("item_" + pos + "Stat_" + numStats + "Name", statName);
                    props.put("item_" + pos + "Stat_" + numStats + "Value", stats.get(statName));
                    numStats++;
                }
                props.put("item_" + pos + "NumStats", numStats);
            } else {
                props.put("item_" + pos + "NumStats", 0);
            }
            // If it is a weapon, add damage/speed stats
            if (item.getItemType().equals("Weapon")) {
                props.put("item_" + pos + "Delay", item.getProperty("delay"));
                props.put("item_" + pos + "DamageType", item.getProperty("attackType"));
                props.put("item_" + pos + "DamageValue", item.getProperty("damage"));
                props.put("item_" + pos + "DamageValueMax", item.getProperty("damageMax"));
                  }
            props.put("item_" + pos + "ActionBarAllowed", item.getProperty("actionBarAllowed"));
            
            int enchantLevel = (int) item.getProperty("enchantLevel");
            props.put("item_" + pos + "ELevel", enchantLevel);
            
            if (item.getProperty("enchantStats") != null) {
                int numStats = 0;
                HashMap<String, Integer> stats = (HashMap) item.getProperty("enchantStats");
                HashMap<String, Integer> bstats = (HashMap) item.getProperty("bonusStats");
                for (String statName : stats.keySet()) {
                    if (bstats.containsKey(statName)) {
                        if(Log.loggingDebug)log.debug(item.getName()+" "+statName+" "+stats.get(statName)+" "+bstats.get(statName)+" "+(stats.get(statName) - bstats.get(statName))+" ?");
                        if (stats.get(statName) - bstats.get(statName) != 0) {
                            props.put("item_" + pos + "EStat_" + numStats + "Name", statName);
                            props.put("item_" + pos + "EStat_" + numStats + "Value", stats.get(statName) - bstats.get(statName));
                            if(Log.loggingDebug)    log.debug(item.getName()+" "+statName+" "+(stats.get(statName) - bstats.get(statName)));
                            numStats++;
                        }
                    } else {
                        props.put("item_" + pos + "EStat_" + numStats + "Name", statName);
                        props.put("item_" + pos + "EStat_" + numStats + "Value", stats.get(statName));
                        if(Log.loggingDebug)    log.debug(item.getName()+" "+statName+" "+(stats.get(statName) )+" |");
                        numStats++;
                        
                    }
                }
                props.put("item_" + pos + "NumEStats", numStats);
            } else {
                props.put("item_" + pos + "NumEStats", 0);
            }
            
            //Added for Enchant Effects and Abilities
            if (enchantLevel > 0) 
            {
            	if (item.getProperty("enchantProfileId") != null) 
            	{
                    EnchantProfile ep = ObjectManagerPlugin.getEnchantProfile((int) item.getProperty("enchantProfileId"));
                    if (ep != null && ep.GetLevels().containsKey(enchantLevel))
        			{	
        				int numEffects = 0;
        				int numAbilities = 0;
        				for (int e = 1; e <= enchantLevel; e++) 
        				{
        					EnchantProfileLevel enchantProfile = ep.GetLevels().get(e);
							for (Integer ability : enchantProfile.GetAbilities()) 
							{
								props.put("item_" + pos + "EAbility_" + numAbilities + "Value", ability);
								numAbilities++;
							}   
							for (Integer effect : enchantProfile.GetEffects()) 
							{
								props.put("item_" + pos + "EEffect_" + numEffects + "Value", effect);
								numEffects++;
							}
        				}
        				props.put("item_" + pos + "NumEAbilities", numAbilities);	
        				props.put("item_" + pos + "NumEEffects", numEffects);
        			}                			
            	}
    		} 
            
            if (item.getProperty("sockets") != null) {
                int numSocket = 0;
                HashMap<Integer, SocketInfo> sockets = (HashMap<Integer, SocketInfo>) item.getProperty("sockets");
                for (Integer socket: sockets.keySet()) {
                    if (sockets.get(socket).GetItemOid()!=null) {
                         AgisItem itemSoc = getAgisItem(sockets.get(socket).GetItemOid());
                         if (itemSoc != null) {
                             props.put("item_" + pos + "socket_" + socket + "Item", itemSoc.getTemplateID());
                                props.put("item_" + pos + "socket_" + socket + "ItemOid", itemSoc.getOid().toLong());
                                 }else {
                             props.put("item_" + pos + "socket_" + socket + "Item", -1); 
                                props.put("item_" + pos + "socket_" + socket + "ItemOid", 0L);
                                  }
                    }else {
                        props.put("item_" + pos + "socket_" + socket + "Item", -1);
                    props.put("item_" + pos + "socket_" + socket + "ItemOid", 0L);
                    }
                    props.put("item_" + pos + "socket_" + socket + "Type", sockets.get(socket).GetType());
                    props.put("item_" + pos + "socket_" + socket + "Id", socket);
                        numSocket++;
                }
                props.put("item_" + pos + "NumSocket", numSocket);
            } else {
                props.put("item_" + pos + "NumSocket", 0);
            }
            int setid = (int) item.getIntProperty("item_set");
            if (setid>0) {
                if (itemSets.containsKey(setid)) {
                    props.put("item_" + pos + "NumOfSet", itemSets.get(setid));
                }else {
                    props.put("item_" + pos + "NumOfSet", 0);
                }
            }else {
                props.put("item_" + pos + "NumOfSet", 0);
            }
        }

        // Send Ammo link
        int ammoItemID = -1;
        Integer ammoLoaded = null;
        if( Log.loggingDebug)Log.debug("sendEquippedInvUpdate ammo "+mobOid+" "+CombatClient.NAMESPACE+" "+CombatInfo.COMBAT_AMMO_LOADED);
        ammoLoaded = (Integer)EnginePlugin.getObjectProperty(mobOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_LOADED);
        if (ammoLoaded != null)
           ammoItemID = ammoLoaded;
        props.put("equippedAmmo", ammoItemID);
        if(Log.loggingDebug)
           log.debug("sendEquippedInvUpdate " + props);
        TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, mobOid, mobOid, false, props);
        int num = Engine.getAgent().sendBroadcast(msg);
        if( Log.loggingDebug)log.debug("sendEquippedInvUpdate: num="+num);
       }
    
    /**
     * Handles the GetGenericItemData Message. Returns the template for the
     * specified template ID.
     *
     */
    class GetGenericItemDataHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage getMsg = (ExtensionMessage)msg;
            Template itemTmpl = null;
            int itemID = (Integer) getMsg.getProperty("itemID");
            itemTmpl = ObjectManagerPlugin.getTemplate(itemID, ObjectManagerPlugin.ITEM_TEMPLATE);
            Engine.getAgent().sendObjectResponse(getMsg, itemTmpl);
            return true;
        }
    }
    
    /**
     * Handles the GetSpecificItemData Message. Sends down a message to the requesting
     * client with information about the requested items.
     *
     */
    class GetSpecificItemDataHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.getSpecificItemDataMessage getMsg = (AgisInventoryClient.getSpecificItemDataMessage)msg;
            OID oid = getMsg.getSubject();
            OID targetOid = (OID) getMsg.getProperty("targetOid");
            ArrayList<Long> itemOids = (ArrayList<Long>) getMsg.getProperty("itemOids");

            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("ext_msg_subtype", "TargetItemData");
            
            int numItems = 0;
            InventoryInfo iInfo = getInventoryInfo(targetOid);
            if (iInfo == null)
                return true;
            OID[] items = iInfo.getBags();
            for (int bagPos = 0; bagPos < items.length; bagPos++) {
                OID subBagOid = items[bagPos];
                if (subBagOid == null) {
                    log.error("sendInvUpdate: sub bag oid is null");
                    continue;
                }
                Bag subBag = getBag(subBagOid);
                if (subBag == null) {
                    log.error("sendInvUpdate: sub bag obj is null");
                    continue;
                }
                //sendInvUpdateHelper(invUpdateMsg, pos, subBag);
                OID[] itemsInBag = subBag.getItemsList();
                for (int itemPos = 0; itemPos < itemsInBag.length; itemPos++) {
                    boolean itemExists = true;
                    // get the item
                    //OID itemOid = itemsInBag[itemPos];
                    if (oid == null) {
                        itemExists = false;
                    }
                    AgisItem item = getAgisItem(oid);
                    if (item == null) {
                        Log.warn("sendInvUpdateHelper: item is null, oid=" + oid);
                        itemExists = false;
                    }
                    if (itemExists && !props.containsValue(item.getName()) && itemOids.contains(item.getOid())) {
                        if (Log.loggingDebug)
                            log.debug("sendInvUpdateHelper: adding bagNum=" + bagPos + ", bagPos=" + itemPos +
                                      ", itemOid=" + oid + ", itemName=" + item.getName() + ",icon=" + item.getIcon());
                        props.put("item_" + numItems + "Name", item.getName());
                        //props.put("item_" + numItems + "Description", description);
                        //props.put("item_" + numItems + "Icon", item.getIcon());
                        props.put("item_" + numItems + "Id", item.getOid());
                        props.put("item_" + numItems + "Count", item.getStackSize());
                        props.put("item_" + numItems + "BagNum", bagPos);
                        props.put("item_" + numItems + "SlotNum", itemPos);
                        /*if (item.getProperty("URL") != null) {
                            props.put("item_" + numItems + "URL", item.getProperty("URL"));
                        } else {
                            props.put("item_" + numItems + "URL", "");
                        }*/
                        if (item.getProperty("energyCost") != null) {
                            props.put("item_" + numItems + "EnergyCost", item.getProperty("energyCost"));
                        }
                        if (item.getProperty("resistanceStats") != null) {
                            int numResist = 0;
                            HashMap<String, Integer> resistances = (HashMap) item.getProperty("resistanceStats");
                            for (String resistance: resistances.keySet()) {
                                props.put("item_" + numItems + "Resist_" + numResist + "Name", resistance);
                                props.put("item_" + numItems + "Resist_" + numResist + "Value", resistances.get(resistance));
                                numResist++;
                            }
                            props.put("item_" + numItems + "NumResistances", numResist);
                        } else {
                            props.put("item_" + numItems + "NumResistances", 0);
                        }
                        if (item.getProperty("bonusStats") != null) {
                            int numStats = 0;
                            HashMap<String, Integer> stats = (HashMap) item.getProperty("bonusStats");
                            for (String statName: stats.keySet()) {
                                props.put("item_" + numItems + "Stat_" + numStats + "Name", statName);
                                props.put("item_" + numItems + "Stat_" + numStats + "Value", stats.get(statName));
                                numStats++;
                            }
                            props.put("item_" + numItems + "NumStats", numStats);
                        } else {
                            props.put("item_" + numItems + "NumStats", 0);
                        }
                        // If it is a weapon, add damage/speed stats
                        if (item.getItemType().equals("Weapon")) {
                            props.put("item_" + numItems + "Speed", item.getProperty("speed"));
                            props.put("item_" + numItems + "DamageType", item.getProperty("damageType"));
                            props.put("item_" + numItems + "DamageValue", item.getProperty("damage"));
                            props.put("item_" + numItems + "DamageValueMax", item.getProperty("damageMax"));
                             }
                        numItems++;
                    }
                }
            }
            
            props.put("numItems", numItems);
            TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(
                    WorldManagerClient.MSG_TYPE_EXTENSION, oid, 
                    oid, false, props);
            Engine.getAgent().sendBroadcast(TEmsg);
            return true;
        }
    }
    
    private void inventoryCheck(OID oid) {
        InventoryInfo iInfo = getInventoryInfo(oid);
        log.debug("InventoryCheck: InventoryInfo for " + oid + " "+iInfo);
        if (iInfo == null) {
            log.debug("InventoryCheck: InventoryInfo for " + oid + " is null");
            return;
        }
        OID[] subBags = iInfo.getBags();
        log.debug("InventoryCheck: bags count = "+subBags.length);
        List<OID> items = new ArrayList<OID>();
        boolean foundDuplicate = false;
        for (OID b : subBags) {
            log.debug("InventoryCheck: inventoryCheck bag="+b);
            Bag subBag = getBag(b);
            OID[] itemsInBag = subBag.getItemsList();
            for (int i = 0; i < itemsInBag.length; i++) {
                if (itemsInBag[i] != null) {
                    if (items.contains(itemsInBag[i])) {
                        log.debug("InventoryCheck: Found duplicte of item "+itemsInBag[i]+" removing");
                        itemsInBag[i] = null;
                        foundDuplicate = true;
                    } else {
                        items.add(itemsInBag[i]);
                    }
                }
            }
            subBag.setItemsList(itemsInBag);
        }
        for(OID itemOid : items) {
        	AgisItem ai = getAgisItem(itemOid);
        	Integer binding = (Integer) ai.getProperty("binding");
        	Log.debug("BIND: got item binding: " + binding);
        	if (binding != null && binding == 2 && !ai.isPlayerBound()) {
        		ai.bindToPlayer(oid);
        		Engine.getPersistenceManager().setDirty(ai);
        	}
        }
        log.debug("InventoryCheck: items="+items);
        if(foundDuplicate) {
            Engine.getPersistenceManager().setDirty(iInfo);
        }
    }

    /**
     * Moves an item into the specified location.
     * @author Andrew Harrison
     *
     */
    
    
    
    class MoveItemHook implements Hook {

        private final PersistenceManager persistenceManager;

        public MoveItemHook(PersistenceManager persistenceManager) {
            this.persistenceManager = persistenceManager;
        }

        public boolean processMessage(Message msg, int flags) {
            if (moveItem(msg)) {
                ExtensionMessage moveItemMsg = (ExtensionMessage) msg;
                OID mobOid = moveItemMsg.getSubject();
                sendBagInvUpdate(mobOid);
                sendStorageInvUpdate(mobOid);
            }
            return true;
        }

        public boolean moveItem(Message msg) {
            ExtensionMessage moveItemMsg = (ExtensionMessage) msg;
            log.debug("MoveItemHook");
            OID mobOid = moveItemMsg.getSubject();
            int containerId = (Integer) moveItemMsg.getProperty("bagNum");
            int slotId = (Integer) moveItemMsg.getProperty("slotNum");
            OID itemOid = (OID) moveItemMsg.getProperty("itemOid");
            int count = (Integer) moveItemMsg.getProperty("count");
            Boolean swap = (Boolean) moveItemMsg.getProperty("swap");
            if (Log.loggingDebug) {
                log.debug("MoveItemHook: mobOid:" + mobOid + " containerId:" + containerId + " slotId:" + slotId + " itemOid:"
                        + itemOid + " count:" + count + " swap:" + swap);
            }
            lock.lock();
            try {

                inventoryCheck(mobOid);
                if (Log.loggingDebug) {
                    Log.debug("MoveItemHook: got move item message with containerId: " + containerId + " and slot: " + slotId + " for item: "
                            + itemOid);
                }
                // Get item and check if we are moving the whole stack, or splitting it
                AgisItem item = getAgisItem(itemOid);
                if (item == null) {
                    if (Log.loggingDebug) {
                        log.debug("MoveItemHook: item: " + itemOid + " is null");
                    }
                    return true;
                }
                InventoryInfo iInfo = getInventoryInfo(mobOid);
                // Is the item equipped? If so, call unequipItem

                if (isItemEquipped(item, mobOid)) {
                    unequipItem(item, mobOid, false);
                    log.warn("moveItem: item is Equipped brake");
                    return false;
                }
                log.debug("MoveItemHook: Check if item js on pet");
                for(int petProfile : iInfo.getPetInventory().keySet()) {
                    log.debug("MoveItemHook: Check pet profile "+petProfile);
                    if (iInfo.getPetInventory().get(petProfile).isItemEquipped(item)) {
                        unequipItem(item, mobOid, false, null, -1, false, true, petProfile);
                        log.warn("moveItem: item is Equipped brake");
                        return false;
                    }
                }


                if (iInfo == null || count <= 0) {
                    log.warn("moveItem: iInfo is null or count is 0");
                    return true;
                }
                ItemLocation itemLocation = getItemLocation(iInfo, itemOid);
                if (itemLocation == null) {
                    // The item is not one of player's bags, fall back to backref
                    OID oldBagId = (OID) item.getProperty(INVENTORY_PROP_BACKREF_KEY);
                    if (oldBagId == null) {
                        log.error("MoveItemHook: Got null inventory backref for item: " + itemOid);
                        return true;
                    }
                    Bag bag = getBag(oldBagId);
                    String storageName = bag.getStorageName();
                    if (storageName.startsWith("Guild_")) {
                    	if(item.isPlayerBound()) {
                    		if(!item.getPlayerBound().equals(mobOid)) {
                    			 log.debug("MoveItemHook: You can't get item bound to diffrent player form guild warehouse");
                                 ExtendedCombatMessages.sendErrorMessage(mobOid, "You dont have permission to do this action");
                                 // FIXME Must be bank
                                 return true;
                    			
                    		}
                    	}
                        int guildPermission = GuildClient.getGuildWhPermition(mobOid);
                        if (Log.loggingDebug) {
                            log.debug("MoveItemHook: storageName=" + storageName + " guildPermission=" + guildPermission);
                        }
                        if (guildPermission != Claim.PERMISSION_OWNER && guildPermission != Claim.PERMISSION_ADD_DELETE) {
                            log.debug("MoveItemHook: You dont have permission to do this action");
                            ExtendedCombatMessages.sendErrorMessage(mobOid, "You dont have permission to do this action");
                            // FIXME Must be bank
                            return true;
                        }
                    }
                    
                    
                    
                    
                    if (!bag.getBank()) {
                        log.debug("MoveItemHook: Old bag is neither player's bag nor bank");
                        ExtendedCombatMessages.sendErrorMessage(mobOid, "Taken Item isn't in the bank.");
                        // FIXME Must be bank
                        return true;
                    }
                    Integer itemPos = bag.findItem(itemOid);
                    if (itemPos != null && itemPos >= 0) {
                        itemLocation = new ItemLocation(bag, itemPos);
                    }
                }
                if (itemLocation == null) {
                    log.error("MoveItemHook: Item location not found for item: " + itemOid);
                    return true;
                }
                if (containerId != -1) {
                    // If the container Id is not -1 then we need to place the item in the specified container/slot
                    swapOrStackItem(mobOid, itemOid, itemLocation, iInfo, containerId, slotId, count);
                }
                log.debug("MoveItemHook end");
            } catch (Exception e) {
                Log.exception("Inventory MoveItem ", e);
            } finally {
                lock.unlock();
            }
            return true;

        }

        private boolean placeItem(OID mobOid, AgisItem item, Bag bag, int slot) {
            boolean rv = bag.putItem(slot, item.getOid());
            if (rv) {
                item.setProperty(INVENTORY_PROP_BACKREF_KEY, bag.getOid());
                item.acquired(mobOid);
                setDirty(item);
            }
            return rv;
        }

        private void setDirty(Entity entity) {
            persistenceManager.setDirty(entity);
        }

        /**
         * Swaps the bag/slots of two items. Takes in the itemOid of the item to move, then the bagNum and slotNum
         * to move it to, finds the item in that location then makes the switch.
         * @param playerOid
         * @param itemOid
         * @param targetBagNum
         * @param targetSlotNum
         * @param count
         */
        private void swapOrStackItem(OID playerOid, OID itemOid, ItemLocation itemLocation, InventoryInfo iInfo, int targetBagNum, int targetSlotNum, int count) {
            OID[] bags = iInfo.getBags();
            OID targetBagOid = bags[targetBagNum];
            Bag targetBag = getBag(targetBagOid);

            AgisItem item = getAgisItem(itemOid);
            if (item  == null) {
                Log.error("Cannot find item " + itemOid);
                return;
            }
            
            OID targetItemOid = targetBag.getItem(targetSlotNum);
            AgisItem targetItem = getAgisItem(targetItemOid);
            if (targetItemOid != null && targetItem == null) {
                Log.error("Cannot find item " + targetItemOid);
                return;
            }
            boolean fullStack = count >= item.getStackSize();
            boolean sameItems = targetItem != null && item.getTemplateID() == targetItem.getTemplateID();
            boolean swap = fullStack && !sameItems;
            Bag sourceBag = itemLocation.bag;

            if (swap) {
                // Now remove both items from their bags
                if (!sourceBag.removeItem(itemOid)) {
                    Log.debug("AgisInventoryPlugin.swapItemPositions failed to remove item: " + itemOid);
                    return;
                }
                if (targetItemOid != null) {
                    if (!targetBag.removeItem(targetItemOid)) {
                        Log.debug("AgisInventoryPlugin.swapItemPositions failed to remove item: " + targetItemOid);
                        return;
                    }
                }
                // Now put item 2 into slot 1
                if (targetItem != null) {
                    if (!placeItem(playerOid, targetItem, sourceBag, itemLocation.slot)) {
                        if (Log.loggingDebug)
                            log.debug("Cannot place item " + targetItem.getOid() + " in slot " + itemLocation.slot + " in bag " + sourceBag.getOid());
                    }
                }
                // And put item 1 into slot 2
                if (!placeItem(playerOid, item, targetBag, targetSlotNum)) {
                    if (Log.loggingDebug)
                        log.debug("Cannot place item " + item.getOid() + " in slot " + targetSlotNum + " in bag " + targetBag.getOid());
                }
                // Work out if the old bag was a storage bag and whether or not it is on the player
                // TODO: clarify why this is needed
                if (sourceBag.getOid().toString().equals(iInfo.getActiveStorage())) {
                    saveNonPlayerStorageBag(sourceBag);
                }
                setDirty(iInfo);
            } else {
                int stackSizeToAdd = fullStack ? item.getStackSize() : count;
                // Make sure the slot the item will be placed in is empty
                if (targetItem == null) {
                    itemOid = generateItem(item.getTemplateID(), item.getName());
                    AgisItem newItem = getAgisItem(itemOid);
                    if (newItem == null) {
                        log.error("MoveItemHook newItem is null:" + newItem);
                        return;
                    }
                    // Use set stack size as we don't need to trigger item gained/lost events
                    item.alterStackSize(playerOid, -stackSizeToAdd);
                    newItem.alterStackSize(playerOid, stackSizeToAdd - 1);
                    if (!placeItem(playerOid, newItem, targetBag, targetSlotNum)) {
                        log.debug("MoveItemHook: Could not place item into destination slot");
                        ExtendedCombatMessages.sendErrorMessage(playerOid, "Could not place item into destination slot");
                    }
                    setDirty(item);
                    setDirty(newItem);
                    setDirty(iInfo);
                } else if (!itemOid.equals(targetItemOid)) {
                    // Check in case it is an item of the same type and the items could be merged.
                    if (sameItems) {
                        if ((targetItem.getStackSize() + stackSizeToAdd) <= targetItem.getStackLimit()) {
                            targetItem.alterStackSize(playerOid, stackSizeToAdd);
                            setDirty(targetItem);
                            if (fullStack) {
                                removeItem(playerOid, itemOid, true);
                                deleteItem(itemOid);
                            } else {
                                // If not full stack, only reduce the item count
                                item.alterStackSize(playerOid, -count);
                                setDirty(item);
                            }
                            return;
                        }
                    }
                    log.debug("MoveItemHook You must place the item in an empty slot.");
                    ExtendedCombatMessages.sendErrorMessage(playerOid, "You must place the item in an empty slot.");
                }
            }
        }

        private ItemLocation getItemLocation(InventoryInfo iInfo, OID itemOid) {
            OID[] bags = iInfo.getBags();
            for (int i = 0; i < bags.length; i++) {
                ItemLocation location = getItemLocation(bags[i], itemOid);
                if (location != null) {
                    return location;
                }
            }
            return getItemLocation(iInfo.getEquipmentItemBag(), itemOid);
        }

        private ItemLocation getItemLocation(OID bagOid, OID itemOid) {
            Bag bag = getBag(bagOid);
            if (bag != null) {
                Integer itemPos = bag.findItem(itemOid);
                if (itemPos != null && itemPos >= 0) {
                    return new ItemLocation(bag, itemPos);
                }
            }
            return null;
        }
    }

    class GenerateItemResponseOid implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage genMsg = (ExtensionMessage) msg;
            OID oid = genMsg.getSubject();
            log.debug("GenerateItemResponseOid: oid :"+oid);
            int templateID = (int) genMsg.getProperty("templateID");
            String itemName = (String) genMsg.getProperty("itemName");
            int count = (int) genMsg.getProperty("count");
            log.debug("GenerateItemResponseOid: oid :"+oid+" templateID:"+templateID+" itemName:"+itemName+" count:"+count);
            
            OID itemOid = generateItem(templateID, itemName);
            log.debug("GenerateItemResponseOid: oid :"+oid+" itemOid:"+itemOid);
            AgisItem item = getAgisItem(itemOid);
            if (count > 1)
                item.alterStackSize(oid, count - 1);
            log.debug("GenerateItemResponseOid: oid :"+oid+" update inv");
            sendBagInvUpdate(oid);
            Engine.getAgent().sendOIDResponse(genMsg, itemOid);

            log.debug("GenerateItemResponseOid: oid :"+oid+" end");
            return true;
        }
    }
    
    
    class GenerateItemAs implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage genMsg = (ExtensionMessage) msg;
            OID oid = genMsg.getSubject();
            if(Log.loggingDebug)log.debug("GenerateItemAs: oid :"+oid);
            OID itemOid = (OID) genMsg.getProperty("itemOid");
            //String itemName = (String) genMsg.getProperty("itemName");
            int count = (int) genMsg.getProperty("count");
            boolean add = (boolean) genMsg.getProperty("add");
            if(Log.loggingDebug)log.debug("GenerateItemAs: oid :"+oid+" itemOid:"+itemOid+"  count:"+count);
            AgisItem item = getAgisItem(itemOid);
            Map<String,Serializable>props =  item.getPropertyMapRef();
             
            OID itemOidDest = generateItem(item.getTemplateID(), item.getName());
            if(Log.loggingDebug)log.debug("GenerateItemAs: oid :"+oid+" itemOid:"+itemOid);
            AgisItem itemDest = getAgisItem(itemOidDest);
            //for()p.keySet()
            for (Map.Entry<String, Serializable> entry : props.entrySet()) {
                if (!entry.getKey().startsWith(":")&&!entry.getKey().equals("inv.backref")) {
                    itemDest.setProperty(entry.getKey(), entry.getValue());
                }
            }
            
            // First check if there is an open slot for the item
            InventoryInfo iInfo = getInventoryInfo(oid);
            boolean hasSpace = hasSpace(oid, item.getTemplateID(),count, 0);
            itemDest.setStackSize(count);
            
            if (hasSpace && add) {
                        addItem(oid, iInfo.getOid(), itemDest.getOid());
                }
        //  if (count > 1)
            if(Log.loggingDebug)log.debug("GenerateItemAs: oid :"+oid+" item props names:"+item.getPropertyMapRef().entrySet());
            if(Log.loggingDebug)log.debug("GenerateItemAs: oid :"+oid+" item props names:"+item.getPropertyMapRef().values());
            if(Log.loggingDebug)log.debug("GenerateItemAs: oid :"+oid+" itemDest props names:"+itemDest.getPropertyMapRef().entrySet());
            if(Log.loggingDebug)log.debug("GenerateItemAs: oid :"+oid+" itemDest props names:"+itemDest.getPropertyMapRef().values());
            if(Log.loggingDebug)log.debug("GenerateItemAs: oid :"+oid+" update inv");
            
            ObjectManagerClient.saveObject(itemOidDest);
            sendBagInvUpdate(oid);
            Engine.getAgent().sendOIDResponse(genMsg, itemOidDest);

            if(Log.loggingDebug)log.debug("GenerateItemAs: oid :"+oid+" end");
            return true;
        }
    }
   
    
    /**
     * Generates the specified item and gives it to the specified player/mob. If the item has randomised stats
     * set to true, it will randomly generate the stats for the item.
     *
     */
    class GenerateItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.generateItemMessage genMsg = (AgisInventoryClient.generateItemMessage)msg;
            OID oid = genMsg.getSubject();
            boolean failIfNotAllItemsAdded = genMsg.failIfNotAllInserted();
            HashMap<Integer, Integer> itemsNotGenerated = new HashMap<Integer, Integer>(genMsg.getItemsToGenerate());
            Log.debug("GenerateItemHook: hit generateItem with item count: " + itemsNotGenerated.size());
            if (failIfNotAllItemsAdded) {
                if (!hasSpace(oid, itemsNotGenerated, 0)) {
                    Engine.getAgent().sendObjectResponse(genMsg, itemsNotGenerated);
                    return true;
                }
            }
            for (int templateID : genMsg.getItemsToGenerate().keySet()) {
                HashMap<Integer, Integer> itemToGenerate = new HashMap<Integer, Integer>();
                itemToGenerate.put(templateID, genMsg.getItemsToGenerate().get(templateID));
                if (!hasSpace(oid, itemToGenerate, 0)) {
                    Log.debug("GenerateItemHook: ran out of space before adding: " + templateID);
                    break;
                }
                //Template tmpl = ObjectManagerClient.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
                //String itemName = tmpl.getName();
                HashMap<String, Serializable> itemProps = new HashMap<String, Serializable>();
                if (genMsg.getItemsToGenerate().size() == 1) {
                    //itemName = (String) genMsg.getProperty("itemName");
                    itemProps = (HashMap) genMsg.getProperty("itemProps");
                }
                if (addItemFromTemplate(oid, templateID, genMsg.getItemsToGenerate().get(templateID), itemProps)) {
                    itemsNotGenerated.remove(templateID);
                    Log.debug("GenerateItemHook: itemsNotGenerated size: " + itemsNotGenerated);
                }
                Log.debug("GenerateItemHook: finished generation and adding of item: " + templateID);
            }
            
            //InventoryClient.addItem(oid, oid, oid, itemOid);
            sendBagInvUpdate(oid);
            if (genMsg.sendResponse()) {
                Engine.getAgent().sendObjectResponse(genMsg, itemsNotGenerated);
            }
            return true;
        }
    }
    
    /**
     * Creates a new AgisItem based on the templateID provided.
     * @param templateID
     * @param itemName
     * @return
     */
    private OID generateItem(int templateID, String itemName) {
        Template itemTemplate = ObjectManagerPlugin.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
        if (itemTemplate == null) {
            Log.error("ITEM: generating item: " + itemName + " failed");
            return null;
        }
        String templateName = itemTemplate.getName();
        Boolean randomisedStats = (Boolean) itemTemplate.get(InventoryClient.ITEM_NAMESPACE, "randomisedStats");
        if (randomisedStats != null && randomisedStats) {
            // Randomise prefix and suffix and see if the template already exists
            Log.debug("ITEM: generating new item: " + templateName + " which has randomised stats.");
            ItemWeight prefix = null;
            ItemWeight suffix = null;
            
            int roll = random.nextInt(100);
            if (roll < 40) {
                // Prefix only
                roll = random.nextInt(itemPrefixes.size());
                prefix = itemPrefixes.get(roll);
                Log.debug("ITEM: prefix name: " + prefix.getItemWeightName());
                templateName = prefix.getItemWeightName() + " " + templateName;
            } else if (roll < 80) {
                // Suffix only
                roll = random.nextInt(itemSuffixes.size());
                suffix = itemSuffixes.get(roll);
                Log.debug("ITEM: suffix name: " + suffix.getItemWeightName());
                templateName = templateName + " " + suffix.getItemWeightName();
            } else {
                // Both prefix and suffix
                roll = random.nextInt(itemPrefixes.size());
                prefix = itemPrefixes.get(roll);
                roll = random.nextInt(itemSuffixes.size());
                suffix = itemSuffixes.get(roll);
                Log.debug("ITEM: prefix name: " + prefix.getItemWeightName() + "; suffix name: " + suffix.getItemWeightName());
                templateName = prefix.getItemWeightName() + " " + templateName + " " + suffix.getItemWeightName();
            }
            
            // Try get template, if successful, return
            //Template baseTemplate = itemTemplate;
            //itemTemplate = ObjectManagerClient.getTemplate(templateName);
            //if (itemTemplate == null) {
            Log.debug("ITEM: randomised template: " + templateName + " does not yet exist.");
            createNewItemTemplate(itemTemplate, templateName, prefix, suffix);
            //}
        }
        
        Template overrideTemplate = new Template();
        overrideTemplate.put(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT, true);
        OID itemOid = ObjectManagerClient.generateObject(templateID, ObjectManagerPlugin.ITEM_TEMPLATE, overrideTemplate);
        AgisItem item = getAgisItem(itemOid);
        if (itemName != null && !itemName.equals("")) {
            item.setName(itemName);
        }
        return itemOid;
    }
    
    /** 
     * Creates a new item template for an item using the given prefix and suffix item weights.
     * @param itemTemplate
     * @param newTemplateName
     * @param prefix
     * @param suffix
     * @return
     */
    private Template createNewItemTemplate(Template itemTemplate, String newTemplateName, 
            ItemWeight prefix, ItemWeight suffix) {
        Log.debug("ITEM: creating new item template with name: " + newTemplateName);
        Template newItemTemplate = itemTemplate;
        newItemTemplate.setName(newTemplateName);
        newItemTemplate.setTemplateID(itemTemplate.getTemplateID() * -1);
        float totalStatWeight = 0.0f;
        double energyCost = 0.0;
        HashMap<String, Integer> statWeights = new HashMap<String, Integer>();
        // Set prefix/suffix data
        if (prefix != null) {
            totalStatWeight += prefix.getTotalStatWeight();
            if (prefix.getStat1() != null && prefix.getWeight1() > 0) {
                statWeights.put(prefix.getStat1(), prefix.getWeight1());
            }
            if (prefix.getStat2() != null && prefix.getWeight2() > 0) {
                statWeights.put(prefix.getStat2(), prefix.getWeight2());
            }
            if (prefix.getStat3() != null && prefix.getWeight3() > 0) {
                statWeights.put(prefix.getStat3(), prefix.getWeight3());
            }
        }
        if (suffix != null) {
            totalStatWeight += suffix.getTotalStatWeight();
            if (suffix.getStat1() != null && suffix.getWeight1() > 0) {
                if (statWeights.containsKey(suffix.getStat1())) {
                    statWeights.put(suffix.getStat1(), suffix.getWeight1() + statWeights.get(suffix.getStat1()));
                } else {
                    statWeights.put(suffix.getStat1(), suffix.getWeight1());
                }
            }
            if (suffix.getStat2() != null && suffix.getWeight2() > 0) {
                if (statWeights.containsKey(suffix.getStat2())) {
                    statWeights.put(suffix.getStat2(), suffix.getWeight2() + statWeights.get(suffix.getStat2()));
                } else {
                    statWeights.put(suffix.getStat2(), suffix.getWeight2());
                }
            }
            if (suffix.getStat3() != null && suffix.getWeight3() > 0) {
                if (statWeights.containsKey(suffix.getStat3())) {
                    statWeights.put(suffix.getStat3(), suffix.getWeight3() + statWeights.get(suffix.getStat3()));
                } else {
                    statWeights.put(suffix.getStat3(), suffix.getWeight3());
                }
            }
        }
        // Stat Quantity = ((Item Quality * Slot Modifier * Grade Modifier)^1.7 / Total Stat Weight * Current Stat Weight)^(1/1.7)/StatMod *Round Down*
        float itemQuality = (Integer) itemTemplate.get(InventoryClient.ITEM_NAMESPACE, "itemQuality");
        String slot = (String) itemTemplate.get(InventoryClient.ITEM_NAMESPACE, "slot");
        float slotModifier = getSlotModifier(slot);
        int itemGrade = (Integer) itemTemplate.get(InventoryClient.ITEM_NAMESPACE, "itemGrade");
        float gradeModifier = getGradeModifier(itemGrade);
        //HashMap<String, Integer> itemStats = (HashMap) newItemTemplate.get(InventoryClient.ITEM_NAMESPACE, "bonusStats");
        HashMap<String, Integer> itemStats = new HashMap<String, Integer>();
        double totalStatsValue = Math.pow((itemQuality * slotModifier * gradeModifier), 1.7);
        Log.debug("CALC: totalStatsValue: " + totalStatsValue);
        for (String statName: statWeights.keySet()) {
            float statMod = getStatModifier(statName);
            Log.debug("CALC: itemQuality: " + itemQuality + "; slotMod: " + slotModifier + "; gradeModifier: " + gradeModifier + 
                    "; totalStatWeight: " + totalStatWeight + "; statWeight: " + (float)statWeights.get(statName));
            double statCalc1 = totalStatsValue / totalStatWeight * (float)statWeights.get(statName);
            Log.debug("CALC: statCalc1: " + statCalc1 + "; statMod: " + statMod);
            double statValue = Math.floor(Math.pow(statCalc1, (1.0/1.7))/statMod);
            Log.debug("CALC: final stat value for " + statName + " is: " + statValue);
            itemStats.put(statName, (int)statValue);
            energyCost += Math.pow(statValue * statMod, 1.7);
        }
        energyCost = Math.ceil(energyCost);
        newItemTemplate.put(InventoryClient.ITEM_NAMESPACE, "bonusStats", itemStats);
        newItemTemplate.put(InventoryClient.ITEM_NAMESPACE, "energyCost", (int) energyCost);
        ObjectManagerClient.registerTemplate(newItemTemplate);
        return newItemTemplate;
    }
    
//    /**
//     * Un-used at the moment. Eventually will be used to create an item to summon a pet.
//     * @author Andrew
//     *
//     */
//    class CreatePetItemHook implements Hook {
//        public boolean processMessage(Message msg, int flags) {
//            AgisInventoryClient.generateItemMessage genMsg = (AgisInventoryClient.generateItemMessage)msg;
//            OID oid = genMsg.getSubject();
//            String templateName = (String) genMsg.getProperty("itemName");
//            //Long itemOid = generateItem(templateName);
//            //InventoryClient.addItem(oid, oid, oid, itemOid);
//            Log.debug("CreatePetItemHook: finished generation and adding of item: " + templateName);
//            return true;
//        }
//    }
//
//    /**
//     * Creates a new item template for an item using the given prefix and suffix item weights.
//     * @param itemTemplate
//     * @param newTemplateName
//     * @param petRef
//     * @return
//     */
//    private Template createPetItemTemplate(Template itemTemplate, String newTemplateName,
//            String petRef) {
//        Log.debug("ITEM: creating new item template with name: " + newTemplateName);
//        Template newItemTemplate = itemTemplate;
//        newItemTemplate.setName(newTemplateName);
//        newItemTemplate.put(InventoryClient.ITEM_NAMESPACE, "petRef", petRef);
//        ObjectManagerClient.registerTemplate(newItemTemplate);
//        return newItemTemplate;
//    }
    
    /**
     * Hook for the RemoveSpecificItem Message Removes the specified item (using its oid) from the 
     * specified player/mob. If the removeStack property is set to true it will remove the full 
     * item stack from the player.
     *
     */
    class RemoveSpecificItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.removeSpecificItemMessage removeMsg = (AgisInventoryClient.removeSpecificItemMessage)msg;
            OID oid = removeMsg.getSubject();
            HashMap<OID, Integer> itemsToRemove = removeMsg.getItemsToRemove();
            boolean removeStack =  removeMsg.getRemoveStack();
            lock.lock();
            try {
                for (OID itemOid : itemsToRemove.keySet()) {
                    removeSpecificItem(oid, itemOid, itemsToRemove.get(itemOid), removeStack);
                }
            } finally {
                lock.unlock();
            }
            //Send an inventory update
            sendBagInvUpdate(oid);
            Engine.getAgent().sendBooleanResponse(removeMsg, true); 
            return true;
        }
    }
    
    boolean removeSpecificItem(OID oid, OID itemOid, int numToRemove, boolean removeStack) {
        InventoryInfo iInfo = getInventoryInfo(oid);
        lock.lock();
        try {
            AgisItem item = getAgisItem(itemOid);
            if (item == null)
                return false;
            if (!removeStack) {
                // Just reduce the stack size
                item.alterStackSize(oid, -numToRemove);
                Engine.getPersistenceManager().setDirty(item);
                Engine.getPersistenceManager().setDirty(iInfo);
                Log.debug("ITEM: reduced stack: " + itemOid + " of item type: " + item.getName() + " to size: " + item.getStackSize());
            }
            if (item.getStackSize() < 1 || removeStack) {
            	item.alterStackSize(oid, -item.getStackSize());
                unequipItem(item, oid, false);
                OID rootBagOid = oid;
                if (rootBagOid == null) {
                    log.debug("removeItem: cant find rootBagOid");
                    return false;
                }
                Boolean result = removeItemFromBag(rootBagOid, itemOid);
                if (result)
                    item.unacquired(rootBagOid);
                
                return result;
            }
        } finally {
            lock.unlock();
        }
        return true;
    }

    /**
     * Removes the generic item (using its name) from the specified player/mob. If the removeStack
     * property is set to true it will remove the full item stack from the player.
     *
     */
    class CountGenericItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.countGenericItemMessage countMsg = (AgisInventoryClient.countGenericItemMessage)msg;
            OID oid = countMsg.getSubject();
            Integer itemId = countMsg.getTemplateId();
            ArrayList<OID> itemOids = findItemStacks(oid, itemId);
            int count = 0;
            if (itemOids!=null && itemOids.size() > 0) {
                for (OID itemOid: itemOids) {
                    AgisItem tempItem = getAgisItem(itemOid);
                    Log.debug("ITEM: stackSize " + tempItem.getStackSize() + ", stackLimit: " + tempItem.getStackLimit() + "for item: " + itemOid);
                    if(tempItem.getStackSize() > 0) {
                        count = count + tempItem.getStackSize();
                    }
                }
            }
            Log.debug("CountGenericItemHook: cont="+count+" of item template "+itemId+" for ply="+oid);
            Engine.getAgent().sendIntegerResponse(countMsg, count);
            return true;
        }
    }
    
    /**
     * Removes the generic item (using its name) from the specified player/mob. If the removeStack
     * property is set to true it will remove the full item stack from the player.
     *
     */
    class RemoveGenericItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.removeGenericItemMessage removeMsg = (AgisInventoryClient.removeGenericItemMessage)msg;
            OID oid = removeMsg.getSubject();
            HashMap<Integer, Integer> itemsToRemove = removeMsg.getItemsToRemove();
            boolean removeStack = removeMsg.removeStack();
            Log.debug("RemoveGenericItemHook: oid="+oid+" itemsToRemove="+itemsToRemove+" removeStack="+removeStack);
            lock.lock();
            try {
                for (int itemID : itemsToRemove.keySet()) {
                    removeGenericItem(oid, itemID, itemsToRemove.get(itemID), removeStack);
                }
            } finally {
                lock.unlock();
            }
            //Send an inventory update
            sendInvUpdate(oid);
        //    sendEquippedInvUpdate(oid);
            Engine.getAgent().sendBooleanResponse(removeMsg, true); 
            Log.debug("RemoveGenericItemHook: oid="+oid+" END");
            
            return true;
        }
    }
    
    void removeGenericItem(OID oid, int itemID, int numToRemove, boolean removeStack) {
        Log.debug("removeGenericItem: oid="+oid+" itemID="+itemID+" numToRemove="+numToRemove+" removeStack="+removeStack);
        InventoryInfo iInfo = getInventoryInfo(oid);
        lock.lock();
        try {
            ArrayList<OID> itemOids = findItemStacks(oid, itemID);
            for (OID itemOid: itemOids) {
                AgisItem item = getAgisItem(itemOid);
                Log.debug("ITEM: found stack: " + itemOid + " of item type: " + item.getName() + " to remove from.");
                if (item == null)
                    continue;
                if (!removeStack) {
                    // Just reduce the stack size
                    int stackSize = item.getStackSize();
                    item.alterStackSize(oid, -numToRemove);
                    numToRemove = numToRemove - stackSize;
                    Engine.getPersistenceManager().setDirty(item);
                    Engine.getPersistenceManager().setDirty(iInfo);
                    Log.debug("ITEM: reduced stack: " + itemOid + " of item type: " + item.getName() + " to size: " + item.getStackSize());
                }
                if (item.getStackSize() < 1 || removeStack) {
                    unequipItem(item, oid, false);
                    OID rootBagOid = oid;
                    if (rootBagOid == null) {
                        log.debug("removeItem: cant find rootBagOid");
                        continue;
                    }
                    Boolean result = removeItemFromBag(rootBagOid, itemOid);
                    if (result) {
                        item.unacquired(rootBagOid);
                    }
                    Log.debug("ITEM: removing from stack: " + itemOid + " of item type: " + item.getName() + " had result: " + result);
                }
                if (numToRemove < 1)
                    break;
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Checks if the player has enough of the specified currency against the requested amount.
     * Sends a boolean response to indicate if the player has enough.
     * @author Andrew Harrison.
     *
     */
    class CheckCurrencyHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.checkCurrencyMessage purMsg = (AgisInventoryClient.checkCurrencyMessage)msg;
            OID oid = purMsg.getSubject();
            //OID mobOid = OID.fromLong((Long) purMsg.getProperty("mobOid"));
            int currencyID = purMsg.getCurrencyID();
            long cost = purMsg.getCount();
            long currencyAmount = getMobCurrency(oid, currencyID);
            
            if (currencyAmount < cost) {
                // Send boolean response back saying it was a success
                Engine.getAgent().sendBooleanResponse(purMsg, false);
                Log.debug("CURRENCY: not enough money");
                return true;
            }
            Log.debug("CURRENCY: has enough money");
            
            // Send boolean response back saying it was a success
            Engine.getAgent().sendBooleanResponse(purMsg, true);
            return true;
        }
    }
    /**
     * Checks if the player has enough of the specified currency against the requested amount.
     * Sends a boolean response to indicate if the player has enough.
     * @author Andrew Harrison.
     *
     */
    class CheckCurrencyOfflineHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.checkCurrencyOfflineMessage purMsg = (AgisInventoryClient.checkCurrencyOfflineMessage)msg;
            OID oid = purMsg.getPlayerOid();
            //OID mobOid = OID.fromLong((Long) purMsg.getProperty("mobOid"));
            int currencyID = purMsg.getCurrencyID();
            long cost = purMsg.getCount();
            Entity entity = null;
            try {
                entity = Engine.getDatabase().loadEntity(oid, Namespace.BAG);
            }
            catch (AORuntimeException e) {
                Engine.getAgent().sendBooleanResponse(msg, false);
                return true;
            }
           
            try {
                InventoryInfo iInfo= (InventoryInfo)entity;
                //Entity e = Engine.getDatabase().loadEntity(oid, Namespace.getNamespace("NS.inv"));                
                long currencyAmount = getPlayerCurrency(oid, currencyID,iInfo);
                if (currencyAmount < cost) {
                    // Send boolean response back saying it was a success
                    Engine.getAgent().sendBooleanResponse(purMsg, false);
                    Log.debug("CheckCurrencyOfflineHook: not enough money");
                    return true;
                }
                Log.debug("CheckCurrencyOfflineHook: has enough money");
                
                // Send boolean response back saying it was a success
                Engine.getAgent().sendBooleanResponse(purMsg, true);
            } catch (Exception e) {
                Engine.getAgent().sendBooleanResponse(purMsg, false);
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return true;
        }
    }
    
    long getPlayerCurrency(OID mobOid, int currencyID,InventoryInfo iInfo){
         Currency c = Agis.CurrencyManager.get(currencyID);
        if(c==null) {
            log.debug("CURRENCY: cant get currency id "+currencyID);
            return 0l;
        }
        if (c.getExternal()) {
            OID accountID = Engine.getDatabase().getAccountOid(mobOid);
            //OID accountID = (OID) EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, WorldManagerClient.ACCOUNT_PROPERTY);
            return authDB.getAccountCoinAmount(accountID);
        } else {
            //InventoryInfo iInfo = getInventoryInfo(mobOid);
            return iInfo.getCurrencyAmount(currencyID, true);
        }
    }
    
    /**
     * Gets the amount of the specified currency the mob specified has.
     * @param mobOid
     * @param currencyID
     * @return
     */
    public long getMobCurrency(OID mobOid, int currencyID) {
        Log.debug("CURRENCY: getting mob currency: " + currencyID + " for mob: " + mobOid);
        Currency c = Agis.CurrencyManager.get(currencyID);
        if (c == null) {
            log.debug("CURRENCY: cant get currency id " + currencyID);
            return 0l;
        }
        if (c.getExternal()) {
            OID accountID = Engine.getDatabase().getAccountOid(mobOid);
            // OID accountID = (OID) EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, WorldManagerClient.ACCOUNT_PROPERTY);
            return authDB.getAccountCoinAmount(accountID);
        } else {
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            if (iInfo != null)
                return iInfo.getCurrencyAmount(currencyID, true);
            Entity entity = null;
            try {
                entity = Engine.getDatabase().loadEntity(mobOid, Namespace.BAG);
            } catch (AORuntimeException e) {

                return 0L;
            }

            iInfo = (InventoryInfo) entity;
            return iInfo.getCurrencyAmount(currencyID, true);
        }
    }
    
    /**
     * Changes the amount of the specified currency for the player 
     * by the specified amount.
     * @param mobOid
     * @param currencyID
     * @param delta
     */
    public void alterMobCurrency(OID mobOid, int currencyID, long delta) {
        Currency c = Agis.CurrencyManager.get(currencyID);
        log.debug("alterMobCurrency CURRENCY: getting currency: " + currencyID+" delta: "+delta);
        if (c.getExternal()) {
        //  OID accountID = (OID)EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, WorldManagerClient.ACCOUNT_PROPERTY);
            OID accountID = Engine.getDatabase().getAccountOid(mobOid);
            authDB.alterAccountCoinAmount(accountID, delta);
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            ExtendedCombatMessages.sendCurrencies(mobOid, iInfo.getCurrentCurrencies());
        } else {
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            if (iInfo == null) {

                Entity entity = null;
                try {
                    entity = Engine.getDatabase().loadEntity(mobOid, Namespace.BAG);
                } catch (AORuntimeException e) {

                }
                iInfo = (InventoryInfo) entity;
            }
            if (iInfo != null) {
                iInfo.alterCurrencyAmount(currencyID, delta);
                Engine.getPersistenceManager().setDirty(iInfo);
                ExtendedCombatMessages.sendCurrencies(mobOid, iInfo.getCurrentCurrencies());
            }else {
                log.error("alterMobCurrency mobOid="+mobOid+" InventoryInfo is null");
            }
        }
        
        
    }
    
    /**
     * Handles the request to get the Merchant List. Calls sendMerchantList.
     * @author Andrew
     *
     */
    class GetMerchantListHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage getMsg = (ExtensionMessage)msg;
            OID oid = getMsg.getSubject();
            int merchantTable = (Integer) getMsg.getProperty("merchantTable");
            //sendMerchantList(oid, merchantTable);
            return true;
        }
    }
    
    /**
     * Handles the request to purchase an item. Checks if the merchant does
     * sell the item, then if the player has enough currency to purchase it. 
     * If the checks are met, the currency is deducted and the item is generated
     * and given to the player.
     * @author Andrew Harrison.
     *
     */
    class PurchaseItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.purchaseItemMessage purMsg = (AgisInventoryClient.purchaseItemMessage)msg;
            OID oid = purMsg.getSubject();
            inventoryCheck(oid);
            //OID mobOid = OID.fromLong((Long) purMsg.getProperty("mobOid"));
            int templateID = purMsg.getItemID();
            int count = purMsg.getCount();
            if(count < 1) {
                log.debug("Player "+oid+" try buy "+count+" of item "+templateID);
                count = 1;
            }
               Map<String, Serializable> props = new HashMap<String, Serializable>();
                TargetedExtensionMessage resultMsg = new TargetedExtensionMessage( WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
          synchronized(oid.toString().intern()) {
                
            Log.debug("PURCHASE: attempting to purchase " + count + " of item " + templateID);
            props.put("ext_msg_subtype", "item_purchase_result");
            Template itemTemplate = ObjectManagerPlugin.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
            props.put("itemName", itemTemplate.getName());
             // First make sure the merchant can sell this item
            //int tableNum = (Integer) EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, "merchantTable");
            /*int tableNum = (Integer) purMsg.getProperty("tableNum");
            MerchantTable mt = merchantTables.get(tableNum);
            
            if (!mt.getItems().contains(templateID)) {
                props.put("result", "no_item");
                Engine.getAgent().sendBroadcast(resultMsg);
                Log.error("MERCHANT: table " + tableNum + " does not contain item: " + templateID);
                return true;
            }*/
            // Now make sure the player can afford the item
            Template tmpl = ObjectManagerPlugin.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
            int purchaseCurrency = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "purchaseCurrency");
            Long cost = (Long) tmpl.get(InventoryClient.ITEM_NAMESPACE, "purchaseCost");
            float bonusModp = 0;
            long bonusMod = 0;
            InventoryInfo iInfo = getInventoryInfo(oid);
            if(iInfo.getBonuses().containsKey("PriceMerchant")) {
                bonusMod =iInfo.getBonuses().get("PriceMerchant").GetValue();
                bonusModp =iInfo.getBonuses().get("PriceMerchant").GetValuePercentage();
            }
            if(globalEventBonusesArray.containsKey("PriceMerchant")) {
                bonusMod += globalEventBonusesArray.get("PriceMerchant").GetValue();
                bonusModp += globalEventBonusesArray.get("PriceMerchant").GetValuePercentage();
            }
            long PriceValue = Math.round(Math.ceil( bonusMod+cost +(cost * bonusModp)/100f));
            if(PriceValue < 0)
                PriceValue = 0l;
            PriceValue= count * PriceValue;
            log.debug("PurchaseItemHook PURCHASE: cost="+cost+" count="+count+" bonusMod="+bonusMod+" bonusModp="+bonusModp+" PriceValue="+PriceValue);
            
            
            long currencyAmount = getMobCurrency(oid, purchaseCurrency);
            Currency c = Agis.CurrencyManager.get(purchaseCurrency);
            if (currencyAmount < (PriceValue)) {
                props.put("result", "insufficient_funds");
                props.put("currency", c.getCurrencyName());
                Engine.getAgent().sendBroadcast(resultMsg);
                Log.debug("PURCHASE: not enough funds: " + currencyAmount + " for cost: " + (cost * count));
                // Send boolean response back saying it was a success
                Engine.getAgent().sendBooleanResponse(purMsg, false);
                return true;
            }
            Log.debug("PURCHASE: has enough money");
            
            // Check if the player has space
            if (!hasSpace(oid, templateID, count, 0)) {
                props.put("result", "insufficient_space");
                Engine.getAgent().sendBroadcast(resultMsg);
                ExtendedCombatMessages.sendErrorMessage(oid, "You do not have enough space in your inventory to purchase that item.");
                // Send boolean response back saying it was a success
                Log.debug("PURCHASE: not enough space: ");
                Engine.getAgent().sendBooleanResponse(purMsg, false);
                return true;
            }
            
            boolean itemAdded = addItemFromTemplate(oid, templateID, count, null);
            //boolean itemAdded = InventoryClient.addItem(oid, oid, oid, itemOid);
                if (itemAdded) {
                    long delta = (-PriceValue);
                    alterMobCurrency(oid, purchaseCurrency, delta);
                    alterCurrecyLog(oid,null, purchaseCurrency, delta,"purchaseItemMessage");
                    sendBagInvUpdate(oid);
                }
            }
            Log.debug("PurchaseItemHook: finished generation and adding of item: " + templateID);
            props.put("result", "success");
            Engine.getAgent().sendBroadcast(resultMsg);
            // Send boolean response back saying it was a success
            Engine.getAgent().sendBooleanResponse(purMsg, true);
            return true;
        }
    }
    
    /**
     * Removes an item from a players inventory and increases the players matching currency type by the value of the item.
     * First checks if the item is an Account item - if so, sellAccountItem() is called;
     * @author Andrew Harrison
     *
     */
    class SellItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage removeMsg = (ExtensionMessage)msg;
            OID oid = removeMsg.getSubject();
            
            String sellType = (String)removeMsg.getProperty("sellType");
            if (sellType.equals("Account")) {
                int itemID = (Integer) removeMsg.getProperty("itemID");
                Log.debug("SELL: got account sell for item: " + itemID);
                //sellAccountItem(oid, itemID);
            }
            OID itemOid = (OID) removeMsg.getProperty("itemOid");
            Log.debug("SELL: got sell for item: " + itemOid);
            lock.lock();
            try {
                AgisItem item = getAgisItem(itemOid);
                if (item == null)
                    return true;
                OID oldBagID = (OID) item.getProperty(INVENTORY_PROP_BACKREF_KEY);
                log.debug("SellItemHook: item:"+item+" oldBagID:"+oldBagID);
                if (oldBagID==null) {
                    log.debug("SellItemHook: item is not assign to bag cannot sell item");
                    EventMessageHelper.SendErrorEvent(oid, EventMessageHelper.CANNOT_SELL_ITEM, item.getTemplateID(), "");
                    return true;
                }
                unequipItem(item, oid, false);
                OID rootBagOid = oid;
                if (rootBagOid == null) {
                    log.debug("removeItem: cant find rootBagOid");
                    return true;
                }
                Log.debug("SELL: got root bag");
                if (item.getPurchaseCurrency() < 1) {
                    EventMessageHelper.SendErrorEvent(oid, EventMessageHelper.CANNOT_SELL_ITEM, item.getTemplateID(), "");
                    return true;
                }
                // Check if item is sellable
                boolean sellable = item.getBooleanProperty("sellable");
                if (!sellable) {
                    EventMessageHelper.SendErrorEvent(oid, EventMessageHelper.CANNOT_SELL_ITEM, item.getTemplateID(), "");
                    return true;
                }
                
             
                boolean removed = removeItemFromBag(rootBagOid, itemOid);
                if (removed) {
                    Log.debug("SELL: removed Item");
                    long price = 0;
                    int currencyType = 0;
                    long amount = item.getPurchaseCost();
                    Currency c = Agis.CurrencyManager.get(item.getPurchaseCurrency());
                    if (c != null)
                        while (c.getCurrencyThatConvertsToThis() != null) {
                            c = c.getCurrencyThatConvertsToThis();
                            amount *= c.getConversionAmountReq();
                        }
                    price += amount;
                    currencyType = c.getCurrencyID();
                    log.debug("SellItemHook: convert Currency :" + currencyType + " price:" + price);

                    float bonusModp = 0;
                    InventoryInfo iInfo = getInventoryInfo(oid);
                    if (iInfo.getBonuses().containsKey("SellFactor")) {
                        bonusModp = iInfo.getBonuses().get("SellFactor").GetValuePercentage();
                    }
                    if(globalEventBonusesArray.containsKey("SellFactor")) {
                        bonusModp += globalEventBonusesArray.get("SellFactor").GetValuePercentage();
                    }
                    log.debug("SellItemHook: convert Currency :" + currencyType + " price:" + price+" SELL_FACTOR="+SELL_FACTOR+" bonusModp="+bonusModp+"%");
                    float _delta = price * (SELL_FACTOR + (SELL_FACTOR * bonusModp / 100f));
                    double p = (_delta - Math.floor(_delta));
                    log.debug("SellItemHook: price per item :" + _delta+" p="+p);
                    //log.debug("SellItemHook: round test:" + Math.round(2.4f)+" "+Math.round(2.49f)+" "+ Math.round(2.5f)+" "+ Math.round(2.51f)+" "+ Math.round(2.6f));
                    
                    long delta = Math.round(_delta);
                    if(p==0.5d)
                        delta--;
                    log.debug("SellItemHook: price per item (round):" + delta);
                    
                    if (delta == 0 && item.getPurchaseCost() > 0)
                        delta = 1;
                    delta *= item.getStackSize();
                    log.debug("SellItemHook: final price:" + delta);
                    alterMobCurrency(oid, /* item.getPurchaseCurrency() */currencyType, delta);
                    alterCurrecyLog(oid,null, currencyType, delta,"SellItemHook");

                    item.unacquired(rootBagOid);
                    //TODO: We should look at adding some list or something to hold sold items for buy back
                } else {
                    Log.debug("SELL: remove failed");
                }
            } finally {
                lock.unlock();
            }
            //Send an inventory update
            sendBagInvUpdate(oid);
            return true;
        }
    }
    
    /**
     * Deprecated. Handles the request to generate numerous of an item. This functionality is found in GenerateItemHook.
     */
    class AlterItemCountHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage purMsg = (ExtensionMessage)msg;
            OID playerOid = purMsg.getSubject();
            Log.debug("ALTER: got item alter message");
            int templateID = (Integer) purMsg.getProperty("itemID");
            int count = (Integer) purMsg.getProperty("count");
            Template tmpl = ObjectManagerPlugin.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
            if (tmpl == null) {
                Log.error("PICKUP: item ID: " + templateID + " does not exist");
                return true;
            }
            
            addItemFromTemplate(playerOid, templateID, count, null);
            ChatClient.sendObjChatMsg(playerOid, 2, "Received " + tmpl.getName() + " x" + count);
            
            sendBagInvUpdate(playerOid);
            return true;
        }
    }
    
    /**
     * Generates the specified item and gives it to the specified player/mob. Should be called when a player
     * walks into (picks up) an item.
     *
     */
    class PickupItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage purMsg = (ExtensionMessage)msg;
            OID oid = purMsg.getSubject();
            inventoryCheck(oid);
             int templateID = (Integer) purMsg.getProperty("itemID");
            int count = (Integer) purMsg.getProperty("count");
            int itemsAcquired = 0;
            if (templateID == -1) {
                HashMap<Integer, Float> lootTables = (HashMap) purMsg.getProperty("lootTables");
                Log.debug("LOOT: num loot tables: " + lootTables.size());
                for (int lootTable: lootTables.keySet()) {
                    float tableChance = lootTables.get(lootTable);
                    float roll = random.nextFloat() * 100f;
                    Log.debug("LOOT: roll for loot table: " + lootTable + " is: " + roll + " with tablechance: " + tableChance);
                    if (roll < tableChance) {
                        // Check the loot table number
                        Log.debug("LOOT: lootManager has: " + Agis.LootTableManager.getMap());
                        LootTable lt = Agis.LootTableManager.get(lootTable);
                        int itemNum = lt.getNewRandomItemNum();
                        Log.debug("LOOT: randomised item number: " + itemNum);
                        if (itemNum >= 0) {
                            templateID = lt.getItems().get(itemNum);
                            count = lt.getRandomCountOfItem(itemNum);
                            if (accountItemAcquired(oid, templateID, count) == ItemAcquireResult.SUCCESS)
                                itemsAcquired++;
                        }else {
                            Log.debug("LOOT: roll get no item for loot Table "+lt.getID());
                        }
                    }
                }
            } else {
                if (accountItemAcquired(oid, templateID, count) == ItemAcquireResult.SUCCESS)
                    itemsAcquired++;
            }
            sendBagInvUpdate(oid);
            if (itemsAcquired == 0) {
                // There was no items
                ExtendedCombatMessages.sendCombatText(oid, "No items found", 9);
            }
            return true;
        }
    }
    
    /**
     * Old function that should be deleted. Do not use.
     * @param oid
     * @param templateID
     * @param count
     * @return
     */
    protected ItemAcquireResult accountItemAcquired(OID oid, int templateID, int count) {
        if(Log.loggingDebug)Log.debug("PICKUP: got pickup message with player: " + oid + " and item: " + templateID);
        Template tmpl = ObjectManagerPlugin.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
        if (tmpl == null) {
            Log.error("PICKUP: item ID: " + templateID + " does not exist");
            return ItemAcquireResult.ITEM_DOESNT_EXIST;
        }
        
        // Check if the player has space
        if (!hasSpace(oid, templateID, count, 0)) {
            ExtendedCombatMessages.sendErrorMessage(oid, "You do not have enough space in your inventory to receive that item.");
            return ItemAcquireResult.STACK_LIMIT_REACHED;
        }

        if(Log.loggingDebug) Log.debug("AcquireItem: finished generation and adding of item: " + templateID);
        if (addItemFromTemplate(oid, templateID, count, null))
            return ItemAcquireResult.SUCCESS;
        else
            return ItemAcquireResult.UNKNOWN_FAILURE;
    }
    
    /**
     * Handles the EquippedItemUsedMessage. Rolls a chance to reduce the durability of an item. Should
     * be called when an item has been used by some process such as a combat ability, crafting, 
     * resource gathering etc.
     * @author Andrew Harrison
     *
     */
    class EquippedItemUsedHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            EquippedItemUsedMessage purMsg = (EquippedItemUsedMessage)msg;
            OID oid = purMsg.getSubject();
            
            String useType = (String) purMsg.getUseType();
            String itemSubType = (String) purMsg.getItemSubType();
           if(Log.loggingDebug) log.debug("EquippedItemUsedHook: obj:"+oid+" useType:"+useType+" itemSubType="+itemSubType);
            OID itemOID = null;
            Random rand = new Random();
           
            if (useType.equals("Attack")) {
                EquipMap equipMap = getEquipMap(oid);
                 List <AgisEquipSlot> slots = AgisEquipSlot.getSlotByType("Weapon");
                for(AgisEquipSlot aes : slots) {
                    if(!aes.getName().startsWith("Set_")) {
                        itemOID = equipMap.get(aes);
                        if(Log.loggingDebug) Log.debug("EquippedItemUsedHook " + aes.getName() + " " + itemOID);

                        if (itemOID != null && rand.nextInt(100) < DURABILITY_LOSS_CHANCE_FROM_ATTACK)
                            reduceDurabilityOfItem(oid, itemOID, true);
                        if (itemOID != null)
                            applySkillExp(oid, itemOID, purMsg.getSkillId(), true);
                    }
                }

            } else if (useType.equals("Defend")) {
                EquipMap equipMap = getEquipMap(oid);
                // Roll a chance for each item
                 List <AgisEquipSlot> slots = AgisEquipSlot.getSlotByType("Armor");
                    for(AgisEquipSlot aes : slots) {
                        if(!aes.getName().startsWith("Set_")) {
                            itemOID = equipMap.get(aes);
                            if (itemOID != null && rand.nextInt(100) < DURABILITY_LOSS_CHANCE_FROM_DEFEND)
                                reduceDurabilityOfItem(oid, itemOID, true);
                        }
                    }
            } else if (useType.equals("Craft")) {
                EquipMap equipMap = getEquipMap(oid);
                List <AgisEquipSlot> slots = AgisEquipSlot.getSlotByType("Tool");
                for(AgisEquipSlot aes : slots) {
                    if(!aes.getName().startsWith("Set_")) {
                        itemOID = equipMap.get(aes);
                        if (itemOID != null && rand.nextInt(100) < DURABILITY_LOSS_CHANCE_FROM_CRAFT)
                            reduceDurabilityOfItem(oid, itemOID, true);
                    }
                }
                
            } else if (useType.equals("Gather")) {
                EquipMap equipMap = getEquipMap(oid);
                List <AgisEquipSlot> slots = AgisEquipSlot.getSlotByType("Tool");
                for(AgisEquipSlot aes : slots) {
                    if(!aes.getName().startsWith("Set_")) {
                        itemOID = equipMap.get(aes);
                        if (itemOID != null && rand.nextInt(100) < DURABILITY_LOSS_CHANCE_FROM_GATHER)
                            reduceDurabilityOfItem(oid, itemOID, true);
                    }
                }
                
            }
            return true;
        }
    }
    
    void applySkillExp(OID playerOid, OID itemOid,int skillId, boolean isWeapon) {
        AgisItem item = getAgisItem(itemOid);
        if (item == null) {
            log.error("applySkillExp: got null item: " + itemOid);
            return;
        }
        if(skillId < 1) {
            log.debug("applySkillExp: skillId must be > 0 ");
            return;
        }
        if (item.getProperty("skillExp") != null) {
            int skillExp = (Integer)item.getProperty("skillExp");
            if(Log.loggingDebug) log.debug("applySkillExp: item "+itemOid+" have skillExp "+skillExp);
            if(skillExp > 0)
                CombatClient.abilityUsed(playerOid,skillId,skillExp,1);
            
        }else {
            log.error("applySkillExp: item not have skillExp param ");
        }
        log.debug("applySkillExp: end");
    }
    
    void reduceDurabilityOfItem(OID playerOid, OID itemOid, boolean isWeapon) {
        AgisItem item = getAgisItem(itemOid);
        if (item == null) {
            Log.error("DURABILITY: got null item: " + itemOid);
            return;
        }
        // Reduce the durability of the item
        if (item.getProperty("durability") != null) {
            int durability = (Integer)item.getProperty("durability");
            durability--;
            if (durability == 0) {
                if (DURABILITY_DESTROY_BROKEN_ITEMS) {
                // Destroy item
                unequipItem(item, playerOid, false,true);
                /*OID rootBagOid = playerOid;
                if (rootBagOid == null) {
                    log.debug("removeItem: cant find rootBagOid");
                    return;
                }
                Boolean result = removeItemFromBag(rootBagOid, item.getOid());
                AgisInventoryClient.itemEquipStatusChanged(playerOid, null, item, "");*/
            } else {
                item.setProperty("durability", 0);
                HashMap<Integer, Integer> list = new HashMap<Integer, Integer>();
                list = getEquipSetsInfo(playerOid);
                AgisInventoryClient.itemEquipStatusChanged(playerOid, null, item, "", list);

                item.setProperty("unequipped", true); //Used to check if the item has already been unequipped
                    if(Log.loggingDebug) Log.debug("DURABILITY: item " + item.toString() + " broke");
            }
                ExtendedCombatMessages.sendErrorMessage(playerOid, "Your " + item.getName() + " broke.");
                ExtendedCombatMessages.sendItemBroken(playerOid, item.getTemplateID());
            } else if (durability > 0) {
                item.setProperty("durability", durability);
                if (durability == 5) {
                    ExtendedCombatMessages.sendErrorMessage(playerOid, "Your " + item.getName() + "'s durability is getting low. It will break when it reaches 0.");
                }
            }
            Engine.getPersistenceManager().setDirty(item);
            sendInvUpdate(playerOid);
            sendEquippedInvUpdate(playerOid);
        }
    }
    
    void reduceDurabilityOfItem(OID playerOid, OID itemOid, boolean isWeapon, String subType) {
        AgisItem item = getAgisItem(itemOid);
        if (item == null) {
            Log.error("DURABILITY: got null item: " + itemOid);
            return;
        }
        String itemSubType = (String) item.getProperty("subType");
        if(!itemSubType.equals(subType) && subType != "") {
            return;
        }
        // Reduce the durability of the item
        if (item.getProperty("durability") != null) {
            int durability = (Integer)item.getProperty("durability");
            durability--;
            if (durability == 0) {
                if (DURABILITY_DESTROY_BROKEN_ITEMS) {
                // Destroy item
                unequipItem(item, playerOid, false,true);
                /*OID rootBagOid = playerOid;
                if (rootBagOid == null) {
                    log.debug("removeItem: cant find rootBagOid");
                    return;
                }
                Boolean result = removeItemFromBag(rootBagOid, item.getOid());
                AgisInventoryClient.itemEquipStatusChanged(playerOid, null, item, "");*/

            } else {
                item.setProperty("durability", 0);
                HashMap<Integer, Integer> list = new HashMap<Integer, Integer>();
                list = getEquipSetsInfo(playerOid);
                AgisInventoryClient.itemEquipStatusChanged(playerOid, null, item, "", list);
                    if(Log.loggingDebug) Log.debug("DURABILITY: item " + item.toString() + " broke");
            }
                ExtendedCombatMessages.sendErrorMessage(playerOid, "Your " + item.getName() + " broke.");
                ExtendedCombatMessages.sendItemBroken(playerOid, item.getTemplateID());
            } else if (durability > 0) {
                item.setProperty("durability", durability);
                if (durability == 5) {
                    ExtendedCombatMessages.sendErrorMessage(playerOid, "Your " + item.getName() + "'s durability is getting low. It will break when it reaches 0.");
                }
            }
            Engine.getPersistenceManager().setDirty(item);
            sendInvUpdate(playerOid);
            sendEquippedInvUpdate(playerOid);
        }
    }
    
    class ActivateChestHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage acMsg = (ExtensionMessage)msg;
            OID playerOid = (OID)acMsg.getSubject();
            OID bagOid = (OID)acMsg.getProperty("BagOid");
            
            InventoryInfo iInfo = getInventoryInfo(playerOid);
            iInfo.setActiveStorage(bagOid.toString());
            
            return true;
        }
    }
    
    class RepairItemsHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage purMsg = (ExtensionMessage)msg;
            OID playerOid = purMsg.getSubject();
            
            boolean repairAll = (Boolean) purMsg.getProperty("repairAll");
            if(Log.loggingDebug) log.debug("RepairItemsHook: playerOid="+playerOid+" repairAll="+repairAll+" numItems="+purMsg.getProperty("numItems"));
            if (repairAll) {
                ArrayList<AgisItem> items = new ArrayList<AgisItem>();
                InventoryInfo iInfo = getInventoryInfo(playerOid);
                if(iInfo==null){
                    log.error("RepairItemsHook: cant get InventoryInfo oi ="+playerOid);
                    return true;
                }
                OID[] subBags = iInfo.getBags();
                for (int pos = 0; pos < subBags.length; pos++) {
                    OID subBag = subBags[pos];
                    Bag bag = getBag(subBag);
                    for (OID itemOid : bag.getItemsList()) {
                        if (itemOid != null) {
                            AgisItem item = getAgisItem(itemOid);
                            if (item.getProperty("maxDurability") != null) {
		        				if(item.getBooleanProperty("repairable"))
                                    items.add(item);
                            }
                        }
                    }
                }
                
                EquipMap equipMap = getEquipMap(playerOid);
                for(AgisEquipSlot slot : equipMap.map.keySet()) {
                    OID itemOid = equipMap.get(slot);
                    if (itemOid != null) {
                        AgisItem item = getAgisItem(itemOid);
                        if (item.getProperty("maxDurability") != null) {
	    	        		if(item.getBooleanProperty("repairable"))
                                items.add(item);
                        }
                    }
                }
                
                boolean repairSuccessful = repairItems(playerOid, items);
                if (repairSuccessful) {
                    EventMessageHelper.SendGeneralEvent(playerOid, EventMessageHelper.REPAIR_SUCCESSFUL, 0, "");
                }
            } else {
                ArrayList<AgisItem> items = new ArrayList<AgisItem>();
                int numItems = (Integer)purMsg.getProperty("numItems");
                for (int i = 0; i < numItems; i++) {
                    OID itemOid = (OID) purMsg.getProperty("itemOid" + i);
                    AgisItem item = getAgisItem(itemOid);
                    if (item.getProperty("durability") != null) {
	        			if(item.getBooleanProperty("repairable"))
                            items.add(item);
                    }
                }
                boolean repairSuccessful = repairItems(playerOid, items);
                if (repairSuccessful) {
                    EventMessageHelper.SendGeneralEvent(playerOid, EventMessageHelper.REPAIR_SUCCESSFUL, 0, "");
                }
            }
            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("ext_msg_subtype", "repair_successful");
            TargetedExtensionMessage msg1 = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
            Engine.getAgent().sendBroadcast(msg1);
            
            sendInvUpdate(playerOid);
            sendEquippedInvUpdate(playerOid);
            
            return true;
        }
    }
    
    boolean repairItems(OID playerOid, ArrayList<AgisItem> items) {
        if (items.size() == 0) {
            EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.NO_ITEM_DURABILITY, 0, "");
            return false;
        }
        
        //TODO: work out some repair cost - Currently assuming all use the same currency
        int purchaseCurrency = (Integer) items.get(0).getProperty("purchaseCurrency");
        long totalCost = 0;
        if(Log.loggingDebug)log.debug("repairItems playerOid="+playerOid+" items="+items+" purchaseCurrency="+purchaseCurrency);
        for (AgisItem item : items) {
            int durability = (Integer)item.getProperty("durability");
            int maxDurability = (Integer)item.getProperty("maxDurability");
        
            float damagePercent = (float)durability / (float)maxDurability;
        
            if (USE_FLAT_REPAIR_RATE) {
                int grade = (Integer)item.getProperty("itemGrade");
                long cost = FLAT_REPAIR_RATE + (grade * FLAT_REPAIR_RATE_GRADE_MODIFIER);
                //long cost = (long) item.getProperty("purchaseCost");
                cost = Math.round(Math.ceil((float)cost * (1.0f - damagePercent)));
                totalCost += cost;
            } else {
                long cost = (Long) item.getProperty("purchaseCost");
                cost = Math.round(Math.ceil((float)cost * REPAIR_RATE * (1.0f - damagePercent)));
                totalCost += cost;
            }
        }
        
        long currencyAmount = getMobCurrency(playerOid, purchaseCurrency);
        if(Log.loggingDebug) Log.debug("Currency: repair cost: " + totalCost + " against players currency: " + currencyAmount);
        if (currencyAmount < totalCost) {
            EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.NOT_ENOUGH_CURRENCY, 0, "");
            return false;
        }
        alterMobCurrency(playerOid, purchaseCurrency, -totalCost);
        alterCurrecyLog(playerOid,null, purchaseCurrency, -totalCost,"repairItems");
        
        // Set durability back to maximum
        for (AgisItem item : items) {
            item.setProperty("durability", (Integer)item.getProperty("maxDurability"));
            item.setProperty("unequipped", false); //Revert unequipped state
        
            // If item is equipped, re-apply the equipped status
            EquipMap equipMap = getEquipMap(playerOid);
            for(AgisEquipSlot slot : equipMap.map.keySet()) {
                OID equippedItem = equipMap.get(slot);
                if (item.getOid().equals(equippedItem)) {
                	HashMap<Integer, Integer> list = new HashMap<Integer, Integer>();
                    list = getEquipSetsInfo(playerOid);
                    AgisInventoryClient.itemEquipStatusChanged(playerOid, item, null, "", list);
                }
            }
        
            Engine.getPersistenceManager().setDirty(item);
        }
        
        return true;
    }
    
    /**
     * Handles the AlterCurrencyMessage. Alters a players amount of the currency specified.
     * @author Andrew Harrison
     *
     */
    class AlterCurrencyHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.alterCurrencyMessage alterMsg = (AgisInventoryClient.alterCurrencyMessage) msg;
            OID oid = alterMsg.getSubject();
            int currencyType = alterMsg.getCurrencyType();
            long delta = alterMsg.getDelta();
            if (Log.loggingDebug)
                log.debug("AlterCurrencyHook: playerOid:" + oid + ";  currencyType:" + currencyType + "; delta:" + delta);
            alterMobCurrency(oid, currencyType, delta);
            alterCurrecyLog(oid,null, currencyType, delta,"alterCurrencyMessage");
            if (Log.loggingDebug)
                log.debug("AlterCurrencyHook: playerOid:" + oid + "; END");
            return true;
        }
    }
    
    /**
    * Handles the AlterCurrencyMessage. Alters a players amount of the currency specified.
    *
    */
    class AlterCurrencyOfflineHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.alterCurrencyOfflineMessage alterMsg = (AgisInventoryClient.alterCurrencyOfflineMessage) msg;
            OID oid = alterMsg.getPlayerOid();
            int currencyType = alterMsg.getCurrencyType();
            long delta = alterMsg.getDelta();
            if (Log.loggingDebug)
                log.debug("AlterCurrencyOfflineHook: playerOid:" + oid + ";  currencyType:" + currencyType + "; delta:" + delta);
            Entity entity = null;
            try {
                entity = Engine.getDatabase().loadEntity(oid, Namespace.BAG);
            } catch (AORuntimeException e) {
                return true;
            }

            InventoryInfo iInfo = (InventoryInfo) entity;
            Currency c = Agis.CurrencyManager.get(currencyType);
            if(Log.loggingDebug) log.debug("AlterCurrencyOfflineHook CURRENCY: getting currency: " + currencyType+" delta: "+delta);
            if (c.getExternal()) {
                //OID accountID = (OID)EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, WorldManagerClient.ACCOUNT_PROPERTY);
                OID accountID = Engine.getDatabase().getAccountOid(oid);
                authDB.alterAccountCoinAmount(accountID, delta);
            } else {
                iInfo.alterCurrencyAmount(currencyType, delta);
                Engine.getDatabase().saveObject(null, (Entity)iInfo, iInfo.getNamespace());
            }
            
            if (Log.loggingDebug)
                log.debug("AlterCurrencyOfflineHook: playerOid:" + oid + "; END");
            return true;
        }
    }

    /**
     * handles requests to start a trading session
     *
     */
    class TradeStartReqHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            Log.debug("TRADE: Start Req Hook "+msg);
            ExtensionMessage startMsg = (ExtensionMessage)msg;
            OID trader1Oid = (OID)startMsg.getProperty("requesterOid");
            OID trader2Oid = (OID)startMsg.getProperty("partnerOid");
            
          //  AccountDatabase aDB = new AccountDatabase(false);
            boolean isOnBlockList = aDB.isOnBlackList(trader1Oid, trader2Oid);
            if (isOnBlockList) {
                String targetName = aDB.getCharacterNameByOid(trader2Oid);
                //String targetName = WorldManagerClient.getObjectInfo(trader2Oid).name;
                EventMessageHelper.SendErrorEvent(trader1Oid, EventMessageHelper.ERROR_PLAYER_ON_BLOCK_LIST, 0, targetName);
                //   aDB.close();
                   return false;
            }else {
                isOnBlockList = aDB.isOnBlackList(trader2Oid, trader1Oid);
                if (isOnBlockList) {
                    String targetName = aDB.getCharacterNameByOid(trader2Oid);
                    //String targetName = WorldManagerClient.getObjectInfo(trader2Oid).name;
                    EventMessageHelper.SendErrorEvent(trader1Oid, EventMessageHelper.ERROR_PLAYER_ON_YOUR_BLOCK_LIST, 0, targetName);
                    // aDB.close();
                       return false;
                }
            }
                
          //  aDB.close();
            if (!CombatPlugin.isPlayerAlive(trader1Oid)) {
                return true;
            } else if (!CombatPlugin.isPlayerAlive(trader2Oid)) {
                return true;
            }

            Log.debug("TradeStartReqHook: trader1=" + trader1Oid + " trader2=" + trader2Oid);
            if (trader1Oid.equals(trader2Oid)) {
                return true;
            }
            if (tradeSessionMap.containsKey(trader1Oid) || tradeSessionMap.containsKey(trader2Oid)) {
                sendTradeComplete(trader1Oid, trader2Oid, AgisInventoryClient.tradeBusy);
                return true;
            }
            TradeSession tradeSession = new TradeSession(trader1Oid, trader2Oid);
            tradeSessionMap.put(trader1Oid, tradeSession);
            tradeSessionMap.put(trader2Oid, tradeSession);
         
            TargetedExtensionMessage msgTarget = new TargetedExtensionMessage(trader2Oid);
            msgTarget.setExtensionType(AgisInventoryClient.MSG_TYPE_TRADE_START_REQ_PARTNER);
            msgTarget.setProperty("requesterOid", trader1Oid);
            msgTarget.setProperty("tradeInviteTimeout", AgisInventoryPlugin.TRADE_INVITE_TIMEOUT);
            Engine.getAgent().sendBroadcast(msgTarget);
            
            CancelTradeInviteTimer timer = new CancelTradeInviteTimer( trader1Oid,  trader2Oid/*, this*/);
            ScheduledFuture<?> sf = Engine.getExecutor().schedule(timer, (long) AgisInventoryPlugin.TRADE_INVITE_TIMEOUT * 1000, TimeUnit.MILLISECONDS);
            tasks.put(trader1Oid, sf);
            if(Log.loggingDebug)Log.debug("TRADE: Start Req Hook send mesage "+msgTarget);
               return true;
        }
    }

    public class GroundItemTimer implements Runnable {


        public GroundItemTimer() {
            Log.debug("GroundItemTimer: Create");
        }

        @Override
        public void run() {
            Log.debug("GroundItemTimer: run");
            // Check user still has items
            HashMap<OID,ArrayList<OID>> playerToUpdate = new HashMap<>();
            try {
            Iterator<ConcurrentHashMap.Entry<OID,ConcurrentHashMap<OID,ConcurrentHashMap<OID,ItemOnGroundData>>>> iterator = itemsOnGround.entrySet().iterator();
            while (iterator.hasNext()) {
                ConcurrentHashMap.Entry<OID,ConcurrentHashMap<OID,ConcurrentHashMap<OID,ItemOnGroundData>>> entry = iterator.next();
                OID instanceOid = entry.getKey();
                Iterator<ConcurrentHashMap.Entry<OID,ConcurrentHashMap<OID,ItemOnGroundData>>> iteratorPly = entry.getValue().entrySet().iterator();
                while (iteratorPly.hasNext()) {
                    ConcurrentHashMap.Entry<OID,ConcurrentHashMap<OID,ItemOnGroundData>> entryPlayer = iteratorPly.next();
                    OID playerOID = entryPlayer.getKey();
                    Iterator<ConcurrentHashMap.Entry<OID,ItemOnGroundData>> iteratorItems = entryPlayer.getValue().entrySet().iterator();
                    while (iteratorItems.hasNext()) {
                        ConcurrentHashMap.Entry<OID,ItemOnGroundData> entryItem = iteratorItems.next();
                        ItemOnGroundData item = entryItem.getValue();
                        if(System.currentTimeMillis() - item.getCreateTime() > INVENTORY_LOOT_ON_GROUND_TIMEOUT * 1000){
                            Template tmpl = ObjectManagerPlugin.getTemplate(item.getTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
                            Integer itemGrade = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "itemGrade");
                            String[] grades = INVENTORY_LOOT_ON_GROUND_LOGOUT_QUALITY_MAIL.split(";");
                            boolean found = false;
                            for (String grade : grades) {
                                if (itemGrade == Integer.parseInt(grade)) {
                                    found = true;
                                }
                            }
                            if (!found) {
                                entryPlayer.getValue().remove(item.getOid());
                                playerToUpdate.computeIfAbsent(instanceOid, __ -> new ArrayList<OID>()).add(playerOID);
                            }
                        }
                    }
                }
                }
            }catch (Exception e){
                Log.exception("GroundItemTimer ",e);
            }
            try {
                playerToUpdate.forEach((k, v) -> {
                    for (OID ply : v) {
                        sendLootGroundUpdate(ply, k);
                    }
                });
            }catch (Exception e){
                Log.exception("GroundItemTimer ",e);
            }
            Log.debug("GroundItemTimer: END");

        }
    }
    
    public class CancelTradeInviteTimer implements Runnable {
        
     protected OID inviterOid;
     protected OID inviteeOid;
        //protected AgisInventoryPlugin group;
        
        public CancelTradeInviteTimer(OID inviterOid, OID inviteeOid/*, AgisInventoryPlugin group*/) {
            this.inviterOid = inviterOid;
            this.inviteeOid = inviteeOid;
            //this.group = group;
            Log.debug("CancelTradeInviteTimer: Create");
            }
        
        @Override
        public void run() {
            // Check user still has items
            if(Log.loggingDebug) Log.debug("Trade: running remove invite task for " + inviterOid);
            tasks.remove(inviterOid);
        
            tradeSessionMap.remove(inviterOid);
            tradeSessionMap.remove(inviteeOid);
            sendTradeComplete(inviterOid, inviteeOid, AgisInventoryClient.tradeCancelled);
            sendTradeComplete(inviteeOid, inviterOid, AgisInventoryClient.tradeCancelled);
            Log.debug("CancelTradeInviteTimer: END");
                      
        }
       }
    
    /**
     * handles requests to start a trading session
     *
     */
    class TradeStartReqResponseHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            if (Log.loggingDebug)
                Log.debug("TRADE: Start Req respons Hook " + msg);
            ExtensionMessage startMsg = (ExtensionMessage) msg;
            OID trader1Oid = (OID) startMsg.getProperty("requesterOid");
            OID trader2Oid = (OID) startMsg.getProperty("partnerOid");

            if (!CombatPlugin.isPlayerAlive(trader1Oid)) {
                return true;
            } else if (!CombatPlugin.isPlayerAlive(trader2Oid)) {
                return true;
            }
            if (Log.loggingDebug)
                Log.debug("TradeStartReqHook: trader1=" + trader1Oid + " trader2=" + trader2Oid);
            if (trader1Oid.equals(trader2Oid)) {
                return true;
            }
            if (tasks.containsKey(trader1Oid)) {
                boolean status = tasks.get(trader1Oid).cancel(true);
                if (Log.loggingDebug)
                    Log.debug("TradeStartReqHook: trader1=" + trader1Oid + " trader2=" + trader2Oid + " Cancel Invite Timer status:" + status);
            }
            // tasks.get(trader1Oid).isCancelled()
            if (Log.loggingDebug)
                Log.debug("TradeStartReqHook: trader1=" + trader1Oid + " trader2=" + trader2Oid + " Cancel Invite Timer status:" + tasks.get(trader1Oid).isCancelled());
            tasks.remove(trader1Oid);
            sendTradeStart(trader1Oid, trader2Oid);
            sendTradeStart(trader2Oid, trader1Oid);
            Log.debug("TradeStartReqHook: END");
            return true;
        }
    }

    /**
     * send an ao.TRADE_COMPLETE message to trader1, telling it that a trade with trader2 has completed
     */
    protected static void sendTradeComplete(OID trader1, OID trader2, byte status) {
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "ao.TRADE_COMPLETE");
        props.put("status", status);
        TargetedExtensionMessage msg = new TargetedExtensionMessage(AgisInventoryClient.MSG_TYPE_TRADE_COMPLETE, trader1, trader2, false, props);
        Engine.getAgent().sendBroadcast(msg);
    }

    /**
     * sends an ao.TRADE_START message to trader1 telling it that a trade has
     * started with trader2
     */
    protected static void sendTradeStart(OID trader1, OID trader2) {
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "ao.TRADE_START");
        TargetedExtensionMessage msg = new TargetedExtensionMessage(AgisInventoryClient.MSG_TYPE_TRADE_START, trader1, trader2, false, props);
        Engine.getAgent().sendBroadcast(msg);
    }

    /**
     * handles requests to update an existing trading session
     *
     */
    class TradeOfferReqHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage tradeMsg = (ExtensionMessage)msg;
            OID trader1 = (OID)tradeMsg.getProperty("requesterOid");
            OID trader2 = (OID)tradeMsg.getProperty("partnerOid");

            if(Log.loggingDebug)Log.debug("TradeOfferReqHook: trader1=" + trader1 + " trader2=" + trader2);
            TradeSession tradeSession = tradeSessionMap.get(trader1);

            // fail if trade session doesn't exist or is invalid
            if ((tradeSession == null) || !tradeSession.isTrader(trader2)) {
                Log.debug("TradeOfferReqHook: trader1=" + trader1 + " trader2=" + trader2+" trade failed no session");
                    sendTradeComplete(trader1, trader2, AgisInventoryClient.tradeFailed);
                if (tradeSession != null) {
                    tradeSessionMap.remove(trader1);
                    OID partner = tradeSession.getPartnerOid(trader1);
                    tradeSessionMap.remove(partner);
                    sendTradeComplete(partner, trader1, AgisInventoryClient.tradeFailed);
                }
                return true;
            }

            List<OID> offer = (List<OID>)tradeMsg.getProperty("offerItems");
            HashMap<String, Integer> currencyOffer = (HashMap<String, Integer>)tradeMsg.getProperty("offerCurrencies");
            Log.debug("TradeOfferReqHook: got offerCurrencies");
            // if offer is cancelled or invalid, fail
            boolean cancelled = (Boolean)tradeMsg.getProperty("cancelled");
            if(Log.loggingDebug)Log.debug("TradeOfferReqHook: trader1=" + trader1 + " trader2=" + trader2+" cancelled:"+cancelled);
            
            if (cancelled || !validateTradeOffer(trader1, offer)) {
                byte status = AgisInventoryClient.tradeFailed;
                if (cancelled) {
                    status = AgisInventoryClient.tradeCancelled;
                }
                if(Log.loggingDebug)Log.debug("TradeOfferReqHook: trader1=" + trader1 + " trader2=" + trader2+" trade failed status:"+status);
                tradeSessionMap.remove(trader1);
                tradeSessionMap.remove(trader2);
                sendTradeComplete(trader1, trader2, status);
                sendTradeComplete(trader2, trader1, status);
                return true;
            }

            // update session with this offer
            boolean accepted = (Boolean)tradeMsg.getProperty("accepted");
            if(Log.loggingDebug) Log.debug("TradeOfferReqHook: trader1=" + trader1 + " trader2=" + trader2+" accepted:"+accepted);
                 if (accepted) {
                if (!validateTradeCurrencyOffer(trader1, currencyOffer)) {
                    accepted = false;
                    // Send down event to let the client know why it cancelled
                    if(Log.loggingDebug)Log.debug("TradeOfferReqHook: trader1=" + trader1 + " trader2=" + trader2+" INVALID_TRADE_CURRENCY");
                                EventMessageHelper.SendGeneralEvent(trader1, EventMessageHelper.INVALID_TRADE_CURRENCY, 0, "");
                }
                
                HashMap<Integer, Integer> items = new HashMap<Integer, Integer> ();
                for (OID itemOid : tradeSession.getOffer(trader2)) {
                     AgisItem item = getAgisItem(itemOid);
                      if (Log.loggingTrace) log.trace("Chacking Item offer "+itemOid+" "+item);
                     if (item != null)
                     {
                         if(items.containsKey(item.getTemplateID()))
                             items.put(item.getTemplateID(), items.get(item.getTemplateID())+item.getStackSize());
                         else    
                             items.put(item.getTemplateID(), item.getStackSize());
                         }
                }
                boolean invHasSpace = hasSpace(trader1, items,0);
                  if (Log.loggingTrace) log.trace("Chacking offer space "+invHasSpace+" items "+items+" "+items.size());
                if(!invHasSpace) {
                    accepted = false;
                    if(Log.loggingDebug)  Log.debug("TradeOfferReqHook: trader1=" + trader1 + " trader2=" + trader2+" INVENTORY_FULL");
                        // Send down event to let the client know why it cancelled
                    EventMessageHelper.SendGeneralEvent(trader1, EventMessageHelper.INVENTORY_FULL, 0, "");
                    EventMessageHelper.SendErrorEvent(trader1, EventMessageHelper.INVENTORY_FULL, 0, "");
                        }
            }
            tradeSession.updateOffer(trader1, offer, currencyOffer, accepted);

            // if session is complete, then complete the trade
            if (tradeSession.isComplete()) {
                // Check each trade offer again to make sure the users have what they offered
                if (!validateTradeOffer(trader1, tradeSession.getOffer(trader1)) || !validateTradeOffer(trader2, tradeSession.getOffer(trader2))
                        || !validateTradeCurrencyOffer(trader1, tradeSession.getCurrencyOffer(trader1)) 
                        || !validateTradeCurrencyOffer(trader2, tradeSession.getCurrencyOffer(trader2))) {
                    byte status = AgisInventoryClient.tradeFailed;
                    tradeSessionMap.remove(trader1);
                    tradeSessionMap.remove(trader2);
                    sendTradeComplete(trader1, trader2, status);
                    sendTradeComplete(trader2, trader1, status);
                    return true;
                }

                if(Log.loggingDebug) Log.debug("TradeOfferReqHook: trader1=" + trader1 + " trader2=" + trader2+" tradeSuccess");
                tradeSessionMap.remove(trader1);
                tradeSessionMap.remove(trader2);
                sendTradeComplete(trader1, trader2, AgisInventoryClient.tradeSuccess);
                sendTradeComplete(trader2, trader1, AgisInventoryClient.tradeSuccess);
                completeTrade(tradeSession);
                return true;
            }

            // otherwise, send trade updates to both traders
            sendTradeOfferUpdate(trader1, trader2, tradeSession);
            sendTradeOfferUpdate(trader2, trader1, tradeSession);
            return true;
        }
    }

    /**
     * Ensures the items offered in the Trade are valid. Checks there
     * are no doubles, and that the Trader does have the items.
     * @param trader
     * @param offer
     * @return
     */
    public boolean validateTradeOffer(OID trader, List<OID> offer) {
        Set<OID> itemSet = new HashSet<OID>();

        for (OID itemOid : offer) {
            // null is an empty slot in the offer
            if (itemOid == null) {
                continue;
            }
            // don't allow duplicate items in trade offer
            if (!itemSet.add(itemOid)) {
                return false;
            }
            
            // Verify the item is not bound to the player
            AgisItem item = getAgisItem(itemOid);
            if (item == null) {
                return false;
            }
            if (!item.canBeTraded()) {
                ChatClient.sendObjChatMsg(trader, 2, "You cannot trade a bound item.");
                return false;
            }
        }

        InventoryInfo iInfo = getInventoryInfo(trader);
        if (iInfo == null)
            return false;
        OID[] subBags = iInfo.getBags();

        // go through all items trader has in inventory and remove from itemSet.
        // anything left, the trader doesn't have.
        for (OID subBagOid : subBags) {
            if (subBagOid != null) {
            Bag subBag = getBag(subBagOid);
            for (OID itemOid : subBag.getItemsList()) {
                itemSet.remove(itemOid);
            }
            }
        }

        // if there are any items we didn't find, fail
        if (!itemSet.isEmpty()) {
            return false;
        }

        return true;
    }
    
    /**
     * Ensures the items offered in the Trade are valid. Checks there
     * are no doubles, and that the Trader does have the items.
     * @param trader
     * @param currencyOffer
     * @return
     */
    public boolean validateTradeCurrencyOffer(OID trader, HashMap<String, Integer> currencyOffer) {
        InventoryInfo iInfo = getInventoryInfo(trader);
        if (iInfo == null)
            return false;

        // go through all currencies offered and ensure the player has enough
        /*for (String currencyID : currencyOffer.keySet()) {
            if (currencyID != null && !currencyID.isEmpty()) {
                int currencyAmount = iInfo.getCurrencyAmount(Integer.parseInt(currencyID), true);
                if (currencyOffer.get(currencyID) > currencyAmount) {
                    currencyOffer.put(currencyID, currencyAmount);
                }
            }
        }*/
        
        int currencyType = -1;
        int currencyAmount = 0;
        for (String currency : currencyOffer.keySet()) {
            int cType = Integer.parseInt(currency);
            int amount = currencyOffer.get(currency);
            Currency c = Agis.CurrencyManager.get(cType);
            while (c.getCurrencyThatConvertsToThis() != null) {
                c = c.getCurrencyThatConvertsToThis();
                amount *= c.getConversionAmountReq();
            }
            currencyAmount += amount;
            currencyType = c.getCurrencyID();
        }
        if(Log.loggingDebug)Log.debug("TRADE: checking trade currencies. Player is trading currency: " + currencyType + " x: " + currencyAmount);
        if (currencyType > 0 && currencyAmount > 0) {
            // Does player have enough?
            long playersCurrency = getMobCurrency(trader, currencyType);
            if(Log.loggingDebug)Log.debug("TRADE: checking trade currencies. Player is trading: " + currencyAmount + " and has: " + playersCurrency);
            if (playersCurrency < currencyAmount) {
                return false;
            }
        }
        

        return true;
    }

    /**
     * Sends down the updated list of offers to players involved in the Trade.
     * @param trader1
     * @param trader2
     * @param tradeSession
     */
    public static void sendTradeOfferUpdate(OID trader1, OID trader2, TradeSession tradeSession) {
        Boolean accepted1 = tradeSession.getAccepted(trader1);
        Boolean accepted2 = tradeSession.getAccepted(trader2);
        ArrayList<ArrayList<Object>> offer1 = sendTradeOfferUpdateHelper(trader1, tradeSession);
        ArrayList<ArrayList<Object>> offer2 = sendTradeOfferUpdateHelper(trader2, tradeSession);

        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "ao.TRADE_OFFER_UPDATE");
        props.put("accepted1", accepted1);
        props.put("accepted2", accepted2);
        props.put("offer1", offer1);
        props.put("offer2", offer2);
        props.put("currencyOffer1", tradeSession.getCurrencyOffer(trader1));
        props.put("currencyOffer2", tradeSession.getCurrencyOffer(trader2));
        TargetedExtensionMessage msg = new TargetedExtensionMessage(AgisInventoryClient.MSG_TYPE_TRADE_OFFER_UPDATE,
                                                                    trader1, trader2, false, props);
        Engine.getAgent().sendBroadcast(msg);
    }

    /**
     * Helper function for the sendTradeOfferUpdate function. 
     * @param traderOid
     * @param tradeSession
     * @return
     */
    protected static ArrayList<ArrayList<Object>> sendTradeOfferUpdateHelper(OID traderOid, TradeSession tradeSession) {
        ArrayList<ArrayList<Object>> offer = new ArrayList<ArrayList<Object>>();
        for (OID itemOid : tradeSession.getOffer(traderOid)) {
            ArrayList<Object> info = new ArrayList<Object>();
            if ((itemOid == null)) {
                info.add(itemOid);
                info.add(-1);
                info.add(1);
                //info.add("");
                //info.add("");
            } else {
                AgisItem item = getAgisItem(itemOid);
                info.add(itemOid);
                info.add(item.getTemplateID());
                info.add(item.getStackSize());
                //info.add(item.getName());
                //info.add(item.getIcon());
            }
            offer.add(info);
        }
        return offer;
    }

    /**
     * Completes the Trade moving the items offered between the two players.
     * @param tradeSession
     */
    public void completeTrade(TradeSession tradeSession) {
        OID trader1Oid = tradeSession.getTrader1();
        OID trader2Oid = tradeSession.getTrader2();
        InventoryInfo iInfo1 = getInventoryInfo(trader1Oid);
        InventoryInfo iInfo2 = getInventoryInfo(trader2Oid);
        List<OID> offer1 = tradeSession.getOffer(trader1Oid);
        List<OID> offer2 = tradeSession.getOffer(trader2Oid);
    
        for (OID itemOid : offer1) {
            removeItem(trader1Oid, itemOid, true);
        }
        for (OID itemOid : offer2) {
            removeItem(trader2Oid, itemOid, true);
        }
        for (OID itemOid : offer1) {
            addItem(trader2Oid, iInfo2.getOid(), itemOid);
        }
        for (OID itemOid : offer2) {
            addItem(trader1Oid, iInfo1.getOid(), itemOid);
        }
        sendInvUpdate(trader1Oid);
        sendInvUpdate(trader2Oid);
        
        // Move currencies
        HashMap<String, Integer> currencyOffer1 = tradeSession.getCurrencyOffer(trader1Oid);
        HashMap<String, Integer> currencyOffer2 = tradeSession.getCurrencyOffer(trader2Oid);
        
        // Transfer currency from trader 1 to trader 2
        int currencyType = -1;
        int currencyAmount = 0;
        for (String currency : currencyOffer1.keySet()) {
            int cType = Integer.parseInt(currency);
            int amount = currencyOffer1.get(currency);
            Currency c = Agis.CurrencyManager.get(cType);
            while (c.getCurrencyThatConvertsToThis() != null) {
                c = c.getCurrencyThatConvertsToThis();
                amount *= c.getConversionAmountReq();
            }
            currencyAmount += amount;
            currencyType = c.getCurrencyID();
        }
        if (currencyType > 0 && currencyAmount > 0) {
            alterMobCurrency(trader1Oid, currencyType, -currencyAmount);
            alterMobCurrency(trader2Oid, currencyType, currencyAmount);
            alterCurrecyLog(trader1Oid,null, currencyType, -currencyAmount,"completeTrade");
            alterCurrecyLog(trader2Oid,null, currencyType, currencyAmount,"completeTrade");

        }
        
        // Transfer currency from trader 2 to trader 1
        currencyType = -1;
        currencyAmount = 0;
        for (String currency : currencyOffer2.keySet()) {
            int cType = Integer.parseInt(currency);
            int amount = currencyOffer2.get(currency);
            Currency c = Agis.CurrencyManager.get(cType);
            while (c.getCurrencyThatConvertsToThis() != null) {
                c = c.getCurrencyThatConvertsToThis();
                amount *= c.getConversionAmountReq();
            }
            currencyAmount += amount;
            currencyType = c.getCurrencyID();
        }
        if (currencyType > 0 && currencyAmount > 0) {
            alterMobCurrency(trader2Oid, currencyType, -currencyAmount);
            alterMobCurrency(trader1Oid, currencyType, currencyAmount);
            alterCurrecyLog(trader2Oid,null, currencyType, -currencyAmount,"completeTrade");
            alterCurrecyLog(trader1Oid,null, currencyType, currencyAmount,"completeTrade");
        }
    }

    /**
     * Handles the DespawnedMessage which is sent when a WorldObject is despawned. 
     * Cancels any TradeSessions the player is involved in.
     */
    class DespawnedHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            WorldManagerClient.DespawnedMessage despawnedMsg = (WorldManagerClient.DespawnedMessage) msg;
            OID oid = despawnedMsg.getSubject();
            TradeSession tradeSession = tradeSessionMap.get(oid);
            if (tradeSession != null) {
                OID trader1 = tradeSession.getTrader1();
                OID trader2 = tradeSession.getTrader2();
                tradeSessionMap.remove(trader1);
                tradeSessionMap.remove(trader2);
                sendTradeComplete(trader1, trader2, AgisInventoryClient.tradeFailed);
                sendTradeComplete(trader2, trader1, AgisInventoryClient.tradeFailed);
            }
            OID instanceOid = despawnedMsg.getInstanceOid();
            if (despawnedMsg.getType() == null || !despawnedMsg.getType().isPlayer())
                return true;
            playerInInstance.computeIfAbsent(instanceOid, __ -> new HashSet<OID>()).remove(oid);
            ArrayList<OID> generatedItems = new ArrayList<OID>();
            try {
                ConcurrentHashMap<OID, ItemOnGroundData> data = itemsOnGround.computeIfAbsent(instanceOid, __ -> new ConcurrentHashMap<OID, ConcurrentHashMap<OID, ItemOnGroundData>>()).getOrDefault(oid, new ConcurrentHashMap<OID, ItemOnGroundData>());
                data.forEach((k, v) -> {
                    Template tmpl = ObjectManagerPlugin.getTemplate(v.getTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
                    Integer itemGrade = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "itemGrade");
                    String[] grades = INVENTORY_LOOT_ON_GROUND_LOGOUT_QUALITY_MAIL.split(";");
                    boolean found = false;
                    for (String grade : grades) {
                        if (itemGrade == Integer.parseInt(grade)) {
                            found = true;
                        }
                    }
                    if (found) {
                        if (v.getItemOID() == null) {
                            OID itemOid = generateItem(v.getTemplateId(), null);
                            AgisItem item = getAgisItem(itemOid);
                            item.setStackSize(v.getStack());
                            ObjectManagerClient.saveObject(itemOid);
                            generatedItems.add(itemOid);
                        }
                    }
                });
            } catch (Exception E) {

            }
            if (generatedItems.size() > 0) {
                Log.debug("MAIL: creating mail");

                Mail m = new Mail(-1, false, oid, "", null, "System", "", "", -1, 0, generatedItems, 1, false);
                aDB.addNewMail(m);
            }
            return true;
        }
    }

    // HashMap storing all of the active TradeSessions.
    Map<OID, TradeSession> tradeSessionMap = new HashMap<OID, TradeSession>();

    ConcurrentHashMap<OID, Set<OID>> playerInInstance = new ConcurrentHashMap<OID, Set<OID>>();

    class SpawnedHook implements Hook {
        @Override
        public boolean processMessage(Message msg, int arg1) {
            // TODO Auto-generated method stub
            WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;
            OID objOid = spawnedMsg.getSubject();
            OID instanceOid = spawnedMsg.getInstanceOid();
            if (spawnedMsg.getType() == null || !spawnedMsg.getType().isPlayer())
                return true;
            playerInInstance.computeIfAbsent(instanceOid, __ -> new HashSet<OID>()).add(objOid);
            return true;
        }
    }
    /**
     * Creates an AgisItem from the Template specified.
     */
    protected SubObjData createItemSubObj(OID masterOid, Template template) {
        if (Log.loggingDebug)
            log.debug("createItemSubObj: creating item=" + template.getName()
              + " masterOid=" + masterOid);
        AgisItem item = new AgisItem(masterOid);
        item.setName(template.getName());
        item.setTemplateID(template.getTemplateID());

        Map<String, Serializable> props = template.getSubMap(Namespace.AGISITEM);
        if (props == null) {
            Log.warn("createItemSubObj: no props in ns " + Namespace.AGISITEM);
            return null;
        }

        Boolean persistent = (Boolean)template.get(
            Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT);
        if (persistent == null)
            persistent = false;
        item.setPersistenceFlag(persistent);

        // copy properties from template to object
        for (Map.Entry<String, Serializable> entry : props.entrySet()) {
            String key = entry.getKey();
            Serializable value = entry.getValue();
            if (!key.startsWith(":")) {
                item.setProperty(key, value);
            }
        }
    
        // register the entity
        registerAgisItem(item);
        if (persistent) {
            Engine.getPersistenceManager().persistEntity(item);
        }
        return new SubObjData();
    }

    /**
     * Loads the players inventory from the internal object database then sends
     * down the inventory information to their client.
     */
    protected void loadInventory(Entity e) {
        try {
            InventoryInfo iInfo = (InventoryInfo) e;

            OID ownerOid = iInfo.getOid();
            //Reset equiped tools
            try {
                HashMap<String, Serializable> params1 = new HashMap<String, Serializable>();
                params1.put("weaponType", new ArrayList<String>());
                params1.put("toolType", new ArrayList<String>());
                EnginePlugin.setObjectPropertiesNoResponse(ownerOid, CombatClient.NAMESPACE, params1);
            } catch (Exception e1) {
                
            }
            
			boolean dirty = false;
			boolean added = false;
			OID[] bags = iInfo.getBags();
			for (int i = 0; i < bags.length; i++) {
				OID subBagOid = bags[i];
				log.debug("loadInventory: " + ownerOid + " subBagOid=" + subBagOid);
				if (subBagOid != null) {
					if (loadSubBag(subBagOid, iInfo))
						dirty = true;
				} else {
					if (Log.loggingDebug)
						log.debug("createInvSubObj: creating sub bag, moboid=" + iInfo.getOid() + ", bag pos=" + i);
					Bag subBag = createSubBag(iInfo.getOid(), Bag.BAG_USE_TYPE_STANDARD, INVENTORY_OTHER_BAG_SIZE,"Bag");
					if (subBag == null) {
						return;
					}
					added = true;
					dirty = true;
					bags[i] = subBag.getOid();
				}
			}
			
            if(added)
            	iInfo.setBags(bags);
            HashMap<Integer, Long> currencies = new HashMap<Integer, Long>();
            for (Integer currencyID : iInfo.getCurrencies(iInfo.getCurrentCategory()).keySet()) {
                currencies.put(currencyID, Long.parseLong(String.valueOf(iInfo.getCurrencies(iInfo.getCurrentCategory()).get(currencyID))));
            }
            iInfo.setCurrentCurrencies(currencies);

            if (iInfo.getEquipmentItemBag() != null) {
                if (loadSubBag(iInfo.getEquipmentItemBag(), iInfo))
                    dirty = true;
            }

            for (OID bagOid : iInfo.getStorageBags().values()) {
                if (loadSubBag(bagOid, iInfo))
                    dirty = true;
            }

            if (dirty)
                Engine.getPersistenceManager().setDirty(iInfo);

            // Add any currencies the user doesn't know about yet
            for (int currencyID : Agis.CurrencyManager.keyList()) {
                if (!iInfo.getCurrentCurrencies().containsKey(currencyID)) {
                    iInfo.addCurrency(currencyID, 0l);
                }
            }
            ExtendedCombatMessages.sendCurrencies(ownerOid, iInfo.getCurrencies(iInfo.getCurrentCategory()));
            sendInvUpdate(ownerOid);

            // Send the Equip message again for all equipped items to ensure the latest
            // display and stats are set
            
            
                
            
            OID subBagOid = iInfo.getEquipmentItemBag();
            Bag subBag = getBag(subBagOid);
            if (subBag != null) {
                for (OID itemOid : subBag.getItemsList()) {
                    if (itemOid != null) {
                        AgisItem item = getAgisItem(itemOid);
                        
                        /* Already handled in CombatPlugin on login
                        Integer abilityId = (Integer) item.getProperty("pabilityID");
                        if(abilityId>0) {
                            AgisInventoryClient.itemEquipStatusChanged(ownerOid, null, item, "");
                        }
                        */
                        
                        String displayVal = (String) item.getProperty("displayVal");
                        String unicItem = item.getTemplateID() + "";
                        if ((int) item.getProperty("enchantLevel") > 0)
                            unicItem += ";E" + item.getProperty("enchantLevel");

                        HashMap<String, Integer> bstats = (HashMap) item.getProperty("bonusStats");
                        for (String statName : bstats.keySet()) {
                            unicItem += ";B" + statName + "|" + bstats.get(statName);
                        }

                        if (item.getProperty("enchantStats") != null) {
                            HashMap<String, Integer> estats = (HashMap) item.getProperty("enchantStats");
                            for (String statName : estats.keySet()) {
                                if (bstats.containsKey(statName)) {
                                    if (estats.get(statName) - bstats.get(statName) != 0) {
                                        unicItem += ";T" + statName + "|" + (estats.get(statName) - bstats.get(statName));
                                    }
                                } else {
                                    unicItem += ";T" + statName + "|" + estats.get(statName);
                                }
                            }
                        }
                        
                        HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) item.getProperty("sockets");
                        ArrayList<String> socketItems = new ArrayList<String>();
                        for (Integer sId : itemSockets.keySet()) {
                            if (itemSockets.get(sId).GetItemOid() != null) {
                                AgisItem sitem = getAgisItem(itemSockets.get(sId).GetItemOid());
                                socketItems.add(itemSockets.get(sId).GetType() + "|" + sId + "|" + sitem.getTemplateID());
                            }
                        }
                        // Collections.sort(socketItems);
                        for (String l : socketItems) {
                            unicItem += ";S" + l;
                        }
                        EquipMap equipMap = getEquipMap(ownerOid);
                            
                        AgisEquipSlot slot = equipMap.getSlot(item.getMasterOid());
				        if(slot != null) {
                            EquipHelper.updateDisplay(ownerOid, displayVal, slot,unicItem);
							//Handled in CombatPlugin on login instead
                            /*AgisInventoryClient.itemEquipStatusChanged(ownerOid, item, null, slot.toString());*/
                        }
                    }
                }
			}

            //Load Pet Profile
            AtavismDatabase atDB = new AtavismDatabase();
           ArrayList<PetInfo> pets = atDB.loadPlayerAllPet(iInfo.getOid());
            if (Log.loggingDebug)
                log.debug("loadInventory: Pet Profiles "+pets.size());

           for(PetInfo pi : pets){
               Log.error("loadInventory: "+pi);
               PetInventoryInfo pii = new PetInventoryInfo(iInfo.getOid(), pi.id);
               pii.setEquipmentItemBag(OID.fromLong(pi.equipment));
               iInfo.getPetInventory().put(pi.id, pii);
               pii.activate();
           }
            if (Log.loggingDebug)
                log.debug("loadInventory: Load Pet Equip Items");
            //Load Pet Equip Items
            if(iInfo.getPetInventory().size() > 0) {

                for (PetInventoryInfo pii : iInfo.getPetInventory().values()) {
                    if (Log.loggingDebug)
                        log.debug("loadInventory: PetInventoryInfo "+pii);
                    if (pii.getEquipmentItemBag() != null) {
                        if (loadSubBag(pii.getEquipmentItemBag(), iInfo))
                            dirty = true;
                    }
                    Bag petBag = getBag(pii.getEquipmentItemBag());
                    if (petBag != null) {
                        for (OID itemOid : petBag.getItemsList()) {
                            if (itemOid != null) {
                                AgisItem item = getAgisItem(itemOid);
                            }
                        }
                    }
                    pii.generateEquipMapFromBag();
                }
            }
            if (Log.loggingDebug)
                log.debug("loadInventory: Load Pet Equip Items End");

            LinkedList<String> params = new LinkedList<String>();
            for(AgisEquipSlot slot : AgisEquipInfo.DefaultEquipInfo.getEquippableSlots()) {
                params.add(slot.getName() + "DisplayVAL");
                params.add(slot.getName() + "DisplayID");
            }
            EnginePlugin.setObjectPropertiesNoResponse(ownerOid, WorldManagerClient.NAMESPACE, new HashMap<String, Serializable>(), params);

            if (Log.loggingDebug)
                log.debug("loadInventory loaded for " + ownerOid);
        } catch (NoRecipientsException e1) {
            Log.exception("LoadInventory NoRecipientsException Exception ", e1);
        } catch (NumberFormatException e1) {
            Log.exception("LoadInventory NumberFormatException Exception ", e1);
        }

    }

    /**
     * Loads a Bag that resides inside another Bag from the internal object database.
     * @param subBagOid
     * @param rootBag
     * @return
     */
    protected boolean loadSubBag(OID subBagOid, Entity rootBag) {
        Bag subBag = (Bag) Engine.getDatabase().loadEntity(subBagOid, Namespace.BAG);
        if(subBag==null){
            log.error("loadSubBag: Not Found Bag" + subBagOid);
            return false;
        }
        registerBag(subBag);
        boolean dirty = false;
        for (OID itemOid : subBag.getItemsList()) {
            if (itemOid != null) {
                if (ObjectManagerClient.loadObject(itemOid) == null) {
                    // If we can't load the item, then delete reference
                    // to it from the bag.
                    Log.warn("loadSubBag: item " + itemOid + " does not exist, removing from bag " + subBagOid);
                    boolean rv = subBag.removeItem(itemOid);
                    if (rv)
                        dirty = true;
                } else {
                    if (Log.loggingDebug)
                        log.debug("loadSubBag: loading items in sockets for item " + itemOid);
                    AgisItem item = getAgisItem(itemOid);
                    HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) item.getProperty("sockets");
                    Set<Integer> keys = itemSockets.keySet();
                    for (Integer i : keys) {
                        if(Log.loggingDebug)  Log.debug("loadSubBag: socket item " + itemSockets.get(i).GetItemOid() + " for itemOid:" + itemOid);
                        if (itemSockets.get(i).GetItemOid() != null) {
                            if (ObjectManagerClient.loadObject(itemSockets.get(i).GetItemOid()) == null) {
                                if(Log.loggingDebug)Log.debug("loadSubBag: item " + itemSockets.get(i).GetItemOid() + " does not exist");

                            } else {
                                if(Log.loggingDebug)log.debug("loadSubBag: loading items in sockets for item " + itemSockets.get(i).GetItemOid());
                                AgisItem item2 = getAgisItem(itemSockets.get(i).GetItemOid());
                                HashMap<Integer, SocketInfo> itemSockets2 = (HashMap<Integer, SocketInfo>) item2.getProperty("sockets");
                                Set<Integer> keys2 = itemSockets2.keySet();
                                for (Integer i2 : keys2) {
                                    if(Log.loggingDebug) Log.debug("loadSubBag: socket item " + itemSockets2.get(i2).GetItemOid() + " for item:" + itemSockets.get(i).GetItemOid() + " for itemOid:" + itemOid);
                                    if (itemSockets2.get(i2).GetItemOid() != null) {
                                        if (ObjectManagerClient.loadObject(itemSockets2.get(i2).GetItemOid()) == null) {
                                            if(Log.loggingDebug)  Log.debug("loadSubBag: item " + itemSockets2.get(i2).GetItemOid() + " does not exist");

                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return dirty;
    }
    
    protected void loadMailItems(OID oid) {
        // Load mail items
        InventoryInfo iInfo = getInventoryInfo(oid);
        for (Mail m : iInfo.getMail()) {
            if (m.getItems() != null) {
                for (int i = 0; i < m.getItems().size(); i++) {
                    if (m.getItems().get(i) != null) {
                        ObjectManagerClient.loadObject(m.getItems().get(i));
                    }
                }
            }
        }
    }

    /**
     * Unloads the players inventory object. If the root bag is dirty it is saved
     * before being unloaded.
     */
    protected void unloadInventory(Entity e) {
        InventoryInfo iInfo = (InventoryInfo) e;
        OID ownerOid = iInfo.getOid();

        if (e.isDeleted())
            return;

        // If the root bag is dirty, then save immediately.  We can't
        // wait for the PersistenceManager because the sub bag entities
        // will be unregistered, so the save wouldn't do anything.
        if (Engine.getPersistenceManager().isDirty(e)) {
            Engine.getPersistenceManager().persistEntity(e);
        }

        if(Log.loggingDebug)Log.debug("unloadInventory: oid="+e.getOid()+" owner="+ownerOid);

        for (OID subBagOid : iInfo.getBags()) {
            if (subBagOid != null) {
                Bag subBag = getBag(subBagOid);
                if (subBag == null)
                    continue;
                for (OID itemOid : subBag.getItemsList()) {
                    if (itemOid == null)
                        continue;
                    if (Log.loggingDebug)
                        Log.debug("unloadInventory: bag oid="+e.getOid()+
                                " subbag="+subBagOid+ " item="+itemOid);
                    ObjectManagerClient.unloadObject(itemOid);
                }
                EntityManager.removeEntityByNamespace(subBagOid, Namespace.BAG);
            }
        }
        
        if (iInfo.getEquipmentItemBag() != null) {
            Bag subBag = getBag(iInfo.getEquipmentItemBag());
            if (subBag == null)
                return;
            for (OID itemOid : subBag.getItemsList()) {
                if (itemOid == null)
                    continue;
                if (Log.loggingDebug)
                    Log.debug("unloadInventory: bag oid="+e.getOid()+
                            " subbag="+iInfo.getEquipmentItemBag()+ " item="+itemOid);
                ObjectManagerClient.unloadObject(itemOid);
            }
            EntityManager.removeEntityByNamespace(iInfo.getEquipmentItemBag(), Namespace.BAG);
        }
        
        for (OID bagOid : iInfo.getStorageBags().values()) {
            Bag storageBag = getBag(bagOid);
            if (storageBag == null)
                return;
            for (OID itemOid : storageBag.getItemsList()) {
                if (itemOid == null)
                    continue;
                if (Log.loggingDebug)
                    Log.debug("unloadInventory: bag oid="+e.getOid()+
                            " subbag="+iInfo.getEquipmentItemBag()+ " item="+itemOid);
                ObjectManagerClient.unloadObject(itemOid);
            }
            EntityManager.removeEntityByNamespace(bagOid, Namespace.BAG);
        }

        if(iInfo.getPetInventory().size() > 0) {
            for (PetInventoryInfo pii : iInfo.getPetInventory().values()) {
                Bag petBag = getBag(pii.getEquipmentItemBag());
                if (petBag != null) {
                    for (OID itemOid : petBag.getItemsList()) {
                        if (itemOid != null) {
                            if (Log.loggingDebug)
                                Log.debug("unloadInventory: bag oid="+e.getOid()+" subbag="+pii.getEquipmentItemBag()+ " item="+itemOid);
                            ObjectManagerClient.unloadObject(itemOid);
                        }
                    }
                    EntityManager.removeEntityByNamespace(pii.getEquipmentItemBag(), Namespace.BAG);
                } else {
                    if (Log.loggingDebug)
                        log.debug("unloadInventory: equippedItemsBag not found oid=" + iInfo.getEquipmentItemBag());
                }

            }
        }

    }

    /**
     * Deletes a InventoryInfo object from the internal object database.
     */
    protected void deleteInventory(Entity e) {
        InventoryInfo iInfo = (InventoryInfo) e;
        OID ownerOid = iInfo.getOid();

        if(Log.loggingDebug) Log.debug("deleteInventory: oid="+e.getOid()+" owner="+ownerOid);

        for (OID subBagOid : iInfo.getBags()) {
            if (subBagOid != null) {
                Bag subBag = getBag(subBagOid);
                if (subBag == null)
                    continue;
                for (OID itemOid : subBag.getItemsList()) {
                    if (itemOid == null)
                        continue;
                    if (Log.loggingDebug)
                        Log.debug("deleteInventory: bag oid="+e.getOid()+
                                " subbag="+subBagOid+ " item="+itemOid);
                    ObjectManagerClient.deleteObject(itemOid);
                }
                subBag.setDeleted();
                EntityManager.removeEntityByNamespace(subBagOid, Namespace.BAG);
                Engine.getDatabase().deleteObjectData(subBagOid);
            }
        }
        
        if (iInfo.getEquipmentItemBag() != null) {
            Bag subBag = getBag(iInfo.getEquipmentItemBag());
            if (subBag == null)
                return;
            for (OID itemOid : subBag.getItemsList()) {
                if (itemOid == null)
                    continue;
                if (Log.loggingDebug)
                    Log.debug("deleteInventory: bag oid="+e.getOid()+
                            " subbag="+iInfo.getEquipmentItemBag()+ " item="+itemOid);
                ObjectManagerClient.deleteObject(itemOid);
            }
            subBag.setDeleted();
            EntityManager.removeEntityByNamespace(iInfo.getEquipmentItemBag(), Namespace.BAG);
            Engine.getDatabase().deleteObjectData(iInfo.getEquipmentItemBag());
        }
        
        for (OID bagOid : iInfo.getStorageBags().values()) {
            Bag storageBag = getBag(bagOid);
            if (storageBag == null)
                return;
            for (OID itemOid : storageBag.getItemsList()) {
                if (itemOid == null)
                    continue;
                if (Log.loggingDebug)
                    Log.debug("deleteInventory: bag oid="+e.getOid()+
                            " storageBag="+bagOid+ " item="+itemOid);
                ObjectManagerClient.deleteObject(itemOid);
            }
            storageBag.setDeleted();
            EntityManager.removeEntityByNamespace(bagOid, Namespace.BAG);
            Engine.getDatabase().deleteObjectData(bagOid);
        }
    }


    /**
     * Does nothing.
     */
    protected void loadItem(Entity e) {
    }

    protected void unloadItem(Entity item) {
        if(Log.loggingInfo)Log.info("Removing unloaded item from entity manager: " + item.getOid());
        if(EntityManager.getEntityByNamespace(item.getOid(), Namespace.AGISITEM) != null)
            EntityManager.removeEntityByNamespace(item.getOid(), Namespace.AGISITEM);
    }

    /**
     * Deletes an AgisItem from the internal object database.
     */
    protected void deleteItem(Entity item) {
        if (Log.loggingDebug)
            Log.debug("deleteItem: oid="+item.getOid());

        OID subBagOid = (OID)item.getProperty(INVENTORY_PROP_BACKREF_KEY);
        if (subBagOid != null) {
            if (removeItemFromBagHelper(subBagOid, item)) {
                Bag subBag = getBag(subBagOid);
                OID ownerOid = (OID)subBag.getProperty(INVENTORY_PROP_BACKREF_KEY);
                AgisItem aItem = (AgisItem)item;
                aItem.unacquired(ownerOid);
                InventoryInfo iInfo = getInventoryInfo(ownerOid);
                if (iInfo != null) {
                    Engine.getPersistenceManager().setDirty(iInfo);
                    sendInvUpdate(ownerOid);
                }
            }
        }

        // EnginePlugin DeleteSubObject handler has already removed
        // entity, so we just need to delete from data base.

        EntityManager.removeEntityByNamespace(item, Namespace.AGISITEM);
        Engine.getDatabase().deleteObjectData(item.getOid());
    }

    protected void deleteItem(OID itemId) {
        AgisItem.deleteById(itemId);
    }

    /**
     * Saves an InventoryInfo object into the internal object database.
     */
    protected boolean saveInventory(Entity e, Namespace namespace) {
        //long startTime = System.currentTimeMillis();
        InventoryInfo iInfo = (InventoryInfo) e;
        if (Log.loggingDebug)
            log.debug("saveInventory: rootBag=" + iInfo.getOid());
    //  log.dumpStack("saveInventory:Name=" + e.getName());

        for (OID subBagOid : iInfo.getBags()) {
            if (subBagOid != null) {
                Bag subBag = getBag(subBagOid);
                if (subBag == null) {
                    log.error("saveInventory: subBag not found oid=" + subBagOid);
                    continue;
                }
                if (Log.loggingDebug)
                    log.debug("saveInventory: subBag oid=" + subBag.getOid());
                long startTime1 = System.currentTimeMillis();
                Engine.getDatabase().saveObject(subBag, Namespace.BAG);
                //log.error("saveInventory: save bag Name=" + e.getName() + " namespace=" + namespace + " time=" + (System.currentTimeMillis() - startTime1) + "ms");

                for (OID itemOid : subBag.getItemsList()) {
                    if (itemOid != null) {
                        if (Log.loggingDebug)
                            log.debug("saveInventory: saving itemOid=" + itemOid);
                        //long startTime2 = System.currentTimeMillis();
                        ObjectManagerClient.saveObject(itemOid);
                        //log.error("saveInventory: save item Name=" + e.getName() + " namespace=" + namespace + " time=" + (System.currentTimeMillis() - startTime2) + "ms");
                        if (Log.loggingDebug)
                            log.debug("saveInventory: done saving itemOid=" + itemOid);
                    }
                }
            }
        }
        //log.error("saveInventory: I Name="+e.getName()+" namespace="+namespace+" time="+(System.currentTimeMillis() - startTime)+"ms");
        
        // Save Equipped Items Bag
        Bag subBag = getBag(iInfo.getEquipmentItemBag());
        if (subBag != null) {
            if (Log.loggingDebug)
                log.debug("saveInventory: subBag oid=" + subBag.getOid());
            Engine.getDatabase().saveObject(subBag, Namespace.BAG);

            for (OID itemOid : subBag.getItemsList()) {
                if (itemOid != null) {
                    if (Log.loggingDebug)
                        log.debug("saveInventory: saving itemOid=" + itemOid);
                    ObjectManagerClient.saveObject(itemOid);
                    if (Log.loggingDebug)
                        log.debug("saveInventory: done saving itemOid=" + itemOid);
                }
            }
        } else {
            log.debug("saveInventory: equippedItemsBag not found oid="+iInfo.getEquipmentItemBag());
        }
        //log.error("saveInventory: II Name="+e.getName()+" namespace="+namespace+" time="+(System.currentTimeMillis() - startTime)+"ms");
        
        // Save Storage Bags
        for (OID bagOid : iInfo.getStorageBags().values()) {
            Bag storageBag = getBag(bagOid);
            if (storageBag != null) {
                if (Log.loggingDebug)
                    log.debug("saveInventory: storageBag oid=" + storageBag.getOid());
                Engine.getDatabase().saveObject(storageBag, Namespace.BAG);

                for (OID itemOid : storageBag.getItemsList()) {
                    if (itemOid != null) {
                        if (Log.loggingDebug)
                            log.debug("saveInventory: saving itemOid=" + itemOid);
                        ObjectManagerClient.saveObject(itemOid);
                        if (Log.loggingDebug)
                            log.debug("saveInventory: done saving itemOid=" + itemOid);
                    }
                }
            } else {
                if (Log.loggingDebug)
                    log.debug("saveInventory: storageBag not found oid=" + bagOid);
            }
        }
        //log.error("saveInventory: III Name="+e.getName()+" namespace="+namespace+" time="+(System.currentTimeMillis() - startTime)+"ms");
        
        for (Mail m : iInfo.getMail()) {
            if (m.getItems() != null) {
                for (int i = 0; i < m.getItems().size(); i++) {
                    if (m.getItems().get(i) != null) {
                        AgisItem item = getAgisItem(m.getItems().get(i));
                        if (item != null) {
                            ObjectManagerClient.saveObject(m.getItems().get(i));
                        } else {
                            if (ObjectManagerClient.loadObject(m.getItems().get(i)) == null) {
                                if(Log.loggingDebug) Log.debug("MAIL: got null item to save: " + m.getItems().get(i) + " in mail: " + m.getID());
                            }else {
                                ObjectManagerClient.saveObject(m.getItems().get(i));
                            }
                        }
                    }
                }
            }
        }
        //log.error("saveInventory: IV Name="+e.getName()+" namespace="+namespace+" time="+(System.currentTimeMillis() - startTime)+"ms");
        if(iInfo.getPetInventory().size()>0) {
            for (PetInventoryInfo pii : iInfo.getPetInventory().values()) {
                Bag petBag = getBag(pii.getEquipmentItemBag());
                if (petBag != null) {
                    if (Log.loggingDebug)
                        log.debug("saveInventory: PetInventoryInfo petBag oid=" + petBag.getOid());
                    Engine.getDatabase().saveObject(petBag, Namespace.BAG);

                    for (OID itemOid : petBag.getItemsList()) {
                        if (itemOid != null) {
                            if (Log.loggingDebug)
                                log.debug("saveInventory: saving itemOid=" + itemOid);
                            ObjectManagerClient.saveObject(itemOid);
                            if (Log.loggingDebug)
                                log.debug("saveInventory: done saving itemOid=" + itemOid);
                        }
                    }
                } else {
                    if (Log.loggingDebug)
                        log.debug("saveInventory: equippedItemsBag not found oid=" + iInfo.getEquipmentItemBag());
                }

            }
        }

        // Save the actual InventoryInfo itself to make sure currencies are saved
        Engine.getDatabase().saveObject(e, Namespace.BAG);
        //log.error("saveInventory: End Name="+e.getName()+" namespace="+namespace+" time="+(System.currentTimeMillis() - startTime)+"ms");
        return true;
    }

    /**
     * Does nothing.
     */
    protected boolean saveItem(Entity e, Namespace namespace) {
        return false;
    }
    
    /**
     * Places an item into the specified container and slot.
     * @param mobOid
     * @param rootBagOid
     * @param itemOid
     * @param containerNum
     * @param slotNum
     * @return
     */
    private boolean placeItem(OID mobOid, OID rootBagOid, OID itemOid, int containerNum, int slotNum) {
        InventoryInfo iInfo = getInventoryInfo(rootBagOid);
        OID[] subBagOids = iInfo.getBags();
        OID subBagOid = subBagOids[containerNum];
        Bag subBag = getBag(subBagOid);
        if (subBag == null) {
            Log.warn("placeItem: did not find sub bag: " + subBagOid + " for bagoid=" + subBagOid);
            return false;
        }
        
        // get item
        AgisItem item = getAgisItem(itemOid);
        if (item == null) {
            return false;
        }

        // add backref to item
        /*if (item.getProperty(INVENTORY_PROP_BACKREF_KEY) != null) {
            Log.warn("placeItem: item is already in a container, itemOid="
                    + item.getOid());
            return false;
        }*/
        // add item to bag
        boolean rv = subBag.putItem(slotNum, itemOid);
        if (Log.loggingDebug)
            log.debug("placeItem: adding to bag, rv=" + rv);

        if (rv) {
            item.setProperty(INVENTORY_PROP_BACKREF_KEY, subBagOid);
            item.acquired(mobOid);
        }

        // mark dirty
        Engine.getPersistenceManager().setDirty(item);
        Engine.getPersistenceManager().setDirty(iInfo);
        sendBagInvUpdate(mobOid);
        return rv;
    }
    
    /**
     * Adds the item to the player/mob specified. Existing items will be checked for to see if an item can be added
     * to an existing stack before a new item is made.
     * TODO: Merge some of this code into the addItem function, and call that from here (once the item has been generated).
     * @param mobOid: the object identifier of the player/mob to add the item to
     * @param itemID: the id of the template of the item to be added
     * @param count: how many of the item should be added
     * @return: a boolean indicating whether or not the item was successfully added.
     */
    private boolean addItemFromTemplate(OID mobOid, int itemID, int count, HashMap<String, Serializable> itemProps) {
        lock.lock();
        try {
            // get bag
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            if (Log.loggingDebug) {
                log.debug("Adding item from template: " + itemID + " count: " + count);
                log.debug("addItem: found bag object: " + iInfo);
            }
            Template tmpl = ObjectManagerPlugin.getTemplate(itemID, ObjectManagerPlugin.ITEM_TEMPLATE);
            String itemName = tmpl.getName();
            int stackLimit = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "stackLimit");

            
            ArrayList<AcquireHook> acquireHook = (ArrayList<AcquireHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_ACQUIRE_HOOK);
            boolean curr = false;
            if (acquireHook != null) {
                for (AcquireHook ah : acquireHook) {
                    if (ah.getClass().equals(CurrencyItemAcquireHook.class)) {
                        CurrencyItemAcquireHook ciah = (CurrencyItemAcquireHook) ah;
                        int currencyId = ciah.getCurrencyID();
                        curr = true;
                         AgisInventoryClient.alterCurrency(mobOid, currencyId, count);
                    }
                }
            }

            if(!curr) {
            // Check if there are any existing stacks to add to
            ArrayList<OID> existingOids = findItemStacks(mobOid, itemID);
            if (existingOids.size() > 0) {
                if (Log.loggingDebug)
                    log.debug("ITEM: user already has item " + itemID + ", see if we can add it to one of the stacks.");
                for (OID existingOid: existingOids) {
                    AgisItem tempItem = getAgisItem(existingOid);
                    if (Log.loggingDebug)
                        log.debug("ITEM: stackSize " + tempItem.getStackSize() + ", stackLimit: " + tempItem.getStackLimit() + "for item: " + existingOid);
                    if((tempItem.getStackSize() + count) <= tempItem.getStackLimit()) {
                        // If the whole amonut fits into an existing stack, this is nice and easy
                        if (Log.loggingDebug)
                            log.debug("ITEM: increasing stack size for item: " + existingOid);
                        tempItem.alterStackSize(mobOid, count);
                        Engine.getPersistenceManager().setDirty(tempItem);
                        Engine.getPersistenceManager().setDirty(iInfo);
                        return true;
                    } else {
                        // Otherwise we add what we can to an existing stack and move onto the creation of new items
                        if (Log.loggingDebug)
                            log.debug("ITEM: increasing stack size to max for item: " + existingOid);
                        int stackSpace = tempItem.getStackLimit() - tempItem.getStackSize();
                        tempItem.alterStackSize(mobOid, stackSpace);
                        Engine.getPersistenceManager().setDirty(tempItem);
                        Engine.getPersistenceManager().setDirty(iInfo);
                        count = count - stackSpace;
                    }
                }
            }
            // check each subbag and see if it can be added there
            OID[] subBags = iInfo.getBags();
            int stacksNeeded = ((count-1) / stackLimit) + 1;
                if (Log.loggingDebug)
                    log.debug("ITEM: there is no stacks to add to for item: " + itemID + " so going to create a new item. Stacks needed: " + stacksNeeded);
            for (int i = 0; i < stacksNeeded; i++) {
                OID itemOid = generateItem(itemID, itemName);
                if (itemProps != null) {
                    AgisItem item = getAgisItem(itemOid);
                    for (String itemProp: itemProps.keySet()) {
                        item.setProperty(itemProp, itemProps.get(itemProp));
                    }
                }
                AgisItem item = getAgisItem(itemOid);
                if (count > 1) {
                    //int stackLimit = item.getStackLimit();
                    if (count > stackLimit) {
                        int stackSpace = item.getStackLimit() - item.getStackSize();
                        item.alterStackSize(mobOid, stackSpace);
                        count = count - stackLimit;
                    } else {
                        item.alterStackSize(mobOid, count-1);
                        count = 0;
                    }
                } else {
                    count = 0;
                }
                boolean stackAdded = false;
                for (int pos = 0; pos < subBags.length; pos++) {
                    OID subBag = subBags[pos];
                    if (addItemHelper(mobOid, subBag, -1, item)) {
                        Engine.getPersistenceManager().setDirty(iInfo);
                        stackAdded = true;
                        break;
                    }
                }
                if (stackAdded == false) {
                    if (Log.loggingDebug)
                        log.debug("ITEM: space for item: " + itemName + " was not found.");
                    return false;
                }
            }
            if (count > 0)
                return false;
            }
            //ExtendedCombatMessages.sendErrorMessage(oid, "There is no space in your bags.");
            //return false;
        }catch(Exception e) {
            Log.exception("addItemFromTemplate",e);
        } finally {
            lock.unlock();
        }
        return true;
    }

    /**
     * adds the item to the container item must not be in a container already
     * sets the item's containedBy backlink to the container
     */
    protected boolean addItem(OID mobOid, OID rootBagOid, OID itemOid) {
        return addItem(mobOid, rootBagOid, itemOid,true) ;
    }
    
    protected boolean addItem(OID mobOid, OID rootBagOid, OID itemOid, boolean tryStack) {
        lock.lock();
        try {
            // get bag
            InventoryInfo iInfo = getInventoryInfo(mobOid);

            if (Log.loggingDebug)
                log.debug("addItem: found bag object: " + iInfo);
            if(iInfo==null){
                log.warn("addItem: not found InventoryInfo for " + mobOid);
                return false;
            }
            // get item
            AgisItem item = getAgisItem(itemOid);
            /*if (item == null) {
                item = getBag(itemOid);
            }*/
            if (item == null) {
                Log.warn("addItem: item is null: oid=" + itemOid);
                return false;
            }
            if (Log.loggingDebug)
                log.debug("addItem: found item: " + item);
            
            int stackSizeToAdd = item.getStackSize();
            
            // check to see if the player already has the item
            ArrayList<OID> existingOids = findItemStacks(mobOid, item.getTemplateID());
            if(!tryStack) {
                existingOids =null;
            }
            if (existingOids != null) {
                if (existingOids.size() > 0) {

                    Log.debug("ITEM: user already has item " + item.getName() + ", see if we can add it to one of the stacks.");
                    for (OID existingOid : existingOids) {
                        AgisItem tempItem = getAgisItem(existingOid);
                        Log.debug("ITEM: stackSize " + tempItem.getStackSize() + ", stackLimit: " + tempItem.getStackLimit() + "for item: " + existingOid);
                        if ((tempItem.getStackSize() + stackSizeToAdd) <= tempItem.getStackLimit()) {
                            Log.debug("ITEM: increasing stack size for item: " + existingOid);
                            tempItem.alterStackSize(mobOid, stackSizeToAdd);
                            Engine.getPersistenceManager().setDirty(tempItem);
                            Engine.getPersistenceManager().setDirty(iInfo);
                            return true;
                        }
                    }
                }
            } else {
                Log.debug("ITEM: existingOids is null");
            }
       
            // check each subbag and see if it can be added there
            if (Log.loggingDebug)
                log.debug("ITEM: there is no stacks to add to for item: " + item.getName() + " so going to create a new item");
            OID[] subBags = iInfo.getBags();
            if (Log.loggingDebug)
                log.debug("ITEM: bag count: " + subBags.length);
            for (int pos = 0; pos < subBags.length; pos++) {
                OID subBag = subBags[pos];
                if (addItemHelper(mobOid, subBag, -1, item)) {
                    Engine.getPersistenceManager().setDirty(iInfo);
                    return true;
                }
            }
            ExtendedCombatMessages.sendErrorMessage(mobOid, "There is no space in your bags.");
            return false;
        }catch(Exception e){
            log.exception(e);
             return false;
        }
        finally {
            lock.unlock();
        }
       
    }
    
    /**
     * Helper function for the addItem function. This is where special on-acquire item actions
     * should take place as it is where the item is actually added to the bag.
     * @param subBagOid
     * @param slotNum
     * @param item
     * @return
     */
    protected boolean addItemHelper(OID ownerOid, OID subBagOid, int slotNum, AgisItem item) {
        // Run the acquire function on the item and if it returns true, destroy the item
        if (item.acquired(ownerOid)) {
            return true;
        }
        
        // get the bag object
        Bag subBag = getBag(subBagOid);
        if (subBag == null) {
            Log.warn("addItemHelper: did not find sub bag: " + subBagOid + " for bagoid=" + subBagOid);
            return false;
        }

        // add backref to item
        if (item.getProperty(INVENTORY_PROP_BACKREF_KEY) != null) {
            Log.warn("addItem: item is already in a container, itemOid="
                    + item.getOid());
            return false;
        }
        // add item to bag
        boolean rv = false;
        if (slotNum > 0) {
            rv = subBag.putItem(slotNum, item.getOid());
        } else {
            rv = subBag.addItem(item.getOid());
        }
        if (Log.loggingDebug)
            log.debug("addItem: adding to bag=" + subBag + " with slots=" + subBag.getNumSlots() + ", rv=" + rv);

        if (rv) {
            item.setProperty(INVENTORY_PROP_BACKREF_KEY, subBagOid);
        }else {
        	item.unacquired(ownerOid);
        }

        // mark dirty
        Engine.getPersistenceManager().setDirty(item);
        return rv;
    }

    /**
     * Removes an item from a Bag.
     */
    protected boolean removeItemFromBag(OID rootBagOid, OID itemOid) {
        lock.lock();
        try {
            // get root bag
            InventoryInfo iInfo = getInventoryInfo(rootBagOid);
            if (Log.loggingDebug)
                log.debug("removeItemFromBag: found root bag object: " + iInfo);
            if (iInfo == null) {
                Log.warn("removeItemFromBag: InventoryInfo is null: itemOid=" + itemOid+" rootBagOid="+rootBagOid);
                return true;
            }
            // get item
            AgisItem item = getAgisItem(itemOid);
            if (item == null) {
                Log.warn("removeItemFromBag: item is null: oid=" + itemOid);
                return false;
            }
            if (Log.loggingDebug)
                log.debug("removeItemFromBag: found item: " + item);
            
           
            // check each subbag to find its container
            OID[] subBags = iInfo.getBags();
            for (int pos = 0; pos < subBags.length; pos++) {
                OID subBag = subBags[pos];
                if (removeItemFromBagHelper(subBag, item)) {
                    Engine.getPersistenceManager().setDirty(iInfo);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Helper function for the removeItemFromBag function.
     * @param subBagOid
     * @param item
     * @return
     */
    protected boolean removeItemFromBagHelper(OID subBagOid, Entity item) {
        if (Log.loggingDebug) Log.debug("removeItemFromBagHelper: bag oid="+subBagOid+" item=" + (item != null ? item.getOid() : "bd"));
        // get the bag object
        Bag subBag = getBag(subBagOid);
        if (subBag == null) {
            Log.warn("removeItemFromBagHelper: did not find sub bag: " + subBagOid);
            return false;
        }

        // does bag contain the item?
        Integer slotNum = subBag.findItem(item.getOid());
        if (slotNum == null) {
            if (Log.loggingDebug)
                log.debug("removeItemFromBagHelper: item not in bag itemOid=" + item.getOid() + " bagOid="+subBagOid);
            return false;
        }
        
        // found the item
        if (Log.loggingDebug)
            log.debug("removeItemFromBagHelper: found - slot=" + slotNum + ", itemOid=" + item.getOid());
        
        // remove item from bag - we separate the logic here from finding the item
        // because perhaps there was some other reason why the remove failed
        boolean rv = subBag.removeItem(item.getOid());
        if (rv == false) {
            if (Log.loggingDebug)
                log.debug("removeItemFromBagHelper: remove item failed");
            return false;
        }
        log.debug("removeItemFromBagHelper: findItem "+subBag.findItem(item.getOid()));
        // remove the back reference
        item.setProperty(INVENTORY_PROP_BACKREF_KEY, null);

        // mark dirty
        Engine.getPersistenceManager().setDirty(item);
        if (Log.loggingDebug)
            log.debug("removeItemFromBagHelper: remove from bag, rv=" + rv);
        return rv;
    }
    
    protected OID removeItemFromStorage(OID mobOid, OID itemOid, boolean removeFromPlayer) {
        if (Log.loggingDebug)
            log.debug("removeItemFromStorage: mobOid="+mobOid+" itemOid="+itemOid+" removeFromPlayer="+removeFromPlayer);
        InventoryInfo iInfo = getInventoryInfo(mobOid);
        if (iInfo==null) {
            log.debug("removeItemFromStorage: InventoryInfo:"+iInfo+"  is null break");
                return itemOid;
        }
        
        AgisItem item = getAgisItem(itemOid);
        OID oldBagID = (OID) item.getProperty(INVENTORY_PROP_BACKREF_KEY);
        if (Log.loggingDebug)
            log.debug("removeItemFromStorage: item:"+item+" oldBagID:"+oldBagID);
        if (oldBagID==null) {
            log.debug("removeItemFromStorage: item:"+item+" oldBagID:"+oldBagID+" is null break");
                return itemOid;
        }
        
        
        boolean nonPlayerStorage = false;
        if (iInfo.getActiveStorage() == null || iInfo.getActiveStorage().isEmpty()) {
            return null;
        }
        OID bankBagOid = iInfo.getActiveStorageBag();
        if (bankBagOid == null) {
            // It may be a non player storage bag
            if (iInfo.getActiveStorage() != null) {
                bankBagOid = OID.fromString(iInfo.getActiveStorage());
                nonPlayerStorage = true;
            }
            if (bankBagOid == null) {
                Log.debug("removeItemFromStorage: bankBagOid is null");
                
                //TODO: some error
                return null;
            }
        }
        
        Bag storageBag = getBag(bankBagOid);
        if (Log.loggingDebug)
            log.debug("STORAGE: got storageBagOid: " + bankBagOid + " against oldBagID: " + oldBagID);
        if (!oldBagID.equals(bankBagOid)) {
            if (Log.loggingDebug)
                log.debug("removeItemFromStorage: checking storage for item: " + itemOid + " with oldBagID: " + oldBagID);
            for (String storageBagName : iInfo.getStorageBags().keySet()) {
                if (Log.loggingDebug)
                    log.debug("removeItemFromStorage: checking storage bag: " + iInfo.getStorageBags().get(storageBagName));
                if (iInfo.getStorageBags().get(storageBagName).equals(oldBagID)) {
                    if (Log.loggingDebug)
                        log.debug("removeItemFromStorage: found storageBag for item: " + storageBagName);
                    storageBag = getBag(oldBagID);
                }
            }
        } 
        
        Integer slotNum = storageBag.findItem(item.getOid());
        if (slotNum == null) {
            if (Log.loggingDebug)
                log.debug("removeItemFromStorage: item not in bag itemOid=" + item.getOid() + " bagOid="+oldBagID);
            return null;
        }
        // remove item from bag - we separate the logic here from finding the item
        // because perhaps there was some other reason why the remove failed
        boolean rv = storageBag.removeItem(item.getOid());
        if (rv == false) {
            if (Log.loggingDebug)
                log.debug("removeItemFromStorage: remove item failed");
            return null;
        }
        
        // remove the back reference
        item.setProperty(INVENTORY_PROP_BACKREF_KEY, null);

        // mark dirty
        Engine.getPersistenceManager().setDirty(item);
        Engine.getPersistenceManager().setDirty(iInfo);
        
        // send bank update
        sendStorageInvUpdate(mobOid);
        
        if (nonPlayerStorage) {
            saveNonPlayerStorageBag(storageBag);
        }
        
        return itemOid;
    }
    
    /**
     * Called when an object is updated, sending information about the inventory
     * to the client.
     */
    public void updateObject(OID mobOid, OID target) {
        // This is a player-type mob if it's asking about itself, i.e., subject == target
        if (!mobOid.equals(target)) {
            if (Log.loggingDebug)
                log.debug("updateObject: obj is not a player, ignoring: " + mobOid);
            return;
        }
        if (Log.loggingDebug)
            log.debug("updateObject: obj is a player: " + mobOid);
        
        // send out inv update
        InventoryInfo iInfo = getInventoryInfo(mobOid);
        if (iInfo != null){
            Log.debug("AgisInventoryPlugin - sending inventory update");            
            sendInvUpdate(mobOid);
        }
        else
        if (Log.loggingDebug)
            log.debug("updateObject: could not find entity in " + Namespace.BAG + " for mobOid " + mobOid);
        
        if (VendorPlugin.useVirtualCurrency()) {
            Log.debug("AgisInventoryPlugin - sending token balance");
            VendorClient.sendBalanceUpdate(mobOid, BillingClient
                    .getTokenBalance(mobOid));
        }
        return;
    }
    
    
	class GenerateLootEffectHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			AgisInventoryClient.generateLootEffectMessage getMsg = (AgisInventoryClient.generateLootEffectMessage) msg;
			OID playerOid = getMsg.getSubject();

			HashMap<Integer, Float> lootsChance = (HashMap<Integer, Float>) getMsg.getLootsChance();
			HashMap<Integer, Integer> lootsCount = (HashMap<Integer, Integer>) getMsg.getLootsCount();

			HashMap<Integer, Integer> itemsToAdd = new HashMap<Integer, Integer>();
			LinkedList<OID> itemsToLoot = new LinkedList<OID>();

			for (int lootTable : lootsChance.keySet()) {
				float tableChance = lootsChance.get(lootTable);
				float roll = AgisInventoryPlugin.random.nextFloat() * 100f;
                if (Log.loggingDebug)
                    log.debug("LOOT: roll for loot table: " + lootTable + " is: " + roll + " with tablechance: " + tableChance);
				if (roll < tableChance) {
                    if (Log.loggingDebug)
                        log.debug("LOOT: lootManager has: " + Agis.LootTableManager.getMap());
					LootTable lt = Agis.LootTableManager.get(lootTable);
					 for (int i = 0; i < lootsCount.get(lootTable); i++) {
					int itemNum = lt.getNewRandomItemNum();
                         if (Log.loggingDebug)
                             log.debug("LOOT: randomised item number: " + itemNum);
					if (itemNum >= 0) {
						int templateID = lt.getItems().get(itemNum);
                        if (Log.loggingDebug)
                            log.debug("LOOT: LootTable=" + lootTable + " randomised " + i + " item number: " + itemNum + " templateID=" + templateID);
						if (templateID > -1) {
							int count = lt.getRandomCountOfItem(itemNum);
                            if (Log.loggingDebug)
                                log.debug("LOOT: LootTable=" + lootTable + " randomised " + i + "item templateID: " + templateID + " count=" + count);
							if (itemsToAdd.containsKey(templateID)) {
								itemsToAdd.put(templateID, itemsToAdd.get(templateID) + count);
							} else {
								itemsToAdd.put(templateID, count);
							}

						}
					} else {
                        if (Log.loggingDebug)
                            log.debug("LOOT: roll get no item for loot Table " + lt.getID());
					}
					 }
				}
			}
         Log.debug("loot CreateItemFromLootEffect "+itemsToAdd);
         if (!AgisInventoryClient.doesInventoryHaveSufficientSpace(playerOid, itemsToAdd)) {
             Log.debug("loot CreateItemFromLootEffect INVENTORY_FULL");
                EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INVENTORY_FULL, 0, "");
            return false;
        }
         if (itemsToAdd.size() > 0) {
             Log.debug("loot CreateItemFromLootEffect generateItems");
                AgisInventoryClient.generateItems(playerOid, itemsToAdd, false);
        } 
            return true;
        }
        
    }
    
    
    
    
    /**
     * Handles the GenerateLootMessage. Generates the loot for the specified mob
     * based on the loot tables assigned to the mob.
     * The GenerateLootMessage is usually sent when a mob dies.
     * @author Andrew
     *
     */
    class GenerateLootHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.generateLootMessage lootMsg = (AgisInventoryClient.generateLootMessage) msg;
            OID mobOid = lootMsg.getSubject();
            if (Log.loggingDebug)
                log.debug("GenerateLootHook: mobOid=" + mobOid + "  IsPlayer=" + lootMsg.getIsPlayer());

            if (lootMsg.getIsPlayer()) {
                generatePlayerLoot(mobOid, lootMsg.getLoc());
                return true;
            }
            ArrayList<OID> targets = lootMsg.getTagats();
            boolean lootForAll = true;
            if (targets == null) {
                lootForAll = false;
                targets = new ArrayList<OID>();
            }
            int groll = 0;
            int gdice = 0;
            int ggrade = 0;

            if (Log.loggingDebug)
                log.debug("LOOT: Generating Loot for mob: " + mobOid);
            // Work out who can loot the mob
            List<OID> lootableTargets = new ArrayList<OID>();
            OID ply = lootMsg.getTagOwner();
            if (lootMsg.getTagOwner() != null) {
                if (!targets.contains(lootMsg.getTagOwner()))
                    targets.add(lootMsg.getTagOwner());
                GroupInfo gInfo = GroupClient.GetGroupMemberOIDs(lootMsg.getTagOwner());
                if (gInfo.groupOid != null) {
                    groll = gInfo.roll;
                    gdice = gInfo.dice;
                    ggrade = gInfo.grade;
                    if (groll == 2) {
                        ply = gInfo.groupLeaderOid;
                        lootableTargets.add(ply);
                        /*TargetedPropertyMessage propMsg = new TargetedPropertyMessage(gInfo.groupLeaderOid, mobOid);
                        propMsg.setProperty("lootable", true);
                        Engine.getAgent().sendBroadcast(propMsg);*/
                    }
                    for (OID memberOid : gInfo.memberOidSet) {
                        targets.add(memberOid);

                    }
                } else {
                	lootableTargets.add(lootMsg.getTagOwner());
                   /* TargetedPropertyMessage propMsg = new TargetedPropertyMessage(lootMsg.getTagOwner(), mobOid);
                    propMsg.setProperty("lootable", true);
                    Engine.getAgent().sendBroadcast(propMsg);*/
                }
            }
            if (groll == 1) {
                int roll = AgisInventoryPlugin.random.nextInt(targets.size());
                lootableTargets.add(targets.get(roll));
               /* TargetedPropertyMessage propMsg = new TargetedPropertyMessage(targets.get(roll), mobOid);
                propMsg.setProperty("lootable", true);
                Engine.getAgent().sendBroadcast(propMsg);*/
            }
            BasicWorldNode mobnode = null;
            mobnode = WorldManagerClient.getWorldNode(mobOid);
            ArrayList<OID> targetsInArrea = targets;
            if (mobnode != null) {
                try {
					targetsInArrea = AgisWorldManagerClient.checkIfTargetsInArea(mobOid, targets, mobnode.getLoc(), 40, 360, Quaternion.Identity, 0F);
				} catch (Exception e) {
					Log.exception("GenerateLootHook",e);
				}
            }
            if (ply != null && !targetsInArrea.contains(ply))
                targetsInArrea.add(ply);

            if (groll == 0) {
                for (OID memberOid : targetsInArrea) {
                	 lootableTargets.add(memberOid);
                   /* TargetedPropertyMessage propMsg = new TargetedPropertyMessage(memberOid, mobOid);
                    propMsg.setProperty("lootable", true);
                    Engine.getAgent().sendBroadcast(propMsg);*/
                }
            }
            // Calculate loot
            HashMap<OID, List<OID>> itemsLootedByPlayer = new HashMap<OID, List<OID>>();
            HashMap<Integer, MobLootTable> mobLootTables = (HashMap) getLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "lootTables");

            if (Log.loggingDebug)
                log.debug("GenerateLootHook: mobLootTables=" + mobLootTables);
            if (!INVENTORY_LOOT_ON_GROUND  && !lootForAll) {
                
            
           // for(OID target: targets) {
            
                HashMap<Integer, Integer> itemsToAdd = new HashMap<Integer, Integer>();
                LinkedList<OID> itemsToLoot = new LinkedList<OID>();
                HashMap<Integer, Integer> currencyToAdd = new HashMap<Integer, Integer>();

                if (mobLootTables != null && mobLootTables.size() > 0)
                    for (int id = 0; id < mobLootTables.size(); id++) {
                        MobLootTable mlt = mobLootTables.get(id);
                        float tableChance = mlt.getChances();
                        float roll = AgisInventoryPlugin.random.nextFloat() * 100f;
                        if (Log.loggingDebug)
                            log.debug("LOOT: roll for loot table: " + mlt.getID() + " is: " + roll + " with tablechance: " + tableChance + " count Item=" + mlt.getItemsPerLoot());
                        if (roll < tableChance) {
                            if (Log.loggingDebug)
                                log.debug("LOOT: lootManager has: " + Agis.LootTableManager.getMap());
                            LootTable lt = Agis.LootTableManager.get(mlt.getID());
                            for (int i = 0; i < mlt.getItemsPerLoot(); i++) {
                                int itemNum = lt.getNewRandomItemNum();
                                if (itemNum >= 0) {
                                    int templateID = lt.getItems().get(itemNum);
                                    if (Log.loggingDebug)
                                        log.debug("LOOT: LootTable=" + lt.getID() + " randomised " + i + " item number: " + itemNum + " templateID=" + templateID);
                                    if (templateID > -1) {
                                        int count = lt.getRandomCountOfItem(itemNum);
                                        if (Log.loggingDebug)
                                            log.debug("LOOT: LootTable=" + lt.getID() + " randomised " + i + "item templateID: " + templateID + " count=" + count);
                                        if (itemsToAdd.containsKey(templateID)) {
                                            itemsToAdd.put(templateID, itemsToAdd.get(templateID) + count);
                                        } else {
                                            itemsToAdd.put(templateID, count);
                                        }
                                    }
                                }else {
                                    if (Log.loggingDebug)
                                        log.debug("LOOT: Mob loot table: " + mlt.getID() +" roll get no item for loot Table "+lt.getID());
                                }
                            }
                        }
                    }

                if (Log.loggingDebug)
                    log.debug("GenerateLootHook: itemsToAdd=" + itemsToAdd);

            
           // }
            for (int templateID: itemsToAdd.keySet()) {
                //Template tmpl = ObjectManagerClient.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
                Template tmpl = ObjectManagerPlugin.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);

                int stackLimit = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "stackLimit");
                    ArrayList<AcquireHook> acquireHook = (ArrayList<AcquireHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_ACQUIRE_HOOK);
                    boolean curr = false;
                    // if (Log.loggingDebug)
                    //      log.debug("CreateInventoryHook.processMessage: generating item=" +templateID);
                    if (acquireHook != null) {
                        for (AcquireHook ah : acquireHook) {
                            if (ah.getClass().equals(CurrencyItemAcquireHook.class)) {
                                CurrencyItemAcquireHook ciah = (CurrencyItemAcquireHook) ah;
                                int currencyId = ciah.getCurrencyID();
                                curr = true;
                                // AgisInventoryClient.alterCurrency(masterOid, currencyId, itemsToAdd.get(templateID));
                                if (currencyToAdd.containsKey(currencyId))
                                    currencyToAdd.replace(currencyId, itemsToAdd.get(templateID) + currencyToAdd.get(currencyId));
                                else
                                    currencyToAdd.put(currencyId, itemsToAdd.get(templateID));
                            }
                        }
                    }
                    if (!curr) {

                        if (stackLimit > itemsToAdd.get(templateID)) {
                            OID itemOid = generateItem(templateID, tmpl.getName());
                            AgisItem item = getAgisItem(itemOid);
                            item.setStackSize(itemsToAdd.get(templateID));
                            if (Log.loggingDebug)
                                log.debug("LOOT: Generating Loot: created item=" + itemOid);
                            boolean itemAdded = InventoryClient.addItem(mobOid, mobOid, mobOid, itemOid);
                            Log.debug("LOOT: adding item: " + tmpl.getName() + " to mobs loot with result: " + itemAdded + " " + itemsToAdd.get(templateID));
                            if (itemAdded) {
                                itemsToLoot.add(itemOid);
                                itemsLootedByPlayer.put(itemOid, new LinkedList<OID>());
                            }
                        } else {
                            int countUsed = 0;
                            int counttogenerate = itemsToAdd.get(templateID) / stackLimit;
                            if (itemsToAdd.get(templateID) % stackLimit > 0)
                                counttogenerate++;
                            for (int i = 0; i < counttogenerate; i++) {
                                if (Log.loggingDebug)
                                    log.debug("LOOT: Generating Loot: i=" + i + " count=" + itemsToAdd.get(templateID) + " stackLimit=" + stackLimit + " counttogenerate=" + counttogenerate + " countUsed=" + countUsed);

                                OID itemOid = generateItem(templateID, tmpl.getName());
                                AgisItem item = getAgisItem(itemOid);
                                if (itemsToAdd.get(templateID) - countUsed < stackLimit) {
                                    item.setStackSize(itemsToAdd.get(templateID) - countUsed);
                                } else {
                                    item.setStackSize(stackLimit);
                                    countUsed += stackLimit;
                                }
                                // addItem(mobOid, mobOid, itemOid);
                                boolean itemAdded = InventoryClient.addItem(mobOid, mobOid, mobOid, itemOid);
                                if (Log.loggingDebug)
                                    log.debug("LOOT: adding item: " + tmpl.getName() + " to mobs loot with result: " + itemAdded + " " + itemsToAdd.get(templateID));

                                if (itemAdded) {
                                    itemsToLoot.add(itemOid);
                                    itemsLootedByPlayer.put(itemOid, new LinkedList<OID>());
                                }
                            }

                        }
                    }
            
            }
            
				InventoryInfo iInfo = getInventoryInfo(mobOid);
				if (iInfo != null) {
					iInfo.setProperty("itemsLootedByPlayer", itemsLootedByPlayer);
					iInfo.setProperty("groll", groll);
					iInfo.setProperty("gdice", gdice);
					iInfo.setProperty("ggrade", ggrade);
					iInfo.setProperty("loottargets", targetsInArrea);
					iInfo.setProperty("roll", 0);
                    if (Log.loggingDebug)
                        log.debug("LOOT: currencyToAdd=" + currencyToAdd);
					LinkedList<String> currencyToLoot = new LinkedList<String>();
					for (int i : currencyToAdd.keySet()) {
						currencyToLoot.add(i + ";" + currencyToAdd.get(i));
					}
					setLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot", itemsToLoot);
					setLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot_curr", currencyToLoot);
                    if (Log.loggingDebug)
                        log.debug("Mob: " + mobOid + " now has loot: " + itemsToLoot + " currencyToAdd=" + currencyToAdd);
					if (itemsToLoot.isEmpty() && currencyToLoot.isEmpty()) {
						setMobSkinnable(mobOid);
					} else {
						for (OID oid : lootableTargets) {
							TargetedPropertyMessage propMsg = new TargetedPropertyMessage(oid, mobOid);
							propMsg.setProperty("lootable", true);
							Engine.getAgent().sendBroadcast(propMsg);
						}
					}
				}
            } else {
                
                // Personal Loot For All Player
                if (Log.loggingDebug)
                    log.debug("Mob: " + mobOid + " LootForAll");
                HashMap<OID, LinkedList<OID>> itemsLootForPlayer = new HashMap<OID, LinkedList<OID>>();
                HashMap<OID, HashMap<Integer, Integer>> currencyLootForPlayer = new HashMap<OID,HashMap<Integer, Integer>>();
                HashMap<OID, HashMap<Integer, Integer>> newItemsLootForPlayer = new HashMap<OID,HashMap<Integer, Integer>>();

                int itemsinloot=0;
                for (OID target : targetsInArrea) {
                    float bonusModp = 0f;
                    InventoryInfo iInfo = getInventoryInfo(target);
                    if(iInfo.getBonuses().containsKey("LootChance")) {
                        bonusModp =iInfo.getBonuses().get("LootChance").GetValuePercentage();
                    }
                    if(globalEventBonusesArray.containsKey("LootChance")) {
                        bonusModp += globalEventBonusesArray.get("LootChance").GetValuePercentage();
                    }
                    HashMap<Integer, Integer> itemsToAdd = new HashMap<Integer, Integer>();
                    LinkedList<OID> itemsToLoot = new LinkedList<OID>();
                    HashMap<Integer, Integer> currencyToAdd = new HashMap<Integer, Integer>();
                    
                    
                    if (mobLootTables != null && mobLootTables.size() > 0)
                        for (int id = 0; id < mobLootTables.size(); id++) {
                            MobLootTable mlt = mobLootTables.get(id);
                            float tableChance = mlt.getChances();
                            float roll = AgisInventoryPlugin.random.nextFloat() * 100f;
                            if (Log.loggingDebug)
                                log.debug("LOOT: roll for loot table: " + mlt.getID() + " is: " + roll + " with tablechance: " + tableChance + " count Item=" + mlt.getItemsPerLoot());
                            if (roll < tableChance) {
                                if (Log.loggingDebug)
                                    log.debug("LOOT: lootManager has: " + Agis.LootTableManager.getMap());
                                LootTable lt = Agis.LootTableManager.get(mlt.getID());
                                for (int i = 0; i < mlt.getItemsPerLoot(); i++) {
                                    int itemNum = lt.getNewRandomItemNum(bonusModp);
                                    if (Log.loggingDebug)
                                        log.debug("LOOT: randomised item number: " + itemNum);
									if (itemNum >= 0) {
                                        int templateID = lt.getItems().get(itemNum);
                                        if (templateID > -1) {
                                            int count = lt.getRandomCountOfItem(itemNum);
                                            if (itemsToAdd.containsKey(templateID)) {
                                                itemsToAdd.put(templateID, itemsToAdd.get(templateID) + count);
                                            } else {
                                                itemsToAdd.put(templateID, count);
                                            }
                                        }
                                    }
                                }
                            }
                        }


                    if (Log.loggingDebug)
                        log.debug("Mob: " + mobOid + " LootForAll itemsToAdd="+itemsToAdd);
                    // }
                    if(!INVENTORY_LOOT_ON_GROUND) {
                        for (int templateID : itemsToAdd.keySet()) {
                            Template tmpl = ObjectManagerPlugin.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);


                            int stackLimit = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "stackLimit");
                            ArrayList<AcquireHook> acquireHook = (ArrayList<AcquireHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_ACQUIRE_HOOK);
                            boolean curr = false;
                            // if (Log.loggingDebug)
                            //      log.debug("CreateInventoryHook.processMessage: generating item=" +templateID);
                            if (acquireHook != null) {
                                for (AcquireHook ah : acquireHook) {
                                    if (ah.getClass().equals(CurrencyItemAcquireHook.class)) {
                                        CurrencyItemAcquireHook ciah = (CurrencyItemAcquireHook) ah;
                                        int currencyId = ciah.getCurrencyID();
                                        curr = true;
                                        // AgisInventoryClient.alterCurrency(masterOid, currencyId, itemsToAdd.get(templateID));
                                        if (currencyToAdd.containsKey(currencyId))
                                            currencyToAdd.replace(currencyId, itemsToAdd.get(templateID) + currencyToAdd.get(currencyId));
                                        else
                                            currencyToAdd.put(currencyId, itemsToAdd.get(templateID));
                                    }
                                }
                            }


                            if (!curr) {

                                if (stackLimit > itemsToAdd.get(templateID)) {
                                    OID itemOid = generateItem(templateID, tmpl.getName());
                                    AgisItem item = getAgisItem(itemOid);
                                    item.setStackSize(itemsToAdd.get(templateID));
                                    if (Log.loggingDebug)
                                        log.debug("LOOT: Generating Loot: created item=" + itemOid);
                                    boolean itemAdded = InventoryClient.addItem(mobOid, mobOid, mobOid, itemOid);
                                    Log.debug("LOOT: adding item: " + tmpl.getName() + " to mobs loot with result: " + itemAdded + " " + itemsToAdd.get(templateID));
                                    if (itemAdded) {
                                        itemsToLoot.add(itemOid);
                                        itemsLootedByPlayer.put(itemOid, new LinkedList<OID>());
                                    }
                                } else {
                                    int countUsed = 0;
                                    int counttogenerate = itemsToAdd.get(templateID) / stackLimit;
                                    if (itemsToAdd.get(templateID) % stackLimit > 0)
                                        counttogenerate++;
                                    for (int i = 0; i < counttogenerate; i++) {
                                        if (Log.loggingDebug)
                                            log.debug("LOOT: Generating Loot: i=" + i + " count=" + itemsToAdd.get(templateID) + " stackLimit=" + stackLimit + " counttogenerate=" + counttogenerate + " countUsed=" + countUsed);

                                        OID itemOid = generateItem(templateID, tmpl.getName());
                                        AgisItem item = getAgisItem(itemOid);
                                        if (itemsToAdd.get(templateID) - countUsed < stackLimit) {
                                            item.setStackSize(itemsToAdd.get(templateID) - countUsed);
                                        } else {
                                            item.setStackSize(stackLimit);
                                            countUsed += stackLimit;
                                        }
                                        // addItem(mobOid, mobOid, itemOid);
                                        boolean itemAdded = InventoryClient.addItem(mobOid, mobOid, mobOid, itemOid);
                                        if (Log.loggingDebug)
                                            log.debug("LOOT: adding item: " + tmpl.getName() + " to mobs loot with result: " + itemAdded + " " + itemsToAdd.get(templateID));

                                        if (itemAdded) {
                                            itemsToLoot.add(itemOid);
                                            itemsLootedByPlayer.put(itemOid, new LinkedList<OID>());
                                        }
                                    }

                                }
                            }


                        }
                        itemsLootForPlayer.put(target, itemsToLoot);
                        currencyLootForPlayer.put(target, currencyToAdd);
                        if (Log.loggingDebug)
                            log.debug("Mob: " + mobOid + " LootForAll itemsLootForPlayer="+itemsLootForPlayer);
                    } else {
                        if (Log.loggingDebug)
                            log.debug("GenerateLootHook INVENTORY_LOOT_ON_GROUND for target "+target);
                        List<Integer> questItemReqs = QuestClient.getQuestItemReqs(target);
                      for (int templateID : itemsToAdd.keySet()) {
                          Template tmpl = ObjectManagerPlugin.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
                          int stackLimit = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "stackLimit");
                          String itemType = (String) tmpl.get(InventoryClient.ITEM_NAMESPACE, "itemType");
                          if (itemType.equals("Quest") && !questItemReqs.contains(templateID)) {
                              if(Log.loggingDebug)Log.debug("Mob: " + mobOid + " for target "+target+" skip item templateID="+templateID+" not have Quest");
                          } else {
                              if (stackLimit > itemsToAdd.get(templateID)) {
                                  if (Log.loggingDebug)
                                      log.debug("LOOT: adding item: " + tmpl.getName() + " count " + itemsToAdd.get(templateID));
                                  ItemOnGroundData item = new ItemOnGroundData(null, templateID, itemsToAdd.get(templateID), new AOVector(mobnode.getLoc()));
                                  itemsOnGround.computeIfAbsent(mobnode.getInstanceOid(), __ -> new ConcurrentHashMap<>()).
                                          computeIfAbsent(target, __ -> new ConcurrentHashMap<>()).put(item.getOid(), item);
                              } else {
                                  int countUsed = 0;
                                  int counttogenerate = itemsToAdd.get(templateID) / stackLimit;
                                  if (itemsToAdd.get(templateID) % stackLimit > 0)
                                      counttogenerate++;
                                  for (int i = 0; i < counttogenerate; i++) {
                                      if (Log.loggingDebug)
                                          log.debug("LOOT: Generating Loot: i=" + i + " count=" + itemsToAdd.get(templateID) + " stackLimit=" + stackLimit + " counttogenerate=" + counttogenerate + " countUsed=" + countUsed);
                                      if (itemsToAdd.get(templateID) - countUsed < stackLimit) {
                                          ItemOnGroundData item = new ItemOnGroundData(null, templateID, itemsToAdd.get(templateID) - countUsed, new AOVector(mobnode.getLoc()));
                                          itemsOnGround.computeIfAbsent(mobnode.getInstanceOid(), __ -> new ConcurrentHashMap<>()).
                                                  computeIfAbsent(target, __ -> new ConcurrentHashMap<>()).put(item.getOid(), item);

                                      } else {
                                          ItemOnGroundData item = new ItemOnGroundData(null, templateID, stackLimit, new AOVector(mobnode.getLoc()));
                                          itemsOnGround.computeIfAbsent(mobnode.getInstanceOid(), __ -> new ConcurrentHashMap<>()).
                                                  computeIfAbsent(target, __ -> new ConcurrentHashMap<>()).put(item.getOid(), item);
                                          countUsed += stackLimit;

                                      }
                                  }
                              }
                          }
                      }

                     //   currencyLootForPlayer.put(target, currencyToAdd);
                        if (Log.loggingDebug)
                            log.debug("Mob: " + mobOid + " LootForAll itemsLootForPlayer=" + itemsLootForPlayer);


                    }
                }
                if(!INVENTORY_LOOT_ON_GROUND) {
                    InventoryInfo iInfo = getInventoryInfo(mobOid);
                    if (iInfo != null) {
                        iInfo.setProperty("itemsLootedByPlayer", itemsLootedByPlayer);
                        iInfo.setProperty("itemsLootForPlayer", itemsLootForPlayer);
                        iInfo.setProperty("currLootForPlayer", currencyLootForPlayer);
                        iInfo.setProperty("loottargets", targets);
                        setLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot", null);
                        setLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot_curr", null);
                        if (Log.loggingDebug)
                            log.debug("Mob: " + mobOid + " now has loot: " + itemsLootForPlayer);

                        if (itemsinloot == 0) {
                            setMobSkinnable(mobOid);
                        } else {

                        }
                        for (OID memberOid : itemsLootForPlayer.keySet()) {
                            if (itemsLootForPlayer.get(memberOid).size() > 0) {
                                if (Log.loggingDebug)
                                    log.debug("Mob: " + mobOid + " LootForAll send lootable for " + memberOid);
                                TargetedPropertyMessage propMsg = new TargetedPropertyMessage(memberOid, mobOid);
                                propMsg.setProperty("lootable", true);
                                Engine.getAgent().sendBroadcast(propMsg);
                            }
                        }
                    }
                }else{
                    setMobSkinnable(mobOid);
                    for (OID target: targets) {
                       sendLootGroundUpdate(target, mobnode.getInstanceOid());
                    }
                }
                if (Log.loggingDebug)
                    log.debug("Mob: " + mobOid + " LootForAll End");
            }
            return true;
        }
    }

    private void sendLootGroundUpdate(OID target, OID instanceOid) {
            if (Log.loggingDebug)
                log.debug("sendLootGroundUpdate target=" + target);
            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("ext_msg_subtype", "LootGroundUpdate");
            AtomicInteger c = new AtomicInteger(0);
            itemsOnGround.computeIfAbsent(instanceOid, __ -> new ConcurrentHashMap<>()).compute(target,(k,v)->{
                    if(v!=null) {
                        for (ItemOnGroundData i : v.values()) {
                            props.put("i" + c.get() + "id", i.getOid());
                            props.put("i" + c.get() + "tid", i.getTemplateId());
                            props.put("i" + c.get() + "loc", i.getSpawnLoc());
                            props.put("i" + c.getAndIncrement() + "s", i.getStack());
                        }
                    }
                    return v;
                });

            props.put("num", c.get());
            if(Log.loggingDebug)
                log.debug("SendBagInvUpdate target="+target+" " + props);
            TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, target, target, false, props);
            Engine.getAgent().sendBroadcast(msg);
    }

    void generatePlayerLoot(OID playerOid, Point loc) {
        if (Log.loggingDebug)
            log.debug("generatePlayerLoot: Generating Loot for player: " + playerOid);
        if (PLAYER_CORPSE_LOOT_DURATION < 1) {
            log.debug("generatePlayerLoot PLAYER_CORPSE_LOOT_DURATION <1 return");
            return;
        }
        if (Log.loggingDebug)
            log.debug("generatePlayerLoot: removing players equipped items? " + PLAYER_CORPSE_DROPS_EQUIPMENT);
        // Remove players items and give them to the mob
        //LinkedList<OID> loot = new LinkedList<OID>(removeAllItems(playerOid));
        Random rand = new Random();
        LinkedList<OID> loot = new LinkedList<OID>();
        InventoryInfo iInfo = getInventoryInfo(playerOid);
        OID[] subBags = iInfo.getBags();
        for (int pos = 0; pos < subBags.length; pos++) {
            if (pos >= INVENTORY_BAG_COUNT)
                break;
            Bag subBag = getBag(subBags[pos]);
            for(OID itemOid : subBag.getItemsList()) {
                if (itemOid != null) {
                    AgisItem item = getAgisItem(itemOid);
                    // Some items may be set not to drop (or have a lower chance)
                    int deathLossChance = 100;
                    if (item.getProperty("deathLossChance") != null) {
                        deathLossChance = (Integer)item.getProperty("deathLossChance");
                    }
                    if (rand.nextInt(100) < deathLossChance) {
                        loot.add(itemOid);
                    }
                }
            }
        }
        for (OID itemOid : loot) {
            if (itemOid != null)
                removeItem(playerOid, itemOid, true);
        }
        if (Log.loggingDebug)
            log.debug("generatePlayerLoot PLAYER_CORPSE_DROPS_EQUIPMENT="+PLAYER_CORPSE_DROPS_EQUIPMENT+" PLAYER_CORPSE_MOB_TEMPLATE="+PLAYER_CORPSE_MOB_TEMPLATE+" PLAYER_CORPSE_LOOT_DURATION="+PLAYER_CORPSE_LOOT_DURATION+" PLAYER_CORPSE_SAFE_LOOT_DURATION="+PLAYER_CORPSE_SAFE_LOOT_DURATION);
        if (PLAYER_CORPSE_DROPS_EQUIPMENT) {
            // Also remove all equipped gear
            Bag subBag = getBag(iInfo.getEquipmentItemBag());
            EquipMap equipMap = getEquipMap(playerOid);
            if (subBag != null) {
                for (OID itemOid : subBag.getItemsList()) {
                    if (itemOid != null) {
                        AgisItem item = getAgisItem(itemOid);
                        // Some items may be set not to drop (or have a lower chance)
                        int deathLossChance = 100;
                        if (item.getProperty("deathLossChance") != null) {
                            deathLossChance = (Integer)item.getProperty("deathLossChance");
                        }
                        if (rand.nextInt(100) < deathLossChance) {
                            AgisEquipSlot slot = equipMap.getSlot(item.getMasterOid());
                            removeEquippedItem(playerOid, itemOid, slot, -1);
                            equipMap.remove(slot);
                            HashMap<Integer, Integer> list = new HashMap<Integer, Integer>();
                            list = getEquipSetsInfo(playerOid);
                            AgisInventoryClient.itemEquipStatusChanged(playerOid, null, item, slot.toString(), list);
                            EquipHelper.updateDisplay(playerOid, null, slot);
                            // Also mark item as unacquired
                            item.unacquired(playerOid);
                            loot.add(itemOid);
                        }
                    }
                }
            }
        }
        if (loot == null || loot.size() == 0) {
        log.debug("generatePlayerLoot loot null or size =0");
            return;
        }
        BehaviorTemplate bTmpl = new BehaviorTemplate();
        bTmpl.setIsPlayerCorpse(true);
        bTmpl.setOtherUse(playerOid.toString());
        SpawnData sd = new SpawnData();
        sd.setProperty("id", (int)playerOid.toLong());
        sd.setName(playerOid.toString());
        sd.setTemplateID(PLAYER_CORPSE_MOB_TEMPLATE);
        WorldManagerClient.ObjectInfo objInfo = WorldManagerClient.getObjectInfo(playerOid);
        sd.setInstanceOid(objInfo.instanceOid);
        sd.setLoc(loc);
        sd.setOrientation(objInfo.orient);
        sd.setNumSpawns(1);
        sd.setSpawnRadius(0);
        sd.setRespawnTime(-1);
        sd.setRespawnTimeMax(-1);
        sd.setCorpseDespawnTime(0);
        sd.setProperty(AgisMobPlugin.BEHAVIOR_TMPL_PROP, bTmpl);
        sd.setProperty("loot", loot);
        sd.setProperty("corpseDuration", PLAYER_CORPSE_LOOT_DURATION);
        sd.setProperty("safeDuration", PLAYER_CORPSE_SAFE_LOOT_DURATION);
        log.debug("generatePlayerLoot send spawn");
        AgisMobClient.spawnMob(sd);
        log.debug("generatePlayerLoot update inv");
            sendInvUpdate(playerOid);
        log.debug("generatePlayerLoot END");
    }
    
    
    
    class GenerateLootFromLootTableHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            PropertyMessage lootMsg = (PropertyMessage) msg;
            OID playerOid = lootMsg.getSubject();
            if (Log.loggingDebug)
                log.debug("GenerateBuildingLootHook: playerOid=" + playerOid);
            Point loc = (Point) lootMsg.getProperty("loc");
            Quaternion orient = (Quaternion) lootMsg.getProperty("orient");
            OID instanceOid = (OID) lootMsg.getProperty("instanceOid");
            int lootTable = (int) lootMsg.getProperty("lootTable");
            HashMap<Integer, Integer> items = (HashMap<Integer, Integer>) lootMsg.getProperty("items");
            if (Log.loggingDebug)
                log.debug("GenerateBuildingLootHook: playerOid=" + playerOid + " loc=" + loc + " orient=" + orient + " instance=" + instanceOid + " lootTable=" + lootTable + " items=" + items);
            generateLoot(playerOid, loc, orient, instanceOid, lootTable, items);
            log.debug("GenerateBuildingLootHook: End");
            return true;
        }
    }

    void generateLoot(OID playerOid, Point loc, Quaternion orient, OID instanceOid, int lootTable, HashMap<Integer, Integer> items) {
        if (Log.loggingDebug)
            log.debug("generateLoot: Generating Loot for player: " + playerOid);
        if (BUILDING_CORPSE_LOOT_DURATION < 1) {
            log.debug("generateLoot BUILDING_CORPSE_LOOT_DURATION <1 return");
            return;
        }
        if (Log.loggingDebug)
            log.debug("generateLoot: playerOid="+playerOid+" loc="+loc+" orient="+orient+" instanceOid="+instanceOid+" lootTable="+lootTable+" items="+items);
        
        HashMap<Integer, Integer> itemsToAdd = new HashMap<Integer, Integer>();
        itemsToAdd.putAll(items);
        LinkedList<OID> itemsToLoot = new LinkedList<OID>();
        HashMap<Integer, Integer> currencyToAdd = new HashMap<Integer, Integer>();

        if (Log.loggingDebug)
            log.debug("generateLoot: lootManager has: " + Agis.LootTableManager.getMap()+" lootTable="+lootTable);
        if (lootTable > 0) {
            LootTable lt = Agis.LootTableManager.get(lootTable);
            int itemNum = lt.getNewRandomItemNum();
            if (Log.loggingDebug)
                log.debug("generateLoot: randomised item number: " + itemNum);
            if (itemNum >= 0) {
                int templateID = lt.getItems().get(itemNum);
                if (templateID > -1) {
                    int count = lt.getRandomCountOfItem(itemNum);
                    if (itemsToAdd.containsKey(templateID)) {
                        itemsToAdd.put(templateID, itemsToAdd.get(templateID) + count);
                    } else {
                        itemsToAdd.put(templateID, count);
                    }

                }
            } else {
                if (Log.loggingDebug)
                    log.debug("generateLoot: roll get no item for loot Table " + lt.getID());
            }
        }
        if(!INVENTORY_LOOT_ON_GROUND) {
            for (int templateID : itemsToAdd.keySet()) {
                Template tmpl = ObjectManagerPlugin.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
                int stackLimit = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "stackLimit");
                ArrayList<AcquireHook> acquireHook = (ArrayList<AcquireHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_ACQUIRE_HOOK);
                boolean curr = false;
                if (acquireHook != null) {
                    for (AcquireHook ah : acquireHook) {
                        if (ah.getClass().equals(CurrencyItemAcquireHook.class)) {
                            CurrencyItemAcquireHook ciah = (CurrencyItemAcquireHook) ah;
                            int currencyId = ciah.getCurrencyID();
                            curr = true;
                            if (currencyToAdd.containsKey(currencyId))
                                currencyToAdd.replace(currencyId, itemsToAdd.get(templateID) + currencyToAdd.get(currencyId));
                            else
                                currencyToAdd.put(currencyId, itemsToAdd.get(templateID));
                        }
                    }
                }

                if (!curr) {
                    if (stackLimit > itemsToAdd.get(templateID)) {
                        OID itemOid = generateItem(templateID, tmpl.getName());
                        AgisItem item = getAgisItem(itemOid);
                        item.setStackSize(itemsToAdd.get(templateID));
                        itemsToLoot.add(itemOid);

                    } else {
                        int countUsed = 0;
                        int counttogenerate = itemsToAdd.get(templateID) / stackLimit;
                        if (itemsToAdd.get(templateID) % stackLimit > 0)
                            counttogenerate++;
                        for (int i = 0; i < counttogenerate; i++) {
                            if (Log.loggingDebug)
                                log.debug("generateLoot: Generating Loot: i=" + i + " count=" + itemsToAdd.get(templateID) + " stackLimit=" + stackLimit + " counttogenerate=" + counttogenerate + " countUsed=" + countUsed);

                            OID itemOid = generateItem(templateID, tmpl.getName());
                            AgisItem item = getAgisItem(itemOid);
                            if (itemsToAdd.get(templateID) - countUsed < stackLimit) {
                                item.setStackSize(itemsToAdd.get(templateID) - countUsed);
                            } else {
                                item.setStackSize(stackLimit);
                                countUsed += stackLimit;
                            }
                            itemsToLoot.add(itemOid);
                        }
                    }
                }
            }
        } else {
            for (int templateID : itemsToAdd.keySet()) {
                Template tmpl = ObjectManagerPlugin.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
                int stackLimit = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "stackLimit");
                if (stackLimit > itemsToAdd.get(templateID)) {
                    if (Log.loggingDebug)
                        log.debug("LOOT: adding item: " + tmpl.getName() + " count " + itemsToAdd.get(templateID));
                    ItemOnGroundData item = new ItemOnGroundData(null, templateID, itemsToAdd.get(templateID), new AOVector(loc) );
                    itemsOnGround.computeIfAbsent(instanceOid, __ -> new ConcurrentHashMap<>()).
                            computeIfAbsent(playerOid,__ -> new ConcurrentHashMap<>()).put(item.getOid(),item);
                } else {
                    int countUsed = 0;
                    int counttogenerate = itemsToAdd.get(templateID) / stackLimit;
                    if (itemsToAdd.get(templateID) % stackLimit > 0)
                        counttogenerate++;
                    for (int i = 0; i < counttogenerate; i++) {
                        if (Log.loggingDebug)
                            log.debug("LOOT: Generating Loot: i=" + i + " count=" + itemsToAdd.get(templateID) + " stackLimit=" + stackLimit + " counttogenerate=" + counttogenerate + " countUsed=" + countUsed);
                        if (itemsToAdd.get(templateID) - countUsed < stackLimit) {
                            ItemOnGroundData item = new ItemOnGroundData(null, templateID, itemsToAdd.get(templateID) - countUsed, new AOVector(loc));
                            itemsOnGround.computeIfAbsent(instanceOid, __ -> new ConcurrentHashMap<>()).
                                    computeIfAbsent(playerOid,__ -> new ConcurrentHashMap<>()).put(item.getOid(),item);

                        } else {
                            ItemOnGroundData item = new ItemOnGroundData(null, templateID, stackLimit, new AOVector(loc));
                            itemsOnGround.computeIfAbsent(instanceOid, __ -> new ConcurrentHashMap<>()).
                                    computeIfAbsent(playerOid,__ -> new ConcurrentHashMap<>()).put(item.getOid(),item);
                            countUsed += stackLimit;

                        }
                    }
                }
            }
        }
        if (Log.loggingDebug)
            log.debug("generateLoot: currencyToAdd=" + currencyToAdd);
        LinkedList<String> currencyToLoot = new LinkedList<String>();
        for (int i : currencyToAdd.keySet()) {
            currencyToLoot.add(i + ";" + currencyToAdd.get(i));
        }
        if (Log.loggingDebug)
            log.debug("generateLoot: now loot: " + itemsToLoot + " currencyToAdd=" + currencyToAdd);

        if (Log.loggingDebug)
            log.debug("generateLoot BUILDING_CORPSE_MOB_TEMPLATE=" + BUILDING_CORPSE_MOB_TEMPLATE + " BUILDING_CORPSE_LOOT_DURATION=" + BUILDING_CORPSE_LOOT_DURATION);

        if (currencyToAdd.size() == 0 && itemsToLoot.size() == 0) {
            log.debug("generateLoot loot null or size =0");
            return;
        }
        if(!INVENTORY_LOOT_ON_GROUND) {
            Random random = new Random();
            BehaviorTemplate bTmpl = new BehaviorTemplate();
            bTmpl.setIsPlayerCorpse(true);
            bTmpl.setOtherUse(playerOid.toString());
            SpawnData sd = new SpawnData();
            sd.setProperty("id", (int) playerOid.toLong() + random.nextInt(100000));
            sd.setName(playerOid.toString() + "buildLoot" + random.nextInt(100000));
            sd.setTemplateID(BUILDING_CORPSE_MOB_TEMPLATE);
            sd.setInstanceOid(instanceOid);
            sd.setLoc(loc);
            sd.setOrientation(orient);
            sd.setNumSpawns(1);
            sd.setSpawnRadius(0);
            sd.setRespawnTime(-1);
            sd.setRespawnTimeMax(-1);
            sd.setCorpseDespawnTime(0);
            sd.setProperty(AgisMobPlugin.BEHAVIOR_TMPL_PROP, bTmpl);
            sd.setProperty("loot", itemsToLoot);
            sd.setProperty("loot_curr", currencyToLoot);
            sd.setProperty("corpseDuration", BUILDING_CORPSE_LOOT_DURATION);
            sd.setProperty("safeDuration", 0);
            log.debug("generateLoot send spawn");
            AgisMobClient.spawnMob(sd);
        } else{
            sendLootGroundUpdate(playerOid, instanceOid);
        }

        log.debug("generateLoot END");

    }

    /**
     * Handles the GetLootListMessage. Calls the sendLootList function and 
     * sends down a CoordinatedEffect to play a looting animation.
     */
    class GetLootListHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.getLootListMessage getMsg = (AgisInventoryClient.getLootListMessage)msg;
            OID oid = getMsg.getSubject();
            OID mobOid = (OID) getMsg.getProperty("mobOid");
            
            // Death check
            boolean dead = (Boolean) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
            if (dead) {
                ExtendedCombatMessages.sendErrorMessage(oid, "You cannot peform that action when dead");
                return true;
            }
            
            sendLootList(oid, mobOid);
            // TODO: Replace this Coord Effect Code with just a name.
            CoordinatedEffect cE = new CoordinatedEffect("LootEffect");
            cE.sendSourceOid(true);
            cE.sendTargetOid(true);
            cE.invoke(oid, oid);
            return true;
        }
    }
    
    /**
     * Sends down the list of loot from the specified mob to the requesting player.
     * @param playerOid
     * @param mobOid
     */
    public void sendLootList(OID playerOid, OID mobOid) {
        InventoryInfo iInfo = getInventoryInfo(mobOid);
        if (Log.loggingDebug)
            log.debug("sendLootList: "+iInfo);
        if(iInfo==null) {
            log.debug("sendLootList: playerOid="+playerOid+" mobOid="+mobOid+" inventory for mob is null break !!!");
            return;
        }
            
        //ArrayList<OID> targets = (ArrayList<OID>) iInfo.getProperty("loottargets");
        /*if(targets!=null)
        if(!targets.contains(playerOid)) {
            log.error("sendLootList player="+playerOid+" is not in list of targetlotts "+targets+" Break");
            return;
        }*/
        HashMap<OID, LinkedList<OID>>  itemsLootForPlayer = (HashMap<OID, LinkedList<OID>>) iInfo.getProperty("itemsLootForPlayer");
        LinkedList<OID> itemOids = (LinkedList) getLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot");
    
        HashMap<Integer, Integer> lootCurr = new HashMap<Integer, Integer>();
        LinkedList<String> currLoot = (LinkedList<String>) getLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot_curr");
        if (currLoot != null) {
            for (String s : currLoot) {
                if (!s.isEmpty() && s.contains(";")) {
                    String[] sa = s.split(";");
                    lootCurr.put(Integer.parseInt(sa[0]), Integer.parseInt(sa[1]));
                }
            }
        }
        if (Log.loggingDebug)
            log.debug("sendLootList looterOid="+playerOid+" get lootCurr="+lootCurr);
        HashMap<OID, HashMap<Integer, Integer>> currencyLootForPlayer = ( HashMap<OID,HashMap<Integer, Integer>>)iInfo.getProperty("currLootForPlayer");
        if(currencyLootForPlayer!=null) {
            if (Log.loggingDebug)
                log.debug("sendLootList looterOid="+playerOid+" get from currencyLootForPlayer");
            if(currencyLootForPlayer.containsKey(playerOid))
                lootCurr = currencyLootForPlayer.get(playerOid);
        }
        if (Log.loggingDebug)
            log.debug("sendLootList looterOid="+playerOid+" get lootCurr="+lootCurr);
        
        HashMap<OID, LinkedList<OID>> itemsLootedByPlayer = (HashMap) iInfo.getProperty("itemsLootedByPlayer");
        //HashMap<OID, LinkedList<OID>> itemsLootedByPlayer = (HashMap) EnginePlugin.getObjectProperty(mobOid, InventoryClient.NAMESPACE, "itemsLootedByPlayer");
        if (Log.loggingDebug)
            log.debug("sendLootList loot="+itemOids+" itemsLootedByPlayer="+itemsLootedByPlayer+" itemsLootForPlayer="+itemsLootForPlayer);
           
        //if(itemOids==null ||(itemOids != null && itemOids.size()==0)){
            if(itemsLootForPlayer != null) {
                if(itemsLootForPlayer.containsKey(playerOid)) {
                    log.debug("sendLootList get items from itemsLootForPlayer");
                    itemOids = itemsLootForPlayer.get(playerOid);
                }else {
                    log.debug("sendLootList player is not at list to get from itemsLootForPlayer maybe is looted");
                }
            }
        //}
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "LootList");
        props.put("lootTarget", mobOid);
        props.put("Type", "Mob");
        if (Log.loggingDebug)
            log.debug("sendLootList LOOT: list to send down to client: " + itemOids);
        int numItems = 0;
        if (itemOids == null) {
            log.debug("sendLootList list is null end");
            props.put("numItems", numItems);
            TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
            Engine.getAgent().sendBroadcast(TEmsg);
            return;
        }
        
        List<Integer> questItemReqs = QuestClient.getQuestItemReqs(playerOid);
        for (int itemPos = 0; itemPos < itemOids.size(); itemPos++) {
            boolean itemExists = true;
            // get the item
            OID itemOid = itemOids.get(itemPos);
            if (itemOid == null) {
                itemExists = false;
            }
            AgisItem item = getAgisItem(itemOid);
            if (item == null) {
                Log.warn("sendLootList sendInvUpdateHelper: item is null, oid=" + itemOid);
                itemExists = false;
            }
            // Check to see if the player needs an item that is a quest item
            boolean itemNeeded = true;
            if(item.getItemType().equals("Quest")){
                itemNeeded = false;
                if (Log.loggingDebug)
                    log.debug("sendLootList QUEST: found quest item " + item.getName() + ". does the player need it?");
                for (int i = 0; i < questItemReqs.size(); i++) {
                    if (Log.loggingDebug)
                        log.debug("sendLootList QUEST: checking questItem req: " + questItemReqs.get(i) + " against " + item.getTemplateID());
                    if (questItemReqs.get(i) == item.getTemplateID() && !itemsLootedByPlayer.get(itemOid).contains(playerOid)) {
                        // If the quest item is not to be given to all players and it has been looted - don;t add it
                        if (GIVE_QUEST_ITEMS_TO_ALL_IN_GROUP || itemsLootedByPlayer.get(itemOid).size() == 0) {
                        itemNeeded = true;
                        Log.debug("sendLootList QUEST: found quest item - it was needed");
                        }
                    }
                }
                
            }
           
            
            if (itemExists && itemOids.contains(item.getOid()) && itemNeeded) {
                if (Log.loggingDebug)
                    log.debug("sendLootList sendInvUpdateHelper: adding bagPos=" + itemPos +
                              ", itemOid=" + playerOid + ", itemName=" + item.getName() + ",icon=" + item.getIcon());
                props.put("item_" + numItems + "Name", item.getName());
                props.put("item_" + numItems + "BaseName", item.getProperty("baseName"));
                //props.put("item_" + numItems + "Description", description);
                //props.put("item_" + numItems + "Icon", item.getIcon());
                props.put("item_" + numItems + "Id", item.getOid());
                props.put("item_" + numItems + "Count", item.getStackSize());
                props.put("item_" + numItems + "SlotNum", itemPos);
                if (item.getProperty("energyCost") != null) {
                    props.put("item_" + numItems + "EnergyCost", item.getProperty("energyCost"));
                } else {
                    props.put("item_" + numItems + "EnergyCost", 0);
                }
                if (item.getProperty("resistanceStats") != null) {
                    int numResist = 0;
                    HashMap<String, Integer> resistances = (HashMap) item.getProperty("resistanceStats");
                    for (String resistance: resistances.keySet()) {
                        props.put("item_" + numItems + "Resist_" + numResist + "Name", resistance);
                        props.put("item_" + numItems + "Resist_" + numResist + "Value", resistances.get(resistance));
                        numResist++;
                    }
                    props.put("item_" + numItems + "NumResistances", numResist);
                } else {
                    props.put("item_" + numItems + "NumResistances", 0);
                }
                if (item.getProperty("bonusStats") != null) {
                    int numStats = 0;
                    HashMap<String, Integer> stats = (HashMap) item.getProperty("bonusStats");
                    for (String statName: stats.keySet()) {
                        props.put("item_" + numItems + "Stat_" + numStats + "Name", statName);
                        props.put("item_" + numItems + "Stat_" + numStats + "Value", stats.get(statName));
                        numStats++;
                    }
                    props.put("item_" + numItems + "NumStats", numStats);
                } else {
                    props.put("item_" + numItems + "NumStats", 0);
                }
                // If it is a weapon, add damage/speed stats
                if (item.getItemType().equals("Weapon")) {
                    props.put("item_" + numItems + "Speed", item.getProperty("speed"));
                    props.put("item_" + numItems + "DamageType", item.getProperty("damageType"));
                    props.put("item_" + numItems + "DamageValue", item.getProperty("damage"));
                    props.put("item_" + numItems + "DamageValueMax", item.getProperty("damageMax"));
                      }
                numItems++;
            }
        }
        int numCurr=0;
        if (lootCurr != null) {
            
            for (Integer id : lootCurr.keySet()) {
                props.put("curr_" + numCurr + "Id", id);
                props.put("curr_" + numCurr + "Count", lootCurr.get(id));
                numCurr++;
            }
            
        }
     
        if (numItems == 0 && numCurr == 0) {

            if (Log.loggingDebug)
                log.debug("sendLootList sendInvUpdateHelper playerOid="+playerOid+" "+itemsLootForPlayer);
            if(itemsLootForPlayer ==null  ||(itemsLootForPlayer !=null && itemsLootForPlayer.size() == 0)) {
                PropertyMessage propMsg2 = new PropertyMessage(mobOid, mobOid);
                propMsg2.setProperty("lootable", false);
                Engine.getAgent().sendBroadcast(propMsg2);
                Log.debug("sendLootList LOOT: sending lootable = false");
            }else /*if(!itemsLootForPlayer.containsKey(playerOid))*/ {
                TargetedPropertyMessage propMsg = new TargetedPropertyMessage(playerOid, mobOid);
                propMsg.setProperty("lootable", false);
                Engine.getAgent().sendBroadcast(propMsg);
            }
              //WorldManagerClient.sendObjChatMsg(oid, 2, "There is nothing to loot");
        }
        
        props.put("numItems", numItems);
        props.put("numCurr", numCurr);
        if (Log.loggingDebug)
            log.debug("sendLootList props="+props);
        TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
        Engine.getAgent().sendBroadcast(TEmsg);
        log.debug("sendLootList END");
        
      }
    
    /**
     * Sends down the picked item as loot to the requesting player.
     * @param playerOid
     * @param itemOid
     * @param bagOid
     * @param count
     */
    public void sendLockpickLootList(OID playerOid, OID itemOid, OID bagOid, int count) {
        
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "LootList");
        props.put("lootTarget", itemOid);
        props.put("Type", "Lockpick");
        props.put("BagOid", bagOid);
        if (Log.loggingDebug)
            log.debug("LOCKPICK: item to send down to client: " + itemOid);
        int numItems = 0;
        if(itemOid == null) {
            props.put("numItems", numItems);
            TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(
                    WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
                    playerOid, false, props);
            Engine.getAgent().sendBroadcast(TEmsg);
            return;
        }
        
        for (int itemPos = 0; itemPos < 1; itemPos++) {
            boolean itemExists = true;
            // get the item
            if (itemOid == null) {
                Log.debug("LOCKPICK: item is null, oid=" + itemOid);
                itemExists = false;
            }
            AgisItem item = getAgisItem(itemOid);
            if (item == null) {
                Log.warn("LOCKPICK: item is null, oid=" + itemOid);
                itemExists = false;
            }

            if (Log.loggingDebug)
                log.debug("Item exists = " + itemExists);
            if (itemExists) {

                if (Log.loggingDebug)
                    log.debug("LOCKPICK: adding bagPos=" + itemPos +
                         ", itemOid=" + playerOid + ", itemName=" + item.getName() + ",icon=" + item.getIcon());
                props.put("item_" + numItems + "Name", item.getName());
                props.put("item_" + numItems + "BaseName", item.getProperty("baseName"));
                //props.put("item_" + numItems + "Description", description);
                //props.put("item_" + numItems + "Icon", item.getIcon());
                props.put("item_" + numItems + "Id", item.getOid());
                props.put("item_" + numItems + "Count", item.getStackSize());
                props.put("item_" + numItems + "SlotNum", itemPos);
                if (item.getProperty("energyCost") != null) {
                    props.put("item_" + numItems + "EnergyCost", item.getProperty("energyCost"));
                } else {
                    props.put("item_" + numItems + "EnergyCost", 0);
                }
                if (item.getProperty("resistanceStats") != null) {
                    int numResist = 0;
                    HashMap<String, Integer> resistances = (HashMap) item.getProperty("resistanceStats");
                    for (String resistance: resistances.keySet()) {
                        props.put("item_" + numItems + "Resist_" + numResist + "Name", resistance);
                        props.put("item_" + numItems + "Resist_" + numResist + "Value", resistances.get(resistance));
                        numResist++;
                    }
                    props.put("item_" + numItems + "NumResistances", numResist);
                } else {
                    props.put("item_" + numItems + "NumResistances", 0);
                }
                if (item.getProperty("bonusStats") != null) {
                    int numStats = 0;
                    HashMap<String, Integer> stats = (HashMap) item.getProperty("bonusStats");
                    for (String statName: stats.keySet()) {
                        props.put("item_" + numItems + "Stat_" + numStats + "Name", statName);
                        props.put("item_" + numItems + "Stat_" + numStats + "Value", stats.get(statName));
                        numStats++;
                    }
                    props.put("item_" + numItems + "NumStats", numStats);
                } else {
                    props.put("item_" + numItems + "NumStats", 0);
                }
                // If it is a weapon, add damage/speed stats
                if (item.getItemType().equals("Weapon")) {
                    props.put("item_" + numItems + "Speed", item.getProperty("speed"));
                    props.put("item_" + numItems + "DamageType", item.getProperty("damageType"));
                    props.put("item_" + numItems + "DamageValue", item.getProperty("damage"));
                    props.put("item_" + numItems + "DamageValueMax", item.getProperty("damageMax"));
                     }
                log.debug("LOCKPICK: Item is ready for sending!");
                numItems++;
            }
        }
        log.debug("LOCKPICK: Sent message");
        props.put("numItems", numItems);
        TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
        Engine.getAgent().sendBroadcast(TEmsg);
    }
    
    void setMobSkinnable(OID mobOid) {
        if (Log.loggingDebug)
            log.debug("SKINNING: setting mob as skinnable: " + mobOid);
        Integer skinningLootTable = (Integer) EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, "skinningLootTable");
        if (skinningLootTable != null && skinningLootTable > 0) {
            Integer skinningSkill = (Integer) EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, "skinningSkillId");
            Integer skinningLevelReq = (Integer) EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, "skinningLevelReq");
            Integer skinningLevelMax = (Integer) EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, "skinningLevelMax");
            Integer skinningSkillExp = (Integer) EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, "skinningSkillExp");
            String skinningWeaponReq = (String) EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, "skinningWeaponReq");
            float skinningHarvestTime = (float) EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, "skinningHarvestTime");
                
            CraftingClient.sendCreateResourceNodeFromMobMessage(mobOid, skinningLootTable, skinningLevelReq,skinningLevelMax,skinningSkillExp,skinningSkill, skinningWeaponReq,skinningHarvestTime);
        }
    }
    
    class MultiLootAllHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
             ExtensionMessage lootMsg = (ExtensionMessage)msg;
             OID looterOid = lootMsg.getSubject();
            if (Log.loggingDebug)
                log.debug("MultiLootAllHook: START looterOid="+looterOid);
                
             boolean dead = (Boolean) EnginePlugin.getObjectProperty(looterOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
                if (dead) {
                    if (Log.loggingDebug)
                        log.debug("MultiLootAllHook: looterOid="+looterOid+" is dead");
                    ExtendedCombatMessages.sendErrorMessage(looterOid, "You cannot peform that action when dead");
                    return true;
                }
             int count = (int)lootMsg.getProperty("num");
        //   ArrayList<OID> list = new ArrayList<OID>(); 
             for(int i = 0 ; i < count; i++) {
                 long loid = (long)lootMsg.getProperty("o"+i);
                // list.add(OID.fromLong(loid));
                 if (Log.loggingDebug)
                     log.debug("MultiLootAllHook: END looterOid="+looterOid+" "+OID.fromLong(loid));
                 LootAll(looterOid, OID.fromLong(loid));
             }
            if (Log.loggingDebug)
                log.debug("MultiLootAllHook: END looterOid="+looterOid);
            
            
            return true;
        }
    }

    class GenerateGroundLootHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.GenerateGroundLootMessage lootMsg = (AgisInventoryClient.GenerateGroundLootMessage)msg;

            OID playerOid = lootMsg.getSubject();
            if (Log.loggingDebug)
                log.debug("GenerateGroundLootHook: START playerOid="+playerOid);
            HashMap<Integer,Integer> itemsToAdd = lootMsg.getItemsToGenerate();
            boolean dead = (Boolean) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
            if (dead) {
                if (Log.loggingDebug)
                    log.debug("GenerateGroundLootHook: looterOid="+playerOid+" is dead");
                ExtendedCombatMessages.sendErrorMessage(playerOid, "You cannot peform that action when dead");
                return true;
            }
            BasicWorldNode wnode = null;
            wnode = WorldManagerClient.getWorldNode(playerOid);

            List<Integer> questItemReqs = QuestClient.getQuestItemReqs(playerOid);
            for (int templateID : itemsToAdd.keySet()) {
                Template tmpl = ObjectManagerPlugin.getTemplate(templateID, ObjectManagerPlugin.ITEM_TEMPLATE);
                String itemType = (String) tmpl.get(InventoryClient.ITEM_NAMESPACE, "itemType");
                if (itemType.equals("Quest") && !questItemReqs.contains(templateID)) {
                    if(Log.loggingDebug)Log.debug("playerOid: " + playerOid + " skip item templateID="+templateID+" not have Quest");
                } else {
                    int stackLimit = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "stackLimit");

                    if (stackLimit > itemsToAdd.get(templateID)) {
                        if (Log.loggingDebug)
                            log.debug("GenerateGroundLootHook: adding item: " + tmpl.getName() + " count " + itemsToAdd.get(templateID));
                        ItemOnGroundData item = new ItemOnGroundData(null, templateID, itemsToAdd.get(templateID), new AOVector(wnode.getLoc()));
                        itemsOnGround.computeIfAbsent(wnode.getInstanceOid(), __ -> new ConcurrentHashMap<>()).
                                computeIfAbsent(playerOid, __ -> new ConcurrentHashMap<>()).put(item.getOid(), item);
                    } else {
                        int countUsed = 0;
                        int counttogenerate = itemsToAdd.get(templateID) / stackLimit;
                        if (itemsToAdd.get(templateID) % stackLimit > 0)
                            counttogenerate++;
                        for (int i = 0; i < counttogenerate; i++) {
                            if (Log.loggingDebug)
                                log.debug("GenerateGroundLootHook: Generating Loot: i=" + i + " count=" + itemsToAdd.get(templateID) + " stackLimit=" + stackLimit + " countToGenerate=" + counttogenerate + " countUsed=" + countUsed);
                            if (itemsToAdd.get(templateID) - countUsed < stackLimit) {
                                ItemOnGroundData item = new ItemOnGroundData(null, templateID, itemsToAdd.get(templateID) - countUsed, new AOVector(wnode.getLoc()));
                                itemsOnGround.computeIfAbsent(wnode.getInstanceOid(), __ -> new ConcurrentHashMap<>()).
                                        computeIfAbsent(playerOid, __ -> new ConcurrentHashMap<>()).put(item.getOid(), item);

                            } else {
                                ItemOnGroundData item = new ItemOnGroundData(null, templateID, stackLimit, new AOVector(wnode.getLoc()));
                                itemsOnGround.computeIfAbsent(wnode.getInstanceOid(), __ -> new ConcurrentHashMap<>()).
                                        computeIfAbsent(playerOid, __ -> new ConcurrentHashMap<>()).put(item.getOid(), item);
                                countUsed += stackLimit;

                            }
                        }
                    }
                }
            }

            sendLootGroundUpdate(playerOid, wnode.getInstanceOid());
            if (Log.loggingDebug)
                log.debug("GenerateGroundLootHook: END playerOid="+playerOid);

            return true;
        }
    }

    class DropGroundItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage lootMsg = (ExtensionMessage)msg;
            OID playerOid = lootMsg.getSubject();
            if (Log.loggingDebug)
                log.debug("DropGroundItemHook: START playerOid="+playerOid);

            boolean dead = (Boolean) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
            if (dead) {
                if (Log.loggingDebug)
                    log.debug("DropGroundItemHook: playerOid="+playerOid+" is dead");
                ExtendedCombatMessages.sendErrorMessage(playerOid, "You cannot perform that action when dead");
                return true;
            }
            BasicWorldNode wnode = null;
            wnode = WorldManagerClient.getWorldNode(playerOid);
            OID instanceOid = wnode.getInstanceOid();
            long itemid = (long)lootMsg.getProperty("item");
            OID itemOid = OID.fromLong(itemid);
            ArrayList<OID> players = new ArrayList<>();
            lock.lock();
            try {
                AgisItem item = getAgisItem(itemOid);
                boolean rv = removeItemFromBag(playerOid, itemOid);
                if (Log.loggingDebug)
                    log.debug("DropGroundItemHook: removed oid=" + itemOid + ", rv=" + rv);
                if (!rv) {
                    return true;
                }
                item.unacquired(playerOid);
                if (item.isPlayerBound()) {
                    ItemOnGroundData iogd = new ItemOnGroundData(itemOid, item.getTemplateID(), item.getStackSize(), new AOVector(wnode.getLoc()));
                    itemsOnGround.computeIfAbsent(instanceOid, __ -> new ConcurrentHashMap<>()).computeIfAbsent(playerOid, __ -> new ConcurrentHashMap<>()).put(iogd.getOid(), iogd);
                    players.add(playerOid);
                } else {
                    ItemOnGroundData iogd = new ItemOnGroundData(itemOid, item.getTemplateID(), item.getStackSize(), new AOVector(wnode.getLoc()));
                    playerInInstance.computeIfAbsent(wnode.getInstanceOid(), __ -> new HashSet<OID>()).forEach((v) -> {
                        itemsOnGround.computeIfAbsent(instanceOid, __ -> new ConcurrentHashMap<>()).computeIfAbsent(v, __ -> new ConcurrentHashMap<>()).put(iogd.getOid(), iogd);
                        players.add(v);
                    });
                }
            }finally {
                lock.unlock();
            }
            sendBagInvUpdate(playerOid);
            //SendUpdate to Players
            for (OID player: players) {
                try {
                    sendLootGroundUpdate(player, instanceOid);
                }catch (Exception e){
                    Log.exception("DropGroundItemHook player "+player+" ",e);
                }
            }
            log.debug("DropGroundItemHook: END ");
            return true;
        }
    }


    class LootGroundItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage lootMsg = (ExtensionMessage) msg;
            OID looterOid = lootMsg.getSubject();
            if (Log.loggingDebug)
                log.debug("LootGroundItemHook: START looterOid=" + looterOid);

            boolean dead = (Boolean) EnginePlugin.getObjectProperty(looterOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
            if (dead) {
                if (Log.loggingDebug)
                    log.debug("LootGroundItemHook: looterOid=" + looterOid + " is dead");
                ExtendedCombatMessages.sendErrorMessage(looterOid, "You cannot perform that action when dead");
                return true;
            }
            BasicWorldNode wnode = null;
            wnode = WorldManagerClient.getWorldNode(looterOid);
            OID instanceOid = wnode.getInstanceOid();
            int count = (int) lootMsg.getProperty("num");
            ConcurrentHashMap<OID, ItemOnGroundData> loots = itemsOnGround.computeIfAbsent(instanceOid, __ -> new ConcurrentHashMap<>()).get(looterOid);
            ArrayList<OID> toDelete = new ArrayList<>();
            ArrayList<OID> toDeleteFromAll = new ArrayList<>();
            List<Integer> questItemReqs = QuestClient.getQuestItemReqs(looterOid);
            for (int i = 0; i < count; i++) {
                long loid = (long) lootMsg.getProperty("o" + i);

                if (loots.containsKey(OID.fromLong(loid))) {
                    ItemOnGroundData iogd = loots.get(OID.fromLong(loid));
                    if (AOVector.distanceTo(wnode.getLoc(), iogd.getSpawnLoc()) < INVENTORY_LOOT_ON_GROUND_MAX_DISTANCE) {
                        Template tmpl = ObjectManagerPlugin.getTemplate(iogd.getTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
                        if (Log.loggingDebug)
                            log.debug("LootGroundItemHook " + iogd);
                        if (iogd.getItemOID() == null) {
                            int stackLimit = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "stackLimit");
                            ArrayList<AcquireHook> acquireHook = (ArrayList<AcquireHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_ACQUIRE_HOOK);
                            boolean curr = false;
                            if (acquireHook != null) {
                                for (AcquireHook ah : acquireHook) {
                                    if (ah.getClass().equals(CurrencyItemAcquireHook.class)) {
                                        CurrencyItemAcquireHook ciah = (CurrencyItemAcquireHook) ah;
                                        int currencyId = ciah.getCurrencyID();
                                        curr = true;
                                        alterMobCurrency(looterOid, currencyId, iogd.getStack());
                                        alterCurrecyLog(looterOid, null, currencyId, iogd.getStack(), "LootGround");
                                        toDelete.add(OID.fromLong(loid));
                                    }
                                }
                            }
                            boolean hasSpace = hasSpace(looterOid, iogd.getTemplateId(), iogd.getStack(), 0);
                            if (!hasSpace) {
                                continue;
                            }
                            if (!curr) {
                                //        AgisInventoryClient.generateItems(playerOid, itemsToAdd, false);
                                OID itemOid = generateItem(iogd.getTemplateId(), tmpl.getName());
                                AgisItem item = getAgisItem(itemOid);
                                item.setStackSize(iogd.getStack());
                                if (Log.loggingDebug)
                                    log.debug("LOOT: Generating Loot: created item=" + itemOid);
                                boolean itemAdded = InventoryClient.addItem(looterOid, looterOid, looterOid, itemOid);
                                toDelete.add(OID.fromLong(loid));
                            }
                        } else {
                            //
                            AgisItem item = getAgisItem(iogd.getItemOID());

                            boolean hasSpace = hasSpace(looterOid, item.getTemplateID(), item.getStackSize(), 0);
                            if (!hasSpace) {
                                continue;
                            }

                            boolean itemNeeded = true;
                            if (item.getItemType().equals("Quest")) {
                                itemNeeded = false;
                                if (Log.loggingDebug)
                                    log.debug("LootGroundItemHook: QUEST: found quest item " + item.getName() + ". does the player need it?");
                                for (int j = 0; j < questItemReqs.size(); j++) {
                                    if (Log.loggingDebug)
                                        log.debug("LootGroundItemHook: QUEST: checking questItem req: " + questItemReqs.get(j) + " against " + item.getTemplateID());
                                    if (questItemReqs.get(j) == item.getTemplateID()) {
                                        Log.debug("LootGroundItemHook: QUEST: found quest item - it was needed");
                                        itemNeeded = true;
                                    }
                                }
                            }

                            if (!itemNeeded) {
                                continue;
                            }
                            OID accountId = (OID) EnginePlugin.getObjectProperty(looterOid, WorldManagerClient.NAMESPACE, WorldManagerClient.ACCOUNT_PROPERTY);
                            HashMap<String, Serializable> logData = new HashMap<String, Serializable>();
                            logData.put("item", "Item attempt to loot : " + item.getName() + " : OID " + iogd.getItemOID() + " : ");
                            logData.put("playerOid", looterOid);
                            DataLoggerClient.logData("ITEM_LOOTED_EVENT", looterOid, null, accountId, logData);

                            // add item to the looter's root bag
                            boolean rv = addItem(looterOid, looterOid, iogd.getItemOID());
                            if (Log.loggingDebug)
                                log.debug("LootGroundItemHook: LOOT: addItem to looter, oid=" + iogd.getItemOID() + ", rv=" + rv);
                            if (!rv) {
                                DataLoggerClient.logData("ITEM_LOOTED_FAILED_EVENT", looterOid, iogd.getItemOID(), accountId, logData);
                                continue;
                            }
                            AgisInventoryPlugin.addListsRankingData(looterOid, AchievementsClient.LOOT, 1);

                            String lootedItem = item.getName();
                            if (lootedItem != null) {
                                EventMessageHelper.SendInventoryEvent(looterOid, EventMessageHelper.ITEM_LOOTED, item.getTemplateID(), item.getStackSize(), null);
                            }
                            toDeleteFromAll.add(OID.fromLong(loid));
                            sendBagInvUpdate(looterOid);

                        }
                    } else {

                    }
                }

            }
            for (OID item : toDelete) {
                loots.remove(item);
            }

            for (OID item : toDeleteFromAll) {
                itemsOnGround.computeIfAbsent(instanceOid, __ -> new ConcurrentHashMap<>()).forEach((k, v) -> {
                    v.remove(item);
                });
                loots.remove(item);

            }
            if (toDeleteFromAll.size() > 0) {
                playerInInstance.computeIfAbsent(wnode.getInstanceOid(), __ -> new HashSet<OID>()).forEach((v) -> {
                    sendLootGroundUpdate(v, instanceOid);
                });
            } else {
                sendLootGroundUpdate(looterOid, wnode.getInstanceOid());
            }
            if (Log.loggingDebug)
                log.debug("LootGroundItemHook: END looterOid=" + looterOid);


            return true;
        }
    }
    
    /**
     * Handles the LootAllMessage. Takes all lootasble items from the dead mob and places them into the looters bag.
     * @author Andrew
     */
    class LootAllHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.lootAllMessage lootMsg = (AgisInventoryClient.lootAllMessage) msg;
            OID looterOid = lootMsg.getSubject();
            OID mobOid = (OID) lootMsg.getProperty("mobOid");
            if (Log.loggingDebug)
                log.debug("LootAllHook: START looterOid="+looterOid+" mobOid="+mobOid);
            // Death check
            boolean dead = (Boolean) EnginePlugin.getObjectProperty(looterOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
            if (dead) {
                if (Log.loggingDebug)
                    log.debug("LootAllHook: looterOid="+looterOid+" is dead");
                ExtendedCombatMessages.sendErrorMessage(looterOid, "You cannot peform that action when dead");
                return true;
            }
            LootAll(looterOid, mobOid);
            return true;
        }}
    
    boolean LootAll(OID looterOid, OID mobOid ) {
            OID accountId = (OID) EnginePlugin.getObjectProperty(looterOid, WorldManagerClient.NAMESPACE, WorldManagerClient.ACCOUNT_PROPERTY);
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            
            if(iInfo==null) {
                if (Log.loggingDebug)
                    log.debug("LootAllHook: LootAll InventoryInfo is null for "+mobOid+" probably the mob has already been destroyed ");
                return true;
            }

        
            ArrayList<OID> targets = (ArrayList<OID>) iInfo.getProperty("loottargets");
            if (targets != null)
                if (!targets.contains(looterOid)) {
                    log.debug("LootAllHook: sendLootList player=" + looterOid + " is not in list of targetlotts " + targets + " Break");
                    return true;
                }
            if (iInfo.getPropertyMap().containsKey("roll"))
                if ((int) iInfo.getProperty("roll") == 1) {
                    log.debug("LootAllHook: roll = 1 You cannot peform that action when is dice is running");
                    ExtendedCombatMessages.sendErrorMessage(looterOid, "You cannot peform that action when is dice is running");
                    return true;
                }
            
            
            
            LinkedList<OID> loot = null;
            HashMap<OID, LinkedList<OID>> itemsLootedByPlayer = null;
            HashMap<OID, LinkedList<OID>>  itemsLootForPlayer = null;
            try {

                HashMap<Integer, Integer> lootCurr = new HashMap<Integer, Integer>();
                LinkedList<String> currLoot = (LinkedList<String>) getLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot_curr");
                if (currLoot != null) {
                    for (String s : currLoot) {
                        if (!s.isEmpty() && s.contains(";")) {
                            String[] sa = s.split(";");
                            lootCurr.put(Integer.parseInt(sa[0]), Integer.parseInt(sa[1]));
                        }
                    }
                }
                if (Log.loggingDebug)
                    log.debug("LootAllHook looterOid="+looterOid+" get lootCurr="+lootCurr);
                 
                HashMap<OID, HashMap<Integer, Integer>> currencyLootForPlayer = ( HashMap<OID,HashMap<Integer, Integer>>)iInfo.getProperty("currLootForPlayer");
                if(currencyLootForPlayer!=null) {
                    if (Log.loggingDebug)
                        log.debug("LootAllHook looterOid="+looterOid+" get from currencyLootForPlayer");
                    if(currencyLootForPlayer.containsKey(looterOid))
                        lootCurr = currencyLootForPlayer.get(looterOid);
                }
                if (Log.loggingDebug)
                    log.debug("LootAllHook looterOid="+looterOid+" get lootCurr="+lootCurr);
                for (Integer currId : lootCurr.keySet()) {
                    alterMobCurrency(looterOid, currId, lootCurr.get(currId));
                    alterCurrecyLog(looterOid,null, currId, lootCurr.get(currId),"LootAll");
                }
            
                if(currencyLootForPlayer!=null) {
                    if(currencyLootForPlayer.containsKey(looterOid))
                        currencyLootForPlayer.remove(looterOid);
                    iInfo.setProperty("currLootForPlayer", currencyLootForPlayer);
                }else {
                    setLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot_curr",null);
                }
                
                
                loot = (LinkedList) getLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot");
                itemsLootedByPlayer = (HashMap) iInfo.getProperty("itemsLootedByPlayer");
            //   HashMap<OID, LinkedList<OID>> itemsLootedByPlayer = (HashMap) iInfo.getProperty("itemsLootedByPlayer");
                     itemsLootForPlayer = (HashMap<OID, LinkedList<OID>>) iInfo.getProperty("itemsLootForPlayer");
                if (Log.loggingDebug)
                    log.debug("LootAllHook loot="+loot+" itemsLootedByPlayer="+itemsLootedByPlayer+" itemsLootForPlayer="+itemsLootForPlayer);
                //  if(loot==null ||(loot != null && loot.size()==0)){
                        if(itemsLootForPlayer!=null && itemsLootForPlayer.size() > 0) {
                            log.debug("LootAllHook itemOids is 0 get from itemsLootForPlayer");
                            if(itemsLootForPlayer.containsKey(looterOid))
                                loot = itemsLootForPlayer.get(looterOid);
                        }
                    //}
                    
            } catch (NullPointerException e) {
                if (Log.loggingDebug)
                    log.debug("LOOT: could not find suboject to get data from: " + mobOid);
                PropertyMessage propMsg2 = new PropertyMessage(mobOid, mobOid);
                propMsg2.setProperty("lootable", false);
                Engine.getAgent().sendBroadcast(propMsg2);
                return true;
            }

        if (Log.loggingDebug)
            log.debug("LootAllHook: looterOid="+looterOid+" mobOid="+mobOid+" loot="+loot+" itemsLootedByPlayer="+itemsLootedByPlayer);
            if(loot==null) {
                if (Log.loggingDebug)
                    log.debug("LootAllHook: looterOid="+looterOid+" mobOid="+mobOid+" loot is null");
                PropertyMessage propMsg2 = new PropertyMessage(mobOid, mobOid);
                propMsg2.setProperty("lootable", false);
                Engine.getAgent().sendBroadcast(propMsg2);
                return true;
            }

            List<Integer> questItemReqs = QuestClient.getQuestItemReqs(looterOid);
            LinkedList<OID> leftOverLoot = new LinkedList<OID>();
            for (int i = 0; i < loot.size(); i++) {
                OID itemOid = loot.get(i);
                if (Log.loggingDebug)
                    log.debug("LootAllHook: looterOid="+looterOid+" mobOid="+mobOid+" itemOid="+itemOid);
                

                AgisItem item = getAgisItem(itemOid);
                if (item != null) {
                    ObjectManagerClient.setPersistenceFlag(itemOid, true);
                } else {
                    leftOverLoot.add(itemOid);
                    continue;
                }
                if (Log.loggingDebug)
                    log.debug("LootAllHook: looterOid="+looterOid+" mobOid="+mobOid+" item="+item);
                
            //  if(!item.getItemType().equals("Quest"))
                if (iInfo.getPropertyMap().containsKey("gdice"))
                    if ((int) iInfo.getProperty("gdice") == 1) {
                        int ggrade = (int) iInfo.getProperty("ggrade");
                        if (Log.loggingDebug)
                            log.debug("LOOT: itemGrade=" + item.getProperty("itemGrade") + " ggrade=" + ggrade);
                        if ((int) item.getProperty("itemGrade") > ggrade && !item.getItemType().equals("Quest")) {
                            for (OID target : targets) {
                                Map<String, Serializable> props = new HashMap<String, Serializable>();
                                props.put("itemId", item.getTemplateID());
                                props.put("time", LOOT_DICE_TIMEOUT);
                                props.put("mob", mobOid);
                                props.put("ext_msg_subtype", "ao.GROUP_DICE");
                                TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, target, target, false, props);
                                Engine.getAgent().sendBroadcast(TEmsg);
                            }

                            lootAllRollDice timer = new lootAllRollDice(mobOid, looterOid, itemOid);
                            ScheduledFuture sf = Engine.getExecutor().schedule(timer, (long) LOOT_DICE_TIMEOUT * 1000, TimeUnit.MILLISECONDS);
                            iInfo.setProperty("lootall", 1);
                            iInfo.setProperty("roll", 1);
                            iInfo.setProperty("rollItem", itemOid);
                            iInfo.setProperty("rolllooter", looterOid);
                            iInfo.setProperty("rolls", new HashMap<OID, Float>());
                            if (Log.loggingDebug)
                                log.debug("LootAllHook: looterOid="+looterOid+" mobOid="+mobOid+" dice run  schedule");
                            return true;

                        }

                    }
                
                boolean hasSpace = hasSpace(looterOid, item.getTemplateID(),item.getStackSize(), 0);
                if(!hasSpace) {
                	leftOverLoot.add(itemOid);
                	continue;
                }
                boolean rv = removeItemFromBag(mobOid, itemOid);
                if (Log.loggingDebug)
                    log.debug("LootAllHook: LOOT: removed oid=" + itemOid + ", rv=" + rv);
                if (!rv) {
                    // leftOverLoot.add(itemOid);
                    continue;
                }
                
                // Check to see if the player needs an item that is a quest item
                boolean itemNeeded = true;
                if (item.getItemType().equals("Quest")) {
                    itemNeeded = false;
                    if (Log.loggingDebug)
                        log.debug("LootAllHook: QUEST: found quest item " + item.getName() + ". does the player need it?");
                    for (int j = 0; j < questItemReqs.size(); j++) {
                        Log.debug("LootAllHook: QUEST: checking questItem req: " + questItemReqs.get(j) + " against " + item.getTemplateID());
                        if (questItemReqs.get(j) == item.getTemplateID() && !itemsLootedByPlayer.get(itemOid).contains(looterOid)) {
                            // itemNeeded = true;
                            if (GIVE_QUEST_ITEMS_TO_ALL_IN_GROUP || itemsLootedByPlayer.get(itemOid).size() == 0) {
                                Log.debug("LootAllHook: QUEST: found quest item - it was needed");
                                addItemFromTemplate(looterOid, item.getTemplateID(), item.getStackSize(), null);
                                itemsLootedByPlayer.get(itemOid).add(looterOid);
                            }
                            continue;
                        }
                    }
                }

                if (!itemNeeded) {
                    leftOverLoot.add(itemOid);
                    continue;
                }

                HashMap<String, Serializable> logData = new HashMap<String, Serializable>();
                logData.put("item", "Item attempt to loot : " + item.getName() + " : OID " + itemOid + " : ");
                logData.put("playerOid", looterOid);

                DataLoggerClient.logData("ITEM_LOOTED_EVENT", looterOid, mobOid, accountId, logData);

                // add item to the looter's root bag
                rv = addItem(looterOid, looterOid, itemOid);
                if (Log.loggingDebug)
                    log.debug("LootAllHook: LOOT: addItem to looter, oid=" + itemOid + ", rv=" + rv);
                if (!rv) {
                    DataLoggerClient.logData("ITEM_LOOTED_FAILED_EVENT", looterOid, itemOid, accountId, logData);
                    addItem(mobOid, mobOid, itemOid);
                    leftOverLoot.add(itemOid);
                    continue;
                }
                 AgisInventoryPlugin.addListsRankingData(looterOid, AchievementsClient.LOOT, 1);
                  
                String lootedItem = item.getName();
                if (lootedItem != null) {
                    EventMessageHelper.SendInventoryEvent(looterOid, EventMessageHelper.ITEM_LOOTED, item.getTemplateID(), item.getStackSize(), null);
                }
                // WorldManagerClient.sendObjChatMsg(looterOid, 2, "You have looted: " +
                // lootedItem);
            }
        if (Log.loggingDebug)
            log.debug("LootAllHook: LOOT: leftOverLoot=" + leftOverLoot + ", itemsLootedByPlayer=" + itemsLootedByPlayer);
            setLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot", leftOverLoot);
            iInfo.setProperty("itemsLootedByPlayer", itemsLootedByPlayer);
            if(itemsLootForPlayer!=null) {
                if(itemsLootForPlayer.containsKey(looterOid))
                    itemsLootForPlayer.remove(looterOid);
                iInfo.setProperty("itemsLootForPlayer", itemsLootForPlayer);
            }
            log.debug("LootAllHook: LOOT: send  inv update");
            sendBagInvUpdate(looterOid);
            log.debug("LootAllHook: LOOT: send loot list");
            sendLootList(looterOid, mobOid);

            if (leftOverLoot.isEmpty()) {
                log.debug("LootAllHook: LOOT: skinning");
                setMobSkinnable(mobOid);
            }
            log.debug("LootAllHook: LOOT: END");
            
            return true;
        }
    
    
    /**
     * NOT USED. 
     * @param looterOid player/mob that is looting
     * @param mobOid where you are looting from
     * @return success or failure
     */
    protected boolean lootAll(OID looterOid, OID mobOid) {
        if(Log.loggingDebug)
        log.debug("lootAll: looterOid=" + looterOid + ", mobOid=" + mobOid);
        
        Long rootLooterBagOid;
        lock.lock();
        try {
        }
        finally {
            lock.unlock();
        }
        EnginePlugin.setObjectPropertyNoResponse(mobOid, Namespace.WORLD_MANAGER, "lootable", Boolean.FALSE);

        sendInvUpdate(looterOid);
        return true;
    }
    
    /**
     * Takes the items from the dead mob and places them into the looters bag.
     * @author Andrew
     */
    class LootItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.lootItemMessage lootMsg = (AgisInventoryClient.lootItemMessage)msg;
            OID looterOid = lootMsg.getSubject();
            OID mobOid = (OID) lootMsg.getProperty("mobOid");
            OID itemOid = (OID) lootMsg.getProperty("itemOid");
            if(Log.loggingDebug)
                log.debug("LootItemHook: looter="+looterOid+" mob="+mobOid+" itemOid="+itemOid);
            // Death check
            boolean dead = (Boolean) EnginePlugin.getObjectProperty(looterOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
            if (dead) {
                ExtendedCombatMessages.sendErrorMessage(looterOid, "You cannot peform that action when dead");
                return true;
            }
            if(!INVENTORY_LOOT_ON_GROUND) {
                InventoryInfo iInfo = getInventoryInfo(mobOid);
                if (iInfo == null) {
                    if (Log.loggingDebug)
                        log.debug("LootItemHook: looter=" + looterOid + " mob=" + mobOid + " itemOid=" + itemOid);
                    ExtendedCombatMessages.sendErrorMessage(looterOid, "You cannot peform that action");
                    return true;
                }
                ArrayList<OID> targets = (ArrayList<OID>) iInfo.getProperty("loottargets");
                if (targets != null)
                    if (!targets.contains(looterOid)) {
                        if (Log.loggingDebug)
                            log.debug("LootItemHook player=" + looterOid + " is not in list of targetlotts " + targets + " Break");
                        return true;
                    }
                if (iInfo.getPropertyMap().containsKey("roll"))
                    if ((int) iInfo.getProperty("roll") == 1) {
                        ExtendedCombatMessages.sendErrorMessage(looterOid, "You cannot peform that action when is dice is running");
                        return true;
                    }
                synchronized (iInfo) {

                    OID accountId = (OID) EnginePlugin.getObjectProperty(looterOid, WorldManagerClient.NAMESPACE, "accountId");
                    //Long rootLooterBagOid = looterOid;
                    LinkedList<OID> loot = (LinkedList) getLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot");
                    HashMap<Integer, Integer> lootCurr = new HashMap<Integer, Integer>();
                    LinkedList<String> currLoot = (LinkedList<String>) getLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot_curr");
                    if (currLoot != null) {
                        for (String s : currLoot) {
                            if (!s.isEmpty() && s.contains(";")) {
                                String[] sa = s.split(";");
                                lootCurr.put(Integer.parseInt(sa[0]), Integer.parseInt(sa[1]));
                            }
                        }
                    }
                    if (Log.loggingDebug)
                        log.debug("LootItemHook looterOid=" + looterOid + " get lootCurr=" + lootCurr);
                    HashMap<OID, HashMap<Integer, Integer>> currencyLootForPlayer = (HashMap<OID, HashMap<Integer, Integer>>) iInfo.getProperty("currLootForPlayer");
                    if (currencyLootForPlayer != null) {
                        if (Log.loggingDebug)
                            log.debug("LootItemHook looterOid=" + looterOid + " get from itemsLootForPlayer");
                        if (currencyLootForPlayer.containsKey(looterOid))
                            lootCurr = currencyLootForPlayer.get(looterOid);
                    }


                    HashMap<OID, LinkedList<OID>> itemsLootedByPlayer = (HashMap) iInfo.getProperty("itemsLootedByPlayer");
                    HashMap<OID, LinkedList<OID>> itemsLootForPlayer = (HashMap<OID, LinkedList<OID>>) iInfo.getProperty("itemsLootForPlayer");
                    //if(loot==null ||(loot != null && loot.size()==0)){
                    if (itemsLootForPlayer != null) {
                        if (Log.loggingDebug)
                            log.debug("LootItemHook looterOid=" + looterOid + " get from itemsLootForPlayer");
                        if (itemsLootForPlayer.containsKey(looterOid))
                            loot = itemsLootForPlayer.get(looterOid);
                    }
                    //}

                    // Give the player the item
            /*Template overrideTemplate = new Template();
            overrideTemplate.put(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT, true);
            Long itemOid = ObjectManagerClient.generateObject(itemName, overrideTemplate);
            boolean rv = addItem(looterOid, rootLooterBag.getOid(), itemOid);*/
            
/*          //Andrew delete this 30.10.2017
            // remove the item from the mobs root bag
            boolean rv = removeItemFromBag(mobOid, itemOid);
            log.debug("LOOT: removed oid=" + itemOid + ", rv=" + rv);
            if (!rv) {
                return true;
            }
*/
                    AgisItem item = getAgisItem(itemOid, false);
                    if (item != null) {
                        ObjectManagerClient.setPersistenceFlag(itemOid, true);
                    } else {
                        if (itemOid != null) {
                            long currIdLong = itemOid.toLong();
                            int currId = Math.toIntExact(currIdLong);
                            if (lootCurr.containsKey(currId)) {
                                alterMobCurrency(looterOid, currId, lootCurr.get(currId));
                                alterCurrecyLog(looterOid, accountId, currId, lootCurr.get(currId), "Loot Item");
                                lootCurr.remove(currId);
                                if (currencyLootForPlayer != null) {
                                    currencyLootForPlayer.replace(looterOid, lootCurr);
                                    iInfo.setProperty("currLootForPlayer", currencyLootForPlayer);
                                } else {
                                    LinkedList<String> currencyToLoot = new LinkedList<String>();
                                    for (int i : lootCurr.keySet()) {
                                        currencyToLoot.add(i + ";" + lootCurr.get(i));
                                    }
                                    setLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot_curr", currencyToLoot);
                                }
                                sendBagInvUpdate(looterOid);
                                sendLootList(looterOid, mobOid);
                            }
                        }
                        return true;
                    }
                    //iInfo.setProperty("groll", groll);
                    // if(!item.getItemType().equals("Quest"))
                    if (iInfo.getPropertyMap().containsKey("gdice"))
                        if ((int) iInfo.getProperty("gdice") == 1) {
                            int ggrade = (int) iInfo.getProperty("ggrade");
                            if (Log.loggingDebug)
                                log.debug("LOOT: itemGrade=" + item.getProperty("itemGrade") + " ggrade=" + ggrade);
                            if ((int) item.getProperty("itemGrade") > ggrade && !item.getItemType().equals("Quest")) {
                                for (OID target : targets) {
                                    Map<String, Serializable> props = new HashMap<String, Serializable>();
                                    props.put("itemId", item.getTemplateID());
                                    props.put("time", LOOT_DICE_TIMEOUT);
                                    props.put("mob", mobOid);
                                    props.put("ext_msg_subtype", "ao.GROUP_DICE");
                                    TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, target, target, false, props);
                                    Engine.getAgent().sendBroadcast(TEmsg);
                                }

                                lootRollDice timer = new lootRollDice(mobOid, looterOid, itemOid);
                                ScheduledFuture sf = Engine.getExecutor().schedule(timer, (long) LOOT_DICE_TIMEOUT * 1000, TimeUnit.MILLISECONDS);
                                iInfo.setProperty("lootall", 0);
                                iInfo.setProperty("roll", 1);
                                iInfo.setProperty("rollItem", itemOid);
                                iInfo.setProperty("rolllooter", looterOid);
                                iInfo.setProperty("rolls", new HashMap<OID, Float>());

                                return true;

                            }

                        }


                    HashMap<String, Serializable> logData = new HashMap<String, Serializable>();
                    logData.put("item", "Item attempt to loot : " + item.getName() + " : OID " + itemOid + " : ");
                    logData.put("playerOid", looterOid);

                    DataLoggerClient.logData("ITEM_LOOTED_EVENT", looterOid, mobOid, accountId, logData);

                    if (item.getItemType().equals("Quest")) {
                        if (GIVE_QUEST_ITEMS_TO_ALL_IN_GROUP || itemsLootedByPlayer.get(itemOid).size() == 0) {
                            // We don't actually give the item to the player if its a quest item - they just get a copy and we track that they were given a copy
                            addItemFromTemplate(looterOid, item.getTemplateID(), item.getStackSize(), null);
                            itemsLootedByPlayer.get(itemOid).add(looterOid);
                            iInfo.setProperty("itemsLootedByPlayer", itemsLootedByPlayer);
                            EventMessageHelper.SendInventoryEvent(looterOid, EventMessageHelper.ITEM_LOOTED, item.getTemplateID(), item.getStackSize(), null);
                        }
                        sendBagInvUpdate(looterOid);
                        sendLootList(looterOid, mobOid);
                        return true;
                    }

                    // remove the item from the mobs root bag
                    boolean rv = removeItemFromBag(mobOid, itemOid);
                    if (Log.loggingDebug)
                        log.debug("LOOT: removed oid=" + itemOid + ", rv=" + rv);
                    if (!rv) {
                        return true;
                    }


                    // add item to the looter's root bag
                    rv = addItem(looterOid, looterOid, itemOid);
                    if (Log.loggingDebug)
                        log.debug("LOOT: addItem to looter, oid=" + itemOid + ", rv=" + rv);
                    if (!rv) {
                        DataLoggerClient.logData("ITEM_LOOTED_FAILED_EVENT", looterOid, itemOid, accountId, logData);
                        addItem(mobOid, mobOid, itemOid);
                        return true;
                    }
                    AgisInventoryPlugin.addListsRankingData(looterOid, AchievementsClient.LOOT, 1);

                    loot.remove(itemOid);
                    if (itemsLootForPlayer != null) {
                        if (itemsLootForPlayer.containsKey(looterOid))
                            itemsLootForPlayer.replace(looterOid, loot);
                        iInfo.setProperty("itemsLootForPlayer", itemsLootForPlayer);
                    }
                    setLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot", loot);
                    String lootedItem = item.getName();
                    if (lootedItem != null) {
                        EventMessageHelper.SendInventoryEvent(looterOid, EventMessageHelper.ITEM_LOOTED, item.getTemplateID(), item.getStackSize(), null);
                        //WorldManagerClient.sendObjChatMsg(looterOid, 2, "You have looted: " + lootedItem);
                    }
                    if (loot.isEmpty()) {
                        setMobSkinnable(mobOid);
                    } else {
                        if (Log.loggingDebug)
                            Log.debug("SKINNING: mob still has " + loot.size() + " loot items left");
                    }
                }
            }else{
                //Loot On the Ground

            }
            sendBagInvUpdate(looterOid);
            sendLootList(looterOid, mobOid);
            
            
            return true;
        }
    }

    void alterCurrecyLog(OID playerOid, OID accountId, int currencyID, long delta, String comment ){
        Currency c = Agis.CurrencyManager.get(currencyID);
        if(accountId == null){
            accountId = Engine.getDatabase().getAccountOid(playerOid);
                    //(OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
        }
//        if (c.getExternal()) {
            HashMap<String, Serializable> logData = new HashMap<String, Serializable>();
            logData.put("item", comment+ ";" + c.getCurrencyID()+";" + c.getCurrencyName() + ";"+delta );
            logData.put("playerOid", playerOid);
            DataLoggerClient.logData("CURRENCY_EVENT", playerOid, null, accountId, logData);
//        }
    }
    
    
public class lootAllRollDice implements Runnable {
        
        protected OID mobOid;
        protected OID looter;
        protected OID itemOID;
        
        public lootAllRollDice(OID mobOid, OID looter,OID itemOID) {
            this.mobOid = mobOid;
            this.looter = looter;
            this.itemOID = itemOID;
            
        }
        
        @Override
        public void run() {
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            synchronized (iInfo) {
                log.debug("lootAllRollDice Run mobOid=" + mobOid + " looter=" + looter + " itemOID=" + itemOID);

                if (((OID) iInfo.getProperty("rollItem")).equals(itemOID)) {
                    LinkedList<OID> loot = (LinkedList) getLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot");
                    log.debug("lootAllRollDice Run mobOid=" + mobOid + " looter=" + looter + " loot="+loot);
                    ArrayList<OID> targets = (ArrayList<OID>) iInfo.getProperty("loottargets");
                    LinkedList<OID> lootquests = new LinkedList<OID>(); 
                    if (((int) iInfo.getProperty("roll")) == 1) {
                        loot=CalculateLootRoll(mobOid, itemOID,loot);
                        log.debug("lootAllRollDice Run mobOid=" + mobOid + " looter=" + looter + " after CalculateLootRoll loot="+loot);
                        //LinkedList<OID> loot = (LinkedList) EnginePlugin.getObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot");
                        LinkedList<OID> notloot = new LinkedList<OID>(loot);
                        for (int i = 0; i < loot.size(); i++) {
                            OID itemOid = loot.get(i);
                            AgisItem item = getAgisItem(itemOid);
                            log.debug("lootAllRollDice Run mobOid=" + mobOid + " looter=" + looter + " i="+i+" itemOid="+itemOid+" item="+item);
                             if(item.getItemType().equals("Quest")) {
                                    if (GIVE_QUEST_ITEMS_TO_ALL_IN_GROUP ) {
                                        lootquests.add(itemOid);
                                        }
                                    }
                            // if(!item.getItemType().equals("Quest"))
                            if (iInfo.getPropertyMap().containsKey("gdice"))
                                if ((int) iInfo.getProperty("gdice") == 1) {
                                    int ggrade = (int) iInfo.getProperty("ggrade");
                                    log.debug("LOOT: itemGrade=" + item.getProperty("itemGrade") + " ggrade=" + ggrade);
                                    if ((int) item.getProperty("itemGrade") > ggrade && !item.getItemType().equals("Quest")) {
                                        for (OID target : targets) {
                                            Map<String, Serializable> props = new HashMap<String, Serializable>();
                                            props.put("itemId", item.getTemplateID());
                                            props.put("time", LOOT_DICE_TIMEOUT);
                                            props.put("mob", mobOid);
                                            props.put("ext_msg_subtype", "ao.GROUP_DICE");
                                            TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, target, target, false, props);
                                            Engine.getAgent().sendBroadcast(TEmsg);
                                        }

                                        lootAllRollDice timer = new lootAllRollDice(mobOid, looter, itemOid);
                                        ScheduledFuture sf = Engine.getExecutor().schedule(timer, (long) LOOT_DICE_TIMEOUT * 1000, TimeUnit.MILLISECONDS);
                                        iInfo.setProperty("lootall", 1);
                                        iInfo.setProperty("roll", 1);
                                        iInfo.setProperty("rollItem", itemOid);
                                        iInfo.setProperty("rolllooter", looter);
                                        iInfo.setProperty("rolls", new HashMap<OID, Float>());
                                        setLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot", notloot);
                                        return ;
                                        
                                    }else {
                                        loot=CalculateLootRoll(mobOid, itemOid,loot);
                                        notloot.remove(itemOid);
                                    }
                                }

                        }
                    }else {
                        log.debug("lootAllRollDice Run mobOid=" + mobOid + " looter=" + looter +" rooll! =1 ");
                    }
                    HashMap<OID, LinkedList<OID>> itemsLootedByPlayer = (HashMap) iInfo.getProperty("itemsLootedByPlayer");
                    log.debug("lootAllRollDice Run mobOid=" + mobOid + " looter=" + looter +" itemsLootedByPlayer="+itemsLootedByPlayer);
                    boolean notallget=false;
                      for(OID itemOid: lootquests) {
                          int count = itemsLootedByPlayer.get(itemOid).size();
                          if(count<targets.size()) {
                              notallget = true;
                          }
                      }
                    log.debug("lootAllRollDice Run mobOid=" + mobOid + " looter=" + looter +" notallget="+notallget);
                      if(notallget)
                          setLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot", lootquests);
                }
                log.debug("lootAllRollDice Run mobOid=" + mobOid + " looter=" + looter +" EndRun");
            }
            // sendLootList(looter, mobOid);
        }
    }

    
    
 public class lootRollDice implements Runnable {
        
        protected OID mobOid;
        protected OID looter;
        protected OID itemOID;
        
        public lootRollDice(OID mobOid, OID looter,OID itemOID) {
            this.mobOid = mobOid;
            this.looter = looter;
            this.itemOID = itemOID;
            
        }
        
        @Override
        public void run() {
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            synchronized (iInfo) {
                log.debug("lootRollDice Run mobOid=" + mobOid + " looter=" + looter + " itemOID=" + itemOID);

                if (((OID) iInfo.getProperty("rollItem")).equals(itemOID)) {
                    if (((int) iInfo.getProperty("roll")) == 1) {
                        LinkedList<OID> loot = (LinkedList) getLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot");
                        loot=CalculateLootRoll(mobOid,itemOID,loot);
                        setLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot", loot);
                        
                    }
                }
            }
            // sendLootList(looter, mobOid);
        }
    }
    
    class LootRollHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID rollerOid = eMsg.getSubject();
            OID mobOID = (OID) eMsg.getProperty("loottarget");
            //OID mobOID = OID.fromLong(mobOid);
            int isroll = (int) eMsg.getProperty("roll");
            log.debug("LootRollHook rollerOid="+rollerOid+" mobOID="+mobOID+" isroll="+isroll);
            
            InventoryInfo iInfo = getInventoryInfo(mobOID);
            if(iInfo==null) {
                log.debug("LootRollHook iInfo is null for mobOID="+mobOID);
            }
            
            
            
            float roll = 0f;
            if (isroll == 1) {
                roll = AgisInventoryPlugin.random.nextFloat() * 1000f;
            }
            HashMap<OID, Float> rolls = new HashMap<OID, Float>();
            synchronized (iInfo) {
                 rolls = (HashMap<OID, Float>) iInfo.getProperty("rolls");
                if (!rolls.containsKey(rollerOid)) {
                    rolls.put(rollerOid, roll);
                } else {
                    log.debug("LootRollHook: rollerOid=" + rollerOid + " found in list");
                    return true;
                }
            }
            ArrayList<OID> targets = (ArrayList<OID>) iInfo.getProperty("loottargets");
            String targetName = aDB.getCharacterNameByOid(rollerOid);
            //String targetName = WorldManagerClient.getObjectInfo(rollerOid).name;
            
            if (isroll == 1) {
                for (OID roller : targets) {
                    TargetedComReqMessage comMessage = new TargetedComReqMessage(roller, roller, null, 4, targetName+" Roll " + (int)roll);
                    Engine.getAgent().sendBroadcast(comMessage);
                }
            }else {
                
                for (OID roller : targets) {
                    TargetedComReqMessage comMessage = new TargetedComReqMessage(roller, roller, null, 4, targetName+" Pass Roll");
                    Engine.getAgent().sendBroadcast(comMessage);
                }
            }
            
            if(rolls.size()==targets.size()) {
                LinkedList<OID> loot = (LinkedList) getLocalObjectProperty(mobOID, InventoryClient.NAMESPACE, "loot");
                if(((int)iInfo.getProperty("lootall"))==0) {
                    OID itemOid = (OID) iInfo.getProperty("rollItem");
                    log.debug("LootRollHook not loot all itemOid="+itemOid);
                    loot = CalculateLootRoll( mobOID,itemOid,loot);
                    setLocalObjectProperty(mobOID, InventoryClient.NAMESPACE, "loot", loot);
                }else {
                    log.debug("LootRollHook loot all ");
                    
                //  ArrayList<OID> targets = (ArrayList<OID>) iInfo.getProperty("loottargets");
                    LinkedList<OID> lootquests = new LinkedList<OID>(); 
                    if (((int) iInfo.getProperty("roll")) == 1) {
                        OID _itemOid = (OID) iInfo.getProperty("rollItem");
                        log.debug("LootRollHook _itemOid="+_itemOid);
                        
                        loot=CalculateLootRoll(mobOID, _itemOid,loot);
                        //LinkedList<OID> loot = (LinkedList) EnginePlugin.getObjectProperty(mobOID, InventoryClient.NAMESPACE, "loot");
                        log.debug("LootRollHook loot="+loot);
                        
                        LinkedList<OID> notloot = new LinkedList<OID>(loot);
                        for (int i = 0; i < loot.size(); i++) {
                            OID itemOid = loot.get(i);
                        
                            
                            AgisItem item = getAgisItem(itemOid);
                            log.debug("LootRollHook for "+i+"itemOid="+itemOid+" item="+item);
                             if(item.getItemType().equals("Quest")) {
                                    if (GIVE_QUEST_ITEMS_TO_ALL_IN_GROUP ) {
                                        lootquests.add(itemOid);
                                        }
                                    }
                             
                            if (iInfo.getPropertyMap().containsKey("gdice"))
                                if ((int) iInfo.getProperty("gdice") == 1) {
                                    int ggrade = (int) iInfo.getProperty("ggrade");
                                    log.debug("LootRollHook: itemGrade=" + item.getProperty("itemGrade") + " ggrade=" + ggrade);
                                    if ((int) item.getProperty("itemGrade") > ggrade && !item.getItemType().equals("Quest")) {
                                        OID looter =(OID) iInfo.getProperty("rolllooter");
                                        for (OID target : targets) {
                                            Map<String, Serializable> props = new HashMap<String, Serializable>();
                                            props.put("itemId", item.getTemplateID());
                                            props.put("time", LOOT_DICE_TIMEOUT);
                                            props.put("mob", mobOID);
                                            props.put("ext_msg_subtype", "ao.GROUP_DICE");
                                            TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, target, target, false, props);
                                            Engine.getAgent().sendBroadcast(TEmsg);
                                        }

                                        lootAllRollDice timer = new lootAllRollDice(mobOID, looter, itemOid);
                                        ScheduledFuture sf = Engine.getExecutor().schedule(timer, (long) LOOT_DICE_TIMEOUT * 1000, TimeUnit.MILLISECONDS);
                                        iInfo.setProperty("lootall", 1);
                                        iInfo.setProperty("roll", 1);
                                        iInfo.setProperty("rollItem", itemOid);
                                        iInfo.setProperty("rolllooter", looter);
                                        iInfo.setProperty("rolls", new HashMap<OID, Float>());
                                        setLocalObjectProperty(mobOID, InventoryClient.NAMESPACE, "loot", notloot);
                                        log.debug("LootRollHook start dice roll END");
                                        return true;
                                        
                                    } else {
                                        loot= CalculateLootRoll(mobOID, itemOid,loot);
                                        notloot.remove(itemOid);
                                    }
                                }

                        }
                    }
                    HashMap<OID, LinkedList<OID>> itemsLootedByPlayer = (HashMap) iInfo.getProperty("itemsLootedByPlayer");
                    boolean notallget = false;
                    for (OID itemOid : lootquests) {
                        int count = itemsLootedByPlayer.get(itemOid).size();
                        if (count < targets.size()) {
                            notallget = true;
                        }
                    }
                    log.debug("LootRollHook notallget=" + notallget);

                    if (notallget)
                        setLocalObjectProperty(mobOID, InventoryClient.NAMESPACE, "loot", lootquests);

                }

            }

            log.debug("LootRollHook END");

            return true;
        }
    }

   private LinkedList<OID> CalculateLootRoll(OID mobOid,OID itemOid,LinkedList<OID> loot) {
        InventoryInfo iInfo = getInventoryInfo(mobOid);
        log.debug("CalculateLootRoll mobOid="+mobOid+" itemOid="+itemOid);
        HashMap<OID, Float> rolls = (HashMap<OID, Float>) iInfo.getProperty("rolls");
        OID win=null;
        float maxroll=0f;
        for(OID ply:rolls.keySet()) {
            if(maxroll<rolls.get(ply)) {
                maxroll = rolls.get(ply);
                win = ply;
            }
            
        }
        OID looter =(OID) iInfo.getProperty("rolllooter");
        
        synchronized(iInfo) {
           if(maxroll==0f) {
               win = looter;
           }
        
          AgisItem item = getAgisItem(itemOid);
          if (item != null) {
              ObjectManagerClient.setPersistenceFlag(itemOid,true);
          }else {
              log.error("CalculateLootRoll mobOid="+mobOid+" itemOid="+itemOid+" item null");
              
            return loot;
            }
        HashMap<String, Serializable> logData = new HashMap<String, Serializable>();
        logData.put("item", "Item attempt to loot : " + item.getName()+ " : OID " + itemOid + " : " );
        logData.put("playerOid", win);
        OID accountId = (OID) EnginePlugin.getObjectProperty(win, WorldManagerClient.NAMESPACE, "accountId");
         
        DataLoggerClient.logData("ITEM_LOOTED_EVENT", win, mobOid, accountId, logData);
        HashMap<OID, LinkedList<OID>> itemsLootedByPlayer = (HashMap) iInfo.getProperty("itemsLootedByPlayer");
        //LinkedList<OID> loot = (LinkedList) EnginePlugin.getObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot");
      
       
     
        
        if(item.getItemType().equals("Quest")) {
            //TODO LOOT Quest
        /*     List<Integer> questItemReqs = QuestClient.getQuestItemReqs(win);
               for (int j = 0; j < questItemReqs.size(); j++) {
                Log.debug("QUEST: checking questItem req: " + questItemReqs.get(j) + " against " + item.getTemplateID());
                if (questItemReqs.get(j) == item.getTemplateID() && !itemsLootedByPlayer.get(itemOid).contains(win)) {
                    // itemNeeded = true;
                    if (GIVE_QUEST_ITEMS_TO_ALL_IN_GROUP || itemsLootedByPlayer.get(itemOid).size() == 0) {
                        Log.debug("QUEST: found quest item - it was needed");
                        addItemFromTemplate(win, item.getTemplateID(), item.getStackSize(), null);
                        itemsLootedByPlayer.get(itemOid).add(win);
                    }
                    continue;
                }
            }
            */
            
            
            
            if (GIVE_QUEST_ITEMS_TO_ALL_IN_GROUP || itemsLootedByPlayer.get(itemOid).size() == 0) {
                // We don't actually give the item to the player if its a quest item - they just get a copy and we track that they were given a copy
                addItemFromTemplate(win, item.getTemplateID(), item.getStackSize(), null);
                itemsLootedByPlayer.get(itemOid).add(win);
                iInfo.setProperty("itemsLootedByPlayer", itemsLootedByPlayer);
                EventMessageHelper.SendInventoryEvent(win, EventMessageHelper.ITEM_LOOTED, item.getTemplateID(), item.getStackSize(), null);
            }
            sendBagInvUpdate(win);
            sendLootList(looter, mobOid);
            log.debug("CalculateLootRoll mobOid="+mobOid+" itemOid="+itemOid+" quest");
            return loot;
        }
        
        // remove the item from the mobs root bag
        boolean rv = removeItemFromBag(mobOid, itemOid);
        log.debug("LOOT: removed oid=" + itemOid + ", rv=" + rv);
        if (!rv) {
            log.debug("CalculateLootRoll mobOid="+mobOid+" itemOid="+itemOid+" cant remove");
            return loot;
        }
        // add item to the looter's root bag
        rv = addItem(win, win, itemOid);
        log.debug("LOOT: addItem to looter, oid=" + itemOid + ", rv=" + rv);
        if (!rv) {
            DataLoggerClient.logData("ITEM_LOOTED_FAILED_EVENT", win, itemOid, accountId, logData);
            addItem(mobOid, mobOid, itemOid);
            log.debug("CalculateLootRoll mobOid="+mobOid+" itemOid="+itemOid+" cant add");
            return loot;
        }
        AgisInventoryPlugin.addListsRankingData(win, AchievementsClient.LOOT, 1);
        
        loot.remove(itemOid);
        setLocalObjectProperty(mobOid, InventoryClient.NAMESPACE, "loot", loot);
        String lootedItem = item.getName();
        if (lootedItem != null) {
            EventMessageHelper.SendInventoryEvent(win, EventMessageHelper.ITEM_LOOTED, item.getTemplateID(), item.getStackSize(), null);
            //WorldManagerClient.sendObjChatMsg(looterOid, 2, "You have looted: " + lootedItem);
        }
        if (loot.isEmpty()) {
            setMobSkinnable(mobOid);
        } else {
            Log.debug("SKINNING: mob still has " + loot.size() + " loot items left");
        }
        iInfo.setProperty("roll",0);
        }
        sendBagInvUpdate(win);
        sendLootList(looter, mobOid);
        log.debug("CalculateLootRoll mobOid="+mobOid+" END");
        return loot;
    }
    
    
    
    class LootChestHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID looterOid = eMsg.getSubject();
            OID bagOid = (OID)eMsg.getProperty("BagOid");
            OID itemOid = (OID)eMsg.getProperty("ItemOid");
            
            // Death check
            boolean dead = (Boolean) EnginePlugin.getObjectProperty(looterOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
            if (dead) {
                ExtendedCombatMessages.sendErrorMessage(looterOid, "You cannot peform that action when dead");
                return true;
            }
            
            OID accountId = (OID) EnginePlugin.getObjectProperty(looterOid, WorldManagerClient.NAMESPACE, "accountId");
            
            AgisItem item = getAgisItem(itemOid);
            if (item != null)
                ObjectManagerClient.setPersistenceFlag(itemOid,true);
            else
                return true;
            
            HashMap<String, Serializable> logData = new HashMap<String, Serializable>();
            logData.put("item", "Item attempt to loot : " + item.getName()+ " : OID " + itemOid + " : " );
            logData.put("playerOid", looterOid);
            
            DataLoggerClient.logData("ITEM_LOOTED_EVENT", looterOid, bagOid, accountId, logData);
            
            if (!removeItemFromBagHelper(bagOid, item)) {
                log.error("LOOT: failed to remove loot from chest.");
                return true;
            }
            log.debug("LOOT: removed oid=" + itemOid);
            
            // add item to the looter's root bag
            boolean rv = addItem(looterOid, looterOid, itemOid);
            log.debug("LOOT: addItem to looter, oid=" + itemOid + ", rv=" + rv);
            if (!rv) {
                DataLoggerClient.logData("ITEM_LOOTED_FAILED_EVENT", looterOid, itemOid, accountId, logData);
                addItem(bagOid, bagOid, itemOid);
                return true;
            }
            
            String lootedItem = item.getName();
            if (lootedItem != null) {
                EventMessageHelper.SendInventoryEvent(looterOid, EventMessageHelper.ITEM_LOOTED, item.getTemplateID(), item.getStackSize(), null);
                //WorldManagerClient.sendObjChatMsg(looterOid, 2, "You have looted: " + lootedItem);
            }
            sendLockpickLootList(looterOid, null, bagOid, 1);
            
            sendInvUpdate(looterOid);
            
            return true;
        }
    }
    
    protected boolean lootAllHelper(OID looterOid, Bag looterRootBag, OID mobOid, Bag mobRootBag, Bag mobSubBag) {
        // assumes locking
        // process each item in bag
        // First lets see what items a player needs for their quests
        List<Integer> questItemReqs = QuestClient.getQuestItemReqs(looterOid);
        Log.debug("QUEST: got questItemReqs response: " + questItemReqs.toString());
        
        //Random loot hack!
        // Lets random a number between 0 and the number of items, only that item will be added
        int numItems = 2; // Start with 2 so that sometimes no item will be dropped
        int equippedItems = 0;
        for (int slotNum=0; slotNum < mobSubBag.getNumSlots(); slotNum++) {
            EquipMap emap = getEquipMap(mobOid);
            OID itemOid = mobSubBag.getItem(slotNum);
            if (itemOid == null) {
                log.debug("lootAllHelper: slotNum " + slotNum + " is empty");
                continue;
            } else if (emap.containsValue(itemOid)) {
                equippedItems++;
                continue;
            }
            numItems++;
        }
        Random rand = new Random();
        int roll = rand.nextInt(numItems);
        roll = roll + equippedItems;
        Log.debug("LOOT: roll is: " + roll + "; num items: " + numItems + "; equipped items: " + equippedItems);
        
        for (int slotNum=0; slotNum < mobSubBag.getNumSlots(); slotNum++) {
            EquipMap emap = getEquipMap(mobOid);
            OID itemOid = mobSubBag.getItem(slotNum);
            if (itemOid == null ||  emap.containsValue(itemOid)) {
                log.debug("lootAllHelper: slotNum " + slotNum + " is empty");
                continue;
            }
            boolean questItem = false;
            
            log.debug("lootAllHelper: processing sub bags item slot="+slotNum+
                " oid=" + itemOid);
            
            // remove the item from the mobs root bag
            boolean rv = removeItemFromBag(mobRootBag.getOid(), itemOid);
            log.debug("lootAllHelper: removed oid=" + itemOid + ", rv=" + rv);
            if (! rv) {
                continue;
            }

            AgisItem item = getAgisItem(itemOid);
            if(item.getItemType().equals("Quest")){
                Log.debug("QUEST: found quest item " + item.getName() + ". does the player need it?");
                int needed = 0;
                for (int i = 0; i < questItemReqs.size(); i++) {
                    if (questItemReqs.get(i) == item.getTemplateID())
                        needed = 1;
                }
                if (needed == 0)
                    continue;
                Log.debug("QUEST: found quest item - it was needed");
                questItem = true;
            }
            if (item != null) {
                ObjectManagerClient.setPersistenceFlag(itemOid,true);
            } else {
                continue;
            }

            // add item to the looter's root bag
            if (slotNum != roll)
                continue;
            rv = addItem(looterOid, looterRootBag.getOid(), itemOid);
            log.debug("lootAllHelper: addItem to looter, oid=" + itemOid + ", rv=" + rv);
            if (! rv) {
                continue;
            }
            ChatClient.sendObjChatMsg(looterOid, 2, "You have recieved: " + item.getName());
        }
        log.debug("lootAllHelper: done processing subbag " + mobSubBag);
        return true;
    }

    /**
     * Returns whether or not the mob contains the given item
     * @param mobOid the player/mob to check against
     * @param itemOid the item you are checking against
     * @return true if mob contains item, false otherwise
     */
    protected boolean containsItem(OID mobOid, OID itemOid) {
        lock.lock();
        try {
            AgisItem item = getAgisItem(itemOid);
            if (item == null) {
                return false;
            }
            if (item.isDeleted()) {
                return false;
            }
            OID subBagOid = (OID)item.getProperty(INVENTORY_PROP_BACKREF_KEY);
            if (subBagOid == null) {
                return false;
            }
            
            // get the sub-bag
            Bag subBag = getBag(subBagOid);
            if (subBag == null) {
                return false;
            }
            
            // get the mob owner oid -- which matches the parent bag oid by convention
            OID rootBagOid = (OID)subBag.getProperty(INVENTORY_PROP_BACKREF_KEY);
            if (rootBagOid == null) {
                return false;
            }
            return (mobOid.equals(rootBagOid));
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * finds an item based on the template name
     */
    protected OID findItem(OID mobOid, int templateID) {
        lock.lock();
        try {
        // find the mob's root bag
            if (Log.loggingDebug)
                log.debug("findItem: mob=" + mobOid + " template=" + templateID);
            OID rootBagOid = mobOid;
            if (rootBagOid == null) {
                log.debug("findItem: cant find rootBagOid");
                return null;
            }
            InventoryInfo iInfo = getInventoryInfo(rootBagOid);
            if (iInfo == null) {
                log.debug("findItem: could not find root bag");
                return null;
            }

            ArrayList<OID> resultList = new ArrayList<OID>();
            findItemHelper(mobOid, iInfo, templateID, resultList);
            return resultList.get(0);
        }
        finally {
            lock.unlock();
        }
    }
    
    /**
     * Finds all stacks of items matching the templateID that the player has.
     * @param mobOid
     * @param templateID
     * @return
     */
    protected ArrayList<OID> findItemStacks(OID mobOid, int templateID) {
        lock.lock();
        try {
            // find the mob's root bag
            if (Log.loggingDebug)
                log.debug("findItem: mob=" + mobOid + " template=" + templateID);
            OID rootBagOid = mobOid;
            if (rootBagOid == null) {
                log.debug("findItem: cant find rootBagOid");
                return null;
            }
            InventoryInfo iInfo = getInventoryInfo(rootBagOid);
            if (iInfo == null) {
                log.debug("findItem: could not find root bag");
                return null;
            }

            ArrayList<OID> resultList = new ArrayList<OID>();
            findItemStacksHelper(mobOid, iInfo, templateID, resultList);
            return resultList;
        }
        finally {
            lock.unlock();
        }
    }
    
    /**
     * Helper function for the findItemStacks function.
     * @param mobOid
     * @param iInfo
     * @param templateID
     * @param resultList
     */
    protected void findItemStacksHelper(OID mobOid, InventoryInfo iInfo, int templateID, ArrayList<OID>resultList) {
        OID[] subBags = iInfo.getBags();
        for (int i = 0; i < subBags.length; i++) {
            OID subBagOid = subBags[i];
            if (subBagOid == null)
                continue;
            Bag subBag = getBag(subBagOid);
            for (OID itemOid : subBag.getItemsList()) {
                if (itemOid == null)
                    continue;
                AgisItem item = getAgisItem(itemOid);
                if (templateID == item.getTemplateID()) {
                    if (resultList.contains(itemOid))
                        continue;
                    if (Log.loggingDebug)
                        log.debug("findItemHelper: adding item to resultList=" + itemOid);
                    resultList.add(itemOid);
                }
            }
        }
    }

    /**
     * Finds items in the players inventory that match one of the template IDs specified.
     */
    protected ArrayList<OID> findItems(OID mobOid, ArrayList<Integer> templateList) {
        lock.lock();
        try {
        // find the mob's root bag
            if (Log.loggingDebug)
                log.debug("findItem: mob=" + mobOid + " templateList=" + templateList);
            OID rootBagOid = mobOid;
            if (rootBagOid == null) {
                log.debug("findItem: cant find rootBagOid");
                return null;
            }
            InventoryInfo iInfo = getInventoryInfo(rootBagOid);
            if (iInfo == null) {
                log.debug("findItem: could not find root bag");
                return null;
            }

            ArrayList<OID> resultList = new ArrayList<OID>();
            for (int template : templateList) {
                findItemHelper(mobOid, iInfo, template, resultList);
            }
            return resultList;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Helper function for check if the item is in the bag of the player
     * @param plyOid
     * @param itemOid
     * @return boolean 
     */
    protected boolean hasItem(OID plyOid, OID itemOid) {
        lock.lock();
        try {
            InventoryInfo iInfo = getInventoryInfo(plyOid);
            OID[] subBags = iInfo.getBags();
            if (Log.loggingDebug)
                log.debug("hasItem: bags=" + subBags);
            for (int pos = 0; pos < subBags.length; pos++) {
                OID subBagOid = subBags[pos];
                Bag subBag = getBag(subBagOid);
                if (Log.loggingDebug)
                    log.debug("hasItem: subBag=" + subBag);
                if (subBag != null) {
                    Integer slotNum = subBag.findItem(itemOid);
                    if (slotNum != null) {
                        return true;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return false;
    }
    
     /**
     * Helper function for check if the item is in the bag of the player
     * @param plyOid
     * @param itemOid
     * @return OID 
     */
    /*protected OID hasItem(OID plyOid, OID itemOid) {
        lock.lock();
        try {
            InventoryInfo iInfo = getInventoryInfo(plyOid);
            OID[] subBags = iInfo.getBags();
            if (Log.loggingDebug)
                log.debug("hasItem: bags=" + subBags);
            for (int pos = 0; pos < subBags.length; pos++) {
                OID subBagOid = subBags[pos];
                Bag subBag = getBag(subBagOid);
                if (Log.loggingDebug)
                    log.debug("hasItem: subBag=" + subBag);
                if (subBag != null) {
                    Integer slotNum = subBag.findItem(itemOid);
                    if (slotNum != null) {
                        return subBagOid;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return null;
    }*/
    /**
     * Helper function for the findItem and findItems functions.
     * @param mobOid
     * @param iInfo
     * @param templateID
     * @param resultList
     * @return
     */
    protected boolean findItemHelper(OID mobOid, InventoryInfo iInfo, int templateID, ArrayList<OID>resultList) {
        OID[] subBags = iInfo.getBags();
        for (int i = 0; i < subBags.length; i++) {
            OID subBagOid = subBags[i];
            if (subBagOid == null)
                continue;
            Bag subBag = getBag(subBagOid);
            for (OID itemOid : subBag.getItemsList()) {
                if (itemOid == null)
                    continue;
                AgisItem item = getAgisItem(itemOid);
                if (templateID == item.getTemplateID()) {
                    if (resultList.contains(itemOid))
                        continue;
                    if (Log.loggingDebug)
                        log.debug("findItemHelper: adding item to resultList=" + itemOid);
                    resultList.add(itemOid);
                    return true;
                }
            }
        }
        resultList.add(null);
        return false;
    }

    /**
     * Finds an equipped item
     * @param mobOid
     * @param slot
     * @return
     */
    protected OID findItem(OID mobOid, AgisEquipSlot slot) {
    lock.lock();
    try {
        EquipMap equipMap = getEquipMap(mobOid);
        OID itemOid = equipMap.get(slot);
        if (itemOid != null) {
            return equipMap.get(slot);
        } else {
            return null;
        }
    }
    finally {
        lock.unlock();
    }
    }

    /**
     * Removes the specified item from the player. 
     */
    protected OID removeItem(OID mobOid, OID itemOid, boolean removeFromPlayer) {
        lock.lock();
        try {
            AgisItem item = getAgisItem(itemOid);
            if (item == null)
                return null;
            unequipItem(item, mobOid, false);
            OID rootBagOid = mobOid;
            if (rootBagOid == null) {
                log.debug("removeItem: cant find rootBagOid");
                return null;
            }
            Boolean result = removeItemFromBag(rootBagOid, itemOid);
            if (result == true) {
                if (removeFromPlayer)
                    item.unacquired(mobOid);
                return itemOid;
            } else {
                // Item may be in storage
                return removeItemFromStorage(mobOid, itemOid, removeFromPlayer);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes an item from the players inventory that matches the specified templateID
     */
    protected OID removeItem(OID mobOid, int template, boolean removeFromPlayer) {
        lock.lock();
        try {
            OID itemOid = findItem(mobOid, template);
            if (Log.loggingDebug)
                log.debug("removeItem: mobOid=" + mobOid + " template=" + template + " ItemOid=" + itemOid);
            return removeItem(mobOid, itemOid, removeFromPlayer);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes all items from the players inventory that match one of the template IDs specified.
     */
    protected ArrayList<OID> removeItems(OID mobOid, ArrayList<Integer> templateList, boolean removeFromPlayer) {
        lock.lock();
        try {
            if (Log.loggingDebug)
                log.debug("removeItems: mobOid=" + mobOid + " templateList=" + templateList);
            ArrayList<OID> itemList = findItems(mobOid, templateList);
            //if (itemList.contains(null)) {
            //    return null;
            //}
            for (OID itemOid : itemList) {
                if (itemOid != null)
                    removeItem(mobOid, itemOid, removeFromPlayer);
            }
            // remove all the nulls from the list
            for (int i = itemList.size() - 1; i >= 0; i--) {
                if (itemList.get(i) == null)
                    itemList.remove(i);
            }
            return itemList;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Removes all items from the players inventory that match one of the template IDs specified.
     */
    protected ArrayList<OID> removeAllItems(OID mobOid) {
        lock.lock();
        try {
            if (Log.loggingDebug)
                log.debug("removeAllItems: mobOid=" + mobOid);
            ArrayList<OID> itemList = new ArrayList<OID>();
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            OID[] subBags = iInfo.getBags();
            for (int pos = 0; pos < subBags.length; pos++) {
                if (pos >= INVENTORY_BAG_COUNT)
                    break;
                Bag subBag = getBag(subBags[pos]);
                for(OID itemOID : subBag.getItemsList()) {
                    if (itemOID != null)
                        itemList.add(itemOID);
                }
            }
            for (OID itemOid : itemList) {
                if (itemOid != null)
                    removeItem(mobOid, itemOid, true);
            }
            return itemList;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Activates the item. Depending on the activation type the item has it may cause
     * the item to be equipped, start an ability, or do some other action.
     */
    protected boolean activateObject(OID objOid, OID activatorOid, OID targetOid) {
        AgisItem item = getAgisItem(objOid);
        if (item == null) {
            Log.warn("ActivateHook: item is null, oid=" + objOid);
            return false;
        }

        return item.activate(activatorOid, targetOid);
    }

    class EquipItemInSlotHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage getMsg = (ExtensionMessage)msg;
            OID mobOid = getMsg.getSubject();
            OID itemOid = (OID) getMsg.getProperty("itemOid");
            String slotName = (String) getMsg.getProperty("slotName");
            //  slotName = slotName.toLowerCase();

            AgisItem item = getAgisItem(itemOid);
            if (item == null) {
                Log.warn("EquipItemInSlotHook: item is null, oid=" + itemOid);
                return true;
            }
            Log.debug("EquipItemInSlotHook: got equip item: " + itemOid + " for slot: " + slotName);
            
            /*if (slotName.equals("main ring")) {
                slotName = "primaryRing";
            } else if (slotName.equals("off ring")) {
                slotName = "secondaryRing";
            } else if (slotName.equals("ranged")) {
                slotName = "rangedWeapon";
            } else if (slotName.equals("main hand")) {
                slotName = "primaryWeapon";
            } else if (slotName.equals("off hand")) {
                slotName = "secondaryWeapon";
            } else if (slotName.equals("main earring")) {
                slotName = "primaryEarring";
            } else if (slotName.equals("off earring")) {
                slotName = "secondaryEarring";
            } else  */

            // }



            // Check that the slot is valid for the item
            if (AgisEquipInfo.getEquipInfo(slotName) == null) {
                EventMessageHelper.SendErrorEvent(mobOid, EventMessageHelper.ERROR_NO_EQUIP_SLOT, 0, slotName);
                return true;
            }
            
         /*   if (AgisEquipSlot.getSlotByName(slotName) == null) {
                EventMessageHelper.SendErrorEvent(mobOid, EventMessageHelper.ERROR_NO_EQUIP_SLOT, 0, slotName);
                return true;
            }*/


            //  AgisEquipInfo.getEquipInfo(slotName);

            AgisEquipSlot slot = AgisEquipSlot.getSlotByName(slotName);
            Log.debug("EquipItemInSlotHook: slot="+slot+" slotName="+slotName);
            if (!item.equipSlotExists(slot)) {
                EventMessageHelper.SendErrorEvent(mobOid, EventMessageHelper.ERROR_WRONG_EQUIP_SLOT, item.getTemplateID(), slotName);
                return true;
            }
            if (item.getItemType().equals("Ammo")) {
                int t = RequirementChecker.getIdEditorOptionChoice("Item Slot Type", "Ammo");
                if (slot.getTypeIds().contains(t)) {
                    log.debug("Activate Ammo");
                    item.activate(mobOid, mobOid);
                    log.debug("Activated Ammo");
                    return true;
                }
            }

            equipItem(item, mobOid, true, slot);

            return true;
        }
    }

    class EquipItemInPetSlotHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage getMsg = (ExtensionMessage) msg;
            OID ownerOid = getMsg.getSubject();
            OID itemOid = (OID) getMsg.getProperty("itemOid");
            String slotName = (String) getMsg.getProperty("slotName");
            int profile = (int) getMsg.getProperty("profile");

            AgisItem item = getAgisItem(itemOid);
            if (item == null) {
                Log.warn("EquipItemInPetSlotHook: item is null, oid=" + itemOid);
                return true;
            }
            Log.debug("EquipItemInPetSlotHook: got equip item: " + itemOid + " for slot: " + slotName + " profile=" + profile);


            AtavismDatabase adb = new AtavismDatabase();
            PetInfo pi = adb.loadPlayerPet(ownerOid, profile);
            Log.error("EquipItemInPetSlotHook PetInfo " + pi);
            PetProfile pp = Agis.PetProfile.get(profile);
            Log.error("EquipItemInPetSlotHook PetProfile " + pp);
            int slotsProfileId = pp.GetLevels().get(pi.level).getSlotsProfileId();
            AgisEquipInfoProfile slotProfile = AgisInventoryPlugin.slotsProfiles.get(slotsProfileId);

            List<AgisEquipSlot> slots = slotProfile.getEquippableSlots();
            boolean foundSlot = false;
            for (AgisEquipSlot slot : slots) {
                if(slot.getName().equals(slotName))
                    foundSlot = true;
            }

            if(!foundSlot){
                log.error("EquipItemInPetSlotHook: Cant equip item to slot that pet doesnt have");
                EventMessageHelper.SendErrorEvent(ownerOid, EventMessageHelper.ERROR_NO_EQUIP_SLOT, 0, slotName);
                return true;
            }


            // Check that the slot is valid for the item
            if (AgisEquipInfo.getEquipInfo(slotName) == null) {
                EventMessageHelper.SendErrorEvent(ownerOid, EventMessageHelper.ERROR_NO_EQUIP_SLOT, 0, slotName);
                return true;
            }
            AgisEquipSlot slot = AgisEquipSlot.getSlotByName(slotName);
            Log.debug("EquipItemInSlotHook: slot="+slot+" slotName="+slotName);
            if (!item.equipSlotExists(slot)) {
                EventMessageHelper.SendErrorEvent(ownerOid, EventMessageHelper.ERROR_WRONG_EQUIP_SLOT, item.getTemplateID(), slotName);
                return true;
            }


            equipItem(item, ownerOid, true, slot, profile);

            return true;
        }
    }

    /**
     * Checks if the item specified is equipped by the player.
     * @param itemObj
     * @param activatorOid
     * @return
     */
    public boolean isItemEquipped(AOObject itemObj, OID activatorOid) {
        AgisItem item = AgisItem.convert(itemObj);
    	log.debug("isItemEquipped: activatorOid="+activatorOid+" item="+item);
		AgisEquipInfo oaei = item.getEquipInfo();
        EquipMap equipMap = getEquipMap(activatorOid);
		if (oaei != null) {
			if (oaei.slotsCount() > 0) {
				List<AgisEquipSlot> oslots = oaei.getEquippableSlots();
				for (AgisEquipSlot oaes : oslots) {
					OID oItemOid = equipMap.get(oaes);
					log.debug("isItemEquipped: oItemOid=" + oItemOid);
                    if (oItemOid != null && oItemOid.equals(item.getOid())) {
                        Log.debug("EQUIP: got isEquipped item: " + oItemOid);
                        return true;
                    }
				}
			}
		}
        log.debug("isItemEquipped: end not Equipped");
        return false;
    }

    public boolean equipItem(AOObject itemObj, OID activatorOid, boolean replace) {
        return equipItem(itemObj, activatorOid, replace, null,-1);
    }
    public boolean equipItem(AOObject itemObj, OID activatorOid, boolean replace, AgisEquipSlot targetSlot) {
        return equipItem(itemObj, activatorOid, replace, targetSlot,-1);
    }

    /**
     * Equips the item to its primary slot on the player/mob. The replace
     * parameter is used to unequip an existing item if there is one in the 
     * slot already. If it is set to false and there is an item in the slot, 
     * the new item will not be equipped.
     */
    public boolean equipItem(AOObject itemObj, OID activatorOid, boolean replace, AgisEquipSlot targetSlot, int petProfile  ) {
        if (Log.loggingDebug)
            log.debug("AgisInventoryPlugin.equipItem: item=" + itemObj + ", activatorOid=" + activatorOid+" replace="+replace+" targetSlot="+targetSlot);

        AgisItem item = AgisItem.convert(itemObj);
        AgisItem unequippedItem = null;

        OID itemInBag = (OID) item.getProperty(AgisInventoryPlugin.INVENTORY_PROP_BACKREF_KEY);
        if (itemInBag == null) {
            log.error("AgisInventoryPlugin.equipItem: item=" + item + ", activatorOid=" + activatorOid + " itemInBag=" + itemInBag + " reference for bag can not be null break");
            return false;
        }
        InventoryInfo iInfo = getInventoryInfo(activatorOid);
        if (iInfo == null) {
            log.error("AgisInventoryPlugin.equipItem: item=" + item + ", activatorOid=" + activatorOid + " iInfo=" + iInfo + " can not be null break");
            return false;
        }
        OID[] plyBags = iInfo.getBags();
        if (Log.loggingDebug)
            log.debug("AgisInventoryPlugin.equipItem: plyBags="+plyBags);
        boolean itemFoundInBag = false;
        for (int pos = 0; pos < plyBags.length; pos++) {
            OID subBagOid = plyBags[pos];
            Bag subBag = getBag(subBagOid);
            if (subBag.findItem(item.getOid()) != null) {
                if (subBagOid.equals(itemInBag)) {
                    itemFoundInBag = true;
                }
            }
        }
        if (!itemFoundInBag) {
            log.error("AgisInventoryPlugin.equipItem: item=" + item + ", activatorOid=" + activatorOid + " itemInBag=" + itemInBag + " itemFoundInBag=" + itemFoundInBag + " not found reference in bag break");
            return false;
        }

        // TODO: Add checking requirements for pet
        if (petProfile > 0) {
            HashMap<Integer, HashMap<String, Integer>> requirements = (HashMap<Integer, HashMap<String, Integer>>) item.getProperty("requirements");
            RequirementCheckResult canUse = RequirementChecker.DoesPlayerMeetRequirements(activatorOid, requirements);
            if (!canUse.result.equals(RequirementCheckResult.RESULT_SUCCESS)) {
                EventMessageHelper.SendRequirementFailedEvent(activatorOid, canUse);
                return false;
            }
        }
        if(item.getProperty("maxDurability") != null) {
           int durability = (int) item.getProperty("durability");
           int maxDurability = (int)item.getProperty("maxDurability");
           if (Log.loggingDebug)log.debug("AgisInventoryPlugin.equipItem: item=" + item + ", activatorOid=" + activatorOid + " itemInBag=" + itemInBag + " itemFoundInBag=" + itemFoundInBag + " durability "+durability+"/"+maxDurability);
           if(maxDurability>0 && durability==0) {
        	   EventMessageHelper.SendErrorEvent(activatorOid, EventMessageHelper.INVALID_ITEM, 0, "");
        	   return false;
           }
        }
        // is activator allowed to use the item
        // for now, ignore if the item has no callback
        PermissionCallback cb = item.permissionCallback();
        if ((cb != null) && (!cb.use(activatorOid))) {
            log.warn("AgisInventoryPlugin.equipItem: permission callback failed");
            return false;
        }

        // get the primary slot for the item
        AgisEquipSlot slot = item.getPrimarySlot();
        AgisEquipInfo aei = item.getEquipInfo();
        if (targetSlot != null) {
            slot = targetSlot;
        }
        if (slot == null) {
            Log.warn("AgisInventoryPlugin.equipItem: slot is null for item: " + item);
            return false;
        }
        if (Log.loggingDebug)
            log.debug("AgisInventoryPlugin.equipItem: slot ="+slot);
        
        EquipMap equipMap;
        lock.lock();
        try {
            if(petProfile > 0){
                equipMap = iInfo.getPetInventory().get(petProfile).getEquipMap();
            } else {
                equipMap = getEquipMap(activatorOid);
            }
            //checking if a unique item is worn  
            if (item.getProperty("isUnique") != null) {
                boolean isUnique = (boolean) item.getProperty("isUnique");
                log.debug("equipItem isUnique=" + isUnique);
                if (isUnique) {
                    int templateId = item.getTemplateID();
                    for (OID oid : equipMap.getEquipMap().values()) {
                        if (oid != null) {
                            AgisItem oItemObj = getAgisItem(oid);
                            if (oItemObj != null) {
                                if (oItemObj.getTemplateID() == templateId) {
                                    EventMessageHelper.SendErrorEvent(activatorOid, EventMessageHelper.EQUIP_FAIL_ITEM_UNIQUE, 0, "");
                                    log.debug("AgisInventoryPlugin.equipItem item:" + item.getOid() + " Templ:" + item.getTemplateID() + " isUnique=" + isUnique + " in Equiperd List found item:" + oid + " Templ:" + oItemObj.getTemplateID()
                                            + " therefore item can not be equiped");
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
              // is the slot free?
            OID oItemOid = equipMap.get(slot);
            if(Log.loggingDebug)  log.debug("AgisInventoryPlugin.equipItem: item in primary slot: " + slot + " = " + oItemOid);
            
            if (oItemOid != null && !replace) {
                EventMessageHelper.SendErrorEvent(activatorOid, EventMessageHelper.EQUIP_FAIL, 0, "");
                if (Log.loggingDebug)
                    log.debug("AgisInventoryPlugin.equipItem: slot occupied and not set to replace - returning");
                return false;
            }
            String itemSlot = (String) item.getProperty("slot");
            if (Log.loggingDebug)
                log.debug("AgisInventoryPlugin.equipItem: EQUIPITEM itemSlot="+itemSlot);
            // If the item is a two handed one, unequip the secondary weapon as well
            if (aei.slotsCount() > 1 && aei.getAll()) {
                List<AgisEquipSlot> slots = aei.getEquippableSlots();
                for (AgisEquipSlot aes : slots) {
                    OID oItemOid2 = equipMap.get(aes);
                    if (oItemOid2 != null) {
                        if (replace) {
                            AgisItem oItemObj = getAgisItem(oItemOid2);
                            if (!unequipItem(oItemObj, activatorOid, false, null, -1, false, false, petProfile)) {
                                log.debug("AgisInventoryPlugin.equipItem: the offHand item could not be removed break equip");
                                return false;
                            }
                        } else {
                            // TODO: Re-equip the first item
                            return false;
                        }
                    }
                }
            
                List<AgisEquipSlot> weaponSlots = AgisEquipSlot.getSlotsList();
                for (AgisEquipSlot aes : weaponSlots) {
                    if (!slots.contains(aes)) {
                        OID oItemOid2 = equipMap.get(aes);
                        if (oItemOid2 != null) {
                            AgisItem oItemObj = getAgisItem(oItemOid2);
                            AgisEquipInfo oaei = oItemObj.getEquipInfo();
							log.debug("AgisInventoryPlugin.equipItem AgisEquipInfo oaei="+oaei+" replace="+replace);
                            if (oaei.slotsCount() > 1 && oaei.getAll()) {
                                List<AgisEquipSlot> oslots = oaei.getEquippableSlots();
                                for (AgisEquipSlot oaes : oslots) {
									log.debug("AgisInventoryPlugin.equipItem slots="+slots+" oaes="+oaes+" replace="+replace);
                                    if (slots.contains(oaes)) {
                                        if (replace) {
                                            if (!unequipItem(oItemObj, activatorOid, false, null, -1, false, false, petProfile)) {
                                                log.debug("AgisInventoryPlugin.equipItem: the offHand item could not be removed break equip");
                                                return false;
                                            }
                                        } else {
                                            // TODO: Re-equip the first item
                                            return false;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            

           
            // If this item goes in the secondary weapon slot, check if the player has an two hand item

              if(Log.loggingDebug) 
                  log.debug("AgisInventoryPlugin.equipItem: slot: " + slot );
            OID slotItemOid = equipMap.get(slot);
            //
            
            
            if (aei.slotsCount() >= 1 && !aei.getAll()) {
                List<AgisEquipSlot> slots = aei.getEquippableSlots();
                if (slotItemOid != null) {
                    for (AgisEquipSlot aes : slots) {
                        OID oItemOid3 = equipMap.get(aes);
                        if (oItemOid3 == null) {
                            slot = aes;
                            if (Log.loggingDebug)
                                log.debug("AgisInventoryPlugin.equipItem: set slot to: " + slot);
                            oItemOid = equipMap.get(slot);
                        }
                    }
                }
                List<AgisEquipSlot> weaponSlots = AgisEquipSlot.getSlotsList();
                for (AgisEquipSlot aes : weaponSlots) {
                    // if (!slots.contains(aes)) {
                    OID oItemOid2 = equipMap.get(aes);
                    if (oItemOid2 != null) {
                        AgisItem oItemObj = getAgisItem(oItemOid2);
                        AgisEquipInfo oaei = oItemObj.getEquipInfo();
						log.debug("AgisInventoryPlugin.equipItem AgisEquipInfo oaei=" + oaei + " replace=" + replace);
                        if (oaei.slotsCount() > 1 && oaei.getAll()) {
                            List<AgisEquipSlot> oslots = oaei.getEquippableSlots();
                            for (AgisEquipSlot oaes : oslots) {
								log.debug("equipItem slots=" + slots + " oaes=" + oaes + " replace=" + replace);
                                if (slot.equals(oaes)) {
                                    if (replace) {
                                        if (!unequipItem(oItemObj, activatorOid, false, null, -1, false, false, petProfile)) {
                                            log.debug("AgisInventoryPlugin.equipItem: the offHand item could not be removed break equip");
                                            return false;
                                        }
                                    } else {
                                        // TODO: Re-equip the first item
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                    // }
                }
            }
            //}
              if(Log.loggingDebug) 
                  log.debug("AgisInventoryPlugin.equipItem: slot: " + slot );
           
            Bag sourceBag = null;
            int sourceSlot = -1;
            if (oItemOid != null) {
                // Get current bag and slot of the item we are equipping so the currently
                // equipped item can go in that slot
                // InventoryInfo iInfo = getInventoryInfo(activatorOid);
                OID[] subBags = iInfo.getBags();
                for (int pos = 0; pos < subBags.length; pos++) {
                    Bag subBag = getBag(subBags[pos]);
                    Integer slotNum = subBag.findItem(item.getOid());
                    if (slotNum != null) {
                        sourceBag = subBag;
                        sourceSlot = slotNum;
                    }
                }

                if (sourceBag == null) {
                    // Somehow failed to find the bag where the equipped item is currently located
                    return false;
                }
            }

            // Remove the item from the normal bag and place it in the equipped bag
            OID itemOid = removeItem(activatorOid, item.getOid(), false);

            // Unequip the item putting it into the source bag/slot
            if (oItemOid != null) {
                AgisItem oItemObj = getAgisItem(oItemOid);
                if (Log.loggingDebug)
                    log.debug("AgisInventoryPlugin.equipItem: slot occupied 4");
                if (!unequipItem(oItemObj, activatorOid, true, sourceBag, sourceSlot, false, true, petProfile)) { // *************************
                    // The unequip failed, return the item back to it's original slot
                    log.debug("AgisInventoryPlugin.equipItem - unequip failed, returning item to bag");
                    addItemHelper(activatorOid, sourceBag.getOid(), sourceSlot, item);
                    return false;
                }
                unequippedItem = oItemObj;
            }
            OID[] eitems ;
            if(petProfile > 0){
                OID equpBagOid = iInfo.getPetInventory().get(petProfile).getEquipmentItemBag();
                Bag equipBag = getBag(equpBagOid);
                eitems = equipBag.getItemsList();
                if (Log.loggingDebug) log.debug("AgisInventoryPlugin.equipItem: | pet equip=" + Arrays.toString(eitems));
            } else {
                OID equpBagOid = iInfo.getEquipmentItemBag();
                Bag equipBag = getBag(equpBagOid);
                eitems = equipBag.getItemsList();
                if (Log.loggingDebug) log.debug("AgisInventoryPlugin.equipItem: | equip=" + Arrays.toString(eitems));
            }

            SlotsSet ss = AgisInventoryPlugin.slotsSets.get(iInfo.getItemSetSelected());
            if(petProfile > 0){
                //Pet dont have sets
                 ss = null;
            }
            if( Log.loggingDebug)Log.debug("AgisInventoryPlugin.equipItem selected Set"+iInfo.getItemSetSelected()+" sets "+ AgisInventoryPlugin.slotsSets.keySet()+" ss="+ss);
            if(ss != null) {
                int race = -1;
                int aspect = -1;
                try {
                    race = (Integer) EnginePlugin.getObjectProperty(activatorOid, WorldManagerClient.NAMESPACE, "race");
                    aspect = (Integer) EnginePlugin.getObjectProperty(activatorOid, CombatClient.NAMESPACE, "aspect");
                } catch (Exception e) {

                }
                ArrayList<AgisEquipSlot> slotList = ss.getSlotsForRaceClass(race, aspect);
                if (slotList.size() > 0) {
                    boolean foundSlot = false;
                    for (AgisEquipSlot _slot : slotList) {
                        if (_slot.equals(slot)) {
                            foundSlot = true;
                        }
                    }
                    if (foundSlot) {
                        OID setBagOid = iInfo.getEquippedItemsSetBag(iInfo.getItemSetSelected());
                        Bag setBag = getBag(setBagOid);
                        if (setBag != null) {
                            boolean rv = setBag.putItem(slot.getId(), itemOid);
                            Log.debug("AgisInventoryPlugin.equipItem: add item to set bag " + rv);
                        }
                    }
                } else {
                    if (Log.loggingDebug)
                        Log.debug("SwitchWeaponHook: playerOid: " + activatorOid + " _switch=" + iInfo.getItemSetSelected() + " SlotsSet no slot definition for race " + race + " and class " + aspect);
                    return true;
                }
            } else{
                if( Log.loggingDebug)Log.debug("AgisInventoryPlugin.equipItem "+ AgisInventoryPlugin.slotsSets.keySet()+" set not found for "+iInfo.getItemSetSelected());
            }





//            if(set_slot!=null){
//                placeEquippedItem(activatorOid, itemOid, set_slot);
//                equipMap.put(set_slot, itemOid);
//            }
//            eitems = equipBag.getItemsList();
            if( Log.loggingDebug)log.debug("AgisInventoryPlugin.equipItem: || equip="+Arrays.toString(eitems));
            // place it in the equipped bag
            placeEquippedItem(activatorOid, itemOid, slot, petProfile);


            if(petProfile > 0){
                OID equpBagOid = iInfo.getPetInventory().get(petProfile).getEquipmentItemBag();
                Bag equipBag = getBag(equpBagOid);
                eitems = equipBag.getItemsList();
                if (Log.loggingDebug) log.debug("AgisInventoryPlugin.equipItem: | pet equip=" + Arrays.toString(eitems));
            } else {
                OID equpBagOid = iInfo.getEquipmentItemBag();
                Bag equipBag = getBag(equpBagOid);
                eitems = equipBag.getItemsList();
                if (Log.loggingDebug) log.debug("AgisInventoryPlugin.equipItem: | equip=" + Arrays.toString(eitems));
            }
            item.itemEquipped(activatorOid);
            
            // If there is an incompatible ammo type, remove it
            Integer ammoTypeReq = (Integer) item.getProperty(AgisItem.AMMO_TYPE);
            if (ammoTypeReq != null) {
                Integer ammoID = (Integer) EnginePlugin.getObjectProperty(activatorOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_LOADED);
                if (ammoID != null && ammoID > 0) {
                    // get type of ammo and compare it to the req
                    Template itemTemplate = ObjectManagerPlugin.getTemplate(ammoID, ObjectManagerPlugin.ITEM_TEMPLATE);
                    Integer ammoType = (Integer) itemTemplate.get(InventoryClient.ITEM_NAMESPACE, AgisItem.AMMO_TYPE);
                    if (ammoType != ammoTypeReq) {
                        // Clear out ammo settings
                        if (Log.loggingDebug)
                            Log.debug("AgisInventoryPlugin.equipItem AMMO: got incompatible ammo: " + ammoType + "from ammoID: " + ammoID + " when " + ammoTypeReq + " is needed");
                        EnginePlugin.setObjectPropertiesNoResponse(activatorOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_LOADED, -1);
                        EnginePlugin.setObjectPropertiesNoResponse(activatorOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_DAMAGE, 0);
                    }
                }
            }

            // place object in slot
            equipMap.put(slot, item.getMasterOid());
            setDirty(activatorOid);
        } finally {
            lock.unlock();
        }
        
        HashMap<Integer, Integer> list = new HashMap<Integer, Integer>();


        if(petProfile > 0){
            list =  iInfo.getPetInventory().get(petProfile).getEquipSetsInfo();
            for(OID petOid : iInfo.getPetInventory().get(petProfile).getPets()) {
                AgisInventoryClient.itemEquipStatusChanged(petOid, item, unequippedItem, slot.toString(), list);
            }
        } else {
            list = getEquipSetsInfo(activatorOid);
            AgisInventoryClient.itemEquipStatusChanged(activatorOid, item, unequippedItem, slot.toString(), list);
        }

        if (Log.loggingDebug)
            log.debug("AgisInventoryPlugin.equipItem: calling addDC, activatorOid=" + activatorOid + ", item=" + item);

        String displayVal = (String) item.getProperty("displayVal");
        String unicItem = item.getTemplateID() + "";
        if ((int) item.getProperty("enchantLevel") > 0)
            unicItem += ";E" + item.getProperty("enchantLevel");

        HashMap<String, Integer> bstats = (HashMap) item.getProperty("bonusStats");
        for (String statName : bstats.keySet()) {
            unicItem += ";B" + statName + "|" + bstats.get(statName);
        }

        if (item.getProperty("enchantStats") != null) {
            HashMap<String, Integer> estats = (HashMap) item.getProperty("enchantStats");
            for (String statName : estats.keySet()) {
                if (bstats.containsKey(statName)) {
                    if (estats.get(statName) - bstats.get(statName) != 0) {
                        unicItem += ";T" + statName + "|" + (estats.get(statName) - bstats.get(statName));
                    }
                } else {
                    unicItem += ";T" + statName + "|" + estats.get(statName);
                }
            }
        }
        
        HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) item.getProperty("sockets");
        ArrayList<String> socketItems = new ArrayList<String>();
        for (Integer sId : itemSockets.keySet()) {
            if (itemSockets.get(sId).GetItemOid() != null) {
                AgisItem sitem = getAgisItem(itemSockets.get(sId).GetItemOid());
                socketItems.add(itemSockets.get(sId).GetType() + "|" + sId + "|" + sitem.getTemplateID());
            }
        }
        // Collections.sort(socketItems);
        for (String l : socketItems) {
            unicItem += ";S" + l;
        }

        if(petProfile > 0){
            for(OID petOid : iInfo.getPetInventory().get(petProfile).getPets()) {
                EquipHelper.updateDisplay(petOid, displayVal, slot, unicItem);
            }
            iInfo.getPetInventory().get(petProfile).sendEquippedInvUpdate();
        } else {
            EquipHelper.updateDisplay(activatorOid, displayVal, slot, unicItem);
            Log.debug("AgisInventoryPlugin.equipItem: Send Update");
        }
        Log.debug("AgisInventoryPlugin.equipItem: Send Update");
        sendInvUpdate(activatorOid);
        Log.debug("AgisInventoryPlugin.equipItem: END");
        return true;
    }

    public boolean unequipItem(AOObject itemObj, OID activatorOid, boolean isReplaced) {
        return unequipItem(itemObj, activatorOid, isReplaced, null, -1, false);
    }
    
    public boolean unequipItem(AOObject itemObj, OID activatorOid, boolean isReplaced, boolean destroyItem) {
        return unequipItem(itemObj, activatorOid, isReplaced, null, -1, destroyItem);
    }

    public boolean unequipItem(AOObject itemObj, OID activatorOid, boolean isReplaced, Bag targetBag, int slotNum, boolean destroyItem) {
        return unequipItem(itemObj, activatorOid, isReplaced, targetBag, slotNum, destroyItem, true,-1);
    }
    /**
     * Unequips the specified item. If isReplaced is set to true no display update
     * message will be sent as one will be sent when the new item is equipped.
     */
    public boolean unequipItem(AOObject itemObj, OID activatorOid, boolean isReplaced, Bag targetBag, int slotNum, boolean destroyItem, boolean sendUpdate ,int petProfile) {
        if(Log.loggingDebug)  log.debug("AgisInventoryPlugin.unequipItem: item=" + itemObj
                + ", mobOid=" + activatorOid);

        if (itemObj == null) {
            Log.debug("EQUIP: trying to unequip a null item. Will return true for the time being so a new item can replace this one");
            if (isReplaced) {
                return true;
            } else {
                return false;
            }
        }
        AgisItem item = AgisItem.convert(itemObj);

        // is activator allowed to use the item
        // for now, ignore if the item has no callback
        PermissionCallback cb = item.permissionCallback();
        if ((cb != null) && (!cb.use(activatorOid))) {
            log.warn("callback failed");
            return false;
        }

        lock.lock();
        try {
            // First check if there is an open slot for the item
            InventoryInfo iInfo = getInventoryInfo(activatorOid);
            // where is this currently equipped
            EquipMap equipMap ;
            if(petProfile>0) {
                equipMap = iInfo.getPetInventory().get(petProfile).getEquipMap();
            }else {
                equipMap = getEquipMap(activatorOid);
            }
            AgisEquipSlot slot = equipMap.getSlot(item.getMasterOid());
            if (slot == null) {
                // item is not currently equipped
                Log.warn("AgisInventoryPlugin.unequipItem: item not equipped: item=" + item);
                return true;
            }

            boolean hasSpace = hasSpace(activatorOid, item.getTemplateID(), 1, 0);

            if (hasSpace || (isReplaced && targetBag != null) || destroyItem) {
                // remove the item from the map
                equipMap.remove(slot);
                // Remove the item from the equipped bag and place it back in a normal spot
                // We probably should add in a check to make sure the bag has an empty spot to place the item in
                removeEquippedItem(activatorOid, item.getOid(), slot, petProfile);
                Log.debug("AgisInventoryPlugin.unequipItem: set slot 'Set_"+iInfo.getItemSetSelected() + "_" + slot.getName()+"'");
                AgisEquipSlot set_slot = AgisEquipSlot.getSlotByName("Set_"+iInfo.getItemSetSelected() + "_" + slot.getName());

                OID setBagOid = iInfo.getEquippedItemsSetBag(iInfo.getItemSetSelected());
                Bag setBag = getBag(setBagOid);
                if(setBag!=null){
                    boolean rv = setBag.removeItem(slot.getId());
                    Log.debug("AgisInventoryPlugin.unequipItem: remove item form set bag "+rv);
                }
                if(set_slot != null){
                    Log.debug("AgisInventoryPlugin.unequipItem: set slot not null");
                    equipMap.remove(set_slot);
                    removeEquippedItem(activatorOid, item.getOid(), set_slot,petProfile);
                }else{
                    Log.debug("AgisInventoryPlugin.unequipItem: set slot is null");

                }

                if (!destroyItem) {
                    if (targetBag != null) {
                        addItemHelper(activatorOid, targetBag.getOid(), slotNum, item);
                        Engine.getPersistenceManager().setDirty(iInfo);
                    } else {
                        addItem(activatorOid, iInfo.getOid(), item.getOid());
                    }
                }
                setDirty(activatorOid);
                if (!isReplaced) {
                    // Only send equip status changed if the item isn't being replaced, otherwise equipItem will handle it
                    HashMap<Integer, Integer> list = new HashMap<Integer, Integer>();
                    if(petProfile > 0){
                        list =  iInfo.getPetInventory().get(petProfile).getEquipSetsInfo();
                        for(OID petOid : iInfo.getPetInventory().get(petProfile).getPets()) {
                            AgisInventoryClient.itemEquipStatusChanged(petOid, null, item, slot.toString(), list);
                        }
                    } else {
                        list = getEquipSetsInfo(activatorOid);
                        AgisInventoryClient.itemEquipStatusChanged(activatorOid, null, item, slot.toString(), list);
                    }
                }
                if (!isReplaced) {
                     if(petProfile > 0){
                        for(OID petOid : iInfo.getPetInventory().get(petProfile).getPets()) {
                            EquipHelper.updateDisplay(petOid, null, slot);
                        }
                        iInfo.getPetInventory().get(petProfile).sendEquippedInvUpdate();
                    } else {
                        EquipHelper.updateDisplay(activatorOid, null, slot);
                        Log.debug("AgisInventoryPlugin.equipItem: Send Update");
                    }
                }
                if (Log.loggingDebug)
                    log.debug("AgisInventoryPlugin.unequipItem: removed DC for item:" + item);
            }
            if(!hasSpace) {
                log.warn("No space for item");
                return false;
            }

            if (Log.loggingDebug)
                log.debug("AgisInventoryPlugin.unequipItem: item:" + item+" hasSpace="+hasSpace+" isReplaced="+isReplaced+" sendUpdate="+sendUpdate+" destroyItem="+destroyItem);


            OID equpBagOid = iInfo.getEquipmentItemBag();
            Bag equipBag = getBag(equpBagOid);
            OID[]  eitems = equipBag.getItemsList();
            if( Log.loggingDebug)log.debug("AgisInventoryPlugin.unequipItem: |V equip="+Arrays.toString(eitems));
        } finally {
            lock.unlock();
        }

        if (!isReplaced) {
            if(sendUpdate) {
                sendInvUpdate(activatorOid);
            }
        }
        if( Log.loggingDebug)log.debug("AgisInventoryPlugin.unequipItem: END");
        return true;
    }
    
    class DoesInventoryHasSufficientSpaceHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.DoesInventoryHasSufficientSpaceMessage genMsg = (AgisInventoryClient.DoesInventoryHasSufficientSpaceMessage)msg;
            OID oid = genMsg.getSubject();
            HashMap<Integer, Integer> itemsNotGenerated = new HashMap<Integer, Integer>(genMsg.getItemsToGenerate());
            boolean hasSpace = hasSpace(oid, itemsNotGenerated, 0);
            Engine.getAgent().sendBooleanResponse(genMsg, hasSpace);
            return true;
        }
    }
    
    class EditLockHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage getMsg = (ExtensionMessage)msg;
            OID playerOid = getMsg.getSubject();
            String action = (String)getMsg.getProperty("action");
            int containerId = (Integer)getMsg.getProperty("bagNum");
            int slotId = (Integer)getMsg.getProperty("slotNum");
            OID lockOid = (OID)getMsg.getProperty("lockId");
            int lockNum = (Integer)getMsg.getProperty("lockNum");
            Boolean swap = (Boolean)getMsg.getProperty("swap");
            InventoryInfo iInfo = getInventoryInfo(playerOid);
            Bag chest = getBag(OID.fromString(iInfo.getActiveStorage()));
            if (chest == null) {
                log.warn("LOCK: Chest is not active!");
                return false;
            }
            
            if (action.equals("add")) {
                if(swap != null && swap) {
                    swapLockPositions(playerOid, lockOid, lockNum, containerId, slotId);
                } else {
                    removeSpecificItem(playerOid, lockOid, lockNum, true);
                    int extraLocks = chest.addChestLocks(lockNum, lockOid);
                    int count = 0;
                    if(extraLocks > 0) {
                        count++;
                        placeItem(playerOid, playerOid, lockOid, containerId, slotId);
                        for(int i = 0; i < extraLocks - 1; i++) {
                            count++;
                            placeItem(playerOid, playerOid, lockOid, containerId, slotId);
                        }
                    }
                    log.debug("LOCK: Sent " + count + "locks back!");
                }
            } else if (action.equals("remove")) {
                if(swap != null && swap) {
                    swapLockPositions(playerOid, lockOid, lockNum, containerId, slotId);
                } else {
                    chest.removeChestLocks(lockNum);
                    placeItem(playerOid, playerOid, lockOid, containerId, slotId);
                }
            }
            
            saveNonPlayerStorageBag(chest);
            sendBagInvUpdate(playerOid);
            sendBankInvUpdate(playerOid, iInfo.getActiveStorage(), OID.fromString(iInfo.getActiveStorage()),false);
            return true;
        }
    }
    
    /**
     * Swaps the bag/slots of two locks. Takes in the itemOid of the lock to move, then the bagNum and slotNum
     * to move it to, finds the item in that location then makes the switch.
     * @param playerOid
     * @param itemOid
     * @param itemAmount
     * @param bagNum
     * @param slotNum
     */
    public void swapLockPositions(OID playerOid, OID itemOid, int itemAmount, int bagNum, int slotNum) {
        InventoryInfo iInfo = getInventoryInfo(playerOid);
        if (iInfo == null || itemOid == null)
            return;
        OID[] subBags = iInfo.getBags();
        
        // Get the bag of the item being moved and the bag it is being moved to
        Bag subBag1 = getBag(OID.fromString(iInfo.getActiveStorage()));
        AgisItem item1 = getAgisItem(itemOid);
        
        if (subBag1 == null) {
            Log.debug("AgisInventoryPlugin.swapLockPositions failed to find subBag for item: " + itemOid);
            return;
        }
        
        OID subBag2Oid = subBags[bagNum];
        Bag subBag2 = getBag(subBag2Oid);
        OID itemOid2 = subBag2.getItem(slotNum);
        if (itemOid2 == null) {
            Log.debug("AgisInventoryPlugin.swapItemPositions no item found in bag: " + subBag2Oid + " slot: " + slotNum);
        }
        AgisItem item2 = getAgisItem(itemOid2);
        
        subBag1.removeChestLocks(itemAmount);
        if(itemAmount > 0) {
            placeItem(playerOid, playerOid, itemOid2, bagNum, slotNum);
            for(int i = 0; i < itemAmount - 1; i++) {
                placeItem(playerOid, playerOid, itemOid2, bagNum, slotNum);
            }
        }
        
        // Now put item 2 into slot 1
        int extraLocks = subBag1.addChestLocks(itemAmount, itemOid2);
        if(extraLocks > 0) {
            placeItem(playerOid, playerOid, itemOid2, bagNum, slotNum);
            for(int i = 0; i < extraLocks - 1; i++) {
                placeItem(playerOid, playerOid, itemOid2, bagNum, slotNum);
            }
        }
        item2.setProperty(INVENTORY_PROP_BACKREF_KEY, subBag1.getOid());
        Engine.getPersistenceManager().setDirty(item2);
        
        // And put item 1 into slot 2
        subBag2.putItem(slotNum, itemOid);
        item1.setProperty(INVENTORY_PROP_BACKREF_KEY, subBag2.getOid());
        Engine.getPersistenceManager().setDirty(item1);
        
        Engine.getPersistenceManager().setDirty(iInfo);
        
        sendBagInvUpdate(playerOid);
        sendBankInvUpdate(playerOid, subBag1.getOid().toString(), subBag1.getOid(),false);
    }
    
    /**
     * Checks if the player has enough space in their inventory for the items being added.
     * @param looterOid: the identifier of the person adding the items to their inventory
     * @param items: how many items are being added
     * @return: a boolean indicating whether or not the player has space
     */
    public boolean hasSpace(OID looterOid, HashMap<Integer, Integer> items, int invType) {
        int spaceNeeded = 0;
        int freeSpaces = 0;
        try {
            // get bag
            // find the looters root bag
            OID rootLooterBagOid = looterOid;
            if (rootLooterBagOid == null) {
                log.debug("lootAll: cant find rootLooterBagOid");
                return false;
            }
            InventoryInfo iInfo = getInventoryInfo(looterOid);
            // Normal bag(s)
            if (invType == 0) {
                for (int itemID : items.keySet()) {
                    int count = items.get(itemID);
                    // First check for existing stacks that we could add to
                    // Check if there are any existing stacks to add to
                    Template tmpl = ObjectManagerPlugin.getTemplate(itemID, ObjectManagerPlugin.ITEM_TEMPLATE);
                    int stackLimit = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "stackLimit");
                    ArrayList<OID> existingOids = findItemStacks(looterOid, itemID);
                    if (existingOids.size() > 0) {
                        Log.debug("ITEM: user already has item " + itemID + ", see if we can add it to one of the stacks.");
                        for (OID existingOid: existingOids) {
                            AgisItem tempItem = getAgisItem(existingOid);
                            Log.debug("ITEM: stackSize " + tempItem.getStackSize() + ", stackLimit: " + tempItem.getStackLimit() + "for item: " + existingOid);
                            if(tempItem.getStackSize() < tempItem.getStackLimit()) {
                                Log.debug("ITEM: reducing count in hasSpace for item: " + itemID);
                                count = count - (tempItem.getStackLimit() - tempItem.getStackSize());
                                if (count <= 0) {
                                    Log.debug("ITEM: hasSpace check has been fulfilled before creating new stacks for item: " + itemID);
                                    count = 0;
                                    break;
                                }
                            }
                        }
                    }
                    if (count > 0) {
                        spaceNeeded += ((count-1) / stackLimit) + 1;
                        Log.debug("ITEM: item " + itemID + " needs " + spaceNeeded + " with a count of " + count + " and stackLimit of " + stackLimit);
                    }
                }
                
                // check each subbag and see if it can be added there
                OID[] subBags = iInfo.getBags();
                for (int pos = 0; pos < subBags.length; pos++) {
                    OID subBagOid = subBags[pos];
                    Bag subBag = getBag(subBagOid);
                    if (subBag == null) {
                        Log.warn("hasSpace: did not find sub bag: " + subBagOid + " for bagoid=" + subBagOid);
                        continue;
                    }
                    int numSlots = subBag.getNumSlots();
                    for (int i = 0; i < numSlots; i++) {
                        if (subBag.getItem(i) == null) {
                            Log.debug("hasSpace: bag has free space at spot: " + i);
                            freeSpaces++;
                            if (freeSpaces >= spaceNeeded)
                                return true;
                        }   
                    }
                }
            }
        } finally {
        }
        Log.debug("hasSpace: freeSpaces = " + freeSpaces + "; spaceNeeded = " + spaceNeeded);
        if (freeSpaces >= spaceNeeded)
            return true;
        else
            return false;
    }
    
    /**
     * Checks if the player has enough space in their inventory for the items being added.
     * @param looterOid: the identifier of the person adding the items to their inventory
     * @param itemID: template id of item that being added
     * @param count: how many items are being added
     * @return: a boolean indicating whether or not the player has space
     */
    public boolean hasSpace(OID looterOid, int itemID, int count, int invType) {
        lock.lock();
        int spaceNeeded = 0;
        int freeSpaces = 0;
        try {
            // get bag
            // find the looters root bag
            OID rootLooterBagOid = looterOid;
            if (rootLooterBagOid == null) {
                log.debug("lootAll: cant find rootLooterBagOid");
                return false;
            }
            InventoryInfo iInfo = getInventoryInfo(looterOid);
            // Normal bag(s)
            if (invType == 0) {
                // First check for existing stacks that we could add to
                // Check if there are any existing stacks to add to
                Template tmpl = ObjectManagerPlugin.getTemplate(itemID, ObjectManagerPlugin.ITEM_TEMPLATE);
                int stackLimit = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "stackLimit");
                ArrayList<OID> existingOids = findItemStacks(looterOid, itemID);
                if (existingOids.size() > 0) {
                    Log.debug("ITEM: user already has item " + itemID + ", see if we can add it to one of the stacks.");
                    for (OID existingOid: existingOids) {
                        AgisItem tempItem = getAgisItem(existingOid);
                        Log.debug("ITEM: stackSize " + tempItem.getStackSize() + ", stackLimit: " + tempItem.getStackLimit() + "for item: " + existingOid);
                        if(tempItem.getStackSize() < tempItem.getStackLimit()) {
                            Log.debug("ITEM: reducing count in hasSpace for item: " + itemID);
                            count = count - (tempItem.getStackLimit() - tempItem.getStackSize());
                            if (count <= 0) {
                                Log.debug("ITEM: hasSpace check has been fulfilled before creating new stacks for item: " + itemID);
                                count = 0;
                                break;
                            }
                        }
                    }
                }
                if (count > 0) {
                    spaceNeeded = ((count-1) / stackLimit) + 1;
                    Log.debug("ITEM: item " + itemID + " needs " + spaceNeeded + " with a count of " + count + " and stackLimit of " + stackLimit);
                    // check each subbag and see if it can be added there
                    OID[] subBags = iInfo.getBags();
                    for (int pos = 0; pos < subBags.length; pos++) {
                        OID subBagOid = subBags[pos];
                        Bag subBag = getBag(subBagOid);
                        if (subBag == null) {
                            Log.warn("hasSpace: did not find sub bag: " + subBagOid + " for bagoid=" + subBagOid);
                            continue;
                        }
                        int numSlots = subBag.getNumSlots();
                        for (int i = 0; i < numSlots; i++) {
                            if (subBag.getItem(i) == null) {
                                Log.debug("hasSpace: bag has free space at spot: " + i);
                                freeSpaces++;
                                if (freeSpaces >= spaceNeeded)
                                    return true;
                            }   
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        Log.debug("hasSpace: freeSpaces = " + freeSpaces + "; spaceNeeded = " + spaceNeeded);
        if (freeSpaces >= spaceNeeded)
            return true;
        else
            return false;
    }
    
    /**
     * Places an equipped item in the correct slot that it belongs in
     * @param activatorOid
     * @param itemOid
     * @param slot
     * @return a boolean indicating whether or not the item was successfully placed
     */
    public boolean placeEquippedItem(OID activatorOid, OID itemOid, AgisEquipSlot slot , int petProfile) {
        if( Log.loggingDebug)Log.debug("placeEquippedItem: activatorOid="+activatorOid+" itemOid="+itemOid+" slot = " + slot);
        InventoryInfo iInfo = getInventoryInfo(activatorOid);
        OID subBagOid = null;
        if(petProfile > 0){
            subBagOid = iInfo.getPetInventory().get(petProfile).getEquipmentItemBag();
        }else {
            subBagOid = iInfo.getEquipmentItemBag();
        }
        Bag subBag = getBag(subBagOid);
        Entity item = getAgisItem(itemOid);
        if(subBag.getNumSlots()<EQUIP_SLOTS_COUNT) {
            subBag.changeNumSlots(EQUIP_SLOTS_COUNT);
        }
        int slotNum = slot.getId();

        if( Log.loggingDebug) Log.debug("placeEquippedItem: activatorOid="+activatorOid+" itemOid="+itemOid+" slot="+slot+" slotNum="+slotNum+" EQUIP_SLOTS_COUNT="+EQUIP_SLOTS_COUNT+" "+subBag.getNumSlots());
        if (slotNum == -1) {
            if( Log.loggingDebug)Log.debug("placeEquippedItem: slot id is -1 for activatorOid="+activatorOid+" itemOid="+itemOid+" slot="+slot);
            return false;
        }
        boolean rv = subBag.putItem(slotNum, itemOid);
        if (Log.loggingDebug)
            log.debug("placeEquippedItem: adding to bag, rv=" + rv);

        if (rv) {
            item.setProperty(INVENTORY_PROP_BACKREF_KEY, subBagOid);
        }

        // mark dirty
        Engine.getPersistenceManager().setDirty(item);
        Engine.getPersistenceManager().setDirty(iInfo);
        return rv;
        //return true;
    }
    
    /**
     * Removes an equipped item from the equipped items bag
     * @param activatorOid
     * @param itemOid
     * @param slot
     * @return a boolean indicating whether or not the item was successfully removed
     */
    public boolean removeEquippedItem(OID activatorOid, OID itemOid, AgisEquipSlot slot, int petProfile) {
        if (Log.loggingDebug)
            log.debug("removeItemFromBagHelper: activatorOid=" + activatorOid + " itemOid="+itemOid+" slot="+slot);
        InventoryInfo iInfo = getInventoryInfo(activatorOid);

        OID subBagOid = null;
        if(petProfile>0) {
            subBagOid = iInfo.getPetInventory().get(petProfile).getEquipmentItemBag();
        } else{
            subBagOid = iInfo.getEquipmentItemBag();
        }
        Bag subBag = getBag(subBagOid); 
        Entity item = getAgisItem(itemOid);
        OID sItemOid = subBag.getItem(slot.getId());
        if(!itemOid.equals(sItemOid)){
            if (Log.loggingDebug)
                log.debug("removeItemFromBagHelper: item not in bag itemOid=" + item.getOid() + " bagOid="+subBagOid + "in slot "+slot);
            return false;

        }

        // found the item
        if (Log.loggingDebug)
            log.debug("removeItemFromBagHelper: found - slot=" + slot.getId() + ", itemOid=" + item.getOid());
        
        // remove item from bag - we seperate the logic here from finding the item
        // because perhaps there was some other reason why the remove failed
        //boolean rv = subBag.removeItem(item.getOid());
        boolean rv = subBag.removeItem(slot.getId());
        if (rv == false) {
            if (Log.loggingDebug)
                log.debug("removeItemFromBagHelper: remove item failed");
            return false;
        }
        
        // remove the back reference
        item.setProperty(INVENTORY_PROP_BACKREF_KEY, null);

        // mark dirty
        Engine.getPersistenceManager().setDirty(item);
        Engine.getPersistenceManager().setDirty(iInfo);
        if (Log.loggingDebug)
            log.debug("removeItemFromBagHelper: remove from bag, rv=" + rv);
        return rv;
        //return true;
    }
    
    /*
     * Bag Functions
     */
    
    /**
     * Updates the properties for the bag in the specified location. It will get the Bag name and size etc from the item and set 
     * the bag to match those properties.
     * @param item: the item to place as a bag
     * @param mobOid: the oid of the player placing a bag
     * @param parentBagSlotNum: the slot to put the bag data in
     */
    private int changeBagInSlot(AgisItem item, OID mobOid, int parentBagSlotNum) {
        if (parentBagSlotNum == 0) {
            ExtendedCombatMessages.sendErrorMessage(mobOid, "You cannot swap out your Backpack.");
            return -1;
        }
        // Verify the item is a Bag
        Log.debug("BAG: changing bag in slot: " + parentBagSlotNum + " to item with type: " + item.getType());
        if (!item.getItemType().equals("Bag")) {
            return -1;
        }
        InventoryInfo iInfo = getInventoryInfo(mobOid);
        if (iInfo == null)
            return -1;
        OID[] subBags = iInfo.getBags();
        OID subBagOid = subBags[parentBagSlotNum];
        Bag subBag = getBag(subBagOid);
        // If the new bag is of equal size or bigger, automatically move the items to it
        Log.debug("BAG: bag has num slots: " + item.getProperty("numSlots"));
        int newNumSlots = (Integer)item.getProperty("numSlots");
        OID[] itemsInBag = subBag.getItemsList();
        log.debug("changeBagInSlot:  newNumSlots="+newNumSlots+" oldNumSlots="+subBag.getNumSlots());
        if (newNumSlots < subBag.getNumSlots()) {
            // Make sure the bag is empty before swapping.
            //TODO: Get the number of items in the old bag and see if they can be placed in the new one
            //Long[] oldItems = new Long;
            for (int i = 0; i < itemsInBag.length; i++) {
                Log.debug("BAG: checking items in bag for swap. Item: " + itemsInBag[i]);
                if (itemsInBag[i] != null) {
                    ExtendedCombatMessages.sendErrorMessage(mobOid, "This bag cannot hold all of the items in the bag you are currently using.");
                    return -1;
                }
            }
            Boolean result = removeItemFromBag(mobOid, item.getOid());
            if (result == false) {
                return -1;
            }
            subBag.setNumSlots(newNumSlots);
        } else {
            Boolean result = removeItemFromBag(mobOid, item.getOid());
            if (result == false) {
                return -1;
            }
            subBag.setNumSlots(newNumSlots);
            OID[] newItems = new OID[newNumSlots];
            for (int i = 0; i < itemsInBag.length; i++) {
                log.debug("changeBagInSlot: slot "+i+"; "+itemsInBag[i]+" = "+ item.getOid());
                if(itemsInBag[i] != null && itemsInBag[i].equals(item.getOid()))
                    newItems[i] = null;
                else
                    newItems[i] = itemsInBag[i];
            }
            log.debug("changeBagInSlot: newItems:"+Arrays.toString(newItems));
            subBag.setItemsList(newItems);
        }
        
        // get the item template id of the old bag so it can be recreated (if it is a valid item ID)
        int oldBagID = subBag.getItemTemplateID();
        // Now set the new settings
        subBag.setName(item.getName());
        subBag.setItemTemplateID(item.getTemplateID());
        Engine.getPersistenceManager().setDirty(iInfo);
        // return the old back ID
        return oldBagID;
    }
    
    /**
     * Handles the placeBagMessage. Places the specified Bag object in the specified slot.
     *
     */
    class PlaceBagHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.placeBagMessage removeMsg = (AgisInventoryClient.placeBagMessage)msg;
            OID oid = removeMsg.getSubject();
            inventoryCheck(oid);
            OID itemOid = (OID) removeMsg.getProperty("itemOid");
            int bagSpotNum = (Integer) removeMsg.getProperty("bagSpotNum");
            lock.lock();
            try {
                AgisItem item = getAgisItem(itemOid);
                if (item == null)
                    return true;
                OID rootBagOid = oid;
                if (rootBagOid == null) {
                    log.debug("placeBag: cant find rootBagOid");
                    return true;
                }
                 HashMap<Integer, HashMap<String, Integer>> requirements = (HashMap<Integer, HashMap<String, Integer>>) item.getProperty("requirements");
                 RequirementCheckResult canUse = RequirementChecker.DoesPlayerMeetRequirements(oid, requirements);
                 if (!canUse.result.equals(RequirementCheckResult.RESULT_SUCCESS)) {
                    EventMessageHelper.SendRequirementFailedEvent(oid, canUse);
                    return true;
                 }
                
                
                int oldBagID = changeBagInSlot(item, oid, bagSpotNum);
                if (oldBagID > 0) {
                    // Create an item to represent the old Bag
                    HashMap<Integer, Integer> itemsToGenerate = new HashMap<Integer, Integer>();
                    itemsToGenerate.put(oldBagID, 1);
                    AgisInventoryClient.generateItemsNoResponse(oid, itemsToGenerate, false);
                }
            } finally {
                lock.unlock();
            }
            sendBagInvUpdate(oid);
            return true;
        }
    }
    
    /**
     * Handles the MoveBagMessage. Swaps the properties for the bags in the 
     * specified locations. It will get the Bag name and size etc from the item 
     * and set the other bag to match those properties.
     * @author Andrew Harrison
     */
    class MoveBagHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.moveBagMessage genMsg = (AgisInventoryClient.moveBagMessage)msg;
            OID mobOid = genMsg.getSubject();
            inventoryCheck(mobOid);
              int parentBagSlotNum = (Integer) genMsg.getProperty("bagSpotNum");
            int newSlotNum = (Integer) genMsg.getProperty("newSpotNum");
            if (parentBagSlotNum == 0 || newSlotNum == 0) {
                ExtendedCombatMessages.sendErrorMessage(mobOid, "You cannot swap out your Backpack.");
                return true;
            }
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            if (iInfo == null)
                return true;
            
            OID[] subBags = iInfo.getBags();
            OID subBagOid1 = subBags[parentBagSlotNum];
            Bag subBag1 = getBag(subBagOid1);
            OID[] itemsInBag1 = subBag1.getItemsList();
            int bagTemplate1 = subBag1.getItemTemplateID();
            String bagName1 = subBag1.getName();
            int bagNumSlots1 = subBag1.getNumSlots();
            
            // Prevent moving bags if they have any items in them
            //TODO: get this working later - will require re-setting oid for each item
            for (OID itemOid : itemsInBag1) {
                if (itemOid != null) {
                    ExtendedCombatMessages.sendErrorMessage(mobOid, "You cannot move bags with items inside.");
                    return true;
                }
            }
        
            OID subBagOid2 = subBags[newSlotNum];
            Bag subBag2 = getBag(subBagOid2);
            OID[] itemsInBag2 = subBag2.getItemsList();
            int bagTemplate2 = subBag2.getItemTemplateID();
            String bagName2 = subBag2.getName();
            int bagNumSlots2 = subBag2.getNumSlots();
            
            for (OID itemOid : itemsInBag2) {
                if (itemOid != null) {
                    ExtendedCombatMessages.sendErrorMessage(mobOid, "You cannot move bags with items inside.");
                    return true;
                }
            }
            
            subBag1.setItemTemplateID(bagTemplate2);
            subBag1.setName(bagName2);
            subBag1.setNumSlots(bagNumSlots2);
            subBag1.setItemsList(itemsInBag2);
        
            subBag2.setItemTemplateID(bagTemplate1);
            subBag2.setName(bagName1);
            subBag2.setNumSlots(bagNumSlots1);
            subBag2.setItemsList(itemsInBag1);
        
            Engine.getPersistenceManager().setDirty(iInfo);
            sendBagInvUpdate(mobOid);
            return true;
        }
    }
    
    /**
     * Handles the RemoveBagMessage. Clears the bag data from the specified slot 
     * as if the bag was being removed. If a container and slot ID is given 
     * it will generate and place the bag item in that spot.
     * @author Andrew Harrison
     *
     */
    class RemoveBagHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.removeBagMessage genMsg = (AgisInventoryClient.removeBagMessage)msg;
            OID mobOid = genMsg.getSubject();
            inventoryCheck(mobOid);
              int parentBagSlotNum = (Integer) genMsg.getProperty("bagSpotNum");
            int containerId = (Integer) genMsg.getProperty("containerId");
            int slotId = (Integer) genMsg.getProperty("slotId");
            if (parentBagSlotNum == 0) {
                ExtendedCombatMessages.sendErrorMessage(mobOid, "You cannot swap out your Backpack.");
                return true;
            }
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            if (iInfo == null)
                return true;
            OID[] subBags = iInfo.getBags();
            OID subBagOid1 = subBags[parentBagSlotNum];
            Bag subBag1 = getBag(subBagOid1);
            OID[] itemsInBag1 = subBag1.getItemsList();
            for (int i = 0; i < itemsInBag1.length; i++) {
                Log.debug("BAG: checking items in bag for removal. Item: " + itemsInBag1[i]);
                if (itemsInBag1[i] != null) {
                    ExtendedCombatMessages.sendErrorMessage(mobOid, "You cannot remove a bag that has items inside it.");
                    return true;
                }
            }
            int bagID1 = subBag1.getItemTemplateID();
            String bagName1 = subBag1.getName();
            
            // If the container Id is not -1 then we need to place the item in the specified container/slot
            if (containerId != -1) {
                OID subBagOid2 = subBags[containerId];
                Bag subBag2 = getBag(subBagOid2);
                OID[] itemsInBag2 = subBag2.getItemsList();
                
                // Make sure the slot the item will be placed in is empty
                if (itemsInBag2.length < slotId || itemsInBag2[slotId] != null) {
                    ExtendedCombatMessages.sendErrorMessage(mobOid, "You must place the bag in an empty slot.");
                    if (Log.loggingDebug)
                        log.debug("BAG: tried to place bag in item slot: " + slotId + " but the bag only has: " + itemsInBag2.length + " slots");
                    return true;
                }
                
                OID itemOid = generateItem(bagID1, bagName1);
                if (!placeItem(mobOid, mobOid, itemOid, containerId, slotId)) {
                    ExtendedCombatMessages.sendErrorMessage(mobOid, "You must place the bag in an empty slot.");
                    return true;
                }
            }
            
            subBag1.setNumSlots(0);
            subBag1.setName("");
            subBag1.setItemTemplateID(-1);
            Engine.getPersistenceManager().setDirty(iInfo);
            sendBagInvUpdate(mobOid);
            return true;
        }
    }
    
    // Log the login information and send a response
    class LoginHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LoginMessage message = (LoginMessage) msg;
            OID playerOid = message.getSubject();
            OID instanceOid = message.getInstanceOid();
            if(Log.loggingDebug)Log.debug("LoginHook: playerOid=" + playerOid + " instanceOid=" + instanceOid);
            // Check for mail available here?
            try {
                OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.ACCOUNT_PROPERTY);
                authDB.checkAccountPurchases(playerOid, accountID);
                InventoryInfo iInfo = getInventoryInfo(playerOid);
                if (iInfo == null) {
                    Log.error("LoginHook: player: " + playerOid + " instanceOid=" + instanceOid + " InventoryInfo is null");
                    Engine.getAgent().sendResponse(new ResponseMessage(message));
                    return true;
                }
                iInfo.setMail(aDB.retrieveMail(playerOid, accountID));
                if(Log.loggingDebug)Log.debug("LoginHook:MAIL: loaded " + iInfo.getMail().size() + " pieces of mail for player: " + playerOid);
                loadMailItems(playerOid);
                // Send down currencies
                // ExtendedCombatMessages.sendCurrencies(playerOid, iInfo.getCurrentCurrencies());

                // Also get account settings
                HashMap<String, String> accountSettings = authDB.getAccountSettings(accountID);
                if (accountSettings.containsKey("AvatarRing")) {
                    EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "AvatarRing", accountSettings.get("AvatarRing"));
                }
                if (accountSettings.containsKey("DuelFlag")) {
                    EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "DuelFlag", accountSettings.get("DuelFlag"));
                }
                inventoryCheck(playerOid);
                
                
                Map<String, AgisEquipSlot> slotList = AgisEquipSlot.getSlots();
                Map<String, Serializable> slotsProps = new HashMap<String, Serializable>();
                slotsProps.put("ext_msg_subtype", "inventory_definition");
                slotsProps.put("lDist", (int)(LOOT_DISTANCE*1000));
                slotsProps.put("ilog", INVENTORY_LOOT_ON_GROUND);
                int cc = 0;
                for (AgisEquipSlot slot : slotList.values()) {
                    log.debug("slot def "+slot);
                    slotsProps.put("sN" + cc, slot.getName());
                    //slotsProps.put("sS" + cc, slot.getSocketName());
                    slotsProps.put("sT" + cc, slot.getTypeName());
                    cc++;
                }
                slotsProps.put("num", cc);
                cc = 0;
                Map<String, AgisEquipInfo> eqlist = AgisEquipInfo.getEquipInfoList();
                for (AgisEquipInfo aei : eqlist.values()) {
                    slotsProps.put("gs" + cc, aei.getName());
					slotsProps.put("gsa" + cc, aei.getAll());
                    int s = 0;
                    List<AgisEquipSlot> sList = aei.getEquippableSlots();
                    for (AgisEquipSlot eaes : sList) {
                        slotsProps.put("gs" + cc + "s" + s, eaes.getName());
                        s++;
                    }
                    slotsProps.put("gs" + cc + "num", s);
                    cc++;
                }
                slotsProps.put("gnum", cc);

                int race = -1;
                int aspect = -1;
                try {
                    race = (Integer) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "race");
                    aspect = (Integer) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, "aspect");
                }catch (Exception e){

                }
                int sets = 0;
                boolean setNewSetSelected = false;
                List<String> sets_name= new ArrayList<>();
                for (String s : AgisInventoryPlugin.slotsSets.keySet()) {
                    SlotsSet ss = AgisInventoryPlugin.slotsSets.get(s);
                    if( Log.loggingDebug)Log.debug("LoginHook: playerOid: " + playerOid +" Slot sets "+ AgisInventoryPlugin.slotsSets.keySet()+" ss="+ss);
                    if(ss!=null){
                        ArrayList<AgisEquipSlot> setSlotList = ss.getSlotsForRaceClass(race,aspect);
                        if(setSlotList.size()>0){
                            if(iInfo.getItemSetSelected() == null || (iInfo.getItemSetSelected() != null && iInfo.getItemSetSelected().isEmpty())) {
                                iInfo.setItemSetSelected(s);
                                setNewSetSelected = true;
                            }
                            slotsProps.put("s_"+sets,s);
                            sets_name.add(s);
                           sets++;
                        }
                    }

                }
                slotsProps.put("s_num",sets);
                TargetedExtensionMessage slotsMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, slotsProps);
                Engine.getAgent().sendBroadcast(slotsMsg);

                for(String set : sets_name){
                    if(iInfo.getEquippedItemsSetBag(set)==null){
                       Bag subBag = createSubBag(iInfo.getOid(), Bag.BAG_USE_TYPE_EQUIPPED, EQUIP_SLOTS_COUNT,"Set_"+sets_name);
                        if (subBag != null) {
                            iInfo.addEquippedItemsSetBag(set,subBag.getOid());
                        }
                    }
                }

                //Send Shop List
                ArrayList<PlayerShop> list  = aDB.getAllPlayerShop(playerOid);
                Map<String, Serializable> props = new HashMap<String, Serializable>();
                props.put("ext_msg_subtype", "list_player_shop");
                //props.put("slots", slots);
                
                int c= 0;
                for(PlayerShop ps : list) {
                    
                    long time = System.currentTimeMillis();
                    if(Log.loggingDebug)Log.debug("LoginHook: ValidateShop time="+time+" shopCreateTime="+ps.getCreateTime()+" shopTimeOut="+ps.getTimeout()+" shopOwner="+ps.getOwnerOid()+" shopDestroyOnLogOut="+ps.getEndPlayerOnLogout()+" shopOid="+ps.getShopOid());
                    if((ps.getTimeout() > 0 && time > ps.getCreateTime() + ps.getTimeout() * 60000) || ps.getEndPlayerOnLogout()) {
                        
                        ArrayList< PlayerShopItem> listItems = aDB.getPlayerStore(ps.getShopOid());
                        aDB.deletePlayerStore(ps.getShopOid());
                        if(Log.loggingDebug)Log.debug("LoginHook: storeOid=" + ps.getShopOid()+" items="+listItems.size());
                        ArrayList<OID> itemList = new ArrayList<OID>();
                        int curr=0;
                        long currAmount = 0;
                        for (PlayerShopItem psi : listItems) {
                            log.debug("LoginHook: "+psi.getTemplateId()+" "+psi.getItemOid());
                            if (psi.getTemplateId() > 0) {
                                currAmount +=psi.getPrice()*psi.getCount();
                                curr=psi.getCurrency();
                            } else {
                                itemList.add(psi.getItemOid());
                            }
                        }
                        if(Log.loggingDebug)Log.debug("LoginHook: send Mail items:"+itemList+" c:"+curr+" a:"+currAmount+" size="+listItems.size());
                        if (itemList.size() > AgisInventoryPlugin.MAIL_ATTACHMENT_COUNT) {
                            ArrayList<OID> itemList2 = new ArrayList<OID>();
                            int i = 0;
                            while (itemList.size() > 0) {
                                itemList2.add(itemList.remove(0));
                                i++;
                                if (i == AgisInventoryPlugin.MAIL_ATTACHMENT_COUNT) {
                                    createAndSendCharacterMail(OID.fromLong(0L), ps.getOwnerOid(), "Seller", "Sell on Store", "Shop has been terminated", itemList2, curr, currAmount, false);
                                    currAmount = 0;
                                    itemList2= new ArrayList<OID>();
                                    i = 0;
                                }
                            }
                            if (itemList2.size() != 0 || currAmount != 0) {
                                createAndSendCharacterMail(OID.fromLong(0L), ps.getOwnerOid(), "Seller", "Sell on Store", "Shop has been terminated", itemList2, curr, currAmount, false);
                            }
                        } else {
                            createAndSendCharacterMail(OID.fromLong(0L), ps.getOwnerOid(), "Seller", "Sell on Store", "Shop has been terminated", itemList, curr, currAmount, false);
                        }
                        Log.debug("LoginHook: Despawn");
                        if (ps.getPlayer()) {
                            EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "shopTitle", "");
                            EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "playerShop", false);
                            EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "plyShopId", OID.fromLong(0L));
                            EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, false);
                            EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
                        }else
                            AgisMobClient.DespawnPlayerShop(ps.getShopOid());
                    }else {
                        props.put("sOid"+c, ps.getShopOid());
                        props.put("sMsg"+c, ps.getTitle());
                        c++;
                    }
                }
                props.put("num", c);
                TargetedExtensionMessage resultMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);

                Engine.getAgent().sendBroadcast(resultMsg);
                //Set unequipped state for currently equipped items               
                if (iInfo != null) 
                {
                    OID equipBag = iInfo.getEquipmentItemBag();
                    if (equipBag != null) 
                    {
                        Bag bag = getBag(equipBag);
                        ArrayList<OID> _equipList = bag.getItemsListArray();
                        
                        for(OID oid : _equipList) 
                        {
                            if(oid!=null) 
                            {
                                AgisItem ai = getAgisItem(oid);
                                if (!EquipHelper.IsDurabilityOk(ai)) {
                                	 ai.setProperty("unequipped", true);
                                	 //Engine.getPersistenceManager().setDirty(ai);
                                }
                                if(setNewSetSelected) {
                                    if(Log.loggingDebug)log.debug("LoginHook unequipItem then equipItem item "+oid);
                                    unequipItem(ai, playerOid, false);
                                    equipItem(ai, playerOid, false);
                                }
                            }
                        }
                    }                       
                }
             

            } catch (Exception e) {
                Log.exception("Inventory LoginHook", e);
            }

            Engine.getAgent().sendResponse(new ResponseMessage(message));
            return true;
        }
    }

    // Log the logout information and send a response
    class LogoutHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LogoutMessage message = (LogoutMessage) msg;
            OID playerOid = message.getSubject();
            Log.debug("LOGOUT: inventory logout started for: " + playerOid);
            //Log.debug("LogoutHook: playerOid=" + playerOid);
            // Remove the entry from the hashmap
            try {
                for (OID instance : itemsOnGround.keySet()) {
                    itemsOnGround.get(instance).remove(playerOid);
                }
            }catch (Exception e){

            }
            Engine.getAgent().sendResponse(new ResponseMessage(message));
            Log.debug("LOGOUT: inventory logout finished for: " + playerOid);
            return true;
        }
    }
    
    /*
     * Bank Hooks/Functions
     */
    
    /**
     * Handles the SendInventoryUpdateMessage, calling the sendBagInvUpdate function
     * for the specified player.
     * @author Andrew Harrison
     *
     */
    class SendBankInventoryHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            OID mobOid = null;
            Integer bankId = 0;
            if (msg instanceof ExtensionMessage) {
                ExtensionMessage getMsg = (ExtensionMessage) msg;
                mobOid = getMsg.getSubject();
                bankId = (Integer) getMsg.getProperty("bankId");
            } else if (msg instanceof SendBankInventoryMessage) {
                SendBankInventoryMessage getMsg = (SendBankInventoryMessage) msg;
                mobOid = getMsg.getSubject();
                bankId = getMsg.getBankId();
            }
            InventoryInfo iInfo = getInventoryInfo(mobOid);

            if (Log.loggingTrace)
                log.trace("SendBankInventoryHook: mobOid:" + mobOid + " bankId:" + bankId+" InventoryInfo:"+iInfo);
            if(iInfo!=null) {
                if (bankId == null || bankId < 0) {
                    OID bankBagOid = iInfo.getStorageBag(bankBagKey + "0");
                    sendBankInvUpdate(mobOid, bankBagKey + "0", bankBagOid, true);
                } else {
                    OID bankBagOid = iInfo.getStorageBag(bankBagKey + bankId);

                    if (bankBagOid == null) {
                        Bag subBag = createSubBag(iInfo.getOid(), Bag.BAG_USE_TYPE_BANK, BANK_SLOTS_COUNT,bankBagKey + bankId);
                        if (subBag == null) {
                            return false;
                        }
                        iInfo.addStorageBag(bankBagKey + bankId, subBag.getOid());
                        Engine.getPersistenceManager().setDirty(iInfo);

                        bankBagOid = iInfo.getStorageBag(bankBagKey + bankId);
                    }
                    sendBankInvUpdate(mobOid, bankBagKey + bankId, bankBagOid, true);
                }
            } else{
                log.error("SendBankInventoryHook: mobOid:" + mobOid + " bankId:" + bankId+" InventoryInfo is null");
            }

            return true;
        }
    }
    
    class OpenStorageHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.OpenStorageMessage getMsg = (AgisInventoryClient.OpenStorageMessage)msg;
            OID mobOid = getMsg.getSubject();
            boolean playerStorage = getMsg.isPlayerStorage();
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            if (Log.loggingTrace) log.trace("OpenStorageHook");
            Log.debug("OpenStorageHook");
            if (playerStorage) {
                String storageName = getMsg.getStorageName();
                OID bankBagOid = iInfo.getStorageBag(storageName);
                sendBankInvUpdate(mobOid, storageName, bankBagOid,true);
            } else {
                OID storageOid = getMsg.getStorageOid();
                int lockLimit = getMsg.getLockLimit();
                Bag subBag = loadNonPlayerStorageBag(storageOid); // Need to load bag in from Database
                // make sure lock settings match the template.
                if(lockLimit > 0) {
                    subBag.setLockLimit(lockLimit);
                    subBag.setLockable(true);
                }
                iInfo.setActiveStorage(storageOid.toString());
                sendBankInvUpdate(mobOid, storageOid.toString(), storageOid,true);
                //FIXME Test to delete
            /*  Log.error("OpenStorageHook 1");
                HashMap<Integer, Long> cccc = subBag.getCurrentCurrencies();
                Log.error("OpenStorageHook 2");*/
            }
            return true;
        }
    }
    
    /**
     * Sends down the Bags and Items found in the players Inventory.
     * @param mobOid
     */
	protected void sendStorageInvUpdate(OID mobOid) {
		InventoryInfo iInfo = getInventoryInfo(mobOid);
		OID bankBagOid = iInfo.getActiveStorageBag();
		log.debug("sendStorageInvUpdate bankBagOid:" + bankBagOid + " mobOid:" + mobOid);
		if (bankBagOid == null) {
			log.debug("sendStorageInvUpdate: bankBagOid is null 1");
			// It may be a non player storage bag
			if (iInfo.getActiveStorage() != null && !iInfo.getActiveStorage().trim().isEmpty()) {
					bankBagOid = OID.fromString(iInfo.getActiveStorage());
			}
			log.debug("sendStorageInvUpdate bankBagOid:" + bankBagOid + " mobOid:" + mobOid);
			if (bankBagOid == null) {
				log.debug("sendStorageInvUpdate: bankBagOid is null 2");
				return;
			}
		}
		sendBankInvUpdate(mobOid, iInfo.getActiveStorage(), bankBagOid, false);
	}
    
    /**
     * Sends down the Bags and Items found in the players Inventory.
     * @param mobOid
     */
    protected void sendBankInvUpdate(OID mobOid, String storageKey, OID storageBagOid, boolean open) {
        Log.trace("STORAGE: sending storage inventory update for key: " + storageKey+" mobOid:"+mobOid+" storageBagOid:"+storageBagOid);
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        if (storageKey.startsWith(bankBagKey)) {
            props.put("ext_msg_subtype", "BankInventoryUpdate");
        } else {
            props.put("ext_msg_subtype", "StorageInventoryUpdate");
        }
        props.put("storageKey", storageKey);
        props.put("open", open);
          
        int numItems = 0;
        //int numBags = 0;
        
        props.put("numBags", 1);
        for (int bagPos = 0; bagPos < 1; bagPos++) {
            if (storageBagOid == null) {
                log.debug("sendBankInvUpdate: sub bag oid is null");
                continue;
            }
            Bag subBag = getBag(storageBagOid);
            if (subBag == null) {
                log.debug("sendBankInvUpdate: sub bag obj is null");
                props.put("bag_" + bagPos + "ID", 0);
                props.put("bag_" + bagPos + "Name", "");
                props.put("bag_" + bagPos + "TId", "");
                props.put("bag_" + bagPos + "NumSlots", 0);
                continue;
            }
			if (storageKey.startsWith(bankBagKey)) {
				if (subBag.getNumSlots() < BANK_SLOTS_COUNT) {
					subBag.changeNumSlots(BANK_SLOTS_COUNT);
				}
			}
            //sendInvUpdateHelper(invUpdateMsg, pos, subBag);
            props.put("bag_" + bagPos + "ID", subBag.getItemTemplateID());
            props.put("bag_" + bagPos + "Name", subBag.getName());
            props.put("bag_" + bagPos + "TId", subBag.getItemTemplateID());
            props.put("bag_" + bagPos + "NumSlots", subBag.getNumSlots());
            OID[] itemsInBag = subBag.getItemsList();
            for (int itemPos = 0; itemPos < itemsInBag.length; itemPos++) {
                boolean itemExists = true;
                // get the item
                OID oid = itemsInBag[itemPos];
                if (oid == null) {
                    itemExists = false;
                }
                AgisItem item = getAgisItem(oid);
                if (item == null) {
                    Log.warn("sendBankInvUpdateHelper: item is null, oid=" + oid);
                    itemExists = false;
                }
                if (itemExists) {
                    if (Log.loggingDebug)
                        log.debug("sendBankInvUpdateHelper: adding bagNum=" + bagPos + ", bagPos=" + itemPos +
                                  ", itemOid=" + oid + ", itemName=" + item.getName() + ",icon=" + item.getIcon());
                    props.put("item_" + numItems + "TemplateID", item.getTemplateID());
                    props.put("item_" + numItems + "Name", item.getName());
                    props.put("item_" + numItems + "BaseName", item.getProperty("baseName"));
                    //props.put("item_" + numItems + "Description", description);
                    //props.put("item_" + numItems + "Icon", item.getIcon());
                    props.put("item_" + numItems + "Id", item.getOid());
                    props.put("item_" + numItems + "Count", item.getStackSize());
                    props.put("item_" + numItems + "BagNum", bagPos);
                    props.put("item_" + numItems + "SlotNum", itemPos);
                    props.put("item_" + numItems + "Bound", item.isPlayerBound());
                    if (item.getProperty("energyCost") != null) {
                        props.put("item_" + numItems + "EnergyCost", item.getProperty("energyCost"));
                    } else {
                        props.put("item_" + numItems + "EnergyCost", 0);
                    }
                    if (item.getProperty("maxDurability") != null) {
                        props.put("item_" + numItems + "Durability", item.getProperty("durability"));
                        props.put("item_" + numItems + "MaxDurability", item.getProperty("maxDurability"));
                    } else {
                        props.put("item_" + numItems + "MaxDurability", 0);
                    }
                    if (item.getProperty("resistanceStats") != null) {
                        int numResist = 0;
                        HashMap<String, Integer> resistances = (HashMap) item.getProperty("resistanceStats");
                        for (String resistance: resistances.keySet()) {
                            props.put("item_" + numItems + "Resist_" + numResist + "Name", resistance);
                            props.put("item_" + numItems + "Resist_" + numResist + "Value", resistances.get(resistance));
                            numResist++;
                        }
                        props.put("item_" + numItems + "NumResistances", numResist);
                    } else {
                        props.put("item_" + numItems + "NumResistances", 0);
                    }
                    if (item.getProperty("bonusStats") != null) {
                        int numStats = 0;
                        HashMap<String, Integer> stats = (HashMap) item.getProperty("bonusStats");
                        for (String statName: stats.keySet()) {
                            props.put("item_" + numItems + "Stat_" + numStats + "Name", statName);
                            props.put("item_" + numItems + "Stat_" + numStats + "Value", stats.get(statName));
                            numStats++;
                        }
                        props.put("item_" + numItems + "NumStats", numStats);
                    } else {
                        props.put("item_" + numItems + "NumStats", 0);
                    }
                    int enchantLevel = (int) item.getProperty("enchantLevel");
                    props.put("item_" + numItems + "ELevel", enchantLevel);
                    
                    if (item.getProperty("enchantStats") != null) {
                        int numStats = 0;
                        HashMap<String, Integer> stats = (HashMap) item.getProperty("enchantStats");
                        for (String statName: stats.keySet()) {
                            props.put("item_" + numItems + "EStat_" + numStats + "Name", statName);
                            props.put("item_" + numItems + "EStat_" + numStats + "Value", stats.get(statName));
                            numStats++;
                        }
                        props.put("item_" + numItems + "NumEStats", numStats);
                    } else {
                        props.put("item_" + numItems + "NumEStats", 0);
                    }
                    if (item.getProperty("sockets") != null) {
                        int numSocket = 0;
                        HashMap<Integer, SocketInfo> sockets = (HashMap<Integer, SocketInfo>) item.getProperty("sockets");
                        for (Integer socket: sockets.keySet()) {
                            if (sockets.get(socket).GetItemOid()!=null) {
                                 AgisItem itemSoc = getAgisItem(sockets.get(socket).GetItemOid());
                                 if (itemSoc != null) {
                                     props.put("item_" + numItems + "socket_" + socket + "Item", itemSoc.getTemplateID());
                                        props.put("item_" + numItems + "socket_" + socket + "ItemOid", itemSoc.getOid().toLong());
                                         }else {
                                     props.put("item_" + numItems + "socket_" + socket + "Item", -1); 
                                        props.put("item_" + numItems + "socket_" + socket + "ItemOid", 0L);
                                          }
                            }else {
                                props.put("item_" + numItems + "socket_" + socket + "Item", -1);
                            props.put("item_" + numItems + "socket_" + socket + "ItemOid", 0L);
                            }
                            props.put("item_" + numItems + "socket_" + socket + "Type", sockets.get(socket).GetType());
                            props.put("item_" + numItems + "socket_" + socket + "Id", socket);
                                numSocket++;
                        }
                    
                        props.put("item_" + numItems + "NumSocket", numSocket);
                    } else {
                        props.put("item_" + numItems + "NumSocket", 0);
                    }
                  
                    // If it is a weapon, add damage/speed stats
                    if (item.getItemType().equals("Weapon")) {
                        props.put("item_" + numItems + "Delay", item.getProperty("delay"));
                        props.put("item_" + numItems + "DamageType", item.getProperty("damageType"));
                        props.put("item_" + numItems + "DamageValue", item.getProperty("damage"));
                        props.put("item_" + numItems + "DamageValueMax", item.getProperty("damageMax"));
                         }
                    props.put("item_" + numItems + "ActionBarAllowed", item.getProperty("actionBarAllowed"));
                    
                    //Added for Enchant Effects and Abilities
                    if (enchantLevel > 0) 
                    {
                    	if (item.getProperty("enchantProfileId") != null) 
                    	{
                    		EnchantProfile ep = ObjectManagerPlugin.getEnchantProfile((int) item.getProperty("enchantProfileId"));
                			if (ep != null && ep.GetLevels().containsKey(enchantLevel)) 
                			{	
                				int numEffects = 0;
                				int numAbilities = 0;
                				for (int e = 1; e <= enchantLevel; e++) 
                				{
                					EnchantProfileLevel enchantProfile = ep.GetLevels().get(e);
        							for (Integer ability : enchantProfile.GetAbilities()) 
        							{
        								props.put("item_" + numItems + "EAbility_" + numAbilities + "Value", ability);
        								numAbilities++;
        							}   
        							for (Integer effect : enchantProfile.GetEffects()) 
        							{
        								props.put("item_" + numItems + "EEffect_" + numEffects + "Value", effect);
        								numEffects++;
        							}
                				}
                				props.put("item_" + numItems + "NumEAbilities", numAbilities);	
                				props.put("item_" + numItems + "NumEEffects", numEffects);
                			}                			
                    	}
            		} 
                    numItems++;
                }
            }
            
            // Send down locks if bag has them.
            props.put("bag_" + bagPos + "Lockable", subBag.isLockable());
            if (subBag.isLockable()) {
                props.put("bag_" + bagPos + "LockLimit", subBag.getLockLimit());
                props.put("bag_" + bagPos + "NumLocks", subBag.getNumLocks());
                props.put("bag_" + bagPos + "Locked", subBag.isLocked());
                if (subBag.isLocked()) {
                    // Send locks
                    boolean lockExists = true;
                    // get the lock
                    OID oid = subBag.getChestLock();
                    if (oid == null) {
                        lockExists = false;
                    }
                    AgisItem chestLock = getAgisItem(oid);
                    if (chestLock == null) {
                        Log.warn("sendBankInvUpdateHelper: item is null, oid=" + oid);
                        lockExists = false;
                    }
                    props.put("lock_" + bagPos + "LockExists", lockExists);
                    if (lockExists) {
                        if (Log.loggingDebug)
                            log.debug("sendBankInvUpdateHelper: adding bagNum=" + bagPos +
                                    ", itemOid=" + oid + ", itemName=" + chestLock.getName() + ",icon=" + chestLock.getIcon());
                        props.put("lock_" + bagPos + "TemplateID", chestLock.getTemplateID());
                        props.put("lock_" + bagPos + "Name", chestLock.getName());
                        props.put("lock_" + bagPos + "BaseName", chestLock.getProperty("baseName"));
                        props.put("lock_" + bagPos + "ID", oid);
                        props.put("lock_" + bagPos + "BagNum", bagPos);
                        if (chestLock.getProperty("maxDurability") != null) {
                            props.put("lock_" + bagPos + "Durability", chestLock.getProperty("durability"));
                            props.put("lock_" + bagPos + "MaxDurability", chestLock.getProperty("maxDurability"));
                        } else {
                            props.put("lock_" + bagPos + "MaxDurability", 0);
                        }
                        props.put("lock_" + bagPos + "Bound", chestLock.isPlayerBound());
                    }
                }
            }
        }
        
        props.put("numItems", numItems);
        log.debug("sendBankInvUpdate " + props);
        
        TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, mobOid, mobOid, false, props);
        
        Engine.getAgent().sendBroadcast(msg);
    }
    
    /**
     * Handles the GetStorageContentsMessage. Returns a list of itemOids in the specified storage bag
     * back to the requesting server plugin.
     * @author Andrew Harrison
     *
     */
    class GetStorageContentsHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.GetStorageContentsMessage getMsg = (AgisInventoryClient.GetStorageContentsMessage)msg;
            OID mobOid = getMsg.getSubject();
            String storageName = getMsg.getStorageName();
            LinkedList<OID> itemsInStorage = new LinkedList<OID>();
            log.trace("GetStorageContentsHook");
             
            if (getMsg.isPlayerStorage()) {
                InventoryInfo iInfo = getInventoryInfo(mobOid);
                OID storageBagOid = iInfo.getStorageBag(storageName);
            
                if (storageBagOid != null) {
                    Bag storageBag = getBag(storageBagOid);
                    if (storageBag != null) {
                        for(OID itemOid : storageBag.getItemsList()) {
                            if (itemOid != null) {
                                itemsInStorage.add(itemOid);
                            }
                        }
                    }
                }
            } else {
                OID storageOid = getMsg.getStorageOid();
                Bag storageBag = loadNonPlayerStorageBag(storageOid); // Need to load bag in from Database
                if (storageBag != null) {
                    for(OID itemOid : storageBag.getItemsList()) {
                        if (itemOid != null) {
                            itemsInStorage.add(itemOid);
                        }
                    }
                }
            }
            
            Engine.getAgent().sendObjectResponse(msg, itemsInStorage);
            return true;
        }
    }
    
    /**
     * Requests the moving of an item from a players inventory to their bank
     * @author Andrew
     *
     */
    class StoreItemInBankHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage getMsg = (ExtensionMessage)msg;
            OID mobOid = getMsg.getSubject();
            OID itemOid = (OID) getMsg.getProperty("itemOid");
            int bankSlot = (Integer) getMsg.getProperty("bankSlot");
            int count = (Integer) getMsg.getProperty("count");
            log.debug("StoreItemInBankHook: swap=  "+getMsg.getProperty("swap"));
            if (Log.loggingDebug) log.debug("StoreItemInBankHook: mobOid:"+mobOid+" itemOid:"+itemOid+" bankSlot:"+bankSlot+ " count:"+count);
             AgisItem item = getAgisItem(itemOid);
            if (item == null) {
                  log.debug("StoreItemInBankHook: mobOid:"+mobOid+" itemOid:"+itemOid+" Item is null ");
                return true;
            }
            
            // Check space in Bank
            boolean placeIntoNewSlot = true;
            boolean nonPlayerStorage = false;
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            if(iInfo==null) {
                  log.debug("StoreItemInBankHook: mobOid:"+mobOid+" itemOid:"+itemOid+" iInfo is null return");
                  return true;
            }
            OID bankBagOid = iInfo.getActiveStorageBag();
            if (bankBagOid == null) {
                // It may be a non player storage bag
                if (iInfo.getActiveStorage() != null) {
                    bankBagOid = OID.fromString(iInfo.getActiveStorage());
                    nonPlayerStorage = true;
                }
                if (bankBagOid == null) {
                    //TODO: some error
                    return true;
                }
            }
            Bag bankBag = getBag(bankBagOid);
            String storageName = bankBag.getStorageName();
            boolean forGuild=false;
            boolean forClaim=false;
            int guildPermission = 0;

            if (isItemEquipped(item, mobOid)) {
                log.debug("StoreItemInBankHook: item is Equipped brake");
                ExtendedCombatMessages.sendErrorMessage(mobOid, "You can't add equipped item");
                sendBagInvUpdate(mobOid);
                sendStorageInvUpdate(mobOid);
                sendInvUpdate(mobOid);
                return true;
            }

            if(storageName.startsWith("Claim_")) {
            	forClaim = true;
              
			} 
            if(storageName.startsWith("Guild_")) {
                forGuild = true;
                guildPermission = GuildClient.getGuildWhPermition(mobOid);
			}
			log.debug("StoreItemInBankHook: storageName=" + storageName + " forGuild=" + forGuild + " guildPermission=" + guildPermission);
			bankBag.setBank(true);
			if (forGuild && guildPermission != Claim.PERMISSION_OWNER && guildPermission != Claim.PERMISSION_ADD_DELETE && guildPermission != Claim.PERMISSION_ADD_ONLY) {
				log.debug("StoreItemInBankHook: You dont have permission to this action");
				ExtendedCombatMessages.sendErrorMessage(mobOid, "You don't have permission to do this action");
				sendBagInvUpdate(mobOid);
				sendStorageInvUpdate(mobOid);
                sendInvUpdate(mobOid);
				// FIXME Must be bank
				return true;
			}
			if ((forGuild && forClaim )&& item.isPlayerBound()) {
				log.debug("StoreItemInBankHook: Guild Bank You cant add bound Item");
				ExtendedCombatMessages.sendErrorMessage(mobOid, "You can't add bound item");
				sendBagInvUpdate(mobOid);
				sendStorageInvUpdate(mobOid);
                sendInvUpdate(mobOid);
				return true;
			}
			
			if(!STORE_BOUND_ITEM_IN_BANK && item.isPlayerBound()) {
				log.debug("StoreItemInBankHook: Guild Bank You cant add bound Item");
				ExtendedCombatMessages.sendErrorMessage(mobOid, "You can't add bound item");
				sendBagInvUpdate(mobOid);
				sendStorageInvUpdate(mobOid);
                sendInvUpdate(mobOid);
				return true;
			}
        //  if ((forGuild && guildPermission > 0) || !forGuild) {

                if (bankBag.getItem(bankSlot) != null) {
                    // First Check in case it is an item of the same type and the items could be
                    // merged.
                    AgisItem oldItem = getAgisItem(bankBag.getItem(bankSlot));
                    Log.debug("BANK: old item: " + oldItem.getOid() + " new item: " + itemOid);

                    if (oldItem.getTemplateID() == item.getTemplateID() && !itemOid.equals(oldItem.getOid())) {
                        int stackSizeToAdd = item.getStackSize();
                        if ((oldItem.getStackSize() + stackSizeToAdd) <= oldItem.getStackLimit()) {
                            oldItem.alterStackSize(mobOid, stackSizeToAdd);
                            if (bankBag.findItem(itemOid) != null) {
                                bankBag.removeItem(item.getOid());
                            } else {
                                removeItem(mobOid, itemOid, true);
                            }

                            Engine.getPersistenceManager().setDirty(oldItem);
                            Engine.getPersistenceManager().setDirty(iInfo);
                            placeIntoNewSlot = false;
                        } else {
                            log.debug("StoreItemInBankHook: sum items counts exceeds stackLimit");
                            // TODO: some error
                            sendStorageInvUpdate(mobOid);
                            sendInvUpdate(mobOid);
                            return true;
                        }
                    } else {
                        log.debug("StoreItemInBankHook: item is not the same template");
                        sendStorageInvUpdate(mobOid);
                        sendInvUpdate(mobOid);
                        // TODO: some error
                        return true;
                    }
                }

                if (placeIntoNewSlot) {
                    // Item is being placed into an empty slot

                    // Check if the item is already in this same bank bag
                    if (bankBag.findItem(item.getOid()) != null) {
                        boolean rv = bankBag.removeItem(item.getOid());
                        if (!rv) {
                            // TODO: some error
                            return true;
                        }
                    } else {
                        // Remove item from Bag
                        removeItem(mobOid, itemOid, true);
                    }

                    // Place item in Bank
                    boolean rv = bankBag.putItem(bankSlot, itemOid);
                    if (rv) {
                        item.setProperty(INVENTORY_PROP_BACKREF_KEY, bankBagOid);
                    }

                    // mark dirty
                    Engine.getPersistenceManager().setDirty(item);
                    Engine.getPersistenceManager().setDirty(iInfo);
                }
            //}
            sendStorageInvUpdate(mobOid);
            sendInvUpdate(mobOid);
            
            if (nonPlayerStorage) {
                saveNonPlayerStorageBag(bankBag);
            }
            
            return true;
        }
    }
    
    /**
     * Requests the removal of an item from a players bank and for the item to be placed in the players inventory.
     * @author Andrew
     *
     */
    class RetrieveItemInBankHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage getMsg = (ExtensionMessage)msg;
            OID mobOid = getMsg.getSubject();
            OID itemOid = (OID) getMsg.getProperty("itemOid");
            int containerNum = (Integer) getMsg.getProperty("containerNum");
            int slotNum = (Integer) getMsg.getProperty("slotNum");
            if (Log.loggingTrace) log.trace("RetrieveItemInBankHook: mobOid:"+mobOid+" itemOid:"+itemOid+" containerNum:"+containerNum+ " slotNum:"+slotNum);
            lock.lock();
            try {
            AgisItem item = getAgisItem(itemOid);
            if (item == null) {
                //TODO: some error
                log.debug("RetrieveItemInBankHook: item is null");
                return true;
            }
            InventoryInfo iInfo = getInventoryInfo(mobOid);
            // Check space in Bag
            OID[] subBagOids = iInfo.getBags();
            OID subBagOid = subBagOids[containerNum];
            Bag subBag = getBag(subBagOid);
            log.trace("RetrieveItemInBankHook: mobOid:" + mobOid + " itemOid:" + itemOid + " containerNum:" + containerNum + " slotNum:" + slotNum + " subBagOid:" + subBagOid
                    + " subBagOids:" + Arrays.toString(subBagOids)+" Bank:"+subBag.getBank());
               if (subBag.getItem(slotNum) != null) {
                //TODO: some error
                log.debug("RetrieveItemInBankHook: item in bag is null");
                return true;
            }
            
            // Remove item from Bank
            boolean nonPlayerStorage = false;
            OID bankBagOid = iInfo.getActiveStorageBag();
            if (bankBagOid == null) {
                // It may be a non player storage bag
                if (iInfo.getActiveStorage() != null) {
                    bankBagOid = OID.fromString(iInfo.getActiveStorage());
                    nonPlayerStorage = true;
                }
                if (bankBagOid == null) {
                    //TODO: some error
                    log.debug("RetrieveItemInBankHook: bankBagOid is null");
                    return true;
                }
            }
            
           
            Bag bag = getBag(bankBagOid);
            String storageName = bag.getStorageName();
            if (storageName.startsWith("Guild_")) {
            	if(item.isPlayerBound()) {
            		if(!item.getPlayerBound().equals(mobOid)) {
            			 log.debug("RetrieveItemInBankHook: You can't get item bound to diffrent player form guild warehouse");
                         ExtendedCombatMessages.sendErrorMessage(mobOid, "You dont have permission to do this action");
                         // FIXME Must be bank
                         return true;
            			
            		}
            	}
                int guildPermission = GuildClient.getGuildWhPermition(mobOid);
                if (Log.loggingDebug) {
                    log.debug("RetrieveItemInBankHook: storageName=" + storageName + " guildPermission=" + guildPermission);
                }
                if (guildPermission != Claim.PERMISSION_OWNER && guildPermission != Claim.PERMISSION_ADD_DELETE) {
                    log.debug("RetrieveItemInBankHook: You dont have permission to do this action");
                    ExtendedCombatMessages.sendErrorMessage(mobOid, "You dont have permission to do this action");
                    // FIXME Must be bank
                    return true;
                }
            }
            
            
            //Bag bankBag = getBag(bankBagOid);
            if (!removeItemFromBagHelper(bankBagOid, item)) {
                //TODO: some error
                log.debug("RetrieveItemInBankHook: can't remove item from bank bag");
                return true;
            }
            
            // Place item in Bag
            if (!placeItem(mobOid, mobOid, itemOid, containerNum, slotNum)) {
                log.debug("RetrieveItemInBankHook: can't place item in player bag");
                //TODO: handle error
            }
            
            sendStorageInvUpdate(mobOid);
            sendInvUpdate(mobOid);
            
            if (nonPlayerStorage) {
                Bag bankBag = getBag(bankBagOid);
                saveNonPlayerStorageBag(bankBag);
            }
            return true;
            } finally {
                lock.unlock();
            }
            
        } 
    }

    class UpdateStorageHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.UpdateStorageSizeMessage genMsg = (AgisInventoryClient.UpdateStorageSizeMessage) msg;
            OID oid = genMsg.getSubject();
            int storageSize = genMsg.getSize();
            OID storageOid = genMsg.getStorageOid();
            Bag storageBag = loadNonPlayerStorageBag(storageOid); // Need to load bag in from Database
            storageBag.changeNumSlots(storageSize);
            saveNonPlayerStorageBag(storageBag);
            return true;
        }
    }
    
    class CreateStorageHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.CreateStorageMessage genMsg = (AgisInventoryClient.CreateStorageMessage)msg;
            OID oid = genMsg.getSubject();
            String storageName = genMsg.getStorageName();
            int storageSize = genMsg.getStorageSize();
            boolean isPlayerStorage = genMsg.isPlayerStorage();
            int lockLimit = genMsg.getLockLimit();
            log.trace("CreateStorageHook");
              
            OID storageOid = null;
            
            if (isPlayerStorage) {
                InventoryInfo iInfo = getInventoryInfo(oid);
                Bag subBag = createSubBag(iInfo.getOid(), Bag.BAG_USE_TYPE_STORAGE, storageSize,storageName);
                if (subBag == null) {
                    Log.debug("STORAGE: Bag was not created");
                    return true;
                }
                subBag.setStorageName(storageName);
                storageOid = subBag.getOid();
                iInfo.addStorageBag(storageName, subBag.getOid());
                Engine.getPersistenceManager().setDirty(iInfo);
                Log.debug("STORAGE: Bag was created with name: " + storageName + " for player: " + oid);
            } else {
                Bag storageBag = createSubBag(null, Bag.BAG_USE_TYPE_STORAGE, storageSize,storageName);
                storageBag.setLockable(true);
                storageBag.setLockLimit(lockLimit);
                storageBag.setStorageName(storageName);
                if(storageName.startsWith("Guild"))
                    storageBag.setUseType(Bag.BAG_USE_TYPE_GUILD);
                //storageBag.setPersistenceFlag(true);
                //Engine.getPersistenceManager().persistEntity(storageBag);
                storageOid = storageBag.getOid();
                saveNonPlayerStorageBag(storageBag);
            }
            
            Engine.getAgent().sendOIDResponse(msg, storageOid);
            return true;
        }
    }
    
    void saveNonPlayerStorageBag(Bag storageBag) {
        log.trace("saveStorageBag: saving Bag=" + storageBag.getOid());
        Engine.getDatabase().saveObject(storageBag, Namespace.BAG);

        for (OID itemOid : storageBag.getItemsList()) {
            if (itemOid != null) {
                if (Log.loggingDebug)
                    log.debug("saveStorageBag: saving itemOid=" + itemOid);
                ObjectManagerClient.saveObject(itemOid);
                if (Log.loggingDebug)
                    log.debug("saveStorageBag: done saving itemOid=" + itemOid);
            }
        }
  
        if(storageBag.getChestLock() != null) {
            if (Log.loggingDebug)
                log.debug("saveStorageBag: saving itemOid=" + storageBag.getChestLock());
            ObjectManagerClient.saveObject(storageBag.getChestLock());
            if (Log.loggingDebug)
                log.debug("saveStorageBag: done saving itemOid=" + storageBag.getChestLock());
        }
        
    }
    
    Bag loadNonPlayerStorageBag(OID storageOid) {
        Bag subBag = getBag(storageOid);
        if (subBag != null) {
            return subBag;
        }
        Entity e = Engine.getDatabase().loadEntity(storageOid, Namespace.BAG);
        Log.warn("loadStorageBag: trying to load storageBag with Oid: " + storageOid);
        if (e == null) 
            return null;
        
        subBag = (Bag)e;
        registerBag(subBag);
        Log.warn("loadStorageBag: got storageBag");
        boolean dirty = false;
        for (OID itemOid : subBag.getItemsList()) {
            if (itemOid != null) {
                if (ObjectManagerClient.loadObject(itemOid) == null) {
                    // If we can't load the item, then delete reference
                    // to it from the bag.
                    Log.warn("loadStorageBag: item "+itemOid+" does not exist, removing from bag "+storageOid);
                    boolean rv = subBag.removeItem(itemOid);
                    if (rv)
                        dirty = true;
                }
            }
        }
        
        if (subBag.getChestLock() != null) {
            if (ObjectManagerClient.loadObject(subBag.getChestLock()) == null) {
                // If we can't load the lock, then delete reference
                // to it from the bag.
                Log.warn("loadStorageBag: lock "+subBag.getChestLock()+" does not exist, removing from bag "+storageOid);
                subBag.setChestLock(null);
            }
        }
        subBag.setLocked();
        subBag.setLockable(true);
        return subBag;
    }
    
    class MailStorageItemsToUserHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.SendStorageItemsAsMailMessage genMsg = (AgisInventoryClient.SendStorageItemsAsMailMessage)msg;
            OID oid = genMsg.getSubject();
            OID storageOid = genMsg.getStorageOid();
            
            Log.debug("MAIL: mailing storage " + storageOid + " items to player: " + oid);
            
            Bag subBag = getBag(storageOid);
            if (subBag == null) {
                log.debug("sendBankInvUpdate: sub bag obj is null");
                return true;
            }
            
            HashMap<Integer, Integer> items = new HashMap<Integer, Integer>();

            OID[] itemsInBag = subBag.getItemsList();
            for (int itemPos = 0; itemPos < itemsInBag.length; itemPos++) {
                OID itemOid = itemsInBag[itemPos];
                if (itemOid == null) {
                    continue;
                }
                AgisItem item = getAgisItem(itemOid);
                if (item == null) {
                    Log.warn("sendInvUpdateHelper: item is null, oid=" + itemOid);
                    continue;
                }
                Log.debug("MAIL: mailing storage item " + item.getTemplateID());
                if (items.containsKey(item.getTemplateID())) {
                    items.put(item.getTemplateID(), items.get(item.getTemplateID()) + item.getStackSize());
                } else {
                    items.put(item.getTemplateID(), item.getStackSize());
                }
            }
            
            Log.debug("MAIL: mailing " + items.size() + " storage items");
            if (items.size() > 0) {
                String senderName = "System";
                String subject = "Claim chest items";
                String message = "You recently lost your claim, but had some items and objects on it. They have been converted to items and attached to this mail.";
                String result = createAndSendAccountMail(null, senderName, oid, "", subject, message, items, -1, -1, true);
            }
            
            return true;
        }
    }
    
    /*
     * Mail Hooks/Functions
     */
    
    /**
     * Handles the GetMailMessage. Calls sendMailList.
     *
     */
    class GetMailHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage getMsg = (ExtensionMessage)msg;
            OID oid = getMsg.getSubject();
            sendMailList(oid);
            return true;
        }
    }
    
    /**
     * Handles the MailReadMessage. Sets the specified piece of mail's read property
     * to true.
     * @author Andrew
     *
     */
    class MailReadHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage getMsg = (ExtensionMessage)msg;
            OID oid = getMsg.getSubject();
            int mailID = (Integer) getMsg.getProperty("mailID");
            InventoryInfo iInfo = getInventoryInfo(oid);
            ArrayList<Mail> mailList = iInfo.getMail();
            Mail m = getMailByID(mailList, mailID);
            Log.debug("Setting mail read with mailID: " + mailID+" "+m);
            if(m == null){
                log.error("MailReadHook mailID "+mailID+" was not found for "+oid);
                return true;
            }
            m.setMailRead(true);
            iInfo.setMail(mailList);
            
            // Put Read token in DB JSChasle
            aDB.readMail(mailID);
            
            //WorldManagerClient.sendObjChatMsg(oid, 2, "Mail: " + mailID + " marked as read.");
            sendMailList(oid);
            return true;
        }
    }
    
    /**
     * Handles the TakeMailItemMessage. Takes the item attached to the specified
     * piece of mail and puts it in the players inventory.
     * @author Andrew Harrison
     *
     */
    class TakeMailItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage getMsg = (ExtensionMessage)msg;
            OID oid = getMsg.getSubject();
            int mailID = (Integer) getMsg.getProperty("mailID");
            int itemPos = (Integer) getMsg.getProperty("itemPos");
            InventoryInfo iInfo = getInventoryInfo(oid);
            lock.lock();
            try {
            iInfo.lock();
            try {
            Log.debug("MAIL: got take mail item with mailID: " + mailID);
            ArrayList<Mail> mailList = iInfo.getMail();
            Mail m = getMailByID(mailList, mailID);
            Log.debug("MAIL: got take mail item with mailID: " + mailID);
            if (itemPos == -1 && !m.getCoD()) {
                // We are dealing with currency
                if(m.getCurrencyAmount() != 0) {
                    alterMobCurrency(oid, m.getCurrencyType(), m.getCurrencyAmount());
                    alterCurrecyLog(oid,null, m.getCurrencyType(), m.getCurrencyAmount(),"TakeMailItemHook");
                }
                m.setCurrencyAmount(0);
                iInfo.setMail(mailList);
                aDB.takeMailCurrency(mailID);
                sendMailList(oid);
                sendBagInvUpdate(oid);
                return true;
            } else if (m.getCoD()) {
                // Make sure the user has enough currency
                Currency c = Agis.CurrencyManager.get(m.getCurrencyType());
                if (c == null) {
                    ExtendedCombatMessages.sendErrorMessage(oid, "Invalid Currency");
                    log.debug("MAIL: Take Item Invalid Currency");
                    return true;
                }
                if (getMobCurrency(oid, m.getCurrencyType()) < m.getCurrencyAmount()) {
                    ExtendedCombatMessages.sendErrorMessage(oid, "You do not have enough " + c.getCurrencyName() + " to pay the CoD");
                    if(Log.loggingDebug)log.debug("MAIL: Take Item - You do not have enough " + c.getCurrencyName() + " to pay the CoD");
                   
                    return true;
                }
            }
            OID itemOID = null;
            if(itemPos >= 0 && m.getItems().size() > itemPos) {
                itemOID = m.getItems().get(itemPos);
            } else {
                if(Log.loggingDebug) log.debug("MAIL: item position "+itemPos +" is out of bounds break for mail "+mailID );
                return true;
            }
            AgisItem item = getAgisItem(itemOID);
            Log.debug("MAIL: adding item: " + item);
            if (item == null) {
                //TODO: handle this error
                Log.debug("Mail item is null from mail: " + mailID);
                return true;
            }
            
            //Added for handling items in the mail that have more items then their max stack.
	        int stackLimit = item.getStackLimit();
	        int stackSize = item.getStackSize();
	        
	        if (stackSize > stackLimit) 
	        {
	        	for (; stackSize > stackLimit; stackSize -= stackLimit) {
	        		boolean success = addItemFromTemplate(oid, item.getTemplateID(), item.getStackLimit(), null);
	        		if (!success) 
	        		{
	        			item.setStackSize(stackSize);
	        			iInfo.setMail(mailList);
	        			sendMailList(oid);
				        sendBagInvUpdate(oid);
				        ExtendedCombatMessages.sendErrorMessage(oid, "There is no space in your bags.");				        						        
	        			return true;
	        		}
	        	}
	        }
	        item.setStackSize(stackSize);
            
            boolean itemAdded = addItem(oid, oid, item.getOid());
            if (itemAdded) {
                Log.debug("MAIL: taken item: " + item);
                m.itemTaken(itemPos);
                iInfo.setMail(mailList);
                aDB.takeMailItem(mailID, itemPos, m.getCoD());
                if (m.getCoD()) {
                    // Send mail to original sender with the currency amount
                    String message = m.getRecipientName() + " has accepted your CoD request. Your payment is attached.";
                    createAndSendCharacterMail(m.getRecipientOID(), m.getSenderOID(), m.getSenderName(), "CoD Payment", message,
                            new ArrayList<OID>(), m.getCurrencyType(), m.getCurrencyAmount(), false);
                    // Set the CoD flag to false and set currency to 0
                    m.setCoD(false);
                    m.setCurrencyAmount(0);
                }
            }
            
            sendMailList(oid);
            sendBagInvUpdate(oid);
            } finally {
                iInfo.unlock();
            }
            } finally {
                lock.unlock();
            }
            return true;
        }
    }
    
    /**
     * Handles the DeleteMailMessage. Deletes the specified piece of mail from
     * the players mailbox.
     * @author Andrew
     *
     */
    class ReturnMailHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage getMsg = (ExtensionMessage)msg;
            OID oid = getMsg.getSubject();
            int mailID = (Integer) getMsg.getProperty("mailID");
            InventoryInfo iInfo = getInventoryInfo(oid);
            ArrayList<Mail> mailList = iInfo.getMail();
            Mail m = getMailByID(mailList, mailID);
            Log.debug("Setting mail deleted with mail: " + mailID);
            
            // Delete the Mail (or set status to delete) in DB JSChasle
            aDB.returnMail(m.getID(), m.getSenderOID(), m.getSenderName(), m.getRecipientOID(), 
                    m.getRecipientName(), m.getSubject(), m.getCoD());
            
            mailList.remove(m);
            iInfo.setMail(mailList);
            sendMailList(oid);
            return true;
        }
    }
    
    /**
     * Handles the DeleteMailMessage. Deletes the specified piece of mail from
     * the players mailbox.
     * @author Andrew
     *
     */
    class DeleteMailHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage getMsg = (ExtensionMessage)msg;
            OID oid = getMsg.getSubject();
            int mailID = (Integer) getMsg.getProperty("mailID");
            InventoryInfo iInfo = getInventoryInfo(oid);
            ArrayList<Mail> mailList = iInfo.getMail();
            Mail m = getMailByID(mailList, mailID);
            Log.debug("Setting mail deleted with mail: " + mailID);
            
            // Delete the Mail (or set status to delete) in DB JSChasle
            aDB.deleteMail(mailID);
            
            mailList.remove(m);
            iInfo.setMail(mailList);
            sendMailList(oid);
            return true;
        }
    }
    
    /**
     * Handles the SendMailMessage. Sends a piece of mail from the player to the
     * specified recipient.
     * @author Andrew
     *
     */
    class SendMailHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage sendMsg = (ExtensionMessage)msg;
            OID senderOid = sendMsg.getSubject();
            Log.debug("MAIL: got send mail");
            boolean isAccountMail = false;
            boolean isSystemMail = false;
            if (sendMsg.getProperty("isAccountMail") != null) {
                isAccountMail = (Boolean) sendMsg.getProperty("isAccountMail");
            }
            if (sendMsg.getProperty("isSystemMail") != null) {
            	isSystemMail = (Boolean) sendMsg.getProperty("isSystemMail");
            }
            
            OID recipientOid = null;
            String recipientName = "";
            if (!isAccountMail && !isSystemMail) {
                recipientName = (String) sendMsg.getProperty("recipient");
                //Need to check into the DB if the recipientName exist JSChasle (Done)
                recipientOid = Engine.getDatabase().getOidByName(recipientName, WorldManagerClient.NAMESPACE);          
                if (recipientOid == null || recipientOid.equals(0l)) {
                    ExtendedCombatMessages.sendErrorMessage(senderOid, "No character called '" + recipientName + "' exists.");
                    return true;
                }
            } else {
                recipientOid = (OID) sendMsg.getProperty("recipient");
            }
                    
            if(senderOid.equals(recipientOid) && !isSystemMail) {
              Serializable adminLevel = null;
              try {
                    adminLevel = EnginePlugin.getObjectProperty(senderOid, WorldManagerClient.NAMESPACE, "adminLevel");
                } catch (Exception e) {
                    log.debug("Cant get property adminLevel for "+senderOid);
                }
              if(adminLevel!=null) {
                      int lev = (int)adminLevel;
                      if (lev < 5) {
                            ExtendedCombatMessages.sendErrorMessage(senderOid, "You can't send an email to yourself.");
                            return true;
                      }
                  }else {
                    ExtendedCombatMessages.sendErrorMessage(senderOid, "You can't send an email to yourself.");
                    return true;
                  }
            }
            
            
            
            Log.debug("MAIL: got valid recipient");
            String subject = (String) sendMsg.getProperty("subject");
            String message = (String) sendMsg.getProperty("message");
            
            int numCurrencies = (Integer) sendMsg.getProperty("numCurrencies");
            int currencyType = -1;
            long currencyAmount = 0;
            for (int i = 0; i < numCurrencies; i++) {
                int cType = (Integer) sendMsg.getProperty("currencyType" + i);
                long amount = (long) sendMsg.getProperty("currencyAmount" + i);
                if (cType > 0) {
                    Currency c = Agis.CurrencyManager.get(cType);
                    while (c.getCurrencyThatConvertsToThis() != null) {
                        c = c.getCurrencyThatConvertsToThis();
                        amount *= c.getConversionAmountReq();
                    }
                    currencyAmount += amount;
                    currencyType = c.getCurrencyID();
                }
            }
            Log.debug("MAIL: handled currency with type: " + currencyType + " and amount: " + currencyAmount);
            
            String result = "";
            if (!isAccountMail && !isSystemMail) {
                boolean CoD = (Boolean) sendMsg.getProperty("CoD");
                int numItems = (Integer) sendMsg.getProperty("numItems");
                if (CoD && numItems < 1) {
                    ExtendedCombatMessages.sendErrorMessage(senderOid, "It is required to attach the item to the message in order to send it with the CoD option");
                    return false;
                }
                    
                ArrayList<OID> items = new ArrayList<OID>();
                for (int i = 0; i < numItems; i++) {
	        		OID itemOid =(OID) sendMsg.getProperty("item" + i);
	        		if(!items.contains(itemOid))
	        			items.add(itemOid);
                }
                Log.debug("MAIL: handled items");
            //  AgisInventoryClient.checkComponents(senderOid, new LinkedList<Integer>(reagentList.keySet()), new LinkedList<Integer>(reagentList.values()))
                      
                
                //OID mailOID = Engine.getOIDManager().getNextOid();
                result = createAndSendCharacterMail(senderOid, recipientOid, recipientName, subject, message, items, currencyType, currencyAmount, CoD);
                // Send a message to the sender so the client knows the result of the sending
                Map<String, Serializable> props = new HashMap<String, Serializable>();
                props.put("ext_msg_subtype", "MailSendResult");
                props.put("result", result);
                TargetedExtensionMessage successMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, senderOid, senderOid, false, props);
                Engine.getAgent().sendBroadcast(successMsg);
            } else if (isAccountMail) {
                HashMap<Integer, Integer> items = (HashMap) sendMsg.getProperty("items");
                String senderName = "System";
                result = createAndSendAccountMail(senderOid, senderName, recipientOid, recipientName, subject, message, items, currencyType, currencyAmount, true);
            } else if (isSystemMail) {
            	HashMap<Integer, Integer> items = (HashMap) sendMsg.getProperty("items");
                String senderName = "System";
                result = createAndSendAccountMail(senderOid, senderName, recipientOid, recipientName, subject, message, items, currencyType, currencyAmount, false);
            }
            
            if (result.equals("Success")) {
                Log.debug("MAIL: mail sent");
            } else {
                Log.warn("MAIL: mail was not sent to player: " + recipientOid);
            }
            return true;
        }
    }
    
    /**
     * Creates a new mail item from the parameters given and sends it to the recipient. The created mail object
     * is saved to the database.
     * @param senderOid
     * @param recipientOid
     * @param recipientName
     * @param subject
     * @param message
     * @param items
     * @param currencyType
     * @param currencyAmount
     * @param CoD
     * @return
     */
    private String createAndSendCharacterMail(OID senderOid, OID recipientOid, String recipientName, String subject, String message, ArrayList<OID> items, int currencyType, long currencyAmount, boolean CoD) {
        log.debug("createAndSendCharacterMail Start");
        String senderName = "";
        try {
            if (senderOid != null) {
                senderName = aDB.getCharacterNameByOid(senderOid);
                //senderName = WorldManagerClient.getObjectInfo(senderOid).name;
            } else {
                senderName = "Store";
            }
        } catch (Exception e) {
            Log.error("AgisInventoryPlugin.createAndSendCharacterMail: cant read ObjectInfo for "+senderOid+" from wmgr");
        }
        Mail m =null;
        lock.lock();
        try {
            // Do a currency check first
            if (senderOid != null) {
                if (currencyAmount > 0 && !CoD) {
                    Currency c = Agis.CurrencyManager.get(currencyType);
                    if (c == null) {
                        ExtendedCombatMessages.sendErrorMessage(senderOid, "Invalid Currency");
                        return "Invalid Currency";
                    }
                    if (getMobCurrency(senderOid, currencyType) < currencyAmount) {
                        ExtendedCombatMessages.sendErrorMessage(senderOid, "You cannot send more " + c.getCurrencyName() + " than you have");
                        return "Not enough Currency";
                    }
                }
            }
            log.debug("createAndSendCharacterMail ");
            if (senderOid != null) {
                // Check items are not bound to the player
                for (OID itemOid : items) {
                    if (itemOid == null) {
                        continue;
                    }
                    AgisItem item = getAgisItem(itemOid);
                    if (item == null) {
                        log.debug("createAndSendCharacterMail: item is null Cannot send null Item");
                        return "Invalid Item";
                    }
                    if (!item.canBeTraded()) {
                        log.debug("createAndSendCharacterMail: Cannot send bound Item");

                        return "Cannot send bound Item";
                    }
                    OID oldBagID = (OID) item.getProperty(INVENTORY_PROP_BACKREF_KEY);
                    log.debug("createAndSendCharacterMail: item:" + item + " oldBagID:" + oldBagID);
                    if (oldBagID == null) {
                        log.debug("createAndSendCharacterMail: item is not assign to bag cannot send mail");
                        return "Invalid Item";
                    }
                    if (!hasItem(senderOid, itemOid)) {
                        log.debug("createAndSendCharacterMail: item is not assign to bag cannot send mail");
                        return "Invalid Item";
                    }
                }

                // Remove items and currency from senders inventory
                for (OID itemOid : items) {
                    if (itemOid != null) {
                        removeItem(senderOid, itemOid, true);
                    }
                }
                if (currencyType > 0 && currencyAmount > 0 && !CoD) {
                    alterMobCurrency(senderOid, currencyType, -currencyAmount);
                    alterCurrecyLog(senderOid,null, currencyType, -currencyAmount,"createAndSendCharacterMail");

                }
            }

            Log.debug("MAIL: creating mail");
            m = new Mail(-1, false, recipientOid, recipientName, senderOid, senderName, subject, message, currencyType, currencyAmount, items, 1, CoD);
            log.debug("createAndSendCharacterMail: " + m);
            aDB.addNewMail(m);
            log.debug("createAndSendCharacterMail: after save " + m);
        } finally {
            lock.unlock();
        }
        Log.debug("MAIL: created mail object");
        InventoryInfo iInfo = getInventoryInfo(recipientOid);
        
        if (iInfo != null && m != null) {
            log.debug("createAndSendCharacterMail: bedore add "+m);
            iInfo.addMail(m);
            sendMailList(recipientOid);
            ChatClient.sendObjChatMsg(recipientOid, 2, "You have recieved new mail from " + senderName);
        }

        if (senderOid != null) {
            sendBagInvUpdate(senderOid);
        }
        return "Success";
    }
    
    private String createAndSendAccountMail(OID senderOid, String senderName, OID recipientOid, String recipientName, String subject, 
            String message, HashMap<Integer, Integer> items, int currencyType, long currencyAmount, boolean isAccountMail) {
        //String senderName = WorldManagerClient.getObjectInfo(senderOid).name;
        // Do a currency check first
        if (currencyAmount > 0) {
            Currency c = Agis.CurrencyManager.get(currencyType);
            if (c == null) {
                return "Invalid Currency";
            }
        }
        
        // Generate Items
        ArrayList<OID> generatedItems = new ArrayList<OID>();
        for(int itemID : items.keySet()) {
            OID itemOid = generateItem(itemID, null);
            AgisItem item = getAgisItem(itemOid);
            item.setStackSize(items.get(itemID));
            ObjectManagerClient.saveObject(itemOid);
            generatedItems.add(itemOid);
        }
        
        Log.debug("MAIL: creating mail");
        Mail m = new Mail(-1, isAccountMail, recipientOid, recipientName, senderOid, senderName, subject,
                message, currencyType, currencyAmount, generatedItems, 1, false);
        aDB.addNewMail(m);
        
        Log.debug("MAIL: created mail object");
        InventoryInfo iInfo = getInventoryInfo(senderOid);
        if (iInfo != null) {
            iInfo.addMail(m);
            ChatClient.sendObjChatMsg(senderOid, 2, "You have recieved new mail from " + senderName);
            try {
				sendMailList(senderOid);
			} catch (Exception e) {
			}
        }
        
        if (senderOid != null) {
            sendBagInvUpdate(senderOid);
        }
        return "Success";
    }
    
    /**
     * Handles the SendPurchaseMailMessage. Creates a new mail object and sends 
     * it to the buyer.
     *
     */
    class SendPurchaseMailHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            AgisInventoryClient.sendPurchaseMailMessage getMsg = (AgisInventoryClient.sendPurchaseMailMessage)msg;
            OID recipientOID = getMsg.getRecipientOid();
            boolean isAccountMail = getMsg.isAccountMail();
            Log.debug("MAIL: sending purchase item mail for character: " + recipientOID);
            String subject = "Item Shop Purchase";
            String message = "Thank you for shopping at the Item Shop. Your purchase has been included in this mail.";
            ArrayList<OID> items = new ArrayList<OID>();
            HashMap<Integer, Integer> itemsToGive = getMsg.getItems();
            for (int itemID : itemsToGive.keySet()) {
                Template tmpl = ObjectManagerPlugin.getTemplate(itemID, ObjectManagerPlugin.ITEM_TEMPLATE);
                String itemName = tmpl.getName();
                OID itemOid = generateItem(itemID, itemName);
                if (!itemOid.equals(0l)) {
                    AgisItem item = getAgisItem(itemOid);
                    item.setStackSize(itemsToGive.get(itemID));
                    ObjectManagerClient.saveObject(itemOid);
                    items.add(itemOid);
                }
            }
            //OID mailOID = Engine.getOIDManager().getNextOid();
            String senderName = "The Item Shop";
            int currencyType = 0;
            int currencyAmount = 0;
            
            boolean CoD = false;
            Mail m = new Mail(-1, isAccountMail, recipientOID, "", null, senderName, subject,
                    message, currencyType, currencyAmount, items, 1, CoD);
            aDB.addNewMail(m);
            if (isAccountMail) {
                recipientOID = getMsg.getCharacterOID();
                if (recipientOID == null) {
                    return true;
                }
            }
            InventoryInfo iInfo = getInventoryInfo(recipientOID);
            iInfo.addMail(m);
            sendMailList(recipientOID);
            ChatClient.sendObjChatMsg(recipientOID, 2, "You have recieved new mail from " + senderName);
            return true;
        }
    }
    
    /**
     * Simple function used to get a piece of mail from the list of mail items by
     * its ID.
     * @param mailList
     * @param mailID
     * @return
     */
    private Mail getMailByID(ArrayList<Mail> mailList, int mailID) {
        for (Mail m : mailList) {
            if (m.getID() == mailID)
                return m;
        }
        return null;
    }
    /***
     * 
     * @param playerOid
     */
    public static void SendMailList(OID playerOid) {
    //  sendMailList(playerOid);
    }
    /**
     * Sends down the list of mail a player has to their client.
     * @param playerOid
     */
    private void sendMailList(OID playerOid) {
        // Check external mail database
        OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.ACCOUNT_PROPERTY);
        authDB.checkAccountPurchases(playerOid, accountID);

        InventoryInfo iInfo = getInventoryInfo(playerOid);
        if(iInfo==null){
            log.warn("sendMailList: Not found InventoryInfo for "+playerOid+" break");
            return;
        }
        ArrayList<Mail> mailList = iInfo.getMail();

        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "MailList");
        props.put("numMail", mailList.size());
        for (int pos = 0; pos < mailList.size(); pos++) {
            Mail m = mailList.get(pos);
            if(Log.loggingDebug)
            log.debug("sendMailList: "+m);
            props.put("mail_" + pos + "ID", m.getID());
            props.put("mail_" + pos + "SenderOid", m.getSenderOID());
            props.put("mail_" + pos + "SenderName", m.getSenderName());
            props.put("mail_" + pos + "Subject", m.getSubject());
            props.put("mail_" + pos + "Message", m.getMessage());
            props.put("mail_" + pos + "Read", m.getMailRead());
            for (int i = 0; i < m.getItems().size(); i++) {
                if (m.getItems().get(i) == null) {
                    props.put("mail_" + pos + "ItemTemplate" + i, -1);
                    props.put("mail_" + pos + "ItemName" + i, "");
                    props.put("mail_" + pos + "ItemCount" + i, 0);
                } else {
                    AgisItem item = getAgisItem(m.getItems().get(i));
                    if (item == null) {
                        props.put("mail_" + pos + "ItemTemplate" + i, -1);
                        props.put("mail_" + pos + "ItemName" + i, "");
                        props.put("mail_" + pos + "ItemCount" + i, 0);
                        log.trace("MAIL: got null item: " + item + " for mail: " + m.getID());
                        continue;
                    }
                    props.put("mail_" + pos + "ItemTemplate" + i, item.getTemplateID());
                    props.put("mail_" + pos + "ItemName" + i, item.getName());
                    props.put("mail_" + pos + "ItemCount" + i, item.getStackSize());
                }
            }
            props.put("mail_" + pos + "NumItems", m.getItems().size());
            props.put("mail_" + pos + "CurrencyType", m.getCurrencyType());
            props.put("mail_" + pos + "CurrencyAmount", m.getCurrencyAmount());
            props.put("mail_" + pos + "CoD", m.getCoD());
        }

        TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
        Engine.getAgent().sendBroadcast(msg);
    }
    
    /**
     * Hook for the CheckComponentMessage. Checks if the player has all the specified items 
     * in their inventory. Sends a Boolean response.
     * @author Andrew Harrison
     *
     */
    class CheckComponentHook implements Hook {
        public boolean processMessage(Message arg0, int arg1) { 
            CheckComponentMessage msg = (CheckComponentMessage)arg0;    
            
            // Group together the requirements
            if (msg.gridSystem) {
                HashMap<Integer, Integer> requirements = new HashMap<Integer, Integer>();
                HashMap<Integer, Integer> itemTotals = new HashMap<Integer, Integer>();
                for (int i = 0; i < msg._reqComponents.size(); i++) {
                    int reqComponent = msg._reqComponents.get(i);
                    int reqCount = msg._reqStackSizes.get(i);
                    if (reqComponent > 0) {
                        if (!requirements.containsKey(reqComponent)) {
                            requirements.put(reqComponent, reqCount);
                            Log.debug("CHECK: set required itemID " + reqComponent + " to count " + reqCount);
                        } else {
                            reqCount += requirements.get(reqComponent);
                            requirements.put(reqComponent, reqCount);
                            Log.debug("CHECK: set required itemID " + reqComponent + " to count " + reqCount);
                        }
                        itemTotals.put(reqComponent, 0);
                    }
                }
            
                HashMap<Integer, Integer> provided = new HashMap<Integer, Integer>();
                HashMap<Long, Integer> providedIds = new HashMap<Long, Integer>();
                for (int i = 0; i < msg._components.size(); i++) {
                    OID component = OID.fromLong(msg._components.get(i));
                    int providedItemID = getAgisItem(component).getTemplateID();
                    int count = msg._componentCounts.get(i);
                    if (!provided.containsKey(providedItemID)) {
                        provided.put(providedItemID, count);
                        providedIds.put(msg._components.get(i), count);
                        Log.debug("CHECK: set provided itemID " + providedItemID + " to count " + count);
                    } else {
                        count += provided.get(providedItemID);
                        provided.put(providedItemID, count);
                        providedIds.put(msg._components.get(i), count);
                        Log.debug("CHECK: set provided itemID " + providedItemID + " to count " + count);
                    }
                }
                
                // Now verify the user has the items they said they did
                for (Long providedId : providedIds.keySet()) {
                    OID component = OID.fromLong(providedId);
                    AgisItem tempItem = getAgisItem(component);
                    if (tempItem == null) {
                        Log.debug("CHECK: got null item: " + providedId);
                        Engine.getAgent().sendObjectResponse(msg, false);   
                        return true;
                    }
                    if (!itemTotals.containsKey(tempItem.getTemplateID())) {
                        Log.debug("CHECK: got non-existant key: " + tempItem.getTemplateID());
                        Engine.getAgent().sendObjectResponse(msg, false);   
                        return true;
                    }
                    //Log.debug("CHECK: stackSize " + tempItem.getStackSize() + " for item: " + component + " with count: " + providedIds.get(providedId));
                    itemTotals.put(tempItem.getTemplateID(), itemTotals.get(tempItem.getTemplateID()) + tempItem.getStackSize());
                }
            
                // Check each required item to see if the provided count matches
                for (Integer itemID : requirements.keySet()) {
                    if (!provided.containsKey(itemID)) {
                        Log.debug("CHECK: itemID " + itemID + " was not found in the provided list");
                        Engine.getAgent().sendObjectResponse(msg, false);   
                        return true;    
                    } else if (provided.get(itemID) < requirements.get(itemID)) {
                        Log.debug("CHECK: itemID " + itemID + " required " + requirements.get(itemID) + " but player only provided " + provided.get(itemID));
                        Engine.getAgent().sendObjectResponse(msg, false);   
                        return true;
                    } else if (itemTotals.get(itemID) < requirements.get(itemID)) {
                        Log.debug("CHECK: itemID " + itemID + " required " + requirements.get(itemID) + " but player only had " + itemTotals.get(itemID));
                        Engine.getAgent().sendObjectResponse(msg, false);   
                        return true;
                    }
                }
                
            } else if(msg.craftBook){
                
                OID mobOid = msg.getSubject();
                    
                //  HashMap<OID,Integer> ItemsCount = new HashMap<OID,Integer>();
                  HashMap<Integer,Integer> ItemsCount = new HashMap<Integer,Integer>();
                        
                InventoryInfo iInfo = getInventoryInfo(mobOid);
                if (iInfo == null ) {
                    log.debug("GetItemsWithParamHook:  InventoryInfo is null");
                    return true;
                }
                OID[] subBags = iInfo.getBags();
                for (OID bag : subBags) {
                    Bag subBag = getBag(bag);
                    OID[] itemsInBag = subBag.getItemsList();
                    log.debug("CheckComponentHook:  subBagOid:" + bag + " subBags:" + Arrays.toString(subBags) + " itemsInBag:" + Arrays.toString(itemsInBag));
                    for (OID ite : itemsInBag) {
                        AgisItem Item = getAgisItem(ite);
                        log.debug("CheckComponentHook: ite:" + ite + " Item :" + Item);
                        if (Item != null) {
                            String unicItem = Item.getTemplateID() + "";
                            if(ItemsCount.containsKey(Item.getTemplateID())) {
                                ItemsCount.replace(Item.getTemplateID(), ItemsCount.get(Item.getTemplateID())+Item.getStackSize());
                            }else {
                                ItemsCount.put(Item.getTemplateID(), Item.getStackSize());
                            }
                        } else {
                            log.debug("GetItemsWithParamHook:Item is null");
                        }
                    }
                }   
                log.debug("CheckComponentHook: Bag ItemsCount:"+ItemsCount);
                log.debug("CheckComponentHook: "+msg.ItemsCount);
                log.debug("CheckComponentHook: "+msg.ItemsCount.keySet());
                log.debug("CheckComponentHook: "+msg.ItemsCount.values());
                for (int key : msg.ItemsCount.keySet()) {
                    if (ItemsCount.containsKey(key)) {
                        if (ItemsCount.get(key) >= msg.ItemsCount.get(key)) {
                            log.debug("CheckComponentHook: CHECK: find enough of the required item ItemID:"+key+" count:"+msg.ItemsCount.get(key));
                        }else {
                            log.debug("CheckComponentHook: CHECK: did not find enough of the required item ItemID:"+key+" count:"+msg.ItemsCount.get(key)+" found count:"+ItemsCount.get(key));
                            Engine.getAgent().sendObjectResponse(msg, false);   
                            return true;
                        }
                    } else {
                        log.debug("CheckComponentHook: CHECK: did not find the required item ItemID:"+key);
                        Engine.getAgent().sendObjectResponse(msg, false);
                        return true;
                    }
                }
                
            /*  for (int i = 0; i < msg._reqComponents.size(); i++) {
                    int reqComponent = msg._reqComponents.get(i);
                    int reqCount = msg._reqStackSizes.get(i);
                    if (ItemsCount.containsKey(reqComponent)) {
                        if (ItemsCount.get(reqComponent)>=reqCount) {
                            
                        }else {
                            Log.debug("CHECK: did not find enough of the required item");
                            Engine.getAgent().sendObjectResponse(msg, false);   
                        }
                    } else {
                        Log.debug("CHECK: did not find the required item");
                        Engine.getAgent().sendObjectResponse(msg, false);   
                    }
                }*/
            }else {
                ArrayList<Integer> test = new ArrayList<Integer>();
                test.addAll(msg._reqComponents); 
                HashMap<Integer, Integer> requirements = new HashMap<Integer, Integer>();
                for (int i = 0; i < msg._reqComponents.size(); i++) {
                    int reqComponent = msg._reqComponents.get(i);
                    int reqCount = msg._reqStackSizes.get(i);
                    if (!requirements.containsKey(reqComponent)) {
                        requirements.put(reqComponent, reqCount);
                        Log.debug("CHECK: set required itemID " + reqComponent + " to count " + reqCount);
                    } else {
                        reqCount += requirements.get(reqComponent);
                        requirements.put(reqComponent, reqCount);
                        Log.debug("CHECK: set required itemID " + reqComponent + " to count " + reqCount);
                    }
                }
                
                for (int itemID : requirements.keySet()) {
                    int count = requirements.get(itemID);
                    int amountFound = 0;
                    // Check if there are any existing stacks to add to
                    ArrayList<OID> existingOids = findItemStacks(msg._subject, itemID);
                    if (existingOids.size() > 0) {
                        Log.debug("CHECK: user has item " + itemID);
                        for (OID existingOid: existingOids) {
                            AgisItem tempItem = getAgisItem(existingOid);
                            Log.debug("CHECK: increasing amountFound for item: " + existingOid);
                            amountFound += tempItem.getStackSize();
                        }
                    }
                    
                    if (amountFound < count) {
                        Log.debug("CHECK: did not find enough of the required item");
                        Engine.getAgent().sendObjectResponse(msg, false);
                        return true;
                    }
                }
                /*Log.debug("CHECK: checking for items: " + test.toString());
                List<OID> itemList = InventoryClient.findItems(msg._subject, test);
                Log.debug("CHECK: found items: " + itemList.toString());
                if(itemList == null || itemList.contains(null)){
                    Engine.getAgent().sendObjectResponse(msg, false);
                    return true;
                }*/
            }
            Log.debug("CHECK: passed item check");
            Engine.getAgent().sendObjectResponse(msg, true);    
            return true;    
        }
    }  
    
    public class LockpickHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            // get player oid
            // get skill level
            // get lock skill requirement
            // get bag
            // percent = (skill / lock skill) * 100
            // clamp percent between 10 and 100
            // chance = percent / 2
            // Get random number between 1 and 100
            // if number is <= chance then success
            //    pick random item from chest
            //    Open loot table with item
            //    Give stealing exp to player
            //    Reduce players alignment
            // else
            //    Exit
            log.debug("LOCKPICK: Started Lockpick");
            Random random = new Random(Calendar.getInstance().getTimeInMillis());
            AgisInventoryClient.LockpickMessage sMsg = (AgisInventoryClient.LockpickMessage)msg;
            OID playerOid = (OID)sMsg.getSubject();
            int skillLevel = (int)sMsg.getSkillLevel();
            OID targetOid = (OID)sMsg.getTargetOid();
            log.debug("LOCKPICK: Started Lockpick with targetOid: " + targetOid + " as long: " + targetOid.toLong());
            int statReq = 75;
            Integer stealingSkillID = 16;
            int lockSkillReq;
            int maxValue = 100;
            int minValue = 10;
            InventoryInfo iInfo = getInventoryInfo(playerOid);
            OID storageOid = VoxelClient.getChestStorageOid((int)targetOid.toLong());
            if (storageOid == null) {
                log.error("LOCKPICK: Couldn't get storageOid of chest");
                return true;
            }
            Bag bag = loadNonPlayerStorageBag(storageOid);
            //Bag bag = getBag(OID.fromString(iInfo.getActiveStorage()));
            if (bag == null) {
                log.debug("LOCKPICK: Couldn't find bag.");
                return false;
            }
            if(bag.getChestLock() == null) {
                log.warn("LOCKPICK: No lock on chest!");
                return false;
            }
            AgisItem chestLock = getAgisItem(bag.getChestLock());
            if(chestLock != null) {
                HashMap<Integer, HashMap<String, Integer>> lockReqs = (HashMap<Integer, HashMap<String, Integer>>)chestLock.getProperty("requirements");
                if (lockReqs.containsKey(statReq) && lockReqs.get(statReq).containsKey(stealingSkillID.toString())) {
                    lockSkillReq = lockReqs.get(statReq).get(stealingSkillID.toString());
                } else {
                    lockSkillReq = 100;
                }
            } else {
                lockSkillReq = 100;
            }
            
            // Reduce karma faction and set player to pvp
                FactionClient.alterReputation(playerOid, 5, -1);
                HashMap<String, Serializable> props = new HashMap<String, Serializable>();
                props.put("pvpState", true);
                ExtensionMessage eMsg = new WorldManagerClient.ExtensionMessage(FactionClient.MSG_TYPE_UPDATE_PVP_STATE, playerOid, props);
                Engine.getAgent().sendBroadcast(eMsg);
                
            float percent = ((float)skillLevel / (float)lockSkillReq) * 100;
            log.debug("LOCKPICK: " + skillLevel + "/" + lockSkillReq);
            if (percent > maxValue) {
                percent = maxValue;
            } else if (percent < minValue) {
                percent = minValue;
            }
            float chance = percent / 2;
            int roll = random.nextInt(100) + 1;
            log.debug("LOCKPICK: Attempting steal: " + roll + "/" + chance);
            if(roll <= chance) {
                if(chestLock.getProperty("Durability") != null) {
                    int durability = (Integer) chestLock.getProperty("Durability");
                    if(durability <= 0) {
                        bag.removeChestLocks(1);
                        saveNonPlayerStorageBag(bag);
                    }
                }
                
                // Now do loot stuff
                if (bag.isEmpty()) {
                    ExtendedCombatMessages.sendErrorMessage(playerOid, "Chest is empty!");
                    return false;
                }
                
                OID item = getRandomBagItem(bag, random);
                if(item != null) {
                    sendLockpickLootList(playerOid, item, bag.getOid(), 1);
                    reduceDurabilityOfItem(playerOid, bag.getChestLock(), false);
                    saveNonPlayerStorageBag(bag);
                } else {
                    log.debug("LOCKPICK: Did not get any item from the bag.");
                }
            }
            log.debug("LOCKPICK: Finished Lockpick.");
            return true;
        }
    }
    
    private OID getRandomBagItem(Bag bag, Random random) {
        int amount = bag.itemCount();
        OID[] list = bag.getItemsList();
        int roll = random.nextInt(amount);
        int count = 0;
        log.debug("LOCKPICK: Got " + amount + " items. Got roll: " + roll + " producing item: " + list[roll]);
        for(int i = 0; i < list.length; i++) {
            log.debug("LOCKPICK: Got item in slot" + i + ": " + list[i]); 
            if (list[i] == null) {
                //count++;
                continue;
            }
            if(count == roll)
                return list[i];
            if(list[i] != null)
                count++;
        }
        return null;
    }
    
    /**
     * Don't use.
     * @author Andrew
     *
     */
    class UseAccountItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            /*ExtensionMessage eMsg = (ExtensionMessage)msg;
            OID playerOid = eMsg.getSubject();
            int itemID = (Integer) eMsg.getProperty("itemID");
            Template itemTemplate = ObjectManagerClient.getTemplate(itemID, ObjectManagerPlugin.ITEM_TEMPLATE);*/
            // 
            return true;
        }
    }
    
    
    class GetItemProperty implements Hook{
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage)msg;
            OID playerOid = eMsg.getSubject();
            String property = (String) eMsg.getProperty("itemProp");
            OID itemOid = (OID) eMsg.getProperty("itemOid");
            log.debug("GetItemProperty: playerOid: " +playerOid+" property:"+property+" itemOid:"+itemOid);
            AgisItem item = getAgisItem(itemOid);
        
             if (item!=null) {
                Log.debug("GetItemProperty: "+item.getName()+" "+ item.getProperty(property)+ " "+ item.getPropertyMap());
                  Engine.getAgent().sendObjectResponse(msg, item.getProperty(property));
             }else {
              Engine.getAgent().sendObjectResponse(msg, null);
             }
          
            return true;
            }
    }
    
    class GetSetsFromEquiped implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID playerOid = eMsg.getSubject();
            log.debug("GetSetsFromEquiped: playerOid: " + playerOid);
            HashMap<Integer, Integer> list = new HashMap<Integer, Integer>();

            list = getEquipSetsInfo(playerOid);
            Log.debug("GetSetsFromEquiped:  ");
            Engine.getAgent().sendObjectResponse(msg, list);

            return true;
        }
    }
    
    protected HashMap<Integer, Integer> getEquipSetsInfo(OID playerOid){
        log.debug("getEquipSetsInfo: playerOid: " + playerOid);
        
        AgisItem item = null;// = getAgisItem(itemOid);
        OID itemOID;
        EquipMap equipMap;
        HashMap<Integer, Integer> list = new HashMap<Integer, Integer>();
        //lock.lock();
        ArrayList<OID> checkItems = new ArrayList<OID>();
        try {
            equipMap = getEquipMap(playerOid);
            
            Map<String, AgisEquipSlot> slots = AgisEquipSlot.getSlots();
            int weaponType = RequirementChecker.getIdEditorOptionChoice("Item Slot Type", "Weapon");
            int armorType = RequirementChecker.getIdEditorOptionChoice("Item Slot Type", "Armor");
            for (AgisEquipSlot aes : slots.values()) {
                if(!aes.getName().startsWith("Set_"))
                if (aes.getTypeIds().contains(weaponType) || aes.getTypeIds().contains(armorType)) {
                    itemOID = equipMap.get(aes);
                    if (itemOID != null) {
                        checkItems.add(itemOID);
                        item = getAgisItem(itemOID);
                        if (item != null) 
                        {
                            int setId = (int) item.getProperty("item_set");
							if (setId > 0) 
							{								
								if (EquipHelper.IsDurabilityOk(item)) 
								{
									if (list.containsKey(setId))
										list.replace(setId, list.get(setId) + 1);
									else
	                                    list.put(setId, 1);
	                        	}
                            }
                        }
                    }
                }
			}
       
        } catch (Exception e) {
            log.error("getEquipSetsInfo Exception:"+e.getLocalizedMessage()+" "+e.getMessage());
        } finally {
        //  lock.unlock();
        }
        log.debug("getEquipSetsInfo: end list: " + list);
        
        return list;
        
        
    }
    
    class SocketingResetDetaile implements Hook{
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage)msg;
            OID playerOid = eMsg.getSubject();
            OID itemOid = (OID) eMsg.getProperty("itemOid");
            log.debug("SocketingResetDetaile: playerOid: " +playerOid+"  itemOid:"+itemOid);
            AgisItem item = getAgisItem(itemOid);
            log.debug("SocketingResetDetaile: playerOid: " +playerOid+" item:"+item);
            
            float vipModp = 0f;
            long vipMod = 0l;
             InventoryInfo iInfo = getInventoryInfo(playerOid);
            if(iInfo.getBonuses().containsKey("ResetSocketsCost")) {
                vipMod =iInfo.getBonuses().get("ResetSocketsCost").GetValue();
                vipModp =iInfo.getBonuses().get("ResetSocketsCost").GetValuePercentage();
            }
            if(globalEventBonusesArray.containsKey("ResetSocketsCost")) {
                vipMod += globalEventBonusesArray.get("ResetSocketsCost").GetValue();
                vipModp += globalEventBonusesArray.get("ResetSocketsCost").GetValuePercentage();
            }
            log.debug("SocketingResetDetaile: ResetSocketsCost v="+vipMod+" p="+vipModp);
            
            float cost = (SOCKET_RESET_PRICE_BASE + ((int) item.getProperty("itemGrade")) * SOCKET_RESET_PRICE_PER_GRADE);
            long soketingCost = Math.round(Math.ceil(cost + vipMod + cost * vipModp / 100f));
            if (soketingCost < 0)
                soketingCost = 0l; 
            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("ext_msg_subtype", "SocketResetMsg");
            props.put("PluginMessageType", "SocketResetUpdate");
            props.put("creationCost", soketingCost);
            props.put("creationCurrency", SOCKET_RESET_PRICE_CURRENCY);
            TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
            Engine.getAgent().sendBroadcast(playerMsg);
            log.debug("SocketingResetDetaile: playerOid: " +playerOid+" item:"+item+" Send");
              
            
            
            return true;
            }
    }
    
    class SocketingReset implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage)msg;
            OID playerOid = eMsg.getSubject();
            OID itemOid = (OID) eMsg.getProperty("itemOid");
            log.debug("SocketingReset: playerOid: " +playerOid+" itemOid:"+itemOid);
            InventoryInfo iInfo = getInventoryInfo(playerOid);
            if (iInfo == null) {
                log.debug("SocketingReset: iInfo is null");
                return true;
            }
            OID[] subBags = iInfo.getBags();
            AgisItem item = getAgisItem(itemOid);
               long currencyAmount = getMobCurrency(playerOid, SOCKET_RESET_PRICE_CURRENCY);
              // Currency c = Agis.CurrencyManager.get(SOCKET_RESET_PRICE_CURRENCY);
        
            float vipModp = 0f;
            float vipModChance = 0f;
            long vipMod = 0l;
            long vipModtime = 0l;
            // InventoryInfo iInfo = getInventoryInfo(playerOid);
            if (iInfo.getBonuses().containsKey("ResetSocketsCost")) {
                vipMod = iInfo.getBonuses().get("ResetSocketsCost").GetValue();
                vipModp = iInfo.getBonuses().get("ResetSocketsCost").GetValuePercentage();
            }
            if (globalEventBonusesArray.containsKey("ResetSocketsCost")) {
                vipMod += globalEventBonusesArray.get("ResetSocketsCost").GetValue();
                vipModp += globalEventBonusesArray.get("ResetSocketsCost").GetValuePercentage();
            }

            if (iInfo.getBonuses().containsKey("ResetSocketsChance")) {
                vipModChance = iInfo.getBonuses().get("ResetSocketsChance").GetValuePercentage();
            }
            if (globalEventBonusesArray.containsKey("ResetSocketsChance")) {
                vipModChance += globalEventBonusesArray.get("ResetSocketsChance").GetValuePercentage();
            }

            if (iInfo.getBonuses().containsKey("ResetSocketsTime")) {
                vipModtime = iInfo.getBonuses().get("ResetSocketsTime").GetValue();
            }
            if (globalEventBonusesArray.containsKey("ResetSocketsTime")) {
                vipModtime += globalEventBonusesArray.get("ResetSocketsTime").GetValue();
            }

            log.debug("SocketingReset: ResetSocketsCost v=" + vipMod + " p=" + vipModp + "; ResetSocketsChance p=" + vipModChance + "; ResetSocketsTime v=" + vipModtime);

            float cost = (SOCKET_RESET_PRICE_BASE + ((int) item.getProperty("itemGrade")) * SOCKET_RESET_PRICE_PER_GRADE);
            long soketingCost = Math.round(Math.ceil(cost + vipMod + cost * vipModp / 100f));
            if (soketingCost < 0)
                soketingCost = 0l;
               
               
               if (currencyAmount < (soketingCost)) {
                    ExtendedCombatMessages.sendErrorMessage(playerOid, "InsufficientFunds");
                    Log.debug("SocketingReset: insufficient funds currencyAmount:"+currencyAmount);
                       return false;
                }
            //    alterMobCurrency(playerOid, SOCKET_PRICE_CURRENCY, -(SOCKET_PRICE_BASE+((int)socketItem.getProperty("itemGrade"))+SOCKET_PRICE_PER_GRADE));
              if (item==null ) {
                log.debug("SocketingReset: itemOid: "+itemOid+" is "+item);
                return true;
                }
            log.debug("SocketingReset: item stackSize: " + item.getStackSize());
            
                OID oldBagID = (OID) item.getProperty(INVENTORY_PROP_BACKREF_KEY);
                log.debug("SocketingReset: subBags:"+Arrays.toString(subBags)+" BagID:"+oldBagID);
                if (oldBagID == null) {
                    log.debug("SocketingReset: Got null inventory backref for item: " + itemOid);
                    return true;
                }
            //  HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) item.getProperty("sockets");
                
            //  Set<Integer> keys = itemSockets.keySet();
            //  for (Integer i : keys) { 
                //  log.error("SocketingReset: itemSockets.get(i).GetType()"+itemSockets.get(i).GetType()+" itemSockets.get(i).GetItemOid():"+itemSockets.get(i).GetItemOid());
                //  itemSockets.get(i).SetItemOid(null);
                        //Create Soceting task
			SocketResetTask task = new SocketResetTask(playerOid, item, SOCKET_RESET_TIME + vipModtime, SOCKET_RESET_CHANCE + SOCKET_RESET_CHANCE * vipModChance / 100F, SOCKET_RESET_PRICE_CURRENCY, -(soketingCost));
			Engine.getExecutor().schedule(task, SOCKET_RESET_TIME + vipModtime, TimeUnit.SECONDS);
                 //return true;
                    
                        
            //  }
            
            // Remove the item from the normal bag and place it in the equipped bag
          //    ExtendedCombatMessages.sendErrorMessage(playerOid, "No empty sockets ");
            
            return true;
        }
    }
    
    
    
    class SocketingDetaile implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID playerOid = eMsg.getSubject();
            OID socketItemOid = (OID) eMsg.getProperty("socketItemOid");
            OID itemOid = (OID) eMsg.getProperty("itemOid");
            log.debug("SocketingDetaile: playerOid: " + playerOid + " socketItemOid:" + socketItemOid + " itemOid:" + itemOid);

            AgisItem socketItem = getAgisItem(socketItemOid);
            AgisItem item = getAgisItem(itemOid);
            log.debug("SocketingDetaile: playerOid: " + playerOid + " socketItem:" + socketItem + " item:" + item);

            float vipModp = 0f;
            float vipModChance = 0f;
            long vipMod = 0l;
            long vipModtime = 0l;
              InventoryInfo iInfo = getInventoryInfo(playerOid);
                if(iInfo.getBonuses().containsKey("SocketingCost")) {
                    vipMod =iInfo.getBonuses().get("SocketingCost").GetValue();
                    vipModp =iInfo.getBonuses().get("SocketingCost").GetValuePercentage();
                }
                if(globalEventBonusesArray.containsKey("SocketingCost")) {
                    vipMod += globalEventBonusesArray.get("SocketingCost").GetValue();
                    vipModp += globalEventBonusesArray.get("SocketingCost").GetValuePercentage();
                }
                log.debug("SocketingDetaile: SocketingCost v="+vipMod+" p="+vipModp);
                
            float cost = (SOCKET_PRICE_BASE + ((int) socketItem.getProperty("itemGrade")) * SOCKET_PRICE_PER_GRADE);
            long soketingCost = Math.round(Math.ceil(cost + vipMod + cost * vipModp / 100f));
            if (soketingCost < 0)
                soketingCost = 0l;
            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("ext_msg_subtype", "SocketingMsg");
            props.put("PluginMessageType", "SocketingUpdate");
            props.put("creationCost", soketingCost);
            props.put("creationCurrency", SOCKET_PRICE_CURRENCY);
            TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
            Engine.getAgent().sendBroadcast(playerMsg);

            return true;
        }
    }
    
    class InsertItemToSocket implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID playerOid = eMsg.getSubject();
            OID socketItemOid = (OID) eMsg.getProperty("socketItemOid");
            OID itemOid = (OID) eMsg.getProperty("itemOid");
            log.debug("InsertItemToSocket: playerOid: " + playerOid + " socketItemOid: " + socketItemOid + " itemOid:" + itemOid);
            synchronized (playerOid.toString().intern()) {

                InventoryInfo iInfo = getInventoryInfo(playerOid);
                if (iInfo == null) {
                    log.debug("InsertItemToSocket: iInfo is null");
                    return true;
                }
                OID[] subBags = iInfo.getBags();

                AgisItem socketItem = getAgisItem(socketItemOid);

                AgisItem item = getAgisItem(itemOid);
                float vipModp = 0f;
                float vipModChance = 0f;
                long vipMod = 0l;
                long vipModtime = 0l;
                if (iInfo.getBonuses().containsKey("SocketingCost")) {
                    vipMod = iInfo.getBonuses().get("SocketingCost").GetValue();
                    vipModp = iInfo.getBonuses().get("SocketingCost").GetValuePercentage();
                }
                if(globalEventBonusesArray.containsKey("SocketingCost")) {
                    vipMod += globalEventBonusesArray.get("SocketingCost").GetValue();
                    vipModp += globalEventBonusesArray.get("SocketingCost").GetValuePercentage();
                }
                
                if (iInfo.getBonuses().containsKey("SocketingChance")) {
                    vipModChance = iInfo.getBonuses().get("SocketingChance").GetValuePercentage();
                }
                if(globalEventBonusesArray.containsKey("SocketingChance")) {
                    vipModChance += globalEventBonusesArray.get("SocketingChance").GetValuePercentage();
                
                }if (iInfo.getBonuses().containsKey("SocketingTime")) {
                    vipModtime = iInfo.getBonuses().get("SocketingTime").GetValue();
                }
                
                if(globalEventBonusesArray.containsKey("SocketingTime")) {
                    vipModtime += globalEventBonusesArray.get("SocketingTime").GetValue();
                }
                
                log.debug("InsertItemToSocket: SocketingCost v=" + vipMod + " p=" + vipModp + "; SocketingChance p=" + vipModChance + "; SocketingTime v=" + vipModtime);

                float cost = (SOCKET_PRICE_BASE + ((int) socketItem.getProperty("itemGrade")) * SOCKET_PRICE_PER_GRADE);
                long soketingCost = Math.round(Math.ceil(cost + vipMod + cost * vipModp / 100f));
                if (soketingCost < 0)
                    soketingCost = 0l;

                long currencyAmount = getMobCurrency(playerOid, SOCKET_PRICE_CURRENCY);
                Currency c = Agis.CurrencyManager.get(SOCKET_PRICE_CURRENCY);
                if (currencyAmount < (soketingCost)) {
                    ExtendedCombatMessages.sendErrorMessage(playerOid, "InsufficientFunds");
                    Log.debug("InsertItemToSocket: insufficient funds currencyAmount:" + currencyAmount);
                    return false;
                }
            //    alterMobCurrency(playerOid, SOCKET_PRICE_CURRENCY, -(SOCKET_PRICE_BASE+((int)socketItem.getProperty("itemGrade"))+SOCKET_PRICE_PER_GRADE));
                if (item == null || socketItem == null) {
                    log.debug("InsertItemToSocket: itemOid: " + itemOid + " is " + item + " or socketItemOid:" + socketItemOid + " is " + socketItem);
                    return true;
                }
                log.debug("InsertItemToSocket: item stackSize: " + item.getStackSize() + " socketItem stackSize: " + socketItem.getStackSize());

                OID oldBagID = (OID) item.getProperty(INVENTORY_PROP_BACKREF_KEY);
                log.debug("InsertItemToSocket: subBags:" + Arrays.toString(subBags) + " BagID:" + oldBagID);
                if (oldBagID == null) {
                    log.debug("InsertItemToSocket: Got null inventory backref for item: " + itemOid);
                    return true;
                }
                Map<String, Serializable> tt = item.getPropertyMap();
                Set<String> ss = tt.keySet();
                log.debug("InsertItemToSocket: item props:" + ss);
                String socketType = (String) item.getProperty("SocketType");
                if (socketType == null || socketType == "") {
                    // TODO Error
                    log.debug("InsertItemToSocket: error socketType: " + socketType + " ssocketType: " + socketType + " item:" + item.getName());
                    return false;
                }
                HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) socketItem.getProperty("sockets");
                log.debug("InsertItemToSocket: itemSockets:" + itemSockets);
                
                Set<Integer> keys = itemSockets.keySet();
                for (Integer i : keys) {
                    log.debug("InsertItemToSocket: socketType:" + socketType + " itemSockets.get(i).GetType()" + itemSockets.get(i).GetType() + " itemSockets.get(i).GetItemOid():" + itemSockets.get(i).GetItemOid());
                    if (itemSockets.get(i).GetType().equals(socketType) && itemSockets.get(i).GetItemOid() == null) {

                        // get the inventoryplugin
                        AgisInventoryPlugin invPlugin = (AgisInventoryPlugin) Engine.getPlugin(InventoryPlugin.INVENTORY_PLUGIN_NAME);

                        // is this item already equipped
                        AgisInventoryPlugin.EquipMap equipMap = invPlugin.getEquipMap(playerOid);
                        AgisEquipSlot slot;
                        invPlugin.getLock().lock();
                        try {
                            slot = equipMap.getSlot(socketItem.getMasterOid());
                        } finally {
                            invPlugin.getLock().unlock();
                        }
                        if (slot != null) {

                            invPlugin.unequipItem(socketItem, playerOid, false);
                        }
                        // Create Soceting task
                        SocketingTask task = new SocketingTask(playerOid, socketItem, item, SOCKET_CREATE_TIME + vipModtime, SOCKET_CHANCE + SOCKET_CHANCE * vipModChance / 100F, SOCKET_FAILED_CLEAR, SOCKET_PRICE_CURRENCY, -(soketingCost));
                        Engine.getExecutor().schedule(task, SOCKET_CREATE_TIME + vipModtime, TimeUnit.SECONDS);
                        return true;
                    }
                }
            }
            // Remove the item from the normal bag and place it in the equipped bag
            ExtendedCombatMessages.sendErrorMessage(playerOid, "No empty sockets ");
            
            return true;
        }
    }
    
    
    class EnchantingDetaile implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID playerOid = eMsg.getSubject();
            OID itemOid = (OID) eMsg.getProperty("itemOid");
            log.debug("EnchantingDetaile: playerOid: " + playerOid + "  itemOid:" + itemOid);
            AgisItem item = getAgisItem(itemOid);
            log.debug("EnchantingDetaile: playerOid: " + playerOid + " item:" + item);
            String itemType = (String) item.getProperty("itemType");
            if (itemType.equals("Weapon") || itemType.equals("Armor")) {
                Map<String, Serializable> props = new HashMap<String, Serializable>();
                log.debug("EnchantingDetaile: playerOid: " + playerOid + " item:" + item+" kays property:"+item.getPropertyMap().keySet());
                int profileId = (int) item.getProperty("enchantProfileId");
                log.debug("EnchantingDetaile: playerOid: " + playerOid + " item:" + item+" profileId:"+profileId);
                    if (profileId > 0) {
                    EnchantProfile ep = ObjectManagerPlugin.getEnchantProfile(profileId);
                    log.debug("EnchantingDetaile: playerOid: " + playerOid + " item:" + item+" EnchantProfile:"+ep);
                    if (ep==null) {
                        log.debug("EnchantingDetaile: playerOid: " + playerOid + " item:" + item+" EnchantProfile:"+ep+" is null break");
                        return false;
                    }
                        
                    int enchantLevel = (int) item.getProperty("enchantLevel");
                    log.debug("EnchantingDetaile: playerOid: " + playerOid + " item:" + item+" enchantLevel:"+enchantLevel);
                    if (ep.GetLevels().containsKey(enchantLevel + 1)) {
                        log.debug("EnchantingDetaile: playerOid: " + playerOid + " item:" + item+" itemGrade:"+item.getProperty("itemGrade"));
                        QualityInfo qi = ObjectManagerClient.getQualityInfo((int) item.getProperty("itemGrade"));
                        if (qi==null)
                            qi = new QualityInfo();
                        log.debug("EnchantingDetaile: playerOid: " + playerOid + " item:" + item+" QualityInfo:"+qi);
                        
                        float vipModp = 0;
                        long vipMod = 0;
                        InventoryInfo iInfo = getInventoryInfo(playerOid);
                        if(iInfo.getBonuses().containsKey("EnchantingCost")) {
                            vipMod =iInfo.getBonuses().get("EnchantingCost").GetValue();
                            vipModp =iInfo.getBonuses().get("EnchantingCost").GetValuePercentage();
                        }
                        if(globalEventBonusesArray.containsKey("EnchantingCost")) {
                            vipMod += globalEventBonusesArray.get("EnchantingCost").GetValue();
                            vipModp += globalEventBonusesArray.get("EnchantingCost").GetValuePercentage();
                        }log.debug("Enchanting:Detaile EnchantingCost v="+vipMod+" p="+vipModp+"; ");
                        
                        float cost = ep.GetLevels().get(enchantLevel + 1).GetCost() * qi.GetCost();
						long enchantCost = Math.round(Math.ceil(cost + vipMod + cost * vipModp / 100f));
                        if(enchantCost < 0)
                            enchantCost = 0l;
                        
                        
                        props.put("ext_msg_subtype", "EnchantingMsg");
                        props.put("PluginMessageType", "EnchantingUpdate");
                        props.put("creationCost", (enchantCost));
                        props.put("creationCurrency", ep.GetLevels().get(enchantLevel + 1).GetCurrency());
                        props.put("nextLevel", (enchantLevel + 1));
                        //ai.setProperty("enchantStats", enchantStats);
                        HashMap<String, Integer> statsorg = new  HashMap<String, Integer> (); 
                        if(item.getPropertyMapRef().containsKey("enchantStats")) {
                            statsorg = new HashMap<String, Integer>((HashMap<String, Integer>)item.getProperty("enchantStats"));
                        }else {
                            statsorg = new HashMap<String, Integer>((HashMap<String, Integer>)item.getProperty("bonusStats"));
                            int damage = (Integer) item.getProperty("damage");
                            int damageMax = (Integer) item.getProperty("damageMax");
                            if (damageMax < damage) damageMax = damage;
                            statsorg.put("dmg-base", damage);
                            statsorg.put("dmg-max", damageMax);
                        }
                                
                        //HashMap<String, Integer> stats = new HashMap<String, Integer>((HashMap<String, Integer>)item.getProperty("enchantStats"));
                        HashMap<String, Integer> stats = new HashMap<String, Integer>(statsorg);
                        stats.put("Gear Score",0);
                        if (ep.GetLevels().get(enchantLevel + 1).GetAllStats()) {
                                for (String stat : stats.keySet()) {
                                    if (ep.GetLevels().get(enchantLevel + 1).GetPercentage()) {
                                        stats.replace(stat, stats.get(stat) + (int)(stats.get(stat) * ep.GetLevels().get(enchantLevel + 1).GetStatValue()));
                                    } else {
                                        stats.replace(stat, stats.get(stat) + (int)ep.GetLevels().get(enchantLevel + 1).GetStatValue());
                                    }
                                }
                            } else {
                                HashMap<String, EnchantStat> enchLevStats =  ep.GetLevels().get(enchantLevel + 1).GetStats();
                                
                                stats.replace("Gear Score",stats.get("Gear Score")+ep.GetLevels().get(enchantLevel+1).GetGearScoreValue()+(int)(stats.get("Gear Score") * ep.GetLevels().get(enchantLevel+1).GetGearScoreValuePercentage()));
            					
                                for (String stat : enchLevStats.keySet()) {
                                    if (ep.GetLevels().get(enchantLevel + 1).GetAddNotExist() && !stats.containsKey(stat)) {
                                        stats.put(stat, enchLevStats.get(stat).GetValue() + (int)(enchLevStats.get(stat).GetValue() * enchLevStats.get(stat).GetValuePercentage()));
                                    } else if (stats.containsKey(stat)) {
                                        stats.replace(stat, stats.get(stat) + enchLevStats.get(stat).GetValue()
                                                + (int)((stats.get(stat) + enchLevStats.get(stat).GetValue()) * enchLevStats.get(stat).GetValuePercentage()));
                                    }
                                }
                            }
                        int i = 0;
                        for (String stat : stats.keySet()) {
                            if(statsorg.containsKey(stat)) {
                                if((stats.get(stat) -statsorg.get(stat))!=0) {
                                    props.put("stat"+i+"name",stat);
                                    props.put("stat"+i+"value",(stats.get(stat) -statsorg.get(stat)));
                                    i++;
                                }
                            } else {
                                props.put("stat"+i+"name",stat);
                                props.put("stat"+i+"value",stats.get(stat));
                                i++;    
                            }
                        }
                        props.put("statNumber",i);
                        log.debug("EnchantingDetaile: playerOid: " + playerOid + " item:" + item+" props:"+props);
                            TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
                        Engine.getAgent().sendBroadcast(playerMsg);
                    }else {
                        log.debug("EnchantingDetaile:  playerOid: " + playerOid + "  itemOid:" + itemOid + " EnchantProfile profileId:" + profileId+" EnchantIsMaxLevel");
                        ExtendedCombatMessages.sendErrorMessage(playerOid, "EnchantIsMaxLevel");
                        
                    }
                } else {
                    log.debug("EnchantingDetaile:  playerOid: " + playerOid + "  itemOid:" + itemOid + " EnchantProfile error profileId:" + profileId);
                }
            } else {
                log.debug("EnchantingDetaile:  playerOid: " + playerOid + "  itemOid:" + itemOid + " is not Weapon or Armor");
                ExtendedCombatMessages.sendErrorMessage(playerOid, "WrongItem");
            }
            return true;
        }
    }
    
    class Enchanting implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID playerOid = eMsg.getSubject();
            // OID socketItemOid = (OID) eMsg.getProperty("socketItemOid");
            OID itemOid = (OID) eMsg.getProperty("itemOid");
            log.debug("Enchanting: playerOid: " + playerOid + " itemOid:" + itemOid);
            synchronized(playerOid.toString().intern()) {
                
            log.debug("Enchanting: | playerOid: " + playerOid + " itemOid:" + itemOid);
            InventoryInfo iInfo = getInventoryInfo(playerOid);
            if (iInfo == null) {
                log.debug("Enchanting: iInfo is null");
                return true;
            }
            OID[] subBags = iInfo.getBags();
            AgisItem item = getAgisItem(itemOid);
            if (item == null) {
                log.debug("Enchanting: itemOid: " + itemOid + " is " + item);
                return true;
            }
            String itemType = (String) item.getProperty("itemType");
            if (itemType.equals("Weapon") || itemType.equals("Armor")) {

                int profileId = (int) item.getProperty("enchantProfileId");
                if (profileId > 0) {
                    int enchantLevel = (int) item.getProperty("enchantLevel");
                    EnchantProfile ep = ObjectManagerPlugin.getEnchantProfile(profileId);
                    if (ep != null && ep.GetLevels().containsKey(enchantLevel + 1)) {
                        long currencyAmount = getMobCurrency(playerOid, ep.GetLevels().get(enchantLevel + 1).GetCurrency());
                        QualityInfo qi = ObjectManagerClient.getQualityInfo((int) item.getProperty("itemGrade"));
                        if (qi==null)
                            qi = new QualityInfo();
                        
                        
                        float vipModp = 0f;
                        long vipMod = 0l;
                        long vipModtime = 0l;
                        float vipModChance = 0l;
                        if(iInfo.getBonuses().containsKey("EnchantingCost")) {
                            vipMod =iInfo.getBonuses().get("EnchantingCost").GetValue();
                            vipModp =iInfo.getBonuses().get("EnchantingCost").GetValuePercentage();
                        }
                        if(globalEventBonusesArray.containsKey("EnchantingCost")) {
                            vipMod += globalEventBonusesArray.get("EnchantingCost").GetValue();
                            vipModp += globalEventBonusesArray.get("EnchantingCost").GetValuePercentage();
                        }
                        if(iInfo.getBonuses().containsKey("EnchantingChance")) {
                            vipModChance =iInfo.getBonuses().get("EnchantingChance").GetValuePercentage();
                        }
                        if(globalEventBonusesArray.containsKey("EnchantingChance")) {
                            vipModChance += globalEventBonusesArray.get("EnchantingChance").GetValuePercentage();
                        }
                        if(iInfo.getBonuses().containsKey("EnchantingTime")) {
                            vipModtime =iInfo.getBonuses().get("EnchantingTime").GetValue();
                        }
                        if(globalEventBonusesArray.containsKey("EnchantingTime")) {
                            vipModtime += globalEventBonusesArray.get("EnchantingTime").GetValue();
                        }
                        log.debug("Enchanting: EnchantingCost v="+vipMod+" p="+vipModp+"; EnchantingChance p="+vipModChance+"; EnchantingTime v="+vipModtime);
                        float cost = ep.GetLevels().get(enchantLevel + 1).GetCost() * qi.GetCost();
						long enchantCost = Math.round(Math.ceil(cost + vipMod + cost * vipModp / 100f));
                        if(enchantCost < 0)
                            enchantCost = 0l;
                        if (currencyAmount < enchantCost) {
                            ExtendedCombatMessages.sendErrorMessage(playerOid, "InsufficientFunds");
                            Log.debug("Enchanting: insufficient funds currencyAmount:" + currencyAmount);
                            return false;
                        }
                        OID oldBagID = (OID) item.getProperty(INVENTORY_PROP_BACKREF_KEY);
                        log.debug("Enchanting: subBags:" + Arrays.toString(subBags) + " BagID:" + oldBagID);
                        if (oldBagID == null) {
                            log.debug("Enchanting: Got null inventory backref for item: " + itemOid);
                            return true;
                        }
                        // get the inventoryplugin
                        AgisInventoryPlugin invPlugin = (AgisInventoryPlugin)Engine.getPlugin(InventoryPlugin.INVENTORY_PLUGIN_NAME);
                        
                        // is this item already equipped
                        AgisInventoryPlugin.EquipMap equipMap = invPlugin.getEquipMap(playerOid);
                        AgisEquipSlot slot;
                        invPlugin.getLock().lock();
                        try {
                            slot = equipMap.getSlot(item.getMasterOid());
                        }
                        finally {
                            invPlugin.getLock().unlock();
                        }
                        if (slot != null) {
                        
                            invPlugin.unequipItem(item, playerOid, false);
                        }
							EnchantingTask task = new EnchantingTask(playerOid, item, ep, ENCHANTING_TIME + vipModtime, ep.GetLevels().get(enchantLevel + 1).GetCurrency(), -((int) (enchantCost)), qi, vipModChance);
							Engine.getExecutor().schedule(task, ENCHANTING_TIME + vipModtime, TimeUnit.SECONDS);
                    } else {
                        ExtendedCombatMessages.sendErrorMessage(playerOid, "EnchantIsMaxLevel");
                        
                    }
                }
            }
            }
            // Remove the item from the normal bag and place it in the equipped bag
            // ExtendedCombatMessages.sendErrorMessage(playerOid, "No empty sockets ");

            return true;
        }
    }
    
    /*
     * Item Store Hooks
     */
    
    /**
     * 
     * @author Andrew
     *
     */
    class GetStoreItemsHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID playerOid = eMsg.getSubject();
            if (Log.loggingDebug)
                log.debug("GetStoreItemsHook: playerOid: " + playerOid);

            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("ext_msg_subtype", "StoreItems");

            for (int i = 0; i < storeItems.size(); i++) {
                props.put("item" + i, storeItems.get(i));
            }

            props.put("numItems", storeItems.size());
            TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
            Engine.getAgent().sendBroadcast(TEmsg);
            InventoryInfo iInfo = getInventoryInfo(playerOid);
            ExtendedCombatMessages.sendCurrencies(playerOid, iInfo.getCurrentCurrencies());

            return true;
        }
    }
    
    class PurchaseStoreItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID playerOid = eMsg.getSubject();
            int itemID = (Integer) eMsg.getProperty("itemID");
            int count = (Integer) eMsg.getProperty("count");
            int additionalItemCount = (Integer) eMsg.getProperty("additionalItemCount");

            Log.debug("PURCHASE: attempting to purchase " + count + " of item " + itemID);
            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("ext_msg_subtype", "store_item_purchase_result");
            Template itemTemplate = ObjectManagerPlugin.getTemplate(itemID, ObjectManagerPlugin.ITEM_TEMPLATE);
            props.put("itemName", itemTemplate.getName());
            TargetedExtensionMessage resultMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
            // Make sure the player can afford the item
            Template tmpl = ObjectManagerPlugin.getTemplate(itemID, ObjectManagerPlugin.ITEM_TEMPLATE);
            int purchaseCurrency = (Integer) tmpl.get(InventoryClient.ITEM_NAMESPACE, "purchaseCurrency");
            long cost = (Long) tmpl.get(InventoryClient.ITEM_NAMESPACE, "purchaseCost");

            float bonusModp = 0;
            long bonusMod = 0;
            InventoryInfo iInfo = getInventoryInfo(playerOid);

            if (iInfo.getBonuses().containsKey("PriceMerchant")) {
                bonusMod = iInfo.getBonuses().get("PriceMerchant").GetValue();
                bonusModp = iInfo.getBonuses().get("PriceMerchant").GetValuePercentage();
            }
            if(globalEventBonusesArray.containsKey("PriceMerchant")) {
                bonusMod += globalEventBonusesArray.get("PriceMerchant").GetValue();
                bonusModp += globalEventBonusesArray.get("PriceMerchant").GetValuePercentage();
            }
            long PriceValue = Math.round(Math.ceil(bonusMod + cost + (cost * bonusModp) / 100f));
            if (PriceValue < 0)
                PriceValue = 0l;
            PriceValue = count * PriceValue;
            if (Log.loggingDebug)
                log.debug("PurchaseStoreItemHook PURCHASE: cost=" + cost + " count=" + count + " bonusMod=" + bonusMod + " bonusModp=" + bonusModp + " PriceValue=" + PriceValue);

            long currencyAmount = getMobCurrency(playerOid, purchaseCurrency);
            Currency c = Agis.CurrencyManager.get(purchaseCurrency);
            if (currencyAmount < (PriceValue)) {
                props.put("result", "insufficient_funds");
                props.put("currency", c.getCurrencyName());
                Engine.getAgent().sendBroadcast(resultMsg);
                if (Log.loggingDebug)
                    Log.debug("PURCHASE: not enough funds: " + currencyAmount + " for cost: " + (cost * count));
                return true;
            }

            long totalCost = -PriceValue;
            Log.debug("PURCHASE: has enough money");

            // Check for duplicate items here
            ArrayList<OID> items = new ArrayList<OID>();
            for (int i = 1; i <= additionalItemCount; i++) {
                int additionalItemID = (Integer) eMsg.getProperty("itemID" + i);
                int additionalCount = (Integer) eMsg.getProperty("itemCount" + i);
                if (additionalItemID == itemID) {
                    count *= (additionalCount + 1);
                    continue;
                }
            }
            // Send mail with the item
            String recipientName = aDB.getCharacterNameByOid(playerOid);
            //String recipientName = WorldManagerClient.getObjectInfo(playerOid).name;
            String subject = "Item Store Purchase";
            String message = "Thank you for using the Item Store. Your purchased item is attached.";

            // Generate Items
            String itemName = tmpl.getName();
            OID itemOid = generateItem(itemID, itemName);
            if (!itemOid.equals(0l)) {
                AgisItem item = getAgisItem(itemOid);
                item.setStackSize(count);
                ObjectManagerClient.saveObject(itemOid);
                items.add(itemOid);
            }

            for (int i = 1; i < additionalItemCount; i++) {
                int additionalItemID = (Integer) eMsg.getProperty("itemID" + i);
                int additionalCount = (Integer) eMsg.getProperty("itemCount" + i);
                if (additionalItemID == itemID) {
                    continue;
                }
                tmpl = ObjectManagerPlugin.getTemplate(additionalItemID, ObjectManagerPlugin.ITEM_TEMPLATE);
                itemName = tmpl.getName();
                OID additionaltemOid = generateItem(additionalItemID, itemName);
                if (!additionaltemOid.equals(0l)) {
                    AgisItem item = getAgisItem(additionaltemOid);
                    item.setStackSize(additionalCount);
                    ObjectManagerClient.saveObject(additionaltemOid);
                    items.add(additionaltemOid);
                }
            }

            Log.debug("MAIL: handled items");
            
            //OID mailOID = Engine.getOIDManager().getNextOid();
            String result = createAndSendCharacterMail(null, playerOid, recipientName, subject, message, items, -1, 0, false);
            
            // Check if the player has space
            /*if (!hasSpace(playerOid, itemID, count, 0)) {
                props.put("result", "insufficient_space");
                Engine.getAgent().sendBroadcast(resultMsg);
                ExtendedCombatMessages.sendErrorMessage(playerOid, "You do not have enough space in your inventory to purchase that item.");
                // Send boolean response back saying it was a success
                Log.debug("PURCHASE: not enough space: ");
                return true;
            }
            
            boolean itemAdded = addItemFromTemplate(playerOid, itemID, count, null);*/
            //boolean itemAdded = InventoryClient.addItem(oid, oid, oid, itemOid);
            //if (itemAdded) {
            //  long delta = (totalCost);
                alterMobCurrency(playerOid, purchaseCurrency, totalCost);
            alterCurrecyLog(playerOid,null, purchaseCurrency, totalCost,"PurchaseStoreItemHook");
                //sendBagInvUpdate(playerOid);
            //}
            Log.debug("PurchaseStoreItemHook: finished generation and adding of item: " + itemID);
            props.put("result", "success");
            Engine.getAgent().sendBroadcast(resultMsg);
            
            
            return true;
        }
    }

    class GetPlayerShopItemsHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID playerOid = eMsg.getSubject();
            if (Log.loggingDebug)
                log.debug("GetPlayerShopItemsHook: playerOid: " + playerOid);
            OID storeOid = (OID) eMsg.getProperty("shop");
            sendPlayerShop(playerOid, storeOid);
            return true;
        }
    }
    
    
    void sendPlayerShop(OID playerOid, OID storeOid) {
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "PlyShopItems");
        props.put("shop", storeOid.toLong());

        ArrayList<PlayerShopItem> listItems = aDB.getPlayerStore(storeOid);
        int numItems = 0;
        for (PlayerShopItem psi : listItems) {
            if (psi.getTemplateId() > 0) {
                props.put("item_" + numItems + "price", psi.getPrice());
                props.put("item_" + numItems + "Currency", psi.getCurrency());
                props.put("item_" + numItems + "TemplateID", psi.getTemplateId());
                props.put("item_" + numItems + "Count", psi.getCount());
                props.put("item_" + numItems + "sell", psi.getSell());
                numItems++;
            } else {
                AgisItem item = null;
                if (psi.getItemOid() != null) {
                    item = getAgisItem(psi.getItemOid());
                }
                if (item == null && psi.getItemOid() != null) {
                    if (ObjectManagerClient.loadObject(psi.getItemOid()) != null) {
                        item = getAgisItem(psi.getItemOid());
                    }
                }
                if (item != null) {
                    if (Log.loggingDebug)
                        log.debug("GetPlayerShopItemsHook: adding itemOid=" + psi.getItemOid() + ", itemName=" + item.getName() + ",icon=" + item.getIcon());
                    props.put("item_" + numItems + "Oid", psi.getItemOid());
                    props.put("item_" + numItems + "sell", psi.getSell());
                    props.put("item_" + numItems + "price", psi.getPrice());
                    props.put("item_" + numItems + "Currency", psi.getCurrency());
                    props.put("item_" + numItems + "TemplateID", item.getTemplateID());
                    props.put("item_" + numItems + "Name", item.getName());
                    props.put("item_" + numItems + "BaseName", item.getProperty("baseName"));
                    props.put("item_" + numItems + "Id", item.getOid());
                    props.put("item_" + numItems + "Count", item.getStackSize());
                    props.put("item_" + numItems + "Bound", item.isPlayerBound());
                    if (item.getProperty("energyCost") != null) {
                        props.put("item_" + numItems + "EnergyCost", item.getProperty("energyCost"));
                    } else {
                        props.put("item_" + numItems + "EnergyCost", 0);
                    }
                    if (item.getProperty("maxDurability") != null) {
                        props.put("item_" + numItems + "Durability", item.getProperty("durability"));
                        props.put("item_" + numItems + "MaxDurability", item.getProperty("maxDurability"));
                    } else {
                        props.put("item_" + numItems + "MaxDurability", 0);
                    }
                    if (item.getProperty("resistanceStats") != null) {
                        int numResist = 0;
                        HashMap<String, Integer> resistances = (HashMap) item.getProperty("resistanceStats");
                        for (String resistance : resistances.keySet()) {
                            props.put("item_" + numItems + "Resist_" + numResist + "Name", resistance);
                            props.put("item_" + numItems + "Resist_" + numResist + "Value", resistances.get(resistance));
                            numResist++;
                        }
                        props.put("item_" + numItems + "NumResistances", numResist);
                    } else {
                        props.put("item_" + numItems + "NumResistances", 0);
                    }
                    if (item.getProperty("bonusStats") != null) {
                        int numStats = 0;
                        HashMap<String, Integer> stats = (HashMap) item.getProperty("bonusStats");
                        for (String statName : stats.keySet()) {
                            props.put("item_" + numItems + "Stat_" + numStats + "Name", statName);
                            props.put("item_" + numItems + "Stat_" + numStats + "Value", stats.get(statName));
                            numStats++;
                        }
                        props.put("item_" + numItems + "NumStats", numStats);
                    } else {
                        props.put("item_" + numItems + "NumStats", 0);
                    }
                    int enchantLevel = (int) item.getProperty("enchantLevel");
                    props.put("item_" + numItems + "ELevel", enchantLevel);

                    if (item.getProperty("enchantStats") != null) {
                        int numStats = 0;
                        HashMap<String, Integer> stats = (HashMap) item.getProperty("enchantStats");
                        for (String statName : stats.keySet()) {
                            props.put("item_" + numItems + "EStat_" + numStats + "Name", statName);
                            props.put("item_" + numItems + "EStat_" + numStats + "Value", stats.get(statName));
                            numStats++;
                        }
                        props.put("item_" + numItems + "NumEStats", numStats);
                    } else {
                        props.put("item_" + numItems + "NumEStats", 0);
                    }
                    if (item.getProperty("sockets") != null) {
                        int numSocket = 0;
                        HashMap<Integer, SocketInfo> sockets = (HashMap<Integer, SocketInfo>) item.getProperty("sockets");
                        for (Integer socket: sockets.keySet()) {
                            if (sockets.get(socket).GetItemOid()!=null) {
                                 AgisItem itemSoc = getAgisItem(sockets.get(socket).GetItemOid());
                                 if (itemSoc != null) {
                                     props.put("item_" + numItems + "socket_" + socket + "Item", itemSoc.getTemplateID());
                                        props.put("item_" + numItems + "socket_" + socket + "ItemOid", itemSoc.getOid().toLong());
                                         }else {
                                     props.put("item_" + numItems + "socket_" + socket + "Item", -1); 
                                        props.put("item_" + numItems + "socket_" + socket + "ItemOid", 0L);
                                          }
                            }else {
                                props.put("item_" + numItems + "socket_" + socket + "Item", -1);
                            props.put("item_" + numItems + "socket_" + socket + "ItemOid", 0L);
                            }
                            props.put("item_" + numItems + "socket_" + socket + "Type", sockets.get(socket).GetType());
                            props.put("item_" + numItems + "socket_" + socket + "Id", socket);
                                numSocket++;
                        }
                        props.put("item_" + numItems + "NumSocket", numSocket);
                    } else {
                        props.put("item_" + numItems + "NumSocket", 0);
                    }

                    // If it is a weapon, add damage/speed stats
                    if (item.getItemType().equals("Weapon")) {
                        props.put("item_" + numItems + "Delay", item.getProperty("delay"));
                        props.put("item_" + numItems + "DamageType", item.getProperty("damageType"));
                        props.put("item_" + numItems + "DamageValue", item.getProperty("damage"));
                        props.put("item_" + numItems + "DamageValueMax", item.getProperty("damageMax"));
                    }
                    props.put("item_" + numItems + "ActionBarAllowed", item.getProperty("actionBarAllowed"));
                    
                    //Added for Enchant Effects and Abilities
                    if (enchantLevel > 0) 
                    {
                    	if (item.getProperty("enchantProfileId") != null) 
                    	{
                    		EnchantProfile ep = ObjectManagerPlugin.getEnchantProfile((int) item.getProperty("enchantProfileId"));
                			if (ep != null && ep.GetLevels().containsKey(enchantLevel)) 
                			{	
                				int numEffects = 0;
                				int numAbilities = 0;
                				for (int e = 1; e <= enchantLevel; e++) 
                				{
                					EnchantProfileLevel enchantProfile = ep.GetLevels().get(e);
        							for (Integer ability : enchantProfile.GetAbilities()) 
        							{
        								props.put("item_" + numItems + "EAbility_" + numAbilities + "Value", ability);
        								numAbilities++;
        							}   
        							for (Integer effect : enchantProfile.GetEffects()) 
        							{
        								props.put("item_" + numItems + "EEffect_" + numEffects + "Value", effect);
        								numEffects++;
        							}
                				}
                				props.put("item_" + numItems + "NumEAbilities", numAbilities);	
                				props.put("item_" + numItems + "NumEEffects", numEffects);
                			}                			
                    	}
            		} 
                    numItems++;
                } else {
                    log.debug("sendPlayerShop: Item is null");
                }
            }
        }
        props.put("numItems", numItems);

        TargetedExtensionMessage TEmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
        Engine.getAgent().sendBroadcast(TEmsg);
    }
    
    class PurchasePlayerShopItemHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID playerOid = eMsg.getSubject();
            if (Log.loggingDebug)
                Log.debug("PurchasePlayerShopItemHook: " + eMsg.getPropertyMapRef());
            int itemId = (int) eMsg.getProperty("ItemId");
            OID itemOid = (OID) eMsg.getProperty("ItemOid");
            int count = (Integer) eMsg.getProperty("ItemCount");
            OID storeOid = (OID) eMsg.getProperty("shop");
            PlayerShop ps = aDB.getPlayerShop(storeOid);
            if(playerOid == null) {
                Log.debug("PurchasePlayerShopItemHook: ply is null");
                return true;
            }
            if(playerOid.equals(ps.getOwnerOid())) {
                Log.debug("PurchasePlayerShopItemHook: Owner cant buy or sell");
                EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.OWNER_CANT_BUY_SELL, 0, "");
                return true;
            }
            
            ArrayList<PlayerShopItem> listItems = aDB.getPlayerStore(storeOid);
            Map<String, Serializable> props = new HashMap<String, Serializable>();
            for (PlayerShopItem psi : listItems) {
                if (Log.loggingDebug)
                    Log.debug("PurchasePlayerShopItemHook: psi=" + psi.getTemplateId() + " " + psi.getItemOid());
                if (itemId > 0 && psi.getTemplateId() == itemId) {
                    Log.debug("PurchasePlayerShopItemHook: item template =" + itemId);
                    HashMap<Integer, Integer> ItemsCount = new HashMap<Integer, Integer>();

                    InventoryInfo iInfo = getInventoryInfo(playerOid);
                    if (iInfo == null) {
                        log.debug("PurchasePlayerShopItemHook:  InventoryInfo is null");
                        return true;
                    }
                    OID[] subBags = iInfo.getBags();
                    for (OID bag : subBags) {
                        Bag subBag = getBag(bag);
                        OID[] itemsInBag = subBag.getItemsList();
                        if (Log.loggingDebug)
                            log.debug("PurchasePlayerShopItemHook:  subBagOid:" + bag + " subBags:" + Arrays.toString(subBags) + " itemsInBag:" + Arrays.toString(itemsInBag));
                        for (OID ite : itemsInBag) {
                            AgisItem Item = getAgisItem(ite);
                            if (Log.loggingDebug)
                                log.debug("PurchasePlayerShopItemHook: ite:" + ite + " Item :" + Item);
                            if (Item != null) {
                                String unicItem = Item.getTemplateID() + "";
                                if (ItemsCount.containsKey(Item.getTemplateID())) {
                                    ItemsCount.replace(Item.getTemplateID(), ItemsCount.get(Item.getTemplateID()) + Item.getStackSize());
                                } else {
                                    ItemsCount.put(Item.getTemplateID(), Item.getStackSize());
                                }
                            } else {
                                log.debug("PurchasePlayerShopItemHook: Item is null");
                            }
                        }
                    }
                    if (Log.loggingDebug)
                        log.debug("PurchasePlayerShopItemHook: Bag ItemsCount:" + ItemsCount);

                    if (ItemsCount.containsKey(itemId) && ItemsCount.get(itemId) >= count && psi.getCount() >= count) {
                        removeGenericItem(playerOid, itemId, count, false);
                        Template tmpl = ObjectManagerPlugin.getTemplate(itemId, ObjectManagerPlugin.ITEM_TEMPLATE);
                        String itemName = tmpl.getName();
                        OID newItemOid = generateItem(itemId, tmpl.getName());
                        if (Log.loggingDebug)
                            log.debug("PurchasePlayerShopItemHook: generated item newItemOid:" + newItemOid);

                        AgisItem item = getAgisItem(newItemOid);
                        item.setStackSize(count);
                        Engine.getPersistenceManager().setDirty(item);
                        ArrayList<OID> itemList = new ArrayList<OID>();
                        itemList.add(newItemOid);
                        if (Log.loggingDebug)
                            log.debug("PurchasePlayerShopItemHook: send mail itemList:" + itemList);
                        createAndSendCharacterMail(OID.fromLong(0L), ps.getOwnerOid(), "Buyer", "Buy on Store", "", itemList, 0, 0, false);
                        if (Log.loggingDebug)
                            log.debug("PurchasePlayerShopItemHook: item count psi " + psi.getCount() + " sell " + count);
                        
                        long cost = psi.getPrice();
                        int purchaseCurrency = psi.getCurrency();
                        
                        if (Log.loggingDebug)
                            log.debug("PurchasePlayerShopItemHook: add "+(cost*count)+" of currency "+purchaseCurrency+" to player "+playerOid );
                        alterMobCurrency(playerOid, purchaseCurrency, cost*count);
                        alterCurrecyLog(playerOid,null, purchaseCurrency, cost*count,"PurchasePlayerShopItemHook sell");
                        
                        if (psi.getCount() != count) {
                            psi.setCount(psi.getCount() - count);
                            if (Log.loggingDebug)
                                log.debug("PurchasePlayerShopItemHook: psi.getCount()=" + psi.getCount());
                            aDB.SavePlayerShopItem(storeOid, psi.getId(), psi.getCount());
                        } else {
                            log.debug("PurchasePlayerShopItemHook: delete item buyed all");
                            aDB.deletePlayerShopItem(storeOid, psi.getId());
                        }
                        Log.debug("PurchasePlayerShopItemHook: send update inventory and shop");
                        sendPlayerShop(playerOid, storeOid);
                        sendBagInvUpdate(playerOid);
                    } else {
                        Log.debug("PurchasePlayerShopItemHook: item not found in shop");
                    }
                    
                }else if(itemOid != null && itemOid.equals(psi.getItemOid()) ){
                    if(Log.loggingDebug)Log.debug("PurchasePlayerShopItemHook: item ="+itemOid);
                    AgisItem item = getAgisItem(itemOid);
                    long cost = psi.getPrice();
                    int purchaseCurrency = psi.getCurrency();
                    boolean hasSpace = hasSpace(playerOid, item.getTemplateID(), 1, 0);
                    if (!hasSpace) {
                        log.debug("PurchasePlayerShopItemHook: No Space in Inventory");
                        props.put("result", "insufficient_space");
                        return true;
                        }
                    Log.debug("PurchasePlayerShopItemHook: obj has space");
                    
                    long currencyAmount = getMobCurrency(playerOid, purchaseCurrency);
                    if(Log.loggingDebug)    Log.debug("PurchasePlayerShopItemHook: obj currencyAmount "+currencyAmount);
                    Currency c = Agis.CurrencyManager.get(purchaseCurrency);
                    if (currencyAmount < cost) {
                        props.put("result", "insufficient_funds");
                        props.put("currency", c.getCurrencyName());
                        //Engine.getAgent().sendBroadcast(resultMsg);
                        EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.NOT_ENOUGH_CURRENCY, 0, "");
                        Log.debug("PURCHASE: not enough funds: " + currencyAmount + " for cost: " + cost);
                        return true;
                    }
                    Log.debug("PurchasePlayerShopItemHook: has currency ");
                    boolean itemAdded = addItem(playerOid, playerOid, item.getOid());
                    if(Log.loggingDebug)    Log.debug("PurchasePlayerShopItemHook: added "+itemAdded);
                    if (itemAdded) {
                        aDB.deletePlayerShopItem(storeOid, psi.getId());
                        alterMobCurrency(playerOid, purchaseCurrency, -cost);
                        alterCurrecyLog(playerOid,null, purchaseCurrency, -cost,"PurchasePlayerShopItemHook buy");

                        Log.debug("PurchasePlayerShopItemHook: Currency altered ");
                        createAndSendCharacterMail(OID.fromLong(0L), ps.getOwnerOid(), "Seller", "Sell on Store", "", new ArrayList<OID>(), purchaseCurrency, cost, false);
                    }else {
                        Log.debug("PurchasePlayerShopItemHook: item not added");
                    }
                    Log.debug("PurchasePlayerShopItemHook: send update inventory and shop");
                    sendPlayerShop(playerOid, storeOid);
                    sendBagInvUpdate(playerOid);
                }else {
                    Log.debug("PurchasePlayerShopItemHook: Else !!!!!!");
                }
            }
            
            listItems = aDB.getPlayerStore(storeOid);
            Log.debug("PurchasePlayerShopItemHook: listItems="+listItems.size()+" ply?"+ps.getPlayer());
            if(listItems.size()==0) {
                if (ps.getPlayer()) {
                    EnginePlugin.setObjectPropertyNoResponse(ps.getOwnerOid(), WorldManagerClient.NAMESPACE, "shopTitle", "");
                    EnginePlugin.setObjectPropertyNoResponse(ps.getOwnerOid(), WorldManagerClient.NAMESPACE, "playerShop", false);
                    EnginePlugin.setObjectPropertyNoResponse(ps.getOwnerOid(), WorldManagerClient.NAMESPACE, "plyShopId", OID.fromLong(0L));
                    EnginePlugin.setObjectPropertyNoResponse(ps.getOwnerOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, false);
                    EnginePlugin.setObjectPropertyNoResponse(ps.getOwnerOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
                } else
                    AgisMobClient.DespawnPlayerShop(storeOid);
                aDB.deletePlayerStore(storeOid);
                sendShopListUpdate(ps.getOwnerOid());
            }
            Log.debug("PurchasePlayerShopItemHook: End");
    
            return true;
        }
    }
    
    
    
    class OpenPlayerShopHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID playerOid = eMsg.getSubject();
            if(Log.loggingDebug)    Log.debug("OpenPlayerShopHook: playerOid="+playerOid);
            return true;
        }
    }

    class StopShopHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID playerOid = eMsg.getSubject();
            OID storeOid = (OID) eMsg.getProperty("shop");
            if (Log.loggingDebug)
                Log.debug("StopShopHook: storeOid=" + storeOid + " playerOid=" + playerOid);
            if (aDB.isPlayerStore(storeOid, playerOid)) {
                PlayerShop ps = aDB.getPlayerShop(storeOid);
                ArrayList<PlayerShopItem> listItems = aDB.getPlayerStore(storeOid);
                aDB.deletePlayerStore(storeOid);
                if (Log.loggingDebug)
                    Log.debug("StopShopHook: storeOid=" + storeOid + " items=" + listItems.size());
                ArrayList<OID> itemList = new ArrayList<OID>();
                int curr = 0;
                long currAmount = 0;
                for (PlayerShopItem psi : listItems) {
                    if (Log.loggingDebug)
                        log.debug("StopShopHook: " + psi.getTemplateId() + " " + psi.getItemOid());
                    if (psi.getTemplateId() > 0) {
                        currAmount += psi.getPrice() * psi.getCount();
                        curr = psi.getCurrency();
                    } else {
                        itemList.add(psi.getItemOid());
                    }
                }
                if (Log.loggingDebug)
                    Log.debug("StopShopHook: send Mail items:" + itemList + " c:" + curr + " a:" + currAmount + " size=" + listItems.size());
                if (itemList.size() > AgisInventoryPlugin.MAIL_ATTACHMENT_COUNT) {
                    ArrayList<OID> itemList2 = new ArrayList<OID>();
                    int i = 0;
                    for (OID itemOid : itemList) {
                        AgisItem ai = getAgisItem(itemOid);
                          
                    }
                    while (itemList.size() > 0) {
                        itemList2.add(itemList.remove(0));
                        i++;
                        if (i == AgisInventoryPlugin.MAIL_ATTACHMENT_COUNT) {
                            createAndSendCharacterMail(OID.fromLong(0L), ps.getOwnerOid(), "Seller", "Sell on Store", "Shop has been terminated", itemList2, curr, currAmount, false);
                            currAmount = 0;
                            itemList2= new ArrayList<OID>();
                            i = 0;
                        }
                    }
                    if (itemList2.size() != 0 || currAmount != 0)
                        createAndSendCharacterMail(OID.fromLong(0L), ps.getOwnerOid(), "Seller", "Sell on Store", "Shop has been terminated", itemList2, curr, currAmount, false);

                } else {
                    createAndSendCharacterMail(OID.fromLong(0L), ps.getOwnerOid(), "Seller", "Sell on Store", "Shop has been terminated", itemList, curr, currAmount, false);
                }
                if (Log.loggingDebug)
                    Log.debug("StopShopHook: Despawn");
                if (ps.getPlayer()) {
                    EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "shopTitle", "");
                    EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "playerShop", false);
                    EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "plyShopId", OID.fromLong(0L));
                    EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, false);
                    EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
                } else {
                    AgisMobClient.DespawnPlayerShop(storeOid);
                    AgisMobClient.DeleteShopSpawn(storeOid);
                }
            }
            sendShopListUpdate(playerOid);
            Log.debug("StopShopHook: END");
            if (Log.loggingDebug)
                Log.debug("StopShopHook: playerOid=" + playerOid);
            return true;
        }
    }
    
    class CancelPlayerShopHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID playerOid = eMsg.getSubject();
            if (Log.loggingDebug)
                Log.debug("CancelPlayerShopHook: playerOid=" + playerOid);

            InventoryInfo iInfo = getInventoryInfo(playerOid);
            // Reset params needed to create Shop
            if (iInfo != null) {
                iInfo.setShopMobTemplate(-1);
                iInfo.setShopSlots(-1);
                iInfo.setNumShops(-1);
                iInfo.setShopTag("");
                iInfo.setShopTimeOut(0);
            }
            if (Log.loggingDebug)
                Log.debug("CancelPlayerShopHook: END playerOid=" + playerOid);

            return true;
        }
    }

    class DeletePlayerShopHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            DeletePlayerShopMessage eMsg = (DeletePlayerShopMessage) msg;
            OID storeOid = eMsg.getShopOid();

            if (Log.loggingDebug)
                Log.debug("DeletePlayerShopHook: storeOid=" + storeOid);
            if (aDB.isPlayerStore(storeOid)) {
                PlayerShop ps = aDB.getPlayerShop(storeOid);
                OID playerOid = ps.getOwnerOid();
                ArrayList<PlayerShopItem> listItems = aDB.getPlayerStore(storeOid);
                aDB.deletePlayerStore(storeOid);
                if (Log.loggingDebug)
                    Log.debug("DeletePlayerShopHook: storeOid=" + storeOid + " items=" + listItems.size());
                ArrayList<OID> itemList = new ArrayList<OID>();
                int curr = 0;
                long currAmount = 0;
                for (PlayerShopItem psi : listItems) {
                    if (Log.loggingDebug)
                        log.debug("DeletePlayerShopHook: " + psi.getTemplateId() + " " + psi.getItemOid());
                    if (psi.getTemplateId() > 0) {
                        currAmount += psi.getPrice() * psi.getCount();
                        curr = psi.getCurrency();
                    } else {
                        itemList.add(psi.getItemOid());
                    }
                }
                if (Log.loggingDebug)
                    Log.debug("DeletePlayerShopHook: send Mail items:" + itemList + " c:" + curr + " a:" + currAmount + " size=" + listItems.size());
                if (itemList.size() > AgisInventoryPlugin.MAIL_ATTACHMENT_COUNT) {
                    ArrayList<OID> itemList2 = new ArrayList<OID>();
                    int i = 0;
                    while (itemList.size() > 0) {
                        itemList2.add(itemList.remove(0));
                        i++;
                        if (i == AgisInventoryPlugin.MAIL_ATTACHMENT_COUNT) {
                            createAndSendCharacterMail(OID.fromLong(0L), ps.getOwnerOid(), "Seller", "Sell on Store", "Shop has been terminated", itemList2, curr, currAmount, false);
                            currAmount = 0;
                            itemList2= new ArrayList<OID>();
                            i = 0;
                        }
                    }
                    if (itemList2.size() != 0 || currAmount != 0)
                        createAndSendCharacterMail(OID.fromLong(0L), ps.getOwnerOid(), "Seller", "Sell on Store", "Shop has been terminated", itemList2, curr, currAmount, false);

                } else {
                    createAndSendCharacterMail(OID.fromLong(0L), ps.getOwnerOid(), "Seller", "Sell on Store", "Shop has been terminated", itemList, curr, currAmount, false);
                }
                Log.debug("DeletePlayerShopHook: Despawn");
                if (ps.getPlayer()) {
                    EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "shopTitle", "");
                    EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "playerShop", false);
                    EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "plyShopId", OID.fromLong(0L));
                    EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, false);
                    EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
                }
                //aDB.deleteShopSpwanData(storeOid);
                
                AgisMobClient.DespawnPlayerShop(storeOid);
                AgisMobClient.DeleteShopSpawn(storeOid);
                sendShopListUpdate(playerOid);
            }

            Log.debug("DeletePlayerShopHook: END");
            return true;
        }
    }

    class StartShopHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID playerOid = eMsg.getSubject();
            int sellnum = (int) eMsg.getProperty("sellNum");
            String message = (String) eMsg.getProperty("message");
            HashMap<OID, PlayerShopItem> sellList = new HashMap<OID, PlayerShopItem>();
            ArrayList<PlayerShopItem> items = new ArrayList<PlayerShopItem>();
            if (Log.loggingDebug)
                Log.debug("StartShopHook: message=" + message + " sellnum=" + sellnum);
            boolean mobShop = false;
            ArrayList<OID> removedItems = new ArrayList<OID>(); 
            for (int i = 0; i < sellnum; i++) {
                OID itOid = (OID) eMsg.getProperty("sitemOid" + i);
                int itcurr = (int) eMsg.getProperty("sitemCurr" + i);
                long itcost = (long) eMsg.getProperty("sitemCost" + i);

                if (Log.loggingDebug)
                    log.debug("StartShopHook: item oid=" + itOid + " itcurr=" + itcurr + " " + itcost + "");
                try {
                    AgisItem item = getAgisItem(itOid);
                    if (item == null) {
                        for(OID it : removedItems) {
                            boolean itemAdded = addItem(playerOid, playerOid, it);
                            log.debug("StartShopHook: ReaddItem "+it);
                        }
                        return true;
                    }
                    OID oldBagID = (OID) item.getProperty(INVENTORY_PROP_BACKREF_KEY);
                    if (Log.loggingDebug)
                        log.debug("StartShopHook: item:" + item + " oldBagID:" + oldBagID);
                    if (oldBagID == null) {
                        log.debug("StartShopHook: item is not assign to bag cannot sell item");
                        EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.CANNOT_SELL_ITEM, item.getTemplateID(), "");
                        for(OID it : removedItems) {
                            boolean itemAdded = addItem(playerOid, playerOid, it);
                            log.debug("StartShopHook: ReaddItem "+it);
                        }
                        return true;
                    }
                    unequipItem(item, playerOid, false);
                    OID rootBagOid = playerOid;
                    if (rootBagOid == null) {
                        log.debug("StartShopHook: cant find rootBagOid");
                        for(OID it : removedItems) {
                            boolean itemAdded = addItem(playerOid, playerOid, it);
                            log.debug("StartShopHook: ReaddItem "+it);
                        }
                        return true;
                    }
                    Log.debug("StartShopHook: got root bag");
                    if (item.isPlayerBound()) {
                        EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.CANNOT_SELL_ITEM, item.getTemplateID(), "");
                        for(OID it : removedItems) {
                            boolean itemAdded = addItem(playerOid, playerOid, it);
                            log.debug("StartShopHook: ReaddItem "+it);
                        }
                        return true;
                    }
                    // Check if item is sellable
                    boolean sellable = item.getBooleanProperty("sellable");
                    if (!sellable) {
                        EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.CANNOT_SELL_ITEM, item.getTemplateID(), "");
                        for(OID it : removedItems) {
                            boolean itemAdded = addItem(playerOid, playerOid, it);
                            log.debug("StartShopHook: ReaddItem "+it);
                        }
                        return true;
                    }
                    boolean removed = removeItemFromBag(rootBagOid, itOid);
                    removedItems.add(itOid);
                    if (removed) {
                        PlayerShopItem asi = new PlayerShopItem();
                        asi.setItemOid(itOid);
                        asi.setCurrency(itcurr);
                        asi.setPrices(itcost);
                        asi.setSell(true);
                        items.add(asi);
                        Log.debug("StartShopHook: removed Item");
                        item.unacquired(rootBagOid);
                    } else {
                        Log.debug("StartShopHook: remove failed");
                    }
                } finally {

                }
            }
//boolean itemAdded = addItem(playerOid, playerOid, item.getOid());
            int buynum = (int)eMsg.getProperty("buyNum");
            if(Log.loggingDebug)Log.debug("StratShopHook: buynum="+buynum);
            HashMap<Integer, Long> currencyOffer = new HashMap<Integer, Long>();
            
            for(int i = 0 ; i < buynum ; i++) {
                int itOid = (int)eMsg.getProperty("bitemId"+i);
                int itemCount = (int)eMsg.getProperty("bitemCount"+i);
                int itcurr = (int)eMsg.getProperty("bitemCurr"+i);
                long itcost = (long)eMsg.getProperty("bitemCost"+i);
                PlayerShopItem asi = new PlayerShopItem();
                asi.setTemplateId(itOid);
                asi.setCurrency(itcurr);
                asi.setPrices(itcost);
                asi.setCount(itemCount);
                asi.setSell(false);
                items.add(asi);
                Log.debug("StartShopHook: removed Item");
                if(currencyOffer.containsKey(itcurr))
                    currencyOffer.put(itcurr, currencyOffer.get(itcurr)+itcost*itemCount);
                else
                    currencyOffer.put(itcurr, itcost*itemCount);
                }
            if(Log.loggingDebug)    Log.debug("StartShopHook: items="+items.size());
            
            long buyout = 0;
            int currencyType = 0;
            for (int currency : currencyOffer.keySet()) {
                int cType = currency;
                long amount = currencyOffer.get(currency);
                
                Currency c = Agis.CurrencyManager.get(cType);
                if(Log.loggingDebug)log.debug("StartShopHook: convert Currency cType:" + cType + " amount:" + amount + " currency:" + currency + " c:" + c.getCurrencyID() + " convet to: " + c.getCurrencyThatConvertsToThis() + " getConversionAmountReq:"
                        + c.getConversionAmountReq());
                if (c != null)
                    while (c.getCurrencyThatConvertsToThis() != null) {
                        if(Log.loggingDebug)    log.debug("StartShopHook: convert Currency cType:" + cType + " c:" + c.getCurrencyID() + " convet to: " + c.getCurrencyThatConvertsToThis().getCurrencyID() + " getConversionAmountReq:" + c.getConversionAmountReq());
                        c = c.getCurrencyThatConvertsToThis();
                        amount *= c.getConversionAmountReq();
                    }
                buyout += amount;
                 currencyType = c.getCurrencyID();
            }
            if(Log.loggingDebug)Log.debug("StartShopHook: buyout="+buyout);
        
            if (!AgisInventoryClient.checkCurrency(playerOid, currencyType, buyout )) {
                EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.NOT_ENOUGH_CURRENCY, 0, "");
                log.debug("StartShopHook: playerOid:" + playerOid + " NOT_ENOUGH_CURRENCY");
                for(OID it : removedItems) {
                    boolean itemAdded = addItem(playerOid, playerOid, it);
                    log.debug("StartShopHook: ReaddItem "+it);
                }
                return true;
            }
            if(buyout>0) {
                alterMobCurrency(playerOid, currencyType, -buyout);
                alterCurrecyLog(playerOid,null, currencyType, -buyout,"StartShopHook");

            }
            Log.debug("StartShopHook: get curreny");
            OID shopOid = Engine.getOIDManager().getNextOid();
            InventoryInfo iInfo = getInventoryInfo(playerOid);
            
            if(Log.loggingDebug)Log.debug("StartShopHook: shopOid="+shopOid);
            aDB.saveNewPlayerStore(shopOid, playerOid, items, iInfo.getShopTag(), message, iInfo.getShopDestroyOnLogOut(), iInfo.getShopTimeOut(),iInfo.getShopMobTemplate()<1);
            
            if(iInfo.getShopMobTemplate() > 0) {
                AgisMobClient.CreateShopSpawn(playerOid, shopOid,iInfo.getShopMobTemplate(), iInfo.getShopTimeOut(),message, iInfo.getShopDestroyOnLogOut());
                
            }else {
                CoordinatedEffect cE = new CoordinatedEffect("StartShop");
                cE.sendSourceOid(true);
                cE.sendTargetOid(true);
                cE.invoke(playerOid, playerOid);
                EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "shopTitle", message);
                EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "playerShop", true);
                EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "plyShopId", shopOid);
                EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, true);
                EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, true);
                Log.debug("StartShopHook.apply: set incapacitated ");
            //    CombatClient.setCombatInfoState(playerOid, CombatInfo.COMBAT_STATE_INCAPACITATED);
            
            }
            sendBagInvUpdate(playerOid);
            //Reset params needed to create Shop
            if (iInfo != null) {
                iInfo.setShopMobTemplate(-1);
                iInfo.setShopSlots(-1);
                iInfo.setNumShops(-1);
                iInfo.setShopTag("");   
                iInfo.setShopTimeOut(0);    
            }
            
            sendShopListUpdate(playerOid);
            log.debug("StartShopHook: END");
            return true;
        }
    }
    
    void sendShopListUpdate(OID playerOid) {
        ArrayList<PlayerShop> list = aDB.getAllPlayerShop(playerOid);
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "list_player_shop");
        int c = 0;
        for (PlayerShop ps : list) {
            props.put("sOid" + c, ps.getShopOid());
            props.put("sMsg" + c, ps.getTitle());
            c++;
        }
        props.put("num", c);
        TargetedExtensionMessage resultMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
        Engine.getAgent().sendBroadcast(resultMsg);
    }
    
    class StartPlayerShopHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            StartPlayerShopMessage eMsg = (StartPlayerShopMessage) msg;
            OID playerOid = eMsg.getSubject();
            String model = eMsg.getModel();
            int mobtemplate = eMsg.getMobTemplate();
            String tag = eMsg.getTag();
            int numShops = eMsg.getNumShop();
            int slots = eMsg.getSlots();
            int shopTimeOut = eMsg.getShopTimeOut();
            boolean destroyOnLogOut = eMsg.getDestroyOnLogOut();
            if (Log.loggingDebug)
                log.debug("StartPlayerShopHook: Start playerOid=" + playerOid + " model=" + model + " mobtemplate=" + mobtemplate + " tag=" + tag + " numShops=" + numShops + " slots=" + slots);
            int count = aDB.countPlayerStore(playerOid, tag);
            if (count >= numShops) {
                if (Log.loggingDebug)
                    log.debug("StartPlayerShopHook: limit player shops with tag " + tag + " was exided");
                EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.PLAYER_SHOP_LIMIT, count, "");
                return true;
            }
            ObjectInfo oi = WorldManagerClient.getObjectInfo(playerOid);
            OID instanceOid = oi.instanceOid;
            InstanceInfo ii = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_ALL_INFO);
            if(ii.inctanceType!=0) {
                EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.PLAYER_SHOP_WRONG_INSTANCE_TYPE, 0, "");
                if (Log.loggingDebug)   log.debug("StartPlayerShopHook:  wrong Instance Type !! "+ii.inctanceType);
                return true;
            }
            InventoryInfo iInfo = getInventoryInfo(playerOid);
            iInfo.setShopMobTemplate(mobtemplate);
            iInfo.setShopSlots(slots);
            iInfo.setNumShops(numShops);
            iInfo.setShopTag(tag);
            iInfo.setShopTimeOut(shopTimeOut);
            iInfo.setShopDestroyOnLogOut(destroyOnLogOut);

            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("ext_msg_subtype", "start_player_shop");
            props.put("slots", slots);
            props.put("Id", 0L);
            TargetedExtensionMessage resultMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
            Engine.getAgent().sendBroadcast(resultMsg);
            log.debug("StartPlayerShopHook: END");
            return true;
        }
    }
    
    
    /**
     * Helper class used to track what item can be equipped in what slot.
     * @author Andrew
     *
     */
    public static class EquipMap implements Serializable {

        public EquipMap() {
        }
        
        /**
         * returns the slot for item, can return null
         */
        public AgisEquipSlot getSlot(OID itemOid) {
            for (Map.Entry<AgisEquipSlot, OID> entry : map.entrySet()) {
                OID oItemOid = entry.getValue();
                if (oItemOid.equals(itemOid) && !entry.getKey().getName().startsWith("Set_")) {
                    if (Log.loggingDebug)
                        log.debug("EquipMap.getSlot: found item=" + itemOid + " slot=" + entry.getKey());
                    return entry.getKey();
                }
            }
            if (Log.loggingDebug)
                log.debug("EquipMap.getSlot: item=" + itemOid + " slot=null");
            return null;
        }
        
		public String toString() {
			String s = "[EquipMap:";
			for (Map.Entry<AgisEquipSlot, OID> entry : map.entrySet()) {
				s += " " + entry.getKey() + ":" + entry.getValue();
			}
			return s + "]";
		}
        
        public OID get(AgisEquipSlot slot) {
            return map.get(slot);
        }
        
        public void put(AgisEquipSlot slot, OID longVal) {
            Log.debug("SLOT: putting slot: " + slot+" "+longVal);
            map.put(slot, longVal);
        }
        
        public void remove(AgisEquipSlot slot) {
            Log.debug("SLOT: removing slot: " + slot);
            map.remove(slot);
        }

        public boolean containsValue(OID itemOid) {
            return map.containsValue(itemOid);
        }

        public HashMap<AgisEquipSlot, OID> getEquipMap() {
            return map;
        }

        public void setEquipMap(HashMap<AgisEquipSlot, OID> map) {
            this.map = map;
        }
        
        HashMap<AgisEquipSlot, OID> map = new HashMap<AgisEquipSlot, OID>();
        private static final long serialVersionUID = 1L;
    }

    /**
     * Gets the EquipMap for the player/mob.
     * @param activatorOid
     * @return
     */
    public EquipMap getEquipMap(OID activatorOid) {
     //   lock.lock();
        try {
            InventoryInfo subObj = AgisInventoryPlugin.getInventoryInfo(activatorOid);
            EquipMap map = null;
            if (subObj != null) {
                Serializable smap =  subObj.getProperty(EQUIP_MAP_PROP);
                if (smap != null) {
                    map = (EquipMap) smap;
                } else {
                    log.debug("getEquipMap InventoryInfo not have property " + EQUIP_MAP_PROP);
                }
                if (map == null) {
                    log.debug("getEquipMap genering new EquipMap and stor it in InventoryInfo for " + activatorOid);
                    map = new EquipMap();
                    subObj.setProperty(EQUIP_MAP_PROP, map);
                    Engine.getPersistenceManager().setDirty(subObj);
                }
                return map;         
                
            } else {
                log.debug("getEquipMap InventoryInfo is null");
                map = new EquipMap();
            }
            return map;
            
        } finally {
         //   lock.unlock();
        }
    }
    
    public HashMap<OID, AgisItem> getEquipMap(InventoryInfo iInfo) 
    {
    	if (iInfo != null) 
    	{
            OID equipBag = iInfo.getEquipmentItemBag();
            if (equipBag != null) 
            {
                Bag bag = getBag(equipBag);
                ArrayList<OID> _list = bag.getItemsListArray();
                HashMap<OID,AgisItem> list = new HashMap<OID,AgisItem>();
                for(OID oid : _list) 
                {
                    if(oid!=null) {
                        AgisItem ai = getAgisItem(oid);
                        list.put(oid, ai);
                    }
                }
                return list;
            }
        }
    	return null;
    }

    /**
     * Sets the InventoryInfo for the player to dirty causing it to be saved to the
     * internal object database.
     * @param mobOid
     */
    public void setDirty(OID mobOid) {
        InventoryInfo subObj = getInventoryInfo(mobOid);
        Engine.getPersistenceManager().setDirty(subObj);
    }

    // map (cache) of players base dc
    Map<OID, DisplayContext> baseDCMap = new HashMap<OID, DisplayContext>();
    public static final String EQUIP_MAP_PROP = "equipMap";
    
    public enum ItemAcquireResult {
        SUCCESS,
        INSUFFICIENT_CURRENCY,
        STACK_LIMIT_REACHED,
        ITEM_DOESNT_EXIST,
        UNKNOWN_FAILURE
    }
    
    /**
     * Maps and constants
     */
    
    // Item Weights
    public static HashMap<String, Float> equipmentSlots = new HashMap<String, Float>();
    public static ArrayList<ItemWeight> itemPrefixes = new ArrayList<ItemWeight>();
    public static ArrayList<ItemWeight> itemSuffixes = new ArrayList<ItemWeight>();
    public static Random random = new Random();
    
    public static float getSlotModifier(String slot) {
        if (slot.equals("Head"))
            return 0.28f;
        else if (slot.equals("Shoulder"))
            return 0.14f;
        else if (slot.equals("Chest"))
            return 0.28f;
        else if (slot.equals("Hands"))
            return 0.14f;
        else if (slot.equals("Waist"))
            return 0.16f;
        else if (slot.equals("Legs"))
            return 0.27f;
        else if (slot.equals("Feet"))
            return 0.14f;
        else if (slot.equals("Back"))
            return 0.15f;
        else if (slot.equals("Neck"))
            return 0.16f;
        else if (slot.equals("Ring"))
            return 0.14f;
        else if (slot.equals("Main Hand"))
            return 0.25f;
        else if (slot.equals("Off Hand"))
            return 0.25f;
        else if (slot.equals("RangedWeapon"))
            return 0.25f;
        else if (slot.equals("Any Hand"))
            return 0.25f;
        else if (slot.equals("Two Hand"))
            return 0.50f;
        return 0.0f;
    }
    
    public static float getGradeModifier(int grade) {
        if (grade == 1) 
            return 0.667f;
        else if (grade == 2) 
            return 0.8f;
        else if (grade == 3) 
            return 1.0f;
        else if (grade == 4) 
            return 1.25f;
        else if (grade == 5) 
            return 1.55f;
        else if (grade == 6) 
            return 1.95f;
        else
            return 0.5f;
    }
    
    public static float getStatModifier(String statName) {
        /*for (int i = 0; i < attributes.length; i++) {
            if (attributes[i].equals(statName))
                return 1.0f;
        }
        for (int i = 0; i < resists.length; i++) {
            if (resists[i].equals(statName))
                return 0.5f;
        }
        if (statName.equals("damage"))
            return 2.0f;*/
        return 1.0f;
    }
    
    public static List<Float> getArmourTypeModifier(String armourType) {
        List<Float> armourModifiers = new LinkedList<Float>();
        if (armourType.equals("Silk")) {
            armourModifiers.add(0.1f);
            armourModifiers.add(0.9f);
        } else if (armourType.equals("Wool")) {
            armourModifiers.add(0.2f);
            armourModifiers.add(0.8f);
        } else if (armourType.equals("Soft Leather")) {
            armourModifiers.add(0.3f);
            armourModifiers.add(0.7f);
        } else if (armourType.equals("Hard Leather")) {
            armourModifiers.add(0.4f);
            armourModifiers.add(0.6f);
        } else if (armourType.equals("Chain")) {
            armourModifiers.add(0.5f);
            armourModifiers.add(0.5f);
        } else if (armourType.equals("Scale")) {
            armourModifiers.add(0.6f);
            armourModifiers.add(0.4f);
        } else if (armourType.equals("Branded")) {
            armourModifiers.add(0.7f);
            armourModifiers.add(0.3f);
        } else if (armourType.equals("Plate")) {
            armourModifiers.add(0.8f);
            armourModifiers.add(0.2f);
        } else {
            armourModifiers.add(0.5f);
            armourModifiers.add(0.5f);
        }

        return armourModifiers;
    }
    
    public static List<Float> getArmourSlotModifier(String armourSlot) {
        List<Float> armourModifiers = new LinkedList<Float>();
        if (armourSlot.equals("Head")) {
            armourModifiers.add(0.12f);
            armourModifiers.add(0.11f);
        } else if (armourSlot.equals("Chest")) {
            armourModifiers.add(0.22f);
            armourModifiers.add(0.06f);
        } else if (armourSlot.equals("Legs")) {
            armourModifiers.add(0.21f);
            armourModifiers.add(0.06f);
        } else if (armourSlot.equals("Hands")) {
            armourModifiers.add(0.11f);
            armourModifiers.add(0.03f);
        } else if (armourSlot.equals("Feet")) {
            armourModifiers.add(0.11f);
            armourModifiers.add(0.03f);
        } else if (armourSlot.equals("Shoulder")) {
            armourModifiers.add(0.11f);
            armourModifiers.add(0.03f);
        } else if (armourSlot.equals("Waist")) {
            armourModifiers.add(0.02f);
            armourModifiers.add(0.14f);
        } else if (armourSlot.equals("Back")) {
            armourModifiers.add(0.05f);
            armourModifiers.add(0.10f);
        } else if (armourSlot.equals("Neck")) {
            armourModifiers.add(0.00f);
            armourModifiers.add(0.16f);
        } else if (armourSlot.equals("Ring")) {
            armourModifiers.add(0.00f);
            armourModifiers.add(0.14f);
        } else {
            armourModifiers.add(0.05f);
            armourModifiers.add(0.05f);
        }

        return armourModifiers;
    }
    
    public static void addListsRankingData(OID subjectOid, Short type, int value) {
        synchronized (dataSendingCollection) {
            dataSendingCollection.add(new RankingData(subjectOid, type, value));
            if (sf == null) {
                log.debug("ScheduledFuture is null start schedule RankingDataTick");
                RankingDataTick timeTick = new RankingDataTick();
                sf = Engine.getExecutor().scheduleAtFixedRate(timeTick, 5000, 250, TimeUnit.MILLISECONDS);
            }
        }
    }

    static class RankingDataTick implements Runnable {
        public void run() {
            synchronized (dataSendingCollection) {
                if (dataSendingCollection.size() == 0) {
                    Log.debug("RankingData messages: no data to send");
                    return;
                }
                if (Log.loggingDebug)
                    Log.debug("RankingData: sending data message with num : " + dataSendingCollection.size() + " " + dataSendingCollection);
                AchievementsClient.sendRankingData(new LinkedList<RankingData>(dataSendingCollection));
                dataSendingCollection.clear();
            }
        }
    }
// first OID Instance OID, second OID Player OID, third OID Item OID
    static ConcurrentHashMap<OID,ConcurrentHashMap<OID,ConcurrentHashMap<OID,ItemOnGroundData> >> itemsOnGround = new ConcurrentHashMap<OID,ConcurrentHashMap<OID,ConcurrentHashMap<OID,ItemOnGroundData>>>();
    static ScheduledFuture<?> sf = null;
    static protected List<RankingData> dataSendingCollection  =Collections.synchronizedList(new LinkedList<RankingData>()); 
    

    public static ConcurrentHashMap<String, BonusSettings> globalEventBonusesArray = new ConcurrentHashMap<String, BonusSettings>();


    public static HashMap<String, SlotsSet> slotsSets = new HashMap<String, SlotsSet>();
    public static HashMap<Integer , AgisEquipInfoProfile> slotsProfiles = new HashMap<Integer, AgisEquipInfoProfile>();

    private LinkedList<Integer> storeItems = new LinkedList<Integer>();

    public static boolean INVENTORY_LOOT_ON_GROUND = false;
    public int INVENTORY_LOOT_ON_GROUND_TIMEOUT = 600;
    public long INVENTORY_LOOT_ON_GROUND_TIMEOUT_INTERVAL = 5;
    public int INVENTORY_LOOT_ON_GROUND_MAX_DISTANCE = 10;
    public String INVENTORY_LOOT_ON_GROUND_LOGOUT_QUALITY_MAIL = "5";//Separator ;
    public static int TRADE_INVITE_TIMEOUT =30;
    public int LOOT_DICE_TIMEOUT = 30;//seconds
    public static boolean QUEST_CHECK_EQUIPED_ITEMS = false;
    public static int INVENTORY_BAG_COUNT = 4;
    public int INVENTORY_FIRST_BAG_SIZE = 16;
    public int INVENTORY_OTHER_BAG_SIZE = 0;
    public static int EQUIP_SLOTS_COUNT = 38;
    public static int MAIL_ATTACHMENT_COUNT = 10;
    public static int MAIL_LIFE_DAYS = 30;
    public static int MAIL_COD_LIFE_DAYS = 3;
    public static float SELL_FACTOR = 0.25f; // Alters the amount you get for selling an item (item value / sellFactor)
    public static float REPAIR_RATE = 0.5f; // Alters the cost for repairing an item (item value / repairFactor)
    public int PLAYER_CORPSE_LOOT_DURATION = 0; // How long a lootable corpse of the player exists for
    public int PLAYER_CORPSE_SAFE_LOOT_DURATION = 0; // How long the corpse is only lootable by the player themselves
    public int PLAYER_CORPSE_MOB_TEMPLATE = 0;
    public int BUILDING_CORPSE_LOOT_DURATION =0;// How long a lootable corpse of the building exists for
    public int BUILDING_CORPSE_MOB_TEMPLATE = 0;
    
    public Boolean STORE_BOUND_ITEM_IN_BANK = false;
    public boolean PLAYER_CORPSE_DROPS_EQUIPMENT = true;
    public int DURABILITY_LOSS_CHANCE_FROM_ATTACK = 10; // Out of 100
    public int DURABILITY_LOSS_CHANCE_FROM_DEFEND = 5;
    public int DURABILITY_LOSS_CHANCE_FROM_GATHER = 50;
    public int DURABILITY_LOSS_CHANCE_FROM_CRAFT = 50;
    public boolean DURABILITY_DESTROY_BROKEN_ITEMS = false;
    
    public static boolean GIVE_QUEST_ITEMS_TO_ALL_IN_GROUP = true;
    
    protected float LOOT_DISTANCE = 5F;
    
    
    public static int BANK_SLOTS_COUNT = 20;
    public static int BANK_EXTRA_BAGS_COUNT = 3;
    public static final String bankBagKey = "Bank";
    
    public static boolean SOCKET_FAILED_CLEAR = false;
    public static float SOCKET_CHANCE = 60f;
    public static long SOCKET_CREATE_TIME = 4;
    public static int SOCKET_PRICE_CURRENCY = 3;
    public static int SOCKET_PRICE_BASE = 1500;
    public static int SOCKET_PRICE_PER_GRADE = 50000;
    public static float SOCKET_RESET_CHANCE = 60f;
    public static long SOCKET_RESET_TIME = 4;
    public static int SOCKET_RESET_PRICE_CURRENCY = 3;
    public static int SOCKET_RESET_PRICE_BASE = 1500;
    public static int SOCKET_RESET_PRICE_PER_GRADE = 50000;
    
    public static long ENCHANTING_TIME = 4;
    
    boolean USE_FLAT_REPAIR_RATE = false;
    int FLAT_REPAIR_RATE = 25;
    int FLAT_REPAIR_RATE_GRADE_MODIFIER = 10;
   
    public static AccountDatabase aDB;
    public static AuthDatabase authDB;
    public static String TASK_SOCKET_RESET = "socketReset";
    public static String TASK_SOCKETING = "socketing";
     public static String TASK_ENCHANTING = "enchanting";
    
    static final Logger log = new Logger("AgisInventoryPlugin");
}
