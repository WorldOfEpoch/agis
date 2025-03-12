package atavism.agis.plugins;

import atavism.msgsys.Message;
import atavism.msgsys.MessageAgent;
import atavism.msgsys.MessageTypeFilter;
import atavism.msgsys.ResponseMessage;
import atavism.server.engine.*;
import atavism.server.messages.LogoutMessage;
import atavism.server.messages.PropertyMessage;
import atavism.server.objects.ObjectTypes;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.ObjectInfo;
import atavism.server.util.Log;
import atavism.server.util.Logger;
import atavism.agis.database.AccountDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.objects.AgisStat;
import atavism.agis.objects.Claim;
import atavism.agis.objects.FactionStateInfo;
import atavism.agis.objects.Guild;
import atavism.agis.objects.Guild.GuildMember;
import atavism.agis.objects.GuildLevelSettings;
import atavism.agis.plugins.GuildClient.guildClaimPermitionMessage;
import atavism.agis.plugins.GuildClient.guildWarehousePermitionMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GuildPlugin extends EnginePlugin {
	
	public GuildPlugin() {
        super(GUILD_PLUGIN_NAME);
        setPluginType("Guild");
    }

	public String getName() {
		return GUILD_PLUGIN_NAME;
	}

	public static String GUILD_PLUGIN_NAME = "Guild";
	
	protected static final Logger log = new Logger("Guild");
	
	public void onActivate() {
		Log.debug("GUILD PLUGIN: activated");
		registerHooks();
		MessageTypeFilter filter = new MessageTypeFilter();
		filter.addType(GuildClient.MSG_TYPE_CREATE_GUILD);
		filter.addType(GuildClient.MSG_TYPE_INVITE_RESPONSE);
		filter.addType(GuildClient.MSG_TYPE_GUILD_ADD_RESOURCES);
		filter.addType(GuildClient.MSG_TYPE_GUILD_COMMAND);
		filter.addType(WorldManagerClient.MSG_TYPE_SPAWNED);
		filter.addType(LogoutMessage.MSG_TYPE_LOGOUT);
		filter.addType(PropertyMessage.MSG_TYPE_PROPERTY);
		filter.addType(GuildClient.MSG_TYPE_GUILD_CLAIM_PERMISSION);
		Engine.getAgent().createSubscription(filter, this);

		MessageTypeFilter filter2 = new MessageTypeFilter();
		filter2.addType(GuildClient.MSG_TYPE_GUILD_CLAIM_PERMISSION);
		filter2.addType(GuildClient.MSG_TYPE_GUILD_WAREHOUSE_PERMISSION);
		filter2.addType(GuildClient.MSG_TYPE_GET_GUILD_WAREHOUSE);
		filter2.addType(GuildClient.MSG_TYPE_GET_GUILD_MERCHANT);
		Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);
 
		
		
		// Setup connection to admin database
		  aDB = new AccountDatabase(true);
		  cDB = new ContentDatabase(true);
	        
        guilds = aDB.loadGuildData();
        guildLevelSettings = cDB.GetGuildLevelSetting();
	}

	protected void registerHooks() {
		getHookManager().addHook(GuildClient.MSG_TYPE_CREATE_GUILD, new GuildCreateHook());
		getHookManager().addHook(GuildClient.MSG_TYPE_INVITE_RESPONSE, new GuildInviteResponseHook());
		getHookManager().addHook(GuildClient.MSG_TYPE_GUILD_COMMAND, new GuildCommandHook());

		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
		getHookManager().addHook(PropertyMessage.MSG_TYPE_PROPERTY, new PropertyHook());
		getHookManager().addHook(GuildClient.MSG_TYPE_GUILD_CLAIM_PERMISSION, new GuildClaimPermission());
		getHookManager().addHook(GuildClient.MSG_TYPE_GUILD_ADD_RESOURCES, new GuildAddResourcesHook());
		getHookManager().addHook(GuildClient.MSG_TYPE_GUILD_WAREHOUSE_PERMISSION, new GuildWarehousePermission());
		getHookManager().addHook(GuildClient.MSG_TYPE_GET_GUILD_WAREHOUSE, new GetGuildWarehouse());
		getHookManager().addHook(GuildClient.MSG_TYPE_GET_GUILD_MERCHANT, new GetGuildMerchant());
	}

	void loadData() {
		guildLevelSettings = cDB.GetGuildLevelSetting();
	}

	protected void ReloadTemplates(Message msg) {
		Log.error("GuildPlugin ReloadTemplates Start");
		loadData();
		Log.error("GuildPlugin ReloadTemplates End");
	}
    
  
	
	class GetGuildMerchant implements Hook {
		public boolean processMessage(Message msg, int flags) {
			PropertyMessage gmMsg = (PropertyMessage) msg;
			OID oid = gmMsg.getSubject();
			log.error("GetGuildMerchant "+oid);
			int guildId = aDB.GetGuildId(oid);
			if (!guilds.containsKey(guildId)) {
				Engine.getAgent().sendIntegerResponse(gmMsg, -1);
				return true;
			}
			
			if (!GuildPlugin.guildLevelSettings.containsKey(guilds.get(guildId).getLevel())) {
				Engine.getAgent().sendIntegerResponse(gmMsg, -1);
				return true;
			}
			
			 int merchantTable = GuildPlugin.guildLevelSettings.get(guilds.get(guildId).getLevel()).getMerchantTable();
			log.error("GetGuildMerchant "+oid+" merchantTable ="+merchantTable);
			Engine.getAgent().sendIntegerResponse(gmMsg, merchantTable);
			log.error("GetGuildMerchant END "+oid);
			return true;
		}
	}
	
	class GetGuildWarehouse implements Hook {
		public boolean processMessage(Message msg, int flags) {
			PropertyMessage gmMsg = (PropertyMessage) msg;
			OID oid = gmMsg.getSubject();
			log.debug("GetGuildWarehouse "+oid);
			int guildId = aDB.GetGuildId(oid);
			if (!guilds.containsKey(guildId)) {
				Engine.getAgent().sendLongResponse(gmMsg, 0L);
				return true;
			}
			if (!GuildPlugin.guildLevelSettings.containsKey(guilds.get(guildId).getLevel())) {
				Engine.getAgent().sendLongResponse(gmMsg, 0L);
				return true;
			}
			OID wh = guilds.get(guildId).getWarehouse();
			log.debug("GetGuildWarehouse "+oid+" wh="+wh);
			if(wh == null || wh.toLong() == 0L) {
				wh = AgisInventoryClient.createStorage(oid, "Guild_"+guildId, GuildPlugin.guildLevelSettings.get(guilds.get(guildId).getLevel()).getWarehouseNumSlot(), false);
				guilds.get(guildId).setWarehouse(wh);
				aDB.updateGuild(guilds.get(guildId));
			}
			log.debug("GetGuildWarehouse "+oid+" wh ="+wh);
			
			Engine.getAgent().sendLongResponse(gmMsg, wh.toLong());
			log.debug("GetGuildWarehouse END "+oid);
			return true;
		}
	}
	
	class GuildWarehousePermission implements Hook {
		public boolean processMessage(Message msg, int flags) {
			guildWarehousePermitionMessage gmMsg = (guildWarehousePermitionMessage) msg;
			OID oid = (OID) gmMsg.getProperty("player");
			int guildID = aDB.GetGuildId(oid);
			if (!guilds.containsKey(guildID)) {
				Engine.getAgent().sendIntegerResponse(gmMsg, 0);
				return true;
			}

			int perm = 0;
			if (guilds.get(guildID).getGuildMember(oid).getRank() == 0) {
				perm = Claim.PERMISSION_OWNER;
			} else if (guilds.get(guildID).hasPermission(oid, PERMISSION_WAREHOUSE_GET)) {
				perm = Claim.PERMISSION_ADD_DELETE;
			} else if (guilds.get(guildID).hasPermission(oid, PERMISSION_WAREHOUSE_ADD)) {
				perm = Claim.PERMISSION_ADD_ONLY;
			} 

			Engine.getAgent().sendIntegerResponse(gmMsg, perm);
			return true;
		}
	}
	

	
	class GuildClaimPermission implements Hook {
		public boolean processMessage(Message msg, int flags) {
			guildClaimPermitionMessage gmMsg = (guildClaimPermitionMessage) msg;
			
			 // get some info about player
			
			// OID oid = gmMsg.getSubject();
			OID oid = (OID) gmMsg.getProperty("player");
			int guildId = (Integer) gmMsg.getProperty("guildId");
			int guildID = -1;
			try {
				guildID = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, GUILD_PROP);
			} catch (NullPointerException e1) {
			}
			if (guildId != guildID) {
				Engine.getAgent().sendIntegerResponse(gmMsg, 0);
				return true;
			}

			if (!guilds.containsKey(guildID)) {
				Engine.getAgent().sendIntegerResponse(gmMsg, 0);
				return true;
			}

			int perm = 0;
			if (guilds.get(guildID).getGuildMember(oid).getRank() == 0) {
				perm = Claim.PERMISSION_OWNER;
			} else if (guilds.get(guildID).hasPermission(oid, PERMISSION_EDIT_CLAIM)) {
				perm = Claim.PERMISSION_ADD_DELETE;
			} else if (guilds.get(guildID).hasPermission(oid, PERMISSION_ADD_CLAIM)) {
				perm = Claim.PERMISSION_ADD_ONLY;
			} else if (guilds.get(guildID).hasPermission(oid, PERMISSION_ACTION_CLAIM)) {
				perm = Claim.PERMISSION_INTERACTION;
			}

			Engine.getAgent().sendIntegerResponse(gmMsg, perm);
			return true;
		}
	}
	
	
	class GuildCreateHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage gmMsg = (ExtensionMessage) msg;
			OID oid = gmMsg.getSubject();
			
			Log.debug("GUILD: got create guild message from player: " + oid);
			
			// First make sure the player isn't already in a guild
			int guildID = -1;
			try {
				guildID = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, GUILD_PROP);
			} catch (NullPointerException e1) {
			}
			
			if (guildID > 0) {
				Log.warn("GUILD: player attempted to create a guild, but is already currently in a guild");
				ChatClient.sendObjChatMsg(oid, 1, "You cannot create a guild while you are in a guild");
				return true;
			}
			
			String guildName = (String) gmMsg.getProperty("guildName");
			// Check if the guild name is already used
			for (Guild guild : guilds.values()) {
				if (guild.getGuildName().equals(guildName)) {
					ChatClient.sendObjChatMsg(oid, 1, "The guild "
							+ guildName + " already exists. Please choose another name.");
					return true;
				}
			}
			
			ArrayList<OID> initiates = new ArrayList<OID>(); //TODO: Get a list of players who signed the charter
			int factionID = (Integer)EnginePlugin.getObjectProperty(oid, Namespace.FACTION, FactionStateInfo.FACTION_PROP);
			Guild newGuild = new Guild(-1, guildName, factionID, rankNames,  defaultPermissions, oid, initiates);
			
			aDB.writeNewGuild(newGuild);
			newGuild.setAccountDatabase(aDB);
			if (newGuild.getGuildID() == -1) {
				// Something went wrong on the insert
				ChatClient.sendObjChatMsg(oid, 1, "Something went wrong");
				return true;
			}
			guilds.put(newGuild.getGuildID(), newGuild);
			
			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, GUILD_PROP, newGuild.getGuildID());
			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, GUILD_NAME_PROP, newGuild.getGuildName());
			
			return true;
		}
	}
	
	class GuildInviteResponseHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage gmMsg = (ExtensionMessage) msg;
			OID responder = gmMsg.getSubject();
			
			OID inviter = (OID) gmMsg.getProperty("inviter");
			int guildID = (Integer) gmMsg.getProperty("guildID");
			boolean response = (Boolean) gmMsg.getProperty("response");
			
			int inviterGuild = -1;
			try {
				inviterGuild = (Integer) EnginePlugin.getObjectProperty(inviter, WorldManagerClient.NAMESPACE, GUILD_PROP);
			} catch (NullPointerException e1) {
			}
			
			// Check the guild exists, and that the inviter is still a member of the guild
			if (!guilds.containsKey(inviterGuild) || inviterGuild != guildID) {
				return true;
			}
			// Also check the responder is not already in a guild
			int responderGuild = -1;
			try {
				responderGuild = (Integer) EnginePlugin.getObjectProperty(responder, WorldManagerClient.NAMESPACE, GUILD_PROP);
			} catch (NullPointerException e1) {
			}
			if (responderGuild > 0) {
				return true;
			}
			
			if (response == true) {
				// The responder has agreed to join the Guild, add them as a member
				String memberName = WorldManagerClient.getObjectInfo(responder).name;
				int level  = 1;
				try {
	            	AgisStat lev = (AgisStat) EnginePlugin.getObjectProperty(responder, CombatClient.NAMESPACE, "level");
	            	//Log.error("GUILD: ply level : "+lev.toString());
	            	level = lev.getCurrentValue();
	            } catch (NullPointerException e) {
	            	Log.warn("GUILD PLUGIN: player " + responder + " does not have an level property");
	            }  
				guilds.get(guildID).addNewMember(responder, memberName, level);
			} else {
				//TODO: The responder has declined the invitation, let the inviter know
			}
			
			return true;
		}
	}
	
	class GuildCommandHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage gmMsg = (ExtensionMessage) msg;
			/*
			 * get some info about player
			 */
			OID oid = gmMsg.getSubject();
			int guildID = -1;
			try {
				guildID = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, GUILD_PROP);
			} catch (NullPointerException e1) {
			}
			
			if (!guilds.containsKey(guildID)) {
				return true;
			}

			String commandType = (String) gmMsg.getProperty("commandType");
			OID targetOid = (OID) gmMsg.getProperty("targetOid");
			String data = (String) gmMsg.getProperty("data");
			// First check if the command is disband so it can be handled in the Plugin
			if (commandType.equals("disband")) {
				disbandGuild(oid, guilds.get(guildID));
			} else {
				guilds.get(guildID).handleCommand(oid, commandType, targetOid, data);
			}
			
			return true;
		}
	}
	
	
	class GuildAddResourcesHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage gmMsg = (ExtensionMessage) msg;

			OID oid = gmMsg.getSubject();
			Log.debug("GuildAddResourcesHook: "+oid);
			int guildID = -1;
			try {
				guildID = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, GUILD_PROP);
			} catch (NullPointerException e1) {
			}
			Log.debug("GuildAddResourcesHook: "+oid+" GuildId="+guildID);
			if (!guilds.containsKey(guildID)) {
				return true;
			}

			int itemId = (int) gmMsg.getProperty("itId");
			int itemCount = (int) gmMsg.getProperty("itCount");
			Log.debug("GuildAddResourcesHook: "+oid+" GuildId="+guildID+" itemId="+itemId+" count="+itemCount);
			
			if (itemId > 0 && itemCount > 0) {
				guilds.get(guildID).handleAddResources(oid, itemId, itemCount);
			}else {
				Log.debug("GuildAddResourcesHook: ItemId or count is not higher then 0");
			}
			Log.debug("GuildAddResourcesHook: END");
			return true;
		}
	}
	
	private void disbandGuild(OID playerOid, Guild guild) {
		if (guild.processGuildDisband(playerOid)) {
			guilds.remove(guild);
		}
	}
	
	// Log the login information and send a response
	class SpawnedHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;
			OID objOid = spawnedMsg.getSubject();
			ObjectInfo objInfo = WorldManagerClient.getObjectInfo(objOid);
			//Check Spawned Object id Player
			if (objInfo.objType != ObjectTypes.player)
            	  return true;
           
			String plyName = objInfo.name;
			ArrayList<GuildMember> gms;

			// Do something
			int guildID = -1;
			try {
				guildID = (Integer) EnginePlugin.getObjectProperty(objOid, WorldManagerClient.NAMESPACE, GUILD_PROP);
			} catch (NullPointerException e1) {
				log.error("No guild property on the Player");
			}
				
			log.debug("GUILD: got guildID: " + guildID);
			boolean stillInGuild = true;
				
			if (guilds.containsKey(guildID)) {
				// Verify member is still part of the guild
				stillInGuild = guilds.get(guildID).memberLoggedIn(objOid);
				gms = guilds.get(guildID).getMembers();
				
			//	 for( int i = 0; i < gms.size() ; i++ ) {
					 //Send message Logedin To members
			//		WorldManagerClient.sendObjChatMsg(gms.get(i).getOid(), 1, plyName+" logedin");
			//	 }	
		} else if (guildID > 0){
				stillInGuild = false;
			}
			
			if (!stillInGuild) {
				// Player was removed from the guild while they were logged out, reset their guild properties
				EnginePlugin.setObjectProperty(objOid, WorldManagerClient.NAMESPACE, GUILD_PROP, -1);
				EnginePlugin.setObjectProperty(objOid, WorldManagerClient.NAMESPACE, GUILD_NAME_PROP, null);
			}else {
				log.debug("GUILD: User "+plyName +" not in guildID: " + guildID);
			}
			
				
			return true;
		}
	}

	// Log the logout information and send a response.
	class LogoutHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LogoutMessage message = (LogoutMessage) msg;
			OID playerOid = message.getSubject();
			log.debug("LOGOUT: guild logout started for: " + playerOid);
			// Do something
			int guildID = -1;
			try {
				guildID =  aDB.GetGuildId(playerOid);
						//(Integer) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, GUILD_PROP);
			} catch (Exception e1) {
				Log.exception("LogoutHook Exception: ",e1);
			}
			log.debug("LOGOUT: guild id:"+guildID+" for: " + playerOid);
			if (guilds.containsKey(guildID)) {
				guilds.get(guildID).memberLoggedOut(playerOid);
			}else {
				log.debug("LOGOUT: guild id:"+guildID+" not exist for: " + playerOid);
			}
				
			Engine.getAgent().sendResponse(new ResponseMessage(message));
			log.debug("LOGOUT: guild logout finished for: " + playerOid);
			return true;
		}
	}
	
	 /**
     * PropertyHook catches any property updates. We only want to process
     * entities that are flagged as guilded
     */
    class PropertyHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            PropertyMessage propMsg = (PropertyMessage) msg;
            Set<String> props = propMsg.keySet();
            if (!props.contains("level")) 
            	return true;
            
          //  return UpdateGuildMemberProps(propMsg);
             OID objOid = propMsg.getSubject();
            ObjectInfo objInfo = WorldManagerClient.getObjectInfo(objOid);
             if (objInfo ==null || objInfo.objType != ObjectTypes.player)
          	  	return true;
          
          /*
            boolean isPlayerOnline =false;
            
            try {
            	isPlayerOnline =  GuildClient.getPlayerIsOnline(oid);
    		} catch (IOException e1) {
    			e1.printStackTrace();
    		}
            if (!isPlayerOnline)
            	return true;*/
            log.debug("GUILD: PropertyHook  oid " + objOid+" | " +props);
    		int guildID = -1;
    		try {
    			guildID = (Integer) EnginePlugin.getObjectProperty(objOid, WorldManagerClient.NAMESPACE, GUILD_PROP);
    		} catch (NullPointerException e1) {
    			log.debug("GUILD: PropertyHook "+e1);
    			//e1.printStackTrace();
    		}
    		
    		if (!guilds.containsKey(guildID)) {
    			return true;
    		}
    		
    		 if (props.contains("level")) {
    			guilds.get(guildID).memberLevel(objOid, (Integer) propMsg.getProperty("level"));
    		 }
        	 return true;
            
            
            
        }
    }
    
    /**
     * Sends update to group members about the group and its members
     */
    protected boolean UpdateGuildMemberProps(PropertyMessage propMsg) {
    	/*
    	OID oid = propMsg.getSubject();
    	
		int guildID = -1;
		try {
			guildID = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, GUILD_PROP);
		} catch (NullPointerException e1) {
		}
		
		if (!guilds.containsKey(guildID)) {
			return true;
		}
		 Set<String> props = propMsg.keySet();
		 if (props.contains("level")) {
			guilds.get(guildID).memberLevel(oid, (Integer) propMsg.getProperty("level"));
		 }
		 */
    	 return true;
    }
	
	
	public static void AddGuildRank(String rankName, String[] permissions) {
		rankNames.add(rankName);
		ArrayList<String> permissionsList = new ArrayList<String>();
		for (String permission : permissions) {
			permissionsList.add(permission);
		}
		defaultPermissions.add(permissionsList);
	}
	
	private HashMap<Integer, Guild> guilds;
	public static ConcurrentHashMap<Integer, GuildLevelSettings> guildLevelSettings = new ConcurrentHashMap<Integer, GuildLevelSettings>();
	
	private AccountDatabase aDB;
	private ContentDatabase cDB;
	
	public static final String GUILD_PROP = "guild";
	public static final String GUILD_NAME_PROP = "guildName";
	
	public static final String PERMISSION_INVITE = "invite";
	public static final String PERMISSION_KICK = "kick";
	public static final String PERMISSION_PROMOTE = "promote";
	public static final String PERMISSION_DEMOTE = "demote";
	public static final String PERMISSION_SET_MOTD = "setmotd";
	public static final String PERMISSION_CHAT = "chat";
	public static final String PERMISSION_DISBAND = "disband";
	public static final String PERMISSION_ADD_RANK = "addRank";
	public static final String PERMISSION_EDIT_RANK = "editRank";
	public static final String PERMISSION_DEL_RANK = "delRank";
	public static final String PERMISSION_ADD_CLAIM = "claimAdd";
	public static final String PERMISSION_EDIT_CLAIM = "claimEdit";
	public static final String PERMISSION_ACTION_CLAIM = "claimAction";
	public static final String PERMISSION_WAREHOUSE_ADD = "whAdd";
	public static final String PERMISSION_WAREHOUSE_GET = "whGet";
	public static final String PERMISSION_LEVEL_UP = "levelUp";
	
	public static int maxRanks = 10;
	public static ArrayList<String> rankNames = new ArrayList<String>();
	public static ArrayList<ArrayList<String>> defaultPermissions = new ArrayList<ArrayList<String>>();
	
	
}