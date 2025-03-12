package atavism.agis.plugins;

import java.io.IOException;

import atavism.msgsys.GenericMessage;
import atavism.msgsys.MessageType;
import atavism.server.util.*;
import atavism.server.engine.Engine;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.messages.*;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;

/**
 * This class is responsible for sending out messages associated with the Arena System. 
 * The majority (if not all) of the messages should be caught by the ArenaPlugin class.
 * @author Andrew Harrison
 *
 */
public class ArenaClient {
	protected ArenaClient() {
	}
	
	/**
	 * Sends the startArenaCheckMessage which will cause the ArenaCheck
	 * class to be run every second.
	 */
	public static void startArenaCheck() throws IOException {
		startArenaCheckMessage msg = new startArenaCheckMessage();
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: startArenaCheckMessage hit 2");
	}

	public static class startArenaCheckMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public startArenaCheckMessage() {
            super();
            setMsgType(MSG_TYPE_START_ARENA_CHECK);
            Log.debug("ARENA CLIENT: startArenaCheckMessage hit 1");
        }
	}
	
	/**
	 * Sends the addArenaCreatureMessage which will add the oid of the creature to the specified
	 * arena's list of creatures.
	 * @param creatureOid: the identifier for the creature
	 * @param arenaID: the identifier for the arena which will have this creature added
	 */
	public static void addArenaCreature(Long creatureOid, int arenaID) {
		addArenaCreatureMessage msg = new addArenaCreatureMessage(creatureOid, arenaID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: addArenaCreatureMessage hit 2");
	}

	public static class addArenaCreatureMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public addArenaCreatureMessage() {
            super();
        }
        
        public addArenaCreatureMessage(Long creatureOid, int arenaID) {
        	super();
        	setMsgType(MSG_TYPE_ADD_CREATURE);
        	setProperty("creatureOid", creatureOid);
        	setProperty("arenaID", arenaID);
        	Log.debug("ARENA CLIENT: addArenaCreatureMessage hit 1");
        }
	}
	
	/**
	 * Sends the despawnGatesMessage which will cause the gates in 
	 * the specified instance to be removed.
	 * @param instanceOid: the identifier for the instance which needs the gates removed
	 * @param arenaID: the identifier for the arena which needs the gates removed
	 */
	public static void despawnGates(OID instanceOid, int arenaID) {
		despawnGatesMessage msg = new despawnGatesMessage(instanceOid, arenaID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: despawnGatesMessage hit 2");
	}

	public static class despawnGatesMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public despawnGatesMessage() {
            super();
        }
        
        public despawnGatesMessage(OID instanceOid, int arenaID) {
        	super();
        	setMsgType(MSG_TYPE_DESPAWN_GATES);
        	setProperty("instanceOid", instanceOid);
        	setProperty("arenaID", arenaID);
        	Log.debug("ARENA CLIENT: despawnGatesMessage hit 1");
        }
	}
	
	/**
	 * Sends the endArenaMessage which will cause the arena to be removed
	 * from the Map stored in the ArenaPlugin.
	 * @param arenaID: the identifier of the arena that has ended
	 */
	public static void endArena(int arenaID) {
		endArenaMessage msg = new endArenaMessage(arenaID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: endArenaMessage hit 2");
	}

	public static class endArenaMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
        public endArenaMessage() {
            super();
        }
        
        public endArenaMessage(int arenaID) {
        	super();
        	setMsgType(MSG_TYPE_END_ARENA);
        	setArenaID(arenaID);
        	Log.debug("ARENA CLIENT: endArenaMessage hit 1");
        }
        
        public void setArenaID(int arenaID) { this.arenaID = arenaID; }
        public int getArenaID() { return arenaID; }
        
        protected int arenaID;
	}

	/**
	 * Sends the removePlayerMessage which will cause the specified player to be
	 * removed from any arena they are currently in.
	 * @param oid: the player who is going to be removed
	 */
	public static void removePlayer(OID oid) {
		removePlayerMessage msg = new removePlayerMessage(oid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: removePlayerMessage hit 2");
	}

	public static class removePlayerMessage extends PropertyMessage {

		private static final long serialVersionUID = 1L;
		public removePlayerMessage() {
	            super();
	    }
		public removePlayerMessage(OID oid) {
			super(oid);
			setMsgType(MSG_TYPE_REMOVE_PLAYER);
			Log.debug("ARENA CLIENT: removePlayerMessage hit 1");
		}
	}

	/**
	 * Sends the arenaDeathMessage which will cause the scores to be updated 
	 * for the arena the killer/victim was in.
	 * @param k: the oid of the killer
	 * @param v: the oid of the victim
	 */
	public static void arenaDeath (OID k, OID v) {
		arenaKillMessage msg = new arenaKillMessage(k, v);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: arenaKillMessage hit 2");
	}

	public static class arenaKillMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
		public arenaKillMessage() {
	            super();
	    }
		public arenaKillMessage(OID k, OID v) {
			super();
			setMsgType(MSG_TYPE_ARENA_KILL);
			setProperty("killer", k);
			setProperty("victim", v);
			Log.debug("ARENA CLIENT: arenaKillMessage hit 1");
		}
	}
	
	/**
	 * Sends the dotScoreMessage which will cause the scores to be updated
	 * for the arena where the player who ate the dot is in.
	 * @param oid: the oid of the player who ate the dot
	 */
	public static void dotScore (OID oid) {
		dotScoreMessage msg = new dotScoreMessage(oid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: dotScoreMessage hit 2");
	}
	
	public static class dotScoreMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
		public dotScoreMessage() {
	            super();
	    }
		public dotScoreMessage(OID oid) {
			super();
			setMsgType(MSG_TYPE_DOT_SCORE);
			setKiller(oid);
			Log.debug("ARENA CLIENT: dotScoreMessage hit with killer: " + killer);
		}
		
		public void setKiller(OID killer) {
			this.killer = killer;
		}
		public OID getKiller() {
			return killer;
		}
		protected OID killer;
	}
	
	/**
	 * Sends the duelChallengeMessage which will create a new Duel Challenge object.
	 * @param challenger: the oid of the challenger
	 * @param challenged: the oid of the challenged
	 */
	public static void duelChallenge (OID challenger, OID challenged) {
		duelChallengeMessage msg = new duelChallengeMessage(challenger, challenged);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: duelChallengeMessage hit 2");
	}
	
	public static class duelChallengeMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
		public duelChallengeMessage() {
	        super();
	    }
		public duelChallengeMessage(OID challenger, OID challenged) {
			super();
			setMsgType(MSG_TYPE_DUEL_CHALLENGE);
			setProperty("challenger", challenger);
			setProperty("challenged", challenged);
			Log.debug("ARENA CLIENT: duelChallengeMessage hit 1");
		}
	}
	
	/**
	 * Sends the duelChallengeAcceptMessage which will update the accepted status of the player for 
	 * their corresponding duel challenge.
	 * @param accepterOid: the oid of the accepter
	 */
	public static void duelChallengeAccept (OID accepterOid) {
		duelChallengeAcceptMessage msg = new duelChallengeAcceptMessage(accepterOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: duelChallengeAcceptMessage hit 2");
	}
	
	public static class duelChallengeAcceptMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
		public duelChallengeAcceptMessage() {
	        super();
	    }
		public duelChallengeAcceptMessage(OID accepterOid) {
			super();
			setMsgType(MSG_TYPE_DUEL_ACCEPT_CHALLENGE);
			setProperty("accepterOid", accepterOid);
			Log.debug("ARENA CLIENT: duelChallengeAcceptMessage hit 1");
		}
	}
	
	/**
	 * Sends the duelChallengeDeclineMessage which will end the duel challenge. 
	 * @param declinerOid: the oid of the decliner
	 */
	public static void duelChallengeDecline (OID declinerOid) {
		duelChallengeDeclineMessage msg = new duelChallengeDeclineMessage(declinerOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: duelChallengeDeclineMessage hit 2");
	}
	
	public static class duelChallengeDeclineMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
		public duelChallengeDeclineMessage() {
	        super();
	    }
		public duelChallengeDeclineMessage(OID declinerOid) {
			super();
			setMsgType(MSG_TYPE_DUEL_DECLINE_CHALLENGE);
			setProperty("declinerOid", declinerOid);
			Log.debug("ARENA CLIENT: duelChallengeDeclineMessage hit 1");
		}
	}
	
	/**
	 * Sends the duelChallengeDisconnectMessage which will remove the player from whatever duel they are in
	 * @param challengeID: the id of the duel challenge
	 */
	public static void duelChallengeDisconnect (OID defeatedOid, String name, int challengeID) {
		duelChallengeDisconnectMessage msg = new duelChallengeDisconnectMessage(defeatedOid, name, challengeID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: duelChallengeDisconnectMessage hit 2");
	}
	
	public static class duelChallengeDisconnectMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
		public duelChallengeDisconnectMessage() {
	        super();
	    }
		public duelChallengeDisconnectMessage(OID oid, String name, int challengeID) {
			super(oid);
			setProperty("name", name);
			setProperty("challengeID", challengeID);
			setMsgType(MSG_TYPE_DUEL_CHALLENGE_DISCONNECT);
			Log.debug("ARENA CLIENT: duelChallengeDisconnectMessage hit 1");
		}
	}
	
	/**
	 * Sends the duelChallengeRemoveMessage which will remove the duelChallenge object from the ArenaPlugin.
	 * @param challengeID: the id of the duel challenge
	 */
	public static void duelChallengeRemove (int challengeID) {
		duelChallengeRemoveMessage msg = new duelChallengeRemoveMessage(challengeID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: duelChallengeRemoveMessage hit 2");
	}
	
	public static class duelChallengeRemoveMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
		public duelChallengeRemoveMessage() {
	        super();
	    }
		public duelChallengeRemoveMessage(int challengeID) {
			super();
			setMsgType(MSG_TYPE_DUEL_CHALLENGE_REMOVE);
			setProperty("challengeID", challengeID);
			Log.debug("ARENA CLIENT: duelChallengeRemoveMessage hit 1");
		}
	}
	
	/**
	 * Sends the duelStartMessage which will remove the duelChallenge object and create a new Duel
	 * object with the players from the challenge.
	 * @param challengeID: the id of the duel challenge
	 */
	public static void duelStart (int challengeID) {
		duelStartMessage msg = new duelStartMessage(challengeID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: duelStartMessage hit 2");
	}
	
	public static class duelStartMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
		public duelStartMessage() {
	        super();
	    }
		public duelStartMessage(int challengeID) {
			super();
			setMsgType(MSG_TYPE_DUEL_START);
			setProperty("challengeID", challengeID);
			Log.debug("ARENA CLIENT: duelStartMessage hit 1");
		}
	}
	
	/**
	 * Sends the duelDefeatMessage which will remove the player from whatever duel they are in
	 * @param challengeID: the id of the duel challenge
	 */
	public static void duelDefeat (OID defeatedOid) {
		duelDefeatMessage msg = new duelDefeatMessage(defeatedOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: duelDefeatMessage hit 2");
	}
	
	public static class duelDefeatMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
		public duelDefeatMessage() {
	        super();
	    }
		public duelDefeatMessage(OID oid) {
			super(oid);
			setMsgType(MSG_TYPE_DUEL_DEFEAT);
			Log.debug("ARENA CLIENT: duelDefeatMessage hit 1");
		}
	}
	
	/**
	 * Sends the duelDisconnectMessage which will remove the player from whatever duel they are in
	 * @param challengeID: the id of the duel challenge
	 */
	public static void duelDisconnect (OID defeatedOid, String name, int duelID) {
		duelDisconnectMessage msg = new duelDisconnectMessage(defeatedOid, name, duelID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: duelDisconnectMessage hit 2");
	}
	
	public static class duelDisconnectMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
		public duelDisconnectMessage() {
	        super();
	    }
		public duelDisconnectMessage(OID oid, String name, int duelID) {
			super(oid);
			setProperty("name", name);
			setProperty("duelID", duelID);
			setMsgType(MSG_TYPE_DUEL_DISCONNECT);
			Log.debug("ARENA CLIENT: duelDisconnectMessage hit 1");
		}
	}
	
	/**
	 * Sends the duelRemoveMessage which will remove the duelChallenge object from the ArenaPlugin.
	 * @param challengeID: the id of the duel challenge
	 */
	public static void duelRemove (int duelID) {
		duelRemoveMessage msg = new duelRemoveMessage(duelID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: duelRemoveMessage hit 2");
	}
	
	public static class duelRemoveMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
		public duelRemoveMessage() {
	        super();
	    }
		public duelRemoveMessage(int duelID) {
			super();
			setMsgType(MSG_TYPE_DUEL_REMOVE);
			setProperty("duelID", duelID);
			Log.debug("ARENA CLIENT: duelRemoveMessage hit 1");
		}
	}
	
	/**
	 * Sends the removeEffectsMessage which will remove any effects the layer had from the duel or arena
	 * @param ID: the id of the duel or arena
	 */
	public static void removeEffects (OID oid, String type, int ID) {
		removeEffectsMessage msg = new removeEffectsMessage(oid, type, ID);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: removeEffectsMessage hit 2");
	}
	
	public static class removeEffectsMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
		public removeEffectsMessage() {
	        super();
	    }
		public removeEffectsMessage(OID oid, String type, int ID) {
			super(oid);
			setMsgType(MSG_TYPE_REMOVE_EFFECTS);
			setProperty("type", type);
			setProperty("ID", ID);
			Log.debug("ARENA CLIENT: removeEffectsMessage hit 1");
		}
	}
	
	/**
	 * Sends the getArenaStatsMessage which will cause the list of
	 * arena stats to be sent to the requesting client.
	 * @param oid: the oid of the player wanting the arena stats.
	 */
	public static void getArenaStats(OID oid, int statsType) {
		getArenaStatsMessage msg = new getArenaStatsMessage(oid, statsType);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: getArenaStatsMessage hit 2");
	}

	public static class getArenaStatsMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public getArenaStatsMessage() {
            super();
        }
		public getArenaStatsMessage(OID oid, int statsType) {
			super(oid);
			setMsgType(MSG_TYPE_GET_ARENA_STATS);
			setProperty("statsType", statsType);
			Log.debug("ARENA CLIENT: getArenaStatsMessage hit 1");
		}
	}
	
	/**
	 * Sends the resourceNodeAssaultedMessage which will cause the list of
	 * arena stats to be sent to the requesting client.
	 * @param oid: the oid of the player wanting the arena stats.
	 */
	public static void resourceNodeAssaulted(OID oid, OID nodeOid) {
		resourceNodeAssaultedMessage msg = new resourceNodeAssaultedMessage(oid, nodeOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("ARENA CLIENT: resourceNodeAssaultedMessage hit 2");
	}

	public static class resourceNodeAssaultedMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public resourceNodeAssaultedMessage() {
            super();
        }
		public resourceNodeAssaultedMessage(OID oid, OID nodeOid) {
			super(oid);
			setMsgType(MSG_TYPE_GET_ARENA_STATS);
			setProperty("nodeOid", nodeOid);
			Log.debug("ARENA CLIENT: resourceNodeAssaultedMessage hit 1");
		}
	}

	public static void arenaReleaseRequest(OID oid) {
		ExtensionMessage eMsg = new ExtensionMessage(oid);
		eMsg.setMsgType(ArenaClient.MSG_TYPE_RELEASE_REQUEST);
		Engine.getAgent().sendBroadcast(eMsg);
	}

	
	public static final MessageType MSG_TYPE_LEAVE_ARENA = MessageType
        .intern("arena.leaveArena");
	public static final MessageType MSG_TYPE_START_ARENA_CHECK = MessageType
	    .intern("arena.startChecks");
	
	public static final MessageType MSG_TYPE_ADD_CREATURE = MessageType
        .intern("arena.addCreature");
	
	public static final MessageType MSG_TYPE_DESPAWN_GATES = MessageType
        .intern("arena.despawnGates");
	
	public static final MessageType MSG_TYPE_END_ARENA = MessageType
	    .intern("arena.endArena");
	
	public static final MessageType MSG_TYPE_GET_ARENA_TYPES = MessageType
		    .intern("arena.getTypes");
	
	public static final MessageType MSG_TYPE_GET_ARENA_LIST = MessageType
		    .intern("arena.getList");
		
	public static final MessageType MSG_TYPE_JOIN_QUEUE = MessageType
			.intern("arena.joinQueue");
		
		public static final MessageType MSG_TYPE_LEAVE_QUEUE = MessageType
	        .intern("arena.leaveQueue");
	public static final MessageType MSG_TYPE_GROUP_JOIN_QUEUE = MessageType.intern("arena.groupJoinQueue");
			
	public static final MessageType MSG_TYPE_GROUP_LEAVE_QUEUE = MessageType.intern("arena.groupLeaveQueue");
	
	public static final MessageType MSG_TYPE_REMOVE_PLAYER= MessageType
        .intern("arena.removePlayer");

	public static final MessageType MSG_TYPE_ARENA_KILL = MessageType
		.intern("arena.kill");
	
	public static final MessageType MSG_TYPE_RELEASE_REQUEST = MessageType
			.intern("ao.RELEASE_REQUEST");
		
	public static final MessageType MSG_TYPE_DOT_SCORE = MessageType
    	.intern("arena.dotScore");
	
	public static final MessageType MSG_TYPE_DUEL_CHALLENGE = MessageType
	    .intern("duel.startChallenge");
	
	public static final MessageType MSG_TYPE_DUEL_ACCEPT_CHALLENGE = MessageType
        .intern("duel.acceptChallenge");
	
	public static final MessageType MSG_TYPE_DUEL_DECLINE_CHALLENGE = MessageType
        .intern("duel.declineChallenge");
	
	public static final MessageType MSG_TYPE_DUEL_CHALLENGE_DISCONNECT = MessageType
        .intern("duel.challengeDisconnect");
	
	public static final MessageType MSG_TYPE_DUEL_CHALLENGE_REMOVE = MessageType
        .intern("duel.removeChallenge");
	
	public static final MessageType MSG_TYPE_DUEL_START = MessageType
        .intern("duel.startDuel");
	
	public static final MessageType MSG_TYPE_DUEL_DEFEAT = MessageType
        .intern("duel.defeat");
	
	public static final MessageType MSG_TYPE_DUEL_DISCONNECT = MessageType
        .intern("duel.disconnect");
	
	public static final MessageType MSG_TYPE_DUEL_REMOVE = MessageType
        .intern("duel.remove");
	
	public static final MessageType MSG_TYPE_REMOVE_EFFECTS = MessageType
        .intern("arena.removeEffects");
	
	public static final MessageType MSG_TYPE_GET_ARENA_STATS = MessageType
        .intern("arena.getStats");
	
	public static final MessageType MSG_TYPE_ACTIVATE_ARENA_ABILITY = MessageType
		.intern("ao.ACTIVATE_ARENA_ABILITY");
	
	public static final MessageType MSG_TYPE_COMPLETE_TUTORIAL = MessageType
		.intern("ao.COMPLETE_TUTORIAL");
	
	public static final MessageType MSG_TYPE_SELECT_RACE = MessageType
		.intern("ao.SELECT_RACE");
	
	public static final MessageType MSG_TYPE_CHANGE_RACE = MessageType
		.intern("ao.CHANGE_RACE");
	
	public static final MessageType MSG_TYPE_PICKUP_FLAG = MessageType
		.intern("arena.pickupFlag");
	
	public static final MessageType MSG_TYPE_DROP_FLAG = MessageType
		.intern("arena.dropFlag");
	
	public static final MessageType MSG_TYPE_ACTIVATE_MACHINE = MessageType
		.intern("arena.activateMachine");
	
	public static final MessageType MSG_TYPE_ALTER_EXP = MessageType
		.intern("ao.ALTER_EXP");
	
	public static Namespace NAMESPACE = null;

}