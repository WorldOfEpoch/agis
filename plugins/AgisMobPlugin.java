package atavism.agis.plugins;

import atavism.msgsys.Message;
import atavism.msgsys.MessageAgent;
import atavism.msgsys.MessageTypeFilter;
import atavism.msgsys.ResponseMessage;
import atavism.msgsys.SubjectMessage;
import atavism.server.engine.*;
import atavism.server.messages.*;
import atavism.server.math.*;
import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.plugins.InstanceClient.InstanceInfo;
import atavism.server.plugins.WorldManagerClient.CheckVisibilityMessage;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.ObjectInfo;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.telemetry.Prometheus;
import atavism.server.util.Log;
import io.micrometer.core.instrument.Gauge;
import atavism.agis.behaviors.*;
import atavism.agis.core.Agis;
import atavism.agis.core.AgisAbility;
import atavism.agis.core.AgisEffect;
import atavism.agis.database.*;
import atavism.agis.objects.*;
import atavism.agis.objects.Currency;
import atavism.agis.plugins.BonusClient.GlobalEventBonusesUpdateMessage;
import atavism.agis.plugins.VoxelClient.AddDynamicObjectMessage;
import atavism.agis.plugins.VoxelPlugin.TargetTypeUpdateHook;
import atavism.agis.util.*;

import java.sql.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.io.*;

public class AgisMobPlugin extends MobManagerPlugin {

    private final Map<OID, AtomicInteger> mobsPerInstance = new ConcurrentHashMap<>();
    /*
     * Properties
     */
    
    //protected static final Logger _log = new Logger("AgisMobPlugin");

    /*
     * Events
     */ 
    
    public void onActivate() {
        super.onActivate();
        // register message hooks
        getHookManager().addHook(BonusClient.MSG_TYPE_GLOBAL_EVENT_UPDATE, new GlobalEventBonusesUpdateHook());
        WorldManagerClient.visibilityCheckFunction = this::checkVisibility;
    	getHookManager().addHook(WorldManagerClient.MSG_TYPE_CHECK_VISIBILITY, new CheckVisibilityHook());
    	getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_MOB_FIND_NEAREST_POINT, new FindNearestPointHook());
    	getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_MOB_GET_PATH, new GetPathHook());
        		//getHookManager().addHook(AgisMobClient.MSG_TYPE_GET_INSTANCE_TEMPLATE, new GetInstanceTemplateHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_SPAWN_INTERACTIVE_OBJECT, new SpawnInteractiveObjectHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_SPAWN_INSTANCE_MOBS, new SpawnInstanceMobsHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_SPAWN_MOB, new SpawnMobHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_DELETE_SPAWN_GENERATOR, new DeleteSpawnGeneratorHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_SPAWN_ARENA_CREATURE, new SpawnArenaCreatureHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_SPAWN_PET, new SpawnPetHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_TAME_BEAST, new TameBeastHook());
		getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_SERVER_TIME, new ServerTimeHook());
		   // Hooks to process login/logout messages
		getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_DESPAWNED, new DespawnedHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_INTERACT_WITH_OBJECT, new InteractWithObjectHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_VERIFY_ISLAND_ACCESS, new VerifyIslandAccessHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_REQUEST_DEVELOPER_ACCESS, new RequestIslandDeveloperAccessHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_SPAWN_DOME_MOB, new SpawnDomeMobHook());

		getHookManager().addHook(InstanceClient.MSG_TYPE_INSTANCE_LOADED, new LoadInstanceObjectsHook());
		
		//getHookManager().addHook(AgisMobClient.MSG_TYPE_PLAY_COORD_EFFECT, new PlayCoordinatedEffectHook());
		getHookManager().addHook(PropertyMessage.MSG_TYPE_PROPERTY, new PropertyHook());
		
		getHookManager().addHook(AgisMobClient.MSG_TYPE_ADD_NM_OBJECT, new GetNMObjectsHook());//FIXME
		getHookManager().addHook(AgisMobClient.MSG_TYPE_DEBUG_NM, new SetDebugNavMeshHook());
		getHookManager().addHook(VoxelClient.MSG_TYPE_ADD_DYNAMIC_NM_OBJECT, new AddDynamicNMObjectHook());
		
		getHookManager().addHook(CombatClient.MSG_TYPE_TARGET_TYPE, new TargetTypeUpdateHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_MOB_GET_ACTOR_SPEED, new GetActorSpeedHook());

		getHookManager().addHook(AgisMobClient.MSG_TYPE_DEBUG_MOB, new SetDebugPlayerHook());

		//mobFilterTypes.add(WorldManagerClient.MSG_TYPE_DESPAWNED);
		mobFilterTypes.add(AgisMobClient.MSG_TYPE_SPAWN_INSTANCE_MOBS);
		mobFilterTypes.add(AgisMobClient.MSG_TYPE_SPAWN_INTERACTIVE_OBJECT);
		mobFilterTypes.add(AgisMobClient.MSG_TYPE_SPAWN_MOB);
		mobFilterTypes.add(AgisMobClient.MSG_TYPE_DELETE_SPAWN_GENERATOR);
		mobFilterTypes.add(AgisMobClient.MSG_TYPE_SPAWN_ARENA_CREATURE);
		mobFilterTypes.add(AgisMobClient.MSG_TYPE_SPAWN_PET);
		mobFilterTypes.add(AgisMobClient.MSG_TYPE_TAME_BEAST);
		mobFilterTypes.add(AgisMobClient.MSG_TYPE_VERIFY_ISLAND_ACCESS);
		mobFilterTypes.add(AgisMobClient.MSG_TYPE_REQUEST_DEVELOPER_ACCESS);
		mobFilterTypes.add(AgisMobClient.MSG_TYPE_SPAWN_DOME_MOB);
		mobFilterTypes.add(InstanceClient.MSG_TYPE_INSTANCE_LOADED);
		mobFilterTypes.add(AgisMobClient.MSG_TYPE_SET_BLOCK);
		// Account login message - no idea if it should go here
		mobFilterTypes.add(VoxelClient.MSG_TYPE_ADD_DYNAMIC_NM_OBJECT);
		mobFilterTypes.add(ProxyPlugin.MSG_TYPE_ACCOUNT_LOGIN);
		
        // setup message filters
        MessageTypeFilter filter = new MessageTypeFilter();
        filter.addType(BonusClient.MSG_TYPE_GLOBAL_EVENT_UPDATE);
        filter.addType(WorldManagerClient.MSG_TYPE_SPAWNED);
		filter.addType(WorldManagerClient.MSG_TYPE_DESPAWNED);
		filter.addType(AgisMobClient.MSG_TYPE_INTERACT_WITH_OBJECT);
        filter.addType(AgisMobClient.MSG_TYPE_ADD_NM_OBJECT);
		filter.addType(AgisMobClient.MSG_TYPE_DEBUG_NM);
        filter.addType(PropertyMessage.MSG_TYPE_PROPERTY);
        filter.addType(AgisWorldManagerClient.MSG_TYPE_SERVER_TIME);
        filter.addType(CombatClient.MSG_TYPE_TARGET_TYPE);
		filter.addType(AgisMobClient.MSG_TYPE_DEBUG_MOB);
        Engine.getAgent().createSubscription(filter, this);
		
        //setup responder message filters
        MessageTypeFilter responderFilter = new MessageTypeFilter();
      	responderFilter.addType(LogoutMessage.MSG_TYPE_LOGOUT);
        responderFilter.addType(LoginMessage.MSG_TYPE_LOGIN);
       // types.clear();
        
        mobRPCFilterTypes.add(WorldManagerClient.MSG_TYPE_CHECK_VISIBILITY);
        mobRPCFilterTypes.add(AgisWorldManagerClient.MSG_TYPE_MOB_FIND_NEAREST_POINT); 
        mobRPCFilterTypes.add(AgisMobClient.MSG_TYPE_MOB_GET_ACTOR_SPEED); 
        mobRPCFilterTypes.add(AgisWorldManagerClient.MSG_TYPE_MOB_GET_PATH);
        Engine.getAgent().createSubscription(responderFilter, this, MessageAgent.RESPONDER);
        
        
        // Load settings
        cDB = new ContentDatabase(true);
        mobDataBase = new MobDatabase(true);
		aDB = new AccountDatabase(true);
        loadData();
		createFactories();
		
    }
    
    private void loadCategoryContent(MobDatabase mDB, ContentDatabase cDB, int categoryID) {
    	Log.debug("Agis MOB: loading content for category: " + categoryID);
    	ContentCategory cc = new ContentCategory(categoryID);
    	
    	HashMap<Integer, AgisBasicQuest> questMap =  mDB.loadQuests(categoryID);
    	for (int questID : questMap.keySet()) {
    		Log.debug("MOB: loaded Quest: "+questID+" [" + questMap.get(questID) + "]");
    		Agis.QuestManager.register(questID, questMap.get(questID));
    	}
    	// Factions
    	ArrayList<Faction> factions = mDB.loadFactions(categoryID);
    	for (Faction faction: factions) {
    		Agis.FactionManager.register(faction.getID(), faction);
    		if (Log.loggingDebug)
    	    	  	Log.debug("MOB: loaded faction: [" + faction.getName() + "]");
    	}
		//Combat behavior Settings
		mobCombatBehaviorProfiles = mDB.loadBehaviorProfile();

		// Mob Templates
    	ArrayList<Template> mobTemplates = mDB.loadMobTemplates(categoryID);
		if (Log.loggingDebug)
			Log.debug("MOB: loaded templates: "+mobTemplates.size());


    	for (Template tmpl: mobTemplates) {
        	ObjectManagerClient.registerTemplate(tmpl);
        	if (Log.loggingDebug)
          	  	Log.debug("MOB: loaded template: [" + tmpl.getName() + "]");
    	}
    	// Currencies
    	ArrayList<Currency> currencies = mDB.loadCurrencies(categoryID);
    	for (Currency currency: currencies) {
    		Agis.CurrencyManager.register(currency.getCurrencyID(), currency);
    		if (Log.loggingDebug)
    	    	  	Log.debug("MOB: loaded currency: [" + currency.getCurrencyName() + "]");
    	}
    	// Loot Tables
        HashMap<Integer, LootTable> lootTables = cDB.loadLootTables(-1);
        for (LootTable lTbl: lootTables.values()) {
        	Agis.LootTableManager.register(lTbl.getID(), lTbl);
        	if (Log.loggingDebug)
          	      Log.debug("LOOT: loaded loot Table: [" + lTbl.getName() + "]");
        }
        // Dialogues
		contentCategories.put(categoryID, cc);
		HashMap<Integer, Dialogue> dialoguesMap = mDB.loadDialogues();
		for (int dialogueID : dialoguesMap.keySet()) {
			Agis.DialogueManager.register(dialogueID, dialoguesMap.get(dialogueID));
			if (Log.loggingDebug)
				Log.debug("Dialogue: loaded dialogue: [" + dialoguesMap.get(dialogueID).getName() + "]");
		}
		//Load Pet Profiles
		HashMap<Integer, PetProfile> petprofiles = mDB.LoadPetProfiles();
		for (int pp: petprofiles.keySet()) {
			Agis.PetProfile.register(pp, petprofiles.get(pp));
			if (Log.loggingDebug)
				Log.debug("MOB: loaded PetProfile: [" + petprofiles.get(pp).getName() + "]");
		}

    	/**
         * Loads in the merchant tables from the Database.
         * @param iDB
         */
    	ItemDatabase iDB = new ItemDatabase(false);
    	HashMap<Integer, MerchantTable> merchantTables = iDB.loadMerchantTables();
		for (int tableID : merchantTables.keySet()) {
			Agis.MerchantTableManager.register(tableID, merchantTables.get(tableID));
			if (Log.loggingDebug)
				Log.debug("MerchantTables: loaded merchantTable: [" + merchantTables.get(tableID).getName() + "]");
		}
        Log.debug("NPC: merchant tables: " + merchantTables);
    }
    
	void loadData() {
		    cDB.loadEditorOptions();
	        CombatDatabase cbDB = new CombatDatabase(false);
		if(!Engine.isAIO()) {
			CombatPlugin.STAT_LIST = cbDB.LoadStats();
			CombatPlugin.STAT_PROFILES = cbDB.LoadStatProfiles();
		}

	        String bagCount = cDB.loadGameSetting("MOB_DEATH_EXP");
	        if (bagCount != null) {
	        	MOB_DEATH_EXP = Boolean.parseBoolean(bagCount);
	        	Log.debug("EXP: loaded setting MOB_DEATH_EXP set to: " + MOB_DEATH_EXP);
	        	    }
	        String expBasedOnDamageDealt = cDB.loadGameSetting("EXP_BASED_ON_DAMAGE_DEALT");
	        if (expBasedOnDamageDealt != null) {
				EXP_BASED_ON_DAMAGE_DEALT = Boolean.parseBoolean(expBasedOnDamageDealt);
				Log.debug("EXP: Exp based on damage dealt set to: " + EXP_BASED_ON_DAMAGE_DEALT);
			}
	        String lootBasedOnDamageDealt = cDB.loadGameSetting("LOOT_BASED_ON_DAMAGE_DEALT");
	        if (lootBasedOnDamageDealt != null) {
				LOOT_BASED_ON_DAMAGE_DEALT = Boolean.parseBoolean(lootBasedOnDamageDealt);
				Log.debug("LOOT: Loot based on damage dealt set to: " + LOOT_BASED_ON_DAMAGE_DEALT);
			}
	        String lootForAll = cDB.loadGameSetting("LOOT_FOR_ALL");
	        if (lootForAll != null) {
	        	LOOT_FOR_ALL = Boolean.parseBoolean(lootForAll);
				Log.debug("LOOT: LOOT_FOR_ALL set to: " + LOOT_FOR_ALL);
			}
	        
	        String mobSpawnDelay = cDB.loadGameSetting("MOB_SPAWN_DELAY");
	        if (mobSpawnDelay != null) {
	        	MOB_SPAWN_DELAY = Integer.parseInt(mobSpawnDelay);
				Log.debug("Game Setting MOB_SPAWN_DELAY set to: " + MOB_SPAWN_DELAY);
			}
	        
	        String mobSpawnTick = cDB.loadGameSetting("MOB_SPAWN_TICK");
	        if (mobSpawnTick != null) {
	        	MOB_SPAWN_TICK = Integer.parseInt(mobSpawnTick);
				Log.debug("Game Setting MOB_SPAWN_TICK set to: " + MOB_SPAWN_TICK);
			}


			String petDistanceDespawn = cDB.loadGameSetting("PET_DISTANCE_DESPAWN");
			if (petDistanceDespawn != null) {
				CombatPlugin.PET_DISTANCE_DESPAWN = Float.parseFloat(petDistanceDespawn);
			}

			String pet_allow_type_limit_exceeded = cDB.loadGameSetting("PET_ALLOW_TYPE_LIMIT_EXCEEDED");
			if (pet_allow_type_limit_exceeded != null) {
				PET_ALLOW_TYPE_LIMIT_EXCEEDED = Boolean.parseBoolean(pet_allow_type_limit_exceeded);
				log.debug("Game Setting Loaded PET_ALLOW_TYPE_LIMIT_EXCEEDED:"+PET_ALLOW_TYPE_LIMIT_EXCEEDED);
			}

			String petFollowRange = cDB.loadGameSetting("PET_FOLLOW_RANGE");
			if (petFollowRange != null) {
				PET_FOLLOW_RANGE = Float.parseFloat(petFollowRange);
				log.debug("Game Setting  Loaded PET_FOLLOW_RANGE:"+PET_FOLLOW_RANGE);
			}

			String petSpawnRange = cDB.loadGameSetting("PET_SPAWN_RANGE");
			if (petSpawnRange != null) {
				PET_SPAWN_RANGE = Float.parseFloat(petSpawnRange);
				log.debug("Game Setting  Loaded PET_SPAWN_RANGE:"+PET_SPAWN_RANGE);
			}

			String combatPetSpeedFromOwner = cDB.loadGameSetting("COMBAT_PET_SPEED_FROM_OWNER");
			if (combatPetSpeedFromOwner != null) {
				COMBAT_PET_SPEED_FROM_OWNER = Boolean.parseBoolean(combatPetSpeedFromOwner);
				log.debug("Game Setting  Loaded COMBAT_PET_SPEED_FROM_OWNER:"+COMBAT_PET_SPEED_FROM_OWNER);
			}
			String combatPetSpeedMod = cDB.loadGameSetting("COMBAT_PET_SPEED_MOD");
			if (combatPetSpeedMod != null) {
				COMBAT_PET_SPEED_MOD = Float.parseFloat(combatPetSpeedMod);
				log.debug("Game Setting  Loaded COMBAT_PET_SPEED_MOD:"+COMBAT_PET_SPEED_MOD);
			}

			String mobForceDespawnInCombat = cDB.loadGameSetting("MOB_FORCE_DESPAWN_IN_COMBAT");
			if (mobForceDespawnInCombat != null) {
				MOB_FORCE_DESPAWN_IN_COMBAT = Boolean.parseBoolean(mobForceDespawnInCombat);
				log.debug("Game Setting  Loaded MOB_FORCE_DESPAWN_IN_COMBAT:" + MOB_FORCE_DESPAWN_IN_COMBAT);
			}

			String dynamicNavmeshInterval = cDB.loadGameSetting("DYNAMIC_NAVMESH_UPDATE_INTERVAL");
			if (dynamicNavmeshInterval != null) {
				DYNAMIC_NAVMESH_UPDATE_INTERVAL = Integer.parseInt(dynamicNavmeshInterval);
				Log.debug("Game Setting DYNAMIC_NAVMESH_UPDATE_INTERVAL set to: " + DYNAMIC_NAVMESH_UPDATE_INTERVAL);
			}

			String dynamicNavmeshSave = cDB.loadGameSetting("DYNAMIC_NAVMESH_UPDATE_SAVE");
			if (dynamicNavmeshSave != null) {
				DYNAMIC_NAVMESH_UPDATE_SAVE = Boolean.parseBoolean(dynamicNavmeshSave);
				Log.debug("Game Setting DYNAMIC_NAVMESH_UPDATE_SAVE set to: " + DYNAMIC_NAVMESH_UPDATE_SAVE);
			}
			String devMode = cDB.loadGameSetting("SERVER_DEVELOPMENT_MODE");
			if (devMode != null) {
				EnginePlugin.DevMode = Boolean.parseBoolean(devMode);
				log.info("Game Settings EnginePlugin.DevMode set to " + EnginePlugin.DevMode);
			}
			
			String bavariorInterval = cDB.loadGameSetting("MOB_COMBAT_BEHAVIOR_SELECT_INTERVAL");
			if (bavariorInterval != null) {
				MOB_COMBAT_BEHAVIOR_SELECT_INTERVAL = Long.parseLong(bavariorInterval);
				Log.debug("Game Setting MOB_COMBAT_BEHAVIOR_SELECT_INTERVAL set to: " + MOB_COMBAT_BEHAVIOR_SELECT_INTERVAL);
			}
			
			String abilityTimeOut = cDB.loadGameSetting("MOB_COMBAT_BEHAVIOR_USE_ABILITY_TIMEOUT");
			if (abilityTimeOut != null) {
				MOB_COMBAT_BEHAVIOR_USE_ABILITY_TIMEOUT = Long.parseLong(abilityTimeOut);
				Log.debug("Game Setting MOB_COMBAT_BEHAVIOR_USE_ABILITY_TIMEOUT set to: " + MOB_COMBAT_BEHAVIOR_USE_ABILITY_TIMEOUT);
			}
			
			String moveCheckInterval = cDB.loadGameSetting("MOB_COMBAT_BEHAVIOR_MOVE_CHECK_INTERVAL");
			if (moveCheckInterval != null) {
				MOB_COMBAT_BEHAVIOR_MOVE_CHECK_INTERVAL = Long.parseLong(moveCheckInterval);
				Log.debug("Game Setting MOB_COMBAT_BEHAVIOR_MOVE_CHECK_INTERVAL set to: " + MOB_COMBAT_BEHAVIOR_MOVE_CHECK_INTERVAL);
			}
			String changeTargetTimeout = cDB.loadGameSetting("MOB_COMBAT_BEHAVIOR_DEFEND_CHANGE_TARGET_INTERVAL");
			if (changeTargetTimeout != null) {
				MOB_COMBAT_BEHAVIOR_DEFEND_CHANGE_TARGET_INTERVAL = Long.parseLong(changeTargetTimeout);
				Log.debug("Game Setting MOB_COMBAT_BEHAVIOR_DEFEND_CHANGE_TARGET_INTERVAL set to: " + MOB_COMBAT_BEHAVIOR_DEFEND_CHANGE_TARGET_INTERVAL);
			}

			String changeFleeAlliesMaxDist = cDB.loadGameSetting("MOB_COMBAT_BEHAVIOR_FLEE_ALLIES_CHECK_MAX_DISTANCE");
			if (changeFleeAlliesMaxDist != null) {
				MOB_COMBAT_BEHAVIOR_FLEE_ALLIES_CHECK_MAX_DISTANCE = Float.parseFloat(changeFleeAlliesMaxDist);
				Log.debug("Game Setting MOB_COMBAT_BEHAVIOR_FLEE_ALLIES_CHECK_MAX_DISTANCE set to: " + MOB_COMBAT_BEHAVIOR_FLEE_ALLIES_CHECK_MAX_DISTANCE);
			}

			String behaviorEventTimeout = cDB.loadGameSetting("MOB_COMBAT_BEHAVIOR_EVENT_TIMEOUT");
			if (behaviorEventTimeout != null) {
				MOB_COMBAT_BEHAVIOR_EVENT_TIMEOUT = Long.parseLong(behaviorEventTimeout);
				Log.debug("Game Setting MOB_COMBAT_BEHAVIOR_EVENT_TIMEOUT set to: " + MOB_COMBAT_BEHAVIOR_EVENT_TIMEOUT);
			}

			String behaviorNumberTargetsMaxDistance = cDB.loadGameSetting("MOB_COMBAT_BEHAVIOR_NUMBER_TARGETS_CHECK_MAX_DISTANCE");
			if (behaviorNumberTargetsMaxDistance != null) {
				MOB_COMBAT_BEHAVIOR_NUMBER_TARGETS_CHECK_MAX_DISTANCE = Float.parseFloat(behaviorNumberTargetsMaxDistance);
				Log.debug("Game Setting MOB_COMBAT_BEHAVIOR_NUMBER_TARGETS_CHECK_MAX_DISTANCE set to: " + MOB_COMBAT_BEHAVIOR_NUMBER_TARGETS_CHECK_MAX_DISTANCE);
			}

			
			
			String mob_aggro_thread = cDB.loadGameSetting("MOB_AGGRO_RANGED_THREAT_THRESHOLD");
			if (mob_aggro_thread != null) {
				MOB_AGGRO_RANGED_THREAT_THRESHOLD = Float.parseFloat(mob_aggro_thread);
				Log.debug("Game Setting MOB_AGGRO_RANGED_THREAT_THRESHOLD set to: " + MOB_AGGRO_RANGED_THREAT_THRESHOLD);
			}
			String mob_aggro_close_range = cDB.loadGameSetting("MOB_AGGRO_CLOSE_RANGE_CHECK ");
			if (mob_aggro_close_range != null) {
				MOB_AGGRO_CLOSE_RANGE_CHECK  = Float.parseFloat(mob_aggro_close_range);
				Log.debug("Game Setting MOB_AGGRO_CLOSE_RANGE_CHECK  set to: " + MOB_AGGRO_CLOSE_RANGE_CHECK );
			}
			String mob_aggro_melee = cDB.loadGameSetting("MOB_AGGRO_MELEE_THREAT_THRESHOLD ");
			if (mob_aggro_melee != null) {
				MOB_AGGRO_MELEE_THREAT_THRESHOLD  =  Float.parseFloat(mob_aggro_melee);
				Log.debug("Game Setting MOB_AGGRO_MELEE_THREAT_THRESHOLD  set to: " + MOB_AGGRO_MELEE_THREAT_THRESHOLD );
			}

		String interactiveObjectPerceptionRadius = cDB.loadGameSetting("INTERACTIVE_OBJECT_PERCEPTION_RADIUS");
		if (interactiveObjectPerceptionRadius != null) {
			INTERACTIVE_OBJECT_PERCEPTION_RADIUS = Integer.parseInt(interactiveObjectPerceptionRadius);
			Log.debug("Game Setting INTERACTIVE_OBJECT_PERCEPTION_RADIUS set to: " + INTERACTIVE_OBJECT_PERCEPTION_RADIUS);
		}


		//Interactive Object Settings
		interactiveObjectProfiles = cDB.loadInteractiveObjectProfiles();


		loadCategoryContent(mobDataBase, cDB, BASE_CATEGORY);
		 loadCategoryContent(mobDataBase, cDB, 1);
		 

		// Load effects
			ArrayList<AgisEffect> effects = cbDB.loadCombatEffects();
			for (AgisEffect effect : effects)
				Agis.EffectManager.register(effect.getID(), effect);
			
			ArrayList<AgisAbility> abilities = cbDB.loadAbilities();
			for (AgisAbility ability : abilities) {
				Agis.AbilityManager.register(ability.getID(), ability);
				Log.debug("ABILITY: added " + ability.getName() + " to the database.");
			}
			
		
				// Load Damage Types
				CombatPlugin.DAMAGE_TYPES = cbDB.LoadDamageTypes();
				CombatPlugin.STAT_THRESHOLDS = cbDB.LoadStatThresholds();
				
				
				
			
		

	}

	protected void ReloadTemplates(Message msg) {
		if (Log.loggingDebug)Log.debug("AgisMobPlugin ReloadTemplates Start");
			loadData();
			//reload merchant
			
			// Reload Mobs behaviors
		Entity[] entities = EntityManager.getAllEntitiesByNamespace(Namespace.MOB);
		for (Entity e : entities) {
			if (e != null) {
				ObjectStub os = (ObjectStub) e;
				if (os != null) {
					for (Behavior behav : os.getBehaviors()) {
						if (Log.loggingDebug)Log.debug("ReloadTemplates: oid " + e.getOid() + " behav " + behav);
						if (behav instanceof CombatBehavior) {
							CombatBehavior cBehav = ((CombatBehavior) behav);

							if (Log.loggingDebug)Log.debug("ReloadTemplates: oid " + e.getOid() + " tmplId=" + os.getTemplateID());
							if (os.getTemplateID() > 0) {
								Template tmpl = ObjectManagerClient.getTemplate(os.getTemplateID(), ObjectManagerPlugin.MOB_TEMPLATE);
								if (Log.loggingDebug)
									Log.debug("ReloadTemplates load  templateID=" + os.getTemplateID() + " Template=" + tmpl);

								HashMap<Integer, MobLootTable> lootTables = (HashMap<Integer, MobLootTable>) tmpl.get(InventoryClient.NAMESPACE, "lootTables");
								if (lootTables == null)
									lootTables = new HashMap<>();
								cBehav.setLootTables(lootTables);
								cBehav.setAggroRange( (int)tmpl.get(Namespace.FACTION, FactionStateInfo.AGGRO_RADIUS));
								cBehav.setSendLinkedAggro( (boolean)tmpl.get(CombatClient.NAMESPACE, Behavior.LINKED_AGGRO_SEND));
								cBehav.setLinkedAggroRadius( (int)tmpl.get(CombatClient.NAMESPACE, Behavior.LINKED_AGGRO_RADIUS));
								cBehav.setReciveLinkedAggro( (boolean)tmpl.get(CombatClient.NAMESPACE, Behavior.LINKED_AGGRO_GET));
								cBehav.setchaseDistance( (int)tmpl.get(CombatClient.NAMESPACE, Behavior.LINKED_CHASING_DISTANCE));
								
								
								String tags = (String) tmpl.get(CombatClient.NAMESPACE, "tags");
								cBehav.setTags(tags);

								int behavProfile = (int) tmpl.get(CombatClient.NAMESPACE, "behavProfile");
								cBehav.behaviorId = behavProfile;
								cBehav.behaviors.clear();
								if (mobCombatBehaviorProfiles.containsKey(behavProfile)) {

									MobBehaviorProfile mbp = mobCombatBehaviorProfiles.get(behavProfile);
									if (Log.loggingDebug)Log.debug("ReloadTemplates Set mob behavior profile "+mbp);
									for (MobBehavior mb : mbp.behaviors) {
										if (Log.loggingDebug)Log.debug("ReloadTemplates Set mob behavior "+mb);
										CombatBehavior.CombatBehaviorEntry cb = cBehav.new CombatBehaviorEntry();
										cb.behaviorType = mb.getType();
										cb.fleeType = mb.getFleeType();
										cb.fleepoint = mb.getFleePoint();
										cb.fleepoints = mb.getFleePoints();
										cb.mobTag = mb.getMobTag();
										cb.weapon = mb.getWeapon();
										cb.abilityinterval = mb.getAbilityInterval();
										cb.ignoreChaceDistance = mb.getIgnoreChaseDistance();
										// Set Conditions
										for (BehaviorConditionGroupSettings gbcs : mb.conditionsGroup) {
											CombatBehavior.GroupBehaviorConditions gbc = cBehav.new GroupBehaviorConditions();
											List<String> confitionList = new ArrayList<String>();
											for (BehaviorConditionSettings bcs : gbcs.conditions) {

												switch (bcs.type) {
												case 0:
													CombatBehavior.EventBehaviorCondition ebc = cBehav.new EventBehaviorCondition();
													ebc.eventId = bcs.triggerEvent;
													ebc.target = bcs.target;
													gbc.conditions.add(ebc);
													break;
												case 1:
													CombatBehavior.BehaviorCondition bc = cBehav.new BehaviorCondition();
													bc.distance = bcs.distance;
													bc.less = bcs.less;
													gbc.conditions.add(bc);
													break;
												case 2:
													CombatBehavior.StatBehaviorCondition sbc = cBehav.new StatBehaviorCondition();
													sbc.statName = bcs.statName;
													sbc.statValue = bcs.statValue;
													sbc.vitalityPercentage = bcs.statVitalityPercentage;
													sbc.target = bcs.target;
													sbc.less = bcs.less;
													gbc.conditions.add(sbc);
													break;
												case 3:
													CombatBehavior.EffectBehaviorCondition efbc = cBehav.new EffectBehaviorCondition();
													efbc.tagId = bcs.effectTag;
													efbc.target = bcs.target;
													efbc.onTarget = bcs.onTarget;
													gbc.conditions.add(efbc);
													break;
												case 4:
													CombatBehavior.TargetCombatStateBehaviorCondition tcsbc = cBehav.new TargetCombatStateBehaviorCondition();
													tcsbc.combatState = bcs.combatState;
													gbc.conditions.add(tcsbc);
													break;
												case 5:
													CombatBehavior.TargetDeathStateBehaviorCondition tdsbc = cBehav.new TargetDeathStateBehaviorCondition();
													tdsbc.deathState = bcs.deathState;
													gbc.conditions.add(tdsbc);
													break;
												case 6:
													CombatBehavior.NumberOfTargetsStateBehaviorCondition notsbc = cBehav.new NumberOfTargetsStateBehaviorCondition();
													notsbc.number = bcs.target_number;
													notsbc.allay = bcs.target_ally;
													notsbc.less = bcs.less;
													gbc.conditions.add(notsbc);
													break;
												}
											}
											cb.conditions.add(gbc);
										}

										// load abilities
										for (MobAbility ma : mb.abilities) {
											CombatBehavior.AbilityGroup am = cBehav.new AbilityGroup();
											String abi = ma.abilities;
											String[] abis = abi.split(";");
											int count = abis.length;
											if (count > 1) {
												int sumPriority=0;
												for (int i = 0; i < count / 2; i++) {
													if (abis[i * 2].length() > 0) {
														int id = Integer.parseInt(abis[i * 2]);
														int c = Integer.parseInt(abis[i * 2 + 1]);
														am.priority.put(id,c);
														sumPriority+=c;
														//for (int j = 0; j < c; j++) {
															am.ids.add(id);
															
														//}
													}
												}
												am.sumPriority = sumPriority;
											}
											am.minAbilityRangePercentage = ma.minAbilityRangePercentage;
											am.maxAbilityRangePercentage = ma.maxAbilityRangePercentage;

											// Set Conditions for ability
											for (BehaviorConditionGroupSettings gbcs : ma.conditionsGroup) {
												CombatBehavior.GroupBehaviorConditions gbc = cBehav.new GroupBehaviorConditions();
												List<String> confitionList = new ArrayList<String>();
												for (BehaviorConditionSettings bcs : gbcs.conditions) {

													switch (bcs.type) {
													case 0:
														CombatBehavior.EventBehaviorCondition ebc = cBehav.new EventBehaviorCondition();
														ebc.eventId = bcs.triggerEvent;
														ebc.target = bcs.target;
														gbc.conditions.add(ebc);
														break;
													case 1:
														CombatBehavior.BehaviorCondition bc = cBehav.new BehaviorCondition();
														bc.distance = bcs.distance;
														bc.less = bcs.less;
														gbc.conditions.add(bc);
														break;
													case 2:
														CombatBehavior.StatBehaviorCondition sbc = cBehav.new StatBehaviorCondition();
														sbc.statName = bcs.statName;
														sbc.statValue = bcs.statValue;
														sbc.vitalityPercentage = bcs.statVitalityPercentage;
														sbc.target = bcs.target;
														sbc.less = bcs.less;
														gbc.conditions.add(sbc);
														break;
													case 3:
														CombatBehavior.EffectBehaviorCondition efbc = cBehav.new EffectBehaviorCondition();
														efbc.tagId = bcs.effectTag;
														efbc.target = bcs.target;
														efbc.onTarget = bcs.onTarget;
														gbc.conditions.add(efbc);
														break;
													case 4:
														CombatBehavior.TargetCombatStateBehaviorCondition tcsbc = cBehav.new TargetCombatStateBehaviorCondition();
														tcsbc.combatState = bcs.combatState;
														gbc.conditions.add(tcsbc);
														break;
													case 5:
														CombatBehavior.TargetDeathStateBehaviorCondition tdsbc = cBehav.new TargetDeathStateBehaviorCondition();
														tdsbc.deathState = bcs.deathState;
														gbc.conditions.add(tdsbc);
														break;
													case 6:
														CombatBehavior.NumberOfTargetsStateBehaviorCondition notsbc = cBehav.new NumberOfTargetsStateBehaviorCondition();
														notsbc.number = bcs.target_number;
														notsbc.allay = bcs.target_ally;
														notsbc.less = bcs.less;
														gbc.conditions.add(notsbc);
														break;
													}
												}
												am.conditions.add(gbc);
											}
											cb.abilities.add(am);
										}

										// load abilities
										for (MobAbility ma : mb.startAbilities) {
											CombatBehavior.AbilityGroup am = cBehav.new AbilityGroup();
											String abi = ma.abilities;
											String[] abis = abi.split(";");
											int count = abis.length;
											if (count > 1) {
												int sumPriority=0;
												for (int i = 0; i < count / 2; i++) {
													if (abis[i * 2].length() > 0) {
														int id = Integer.parseInt(abis[i * 2]);
														int c = Integer.parseInt(abis[i * 2 + 1]);
														am.priority.put(id,c);
														sumPriority+=c;
														//for (int j = 0; j < c; j++) {
															am.ids.add(id);
															
														//}
													}
												}
												am.sumPriority = sumPriority;
											}
											am.minAbilityRangePercentage = ma.minAbilityRangePercentage;
											am.maxAbilityRangePercentage = ma.maxAbilityRangePercentage;

											// Set Conditions for ability
											for (BehaviorConditionGroupSettings gbcs : ma.conditionsGroup) {
												CombatBehavior.GroupBehaviorConditions gbc = cBehav.new GroupBehaviorConditions();
												List<String> confitionList = new ArrayList<String>();
												for (BehaviorConditionSettings bcs : gbcs.conditions) {

													switch (bcs.type) {
													case 0:
														CombatBehavior.EventBehaviorCondition ebc = cBehav.new EventBehaviorCondition();
														ebc.eventId = bcs.triggerEvent;
														ebc.target = bcs.target;
														gbc.conditions.add(ebc);
														break;
													case 1:
														CombatBehavior.BehaviorCondition bc = cBehav.new BehaviorCondition();
														bc.distance = bcs.distance;
														bc.less = bcs.less;
														gbc.conditions.add(bc);
														break;
													case 2:
														CombatBehavior.StatBehaviorCondition sbc = cBehav.new StatBehaviorCondition();
														sbc.statName = bcs.statName;
														sbc.statValue = bcs.statValue;
														sbc.vitalityPercentage = bcs.statVitalityPercentage;
														sbc.target = bcs.target;
														sbc.less = bcs.less;
														gbc.conditions.add(sbc);
														break;
													case 3:
														CombatBehavior.EffectBehaviorCondition efbc = cBehav.new EffectBehaviorCondition();
														efbc.tagId = bcs.effectTag;
														efbc.target = bcs.target;
														efbc.onTarget = bcs.onTarget;
														gbc.conditions.add(efbc);
														break;
													case 4:
														CombatBehavior.TargetCombatStateBehaviorCondition tcsbc = cBehav.new TargetCombatStateBehaviorCondition();
														tcsbc.combatState = bcs.combatState;
														gbc.conditions.add(tcsbc);
														break;
													case 5:
														CombatBehavior.TargetDeathStateBehaviorCondition tdsbc = cBehav.new TargetDeathStateBehaviorCondition();
														tdsbc.deathState = bcs.deathState;
														gbc.conditions.add(tdsbc);
														break;
													case 6:
														CombatBehavior.NumberOfTargetsStateBehaviorCondition notsbc = cBehav.new NumberOfTargetsStateBehaviorCondition();
														notsbc.number = bcs.target_number;
														notsbc.allay = bcs.target_ally;
														notsbc.less = bcs.less;
														gbc.conditions.add(notsbc);
														break;
													}
												}
												am.conditions.add(gbc);
											}
											cb.startAbilities.add(am);
										}
										// load abilities
										for (MobAbility ma : mb.endAbilities) {
											CombatBehavior.AbilityGroup am = cBehav.new AbilityGroup();
											String abi = ma.abilities;
											String[] abis = abi.split(";");
											int count = abis.length;
											if (count > 1) {
												int sumPriority=0;
												for (int i = 0; i < count / 2; i++) {
													if (abis[i * 2].length() > 0) {
														int id = Integer.parseInt(abis[i * 2]);
														int c = Integer.parseInt(abis[i * 2 + 1]);
														am.priority.put(id,c);
														sumPriority+=c;
														//for (int j = 0; j < c; j++) {
															am.ids.add(id);
															
														//}
													}
												}
												am.sumPriority = sumPriority;
											}
											
											am.minAbilityRangePercentage = ma.minAbilityRangePercentage;
											am.maxAbilityRangePercentage = ma.maxAbilityRangePercentage;

											// Set Conditions for ability
											for (BehaviorConditionGroupSettings gbcs : ma.conditionsGroup) {
												CombatBehavior.GroupBehaviorConditions gbc = cBehav.new GroupBehaviorConditions();
												List<String> confitionList = new ArrayList<String>();
												for (BehaviorConditionSettings bcs : gbcs.conditions) {

													switch (bcs.type) {
													case 0:
														CombatBehavior.EventBehaviorCondition ebc = cBehav.new EventBehaviorCondition();
														ebc.eventId = bcs.triggerEvent;
														ebc.target = bcs.target;
														gbc.conditions.add(ebc);
														break;
													case 1:
														CombatBehavior.BehaviorCondition bc = cBehav.new BehaviorCondition();
														bc.distance = bcs.distance;
														bc.less = bcs.less;
														gbc.conditions.add(bc);
														break;
													case 2:
														CombatBehavior.StatBehaviorCondition sbc = cBehav.new StatBehaviorCondition();
														sbc.statName = bcs.statName;
														sbc.statValue = bcs.statValue;
														sbc.vitalityPercentage = bcs.statVitalityPercentage;
														sbc.target = bcs.target;
														sbc.less = bcs.less;
														gbc.conditions.add(sbc);
														break;
													case 3:
														CombatBehavior.EffectBehaviorCondition efbc = cBehav.new EffectBehaviorCondition();
														efbc.tagId = bcs.effectTag;
														efbc.target = bcs.target;
														efbc.onTarget = bcs.onTarget;
														gbc.conditions.add(efbc);
														break;
													case 4:
														CombatBehavior.TargetCombatStateBehaviorCondition tcsbc = cBehav.new TargetCombatStateBehaviorCondition();
														tcsbc.combatState = bcs.combatState;
														gbc.conditions.add(tcsbc);
														break;
													case 5:
														CombatBehavior.TargetDeathStateBehaviorCondition tdsbc = cBehav.new TargetDeathStateBehaviorCondition();
														tdsbc.deathState = bcs.deathState;
														gbc.conditions.add(tdsbc);
														break;
													case 6:
														CombatBehavior.NumberOfTargetsStateBehaviorCondition notsbc = cBehav.new NumberOfTargetsStateBehaviorCondition();
														notsbc.number = bcs.target_number;
														notsbc.allay = bcs.target_ally;
														notsbc.less = bcs.less;
														gbc.conditions.add(notsbc);
														break;
													}
												}
												am.conditions.add(gbc);
											}
											cb.endAbilities.add(am);
										}

										cBehav.behaviors.add(cb);
									}

								}
							}
							
							cBehav.behaviorSheduler();
							
							} else {
								if (Log.loggingDebug)Log.debug("ReloadTemplates: oid " + e.getOid() + " no combat behavior ");
							}
							
							
							
						

					}

				}

			}
		}
		if (Log.loggingDebug)Log.debug("AgisMobPlugin ReloadTemplates End");
	}
    
    
    
	private void createFactories() {
	    Log.debug("BEHAV: creating factory for Dot");

	    // Create MobFactory
	    MobFactory cFactory = new MobFactory(500);

	    // Add BaseBehavior using a supplier
	    cFactory.addBehavSupplier((obj, spawnData) -> new BaseBehavior());

	    // Add DotBehavior using a supplier
	    cFactory.addBehavSupplier((obj, spawnData) -> {
	        DotBehavior behav = new DotBehavior();
	        behav.setRadius(1500);
	        return behav;
	    });

	    String factoryName = "DotFactory";
	    Log.debug("BEHAV: registering factory for Dot");
	    ObjectFactory.register(factoryName, cFactory);
	}

    
    private void loadBuildingGrids(MobDatabase mDB, String instanceName, OID instanceOID) {
    	
    	resourceGrids.putAll(mDB.loadResourceGrids(instanceName));
    	for (ResourceGrid grid : resourceGrids.values()) {
    		if (grid.getCount() > 0 && grid.getInstance().equals(instanceName)) {
    			grid.spawnResource(instanceOID);
    		}
    	}
    }


	class SetDebugPlayerHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID oid = message.getSubject();
			Serializable _mode = message.getProperty("mode");
			if (Log.loggingDebug) Log.error("SetDebugPlayerHook: playerOid=" + oid + " _mode=" + _mode);
			int mode = 1;
			if (_mode != null)
				mode = (int) _mode;
			if (mode == 0) {
				MOB_DEBUG_PLAYER = null;
				ExtendedCombatMessages.sendAnouncementMessage(oid, "Combat Mob Debugging is Off", "");
			}
			if (mode == 1) {
				MOB_DEBUG_PLAYER = oid;
				ExtendedCombatMessages.sendAnouncementMessage(oid, "Combat Mob Debugging is On", "");
			}

			if (Log.loggingDebug)
				log.debug("AgisMobPlugin.SetDebugPlayerHook: obj: " + oid + " end");
			return true;
		}
	}




	/**
     * Save Stance for objects 
     *
     */
	class TargetTypeUpdateHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			CombatClient.TargetTypeMessage ttMsg = (CombatClient.TargetTypeMessage) msg;
			LinkedList<TargetType> _targetTypes = ttMsg.getTargetTypes();
			 //if(Log.loggingDebug)
			if(Log.loggingDebug)Log.debug("TargetTypeUpdateHook: got target type update.");
			// _targetTypes: " + _targetTypes );

			if (_targetTypes == null) {

			} else {
				// if(Log.loggingDebug) Log.error("TargetTypeUpdateHook: got target type update.
				// _targetTypes: " + _targetTypes );

				for (int i = 0; i < _targetTypes.size(); i++) {
					if(Log.loggingDebug)Log.debug("TargetTypeUpdateHook, subject="+_targetTypes.get(i).getSubjectOid()+" target="+_targetTypes.get(i).getTargetOid()+" type="+_targetTypes.get(i).getTargetType());

					if(!objectsTargetType.containsKey(_targetTypes.get(i).getSubjectOid()) && _targetTypes.get(i).getTargetType().equals(FactionPlugin.Deleted)){
						if(Log.loggingDebug)Log.debug("TargetTypeUpdateHook, subject="+_targetTypes.get(i).getSubjectOid()+" target="+_targetTypes.get(i).getTargetOid()+" type="+_targetTypes.get(i).getTargetType()+" skip");
					} else {
					Map obj= objectsTargetType.computeIfAbsent(_targetTypes.get(i).getSubjectOid(), new Function<OID, Map<OID, Integer>>() {
							@Override
							public Map<OID, Integer> apply(OID t) {
								return new ConcurrentHashMap<>();
							}
						});
						if(_targetTypes.get(i).getTargetType().equals(FactionPlugin.Deleted)) {
							if(Log.loggingDebug)Log.debug("TargetTypeUpdateHook, subject="+_targetTypes.get(i).getSubjectOid()+" dell");
							obj.remove(_targetTypes.get(i).getTargetOid());
						} else {
							obj.put(_targetTypes.get(i).getTargetOid(), _targetTypes.get(i).getTargetType());
							if(Log.loggingDebug)Log.debug("TargetTypeUpdateHook, subject="+_targetTypes.get(i).getSubjectOid()+" add");

						}
						if(Log.loggingDebug)Log.debug("TargetTypeUpdateHook, subject="+_targetTypes.get(i).getSubjectOid()+" target="+_targetTypes.get(i).getTargetOid()+" type="+_targetTypes.get(i).getTargetType()+" objectsTargetType="+obj.size());
						if(obj.size()==0){
							objectsTargetType.remove(_targetTypes.get(i).getSubjectOid());
							if(Log.loggingDebug)Log.debug("TargetTypeUpdateHook,dell subject="+_targetTypes.get(i).getSubjectOid());
						}
					}
				}

			}
			Gauge.builder("objectsTargetType", () -> objectsTargetType.size()).register(Prometheus.registry());
			if(Log.loggingDebug)Log.debug("TARGET: got target type update. END");
			// Log.error("TargetTypeUpdateHook: End");
			return true;
		}
	}

	/*
	 * Get List of objects OID that are friendly  
	 */
    
	public static List<OID> getFriendly(OID oid){
		Map<OID,Integer> objects = objectsTargetType.get(oid);
		List<OID> list = new ArrayList<OID>();
		if(objects!=null)
		for (Map.Entry<OID, Integer> entry : objects.entrySet()) {
			if(entry.getValue() == FactionPlugin.Healable) {
				list.add(entry.getKey());
			}
		}
		return list;
	}
/*
 * Get List of objects OID that are enemy  
 */
	public static List<OID> getEnemy(OID oid){
		Map<OID,Integer> objects = objectsTargetType.get(oid);
		List<OID> list = new ArrayList<OID>();
		for (Map.Entry<OID, Integer> entry : objects.entrySet()) {
			if(entry.getValue() == FactionPlugin.Attackable) {
				list.add(entry.getKey());
			}
		}
		return list;
	}

	
	public static Map<OID, Map<OID,Integer>> objectsTargetType = new ConcurrentHashMap<>();



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
     * Hook recive list of clime objects to add or delete from dynamic NavMesh
     * 
     */
	class AddDynamicNMObjectHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	AddDynamicObjectMessage nmMsg = (AddDynamicObjectMessage)msg;
            log.debug("AddDynamicNMObjectHook InstanceNavMeshManager Start");
            OID instanceOid = nmMsg.getSubject();
            List<AtavismBuildingObject> object = nmMsg.getObobjectsToAdd();
            List<Integer> dellObject = nmMsg.getObjectToDestroy();
            InstanceNavMeshManager inmm = instanceNavMeshes.get(instanceOid);
			if (inmm != null) {
				log.debug("AddNMObjectHook InstanceNavMeshManager ");
				if (dellObject != null && dellObject.size() > 0) {
					inmm.removeDynamicObject(dellObject);
				}
				if (object != null && object.size() > 0) {
					inmm.addDynamicObject(object);
				}
			} else {
				log.debug("AddNMObjectHook InstanceNavMeshManager is null for instanceOid=" + instanceOid);
			}
			return true;
		}
	}

	/**
	 * Hook to send collider definitions that was added to dynamic navmesh
	 */
	class GetNMObjectsHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	ExtensionMessage nmMsg = (ExtensionMessage)msg;
			if(Log.loggingDebug)log.debug("GetNMObjectHook InstanceNavMeshManager Start "+nmMsg.getSubject());
            OID playerOid = nmMsg.getSubject();
            OID instanceOid = WorldManagerClient.getObjectInfo(playerOid).instanceOid; 
            InstanceNavMeshManager inmm = instanceNavMeshes.get(instanceOid);
			if (inmm != null) {
					inmm.getDynamicObject(playerOid);
			} else {
				if(Log.loggingDebug)log.debug("GetNMObjectHook InstanceNavMeshManager is null for instanceOid=" + instanceOid);
			}
			return true;
		}
	}

	/**
	 * Hook to set player OID to send debug data for dynamic navmesh (like check line of sight)
	 */
	class SetDebugNavMeshHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage nmMsg = (ExtensionMessage) msg;
			log.debug("SetDebugNavMeshHook InstanceNavMeshManager Start");
			OID playerOid = nmMsg.getSubject();
			OID instanceOid = WorldManagerClient.getObjectInfo(playerOid).instanceOid;
			Serializable _mode = nmMsg.getProperty("mode");
			if(Log.loggingDebug)Log.error("SetDebugNavMeshHook: playerOid="+playerOid+" instanceOid="+instanceOid+" _mode="+_mode);
			int mode = 1;
			if (_mode != null)
				mode = (int) _mode;
			if(Log.loggingDebug)log.debug("SetDebugNavMeshHook InstanceNavMeshManager instanceOid=" + instanceOid);
			InstanceNavMeshManager inmm = instanceNavMeshes.get(instanceOid);
			if (inmm != null) {
				if (mode == 0) {
					inmm.setDebugPlayer(null);
					ExtendedCombatMessages.sendAnouncementMessage(playerOid, "NavMesh Debugging is Off", "");
				}
				if (mode == 1){
					inmm.setDebugPlayer(playerOid);
					ExtendedCombatMessages.sendAnouncementMessage(playerOid, "NavMesh Debugging is On", "");
				}
			} else {
				if(Log.loggingDebug)log.debug("SetDebugNavMeshHook InstanceNavMeshManager is null for instanceOid=" + instanceOid);
			}
			return true;
		}
	}

	/**
	 * spawn interactive object for an instance
	 */
	class SpawnInteractiveObjectHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			AgisMobClient.SpawnInteractiveObjectMessage SPMsg = (AgisMobClient.SpawnInteractiveObjectMessage) msg;

			if(Log.loggingDebug)
				log.debug("SpawnInteractiveObjectHook instances="+instances+" check "+SPMsg.instanceOid);

			if(instances.contains(SPMsg.instanceOid)) {
				if(Log.loggingDebug)
					log.debug("SpawnInteractiveObjectHook schedule spawn for instance "+SPMsg.instanceOid);

				int templateId = SPMsg.getInteractiveObjectTemplate();
				if(templateId>0) {
					AOVector loc = SPMsg.getPosition();
					OID instanceOid = SPMsg.getInstanceOid();
					int id = interactiveObjectInstanceMaxId.computeIfAbsent(instanceOid, __ -> new AtomicInteger()).incrementAndGet();
					InteractiveObject obj = new InteractiveObject(id, loc, instanceOid);

					InteractiveObject profile = AgisMobPlugin.interactiveObjectProfiles.get(templateId);
					if (profile != null) {
						obj.setName(profile.getName());
						obj.setDynamicObject(true);
						obj.setProfileId(templateId);
						obj.setGameObject(profile.getGameObject());
						obj.setCoordEffect(profile.getCoordEffect());
						obj.setQuestIDReq(profile.getQuestIDReq());
						obj.setInteractionType(profile.getInteractionType());
						obj.setInteractionID(profile.getInteractionID());
						obj.setInteractionData1(profile.getInteractionData1());
						obj.setInteractionData2(profile.getInteractionData2());
						obj.setInteractionData3(profile.getInteractionData3());
						obj.setRespawnTime(profile.getRespawnTime());
						obj.setHarvestTimeReq(profile.getHarvestTimeReq());
						obj.setInteractionDistance(profile.getInteractionDistance());

						obj.setDespawnDelay(profile.getDespawnDelay());
						obj.setDespawnTime(profile.getDespawnTime());
						obj.setMakeBusy(profile.getMakeBusy());
						obj.setUseLimit(profile.getUseLimit());
						obj.setMaxLevel(profile.getMaxLevel());
						obj.setMinLevel(profile.getMinLevel());
						obj.setItemReq(profile.getItemReq());
						obj.setItemReqGet(profile.getItemReqGet());
						obj.setItemCountReq(profile.getItemCountReq());
						obj.setCurrencyReq(profile.getCurrencyReq());
						obj.setCurrencyReqGet(profile.getCurrencyReqGet());
						obj.setCurrencyCountReq(profile.getCurrencyCountReq());
						obj.setCoordinatedEffectsList(profile.getCoordinatedEffectsList());
						interactiveObjects.computeIfAbsent(instanceOid, __ -> new HashMap<Integer, InteractiveObject>()).put(id, obj);
						obj.spawn();
					}else{
						log.error("Interactive Object Profile not Found ");
					}
				}else{
					log.error("Interactive Object Profile id is incorrect");
				}

			}else {
				if(Log.loggingDebug)
					log.debug("SpawnInteractiveObjectHook not found instance "+SPMsg.instanceOid);

			}

			return true;
		}
	}



	/**
     * Starts the spawning for an instance
     */
    class SpawnInstanceMobsHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    	    AgisMobClient.SpawnInstanceMobsMessage SPMsg = (AgisMobClient.SpawnInstanceMobsMessage) msg;
    	    if(Log.loggingDebug)
    	           log.debug("SpawnInstanceMobsHook instances="+instances+" check "+SPMsg.instanceOid);
    	    if(instances.contains(SPMsg.instanceOid)) {
    	    	if(Log.loggingDebug)
    	            log.debug("SpawnInstanceMobsHook schedule spawn for instance "+SPMsg.instanceOid);
    	    	SPMsg.tmpl.scheduleSpawnLoading(SPMsg.instanceOid);
    	    }else {
    	    	if(Log.loggingDebug)
    	            log.debug("SpawnInstanceMobsHook not found instance "+SPMsg.instanceOid);
    	    	
    	    }
    	    	
    	    return true;
    	}
    }
    
    /**
     * Spawns a creature.
     */
	class SpawnMobHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			AgisMobClient.SpawnMobMessage SPMsg = (AgisMobClient.SpawnMobMessage) msg;
			if(Log.loggingDebug)log.debug("SpawnMobHook sd="+SPMsg.sd+" "+SPMsg.spawnDataID);
			SpawnData sd = null;
			if (SPMsg.sd != null) {
				sd = SPMsg.sd;
			} else {
				if(Log.loggingDebug)log.debug("SpawnMobHook loadSpecificSpawnData ");
				sd = mobDataBase.loadSpecificSpawnData(SPMsg.spawnDataID);
				sd.setName("Spawn_" + SPMsg.loc);
				sd.setInstanceOid(SPMsg.instanceOid);
				sd.setLoc(SPMsg.loc);
				sd.setOrientation(SPMsg.orient);
				if (SPMsg.oneOffSpawn) {
					sd.setRespawnTime(-1);
				}
			}
			
			int num = sd.getNumSpawns();
			if(Log.loggingDebug)log.debug("SpawnMobHook num="+num);
			sd.setNumSpawns(1);
			for (int i = 0; i < num; i++) {
				String factoryName = createMobFactory(sd);
				if (!factoryName.equals("")) {
					sd.setFactoryName(factoryName);
					MobManagerClient.createSpawnGenerator(sd);
				}
			}
				if(Log.loggingDebug)log.debug("SpawnMobHook End " + (SPMsg.sd != null ? SPMsg.sd.getName() : ""));

			return true;
		}
	}
    
	private static Map<String, String> factoryCache = new HashMap<>();
	

private static String generateFactoryKey(int templateID, SpawnData sd) {
    BehaviorTemplate behavTmpl = (BehaviorTemplate) sd.getProperty("behaviourTemplate");
    StringBuilder keyBuilder = new StringBuilder();
    keyBuilder.append(templateID);

    keyBuilder.append("_roamRadius=").append(behavTmpl.getRoamRadius());
    keyBuilder.append("_patrolMarkers=").append(behavTmpl.getPatrolPoints().hashCode());
    keyBuilder.append("_hasCombat=").append(behavTmpl.getHasCombat());
    keyBuilder.append("_startsQuests=").append(behavTmpl.getStartsQuests().hashCode());
    keyBuilder.append("_endsQuests=").append(behavTmpl.getEndsQuests().hashCode());
    keyBuilder.append("_startsDialogues=").append(behavTmpl.getStartsDialogues().hashCode());
    keyBuilder.append("_merchantTable=").append(behavTmpl.getMerchantTable());
    keyBuilder.append("_otherActions=").append(behavTmpl.getOtherActions().hashCode());
    keyBuilder.append("_questOpenLoot=").append(behavTmpl.getQuestOpenLoot());
    keyBuilder.append("_isChest=").append(behavTmpl.getIsChest());
    keyBuilder.append("_pickupItem=").append(behavTmpl.getPickupItem());
    keyBuilder.append("_isPlayerCorpse=").append(behavTmpl.getIsPlayerCorpse());
    keyBuilder.append("_isPlayerShop=").append(behavTmpl.getIsPlayerShop());
    keyBuilder.append("_baseAction=").append(behavTmpl.getBaseAction());

    HashMap<Integer, Integer> templateAlterIds = sd.getTemplateAlterIDs();
    for (Integer key : templateAlterIds.keySet())
        keyBuilder.append("_alternateSpawnMobTemplate" + key + "=").append(templateAlterIds.get(key));

    keyBuilder.append("_combat=").append(behavTmpl.getHasCombat());
    keyBuilder.append("_roamRadius=").append(behavTmpl.getRoamRadius());
    keyBuilder.append("_startsQuests=").append(behavTmpl.getStartsQuests().hashCode());
    keyBuilder.append("_endsQuests=").append(behavTmpl.getEndsQuests().hashCode());
    keyBuilder.append("_startsDialogues=").append(behavTmpl.getStartsDialogues().hashCode());
    keyBuilder.append("_otherActions=").append(behavTmpl.getOtherActions().hashCode());
    keyBuilder.append("_baseAction=").append(behavTmpl.getBaseAction());
    keyBuilder.append("_weaponSheathed=").append(behavTmpl.getWeaponsSheathed());
    keyBuilder.append("_merchantTable=").append(behavTmpl.getMerchantTable());
    keyBuilder.append("_patrolPath=").append(behavTmpl.getPatrolPathID());
    keyBuilder.append("_questOpenLootTable=").append(behavTmpl.getQuestOpenLoot());
    keyBuilder.append("_isChest=").append(behavTmpl.getIsChest());
    keyBuilder.append("_pickupItem=").append(behavTmpl.getPickupItem());

    keyBuilder.append("_patrolMarkers=").append(behavTmpl.getPatrolPoints().hashCode());
    keyBuilder.append("_isPlayerCorpse=").append(behavTmpl.getIsPlayerCorpse());
    keyBuilder.append("_isPlayerShop=").append(behavTmpl.getIsPlayerShop());
    
    String keyString = keyBuilder.toString();
    int keyHash = keyString.hashCode();

    return templateID + "_" + keyHash;
}
    /**
     * Creates a new ObjectFactory using the data provided by the spawn data. The mob template name 
     * is obtained from a property in the spawn data.
     * @param sd: the Spawn Data to create an object factory for
     * @return String: the name of the object factory
     */
	public static String createMobFactory(SpawnData sd) {
	    log.debug("createMobFactory");
	
	    // Generate factory key
	    int templateID = sd.getRandomTemplateID();
	    String factoryKey = generateFactoryKey(templateID, sd);
	
	    // Check if factory already exists
	    String factoryName = factoryCache.get(factoryKey);
	    if (factoryName != null) {
	        return factoryName;
	    }
	
	    // Load and register templates
	    for (int tID : sd.getTemplateIDs().keySet()) {
	        Template tmpl = ObjectManagerClient.getTemplate(tID, ObjectManagerPlugin.MOB_TEMPLATE);
	        if (tmpl == null) {
	            Log.error("MOB: template [" + tID + "] doesn't exist.");
	            return "";
	        }
	        String meshName = "";
	        LinkedList<String> displays = (LinkedList) tmpl.get(WorldManagerClient.NAMESPACE, "displays");
	        if (displays != null && displays.size() > 0) {
	            meshName = displays.get(0);
	            if (Log.loggingDebug)
	                Log.debug("MOB: got display: " + meshName);
	        }
	        DisplayContext dc = new DisplayContext(meshName, true);
	        dc.addSubmesh(new DisplayContext.Submesh("", ""));
	        tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
	        tmpl.put(WorldManagerClient.NAMESPACE, "model", meshName);
	        ObjectManagerClient.registerTemplate(tmpl);
	    }
	
	    // Create new factory
	    if (Log.loggingDebug)
	        Log.debug("MOB: creating mob factory for template: " + templateID);
	    Template tmpl = ObjectManagerClient.getTemplate(templateID, ObjectManagerPlugin.MOB_TEMPLATE);
	    if (Log.loggingDebug)
	        Log.debug("Loaded templateID=" + templateID + " Template=" + tmpl);
	
	    MobFactory cFactory = new MobFactory(templateID);
	
	    // Add behaviors using suppliers that accept per-instance data
	    cFactory.addBehavSupplier((obj, spawnData) -> new BaseBehavior());
	
	    // Get behavior template
	    BehaviorTemplate behavTmpl = (BehaviorTemplate) sd.getProperty("behaviourTemplate");
	
	
    int roamRadius = behavTmpl.getRoamRadius();
    if (roamRadius > 0) {
	        cFactory.addBehavSupplier((obj, spawnData) -> {
				if(Log.loggingDebug)
			           	Log.debug("BEHAV: about to add radius roam behaviour to mob: " + templateID);
	            RadiusRoamBehavior rrBehav = new RadiusRoamBehavior();
            rrBehav.setRadius(roamRadius);
            rrBehav.setCenterLoc(spawnData.getLoc()); // Use the object's location

				rrBehav.setMinLingerTime(behavTmpl.getRoamDelayMin());
				rrBehav.setMaxLingerTime(behavTmpl.getRoamDelayMax());
				rrBehav.setRandomLingerEachTime( behavTmpl.getRoamRollTimeEachTime());
				//rrBehav.setMovementSpeed(1.6f);
	            rrBehav.setMovementSpeed((float) tmpl.get(WorldManagerClient.NAMESPACE, "speed_walk"));
				if(Log.loggingDebug)
			           Log.debug("BEHAV: adding radius roam behaviour to mob: " + templateID);
	            return rrBehav;

	        });
    }
	ArrayList<Point> patrolMarkers = behavTmpl.getPatrolPoints();
	if (patrolMarkers.size() > 0) {
	
	        cFactory.addBehavSupplier((obj, spawnData) -> {
			if(Log.loggingDebug)
		           log.debug("createMobFactory patrolMarkers="+patrolMarkers+" templateID="+templateID);
	            PatrolBehavior pBehav = new PatrolBehavior();
		            for (int i = 0; i < patrolMarkers.size(); i++) {
		                pBehav.addWaypoint(patrolMarkers.get(i));
                pBehav.addLingerTime(behavTmpl.getPatrolPauses().get(i));
		            }
			
            pBehav.setMovementSpeed((float) tmpl.get(WorldManagerClient.NAMESPACE, "speed_walk"));
			if(Log.loggingDebug)
		           Log.debug("BEHAV: adding patrol behaviour to mob: " + templateID);
            return pBehav;

        });
    }
	
    // Add CombatBehavior if applicable
    boolean hasCombat = behavTmpl.getHasCombat();
    if (hasCombat) {
        cFactory.addBehavSupplier((obj, spawnData) -> {
				if(Log.loggingDebug)
			           Log.debug("BEHAV: about to add combat behaviour to mob: " + templateID);
            CombatBehavior cBehav = new CombatBehavior();
            HashMap<Integer, MobLootTable> lootTables = (HashMap<Integer, MobLootTable>) tmpl.get(InventoryClient.NAMESPACE, "lootTables");
            if (lootTables == null)
            	lootTables = new HashMap<>();
            cBehav.setLootTables(lootTables);
            cBehav.setCenterLoc(spawnData.getLoc());
            cBehav.setAggroRange((int) tmpl.get(Namespace.FACTION, FactionStateInfo.AGGRO_RADIUS));
            cBehav.setSendLinkedAggro((boolean) tmpl.get(CombatClient.NAMESPACE, Behavior.LINKED_AGGRO_SEND));
            cBehav.setLinkedAggroRadius((int) tmpl.get(CombatClient.NAMESPACE, Behavior.LINKED_AGGRO_RADIUS));
            cBehav.setReciveLinkedAggro((boolean) tmpl.get(CombatClient.NAMESPACE, Behavior.LINKED_AGGRO_GET));
            cBehav.setchaseDistance((int) tmpl.get(CombatClient.NAMESPACE, Behavior.LINKED_CHASING_DISTANCE));

			String tags = (String)tmpl.get(CombatClient.NAMESPACE, "tags");
			cBehav.setTags(tags);
            SetCombatBehavior(cBehav, tmpl);
			if(Log.loggingDebug)
		           Log.debug("BEHAV: adding combat behaviour to mob: " + templateID);
            return cBehav;

        });
    }

    // Add NpcBehavior if applicable
    ArrayList<Integer> startQuestList = behavTmpl.getStartsQuests();
    ArrayList<Integer> endQuestList = behavTmpl.getEndsQuests();
    ArrayList<Integer> startsDialoguesList = behavTmpl.getStartsDialogues();
    int merchantTable = behavTmpl.getMerchantTable();
			log.debug("Spawn merchantTable="+merchantTable);
    if (!startQuestList.isEmpty() || !endQuestList.isEmpty() || !startsDialoguesList.isEmpty()
            || (merchantTable > 0) || !behavTmpl.getOtherActions().isEmpty()) {
	        cFactory.addBehavSupplier((obj, spawnData) -> {
	            NpcBehavior qBehav = new NpcBehavior();
	            for (int i : startQuestList) {
	                if (Agis.QuestManager.get(i) != null) {
	                    qBehav.startsQuest(Agis.QuestManager.get(i));
						}else {
							if(Log.loggingDebug)
						           log.debug("Start Quest not found id="+i);
	                }
	            }
	            for (int i : endQuestList) {
	                if (Agis.QuestManager.get(i) != null) {
	                    qBehav.endsQuest(Agis.QuestManager.get(i));
						}else {
							if(Log.loggingDebug)
						           log.debug("End Quest not found id="+i);
	                }
	            }
	            for (int i : startsDialoguesList) {
	                if (Agis.DialogueManager.get(i) != null) {
	                    qBehav.startsDialogue(Agis.DialogueManager.get(i));
						}else {
							if(Log.loggingDebug)
						           log.debug("Start Dialogue not found id="+i);
	                }
	            }
	            if (merchantTable > 0) {
	                qBehav.setMerchantTable(Agis.MerchantTableManager.get(merchantTable));
                spawnData.setProperty("merchantTable", merchantTable);
	            }
            qBehav.setOtherActions(behavTmpl.getOtherActions());
	            return qBehav;
	        });
    }
	
    int questOpenLoot = behavTmpl.getQuestOpenLoot();
    if (questOpenLoot > 0) {
	        cFactory.addBehavSupplier((obj, spawnData) -> {
				Log.debug("OPEN: adding open behav to mob: " + templateID + "with loot table num: " + questOpenLoot);
	            OpenBehavior oBehav = new OpenBehavior();

            LootTable lootTable = Agis.LootTableManager.get(questOpenLoot);
	            if (lootTable != null) {
				Log.debug("OPEN: got loot table with num items: " + lootTable.getItems().size());
	                oBehav.setItemsHeld(lootTable.getItems());
	                oBehav.setItemLimit(1);
	            }
				if(Log.loggingDebug)
			           Log.debug("OPEN: added open behav to mob: " + templateID + "with item: " + lootTable.getItems().get(0));
	            return oBehav;

	        });
    }
	
	    // Add ChestBehavior if applicable
	    if (behavTmpl.getIsChest()) {
	        cFactory.addBehavSupplier((obj, spawnData) -> {
				if(Log.loggingDebug)
			           Log.debug("OPEN: adding chest behav to mob: " + templateID + "with loot tables");
	            ChestBehavior oBehav = new ChestBehavior();
	            oBehav.setSingleItemPickup(false);
				if(Log.loggingDebug)
			           Log.debug("OPEN: added chest behav to mob: " + templateID);
	            return oBehav;

	        });
	    }
	
	
    int pickupItem = behavTmpl.getPickupItem();
    if (pickupItem > 0) {
	        cFactory.addBehavSupplier((obj, spawnData) -> {
				if(Log.loggingDebug)
			           Log.debug("OPEN: adding pickup behav to mob: " + templateID + "with itemID: " + pickupItem);
	            ChestBehavior oBehav = new ChestBehavior();
	            ArrayList<Integer> itemList = new ArrayList<>();
            itemList.add(pickupItem);
	            oBehav.setItemsHeld(itemList);
	            oBehav.setItemLimit(1);
	            oBehav.setSingleItemPickup(true);
				if(Log.loggingDebug)
			           Log.debug("OPEN: added pickup behav to mob: " + templateID + "with item: " + pickupItem);
	            return oBehav;
	        });
    }
	
	    // Add PlayerCorpseBehavior if applicable
	    if (behavTmpl.getIsPlayerCorpse()) {
	        cFactory.addBehavSupplier((obj, spawnData) -> {
	            PlayerCorpseBehavior pBehav = new PlayerCorpseBehavior();
            pBehav.addAcceptableTarget(OID.fromString(behavTmpl.getOtherUse()));
	            pBehav.setLoot((LinkedList<OID>) spawnData.getProperty("loot"));
	            if (spawnData.getProperty("loot_curr") != null)
	                pBehav.setLootCurrency((LinkedList<String>) spawnData.getProperty("loot_curr"));
	            pBehav.setCorpseDuration((Integer) spawnData.getProperty("corpseDuration"));
	            pBehav.setSafeDuration((Integer) spawnData.getProperty("safeDuration"));
	            pBehav.setSpawnerKey(spawnData.getName());
            pBehav.setCorpseOwner(OID.fromString(behavTmpl.getOtherUse()));
			Log.debug("TEMP: PlayerCorpseBehaviour added");
	            return pBehav;

	        });
	    }
	
	    // Add ShopBehavior if applicable
	    if (behavTmpl.getIsPlayerShop()) {
	        cFactory.addBehavSupplier((obj, spawnData) -> {
	            ShopBehavior sb = new ShopBehavior();
	            sb.setShopOid((OID) spawnData.getProperty("shopOid"));
	            sb.setShopTimeOut((int) spawnData.getProperty("shopTimeOut"));
	            sb.setShopMessage((String) spawnData.getProperty("shopMessage"));
	            sb.setShopDestroyOnLogOut((boolean) spawnData.getProperty("destroyOnLogOut"));
	            sb.setShopOwner((OID) spawnData.getProperty("shopOwner"));
	            return sb;
	        });
	    }
	    
	    // Add BaseBehavior
	    cFactory.addBehavSupplier((obj, spawnData) -> new BaseBehavior());
				
		if (behavTmpl.getBaseAction() != null) {
			sd.setProperty("baseAction", behavTmpl.getBaseAction());
		}
		
	    // Set spawn properties
	    HashMap<String, Serializable> spawnProps = new HashMap<>();
	    spawnProps.put("weaponsSheathed", behavTmpl.getWeaponsSheathed());
	    spawnProps.put("otherUse", behavTmpl.getOtherUse());
	    spawnProps.put("spawnId", sd.getProperty("id"));
	    sd.setProperty("props", spawnProps);
	
	    // Generate factory name and register
	    factoryName = "Factory_" + factoryKey;
		if(Log.loggingDebug)
	           Log.debug("BEHAV: registering factory for mob: templateID=" + templateID+" factoryName="+factoryName);
	    ObjectFactory.register(factoryName, cFactory);
	    numFactories++;
		if(Log.loggingDebug)
	           Log.debug("MOB: finished creating mob factory for template: " + templateID);
		log.debug("createMobFactory END");
	
	    // Cache the factory
	    factoryCache.put(factoryKey, factoryName);
	    return factoryName;
    }


	public static void SetCombatBehavior(CombatBehavior cBehav, Template tmpl){
		String equipment = (String)tmpl.get(InventoryClient.NAMESPACE, InventoryClient.TEMPL_ITEMS);
		int behavProfile = (int)tmpl.get(CombatClient.NAMESPACE, "behavProfile");
		if(mobCombatBehaviorProfiles.containsKey(behavProfile)) {
			cBehav.behaviorId = behavProfile;
			MobBehaviorProfile mbp = mobCombatBehaviorProfiles.get(behavProfile);
			if (Log.loggingDebug)Log.debug("Set mob behavior profile "+mbp);
			for(MobBehavior mb : mbp.behaviors) {
				if (Log.loggingDebug)Log.debug("Set mob behavior  "+mb);
				CombatBehavior.CombatBehaviorEntry cb = cBehav.new CombatBehaviorEntry();
				cb.behaviorType = mb.getType();
				cb.fleeType = mb.getFleeType();
				cb.fleepoint = mb.getFleePoint();
				cb.fleepoints = mb.getFleePoints();
				cb.mobTag = mb.getMobTag();
				cb.weapon = mb.getWeapon();
				cb.abilityinterval = mb.getAbilityInterval();
				cb.ignoreChaceDistance = mb.getIgnoreChaseDistance();
				//Set Conditions
				for(BehaviorConditionGroupSettings gbcs : mb.conditionsGroup) {
					CombatBehavior.GroupBehaviorConditions gbc = cBehav.new GroupBehaviorConditions();
					List<String> confitionList = new ArrayList<String>();
					for(BehaviorConditionSettings bcs : gbcs.conditions) {
						switch (bcs.type) {
							case 0:
								CombatBehavior.EventBehaviorCondition ebc = cBehav.new EventBehaviorCondition();
								ebc.eventId = bcs.triggerEvent;
								ebc.target = bcs.target;
								gbc.conditions.add(ebc);
								break;
							case 1:
								CombatBehavior.BehaviorCondition bc = cBehav.new BehaviorCondition();
								bc.distance = bcs.distance;
								bc.less = bcs.less;
								gbc.conditions.add(bc);
								break;
							case 2:
								CombatBehavior.StatBehaviorCondition sbc = cBehav.new StatBehaviorCondition();
								sbc.statName = bcs.statName;
								sbc.statValue = bcs.statValue;
								sbc.vitalityPercentage = bcs.statVitalityPercentage;
								sbc.target = bcs.target;
								sbc.less = bcs.less;
								gbc.conditions.add(sbc);
								break;
							case 3:
								CombatBehavior.EffectBehaviorCondition efbc = cBehav.new EffectBehaviorCondition();
								efbc.tagId = bcs.effectTag;
								efbc.target = bcs.target;
								efbc.onTarget = bcs.onTarget;
								gbc.conditions.add(efbc);
								break;
							case 4:
								CombatBehavior.TargetCombatStateBehaviorCondition tcsbc = cBehav.new TargetCombatStateBehaviorCondition();
								tcsbc.combatState = bcs.combatState;
								gbc.conditions.add(tcsbc);
								break;
							case 5:
								CombatBehavior.TargetDeathStateBehaviorCondition tdsbc = cBehav.new TargetDeathStateBehaviorCondition();
								tdsbc.deathState = bcs.deathState;
								gbc.conditions.add(tdsbc);
								break;
							case 6:
								CombatBehavior.NumberOfTargetsStateBehaviorCondition notsbc = cBehav.new NumberOfTargetsStateBehaviorCondition();
								notsbc.number = bcs.target_number;
								notsbc.allay = bcs.target_ally;
								notsbc.less = bcs.less;
								gbc.conditions.add(notsbc);
								break;
						}
					}
					cb.conditions.add(gbc);
				}

				//load abilities
				for (MobAbility ma : mb.abilities) {
					if (Log.loggingDebug)Log.debug("Set abilities to behavior  "+ma);
					CombatBehavior.AbilityGroup am = cBehav.new AbilityGroup();
					String abi = ma.abilities;
					String[] abis = abi.split(";");
					if (Log.loggingDebug)Log.debug("Ids "+Arrays.toString(abis));
					int count = abis.length;
					if (Log.loggingDebug)Log.debug("Ability count ="+count);
					if (count > 1) {
						int sumPriority=0;
						for (int i = 0; i < count / 2; i++) {
							if (abis[i * 2].length() > 0) {
								int id = Integer.parseInt(abis[i * 2]);
								int c = Integer.parseInt(abis[i * 2 + 1]);
								am.priority.put(id,c);
								sumPriority+=c;
								//for (int j = 0; j < c; j++) {
								am.ids.add(id);

								//}
							}
						}
						am.sumPriority = sumPriority;
					}
					if (Log.loggingDebug)Log.debug(" Abilities "+am.ids);
					am.minAbilityRangePercentage = ma.minAbilityRangePercentage;
					am.maxAbilityRangePercentage = ma.maxAbilityRangePercentage;


					//Set Conditions for ability
					for(BehaviorConditionGroupSettings gbcs : ma.conditionsGroup) {
						CombatBehavior.GroupBehaviorConditions gbc = cBehav.new GroupBehaviorConditions();
						List<String> confitionList = new ArrayList<String>();
						for(BehaviorConditionSettings bcs : gbcs.conditions) {

							switch (bcs.type) {
								case 0:
									CombatBehavior.EventBehaviorCondition ebc = cBehav.new EventBehaviorCondition();
									ebc.eventId = bcs.triggerEvent;
									ebc.target =  bcs.target;
									gbc.conditions.add(ebc);
									break;
								case 1:
									CombatBehavior.BehaviorCondition bc = cBehav.new BehaviorCondition();
									bc.distance = bcs.distance;
									bc.less = bcs.less;
									gbc.conditions.add(bc);
									break;
								case 2:
									CombatBehavior.StatBehaviorCondition sbc = cBehav.new StatBehaviorCondition();
									sbc.statName = bcs.statName;
									sbc.statValue = bcs.statValue;
									sbc.vitalityPercentage = bcs.statVitalityPercentage;
									sbc.target = bcs.target;
									sbc.less = bcs.less;
									gbc.conditions.add(sbc);
									break;
								case 3:
									CombatBehavior.EffectBehaviorCondition efbc = cBehav.new EffectBehaviorCondition();
									efbc.tagId = bcs.effectTag;
									efbc.target = bcs.target;
									efbc.onTarget = bcs.onTarget;
									gbc.conditions.add(efbc);
									break;
								case 4:
									CombatBehavior.TargetCombatStateBehaviorCondition tcsbc = cBehav.new TargetCombatStateBehaviorCondition();
									tcsbc.combatState = bcs.combatState;
									gbc.conditions.add(tcsbc);
									break;
								case 5:
									CombatBehavior.TargetDeathStateBehaviorCondition tdsbc = cBehav.new TargetDeathStateBehaviorCondition();
									tdsbc.deathState = bcs.deathState;
									gbc.conditions.add(tdsbc);
									break;
								case 6:
									CombatBehavior.NumberOfTargetsStateBehaviorCondition notsbc = cBehav.new NumberOfTargetsStateBehaviorCondition();
									notsbc.number = bcs.target_number;
									notsbc.allay = bcs.target_ally;
									notsbc.less = bcs.less;
									gbc.conditions.add(notsbc);
									break;
							}
						}
						am.conditions.add(gbc);
					}
					cb.abilities.add(am);
				}

				//load abilities
				for (MobAbility ma : mb.startAbilities) {
					if (Log.loggingDebug)Log.debug("Set start abilities to behavior  "+ma);

					CombatBehavior.AbilityGroup am = cBehav.new AbilityGroup();
					String abi = ma.abilities;
					String[] abis = abi.split(";");
					int count = abis.length;
					if (count > 1) {
						int sumPriority=0;
						for (int i = 0; i < count / 2; i++) {
							if (abis[i * 2].length() > 0) {
								int id = Integer.parseInt(abis[i * 2]);
								int c = Integer.parseInt(abis[i * 2 + 1]);
								am.priority.put(id,c);
								sumPriority+=c;
								//	for (int j = 0; j < c; j++) {
								am.ids.add(id);

								//	}
							}
						}
						am.sumPriority = sumPriority;
					}
					am.minAbilityRangePercentage = ma.minAbilityRangePercentage;
					am.maxAbilityRangePercentage = ma.maxAbilityRangePercentage;


					//Set Conditions for ability
					for(BehaviorConditionGroupSettings gbcs : ma.conditionsGroup) {
						CombatBehavior.GroupBehaviorConditions gbc = cBehav.new GroupBehaviorConditions();
						List<String> confitionList = new ArrayList<String>();
						for(BehaviorConditionSettings bcs : gbcs.conditions) {

							switch (bcs.type) {
								case 0:
									CombatBehavior.EventBehaviorCondition ebc = cBehav.new EventBehaviorCondition();
									ebc.eventId = bcs.triggerEvent;
									ebc.target = bcs.target;
									gbc.conditions.add(ebc);
									break;
								case 1:
									CombatBehavior.BehaviorCondition bc = cBehav.new BehaviorCondition();
									bc.distance = bcs.distance;
									bc.less = bcs.less;
									gbc.conditions.add(bc);
									break;
								case 2:
									CombatBehavior.StatBehaviorCondition sbc = cBehav.new StatBehaviorCondition();
									sbc.statName = bcs.statName;
									sbc.statValue = bcs.statValue;
									sbc.vitalityPercentage = bcs.statVitalityPercentage;
									sbc.target = bcs.target;
									sbc.less = bcs.less;
									gbc.conditions.add(sbc);
									break;
								case 3:
									CombatBehavior.EffectBehaviorCondition efbc = cBehav.new EffectBehaviorCondition();
									efbc.tagId = bcs.effectTag;
									efbc.target = bcs.target;
									efbc.onTarget = bcs.onTarget;
									gbc.conditions.add(efbc);
									break;
								case 4:
									CombatBehavior.TargetCombatStateBehaviorCondition tcsbc = cBehav.new TargetCombatStateBehaviorCondition();
									tcsbc.combatState = bcs.combatState;
									gbc.conditions.add(tcsbc);
									break;
								case 5:
									CombatBehavior.TargetDeathStateBehaviorCondition tdsbc = cBehav.new TargetDeathStateBehaviorCondition();
									tdsbc.deathState = bcs.deathState;
									gbc.conditions.add(tdsbc);
									break;
								case 6:
									CombatBehavior.NumberOfTargetsStateBehaviorCondition notsbc = cBehav.new NumberOfTargetsStateBehaviorCondition();
									notsbc.number = bcs.target_number;
									notsbc.allay = bcs.target_ally;
									notsbc.less = bcs.less;
									gbc.conditions.add(notsbc);
									break;
							}
						}
						am.conditions.add(gbc);
					}
					cb.startAbilities.add(am);
				}
				//load abilities
				for (MobAbility ma : mb.endAbilities) {
					if (Log.loggingDebug)Log.debug("Set end abilities to behavior  "+ma);

					CombatBehavior.AbilityGroup am = cBehav.new AbilityGroup();
					String abi = ma.abilities;
					String[] abis = abi.split(";");
					int count = abis.length;
					if (count > 1) {
						int sumPriority=0;
						for (int i = 0; i < count / 2; i++) {
							if (abis[i * 2].length() > 0) {
								int id = Integer.parseInt(abis[i * 2]);
								int c = Integer.parseInt(abis[i * 2 + 1]);
								am.priority.put(id,c);
								sumPriority+=c;
								//	for (int j = 0; j < c; j++) {
								am.ids.add(id);

								//}
							}
						}
						am.sumPriority = sumPriority;
					}
					am.minAbilityRangePercentage = ma.minAbilityRangePercentage;
					am.maxAbilityRangePercentage = ma.maxAbilityRangePercentage;


					//Set Conditions for ability
					for(BehaviorConditionGroupSettings gbcs : ma.conditionsGroup) {
						CombatBehavior.GroupBehaviorConditions gbc = cBehav.new GroupBehaviorConditions();
						List<String> confitionList = new ArrayList<String>();
						for(BehaviorConditionSettings bcs : gbcs.conditions) {

							switch (bcs.type) {
								case 0:
									CombatBehavior.EventBehaviorCondition ebc = cBehav.new EventBehaviorCondition();
									ebc.eventId =  bcs.triggerEvent;
									ebc.target = bcs.target;
									gbc.conditions.add(ebc);
									break;
								case 1:
									CombatBehavior.BehaviorCondition bc = cBehav.new BehaviorCondition();
									bc.distance = bcs.distance;
									bc.less = bcs.less;
									gbc.conditions.add(bc);
									break;
								case 2:
									CombatBehavior.StatBehaviorCondition sbc = cBehav.new StatBehaviorCondition();
									sbc.statName = bcs.statName;
									sbc.statValue = bcs.statValue;
									sbc.vitalityPercentage = bcs.statVitalityPercentage;
									sbc.target = bcs.target;
									sbc.less = bcs.less;
									gbc.conditions.add(sbc);
									break;
								case 3:
									CombatBehavior.EffectBehaviorCondition efbc = cBehav.new EffectBehaviorCondition();
									efbc.tagId = bcs.effectTag;
									efbc.target = bcs.target;
									efbc.onTarget = bcs.onTarget;
									gbc.conditions.add(efbc);
									break;
								case 4:
									CombatBehavior.TargetCombatStateBehaviorCondition tcsbc = cBehav.new TargetCombatStateBehaviorCondition();
									tcsbc.combatState = bcs.combatState;
									gbc.conditions.add(tcsbc);
									break;
								case 5:
									CombatBehavior.TargetDeathStateBehaviorCondition tdsbc = cBehav.new TargetDeathStateBehaviorCondition();
									tdsbc.deathState = bcs.deathState;
									gbc.conditions.add(tdsbc);
									break;
								case 6:
									CombatBehavior.NumberOfTargetsStateBehaviorCondition notsbc = cBehav.new NumberOfTargetsStateBehaviorCondition();
									notsbc.number = bcs.target_number;
									notsbc.allay = bcs.target_ally;
									notsbc.less = bcs.less;
									gbc.conditions.add(notsbc);
									break;
							}
						}
						am.conditions.add(gbc);
					}
					cb.endAbilities.add(am);
				}

				cBehav.behaviors.add(cb);
			}

		}

		//spawnProps.put("tags", tags);


		// NEW

		float attackDistance = (Float) tmpl.get(CombatClient.NAMESPACE, "attackDistance");
		cBehav.setMovementSpeed((float)tmpl.get(WorldManagerClient.NAMESPACE, "speed_run"));
		cBehav.setDefaultMovementSpeed((float)tmpl.get(WorldManagerClient.NAMESPACE, "speed_run"));
		cBehav.setAttackDistance(attackDistance);
	}


    
    /**
     * Creates a new ObjectFactory using the data provided by the spawn data. The mob template name 
     * is obtained from a property in the spawn data.
     * @param sd: the Spawn Data to create an object factory for
     * @return String: the name of the object factory
     */
	public static String createMobShopFactory(SpawnData sd) {
	    log.debug("createMobShopFactory");

	    // Load and register templates
	    for (int tID : sd.getTemplateIDs().keySet()) {
	        Template tmpl = ObjectManagerClient.getTemplate(tID, ObjectManagerPlugin.MOB_TEMPLATE);
	        if (tmpl == null) {
	            Log.error("MOB: template [" + tID + "] doesn't exist.");
	            return "";
	        }
	        String meshName = "";
	        LinkedList<String> displays = (LinkedList) tmpl.get(WorldManagerClient.NAMESPACE, "displays");
	        if (displays != null && displays.size() > 0) {
	            meshName = displays.get(0);
	            if (Log.loggingDebug)
	                Log.debug("MOB: got display: " + meshName);
	        }
	        DisplayContext dc = new DisplayContext(meshName, true);
	        dc.addSubmesh(new DisplayContext.Submesh("", ""));
	        tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
	        tmpl.put(WorldManagerClient.NAMESPACE, "model", meshName);
	        ObjectManagerClient.registerTemplate(tmpl);
	    }

	    int templateID = sd.getRandomTemplateID();

	    if (Log.loggingDebug)
	        Log.debug("MOB: creating mob factory for template: " + templateID);
	    Template tmpl = ObjectManagerClient.getTemplate(templateID, ObjectManagerPlugin.MOB_TEMPLATE);
	    if (Log.loggingDebug)
	        Log.debug("Loaded templateID=" + templateID + " Template=" + tmpl);

	    HashMap<String, Serializable> spawnProps = new HashMap<>();
	    MobFactory cFactory = new MobFactory(templateID);

	    // Add behaviors using suppliers
	    cFactory.addBehavSupplier((obj, spawnData) -> new BaseBehavior());

	    // Get properties from the template and add/edit behaviors
	    BehaviorTemplate behavTmpl = (BehaviorTemplate) sd.getProperty("behaviourTemplate");

	    // Add ShopBehavior using a supplier
	    cFactory.addBehavSupplier((obj, spawnData) -> new ShopBehavior());

	    // Set spawn properties
	    spawnProps.put("weaponsSheathed", behavTmpl.getWeaponsSheathed());
	    spawnProps.put("otherUse", behavTmpl.getOtherUse());
	    spawnProps.put("spawnId", sd.getProperty("id"));
	    sd.setProperty("props", spawnProps);

	    // Generate factory name
	    String factoryName = "Factory_" + templateID + "_" + System.currentTimeMillis();
	    if (Log.loggingDebug)
	        Log.debug("BEHAV: registering factory for mob: templateID=" + templateID + " factoryName=" + factoryName);
	    ObjectFactory.register(factoryName, cFactory);
	    numFactories++;
	    if (Log.loggingDebug)
	        Log.debug("MOB: finished creating mob factory for template: " + templateID);
	    log.debug("createMobShopFactory END");

	    return factoryName;
	}

    
    /**
     * Hook for the DeleteSpawnGenerator Message. Deletes and deactivates the SpawnGenerator matching the
     * instance and name provided.
     * @author Andrew Harrison
     */
    class DeleteSpawnGeneratorHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		Log.debug("DESPAWN: got delete spawn generator message");
    	    AgisMobClient.DeleteSpawnGeneratorMessage delMsg = (AgisMobClient.DeleteSpawnGeneratorMessage) msg;
    	    SpawnGenerator.removeSpawnGenerator(delMsg.instanceOid, delMsg.spawnGeneratorName);
    	    return true;
    	}
    }
    
    /**
     * Handles the ServerTimeMessage. Passes the server time to the spawn generator so it can enable or disable
     * any spawn generators that are affected by the change in time.
     * @author Andrew Harrison
     *
     */
    class ServerTimeHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    	    AgisWorldManagerClient.ServerTimeMessage tMsg = (AgisWorldManagerClient.ServerTimeMessage) msg;
    	    if(Log.loggingDebug)
    	           Log.debug("TIME: got server time message with hour: " + tMsg.getHour());
    	    SpawnGenerator.serverTimeUpdate(tMsg.getHour(), tMsg.getMinute());
    	    return true;
    	}
    }
    
    /**
     * Sets the base model dc map for an NPC so they can have items equipped.
     */
    private void setBaseModel(Template tmpl, String gender) {
    	LinkedList<String> displayList = (LinkedList) tmpl.get(WorldManagerClient.NAMESPACE, "displays");
    	if (displayList != null) {
    		int displayNum = random.nextInt(displayList.size());
    		String display = displayList.get(displayNum);
    		if(Log.loggingDebug)
    	           	Log.debug("NPC: using displayID: " + display);
    		if(Log.loggingDebug)
    	             Log.debug("MOB: chose display " + display);
		    DisplayContext dc = new DisplayContext(display, true);
    		dc.addSubmesh(new DisplayContext.Submesh("", ""));
		    tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
		    tmpl.put(WorldManagerClient.NAMESPACE, "playerAppearance", "NPC");
		    ObjectManagerClient.registerTemplate(tmpl);
    		return;
    	}
    }
    
    /**
     * Sets the display properties for the mob/npc based on their gender.
     * @param oid
     * @param gender
     */
    public static void setDisplay(OID oid, String gender) {
    	LinkedList<String> displayList = (LinkedList) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "displays");
    	Log.debug("DISPLAY: at setDisplay with mob " + oid + " which has displayList: " + displayList);
    	if (displayList != null) {
    		int displayNum = random.nextInt(displayList.size());
    		String display = displayList.get(displayNum);
    		if(Log.loggingDebug)
    	           	Log.debug("DISPLAY: chose display " + display);
    		DisplayContext dc = new DisplayContext(display, true);
    		dc.addSubmesh(new DisplayContext.Submesh("", ""));
    		HashMap<String, Serializable> propMap = new HashMap<String, Serializable>();
    		propMap.put("aoobj.dc", dc);
    		String mobName = WorldManagerClient.getObjectInfo(oid).name;
    		if(Log.loggingDebug)
    	           	Log.debug("DISPLAY: setting " + mobName + "'s gender as: " + gender + " with prefab: " + display);
    		
    		EnginePlugin.setObjectProperties(oid, WorldManagerClient.NAMESPACE, propMap);
    		// Send the appearance afterwards just to make sure that the other properties are updated first
    		HashMap<String, Serializable> propMap2 = new HashMap<String, Serializable>();
    		propMap2.put("playerAppearance", "NPC");
    		EnginePlugin.setObjectProperties(oid, WorldManagerClient.NAMESPACE, propMap2);
    		if(Log.loggingDebug)
    	           	Log.debug("DISPLAY: finished setting display " + display + " for mob: " + oid);
    		//ObjectManagerClient.registerTemplate(tmpl);
    		return;
    	}
    }
    
   
    
    class LoginHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LoginMessage message = (LoginMessage) msg;
            OID playerOid = message.getSubject();
            try {
				EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "nonCombatPet", null);
				//CombatPet cPet = (CombatPet) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "CombatPet");
				EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "hasPet", false);
				EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "activePet", null);
				EnginePlugin.setObjectProperty(playerOid, CombatClient.NAMESPACE, "activePetList", new HashMap<OID,Integer>());
				EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "combatPet", null);
			} catch (Exception e) {
				log.error("LoginHook Exception: "+e);
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
            
			synchronized (shopBehaviors) {
				if (shopBehaviors.size() == 0) {
					Log.debug("PlayerShopTick messages: no data to send");
				} else {
					Log.debug("PlayerShopTick: Shop behaviors=" + shopBehaviors.size());
					for (ShopBehavior sb : shopBehaviors.values()) {
						sb.PlayerLogOut(playerOid);
					}
				}
			}
			Engine.getAgent().sendResponse(new ResponseMessage(message));
			return true;
        }
    }
    
    class SpawnedHook implements Hook  {
    	public boolean processMessage(Message msg, int flags) {
    		WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;
            OID objOid = spawnedMsg.getSubject();
            if(Log.loggingDebug)
        	   log.debug("SpawnedHook: objOid:"+objOid+" instances="+instances);
        	OID instanceOid = spawnedMsg.getInstanceOid();
        	if(Log.loggingDebug)
                log.debug("SpawnedHook: objOid:"+objOid+" instanceOid="+instanceOid);
        	if(!instances.contains(instanceOid)) {
        		return true;
        	}
           ObjectInfo objInfo = WorldManagerClient.getObjectInfo(objOid);
            if(Log.loggingDebug)
                log.debug("SpawnedHook: objOid:"+objOid+" objInfo:"+objInfo+" objInfo.objType:"+objInfo.objType);
             if (objInfo != null && objInfo.objType == ObjectTypes.player) {
            	// Set the players world property
            	 if(Log.loggingDebug)
                     Log.debug("SPAWNED: setting world for player: " + objOid);
            	
        	    int world = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
        	    EnginePlugin.setObjectProperty(objOid, WorldManagerClient.NAMESPACE, "world", world);
        	    // Get the water height of the instance
        	    float globalWaterHeight =  InstanceClient.getInstanceTemplate(world).getGlobalWaterHeight();
        	    EnginePlugin.setObjectProperty(objOid, WorldManagerClient.NAMESPACE, "waterHeight", globalWaterHeight);
            } else if (objInfo != null && objInfo.objType == ObjectTypes.mob) {
                mobsPerInstance.computeIfAbsent(instanceOid, __ -> {
                    Gauge.builder("live_mobs", () -> mobsPerInstance.getOrDefault(instanceOid, new AtomicInteger()).get())
                            .tag("instance_id", instanceOid.toString()).register(Prometheus.registry());
                    return new AtomicInteger();
                }).incrementAndGet();
            	//OID instanceOid = spawnedMsg.getInstanceOid();
            	subscribeForMob(objOid);
            	if(Log.loggingDebug)
                    log.debug("SpawnedHook: objOid:"+objOid+" objInfo:"+objInfo+" instanceOid:"+instanceOid);
                    if (instanceNavMeshes.containsKey(instanceOid)) {
            		ObjectStub objStub = (ObjectStub) EntityManager.getEntityByNamespace(objOid, Namespace.MOB);
            		//EntityManager.registerEntityByNamespace(obj, Namespace.MOB);
            	     if (objStub != null && objStub.getWorldNode() != null) {
            	    	 if(Log.loggingDebug)
            	             	log.debug("SpawnedHook: objOid:"+objOid+" objInfo:"+objInfo+" instanceOid:"+instanceOid+" objStub:"+objStub+" objStub.getWorldNode():"+objStub.getWorldNode());
                 	    DetourActor actor = new DetourActor(objOid, objStub);
            			instanceNavMeshes.get(instanceOid).addActor(objOid, objInfo.loc, actor);
            		}
            	}else {
            		if(Log.loggingDebug)
            	           log.debug("SpawnedHook: objOid:"+objOid+" objInfo:"+objInfo+" instanceOid:"+instanceOid+" no NavMesh for instance");
                  
            	}
            }
            
            return true;
    	}
    }
    
    class DespawnedHook implements Hook  {
    	public boolean processMessage(Message msg, int flags) {
    		WorldManagerClient.DespawnedMessage despawnedMsg = (WorldManagerClient.DespawnedMessage) msg;
            OID objOid = despawnedMsg.getSubject();

			if(Log.loggingDebug)Log.debug("DespawnedHook, "+objOid+" start");
            OID instanceOid = despawnedMsg.getInstanceOid();
            unsubscribeForMob(objOid);
            if (despawnedMsg.getType() != null && despawnedMsg.getType() == ObjectTypes.mob) {
                mobsPerInstance.computeIfAbsent(instanceOid, __ -> new AtomicInteger()).decrementAndGet();
            }
            if (instanceNavMeshes.containsKey(instanceOid)) {
        		instanceNavMeshes.get(instanceOid).removeActor(objOid);
        	}
            
            if (mobsToAlertOnDeath.containsKey(objOid)) {
            	ArrayList<CombatBehavior> cBehavs = new ArrayList<CombatBehavior>(mobsToAlertOnDeath.get(objOid));
    			for (CombatBehavior cBehav : cBehavs) {
    				cBehav.alertMobOfDespawn(objOid);
    			}
            	mobsToAlertOnDeath.remove(objOid);
            }
			if(Log.loggingDebug)Log.debug("DespawnedHook, "+objOid+" objectsTargetType "+objectsTargetType.containsKey(objOid));
			objectsTargetType.remove(objOid);
			objectsTargetType.forEach((k,v)->{
				v.remove(objOid);
			});
			Gauge.builder("objectsTargetType", () -> objectsTargetType.size()).register(Prometheus.registry());
			return true;
    	}
    }
    
    /**
	 * Hook for the Harvest Resource Message. Used when a player attempts to start harvesting 
	 * from a ResourceNode.
	 * @author Andrew Harrison
	 *
	 */
	class InteractWithObjectHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage gridMsg = (ExtensionMessage)msg;
            Log.debug("INTERACTIVE: got interact with object message");
            OID playerOid = gridMsg.getSubject();
            ObjectInfo oi = WorldManagerClient.getObjectInfo(playerOid);
            if(oi == null) {
            	log.error("INTERACTIVE: Can't get ObjectInfo for "+playerOid );
            	return true;
            }
            OID instanceOid = oi.instanceOid;
            int objectID = (Integer)gridMsg.getProperty("objectID");
            String state = (String)gridMsg.getProperty("state");
			Log.debug("INTERACTIVE: InteractWithObjectHook got obj instance: " + instanceOid + " num instances "+interactiveObjects.size());
            if (interactiveObjects.containsKey(instanceOid)) {
            	if(Log.loggingDebug)
                    Log.debug("INTERACTIVE: got obj instance: " + instanceOid + " looking for object: " + objectID+" "+interactiveObjects.get(instanceOid).size());
            	if (interactiveObjects.get(instanceOid).containsKey(objectID)) {
            		Log.debug("INTERACTIVE: got object"); 
            		interactiveObjects.get(instanceOid).get(objectID).tryUseObject(playerOid, state);
            	} else {
            		if(Log.loggingDebug)
            	           Log.debug("INTERACTIVE: not get object objectID="+objectID); 
            		if(Log.loggingDebug)
            	           Log.debug("INTERACTIVE: not get object interactiveObjects.get(instanceOid)="+interactiveObjects.get(instanceOid).keySet()); 
                    
            	}
            }
            return true;
        }
	}
    
    /**
     * Spawns a creature in the specified arena. Gives the creature the arenaID so when it dies the system
     * can track it.
     */
    class SpawnArenaCreatureHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    	    AgisMobClient.spawnArenaCreatureMessage SPMsg = (AgisMobClient.spawnArenaCreatureMessage) msg;
    	    //Long duration = (Long) SPMsg.getProperty("duration");
    	    int arenaID = (Integer) SPMsg.getProperty("arenaID");
    	    OID instanceOid = (OID) SPMsg.getProperty("instanceOid");
    	    //String passiveEffect = (String) SPMsg.getProperty("passiveEffect");
    	    int spawnDataNum = (Integer) SPMsg.getProperty("spawnDataID");
    	    
    	    if (!spawnInfo.containsKey(spawnDataNum))
    	    {
    	    	// Load it in from the database
    	    	spawnInfo.put(spawnDataNum, mobDataBase.loadSpecificSpawnData(spawnDataNum));
    	    }
    	    SpawnData sd = spawnInfo.get(spawnDataNum);
    	    
    		String location = sd.getStringProperty("markerName");
    		if (location.equals("")) {
    			// location is a vector, no need to change it
    			int locX = sd.getIntProperty("locX");
    			int locY = sd.getIntProperty("locY");
    			int locZ = sd.getIntProperty("locZ");
    			Point p = new Point(locX, locY, locZ);
    			sd.setLoc(p);
    			int orientX = sd.getIntProperty("orientX");
    			int orientY = sd.getIntProperty("orientY");
    			int orientZ = sd.getIntProperty("orientZ");
    			int orientW = sd.getIntProperty("orientW");
    			Quaternion q = new Quaternion(orientX, orientY, orientZ, orientW);
    			sd.setOrientation(q);
    		} else {
    			// location is a marker, we will need to get its location
    			Marker m = InstanceClient.getMarker(instanceOid, location);
    			sd.setLoc(m.getPoint());
    			sd.setOrientation(m.getOrientation());
    		}
    		if(Log.loggingDebug)
    	           Log.debug("ARENA: finished location setting for spawn Num: " + spawnDataNum + " for arena id: " + arenaID);
    		sd.setInstanceOid(instanceOid);
    		String factoryName = createMobFactory(sd);
    		if (! factoryName.equals("")) {
    			sd.setFactoryName(factoryName);
    			sd.setProperty("arenaID", arenaID);
        		MobManagerClient.createSpawnGenerator(sd);
    		}
    	    return true;
    	}
    }
    
    public static boolean despawnArenaCreature(OID oid) {
    	ObjectStub obj = arenaSpawns.get(oid);
    	if (obj != null) {
    		obj.despawn(false);
    		ObjectManagerClient.unloadObject(oid);
    		arenaSpawns.remove(oid);
    		return true;
    	}
    	return false;
    }
    
    /**
     * Spawns a creature in the specified dome. Gives the creature the domeID so when it dies the system
     * can track it.
     */
	@Deprecated
    class SpawnDomeMobHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage SPMsg = (ExtensionMessage) msg;
            SpawnData sd = (SpawnData) SPMsg.getProperty("spawnData");
            int spawnType = (Integer) SPMsg.getProperty("spawnType");
            if (Log.loggingTrace) Log.trace("DOME: got spawn dome mob message");
            int templateID = sd.getRandomTemplateID();

            if (Log.loggingDebug)
                Log.debug("MOB: creating mob factory for template: " + templateID);
            Template tmpl = ObjectManagerClient.getTemplate(templateID, ObjectManagerPlugin.MOB_TEMPLATE);
            if (tmpl == null) {
                Log.error("MOB: template [" + templateID + "] doesn't exist.");
                return true;
            }

            MobFactory cFactory = new MobFactory(templateID);

	    // Add BaseBehavior using a supplier
	    cFactory.addBehavSupplier((obj, spawnData) -> new BaseBehavior());
		
            if (spawnType == -4 /*Dome.MOBTYPE_LOOT*/) {
                // Capture loot tables
                HashMap<Integer, MobLootTable> lootTables = (HashMap<Integer, MobLootTable>) sd.getProperty("lootTables");
            if (lootTables == null)
            	lootTables = new HashMap<>();
            tmpl.put(InventoryClient.NAMESPACE, "lootTables", lootTables);

            	// 	Loot tables remain accessed via the template
            	cFactory.addBehavSupplier((obj, spawnData) -> new LootBehavior());
                // Avoid modifying the template; pass loot tables directly to the behavior
                /*cFactory.addBehavSupplier((obj, spawnData) -> {
                    LootBehavior lBehav = new LootBehavior();
                    lBehav.setLootTables((HashMap<Integer, MobLootTable>) tmpl.get(InventoryClient.NAMESPACE, "lootTables"));
                    return lBehav;
                });*/
            }

            int roamRadius = (Integer) SPMsg.getProperty("roamRadius");
            if (roamRadius > 0) {

             // Example for RadiusRoamBehavior
                cFactory.addBehavSupplier((obj, spawnData) -> {
                    RadiusRoamBehavior rrBehav = new RadiusRoamBehavior();
                    rrBehav.setRadius(roamRadius);
                    rrBehav.setCenterLoc(spawnData.getLoc()); // Get location from the object instance
                    //rrBehav.setMovementSpeed((float) tmpl.get(WorldManagerClient.NAMESPACE, "speed_walk"));
                    return rrBehav;
                });

            }

            String factoryName = templateID + "Factory" + numFactories;
            if (Log.loggingDebug)
                Log.debug("BEHAV: registering factory for mob: " + templateID);
            ObjectFactory.register(factoryName, cFactory);
            numFactories++;
            sd.setFactoryName(factoryName);
            MobManagerClient.createSpawnGenerator(sd);
            Log.debug("DOME: spawned dome mob");
            return true;
        }
    }

    
    /**
     * Spawns a pet of the specified type for the specified player.
     */
    
	class SpawnPetHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			AgisMobClient.spawnPetMessage SPMsg = (AgisMobClient.spawnPetMessage) msg;
			OID oid = SPMsg.getSubject();
			if (Log.loggingDebug)Log.debug("SpawnPetHook oid="+oid);
			OID plyOid = (OID) SPMsg.getProperty("plyOid");
			Long duration = (Long) SPMsg.getProperty("duration");
			int petType = (Integer) SPMsg.getProperty("petType");
			int passiveEffect = (Integer) SPMsg.getProperty("passiveEffect");
			int skillType = (Integer) SPMsg.getProperty("skillType");
			if (Log.loggingDebug)
				log.debug("SpawnPetHook petType:" + petType+" passiveEffect:"+passiveEffect+" skillType:"+skillType);
	
			if (petType == 2) {
				int mobID = (Integer) SPMsg.getProperty("mobID");
				if (Log.loggingDebug)
					log.debug("Spawn NonCombatPet mobID:" + mobID);
				spawnNonCombatPet(mobID, plyOid, duration);
			} else if (petType == 3) {
				int mobID = (Integer) SPMsg.getProperty("mobID");
				if (Log.loggingDebug)
					log.debug("Spawn CombatPet mobID:" + mobID);
				spawnCombatPet(mobID, plyOid, duration, passiveEffect, skillType);
			} else if (petType == 4) {
				String mobID = (String) SPMsg.getProperty("mobID");
				if (Log.loggingDebug)
					log.debug("Spawn Tamed CombatPet mobID:" + mobID);
				spawnCapturedCombatPet(plyOid, mobID);
			}
			if (Log.loggingDebug)
				Log.debug("SpawnPetHook: end");
			return true;
		}
	}
    
    /**
     * Spawns a non combat pet that follows the owner around. If there is another non combat pet that the player
     * already has spawned, that pet will be despawned first.
     * @param templateName
     * @param ownerOid
     * @return
     */
    HashMap<OID, NonCombatPet> stackNonCombatPet = new  HashMap<OID, NonCombatPet>();
    
    private boolean spawnNonCombatPet(int templateID, OID ownerOid, Long duration) {
    	if(Log.loggingDebug)
    	    log.debug("spawnNonCombatPet templateID:"+templateID+" ; ownerOid:"+ownerOid);
    	OID oldPetOid = (OID) EnginePlugin.getObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "nonCombatPet");
    	NonCombatPet oldPet = null;
    	if (oldPetOid!=null)
    		if (stackNonCombatPet.containsKey(oldPetOid))
    			oldPet = stackNonCombatPet.get(oldPetOid);
    	if (oldPet != null) {
    		EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "nonCombatPet", null);
    		//EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "hasPet", false);
    		oldPet.despawnPet();
    		// If we are trying to spawn the same pet, it means we are actually wanting to despawn the old one, so lets respawn now that it is done
    		if (oldPet.getMobTemplateID() == templateID) {
    			Log.debug("PET: despawned old ncPet and now setting the player property to null");
    			return true;
    		}
    	} 
   
       NonCombatPet ncPet = new NonCombatPet(templateID, ownerOid, false, duration,  ownerOid);
       stackNonCombatPet.put(ncPet.getOid(),ncPet);
	     // Save the NonCombatPet object to a player property
     //   EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "nonCombatPet", ncPet);
    	return true;
       
    }
    
    /**
     * Spawns a combat pet that the owner can command. If the player already has a combat pet then it will just
     * call the summonPet function for that CombatPet instance, otherwise it will generate a new instance.
     * @param templateName
     * @param ownerOid
     * @return
     */
   public static HashMap<OID, CombatPet> stackCombatPet = new  HashMap<OID, CombatPet>();
    
    private boolean spawnCombatPet(int mobID, OID ownerOid, Long duration, int passiveEffect, int skillType) {
    	if(Log.loggingDebug)
    		log.debug("PET: spawn combat pet hit with owner: " + ownerOid);
//		Serializable activePetOid = EnginePlugin.getObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "activePet");
		Serializable _activePetList = EnginePlugin.getObjectProperty(ownerOid, CombatClient.NAMESPACE, "activePetList");
		if(Log.loggingDebug) log.debug("PET: _activePetList="+_activePetList);
		if(_activePetList==null)
			_activePetList = new HashMap<OID,Integer>();
		HashMap<OID,Integer> activePetList = (HashMap<OID,Integer>) _activePetList;
		if(Log.loggingDebug) log.debug("PET: activePetList="+activePetList);

		HashMap<Integer, ArrayList<CombatPet>> pets = new HashMap<>();
		int totalCount=0;
		for(OID pet : activePetList.keySet()){
			if (stackCombatPet.containsKey(pet)) {
				CombatPet cp = stackCombatPet.get(pet);
//				PetProfile pp = Agis.PetProfile.get(cp.getPetProfile());
				pets.computeIfAbsent(cp.getPetCountStat(), __ -> new ArrayList<CombatPet>()).add(cp);
				totalCount++;
			}
		}
		LinkedList<String> clist = new LinkedList<String>();
		clist.add("petStats");
        HashMap<String, Serializable> r = CombatClient.getCombatInfoParams(ownerOid, clist);
if(Log.loggingDebug){
	Log.debug("PET: player params "+r);
	for (String key : r.keySet()) {
		Log.debug("PET: player param "+key+"=>"+r.get(key));
	}
}


		 int globalCount = (int) r.get(CombatPlugin.PET_GLOBAL_COUNT_STAT);
		if(Log.loggingDebug) log.debug("PET: totalCount="+totalCount+" globalCount="+globalCount);
		 if(globalCount <= totalCount){
			 //Limits of pets
			 //Despawn or do nothing
			log.error("PET: global limit reached");
			ExtendedCombatMessages.sendErrorMessage(ownerOid, "PET_GLOBAL_LIMIT");
			return false;
		 }
		if(Log.loggingDebug) log.debug("PET: PetProfile="+mobID);
		PetProfile pp = Agis.PetProfile.get(mobID);
		AtavismDatabase adb = new AtavismDatabase();
		int level = adb.loadPlayerPetLevel(ownerOid,mobID);
		if(Log.loggingDebug) log.debug("PET: PetProfile="+mobID+" level="+level);

		int mobTemplateID = -1;
		if(level <= 0){
			PetProfileLevel ppl = pp.levels.get(1);
			mobTemplateID = ppl.getTemplateId();
		} else {
			PetProfileLevel ppl = pp.levels.get(level);
			mobTemplateID = ppl.getTemplateId();
		}
		if(Log.loggingDebug) log.debug("PET: mobTemplateID="+mobTemplateID);
		if(mobTemplateID < 0){
			log.error("PET: cant spawn mot template -1");
			return false;
		}
		Template tmpl = ObjectManagerClient.getTemplate(mobTemplateID, ObjectManagerPlugin.MOB_TEMPLATE);
		int petCountStat =(Integer) tmpl.get(CombatClient.NAMESPACE,"petCountStat");
		if(Log.loggingDebug) log.debug("PET: mobTemplateID="+mobTemplateID+" petCountStat="+petCountStat);
		if(petCountStat > 0) {
			if (r.containsKey(CombatPlugin.STAT_NAMES.get(petCountStat))) {
				int petTypeCountLimit = (int) r.get(CombatPlugin.STAT_NAMES.get(petCountStat));
				if(Log.loggingDebug) log.debug("PET: type "+petCountStat+":"+CombatPlugin.STAT_NAMES.get(petCountStat)+ "limit "+petTypeCountLimit+" PET_ALLOW_TYPE_LIMIT_EXCEEDED="+PET_ALLOW_TYPE_LIMIT_EXCEEDED);
				if(!PET_ALLOW_TYPE_LIMIT_EXCEEDED && petTypeCountLimit <= pets.getOrDefault(petCountStat,new ArrayList<>()).size()) {
					ExtendedCombatMessages.sendErrorMessage(ownerOid, "PET_TYPE_LIMIT");
					log.error("PET: type "+petCountStat+":"+CombatPlugin.STAT_NAMES.get(petCountStat)+ "limit reached ");
				    return true;
				}
			}
		}


    	CombatPet cPet = new CombatPet(mobID, mobTemplateID, ownerOid, duration, passiveEffect);
		stackCombatPet.put(cPet.getOid(),cPet);
    	return true;
    }
    
    /**
     * Spawns a combat pet that the owner can command. If the player already has a combat pet then it will just
     * call the summonPet function for that CombatPet instance, otherwise it will generate a new instance.
     * @param petRef
     * @param ownerOid
     * @return
     */
    private boolean spawnCapturedCombatPet(OID ownerOid, String petRef) {
    	if(Log.loggingDebug)
    	    	Log.debug("PET: spawn captured combat pet hit with owner: " + ownerOid + " and pet ref: " + petRef);
    	TamedPet pet = (TamedPet) ObjectManagerClient.loadObjectData(petRef);
    	if (pet != null) {
    		// Do a check to see if the owner has any existing pets
    		OID activePet = (OID) EnginePlugin.getObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "activePet");
    		String petKey = (String) EnginePlugin.getObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "combatPet");
    		if (activePet != null && petKey == null) {
    			// Shall we despawn the old pet, or tell the player they need to dismiss the old pet first?
    			WorldManagerClient.despawn(activePet);
    		}
        	if (petKey != null) {
        		TamedPet oldPet = (TamedPet) ObjectManagerClient.loadObjectData(petKey);
        		oldPet.despawnPet();
        		if (!oldPet.getName().equals(pet.getName())) {
        			pet.summonPet();
        		}
        	} else {
        		pet.summonPet();
        	}
    	}
    	return true;
    }
    
    /**
     * Tames a combat pet that the owner can command. If the player already has a combat pet then it will just
     * call the summonPet function for that CombatPet instance, otherwise it will generate a new instance.
     */
    class TameBeastHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    	    AgisMobClient.tameBeastMessage SPMsg = (AgisMobClient.tameBeastMessage) msg;
    	    OID oid = SPMsg.getSubject();
    	    OID mobOid = (OID) SPMsg.getProperty("mobOid");
    	    int skillType = (Integer) SPMsg.getProperty("skillType");
    	    if(Log.loggingDebug)
    	             Log.debug("PET: tame beast hook hit with target: " + mobOid);
    	  	int mobID = (Integer) EnginePlugin.getObjectProperty(mobOid, WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_ID);
        	String mobName = WorldManagerClient.getObjectInfo(mobOid).name;
        	String objectKey = generateObjectKey("pet");
        	TamedPet cPet = new TamedPet(objectKey, mobID, mobName, oid, skillType);
            cPet.setPersistenceFlag(true);
            ObjectManagerClient.saveObjectData(objectKey, cPet, WorldManagerClient.NAMESPACE);
            // Create a new item to store the object key
            String petItemName = "Whistle (for " + cPet.getMobName() + ")";
            HashMap<String, Serializable> itemProps = new HashMap<String, Serializable>();
            itemProps.put("petRef", objectKey);
            AgisInventoryClient.generateItem(oid, PET_WHISTLE, petItemName, 1, itemProps);
            // Save the NonCombatPet object to a player property
            //EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "CombatPet", cPet);
            ExtendedCombatMessages.sendAnouncementMessage(oid, "You have tamed a pet!", "");
    	    return true;
    	}
    }
    
    class VerifyIslandAccessHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ExtensionMessage verifyIslandAccessMessage = (ExtensionMessage) msg;
    	    OID oid = verifyIslandAccessMessage.getSubject();
    	    int world = (Integer) verifyIslandAccessMessage.getProperty("world");
    	    String password = (String) verifyIslandAccessMessage.getProperty("password");
    	    OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
    	    if(Log.loggingDebug)
    	              Log.debug("VerifyIslandAccess hit with world: " + world);
    	    
    	    TargetedExtensionMessage verifyResponse =
                new TargetedExtensionMessage(oid, oid);
    	    verifyResponse.setExtensionType("world_access_response");
            
            // Determine whether the user has access to the specified island - and if yes, do they 
            // have developer access?
            boolean hasAccess = true;
            boolean isDeveloper = false;
            boolean isAdmin = false;
            InstanceTemplate island = InstanceClient.getInstanceTemplate(world) ;
            		//instanceTemplates.get(world);
            if (!island.getIsPublic() && !island.getAdministrator().equals(accountID)
    				&& !island.getDevelopers().contains(accountID))
            {
            	hasAccess = false;
            }
            if (!island.getPassword().equals(password))
            	hasAccess = false;
            verifyResponse.setProperty("world", world);
            verifyResponse.setProperty("hasAccess", hasAccess);
            verifyResponse.setProperty("isDeveloper", isDeveloper);
            verifyResponse.setProperty("isAdmin", isAdmin);
            Engine.getAgent().sendBroadcast(verifyResponse);
    	    return true;
    	}
    }
    
    
    
   
	class RequestIslandDeveloperAccessHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage requestAccessMessage = (ExtensionMessage) msg;
			OID oid = requestAccessMessage.getSubject();
			OID instanceOid = WorldManagerClient.getObjectInfo(oid).instanceOid;
			String world = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_NAME).templateName;
			OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
			int worldId = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "world");
			if(Log.loggingDebug)
		           Log.debug("RequestIslandDeveloperAccess hit with instanceOid: " + instanceOid + " world: " + world + " and account id: " + accountID);

			// Determine whether the user has access to the specified island - and if yes,
			// do they
			// have developer access?
			boolean hasAccess = true;
			InstanceTemplate island = InstanceClient.getInstanceTemplate(worldId);
			// instanceTemplates.get(world);
			if (island == null)
				return true;
			if (!island.getIsPublic() && !island.getAdministrator().equals(accountID) && !island.getDevelopers().contains(accountID)) {
				hasAccess = false;
			}

			TargetedExtensionMessage verifyResponse = new TargetedExtensionMessage(oid, oid);
			verifyResponse.setExtensionType("world_developer_response");
			boolean isDeveloper = false;
			boolean isAdmin = false;
			if (island.getAdministrator().equals(accountID) && hasAccess)
				isAdmin = true;
			if (island.getDevelopers().contains(accountID) && hasAccess)
				isDeveloper = true;
			verifyResponse.setProperty("isDeveloper", isDeveloper);
			verifyResponse.setProperty("isAdmin", isAdmin);
			Engine.getAgent().sendBroadcast(verifyResponse);
			if (isDeveloper)
				sendIslandBuildingData(oid);
			return true;
		}
	}

    /**
     * Sends data down about the island the user is currently building on. Contains information such as
     * number of spawns and the limit, whether the island is public etc.
     * @param oid
     */
	private void sendIslandBuildingData(OID oid) {
		int world = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "world");
		OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
		if(Log.loggingDebug)
	           Log.debug("SendIslandBuildingData hit with world: " + world);

		// Determine whether the user has developer access
		InstanceTemplate island = InstanceClient.getInstanceTemplate(world);
		// instanceTemplates.get(world);
		if (island == null)
			return;
		if (!accountHasDeveloperAccess(oid, accountID, world))
			return;

		TargetedExtensionMessage markerResponse = new TargetedExtensionMessage(oid, oid);
		markerResponse.setExtensionType("island_building_data");
		markerResponse.setProperty("name", island.getName());
		markerResponse.setProperty("isPublic", island.getIsPublic());
		markerResponse.setProperty("content_packs", island.getContentPacks());
		markerResponse.setProperty("subscription", island.getSubscriptionActive());
		markerResponse.setProperty("numSpawns", mobDataBase.getSpawnCount(island.getID()));

		Engine.getAgent().sendBroadcast(markerResponse);
	}
   
    class UpdatePortalHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ExtensionMessage portalMsg = (ExtensionMessage)msg;
    		OID oid = OID.fromLong((Long)portalMsg.getProperty("playerOid"));
    		OID instanceOid = WorldManagerClient.getObjectInfo(oid).instanceOid;
    		int world = (Integer)  EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "world");
    	    OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
    		
    		if (!accountHasDeveloperAccess(oid, accountID, world))
    			return true;
    		
            int portalID = (Integer)portalMsg.getProperty("portalID");
            String portalName = (String)portalMsg.getProperty("portalName");
            if(Log.loggingDebug)
                 Log.debug("UPDATEPORTAL: got update for portal: " + portalID);
            
    	   	AOVector loc = (AOVector) portalMsg.getProperty("loc");
    	   	Point p = new Point((int)loc.getX(), (int)loc.getY(), (int)loc.getZ());
    	   	Quaternion orient = (Quaternion) portalMsg.getProperty("orient");
    	   	int faction = (Integer) portalMsg.getProperty("faction");
    	   	HashMap<String, Float> portalProps = new HashMap<String, Float>();
    	   	portalProps.put("portalType", (float)1);
			portalProps.put("faction", (float)faction);
			portalProps.put("displayID", (float)LEGION_PORTAL_DISPLAY_ID);
			portalProps.put("locX", p.getX());
			portalProps.put("locY", p.getY());
			portalProps.put("locZ", p.getZ());
			portalProps.put("orientX", orient.getX());
			portalProps.put("orientY", orient.getY());
			portalProps.put("orientZ", orient.getZ());
			portalProps.put("orientW", orient.getW());
			portalProps.put("id", (float)portalID);
			InstanceTemplate island =InstanceClient.getInstanceTemplate(world) ;
					//instanceTemplates.get(world);
    	    island.updatePortal(portalName, portalProps);

			aDB.editPortalData(portalName, portalProps);
    	    return true;
    	}
    }
    
	public static String generateObjectKey(String prefix) {
		Calendar currentTime = Calendar.getInstance();
		String objectKey = prefix + "_" + currentTime.getTimeInMillis();
		return objectKey;
	}
	
	/*
     * NavMesh Loading/Updating
     */
    
    /**
     * Loads in the NavMesh and other world information. Sends out the SpawnInstanceObjects message when finished.
     */
    class LoadInstanceObjectsHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		SubjectMessage message = (SubjectMessage) msg;
    		log.debug("AgisMobPlugin.LoadInstanceObjectsHook");
            OID instanceOid = message.getSubject();
            if(Log.loggingDebug)
                log.debug("AgisMobPlugin.LoadInstanceObjectsHook "+instanceOid);
    	     int instanceID = (Integer)InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
    	     InstanceTemplate iTmpl = InstanceClient.getInstanceTemplate(instanceID) ; 
     		//AgisMobClient.getInstanceTemplate(instanceID);
    	    String instanceName = iTmpl.getName();
    	    //instanceTemplates.get(instanceID).getName();
    	    InstanceNavMeshManager navMeshManager = new InstanceNavMeshManager(instanceName, instanceOid);
    	    instanceNavMeshes.put(instanceOid, navMeshManager);
    	    if (Log.loggingTrace) log.trace("AgisMobPlugin.LoadInstanceObjectsHook instanceName:"+instanceName+" ");
    	   
    	    // Load in interactive objects
    	    ContentDatabase cDB = new ContentDatabase(false);
    	    interactiveObjects.put(instanceOid, cDB.loadInteractiveObjects(instanceID, instanceOid));
    	    
    	    AgisMobClient.spawnInstanceObjects(iTmpl, instanceOid);
            log.debug("AgisMobPlugin.LoadInstanceObjectsHook END");
            return true;
    	}
    }
    public static void RemoveInteractiveObject(OID instanceOid, int id){
		log.debug("AgisMobPlugin.RemoveInteractiveObject instance="+instanceOid+" id="+id);
		if(interactiveObjects.containsKey(instanceOid)){
			log.debug("AgisMobPlugin.RemoveInteractiveObject hes IO id"+interactiveObjects.get(instanceOid).containsKey(id));
			interactiveObjects.get(instanceOid).remove(id);
			log.debug("AgisMobPlugin.RemoveInteractiveObject removed");
		}
		log.debug("AgisMobPlugin.RemoveInteractiveObject END");
	}
	
	/*
	class PlayCoordinatedEffectHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	ExtensionMessage gridMsg = (ExtensionMessage)msg;
        	OID playerOid = gridMsg.getSubject();
        	String coordEffect = (String)gridMsg.getProperty("coordEffect");
        	OID targetOid = null;
        	boolean hasTarget = (Boolean)gridMsg.getProperty("hasTarget");
        	if (hasTarget) {
        		long targetOID = (Long)gridMsg.getProperty("targetOid");
        		targetOid = OID.fromLong(targetOID);
        	}
        	// Send down coord effect
        	CoordinatedEffect effect = new CoordinatedEffect(coordEffect);
            effect.sendSourceOid(true);
            if (hasTarget) {
            	effect.sendTargetOid(true);
            	effect.invoke(playerOid, targetOid);
            } else {
            	effect.invoke(playerOid, playerOid);
            }
            return true;
        }
	}*/
	public static boolean isNavMeshForInstance(OID instanceOid){
		return interactiveObjects.containsKey(instanceOid);
	}


    public static Point findNearestPoint(OID obj, Point p, OID instanceOid) {
    	if (Log.loggingDebug)Log.debug("findNearestPoint: Got obj="+obj+" point="+p+" instanceOid="+instanceOid);
		Point pos = null;
		 if (instanceNavMeshes.containsKey(instanceOid)) {
	            DetourActor da = instanceNavMeshes.get(instanceOid).getDetourActorByOid(obj);
	            if (da != null) {
	            	 pos = da.findNearestPoint(p);
	            }else {
	            	if (Log.loggingDebug)Log.debug("DeturActor is null no NavMesh");
	            	pos = p;
	            }
	        }else {
	        	if (Log.loggingDebug)Log.debug("findNearestPoint:  instance not found");
	        }
		if (Log.loggingDebug)Log.debug("findNearestPoint: Got meaasge findNearestPoint pos:" + pos+" for "+p);
		return pos;
    }
    
	
	class FindNearestPointHook implements Hook {
		public boolean processMessage(Message msg, int arg1) {
			if (Log.loggingDebug)Log.debug("FindNearestPointHook: Got  ");
			
			
			PropertyMessage _msg = (PropertyMessage) msg;
			OID obj = _msg.getSubject();
			Point p = (Point) _msg.getProperty("point");
			OID instanceOid = (OID) _msg.getProperty("instanceOID");
			if (Log.loggingDebug)Log.debug("FindNearestPointHook: Got obj="+obj+" point="+p+" instanceOid="+instanceOid);
			Point pos = null;
			 if (instanceNavMeshes.containsKey(instanceOid)) {
		           // pos = instanceNavMeshes.get(instanceOid).findNearestPoint(p);
		            DetourActor da = instanceNavMeshes.get(instanceOid).getDetourActorByOid(obj);
		            if (da != null) {
		            	 pos = da.findNearestPoint(p);
		            }else {
		            	if (Log.loggingDebug)Log.debug("DeturActor is null no NavMesh");
		            	pos = p;
		            }
		        }else {
		        	if (Log.loggingDebug)Log.debug("FindNearestPointHook:  instance not found");
		        }
		//	if (Log.loggingTrace)
			if (Log.loggingDebug)Log.debug("FindNearestPointHook: Got meaasge findNearestPoint pos:" + pos+" for "+p);
			Engine.getAgent().sendObjectResponse(msg, pos);
			if (Log.loggingDebug)Log.debug("FindNearestPointHook: End  ");
	    	return true;
		}

		//Log.error("DetourActor.checkVisibility: Start");
     //   return navMeshManager.checkVisibility(node.getCurrentLoc(), pos);	
	
	}

	
	class GetActorSpeedHook implements Hook {
		public boolean processMessage(Message msg, int arg1) {
			if (Log.loggingDebug)
				Log.debug("GetActorSpeedHook: Start");
			PropertyMessage _msg = (PropertyMessage) msg;
			OID obj = _msg.getSubject();
			OID instanceOid = (OID) _msg.getProperty("instanceOID");
			float speed = Float.NaN;
			if (instanceNavMeshes.containsKey(instanceOid)) {
				DetourActor da = instanceNavMeshes.get(instanceOid).getDetourActorByOid(obj);
				if (da != null) {
					speed = da.getActorSpeed(obj);
				} else {
					if (Log.loggingDebug)
						Log.debug("DeturActor is null no NavMesh");
				}
			} else {
				if (Log.loggingDebug)
					Log.debug("GetActorSpeedHook:  instance not found");
			}
			if(Float.isNaN(speed)){
				log.error("GetActorSpeedHook speed is NaN for oid="+obj);
			}
			Engine.getAgent().sendObjectResponse(msg, speed);
			if (Log.loggingDebug)
				Log.debug("GetActorSpeedHook: End");

			return true;
		}
	}
	
	/**
	 * 
	 * @param obj
	 * @param p
	 * @param instanceOid
	 * @return
	 */
	public static ArrayList<AOVector> GetPath(OID obj, Point p, OID instanceOid) {
		if (Log.loggingDebug)
			Log.debug("GetPath: Got obj=" + obj + " point=" + p + " instanceOid=" + instanceOid + " " + instanceNavMeshes.keySet());
		ArrayList<AOVector> path = null;
		if (instanceNavMeshes.containsKey(instanceOid)) {
			DetourActor da = instanceNavMeshes.get(instanceOid).getDetourActorByOid(obj);
			if (da != null) {
				path = da.GetPath(p);
			} else {
				// log.error("DeturActor is null no NavMesh");
				ObjectStub objStub = (ObjectStub) EntityManager.getEntityByNamespace(obj, Namespace.MOB);
				if(objStub == null){
					if (Log.loggingDebug)
						Log.debug("GetPath: Got obj=" + obj + " point=" + p + " instanceOid=" + instanceOid + " cant get ObjectStub" );
					return null;
				}
				InterpolatedWorldNode node = (InterpolatedWorldNode) objStub.getWorldNode();
				path = new ArrayList<AOVector>();
				Point loc = node.getCurrentLoc();
				if (loc != null) {
					path.add(new AOVector(loc));
					path.add(new AOVector(p));
				}
			}
		}
		if (Log.loggingDebug)
			Log.debug("GetPath: End path:" + path);
		return path;

	}

	class GetPathHook implements Hook {
		public boolean processMessage(Message msg, int arg1) {
			if (Log.loggingDebug)Log.debug("GetPathHook: Got  ");
			
			
			PropertyMessage _msg = (PropertyMessage) msg;
			OID obj = _msg.getSubject();
			Point p = (Point) _msg.getProperty("point");
			OID instanceOid = (OID) _msg.getProperty("instanceOID");
			if (Log.loggingDebug)Log.debug("GetPathHook: Got obj="+obj+" point="+p+" instanceOid="+instanceOid+" "+instanceNavMeshes.keySet());
			ArrayList<AOVector> path = null;
			 if (instanceNavMeshes.containsKey(instanceOid)) {
		           // pos = instanceNavMeshes.get(instanceOid).findNearestPoint(p);
		            DetourActor da = instanceNavMeshes.get(instanceOid).getDetourActorByOid(obj);
		            if (da != null) {
		            	 path = da.GetPath(p);
		            }else {
		            	//log.error("DeturActor is null no NavMesh");
		            	ObjectStub objStub = (ObjectStub) EntityManager.getEntityByNamespace(obj, Namespace.MOB);
		            	InterpolatedWorldNode node = (InterpolatedWorldNode) objStub.getWorldNode();
		            	path = new ArrayList<AOVector>();
	                    Point loc = node.getCurrentLoc();
	                    if (loc != null) {
	                        path.add(new AOVector(loc));
	                        path.add(new AOVector(p));
	                    }
			            }
		        }
		//	if (Log.loggingTrace)
			 if (Log.loggingDebug)Log.debug("GetPathHook:  path:" + path);
			Engine.getAgent().sendObjectResponse(msg, path);
			if (Log.loggingDebug)Log.debug("GetPathHook: End  ");
	    	return true;
		}

		//Log.error("DetourActor.checkVisibility: Start");
     //   return navMeshManager.checkVisibility(node.getCurrentLoc(), pos);	
	
	}


	class CheckVisibilityHook implements Hook {
		public boolean processMessage(Message msg, int arg1) {
			log.debug("CheckVisibilityHook: Got meaasge CheckVisibilityMessage ");
			CheckVisibilityMessage check_msg = (CheckVisibilityMessage) msg;
			if(Log.loggingDebug)Log.debug("CheckVisibilityHook Subject="+check_msg.getSubject()+" Instance="+check_msg.getInstance()+" Point="+check_msg.getPoint()+" Points="+check_msg.getPoints()+" Source Point="+check_msg.getSourcePoint()+" Voxel="+check_msg.getVoxel()+" ObjOid="+check_msg.getObjOid());
			if(Log.loggingDebug)Log.debug("CheckVisibilityHook Subject="+check_msg.getSubject()+" check_msg="+check_msg);
			if(check_msg.getVoxel()){
				boolean respon = checkVisibilityVoxel(check_msg);
			} else {
	    		boolean respon = checkVisibility(check_msg);
				Engine.getAgent().sendObjectResponse(msg, respon);
			}
			log.debug("CheckVisibilityHook: CheckVisibilityMessage END");
	    	return true;
		}

		//Log.error("DetourActor.checkVisibility: Start");
     //   return navMeshManager.checkVisibility(node.getCurrentLoc(), pos);	
	
	}
	private boolean checkVisibilityVoxel(CheckVisibilityMessage check_msg) {
		if(Log.loggingDebug)Log.debug("CheckVisibilityHook.checkVisibilityVoxel Subject="+check_msg.getSubject()+" check_msg="+check_msg);

		Point pos = check_msg.getPoint();
		OID instanceOid = check_msg.getInstance();
		// OID obj = check_msg.getSubject();
		OID obj = check_msg.getObjOid();
		if (Log.loggingDebug)
			log.debug("checkVisibilityVoxel: Point="+pos+" instanceOid="+instanceOid+" obj="+obj+" sPoint="+check_msg.getSourcePoint()+" Points="+check_msg.getPoints());
		boolean respon = true;
		if (check_msg.getSubject().equals(instanceOid)) {
			log.debug("checkVisibilityVoxel: subject is instance");
			if (check_msg.getPoint() != null) {
				log.debug("checkVisibilityVoxel: target point");
				if (instanceNavMeshes.containsKey(instanceOid)) {
					Optional<Float> hit = instanceNavMeshes.get(instanceOid).checkVisibilityNew(check_msg.getSourcePoint(), pos);
					if(check_msg.getReturnHitPoint()) {
						if(hit.isPresent()){
							float[] startPos = new float[] { check_msg.getSourcePoint().getX(), check_msg.getSourcePoint().getY()+1f, check_msg.getSourcePoint().getZ() };
							float[] endPos = new float[] { pos.getX(), pos.getY()+1f, pos.getZ() };
							float[] raycastHitPos = hit.map(t -> new float[]{startPos[0] + t * (endPos[0] - startPos[0]), startPos[1] + t * (endPos[1] - startPos[1]),	startPos[2] + t * (endPos[2] - startPos[2])}).orElse(endPos);
							log.debug("checkVisibilityVoxel: check result hit"+(new Point(raycastHitPos[0],raycastHitPos[1]-1f,raycastHitPos[2])));
							Engine.getAgent().sendObjectResponse(check_msg, new Point(raycastHitPos[0],raycastHitPos[1]-1f,raycastHitPos[2]));
						}else{
							log.debug("checkVisibilityVoxel: check result no hit");
							Engine.getAgent().sendObjectResponse(check_msg, new Point());
						}
						return respon;
					}else{
						respon = !hit.isPresent();
					}
					log.debug("checkVisibilityVoxel: check result="+respon);
				}else{
					log.debug("checkVisibilityVoxel: not found instance");
				}

			} else if (check_msg.getPoints() != null) {
				log.debug("checkVisibilityVoxel: targets points");
				ArrayList<OID> list = new ArrayList<OID>();
				HashMap<OID,Point> list2 = new HashMap<OID,Point>();
				check_msg.getPoints().forEach((k, v) -> {
					if (instanceNavMeshes.containsKey(instanceOid)) {
						Optional<Float> hit = instanceNavMeshes.get(instanceOid).checkVisibilityNew(check_msg.getSourcePoint(), v);
						if(Log.loggingDebug)log.debug("checkVisibilityVoxel: list check oid="+k+" result="+(!hit.isPresent()));
						if(check_msg.getReturnHitPoint()) {
							if(hit.isPresent()){
								float[] startPos = new float[] { check_msg.getSourcePoint().getX(), check_msg.getSourcePoint().getY()+1f, check_msg.getSourcePoint().getZ() };
								float[] endPos = new float[] { pos.getX(), pos.getY()+1f, pos.getZ() };
								float[] raycastHitPos = hit.map(t -> new float[]{startPos[0] + t * (endPos[0] - startPos[0]), startPos[1] + t * (endPos[1] - startPos[1]),	startPos[2] + t * (endPos[2] - startPos[2])}).orElse(endPos);
								list2.put(k, new Point(raycastHitPos[0],raycastHitPos[1]-1f,raycastHitPos[2]));
							}else{
								list2.put(k, new Point());
							}
						}else{
							if(!hit.isPresent()){
								list.add(k);
							}
						}
					}else{
						log.debug("checkVisibilityVoxel: not found instance");
					}
				});
				if(check_msg.getReturnHitPoint()) {
					log.debug("checkVisibilityVoxel: check result hit list="+list2);

					Engine.getAgent().sendObjectResponse(check_msg, list2);
				}else {
					log.debug("checkVisibilityVoxel: check result oid list="+list);
					Engine.getAgent().sendObjectResponse(check_msg, list);
				}
				log.debug("checkVisibilityVoxel: targets end");
				return respon;
			}
		} else {
			log.debug("checkVisibilityVoxel: subject is not instance");
			if (instanceNavMeshes.containsKey(instanceOid)) {
				DetourActor da = instanceNavMeshes.get(instanceOid).getDetourActorByOid(obj);
				if (da != null) {
					respon = da.checkVisibility(pos);
				}
			}else{
				log.debug("checkVisibilityVoxel: not found instance");
			}
			Engine.getAgent().sendObjectResponse(check_msg, respon);
			return respon;
		}
		Engine.getAgent().sendObjectResponse(check_msg, respon);
		log.debug("checkVisibilityVoxel: end");
		return respon;
	}
	private boolean checkVisibility(CheckVisibilityMessage check_msg) {
		Point pos = check_msg.getPoint();
		OID instanceOid = check_msg.getInstance();
		// OID obj = check_msg.getSubject();
		OID obj = check_msg.getObjOid();
		if (Log.loggingDebug)
			log.debug("CheckVisibilityHook: Got meaasge CheckVisibilityMessage Point:" + pos+" instanceOid="+instanceOid+" sPoint="+check_msg.getSourcePoint()+" voxel="+check_msg.getVoxel()+" obj="+obj);
		boolean respon = true;
		if (check_msg.getVoxel()) {
			if (check_msg.getPoint() != null) {
				if (instanceNavMeshes.containsKey(instanceOid)) {
					respon = !instanceNavMeshes.get(instanceOid).checkVisibilityNew(check_msg.getSourcePoint(), pos).isPresent();
				}
			}
		} else {
			if (instanceNavMeshes.containsKey(instanceOid)) {
				DetourActor da = instanceNavMeshes.get(instanceOid).getDetourActorByOid(obj);
				if (da != null) {
					respon = da.checkVisibility(pos);
				}
			}
		}
		return respon;
	}

	/**
     * Checks if the player has Developer Access to the world, allowing them to modify spawns, and other world objects.
     * @param accountID
     * @param world
     * @return
     */
    public static boolean accountHasDeveloperAccess(OID characterOID, OID accountID, int world) {
    	// First check if the account is an admin account
    	int adminLevel = (Integer) EnginePlugin.getObjectProperty(characterOID, WorldManagerClient.NAMESPACE, "adminLevel");
    	if (adminLevel == AgisLoginPlugin.ACCOUNT_ADMIN) {
    		return true;
    	}
    	// If not, check if they are on the list of island developers
    	InstanceTemplate island =InstanceClient.getInstanceTemplate(world) ;  
    			//instanceTemplates.get(world);
        if (island == null) {
        	Log.debug("ACCESS: world: " + world + " does not exist");
        	return false;
        }
        if (!island.getAdministrator().equals(accountID) && !island.getDevelopers().contains(accountID)) {
        	Log.debug("ACCESS: player " + accountID.toString() + " does not have access to world: " + world);
        	return false;
        }
        return true;
    }
	
	/**
	 * Gets the dialogue matching the specified dialogueID. Should only be used within the mobserver process.
	 * @param dialogueID
	 * @return
	 */
	public static Dialogue getDialogue(int dialogueID) {
		if (Agis.DialogueManager.get(dialogueID) != null) {
			return Agis.DialogueManager.get(dialogueID);
		} else {
			Log.error("Dialogue: dialogueID: " + dialogueID + " not loaded");
		}
	/*	if (dialogues.containsKey(dialogueID)) {
			return dialogues.get(dialogueID);
		}*/
		return null;
	}
	
	/**
	 * Sets the Loot Object Template. If specified, the loot object template will be spawned with the mobs loot
	 * instead of the mob having the loot.
	 * @param tmpl
	 */
	public static void setLootObjectTmpl(int tmpl) {
    	lootObjectTmpl = tmpl;
    }
	
	// If specified, this template will be spawned and given the mobs loot rather than the mob itself
    public static int lootObjectTmpl = -1;
    
    /**
	 * Sets the Loot Object Despawn Time.
	 * @param duration
	 */
	public static void setLootObjectDespawn(int duration) {
		lootObjectDespawn = duration;
    }
	
	public static void AddShopBehavior(OID mob, ShopBehavior sb) {
		synchronized (shopBehaviors) {
			shopBehaviors.put(mob, sb);
		}
		if (shopsf == null) {
			log.error("ScheduledFuture is null start schedule RankingDataTick");
			PlayerShopTick timeTick = new PlayerShopTick();
			shopsf = Engine.getExecutor().scheduleAtFixedRate(timeTick, 1, 1, TimeUnit.MINUTES);
		}
	}
	
	public static void RemoveShopBehavior(OID mob) {
		synchronized (shopBehaviors) {
			shopBehaviors.remove(mob);
		}
	}
	static ScheduledFuture<?> shopsf = null;
	static class PlayerShopTick implements Runnable {
		public void run() {
			synchronized (shopBehaviors) {
				if (shopBehaviors.size() == 0) {
					Log.debug("PlayerShopTick messages: no data to send");
					return;
				}
				Log.debug("PlayerShopTick: Shop behaviors="+shopBehaviors.size());
				for(ShopBehavior sb : shopBehaviors.values()) {
					sb.ValidateShop();
				}
			}
		}
	}
	// If specified, this template will be spawned and given the mobs loot rather than the mob itself
    public static int lootObjectDespawn = 30;
	private AccountDatabase aDB;
	private ContentDatabase cDB;
	private MobDatabase mobDataBase;
    
    private static HashMap<OID, ShopBehavior> shopBehaviors = new HashMap<OID, ShopBehavior>();
	//public static HashMap<String, SmooSkin> skins = new HashMap<String, SmooSkin>();
	private static HashMap<Integer, SpawnData> spawnInfo = new HashMap<Integer, SpawnData>();
	//private static HashMap<Integer, InstanceTemplate> instanceTemplates = new HashMap<Integer, InstanceTemplate>();
	private static HashMap<Integer, ContentCategory> contentCategories = new HashMap<Integer, ContentCategory>();
	public static HashMap<OID, ObjectStub> arenaSpawns = new HashMap<OID, ObjectStub>();
	//private static HashMap<Integer, BuildingGrid> buildingGrids = new HashMap<Integer, BuildingGrid>();
	private static HashMap<Integer, ResourceGrid> resourceGrids = new HashMap<Integer, ResourceGrid>();
	//private static HashMap<Integer, Dialogue> dialogues = new HashMap<Integer, Dialogue>();
	//public static HashMap<Integer, MerchantTable> merchantTables = new HashMap<Integer, MerchantTable>();
	//public static HashMap<Integer, PatrolPoint> patrolPoints = new HashMap<Integer, PatrolPoint>();
	
	private static HashMap<OID, InstanceNavMeshManager> instanceNavMeshes = new HashMap<OID, InstanceNavMeshManager>();
	static HashMap<OID, HashMap<Integer, InteractiveObject>> interactiveObjects = new HashMap<OID, HashMap<Integer, InteractiveObject>>();
//	HashMap<OID, HashMap<Integer, InteractiveObject>> dynamicInteractiveObjects = new HashMap<OID, HashMap<Integer, InteractiveObject>>();

	public static ConcurrentHashMap<OID, AtomicInteger> interactiveObjectInstanceMaxId = new ConcurrentHashMap<OID, AtomicInteger>();
	public static HashMap<Integer, InteractiveObject> interactiveObjectProfiles = new HashMap<Integer, InteractiveObject>();

	public static Map<Integer,MobBehaviorProfile> mobCombatBehaviorProfiles = new HashMap<Integer,MobBehaviorProfile>();
	
	private static final int PET_WHISTLE = 12; // DEPRECATED
	public  static final int BASE_CATEGORY = 0;
	public static final String BEHAVIOR_TMPL_PROP = "behaviourTemplate";
	private static final int LEGION_PORTAL_DISPLAY_ID = 27; // DEPRECATED
	public static final int PORTAL_Y_OFFSET = 0; //750;
	private static final int ISLAND_TYPE_WORLD = 0;
	private static final int ISLAND_TYPE_ARENA = 2;
	
	// Used to convert milliseconds to seconds and vice versa
	public static final int TIME_MULTIPLIER = 1000;

	public static boolean MOB_FORCE_DESPAWN_IN_COMBAT = false;
	/**
	 * Interval between updates of colliders in dynamic Navmesh (in milliseconds)
	 */
	public static int DYNAMIC_NAVMESH_UPDATE_INTERVAL = 1000;
	public static boolean DYNAMIC_NAVMESH_UPDATE_SAVE = false;

	public static OID MOB_DEBUG_PLAYER = null;
	public static boolean MOB_DEATH_EXP = true; // Is EXP given on mob death?
	public static boolean EXP_BASED_ON_DAMAGE_DEALT = true; // Is EXP given from mobs death based on damage dealt to mob?
	public static boolean LOOT_BASED_ON_DAMAGE_DEALT = true; // Is Loot given from mobs death based on damage dealt to mob?
	public static float MOB_AGGRO_CLOSE_RANGE_CHECK = 2f; // How far away a target can be to still be considered within melee range for threat calculations
	public static float MOB_AGGRO_MELEE_THREAT_THRESHOLD = 1.1f; // How much additional threat a target must have if they are within melee range to overtake aggro
	public static float MOB_AGGRO_RANGED_THREAT_THRESHOLD = 1.3f; // How much additional threat a target must have if they are beyond melee range to overtake aggro
	public static float MOB_COMBAT_BEHAVIOR_FLEE_ALLIES_CHECK_MAX_DISTANCE = 40f;
	public static long MOB_COMBAT_BEHAVIOR_EVENT_TIMEOUT = 10_000L;
	
	public static float MOB_COMBAT_BEHAVIOR_NUMBER_TARGETS_CHECK_MAX_DISTANCE = 40f;
	//public static final float FOLLOW_DISTANCE = 1.5f;
	public static boolean LOOT_FOR_ALL = false;
	
	public static int MOB_SPAWN_DELAY = 10000; //Spawn start delay in millisecond
	public static int MOB_SPAWN_TICK = 300; //Spawn tick delay per object in millisecond
	
	/*
	 * MOB_COMBAT_BEHAVIOR_SELECT_INTERVAL interval in millisecond
	 */
	public static long MOB_COMBAT_BEHAVIOR_SELECT_INTERVAL = 1000;
	/*
	 * MOB_COMBAT_BEHAVIOR_DEFEND_CHANGE_TARGET_INTERVAL time in millisecond
	 */
	public static long MOB_COMBAT_BEHAVIOR_DEFEND_CHANGE_TARGET_INTERVAL = 10 * 1000;
	
	/*
	 * MOB_COMBAT_BEHAVIOR_USE_ABILITY_TIMEOUT time in millisecond
	 */
	public static long MOB_COMBAT_BEHAVIOR_USE_ABILITY_TIMEOUT = 20 * 1000;
	
	/*
	 * MOB_COMBAT_BEHAVIOR_USE_ABILITY_TIMEOUT time in millisecond
	 */
	public static long MOB_COMBAT_BEHAVIOR_CANT_USE_ABILITY_TIMEOUT = 20 * 1000;
	
	/*
	 * MOB_COMBAT_BEHAVIOR_MOVE_CHECK_INTERVAL time in millisecond
	 */
	public static long MOB_COMBAT_BEHAVIOR_MOVE_CHECK_INTERVAL = 250L;

	/*
	 * INTERACTIVE_OBJECT_PERCEPTION_RADIUS
	 */
	public static int INTERACTIVE_OBJECT_PERCEPTION_RADIUS = 75;

	public static boolean PET_ALLOW_TYPE_LIMIT_EXCEEDED = false;

	public static float PET_FOLLOW_RANGE = 5f;
	public static float PET_SPAWN_RANGE = 1f;
	public static float COMBAT_PET_SPEED_MOD = 1.2f;
	public static boolean COMBAT_PET_SPEED_FROM_OWNER = true;

	private static int numFactories = 0;
	private static int numInstances = 0;
	static Random random = new Random();
	
	// A temporary HashMap to store what mobs to notify when someone dies
	static Map<OID, Set<CombatBehavior>> mobsToAlertOnDeath = new ConcurrentHashMap<>();
	public static ConcurrentHashMap<String, BonusSettings> globalEventBonusesArray = new ConcurrentHashMap<String, BonusSettings>();
	
	public static synchronized void addMobToAlertOnDeath(OID targetOid, CombatBehavior cBehav) {
	    mobsToAlertOnDeath.computeIfAbsent(targetOid, __ -> ConcurrentHashMap.newKeySet()).add(cBehav);
		if(Log.loggingDebug)
	           Log.debug("DEAD: adding alert for death of: " + targetOid + " to mob: " + cBehav.getObjectStub().getOid() 
				+ "with mobsToAlert count: " + mobsToAlertOnDeath.get(targetOid).size());
	}
	
	public static void removeMobFromDeathAlert(CombatBehavior cBehav) {
		for (Set<CombatBehavior> cBehavs : mobsToAlertOnDeath.values()) {
			cBehavs.remove(cBehav);
		}
	}
	
	class PropertyHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			PropertyMessage propMsg = (PropertyMessage) msg;
			OID objOid = propMsg.getSubject();
			if (Log.loggingDebug)log.debug("PropertyHook: for "+objOid+" "+propMsg);
			Boolean dead = (Boolean) propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
			Log.debug("DEAD: got propertyMessage for mob: " + objOid + " with dead? " + dead);
			if (dead != null && dead) {
				Log.debug("DEAD: got propertyMessage for mob: " + objOid + " remove mob from NavMesh ");
				for (OID instanceOid : instances) {
					if (instanceNavMeshes.containsKey(instanceOid)) {
						instanceNavMeshes.get(instanceOid).removeActor(objOid);
					}
				}
			}
			if (dead != null && mobsToAlertOnDeath.containsKey(objOid)) {
				
				if(Log.loggingDebug)
			           Log.debug("DEAD: got propertyMessage for mob: " + objOid + " with dead? " + dead);
				int mobsAlerted = 0;
				ArrayList<CombatBehavior> cBehavs = new ArrayList<CombatBehavior>(mobsToAlertOnDeath.get(objOid));
				for (CombatBehavior cBehav : cBehavs) {
					if(Log.loggingDebug)
				           Log.debug("DEAD: alerting mob: " + cBehav.getObjectStub().getOid() + " of deadstate change for: " + objOid);
					cBehav.alertMobDeathStateChange(objOid, dead);
					mobsAlerted++;
				}
				if (cBehavs.size() < mobsToAlertOnDeath.get(objOid).size()) {
					if(Log.loggingDebug)
				           Log.debug("DEAD: handling mobs added after original list completed with mobsAlerted: " + mobsAlerted);
					/*for (int i = cBehavs.size(); i < mobsToAlertOnDeath.get(objOid).size(); i++) {
						mobsToAlertOnDeath.get(objOid).get(i).alertMobDeathStateChange(objOid, dead);
					}*/
				}
				if(Log.loggingDebug)
			           Log.debug("DEAD: completed alerting mobs for death of: " + objOid + " with mobsAlerted: " + mobsAlerted);
			}
			return true;
		}
	}

}