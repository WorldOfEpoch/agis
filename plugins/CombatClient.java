package atavism.agis.plugins;

import java.util.*;
import java.io.Serializable;

import atavism.msgsys.*;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.messages.PropertyMessage;
import atavism.server.network.AOByteBuffer;
import atavism.server.math.Point;
import atavism.server.objects.ObjectType;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.agis.core.*;
import atavism.agis.core.Cooldown.State;
import atavism.agis.objects.EnchantStat;
import atavism.agis.objects.TargetType;
import atavism.agis.objects.TriggerProfile;

//
// client for sending/getting messages to the CombatPlugin
//
public class CombatClient {
    private CombatClient() {
    }
    
    public static HashMap<String,Serializable> getCombatInfoParams(OID oid ,LinkedList<String> params) {
    	Log.debug("getCombatInfoParams: oid="+oid+" params="+params);
    	GetCombatObjectParamsMessage msg = new GetCombatObjectParamsMessage(oid, params);
    	HashMap<String,Serializable> objecParams = (HashMap<String,Serializable>) Engine.getAgent().sendRPCReturnObject(msg);
    	return objecParams;
    }
    
    
	public static class GetCombatObjectParamsMessage extends SubjectMessage {
		public GetCombatObjectParamsMessage() {
			super();
		}

		public GetCombatObjectParamsMessage(OID subjectOid, LinkedList<String> params) {
			super(MSG_TYPE_GET_COMBAT_OBJECT_PARAMS, subjectOid);
			this.params = params;
			
		}

		public LinkedList<String> getParams() {
			return params;
		}

		private LinkedList<String>  params;
		private static final long serialVersionUID = 1L;
	}
    
    
    public static void setCombatInfoParamsNoResponse(OID oid , HashMap<String,Serializable> params ) {
    	SetCombatObjectParamsMessage msg = new SetCombatObjectParamsMessage(oid, params, false);
		Engine.getAgent().sendBroadcast(msg);
    }
	
	public static boolean setCombatInfoParams(OID oid , HashMap<String,Serializable> params ) {
		SetCombatObjectParamsMessage msg = new SetCombatObjectParamsMessage(oid, params, true);
		//Log.dumpStack("setCombatInfoParams: oid="+oid+" params="+params);

		boolean objecParams =  Engine.getAgent().sendRPCReturnBoolean(msg);
		return objecParams;
	}

	public static class SetCombatObjectParamsMessage extends SubjectMessage {
		public SetCombatObjectParamsMessage() {
			super();
		}

		public SetCombatObjectParamsMessage(OID subjectOid,  HashMap<String,Serializable> params, boolean reqResponse) {
			super(MSG_TYPE_SET_COMBAT_OBJECT_PARAMS, subjectOid);
			if (!reqResponse)
				setMsgType(MSG_TYPE_SET_COMBAT_OBJECT_PARAMS_NONBLOCK);
			this.params = params;
			setRequestResponse(reqResponse);
			
		}
		public void setRequestResponse(boolean val) {
			reqResponse = val;
			if (reqResponse)
				setMsgType(MSG_TYPE_SET_COMBAT_OBJECT_PARAMS);
			else
				setMsgType(MSG_TYPE_SET_COMBAT_OBJECT_PARAMS_NONBLOCK);
		}

		/**
		 * Get whether this message requires a response.
		 * @return true if the message requires a response.
		 */
		public boolean getRequestResponse() { return this.reqResponse; }

		/**
		 * Message data member that says if the message requires a response.
		 */
		private boolean reqResponse = false;
		public HashMap<String,Serializable> getParams() {
			return params;
		}

		private  HashMap<String,Serializable>  params;
		private static final long serialVersionUID = 1L;
	}
    
    /**
     * Sends the SetCombatInfoState Message, setting the combat info state for the specified oid.
     * @param oid
     * @param state
     */
 	public static void setCombatInfoState(OID oid, String state) {
 		SetCombatInfoStateMessage msg = new SetCombatInfoStateMessage(oid, state, false);
 		Engine.getAgent().sendBroadcast(msg);
 		if (Log.loggingDebug)
 			Log.debug("CombatClient.setCombatInfoState: oid=" + oid);
 	}
 	
 	/**
     * Sends the SetCombatInfoState Message, clearing the given combat info state for the specified oid.
     * @param oid
     * @param state
     */
 	public static void clearCombatInfoState(OID oid, String state) {
 		SetCombatInfoStateMessage msg = new SetCombatInfoStateMessage(oid, state, true);
 		Engine.getAgent().sendBroadcast(msg);
 		if (Log.loggingDebug)
 			Log.debug("CombatClient.clearCombatInfoState: oid=" + oid);
 	}

    // set an object to autoattack a target
	public static void autoAttack(OID oid, OID targetOid, boolean status) {
		AutoAttackMessage msg = new AutoAttackMessage(oid, targetOid, status);
		Engine.getAgent().sendBroadcast(msg);
		if (Log.loggingDebug)
			Log.debug("CombatClient.autoAttack: oid=" + oid + " targetOid=" + targetOid);
	}

    // use an ability on target with item
    /*public static void startAbility(String abilityName,
                                    Long oid, Long targetOid, Long itemOid) {
        StartAbilityMessage msg = new StartAbilityMessage(oid, abilityName, targetOid, itemOid);
        Engine.getAgent().sendBroadcast(msg);
        if (Log.loggingDebug)
            Log.debug("CombatClient.startAbility: oid=" + oid + " abilityName=" +
                      abilityName + " targetOid=" + targetOid + " itemOid=" + itemOid);
    }*/
	
	// use an ability on target with item
	public static void startAbility(int abilityID, OID oid, OID targetOid, OID itemOid) {
		startAbility(abilityID, oid, targetOid, itemOid, null);
	}
	
	public static void startAbility(int abilityID, OID oid, OID targetOid, OID itemOid, boolean mustKnow) {
		StartAbilityMessage msg = new StartAbilityMessage(oid, abilityID, targetOid, itemOid, null, mustKnow);
    	Engine.getAgent().sendBroadcast(msg);
	}
	
	public static void startAbility(int abilityID, OID oid, OID targetOid, OID itemOid, boolean mustKnow, int claimId, int claimObjId) {
		if (Log.loggingDebug)Log.debug("startAbility "+abilityID);
		StartAbilityMessage msg = new StartAbilityMessage(oid, abilityID, targetOid, itemOid, null, mustKnow);
		msg.setClaimID(claimId);
		msg.setClaimObjID(claimObjId);
    	Engine.getAgent().sendBroadcast(msg);
	}

	public static void startAbility(int abilityID, OID oid, OID targetOid, OID itemOid, boolean mustKnow, int claimId, int claimObjId, Point destPoint) {
		if (Log.loggingDebug)Log.debug("startAbility "+abilityID+" destPoint="+destPoint);
		StartAbilityMessage msg = new StartAbilityMessage(oid, abilityID, targetOid, itemOid, null, mustKnow);
		msg.setClaimID(claimId);
		msg.setClaimObjID(claimObjId);
		msg.setDestLocation(destPoint);
		Engine.getAgent().sendBroadcast(msg);
	}


	public static void startAbility(int abilityID, OID oid, OID targetOid, OID itemOid, boolean mustKnow, int claimId, int claimObjId, boolean returnResult ) {
		StartAbilityMessage msg = new StartAbilityMessage(oid, abilityID, targetOid, itemOid, null, mustKnow);
		msg.setClaimID(claimId);
		msg.setClaimObjID(claimObjId);
		msg.returnResult(returnResult);
    	Engine.getAgent().sendBroadcast(msg);
	}
	
	  public static void startAbility(int abilityID, OID oid, OID targetOid, Serializable item, Point loc) {
	    	StartAbilityMessage msg = new StartAbilityMessage(oid, abilityID, targetOid, item, loc);
	    	Engine.getAgent().sendBroadcast(msg);
	    	if (Log.loggingDebug)
	    		Log.debug("CombatClient.startAbility: oid=" + oid + " abilityID=" + abilityID + " targetOid=" + targetOid + " item=" + item);
	    }
	  
	  public static boolean startAbilityResponse(int abilityID, OID oid, OID targetOid, Serializable item, Point loc) {
	    	StartAbilityMessage msg = new StartAbilityMessage(oid, abilityID, targetOid,true, item, loc);
	    	boolean _response = Engine.getAgent().sendRPCReturnBoolean(msg);
	    	if (Log.loggingDebug)
	    		Log.debug("CombatClient.startAbility: oid=" + oid + " abilityID=" + abilityID + " targetOid=" + targetOid + " item=" + item+" return response="+_response);
	    	return _response;
	    }

    // resurect an object in place
	public static void releaseObject(OID oid, boolean releaseToSpirit) {
		ReleaseObjectMessage msg = new ReleaseObjectMessage(oid, releaseToSpirit);
		Engine.getAgent().sendBroadcast(msg);
		if (Log.loggingDebug)
			Log.debug("CombatClient.releaseObject: oid=" + oid);
	}
	
	public static class SetCombatInfoStateMessage extends SubjectMessage {

		public SetCombatInfoStateMessage() {
			super(MSG_TYPE_SET_COMBAT_INFO_STATE);
		}
		
		public SetCombatInfoStateMessage(OID oid, String state, boolean clearState) {
			super(MSG_TYPE_SET_COMBAT_INFO_STATE, oid);
			setState(state);
			setClearState(clearState);
		}

		public void setState(String state) {
			this.state = state;
		}
		public String getState() {
			return state;
		}
	
		protected String state;
		
		
		public void setClearState(boolean clearState) {
			this.clearState = clearState;
		}
		public boolean getClearState() {
			return clearState;
		}
	
		protected boolean clearState;

		private static final long serialVersionUID = 1L;
	}

	/**
	 * messages that have an oid and a target oid
	 */
	public static class CombatTargetMessage extends SubjectMessage
	{
		public CombatTargetMessage() {
			super();
		}
		
		public CombatTargetMessage(MessageType type) {
			super(type);
		}
		
		public CombatTargetMessage(MessageType type, OID oid, OID targetOid) {
			super(type, oid);
			setTargetOid(targetOid);
		}

		public OID getTargetOid() {
			return this.targetOid;
		}

		public void setTargetOid(OID oid) {
			this.targetOid = oid;
		}

		private OID targetOid = null;
		
		private static final long serialVersionUID = 1L;
	}

	public static class AutoAttackMessage extends CombatTargetMessage {

		public AutoAttackMessage() {
			super(MSG_TYPE_AUTO_ATTACK);
		}
		
		public AutoAttackMessage(OID oid, OID targetOid, Boolean status) {
			super(MSG_TYPE_AUTO_ATTACK, oid, targetOid);
			setAttackStatus(status);
		}

		public void setAttackStatus(Boolean status) {
			this.status = status;
		}
		
		public Boolean getAttackStatus() {
			return status;
		}

		Boolean status;
		
		private static final long serialVersionUID = 1L;
	}

	public static class AbilityUpdateMessage extends TargetMessage {

		public AbilityUpdateMessage() {
			super(MSG_TYPE_ABILITY_UPDATE);
		}


		public AbilityUpdateMessage(OID targetOid, OID subjectOid) {
			super(MSG_TYPE_ABILITY_UPDATE, targetOid, subjectOid);
		}

		public void addAbility(int abilityID, String iconName, String category) {
			Entry entry = new Entry(abilityID, iconName, category);
			entries.add(entry);
		}
	
		public List<Entry> getAbilities() {
			return entries;
		}
	
		List<Entry> entries = new LinkedList<Entry>();

		public AOByteBuffer toBuffer() {
			AOByteBuffer buf = new AOByteBuffer(500);
			buf.putOID(getSubject());
			buf.putInt(56);
			buf.putInt(entries.size());
			for (Entry entry : entries) {
				buf.putInt(entry.abilityID);
				buf.putString(entry.iconName);
				buf.putString(entry.category);
			}
			buf.flip();
			return buf;
		}

		class Entry implements Serializable {
			public Entry(int abilityID, String iconName, String category) {
				this.abilityID = abilityID;
				this.iconName = iconName;
				this.category = category;
			}

			public int abilityID;
			public String iconName;
			public String category;

			private static final long serialVersionUID = 1L;
		}

		private static final long serialVersionUID = 1L;
	}

    /*public static class StartAbilityMessage extends CombatTargetMessage {

        public StartAbilityMessage() {
            super(MSG_TYPE_START_ABILITY);
        }

        public StartAbilityMessage(Long oid, String abilityName, Long targetOid, Long itemOid) {
            super(MSG_TYPE_START_ABILITY, oid, targetOid);
            setAbilityName(abilityName);
            setItemOid(itemOid);
        }

        public void setAbilityName(String abilityName) {
            this.abilityName = abilityName;
        }
        
        public String getAbilityName() {
            return abilityName;
        }
        
        public void setItemOid(Long itemOid) {
            this.itemOid = itemOid;
        }
        
        public Long getItemOid() {
            return itemOid;
        }
        
        private Long itemOid;
        private String abilityName;

        private static final long serialVersionUID = 1L;
    }*/
	
	public static class StartAbilityMessage extends CombatTargetMessage {

		public StartAbilityMessage() {
			super(MSG_TYPE_START_ABILITY);
		}

		public StartAbilityMessage(OID oid, int abilityID, OID targetOid, Serializable item) {
			super(MSG_TYPE_START_ABILITY, oid, targetOid);
			setAbilityID(abilityID);
			setItem(item);
		}

		public StartAbilityMessage(OID oid, int abilityID, OID targetOid, Serializable item, Point loc) {
			super(MSG_TYPE_START_ABILITY, oid, targetOid);
			setAbilityID(abilityID);
			setItem(item);
			setLocation(loc);
		}
		public StartAbilityMessage(OID oid, int abilityID, OID targetOid, boolean response, Serializable item, Point loc) {
			super(MSG_TYPE_START_ABILITY_RESPONSE, oid, targetOid);
			setAbilityID(abilityID);
			setItem(item);
			setLocation(loc);
			response(response);
		}
		
		public StartAbilityMessage(OID oid, int abilityID, OID targetOid, Serializable item, Point loc, boolean mustKnow) {
			super(MSG_TYPE_START_ABILITY, oid, targetOid);
			setAbilityID(abilityID);
			setItem(item);
			setLocation(loc);
			mustKnow(mustKnow);
		}

		public int getAbilityID() { return abilityID; }
		public void setAbilityID(int abilityID) { this.abilityID = abilityID; }
		private int abilityID = -1;

		public Serializable getItem() { return item; }
		public void setItem(Serializable item) { this.item = item; }
		private Serializable item = null;

		public Point getLocation() { return location; }
		public void setLocation(Point loc) { this.location = loc; }
		private Point location = null;

		public Point getDestLocation() { return destLocation; }
		public void setDestLocation(Point loc) { this.destLocation = loc; }
		private Point destLocation = null;

		public boolean mustKnow() { return mustKnow; }
		public void mustKnow(boolean mustKnow) { this.mustKnow = mustKnow; }
		private boolean mustKnow = false;
		
		public boolean response() { return response; }
		public void response(boolean response) { this.response = response; }
		private boolean response = false;

		public int getClaimID() { return claimID; }
		public void setClaimID(int claimID) { this.claimID = claimID; }
		private int claimID = -1;

		public int getClaimObjID() { return claimObjID; }
		public void setClaimObjID(int claimObjID) { this.claimObjID = claimObjID; }
		private int claimObjID = -1;

		public boolean returnResult() { return returnResult; }
		public void returnResult(boolean returnResult) { this.returnResult = returnResult; }
		private boolean returnResult = false;
		
		
		
		private static final long serialVersionUID = 1L;
	}

	public static class DamageMessage extends SubjectMessage {

		public DamageMessage() {
			super(MSG_TYPE_DAMAGE);
		}
		
		public DamageMessage(OID targetOid, OID attackerOid, Integer dmg, String dmgType,Integer threat) {
			super(MSG_TYPE_DAMAGE, targetOid);
			this.attackerOid = attackerOid;
			setDmg(dmg);
			setDmgType(dmgType);
			setThreat(threat);
		}

		public void setDmg(Integer dmg) {
			this.dmg = dmg;
		}
		public Integer getDmg() {
			return dmg;
		}
		
		public void setThreat(Integer threat) {
			this.threat = threat;
		}
		
		public Integer getThreat() {
			return threat;
		}


		public OID getTargetOid() {
			return getSubject();
		}

		public OID getAttackerOid() {
			return attackerOid;
		}

		public void setDmgType(String dmgType) {
			this.dmgType = dmgType;
		}
		public String getDmgType() {
			return dmgType;
		}
		
		 public String toString()
		    {
		        return "["+this.getClass().getName()+" subject="+oid+" msgType="+msgType+ "; msgId=" + msgId + " msgInstanceOid=" + msgInstanceOid + " remoteAgent=" + remoteAgent + " dmg="+dmg+" threat="+threat+" attackerOid="+attackerOid+" dmgType="+dmgType+"]";
		    }

		protected Integer threat;
		
		protected Integer dmg;
		
		protected String dmgType;

		protected OID attackerOid;

		public AOByteBuffer toBuffer() {
			AOByteBuffer buf = new AOByteBuffer(200);
			buf.putOID(getAttackerOid());
			buf.putInt(23);
			buf.putOID(getTargetOid());
			buf.putString(dmgType);
			buf.putInt((Integer)dmg);
			buf.flip();
			return buf;
		}

		private static final long serialVersionUID = 1L;
	}

	public static class CooldownMessage extends SubjectMessage {

		public CooldownMessage() {
			super();
		}
		
		public CooldownMessage(OID oid) {
			super(MSG_TYPE_COOLDOWN, oid);
		}

		public CooldownMessage(Cooldown.State state) {
			super(MSG_TYPE_COOLDOWN, state.getObject().getOid());
			addCooldown(state);
		}

		public void addCooldown(String id, long duration, long endTime,long startTime) {
			Entry entry = new Entry(id, duration, endTime, startTime);
			cooldowns.add(entry);
		}

		public void addCooldown(Cooldown.State state) {
			addCooldown(state.getID(), state.getDuration(), state.getEndTime(),state.getStartTime());
		}

		protected Set<Entry> cooldowns = new HashSet<Entry>();

		public class Entry {
			public Entry() {
			}

			public Entry(String id, long duration, long endTime,long startTime) {
				setCooldownID(id);
				setDuration(duration);
				setEndTime(endTime);
				setStartTime(startTime);
			}

			public String getCooldownID() { return cooldownID; }
			public void setCooldownID(String cd) { cooldownID = cd; }
			protected String cooldownID;

			public long getDuration() { return duration; }
			public void setDuration(long duration) { this.duration = duration; }
			protected long duration;

			public long getEndTime() { return endTime; }
			public void setEndTime(long endTime) { this.endTime = endTime; }
			protected long endTime;
	
			public long getStartTime() { return startTime; }
			public void setStartTime(long startTime) { this.startTime = startTime; }
			protected long startTime;
	}

		private static final long serialVersionUID = 1L;
	}

	public static class AbilityProgressMessage extends SubjectMessage {

		public AbilityProgressMessage() {
			super();
		}
		
		public AbilityProgressMessage(AgisAbilityState state) {
			super(MSG_TYPE_ABILITY_PROGRESS, state.getSource().getOid());
			setAbilityID(state.getAbility().getID());
			setState(state.getState().toString());
			setDuration(state.getDuration());
			setEndTime(calculateEndTime(state));
		}

		protected long calculateEndTime(AgisAbilityState state) {
			AgisAbility ability = state.getAbility();

			switch (state.getState()) {
			case ACTIVATING:
				return state.getNextWakeupTime();
			case CHANNELLING:
				int pulsesRemaining = ability.getChannelPulses() - state.getNextPulse() - 1;
				long endTime = state.getNextWakeupTime() + (pulsesRemaining * ability.getChannelPulseTime());
				return endTime;
			default:
				return 0;
			}
		}

		public int getAbilityID() { return abilityID; }
		public void setAbilityID(int id) { abilityID = id; }
		protected int abilityID;

		public String getState() { return state; }
		public void setState(String state) { this.state = state; }
		protected String state;

		public long getDuration() { return duration; }
		public void setDuration(long duration) { this.duration = duration; }
		protected long duration;

		public long getEndTime() { return endTime; }
		public void setEndTime(long time) { endTime = time; }
		protected long endTime;
		private static final long serialVersionUID = 1L;
	}

	public static class ReleaseObjectMessage extends SubjectMessage {

		public ReleaseObjectMessage() {
			super();
		}
		
		public ReleaseObjectMessage(OID oid) {
			super(MSG_TYPE_RELEASE_OBJECT, oid);
		}
		
		public ReleaseObjectMessage(OID oid, boolean turnToSpirit) {
			super(MSG_TYPE_RELEASE_OBJECT, oid);
			setTurnToSpirit(turnToSpirit);
		}
		
		public boolean turnToSpirit() {
            return turnToSpirit;
        }
        public void setTurnToSpirit(boolean turnToSpirit) {
            this.turnToSpirit = turnToSpirit;
        }
        boolean turnToSpirit = false;
		
		private static final long serialVersionUID = 1L;
	}
    
    public static class QuestMobDeath extends SubjectMessage {
        public QuestMobDeath() {
            super(MSG_TYPE_COMBAT_MOB_DEATH);
        }
        public QuestMobDeath(OID playerOid, int mobID, String mobName, LinkedList<String> questCategories) {
            super(MSG_TYPE_COMBAT_MOB_DEATH, playerOid);
            setMobID(mobID);
            setMobName(mobName);
            setQuestCategories(questCategories);
            Log.debug("QUEST: QuestMobDeath message");
        }

        public String getMobName() {
            return mobName;
        }
        public void setMobName(String mobName) {
            this.mobName = mobName;
        }
        String mobName;
        
        public int getMobID() {
            return mobID;
        }
        public void setMobID(int mobID) {
            this.mobID = mobID;
        }
        int mobID;
        
        public LinkedList<String> getQuestCategories() {
            return questCategories;
        }
        public void setQuestCategories(LinkedList<String> questCategories) {
            this.questCategories = questCategories;
        }
        LinkedList<String> questCategories;

        private static final long serialVersionUID = 1L;
    }
    public static float GetSkillDiff(OID oid, int skillType ,int level) {
    	PropertyMessage msg = new PropertyMessage(MSG_TYPE_COMBAT_SKILL_DIFF,oid);
    	msg.setProperty("skillType", skillType);
    	msg.setProperty("level", level);
		Log.debug("COMBAT CLIENT: abilityUsedMessage hit 2");
		return (float) Engine.getAgent().sendRPCReturnObject(msg);
	}
   
    
    public static void abilityUsed(OID oid, int skillType) {
    	abilityUsedMessage msg = new abilityUsedMessage(oid, skillType);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBAT CLIENT: abilityUsedMessage hit 2");
	}
   
    public static void abilityUsed(OID oid, int skillType, int experience,int level) {
    //	Log.dumpStack("abilityUsed");
    	abilityUsedMessage msg = new abilityUsedMessage(oid, skillType, experience, level);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBAT CLIENT: abilityUsedMessage hit 2");
	}

    
	public static class abilityUsedMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public abilityUsedMessage() {
            super();
        }
        
        public abilityUsedMessage(OID oid, int skillType) {
        	super(oid);
        	setMsgType(MSG_TYPE_COMBAT_ABILITY_USED);
        	setProperty("skillType", skillType);
        	setProperty("experience", 0);
        	setProperty("level", 0);
              	Log.debug("COMBAT CLIENT: abilityUsedMessage hit 1");
        }
        
        public abilityUsedMessage(OID oid, int skillType,int experience,int level) {
        	super(oid);
        	setMsgType(MSG_TYPE_COMBAT_ABILITY_USED);
        	setProperty("skillType", skillType);
        	setProperty("experience", experience);
        	setProperty("level", level);
               	Log.debug("COMBAT CLIENT: abilityUsedMessage hit 1");
        }
	}
    
	
	
	// stop an object autoattacking a target
    public static void stopAutoAttack(OID oid) {
        stopAutoAttackMessage msg = new stopAutoAttackMessage(oid);
        Engine.getAgent().sendBroadcast(msg);
        if (Log.loggingDebug)
            Log.debug("CombatClient.stopAutoAttack: oid=" + oid);
    }
	
	public static class stopAutoAttackMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public stopAutoAttackMessage() {
            super();
        }
        
        public stopAutoAttackMessage(OID oid) {
        	super(oid);
        	setMsgType(MSG_TYPE_COMBAT_STOP_AUTO_ATTACK);
        }
	}
	
    public static void setTargetType(LinkedList<OID> oid, LinkedList<OID> targetOid, LinkedList<Integer> targetType) {
    	TargetTypeMessage msg = new TargetTypeMessage(oid, targetOid, targetType);
        Engine.getAgent().sendBroadcast(msg);
        Log.debug("TARGET: sent target message oid="+oid+" targetOid="+targetOid+" targetType="+targetType);
    }
    public static void setTargetType(LinkedList<TargetType> targetType) {
    	TargetTypeMessage msg = new TargetTypeMessage(targetType);
        Engine.getAgent().sendBroadcast(msg);
        Log.debug("TARGET: sent target message targetType="+targetType);
    }

	public static class TargetTypeMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
		
        public TargetTypeMessage() {
            super();
        }
        public TargetTypeMessage(LinkedList<TargetType> targetTypes) {
            super(MSG_TYPE_TARGET_TYPE);
            setTargetTypes(targetTypes);
        }
        public TargetTypeMessage(LinkedList<OID> oid, LinkedList<OID> targetOid, LinkedList<Integer> targetType) {
        	super(MSG_TYPE_TARGET_TYPE);
        	setSubjectOid(oid);
        	setTargetOid(targetOid);
        	setTargetType(targetType);
        }
        
        public LinkedList<OID> getSubjectOid() {
        	return subjectOid;
        }
        public void setSubjectOid(LinkedList<OID> subjectOid) {
        	this.subjectOid = subjectOid;
        }
        public LinkedList<OID> getTargetOid() {
        	return targetOid;
        }
        public void setTargetOid(LinkedList<OID> targetOid) {
        	this.targetOid = targetOid;
        }
        public LinkedList<Integer> getTargetType() {
        	return targetType;
        }
        public void setTargetType(LinkedList<Integer> targetType) {
        	this.targetType = targetType;
        }
        public LinkedList<TargetType> getTargetTypes() {
        	return targetTypes;
        }
        public void setTargetTypes(LinkedList<TargetType> targetType) {
        	this.targetTypes = targetType;
        }
        protected LinkedList<OID> subjectOid;
        protected LinkedList<OID> targetOid;
        protected LinkedList<Integer> targetType;
        protected LinkedList<TargetType> targetTypes;
	}
	
	public static class interruptAbilityMessage extends SubjectMessage
    {
		public interruptAbilityMessage() {
            super(MSG_TYPE_INTERRUPT_ABILITY);
        }
		
        public interruptAbilityMessage(OID oid) {
            super(MSG_TYPE_INTERRUPT_ABILITY, oid);
           // Log.dumpStack("interruptAbilityMessage oid="+oid);
        }
        
        public void setMove(boolean v) {
        	move=v;
        }
        
        public boolean getMove() {
        	return move;
        }
        
        boolean move=false;
        
        public void setForce(boolean v) {
        	force=v;
        }
        
        public boolean getForce() {
        	return force;
        }
        
        boolean force=false;
        
        public void setChance(float v) {
        	chance = v;
        }
        public float getChance() {
        	return chance;
        }
        float chance = 0f;
        private static final long serialVersionUID = 1L;
    }
	
	public static class CombatLogoutMessage extends SubjectMessage
    {
		public CombatLogoutMessage() {
            super(MSG_TYPE_COMBAT_LOGOUT);
        }
		
        public CombatLogoutMessage(OID oid) {
            super(MSG_TYPE_COMBAT_LOGOUT, oid);
        }
        
        public CombatLogoutMessage(OID oid, OID player) {
            super(MSG_TYPE_COMBAT_LOGOUT, oid);
            setPlayerOid(player);
        }
        
        public OID getPlayerOid() { return playerOid; }
        public void setPlayerOid(OID player) { playerOid = player; }
        OID playerOid;

        private static final long serialVersionUID = 1L;
    }
	
	public static class FactionUpdateMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public FactionUpdateMessage() {
            super();
        }
        
        public FactionUpdateMessage(OID oid) {
        	super(oid);
        	setMsgType(MSG_TYPE_FACTION_UPDATE);
        	Log.debug("COMBAT CLIENT: FactionUpdateMessage hit");
        }
	}
	
	public static List<Long> getAoeTargets(OID subjectOid, Point loc, Integer radius, ObjectType objectType) {
		getAoeTargetsMessage msg = new getAoeTargetsMessage(subjectOid, loc, radius, objectType);
		List<Long> objectsIn = (List<Long>) Engine.getAgent().sendRPCReturnObject(msg);
		Log.debug("COMBAT CLIENT: getAoeTargetsMessage hit 2");
		return objectsIn;
	}

	public static class getAoeTargetsMessage extends SubjectMessage {
		private static final long serialVersionUID = 1L;
        public getAoeTargetsMessage() {
            super();
        }
        
        public getAoeTargetsMessage(OID oid, Point loc, Integer radius, ObjectType objectType) {
        	super(MSG_TYPE_GET_AOE_TARGETS, oid);
        	this.loc = loc;
        	this.radius = radius;
        	this.objectType = objectType;
        	Log.debug("COMBAT CLIENT: getAoeTargetsMessage hit 1");
        }
        
        public Point getLoc() { return loc; }
        public void setLoc(Point loc) { this.loc = loc; }
        private Point loc;
        
        public int getRadius() { return radius; }
        public void setRadius(int radius) { this.radius = radius; }
        private int radius;
        
        public ObjectType getObjectType() { return objectType; }
        public void setObjectType(ObjectType type) { objectType = type; }
        private ObjectType objectType;
	}
	
	public static void updateActionBar(OID oid, int actionPosition, String newAction) {
		updateActionBarMessage msg = new updateActionBarMessage(oid, actionPosition, newAction);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBAT CLIENT: updateActionBarMessage hit 2");
	}

	public static class updateActionBarMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public updateActionBarMessage() {
            super();
        }
        
        public updateActionBarMessage(OID oid, int actionPosition, String newAction) {
        	super(oid);
        	setMsgType(MSG_TYPE_UPDATE_ACTIONBAR);
        	setProperty("actionPosition", actionPosition);
        	setProperty("newAction", newAction);
        	Log.debug("COMBAT CLIENT: updateActionBarMessage hit 1");
        }
	}
	
	public static void applyEffect(OID oid, int effectID) {
		applyEffectMessage msg = new applyEffectMessage(oid, effectID, "");
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBAT CLIENT: applyEffectMessage hit 2");
	}
	
	public static void applyEffect(OID oid, int effectID, String dmgType) {
		applyEffectMessage msg = new applyEffectMessage(oid, effectID, dmgType);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBAT CLIENT: applyEffectMessage hit 2");
	}

	public static class applyEffectMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public applyEffectMessage() {
            super();
        }
        
        public applyEffectMessage(OID oid, int effectID, String dmgType) {
        	super(oid);
        	setMsgType(MSG_TYPE_APPLY_EFFECT);
        	setProperty("effectID", effectID);
        	setProperty("dmgType", dmgType);
        	Log.debug("COMBAT CLIENT: applyEffectMessage hit 1");
        }
	}
	
	public static void removeEffect(OID oid, int effectID, Integer stackAmount) {
		removeEffectMessage msg = new removeEffectMessage(oid, effectID);
		msg.setProperty("removeStackAmount", stackAmount);		
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBAT CLIENT: applyEffectMessage hit 2");
	}
	
	public static void removeEffect(OID oid, int effectID) {
		removeEffectMessage msg = new removeEffectMessage(oid, effectID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBAT CLIENT: applyEffectMessage hit 2");
	}
	
	public static void removeEffect(OID oid, int effectID, boolean allSame) {
		removeEffectMessage msg = new removeEffectMessage(oid, effectID, allSame);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBAT CLIENT: applyEffectMessage hit 2");
	}

	public static class removeEffectMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;

		public removeEffectMessage() {
			super();
		}

		public removeEffectMessage(OID oid, int effectID) {
			super(oid);
			setMsgType(MSG_TYPE_REMOVE_EFFECT);
			setEffectID(effectID);
			Log.debug("COMBAT CLIENT: removeEffectMessage hit 1");
		}

		/**
		 * Remove effect by ID
		 * 
		 * @param oid
		 * @param effectID
		 * @param allSame - Remove all effect with same ID
		 */
		public removeEffectMessage(OID oid, int effectID, boolean allSame) {
			super(oid);
			setMsgType(MSG_TYPE_REMOVE_EFFECT);
			setEffectID(effectID);
			setAllSame(allSame);
			Log.debug("COMBAT CLIENT: removeEffectMessage hit 1");
		}

		public void setEffectID(int id) {
			this.effectID = id;
		}

		public int getEffectID() {
			return effectID;
		}

		int effectID = -1;

		public void setAllSame(boolean v) {
			allSame = v;
		}

		public boolean getAllSame() {
			return allSame;
		}

		boolean allSame = false;
	}

	public static void alterExp(OID oid, int expAmount) {
		alterExpMessage msg = new alterExpMessage(oid, expAmount);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBAT CLIENT: alterExpMessage hit 2");
	}
	
	public static class alterExpMessage extends SubjectMessage {
        public alterExpMessage() {
            super(MSG_TYPE_ALTER_EXP);
        }
        public alterExpMessage(OID playerOid, int xpAmount) {
            super(MSG_TYPE_ALTER_EXP, playerOid);
            setXpAmount(xpAmount);
            Log.debug("COMBATCLIENT: alterExpMessage message");
        }

        public int getXpAmount() {
            return xpAmount;
        }
        public void setXpAmount(int xpAmount) {
            this.xpAmount = xpAmount;
        }
        int xpAmount;

        private static final long serialVersionUID = 1L;
    }
    
    public static void updateBreathStatus(OID oid, boolean underwater) {
		updateBreathStatusMessage msg = new updateBreathStatusMessage(oid, underwater);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBAT CLIENT: updateBreathStatusMessage hit 2");
	}
	
	public static class updateBreathStatusMessage extends SubjectMessage {
        public updateBreathStatusMessage() {
            super(MSG_TYPE_UPDATE_BREATH);
        }
        public updateBreathStatusMessage(OID playerOid, boolean underwater) {
            super(MSG_TYPE_UPDATE_BREATH, playerOid);
            setUnderwater(underwater);
            Log.debug("COMBATCLIENT: updateBreathStatusMessage message");
        }

        public boolean getUnderwater() {
            return underwater;
        }
        public void setUnderwater(boolean underwater) {
            this.underwater = underwater;
        }
        boolean underwater;

        private static final long serialVersionUID = 1L;
    }
	
	public static void updateFatigueStatus(OID oid, boolean fatigue) {
		updateFatigueStatusMessage msg = new updateFatigueStatusMessage(oid, fatigue);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBATCLIENT: updateFatigueStatusMessage hit 2");
	}
	
	public static class updateFatigueStatusMessage extends SubjectMessage {
        public updateFatigueStatusMessage() {
            super(MSG_TYPE_UPDATE_FATIGUE);
        }
        public updateFatigueStatusMessage(OID playerOid, boolean fatigue) {
            super(MSG_TYPE_UPDATE_FATIGUE, playerOid);
            setFatigue(fatigue);
            Log.debug("COMBATCLIENT: updateFatigueStatusMessage 1");
        }

        public boolean getFatigue() {
            return fatigue;
        }
        public void setFatigue(boolean fatigue) {
            this.fatigue = fatigue;
        }
        boolean fatigue;

        private static final long serialVersionUID = 1L;
    }
	
	public static HashMap<String, String> getAbilityInfo(String abilityName) {
	    ExtensionMessage getAbilityMsg = new ExtensionMessage();
	    getAbilityMsg.setExtensionType("ao.GET_ABILITY");
	    getAbilityMsg.setMsgType(CombatClient.MSG_TYPE_GET_ABILITY);
	    getAbilityMsg.setProperty("abilityName", abilityName);
	    return (HashMap<String, String>)Engine.getAgent().sendRPCReturnObject(getAbilityMsg);
	}
	
	public static void addAbility(String abilityName, OID playerOid) {
	    ExtensionMessage addAbilityMsg = new ExtensionMessage();
	    addAbilityMsg.setExtensionType("ao.ADD_ABILITY");
	    addAbilityMsg.setProperty("abilityName", abilityName);
	    addAbilityMsg.setSubject(playerOid);
	    Engine.getAgent().sendBroadcast(addAbilityMsg);
	}
	
	public static int getPlayerStatValue(OID playerOid, String statName) {
		GetPlayerStatValueMessage msg = new GetPlayerStatValueMessage(playerOid, statName);
		int statValue = (Integer) Engine.getAgent().sendRPCReturnInt(msg);
		if(Log.loggingDebug)Log.debug("CLASSABILITY CLIENT: GetPlayerStatValueMessage "+playerOid+" "+statName+" "+statValue);
		return statValue;
	}
	
	public static class GetPlayerStatValueMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public GetPlayerStatValueMessage() {
            super();
        }
        
        public GetPlayerStatValueMessage(OID oid, String statName) {
        	super(oid);
        	setMsgType(MSG_TYPE_GET_PLAYER_STAT_VALUE);
        	setStatName(statName);
        	Log.debug("CLASSABILITY CLIENT: GetPlayerSkillLevelMessage hit 1");
        }
        
        public String getStatName() {
        	return statName;
        }
        public void setStatName(String statName) {
        	this.statName = statName;
        }
        String statName;
        public String toString() {
        	return "[GetPlayerStatValueMessage super=" + super.toString()+" statName="+statName+" ]";
            }
	}
	
	public static void sendAutoAttackCompleted(OID attackerOid) {
		AutoAttackCompletedMessage msg = new AutoAttackCompletedMessage(attackerOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBATCLIENT: sendAutoAttackCompleted hit 2");
	}
	
	public static class AutoAttackCompletedMessage extends SubjectMessage {
        public AutoAttackCompletedMessage() {
            super(MSG_TYPE_AUTO_ATTACK_COMPLETED);
        }
        public AutoAttackCompletedMessage(OID playerOid) {
            super(MSG_TYPE_AUTO_ATTACK_COMPLETED, playerOid);
            Log.debug("COMBATCLIENT: sendAutoAttackCompleted message");
        }

        private static final long serialVersionUID = 1L;
    }
	
	public static void sendAlterThreat(OID subjectOid, OID attackerOid, int threatChange) {
		AlterThreatMessage msg = new AlterThreatMessage(subjectOid, attackerOid, threatChange);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBATCLIENT: sendAlterThreat hit 2");
	}
	
	public static class AlterThreatMessage extends SubjectMessage {
        public AlterThreatMessage() {
            super(MSG_TYPE_ALTER_THREAT);
        }
        public AlterThreatMessage(OID subjectOid, OID attackerOid, int threatChange) {
            super(MSG_TYPE_ALTER_THREAT, subjectOid);
            setAttackerOid(attackerOid);
            setThreatChange(threatChange);
            Log.debug("COMBATCLIENT: sendAlterThreat message");
        }
        
        public int getThreatChange() {
        	return threatChange;
        }
        public void setThreatChange(int threatChange) {
        	this.threatChange = threatChange;
        }
        int threatChange = 0;
        
        public OID getAttackerOid() {
        	return attackerOid;
        }
        public void setAttackerOid(OID attackerOid) {
        	this.attackerOid = attackerOid;
        }
        OID attackerOid = null;

        private static final long serialVersionUID = 1L;
    }
	
	public static class SetPlayerRespawnLocationMessage extends SubjectMessage {
        public SetPlayerRespawnLocationMessage() {
            super(MSG_TYPE_SET_PLAYER_RESPAWN_LOCATION);
        }
        
        public SetPlayerRespawnLocationMessage(OID subjectOid, Point respawnLoc) {
            super(MSG_TYPE_SET_PLAYER_RESPAWN_LOCATION, subjectOid);
            setRespawnLoc(respawnLoc);
            Log.debug("COMBATCLIENT: sendAlterThreat message");
        }
        
        public SetPlayerRespawnLocationMessage(OID subjectOid, int instanceID, Point respawnLoc) {
            super(MSG_TYPE_SET_PLAYER_RESPAWN_LOCATION, subjectOid);
            setRespawnLoc(respawnLoc);
            setInstanceID(instanceID);
            Log.debug("COMBATCLIENT: sendAlterThreat message");
        }
        
        public int getInstanceID() {
        	return instanceID;
        }
        public void setInstanceID(int instanceID) {
        	this.instanceID = instanceID;
        }
        int instanceID = -1;
        
        public Point getRespawnLoc() {
        	return respawnLoc;
        }
        public void setRespawnLoc(Point respawnLoc) {
        	this.respawnLoc = respawnLoc;
        }
        Point respawnLoc = null;

        private static final long serialVersionUID = 1L;
    }

	public static void arenaRelease(OID oid, Point respawnLoc, boolean allowMovement, boolean turnToSpirit) {
		ExtensionMessage msg = new ExtensionMessage(oid);
		msg.setMsgType(MSG_TYPE_ARENA_RELEASE);
		msg.setProperty("respawnLoc", respawnLoc);
		msg.setProperty("allowMovement", allowMovement);
		msg.setProperty("turnToSpirit", turnToSpirit);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("COMBAT: arena Release hit 1");
	}

	public static void modifyStats(OID playerOid, ArrayList<EnchantStat> stats, String obj) {
		Log.debug("modifyStats: playerOid=" + playerOid + " stats=" + stats + " obj=" + obj);
		ModifyStatsMessage msm = new ModifyStatsMessage(playerOid);
		msm.setStats(stats);
		msm.setObj(obj);
		Engine.getAgent().sendBroadcast(msm);
	}

	public static class ModifyStatsMessage extends SubjectMessage {
		public ModifyStatsMessage() {
			super(MSG_TYPE_COMBAT_STATS_MODIFY);
		}

		public ModifyStatsMessage(OID oid) {
			super(MSG_TYPE_COMBAT_STATS_MODIFY, oid);
		}

		private ArrayList<EnchantStat> stats;

		public ArrayList<EnchantStat> getStats() {
			return stats;
		}

		public void setStats(ArrayList<EnchantStat> stats) {
			this.stats = stats;
		}

		public String getObj() {
			return obj;
		}

		public void setObj(String obj) {
			this.obj = obj;
		}

		String obj = "";
		private static final long serialVersionUID = 1L;
	}

	public static void delModifyStats(OID playerOid, String obj) {
		DelModifyStatsMessage msm = new DelModifyStatsMessage(playerOid);
		msm.setProperty("obj", obj);
		Engine.getAgent().sendBroadcast(msm);
	}

	public static class DelModifyStatsMessage extends PropertyMessage {
		public DelModifyStatsMessage() {
			super(MSG_TYPE_COMBAT_DEL_STATS_MODIFY);
		}

		public DelModifyStatsMessage(OID oid) {
			super(MSG_TYPE_COMBAT_DEL_STATS_MODIFY, oid);
		}

		private OID targetOid = null;

		private static final long serialVersionUID = 1L;
	}

	/**
	 * Send Message to define names of the stats that updates values should be send to the WMGR 
	 * 
	 */
	
	public static void SendSetWmgrStats(LinkedList<String> stats) {
		SetWmgrStatsMessage msm = new SetWmgrStatsMessage(stats);
		Engine.getAgent().sendBroadcast(msm);
	}

	
	/**
	 *  Definition of the Message to define names of the stats that updates values should be send to the WMGR 
	 * 
	 */
	public static class SetWmgrStatsMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;

		public SetWmgrStatsMessage() {
			super();
		}

		public SetWmgrStatsMessage(LinkedList<String> stats) {
			super(MSG_TYPE_SET_WMGR_STATS);
			Log.debug("SetWmgrStatsMessage "+stats);
			setStats(stats);
		}

		public LinkedList<String> getStats() {
			return stats;
		}

		public void setStats(LinkedList<String> stats) {
			this.stats = stats;
		}

		protected LinkedList<String> stats;
	}
	
	/**
	 * Send Message to reset attacker list in combat 
	 * @param mobOid
	 */
	
	public static void sendResetAttacker(OID mobOid) {
		Log.debug("sendResetAttacker: mobOid=" + mobOid );
		ResetAttacterMessage msm = new ResetAttacterMessage(mobOid);
		Engine.getAgent().sendBroadcast(msm);
	}
	
	public static class ResetAttacterMessage extends SubjectMessage {
		public ResetAttacterMessage() {
			super(MSG_TYPE_COMBAT_RESET_ATTACKER);
		}

		public ResetAttacterMessage(OID oid) {
			super(MSG_TYPE_COMBAT_RESET_ATTACKER, oid);
		}
		private static final long serialVersionUID = 1L;
	}

	public static void sendStartCombatState(OID oid) {
		if (Log.loggingDebug)
			Log.debug("send start combat state");
		PropertyMessage msg = new PropertyMessage(MSG_TYPE_COMBAT_STATE, oid);
		Engine.getAgent().sendBroadcast(msg);
		if (Log.loggingDebug)
			Log.debug("send start combat state send");
	}

	public static void sendCombatEvent(OID oid, boolean reviced, TriggerProfile.Type event) {
		if (Log.loggingDebug)
			Log.debug("sendStartCombatEvent");
		PropertyMessage msg = new PropertyMessage(MSG_TYPE_COMBAT_EVENT, oid);
		msg.setProperty("combatEvent", event);
		msg.setProperty("reviced", reviced);
		Engine.getAgent().sendBroadcast(msg);
		if (Log.loggingDebug)
			Log.debug("sendStartCombatEvent send");
	}
	
	@SuppressWarnings("unchecked")
	public static Map<String , Cooldown.State> getPlayerCooldowns(OID oid) {
		if (Log.loggingDebug)
			Log.debug("getPlayerCooldowns start");
		PropertyMessage msg = new PropertyMessage(MSG_TYPE_GET_PLAYER_COOLDOWNS, oid);
		Map<String, Cooldown.State> response = (Map<String, Cooldown.State>)Engine.getAgent().sendRPCReturnObject(msg);
		if (Log.loggingDebug)
			Log.debug("getPlayerCooldowns End "+response);
		return response;
	}
	
    /**
     * sub object creation namespace for the animation plugin
     */
    public static Namespace NAMESPACE = null;
    public static Namespace TEST_NAMESPACE = null;
    public static final String TEMPL_COMBAT_NAME = ":combatName";
    public static Namespace INSTANCE_NAMESPACE = null;
    
    public static final MessageType MSG_TYPE_COMBAT_RESET_ATTACKER = MessageType.intern("combat.RESET_ATTACKER");
    public static final MessageType MSG_TYPE_COMBAT_STATE = MessageType.intern("combat.START_STATE");
    public static final MessageType MSG_TYPE_COMBAT_EVENT = MessageType.intern("combat.COMBAT_EVENT");
      
    public static final MessageType MSG_TYPE_GET_COMBAT_OBJECT_PARAMS = MessageType.intern("ao.GET_COMBAT_OBJECT_PARAMS");
	public static final MessageType MSG_TYPE_SET_COMBAT_OBJECT_PARAMS = MessageType.intern("ao.SET_COMBAT_OBJECT_PARAMS");
	public static final MessageType MSG_TYPE_SET_COMBAT_OBJECT_PARAMS_NONBLOCK = MessageType.intern("ao.SET_COMBAT_OBJECT_PARAMS_NONBLOCK");
    public static final MessageType MSG_TYPE_SET_COMBAT_INFO_STATE = MessageType.intern("ao.SET_COMBAT_INFO_STATE");
    public static final MessageType MSG_TYPE_AUTO_ATTACK = MessageType.intern("ao.AUTO_ATTACK");
    public static final MessageType MSG_TYPE_START_ABILITY = MessageType.intern("ao.START_ABILITY");
    public static final MessageType MSG_TYPE_START_ABILITY_RESPONSE = MessageType.intern("ao.START_ABILITY_RESPONSE");
    public static final MessageType MSG_TYPE_COOLDOWN = MessageType.intern("ao.COOLDOWN");
    public static final MessageType MSG_TYPE_ABILITY_PROGRESS = MessageType.intern("ao.ABILITY_PROGRESS");
    public static final MessageType MSG_TYPE_DAMAGE = MessageType.intern("ao.DAMAGE");
    public static final MessageType MSG_TYPE_RELEASE_OBJECT = MessageType.intern("ao.RELEASE_OBJECT");
    public static final MessageType MSG_TYPE_ABILITY_STATUS = MessageType.intern("ao.ABILITY_STATUS");
    public static final MessageType MSG_TYPE_ABILITY_UPDATE = MessageType.intern("ao.ABILITY_UPDATE");
    public static final MessageType MSG_TYPE_GET_ABILITY = MessageType.intern("ao.GET_ABILITY");
    public static final MessageType MSG_TYPE_SKILL_UPDATE = MessageType.intern("ao.SKILL_UPDATE");
    public static final MessageType MSG_TYPE_ADD_SKILL = MessageType.intern("ao.ADD_SKILL");
    public static final MessageType MSG_TYPE_TRAINING_FAILED = MessageType.intern("ao.TRAINING_FAILED");
    public static final MessageType MSG_TYPE_COMBAT_MOB_DEATH = MessageType.intern("ao.COMBAT_MOB_DEATH");
    public static final MessageType MSG_TYPE_COMBAT_ABILITY_USED = MessageType.intern("combat.ABILITY_USED");
    public static final MessageType MSG_TYPE_COMBAT_STOP_AUTO_ATTACK = MessageType.intern("combat.STOP_AUTO_ATTACK");
    public static final MessageType MSG_TYPE_TARGET_TYPE = MessageType.intern("combat.TARGET_TYPE");
    public static final MessageType MSG_TYPE_INTERRUPT_ABILITY = MessageType.intern("combat.INTERRUPT_ABILITY");
    public static final MessageType MSG_TYPE_COMBAT_LOGOUT = MessageType.intern("combat.LOGOUT");
    public static final MessageType MSG_TYPE_FACTION_UPDATE = MessageType.intern("faction.UPDATE");
    public static final MessageType MSG_TYPE_GET_AOE_TARGETS = MessageType.intern("combat.GET_AOE_TARGETS");
    public static final MessageType MSG_TYPE_UPDATE_ACTIONBAR = MessageType.intern("combat.UPDATE_ACTIONBAR");
    public static final MessageType MSG_TYPE_APPLY_EFFECT = MessageType.intern("combat.APPLY_EFFECT");
    public static final MessageType MSG_TYPE_REMOVE_EFFECT = MessageType.intern("combat.REMOVE_EFFECT");
    public static final MessageType MSG_TYPE_ALTER_EXP = MessageType.intern("combat.ALTER_EXP");
    public static final MessageType MSG_TYPE_UPDATE_BREATH = MessageType.intern("combat.UPDATE_BREATH");
    public static final MessageType MSG_TYPE_UPDATE_FATIGUE = MessageType.intern("combat.UPDATE_FATIGUE");
    public static final MessageType MSG_TYPE_ALTER_HEARTS = MessageType.intern("ao.ALTER_HEARTS");
    public static final MessageType MSG_TYPE_KNOCKED_OUT = MessageType.intern("ao.KNOCKED_OUT");
    public static final MessageType MSG_TYPE_UPDATE_HEALTH_PROPS = MessageType.intern("ao.UPDATE_HEALTH_PROPS");
    public static final MessageType MSG_TYPE_REGEN_HEALTH_MANA = MessageType.intern("ao.REGEN_HEALTH_MANA");
    public static final MessageType MSG_TYPE_DECREMENT_WEAPON_USES = MessageType.intern("ao.DECREMENT_WEAPON_USES");
    public static final MessageType MSG_TYPE_DISMOUNT = MessageType.intern("ao.DISMOUNT");
    public static final MessageType MSG_TYPE_GET_PLAYER_STAT_VALUE = MessageType.intern("ao.GET_PLAYER_STAT_VALUE");
    public static final MessageType MSG_TYPE_REMOVE_BUFF = MessageType.intern("ao.REMOVE_BUFF");
    public static final MessageType MSG_TYPE_AUTO_ATTACK_COMPLETED = MessageType.intern("ao.AUTO_ATTACK_COMPLETED");
    public static final MessageType MSG_TYPE_ALTER_THREAT = MessageType.intern("ao.ALTER_THREAT");
    public static final MessageType MSG_TYPE_SET_PLAYER_RESPAWN_LOCATION = MessageType.intern("ao.SET_PLAYER_RESPAWN_LOCATION");
    public static final MessageType MSG_TYPE_FALLING_EVENT = MessageType.intern("ao.FALLING_EVENT");
    public static final MessageType MSG_TYPE_ARENA_RELEASE = MessageType.intern("ao.ARENA_RELEASE");
    public static final MessageType MSG_TYPE_COMBAT_SKILL_DIFF = MessageType.intern("combat.SKILL_DIFF");
    public static final MessageType MSG_TYPE_COMBAT_STATS_MODIFY = MessageType.intern("combat.STATS_MODIFY");
    public static final MessageType MSG_TYPE_COMBAT_DEL_STATS_MODIFY = MessageType.intern("combat.DEL_STATS_MODIFY");
    public static final MessageType MSG_TYPE_SET_WMGR_STATS = MessageType.intern("combat.SET_WMGR_STATS");
	public static final MessageType MSG_TYPE_SPRINT = MessageType.intern("combat.SPRINT");
	public static final MessageType MSG_TYPE_DODGE = MessageType.intern("combat.DODGE");
    public static final MessageType MSG_TYPE_GET_PLAYER_COOLDOWNS = MessageType.intern("combat.GET_COOLDOWNS");
	public static final MessageType MSG_TYPE_ABILITY_VECTOR = MessageType.intern("combat.ABILITY_VECTOR");
	public static final MessageType MSG_TYPE_DEBUG_ABILITY = MessageType.intern("combat.DEBUG_ABILITY");
         
    // Should not be here - Move this later
    public static final MessageType MSG_CLIENT_LEVEL_LOADED = MessageType.intern("ao.CLIENT_LEVEL_LOADED");
}
