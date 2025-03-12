package atavism.agis.arenas;

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
import atavism.agis.database.AccountDatabase;
import atavism.agis.objects.*;
import atavism.agis.objects.ArenaQueue.QueueMember;
import atavism.agis.plugins.*;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.server.math.*;

/**
 * The Arena class handles an arena instance. It has numerous scheduled executors and a state
 * variable to control the flow of the arena from setup to the end. This class is complete, it 
 * relies on no other class to perform it's duties.
 * @author Andrew Harrison
 *
 */
public class DeathmatchArena extends Arena implements Serializable, MessageDispatch {

    /**
     * The default constructor. This creates the Arrays and ArrayLists. This needs to be 
     * called by the other constructors.
     * @param numTeams: the number of teams in the arena
     */
    public DeathmatchArena(int numTeams) {
    	Log.debug("MUNCH: starting generic arena object construction");
    	this.numTeams = numTeams;

    	teams = new ArenaTeam[numTeams];
    	for (int i = 0; i < numTeams; i++) {
	    	if (teams[i] == null) {
	    		teams[i] = new ArenaTeam();
	    		if(Log.loggingDebug)	Log.debug("MUNCH: created team: " + i);
	    	}
    	}
    	Log.debug("MUNCH: finished generic arena object construction");
    }

    /**
     * Constructor that takes in two lists that contain the oids of the players
     * on each team.
     */
	public DeathmatchArena(int numTeams, ArrayList<QueueMember> members[], int type, int arenaDatabaseID, String arenaName, int category, int dur, int[] goals, String[] teamNames, Point[] spawnPoints, int condition, int id, int[] minPlayers,
			int instanceTemplateID, HashMap<Integer, Integer> victoryPayment, HashMap<Integer, Integer> defeatPayment, int victoryExp, int defeatExp, boolean useWeapons) {
    	this(numTeams);
    	if(Log.loggingDebug)	Log.debug("ARENA: starting arena creation: " + id);

	    for (int i = 0; i < numTeams; i++) {
	    	teams[i].initialiseTeam(i, teamNames[i], goals[i], minPlayers[i], spawnPoints[i]);
	    	for (int j = 0; j < members[i].size(); j++) {
	    		QueueMember member = members[i].get(j);
	    		int base_speed =  CombatPlugin.DEFAULT_MOVEMENT_SPEED;
	    		teams[i].addTeamMember(member.getOid(), member.getName(), member.getRace(), base_speed, useWeapons, true);
	    		GroupClient.removeMember(member.getOid());
	    	}
	    }
    	arenaID = id;
		ArenaPlugin.arenas.put(id,this);
    	this.arenaDatabaseID = arenaDatabaseID;
    	arenaGameType = type;
    	this.arenaName = arenaName;
    	this.category = category;
    	length = dur;
    	state = STATE_UNSET;
    	victoryCondition = condition;
    	this.goalType = 1;//0 Counting Team Score; 1 Increasing Team Score
    	
    	this.victoryPayment = victoryPayment;
    	this.defeatPayment = defeatPayment;
    	this.victoryXP = victoryExp;
    	this.defeatXP = defeatExp;
    	this.useWeapons = useWeapons;
    	
    	// Set the arena ID property for all players
    	for (int i = 0; i < numTeams; i++) {
    		if(Log.loggingDebug)Log.debug("MUNCH: getting members for team: " + i);
    		for (ArenaMember member : teams[i].getTeamMembers()) {
    			OID oid = member.getOid();
    			EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID, arenaID);
    			//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, 0);
    			// Set member properties
    			member.setProperty("state", 0);
    			member.setProperty("cooldownEnds", System.currentTimeMillis());
    			//member.setAbility(0, ArenaAbilities.ABILITY_ATTACK);
    		}
    		if (teams[i].getTeamSize() > 1)
    		    GroupClient.createGroup(teams[i].getTeamMembersOids());
    	}
    	
    	this.instanceTemplateID = instanceTemplateID;
    	if(Log.loggingDebug)	Log.debug("ARENA: finished arena creation: " + id);
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
    	overrideTemplate.setName("arena_" + arenaID);
    	
    	instanceOid = InstanceClient.createInstance(instanceTemplateID, overrideTemplate);
    	if(Log.loggingDebug)Log.debug("ZBDS New Instance ID="+InstanceClient.getInstanceOid("arena_" + arenaID));
    	AOVector dir = new AOVector();
    	for (int i = 0; i < numTeams; i++) {
    		BasicWorldNode node = new BasicWorldNode();
    		//String markerName = "team" + i + "Spawn";
    		String markerName = "spawn";
    		if(Log.loggingWarn)	Log.warn("ARENA: instanceOid=" + instanceOid + " markerName=" + markerName);
    		//Marker spawn = InstanceClient.getMarker(instanceOid, markerName);
    		//Log.warn("ARENA: marker=" + spawn);
    		node.setInstanceOid(instanceOid);
    		//node.setOrientation(spawn.getOrientation());
        	node.setLoc(teams[i].getSpawnPoint());
        	node.setDir(dir);
        	LinkedList<OID> playersDisconnected = new LinkedList<OID>();
        	for (ArenaMember member : teams[i].getTeamMembers()) {
    			OID oid = member.getOid();
    			try {
    				// Use the Push flag so it will save where the player currently is before they get teleported into the Arena
    				InstanceClient.objectInstanceEntry(oid, node, InstanceClient.InstanceEntryReqMessage.FLAG_PUSH);
    				EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "world", instanceTemplateID);
    				// Also set some faction/attitude properties here
        			String factionOverride = arenaID + "_team" + i;
        			EnginePlugin.setObjectProperty(oid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, factionOverride);
        			
        			WorldManagerClient.refreshWNode(oid);
    			} catch (NoRecipientsException e) {
    				Log.error("ARENA: could not teleport player " + oid + " into arena instance");
    				playersDisconnected.add(oid);
    			}
    			
    			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, true);
    			//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, true);
    		
    			
    			
			}
	    	// Remove any players who have already disconnected
	    	for (OID playerOid : playersDisconnected)
	    		removePlayer(playerOid, false);
			if (teams[i].getTeamSize() > 0) {
				if(Log.loggingDebug)Log.debug("ARENA: setting team " + i + " to active");
				teams[i].setTeamActive(true);
			} else {
				if(Log.loggingDebug)Log.debug("ARENA: setting team " + i + " to inactive");
				teams[i].setTeamActive(false);
			}
    	}
    	if (goalType == 0) {
    		// Set up team scores to be equal to number of players in each team
    		for (int i = 0; i < numTeams; i++) {
    			teams[i].setTeamScore(teams[i].getTeamSize());
    		}
    	}
    	
    	createSubs();
    	
		ArenaCountdown arenaCountdown = new ArenaCountdown();
		Engine.getExecutor().schedule(arenaCountdown, 5, TimeUnit.SECONDS);
		
		sendMessageAll("arena_setup", null);
    }
    
    public class ArenaCountdown implements Runnable {
    	public void run() {
    		int setupTime = 20;
    		sendMessageAll("arena_countdown", setupTime * 1000);
        	//sendMessageAll("message_text", "Prepare yourself, the Arena will begin in " + setupTime + " seconds.");
    		
        	ArenaReady arenaTimer = new ArenaReady();
    		Engine.getExecutor().schedule(arenaTimer, setupTime - 5, TimeUnit.SECONDS);
    	}
    }
    
    public class ArenaReady implements Runnable {
    	public void run() {
        	sendMessageAll("arena_ready", null);
        	
    		ArenaStart arenaTimer = new ArenaStart();
    		Engine.getExecutor().schedule(arenaTimer, 5, TimeUnit.SECONDS);
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
            Log.error("DeathmatchArena: unknown msg: " + msg);
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
		if (victimTeam > -1) {
			teams[victimTeam].getTeamMember(victimOid).addDamageTaken(damage);
			if(Log.loggingDebug)Log.debug("ARENA: damage taken by " + victimOid + " is now: " + teams[victimTeam].getTeamMember(victimOid).getDamageTaken());
			sendStatMessageAll("Arena_stat_update", victimTeam, victimOid, "damageTaken", teams[victimTeam].getTeamMember(victimOid).getDamageTaken());
		} else {
			if(Log.loggingDebug)Log.debug("ARENA: victim player no found to take damage! victim: " + victimOid);
		}
		if (attackerTeam > -1) {
			if (!attackerOid.equals(victimOid)) {
				teams[attackerTeam].getTeamMember(attackerOid).addDamageDealt(damage);
				if(Log.loggingDebug)Log.debug("ARENA: damage dealt by " + attackerOid + " is now: " + teams[attackerTeam].getTeamMember(attackerOid).getDamageDealt());
				sendStatMessageAll("Arena_stat_update", attackerTeam, attackerOid, "damageDealt", teams[attackerTeam].getTeamMember(attackerOid).getDamageDealt());
			}
		} else {
			if(Log.loggingDebug)Log.debug("ARENA: attacker player no found to deal damage! attacker: " + attackerOid);

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
				Log.debug("ARENA: Arena " + arenaID + " is not starting. state is not 1");
				return;
			}
			state = STATE_RUNNING;
			// First make sure there are at least 2 activeTeams left (incase of disconnects etc)
			int numActiveTeams = 0;
			for (int i = 0; i < numTeams; i++) {
				if (teams[i].getTeamActive())
					numActiveTeams++;
			}
			if (numActiveTeams < 2 && numTeams != 1) {
				endGame(-1);
				return;
			}
			for (int i = 0; i < numTeams; i++) {
	    		for (ArenaMember member : teams[i].getTeamMembers()) {
	    			OID oid = member.getOid();
	    			//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, baseMovementSpeed);
	    			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, false);
	    			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
	    		
	    		}
	    	}
	    	sendMessageAll("message_text", "Fight!");
			
			startTime = (int) System.currentTimeMillis();
			sendMessageAll("arena_started", null);
			if (length != 0) {
				ArenaEnd arenaTimer = new ArenaEnd();
				Engine.getExecutor().schedule(arenaTimer, length, TimeUnit.SECONDS);
			}
			for (int i = 0; i < numTeams; i++) {
				for (ArenaMember member : teams[i].getTeamMembers()) {
					OID oid = member.getOid();
					sendAbilities(oid);
				}
			}
			
			CollisionCheck collisionCheck = new CollisionCheck();
			// Run the collision check every half a second
			scheduledExecutioner = Engine.getExecutor().scheduleAtFixedRate(collisionCheck, 1, 50, TimeUnit.MILLISECONDS);
		}
	}
    
    /**
     * Iterates through every player in the arena and compares their location to 
     * all the other players in the other teams.
     *
     */
    public class CollisionCheck implements Runnable {
		public void run() {
			if (state != STATE_RUNNING)
				return;
			//Log.debug("ARENA: doing a collision check");
			for (int i = 0; i < numTeams; i++) {
				for (ArenaMember member : teams[i].getActiveMembers()) {
	    			OID oid = member.getOid();
					WorldManagerClient.refreshWNode(oid);
					BasicWorldNode node = WorldManagerClient.getWorldNode(oid);
					Point loc = node.getLoc();
					objectCheck(loc, member);
				}
			}
		}
    }
    
    private void objectCheck(Point loc, ArenaMember member) {
    	OID oid = member.getOid();
    	int tileX = getGridXTile(loc.getX());
    	int tileZ = getGridZTile(loc.getZ());
    	ArenaGridBlock block = arenaGrid.get(tileX).get(tileZ);
    	if (!block.hasObject()) {
    		Log.debug("BC: no object found in tile: " + tileX + "/" + tileZ);
    		return;
    	}
    	ArenaObject aObject = (ArenaObject) block.getObjectInBlock();
    	if(Log.loggingDebug)Log.debug("BC: found object + " + aObject.getObjectType() + " in tile: " + tileX + "/" + tileZ);
    	if (aObject.getObjectType().equals(ArenaObject.ARENA_OBJECT_ABILITY) && aObject.getActive()) {
			if (addAbility(oid, member.getTeam())) {
				WorldManagerClient.despawn(aObject.getObjectOID());
				aObject.setActive(false);
				aObject.respawn(15);
				Log.debug("ALOC: object despawned");
			}
		} else if (aObject.getObjectType().equals(ArenaObject.ARENA_OBJECT_TRAP)) {
			// Check if the trap is set to react to this team
			int playerTeam = getPlayerTeam(oid);
			if (playerTeam != aObject.getTeamToReactTo())
				return;
			CombatClient.startAbility(ArenaAbilities.ABILITY_TRAP, oid, oid, null);
			WorldManagerClient.despawn(aObject.getObjectOID());
			block.setObjectInBlock(null);
			AccountDatabase aDB = new AccountDatabase(false);
			String targetName = aDB.getCharacterNameByOid(oid);
			//String targetName = WorldManagerClient.getObjectInfo(oid).name;
			sendMessageAll("Arena_event", targetName + " ran into a Trap!");
		} else if (aObject.getObjectType().equals(ArenaObject.ARENA_OBJECT_POWERUP) && aObject.getActive()) {
			WorldManagerClient.despawn(aObject.getObjectOID());
			addPowerup(member, member.getTeam());
			block.setObjectInBlock(null);
		}
    	/*for (int x : arenaObjects.keySet()) {
    		if (Math.abs(loc.getX() - x) < 1000) {
    			HashMap<Integer, ArenaObject> xObjects = arenaObjects.get(x);
    			for (int z : xObjects.keySet()) {
    				if (Math.abs(loc.getZ() - z) < 1000) {
    					Log.debug("ALOC: got in range, despawning object");
    					ArenaObject aObject = xObjects.get(z);
    					if (aObject.getObjectType().equals(ArenaObject.ARENA_OBJECT_ABILITY) && aObject.getActive()) {
    						if (addAbility(oid, member.getTeam())) {
    							WorldManagerClient.despawn(aObject.getObjectOID());
    							aObject.setActive(false);
    							aObject.respawn(15);
    							arenaObjects.put(x, xObjects);
    							Log.debug("ALOC: object despawned");
    						}
    					} else if (aObject.getObjectType().equals(ArenaObject.ARENA_OBJECT_TRAP)) {
    						// Check if the trap is set to react to this team
    						int playerTeam = getPlayerTeam(oid);
    						if (playerTeam != aObject.getTeamToReactTo())
    							return;
    						CombatClient.startAbility(ArenaPlugin.ABILITY_TRAP, oid, oid, null);
    						WorldManagerClient.despawn(aObject.getObjectOID());
	    					xObjects.remove(z);
	    					arenaObjects.put(x, xObjects);
	    					String targetName = WorldManagerClient.getObjectInfo(oid).name;
	    					sendMessageAll("Arena_event", targetName + " ran into a Trap!");
    					} else if (aObject.getObjectType().equals(ArenaObject.ARENA_OBJECT_POWERUP) && aObject.getActive()) {
    						addPowerup(member, member.getTeam());
    						WorldManagerClient.despawn(aObject.getObjectOID());
	    					xObjects.remove(z);
	    					arenaObjects.put(x, xObjects);
    					}
    					return;
    				}
    			}
    		}
    	}*/
    }
    
    private void removeArenaObject(ArenaObject objectToRemove) {
    	for (int x : arenaObjects.keySet()) {
    		HashMap<Integer, ArenaObject> xObjects = arenaObjects.get(x);
    		for (int z : xObjects.keySet()) {
    			ArenaObject aObject = xObjects.get(z);
    			if (aObject.equals(objectToRemove)) {
    				xObjects.remove(z);
					arenaObjects.put(x, xObjects);
					Log.debug("BOMB: removed bomb");
					WorldManagerClient.despawn(aObject.getObjectOID());
    			}
    		}
    	}
    }
    
    protected boolean addAbility(OID playerOid, int team) {
    	int playerabilities[] = getArenaPlayer(playerOid).getAbilities();
    	for (int i = 0; i < playerabilities.length; i++) {
    		if (playerabilities[i] == -1) {
    			// Get list of abilities
    			LinkedList<Integer> abilityList = getAbilityList(team);
    			Random random = new Random();
    			playerabilities[i] = abilityList.get(random.nextInt(abilityList.size()));
    			sendAbilities(playerOid);
    			return true;
    		}
    	}
    	return false;
    }
    
    protected boolean addPowerup(ArenaMember member, int playerTeam) {
    	// Get list of abilities
    	LinkedList<Integer> powerupList = getPowerupList(playerTeam);
    	Random random = new Random();
    	int powerup = powerupList.get(random.nextInt(powerupList.size()));
    	if (powerup == POWERUP_BLAST_RADIUS) {
    		int blastSize = (Integer) member.getProperty(PROP_BLAST_SIZE);
    		blastSize++;
    		member.setProperty(PROP_BLAST_SIZE, blastSize);
    		ExtendedCombatMessages.sendCombatText(member.getOid(), "Blast Size +1", 9);
    	} else if (powerup == POWERUP_REDUCE_COOLDOWN) {
    		int cooldownLength = (Integer) member.getProperty(PROP_BOMB_COOLDOWN);
    		cooldownLength = cooldownLength / 2;
    		member.setProperty(PROP_BOMB_COOLDOWN, cooldownLength);
    		ExtendedCombatMessages.sendCombatText(member.getOid(), "Bomb Cooldown / 2", 9);
    	}
    	return true;
    }
    
    protected boolean canPlaceObject(int x, int z) {
    	if (!arenaGrid.containsKey(x)) {
    		return false;
    	} else if (!arenaGrid.get(x).containsKey(z)) {
    		return false;
    	}
    	ArenaGridBlock block = arenaGrid.get(x).get(z);
    	return !block.hasObject();
    }
    
    protected boolean placeObjectInGrid(int x, int z, Object object) {
    	if (!arenaGrid.containsKey(x)) {
    		return false;
    	} else if (!arenaGrid.get(x).containsKey(z)) {
    		return false;
    	}
    	ArenaGridBlock block = arenaGrid.get(x).get(z);
    	if(Log.loggingDebug)	Log.debug("BLOCK: placing object in block: " + x + "/" + z);
    	return block.placeObjectInBlock(object);
    }
    
    protected boolean removeObjectFromGrid(int x, int z, Object object) {
    	if (!arenaGrid.containsKey(x)) {
    		return false;
    	} else if (!arenaGrid.get(x).containsKey(z)) {
    		return false;
    	}
    	if(Log.loggingDebug)Log.debug("BLOCK: removing object from tile: " + x + "/" + z);
    	ArenaGridBlock block = arenaGrid.get(x).get(z);
    	Object blockObject = block.getObjectInBlock();
    	if (!blockObject.equals(object))
    		return false;
    	block.setObjectInBlock(null);
    	Log.debug("BLOCK: returning from removeObject");
    	return true;
    }
    
    protected int getGridXTile(float f) {
    	// First get distance from the centre
    	int distance = (int)f - centreX;
    	// Because the locations refer to the center of the tile we need
    	// to alter the distance by half the grid size in the correct direction
    	if (f > centreX)
    		distance += gridSize / 2;
    	else 
    		distance -= gridSize / 2;
    	// Now divide by grid size
    	distance = distance / gridSize;
    	return distance;
    }
    
    protected int getGridZTile(float f) {
    	// First get distance from the centre
    	int distance = (int)f - centreZ;
    	// Because the locations refer to the center of the tile we need
    	// to alter the distance by half the grid size in the correct direction
    	if (f > centreZ)
    		distance += gridSize / 2;
    	else 
    		distance -= gridSize / 2;
    	// Now divide by grid size
    	distance = distance / gridSize;
    	return distance;
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
				if(Log.loggingDebug)	Log.debug("ARENA: maximum: " + maximum);
			    for (int i = 1; i < numTeams; i++) {
			    	double goalPercent = (double)teams[i].getTeamScore() / (double)teams[i].getTeamGoal();
			    	if(Log.loggingDebug)	Log.debug("ARENA: team: " + i + " goal percent");
			    	//TODO: Have some way of handling draws
			        if (goalPercent > maximum) {
			            maximum = goalPercent;
			            winningTeam = i;
			            if(Log.loggingDebug)    Log.debug("ARENA: setting winning team to " + i);
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
			Log.debug("ARENA: Arena " + arenaID + " is not running. state is not 2");
			return;
		}
		state = STATE_END;
		if (scheduledExecutioner != null) {
			scheduledExecutioner.cancel(true);
			scheduledExecutioner = null;
		}
		logArenaResult(winningTeam);
		
		if (winningTeam != -1) {
			sendChatMessageAll(teams[winningTeam].getTeamMember(0).getName() + " has won!");
		}
		
		// Log it
        HashMap<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("arenaID", arenaID);
        DataLoggerClient.logData("ARENA_ENDED", null, null, null, props);
		
        if(Log.loggingDebug)Log.debug("ARENA: endGame hit with winner: " + winningTeam);
    	sendMessageAll("arena_end", null);
    	
    	for (int i = 0; i < numTeams; i++) {
    		for (ArenaMember member : teams[i].getActiveMembers()) {
    			OID oid = member.getOid();
				EnginePlugin.setObjectProperty(oid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, "");
				//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, 0);
				BasicWorldNode bwNode = WorldManagerClient.getWorldNode(oid);
//				bwNode.setDir(new AOVector());
//				WorldManagerClient.updateWorldNode(oid, bwNode);
				
				// Bring all dead characters back alive
				if (!CombatPlugin.isPlayerAlive(oid)) {
					CombatClient.arenaRelease(oid, bwNode.getLoc(), false, false);
				}
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
    
    public void handleDeath(OID attackerOid, OID victimOid) {
		Log.debug("ARENA: handleDeath hit. Attacker: " + attackerOid + "; victim: " + victimOid);
		
    	if (state != STATE_RUNNING) {
    		Log.debug("ARENA: handleDeath(): Arena is not running. State is not 2.");
    		return;
		}
		
		// First figure out the team of victim and killer
		int victimTeam = -1;
		int attackerTeam = -1;
		for (int i = 0; i < numTeams; i++) {
			if (teams[i].hasMember(attackerOid))
				attackerTeam = i;
			if (teams[i].hasMember(victimOid))
				victimTeam = i;
		}
		
		//EnginePlugin.setObjectProperty(victimOid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, 0);
		
		ArenaMember victim = getArenaPlayer(victimOid);
		victim.playerDied();
		ArenaMember attacker = getArenaPlayer(attackerOid);
		sendMessageAll("message_text", victim.getName() + " was blown up by " + attacker.getName());
		sendMessageAll("Arena_event", victim.getName() + " was blown up by " + attacker.getName());
		addIndividualScore(attackerTeam, attackerOid, 1);
		addIndividualKill(attackerTeam, attackerOid, 1);
		addIndividualDeath(victimTeam, victimOid, 1);
		// Now update the stats as needed
	//	alterTeamScore(victimTeam, -1);
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
    	if(Log.loggingDebug)Log.debug("ARENA: addKillScore hit. Attacker: " + attackerOid + "; victim: " 
    			+ victimOid);
		
    	if (state != STATE_RUNNING) {
    		Log.debug("ARENA: addKillScore(): Arena is not running. State is not 2.");
    		return;
		}
		if (attackerOid == victimOid) {
			Log.debug("ARENA: addKillScore(): Suicide attempt. AttackerOid equals VictimOid");
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
    
    /**
     * This is called whenever a pac eats a dot. This function is called from the ArenaPlugin
     * which has a hook to catch when a pac runs into a dot.
     * @param attackerOid: the oid of the pac who ate the dot
     */
    public void addDotScore(OID attackerOid) {
    	if(state != STATE_RUNNING){
    		Log.debug("ARENA: addDotScore: Arena is not running. State is not 2");
    		return;
    	}
        // First figure out the team of killer
		int attackerTeam = -1;
		for (int i = 0; i < numTeams; i++) {
			if (teams[i].hasMember(attackerOid))
				attackerTeam = i;
		}
		
		if (attackerTeam == -1)
			return;
		
		alterTeamScore(attackerTeam, 1);
        addIndividualScore(attackerTeam, attackerOid, 1);
        CoordinatedEffect effect = new CoordinatedEffect("DotEffect");
        effect.sendSourceOid(true);
        effect.invoke(attackerOid, attackerOid);
    }
    
    public void releaseRequest(OID playerOid) {
    	// TODO: add logic here to handle a player clicking release in Arena
    	// In this case we probably do nothing as players should not be releasing in the arena. Maybe allow them to turn into Spirit form?
    	if(Log.loggingDebug)Log.debug("ARENE RELEASE: Got Message DeathmatchArena releaseRequest PlayerOid: "+playerOid);
    	// CombatInfo info = (CombatInfo) EntityManager.getEntityByNamespace(playerOid,Namespace.COMBAT);
    /*	 CombatInfo info =  CombatPlugin.getCombatInfo(playerOid);
		if (info == null) {
			if (Log.loggingDebug)
				Log.debug("ARENA RELEASE: no combat info found oid=" + playerOid);
			return ;
		}
		if (!info.dead()) {
			if (Log.loggingDebug)
				Log.debug("ARENA RELEASE: subject not dead oid=" + playerOid);
			return;// true;
		}
		info.setCombatState(false);
		EnginePlugin.setObjectPropertiesNoResponse(info.getOwnerOid(),
				Namespace.WORLD_MANAGER,
				WorldManagerClient.WORLD_PROP_NOMOVE, new Boolean(false),
				WorldManagerClient.WORLD_PROP_NOTURN, new Boolean(false));
		Log.debug("RELEASE: about to generate loot");

		info.clearState(CombatInfo.COMBAT_STATE_SPIRIT);
		//relocateReleasedPlayer(playerOid, info);
		
		BasicWorldNode wnode = WorldManagerClient.getWorldNode(playerOid);*/
		for (int i = 0; i < numTeams; i++) {
			if (teams[i].hasMember(playerOid))
				CombatClient.arenaRelease(playerOid,teams[i].getSpawnPoint(),true,false);
		//	EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, CombatPlugin.DEFAULT_MOVEMENT_SPEED);

		}
		//wnode.setLoc(info.getRespawnPosition());
	/*	WorldManagerClient.updateWorldNode(playerOid, wnode, true);
		//relocateReleasedPlayer(playerOid, info);
		// Do this after relocating so mobs don't aggro from where the player died
		info.setDeadState(false);
		CombatPlugin.setReleaseStatValues(info);*/
		Log.debug("ARENE RELEASE: released on team Point");
		
		
    }
    
    public void completeTutorial(OID oid) {
    	int team = -1;

    	if (team < 0) {
    		Log.warn("ARENA: trying to remove a player: " + oid + " who is not on any team");
    		return;
    	}
    	
    	sendMessageSingle("Arena_end", oid, null);
    	
    	teams[team].removePlayer(oid);
    	
    //	EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "state", 0);
		EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID, -1);
		
		ArenaCleanup arenaCleanup = new ArenaCleanup();
		Engine.getExecutor().schedule(arenaCleanup, 10, TimeUnit.SECONDS);
    }
    
    protected LinkedList<Integer> getAbilityList(int team) {
    	LinkedList<Integer> abilities = new LinkedList<Integer>();
    	abilities.add(ArenaAbilities.ABILITY_SPEED_BOOST);
    	abilities.add(ArenaAbilities.ABILITY_REMOTE_DETONATION);
    	abilities.add(ArenaAbilities.ABILITY_SLOW_GO);
    	return abilities;
    }
    
    protected LinkedList<Integer> getPowerupList(int team) {
    	LinkedList<Integer> powerups = new LinkedList<Integer>();
    	// First add abilities that can be used by both races
    	powerups.add(POWERUP_BLAST_RADIUS);
    	powerups.add(POWERUP_REDUCE_COOLDOWN);
    	return powerups;
    }
    
    /**
     * Add the score to the teams score.
     * 
     * @param team: Which team to add score to.
     * @param score: Score value to add. Can be negative.
     */
    protected void alterTeamScore(int team, int score) {
    	if(Log.loggingDebug)	Log.debug("ARENA: team " + team + "'s score is being altered by: " + score);
    	teams[team].updateScore(score);
    	//Check the teams goal. If the goal is -1 we don't need to do anything else
    	//if (teamGoal[team] == -1)
    	//	return;
    	
    	sendStatMessageAll("Arena_teamstat_update", team, null, "score", teams[team].getTeamScore());
    	if (goalType == 0) {
    		if(Log.loggingDebug)Log.debug("ARENA: about to check team's score to see if they have been defeated. Current score: " + teams[team].getTeamScore());
    	    if (teams[team].getTeamScore() < 1)
    	    	teamDefeated(team);
    	} else {
    		if (teams[team].getTeamGoal() == -1)
    	    	return;
    		if (teams[team].getTeamScore() >= teams[team].getTeamGoal())
    		    endGame(team);
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
		if (msgType == "bomb_cooldown") {
			props.put("cooldown_length", data);
			props.put("ability_id", ArenaAbilities.ABILITY_BOMB);
			handled = true;
		}
		
		if (handled) {
			TargetedExtensionMessage msg = new TargetedExtensionMessage(
				WorldManagerClient.MSG_TYPE_EXTENSION, oid, 
				oid, false, props);
			Engine.getAgent().sendBroadcast(msg);
		} else {
			handled = super.sendMessageSingle(msgType, oid, data);
		}
		return handled;
    }
    
    private HashMap<Integer, HashMap<Integer, ArenaGridBlock>> arenaGrid;
    private static final int centreX = 0;
    private static final int centreZ = 0;
    private static final int gridSize = 2000;
    private static final int baseMovementSpeed = CombatPlugin.DEFAULT_MOVEMENT_SPEED / 3 * 2;
    
    private static final int POWERUP_BLAST_RADIUS = 1;
    private static final int POWERUP_REDUCE_COOLDOWN = 2;
    
    private static final String PROP_BLAST_SIZE = "blastSize";
    private static final String PROP_BOMB_COOLDOWN = "bombCooldown";

    private static final long serialVersionUID = 1L;

	@Override
	public void activateAbility(OID playerOid, OID targetOid, int slot) {
		// TODO Auto-generated method stub
		
	}
}
