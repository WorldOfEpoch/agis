/**
 *
 */
package atavism.agis.plugins;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import atavism.agis.core.Agis;
import atavism.agis.core.AgisAbility;
import atavism.agis.core.AgisEffect;
import atavism.agis.core.AgisSkill;
import atavism.agis.database.AuthDatabase;
import atavism.agis.database.CombatDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.objects.*;
import atavism.agis.plugins.ClassAbilityClient.RewardExpForKillMessage;
import atavism.agis.plugins.CombatClient.StartAbilityMessage;
import atavism.agis.plugins.CombatClient.AbilityUpdateMessage.Entry;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.msgsys.Message;
import atavism.msgsys.MessageAgent;
import atavism.msgsys.MessageTypeFilter;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.Hook;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.objects.Entity;
import atavism.server.objects.EntityManager;
import atavism.server.objects.Template;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.ObjectInfo;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;
import atavism.server.util.Logger;
import atavism.server.math.*;//Dragonsan
import atavism.server.messages.PropertyMessage;
import atavism.agis.database.*;

public class ClassAbilityPlugin extends EnginePlugin {

	/**
     * Holds the list of professions
     */
    protected static HashMap<String, ProfessionObject> professions = new HashMap<String, ProfessionObject>();

    /*
     * This will hold the keying between ability start and ability completion
     */
	Map<Integer, Set<OID>> playerabilitykey = new ConcurrentHashMap<Integer, Set<OID>>();

    protected static Map<String, AgisStatDef> statDefMap = new HashMap<String, AgisStatDef>();

    public ClassAbilityPlugin() {
        super("ClassAbility");
        setPluginType("ClassAbility");
    }

    private static final Logger log = new Logger("ClassAbility");

    public void onActivate() {
        if (Log.loggingDebug)
            log.debug(this.getName() + " OnActivate Started");
        super.onActivate();
        if (Log.loggingDebug)
            log.debug(this.getName() + " base class onActivate ran");
        registerHooks();
        if (Log.loggingDebug)
            log.debug(this.getName() + " registered hooks");
        MessageTypeFilter filter = new MessageTypeFilter();
        filter.addType(CombatClient.MSG_TYPE_SKILL_UPDATE);
        //filter.addType(CombatClient.MSG_TYPE_ABILITY_UPDATE);
        //filter.addType(CombatClient.MSG_TYPE_ABILITY_PROGRESS);
        //filter.addType(CombatClient.MSG_TYPE_START_ABILITY);
        filter.addType(ClassAbilityClient.MSG_TYPE_HANDLE_EXP);
        filter.addType(CombatClient.MSG_TYPE_ALTER_EXP);
        filter.addType(CombatClient.MSG_TYPE_COMBAT_ABILITY_USED);
	    filter.addType(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_INCREASE);
	    filter.addType(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_DECREASE);
	    filter.addType(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_RESET);
	   // filter.addType(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_POINTS);
	    filter.addType(ClassAbilityClient.MSG_TYPE_LEVEL_CHANGE);
	    filter.addType(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_ALTER_CURRENT);
	    filter.addType(ClassAbilityClient.MSG_TYPE_PURCHASE_SKILL_POINT);
	    filter.addType(ClassAbilityClient.MSG_TYPE_ADD_SKILL_POINT);
		filter.addType(ClassAbilityClient.MSG_TYPE_SET_SKILL_STATE);
	    filter.addType(ClassAbilityClient.MSG_TYPE_LEARN_ABILITY);
	    filter.addType(ClassAbilityClient.MSG_TYPE_UNLEARN_ABILITY);
        filter.addType(CombatClient.MSG_TYPE_UPDATE_ACTIONBAR);
        Engine.getAgent().createSubscription(filter, this);
        
        // Create responder subscription
  	    MessageTypeFilter filter2 = new MessageTypeFilter();
  	    //filter2.addType(LoginMessage.MSG_TYPE_LOGIN);
  	    //filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
  	    filter2.addType(ClassAbilityClient.MSG_TYPE_COMBAT_GET_SKILL);
  	    filter2.addType(ClassAbilityClient.MSG_TYPE_COMBAT_GET_PLAYER_SKILL_LEVEL);
  	    filter2.addType(CombatClient.MSG_TYPE_COMBAT_ABILITY_USED);
		Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);

        registerLoadHook(Namespace.CLASSABILITY, new ClassAbilityLoadHook());
        //registerSaveHook(Namespace.CLASSABILITY, new ClassAbilitySaveHook());

        this.registerPluginNamespace(ClassAbilityClient.NAMESPACE, new ClassAbilitySubObjectHook());

        // Get the ability list from the database.
        //this.buildAbilitiesList();
       
        if (Log.loggingDebug)
            log.debug(this.getName() + " activated");
        
        loadProgressionData();
    }

    public void registerHooks() {
      //  getHookManager().addHook(CombatClient.MSG_TYPE_ABILITY_UPDATE, new ClassAbilityAddAbilityHook());
       // getHookManager().addHook(CombatClient.MSG_TYPE_ABILITY_PROGRESS, new ClassAbilityAbilityProgressHook());
        //getHookManager().addHook(CombatClient.MSG_TYPE_START_ABILITY, new ClassAbilityStartAbilityHook());
        getHookManager().addHook(ClassAbilityClient.MSG_TYPE_HANDLE_EXP, new ClassAbilityHandleXpHook());
        getHookManager().addHook(CombatClient.MSG_TYPE_ALTER_EXP, new AlterExpHook());
        getHookManager().addHook(CombatClient.MSG_TYPE_COMBAT_ABILITY_USED, new AbilityUsedHook());
        getHookManager().addHook(ClassAbilityClient.MSG_TYPE_PURCHASE_SKILL_POINT, new PurchaseSkillPointHook());
        getHookManager().addHook(ClassAbilityClient.MSG_TYPE_ADD_SKILL_POINT, new AddSkillPointHook());
          getHookManager().addHook(ClassAbilityClient.MSG_TYPE_SET_SKILL_STATE, new SetSkillStateHook());
        getHookManager().addHook(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_INCREASE, new SkillIncreaseHook());
        getHookManager().addHook(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_DECREASE, new SkillDecreaseHook());
        getHookManager().addHook(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_RESET, new SkillResetHook());
      //  getHookManager().addHook(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_POINTS, new SkillPointsHook());
         getHookManager().addHook(ClassAbilityClient.MSG_TYPE_LEVEL_CHANGE, new LevelChangeHook());
        getHookManager().addHook(ClassAbilityClient.MSG_TYPE_COMBAT_SKILL_ALTER_CURRENT, new IncreaseSkillCurrentHook());
        getHookManager().addHook(ClassAbilityClient.MSG_TYPE_COMBAT_GET_SKILL, new SkillGetHook());
        getHookManager().addHook(ClassAbilityClient.MSG_TYPE_COMBAT_GET_PLAYER_SKILL_LEVEL, new GetSkillLevelHook());
        getHookManager().addHook(ClassAbilityClient.MSG_TYPE_LEARN_ABILITY, new LearnAbilityHook());
        getHookManager().addHook(ClassAbilityClient.MSG_TYPE_UNLEARN_ABILITY, new UnlearnAbilityHook());
        getHookManager().addHook(CombatClient.MSG_TYPE_UPDATE_ACTIONBAR, new UpdateActionBarHook());
        getHookManager().addHook(CombatClient.MSG_TYPE_COMBAT_SKILL_DIFF, new GetSkillDiffExpHook());
        
    
    }
    
    void loadProgressionData() {
    	CombatDatabase cDB = new CombatDatabase(false);
    	// Level xp amounts
        levelXpRequirements = cDB.loadLevelExpRequirements();
      //  cDB.close();
        // Stat progression
        ContentDatabase ctDB = new ContentDatabase(false);
        characterTemplates = ctDB.loadCharacterFactoryTemplates();
       // ctDB.close();

		if(!Engine.isAIO()) {
			MobDatabase mDB = new MobDatabase(false);
			HashMap<Integer, PetProfile> petprofiles = mDB.LoadPetProfiles();
			for (int pp: petprofiles.keySet()) {
				Agis.PetProfile.register(pp, petprofiles.get(pp));
				if (Log.loggingDebug)
					Log.debug("MOB: loaded PetProfile: [" + petprofiles.get(pp).getName() + "]");
			}

		}
        String useSkillPurchasePoints = ctDB.loadGameSetting("USE_SKILL_PURCHASE_POINTS");
		if (useSkillPurchasePoints != null)
			USE_SKILL_PURCHASE_POINTS = Boolean.parseBoolean(useSkillPurchasePoints);
		log.debug("Loaded Game Setting USE_SKILL_PURCHASE_POINTS="+USE_SKILL_PURCHASE_POINTS);
		
		String skillPointsPerLevel = ctDB.loadGameSetting("SKILL_POINTS_GIVEN_PER_LEVEL");
		if (skillPointsPerLevel != null)
			SKILL_POINTS_GIVEN_PER_LEVEL = Integer.parseInt(skillPointsPerLevel);
		log.debug("Loaded Game Setting SKILL_POINTS_GIVEN_PER_LEVEL="+SKILL_POINTS_GIVEN_PER_LEVEL);
		
		
		   String useTalentPurchasePoints = ctDB.loadGameSetting("USE_TALENT_PURCHASE_POINTS");
			if (useTalentPurchasePoints != null)
				USE_TALENT_PURCHASE_POINTS = Boolean.parseBoolean(useTalentPurchasePoints);
			log.debug("Loaded Game Setting USE_TALENT_PURCHASE_POINTS="+USE_TALENT_PURCHASE_POINTS);
		
		
		String talentPointsPerLevel = ctDB.loadGameSetting("TALENT_POINTS_GIVEN_PER_LEVEL");
		if (talentPointsPerLevel != null)
			TALENT_POINTS_GIVEN_PER_LEVEL = Integer.parseInt(talentPointsPerLevel);
		log.debug("Loaded Game Setting TALENT_POINTS_GIVEN_PER_LEVEL="+TALENT_POINTS_GIVEN_PER_LEVEL);

		
		
		String useSkillMax = ctDB.loadGameSetting("USE_SKILL_MAX");
		if (useSkillMax != null)
			USE_SKILL_MAX = Boolean.parseBoolean(useSkillMax);
		log.debug("Loaded Game Setting USE_SKILL_MAX="+USE_SKILL_MAX);
		
		String skillStartingMax = ctDB.loadGameSetting("SKILL_STARTING_MAX");
		if (skillStartingMax != null)
			SKILL_STARTING_MAX = Integer.parseInt(skillStartingMax);
		log.debug("Loaded Game Setting SKILL_STARTING_MAX="+SKILL_STARTING_MAX);
		
		String expMaxLevelDifference = ctDB.loadGameSetting("EXP_MAX_LEVEL_DIFFERENCE");
		if (expMaxLevelDifference != null)
			EXP_MAX_LEVEL_DIFFERENCE = Integer.parseInt(expMaxLevelDifference);
		log.debug("Loaded Game Setting EXP_MAX_LEVEL_DIFFERENCE="+EXP_MAX_LEVEL_DIFFERENCE);
		
		String autoAddAbilitiesToActionBar = ctDB.loadGameSetting("AUTO_ADD_ABILITIES_TO_ACTION_BAR");
		if (autoAddAbilitiesToActionBar != null)
			AUTO_ADD_ABILITIES_TO_ACTION_BAR = Boolean.parseBoolean(autoAddAbilitiesToActionBar);
		log.debug("Loaded Game Setting AUTO_ADD_ABILITIES_TO_ACTION_BAR="+AUTO_ADD_ABILITIES_TO_ACTION_BAR);
		
		String skillUpRate = ctDB.loadGameSetting("SKILL_UP_RATE");
		if (skillUpRate != null)
			SKILL_UP_RATE = Float.parseFloat(skillUpRate);
		log.debug("Loaded Game Setting SKILL_UP_RATE="+SKILL_UP_RATE);
		
		
		String mobExpRateNormal = ctDB.loadGameSetting("MOB_EXP_RATE_NORMAL");
		if (mobExpRateNormal != null)
			MOB_EXP_RATE_NORMAL = Float.parseFloat(mobExpRateNormal);
		log.debug("Loaded Game Setting MOB_EXP_RATE_NORMAL="+MOB_EXP_RATE_NORMAL);
		
		String mobExpRateRare = ctDB.loadGameSetting("MOB_EXP_RATE_RARE");
		if (mobExpRateRare != null)
			MOB_EXP_RATE_RARE = Float.parseFloat(mobExpRateRare);
		log.debug("Loaded Game Setting MOB_EXP_RATE_RARE="+MOB_EXP_RATE_RARE);
		
		String mobExpRateBoss = ctDB.loadGameSetting("MOB_EXP_RATE_BOSS");
		if (mobExpRateBoss != null)
			MOB_EXP_RATE_BOSS = Float.parseFloat(mobExpRateBoss);
		log.debug("Loaded Game Setting MOB_EXP_RATE_BOSS="+MOB_EXP_RATE_BOSS);
		

		String expMaxDistance = ctDB.loadGameSetting("EXP_MAX_DISTANCE");
		if (expMaxDistance != null)
			EXP_MAX_DISTANCE = Float.parseFloat(expMaxDistance);
		log.debug("Loaded Game Setting EXP_MAX_DISTANCE="+EXP_MAX_DISTANCE);
		
		String expGoupAddPercentage = ctDB.loadGameSetting("EXP_GROUP_ADD_PERCENTAGE");
		if (expGoupAddPercentage != null)
			EXP_GROUP_ADD_PERCENTAGE = Float.parseFloat(expGoupAddPercentage);
		log.debug("Loaded Game Setting EXP_GROUP_ADD_PERCENTAGE="+EXP_GROUP_ADD_PERCENTAGE);

		
		String lostLevel = ctDB.loadGameSetting("LOST_LEVEL");
		if (lostLevel != null)
			LOST_LEVEL = Boolean.parseBoolean(lostLevel);
		log.debug("Loaded Game Setting LOST_LEVEL="+LOST_LEVEL);

		String swap_action = ctDB.loadGameSetting("ACTION_BAR_SWAP_ACTIONS");
		if (swap_action != null)
			ACTION_BAR_SWAP_ACTIONS = Boolean.parseBoolean(swap_action);
		log.debug("Loaded Game Setting ACTION_BAR_SWAP_ACTIONS="+ACTION_BAR_SWAP_ACTIONS);

    }

    	

	protected void ReloadTemplates(Message msg) {
		Log.error("ClassAbilityPlugin ReloadTemplates Start");
		loadProgressionData();
		Log.error("ClassAbilityPlugin ReloadTemplates End");
	}
    
    public class ClassAbilitySubObjectHook extends GenerateSubObjectHook {

        public ClassAbilitySubObjectHook(){ super(ClassAbilityPlugin.this); }

		public SubObjData generateSubObject(Template template, Namespace namespace, OID masterOid) {
			if (Log.loggingDebug)
				log.debug("GenerateSubObjectHook: masterOid=" + masterOid + ", template=" + template);

			if (masterOid == null) {
				log.error("GenerateSubObjectHook: no master oid");
				return null;
			}

			if (Log.loggingDebug)
				log.debug("GenerateSubObjectHook: masterOid=" + masterOid + ", template=" + template);

			Map<String, Serializable> props = template.getSubMap(ClassAbilityClient.NAMESPACE);

			if (props == null) {
				Log.warn("GenerateSubObjectHook: no props in ns " + ClassAbilityClient.NAMESPACE);
				return null;
			}

			// generate the subobject
			ClassAbilityObject tinfo = new ClassAbilityObject(masterOid);
			tinfo.setName(template.getName());

			Boolean persistent = (Boolean) template.get(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT);
			if (persistent == null)
				persistent = false;
			tinfo.setPersistenceFlag(persistent);

			// copy properties from template to object
			for (Map.Entry<String, Serializable> entry : props.entrySet()) {
				String key = entry.getKey();
				Serializable value = entry.getValue();
				if (!key.startsWith(":")) {
					tinfo.setProperty(key, value);
				}
			}

			tinfo.setPlayerClass((String) tinfo.getProperty("class"));

			if (Log.loggingDebug)
				log.debug("GenerateSubObjectHook: created entity " + tinfo);

            // register the entity
            EntityManager.registerEntityByNamespace(tinfo, ClassAbilityClient.NAMESPACE);

            //send a response message
            return new SubObjData();
        }
    }
@Deprecated
    public class ClassAbilityAddAbilityHook implements Hook {
        public boolean processMessage(Message msg, int flags) {

            CombatClient.AbilityUpdateMessage reqMsg = (CombatClient.AbilityUpdateMessage) msg;
            // extract the base information
            List<Entry> skilllist = reqMsg.getAbilities();
            OID oid = reqMsg.getSubject();

            ClassAbilityObject caobj = (ClassAbilityObject)EntityManager.getEntityByNamespace(oid, ClassAbilityClient.NAMESPACE);
            if (caobj == null)
                return true;
            String playerclass = caobj.getPlayerClass();

            for (Entry e : skilllist) {

                log.debug("Adding ability to the player: " + oid + " ability: " + e.abilityID);

                // do a couple of double checks..
                if (Agis.AbilityManager.keySet().contains(e.abilityID)) {
                    // okay it is a valid system skill, now make sure it is in our list
                    // but first we need the player's object for classability

                    if (playerclass == null) {
                        // they didn't define a class for this character, so we can't track stats for them
                        log.warn("They didn't define a class type for this player...");
                        return true;
                    }

                    // now use the player's class to get the list of skills avaiable
                    if (professions.get(playerclass).hasAbility(e.abilityID)) {
                        if (caobj.getProperty(e.abilityID + "_exp") == null) {
                            // this is valid skill for tracking experience. So generate an agisstat for this skill
                            createStats(caobj, Agis.AbilityManager.get(e.abilityID), professions.get(playerclass).getAbility(e.abilityID).getExperiencePerUse());
                        }
                    }
                }
            }
            // return true so that the chain continues if there is more
            return true;
        }
    }

    /**
     * This method creates stats based on the passed in skill, and assigns them to the player via the
     * created players' ClassAbilityObject.
     *
     * @param caobj
     * @param skill
     * @param xp_use
     */
    public static void createStats(ClassAbilityObject caobj, AgisSkill skill, Integer xp_use) {
        AgisStat tmp_exp = new AgisStat(skill.getName() + "_exp");
        tmp_exp.min = tmp_exp.current = tmp_exp.base = 0 ;
        tmp_exp.max = skill.getBaseExpThreshold();

        AgisStat tmp_rank = new AgisStat(skill.getName() + "_rank");
        tmp_rank.min = tmp_rank.current = tmp_rank.base = 0;
        tmp_rank.max = skill.getMaxRank();

        caobj.setProperty(skill.getName() + "_exp", tmp_exp);
        caobj.setProperty(skill.getName() + "_rank", tmp_rank);
        caobj.setProperty(skill.getName(), xp_use);
    }

    /**
     * This method creates stats based on the passed in ability, and assigns them to the player via the
     * created players' ClassAbilityObject.
     *
     * @param caobj
     * @param ability
     * @param xp_use
     */
    public static void createStats(ClassAbilityObject caobj, AgisAbility ability, Integer xp_use) {
        AgisStat tmp_exp = new AgisStat(ability.getName() + "_exp");
        tmp_exp.min = tmp_exp.current = tmp_exp.base = 0 ;
        tmp_exp.max = ability.getBaseExpThreshold(); // TODO: Replace this value with a modifiable one

        AgisStat tmp_rank = new AgisStat(ability.getName() + "_rank");
        tmp_rank.min = tmp_rank.current = tmp_rank.base = 0;
        tmp_rank.max = ability.getMaxRank(); // TODO: Replace this value with a modifiable one

        caobj.setProperty(ability.getName() + "_exp", tmp_exp);
        caobj.setProperty(ability.getName() + "_rank", tmp_rank);
        caobj.setProperty(ability.getName(), xp_use);
    }

    /**
     * Register the stat with the specific player, since only the player themselves have to be aware of what stats they
     * should be paying attention to.
     *
     * @param stat
     */
    public static void registerStat(AgisStatDef stat) {
        registerStat(stat, new String[0]);
    }

	public static void registerStat(AgisStatDef stat, String... dependencies) {
		String statName = stat.getName();
		if (!statDefMap.containsKey(statName)) {
			statDefMap.put(statName, stat);
			for (String depName : dependencies) {
				AgisStatDef depStat = statDefMap.get(depName);
				if (depStat != null) {
					depStat.addDependent(stat);
				} else {
					Log.error("no stat definition for dependency " + depName + " of stat " + statName);
				}
			}
		}
	}

    /**
     * This method allows registering a profession.
     *
     * @param profession
     */
    public static void registerProfession(ProfessionObject profession) {
        log.debug("Registering Profession: " + profession);
        professions.put(profession.getName(), profession);
    }

    public static AgisStatDef lookupStatDef(String name) {
        return statDefMap.get(name);
    }

    public static void sendSkillUpdate(CombatInfo info) {
        // extract the base information
       // SkillInfo skillInfo = info.getCurrentSkillInfo();
        OID oid = info.getOwnerOid();

        //Setup message to send to client
        TargetedExtensionMessage updateMsg = new TargetedExtensionMessage(info.getOwnerOid());
        updateMsg.setExtensionType("ao.SKILL_UPDATE");

        ClassAbilityObject caobj = (ClassAbilityObject)EntityManager.getEntityByNamespace(oid, ClassAbilityClient.NAMESPACE);
        if (caobj == null)
            return;
        //String playerclass = caobj.getPlayerClass();

        //Create xp and rank stats for any new skills
        /*for (int skillID : skillInfo.getSkills().keySet()){

            log.debug("Adding skill to the player: " + oid + " skill: " + skillInfo);

            // do a couple of double checks..
            if (Agis.SkillManager.keySet().contains(skillID)){
                // okay it is a valid system skill, now make sure it is in our list
                // but first we need the player's object for classability

                if (playerclass == null){
                    // they didn't define a class for this character, so we can't track stats for them
                    log.warn("They didn't define a class type for this player...");
                    return;
                }
                log.debug(professions.get(playerclass).toString());
                // now use the player's class to get the list of skills avaiable
                if (professions.get(playerclass).hasSkill(skillID)){
                    if (caobj.getProperty(skillID + "_exp") == null){
                        // this is valid skill for tracking experience. So generate an agisstat for this skill
                        //createStats(caobj, Agis.SkillManager.get(skillID), 
                        //		professions.get(playerclass).getSkill(skillID).getExperiencePerUse());
                    }
                }

                //Add skill to update message - Skill Name and Current Rank
                HashMap<String,Serializable> skillData = new HashMap<String,Serializable> ();
                skillData.put("id",skillID);
                skillData.put("rank",((AgisStat)caobj.getProperty(skillID + "_rank")).current);
                updateMsg.setProperty("Skill_"+skillID, skillData);
            }
        }*/

        //Send update to the client on new skills
        Engine.getAgent().sendBroadcast(updateMsg);
    }
@Deprecated
    class ClassAbilityStartAbilityHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            CombatClient.StartAbilityMessage abilityMsg = (CombatClient.StartAbilityMessage) msg;
            OID oid = abilityMsg.getSubject();
            int abilityID = abilityMsg.getAbilityID();

            log.debug("Processing Start Ability Message: " + oid + ", ability: " + abilityID);

			//Not used
		/*	playerabilitykey.computeIfAbsent(abilityID, __ -> ConcurrentHashMap.newKeySet()).add(oid);
            if(Log.loggingDebug)
            	log.debug("PlayerAbilityKey list for " + abilityID );//+ " : [ " + playerabilitykey.get(abilityID).toString() + " ]");
*/
            return true;
        }
    }
@Deprecated
    class ClassAbilityAbilityProgressHook implements Hook{
        public boolean processMessage(Message msg, int flags) {
            // Convert the message, then get the pertinent information
			// Not used
         /*
            CombatClient.AbilityProgressMessage abilityMsg = (CombatClient.AbilityProgressMessage) msg;
            int abilityID = abilityMsg.getAbilityID();
            String state = abilityMsg.getState();

            log.debug("Processing Progress Ability Message: " + state + ":" + AgisAbility.ActivationState.COMPLETED +", ability: " + abilityID);

            if (state.equals(AgisAbility.ActivationState.COMPLETED.toString())) {
                // now we need to find a matching oid from our key list
				Set<OID> oids = playerabilitykey.get(abilityID);
                if (oids == null){ return true; }       // there are no oids associated with this ability
                log.debug("Getting OIDS: [ " + oids.toString() + " ]");
                for( OID oid : oids) {
                    // now load the combat namespace for this oid so we can find out if the current state is completed or not
                    CombatInfo ci = (CombatInfo)EntityManager.getEntityByNamespace(oid, CombatClient.NAMESPACE);
                    if (ci == null)
                        continue;
                    log.debug("Checking the current action state for " + ci.getName() +  " ( " + ci.getOid() + " ) : " + ci.getCurrentAction());
                    if (ci.getCurrentAction() == null) {
                        // we found one that is completed so pull in the classabilityobject and complete the task
                        ClassAbilityObject caobj = (ClassAbilityObject)EntityManager.getEntityByNamespace(oid, ClassAbilityClient.NAMESPACE);
                        AgisAbility ability = professions.get(caobj.getPlayerClass()).getAbility(abilityID);

                        if (ability != null) { // this is not a valid profession ability for this player
                            //Get associated skill information
                            AgisSkill skill = ability.getRequiredSkill();
                            if(skill != null){
                                // Make sure the player's profession has this skill
                                if(professions.get(caobj.getPlayerClass()).getSkillMap().get(skill.getName()) != null)
                                    //Update skill
                                    caobj.updateBaseStat(skill.getID(), skill.getExperiencePerUse());
                            }
                                 // now increment the ability
                                 caobj.updateBaseStat(abilityID, ability.getExperiencePerUse());
                            }
                        }
                    }
                }
*/
            return true;
        }
    }

    class ClassAbilityLoadHook implements LoadHook {
        public void onLoad(Entity e) {
        	Log.error("ClassAbilityLoadHook: "+e);
        }
    }

    public static ClassAbilityObject getClassAbilityObject(OID oid) {
        log.debug("Checking the data for oid: " + oid);
        Entity entity = EntityManager.getEntityByNamespace(oid, Namespace.CLASSABILITY);
        log.debug("What is this entity type? " + entity.getType() + " and Name? " + entity.getName() + " and OID: " + entity.getOid());
        return (ClassAbilityObject)entity;
    }

    public class ClassAbilityHandleXpHook implements Hook{
    	public boolean processMessage(Message msg, int flags) {
    		RewardExpForKillMessage xpUpdateMsg = (RewardExpForKillMessage)msg;
    		Log.debug("ClassAbilityHandleXpHook "+xpUpdateMsg.getTargetOid()+" "+xpUpdateMsg.getAttackers());
    	try {
			CombatInfo target = CombatPlugin.getCombatInfo(xpUpdateMsg.getTargetOid());
			if (target == null) {
				log.debug("ClassAbilityHandleXpHook:  target Combat Info is null");
				return true;
			}
			if (xpUpdateMsg.getAttackers().size() > 1) {
				int totalDamage = 0;
				for (int damage : xpUpdateMsg.getAttackers().values()) {
					totalDamage += damage;
				}

				float percentPerDamage = 1 / (float) totalDamage;
				for (OID attackerOid : xpUpdateMsg.getAttackers().keySet()) {
					rewardExpForKill(target, attackerOid, percentPerDamage * (float) xpUpdateMsg.getAttackers().get(attackerOid));
				}
			} else if (xpUpdateMsg.getAttackers().size() == 1) {
				OID attackerOid = xpUpdateMsg.getAttackers().keySet().iterator().next();
				rewardExpForKill(target, attackerOid, 1f);
			}
		}catch (Exception e){

		}
    		return true;
    	}
    }
    
    /**
     * This function is used when a target dies to give the player XP from the kill.
     *
     * It is in the CombatAPI to allow for modification as systems are added to the combat.
     *
     * @param target
     * @param attackerOid
     * @param expPercent
     */
	public static void rewardExpForKill(CombatInfo target, OID attackerOid, float expPercent) {
		// pull the xp value from the target
		Integer mobexp = (Integer) target.getProperty(KILL_EXP_STAT);
		Integer mobexp_add = (Integer) target.getProperty(KILL_ADD_EXP_LEV_STAT);
		Integer minLev = (Integer) target.getProperty(MIN_MOB_LEVEL);

		int targetLevel = target.statGetCurrentValue("level");
		int diffMobLev = targetLevel - minLev;

		log.debug("EXP: handle xp");
		// int targetLevel = target.statGetCurrentValue("level");

		if (mobexp == null) {
			return;
		} // this target has no xp to gain
		LinkedList<OID> handledOids = new LinkedList<OID>();

		CombatInfo attacker = CombatPlugin.getCombatInfo(attackerOid);
		if (attacker == null || attacker.isMob())
			return;

		// Set mob Exp multiplier based on mob type
	/*	double mobExpMultiplier = MOB_EXP_RATE_NORMAL;
		int mobType = (Integer) target.getProperty("mobType");
		if (mobType == 2) {
			mobExpMultiplier = MOB_EXP_RATE_BOSS;
		} else if (mobType == 3) {
			mobExpMultiplier = MOB_EXP_RATE_RARE;
		}*/

		// If the attacker is grouped then all group members should get xp also
		HashSet<OID> groupMembers = new HashSet<OID>();
		if (attacker.isGrouped()) {

			/*
			 * //Andrew solution send 30.10.2017 
			 * ObjectInfo attackerInfo = WorldManagerClient.getObjectInfo(attackerOid); groupMembers = GroupClient.GetGroupMemberOIDs(attackerOid).memberOidSet; for(OID
			 * groupMemberOid : new HashSet<OID>(groupMembers)){ ObjectInfo groupMemberInfo = WorldManagerClient.getObjectInfo(groupMemberOid); // Don't give exp to members in different instances or more than 100
			 * metres away if (!groupMemberInfo.instanceOid.equals(attackerInfo.instanceOid) || Point.distanceTo(attackerInfo.loc, groupMemberInfo.loc) > 100) { groupMembers.remove(groupMemberOid); } }
			 * 
			 */

			groupMembers = GroupClient.GetGroupMemberOIDs(attackerOid).memberOidSet;
			// Dragonsan Start
			HashSet<OID> groupMembersInRange = new HashSet<OID>();
			Point attakerPos = new Point(0,0,0);
			ObjectInfo oia = WorldManagerClient.getObjectInfo(attackerOid);
			if (oia != null) {
				attakerPos = oia.loc;
				ObjectInfo oinfo = WorldManagerClient.getObjectInfo(target.getOid());
				if(oinfo!=null) {
					OID targetInstance = oinfo.instanceOid;
					// OID inst = WorldManagerClient.getObjectInfo(attackerOid).instanceOid;
					for (OID member : groupMembers) {
						ObjectInfo oi = WorldManagerClient.getObjectInfo(member);
						if (oi != null) {
							OID memberInstance = oi.instanceOid;
							if (Log.loggingDebug)
								log.debug("Group Exp: t: " + target.getOid() + " i: " + targetInstance + " | m: " + member + " i: " + memberInstance);
							if (targetInstance.equals(memberInstance)) {
								log.debug("Group Exp: Instance same ");
								Point memberPos = oi.loc;
								// float distance = Point.distanceTo(attakerPos, memberPos);
								if (Point.distanceTo(attakerPos, memberPos) <= EXP_MAX_DISTANCE) {
									groupMembersInRange.add(member);
								} else {
									if (Log.loggingDebug)
										log.debug("Group: Member and target distance > " + EXP_MAX_DISTANCE);
								}
							} else {
								log.debug("Group: Member not same instance and Target");
							}
						}
					}
				}else{
					log.debug("rewardExpForKill target ObjectInfo is null");
				}
			}
			// Dragonsan End
			// expPercent = (expPercent / (float)groupMembers.size()) + 0.1f;
			expPercent = (expPercent / (float) groupMembersInRange.size()) + EXP_GROUP_ADD_PERCENTAGE;
			if(Log.loggingDebug)log.debug("GROUP: xp hit, group members: " + groupMembers + "|" + groupMembersInRange);

			for (OID groupMemberOid : groupMembersInRange) {
				// If the group member is in the attackers list or already handled list,
				// then do not set their xp here
				if (!handledOids.contains(groupMemberOid)) {
					CombatInfo groupMember = CombatPlugin.getCombatInfo(groupMemberOid);
					if(Log.loggingDebug)log.debug("GROUP: xp hit, giving exp to: " + (groupMember==null?"":groupMember.getOwnerOid()));
					// now apply the value to the group member
					// groupMember.statModifyBaseValue(EXPERIENCE_STAT, xpPercent);
					int attackerLevel = attacker.statGetCurrentValue(LEVEL_STAT);
					if (attackerLevel - targetLevel <= EXP_MAX_LEVEL_DIFFERENCE) {
						// double xpval = (65* Math.pow(1.1, targetLevel))*(Math.pow(0.95, Math.abs(targetLevel-attackerLevel))) * expPercent * mobExpMultiplier;
						int diffLev = attackerLevel - targetLevel;
						if (diffLev < 0)
							diffLev = 0;
						double xpval = expPercent * /*mobExpMultiplier **/ (mobexp + mobexp_add * diffMobLev) * (EXP_MAX_LEVEL_DIFFERENCE - diffLev) / EXP_MAX_LEVEL_DIFFERENCE;
						int xpValInt = (int) Math.round(xpval);
						
						
						
						float vipModp = 0f;
						long vipMod = 0l;
						if(groupMember != null && groupMember.getBonuses().containsKey("ExpGain")) {
							vipMod =groupMember.getBonuses().get("ExpGain").GetValue();
			      			vipModp =groupMember.getBonuses().get("ExpGain").GetValuePercentage();
			      		}
						if(CombatPlugin.globalEventBonusesArray.containsKey("ExpGain")) {
							vipMod += CombatPlugin.globalEventBonusesArray.get("ExpGain").GetValue();
			      			vipModp += CombatPlugin.globalEventBonusesArray.get("ExpGain").GetValuePercentage();
			      		}
						int expwithBonus = (int) Math.round(Math.ceil(xpValInt + vipMod + xpValInt * vipModp / 100f));
						if(Log.loggingDebug)log.debug("EXP: playerOid=" + groupMemberOid + " calculated Exp=" + xpValInt + " with Bonus " + expwithBonus);
						CombatPlugin.addListsRankingData(groupMemberOid, AchievementsClient.EXPERIENCE, expwithBonus);
						CombatPlugin.addListsRankingData(groupMemberOid, AchievementsClient.KILL, target.getID());
						giveExp(groupMemberOid, expwithBonus);
					} else {
						log.debug("GROUP: no EXP Reson Max Level Difference");
					}
					// Add to handled list to ensure the OID is not processed more than once
					handledOids.add(groupMemberOid);
					// ClassAbilityClient.sendXPUpdate(groupMemberOid, EXPERIENCE_STAT, groupMember.statGetCurrentValue(EXPERIENCE_STAT));
				}
			}
		} else {
			log.debug("EXP: handle xp 2");
			int attackerLevel = attacker.statGetCurrentValue(LEVEL_STAT);
			// double xpval = (65* Math.pow(1.1, targetLevel))*(Math.pow(0.95, Math.abs(targetLevel-attackerLevel))) * expPercent * mobExpMultiplier;
			// int xpValInt = (int) xpval;
			if (attackerLevel - targetLevel <= EXP_MAX_LEVEL_DIFFERENCE) {
				int diffLev = attackerLevel - targetLevel;
				if (diffLev < 0)
					diffLev = 0;
				double xpval = expPercent */* mobExpMultiplier * */(mobexp + mobexp_add * diffMobLev) * (EXP_MAX_LEVEL_DIFFERENCE - diffLev) / EXP_MAX_LEVEL_DIFFERENCE;
				int xpValInt = (int) Math.round(xpval);
			
				float vipModp = 0f;
				long vipMod = 0l;
				if(attacker.getBonuses().containsKey("ExpGain")) {
					vipMod =attacker.getBonuses().get("ExpGain").GetValue();
	      			vipModp =attacker.getBonuses().get("ExpGain").GetValuePercentage();
	      		}
				if(CombatPlugin.globalEventBonusesArray.containsKey("ExpGain")) {
					vipMod += CombatPlugin.globalEventBonusesArray.get("ExpGain").GetValue();
	      			vipModp += CombatPlugin.globalEventBonusesArray.get("ExpGain").GetValuePercentage();
	      		}
				int expwithBonus = (int) Math.round(Math.ceil(xpValInt + vipMod + xpValInt * vipModp / 100f));
				if(Log.loggingDebug)log.debug("EXP: playerOid=" + attackerOid + " calculated Exp=" + xpValInt + " with Bonus " + expwithBonus);
				CombatPlugin.addListsRankingData(attackerOid, AchievementsClient.EXPERIENCE, expwithBonus);
				CombatPlugin.addListsRankingData(attackerOid, AchievementsClient.KILL, target.getID());
					
				giveExp(attackerOid, expwithBonus );
			//	giveExp(attackerOid, xpValInt);
			} else {
				log.debug("no EXP Reson Max Level Difference");
			}

		}
	}

	static void calculatePetExp(CombatInfo info, int exp){
		if(Log.loggingDebug)Log.debug("calculatePetExp "+info+" exp="+exp);
		Serializable _activePetList = info.getProperty("activePetList");
		if(_activePetList==null)
			_activePetList = new HashMap<OID,Integer>();
		HashMap<OID,Integer> activePetList = (HashMap<OID,Integer>) _activePetList;
		if(Log.loggingDebug)Log.debug("calculatePetExp "+info+" exp="+exp);
		if(activePetList.size()>0){
			ArrayList<Integer> petProfiles =new ArrayList<Integer>();
			for (int v : activePetList.values()){
				if(!petProfiles.contains(v)){
					petProfiles.add(v);
				};
			}
			for (int v : petProfiles){
				if(Log.loggingDebug)Log.debug("calculatePetExp Add exp for profile "+v);

				AtavismDatabase adb = new AtavismDatabase();
				PetInfo pi = adb.loadPlayerPet(info.getOid(), v);
				if(Log.loggingDebug)Log.debug("calculatePetExp PetInfo "+pi);
				pi.experience += exp;
				PetProfile pp = Agis.PetProfile.get(v);
				if(Log.loggingDebug)Log.debug("calculatePetExp PetProfile "+pp);
				int e = pp.GetLevels().get(pi.level).getExperience();
				String ce = pp.GetLevels().get(pi.level).getLevelUpCoordEffect();
				if(Log.loggingDebug)Log.debug("calculatePetExp exp limit "+e+" ce "+ce);
				boolean levelUp = false;
				if(pi.experience > e && pp.GetLevels().size() > pi.level){
					pi.level = pi.level + 1;
					pi.experience -= e;
					levelUp = true;
					if(Log.loggingDebug)Log.debug("calculatePetExp Level up");
				}
				if(Log.loggingDebug)Log.debug("calculatePetExp PetInfo "+pi+ " after ex mod");
				adb.savePlayerPet(info.getOid(), v,pi);
				if(levelUp) {
					for (OID o : activePetList.keySet()) {
						if (activePetList.get(o) == v) {
							AgisMobClient.sendPetLevelUpdate(o);
						}
					}
					AgisMobClient.sendPetLevelUpdate(info.getOid(), v);
				}
			}
		}
	}


   public static void giveExp(OID oid, int exp) {
		if(Log.loggingDebug)Log.debug("EXP: exp alter hit, oid: " + oid + "; xp amount: " + exp);
	    CombatInfo info = CombatPlugin.getCombatInfo(oid);
		if(info == null){
			log.error("EXP: cant get CombatInfo for player oid "+oid);
			return;
		}
	    int level = info.statGetCurrentValue("level");
	    if (exp == 0) {
	    	return;
	    }
	    calculatePetExp(info, exp);


		if (levelXpRequirements.containsKey(info.getExpProfile())) 
		{
	    int curXP = info.statGetCurrentValue(EXPERIENCE_STAT);
			HashMap<Integer, LevelExpRequirement> requirements = levelXpRequirements.get(info.getExpProfile());
			if (requirements.containsKey(level)) {
				LevelExpRequirement requirement = requirements.get(level);
				info.statSetBaseValue(EXPERIENCE_MAX_STAT, requirement.expRequired);

				int maxXP = requirement.expRequired;
        curXP = curXP + exp;
        
        ChatClient.sendObjChatMsg(oid, 2, "You have received: " + exp + " experience points.");
        while (curXP > maxXP) {
        	// level up!
        	curXP = curXP - maxXP;
        	level = level + 1;
        	handleLevelUp(info, level);

					if (requirements.containsKey(level)) {
						requirement = requirements.get(level);
						maxXP = requirement.expRequired;
					}
        }
        info.statSetBaseValue(EXPERIENCE_STAT, curXP);
        Log.debug("EXP: experience stat is now: " + info.statGetBaseValue(EXPERIENCE_STAT) + ", " + curXP);
        
        String abilityEvent = EventMessageHelper.COMBAT_EXP_GAINED;
				EventMessageHelper.SendCombatEvent(info.getOwnerOid(), info.getOwnerOid(), abilityEvent, -1, -1, exp,
						-1);
			}
		}
    }
    
	public static void lostExp(OID oid, int exp) {
		Log.debug("lostExp EXP: exp alter hit, oid: " + oid + "; lost xp amount: " + exp);
		CombatInfo info = CombatPlugin.getCombatInfo(oid);
		int level = info.statGetCurrentValue("level");
		if (exp == 0) {
			return;
		}

		if (levelXpRequirements.containsKey(info.getExpProfile())) {
			int curXP = info.statGetCurrentValue(EXPERIENCE_STAT);
			HashMap<Integer, LevelExpRequirement> requirements = levelXpRequirements.get(info.getExpProfile());
			if (requirements.containsKey(level)) {
				LevelExpRequirement requirement = requirements.get(level);
				int maxXP = requirement.expRequired;
				Log.debug("lostExp EXP: oid: " + oid + "; curXP" + curXP + " maxXP=" + maxXP + " lost=" + exp);
				curXP = curXP - exp;
				Log.debug("lostExp EXP: oid: " + oid + "; curXP: " + exp + " LOST_LEVEL=" + LOST_LEVEL);

				if (LOST_LEVEL) {
					// ChatClient.sendObjChatMsg(oid, 2, "You have lost " + exp + " experience
					// points.");
					if(level == 1 && curXP < 0)
						curXP = 0;
					while (curXP < 0) {
						// level up!
						if (requirements.containsKey(level - 1)) {
							requirement = requirements.get(level - 1);
							maxXP = requirement.expRequired;
							curXP = curXP + maxXP;
							level = level - 1;
							handleLevelDown(info, level);
						}
					}
				} else {
					if (curXP < 0)
						curXP = 0;
				}

				info.statSetBaseValue(EXPERIENCE_STAT, curXP);
				Log.debug("lostExp EXP: oid=" + oid + " experience stat is now: "
						+ info.statGetBaseValue(EXPERIENCE_STAT) + ", " + curXP);

				// String abilityEvent = EventMessageHelper.COMBAT_EXP_GAINED;
				// EventMessageHelper.SendCombatEvent(info.getOwnerOid(), info.getOwnerOid(),
				// abilityEvent, -1, -1, exp, -1);

			}
		}
	}
    
    
    /**
     * This method calculates what a players stats should be based on their new level
     * @param player - the combat Info of the player levelling up
     * @param newLevel - the level the player is now
     */
    public static void handleLevelDown(CombatInfo player, int newLevel) {
    	Log.debug("EXP: level up. new level: " + newLevel);
    	
    	if (levelXpRequirements.containsKey(player.getExpProfile())) 
    	{
    		HashMap<Integer,LevelExpRequirement> requirements = levelXpRequirements.get(player.getExpProfile());
			if (requirements.containsKey(newLevel)) {
				LevelExpRequirement requirement = requirements.get(newLevel);
				player.statSetBaseValue(EXPERIENCE_MAX_STAT, requirement.expRequired);
			}
    	}
    	
    	// Get the character template based on race and class
    	int race = (Integer)EnginePlugin.getObjectProperty(player.getOwnerOid(), WorldManagerClient.NAMESPACE, "race");
    	int aspect = player.aspect();
    	CharacterTemplate tmpl = characterTemplates.get(race + " " + aspect);
    	// Increment their level and recalculate their stats
    	//player.statModifyBaseValue("dmg-base", 1);
    	int oldLevel = player.statGetCurrentValue("level");
    	player.statSetBaseValue("level", newLevel);
    	calculatePlayerStats(player, tmpl);
    	
    	//TODO: set vitality stats to max on level up?
    	
    	ClassAbilityClient.levelChange(player.getOwnerOid(), newLevel, oldLevel);
		//ChatClient.sendObjChatMsg(player.getOwnerOid(), 2, "Congratulations, you have reached level " + newLevel + "!");
	
    	CoordinatedEffect cE = new CoordinatedEffect("LevelDownEffect");
    	cE.sendSourceOid(true);
    	cE.invoke(player.getOwnerOid(), player.getOwnerOid());
    }
    
    
    /**
     * This method calculates what a players stats should be based on their new level
     * @param player - the combat Info of the player levelling up
     * @param newLevel - the level the player is now
     */
    public static void handleLevelUp(CombatInfo player, int newLevel) {
    	Log.debug("EXP: level up. new level: " + newLevel);
    	
    	if (levelXpRequirements.containsKey(player.getExpProfile())) 
    	{
    		HashMap<Integer,LevelExpRequirement> requirements = levelXpRequirements.get(player.getExpProfile());
    		if (requirements.containsKey(newLevel)) {
    			LevelExpRequirement requirement = requirements.get(newLevel);
	    		player.statSetBaseValue(EXPERIENCE_MAX_STAT, requirement.expRequired);
    		}
    	}
    	
    	// Get the character template based on race and class
    	int race = (Integer)EnginePlugin.getObjectProperty(player.getOwnerOid(), WorldManagerClient.NAMESPACE, "race");
    	int aspect = player.aspect();
    	CharacterTemplate tmpl = characterTemplates.get(race + " " + aspect);
    	// Increment their level and recalculate their stats
    	//player.statModifyBaseValue("dmg-base", 1);
    	int oldLevel = player.statGetCurrentValue("level");
    	player.statSetBaseValue("level", newLevel);
    	calculatePlayerStats(player, tmpl);
    	
    	//TODO: set vitality stats to max on level up?
    	
    	ClassAbilityClient.levelChange(player.getOwnerOid(), newLevel, oldLevel);

		ChatClient.sendObjChatMsg(player.getOwnerOid(), 2, "Congratulations, you have reached level " + newLevel + "!");
	
    	CoordinatedEffect cE = new CoordinatedEffect("LevelUpEffect");
    	cE.sendSourceOid(true);
    	cE.invoke(player.getOwnerOid(), player.getOwnerOid());
    }
    
    public static void calculatePlayerStats(CombatInfo info, CharacterTemplate tmpl) {
		int level = info.statGetBaseValue("level");
		// Update every stat based on the progression data for each stat the template has
		if (Log.loggingDebug)
			Log.debug("calculatePlayerStats: StatProfile : "+tmpl.getStatProfileId());

		if (tmpl.getStatProfileId() != 0) {
			StatProfile sp = CombatPlugin.STAT_PROFILES.get(tmpl.getStatProfileId());
			if (Log.loggingDebug)
				Log.debug("calculatePlayerStats: StatProfile : "+sp.getName());
			if (sp != null) {
				Log.debug("calculatePlayerStats: StatProfile stats count "+sp.getStats().size());
				Log.debug("calculatePlayerStats: Base stats");
				for (Map.Entry<Integer, Integer> e : sp.getStats().entrySet()) {
					if (Log.loggingDebug)
						Log.debug("calculatePlayerStats: Key=" + e.getKey() + " name=" + CombatPlugin.STAT_NAMES.get(e.getKey()) + " value: " + e.getValue());

					String statName = CombatPlugin.STAT_NAMES.get(e.getKey());
					AgisStatDef statDef = CombatPlugin.statDefMap.get(statName);
					if (statDef instanceof VitalityStatDef || statName.equals(CombatPlugin.EXPERIENCE_MAX_STAT)) {
						if (Log.loggingDebug)Log.debug("calculatePlayerStats: "+statName+" skip Vitality?");
						continue;
					}
					int baseValue = e.getValue();
					float levelIncrease = sp.getStatsLevelIncrease().get(e.getKey());
					float levelPercentIncrease = sp.getStatsLevelPercentIncrease().get(e.getKey());
					float value = baseValue + ((level - 1) * levelIncrease);
					value += value * ((level - 1) * (levelPercentIncrease / 100));
					if (info.getProperty(statName) == null) {
						// Add the stat to the player
						AgisStat newStat = new AgisStat(statDef.getId(), statName);
						info.setProperty(statName, newStat);
					}
					info.statSetBaseValue(statName, (int) value);
				}
				if (Log.loggingDebug)Log.debug("calculatePlayerStats: Vitality stats ");
				for (Map.Entry<Integer, Integer> e : sp.getStats().entrySet()) {
					String statName = CombatPlugin.STAT_NAMES.get(e.getKey());
					if (Log.loggingDebug)Log.debug("calculatePlayerStats: Vitality stat "+statName);
					AgisStatDef statDef = CombatPlugin.statDefMap.get(statName);
					if (statDef instanceof VitalityStatDef) {
						VitalityStatDef vstatDef = (VitalityStatDef) statDef;
						AgisStat as = new AgisStat(e.getKey(), statName);
						info.setProperty(statName, as);
						// Set it to the start percent
						int startingValue = 1;
						startingValue = vstatDef.getStartingValue(info);
						info.statSetBaseValue(statName, startingValue);

						// Add the vitality stat listing to the Combat Info
						if (Log.loggingDebug)Log.debug("STAT: adding vitality stat with shift inteval: " + vstatDef.getShiftInterval());
						if ((vstatDef.checkShiftTarget(info)) && vstatDef.getShiftInterval() > 0) {
							info.addVitalityStat(as, vstatDef.getShiftInterval());
						}
					}else{
						if (Log.loggingDebug)	Log.debug("calculatePlayerStats: Vitality stat "+statName+" skip");
					}
				}

				for (String statName : sp.getStatsToDelete()){
					if (Log.loggingDebug)Log.debug("calculatePlayerStats: stat "+statName+" to delete ");
					if (info.getProperty(statName) != null) {
						info.removeProperty(statName);
						if (Log.loggingDebug)Log.debug("calculatePlayerStats: stat "+statName+" deleted ");
					}else{
						if (Log.loggingDebug)Log.debug("calculatePlayerStats: stat "+statName+" not found ");
					}
				}

			}
		} else {
			if (Log.loggingDebug)Log.debug("calculatePlayerStats: getStartingStats no Profile Base Stats");
			for (String statName : tmpl.getStartingStats().keySet()) {
				if (Log.loggingDebug)	Log.debug("calculatePlayerStats: getStartingStats statName ="+statName);
				if (CombatPlugin.statDefMap.get(statName) instanceof VitalityStatDef) {
					if (Log.loggingDebug)Log.debug("calculatePlayerStats: getStartingStats statName ="+statName+" skip Vitality ");
					continue;
				}

				AgisStatDef s = CombatPlugin.statDefMap.get(statName);
					int baseValue = tmpl.getStartingStats().get(statName).baseValue;
					float levelIncrease = tmpl.getStartingStats().get(statName).levelIncrease;
					float levelPercentIncrease = tmpl.getStartingStats().get(statName).levelPercentIncrease;
					float value = baseValue + ((level - 1) * levelIncrease);
					value += value * ((level - 1) * (levelPercentIncrease / 100));

					if (info.getProperty(statName) == null) {
						// Add the stat to the player
						AgisStat newStat = new AgisStat(s.getId(), statName);
						info.setProperty(statName, newStat);
					}
					info.statSetBaseValue(statName, (int) value);
//				}else{
//					if (info.getProperty(statName) != null) {
//						// Add the stat to the player
//						info.removeProperty(statName);
//					}
//				}
			}
			if (Log.loggingDebug)	Log.debug("calculatePlayerStats: getStartingStats no Profile Vitality stat" );
			for (String stat : tmpl.getStartingStats().keySet()) {
				if (Log.loggingDebug)Log.debug("calculatePlayerStats: getStartingStats no Profile stat "+stat );
				if (CombatPlugin.statDefMap.get(stat) instanceof VitalityStatDef) {
					if (info.getProperty(stat) == null) {
						VitalityStatDef statDef = (VitalityStatDef) CombatPlugin.statDefMap.get(stat);
						AgisStat astat = new AgisStat(statDef.getId(), stat);
						info.setProperty(stat, astat);
						// Set it to the start percent
						int startingValue = 1;
						startingValue = statDef.getStartingValue(info);
						info.statSetBaseValue(stat, startingValue);
						// stat.setBase(startingValue);

						// Add the vitality stat listing to the Combat Info
						if (Log.loggingDebug)Log.debug("STAT: adding vitality stat with shift inteval: " + statDef.getShiftInterval());
						if ((statDef.checkShiftTarget(info)) && statDef.getShiftInterval() > 0) {
							info.addVitalityStat(astat, statDef.getShiftInterval());
						}
					} else {
						if (Log.loggingDebug)Log.debug("calculatePlayerStats: getStartingStats no Profile stat "+stat+" skip stat not null?" );

					}
				}else{
					if (Log.loggingDebug)	Log.debug("calculatePlayerStats: getStartingStats no Profile stat "+stat+" skip base?" );

				}
			}
		}
		if (levelXpRequirements.containsKey(info.getExpProfile())) {
			HashMap<Integer, LevelExpRequirement> requirements = levelXpRequirements.get(info.getExpProfile());
			if (requirements.containsKey(level)) {
				LevelExpRequirement requirement = requirements.get(level);
				info.statSetBaseValue(EXPERIENCE_MAX_STAT, requirement.expRequired);
			}
		}

		if (Log.loggingDebug)Log.debug("calculatePlayerStats: End");
	}

    /**
     * This method handles leveling the player profession based on the level that they have reached.
     *
     * Modifications are based on the leveling map for the profession in question as well as the stat's
     * own leveling map, if applicable.
     *
     * @param player
     * @param lvl
     */
    public static void handleLevelingProfessionPlayer(CombatInfo player, int lvl) {
        // first we need to get a hold of the players profession if it exists
        String profession = null;
        try {
            profession = (String)EnginePlugin.getObjectProperty(player.getOid(), Namespace.WORLD_MANAGER, "class");
        } catch(Exception e)  {

        }
        if (profession == null) {
            return;
        }
        ProfessionObject po = professions.get(profession);

        // now now get the leveling map for the profession
        LevelingMap lm = po.getLevelingMap();

        // now go through every proprety of the combatinfo and check to see if it is an agis stat or not
        for (String propname : player.getPropertyMap().keySet()) {
            // check to make sure we aren't dealing with the experience stat (as that controls leveling)
            // that this is indeed a base stat, and that it is also an instance of the AgisStat (in case something
            // went wrong and it was never created as a AgisStat).
            if (!propname.equals(CombatPlugin.EXPERIENCE_STAT) && po.isBaseStat(propname) && player.getProperty(propname) instanceof AgisStat) {
                // this is an agis stat so this is something maybe modified according to the profession map
                // do initial modification of full stats based on leveling map 0
                log.debug("Leveling up stat " + propname);
                AgisStat stat = (AgisStat)player.getProperty(propname);
                // this update call could be done when stats are finishing being setup. so bypass..
                log.debug("Leveling up " + propname + " : " + stat);
                int checker = stat.base; // setup a catch to see if we need to mark it as dirty or not at the end

                log.debug("Base " + propname + " base stat: " + stat.base);
                stat.base += ((Float)((stat.base * lm.getLevelPercentageModification(0)))).intValue() + lm.getLevelFixedAmountModification(0);
                log.debug(propname + " base stat after global modification: " + stat.base);

                // check to see if there is a leveling map for this level
                if (lm.hasLevelModification(lvl)) {
                    // now apply it to the stats as well
                    stat.base += ((Float)((stat.base * lm.getLevelPercentageModification(lvl)))).intValue() + lm.getLevelFixedAmountModification(lvl);
                    log.debug(propname + " base stat after level modification: " + stat.base);
                }

                // now to find out if this stat has it's own level map
                if (po.hasStatLevelModification(propname, lvl)) {
                    // we do have a level modification for this stat, so lets make it happen
                    LevelingMap statlm = po.getStatsLevelingMap(propname);
                    stat.base += ((Float)((stat.base * statlm.getLevelPercentageModification(lvl)))).intValue() + statlm.getLevelFixedAmountModification(lvl);
                    log.debug(propname + " base stat after stat modification: " + stat.base);
                }

                log.debug(propname + " checking comparison: " + checker + " : " + stat.base);
                if (checker != stat.base) {
                    stat.current = stat.max = stat.base;
                    // the stat has changed value
                    player.setProperty(propname, stat);
                    stat.setDirty(true);
                    log.debug(propname + " updating base stat def");
                    CombatPlugin.getBaseStatDef(propname).update(stat, player);
                }
            }
        }
    }

    public static void handleSkillAbilityRanking(ClassAbilityObject player, int statid, int lvl) {
        /*LevelingMap lm;

        // In this case the skill/ability itself stores the leveling map so grab it from the stat.
        if (Agis.SkillManager.get(statid) != null){
            //lm = Agis.SkillManager.get(statid).getLevelingMap();
        }
        else if (Agis.AbilityManager.get(statid) != null) {
            lm = Agis.AbilityManager.get(statid).getLevelingMap();
        }
        else {
            // Nothing was found, so do nothing..
            return;
        }

        // Now grab the stat that is to be modified
        AgisStat stat = (AgisStat)player.getProperty(statid + "_exp");

        // this update call could be done when stats are finishing being setup. so bypass..
        log.debug("Leveling up " + statid + " : " + stat);
        int checker = stat.max; // setup a catch to see if we need to mark it as dirty or not at the end

        log.debug("Max " + statid + " max stat: " + stat.max);
        stat.max += (new Float((stat.max * lm.getLevelPercentageModification(0)))).intValue() + lm.getLevelFixedAmountModification(0);
        log.debug(statid + " max stat after global modification: " + stat.max);

        // check to see if there is a leveling map for this level
        if (lm.hasLevelModification(lvl)) {
            // now apply it to the stats as well
            stat.max += (new Float((stat.max * lm.getLevelPercentageModification(lvl)))).intValue() + lm.getLevelFixedAmountModification(lvl);
            log.debug(statid + " max stat after level modification: " + stat.max);
        }

        log.debug(statid + " checking comparison: " + checker + " : " + stat.max);
        if (checker != stat.max) {
            // the stat has changed value
            player.setProperty(statid+"_exp", stat);
            stat.setDirty(true);
            log.debug(statid + " updating base stat def");
            ClassAbilityPlugin.lookupStatDef(statid+"_exp").update(stat, player);
        }*/
    	return;
    }
    
    class AlterExpHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    	    CombatClient.alterExpMessage QERMsg = (CombatClient.alterExpMessage) msg;
    	    OID oid = QERMsg.getSubject();
    	    int xpReward = QERMsg.getXpAmount();
    	    giveExp(oid, xpReward);
    	    return true;
    	}
    }
    
    /*
     * Skill Progression Code.
     * This may be moved later on.
     */
 
    
    class PurchaseSkillPointHook implements Hook  {
    	public boolean processMessage(Message msg, int flags) {
    		ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID oid = eMsg.getSubject();
            Log.debug("SKILL: got purchase skill point for: " + oid);
            CombatInfo info = CombatPlugin.getCombatInfo(oid);
            SkillInfo sInfo = info.getCurrentSkillInfo();
            // work out cost based on how many points the player has bought
            int totalPoints = sInfo.getPointsSpent() + sInfo.getSkillPoints();
            int cost = GetSkillPointCost(totalPoints);
            // Does the player have enough xp to buy a point?
            int curXP = info.statGetCurrentValue(CombatPlugin.EXPERIENCE_STAT);
            if (curXP < cost) {
            	Log.debug("SKILL: player: " + oid + " does not have enough experience to purchase a skill point");
            	return true;
            }
            sInfo.setSkillPoints(sInfo.getSkillPoints() + 1);
            info.statSetBaseValue(CombatPlugin.EXPERIENCE_STAT, curXP - cost);
            Log.debug("SKILL: player: " + oid + " has purchased a skill point");
            ExtendedCombatMessages.sendSkills(oid, info.getCurrentSkillInfo());
       // 	ExtendedCombatMessages.sendAbilities(oid, info.getCurrentAbilities());
        	 return true;
    	}
    }
    
    public static int GetSkillPointCost(int pointsPurchased) {
    	return pointsPurchased * 100 + 100;
    }
    
    class SetSkillStateHook implements Hook  {
    	public boolean processMessage(Message msg, int flags) {
    		ExtensionMessage eMsg = (ExtensionMessage) msg;
            OID oid = eMsg.getSubject();
            Log.debug("SKILL: got set skill state for: " + oid);
            int skillID = (Integer) eMsg.getProperty("skillID");
            int state = (Integer) eMsg.getProperty("state");
            CombatInfo info = CombatPlugin.getCombatInfo(oid);
            SkillInfo sInfo = info.getCurrentSkillInfo();
            SkillData skillData = sInfo.getSkills().get(skillID);
            skillData.setState(state);
            Engine.getPersistenceManager().setDirty(info);
            
            Log.debug("SKILL: player: " + oid + " has changed the state of a skill to: " + skillData.getState());
            ExtendedCombatMessages.sendSkills(oid, info.getCurrentSkillInfo());
       // 	ExtendedCombatMessages.sendAbilities(oid, info.getCurrentAbilities());
        	     return true;
    	}
    }
    
    class SkillIncreaseHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ClassAbilityClient.skillIncreasedMessage EBMsg = (ClassAbilityClient.skillIncreasedMessage) msg;
    		OID oid = EBMsg.getSubject();
    		// Check if the server is set to use Skill Points
    		
    	    
    	    int skillType = EBMsg.getSkillID();
    	    Log.debug("SKILL: got SkillIncreaseMessage with skill: " + skillType);
    	    CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);
    	    SkillInfo.increaseSkill(cInfo.getCurrentSkillInfo(), skillType, cInfo.aspect(), cInfo, false, false);
    	    ClassAbilityClient.skillLevelChange(oid);
    	    ExtendedCombatMessages.sendSkills(oid, cInfo.getCurrentSkillInfo());
    	//	ExtendedCombatMessages.sendAbilities(oid, cInfo.getCurrentAbilities());
        	return true;
    	}
    }
    
    class SkillDecreaseHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ClassAbilityClient.skillDecreasedMessage EBMsg = (ClassAbilityClient.skillDecreasedMessage) msg;
    	    OID oid = EBMsg.getSubject();
    	    int skillType = (Integer) EBMsg.getProperty("skillType");
    	    CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);
    	    SkillInfo.decreaseSkill(cInfo.getCurrentSkillInfo(), skillType, cInfo.aspect(), cInfo);
    	    ExtendedCombatMessages.sendSkills(oid, cInfo.getCurrentSkillInfo());
    	//	ExtendedCombatMessages.sendAbilities(oid, cInfo.getCurrentAbilities());
        	  return true;
    	}
    }
    
    class GetSkillDiffExpHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		PropertyMessage EBMsg = (PropertyMessage) msg;
    	    OID oid = EBMsg.getSubject();
    	    int skillType = (Integer) EBMsg.getProperty("skillType");
    	    int level = (Integer) EBMsg.getProperty("level");
       	    Log.debug("SKILL: Ability used of skill type: " + skillType);
    	    CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);
    	    float diff =  SkillInfo.skillDiffExp(cInfo.getCurrentSkillInfo(), skillType, cInfo, level);
    	   
    		Engine.getAgent().sendObjectResponse(msg, diff);
    	    
    	    return true;
    	}
    }
    
    
    class AbilityUsedHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    	    CombatClient.abilityUsedMessage EBMsg = (CombatClient.abilityUsedMessage) msg;
    	    OID oid = EBMsg.getSubject();
    	    int skillType = (Integer) EBMsg.getProperty("skillType");
    	    int experience = (Integer) EBMsg.getProperty("experience");
    	    int level = (Integer) EBMsg.getProperty("level");
        	    Log.debug("SKILL: Ability used of skill type: " + skillType+" experience:"+experience+" level:"+level);
    	    CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);
    	    SkillInfo.skillUpAttempt(cInfo.getCurrentSkillInfo(), skillType, cInfo, experience, level);
    	    ExtendedCombatMessages.sendSkills(oid, cInfo.getCurrentSkillInfo());
    	//	ExtendedCombatMessages.sendAbilities(oid, cInfo.getCurrentAbilities());
    		  
    	    return true;
    	}
    }
    
    class SkillResetHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ClassAbilityClient.skillResetMessage EBMsg = (ClassAbilityClient.skillResetMessage) msg;
    	    OID oid = EBMsg.getSubject();
    	    CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);
    	    boolean skill = (boolean) EBMsg.getProperty("skill");
    	    log.debug("SkillResetHook: oid="+oid+" skill="+skill+" cInfo="+cInfo);
     	    if(skill)
    	    	SkillInfo.resetSkills(cInfo.getCurrentSkillInfo(), cInfo);
    	    else
    	    	SkillInfo.resetTalents(cInfo.getCurrentSkillInfo(), cInfo);
        	ExtendedCombatMessages.sendSkills(oid, cInfo.getCurrentSkillInfo());
    		ExtendedCombatMessages.sendAbilities(oid, cInfo.getCurrentAbilities());
    		ExtendedCombatMessages.sendActions(cInfo.getOwnerOid(), cInfo.getCurrentActions(), cInfo.getCurrentActionBar());
			
        	 log.debug("SkillResetHook: End");
        	 return true;
    	}
    }
    
  /*  class SkillPointsHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ClassAbilityClient.skillPointsMessage EBMsg = (ClassAbilityClient.skillPointsMessage) msg;
    	    OID oid = EBMsg.getSubject();
    	    CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);
    	    boolean skill = (boolean) EBMsg.getProperty("skill");
    	    log.debug("SkillPointsHook: oid="+oid+" skill="+skill+" cInfo="+cInfo);
    	    int points = (int) EBMsg.getProperty("points");
    	    log.debug("SkillPointsHook: oid="+oid+" skill="+skill+" points="+points);
    	    if(skill)
    	    	SkillInfo.addSkillPoints(cInfo.getCurrentSkillInfo(), cInfo, points);
    	    else
    	    	SkillInfo.addTalentPoints(cInfo.getCurrentSkillInfo(), cInfo, points);
        	ExtendedCombatMessages.sendSkills(oid, cInfo.getCurrentSkillInfo());
    		ExtendedCombatMessages.sendAbilities(oid, cInfo.getCurrentAbilities());
        	  log.debug("SkillPointsHook: End");
        	  
        	return true;
    	}
    }*/
    
    class AddSkillPointHook implements Hook  {
    	public boolean processMessage(Message msg, int flags) {
    		PropertyMessage eMsg = (PropertyMessage) msg;
            OID oid = eMsg.getSubject();
            int points = (int)eMsg.getProperty("points");
            boolean skill = (boolean) eMsg.getProperty("skill");
            log.debug("AddSkillPointHook: oid="+oid+" skill="+skill+" points="+points);
     	   
            CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);
            log.debug("AddSkillPointHook: oid="+oid+" skill="+skill+" cInfo="+cInfo);
            //  SkillInfo sInfo = info.getCurrentSkillInfo();
            // work out cost based on how many points the player has bought
            
         //   sInfo.setSkillPoints(sInfo.getSkillPoints() + points);
            if(skill)
    	    	SkillInfo.addSkillPoints(cInfo.getCurrentSkillInfo(), cInfo, points);
    	    else
    	    	SkillInfo.addTalentPoints(cInfo.getCurrentSkillInfo(), cInfo, points);
            Log.debug("AddSkillPointHook: player: " + oid + " has purchased a skill point");
            ExtendedCombatMessages.sendSkills(oid, cInfo.getCurrentSkillInfo());
       // 	ExtendedCombatMessages.sendAbilities(oid, info.getCurrentAbilities());
            log.debug("AddSkillPointHook: End");
        		 return true;
    	}
    }
    
    
    class SkillGetHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ClassAbilityClient.skillGetMessage EBMsg = (ClassAbilityClient.skillGetMessage) msg;
    		int templateNum = (Integer) EBMsg.getProperty("templateNum");
    		Log.debug("SKILL: Getting skill template");
    		Engine.getAgent().sendObjectResponse(msg, Agis.SkillManager.get(templateNum));
    		return true;
    	}
    }
    
    /**
     * Hook for the GetPlayerSkillLevelMessage. Gets the level of the specified skill for the player in question
     * and sends it back to the remote call procedure that sent the message out.
     * @author Andrew Harrison
     *
     */
    class GetSkillLevelHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ClassAbilityClient.GetPlayerSkillLevelMessage EBMsg = (ClassAbilityClient.GetPlayerSkillLevelMessage) msg;
    		Log.debug("SKILL: got GetPlayerSkillLevelMessage");
    		OID oid = EBMsg.getSubject();
    	    int skillType = EBMsg.getSkillType();
    	    CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);
    	    int skillLevel = -1;
    	    if (cInfo.getCurrentSkillInfo().getSkills().containsKey(skillType)) {
    	    	skillLevel = cInfo.getCurrentSkillInfo().getSkills().get(skillType).getSkillLevel();
    	    }
    		
    		Engine.getAgent().sendIntegerResponse(msg, skillLevel);
    		return true;
    	}
    }
    
    class LevelChangeHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ClassAbilityClient.levelChangeMessage EBMsg = (ClassAbilityClient.levelChangeMessage) msg;
    		OID oid = EBMsg.getSubject();
    		int newLevel = EBMsg.getLevel();
    		int oldLevel = EBMsg.getOldLevel();
    		CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);    		
    		Integer highestLevel = (Integer)cInfo.getProperty(CombatInfo.HIGHEST_LEVEL_GAINED); 
    		Integer lowestLevel = (Integer)cInfo.getProperty(CombatInfo.LOWEST_LEVEL_GAINED);
    		//log.error("LevelChangeHook: oid="+oid+" oldLevel "+oldLevel+" newLevel="+newLevel+" highestLevel="+highestLevel+" lowestLevel="+lowestLevel);
    		
			if (highestLevel == null) {
				highestLevel = oldLevel;
			}
			if (lowestLevel == null) {
				lowestLevel = oldLevel;
			}
    		//log.error("LevelChangeHook: oid="+oid+" oldLevel "+oldLevel+" newLevel="+newLevel+" | highestLevel="+highestLevel+" lowestLevel="+lowestLevel);
    		
			if (newLevel >= highestLevel) 
			{
				//Set highest level to new highest level gained
				cInfo.setProperty(CombatInfo.HIGHEST_LEVEL_GAINED, newLevel);
			}
			else if (newLevel < oldLevel)
			{
				handlePreviousLevelExpRewards(oid, cInfo, oldLevel);
				//Set lowest level to new lowest level gained
				if (newLevel <= lowestLevel)
				{					
					cInfo.setProperty(CombatInfo.LOWEST_LEVEL_GAINED, newLevel);					
				}
			}
			
			//Only give out rewards if the player has leveled up not leveled down
			//if (newLevel > oldLevel) {
				handleLevelExpRewards(oid, cInfo, newLevel, lowestLevel, highestLevel);
			//}

    		if (USE_SKILL_PURCHASE_POINTS ||USE_TALENT_PURCHASE_POINTS) {
    			SkillInfo.levelChanged(cInfo.getCurrentSkillInfo(), cInfo, newLevel);
    		}
    	    ExtendedCombatMessages.sendSkills(oid, cInfo.getCurrentSkillInfo());
    	//	ExtendedCombatMessages.sendAbilities(oid, cInfo.getCurrentAbilities());
    	    //log.error("LevelChangeHook: oid="+oid+" End");
        	  return true;
    	}
    	
    	//Used for when a level has been lost and certain rewards need be removed.
    	private void handlePreviousLevelExpRewards(OID oid, CombatInfo cInfo, int oldLevel)
    	{
    		//log.error("handlePreviousLevelExpRewards: oid="+oid+" oldLevel "+oldLevel);
    		HashMap<Integer, LevelExpRequirement> requirements = levelXpRequirements.get(cInfo.getExpProfile());
    		if (requirements.containsKey(cInfo.getExpProfile())) 
    		{
				LevelExpRequirement requirement = requirements.get(oldLevel);
				if (requirement.rewardTemplate != null && requirement.rewardTemplate.getRewards().size() > 0)
				{	
					LevelExpRequirement.RewardTemplate rewardTemplate = requirement.rewardTemplate;
					for (LevelExpRequirement.Reward reward : rewardTemplate.getRewards()) 
					{
						//log.error("handlePreviousLevelExpRewards: oid="+oid+" reward="+reward);
	    				switch (reward.reward_type) 
	    				{
		    				case ABILITY:
								AgisAbility ab = Agis.AbilityManager.get(reward.reward_value);
								if (ab != null) 
								{
									if (ab.getAbilityType() == 2) {								
										SkillInfo.removePassiveEffect(ab, cInfo);
									} 
								}
								break;
							case EFFECT:
								//CombatClient.removeEffect(oid, reward.reward_value, 1);
								HashMap<String, Serializable> params = new HashMap<String, Serializable>();
								params.put("ignoreDead", true); //Used to re-apply effects if it only removes some of the stacks and the player is dead.
								AgisEffect.removeEffectByID(cInfo, reward.reward_value, 1, params);
								break;	
		    				default:
		    					break;
	    				}
					}
				}
    		}
    		//log.error("handlePreviousLevelExpRewards: End"); 
    	}

		private void handleLevelExpRewards(OID oid, CombatInfo cInfo, int newLevel, int lowestLevel, int highestLevel) {
			//log.error("handleLevelExpRewards: oid="+oid+" newLevel "+newLevel+" lowestLevel "+lowestLevel+" highestLevel "+highestLevel+" profile "+cInfo.getExpProfile());
			
			HashMap<Integer, LevelExpRequirement> requirements = levelXpRequirements.get(cInfo.getExpProfile());
			if (requirements.containsKey(cInfo.getExpProfile())) {
				LevelExpRequirement requirement = requirements.get(newLevel);
				if (requirement!=null && requirement.rewardTemplate != null && requirement.rewardTemplate.getRewards().size() > 0) {
					HashMap<Integer, Integer> itemsToGenerate = new HashMap<Integer, Integer>();
					HashMap<Integer, Integer> itemsToMail = new HashMap<Integer, Integer>();
					LevelExpRequirement.RewardTemplate rewardTemplate = requirement.rewardTemplate;
					for (LevelExpRequirement.Reward reward : rewardTemplate.getRewards()) 
					{	
						//log.error("handleLevelExpRewards: oid="+oid+" reward="+reward);
						boolean skipReward = false;
						if (reward.on_level_down) {							
							skipReward = lowestLevel <= newLevel;							
						} else {
							skipReward = highestLevel >= newLevel;							
						}
						//Skip reward if it has already been given out.
						if (reward.give_once && skipReward) {							
							continue;
						}
						int amount = reward.reward_amount;
						switch (reward.reward_type) {
						case ITEM:
							int template_id = reward.reward_value;
							if (itemsToGenerate.containsKey(template_id)) {
								amount += itemsToGenerate.get(template_id);
							}
							itemsToGenerate.put(template_id, amount);
							break;
						case ITEM_MAIL:
							template_id = reward.reward_value;
							if (itemsToMail.containsKey(template_id)) {
								amount += itemsToMail.get(template_id);
							}
							itemsToMail.put(template_id, amount);
							break;
						case SKILL_POINT:
							SkillInfo.addSkillPoints(cInfo.getCurrentSkillInfo(), cInfo, reward.reward_amount);
							break;
						case TALENT_POINT:
							SkillInfo.addTalentPoints(cInfo.getCurrentSkillInfo(), cInfo, reward.reward_amount);
							break;
						case ABILITY:
							AgisAbility ab = Agis.AbilityManager.get(reward.reward_value);
							if (ab != null) 
							{
								if (ab.getAbilityType() == 2) {
									SkillInfo.applyPassiveEffects(ab, cInfo);
								} else {
									CombatClient.startAbility(reward.reward_value, oid, oid, null, null);
								}
							}
							break;
						case EFFECT:
							//CombatClient.applyEffect(oid, reward.reward_value);
							AgisEffect effect = Agis.EffectManager.get(reward.reward_value);
							//Log.error("apply effect  "+reward.reward_value+" effect="+effect);
							AgisEffect.applyEffect(effect, cInfo, cInfo, -1);		
							break;
						}
					}

					//Log.error("handleLevelExpRewards itemsToGenerate  "+itemsToGenerate+" itemsToMail="+itemsToMail);
					if (itemsToGenerate.size() > 0) {
						HashMap<Integer, Integer> leftOverItems = AgisInventoryClient.generateItems(oid,
								itemsToGenerate, false); //Changed from true to false
						if (itemsToMail.size() > 0) {
							for (Map.Entry<Integer, Integer> leftOverItem : leftOverItems.entrySet()) {
								boolean foundItem = false;
								for (Map.Entry<Integer, Integer> items : itemsToMail.entrySet()) {
									if (items.getKey() == leftOverItem.getKey()) {
										itemsToMail.put(items.getKey(), items.getValue() + leftOverItem.getValue());
										foundItem = true;
										break;
									}
								}
								if (!foundItem) {
									itemsToMail.put(leftOverItem.getKey(), leftOverItem.getValue());
								}
							}
						} else {
							itemsToMail.putAll(leftOverItems);
						}
					}

					if (itemsToMail.size() > 0) {
						String playerName = Engine.getDatabase().getObjectName(oid, WorldManagerClient.NAMESPACE);

						// Replaces {PLAYER_NAME} and {PLAYER_LEVEL} in custom subject / message with playerName and Level
					
						String mailMessage = requirement.rewardTemplate.mailMessage;
						mailMessage = mailMessage.replace("{PLAYER_NAME}", playerName);
						mailMessage = mailMessage.replace("{PLAYER_LEVEL}", newLevel+"");
						
						String mailSubject = requirement.rewardTemplate.mailSubject;
						mailSubject = mailSubject.replace("{PLAYER_NAME}", playerName);
						mailSubject = mailSubject.replace("{PLAYER_LEVEL}", newLevel+"");

						// Set Default Mail Message if empty, needs localization support?
						if (mailSubject == "")
							mailSubject = "Level " + newLevel + " Reward";
						if (mailMessage == "")
							mailMessage = "Congratulations " + playerName + " for reaching level " + newLevel + " attached are your rewards";

						// Max mail item attachment size = 10 split the mails in parts if total items exceed that
						if (itemsToMail.size() > 10) {
							HashMap<Integer, Integer> itemsToMailInPart = new HashMap<Integer, Integer>();
							int part = 1;
							int i = 0;

							for (Map.Entry<Integer, Integer> item : itemsToMail.entrySet()) {
								if (i / part == 10) {
									AgisInventoryClient.sendSystemMail(oid, oid, mailSubject + " Part: " + part,
											mailMessage, -1, 0, itemsToMailInPart);
									itemsToMailInPart.clear();
									part++;
								}
								itemsToMailInPart.put(item.getKey(), item.getValue());
								i++;
							}
							if (itemsToMailInPart.size() > 0) {
								AgisInventoryClient.sendSystemMail(oid, oid, mailSubject + " Part: " + part,
										mailMessage, -1, 0, itemsToMailInPart);
								itemsToMailInPart.clear();
							}
						} else {
							AgisInventoryClient.sendSystemMail(oid, oid, mailSubject, mailMessage, -1, 0, itemsToMail);
						}
					}
				}
			}
			//Log.error("handleLevelExpRewards End");
		}
    }
    
    class IncreaseSkillCurrentHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ClassAbilityClient.skillAlterCurrentMessage EBMsg = (ClassAbilityClient.skillAlterCurrentMessage) msg;
    	    OID oid = EBMsg.getSubject();
    	    int skillType = EBMsg.getSkillType();
    	    int alterValue = EBMsg.getAlterAmount();
    	    boolean admin = EBMsg.getAdmin();
     	    Log.debug("SKILL: Ability used of skill type: " + skillType+" alterValue="+alterValue+" admin="+admin);
    	    CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);
    	    if(cInfo==null)
    	    	return true;
    	    //SkillInfo.increaseSkillCurrent(cInfo.getCurrentSkillInfo(), skillType, alterValue, cInfo);
    	    for (int i = 0; i < alterValue; i++) {
    	    	Log.debug("SKILL: Ability used of skill type: " + skillType+" for");
    	    	SkillInfo.increaseSkill(cInfo.getCurrentSkillInfo(), skillType, cInfo.aspect(), cInfo, true,admin);
    	    }
    	   ClassAbilityClient.skillLevelChange(oid);
     	   ExtendedCombatMessages.sendSkills(oid, cInfo.getCurrentSkillInfo());
     	//	ExtendedCombatMessages.sendAbilities(oid, cInfo.getCurrentAbilities());
        	  return true;
    	}
    }
    
    /**
     * Adds the given ability to the players list of known abilities.
     * @author Andrew Harrison
     *
     */
    class LearnAbilityHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ClassAbilityClient.LearnAbilityMessage laMsg = (ClassAbilityClient.LearnAbilityMessage) msg;
    	    OID oid = laMsg.getSubject();
    	    CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);
    	    SkillInfo.learnAbility(cInfo, laMsg.getAbilityID());
    	    return true;
    	}
    }
    
    /**
     * Removes the given ability from the players list of known abilities.       
     */
    class UnlearnAbilityHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ClassAbilityClient.UnlearnAbilityMessage laMsg = (ClassAbilityClient.UnlearnAbilityMessage) msg;
    	    OID oid = laMsg.getSubject();
    	    CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);    	    
    	    SkillInfo.unlearnAbility(cInfo, laMsg.getAbilityID());
    	    return true;
    	}
    }
    
    /**
     * Hook for the UpdateActionBarMessage. Attempts to add the new action to the players
     * action bar then sends down the action bar info to the player.
     * @author Andrew Harrison
     *
     */
    class UpdateActionBarHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
			ExtensionMessage UABMsg = (ExtensionMessage) msg;
			OID oid = UABMsg.getSubject();
			int bar = (Integer) UABMsg.getProperty("bar");
			int slot = (Integer) UABMsg.getProperty("slot");
			String action = (String) UABMsg.getProperty("action");

			boolean swap = false;
			String swapAction = "";

			if (Log.loggingDebug)
				log.debug("UpdateActionBarHook: "+oid+" "+bar+" "+slot+" "+action);

			CombatInfo cInfo = CombatPlugin.getCombatInfo(oid);
			ArrayList<ArrayList<String>> actionBars = cInfo.getCurrentActions();
			while (actionBars.size() <= bar) {
				actionBars.add(new ArrayList<String>());
			}
			ArrayList<String> actions = actionBars.get(bar);
			while (actions.size() <= slot) {
				actions.add("");
			}

			// Added Checks to see if the slot already contains an ability, if so mark it to be swapped with sourceSlot
			if(ACTION_BAR_SWAP_ACTIONS) {
				if (actions.get(slot).length() > 0) {
					swap = true;
					swapAction = actions.get(slot);
					if (Log.loggingDebug)
						Log.debug("UpdateActionBarHook: swapAction is " + swapAction);
				}
			}
			actions.set(slot, action);

			// Now check if it came from a source slot, which will then need to be cleared or swapped if it was another ability
			boolean movingSlot = (Boolean) UABMsg.getProperty("movingSlot");
			if (movingSlot) {
				if (Log.loggingDebug)
					Log.debug("UpdateActionBarHook: movingSlot");
				bar = (Integer) UABMsg.getProperty("sourceBar");
				slot = (Integer) UABMsg.getProperty("sourceSlot");
				actions = actionBars.get(bar);
				while (actions.size() <= slot) {
					actions.add("");
				}
				if (swap == false)
					actions.set(slot, "");
				else
					actions.set(slot, swapAction);
			}

			cInfo.setCurrentActions(actionBars);
			ExtendedCombatMessages.sendActions(oid, cInfo.getCurrentActions(), cInfo.getCurrentActionBar());
			return true;
    	}
    }
    
    public static int GetStartingXpReq(int xpProfile) 
    {
    	if (levelXpRequirements.containsKey(xpProfile)) 
    	{
    		HashMap<Integer, LevelExpRequirement> requirements = levelXpRequirements.get(xpProfile);
    		if (requirements.containsKey(1)) 
        	{
        		LevelExpRequirement requirement = requirements.get(1);
        		return requirement.expRequired;
        	}
    	else
        		return 0;    		
    	} else
    		return 0;
    }
    
    public static CharacterTemplate getCharacterTemplate(String key) {
    	if(!characterTemplates.containsKey(key)) {
    		Log.error("getCharacterTemplate keys="+characterTemplates.keySet()+" no key "+key);
    	}
    	return characterTemplates.get(key);
    }
    
  
	
    static HashMap<Integer,HashMap<Integer, LevelExpRequirement>> levelXpRequirements = new HashMap<Integer,HashMap<Integer, LevelExpRequirement>>();
    static Map<String, CharacterTemplate> characterTemplates = new HashMap<String, CharacterTemplate>();
    protected static AuthDatabase authDB = new AuthDatabase();
    //public static final int SKILL_MAX = 15;
    public static final int POINTS_PER_SKILL_LEVEL = 10;
    public static final int MAX_SKILL_ABILITIES = 10;
    public static int TOTAL_SKILL_MAX = 1000000;
    public static float SKILL_UP_RATE = 1.0f;
    
    public static boolean USE_SKILL_PURCHASE_POINTS = true;
    public static int SKILL_POINTS_GIVEN_PER_LEVEL = 3;
    public static boolean USE_TALENT_PURCHASE_POINTS = true;
     public static int TALENT_POINTS_GIVEN_PER_LEVEL = 3;
    public static boolean USE_SKILL_MAX = true;
    public static int SKILL_STARTING_MAX = 5;
    public static int EXP_MAX_LEVEL_DIFFERENCE = 10;
    public static float EXP_GROUP_ADD_PERCENTAGE = 0.1f; 
    public static float EXP_MAX_DISTANCE = 40f;
    public static boolean AUTO_ADD_ABILITIES_TO_ACTION_BAR = true;
    public static float MOB_EXP_RATE_NORMAL = 1.0f;
    public static float MOB_EXP_RATE_RARE = 1.5f;
    public static float MOB_EXP_RATE_BOSS = 2.5f;
    

    public static boolean LOST_LEVEL = false;
    //Define combat related string constants
    public static final String MIN_MOB_LEVEL = "mL";
    public static final String KILL_EXP_STAT = "ke";
     public static final String KILL_ADD_EXP_LEV_STAT = "ael";
       public static final String EXPERIENCE_STAT = "experience";
    public static final String EXPERIENCE_MAX_STAT = "experience-max";
    public static final String LEVEL_STAT =  "level";
	
	/**
	 * Game Settings that allow swap actions in action bars
	 * Default true
	 */
	public static boolean ACTION_BAR_SWAP_ACTIONS = true;
}
