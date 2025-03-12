package atavism.agis.plugins;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import atavism.agis.arenas.*;
import atavism.agis.database.AccountDatabase;
import atavism.agis.database.CombatDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.objects.*;
import atavism.agis.objects.ArenaQueue.QueueMember;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.msgsys.*;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.objects.Marker;
import atavism.server.plugins.*;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.plugins.WorldManagerClient.ObjectInfo;

/**
 * This class is responsible for catching the messages sent out by the ArenaClient 
 * then dealing with the data received appropriately. In particular, this class handles
 * the queuing for arenas and will create new arenas when enough players are queued.
 * @author Andrew Harrison
 *
 */
public class ArenaPlugin extends EnginePlugin {

	public ArenaPlugin() {
		super(ARENA_PLUGIN_NAME);
		setPluginType("Arena");
	}
	
	public String getName() {
		return ARENA_PLUGIN_NAME;
	}

	public static String ARENA_PLUGIN_NAME = "Arena";
	
	protected static final Logger log = new Logger("Arena");

	public void onActivate() {
		log.debug("ArenaPlugin.onActivate()");
		registerHooks();
		MessageTypeFilter filter = new MessageTypeFilter();
		filter.addType(ArenaClient.MSG_TYPE_LEAVE_ARENA);
		filter.addType(ArenaClient.MSG_TYPE_GET_ARENA_LIST);
		filter.addType(ArenaClient.MSG_TYPE_GET_ARENA_STATS);
		filter.addType(ArenaClient.MSG_TYPE_GET_ARENA_TYPES);
		filter.addType(ArenaClient.MSG_TYPE_JOIN_QUEUE);
		filter.addType(ArenaClient.MSG_TYPE_LEAVE_QUEUE);
		filter.addType(ArenaClient.MSG_TYPE_GROUP_JOIN_QUEUE);
		filter.addType(ArenaClient.MSG_TYPE_GROUP_LEAVE_QUEUE);
		filter.addType(ArenaClient.MSG_TYPE_SELECT_RACE);
		filter.addType(ArenaClient.MSG_TYPE_ARENA_KILL);
		filter.addType(ArenaClient.MSG_TYPE_RELEASE_REQUEST);
		filter.addType(ArenaClient.MSG_TYPE_ADD_CREATURE);
		filter.addType(ArenaClient.MSG_TYPE_REMOVE_PLAYER);
		filter.addType(ArenaClient.MSG_TYPE_PICKUP_FLAG);
		filter.addType(ArenaClient.MSG_TYPE_ACTIVATE_MACHINE);
		filter.addType(ArenaClient.MSG_TYPE_DOT_SCORE);
		filter.addType(ArenaClient.MSG_TYPE_START_ARENA_CHECK);
		filter.addType(ArenaClient.MSG_TYPE_END_ARENA);
		filter.addType(ArenaClient.MSG_TYPE_ACTIVATE_ARENA_ABILITY);
		filter.addType(ArenaClient.MSG_TYPE_COMPLETE_TUTORIAL);
		filter.addType(ArenaClient.MSG_TYPE_DUEL_ACCEPT_CHALLENGE);
		filter.addType(ArenaClient.MSG_TYPE_DUEL_CHALLENGE);
		filter.addType(ArenaClient.MSG_TYPE_DUEL_CHALLENGE_REMOVE);
		filter.addType(ArenaClient.MSG_TYPE_DUEL_DECLINE_CHALLENGE);
		filter.addType(ArenaClient.MSG_TYPE_DUEL_CHALLENGE_DISCONNECT);
		filter.addType(ArenaClient.MSG_TYPE_DUEL_START);
		filter.addType(ArenaClient.MSG_TYPE_DUEL_DEFEAT);
		filter.addType(ArenaClient.MSG_TYPE_DUEL_DISCONNECT);
		filter.addType(ArenaClient.MSG_TYPE_DUEL_REMOVE);
		filter.addType(ArenaClient.MSG_TYPE_ALTER_EXP);
		filter.addType(WorldManagerClient.MSG_TYPE_SPAWNED);
		filter.addType(WorldManagerClient.MSG_TYPE_DESPAWNED);
		filter.addType(AgisWorldManagerClient.MSG_TYPE_SERVER_TIME);
	       Engine.getAgent().createSubscription(filter, this);
		
		// Create responder subscription
	 	MessageTypeFilter filter2 = new MessageTypeFilter();
		filter2.addType(LoginMessage.MSG_TYPE_LOGIN);
		filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
		Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);
		
		 Log.debug("Registering arena plugin");
		Engine.registerStatusReportingPlugin(this);

	 	Log.debug("ARENA: completed Plugin activation");
	 	
       
	}

	protected void registerHooks() {
		getHookManager().addHook(ArenaClient.MSG_TYPE_LEAVE_ARENA, new LeaveArenaHook());
		getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_SERVER_TIME, new ServerTimeHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_GET_ARENA_LIST, new GetArenaListHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_GET_ARENA_STATS, new GetArenaStatsHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_GET_ARENA_TYPES, new GetArenaTypesHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_JOIN_QUEUE, new JoinQueueHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_GROUP_JOIN_QUEUE, new GroupJoinQueueHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_LEAVE_QUEUE, new LeaveQueueHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_GROUP_LEAVE_QUEUE, new GroupLeaveQueueHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_SELECT_RACE, new SkinSelectedHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_REMOVE_PLAYER, new RemovePlayerHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_ARENA_KILL, new ArenaDeathHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_RELEASE_REQUEST, new ArenaReleaseRequestHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_PICKUP_FLAG, new PickupFlagHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_DROP_FLAG, new DropFlagHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_ACTIVATE_MACHINE, new ActivateMachineHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_ADD_CREATURE, new ArenaCreatureAddedHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_END_ARENA, new ArenaEndHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_ACTIVATE_ARENA_ABILITY, new ActivateArenaAbilityHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_COMPLETE_TUTORIAL, new CompleteTutorialHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_DUEL_CHALLENGE, new DuelChallengeHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_DUEL_ACCEPT_CHALLENGE, new DuelAcceptHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_DUEL_DECLINE_CHALLENGE, new DuelDeclineHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_DUEL_CHALLENGE_DISCONNECT, new DuelChallengeDisconnectHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_DUEL_CHALLENGE_REMOVE, new DuelChallengeRemoveHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_DUEL_START, new DuelStartHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_DUEL_DEFEAT, new DuelDefeatHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_DUEL_DISCONNECT, new DuelDisconnectHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_DUEL_REMOVE, new DuelRemoveHook());
		getHookManager().addHook(ArenaClient.MSG_TYPE_ALTER_EXP, new AlterExpHook());

		getHookManager().addHook(WorldManagerClient.MSG_TYPE_DESPAWNED, new DespawnedHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
		// Hook to process login/logout messages
		getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());

		loadArenasFromDatabase();
	}
	
	public void loadArenasFromDatabase() {
    	CombatDatabase cDB = new CombatDatabase(false);
        ArrayList<ArenaTemplate> templates = cDB.loadArenaTemplates();
        for (ArenaTemplate tmpl: templates) {
        	addArenaTemplate(tmpl);
        	Log.debug("ARENA: added template: " + tmpl.getArenaName());
        }
        
        ArrayList<ArenaCategory> categories = cDB.loadArenaCategories();
        for (ArenaCategory category: categories) {
        	arenaCategories.put(category.getCategoryID(), category);
        }
      //  cDB.close();
        
        ContentDatabase coDB = new ContentDatabase(false);
		String worldTimeZone = coDB.loadGameSetting("WORLD_TIME_ZONE");
		if (worldTimeZone != null) {
			WORLD_TIME_ZONE = worldTimeZone;
			Log.debug("Loaded Game Setting WORLD_TIME_ZONE=" + WORLD_TIME_ZONE);
		}
		String duelDuration = coDB.loadGameSetting("DUEL_DURATION");
		if (duelDuration != null) {
			DUEL_DURATION = Integer.parseInt(duelDuration);
			Log.debug("Loaded Game Setting DUEL_DURATION=" + DUEL_DURATION);
		}
		String devMode = coDB.loadGameSetting("SERVER_DEVELOPMENT_MODE");
		if (devMode != null) {
			EnginePlugin.DevMode = Boolean.parseBoolean(devMode);
			log.info("Game Settings EnginePlugin.DevMode set to " + EnginePlugin.DevMode);
		}
		if(!Engine.isAIO())
			cDB.LoadStats();
	    
		//coDB.close();
	}

	
	protected void ReloadTemplates(Message msg) {
		Log.error("ArenaPlugin ReloadTemplates Start");
			loadArenasFromDatabase();
		Log.error("ArenaPlugin ReloadTemplates End");
	}
	  /**
     * Handles the ServerTimeMessage. Passes the server time to the spawn generator so it can enable or disable
     * any spawn generators that are affected by the change in time.
     * @author Andrew Harrison
     *
     */
    class ServerTimeHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    	    AgisWorldManagerClient.ServerTimeMessage tMsg = (AgisWorldManagerClient.ServerTimeMessage) msg;
    	    Log.debug("TIME: got server time message with hour: " + tMsg.getHour());
    	    serverTimeUpdate(tMsg.getHour(), tMsg.getMinute());
    	    return true;
    	}
    }
    
    public  void serverTimeUpdate(int wHour, int wMinute) {
    	hour = wHour;
    	minute = wMinute;
    }
	
	/**
	 * Sends down either the list of top players in the arena, or the players current stats, depending on 
	 * the value of the statsType property.
	 * TODO: Implement this properly
	 */
	class GetArenaStatsHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ArenaClient.getArenaStatsMessage getMsg = (ArenaClient.getArenaStatsMessage) msg;
			OID oid = getMsg.getSubject();
			int statsType = (Integer) getMsg.getProperty("statsType");
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			int arenaNum = 0;
			props.put("ext_msg_subtype", "Arena_Ratings");
			props.put("numArenaTypes", arenaNum);
			TargetedExtensionMessage EXTmsg = new TargetedExtensionMessage(
					WorldManagerClient.MSG_TYPE_EXTENSION, oid, 
					oid, false, props);
			Engine.getAgent().sendBroadcast(EXTmsg);
			
			Log.debug("STATS: statsMap: " + statsMap);
		    return true;
		}
	}
	
	/**
	 * The hook for the getArenaTypes message. This is called when someone wants to 
	 * see what arenas are available for them to join.
	 *
	 */
	class GetArenaTypesHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage getMsg = (ExtensionMessage) msg;
			OID oid = OID.fromLong((Long)getMsg.getProperty("playerOid"));
			int arenaType = (Integer) getMsg.getProperty("type");
			sendArenasInCategory(oid, arenaType);
		    return true;
		}
	}
	
	/**
	 * The hook for the getArenaList message. This is called when someone wants to 
	 * see what arenas are available for them to join.
	 *
	 */
	class GetArenaListHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage getMsg = (ExtensionMessage) msg;
			OID oid = OID.fromLong((Long)getMsg.getProperty("playerOid"));
			int arenaCat = (Integer) getMsg.getProperty("cat");
			sendArenasList(oid, arenaCat);
		    return true;
		}
	}
	
	private void sendArenasList(OID oid, int arenaCategory) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "Arena_List");
		int arenaID = -1;
		try {
        	arenaID = (Integer) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
        } catch (NullPointerException e) {
        	Log.warn("ARENA PLUGIN: player " + oid + " does not have an arenaID property");
        }
		   if (arenaID == -1) {
			    // Iterate through each queue and check if the player is in the queue
			//    Log.debug("ARENA PLUGIN: about to iterate through arena queues. Num arena types: " + arenaTemplates.size()
			 //   		+ " and requested arena category: " + arenaCategory);
			  for (int i=0;i<arenaTemplates.size();i++) {
				  props.put("arenaTemp" +i, arenaTemplates.get(i).getTypeID());
				  props.put("arenaType" +i, arenaTemplates.get(i).getArenaType());
				  props.put("arenaName"+i, arenaTemplates.get(i).getArenaName());
				  props.put("arenaLevel" +i, arenaTemplates.get(i).getLevelReq());
				  props.put("arenaMaxLevel" +i, arenaTemplates.get(i).getLevelMax());
				  props.put("arenaLenght" +i, arenaTemplates.get(i).getLength());
				  props.put("arenaDesc" +i, arenaTemplates.get(i).getDescription());
				  props.put("arenaWorld" + i, arenaTemplates.get(i).getInstanceTemplateID());
				  props.put("arenaQueued" +i,queues.get(arenaTemplates.get(i).getTypeID()).isPlayerInQueue(oid));
				  props.put("arenaStartMin" +i,arenaTemplates.get(i).getStartMinute());
				  props.put("arenaStartHour" +i,arenaTemplates.get(i).getStartHour());
				  props.put("arenaEndMin" +i,arenaTemplates.get(i).getEndMinute());
				  props.put("arenaEndHour" +i,arenaTemplates.get(i).getEndHour());
				  props.put("arenaNumTeams"+i, arenaTemplates.get(i).getNumTeams());
				  for (int j=0;j<arenaTemplates.get(i).getNumTeams();j++) {
					  props.put("arenaTeamSize"+i+"_"+j, arenaTemplates.get(i).getTeamSize(j));
				  }
				  
			  }
				props.put("numArena", arenaTemplates.size());
				TargetedExtensionMessage EXTmsg = new TargetedExtensionMessage(
						WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
				Engine.getAgent().sendBroadcast(EXTmsg);
				
		   }
		
	}
	
	
	
	
	
	/**
	 * Sends down an extension message to the requesting client with the list 
	 * of arena types they can queue up for.
	 * @param oid
	 * @param arenaType
	 */
	private void sendArenasInCategory(OID oid, int arenaCategory) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "Arena_Types");
		// First check to see if they are currently in an arena. If they are, there should not be
		// any available queues
		int arenaID = -1;
		try {
        	arenaID = (Integer) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
        } catch (NullPointerException e) {
        	Log.warn("ARENA PLUGIN: player " + oid + " does not have an arenaID property");
        }
        int numArenaTypes = 0;
        if (arenaID == -1) {
		    // Iterate through each queue and check if the player is in the queue
		    Log.debug("ARENA PLUGIN: about to iterate through arena queues. Num arena types: " + arenaTemplates.size()
		    		+ " and requested arena category: " + arenaCategory);
		    for (int i = 0; i < queues.size(); i++) {
			    ArenaQueue tempQueue = queues.get(i);
			    if (arenaCategory != -1 && tempQueue.getArenaCategory() != arenaCategory) {
			    	Log.debug("AJ: arenaType: " + tempQueue.getArenaCategory() + " was not equal to arenaType: " + arenaCategory);
			    	continue;
			    }
			    if (!tempQueue.isPlayerInQueue(oid)) {
				    props.put("arenaType" + numArenaTypes, i);
				    props.put("arenaName" + numArenaTypes, tempQueue.getArenaName());
				    props.put("arenaWorld" + numArenaTypes, arenaTemplates.get(i).getInstanceTemplateID());
				    numArenaTypes++;
			    }
		    }
        }
		props.put("numArenaTypes", numArenaTypes);
		TargetedExtensionMessage EXTmsg = new TargetedExtensionMessage(
				WorldManagerClient.MSG_TYPE_EXTENSION, oid, 
				oid, false, props);
		Engine.getAgent().sendBroadcast(EXTmsg);
	}
	
	/**
	 * The hook for the joinQueue message. This is called when someone wants to 
	 * join a queue for an arena.
	 *
	 */
	class JoinQueueHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage eMsg = (ExtensionMessage)msg;
    		OID oid = eMsg.getSubject();
    		int arenaType = (Integer) eMsg.getProperty("arenaType");
    		int arenaTemp = (Integer) eMsg.getProperty("arenaTemp");//Dragonsan
    		//String race = (String) eMsg.getProperty("raceSelected");
    		String name = WorldManagerClient.getObjectInfo(oid).name;
    		
			
			int arenaID = -1;
			try {
            	arenaID = (Integer) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
            } catch (NullPointerException e) {
            	Log.warn("ARENA PLUGIN: player " + oid + " does not have an arenaID property");
            }
            if (arenaID != -1) {
            	SendArenasQueued(oid);
    			// Lets get the server to re-send the list of arenas down
            	sendArenasInCategory(oid, arenaType);
    			return true;
            }
            int level = 1;
            try {
            	AgisStat lev = (AgisStat) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, "level");
            	Log.debug("ARENA: ply level : "+lev.toString());
            	level = lev.getCurrentValue();
            } catch (NullPointerException e) {
            	Log.warn("ARENA PLUGIN: player " + oid + " does not have an level property");
            }  
            
    		ArenaQueue queue = queues.get(arenaTemp);//Dragonsan
         
//          LinkedList<OID> members = GroupClient.GetGroupMembers(oid);
        //    Log.error("ARENA PLUGIN: WORLD_TIME_ZONE:"+WORLD_TIME_ZONE);
    		TimeZone tz = TimeZone.getTimeZone(WORLD_TIME_ZONE);
    	 	Calendar cal = Calendar.getInstance(tz);
    	 	//int hour = cal.get(Calendar.HOUR_OF_DAY);
		//	int minute = cal.get(Calendar.MINUTE);
    	 	//TODO FIXME Time Start Arena
		//	hour =this.hour;	
			int arenaLevelMin = -1;
			int arenaLevelMax = -1;
			for (int a = 0; a < arenaTemplates.size(); a++) {
				ArenaTemplate tmpl = arenaTemplates.get(a);
				if (arenaTemp == tmpl.getTypeID()) {
					arenaLevelMin = tmpl.getLevelReq();
					arenaLevelMax = tmpl.getLevelMax();
					if(tmpl.getStartHour() == 0 && tmpl.getStartMinute() == 0 && tmpl.getEndHour() == 0 && tmpl.getEndMinute() == 0 ) {
						
					}else if (tmpl.getStartHour() > hour || (tmpl.getStartHour() == hour && tmpl.getStartMinute() > minute)) {
						ChatClient.sendObjChatMsg(oid, 2, "You cannot join arena because arena not started");
						return false;
					} else if (tmpl.getEndHour() < hour || (tmpl.getEndHour() == hour && tmpl.getEndMinute() < minute)) {
						ChatClient.sendObjChatMsg(oid, 2, "You cannot join arena because arena not started");
						return false;
					}
				}
			}

			if (level < arenaLevelMin) {
				ChatClient.sendObjChatMsg(oid, 2, "You cannot join arena because your level is to low");
				return false;
			}
			if (level > arenaLevelMax) {
				ChatClient.sendObjChatMsg(oid, 2, "You cannot join arena because your level is to high");
				return false;
			}
       
            Log.debug("ARENA PLUGIN: JoinQueue  player " + oid + " is no group");
            // Log it
            HashMap<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("arenaType", arenaType);
            props.put("arenaTemp", arenaTemp);//Dragonsan
             OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
            DataLoggerClient.logData("PLAYER_JOINED_ARENA_QUEUE", oid, null, accountID, props);
			// Go through the ArrayList and add the players oid to each 
			// arena type they want to join.
			Log.debug("ARENA PLUGIN: adding player: " + oid + " to arenaType:" + arenaType+"; arenaTemp:"+arenaTemp);
			//ArenaQueue queue = queues.get(arenaType);
	
			boolean success = queue.addPlayer(oid, name);
			if (!success)
				ChatClient.sendObjChatMsg(oid, 2, "You cannot join arena: " + queue.getArenaName());
			SendArenasQueued(oid);
			// Lets get the server to re-send the list of arenas down
			//sendArenasInCategory(oid, arenaType);
			// Now check arenas
			  sendArenasList(oid, 1);
			  checkArenas();
			return true;
			
		}
	}
	
	/**
	 * The hook for the GroupJoinQueue message. This is called when someone wants to 
	 * join a queue for an arena.
	 *
	 */
	class GroupJoinQueueHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage eMsg = (ExtensionMessage)msg;
    		OID oid = eMsg.getSubject();
    		int arenaType = (Integer) eMsg.getProperty("arenaType");
    		int arenaTemp = (Integer) eMsg.getProperty("arenaTemp");//Dragonsan
    		//String race = (String) eMsg.getProperty("raceSelected");
    		String name = WorldManagerClient.getObjectInfo(oid).name;
    		
			int arenaID = -1;
			try {
            	arenaID = (Integer) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
            } catch (NullPointerException e) {
            	Log.warn("ARENA PLUGIN: player " + oid + " does not have an arenaID property");
            }
            if (arenaID != -1) {
            	SendArenasQueued(oid);
    			// Lets get the server to re-send the list of arenas down
            	sendArenasInCategory(oid, arenaType);
    			return true;
            }
    		ArenaQueue queue = queues.get(arenaTemp);//Dragonsan
    //		Log.debug("ARENA PLUGIN: JoinQueue  player " + oid + " is in groupID "+groupID+" |arenaID "+arenaID);
            AgisGroup group = null;
            LinkedList<OID> members = GroupClient.GetGroupMembers(oid);
            Log.debug("ARENA PLUGIN: JoinQueue  player " + oid + ";  group members "+members+" ");
            if (members != null) {
            		if (members.size()>0) {
            			LinkedList<String> membersName = new LinkedList<String>();
            			for (OID groupMember : members) {
            				membersName.add(WorldManagerClient.getObjectInfo(groupMember).name);
            			}  	
            			int teamSize=0;
            			int arenaLevelMin = -1;
            			int arenaLevelMax = -1;
                			for (int a = 0; a < arenaTemplates.size(); a++) {
            				ArenaTemplate tmpl = arenaTemplates.get(a);
            				if (arenaTemp == tmpl.getTypeID()){
            					arenaLevelMin = tmpl.getLevelReq();
            					arenaLevelMax = tmpl.getLevelMax();
            					for (int i =0;i<tmpl.getNumTeams();i++) {
            						if (members.size()>tmpl.getTeamSize(i)) {
            							ChatClient.sendObjChatMsg(oid, 2, "You cannot join arena " + queue.getArenaName()+" because number group member is to large");
                        				return true;	
            						}
            					}
            				}            		 
            			}
                			int inqueue=0;
                			int outLevel=0;
                			
            			  for (OID groupMember : members) {
            	    		  if (queue.isPlayerInQueue(groupMember)) {
            	    			  Log.warn("ARENA QUEUE: player " + groupMember + " is already in this queue");
            	    			  ChatClient.sendObjChatMsg(oid, 2, "Group cannot join arena " + queue.getArenaName()+" because  member "+WorldManagerClient.getObjectInfo(groupMember).name+" is in Queue");
            	    			inqueue++;
            	    		  }else {
            	    			  int level = 1;
            	    	            try {
            	    	            	AgisStat lev = (AgisStat) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, "level");
            	    	            	Log.debug("ARENA: ply level : "+lev.toString());
            	    	            	level = lev.getCurrentValue();
            	    	            } catch (NullPointerException e) {
            	    	            	Log.warn("ARENA PLUGIN: player " + oid + " does not have an level property");
            	    	            }  
            	    	            if (level < arenaLevelMin) {
            	    	            	ChatClient.sendObjChatMsg(oid, 2, "Group cannot join arena because  member "+WorldManagerClient.getObjectInfo(groupMember).name+" have to low level");
            	    	            	ChatClient.sendObjChatMsg(groupMember, 2, "You cannot join arena because your level is to low");
            	    					outLevel++;
            	    			  }
            	    			  if (level > arenaLevelMax) {
            	    				  ChatClient.sendObjChatMsg(oid, 2, "Group cannot join arena because  member "+WorldManagerClient.getObjectInfo(groupMember).name+" have to high level");
            	    				  ChatClient.sendObjChatMsg(groupMember, 2, "You cannot join arena because your level is to high");
            	    					outLevel++;
            	    			  }
            	    		  }
            	    	  }
            			  if (inqueue>0) return false;
            			  if (outLevel>0) {
            				  ChatClient.sendObjChatMsg(oid, 2, "Group not join arena queue because  some member have to high or low level");
            				  return false;
            			  }
            			Log.debug("ARENA PLUGIN: JoinQueue  group members " + members + " "+membersName+" add group to queue");
            			
            			queue.addGroup(members,membersName);
            			  sendArenasList(oid, 1);

            		
            			checkArenas();
            			
            			return true;
            			}
            }
            ChatClient.sendObjChatMsg(oid, 2, "You cannot join arena: " + queue.getArenaName());
			SendArenasQueued(oid);
			// Lets get the server to re-send the list of arenas down
			//sendArenasInCategory(oid, arenaType);
			// Now check arenas
		  sendArenasList(oid, 1);

			checkArenas();

			return true;
			
		}
	}
	
	
	/**
	 * The hook for the leaveQueue message. This is called when someone wants to 
	 * leave a queue they have previously joined.
	 *
	 */
	class LeaveQueueHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage leaveMsg = (ExtensionMessage) msg;
			OID oid = leaveMsg.getSubject();//Dragonsan
		//	OID oid = OID.fromLong((Long)leaveMsg.getProperty("playerOid"));
			int arenaType = (Integer) leaveMsg.getProperty("arenaType");
			int arenaTemp = (Integer) leaveMsg.getProperty("arenaTemp");
			Log.debug("QUEUE: player " + oid + " is leaving queue type: " + arenaType+"; arenaTemp:"+arenaTemp);
			//ArenaQueue queue = queues.get(arenaType);
			ArenaQueue queue = queues.get(arenaTemp);
			queue.removePlayer(oid);
			SendArenasQueued(oid);
			//sendArenaTypes(oid, arenaType);
			// Log it
            HashMap<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("arenaType", arenaType);
            props.put("arenaTemp", arenaTemp);
              OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
            DataLoggerClient.logData("PLAYER_LEFT_ARENA_QUEUE", oid, null, accountID, props);
            sendArenasList(oid, 1);
			return true;
		}
	}
	
	
	/**
	 * The hook for the leaveQueue message. This is called when someone wants to 
	 * leave a queue they have previously joined.
	 *
	 */
	class GroupLeaveQueueHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage leaveMsg = (ExtensionMessage) msg;
			OID oid = leaveMsg.getSubject();//Dragonsan
		//	OID oid = OID.fromLong((Long)leaveMsg.getProperty("playerOid"));
			int arenaType = (Integer) leaveMsg.getProperty("arenaType");
			int arenaTemp = (Integer) leaveMsg.getProperty("arenaTemp");
			//ArenaQueue queue = queues.get(arenaType);
			ArenaQueue queue = queues.get(arenaTemp);
			queue.removePlayer(oid);
		    AgisGroup group = null;
		    LinkedList<OID> members = GroupClient.GetGroupMembers(oid);
		    if (members != null) {
		    	if (members.size()>0) {
		    		LinkedList<String> membersName = new LinkedList<String>();
		            for (OID groupMember : members) {
		            	Log.debug("QUEUE: player " + groupMember + " is leaving queue type: " + arenaType+"; arenaTemp:"+arenaTemp);
		            	queue.removePlayer(groupMember);
		            	ChatClient.sendObjChatMsg(groupMember, 2, "You leave Queue of arena " + queue.getArenaName());
		       			sendArenasList(groupMember, 1);

		            }  	
		    	}
		    }
			SendArenasQueued(oid);
			//sendArenaTypes(oid, arenaType);
			// Log it
            HashMap<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("arenaType", arenaType);
            props.put("arenaTemp", arenaTemp);
              OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
            DataLoggerClient.logData("PLAYER_LEFT_ARENA_QUEUE", oid, null, accountID, props);
            sendArenasList(oid, 1);
            return true;
		}
	}
	
	
	/**
	 * The hook for the skinSelected message. This is called when someone has chosen what skin
	 * they want to use at the setup stage on an arena.
	 *
	 */
	class SkinSelectedHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage eMsg = (ExtensionMessage) msg;
			OID oid = eMsg.getSubject();
			String skin = (String) eMsg.getProperty("skin");
			int arenaID = -1;
			try {
			    arenaID = (Integer) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
			} catch(NullPointerException e) {
				return true;
			}
			if (arenaID != -1) {
				if (arenas.containsKey(arenaID)) {
					Arena a = arenas.get(arenaID);
				    int categoryID = a.getArenaCategory();
				    // First check if the skin is in the category
				    ArenaCategory category = getArenaCategory(categoryID);
				    if (category.getSkins().contains(skin)) {
				    	sendChangePlayerRaceMessage(oid, skin, true);
				    	return true;
				    }
				    if (AgisInventoryClient.getAccountSkins(oid).contains(skin)) {
				    	sendChangePlayerRaceMessage(oid, skin, true);
				    	return true;
				    }
				}
			}
			return true;
		}
	}
	
	public static void sendChangePlayerRaceMessage(OID oid, String race, boolean temporary) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
    	props.put("raceToChangeTo", race);
    	props.put("temporary", temporary);
    	ExtensionMessage eMessage = new ExtensionMessage(ArenaClient.MSG_TYPE_CHANGE_RACE, oid, props);
    	Engine.getAgent().sendBroadcast(eMessage);
	}

	/**
	 * The hook for the arenaKill message. This is called when one player has killed
	 * another. 
	 *
	 */
	class ArenaDeathHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ArenaClient.arenaKillMessage gdMsg = (ArenaClient.arenaKillMessage) msg;
			OID killer = (OID) gdMsg.getProperty("killer");
			OID victim = (OID) gdMsg.getProperty("victim");
			int arenaID = -1;
			try {
			    arenaID = (Integer) EnginePlugin.getObjectProperty(killer, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
			} catch(NullPointerException e) {
				log.debug("Arena Get Death: killer:"+killer+" victim:"+victim +" Cant get arena id "+e);
				return true;
			}
			Log.debug("ARENA: got death hook with arena ID: " + arenaID);
			if (arenaID != -1) {
				Log.debug("ARENA: death hook arena map: " + arenas);
				if (arenas.containsKey(arenaID)) {
					Log.debug("ARENA: death hook arena found in normal Arenas");
					Arena a = arenas.get(arenaID);
				    //a.addKillScore(killer, victim);
				    a.handleDeath(killer, victim);
				}
			    
			}
			return true;
		}
	}
	
	class ArenaReleaseRequestHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage eMsg = (ExtensionMessage)msg;
			OID oid = eMsg.getSubject();
    		int arenaID = (Integer) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
    		Log.debug("ARENA: getting arena: " + arenaID + " from map: " + arenas);
    		if (arenaID != -1) {
				Log.debug("ARENA: death hook arena map: " + arenas);
				if (arenas.containsKey(arenaID)) {
					Log.debug("ARENA: death hook arena found in normal Arenas");
					Arena a = arenas.get(arenaID);
					a.releaseRequest(oid);
				}
			    
			}
			return true;
		}
	}
	
	/**
	 * The Hook for when a player has clicked on a flag.
	 */
	class PickupFlagHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage eMsg = (ExtensionMessage)msg;
    		OID oid = OID.fromLong((Long)eMsg.getProperty("playerOid"));
    		int team = (Integer)eMsg.getProperty("team");
    		int arenaID = (Integer) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
    		Log.debug("FLAG: getting arena: " + arenaID + " from map: " + arenas);
            CaptureTheFlagArena a = (CaptureTheFlagArena) arenas.get(arenaID);
			a.pickupFlag(oid, team);
			return true;
		}
	}
	
	/**
	 * The Hook for when a player wants to drop the flag they are carrying.
	 */
    class DropFlagHook implements Hook {
        public boolean processMessage(atavism.msgsys.Message msg, int flags) {
        	ExtensionMessage eMsg = (ExtensionMessage)msg;
    		OID oid = OID.fromLong((Long)eMsg.getProperty("playerOid"));
    		int arenaID = (Integer) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
    		Log.debug("FLAG: getting arena: " + arenaID + " from map: " + arenas);
            CaptureTheFlagArena a = (CaptureTheFlagArena) arenas.get(arenaID);
			a.dropFlag(oid);
            return true;
        }
    }
    
    /**
	 * The Hook for when a player has clicked on a machine in an arena to activate it.
	 */
    class ActivateMachineHook implements Hook {
        public boolean processMessage(atavism.msgsys.Message msg, int flags) {
        	ExtensionMessage eMsg = (ExtensionMessage)msg;
    		OID oid = OID.fromLong((Long)eMsg.getProperty("playerOid"));
    		int machineID = (Integer)eMsg.getProperty("machineID");
    		int arenaID = (Integer) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
    		Log.debug("MACHINE: getting arena: " + arenaID + " from map: " + arenas);
            Arena a = arenas.get(arenaID);
			a.activateMachine(oid, machineID);
            return true;
        }
    }
	
	/**
	 * The hook for when a creature has been spawned in the arena. Not currently used.
	 *
	 */
	class ArenaCreatureAddedHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ArenaClient.addArenaCreatureMessage gdMsg = (ArenaClient.addArenaCreatureMessage) msg;
			OID creatureOid = (OID) gdMsg.getProperty("creatureOid");
			int arenaID = (Integer) gdMsg.getProperty("arenaID");
			Log.debug("ARENA: got arenaCreatureAdded message with oid: " + creatureOid + " and arenaID: " + arenaID);
			if (arenaID != -1) {
				Log.debug("ARENA: creature added hook challenge arena map: " + arenas);
				Arena a = arenas.get(arenaID);
			    //a.addArenaCreature(creatureOid);
			}
			return true;
		}
	}
	 /**
		 * The hook for when players click leave. This will remove the player from
		 * any arenas and queues they are in.
		 *
		 */
	    class LeaveArenaHook implements Hook {
	        public boolean processMessage(Message msg, int flags) {
	        	ExtensionMessage leaveMsg = (ExtensionMessage) msg;  
	            OID playerOid = leaveMsg.getSubject();
	            //Log.debug("LogoutHook: playerOid=" + playerOid);
	            Log.debug("ARENA: arena click leave : " + playerOid);
	            
	            // Remove the player from any queues they may have been in
	       /*     for (int k = 0; k < queues.size(); k++) {
	            	if (queues.get(k) != null)
					queues.get(k).removePlayer(playerOid);
				}
				*/
	            // Remove the player from any arenas they might have been in
	           /* for (Arena arena : arenas.values()) {
	            	Log.debug("ARENA: trying to remove player " + playerOid + " from arena " + arena.getArenaID());
	            	arena.removePlayer(playerOid, true);
	            }*/
	        	int arenaID = (Integer) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
				if (arenas.containsKey(arenaID)) {
					Arena a = arenas.get(arenaID);
				    a.removePlayer(playerOid, true);
				}
	         //   statsMap.remove(playerOid);
	            //TODO: search through all duels/arenas etc. for the player that has just logged out
	            
	           // Engine.getAgent().sendResponse(new ResponseMessage(message));
	            Log.debug("ARENA: arena leave click finished for: " + playerOid);
	            return true;
	        }
	    }
	    
    /**
     * The hook for the removePlayer message. This gets the arena the player is currently
     * in and then removes them from it.
     *
     */
	class RemovePlayerHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ArenaClient.removePlayerMessage removeMsg = (ArenaClient.removePlayerMessage) msg;
			OID oid = removeMsg.getSubject();
			// Get the players arenaID then get that arena. Then call remove player in
			// the arena object.
			int arenaID = (Integer) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
			if (arenas.containsKey(arenaID)) {
				Arena a = arenas.get(arenaID);
			    a.removePlayer(oid, true);
			}
			return true;
		}
	}
	
	/**
	 * The hook for the endArenaMessage. Removes the arena object from the map of 
	 * Arenas.
	 *
	 */
	class ArenaEndHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ArenaClient.endArenaMessage removeMsg = (ArenaClient.endArenaMessage) msg;
			
			int arenaID = removeMsg.getArenaID();
			if (arenas.containsKey(arenaID)) {
				arenas.remove(arenaID);
			}
			
			Log.debug("ARENA PLUGIN: removed arena: " + arenaID + " from the Map of Arenas");
		    return true;
		}
	}
	
	/**
	 * The Hook for when a player is wanting to activate an ability.
	 *
	 */
	class ActivateArenaAbilityHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage eMsg = (ExtensionMessage)msg;
    		OID playerOid = eMsg.getSubject();
    		OID targetOid = OID.fromLong((Long)eMsg.getProperty("targetOid"));
    		int slot = (Integer) eMsg.getProperty("slot");
    		int arenaID = (Integer) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
    		Arena a = arenas.get(arenaID);
			a.activateAbility(playerOid, targetOid, slot);
		    return true;
		}
	}
	
	/**
	 * The hook for the completion of the Arena Tutorial.
	 *
	 */
	class CompleteTutorialHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage eMsg = (ExtensionMessage)msg;
    		OID playerOid = OID.fromLong((Long)eMsg.getProperty("playerOid"));
    		int arenaID = (Integer) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
    		Arena a = arenas.get(arenaID);
    		a.completeTutorial(playerOid);
    		// Now teleport the user to Ghost island
    		OID defaultInstanceOid = InstanceClient.getInstanceOid("Ghost Island");
    		Marker defaultMarker;
        	defaultMarker = InstanceClient.getMarker(defaultInstanceOid, "spawn");
        	BasicWorldNode defaultLoc = new BasicWorldNode();
    		defaultLoc.setInstanceOid(defaultInstanceOid);
    		defaultLoc.setLoc(defaultMarker.getPoint());
    		defaultLoc.setOrientation(defaultMarker.getOrientation());
    		AOVector dir = new AOVector();
    		defaultLoc.setDir(dir);
    		InstanceClient.objectInstanceEntry(playerOid, defaultLoc, InstanceClient.InstanceEntryReqMessage.FLAG_NONE);
    		//EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "world", "Ghost Island");
		    return true;
		}
	}

	class SpawnedHook implements Hook {
		@Override
		public boolean processMessage(Message msg, int arg1) {
			// TODO Auto-generated method stub
			WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;
			OID objOid = spawnedMsg.getSubject();
			if (spawnedMsg.getType() == null || !spawnedMsg.getType().isPlayer())
				return true;
			if(Log.loggingDebug)log.debug("ArenaPlugin: SpawnedHook: oid "+objOid+" instance="+spawnedMsg.getInstanceOid());
			int arenaID = -1;
			try {
				arenaID = (Integer) EnginePlugin.getObjectProperty(objOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
			} catch (NullPointerException e) {
			} catch (NoRecipientsException e) {
			}
			if(Log.loggingDebug)log.debug("ArenaPlugin: SpawnedHook: oid "+objOid+" arenaId="+arenaID+" count of arena "+arenas.size());
			if(arenaID == -1){
				if(Log.loggingDebug)log.debug("ArenaPlugin: SpawnedHook: oid "+objOid+" adena id -1 END");
				return true;
			}
			Arena arena = arenas.get(arenaID);
			if(arena==null){
				if(Log.loggingDebug)log.debug("ArenaPlugin: SpawnedHook: oid "+objOid+" arena not found");
			}else {
				if (arena.getArenaInstanceOid().equals(spawnedMsg.getInstanceOid())) {
					if(Log.loggingDebug)log.debug("ArenaPlugin: SpawnedHook: oid " + objOid + " same instance");
					if( arena.isActivePlayer(objOid)) {
						if(Log.loggingDebug)log.debug("ArenaPlugin: SpawnedHook: oid " + objOid + " Active Player END");
						return true;
					}
				} else {
					if(Log.loggingDebug)log.debug("ArenaPlugin: SpawnedHook: oid " + objOid + " not same instance");
				}
			}
			AgisWorldManagerClient.returnToLastInstance(objOid);
			GroupClient.removeMember(objOid);
			EnginePlugin.setObjectProperty(objOid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, "");
			EnginePlugin.setObjectProperty(objOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID, -1);
			Map<String, Serializable> wmgrprops = new HashMap<String,Serializable>();
			wmgrprops.put(WorldManagerClient.WORLD_PROP_NOMOVE, false);
			wmgrprops.put(WorldManagerClient.WORLD_PROP_NOTURN, false);
			EnginePlugin.setObjectProperties(objOid, WorldManagerClient.NAMESPACE, wmgrprops);
			if(Log.loggingDebug)log.debug("ArenaPlugin: SpawnedHook: oid "+objOid+" END");
			return true;
		}
	}

	/**
	 * The Hook for when an object has been despawned. Used for duels to cancel any duels the player
	 * was in when they despawned (either from changing instance or logging out).
	 * @author Andrew
	 *
	 */
	class DespawnedHook implements Hook	 {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.DespawnedMessage despawnedMsg = (WorldManagerClient.DespawnedMessage) msg;
			OID objOid = despawnedMsg.getSubject();
			// Only run the duel check for players
			if (despawnedMsg == null || !despawnedMsg.getType().isPlayer()) {
				return true;
			}
			if(Log.loggingDebug)log.debug("ArenaPlugin: DespawnedHook: oid "+objOid+" instance="+despawnedMsg.getInstanceOid());
			// Now check Duel Challenges
            int challengeID = -1;
            try {
            	challengeID = (Integer) EnginePlugin.getObjectProperty(objOid, WorldManagerClient.NAMESPACE, "duelChallengeID");
            } catch (NoRecipientsException e) {
            } catch (NullPointerException e) {
            }
            if (challengeID != -1) {
            	EnginePlugin.setObjectProperty(objOid, WorldManagerClient.NAMESPACE, "duelChallengeID", -1);
            	DuelChallenge challenge = duelChallenges.get(challengeID);
				if (challenge != null)
					challenge.playerDeclined(objOid);
		     }
            
            // And now the Duel ID
            int duelID = -1;
            try {
            	duelID = (Integer) EnginePlugin.getObjectProperty(objOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DUEL_ID);
            } catch (NoRecipientsException e) {
            } catch (NullPointerException e) {
            }
            if (duelID != -1) {
				if(Log.loggingDebug)Log.debug("DUEL: got player despawned in duel: " + duelID);
            	EnginePlugin.setObjectProperty(objOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DUEL_ID, -1);
            	Duel d = duels.get(duelID);
    			//d.disconnectedPlayer(objOid, WorldManagerClient.getObjectInfo(objOid).name);
            	ObjectInfo oi =  WorldManagerClient.getObjectInfo(objOid);
            	if(oi == null) {
            		log.error("Cant Get ObjectInfo from wm for "+objOid);
            		d.disconnectedPlayer(objOid,"");
            	}else
            		d.disconnectedPlayer(objOid,oi.name);

            }
			return true;
		}
	}
	
	/**
	 * The hook for when players login. This will reset their arenaID (in case there was a 
	 * server crash) and teleport them back to the original world.
	 *
	 */
    class LoginHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LoginMessage message = (LoginMessage) msg;
            OID playerOid = message.getSubject();
            OID instanceOid = message.getInstanceOid();
			if(Log.loggingDebug)  Log.debug("ArenaLoginHook: playerOid=" + playerOid + " instanceOid=" + instanceOid);
            int arenaID = -1;
            try {
                arenaID = (Integer) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID);
            } catch (NullPointerException e) {
            }
			if(Log.loggingDebug) log.debug("LoginHook: arenaId "+arenaID);
            
            // Now check Duel Challenges
            int challengeID = -1;
            try {
            	challengeID = (Integer) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "duelChallengeID");
            } catch (NullPointerException e) {
            }
			if(Log.loggingDebug)log.debug("LoginHook: challengeID "+challengeID);
            if (challengeID != -1) {
				EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "duelChallengeID", -1);
				EnginePlugin.setObjectProperty(playerOid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, "");
			}
            // Reset core properties.
            EnginePlugin.setObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DUEL_ID, -1);
            EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "busy", false);
            //EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "state", 0);
            //EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "hunger", 0);
            
            // Load the players arena stats from the database for quick access.
            //TODO: Re-enable this later on
            /*ArenaDatabase aDB = new ArenaDatabase();
            ArenaStats arenaStats = aDB.loadArenaStats(playerOid, WorldManagerClient.getObjectInfo(playerOid).name);
            statsMap.put(playerOid, arenaStats);
            arenaStats.sendArenaStatUpdate();*/

            Engine.getAgent().sendResponse(new ResponseMessage(message));
            return true;
        }
    }
   
	/**
	 * The hook for when players logout (or disconnect). This will remove the player from
	 * any arenas and queues they are in.
	 *
	 */
    class LogoutHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LogoutMessage message = (LogoutMessage) msg;
            OID playerOid = message.getSubject();
            //Log.debug("LogoutHook: playerOid=" + playerOid);
			if(Log.loggingDebug) Log.debug("LOGOUT: arena logout started for: " + playerOid);
            
            // Remove the player from any queues they may have been in
            Set<Integer> keys = queues.keySet();
            for(Integer k :  keys){
            	if (queues.get(k) != null) {
					if(Log.loggingDebug)Log.debug("LOGOUT: arena logout queue remove player "+k );
                	queues.get(k).removePlayer(playerOid);
            	}
            }
/*            for (int k = 0; k < queues.size(); k++) {
            	if (queues.get(k) != null) {
            		Log.error("LOGOUT: arena logout queue remove player "+k );
                	queues.get(k).removePlayer(playerOid);
                	}
			}*/
            // Remove the player from any arenas they might have been in
            for (Arena arena : arenas.values()) {
				if(Log.loggingDebug)	Log.debug("ARENA: trying to remove player " + playerOid + " from arena " + arena.getArenaID());
            	arena.removePlayer(playerOid, false);
            }
            
            statsMap.remove(playerOid);
            //TODO: search through all duels/arenas etc. for the player that has just logged out
            
            Engine.getAgent().sendResponse(new ResponseMessage(message));
			if(Log.loggingDebug) Log.debug("LOGOUT: arena logout finished for: " + playerOid);
            return true;
        }
    }
    
    /**
     * Checks all queues to see if there are any that have enough players in them to start an arena.
     * Called when a player has joined an arena queue.
     */
    public void checkArenas() {
		if(Log.loggingDebug)Log.debug("ARENA: arena check; num templates:" + arenaTemplates.size());
		if (arenaTemplates.size() == 0)
			return;
		boolean testError = false; 
		// First find out how many players are needed for the template
		for (int a = 0; a < arenaTemplates.size(); a++) {
			testError =false;
			ArenaTemplate tmpl = arenaTemplates.get(a);
			//ArenaQueue queue = queues.get(tmpl.getArenaType());
			ArenaQueue queue = queues.get(tmpl.getTypeID());//Dragonsan
			if(Log.loggingDebug)Log.debug("QUEUE: Doing another arenaCheck - arena: " + tmpl.getArenaName()
				+ ". playersQueued: " + queue.getNumPlayersInQueue());
			if (queue.isQueueReady()) {
				Log.debug("ARENA: arena check 0");
				ArrayList<QueueMember> members[] = queue.getPlayersQueued();
				Log.debug("ARENA: arena check 1");
				int numTeams = tmpl.getNumTeams();
				for (int i = 0; i < numTeams; i++) {
					for (int j = 0; j < tmpl.getTeamSize(i); j++) {
						QueueMember member = null;
						if(Log.loggingDebug)	log.debug("ARENA: checkArena mambers i:"+i+" j:"+j+" members "+members[i]);
						if (members[i] != null) {
							if(Log.loggingDebug)	log.debug("ARENA: checkArena mambers not null i:" + i + " j:" + j + " members " + members[i].size());
						}
						if (members[i] != null && members[i].size() >= j )
							member = members[i].get(j);
						if (member!=null) {
						// Log it
						HashMap<String, Serializable> props = new HashMap<String, Serializable>();
						props.put("arenaType", tmpl.getArenaType());
						OID accountID = (OID) EnginePlugin.getObjectProperty(member.getOid(), WorldManagerClient.NAMESPACE, "accountId");
						DataLoggerClient.logData("PLAYER_JOINED_ARENA", member.getOid(), null, accountID, props);
						}else {
							//members;
							log.debug("ARENA: Remove death client from queue !!!");
							testError =true;
						}
					}
				}
				if(testError)
					continue;
				Log.debug("ARENA: arena check 2");
				createNewArena(tmpl, numTeams, members);
				// Log it
				HashMap<String, Serializable> props = new HashMap<String, Serializable>();
				//props.put("players", playerOids);
				props.put("arenaName", tmpl.getArenaName());
				DataLoggerClient.logData("ARENA_STARTED", null, null, null, props);
			}
		}
		// Move to the next template
		arenaCheckType = (arenaCheckType + 1) % arenaTemplates.size();
    }
	
	/**
	 * Creates a new Arena instance
	 * @param tmpl
	 * @param numTeams
	 * @param members
	 * @return
	 */
	private static boolean createNewArena(ArenaTemplate tmpl, int numTeams, 
			ArrayList<QueueMember> members[]) {
		int id = getNextArenaID();
		Log.debug("ARENA: createNewArena");
		int arenaID = tmpl.getTypeID();
		int arenaGameType = tmpl.getArenaType();
		String arenaName = tmpl.getArenaName();
		int duration = tmpl.getLength();
		int[] goals = tmpl.getTeamGoals();
		String[] teamNames = tmpl.getTeamNames();
		Point[] spawnPoints = tmpl.getSpawnPoints();
		int[] minPlayers = tmpl.getTeamSizes();
		Log.debug("ARENA: arena check 4");
		int victoryCondition = tmpl.getVictoryCondition();
		int instanceTemplateID = tmpl.getInstanceTemplateID();
		HashMap<Integer, Integer> victoryPayment = tmpl.getVictoryPayment();
		HashMap<Integer, Integer> defeatPayment = tmpl.getDefeatPayment();
		int victoryExp = tmpl.getVictoryExp();
		int defeatExp = tmpl.getDefeatExp();
		boolean useWeapons = tmpl.getUseWeapons();
		Log.debug("ARENA: arena check 5");
		Arena newArena;
		// Remove players from all queues first
		ArrayList<OID> playersToRemove = new ArrayList<OID>();
		for (int i = 0; i < numTeams; i++) {
			for (int j = 0; j < members[i].size(); j++) {
				if(Log.loggingDebug)Log.debug("QUEUE: adding player: " + members[i].get(j).getName() + " with oid: " + members[i].get(j).getOid() + " to list of players to be removed from queues");
				OID memberOid = members[i].get(j).getOid();
				playersToRemove.add(memberOid);
			}
		}
		/*for (int i = 0; i < numTeams; i++) {
			Log.error("QUEUE: removing players from team: " + i + " with num members: " + members[i].size());
			for (int j = 0; j < members[i].size(); j++) {
				Log.error("QUEUE: removing player: " + members[i].get(j).getName());
				OID memberOid = members[i].get(j).getOid();
				for (int k = 0; k < queues.size(); k++) {
					Log.error("QUEUE: removing player: " + members[i].get(j).getName() + " from queue: " + queues.get(k).getArenaName());
					queues.get(k).removePlayer(memberOid);
				}
			}
		}*/
		
		// Now create arena based on type
		Log.debug("ARENA: createNewArena Create arena");
		if (tmpl.getArenaType() == DEATHMATCH_ARENA) {
			newArena = new DeathmatchArena(numTeams, members, arenaGameType, arenaID, arenaName, tmpl.getArenaCategory(), duration, goals, teamNames, spawnPoints, victoryCondition, id, minPlayers, instanceTemplateID, victoryPayment, defeatPayment,
					victoryExp, defeatExp, useWeapons);
		} else if (tmpl.getArenaType() == CTF_ARENA) {
			newArena = new CaptureTheFlagArena(numTeams, members, arenaGameType, arenaID, arenaName, tmpl.getArenaCategory(), duration, goals, teamNames, spawnPoints, victoryCondition, id, minPlayers, instanceTemplateID, victoryPayment,
					defeatPayment, victoryExp, defeatExp, useWeapons);
		}
		if(Log.loggingDebug)Log.debug("ARENA: arena check 6. arenaID: " + id + " arena map: " + arenas);
		for (OID playerToRemove : playersToRemove) {
			//for (int arenaType : queues.keySet()) {
			for (int arenaTemp : queues.keySet()) {//Dragonsan
					Log.debug("QUEUE: removing player: " + playerToRemove + " from queue: " + queues.get(arenaTemp).getArenaName());
					queues.get(arenaTemp).removePlayer(playerToRemove);//Dragonsan
					//queues.get(arenaType).removePlayer(playerToRemove);
					// Send leave dome request - should be done before creating the arena (although this may create issues?)
				/*int domeID = (Integer) EnginePlugin.getObjectProperty(playerToRemove, WorldManagerClient.NAMESPACE, "domeID");
				if (domeID != -1) {
					ExtensionMessage leaveMsg = new ExtensionMessage(AgisMobClient.MSG_TYPE_DOME_LEAVE_REQUEST,
		                null, playerToRemove);
					leaveMsg.setProperty("domeID", domeID);
					Engine.getAgent().sendBroadcast(leaveMsg);
				}*/
			}
		}
		Log.debug("ARENA: createNewArena end");
		return true;
	}
	
	/**
     * The hook for when a player has requested a duel with another player. Checks to see if
     * both the requester and the target are able to start a duel.
     *
     */
	class DuelChallengeHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			Log.debug("DUEL: player requested a duel");
			ArenaClient.duelChallengeMessage duelMsg = (ArenaClient.duelChallengeMessage) msg;
			// Check both the challenger and the challenged are not currently in a duel
			OID challenger = (OID) duelMsg.getProperty("challenger");
			OID challenged = (OID) duelMsg.getProperty("challenged");
			 if(aDB==null)
				 aDB = new AccountDatabase(false);
	          
				boolean isOnBlockList = aDB.isOnBlackList(challenger, challenged);
				if (isOnBlockList) {
					String targetName = WorldManagerClient.getObjectInfo(challenged).name;
					EventMessageHelper.SendErrorEvent(challenger, EventMessageHelper.ERROR_PLAYER_ON_BLOCK_LIST, 0, targetName);
					   aDB.close();
					   return false;
				}else {
					isOnBlockList = aDB.isOnBlackList(challenged, challenger);
					if (isOnBlockList) {
						String targetName = WorldManagerClient.getObjectInfo(challenged).name;
						EventMessageHelper.SendErrorEvent(challenger, EventMessageHelper.ERROR_PLAYER_ON_YOUR_BLOCK_LIST, 0, targetName);
						 aDB.close();
						   return false;
					}
				}
					
			// aDB.close();
 			
			if (challenger.equals(challenged)) {
				return true;
			}
			boolean challengerBusy = (Boolean) EnginePlugin.getObjectProperty(challenger, WorldManagerClient.NAMESPACE, "busy");
			boolean challengedBusy = (Boolean) EnginePlugin.getObjectProperty(challenged, WorldManagerClient.NAMESPACE, "busy");
			
			if (challengerBusy == true) {
				ExtendedCombatMessages.sendErrorMessage(challenger, "You are too busy to start a Duel");
			} else if (challengedBusy == true) {
				String challengedName = WorldManagerClient.getObjectInfo(challenged).name;
				ExtendedCombatMessages.sendErrorMessage(challenger, challengedName + " is too busy to start a Duel");
			} else {
				// Check if the players are within range (20m)
				Point challengerLoc = WorldManagerClient.getObjectInfo(challenger).loc;
				Point challengedLoc = WorldManagerClient.getObjectInfo(challenged).loc;
				if (Point.distanceTo(challengerLoc, challengedLoc) > 20000) {
					String challengedName = WorldManagerClient.getObjectInfo(challenged).name;
					ExtendedCombatMessages.sendErrorMessage(challenger, challengedName + " is too far away to Duel");
					return true;
				}
				
				// Lets make a new DuelChallenge object
				int id = -1;
				while (true) {
					id++;
					if (!duelChallenges.containsKey(id))
						break;
				}
				
				int numTeams = 2;
				ArrayList<OID> playerOids[] = new ArrayList[numTeams];
				Log.debug("DUEL: duel check 1");
				for (int i = 0; i < numTeams; i++) {
					playerOids[i] = new ArrayList<OID>();
				}
				playerOids[0].add(challenger);
				playerOids[1].add(challenged);
				EnginePlugin.setObjectProperty(challenger, WorldManagerClient.NAMESPACE, "busy", true);
				EnginePlugin.setObjectProperty(challenged, WorldManagerClient.NAMESPACE, "busy", true);
				
				String challengerName = WorldManagerClient.getObjectInfo(challenger).name;
				String challengedName = WorldManagerClient.getObjectInfo(challenged).name;
				OID instanceOid = WorldManagerClient.getObjectInfo(challenged).instanceOid;
				DuelChallenge newChallenge = new DuelChallenge(challengerName, challengedName, playerOids, 1, id, instanceOid);
				// Store the DuelChallenge
				duelChallenges.put(id, newChallenge);
				Log.debug("DUEL: added " + id + " to challenges map=" + duelChallenges);
			}
			
		    return true;
		}
	}
	
	/**
     * The hook for the DuelAccept message. This is run when a player accepts a duel challenge from 
     * another player. If everyone involved in the challenge has accepted the challenge a new Duel 
     * will start
     *
     */
	class DuelAcceptHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			Log.debug("DUEL: player accepted duel");
		    
			ArenaClient.duelChallengeAcceptMessage duelMsg = (ArenaClient.duelChallengeAcceptMessage) msg;
			// Check the duel challenge object to make sure it is still valid
			OID accepterOid = (OID) duelMsg.getProperty("accepterOid");
			int challengeID = (Integer) EnginePlugin.getObjectProperty(accepterOid, WorldManagerClient.NAMESPACE, "duelChallengeID");
			DuelChallenge challenge = duelChallenges.get(challengeID);
			Log.debug("DUEL: checking challenges map=" + duelChallenges);
			if (challenge == null) {
				Log.error("DUEL: player " + accepterOid + " accepted a duel challenge which does not exist");
				// Send some message to the client
			} else {
				challenge.playerAccept(accepterOid);
			}
			
		    return true;
		}
	}
	
	/**
     * The hook for the DuelDecline message. This is run when a player declines a duel challenge from 
     * another player. This will cause the challenge to be removed.
     *
     */
	class DuelDeclineHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			Log.debug("DUEL: player declined duel");
		    
			ArenaClient.duelChallengeDeclineMessage duelMsg = (ArenaClient.duelChallengeDeclineMessage) msg;
			// Check the duel challenge object to make sure it is still valid
			OID delinerOid = (OID) duelMsg.getProperty("declinerOid");
			int challengeID = (Integer) EnginePlugin.getObjectProperty(delinerOid, WorldManagerClient.NAMESPACE, "duelChallengeID");
			DuelChallenge challenge = duelChallenges.get(challengeID);
			if (challenge == null) {
				Log.error("DUEL: player " + delinerOid + " declined a duel challenge which does not exist");
				// Send some message to the client
			} else {
				challenge.playerDeclined(delinerOid);
			}
			
		    return true;
		}
	}
	
	/**
     * The hook for the duelDisconnect message. This gets the arena the player is currently
     * in and then removes them from it.
     *
     */
	class DuelChallengeDisconnectHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ArenaClient.duelChallengeDisconnectMessage defeatMsg = (ArenaClient.duelChallengeDisconnectMessage) msg;
			OID oid = defeatMsg.getSubject();
			// Get the players arenaID then get that arena. Then call remove player in
			// the arena object.
			int challengeID = (Integer) defeatMsg.getProperty("challengeID");
			String playerName = (String) defeatMsg.getProperty("name");
			Log.debug("ARENA PLUGIN: player " + oid + " has been disconnected in duel challenge " + challengeID);
			DuelChallenge challenge = duelChallenges.get(challengeID);
			challenge.playerDisconnected(oid, playerName);
			return true;
		}
	}
	
	/**
	 * The hook for the duelChallengeRemoveMessage. Removes the Duel Challenge object from the map of 
	 * Duel Challenges.
	 *
	 */
	class DuelChallengeRemoveHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ArenaClient.duelChallengeRemoveMessage removeMsg = (ArenaClient.duelChallengeRemoveMessage) msg;
			
			int challengeID = (Integer) removeMsg.getProperty("challengeID");
			Log.debug("DUEL: removing challenge " + challengeID + " from challenges map=" + duelChallenges);
			DuelChallenge challenge = duelChallenges.remove(challengeID);
			
			OID flagOid = challenge.getFlagOid();
			WorldManagerClient.despawn(flagOid);
			Log.debug("ARENA PLUGIN: removed duel challenge: " + challengeID + " from the Map of Duel Challenges");
		    return true;
		}
	}
	
	/**
	 * The hook for the duelStartMessage. Removes the Duel Challenge object from the map of 
	 * Duel Challenges. Creates a new Duel object from the challenge.
	 *
	 */
	class DuelStartHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ArenaClient.duelStartMessage duelMsg = (ArenaClient.duelStartMessage) msg;
			
			int challengeID = (Integer) duelMsg.getProperty("challengeID");
			DuelChallenge challenge = duelChallenges.remove(challengeID);
			ArrayList<OID>[] oids = challenge.getTeam();
			Log.debug("ARENA PLUGIN: creating new Duel from challenge: " + challengeID);
			int numTeams = 2;
			String[] teamLeaders = new String[numTeams];
			teamLeaders[0] = challenge.getChallenger();
			teamLeaders[1] = challenge.getChallenged();
			int type = challenge.getDuelType();
			Point centerLoc = challenge.getCenter();
			OID flagOid = challenge.getFlagOid();
			for (int i = 0; i < numTeams; i++) {
				for (int j = 0; j < oids[i].size(); j++) {
					OID oid = oids[i].get(j);
					EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "duelChallengeID", -1);
				}
			}
			
			int id = -1;
			while (true) {
				id++;
				if (!duels.containsKey(id))
					break;
			}
			Duel duel = new Duel(teamLeaders, oids, type, id, centerLoc, flagOid);
			duels.put(id, duel);
			
		    return true;
		}
	}
	
	/**
     * The hook for the DuelDefeatMessage. Called when a player has either wandered too far 
     * from the duel area or their health reached 1.
     *
     */
	class DuelDefeatHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ArenaClient.duelDefeatMessage defeatMsg = (ArenaClient.duelDefeatMessage) msg;
			OID oid = defeatMsg.getSubject();
			// Get the players duelID then get that arena. Then call remove player in
			// the duel object.
			
			int duelID = -1;
			try {
				duelID = (Integer) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DUEL_ID);
			} catch(NullPointerException e) {
				return true;
			}
			if (duelID != -1) {
				Log.debug("ARENA PLUGIN: player " + oid + " has been defeated in duel " + duelID);
				Duel d = duels.get(duelID);
				d.removePlayer(oid);
			}
			return true;
		}
	}
	
	/**
     * The hook for the duelDisconnect message. This gets the arena the player is currently
     * in and then removes them from it.
     *
     */
	class DuelDisconnectHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ArenaClient.duelDisconnectMessage defeatMsg = (ArenaClient.duelDisconnectMessage) msg;
			OID oid = defeatMsg.getSubject();
			// Get the players arenaID then get that arena. Then call remove player in
			// the arena object.
			int duelID = (Integer) defeatMsg.getProperty("duelID");
			String playerName = (String) defeatMsg.getProperty("name");
			Log.debug("ARENA PLUGIN: player " + oid + " has been disconnected in duel " + duelID);
			Duel d = duels.get(duelID);
			d.disconnectedPlayer(oid, playerName);
			return true;
		}
	}
	
	/**
	 * The hook for the duelRemoveMessage. Removes the Duel Challenge object from the map of 
	 * Duel Challenges.
	 *
	 */
	class DuelRemoveHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ArenaClient.duelRemoveMessage removeMsg = (ArenaClient.duelRemoveMessage) msg;
			
			int duelID = (Integer) removeMsg.getProperty("duelID");
			Duel d = duels.remove(duelID);
			//OID flagOid = d.getFlagOid();
			//WorldManagerClient.despawn(flagOid);
			Log.debug("ARENA PLUGIN: removed duel: " + duelID + " from the Map of Duels");
		    return true;
		}
	}
	
	/**
	 * The Hook for when a player is wanting to activate an ability.
	 *
	 */
	class AlterExpHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage eMsg = (ExtensionMessage)msg;
    		OID playerOid = eMsg.getSubject();
    		int expAmount = (Integer) eMsg.getProperty("amount");
    		ArenaStats stats = getPlayerArenaStats(playerOid);
    		stats.alterExp(expAmount);
		    return true;
		}
	}
	
	/**
	 * Gets the ArenaCategory object for the given category id.
	 */
	public static ArenaCategory getArenaCategory(int categoryID) {
		return arenaCategories.get(categoryID);
	}
	
	/**
	 * Updates the players stats for this arena type based on their performance in the arena battle.
	 * @param oid
	 */
	public static void updateArenaStats(int arenaType, int arenaSubType, OID oid, int kills, int deaths, boolean wonArena, int expAwarded, int ratingAdjustment) {
		ArenaStats arenaStats = statsMap.get(oid);
		if (arenaStats != null) {
			arenaStats.updateStats(arenaType, arenaSubType, kills, deaths, wonArena, expAwarded, ratingAdjustment);
			statsMap.put(oid, arenaStats);
		} else {
			Log.debug("ARENA: updateArenaStats arenaStats null for player " + oid);
		}
	}
	
	public static ArenaStats getPlayerArenaStats(OID oid) {
		ArenaStats arenaStats = statsMap.get(oid);
		/*HashMap<OID, ArenaStats> arenaStatsMap = statsMap.get(arenaName);
		if (arenaStatsMap.containsKey(oid)) {
			stats = arenaStatsMap.get(oid);
		} else {
			String name = WorldManagerClient.getObjectInfo(oid).name;
			if (arenaName.contains("Survival")) {
				stats = new ArenaStats(oid, name, survivalArenaStartingRating);
			} else {
				stats = new ArenaStats(oid, name, pvpArenaStartingRating);
			}
		}*/
		return arenaStats;
	}
	
	/**
	 * Adds the given arenaTemplate to the ArrayList of arenaTemplates then
	 * creates a new ArenaQueue object to handle players queuing for that
	 * arena type.
	 * @param template: the ArenaTemplate to add 
	 */
	public static void addArenaTemplate(ArenaTemplate template) {
		arenaTemplates.add(template);
		int numTeams = template.getNumTeams();
		boolean raceSpecific = template.getRaceSpecific();
		ArrayList<String>[] teamRaces = template.getTeamRaces();
		int[] queueSizeReqs = template.getTeamSizes();
		int levelReq = template.getLevelReq();
		ArenaQueue queue = new ArenaQueue(numTeams, raceSpecific, teamRaces, queueSizeReqs, 
				template.getArenaType(), template.getArenaName(), template.getArenaCategory(), levelReq);
		queues.put(template.getTypeID(), queue);
		//queues.put(template.getArenaType(), queue);
		}
	
	/**
	 * Whenever a player joins or leaves an arena queue this function should be called so the current list 
	 * of arenas currently queued for can be sent down to the client.
	 * @param oid: the oid of the player who needs their arenas queued for data sent
	 */
	public void SendArenasQueued(OID oid) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "Arena_Queued");
		int arenasQueuedFor = 0;
		// Here we iterate through the map to get each queue, then see if the 
		// players oid is in the queue
		Log.debug("QUEUE: about to iterate through arena queues. Num arena types: " + arenaTemplates.size());
		//for (int arenaType : queues.keySet()) {
			//ArenaQueue tempQueue = queues.get(arenaType);
		for (int arenaTemp : queues.keySet()) {//Dragonsan
			ArenaQueue tempQueue = queues.get(arenaTemp);//Dragonsan
			Log.debug("QUEUE: about to iterate through arena queue: " + arenaTemp + "Num players in queue: "
					+ "; Player oid: " + oid + "; list of oids: " + tempQueue.toString());
			if (tempQueue.isPlayerInQueue(oid)) {
				//props.put("arenaType" + arenasQueuedFor, arenaType);
				props.put("arenaTemp" + arenasQueuedFor, arenaTemp);//Dragonsan
				props.put("arenaName" + arenasQueuedFor, tempQueue.getArenaName());
				arenasQueuedFor++;
			}
		}
		props.put("numArenasQueued", arenasQueuedFor);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(
				WorldManagerClient.MSG_TYPE_EXTENSION, oid, 
				oid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}
	
	public static Map<Integer, Arena> getArenas() {
		return arenas;
	}
	
	public static Map<Integer, ArenaQueue> getArenaQueues() {
		return queues;
	}
	
	public static Map<Integer, DuelChallenge> getDuelChallenges() {
		return duelChallenges;
	}
	
	public static Map<Integer, Duel> getDuels() {
		return duels;
	}
	
	public static int getNextArenaID() {
		int id = -1;
		while (true) {
			id++;
			if (!arenas.containsKey(id))
				break;
		}
		return id;
	}
	AccountDatabase aDB ;
	/*
	 * Variables for the Arena Plugin
	 */
	protected static int hour=0;
	protected static int minute=0;
	protected static Map<Integer, ArenaCategory> arenaCategories = new HashMap<Integer, ArenaCategory>();
    protected static ArrayList<ArenaTemplate> arenaTemplates = new ArrayList<ArenaTemplate>();
    public static Map<Integer, Arena> arenas = new ConcurrentHashMap<Integer, Arena>();
    protected static Map<OID, Integer> arenaOids = new HashMap<OID, Integer>();
    protected static Map<Integer, ArenaQueue> queues = new HashMap<Integer, ArenaQueue>();
    protected int arenaCheckType;
    // Arena Stats
    protected static HashMap<OID, ArenaStats> statsMap = new HashMap<OID, ArenaStats>();
    //protected static ArenaDatabase arenaDB = new ArenaDatabase();
    
    protected static Map<Integer, DuelChallenge> duelChallenges = new HashMap<Integer, DuelChallenge>();
    protected static Map<Integer, Duel> duels = new HashMap<Integer, Duel>();
    
    // Arena Types
    public static final int MUNCHER_ARENA = 0;
    public static final int DEATHMATCH_ARENA = 1;
    public static final int CTF_ARENA = 2;
    
    //TODO: Replace this with a real Template ID
    public static int duelFlagTemplateID = ObjectManagerClient.BASE_TEMPLATE_ID;
    
    public static int FIRST_GAME_MULTIPLIER = 10;
    public static int SECOND_GAME_MULTIPLIER = 5;
    public static int THIRD_GAME_MULTIPLIER = 2;
    
    protected static String WORLD_TIME_ZONE = "UTC"; 
    
    public static int DUEL_DURATION = 120;
    
    
    // Arena Races
    public static String RACE_SMOO = "Smoo";
}