package atavism.agis.objects;

import java.io.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import atavism.agis.database.AccountDatabase;
import atavism.agis.plugins.*;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.math.*;

/**
 * The Duel class handles a single duel between two teams. It has numerous scheduled executors and a state
 * variable to control the flow of the duel from setup to the end. This class is complete, it 
 * relies on no other class to perform it's duties.
 * @author Andrew Harrison
 *
 */
public class DuelChallenge implements Serializable {
	
	private int numTeams;
    protected int challengeID;
    protected int duelType;
    protected Point centerLoc;
    protected ArrayList<OID> teams[];
    protected ArrayList<Boolean> accepted[];
    protected String challenger;
    protected String challenged;
    protected int state;
    protected OID flagOid;
    HashMap<Integer, ScheduledFuture> tasks = new HashMap<Integer, ScheduledFuture>();

    /**
     * The default constructor. This creates the Arrays and ArrayLists. This needs to be 
     * called by the other constructors.
     */
    public DuelChallenge() {
    	Log.debug("DUELCHALLENGE: starting generic duel challenge object construction");
    	this.numTeams = 2;
    	
    	teams = new ArrayList[numTeams];
    	accepted = new ArrayList[numTeams];
    	for (int i = 0; i < numTeams; i++) {
	    	if (teams[i] == null) {
	    		teams[i] = new ArrayList<OID>();
	    		accepted[i] = new ArrayList<Boolean>();
	    	}
    	}
    	
    	Log.debug("DUELCHALLENGE: finished generic duel challenge object construction");
    }

    /**
     * Constructor that takes in the challenger, the challenged, and all players involved
     * and creates a new DuelChallenge object.
     * @param challenger: the oid of the challenger
     * @param challenged: the oid of the player who was challenged
     * @param oids: a list of all players involved broken up into teams
     * @param type: the type of duel
     * @param id: the identification number of the arena
     */
    public DuelChallenge(String challenger, String challenged, ArrayList<OID> oids[], 
    		int type, int id, OID instanceOid) {
    	this();
    	Log.debug("DUELCHALLENGE: starting duel challenge creation: " + id);
    	
    	teams = oids;
    	challengeID = id;
    	duelType = type;
    	this.challenger = challenger;
    	this.challenged = challenged;
    	Point middle = new Point();
    	int numPlayers = 0;
    	
    	// Set the challenge ID property for all players
    	for (int i = 0; i < numTeams; i++) {
    		Iterator<OID> iter = teams[i].iterator();
    		while (iter.hasNext()) {
    			OID oid = iter.next();
    			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "duelChallengeID", challengeID);
    			
    			BasicWorldNode node = WorldManagerClient.getWorldNode(oid);
    			middle.add(node.getLoc());
    			numPlayers++;
    			
    			// If the oid matches the challengers oid, set his Accepted to True, set the rest to False
    			String name = WorldManagerClient.getObjectInfo(oid).name;
    			if (name.equals(challenger))
    				accepted[i].add(true);
    			else
    				accepted[i].add(false);
    		}
    	}
    	
    	centerLoc = new Point();
    	centerLoc.setX(middle.getX() / numPlayers);
    	centerLoc.setY(middle.getY() / numPlayers);
    	centerLoc.setZ(middle.getZ() / numPlayers);
    	
    	Log.debug("DUELCHALLENGE: finished duel challenge creation: " + id);
    	state = 0;
    	setup(instanceOid);
    }
    
    /**
     * This is run once the Duel Challenge object is created. It will be responsible for starting two timers, one 
     * for the challenge expiration (which occurs in 20 seconds) and the other being a position check to make sure
     * no one in this duel challenge has wandered too far away.
     */
    public void setup(OID instanceOid) {
    	if (state != 0) {
    		Log.warn("DUELCHALLENGE: Challenge " + challengeID + " has already been setup. State is not 0: " + state);
    		return;
    	}
    	state = 1;
    	sendMessageTeam("Duel_Challenge", 1, challenger);
    	sendChatMessageAll(challenger + " has challenged " + challenged + " to a duel!");
    	
    	// Spawn a flag or something at the center Loc
    	createFlag(instanceOid);
    	ChallengeExpire challengeExpire = new ChallengeExpire();
		Engine.getExecutor().schedule(challengeExpire, 30, TimeUnit.SECONDS);
		
		PositionCheck positionCheck = new PositionCheck();
		// Run the position check every second
		testTimer = 0;
		
		ScheduledFuture sf = Engine.getExecutor().scheduleAtFixedRate(positionCheck, 5, 1000, TimeUnit.MILLISECONDS);
		tasks.put(challengeID, sf);
    }
    
    private void createFlag(OID instanceOid) {
    	String specialFlag = (String)EnginePlugin.getObjectProperty(teams[0].get(0), WorldManagerClient.NAMESPACE, "DuelFlag");
    	// Get the current instance the challenger is in
    	Template markerTemplate = new Template();
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_NAME, "DuelFlag" + challengeID);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_OBJECT_TYPE, ObjectTypes.structure);
    	markerTemplate.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_PERCEPTION_RADIUS, 75);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_INSTANCE, instanceOid);
    	//markerTemplate.put(Namespace.COMBAT, WorldManagerClient.TEMPL_INSTANCE, instanceOid);
        	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_LOC, centerLoc);
    	//markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_ORIENT, orientation);
    	if (specialFlag != null && !specialFlag.isEmpty()) {
    		DisplayContext dc = new DisplayContext(specialFlag, true);
    		dc.addSubmesh(new DisplayContext.Submesh("", ""));
    		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
    		markerTemplate.put(Namespace.WORLD_MANAGER, "model", specialFlag); 
    	} else {
    		DisplayContext dc = new DisplayContext(Duel.DUEL_FLAG_MODEL, true);
    		dc.addSubmesh(new DisplayContext.Submesh("", ""));
    		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
    		markerTemplate.put(Namespace.WORLD_MANAGER, "model", Duel.DUEL_FLAG_MODEL); 
    	}
    	// Create the object
    	flagOid = ObjectManagerClient.generateObject(ObjectManagerClient.BASE_TEMPLATE_ID,
                ObjectManagerClient.BASE_TEMPLATE, markerTemplate);
            WorldManagerClient.spawn(flagOid);
    }
    
    /**
     * Iterates through every player in the duel and makes sure they are still within
     * 30m of the center point of the duel.
     *
     */
    protected Integer testTimer = 0;
    
    public class PositionCheck implements Runnable {
		public void run() {
			Log.debug("DUELChallenge: "+challengeID+" PositionCheck state:"+state);
			testTimer++;
			if (state != 1) {
				if (testTimer > 120) {
					tasks.get(challengeID).cancel(true);
					tasks.remove(challengeID);
				}
				return;
				}
			
			Log.debug("DUEL: PositionCheck tesyTimer: "+testTimer);
			//Log.debug("DUELCHALLENGE: doing a position check");
			for (int i = 0; i < numTeams; i++) {
				for (int j = 0; j < teams[i].size(); j++) {
					OID oid = teams[i].get(j);
					BasicWorldNode node = WorldManagerClient.getWorldNode(oid);
					if (node == null)
						continue;
					Point loc = node.getLoc();
					Log.debug("DUEL: player distance:"+Point.distanceTo(centerLoc, loc)+" from center: "+centerLoc);
					
					if (Point.distanceTo(centerLoc, loc) > 30000) {
						sendMessageAll("Duel_Challenge_End", null);
						String leaverName = WorldManagerClient.getObjectInfo(oid).name;
						sendChatMessageAll(leaverName + " has declined the duel challenge.");
						killChallenge();
					}
				}
			}
		}
    }
    
    /**
     * Sets the players accepted property to true. This is called when a player sends a /duelAccept
     * command to the server.
     * @param acceptOid: the oid of the player who has accepted this duel.
     */
    public void playerAccept(OID acceptOid) {
    	for (int i = 0; i < numTeams; i++) {
    		for (int j = 0; j < teams[i].size(); j++) {
    			OID oid = teams[i].get(j);
    			
    			// If the oid matches the acceptors oid, set his Accepted to True
    			if (oid.equals(acceptOid)) {
    				accepted[i].set(j, true);
    				Log.debug("DUELCHALLENGE: accept, found player and setting to accepted");
    			}
    		}
    	}
    	
    	// Now check to see if everyone has accepted
    	checkAccepts();
    }
    
    /**
     * Cancels the duel challenge because a player does not wish to partake in the duel. This is called when 
     * a player sends a /duelDecline command to the server.
     * @param oid: the oid of the player who declined the duel
     */
    public void playerDeclined(OID delineOid) {
    	// Make sure the player is infact in this duel, if so, kill it
    	for (int i = 0; i < numTeams; i++) {
    		Iterator<OID> iter = teams[i].iterator();
    		while (iter.hasNext()) {
    			OID oid = iter.next();
    			// If the oid matches the decliners oid, kill the challenge
    			if (oid.equals(delineOid)) {
    				sendMessageAll("Duel_Challenge_End", null);
    				//String leaverName = WorldManagerClient.getObjectInfo(delineOid).name;
    				AccountDatabase adb = new AccountDatabase(false);
    				String leaverName = adb.getCharacterNameByOid(delineOid);
					sendChatMessageAll(leaverName + " has declined the duel challenge.");
    				killChallenge();
    			}
    		}
    	}
    }
    
    /**
     * Cancels the duel challenge because a player has disconnected.
     * @param oid: the oid of the player who disconnected
     * @param name: the name of the player who disconnected
     */
    public void playerDisconnected(OID delineOid, String name) {
    	// Make sure the player is infact in this duel, if so, kill it
    	for (int i = 0; i < numTeams; i++) {
    		Iterator<OID> iter = teams[i].iterator();
    		while (iter.hasNext()) {
    			OID oid = iter.next();
    			// If the oid matches the decliners oid, kill the challenge
    			if (oid.equals(delineOid)) {
    				sendMessageAll("Duel_Challenge_End", null);
					sendChatMessageAll(name + " has declined the duel challenge.");
    				killChallenge();
    			}
    		}
    	}
    }
    
    private void checkAccepts() {
    	Log.debug("DUELCHALLENGE: checking if all players have accepted");
    	for (int i = 0; i < numTeams; i++) {
    		for (int j = 0; j < teams[i].size(); j++) {
    			if (accepted[i].get(j) == false) {
    				Log.debug("DUELCHALLENGE: found player who has not accepted on team " + i + "; position " + j);
    				return;
    			}
    		}
    	}
    	
    	// If the code gets to here everyone has accepted, so lets start the duel
    	Log.debug("DUELCHALLENGE: all players have accepted, starting duel");
    	if (state != 1)
			return;
    	state = 2;
    	ArenaClient.duelStart(challengeID);
    }
    
    /**
     * Starts the arena. Sends a message to the client so the client can update the UI and
     * schedules the endArena executioner.
     *
     */
    public class ChallengeExpire implements Runnable {
		public void run() {
			Log.debug("DUELCHALLANGE: "+challengeID+" ChallengeExpire state: "+state);
		    	if (state != 1) {
				Log.warn("DUELCHALLENGE: Duel challenge " + challengeID + " is not starting. state is not 1");
				return;
			}
			//state = 2;
			
			sendMessageAll("Duel_Challenge_End", null);
			sendChatMessageAll("The duel challenge has expired");
			killChallenge();
		}
	}
    
    private void killChallenge() {
    	Log.debug("DUELCHALLANGE: "+challengeID+" killChallenge state: "+state);
    	if (state != 1)
			return;
    	state = 2;
    	for (int i = 0; i < numTeams; i++) {
    		Iterator<OID> iter = teams[i].iterator();
    		while (iter.hasNext()) {
    			OID oid = iter.next();
    			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "duelChallengeID", -1);
    			EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "busy", false);
    		}
    	}
    	WorldManagerClient.despawn(flagOid);
    	ArenaClient.duelChallengeRemove(challengeID);
    }
    
    /**
     * Calls sendMessageTeam for each team in the arena.
     * @param msgType: the message type
     * @param data: some form of data to be sent
     */
    private void sendMessageAll(String msgType, Serializable data) {
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
    private void sendMessageTeam(String msgType, int team, Serializable data) {
		for (int i = 0; i < teams[team].size(); i++) {
			sendMessageSingle(msgType, teams[team].get(i), data);
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
		if (msgType == "Duel_Challenge") {
			props.put("challenger", data);
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
    	for (int i = 0; i < numTeams; i++) {
    		sendChatMessageTeam(msg, i);
    	}
    }
    
    /**
     * Calls sendChatMessageSingle for each player in the specified team.
     * @param msg: the chat message to send
     * @param team: the team to send the chat message to
     */
    private void sendChatMessageTeam(String msg, int team) {
	    for (int i = 0; i < teams[team].size(); i++) {
	    	sendChatMessageSingle(msg, teams[team].get(i));
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
    
    public int getChallengeID() { return challengeID; }
    public void setChallengeID(int challengeID) { this.challengeID = challengeID; }
    
    public int getDuelType() { return duelType; }
    public void setDuelType(int duelType) { this.duelType = duelType; }
    
    public int getState() { return state; }
    public void setState(int state) { this.state = state; }
    
    public ArrayList<OID>[] getTeam() { return teams; }
    public void setTeam(ArrayList<OID>[] teams) { this.teams = teams; }
    
    public ArrayList<OID> getTeam(int team) { return teams[team]; }
    public void setTeam(int team, ArrayList<OID> teams) { this.teams[team] = teams; }
    
    public ArrayList<Boolean>[] getAccepted() { return accepted; }
    public void setAccepted(ArrayList<Boolean>[] accepted) { this.accepted = accepted; }
    
    public String getChallenger() { return challenger; }
    public void setChallenger(String challenger) { this.challenger = challenger; }
    
    public String getChallenged() { return challenged; }
    public void setChallenged(String challenged) { this.challenged = challenged; }
    
    public Point getCenter() { return centerLoc; }
    public void setCenter(Point centerLoc) { this.centerLoc = centerLoc; }
    
    public OID getFlagOid() { return flagOid; }
    public void setFlagOid(OID flagOid) { this.flagOid = flagOid; }

    private static final long serialVersionUID = 1L;
}
