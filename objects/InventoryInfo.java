package atavism.agis.objects;

import atavism.agis.core.Agis;
import atavism.agis.plugins.AgisInventoryPlugin;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.server.objects.*;
import atavism.server.engine.*;
import atavism.server.util.*;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Information related to the inventory system. Any object that wants to carry items or
 * currency requires one of these.
 */
public class InventoryInfo extends Entity {
	public InventoryInfo() {
		super();
		setNamespace(Namespace.BAG);
	}

	public InventoryInfo(OID objOid) {
		super(objOid);
		setNamespace(Namespace.BAG);
	}

    public String toString() {
        return "[Entity: " + getName() + ":" + getOid() + "]";
    }

    public ObjectType getType() {
        return ObjectTypes.bag;
    }
	
	public int getID() {
		return id;
	}
	public void setID(int id) {
		this.id = id;
	}
	private int id;
	
	/**
	 * Category control
	 * Each category has its own collection of items.
	 */
	public int getCurrentCategory() {
		return currentCategory;
	}
	public void setCurrentCategory(int category) {
		Log.debug("QSI: setting current category to: " + category + " from: " + currentCategory);
		this.currentCategory = category;
	}
	public boolean categoryUpdated(int category) {
		boolean createInventory = false;
		if (!bags.containsKey(category)) {
			//bags.put(category, new OID[numBags]);
			createInventory = true;
		}
		if (!currencies.containsKey(category))
    		currencies.put(category, new HashMap<Integer, Long>());
		this.currentCategory = category;
		Engine.getPersistenceManager().setDirty(this);
		return createInventory;
	}
	protected int currentCategory;
	
	/*
	 * Currencies
	 */
	
	public long getCurrencyAmount(int currencyID, boolean includeConversions) {
		Currency c = Agis.CurrencyManager.get(currencyID);
		if (getCurrencies(currentCategory).containsKey(currencyID)) {
			//TODO: run external check, if its an external currency then query the account db
			 getCurrencies(currentCategory).get(currencyID);
			 long amount =0l;
		/*	if( getCurrencies(currentCategory).get(currencyID) instanceof Integer ) {
				int a = getCurrencies(currentCategory).get(currencyID);
				amount =  Long.valueOf(a); 
			}else {*/
				try {
					amount = getCurrencies(currentCategory).get(currencyID).longValue();
				} catch (Exception e) {
					log.error("Przez string");
					amount = Long.parseLong(String.valueOf(getCurrencies(currentCategory).get(currencyID)));
					
				}
			/*}*/
			//log.error("getCurrencyAmount "+getCurrencies(currentCategory).get(currencyID));
			//long amount = Long.valueOf(getCurrencies(currentCategory).get(currencyID));
			// Check if there is a parent currency, if so, that needs added to the amount
			int conversionCurrency = c.getAutoConversionCurrency();
			if (conversionCurrency > 0 && includeConversions) {
				amount += getCurrencyAmount(conversionCurrency, true) * c.getConversionAmountReq(conversionCurrency);
			}
			log.debug("Currency: getCurrencyAmount currencyID="+currencyID+ " includeConversions="+includeConversions+" amount="+amount);
			return amount;
		} else {
			// Player does not yet have this currency, lets create it
			addCurrency(currencyID, 0l);
					return 0l;
		}
	}
	
	/**
	 * Alters the amount of the specified currency the player by the specified amount. Due to the
	 * use of subCurrencies this may alter more than one currency.
	 * @param currencyID
	 * @param delta
	 */
	public boolean alterCurrencyAmount(int currencyID, long delta) {
		long currentAmount = getCurrencyAmount(currencyID, false);
		log.debug("Currency: alterCurrencyAmount I currencyID="+currencyID+ " delta="+delta+" currentAmount="+currentAmount);
			currentAmount += delta;
		Currency c = Agis.CurrencyManager.get(currencyID);
		// First check against the maximum
		int conversionCurrency = c.getAutoConversionCurrency();
		log.debug("Currency: alterCurrencyAmount II currencyID="+currencyID+ " delta="+delta+" currentAmount="+currentAmount+" conversionCurrency="+conversionCurrency);
		if (conversionCurrency != -1 && currentAmount >= c.getConversionAmountReq(conversionCurrency)) {
			long newCurrencyAlteration = currentAmount / c.getConversionAmountReq(conversionCurrency);
			log.debug("Currency: alterCurrencyAmount III currencyID="+currencyID+ " delta="+delta+" currentAmount="+currentAmount+" newCurrencyAlteration="+newCurrencyAlteration);
			alterCurrencyAmount(conversionCurrency, newCurrencyAlteration);
			currentAmount = currentAmount % c.getConversionAmountReq(conversionCurrency);
		} else if (conversionCurrency != -1 && currentAmount < 0) {
			// Reduce the higher up currency as this one is now below 0
			long newCurrencyAlteration = (long) Math.floor((double) currentAmount / (double)c.getConversionAmountReq(conversionCurrency));
			log.debug("Currency: alterCurrencyAmount IV currencyID="+currencyID+ " delta="+delta+" currentAmount="+currentAmount+" newCurrencyAlteration="+newCurrencyAlteration);
			alterCurrencyAmount(conversionCurrency, newCurrencyAlteration/*-1*/);
			long leftOver = Math.abs(currentAmount % c.getConversionAmountReq(conversionCurrency));
			log.debug("Currency: alterCurrencyAmount V currencyID="+currencyID+ " delta="+delta+" leftOver="+leftOver);
			
			if (leftOver > 0)
				currentAmount = c.getConversionAmountReq(conversionCurrency) - leftOver;
			else
				currentAmount = 0l;
		}
		log.debug("Currency: alterCurrencyAmount VI currencyID="+currencyID+ " delta="+delta+" currentAmount="+currentAmount);
		
		if (currentAmount > c.maximumAmount) {
			currentAmount = c.maximumAmount;
		} else if (currentAmount < 0) {
			currentAmount = 0l;
		}
		
		getCurrencies(currentCategory).put(currencyID, currentAmount);
		// Return true to show the currency was increased
		return true;
	}

	public void addCurrency(int currencyID, Long amount) {
		if (Log.loggingDebug) {
			Log.debug("InventoryInfo.addCurrency: adding currency=" + currencyID + " to obj=" + this);
        }
        lock.lock();
        try {
            if (getCurrencies(currentCategory).containsKey(currencyID)) {
                return;
            }
            getCurrencies(currentCategory).put(currencyID, amount);
            Engine.getPersistenceManager().setDirty(this);
        } finally {
            lock.unlock();
        }
	}
	public void removeCurrency(int currencyID) {
		if (Log.loggingDebug) {
			Log.debug("InventoryInfo.removeCurrency: removing currency=" + currencyID + " from obj=" + this);
        }
        lock.lock();
        try {
        	getCurrencies(currentCategory).remove(currencyID);
            Engine.getPersistenceManager().setDirty(this);
            ExtendedCombatMessages.sendCurrencies(this.getOid(), getCurrencies(currentCategory));
        } finally {
            lock.unlock();
        }
	}
	public HashMap<Integer, Long> getCurrentCurrencies() {
        lock.lock();
        try {
        	Log.debug("II: currencies: " + currencies);
            return getCurrencies(currentCategory);
        } finally {
            lock.unlock();
        }
    }
	public void setCurrentCurrencies(HashMap<Integer, Long>currencies) {
        lock.lock();
        try {
            this.currencies.put(currentCategory, new HashMap<Integer, Long>(currencies));
        } finally {
            lock.unlock();
        }
	}
	public HashMap<Integer, HashMap<Integer, Long>> getCurrencies() {
        lock.lock();
        try {
        	Log.debug("II: currencies: " + currencies);
            return new HashMap<Integer, HashMap<Integer, Long>>(currencies);
        } finally {
            lock.unlock();
        }
    }
	public void setCurrencies(HashMap<Integer, HashMap<Integer, Long>>currencies) {
        lock.lock();
        try {
            this.currencies = new HashMap<Integer, HashMap<Integer, Long>>(currencies);
        } finally {
            lock.unlock();
        }
	}

	public HashMap<Integer, Long> getCurrencies(int category) {
        lock.lock();
        try {
        	Log.debug("II: currencies: " + currencies);
        	if (!currencies.containsKey(category))
        		currencies.put(category, new HashMap<Integer, Long>());
            return currencies.get(category);
        } finally {
            lock.unlock();
        }
    }
	
	private HashMap<Integer, HashMap<Integer, Long>> currencies = new HashMap<Integer, HashMap<Integer, Long>>();
	
	/*
	 * Standard Inventory Bags
	 */

	public OID[] getBags() {
        lock.lock();
        try {
        	Log.debug("II: bags: " + bags.get(currentCategory) + " from current category: " + currentCategory);
            return getBags(currentCategory);
        } finally {
            lock.unlock();
        }
    }
	public void setBags(OID bags[]) {
        lock.lock();
        try {
        	Log.debug("II: setting bags for currentCategory: " + currentCategory);
            this.bags.put(currentCategory, bags);
        } finally {
            lock.unlock();
        }
	}
	public HashMap<Integer, OID[]> getBagsMap() {
        lock.lock();
        try {
            return new HashMap<Integer, OID[]>(bags);
        } finally {
            lock.unlock();
        }
    }
	public void setBagsMap(HashMap<Integer, OID[]> rootBags) {
        lock.lock();
        try {
            this.bags = new HashMap<Integer, OID[]>(rootBags);
        } finally {
            lock.unlock();
        }
	}
	public OID[] getBags(int category) {
        lock.lock();
        try {
        	if (!bags.containsKey(category)) {
        		bags.put(category, new OID[AgisInventoryPlugin.INVENTORY_BAG_COUNT]);
        	}
            return bags.get(category);
        } finally {
            lock.unlock();
        }
    }
	
	private HashMap<Integer, OID[]> bags = new HashMap<Integer, OID[]>();
	
	/*
	 * Equipped Items
	 */
	public OID getEquipmentItemBag() {
        lock.lock();
        try {
        	Log.debug("II: bags: " + equippedItemsBag.get(currentCategory) + " from current category: " + currentCategory);
            return getEquippedItemsBag(currentCategory);
        } finally {
            lock.unlock();
        }
    }
	public void setEquipmentItemBag(OID bagOid) {
        lock.lock();
        try {
        	Log.debug("II: setting bags for currentCategory: " + currentCategory);
            this.equippedItemsBag.put(currentCategory, bagOid);
        } finally {
            lock.unlock();
        }
	}
	public HashMap<Integer, OID> getEquippedItemsBagMap() {
        lock.lock();
        try {
            return new HashMap<Integer, OID>(equippedItemsBag);
        } finally {
            lock.unlock();
        }
    }
	public void setEquippedItemsBagMap(HashMap<Integer, OID> rootBags) {
        lock.lock();
        try {
            this.equippedItemsBag = new HashMap<Integer, OID>(rootBags);
        } finally {
            lock.unlock();
        }
	}
	public OID getEquippedItemsBag(int category) {
        lock.lock();
        try {
        	if (!equippedItemsBag.containsKey(category))
        		equippedItemsBag.put(category, null);
            return equippedItemsBag.get(category);
        } finally {
            lock.unlock();
        }
    }
	
	private HashMap<Integer, OID> equippedItemsBag = new HashMap<Integer, OID>();
	
	/*
	 * Storage Bags
	 */
	
	public OID getActiveStorageBag() {
		lock.lock();
        try {
        	if (getStorageBags(currentCategory).containsKey(activeStorageBag)) {
        		return getStorageBags(currentCategory).get(activeStorageBag);
        	} else {
        		return null;
        	}
        } finally {
            lock.unlock();
        }
	}

	public OID getEquippedItemsSetBag(String key) {
		lock.lock();
		try {
			if (getStorageBags(currentCategory).containsKey("SET_"+key)) {
			//	activeStorage = key;
				return getStorageBags(currentCategory).get("SET_"+key);
			} else {
				return null;
			}
		} finally {
			lock.unlock();
		}
	}
	public void addEquippedItemsSetBag(String key, OID bagOid) {
		lock.lock();
		try {
			getStorageBags(currentCategory).put("SET_"+key, bagOid);
		} finally {
			lock.unlock();
		}
	}
	public OID getStorageBag(String key) {
		lock.lock();
        try {
        	if (getStorageBags(currentCategory).containsKey(key)) {
        		//activeStorage = key;
        		return getStorageBags(currentCategory).get(key);
        	} else {
        		return null;
        	}
        } finally {
            lock.unlock();
        }
	}

	public void addStorageBag(String key, OID bagOid) {
		lock.lock();
        try {
        	getStorageBags(currentCategory).put(key, bagOid);
        } finally {
            lock.unlock();
        }
	}

	public HashMap<String, OID> getStorageBags() {
        lock.lock();
        try {
        	Log.debug("II: bags: " + bags.get(currentCategory) + " from current category: " + currentCategory);
            return getStorageBags(currentCategory);
        } finally {
            lock.unlock();
        }
    }
	public void setStorageBags(HashMap<String, OID> bags) {
        lock.lock();
        try {
        	Log.debug("II: setting bags for currentCategory: " + currentCategory);
            this.storageBags.put(currentCategory, bags);
        } finally {
            lock.unlock();
        }
	}
	public HashMap<Integer, HashMap<String, OID>> getStorageBagsMap() {
        lock.lock();
        try {
            return new HashMap<Integer, HashMap<String, OID>>(storageBags);
        } finally {
            lock.unlock();
        }
    }
	public void setStorageBagsMap(HashMap<Integer, HashMap<String, OID>> rootBags) {
        lock.lock();
        try {
            this.storageBags = new HashMap<Integer, HashMap<String, OID>>(rootBags);
        } finally {
            lock.unlock();
        }
	}
	public HashMap<String, OID> getStorageBags(int category) {
        lock.lock();
        try {
        	if (!storageBags.containsKey(category))
        		storageBags.put(category, new HashMap<String, OID>());
            return storageBags.get(category);
        } finally {
            lock.unlock();
        }
    }
	
	private HashMap<Integer, HashMap<String, OID>> storageBags = new HashMap<Integer, HashMap<String, OID>>();
	
	
	public String getActiveStorage() {
        lock.lock();
        try {
            return activeStorage;
        } finally {
            lock.unlock();
        }
    }
	public void setActiveStorage(String storageName) {
        lock.lock();
        try {
            this.activeStorage = storageName;
        } finally {
            lock.unlock();
        }
	}
	// Need to keep track of which storage bag the character has open
	private String activeStorage = "";
	private String activeStorageBag = "";

	/*
	 * Mail
	 */
	
	public void addMail(Mail m) {
		mail.add(m);
		Engine.getPersistenceManager().setDirty(this);
	}
	public ArrayList<Mail> getMail() {
		return new ArrayList<Mail>(mail);
	}
	public void setMail(ArrayList<Mail> mail) {
		this.mail = new ArrayList<Mail>(mail);
	}
	private ArrayList<Mail> mail = new ArrayList<Mail>();
	
	/*
	 * Bonuses
	 */
	public HashMap<String, BonusSettings> getBonuses(){
		return bonuses;
	}
	public void setBonuses(HashMap<String, BonusSettings> bonuses) {
		this.bonuses =bonuses;
	}
	
	HashMap<String, BonusSettings> bonuses =new HashMap<String, BonusSettings>();
	/*
	 * Final Static properties
	 */
	
	
	/*
	 * Shop Creation
	 */
	public int getShopMobTemplate() {
		return shopMobTemplate;
	}

	public void setShopMobTemplate(int value) {
		this.shopMobTemplate = value;
	}

	public int getNumShops() {
		return numShops;
	}

	public void setNumShops(int value) {
		this.numShops = value;
	}

	public int getShopSlots() {
		return shopSlots;
	}

	public void setShopSlots(int value) {
		this.shopSlots = value;
	}

	public String getShopTag() {
		return shopTag;
	}

	public void setShopTag(String value) {
		this.shopTag = value;
	}

	public int getShopTimeOut() {
		return shopTimeOut;
	}

	public void setShopTimeOut(int value) {
		this.shopTimeOut = value;
	}

	public String getItemSetSelected() {
		return itemSetSelected;
	}

	public void setItemSetSelected(String value) {
		this.itemSetSelected = value;
	}

	public Map<Integer, PetInventoryInfo> getPetInventory() {
		return petInventory;
	}

	public void setPetInventory(Map<Integer, PetInventoryInfo> petInventory) {
		this.petInventory = new ConcurrentHashMap<Integer, PetInventoryInfo>(petInventory);
	}

	public void setShopDestroyOnLogOut(boolean v) {
		destroyOnLogOut =v;
	}
	public boolean getShopDestroyOnLogOut() {
		return destroyOnLogOut;
	}
	protected boolean destroyOnLogOut = true;
	protected int shopMobTemplate = -1;
	protected int numShops = -1;
	protected String shopTag = "";
	protected int shopSlots = -1;
	protected int shopTimeOut = 0;

	protected String itemSetSelected = "";


	transient ConcurrentHashMap<Integer, PetInventoryInfo> petInventory = new ConcurrentHashMap<Integer, PetInventoryInfo>();

	static {
		try {
			BeanInfo info = Introspector.getBeanInfo(InventoryInfo.class);
			PropertyDescriptor[] propertyDescriptors = info.getPropertyDescriptors();
			for (int i = 0; i < propertyDescriptors.length; ++i) {
				PropertyDescriptor pd = propertyDescriptors[i];
				if (pd.getName().equals("currencies")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("mail")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("bonuses")) {
					pd.setValue("transient", Boolean.TRUE);
				} else if (pd.getName().equals("petInventory")) {
					pd.setValue("transient", Boolean.TRUE);
				}
//				else if (pd.getName().equals("attackableTargets")) {
//					pd.setValue("transient", Boolean.TRUE);
//				}
				log.debug("BeanInfo name="+pd.getName());
			}
		} catch (Exception e) {
			Log.error("failed beans initalization");
		}
	}

	private static final long serialVersionUID = 1L;
}
