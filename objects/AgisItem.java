package atavism.agis.objects;

import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.util.*;
import atavism.server.plugins.*;
import atavism.agis.core.*;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.AgisInventoryPlugin;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.RequirementCheckResult;
import atavism.agis.util.RequirementChecker;

import java.io.Serializable;
import java.util.*;

public class AgisItem extends AgisObject {
	public AgisItem() {
		setType(ObjectTypes.item);
	}

	public AgisItem(OID oid) {
		super(oid);
		setType(ObjectTypes.item);
	}

	public static AgisItem convert(AOObject obj) {
		if (!(obj instanceof AgisItem)) {
			throw new AORuntimeException("AgisItem.convert: obj is not an agisitem: " + obj);
		}
		return (AgisItem) obj;
	}

	public AgisPermissionCallback getAgisPermissionCallback() {
		return (AgisPermissionCallback) permissionCallback();
	}

	/**
	 * adds an equip mapping for this item. a mob can only equip this item if the
	 * slot if registered
	 */
	public void addEquipSlot(AgisEquipSlot equipSlot) {
		lock.lock();
		try {
			List<AgisEquipSlot> slots = getEquipSlots();
			if (slots == null) {
				slots = new ArrayList<AgisEquipSlot>();
				setEquipSlots(slots);
			}
			slots.add(equipSlot);
		} finally {
			lock.unlock();
		}
	}

	public void setEquipSlots(List<AgisEquipSlot> equipSlots) {
		AgisEquipInfo equipInfo = new AgisEquipInfo();
		for (AgisEquipSlot slot : equipSlots) {
			equipInfo.addEquipSlot(slot);
		}
		setProperty(InventoryClient.TEMPL_EQUIP_INFO, equipInfo);
	}

	public List<AgisEquipSlot> getEquipSlots() {
		AgisEquipInfo equipInfo = (AgisEquipInfo) getProperty(InventoryClient.TEMPL_EQUIP_INFO);
		if (equipInfo == null)
			return null;
		return equipInfo.getEquippableSlots();
	}
	
	public AgisEquipInfo getEquipInfo() {
		AgisEquipInfo equipInfo = (AgisEquipInfo) getProperty(InventoryClient.TEMPL_EQUIP_INFO);
		return equipInfo;
	}

	public AgisEquipSlot getPrimarySlot() {
		lock.lock();
		try {
			List<AgisEquipSlot> slots = getEquipSlots();
			if ((slots == null) || slots.isEmpty()) {
				return null;
			}
			return slots.get(0);
		} finally {
			lock.unlock();
		}
	}

	public boolean equipSlotExists(AgisEquipSlot equipSlot) {
		lock.lock();
		try {
			List<AgisEquipSlot> slots = getEquipSlots();
			if ((slots == null) || slots.isEmpty()) {
				return false;
			}
			return slots.contains(equipSlot);
		} finally {
			lock.unlock();
		}
	}

    /**
     * Called when an item has been equipped. Checks the binding value of the item and if it 
     * is bind on equip (1) it will bind the item to the player.
     */
	public void itemEquipped(OID playerOid) {
		// Check if the item was bind on equip
		Integer binding = (Integer) getProperty("binding");
		Log.debug("BIND: got item binding: " + binding);
		if (binding != null && binding == 1) {
			bindToPlayer(playerOid);
		}
	}

	public boolean canBeTraded() {
		if (isPlayerBound())
			return false;
		return true;
	}

	public void setIcon(String icon) {
		setProperty(InventoryClient.TEMPL_ICON, icon);
	}

	public String getIcon() {
		String icon = (String) getProperty(InventoryClient.TEMPL_ICON);
		if (icon == null) {
			return "UNKNOWN_ICON";
		}
		return icon;
	}

	String icon = null;

	public String getIcon2() {
		String icon = (String) getProperty(InventoryClient.TEMPL_ICON2);
		if (icon == null) {
			return "UNKNOWN_ICON";
		}
		return icon;
	}

	public void setItemType(String itemType) {
		setProperty("itemType", itemType);
	}

	public String getItemType() {
		String itemType = (String) getProperty("itemType");
		if (itemType == null) {
			return "";
		}
		return itemType;
	}

	String itemType = null;

    /**
     * Use to change the number of items in the stack. If the item is in a standard inventory bag it
     * will send the ItemAcquiredStatusChange so its effects can be re-applied.
     * @param itemOwner
     * @param delta
     */
    public void alterStackSize(OID itemOwner, int delta) {
    	Log.debug("ITEM: altering stack size by: " + delta + " with stackSize: " + getStackSize() + " and stackLimit: " + getStackLimit());
    	//log.dumpStack(this+" itemOwner:"+itemOwner+" delta:"+delta);
    	if (getStackSize() + delta > getStackLimit()) {
    		setProperty("stackSize", getStackLimit());
    	} else {
    		setProperty("stackSize", getStackSize() + delta);
    	}
    	Log.debug("ITEM: 2 altering stack size by: " + delta + " with stackSize: " + getStackSize() + " and stackLimit: " + getStackLimit());
    	
    	if (itemOwner != null && getProperty(AgisInventoryPlugin.INVENTORY_PROP_BACKREF_KEY) != null) {
    		OID bagOid = (OID) getProperty(AgisInventoryPlugin.INVENTORY_PROP_BACKREF_KEY);
    		// This will currently only get standard or equipped bags
    		Bag bag = AgisInventoryPlugin.getBag(bagOid);
    		if (bag != null && bag.getUseType() == Bag.BAG_USE_TYPE_STANDARD) {
    			AgisInventoryClient.itemAcquiredStatusChange(itemOwner, this, true);
    		}
    	}
    }
    
    public void setStackSize(int stackSize) {
    //	log.dumpStack(this+" stackSize:"+stackSize);
    	setProperty("stackSize", stackSize);
    }
    public int getStackSize() {
    	Integer stackSize = (Integer) getProperty("stackSize");
    	if (stackSize == null) {
            return 1;
        }
        return stackSize;
    }
    
    public void setStackLimit(int stackLimit) {
    	setProperty("stackLimit", stackLimit);
    }
    public int getStackLimit() {
    	Integer stackLimit = (Integer) getProperty("stackLimit");
    	if (stackLimit == null) {
            return 1;
        }
        return stackLimit;
    }
    
    public void setPurchaseCurrency(int purchaseCurrency) {
    	setProperty("purchaseCurrency", purchaseCurrency);
    }
    public int getPurchaseCurrency() {
    	Integer purchaseCurrency = (Integer) getProperty("purchaseCurrency");
    	if (purchaseCurrency == null) {
            return 0;
        }
        return purchaseCurrency;
    }
    int purchaseCurrency = 0;

	public void setPurchaseCost(Long purchaseCost) {
		setProperty("purchaseCost", purchaseCost);
	}

	public void setPurchaseCost(Integer purchaseCost) {
		setProperty("purchaseCost", (long) purchaseCost);
	}

	public Long getPurchaseCost() {
		Long purchaseCost = (Long) getProperty("purchaseCost");
		if (purchaseCost == null) {
			return 0L;
		}
		return purchaseCost;
	}

	Long purchaseCost = 0L;
    
    
    public void bindToPlayer(OID playerOid) {
    	setProperty("boundToPlayer", true);
    	setProperty("boundPlayerOid", playerOid.toLong());
    }
    public boolean isPlayerBound() {
    	Boolean isBound = (Boolean) getProperty("boundToPlayer");
    	Log.debug("BIND: is item bound? " + isBound);
    	if (isBound == null) {
            return false;
        }
        return isBound;
    }

    public OID getPlayerBound() {
    	long bound = (long) getProperty("boundPlayerOid");
    	Log.debug("BIND: item is bound to ? " + bound);
    	if (bound == 0L) {
            return null;
        }
        return OID.fromLong(bound);
    }

    /**
     * register's the method to call when this item gets activated by 
     * the user
     */

	public void setActivateHook(ActivateHook hook) {
		ArrayList<ActivateHook> hooks = (ArrayList<ActivateHook>) getProperty(InventoryClient.TEMPL_ACTIVATE_HOOK);
		if (hooks == null) {
			hooks = new ArrayList<ActivateHook>();
		}
		hooks.add(hook);
		setProperty(InventoryClient.TEMPL_ACTIVATE_HOOK, hook);
	}

	public void setActivateHook(ArrayList<ActivateHook> hook) {
		setProperty(InventoryClient.TEMPL_ACTIVATE_HOOK, hook);
	}

	public ArrayList<ActivateHook> getActivateHook() {
		return (ArrayList<ActivateHook>) getProperty(InventoryClient.TEMPL_ACTIVATE_HOOK);
	}

	public boolean activate(OID activatorOid, OID targetOid) {
		if (Log.loggingDebug)
			log.debug("AgisItem.activate: activator=" + activatorOid + " item=" + this + " target=" + targetOid);
		// Do requirements checks
		HashMap<Integer, HashMap<String, Integer>> requirements = (HashMap<Integer, HashMap<String, Integer>>) getProperty("requirements");
		RequirementCheckResult canUse = RequirementChecker.DoesPlayerMeetRequirements(activatorOid, requirements);
		if (!canUse.result.equals(RequirementCheckResult.RESULT_SUCCESS)) {
			EventMessageHelper.SendRequirementFailedEvent(activatorOid, canUse);
			return false;
		}

		// If all checks have passed, run the activation
		ArrayList<ActivateHook> activateHook = (ArrayList<ActivateHook>) getProperty(InventoryClient.TEMPL_ACTIVATE_HOOK);
		if (activateHook == null) {
			log.warn("activate: activateHook is null");
			return false;
		}
		boolean delete = false;
		for (ActivateHook ah : activateHook) {
			delete = ah.activate(activatorOid, this, targetOid);
			log.debug("Activate Item Hook " + activateHook + " activatorOid=" + activatorOid + " targetOid=" + targetOid);
		}

		if(delete) {
			if (AgisInventoryClient.removeSpecificItem(activatorOid, getOid(), false, 1)) {
				if(getStackSize()<1) {
					deleteById(getOid());
				}
			}
		}
	    		
		return true;
	}

    /**
     * registers the method to call when this item is acquired by 
     * the user
     */
    public void addAcquiredHook(ArrayList<AcquireHook> hook) {
    	setProperty(TEMPL_ACQUIRE_HOOK, hook);
    }
    public ArrayList<AcquireHook> getAcquiredHooks() {
        return (ArrayList<AcquireHook>) getProperty(TEMPL_ACQUIRE_HOOK);
    }
    
    /**
     * Runs the AcquireHook for the item, returning false if the item is to
     * be destroyed instead of adding it to the players bag.
     * @param activatorOid
     * @return should the item be destroyed
     */
    public boolean acquired(OID activatorOid) {
        if (Log.loggingDebug)
            log.debug("AgisItem.acquire: activator=" + activatorOid + " item=" + this);
        
        // Check if the item was bind on acquire
    	Integer binding = (Integer) getProperty("binding");
    	Log.debug("BIND: got item binding: " + binding);
    	if (binding != null && binding == 2 && !isPlayerBound()) {
    		bindToPlayer(activatorOid);
    	}
    	
        // Get the AcquireHook if there is one and run the acquired() function
    	ArrayList<AcquireHook> acquireHook = (ArrayList<AcquireHook>) getProperty(TEMPL_ACQUIRE_HOOK);
        if (acquireHook == null) {
        	log.debug("activate: acquireHook is null");
        	AgisInventoryClient.itemAcquiredStatusChange(activatorOid, this, true);
            return false;
        }
        boolean destroyItem = false;
        for(AcquireHook ah : acquireHook) {
        	destroyItem = ah.acquired(activatorOid, this);
            log.debug("AgisItem.acquire: "+ah+" activator=" + activatorOid + " item=" + this);
            
        }
       // boolean destroyItem = acquireHook.acquired(activatorOid, this);
        if (!destroyItem) {
        	// Send the item acquired message out
        	AgisInventoryClient.itemAcquiredStatusChange(activatorOid, this, true);
        }
        return destroyItem;
    }
    
    /**
     * register's the method to call when this item is acquired by 
     * the user
     */
    public void addUnacquiredHook(UnacquireHook hook) {
    	setProperty(TEMPL_UNACQUIRE_HOOK, hook);
    }
    public UnacquireHook getUnacquiredHooks() {
        return (UnacquireHook) getProperty(TEMPL_UNACQUIRE_HOOK);
    }
    
    /**
     * Called when a player no longer has an item in their inventory. Calls any
     * unacquireHooks which can perform actions when this item is being removed from the player.
     * @param activatorOid
     * @return
     */
    public boolean unacquired(OID activatorOid) {
    	if (Log.loggingDebug)
            log.debug("AgisItem.unacquire: activator=" + activatorOid + " item=" + this);
    	// Send the item unacquired message out. Do this before the hook is run as it may delete the item
        AgisInventoryClient.itemAcquiredStatusChange(activatorOid, this, false);
        
    	// Get the UnacquireHook if there is one and run the unacquired() function
        UnacquireHook unacquireHook = (UnacquireHook) getProperty(TEMPL_UNACQUIRE_HOOK);
        if (unacquireHook == null) {
        	log.debug("activate: unacquireHook is null");
            return false;
        }
        return unacquireHook.unacquired(activatorOid, this);
    }
    
    public String toString() {
   	 String s = "[AgisItem: " + getName() + ":" + getOid() + " Type="+getItemType()+" stack="+getStackSize() + "] ";
        lock.lock();
        try {
            for (Map.Entry<String, Serializable> entry : this.getPropertyMap().entrySet()) {
                    String key = entry.getKey();
                    Serializable val = entry.getValue();
                    s += "( key=" + key + ", val=" + val + ")";
            }
            return s;
        } finally {
            lock.unlock();
        }
//       return s;
   }
    
 
   public static void deleteById(OID itemId) {
	   if(EntityManager.getEntityByNamespace(itemId, Namespace.AGISITEM) != null)
       EntityManager.removeEntityByNamespace(itemId, Namespace.AGISITEM);
       Engine.getDatabase().deleteObjectData(itemId);
   }

    protected static String EQUIP_INFO_PROP = "equipInfo";
    
    public static String TEMPL_ACQUIRE_HOOK = "item_acquireHook";
    public static String TEMPL_UNACQUIRE_HOOK = "item_unacquireHook";
    
    public static String AMMO_TYPE = "item_ammoType";
    public static String AMMO_CAPACITY = "item_ammoCapacity";
    public static String AMMO_LOADED = "item_ammoLoaded";

    private static final long serialVersionUID = 1L;
}