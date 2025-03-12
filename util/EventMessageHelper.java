package atavism.agis.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import atavism.server.engine.*;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

/**
 * Helper class that contains static functions for sending event messages down to the client.
 * @author Andrew Harrison
 *
 */
public class EventMessageHelper {
	
	/**
	 * Sends a Combat Event to the client allowing it to add an entry to the combat log and be used
	 * for any other display purposes.
	 * @param casterOID
	 * @param targetOID
	 * @param eventType
	 * @param val
	 */
	public static void SendCombatEvent(OID casterOID, OID targetOID, String eventType, int abilityID, int effectID, int val, int val2) {
		SendCombatEvent( casterOID,  targetOID,  eventType,  abilityID,  effectID,  val,  val2,"","");
	}
	
	public static void SendCombatEvent(OID casterOID, OID targetOID, String eventType, int abilityID, int effectID, int val, int val2, String val3, String val4) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "combat_event");
        props.put("event", eventType);
        props.put("caster", casterOID);
        props.put("target", targetOID);
        props.put("abilityID", abilityID);
        props.put("effectID", effectID);
        props.put("value1", val);
        props.put("value2", val2);
        props.put("value3", val3);
        props.put("value4", val4);
        TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, casterOID, targetOID, false, props);
        Engine.getAgent().sendBroadcast(msg);
        if (!casterOID.equals(targetOID)) {
        	msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, targetOID, casterOID, false, props);
        	Engine.getAgent().sendBroadcast(msg);
        }
        Log.debug("EventMessageHelper.SendCombatEvent Event: " + eventType);
	}

	public static void SendCombatEvent(OID casterOID, OID targetOID, String eventType, int abilityID, int effectID, int val, int val2, String val3, String val4 ,OID sendTo) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "combat_event");
		props.put("event", eventType);
		props.put("caster", casterOID);
		props.put("target", targetOID);
		props.put("abilityID", abilityID);
		props.put("effectID", effectID);
		props.put("value1", val);
		props.put("value2", val2);
		props.put("value3", val3);
		props.put("value4", val4);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, sendTo, sendTo, false, props);
    	Engine.getAgent().sendBroadcast(msg);
		Log.debug("EventMessageHelper.SendCombatEvent Event: " + eventType);
	}



//	public static final String COMBAT_PHYSICAL_DAMAGE = "CombatPhysicalDamage";
//	public static final String COMBAT_MAGICAL_DAMAGE = "CombatMagicalDamage";
//	public static final String COMBAT_PHYSICAL_CRITICAL = "CombatPhysicalCritical";
//	public static final String COMBAT_MAGICAL_CRITICAL = "CombatMagicalCritical";
	
	public static final String COMBAT_DAMAGE = "CombatDamage";
	public static final String COMBAT_DAMAGE_CRITICAL = "CombatDamageCritical";
	
	public static final String COMBAT_HEAL = "CombatHeal";
	public static final String COMBAT_HEAL_CRITICAL = "CombatHealCritical";
	public static final String COMBAT_HEALTH_TRANSFER = "CombatHealthTransfer";
	public static final String COMBAT_REVIVED = "CombatRevived";
	public static final String COMBAT_MISSED = "CombatMissed";
	public static final String COMBAT_DODGED = "CombatDodged";
	public static final String COMBAT_BLOCKED = "CombatBlocked";
	public static final String COMBAT_PARRIED = "CombatParried";
	public static final String COMBAT_EVADED = "CombatEvaded";
	public static final String COMBAT_IMMUNE = "CombatImmune";
    public static final String COMBAT_RESISTED = "CombatResisted";
    public static final String COMBAT_BUFF_GAINED = "CombatBuffGained";
	public static final String COMBAT_DEBUFF_GAINED = "CombatDebuffGained";
	public static final String COMBAT_BUFF_LOST = "CombatBuffLost";
	public static final String COMBAT_DEBUFF_LOST = "CombatDebuffLost";
	public static final String COMBAT_COOLDOWN_EXTENDED = "CombatCooldownExtended";
	public static final String COMBAT_REPUTATION_CHANGED = "CombatReputationChanged";
	public static final String COMBAT_EXP_GAINED = "CombatExpGained";
	public static final String COMBAT_ABILITY_LEARNED = "CombatAbilityLearned";
	public static final String COMBAT_CASTING_STARTED = "CastingStarted";
	public static final String COMBAT_CASTING_CANCELLED = "CastingCancelled";
	
	/**
	 * Sends an Inventory Event to the client allowing it to display the information if wanted.
	 * @param playerOid
	 * @param eventType
	 * @param itemID
	 * @param count
	 * @param data
	 */
	public static void SendInventoryEvent(OID playerOid, String eventType, int itemID, int count, String data) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "inventory_event");
		props.put("event", eventType);
		props.put("itemID", itemID);
		props.put("count", count);
		props.put("data", data);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Log.debug("EventMessageHelper.SendInventoryEvent Event: " + eventType + " with item: " + itemID);
		Engine.getAgent().sendBroadcast(msg);
	}

	public static final String ITEM_HARVESTED = "ItemHarvested";
	public static final String ITEM_LOOTED = "ItemLooted";
	
	/**
	 * Not used yet. To replace sendAnnouncementMessage
	 * @param playerOid
	 * @param eventType
	 * @param val
	 * @param data
	 */
	public static void SendGeneralEvent(OID playerOid, String eventType, int val, String data) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "general_event");
		props.put("event", eventType);
		props.put("val", val);
		props.put("data", data);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Log.debug("EventMessageHelper.SendGeneralEvent Event: " + eventType);
		Engine.getAgent().sendBroadcast(msg);
	}
	
	public static void SendReputationChangedEvent(OID playerOid, String eventType, int val, String name, String data) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "general_event");
		props.put("event", eventType);
		props.put("val", val);
		String[] dataParts = data.split(" ");
		props.put("data", dataParts[1]);
		props.put("newReputation", dataParts[0]);
		props.put("name", name);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Log.debug("EventMessageHelper.SendGeneralEvent Event: " + eventType);
		Engine.getAgent().sendBroadcast(msg);
	}
	
	public static final String REPUTATION_CHANGED = "ReputationChanged";
	public static final String REPAIR_SUCCESSFUL = "RepairSuccessful";
	public static final String DUEL_COUNTDOWN = "DuelCountdown";
	public static final String DUEL_START = "DuelStart";
	public static final String DUEL_VICTORY = "DuelVictory";
	public static final String DUEL_DEFEAT = "DuelDefeat";
	public static final String DUEL_OUT_OF_BOUNDS = "DuelOutOfBounds";
	public static final String DUEL_NOT_OUT_OF_BOUNDS = "DuelNotOutOfBounds";
	public static final String STAT_INCREASE = "StatIncrease";
	public static final String STAT_DECREASE = "StatDecrease";
	public static final String CLAIM_PURCHASED = "ClaimPurchased";
	public static final String GUILD_MEMBER_JOINED = "GuildMemberJoined";
	public static final String GUILD_MEMBER_LEFT = "GuildMemberLeft";
	public static final String ERROR_GUILD_RANK_NO_DELETE_IS_MEMBER = "GuildRankNoDeleteIsMember";
	public static final String GUILD_MASTER_NO_LEAVE = "GuildMasterNoLeave";
	public static final String GUILD_NO_DEMOTE = "GuildNoDemote";
	public static final String GUILD_NO_PROMOTE = "GuildNoPromote";
	public static final String SOCIAL_PLAYER_OFFLINE = "SocialPlayerOffline";
	
	
	
	/**
	 * Not used yet. To replace sendErrorMessage
	 * @param playerOid
	 * @param eventType
	 * @param val
	 * @param data
	 */
	public static void SendErrorEvent(OID playerOid, String eventType, int val, String data) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "error_event");
		props.put("event", eventType);
		props.put("val", val);
		props.put("data", data);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Log.debug("EventMessageHelper.SendErrorEvent Event: " + eventType);
		Engine.getAgent().sendBroadcast(msg);
	}
	public static final String AUCTION_OWN_LIMIT = "AuctionOwnLimit";
	
	public static final String ERROR_DEAD = "ErrorDead";
	public static final String NOT_ENOUGH_CURRENCY = "NotEnoughCurrency";
	public static final String INVALID_TRADE_CURRENCY = "InvalidTradeCurrency";
	public static final String NO_ITEM_DURABILITY = "NoItemDurability";
	public static final String SKILL_MISSING = "SkillMissing";
	public static final String SKILL_LEVEL_TOO_LOW = "SkillLevelTooLow";
	public static final String CRAFTING_STATION_ERROR = "CraftingStationError";
	public static final String EQUIP_MISSING = "EquipMissing";
	public static final String EQUIP_FAIL_ITEM_UNIQUE = "EquipFailItemUnique";
	public static final String EQUIP_FAIL = "EquipFail";
	public static final String TOO_FAR_AWAY = "TooFarAway";
	public static final String RESOURCE_NODE_BUSY = "ResourceNodeBusy";
	public static final String RESOURCE_HARVEST_FAILED = "ResourceHarvestFailed";
	public static final String INSTANCE_REQUIRES_GROUP = "InstanceRequiresGroup";
	public static final String INSTANCE_REQUIRES_GUILD = "InstanceRequiresGuild";
	public static final String SKILL_ALREADY_KNOWN = "SkillAlreadyKnown";
	public static final String CANNOT_SELL_ITEM = "CannotSellItem";
	public static final String INVENTORY_FULL = "InventoryFull";
	public static final String STORAGE_NOT_EMPTY = "StorageNotEmpty";
	public static final String ERROR_MOUNTED = "ErrorMounted";
	public static final String INVALID_ITEM = "InvalidItem";
	public static final String MISSING_ITEM = "MissingItem";
	public static final String INSUFFICIENT_CLAIM_OBJECT_ITEMS = "InsufficientClaimObjectItems";
	public static final String ERROR_IN_COMBAT = "ErrorInCombat";
	public static final String ERROR_INSUFFICIENT_PERMISSION = "ErrorInsufficientPermission";
	public static final String ERROR_ALREADY_IN_GUILD = "ErrorAlreadyInGuild";
	public static final String ERROR_PLAYER_ON_BLOCK_LIST = "ErrorPlayerOnBlockList";
	public static final String ERROR_PLAYER_ON_YOUR_BLOCK_LIST = "ErrorPlayerYourOnBlockList";
	public static final String ERROR_NO_EQUIP_SLOT = "ErrorNoEquipSlot";
	public static final String ERROR_WRONG_EQUIP_SLOT = "ErrorWrongEquipSlot";
	public static final String SOCKETING_FAILED = "SocketingFail";
	public static final String SOCKET_RESET_FAILED = "SocketResetFail";
	public static final String SOCKETING_SUCCESS = "SocketingSuccess";
	public static final String SOCKET_RESET_SUCCESS = "SocketResetSuccess";
	public static final String SOCKETING_INTERRUPT = "SocketingInterrupt";
	public static final String SOCKET_RESET_INTERRUPT = "SocketResetInterrupt";
	public static final String ENCHANTING_FAILED = "EnchantingFail";
	public static final String ENCHANTING_SUCCESS = "EnchantingSuccess";
	public static final String ENCHANTING_INTERRUPT = "EnchantingInterrupt";
	public static final String ERROR_GUILD_MEMBER_LIMIT = "ErrorGuildMemberLimit";
	public static final String PLAYER_SHOP_LIMIT = "PlayerShopLimit";
	public static final String OWNER_CANT_BUY_SELL = "OwnerCantBuySell";
	public static final String PLAYER_SHOP_WRONG_INSTANCE_TYPE = "PlayerShopWrongInstanceType";
	
	
	public static void SendRequirementFailedEvent(OID playerOid, RequirementCheckResult result) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "requirement_failed_event");
		props.put("event", result.result);
		props.put("val", result.numericData);
		props.put("data", result.stringData);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Log.debug("EventMessageHelper.SendRequirementFailedEvent Event: " + result.result);
		Engine.getAgent().sendBroadcast(msg);
	}
	
	public static void SendQuestEvent(OID playerOid, String eventType, String data, int val1, int val2, int val3) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "quest_event");
		props.put("event", eventType);
		props.put("val1", val1);
		props.put("val2", val2);
		props.put("val3", val3);
		props.put("data", data);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

	public static final String QUEST_PROGRESS = "QuestProgress";
}