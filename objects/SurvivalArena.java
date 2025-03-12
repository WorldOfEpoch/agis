package atavism.agis.objects;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import atavism.server.util.*;
import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.plugins.InstanceClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.agis.plugins.*;
import atavism.server.math.*;

/**
 * The Arena class handles an arena instance. It has numerous scheduled executors and a state
 * variable to control the flow of the arena from setup to the end. This class is complete, it 
 * relies on no other class to perform it's duties.
 * @author Andrew Harrison
 *
 */
public class SurvivalArena implements Serializable, MessageCallback {
	
    protected int arenaID;
    protected int arenaType;
    protected String arenaName;
    protected OID instanceOid;
    protected int length;
    protected int startTime;
    protected int instanceTemplateID;
    protected HashMap<Integer, Integer> victoryPayment;
    protected int numRounds;
    protected int currentRound;
    protected long timeArenaStarted;
    
    /**
     * Status of the arena
     * 0: Not yet set
     * 1: Starting
     * 2: Running
     * 3: Ending
     */
    private int state;
    
    protected boolean teamActive;
    protected int teamScore;
    //protected int teamGoal;
    protected String teamName;
    protected int activePlayers;
    protected ArrayList<OID> creatures;
    protected ArrayList<OID> players;
    protected ArrayList<String> names;
    protected ArrayList<Integer> scores;
    protected ArrayList<Integer> kills;
    protected ArrayList<Integer> deaths;
    protected ArrayList<Integer> damageTaken;
    protected ArrayList<Integer> damageDealt;
    protected ArrayList<Integer> creatureSpawns[];
    protected transient ArrayList<Long> subs;
    protected ArrayList<ArenaStats> ratings;
    
    /*
    * Victory Condition information
    * The victoryCondition variable is an integer that represents which team wins when
    * the arena has finished and no team has reached their goal.
    * -1: whichever team is closest to their goal wins
    * >0: integer value of the team that wins (if no other team reaches their goal in time)
    */
    protected int victoryCondition;
    
    /*
     * Goal Type Information
     * This int variable determines what the aim of the game is for the players
     * 0: Survival. When a team reaches 0 they lose
     * 1: Points. The goal is to gain a certain amount of points.
     */
    //protected int goalType;

    /**
     * The default constructor. This creates the Arrays and ArrayLists. This needs to be 
     * called by the other constructors.
     * @param numTeams: the number of teams in the arena
     */
    public SurvivalArena(int numRounds) {
    	Log.debug("ARENA: starting generic arena object construction");
    	
    	creatures = new ArrayList<OID>();
    	players = new ArrayList<OID>();
    	names = new ArrayList<String>();
    	scores = new ArrayList<Integer>();
    	kills = new ArrayList<Integer>();
    	deaths = new ArrayList<Integer>();
    	damageTaken = new ArrayList<Integer>();
    	damageDealt = new ArrayList<Integer>();
    	creatureSpawns = new ArrayList[numRounds];
    	for (int i = 0; i < numRounds; i++) {
    		creatureSpawns[i] = new ArrayList<Integer>();
    	}
    	ratings = new ArrayList<ArenaStats>();
    	
    	Log.debug("ARENA: finished generic arena object construction");
    }

    /**
     * Constructor that takes in two lists that contain the oids of the players
     * on each team.
     * @param one: the list of oids for players on team one
     * @param two: the list of oids for players on team two
     * @param type: the arena type
     * @param dur: the duration of the arena
     * @param goals: the goal score for the teams
     * @param loc: the centre point of the arena
     * @param id: the identification number of the arena
     * @param minPlayers: the minimum number of players in each team
     */
    public SurvivalArena(int id, int numRounds, ArrayList<Integer> creatureSpawns[], ArrayList<OID> oids, 
    		ArrayList<String> names, int type, String arenaName, int dur, int condition, int instanceTemplateID, 
    		HashMap<Integer, Integer> victoryPayment) {
    	this(numRounds);
    	Log.debug("ARENA: starting arena creation: " + id);
    	
    	this.numRounds = numRounds;
    	this.creatureSpawns = creatureSpawns;
    	players = oids;
	    this.names = names;
	    teamScore = 0;
	    //teamGoal = goal;
	    for (int j = 0; j < players.size(); j++) {
	    	scores.add(0);
	    	kills.add(0);
	    	deaths.add(0);
	    	damageTaken.add(0);
	    	damageDealt.add(0);
	    	//ratings.add(ArenaPlugin.getPlayerArenaStats(arenaName, players.get(j)));
	    }
    	arenaID = id;
    	arenaType = type;
    	this.arenaName = arenaName;
    	length = dur;
    	state = 0;
    	victoryCondition = condition;
    	this.victoryPayment = victoryPayment;
    	activePlayers = players.size();
    	
    	// Set the arena ID property for all players
    	for (int i = 0; i < players.size(); i++) {
    		OID oid = players.get(i);
    		EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID, arenaID);
    		//String playerRace = (String) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "race");
    	}
    	
    	this.instanceTemplateID = instanceTemplateID;
    	Log.debug("ARENA: finished arena creation: " + id);
    	setup();
    }
    
    /**
     * This is run once the Arena object is created. It will be responsible for teleporting players to their bases, sending 
     * the setup extension message to all players and then getting arenaStart() to be activated in 30 seconds.
     */
    public void setup() {
    	if (state != 0) {
    		Log.error("ARENA: Arena " + arenaID + " has already been setup. State is not 0");
    		return;
    	}
    	state = 1;
    	
    	// Create a new instance
    	Template overrideTemplate = new Template();
    	overrideTemplate.put(Namespace.INSTANCE, InstanceClient.TEMPL_INSTANCE_NAME, "arena_" + arenaID);
    	instanceOid = InstanceClient.createInstance(instanceTemplateID, overrideTemplate);
    	AOVector dir = new AOVector();
    	BasicWorldNode node = new BasicWorldNode();
    	String markerName = "team" + 0 + "Spawn";
    	Marker spawn = InstanceClient.getMarker(instanceOid, markerName);
    	node.setInstanceOid(instanceOid);
    	node.setOrientation(spawn.getOrientation());
        node.setLoc(spawn.getPoint());
        node.setDir(dir);
        	
	    Log.debug("ARENA: teleporting team " + 0 + " to new instance. Num people in team: " + players.size());
		for (int i = 0; i < players.size(); i++) {
			OID oid = players.get(i);
			InstanceClient.objectInstanceEntry(oid, node, InstanceClient.InstanceEntryReqMessage.FLAG_NONE);
			// Also set some faction/attitude properties here
    		String factionOverride = "arena_" + arenaID + "_team" + 0;
    		EnginePlugin.setObjectProperty(oid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, factionOverride);
		}
    	sendMessageAll("Arena_setup", null);
    	sendMessageAll("message_text", "Prepare yourself, the Arena will begin in 20 seconds.");
    	
    	if (players.size() > 1)
		    GroupClient.createGroup(players);
    	
    	createSubs();
    	
		ArenaStart arenaTimer = new ArenaStart();
		Engine.getExecutor().schedule(arenaTimer, 20, TimeUnit.SECONDS);
    }
    
    /**
     * Creates a subscription for each player so we can catch their damage messages.
     */
    private void createSubs() {
        // subscribe for some messages
    	subs = new ArrayList<Long>();
    	for (int i = 0; i < players.size(); i++) {
    		SubjectFilter filter = new SubjectFilter(players.get(i));
    	    //filter.addType(InventoryClient.MSG_TYPE_INV_UPDATE);
    	    filter.addType(CombatClient.MSG_TYPE_DAMAGE);
    	    subs.add(Engine.getAgent().createSubscription(filter, this));
    	}
    }
    
    /**
     * process network messages
     */
    public void handleMessage(Message msg, int flags) {
    	//Log.debug("PET: got message with type: " + msg.getMsgType());
    	if (msg instanceof SubjectMessage) {
    		processDamageMessage((CombatClient.DamageMessage) msg);
    	} else {
            Log.error("ARENA: unknown msg: " + msg);
        }
        //return true;
    }
    
    /**
     * Updates the damage taken and/or damage dealt stats for the subject
     * and attacker.
     * @param msg
     */
    private void processDamageMessage(CombatClient.DamageMessage msg) {
    	OID attackerOid = msg.getAttackerOid();
    	OID victimOid = msg.getTargetOid();
    	int damage = msg.getDmg();
    	String dmgType = msg.getDmgType();
    	Log.debug("ARENA: got damage message. " + attackerOid + " dealt " + damage + " to " + victimOid);
    	
    	int victimTeam = 0;
		int attackerTeam = 0;
		
    	for (int i = 0; i < players.size(); i++) { 
		    if (players.get(i).equals(victimOid)) { 
		        damageTaken.set(i, damage + damageTaken.get(i));
		        Log.debug("ARENA: damage taken by " + victimOid + " is now: " + damageTaken.get(i));
				sendStatMessageAll("Arena_stat_update", victimTeam, victimOid, "damageTaken", damageTaken.get(i));
		    }
		}
    	for (int i = 0; i < players.size(); i++) { 
		    if (players.get(i).equals(attackerOid)) { 
		        damageDealt.set(i, damage + damageDealt.get(i));
		        Log.debug("ARENA: damage dealt by " + attackerOid + " is now: " + damageDealt.get(i));
		        sendStatMessageAll("Arena_stat_update", attackerTeam, attackerOid, "damageDealt", damageDealt.get(i));
		    }
		}
    }
    
    /**
     * Starts the arena. Sends a message to the client so the client can update the UI and
     * schedules the endArena executioner.
     *
     */
    public class ArenaStart implements Runnable {
		public void run() {
			if (state != 1) {
				Log.error("ARENA: Arena " + arenaID + " is not starting. state is not 1");
				return;
			}
			state = 2;
			// Send the message to get the gates so they can be despawned
	    	ArenaClient.despawnGates(instanceOid, arenaID);
	    	sendMessageAll("JoinedChallengeArena", null);
	    	// Spawn the creatures for round 0 (1)
	    	for (int i = 0; i < creatureSpawns[0].size(); i++) {
	    		AgisMobClient.spawnArenaCreature(creatureSpawns[0].get(i), arenaID, instanceOid);
	    	}
	    	sendMessageAll("message_text", "Fight!");
	    	currentRound = 1;
	    	sendStatMessageAll("Arena_teamstat_update", 0, null, "score", currentRound);
	    	
			startTime = (int) System.currentTimeMillis();
			
			//ArenaEnd arenaTimer = new ArenaEnd();
			//Engine.getExecutor().schedule(arenaTimer, length, TimeUnit.SECONDS);
			timeArenaStarted = System.currentTimeMillis();
		}
	}
    
    /**
     * Adds a newly spawned creature oid to the list of creatures alive in the arena and sets up a subscription
     * to catch their damage messages.
     * @param creatureOid
     */
    public void addArenaCreature(OID creatureOid) {
    	Log.debug("ARENA: adding creature: " + creatureOid + " to creature list for arena: " + arenaID);
    	creatures.add(creatureOid);
    	SubjectFilter filter = new SubjectFilter(creatureOid);
    	filter.addType(CombatClient.MSG_TYPE_DAMAGE);
    	subs.add(Engine.getAgent().createSubscription(filter, this));
    }
    
    /**
     * Marks a team as defeated and will check to see if there is only 1 team 
     * left (making them the winners).
     * @param team: the number of the team that was defeated.
     */
    private void teamDefeated(int team) {
    	Log.debug("ARENA: team " + team + " has been defeated.");
    	endGame(-1);
    }
    
    /**
     * Ends this arena instance.
     * 
     * @param winningTeam: An integer representing the winning team
     */
    private void endGame(int winningTeam) {
    	if (state != 2) {
			Log.debug("ARENA: Arena " + arenaID + " is not running. state is not 2");
			return;
		}
		state = 3;
		
		long timeArenaEnded = System.currentTimeMillis();
		long arenaDuration = (timeArenaEnded - timeArenaStarted) / 1000;
		//int minutes = (int)arenaDuration / 60;
		//int seconds = (int)arenaDuration - (minutes * 60);
		int timeTaken = (int)arenaDuration;
		Log.debug("ARENA: time taken: " + timeTaken);
		
		// Log it
        HashMap<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("arenaID", arenaID);
        DataLoggerClient.logData("ARENA_ENDED", null, null, null, props);
		
		if (winningTeam != -1) {
			sendChatMessageAll("The Players are victorious!");
			sendMessageAll("message_text", "Victory!");
			currentRound++;
		} else {
			sendChatMessageAll("The Players have been defeated!");
			sendMessageAll("message_text", "Defeat!");
		}
		
    	Log.debug("ARENA: endGame hit with winner: " + winningTeam);
    	sendMessageAll("Arena_end", null);
    	
    	for (int i = 0; i < players.size(); i++) {
    		OID oid = players.get(i);
    		EnginePlugin.setObjectProperty(oid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, "");
			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, true);
			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, true);
		    for (int currency : victoryPayment.keySet()) {
		    	int payment = (int)(((float)victoryPayment.get(currency) / (float)numRounds) * (float)(currentRound-1));
		    	AgisInventoryClient.alterCurrency(oid, currency, payment);
		    	sendChatMessageSingle("You have received " + payment + " Tauranga Stones", oid);
		    }
    	}
		
		ArenaCleanup arenaCleanup = new ArenaCleanup();
		Engine.getExecutor().schedule(arenaCleanup, 120, TimeUnit.SECONDS);
		
		for (int j = 0; j < players.size(); j++) {
	    	ArenaStats stats = ratings.get(j);
	    	int newRating = currentRound-1;
	    	/*int oldRating = stats.getRating();
	    	if (newRating < oldRating) {
	    		newRating = oldRating;
	    	}*/
	    	
	    }
		
		Log.debug("ARENA: rating calcs 1");
    }
    
    /**
     * Teleports all players out and then sends out a message to remove this arena 
     * instance from the Map stored in the ArenaPlugin.
     *
     */
    public class ArenaCleanup implements Runnable {
		public void run() {
			Log.debug("ARENA: cleaning up the Arena");
			teleportAllOut();
			// Despawn the mobs
			for (int i = 0; i < creatures.size(); i++) {
				OID creatureOid = creatures.get(i);
				boolean creatureDespawned = AgisMobPlugin.despawnArenaCreature(creatureOid);
				if (!creatureDespawned) {
					Log.warn("ARENA: creature: " + creatureOid + " was not despawned properly 1");
					creatureDespawned = AgisMobPlugin.despawnArenaCreature(creatureOid);
					if (!creatureDespawned) {
						//ObjectManagerClient.unloadObject(creatureOid);
						//WorldManagerClient.despawn(creatureOid);
						Log.warn("ARENA: creature: " + creatureOid + " was not despawned properly 2");
					}
				}
				//ObjectManagerClient.unloadObject(creatures.get(i));
				//WorldManagerClient.despawn(creatures.get(i));
			}
			creatures.clear();
			InstanceClient.deleteInstance(instanceOid);
			// Finally lets remove this Arena object from the Map of current Arenas
			ArenaClient.endArena(arenaID);
		}
    }
    
    /**
     * Teleports all players back to their bases. While this is not currently used
     * it will probably have a use at a later date
     */
    private void teleportAllBase() {
		BasicWorldNode tnode = new BasicWorldNode();
		for (int i = 0; i < players.size(); i++) {
			String markerName = "team" + 0 + "Spawn";
			Marker spawn = InstanceClient.getMarker(instanceOid, markerName);
			OID oid = players.get(i);
			tnode.setLoc(spawn.getPoint());
			WorldManagerClient.updateWorldNode(oid, tnode, true);
		}
	}
    
    /**
     * Goes through all the players in the arena and calls teleportOut. This
     * will also delete the instance, so do not use this unless the instance
     * has finished.
     */
    private void teleportAllOut() {
		Log.debug("ARENA: teleporting all players out");
		for (int i = 0; i < players.size(); i++) {
			OID oid = players.get(i);
			teleportOut(oid);
		}
		//InstanceClient.deleteInstance(instanceOid);
	}
    
    /**
     * Teleports the specified player out of the instance. 
     * @param oid: The oid of the player being teleported out
     */
    private void teleportOut(OID oid) {
    	Log.debug("ARENA: teleporting out player: " + oid);
    	sendMessageSingle("Arena_Left", oid, null);
    	OID defaultInstanceOid = InstanceClient.getInstanceOid("Tauranga Arena");
    	String race = (String) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "race");
    	Marker defaultMarker;
    	if (race.equals("Human")) {
    		defaultMarker = InstanceClient.getMarker(defaultInstanceOid, "arena1_legion");
    	} else if (race.equals("Orc")) {
    		defaultMarker = InstanceClient.getMarker(defaultInstanceOid, "arena1_outcast");
    	} else {
    		defaultMarker = InstanceClient.getMarker(defaultInstanceOid, "arena_respawn");
    	}
		
		BasicWorldNode defaultLoc = new BasicWorldNode();
		defaultLoc.setInstanceOid(defaultInstanceOid);
		defaultLoc.setLoc(defaultMarker.getPoint());
		defaultLoc.setOrientation(defaultMarker.getOrientation());
		AOVector dir = new AOVector();
		defaultLoc.setDir(dir);
		// We better release them in case they were dead
		CombatClient.releaseObject(oid, false);
		// Then make sure they are able to be moved
		EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, false);
		EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
		// Now finally change instance
		InstanceClient.objectInstanceEntry(oid, defaultLoc, InstanceClient.InstanceEntryReqMessage.FLAG_NONE);
		// Reset their state
		EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "state", 0);
		EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID, -1);
		// Remove them from the group
		GroupClient.removeMember(oid);
		// Log it
        HashMap<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("arenaID", arenaID);
        OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
        DataLoggerClient.logData("PLAYER_LEFT_ARENA", oid, null, accountID, props);
    }
    
    /**
     * Removes a player from the arena. 
     * 
     * @param oid: The player being removed from the arena
     */
    public void removePlayer(OID oid) {
    	Log.debug("ARENA: removing player: " + oid);
    	int team = 0;
    	int playerIndex = -1;
    	if (playerIndex(oid) >= 0) {
    		playerIndex = playerIndex(oid);
    	}

    	if (team < 0) {
    		Log.error("ARENA: trying to remove a player: " + oid + " who is not on any team");
    		return;
    	}
    		
    	
    	sendChatMessageAll("Player " + players.get(playerIndex) + " has left the arena.");
    	sendMessageAll("Arena_player_left", oid);
    	players.remove(playerIndex);
    	names.remove(playerIndex);
    	scores.remove(playerIndex);
    	kills.remove(playerIndex);
    	deaths.remove(playerIndex);
    	
    	if (state == 2) {
    	    if (players.size() == 0) {
    	    	endGame(-1);
    	    } else if (activePlayers == 0) {
				endGame(-1);
			}
    	}
    	
    	teleportOut(oid);
		EnginePlugin.setObjectProperty(oid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, "");
    }
    
    /**
     * This will be called whenever a player or creature in the arena dies.
     * @param attackerOid: the oid of the attacker
     * @param victimOid: the oid of the victim
     */
    public void handleDeath(OID attackerOid, OID victimOid) {
    	Log.debug("ARENA: handleDeath hit. Attacker: " + attackerOid + "; victim: " 
    			+ victimOid);
		
    	if (!(state == 2)) {
    		Log.error("ARENA: handleDeath(): Arena is not running. State is not 2.");
    		return;
		}
		if (attackerOid == victimOid) {
			Log.error("ARENA: handleDeath(): Suicide attempt. AttackerOid equals VictimOid");
			return;
		}
		
		
		
		// First figure out whether it was a player or creature that died
		if (players.contains(victimOid)) {
			addIndividualDeath(victimOid, 1);
			activePlayers--;
			if (activePlayers == 0) {
				endGame(-1);
			}
		} else if (creatures.contains(victimOid)) {
			addIndividualScore(attackerOid, 1);
			addIndividualKill(attackerOid, 1);
			creatures.remove(victimOid);
			if (creatures.size() == 0) {
				endRound();
			}
		}
    }
    
    /**
     * Handles the end of a round when all of the creatures are dead.
     */
    private void endRound() {
    	if (!(state == 2)) {
    		Log.error("ARENA: handleDeath(): Arena is not running. State is not 2.");
    		return;
		}
    	
    	Log.debug("ARENA: end of round hit, new round: " + currentRound + " and numRounds: " + numRounds);
    	if (currentRound >= numRounds) {
    		endGame(0);
    	} else {
    		currentRound++;
    		sendMessageAll("message_text", "Wave " + currentRound + " will start in 20 seconds.");
    		NewRound arenaTimer = new NewRound();
    		Engine.getExecutor().schedule(arenaTimer, 20, TimeUnit.SECONDS);
    	}
    }
    
    public class NewRound implements Runnable {
		public void run() {
			Log.debug("ARENA: new round hit with current round: " + currentRound + " and numSpawns: " + creatureSpawns[currentRound-1].size());
			sendMessageAll("message_text", "<<Wave " + currentRound + ">>");
			sendStatMessageAll("Arena_teamstat_update", 0, null, "score", currentRound);
			for (int i = 0; i < creatureSpawns[currentRound-1].size(); i++) {
				AgisMobClient.spawnArenaCreature(creatureSpawns[currentRound-1].get(i), arenaID, instanceOid);
				Log.debug("ARENA: spawning arena creature spawn num: " + creatureSpawns[currentRound-1].get(i));
			}
		}
    }
    
    /**
     * Add the score to the players individual score.
     * @param team: the team the player belongs to
     * @param oid: the oid of the player
     * @param score: the score value to add. Can be negative.
     */
    private void addIndividualScore(OID oid, int score) {
    	int playerPosition = -1;
		for (int i = 0; i < players.size(); i++) { 
		    if (players.get(i).equals(oid)) { 
		    	scores.set(i, scores.get(i) + score);
		        playerPosition = i;
		    }
		}
		Log.debug("ARENA: about to send individual score for player: " + oid + " with player pos: " + playerPosition);
		if (playerPosition != -1) {
			teamScore++;
		    sendStatMessageAll("Arena_stat_update", 0, oid, "score", scores.get(playerPosition));
		    //sendStatMessageAll("Arena_teamstat_update", 0, oid, "score", teamScore);
		}
    }
    
    /**
     * Add the score to the players individual score.
     * @param team: the team the player belongs to
     * @param oid: the oid of the player
     * @param adjustment: the value to adjustment the kill variable by
     */
    private void addIndividualKill(OID oid, int adjustment) {
    	int playerPosition = -1;
    	for (int i = 0; i < players.size(); i++) { 
		    if (players.get(i).equals(oid)) { 
		    	kills.set(i, adjustment + kills.get(i));
		        playerPosition = i;
		    }
		}
    	Log.debug("ARENA: about to send individual kills for player: " + oid + " with player pos: " + playerPosition);
    	if (playerPosition != -1) {
		    sendStatMessageAll("Arena_stat_update", 0, oid, "kill", kills.get(playerPosition));
		    int arenaKills = (Integer) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "arenaKills");
		    arenaKills = arenaKills + adjustment;
        	EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "arenaKills", arenaKills);
        	//ArenaPlugin.updateArenaKills(oid, adjustment);
    	}
    }
    
    /**
     * Add the score to the players individual score.
     * @param team: the team the player belongs to
     * @param oid: the oid of the player
     * @param adjustment: the value to adjustment the death variable by
     */
    private void addIndividualDeath(OID oid, int adjustment) {
    	int playerPosition = -1;
    	for (int i = 0; i < players.size(); i++) { 
		    if (players.get(i).equals(oid)) { 
		        deaths.set(i, adjustment + deaths.get(i));
		        playerPosition = i;
		    }
		}
    	Log.debug("ARENA: about to send individual deaths for player: " + oid + " with player pos: " + playerPosition);
    	if (playerPosition != -1) {
		    sendStatMessageAll("Arena_stat_update", 0, oid, "death", deaths.get(playerPosition));
		    //ArenaPlugin.updateArenaDeaths(oid, adjustment);
    	}
    }
    
    /**
     * Searches the target team for the index of the player
     * 
     * @param oid: Player object id
     * @param team: Which team to search
     * @return Index:the position of the player within the team list. Returns -1 
     * if the player doesn't exist in that team.
     */
    private int playerIndex(OID oid) {
    	for (int i = 0; i < players.size(); i++) {
    		if (players.get(i).equals(oid))
    			return i;
    	}
    	return -1;
    }
    
    /**
     * Calls sendMessageTeam for each team in the arena.
     * @param msgType: the message type
     * @param data: some form of data to be sent
     */
    private void sendMessageAll(String msgType, Serializable data) {
    	for (int i = 0; i < players.size(); i++) {
    		sendMessageSingle(msgType, players.get(i), data);
    	}
    }
    
    /**
     * Sends an extension message of the specified type to the specified player
     * @param msgType: the message type
     * @param oid: the oid to send the message to
     * @param data: some form of data to be sent
     */
    private void sendMessageSingle(String msgType, OID oid, Serializable data) {
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", msgType);
		// Check for type and fill props here
		if (msgType == "JoinedChallengeArena") {
			int timeLeft = (int)System.currentTimeMillis() - startTime;
			timeLeft = (length * 1000) - timeLeft;
			props.put("timeLeft", timeLeft);
			props.put("numRounds", numRounds);
			//props.put("goalType", goalType);
			
			props.put("names", names.toString());
			props.put("oids", players.toString());
			props.put("scores", scores.toString());
			props.put("kills", kills.toString());
			props.put("deaths", deaths.toString());
			props.put("damageTaken", damageTaken.toString());
			props.put("damageDealt", damageDealt.toString());
			props.put("teamGoal" + 0, 0);
			props.put("teamScore" + 0, currentRound);
			props.put("teamName" + 0, teamName);
		} else if (msgType == "message_text") {
			String value = (String) data;
			props.put("state", state);
			props.put("message", value);
		} else if (msgType == "Arena_player_left") {
			props.put("playerOid", data);
		} else if (msgType == "Arena_setup") {
			props.put("arenaType", arenaType);
		}
		
		TargetedExtensionMessage msg = new TargetedExtensionMessage(
				WorldManagerClient.MSG_TYPE_EXTENSION, oid, 
				oid, false, props);
		Engine.getAgent().sendBroadcast(msg);
    }
    
    /**
     * Calls sendStateMessageSingle for each player in the arena.
     * @param msgType: the message type
     * @param team: the team which the player whose stat is being updated is from
     * @param oid: the oid of the player whose stat was changed
     * @param stat: the type of stat that was changed
     * @param score: the value of the stat that was changed
     */
    private void sendStatMessageAll(String msgType, int team, OID oid, String stat, int score) {
    	for (int i = 0; i < players.size(); i++) {
    			sendStatMessageSingle(msgType, players.get(i), team, oid, stat, score);
    	}
    }
    
    /**
     * Sends a stat update extension message to the specified player.
     * @param msgType: the message type
     * @param oid: the oid of the player the message is being sent to
     * @param team: the team which the player whose stat is being updated is from
     * @param scoreOid: the oid of the player whose stat was changed
     * @param stat: the type of stat that was changed
     * @param score: the value of the stat that was changed
     */
    private void sendStatMessageSingle(String msgType, OID oid, int team, OID scoreOid,  
    		String stat, int score) {
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", msgType);
		// Check for type and fill props here
		if (msgType.equals("Arena_stat_update")) {
			props.put("stat", stat);
			props.put("player", scoreOid);
			props.put("team", team);
			props.put("score", score);
		} else if (msgType.equals("Arena_teamstat_update")) {
			props.put("team", team);
			props.put("score", score);
		}

		TargetedExtensionMessage msg = new TargetedExtensionMessage(
				WorldManagerClient.MSG_TYPE_EXTENSION, oid, 
				oid, false, props);
		Engine.getAgent().sendBroadcast(msg);
    }
    
    /**
     * Calls sendChatMessageTeam for each team in the arena.
     * @param msg: the chat message to send
     */
    private void sendChatMessageAll(String msg) {
    	for (int i = 0; i < players.size(); i++) {
    		sendChatMessageSingle(msg, players.get(i));
    	}
    }
    
    /**
     * Sends the specified chat message to the specified player
     * @param msg: the chat message to send
     * @param oid: the oid of the player to send the message to
     */
    private void sendChatMessageSingle(String msg, OID oid) {
    	ChatClient.sendObjChatMsg(oid, 2, msg);
    }
    
    public int getArenaID() { return arenaID; }
    public void setArenaID(int arenaID) { this.arenaID = arenaID; }
    
    public OID getArenaInstanceOid() { return instanceOid; }
    public void setArenaInstanceOid(OID instanceOid) { this.instanceOid = instanceOid; }
    
    public int getArenaType() { return arenaType; }
    public void setArenaType(int arenaType) { this.arenaType = arenaType; }
    
    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }
    
    public int getInstanceTemplateID() { return instanceTemplateID; }
    public void setInstanceTemplateID(int instanceTemplateID) { this.instanceTemplateID = instanceTemplateID; }
    
    public int getState() { return state; }
    public void setState(int state) { this.state = state; }
    
    /*public int getTeamScore() { return teamScore; }
    public void setTeamScore(int teamscore) { this.teamScore = teamscore; }
    
    public int getTeamGoal() { return teamGoal; }
    public void setTeamGoal(int teamgoal) { this.teamGoal = teamgoal; }*/
    
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    
    public ArrayList<OID> getPlayers() { return players; }
    public void setPlayers(ArrayList<OID> players) { this.players = players; }
    
    public ArrayList<String> getNames() { return names; }
    public void setNames(ArrayList<String> names) { this.names = names; }
    
    public ArrayList<Integer> getScores() { return scores; }
    public void setScores(ArrayList<Integer> scores) { this.scores = scores; }

    public ArrayList<Integer> getKills() { return kills; }
    public void setKills(ArrayList<Integer> kills) { this.kills = kills; }
    
    public ArrayList<Integer> getDeaths() { return deaths; }
    public void setDeaths(ArrayList<Integer> deaths) { this.deaths = deaths; }

    private static final long serialVersionUID = 1L;
}
