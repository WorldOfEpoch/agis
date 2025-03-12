package atavism.agis.plugins;

import java.time.Duration;
import java.util.*;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;
import java.util.function.*;

import atavism.agis.server.combat.DmgBaseStat;
import atavism.agis.server.combat.ExperienceStat;
import atavism.msgsys.*;
import atavism.server.math.AOVector;
import atavism.server.telemetry.Prometheus;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.plugins.ObjectManagerClient.GenerateSubObjectMessage;
import atavism.server.math.Point;
import atavism.server.messages.*;
import atavism.agis.abilities.FriendlyEffectAbility;
import atavism.agis.core.*;
import atavism.agis.core.AgisAbility.AbilityResult;
import atavism.agis.core.AgisEffect.EffectState;
import atavism.agis.core.Cooldown.CooldownObject;
import atavism.agis.core.Cooldown.State;
import atavism.agis.database.AccountDatabase;
import atavism.agis.database.CombatDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.database.ItemDatabase;
import atavism.agis.effects.MountEffect;
import atavism.agis.effects.StealthEffect;
import atavism.agis.objects.*;
import atavism.agis.plugins.CombatClient.DelModifyStatsMessage;
import atavism.agis.plugins.CombatClient.GetCombatObjectParamsMessage;
import atavism.agis.plugins.CombatClient.ModifyStatsMessage;
import atavism.agis.plugins.CombatClient.SetCombatObjectParamsMessage;
import atavism.agis.plugins.CombatClient.SetWmgrStatsMessage;
import atavism.agis.plugins.GroupClient.GroupInfo;
import atavism.agis.plugins.BonusClient.BonusesUpdateMessage;
import atavism.agis.plugins.BonusClient.GlobalEventBonusesUpdateMessage;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.ObjectInfo;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.plugins.WorldManagerPlugin.HostInstanceFilter;
import atavism.server.plugins.WorldManagerPlugin.WorldManagerInstance;
import atavism.agis.util.*;
import atavism.management.Management;

//
// the combat plugin tracks autoattacks and resolves combat messages
//
public class CombatPlugin extends atavism.server.engine.EnginePlugin {

	public CombatPlugin() {
		super(getPluginName());
		setPluginType("Combat");
	}

    private static String getPluginName() {
        String combatAgentName;
		try {
			combatAgentName = Engine.getAgent().getDomainClient().allocName("PLUGIN", "Combat#");
		} catch (java.io.IOException e) {
			throw new AORuntimeException("Could not allocate world manager plugin name", e);
		}
        return combatAgentName;
    }

	//public static String COMBAT_PLUGIN_NAME = "Combat";

	protected static final Logger log = new Logger("CombatPlugin");

	public void onActivate() {
		try {
			log.debug("CombatPlugin.onActivate() |");
			// register for msgtype->hooks
			registerHooks();
			List<Namespace> namespaces = new LinkedList<Namespace>();
			namespaces.add(Namespace.COMBAT);
			namespaces.add(Namespace.COMBAT_INSTANCE);
			
			CombatFilter selectionFilter = new CombatFilter(getName());
            SubObjectFilter subObjectFilter = new SubObjectFilter();
			subObjectFilter.setMatchSubjects(true);
			//CombatPluginGenerateSubObjectHook
			registerPluginNamespaces(namespaces, new CombatPluginGenerateSubObjectHook()/*CombatGenerateSubObjectHook()*/, selectionFilter, subObjectFilter);
            // Remove the subscriptions as we will use SubscriptionManager instead
            Engine.getAgent().removeSubscription(subObjectSubscription);
            Engine.getAgent().removeSubscription(propertySubscription);
            
            subObjectFilterTypes.addAll(subObjectFilter.getMessageTypes());
            subObjectFilterNamespaces.addAll(subObjectFilter.getNamespaces());
			log.debug("CombatPlugin.onActivate() ||");
			
			HostInstanceFilter hostInstanceFilter = new HostInstanceFilter(getName());
			hostInstanceFilter.addType(WorldManagerClient.MSG_TYPE_HOST_INSTANCE);
			Engine.getAgent().createSubscription(hostInstanceFilter, this, MessageAgent.RESPONDER);
			log.debug("CombatPlugin.onActivate() |||");
			
			
			mobFilterTypes.add(CombatClient.MSG_CLIENT_LEVEL_LOADED);
			mobFilterTypes.add(CombatClient.MSG_TYPE_SET_COMBAT_INFO_STATE);
			mobFilterTypes.add(CombatClient.MSG_TYPE_AUTO_ATTACK);
			mobFilterTypes.add(CombatClient.MSG_TYPE_START_ABILITY);
			mobFilterTypes.add(CombatClient.MSG_TYPE_RELEASE_OBJECT);
			mobFilterTypes.add(CombatClient.MSG_TYPE_ARENA_RELEASE);
			mobFilterTypes.add(CombatClient.MSG_TYPE_SET_PLAYER_RESPAWN_LOCATION);
			mobFilterTypes.add(WorldManagerClient.MSG_TYPE_UPDATE_OBJECT);
			mobFilterTypes.add(PropertyMessage.MSG_TYPE_PROPERTY);
			mobFilterTypes.add(WorldManagerClient.MSG_TYPE_DESPAWNED);
			mobFilterTypes.add(WorldManagerClient.MSG_TYPE_SPAWNED);
			mobFilterTypes.add(CombatClient.MSG_TYPE_ADD_SKILL);
			mobFilterTypes.add(AgisInventoryClient.MSG_TYPE_ITEM_ACQUIRE_STATUS_CHANGE);
			mobFilterTypes.add(AgisInventoryClient.MSG_TYPE_ITEM_EQUIP_STATUS_CHANGE);
			mobFilterTypes.add(CombatClient.MSG_TYPE_COMBAT_STOP_AUTO_ATTACK);
		//	types.add(CombatClient.MSG_TYPE_TARGET_TYPE);
			mobFilterTypes.add(ArenaClient.MSG_TYPE_REMOVE_EFFECTS);
			mobFilterTypes.add(CombatClient.MSG_TYPE_APPLY_EFFECT);
			mobFilterTypes.add(CombatClient.MSG_TYPE_REMOVE_EFFECT);
			mobFilterTypes.add(CombatClient.MSG_TYPE_REMOVE_BUFF);
			mobFilterTypes.add(AgisMobClient.MSG_TYPE_UPDATE_PET_STATS);
			mobFilterTypes.add(CombatClient.MSG_TYPE_UPDATE_BREATH);
			mobFilterTypes.add(CombatClient.MSG_TYPE_UPDATE_FATIGUE);
			mobFilterTypes.add(CombatClient.MSG_TYPE_UPDATE_HEALTH_PROPS);
			mobFilterTypes.add(CombatClient.MSG_TYPE_REGEN_HEALTH_MANA);
			mobFilterTypes.add(CombatClient.MSG_TYPE_DISMOUNT);
			mobFilterTypes.add(CombatClient.MSG_TYPE_FALLING_EVENT);
			mobFilterTypes.add(BonusClient.MSG_TYPE_BONUSES_UPDATE);
			mobFilterTypes.add(CombatClient.MSG_TYPE_COMBAT_STATS_MODIFY);
			mobFilterTypes.add(CombatClient.MSG_TYPE_DAMAGE);
			mobFilterTypes.add(CombatClient.MSG_TYPE_SPRINT);
			mobFilterTypes.add(CombatClient.MSG_TYPE_DODGE);
			mobFilterTypes.add(CombatClient.MSG_TYPE_COMBAT_RESET_ATTACKER);
			mobFilterTypes.add(CombatClient.MSG_TYPE_COMBAT_STATE);
			mobFilterTypes.add(CombatClient.MSG_TYPE_SET_COMBAT_OBJECT_PARAMS_NONBLOCK);
			mobFilterTypes.add(CombatClient.MSG_TYPE_ABILITY_VECTOR);
			log.debug("CombatPlugin.onActivate() |V");
			
			mobRPCFilterTypes.add(CombatClient.MSG_TYPE_GET_PLAYER_COOLDOWNS);
			mobRPCFilterTypes.add(CombatClient.MSG_TYPE_GET_PLAYER_STAT_VALUE);
			mobRPCFilterTypes.add(CombatClient.MSG_TYPE_GET_COMBAT_OBJECT_PARAMS);
			mobRPCFilterTypes.add(CombatClient.MSG_TYPE_SET_COMBAT_OBJECT_PARAMS);
			mobRPCFilterTypes.add(CombatClient.MSG_TYPE_START_ABILITY_RESPONSE);
			
			MessageTypeFilter filter = new MessageTypeFilter();
			filter.addType(BonusClient.MSG_TYPE_GLOBAL_EVENT_UPDATE);
		    filter.addType(CombatClient.MSG_TYPE_TARGET_TYPE);
			filter.addType(CombatClient.MSG_TYPE_SET_WMGR_STATS);
			filter.addType(CombatClient.MSG_TYPE_DEBUG_ABILITY);
			WorldManagerClient.initCache();
			 Engine.getAgent().createSubscription(filter, this);
			
			// Create responder subscription
			MessageTypeFilter filter2 = new MessageTypeFilter();
			filter2.addType(LoginMessage.MSG_TYPE_LOGIN);
			// filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
			//filter2.addType(CombatClient.MSG_TYPE_GET_PLAYER_STAT_VALUE);
			//filter2.addType(CombatClient.MSG_TYPE_GET_PLAYER_STAT_VALUE);
			Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);
			log.debug("CombatPlugin.onActivate() V");
				//LinkedList<MessageType> types = new LinkedList<MessageType>();
			Engine.getAgent().createSubscription(new MessageTypeFilter(Management.MSG_TYPE_GET_PLUGIN_STATUS), this, MessageAgent.RESPONDER);
			log.debug("CombatPlugin.onActivate() V|");
				
			registerLoadHook(Namespace.COMBAT, new CombatLoadHook());
			log.debug("CombatPlugin.onActivate() V||");
			registerUnloadHook(Namespace.COMBAT, new CombatUnloadHook());
			log.debug("CombatPlugin.onActivate() V|||");
			//registerSaveHook(Namespace.COMBAT, new CombatSaveHook());
			log.debug("CombatPlugin.onActivate() |X");
			registerLoadHook(CombatClient.INSTANCE_NAMESPACE, new InstanceLoadHook());
			registerUnloadHook(CombatClient.INSTANCE_NAMESPACE, new InstanceUnloadHook());
			registerDeleteHook(CombatClient.INSTANCE_NAMESPACE, new InstanceDeleteHook());

		//	registerPluginNamespace(Namespace.COMBAT, new CombatPluginGenerateSubObjectHook());
		} catch (Exception e) {
			throw new AORuntimeException("onActivate failed", e);
		}

		// Load items from the database
		aDB = new AccountDatabase(true);
		log.debug("CombatPlugin.onActivate() X");

		 loadData() ;
		  
		 loadCombatDataFromDatabase();
		
		
		
		// Start combat stat tick
		// Engine.getExecutor().scheduleAtFixedRate(statTick, 10, 1,
		// TimeUnit.SECONDS);
		
        Log.debug("Registering Combat plugin");
		Engine.registerStatusReportingPlugin(this);

	}

	public void loadCombatDataFromDatabase() {
		CombatDatabase cDB = new CombatDatabase(false);
		if (STAT_LIST == null) {
			loadStatData(cDB);
		}
		
		
		// Load effects
		ArrayList<AgisEffect> effects = cDB.loadCombatEffects();
		for (AgisEffect effect : effects)
			Agis.EffectManager.register(effect.getID(), effect);
		// Abilities
		ArrayList<AgisAbility> abilities = cDB.loadAbilities();
		for (AgisAbility ability : abilities) {
			Agis.AbilityManager.register(ability.getID(), ability);
			if (Log.loggingDebug)Log.debug("ABILITY: added " + ability.getName() + " to the database.");
		}
		// Skills
		HashMap<Integer, SkillTemplate> skillTemplates = cDB.loadSkills();
		for (SkillTemplate tmpl : skillTemplates.values())
			Agis.SkillManager.register(tmpl.getSkillID(), tmpl);

		HashMap<Integer, SkillProfileTemplate> skillProfileTemplates = cDB.loadSkillProfiles();
		for (SkillProfileTemplate tmpl : skillProfileTemplates.values())
			Agis.SkillProfileManager.register(tmpl.getProfileID(), tmpl);

		//cDB.close();

		ContentDatabase ctDB = new ContentDatabase(false);
		graveyards = ctDB.loadGraveyards();
	}

	private void loadStatData(CombatDatabase cDB) {
		// Load stats
		STAT_LIST = cDB.LoadStats();
		STAT_PROFILES = cDB.LoadStatProfiles();
		// Load Damage Types
		DAMAGE_TYPES = cDB.LoadDamageTypes();
		STAT_THRESHOLDS = cDB.LoadStatThresholds();
	}

	// how to process incoming messages
	protected void registerHooks() {
		getHookManager().addHook(CombatClient.MSG_CLIENT_LEVEL_LOADED, new ClientLevelLoadedHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_GET_PLAYER_STAT_VALUE, new GetPlayerStatValueHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_GET_PLAYER_COOLDOWNS, new GetPlayerCooldownHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_SET_COMBAT_INFO_STATE, new UpdateCombatInfoStateHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_AUTO_ATTACK, new AutoAttackHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_START_ABILITY, new StartAbilityHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_START_ABILITY_RESPONSE, new StartAbilityHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_RELEASE_OBJECT, new ReleaseObjectHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_ARENA_RELEASE, new ReleaseArenaObjectHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_SET_PLAYER_RESPAWN_LOCATION, new SetRespawnLocationHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_UPDATE_OBJECT, new UpdateObjectHook());
		getHookManager().addHook(PropertyMessage.MSG_TYPE_PROPERTY, new PropertyHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_DESPAWNED, new DespawnedHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_TARGET_TYPE, new TargetTypeUpdateHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_ADD_SKILL, new AddSkillHook());
		getHookManager().addHook(AgisInventoryClient.MSG_TYPE_ITEM_ACQUIRE_STATUS_CHANGE, new ItemAcquireStatusChangeHook());
		getHookManager().addHook(AgisInventoryClient.MSG_TYPE_ITEM_EQUIP_STATUS_CHANGE, new ItemEquipStatusChangeHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_COMBAT_STOP_AUTO_ATTACK, new StopAutoAttackHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_REMOVE_EFFECTS, new RemoveArenaEffectsHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_APPLY_EFFECT, new ApplyEffectHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_REMOVE_EFFECT, new RemoveEffectHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_REMOVE_BUFF, new RemoveBuffHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_UPDATE_HEALTH_PROPS, new SetHealthPropertiesHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_REGEN_HEALTH_MANA, new RegenerateHealthHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_DISMOUNT, new DismountHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_FALLING_EVENT, new FallingEventHook());
		
		getHookManager().addHook(Management.MSG_TYPE_GET_PLUGIN_STATUS, new GetPluginStatusHook());
		
		// Hooks to process login/logout messages
		getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
		
		getHookManager().addHook(CombatClient.MSG_TYPE_GET_COMBAT_OBJECT_PARAMS, new GetCombatInfoParamsHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_SET_COMBAT_OBJECT_PARAMS, new SetCombatInfoParamsHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_SET_COMBAT_OBJECT_PARAMS_NONBLOCK, new SetCombatInfoParamsHook());
		getHookManager().addHook(BonusClient.MSG_TYPE_BONUSES_UPDATE, new BonusesUpdateHook());
        getHookManager().addHook(BonusClient.MSG_TYPE_GLOBAL_EVENT_UPDATE, new GlobalEventBonusesUpdateHook());
        getHookManager().addHook(CombatClient.MSG_TYPE_COMBAT_STATS_MODIFY, new ModifyStatsHook());
        getHookManager().addHook(CombatClient.MSG_TYPE_COMBAT_DEL_STATS_MODIFY, new DelModifyStatsHook());
    		     
        getHookManager().addHook(CombatClient.MSG_TYPE_SET_WMGR_STATS, new SetWmgrStatsHook());
        getHookManager().addHook(CombatClient.MSG_TYPE_DAMAGE, new DamageHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_SPRINT, new SprintHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_DODGE, new DodgeHook());

		getHookManager().addHook(CombatClient.MSG_TYPE_COMBAT_RESET_ATTACKER, new ResetAttackerHook());
        getHookManager().addHook(CombatClient.MSG_TYPE_COMBAT_STATE, new StartCombatHook());
		getHookManager().addHook(CombatClient.MSG_TYPE_ABILITY_VECTOR, new AbilityVectorHook());

		getHookManager().addHook(CombatClient.MSG_TYPE_DEBUG_ABILITY, new SetDebugAbilityHook());
         		
        
	}
	
	
	void loadData() {
		// Load settings
		ContentDatabase cDB = new ContentDatabase(false);
		String rangeCheckVertical = cDB.loadGameSetting("RANGE_CHECK_VERTICAL");
		if (rangeCheckVertical != null)
			RANGE_CHECK_VERTICAL = Boolean.parseBoolean(rangeCheckVertical);
		String maxSkillLevel = cDB.loadGameSetting("TOTAL_SKILL_MAX");
		if (maxSkillLevel != null)
			ClassAbilityPlugin.TOTAL_SKILL_MAX = Integer.parseInt(maxSkillLevel);
		if (Log.loggingDebug)Log.debug("GameSettings: Set TOTAL_SKILL_MAX to: " + ClassAbilityPlugin.TOTAL_SKILL_MAX);

		String spiritEffect = cDB.loadGameSetting("SPIRIT_EFFECT");
		if (spiritEffect != null) {
			SPIRIT_EFFECT = Integer.parseInt(spiritEffect);
		}
		if (Log.loggingDebug)Log.debug("GameSettings: Set SPIRIT_EFFECT to: " + SPIRIT_EFFECT);

		String releaseOnLogin = cDB.loadGameSetting("RELEASE_ON_LOGIN");
		if (releaseOnLogin != null)
			RELEASE_ON_LOGIN = Boolean.parseBoolean(releaseOnLogin);
		String magicalAttacksUseWeaponDamage = cDB.loadGameSetting("MAGICAL_ATTACKS_USE_WEAPON_DAMAGE");
		if (magicalAttacksUseWeaponDamage != null)
			MAGICAL_ATTACKS_USE_WEAPON_DAMAGE = Boolean.parseBoolean(magicalAttacksUseWeaponDamage);
		String expBasedOnDamageDealt = cDB.loadGameSetting("EXP_BASED_ON_DAMAGE_DEALT");
		if (expBasedOnDamageDealt != null) {
			EXP_BASED_ON_DAMAGE_DEALT = Boolean.parseBoolean(expBasedOnDamageDealt);
			if (Log.loggingDebug)Log.debug("EXP: Exp based on damage dealt set to: " + EXP_BASED_ON_DAMAGE_DEALT);
		}
		String weaponReqUsesSharedTypes = cDB.loadGameSetting("WEAPON_REQ_USES_SHARED_TYPES");
		if (weaponReqUsesSharedTypes != null)
			WEAPON_REQ_USES_SHARED_TYPES = Boolean.parseBoolean(weaponReqUsesSharedTypes);
		String combatTimeout = cDB.loadGameSetting("COMBAT_TIMEOUT");
		if (combatTimeout != null)
			COMBAT_TIMEOUT = Long.parseLong(combatTimeout) * 1000;
		String usePlayerSetRespawnLocations = cDB.loadGameSetting("USE_PLAYER_SET_RESPAWN_LOCATIONS");
		if (usePlayerSetRespawnLocations != null)
			USE_PLAYER_SET_RESPAWN_LOCATIONS = Boolean.parseBoolean(usePlayerSetRespawnLocations);
		String respawnInDungeon = cDB.loadGameSetting("RESPAWN_IN_DUNGEON");
		if (respawnInDungeon != null)
			RESPAWN_IN_DUNGEON = Boolean.parseBoolean(respawnInDungeon);

		String fallSafeHeight = cDB.loadGameSetting("FALL_SAFE_HEIGHT");
		if (fallSafeHeight != null)
			FALL_SAFE_HEIGHT = Float.parseFloat(fallSafeHeight);

		String fallDeathHeight = cDB.loadGameSetting("FALL_DEATH_HEIGHT");
		if (fallDeathHeight != null)
			FALL_DEATH_HEIGHT = Float.parseFloat(fallDeathHeight);

		String fallDamageStat = cDB.loadGameSetting("FALL_DAMAGE_STAT");
		if (fallDamageStat != null)
			FALL_DAMAGE_STAT = fallDamageStat;

		String fallDamageType = cDB.loadGameSetting("FALL_DAMAGE_TYPE");
		if (fallDamageType != null)
			FALL_DAMAGE_TYPE = fallDamageType;

		String defaultDamageType = cDB.loadGameSetting("DEFAULT_EFFECT_DAMAGE_TYPE");
		if (defaultDamageType != null)
			DEFAULT_EFFECT_DAMAGE_TYPE = defaultDamageType;

		String resistanceStatMax = cDB.loadGameSetting("RESISTANCE_STAT_MAX");
		if (resistanceStatMax != null)
			RESISTANCE_STAT_MAX = Integer.parseInt(resistanceStatMax);

		String flatArmorDamageCalculations = cDB.loadGameSetting("FLAT_ARMOR_DAMAGE_CALCULATIONS");
		if (flatArmorDamageCalculations != null)
			FLAT_ARMOR_DAMAGE_CALCULATIONS = Boolean.parseBoolean(flatArmorDamageCalculations);

		String expLostFromMobDeath = cDB.loadGameSetting("EXP_LOST_FROM_MOB_DEATH");
		if (expLostFromMobDeath != null)
			EXP_LOST_FROM_MOB_DEATH = Integer.parseInt(expLostFromMobDeath);

		String damageHitrollModifier = cDB.loadGameSetting("DAMAGE_HITROLL_MODIFIER");
		if (damageHitrollModifier != null)
			DAMAGE_HITROLL_MODIFIER = Integer.parseInt(damageHitrollModifier);

		String pvpDamageReductionUse = cDB.loadGameSetting("PVP_DAMAGE_REDUCTION_USE");
		if (pvpDamageReductionUse != null) {
			PVP_DAMAGE_REDUCTION_USE = Boolean.parseBoolean(pvpDamageReductionUse);
		}
		String pvpDamageReductionPercent = cDB.loadGameSetting("PVP_DAMAGE_REDUCTION_PERCENT");
		if (pvpDamageReductionPercent != null) {
			PVP_DAMAGE_REDUCTION_PERCENT = Float.parseFloat(pvpDamageReductionPercent);
		}
		String petDistanceDespawn = cDB.loadGameSetting("PET_DISTANCE_DESPAWN");
		if (petDistanceDespawn != null) {
			PET_DISTANCE_DESPAWN = Float.parseFloat(petDistanceDespawn);
		}

		String HitChancePointPerPercentage = cDB.loadGameSetting("HIT_CHANCE_POINT_PER_PERCENTAGE");
		if (HitChancePointPerPercentage != null) {
			HIT_CHANCE_POINT_PER_PERCENTAGE = Integer.parseInt(HitChancePointPerPercentage);
			log.info("Game Settings HIT_CHANCE_POINT_PER_PERCENTAGE set to " + HIT_CHANCE_POINT_PER_PERCENTAGE);
		}

		String HitChancePercentagePerDiffLevel = cDB.loadGameSetting("HIT_CHANCE_PERCENTAGE_PER_DIFF_LEVEL");
		if (HitChancePercentagePerDiffLevel != null) {
			HIT_CHANCE_PERCENTAGE_PER_DIFF_LEVEL = Integer.parseInt(HitChancePercentagePerDiffLevel);
			log.info("Game Settings HIT_CHANCE_PERCENTAGE_PER_DIFF_LEVEL set to " + HIT_CHANCE_PERCENTAGE_PER_DIFF_LEVEL);
		}

		String HitChancePercentageCap = cDB.loadGameSetting("HIT_CHANCE_PERCENTAGE_CAP ");
		if (HitChancePercentageCap != null) {
			HIT_CHANCE_PERCENTAGE_CAP = Float.parseFloat(HitChancePercentageCap);
			log.info("Game Settings HIT_CHANCE_PERCENTAGE_CAP set to " + HIT_CHANCE_PERCENTAGE_CAP);
		}

		String ParryPercentageCap = cDB.loadGameSetting("PARRY_PERCENTAGE_CAP ");
		if (ParryPercentageCap != null) {
			PARRY_PERCENTAGE_CAP = Float.parseFloat(ParryPercentageCap);
			log.info("Game Settings PARRY_PERCENTAGE_CAP  set to " + PARRY_PERCENTAGE_CAP);
		}

		String deathLostExpPercentage = cDB.loadGameSetting("DEATH_LOST_EXP_PERCENTAGE");
		if (deathLostExpPercentage != null) {
			DEATH_LOST_EXP_PERCENTAGE = Float.parseFloat(deathLostExpPercentage);
			log.info("Game Settings DEATH_LOST_EXP_PERCENTAGE  set to " + DEATH_LOST_EXP_PERCENTAGE);
		}

		String deathLostExp = cDB.loadGameSetting("DEATH_LOST_EXP");
		if (deathLostExp != null) {
			DEATH_LOST_EXP = Boolean.parseBoolean(deathLostExp);
			log.info("Game Settings DEATH_LOST_EXP  set to " + DEATH_LOST_EXP);
		}
		String deathPerma = cDB.loadGameSetting("DEATH_PERMANENTLY");
		if (deathPerma != null) {
			DEATH_PERMANENTLY = Boolean.parseBoolean(deathPerma);
			log.info("Game Settings DEATH_PERMANENTLY  set to " + DEATH_PERMANENTLY);
		}

		String globalCooldown = cDB.loadGameSetting("GLOBAL_COOLDOWN");
		if (globalCooldown != null) {
			GLOBAL_COOLDOWN = Float.parseFloat(globalCooldown);
			log.info("Game Settings GLOBAL_COOLDOWN  set to " + GLOBAL_COOLDOWN);
		}

		String weaponCooldown = cDB.loadGameSetting("WEAPON_COOLDOWN");
		if (weaponCooldown != null) {
			WEAPON_COOLDOWN = Float.parseFloat(weaponCooldown);
			log.info("Game Settings WEAPON_COOLDOWN  set to " + WEAPON_COOLDOWN);
		}

		String weaponCooldownAs = cDB.loadGameSetting("ABILITY_WEAPON_COOLDOWN_ATTACK_SPEED");
		if (weaponCooldownAs != null) {
			ABILITY_WEAPON_COOLDOWN_ATTACK_SPEED = Boolean.parseBoolean(weaponCooldownAs);
			log.info("Game Settings ABILITY_WEAPON_COOLDOWN_ATTACK_SPEED  set to " + ABILITY_WEAPON_COOLDOWN_ATTACK_SPEED);
		}
		String stealthRed = cDB.loadGameSetting("USE_ABILITY_STEALTH_REDUCTION_PERCENTAGE");
		if (stealthRed != null) {
			USE_ABILITY_STEALTH_REDUCTION_PERCENTAGE = Float.parseFloat(stealthRed);
			log.info("Game Settings USE_ABILITY_STEALTH_REDUCTION_PERCENTAGE  set to " + USE_ABILITY_STEALTH_REDUCTION_PERCENTAGE);
		}
		String stealthRedPer = cDB.loadGameSetting("USE_ABILITY_STEALTH_REDUCTION");
		if (stealthRedPer != null) {
			USE_ABILITY_STEALTH_REDUCTION = Integer.parseInt(stealthRedPer);
			log.info("Game Settings USE_ABILITY_STEALTH_REDUCTION  set to " + USE_ABILITY_STEALTH_REDUCTION);
		}
		String stealthRedTime = cDB.loadGameSetting("USE_ABILITY_STEALTH_REDUCTION_TIMEOUT");
		if (stealthRedTime != null) {
			USE_ABILITY_STEALTH_REDUCTION_TIMEOUT = Integer.parseInt(stealthRedTime);
			log.info("Game Settings USE_ABILITY_STEALTH_REDUCTION_TIMEOUT  set to " + USE_ABILITY_STEALTH_REDUCTION_TIMEOUT);
		}

		String statRandomHitStun = cDB.loadGameSetting("STAT_RANDOM_HIT_STUN");
		if (statRandomHitStun != null) {
			STAT_RANDOM_HIT_STUN = Float.parseFloat(statRandomHitStun);
			log.info("Game Settings STAT_RANDOM_HIT_STUN  set to " + STAT_RANDOM_HIT_STUN);
		}
		String statRandomHitSleep = cDB.loadGameSetting("STAT_RANDOM_HIT_SLEEP");
		if (statRandomHitSleep != null) {
			STAT_RANDOM_HIT_SLEEP = Float.parseFloat(statRandomHitSleep);
			log.info("Game Settings STAT_RANDOM_HIT_SLEEP  set to " + STAT_RANDOM_HIT_SLEEP);
		}
		String statRandomHitInterruption = cDB.loadGameSetting("STAT_RANDOM_HIT_INTERRUPTION");
		if (statRandomHitInterruption != null) {
			STAT_RANDOM_HIT_INTERRUPTION = Float.parseFloat(statRandomHitInterruption);
			log.info("Game Settings STAT_RANDOM_HIT_INTERRUPTION  set to " + STAT_RANDOM_HIT_INTERRUPTION);
		}

		String save_cooldowns = cDB.loadGameSetting("SAVE_COOLDOWN_LIMIT_DURATION");
		if (save_cooldowns != null) {
			SAVE_COOLDOWN_LIMIT_DURATION = Integer.parseInt(save_cooldowns);
			log.info("Game Settings SAVE_COOLDOWN_LIMIT_DURATION  set to " + SAVE_COOLDOWN_LIMIT_DURATION);
		}

		String devMode = cDB.loadGameSetting("SERVER_DEVELOPMENT_MODE");
		if (devMode != null) {
			EnginePlugin.DevMode = Boolean.parseBoolean(devMode);
			log.info("Game Settings EnginePlugin.DevMode set to " + EnginePlugin.DevMode);
		}

		String pet_allow_type_limit_exceeded = cDB.loadGameSetting("PET_ALLOW_TYPE_LIMIT_EXCEEDED");
		if (pet_allow_type_limit_exceeded != null) {
			AgisMobPlugin.PET_ALLOW_TYPE_LIMIT_EXCEEDED = Boolean.parseBoolean(pet_allow_type_limit_exceeded);
			log.debug("CombatPlugin Loaded PET_ALLOW_TYPE_LIMIT_EXCEEDED:"+AgisMobPlugin.PET_ALLOW_TYPE_LIMIT_EXCEEDED);
		}

		
		cDB.loadEditorOptions();
		ItemDatabase iDB = new ItemDatabase(false);
		iDB.LoadSlotsDefinition();

	}

	protected void ReloadTemplates(Message msg) {
		Log.debug("CombatPlugin ReloadTemplates Start");
		loadData();
		CombatDatabase cDB = new CombatDatabase(false);
		loadStatData(cDB);

		// Load effects
		ArrayList<AgisEffect> effects = cDB.loadCombatEffects();
		for (AgisEffect effect : effects)
			Agis.EffectManager.register(effect.getID(), effect);
		// Abilities
		ArrayList<AgisAbility> abilities = cDB.loadAbilities();
		for (AgisAbility ability : abilities) {
			Agis.AbilityManager.register(ability.getID(), ability);
			Log.debug("ABILITY: added " + ability.getName() + " to the database.");
		}
		// Skills
		HashMap<Integer, SkillTemplate> skillTemplates = cDB.loadSkills();
		for (SkillTemplate tmpl : skillTemplates.values())
			Agis.SkillManager.register(tmpl.getSkillID(), tmpl);

		HashMap<Integer, SkillProfileTemplate> skillProfileTemplates = cDB.loadSkillProfiles();
		for (SkillProfileTemplate tmpl : skillProfileTemplates.values())
			Agis.SkillProfileManager.register(tmpl.getProfileID(), tmpl);

		// cDB.close();

		ContentDatabase ctDB = new ContentDatabase(false);
		graveyards = ctDB.loadGraveyards();
		
		
		
		Entity[] objects =  EntityManager.getAllEntitiesByNamespace(Namespace.COMBAT);
		for(Entity e : objects){
			CombatInfo info = (CombatInfo)e;
			reApplyStats(info);
		}
		
		
		Log.debug("CombatPlugin ReloadTemplates End");
	}

	class SetDebugAbilityHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID oid = message.getSubject();
			CombatInfo info = getCombatInfo(oid);
			Serializable _mode = message.getProperty("mode");
			if (Log.loggingDebug) Log.error("SetDebugNavMeshHook: playerOid=" + oid + " _mode=" + _mode);
			int mode = 1;
			if (_mode != null)
				mode = (int) _mode;
			if (mode == 0) {
				ABILITY_DEBUG_PLAYER = null;
				ExtendedCombatMessages.sendAnouncementMessage(oid, "Ability Debugging is Off", "");
			}
			if (mode == 1) {
				ABILITY_DEBUG_PLAYER = oid;
				ExtendedCombatMessages.sendAnouncementMessage(oid, "Ability Debugging is On", "");
			}

			if (Log.loggingDebug)
				log.debug("CombatPlugin.SetDebugAbilityHook: obj: " + oid + " info: " + info + " end");
			return true;
		}
	}
	
	class SprintHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage rmMsg = (ExtensionMessage) msg;
			OID oid = rmMsg.getSubject();
			int state = (int)rmMsg.getProperty("state");
			CombatInfo info = getCombatInfo(oid);
			if (Log.loggingDebug)
				log.debug("SprintHook: obj: " + oid + " info: " + info);
			//ContentDatabase ctDB = new ContentDatabase(false);
			// int race = (Integer) EnginePlugin.getObjectProperty(info.getOwnerOid(),
			// WorldManagerClient.NAMESPACE, "race");
			int aspect = info.aspect();
			int race = info.getIntProperty("race");

			CharacterTemplate tmpl = ClassAbilityPlugin.getCharacterTemplate(race + " " + aspect);

			int abilityID = tmpl.getSprint();
			if (abilityID < 1) {
				log.warn("CombatPlugin SprintHook ability id should be bigger then 0 break");
				return true;
			}

			CombatInfo target = info;
			AgisAbility ability = Agis.AbilityManager.get(abilityID);
			if (ability == null) {
				log.error("CombatPlugin SprintHook ability is null break");
				return true;
			}

			if (ability.isToggle()) {
				if (Log.loggingDebug)Log.debug("AGISABILITY: ability: " + abilityID + " is toggle");
				if (Log.loggingDebug)Log.debug("AGISABILITY: ability: " + abilityID + " is toggle " + info.currentRunAbilities().keySet() + " " + info.currentRunAbilities());

				if (info.currentRunAbilities().containsKey(ability.getID())) {
					if (Log.loggingDebug)Log.debug("AGISABILITY: toggle ability: " + abilityID + " is runing abort it");
					AgisAbility.deactivateAbility(info.currentRunAbilities().get(ability.getID()));
					return true;
				}
				if (info.currentRunAbilities().size() > 0) {
					int tag = ability.getTagToDisable();
					int tagCount = ability.getTagToDisableCount();
					HashMap<Integer, AgisAbilityState> list = new HashMap<Integer, AgisAbilityState>(info.currentRunAbilities());
					int i = 1;
					for (AgisAbilityState aas : list.values()) {
						if (aas.getAbility().getTags().contains(tag)) {
							if (i >= tagCount) {
								AgisAbility.deactivateAbility(aas);
								i++;
							}
						}
					}
				}
				log.debug(" CombatPlugin SprintHook before AgisAbility.startAbility");
				if(state==1)
					AgisAbility.startAbility(ability, info, target, null, null,-1,-1, null,0L);
				return true;
			}

			return true;
		}
	}

	class DodgeHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage rmMsg = (ExtensionMessage) msg;
			OID oid = rmMsg.getSubject();
			CombatInfo info = getCombatInfo(oid);
			if (Log.loggingDebug)
				log.debug("CombatPlugin.DodgeHook: obj: " + oid + " info: " + info);
			int aspect = info.aspect();
			int race = info.getIntProperty("race");
			//Get Player Template definition
			CharacterTemplate tmpl = ClassAbilityPlugin.getCharacterTemplate(race + " " + aspect);
			//Get Dodge Ability from Player Template definition
			int abilityID = tmpl.getDodge();
			if (abilityID < 1) {
				log.warn("CombatPlugin.DodgeHook ability id should be bigger then 0 break");
				return true;
			}

			CombatInfo target = info;
			AgisAbility ability = Agis.AbilityManager.get(abilityID);
			if (ability == null) {
				log.error("CombatPlugin.DodgeHook ability is null for id "+abilityID+" break");
				return true;
			}
			AgisAbility.startAbility(ability, info, target, null, null, -1, -1, null,0L);
			if (Log.loggingDebug)log.debug("CombatPlugin.DodgeHook: obj: " + oid + " info: " + info+" end");
			return true;
		}
	}

	class DamageHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			CombatClient.DamageMessage dmgMsg = (CombatClient.DamageMessage) msg;
			OID attackerOid = dmgMsg.getAttackerOid();
			OID targetOid = dmgMsg.getTargetOid();
			Log.debug("DamageHook: Start");
			CombatInfo ci = getCombatInfo(targetOid);
			try {
				ArrayList<EffectState> toRemove = new ArrayList<>();
				Iterator<EffectState> effects = ci.getCurrentEffects().iterator();
				while (effects.hasNext()){
					EffectState effect = effects.next();
					if (effect.getEffect() instanceof StealthEffect) {
						toRemove.add(effect);
					}
				}
				for (EffectState es : toRemove) {
					AgisEffect.removeEffect(es);
				}
			}catch (Exception e){
				Log.exception("DamageHook: Exception ",e);
			}
			

			Log.debug("DamageHook: End");
			return true;
		}
	}

	class SetWmgrStatsHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			SetWmgrStatsMessage message = (SetWmgrStatsMessage) msg;
			Log.debug("SetWmgrStatsHook: Start");
			LinkedList<String> stats = message.getStats();
			if (Log.loggingDebug)Log.debug("SetWmgrStatsHook: set Wmgr Stats "+stats);
			WMGR_STATS = stats;
			Log.debug("SetWmgrStatsHook: End");
			return true;
		}
	}

	class ModifyStatsHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ModifyStatsMessage message = (ModifyStatsMessage) msg;
			OID playerOid = message.getSubject();
			ArrayList<EnchantStat> stats = message.getStats();
			String obj = message.getObj();

			if (Log.loggingDebug)Log.debug("ModifyStatsHook: playerOid: " + playerOid + " obj=" + obj);
			CombatInfo ci = getCombatInfo(playerOid);
			for (EnchantStat es : stats) {
				if (Log.loggingDebug)
					log.debug("ModifyStatsHook stat=" + es);
				if (es.GetValue() != 0)
					ci.statAddModifier(es.GetStatName(), obj, es.GetValue(), false);
				if (es.GetValuePercentage() != 0)
					ci.statAddPercentModifier(es.GetStatName(), obj, es.GetValuePercentage(), false);
			}
			ci.statSendUpdate(false);
			if (Log.loggingDebug)Log.debug("ModifyStatsHook: playerOid: " + playerOid + " End");
			return true;
		}
	}

	   class DelModifyStatsHook implements Hook {
	        public boolean processMessage(Message msg, int flags) {
	        	DelModifyStatsMessage message = (DelModifyStatsMessage) msg;
	        	OID playerOid = message.getSubject();
	        	String obj = (String)message.getProperty("obj");
	        	
	        	if (Log.loggingDebug)Log.debug("DelModifyStatsHook: playerOid: " + playerOid +" obj="+obj);
	            CombatInfo ci = getCombatInfo(playerOid);
	            for (String statName : CombatPlugin.STAT_LIST) {
	            	ci.statRemoveModifier(statName, obj, false);
	            	ci.statRemovePercentModifier(statName, obj, false);
					}
	            ci.statSendUpdate(false);
	            if (Log.loggingDebug)  Log.debug("DelModifyStatsHook: playerOid: " + playerOid+" End");
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
	        	if (Log.loggingDebug) Log.debug("GlobalEventBonusesUpdateHook: "+message.getBonuses());
	          
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
	            CombatInfo obj = getCombatInfo(playerOid);
	            obj.setBonuses(message.getBonuses());
	            if (Log.loggingDebug) Log.debug("BonusesUpdateHook: playerOid: " + playerOid+" End");
	            return true;
	        }
	    }
	class GetCombatInfoParamsHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			GetCombatObjectParamsMessage message = (GetCombatObjectParamsMessage) msg;
			CombatInfo obj = getCombatInfo(message.getSubject());
			LinkedList<String> params = message.getParams();
			if (Log.loggingDebug)
				log.debug("GetCombatInfoParamsHook: CombatInfo for " + message.getSubject() + " params=" + params);
			HashMap<String, Serializable> responeParams = new HashMap<String, Serializable>();
			try {
				if (obj != null) {
					responeParams.put("subject", true);
					for (String s : params) {

						switch (s) {
							case "isGrouped":
								if (Log.loggingDebug)
									log.debug("GetCombatInfoParamsHook: isGrouped=" + obj.isGrouped());
								responeParams.put(s, obj.isGrouped());
								break;
							case "effects":
								responeParams.put(s, obj.getProperty("effects"));
								break;
							case "groupOid":
								if (Log.loggingDebug)
									log.debug("GetCombatInfoParamsHook: groupOid=" + obj.getGroupOid());
								responeParams.put(s, obj.getGroupOid());
								break;
							case "groupMemberOid":
								if (Log.loggingDebug)
									log.debug("GetCombatInfoParamsHook: groupMemberOid=" + obj.getGroupMemberOid());
								responeParams.put(s, obj.getGroupMemberOid());
								break;
							case "isPendingGroupInvite":
								if (Log.loggingDebug)
									log.debug("GetCombatInfoParamsHook: isPendingGroupInvite=" + obj.isPendingGroupInvite());
								responeParams.put(s, obj.isPendingGroupInvite());
								break;
							case "bonuses":
								if (Log.loggingDebug) log.debug("GetCombatInfoParamsHook: bonuses=" + obj.getBonuses());
								responeParams.put(s, obj.getBonuses());
								break;
							case "petStats":
								if (Log.loggingDebug)
									log.debug("GetCombatInfoParamsHook: pets stats ");
								responeParams.put(PET_GLOBAL_COUNT_STAT,obj.statGetCurrentValue(PET_GLOBAL_COUNT_STAT));
								for (String stat : STAT_PETS_COUNT.values()){
									responeParams.put(stat,obj.statGetCurrentValue(stat));
								}
								break;
							default:
								//	log.debug("GetCombatInfoParamsHook: effects="+obj.getEffects());
								responeParams.put(s, obj.statGetCurrentValue(s));
								break;
						}
					}

				} else {
					if(Log.loggingDebug)Log.debug("GetCombatInfoParamsHook: CombatInfo for " + message.getSubject() + " is null cant get params ");
				}
			} catch (Exception e) {
				Log.exception("GetCombatInfoParamsHook ", e);
			}
			if (Log.loggingDebug)
				log.debug("GetCombatInfoParamsHook: CombatInfo for " + message.getSubject() + " responeParams=" + responeParams);
			Engine.getAgent().sendObjectResponse(message, responeParams);
			return true;
		}
	}

	class SetCombatInfoParamsHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			SetCombatObjectParamsMessage message = (SetCombatObjectParamsMessage) msg;
			CombatInfo obj = getCombatInfo(message.getSubject());
			boolean reqResp = message.getRequestResponse();
			if (Log.loggingDebug)log.debug("SetCombatInfoParamsHook: CombatInfo for "+message.getSubject()+" reqResp="+reqResp);
			if(obj==null) {
				log.error("SetCombatInfoParamsHook: CombatInfo for "+message.getSubject()+" is null cant set params ");
				if(reqResp)
				Engine.getAgent().sendBooleanResponse(message,false);
				return true;
			}
			HashMap<String, Serializable> params = message.getParams();
			for (String s : params.keySet()) {
				
				if (Log.loggingDebug)log.debug("SetCombatInfoParamsHook: CombatInfo for "+message.getSubject()+" set  key="+s+" value="+params.get(s));
				
				switch (s) {
				case "groupMemberOid":
					if (Log.loggingDebug)log.debug("SetCombatInfoParamsHook: CombatInfo for "+message.getSubject()+" set  groupMemberOid="+(OID)params.get(s));
					 obj.setGroupMemberOid((OID)params.get(s));
					break;
				case "groupOid":
					if (Log.loggingDebug)log.debug("SetCombatInfoParamsHook: CombatInfo for "+message.getSubject()+" set  groupOid="+(OID)params.get(s));
					obj.setGroupOid((OID)params.get(s));
					break;
				case "setPendingGroupInvite":
					if (Log.loggingDebug)log.debug("SetCombatInfoParamsHook: CombatInfo for "+message.getSubject()+" set  setPendingGroupInvite="+(boolean)params.get(s));
					obj.setPendingGroupInvite((boolean)params.get(s));
					break;
				}
			}
			if(reqResp)
			Engine.getAgent().sendBooleanResponse(message, true);
			return true;
		}
	}
	
	
	/************************************************************************************************************/
	//Modyfikacje
	class CombatGenerateSubObjectHook extends GenerateSubObjectHook {
		public CombatGenerateSubObjectHook() {
			super(CombatPlugin.this);
		}

		public SubObjData generateSubObject(Template template, Namespace namespace, OID masterOid) {
			if (Log.loggingDebug)
				log.debug("GenerateSubObjectHook: masterOid=" + masterOid + " namespace=" + namespace + " template=" + template);

			Boolean persistent = (Boolean) template.get(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT);
			if (persistent == null)
				persistent = false;

			if (namespace == CombatClient.INSTANCE_NAMESPACE) {
				return generateInstanceSubObject(masterOid, persistent);
			}

			Map<String, Serializable> props = template.getSubMap(Namespace.COMBAT);
			if (props == null) {
				Log.warn("GenerateSubObjectHook: no props in ns " + Namespace.COMBAT);
				return null;
			}

			OID instanceOid = (OID) props.get(WorldManagerClient.TEMPL_INSTANCE);
			if (instanceOid == null) {
				Log.error("GenerateSubObjectHook: missing instanceOid");
				return null;
			}
			
			
		/*	
			// generate the subobject
			AOObject wObj = generateWorldManagerSubObject(template, masterOid);

			wObj.setName(objName);
			wObj.scale(scale);

			// set the base display context for the object
			DisplayContext dc = (DisplayContext) props.get(WorldManagerClient.TEMPL_DISPLAY_CONTEXT);
			if (dc != null) {
				dc = (DisplayContext) dc.clone();
				dc.setObjRef(wObj.getOid());
				wObj.displayContext(dc);
			} else {
				Log.debug("GenerateSubObjectHook: object has no display context, oid=" + masterOid);// ZBDS debug
			}
			if (Log.loggingDebug)
				log.debug("GenerateSubObjectHook: created entity " + wObj + ", loc=" + loc);

			// create a world node for the object
		
			

			// register the entity
			EntityManager.registerEntityByNamespace(wObj, Namespace.WORLD_MANAGER);

			if (persistent)
				Engine.getPersistenceManager().persistEntity(wObj);
*/
			// subscribe to messages regarding this object
			// we need this for static objects.
			// problem is when players log in, we try to bind them
			// again, but subscribeForMob checks for double binds.
			subscribeForMob(masterOid);

			return new SubObjData();
		}

		public SubObjData generateInstanceSubObject(OID masterOid, Boolean persistent) {
			WorldManagerInstance instance = createInstanceEntity(masterOid);
			instance.setPersistenceFlag(persistent);

			if (persistent)
				Engine.getPersistenceManager().persistEntity(instance);

			subscribeForObject(masterOid);
			hostInstance(masterOid);

			// ##
			// WorldManagerClient.sendPerceiverRegionsMsg(0L,
			// Geometry.maxGeometry(), null);

			return new SubObjData();
		}
	} // end WorldManagerGenerateSubObjectHook

	class GetPluginStatusHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LinkedHashMap<String, Serializable> status = new LinkedHashMap<String, Serializable>();
			status.put("plugin", getName());
			status.put("entity", EntityManager.getEntityCount());
			if (Log.loggingDebug)log.debug("GetPluginStatusHook "+EntityManager.getAllEntitiesByNamespace(Namespace.COMBAT));
			status.put("instance", instances.size());
			Engine.getAgent().sendObjectResponse(msg, status);
			return true;
		}
	}
	
	public Map<String, String> getStatusMap() {
		Map<String, String> status = new HashMap<String, String>();
		status.put("instances", Integer.toString(instances.size()));
		status.put("entities", Integer.toString(EntityManager.getEntityCount()));
		return status;
	}
	

	WorldManagerInstance createInstanceEntity(OID instanceOid) {
		WorldManagerInstance instance = new WorldManagerInstance(instanceOid);
		if (Log.loggingDebug)log.debug("createInstanceEntity instanceOid="+instanceOid);
		initializeInstance(instance);

		EntityManager.registerEntityByNamespace(instance, CombatClient.INSTANCE_NAMESPACE);

		return instance;
	}

	void initializeInstance(WorldManagerInstance instance) {
		instances.add(instance.getOid());
			
	}
	void hostInstance(OID masterOid) {
		CombatFilter.InstanceGeometry instanceGeo = new CombatFilter.InstanceGeometry();
		instanceGeo.instanceOid = masterOid;
		

		// Update WM subscriptions with new instance
		FilterUpdate filterUpdate = new FilterUpdate();
		filterUpdate.addFieldValue(CombatFilter.FIELD_INSTANCES, instanceGeo);
		((CombatFilter) selectionFilter).applyFilterUpdate(filterUpdate);
		Engine.getAgent().applyFilterUpdate(selectionSubscription, filterUpdate, MessageAgent.BLOCKING);

	/*	filterUpdate = new FilterUpdate();
		filterUpdate.addFieldValue(WorldManagerFilter.FIELD_INSTANCES, instanceGeo);
		newRegionFilter.applyFilterUpdate(filterUpdate);
		Engine.getAgent().applyFilterUpdate(newRegionSub, filterUpdate, MessageAgent.BLOCKING);
	*/
		}

	void unhostInstance(OID oid) {
		// Update WM subscriptions with removed instance
		FilterUpdate filterUpdate = new FilterUpdate();
		filterUpdate.removeFieldValue(CombatFilter.FIELD_INSTANCES, oid);
		((CombatFilter) selectionFilter).applyFilterUpdate(filterUpdate);
		Engine.getAgent().applyFilterUpdate(selectionSubscription, filterUpdate, MessageAgent.BLOCKING);

		/*filterUpdate = new FilterUpdate();
		filterUpdate.removeFieldValue(WorldManagerFilter.FIELD_INSTANCES, oid);
		newRegionFilter.applyFilterUpdate(filterUpdate);
		Engine.getAgent().applyFilterUpdate(newRegionSub, filterUpdate, MessageAgent.BLOCKING);
*/	}

	class InstanceLoadHook implements LoadHook {
		public void onLoad(Entity entity) {
			WorldManagerInstance instance = (WorldManagerInstance) entity;
			if (Log.loggingDebug)log.debug("InstanceLoadHook entity="+entity.getOid()+" inst oid="+instance.getOid());
			initializeInstance(instance);
			subscribeForObject(entity.getOid());
		}
	}

	class InstanceUnloadHook implements UnloadHook {
		public void onUnload(Entity entity) {
			instances.remove(entity.getOid());
			unsubscribeForObject(entity.getOid());
			unhostInstance(entity.getOid());
		}
	}

	class InstanceDeleteHook implements DeleteHook {
		public void onDelete(Entity entity) {
			instances.remove(entity.getOid());
			unsubscribeForObject(entity.getOid());
			unhostInstance(entity.getOid());
		}

		public void onDelete(OID oid, Namespace namespace) {
			// n/a
		}
	}

	
	protected void subscribeForMob(OID oid) {
		// subscribe for subsequent messages from this oid
		// do this before responding to the bind message
		if (Log.loggingDebug)
			log.debug("subscribeForMob: oid=" + oid);

		SubscriptionManager.get().subscribe(this, oid, mobFilterTypes);
	    SubscriptionManager.get(MessageAgent.RESPONDER).subscribe(this, oid, mobRPCFilterTypes);
		subscribeForObject(oid);
	}

	protected void subscribeForObject(OID masterOid) {
		// subscribe for subsequent messages from this oid
		// do this before responding to the bind message
		if (Log.loggingDebug)
			log.debug("subscribeForObject: oid=" + masterOid);

        NamespaceSubscriptionManager.get(MessageAgent.RESPONDER, subObjectFilterNamespaces).subscribe(this, masterOid, subObjectFilterTypes);
        NamespaceSubscriptionManager.get(subObjectFilterNamespaces).subscribe(this, masterOid, MSG_TYPE_SET_PROPERTY_NONBLOCK);
	}

	protected void unsubscribeForMob(OID oid) {
		// subscribe for subsequent messages from this oid
		// do this before responding to the bind message
		if (Log.loggingDebug)
			log.debug("unsubscribeForObject: oid=" + oid);

        SubscriptionManager.get().unsubscribe(this, oid, mobFilterTypes);
        SubscriptionManager.get(MessageAgent.RESPONDER).unsubscribe(this, oid, mobRPCFilterTypes);
		unsubscribeForObject(oid);
	}

	protected void unsubscribeForObject(OID oid) {
		// subscribe for subsequent messages from this oid
		// do this before responding to the bind message
		if (Log.loggingDebug)
			log.debug("unsubscribeForObject: oid=" + oid);

        NamespaceSubscriptionManager.get(MessageAgent.RESPONDER, subObjectFilterNamespaces).unsubscribe(this, oid);
        NamespaceSubscriptionManager.get(subObjectFilterNamespaces).unsubscribe(this, oid);
	}

	
	
	
	
	
	public static class CombatFilter extends NamespaceFilter {
		public CombatFilter() {
			super();
		}

		public CombatFilter(String pluginName) {
			super();
			setPluginName(pluginName);
		}

		public String getPluginName() {
			return pluginName;
		}

		public void setPluginName(String pluginName) {
			this.pluginName = pluginName;
		}

		public void addInstance(OID instanceOid) {
			// ## check for duplicates
			instances.add(instanceOid);
		}

		public void removeInstance(OID instanceOid) {
			instances.remove(instanceOid);
		}

		public boolean getInstance(OID instanceOid) {
			return instances.contains(instanceOid);
		}

		public synchronized boolean matchRemaining(Message message) {
			OID instanceOid = null;
			Point location = null;
			MessageType type = message.getMsgType();
			Namespace namespace = null;

			if (!super.matchRemaining(message)) {
				if (type != WorldManagerClient.MSG_TYPE_NEW_REGION && type != WorldManagerClient.MSG_TYPE_PLAYER_PATH_WM_REQ)
					return false;
			}

			// generate sub-object: match on instance-oid and location
			// load sub-object: match on instance-oid and location

			if (type == ObjectManagerClient.MSG_TYPE_GENERATE_SUB_OBJECT && message instanceof GenerateSubObjectMessage) {
				GenerateSubObjectMessage genMsg = (GenerateSubObjectMessage) message;
				Template template = genMsg.getTemplate();

				String targetPlugin = (String) template.get(Namespace.COMBAT_INSTANCE, CombatClient.TEMPL_COMBAT_NAME);
				if (targetPlugin != null) {
					if (targetPlugin.equals(pluginName))
						return true;
					else
						return false;
				}
				if (Log.loggingDebug)Log.debug("CombatFilter: GENERATE: getting template: " + template);
				
				instanceOid = (OID) template.get(Namespace.COMBAT, WorldManagerClient.TEMPL_INSTANCE);
				if (instanceOid == null) {
					Log.error("CombatFilter: generate msg has null instanceOid, oid=" + genMsg.getSubject());
					return false;
				}
			} else if (type == ObjectManagerClient.MSG_TYPE_LOAD_SUBOBJECT) {
				if (message instanceof WorldManagerClient.LoadSubObjectMessage) {
					WorldManagerClient.LoadSubObjectMessage loadMsg = (WorldManagerClient.LoadSubObjectMessage) message;
					instanceOid = loadMsg.getInstanceOid();
					location = loadMsg.getLocation();
				} else if (message instanceof ObjectManagerClient.LoadSubObjectMessage) {
					ObjectManagerClient.LoadSubObjectMessage loadMsg = (ObjectManagerClient.LoadSubObjectMessage) message;
					instanceOid = loadMsg.getSubject();
					namespace = loadMsg.getNamespace();
				}
			} else /*if (type == WorldManagerClient.MSG_TYPE_NEW_REGION) {
				NewRegionMessage regionMsg = (NewRegionMessage) message;
				instanceOid = regionMsg.getInstanceOid();
				List<Geometry> localGeometry = instanceGeometry.get(instanceOid);
				if (localGeometry == null)
					return false;

				// ## GAK! Must intersect region with instance geometry
				return true;
			} else */
				if (type == WorldManagerClient.MSG_TYPE_PLAYER_PATH_WM_REQ) {
				WorldManagerClient.PlayerPathWMReqMessage reqMsg = (WorldManagerClient.PlayerPathWMReqMessage) message;
				instanceOid = reqMsg.getInstanceOid();
			}
			if (instanceOid != null) {
				if(instances.contains(instanceOid))
					return true;
		
				return false;
			}

			return false;
		}

		public synchronized boolean applyFilterUpdate(FilterUpdate update) {
			List<FilterUpdate.Instruction> instructions = update.getInstructions();

			for (FilterUpdate.Instruction instruction : instructions) {
				switch (instruction.opCode) {
				case FilterUpdate.OP_ADD:
					if (instruction.fieldId == FIELD_INSTANCES) {
						InstanceGeometry instanceGeo = (InstanceGeometry) instruction.value;
						if (Log.loggingDebug)
							Log.debug("CombatFilter ADD INSTANCE " + instruction.value+" instanceGeo="+instanceGeo);
						instances.add(instanceGeo.instanceOid);
					} else
						Log.error("CombatFilter: invalid fieldId " + instruction.fieldId);
					break;
				case FilterUpdate.OP_REMOVE:
					if (instruction.fieldId == FIELD_INSTANCES) {
						if (Log.loggingDebug)
							Log.debug("CombatFilter REMOVE INSTANCE " + instruction.value);
						instances.remove((OID) instruction.value);
					} else
						Log.error("CombatFilter: invalid fieldId " + instruction.fieldId);
					break;
				case FilterUpdate.OP_SET:
					Log.error("CombatFilter: OP_SET is not supported");
					break;
				default:
					Log.error("CombatFilter: invalid opCode " + instruction.opCode);
					break;
				}
			}
			return false;
		}

		public String toString() {
			return "[CombatFilter " + toStringInternal() + "]";
		}

		protected String toStringInternal() {
			return super.toStringInternal() + " pluginName=" + pluginName + " instances=" + instances.size();
		}

		public static final int FIELD_INSTANCES = 1;

		public static class InstanceGeometry {
			OID instanceOid;
			public String toString() {
				return "[InstanceGeometry instanceOid=" + instanceOid + "]";
			}

			
		}

		private String pluginName;
		private List<OID> instances = new ArrayList<OID>();
	}

	
	public static class CombatInstance extends Entity {
		public CombatInstance() {
			super();
		}

		public CombatInstance(OID instanceOid) {
			super(instanceOid);
		}

		private static final long serialVersionUID = 1L;
	}

	
	
	
	
	
	protected List<OID> instances = new ArrayList<OID>();

	
    protected Set<MessageType> subObjectFilterTypes = new HashSet<>(); 
    protected Set<Namespace> subObjectFilterNamespaces = new HashSet<>(); 
	protected CombatFilter newRegionFilter;
	protected long newRegionSub;

	// Subscription for non-structures
    protected final Set<MessageType> mobFilterTypes = new HashSet<>();
    protected final Set<MessageType> mobRPCFilterTypes = new HashSet<>();
	
	
	/*************************************************************************************************************************/
	
	public static LinkedList<Cooldown> getCooldowns(OID characterOID) {
		if (aDB == null)
			aDB = new AccountDatabase(true);
		return aDB.getCooldowns(characterOID);

	}

	public static void saveCooldowns(OID characterOID, LinkedList<Cooldown> cooldownList) {
		if (aDB == null)
			aDB = new AccountDatabase(true);
		if (Log.loggingDebug)Log.debug("CombatPlugin: save Cooldown " + cooldownList.size());
		aDB.saveCooldowns(characterOID, cooldownList);
	}

	class ClientLevelLoadedHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage eMsg = (ExtensionMessage) msg;
			Log.debug("LevelLoaded: got Message ClientLevelLoaded");
			OID oid = eMsg.getSubject();
			CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);
			LinkedList<Cooldown> cooldownList = CombatPlugin.getCooldowns(oid);
			if (Log.loggingDebug)Log.debug("LevelLoaded: cooldownList size: " + cooldownList.size());
			if (cooldownList.size() > 0) {
				if (Log.loggingDebug)Log.debug("LevelLoaded: Apply Cooldowns " + cooldownList.size() + " to " + oid + " " + cInfo.getName());
				double cooldownMod = 100;
				if(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT!=null) {
					double statValue = cInfo.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
						int pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("AgisAbility: CooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("AgisAbility: CooldownMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("AgisAbility: CooldownMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						/*if (pointsCalculated < statValue) {
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						}*/
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CooldownMod calculated: " + calculated);	
					cooldownMod = calculated;//Math.round(calculated/100f);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: CooldownMod calculated="+calculated+" mod="+cooldownMod);
					
					
				}
				double cooldownGlobalMod = 100;
				if(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT!=null) {
					double statValue = cInfo.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: GlobalCooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
						int pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("AgisAbility: GlobalCooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("AgisAbility: GlobalCooldownMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("AgisAbility: GlobalCooldownMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						/*if (pointsCalculated < statValue) {
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						}*/
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("AgisAbility: GlobalCooldownMod calculated: " + calculated);	
					cooldownGlobalMod = calculated;//Math.round(calculated/100f);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: GlobalCooldownMod calculated="+calculated+" mod="+cooldownGlobalMod);
					
					
				}
				double cooldownWeaponMod = 100;
				if(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT!=null) {
					double statValue = cInfo.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: WeaponCooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
						int pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("AgisAbility: WeaponCooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("AgisAbility: WeaponCooldownMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("AgisAbility: WeaponCooldownMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						/*if (pointsCalculated < statValue) {
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						}*/
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("AgisAbility: WeaponCooldownMod calculated: " + calculated);	
					cooldownWeaponMod = calculated;//Math.round(calculated/100f);
					if (Log.loggingDebug)
						Log.debug("AgisAbility: WeaponCooldownMod calculated="+calculated+" mod="+cooldownWeaponMod);
					
					
				}
				Cooldown.activateCooldowns(cooldownList, cInfo,cooldownGlobalMod, cooldownWeaponMod, cooldownMod, cInfo.statGetBaseValue(CombatPlugin.ATTACK_SPEED_STAT));

			} else {
				Log.debug("LevelLoaded: No Cooldowns ");
			}
			return true;
		}
	}

	public static void resolveAutoAttack(CombatInfo info) {
		if (Log.loggingDebug)
			log.debug("CombatPlugin.resolveAutoAttack: info=" + info);
		OID targetOid = info.getAutoAttackTarget();
		CombatInfo target = getCombatInfo(targetOid);
		if (target == null) {
			return;
		}

		int abilityID = (Integer) info.getProperty(CombatInfo.COMBAT_PROP_AUTOATTACK_ABILITY);

		int autoattack = abilityID;
		if (!info.isUser()) {
			// Intercept here and check to see if the mob should use a different
			// ability - this is only temp
			for (int i = 0; i < 3; i++) {
				Integer abilityID1 = (Integer) info.getProperty("ability" + i);
				if (abilityID1 != null && abilityID1 > 0) {
					String statReq = (String) info.getProperty("abilityStatReq" + i);
					Integer statValue = (Integer) info.getProperty("abilityStatPercent" + i);

					// Check if stat requirement exists and if so, is met
					if (statReq != null && !statReq.equals("") && !statReq.contains("none")) {
						double currentVal = info.statGetCurrentValueWithPrecision(statReq);
						double maxVal = info.statGetMaxValue(statReq);
						if ((float) currentVal / (float) maxVal * 100 > statValue) {
							continue;
						}
					}

					// Check that the ability isn't on cooldown
					boolean abilityReady = true;
					AgisAbility ability = Agis.AbilityManager.get(abilityID1);
					if (ability == null)
						continue;

					Map<String, Cooldown> cooldowns = ability.getCooldownMap();
					Set<Map.Entry<String, Cooldown>> cooldownSet = cooldowns.entrySet();
					for (Map.Entry<String, Cooldown> entry : cooldownSet) {
						String cooldownName = (String) entry.getKey();
						Cooldown.State cdState = info.getCooldownState(cooldownName);
						if (cdState != null) {
							Log.debug("AUTO: HealAbility is currently on cooldown");
							abilityReady = false;
						}
					}

					if (abilityReady) {
						abilityID = abilityID1;
						break;
					}
				}
			}
		}

		if (abilityID < 1) {
			return;
		}

		AgisAbility ability = Agis.AbilityManager.get(abilityID);
		if (Log.loggingDebug)
			log.debug("CombatPlugin.resolveAutoAttack: abilityID " + abilityID + ", ability " + ability);
		/*
		 * if (abilityID != autoattack) { String casterName =
		 * WorldManagerClient.getObjectInfo(info .getOwnerOid()).name;
		 * WorldManagerClient.sendObjChatMsg(target.getOwnerOid(), 0, casterName +
		 * " begins casting " + ability.getName()); }
		 */

		// Try make the attacker (only for mobs) face their target
		/*
		 * if (!info.isUser()) { InterpolatedWorldNode wnode = info.getWorldNode();
		 * //Quaternion direction = null; //wnode.setOrientation(direction); }
		 */

		/*
		 * int duelID = -1; int duelID2 = -1; if (info.isUser() && target.isUser()) {
		 * try { duelID = (Integer) target.getProperty(CombatInfo.COMBAT_PROP_DUEL_ID);
		 * duelID2 = (Integer) info.getProperty(CombatInfo.COMBAT_PROP_DUEL_ID); } catch
		 * (NullPointerException e) { } } if (duelID == duelID2)
		 * ability.setDuelID(duelID); else ability.setDuelID(-1);
		 */

		Log.debug("AUTO: calling auto attack");
		AgisAbility.startAbility(ability, info, target, null);
		Log.debug("AUTO: finished auto attack");
		if (MOBS_STOP_TO_ATTACK && !info.isUser()) {
			CombatClient.sendAutoAttackCompleted(info.getOwnerOid());
		}
	}

	public static void sendAbilityUpdate(CombatInfo info) {
		if (Log.loggingDebug)
			log.debug("CombatPlugin: sending AbilityUpdate for obj=" + info);
		CombatClient.AbilityUpdateMessage msg = new CombatClient.AbilityUpdateMessage(info.getOwnerOid(), info.getOwnerOid());
		for (int abilityID : info.getCurrentAbilities()) {
			AgisAbility ability = Agis.AbilityManager.get(abilityID);
			if (Log.loggingDebug)
				log.debug("CombatPlug: adding ability to message. ability=" + ability);
			msg.addAbility(ability.getID(), ability.getIcon(), "");
		}
		Engine.getAgent().sendBroadcast(msg);
	}

	public static CombatInfo getCombatInfo(OID oid) {
		return (CombatInfo) EntityManager.getEntityByNamespace(oid, Namespace.COMBAT);
	}

	public static void registerCombatInfo(CombatInfo cinfo) {
		EntityManager.registerEntityByNamespace(cinfo, Namespace.COMBAT);
	}

	class CombatLoadHook implements LoadHook {
		public void onLoad(Entity e) {
			Log.debug("CombatLoadHook: "+e);
			CombatInfo info = (CombatInfo) e;
			subscribeForMob(info.getOid());
			Serializable wType = info.getProperty("weaponType");
			if (wType instanceof String) {
				ArrayList<String> newWType = new ArrayList<String>();
				String s = (String) wType;
				if (s.length() > 0)
					newWType.add(s);
				info.setProperty("weaponType", newWType);
			}
			// Clear the target lists
			info.setAttackableTargets(new HashMap<OID, TargetInfo>());
			info.setFriendlyTargets(new HashMap<OID, TargetInfo>());
			AgisEffect.removeNonContinuousEffects(info, true);
			if (info.dead()) {
				sendDeathBox(info.getOwnerOid());
			}else {
				removeSpiritEffect(info.getOid());
			}
			
			//Check if xpProfile has been set for character if not set it
			if (info.getExpProfile() == -1)
			{				
				int aspect = info.aspect();
				int race = info.getIntProperty("race");				
				if(race > 0) {
					CharacterTemplate tmpl = ClassAbilityPlugin.getCharacterTemplate(race + " " + aspect);
					info.setExpProfile(tmpl.getExpProfile());
				}
			}
			
			reApplyStats(info);
			
			// Load Cooldowns
			/*
			 * LinkedList<Cooldown> cooldownList = CombatPlugin.getCooldowns(info.getOid());
			 * Log.error("Login: Apply Cooldowns "+cooldownList.size()
			 * +" to "+info.getOid()+" "+info.getName()); if (cooldownList.size() > 0) {
			 * Log.error("Login: Apply Cooldowns "+cooldownList.size()
			 * +" to "+info.getOid()+" "+info.getName());
			 * Cooldown.activateCooldowns(cooldownList, info, 100); }else {
			 * Log.error("Login: No Cooldowns "); }
			 */

			testmsgTimer timer = new testmsgTimer(info.getOid(), info);
			Engine.getExecutor().schedule(timer, 2, TimeUnit.SECONDS);
			Log.debug("start schedule");

			boolean effectUpdated =false;
			for (EffectState state : info.getCurrentEffects()) {
				if (state.getEffect().isPassive()) {
					boolean requirementsMet = true;
					AgisAbility ability = Agis.AbilityManager.get(state.getAbilityID());
					if (ability == null) {
						continue;
					}
					if (ability.checkEquip(info, info, null) != AbilityResult.SUCCESS) {
						requirementsMet = false;
					}
					Log.debug("WEAPON: requirements met for effect: " + state.getEffectID() + "? " + requirementsMet);
					if (!requirementsMet && state.isActive()) {
						// Disable the boost from the passive effect
						state.getEffect().deactivate(state);
						effectUpdated = true;
					} else if (requirementsMet && !state.isActive()) {
						// Enable the boost from the passive effect
						state.getEffect().activate(state);
						effectUpdated = true;
					}
				}
			}

			if (effectUpdated) {
				info.updateEffectsProperty();
			}
			
			
			
			// Add to stat tick
			CombatStatTick statTick = new CombatStatTick(info);
			// statTick.AddCombatInfo(info);
			Engine.getExecutor().schedule(statTick, 10, TimeUnit.SECONDS);
			statTicks.put(info.getOwnerOid(), statTick);
		//	subscribeForMob(info.getOid());
			
		}
	}

	void reApplyStats(CombatInfo info) {

		// Calculate their base stats to ensure they are correct
		int race = -1;
		race = info.getIntProperty("race");
		if (race < 1) {
			try {
				race = (Integer) EnginePlugin.getObjectProperty(info.getOwnerOid(), WorldManagerClient.NAMESPACE, "race");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
		}
		int aspect = info.aspect();
		if(Log.loggingDebug)
			log.debug("reApplyStats obj "+info.getOwnerOid()+" race= "+race+" aspect="+aspect);
		if(race>0) {
			
		
		CharacterTemplate tmpl = ClassAbilityPlugin.getCharacterTemplate(race + " " + aspect);
		ClassAbilityPlugin.calculatePlayerStats(info, tmpl);
		for (SkillData sData : info.getCurrentSkillInfo().getSkills().values()) {
			if(Agis.SkillManager.get(sData.getSkillID()) !=null) {
				if (SkillInfo.checkSkillRequirement(info.getCurrentSkillInfo(), info, Agis.SkillManager.get(sData.getSkillID()))) {
					SkillInfo.applyStatModifications(info, Agis.SkillManager.get(sData.getSkillID()), sData.getSkillLevel());
					if (Log.loggingDebug) Log.debug("OnLoad SkillData " + sData);
					SkillInfo.applyNewAbilities(info, Agis.SkillManager.get(sData.getSkillID()), sData.getSkillLevel());
				} else {
					if (Log.loggingDebug)
						Log.debug("OnLoad SkillData no apply Skill Stat Modification because requirements not been met for skill " + sData.getSkillID());
				}
			}else{
				Log.error("OnLoad SkillData no apply Skill Stat Modification because not found skill definition with id " + sData.getSkillID());
			}
		}

		if (STEALTH_STAT != null) {
			AgisStat stat = (AgisStat) info.getProperty(STEALTH_STAT);
			if (stat != null) {
				stat.removeModifier("Ability");
				stat.removePercentModifier("Ability");
			} else {
				if(Log.loggingDebug)log.debug("Stealth stat was defined but not assign as start stats for Character Template race " + RequirementChecker.getRace(race) + " and class " + RequirementChecker.getClass(aspect));
			}
		}

		if (PERCEPTION_STEALTH_STAT != null) {
			AgisStat stat = (AgisStat) info.getProperty(PERCEPTION_STEALTH_STAT);
			if (stat != null) {
				stat.removeModifier("Ability");
				stat.removePercentModifier("Ability");
			} else {
				if(Log.loggingDebug)log.debug("Stealth Perceprion stat was defined but not assign as start stats for Character Template race " + RequirementChecker.getRace(race) + " and class " + RequirementChecker.getClass(aspect));
			}
		}

		// Apply stat shift values again
		for (String statName : info.getVitalityStats().keySet()) {
			if (!info.getPropertyMap().containsKey(statName))
				log.debug("CombatInfo not contains Stat " + statName);
			AgisStat stat = (AgisStat) info.getProperty(statName);
			VitalityStatDef statDef = (VitalityStatDef) CombatPlugin.lookupStatDef(statName);
			if (stat == null) {
				log.error("Combat Stat " + statName + " not find ");
				continue;
			}
			if (statDef == null) {
				log.error("Combat Stat def " + statName + " not find ");
				continue;
			}
			stat.setBaseShiftValue(statDef.getShiftValue(), statDef.getReverseShiftValue(), statDef.getIsShiftPercent());

			// Set the canExceedMax property
			if (lookupStatDef(stat.getName()).getCanExceedMax()) {
				stat.setCanExceedMax(true);
				if (Log.loggingDebug)Log.debug("MAX: canExceedMax for stat: " + stat.getName());
			}
		}

		// Send down the messages about the players current abilities/skills etc.
		Log.debug("COMBAT: Sending combat messages");
		ExtendedCombatMessages.sendAbilities(info.getOwnerOid(), info.getCurrentAbilities());
		ExtendedCombatMessages.sendActions(info.getOwnerOid(), info.getCurrentActions(), info.getCurrentActionBar());
		ExtendedCombatMessages.sendSkills(info.getOwnerOid(), info.getCurrentSkillInfo());

		int abilityID = tmpl.getDodge();
		OID playerOid = info.getOid();
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "combatSettings");
		props.put("DevMode", EnginePlugin.DevMode);
		props.put("dodge", abilityID);
		if (Log.loggingDebug)
			Log.debug("CombatPlugin.ReloadTemplates: playerOid=" + playerOid + " Send combatSettings props "+props);
		TargetedExtensionMessage emsg3 = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(emsg3);
		} else {
			//Mobs
			int tID = info.getIntProperty(WorldManagerClient.TEMPL_ID);
			//int level = info.statGetBaseValue("level");
			if(Log.loggingDebug) log.debug("Reload oid="+info.getOid()+" Template id="+tID);
			//WorldManagerClient.TEMPL_ID
			Template tmp = ObjectManagerClient.getTemplate(tID, ObjectManagerPlugin.MOB_TEMPLATE);
			if(Log.loggingDebug) log.debug("Reload tmp="+tmp);
			Map<String, Serializable> params = tmp.getSubMap(Namespace.COMBAT);
			if(Log.loggingDebug) log.debug("Reload params="+params);
			int minlevel = 1;
			int maxlevel = 1;
			if(params.size()>0)
				for (Map.Entry<String, Serializable> entry : params.entrySet()) {
					String key = entry.getKey();
					Serializable value = entry.getValue();
					if(Log.loggingDebug)	log.debug("Props key="+key+" value="+value);
					
					if (key.equals("weaponType")) {
						// Skip override weaponType	
					} else if (!key.startsWith(":")) {
						info.setProperty(key, value);
						if(Log.loggingDebug)Log.debug("COMBAT: added property: " + key);

					} else if (key.equals(":minLevel")) {
						// Grab the level value for the starting stats
						// calculation
						int stat = (Integer) value;
						minlevel = stat;
					} else if (key.equals(":maxLevel")) {
						// Grab the level value for the starting stats
						// calculation
						int stat = (Integer) value;
						maxlevel = stat;
					}
				}
			Random rand = new Random();
			int level = minlevel + rand.nextInt(maxlevel - minlevel + 1);
			
			HashMap<String, Integer> statOverrides = (HashMap<String, Integer>)params.get(":statOverrides");
			if(Log.loggingDebug) log.debug("Reload statOverrides="+statOverrides);
			
			
		/*	HashMap<String, AgisStat> statMap = getStartingStats(level, info.isMob(), statOverrides);
			if(Log.loggingDebug)log.debug("generateSubObject: statMap="+statMap);
			// Insert properties for public stats
			for (AgisStat stat : statMap.values()) {
				if(Log.loggingDebug)log.debug("generateSubObject: set stat ="+stat);
				//	info.setProperty(stat.getName(), stat);
					info.statSetBaseValue(stat.getName(),stat.getBaseValue());
			}		
			*/
			for (String stat : CombatPlugin.statDefMap.keySet()) {
				if (CombatPlugin.statDefMap.get(stat) instanceof VitalityStatDef) {
					continue;
				}
				if (statOverrides.containsKey(stat)) {
					if (info.getProperty(stat) == null) {
						// Add the stat to the player
						AgisStatDef asd = CombatPlugin.statDefMap.get(stat);
						AgisStat newStat = new AgisStat(asd.getId(),stat);
						info.setProperty(stat, newStat);
					}
					if(Log.loggingDebug) log.debug("Reload Override stat "+stat+" -> "+statOverrides.get(stat));
					info.statSetBaseValue(stat, statOverrides.get(stat));
				} else {
					AgisStatDef asd = CombatPlugin.statDefMap.get(stat);
					int baseValue = asd.getMobStartingValue();// .baseValue;
					float levelIncrease = asd.getMobLevelIncrease();// levelIncrease;
					float levelPercentIncrease = asd.getMobLevelPercentIncrease();// levelPercentIncrease;
					float value = baseValue + ((level - 1) * levelIncrease);
					value += value * ((level - 1) * (levelPercentIncrease / 100));

					if (info.getProperty(stat) == null) {
						// Add the stat to the player
						AgisStat newStat = new AgisStat(asd.getId(),stat);
						info.setProperty(stat, newStat);
					}
					if(Log.loggingDebug) log.debug("Reload stat=" + stat+" -> "+value);
					info.statSetBaseValue(stat, (int) value);
				}
			}
			
			
			if (params.containsKey(":speed_run")) {
				float speed = (float) params.get(":speed_run");
				info.statSetBaseValue(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED,  (int) speed);
			}
			
			if (params.containsKey("dmg-max")) {
				AgisStat newStat =  (AgisStat)params.get("dmg-max");
				info.setProperty("dmg-max", newStat);
				
			}
			
			if (params.containsKey("dmg-base")) {
				AgisStat newStat =  (AgisStat)params.get("dmg-base");
				info.setProperty("dmg-base", newStat);
				
			}
			
			for (AgisStatDef statDef : baseStats) {
				String statName = statDef.getName();
				AgisStat stat = (AgisStat) info.getProperty(statName);
				if(Log.loggingDebug)	Log.debug("STAT: updating stat: " + stat);
				statDef.update(stat, info);
			}
			
			for (String stat : CombatPlugin.statDefMap.keySet()) {
				if (CombatPlugin.statDefMap.get(stat) instanceof VitalityStatDef) {
					if (info.getProperty(stat) == null) {
						AgisStatDef s = CombatPlugin.statDefMap.get(stat);
						AgisStat astat = new AgisStat(s.getId(),stat);
						info.setProperty(stat, astat);
						VitalityStatDef statDef = (VitalityStatDef) CombatPlugin.statDefMap.get(stat);
						// Set it to the start percent
						int startingValue = 1;
						startingValue = statDef.getStartingValue(info);
						if (statOverrides.containsKey(stat)) {
							startingValue = statOverrides.get(stat);
						}
						info.statSetBaseValue(stat, startingValue);
						// stat.setBase(startingValue);

						// Add the vitality stat listing to the Combat Info
						if(Log.loggingDebug) log.debug("STAT: adding vitality stat with shift inteval: " + statDef.getShiftInterval());
						if ((statDef.checkShiftTarget(info)) && statDef.getShiftInterval() > 0) {
							info.addVitalityStat(astat, statDef.getShiftInterval());
						}
					}
				}
			}
			info.statSetBaseValue(LEVEL_STAT,  level);
			
			info.setProperty(CombatInfo.COMBAT_PROP_AUTOATTACK_BASE, info.getAutoAttackAbility());
		}
	}
	
	

	
	public class testmsgTimer implements Runnable {

		protected OID plyOid;
		protected CombatInfo info;

		public testmsgTimer(OID Oid, CombatInfo info) {
			this.plyOid = Oid;
			this.info = info;
			Log.debug("schedule setup");

		}

		@Override
		public void run() {
			Log.debug("schedule run");
			// Check user still has items
			LinkedList<Cooldown> cooldownList = CombatPlugin.getCooldowns(this.plyOid);
			if(Log.loggingDebug)Log.debug("Login: Apply Cooldowns " + cooldownList.size() + " to " + this.plyOid + " " + info.getName());
			if (cooldownList.size() > 0) {
				if(Log.loggingDebug)	Log.debug("Login: Apply Cooldowns " + cooldownList.size() + " to " + this.plyOid + " " + info.getName());
				Cooldown.activateCooldowns(cooldownList, info, 100,  100, 100, 3000);
			} else {
				Log.debug("Login: No Cooldowns ");
			}

		}

	}

	class CombatUnloadHook implements UnloadHook {
		public void onUnload(Entity e) {
			if(Log.loggingDebug)Log.debug("COMBAT: Unloading CombatInfo for Entity: " + e.getOid());
			CombatInfo info = (CombatInfo) e;
			// Remove to stat tick
			// statTick.RemoveCombatInfo(info);
			if (statTicks.containsKey(e.getOid())) {
				statTicks.get(e.getOid()).disable();
			}
			// Not sure if turning this off for mobs will break anything
			/*if (info.isUser()) {
				AgisEffect.removeNonContinuousEffects(info, false);
			}*/
			if(Log.loggingDebug)	Log.debug("COMBAT: Unloaded CombatInfo for Entity: " + e.getOid());
			unsubscribeForMob(info.getOid());
		}
	}

	/**
	 * Creates the CombatInfo subObject for the mob/player characters and
	 * initialises the properties including the combat stats.
	 * 
	 * @author Andrew Harrison
	 * 
	 */
	class CombatPluginGenerateSubObjectHook extends GenerateSubObjectHook {
		public CombatPluginGenerateSubObjectHook() {
			super(CombatPlugin.this);
		}
		public SubObjData generateInstanceSubObject(OID masterOid, Boolean persistent) {
			WorldManagerInstance instance = createInstanceEntity(masterOid);
			instance.setPersistenceFlag(persistent);

			if (persistent)
				Engine.getPersistenceManager().persistEntity(instance);

			subscribeForObject(masterOid);
			hostInstance(masterOid);

			// ##
			// WorldManagerClient.sendPerceiverRegionsMsg(0L,
			// Geometry.maxGeometry(), null);

			return new SubObjData();
		}
		
		public SubObjData generateSubObject(Template template, Namespace namespace, OID masterOid) {
			if (Log.loggingDebug)
				log.debug("GenerateSubObjectHook: masterOid=" + masterOid + ", namespace="+namespace+", template=" + template);
		
				
			Boolean persistent = (Boolean) template.get(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT);
			if (persistent == null)
				persistent = false;

			if (namespace == CombatClient.INSTANCE_NAMESPACE) {
				return generateInstanceSubObject(masterOid, persistent);
			}
			
			if(template == null ) {
				if (Log.loggingDebug)log.debug("GenerateSubObjectHook: masterOid="+masterOid+" namespace="+namespace+" templateId is null");
				return null;
			}
			
			Map<String, Serializable> props = template.getSubMap(Namespace.COMBAT);
			if (props == null) {
				Log.warn("GenerateSubObjectHook: no props in ns " + Namespace.COMBAT);
				return null;
			}
			if(template.getTemplateID()==-1 && props.size() < 3) {
				if (Log.loggingDebug)log.debug("GenerateSubObjectHook: masterOid="+masterOid+" namespace="+namespace+" templateId = -1 and Combat params count is too low");
				return null;
			}
			// generate the subobject
			CombatInfo cinfo = new CombatInfo(masterOid, template.getTemplateID());
			cinfo.setName(template.getName());
			cinfo.setCurrentCategory(1);

			cinfo.setPersistenceFlag(persistent);
			if(Log.loggingDebug)Log.debug("Combat generateSubObject "+cinfo);
			int minlevel = 1;
			int maxlevel = 1;
			// copy properties from template to object
			if(props.size()>0)
			for (Map.Entry<String, Serializable> entry : props.entrySet()) {
				String key = entry.getKey();
				Serializable value = entry.getValue();
				if(Log.loggingDebug)	log.debug("Props key="+key+" value="+value);
				if (!key.startsWith(":")) {
					cinfo.setProperty(key, value);
					if(Log.loggingDebug)Log.debug("COMBAT: added property: " + key);

				} else if (key.equals(":minLevel")) {
					// Grab the level value for the starting stats
					// calculation
					int stat = (Integer) value;
					minlevel = stat;
				} else if (key.equals(":maxLevel")) {
					// Grab the level value for the starting stats
					// calculation
					int stat = (Integer) value;
					maxlevel = stat;
				}
			}

			Random rand = new Random();
			int level = minlevel + rand.nextInt(maxlevel - minlevel + 1);

			// Get any stat overrides - these are for mob templates
			HashMap<String, Integer> statOverrides = null;
			if (props.containsKey(":statOverrides"))
				statOverrides = (HashMap) props.get(":statOverrides");

			int statProfile =-1;
			if (props.containsKey(":statProfile"))
				statProfile = (int) props.get(":statProfile");
			cinfo.setStatProfile(statProfile);
			// Get the starting stats and values
			HashMap<String, AgisStat> statMap = getStartingStats(cinfo.getExpProfile(), level, cinfo.isMob(), statOverrides,statProfile);
			if(Log.loggingDebug)log.debug("generateSubObject: statMap="+statMap);
			// Insert properties for public stats
			for (AgisStat stat : statMap.values()) {
				if(Log.loggingDebug)log.debug("generateSubObject: set stat ="+stat);
					cinfo.setProperty(stat.getName(), stat);
			}

			// Make sure no stats are missing
			for (Map.Entry<String, AgisStatDef> statEntry : statDefMap.entrySet()) {
				String statName = statEntry.getKey();
				AgisStat stat = (AgisStat) cinfo.getProperty(statName);
				if (stat == null) {
					if(Log.loggingDebug)Log.debug("STAT: stat is null - " + statName);
//					stat = new AgisStat(statName);
//					cinfo.setProperty(statName, stat);
				}else{

				}
			}
			
			//Mob Force set run speed for movement_speed
			if (props.containsKey(":speed_run")) {
				float speed = (float) props.get(":speed_run");
				AgisStatDef asd = lookupStatDef(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED);
				cinfo.setProperty(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, new AgisStat(asd.getId(),AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, (int) speed));
			}
			// Run the update for base stats before running through vitality
			// stats so the
			// starting values will be correct
			for (AgisStatDef statDef : baseStats) {
				String statName = statDef.getName();
				AgisStat stat = (AgisStat) cinfo.getProperty(statName);
				if(Log.loggingDebug)	Log.debug("STAT: updating stat: " + stat);
				if(stat!=null) {
					statDef.update(stat, cinfo);
				} else {
					if(Log.loggingDebug)
						Log.debug("STAT: AgisStatDef stat "+statName+ " is not on the "+cinfo);
				}
			}

			// Look through all stats and grab all vitality stats to apply a few
			// extra settings
			for (Map.Entry<String, AgisStatDef> statEntry : statDefMap.entrySet()) {
				String statName = statEntry.getKey();
				AgisStat stat = (AgisStat) cinfo.getProperty(statName);

				// If the stat is a VitalityStatDef (or child) then add the stat
				// to the statDef
				// and set the base value
				if(stat!=null) {
					if (lookupStatDef(stat.getName()) instanceof VitalityStatDef) {
						VitalityStatDef statDef = (VitalityStatDef) lookupStatDef(stat.getName());
						// Set it to the start percent
						int startingValue = 1;
						startingValue = statDef.getStartingValue(cinfo);
						cinfo.statSetBaseValue(statName, startingValue);
						// stat.setBase(startingValue);

						// Add the vitality stat listing to the Combat Info
						if (Log.loggingDebug)
							Log.debug("STAT: adding vitality stat with shift inteval: " + statDef.getShiftInterval());
						if ((statDef.checkShiftTarget(cinfo)) && statDef.getShiftInterval() > 0) {
							cinfo.addVitalityStat(stat, statDef.getShiftInterval());
						}
					}
				} else {
					if(Log.loggingDebug)
						Log.debug("STAT: VitalityStatDef stat "+statName+ " is not on the "+cinfo);
				}
			}

			// Save auto attack ability for future reference
			cinfo.setProperty(CombatInfo.COMBAT_PROP_AUTOATTACK_BASE, cinfo.getAutoAttackAbility());

			if (Log.loggingDebug)
				log.debug("GenerateSubObjectHook: created entity " + cinfo);

			// register the entity
			registerCombatInfo(cinfo);

			// If the object is a players character create the skillInfo
			if (cinfo.isUser()) {
				if (USE_PLAYER_SET_RESPAWN_LOCATIONS) {
					// Read in settings from the character template
					int respawnInstance = (Integer) props.get(":respawnInstance");
					Point respawnPoint = (Point) props.get(":respawnPoint");
					cinfo.setRespawnInstance(respawnInstance);
					cinfo.setRespawnPosition(respawnPoint);
					// cinfo.setRespawnInstance(29);
					// cinfo.setRespawnPosition(new Point(-28, 8.5f, -49));
				}

				ArrayList<Integer> skills = (ArrayList<Integer>) props.get(":startingSkills");
				createNewSkillInfo(cinfo, masterOid, skills);

				int race = (Integer) props.get(":race");
				int aspect = cinfo.aspect();
				CharacterTemplate tmpl = ClassAbilityPlugin.getCharacterTemplate(race + " " + aspect);
				ClassAbilityPlugin.calculatePlayerStats(cinfo, tmpl);
			}

			if (persistent)
				Engine.getPersistenceManager().persistEntity(cinfo);

			// Add to stat tick
			CombatStatTick statTick = new CombatStatTick(cinfo);
			// statTick.AddCombatInfo(cinfo);
			Engine.getExecutor().schedule(statTick, 10, TimeUnit.SECONDS);
			statTicks.put(masterOid, statTick);
			
			subscribeForMob(masterOid);
			// send a response message
			return new SubObjData();
		}
	}

	/**
	 * Creates the skillInfo object for the CombatInfo object based on the skill
	 * templates loaded in from the database.
	 * 
	 * @param info
	 * @param mobOid
	 * @param skills
	 */
	protected void createNewSkillInfo(CombatInfo info, OID mobOid, ArrayList<Integer> skills) {
		// First get the skills the character will get
		SkillInfo skillInfo = new SkillInfo(info.getCurrentCategory());
		ArrayList<Integer> abilities = new ArrayList<Integer>();
		ArrayList<String> actions = new ArrayList<String>();
		// First give the players auto attack
		abilities.add(PLAYER_ATTACK_ABILITY);
		for (int skill : skills) {
			if(Log.loggingDebug)Log.debug("SKILL: adding skill: " + skill);
			SkillTemplate tmpl = Agis.SkillManager.get(skill);
			if (tmpl == null)
				continue;
			if(Log.loggingDebug)Log.debug("SKILL: 1 adding skill: " + tmpl.getSkillName());
			skillInfo.addSkill(tmpl);

			if(Log.loggingDebug)Log.debug("SKILL: 2 adding skill: " + skill);
			ArrayList<Integer> abilityIDs = tmpl.getStartAbilityIDs();
			if(Log.loggingDebug)Log.debug("SKILL: got " + abilityIDs.size() + " abilities");
			for (int ability : abilityIDs) {
				// abilities.add(ability);
				// actions.add("a" + ability);
				SkillInfo.learnAbility(info, ability);
			}
			SkillInfo.applyStatModifications(info, tmpl, 1);
		}
		// Store the information in the combat info
		// info.setCurrentAbilities(abilities);
		// info.setCurrentActionsOnCurrentBar(actions);
		info.setCurrentSkillInfo(skillInfo);
		// Send down the messages about the players current abilities/skills
		// etc.
		Log.debug("AJ: Sending combat messages");
		ExtendedCombatMessages.sendAbilities(info.getOwnerOid(), info.getCurrentAbilities());
		ExtendedCombatMessages.sendActions(info.getOwnerOid(), info.getCurrentActions(), info.getCurrentActionBar());
		ExtendedCombatMessages.sendSkills(info.getOwnerOid(), info.getCurrentSkillInfo());
	}

	/**
	 * Hook for the GetPlayerStatValueMessage. Gets the value of the specified stat
	 * for the player in question and sends it back to the remote call procedure
	 * that sent the message out.
	 * 
	 * @author Andrew Harrison
	 * 
	 */
	class GetPlayerStatValueHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
		  	long start = System.nanoTime();
		    CombatClient.GetPlayerStatValueMessage EBMsg = (CombatClient.GetPlayerStatValueMessage) msg;
			Log.debug("SKILL: got GetPlayerSkillLevelMessage");
			OID oid = EBMsg.getSubject();
			String statName = EBMsg.getStatName();
		//	List<Lock> requiredLocks =  new ArrayList<Lock>();
			CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);
			if(Log.loggingDebug)
				Log.debug("SKILL: got GetPlayerSkillLevelMessage CombatInfo="+cInfo+" for Oid="+oid+" statName="+statName);
			if(cInfo ==null) {
				Engine.getAgent().sendIntegerResponse(msg, 0);
				return true;
			}
			if(Log.loggingTrace)
				Log.trace("CombatPlugin.GetPlayerStatValueHook 1 nanoseconds: " + (System.nanoTime() - start));
    		int statValue = -1;
			if (lookupStatDef(statName) != null) {
				statValue = cInfo.statGetCurrentValue(statName);
			}
				if(Log.loggingTrace)
				Log.trace("CombatPlugin.GetPlayerStatValueHook end nanoseconds: " + (System.nanoTime() - start));

			Engine.getAgent().sendIntegerResponse(msg, statValue);
			return true;
		}
	}

	
	class GetPlayerCooldownHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
		  	long start = System.nanoTime();
		  	PropertyMessage EBMsg = (PropertyMessage) msg;
			Log.debug("GetPlayerCooldownHook start");
			OID oid = EBMsg.getSubject();
			CombatInfo cInfo = getCombatInfo(oid);
			if(Log.loggingDebug)
				Log.debug("GetPlayerCooldownHook CombatInfo="+cInfo+" for Oid="+oid);
			if(cInfo ==null) {
				Engine.getAgent().sendObjectResponse(msg, new HashMap<String, State>());
				return true;
			}
			if(Log.loggingTrace)
				Log.trace("CombatPlugin.GetPlayerCooldownHook 1 nanoseconds: " + (System.nanoTime() - start));
            Map<String, Cooldown.State> cooldowns = new HashMap<>(cInfo.getCooldownMap());
			if(Log.loggingTrace)
				Log.trace("CombatPlugin.GetPlayerCooldownHook end nanoseconds: " + (System.nanoTime() - start));
			if(Log.loggingDebug)
				Log.debug("GetPlayerCooldownHook CombatInfo="+cInfo+" for Oid="+oid+" cooldowns="+cooldowns);
			Engine.getAgent().sendObjectResponse(msg, cooldowns);
			return true;
		}
	}

	
	
	
	
	class AutoAttackHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			CombatClient.AutoAttackMessage autoAtkMsg = (CombatClient.AutoAttackMessage) msg;
			OID oid = autoAtkMsg.getSubject();
			CombatInfo obj = getCombatInfo(oid);
			if (obj == null)
				return true;
			OID targetOid = autoAtkMsg.getTargetOid();
			CombatInfo target = getCombatInfo(targetOid);
			Boolean status = autoAtkMsg.getAttackStatus();
			Lock objLock = obj.getLock();
			Lock targetLock = null;
		    if (target != null) targetLock = target.getLock();//FIXME ZBDS 
		    List<Lock> requiredLocks =  new ArrayList<Lock>();
		    requiredLocks.add(objLock);
		    if(!requiredLocks.contains(targetLock)) {
		    	  requiredLocks.add(targetLock);
		    }
			try {
				//   ObjectLockManager.lockAll(requiredLocks);

			/*	objLock.lock();
			//	while ((targetLock != null) && !targetLock.tryLock()) {
				while ((targetLock != null) && !targetLock.tryLock(100, TimeUnit.MILLISECONDS)) {
						objLock.unlock();
					Thread.yield();
					objLock.lock();
				}
*/
				if (Log.loggingDebug)
					log.debug("AutoAttackHook.processMessage: oid=" + oid + ", targetOid=" + targetOid + ", status=" + status);

				if (!status || obj.dead() || (target == null) || target.dead()) {
					obj.stopAutoAttack();
					if (target != null) {
						OID tagOwner = (OID) target.getProperty(CombatInfo.COMBAT_TAG_OWNER);
						if (tagOwner != null && tagOwner.equals(oid)) {
							// You owned the tag, but now you have stopped so
							// lets clear the ownership
							OID newOwner = null;
							if (getAttackers(targetOid) != null) {
								for (OID attacker : getAttackers(targetOid)) {
									if (!attacker.equals(oid))
										newOwner = attacker;
								}
								target.setProperty(CombatInfo.COMBAT_TAG_OWNER, newOwner);
							}
						}
					}
					Long petOwner = (Long) obj.getProperty("petOwner");
					if (petOwner != null) {
						AgisMobClient.petTargetLost(obj.getOid());
					}

				} else {
					obj.setAutoAttack(targetOid);
				}
				return true;
			}
			catch (Exception e) {
				Log.exception("CombatPlugin.run: got exception", e);
				Log.debug("CombatPlugin.run: got exception: " + e);
			} finally {
				// ObjectLockManager.unlockAll(requiredLocks);
				/*if (targetLock != null)
					targetLock.unlock();
				objLock.unlock();*/
				return true;
			}
		}
	}

	class StopAutoAttackHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			CombatClient.stopAutoAttackMessage EBMsg = (CombatClient.stopAutoAttackMessage) msg;
			OID oid = EBMsg.getSubject();
			if(Log.loggingDebug)Log.debug("COMBATPLUGIN: stop autoAttack caught: " + EBMsg);
			CombatInfo info = getCombatInfo(oid);
			info.stopAutoAttack();
			return true;
		}
	}
	class AbilityVectorHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			int abilityId = (int) message.getProperty("aid");
			AOVector vector = (AOVector) message.getProperty("v");
			if(Log.loggingDebug)log.debug("AbilityVectorHook: playerOid="+playerOid+" abilityId="+abilityId+" vector="+vector);
			CombatInfo obj = getCombatInfo(playerOid);
			AgisAbilityState aas = obj.getRunAbility(abilityId);
			aas.setDestLocation(vector.toPoint());

			if(Log.loggingDebug)log.debug("AbilityVectorHook: playerOid="+playerOid+" end");
			return true;
		}
	}


	class StartAbilityHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			CombatClient.StartAbilityMessage abilityMsg = (CombatClient.StartAbilityMessage) msg;
			OID oid = abilityMsg.getSubject();
			OID targetOid = abilityMsg.getTargetOid();
			int abilityID = abilityMsg.getAbilityID();
			Point loc = abilityMsg.getLocation();
			Point dstLoc = abilityMsg.getDestLocation();
			AgisItem item = (AgisItem) abilityMsg.getItem();
			int claimID = abilityMsg.getClaimID();
			int claimObjID = abilityMsg.getClaimObjID();
			boolean response = abilityMsg.response();
			if(Log.loggingDebug)log.debug("StartAbilityHook.processMessage: oid=" + oid + ", targetOid=" + targetOid + " ability=" + abilityID + ", loc=" + loc+" claimID="+claimID+" claimObjID="+claimObjID+" "+msg.getSenderName()+" dstLoc="+dstLoc);

			CombatInfo obj = getCombatInfo(oid);
			CombatInfo target = obj;
			if (targetOid != null && targetOid.toLong() != -1)
				target = getCombatInfo(targetOid);
			else
				log.debug("AGISABILITY: Target has no combat info.");
			AgisAbility ability = Agis.AbilityManager.get(abilityID);
			if(ability==null) {
				log.error("CombatPlugin StartAbility ability "+abilityID+" is null break");
				if(response)
					Engine.getAgent().sendBooleanResponse(abilityMsg, false);
				return true;
			}
			if(ability.isChild()){
				if(obj.getComboTime(abilityID) < System.currentTimeMillis()){
					if(Log.loggingDebug) log.debug("CombatPlugin StartAbility ability "+abilityID+" is Child but cant be run break");
					if(response)
						Engine.getAgent().sendBooleanResponse(abilityMsg, false);
					return true;
				} else {
					obj.removeComboTime(abilityID);
					ExtendedCombatMessages.sendActions(obj.getOwnerOid(), obj.getCurrentActions(), obj.getCurrentActionBar());
				}
			}

			if (ability.getInterceptType() == 1) {
				target = new CombatInfo(targetOid, 0);
			}

			long t0 = System.currentTimeMillis();
			if (ability.isToggle()) {
				if(Log.loggingDebug) {	
					Log.debug("AGISABILITY: ability: " + abilityID + " is toggle");
				Log.debug("AGISABILITY: ability: " + abilityID + " is toggle "+obj.currentRunAbilities().keySet()+" "+obj.currentRunAbilities());
				}
				if (obj.currentRunAbilities().containsKey(ability.getID())) {
					if(Log.loggingDebug)Log.debug("AGISABILITY: toggle ability: " + abilityID + " is runing abort it");
					AgisAbility.deactivateAbility(obj.currentRunAbilities().get(ability.getID()));
					return true;
				}
				if(obj.currentRunAbilities().size() > 0) {
					int tag = ability.getTagToDisable();
					int tagCount = ability.getTagToDisableCount();
					HashMap<Integer,AgisAbilityState> list = new HashMap<Integer,AgisAbilityState>(obj.currentRunAbilities());
					int i = 1;
					for(AgisAbilityState aas : list.values()) {
						if(aas.getAbility().getTags().contains(tag)) {
							if(i>=tagCount) {
								AgisAbility.deactivateAbility(aas);
								i++;
							}
						}
					}
				}
			}



			Prometheus.registry().timer("start_ability_message", "phase", "toggle", "ability",
					ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - t0));
			
			// Check if ability can attack building
			if (!ability.getAttackBuilding() && claimID > 0 && claimObjID > 0) {
				if(Log.loggingDebug)Log.debug("AGISABILITY: ability for not buildings cant attack building: " + abilityID);
				ExtendedCombatMessages.sendAbilityFailMessage(obj, AbilityResult.INVALID_TARGET, abilityID,ability.getCostProperty());
				if(response)
					Engine.getAgent().sendBooleanResponse(abilityMsg, false);
				return true;
			}
			// First check if the player must know the ability
			if (abilityMsg.mustKnow()) {
				Collection<Integer> currentAbilities = obj.getCurrentAbilities();
				if (!currentAbilities.contains(abilityID)) {
					if(Log.loggingDebug)Log.debug("AGISABILITY: player does not know this ability: " + abilityID);
					if(response)
						Engine.getAgent().sendBooleanResponse(abilityMsg, false);
					return true;
				}
			}

			long powerUpTime = 0L;
			if(ability.getPowerUpDefinitions().size()>1) {
				if (Log.loggingDebug)Log.debug("AGISABILITY: ability: " + abilityID + " PowerUp");
				if (obj.powerUpAbilities.containsKey(abilityID)) {
					long lastUse = obj.powerUpAbilities.remove(abilityID);
					powerUpTime = System.currentTimeMillis() - lastUse;
					CoordinatedEffectState ces = obj.getPowerUpCoordEffectState();
					if(ces != null) {
						ces.invokeCancel();
						obj.setPowerUpCoordEffectState(null);
					}
					if (Log.loggingDebug)Log.debug("AGISABILITY: ability: " + abilityID + " PowerUp lastUse=" + lastUse + " powerUpTime=" + powerUpTime);
					Map<String, Serializable> props = new HashMap<String, Serializable>();
					props.put("ext_msg_subtype", "powerUpEnd");
					props.put("ability", abilityID);
					TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
					Engine.getAgent().sendBroadcast(_msg);
				} else {
					obj.powerUpAbilities.put(abilityID, System.currentTimeMillis());
					if (Log.loggingDebug)Log.debug("AGISABILITY: ability: " + abilityID + " PowerUp start powerUp");

					Map<String, Serializable> props = new HashMap<String, Serializable>();
					props.put("ext_msg_subtype", "powerUp");
					props.put("ability", abilityID);
					TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
					Engine.getAgent().sendBroadcast(_msg);
					CoordinatedEffect ce = ability.getPowerUpCoordEffect();
					if(ce != null) {
						CoordinatedEffectState cef = ce.invoke(oid, oid);
						obj.setPowerUpCoordEffectState(cef);
					}
					return true;
				}
			}

			log.debug("CombatPlugin 922 before AgisAbility.startAbility "+ability+" obj="+obj+" target="+target+" ");
			boolean rv = AgisAbility.startAbility(ability, obj, target, item, loc, claimID, claimObjID,dstLoc,powerUpTime);
			if(Log.loggingDebug)log.debug("CombatPlugin after AgisAbility.startAbility abilityID="+abilityID+" result "+rv);
			if(response) {
				Engine.getAgent().sendBooleanResponse(abilityMsg, rv);
			}

			return true;
		}
	}

	class ReleaseObjectHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			CombatClient.ReleaseObjectMessage releaseMsg = (CombatClient.ReleaseObjectMessage) msg;
			OID oid = releaseMsg.getSubject();

			if (Log.loggingDebug)
				log.debug("ReleaseObjectHook.processMessage: oid=" + oid);

			CombatInfo info = getCombatInfo(oid);
			if (info == null) {
				if (Log.loggingDebug)
					log.debug("RELEASE: no combat info found oid=" + oid);
				return true;
			}
			if (!info.dead()) {
				if (Log.loggingDebug)
					log.debug("RELEASE: subject not dead oid=" + oid);
				return true;
			}

			if(info.getDeathPermanently()) {
		        	Log.debug("RELEASE: subject has Death Permanently");
		        	return true;
		    }
			// TODO Intercept release message if the player is in an arena
			Integer arenaID = (Integer) info.getProperty(CombatInfo.COMBAT_PROP_ARENA_ID);
			if (arenaID != null && arenaID > -1) {
				// Send message to arena system to handle the release
				log.debug("RELEASE: subject dead oid=" + oid + " is in Arena redirect");
				ArenaClient.arenaReleaseRequest(oid);
				return true;
			}

			info.setCombatState(false);
			if (releaseMsg.turnToSpirit() && info.getState() != null && info.getState().equals(CombatInfo.COMBAT_STATE_SPIRIT)) {
				// If player is already a spirit and they are trying to turn
				// into a spirit - return
				return true;
			}

			EnginePlugin.setObjectPropertiesNoResponse(info.getOwnerOid(), Namespace.WORLD_MANAGER, WorldManagerClient.WORLD_PROP_NOMOVE, false,
					WorldManagerClient.WORLD_PROP_NOTURN, false);
			Log.debug("RELEASE: about to generate loot");

			AgisInventoryClient.generatePlayerLoot(oid, null, WorldManagerClient.getObjectInfo(oid).loc);

			if (!releaseMsg.turnToSpirit()) {
				// Handle respawn positioning
				info.clearState(CombatInfo.COMBAT_STATE_SPIRIT);
				relocateReleasedPlayer(oid, info);
				// Do this after relocating so mobs don't aggro from where the player died
				info.setDeadState(false);
				setReleaseStatValues(info);
				AgisWorldManagerClient.sendRevived(oid);
			} else {
				info.setState(CombatInfo.COMBAT_STATE_SPIRIT);
				if (SPIRIT_EFFECT > 0)
					AgisEffect.applyEffect(Agis.EffectManager.get(SPIRIT_EFFECT), info, info, -1);
			}

			return true;
		}
	}

	public static void setReleaseStatValues(CombatInfo info) {
		for (Map.Entry<String, AgisStatDef> statEntry : statDefMap.entrySet()) {
			String statName = statEntry.getKey();
			// AgisStatDef statDef = statEntry.getValue();

			if(Log.loggingDebug) Log.debug("setReleaseStatValues stat="+statName);
			AgisStat stat = (AgisStat) info.getProperty(statName);
			if (stat==null) {
				log.error("setReleaseStatValues stat="+statName+" is null make stat ");
				return;
				//stat = new AgisStat(statName);
			}
				
			// If the stat is a VitalityStatDef (or child) then add the stat to
			// the statDef
			if (lookupStatDef(stat.getName()) instanceof VitalityStatDef) {
				VitalityStatDef statDef = (VitalityStatDef) lookupStatDef(stat.getName());
				if (statDef.getReleaseResetPercent() == -1)
					continue;
				// Reset to set percent
				int maxval = info.statGetMaxValue(statName);
				int currentval = info.statGetCurrentValue(statName);
				currentval = maxval * statDef.getReleaseResetPercent() / 100;
				if(Log.loggingDebug) Log.debug("RELEASE: setting vitality stat: " + statName + " to current: " + currentval);
				info.statSetBaseValue(statName, currentval);
			}
		}
	}

	public static void setDeathStatValues(CombatInfo info) {
		for (Map.Entry<String, AgisStatDef> statEntry : statDefMap.entrySet()) {
			String statName = statEntry.getKey();
			// AgisStatDef statDef = statEntry.getValue();

			AgisStat stat = (AgisStat) info.getProperty(statName);
			// If the stat is a VitalityStatDef (or child) then add the stat to
			// the statDef
			if (lookupStatDef(stat.getName()) instanceof VitalityStatDef) {
				VitalityStatDef statDef = (VitalityStatDef) lookupStatDef(stat.getName());
				if (statDef.getDeathResetPercent() == -1)
					continue;
				// Reset to set percent
				int maxval = info.statGetMaxValue(statName);
				int currentval = info.statGetCurrentValue(statName);
				currentval = maxval * statDef.getDeathResetPercent() / 100;
				if(Log.loggingDebug)Log.debug("DEATH: setting vitality stat: " + statName + " to current: " + currentval);
				info.statSetBaseValue(statName, currentval);
			}
		}
	}

	/**
	 * Calculates where the player should be moved to upon releasing. Takes into
	 * account variables such as their race, current zone/subzone, and whether or
	 * not they are in an arena.
	 * 
	 * @param oid:
	 *            the oid of the player who has released.
	 */
	private void relocateReleasedPlayer(OID oid, CombatInfo info) {
		Log.debug("RELEASE: moving player: " + oid);
		/*
		 * int arenaID = (Integer) EnginePlugin.getObjectProperty(oid,
		 * CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID); if (arenaID != -1)
		 * { arenaRelease(oid); return; //if (relocated) // return; }
		 */
		BasicWorldNode wnode = WorldManagerClient.getWorldNode(oid);
		OID instanceOid = WorldManagerClient.getObjectInfo(oid).instanceOid;
		int instanceID = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;

		if (USE_PLAYER_SET_RESPAWN_LOCATIONS && info.getRespawnInstance() > 0) {
			// Get the players set respawn location instead
			if (instanceID != info.getRespawnInstance()) {
				if (RESPAWN_IN_DUNGEON) {
					InstanceTemplate tmpl = InstanceClient.getInstanceTemplate(instanceID);
					if (tmpl.getIslandType() != InstanceTemplate.ISLAND_TYPE_WORLD) {
						Marker marker = InstanceClient.getMarker(instanceOid, "spawn");
						wnode.setLoc(marker.getPoint());
						WorldManagerClient.updateWorldNode(oid, wnode, true);
						return;
					}
				}
				// Need to change instance
				AgisWorldManagerClient.sendChangeInstance(oid, info.getRespawnInstance(), info.getRespawnPosition());
			} else {
				// Just changing location inside the same instance
				wnode.setLoc(info.getRespawnPosition());
				WorldManagerClient.updateWorldNode(oid, wnode, true);
			}
			return;
		}

		float distanceToClosestGraveyard = Float.MAX_VALUE;
		Graveyard closestGraveyard = null;
		HashMap<Integer, PlayerFactionData> pfdMap = (HashMap) EnginePlugin.getObjectProperty(oid, Namespace.FACTION, "factionData");
		if (graveyards.containsKey(instanceID)) {
			for (Graveyard gy : graveyards.get(instanceID)) {
				// Check if graveyard is compatible for the players faction/reputation
				int playerFaction = (Integer) EnginePlugin.getObjectProperty(oid, Namespace.FACTION, FactionStateInfo.FACTION_PROP);
				if(Log.loggingDebug)log.debug("Graveyard: playerFaction != gy.getFactionReq() playerFaction:" + playerFaction + " gy.getFactionReq():" + gy.getFactionReq() + " " + gy.getLoc());
				if (playerFaction != gy.getFactionReq()) {
					// If the players faction is not the same as the graveyards, make sure they have
					// high enough rep to use it
					if (!pfdMap.containsKey(gy.getFactionReq())) {
						if(Log.loggingDebug)log.debug("Graveyard: !pfdMap.containsKey(gy.getFactionReq()) " + (!pfdMap.containsKey(gy.getFactionReq())) + " gy faction:" + gy.getFactionReq() + " "
								+ gy.getName() + " continue " + gy.getLoc());
						// TODO: Player hasn't met the faction yet. Should do a check for default stance
						// - will do this later
						continue;
					}
					PlayerFactionData pfd = pfdMap.get(gy.getFactionReq());
					if(Log.loggingDebug)log.debug("Graveyard: FACTION: got faction from players FactionDataMap " + " " + gy.getLoc());
					int reputation = pfd.getReputation();
					if(Log.loggingDebug)log.debug("Graveyard: FactionPlugin.calculateStanding(reputation) < gy.getFactionRepReq():" + (FactionPlugin.calculateStanding(reputation) < gy.getFactionRepReq())
							+ " " + gy.getLoc());
					if (FactionPlugin.calculateStanding(reputation) < gy.getFactionRepReq()) {
						log.debug("Graveyard: FactionPlugin.calculateStanding(reputation) < gy.getFactionRepReq() false - continue" + " " + gy.getLoc());
						continue;
					}
				}

				float distance = Point.distanceTo(gy.getLoc(), wnode.getLoc());
				if(Log.loggingDebug)log.debug("Graveyard: distance:" + distance + " " + gy.getLoc());
				if (distance < distanceToClosestGraveyard) {
					closestGraveyard = gy;
					distanceToClosestGraveyard = distance;
				}
			}
		}
		if(Log.loggingDebug)log.debug("Graveyard: distanceToClosestGraveyard:" + distanceToClosestGraveyard);

		if (closestGraveyard != null) {
			wnode.setLoc(closestGraveyard.getLoc());
		} else {
			Marker marker = InstanceClient.getMarker(instanceOid, "spawn");
			wnode.setLoc(marker.getPoint());
		}

		WorldManagerClient.updateWorldNode(oid, wnode, true);
	}

	/**
	 * Removes the Spirit Effect from the specified player if they have one.
	 * 
	 * @param oid
	 */
	private void removeSpiritEffect(OID oid) {
		// Remove the mount effect from the player
		CombatInfo player = getCombatInfo(oid);
		EffectState spiritEffect = null;
		if (player != null) {
			if (SPIRIT_EFFECT > 0) {
				for (EffectState state : player.getCurrentEffects()) {
					if(Log.loggingDebug)Log.debug("SPIRIT: found effect: " + state.getEffectID());
					if (state.getEffect() != null && state.getEffectID() == SPIRIT_EFFECT) {
						spiritEffect = state;
						if(Log.loggingDebug)	Log.debug("SPIRIT: found effect to remove: " + spiritEffect.getEffectID());
						break;
					}
				}

				if (spiritEffect != null) {
					AgisEffect.removeEffect(spiritEffect, player);
				}
			}
		}
	}

	/**
	 * Deals with players who have released while in an arena. It determines their
	 * team and will teleport them to the appropriate graveyard.
	 * 
	 *  the oid of the player who has released.
	 */
	class ReleaseArenaObjectHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage eMsg = (ExtensionMessage) msg;
			OID oid = eMsg.getSubject();
			CombatInfo info = getCombatInfo(oid);
			if (info == null) {
				if (Log.loggingDebug)
					log.debug("RELEASE: no combat info found oid=" + oid);
				return true;
			}
			if (!info.dead()) {
				if (Log.loggingDebug)
					log.debug("RELEASE: subject not dead oid=" + oid);
				return true;
			}

			// Intercept release message if the player is in an arena
			Integer arenaID = (Integer) info.getProperty(CombatInfo.COMBAT_PROP_ARENA_ID);
			if (arenaID == null && arenaID < 1) {
				// Player is not in an arena so they cannot use this function
				return true;
			}

			info.setCombatState(false);

			boolean turnToSpirit = (Boolean) eMsg.getProperty("turnToSpirit");
			if (turnToSpirit && info.getState() != null && info.getState().equals(CombatInfo.COMBAT_STATE_SPIRIT)) {
				// If player is already a spirit and they are trying to turn
				// into a spirit - return
				return true;
			}

			boolean allowMovement = (Boolean) eMsg.getProperty("allowMovement");

			if (allowMovement) {
				Log.debug("RELEASE: allowMovement");

				EnginePlugin.setObjectPropertiesNoResponse(info.getOwnerOid(), Namespace.WORLD_MANAGER, WorldManagerClient.WORLD_PROP_NOMOVE, false,
						WorldManagerClient.WORLD_PROP_NOTURN, false);
			}
			// Log.debug("RELEASE: about to generate loot");

			// AgisInventoryClient.generatePlayerLoot(oid, null,
			// WorldManagerClient.getObjectInfo(oid).loc);

			if (!turnToSpirit) {
				// Handle respawn positioning
				info.clearState(CombatInfo.COMBAT_STATE_SPIRIT);
				Point respawnLoc = (Point) eMsg.getProperty("respawnLoc");
				BasicWorldNode wnode = WorldManagerClient.getWorldNode(oid);
				wnode.setLoc(respawnLoc);
				WorldManagerClient.updateWorldNode(oid, wnode, true);

				// Do this after relocating so mobs don't aggro from where the player died
				info.setDeadState(false);
				setReleaseStatValues(info);
			    AgisWorldManagerClient.sendRevived(oid);
			      
			} else {
				info.setState(CombatInfo.COMBAT_STATE_SPIRIT);
				if (SPIRIT_EFFECT > 0)
					AgisEffect.applyEffect(Agis.EffectManager.get(SPIRIT_EFFECT), info, info, -1);
			}

			return true;
		}
	}

	/**
	 * This will handle the death of a player or mob. It may try to activate any
	 * after-death effects and it will also send out messages for other
	 * death-related events.
	 * 
	 * @param obj
	 *            : the combatInfo object for the player or mob that died
	 */
	public static void handleDeath(CombatInfo obj) {
		// TODO: Check for any death-related abilities that may prevent death
		// from occurring
		log.debug("handleDeath: Start");

		// TEMP: Remove all attackable targets
		OID oid = obj.getOwnerOid();
		for (OID attackableTarget : obj.getAttackableTargets().keySet()) {
			CombatInfo cInfo = getCombatInfo(attackableTarget);
			if (cInfo != null) {
				cInfo.removeAttackableTarget(oid);
			}
		}

		// If we have reached this point, the unit will be set as dead
		obj.setDeadState(true);
		obj.stopAutoAttack();

		Set<OID> attackers = getAttackers(oid);
		OID last=null;
		if (attackers != null)
			for (OID o : attackers) {
				last = o;
			}
		CombatInfo lastCInfo = getCombatInfo(last);
		if(lastCInfo!=null && !lastCInfo.isMob()) {
			CombatPlugin.addListsRankingData(last,AchievementsClient.FINAL_BLOW,1);
		}
		//if (Log.loggingTrace)
		    log.debug("handleDeath: attacker:" + attackers);
		if (attackers != null) {
			clearAttackers(oid);
		}

		// sendDeathBox(oid);
		AgisEffect.removeNonPassiveEffects(obj);
		dismountPlayer(obj.getOwnerOid());
		log.debug("DEATH: effects removed");
		// OID killerOid = lastAttackerMap.get(oid);
		// Log.debug("DEATH: sending out Arena client death related messages for object:
		// " + oid);
		// ArenaClient.arenaDeath(killerOid, oid);
		// Now set the unit as lootable, if applicable
		// Log.debug("DEATH: sent Arena client death related messages for object: " +
		// oid);
		if (obj.isMob()) {
			log.debug("handleDeath: is mob");
			/*
			 * OID petOwner = (OID) obj.getProperty("petOwner"); if (petOwner != null) { //
			 * It's a pet so we don't want to set it as lootable return; }
			 */
			if (Log.loggingDebug)
	            log.debug("handleDeath: attacker:" + attackers);

			if (attackers != null) {
				log.debug("handleDeath: attacker is not null");

				// Tell all attackers to stop attacking
				/*
				 * for (OID attacker : attackers) { CombatInfo info = getCombatInfo(attacker);
				 * if (info == null) { continue; } info.stopAutoAttack(); }
				 */

				// Send message to ClassAbilityPlugin to handle xp gain
				String name = obj.getName();
				// int mobID = -1;
				Integer mobID = (Integer) obj.getProperty(WorldManagerClient.TEMPL_ID);
				/*
				 * EnginePlugin.getObjectProperty( obj.getOwnerOid(),
				 * WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_ID);
				 */
				if (Log.loggingDebug)
		            log.debug("DEATH: mob " + mobID);
				LinkedList<String> questCategories = (LinkedList<String>) EnginePlugin.getObjectProperty(obj.getOwnerOid(), WorldManagerClient.NAMESPACE, "questCategories");

		        if (Log.loggingDebug) {
    				if (questCategories == null)
    					log.debug("QuestCategories is null");
    				else
    					log.debug("QuestCategories = " + questCategories);
		        }

				ArrayList<OID> groupsAlreadyChecked = new ArrayList<OID>();
				for (OID attacker : attackers) {
					CombatInfo info = getCombatInfo(attacker);
					if (info == null) {
						continue;
					}
					if (info.isMob()) {
						continue;
					}

					// Get group members that are close by and send message for them as well
					if (info.isGrouped()) {
						log.debug("handleDeath: in group");
						// Skip any group members where the group has already had a Mob Death message
						// sent for it
						GroupInfo gInfo = GroupClient.GetGroupMemberOIDs(attacker);
						if (gInfo.groupOid != null) {
							if (groupsAlreadyChecked.contains(gInfo.groupOid)) {
								continue;
							} else {
								groupsAlreadyChecked.add(gInfo.groupOid);
							}
						}
						ObjectInfo objInfo = WorldManagerClient.getObjectInfo(attacker);
						for (OID groupMember : gInfo.memberOidSet) {
							log.debug("GROUP: checking group member to send quest mob death: " + groupMember);
							if (!groupMember.equals(attacker)) {
								// Distance check
								ObjectInfo memberInfo = WorldManagerClient.getObjectInfo(groupMember);
								if (memberInfo == null || !memberInfo.instanceOid.equals(objInfo.instanceOid)) {
									log.debug("GROUP: group member is not in the same instance");
									continue;
								}
								if (Point.distanceTo(memberInfo.loc, objInfo.loc) > 100) {
									if(Log.loggingDebug)log.debug("GROUP: group member is not close enough: " + Point.distanceTo(memberInfo.loc, objInfo.loc));
									continue;
								}
							}
					        if (Log.loggingDebug)
					            log.debug("handleDeath: in group groupMember:" + groupMember + " mobID:" + mobID + " name:" + name + " questCategories:" + questCategories);

							CombatClient.QuestMobDeath QMDmsg = new CombatClient.QuestMobDeath(groupMember, mobID, name, questCategories);
							Engine.getAgent().sendBroadcast(QMDmsg);
						}
					} else {
                        if (Log.loggingDebug)
                            log.debug("handleDeath: no in group attacker:" + attacker + " mobID:" + mobID + " name:" + name + " questCategories:" + questCategories);
						CombatClient.QuestMobDeath QMDmsg = new CombatClient.QuestMobDeath(attacker, mobID, name, questCategories);
						Engine.getAgent().sendBroadcast(QMDmsg);
					}
				}
			} else {
				log.debug("handleDeath: attakers is null");
			}
		} else {
			log.debug("handleDeath: not mob");
			if (DEATH_LOST_EXP) {
				int vipLevel = obj.getVipLevel();
				long vipExpire = obj.getVipExpire();
				long now = System.currentTimeMillis();
				float vipModp = 0f;
				float vipMod = 0f;
					
				if(obj.getBonuses().containsKey("ExpLostPVE")) {
					vipMod =obj.getBonuses().get("ExpLostPVE").GetValue();
	      			vipModp =obj.getBonuses().get("ExpLostPVE").GetValuePercentage();
	      		}
				if(globalEventBonusesArray.containsKey("ExpLostPVE")) {
					vipMod += globalEventBonusesArray.get("ExpLostPVE").GetValue();
	      			vipModp += globalEventBonusesArray.get("ExpLostPVE").GetValuePercentage();
	      		}
				int expMax = obj.statGetCurrentValue(ClassAbilityPlugin.EXPERIENCE_MAX_STAT);
				int exp = (Math.round(vipMod + expMax * (DEATH_LOST_EXP_PERCENTAGE + vipModp) / 100f));
				ClassAbilityPlugin.lostExp(oid, exp);
			}
			if (DEATH_PERMANENTLY) {
				obj.setDeathPermanently(true);
			} else {
				//Remove item effects
				HashMap<OID, AgisItem> equipMap = AgisInventoryClient.getEquipedItems(obj.getOwnerOid());
				EquipHelper.UpdateEffectsAndAbilities(obj.getOwnerOid(), obj, equipMap, true, false);
			}

			// ArenaClient.duelDefeat(oid); - doesn't need to be sent, a death property
			// handler can be used
			OID accountId = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
			DataLoggerClient.logData("PLAYER_DIED", oid, null, accountId, null);
		}
		log.debug("handleDeath: End");
		
	
		
		
	}

	/**
	 * NOTE: Not sure if this is used at all anymore
	 * 
	 * Sends a message to the client to display a death (release) box. Depending on
	 * certain circumstances the box may have different options, or not displayed at
	 * all.
	 * 
	 * @param oid
	 */
	private static void sendDeathBox(OID oid) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "deathBox");

		props.put("boxType", "normal");
		// Check if they are in an arena
		int arenaID = -1;
		try {
			arenaID = (Integer) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
		} catch (NullPointerException e) {
		}
		if (arenaID != -1) {
			props.put("boxType", "arena");
		}

		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

	/**
	 * Handles the SetPlayerRespawnLocationMessage. This will update the players
	 * respawn location that they will teleport to when they release.
	 * 
	 * @author Andrew Harrison
	 *
	 */
	class SetRespawnLocationHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			CombatClient.SetPlayerRespawnLocationMessage stateMsg = (CombatClient.SetPlayerRespawnLocationMessage) msg;

			OID subjectOid = stateMsg.getSubject();
			// is the update object spawned?
			CombatInfo info = getCombatInfo(subjectOid);
			if (info == null) {
				return false;
			}

			if (stateMsg.getInstanceID() > 0) {
				info.setRespawnInstance(stateMsg.getInstanceID());
			} else {
				OID instanceOid = WorldManagerClient.getObjectInfo(subjectOid).instanceOid;
				int instanceID = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
				info.setRespawnInstance(instanceID);
			}

			info.setRespawnPosition(stateMsg.getRespawnLoc());

			return true;
		}
	}

	/**
	 * Hook for the Dismount Extension Message. Will attempt to remove a MountEffect
	 * from the player (resulting in them being dismounted).
	 * 
	 * @author Andrew Harrison
	 * 
	 */
	class DismountHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage eMsg = (ExtensionMessage) msg;
			OID playerOid = eMsg.getSubject();
			Log.debug("MOUNT: got dismount message");
			dismountPlayer(playerOid);

			return true;
		}
	}

	/**
	 * Removes a MountEffect from the specified player if they have one. The result
	 * is the player being dismounted.
	 * 
	 * @param oid
	 */
	private static void dismountPlayer(OID oid) {
		// Remove the mount effect from the player
		CombatInfo player = getCombatInfo(oid);
		EffectState mountEffect = null;
		if (player == null) {
			log.error("dismountPlayer: player with OID:" + oid + " is null");
			return;
		}
		for (EffectState state : player.getCurrentEffects()) {
			if(Log.loggingDebug)Log.debug("MOUNT: found effect: " + state.getEffectID());
			if (state.getEffect() != null && state.getEffect() instanceof MountEffect) {
				mountEffect = state;
				if(Log.loggingDebug)Log.debug("MOUNT: found effect to remove: " + mountEffect.getEffectID());
				break;
			}
		}

		if (mountEffect != null) {
			AgisEffect.removeEffect(mountEffect, player);
		}
	}

	class UpdateCombatInfoStateHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			CombatClient.SetCombatInfoStateMessage stateMsg = (CombatClient.SetCombatInfoStateMessage) msg;

			OID subjectOid = stateMsg.getSubject();
			// is the update object spawned?
			CombatInfo info = getCombatInfo(subjectOid);
			if (info == null) {
				return false;
			}

			String state = stateMsg.getState();
			if (Log.loggingDebug)
				Log.debug("UpdateCombatInfoStateHook: get state : " + state + " to set for obj=" + subjectOid);
			if (state != null) {
				if (state.equals(CombatInfo.COMBAT_STATE_RESET)) {
					state = CombatInfo.COMBAT_STATE_EVADE;
				}

				if (state.equals(CombatInfo.COMBAT_STATE_RESET_END)) {
					// Mob Reset Remove Stealth Modifiers
					if (CombatPlugin.STEALTH_STAT != null) {

						// AgisStat stat = (AgisStat) info.getProperty(CombatPlugin.STEALTH_STAT);
						info.statRemoveModifier(CombatPlugin.STEALTH_STAT, "Ability", false);
						info.statRemovePercentModifier(CombatPlugin.STEALTH_STAT, "Ability", false);
						// stat.removeModifier("Ability");
						// stat.removePercentModifier("Ability");
						info.statSendUpdate(false);
					}
					// Disable combat State
					// info.setCombatState(false);
					state = "";
				}
				if (!stateMsg.getClearState()) {
					if (Log.loggingDebug)
						Log.debug("STATE: setting combat info state to: " + state);
					info.setState(state);
				} else {
					if (Log.loggingDebug)
						Log.debug("STATE: clearing combat info state: " + state);
					info.clearState(state);
				}
			}
			return true;
		}
	}

	class UpdateObjectHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.UpdateMessage updateReq = (WorldManagerClient.UpdateMessage) msg;
			OID subjectOid = updateReq.getSubject();
			OID targetOid = updateReq.getTarget();

			// is the update object spawned?
			CombatInfo info = getCombatInfo(subjectOid);
			if (info == null) {
				return false;
			}

			// send over properties
			if (Log.loggingDebug)
				log.debug("UpdateObjectHook.processMessage: sending properties for subjectOid=" + subjectOid);

			WorldManagerClient.TargetedPropertyMessage propMessage = new WorldManagerClient.TargetedPropertyMessage(targetOid, subjectOid);
			for (Map.Entry<String, Serializable> kvp : info.getPropertyMap().entrySet()) {
				if (!(kvp.getValue() instanceof AgisStat))
					propMessage.setProperty(kvp.getKey(), kvp.getValue(), true);
			}

			// Comment out next two lines for LES world
			Engine.getAgent().sendBroadcast(propMessage);
			info.statSendUpdate(true, targetOid);

			// Abilities are only sent to the player themselves
			// if (subjectOid.equals(targetOid))
			// ClassAbilityPlugin.sendSkillUpdate(info);
			// sendAbilityUpdate(info);

			return true;
		}
	}

	/**
	 * Handles property updates for characters/mobs.
	 * 
	 * @author Andrew Harrison
	 * 
	 */
	class PropertyHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			PropertyMessage propMsg = (PropertyMessage) msg;
			OID objOid = propMsg.getSubject();

			Boolean dead = (Boolean) propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
			if (dead != null && !dead) {
				removeSpiritEffect(objOid);
				
				//Re-Apply equipment effects / abilities when revived
				CombatInfo info = getCombatInfo(objOid);
				HashMap<OID, AgisItem> equipMap = AgisInventoryClient.getEquipedItems(objOid);
				EquipHelper.UpdateEffectsAndAbilities(objOid, info, equipMap, false , true);
			}
			Serializable wType = propMsg.getProperty("weaponType");
			
			if (wType != null) {
				if(Log.loggingDebug)	Log.debug("WEAPON: got weapon Type property update: " + wType);
				// Go through all effects, if they are passive then check for weapon requirement
				// and activate/deactivate as needed
				boolean effectUpdated = false;
				CombatInfo obj = getCombatInfo(objOid);
				Set<EffectState> effects = obj.getCurrentEffects()!= null?new HashSet<EffectState>(obj.getCurrentEffects()):new HashSet<EffectState>();
				for (EffectState state : effects) {
					if (state.getEffect().isPassive()) {
						boolean requirementsMet = true;
						AgisAbility ability = Agis.AbilityManager.get(state.getAbilityID());
						if (ability == null) {
							continue;
						}
						if (ability.checkEquip(obj, obj, null) != AbilityResult.SUCCESS) {
							requirementsMet = false;
						}
						if(Log.loggingDebug)Log.debug("WEAPON: requirements met for effect: " + state.getEffectID() + "? " + requirementsMet);
						if (!requirementsMet && state.isActive()) {
							// Disable the boost from the passive effect
							state.getEffect().deactivate(state);
							effectUpdated = true;
						} else if (requirementsMet && !state.isActive()) {
							// Enable the boost from the passive effect
							state.getEffect().activate(state);
							effectUpdated = true;
						}
					}
				}

				if (effectUpdated) {
					obj.updateEffectsProperty();
				}
			}

			return true;
		}
	}

	/**
	 * Sets combat state to false upon despawning
	 */
	class ResetAttackerHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			CombatClient.ResetAttacterMessage _msg = (CombatClient.ResetAttacterMessage) msg;
			OID objOid = _msg.getSubject();
			CombatInfo obj = getCombatInfo(objOid);
			if (Log.loggingDebug)
				log.debug("ResetAttackerHook: got a reset attacker message for oid=" + objOid);
			if (obj == null)
				return false;
			obj.stopAutoAttack();
			if (Log.loggingDebug)
				log.debug("ResetAttackerHook: reset combat state for oid=" + objOid);
			if (obj != null)
				obj.setCombatState(false);

			// Also tell all attackers to stop
			Set<OID> attackers = getAttackers(objOid);
			if (attackers != null) {
				clearAttackers(objOid);
			}
				log.debug("ResetAttackerHook: end");
			return true;
		}
	}
	
	/**
	 * Sets combat state to false upon despawning
	 */
	class StartCombatHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			PropertyMessage _msg = (PropertyMessage) msg;
			OID objOid = _msg.getSubject();
			CombatInfo obj = getCombatInfo(objOid);
			if (Log.loggingDebug)
				log.debug("StartCombatHook: got a reset attacker message for oid=" + objOid+" "+obj);
			if (obj == null)
				return false;
			obj.setCombatState(true);
				log.debug("StartCombatHook: end");
			return true;
		}
	}
	
	
	
	
	
	
	/**
	 * Sets combat state to false upon despawning
	 */
	class DespawnedHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.DespawnedMessage despawnedMsg = (WorldManagerClient.DespawnedMessage) msg;
			OID objOid = despawnedMsg.getSubject();
			CombatInfo obj = getCombatInfo(objOid);
			if (obj == null)
				return false;
			// FIXME Dragonsan Added Stop AutoAttack
			obj.stopAutoAttack();
			if (Log.loggingDebug)
				log.debug("DespawnedHook: got a despawned message for oid=" + objOid);
			if (obj != null)
				obj.setCombatState(false);

			// Also tell all attackers to stop
			Set<OID> attackers = getAttackers(objOid);
			if (attackers != null) {
				clearAttackers(objOid);

				for (OID attacker : attackers) {
					CombatInfo info = getCombatInfo(attacker);
					if (info == null) {
						continue;
					}
					// info.stopAutoAttack();
					// remove target from all sets
					info.removeAttackableTarget(objOid);
					info.removeFriendlyTarget(objOid);
				}
			}

			return true;
		}
	}

	class SpawnedHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;
			OID objOid = spawnedMsg.getSubject();
			CombatInfo info = getCombatInfo(objOid);
			if (info != null && info.isUser()) {
				// Send down the messages about the players current
				// abilities/skills etc.
				Log.debug("AJ: Sending combat messages");
				ExtendedCombatMessages.sendAbilities(info.getOwnerOid(), info.getCurrentAbilities());
				ExtendedCombatMessages.sendActions(info.getOwnerOid(), info.getCurrentActions(), info.getCurrentActionBar());
				ExtendedCombatMessages.sendSkills(info.getOwnerOid(), info.getCurrentSkillInfo());
				info.setAttackableTargets(new HashMap<OID, TargetInfo>());
				info.setFriendlyTargets(new HashMap<OID, TargetInfo>());
			}
			return true;
		}
	}

	/**
	 * Called when a player enters a combat area and their health properties need to
	 * be set. Also called when a player levels up.
	 * 
	 * @author Andrew
	 * 
	 */
	class SetHealthPropertiesHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage spawnedMsg = (ExtensionMessage) msg;
			OID objOid = spawnedMsg.getSubject();
			CombatInfo info = getCombatInfo(objOid);
			if (info.isUser()) {
				// Set players health based on their level
				AgisStat level = (AgisStat) info.getProperty("level");
				int health = 20 + ((level.getCurrentValue() - 1) * 2);
				info.statSetBaseValue(HEALTH_STAT, health);
				info.statSetBaseValue(HEALTH_MAX_STAT, health);
			}
			return true;
		}
	}

	/**
	 * Called when a player enters a combat area and their health properties need to
	 * be set. Also called when a player levels up.
	 * 
	 * @author Andrew
	 * 
	 */
	class RegenerateHealthHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage healthMsg = (ExtensionMessage) msg;
			OID objOid = healthMsg.getSubject();
			CombatInfo info = getCombatInfo(objOid);
			int health = info.statGetCurrentValue(CombatPlugin.HEALTH_STAT);
			int amount = (Integer) healthMsg.getProperty("amount");
			int healthMax = info.statGetCurrentValue(HEALTH_MAX_STAT);
			health += amount;
			if (health > healthMax) {
				health = healthMax;
			}
			info.statSetBaseValue(CombatPlugin.HEALTH_STAT, health);
			/*
			 * if (health < healthMax) { // If health is lower than max, regen some
			 * CombatClient.startAbility(200, objOid, objOid, null); }
			 */
			return true;
		}
	}

	class TargetTypeUpdateHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			CombatClient.TargetTypeMessage ttMsg = (CombatClient.TargetTypeMessage) msg;
			LinkedList<TargetType> _targetTypes = ttMsg.getTargetTypes();
			//System.out.println("TargetTypeUpdateHook");
			if(Log.loggingDebug)Log.debug("TargetTypeUpdateHook: Start");
			
				if (_targetTypes == null) {
				LinkedList<OID> objOids = ttMsg.getSubjectOid();
				LinkedList<Integer> targetTypes = ttMsg.getTargetType();
				LinkedList<OID> targetOids = ttMsg.getTargetOid();
				for (int i = 0; i < objOids.size(); i++) {
					OID objOid = objOids.get(i);
					OID target = targetOids.get(i);
					int targetType = targetTypes.get(i);
					CombatInfo info = getCombatInfo(objOid);
					if (info == null) {
						if (Log.loggingDebug)Log.debug("TargetTypeUpdateHook: got target type update. CombatInfo is null for "+objOid);
						return true;
					}
					//if(Log.loggingDebug)Log.debug("TargetTypeUpdateHook: got target type update. Setting target: " + target + " to type: " + targetType + " for mob: " + objOid);
					// First remove target from all sets
				//	System.out.println("Remove atackable and friendly for "+objOid);

					info.removeAttackableTarget(target);
					info.removeFriendlyTarget(target);
					// Create new target Info
					if (targetType != FactionPlugin.Neither) {
						TargetInfo tInfo = new TargetInfo();
						tInfo.setOid(target);
						String species = (String) EnginePlugin.getObjectProperty(target, WorldManagerClient.NAMESPACE, "species");

						tInfo.setSpecies(species);
						if (targetType == FactionPlugin.Attackable) {
							//CombatInfo ctinfo = getCombatInfo(target);
							//if(ctinfo==null) {
							//	System.out.println("For "+objOid+" Add target "+target+" target Dead? NA sub dead? "+info.dead());
						//	}else
						//	System.out.println("For "+objOid+" Add target "+target+" target Dead? "+ctinfo.dead()+" sub dead? "+info.dead());
							info.addAttackableTarget(target, tInfo);
						} else if (targetType == FactionPlugin.Healable) {
							info.addFriendlyTarget(target, tInfo);
						}
					}
				//	if(Log.loggingDebug)	Log.debug("TargetTypeUpdateHook: "+info.getAttackableTargets().keySet()+" "+info.getFriendlyTargets().keySet());
				}
			} else {
				if(Log.loggingDebug)	Log.debug("TargetTypeUpdateHook: got target type update. _targetTypes: " + _targetTypes );
				
				for (int i = 0; i < _targetTypes.size(); i++) {
					CombatInfo info = getCombatInfo(_targetTypes.get(i).getSubjectOid());
					if (info == null) {
						Log.debug("TargetTypeUpdateHook: CombatInfo is null");
						continue;
					}
				//	if(Log.loggingDebug)Log.debug("TargetTypeUpdateHook: got target type update. Setting target: " + _targetTypes.get(i).getTargetOid() + " to type: " + _targetTypes.get(i).getTargetType() + " for mob: " + _targetTypes.get(i).getSubjectOid());
					//log.error("\nremove targets for "+info.getOid()); 
					info.removeAttackableTarget(_targetTypes.get(i).getTargetOid());
					info.removeFriendlyTarget(_targetTypes.get(i).getTargetOid());
					//System.out.println("Remove atackable and friendly for "+_targetTypes.get(i).getSubjectOid()+" removerd "+_targetTypes.get(i).getTargetOid()+" new state "+_targetTypes.get(i).getTargetType());
					
					if (_targetTypes.get(i).getTargetType() != FactionPlugin.Neither && _targetTypes.get(i).getTargetType() != FactionPlugin.Deleted) {
						TargetInfo tInfo = new TargetInfo();
						tInfo.setOid(_targetTypes.get(i).getTargetOid());
						String species = (String) EnginePlugin.getObjectProperty(_targetTypes.get(i).getTargetOid(), WorldManagerClient.NAMESPACE, "species");

						tInfo.setSpecies(species);
						if (_targetTypes.get(i).getTargetType() == FactionPlugin.Attackable) {
							//CombatInfo ctinfo = getCombatInfo(_targetTypes.get(i).getTargetOid());
							//System.out.println("For "+_targetTypes.get(i).getSubjectOid()+" Add Attackable target "+_targetTypes.get(i).getTargetOid()+" target Dead? "+ctinfo.dead()+" sub dead? "+info.dead());
							//log.error("adding attackable target "+_targetTypes.get(i).getTargetOid()+" "+(ctinfo != null ? ctinfo.dead():"NA"));
								info.addAttackableTarget(_targetTypes.get(i).getTargetOid(), tInfo);
						} else if (_targetTypes.get(i).getTargetType() == FactionPlugin.Healable) {
						//	CombatInfo ctinfo = getCombatInfo(_targetTypes.get(i).getTargetOid());
						//	System.out.println("For "+_targetTypes.get(i).getSubjectOid()+" Add Friandly target "+_targetTypes.get(i).getTargetOid()+" target Dead? "+ctinfo.dead()+" sub dead? "+info.dead());
							info.addFriendlyTarget(_targetTypes.get(i).getTargetOid(), tInfo);
						}
					}
				//	if(Log.loggingDebug)
				//		Log.debug("TargetTypeUpdateHook: "+info.getAttackableTargets().keySet()+" "+info.getFriendlyTargets().keySet());
					
				}
			}
			Log.debug("TARGET: got target type update. END");
			Log.debug("TargetTypeUpdateHook: End");
			return true;
		}
	}

	public static void addAttacker(OID target, OID attacker) {
if(Log.loggingDebug)log.debug("addAttacker: target="+target+" attacker="+attacker);
		autoAttackReverseMap.computeIfAbsent(target, new Function<OID, Set<OID>>() {
            @Override
            public Set<OID> apply(OID t) {
                return ConcurrentHashMap.newKeySet();
            }
		}).add(attacker);
		lastAttackerMap.put(target, attacker);
		// Check if the mobs tagOwner property is set
		/*
		 * CombatInfo info = getCombatInfo(target);
		 * 
		 * if (info.isMob()) { OID tagOwner = (OID)
		 * info.getProperty(CombatInfo.COMBAT_TAG_OWNER); if (tagOwner == null) {
		 * info.setProperty(CombatInfo.COMBAT_TAG_OWNER, attacker); } }
		 */
	}

	public static void removeAttacker(OID target, OID attacker) {
		autoAttackReverseMap.computeIfPresent(target, new BiFunction<OID, Set<OID>, Set<OID>>() {
            @Override
            public Set<OID> apply(OID __, Set<OID> attackers) {
                attackers.remove(attacker);
                return attackers.isEmpty() ? null : attackers;
            }
		});
	}

	public static Set<OID> getAttackers(OID target) {
		return autoAttackReverseMap.get(target);
	}

	public static void clearAttackers(OID target) {
		autoAttackReverseMap.remove(target);
	}

	protected static Map<OID, Set<OID>> autoAttackReverseMap = new ConcurrentHashMap<>();
	protected static Map<OID, OID> lastAttackerMap = new ConcurrentHashMap<OID, OID>();

	public static void registerStat(AgisStatDef stat) {
		registerStat(stat, false, new String[0]);
	}

	public static void registerStat(AgisStatDef stat, boolean isPublic) {
		registerStat(stat, isPublic, new String[0]);
	}

	public static void registerStat(AgisStatDef stat, boolean isPublic, String... dependencies) {
		String statName = stat.getName();
		if(Log.loggingDebug)log.debug("registerStat "+statName+" "+stat+" isPublic="+isPublic+" dependencies="+dependencies);
		if (statDefMap.containsKey(statName)) {
			if(!DevMode) {
					throw new AORuntimeException(statName+" stat already defined");
			}
		}
		statDefMap.put(statName, stat);
		if (dependencies.length == 0) {
			baseStats.add(stat);
		}
		for (String depName : dependencies) {
			AgisStatDef depStat = statDefMap.get(depName);
			if (depStat != null) {
				depStat.addDependent(stat);
			} else {
				Log.error("no stat definition for dependency " + depName + " of stat " + statName);
			}
		}
		if (isPublic)
			publicStats.add(statName);
	}

	public static AgisStatDef lookupStatDef(String name) {
		return statDefMap.get(name);
	}

	public static Map<String, AgisStatDef> statDefMap = new HashMap<String, AgisStatDef>();
	protected static Set<AgisStatDef> baseStats = new HashSet<AgisStatDef>();
	public static Set<String> publicStats = new HashSet<String>();

	// Process Skill training message
	class AddSkillHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage reqMsg = (ExtensionMessage) msg;

			applySkillTraining((OID) reqMsg.getProperty("playerOid"), (Integer) reqMsg.getProperty("skill"));

			return true;
		}
	}

	class AddAbilityHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage reqMsg = (ExtensionMessage) msg;

			CombatInfo player = getCombatInfo(reqMsg.getSubject());
			// player.addAbility((Integer)reqMsg.getProperty("abilityID"));
			// sendAbilityUpdate(player);
			return true;
		}
	}

	class GetAbilityHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage getMsg = (ExtensionMessage) msg;

			AgisAbility ability = Agis.AbilityManager.get((Integer) getMsg.getProperty("abilityID"));
			HashMap<String, String> abilityInfo = new HashMap<String, String>();
			abilityInfo.put("name", ability.getName());
			abilityInfo.put("id", "" + ability.getID());
			abilityInfo.put("icon", ability.getIcon());
			Engine.getAgent().sendObjectResponse(msg, abilityInfo);

			return true;
		}
	}

	// Add the Skill to the player object and notify the client of the updates
	public void applySkillTraining(OID playerOid, int skill) {
		if(Log.loggingDebug)	Log.debug("CombatPlugin.applySkillTraining : skill = " + skill);
		CombatInfo player = (CombatInfo) getCombatInfo(playerOid);
		// Only add the skill to the player if he does not already have that
		// skill

		/*
		 * if (!player.getCurrentSkillInfo().getSkills().containsKey(skill)) {
		 * player.addSkill(skill); // Also adds default ability
		 * ClassAbilityPlugin.sendSkillUpdate(player); // Handle skill updates // in
		 * ClassAbilityPlugin sendAbilityUpdate(player); // Move to ClassAbilityPlugin?
		 * } else { Map<String, Serializable> props = new HashMap<String,
		 * Serializable>(); props.put("ext_msg_subtype", "ao.TRAINING_FAILED");
		 * props.put("playerOid", playerOid); props.put("reason",
		 * "You cannot train any further in the selected skill.");
		 * 
		 * TargetedExtensionMessage msg = new TargetedExtensionMessage(
		 * CombatClient.MSG_TYPE_TRAINING_FAILED, playerOid, playerOid, false, props);
		 * Engine.getAgent().sendBroadcast(msg); }
		 */
	}

	public static AgisStatDef getBaseStatDef(String name) {
		for (AgisStatDef statdef : baseStats) {
			if (statdef.getName().equals(name)) {
				return statdef;
			}
		}
		return null;
	}

	class RemoveArenaEffectsHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ArenaClient.removeEffectsMessage EBMsg = (ArenaClient.removeEffectsMessage) msg;
			OID oid = EBMsg.getSubject();
			String type = (String) EBMsg.getProperty("type");
			int ID = (Integer) EBMsg.getProperty("ID");
			CombatInfo info = getCombatInfo(oid);
			// TODO: Deal with this later
			return true;
		}
	}

	/**
	 * Handles the EquipBonusMessage which is sent when a player/mob equips or
	 * unequips an item. Calls the UpdateEquiperStats which updates the stats for
	 * the player/mob based on the item equipped or unequipped.
	 * 
	 * @author Andrew Harrison
	 * 
	 */
	class ItemAcquireStatusChangeHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			AgisInventoryClient.ItemAcquiredStatusMessage EBMsg = (AgisInventoryClient.ItemAcquiredStatusMessage) msg;
			OID oid = EBMsg.getSubject();
			if(Log.loggingDebug)	Log.debug("ITEM ACQUIRE CHANGE: item " + EBMsg.getItem() + " was acquired? " + EBMsg.getAcquired());
			CombatInfo info = getCombatInfo(oid);
			EquipHelper.ItemAcquiredStatHelper(oid, EBMsg.getItem(), EBMsg.getAcquired(), info);
			return true;
		}
	}

	/**
	 * Handles the EquipBonusMessage which is sent when a player/mob equips or
	 * unequips an item. Calls the UpdateEquiperStats which updates the stats for
	 * the player/mob based on the item equipped or unequipped.
	 * 
	 * @author Andrew Harrison
	 * 
	 */
	class ItemEquipStatusChangeHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			AgisInventoryClient.ItemEquipStatusMessage EBMsg = (AgisInventoryClient.ItemEquipStatusMessage) msg;
			OID oid = EBMsg.getSubject();
			if(Log.loggingDebug)	Log.debug("ITEM EQUIP CHANGE: " + EBMsg);
			
			if (EBMsg.getItemToEquip() == null && EBMsg.getItemToUnequip() == null) {
				// Requires a valid item to do any changing
				return true;
			}
			
			HashMap<Integer,Integer> itemSets = (HashMap<Integer,Integer>)EBMsg.getProperty("itemSets");
			
			CombatInfo info = getCombatInfo(oid);
			EquipHelper.UpdateEquiperStats(oid, EBMsg.getItemToEquip(), EBMsg.getItemToUnequip(), info);
			boolean sameItemID = false;	
			HashMap<Integer, Integer> inventoryClientSets = (HashMap) AgisInventoryClient.getSetsEquiped(oid);
			
			AgisItem itemToEquip = EBMsg.getItemToEquip();
			AgisItem itemToUnequip = EBMsg.getItemToUnequip();
			
			if (itemToUnequip != null && itemToEquip == null) 
			{
				Boolean isUnequipped = (Boolean) itemToUnequip.getProperty("unequipped");
				if (isUnequipped != null && isUnequipped) {
					//Skip checks as item has already been unequipped
					return true;
				}
			}
			
			if (itemToEquip != null && itemToUnequip != null) 
			{
				if (itemToEquip.getTemplateID() == itemToUnequip.getTemplateID()) 
				{
					sameItemID = true;
				}
			}
			
			if (!EquipHelper.IsDurabilityOk(itemToEquip)) 
			{	
				if (itemToUnequip == null) 
				{	
					itemToUnequip = itemToEquip;
				}
			}
			
			//Checks internally if items use the same enchant profile id or not
			EquipHelper.UpdateEquiperEnchantEffectsAndAbilities(oid, itemToEquip, itemToUnequip, info);
			
			//Always update socket effects/abilities as occupied sockets can always be different between same item ID
			EquipHelper.UpdateEquiperSocketEffectsAndAbilities(oid, itemToEquip, itemToUnequip, info);
			
			//Only update effects / abilities if the item isn't the same
			if (!sameItemID) 
			{				
				EquipHelper.UpdateEquiperItemSetEffectsAndAbilities(oid, itemToEquip, itemToUnequip, info, itemSets);
				EquipHelper.UpdateEquiperPassiveAbility(oid, itemToEquip, itemToUnequip, info);
			}
			return true;
		}
	}

	/**
	 * Hook receive message to Apply Effect on the target
	 * @author Zbawca
	 *
	 */
	class ApplyEffectHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			CombatClient.applyEffectMessage upsMsg = (CombatClient.applyEffectMessage) msg;
			OID oid = upsMsg.getSubject();
			int effectID = (Integer) upsMsg.getProperty("effectID");

			if(Log.loggingDebug)	Log.debug("COMBATPLUGIN: about to apply effect: " + effectID + " to object: " + oid);
			CombatInfo info = getCombatInfo(oid);
			AgisEffect effect = Agis.EffectManager.get(effectID);
			if(effect==null){
				log.error("ApplyEffectHook: not found effect definition for id "+effectID);
				return true;
			}
			HashMap<String, Serializable> params = new HashMap<String, Serializable>();
			params.put("skillType", -1);
			params.put("hitRoll", 50);
			params.put("result", AgisAbility.RESULT_HIT);
			params.put("dmgType", DEFAULT_EFFECT_DAMAGE_TYPE);
			AgisEffect.applyEffect(effect, info, info, -1, params);
			return true;
		}
	}

	class RemoveEffectHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			CombatClient.removeEffectMessage upsMsg = (CombatClient.removeEffectMessage) msg;
			OID oid = upsMsg.getSubject();
			int effectID = upsMsg.getEffectID();
			boolean allSame = upsMsg.getAllSame();
			if(Log.loggingDebug)Log.debug("RemoveEffectHook: about to remove effect: " + effectID + " from object: " + oid);
			CombatInfo info = getCombatInfo(oid);
			if (info != null) 
			{	
				if (allSame)
					AgisEffect.removeAllEffectsByID(info, effectID, allSame);
				else {
					if (upsMsg.getProperty("removeStackAmount") != null) {
						AgisEffect.removeEffectByID(info, effectID, (Integer)upsMsg.getProperty("removeStackAmount"));
					}
				else
					AgisEffect.removeEffectByID(info, effectID);
				}
			} else {
				if(Log.loggingDebug)Log.debug("RemoveEffectHook: effect: " + effectID + " from object: " + oid + " CombatInfo is null");
			}
			return true;
		}
	}

	/**
	 * Handles a message from the player when they want to remove one of their
	 * buffs.
	 * 
	 * @author Andrew Harrison
	 *
	 */
	class RemoveBuffHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage rmMsg = (ExtensionMessage) msg;

			OID oid = rmMsg.getSubject();
			CombatInfo info = getCombatInfo(oid);

			int effectID = (Integer) rmMsg.getProperty("effectID");
			int pos = (Integer) rmMsg.getProperty("pos");
			long id = (long)rmMsg.getProperty("id");
			
			if(Log.loggingDebug)	Log.debug("REMOVE: playerEffect at pos: " + pos + " effectId: " + effectID+" state id: "+id);

			ArrayList<EffectState> playerEffects = new ArrayList<EffectState>(info.getCurrentEffects());
			
			for(EffectState es : playerEffects) {				
				if(es.getId() == id && es.getEffectID() == effectID && es.getEffect().isBuff()) {
					AgisEffect.removeEffect(es);
				}
				/*if (es.getId().equals(id) && es.getEffectID() == effectID) {
					boolean removeEffect = false;
					if (es.getEffect() != null && es.getEffect().isBuff()) {
						removeEffect = true;
					} else if (es.getEffect() == null) {
						AgisEffect ef = Agis.EffectManager.get(es.getEffectID());
						if (ef != null && ef.isBuff()) {
							removeEffect = true;
						}
					}
					if (removeEffect)
					AgisEffect.removeEffect(es);
				}*/
			}
			
			/*if(playerEffects.size()<=pos)
				return true;

			if(Log.loggingDebug)Log.debug("REMOVE: playerEffect at pos: " + 0 + " is: " + playerEffects.get(pos).getEffectID() + " against " + effectID);
			if (playerEffects.get(pos).getEffectID() == effectID && playerEffects.get(pos).getEffect().isBuff()) {
				AgisEffect.removeEffect(playerEffects.get(pos));
			}
*/
			return true;
		}
	}

	class FallingEventHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage fMsg = (ExtensionMessage) msg;
			OID playerOid = fMsg.getSubject();
			String fallingState = (String) fMsg.getProperty("fallingState");

			if(Log.loggingDebug)	Log.debug("Got FallingEventHook with state: " + fallingState);

			CombatInfo info = getCombatInfo(playerOid);
			if (info == null) {
				Log.debug("FallingEventHook: info is null");
				return true;
			}
			if (fallingState.equals("cancel")) {
				// TODO: Set falling distance to 0
				info.setProperty(CombatInfo.COMBAT_PROP_FALLING_START_HEIGHT, 0);
				return true;
			}

			// Get in the current y value of the player

			BasicWorldNode node = null;
					try{
						node = WorldManagerClient.getWorldNode(playerOid);
					} catch (Exception e ){
						Log.error("FallingEventHook: BasicWorldNode is null");
					}
			if (node == null){
				Log.debug("FallingEventHook: BasicWorldNode is null");
				return true;
			}
			float currentY = node.getLoc().getY();

			if (fallingState.equals("start")) {
				// Set falling distance to currentY
				info.setProperty(CombatInfo.COMBAT_PROP_FALLING_START_HEIGHT, currentY);
			} else if (fallingState.equals("end")) {
				// Get distance between start falling height and current Y
				float startHeight =0f;
				try {
					startHeight = (Float) info.getProperty(CombatInfo.COMBAT_PROP_FALLING_START_HEIGHT);
				} catch (Exception e) {
					return true;
				}
				float fallDistance = startHeight - currentY;
				if(Log.loggingDebug)	Log.debug("FallingEventHook: startHeight="+startHeight+" currentY="+currentY+"fallDistance="+fallDistance+" FALL_SAFE_HEIGHT="+FALL_SAFE_HEIGHT);
				if (fallDistance > FALL_SAFE_HEIGHT) {
					// Work out damage to be dealt to the player
					VitalityStatDef statDef = (VitalityStatDef) lookupStatDef(FALL_DAMAGE_STAT);
					float damagePercent = (fallDistance ) / (FALL_DEATH_HEIGHT );
					int damage = (int) (info.statGetCurrentValueWithPrecision(statDef.getMaxStat()) * damagePercent);
					info.statModifyBaseValue(FALL_DAMAGE_STAT, -damage);
					info.statSendUpdate(false);

					// if (dmg > 0)
					Engine.getAgent().sendBroadcast(new CombatClient.DamageMessage(playerOid, playerOid, damage, "",0));

					EventMessageHelper.SendCombatEvent(playerOid, playerOid, EventMessageHelper.COMBAT_DAMAGE, -1, -1, damage, -1,"",FALL_DAMAGE_TYPE);
				}
			}

			return true;
		}
	}

	// Log the login information and send a response
	class LoginHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LoginMessage message = (LoginMessage) msg;
			OID playerOid = message.getSubject();
			OID instanceOid = message.getInstanceOid();
			if (Log.loggingDebug)
				log.debug("LoginHook: playerOid=" + playerOid + " instanceOid=" + instanceOid);

			// Do something
			CombatInfo info = getCombatInfo(playerOid);
			if (info == null) {
				if (Log.loggingDebug)log.debug("LoginHook: playerOid=" + playerOid + " instanceOid=" + instanceOid + " is not loaded");
				Engine.getAgent().sendResponse(new ResponseMessage(message));
				return true;

			}
			
			HashMap<OID, AgisItem> equipList = null;
			try {
				equipList = AgisInventoryClient.getEquipedItems(info.getOid());
			} catch (Exception e1) {
				log.debug("LoginHook Exception: " + e1);
			}

			if (Log.loggingDebug)
				log.debug("LoginHook equipList " + (equipList != null ? equipList.size() : "") + " " + equipList);
			
			if (equipList != null) {
				ArrayList<OID> socketItemList = new ArrayList<OID>();
				for(AgisItem ai : equipList.values()) {
					HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) ai.getProperty("sockets");
					for(SocketInfo si : itemSockets.values()) {
						if(si.GetItemOid()!=null) {
							socketItemList.add(si.GetItemOid());
						}
					}
				}

				for (String statName : CombatPlugin.statDefMap.keySet()) {
					if (Log.loggingDebug)
						log.debug("LoginHook stat " + statName);
					AgisStat stat = (AgisStat) info.getProperty(statName);
					if (stat == null) {
						log.debug("LoginHook stat " + statName + " is nul for player ");
						continue;
					}
					ArrayList<OID> list = stat.getOidModifiers();
					if (Log.loggingDebug)
						log.debug("LoginHook stat " + statName + " modity " + list);
					for (OID oid : list) {
						if (!equipList.containsKey(oid)) {
							
							if(!socketItemList.contains(oid)) {
							if (Log.loggingDebug)
								log.debug("LoginHook not found in equipList remove modifier " + oid + " for stat " + statName + " from Ply " + playerOid);
							AgisItem ai = AgisInventoryClient.getItem(oid);
							if (Log.loggingDebug)
								log.debug("LoginHook item = " + ai);
							if(ai!=null) {
								EquipHelper.UpdateEquiperStats(playerOid, null, ai, info);
							}else {
								info.statRemoveModifier(statName, oid, false);
								info.statRemovePercentModifier(statName, oid, false);
							}
							} else {
								if (Log.loggingDebug)
									log.debug("LoginHook found in sockets in equipList " + oid + " for stat " + statName + " from Ply " + playerOid);
							}
						} else {
							if (Log.loggingDebug)
								log.debug("LoginHook " + oid + " is equiped ");
						}
					}
				}

				//HashMap<Integer, Integer> itemSets = (HashMap) AgisInventoryClient.getSetsEquiped(info.getOid());				
				for (Map.Entry<OID, AgisItem> items : equipList.entrySet())
				{	
					if (items.getValue() != null) 
					{
						AgisItem item = items.getValue();
						EquipHelper.UpdateEquiperStats(playerOid, item, null, info);
						EquipHelper.UpdateEquiperPassiveAbility(playerOid, null, item, info); //Remove effects first
						
						//Only Update if durability is good
						if (EquipHelper.IsDurabilityOk(item)) {
							EquipHelper.UpdateEquiperPassiveAbility(playerOid, item, null, info);
						}
					}
				}
				
				//Update effects and Abilities for enchants/sockets/itemsets
				EquipHelper.UpdateEffectsAndAbilities(playerOid, info, equipList, true, true);


				/*
				EquipHelper.UpdateEquiperItemSetAbilitiesAndEffectsLogin(info, equipList, itemSets);
				EquipHelper.UpdateEnchantAbilitiesAndEffectsLogin(info, equipList);
				*/
				
			}

			//Remove old stats mods form effects
			HashSet<EffectState> currentEffects = (HashSet<EffectState>) info.getCurrentEffects();
			for (String statName : CombatPlugin.statDefMap.keySet()) {
				if (Log.loggingDebug)
					log.debug("LoginHook stat " + statName);
				AgisStat stat = (AgisStat) info.getProperty(statName);
				if (stat == null) {
					log.debug("LoginHook stat " + statName + " is nul for player ");
					continue;
				}
				ArrayList<EffectState> elist = stat.getEffectModifiers();
				ArrayList<EffectState> effectStateToRemoveFromStat = new ArrayList<EffectState>();
				for(EffectState e : elist){
					boolean found =false;
					for(EffectState st : currentEffects){
						if(e.getId() == st.getId()){
							found = true;
						}
					}
					if(!found)
						effectStateToRemoveFromStat.add(e);
				}
				if (Log.loggingDebug)
					log.debug("LoginHook " + playerOid + "stat" + statName+" effectStateToRemoveFromStat "+effectStateToRemoveFromStat.size()+" "+effectStateToRemoveFromStat);
				for (EffectState e : effectStateToRemoveFromStat) {
					info.statRemoveModifier(statName, e, false);
					info.statRemovePercentModifier(statName, e, false);
				}
				ArrayList<String> eslist = stat.getEffectNewModifiers();
				ArrayList<String> sEffectStateToRemoveFromStat = new ArrayList<String>();
				for(String e : eslist){
					boolean found =false;
					String[] ef = e.split("\\|");
					for(EffectState st : currentEffects){
						if(Long.parseLong(ef[2]) == st.getId()){
							found = true;
						}
					}
					if(!found)
						sEffectStateToRemoveFromStat.add(e);
				}
				if (Log.loggingDebug)
					log.debug("LoginHook " + playerOid + "stat" + statName+" sEffectStateToRemoveFromStat "+sEffectStateToRemoveFromStat.size()+" "+sEffectStateToRemoveFromStat);
				for (String e : sEffectStateToRemoveFromStat) {
					info.statRemoveModifier(statName, e, false);
					info.statRemovePercentModifier(statName, e, false);
				}
			}


			if(!info.getPropertyMap().containsKey("genderId")) {
				String gender = "";
				if(!info.getPropertyMap().containsKey("gender")) {
					try {
						gender = (String)EnginePlugin.getObjectProperty(info.getOid(), WorldManagerClient.NAMESPACE, "gender");
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else {
					gender =  (String)info.getProperty("gender");
				}
		    	
				int genderId = RequirementChecker.getIdEditorOptionChoice("Gender", gender);
				if(genderId > 0)
					info.setProperty("genderId", genderId);
			}
			
			//Send Tresholds for Range Stat
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "statRange");
			if (ABILITY_RANGE_MOD_STAT == null) {
				props.put("stat", "");
			} else {
				props.put("stat", ABILITY_RANGE_MOD_STAT);
			}
			if (ABILITY_RANGE_MOD_STAT != null && STAT_THRESHOLDS.containsKey(ABILITY_RANGE_MOD_STAT)) {
				StatThreshold def = STAT_THRESHOLDS.get(ABILITY_RANGE_MOD_STAT);
				for (int i = 1; i <= def.getPoints().size(); i++) {
					props.put("def" + (i - 1) + "T", def.getThresholds().get(i));
					props.put("def" + (i - 1) + "P", def.getPoints().get(i));
				}
				props.put("num", def.getPoints().size());
			} else {
				props.put("num", 0);
			}
			if (Log.loggingDebug)Log.debug("LoginHook: playerOid=" + playerOid + " Send Stat Range props "+props);
			TargetedExtensionMessage emsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			int agents = Engine.getAgent().sendBroadcast(emsg);
			if (Log.loggingDebug)Log.debug("LoginHook: playerOid=" + playerOid + " Send Stat Range agents "+agents);
			
			
			//Send Tresholds for Cost Stat
			props.clear();
			props.put("ext_msg_subtype", "statCost");
			if (ABILITY_COST_MOD_STAT == null) {
				props.put("stat", "");
			} else {
				props.put("stat", ABILITY_COST_MOD_STAT);
			}
			if (ABILITY_COST_MOD_STAT != null && STAT_THRESHOLDS.containsKey(ABILITY_COST_MOD_STAT)) {
				StatThreshold def = STAT_THRESHOLDS.get(ABILITY_COST_MOD_STAT);
				for (int i = 1; i <= def.getPoints().size(); i++) {
					props.put("def" + (i - 1) + "T", def.getThresholds().get(i));
					props.put("def" + (i - 1) + "P", def.getPoints().get(i));
				}
				props.put("num", def.getPoints().size());
			} else {
				props.put("num", 0);
			}
			if (Log.loggingDebug)
				Log.debug("LoginHook: playerOid=" + playerOid + " Send Stat Cost props "+props);
			TargetedExtensionMessage emsg1 = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			agents = Engine.getAgent().sendBroadcast(emsg1);
			if (Log.loggingDebug)
				Log.debug("LoginHook: playerOid=" + playerOid + " Send Stat Cost agents "+agents);
	
			
			//Send Tresholds for Cast time Stat
			props.clear();
			props.put("ext_msg_subtype", "statCastTime");
			if (ABILITY_CAST_TIME_MOD_STAT == null) {
				props.put("stat", "");
			} else {
				props.put("stat", ABILITY_CAST_TIME_MOD_STAT);
			}
			if (ABILITY_CAST_TIME_MOD_STAT != null && STAT_THRESHOLDS.containsKey(ABILITY_CAST_TIME_MOD_STAT)) {
				StatThreshold def = STAT_THRESHOLDS.get(ABILITY_CAST_TIME_MOD_STAT);
				for (int i = 1; i <= def.getPoints().size(); i++) {
					props.put("def" + (i - 1) + "T", def.getThresholds().get(i));
					props.put("def" + (i - 1) + "P", def.getPoints().get(i));
				}
				props.put("num", def.getPoints().size());
			} else {
				props.put("num", 0);
			}
			if (Log.loggingDebug)
				Log.debug("LoginHook: playerOid=" + playerOid + " Send Stat Cost props "+props);
			TargetedExtensionMessage emsg2 = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			agents = Engine.getAgent().sendBroadcast(emsg2);
			if (Log.loggingDebug)
				Log.debug("LoginHook: playerOid=" + playerOid + " Send Stat Cost agents "+agents);
	
			
			
			
			
			/*
			 * if (info != null) { AgisEffect regenEffect =
			 * Agis.EffectManager.get(HEALTH_REGEN_EFFECT); AgisEffect regenEffect2 =
			 * Agis.EffectManager.get(ENERGY_REGEN_EFFECT); startRegen(info, "health",
			 * regenEffect); startRegen(info, "mana", regenEffect2);
			 * 
			 * }
			 */
			// Reactivation cooldowns
			Collection<Cooldown> cooldowns = (LinkedList<Cooldown>) info.getProperty("cooldowns");
			if (cooldowns != null && cooldowns.size() > 0) {
				
				double cooldownMod = 100;
			if(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT!=null) {
				double statValue = info.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
				if (Log.loggingDebug)
					Log.debug("AgisAbility: CooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
				double calculated = 0;
				if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT)) {
					StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
					int pointsCalculated = 0;
					for (int i = 1; i <= def.getPoints().size(); i++) {
						if (Log.loggingDebug)
							Log.debug("AgisAbility: CooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
						if (statValue <= def.getThresholds().get(i)) {
							Log.debug("AgisAbility: CooldownMod statValue < th");
							if (statValue - pointsCalculated < 0)
								break;
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += statValue - pointsCalculated;
						} else {
							Log.debug("AgisAbility: CooldownMod statValue > th");
							calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
						}
					}
					/*if (pointsCalculated < statValue) {
						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
					}*/
				} else {
					calculated = statValue;
				}
				if (Log.loggingDebug)
					Log.debug("AgisAbility: CooldownMod calculated: " + calculated);	
				cooldownMod = calculated;//Math.round(calculated/100f);
				if (Log.loggingDebug)
					Log.debug("AgisAbility: CooldownMod calculated="+calculated+" mod="+cooldownMod);
				
				
			}
			double cooldownGlobalMod = 100;
			if(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT!=null) {
				double statValue = info.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
				if (Log.loggingDebug)
					Log.debug("AgisAbility: GlobalCooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
				double calculated = 0;
				if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT)) {
					StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
					int pointsCalculated = 0;
					for (int i = 1; i <= def.getPoints().size(); i++) {
						if (Log.loggingDebug)
							Log.debug("AgisAbility: GlobalCooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
						if (statValue <= def.getThresholds().get(i)) {
							Log.debug("AgisAbility: GlobalCooldownMod statValue < th");
							if (statValue - pointsCalculated < 0)
								break;
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += statValue - pointsCalculated;
						} else {
							Log.debug("AgisAbility: GlobalCooldownMod statValue > th");
							calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
						}
					}
					/*if (pointsCalculated < statValue) {
						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
					}*/
				} else {
					calculated = statValue;
				}
				if (Log.loggingDebug)
					Log.debug("AgisAbility: GlobalCooldownMod calculated: " + calculated);	
				cooldownGlobalMod = calculated;//Math.round(calculated/100f);
				if (Log.loggingDebug)
					Log.debug("AgisAbility: GlobalCooldownMod calculated="+calculated+" mod="+cooldownGlobalMod);
				
				
			}
			double cooldownWeaponMod = 100;
			if(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT!=null) {
				double statValue = info.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
				if (Log.loggingDebug)
					Log.debug("AgisAbility: WeaponCooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
				double calculated = 0;
				if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT)) {
					StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
					int pointsCalculated = 0;
					for (int i = 1; i <= def.getPoints().size(); i++) {
						if (Log.loggingDebug)
							Log.debug("AgisAbility: WeaponCooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
						if (statValue <= def.getThresholds().get(i)) {
							Log.debug("AgisAbility: WeaponCooldownMod statValue < th");
							if (statValue - pointsCalculated < 0)
								break;
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += statValue - pointsCalculated;
						} else {
							Log.debug("AgisAbility: WeaponCooldownMod statValue > th");
							calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
						}
					}
					/*if (pointsCalculated < statValue) {
						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
					}*/
				} else {
					calculated = statValue;
				}
				if (Log.loggingDebug)
					Log.debug("AgisAbility: WeaponCooldownMod calculated: " + calculated);	
				cooldownWeaponMod = calculated;//Math.round(calculated/100f);
				if (Log.loggingDebug)
					Log.debug("AgisAbility: WeaponCooldownMod calculated="+calculated+" mod="+cooldownWeaponMod);
				
				
			}
			Cooldown.activateCooldowns(cooldowns, info, cooldownGlobalMod, cooldownWeaponMod, cooldownMod, info.statGetBaseValue(CombatPlugin.ATTACK_SPEED_STAT));
			//	Cooldown.activateCooldowns(cooldowns, info, 100);
			}
			if (info.dead() && RELEASE_ON_LOGIN) {
				CombatClient.releaseObject(playerOid, false);
			}

			info.setCombatState(false);
			
			Integer arenaId = info.getIntProperty(CombatInfo.COMBAT_PROP_ARENA_ID);
			
			if(arenaId == null || arenaId != -1) {
			//	info.setProperty(CombatInfo.COMBAT_PROP_ARENA_ID, -1);
			}
			info.setProperty(CombatInfo.COMBAT_PROP_DUEL_ID, -1);


			int aspect = info.aspect();
			int race = info.getIntProperty("race");
			//Get Player Template definition
			CharacterTemplate tmpl = ClassAbilityPlugin.getCharacterTemplate(race + " " + aspect);
			//Get Dodge Ability from Player Template definition
			int abilityID = tmpl.getDodge();


			props.clear();
			props.put("ext_msg_subtype", "combatSettings");
			props.put("DevMode", EnginePlugin.DevMode);
			props.put("dodge", abilityID);
			if (Log.loggingDebug)
				Log.debug("LoginHook: playerOid=" + playerOid + " Send combatSettings props "+props);
			TargetedExtensionMessage emsg3 = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			agents = Engine.getAgent().sendBroadcast(emsg3);
			if (Log.loggingDebug)
				Log.debug("LoginHook: playerOid=" + playerOid + " Send combatSettings agents "+agents);
			
			/*
			 * EnginePlugin.setObjectProperty(playerOid, CombatClient.NAMESPACE,
			 * "combatstate", false); EnginePlugin.setObjectPropertyNoResponse(playerOid,
			 * WorldManagerClient.NAMESPACE, "weaponsSheathed", true);
			 */

			// Update the attitudes of all players around this one
			// TODO: addPlayerAttitudes(playerOid);?

			Engine.getAgent().sendResponse(new ResponseMessage(message));
			
			
			if (Log.loggingDebug)	Log.debug("LoginHook: playerOid=" + playerOid + "send response");
			
			if (Log.loggingDebug)	Log.debug("LoginHook: playerOid=" + playerOid + " END");
			return true;
		}
	}

	// Log the logout information and send a response. No longer used.
	class LogoutHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LogoutMessage message = (LogoutMessage) msg;
			OID playerOid = message.getSubject();
			if (Log.loggingDebug)Log.debug("LOGOUT: combat logout started for: " + playerOid);
			// removeBreathEffect(playerOid);
			// removeFatigueEffect(playerOid);
			// Log.debug("LogoutHook: playerOid=" + playerOid);
			// CombatInfo info = getCombatInfo(playerOid);
			// Do something
			Engine.getAgent().sendResponse(new ResponseMessage(message));
			if (Log.loggingDebug)Log.debug("LOGOUT: combat logout finished for: " + playerOid);
			return true;
		}
	}

	/**
	 * Sets up the starting stats for players and mobs. I'm not totally happy with
	 * this setup.
	 *
	 * @param xpProfile
	 * @param level
	 * @param isMob
	 * @param statOverrides
	 * @return
	 */
	public HashMap<String, AgisStat> getStartingStats(int xpProfile, int level, boolean isMob, HashMap<String, Integer> statOverrides,int statProfile) {
		
		HashMap<String, AgisStat> statMap = new HashMap<String, AgisStat>();
		// Other Stats
		// int attack_speed = 2000;
		// int dmg_base = 20;
		AgisStatDef dmgMod = statDefMap.get("dmg-dealt-mod");
		statMap.put("dmg-dealt-mod", new AgisStat(dmgMod.getId(),"dmg-dealt-mod", dmgMod.getMobStartingValue()));
		dmgMod = statDefMap.get("dmg-taken-mod");
		statMap.put("dmg-taken-mod", new AgisStat(dmgMod.getId(),"dmg-taken-mod", dmgMod.getMobStartingValue()));

		// statMap.put("attack_speed", new AgisStat("attack_speed",
		// attack_speed));
		AgisStatDef expMax = statDefMap.get(EXPERIENCE_MAX_STAT);
		AgisStatDef exp = statDefMap.get(EXPERIENCE_STAT);
		statMap.put(EXPERIENCE_MAX_STAT, new AgisStat(expMax.getId(),EXPERIENCE_MAX_STAT, ClassAbilityPlugin.GetStartingXpReq(xpProfile)));
		statMap.put(EXPERIENCE_STAT, new AgisStat(exp.getId(),EXPERIENCE_STAT, 0));

		AgisStatDef lev = statDefMap.get(LEVEL_STAT);
		statMap.put(LEVEL_STAT, new AgisStat(lev.getId(), LEVEL_STAT, level));
		// Mobs stats are set by default with override stats being used to alter
		// values for an indivdual mob
		if (Log.loggingDebug)log.debug("getStartingStats: isMob="+isMob+" STAT_LIST="+STAT_LIST+" statDefMap="+statDefMap);
		if (STAT_LIST == null) {
			CombatDatabase cDB = new CombatDatabase(false);
			loadStatData(cDB);
			cDB.close();
		}
		if (isMob) {
			if (Log.loggingDebug)log.debug("getStartingStats: add from Stat Default Definitions");
			for (String stat : STAT_LIST) {
				AgisStatDef statDef = statDefMap.get(stat);
				if (Log.loggingDebug)log.debug("getStartingStats: stat="+stat);
				if(statDef.getServerPresent()) {
					float value = statDef.getMobStartingValue() + ((level - 1) * statDef.getMobLevelIncrease());
					if (statOverrides != null && statOverrides.containsKey(stat)) {
						value = statOverrides.get(stat);
						if (Log.loggingDebug)
							Log.debug("STAT: using mob stat override for stat: " + stat + " with value: " + value);
					} else {
						value += value * ((level - 1) * (statDef.getMobLevelPercentIncrease() / 100));
					}
					statMap.put(stat, new AgisStat(statDef.getId(), stat, (int) value));
					if (Log.loggingDebug) Log.debug("STAT: gave mob stat: " + stat + " with value: " + value);
				}else{
					if (Log.loggingDebug)log.debug("getStartingStats: stat="+stat+" skip ");
				}
			}
			if (Log.loggingDebug)log.debug("getStartingStats:statProfile="+statProfile);

			if(statProfile > 0) {
				StatProfile sp = STAT_PROFILES.get(statProfile);
				if(sp!=null){
					for(Map.Entry<Integer,Integer> e : sp.getStats().entrySet()){
						if (Log.loggingDebug)
							Log.debug("getStartingStats: Key="+e.getKey()+" name=" + STAT_NAMES.get(e.getKey()) + " value: " + e.getValue());
						AgisStatDef statDef = statDefMap.get(STAT_NAMES.get(e.getKey()));
						if(statDef.getServerPresent() && !(statDef instanceof DmgBaseStat) && !(statDef instanceof ExperienceStat) && !statDef.getName().equals(EXPERIENCE_MAX_STAT)) {
							float value = 1F * e.getValue() + ((level - 1) * sp.getStatsLevelIncrease().get(e.getKey()));
								value += value * ((level - 1) * (sp.getStatsLevelPercentIncrease().get(e.getKey()) / 100F));
							statMap.put(STAT_NAMES.get(e.getKey()), new AgisStat(statDef.getId(), STAT_NAMES.get(e.getKey()), (int) value));
							if (Log.loggingDebug)
								Log.debug("STAT: gave mob stat: " + STAT_NAMES.get(e.getKey()) + " with value: " + value);
						}else{
							if (Log.loggingDebug)log.debug("getStartingStats: stat="+statDef.getName()+" skip ");
						}
					}
					for (String s : sp.getStatsNotSendToClient() ){
						if (Log.loggingDebug)
							Log.debug("getStartingStats: getStatsNotSendToClient name=" + s );
						if(!statMap.containsKey(s)  && !s.equals(EXPERIENCE_MAX_STAT) ) {
							AgisStatDef statDef = statDefMap.get(s);
							if( !(statDef instanceof DmgBaseStat) && !(statDef instanceof ExperienceStat)) {
								float value = statDef.getMobStartingValue() + ((level - 1) * statDef.getMobLevelIncrease());
								value += value * ((level - 1) * (statDef.getMobLevelPercentIncrease() / 100));
								statMap.put(s, new AgisStat(statDef.getId(), s, (int) value));
								if (Log.loggingDebug) Log.debug("STAT: gave mob stat: " + s + " with value: " + value);
							}else{
								if (Log.loggingDebug)log.debug("getStartingStats: stat="+s+" skip ");
							}
						}else{
							if (Log.loggingDebug)log.debug("getStartingStats: stat="+s+" skip ");
						}
					}
					for (String s : sp.getStatsSendToClient() ){
						if (Log.loggingDebug)
							Log.debug("getStartingStats: getStatsSendToClient name=" + s );

						if(!statMap.containsKey(s) && !s.equals(EXPERIENCE_MAX_STAT) ) {
							AgisStatDef statDef = statDefMap.get(s);
							if(!(statDef instanceof DmgBaseStat) && !(statDef instanceof ExperienceStat)) {
								float value = statDef.getMobStartingValue() + ((level - 1) * statDef.getMobLevelIncrease());
								value += value * ((level - 1) * (statDef.getMobLevelPercentIncrease() / 100));
								statMap.put(s, new AgisStat(statDef.getId(), s, (int) value));
								if (Log.loggingDebug) Log.debug("STAT: gave mob stat: " + s + " with value: " + value);
							}else{
								if (Log.loggingDebug)log.debug("getStartingStats: stat="+s+" skip ");
							}
						}else{
							if (Log.loggingDebug)log.debug("getStartingStats: stat="+s+" skip ");
						}
					}

					for (String statName : sp.getStatsToDelete()){
						if (Log.loggingDebug)Log.debug("getStartingStats: stat "+statName+" to delete ");
						if (statMap.containsKey(statName)) {
							statMap.remove(statName);
							if (Log.loggingDebug)Log.debug("getStartingStats: stat "+statName+" deleted ");
						}else{
							if (Log.loggingDebug)Log.debug("getStartingStats: stat "+statName+" not found ");
						}
					}


				}
			}
//			else {
//				for (String stat : STAT_LIST) {
//					AgisStatDef statDef = statDefMap.get(stat);
//					if(statDef.getServerPresent()) {
//						float value = statDef.getMobStartingValue() + ((level - 1) * statDef.getMobLevelIncrease());
//						if (statOverrides != null && statOverrides.containsKey(stat)) {
//							value = statOverrides.get(stat);
//							if (Log.loggingDebug)
//								Log.debug("STAT: using mob stat override for stat: " + stat + " with value: " + value);
//						} else {
//							value += value * ((level - 1) * (statDef.getMobLevelPercentIncrease() / 100));
//						}
//						statMap.put(stat, new AgisStat(statDef.getId(), stat, (int) value));
//						if (Log.loggingDebug) Log.debug("STAT: gave mob stat: " + stat + " with value: " + value);
//					}
//				}
//			}
			// dmg_base = (int)((10 + level * 3) *
			// ((float)attack_speed/1000.0f));
			// statMap.put("dmg-base", new AgisStat("dmg-base", dmg_base));
		} else {
			// Still need to do health and mana calculations for player
			// characters
			AgisStatDef statDef1 = statDefMap.get("dmg-base");
			AgisStatDef statDef2 = statDefMap.get("dmg-max");
			statMap.put("dmg-base", new AgisStat(statDef1.getId(),"dmg-base", 0));
			statMap.put("dmg-max", new AgisStat(statDef2.getId(),"dmg-max", 0));
		}
		if (Log.loggingDebug)log.debug("getStartingStats   End statMap="+statMap);
		return statMap;
	}

	// Temporary tick system
	class CombatStatTick implements Runnable {

		public CombatStatTick(CombatInfo info) {
			this.info = info;
			oid = info.getOwnerOid();
		}

		CombatInfo info;
		OID oid;
		boolean active = true;

		public void run() {
			// for (CombatInfo info : activeInfos) {
			if (info != null && active) {
				info.runCombatTick();
				Engine.getExecutor().schedule(this, 1, TimeUnit.SECONDS);
			} else {
				statTicks.remove(oid);
			}
			// }
		}

		public void disable() {
			active = false;
			Log.debug("STAT: disabling vitality stat ticks");
		}

		/*
		 * public void AddCombatInfo(CombatInfo info) { activeInfos.add(info); } public
		 * void RemoveCombatInfo(CombatInfo info) { activeInfos.remove(info); }
		 * ArrayList<CombatInfo> activeInfos = new ArrayList<CombatInfo>();
		 */
	}

	public static boolean isPlayerAlive(OID playerOid) {
		// Dead check
		Boolean dead = (Boolean) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
		if (dead != null && dead) {
			return false;
		} else {
			return true;
		}
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

	static ScheduledFuture<?> sf = null;
	static protected List<RankingData> dataSendingCollection = Collections.synchronizedList(new LinkedList<RankingData>());
    
		public static void RunStealthReduceTimeOut(OID subjectOid, AgisAbility ability) {
			StealthReduceTimeout timer = new StealthReduceTimeout( subjectOid);
        	ScheduledFuture sf = Engine.getExecutor().schedule(timer, (long) ability.getStealthReductionTimeout() * 1000, TimeUnit.MILLISECONDS);
        	if (tasks.containsKey(subjectOid)) {
        		tasks.get(subjectOid).cancel(true);
	    		tasks.remove(subjectOid);
        	}
        	tasks.put(subjectOid, sf);
		}    
	    
	    
	static class StealthReduceTimeout implements Runnable {
		OID _obj = null;
		
		StealthReduceTimeout(OID obj){
			this._obj = obj;
		}
		
		public void run() {
			tasks.remove(_obj);
			CombatInfo ci = getCombatInfo(_obj);
			ci.statRemoveModifier(STEALTH_STAT, "Ability", false);
			ci.statRemovePercentModifier(STEALTH_STAT, "Ability", true);
		}
	}
	
   static HashMap<OID, ScheduledFuture> tasks = new HashMap<OID, ScheduledFuture>();

	// CombatStatTick statTick = new CombatStatTick();
	HashMap<OID, CombatStatTick> statTicks = new HashMap<OID, CombatStatTick>();

	// Graveyards
	HashMap<Integer, ArrayList<Graveyard>> graveyards = new HashMap<Integer, ArrayList<Graveyard>>();
	
	public static ConcurrentHashMap<String, BonusSettings> globalEventBonusesArray = new ConcurrentHashMap<String, BonusSettings>();
	
	public static LinkedList<String> WMGR_STATS ; 
	private static AccountDatabase aDB;
	// Static settings

	public static OID ABILITY_DEBUG_PLAYER = null;
	public static final int STAT_BASE = 100;
	public static final int HEALTH_BASE = 500;
	public static final int MANA_BASE = 10;
	public static final int FLATRESIST_BASE = 0;
	public static final int PERCENTRESIST_BASE = 0;
	public static boolean DEATH_LOST_EXP = false;
	public static float DEATH_LOST_EXP_PERCENTAGE = 10f;
	public static boolean DEATH_PERMANENTLY = false;
	public static int RESISTANCE_STAT_MAX = 10000;

	public static int DAMAGE_HITROLL_MODIFIER = 35;

	public static String DAMAGE_DEALT_MODIFIER = "dmg-dealt-mod";
	public static String DAMAGE_TAKEN_MODIFIER = "dmg-taken-mod";
	public static String ATTACK_SPEED_STAT = "attack_speed";
	
	
	public static String HEALTH_STAT = "health";
	public static String HEALTH_MAX_STAT = "health-max";

	public static String EXPERIENCE_STAT = "experience";
	public static String EXPERIENCE_MAX_STAT = "experience-max";
	public static String PET_GLOBAL_COUNT_STAT = "pet-global-count";
//	public static String MOVEMENT_SPEED_STAT = null;
	
	// These stat mappings are loaded in from the database
	public static String POWER = "Power";
	public static String ACCURACY = "Accuracy";
	public static String EVASION = "Evasion";
	public static String CRITIC_CHANCE = "Critic Chance";
	public static String CRITIC_POWER = "Critic Power";
	//public static String PHYSICAL_POWER_STAT = null;
	//public static String PHYSICAL_ACCURACY_STAT = null;
	//public static String MAGICAL_CRITIC_STAT = null;
	//public static String MAGICAL_CRITIC_POWER_STAT = null;
	//public static String MAGICAL_EVASION_STAT = null;
	public static String PARRY_STAT = null;

	public static String STEALTH_STAT = null;
	public static String PERCEPTION_STEALTH_STAT = null;
	
	
	public static double HIT_CHANCE_POINT_PER_PERCENTAGE = 10;
	public static double HIT_CHANCE_PERCENTAGE_PER_DIFF_LEVEL = 1;
	public static double HIT_CHANCE_PERCENTAGE_CAP =30f;
	public static double PARRY_PERCENTAGE_CAP = 40f;
	
	public static String SLEEP_RESISTANCE_STAT = null;
	public static String STUN_RESISTANCE_STAT = null;
	public static String SLEEP_CHANCE_STAT = null;
	public static String STUN_CHANCE_STAT = null;
	
	public static String SLOWDOWN_RESISTANCE_STAT = null;
	public static String IMMOBILIZE_RESISTANCE_STAT = null;
	
	public static String INTERRUPTION_RESISTANCE_STAT = null;
	public static String INTERRUPTION_CHANCE_STAT = null;
	
	
	public static String ABILITY_COST_MOD_STAT = null;
	public static String ABILITY_CAST_TIME_MOD_STAT = null;
	public static String ABILITY_COOLDOWN_MOD_STAT = null;
	public static String ABILITY_GLOBAL_COOLDOWN_MOD_STAT = null;
	public static String ABILITY_WEAPON_COOLDOWN_MOD_STAT = null;
	
	public static String ABILITY_HEALTH_DEALT_MOD_STAT = null;
	public static String ABILITY_HEALTH_RECEIVE_MOD_STAT = null;
	public static String ABILITY_DAMAGE_DEALT_MOD_STAT = null;
	public static String ABILITY_DAMAGE_RECEIVE_MOD_STAT = null;
	public static String ABILITY_RANGE_MOD_STAT = null;


	// Dragonsan End Add Stars
	public static String WEIGHT_STAT = null;
	public static String LEVEL_STAT = "level";

	public static Set<String> GetStatNotSendToClientByStatDef(){
		Set<String> list = new HashSet<>();

		for (String s : STAT_NAMES.values()){
			AgisStatDef statDef = CombatPlugin.statDefMap.get(s);
			if(statDef != null){
				if(!statDef.getSendToClient()){
					list.add(s);
				}
			}
		}
		return list;
	}

	// Maps damage types to the stat that is used to reduce damage taken
	public static HashMap<String, DamageType> DAMAGE_TYPES;
	public static LinkedList<String> STAT_LIST;
	public static HashMap<Integer ,StatProfile> STAT_PROFILES;
	public static HashMap<Integer ,String> STAT_NAMES = new HashMap<Integer ,String>();
	public static HashMap<String ,Integer> STAT_IDS = new HashMap<String ,Integer>();
	public static HashMap<String ,StatThreshold> STAT_THRESHOLDS;
	public static HashMap<Integer ,String> STAT_PETS_COUNT = new HashMap<Integer ,String>();

	public static final String PROP_HITBOX = "hitBox";

	public static boolean RANGE_CHECK_VERTICAL = true;
	public static boolean MOBS_STOP_TO_ATTACK = false;
	public static int SPIRIT_EFFECT = -1;
	public static boolean RELEASE_ON_LOGIN = false;
	public static boolean MAGICAL_ATTACKS_USE_WEAPON_DAMAGE = false;//obsolote
	public static boolean EXP_BASED_ON_DAMAGE_DEALT = true;
	public static boolean WEAPON_REQ_USES_SHARED_TYPES = true;//obsolete
	public static long COMBAT_TIMEOUT = 20000; // 20 seconds
	public static boolean USE_PLAYER_SET_RESPAWN_LOCATIONS = true;
	public static boolean RESPAWN_IN_DUNGEON = true;
	public static boolean FLAT_ARMOR_DAMAGE_CALCULATIONS = true;

	public static float GLOBAL_COOLDOWN = 1f;
	public static float WEAPON_COOLDOWN = 3f;
	public static boolean ABILITY_WEAPON_COOLDOWN_ATTACK_SPEED = false;
	
	public static int SAVE_COOLDOWN_LIMIT_DURATION = 60;
	
	public static float FALL_SAFE_HEIGHT = 10f;
	public static float FALL_DEATH_HEIGHT = 50f;
	public static String FALL_DAMAGE_STAT = "health";
	public static String FALL_DAMAGE_TYPE = "crash";
	public static String DEFAULT_EFFECT_DAMAGE_TYPE = "";

	public static int USE_ABILITY_STEALTH_REDUCTION = 0;
	public static int USE_ABILITY_STEALTH_REDUCTION_TIMEOUT = 60;
	public static float USE_ABILITY_STEALTH_REDUCTION_PERCENTAGE = 0f;

	public static int EXP_LOST_FROM_MOB_DEATH = 0;
	public static int PREMIUM_EXP_LOST_FROM_MOB_DEATH = 0;
	public static int PREMIUM_BONUS_EXP = 0;
	// PVP Damage Game Settings
	public static float PVP_DAMAGE_REDUCTION_PERCENT = 0.1f;
	public static boolean PVP_DAMAGE_REDUCTION_USE = true;
	// Settings below here to be deleted
	public static final int ATTACK_ABILITY = 1;
	public static final int PLAYER_ATTACK_ABILITY = 1;
	public static final int DEFAULT_MOVEMENT_SPEED = 7;
	public static float PET_DISTANCE_DESPAWN = 50f;
	// public static boolean MOB_FORCE_DESPAWN_IN_COMBAT=false;
	
	
	
	
	/**
	 * Percentage for Additional Randomization for stun chance
	 */
	public static float STAT_RANDOM_HIT_STUN = 0F;
	
	/**
	 * Percentage for Additional Randomization for sleep chance
	 */
	public static float STAT_RANDOM_HIT_SLEEP = 0F;
	
	/**
	 * Percentage for Additional Randomization for interruption chance
	 */
	public static float STAT_RANDOM_HIT_INTERRUPTION = 0F;

}
