package atavism.agis.plugins;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import atavism.agis.database.AccountDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.objects.CombatInfo;
import atavism.agis.plugins.ChatClient.TargetedComMessage;
import atavism.msgsys.Message;
import atavism.msgsys.MessageAgent;
import atavism.msgsys.MessageTypeFilter;
import atavism.msgsys.ResponseMessage;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.Hook;
import atavism.server.engine.OID;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.plugins.InstanceClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.util.Log;
import atavism.server.util.Logger;

public class ChatPlugin extends EnginePlugin {

    /*
     * Properties
     */
    
    protected static final Logger _log = new Logger("ChatPlugin");
  //  protected static List<String> _registeredStats = new ArrayList<String>();
  //  protected static Map<OID, AgisGroup> _currentGroups = new Hashtable<OID, AgisGroup>();
   // protected static int _maxGroupSize = 4; // Default max group size to 4
  //  HashMap<OID, ScheduledFuture> tasks = new HashMap<OID, ScheduledFuture>();

    /*
     * Constructors
     */ 
    public ChatPlugin() {
        super("Chat");
        setPluginType("Chat");
    }

    public String GetName() {
        return "ChatPlugin";
    }

    /*
     * Events
     */ 
    
	public void onActivate() {
		super.onActivate();

		// register message hooks
		RegisterHooks();
	
		// setup message filters
		MessageTypeFilter filter = new MessageTypeFilter();
		filter.addType(AgisWorldManagerClient.MSG_TYPE_GLOBAL_CHAT);
		filter.addType(WorldManagerClient.MSG_TYPE_DESPAWNED);
	    filter.addType(WorldManagerClient.MSG_TYPE_SPAWNED);
	      
		//filter.addType(WorldManagerClient.MSG_TYPE_COM);
	    filter.addType(ChatClient.MSG_TYPE_COM_TARGET_REQ);
	    filter.addType(ChatClient.MSG_TYPE_COM_REQ);
		filter.addType(ChatClient.MSG_TYPE_SYS_CHAT);
		Engine.getAgent().createSubscription(filter, this);

		// setup responder message filters
		MessageTypeFilter responderFilter = new MessageTypeFilter();
		responderFilter.addType(GuildClient.MSG_TYPE_GET_PLAYER_IS_ONLINE);
	    responderFilter.addType(LoginMessage.MSG_TYPE_LOGIN);
		responderFilter.addType(LogoutMessage.MSG_TYPE_LOGOUT);
		Engine.getAgent().createSubscription(responderFilter, this, MessageAgent.RESPONDER);

		aDB = new AccountDatabase(true);
		loadData();
		if (Log.loggingDebug)
			_log.debug("ChatPlugin activated.");
		Engine.registerStatusReportingPlugin(this);
	}
	
	void loadData() {
		ContentDatabase cDB = new ContentDatabase(false);
		String logDb = cDB.loadGameSetting("CHAT_LOG_DB");
		if (logDb != null) {
			CHAT_LOG_DB = Boolean.parseBoolean(logDb);
			Log.debug("GameSetting: set LOG_DB: " + CHAT_LOG_DB);
		}
		String chatLogFile = cDB.loadGameSetting("CHAT_LOG_FILE");
		if (chatLogFile != null) {
			CHAT_LOG_FILE = Boolean.parseBoolean(chatLogFile);
			Log.debug("LOOT: Loot based on damage dealt set to: " + CHAT_LOG_FILE);
		}
	}

	protected void ReloadTemplates(Message msg) {
		Log.error("ChatPlugin ReloadTemplates Start");
			loadData();
			Log.error("ChatPlugin ReloadTemplates End");
	}

    /*
     * Methods
     */ 
    
	public void RegisterHooks() {
		getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_GLOBAL_CHAT, new InstanceChatHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_DESPAWNED, new DespawnedHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
		getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
		getHookManager().addHook(GuildClient.MSG_TYPE_GET_PLAYER_IS_ONLINE, new GetPlayerIsOnlineHook());
		getHookManager().addHook(ChatClient.MSG_TYPE_COM_REQ, new ComReqHook());
		getHookManager().addHook(ChatClient.MSG_TYPE_COM_TARGET_REQ, new ComTargetReqHook());

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
        	    playersInInstance.computeIfAbsent(world, __ -> ConcurrentHashMap.newKeySet()).add(objOid);
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
                playersInInstance.computeIfPresent(world, (__, players) -> {
                    players.remove(objOid);
                    return players;
                });
            }
            
            return true;
    	}
    }
    class GetPlayerIsOnlineHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    	    GuildClient.getPlayerIsOnlineMessage GPBNMsg = (GuildClient.getPlayerIsOnlineMessage) msg;
    	    OID oid = (OID) GPBNMsg.getProperty("plyOid");
    	    if(Log.loggingDebug)Log.debug("PROXY: getting player is online " + oid);
    	    boolean isonline = isPlayerOnline(oid);
    	    Engine.getAgent().sendBooleanResponse(msg, isonline);
    	    return true;
    	}
    }
  
    class GetPlayersOnlineHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    	    AgisMobClient.GetPlayersOnlineMessage GPBNMsg = (AgisMobClient.GetPlayersOnlineMessage) msg;
    	    OID instanceOid = GPBNMsg.getInstanceOid();
    	    if(Log.loggingDebug) Log.debug("PROXY: getting players is online");
    	    LinkedList<OID> plyOids = new LinkedList<OID>();
          	plyOids.addAll((Collection<? extends OID>) playersOnlineList.keys());
           
            Engine.getAgent().sendObjectResponse(msg, plyOids);
    	    return true;
    	}
    }
  
    
    public boolean isPlayerOnline(OID oid) {
    	if (playersOnlineList.containsKey(oid)) {
        		return true;
        }
    	return false;
    }
    /**
     * Hook receives ordinary chat messages and forwards them
     * @author Zbawca
     *
     */
    
	class ComReqHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ChatClient.ComReqMessage comReqMsg = (ChatClient.ComReqMessage) msg;

			if (Log.loggingDebug)
				Log.debug("ComReqHook: got com msg from " + comReqMsg.getSubject() + ", msg=" + comReqMsg.getString());
			//Log.error("Chat Message "+comReqMsg.getSubject()+", "+ comReqMsg.getChatterName()+", "+comReqMsg.getChannel()+", "+comReqMsg.getString());
			LogChat(comReqMsg.getSubject(),null,comReqMsg.getChannel(),comReqMsg.getString());
				// maybe later we can do some filtering but for now just rebroadcast it
			ChatClient.ComMessage comMsg = new ChatClient.ComMessage(comReqMsg.getSubject(), comReqMsg.getChatterName(), comReqMsg.getChannel(), comReqMsg.getString());
			Engine.getAgent().sendBroadcast(comMsg);
			return true;
		}
	}
	
	/**
	 * Function receives whisper messages and send it to the revicer
	 * @author Zbawca
	 *
	 */
	class ComTargetReqHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ChatClient.TargetedComReqMessage comReqMsg = (ChatClient.TargetedComReqMessage) msg;

			if (Log.loggingDebug)
				//Log.debug("ComTargetReqHook: got com msg from " + comReqMsg.getSubject() + ", msg=" + comReqMsg.getString());
			Log.debug("Chat Whisper Message "+comReqMsg.getSubject()+", "+ comReqMsg.getTarget()+" "+comReqMsg.getChatterName()+", "+comReqMsg.getChannel()+", "+comReqMsg.getString());
			LogChat(comReqMsg.getSubject(),comReqMsg.getTarget(),comReqMsg.getChannel(),comReqMsg.getString());
				// maybe later we can do some filtering but for now just rebroadcast it
			ChatClient.TargetedComMessage comMsg = new ChatClient.TargetedComMessage(comReqMsg.getSubject(), comReqMsg.getTarget(), comReqMsg.getChatterName(), comReqMsg.getChannel(), comReqMsg.getString());
			Log.debug("Chat Send TargetedComMessage ="+comMsg);
			Engine.getAgent().sendBroadcast(comMsg);
			return true;
		}
	}

   /* class ComHook implements Hook {
        public void processMessage(Message msg, int flags, Player player) {
            AOByteBuffer buf = null;
            
            if (msg instanceof ChatClient.ComMessage) {
                ChatClient.ComMessage comMsg = (ChatClient.ComMessage) msg;
                AccountDatabase aDB = new AccountDatabase(false);
                //int guildID = aDB.isOnBlackList(player.getOid(), oid)
            
                OID oid = comMsg.getSubject();
                Log.debug("ComHook.processMessage: ComMessage player:"+player+" plyOid:"+player.getOid()+" getSubject:"+oid+" comMsg:"+comMsg);
              //  if (player.oidIgnored(oid))
                if( SocialClient.isOnBlockList(player.getOid(),oid))
                //if(aDB.isOnBlackList(player.getOid(), oid))
                {
                    if (Log.loggingDebug)
                        Log.debug("ComHook.processMessage: Ignoring chat from player "
                                        + oid
                                        + " to player "
                                        + player.getOid()
                                        + " because originator is in the player's ignored list");
                   // aDB.close();
                    return;
                }
               // aDB.close();

                buf = comMsg.toBuffer();
                Log.info("ProxyPlugin: CHAT_RECV player=" + player + " from="
                        + comMsg.getSubject() + " private=false" + " msg=["
                        + comMsg.getString() + "]");
            } else if (msg instanceof ChatClient.TargetedComMessage) {
            	ChatClient.TargetedComMessage comMsg = (ChatClient.TargetedComMessage) msg;
                AccountDatabase aDB = new AccountDatabase(false);
                  OID oid = comMsg.getSubject();
                  Log.debug("ComHook.processMessage: TargetedComMessage player:"+player+" plyOid:"+player.getOid()+" getSubject:"+oid+" comMsg:"+comMsg);
                     //if (player.oidIgnored(oid))
              //  if(aDB.isOnBlackList(player.getOid(), oid))
                  if( SocialClient.isOnBlockList(player.getOid(),oid))
                      
                  {
                    if (Log.loggingDebug)
                        Log.debug("ComHook.processMessage: Ignoring chat from player "
                                        + oid
                                        + " to player "
                                        + player.getOid()
                                        + " because originator is in the player's ignored list");
                   // aDB.close();
                    return;
                }
               // aDB.close();
                buf = comMsg.toBuffer();
                Log.info("ProxyPlugin: CHAT_RECV player=" + player + " from="
                        + comMsg.getSubject() + " private=true" + " msg=["
                        + comMsg.getString() + "]");
            } else {
            	
            	   Log.debug("ComHook.processMessage: else player:"+player+" plyOid:"+player.getOid()+" msg "+msg);
                   
            	
                return;
            }
            ClientConnection con = player.getConnection();
            con.send(buf);
        }
    }
*/
    /*
     * Chat functions
     */
    public class InstanceChatHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
        	ExtensionMessage getMsg = (ExtensionMessage)msg;
    		OID playerOid = getMsg.getSubject();
    		int chatChannel = (Integer)getMsg.getProperty("channel");
    		String message = (String)getMsg.getProperty("message");
    		 if(Log.loggingDebug)Log.debug("CHAT: got chat message from: " + playerOid);
    		String plyName = aDB.getCharacterNameByOid(playerOid);
    		TargetedComMessage comMessage = new TargetedComMessage();
	        comMessage.setString(message);
	        comMessage.setChannel(chatChannel);
	        comMessage.setChatterName(plyName);
	   
	        //Log.error("Chat Message "+comMessage.getSubject()+", "+ comMessage.getChatterName()+", "+comMessage.getChannel()+", "+comMessage.getString());
			//ObjectInfo plyInfo = WorldManagerClient.getObjectInfo(playerOid);
	        int level = 0;
	        Serializable adminLevel = EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "adminLevel");
	        if(Log.loggingDebug)  Log.debug("CHAT: got chat message from: " + playerOid+"; adminLevel="+adminLevel);
	        if (adminLevel != null)
	        	level = (Integer)adminLevel;
	        //if (plyInfo != null)
	        //	level = (Integer)plyInfo.getProperty("adminLevel");
	        if(Log.loggingDebug) Log.debug("CHAT: got chat message from: " + playerOid+"; permLevel="+level);
			LogChat(playerOid,null,chatChannel,message);
	    	if (chatChannel == -2 && level>4) {
    			//Admin Global chat
    			for (Collection<OID> instancePlayers : playersInInstance.values()) {
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
    			for (Collection<OID> instancePlayers : playersInInstance.values()) {
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
    
    
    /**
     * Logic to handle group specific chat
     */
  /*  protected void HandleGroupChat(ExtensionMessage groupChatMsg) {
        String message = groupChatMsg.getProperty("message").toString();
        OID senderOid = groupChatMsg.getSubject();

        CombatInfo sender = CombatPlugin.getCombatInfo(senderOid);
        if (sender.isGrouped()) {
            String senderName =  WorldManagerClient.getObjectInfo(sender.getOwnerOid()).name;
            AgisGroup group = GetGroup(sender.getGroupOid());
            if(group == null){
                _log.error("GroupPlugin.HandleGroupChat - group is null");
                sender.setGroupMemberOid(null);
                sender.setGroupOid(null);
                return;
            }
            Collection<AgisGroupMember> groupMembers = group.GetGroupMembers().values();            
            //Send chat message to each group member
            for (AgisGroupMember groupMember : groupMembers) {
            	if (groupMember.GetMemberStatus() == AgisGroupMember.MEMBER_STATUS_OFFLINE) {
            		continue;
            	}
            	SendTargetedGroupMessage(groupMember.GetGroupMemberOid(), "[" + senderName + "]: " + message);
            }
        } else {
            SendTargetedGroupMessage(sender.getOwnerOid(), "You are not grouped!");
        }
    }
*/
    /**
     * Handles group chat messages from the client
     */
 /*   class GroupChatHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            ExtensionMessage groupChatMsg = (ExtensionMessage) msg;
            HandleGroupChat(groupChatMsg);
            return true;
        }
    }
*/
    /**
     * LoginHook is used to store the names of users currently logged in. It needs to be moved.
     * @author Andrew
     *
     */
class LoginHook implements Hook {
	public boolean processMessage(Message msg, int flags) {
		LoginMessage message = (LoginMessage) msg;
		OID playerOid = message.getSubject();
		OID instanceOid = message.getInstanceOid();
		CombatInfo subject = CombatPlugin.getCombatInfo(message.getSubject());
		String plyName = message.getPlayerName();
		if (playersOnlineList.containsKey(playerOid)) {
			Log.debug("LoginHook: playerOid=" + playerOid + " plyName=" + plyName + " instanceOid=" + instanceOid + " !!!! Player is on List !!!! ");
		} else {
			playersOnlineList.put(playerOid, message.getPlayerName());
			if (!playersOnlineOnProxy.containsKey(playerOid)) {
				playersOnlineOnProxy.put(playerOid, message.getProxy());
			} else {
				playersOnlineOnProxy.replace(playerOid, message.getProxy());
			}
		}
		Engine.getAgent().sendResponse(new ResponseMessage(message));
		return true;
	}
}
  
	/**
	 * LogOutHook is used to remove group members from a group who log out of the
	 * game.
	 */
	class LogoutHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LogoutMessage logoutMsg = (LogoutMessage) msg;
			OID playerOid = logoutMsg.getSubject();
			String plyName = logoutMsg.getPlayerName();
			if (Log.loggingDebug)
				Log.debug("LOGOUT: group logout started for: " + playerOid);
			if (playersOnlineList.containsKey(playerOid)) {
				playersOnlineList.remove(playerOid);
				if (playersOnlineOnProxy.containsKey(playerOid)) {
					playersOnlineOnProxy.remove(playerOid);

				}
			} else {
				Log.debug("LoginHook: playerOid=" + playerOid + " plyName=" + plyName + " !!!! Player is not on List !!!! ");
			}

			Engine.getAgent().sendResponse(new ResponseMessage(logoutMsg));
			if (Log.loggingDebug)
				Log.debug("LOGOUT: group logout finished for: " + playerOid);
			return true;
		}

	}

	private void LogChat(OID source, OID target,int channel,String message ) {
		Log.debug("ChatLog Start");
		
		if(CHAT_LOG_FILE) {
			  try {
				  Log.debug("ChatLog Write to file");
				  if (out == null) {
			            try {
			                out = new FileWriter(new File(LOGS_DIR + "/../ChatLog.txt"),true);
			            } catch (Exception e) {
			            }
				  }
				    String timeStamp = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new Date());
				    long _source =0l;
				    long _target =0l;
				    if(source!=null)
				    	_source = source.toLong();
				    if(target!=null)
				    	_target = target.toLong();
				    out.write(timeStamp+";"+Engine.getWorldName()+";"+channel+";"+_source+";"+_target+";"+message+"\n");
				    out.flush();
				    
					  Log.debug("ChatLog Write to file end");
			} catch (IOException  e) {
				Log.error("ChatPlugin: LogChat "+e.getLocalizedMessage());
			}
		}
		if(CHAT_LOG_DB) {
			if (aDB ==null)
				aDB = new AccountDatabase(true);
			aDB.LogChat(source , target, channel, message);
			
		}
		Log.debug("ChatLog End");
	}


    /**
     * Used by the /who command to get the set of player names from all Proxys.
     * 
     * @return The list of names for logged-in players .
     */
    public List<String> getAllPlayerNames() {
    	 if(Log.loggingDebug)Log.debug("ProxyPlugin.getAllPlayerNames: count is " + playersOnlineList.size());
        List<String> result = new ArrayList<String>(playersOnlineList.size());
        HashMap<OID,String> plays = new HashMap<OID,String>();
        plays.putAll(playersOnlineList);
        for (OID player : plays.keySet()) {
            result.add(plays.get(player)+" "+player+" "+playersOnlineOnProxy.get(player));
        }
        Collections.sort(result);
        return result;
    }
    protected static final String LOGS_DIR = System.getProperty("atavism.logs");
    protected static FileWriter out = null;

    AccountDatabase aDB ;
    protected ConcurrentHashMap<OID,String> playersOnlineList = new ConcurrentHashMap<OID,String>();
    protected ConcurrentHashMap<OID,String> playersOnlineOnProxy = new ConcurrentHashMap<OID,String>();
    private boolean CHAT_LOG_DB =false;
    private boolean CHAT_LOG_FILE =false;
    
    static Map<Integer, Set<OID>> playersInInstance = new ConcurrentHashMap<>();
    HashMap<OID, ScheduledFuture> tasks = new HashMap<OID, ScheduledFuture>();
	ArrayList<OID> playerOnlineOids = new ArrayList<OID>();

    
}
