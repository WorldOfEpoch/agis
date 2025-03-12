package atavism.agis.objects;

import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.AgisInventoryPlugin;
import atavism.agis.plugins.AgisMobClient;
import atavism.agis.util.EquipHelper;
import atavism.agis.util.RequirementChecker;
import atavism.msgsys.Message;
import atavism.msgsys.MessageDispatch;
import atavism.server.engine.Engine;
import atavism.server.engine.OID;
import atavism.server.messages.LogoutMessage;
import atavism.server.messages.PropertyMessage;
import atavism.server.messages.SubscriptionManager;
import atavism.server.objects.AOObject;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;

import java.io.Serializable;
import java.util.*;

import static atavism.agis.plugins.AgisInventoryPlugin.getAgisItem;

public class PetInventoryInfo implements Serializable, MessageDispatch {
	protected int petProfile;
	protected OID ownerOid;
	protected AgisInventoryPlugin.EquipMap equipMap = new AgisInventoryPlugin.EquipMap();
	protected HashMap<Integer, OID> equippedItemsBag = new HashMap<Integer, OID>();
	protected int currentCategory = 1;
	transient ArrayList<OID> pets = new ArrayList<OID>();

	public PetInventoryInfo() {
	}
	public PetInventoryInfo(OID ownerOid,int petProfile) {
		this.petProfile = petProfile;
		this.ownerOid = ownerOid;
	}

	/**
	 * Activates the message subscriptions so this pet object can pick up relevant messages.
	 *
	 * @return
	 */
	public boolean activate() {
		if (Log.loggingDebug)
			Log.debug("PetInventoryInfo: in activate: owner this " + this);
		// subscribe for some messages
		SubscriptionManager.get().subscribe(this, ownerOid, PropertyMessage.MSG_TYPE_PROPERTY, LogoutMessage.MSG_TYPE_LOGOUT, WorldManagerClient.MSG_TYPE_DESPAWNED, AgisInventoryClient.MSG_TYPE_GET_PET_INVENTORY, AgisMobClient.MSG_TYPE_PET_LEVELUP);
		if (Log.loggingDebug)
			Log.debug("PetInventoryInfo: set up subscription for pet owner: " + ownerOid);
		return true;
	}

	public boolean activate(OID petOid) {
		if (Log.loggingDebug)
			Log.debug("PetInventoryInfo: in activate: for pet "+petOid+" this " + this);
		// subscribe for some messages
		SubscriptionManager.get().subscribe(this, petOid, PropertyMessage.MSG_TYPE_PROPERTY, AgisMobClient.MSG_TYPE_PET_TARGET_LOST,WorldManagerClient.MSG_TYPE_DESPAWNED);
		if (Log.loggingDebug)
			Log.debug("PetInventoryInfo: set up subscription for pet owner: " + ownerOid);
		return true;
	}

	public void deactivate(OID petOid) {
		if (Log.loggingDebug)
			Log.debug("PetInventoryInfo: in deactivate: for pet " + petOid);
		// subscribe for some messages
		SubscriptionManager.get().unsubscribe(this, petOid, PropertyMessage.MSG_TYPE_PROPERTY, AgisMobClient.MSG_TYPE_PET_TARGET_LOST,WorldManagerClient.MSG_TYPE_DESPAWNED);
		if (Log.loggingDebug)
			Log.debug("PetInventoryInfo: set up unsubscription for pet : " + petOid);
	}

	public void deactivate() {
		if (Log.loggingDebug)
			Log.debug("PetInventoryInfo: in deactivate: this " + this);
		// subscribe for some messages
		SubscriptionManager.get().unsubscribe(this, ownerOid, PropertyMessage.MSG_TYPE_PROPERTY, LogoutMessage.MSG_TYPE_LOGOUT, WorldManagerClient.MSG_TYPE_DESPAWNED, AgisInventoryClient.MSG_TYPE_GET_PET_INVENTORY, AgisMobClient.MSG_TYPE_PET_LEVELUP);
		for (OID petOid : pets) {
			deactivate(petOid);
		}
		if (Log.loggingDebug)
			Log.debug("PetInventoryInfo: set up unsubscription for pet owner: " + ownerOid);
	}



	@Override
	public void handleMessage(Message message, int flags) {

	 if (message.getMsgType() == AgisInventoryClient.MSG_TYPE_GET_PET_INVENTORY) {
		 Log.debug("PetInventoryInfo: Get Pet Inventory");
		 WorldManagerClient.ExtensionMessage msg = (WorldManagerClient.ExtensionMessage) message;
		 int profile = (int) msg.getProperty("petProfile");
		if(Log.loggingDebug) Log.debug("PetInventoryInfo: profileId: " + profile+" petProfile="+petProfile);
		 if(petProfile == profile) {
			 Log.error("PetInventoryInfo: correct pet profile send Equip information");
			 sendEquippedInvUpdate();
		 }else{
			 Log.error("PetInventoryInfo: not correct pet profile send Equip information");
		 }
		 Log.debug("PetInventoryInfo: Get Pet Inventory End");
	 } else if (message.getMsgType() == WorldManagerClient.MSG_TYPE_DESPAWNED) {
		 Log.debug("PetInventoryInfo: DespawnedMessage");
		 WorldManagerClient.DespawnedMessage despawnedMsg = (WorldManagerClient.DespawnedMessage) message;
		 OID objOid = despawnedMsg.getSubject();
		 getPets().remove(objOid);

		 AgisInventoryPlugin.updatePetList(ownerOid);
	 } else if (message.getMsgType() == AgisMobClient.MSG_TYPE_PET_LEVELUP) {
		 Log.debug("PetInventoryInfo: MSG_TYPE_PET_LEVELUP");
		 Log.debug("PetInventoryInfo: got levelup message");
		 AgisMobClient.PetLevelUpMessage msg = (AgisMobClient.PetLevelUpMessage) message;
		 OID subject = msg.getSubject();
		 int _profile = (int)msg.getProperty("profile");
		 if(getPetProfile()==_profile) {
//			 AgisInventoryPlugin.updatePetList(ownerOid);

		 }
	 } else {
		 Log.debug("PetInventoryInfo: not handling message: " + message.getMsgType());
	 }



	}

	public HashMap<Integer, Integer> getEquipSetsInfo() {
		Log.debug("PetInventoryInfo: getEquipSetsInfo: ");

		AgisItem item = null;// = getAgisItem(itemOid);
		OID itemOID;
		AgisInventoryPlugin.EquipMap equipMap;
		HashMap<Integer, Integer> list = new HashMap<Integer, Integer>();
		//lock.lock();
		ArrayList<OID> checkItems = new ArrayList<OID>();
		try {
			equipMap = getEquipMap();

			Map<String, AgisEquipSlot> slots = AgisEquipSlot.getSlots();
			int weaponType = RequirementChecker.getIdEditorOptionChoice("Item Slot Type", "Weapon");
			int armorType = RequirementChecker.getIdEditorOptionChoice("Item Slot Type", "Armor");
			for (AgisEquipSlot aes : slots.values()) {
				if (!aes.getName().startsWith("Set_"))
					if (aes.getTypeIds().contains(weaponType) || aes.getTypeIds().contains(armorType)) {
						itemOID = equipMap.get(aes);
						if (itemOID != null) {
							checkItems.add(itemOID);
							item = getAgisItem(itemOID);
							if (item != null) {
								int setId = (int) item.getProperty("item_set");
								if (setId > 0) {
									if (EquipHelper.IsDurabilityOk(item)) {
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
			Log.error("PetInventoryInfo: getEquipSetsInfo Exception:" + e.getLocalizedMessage() + " " + e.getMessage());
		} finally {
			//  lock.unlock();
		}
		Log.debug("PetInventoryInfo: getEquipSetsInfo: end list: " + list);
		return list;
	}


	public AgisInventoryPlugin.EquipMap getEquipMap() {
		return equipMap;
	}

	public void setEquipMap(AgisInventoryPlugin.EquipMap equipMap) {
		this.equipMap = equipMap;
	}

	public void generateEquipMapFromBag() {
		OID subBagOid = getEquipmentItemBag();
		if (subBagOid == null) {
			Log.error("PetInventoryInfo: generateEquipMapFromBag: owner " + ownerOid + " pet profile=" + petProfile);
		}
		Bag subBag = AgisInventoryPlugin.getBag(subBagOid);
		if (subBag == null) {
			Log.error("PetInventoryInfo: generateEquipMapFromBag: owner " + ownerOid + " pet profile=" + petProfile);
		}
		//Clear EquipMap
		setEquipMap(new AgisInventoryPlugin.EquipMap());
		for (int pos = 0; pos < subBag.getNumSlots(); pos++) {
			OID oid = subBag.getItem(pos);
			if (oid != null) {
				for (AgisEquipSlot aei : AgisEquipInfo.DefaultEquipInfo.getEquippableSlots()) {
					if (aei.getId() == pos) {
						equipMap.getEquipMap().put(aei, oid);
					}
				}
			}
		}
	}



	/*
	 * Equipped Items
	 */
	public OID getEquipmentItemBag() {
			return getEquippedItemsBag(currentCategory);
	}

	public void setEquipmentItemBag(OID bagOid) {
			this.equippedItemsBag.put(currentCategory, bagOid);
	}

	public HashMap<Integer, OID> getEquippedItemsBagMap() {
			return new HashMap<Integer, OID>(equippedItemsBag);
	}

	public void setEquippedItemsBagMap(HashMap<Integer, OID> rootBags) {
			this.equippedItemsBag = new HashMap<Integer, OID>(rootBags);
	}

	public OID getEquippedItemsBag(int category) {
			if (!equippedItemsBag.containsKey(category))
				equippedItemsBag.put(category, null);
			return equippedItemsBag.get(category);
	}

	/**
	 * Function check if item is
	 * @param itemObj
	 * @return
	 */
	public boolean isItemEquipped(AOObject itemObj) {
		AgisItem item = AgisItem.convert(itemObj);
		Log.debug("pet isItemEquipped: petProfile=" + petProfile + " activatorOid=" + ownerOid + " item=" + item);
		AgisEquipInfo oaei = item.getEquipInfo();
		AgisInventoryPlugin.EquipMap equipMap = getEquipMap();
		if (oaei != null) {
			Log.debug("pet isItemEquipped: AgisEquipInfo not null");
			if (oaei.slotsCount() > 0) {
				Log.debug("pet isItemEquipped: AgisEquipInfo slotsCount >0 ");
				List<AgisEquipSlot> oslots = oaei.getEquippableSlots();
				Log.debug("pet isItemEquipped: AgisEquipInfo oslots  "+oslots);
				for (AgisEquipSlot oaes : oslots) {
					Log.debug("pet isItemEquipped: AgisEquipInfo oslots  "+oaes);
					OID oItemOid = equipMap.get(oaes);
					Log.debug("pet isItemEquipped: oItemOid=" + oItemOid);
					if (oItemOid != null && oItemOid.equals(item.getOid())) {
						Log.debug("pet EQUIP: got isEquipped item: " + oItemOid);
						return true;
					}
				}
			}
		}
		Log.debug("pet isItemEquipped: end not Equipped");
		return false;
	}

	public void sendEquippedInvUpdate() {
		if(Log.loggingDebug) Log.debug("PetInventoryInfo: sendEquippedInvUpdate");
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "EquippedPetInventoryUpdate");

		// go through each bag and place contents into inv update msg
		OID subBagOid = getEquipmentItemBag();
		if (subBagOid == null) {
			Log.error("PetInventoryInfo: sendEquippedInvUpdate: sub bag oid is null for " + ownerOid+" pet profile="+petProfile);
		}
		Bag subBag = AgisInventoryPlugin.getBag(subBagOid);
		if (subBag == null) {
			Log.error("PetInventoryInfo: sendEquippedInvUpdate: sub bag obj is null for " + ownerOid+" pet profile="+petProfile);
		}
		props.put("numSlots", subBag.getNumSlots());
		HashMap<Integer, Integer> itemSets = getEquipSetsInfo();
		for (int pos = 0; pos < subBag.getNumSlots(); pos++) {
			if (Log.loggingDebug) Log.debug("PET: sendEquippedInvUpdate: slot=" + pos);
			// get the item
			OID oid = subBag.getItem(pos);
			if (oid == null) {
				Log.warn("PetInventoryInfo: sendEquippedInvUpdate:  item oid  " + oid + " is null");
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
				Log.debug("PetInventoryInfo: sendEquippedInvUpdate: " + ", itemOid=" + oid + ", itemName=" + item.getName() + ",icon=" + item.getIcon());
			props.put("item_" + pos + "TemplateID", item.getTemplateID());

			props.put("item_" + pos + "Name", item.getName());
			props.put("item_" + pos + "BaseName", item.getProperty("baseName"));
			props.put("item_" + pos + "Id", item.getOid());
			props.put("item_" + pos + "Count", item.getStackSize());
			AgisEquipSlot slot = AgisEquipSlot.getSlotById(pos);
			props.put("item_" + pos + "Slot", slot != null ? slot.getName() : "");
			props.put("item_" + pos + "Bound", item.isPlayerBound());

			if (Log.loggingDebug)
				Log.debug("PetInventoryInfo: sendEquippedInvUpdate: " + ", itemOid=" + oid + ", pos=" + pos + " itemName=" + item.getName() + ",slot=" + (slot != null ? slot.getName() : " is null"));
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
				for (String resistance : resistances.keySet()) {
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
				for (String statName : stats.keySet()) {
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
						if (Log.loggingDebug)
							Log.debug(item.getName() + " " + statName + " " + stats.get(statName) + " " + bstats.get(statName) + " " + (stats.get(statName) - bstats.get(statName)) + " ?");
						if (stats.get(statName) - bstats.get(statName) != 0) {
							props.put("item_" + pos + "EStat_" + numStats + "Name", statName);
							props.put("item_" + pos + "EStat_" + numStats + "Value", stats.get(statName) - bstats.get(statName));
							if (Log.loggingDebug)
								Log.debug(item.getName() + " " + statName + " " + (stats.get(statName) - bstats.get(statName)));
							numStats++;
						}
					} else {
						props.put("item_" + pos + "EStat_" + numStats + "Name", statName);
						props.put("item_" + pos + "EStat_" + numStats + "Value", stats.get(statName));
						if (Log.loggingDebug)
							Log.debug(item.getName() + " " + statName + " " + (stats.get(statName)) + " |");
						numStats++;

					}
				}
				props.put("item_" + pos + "NumEStats", numStats);
			} else {
				props.put("item_" + pos + "NumEStats", 0);
			}

			//Added for Enchant Effects and Abilities
			if (enchantLevel > 0) {
				if (item.getProperty("enchantProfileId") != null) {
					EnchantProfile ep = ObjectManagerPlugin.getEnchantProfile((int) item.getProperty("enchantProfileId"));
					if (ep != null && ep.GetLevels().containsKey(enchantLevel)) {
						int numEffects = 0;
						int numAbilities = 0;
						for (int e = 1; e <= enchantLevel; e++) {
							EnchantProfileLevel enchantProfile = ep.GetLevels().get(e);
							for (Integer ability : enchantProfile.GetAbilities()) {
								props.put("item_" + pos + "EAbility_" + numAbilities + "Value", ability);
								numAbilities++;
							}
							for (Integer effect : enchantProfile.GetEffects()) {
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
				for (Integer socket : sockets.keySet()) {
					if (sockets.get(socket).GetItemOid() != null) {
						AgisItem itemSoc = getAgisItem(sockets.get(socket).GetItemOid());
						if (itemSoc != null) {
							props.put("item_" + pos + "socket_" + socket + "Item", itemSoc.getTemplateID());
							props.put("item_" + pos + "socket_" + socket + "ItemOid", itemSoc.getOid().toLong());
						} else {
							props.put("item_" + pos + "socket_" + socket + "Item", -1);
							props.put("item_" + pos + "socket_" + socket + "ItemOid", 0L);
						}
					} else {
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
			if (setid > 0) {
				if (itemSets.containsKey(setid)) {
					props.put("item_" + pos + "NumOfSet", itemSets.get(setid));
				} else {
					props.put("item_" + pos + "NumOfSet", 0);
				}
			} else {
				props.put("item_" + pos + "NumOfSet", 0);
			}
		}

		// Send Ammo link
//		int ammoItemID = -1;
//		Integer ammoLoaded = null;
//		if (Log.loggingDebug)
//			Log.debug("PET: sendEquippedInvUpdate ammo " + ownerOid + " " + CombatClient.NAMESPACE + " " + CombatInfo.COMBAT_AMMO_LOADED);
//		ammoLoaded = (Integer) EnginePlugin.getObjectProperty(ownerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_AMMO_LOADED);
//		if (ammoLoaded != null)
//			ammoItemID = ammoLoaded;
//		props.put("equippedAmmo", ammoItemID);
		if (Log.loggingDebug)
			Log.debug("PetInventoryInfo: sendEquippedInvUpdate " + props);
		WorldManagerClient.TargetedExtensionMessage msg = new WorldManagerClient.TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, ownerOid, ownerOid, false, props);
		int num = Engine.getAgent().sendBroadcast(msg);
		if (Log.loggingDebug) Log.debug("PetInventoryInfo: sendEquippedInvUpdate: num=" + num);
	}


	public ArrayList<OID> getPets() {
		return pets;
	}

	public void addPet(OID petOid) {
		Log.debug("PetInventoryInfo: addPet: " + petOid);
		pets.add(petOid);
		activate(petOid);

		Log.debug("PetInventoryInfo: addPet: apply equip for pet");
		AgisInventoryPlugin.EquipMap equipMap =  getEquipMap();
		HashMap<AgisEquipSlot, OID> items = equipMap.getEquipMap();
		if(items.size()>0) {
			HashMap<Integer, Integer> list = getEquipSetsInfo();
			for (AgisEquipSlot slot : items.keySet()) {
				AgisItem item = getAgisItem(items.get(slot));
				if(item!=null) {
					AgisInventoryClient.itemEquipStatusChanged(petOid, item, null, slot.toString(), list);
					updatePetEquippedItem(petOid, slot, item);
				}
			}
		}
	}


	public void updatePetEquippedItem(OID petOid, AgisEquipSlot slot, AgisItem item ){
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
		EquipHelper.updateDisplay(petOid, displayVal, slot, unicItem);
	}

	public void removePet(OID petOid) {
		pets.remove(petOid);

	}

	public OID getOwnerOid() {
		return ownerOid;
	}

	public int getPetProfile() {
		return petProfile;
	}

	@Override
	public String toString() {
		return "PetInventoryInfo [owner="+ownerOid+" petProfile=" + petProfile+ " petsOid="+pets+ "]";
	}

	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;


}
