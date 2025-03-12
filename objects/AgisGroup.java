package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.GroupClient;
import atavism.agis.plugins.GroupPlugin;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.objects.Entity;
import atavism.server.plugins.VoiceClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;

public class AgisGroup extends Entity {
    // properties
    private Hashtable<OID, AgisGroupMember> _groupMembers;
    private static final long serialVersionUID = 1L;
    private OID _groupLeaderOid;
    private Boolean _groupMuted = false;
    HashMap<OID, ScheduledFuture> tasks = new HashMap<OID, ScheduledFuture>();
    private int roll = GroupPlugin.GROUP_LOOT_DEFAULT_ROLL;
    private int dice = GroupPlugin.GROUP_LOOT_DEFAULT_DICE;
    private int grade = GroupPlugin.GROUP_LOOT_DEFAULT_GRADE;
    private boolean noLeader = false;
    // constructors
    public AgisGroup() {
        super("");
        _groupMembers = new Hashtable<OID, AgisGroupMember>();
        if(Log.loggingDebug)
            log.debug("AgisGroup - creating new group " + this.getOid().toString());
        //SetupVoiceGroup();        
    }

    public boolean GetNoLeader() {
        return noLeader;
    }

    public void SetNoleader(boolean value) {
        noLeader = value;
    }

    // events

    public int GetRoll() {
    	return roll;
    }
    
    public void SetRoll(int value) {
    	roll = value;
    }
    
    public int GetDice() {
    	return dice;
    }
    
    public void SetDice(int value) {
    	dice = value;
    }
    
    public int GetGrade() {
    	return grade;
    }
    
    public void SetGrade(int value) {
    	grade = value;
    }
    
    
    // methods
    public OID GetGroupOid() {
        return this.getOid();
    }

    public AgisGroupMember AddGroupMember(OID oid) {
        AgisGroupMember newMember = new AgisGroupMember(oid, this.getOid());
        _groupMembers.put(newMember.getOid(), newMember);
      
        HashMap<String,Serializable> objecParams = new HashMap<String,Serializable> () ;
        objecParams.put("groupMemberOid", newMember.getOid());
        objecParams.put("groupOid", this.GetGroupOid());
        CombatClient.setCombatInfoParams(oid,objecParams);

        if(!noLeader)
            GroupClient.SendGroupEventMessage(GroupClient.GroupEventType.JOINED, this, oid);
    	EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "groupOid", this.GetGroupOid());
        
        return newMember;
    }

    public void RemoveGroupMember(OID oid) {
    	//LinkedList<String> param = new LinkedList<String>();
    	//param.add("groupMemberOid");
    	HashMap<String,Serializable> objecParams = new HashMap<String,Serializable>(); 
    			//CombatClient.getCombatInfoParams(oid, param );
        if(!noLeader)
            GroupClient.SendGroupEventMessage(GroupClient.GroupEventType.LEFT, this, oid);
      //  if(objecParams.get("groupMemberOid")!=null) {
        Log.debug("RemoveGroupMember: "+oid+" "+_groupMembers);
		AgisGroupMember agm = GetGroupMember(oid);
		if (agm != null) {
			if (_groupMembers.containsKey(agm.getOid()))
				_groupMembers.remove(agm.getOid());
		}
       // }
       // objecParams.clear();
        objecParams.put("groupMemberOid", null);
        objecParams.put("groupOid", null);
       try {
			CombatClient.setCombatInfoParamsNoResponse(oid,objecParams);
			EnginePlugin.setObjectPropertyNoResponse(oid, WorldManagerClient.NAMESPACE, "groupOid", null);
		} catch (atavism.msgsys.NoRecipientsException e) {
			Log.exception("RemoveGroupMember",e);
		}
        
      
    }
    
    public void RemoveOfflineGroupMember(OID targetOid) {
        if(!noLeader)
            GroupClient.SendGroupEventMessage(GroupClient.GroupEventType.LEFT, this, targetOid);
        for (AgisGroupMember member : _groupMembers.values()) {
        	if (member.GetGroupMemberOid().equals(targetOid)) {
        		_groupMembers.remove(member.getOid());
        		return;
        	}
        }
        
       
    }

    public Hashtable<OID, AgisGroupMember> GetGroupMembers() {
        return _groupMembers;
    }

    public int GetNumGroupMembers() {
        return _groupMembers.size();
    }

    public OID GetGroupLeaderOid() {
        return _groupLeaderOid;
    }
    
    public AgisGroupMember GetGroupMember(OID groupMemberOid) {
        for (AgisGroupMember groupMember : _groupMembers.values()) {
            if (groupMember.GetGroupMemberOid().equals(groupMemberOid))
                return groupMember;
        }
        return null;
    }
    
    public void SetGroupLeaderOid(OID value) {
        _groupLeaderOid = value;
        if (value != null)
            GroupClient.SendGroupEventMessage(GroupClient.GroupEventType.LEADERCHANGED, this, value);
        //Default to remaining person until group is cleaned up and removed
        if (_groupMembers.size() == 1){
            List<AgisGroupMember> groupMembers = new ArrayList<AgisGroupMember>(_groupMembers.values());
            _groupLeaderOid = groupMembers.get(0).GetGroupMemberOid();
        }
    }
    
    protected void SetupVoiceGroup(){
        int error = 0;

        // Create a new voice chat group specific to this group that is non-positional
        error = VoiceClient.addVoiceGroup(this.GetGroupOid(), false, 4);
        if(error != VoiceClient.SUCCESS){
            Log.error("AgisGroup.SetupGroupVoice : Create Voice Group Response - " + VoiceClient.errorString(error));            
        }
    }
    
    public void RemoveVoiceGroup(){
        int error = 0;
        // Remove voice group voice server
        error = VoiceClient.removeVoiceGroup(this.GetGroupOid());
        if(error != VoiceClient.SUCCESS){
            Log.error("AgisGroup.RemoveVoiceGroup : Remove Voice Group Response - " + VoiceClient.errorString(error));            
        }  
    }
    
    public void SetGroupMuted(Boolean value){
        this._groupMuted = value;
    }
    
    public Boolean GetGroupMuted(){
        return this._groupMuted;
    }
    
    public void SetMemberOffline(OID groupMemberOid) {
    	Log.debug("GROUP: member went offline OID: " + groupMemberOid);
        if(!noLeader)
        if (groupMemberOid.equals(_groupLeaderOid)) {
    		// Leader disconnected so assign next member as leader
    		List<AgisGroupMember> groupMembers = new ArrayList<AgisGroupMember>(_groupMembers.values());
    		for (AgisGroupMember groupMember : groupMembers) {
    			if (!groupMember.GetGroupMemberOid().equals(groupMemberOid)) {
    				_groupLeaderOid = groupMember.GetGroupMemberOid();
                    GroupClient.SendGroupEventMessage(GroupClient.GroupEventType.LEADERCHANGED, this, _groupLeaderOid);
    				Log.debug("GROUP: leader disconnected so setting leader to: " + _groupLeaderOid);
    				break;
    			}
    		}
    	}
    	AgisGroupMember member =  GetGroupMember(groupMemberOid);
    	member.SetMemberStatus(AgisGroupMember.MEMBER_STATUS_OFFLINE);
    	MemberDisconnectTimer timer = new MemberDisconnectTimer(groupMemberOid, this);
    	ScheduledFuture sf = Engine.getExecutor().schedule(timer, (long) GroupPlugin.GROUP_DISCONNECT_TIMEOUT * 1000, TimeUnit.MILLISECONDS);
    	tasks.put(groupMemberOid, sf);
    	Log.debug("GROUP: created member offline task for: " + groupMemberOid);
    }
    
    public void SetMemberOnline(OID groupMemberOid) {
    	Log.debug("GROUP: setting member online: " + groupMemberOid);
        AgisGroupMember member =  GetGroupMember(groupMemberOid);
    	member.SetMemberStatus(AgisGroupMember.MEMBER_STATUS_ONLINE);
    	// Remove disconnect task
    	if (tasks.containsKey(groupMemberOid)) {
    		Log.debug("GROUP: cancelling disconnect task");
    		tasks.get(groupMemberOid).cancel(true);
    		tasks.remove(groupMemberOid);
    	}
    }
    
    public class MemberDisconnectTimer implements Runnable {
    	
    	protected OID groupMemberOid;
    	protected AgisGroup group;
    	
    	public MemberDisconnectTimer(OID groupMemberOid, AgisGroup group) {
    		Log.debug("GROUP: MemberDisconnectTimer for " + groupMemberOid);
    		this.groupMemberOid = groupMemberOid;
    		this.group = group;
    		
    	}
    	
		@Override
		public void run() {
			// Check user still has items
			Log.debug("GROUP: running disconnect task for " + groupMemberOid);
			group.tasks.remove(groupMemberOid);
			
			try {
				AgisGroupMember member =  GetGroupMember(groupMemberOid);
				if (member != null) {
					GroupClient.removeMember(groupMemberOid, _groupLeaderOid,GetGroupOid());
				}else {
					Log.debug("GROUP: running disconnect task for " + groupMemberOid+" group member is null");
				}
			} catch (atavism.msgsys.NoRecipientsException e) {
				group.RemoveOfflineGroupMember(groupMemberOid);
				if(group.GetNumGroupMembers()==1) {
					GroupPlugin.RemoveGroup(group.getOid());
				}
			}
			
			
			
			
		}
    }
}
