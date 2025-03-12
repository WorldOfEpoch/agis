package atavism.agis.plugins;

import java.io.Serializable;
import java.util.*;

import atavism.agis.objects.CombatInfo;
import atavism.agis.objects.SkillTemplate;
import atavism.agis.core.*;
import atavism.msgsys.GenericMessage;
import atavism.msgsys.MessageType;
import atavism.server.engine.Engine;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.messages.PropertyMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;
import atavism.server.util.Logger;

public class ClassAbilityClient {

    public static Namespace NAMESPACE = null;

    private static final Logger log = new Logger("ClassesPlugin");

    // Message types that are sent by the ClassAbility process
    private ClassAbilityClient(){}

    public static void sendXPUpdate(OID oid, String statName, int statCurrentValue){
		// we need to notify the client that a stat has increased.
		log.debug("Sending Client Stat XP Increase Message");

		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "ao.STAT_XP_UPDATE");
		props.put("stat", "Stat XP Increased: " + statName + " : " + statCurrentValue);
		props.put("playerOid", oid);

		TargetedExtensionMessage sendXPUpdate = new TargetedExtensionMessage(ClassAbilityClient.MSG_TYPE_STAT_XP_UPDATE, oid, oid, false, props);

		Engine.getAgent().sendBroadcast(sendXPUpdate);
	}

	public static void CheckSkillAbilities(OID playerOid, String skill, int level){
		if(skill == null)
			log.warn("ClassAbilityClient.CheckSkillAbilities - Skill is null");

		//First find a list of abilities we should have for this skill and skill level
		ArrayList<AgisAbility> skillAbilities = new ArrayList<AgisAbility>();

		Collection<AgisAbility> abilities = Agis.AbilityManager.getMap().values();
		for (AgisAbility ability : abilities) {
			if (ability.getRequiredSkill() == null)
				log.warn("ClassAbilityClient.CheckSkillAbilities - Required Skill for ability "+ability.getName()+" is null");
			else{
				if(ability.getRequiredSkill().getName().equals(skill) && ability.getRequiredSkillLevel() <= level){
					log.debug("ClassAbilityClient.CheckSkillAbilities: Adding ability to skillAbilities : " + ability.getName());
					skillAbilities.add(ability);
				}
			}
		}

		//Get player object
		CombatInfo player = CombatPlugin.getCombatInfo(playerOid);
		//get list of currently known abilities
		Collection<Integer> currentAbilities = player.getCurrentAbilities();
		//Check to see if any of the item in our skillAbilities list is not in our currentAbilities
		for(AgisAbility ability : skillAbilities){
			if(!currentAbilities.contains(ability.getID())){
				log.debug("ClassAbilityClient.CheckSkillAbilities: Adding new ability : " + ability.getName());
				//Ability is not currently in our list so lets add the new ability to the player
				//player.addAbility(ability.getID());
				CombatPlugin.sendAbilityUpdate(player);
			}
		}
		log.debug("ClassAbilityClient.CheckSkillAbilities: Finished");
	}
    
    public static void levelChange(OID oid, int level, int oldLevel) {
    	levelChangeMessage msg = new levelChangeMessage(oid, level, oldLevel);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CLASSABILITY - client levelChange hit 1");
	}

	public static class levelChangeMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public levelChangeMessage() {
            super();
        }
		public levelChangeMessage(OID oid, int level, int oldLevel) {
			super(oid);
			setMsgType(MSG_TYPE_LEVEL_CHANGE);
			setLevel(level);
			setOldLevel(oldLevel);
			Log.debug("CLASSABILITY - client levelChange hit 2");
		}
		
		public int getLevel() {
			return level;
		}
		public void setLevel(int level) {
			this.level = level;
		}
		
		public int getOldLevel() {
			return oldLevel;
		}
		
		public void setOldLevel(int oldLevel) {
			this.oldLevel = oldLevel;
		}
		int level = 1;
		int oldLevel = -1;
	}
	
	  public static void skillLevelChange(OID oid) {
		  skillLevelChangeMessage msg = new skillLevelChangeMessage(oid);
			Engine.getAgent().sendBroadcast(msg);
			Log.debug("CLASSABILITY - client skillLevelChange hit 1");
		}
	
	
	public static class skillLevelChangeMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public skillLevelChangeMessage() {
            super();
        }
		public skillLevelChangeMessage(OID oid) {
			super(oid);
			setMsgType(MSG_TYPE_SKILL_LEVEL_CHANGE);
			//setLevel(level);
			Log.debug("CLASSABILITY - client skillLevelChange hit 2");
		}
		
		public int getLevel() {
			return level;
		}
		public void setLevel(int level) {
			this.level = level;
		}
		int level = 1;
	}
	

	/**
	 * Called from extensions_proxy.py when the player wants to spend points to increase their skill level.
	 * @param oid
	 * @param skillType
	 */
	public static void skillIncreased(OID oid, int skillType) {
		skillIncreasedMessage msg = new skillIncreasedMessage(oid, skillType);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CLASSABILITY CLIENT: skillIncreasedMessage hit 2");
	}

	public static class skillIncreasedMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public skillIncreasedMessage() {
            super();
        }
        
        public skillIncreasedMessage(OID oid, int skillID) {
        	super(oid);
        	setMsgType(MSG_TYPE_COMBAT_SKILL_INCREASE);
        	setSkillID(skillID);
        	Log.debug("CLASSABILITY CLIENT: skillIncreasedMessage hit 1");
        }
        
        public void setSkillID(int skillID) {
        	this.skillID = skillID;
        }
        public int getSkillID() {
        	return skillID;
        }
        int skillID = -1;
	}
	
	public static void skillDecreased(OID oid, int skillType) {
		skillDecreasedMessage msg = new skillDecreasedMessage(oid, skillType);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CLASSABILITY CLIENT: skillDecreasedMessage hit 2");
	}

	public static class skillDecreasedMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public skillDecreasedMessage() {
            super();
        }
        
        public skillDecreasedMessage(OID oid, int skillType) {
        	super(oid);
        	setMsgType(MSG_TYPE_COMBAT_SKILL_DECREASE);
        	setProperty("skillType", skillType);
        	Log.debug("CLASSABILITY CLIENT: skillDecreasedMessage hit 1");
        }
	}
	
	public static void skillReset(OID oid) {
		skillResetMessage msg = new skillResetMessage(oid);
		msg.setProperty("skill", true);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CLASSABILITY CLIENT: skillReset skillResetMessage hit 2");
	}
	public static void talentReset(OID oid) {
		skillResetMessage msg = new skillResetMessage(oid);
		msg.setProperty("skill", false);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CLASSABILITY CLIENT: talentReset skillResetMessage hit 2");
	}

	

	public static class skillResetMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public skillResetMessage() {
            super();
        }
        
        public skillResetMessage(OID oid) {
        	super(oid);
        	setMsgType(MSG_TYPE_COMBAT_SKILL_RESET);
        	Log.debug("CLASSABILITY CLIENT: skillResetMessage hit 1");
        }
	}
	
	/*public static void skillPoints(OID oid,int points) {
		skillResetMessage msg = new skillResetMessage(oid);
		msg.setProperty("skill", true);
		msg.setProperty("points", points);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CLASSABILITY CLIENT: skillPoints skillResetMessage hit 2");
	}
	public static void talentPoints(OID oid,int points) {
		skillResetMessage msg = new skillResetMessage(oid);
		msg.setProperty("skill", false);
		msg.setProperty("points", points);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CLASSABILITY CLIENT: talentPoints skillResetMessage hit 2");
	}

	
	public static class skillPointsMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public skillPointsMessage() {
            super();
        }
        
        public skillPointsMessage(OID oid) {
        	super(oid);
        	setMsgType(MSG_TYPE_COMBAT_SKILL_POINTS);
        	Log.debug("CLASSABILITY CLIENT: skillResetMessage hit 1");
        }
	}*/
	
	public static void skillAlterCurrent(OID oid, int skillType, int alterValue, boolean admin ) {
		skillAlterCurrentMessage msg = new skillAlterCurrentMessage(oid, skillType, alterValue, admin);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CLASSABILITY CLIENT: skillAlterCurrentMessage hit 2");
	}

	public static class skillAlterCurrentMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public skillAlterCurrentMessage() {
            super();
        }
        
        public skillAlterCurrentMessage(OID oid, int skillType, int alterValue, boolean admin) {
        	super(oid);
        	setMsgType(MSG_TYPE_COMBAT_SKILL_ALTER_CURRENT);
        	setSkillType(skillType);
        	setAlterAmount(alterValue);
        	setAdmin(admin);
        	Log.debug("CLASSABILITY CLIENT: skillAlterCurrentMessage hit 1");
        }
        
        public int getSkillType() {
        	return skillType;
        }
        public void setSkillType(int skillType) {
        	this.skillType = skillType;
        }
        int skillType;
        
        public int getAlterAmount() {
        	return alterAmount;
        }
        public void setAlterAmount(int alterAmount) {
        	this.alterAmount = alterAmount;
        }
        int alterAmount;
        
        public boolean getAdmin() {
        	return admin;
        }
        public void setAdmin(boolean admin) {
        	this.admin = admin;
        }
        boolean admin;
	}
	
	public static SkillTemplate getSkillTemplate(int num) {
		skillGetMessage msg = new skillGetMessage(num);
		SkillTemplate tmpl = (SkillTemplate) Engine.getAgent().sendRPCReturnObject(msg);
		Log.debug("CLASSABILITY CLIENT: skillGetMessage hit 2");
		return tmpl;
	}

	public static class skillGetMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public skillGetMessage() {
            super();
        }
        
        public skillGetMessage(int num) {
        	super();
        	setProperty("templateNum", num);
        	setMsgType(MSG_TYPE_COMBAT_GET_SKILL);
        	Log.debug("CLASSABILITY CLIENT: skillGetMessage hit 1");
        }
	}
	
	public static int getPlayerSkillLevel(OID playerOid, int skillType) {
		GetPlayerSkillLevelMessage msg = new GetPlayerSkillLevelMessage(playerOid, skillType);
		int skillLevel = (Integer) Engine.getAgent().sendRPCReturnInt(msg);
		Log.debug("CLASSABILITY CLIENT: GetPlayerSkillLevelMessage hit 2");
		return skillLevel;
	}
	
	public static class GetPlayerSkillLevelMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public GetPlayerSkillLevelMessage() {
            super();
        }
        
        public GetPlayerSkillLevelMessage(OID oid, int skillType) {
        	super(oid);
        	setMsgType(MSG_TYPE_COMBAT_GET_PLAYER_SKILL_LEVEL);
        	setSkillType(skillType);
        	Log.debug("CLASSABILITY CLIENT: GetPlayerSkillLevelMessage hit 1");
        }
        
        public int getSkillType() {
        	return skillType;
        }
        public void setSkillType(int skillType) {
        	this.skillType = skillType;
        }
        int skillType;
	}
	
	/**
	 * Sends the UnLearnAbilityMessage which tells the Combat system to remove
	 * the specified ability from the specified player.
	 * @param oid
	 * @param abilityID
	 */
	public static void unlearnAbility(OID oid, int abilityID) {
		UnlearnAbilityMessage msg = new UnlearnAbilityMessage(oid, abilityID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CLASSABILITY CLIENT: sent LearnAbilityMessage");
	}

	/**
	 * Message used to tell the Combat system that the specified player is to unlearn
	 * the given ability.	 
	 *
	 */
	public static class UnlearnAbilityMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public UnlearnAbilityMessage() {
            super();
        }
        
        public UnlearnAbilityMessage(OID oid, int abilityID) {
        	super(oid);
        	setMsgType(MSG_TYPE_UNLEARN_ABILITY);
        	setAbilityID(abilityID);
        }
        
        public void setAbilityID(int id) {
        	this.abilityID = id;
        }
        public int getAbilityID() {
        	return abilityID;
        }
        
        int abilityID;
	}
	
	
	/**
	 * Sends the LearnAbilityMessage which tells the Combat system to add
	 * the specified ability to the specified player.
	 * @param oid
	 * @param abilityID
	 */
	public static void learnAbility(OID oid, int abilityID) {
		LearnAbilityMessage msg = new LearnAbilityMessage(oid, abilityID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CLASSABILITY CLIENT: sent LearnAbilityMessage");
	}

	/**
	 * Message used to tell the Combat system that the specified player is to learn
	 * the given ability.
	 * @author Andrew Harrison
	 *
	 */
	public static class LearnAbilityMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public LearnAbilityMessage() {
            super();
        }
        
        public LearnAbilityMessage(OID oid, int abilityID) {
        	super(oid);
        	setMsgType(MSG_TYPE_LEARN_ABILITY);
        	setAbilityID(abilityID);
        }
        
        public void setAbilityID(int id) {
        	this.abilityID = id;
        }
        public int getAbilityID() {
        	return abilityID;
        }
        
        int abilityID;
	}
	
	public static void rewardExpForKill(OID oid, HashMap<OID, Integer> attackers) {
		RewardExpForKillMessage msg = new RewardExpForKillMessage(oid, attackers);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CLASSABILITY CLIENT: sent RewardExpForKillMessage "+oid+" "+attackers);
	}

	/**
	 * Message used to tell the Combat system that the specified player is to learn
	 * the given ability.
	 * @author Andrew Harrison
	 *
	 */
	public static class RewardExpForKillMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
        public RewardExpForKillMessage() {
            super();
        }
        
        public RewardExpForKillMessage(OID targetOid, HashMap<OID, Integer> attackers) {
        	setMsgType(MSG_TYPE_HANDLE_EXP);
        	setTargetOid(targetOid);
        	setAttackers(attackers);
        }
        
        public void setTargetOid(OID targetOid) {
        	this.targetOid = targetOid;
        }
        public OID getTargetOid() {
        	return targetOid;
        }
        OID targetOid;
        
        public void setAttackers(HashMap<OID, Integer> attackers) {
        	this.attackers = attackers;
        }
        public HashMap<OID, Integer> getAttackers() {
        	return attackers;
        }
        
        HashMap<OID, Integer> attackers;
	}
	
	
	/**
	 * Sends the LearnAbilityMessage which tells the Combat system to add
	 * the specified ability to the specified player.
	 * @param oid
	 * @param abilityID
	 */
	public static void addSkillPoints(OID oid, int points) {
		PropertyMessage msg = new PropertyMessage(oid);
		msg.setMsgType(MSG_TYPE_ADD_SKILL_POINT);
		msg.setProperty("points", points);
		msg.setProperty("skill", true);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CLASSABILITY CLIENT: sent addSkillPoints");
	}
	public static void addTalentPoints(OID oid, int points) {
		PropertyMessage msg = new PropertyMessage(oid);
		msg.setMsgType(MSG_TYPE_ADD_SKILL_POINT);
		msg.setProperty("points", points);
		msg.setProperty("skill", false);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CLASSABILITY CLIENT: sent addTalentPoints");
	}

	/**
	 * Message used to tell the Combat system that the specified player is to learn
	 * the given ability.
	 * @author Andrew Harrison
	 *
	 */
	/*public static class LearnAbilityMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public LearnAbilityMessage() {
            super();
        }
        
        public LearnAbilityMessage(OID oid, int abilityID) {
        	super(oid);
        	setMsgType(MSG_TYPE_LEARN_ABILITY);
        	setAbilityID(abilityID);
        }
        
        public void setAbilityID(int id) {
        	this.abilityID = id;
        }
        public int getAbilityID() {
        	return abilityID;
        }
        
        int abilityID;
	}
	
	*/
	
	public static final MessageType MSG_TYPE_STAT_XP_UPDATE = MessageType.intern("ao.STAT_XP_UPDATE");
    public static final MessageType MSG_TYPE_HANDLE_EXP = MessageType.intern("ao.HANDLE_EXP");
    public static final MessageType MSG_TYPE_LEVEL_CHANGE = MessageType.intern("ao.LEVEL_CHANGE");
    public static final MessageType MSG_TYPE_SKILL_LEVEL_CHANGE = MessageType.intern("ao.SKILL_LEVEL_CHANGE");
	public static final MessageType MSG_TYPE_COMBAT_SKILL_INCREASE = MessageType.intern("combat.SKILL_INCREASE");
    public static final MessageType MSG_TYPE_COMBAT_SKILL_DECREASE = MessageType.intern("combat.SKILL_DECREASE");
    public static final MessageType MSG_TYPE_COMBAT_SKILL_RESET = MessageType.intern("combat.SKILL_RESET");
    public static final MessageType MSG_TYPE_COMBAT_SKILL_POINTS = MessageType.intern("combat.SKILL_POINTS");
      public static final MessageType MSG_TYPE_COMBAT_SKILL_ALTER_CURRENT = MessageType.intern("combat.SKILL_ALTER_CURRENT");
    public static final MessageType MSG_TYPE_COMBAT_GET_SKILL = MessageType.intern("combat.GET_SKILL");
    public static final MessageType MSG_TYPE_COMBAT_GET_PLAYER_SKILL_LEVEL = MessageType.intern("combat.GET_PLAYER_SKILL_LEVEL");
    public static final MessageType MSG_TYPE_PURCHASE_SKILL_POINT = MessageType.intern("combat.PURCHASE_SKILL_POINT");
    public static final MessageType MSG_TYPE_ADD_SKILL_POINT = MessageType.intern("combat.ADD_SKILL_POINT");
    public static final MessageType MSG_TYPE_LEARN_ABILITY = MessageType.intern("combat.LEARN_ABILITY");
    public static final MessageType MSG_TYPE_UNLEARN_ABILITY = MessageType.intern("combat.UNLEARN_ABILITY");
    public static final MessageType MSG_TYPE_SET_SKILL_STATE = MessageType.intern("combat.SET_SKILL_STATE");
}
