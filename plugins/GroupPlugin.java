package atavism.agis.plugins;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import atavism.agis.core.AgisEffect.EffectState;
import atavism.agis.database.AccountDatabase;
import atavism.agis.database.CombatDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.plugins.ChatClient.TargetedComReqMessage;
import atavism.agis.objects.AgisGroup;
import atavism.agis.objects.AgisGroupMember;
import atavism.agis.util.*;
import atavism.msgsys.*;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.Hook;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.messages.PropertyMessage;
import atavism.server.objects.InstanceRestorePoint;
import atavism.server.plugins.InstanceClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.ObjectInfo;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.plugins.VoiceClient;
import atavism.server.util.AORuntimeException;
import atavism.server.util.Log;
import atavism.server.util.Logger;

public class GroupPlugin extends EnginePlugin {

    /*
     * Properties
     */
    
    protected static final Logger _log = new Logger("GroupPlugin");
    protected static List<String> _registeredStats = new ArrayList<String>();
    protected static ConcurrentHashMap<OID, AgisGroup> _currentGroups = new ConcurrentHashMap<OID, AgisGroup>();
    protected static int _maxGroupSize = 4; // Default max group size to 4
    HashMap<OID, ScheduledFuture> tasks = new HashMap<OID, ScheduledFuture>();

    /*
     * Constructors
     */ 
    public GroupPlugin() {
        super("Group");
        setPluginType("Group");
    }

    public String GetName() {
        return "GroupPlugin";
    }

    /*
     * Events
     */ 
    
	public void onActivate() {
		super.onActivate();

		// register message hooks
		RegisterHooks();
		try {
			MessageTypeFilter filterNeedsResponse = new MessageTypeFilter();
			filterNeedsResponse.addType(GroupClient.MSG_TYPE_GET_GROUP_MEMBERS);
			/* Long sub = */ Engine.getAgent().createSubscription(filterNeedsResponse, this, MessageAgent.RESPONDER);
		} catch (Exception e) {
			throw new AORuntimeException("activate failed", e);
		}
		// setup message filters
		MessageTypeFilter filter = new MessageTypeFilter();
		filter.addType(PropertyMessage.MSG_TYPE_PROPERTY);
	//	filter.addType(GroupClient.MSG_TYPE_GET_GROUP_MEMBERS);
		filter.addType(GroupClient.MSG_TYPE_GROUP_INVITE);
		filter.addType(GroupClient.MSG_TYPE_GROUP_LEAVE);
		filter.addType(GroupClient.MSG_TYPE_GROUP_REMOVE_MEMBER);
		filter.addType(GroupClient.MSG_TYPE_GROUP_CHAT);
		filter.addType(GroupClient.MSG_TYPE_GROUP_INVITE_RESPONSE);
		filter.addType(GroupClient.MSG_TYPE_GROUP_SET_ALLOWED_SPEAKER);
		filter.addType(GroupClient.MSG_TYPE_GROUP_MUTE_VOICE_CHAT);
		filter.addType(GroupClient.MSG_TYPE_GROUP_VOICE_CHAT_STATUS);
		filter.addType(VoiceClient.MSG_TYPE_VOICE_MEMBER_ADDED);
		filter.addType(GroupClient.MSG_TYPE_GROUP_INVITE_BY_NAME);
		filter.addType(GroupClient.MSG_TYPE_CREATE_GROUP);
		filter.addType(GroupClient.MSG_TYPE_GROUP_SETTINGS);
		filter.addType(GroupClient.MSG_TYPE_GROUP_PROMOTE_LEADER);
		filter.addType(WorldManagerClient.MSG_TYPE_SPAWNED);
		Engine.getAgent().createSubscription(filter, this);
        
        //setup responder message filters
		MessageTypeFilter responderFilter = new MessageTypeFilter();
		// responderFilter.addType(LoginMessage.MSG_TYPE_LOGIN);
		responderFilter.addType(LogoutMessage.MSG_TYPE_LOGOUT);
		responderFilter.addType(GroupClient.MSG_TYPE_REQUEST_GROUP_INFO);
	//	responderFilter.addType(GroupClient.MSG_TYPE_GET_GROUP_MEMBERS);
		Engine.getAgent().createSubscription(responderFilter, this, MessageAgent.RESPONDER);

		if (Log.loggingDebug)
			_log.debug("GroupPlugin activated.");

		// Load settings
		ContentDatabase ctDB = new ContentDatabase(false);
		String groupSize = ctDB.loadGameSetting("GROUP_MAX_SIZE");
		if (groupSize != null) {
			_maxGroupSize = Integer.parseInt(groupSize);
			Log.debug("Loaded Game Setting maxGroupSize=" + _maxGroupSize);
		}
		String disconnectTimeout = ctDB.loadGameSetting("GROUP_DISCONNECT_TIMEOUT");
		if (disconnectTimeout != null) {
			GROUP_DISCONNECT_TIMEOUT = Integer.parseInt(disconnectTimeout);
			Log.debug("Loaded Game Setting GROUP_DISCONNECT_TIMEOUT=" + GROUP_DISCONNECT_TIMEOUT);
		}
		
		String groupLootRoll = ctDB.loadGameSetting("GROUP_LOOT_DEFAULT_ROLL");
		if (groupLootRoll != null) {
			GROUP_LOOT_DEFAULT_ROLL = Integer.parseInt(groupLootRoll);
			Log.debug("Loaded Game Setting GROUP_LOOT_DEFAULT_ROLL=" + GROUP_LOOT_DEFAULT_ROLL);
		}
	
		String groupLootDice = ctDB.loadGameSetting("GROUP_LOOT_DEFAULT_DICE");
		if (groupLootDice != null) {
			GROUP_LOOT_DEFAULT_DICE = Integer.parseInt(groupLootDice);
			Log.debug("Loaded Game Setting GROUP_LOOT_DEFAULT_DICE=" + GROUP_LOOT_DEFAULT_DICE);
		}
		String groupLootGrade = ctDB.loadGameSetting("GROUP_LOOT_DEFAULT_GRADE");
		if (groupLootGrade != null) {
			GROUP_LOOT_DEFAULT_GRADE = Integer.parseInt(groupLootGrade)-1;
			Log.debug("Loaded Game Setting GROUP_LOOT_DEFAULT_GRADE=" + GROUP_LOOT_DEFAULT_GRADE);
		}
		
		CombatDatabase cDB = new CombatDatabase(false);
		LinkedList<String> sharedStats = cDB.LoadGroupSharedStats();
		for (String statname : sharedStats) {
			RegisterStat(statname);
		}

		// Hard coded level stat
		RegisterStat(CombatPlugin.LEVEL_STAT);
		// Hard coded effects stat
		RegisterStat("effects");


		aDB = new AccountDatabase(false);
    	
    }

    /*
     * Methods
     */ 
    
    public void RegisterHooks() {
		getHookManager().addHook(PropertyMessage.MSG_TYPE_PROPERTY, new PropertyHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GET_GROUP_MEMBERS, new GroupGetMembersHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GROUP_INVITE, new GroupInviteHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GROUP_INVITE_RESPONSE, new GroupInviteResponseHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GROUP_REMOVE_MEMBER, new GroupRemoveMemberHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GROUP_CHAT, new GroupChatHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_REQUEST_GROUP_INFO, new RequestGroupInfoHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GROUP_LEAVE, new GroupLeaveHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GROUP_SETTINGS, new GroupSettingsHook());
		// getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN,
		// new LoginHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogOutHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GROUP_SET_ALLOWED_SPEAKER, new SetAllowedSpeakerHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GROUP_MUTE_VOICE_CHAT, new MuteGroupHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GROUP_VOICE_CHAT_STATUS, new VoiceStatusHook());
		getHookManager().addHook(VoiceClient.MSG_TYPE_VOICE_MEMBER_ADDED, new VoiceMemberAddedHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GROUP_INVITE_BY_NAME, new GroupInviteByNameHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_CREATE_GROUP, new CreateGroupHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GROUP_PROMOTE_LEADER, new PromoteToLeaderHook());
    }

	public static void RegisterStat(String stat) {
		_registeredStats.add(stat);
	}

	public static AgisGroup GetGroup(OID groupOid) {
		if (groupOid == null)
			return null;
		if (_currentGroups != null && _currentGroups.containsKey(groupOid))
			return _currentGroups.get(groupOid);
		else
			return null;
	}

	public static void RemoveGroup(OID groupOid) {
		if (groupOid == null)
			return ;
		if (_currentGroups != null && _currentGroups.containsKey(groupOid))
			 _currentGroups.remove(groupOid);
		
	}
	
	
	public static List<String> GetRegisteredStats() {
		return _registeredStats;
	}

    /**
     * Gets information about the group and its members and sends it to each
     * group member
     */
	public static void SendGroupUpdate(AgisGroup group) {
		MessageAgent agent = Engine.getAgent();
		TargetedExtensionMessage groupUpdateMsg = new TargetedExtensionMessage();
		groupUpdateMsg.setExtensionType(GroupClient.EXTMSG_GROUP_UPDATE);
		groupUpdateMsg.setProperty("maxGroupSize", String.valueOf(_maxGroupSize));
		// groupOid is the unique key for the group and the voice group
		groupUpdateMsg.setProperty("groupOid", group.getOid());
		
		groupUpdateMsg.setProperty("roll", group.GetRoll());
		groupUpdateMsg.setProperty("dice", group.GetDice());
		groupUpdateMsg.setProperty("grade", group.GetGrade());

		// Set each group members info
		int counter = 1; // Counter is used to supply the client with an ordered key for accessing group members
		Hashtable<OID, AgisGroupMember> groupMembers = group.GetGroupMembers();
		// We must make sure keys are ordered properly by order the player joined the group
		Set<OID> groupMemberKeys = new TreeSet<OID>(group.GetGroupMembers().keySet());

		for (OID groupMemberKey : groupMemberKeys) {
			HashMap<String, Serializable> groupMemberInfo = new HashMap<String, Serializable>();
			if(groupMembers.containsKey(groupMemberKey)) {
				AgisGroupMember groupMember = groupMembers.get(groupMemberKey);
				if (groupMember != null) {
					groupMemberInfo.put("memberOid", groupMember.GetGroupMemberOid());
					groupMemberInfo.put("status", groupMember.GetMemberStatus());
					groupMemberInfo.put("name", groupMember.GetGroupMemberName());
					groupMemberInfo.put("portrait", groupMember.GetGroupMemberPortrait());
					groupMemberInfo.put("level", groupMember.GetGroupMemberLevel());
					groupMemberInfo.put("voiceEnabled", groupMember.GetVoiceEnabled());
					groupMemberInfo.put("allowedSpeaker", groupMember.GetAllowedSpeaker());
					groupMemberInfo.put("groupMuted", group.GetGroupMuted());
					groupMemberInfo.put("statCount", _registeredStats.size());
					for (int i = 0; i < _registeredStats.size(); i++) { // Add any registered stats to info
						String stat = _registeredStats.get(i);
						groupMemberInfo.put("stat" + i, stat);
						groupMemberInfo.put("stat" + i + "Value", groupMember.GetGroupMemberStat(stat));
					}
					groupMemberInfo.put("effects", groupMember.GetGroupMemberEffects());
					// Store counter as our key so we can sort the list on the client
					groupUpdateMsg.setProperty(String.valueOf(counter), groupMemberInfo);
					//if group does not have a leader set, then the first group member in order is now the leader
					if ((group.GetGroupLeaderOid() == null) && (counter == 1) && !group.GetNoLeader())
						group.SetGroupLeaderOid(groupMember.GetGroupMemberOid());
					counter += 1;
				}
			}
        }
        
        // do this after looping through group members incase the group leader was changed
        groupUpdateMsg.setProperty("groupLeaderOid", group.GetGroupLeaderOid());

        // Send message to each group member
        for (AgisGroupMember groupMember : group.GetGroupMembers().values()) {
        	if (groupMember.GetMemberStatus() != AgisGroupMember.MEMBER_STATUS_OFFLINE) {
        		groupUpdateMsg.setTarget(groupMember.GetGroupMemberOid());
        		agent.sendBroadcast(groupUpdateMsg);
        	}
        }
    }

    protected void RemoveGroupMember(OID oid, boolean kicked, OID kicker, boolean removeFromInstance) {
		if (Log.loggingDebug)_log.debug("GROUP: removing group member: " + oid + ". kicked="+kicked+" kicker="+kicker+" Remove from instance? " + removeFromInstance);
     	LinkedList<String> param = new LinkedList<String>();
     	param.add("isGrouped");
     	param.add("groupOid");
    	HashMap<String, Serializable> objectParams = CombatClient.getCombatInfoParams(oid, param);
		if (Log.loggingDebug)_log.debug("GROUP: removing group member: " + oid + ". objectParams="+objectParams);
		AgisGroup group =null;
    	if((OID) objectParams.get("groupOid")==null) {
			if (Log.loggingDebug)_log.debug("GROUP: removing group member: " + oid + ". groupOid is null");
			AgisGroupMember agm = GetGroupMember(oid);

			//AgisGroup group = GetGroup((OID)infoParams.get("groupOid"));
			if(agm!=null) {
				group = GetGroup(agm.GetGroupOid());
				if(group==null) {
					return;
				}
				if (Log.loggingDebug)_log.debug("GROUP: removing group member: " + oid + ". group found by player Oid ");
			} else {
				if (Log.loggingDebug)_log.debug("GROUP: removing group member: " + oid + ". group not found by player Oid |");
				return;
			}
    	}
		if(group == null)
        	group = GetGroup((OID) objectParams.get("groupOid"));
        //Check to ensure group is valid
        if (group == null){
            if (Log.loggingDebug) {
                _log.debug("GroupPlugin.RemoveGroupMember : group is null");
            }
         
            HashMap<String, Serializable> sobjectParams = new HashMap<String, Serializable>();
        	sobjectParams.put("groupMemberOid", null);
        	sobjectParams.put("groupOid", null);
            CombatClient.setCombatInfoParams(oid,sobjectParams);
        	EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "groupOid", null);
            return;
        }
		if (Log.loggingDebug) _log.debug("GroupPlugin.RemoveGroupMember : kicked "+kicked);
        
        // If kicked, verify the kicker is a group leader
        if (kicked) {
        	if (group.GetGroupLeaderOid() !=null && !group.GetGroupLeaderOid().equals(kicker)) {
				if (Log.loggingDebug)_log.debug("GroupPlugin.RemoveGroupMember leader is not null and not kicker");
				return;
			}
        }
        
        // Dis-associate group with member and member with group
        group.RemoveGroupMember(oid);

        //If the group leader left, trigger system to set new group leader
        if(oid.equals(group.GetGroupLeaderOid())){
            group.SetGroupLeaderOid(null); //Clear group leader Oid so it is reset in SendGroupUpdate method
        }
        
        // Check if user was in a group instance - if so, remove them
        if (removeFromInstance) {
        	removeMemberFromInstance(group,oid);
        }
		if (Log.loggingDebug)_log.debug("GroupPlugin.RemoveGroupMember : NumGroupMembers="+group.GetNumGroupMembers()+" no leader="+group.GetNoLeader());
        // Send update to group
		if(group.GetNoLeader()){
			if (group.GetNumGroupMembers() > 0) {
				SendGroupUpdate(group);
				sendMembersUpdate(group.GetGroupOid());
			} else {
				if (removeFromInstance) {
					removeMemberFromInstance(group, oid);
				}
				TargetedExtensionMessage groupUpdateMsg = new TargetedExtensionMessage(oid);
				groupUpdateMsg.setExtensionType(GroupClient.EXTMSG_GROUP_UPDATE);
				Engine.getAgent().sendBroadcast(groupUpdateMsg);
				_currentGroups.remove(group.GetGroupOid());
				sendMembersUpdate(group.GetGroupOid());
				group = null;
			}
			if (Log.loggingDebug)_log.debug("GroupPlugin.RemoveGroupMember : no leader end");
		} else if (group.GetNumGroupMembers() > 1) {
            SendGroupUpdate(group);     
            sendMembersUpdate(group.GetGroupOid());
        } else  {
            // Update remaining member that they no longer are in group either
            // since one person can't be a group
          //  CombatInfo groupLeader = CombatPlugin.getCombatInfo(group.GetGroupLeaderOid());
        	LinkedList<String> leaderparam = new LinkedList<String>();
        	leaderparam.add("isGrouped");
        	HashMap<String, Serializable> groupLeaderParams = new HashMap<String, Serializable>();
			if (Log.loggingDebug)_log.debug("GroupPlugin.RemoveGroupMember : groupLeaderParams GroupLeaderOid="+group.GetGroupLeaderOid());
			try {
				groupLeaderParams = CombatClient.getCombatInfoParams(group.GetGroupLeaderOid(), leaderparam);
			} catch (Exception e){
				_log.exception("GroupPlugin.RemoveGroupMember : groupLeaderParams Exception",e);
			}
			if (Log.loggingDebug)_log.debug("GroupPlugin.RemoveGroupMember : groupLeaderParams"+groupLeaderParams);
               
        	    
            if(groupLeaderParams.containsKey("subject")){
            	if (removeFromInstance) {
                	removeMemberFromInstance(group, group.GetGroupLeaderOid());
                }
                group.RemoveGroupMember(group.GetGroupLeaderOid()); //Removing the last group member triggers removal of voice group
                TargetedExtensionMessage groupUpdateMsg = new TargetedExtensionMessage(group.GetGroupLeaderOid());
                groupUpdateMsg.setExtensionType(GroupClient.EXTMSG_GROUP_UPDATE);
                Engine.getAgent().sendBroadcast(groupUpdateMsg);
            } else {
                _log.error("GroupPlugin.RemoveGroupMember - Group leader is null");
            }
            sendMembersUpdate(group.GetGroupOid());
            _currentGroups.remove(group.GetGroupOid());
            group = null;
        }

        // Send group update message to player being removed in order to clear their group info
        TargetedExtensionMessage groupUpdateMsg = new TargetedExtensionMessage(oid);
        groupUpdateMsg.setExtensionType(GroupClient.EXTMSG_GROUP_UPDATE);
        Engine.getAgent().sendBroadcast(groupUpdateMsg);
		if (Log.loggingDebug)_log.debug("GroupPlugin.RemoveGroupMember : end");
        
    }

    protected void removeMemberFromInstance(AgisGroup group, OID memberOid) {
    	ObjectInfo oi = WorldManagerClient.getObjectInfo(memberOid);
    	if(oi == null)
    		return;
    	OID instanceOid = oi.instanceOid;
    	OID groupOid = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_GROUP_OID).groupOid;
    	if (groupOid != null && groupOid.equals(group.GetGroupOid())) {
    		LinkedList restoreStack = (LinkedList) EnginePlugin.getObjectProperty(memberOid, Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_INSTANCE_RESTORE_STACK);
    		InstanceRestorePoint restorePoint = null;
    		if (restoreStack.size() < 2) {
    			Log.error("GROUP: restore stack only has " + restoreStack.size() + " entries. There should be at least 2");
    			restorePoint = (InstanceRestorePoint) restoreStack.get(restoreStack.size());
    			return;
    		} else {
    			restorePoint = (InstanceRestorePoint) restoreStack.get(restoreStack.size() - 1);
    		}
    		
    		BasicWorldNode node = new BasicWorldNode();
			if (Log.loggingDebug)Log.debug("GROUP: teleporting member from instance: " + instanceOid + " to: " + restorePoint.getInstanceID());
    		instanceOid = InstanceClient.getInstanceOid(restorePoint.getInstanceID());
    		node.setInstanceOid(instanceOid);
    		node.setLoc(restorePoint.getLoc());
    		InstanceClient.objectInstanceEntry(memberOid, node, InstanceClient.InstanceEntryReqMessage.FLAG_NONE);
    	}
    }
    
    protected void removeOfflineMember(OID targetOid, OID kicker, OID groupOid) {
    	Log.debug("GROUP: removing offline member");
    	
    	LinkedList<String> param = new LinkedList<String>();
    	param.add("groupOid");
    	HashMap<String,Serializable> leaderParams = null ;
    			try {
					leaderParams = CombatClient.getCombatInfoParams(kicker, param );
				} catch (atavism.msgsys.NoRecipientsException e) {
					
				}
    	
    	AgisGroup group = null;
		if (leaderParams != null) {
			group = GetGroup((OID) leaderParams.get("groupOid"));
			if (group == null || !group.GetGroupLeaderOid().equals(kicker)) {
				Log.debug("GROUP: removing offline member leader != kicker");
				return;
			}
		} else {
			if (groupOid != null)
				group = GetGroup(groupOid);
			else
				Log.error("GROUP: removing offline member groupOid = null");
		}
    	
    	// Dis-associate group with member and member with group
    	if (group != null) {
    		group.RemoveOfflineGroupMember(targetOid);
        
    		// Send update to group
	        if (group.GetNumGroupMembers() > 1) {
	            SendGroupUpdate(group);   
	            sendMembersUpdate(group.GetGroupOid());
	        } else {
	        	Log.debug("GROUP: dissolving group due to only 1 member left");
	            // Update remaining member that they no longer are in group either since one person can't be a group
	         //   CombatInfo groupLeader = CombatPlugin.getCombatInfo(group.GetGroupLeaderOid());
	          //  if(groupLeader != null) {
	            	removeMemberFromInstance(group, group.GetGroupLeaderOid());
	            	Log.debug("GROUP: I groupLeader="+group.GetGroupLeaderOid());
	  	            group.RemoveGroupMember(group.GetGroupLeaderOid()); //Removing the last group member triggers removal of voice group
	                Log.debug("GROUP: II groupLeader="+group.GetGroupLeaderOid());
	               
	                TargetedExtensionMessage groupUpdateMsg = new TargetedExtensionMessage(group.GetGroupLeaderOid());
	                groupUpdateMsg.setExtensionType(GroupClient.EXTMSG_GROUP_UPDATE);
	                Engine.getAgent().sendBroadcast(groupUpdateMsg);
	           // } else {
	           //     _log.error("GroupPlugin.RemoveGroupMember - Group leader is null");
	           // }
	                sendMembersUpdate(group.GetGroupOid());
	            _currentGroups.remove(group.GetGroupOid());
				if (Log.loggingDebug) Log.debug("GROUP: removed group from currentGroups. Num groups: " + _currentGroups.size());
	            group = null;
	        }
    	}else{
    		Log.debug("GROUP: removing offline member Group is null");
    	}
    }
   
    protected void PromoteMemberToLeader(OID oid, OID promoter) {
    	LinkedList<String> param = new LinkedList<String>();
    	param.add("groupOid");
    	HashMap<String,Serializable> infoParams = CombatClient.getCombatInfoParams(oid, param );
    
    	
        AgisGroup group = GetGroup((OID)infoParams.get("groupOid"));
        Log.debug("GROUP: got promote message for group: " + group);
        //Check to ensure group is valid
        if (group == null){
            if (Log.loggingDebug) {
                _log.error("GroupPlugin.PromoteMemberToLeader : group is null");
            }
            infoParams.clear();
            infoParams.put("groupMemberOid", null);
            infoParams.put("groupOid", null);
            CombatClient.setCombatInfoParamsNoResponse(oid,infoParams);
        	EnginePlugin.setObjectPropertyNoResponse(oid, WorldManagerClient.NAMESPACE, "groupOid", null);
              return;
        }
        
        // Verify the leader is doing the promoting
        if (!group.GetGroupLeaderOid().equals(promoter)) {
        	Log.debug("GROUP: promoter is not the group leader: ");
        	return;
        }

        group.SetGroupLeaderOid(oid); //Clear group leader Oid so it is reset in SendGroupUpdate method

        // Send update to group
        SendGroupUpdate(group);            
    }

    protected void SetMemberOffline(OID oid) {
    	/*LinkedList<String> param = new LinkedList<String>();
    	param.add("groupOid");
    	HashMap<String,Serializable> infoParams = CombatClient.getCombatInfoParams(oid, param );*/
    	AgisGroupMember agm = GetGroupMember(oid);
		
    	//AgisGroup group = GetGroup((OID)infoParams.get("groupOid"));
    	AgisGroup group = GetGroup(agm.GetGroupOid());
        Log.debug("GROUP: setting member offline for group: " + group);
        //Check to ensure group is valid
        if (group == null) {
            if (Log.loggingDebug) {
                _log.error("GroupPlugin.SetMemberOffline : group is null");
            }
            HashMap<String,Serializable>  infoParams = new HashMap<String,Serializable>();
            infoParams.clear();
            infoParams.put("groupMemberOid", null);
            infoParams.put("groupOid", null);
			try {
				CombatClient.setCombatInfoParamsNoResponse(oid, infoParams);
			} catch (NoRecipientsException e){
				Log.warn("Group: SetMemberOffline cant find object");
			}
			try {
				EnginePlugin.setObjectPropertyNoResponse(oid, WorldManagerClient.NAMESPACE, "groupOid", null);
			} catch (NoRecipientsException e){
				Log.warn("Group: SetMemberOffline cant find object");
			} catch (Exception e){
				Log.exception("SetMemberOffline ",e);
			}
            return;
        }
        
        group.SetMemberOffline(oid);
        SendGroupUpdate(group);
    }
    
    protected void SetMemberOnline(OID oid) {
    	LinkedList<String> param = new LinkedList<String>();
    	param.add("groupOid");
    	HashMap<String,Serializable> infoParams = CombatClient.getCombatInfoParams(oid, param );
    
    	AgisGroup group = GetGroup((OID)infoParams.get("groupOid"));
		if(Log.loggingDebug) Log.debug("GROUP: setting member online for group: " + group);
        //Check to ensure group is valid
        if (group == null || group.GetGroupMember(oid) == null) {
            if (Log.loggingDebug) {
                _log.debug("GroupPlugin.SetMemberOnline : group is null");
            }
            infoParams.clear();
            infoParams.put("groupMemberOid", null);
            infoParams.put("groupOid", null);
            CombatClient.setCombatInfoParams(oid,infoParams);
            // Also kick them out of the instance
            if (group != null) {
            	removeMemberFromInstance(group,oid);
            }
            
            return;
        }
        
        group.SetMemberOnline(oid);
        SendGroupUpdate(group);
    }

    /**
     * Sets the maximum number of players that can be in a single group - Default is 8
     */
    public static void SetMaxGroupSize(int size) {
        _maxGroupSize = size;
    }

    /**
     * Sends update to group members about the group and its members
     */
    protected boolean UpdateGroupMemberProps(PropertyMessage propMsg) {
    	AgisGroupMember member =  GetGroupMember(propMsg.getSubject());
    	if(member == null) {
    		return true;
    	}
		Map<String, Serializable> statsToUpdate = new HashMap<String, Serializable>();
		Set<String> props = propMsg.keySet();
		for (String stat : _registeredStats) {
			_log.debug("GROUP: checking stat: " + stat);
			if (props.contains(stat)) {
				statsToUpdate.put(stat, propMsg.getProperty(stat));
			}
		}
		if(statsToUpdate.size()==0) {
			//Log.error("UpdateGroupMemberProps skip");
			return true;
		}
		HashMap<String,Serializable> infoParams = null;
		try {
			LinkedList<String> param = new LinkedList<String>();
			param.add("isGrouped");
			param.add("groupOid");
			param.add("groupMemberOid");
			param.add("effects");
			infoParams = CombatClient.getCombatInfoParams(propMsg.getSubject(), param);
		}catch (NoRecipientsException e){
			Log.exception("UpdateGroupMemberProps",e);
			return true;
		}
		if(Log.loggingDebug) Log.debug("GROUP: got propMessage for subject: " + propMsg.getSubject()+" infoParams="+infoParams);
        if (!infoParams.containsKey("subject")) {
        	  Log.debug("GROUP: got propMessage got no subject ");
            return true;//from false
        }
        Log.debug("GROUP: got propMessage got subject ");
        
        // If the subject is grouped, continue
        if ((boolean)infoParams.get("isGrouped")) {
        	Log.debug("GROUP: got propMessage for grouped player");


            // Check our registered properties to see if any are in the update list
			if(Log.loggingDebug) Log.debug("UpdateGroupMemberProps: "+propMsg.getSubject()+" props:"+props+" statsToUpdate="+statsToUpdate);
            
            if (props.contains("level")) {
                statsToUpdate.put("level", propMsg.getProperty("level"));
            }
            

			if (infoParams.get("effects") instanceof LinkedList<?>) {
				statsToUpdate.put("effects", infoParams.get("effects"));
			} else {

				HashMap<Integer, HashSet<EffectState>> effects = (HashMap<Integer, HashSet<EffectState>>) infoParams.get("effects");// subject.getEffects();

				LinkedList<String> effectsProp = new LinkedList<String>();
				Set<Integer> effectsKeys = effects.keySet();
				for (int k : effectsKeys) {
					if (effects.get(k) == null) {
						if(Log.loggingDebug)Log.debug("UpdateGroupMemberProps: key=" + k + " " + effects.get(k));
					} else {
						for (EffectState eState : effects.get(k)) {
							if(Log.loggingDebug)Log.debug("UpdateGroupMemberProps: key=" + k + " " + eState);
							if (eState.getEffect() != null) {
								String effectData = eState.getEffectID() + "," + eState.getCurrentStack() + "," + eState.getEffect().isBuff() + "," + eState.getEndTime() + "," + eState.getTimeUntilEnd() + ","
										+ eState.isActive() + "," + eState.getEffect().getDuration() + "," + eState.getEffect().isPassive() + "," + eState.getEffect().isStackTime();
								Log.debug("Group Effects " + effectData);
								effectsProp.add(effectData);
							}
						}
					}
				}
            statsToUpdate.put("effects" ,effectsProp);
            }

			if(Log.loggingDebug)Log.debug("GROUP: got stat count: " + statsToUpdate.size());
			if(Log.loggingDebug)Log.debug("GROUP: got stat: " + statsToUpdate);
			// If any properties that the group cares about was updated,
			// send out a message to all the group members
			if (statsToUpdate.size() > 0 && infoParams.get("isGrouped") != null) {
				AgisGroup group = GetGroup((OID) infoParams.get("groupOid"));
				if (group == null) {
					_log.error("GroupPlugin.UpdateGroupMemberProps - group is null");
					infoParams.clear();
					infoParams.put("groupMemberOid", null);
					infoParams.put("groupOid", null);
					try {
						CombatClient.setCombatInfoParamsNoResponse(propMsg.getSubject(), infoParams);
					}catch (NoRecipientsException e){
						Log.exception("UpdateGroupMemberProps",e);
					}
					try{
					EnginePlugin.setObjectPropertyNoResponse(propMsg.getSubject(), WorldManagerClient.NAMESPACE, "groupOid", null);
					}catch (NoRecipientsException e){
						Log.exception("UpdateGroupMemberProps",e);
					}
					return true;
				}

				SendGroupPropertyUpdate(propMsg.getSubject(), group, statsToUpdate);
			}
		}else {
			if(Log.loggingDebug) Log.debug("UpdateGroupMemberProps: not grouped oid="+propMsg.getSubject()+" props:"+propMsg.getPropertyMapRef());
		}
		return true;
	}

    /**
     * SendGroupPropertyUpdate - Sends an ao.GROUP_PROPERTY_UPDATE message to each client in the group client
     * @param playerOid - Player whos property changed
     * @param group - Group in which the subject belongs to
     * @param statsToUpdate - Map<String, Serializable> of properties that were updated
     */
	protected void SendGroupPropertyUpdate(OID playerOid, AgisGroup group, Map<String, Serializable> statsToUpdate) {

		Set<AgisGroupMember> members =  new HashSet<AgisGroupMember>(group.GetGroupMembers().values());
		for (AgisGroupMember groupEntry: members){
			if (groupEntry.GetMemberStatus() == AgisGroupMember.MEMBER_STATUS_OFFLINE) {
				continue;
			}
			try {
				TargetedExtensionMessage updateMessage = new TargetedExtensionMessage(groupEntry.GetGroupMemberOid());

				updateMessage.setExtensionType(GroupClient.EXTMSG_GROUP_PROPERTY_UPDATE);
				updateMessage.setProperty("memberOid", playerOid); // member being updated
				updateMessage.setProperty("statCount", statsToUpdate.size());
				int statNum = 0;
				for (String stat : statsToUpdate.keySet()) {
					updateMessage.setProperty("stat" + statNum, stat);
					updateMessage.setProperty("stat" + statNum + "Value", statsToUpdate.get(stat));
					statNum++;
				}
				Engine.getAgent().sendBroadcast(updateMessage);
			}catch (NoRecipientsException e){
				Log.exception("SendGroupPropertyUpdate ",e);
			}
		}
	}

    /**
     * Handles logic for an invite request response - either accepted or declined
     * Creates a new group if the inviter is not currently grouped
     */
    protected boolean HandleInviteResponse(ExtensionMessage inviteMsg) {
        _log.debug("GroupPlugin.HandleInviteResponse");

        OID inviterOid = (OID) inviteMsg.getProperty("groupLeaderOid");
        OID inviteeOid = inviteMsg.getSubject();
		if(Log.loggingDebug)  _log.debug("GroupPlugin.HandleInviteResponse: inviterOid=" + inviterOid + ", inviteeOid=" + inviteeOid);

     //   CombatInfo invitee = CombatPlugin.getCombatInfo(inviteeOid);
      //  CombatInfo inviter = CombatPlugin.getCombatInfo(inviterOid);
                
        LinkedList<String> param = new LinkedList<String>();
    	param.add("isGrouped");
    	param.add("groupOid");
    	param.add("groupMemberOid");
    	param.add("isPendingGroupInvite");
    	  HashMap<String,Serializable> inviteeParams = CombatClient.getCombatInfoParams(inviteeOid, param );
    	  HashMap<String,Serializable> inviterParams = CombatClient.getCombatInfoParams(inviterOid, param );
		if(Log.loggingDebug) _log.debug("GroupPlugin.HandleInviteResponse: inviteeParams="+inviteeParams+" inviterParams="+inviterParams);
        
        
        
        if (/*inviter == null || invitee == null*/!inviteeParams.containsKey("subject") || !inviterParams.containsKey("subject")) {
			if(Log.loggingDebug) _log.debug("GroupPlugin.HandleInviteResponse: null inviter/invitee, inviter=" + inviterParams.containsKey("subject") + ", invitee=" + inviteeParams.containsKey("subject"));
            return false;
        }
        
        if (/*!invitee.isPendingGroupInvite()*/!(boolean)inviteeParams.get("isPendingGroupInvite")) {
        	_log.debug("GROUP: user does not have a pending invite");
        	return true;
        }
        // Clear pending group invite flag
        /*
        invitee.setPendingGroupInvite(false);
        inviter.setPendingGroupInvite(false);
        */
       	_log.debug("GROUP: reset setPendingGroupInvite");
         HashMap<String,Serializable> objecParams =new  HashMap<String,Serializable> ();
        objecParams.put("setPendingGroupInvite", false);
        CombatClient.setCombatInfoParams(inviteeOid,objecParams);
        CombatClient.setCombatInfoParams(inviterOid,objecParams);
      	_log.debug("GROUP: check task");
           
        if (tasks.containsKey(inviterOid)) {
    		Log.debug("GROUP: cancelling invite member task");
    		tasks.get(inviterOid).cancel(true);
    		tasks.remove(inviterOid);
    	}
    	_log.debug("GROUP: check response from player");
        
        String response = inviteMsg.getProperty("response").toString();
		if(Log.loggingDebug)_log.debug("GROUP: response="+response);
        if (response.equals("accept")) {
            AgisGroup group = null;
            //Boolean voiceEnabled = (Boolean)inviteMsg.getProperty("groupVoiceEnabled");
			if(Log.loggingDebug)_log.debug("GROUP: isGrouped="+inviterParams.get("isGrouped"));
            
            if (/*inviter.isGrouped()*/(boolean)inviterParams.get("isGrouped")) { // Add invitee to group
                group = GetGroup(/*inviter.getGroupOid()*/(OID)inviterParams.get("groupOid"));
                if(group == null){
                    _log.error("GroupPlugin.HandleInviteResponse - group is null");
                    /*inviter.setGroupMemberOid(null);
                    inviter.setGroupOid(null);*/
                    objecParams.clear();
                    objecParams.put("groupMemberOid", null);
                    objecParams.put("groupOid", null);
                    CombatClient.setCombatInfoParams(inviterOid,objecParams);
                    return false;
                }
                AgisGroupMember groupMember = group.AddGroupMember(/*invitee*/inviteeOid); 
                //groupMember.SetVoiceEnabled(voiceEnabled);
            } else { // Create a new group
                group = new AgisGroup();
                AgisGroupMember groupLeader = group.AddGroupMember(/*inviter*/inviterOid);
                groupLeader.SetVoiceEnabled(true); //TODO: Figure out how to get leader's setting
                group.SetGroupLeaderOid(inviterOid/*inviter.getOwnerOid()*/);
                AgisGroupMember groupMember = group.AddGroupMember(/*invitee*/inviteeOid);  
                //groupMember.SetVoiceEnabled(voiceEnabled);
                _currentGroups.put(group.GetGroupOid(), group); // Add to our list to track
				if(Log.loggingDebug) Log.debug("GROUP: added group to currentGroups 1. Num groups: " + _currentGroups.size());
          
                // If the group leader is already in a dungeon, change the instances group oid
                OID instanceOid = WorldManagerClient.getObjectInfo(inviterOid).instanceOid;
                OID instanceGroupOid = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_GROUP_OID).groupOid;
                if (instanceGroupOid != null && /*inviter.getOwnerOid()*/inviterOid.equals(instanceGroupOid)) {
                	InstanceClient.setInstanceGroup(instanceOid, group.GetGroupOid());
                }
            }    
            sendMembersUpdate(group.GetGroupOid());
            // Send clients info about the group and group members
            SendGroupUpdate(group);
        } else {
            _log.debug("GroupPlugin.HandleInviteResponse: declined");
            String inviteeName = aDB.getCharacterNameByOid(inviteeOid);
        	//String inviteeName = WorldManagerClient.getObjectInfo(/*invitee.getOwnerOid()*/inviteeOid).name;
            SendTargetedGroupMessage(/*inviter.getOwnerOid()*/inviterOid,inviterOid, inviteeName + " has declined your group invite.");
        }
        
        _log.debug("GroupPlugin.HandleInviteResponse: done");
        return true;
    }

    /**
     * Logic to handle group specific chat
     */
    protected void HandleGroupChat(ExtensionMessage groupChatMsg) {
        String message = groupChatMsg.getProperty("message").toString();
        OID senderOid = groupChatMsg.getSubject();
		if(Log.loggingDebug) Log.debug("HandleGroupChat: senderOid="+senderOid+" message="+message);
        LinkedList<String> param = new LinkedList<String>();
    	param.add("isGrouped");
    	param.add("groupOid");
    	param.add("groupMemberOid");
    	param.add("effects");
    	  HashMap<String,Serializable> senderParams = CombatClient.getCombatInfoParams(senderOid, param );
		if(Log.loggingDebug) Log.debug("HandleGroupChat: senderOid="+senderOid+" param="+param+" senderParams="+senderParams);
          
    	  
       // CombatInfo sender = CombatPlugin.getCombatInfo(senderOid);
        if (/*sender.isGrouped()*/(boolean)senderParams.get("isGrouped")) {
			if(Log.loggingDebug)  Log.debug("HandleGroupChat: senderOid="+senderOid+" is grouped");
        	//  AccountDatabase aDB = new AccountDatabase(false);
          	String senderName = aDB.getCharacterNameByOid(senderOid);
          	// String senderName =  WorldManagerClient.getObjectInfo(/*sender.getOwnerOid()*/senderOid).name;
            AgisGroup group = GetGroup(/*sender.getGroupOid()*/(OID)senderParams.get("groupOid"));
            if(group == null){
                _log.error("GroupPlugin.HandleGroupChat - group is null");
               /* sender.setGroupMemberOid(null);
                sender.setGroupOid(null);*/
                senderParams.clear();
                senderParams.put("groupMemberOid", null);
                senderParams.put("groupOid", null);
                CombatClient.setCombatInfoParams(senderOid,senderParams);
                return;
            }
            Collection<AgisGroupMember> groupMembers = group.GetGroupMembers().values();            
            //Send chat message to each group member
            for (AgisGroupMember groupMember : groupMembers) {
            	if (groupMember.GetMemberStatus() == AgisGroupMember.MEMBER_STATUS_OFFLINE) {
            		continue;
            	}
				if(Log.loggingDebug) Log.debug("HandleGroupChat: senderOid="+senderOid+" send to "+groupMember.GetGroupMemberOid());
              
            	SendTargetedGroupMessage(senderOid,groupMember.GetGroupMemberOid(), "[" + senderName + "]: " + message);
            }
        } else {
            SendTargetedGroupMessage(/*sender.getOwnerOid()*/senderOid,senderOid, "You are not grouped!");
        }
    }

    /**
     * Handles invite request by sending invite request message to the invitee
     */
    protected boolean HandleGroupInvite(OID inviterOid, OID inviteeOid) {
      //  CombatInfo inviter = CombatPlugin.getCombatInfo(inviterOid);
     //   CombatInfo invitee = CombatPlugin.getCombatInfo(inviteeOid);
        LinkedList<String> param = new LinkedList<String>();
    	param.add("isGrouped");
    	param.add("groupOid");
    	param.add("groupMemberOid");
    	param.add("isPendingGroupInvite");
    	  HashMap<String,Serializable> inviteeParams = CombatClient.getCombatInfoParams(inviteeOid, param );
    	  HashMap<String,Serializable> inviterParams = CombatClient.getCombatInfoParams(inviterOid, param );
    
    	  if (/*inviter == null || invitee == null*/!inviteeParams.containsKey("subject") || !inviterParams.containsKey("subject")) {
			  if(Log.loggingDebug)  _log.debug("GroupPlugin.HandleInviteResponse: null inviter/invitee, inviter=" + inviterParams.containsKey("subject") + ", invitee=" + inviteeParams.containsKey("subject"));
              return false;
          }
          
        if (Log.loggingDebug) {
            _log.debug("GroupPlugin.GroupInviteHook: Received group invite message inviter:"
                       + inviterOid
                       + " invitee:"
                       + inviteeOid);
        }    

        // A player should not be able to invite themselves to a group
        if(inviterOid.equals(inviteeOid)){
            return true;
        }
        
        if (/*inviter.isPendingGroupInvite()*/(boolean)inviterParams.get("isPendingGroupInvite")) {
        	return false;
        }
        
        if(/*inviter.isGrouped()*/(boolean)inviterParams.get("isGrouped")){
            AgisGroup group = GetGroup(/*inviter.getGroupOid()*/(OID)inviterParams.get("groupOid"));
            if(group == null){
                _log.error("GroupPlugin.HandleGroupInvite - Inviter's group is null");
               /* inviter.setGroupMemberOid(null);
                inviter.setGroupOid(null);*/
                inviterParams.clear();
                inviterParams.put("groupMemberOid", null);
                inviterParams.put("groupOid", null);
                CombatClient.setCombatInfoParams(inviterOid,inviterParams);
                     return false;
            }
            if (group.GetGroupMembers().size() >= _maxGroupSize){
                SendTargetedGroupMessage(/*inviter.getOwnerOid()*/inviterOid,inviterOid, "Your group is full.");
                return true;
            }
            if (!group.GetGroupLeaderOid().equals(inviterOid)){
            	// Only group leader can invite more people
                SendTargetedGroupMessage(/*inviter.getOwnerOid()*/inviterOid,inviterOid, "Only the group leader can invite new members");
                return true;
            }
        }
        
      //  AccountDatabase aDB = new AccountDatabase(false);
      	String inviteeName = aDB.getCharacterNameByOid(inviteeOid);
      	// String inviteeName = WorldManagerClient.getObjectInfo(/*invitee.getOwnerOid()*/inviteeOid).name;
        //Send message to inviter
        SendTargetedGroupMessage(/*inviter.getOwnerOid()*/inviterOid,inviterOid, "You have invited " + inviteeName + " to your group.");

        if (/*invitee.isGrouped()*/(boolean)inviteeParams.get("isGrouped")) {            
            SendTargetedGroupMessage(/*inviter.getOwnerOid()*/inviterOid,inviterOid, inviteeName + " is already grouped.");
        } else if (/*invitee.isPendingGroupInvite()*/(boolean)inviteeParams.get("isPendingGroupInvite")) {
            SendTargetedGroupMessage(/*inviter.getOwnerOid()*/inviterOid,inviterOid, inviteeName + " is already considering a group invite.");
        } else if (/*!invitee.isGrouped()*/!(boolean)inviteeParams.get("isGrouped")) {            
            // Set pending group invite flag
         /*   invitee.setPendingGroupInvite(true);
            inviter.setPendingGroupInvite(true);*/
            HashMap<String,Serializable> objecParams =new  HashMap<String,Serializable> ();
            objecParams.put("setPendingGroupInvite", true);
            CombatClient.setCombatInfoParams(inviteeOid,objecParams);
            CombatClient.setCombatInfoParams(inviterOid,objecParams);
            TargetedExtensionMessage inviteRequestMsg = new TargetedExtensionMessage(/*invitee.getOwnerOid()*/inviteeOid);
            inviteRequestMsg.setExtensionType(GroupClient.EXTMSG_GROUP_INVITE_REQUEST);
            inviteRequestMsg.setProperty("groupLeaderOid", /*inviter.getOwnerOid()*/inviterOid);
           String inviterName = aDB.getCharacterNameByOid(inviterOid);
          	//String inviterName = WorldManagerClient.getObjectInfo(/*inviter.getOwnerOid()*/inviterOid).name;
            inviteRequestMsg.setProperty("groupLeaderName", inviterName);
            inviteRequestMsg.setProperty("groupInviteTimeout", GroupPlugin.GROUP_INVITE_TIMEOUT);

         	MemberCancelInviteTimer timer = new MemberCancelInviteTimer( inviterOid,  inviteeOid, this);
        	ScheduledFuture sf = Engine.getExecutor().schedule(timer, (long) GroupPlugin.GROUP_INVITE_TIMEOUT * 1000, TimeUnit.MILLISECONDS);
        	tasks.put(inviterOid, sf);
			if(Log.loggingDebug)Log.debug("GROUP: created invite member task for: " + inviterOid);
            
            if (Log.loggingDebug) {
                _log.debug("GroupPlugin.GroupInviteHook: Sending group invite request inviter:"
                           + inviterOid
                           + " invitee:"
                           + inviteeOid);
            }

            Engine.getAgent().sendBroadcast(inviteRequestMsg);
        }
        return true;
    }

 public class MemberCancelInviteTimer implements Runnable {
    	
	 	protected OID inviterOid;
	 	protected OID inviteeOid;
 		protected GroupPlugin group;
    	
    	public MemberCancelInviteTimer(OID inviterOid, OID inviteeOid, GroupPlugin group) {
    		this.inviterOid = inviterOid;
    		this.inviteeOid = inviteeOid;
    		this.group = group;
    	}
    	
		@Override
		public void run() {
			// Check user still has items
			Log.debug("GROUP: running remove invite task for " + inviterOid);
			group.tasks.remove(inviterOid);
			/*   CombatInfo inviter = CombatPlugin.getCombatInfo(inviterOid);
		       CombatInfo invitee = CombatPlugin.getCombatInfo(inviteeOid);
			if (invitee != null) 
				  invitee.setPendingGroupInvite(false);
			if (inviter != null) {
				inviter.setPendingGroupInvite(false);*/
				 HashMap<String,Serializable> objecParams =new  HashMap<String,Serializable> ();
		            objecParams.put("setPendingGroupInvite", false);
		            CombatClient.setCombatInfoParams(inviteeOid,objecParams);
		            CombatClient.setCombatInfoParams(inviterOid,objecParams);
		        
		           
				 SendTargetedGroupMessage(inviterOid,inviterOid, "Invitation for " + aDB.getCharacterNameByOid(inviteeOid) + " was cancelled.");
				 SendTargetedGroupMessage(inviteeOid,inviteeOid, "Invitation from " + aDB.getCharacterNameByOid(inviterOid) + " was cancelled.");

			  TargetedExtensionMessage inviteRequestMsg = new TargetedExtensionMessage(/*invitee.getOwnerOid()*/inviteeOid);
	            inviteRequestMsg.setExtensionType(GroupClient.EXTMSG_GROUP_CANCEL_INVITE_REQUEST);
	            inviteRequestMsg.setProperty("groupLeaderOid", /*inviter.getOwnerOid()*/inviterOid);
	            String inviterName = aDB.getCharacterNameByOid(inviterOid);
	        //	 String inviterName = WorldManagerClient.getObjectInfo(/*inviter.getOwnerOid()*/inviterOid).name;
	            inviteRequestMsg.setProperty("groupLeaderName", inviterName);
	            Engine.getAgent().sendBroadcast(inviteRequestMsg);
	            //}
		}
    }
    
    
    
    
    
    
    
    /**
     * HandleGroupInfoRequest handles a request for information about a group.
     * Returns the groupOid, groupleaderOid and each member's Oid in a response message.
     */
    protected GroupClient.GroupInfo HandleGroupInfoRequest(/*CombatInfo subject*/OID oid){
        GroupClient.GroupInfo groupInfo = new GroupClient.GroupInfo();
        LinkedList<String> param = new LinkedList<String>();
    	param.add("isGrouped");
    	param.add("groupOid");
    	HashMap<String,Serializable> objectParams = CombatClient.getCombatInfoParams(oid, param );
        HashSet<OID> memberOids = new HashSet<OID>();
        if(/*subject.isGrouped()*/(boolean)objectParams.get("isGrouped")){
            AgisGroup group = GetGroup(/*subject.getGroupOid()*/(OID) objectParams.get("groupOid"));
            if(group == null){
                //_log.error("GroupPlugin.HandleGroupInfoRequest - group is null");
               /* subject.setGroupMemberOid(null);
                subject.setGroupOid(null);
                */
            	objectParams.clear();
            	objectParams.put("groupMemberOid", null);
            	objectParams.put("groupOid", null);
                CombatClient.setCombatInfoParams(oid,objectParams);
                return groupInfo;
            }
            groupInfo.groupOid = group.GetGroupOid();
            groupInfo.groupLeaderOid = group.GetGroupLeaderOid();
            groupInfo.roll = group.GetRoll();
            groupInfo.dice = group.GetDice();
            groupInfo.grade = group.GetGrade();
            
            Collection<AgisGroupMember> groupMembers = group.GetGroupMembers().values();
            for(AgisGroupMember groupMember : groupMembers){
                memberOids.add(groupMember.GetGroupMemberOid());
            }
            groupInfo.memberOidSet = memberOids;
        }
        return groupInfo;
    }

    /**
     * SendTargetedGroupMessage - Handles sending messages to the group com channel
     */
    protected void SendTargetedGroupMessage(OID source, OID target, String message){
    	TargetedComReqMessage comMessage = new TargetedComReqMessage(source,target,null,4,message);
      //  comMessage.setString(message);
      //  comMessage.setChannel(4); //Group channel
      //  comMessage.setTarget(target);
        Engine.getAgent().sendBroadcast(comMessage);
    }

    protected static AgisGroupMember GetGroupMember(OID subjectOid){
    	
        Collection<AgisGroup> groups = _currentGroups.values();
        for(AgisGroup group : groups){
            AgisGroupMember subject = group.GetGroupMember(subjectOid);
            if(subject != null)
                return subject;
        }
        return null;
    }

    /**
     * HandleSetAllowedSpeaker - Used to mark the target as an allowed speaker or not of the group's voice chat.
     *                              If the target is currently an allowed speaker they will in effect become muted
     * @param targetOid - Player to mute or un-mute
     * @param setterOid - Requesting Player
     * @param groupOid - Identifier for the group the target and setter belong to
     */
    protected boolean HandleSetAllowedSpeaker(OID targetOid, OID setterOid, OID groupOid) {
        AgisGroup group = GetGroup(groupOid);
        AgisGroupMember target = group.GetGroupMember(targetOid);

        if(group == null) {
            Log.error("GroupPlugin.HandleSetAllowedSpeaker - Group is null.");
            return false;
        }

        if(target == null){
            Log.error("GroupPlugin.HandleSetAllowedSpeaker - Target is null.");
            return false;
        }

        if(target.GetVoiceEnabled()){
            Map<String, Serializable> statToUpdate = new HashMap<String, Serializable>();
            // If group is muted, then cannot change status unless the setter is the gorup leader
            if(!group.GetGroupMuted() || (setterOid.equals(group.GetGroupLeaderOid()))) {
                target.SetAllowedSpeaker(!target.GetAllowedSpeaker());  
                // Update voice server                
                int result = VoiceClient.setAllowedSpeaker(groupOid, targetOid, target.GetAllowedSpeaker());
                if(result != VoiceClient.SUCCESS)
                    Log.error("GroupPlugin.HandleSetAllowedSpeaker : Create Voice Group Response - " + VoiceClient.errorString(result));
            }
            // Send voice status to all group members
            statToUpdate.put("allowedSpeaker", target.GetAllowedSpeaker());
            SendGroupPropertyUpdate(targetOid, group, statToUpdate);

        }
        return true;
    }
    
    /**
     * HandleMuteGroup - Allows group leader to mute or un-mute the group's voice chat
     * @param setterOid
     * @param groupOid
     */
    protected boolean HandleMuteGroup(OID setterOid, OID groupOid){
        AgisGroup group = GetGroup(groupOid);
        
        if(group == null){
            Log.error("GroupPlugin.HandleMuteGroup - Group is null.");
            return false;
        }

        // Only the group leader should be able to mute the group
        if(setterOid.equals(group.GetGroupLeaderOid())){
            group.SetGroupMuted(!group.GetGroupMuted());

            Collection<AgisGroupMember> groupMembers = group.GetGroupMembers().values();

            // Mute each player in the group except for group leader
            for(AgisGroupMember groupMember : groupMembers){
                if(groupMember.GetVoiceEnabled() && !groupMember.GetGroupMemberOid().equals(group.GetGroupLeaderOid())) {
                    groupMember.SetAllowedSpeaker(!group.GetGroupMuted());
                    // Call voice server to mute player
                    VoiceClient.setAllowedSpeaker(groupOid, groupMember.GetGroupMemberOid(), !group.GetGroupMuted());
                    // Update group members about player voice status
                    Map<String, Serializable> statToUpdate = new HashMap<String, Serializable>();
                    statToUpdate.put("allowedSpeaker", !group.GetGroupMuted());
                    statToUpdate.put("groupMuted", group.GetGroupMuted());
                    SendGroupPropertyUpdate(groupMember.GetGroupMemberOid(), group, statToUpdate);
                }
            }
            GroupClient.GroupEventType eventType = GroupClient.GroupEventType.MUTED;
            if (!group.GetGroupMuted())
                eventType = GroupClient.GroupEventType.UNMUTED;
            GroupClient.SendGroupEventMessage(eventType, group, setterOid);
        }

        return true;
    }

    /**
     * HandledVoiceStatus - Logic to handle ao.VOICE_CHAT_STATUS message.
     *                          Updates group member's voiceEnabled property and
     *                          broadcasts update to the other group members
     * @param playerOid - Player being updated
     * @param groupOid - Group being referenced
     * @param voiceEnabled - Value to determine if the player's voice is enabled on their client (Voice enabled and join Party enabled)
     */
    protected boolean HandledVoiceStatus(OID playerOid, OID groupOid, Boolean voiceEnabled){
        AgisGroup group = GetGroup(groupOid);        

        if(group == null){
            Log.error("GroupPlugin.HandledVoiceStatus - Group is null.");
            return false;
        }

        AgisGroupMember player = group.GetGroupMember(playerOid);

        if(player == null){
            Log.error("GroupPlugin.HandledVoiceStatus - Player is null.");
            return false; 
        }

        player.SetVoiceEnabled(voiceEnabled);
        Map<String, Serializable> statToUpdate = new HashMap<String, Serializable>();
        statToUpdate.put("voiceEnabled", voiceEnabled);
        SendGroupPropertyUpdate(playerOid, group, statToUpdate);

        return true;
    }

    /**
     * HandleVoiceMemberAdded - Handles logic for processing the VoiceClient.MSG_TYPE_VOICE_MEMBER_ADDED
     *  message type. Update any group or group member information related to a player joining the voice group
     *  that is associated with a corresponding AgisGroup object.
     * @param memberOid
     * @param groupOid
     */
    protected boolean HandleVoiceMemberAdded(OID memberOid, OID groupOid){
		if(Log.loggingDebug)  _log.debug("GroupPlugin.HandleVoiceMemberAdded - Got member added message");
        // We only want to process groupOids that matches an OID in our current groups list
        if(_currentGroups.containsKey(groupOid)){
            _log.debug("GroupPlugin.HandleVoiceMemberAdded - Got member match");
            AgisGroup group = _currentGroups.get(groupOid);
            AgisGroupMember groupMember = group.GetGroupMember(memberOid);
            if(groupMember != null){
                Map<String, Serializable> statsToUpdate = new HashMap<String, Serializable>();
                //If the group is currently in a muted state, then we need to mute the new member
                if(group.GetGroupMuted()){
                    groupMember.SetAllowedSpeaker(Boolean.FALSE);                
                    statsToUpdate.put("allowedSpeaker", Boolean.FALSE);                
                }

                //If the group member is flagged to indicate their voice is disabled, then enable it
                if(!groupMember.GetVoiceEnabled()){
                    groupMember.SetVoiceEnabled(Boolean.TRUE);
                    statsToUpdate.put("voiceEnabled", Boolean.FALSE);
                }

                if(statsToUpdate.size() > 0)
                    SendGroupPropertyUpdate(memberOid, group, statsToUpdate);
            }
            else
                _log.error("GroupPlugin.HandleVoiceMemberAdded - Player with OID " + memberOid.toString() + 
                           " is not a member of the group with OID " + groupOid.toString());
        }
        return true;
    }
    
    /**
     * Creates a new group if the inviter is not currently grouped
     */
	protected boolean HandleCreateGroup(GroupClient.createGroupMessage createMsg) {
		ArrayList<OID> groupMemberOids = (ArrayList<OID>) createMsg.getProperty("groupMembers");
		//ArrayList<CombatInfo> groupMembers = new ArrayList<CombatInfo>();
		if (groupMemberOids.size() < 2)
			return false;

		AgisGroup group = null;// Create a new group
		group = new AgisGroup();
		//AgisGroupMember groupLeader = group.AddGroupMember(groupMembers.get(0));
		AgisGroupMember groupLeader = group.AddGroupMember(groupMemberOids.get(0));
			groupLeader.SetVoiceEnabled(false); // TODO: Figure out how to get leader's setting
		group.SetNoleader(true);
		//group.SetGroupLeaderOid(groupMemberOids.get(0));
		//for (OID memberOid : groupMemberOids) {
		//	groupMembers.add(CombatPlugin.getCombatInfo(memberOid));
		//}
		HashMap<String, Serializable> sobjectParams = new HashMap<String, Serializable>();
		sobjectParams.put("setPendingGroupInvite", false);
		for (int i = 1; i < groupMemberOids.size(); i++) {
			AgisGroupMember groupMember = group.AddGroupMember(groupMemberOids.get(i));
			groupMember.SetVoiceEnabled(false);
		    CombatClient.setCombatInfoParams(groupMemberOids.get(i),sobjectParams);
		//	groupMembers.get(i).setPendingGroupInvite(false);
		}
		_currentGroups.put(group.GetGroupOid(), group); // Add to our list to track
		if(Log.loggingDebug)Log.debug("GROUP: added group to currentGroups 2. Num groups: " + _currentGroups.size());
		// Send clients info about the group and group members
		SendGroupUpdate(group);

		return true;
	}
    
    /*
     * Hooks
     */     

	
	  /**
     * GroupInviteResponseHook Adds a player to a group, or creates a new group
     * and sends out group info to the clients
     */
	class GroupSettingsHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage setMsg = (ExtensionMessage) msg;
			OID oid = setMsg.getSubject();
			if(Log.loggingDebug) Log.debug("GroupSettingsHook oid="+oid);
			AgisGroupMember member = GetGroupMember(oid);
			if (member == null) {
				return true;
			}
			AgisGroup group = GetGroup(member.GetGroupOid());
			if (group.GetGroupLeaderOid().equals(oid)) {
				if (setMsg.getPropertyMapRef().containsKey("roll")) {
					group.SetRoll((int)setMsg.getProperty("roll"));
					group.SetDice((int)setMsg.getProperty("dice"));
					group.SetGrade((int)setMsg.getProperty("grade"));
					if(Log.loggingDebug) Log.debug("GroupSettingsHook: Settup Roll="+group.GetRoll()+" dice="+group.GetDice()+" grade="+group.GetGrade());
					SendGroupUpdate(group);        
				}
			} else {
				Log.debug("Only Leader of party can change loot settings");
			}
			if(Log.loggingDebug) Log.debug("GroupSettingsHook oid="+oid+" END");
			return true;
		}
	}

	
	
	
	
    /**
     * GroupGetMembersHook return group members for player if is leader
     */
 
    
	class GroupGetMembersHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			GroupClient.GetGroupMembersMessage getMsg = (GroupClient.GetGroupMembersMessage) msg;
			OID oid = getMsg.getSubject();
			//CombatInfo player = CombatPlugin.getCombatInfo(oid);
			LinkedList<String> param = new LinkedList<String>();
			param.add("groupOid");
			HashMap<String, Serializable> objectParams = CombatClient.getCombatInfoParams(oid, param);


			if(Log.loggingDebug)Log.debug("Group: Get members for oid=" + oid + " OidIsLeader=" + getMsg.getOidIsLeader());
			// String storageName = getMsg.getStorageName();
			LinkedList<OID> members = new LinkedList<OID>();
			OID groupOid = null;
			if(objectParams.containsKey("groupOid"))
				groupOid = (OID)objectParams.get("groupOid");
			if(Log.loggingDebug)Log.debug("Group: Get members for oid=" + oid + " groupOid=" + groupOid);
			if (groupOid != null) {
				AgisGroup group = GroupPlugin.GetGroup(groupOid);
				if(Log.loggingDebug)	Log.debug("Group: Get members for oid=" + oid + " group=" + group);
				if (group != null) {
					if ((group.GetGroupLeaderOid().equals(oid) && getMsg.getOidIsLeader()) || !getMsg.getOidIsLeader()) {
						Hashtable<OID, AgisGroupMember> groupMembers = group.GetGroupMembers();
						Set<OID> groupMemberKeys = new TreeSet<OID>(groupMembers.keySet());
						for (OID groupMemberKey : groupMemberKeys) {
							// Log.error("GroupPlugin: member "+groupMemberKey+"|"+groupMembers.get(groupMemberKey).GetGroupMemberOid());
							members.add(groupMembers.get(groupMemberKey).GetGroupMemberOid());
							// groupMembers.get(groupMemberKey).get
						}
					}
				}else {
					if(Log.loggingDebug)Log.debug("Group: Get members for oid=" + oid + " group=" + group+" is null");
				}
			}
			if(Log.loggingDebug)Log.debug("Group: Get members for oid=" + oid + " members=" + members + " count=" + members.size());

			Engine.getAgent().sendObjectResponse(msg, members);
			return true;
		}
	}
    
    
    /**
     * PropertyHook catches any property updates. We only want to process
     * entities that are flagged as grouped
     */
    class PropertyHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            PropertyMessage propMsg = (PropertyMessage) msg;
            return UpdateGroupMemberProps(propMsg);
        }
    }
    
    
    
    

    /**
     * GroupInviteResponseHook Adds a player to a group, or creates a new group
     * and sends out group info to the clients
     */
    class GroupInviteResponseHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage inviteMsg = (ExtensionMessage) msg;
            return HandleInviteResponse(inviteMsg);
        }
    }

    /**
     * GroupRemoveMemberHook is used to remove the group member in question.
     * 
     */
    class GroupRemoveMemberHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage removeMemberMsg = (ExtensionMessage) msg;
			if(Log.loggingDebug)  Log.debug("GROUP: removing member: " +removeMemberMsg.getProperty("target")+" subject="+removeMemberMsg.getSubject());
            //   CombatInfo subject = CombatPlugin.getCombatInfo((OID) removeMemberMsg.getProperty("target"));
        	LinkedList<String> param = new LinkedList<String>();
			param.add("groupOid");
			HashMap<String, Serializable> objectParams = new  HashMap<String, Serializable> ();
			 try {
				objectParams =	CombatClient.getCombatInfoParams((OID) removeMemberMsg.getProperty("target"), param);
			} catch (Exception e) {
				  Log.debug("GROUP: removing member is offline not found CombatInfo"); 
			}

			if(Log.loggingDebug)  Log.debug("GROUP: removing member: " + objectParams);
            if (!objectParams.containsKey("subject")) {
            	OID groupOid = null;
            	if (removeMemberMsg.getProperty("group") != null)
            		groupOid = (OID) removeMemberMsg.getProperty("group");
    		
            	// Kicking an offline player?
            	removeOfflineMember((OID) removeMemberMsg.getProperty("target"), removeMemberMsg.getSubject(),groupOid);
				if(Log.loggingDebug)Log.debug("GROUP: removing member: " +removeMemberMsg.getProperty("target")+" END 1");
                return true;
            }

            RemoveGroupMember((OID) removeMemberMsg.getProperty("target"), true, removeMemberMsg.getSubject(), true);
			if(Log.loggingDebug)Log.debug("GROUP: removing member: " +removeMemberMsg.getProperty("target")+" END 2");
            return true;
        }
    }

    /**
     * Handles group chat messages from the client
     */
    class GroupChatHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage groupChatMsg = (ExtensionMessage) msg;
            HandleGroupChat(groupChatMsg);
            return true;
        }
    }

    /**
     * LoginHook is used to store the names of users currently logged in. It needs to be moved.
     * @author Andrew
     *
     */
    class LoginHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LoginMessage message = (LoginMessage) msg;
          //  CombatInfo subject = CombatPlugin.getCombatInfo(message.getSubject());
            //String playerName = WorldManagerClient.getObjectInfo(playerOid).name;
            //playerNamesOids.put(playerName, playerOid);
        	LinkedList<String> param = new LinkedList<String>();
    		param.add("isGrouped");
    		param.add("groupOid");
    		HashMap<String, Serializable> objectParams = CombatClient.getCombatInfoParams(message.getSubject(), param);  
    	   if (objectParams.containsKey("subject")  && (boolean)objectParams.get("isGrouped")) {
    			OID groupOid = (OID)objectParams.get("groupOid");
			   if(Log.loggingDebug)Log.debug("GROUP: on login groupOid = " + groupOid);
             	SetMemberOnline(/*subject*/message.getSubject());
            }

            Engine.getAgent().sendResponse(new ResponseMessage(message));
            return true;
        }
    }

    class SpawnedHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;
			OID objOid = spawnedMsg.getSubject();
		//	Log.error("Group: SpawnedHook: objOid="+objOid+" type"+spawnedMsg.getType()+" instance="+spawnedMsg.getInstanceOid()+" isPlayer="+spawnedMsg.getType().isPlayer());
			if (!spawnedMsg.getType().isPlayer()) {
				if(Log.loggingDebug)Log.debug("Group: SpawnedHook: objOid="+objOid+" type"+spawnedMsg.getType()+" is mob break");
				return true;
				 
			}
			//CombatInfo subject = CombatPlugin.getCombatInfo(objOid);
		 	LinkedList<String> param = new LinkedList<String>();
    		param.add("isGrouped");
    		param.add("groupOid");
    		HashMap<String, Serializable> objectParams = CombatClient.getCombatInfoParams(objOid, param);  
      //String playerName = WorldManagerClient.getObjectInfo(playerOid).name;
            //playerNamesOids.put(playerName, playerOid);
			if(Log.loggingDebug)Log.debug("GROUP: on spawned objOid="+objOid+" "+objectParams);
			if (objectParams.containsKey("subject")  ) {
				OID groupOid =  (OID)objectParams.get("groupOid");//subject.getGroupOid();
				if(Log.loggingDebug)Log.debug("GROUP: on spawned objOid="+objOid+"  groupOid = " + groupOid);
				if(groupOid!=null) {
					AgisGroup group = GetGroup(groupOid);
					if(group==null) {
						if(Log.loggingDebug)Log.debug("GROUP: on spawned groupOid = " + groupOid+" group no found reset group params on combat info");
					//	subject.setGroupOid(null);
					//	subject.setGroupMemberOid(null);
						HashMap<String, Serializable> sobjectParams = new HashMap<String, Serializable>();
						sobjectParams.put("groupMemberOid", null);
						sobjectParams.put("groupOid", null);
		                CombatClient.setCombatInfoParams(objOid,sobjectParams);
		                }
				}
				if (/*subject.isGrouped()*/(boolean)objectParams.get("isGrouped")) {
					SetMemberOnline(/*subject*/objOid);
				}
			}
			return true;
		}
	}

    /**
     * LogOutHook is used to remove group members from a group who log out of the game.
     */
    class LogOutHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LogoutMessage logoutMsg = (LogoutMessage) msg;
			OID playerOid = logoutMsg.getSubject();
			try {
				if(Log.loggingDebug)  Log.debug("LOGOUT: group logout started for: " + playerOid);
          //  CombatInfo subject = CombatPlugin.getCombatInfo(logoutMsg.getSubject());
         	/*LinkedList<String> param = new LinkedList<String>();
    		param.add("isGrouped");
    		param.add("groupOid");
    		HashMap<String, Serializable> objectParams = CombatClient.getCombatInfoParams(logoutMsg.getSubject(), param);  */

				AgisGroupMember agm = GetGroupMember(logoutMsg.getSubject());
				if (agm != null) {
					SetMemberOffline(logoutMsg.getSubject());
				}
            //String playerName = WorldManagerClient.getObjectInfo(playerOid).name;
            //playerNamesOids.remove(playerName);

            // If the player logging out is grouped, then remove them from their group
         /*   if (objectParams.containsKey("subject") && (boolean)objectParams.get("isGrouped")) {
                //RemoveGroupMember(subject, false, null, false);
            	SetMemberOffline(logoutMsg.getSubject());
            }*/
			}catch (Exception e ){
				Log.exception("Group Logout ",e);
			}

			Engine.getAgent().sendResponse(new ResponseMessage(logoutMsg));
			if(Log.loggingDebug) Log.debug("LOGOUT: group logout finished for: " + playerOid);
            return true;
        }
    }

    /*
     * GroupInviteHook handles invitee response
     */
    class GroupInviteHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage inviteMsg = (ExtensionMessage) msg;
            OID inviterOid = inviteMsg.getSubject();
            OID inviteeOid = (OID) inviteMsg.getProperty("target");
            AccountDatabase aDB = new AccountDatabase(false);
        	boolean isOnBlockList = aDB.isOnBlackList(inviterOid, inviteeOid);
			if (isOnBlockList) {
			     String targetName = aDB.getCharacterNameByOid(inviteeOid);
			     //  String targetName = WorldManagerClient.getObjectInfo(inviteeOid).name;
				EventMessageHelper.SendErrorEvent(inviterOid, EventMessageHelper.ERROR_PLAYER_ON_BLOCK_LIST, 0, targetName);
				   aDB.close();
				   return false;
			}else {
				isOnBlockList = aDB.isOnBlackList(inviteeOid, inviterOid);
				if (isOnBlockList) {
					 String targetName = aDB.getCharacterNameByOid(inviteeOid);
					 //   String targetName = WorldManagerClient.getObjectInfo(inviteeOid).name;
					EventMessageHelper.SendErrorEvent(inviterOid, EventMessageHelper.ERROR_PLAYER_ON_YOUR_BLOCK_LIST, 0, targetName);
					 aDB.close();
					   return false;
				}
			}
            //aDB.close();
            return HandleGroupInvite(inviterOid, inviteeOid);
        }
    }
    
    /*
     * GroupInviteByNameHook handles invitee response
     */
    class GroupInviteByNameHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            GroupClient.groupInviteByNameMessage inviteMsg = (GroupClient.groupInviteByNameMessage) msg;
            OID inviterOid = (OID) inviteMsg.getProperty("inviterOid");
            String inviteeName = (String) inviteMsg.getProperty("inviteeName");
            // Get the targets oid
			try {
				OID inviteeOid = GroupClient.getPlayerByName(inviteeName);
				if (inviteeOid != null) {
	            	//OID inviteeOid = playerNamesOids.get(inviteeName);
	            	HandleGroupInvite(inviterOid, inviteeOid);
					if(Log.loggingDebug)Log.debug("GROUP: invited player oid: " + inviteeOid);
	            } else {
	            	ExtendedCombatMessages.sendErrorMessage(inviterOid, "Player " + inviteeName + " could not be found.");
	            }
			} catch (IOException e) {
			}
            
            return true;
        }
    }
    
    class GroupLeaveHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage inviteMsg = (ExtensionMessage) msg;
            OID leaverOid = inviteMsg.getSubject();
          //  CombatInfo subject = CombatPlugin.getCombatInfo(leaverOid);
         	LinkedList<String> param = new LinkedList<String>();
    		param.add("isGrouped");
    		HashMap<String, Serializable> objectParams = CombatClient.getCombatInfoParams(leaverOid, param);  
            if (!objectParams.containsKey("subject"))
                return false;

            RemoveGroupMember(leaverOid, false, null, true);
            return true;
        }
    }
    

    /**
     * RequestGroupInfoHook handles group info requests. Returns information about the group and who is in it
     * Message is intend for server to server comm.
     */
    class RequestGroupInfoHook implements Hook{
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage requestGroupInfoMsg = (ExtensionMessage) msg;
           // CombatInfo subject = CombatPlugin.getCombatInfo(requestGroupInfoMsg.getSubject());
         	LinkedList<String> param = new LinkedList<String>();
    		param.add("isGrouped");
    		try {
        		HashMap<String, Serializable> objectParams = CombatClient.getCombatInfoParams(requestGroupInfoMsg.getSubject(), param);  
        		if(!objectParams.containsKey("subject")) {
                	Engine.getAgent().sendObjectResponse(msg, new GroupClient.GroupInfo());
                    return true;
                }
    		} catch (NoRecipientsException e) {
                Engine.getAgent().sendObjectResponse(msg, new GroupClient.GroupInfo());
                return true;
    		}
            //OID sOid = subject.getOwnerOid();
            //Lock lock = getObjectLockManager().getLock(sOid);
            //lock.lock();

            //try {
                GroupClient.GroupInfo groupInfo = HandleGroupInfoRequest(/*subject*/requestGroupInfoMsg.getSubject());
                Engine.getAgent().sendObjectResponse(msg, groupInfo);
            //} finally {
            //    lock.unlock();
            //}
            return true;
        }
    }
    
    /**
     * SetAllowedSpeakerInfoHook - used to set a whether the group member is allowed to talk in voice chat
     * 
     */
    class SetAllowedSpeakerHook implements Hook{
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage setAllowedSpeakerMsg = (ExtensionMessage)msg;

            OID targetOid = (OID)setAllowedSpeakerMsg.getProperty("target");
            OID setterOid = (OID)setAllowedSpeakerMsg.getProperty("setter");
            OID groupOid = (OID)setAllowedSpeakerMsg.getProperty("groupOid");

            return HandleSetAllowedSpeaker(targetOid, setterOid, groupOid);
        }
    }

    /**
     * MuteGrouphook - Used to mute or un-mute the group voice chat
     *                  If muting, only the group leader may talk
     */
    class MuteGroupHook implements Hook{
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage setAllowedSpeakerMsg = (ExtensionMessage)msg;

            OID setterOid = (OID)setAllowedSpeakerMsg.getProperty("setter");
            OID groupOid = (OID)setAllowedSpeakerMsg.getProperty("groupOid");
            return HandleMuteGroup(setterOid, groupOid);
        }
    }

    /**
     * VoiceStatusHook - Handles updates that determine if the player in a group has their voice
     *  configuration set to be enabled and to join group chat.
     *  Updates AgisGroupMember._voiceEnabled and broadcasts that to the rest of the group.
     */
    class VoiceStatusHook implements Hook{
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage voiceStatusMsg = (ExtensionMessage)msg;

            OID playerOid = (OID)voiceStatusMsg.getProperty("playerOid");
            OID groupOid = (OID)voiceStatusMsg.getProperty("groupOid");
            Boolean voiceEnabled = (Boolean)voiceStatusMsg.getProperty("voiceEnabled");

            return HandledVoiceStatus(playerOid, groupOid, voiceEnabled);
        }
    }

    /**
     * VoiceMemberAddedHook - Handles messages from the VoiceServer that 
     *  a new player was added to a voice group.
     */
    class VoiceMemberAddedHook implements Hook{
        public boolean processMessage (Message msg, int flags){
            ExtensionMessage voiceMemberAddedMsg = (ExtensionMessage)msg;
            OID memberOid = (OID)voiceMemberAddedMsg.getProperty("memberOid");
            OID groupOid = (OID)voiceMemberAddedMsg.getProperty("groupOid");
            return HandleVoiceMemberAdded(memberOid, groupOid);
        }
    }
    
    /**
     * CreateGroupHook creates a new group and sends out group info to the clients
     */
    class CreateGroupHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            GroupClient.createGroupMessage createMsg = (GroupClient.createGroupMessage) msg;
            return HandleCreateGroup(createMsg);
        }
    }
    
    /**
     * VoiceMemberAddedHook - Handles messages from the VoiceServer that 
     *  a new player was added to a voice group.
     */
    class PromoteToLeaderHook implements Hook{
        public boolean processMessage (Message msg, int flags){
        	ExtensionMessage promoteMsg = (ExtensionMessage)msg;
        	Log.debug("GROUP: got promote message");
        //	CombatInfo subject = CombatPlugin.getCombatInfo((OID) promoteMsg.getProperty("target"));
        //    if (subject == null)
            	LinkedList<String> param = new LinkedList<String>();
    		param.add("isGrouped");
    		HashMap<String, Serializable> objectParams = CombatClient.getCombatInfoParams((OID) promoteMsg.getProperty("target"), param);  
    		if(!objectParams.containsKey("subject")) {
              return false;
    		}
            PromoteMemberToLeader(/*subject*/(OID) promoteMsg.getProperty("target"), promoteMsg.getSubject());
            return true;
        }
    }
    
    protected void sendMembersUpdate(OID groupId) {
    	  AgisGroup group = GetGroup(groupId);
    	  LinkedList<OID> members = new LinkedList<OID>();
		  if(group!=null)
          for(OID member : group.GetGroupMembers().keySet()) {
        	  members.add(group.GetGroupMembers().get(member).GetGroupMemberOid());
          }
    	GroupClient.GroupMembersMessage gmm = new GroupClient.GroupMembersMessage(groupId, members);
    	Engine.getAgent().sendBroadcast(gmm);
    	
    }
    AccountDatabase aDB;
    public static int GROUP_DISCONNECT_TIMEOUT = 30; // How many seconds a player can be disconnected for before they are kicked out of a group
    public static int GROUP_INVITE_TIMEOUT =30;// How many seconds invite cancel 
    public static int GROUP_LOOT_DEFAULT_ROLL = 0;// 0-FreeForAll 1-Random 2-Leader 
    public static int GROUP_LOOT_DEFAULT_DICE = 0;// 0-Normal 1-Dice 
    public static int GROUP_LOOT_DEFAULT_GRADE = 3;// min ItemQuality 
    
    
    
    
}
