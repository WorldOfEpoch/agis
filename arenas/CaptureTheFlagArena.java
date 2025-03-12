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
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;
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
public class CaptureTheFlagArena extends Arena implements Serializable, MessageDispatch {
	
	private HashMap<Integer, ArenaFlagPlatform> flagPlatforms;
	private HashMap<Integer, ArenaFlag> flags;
	private String flagMesh = "prop_castleflag.mesh";
	private int flagDisplayID0 = 113;
	private int flagDisplayID1 = 112;
	private int flagBaseDisplayID = 147;
	//private Point boundaryMin = new Point();
	//private Point boundaryMax = new Point();

    /**
     * The default constructor. This creates the Arrays and ArrayLists. This needs to be 
     * called by the other constructors.
     * @param numTeams: the number of teams in the arena
     */
    public CaptureTheFlagArena(int numTeams) {
    	Log.debug("MUNCH: starting generic arena object construction");
    	this.numTeams = numTeams;

    	teams = new ArenaTeam[numTeams];
    	for (int i = 0; i < numTeams; i++) {
	    	if (teams[i] == null) {
	    		teams[i] = new ArenaTeam();
	    		Log.debug("MUNCH: created team: " + i);
	    	}
    	}
    	flagPlatforms = new HashMap<Integer, ArenaFlagPlatform>();
    	flags = new HashMap<Integer, ArenaFlag>();
    	Log.debug("MUNCH: finished generic arena object construction");
    }

   /**
    * Constructor that takes in two lists that contain the oids of the players
     * on each team.
    * @param numTeams
    * @param members
    * @param type
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
    public CaptureTheFlagArena(int numTeams, ArrayList<QueueMember> members[], 
    		int type, int arenaDatabaseID, String arenaName, int category, int dur, int[] goals, String[] teamNames, 
    		Point[] spawnPoints, int condition, int id, int[] minPlayers, int instanceTemplateID, 
    		HashMap<Integer, Integer> victoryPayment, HashMap<Integer, Integer> defeatPayment,
    		int victoryExp, int defeatExp, boolean useWeapons) {
    	this(numTeams);
    	Log.debug("ARENA: starting arena creation: " + id);

	    for (int i = 0; i < numTeams; i++) {
	    	teams[i].initialiseTeam(i, teamNames[i], goals[i], minPlayers[i], spawnPoints[i]);
	    	for (int j = 0; j < members[i].size(); j++) {
	    		QueueMember member = members[i].get(j);
	    		int base_speed =  CombatPlugin.DEFAULT_MOVEMENT_SPEED;
	    		teams[i].addTeamMember(member.getOid(), member.getName(), member.getRace(), base_speed, useWeapons, true);
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
    	this.goalType = 1;
    	
    	this.victoryPayment = victoryPayment;
    	this.defeatPayment = defeatPayment;
    	this.victoryXP = victoryExp;
    	this.defeatXP = defeatExp;
    	this.useWeapons = useWeapons;
    	
    	// Set the arena ID property for all players
    	for (int i = 0; i < numTeams; i++) {
    		Log.debug("MUNCH: getting members for team: " + i);
    		for (ArenaMember member : teams[i].getTeamMembers()) {
    			OID oid = member.getOid();
    			EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ARENA_ID, arenaID);
    			//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, 0);
    			// Set member properties
    			member.setProperty("state", 0);
    			member.setProperty("carryingFlag", -1);
    			member.setProperty(CombatPlugin.HEALTH_STAT, initialHealth);
    			member.setProperty(CombatPlugin.HEALTH_MAX_STAT, initialHealth);
    			member.setProperty(PROP_ATTACK_COOLDOWN, 4000);
    			member.setProperty("cooldownEnds", System.currentTimeMillis());
    			//member.setAbility(0, ArenaAbilities.ABILITY_ATTACK);
    		}
    		if (teams[i].getTeamSize() > 1)
    		    GroupClient.createGroup(teams[i].getTeamMembersOids());
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
    	if (state != STATE_UNSET) {
    		Log.debug("ARENA: Arena " + arenaID + " has already been setup. State is not 0");
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
    		Log.warn("ARENA: instanceOid=" + instanceOid + " markerName=" + markerName);
    		Marker spawn = InstanceClient.getMarker(instanceOid, markerName);
    		Log.warn("ARENA: marker=" + spawn);
    		node.setInstanceOid(instanceOid);
    		node.setOrientation(spawn.getOrientation());
        	node.setLoc(spawn.getPoint());
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
			}
	    	// Remove any players who have already disconnected
	    	for (OID playerOid : playersDisconnected)
	    		removePlayer(playerOid, false);
			if (teams[i].getTeamSize() > 0) {
				Log.debug("ARENA: setting team " + i + " to active");
				teams[i].setTeamActive(true);
			} else {
				Log.debug("ARENA: setting team " + i + " to inactive");
				teams[i].setTeamActive(false);
			}
    	}
    	/*if (goalType == 0) {
    		// Set up team scores to be equal to number of players in each team
    		for (int i = 0; i < numTeams; i++) {
    			teamScore[i] = teams[i].size();
    		}
    	}*/
    	
    	createSubs();
    	
    	SpawnObjects spawnObjects = new SpawnObjects();
		Engine.getExecutor().schedule(spawnObjects, 1, TimeUnit.SECONDS);
    	
		ArenaCountdown arenaCountdown = new ArenaCountdown();
		Engine.getExecutor().schedule(arenaCountdown, 5, TimeUnit.SECONDS);
		
		sendMessageAll("arena_setup", null);
    }
    
    /**
     * Calls the functions to spawn all the objects needed in this arena.
     *
     */
    public class SpawnObjects implements Runnable {
		public void run() {
			readSettings();
			//createFlags();
			createFlagPlatforms();
			createStars();
			createWeapons();
			createHealth();
		}
	}
    
    private void readSettings() {
    	String fileName = ""; //"$WORLD_DIR/" + worldFile + "/settings.cfg";
        String worldFileName = FileUtil.expandFileName(fileName);
        Log.debug("ARENA: opening settings file: " + worldFileName);
        File settingsFile = new File(worldFileName);
        try {
			BufferedReader bufRdr  = new BufferedReader(new FileReader(settingsFile));
			String line = null;
			// Read gridsize
			line = bufRdr.readLine();
			int split = line.indexOf("=");
			gridSize = Integer.parseInt(line.substring(split+1));
			// Read center loc
			line = bufRdr.readLine();
			split = line.indexOf("=");
			line = line.substring(split+1);
			split = line.indexOf(",");
			centreX = Integer.parseInt(line.substring(0, split));
			line = line.substring(split+1);
            split = line.indexOf(",");
            centreY = Integer.parseInt(line.substring(0, split));
            line = line.substring(split+1);
            centreZ = Integer.parseInt(line);
            // Boundary min
            
            //Boundary max
            bufRdr.close();
        } catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			
		}
    }
    
    private void createFlagPlatforms() {
    	String fileName = ""; //"$WORLD_DIR/" + worldFile + "/FlagLocations.csv";
        String worldFileName = FileUtil.expandFileName(fileName);
        Log.debug("ARENA: opening flagFile: " + worldFileName);
        File dotFile = new File(worldFileName);
        int dotNum = 0;
        try {
			BufferedReader bufRdr  = new BufferedReader(new FileReader(dotFile));
			String line = null;
			while((line = bufRdr.readLine()) != null)
			{
				int split = line.indexOf(",");
				int x = Integer.parseInt(line.substring(0, split));// * gridSize;
	            line = line.substring(split+1);
	            split = line.indexOf(",");
	            int y = Integer.parseInt(line.substring(0, split));
	            line = line.substring(split+1);
	            split = line.indexOf(",");
	            int z = Integer.parseInt(line.substring(0, split));// * gridSize;
	            //x = x + centreX;
	            //y = y + centreY;
	            //z = z + centreZ;
	            Point loc = new Point(x, y, z);
	        	DisplayContext dc = new DisplayContext(flagMesh, true);	  
	        	dc.setDisplayID(flagBaseDisplayID);
	        	ArenaFlagPlatform aObject = new ArenaFlagPlatform(dotNum, loc, instanceOid, ArenaObject.ARENA_OBJECT_FLAG_PLATFORM, dc, null);
	            aObject.setTeamToReactTo(dotNum);
	            addArenaObject(aObject);
	            flagPlatforms.put(dotNum, aObject);
	            dotNum++;
			}
			 
			//close the file
			bufRdr.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			
		}
    }
    
    private void createStars() {
    	String fileName = ""; //"$WORLD_DIR/" + worldFile + "/StarLocations.csv";
        String worldFileName = FileUtil.expandFileName(fileName);
        Log.debug("ARENA: opening starFile: " + worldFileName);
        File starFile = new File(worldFileName);
        int starNum = 0;
        try {
			BufferedReader bufRdr  = new BufferedReader(new FileReader(starFile));
			String line = null;
			while((line = bufRdr.readLine()) != null)
			{
				int split = line.indexOf(",");
				int x = Integer.parseInt(line.substring(0, split)); // * gridSize;
	            line = line.substring(split+1);
	            split = line.indexOf(",");
	            int y = Integer.parseInt(line.substring(0, split));
	            line = line.substring(split+1);
	            split = line.indexOf(",");
	            int z = Integer.parseInt(line.substring(0, split)); // * gridSize;
	            //x = x + centreX;
	            //y = y + centreY;
	            //z = z + centreZ;
	            Point loc = new Point(x, y, z);
	            DisplayContext dc = new DisplayContext("Star.mesh", true);	      
	        	dc.setDisplayID(-4);
	        	HashMap<String, Serializable> props = new HashMap<String, Serializable>();
	        	props.put("StaticAnim", "idle");
	            ArenaObject aObject = new ArenaObject(starNum, loc, instanceOid, "Star", dc, props);
	            addArenaObject(aObject);
	            starNum++;
			}
			 
			//close the file
			bufRdr.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			
		}
    }
    
    private void createWeapons() {
    	String fileName = ""; //"$WORLD_DIR/" + worldFile + "/WeaponLocations.csv";
        String worldFileName = FileUtil.expandFileName(fileName);
        Log.debug("ARENA: opening starFile: " + worldFileName);
        File starFile = new File(worldFileName);
        int starNum = 0;
        try {
			BufferedReader bufRdr  = new BufferedReader(new FileReader(starFile));
			String line = null;
			while((line = bufRdr.readLine()) != null)
			{
				int split = line.indexOf(",");
				int x = Integer.parseInt(line.substring(0, split)); // * gridSize;
	            line = line.substring(split+1);
	            split = line.indexOf(",");
	            int y = Integer.parseInt(line.substring(0, split));
	            line = line.substring(split+1);
	            split = line.indexOf(",");
	            int z = Integer.parseInt(line.substring(0, split)); // * gridSize;
	            //x = x + centreX;
	            //y = y + centreY;
	            //z = z + centreZ;
	            Point loc = new Point(x, y, z);
	            DisplayContext meleeDC = new DisplayContext("weapon_pickup.mesh", true);	      
	            meleeDC.setDisplayID(124);
	            DisplayContext rangedDC = new DisplayContext("weapon_pickup.mesh", true);	      
	            rangedDC.setDisplayID(126);
	        	HashMap<String, Serializable> props = new HashMap<String, Serializable>();
	        	props.put("StaticAnim", "idle");
	            ArenaObject aObject = new ArenaWeaponObject(starNum, loc, instanceOid, meleeDC, rangedDC, props);
	            addArenaObject(aObject);
	            starNum++;
			}
			 
			//close the file
			bufRdr.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			
		}
    }
    
    private void createHealth() {
    	String fileName = ""; //"$WORLD_DIR/" + worldFile + "/HealthLocations.csv";
        String worldFileName = FileUtil.expandFileName(fileName);
        Log.debug("ARENA: opening starFile: " + worldFileName);
        File starFile = new File(worldFileName);
        int starNum = 0;
        try {
			BufferedReader bufRdr  = new BufferedReader(new FileReader(starFile));
			String line = null;
			while((line = bufRdr.readLine()) != null)
			{
				int split = line.indexOf(",");
				int x = Integer.parseInt(line.substring(0, split)); // * gridSize;
	            line = line.substring(split+1);
	            split = line.indexOf(",");
	            int y = Integer.parseInt(line.substring(0, split));
	            line = line.substring(split+1);
	            split = line.indexOf(",");
	            int z = Integer.parseInt(line.substring(0, split)); // * gridSize;
	            //x = x + centreX;
	            //y = y + centreY;
	            //z = z + centreZ;
	            Point loc = new Point(x, y, z);
	            DisplayContext dc = new DisplayContext("prop_heart.mesh", true);	      
	        	dc.setDisplayID(128);
	        	HashMap<String, Serializable> props = new HashMap<String, Serializable>();
	        	props.put("StaticAnim", "idle");
	            ArenaObject aObject = new ArenaObject(starNum, loc, instanceOid, ArenaObject.ARENA_OBJECT_HEALTH, dc, props);
	            addArenaObject(aObject);
	            starNum++;
			}
			 
			//close the file
			bufRdr.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			
		}
    }
    
    public class ArenaCountdown implements Runnable {
    	public void run() {
    		int setupTime = 15;
    		sendMessageAll("arena_countdown", setupTime * 1000);
        	//sendMessageAll("message_text", "Prepare yourself, the Arena will begin in " + setupTime + " seconds.");
    		
    		ArrayList<String> skins = getUsableSkins();
        	for (int i = 0; i < numTeams; i++) {
	    		for (ArenaMember member : teams[i].getActiveMembers()) {
	    			OID oid = member.getOid();
	    			// Set team colour & race
	    			changePlayerRace(member, skins.get(0), true);
	    			setPlayerTeamColour(oid, i + 1);
	    		}
			}
    		
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
     * Starts the arena. Sends a message to the client so the client can update the UI and
     * schedules the endArena executioner.
     *
     */
    public class ArenaStart implements Runnable {
		public void run() {
			if (state != STATE_SETUP) {
				Log.error("ARENA: Arena " + arenaID + " is not starting. state is not 1");
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
	    		for (ArenaMember member : teams[i].getActiveMembers()) {
	    			OID oid = member.getOid();
	    			//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, CombatPlugin.DEFAULT_MOVEMENT_SPEED);
	    		}
	    	}
			// Send the message to get the gates so they can be despawned
	    	spawnFlags();
			//despawnGates();
	    	sendMessageAll("message_text", "Fight!");
			
			startTime = (int) System.currentTimeMillis();
			sendMessageAll("arena_started", null);
			if (length != 0) {
				ArenaEnd arenaTimer = new ArenaEnd();
				Engine.getExecutor().schedule(arenaTimer, length, TimeUnit.SECONDS);
			}
			for (int i = 0; i < numTeams; i++) {
				for (ArenaMember member : teams[i].getActiveMembers()) {
					OID oid = member.getOid();
					sendAbilities(oid);
					int health = (Integer) member.getProperty(CombatPlugin.HEALTH_STAT);
					setPlayerProperty(member.getOid(), "hearts", health);
				}
			}
			
			CollisionCheck collisionCheck = new CollisionCheck();
			// Run the collision check every half a second
			scheduledExecutioner = Engine.getExecutor().scheduleAtFixedRate(collisionCheck, 1, 50, TimeUnit.MILLISECONDS);
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
            Log.error("PET: unknown msg: " + msg);
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
    	Log.debug("ARENA: got damage message for arena: " + arenaID + ". " + attackerOid + " dealt " + damage + " to " + victimOid);
    	
    	int victimTeam = -1;
		int attackerTeam = -1;
		for (int i = 0; i < numTeams; i++) {
			if (teams[i].hasMember(attackerOid))
				attackerTeam = i;
			else if (teams[i].hasMember(victimOid))
				victimTeam = i;
		}
		
		teams[victimTeam].getTeamMember(victimOid).addDamageTaken(damage);
		Log.debug("ARENA: damage taken by " + victimOid + " is now: " + teams[victimTeam].getTeamMember(victimOid).getDamageTaken());
		sendStatMessageAll("Arena_stat_update", victimTeam, victimOid, "damageTaken", teams[victimTeam].getTeamMember(victimOid).getDamageTaken());
		teams[attackerTeam].getTeamMember(attackerOid).addDamageDealt(damage);
		Log.debug("ARENA: damage dealt by " + attackerOid + " is now: " + teams[attackerTeam].getTeamMember(attackerOid).getDamageDealt());
		sendStatMessageAll("Arena_stat_update", attackerTeam, attackerOid, "damageDealt", teams[attackerTeam].getTeamMember(attackerOid).getDamageDealt());
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
    	for (int x : arenaObjects.keySet()) {
    		if (Math.abs(loc.getX() - x) < 1000) {
    			HashMap<Integer, ArenaObject> xObjects = arenaObjects.get(x);
    			for (int z : xObjects.keySet()) {
    				if (Math.abs(loc.getZ() - z) < 1000) {
    					Log.debug("ALOC: got in range");
    					ArenaObject aObject = xObjects.get(z);
    					if (aObject.getObjectType().equals(ArenaObject.ARENA_OBJECT_FLAG_PLATFORM)) {
    						Log.debug("PLATFORM: checking member team: " + member.getProperty("carryingFlag") + " against: " + aObject.getTeamToReactTo());
    						if (aObject.getTeamToReactTo() == (Integer)member.getTeam()) {
    							// We have to check the player is carrying an en
    							int carryingFlag = (Integer)member.getProperty("carryingFlag");
    							ArenaFlagPlatform platform = (ArenaFlagPlatform) aObject;
    							if (carryingFlag != -1 && platform.hasFlag()) {
    								member.setProperty("carryingFlag", -1);
    								flagCaptured(member, carryingFlag);
    								return;
    							}
    						}
    					} else if (aObject.getObjectType().equals(ArenaObject.ARENA_OBJECT_ABILITY) && aObject.getActive()) {
    						if (addAbility(member.getOid(), member.getTeam())) {
    							WorldManagerClient.despawn(aObject.getObjectOID());
    							aObject.setActive(false);
    							//xObjects.remove(z);
    							aObject.respawn(15);
    							arenaObjects.put(x, xObjects);
    							Log.debug("ALOC: object despawned");
    							return;
    						}
    					} else if (aObject.getObjectType().equals(ArenaObject.ARENA_OBJECT_TRAP)) {
    						// Check if the trap is set to react to this team
    						int playerTeam = getPlayerTeam(member.getOid());
    						if (playerTeam != aObject.getTeamToReactTo())
    							return;
    						// Check state
    						int state = (Integer) EnginePlugin.getObjectProperty(member.getOid(), WorldManagerClient.NAMESPACE, "state");
    						if (state == PLAYER_IMMUNE)
    							return;
    						CombatClient.startAbility(ArenaAbilities.ABILITY_TRAP, member.getOid(), member.getOid(), null);
    						WorldManagerClient.despawn(aObject.getObjectOID());
	    					xObjects.remove(z);
	    					arenaObjects.put(x, xObjects);
	    					String targetName = WorldManagerClient.getObjectInfo(member.getOid()).name;
	    					sendMessageAll("Arena_event", targetName + " ran into a Trap!");
	    					return;
    					} else if (aObject.getObjectType().equals(ArenaObject.ARENA_OBJECT_HEALTH) && aObject.getActive()) {
    						if (addHealth(member)) {
    							WorldManagerClient.despawn(aObject.getObjectOID());
    							aObject.setActive(false);
    							//xObjects.remove(z);
    							aObject.respawn(15);
    							arenaObjects.put(x, xObjects);
    							Log.debug("ALOC: object despawned");
    							return;
    						}
    					} else if ((aObject.getObjectType().equals(ArenaObject.ARENA_OBJECT_MELEE_WEAPON)
    							|| aObject.getObjectType().equals(ArenaObject.ARENA_OBJECT_RANGED_WEAPON)) && aObject.getActive()) {
    						if (addWeapon(member, aObject.getObjectType())) {
    							WorldManagerClient.despawn(aObject.getObjectOID());
    							aObject.setActive(false);
    							//xObjects.remove(z);
    							aObject.respawn(15);
    							arenaObjects.put(x, xObjects);
    							Log.debug("ALOC: object despawned");
    							return;
    						}
    					}
    					
    					//return;
    				}
    			}
    		}
    	}
    }
    
    protected boolean addAbility(OID playerOid, int playerTeam) {
    	int playerabilities[] = getArenaPlayer(playerOid).getAbilities();
    	for (int i = 1; i < playerabilities.length; i++) {
    		if (playerabilities[i] == -1) {
    			// Get list of abilities
    			LinkedList<Integer> abilityList = getAbilityList(playerTeam);
    			Random random = new Random();
    			playerabilities[i] = abilityList.get(random.nextInt(abilityList.size()));
    			sendAbilities(playerOid);
    			return true;
    		}
    	}
    	return false;
    }
    
    /**
     * The class to handle when the arenas timer has expired. This will check 
     * the victory condition and each teams score to determine a winner.
     *
     */
    public class ArenaEnd implements Runnable {
		public void run() {
			Log.debug("ARENA: time expired");
			
			Log.debug("ARENA: victory Condition is 1");
			int winningTeam = -1;
			double bestScore = 0;
			for (int i = 0; i < numTeams; i++) {
			    double goalPercent = (double)teams[i].getTeamScore() / (double)teams[i].getTeamGoal();
			    Log.debug("ARENA: team: " + i + " goal percent");
			    if (goalPercent > bestScore) {
			    	bestScore = goalPercent;
			        winningTeam = i;
			        Log.debug("ARENA: setting winning team to " + i);
			    } else if (goalPercent == bestScore) {
			    	// scores are equal
			        winningTeam = -1;
			        Log.debug("ARENA: setting winningTeam to -1");
			    }
			}
			Log.debug("ARENA: about to run end game");
			endGame(winningTeam);
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
    	Log.debug("ARENA: team " + team + " has been defeated.");
    	for (int i = 0; i < numTeams; i++) {
    		if (teams[i].getTeamActive() == true)
    			teamsActive.add(i);
    	}
    	
    	Log.debug("ARENA: number of teams active: " + teamsActive.size());
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
			sendChatMessageAll(teams[winningTeam].getTeamName() + " Team wins!");
		}
		
		// Log it
        HashMap<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("arenaID", arenaID);
        DataLoggerClient.logData("ARENA_ENDED", null, null, null, props);
		
    	Log.debug("ARENA: endGame hit with winner: " + winningTeam);
    	
    	for (int i = 0; i < numTeams; i++) {
    		for (ArenaMember member : teams[i].getActiveMembers()) {
    			OID oid = member.getOid();
				EnginePlugin.setObjectProperty(oid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, "");
				//EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, 0);
				setPlayerProperty(member.getOid(), "primaryItem", -1);
				BasicWorldNode bwNode = WorldManagerClient.getWorldNode(oid);
				bwNode.setDir(new AOVector());
				WorldManagerClient.updateWorldNode(oid, bwNode);
				setPlayerProperty(member.getOid(), "hearts", 0);
			}
		}
		
		ArenaCleanup arenaCleanup = new ArenaCleanup();
		Engine.getExecutor().schedule(arenaCleanup, 10, TimeUnit.SECONDS);
		
		for (int i = 0; i < numTeams; i++) {
			if (i == winningTeam) {
				sendMessageTeam("message_text", i, "Victory");
				teams[i].playTeamVictoryAnimations();
				for (ArenaMember member : teams[i].getActiveMembers()) {
					addIndividualScore(i, member.getOid(), pointsForVictory);
				}
			} else if (winningTeam == -1) { // DRAW
				sendMessageTeam("message_text", i, "It's a draw?");
			} else {
				sendMessageTeam("message_text", i, "Defeat");
			}
		}
		
		HashMap<OID, Integer> ratingAdjustments = 
			ArenaStats.CalculateRatings(arenaGameType, arenaDatabaseID, teams, winningTeam, victoryPayment, defeatPayment, victoryXP, defeatXP);
		sendMessageAll("arena_end", ratingAdjustments);
		Log.debug("ARENA: completed end arena");
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
    	Log.debug("ARENA: handleDeath hit. Attacker: " + attackerOid + "; victim: " 
    			+ victimOid);
		
    	if (state != STATE_RUNNING) {
    		Log.debug("ARENA: handleDeath(): Arena is not running. State is not 2.");
    		return;
		}
    	
    	// We need to prevent the player from being attacked for the rest of the arena
    	EnginePlugin.setObjectPropertyNoResponse(victimOid, CombatClient.NAMESPACE, "attackable", false);
    	
		if (attackerOid == victimOid) {
			Log.debug("ARENA: handleDeath(): Suicide attempt. AttackerOid equals VictimOid");
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
		/*BasicWorldNode tnode = new BasicWorldNode();
		String markerName = "team" + victimTeam + "Spawn";
		Marker spawn = InstanceClient.getMarker(instanceOid, markerName);
		tnode.setLoc(spawn.getPoint());
		WorldManagerClient.updateWorldNode(victimOid, tnode, true);*/
		
		
		Log.debug("ARENA: about to send individual stat updates for attacker: " + attackerTeam + " and victim: " + victimOid);
		addIndividualScore(attackerTeam, attackerOid, pointsPerKill);
		addIndividualKill(attackerTeam, attackerOid, 1);
		addIndividualDeath(victimTeam, victimOid, 1);
    }
    
    /**
     * This will be called whenever one player kills another. This function 
     * is called from the ArenaPlugin which has a hook to catch the kill message.
     * @param attackerOid: the oid of the attacker
     * @param victimOid: the oid of the victim
     */
    public void addKillScore(OID attackerOid, OID victimOid) {
    	Log.debug("ARENA: addKillScore hit. Attacker: " + attackerOid + "; victim: " 
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
		
		ArenaMember member = getArenaPlayer(victimOid);
		int carryingFlag = (Integer)member.getProperty("carryingFlag");
		if (carryingFlag != -1) {
			flagDropped(member);
		}
		
		// Next move the victim back to their base
		BasicWorldNode tnode = new BasicWorldNode();
		String markerName = "team" + victimTeam + "Spawn";
		Marker spawn = InstanceClient.getMarker(instanceOid, markerName);
		tnode.setLoc(spawn.getPoint());
		tnode.setDir(new AOVector());
		WorldManagerClient.updateWorldNode(victimOid, tnode, true);
		WorldManagerClient.refreshWNode(victimOid);
		
		// Now update the stats as needed
		//alterTeamScore(attackerTeam, 1);
		addIndividualScore(attackerTeam, attackerOid, pointsPerKill);
		addIndividualKill(attackerTeam, attackerOid, 1);
		addIndividualDeath(victimTeam, victimOid, 1);
        // Apply a stun effect
        CombatClient.startAbility(-502, attackerOid, victimOid, null);
        // Returns their health back to full
        member.setProperty(CombatPlugin.HEALTH_STAT, initialHealth);
		int health = (Integer) member.getProperty(CombatPlugin.HEALTH_STAT);
		setPlayerProperty(member.getOid(), "hearts", health);
		Log.debug("HEALTH: reset player health to: " + health);
		String targetName = member.getName();
		String sourceName = getArenaPlayer(attackerOid).getName();
		sendMessageAll("Arena_event", sourceName + " took out " + targetName);
    }
    
    /**
     * Removes a player from the arena. 
     * 
     * @param oid: The player being removed from the arena
     */
    public void removePlayer(OID oid, boolean teleport) {
    	Log.debug("CTF: removing player: " + oid);
    	int team = -1;
    	
    	team = getPlayerTeam(oid);

    	if (team < 0) {
    		Log.warn("CTF: trying to remove a player: " + oid + " who is not on any team");
    		return;
    	}
    		
    	
    	sendChatMessageAll("Player " + teams[team].getTeamMember(oid).getName() + " has left the arena.");
    	sendMessageAll("arena_player_left", oid);
    	ArenaMember member = teams[team].removePlayer(oid);
    	// If they are carrying a flag, drop it
    	int flagTeam = (Integer) member.getProperty("carryingFlag");
    	if (flagTeam != -1)
    		flagDropped(member);
    	
    	if (state == STATE_RUNNING) {
    	    if (!teams[team].getTeamActive()) {
    		    teamDefeated(team);
    	    } else if (goalType == 0) {
    		    alterTeamScore(team, -1);
    	    }
    	}
    	if (teleport) {
    		teleportOut(oid);
    		EnginePlugin.setObjectProperty(oid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, "");
    	}
    	
    	// Remove the players sub
		Long subId = member.getSub();
		if (subId != null)
			Engine.getAgent().removeSubscription(subId);
    }
    
    protected void spawnFlags() {
    	for (int team : flagPlatforms.keySet()) {
    		Log.debug("FLAG: spawning flag for team: " + team);
    		ArenaFlag flag = flagPlatforms.get(team).spawnFlag(this, team);
    		flags.put(team, flag);
    	}
    }
    
    public void pickupFlag(OID playerOid, int teamNum) {
    	ArenaMember member = getArenaPlayer(playerOid);
    	// Compare the team of the flag and the team of the person picking it up.
    	// If they are the same then we return the flag to its platform
    	ArenaTeam team = getTeam(teamNum);
    	if (member.getTeam() == teamNum) {
    		//sendChatMessageAll(member.getName() + " has returned the " + team.getTeamName() + " team flag!");
    		ArenaFlag flag = flags.remove(teamNum);
        	WorldManagerClient.despawn(flag.getObjectOID());
        	ArenaFlagPlatform platform = flagPlatforms.get(teamNum);
        	ArenaFlag newFlag = platform.spawnFlag(this, teamNum);
        	flags.put(teamNum, newFlag);
        	String sourceName = member.getName();
    		sendMessageAll("Arena_event", sourceName + " returned the " + team.getTeamName() + " flag");
    	} else {
        	//sendChatMessageAll(member.getName() + " has picked up the " + team.getTeamName() + " team flag!");
        	member.setProperty("carryingFlag", teamNum);
        	ArenaFlag flag = flags.remove(teamNum);
        	WorldManagerClient.despawn(flag.getObjectOID());
        	ArenaFlagPlatform platform = flagPlatforms.get(teamNum);
        	platform.flagTaken();
        	HashMap<String, Serializable> props = new HashMap<String, Serializable>();
        	props.put("team", teamNum);
        	props.put("carrier", member.getName());
        	sendMessageAll("flag_carrier", props);
        	PropertyMessage propMsg = new PropertyMessage(playerOid);
	        propMsg.setProperty("carryingFlag", teamNum);
	        Engine.getAgent().sendBroadcast(propMsg);
	        String sourceName = member.getName();
			sendMessageAll("Arena_event", sourceName + " picked up the " + team.getTeamName() + " flag");
    	}
    }
    
    public void dropFlag(OID playerOid) {
    	ArenaMember member = getArenaPlayer(playerOid);
    	flagDropped(member);
    }
    
    /**
     * Spawns a flag at the position of the player dropping a flag
     * @param member
     */
    protected void flagDropped(ArenaMember member) {
    	int flagTeam = (Integer) member.getProperty("carryingFlag");
    	if (flagTeam == -1)
    		return;
    	member.setProperty("carryingFlag", -1);
    	OID oid = member.getOid();
		BasicWorldNode node = WorldManagerClient.getWorldNode(oid);
    	ArenaFlag flag = new ArenaFlag(flagTeam, node.getLoc(), instanceOid, ArenaObject.ARENA_OBJECT_FLAG, 
    			null, flagTeam, true, this);
    	flags.put(flagTeam, flag);
    	ArenaTeam team = getTeam(flagTeam);
    	//sendChatMessageAll("The " + team.getTeamName() + " team flag has been dropped by " + member.getName());
    	HashMap<String, Serializable> props = new HashMap<String, Serializable>();
    	props.put("team", flagTeam);
    	props.put("carrier", "");
    	sendMessageAll("flag_carrier", props);
    	PropertyMessage propMsg = new PropertyMessage(oid);
        propMsg.setProperty("carryingFlag", -1);
        Engine.getAgent().sendBroadcast(propMsg);
        String sourceName = member.getName();
		sendMessageAll("Arena_event", sourceName + " dropped the " + team.getTeamName() + " flag");
    }
    
    /**
     * Called when a player carrying an enemy teams flag enters their flag platform and their flag
     * is still on it. Awards a point to the team of the flag capturer then despawns all other
     * flags and sets up the system to respawn the flags again.
     * @param member
     * @param flagTeam
     */
    public void flagCaptured(ArenaMember member, int flagTeam) {
    	if(state != STATE_RUNNING) {
    		Log.debug("ARENA: flag captured: Arena is not running.");
    		return;
    	}
    	
    	sendMessageAll("message_text", member.getName() + " has captured the enemy flag!");
    	HashMap<String, Serializable> props = new HashMap<String, Serializable>();
    	props.put("team", flagTeam);
    	props.put("carrier", "");
    	sendMessageAll("flag_carrier", props);
    	PropertyMessage propMsg = new PropertyMessage(member.getOid());
        propMsg.setProperty("carryingFlag", -1);
        Engine.getAgent().sendBroadcast(propMsg);
		
		alterTeamScore(member.getTeam(), 1);
        addIndividualScore(member.getTeam(), member.getOid(), pointsPerCapture);
        CoordinatedEffect effect = new CoordinatedEffect("DotEffect");
        effect.sendSourceOid(true);
        effect.invoke(member.getOid(), member.getOid());
        
        // Despawn other platforms flags etc.
        for (ArenaFlagPlatform platform : flagPlatforms.values()) {
        	platform.flagTaken();
        }
        for (ArenaFlag flag : flags.values()) {
        	WorldManagerClient.despawn(flag.getObjectOID());
        }
        flags.clear();
        
        // If game hasn't ended set flags to respawn in 10 seconds
        if (state == STATE_RUNNING) {
        	RespawnFlags flagRespawn = new RespawnFlags();
			Engine.getExecutor().schedule(flagRespawn, 10, TimeUnit.SECONDS);
        }
    }
    
    /**
     * The class to handle when the arenas timer has expired. This will check 
     * the victory condition and each teams score to determine a winner.
     *
     */
    public class RespawnFlags implements Runnable {
		public void run() {
			sendMessageAll("message_text", "Flags have respawned!");
			spawnFlags();
		}
    }
    
    public void activateAbility(OID playerOid, OID targetOid, int slot) {
    	if (state != STATE_RUNNING)
    		return;
    	ArenaMember player = getArenaPlayer(playerOid);
    	if (targetOid == null)
    		ArenaAbilities.ActivateAbility(slot, player, null, this);
    	else
    		ArenaAbilities.ActivateAbility(slot, player, getArenaPlayer(targetOid), this);
    }
    
    /**
     * Sets the flag clickable to either every team in the game, or just the opposing
     * teams of the flag.
     * @param flag
     * @param allTeams
     */
    public void setFlagClickable(ArenaFlag flag, boolean allTeams) {
    	int flagTeam = flag.getTeam();
    	if (allTeams) {
    		for (int i = 0; i < numTeams; i++) {
    			setFlagClickableToTeam(flag, flagTeam, i);
    		}
    	} else {
    		for (int teamToReactTo : getOpposingTeams(flagTeam)) {
        		setFlagClickableToTeam(flag, flagTeam, teamToReactTo);
            }
    	}
    	
    	Log.debug("FLAG: set flag clickable");
    }
    
    private void setFlagClickableToTeam(ArenaFlag flag, int flagTeam, int teamToReactTo) {
    	Log.debug("FLAG: about to mark flag as clickable for team: " + teamToReactTo);
    	for (ArenaMember member : getTeam(teamToReactTo).getActiveMembers()) {
    		OID oid = member.getOid();
    		TargetedPropertyMessage propMsg = new TargetedPropertyMessage(oid, flag.getObjectOID());
    		propMsg.setProperty("arena_flag", flagTeam);
    		Engine.getAgent().sendBroadcast(propMsg);
    		Log.debug("FLAG: set flag team clickable for player: " + oid);
    	}
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
    	// First add abilities that can be used by both races
    	abilities.add(ArenaAbilities.ABILITY_SPEED_BOOST);
    	abilities.add(ArenaAbilities.ABILITY_IMMUNITY);
    	abilities.add(ArenaAbilities.ABILITY_CREATE_TRAP);
    	abilities.add(ArenaAbilities.ABILITY_SLOW_GO);
    	//abilities.add(ArenaAbilities); AJ: Temporarily removed due to it causing problems
    	/*if (team == 0)
    		abilities.add(ArenaAbilities);
    	else if (team == 1)
    		abilities.add(ArenaAbilities);*/
    	return abilities;
    }
    
    /**
     * Add the score to the teams score.
     * 
     * @param team: Which team to add score to.
     * @param score: Score value to add. Can be negative.
     */
    protected void alterTeamScore(int team, int score) {
    	Log.debug("ARENA: team " + team + "'s score is being altered by: " + score);
    	teams[team].updateScore(score);
    	//Check the teams goal. If the goal is -1 we don't need to do anything else
    	//if (teamGoal[team] == -1)
    	//	return;
    	
    	sendStatMessageAll("Arena_teamstat_update", team, null, "score", teams[team].getTeamScore());
    	if (teams[team].getTeamGoal() == -1)
    	    return;
    	if (teams[team].getTeamScore() >= teams[team].getTeamGoal()) {
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
		if (msgType == "attack_cooldown") {
			HashMap<String, Serializable> map = (HashMap) data;
			props.put("cooldown_length", map.get("length"));
			props.put("ability_id", map.get("abilityID"));
			handled = true;
		} else if (msgType == "flag_carrier") {
			HashMap<String, Serializable> map = (HashMap) data;
			props.put("team", map.get("team"));
			props.put("carrier", map.get("carrier"));
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
    
    public String getFlagMesh() {
    	return flagMesh;
    }
    public int getFlagDisplayID(int team) {
    	if (team == 0)
    		return this.flagDisplayID0;
    	else
    		return this.flagDisplayID1;
    }
    
    //private static final int numAbilities = 4;
    private static final String PROP_ATTACK_COOLDOWN = "attackCooldown";
    private static final int initialHealth = 10;
    private static final int pointsPerKill = 15;
    private static final int pointsPerCapture = 20;
    private static final int pointsForVictory = 40;

    private static final long serialVersionUID = 1L;

	@Override
	public void releaseRequest(OID playerOid) {
		// TODO Auto-generated method stub
		
	}
}
