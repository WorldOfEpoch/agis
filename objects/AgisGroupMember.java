package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import atavism.agis.core.AgisEffect.EffectState;
import atavism.agis.database.AccountDatabase;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.GroupPlugin;
import atavism.msgsys.NoRecipientsException;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;
import atavism.server.util.Logger;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.objects.Entity;

public class AgisGroupMember extends Entity {
    // properties
    private static final long serialVersionUID = 1L;
    private OID _groupMemberOid;
    private OID _groupOid;
    private String _groupMemberName;
    private int _groupMemberLevel;
    private Boolean _voiceEnabled = Boolean.FALSE;
    private Boolean _allowedSpeaker = Boolean.TRUE; // everyone can speak by default   
    private int _status = 1;
    private Map<String, Serializable> _entryStats = new ConcurrentHashMap<String, Serializable>();
    private static final Logger _log = new Logger("GroupMember");

    // constructor
    public AgisGroupMember(OID oid, OID groupOid) {
        super("");
		this._groupMemberOid = oid;
		AccountDatabase aDB = new AccountDatabase(false);
		this._groupMemberName = aDB.getCharacterNameByOid(oid);
		//this._groupMemberName = WorldManagerClient.getObjectInfo(oid).name;
		   //  this._groupMemberLevel = (int)WorldManagerClient.getObjectInfo(combatInfo .getOwnerOid()).getProperty("level");
        
        this._groupOid = groupOid;
        SetGroupMemberStats();
    }

    // methods
    public OID GetGroupMemberOid() {
        return this._groupMemberOid;
    }

    public String GetGroupMemberName() {
        return this._groupMemberName;
    }
    
  public int GetGroupMemberLevel() {
    	
    	if (Log.loggingDebug) {
            _log.debug("AgisGroup.GetGroupMemberLevel : " +_groupMemberName+" "+_entryStats.get("level"));
        }
    	int level = -1;
		try {
			LinkedList<String> param = new LinkedList<String>();
			param.add("level");
			HashMap<String,Serializable> objecParams = CombatClient.getCombatInfoParams(_groupMemberOid, param );
			if (Log.loggingDebug) {
			    _log.debug("AgisGroup.GetGroupMemberLevel : " +objecParams);
			}
			level = (Integer)objecParams.get("level");
		} catch (NullPointerException e) {
			Log.warn("GroupMember: player " + _groupMemberOid + "|" + _groupMemberName + " does not have an portrait property");
			if(_entryStats.containsKey("level"))
				level = (int)_entryStats.get("level");
		} catch (NoRecipientsException e) {
			Log.warn("GroupMember: player " + _groupMemberOid + "|" + _groupMemberName + " cant get level property");
			if(_entryStats.containsKey("level"))
				level = (int)_entryStats.get("level");
		}
     	
     //	if(level != -1)
		 _entryStats.put("level",level);
     	if (Log.loggingDebug) {
            _log.debug("AgisGroup.GetGroupMemberLevel END : " +_groupMemberName+" "+_entryStats.get("level"));
        }
    	return level;
    	/*
        CombatInfo combatInfo = CombatPlugin.getCombatInfo(_groupMemberOid);
        return combatInfo.statGetCurrentValue("level");
    	*/
       // return this._groupMemberLevel;
    }

	public String GetGroupMemberPortrait() {

		if (Log.loggingDebug) {
			_log.debug("AgisGroup.GetGroupMemberPortrait : "+_groupMemberName+" " + _entryStats.get("portrait"));
		}
		String portrait = null;
		try {
			portrait = (String) EnginePlugin.getObjectProperty(_groupMemberOid, WorldManagerClient.NAMESPACE, "portrait");
		} catch (NullPointerException e) {
			Log.warn("GroupMember: player " + _groupMemberOid + "|" + _groupMemberName + " does not have an portrait property");
			if(_entryStats.containsKey("portrait"))
				portrait = (String)_entryStats.get("portrait");
		} catch (NoRecipientsException e) {
			Log.warn("GroupMember: player " + _groupMemberOid + "|" + _groupMemberName + " does not have an portrait property");
			if(_entryStats.containsKey("portrait"))
				portrait = (String)_entryStats.get("portrait");
		}
		if(portrait != null)
			_entryStats.put("portrait",portrait);
		return portrait;

		// return this._groupMemberLevel;
	}

	public /* HashMap<Integer, HashSet<EffectState>> */Serializable GetGroupMemberEffects() {

		if (Log.loggingDebug) {
			_log.debug("AgisGroup.GetGroupMemberEffects : " + _entryStats.get("effects"));
		}

		try {
			LinkedList<String> param = new LinkedList<String>();
			param.add("effects");
			HashMap<String, Serializable> objecParams = CombatClient.getCombatInfoParams(_groupMemberOid, param);
			log.debug("GetGroupMemberEffects: params=" + param + " objecParams=" + objecParams);

			if (objecParams.get("effects") instanceof LinkedList<?>) {
				return objecParams.get("effects");
			} else {

				// effects = new HashMap<Integer, HashSet<EffectState>>();
				HashMap<Integer, HashSet<EffectState>> effects = (HashMap<Integer, HashSet<EffectState>>) objecParams.get("effects");

				LinkedList<String> effectsProp = new LinkedList<String>();
				Set<Integer> effectsKeys = effects.keySet();
				for (int k : effectsKeys) {
					Log.debug("SendGroupUpdate k=" + k + " effects.get(k)=" + effects.get(k));
					for (EffectState eState : effects.get(k)) {
						Log.debug("SendGroupUpdate " + eState);
						Log.debug("SendGroupUpdate effect id=" + eState.getEffectID() + " Effect=" + eState.getEffect());
						if (eState.getEffect() != null) {
							String effectData = eState.getEffectID() + "," +
									eState.getCurrentStack() + "," + 
									eState.getEffect().isBuff() + "," + 
									eState.getEndTime() + "," + 
									eState.getTimeUntilEnd() + ","	+ 
									eState.isActive() + "," + 
									eState.getEffect().getDuration() + "," + 
									eState.getEffect().isPassive() + "," + 
									eState.getEffect().isStackTime();
							effectsProp.add(effectData);
						}
					}
				}
				return effectsProp;

			}

		} catch (NullPointerException e) {
			Log.warn("GroupMember: player " + _groupMemberOid + "|" + _groupMemberName + " does not have an portrait property");

		} catch (NoRecipientsException e) {
			Log.warn("GroupMember: player " + _groupMemberOid + "|" + _groupMemberName + " cant get effect property");

		}
		return new LinkedList<String>();
	}

    public OID GetGroupOid(){
        return this._groupOid;
    }

    protected void SetGroupMemberStats(/*CombatInfo combatInfo*/) {
/*        for (String stat : GroupPlugin.GetRegisteredStats()) {
        	Log.debug("GROUP: setting member stat: " + stat);
            _entryStats.put(stat, combatInfo.statGetCurrentValue(stat));
        }
        _entryStats.put("level", combatInfo.statGetCurrentValue("level"));
        */
    	LinkedList<String> param = new LinkedList<String>();
    	  for (String stat : GroupPlugin.GetRegisteredStats()) {
    	        	param.add(stat);
    	  }
    	  param.add("level");
    	HashMap<String,Serializable> objecParams = CombatClient.getCombatInfoParams(_groupMemberOid, param );
    	 for(String s: objecParams.keySet()) {
    		  _entryStats.put(s, objecParams.get(s));
    	 }
    	
    }

	public Serializable GetGroupMemberStat(String stat) {
		if (Log.loggingDebug) {
			_log.debug("AgisGroup.GetGroupMemberStat : " + stat + " = " + _entryStats.get(stat));
		}
				
		try {
			LinkedList<String> param = new LinkedList<String>();
			param.add(stat);
			HashMap<String, Serializable> objecParams = CombatClient.getCombatInfoParams(_groupMemberOid, param);
			_entryStats.put(stat,objecParams.get(stat));
			return objecParams.get(stat);
		} catch (NoRecipientsException e) {
			return _entryStats.get(stat);
		}
	}
    
    public void SetVoiceEnabled(Boolean value){        
        this._voiceEnabled = value;
    }
    
    public Boolean GetVoiceEnabled(){
        return this._voiceEnabled;
    }
    
    public void SetAllowedSpeaker(Boolean value){
        this._allowedSpeaker = value;
    }
    
    public Boolean GetAllowedSpeaker(){
        return this._allowedSpeaker;
    }
    
    public int GetMemberStatus() {
    	return _status;
    }
    
    public void SetMemberStatus(int status) {
    	_status = status;
    }
    
    public static final int MEMBER_STATUS_OFFLINE = 0;
    public static final int MEMBER_STATUS_ONLINE = 1;
    public static final int MEMBER_STATUS_AWAY = 2;
}
