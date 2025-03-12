package atavism.agis.plugins;

import java.util.*;
import java.io.Serializable;

import atavism.msgsys.*;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.messages.PropertyMessage;
import atavism.server.objects.Template;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.agis.objects.*;


/**
 * AGIS-specific calls for sending/getting messages to the AgisInventoryPlugin
 */
public class AgisInventoryClient {
	
	/**
	 * Sends the RemoveOrFindItemMessage, returning the OID of the item if finds.
	 * @param mobOid OID of mob
	 * @param slot Wquip Slot
	 * @return OID of item
	 */
    public static OID findItem(OID mobOid, AgisEquipSlot slot) {
        InventoryClient.RemoveOrFindItemMessage msg = 
            new InventoryClient.RemoveOrFindItemMessage(MSG_TYPE_AGIS_INV_FIND, mobOid, INV_METHOD_SLOT, slot);
	    OID oid = Engine.getAgent().sendRPCReturnOID(msg);
        Log.debug("findItem: got response");
        return oid;
    }
    
    /**
	 * Sends the RemoveOrFindItemMessage, returning the OID of the item if finds.
	 * @param mobOid OID of the mob
	 * @param itemType  item type
	 * @return OID of item
	 */
    public static OID findItemOfType(OID mobOid, String itemType) {
        InventoryClient.RemoveOrFindItemMessage msg = 
            new InventoryClient.RemoveOrFindItemMessage(MSG_TYPE_AGIS_INV_FIND, mobOid, INV_METHOD_TYPE, itemType);
	    OID oid = Engine.getAgent().sendRPCReturnOID(msg);
        Log.debug("findItem: got response");
        return oid;
    }
    
    /**
	 * Sends the RemoveOrFindItemMessage, returning the OID of the item if finds.
	 * @param mobOid OID of the mob
	 * @param weaponType weapon type
	 * @return OID of the item
	 */
    public static OID findWeaponOfType(OID mobOid, String weaponType) {
        InventoryClient.RemoveOrFindItemMessage msg = 
            new InventoryClient.RemoveOrFindItemMessage(MSG_TYPE_AGIS_INV_FIND, mobOid, INV_METHOD_WEAPON_TYPE, weaponType);
	    OID oid = Engine.getAgent().sendRPCReturnOID(msg);
        Log.debug("findItem: got response");
        return oid;
    }

	/**
	 * Sends the RemoveOrFindItemMessage, returning the OID of the item if finds.
	 * @param mobOid OID of the mob
	 * @param slot Equip stop
	 * @return Id Ammo Type
	 */
    public static Integer findItemAmmoType(OID mobOid, AgisEquipSlot slot) {
        InventoryClient.RemoveOrFindItemMessage msg = 
            new InventoryClient.RemoveOrFindItemMessage(MSG_TYPE_AGIS_INV_FIND, mobOid, INV_METHOD_AMMO_TYPE, slot);
	    Integer ammoType = Engine.getAgent().sendRPCReturnInt(msg);
        Log.debug("findItemAmmoType: got response");
        return ammoType;
    }

    /**
     * Sends a message to start a Trade.
     * @param requesterOid OID of the requester
     * @param partnerOid OID of the partner
     */
    public static void tradeStart(OID requesterOid, OID partnerOid) {
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("requesterOid", requesterOid);
        props.put("partnerOid", partnerOid);
        ExtensionMessage msg = new ExtensionMessage(MSG_TYPE_TRADE_START_REQ, requesterOid, props);
        Engine.getAgent().sendBroadcast(msg);
    }

   /* public static void tradeStart(OID requesterOid, OID partnerOid) {
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", MSG_TYPE_TRADE_START_REQ);
        props.put("requesterOid", requesterOid);
        props.put("partnerOid", partnerOid);
      //  ExtensionMessage msg = new ExtensionMessage(MSG_TYPE_TRADE_START_REQ, requesterOid, props);
       // Engine.getAgent().sendBroadcast(msg);
        TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, partnerOid, partnerOid, false, props);
        Engine.getAgent().sendBroadcast(playerMsg);
    }
    */
    
    
    /**
     * Sends a message to update the status of the trade (such as the items offered has changed or a player has accepted/declined).
     * @param requesterOid OID of the requester
     * @param partnerOid OID of the partner
     * @param offerItems List OID of the offer items
     * @param accepted boolean
     * @param cancelled boolean
     */
    public static void tradeUpdate(OID requesterOid, OID partnerOid,
				   LinkedList<OID> offerItems, boolean accepted, boolean cancelled) {
        Log.debug("AgisInventoryClient.tradeUpdate: requesterOid=" + requesterOid + " partnerOid="
		  + partnerOid + " offer=" + offerItems + " accepted=" + accepted + " cancelled=" + cancelled);
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("requesterOid", requesterOid);
        props.put("partnerOid", partnerOid);
        props.put("offerItems", offerItems);
        props.put("accepted", accepted);
        props.put("cancelled", cancelled);
        ExtensionMessage msg = new ExtensionMessage(MSG_TYPE_TRADE_OFFER_REQ, requesterOid, props);
        Engine.getAgent().sendBroadcast(msg);
    }
    
    /**
     * Sends the ItemAcquriedStatusMessage telling the system that the player has either
     * acquired or lost an item.
     * @param oid  
     * @param item 
     * @param acquried 
     */
    public static void itemAcquiredStatusChange(OID oid, AgisItem item, boolean acquried) {
    	ItemAcquiredStatusMessage msg = new ItemAcquiredStatusMessage(oid, item, acquried);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("INV: ItemAcquiredStatusMessage sent");
	}


    /**
     * Message indicating that a player has either acquired or lost an item.
     * @author Andrew Harrison
     *
     */
	public static class ItemAcquiredStatusMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public ItemAcquiredStatusMessage() {
            super();
        }
		public ItemAcquiredStatusMessage(OID oid, AgisItem item, boolean acquried) {
			super(oid);
			setMsgType(MSG_TYPE_ITEM_ACQUIRE_STATUS_CHANGE);
			setItem(item);
			setAcquired(acquried);
		}
		
		public AgisItem getItem() {
			return item;
		}
		public void setItem(AgisItem item) {
			this.item = item;
		}
		
		public boolean getAcquired() {
			return acquired;
		}
		public void setAcquired(boolean acquired) {
			this.acquired = acquired;
		}
		
		private AgisItem item;
		private boolean acquired;
	}
    
	
	public static HashMap<Integer, Integer> getSetsEquiped(OID oid) {
		ExtensionMessage msg = new ExtensionMessage(MSG_TYPE_GET_SETS_EQUIPED, null, oid);
		Log.debug("ITEM: client getSetsEquiped hit 1");
		return (HashMap<Integer, Integer>) Engine.getAgent().sendRPCReturnObject(msg);
	}

	
	
	
	
    /**
     * Sends the ItemEquipStatusMessage telling the system that an item has been equipped.
     * @param oid OID
     * @param itemToEquip AgisItem
     * @param itemToUnequip AgisItem
     * @param slot Slot name
     * @param itemSets currently equipped
     */
    public static void itemEquipStatusChanged(OID oid, AgisItem itemToEquip, AgisItem itemToUnequip, String slot, HashMap<Integer,Integer> itemSets) {
    	ItemEquipStatusMessage msg = new ItemEquipStatusMessage(oid, itemToEquip, itemToUnequip, slot);
    	msg.setProperty("itemSets", itemSets);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("INV: ItemEquipStatusMessage sent");
	}

    /**
     * Message indicating an item is being equipped or unequipped.
     * @author Andrew Harrison
     *
     */
	public static class ItemEquipStatusMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public ItemEquipStatusMessage() {
            super();
        }
		public ItemEquipStatusMessage(OID oid, AgisItem item, AgisItem itemToUnequip, String slot) {
			super(oid);
			setMsgType(MSG_TYPE_ITEM_EQUIP_STATUS_CHANGE);
			setItemToEquip(item);
			setItemToUnequip(itemToUnequip);
			setSlot(slot);
		}
		
		public AgisItem getItemToEquip() {
			return itemToEquip;
		}
		public void setItemToEquip(AgisItem itemToEquip) {
			this.itemToEquip = itemToEquip;
		}
		
		public AgisItem getItemToUnequip() {
			return itemToUnequip;
		}
		public void setItemToUnequip(AgisItem itemToUnequip) {
			this.itemToUnequip = itemToUnequip;
		}
		
		public String getSlot() {
			return slot;
		}
		public void setSlot(String slot) {
			this.slot = slot;
		}
		
		private AgisItem itemToEquip;
		private AgisItem itemToUnequip;
		private String slot;
	}
	
	/**
	 * Sends the RequestOpenMobMessage
	 * @param mobOid OID
	 * @param playerOid OID
	 */
	public static void requestOpenMob(OID mobOid, OID playerOid) {
		RequestOpenMobMessage msg = new RequestOpenMobMessage(mobOid, playerOid);
        Engine.getAgent().sendBroadcast(msg);
    }

	/**
	 * Message indicating the player is trying to open a mob (likely a chest or other resource object).
	 * @author Andrew
	 *
	 */
	public static class RequestOpenMobMessage extends SubjectMessage {

        public RequestOpenMobMessage() {
            super(MSG_TYPE_REQ_OPEN_MOB);
        }

        RequestOpenMobMessage(OID npcOid, OID playerOid) {
            super(MSG_TYPE_REQ_OPEN_MOB, npcOid);
            setPlayerOid(playerOid);
        }
        
        OID playerOid = null;

        public OID getPlayerOid() {
            return playerOid;
        }

        public void setPlayerOid(OID playerOid) {
            this.playerOid = playerOid;
        }

        private static final long serialVersionUID = 1L;
    }
	
	/**
	 * Sends the RemoveSpecificItemMessage.
	 * @param oid OID
	 * @param itemOid OID
	 * @param removeStack boolean
	 * @param numToRemove int
	 */
	public static boolean removeSpecificItem(OID oid, OID itemOid, boolean removeStack, int numToRemove) {
		removeSpecificItemMessage msg = new removeSpecificItemMessage(oid, itemOid, removeStack, numToRemove);
		boolean b =  Engine.getAgent().sendRPCReturnBoolean(msg);
		Log.debug("ITEM: client removeSpecificItemMessage hit 1");
		return b;
	}
	
	public static boolean removeSpecificItem(OID oid, HashMap<OID, Integer> itemsToRemove, boolean removeStack) {
		removeSpecificItemMessage msg = new removeSpecificItemMessage(oid, itemsToRemove, removeStack);
		boolean b =  Engine.getAgent().sendRPCReturnBoolean(msg);
		Log.debug("ITEM: client removeSpecificItemMessage hit 1");
		return b;
	}

	/**
	 * Message used to remove a specific item from the players inventory. Requires the OID of the item to be removed
	 * along with the amount to remove.
	 * @author Andrew Harrison
	 *
	 */
	public static class removeSpecificItemMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public removeSpecificItemMessage() {
            super();
        }
		public removeSpecificItemMessage(OID oid, OID itemOid, boolean removeStack, int numToRemove) {
			super(oid);
			setMsgType(MSG_TYPE_REMOVE_SPECIFIC_ITEM);
			itemsToRemove = new HashMap<OID, Integer>();
			itemsToRemove.put(itemOid, numToRemove);
			setRemoveStack(removeStack);
			Log.debug("ITEM: client removeSpecificItemMessage hit 2");
		}
		public removeSpecificItemMessage(OID oid, HashMap<OID, Integer> itemsToRemove, boolean removeStack) {
			super(oid);
			setMsgType(MSG_TYPE_REMOVE_SPECIFIC_ITEM);
			setItemsToRemove(itemsToRemove);
			setRemoveStack(removeStack);
			Log.debug("ITEM: client removeSpecificItemMessage hit 2");
		}
		
		public HashMap<OID, Integer> getItemsToRemove() {
            return itemsToRemove;
        }
        public void setItemsToRemove(HashMap<OID, Integer> itemsToRemove) {
            this.itemsToRemove = itemsToRemove;
        }
		HashMap<OID, Integer> itemsToRemove;
		
		public boolean getRemoveStack() {
            return removeStack;
        }
        public void setRemoveStack(boolean removeStack) {
            this.removeStack = removeStack;
        }
		boolean removeStack;
	}
	
	/**
	 * Sends the RemoveGenericItemMessage.
	 * @param oid OID 
	 * @param itemID Integer
	 * @param removeStack boolean
	 * @param numToRemove Integer
	 */
	public static boolean removeGenericItem(OID oid, int itemID, boolean removeStack, int numToRemove) {
		removeGenericItemMessage msg = new removeGenericItemMessage(oid, itemID, removeStack, numToRemove);
		boolean b =  Engine.getAgent().sendRPCReturnBoolean(msg);
		Log.debug("ITEM: client removeGenericItemMessage hit 1");
		return b;
	}
	
	public static boolean removeGenericItems(OID oid, HashMap<Integer, Integer> itemsToRemove, boolean removeStack) {
		removeGenericItemMessage msg = new removeGenericItemMessage(oid, itemsToRemove, removeStack);
		boolean b =  Engine.getAgent().sendRPCReturnBoolean(msg);
		Log.debug("ITEM: client removeGenericItemMessage hashmap hit 1");
		return b;
	}

	/**
	 * Message used to remove an item from the players inventory based on the type of item. Requires the
	 * templateID of the item to be removed along with how many to remove.
	 * @author Andrew
	 *
	 */
	public static class removeGenericItemMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public removeGenericItemMessage() {
            super();
        }
		public removeGenericItemMessage(OID oid, int itemID, boolean removeStack, int numToRemove) {
			super(oid);
			Log.debug("ITEM: client removeGenericItemMessage hit 2");
			setMsgType(MSG_TYPE_REMOVE_GENERIC_ITEM);
			itemsToRemove = new HashMap<Integer, Integer>();
			itemsToRemove.put(itemID, numToRemove);
			removeStack(removeStack);
			Log.debug("ITEM: client removeGenericItemMessage hit 3");
		}
		
		public removeGenericItemMessage(OID oid, HashMap<Integer, Integer> itemsToRemove, boolean removeStack) {
			super(oid);
			Log.debug("ITEM: client removeGenericItemMessage hit 2");
			setMsgType(MSG_TYPE_REMOVE_GENERIC_ITEM);
			setItemsToRemove(itemsToRemove);
			removeStack(removeStack);
			Log.debug("ITEM: client removeGenericItemMessage hit 3");
		}
		
		public HashMap<Integer, Integer> getItemsToRemove() {
            return itemsToRemove;
        }
        public void setItemsToRemove(HashMap<Integer, Integer> itemsToRemove) {
            this.itemsToRemove = itemsToRemove;
        }
		HashMap<Integer, Integer> itemsToRemove;
		
		public boolean removeStack() {
            return removeStack;
        }
        public void removeStack(boolean removeStack) {
            this.removeStack = removeStack;
        }
		boolean removeStack;
	}
	
	/**
	 * Sends the countGenericItemMessage, returning the Count of the item.
	 * @param mobOid OID of the mob
	 * @param templateId id of item Tmplate
	 * @return Id Ammo Type
	 */
    public static Integer getCountGenericItem(OID mobOid, Integer templateId) {
        countGenericItemMessage msg =  new countGenericItemMessage(mobOid, templateId);
	    Integer count = Engine.getAgent().sendRPCReturnInt(msg);
        Log.debug("findItemAmmoType: got response");
        return count;
    }

	/**
	 * Message used to count of item from the players inventory based on the item templateId. Requires the
	 * templateID of the item to be count.
	 *
	 */
	public static class countGenericItemMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public countGenericItemMessage() {
            super();
        }
		public countGenericItemMessage(OID oid, Integer itemID) {
			super(oid);
			Log.debug("ITEM: client countGenericItemMessage hit 2");
			setMsgType(MSG_TYPE_COUNT_GENERIC_ITEM);
			setTemplateId(itemID);
			Log.debug("ITEM: client countGenericItemMessage hit 3");
		}
		
		public Integer getTemplateId() {
            return templateId;
        }
        public void setTemplateId(Integer templateId) {
            this.templateId = templateId;
        }
        Integer templateId;
	}
	
	
	/**
	 * Sends the GetSpecificItemDataMessage. Used to send down information about an item to a players client.
	 * @param oid OID 
	 * @param targetOid OID
	 * @param itemOids List Oids
	 */
	public static void getSpecificItemData(OID oid, OID targetOid, ArrayList<Long> itemOids) {
		getSpecificItemDataMessage msg = new getSpecificItemDataMessage(oid, targetOid, itemOids);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client getSpecificItemData hit 1");
	}

	/**
	 * Message used to request information about a list of items to be sent down to a players client.
	 * @author Andrew Harrison
	 *
	 */
	public static class getSpecificItemDataMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public getSpecificItemDataMessage() {
            super();
        }
		public getSpecificItemDataMessage(OID oid, OID targetOid, ArrayList<Long> itemOids) {
			super(oid);
			setMsgType(MSG_TYPE_GET_SPECIFIC_ITEM_DATA);
			setProperty("itemOids", itemOids);
			setProperty("targetOid", targetOid);
			Log.debug("ITEM: client getSpecificItemDataMessage hit 2");
		}
	}
	
	/**
	 * Sends a Message asking for the Template for an item to be returned.
	 * @param oid OID
	 * @param itemID Integer
	 * @return Template
	 */
	public static Template getGenericItemData(OID oid, int itemID) {
		ExtensionMessage msg = new ExtensionMessage(MSG_TYPE_GET_GENERIC_ITEM_DATA, null, oid);
		msg.setProperty("itemID", itemID);
		msg.setProperty("dataType", "id");
		Log.debug("ITEM: client getGenericItemData hit 1");
		return (Template) Engine.getAgent().sendRPCReturnObject(msg);
	}

	/**
	 * Unused Message class, look to remove this in the near future as it looks to be obsolete.
	 * @author Andrew Harrison
	 *
	 */
	public static class getGenericItemDataMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public getGenericItemDataMessage() {
            super();
        }
		public getGenericItemDataMessage(OID oid, String itemName) {
			super(oid);
			setMsgType(MSG_TYPE_GET_GENERIC_ITEM_DATA);
			setProperty("itemName", itemName);
			Log.debug("ITEM: client getGenericItemDataMessage hit 2");
		}
	}
	
	/**
	 * Sends the GenerateItemMessage.
	 * @param oid  
	 * @param templateID 
	 * @param itemName 
	 * @param count 
	 * @param itemProps 
	 */
	public static void generateItem(OID oid, int templateID, String itemName, int count, HashMap<String, Serializable> itemProps) {
		generateItemMessage msg = new generateItemMessage(oid, templateID, itemName, count, itemProps);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client generateItem hit 1");
	}
	public static OID generateItem(OID oid, int templateID, String itemName, int count) {
		ExtensionMessage msg = new ExtensionMessage(MSG_TYPE_GENERATE_ITEM_RESPONSE_OID, null, oid);
		msg.setProperty("templateID", templateID);
		msg.setProperty("itemName", itemName);
		msg.setProperty("count", count);
		Log.debug("ITEM: client generateItem hit 1");
		return Engine.getAgent().sendRPCReturnOID(msg);
		
	}
	
	
	public static HashMap<Integer, Integer> generateItems(OID oid, HashMap<Integer, Integer> itemsToGenerate, boolean failIfNotAllInserted) {
		generateItemMessage msg = new generateItemMessage(oid, itemsToGenerate, failIfNotAllInserted, true);
		//Engine.getAgent().sendBroadcast(msg);
		HashMap<Integer, Integer> itemsLeftOver = (HashMap)Engine.getAgent().sendRPCReturnObject(msg);
		Log.debug("ITEM: client generateItem hit 1");
		return itemsLeftOver;
	}
	
	public static void generateItemsNoResponse(OID oid, HashMap<Integer, Integer> itemsToGenerate, boolean failIfNotAllInserted) {
		generateItemMessage msg = new generateItemMessage(oid, itemsToGenerate, failIfNotAllInserted, false);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client generateItem hit 1");
	}

	public static OID generateItemAsItem(OID oid, OID itemOid, int count,boolean add) {
		ExtensionMessage msg = new ExtensionMessage(MSG_TYPE_GENERATE_ITEM_AS, null, oid);
		msg.setProperty("itemOid", itemOid);
		msg.setProperty("add", add);
		msg.setProperty("count", count);
		Log.debug("ITEM: client generateItemAsItem hit 1");
		return Engine.getAgent().sendRPCReturnOID(msg);
		
	}
	/**
	 * Message used to request the generation of an item based on the templateID specified and then to add the item
	 * to the players inventory.
	 * @author Andrew Harrison
	 *
	 */
	public static class generateItemMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public generateItemMessage() {
            super();
        }
		public generateItemMessage(OID oid, int templateID, String itemName, int count, HashMap<String, Serializable> itemProps) {
			super(oid);
			setMsgType(MSG_TYPE_GENERATE_ITEM);
			setProperty("itemName", itemName);
			setProperty("itemProps", itemProps);
			itemsToGenerate = new HashMap<Integer, Integer>();
			itemsToGenerate.put(templateID, count);
			sendResponse(false);
			
			Log.debug("ITEM: client generateItemMessage hit 2");
		}
		public generateItemMessage(OID oid, HashMap<Integer, Integer> itemsToGenerate, boolean failIfNotAllInserted, boolean sendResponse) {
			super(oid);
			if (sendResponse) {
				setMsgType(MSG_TYPE_GENERATE_ITEM_NEEDS_RESPONSE);
			} else {
				setMsgType(MSG_TYPE_GENERATE_ITEM);
			}
			setItemsToGenerate(itemsToGenerate);
			failIfNotAllInserted(failIfNotAllInserted);
			sendResponse(sendResponse);
			Log.debug("ITEM: client generateItemMessage hit 2");
		}
		
		public HashMap<Integer, Integer> getItemsToGenerate() {
            return itemsToGenerate;
        }
        public void setItemsToGenerate(HashMap<Integer, Integer> itemsToGenerate) {
            this.itemsToGenerate = itemsToGenerate;
        }
		HashMap<Integer, Integer> itemsToGenerate;
		
		// If failIfNotAllInserted is true, it will only insert any items if their is space for all of the items
		public void failIfNotAllInserted(boolean failIfNotAllInserted) {
			this.failIfNotAllInserted = failIfNotAllInserted;
		}
		public boolean failIfNotAllInserted() {
			return failIfNotAllInserted;
		}
		boolean failIfNotAllInserted = false;
		
		public void sendResponse(boolean sendResponse) {
			this.sendResponse = sendResponse;
		}
		public boolean sendResponse() {
			return sendResponse;
		}
		boolean sendResponse = true;
	}
	
	
	
	public static AgisItem getItem(OID itemOid) {
		getItemMessage msg = new getItemMessage(itemOid);
	//	ExtensionMessage msg = new ExtensionMessage(MSG_TYPE_GET_ITEM, null, null);
		msg.setProperty("itemOid", itemOid);
		Log.debug("ITEM: client getGenericItemData hit 1");
		return (AgisItem) Engine.getAgent().sendRPCReturnObject(msg);
}



	public static class getItemMessage extends ExtensionMessage {
		private static final long serialVersionUID = 1L;

		public getItemMessage() {
			super();
			setMsgType(MSG_TYPE_GET_ITEM);
		}

		public getItemMessage(OID itemOid) {
			super();
			setMsgType(MSG_TYPE_GET_ITEM);
			setProperty("itemOid", itemOid);
			Log.debug("ITEM: client getItemMessage hit 2");
		}
	}

	/**
	 * Function to equip Item
	 * 
	 * @param playerOid
	 * @param itemOid
	 * @return result of equip
	 */
	public static boolean equipeItem(OID playerOid, OID itemOid) {
		equipItemMessage msg = new equipItemMessage(playerOid, itemOid);
		Log.debug("ITEM: client getEquipedItems hit 1");
		return Engine.getAgent().sendRPCReturnBoolean(msg);
	}

	public static class equipItemMessage extends PropertyMessage {

		public equipItemMessage() {
			super(MSG_TYPE_EQUIP_ITEM);
		}

		equipItemMessage(OID playerOid, OID itemOid) {
			super(MSG_TYPE_EQUIP_ITEM, playerOid);
			setProperty("itemOid", itemOid);

		}

		private static final long serialVersionUID = 1L;
	}

	/**
	 * Function to unequip Item
	 * 
	 * @param playerOid
	 * @param itemOid
	 * @return result of equip
	 */
	public static boolean unequipeItem(OID playerOid, OID itemOid) {
		unequipItemMessage msg = new unequipItemMessage(playerOid, itemOid);
		Log.debug("ITEM: client getEquipedItems hit 1");
		return Engine.getAgent().sendRPCReturnBoolean(msg);
	}

	public static class unequipItemMessage extends PropertyMessage {

		public unequipItemMessage() {
			super(MSG_TYPE_UNEQUIP_ITEM);
		}

		unequipItemMessage(OID playerOid, OID itemOid) {
			super(MSG_TYPE_UNEQUIP_ITEM, playerOid);
			setProperty("itemOid", itemOid);

		}

		private static final long serialVersionUID = 1L;
	}

	public static HashMap<OID, AgisItem> getEquipedItems(OID playerOid) {
		getEquipItemListMessage msg = new getEquipItemListMessage(playerOid);
		Log.debug("ITEM: client getEquipedItems hit 1");
		return (HashMap<OID, AgisItem>) Engine.getAgent().sendRPCReturnObject(msg);
	}

	public static class getEquipItemListMessage extends SubjectMessage {

		public getEquipItemListMessage() {
			super(MSG_TYPE_GET_EQUIP_ITEMS);
		}

		getEquipItemListMessage(OID playerOid) {
			super(MSG_TYPE_GET_EQUIP_ITEMS, playerOid);

		}

		private static final long serialVersionUID = 1L;
	}

	/***
	 * Function to Set property to Item
	 * 
	 * @param itemOid
	 * @param param
	 * @param Object
	 * @return
	 */
public static ArrayList<OID> GetListItemsWithParam(OID oid, int templateId, int enchantLevel,String sockets) {
	ExtensionMessage msg = new ExtensionMessage(MSG_TYPE_GET_ITEMS_WITH_PARAM, null, oid);
	msg.setProperty("templateId", templateId);
	msg.setProperty("enchantLevel", enchantLevel);
	msg.setProperty("sockets", sockets);
	msg.setProperty("itemGroup", "");
	Log.debug("ITEM: client SetItemProperty hit 1");
	return (ArrayList<OID>)Engine.getAgent().sendRPCReturnObject(msg);
	
}
public static ArrayList<OID> GetListItemsWithParam(OID oid, String itemGroup) {
	ExtensionMessage msg = new ExtensionMessage(MSG_TYPE_GET_ITEMS_WITH_PARAM, null, oid);
	msg.setProperty("templateId", -1);
	msg.setProperty("enchantLevel", 0);
	msg.setProperty("sockets", "");
	msg.setProperty("itemGroup", itemGroup);
	Log.debug("ITEM: client SetItemProperty hit 1");
	return (ArrayList<OID>)Engine.getAgent().sendRPCReturnObject(msg);
	
}

public static boolean AlterItemCount(OID oid, OID itemOid,int count) {
	ExtensionMessage msg = new ExtensionMessage(MSG_TYPE_SET_ITEM_COUNT, null, oid);
	msg.setProperty("itemOid", itemOid);
	//msg.setProperty("value", Object);
	msg.setProperty("count", count);
	Log.debug("ITEM: client SetItemProperty hit 1");
	return Engine.getAgent().sendRPCReturnBoolean(msg);
	
}

public static boolean SetItemProperty(OID oid, OID itemOid,String param,Serializable Object ) {
	ExtensionMessage msg = new ExtensionMessage(MSG_TYPE_SET_ITEM_PROPERTY, null, oid);
	msg.setProperty("itemOid", itemOid);
	msg.setProperty("value", Object);
	msg.setProperty("param", param);
	Log.debug("ITEM: client SetItemProperty hit 1");
	return Engine.getAgent().sendRPCReturnBoolean(msg);
	
}
public static boolean  SetItemProperty(OID itemOid,String param,Serializable Object ) {
	SetItemMessage msg = new SetItemMessage(itemOid);
	msg.setProperty("itemOid", itemOid);
	msg.setProperty("value", Object);
	msg.setProperty("param", param);
	
	Log.debug("ITEM: client SetItemProperty hit 1");
	return  Engine.getAgent().sendRPCReturnBoolean(msg);
}



public static class SetItemMessage extends ExtensionMessage {
private static final long serialVersionUID = 1L;
public SetItemMessage() {
    super();
    setMsgType(MSG_TYPE_SET_ITEM_PROPERTY);
	 }
public SetItemMessage( OID itemOid) {
	super();
	setMsgType(MSG_TYPE_SET_ITEM_PROPERTY);
	setProperty("itemOid", itemOid);
	Log.debug("ITEM: client SetItemMessage hit 2");
}
}
/*
public static OID  GenerateItemAsItem(OID itemOid, Integer count ) {
	generateItemAsItem msg = new generateItemAsItem(itemOid);
	msg.setProperty("itemOid", itemOid);
	msg.setProperty("count", count);
	Log.debug("ITEM: client GenerateItemAsItem hit 1");
	return  Engine.getAgent().sendRPCReturnOID(msg);
}

public static class generateItemAsItem extends ExtensionMessage {
private static final long serialVersionUID = 1L;
public generateItemAsItem() {
    super();
    setMsgType(MSG_TYPE_GENERATE_ITEM_AS);
	 }
public generateItemAsItem( OID itemOid) {
	super();
	setMsgType(MSG_TYPE_GENERATE_ITEM_AS);
	setProperty("itemOid", itemOid);
	Log.debug("ITEM: client generateItemAsItem hit 2");
}
}
*/

	
/***
 *  function getItemProperty
 * 
 * @param playerOid
 * @param itemOid
 * @param property
 * @return 
 */
	public static Object getItemProperty(OID playerOid, OID itemOid, String property) {
			ExtensionMessage msg = new ExtensionMessage(MSG_TYPE_GET_ITEM_PROPERTY, null, playerOid);
			msg.setProperty("itemOid", itemOid);
			msg.setProperty("itemProp", property);
			Log.debug("ITEM: client getGenericItemData hit 1");
			return  Engine.getAgent().sendRPCReturnObject(msg);
	}
	
	
	
	public static class itemPropertyMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public itemPropertyMessage() {
            super();
        }
		public itemPropertyMessage(OID platerOid, OID itemOid, String property) {
			super(platerOid);
			setMsgType(MSG_TYPE_GET_ITEM_PROPERTY);
			setProperty("itemOid", itemOid);
			setProperty("itemProp", property);
			Log.debug("ITEM: client itemPropertyMessage hit 2");
		}
	}
	
	
	/**
	 * Sends the PlaceBagMessage.
	 * @param oid OID
	 * @param itemOid OID
	 * @param bagSpotNum Integer
	 */
	public static void placeBag(OID oid, OID itemOid, int bagSpotNum) {
		placeBagMessage msg = new placeBagMessage(oid, itemOid, bagSpotNum);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client placeBag hit 1");
	}

	/**
	 * Message used to put a bag item from the players inventory into a bag slot. 
	 * Do not use this to move a bag from one slot to another.
	 * @author Andrew Harrison
	 *
	 */
	public static class placeBagMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public placeBagMessage() {
            super();
        }
		public placeBagMessage(OID oid, OID itemOid, int bagSpotNum) {
			super(oid);
			setMsgType(MSG_TYPE_PLACE_BAG);
			setProperty("itemOid", itemOid);
			setProperty("bagSpotNum", bagSpotNum);
			Log.debug("ITEM: client placeBagMessage hit 2");
		}
	}
	
	/**
	 * Sends the MoveBagMessage.
	 * @param oid OID
	 * @param bagSpotNum Integer
	 * @param newSpotNum Integer
	 */
	public static void moveBag(OID oid, int bagSpotNum, int newSpotNum) {
		moveBagMessage msg = new moveBagMessage(oid, bagSpotNum, newSpotNum);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client moveBagMessage hit 1");
	}

	/**
	 * Message used to move a bag from one bag slot into another.
	 * @author Andrew Harrison
	 *
	 */
	public static class moveBagMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public moveBagMessage() {
            super();
        }
		public moveBagMessage(OID oid, int bagSpotNum, int newSpotNum) {
			super(oid);
			setMsgType(MSG_TYPE_MOVE_BAG);
			setProperty("bagSpotNum", bagSpotNum);
			setProperty("newSpotNum", newSpotNum);
			Log.debug("ITEM: client moveBagMessage hit 2");
		}
	}
	
	/**
	 * Sends the RemoveBagMessage.
	 * @param oid OID
	 * @param bagSpotNum Integer
	 * @param containerId Integer
	 * @param slotId Integer
	 */
	public static void removeBag(OID oid, int bagSpotNum, int containerId, int slotId) {
		removeBagMessage msg = new removeBagMessage(oid, bagSpotNum, containerId, slotId);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client removeBagMessage hit 1");
	}

	/**
	 * Message used to remove a bag from the players bag slots and put it back into the inventory.
	 * @author Andrew Harrison
	 *
	 */
	public static class removeBagMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public removeBagMessage() {
            super();
        }
		public removeBagMessage(OID oid, int bagSpotNum, int containerId, int slotId) {
			super(oid);
			setMsgType(MSG_TYPE_REMOVE_BAG);
			setProperty("bagSpotNum", bagSpotNum);
			setProperty("containerId", containerId);
			setProperty("slotId", slotId);
			Log.debug("ITEM: client removeBagMessage hit 2");
		}
	}
	
	/**
	 * Sends the LootItemMessage.
	 * @param oid
	 * @param itemOid
	 * @param mobOid
	 */
	public static void lootItem(OID oid, OID itemOid, OID mobOid) {
		lootItemMessage msg = new lootItemMessage(oid, itemOid, mobOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client lootItemMessage hit 1");
	}

	/**
	 * Message used to indicate the player wants to loot the specified the item 
	 * from the specified mob.
	 * @author Andrew Harrison
	 *
	 */
	public static class lootItemMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public lootItemMessage() {
            super();
        }
		public lootItemMessage(OID oid, OID itemOid, OID mobOid) {
			super(oid);
			setMsgType(MSG_TYPE_LOOT_ITEM);
			setProperty("mobOid", mobOid);
			setProperty("itemOid", itemOid);
			Log.debug("ITEM: client lootItemMessage hit 2");
		}
	}
	
	/**
	 * Sends the LootAllMessage.
	 * @param oid OID
	 * @param mobOid OID
	 */
	public static void lootAll(OID oid, OID mobOid) {
		lootAllMessage msg = new lootAllMessage(oid, mobOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client lootAllMessage hit 1");
	}

	/**
	 * Message used to indicate a player wants to loot all items from
	 * the specified mob.
	 * @author Andrew Harrison
	 *
	 */
	public static class lootAllMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public lootAllMessage() {
            super();
        }
		public lootAllMessage(OID oid, OID mobOid) {
			super(oid);
			setMsgType(MSG_TYPE_LOOT_ALL);
			setProperty("mobOid", mobOid);
			Log.debug("ITEM: client lootAllMessage hit 2");
		}
	}
	
	/**
	 * Sends the GenerateLootEffectMessage.
	 * @param oid 
	 * @param lootsChance 
	 * @param lootsCount 
	 */
	public static void generateLootEffect(OID oid, HashMap<Integer, Float> lootsChance,HashMap<Integer, Integer> lootsCount) {
		generateLootEffectMessage msg = new generateLootEffectMessage(oid, lootsChance, lootsCount);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client generateLootEffectMessage hit 1");
	}
	
	
	
	/**
	 * Message used to request the generation of Items from loot tabel for player.
	 * @author Rafal Dorobisz 
	 */
	
	public static class generateLootEffectMessage extends PropertyMessage {

        public generateLootEffectMessage() {
            super(MSG_TYPE_GENERATE_LOOT_EFFECT);
        }

        public generateLootEffectMessage(OID playerOid, HashMap<Integer, Float> lootsChance, HashMap<Integer, Integer> lootsCount) {
            super(MSG_TYPE_GENERATE_LOOT_EFFECT, playerOid);
            setLootsChance(lootsChance);
            setLootsCount(lootsCount);
        }
        
        public HashMap<Integer, Float> getLootsChance() {
            return lootsChance;
        }

        public void setLootsChance(HashMap<Integer, Float> lootsChance) {
            this.lootsChance = lootsChance;
        }
        HashMap<Integer, Float> lootsChance;
        
        public HashMap<Integer, Integer> getLootsCount() {
            return lootsCount;
        }

        public void setLootsCount(HashMap<Integer, Integer> lootsCount) {
            this.lootsCount = lootsCount;
        }
        
        HashMap<Integer, Integer> lootsCount;
        
        private static final long serialVersionUID = 1L;
    }
	/**
	 * Sends the GenerateLootMessage.
	 * @param oid
	 */
	public static void generateLoot(OID oid, OID tagOwner) {
		generateLootMessage msg = new generateLootMessage(oid, tagOwner);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client generateLootMessage hit 1");
	}
	
	
	/**
	 * Sends the GenerateLootMessage.
	 * @param oid
	 */
	public static void generateLoot(OID oid, OID tagOwner, ArrayList<OID> targets) {
		generateLootMessage msg = new generateLootMessage(oid, targets);
		msg.setTagOwner(tagOwner);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client generateLootMessage hit 1");
	}
	
	/**
	 * Sends the GenerateLootMessage.
	 * @param oid
	 */
	public static void generateLoot(OID playerOid, Point loc, Quaternion orient, OID instanceOid, int lootTable, HashMap<Integer, Integer> items) {
		PropertyMessage msg = new PropertyMessage(MSG_TYPE_GENERATE_LOOT_FROM_LOOT_TABLE, playerOid);
		msg.setProperty("loc", loc);
		msg.setProperty("orient", orient);
		msg.setProperty("instanceOid", instanceOid);
		msg.setProperty("lootTable", lootTable);
		msg.setProperty("items", items);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client generateLoot hit 1");
	}

	
	/**
	 * Sends the GenerateLootMessage.
	 * @param oid
	 */
	public static void generatePlayerLoot(OID oid, OID tagOwner, Point loc) {
		generateLootMessage msg = new generateLootMessage(oid, tagOwner, true, loc);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client generateLootMessage hit 1");
	}

	/**
	 * Message used to request the generation of loot for a mob.
	 * @author Andrew Harrison
	 *
	 */
	public static class generateLootMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public generateLootMessage() {
            super();
        }
		public generateLootMessage(OID oid, OID tagOwner) {
			super(oid);
			setTagOwner(tagOwner);
			setMsgType(MSG_TYPE_GENERATE_LOOT);
			Log.debug("ITEM: generateLootMessage created");
		}
		public generateLootMessage(OID oid, OID tagOwner, boolean isPlayer) {
			super(oid);
			setMsgType(MSG_TYPE_GENERATE_LOOT);
			setTagOwner(tagOwner);
			setIsPlayer(isPlayer);
			Log.debug("ITEM: generateLootMessage created with isPlayer");
		}
		public generateLootMessage(OID oid, OID tagOwner, boolean isPlayer, Point loc) {
			super(oid);
			setMsgType(MSG_TYPE_GENERATE_LOOT);
			setTagOwner(tagOwner);
			setIsPlayer(isPlayer);
			setLoc(loc);
			Log.debug("ITEM: generateLootMessage created with isPlayer");
		}
		public generateLootMessage(OID oid, ArrayList<OID> targets) {
			super(oid);
			setTargats(targets);
			setMsgType(MSG_TYPE_GENERATE_LOOT);
			Log.debug("ITEM: generateLootMessage created with targets");
		}
		
		boolean isPlayer = false;
		public boolean getIsPlayer() {
			return isPlayer;
		}
		public void setIsPlayer(boolean isPlayer) {
			this.isPlayer = isPlayer;
		}
		
		Point loc;
		public Point getLoc() {
			return loc;
		}
		public void setLoc(Point loc) {
			this.loc = loc;
		}
		
		OID tagOwner;
		public OID getTagOwner() {
			return tagOwner;
		}
		public void setTagOwner(OID tagOwner) {
			this.tagOwner = tagOwner;
		}
		ArrayList<OID> targets;
		public ArrayList<OID> getTagats() {
			return targets;
		}
		public void setTargats(ArrayList<OID> targets) {
			this.targets = targets;
		}
		
	}
	
	/**
	 * Sends the GetLootListMessage.
	 * @param oid
	 * @param mobOid
	 */
	public static void getLootList(OID oid, OID mobOid) {
		getLootListMessage msg = new getLootListMessage(oid, mobOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client getLootListMessage hit 1");
	}

	/**
	 * Message used to request the list of loot for the specified mob
	 * is sent down to the requesting player.
	 * @author Andrew Harrison
	 *
	 */
	public static class getLootListMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public getLootListMessage() {
            super();
        }
		public getLootListMessage(OID oid, OID mobOid) {
			super(oid);
			setMsgType(MSG_TYPE_GET_LOOT_LIST);
			setProperty("mobOid", mobOid);
			Log.debug("ITEM: client getLootListMessage hit 2");
		}
	}
	
	/**
	 * Sends the GetMerchantListMessage.
	 * @param oid
	 * @param mobOid
	 */
	public static void getMerchantList(OID oid, OID mobOid) {
		getMerchantListMessage msg = new getMerchantListMessage(oid, mobOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client getMerchantListMessage hit 1");
	}

	/**
	 * Message used to request the list of items the specified merchant
	 * sells to be sent to the requesting player.
	 * @author Andrew Harrison
	 *
	 */
	public static class getMerchantListMessage extends SubjectMessage {
		private static final long serialVersionUID = 1L;
        public getMerchantListMessage() {
            super();
        }
		public getMerchantListMessage(OID oid, OID mobOid) {
			super(MSG_TYPE_GET_MERCHANT_LIST, mobOid);
			setPlayerOid(oid);
			Log.debug("ITEM: client getMerchantListMessage hit 2");
		}
		
		OID playerOid = null;

        public OID getPlayerOid() {
            return playerOid;
        }
        public void setPlayerOid(OID playerOid) {
            this.playerOid = playerOid;
        }
	}
	
	/**
	 * Sends the PurchaseItemFromMerchantMessage.
	 * @param oid
	 * @param mobOid
	 */
	public static void purchaseItemFromMerchant(OID oid, OID mobOid, int itemID, int count) {
		purchaseItemFromMerchantMessage msg = new purchaseItemFromMerchantMessage(oid, mobOid, itemID, count);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: purchaseItemFromMerchantMessage hit 1");
	}

	/**
	 * Message used to request the purchase of the specified item from the specified NPC.
	 * @author Andrew Harrison
	 *
	 */
	public static class purchaseItemFromMerchantMessage extends SubjectMessage {
		private static final long serialVersionUID = 1L;
        public purchaseItemFromMerchantMessage() {
            super();
        }
		public purchaseItemFromMerchantMessage(OID oid, OID mobOid, int itemID, int count) {
			super(MSG_TYPE_PURCHASE_ITEM_FROM_MERCHANT, mobOid);
			setPlayerOid(oid);
			setItemID(itemID);
			setCount(count);
			Log.debug("ITEM: purchaseItemFromMerchantMessage hit 2");
		}
		
		OID playerOid = null;
		int itemID = -1;
		int count = 1;

        public OID getPlayerOid() {
            return playerOid;
        }
        public void setPlayerOid(OID playerOid) {
            this.playerOid = playerOid;
        }
        
        public int getItemID() {
            return itemID;
        }
        public void setItemID(int itemID) {
            this.itemID = itemID;
        }
        
        public int getCount() {
            return count;
        }
        public void setCount(int count) {
            this.count = count;
        }
	}
	
	/**
	 * Sends the PurchaseItem Message. Sends back a boolean response indicating
	 * whether or not the purchase succeeded.
	 * @param playerOid
	 * @param itemID
	 * @param count
	 * @return
	 */
	public static Boolean purchaseItem(OID playerOid, int itemID, int count){
		purchaseItemMessage msg = new purchaseItemMessage(playerOid, itemID, count);
    	Boolean purchaseSuccessful = Engine.getAgent().sendRPCReturnBoolean(msg);
    	return purchaseSuccessful;
    }
	
	/**
	 * Message used to request the purchase of an item.
	 * @author Andrew
	 *
	 */
	public static class purchaseItemMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public purchaseItemMessage() {
            super();
        }
		public purchaseItemMessage(OID oid, int itemID, int count) {
			super(oid);
			setMsgType(MSG_TYPE_PURCHASE_ITEM);
			setItemID(itemID);
			setCount(count);
			Log.debug("ITEM: client getLootListMessage hit 2");
		}
		
		int itemID = -1;
		int count = 1;
		
		public int getItemID() {
            return itemID;
        }
        public void setItemID(int itemID) {
            this.itemID = itemID;
        }
        
        public int getCount() {
            return count;
        }
        public void setCount(int count) {
            this.count = count;
        }
	}
	
	
	/**
	 * Message used to update item collection quest status.
	 * Contains a map listing each item type and its item count.
	 * @author Andrew Harrison
	 *
	 */
	public static class QuestItemsListMessage extends SubjectMessage {

        public QuestItemsListMessage() {
            super(MSG_TYPE_QUEST_ITEMS_LIST);
        }

        public QuestItemsListMessage(OID playerOid, HashMap<Integer, Integer> itemList) {
            super(MSG_TYPE_QUEST_ITEMS_LIST, playerOid);
            setItemList(itemList);
        }
        
        public HashMap<Integer, Integer> getItemList() {
            return itemList;
        }

        public void setItemList(HashMap<Integer, Integer> itemList) {
            this.itemList = itemList;
        }
        HashMap<Integer, Integer> itemList;
        
        private static final long serialVersionUID = 1L;
    }
	
	/**
	 * Message used to request an inventory update message be sent so the other 
	 * parts of the server know what items the player has.
	 * @author Andrew Harrison
	 *
	 */
	public static class SendInventoryUpdateMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public SendInventoryUpdateMessage() {
            super();
        }
		public SendInventoryUpdateMessage(OID oid) {
			super(oid);
			setMsgType(MSG_TYPE_SEND_INV_UPDATE);
			Log.debug("ITEM: client SendInventoryUpdateMessage hit 2");
		}
	}

	/*
	 * Mail Messages
	 */
	
	/**
	 * Sends the GetMailMessage.
	 * @param oid
	 */
	public static void getMail(OID oid) {
		ExtensionMessage msg = new ExtensionMessage(oid);
		msg.setMsgType(MSG_TYPE_GET_MAIL);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client getMailMessage hit 1");
	}
	
	/**
	 * Sends the MailReadMessage.
	 * @param oid
	 * @param mailID
	 */
	public static void mailRead(OID oid, OID mailID) {
		ExtensionMessage msg = new ExtensionMessage(oid);
		msg.setMsgType(MSG_TYPE_MAIL_READ);
		msg.setProperty("mailID", mailID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client mailReadMessage hit 1");
	}
	
	/**
	 * Sends the TakeMailItemMessage.
	 * @param oid
	 * @param mailID
	 */
	public static void takeMailItem(OID oid, OID mailID, int itemPos) {
		ExtensionMessage msg = new ExtensionMessage(oid);
		msg.setMsgType(MSG_TYPE_MAIL_TAKE_ITEM);
		msg.setProperty("mailID", mailID);
		msg.setProperty("itemPos", itemPos);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client takeMailItemMessage hit 1");
	}
	
	/**
	 * Sends the DeleteMailMessage.
	 * @param oid
	 * @param mailID
	 */
	public static void deleteMail(OID oid, OID mailID) {
		ExtensionMessage msg = new ExtensionMessage(oid);
		msg.setMsgType(MSG_TYPE_DELETE_MAIL);
		msg.setProperty("mailID", mailID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client deleteMailMessage hit 1");
	}
	
	/**
	 * Sends the SendMailMessage.
	 * @param oid
	 * @param recipient
	 * @param subject
	 * @param message
	 * @param currencyType
	 * @param currencyAmount
	 * @param CoD
	 */
	public static void sendMail(OID oid, String recipient, String subject, String message, 
			int currencyType, long currencyAmount, boolean CoD) {
		//sendMailMessage msg = new sendMailMessage(oid, recipient, subject, message, itemOID);
		//Engine.getAgent().sendBroadcast(msg);
		Log.debug("MAIL: client sendMailMessage hit 1");
		
		ExtensionMessage sendMailMessage = new ExtensionMessage();
        sendMailMessage.setMsgType(AgisInventoryClient.MSG_TYPE_SEND_MAIL);
        sendMailMessage.setSubject(oid); 
        sendMailMessage.setProperty("recipient", recipient);
        sendMailMessage.setProperty("subject", subject); 
        sendMailMessage.setProperty("message", message);
        sendMailMessage.setProperty("numItems", 0);
        sendMailMessage.setProperty("numCurrencies", 1);
        sendMailMessage.setProperty("currencyType0", currencyType);
        sendMailMessage.setProperty("currencyAmount0", currencyAmount);
        sendMailMessage.setProperty("CoD", CoD);
        Engine.getAgent().sendBroadcast(sendMailMessage);
	}
	
	public static void sendAccountMail(OID oid, OID accountID, String subject, String message, 
			int currencyType, long currencyAmount, HashMap<Integer, Integer> items) {
		//sendMailMessage msg = new sendMailMessage(oid, recipient, subject, message, itemOID);
		//Engine.getAgent().sendBroadcast(msg);
		Log.debug("MAIL: client sendMailMessage hit 1");
		
		ExtensionMessage sendMailMessage = new ExtensionMessage();
        sendMailMessage.setMsgType(AgisInventoryClient.MSG_TYPE_SEND_MAIL);
        sendMailMessage.setSubject(oid); 
        sendMailMessage.setProperty("isAccountMail", true);
        sendMailMessage.setProperty("recipient", accountID);
        sendMailMessage.setProperty("subject", subject); 
        sendMailMessage.setProperty("message", message);
        sendMailMessage.setProperty("numCurrencies", 1);
        sendMailMessage.setProperty("currencyType0", currencyType);
        sendMailMessage.setProperty("currencyAmount0", currencyAmount);
        sendMailMessage.setProperty("items", items);
        
        Engine.getAgent().sendBroadcast(sendMailMessage);
	}

	public static void sendSystemMail(OID oid, OID playerID, String subject, String message, 
			int currencyType, long currencyAmount, HashMap<Integer, Integer> items) {
		//sendMailMessage msg = new sendMailMessage(oid, recipient, subject, message, itemOID);
		//Engine.getAgent().sendBroadcast(msg);
		Log.debug("MAIL: client sendMailMessage hit 1");
		
		ExtensionMessage sendMailMessage = new ExtensionMessage();
        sendMailMessage.setMsgType(AgisInventoryClient.MSG_TYPE_SEND_MAIL);
        sendMailMessage.setSubject(oid); 
        sendMailMessage.setProperty("isSystemMail", true);
        sendMailMessage.setProperty("recipient", playerID);
        sendMailMessage.setProperty("subject", subject); 
        sendMailMessage.setProperty("message", message);
        sendMailMessage.setProperty("numCurrencies", 1);
        sendMailMessage.setProperty("currencyType0", currencyType);
        sendMailMessage.setProperty("currencyAmount0", currencyAmount);
        sendMailMessage.setProperty("items", items);
        
        Engine.getAgent().sendBroadcast(sendMailMessage);
	}

	/**
	 * Message used to request the sending of a piece of mail from the sender
	 * to the specified recipient.
	 * @author Andrew Harrison
	 *
	 */
	/*public static class sendMailMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public sendMailMessage() {
            super();
        }
		public sendMailMessage(OID oid, String recipient, String subject, String message, OID itemOID) {
			super(oid);
			setMsgType(MSG_TYPE_SEND_MAIL);
			setProperty("recipient", recipient);
			setProperty("subject", subject);
			setProperty("message", message);
			setProperty("itemOID", itemOID);
			Log.debug("ITEM: client sendMailMessage hit 2");
		}
	}*/
	
	/**
	 * Sends the SendPurchaseMailMessage.
	 * @param oid
	 * @param itemID
	 */
	public static void sendPurchaseMail(OID oid, OID characterOid, boolean isAccountMail, int itemID, int count) {
		sendPurchaseMailMessage msg = new sendPurchaseMailMessage(oid, characterOid, isAccountMail, itemID, count);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client sendPurchaseMailMessage hit 1");
	}

	/**
	 * Message used to send a piece of mail containing an item a player purchased
	 * from the online store. 
	 * Not recommended for use at the current time.
	 * @author Andrew Harrison
	 *
	 */
	public static class sendPurchaseMailMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
        public sendPurchaseMailMessage() {
            super();
        }
		public sendPurchaseMailMessage(OID recipientOID, OID characterOid, boolean isAccountMail, int itemID, int count) {
			super();
			setMsgType(MSG_TYPE_SEND_PURCHASE_MAIL);
			setRecipientOid(recipientOID);
			setCharacterOID(characterOid);
			isAccountMail(isAccountMail);
			items = new HashMap<Integer, Integer>();
			items.put(itemID, count);
			Log.debug("ITEM: client sendPurchaseMailMessage hit 2");
		}
		
		public OID getRecipientOid() {
            return recipientOid;
        }
        public void setRecipientOid(OID recipientOid) {
            this.recipientOid = recipientOid;
        }
		OID recipientOid;
		
		public OID getCharacterOID() {
            return characterOid;
        }
        public void setCharacterOID(OID characterOid) {
            this.characterOid = characterOid;
        }
		OID characterOid;
		
		public boolean isAccountMail() {
            return isAccountMail;
        }
        public void isAccountMail(boolean isAccountMail) {
            this.isAccountMail = isAccountMail;
        }
		boolean isAccountMail = false;
		
		public HashMap<Integer, Integer> getItems() {
            return items;
        }
        public void setItems(HashMap<Integer, Integer> items) {
            this.items = items;
        }
		HashMap<Integer, Integer> items;
	}
	
	/**
	 * Sends the CheckCurrency Message. Sends back a boolean response indicating
	 * whether or not the player has enough of the specified currency.
	 * @param playerOid
	 * @param currencyID
	 * @param cost
	 * @return
	 */
	public static Boolean checkCurrency(OID playerOid, int currencyID, long cost){
		checkCurrencyMessage msg = new checkCurrencyMessage(playerOid, currencyID, cost);
    	Boolean hasEnoughCurrency = Engine.getAgent().sendRPCReturnBoolean(msg);
    	return hasEnoughCurrency;
    }
	/**
	 * Sends the CheckCurrency offline Message. Sends back a boolean response indicating
	 * whether or not the player has enough of the specified currency.
	 * @param playerOid
	 * @param currencyID
	 * @param cost
	 * @return
	 */
	public static Boolean checkCurrencyOffline(OID playerOid, int currencyID, long cost){
		checkCurrencyOfflineMessage msg = new checkCurrencyOfflineMessage(playerOid, currencyID, cost);
    	Boolean hasEnoughCurrency = Engine.getAgent().sendRPCReturnBoolean(msg);
    	return hasEnoughCurrency;
    }
	
	/**
	 * Message used to check if the player has enough of the specified currency
	 * @author Andrew Harrison
	 *
	 */
	public static class checkCurrencyMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public checkCurrencyMessage() {
            super();
        }
		public checkCurrencyMessage(OID oid, int currencyID, long count) {
			super(oid);
			setMsgType(MSG_TYPE_CHECK_CURRENCY);
			setCurrencyID(currencyID);
			setCount(count);
			Log.debug("ITEM: client checkCurrencyMessage hit 2");
		}
		
		int currencyID = -1;
		long count = 1;
		
		public int getCurrencyID() {
            return currencyID;
        }
        public void setCurrencyID(int currencyID) {
            this.currencyID = currencyID;
        }
        
        public long getCount() {
            return count;
        }
        public void setCount(long count) {
            this.count = count;
        }
	}
	
	public static class checkCurrencyOfflineMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
        public checkCurrencyOfflineMessage() {
            super();
        }
		public checkCurrencyOfflineMessage(OID oid, int currencyID, long count) {
			setPlayerOid(oid);
			setMsgType(MSG_TYPE_CHECK_CURRENCY_OFFLINE);
			setCurrencyID(currencyID);
			setCount(count);
			Log.debug("ITEM: client checkCurrencyOfflineMessage hit 2");
		}
		
		int currencyID = -1;
		long count = 1;
		OID playerOid;
		public OID getPlayerOid() {
			return playerOid;
		}
		
		public void setPlayerOid(OID playerOid) {
			this.playerOid = playerOid;
		}
		
		public int getCurrencyID() {
            return currencyID;
        }
        public void setCurrencyID(int currencyID) {
            this.currencyID = currencyID;
        }
        
        public long getCount() {
            return count;
        }
        public void setCount(long count) {
            this.count = count;
        }
	}
	
	/**
	 * Sends the AlterCurrencyMessage.
	 * @param oid
	 * @param currencyType
	 * @param delta
	 */
	public static void alterCurrency(OID oid, int currencyType, long delta) {
		alterCurrencyMessage msg = new alterCurrencyMessage(oid, currencyType, delta);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEMCLIENT: alterCurrencyMessage hit 1");
	}
	/**
	 * Sends the AlterCurrencyOfflineMessage.
	 * @param oid
	 * @param currencyType
	 * @param delta
	 */
	public static void alterCurrencyOffline(OID oid, int currencyType, long delta) {
		alterCurrencyOfflineMessage msg = new alterCurrencyOfflineMessage(oid, currencyType, delta);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEMCLIENT: alterCurrencyMessage hit 1");
	}

	/**
	 * Message used to alter the amount of the specified currency a player has.
	 * @author Andrew
	 *
	 */
	public static class alterCurrencyMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public alterCurrencyMessage() {
            super();
        }
		public alterCurrencyMessage(OID oid, int currencyType, long delta) {
			super(oid);
			setMsgType(MSG_TYPE_ALTER_CURRENCY);
			setCurrencyType(currencyType);
			setDelta(delta);
			Log.debug("ITEMCLIENT: alterCurrencyMessage hit 2");
		}
		
		public int getCurrencyType() { return currencyType; }
		public void setCurrencyType(int currencyType) { this.currencyType = currencyType; }
		
		public long getDelta() { return delta; }
		public void setDelta(long delta) { this.delta = delta; }
		
		protected int currencyType;
		protected long delta;
	}
	
	public static class alterCurrencyOfflineMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
        public alterCurrencyOfflineMessage() {
            super();
        }
		public alterCurrencyOfflineMessage(OID oid, int currencyType, long delta) {
			setPlayerOid(oid);
			setMsgType(MSG_TYPE_ALTER_CURRENCY_OFFLINE);
			setCurrencyType(currencyType);
			setDelta(delta);
			Log.debug("ITEMCLIENT: alterCurrencyMessage hit 2");
		}
		OID playerOid;
		public OID getPlayerOid() {
			return playerOid;
		}
		
		public void setPlayerOid(OID playerOid) {
			this.playerOid = playerOid;
		}
		public int getCurrencyType() { return currencyType; }
		public void setCurrencyType(int currencyType) { this.currencyType = currencyType; }
		
		public long getDelta() { return delta; }
		public void setDelta(long delta) { this.delta = delta; }
		
		protected int currencyType;
		protected long delta;
	}
	

	/**
	 * Sends the EquippedItemUsedMessage.
	 * @param oid
	 * @param useType
	 */
	public static void equippedItemUsed(OID oid, String useType) {
		//if(Log.loggingDebug)
		//	Log.dumpStack("equippedItemUsed oid="+oid+" useT="+useType);
		EquippedItemUsedMessage msg = new EquippedItemUsedMessage(oid, useType);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEMCLIENT: EquippedItemUsedMessage hit 1");
	}
	
	public static void equippedItemUsed(OID oid, String useType, String itemSubType) {
		//if(Log.loggingDebug)
		//	Log.dumpStack("equippedItemUsed oid="+oid+" useT="+useType);
		EquippedItemUsedMessage msg = new EquippedItemUsedMessage(oid, useType);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEMCLIENT: EquippedItemUsedMessage hit 1");
	}
	
	public static void equippedItemUsed(OID oid, String useType, int skillId) {
		if(Log.loggingDebug)
			Log.debug("equippedItemUsed oid="+oid+" useT="+useType+" skill="+skillId);
		EquippedItemUsedMessage msg = new EquippedItemUsedMessage(oid, useType);
		msg.setSkillId(skillId);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEMCLIENT: EquippedItemUsedMessage hit 1");
	}
	
	/**
	 * Message used to alter the amount of the specified currency a player has.
	 * @author Andrew
	 *
	 */
	public static class EquippedItemUsedMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public EquippedItemUsedMessage() {
            super();
        }
		public EquippedItemUsedMessage(OID oid, String useType) {
			super(oid);
			setMsgType(MSG_TYPE_EQUIPPED_ITEM_USED);
			setUseType(useType);
			Log.debug("ITEMCLIENT: EquippedItemUsedMessage hit 2");
		}
		
		public String getUseType() { return useType; }
		public void setUseType(String useType) { this.useType = useType; }
		
		public String getItemSubType() { return itemSubType; }
		public void setItemSubType(String itemSubType) { this.itemSubType = itemSubType; }
		
		public int getSkillId() { return skillId; }
		public void setSkillId(int skillId) { this.skillId = skillId; }
		
		//public OID getItemOid() { return itemOid; }
		//public void setItemOid(OID itemOid) { this.itemOid = itemOid; }
		
		protected String itemSubType="";
		protected String useType;
		
		protected int skillId=0;
		
		
		//protected OID itemOid;
	}


	public static HashMap<String, Serializable> DrawWeapon(OID oid, ArrayList<String> weaponReq) {
		DrawWeaponMessage msg = new DrawWeaponMessage(oid, weaponReq);
		HashMap<String, Serializable> response = (HashMap<String, Serializable>)Engine.getAgent().sendRPCReturnObject(msg);
		return response;
	}

	public static class DrawWeaponMessage extends SubjectMessage {
		private static final long serialVersionUID = 1L;
		public DrawWeaponMessage() {
			super(MSG_TYPE_DRAW_WEAPON);
		}
		public DrawWeaponMessage(OID oid, ArrayList<String> weaponReq) {
			super(MSG_TYPE_DRAW_WEAPON,oid);
			setWeaponReq(weaponReq);
			Log.debug("ITEMCLIENT: DrawWeaponMessage hit 2");
		}

		public ArrayList<String> getWeaponReq() {
			return weaponReq;
		}

		public void setWeaponReq(ArrayList<String> weaponReq) {
			this.weaponReq = weaponReq;
		}
		protected  ArrayList<String> weaponReq = new ArrayList<>();
	}

	/*
	 * Old Messages that are not currently used anymore.
	 */
	
	public static ArrayList<String> getAccountSkins(OID oid) {
		ExtensionMessage eMsg = new ExtensionMessage(MSG_TYPE_GET_SKINS, null, oid);
		return (ArrayList<String>) Engine.getAgent().sendRPCReturnObject(eMsg);
	}
	
	public static int getAccountItemCount(OID oid, int itemID) {
		ExtensionMessage eMsg = new ExtensionMessage(MSG_TYPE_GET_ACCOUNT_ITEM_COUNT, null, oid);
		eMsg.setProperty("itemID", itemID);
		return Engine.getAgent().sendRPCReturnInt(eMsg);
	}
	
	/**
	 * Sends the CheckComponentMessage checking if the player has enough of the given item types.
	 * @param id
	 * @param components
	 * @param componentCounts
	 * @return
	 */
	public static Boolean checkComponents(OID id, LinkedList<Integer> components, LinkedList<Integer> componentCounts) {
		CheckComponentMessage msg = new CheckComponentMessage(id, components, componentCounts);
		Boolean hasRequiredComponents = (Boolean) Engine.getAgent().sendRPCReturnObject(msg);
		return hasRequiredComponents;
	}
	/**
	 * Sends the CheckComponentMessage checking if the player has enough of the given item types.
	 * @param id
	 * @param ItemsCount
	 * @return
	 */
	public static Boolean checkComponents(OID id, HashMap<Integer, Integer> ItemsCount) {
		Log.debug("checkComponents: " + ItemsCount);
		CheckComponentMessage msg = new CheckComponentMessage(id, ItemsCount);
		Boolean hasRequiredComponents = (Boolean) Engine.getAgent().sendRPCReturnObject(msg);
		return hasRequiredComponents;
	}

	/**
	 * Sends the CheckComponentMessage checking if the player has the specific items given.
	 * @param id
	 * @param reqComponentIDs
	 * @param reqStackSizes
	 * @param components
	 * @param componentCounts
	 * @return
	 */
	public static Boolean checkSpecificComponents(OID id, LinkedList<Integer> reqComponentIDs, LinkedList<Integer> reqStackSizes, LinkedList<Long> components, LinkedList<Integer> componentCounts) {
		CheckComponentMessage msg = new CheckComponentMessage(id, reqComponentIDs, reqStackSizes, components, componentCounts);
		Boolean hasRequiredComponents = (Boolean) Engine.getAgent().sendRPCReturnObject(msg);
		return hasRequiredComponents;
	}
	
	
	
    public static class CheckComponentMessage extends SubjectMessage{
    	private static final long serialVersionUID = 1L;
    	public LinkedList<Integer> _reqComponents;
    	public LinkedList<Integer> _reqStackSizes;
    	public LinkedList<Long> _components;
    	public LinkedList<Integer> _componentCounts;
    	public OID _subject;
    	public HashMap<Integer,Integer> ItemsCount ;
    	public boolean gridSystem = false;
    	public boolean craftBook = false;
    	
    	public CheckComponentMessage() {
    		super(MSG_TYPE_RETURNBOOLEAN_CHECK_COMPONENTS);
    	}
    	
		public CheckComponentMessage(OID subject, LinkedList<Integer> componentIDs, LinkedList<Integer> stackSize, LinkedList<Long> components, LinkedList<Integer> componentCounts) {
			super(MSG_TYPE_RETURNBOOLEAN_CHECK_COMPONENTS, subject);
	 		_reqComponents = componentIDs;
    		_reqStackSizes = stackSize;
    		_components = components;
    		_componentCounts = componentCounts;
    		_subject = subject;
    		gridSystem = true;
    		craftBook = false;
    		}

		public CheckComponentMessage(OID subject, LinkedList<Integer> components, LinkedList<Integer> componentCounts) {
			super(MSG_TYPE_RETURNBOOLEAN_CHECK_COMPONENTS, subject);
    		_reqComponents = components;
    		_reqStackSizes = componentCounts;
    		_subject = subject;
    		gridSystem = false;
    		craftBook = false;
    		}
    	public CheckComponentMessage(OID subject, HashMap<Integer,Integer> itemsCount){
    		super(MSG_TYPE_RETURNBOOLEAN_CHECK_COMPONENTS, subject);
    	//	_reqComponents = components;
    	//	_reqStackSizes = componentCounts;
    		_subject = subject;
    		gridSystem = false;
    		craftBook = true;
    		ItemsCount = itemsCount;
    	}
    }
    
    /**
     * Called by lockpick effect and sends message to AgisInventoryPlugin
     * @param PlayerOid
     * @param targetOid
     * @param skillLevel
     */
    public static void pickLock(OID PlayerOid, OID targetOid, int skillLevel) {
    	LockpickMessage msg = new LockpickMessage(PlayerOid, targetOid, skillLevel);
    	Engine.getAgent().sendBroadcast(msg);
    }
    
    /**
     * The lockpick message that sends holds the player's skill.
     * @author seanbrown
     *
     */
    public static class LockpickMessage extends SubjectMessage {
    	
    	public LockpickMessage() {
    		super();
    	}
    	
    	public LockpickMessage(OID oid, int skillLevel) {
    		super(MSG_TYPE_LOCKPICK, oid);
    		setSkillLevel(skillLevel);
    	}
    	
    	public LockpickMessage(OID pOid, OID tOid, int skillLevel) {
    		super(MSG_TYPE_LOCKPICK, pOid);
    		setSkillLevel(skillLevel);
    		setTargetOid(tOid);
    	}
    	
    	public void setSkillLevel(int skill) {skillLevel = skill;}
    	public int getSkillLevel() {return skillLevel;}
    	
    	int skillLevel;
    	
    	public void setTargetOid(OID targetOid) {this.targetOid = targetOid;}
    	public OID getTargetOid() {return targetOid;}
    	
    	OID targetOid;
    }
    
    public static OID createStorage(OID oid, String storageName, int storageSize, boolean playerStorage) {
    	CreateStorageMessage msg = new CreateStorageMessage(oid, storageName, storageSize, playerStorage);
    	OID storageOid = (OID) Engine.getAgent().sendRPCReturnOID(msg);
		Log.debug("ITEMCLIENT: CreateStorageMessage hit 1");
		return storageOid;
	}
    
    public static OID createStorage(OID oid, String storageName, int storageSize, int lockLimit, boolean playerStorage) {
    	CreateStorageMessage msg = new CreateStorageMessage(oid, storageName, storageSize, lockLimit, playerStorage);
    	OID storageOid = (OID) Engine.getAgent().sendRPCReturnOID(msg);
		Log.debug("ITEMCLIENT: CreateStorageMessage hit 1");
		return storageOid;
	}

    public static class CreateStorageMessage extends SubjectMessage {
    	public CreateStorageMessage() {
            super(MSG_TYPE_CREATE_STORAGE);
        }

        public CreateStorageMessage(OID playerOid, String storageName, int storageSize, boolean playerStorage) {
            super(MSG_TYPE_CREATE_STORAGE, playerOid);
            setStorageName(storageName);
            setStorageSize(storageSize);
            isPlayerStorage(playerStorage);
            setLockLimit(0);
        }
        
        public CreateStorageMessage(OID playerOid, String storageName, int storageSize, int lockLimit, boolean playerStorage) {
            super(MSG_TYPE_CREATE_STORAGE, playerOid);
            setStorageName(storageName);
            setStorageSize(storageSize);
            isPlayerStorage(playerStorage);
            setLockLimit(lockLimit);
        }
        
        public String getStorageName() {
            return storageName;
        }
        public void setStorageName(String storageName) {
            this.storageName = storageName;
        }
        String storageName;
        
        public int getStorageSize() {
            return storageSize;
        }
        public void setStorageSize(int storageSize) {
            this.storageSize = storageSize;
        }
        int storageSize;
        
        public boolean isPlayerStorage() {
            return playerStorage;
        }
        public void isPlayerStorage(boolean playerStorage) {
            this.playerStorage = playerStorage;
        }
        boolean playerStorage;
        
        public int getLockLimit() {
			return lockLimit;
		}
		public void setLockLimit(int lockLimit) {
			this.lockLimit = lockLimit;
		}
		int lockLimit;
        
        private static final long serialVersionUID = 1L;
    }
    
    public static void updateStorageSize(OID oid, OID storageOid, int size) {
		UpdateStorageSizeMessage msg = new UpdateStorageSizeMessage(oid,  storageOid,  size);
	Engine.getAgent().sendBroadcast(msg);
	Log.debug("ITEMCLIENT: EquippedItemUsedMessage hit 1");
}

public static class UpdateStorageSizeMessage extends SubjectMessage {
	public UpdateStorageSizeMessage() {
        super(MSG_TYPE_UPDATE_STORAGE);
    }

    public UpdateStorageSizeMessage(OID playerOid, OID storageOid, int size) {
        super(MSG_TYPE_UPDATE_STORAGE, playerOid);
        setStorageOid(storageOid);
        setSize(size);
    }
    
    public OID getStorageOid() {
        return storageOid;
    }
    public void setStorageOid(OID storageOid) {
        this.storageOid = storageOid;
    }
    OID storageOid;
    
    public int getSize() {
    	return size;
    }
    
    public void setSize(int size) {
    	this.size = size;
    }
    int size;
    private static final long serialVersionUID = 1L;
}
    
    
    /**
     * Sends open storage message by name
     * @param oid
     * @param storageName
     */
    public static void openStorage(OID oid, String storageName) {
    	OpenStorageMessage msg = new OpenStorageMessage(oid, storageName);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEMCLIENT: OpenStorageMessage hit 1");
	}
    
    /**
     * Sends open storage message by id
     * @param oid
     * @param storageOid
     */
    public static void openNonPlayerStorage(OID oid, OID storageOid) {
    	OpenStorageMessage msg = new OpenStorageMessage(oid, storageOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEMCLIENT: OpenStorageMessage hit 1");
	}
    
    public static void openNonPlayerStorage(OID oid, OID storageOid, int lockLimit) {
    	OpenStorageMessage msg = new OpenStorageMessage(oid, storageOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEMCLIENT: OpenStorageMessage hit 1");
	}

    public static class OpenStorageMessage extends SubjectMessage {
    	public OpenStorageMessage() {
            super(MSG_TYPE_OPEN_STORAGE);
        }
    	
    	// opening bank
        public OpenStorageMessage(OID playerOid, String storageName) {
            super(MSG_TYPE_OPEN_STORAGE, playerOid);
            setStorageName(storageName);
            isPlayerStorage(true);
        }
        
        // opening chest
        public OpenStorageMessage(OID playerOid, OID storageOid) {
            super(MSG_TYPE_OPEN_STORAGE, playerOid);
            setStorageOid(storageOid);
            isPlayerStorage(false);
            setLockLimit(0);
        }
        
        public OpenStorageMessage(OID playerOid, OID storageOid, int lockLimit) {
            super(MSG_TYPE_OPEN_STORAGE, playerOid);
            setStorageOid(storageOid);
            isPlayerStorage(false);
            setLockLimit(lockLimit);
        }
        
        public String getStorageName() {
            return storageName;
        }
        public void setStorageName(String storageName) {
            this.storageName = storageName;
        }
        String storageName;
        
        public OID getStorageOid() {
            return storageOid;
        }
        public void setStorageOid(OID storageOid) {
            this.storageOid = storageOid;
        }
        OID storageOid;
        
        public boolean isPlayerStorage() {
            return playerStorage;
        }
        public void isPlayerStorage(boolean playerStorage) {
            this.playerStorage = playerStorage;
        }
        boolean playerStorage;
        
        public int getLockLimit() {
			return lockLimit;
		}
		public void setLockLimit(int lockLimit) {
			this.lockLimit = lockLimit;
		}
		int lockLimit;
        
        private static final long serialVersionUID = 1L;
    }
    
    public static void sendStorageItemsAsMail(OID oid, OID storageOid) {
    	SendStorageItemsAsMailMessage msg = new SendStorageItemsAsMailMessage(oid, storageOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEMCLIENT: OpenStorageMessage hit 1");
	}

    public static class SendStorageItemsAsMailMessage extends SubjectMessage {
    	public SendStorageItemsAsMailMessage() {
            super(MSG_TYPE_SEND_STORAGE_ITEMS_AS_MAIL);
        }
        
        public SendStorageItemsAsMailMessage(OID playerOid, OID storageOid) {
            super(MSG_TYPE_SEND_STORAGE_ITEMS_AS_MAIL, playerOid);
            setStorageOid(storageOid);
        }
        
        public OID getStorageOid() {
            return storageOid;
        }
        public void setStorageOid(OID storageOid) {
            this.storageOid = storageOid;
        }
        OID storageOid;
        
        private static final long serialVersionUID = 1L;
    }
    
    /*public static void closeStorage(OID oid, String storageName) {
    	CloseStorageMessage msg = new CloseStorageMessage(oid, storageName);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEMCLIENT: EquippedItemUsedMessage hit 1");
	}

    public static class CloseStorageMessage extends SubjectMessage {
    	public CloseStorageMessage() {
            super(MSG_TYPE_CLOSE_STORAGE);
        }

        public CloseStorageMessage(OID playerOid, String storageName) {
            super(MSG_TYPE_CLOSE_STORAGE, playerOid);
            setStorageName(storageName);
        }
        
        public String getStorageName() {
            return storageName;
        }
        public void setStorageName(String storageName) {
            this.storageName = storageName;
        }
        String storageName;
        
        private static final long serialVersionUID = 1L;
    }*/
    
    public static LinkedList<OID> getStorageContents(OID oid, String storageName) {
    	GetStorageContentsMessage msg = new GetStorageContentsMessage(oid, storageName);
    	LinkedList<OID> contents = (LinkedList) Engine.getAgent().sendRPCReturnObject(msg);
    	return contents;
	}
    
    public static LinkedList<OID> getStorageContents(OID oid, OID storageOid) {
    	GetStorageContentsMessage msg = new GetStorageContentsMessage(oid, storageOid);
    	LinkedList<OID> contents = (LinkedList) Engine.getAgent().sendRPCReturnObject(msg);
    	return contents;
	}

    public static class GetStorageContentsMessage extends SubjectMessage {
    	public GetStorageContentsMessage() {
            super(MSG_TYPE_GET_STORAGE_CONTENTS);
        }

        public GetStorageContentsMessage(OID playerOid, String storageName) {
            super(MSG_TYPE_GET_STORAGE_CONTENTS, playerOid);
            setStorageName(storageName);
            isPlayerStorage(true);
        }
        
        public GetStorageContentsMessage(OID playerOid, OID storageOid) {
            super(MSG_TYPE_GET_STORAGE_CONTENTS, playerOid);
            setStorageOid(storageOid);
            isPlayerStorage(false);
        }
        
        public String getStorageName() {
            return storageName;
        }
        public void setStorageName(String storageName) {
            this.storageName = storageName;
        }
        String storageName;
        
        public OID getStorageOid() {
            return storageOid;
        }
        public void setStorageOid(OID storageOid) {
            this.storageOid = storageOid;
        }
        OID storageOid;
        
        public boolean isPlayerStorage() {
            return playerStorage;
        }
        public void isPlayerStorage(boolean playerStorage) {
            this.playerStorage = playerStorage;
        }
        boolean playerStorage;
        
        private static final long serialVersionUID = 1L;
    }
    
    public static boolean doesInventoryHaveSufficientSpace(OID oid, HashMap<Integer, Integer> itemsToGenerate) {
    	DoesInventoryHasSufficientSpaceMessage msg = new DoesInventoryHasSufficientSpaceMessage(oid, itemsToGenerate);
		//Engine.getAgent().sendBroadcast(msg);
		boolean hasSpace = Engine.getAgent().sendRPCReturnBoolean(msg);
		Log.debug("ITEM: client doesInventoryHaveSufficientSpace hit 1");
		return hasSpace;
	}

	/**
	 * Message used to request the generation of an item based on the templateID specified and then to add the item
	 * to the players inventory.
	 * @author Andrew Harrison
	 *
	 */
	public static class DoesInventoryHasSufficientSpaceMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public DoesInventoryHasSufficientSpaceMessage() {
            super();
        }
		public DoesInventoryHasSufficientSpaceMessage(OID oid, int templateID, int count) {
			super(oid);
			setMsgType(MSG_TYPE_DOES_INVENTORY_HAS_SUFFICIENT_SPACE);
			itemsToGenerate = new HashMap<Integer, Integer>();
			itemsToGenerate.put(templateID, count);
			
			Log.debug("ITEM: client doesInventoryHaveSufficientSpace hit 2");
		}
		public DoesInventoryHasSufficientSpaceMessage(OID oid, HashMap<Integer, Integer> itemsToGenerate) {
			super(oid);
			setMsgType(MSG_TYPE_DOES_INVENTORY_HAS_SUFFICIENT_SPACE);
			setItemsToGenerate(itemsToGenerate);
			Log.debug("ITEM: client doesInventoryHaveSufficientSpace hit 2");
		}
		
		public HashMap<Integer, Integer> getItemsToGenerate() {
            return itemsToGenerate;
        }
        public void setItemsToGenerate(HashMap<Integer, Integer> itemsToGenerate) {
            this.itemsToGenerate = itemsToGenerate;
        }
		HashMap<Integer, Integer> itemsToGenerate;
	}

	public static void generateGroundLoot(OID oid, HashMap<Integer, Integer> itemsToGenerate) {
		GenerateGroundLootMessage msg = new GenerateGroundLootMessage(oid, itemsToGenerate);
		//Engine.getAgent().sendBroadcast(msg);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ITEM: client doesInventoryHaveSufficientSpace hit 1");
	}

	/**
	 * Message used to request the generation of an loot based on the templateID specified and then to show on terrain
	 */
	public static class GenerateGroundLootMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
		public GenerateGroundLootMessage() {
			super();
		}
		public GenerateGroundLootMessage(OID oid, int templateID, int count) {
			super(oid);
			setMsgType(MSG_TYPE_INVENTORY_GENERATE_GROUND_LOOT);
			itemsToGenerate = new HashMap<Integer, Integer>();
			itemsToGenerate.put(templateID, count);

			Log.debug("ITEM: client doesInventoryHaveSufficientSpace hit 2");
		}
		public GenerateGroundLootMessage(OID oid, HashMap<Integer, Integer> itemsToGenerate) {
			super(oid);
			setMsgType(MSG_TYPE_INVENTORY_GENERATE_GROUND_LOOT);
			setItemsToGenerate(itemsToGenerate);
			Log.debug("ITEM: client doesInventoryHaveSufficientSpace hit 2");
		}

		public HashMap<Integer, Integer> getItemsToGenerate() {
			return itemsToGenerate;
		}
		public void setItemsToGenerate(HashMap<Integer, Integer> itemsToGenerate) {
			this.itemsToGenerate = itemsToGenerate;
		}
		HashMap<Integer, Integer> itemsToGenerate;
	}

	/**
	 * Send to Player Items in Bank with id
	 * 
	 * @param oid
	 * @param BankId
	 */

	public static void SendBankInventory(OID oid, int BankId) {
		SendBankInventoryMessage msg = new SendBankInventoryMessage(oid, BankId);
		Engine.getAgent().sendBroadcast(msg);
	}

	public static class SendBankInventoryMessage extends SubjectMessage {
		public SendBankInventoryMessage() {
			super(MSG_TYPE_GET_BANK_ITEMS);
		}

		public SendBankInventoryMessage(OID playerOid, int BankId) {
			super(MSG_TYPE_GET_BANK_ITEMS, playerOid);
			setBankId(BankId);

		}

		public int getBankId() {
			return bankId;
		}

		public void setBankId(int bankId) {
			this.bankId = bankId;
		}

		int bankId;

		private static final long serialVersionUID = 1L;
	}
	
	public static void StartPlayerShop(OID playerOid, String model, String tag, int numShop, int slots, boolean destroyOnLogOut, int mobTemplate, int shopTimeOut) {
		StartPlayerShopMessage msg = new StartPlayerShopMessage(playerOid, model, tag, numShop, slots, destroyOnLogOut,mobTemplate,shopTimeOut);
		Engine.getAgent().sendBroadcast(msg);

	}

	public static class StartPlayerShopMessage extends SubjectMessage {
		public StartPlayerShopMessage() {
			super(MSG_TYPE_START_PLAYER_SHOP);
		}

		public StartPlayerShopMessage(OID playerOid, String model, String tag, int numShop, int slots, boolean destroyOnLogOut, int mobTemplate, int shopTimeOut) {
			super(MSG_TYPE_START_PLAYER_SHOP, playerOid);
			setNumShop(numShop);
			setTag(tag);
			setModel(model);
			setSlots(slots);
			setDestroyOnLogOut(destroyOnLogOut);
			setMobTemplate(mobTemplate);
			setShopTimeOut(shopTimeOut);
		}

		public void setShopTimeOut(int v) {
			shopTimeOut=v;
		}
		public int getShopTimeOut() {
			return shopTimeOut;
		}
		int shopTimeOut = 0;
		public int getNumShop() {
			return numShop;
		}

		public void setNumShop(int num) {
			this.numShop = num;
		}

		int numShop;

		public String getModel() {
			return model;
		}

		public void setModel(String model) {
			this.model = model;
		}

		String model;

		public int getSlots() {
			return slots;
		}

		public void setSlots(int slots) {
			this.slots = slots;
		}

		int slots;

		public int getMobTemplate() {
			return mobTemplate;
		}

		public void setMobTemplate(int mobTemplate) {
			this.mobTemplate = mobTemplate;
		}

		int mobTemplate;

		public String getTag() {
			return tag;
		}

		public void setTag(String tag) {
			this.tag = tag;
		}

		String tag;

		public boolean getDestroyOnLogOut() {
			return destroyOnLogOut;
		}

		public void setDestroyOnLogOut(boolean destroyOnLogOut) {
			this.destroyOnLogOut = destroyOnLogOut;
		}

		boolean destroyOnLogOut = false;

		public boolean getShopOnPlayer() {
			return shopOnPlayer;
		}

		public void setShopOnPlayer(boolean shopOnPlayer) {
			this.shopOnPlayer = shopOnPlayer;
		}

		boolean shopOnPlayer = false;
		private static final long serialVersionUID = 1L;
	}

	public static void DeleteShop(OID shopOid) {
		DeletePlayerShopMessage msg = new DeletePlayerShopMessage(shopOid);
		msg.setShopOid(shopOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("INSTANCE: CreateShopSpawn hit 2");
	}

	public static class DeletePlayerShopMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;

		public DeletePlayerShopMessage() {
			super();
		}

		public DeletePlayerShopMessage(OID shopOid) {
			setMsgType(MSG_TYPE_DELETE_PLAYER_SHOP);
			setShopOid(shopOid);
			Log.debug("INSTANCE: DespawnPlayerShop hit 1");
		}

		public void setShopOid(OID shopOid) {
			this.shopOid = shopOid;
		}

		public OID getShopOid() {
			return this.shopOid;
		}

		OID shopOid;

	}
	
	
	//Sets
	public static final MessageType MSG_TYPE_DELETE_PLAYER_SHOP  = MessageType.intern("inventory.DELETE_PLAYER_SHOP");
	public static final MessageType MSG_TYPE_START_PLAYER_SHOP  = MessageType.intern("inventory.START_PLAYER_SHOP");

	public static final MessageType MSG_TYPE_GET_PET_INVENTORY  = MessageType.intern("inventory.GET_PET_INVENTORY");
	public static final MessageType MSG_TYPE_DRAW_WEAPON = MessageType.intern("ao.DRAW_WEAPON");
	public static final MessageType MSG_TYPE_DRAW_WEAPON_CLIENT = MessageType.intern("inventory.DRAW_WEAPON");
	public static final MessageType MSG_TYPE_SWITCH_WEAPON = MessageType.intern("inventory.SWITCH_WEAPON");

	public static final MessageType MSG_TYPE_GET_SETS_EQUIPED  = MessageType.intern("inventory.GET_SETS_EQUIPED");
	public static final MessageType MSG_TYPE_GET_ITEMS_WITH_PARAM = MessageType.intern("inventory.GET_ITEMS_WITH_PARAM");
	public static final MessageType MSG_TYPE_ENCHANTING_DETAIL = MessageType.intern("inventory.ENCHANTING_DETAIL");
	public static final MessageType MSG_TYPE_ENCHANT = MessageType.intern("inventory.ENCHANT");
	public static final MessageType MSG_TYPE_SET_ITEM_PROPERTY = MessageType.intern("inventory.SET_ITEM_PROPERTY");
	public static final MessageType MSG_TYPE_SET_ITEM_COUNT = MessageType.intern("inventory.SET_ITEM_COUNT");
	public static final MessageType MSG_TYPE_GENERATE_ITEM_AS = MessageType.intern("inventory.GENERATE_ITEM_AS");
	public static final MessageType MSG_TYPE_GENERATE_ITEM_RESPONSE_OID = MessageType.intern("inventory.GENERATE_ITEM_RESPONSE_OID");
	public static final MessageType MSG_TYPE_GET_ITEM_PROPERTY = MessageType.intern("inventory.GET_ITEM_PROPERTY");
	public static final MessageType MSG_TYPE_GET_ITEM = MessageType.intern("inventory.GET_ITEM");
	public static final MessageType MSG_TYPE_AGIS_INV_FIND = MessageType.intern("ao.AGIS_INV_FIND");
    public static final MessageType MSG_TYPE_INSERT_TO_SOCKET = MessageType.intern("inventory.INSERT_TO_SOCKET");
    public static final MessageType MSG_TYPE_SOCKETING_DETAIL = MessageType.intern("inventory.SOCKETING_DETAIL");
    public static final MessageType MSG_TYPE_SOCKETING_RESET = MessageType.intern("inventory.SOCKETING_RESET");
    public static final MessageType MSG_TYPE_SOCKETING_RESET_DETAIL = MessageType.intern("inventory.SOCKETING_RESET_DETAIL");
    public static final MessageType MSG_TYPE_TRADE_START_REQ = MessageType.intern("ao.TRADE_START_REQ");
    public static final String MSG_TYPE_TRADE_START_REQ_PARTNER = "ao.TRADE_START_REQ_PARTNER";
    public static final MessageType MSG_TYPE_TRADE_START_REQ_RESPONSE = MessageType.intern("ao.TRADE_START_REQ_RESPONSE");
    public static final MessageType MSG_TYPE_TRADE_START = MessageType.intern("ao.TRADE_START");
    public static final MessageType MSG_TYPE_TRADE_COMPLETE = MessageType.intern("ao.TRADE_COMPLETE");
    public static final MessageType MSG_TYPE_TRADE_OFFER_REQ = MessageType.intern("ao.TRADE_OFFER_REQ");
    public static final MessageType MSG_TYPE_TRADE_OFFER_UPDATE = MessageType.intern("ao.TRADE_OFFER_UPDATE");
    public static final MessageType MSG_TYPE_ITEM_ACQUIRE_STATUS_CHANGE = MessageType.intern("ao.ITEM_ACQUIRE_STATUS_CHANGE");
    public static final MessageType MSG_TYPE_ITEM_EQUIP_STATUS_CHANGE = MessageType.intern("ao.ITEM_EQUIP_STATUS_CHANGE");
    public static final MessageType MSG_TYPE_REQ_OPEN_MOB = MessageType.intern("inventory.REQ_OPEN_MOB");
    public static final MessageType MSG_TYPE_REMOVE_SPECIFIC_ITEM = MessageType.intern("inventory.REMOVE_SPECIFIC_ITEM");
    public static final MessageType MSG_TYPE_REMOVE_GENERIC_ITEM = MessageType.intern("inventory.REMOVE_GENERIC_ITEM");
    public static final MessageType MSG_TYPE_COUNT_GENERIC_ITEM = MessageType.intern("inventory.COUNT_GENERIC_ITEM");
      public static final MessageType MSG_TYPE_GET_SPECIFIC_ITEM_DATA = MessageType.intern("inventory.GET_SPECIFIC_ITEM_DATA");
    public static final MessageType MSG_TYPE_GET_GENERIC_ITEM_DATA = MessageType.intern("inventory.GET_GENERIC_ITEM_DATA");
    public static final MessageType MSG_TYPE_GENERATE_ITEM = MessageType.intern("inventory.GENERATE_ITEM");
    public static final MessageType MSG_TYPE_GENERATE_ITEM_NEEDS_RESPONSE = MessageType.intern("inventory.GENERATE_ITEM_NEEDS_RESPONSE");
    public static final MessageType MSG_TYPE_PLACE_BAG = MessageType.intern("inventory.PLACE_BAG");
    public static final MessageType MSG_TYPE_MOVE_BAG = MessageType.intern("inventory.MOVE_BAG");
    public static final MessageType MSG_TYPE_REMOVE_BAG = MessageType.intern("inventory.REMOVE_BAG");
    public static final MessageType MSG_TYPE_MOVE_ITEM = MessageType.intern("inventory.MOVE_ITEM");
    public static final MessageType MSG_TYPE_LOOT_ITEM = MessageType.intern("inventory.LOOT_ITEM");
    public static final MessageType MSG_TYPE_LOOT_ROLL = MessageType.intern("inventory.LOOT_ROLL");
    public static final MessageType MSG_TYPE_LOOT_ALL = MessageType.intern("inventory.LOOT_ALL");
    public static final MessageType MSG_TYPE_LOOT_ALL_F = MessageType.intern("inventory.LOOT_ALL_F");
	public static final MessageType MSG_TYPE_LOOT_GROUND_ITEM = MessageType.intern("inventory.LOOT_GROUND");
	public static final MessageType MSG_TYPE_DROP_GROUND_ITEM = MessageType.intern("inventory.DROP_ITEM_GROUND");
	public static final MessageType MSG_TYPE_LOOT_CHEST = MessageType.intern("inventory.LOOT_CHEST");
    public static final MessageType MSG_TYPE_GENERATE_LOOT = MessageType.intern("inventory.GENERATE_LOOT");
    public static final MessageType MSG_TYPE_GENERATE_LOOT_FROM_LOOT_TABLE= MessageType.intern("inventory.GENERATE_LOOT_FROM_LOOT_TABLE");
    public static final MessageType MSG_TYPE_GENERATE_LOOT_EFFECT = MessageType.intern("inventory.GENERATE_LOOT_EFFECT");
     public static final MessageType MSG_TYPE_GET_LOOT_LIST = MessageType.intern("inventory.GET_LOOT_LIST");
    public static final MessageType MSG_TYPE_GET_MERCHANT_LIST = MessageType.intern("inventory.GET_MERCHANT_LIST");
    public static final MessageType MSG_TYPE_PURCHASE_ITEM_FROM_MERCHANT = MessageType.intern("inventory.MSG_TYPE_PURCHASE_ITEM_FROM_MERCHANT");
    public static final MessageType MSG_TYPE_PURCHASE_ITEM = MessageType.intern("inventory.PURCHASE_ITEM");
    public static final MessageType MSG_TYPE_SELL_ITEM = MessageType.intern("inventory.SELL_ITEM");
    public static final MessageType MSG_TYPE_PICKUP_ITEM = MessageType.intern("inventory.PICKUP_ITEM");
    public static final MessageType MSG_TYPE_QUEST_ITEMS_LIST = MessageType.intern("inventory.QUEST_ITEMS_LIST");
    public static final MessageType MSG_TYPE_SEND_INV_UPDATE = MessageType.intern("inventory.SEND_INV_UPDATE");
    public static final MessageType MSG_TYPE_CHECK_CURRENCY = MessageType.intern("inventory.CHECK_CURRENCY");
    public static final MessageType MSG_TYPE_ALTER_CURRENCY = MessageType.intern("inventory.ALTER_CURRENCY");
    public static final MessageType MSG_TYPE_CHECK_CURRENCY_OFFLINE = MessageType.intern("inventory.CHECK_CURRENCY_OFFLINE");
    public static final MessageType MSG_TYPE_ALTER_CURRENCY_OFFLINE = MessageType.intern("inventory.ALTER_CURRENCY_OFFLINE");
    public static final MessageType MSG_TYPE_EDIT_LOCK = MessageType.intern("inventory.EDIT_LOCK");
    public static final MessageType MSG_TYPE_GET_SKINS = MessageType.intern("ao.GET_SKINS");
    public static final MessageType MSG_TYPE_PURCHASE_SKIN = MessageType.intern("ao.PURCHASE_SKIN");
    public static final MessageType MSG_TYPE_SET_WEAPON = MessageType.intern("ao.SET_WEAPON");
    public static final MessageType MSG_TYPE_SET_SKIN_COLOUR = MessageType.intern("ao.SET_SKIN_COLOUR");
    public static final MessageType MSG_TYPE_GET_ACCOUNT_ITEM_COUNT = MessageType.intern("ao.GET_ACCOUNT_ITEM_COUNT");
    public static final MessageType MSG_TYPE_ALTER_ITEM_COUNT = MessageType.intern("ao.ALTER_ITEM_COUNT");
    public static final MessageType MSG_TYPE_USE_ACCOUNT_ITEM = MessageType.intern("ao.USE_ACCOUNT_ITEM");
    public static final MessageType MSG_TYPE_ITEM_ACTIVATED = MessageType.intern("ao.ITEM_ACTIVATED");
    public static final MessageType MSG_TYPE_RETURNBOOLEAN_CHECK_COMPONENTS = MessageType.intern("ao.CHECK_COMPONENT");
    public static final MessageType MSG_TYPE_SET_AMMO = MessageType.intern("ao.SET_AMMO");
    public static final MessageType MSG_TYPE_EQUIPPED_ITEM_USED = MessageType.intern("ao.EQUIPPED_ITEM_USED");
    public static final MessageType MSG_TYPE_REPAIR_ITEMS = MessageType.intern("ao.REPAIR_ITEMS");
    public static final MessageType MSG_TYPE_GET_BANK_ITEMS = MessageType.intern("ao.GET_BANK_ITEMS");
    public static final MessageType MSG_TYPE_STORE_ITEM_IN_BANK = MessageType.intern("ao.STORE_ITEM_IN_BANK");
    public static final MessageType MSG_TYPE_RETRIEVE_ITEM_FROM_BANK = MessageType.intern("ao.RETRIEVE_ITEM_FROM_BANK");
    public static final MessageType MSG_TYPE_CREATE_STORAGE = MessageType.intern("ao.CREATE_STORAGE");
    public static final MessageType MSG_TYPE_OPEN_STORAGE = MessageType.intern("ao.OPEN_STORAGE");
    public static final MessageType MSG_TYPE_SEND_STORAGE_ITEMS_AS_MAIL = MessageType.intern("ao.SEND_STORAGE_ITEMS_AS_MAIL");
    public static final MessageType MSG_TYPE_UPDATE_STORAGE = MessageType.intern("ao.UPDATE_STORAGE");
    //public static final MessageType MSG_TYPE_CLOSE_STORAGE = MessageType.intern("ao.CLOSE_STORAGE");
     public static final MessageType MSG_TYPE_GET_STORAGE_CONTENTS = MessageType.intern("ao.GET_STORAGE_CONTENTS");
    public static final MessageType MSG_TYPE_EQUIP_ITEM_IN_SLOT = MessageType.intern("ao.EQUIP_ITEM_IN_SLOT");
	public static final MessageType MSG_TYPE_EQUIP_ITEM_IN_PET_SLOT = MessageType.intern("ao.EQUIP_ITEM_IN_PET_SLOT");
	public static final MessageType MSG_TYPE_DOES_INVENTORY_HAS_SUFFICIENT_SPACE = MessageType.intern("ao.DOES_INVENTORY_HAS_SUFFICIENT_SPACE");
	public static final MessageType MSG_TYPE_INVENTORY_GENERATE_GROUND_LOOT = MessageType.intern("ao.INVENTORY_GENERATE_GROUND_LOOT");

    public static final MessageType MSG_TYPE_ACTIVATE_CHEST = MessageType.intern("inventory.ACTIVATE_CHEST");
    public static final MessageType MSG_TYPE_GET_EQUIP_ITEMS = MessageType.intern("inventory.GET_EQUIP_ITEMS");
    public static final MessageType MSG_TYPE_EQUIP_ITEM = MessageType.intern("inventory.EQUIP_ITEM");
    public static final MessageType MSG_TYPE_UNEQUIP_ITEM = MessageType.intern("inventory.UNEQUIP_ITEM");
        // Mail Messages
    public static final MessageType MSG_TYPE_GET_MAIL = MessageType.intern("inventory.GET_MAIL");
    public static final MessageType MSG_TYPE_MAIL_READ = MessageType.intern("inventory.MAIL_READ");
    public static final MessageType MSG_TYPE_MAIL_TAKE_ITEM = MessageType.intern("inventory.MAIL_TAKE_ITEM");
    public static final MessageType MSG_TYPE_RETURN_MAIL = MessageType.intern("inventory.RETURN_MAIL");
    public static final MessageType MSG_TYPE_DELETE_MAIL = MessageType.intern("inventory.DELETE_MAIL");
    public static final MessageType MSG_TYPE_SEND_MAIL = MessageType.intern("inventory.SEND_MAIL");
    public static final MessageType MSG_TYPE_SEND_PURCHASE_MAIL = MessageType.intern("inventory.SEND_PURCHASE_MAIL");
    // Item Store
    public static final MessageType MSG_TYPE_GET_ITEM_STORE = MessageType.intern("inventory.GET_ITEM_STORE");
    public static final MessageType MSG_TYPE_PURCHASE_STORE_ITEM = MessageType.intern("inventory.PURCHASE_STORE_ITEM");
    
    public static final MessageType MSG_TYPE_GET_CURR_ICON = MessageType.intern("ao.GET_CURR_ICON");
    public static final MessageType MSG_TYPE_GET_SKILL_ICON = MessageType.intern("ao.GET_SKILL_ICON");
    public static final MessageType MSG_TYPE_GET_ABILITY_ICON = MessageType.intern("ao.GET_ABILITY_ICON");
    public static final MessageType MSG_TYPE_GET_EFFECT_ICON = MessageType.intern("ao.GET_EFFECT_ICON");
    public static final MessageType MSG_TYPE_GET_BUILD_OBJ_ICON = MessageType.intern("ao.GET_BUILD_OBJ_ICON");
    public static final MessageType MSG_TYPE_GET_CRAFTING_RECIPE_ICON = MessageType.intern("ao.GET_CRAFTING_RECIPE_ICON");
     
    public static final MessageType MSG_TYPE_START_SHOP = MessageType.intern("ao.START_SHOP");
    public static final MessageType MSG_TYPE_STOP_SHOP = MessageType.intern("ao.STOP_SHOP");
    public static final MessageType MSG_TYPE_CANCEL_SHOP= MessageType.intern("ao.CANCEL_SHOP");
    public static final MessageType MSG_TYPE_OPEN_PLAYER_SHOP = MessageType.intern("inventory.openPlayerShop");
    public static final MessageType MSG_TYPE_PLAYER_SHOP_BUY= MessageType.intern("ao.PLAYER_SHOP_BUY");
   
    // Other/Admin
    public static final MessageType MSG_TYPE_RELOAD_ITEMS = MessageType.intern("ao.RELOAD_ITEMS");
    public static final MessageType MSG_TYPE_LOCKPICK = MessageType.intern("ao.LOCKPICK");

    public static final String INV_METHOD_SLOT = "slot";
    public static final String INV_METHOD_AMMO_TYPE = "itemAmmoType";
     public static final String MSG_INV_SLOT = "inv_slot";
    public static final String INV_METHOD_TYPE = "itemType";
    public static final String INV_METHOD_WEAPON_TYPE = "weaponType";

    public final static byte tradeSuccess = (byte)1;
    public final static byte tradeCancelled = (byte)2;
    public final static byte tradeFailed = (byte)3;
    public final static byte tradeBusy = (byte)4;
	
}
