package atavism.agis.plugins;

import java.io.IOException;
import java.io.Serializable;

import atavism.msgsys.GenericMessage;
import atavism.msgsys.MessageType;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.messages.*;

/**
 * This class is responsible for sending out messages associated with the Guild System. 
 * The majority (if not all) of the messages should be caught by the GuildPlugin class.
 * @author Andrew Harrison
 *
 */
public class GuildClient {
	protected GuildClient() {
	}
	
	/**
	 * Sends the createGuildMessage which will create a new guild if one with
	 * the given name doesn't exist.
	 * @param oid: the identifier of the player who wishes to create the guild
	 * @param guildName: the name of the new guild to be created
	 */
	public static void createGuild(OID oid, String guildName) {
		createGuildMessage msg = new createGuildMessage(oid, guildName);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("GUILD CLIENT: createGuildMessage hit 2");
	}

	public static class createGuildMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public createGuildMessage() {
            super();
        }
        
        public createGuildMessage(OID oid, String guildName) {
        	super(oid);
        	setMsgType(MSG_TYPE_CREATE_GUILD);
        	setProperty("guildName", guildName);
        	Log.debug("GUILD CLIENT: createGuildMessage hit 1");
        }
	}
	/**
	 * Get Guild Merchant Table Id
	 * @param player oid
	 * @return merchant table id
	 */
	
	public static int GetGuildMerchant(OID oid) {
		getGuildMerchantMessage msg = new getGuildMerchantMessage(oid);
		int mtid = Engine.getAgent().sendRPCReturnInt(msg);
		
		Log.debug("GUILD CLIENT: getGuildMerchantMessage hit 2 "+mtid);
		return mtid;
	}

	public static class getGuildMerchantMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public getGuildMerchantMessage() {
            super();
        }
        
        public getGuildMerchantMessage(OID oid) {
        	super(oid);
        	setMsgType(MSG_TYPE_GET_GUILD_MERCHANT);
        	Log.debug("GUILD CLIENT: getGuildMerchantMessage hit 1");
        }
	}
	
	/**
	 * Get Guild Warehouse Oid
	 * @param player oid
	 * @return warehouse oid
	 */
	public static OID GetGuildWarehouse(OID oid) {
		getGuildWarehouseMessage msg = new getGuildWarehouseMessage(oid);
		long w = Engine.getAgent().sendRPCReturnLong(msg);
		
		Log.debug("GUILD CLIENT: GetGuildWarehouse hit 2 "+w);
		return OID.fromLong(w);
	}

	public static class getGuildWarehouseMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public getGuildWarehouseMessage() {
            super();
        }
        
        public getGuildWarehouseMessage(OID oid) {
        	super(oid);
        	setMsgType(MSG_TYPE_GET_GUILD_WAREHOUSE);
        	Log.debug("GUILD CLIENT: getGuildWarehouseMessage hit 1");
        }
	}
	
	/**
	 * Sends the getPlayerIsOnlineMessage which will attempt to get a players oid from their name.
	 */
	public static boolean getPlayerIsOnline(OID oid) throws IOException {
		getPlayerIsOnlineMessage msg = new getPlayerIsOnlineMessage(oid);
		//Engine.getAgent().sendBroadcast(msg);
		Log.debug("GROUP CLIENT: getPlayerIsOnlineMessage hit 1");
		return Engine.getAgent().sendRPCReturnBoolean(msg);
	}

	public static class getPlayerIsOnlineMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
        public getPlayerIsOnlineMessage() {
            super();
            Log.debug("GROUP CLIENT: getPlayerIsOnlineMessage hit 1");
        }
        public getPlayerIsOnlineMessage(OID oid) {
        	super();
        	setMsgType(MSG_TYPE_GET_PLAYER_IS_ONLINE);
        	setProperty("plyOid", oid);
        	Log.debug("GROUP CLIENT: getPlayerIsOnlineMessage hit 1");
        }
	}
	/**
	 * Sends the guildCommandMessage which will deal with the given command appropriately.
	 * @param oid: the identifier of the player who sent the command
	 * @param commandType: the type of command
	 * @param data: some data to assist with execution of the command
	 * @param dataTwo: another piece of data to assist with execution of the command0
	 */
	public static void guildCommand(OID oid, String commandType, Serializable data, Serializable dataTwo) {
		guildCommandMessage msg = new guildCommandMessage(oid, commandType, data, dataTwo);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("GUILD CLIENT: guildCommandMessage hit 2");
	}

	public static class guildCommandMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public guildCommandMessage() {
            super();
        }
        
        public guildCommandMessage(OID oid, String commandType, Serializable data, Serializable dataTwo) {
        	super(oid);
        	setMsgType(MSG_TYPE_GUILD_COMMAND);
        	setProperty("commandType", commandType);
        	setProperty("commandData", data);
        	setProperty("commandDataTwo", dataTwo);
        	Log.debug("GUILD CLIENT: guildCommandMessage hit 1");
        }
	}
	
	public static int getGuildClaimPermition(OID oid,int guildId) {
		guildClaimPermitionMessage msg = new guildClaimPermitionMessage(oid,guildId);
		Log.debug("GUILD CLIENT: guildClaimPermitionMessage hit 2 "+oid+" "+guildId);
		return Engine.getAgent().sendRPCReturnInt(msg);
	}

	public static class guildClaimPermitionMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
        public guildClaimPermitionMessage() {
            super();
        }
        
        public guildClaimPermitionMessage(OID oid,int guildId) {
        	super();
        	setMsgType(MSG_TYPE_GUILD_CLAIM_PERMISSION);
          	setProperty("player", oid);
          	setProperty("guildId", guildId);
            Log.debug("GUILD CLIENT: guildClaimPermitionMessage hit 1");
        }
	}
	
	public static int getGuildWhPermition(OID oid) {
		guildWarehousePermitionMessage msg = new guildWarehousePermitionMessage(oid);
		Log.debug("GUILD CLIENT: guildWarehousePermitionMessage hit 2 "+oid);
		return Engine.getAgent().sendRPCReturnInt(msg);
	}

	public static class guildWarehousePermitionMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
        public guildWarehousePermitionMessage() {
            super();
        }
        
        public guildWarehousePermitionMessage(OID oid) {
        	super();
        	setMsgType(MSG_TYPE_GUILD_WAREHOUSE_PERMISSION);
          	setProperty("player", oid);
           	Log.debug("GUILD CLIENT: guildWarehousePermitionMessage hit 1");
        }
	}
	
	public static final MessageType MSG_TYPE_GET_PLAYER_IS_ONLINE = MessageType.intern("iow.GET_PLAYER_IS_ONLINE");
	public static final MessageType MSG_TYPE_CREATE_GUILD = MessageType.intern("guild.createGuild");
	public static final MessageType MSG_TYPE_INVITE_RESPONSE = MessageType.intern("guild.inviteResponse");
	public static final MessageType MSG_TYPE_GUILD_COMMAND = MessageType.intern("guild.guildCommand");
	public static final MessageType MSG_TYPE_GUILD_CLAIM_PERMISSION = MessageType.intern("guild.claimPermission");
	public static final MessageType MSG_TYPE_GUILD_WAREHOUSE_PERMISSION = MessageType.intern("guild.warehousePermission");
	public static final MessageType MSG_TYPE_GUILD_ADD_RESOURCES = MessageType.intern("guild.addResource");
	public static final MessageType MSG_TYPE_GET_GUILD_WAREHOUSE = MessageType.intern("guild.getWarehouse");
	public static final MessageType MSG_TYPE_GET_GUILD_MERCHANT = MessageType.intern("guild.getMerchant");
}