package atavism.agis.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import atavism.agis.core.Agis;
import atavism.agis.core.AgisAbility.AbilityResult;
import atavism.agis.objects.CombatInfo;
import atavism.agis.objects.Currency;
import atavism.agis.objects.InventoryInfo;
import atavism.agis.objects.SkillInfo;
import atavism.agis.objects.SkillData;
import atavism.agis.plugins.AgisInventoryPlugin;
import atavism.agis.plugins.ClassAbilityPlugin;
import atavism.server.engine.*;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;
import atavism.server.util.Log;

public class ExtendedCombatMessages {

	/**
	 * Sends a message to the client to display the combat event that occured to this person.
	 * @param oid
	 * @param dmg
	 * @param dmgType
	 */
	public static void sendCombatText(OID oid, String dmg, int dmgType) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "combat_text");
		props.put("DmgAmount", dmg);
		props.put("DmgType", dmgType);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		Log.debug("ECM: Sending combat text with value " + dmg + " and type: " + dmgType);
		Engine.getAgent().sendBroadcast(msg);
	}
	
	public static void sendCombatChat(CombatInfo obj, String cmsg, String msgType) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "combat_chat");
        props.put("Msg", cmsg);
        props.put("MsgType", msgType);
        TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION,
        		obj.getOwnerOid(), obj.getOwnerOid(), false, props);
	    Engine.getAgent().sendBroadcast(msg);
	}
	
	/**
	 * Sends a message to the client to display the combat event on the target to the caster.
	 * @param caster
	 * @param target
	 * @param dmg
	 * @param dmgType
	 */
	public static void sendCombatText2(OID caster, OID target, String dmg, int dmgType) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "combat_text2");
        props.put("DmgAmount", dmg);
        props.put("DmgType", dmgType);
        props.put("target", target);
        TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION,
        		caster, caster, false, props);
	    Engine.getAgent().sendBroadcast(msg);
	}
	
	public static void sendAbilityFailMessage(CombatInfo obj, AbilityResult result, int abilityID, String prop) {
        int failmessageType = 0;
        if (result == AbilityResult.INVALID_TARGET)
        	failmessageType = 1;
        else if (result == AbilityResult.OUT_OF_RANGE)
        	failmessageType = 2;
        else if (result == AbilityResult.TOO_CLOSE)
        	failmessageType = 3;
        else if (result == AbilityResult.BUSY || result == AbilityResult.NOT_READY)
        	failmessageType = 4;
        else if (result == AbilityResult.INSUFFICIENT_ENERGY)
        	failmessageType = 5;
        else if (result == AbilityResult.MISSING_REAGENT)
        	failmessageType = 6;
        else if (result == AbilityResult.MISSING_TOOL)
        	failmessageType = 7;
        else if (result == AbilityResult.MISSING_AMMO)
        	failmessageType = 8;
        //else if (result == AbilityResult.WRONG_STANCE)
        //	failmessageType = 9;
        else if (result == AbilityResult.INSUFFICIENT_VIGOR)
        	failmessageType = 12;
        else if (result == AbilityResult.EFFECT_MISSING)
        	failmessageType = 13;
        else if (result == AbilityResult.NO_TARGET)
        	failmessageType = 14;
        else if (result == AbilityResult.MISSING_WEAPON)
        	failmessageType = 15;
        else if (result == AbilityResult.PASSIVE)
        	failmessageType = 16;
        else if (result == AbilityResult.INTERRUPTED)
        	failmessageType = 17;
        else if (result == AbilityResult.DEAD)
        	failmessageType = 18;
        else if (result == AbilityResult.NOT_IN_FRONT)
        	failmessageType = 19;
		else if (result == AbilityResult.NOT_BEHIND)
			failmessageType = 20;
		else if (result == AbilityResult.OUT_OF_LOS)
			failmessageType = 21;
		else if (result == AbilityResult.NOT_DEAD)
			failmessageType = 22;
		else if (result == AbilityResult.NOT_SPIRIT)
			failmessageType = 23;
		else if (result == AbilityResult.NOT_IN_COMBAT)
			failmessageType = 24;
		else if (result == AbilityResult.IN_COMBAT)
			failmessageType = 25;
		else if (result == AbilityResult.PET_GLOBAL_LIMIT)
			failmessageType = 26;
		else if (result == AbilityResult.PET_TYPE_LIMIT)
			failmessageType = 27;
		Log.debug("ABILITY: sending fail message: " + failmessageType);
        
        Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "ability_error");
		props.put("AbilityID", abilityID);
		props.put("ErrorText", failmessageType);
		props.put("data", prop);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, obj.getOwnerOid(), obj.getOwnerOid(), false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

	public static void sendErrorMessage(OID oid, String message) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "error_message");
		props.put("ErrorText", message);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		if (Log.loggingTrace)
			Log.trace("sendErrorMessage to " + oid + " " + props);

		Engine.getAgent().sendBroadcast(msg);
		// Engine.getAgent().sendBroadcastRPC(message, callback)
	}

	public static void sendItemBroken(OID oid, int itemId) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "item_broken");
		props.put("item", itemId);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		if (Log.loggingTrace)
			Log.trace("sendErrorMessage to " + oid + " " + props);

		Engine.getAgent().sendBroadcast(msg);
		// Engine.getAgent().sendBroadcastRPC(message, callback)
	}


	public static void sendCooldownMessage(OID oid, String type, long length, long startTime) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "cooldown");
		props.put("CdType", type);
		props.put("CdLength", length);
		props.put("CdStart", startTime);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		if (Log.loggingTrace)
			Log.trace("sendCooldownMessage " + type + "  " + length + " to " + oid + " " + props);
		Engine.getAgent().sendBroadcast(msg);
	}

	/**
	 * Unused.
	 * @param obj
	 * @param type
	 * @param length
	 */
	public static void sendEffectMessage(CombatInfo obj, int type, long length) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "effect");
		props.put("EffectType", type);
		props.put("EffectLength", length);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, obj.getOwnerOid(), obj.getOwnerOid(), false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

	public static void sendAnouncementMessage(OID oid, String message, String type) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "announcement");
		props.put("AnnouncementText", message);
		props.put("AnnouncementType", type);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}
	public static void sendAnouncementSpecialMessage(OID oid, String message, String type) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "announcement_special");
		props.put("AnnouncementText", message);
		props.put("AnnouncementType", type);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

	public static void sendAbilities(OID oid, Collection<Integer> abilities) {
		Log.debug("sendAbilities");
		TargetedPropertyMessage propMsg = new TargetedPropertyMessage(oid, oid);
		propMsg.setProperty("abilities", new ArrayList<>(abilities));
    	Log.debug("SKILL: sendAbilities props= " + propMsg );
    	    Engine.getAgent().sendBroadcast(propMsg);
	}
	
	public static void sendExtraAbilities(OID oid, Collection<Integer> abilities) {
		Log.debug("sendAbilities");
		TargetedPropertyMessage propMsg = new TargetedPropertyMessage(oid, oid);
		propMsg.setProperty("extabilities", new ArrayList<>(abilities));
    	Log.debug("SKILL: sendAbilities props= " + propMsg );
    	    Engine.getAgent().sendBroadcast(propMsg);
	}

	public static void sendCombo(OID oid, int abilityId, int comboId, float time, boolean reset, boolean showCenterUi, boolean replaceSlot){
		Log.info("ACTIONS: sending combo message to OID: " + oid );
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "comboAction");
		props.put("aId", abilityId);
		props.put("cId", comboId);
		props.put("scu", showCenterUi);
		props.put("rs", replaceSlot);
		props.put("time", (int)(time * 1000));
		props.put("r",reset);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}
	public static void sendCombo(OID oid, int abilityId, boolean reset, int[] comboIds, float[] times){
		Log.info("ACTIONS: sending combos message to OID: " + oid );
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "comboActions");
		props.put("aId", abilityId);
		int c = 0;
		for (int comboId : comboIds ) {
			props.put("cId"+c, comboId);
			props.put("t"+c, (int)(times[c] * 1000));
			c++;
		}
		props.put("num", c);
		props.put("r",reset);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}
	public static void sendActions(OID oid, ArrayList<ArrayList<String>> actions, int currentActionBar) {
		//TargetedPropertyMessage propMsg = new TargetedPropertyMessage(oid, oid);
		Log.info("ACTIONS: sending action message to OID: " + oid );//+ " with actions: " + actions);
    	/*propMsg.setProperty("actions", actions.get(currentActionBar));
        Engine.getAgent().sendBroadcast(propMsg);
        // Also send current action bar
        sendCurrentActionBar(oid, currentActionBar);*/
        
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "actions");
        props.put("numBars", actions.size());
        int barNum = 0;
        //Log.debug("SKILL: got " + skillInfo.getSkills().size() + " skills to send down");
        for (ArrayList<String> actionList : actions) {
        	int barActionCount = 0;
        	for (String action : actionList) {
        		props.put("bar" + barNum + "action" + barActionCount, action);
        		barActionCount++;
        	}
        	props.put("barActionCount" + barNum, barActionCount);
        	barNum++;
        }
        
        props.put("currentBar", currentActionBar);
        
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		   Engine.getAgent().sendBroadcast(msg);
	}
	
	/*public static void sendCurrentActionBar(OID oid, int currentActionBar) {
		TargetedPropertyMessage propMsg = new TargetedPropertyMessage(oid, oid);
    	propMsg.setProperty("currentActionBar", currentActionBar);
        Engine.getAgent().sendBroadcast(propMsg);
	}*/
	
	public static void sendSkills(OID oid, SkillInfo skillInfo) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "skills");
		props.put("skillPoints", skillInfo.getSkillPoints());
		props.put("talentPoints", skillInfo.getTalentPoints());
		int totalPoints = skillInfo.getSkillPoints() + skillInfo.getPointsSpent();
		props.put("totalSkillPoints", totalPoints);
		props.put("totalTalentPoints", skillInfo.getTalentPoints() + skillInfo.getTalentPointsSpent());
		props.put("skillPointCost", ClassAbilityPlugin.GetSkillPointCost(totalPoints));
		int numSkills = 0;
		// Log.debug("SKILL: got " + skillInfo.getSkills().size() + " skills to send down");
		for (SkillData skillData : skillInfo.getSkills().values()) {
			props.put("skill" + numSkills + "ID", skillData.getSkillID());
			props.put("skill" + numSkills + "Name", skillData.getSkillName());
			props.put("skill" + numSkills + "Current", skillData.getSkillCurrent());
			props.put("skill" + numSkills + "Level", skillData.getSkillLevel());
			props.put("skill" + numSkills + "Max", skillData.getSkillMaxLevel());
			props.put("skill" + numSkills + "State", skillData.getState());
			props.put("skill" + numSkills + "Xp", skillData.getExperience());
			props.put("skill" + numSkills + "XpMax", skillData.getExperienceMax());
			numSkills++;
		}

		props.put("numSkills", numSkills);
		Log.debug("SKILL: sendSkills props= " + props );
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

	/**
	 * This should be moved to another class.
	 * @param oid
	 * @param currencies
	 */
	public static void sendCurrencies(OID oid, HashMap<Integer, Long> currencies) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "currencies");
		InventoryInfo iInfo = AgisInventoryPlugin.getInventoryInfo(oid);
		if(iInfo==null) {
			Log.error("sendCurrencies can not found InventoryInfo for "+oid);
			return;
		}
		float bonusModp = 0;
		if (iInfo.getBonuses().containsKey("SellFactor")) {
			bonusModp = iInfo.getBonuses().get("SellFactor").GetValuePercentage();
		}
		if(AgisInventoryPlugin.globalEventBonusesArray.containsKey("SellFactor")) {
			bonusModp += AgisInventoryPlugin.globalEventBonusesArray.get("SellFactor").GetValuePercentage();
  		}
		props.put("SellFactor", AgisInventoryPlugin.SELL_FACTOR + (AgisInventoryPlugin.SELL_FACTOR * bonusModp / 100f));
		int numCurrencies = 0;
		OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, WorldManagerClient.ACCOUNT_PROPERTY);
		for (int currencyID : currencies.keySet()) {
			Currency currency = Agis.CurrencyManager.get(currencyID);
			if (currency != null) {
				// Log.debug("CURRENCY: got currency to send: " + currencyID);
				props.put("currency" + numCurrencies + "ID", currency.getCurrencyID());
				//props.put("currency" + numCurrencies + "Name", currency.getCurrencyName());
				//props.put("currency" + numCurrencies + "Icon", currency.getCurrencyIcon());
				if (currency.getExternal()) {
					props.put("currency" + numCurrencies + "Current", AgisInventoryPlugin.authDB.getAccountCoinAmount(accountID));
					// props.put("currency" + numCurrencies + "Current", currencies.get(currencyID));
				} else {
					props.put("currency" + numCurrencies + "Current", currencies.get(currencyID).longValue());
				}
				numCurrencies++;
			} else {
				Log.error("sendCurrencies: oid:" + oid + " cant found currencyID=" + currencyID + " deleted?");
			}
		}

		props.put("numCurrencies", numCurrencies);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("AJCURRENCY: sending down currencies message to: " + oid + " with props: " + props);
	}
}