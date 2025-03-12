package atavism.agis.arenas;

import java.io.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import atavism.server.util.*;
import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.plugins.InstanceClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.agis.objects.*;
import atavism.agis.objects.ArenaQueue.QueueMember;
import atavism.agis.plugins.*;
import atavism.server.math.*;
import atavism.server.messages.PropertyMessage;

/**
 * The Arena class handles an arena instance. It has numerous scheduled executors and a state
 * variable to control the flow of the arena from setup to the end. This class is complete, it 
 * relies on no other class to perform it's duties.
 * @author Andrew Harrison
 *
 */
abstract public class Arena implements Serializable, MessageDispatch {
	
	protected int numTeams;
    protected int arenaID;
    protected int arenaDatabaseID;
    protected int arenaGameType;
    protected int category;
    protected String arenaName;
    protected OID instanceOid;
    protected int length;
    protected int startTime;
    protected int instanceTemplateID;
    protected HashMap<Integer, Integer> victoryPayment;
    protected HashMap<Integer, Integer> defeatPayment;
    protected int victoryXP;
    protected int defeatXP;
    protected boolean useWeapons;
    protected ScheduledFuture<?> scheduledExecutioner;
    
    /**
     * Status of the arena
     * 0: Not yet set
     * 1: Starting
     * 2: Running
     * 3: Ending
     */
    protected int state;
    
    protected ArenaTeam teams[];
    
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
    protected int goalType;
    
    protected HashMap<Integer, HashMap<Integer, ArenaObject>> arenaObjects = new HashMap<Integer, HashMap<Integer, ArenaObject>>();

    public Arena() {
    	this(1);
    }
    /**
     * The default constructor. This creates the Arrays and ArrayLists. This needs to be 
     * called by the other constructors.
     * @param numTeams: the number of teams in the arena
     */
    public Arena(int numTeams) {
    	Log.debug("ARENA: starting generic arena object construction");
    	this.numTeams = numTeams;

    	teams = new ArenaTeam[numTeams];
    	for (int i = 0; i < numTeams; i++) {
	    	if (teams[i] == null) {
	    		teams[i] = new ArenaTeam();
	    	}
    	}
    	Log.debug("ARENA: finished generic arena object construction");
    }

  
    /**
     * Constructor that takes in two lists that contain the oids of the players
     * on each team.
     * @param numTeams
     * @param members
     * @param arenaGameType
     * @param arenaDatabaseID
     * @param arenaName
     * @param category
     * @param dur
     * @param goals
     * @param teamNames
     * @param spawnPoints
     * @param condition
     * @param id
     * @param minPlayers
     * @param instanceTemplateID
     * @param victoryPayment
     * @param defeatPayment
     * @param victoryExp
     * @param defeatExp
     * @param useWeapons
     */
    public Arena(int numTeams, ArrayList<QueueMember>[] members, 
    		int arenaGameType, int arenaDatabaseID, String arenaName, int category, int dur, int[] goals, String[] teamNames, 
    		Point[] spawnPoints, int condition, int id, int[] minPlayers, int instanceTemplateID, 
    		HashMap<Integer, Integer> victoryPayment, HashMap<Integer, Integer> defeatPayment,
    		int victoryExp, int defeatExp, boolean useWeapons) {
    	this(numTeams);
		if(Log.loggingDebug)Log.debug("ARENA: starting arena creation: " + id);

	    for (int i = 0; i < numTeams; i++) {
	    	teams[i].initialiseTeam(i, teamNames[i], goals[i], minPlayers[i], spawnPoints[i]);
	    	for (int j = 0; j < members[i].size(); j++) {
	    		QueueMember member = members[i].get(j);
	    		teams[i].addTeamMember(member.getOid(), member.getName(), member.getRace(), CombatPlugin.DEFAULT_MOVEMENT_SPEED, useWeapons, false);
	    	}
	    }
    	arenaID = id;
		ArenaPlugin.arenas.put(id,this);
    	this.arenaDatabaseID = arenaDatabaseID;
    	//arenaGameType = arenaGameType;
    	this.arenaName = arenaName;
    	this.category = category;
    	length = dur;
    	state = STATE_UNSET;
    	victoryCondition = condition;
    	this.goalType = -1;
    	
    	this.victoryPayment = victoryPayment;
    	this.defeatPayment = defeatPayment;
    	this.victoryXP = victoryExp;
    	this.defeatXP = defeatExp;
    	this.useWeapons = useWeapons;
    	
    	// Set the arena ID property for all players
    	for (int i = 0; i < numTeams; i++) {
    		for (ArenaMember member : teams[i].getTeamMembers()) {
    			OID oid = member.getOid();
    			EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID, arenaID);
    			//String playerRace = (String) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "race");
    		}
    		if (teams[i].getTeamSize() > 1)
    		    GroupClient.createGroup(teams[i].getTeamMembersOids());
    	}
    	
    	this.instanceTemplateID = instanceTemplateID;
		if(Log.loggingDebug)Log.debug("ARENA: finished arena creation: " + id);
    	setup();
    }
    
    /**
     * This is run once the Arena object is created. It will be responsible for teleporting players to their bases, sending 
     * the setup extension message to all players and then getting arenaStart() to be activated in 30 seconds.
     */
    public void setup() {
    	if (state != STATE_UNSET) {
    		if(Log.loggingDebug)Log.debug("ARENA: Arena " + arenaID + " has already been setup. State is not 0");
    		return;
    	}
    	state = STATE_SETUP;
    	
    	// Create a new instance
    	Template overrideTemplate = new Template();
    	overrideTemplate.put(Namespace.INSTANCE, InstanceClient.TEMPL_INSTANCE_NAME, "arena_" + arenaID);
    	instanceOid = InstanceClient.createInstance(instanceTemplateID, overrideTemplate);
    	AOVector dir = new AOVector();
    	for (int i = 0; i < numTeams; i++) {
    		BasicWorldNode node = new BasicWorldNode();
    		String markerName = "team" + i + "Spawn";
			if(Log.loggingWarn)Log.warn("ARENA: instanceOid=" + instanceOid + " markerName=" + markerName);
    		Marker spawn = InstanceClient.getMarker(instanceOid, markerName);
			if(Log.loggingWarn)Log.warn("ARENA: marker=" + spawn);
    		node.setInstanceOid(instanceOid);
    		node.setOrientation(spawn.getOrientation());
        	node.setLoc(spawn.getPoint());
        	node.setDir(dir);

	    	for (ArenaMember member : teams[i].getTeamMembers()) {
    			OID oid = member.getOid();
				InstanceClient.objectInstanceEntry(oid, node, InstanceClient.InstanceEntryReqMessage.FLAG_PUSH);
				if(Log.loggingDebug)
					Log.debug("ARENA Setup "+oid+" team "+i+" "+markerName+" move to arena "+instanceOid+" : " +instanceTemplateID+" loc "+spawn.getPoint()+" rot  "+spawn.getOrientation());
				EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "world", instanceTemplateID);
				// Also set some faction/attitude properties here
    			String factionOverride = arenaID + "_team" + i;
    			EnginePlugin.setObjectProperty(oid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, factionOverride);
    			WorldManagerClient.refreshWNode(oid);
			}
			if (teams[i].getTeamSize() > 0) {
				if(Log.loggingDebug)	Log.debug("ARENA: setting team " + i + " to active");
				teams[i].setTeamActive(true);
			} else {
				if(Log.loggingDebug)Log.debug("ARENA: setting team " + i + " to inactive");
				teams[i].setTeamActive(false);
			}
    	}
    	/*if (goalType == 0) {
    		// Set up team scores to be equal to number of players in each team
    		for (int i = 0; i < numTeams; i++) {
    			teamScore[i] = teams[i].size();
    		}
    	}*/
    	
    	int setupTime = 30;
    	sendMessageAll("Arena_setup", setupTime * 1000);
    	sendMessageAll("message_text", "Prepare yourself, the Arena will begin in " + setupTime + " seconds.");
    	
    	createSubs();
    	
    	
		ArenaStart arenaTimer = new ArenaStart();
		Engine.getExecutor().schedule(arenaTimer, setupTime, TimeUnit.SECONDS);
    }
    
    
    /**
     * Gets the list of skins the member can use. This is determined by the arena
     * category skins and the list of skins the user has purchased. 
     * @return
     */
    protected ArrayList<String> getUsableSkins() {
    	ArrayList<String> skins = ArenaPlugin.getArenaCategory(category).getSkins();
    	return skins;
    }
    
    public void setPlayerSkin(OID oid, String race) {
    	ArenaMember member = getArenaPlayer(oid);
    	member.setProperty("race", race);
		setPlayerProperty(oid, "race", race);
		setPlayerProperty(oid, "playerAppearance", 1);
    }
    
    protected void setPlayerTeamColour(OID oid, int teamColour) {
		//setPlayerProperty(oid, "skinColour", teamColour);
		setPlayerProperty(oid, "playerAppearance", 1);
    }
    
    protected void changePlayerRace(ArenaMember member, String race, boolean temporary) {
    	ArenaPlugin.sendChangePlayerRaceMessage(member.getOid(), race, temporary);
    }
    
    public LinkedList<Integer> getOpposingTeams(int team) {
    	Log.debug("FLAG: getting opposing teams");
    	LinkedList<Integer> opposingTeams = new LinkedList<Integer>();
    	for (int i = 0; i < numTeams; i++) {
    		if (i != team)
    			opposingTeams.add(i);
    	}
		if(Log.loggingDebug)Log.debug("FLAG: returning opposing teams: " + opposingTeams);
    	return opposingTeams;
    }
    
    /**
     * Creates a subscription for each player so we can catch their damage messages.
     */
    protected void createSubs() {
        // subscribe for some messages
    	for (int i = 0; i < numTeams; i++) {
    		for (ArenaMember member : teams[i].getTeamMembers()) {
    			OID oid = member.getOid();
    			SubjectFilter filter = new SubjectFilter(oid);
    	        //filter.addType(InventoryClient.MSG_TYPE_INV_UPDATE);
    	        filter.addType(CombatClient.MSG_TYPE_DAMAGE);
    	        member.setSub(Engine.getAgent().createSubscription(filter, this));
    		}
    	}
    }
    
    public void addArenaObject(ArenaObject aObject) {
    	HashMap<Integer, ArenaObject> xObjects;
    	if (arenaObjects.containsKey(aObject.getLoc().getX())) {
    		xObjects = arenaObjects.get(aObject.getLoc().getX());
    	} else {
    		xObjects = new HashMap<Integer, ArenaObject>();
    	}
    	xObjects.put((int)aObject.getLoc().getZ(), aObject);
    	arenaObjects.put((int)aObject.getLoc().getX(), xObjects);
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
		if(Log.loggingDebug)Log.debug("ARENA: got damage message for arena: " + arenaID + ". " + attackerOid + " dealt " + damage + " to " + victimOid);
    	
    	int victimTeam = -1;
		int attackerTeam = -1;
		for (int i = 0; i < numTeams; i++) {
			if (teams[i].hasMember(attackerOid))
				attackerTeam = i;
			if (teams[i].hasMember(victimOid))
				victimTeam = i;
		}
		
		teams[victimTeam].getTeamMember(victimOid).addDamageTaken(damage);
		if(Log.loggingDebug)Log.debug("ARENA: damage taken by " + victimOid + " is now: " + teams[victimTeam].getTeamMember(victimOid).getDamageTaken());
		sendStatMessageAll("Arena_stat_update", victimTeam, victimOid, "damageTaken", teams[victimTeam].getTeamMember(victimOid).getDamageTaken());
		if(!attackerOid.equals(victimOid)) {
			teams[attackerTeam].getTeamMember(attackerOid).addDamageDealt(damage);
			if(Log.loggingDebug)Log.debug("ARENA: damage dealt by " + attackerOid + " is now: " + teams[attackerTeam].getTeamMember(attackerOid).getDamageDealt());
			sendStatMessageAll("Arena_stat_update", attackerTeam, attackerOid, "damageDealt", teams[attackerTeam].getTeamMember(attackerOid).getDamageDealt());
		}
    }
    
    /**
     * Starts the arena. Sends a message to the client so the client can update the UI and
     * schedules the endArena executioner.
     *
     */
    public class ArenaStart implements Runnable {
		public void run() {
			if (state != STATE_SETUP) {
				if(Log.loggingDebug)Log.debug("ARENA: Arena " + arenaID + " is not starting. state is not 1");
				return;
			}
			state = STATE_RUNNING;
			// Send the message to get the gates so they can be despawned
	    	//ArenaClient.despawnGates(instanceOid, arenaID);
			despawnGates();
	    	sendMessageAll("message_text", "Fight!");
			
			startTime = (int) System.currentTimeMillis();
			sendMessageAll("arena_started", null);
			if (length != 0) {
				ArenaEnd arenaTimer = new ArenaEnd();
				Engine.getExecutor().schedule(arenaTimer, length, TimeUnit.SECONDS);
			}
		}
	}
    
    protected void despawnGates() {
    	for (int x : arenaObjects.keySet()) {
    		HashMap<Integer, ArenaObject> xObjects = arenaObjects.get(x);
    		for (int z : xObjects.keySet()) {
    			ArenaObject aObject = xObjects.get(z);
    			if (aObject.getObjectType().equals(ArenaObject.ARENA_OBJECT_GATE)) {
    				WorldManagerClient.despawn(aObject.getObjectOID());
    			}
    		}
    	}
    }
    
    protected abstract boolean addAbility(OID playerOid, int team);
    
    public void sendAbilities(OID playerOid) {
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "arena_abilities");
		// Fill props here
		int playerabilities[] = getArenaPlayer(playerOid).getAbilities();
		props.put("numAbilities", playerabilities.length);
		for (int i = 0; i < playerabilities.length; i++) {
			int abilityID = playerabilities[i];
			props.put("ability" + i + "ID", abilityID);
		}
		TargetedExtensionMessage msg = new TargetedExtensionMessage(
				WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, 
				playerOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
    }
    
    protected boolean addHealth(ArenaMember member) {
    	CombatClient.startAbility(300, member.getOid(), member.getOid(), null);
    	return true;
    }
    
    protected boolean addWeapon(ArenaMember member, String objectType) {
    	//ArenaWeapon weapon = (ArenaWeapon) member.getProperty(PROP_WEAPON);
    	HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("slot", 0);
    	if (objectType.equals(ArenaObject.ARENA_OBJECT_MELEE_WEAPON)) {
    		ArenaWeapon newWeapon = new ArenaWeapon(ArenaAbilities.ABILITY_MELEE_ATTACK, ArenaWeapon.ARENA_WEAPON_MELEE, 188);
        	member.setProperty(PROP_WEAPON, newWeapon);
        	setPlayerProperty(member.getOid(), "primaryItem", 188);
        	setPlayerProperty(member.getOid(), "playerAppearance", -1);
        	member.setAbility(0, ArenaAbilities.ABILITY_MELEE_ATTACK);
        	props.put("uses", newWeapon.getUses());
    	} else {
    		ArenaWeapon newWeapon = new ArenaWeapon(ArenaAbilities.ABILITY_RANGED_ATTACK, ArenaWeapon.ARENA_WEAPON_RANGED, 195);
        	member.setProperty(PROP_WEAPON, newWeapon);
        	setPlayerProperty(member.getOid(), "primaryItem", 195);
        	setPlayerProperty(member.getOid(), "playerAppearance", -1);
        	member.setAbility(0, ArenaAbilities.ABILITY_RANGED_ATTACK);
        	props.put("uses", newWeapon.getUses());
    	}
    	sendAbilities(member.getOid());
		sendMessageSingle("ability_uses", member.getOid(), props);
    	return true;
    }
    
    /**
     * The class to handle when the arenas timer has expired. This will check 
     * the victory condition and each teams score to determine a winner.
     *
     */
    public class ArenaEnd implements Runnable {
		public void run() {
			Log.debug("ARENA: time expired");
			
			// Check to see what the victoryCondition is
			if (victoryCondition == -1) {
				Log.debug("ARENA: victory Condition is 1");
				int winningTeam = 0;
				double maximum = (double)teams[0].getTeamScore() / (double)teams[0].getTeamGoal();
				if(Log.loggingDebug)Log.debug("ARENA: maximum: " + maximum);
			    for (int i = 1; i < numTeams; i++) {
			    	double goalPercent = (double)teams[i].getTeamScore() / (double)teams[i].getTeamGoal();
					if(Log.loggingDebug)Log.debug("ARENA: team: " + i + " goal percent");
			    	//TODO: Have some way of handling draws
			        if (goalPercent > maximum) {
			            maximum = goalPercent;
			            winningTeam = i;
						if(Log.loggingDebug) Log.debug("ARENA: setting winning team to " + i);
			        } else if (goalPercent == maximum) {
			        	//maximum = goalPercent;
			            winningTeam = -1;
			            Log.debug("ARENA: setting winningTeam to -1");
			        }
			    }
			    Log.debug("ARENA: about to run end game");
				endGame(winningTeam);
			} else {
				endGame(victoryCondition);
			}
		}
    }
    
    /**
     * Marks a team as defeated and will check to see if there is only 1 team 
     * left (making them the winners).
     * @param team: the number of the team that was defeated.
     */
    private void teamDefeated(int team) {
    	teams[team].setTeamActive(false);
    	LinkedList<Integer> teamsActive = new LinkedList<Integer>();
		if(Log.loggingDebug)Log.debug("ARENA: team " + team + " has been defeated.");
    	for (int i = 0; i < numTeams; i++) {
    		if (teams[i].getTeamActive() == true)
    			teamsActive.add(i);
    	}

		if(Log.loggingDebug)Log.debug("ARENA: number of teams active: " + teamsActive.size());
    	if (teamsActive.size() == 1) {
    		endGame(teamsActive.get(0));
    	} else if (teamsActive.size() < 1) {
    		Log.debug("ARENA: there are no active teams left in the arena");
    		endGame(-1);
    	}
    }
    
    /**
     * Ends this arena instance.
     * 
     * @param winningTeam: An integer representing the winning team
     */
    private void endGame(int winningTeam) {
    	if (state != STATE_RUNNING) {
			if(Log.loggingDebug)Log.debug("ARENA: Arena " + arenaID + " is not running. state is not 2");
			return;
		}
		state = STATE_END;
		if (scheduledExecutioner != null)
			scheduledExecutioner.cancel(true);
		logArenaResult(winningTeam);
		
		if (winningTeam != -1) {
			sendChatMessageAll(teams[winningTeam].getTeamName() + " is victorious!");
		}

		// Log it
        HashMap<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("arenaID", arenaID);
        DataLoggerClient.logData("ARENA_ENDED", null, null, null, props);

		if(Log.loggingDebug)Log.debug("ARENA: endGame hit with winner: " + winningTeam);
    	sendMessageAll("Arena_end", null);
    	
    	for (int i = 0; i < numTeams; i++) {
    		for (ArenaMember member : teams[i].getActiveMembers()) {
    			OID oid = member.getOid();
				EnginePlugin.setObjectProperty(oid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, "");
				//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, 0);
				BasicWorldNode bwNode = WorldManagerClient.getWorldNode(oid);
//				bwNode.setDir(new AOVector());
//				WorldManagerClient.updateWorldNode(oid, bwNode);
			}
		}
		
		ArenaCleanup arenaCleanup = new ArenaCleanup();
		Engine.getExecutor().schedule(arenaCleanup, 10, TimeUnit.SECONDS);
		
		for (int i = 0; i < numTeams; i++) {
			if (i == winningTeam) {
				sendMessageTeam("message_text", i, "Victory");
			} else if (winningTeam == -1) { // DRAW
				sendMessageTeam("message_text", i, "It's a draw?");
			} else {
				sendMessageTeam("message_text", i, "Defeat");
			}
		}
		
		Log.debug("ARENA: rating calcs 1");
		// Calculate the rating total for both teams
		ArenaStats.CalculateRatings(arenaGameType, arenaDatabaseID, teams, winningTeam, victoryPayment, defeatPayment, victoryXP, defeatXP);
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
			// Finally lets remove this Arena object from the Map of current Arenas
			ArenaClient.endArena(arenaID);
		}
    }
    
    /**
     * Teleports all players back to their bases. While this is not currently used
     * it will probably have a use at a later date
     */
    protected void teleportAllBase() {
		BasicWorldNode tnode = new BasicWorldNode();
		for (int i = 0; i < numTeams; i++) {
			String markerName = "team" + i + "Spawn";
			Marker spawn = InstanceClient.getMarker(instanceOid, markerName);
			for (ArenaMember member : teams[i].getActiveMembers()) {
    			OID oid = member.getOid();
				tnode.setLoc(spawn.getPoint());
				WorldManagerClient.updateWorldNode(oid, tnode, true);
			}
		}
	}
    
    /**
     * Goes through all the players in the arena and calls teleportOut. This
     * will also delete the instance, so do not use this unless the instance
     * has finished.
     */
    protected void teleportAllOut() {
		Log.debug("ARENA: teleporting all players out");
		for (int i = 0; i < numTeams; i++) {
			for (ArenaMember member : teams[i].getActiveMembers()) {
    			OID oid = member.getOid();
				teleportOut(oid);
				Engine.getAgent().removeSubscription(member.getSub());
			}
		}
		InstanceClient.deleteInstance(instanceOid);
	}
    
    /**
     * Teleports the specified player out of the instance. 
     * @param oid: The oid of the player being teleported out
     */
    protected void teleportOut(OID oid) {
		if(Log.loggingDebug)Log.debug("ARENA: teleporting out player: " + oid);
    	sendMessageSingle("Arena_Left", oid, null);
		// We better release them in case they were dead
		//CombatClient.releaseObject(oid); - No one dies in Smoo (yet)
		// Then make sure they are able to be moved
		Map<String, Serializable> wmgrprops = new HashMap<String,Serializable>();
		wmgrprops.put(WorldManagerClient.WORLD_PROP_NOMOVE, false);
		wmgrprops.put(WorldManagerClient.WORLD_PROP_NOTURN, false);
		EnginePlugin.setObjectProperties(oid, WorldManagerClient.NAMESPACE, wmgrprops);
		// They also now need to be attackable again
    	// Reset their speed
		Log.debug("RESTORE: resetting speed");
    	//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, CombatPlugin.DEFAULT_MOVEMENT_SPEED);
		// Now finally change instance
		if(Log.loggingDebug)Log.debug("ARENA: teleportOut "+oid);
    	AgisWorldManagerClient.returnToLastInstance(oid);
		// Reset colour
    	/*EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "skinColour", 1);
    	String previousRace = (String)EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "race");
    	changePlayerRace(getArenaPlayer(oid), previousRace, false);*/
		// Reset their state
		//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "state", 0);
		EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID, -1);
		// Remove them from the group
		Log.debug("RESTORE: removing from group");
		GroupClient.removeMember(oid);
		EnginePlugin.setObjectProperty(oid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, "");
		// Log it
        HashMap<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("arenaID", arenaID);
        OID accountID = (OID) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "accountId");
        DataLoggerClient.logData("PLAYER_LEFT_ARENA", oid, null, accountID, props);
		if(Log.loggingDebug)Log.debug("ARENA: finished teleporting player "+oid);
    }
    
    public void activateMachine(OID oid, int machineID) {
    	ArenaObject aObject = getArenaObject("Machine-" + machineID);
    	if (aObject == null) {
    		return;
    	}
    }
    
    private ArenaObject getArenaObject(String objectName) {
    	
    	return null;
    }
    
    /**
     * Removes a player from the arena. 
     * 
     * @param oid: The player being removed from the arena
     */
    public void removePlayer(OID oid, boolean teleport) {
		if(Log.loggingDebug)Log.debug("ARENA: removing player: " + oid);
    	int team = -1;
    	
    	team = getPlayerTeam(oid);

    	if (team < 0) {
    		Log.warn("ARENA: trying to remove a player: " + oid + " who is not on any team");
    		return;
    	}
    		
    	
    	sendChatMessageAll("Player " + teams[team].getTeamMember(oid).getName() + " has left the arena.");
    	sendMessageAll("arena_player_left", oid);
    	ArenaMember member = teams[team].removePlayer(oid);
    	
    	if (state == STATE_RUNNING) {
    	    if (!teams[team].getTeamActive()) {
    		    teamDefeated(team);
    	    } else if (goalType == 0) {
    		    alterTeamScore(team, -1);
    	    }
    	}
    	if (teleport) {
			try {
				teleportOut(oid);
			}catch (Exception e){
				Log.exception("teleportOut ",e);
			}
    	}
    	
    	//Deactivate the member object
		if(member!=null)
			member.deactivate();
		if(Log.loggingDebug)Log.debug("ARENA: removing player: " + oid+" END");
    }
    
    public void handleDeath(OID attackerOid, OID victimOid) {
		if(Log.loggingDebug)Log.debug("ARENA: handleDeath hit. Attacker: " + attackerOid + "; victim: " + victimOid);

		if (state != STATE_RUNNING) {
			Log.debug("ARENA: handleDeath(): Arena is not running. State is not 2.");
			return;
		}
    	
    	// We need to prevent the player from being attacked for the rest of the arena
    	//EnginePlugin.setObjectPropertyNoResponse(victimOid, CombatClient.NAMESPACE, "attackable", false);
    	
		/*if (attackerOid == victimOid) {
			Log.debug("ARENA: handleDeath(): Suicide attempt. AttackerOid equals VictimOid");
			return;
		}*/
		
		// First figure out the team of victim and killer
		int victimTeam = -1;
		int attackerTeam = -1;
		Log.debug("ARENA: checking arena death teams");
		for (int i = 0; i < numTeams; i++) {
			Log.debug("ARENA: checking arena death teams for team: " + i);
			if (teams[i].hasMember(attackerOid))
				attackerTeam = i;
			if (teams[i].hasMember(victimOid))
				victimTeam = i;
		}
		Log.debug("ARENA: finished checking arena death teams");
		
		//if (victimTeam == -1 || attackerTeam == -1)
		//	return;
		
		// Next move the victim back to their base
		/*BasicWorldNode tnode = new BasicWorldNode();
		String markerName = "team" + victimTeam + "Spawn";
		Marker spawn = InstanceClient.getMarker(instanceOid, markerName);
		tnode.setLoc(spawn.getPoint());
		WorldManagerClient.updateWorldNode(victimOid, tnode, true);*/


		if(Log.loggingDebug)Log.debug("ARENA: updating individual stat updates for attacker: " + attackerTeam + " and victim: " + victimOid);
		addIndividualScore(attackerTeam, attackerOid, 1);
		addIndividualKill(attackerTeam, attackerOid, 1);
		addIndividualDeath(victimTeam, victimOid, 1);
		// Now update the stats as needed
		if (goalType == 0)
		    alterTeamScore(victimTeam, -1);
		else
			alterTeamScore(attackerTeam, 1);
    }
    
    /**
     * This will be called whenever one player kills another. This function 
     * is called from the ArenaPlugin which has a hook to catch the kill message.
     * @param attackerOid: the oid of the attacker
     * @param victimOid: the oid of the victim
     */
    public void addKillScore(OID attackerOid, OID victimOid) {
		if(Log.loggingDebug)	Log.debug("ARENA: addKillScore hit. Attacker: " + attackerOid + "; victim: "
    			+ victimOid);
		
    	if (state != STATE_RUNNING) {
    		Log.debug("ARENA: addKillScore: Arena is not running. State is not 2.");
    		return;
		}
		if (attackerOid == victimOid) {
			Log.debug("ARENA: addKillScore: Suicide attempt. AttackerOid equals VictimOid");
			return;
		}
		
		// First figure out the team of victim and killer
		int victimTeam = -1;
		int attackerTeam = -1;
		for (int i = 0; i < numTeams; i++) {
			if (teams[i].hasMember(attackerOid))
				attackerTeam = i;
			else if (teams[i].hasMember(victimOid))
				victimTeam = i;
		}
		
		if (victimTeam == -1 || attackerTeam == -1)
			return;
		
		// Next move the victim back to their base
		BasicWorldNode tnode = new BasicWorldNode();
		String markerName = "team" + victimTeam + "Spawn";
		Marker spawn = InstanceClient.getMarker(instanceOid, markerName);
		tnode.setLoc(spawn.getPoint());
		tnode.setDir(new AOVector());
		WorldManagerClient.updateWorldNode(victimOid, tnode, true);
		WorldManagerClient.refreshWNode(victimOid);
		
		// Now update the stats as needed
		alterTeamScore(attackerTeam, 1);
		addIndividualScore(attackerTeam, attackerOid, 1);
		addIndividualKill(attackerTeam, attackerOid, 1);
		addIndividualDeath(victimTeam, victimOid, 1);
        // Apply a stun effect
        CombatClient.startAbility(-502, attackerOid, victimOid, null);
    }
    
    public abstract void activateAbility(OID playerOid, OID targetOid, int slot);
    
    public abstract void completeTutorial(OID oid);
    
    /**
     * Add the score to the teams score.
     * 
     * @param team: Which team to add score to.
     * @param score: Score value to add. Can be negative.
     */
    protected abstract void alterTeamScore(int team, int score) ;
    
    public abstract void releaseRequest(OID playerOid);
    
    /**
     * Add the score to the players individual score.
     * @param team: the team the player belongs to
     * @param oid: the oid of the player
     * @param score: the score value to add. Can be negative.
     */
    protected void addIndividualScore(int team, OID oid, int score) {
    	ArenaMember member = teams[team].getTeamMember(oid);
    	member.updateScore(score);
		if(Log.loggingDebug)Log.debug("ARENA: about to send individual score for player: " + oid);
		sendStatMessageAll("Arena_stat_update", team, oid, "score", member.getScore());
    }
    
    /**
     * Add the score to the players individual score.
     * @param team: the team the player belongs to
     * @param oid: the oid of the player
     * @param adjustment: the value to adjustment the kill variable by
     */
    protected void addIndividualKill(int team, OID oid, int adjustment) {
    	ArenaMember member = teams[team].getTeamMember(oid);
    	member.addKill();
		if(Log.loggingDebug)Log.debug("ARENA: about to send individual kills for player: " + oid);
		sendStatMessageAll("Arena_stat_update", team, oid, "kill", member.getKills());
    }
    
    /**
     * Add the score to the players individual score.
     * @param team: the team the player belongs to
     * @param oid: the oid of the player
     * @param adjustment: the value to adjustment the death variable by
     */
    protected void addIndividualDeath(int team, OID oid, int adjustment) {
    	ArenaMember member = teams[team].getTeamMember(oid);
    	member.addDeath();
		if(Log.loggingDebug)Log.debug("ARENA: about to send individual deaths for player: " + oid);
		sendStatMessageAll("Arena_stat_update", team, oid, "death", member.getDeaths());
    }
    
    /**
     * Searches the target team for the index of the player
     * 
     * @param oid: Player object id
     * @return Index:the position of the player within the team list. Returns -1 
     * if the player doesn't exist in that team.
     */
    public int getPlayerTeam(OID oid) {
    	for (int i = 0; i < teams.length; i++) {
    		if (teams[i].hasMember(oid))
    			return i;
    	}
    	return -1;
    }
    
    public ArenaMember getArenaPlayer(OID oid) {
    	for (int i = 0; i < teams.length; i++) {
    		if (teams[i].hasMember(oid)) {
    			return teams[i].getTeamMember(oid);
    		}
    	}
    	return null;
    }

	public boolean isActivePlayer(OID oid) {
		for (int i = 0; i < teams.length; i++) {
			if (teams[i].isActiveTeamMember(oid)) {
				return true;
			}
		}
		return false;
	}

	/**
     * Calls sendMessageTeam for each team in the arena.
     * @param msgType: the message type
     * @param data: some form of data to be sent
     */
    public void sendMessageAll(String msgType, Serializable data) {
    	for (int i = 0; i < numTeams; i++) {
    		sendMessageTeam(msgType, i, data);
    	}
    }
    
    /**
     * Calls sendMessageSingle for each member in the specified team.
     * @param msgType: the message type
     * @param team: the team to send the message to
     * @param data: some form of data to be sent
     */
    public void sendMessageTeam(String msgType, int team, Serializable data) {
    	for (ArenaMember member : teams[team].getActiveMembers()) {
    		sendMessageSingle(msgType, member.getOid(), data);
    	}
    }
    
    /**
     * Sends an extension message of the specified type to the specified player
     * @param msgType: the message type
     * @param oid: the oid to send the message to
     * @param data: some form of data to be sent
     */
    public boolean sendMessageSingle(String msgType, OID oid, Serializable data) {
    	boolean handled = false;
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", msgType);
		// Check for type and fill props here
		if (msgType == "arena_started") {
			int timeLeft = (int)System.currentTimeMillis() - startTime;
			timeLeft = (length * 1000) - timeLeft;
			props.put("timeLeft", timeLeft);
			props.put("goalType", goalType);
			handled = true;
		} else if (msgType == "message_text") {
			String value = (String) data;
			props.put("state", state);
			props.put("message", value);
			handled = true;
		} else if (msgType == "arena_player_left") {
			props.put("playerOid", data);
			handled = true;
		} else if (msgType == "arena_countdown") {
			props.put("setupLength", data);
			handled = true;
		} else if (msgType == "arena_setup") {
			props.put("arenaType", arenaGameType);
			props.put("arenaCategory", category);
			props.put("arenaTeam", this.getPlayerTeam(oid));
			props.put("numTeams", numTeams);
			
			for (int i = 0; i < numTeams; i++) {
				props.put("teamGoal" + i, teams[i].getTeamGoal());
				props.put("teamScore" + i, teams[i].getTeamScore());
				props.put("teamName" + i, teams[i].getTeamName());
				props.put("teamSize" + i, teams[i].getTeamSize());
				for (int j = 0; j < teams[i].getTeamSize(); j++) {
					props.put("team" + i + "OID" + j, teams[i].getTeamMember(j).getOid());
					props.put("team" + i + "Name" + j, teams[i].getTeamMember(j).getName());
					props.put("team" + i + "Score" + j, teams[i].getTeamMember(j).getScore());
					props.put("team" + i + "Kills" + j, teams[i].getTeamMember(j).getKills());
					props.put("team" + i + "Deaths" + j, teams[i].getTeamMember(j).getDeaths());
					props.put("team" + i + "DamageTaken" + j, teams[i].getTeamMember(j).getDamageTaken());
					props.put("team" + i + "DamageDealt" + j, teams[i].getTeamMember(j).getDamageDealt());
				}
			}
			// Send down list of skins for this category
		/*	ArrayList<String> usableSkins = getUsableSkins();
			for (int i = 0; i < usableSkins.size(); i++) {
				props.put("skin" + i, usableSkins.get(i));
			}
			props.put("numSkins", usableSkins.size());*/
			handled = true;
		} else if (msgType == "arena_ready") {
			handled = true;
		} else if (msgType == "Arena_event") {
			props.put("eventMessage", data);
			handled = true;
		} else if (msgType == "arena_end") {
			/*HashMap<OID, Integer> ratingAdjustments = (HashMap)data;
			int count = 0;
			for (OID playerOid : ratingAdjustments.keySet()) {
				props.put("playerOid" + count, playerOid);
				props.put("playerTeam" + count, getPlayerTeam(playerOid));
				props.put("playerAdjustment" + count, ratingAdjustments.get(playerOid));
				ArenaStats stats = ArenaPlugin.getPlayerArenaStats(playerOid);
		    	ArenaTypeStats typeStats = stats.getArenaTypeStats(arenaGameType);
				props.put("playerRating" + count, typeStats.getRating());
				count++;
			}
			props.put("numRatings", ratingAdjustments.size());*/
			handled = true;
		} else if (msgType == "ability_uses") {
			HashMap<String, Serializable> inProps = (HashMap)data;
			props.put("slot", inProps.get("slot"));
			props.put("uses", inProps.get("uses"));
			handled = true;
		}
		
		if (handled) {
			TargetedExtensionMessage msg = new TargetedExtensionMessage(
				WorldManagerClient.MSG_TYPE_EXTENSION, oid, 
				oid, false, props);
			Engine.getAgent().sendBroadcast(msg);
		}
		return handled;
    }
    
    /**
     * Calls sendStateMessageSingle for each player in the arena.
     * @param msgType: the message type
     * @param team: the team which the player whose stat is being updated is from
     * @param oid: the oid of the player whose stat was changed
     * @param stat: the type of stat that was changed
     * @param score: the value of the stat that was changed
     */
    protected void sendStatMessageAll(String msgType, int team, OID oid, String stat, int score) {
    	for (int i = 0; i < numTeams; i++) {
    		for (ArenaMember member : teams[i].getActiveMembers()) {
    			sendStatMessageSingle(msgType, member.getOid(), team, oid, stat, score);
    		}
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
    protected void sendStatMessageSingle(String msgType, OID oid, int team, OID scoreOid,  
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
    protected void sendChatMessageAll(String msg) {
    	for (int i = 0; i < numTeams; i++) {
    		sendChatMessageTeam(msg, i);
    	}
    }
    
    /**
     * Calls sendChatMessageSingle for each player in the specified team.
     * @param msg: the chat message to send
     * @param team: the team to send the chat message to
     */
    protected void sendChatMessageTeam(String msg, int team) {
    	for (ArenaMember member : teams[team].getActiveMembers()) {
	    	sendChatMessageSingle(msg, member.getOid());
	    }
    }
    
    /**
     * Sends the specified chat message to the specified player
     * @param msg: the chat message to send
     * @param oid: the oid of the player to send the message to
     */
    protected void sendChatMessageSingle(String msg, OID oid) {
    	ChatClient.sendObjChatMsg(oid, 2, msg);
    }
    
    /**
     * Sends a property message with the given properties for the player. This allows
     * everyone in the arena to receive the property while not storing it permanently.
     * @param oid
     * @param prop
     * @param value
     */
    public void setPlayerProperty(OID oid, String prop, Serializable value) {
    	PropertyMessage propMsg = new PropertyMessage(oid, oid);
    	propMsg.setProperty(prop, value);
		Engine.getAgent().sendBroadcast(propMsg);
    }
    
    protected void logArenaResult(int winningTeam) {
    	HashMap<String, Serializable> map = new HashMap<String, Serializable>();
    	map.put("arenaName", arenaName);
    	map.put("winningTeam", winningTeam);
    	map.put("numTeams", numTeams);
    	for (int i = 0; i < numTeams; i++) {
    		ArenaTeam team = teams[i];
    		map.put("teamName" + i, team.getTeamName());
    		map.put("teamGoal" + i, team.getTeamGoal());
    		map.put("teamScore" + i, team.getTeamScore());
    	}
    	int timeLeft = (int)System.currentTimeMillis() - startTime;
    	map.put("timeLeft", timeLeft);
    	DataLoggerClient.logData("ARENA_RESULT", null, null, null, map);
    }
    
    public int getArenaID() { return arenaID; }
    public void setArenaID(int arenaID) { this.arenaID = arenaID; }
    
    public OID getArenaInstanceOid() { return instanceOid; }
    public void setArenaInstanceOid(OID instanceOid) { this.instanceOid = instanceOid; }
    
    public int getArenaType() { return arenaGameType; }
    public void setArenaType(int arenaGameType) { this.arenaGameType = arenaGameType; }
    
    public int getArenaCategory() { return category; }
    public void setArenaCategory(int category) { this.category = category; }
    
    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }
    
    public int getInstanceTemplateID() { return instanceTemplateID; }
    public void setInstanceTemplateID(int instanceTemplateID) { this.instanceTemplateID = instanceTemplateID; }
    
    public int getState() { return state; }
    public void setState(int state) { this.state = state; }
    
    public ArenaTeam[] getTeams() { return teams; }
    public void setTeams(ArenaTeam[] teams) { this.teams = teams; }
    
    public ArenaTeam getTeam(int team) { return teams[team]; }
    
    private static final int numAbilities = 3;
    protected static int gridSize = 3000;
    protected static int centreX = 0;
    protected static int centreY = 0;
    protected static int centreZ = 0;
    
    protected static final int STATE_UNSET = 0;
    protected static final int STATE_SETUP = 1;
    protected static final int STATE_RUNNING = 2;
    protected static final int STATE_END = 3;
     
    protected static final int PLAYER_INACTIVE = -1;
    protected static final int PLAYER_IMMUNE = 1;
    
  //  public static final String PROP_HEALTH = "health";
   // protected static final String PROP_MAX_HEALTH = "max_health";
    public static final String PROP_WEAPON = "weapon";

    private static final long serialVersionUID = 1L;
}
