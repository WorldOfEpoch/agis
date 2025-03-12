package atavism.agis.objects;

import java.io.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import atavism.agis.database.AccountDatabase;
import atavism.agis.plugins.*;
import atavism.agis.util.EventMessageHelper;
import atavism.server.util.*;
import atavism.server.engine.*;
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
public class Duel implements Serializable {
	
	private int numTeams;
    protected int duelID;
    protected int duelType;
    HashMap<Integer, ScheduledFuture> tasks = new HashMap<Integer, ScheduledFuture>();

    /**
     * Status of the arena
     * 0: Not yet set
     * 1: Starting
     * 2: Running
     * 3: Ending
     */
    private int state;
    
    protected ArrayList<OID> teams[];
    protected ArrayList<Boolean> activeFighters[];
    protected ArrayList<Integer> oobTimer[];
    protected Point centerLoc;
    protected String teamLeader[];
    protected OID flagOid;
    
    protected int timeUntilStart = 5;

    /**
     * The default constructor. This creates the Arrays and ArrayLists. This needs to be 
     * called by the other constructors.
     */
    public Duel() {
    	Log.debug("DUEL: starting generic duel object construction");
    	this.numTeams = 2;
    	
    	teams = new ArrayList[numTeams];
    	activeFighters = new ArrayList[numTeams];
    	oobTimer = new ArrayList[numTeams];
    	teamLeader = new String[numTeams];
    	for (int i = 0; i < numTeams; i++) {
	    	if (teams[i] == null) {
	    		teams[i] = new ArrayList<OID>();
	    		activeFighters[i] = new ArrayList<Boolean>();
	    		oobTimer[i] = new ArrayList<Integer>();
	    	}
    	}
    	
    	Log.debug("DUEL: finished generic duel object construction");
    }

    /**
     * Constructor that takes in two lists that contain the oids of the players
     * on each team.
     * @param oids: the oids of the players involved in this duel
     * @param type: the duel type
     * @param loc: the centre point of the duel
     * @param id: the identification number of the duel
     */
    public Duel(String[] teamLeaders, ArrayList<OID> oids[],  int type, int id, Point centerLoc, OID flagOid) {
    	this();
    	Log.debug("DUEL: starting duel creation: " + id);
    	
    	teams = oids;
	    for (int i = 0; i < numTeams; i++) {
	    	for (int j = 0; j < teams[i].size(); j++) {
	    		activeFighters[i].add(true);
	    		oobTimer[i].add(0);
	    	}
	    }
    	duelID = id;
    	duelType = type;
    	state = 0;
    	this.teamLeader = teamLeaders;
    	this.centerLoc = centerLoc;
    	this.flagOid = flagOid;
    	
    	// Set the duel ID property for all players
    	for (int i = 0; i < numTeams; i++) {
    		Iterator<OID> iter = teams[i].iterator();
    		while (iter.hasNext()) {
    			OID oid = iter.next();
    			EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DUEL_ID, duelID);
    		}
    	}
    	
    	Log.debug("DUEL: finished duel creation: " + id);
    	setup();
    }
    
    /**
     * This is run once the Duel object is created. It will be responsible for sending the setup extension 
     * message to all players and then getting DuelStart() to be activated in 5 seconds.
     */
    public void setup() {
    	if (state != 0) {
    		Log.error("DUEL: Duel " + duelID + " has already been setup. State is not 0");
    		return;
    	}
    	state = 1;
    	sendMessageAll("duel_setup", null);
    	sendEventAll(EventMessageHelper.DUEL_COUNTDOWN, timeUntilStart, "");
    	
    	DuelStart duelTimer = new DuelStart();
		Engine.getExecutor().schedule(duelTimer, 1, TimeUnit.SECONDS);
    }
    
    /**
     * Starts the arena. Sends a message to the client so the client can update the UI and
     * schedules the endArena executioner.
     *
     */
    public class DuelStart implements Runnable {
		public void run() {
			Log.debug("DUEL: Duel started with state: " + state+"; timeUntilStart"+timeUntilStart);
			if (state != 1) {
				Log.error("DUEL: Duel " + duelID + " is not starting. state is not 1; it is: " + state);
				return;
			}
			// Check timer
			timeUntilStart--;
			if (timeUntilStart > 0) {
				sendEventAll(EventMessageHelper.DUEL_COUNTDOWN, timeUntilStart, "");
				Engine.getExecutor().schedule(this, 1, TimeUnit.SECONDS);
				return;
			}
			state = 2;
			// Set the duel ID property for all players
	    	for (int i = 0; i < numTeams; i++) {
	    		Iterator<OID> iter = teams[i].iterator();
	    		while (iter.hasNext()) {
	    			OID oid = iter.next();
	    			// Also set some faction/attitude properties here
	    			String factionOverride = duelID + "_team" + i;
	        		EnginePlugin.setObjectProperty(oid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, factionOverride);
	    		}
	    	}
			Log.debug("DUEL: Duel started with state: " + state);
			sendMessageAll("duel_start", null);
			PositionCheck positionCheck = new PositionCheck();
			sendEventAll(EventMessageHelper.DUEL_START, ArenaPlugin.DUEL_DURATION, "");
			testTimer = 0;
			// Run the position check every second
			ScheduledFuture sf = Engine.getExecutor().scheduleAtFixedRate(positionCheck, 5, 1000, TimeUnit.MILLISECONDS);
			tasks.put(duelID, sf);
		}
	}
    
    
    
    
    
    
    
    /**
     * Iterates through every player in the duel and makes sure they are still within
     * 30m of the center point of the duel.
     *
     */
    protected Integer testTimer = 0;
    protected ArrayList<Integer> teamHealths ; 

    public class PositionCheck implements Runnable {
		public void run() {
			Log.debug("DUEL: PositionCheck state:"+state);
				if (state != 2)
				return;
			testTimer++;
			int teamHealth = 0;
			teamHealths = new ArrayList<Integer>(); 
			Log.debug("DUEL: PositionCheck tesyTimer: "+testTimer);
			for (int i = 0; i < numTeams; i++) {
				
				for (int j = 0; j < teams[i].size(); j++) {
			        // Check if the player is still active, if not, skip
					if (activeFighters[i].get(j) == false)
						continue;
					
					OID oid = teams[i].get(j);
					Log.debug("DUEL: PositionCheck before get node ");
					
					BasicWorldNode node = WorldManagerClient.getWorldNode(oid);
					Log.debug("DUEL: PositionCheck teamHealth: "+teamHealth);
					teamHealth += CombatClient.getPlayerStatValue(oid, CombatPlugin.HEALTH_STAT);
					Log.debug("DUEL: PositionCheck teamHealth: "+teamHealth);
					if (node == null)
						continue;
					Point loc = node.getLoc();
					Log.debug("DUEL: player distance:"+Point.distanceTo(centerLoc, loc)+" from center: "+centerLoc);
					if (Point.distanceTo(centerLoc, loc) > 30000) {
						// Start a 10 second count down
						int timer = oobTimer[i].get(j);
						timer++;
						oobTimer[i].set(j, timer);
						if (timer == 1) {
							sendEventSingle(EventMessageHelper.DUEL_OUT_OF_BOUNDS, oid, 0, "");
						}
						// If timer is 10 seconds, player forfeits
						if (timer >= 10) {
							removePlayer(teams[i].get(j));
						}
					} else {
						// Reset their out of bounds timer
						int timer = oobTimer[i].get(j);
						if (timer != 0) {
						    oobTimer[i].set(j, 0);
						    Log.debug("DUEL: doing a position check 4");
						    sendEventSingle(EventMessageHelper.DUEL_NOT_OUT_OF_BOUNDS, oid, 0, "");
						}
					}
				}
				Log.debug("DUEL: doing a position check teamHealths set");
				teamHealths.add(i, teamHealth);
				Log.debug("DUEL: doing a position check teamHealths 0");
				teamHealth = 0;
				Log.debug("DUEL: doing a position check next team");
			}
			   Log.debug("DUEL: doing a position check testTimer if");
			if (testTimer > ArenaPlugin.DUEL_DURATION) {
				int teamsLessHealth=0;
			//	for (int ii=0;ii< numTeams-1; ii++)
					for (int ik=0;ik< teamHealths.size(); ik++) {
						Log.debug("DUEL: doing a position check timeout "+teamHealths.get(teamsLessHealth)+"|"+teamHealths.get(ik));
						if (teamHealths.get(teamsLessHealth) > teamHealths.get(ik)) {
							   Log.debug("DUEL: teamsMoreHealth < ik");
							teamsLessHealth = ik;
						}
					}
					ArenaClient.duelDefeat(teams[teamsLessHealth].get(0));
					Log.debug("DUEL: Team "+teamsLessHealth+" have more health from others");
			}
		}
    }
    
    /**
     * Ends this arena instance.
     * 
     * @param winningTeam: An integer representing the winning team
     */
    private void endGame(int losingTeam) {
    	if (state != 2) {
			Log.error("DUEL: Duel " + duelID + " is not running. state is not 2");
			return;
		}
		state = 3;
		
    	Log.debug("DUEL: endGame hit with loser: " + losingTeam);
    	sendMessageAll("duel_end", null);
    	int winningTeam = 0;
    	if (losingTeam == 0) 
    		winningTeam = 1;
    	
    	String winnerName = teamLeader[winningTeam];
    	String loserName = teamLeader[losingTeam];
    	sendEventAll(EventMessageHelper.DUEL_VICTORY, 0, winnerName + "," + loserName);
    	
    	for (int i = 0; i < numTeams; i++) {
			for (int j = 0; j < teams[i].size(); j++) {
				if (activeFighters[i].get(j)) {
					OID oid = teams[i].get(j);
				    EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DUEL_ID, -1);
				    EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "busy", false);
		    		EnginePlugin.setObjectProperty(oid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, "");
				}
			}
    	}
		
		for (int i = 0; i < numTeams; i++) {
			if (i == winningTeam) {
				int value = 101;
				sendMessageTeam("message_text", i, value);
			} else {
				int value = 102;
				sendMessageTeam("message_text", i, value);
			}
		}
		 if (tasks.containsKey(duelID)) {
			 Log.debug("DUEL: cancelling PositionCheck task");
	    	 tasks.get(duelID).cancel(true);
	    	 tasks.remove(duelID);
		 }
		
		WorldManagerClient.despawn(flagOid);
		ArenaClient.duelRemove(duelID);
    }
    
    /**
     * This function runs through all the players in both teams to see if any teams have 
     * no active players left. If a team is found, the other team is the winner.
     */
    public void checkActivePlayers() {
    	Log.debug("DUEL: Checking active players");
    	for (int i = 0; i < numTeams; i++) {
    		int teamMembersActive = 0;
			for (int j = 0; j < activeFighters[i].size(); j++) {
				if (activeFighters[i].get(j) == true)
					teamMembersActive++;
			}
			Log.debug("DUEL: team " + i + " has " + teamMembersActive + " left.");
			if (teamMembersActive == 0) {
				endGame(i);
			}
		}
    }
    
    /**
     * Removes a player from the arena. 
     * 
     * @param oid: The player being removed from the arena
     */
    public void removePlayer(OID oid) {
    	Log.debug("DUEL: removing player: " + oid);
    	int team = -1;
    	int playerIndex = -1;
    	for (int i = 0; i < numTeams; i++) {
    		if (teams[i].contains(oid)) {
    			playerIndex = teams[i].indexOf(oid);
    			if (activeFighters[i].get(playerIndex) == true)
    			    team = i;
    			break;
    		}
    	}
    	
    	Log.debug("DUEL: removing player team: " + team);
    	if (team < 0) 
    		return;
    	
    	// Remove all the effects from other duel members
    	//removeDuelEffects(oid);
    	sendMessageAll("duel_player_removed", null);
    	activeFighters[team].set(playerIndex, false);
    	EnginePlugin.setObjectProperty(oid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DUEL_ID, -1);
    	EnginePlugin.setObjectProperty(oid, WorldManagerClient.NAMESPACE, "busy", false);
		EnginePlugin.setObjectProperty(oid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, "");
    	checkActivePlayers();
    	AccountDatabase aDB = new AccountDatabase(false);
    	String playerName = aDB.getCharacterNameByOid(oid);
    	//String playerName = WorldManagerClient.getObjectInfo(oid).name;
    	sendEventAll(EventMessageHelper.DUEL_DEFEAT, 0, playerName);
    }
    
    /**
     * Removes a player from the arena. 
     * 
     * @param oid: The player being removed from the arena
     */
    public void disconnectedPlayer(OID oid, String playerName) {
    	Log.debug("DUEL: removing disconnected player: " + oid);
    	int team = -1;
    	int playerIndex = -1;
    	for (int i = 0; i < numTeams; i++) {
    		if (teams[i].contains(oid)) {
    			playerIndex = teams[i].indexOf(oid);
    			if (activeFighters[i].get(playerIndex) == true)
    			    team = i;
    			break;
    		}
    	}
    	
    	Log.debug("DUEL: removing disconnected player team: " + team);
    	if (team < 0) 
    		return;
    	
    	// Remove all the effects from other duel members
    	ArenaClient.removeEffects(oid, "duel", duelID);
    	sendEventAll(EventMessageHelper.DUEL_DEFEAT, 0, playerName);
    	sendMessageAll("duel_player_removed", null);
    	activeFighters[team].set(playerIndex, false);
    	checkActivePlayers();
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
		
    	if (!(state == 2)) {
    		Log.error("ARENA: addKillScore(): Arena is not running. State is not 2.");
    		return;
		}
		if (attackerOid == victimOid) {
			Log.error("ARENA: addKillScore(): Suicide attempt. AttackerOid equals VictimOid");
			return;
		}
		
		// First figure out the team of victim and killer
		int victimTeam = -1;
		int attackerTeam = -1;
		for (int i = 0; i < numTeams; i++) {
			if (teams[i].contains(attackerOid))
				attackerTeam = i;
			else if (teams[i].contains(victimOid))
				victimTeam = i;
		}
		
		if (victimTeam == -1 || attackerTeam == -1)
			return;
		
		removePlayer(victimOid);
        // Apply a stun effect
        // CombatClient.startAbility("knockOut", attackerOid, victimOid, null);
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
		if (msgType == "JoinedArena") {
			props.put("numTeams", numTeams);
			
			for (int i = 0; i < numTeams; i++) {
				props.put("teamOids" + i, teams[i].toString());
				props.put("activeFighers" + i, activeFighters[i].toString());
			}
		} else if (msgType == "message_text") {
			int value = (Integer) data;
			props.put("state", state);
			props.put("MessageType", value);
			if (value == 50)
			    props.put("duration", 30000);
		} else if (msgType == "Arena_player_left") {
			props.put("playerOid", data);
		} else if (msgType == "Arena_setup") {
			props.put("arenaType", duelType);
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
    private void sendEventAll(String msg, int val, String data) {
    	for (int i = 0; i < numTeams; i++) {
    		sendEventTeam(msg, i, val, data);
    	}
    }
    
    /**
     * Calls sendChatMessageSingle for each player in the specified team.
     * @param msg: the chat message to send
     * @param team: the team to send the chat message to
     */
    private void sendEventTeam(String eventType, int team, int val, String data) {
	    for (int i = 0; i < teams[team].size(); i++) {
	    	sendEventSingle(eventType, teams[team].get(i), val, data);
	    }
    }
    
    /**
     * Sends the specified chat message to the specified player
     * @param msg: the chat message to send
     * @param oid: the oid of the player to send the message to
     */
    private void sendEventSingle(String eventType, OID oid, int val, String data) {
    	EventMessageHelper.SendGeneralEvent(oid, eventType, val, data);
    }
    
    public int getDuelID() { return duelID; }
    public void setDuelID(int duelID) { this.duelID = duelID; }
    
    public int getDuelType() { return duelType; }
    public void setDuelType(int duelType) { this.duelType = duelType; }
    
    public int getState() { return state; }
    public void setState(int state) { this.state = state; }
    
    public ArrayList<OID>[] getTeams() { return teams; }
    public void setTeams(ArrayList<OID>[] teams) { this.teams = teams; }
    
    public ArrayList<OID> getTeam(int team) { return teams[team]; }
    public void setTeam(int team, ArrayList<OID> teams) { this.teams[team] = teams; }
    
    public ArrayList<Boolean>[] getActiveFighters() { return activeFighters; }
    public void setActiveFighters(ArrayList<Boolean>[] activeFighters) { this.activeFighters = activeFighters; }
    
    public ArrayList<Integer>[] getOobTimers() { return oobTimer; }
    public void setOobTimers(ArrayList<Integer>[] oobTimer) { this.oobTimer = oobTimer; }
    
    public String[] getTeamLeaders() { return teamLeader; }
    public void setTeamLeaders(String[] teamLeader) { this.teamLeader = teamLeader; }
    
    public Point getCenter() { return centerLoc; }
    public void setCenter(Point centerLoc) { this.centerLoc = centerLoc; }
    
    public OID getFlagOid() { return flagOid; }
    public void setFlagOid(OID flagOid) { this.flagOid = flagOid; }

    public static final String DUEL_FLAG_MODEL = "DuelFlag";
    private static final long serialVersionUID = 1L;
}
