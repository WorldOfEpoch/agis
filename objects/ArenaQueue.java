package atavism.agis.objects;

import java.util.ArrayList;
import java.util.LinkedList;
import atavism.agis.plugins.ChatClient;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;

/**
 * The ArenaQueue class keeps track of players queued for a certain arena type. There 
 * should be a separate ArenaQueue object for each ArenaTemplate.
 * @author Andrew Harrison
 *
 */
public class ArenaQueue {
    protected int numTeams;
    protected int playersInQueue;
    protected int groupInQueue;
    protected boolean raceSpecific;
    protected ArrayList<QueueTeam> teams;
    protected QueueTeam groupteams[];
    protected int arenaType;
    protected int arenaCategory;
    protected String arenaName;
    protected int arenaDifficulty;
    ArrayList<String>[] teamRaces;
    /**
     * ArenaQueue Constructor. 
     * @param numTeams: the number of teams in this queue
     * @param raceSpecific: whether or not the teams require certain races
     * @param teamRaces: an array of ArrayLists which list which races can queue for which team
     * @param sizeReqs: an array indicating how many players need to be queued for each 
     *     team for a new arena to begin
     * @param arenaName: the name of the arena template this queue is for
     */
    public ArenaQueue(int numTeams, boolean raceSpecific, ArrayList<String>[] teamRaces, 
    		int[] sizeReqs, int arenaType, String arenaName, int category, int difficulty) {
    	this.numTeams = numTeams;
    	this.raceSpecific = raceSpecific;
    	teams = new ArrayList<QueueTeam>();//[numTeams+2];
    	for (int i = 0; i < numTeams; i++) {
    	    teams.add(i, new QueueTeam(sizeReqs[i], teamRaces[i])); //= new QueueTeam(sizeReqs[i], teamRaces[i]);
    	}
    	this.teamRaces = teamRaces;
    	playersInQueue = 0;
    	groupInQueue = 0;
    	this.arenaType = arenaType;
    	this.arenaName = arenaName;
    	this.arenaCategory = category;
    	this.arenaDifficulty = difficulty;
    }
    	
    /**
     * Adds the player to the queue. It checks if the queue requires different races
     * and will get the players race if so. Otherwise adds them to the team that has the 
     * lowest filled percent.
     * @param oid: the oid of the player being added to the queue
     */
    public boolean addPlayer(OID oid, String name/*, String selectedRace*/) {
    	if (isPlayerInQueue(oid)) {
			// A player should only be added to the queue once. If we get 
			// here, return false
			Log.warn("ARENA QUEUE: player " + oid + " is already in this queue");
			return false;
		}
    	if (raceSpecific) {
    		// Each team will only take members of certain races. We need to get
    		// the players race and find out which queue(s) they can go in.
    		ArrayList<Integer> queuesToJoin = new ArrayList<Integer>();
    		String playerRace = "";
    		try {
    			playerRace = (String) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "race");
    		} catch (NullPointerException e) {
    			Log.warn("ARENA QUEUE: players race not defined. Player oid: " + oid);
    		}
    		
    		for (int i = 0; i < numTeams; i++) {
    			if (teams.get(i).canJoinTeam(playerRace))
    				queuesToJoin.add(i);
    		}
    		if (queuesToJoin.size() == 0) {
    			// The player cannot join any queue as there are no queues which accept 
    			// players of their race. Ideally this code should never be run.
    			Log.warn("ARENA QUEUE: player " + oid + " has no queue they can join.");
    			return false;
    		}
    		int queueToJoin = queuesToJoin.get(0);
    		int percentOfSmallestQueue = teams.get(queuesToJoin.get(0)).getPercentFull();
    		for (int i = 0; i < numTeams; i++) {
    			int percentFull = teams.get(i).getPercentFull();
    			if (percentFull < percentOfSmallestQueue) {
    				queueToJoin = i;
    				percentOfSmallestQueue = percentFull;
    			}
    		}
    		teams.get(queueToJoin).addPlayer(oid, name/*, playerRace*/);
    		playersInQueue++;
    	} else {
    		// The player can be added to any queue. Lets find the queue with the 
    		// smallest filled percentage.
    		int queueToJoin = 0;
    		int percentOfSmallestQueue = teams.get(0).getPercentFull();
    		for (int i = 0; i < numTeams; i++) {
    			int percentFull = teams.get(i).getPercentFull();
    			if (percentFull < percentOfSmallestQueue) {
    				queueToJoin = i;
    				percentOfSmallestQueue = percentFull;
    			}
    		}
    		teams.get(queueToJoin).addPlayer(oid, name/*, selectedRace*/);
    		ChatClient.sendObjChatMsg(oid, 2, "You joined to queue of " + getArenaName());
			playersInQueue++;
    	}
    	return true;
    }
    
    
    /**
     * Adds the player to the queue. It checks if the queue requires different races
     * and will get the players race if so. Otherwise adds them to the team that has the 
     * lowest filled percent.
     * @param oid: the oid of the player being added to the queue
     */
    public boolean addGroup(LinkedList<OID> members,LinkedList<String> membersName) {
    	// LinkedList<OID> members = GroupClient.GetGroupMembers(oid);
    	  //Check all players in group is in queue
    	/*  for (OID groupMember : members) {
    		  if (isPlayerInQueue(groupMember)) {
  			// 	A player should only be added to the queue once. If we get 
  			// 	here, return false
    			  Log.warn("ARENA QUEUE: player " + groupMember + " is already in this queue");
    			  return false;
    		  }
    	  }
    	  */
       	  //First try check space for group and add group member to queue
   	  for (int i = 0; i < teams.size(); i++) {
    			if (teams.get(i).getNumQueued()+members.size()<=teams.get(i).getSizeReq()) {
    				 for (int j=0;j< members.size();j++) {
    				//	AgisGroupMember groupMember = groupMembers.get(groupMemberKey);
    					// String name = WorldManagerClient.getObjectInfo(members.get(j)).name;
    					 teams.get(i).addPlayer(members.get(j), membersName.get(j));
    					
    					 ChatClient.sendObjChatMsg(members.get(j), 2, "You joined to queue of " + getArenaName());
    					 playersInQueue++;
    					}
    				return true;
    				}
    			}
    	  //Add new QueueTaeam for Arena
    	  teams.add(new QueueTeam(teams.get(0).getSizeReq(), teamRaces[0]));
    	  //Secound try add group member to queue
    	  for (int i = 0; i < teams.size(); i++) {
  			if (teams.get(i).getNumQueued()+members.size()<=teams.get(i).getSizeReq()) {
  				 for (int j=0;j< members.size();j++) {
     				//	AgisGroupMember groupMember = groupMembers.get(groupMemberKey);
     					// String name = WorldManagerClient.getObjectInfo(members.get(j)).name;
     					 teams.get(i).addPlayer(members.get(j), membersName.get(j));
     					ChatClient.sendObjChatMsg(members.get(j), 2, "You joined to queue of " + getArenaName());
     					 playersInQueue++;
     					}
  				return true;
  				}
  			}
    	return false;
    }
    	
    
    /**
     * Removes the player from the queue. Searches through all the queues to find out
     * which team queue the player was in.
     * @param oid: the oid of the player being removed from the queue
     */
    public void removePlayer(OID oid) {
    	Log.debug("QUEUE: try removed player " + oid + " from queue: " + arenaName);
    	for (int i = 0; i < numTeams; i++) {
     		Log.debug("QUEUE: try removed player " + oid + " in team "+i+" from queue: " + arenaName);
        	if (teams.get(i).hasPlayer(oid)) {
    			teams.get(i).removePlayer(oid);
        		playersInQueue--;
        		Log.debug("QUEUE: removed player " + oid + " from queue: " + arenaName);
    		}
    	}
    }
    	
    /**
     * Searches through the arena queue to see if the player is already in it.
     * @param oid: the oid to check the queue for
     * @return: boolean indicating whether or not the player is in this arena queue
     */
    public boolean isPlayerInQueue(OID oid) {
    	for (int i = 0; i < teams.size(); i++) {
    		if (teams.get(i).hasPlayer(oid))
    			return true;
    	}
    	return false;
    }
    
    /**
     * Checks to see if there enough players queued to start a new arena.
     * The method gets the size of each team and sees if it is higher than
     * the size required for that team.
     * @return: a boolean indicating whether or not there are enough players queued
     */
    public boolean isQueueReady() {
    	Log.debug("ARENA - checking if queue for arena " + arenaName + " is ready. TempNumTeams: " + numTeams+" Teams: "+teams.size());
    	int ready=0;
    	for (int i = 0; i < teams.size(); i++) {
    		Log.debug("ARENA QUEUE: checking size of team " + i + ". size of queue: " + teams.get(i).getNumQueued());
    		for (int j=0;j<teams.get(i).getPlayersQueued().size();j++) {
    			if (teams.get(i).getPlayersQueued().get(j) == null) {
    				teams.get(i).getPlayersQueued().remove(j);
    				Log.debug("ARENA QUEUE:  team "+i+" ply "+j+" Remove because is null");
    			}
    		}
    			
    		if (teams.get(i).teamReady())	ready++; 
    	
    		if (ready>= this.numTeams)
    			return true;
    		}
    	
    	return false;
    }
    	
    public int getNumPlayersInQueue() { return playersInQueue; }
    public ArrayList<QueueMember>[] getPlayersQueued() { 
    	ArrayList<QueueMember>[] playersQueued = new ArrayList[numTeams];
    	int j=0;
    	for (int i = 0; i < teams.size(); i++) {
    		Log.debug("ARENA QUEUE: getPlayersQueued "+i+" "+teams.get(i).getPlayersQueued());
    		if (teams.get(i).getPlayersQueued().size()==teams.get(i).getSizeReq()) {
    			Log.debug("ARENA QUEUE: getPlayersQueued "+i+"  team == sizeReq adding");
    	    	
    			playersQueued[j++] = teams.get(i).getPlayersQueued();
    			}
    		
    		if (j>=2)
    			return playersQueued;
    	}
    	
    	return playersQueued; 
    }
    
    public int getArenaType() { return arenaType; }
    public void setArenaType(int arenaType) { this.arenaType = arenaType; }
    
    public String getArenaName() { return arenaName; }
    public void setArenaName(String arenaName) { this.arenaName = arenaName; }
    
    public int getArenaCategory() { return arenaCategory; }
    public void setArenaCategory(int arenaCategory) { this.arenaCategory = arenaCategory; }
    
    public int getArenaDifficulty() { return arenaDifficulty; }
    public void setArenaDifficulty(int arenaDifficulty) { this.arenaDifficulty = arenaDifficulty; }
    
    class QueueTeam {
    	ArrayList<QueueMember> playersQueued;
    	int sizeReq;
    	ArrayList<String> racesAllowed;
    	
    	public QueueTeam(int sizeReq, ArrayList<String> racesAllowed) {
    		this.sizeReq = sizeReq;
    		this.racesAllowed = racesAllowed;
    		playersQueued = new ArrayList<QueueMember>();
    	}
    	
    	public boolean canJoinTeam(String race) {
    		for (String raceAllowed : racesAllowed) {
    			if (raceAllowed.equals(race))
    				return true;
    		}
    		return false;
    	}
    	
    	public boolean teamReady() {
    		if (playersQueued.size() >= sizeReq)
    			return true;
    		return false;
    	}
    	
    	public boolean hasPlayer(OID oid) {
    		for (QueueMember member : playersQueued) {
    			if (member.getOid().equals(oid))
    				return true;
    		}
    		return false;
    	}
    	public void addPlayer(OID oid, String name/*, String selectedRace*/) {
    		QueueMember member = new QueueMember(oid, name/*, selectedRace*/);
    		Log.debug("Arena Queue: "+playersQueued+ " Add player "+name);
    		playersQueued.add(member);
    	}
    	public void removePlayer(OID oid) {
    	Log.debug("Arena Queue: start remove player "+oid+" "+playersQueued);
 		   for (QueueMember member : playersQueued) {
    			if (member.getOid().equals(oid)) {
    				Log.debug("Arena Queue: "+playersQueued+ " remove player "+oid);
    		    			playersQueued.remove(member);
    		    			Log.debug("Arena Queue: "+playersQueued+ " removeed ");
    		      		  		return;
    			}
    		}
    		
    	}
    	
    	public int getPercentFull() {
    		return playersQueued.size() / sizeReq;
    	}
    	
    	public int getNumQueued() {
    		return playersQueued.size();
    	}
    	public int getSizeReq() {
    		return sizeReq;
    	}
    	public ArrayList<QueueMember> getPlayersQueued() {
    		return playersQueued;
    	}
    }
    
    public class QueueMember {
    	protected OID oid;
    	protected String name;
    	protected String race;
    	
    	public QueueMember(OID oid, String name/*, String race*/) {
    		this.oid = oid;
    		this.name = name;
    		this.race = "";
    		//this.race = race;
    		Log.debug("QUEUE: added member with race: " + race);
    	}
    	
    	public OID getOid() { return oid; }
    	public String getName() { return name; }
    	public String getRace() { return race; }
    }
    
    
    
    
    
    
}