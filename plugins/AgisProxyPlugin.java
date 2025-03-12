package atavism.agis.plugins;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import atavism.agis.database.AccountDatabase;
import atavism.agis.database.AuthDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.database.MobDatabase;
import atavism.agis.objects.CombatInfo;
import atavism.agis.objects.QuestStateInfo;
import atavism.agis.plugins.ChatClient.TargetedComMessage;
import atavism.agis.util.EventMessageHelper;
import atavism.msgsys.GenericMessage;
import atavism.msgsys.Message;
import atavism.msgsys.MessageAgent;
import atavism.msgsys.MessageType;
import atavism.msgsys.MessageTypeFilter;
import atavism.msgsys.ResponseMessage;
import atavism.msgsys.TargetMessage;
import atavism.server.objects.Entity;
import atavism.server.objects.EntityManager;
import atavism.server.objects.Player;
import atavism.server.plugins.*;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.telemetry.Prometheus;
import atavism.server.util.*;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.Hook;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.events.*;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.network.*;

/**
 * handles client traffic to the rest of the servers
 */
public class AgisProxyPlugin extends ProxyPlugin implements Runnable {
    
	public void onActivate() {
		super.onActivate();
		try {
		    log.debug("AgisProxyPlugin.onActivate()");
		    // register for msgtype->hooks
	 	    this.registerProxyHooks();
	 	    
	 	   MessageTypeFilter filter = new MessageTypeFilter();
	 	   filter.addType(GroupClient.MSG_TYPE_GET_FRIENDS);
		   filter.addType(GroupClient.MSG_TYPE_ADD_FRIEND);
		   filter.addType(AgisWorldManagerClient.MSG_TYPE_LOGOUT_REQUEST);
		   filter.addType(AgisWorldManagerClient.MSG_TYPE_CANCEL_LOGOUT_REQUEST);
		   filter.addType(MSG_TYPE_SERVER_SHUTDOWN_MESSAGE);
		   filter.addType(MSG_TYPE_SERVER_SHUTDOWN);
		   filter.addType(MSG_TYPE_SERVER_RELOAD);
		   Engine.getAgent().createSubscription(filter, this);
		   
		 //  MessageTypeFilter responderFilter = new MessageTypeFilter();
		   responderFilter2.addType(GroupClient.MSG_TYPE_GET_PLAYER_BY_NAME); 
	       //responderFilter.addType(GuildClient.MSG_TYPE_GET_PLAYER_IS_ONLINE);
	     //  responderFilter.addType(LoginMessage.MSG_TYPE_LOGIN);
	     //  responderFilter.addType(LogoutMessage.MSG_TYPE_LOGOUT);
	     //  responderFilter.addType(AgisMobClient.MSG_TYPE_GET_PLAYERS_ONLINE);
	     //  Engine.getAgent().createSubscription(responderFilter, this, MessageAgent.RESPONDER);

	      // responderFilter.addType(AgisWorldManagerClient.MSG_TYPE_WATER_REGION_TRANSITION);
		   log.error(""+responderFilter2.getMessageTypes());
	       Engine.getAgent().removeSubscription(responderSubId2);
	       responderSubId2 = Engine.getAgent().createSubscription(responderFilter2, this, MessageAgent.RESPONDER);
	       
		   MessageTypeFilter responderFilter2 = new MessageTypeFilter();
//	       responderFilter.addType(GroupClient.MSG_TYPE_GET_PLAYER_BY_NAME); 
	       //responderFilter.addType(GuildClient.MSG_TYPE_GET_PLAYER_IS_ONLINE);
	       responderFilter2.addType(LoginMessage.MSG_TYPE_LOGIN);
	       responderFilter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
	       responderFilter2.addType(AgisMobClient.MSG_TYPE_GET_PLAYERS_ONLINE);
	       Engine.getAgent().createSubscription(responderFilter2, this, MessageAgent.RESPONDER);

		   //Block params to send to the client
			addFilteredProperty("MobBehav");
			addFilteredProperty("spawnId");
			addFilteredProperty("attackDistance");
			addFilteredProperty("combat.autoabilitybase");
			addFilteredProperty("combat.mobflag");
			addFilteredProperty(ClassAbilityPlugin.KILL_ADD_EXP_LEV_STAT);
			addFilteredProperty(ClassAbilityPlugin.MIN_MOB_LEVEL);
			addFilteredProperty(ClassAbilityPlugin.KILL_EXP_STAT);
			addFilteredProperty("attackType");
			addFilteredProperty("statProfile");
			addFilteredProperty("behavProfile");
			addFilteredProperty("speed_run");
			addFilteredProperty("speed_walk");
			addFilteredProperty("animationState");

		}
		catch(Exception e) {
		    throw new AORuntimeException("onActivate failed", e);
		}
		
		aDB = new AccountDatabase(true);
		authDB = new AuthDatabase();
		
		loadData();
		
		
		
		Engine.getExecutor().scheduleAtFixedRate(this, 5, 15, TimeUnit.SECONDS);
	}
	
	protected void registerProxyHooks() {
	  	//getHookManager().addHook(CombatClient.MSG_TYPE_UPDATE_PLAYER_ATTITUDES,
	  	//			 new UpdatePlayerAttitudesHook());
	//	getHookManager().addHook(GuildClient.MSG_TYPE_GET_PLAYER_IS_ONLINE, new GetPlayerIsOnlineHook());
		//getHookManager().addHook(GuildClient.MSG_TYPE_GET_PLAYER_IS_ONLINE, new GetPlayerIsOnlineHook());
		getHookManager().addHook(GroupClient.MSG_TYPE_GET_PLAYER_BY_NAME, new GetPlayerOidFromNameHook());
		getHookManager().addHook(AgisMobClient.MSG_TYPE_GET_PLAYERS_ONLINE, new GetPlayersOnlineHook());
		//	getHookManager().addHook(GroupClient.MSG_TYPE_GET_FRIENDS, new GetFriendsHook());
	 // 	getHookManager().addHook(GroupClient.MSG_TYPE_ADD_FRIEND, new AddFriendHook());
	  	getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
	  	getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
	  	getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_LOGOUT_REQUEST, new LogoutRequestHook());
	  	getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_CANCEL_LOGOUT_REQUEST, new CancelLogoutRequestHook());
		getHookManager().addHook(MSG_TYPE_SERVER_SHUTDOWN, new ServerShutdownHook());
		getHookManager().addHook(MSG_TYPE_SERVER_SHUTDOWN_MESSAGE, new ServerShutdownMessageHook());
		getHookManager().addHook(MSG_TYPE_SERVER_RELOAD, new ServerReloadHook());
	
	}
	
	@Override
	public void run() {
		Log.debug("AUTH: updating server status");
		authDB.sendServerStatusUpdate(getOids().size());
	}
	
	
	void loadData() {
		ContentDatabase cDB = new ContentDatabase(false);
		String logoutTime = cDB.loadGameSetting("LOGOUT_TIME");
        if (logoutTime != null)
        	LOGOUT_TIME = Integer.parseInt(logoutTime);
        
        String logoutToCharacterSelection = cDB.loadGameSetting("LOGOUT_TO_CHARACTER_SELECTION");
        if (logoutToCharacterSelection != null)
        	LOGOUT_TO_CHARACTER_SELECTION = Boolean.parseBoolean(logoutToCharacterSelection);
    	String devMode = cDB.loadGameSetting("SERVER_DEVELOPMENT_MODE");
		if (devMode != null) {
			EnginePlugin.DevMode = Boolean.parseBoolean(devMode);
			log.info("Game Settings EnginePlugin.DevMode set to " + EnginePlugin.DevMode);
		}
		cDB.loadEditorOptions();
		
	}
	
/**
 * Function to Execute Reload Data
 */
	protected void ReloadTemplates(Message msg) {
		Log.error("AgisProxyPlugin ReloadTemplates Start");
			loadData();
		Log.error("AgisProxyPlugin ReloadTemplates End");
	}
	
    /**
     * Send Tagteted message to player by player OID
     * @param msgType
     * @param target
     */
	public static void sendTargetMessage(MessageType msgType, OID target)
	{
		TargetMessage tm = new TargetMessage(msgType, target);
	    Engine.getAgent().sendRPCReturnObject(tm);
	}
	
	/**
	 * Send Targeted message to player by character name
	 * @param msgType
	 * @param targetName
	 */
	public static void sendTargetMessage(MessageType msgType, String targetName) {
		AccountDatabase aDB = new AccountDatabase(false);
		long target = aDB.getCharacterOidByName(targetName);
		if (target > 0L) {
			TargetMessage tm = new TargetMessage(msgType, OID.fromLong(target));
			Engine.getAgent().sendRPCReturnObject(tm);
		}
	}

	/**
	 * Send message to force logout player by OID
	 * @param playerOid
	 * @return boolean
	 */
	public static boolean logoutPlayer(OID playerOid) {
		if (playerOid != null) {
			TargetMessage logoutPly = new TargetMessage(MSG_TYPE_LOGOUT_PLAYER, playerOid);
			Engine.getAgent().sendRPCReturnObject(logoutPly);
			return true;
		} else {
			return false;
		}

	}
	
	/**
	 * Send message to force logout player by name
	 * @param playerOid
	 * @return boolean
	 */
	public static boolean logoutPlayer(String playerName) {
		AccountDatabase aDB = new AccountDatabase(false);
		long target = aDB.getCharacterOidByName(playerName);
		if (target > 0L) {
			TargetMessage logoutPly = new TargetMessage(MSG_TYPE_LOGOUT_PLAYER, OID.fromLong(target));
			Engine.getAgent().sendRPCReturnObject(logoutPly);
			return true;
		} else {
			return false;
		}
	}
	
	/**
     * process login message from the client.
     */
    protected boolean processLogin(ClientConnection con, AuthorizedLoginEvent loginEvent) {

        if (! super.processLogin(con, loginEvent))
	    return false;
        
        OID oid = loginEvent.getOid();
        Log.debug("LOGIN: login oid: " + oid);
        //Long accountID = getAccountId(oid);
        OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
        //Entity banList = ObjectManagerClient.loadObjectData("banList");
    	//HashMap<Long, Long> bannedUsers = (HashMap) banList.getProperty("bannedUsers");
        if (bannedUsers.containsValue(accountID)) {
        	// Check to see if time is up
        	Calendar c = Calendar.getInstance();
        	Long currentTime = c.getTimeInMillis();
        	if (currentTime.compareTo(bannedUsers.get(accountID)) > 0) {
        		removeAccountFromBanList(accountID);
        	} else {
        		Log.debug("LOGIN: user " + oid + " tried to login but they are banned, so closing connection");
        		Prometheus.registry().counter("rdp_connection_closed", "reason", "banned").increment();
            	con.close();
        	}
        }
        
        // get friends list and add them to the reverse map
        
        // now get the entry for this player and send a message to each person in the list
        // letting them know the player is now online
        
        // Example: get the inventory
        String IPaddress = con.IPAndPort();
        Log.debug("LOGIN: login IPAddress: " + con.toString());
        
        //Events.loginToWorld(oid, IPaddress);
        HashMap<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ipaddress", IPaddress);
        DataLoggerClient.logData("PLAYER_LOGGED_IN_EVENT", oid, null, accountID, props);
        Log.debug("LOGIN: sent login event");

        return true;
    }

	private class ServerReloadHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			Log.error("ServerReloadHook Start");
			ExtensionMessage tMsg = (ExtensionMessage) msg;
			if (EnginePlugin.DevMode) {
				GenericMessage gm = new GenericMessage(EnginePlugin.MSG_TYPE_RELOAD_TEMPLATES);
				Engine.getAgent().sendBroadcast(gm);
			}
			Log.error("ServerReloadHook End");

			return true;
		}

	}
    
	private class ServerShutdownHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			Log.debug("ServerShutdownHook hit");
			ExtensionMessage tMsg = (ExtensionMessage) msg;
			OID subject = tMsg.getSubject();
			Set<OID> players = getPlayerOids();
			if (!players.contains(subject))
				return true;
			String message = (String) tMsg.getProperty("message");
			String schedule = (String) tMsg.getProperty("schedule");
			long time = (int) tMsg.getProperty("time");
			boolean restart = (boolean) tMsg.getProperty("restart");
			Serializable adminLevel = EnginePlugin.getObjectProperty(subject, WorldManagerClient.NAMESPACE, "adminLevel");
			if (adminLevel == null)
				return true;
			if ((int) adminLevel < 5)
				return true;
			GenericMessage serverShutdownMessage = new GenericMessage(ProxyPlugin.MSG_TYPE_SERVER_SHUTDOWN_MESSAGE);
			serverShutdownMessage.setProperty("oid", subject);
			serverShutdownMessage.setProperty("message", message);
			serverShutdownMessage.setProperty("schedule", schedule);
			serverShutdownMessage.setProperty("restart", restart);
			serverShutdownMessage.setProperty("time", time);
			Engine.getAgent().sendBroadcast(serverShutdownMessage);
			Log.debug("ServerShutdownHook End");
			return true;
		}
	}

	private class ServerShutdownMessageHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			Log.debug("ServerShutdownMessageHook hit");
			GenericMessage tMsg = (GenericMessage) msg;

			String message = (String) tMsg.getProperty("message");
			String schedule = (String) tMsg.getProperty("schedule");
			long time = (long) tMsg.getProperty("time");
			boolean closeClients = tMsg.getProperty("restart") != null ? (boolean) tMsg.getProperty("restart") : true;

			Log.debug("ServerShutdownMessageHook message=" + message + " schedule=" + schedule + " time=" + time+" closeClients="+closeClients+" "+ tMsg.getProperty("restart")+" tMsg.");
			LinkedList<Long> timeList = new LinkedList<Long>();
			String[] _shedule = schedule.split(";");
			for (String t : _shedule) {
				if (t.length() > 0)
					timeList.add((long) Integer.parseInt(t));
			}

			String timeText = time + " second" + (time > 1 ? "s" : "");
			if (time > 60) {
				long minute = time / 60;
				timeText = minute + " minute" + (minute > 1 ? "s" : "");
			}
			Log.debug("ServerShutdownMessageHook timeList=" + timeList);
			if (timeList.size() > 0 || time > 0) {
				long ltime = time;
				if (timeList.size() > 0) {
					ltime = timeList.remove(0);
					time = time - ltime;
				}else {
					time=0;
				}
				CountdownShutdown countdownShutdown = new CountdownShutdown(message, time, timeList,closeClients);
				Engine.getExecutor().schedule(countdownShutdown, ltime, TimeUnit.SECONDS);
			}
			Log.debug("ServerShutdownMessageHook |");
			TargetedComMessage comMessage = new TargetedComMessage();
			comMessage.setString(message.replace("{Time}", timeText));
			comMessage.setChannel(-2);
			comMessage.setChatterName("Server");

			Set<OID> players = getPlayerOids();
			for (OID pOid : players) {
				comMessage.setTarget(pOid);
				Engine.getAgent().sendBroadcast(comMessage);
			}
			if(closeClients) {
				ProxyPlugin.restriction_level = 5;
				authDB.sendServerRestrictionLevelUpdate(5);
			}
			Log.debug("ServerShutdownMessageHook End");
			return true;
		}
	}

	class CountdownShutdown implements Runnable {
		protected long timeLeft = -1;
		protected String message = "";
		protected LinkedList<Long> timeList;
		protected boolean closeClients = false;
				
		public CountdownShutdown(String message, long time, LinkedList<Long> timeList,boolean closeClients) {
			Log.debug("CountdownShutdown");
			this.message = message;
			this.timeLeft = time;
			this.timeList = timeList;
			this.closeClients = closeClients;
		}

		public void run() {
			Log.debug("CountdownShutdown run");
			String timeText = timeLeft + " second" + (timeLeft > 1 ? "s" : "");
			if (timeLeft > 60) {
				long minute = timeLeft / 60;
				timeText = minute + " minute" + (minute > 1 ? "s" : "");
			}
			if (timeList.size() > 0 || timeLeft > 0) {
				long ltime = timeLeft;
				if (timeList.size() > 0) {
					ltime = timeList.remove(0);
					timeLeft = timeLeft - ltime;
				}else {
					timeLeft=0;
				}
				CountdownShutdown countdownShutdown = new CountdownShutdown(message, timeLeft, timeList, closeClients);
				Engine.getExecutor().schedule(countdownShutdown, ltime, TimeUnit.SECONDS);
				TargetedComMessage comMessage = new TargetedComMessage();
				//MessageFormat.format(message, timeText);
				comMessage.setString(message.replace("{Time}", timeText));
				comMessage.setChannel(-2);
				comMessage.setChatterName("Server");

				Set<OID> players = getPlayerOids();
				for (OID pOid : players) {
					comMessage.setTarget(pOid);
					Engine.getAgent().sendBroadcast(comMessage);
				}
			} else if (closeClients){
				
				Set<OID> players = getPlayerOids();
				for (OID pOid : players) {
				    Prometheus.registry().counter("player_logout", "reason", "shutdown").increment();
					TargetMessage logoutPly = new TargetMessage(MSG_TYPE_LOGOUT_PLAYER, pOid);
					Engine.getAgent().sendRPCReturnObject(logoutPly);
				}
			} else {
				Log.debug("CountdownShutdown no closeClients ");
			}
			Log.debug("CountdownShutdown end");

		}

		private static final long serialVersionUID = 1L;
	}
    
	AtomicInteger maxPlayersOnline = new AtomicInteger();
    
    // Log the login information and send a response
    class LoginHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LoginMessage message = (LoginMessage) msg;
            OID playerOid = message.getSubject();
            OID instanceOid = message.getInstanceOid();
            String plyName = message.getPlayerName();
            Log.debug("LoginHook: playerOid=" + playerOid + " plyName="+plyName+" instanceOid=" + instanceOid);
            Log.debug("LoginHook: playerOid=" + playerOid + " plyName="+plyName+" instanceOid=" + instanceOid + " " + playerManager.getPlayer(playerOid));
            if(playersOnlineList.containsKey(playerOid)) {
            	  Log.debug("LoginHook: playerOid=" + playerOid + " plyName="+plyName+" instanceOid=" + instanceOid+" !!!! Player is on List !!!! ");
            }else {
            	playersOnlineList.put(playerOid, message.getPlayerName());
            	int count = maxPlayersOnline.get();
            	if(playersOnlineList.size()>count) {
            		count = maxPlayersOnline.incrementAndGet();
            		aDB.saveMemoryDataStats("players", count);
            	}
              	if(!playersOnlineOnProxy.containsKey(playerOid)) {
            		playersOnlineOnProxy.put(playerOid, message.getProxy());
            	}else {
            		playersOnlineOnProxy.replace(playerOid, message.getProxy());
            	}
              	
              	
            }
            
            if(playerManager.getPlayer(playerOid)==null) {
            	Log.debug("LoginHook: playerOid=" + playerOid + " instanceOid=" + instanceOid + " player is not on this proxy server break and send response");
                Engine.getAgent().sendResponse(new ResponseMessage(message));
                return true;
            }
            String playerName = aDB.getCharacterNameByOid(playerOid);
            //WorldManagerClient.getObjectInfo(playerOid).name;
            LinkedList<OID> friendsOf = aDB.getFriendsOf(playerOid);
            // For each friend of, check if they are online then alert them to the fact their friend has logged in.
            for (OID friendOid : friendsOf) {
            	if (getOids().contains(friendOid)) {
            		ChatClient.sendObjChatMsg(friendOid, 2, playerName + " has logged in.");
            		SendFriendsList(friendOid);
            	}
            }
            Engine.getAgent().sendResponse(new ResponseMessage(message));
            
            SendFriendsList(playerOid);
         //   ChatClient.sendObjChatMsg(playerOid, 2, "Welcome to Smoo Online!");
            //sendPlayersOnline(false);
            authDB.sendServerStatusUpdate(getOids().size());
            return true;
        }
    }
    
    // Log the logout information and send a response
    class LogoutHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LogoutMessage message = (LogoutMessage) msg;
            OID playerOid = message.getSubject();
            String plyName = message.getPlayerName();
            Log.debug("LOGOUT: proxy logout started for: " + playerOid);
            Engine.getAgent().sendResponse(new ResponseMessage(message));
            if(playersOnlineList.containsKey(playerOid)) {
            	playersOnlineList.remove(playerOid);
            	if(playersOnlineOnProxy.containsKey(playerOid)) {
            		playersOnlineOnProxy.remove(playerOid);
                	
            	}
            }else {
            	Log.debug("LoginHook: playerOid=" + playerOid + " plyName="+plyName+" !!!! Player is not on List !!!! ");
        	}
     
            if(playerManager.getPlayer(playerOid)==null) {
            	Log.debug("LogoutHook: playerOid=" + playerOid +" player is not on this proxy server break and send response");
                return true;
            }

            //sendPlayersOnline(true);
            authDB.sendServerStatusUpdate(getOids().size());
            
            LinkedList<OID> friendsOf = aDB.getFriendsOf(playerOid);
            // For each friend of, check if they are online then alert them to the fact their
            // friend has logged in.
            String playerName = aDB.getCharacterNameByOid(playerOid);
            		//WorldManagerClient.getObjectInfo(playerOid).name;
            
            for (OID friendOid : friendsOf) {
            	if (getOids().contains(friendOid)) {
            		ChatClient.sendObjChatMsg(friendOid, 2, playerName + " has logged out.");
            		SendFriendsList(friendOid);
            	}
            }
            
            if (scheduledLogouts.containsKey(playerOid)) {
    			ScheduledLogout logoutTimer = scheduledLogouts.remove(playerOid);
    			logoutTimer.scheduledExecutioner.cancel(true);
    		}
            
            Log.debug("LOGOUT: proxy logout finished for: " + playerOid);
            return true;
        }
    }
    
    public class LogoutRequestHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	ExtensionMessage getMsg = (ExtensionMessage)msg;
    		OID playerOid = getMsg.getSubject();
    		Log.debug("LOGOUT: got logout request from: " + playerOid);
    	    if(playerManager.getPlayer(playerOid)==null) {
            	Log.debug("LogoutRequestHook: playerOid=" + playerOid +" player is not on this proxy server break and send response");
                return true;
            }

    		// Run combat check etc. to verify player can logout
    		boolean inCombat = (Boolean) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_COMBATSTATE);
    		if (inCombat) {
    			EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.ERROR_IN_COMBAT, 0, "");
    			return true;
    		}
    		
    		if (LOGOUT_TIME < 1) {
    			logPlayerOut(playerOid);
    		} else {
    			ScheduledLogout logoutTimer = new ScheduledLogout();
    			logoutTimer.setPlayerOid(playerOid);
    			logoutTimer.setScheduledExecutioner(Engine.getExecutor().schedule(logoutTimer, LOGOUT_TIME, TimeUnit.SECONDS));
    			scheduledLogouts.put(playerOid, logoutTimer);
    			
    			// Send down message to client to show the logout timer window
    	    	Map<String, Serializable> props = new HashMap<String, Serializable>();
    			props.put("ext_msg_subtype", "logout_timer");
    			props.put("timer", LOGOUT_TIME);
    			TargetedExtensionMessage eMsg = new TargetedExtensionMessage(
    					WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
    					playerOid, false, props);
    			Engine.getAgent().sendBroadcast(eMsg);
    		}
    		
    		return true;
        }
    }
    
    public class CancelLogoutRequestHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	ExtensionMessage getMsg = (ExtensionMessage)msg;
    		OID playerOid = getMsg.getSubject();
    		Log.debug("LOGOUT: got cancel logout request from: " + playerOid);
    		if(playerManager.getPlayer(playerOid)==null) {
               	Log.debug("CancelLogoutRequestHook: playerOid=" + playerOid +" player is not on this proxy server break and send response");
                   return true;
               }

    		
    		if (scheduledLogouts.containsKey(playerOid)) {
    			ScheduledLogout logoutTimer = scheduledLogouts.remove(playerOid);
    			logoutTimer.scheduledExecutioner.cancel(true);
    		}
    		
    		return true;
        }
    }
    
    protected void logPlayerOut(OID playerOid) {
    	scheduledLogouts.remove(playerOid);
    	
    	ClientConnection con = playerManager.getPlayer(playerOid).getConnection();
    	LogoutEvent logoutEvent = new LogoutEvent();
		logoutEvent.setSuccess(true);
		con.setLogoutState(true);
		if (LOGOUT_TO_CHARACTER_SELECTION) {
			logoutEvent.logoutToCharacterSelection(true);
			OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
			int account = (int)accountID.toLong();
    	
			SecureTokenSpec masterSpec = new SecureTokenSpec(SecureTokenSpec.TOKEN_TYPE_DOMAIN,
    			Engine.getAgent().getName(), System.currentTimeMillis() + 30000);
    	
			masterSpec.setProperty("account_id", account);
			masterSpec.setProperty("account_name", "");
			byte[] masterToken = SecureTokenManager.getInstance().generateToken(masterSpec);
			Log.debug("tokenLen=" + masterToken.length + " token=" + atavism.server.util.Base64.encodeBytes(masterToken));
			logoutEvent.setAuthToken(masterToken);
		} else {
			logoutEvent.logoutToCharacterSelection(false);
		}
		
        con.send(logoutEvent.toBytes());
    }
    
    protected void sendPlayersOnline(boolean loggingOut) {
    	int playersOnline = playerManager.getPlayerCount();
    	if (loggingOut)
    		playersOnline -= 1;
    	// Send this message to every player (probably isn't very efficient)
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "players_online");
		props.put("online", playersOnline);
    	for (OID oid : super.getPlayerOids()) {
    		TargetedExtensionMessage msg = new TargetedExtensionMessage(
    				WorldManagerClient.MSG_TYPE_EXTENSION, oid, 
    				oid, false, props);
    		Engine.getAgent().sendBroadcast(msg);
    	}
    }
    
    /**
     * Used to get a Set of all players logged in.
     * @return
     */
    public Set<OID> getOids() {
    	return super.getPlayerOids();
    }
    
    class GetPlayerOidFromNameHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		log.debug("PROXY: GetPlayerOidFromNameHook " + msg);
        	    GroupClient.getPlayerByNameMessage GPBNMsg = (GroupClient.getPlayerByNameMessage) msg;
    	    String playerName = (String) GPBNMsg.getProperty("inviteeName");
    	    log.debug("PROXY: getting player oid from name " + playerName);
    	    OID oid = getPlayerOid(playerName);
    	    log.debug("PROXY: getting player oid from name " + playerName+" oid:"+oid);
      	  
    	    Engine.getAgent().sendOIDResponse(msg, oid);
    	    return true;
    	}
    }
    
    class GetPlayerIsOnlineHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    	    GuildClient.getPlayerIsOnlineMessage GPBNMsg = (GuildClient.getPlayerIsOnlineMessage) msg;
    	    OID oid = (OID) GPBNMsg.getProperty("plyOid");
    	    Log.debug("PROXY: getting player is online " + oid);
    	    boolean isonline = isPlayerOnline(oid);
    	    Engine.getAgent().sendBooleanResponse(msg, isonline);
    	    return true;
    	}
    }
  
    class GetPlayersOnlineHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    	    AgisMobClient.GetPlayersOnlineMessage GPBNMsg = (AgisMobClient.GetPlayersOnlineMessage) msg;
    	    OID instanceOid = GPBNMsg.getInstanceOid();
    	    Log.debug("PROXY: getting players is online");
    	    List<Player> players = new ArrayList<Player>(playerManager.getPlayerCount());
    	    LinkedList<OID> plyOids = new LinkedList<OID>();
        	playerManager.getPlayers(players);
            for (Player player : players) {
            
            	plyOids.add(player.getOid());
            }
            Engine.getAgent().sendObjectResponse(msg, plyOids);
    	    return true;
    	}
    }
  
   
    
    
    public OID getPlayerOid(String name) {
    	List<Player> players = new ArrayList<Player>(playerManager.getPlayerCount());
    	playerManager.getPlayers(players);
    	Log.debug("PROXY: searching for player oid from name: " + name + " and numPlayers: " + playerManager.getPlayerCount());
        for (Player player : players) {
            if (name.equals(player.getName()))
            	return player.getOid();
        }
    	Log.debug("PROXY: found no oid for player " + name);
    	return null;
    }
    
    public boolean isPlayerOnline(OID oid) {
    	List<Player> players = new ArrayList<Player>(playerManager.getPlayerCount());
    	playerManager.getPlayers(players);
        for (Player player : players) {
        	if (player.getOid().equals(oid))
        		return true;
        }
    	return false;
    }
    
    public static void banUser(OID characterOid) {
    	AccountDatabase accDB = new AccountDatabase(false);
    	OID accountID = accDB.getAccountForCharacter(characterOid);
    	if(accountID != null) {
    		accDB.setAccountStatus(accountID, 0);
    		AuthDatabase authDB = new AuthDatabase();
    		authDB.setAccountStatus(accountID, 0);
    	}
    }
    
	public static void banUser(String characterName) {
		AccountDatabase accDB = new AccountDatabase(false);
		long charId = accDB.getCharacterOidByName(characterName);
		if (charId > 0) {
			OID characterOid = OID.fromLong(charId);
			OID accountID = accDB.getAccountForCharacter(characterOid);
			if (accountID != null) {
				accDB.setAccountStatus(accountID, 0);
				AuthDatabase authDB = new AuthDatabase();
				authDB.setAccountStatus(accountID, 0);
			}
		}
	}
    
    
	protected static void removeAccountFromBanList(OID accountID) {
		Entity banList = ObjectManagerClient.loadObjectData("banList");
		bannedUsers.remove(accountID);
		banList.setProperty("bannedUsers", (Serializable) bannedUsers);
		Log.debug("BAN: removed user: " + accountID + ". Banlist = " + banList);
		ObjectManagerClient.saveObjectData("banList", banList, WorldManagerClient.NAMESPACE);
	}

    public class AddFriendHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	ExtensionMessage spawnMsg = (ExtensionMessage)msg;
    		OID playerOid = (OID) spawnMsg.getProperty("playerOid");
    		String friendName = (String) spawnMsg.getProperty("friend");
    		Log.debug("FRIEND: adding friend " + friendName + " to " + playerOid);
    		// Check to make sure the friend does exist
    		OID friendOID = getPlayerOid(friendName);
    		if (friendOID == null) {
    			ChatClient.sendObjChatMsg(playerOid, 2, friendName + " could not be found.");
    			return true;
    		}
    		// Check to make sure they do not already have this player as a friend
    		if (aDB.getFriends(playerOid).containsKey(friendOID)) {
    			ChatClient.sendObjChatMsg(playerOid, 2, friendName + " is already your friend.");
    			return true;
    		}
    		// Finally, add friend
    		aDB.addFriend(playerOid, friendOID, friendName);
    		ChatClient.sendObjChatMsg(playerOid, 2, friendName + " added to friends.");
    		SendFriendsList(playerOid);
            return true;
        }
    }
    
    public class GetFriendsHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	ExtensionMessage getMsg = (ExtensionMessage)msg;
    		OID playerOid = OID.fromLong((Long)getMsg.getProperty("playerOid"));
    		SendFriendsList(playerOid);
    		sendPlayersOnline(false);
    		return true;
        }
    }
    
    public void SendFriendsList(OID playerOid) {
    	Log.debug("FRIENDS: sending friends list for player: " + playerOid);
    	HashMap<OID, String> friends = aDB.getFriends(playerOid);
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "friends_list");
		int numFriends = 0;
		for (OID friendOID : friends.keySet()) {
			props.put("friend" + numFriends + "Oid", friendOID);
			props.put("friend" + numFriends + "Name", friends.get(friendOID));
			props.put("friend" + numFriends + "Status", isPlayerOnline(friendOID));
			numFriends++;
		}
		props.put("numFriends", numFriends);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(
				WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
				playerOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("FRIENDS: sent friends list for player: " + playerOid + " with props: " + props);
    }
    
    class ScheduledLogout implements Runnable {
    	OID playerOid;
    	ScheduledFuture<?> scheduledExecutioner;
    	
    	public void run() {
    		logPlayerOut(playerOid);
    	}
    	
    	public void setPlayerOid(OID playerOid) {
    		this.playerOid = playerOid;
    	}
    	
    	public void setScheduledExecutioner(ScheduledFuture<?> executioner) {
    		this.scheduledExecutioner = executioner;
    	}
    }
  
    public static HashMap<OID, Long> bannedUsers = new HashMap<OID, Long>();
    // Keeps track of who to notify when a player logs in
    public static HashMap<OID, ArrayList<OID>> friendReverseMap = new HashMap<OID, ArrayList<OID>>();
    // Keeps track of who to send chat messages to
    public static HashMap<String, ArrayList<OID>> chatChannelSubscribers = new HashMap<String, ArrayList<OID>>();
    
    public static HashMap<OID, ScheduledLogout> scheduledLogouts = new HashMap<OID, ScheduledLogout>();
    
    public static int LOGOUT_TIME = 10;
    public static boolean LOGOUT_TO_CHARACTER_SELECTION = true;
    
    AccountDatabase aDB;
    AuthDatabase authDB;
  
}
