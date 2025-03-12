package atavism.agis.plugins;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import atavism.agis.objects.AgisGroup;
import atavism.agis.objects.AgisGroupMember;
import atavism.agis.plugins.ChatClient.TargetedComReqMessage;
import atavism.msgsys.GenericMessage;
import atavism.msgsys.MessageAgent;
import atavism.msgsys.MessageType;
import atavism.msgsys.SubjectMessage;
import atavism.server.engine.Engine;
import atavism.server.engine.OID;
import atavism.server.messages.PropertyMessage;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.util.Log;

public class GroupClient {
    //properties
    public static final String EXTMSG_GROUP_UPDATE = "ao.GROUP_UPDATE";
    public static final String EXTMSG_GROUP_PROPERTY_UPDATE = "ao.GROUP_PROPERTY_UPDATE";
    public static final String EXTMSG_GROUP_INVITE_REQUEST = "ao.GROUP_INVITE_REQUEST";
    public static final String EXTMSG_GROUP_CANCEL_INVITE_REQUEST = "ao.GROUP_CANCEL_INVITE_REQUEST";
     public static final String EXTMSG_GROUP_INVITE_DECLINED = "ao.GROUP_INVITE_DECLINED";
    public static final MessageType MSG_TYPE_GROUP_INVITE = MessageType.intern("ao.GROUP_INVITE");
    public static final MessageType MSG_TYPE_GROUP_INVITE_RESPONSE = MessageType.intern("ao.GROUP_INVITE_RESPONSE");
    public static final MessageType MSG_TYPE_GROUP_LEAVE = MessageType.intern("ao.GROUP_LEAVE");
    public static final MessageType MSG_TYPE_GROUP_REMOVE_MEMBER = MessageType.intern("ao.GROUP_REMOVE_MEMBER");
    public static final MessageType MSG_TYPE_GROUP_CHAT = MessageType.intern("ao.GROUP_CHAT");
    public static final MessageType MSG_TYPE_REQUEST_GROUP_INFO = MessageType.intern("ao.REQUEST_GROUP_INFO");
    public static final MessageType MSG_TYPE_GROUP_INFO_RESPONSE = MessageType.intern("ao.GROUP_INFO_RESPONSE");
    public static final MessageType MSG_TYPE_GROUP_SET_ALLOWED_SPEAKER = MessageType.intern("ao.GROUP_SET_ALLOWED_SPEAKER");
    public static final MessageType MSG_TYPE_GROUP_MUTE_VOICE_CHAT = MessageType.intern("ao.GROUP_MUTE_VOICE_CHAT");
    public static final MessageType MSG_TYPE_GROUP_VOICE_CHAT_STATUS = MessageType.intern("ao.GROUP_VOICE_CHAT_STATUS");
    public static final MessageType MSG_TYPE_GROUP_PROMOTE_LEADER = MessageType.intern("ao.GROUP_PROMOTE_LEADER");
    // IOW MessageTypes
    public static final MessageType MSG_TYPE_GROUP_INVITE_BY_NAME = MessageType.intern("iow.GROUP_INVITE_BY_NAME");
    public static final MessageType MSG_TYPE_GET_PLAYER_BY_NAME = MessageType.intern("iow.GET_PLAYER_BY_NAME");
    public static final MessageType MSG_TYPE_CREATE_GROUP = MessageType.intern("iow.CREATE_GROUP");
    
    
    public static final MessageType MSG_TYPE_GROUP_SETTINGS = MessageType.intern("iow.GROUP_SETTINGS");
    public static final String EXTMSG_GROUP_SEND_SETTINGS = "ao.GROUP_SEND_SETTINGS";
    
    
    // Shouldn't be here, but they are for now
    public static final MessageType MSG_TYPE_GET_FRIENDS = MessageType.intern("ao.GET_FRIENDS");
    public static final MessageType MSG_TYPE_ADD_FRIEND = MessageType.intern("ao.ADD_FRIEND");
    
    public static final MessageType MSG_TYPE_SOCIAL_ADD_FRIEND = MessageType.intern("social.ADD_FRIEND");
	public static final MessageType MSG_TYPE_SOCIAL_DEL_FRIEND = MessageType.intern("social.DEL_FRIEND");
	public static final MessageType MSG_TYPE_SOCIAL_GET_FRIENDS = MessageType.intern("social.GET_FRIENDS");
	public static final MessageType MSG_TYPE_ADD_BLOCK = MessageType.intern("social.ADD_BLOCK");
	public static final MessageType MSG_TYPE_DEL_BLOCK = MessageType.intern("social.DEL_BLOCK");
	 public static final MessageType MSG_TYPE_BLOCK_LIST = MessageType.intern("ao.BLOCKLIST");
	 public static final MessageType MSG_TYPE_IS_ON_BLOCK_LIST =MessageType.intern("ao.IS_ON_BLOCK_LIST");
	 public static final MessageType MSG_SOCIAL_INVITE_RESPONSE = MessageType.intern("social.INVITE_RESPONSE");
	 	//constructors
    public GroupClient(){}

    /*
     * Methods
     */

    /**
     * SendGroupEventMessage is used to send messages to each group member about specific group releated events
     * @param eventType Type of event to send to the group members
     * @param group Group object for which the event pertains
     * @param subjectOid Oid of the player/object that the message is about
     */
    public static void SendGroupEventMessage(GroupEventType eventType, AgisGroup group, OID subjectOid) {
        //Set Message to send to each group member
        AgisGroupMember subject = group.GetGroupMember(subjectOid);
        if(subject != null){
            String message = subject.GetGroupMemberName();
            switch(eventType){
            case JOINED:
                message += " has joined the group.";
                break;
            case LEFT:
                message += " has left the group.";
                break;
            case DISBANDED:
                message += " has disbanded the group";
                break;
            case LEADERCHANGED:
                message += " is now the group leader.";
                break;
            case MUTED:                
                message += " has muted the group.";
                break;
            case UNMUTED:
                message += " has un-muted the group.";
                break;
            }

            MessageAgent agent = Engine.getAgent();
            TargetedComReqMessage groupEventMessage = new TargetedComReqMessage();
            groupEventMessage.setString(message);
            groupEventMessage.setChannel(4); //Group channel
            Collection<AgisGroupMember> groupMembers = group.GetGroupMembers().values();
            for (AgisGroupMember groupMember : groupMembers) {
                if (!groupMember.GetGroupMemberOid().equals(subjectOid) || eventType.equals(GroupEventType.LEADERCHANGED)) {
                    groupEventMessage.setSubject(groupMember.GetGroupMemberOid());
                    groupEventMessage.setTarget(groupMember.GetGroupMemberOid());
                    agent.sendBroadcast(groupEventMessage);
                }
            }
        } else {
			Log.debug("GroupClient.SendGroupEventMessage - AgisGroup.GetGroupMember(" + subjectOid + ") returned null object");
	     }
    }

    /**
     * Sends an RPC message to the GroupPlugin and returns a list of group member OIDs.
     * @param subject Oid of the player/object assoicated with the group you want info about
     * 
     */
	public static GroupInfo GetGroupMemberOIDs(OID subject) {
		ExtensionMessage groupInfoRequest = new ExtensionMessage(GroupClient.MSG_TYPE_REQUEST_GROUP_INFO, "ao.REQUEST_GROUP_INFO", subject);

		Object groupInfo = Engine.getAgent().sendRPCReturnObject(groupInfoRequest);
		if (Log.loggingDebug)
			Log.debug("GroupClient.GetGroupMemberOIDs - Received group info - " + groupInfo.toString());
		return (GroupInfo) groupInfo;
	}
    
    public static class GroupInfo implements Serializable {
		public OID groupOid = null;
        public OID groupLeaderOid = null;
        public HashSet<OID> memberOidSet = new HashSet<OID>();
        public int roll=GroupPlugin.GROUP_LOOT_DEFAULT_ROLL;
        public int dice=GroupPlugin.GROUP_LOOT_DEFAULT_DICE;
        public int grade=GroupPlugin.GROUP_LOOT_DEFAULT_GRADE;
        
        
		private static final long serialVersionUID = 1L;
    }
    
    /**
	 * Sends the groupInviteByNameMessage which will attempt to invite the named member to the group.
	 */
	public static void groupInviteByName(OID oid, String name) throws IOException {
		groupInviteByNameMessage msg = new groupInviteByNameMessage(oid, name);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("GROUP CLIENT: groupInviteByNameMessage hit 2");
	}

	public static class groupInviteByNameMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
        public groupInviteByNameMessage() {
            super();
            Log.debug("GROUP CLIENT: groupInviteByNameMessage hit 1");
        }
        public groupInviteByNameMessage(OID oid, String name) {
        	super();
        	setMsgType(MSG_TYPE_GROUP_INVITE_BY_NAME);
        	setProperty("inviterOid", oid);
        	setProperty("inviteeName", name);
        	Log.debug("GROUP CLIENT: groupInviteByNameMessage hit 1");
        }
	}
	
	/**
	 * Sends the getPlayerByNameMessage which will attempt to get a players oid from their name.
	 */
	public static OID getPlayerByName(String name) throws IOException {
		//getPlayerByNameMessage msg = new getPlayerByNameMessage(name);
		// Engine.getAgent().sendBroadcast(msg);
		Log.debug("GROUP CLIENT: getPlayerByNameMessage hit 1");
		//return Engine.getAgent().sendRPCReturnOID(msg);
		 return Engine.getDatabase().getOidByName(name, WorldManagerClient.NAMESPACE);
	}

	public static class getPlayerByNameMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;

		public getPlayerByNameMessage() {
			super();
			Log.debug("GROUP CLIENT: getPlayerByNameMessage hit 1");
		}

		public getPlayerByNameMessage(String name) {
			super();
			setMsgType(MSG_TYPE_GET_PLAYER_BY_NAME);
			setProperty("inviteeName", name);
			Log.debug("GROUP CLIENT: getPlayerByNameMessage hit 1 " + this);
		}
	}
    /**
     * Sends an RPC message to the GroupPlugin to remove member from group. 
     * @param oid 
     */
	public static void removeMember(OID oid) {
		Map<String, Serializable> propertyMap = new HashMap<String, Serializable>();
		propertyMap.put("target", oid);
		propertyMap.put("group", null);
		ExtensionMessage msg = new ExtensionMessage(GroupClient.MSG_TYPE_GROUP_REMOVE_MEMBER, oid, propertyMap);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("GROUP CLIENT: removeMember hit 2");
	}
    /**
     * Sends an RPC message to the GroupPlugin to remove member from group.
     * @param oid
     * @param kickerOid
     */
	public static void removeMember(OID oid, OID kickerOid) {
		Map<String, Serializable> propertyMap = new HashMap<String, Serializable>();
		propertyMap.put("target", oid);
		propertyMap.put("group", null);
		ExtensionMessage msg = new ExtensionMessage(GroupClient.MSG_TYPE_GROUP_REMOVE_MEMBER, oid, propertyMap);
		msg.setSubject(kickerOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("GROUP CLIENT: removeMember hit 2");
	}
    /**
     * Sends an RPC message to the GroupPlugin to remove member from group.
     * @param oid
     * @param kickerOid
     * @param groupOid
     */
	public static void removeMember(OID oid, OID kickerOid, OID groupOid) {
		Log.debug("GROUP CLIENT: removeMember hit 1");
	Map<String, Serializable> propertyMap = new HashMap<String, Serializable>();
		propertyMap.put("target", oid);
		propertyMap.put("group", groupOid);
		ExtensionMessage msg = new ExtensionMessage(GroupClient.MSG_TYPE_GROUP_REMOVE_MEMBER, oid, propertyMap);
		msg.setSubject(kickerOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("GROUP CLIENT: removeMember hit 2");
	}

	/**
 * 
 * @param oid
 * @return
 */
    public static void createGroup(ArrayList<OID> groupMembers) {
    	createGroupMessage msg = new createGroupMessage(groupMembers);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("GROUP CLIENT: createGroupMessage hit 2");
	}

	public static class createGroupMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public createGroupMessage() {
            super();
        }
        
        public createGroupMessage(ArrayList<OID> groupMembers) {
        	super();
        	setMsgType(MSG_TYPE_CREATE_GROUP);
        	setProperty("groupMembers", groupMembers);
        	Log.debug("GROUP CLIENT: createGroupMessage hit 1");
        }
	}

	/*
	 * Enumerations
	 */
	/**
	 * Function return List of group members
	 * 
	 * @param oid
	 * @param oidIsLeader set true to check oid is Group Leader
	 * @return list of the group members
	 */
	public static LinkedList<OID> GetGroupMembers(OID oid, boolean oidIsLeader) {
		GetGroupMembersMessage msg = new GetGroupMembersMessage(oid/* , storageOid */);
		msg.setOidIsLeader(oidIsLeader);
		LinkedList<OID> contents = (LinkedList) Engine.getAgent().sendRPCReturnObject(msg);
		return contents;
	}

	/**
	 * Function return List of group members only if oid is Group Leader
	 * 
	 * @param oid
	 * @return
	 */

	  public static LinkedList<OID> GetGroupMembers(OID oid/*, OID storageOid*/) {
	    	GetGroupMembersMessage msg = new GetGroupMembersMessage(oid/*, storageOid*/);
	    	msg.setOidIsLeader(true);
		    LinkedList<OID> contents = (LinkedList) Engine.getAgent().sendRPCReturnObject(msg);
	    	return contents;
		}
	    
	    public static class GetGroupMembersMessage extends SubjectMessage {
	    	public GetGroupMembersMessage() {
	            super(MSG_TYPE_GET_GROUP_MEMBERS);
	        }
	        
	        public GetGroupMembersMessage(OID playerOid/*, OID storageOid*/) {
	            super(MSG_TYPE_GET_GROUP_MEMBERS, playerOid);
	            //setStorageOid(storageOid);
	        }
	     
	        
	        public boolean getOidIsLeader() {
	            return oidIsLeader;
	        }
	        public void setOidIsLeader(boolean oidIsLeader) {
	            this.oidIsLeader = oidIsLeader;
	        }
	        boolean oidIsLeader;
	        
	      	        
	        private static final long serialVersionUID = 1L;
	    }
	    
	    public static final MessageType MSG_TYPE_GET_GROUP_MEMBERS = MessageType.intern("ao.GET_GROUP_MEMBERS");
	    public static final MessageType MSG_TYPE_GROUP_MEMBERS_UPDATE = MessageType.intern("ao.GROUP_MEMBERS_UPDATE");

	/**
	 * Message to send Update with list of the group members
	 */
	public static class GroupMembersMessage extends GenericMessage {
		public GroupMembersMessage() {
			super(MSG_TYPE_GROUP_MEMBERS_UPDATE);
		}

		public GroupMembersMessage(OID id, LinkedList<OID> list) {
			super(MSG_TYPE_GROUP_MEMBERS_UPDATE);
			setMembers(list);
			setGroupOid(id);
		}

		public void setGroupOid(OID id) {
			this.groupOid = id;
		}

		public OID getGroupOid() {
			return this.groupOid;
		}

		OID groupOid;

		public LinkedList<OID> getMembers() {
			return members;
		}

		public void setMembers(LinkedList<OID> list) {
			this.members = list;
		}

		LinkedList<OID> members;

		private static final long serialVersionUID = 1L;
	}
	
    public enum GroupEventType {
        JOINED, LEFT, DISBANDED, LEADERCHANGED, MUTED, UNMUTED
    }
}
