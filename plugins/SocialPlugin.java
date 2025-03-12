package atavism.agis.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.io.Serializable;

import atavism.agis.database.AccountDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.objects.SocialInfo;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.objects.Entity;
import atavism.server.objects.EntityManager;
import atavism.server.objects.Template;
import atavism.server.plugins.*;
import atavism.server.plugins.InstanceClient.InstanceInfo;
import atavism.server.util.Log;
import atavism.server.util.Logger;

/**
 * handles requests for social states such as friends, guilds etc.
 * 
 * @author AJ
 * 
 */
public class SocialPlugin extends EnginePlugin {
    public SocialPlugin() {
        super("Social");
        setPluginType("Social");
    }

    public void onActivate() {
        registerHooks();
        
        MessageTypeFilter filter = new MessageTypeFilter();
     //   filter.addType(AgisWorldManagerClient.MSG_TYPE_GLOBAL_CHAT);
        filter.addType(WorldManagerClient.MSG_TYPE_DESPAWNED);
        filter.addType(WorldManagerClient.MSG_TYPE_SPAWNED);
        filter.addType(GroupClient.MSG_TYPE_ADD_BLOCK);
        filter.addType(GroupClient.MSG_TYPE_DEL_BLOCK);
       // filter.addType(SocialClient.MSG_TYPE_GET_FRIENDS);
        filter.addType(GroupClient.MSG_TYPE_SOCIAL_ADD_FRIEND);
        filter.addType(GroupClient.MSG_TYPE_SOCIAL_DEL_FRIEND);
        filter.addType(GroupClient.MSG_TYPE_SOCIAL_GET_FRIENDS);
        filter.addType(GroupClient.MSG_SOCIAL_INVITE_RESPONSE);
        filter.addType(SocialClient.MSG_TYPE_SOCIAL_PRIVATE_INVITE);
        filter.addType(SocialClient.MSG_TYPE_SOCIAL_PRIVATE_INVITE_RESPONSE);
          /* Long sub = */ Engine.getAgent().createSubscription(filter, this);

        //MessageTypeFilter filter = new MessageTypeFilter();
        /*filter.addType(QuestClient.MSG_TYPE_NEW_QUESTSTATE);
        filter.addType(QuestClient.MSG_TYPE_GET_QUEST_STATUS);
        filter.addType(QuestClient.MSG_TYPE_CONCLUDE_QUEST);
        filter.addType(QuestClient.MSG_TYPE_QUEST_ITEM_REQS);*/
        //filter.addType(LoginMessage.MSG_TYPE_LOGIN);
        /* Long sub = */ //Engine.getAgent().createSubscription(filter, this,MessageAgent.RESPONDER);

          MessageTypeFilter responderFilter = new MessageTypeFilter();
          responderFilter.addType(LoginMessage.MSG_TYPE_LOGIN);
          responderFilter.addType(LogoutMessage.MSG_TYPE_LOGOUT);
           responderFilter.addType(GroupClient.MSG_TYPE_IS_ON_BLOCK_LIST); 
          Engine.getAgent().createSubscription(responderFilter, this, MessageAgent.RESPONDER);
          
        if (Log.loggingDebug)
            log.debug("SocialPlugin activated");
        
        registerLoadHook(SocialClient.NAMESPACE, new SocialStateLoadHook());
        //registerSaveHook(SocialClient.NAMESPACE, new SocialStateSaveHook());
        registerUnloadHook(SocialClient.NAMESPACE, new SocialStateUnloadHook());
        registerPluginNamespace(SocialClient.NAMESPACE, new SocialSubObjectHook());
        
        aDB = new AccountDatabase(true);
       
        loadData();
    }

    // how to process incoming messages
    protected void registerHooks() {
	//	getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_GLOBAL_CHAT, new InstanceChatHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_DESPAWNED, new DespawnedHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
		getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(GroupClient.MSG_SOCIAL_INVITE_RESPONSE, new SocialInviteResponseHook());
    	getHookManager().addHook(GroupClient.MSG_TYPE_DEL_BLOCK, new DelBlockListHook());
    	getHookManager().addHook(GroupClient.MSG_TYPE_ADD_BLOCK, new AddBlockListHook());
    	getHookManager().addHook(GroupClient.MSG_TYPE_SOCIAL_ADD_FRIEND, new InviteByNameHook());//AddFriendHook());
    //	getHookManager().addHook(GroupClient.MSG_TYPE_SOCIAL_ADD_FRIEND, new AddFriendHook());
        getHookManager().addHook(GroupClient.MSG_TYPE_SOCIAL_DEL_FRIEND, new DelFriendHook());
    	getHookManager().addHook(GroupClient.MSG_TYPE_SOCIAL_GET_FRIENDS, new GetFriendsHook());
    	getHookManager().addHook(SocialClient.MSG_TYPE_SOCIAL_INVITE_BY_NAME, new InviteByNameHook());
    	getHookManager().addHook(SocialClient.MSG_TYPE_SOCIAL_PRIVATE_INVITE, new PrivateInviteHook());
    	getHookManager().addHook(SocialClient.MSG_TYPE_SOCIAL_PRIVATE_INVITE_RESPONSE, new PrivateInviteResponseHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
		 getHookManager().addHook(GroupClient.MSG_TYPE_IS_ON_BLOCK_LIST, new IsOnBlockList());
		 }

	void loadData() {
		ContentDatabase cDB = new ContentDatabase(false);
		cDB.loadEditorOptions();
		String privateInviteTimeout = cDB.loadGameSetting("PRIVATE_INVITE_TIMEOUT");
		if (privateInviteTimeout != null)
			PRIVATE_INVITE_TIMEOUT = Integer.parseInt(privateInviteTimeout);
		log.debug("Game Settings set PRIVATE_INVITE_TIMEOUT=" + PRIVATE_INVITE_TIMEOUT);
	}

	/**
	 * A hook called whenever a ReloadTemplatesMessage is received.
	 */
	class ReloadTemplates implements Hook {

		public boolean processMessage(Message msg, int flags) {
			Log.error("ReloadTemplatesHook Start");
			loadData();
			Log.error("ReloadTemplatesHook End");
			return true;
		}
	}
    
  
    public static SocialInfo getSocialInfo(OID oid) {
		return (SocialInfo)EntityManager.getEntityByNamespace(oid, SocialClient.NAMESPACE);
	}
	
	public static void registerSocialInfo(SocialInfo sInfo) {
		EntityManager.registerEntityByNamespace(sInfo, SocialClient.NAMESPACE);
	}
	
	class SocialStateLoadHook implements LoadHook {
    	public void onLoad(Entity e) {
    		SocialInfo sInfo = (SocialInfo) e;
    		// Re-activate all quest states
    		log.debug("CHANNEL: got channelson Load:" + sInfo.getChannels());
    		for (String channel : sInfo.getChannels()) {
    			//TODO: Send join channel command.
    		}
    	}
    }
	
	class SocialStateUnloadHook implements UnloadHook {
    	public void onUnload(Entity e) {
    		SocialInfo sInfo = (SocialInfo) e;
    		// Re-activate all quest states
    		for (String channel : sInfo.getChannels()) {
    			//TODO: Send leave channel command
    		}
    	}
    }
    
	
	class PrivateInviteHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage gmMsg = (ExtensionMessage) msg;
			/*
			 * get some info about player
			 */
			OID oid = gmMsg.getSubject();
			OID targetOid = (OID) gmMsg.getProperty("targetOid");
			log.error("PrivateInviteHook oid=" + oid + " targetOid=" + targetOid);

			BasicWorldNode player = WorldManagerClient.getWorldNode(oid);
			InstanceInfo instInfo = InstanceClient.getInstanceInfo(player.getInstanceOid(), InstanceClient.FLAG_ALL_INFO);
			OID plyInst = instInfo.playerOid;
			if (plyInst == null) {
				log.error("PrivateInviteHook: playerOid of the instance is null");
				ExtendedCombatMessages.sendErrorMessage(oid, "You can only invite when you are in a private instance.");
				return true;
			}
			if (!plyInst.equals(oid)) {
				log.error("PrivateInviteHook: playerOid of the instance " + plyInst + " is not equals " + oid);
				ExtendedCombatMessages.sendErrorMessage(oid, "You can only invite when you are in your private instance ");
				return true;
			}
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "privateInvite");
			//String inviterName = WorldManagerClient.getObjectInfo(oid).name;
			String inviterName = aDB.getCharacterNameByOid(oid);
			props.put("inviterOid", oid);
			props.put("inviterName", inviterName);
			props.put("timeOut", PRIVATE_INVITE_TIMEOUT);
				TargetedExtensionMessage msgInv = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, targetOid, targetOid, false, props);
			Engine.getAgent().sendBroadcast(msgInv);
			return true;
		}
	}

	class PrivateInviteResponseHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage gmMsg = (ExtensionMessage) msg;
			OID responder = gmMsg.getSubject();

			OID inviter = (OID) gmMsg.getProperty("inviterOid");
			boolean response = (Boolean) gmMsg.getProperty("response");
			log.error("PrivateInviteHook responder=" + responder + " inviter=" + inviter + " response=" + response);

			if (response == true) {
				BasicWorldNode player = WorldManagerClient.getWorldNode(inviter);
				InstanceInfo instInfo = InstanceClient.getInstanceInfo(player.getInstanceOid(), InstanceClient.FLAG_ALL_INFO);
				log.error("PrivateInviteHook: inctanceType="+instInfo.inctanceType+" inviter="+inviter+" instInfo.playerOid="+instInfo.playerOid); 
				if (instInfo.inctanceType == 3 && instInfo.playerOid.equals(inviter)) {
					BasicWorldNode wnode = new BasicWorldNode();
					wnode.setLoc(player.getLoc());
					wnode.setOrientation(player.getOrientation());
					wnode.setInstanceOid(player.getInstanceOid());
					InstanceClient.objectInstanceEntry(responder, wnode, InstanceClient.InstanceEntryReqMessage.FLAG_NONE);
				} else {
					ExtendedCombatMessages.sendErrorMessage(responder, "The invitier is no longer in this self private instance");
					return true;
				}
			} else {
				// TODO: The responder has declined the invitation, let the inviter know
			}
			return true;
		}
	}
	
	
    public class SocialSubObjectHook extends GenerateSubObjectHook
    {
    	public SocialSubObjectHook() {
    	    super(SocialPlugin.this);
    	}
    	public SubObjData generateSubObject(Template template, Namespace name, OID masterOid)
    	{
    		if (Log.loggingDebug)
    		     Log.debug("SocialPlugin::GenerateSubObjectHook::generateSubObject()");
   	        if (masterOid == null) {
   	            Log.debug("GenerateSubObjectHook: no master oid");
   	            return null;
   	        }
    		if (Log.loggingDebug)
   	             Log.debug("GenerateSubObjectHook: masterOid=" + masterOid + ", template=" + template);
   	        
   	        Map<String,Serializable> props = template.getSubMap(SocialClient.NAMESPACE);
   	            
   	        // generate the subobject
			SocialInfo sInfo = new SocialInfo(masterOid);
			sInfo.setName(template.getName());

			Boolean persistent = (Boolean) template.get(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT);
			if (persistent == null)
				persistent = false;
			sInfo.setPersistenceFlag(persistent);
   		    
   	        if (props != null)
   	        {
   	        	// copy properties from template to object
   	        	for (Map.Entry<String,Serializable> entry : props.entrySet()) {
   	        		String key = entry.getKey();
   	        		Serializable value = entry.getValue();
   		    		if (!key.startsWith(":")) {
   		    			sInfo.setProperty(key, value);
   		    		}
   	        	}
   	        }
    		if (Log.loggingDebug)
    		     Log.debug("GenerateSubObjectHook: created entity " + sInfo);
    		
    		// register the entity
			registerSocialInfo(sInfo);
			
			if (persistent)
				Engine.getPersistenceManager().persistEntity(sInfo);
   	            
   	        // send a response message
   	        return new SubObjData();
    	}
    }
    
	class LogoutHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LogoutMessage message = (LogoutMessage) msg;
			OID playerOid = message.getSubject();
			log.debug("LOGOUT: SocialPlugin logout started for: " + playerOid);
			// Remove the player from any queues they may have been in
			if (playerOnlineOids.contains(playerOid))
				playerOnlineOids.remove(playerOid);
			else
				log.debug("LOGOUT: SocialPlugin logout player " + playerOid + " not on list");
			// Response
			Engine.getAgent().sendResponse(new ResponseMessage(message));
			log.debug("LOGOUT: SocialPlugin logout finished for: " + playerOid);
			return true;
		}
	}

    
    
    // Log the login information and send a response
    class LoginHook implements Hook {
    	 public boolean processMessage(Message msg, int flags) {
            LoginMessage message = (LoginMessage) msg;
            OID playerOid = message.getSubject();
           if( !playerOnlineOids.contains(playerOid))
        	   playerOnlineOids.add(playerOid);
        	   
            OID instanceOid = message.getInstanceOid();
            log.debug("LoginHook: playerOid=" + playerOid + " instanceOid=" + instanceOid);
            LinkedList<OID> friendsOf = aDB.getFriendsOf(playerOid);
          if(loginmessage!="")
        	  ChatClient.sendObjChatMsg(playerOid, 2, loginmessage);
            HashMap<OID, String> friends = aDB.getFriends(playerOid);
            HashMap props = new HashMap();
	            props.put("ext_msg_subtype", "ao.friedList");
	            Set<OID> friendsOids = friends.keySet();
	            int i=0;
    			for (OID k :friendsOids){
	            props.put("friendOid" + i, k);
	        	props.put("friendName" + i, friends.get(k));
	        	props.put("friendOnline" + i, playerOnlineOids.contains(k));
					i++;
    			}
    			props.put("friendsCount", friends.size());
    			HashMap<OID, String> clockList = aDB.getBlockList(playerOid);
    			Set<OID> blockListOids = clockList.keySet();
    			//ProxyPlugin.
    			i = 0;
    			for (OID k : blockListOids) {
    				props.put("blockOID" + i, k);
    				props.put("blockName" + i, clockList.get(k));
    				i++;
    			}
    			props.put("blockCount", clockList.size());
    		//	SocialClient.ApplyBlackList(playerOid, (ArrayList)blockListOids);
    	           
				if(Log.loggingDebug)	
        		log.debug("Social: send props "+props);
	            TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
	            Engine.getAgent().sendBroadcast(_msg);
              
            Engine.getAgent().sendResponse(new ResponseMessage(message));
            return true;
        }
    }
    class IsOnBlockList implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			OID targetOid = (OID)message.getProperty("targetOid");
			log.debug("IsOnBlockList: SocialPlugin  started for: " + playerOid);
			// Remove the player from any queues they may have been in
			boolean isOnBlockList = aDB.isOnBlackList(playerOid, targetOid);
			if (isOnBlockList) {
				//String targetName = WorldManagerClient.getObjectInfo(targetOid).name;
				String targetName = aDB.getCharacterNameByOid(targetOid);
				EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.ERROR_PLAYER_ON_BLOCK_LIST, 0, targetName);
			
			}else {
				isOnBlockList = aDB.isOnBlackList(targetOid, playerOid);
				if (isOnBlockList) {
					//String targetName = WorldManagerClient.getObjectInfo(targetOid).name;
					String targetName = aDB.getCharacterNameByOid(targetOid);
					EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.ERROR_PLAYER_ON_YOUR_BLOCK_LIST, 0, targetName);
				}
			}
				
			
			// Response
			Engine.getAgent().sendBooleanResponse(message, isOnBlockList);
			log.debug("IsOnBlockList: SocialPlugin  finished for: " + playerOid);
			return true;
		}
	}

    
 // Add Fried to list
	class GetFriendsHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			HashMap<OID, String> friends = aDB.getFriends(playerOid);
			HashMap props = new HashMap();
			props.put("ext_msg_subtype", "ao.friedList");
			Set<OID> friendsOids = friends.keySet();
			int i = 0;
			for (OID k : friendsOids) {
				props.put("friendOid" + i, k);
				props.put("friendName" + i, friends.get(k));
				props.put("friendOnline" + i, playerOnlineOids.contains(k));
					i++;
			}
			props.put("friendsCount", friends.size());

			HashMap<OID, String> blockList = aDB.getBlockList(playerOid);
			Set<OID> blockListOids = blockList.keySet();
			i = 0;
			for (OID k : blockListOids) {
				props.put("blockOID" + i, k);
				props.put("blockName" + i, blockList.get(k));
				i++;
			}
			props.put("blockCount", blockList.size());

			if (Log.loggingDebug)
				log.debug("GetFriendsHook: send props " + props);
			TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(_msg);

			// Engine.getAgent().sendResponse(new ResponseMessage(message));
			return true;
		}
	}
    
	class InviteByNameHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage inviteMsg = (ExtensionMessage) msg;
			OID inviterOid = inviteMsg.getSubject();
			OID inviteeOid = (OID) inviteMsg.getProperty("friendOid");
			String inviteeName = (String) inviteMsg.getProperty("friendName");
			if(Log.loggingDebug)
				log.debug("InviteByNameHook: inviterOid: " + inviterOid + " inviteeOid:" + inviteeOid + " inviteeName:" + inviteeName);
			// Get the targets oid
			try {
				if (inviteeOid != null) {
					 inviteeName = aDB.getCharacterNameByOid(inviteeOid);
					// inviteeName = WorldManagerClient.getObjectInfo(inviteeOid).name;
				} else {
					try {
						inviteeOid = GroupClient.getPlayerByName(inviteeName);
					} catch (IOException e) {
						log.dumpStack("InviteByNameHook: " + e.getMessage());
					}
				}
				if(inviteeOid == null){
					if(Log.loggingWarn)
						log.warn("InviteByNameHook: inviterOid: " + inviterOid + " cant find player oid for inviteeName:" + inviteeName);
					return true;
				}
				boolean isOnBlockList = aDB.isOnBlackList(inviterOid, inviteeOid);
				if (isOnBlockList) {
					String targetName = aDB.getCharacterNameByOid(inviteeOid);
					//String targetName = WorldManagerClient.getObjectInfo(inviteeOid).name;
					EventMessageHelper.SendErrorEvent(inviterOid, EventMessageHelper.ERROR_PLAYER_ON_BLOCK_LIST, 0, targetName);
				
				}else {
					isOnBlockList = aDB.isOnBlackList(inviteeOid, inviterOid);
					if (isOnBlockList) {
						String targetName = aDB.getCharacterNameByOid(inviteeOid);
						//String targetName = WorldManagerClient.getObjectInfo(inviteeOid).name;
						EventMessageHelper.SendErrorEvent(inviterOid, EventMessageHelper.ERROR_PLAYER_ON_YOUR_BLOCK_LIST, 0, targetName);
					}
				}
				if(Log.loggingDebug)
					log.debug("InviteByNameHook: isOnBlockList: " + isOnBlockList);
				if (isOnBlockList) {
					log.error("InviteByNameHook: isOnBlockList is true block");
					return false;
				}
				if(Log.loggingDebug)
					log.debug("InviteByNameHook: invited player oid: " + inviteeOid);
				HandleSocialInvite(inviterOid, inviteeOid);
				if(Log.loggingDebug)
					log.debug("InviteByNameHook: invited player oid: " + inviteeOid + " after HandleSocialInvite");
			} catch (Exception e) {
				log.dumpStack("InviteByNameHook: " + e.getLocalizedMessage()+" msg :"+e.getMessage());
				
				
			}
			if(Log.loggingDebug)
				log.debug("InviteByNameHook: inviterOid: " + inviterOid + " inviteeName:" + inviteeName + " End");

			return true;
		}
	}
    /**
     * Handles invite request by sending invite request message to the invitee
     */
	protected boolean HandleSocialInvite(OID inviterOid, OID inviteeOid) {
		log.debug("InviteByNameHook: invited player oid: " + inviteeOid+" by "+inviterOid);
	
		if (Log.loggingDebug) {
			log.debug("HandleSocialInvite: Received social invite message inviter:" +inviterOid + " invitee:" +inviteeOid);
		}

		// A player should not be able to invite themselves to a group
		if (inviterOid.equals(inviteeOid)) {
			log.debug("HandleSocialInvite: inviter:" + inviterOid + " invitee:" + inviteeOid+ " is the same break");
				return true;
		}

		//String inviteeName = WorldManagerClient.getObjectInfo(inviteeOid).name;
		String inviteeName = aDB.getCharacterNameByOid(inviteeOid);
		log.debug("HandleSocialInvite: invitee:" + inviteeOid + " inviteeName:"+inviteeName);
		log.debug("HandleSocialInvite: setPendingSocialInvite  test true to inviter and invitee");
		
		TargetedExtensionMessage inviteRequestMsg = new TargetedExtensionMessage(inviteeOid);
		inviteRequestMsg.setExtensionType(SocialClient.EXTMSG_SOCIAL_INVITE_REQUEST);
		inviteRequestMsg.setProperty("inviterOid", inviterOid);
		//String inviterName = WorldManagerClient.getObjectInfo(inviterOid).name;
		String inviterName = aDB.getCharacterNameByOid(inviterOid);
		inviteRequestMsg.setProperty("inviterName", inviterName);
		inviteRequestMsg.setProperty("inviteTimeout", SOCIAL_INVITE_TIMEOUT);
		log.debug("HandleSocialInvite: start  CancelInviteTimer");
		CancelInviteTimer timer = new CancelInviteTimer(inviterOid, inviteeOid, this);
		ScheduledFuture sf = Engine.getExecutor().schedule(timer, (long) SOCIAL_INVITE_TIMEOUT * 1000, TimeUnit.MILLISECONDS);
		tasks.put(inviterOid, sf);
		log.debug("HandleSocialInvite: created invite member task for: " + inviterOid);

		if (Log.loggingDebug) {
			log.debug("HandleSocialInvite: Sending group invite request inviter:" + inviterOid + " invitee:" + inviteeOid);
		}

		Engine.getAgent().sendBroadcast(inviteRequestMsg);

		return true;
	}

	public class CancelInviteTimer implements Runnable {

		protected OID inviterOid;
		protected OID inviteeOid;
		protected SocialPlugin social;

		public CancelInviteTimer(OID inviterOid, OID inviteeOid, SocialPlugin social) {
			log.debug("CancelInviteTimer: start task ");
			this.inviterOid = inviterOid;
			this.inviteeOid = inviteeOid;
			this.social = social;
		}

		@Override
		public void run() {
			// Check user still has items
			log.debug("CancelInviteTimer: running remove invite task for " + inviterOid);
			social.tasks.remove(inviterOid);
			 
		/*	CombatInfo inviter = CombatPlugin.getCombatInfo(inviterOid);
			CombatInfo invitee = CombatPlugin.getCombatInfo(inviteeOid);
			if (invitee != null)
				invitee.setPendingSocialInvite(false);
			if (inviter != null) {
				inviter.setPendingSocialInvite(false);
				*/
				// SendTargetedGroupMessage(inviter.getOwnerOid(), "Invitation for " +
				// WorldManagerClient.getObjectInfo(invitee.getOwnerOid()).name + " was
				// cancelled.");
				// SendTargetedGroupMessage(invitee.getOwnerOid(), "Invitation from " +
				// WorldManagerClient.getObjectInfo(inviter.getOwnerOid()).name + " was
				// cancelled.");

				TargetedExtensionMessage inviteRequestMsg = new TargetedExtensionMessage(inviteeOid);
				inviteRequestMsg.setExtensionType(SocialClient.EXTMSG_SOCIAL_CANCEL_INVITE_REQUEST);
				inviteRequestMsg.setProperty("inviterOid", inviterOid);
				//String inviterName = WorldManagerClient.getObjectInfo(inviterOid).name;
			    String inviterName = aDB.getCharacterNameByOid(inviterOid);
				inviteRequestMsg.setProperty("inviterName", inviterName);
				Engine.getAgent().sendBroadcast(inviteRequestMsg);
			//}
		}
	}
	 
	/**
     * SocialInviteResponseHook Adds a player to a group, or creates a new group
     * and sends out group info to the clients
     */
    class SocialInviteResponseHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage inviteMsg = (ExtensionMessage) msg;
            return HandleInviteResponse(inviteMsg);
        }
    }
	 /**
     * Handles logic for an invite request response - either accepted or declined
     * Creates a new group if the inviter is not currently grouped
     */
    protected boolean HandleInviteResponse(ExtensionMessage inviteMsg) {
        log.debug("SocialPlugin.HandleInviteResponse");

        OID inviterOid = (OID) inviteMsg.getProperty("inviterOid");
        OID inviteeOid = inviteMsg.getSubject();
        log.debug("SocialPlugin.HandleInviteResponse: inviterOid=" + inviterOid + ", inviteeOid=" + inviteeOid);

    
        if (tasks.containsKey(inviterOid)) {
    		Log.debug("SocialPlugin: cancelling invite member task");
    		tasks.get(inviterOid).cancel(true);
    		tasks.remove(inviterOid);
    	}
        String response = inviteMsg.getProperty("response").toString();
        if (response.equals("accept")) {
            log.debug("SocialPlugin.HandleInviteResponse: accept");
        	//String inviterName = WorldManagerClient.getObjectInfo(inviterOid).name;
        	//String inviteeName = WorldManagerClient.getObjectInfo(inviteeOid).name;
			String inviterName = aDB.getCharacterNameByOid(inviterOid);
			String inviteeName = aDB.getCharacterNameByOid(inviteeOid);
            aDB.addFriend(inviteeOid, inviterOid, inviterName);
            aDB.addFriend(inviterOid, inviteeOid, inviteeName);
            ChatClient.sendObjChatMsg(inviteeOid, 2, inviterName+" is now your friend!");
            ChatClient.sendObjChatMsg(inviterOid, 2, inviteeName+" is now your friend!");
            sendFriendList(inviteeOid);
            sendFriendList(inviterOid);
            
        } else {
            log.debug("SocialPlugin.HandleInviteResponse: declined");
        	//String inviteeName = WorldManagerClient.getObjectInfo(inviteeOid).name;
        //   SendTargetedGroupMessage(inviterOid, inviteeName + " has declined your group invite.");
        }
        
        log.debug("SocialPlugin.HandleInviteResponse: done");
        return true;
    }
    
void sendFriendList(OID playerOid){
	HashMap<OID, String> friends = aDB.getFriends(playerOid);
	HashMap props = new HashMap();
	props.put("ext_msg_subtype", "ao.friedList");
	Set<OID> friendsOids = friends.keySet();
	int i = 0;
	for (OID k : friendsOids) {
		props.put("friendOid" + i, k);
		props.put("friendName" + i, friends.get(k));
		props.put("friendOnline" + i, playerOnlineOids.contains(k));
			i++;
	}
	props.put("friendsCount", friends.size());
	
	HashMap<OID, String> blockList = aDB.getBlockList(playerOid);
	Set<OID> blockListOids = blockList.keySet();
	i = 0;
	for (OID k : blockListOids) {
		props.put("blockOID" + i, k);
		props.put("blockName" + i, blockList.get(k));
		i++;
	}
	props.put("blockCount", blockList.size());

	
	
	if (Log.loggingDebug)
		log.debug("sendFriendList: playerOid:+"+playerOid+" send props " + props);
	TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
	log.debug("sendFriendList Engine.getAgent():"+Engine.getAgent().getName());
	Engine.getAgent().sendBroadcast(_msg);

}
    
    
	// Add Player to Block list
	class AddBlockListHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			OID blockOID = (OID) message.getProperty("blockOid");
			String blockName = (String) message.getProperty("blockName");
			log.debug("AddBlockListHook " + playerOid + " " + blockOID + " " + blockName);
			if (blockOID != null)
				//blockName = WorldManagerClient.getObjectInfo(blockOID).name;
			    blockName = aDB.getCharacterNameByOid(blockOID);
			else {
				try {
//					blockOID = GroupClient.getPlayerByName(blockName);
					long blockOid = aDB.getCharacterOidByName(blockName);
					log.debug("AddBlockListHook: blockOid:"+blockOid);
					blockOID = OID.fromLong(blockOid);
					//} catch (IOException e) {
				} catch (Exception e) {
					log.error(e.getMessage());
				}
			}
			log.debug("AddBlockListHook: playerOid=" + playerOid + " " + blockOID + " " + blockName);
			if (blockOID != null) {
				
				HashMap<OID, String> friends = aDB.getFriends(playerOid);
				Set<OID> friendsOids = friends.keySet();
				if (friendsOids.contains(blockOID)) {
					  aDB.DelFriend(playerOid, blockOID);
			          aDB.DelFriend(blockOID, playerOid);
				}
				aDB.addToBlackList(playerOid, blockOID, blockName);
			} else {
				EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.SOCIAL_PLAYER_OFFLINE, 0, "");

			}
			// WorldManagerClient.sendObjChatMsg(playerOid, 2, "Welcome to Atavism
			// Online!");
			HashMap<OID, String> blocklist = aDB.getBlockList(playerOid);
			HashMap props = new HashMap();
			
			HashMap<OID, String> friends = aDB.getFriends(playerOid);
			props.put("ext_msg_subtype", "ao.friedList");
			Set<OID> friendsOids = friends.keySet();
			int i = 0;
			for (OID k : friendsOids) {
				props.put("friendOid" + i, k);
				props.put("friendName" + i, friends.get(k));
				props.put("friendOnline" + i, playerOnlineOids.contains(k));
					i++;
			}
			props.put("friendsCount", friends.size());

			HashMap<OID, String> blockList = aDB.getBlockList(playerOid);
			Set<OID> blockListOids = blockList.keySet();
			i = 0;
			for (OID k : blockListOids) {
				props.put("blockOID" + i, k);
				props.put("blockName" + i, blockList.get(k));
				i++;
			}
			props.put("blockCount", blockList.size());

		//	SocialClient.ApplyBlackList(playerOid, (ArrayList) blockListOids);

			if (Log.loggingDebug)
				log.debug("AddBlockListHook: send props " + props);
			TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(_msg);

			return true;
		}
	}

	class DelBlockListHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			OID blockOID = (OID) message.getProperty("blockOid");
			// String friendName = WorldManagerClient.getObjectInfo(friendOID).name;
			//if (Log.loggingTrace)
				log.trace("DelBlockListHook: playerOid=" + playerOid + " " + blockOID + " ");
			// Delete Friend from database
			aDB.DelFromBlackList(playerOid, blockOID);
			// Load FriendList from Database
			HashMap<OID, String> blocklist = aDB.getFriends(playerOid);

			HashMap props = new HashMap();
			HashMap<OID, String> friends = aDB.getFriends(playerOid);
			props.put("ext_msg_subtype", "ao.friedList");
			Set<OID> friendsOids = friends.keySet();
			int i = 0;
			for (OID k : friendsOids) {
				props.put("friendOid" + i, k);
				props.put("friendName" + i, friends.get(k));
				props.put("friendOnline" + i, playerOnlineOids.contains(k));
					i++;
			}
			props.put("friendsCount", friends.size());

			HashMap<OID, String> blockList = aDB.getBlockList(playerOid);
			Set<OID> blockListOids = blockList.keySet();
			i = 0;
			for (OID k : blockListOids) {
				props.put("blockOID" + i, k);
				props.put("blockName" + i, blockList.get(k));
				i++;
			}
			props.put("blockCount", blockList.size());

			//SocialClient.ApplyBlackList(playerOid, (ArrayList) blockListOids);

			if (Log.loggingDebug)
				log.debug("DelBlockListHook: send props " + props);
			TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(_msg);

			// Engine.getAgent().sendResponse(new ResponseMessage(message));
			return true;
		}
	}

    class DelFriendHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	ExtensionMessage message = (ExtensionMessage) msg;
            OID playerOid = message.getSubject();
            OID friendOID = (OID)message.getProperty("friendOid");
          //  String friendName = WorldManagerClient.getObjectInfo(friendOID).name;
            if (Log.loggingTrace) log.trace("DelFriendHook: playerOid=" + playerOid + " "+friendOID+ " " );
            //Delete Friend from database
            aDB.DelFriend(playerOid, friendOID);
            aDB.DelFriend(friendOID, playerOid);
              //Load FriendList from Database
            HashMap props = new HashMap();
        	HashMap<OID, String> friends = aDB.getFriends(playerOid);
			props.put("ext_msg_subtype", "ao.friedList");
			Set<OID> friendsOids = friends.keySet();
			int i = 0;
			for (OID k : friendsOids) {
				props.put("friendOid" + i, k);
				props.put("friendName" + i, friends.get(k));
				props.put("friendOnline" + i, playerOnlineOids.contains(k));
					i++;
			}
			props.put("friendsCount", friends.size());

			HashMap<OID, String> blockList = aDB.getBlockList(playerOid);
			Set<OID> blockListOids = blockList.keySet();
			i = 0;
			for (OID k : blockListOids) {
				props.put("blockOID" + i, k);
				props.put("blockName" + i, blockList.get(k));
				i++;
			}
			props.put("blockCount", blockList.size());

  				
				if(Log.loggingDebug)	
        		log.debug("DelFriendHook: send props "+props);
	            TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
	            Engine.getAgent().sendBroadcast(_msg);
            
            //Engine.getAgent().sendResponse(new ResponseMessage(message));
            return true;
        }
    }
    
    
    
    class SpawnedHook implements Hook  {
    	public boolean processMessage(Message msg, int flags) {
    		WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;
            OID objOid = spawnedMsg.getSubject();
            
            if (spawnedMsg.getType() != null && spawnedMsg.getType().isPlayer()) {            
            	// Set the players world property
            	Log.debug("SPAWNED: getting world for player: " + objOid);
            	OID instanceOid = spawnedMsg.getInstanceOid();
        	    int world = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
        	    if (playersInInstance.containsKey(world)) {
        	    	if (!playersInInstance.get(world).contains(objOid)) {
        	    		playersInInstance.get(world).add(objOid);
        	    	}
        	    } else {
        	    	ArrayList<OID> players = new ArrayList<OID>();
        	    	players.add(objOid);
        	    	playersInInstance.put(world, players);
        	    }
            }
            
            return true;
    	}
    }
    
    class DespawnedHook implements Hook  {
    	public boolean processMessage(Message msg, int flags) {
    		WorldManagerClient.DespawnedMessage despawnedMsg = (WorldManagerClient.DespawnedMessage) msg;
            OID objOid = despawnedMsg.getSubject();
            
            if (despawnedMsg.getType() != null && despawnedMsg.getType().isPlayer()) {
            	// Set the players world property
            	Log.debug("SPAWNED: getting world for player: " + objOid);
            	OID instanceOid = despawnedMsg.getInstanceOid();
        	    int world = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
        	    if (playersInInstance.containsKey(world)) {
        	    	if (playersInInstance.get(world).contains(objOid)) {
        	    		playersInInstance.get(world).remove(objOid);
        	    	}
        	    }
            }
            
            return true;
    	}
    }
    
    /*
     * Chat functions
     */
  /*  public class InstanceChatHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	ExtensionMessage getMsg = (ExtensionMessage)msg;
    		OID playerOid = getMsg.getSubject();
    		int chatChannel = (Integer)getMsg.getProperty("channel");
    		String message = (String)getMsg.getProperty("message");
    		Log.debug("CHAT: got chat message from: " + playerOid);
    		
    		TargetedComMessage comMessage = new TargetedComMessage();
	        comMessage.setString(message);
	        comMessage.setChannel(chatChannel);
	        comMessage.setChatterName(WorldManagerClient.getObjectInfo(playerOid).name);
	        ObjectInfo plyInfo = WorldManagerClient.getObjectInfo(playerOid);
	        int level = 0;
	        Serializable adminLevel = EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "adminLevel");
	        Log.debug("CHAT: got chat message from: " + playerOid+"; adminLevel="+adminLevel);
	        if (adminLevel != null)
	        	level = (Integer)adminLevel;
	        //if (plyInfo != null)
	        //	level = (Integer)plyInfo.getProperty("adminLevel");
	        Log.debug("CHAT: got chat message from: " + playerOid+"; permLevel="+level);
    		if (chatChannel == -2 && level>4) {
    			//Admin Global chat
    			for (ArrayList<OID> instancePlayers : playersInInstance.values()) {
    				for (OID pOid : instancePlayers) {
    	    	        comMessage.setTarget(pOid);
    	    	        Engine.getAgent().sendBroadcast(comMessage);
    	    		}
    			}
    			return true;
    		}
    		if(chatChannel == -2) {
    			return true;
    		}
    		if (chatChannel == -1) {
    			// Global chat
    			for (ArrayList<OID> instancePlayers : playersInInstance.values()) {
    				for (OID pOid : instancePlayers) {
    	    	        comMessage.setTarget(pOid);
    	    	        Engine.getAgent().sendBroadcast(comMessage);
    	    		}
    			}
    			return true;
    		}
    		
    		int world = (Integer) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "world");
    		if (!playersInInstance.containsKey(world)) {
    			Log.error("CHAT: No players found in instance");
    			return true;
    		}
    		
    		for (OID pOid : playersInInstance.get(world)) {
    	        comMessage.setTarget(pOid);
    	        Engine.getAgent().sendBroadcast(comMessage);
    		}
    		
    		return true;
        }
    }
    */
    
    public class ChannelChangeHook implements Hook {
        public boolean processMessage(Message m, int flags) {
            /*SocialClient.ChannelChangeMessage msg = (SocialClient.ChannelChangeMessage) m;
            OID playerOid = msg.getSubject();
            String channelName = msg.getChannelName();
            boolean joining = msg.getJoining();
            if (Log.loggingDebug)
                log.debug("ChannelChangeHook: playerOid=" + playerOid + ", channel=" + channelName 
                		+ ", joining=" + joining);
            
            Lock lock = getObjectLockManager().getLock(playerOid);
            lock.lock();
            try {
                SocialInfo sInfo = getSocialInfo(playerOid);
                if (joining)
                	sInfo.addChannel(channelName);
                else
                	sInfo.removeChannel(channelName);
            }
            finally {
                lock.unlock();
            }*/
            return true;
        }
    }
    
    static int PRIVATE_INVITE_TIMEOUT = 60;
    static HashMap<Integer, ArrayList<OID>> playersInInstance = new HashMap<Integer, ArrayList<OID>>();
    HashMap<OID, ScheduledFuture> tasks = new HashMap<OID, ScheduledFuture>();
	ArrayList<OID> playerOnlineOids = new ArrayList<OID>();
	public static String loginmessage = "";
    protected AccountDatabase aDB;
    protected Integer SOCIAL_INVITE_TIMEOUT = 60;
    private static final Logger log = new Logger("SocialPlugin");
}
