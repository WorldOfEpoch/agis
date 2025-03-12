package atavism.agis.util;

import atavism.agis.objects.*;
import atavism.agis.abilities.FriendlyEffectAbility;
import atavism.agis.core.Agis;
import atavism.agis.core.AgisAbility;
import atavism.agis.core.AgisEffect;
import atavism.agis.core.AgisAbility.AbilityResult;
import atavism.agis.effects.TeachAbilityEffect;
import atavism.agis.plugins.*;
import atavism.server.engine.*;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.util.Log;
import atavism.server.util.ObjectLockManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

/**
 * Contains static functions that assist with Equipping and Unequipping items.
 * @author Andrew
 *
 */
public class EquipHelper {
	
	/**
	 * Updates the stats and other properties of the character who has just acquired or lost
	 * an item. By default is empty and is to be filled in by developers if they wish.
	 * @param oid
	 * @param item: The item acquired or lost
	 * @param acquired: True means the player now has the item, false means they lost it.
	 * @param obj
	 */
	public static void ItemAcquiredStatHelper(OID oid, AgisItem item, boolean acquired, CombatInfo obj) {
		Log.debug("EquipHelper.ItemAcquiredStatHelper item =" + item);
		if (item == null || item.getProperty("weight") == null || CombatPlugin.WEIGHT_STAT == null) {
			// Requires a valid item to do any stat changing
			return;
		}
		
		obj.statRemoveModifier(CombatPlugin.WEIGHT_STAT, "acquired" + item.getOid(), false);
		Log.debug("WEIGHT: removing item =" + item.getOid());
		
		Integer weight = (Integer) item.getProperty("weight");
		weight *= item.getStackSize();
		if (acquired) {
			obj.statAddModifier(CombatPlugin.WEIGHT_STAT, "acquired" + item.getOid(), weight, false); // Weight is added on
			Log.debug("WEIGHT: item =" + item.getOid() + " with count: " + item.getStackSize() + " is giving weight: " + weight);
		}
		
		obj.statSendUpdate(false);
	}
	
	public static boolean IsDurabilityOk(AgisItem item) {
		boolean durabilityOk = true;
		if (item != null && item.getProperty("maxDurability") != null) 
		{
			int durability = (int) item.getProperty("durability");
			int maxDurability = (int) item.getProperty("maxDurability");
			if(Log.loggingDebug)Log.debug("EquipHelper.IsDurabilityOk: durability " + durability + "/" + maxDurability);
			if (maxDurability > 0 && durability == 0) {
				durabilityOk = false;
			}
		}
		return durabilityOk;
	}

	public static void UpdateEquiperPassiveAbility(OID oid, AgisItem itemToEquip, AgisItem itemToUnequip, CombatInfo info) 
	{
		boolean update=false;
		if (itemToUnequip != null) {
			if (itemToUnequip.getProperty("pabilityID") != null && (Integer) itemToUnequip.getProperty("pabilityID") > 0) {
				Integer abilityId = (Integer) itemToUnequip.getProperty("pabilityID");
				AgisAbility ability = Agis.AbilityManager.get(abilityId);
				if (ability != null) {
					FriendlyEffectAbility Eability = (FriendlyEffectAbility) ability;
					ArrayList<AbilityEffectDefinition> effectsToAdd = Eability.getPowerUpDefinition(0L).getEffectDefinition();
					for (int i = 0; i < effectsToAdd.size(); i++) {
						if(Log.loggingDebug)Log.debug("COMBATPLUGIN: about to apply passive effect: " + effectsToAdd.get(i).getEffectId() + " to player: " + info.getOwnerOid());
						AgisEffect.removeEffectByID(info, effectsToAdd.get(i).getEffectId());
						update=true;
					}
				}
			}
		}
		if (itemToEquip != null) {
			if (itemToEquip.getProperty("pabilityID") != null && (Integer) itemToEquip.getProperty("pabilityID") > 0) {
				Integer abilityId = (Integer) itemToEquip.getProperty("pabilityID");
				AgisAbility ability = Agis.AbilityManager.get(abilityId);
				if (ability != null) {
					if (ability.checkEquip(info, info, null) == AbilityResult.SUCCESS) {
						if (ability.getAbilityType() == 2) {
							if (ability instanceof FriendlyEffectAbility) {
								FriendlyEffectAbility Eability = (FriendlyEffectAbility) ability;
								ArrayList<AbilityEffectDefinition> effectsToAdd = Eability.getPowerUpDefinition(0L).getEffectDefinition();
								for (int i = 0; i < effectsToAdd.size(); i++) {
									if(Log.loggingDebug)Log.debug("COMBATPLUGIN: about to apply passive effect: " + effectsToAdd.get(i).getEffectId() + " to player: " + info.getOwnerOid());
									HashMap<String, Serializable> params = new HashMap<String, Serializable>();
									params.put("skillType", Eability.getSkillType());
									params.put("powerUp",0L);
									AgisEffect effect = Agis.EffectManager.get(effectsToAdd.get(i).getEffectId());
									AgisEffect.applyPassiveEffect(effect, info, info, ability.getID(), params);
									update=true;
								}
							}
						}
					}
				}
				
			}
		}
		if(update)
			info.updateEffectsProperty();
	}
	
	public static void UpdateEquiperItemSetEffectsAndAbilities(OID oid, AgisItem itemToEquip, AgisItem itemToUnequip, CombatInfo info, HashMap<Integer,Integer> itemSets) 
	{	
		boolean update = false;
		Integer setId = 0;
		
		if (itemToUnequip != null) 
		{	
			setId = itemToUnequip.getIntProperty("item_set");
			if (setId > 0) {
				ItemSetProfile isp = ObjectManagerClient.getItemSetProfile(setId);
				for (ItemSetLevel isl : isp.GetLevels()) {
					// If Last item from set is unequipped then itemSets.get will return null
					if (isl.GetNumberOfParts() == (itemSets.get(setId) != null ? itemSets.get(setId) + 1 : 1)) {
						// Remove Effects
						if (isl.GetEffects().size() > 0) {
							for (int i = 0; i < isl.GetEffects().size(); i++) {
								// CombatClient.removeEffect(oid, isl.GetEffects().get(i),1);
								RemoveTeachAbilityForEquipment(info, isl.GetEffects().get(i));
								AgisEffect.removeEffectByID(info, isl.GetEffects().get(i), 1);
								update = true;
							}
						}
						if (isl.GetAbilities().size() > 0) {
							for (int i = 0; i < isl.GetAbilities().size(); i++) {
								AgisAbility ab = Agis.AbilityManager.get(isl.GetAbilities().get(i));
								if (ab.getAbilityType() == 2) {
									SkillInfo.removePassiveEffect(ab, info);										
									update = true;
								}
							}
						}
					}
				}
			}						
		}

		if (itemToEquip != null) {
			setId = itemToEquip.getIntProperty("item_set");

			if (setId > 0) {					
				ItemSetProfile isp = ObjectManagerClient.getItemSetProfile(setId);
				for (ItemSetLevel isl : isp.GetLevels()) {
					if (isl.GetNumberOfParts() == itemSets.get(setId)) {
						// Apply Effects
						if (isl.GetEffects().size() > 0) {
							for (int i = 0; i < isl.GetEffects().size(); i++) {
								// CombatClient.applyEffect(oid, isl.GetEffects().get(i));
								AgisEffect effect = Agis.EffectManager.get(isl.GetEffects().get(i));
								AgisEffect.applyEffect(effect, info, info, -1);									
								update = true;
							}
						}
						if (isl.GetAbilities().size() > 0) {
							for (int i = 0; i < isl.GetAbilities().size(); i++) {
								AgisAbility ab = Agis.AbilityManager.get(isl.GetAbilities().get(i));
								if (ab.getAbilityType() == 2) {
									SkillInfo.applyPassiveEffects(ab, info);										
									update = true;
								} else {
									CombatClient.startAbility(isl.GetAbilities().get(i), oid, oid, null, null);										
									update = true;
								}
							}
						}
					}
				}
			}
		}
		if (update)
			info.updateEffectsProperty();
	}
	
	public static void UpdateEffectsAndAbilities(OID oid, CombatInfo info, HashMap<OID, AgisItem> equipMap, boolean remove, boolean apply) 
	{
		HashMap<OID, AgisItem> removeItems = new HashMap<OID, AgisItem>();
		HashMap<Integer,Integer> itemSets = new HashMap<Integer,Integer>();
		
		//Remove Effects and Abilities First
		for (Map.Entry<OID, AgisItem> entry : equipMap.entrySet()) 
		{
			AgisItem item = entry.getValue();
			if (item != null) 
			{	
				//Is item part of a set then store it for later
				Integer setId = item.getIntProperty("item_set");				
				if (setId != null && setId > 0) 
				{
					Integer setCount = itemSets.get(setId);
					itemSets.put(setId, (setCount == null ? 0 : setCount) + 1);					
				}
				
				//Check Durability
				if (!IsDurabilityOk(item)) 
				{	
					removeItems.put(entry.getKey(), entry.getValue());					
				}				
				//Remove Effects for Socket and Enchantments	
				if (remove) {
					UpdateEquiperSocketEffectsAndAbilities(oid, null, item, info);
					UpdateEquiperEnchantEffectsAndAbilities(oid, null, item, info);						
				}
			}			
		}
		
		if (remove) 
		{
			//Remove Effects and Abilities for itemSets
			for (Map.Entry<Integer, Integer> itemSet : itemSets.entrySet()) 
			{
				ItemSetProfile isp = ObjectManagerClient.getItemSetProfile(itemSet.getKey());	
				for (ItemSetLevel isl : isp.GetLevels()) 
				{
					for (int i = itemSet.getValue(); i > 0; i--) 
					{
						if (isl.GetNumberOfParts() == i) 
						{
							if (isl.GetEffects().size() > 0) {
								for (int e = 0; e < isl.GetEffects().size(); e++) {
									Integer effectID = isl.GetEffects().get(e);
									AgisEffect.removeEffectByID(info, effectID);
									AgisEffect ef = Agis.EffectManager.get(effectID);
								}
							}

							for (int a = 0; a < isl.GetAbilities().size(); a++) {
								AgisAbility ab = Agis.AbilityManager.get(isl.GetAbilities().get(a));
								if (ab.getAbilityType() == 2) {
									SkillInfo.removePassiveEffect(ab, info);	
								} 
							}
						}
					}
				}
			}
		}
		
		if (!info.dead() && apply) 
		{
			//Remove invalid items from equipMap
			equipMap.keySet().removeAll(removeItems.keySet());
			for (Map.Entry<OID, AgisItem> removeItem : removeItems.entrySet()) 
			{
				AgisItem item = removeItem.getValue();
				if (item != null) 
				{
					Integer setId = item.getIntProperty("item_set");				
					if (setId != null && setId > 0) 
					{
						//Reduce itemSet count
						Integer setCount = itemSets.get(setId);
						if (setCount != null) {
							setCount--;
							if (setCount <= 0) {
								itemSets.remove(setId);							
							} else {
								itemSets.put(setId, setCount);
							}							
						}					
					}
				}
			}
			
			//Re-Apply Effects and Abilities for valid items
			for (Map.Entry<OID, AgisItem> entry : equipMap.entrySet()) 
			{
				AgisItem item = entry.getValue();
				if (item != null) 
				{				
					//Apply Effects and Abilities for Socket and Enchantments				
					UpdateEquiperSocketEffectsAndAbilities(oid, item, null, info);
					UpdateEquiperEnchantEffectsAndAbilities(oid, item, null, info);	
					UpdateEquiperItemSetEffectsAndAbilities(oid, item, null, info, itemSets);
				}			
			}	
		}
		info.updateEffectsProperty();
	}
	
	public static void UpdateEquiperSocketEffectsAndAbilities(OID oid, AgisItem itemToEquip, AgisItem itemToUnequip, CombatInfo info)
	{	
		try 
		{	
			boolean update = false;		
			if (itemToUnequip != null) 
			{
				HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) itemToUnequip.getProperty("sockets");
				if (itemSockets != null) 
				{
					Set<Integer> keys = itemSockets.keySet();
					for (Integer i : keys) 
					{
						if (itemSockets.get(i) != null && itemSockets.get(i).GetItemOid() != null) 
						{
							OID socketOid = itemSockets.get(i).GetItemOid();
							//Grab item and check effects on it
							AgisItem sItem = AgisInventoryClient.getItem(socketOid);
							if (sItem != null)
							{
								ArrayList<Integer> socketEffects = (ArrayList<Integer>)sItem.getProperty("socketEffects");							
								if (socketEffects != null) 
								{									
									for (Integer socketEffect : socketEffects) 
									{	
										// CombatClient.applyEffect(oid, isl.GetEffects().get(i));	
										RemoveTeachAbilityForEquipment(info, socketEffect);
										AgisEffect.removeEffectByID(info, socketEffect, 1);
										update = true;										
									}
								} 
								ArrayList<Integer> socketAbilities = (ArrayList<Integer>)sItem.getProperty("socketAbilities");
								if (socketAbilities != null) 
								{									
									for (Integer socketAbility : socketAbilities) 
									{	
										AgisAbility ab = Agis.AbilityManager.get(socketAbility);
										if (ab != null && ab.getAbilityType() == 2) {
											SkillInfo.removePassiveEffect(ab, info);											
											update = true;
										}									
									}
								}
							}							
						}	
					}
				}
			}
			
			if (itemToEquip != null) 
			{
				HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) itemToEquip.getProperty("sockets");
				if (itemSockets != null) 
				{
					Set<Integer> keys = itemSockets.keySet();
					for (Integer i : keys) 
					{
						if (itemSockets.get(i) != null && itemSockets.get(i).GetItemOid() != null) 
						{
							OID socketOid = itemSockets.get(i).GetItemOid();
							//Grab item and check effects on it
							AgisItem sItem = AgisInventoryClient.getItem(socketOid);
							if (sItem != null)
							{
								ArrayList<Integer> socketEffects = (ArrayList<Integer>)sItem.getProperty("socketEffects");							
								if (socketEffects != null) 
								{									
									for (Integer socketEffect : socketEffects) 
									{	
										AgisEffect effect = Agis.EffectManager.get(socketEffect);
										AgisEffect.applyEffect(effect, info, info, -1);
										update = true;
									}
								} 	
								ArrayList<Integer> socketAbilities = (ArrayList<Integer>)sItem.getProperty("socketAbilities");
								if (socketAbilities != null) 
								{									
									for (Integer socketAbility : socketAbilities) 
									{	
										AgisAbility ab = Agis.AbilityManager.get(socketAbility);
										if (ab != null) 
										{
											if (ab.getAbilityType() == 2) {
												SkillInfo.applyPassiveEffects(ab, info);												
												update = true;
											} else {
												CombatClient.startAbility(socketAbility, oid, oid, null, null);												
												update = true;
											}
										}					
									}
								}
							}							
						}	
					}
				}
			}
			
			if (update)
				info.updateEffectsProperty();			
			
		} catch (Exception e) {
			Log.error("UpdateEquiperSocketEffectsAndAbilities\n" + Log.exceptionToString(e));
		}
	}
	
	public static void UpdateEquiperEnchantEffectsAndAbilities(OID oid, AgisItem itemToEquip, AgisItem itemToUnequip,CombatInfo info) 
	{
		try {			
			int enchantLevelToEquip = -1;
			int enchantLevelToUnequip = -1;
			int enchantProfileIDToEquip = -1;
			int enchantProfileIDToUnEquip = -1;
			int templateIDToEquip = 1;
			int templateIDToUnequip = -1;

			if (itemToEquip != null) {
				enchantProfileIDToEquip = (int) itemToEquip.getProperty("enchantProfileId");
				enchantLevelToEquip = (int) itemToEquip.getProperty("enchantLevel");
				templateIDToEquip = itemToEquip.getTemplateID();
			}

			if (itemToUnequip != null) {
				enchantProfileIDToUnEquip = (int) itemToUnequip.getProperty("enchantProfileId");
				enchantLevelToUnequip = (int) itemToUnequip.getProperty("enchantLevel");
				templateIDToUnequip = itemToUnequip.getTemplateID();
			}

			boolean sameEnchantProfileID = (enchantProfileIDToEquip == enchantProfileIDToUnEquip);
			boolean sameEnchantLevel = (enchantLevelToEquip == enchantLevelToUnequip);
			boolean sameTemplateID = (templateIDToEquip == templateIDToUnequip);

			// Only update if enchantLevel and ProfileID is different
			if (!sameTemplateID || sameTemplateID && sameEnchantProfileID && !sameEnchantLevel) {
				boolean update = false;
				if (itemToUnequip != null) 
				{
					if (enchantProfileIDToUnEquip > 0) 
					{
						EnchantProfile ep = ObjectManagerClient.getEnchantProfile(enchantProfileIDToUnEquip);
						if (ep.GetLevels().containsKey(enchantLevelToUnequip)) {							
							for (Map.Entry<Integer, EnchantProfileLevel> epe : ep.GetLevels().entrySet()) 
							{
								EnchantProfileLevel epl = epe.getValue();
								if (enchantLevelToUnequip >= epl.GetLevel()) 
								{	
									if (epe.getValue().GetAbilities().size() > 0) {										
										for (int i = 0; i < epl.GetAbilities().size(); i++) {
											AgisAbility ab = Agis.AbilityManager.get(epl.GetAbilities().get(i));
											if (ab.getAbilityType() == 2) {
												SkillInfo.removePassiveEffect(ab, info);
												update = true;
											}
										}
									}
									if (epe.getValue().GetEffects().size() > 0) {
										for (int i = 0; i < epl.GetEffects().size(); i++) {		
											RemoveTeachAbilityForEquipment(info, epl.GetEffects().get(i));
											AgisEffect.removeEffectByID(info, epl.GetEffects().get(i), 1);
											update = true;
										}
									}
								}
							}
						}
					}
				}

				if (itemToEquip != null) 
				{
					if (enchantProfileIDToEquip > 0) 
					{
						EnchantProfile ep = ObjectManagerClient.getEnchantProfile(enchantProfileIDToEquip);
						if (ep.GetLevels().containsKey(enchantLevelToEquip)) {							
							for (Map.Entry<Integer, EnchantProfileLevel> epe : ep.GetLevels().entrySet()) {
								EnchantProfileLevel epl = epe.getValue();								
								if (enchantLevelToEquip >= epl.GetLevel()) 
								{	
									if (epe.getValue().GetAbilities().size() > 0) {										
										for (int i = 0; i < epl.GetAbilities().size(); i++) {
											AgisAbility ab = Agis.AbilityManager.get(epl.GetAbilities().get(i));
											if (ab.getAbilityType() == 2) {
												SkillInfo.applyPassiveEffects(ab, info);												
												update = true;
											} else {
												CombatClient.startAbility(epl.GetAbilities().get(i), oid, oid, null, null);												
												update = true;
											}
										}

									}
									if (epe.getValue().GetEffects().size() > 0) {
										for (int i = 0; i < epl.GetEffects().size(); i++) {											
											AgisEffect effect = Agis.EffectManager.get(epl.GetEffects().get(i));
											AgisEffect.applyEffect(effect, info, info, -1);											
											update = true;
										}										
									}
								}
							}
						}
					}
				}

				if (update)
					info.updateEffectsProperty();
			}
		} catch (Exception e) {
			Log.error("UpdateEquiperEnchantEffectsAndAbilities\n" + Log.exceptionToString(e));
		}
	}
	
	private static void RemoveTeachAbilityForEquipment(CombatInfo target, int effectID)
	{
		//If TeachAbilityEffect and comes from equipment remove ability instead
		AgisEffect agEffect = Agis.EffectManager.get(effectID);
		if (agEffect != null && agEffect instanceof TeachAbilityEffect) 
		{	
			SkillInfo.unlearnAbility(target,((TeachAbilityEffect)agEffect).getAbilityID());
			//return true;
		}
	}

	/**
	 * Updates the stats and other properties of the character equipping or unequipping an item.
	 * @param oid: the identifier of the equipers
	 * @param item: the item being equipped
	 * @param equipping: whether the item is being equipped or unequipped
	 * @param obj: the equipers combat object
	 */
	public static void UpdateEquiperStats(OID oid, AgisItem itemToEquip, AgisItem itemToUnequip, CombatInfo obj) {
		Log.debug("EquipHelper.UpdateEquiperStats oid="+oid+" itemToEquip =" + itemToEquip + " and itemToUnequip = " + itemToUnequip);
		if (itemToEquip == null && itemToUnequip == null) {
			// Requires a valid item to do any stat changing
			return;
		}
		Integer setId = 0;
		// Is the item being equipped broken?
		boolean itemBroke = false;
		if (itemToEquip != null) {
			if (itemToEquip.getProperty("maxDurability") != null  && (Integer)itemToEquip.getProperty("maxDurability") > 0 && (Integer)itemToEquip.getProperty("durability") == 0) {
				itemBroke = true;
				Log.debug("UpdateEquiperStats Item is Broken !!!!!!!!!!!!!!!! ");
			}
		}
		
		if (itemBroke && itemToUnequip == null) {
			itemToUnequip = itemToEquip;
			itemToEquip = null;
		}
		ArrayList<String> weaponType = new ArrayList<String>();
		ArrayList<String> toolType = new ArrayList<String>();
		if(obj.getPropertyMap().containsKey("weaponType"))
			weaponType = (ArrayList<String>)obj.getProperty("weaponType");
		if(obj.getPropertyMap().containsKey("toolType"))
			toolType = (ArrayList<String>)obj.getProperty("toolType");
	// Unequip old item first
		if (itemToUnequip != null) {
			// First get the item type
			String itemType = (String) itemToUnequip.getProperty("itemType");
			String slot = (String) itemToUnequip.getProperty("slot");
			
			
			if (itemType.equals("Weapon")) {
				String subType = (String) itemToUnequip.getProperty("subType");
				weaponType.remove(subType);
				Log.debug("ITEMS: slot for item " + itemToUnequip.getName() + " is: " + slot);
					if(itemToUnequip.getPropertyMap().containsKey(InventoryClient.TEMPL_PARRY_EFFECT)) {
						if(itemToUnequip.getBooleanProperty(InventoryClient.TEMPL_PARRY_EFFECT) && itemToEquip == null)
							EnginePlugin.setObjectPropertiesNoResponse(oid, CombatClient.NAMESPACE, "weaponParry", false);
					}
					
					EnginePlugin.setObjectPropertiesNoResponse(oid, CombatClient.NAMESPACE, "attackType", "crush");
					EnginePlugin.setObjectPropertiesNoResponse(oid, CombatClient.NAMESPACE, "equipType", "");
					obj.statRemoveModifier(CombatPlugin.ATTACK_SPEED_STAT, itemToUnequip.getOid(), false);
					Log.debug("SPEED: removing attack speed modifier");
					//obj.statSetBaseValue(CombatPlugin.ATTACK_SPEED_STAT, obj.statGetMaxValue(CombatPlugin.ATTACK_SPEED_STAT));
					Integer autoAttack = (Integer)itemToUnequip.getProperty("autoAttack");
			    	if (autoAttack != null && autoAttack > 0) {
			    		//obj.resetAutoAttackAbility();
						Log.debug("UpdateEquiperStats: itemToUnequip:" + itemToUnequip + " autoAttack:" + autoAttack);
						EnginePlugin.setObjectPropertiesNoResponse(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_AUTOATTACK_ABILITY,
								obj.getProperty(CombatInfo.COMBAT_PROP_AUTOATTACK_BASE));
			    	}
				obj.statRemoveModifier("dmg-base", itemToUnequip.getOid(), false);
				obj.statRemoveModifier("dmg-max", itemToUnequip.getOid(), false);
			}
			if (itemType.equals("Tool")) {
				String subType = (String) itemToUnequip.getProperty("subType");
				toolType.remove(subType);
				}
			ArrayList<BonusSettings> bonuses =  (ArrayList<BonusSettings>)itemToUnequip.getProperty("bonuses");
			if( bonuses.size() > 0)
				BonusClient.sendBonusRemove(oid, bonuses, "Item"+itemToUnequip.getOid());
			
			HashMap<String, Integer> stats = (HashMap<String, Integer>) itemToUnequip.getProperty("bonusStats");
	
			// Alter players stats - First remove all stat links to this item
			for (String statName : CombatPlugin.STAT_LIST) {
				//if (stats.containsKey(statName)) {
					obj.statRemoveModifier(statName, itemToUnequip.getOid(), false);
				//}
			}
			
			HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) itemToUnequip.getProperty("sockets");
    		Set<Integer> keys = itemSockets.keySet();
    		for (Integer i : keys) {
    			if (itemSockets.get(i).GetItemOid()!=null) {
    				HashMap<String, Integer> aistats = (HashMap) AgisInventoryClient.getItemProperty(obj.getOid(), itemSockets.get(i).GetItemOid(), "bonusStats");
    				Log.debug("EQUIP:unequipping item altering stat aistats: " + aistats );
					
    			//	AgisItem ai = AgisInventoryPlugin.getAgisItem(itemSockets.get(i).GetItemOid());
    				if (aistats!=null) {
    			//		HashMap<String, Integer> aistats = (HashMap) ai.getProperty("bonusStats");
    					for (String statName : CombatPlugin.STAT_LIST) {
    						//if (stats.containsKey(statName)) {
    						Log.debug("EQUIP:unequipping item altering stat: " + statName );
    						
    							obj.statRemoveModifier(statName, itemSockets.get(i).GetItemOid(), false);
    						//}
    					}
    					
    				} else {
    					Log.debug("EQUIP: unequipping item socketing stats is null OID:"+itemSockets.get(i).GetItemOid());
    					
    				}
    			} else {
					Log.debug("EQUIP: unequipping item socketing itemOID is null");
					
				}
    		}
			
			setId = itemToUnequip.getIntProperty("item_set");
    		obj.statRemoveModifier("gearScore", itemToUnequip.getOid(), false);
			
		}
		
	//	Log.error("EQUIP: setId:" + setId);
		if (setId > 0) {
			HashMap<Integer, Integer> itemSets = (HashMap) AgisInventoryClient.getSetsEquiped(obj.getOid());
		//	Log.error("EQUIP: itemSets:" + itemSets);
			// for (Integer set : itemSets.keySet()) {
			obj.statRemoveModifier("dmg-base", "ItsemSet" + setId, false);
			obj.statRemoveModifier("dmg-max", "ItsemSet" + setId, false);
			obj.statRemovePercentModifier("dmg-base", "ItsemSet" + setId, false);
			obj.statRemovePercentModifier("dmg-max", "ItsemSet" + setId, false);
			for (String statName : CombatPlugin.STAT_LIST) {
				// if (stats.containsKey(statName)) {
				obj.statRemoveModifier(statName, "ItsemSet" + setId, false);
				obj.statRemovePercentModifier(statName, "ItsemSet" + setId, false);
				// }
			}
		//	Log.error("EQUIP: stats cleared for set:" + setId);

			HashMap<String, Integer> setStats = new HashMap<String, Integer>();
			HashMap<String, Integer> setStatsp = new HashMap<String, Integer>();
			ItemSetProfile isp = ObjectManagerClient.getItemSetProfile(setId);
		//	Log.error("EQUIP: setId:" + setId + " ItemSetProfile:" + isp + " levels:" + isp.GetLevels().size());

			for (ItemSetLevel isl : isp.GetLevels()) {
			//	Log.error("EQUIP: ItemSetLevel:" + isl);
				if (itemSets.containsKey(setId))
				{	
					if (isl.GetNumberOfParts() <= itemSets.get(setId)) 
					{	
					HashMap<String, EnchantStat> setLevStats = isl.GetStats();
					for (String stat : setLevStats.keySet()) {
						if (!setStats.containsKey(stat)) {
							setStats.put(stat, setLevStats.get(stat).GetValue());
							setStatsp.put(stat, (int)setLevStats.get(stat).GetValuePercentage());
						} else if (setStats.containsKey(stat)) {
							setStats.replace(stat, setStats.get(stat) + setLevStats.get(stat).GetValue());
							setStatsp.replace(stat, setStatsp.get(stat) + (int)setLevStats.get(stat).GetValuePercentage());
							}
						}
					}
				}
			}
			for (String statName : setStats.keySet()) {
				int value = setStats.get(statName);
			//	Log.error("EQUIP: Item Set altering stat: " + statName + " by: " + value);
				if (value > 0) {
					obj.statAddModifier(statName, "ItsemSet" + setId, value, false);
				//	Log.error("EQUIP: Item Set altered stat: " + statName + " by: " + value);
				}
			}
			for (String statName : setStatsp.keySet()) {
				int value = setStatsp.get(statName);
			//	Log.error("EQUIP: item Set altering percent stat: " + statName + " by: " + value);
				if (value > 0) {
					obj.statAddPercentModifier(statName, "ItsemSet" + setId, value, false);
			//		Log.error("EQUIP: Item Set altered percent stat: " + statName + " by: " + value);
				}
			}
			// }
		}
		if (itemToEquip != null) {
			// First get the item type
			String itemType = (String) itemToEquip.getProperty("itemType");
			String slot = (String) itemToEquip.getProperty("slot");
			if (itemType.equals("Weapon")) {
				String subType = (String) itemToEquip.getProperty("subType");
			//	String weaponType = "Armed";
				Log.debug("ITEMS: weapon type for item " + itemToEquip.getName() + " is: " + weaponType + " in slot: " + slot);
				weaponType.add(subType);
				if (slot.contains("Off Hand")) {
					//Log.debug("Equip Helper itemToEquip="+itemToEquip+" set weapon2Type="+subType);
					//EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, "weapon2Type", subType);
					String damageType = (String) itemToEquip.getProperty("damageType");
			    	EnginePlugin.setObjectPropertiesNoResponse(oid, CombatClient.NAMESPACE, "attack2Type", damageType);
			    	// Set equip type
			    	EnginePlugin.setObjectPropertiesNoResponse(oid, CombatClient.NAMESPACE, "equip2Type", subType);
			      
				} else {
					//Log.debug("Equip Helper itemToEquip="+itemToEquip+" set weaponType="+subType);
					//EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, "weaponType", subType);
					if(itemToEquip.getPropertyMap().containsKey(InventoryClient.TEMPL_PARRY_EFFECT)) {
						if(itemToEquip.getBooleanProperty(InventoryClient.TEMPL_PARRY_EFFECT))
							EnginePlugin.setObjectPropertiesNoResponse(oid, CombatClient.NAMESPACE, "weaponParry", true);
						else
							EnginePlugin.setObjectPropertiesNoResponse(oid, CombatClient.NAMESPACE, "weaponParry", false);
							
					} else {
						EnginePlugin.setObjectPropertiesNoResponse(oid, CombatClient.NAMESPACE, "weaponParry", false);
						
					}
						
					String damageType = (String) itemToEquip.getProperty("damageType");
			    	EnginePlugin.setObjectPropertiesNoResponse(oid, CombatClient.NAMESPACE, "attackType", damageType);
			    	// Set equip type
			    	EnginePlugin.setObjectPropertiesNoResponse(oid, CombatClient.NAMESPACE, "equipType", subType);
			    	// If the item has an auto attack, set it now
			    	Integer autoAttack = (Integer)itemToEquip.getProperty("autoAttack");
			    	if (autoAttack != null && autoAttack > 0) {
			    		//obj.overrideAutoAttackAbility(autoAttack);
			    		Log.debug("UpdateEquiperStats: itemToEquip:"+itemToEquip+" autoAttack:"+autoAttack);
			    		EnginePlugin.setObjectPropertiesNoResponse(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_AUTOATTACK_ABILITY, autoAttack);
			    	}
				}
		    	//obj.statSetBaseValue(CombatPlugin.ATTACK_SPEED_STAT, speed); // Speed is a flat value
		    /*	int damage = (Integer) itemToEquip.getProperty("damage");
		       	obj.statAddModifier("dmg-base", itemToEquip.getOid(), damage, false); // Damage is added on
		      	    Log.debug("Equip Item: "+obj.getName()+" dmg-base "+damage +" to equip "+ itemToEquip.getName());
		    	int damageMax = (Integer) itemToEquip.getProperty("damageMax");
		    	if (damageMax < damage) damageMax = damage;
		    	obj.statAddModifier("dmg-max", itemToEquip.getOid(), damageMax, false); // Damage is added on
		    	*/
					} 
			
			if (itemType.equals("Tool")) {
				String subType = (String) itemToEquip.getProperty("subType");
				toolType.add(subType);
			}
	
			// Alter players stats - First remove all stat links to this item
			HashMap<String, Integer> stats = new HashMap<String, Integer>((HashMap<String, Integer>)itemToEquip.getProperty("bonusStats"));
			if (itemType.equals("Weapon")) {
				int damage = (Integer) itemToEquip.getProperty("damage");
		     //  	obj.statAddModifier("dmg-base", itemToEquip.getOid(), damage, false); // Damage is added on
		      	    Log.debug("Equip Item: "+obj.getName()+" dmg-base "+damage +" to equip "+ itemToEquip.getName());
		    	int damageMax = (Integer) itemToEquip.getProperty("damageMax");
		    	if (damageMax < damage) damageMax = damage;
		    //	obj.statAddModifier("dmg-max", itemToEquip.getOid(), damageMax, false); // Damage is added on
				stats.put("dmg-base", damage);
				stats.put("dmg-max", damageMax);
			}
			for (String statName: stats.keySet()) {
				int value = stats.get(statName);
				Log.debug("EQUIP: item:"+itemToEquip+" equipping stat: " + statName + " by: " + value);
			}
			int gear_score = itemToEquip.getIntProperty("gearScore");
		//	HashMap<String, Integer> stats = (HashMap<String, Integer>) itemToUnequip.getProperty("bonusStats");
			int enchantLevel = (int) itemToEquip.getProperty("enchantLevel");
			if (enchantLevel > 0) {
				EnchantProfile ep = ObjectManagerClient.getEnchantProfile((int) itemToEquip.getProperty("enchantProfileId"));
				if (ep.GetLevels().containsKey(enchantLevel)) {
					for (int e = 1; e <= enchantLevel; e++) {
						gear_score += ep.GetLevels().get(e).GetGearScoreValue() + gear_score * ep.GetLevels().get(e).GetGearScoreValuePercentage();
						if (ep.GetLevels().get(e).GetAllStats()) {
							for (String stat : stats.keySet()) {
								if (ep.GetLevels().get(e).GetPercentage()) {
									stats.replace(stat, stats.get(stat) + (int)(stats.get(stat) * ep.GetLevels().get(e).GetStatValue()));
								} else {
									stats.replace(stat, stats.get(stat) + (int)ep.GetLevels().get(e).GetStatValue());
								}
							}
						} else {
							HashMap<String, EnchantStat> enchLevStats = ep.GetLevels().get(e).GetStats();
							for (String stat : enchLevStats.keySet()) {
								if (ep.GetLevels().get(e).GetAddNotExist() && !stats.containsKey(stat)) {
									stats.put(stat, enchLevStats.get(stat).GetValue() + (int)(enchLevStats.get(stat).GetValue() * enchLevStats.get(stat).GetValuePercentage()));
								} else if (stats.containsKey(stat)) {
									stats.replace(stat, stats.get(stat) + enchLevStats.get(stat).GetValue()
											+ (int)((stats.get(stat) + enchLevStats.get(stat).GetValue()) * enchLevStats.get(stat).GetValuePercentage()));
								}
							}
						}
					}
				}
			}
			for (String statName: stats.keySet()) {
				int value = stats.get(statName);
				Log.debug("EQUIP: item:"+itemToEquip+" equipping with enchant stat: " + statName + " by: " + value);
			}
			itemToEquip.setProperty("enchantStats", stats);
			
			for (String statName : CombatPlugin.STAT_LIST) {
				if (!stats.containsKey(statName)) {
					obj.statRemoveModifier(statName, itemToEquip.getOid(), false);
				} else {
					int value = stats.get(statName);
					obj.statReapplyModifier(statName, itemToEquip.getOid(), value, false);
					stats.remove(statName);
				}
			}
		
			
			for (String statName: stats.keySet()) {
				int value = stats.get(statName);
				Log.debug("EQUIP: equipping item altering stat: " + statName + " by: " + value);
				obj.statAddModifier(statName, itemToEquip.getOid(), value, false);
				//TODO: Update vitality stats based on the mods?
			}
			
			if (itemType.equals("Weapon")) {
				int speed = (Integer) itemToEquip.getProperty("delay");
		    	int speed_mod = speed - obj.statGetBaseValue(CombatPlugin.ATTACK_SPEED_STAT);
				Log.debug("SPEED: weapon speed: " + speed + " and player speed: " + obj.statGetBaseValue(CombatPlugin.ATTACK_SPEED_STAT) + " gives mod: " + speed_mod);
		    	obj.statAddModifier(CombatPlugin.ATTACK_SPEED_STAT, itemToEquip.getOid(), speed_mod, false);
			}
			HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) itemToEquip.getProperty("sockets");
    		Set<Integer> keys = itemSockets.keySet();
    		Log.debug("Equip: item:"+itemToEquip+" sokets:"+keys);
    		for (Integer i : keys) {
    			if (itemSockets.get(i).GetItemOid()!=null) {
    			//	AgisItem ai = AgisInventoryPlugin.getAgisItem(itemSockets.get(i).GetItemOid());
    			//	if (ai!=null) {
    				//	HashMap<String, Integer> aistats = (HashMap) ai.getProperty("bonusStats");
    				HashMap<String, Integer> aistats = (HashMap) AgisInventoryClient.getItemProperty(obj.getOid(), itemSockets.get(i).GetItemOid(), "bonusStats");
    				int gearScore = (int) AgisInventoryClient.getItemProperty(obj.getOid(), itemSockets.get(i).GetItemOid(), "gearScore");
    				gear_score += gearScore;
					Log.debug("EQUIP:equipping item altering stat aistats: " + aistats +" Item in socket "+itemSockets.get(i).GetItemOid());
    				if (aistats!=null) {
    				
			
    					for (String statName : CombatPlugin.STAT_LIST) {
    						if (!aistats.containsKey(statName)) {
    							obj.statRemoveModifier(statName, itemSockets.get(i).GetItemOid(), false);
    						} else {
    							int value = aistats.get(statName);
    							obj.statReapplyModifier(statName, itemSockets.get(i).GetItemOid(), value, false);
    							aistats.remove(statName);
    						}
    					}
    					
    					for (String statName: aistats.keySet()) {
    						int value = aistats.get(statName);
    						Log.error("EQUIP: equipping item altering stat: " + statName + " by: " + value);
    						obj.statAddModifier(statName, itemSockets.get(i).GetItemOid(), value, false);
    						//TODO: Update vitality stats based on the mods?
    					}
    					
    				}else {
    					Log.debug("EQUIP: equipping item socketing stats is null");
    					
    				}
    			}else {
					Log.debug("EQUIP: equipping item socketing itemOID is null");
					
				}
    		}
    		ArrayList<BonusSettings> bonuses =  (ArrayList<BonusSettings>)itemToEquip.getProperty("bonuses");
    		if( bonuses.size() > 0)
    			BonusClient.sendBonusAdd(oid, bonuses, "Item"+itemToEquip.getOid());
		
			//Apply Set
		
    	setId = itemToEquip.getIntProperty("item_set");
		obj.statAddModifier("gearScore", itemToEquip.getOid(),gear_score, false);
		
		}
		Log.debug("EQUIP: setId:" + setId);
		if (setId > 0) {
			HashMap<Integer, Integer> itemSets = (HashMap) AgisInventoryClient.getSetsEquiped(obj.getOid());
			Log.debug("EQUIP: itemSets:" + itemSets);
			// for (Integer set : itemSets.keySet()) {
			obj.statRemoveModifier("dmg-base", "ItsemSet" + setId, false);
			obj.statRemoveModifier("dmg-max", "ItsemSet" + setId, false);
			obj.statRemovePercentModifier("dmg-base", "ItsemSet" + setId, false);
			obj.statRemovePercentModifier("dmg-max", "ItsemSet" + setId, false);
			for (String statName : CombatPlugin.STAT_LIST) {
				// if (stats.containsKey(statName)) {
				obj.statRemoveModifier(statName, "ItsemSet" + setId, false);
				obj.statRemovePercentModifier(statName, "ItsemSet" + setId, false);
				// }
			}
			Log.debug("EQUIP: stats cleared for set:" + setId);

			HashMap<String, Integer> setStats = new HashMap<String, Integer>();
			HashMap<String, Integer> setStatsp = new HashMap<String, Integer>();
			ItemSetProfile isp = ObjectManagerClient.getItemSetProfile(setId);
			Log.debug("EQUIP: setId:" + setId + " ItemSetProfile:" + isp + " levels:" + isp.GetLevels().size());

			for (ItemSetLevel isl : isp.GetLevels()) {
				Log.debug("EQUIP: ItemSetLevel:" + isl);
				if (itemSets.containsKey(setId))
				{
					if (isl.GetNumberOfParts() <= itemSets.get(setId)) 
					{
					HashMap<String, EnchantStat> setLevStats = isl.GetStats();
					for (String stat : setLevStats.keySet()) {
						if (!setStats.containsKey(stat)) {
							setStats.put(stat, setLevStats.get(stat).GetValue());
							setStatsp.put(stat, (int)setLevStats.get(stat).GetValuePercentage());
						} else if (setStats.containsKey(stat)) {
							setStats.replace(stat, setStats.get(stat) + setLevStats.get(stat).GetValue());
							setStatsp.replace(stat, setStatsp.get(stat) + (int)setLevStats.get(stat).GetValuePercentage());
							}
						}
					}
				}
			}
			for (String statName : setStats.keySet()) {
				int value = setStats.get(statName);
				Log.debug("EQUIP: Item Set altering stat: " + statName + " by: " + value);
				if (value > 0) {
					obj.statAddModifier(statName, "ItsemSet" + setId, value, false);
					Log.debug("EQUIP: Item Set altered stat: " + statName + " by: " + value);
				}
			}
			for (String statName : setStatsp.keySet()) {
				int value = setStatsp.get(statName);
				Log.debug("EQUIP: item Set altering percent stat: " + statName + " by: " + value);
				if (value > 0) {
					obj.statAddPercentModifier(statName, "ItsemSet" + setId, value, false);
					Log.debug("EQUIP: Item Set altered percent stat: " + statName + " by: " + value);
				}
			}
			// }
		}
		
		Log.debug("Equip Helper itemToEquip="+itemToEquip+" set weaponType="+weaponType);
		EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, "weaponType", weaponType);
		Log.debug("Equip Helper itemToEquip="+itemToEquip+" set toolType="+toolType);
		EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, "toolType", toolType);
		
		int g = obj.statGetCurrentValue("gearScore");
		AgisInventoryPlugin.addListsRankingData(obj.getOid(), AchievementsClient.GEAR_SCORE, g);
		obj.statSendUpdate(false); // Was set to true, see how this goes
		Log.debug("EquipHelper.UpdateEquiperStats oid="+oid+" END");
	}
	
	/**
	 * Sets the displayID on the player for the specified slot.
	 * @param mobOid
	 * @param displayVal
	 * @param slot
	 */
	public static void updateDisplay(OID mobOid, String displayVal, AgisEquipSlot slot) {
		updateDisplay(mobOid,displayVal, slot,"");
	}
	
	public static void updateDisplay(OID mobOid, String displayVal, AgisEquipSlot slot, String itemVal) {
		Log.debug("updateDisplay: oid=" + mobOid + " slot=" + slot + " displayVal=" + displayVal + " itemVal=" + itemVal);

		EnginePlugin.setObjectPropertiesNoResponse(mobOid, CombatClient.NAMESPACE, slot.getName() + "DisplayVAL", itemVal);
		EnginePlugin.setObjectPropertiesNoResponse(mobOid, CombatClient.NAMESPACE, slot.getName() + "DisplayID", displayVal);
		
	}
}
