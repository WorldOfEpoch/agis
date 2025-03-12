package atavism.agis.objects;

import java.util.*;

import atavism.server.engine.*;
import atavism.server.objects.Entity;
import atavism.server.objects.ObjectType;
import atavism.server.objects.ObjectTypes;
import atavism.server.util.Log;

/**
 * a bag used to hold objects, either items or other bags
 */
public class Bag extends Entity {
    public Bag() {
        super();
        setNamespace(Namespace.BAG);
        this.setName("Bag");
        this.setNumSlots(0);
    }
    
    public Bag(OID oid) {
        super(oid);
        setNamespace(Namespace.BAG);
    }

    public Bag(int numSlots) {
        super();
        setNamespace(Namespace.BAG);
        this.setName("Bag");
        setNumSlots(numSlots);
    }
    
    public Bag(int id, int numSlots) {
    	this(numSlots);
    	setItemTemplateID(id);
    }
    
    /**
     * Applies the settings for the bag. WARNING: this will clear the currentItems.
     * @param numSlots
     * @param useType
     */
    public void applySettings(int numSlots, int useType) {
    	this.numSlots = numSlots;
    	this.useType = useType;
    	
    	items = new ArrayList<OID>();
        for (int i=0; i<numSlots; i++)
            items.add(null);
    }

    public ObjectType getType() {
        return ObjectTypes.bag;
    }

    public int getNumSlots() {
        return numSlots;
    }
    
    public void setNumSlots(int numSlots) {
        items = new ArrayList<OID>();
        for (int i=0; i<numSlots; i++)
            items.add(null);
        this.numSlots = numSlots;
    }
    
	public void changeNumSlots(int numSlots) {
		if (items == null)
			items = new ArrayList<OID>();
		if( Log.loggingDebug)log.debug("bag changeNumSlots from "+items.size()+" to "+numSlots);
		int from = items.size();
		for (int i = from; i < numSlots; i++)
			items.add(null);
		this.numSlots = numSlots;
	}

    public int getItemTemplateID() {
    	return itemTemplateID;	
    }
    public void setItemTemplateID(int id) {
    	this.itemTemplateID = id;
    }
    
    public int getUseType() {
    	return useType;	
    }
    public void setUseType(int useType) {
    	this.useType = useType;
    }

    /**
     * places item into specified slot. slotNum starts with 0 returns false if
     * there already is an item
     */
    public boolean putItem(int slotNum, OID itemOid) {
        lock.lock();
        try {
            // make sure the slot is within range
            if (slotNum >= numSlots) {
                if( Log.loggingDebug)log.debug("BAG numSlots="+numSlots+" slotNum="+slotNum+" false");
                return false;
            }

            // make sure slot is empty
            if (items.get(slotNum) != null) {
                if( Log.loggingDebug)log.debug("BAG numSlots="+numSlots+" slotNum="+slotNum+" in use");
                return false;
            }

            // add item into slot
            items.set(slotNum, itemOid);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public OID getItem(int slotNum) {
        lock.lock();
        try {
            if (slotNum >= numSlots) {
                return null;
            }
            return items.get(slotNum);
        } finally {
            lock.unlock();
        }
    }

    /**
     * add item to next available slot
     */
    public boolean addItem(OID oid) {
        lock.lock();
        try {
            for (int i = 0; i < numSlots; i++) {
                if (getItem(i) == null) {
                    putItem(i, oid);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean removeItem(OID oid) {
        lock.lock();
        try {
            Integer slotNum = findItem(oid);
            if (slotNum == null) {
                return false;
            }
            items.set(slotNum, null);
            return true;
        }
        finally {
            lock.unlock();
        }
    }
    public boolean removeItem(int slotNum) {
        lock.lock();
        try {
            if (slotNum >= numSlots) {
                return false;
            }
            items.set(slotNum, null);
            return true;
        }
        finally {
            lock.unlock();
        }
    }
    /**
     * java beans paradigm for saving into the databse
     */
    public void setItemsList(OID[] items) {
        lock.lock();
        try {
            this.items = new ArrayList<OID>();
            for (OID oidVal : items)
                this.items.add(oidVal);
            numSlots = items.length;
        } finally {
            lock.unlock();
        }
    }

    public OID[] getItemsList() {
        lock.lock();
        try {
            OID[] copy = new OID[numSlots];
            for (int i=0; i<numSlots; i++)
                copy[i] = items.get(i);
            return copy;
        } finally {
            lock.unlock();
        }
    }
    
    public ArrayList<OID> getItemsListArray(){
    	lock.lock();
    	try {
    		return (ArrayList<OID>)items.clone();
        } finally {
            lock.unlock();
        }
    }
    public boolean isEmpty() {
    	lock.lock();
    	try {
	    	for(int i = 0; i < getNumSlots(); i++) {
	    		if(items.get(i) != null) {
	    			return false;
	    		}
	    	}
	    	return true;
    	}
    	finally {
    		lock.unlock();
    	}
    }
    
    public int itemCount() {
    	lock.lock();
    	try {
    		int count = 0;
	    	for(int i = 0; i < getNumSlots(); i++) {
	    		if(items.get(i) != null) {
	    			count++;
	    		}
	    	}
	    	return count;
    	}
    	finally {
    		lock.unlock();
    	}
    }

    /**
     * returns the slotnumber where the item is located in this bag, or null if not
     * found
     * @param itemOid oid for the item you are looking for
     * @return slotnumber or null if not found
     */
    public Integer findItem(OID itemOid) {
        lock.lock();
        try {
            for (int i=0; i<getNumSlots(); i++) {
                if (itemOid.equals(items.get(i))) {
                    return i;
                }
            }
            return null;
        }
        finally {
            lock.unlock();
        }
    }
    
	public int getNumLocks() {
    	return numLocks;
    }
    public void setNumLocks(int numLocks) {
    	this.numLocks = numLocks;
    }
    
    public OID getChestLock() {
    	return chestLock;
    }
    public void setChestLock(OID chestLock) {
    	this.chestLock = chestLock;
    	setLocked();
    }
    
    /**
     * Adds a specific amount of locks to the chest and returns
     * the leftover amount if the limit is reached.
     * @param amount
     * @param chestLock
     * @return
     */
    public int addChestLocks(int amount, OID chestLock) {
    	if(this.chestLock == null) {
    		this.chestLock = chestLock;
    	}
    	setLocked();
    	if(numLocks + amount > lockLimit) {
    		numLocks = lockLimit;
    		return (numLocks + amount) - lockLimit;
    	} else {
    		numLocks += amount;
    		return 0;
    	}
    }
    public void removeChestLocks(int amount) {
    	if(numLocks - amount <= 0) {
    		numLocks = 0;
    		setChestLock(null);
    	} else {
    		numLocks -= amount;
    		setLocked();
    	}
    }
    
    public boolean isLocked() {
    	return locked;
    }
    
    public boolean isLockable() {
    	return lockable;
    }
    public void setLockable(boolean lockable) {
    	this.lockable = lockable;
    }
    
    public int getLockLimit() {
		return lockLimit;
	}
	public void setLockLimit(int lockLimit) {
		this.lockLimit = lockLimit;
	}
    
    public void setLocked() {
    	locked = chestLock != null;
    }
    
    public void setBank(boolean b) {
    	bank=b;
    }
    public boolean getBank() {
    	return bank;
    }
    public void setStorageName(String name) {
    	storageName=name;
    }
    public String getStorageName() {
    	return storageName;
    }
    
    
    private int itemTemplateID; // What item template was this bag made from (to recreate the item if needed)
    private int numSlots;
    private int useType;
    private ArrayList<OID> items = new ArrayList<OID>(); // contains the oids of the items in this bag
    private int lockLimit = 0;			// How many locks bag is allowed.
    private int numLocks = 0;			// How many locks are on bag. Must be below lockLimit.
    private OID chestLock = null;		// OID of lock object.
    private boolean locked = false;   	// Does bag have at least one lock.
    private boolean lockable = false; 	// Can bag hold locks.
    private boolean bank =false;
    private String storageName = "";
    public static final int BAG_USE_TYPE_STANDARD = 1;
    public static final int BAG_USE_TYPE_EQUIPPED = 2;
    public static final int BAG_USE_TYPE_STORAGE = 3;
    public static final int BAG_USE_TYPE_BANK = 4;
    public static final int BAG_USE_TYPE_GUILD = 5;

    private static final long serialVersionUID = 1L;
}
