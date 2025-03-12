package atavism.agis.objects;

import java.io.*;
import java.util.*;

import atavism.server.util.*;
import atavism.server.math.Point;

/**
 * The ArenaTemplate class stores all the information needed about an arena type. 
 * @author Andrew Harrison
 *
 */
public class ArenaTemplate implements Serializable {
	protected int typeID;
	protected int arenaType; // 0 = Muncher, 1 = Bomber, 2 = CTF
	protected String arenaName;
	protected int arenaCategory;
	protected int levelReq;
	protected int levelMax;
	protected ArrayList<ArenaTeam> teams;
    protected HashMap<String, Integer> resourceGoals;
    protected int length;
    protected int victoryCondition;
    protected boolean raceSpecific;
    protected int numRounds;
    protected ArrayList<Integer> spawnIDs[];
    protected HashMap<Integer, Integer> victoryPayment;
    protected HashMap<Integer, Integer> defeatPayment;
    protected int victoryExp;
    protected int defeatExp;
    protected boolean useWeapons;
    protected int instanceTemplateID;
    protected String description = "";
    protected int start_minute;
    protected int start_hour;
    protected int end_minute;
    protected int end_hour;
    
   /**
    * The constructor for the ArenaTemplate. Fills all the data for the class as needed.
    * @param type: an integer to identify the arena (should be unique)
    * @param numTeams: the number of teams for this arena
    * @param teamNames: the names of each team
    * @param teamMinSizes: the minimum size for each team
    * @param teamSizes: the maximum size for each team
    * @param teamGoals: the goal for each team (as points)
    * @param duration: how long the arena goes for
    * @param condition: which team wins when the time expires
    * @param worldFile: which instance template should be used
    * @param arenaName: the name for the arena type
    * @param raceSpecific: does each team require specific races?
    * @param races: an arraylist of which races can go in which team
    */
    public ArenaTemplate(int typeID, int arenaType, int arenaCategory, int duration, int condition, 
    		int instanceTemplateID, String arenaName, boolean raceSpecific, int numRounds,
    		ArrayList<ArrayList<Integer>> spawns) {
    	Log.debug("ARENA TEMPLATE: starting arenaTemplate creation");
    	length  = duration;
    	victoryCondition = condition;
    	this.typeID = typeID;
    	this.arenaType = arenaType;
    	this.arenaCategory = arenaCategory;
    	this.instanceTemplateID = instanceTemplateID;
    	this.arenaName = arenaName;
    	this.raceSpecific = raceSpecific;
    	this.numRounds = numRounds;
    	this.useWeapons = false;
    	/*if (arenaType == 1) {
    		// Read in the spawn information
    		spawnIDs = new ArrayList[numRounds];
    		for (int i = 0; i < numRounds; i++) {
        		spawnIDs[i] = spawns.get(i);
        	}
    	}*/
    	teams = new ArrayList<ArenaTeam>();
	    Log.debug("ARENA TEMPLATE: finished arenaTemplate creation");
    }
    
    public void addTeam(String name, int size, String race, int goal, Point spawnPoint) {
    	ArenaTeam team = new ArenaTeam(name, size, race, goal, spawnPoint);
    	teams.add(team);
    }
    
    public int getNumTeams() {
    	return teams.size();
    }
    
    public int getTeamSize(int team) {
    	return teams.get(team).getSize();
    }
    
    public ArrayList<String>[] getTeamRaces() {
    	ArrayList<String>[] teamRaces = new ArrayList[teams.size()];
    	for (int i = 0; i < teams.size(); i++) {
    		teamRaces[i] = teams.get(i).getRaces();
    	}
    	return teamRaces;
    }
    
    public int[] getTeamSizes() {
    	int[] teamSizes = new int[teams.size()];
    	for (int i = 0; i < teams.size(); i++) {
    		teamSizes[i] = teams.get(i).getSize();
    	}
    	return teamSizes;
    }
    
    public String[] getTeamNames() {
    	String[] teamNames = new String[teams.size()];
    	for (int i = 0; i < teams.size(); i++) {
    		teamNames[i] = teams.get(i).getName();
    	}
    	return teamNames;
    }
    
    public int[] getTeamGoals() {
    	int[] teamGoals = new int[teams.size()];
    	for (int i = 0; i < teams.size(); i++) {
    		teamGoals[i] = teams.get(i).getGoal();
    	}
    	return teamGoals;
    }
    
    public Point[] getSpawnPoints() {
    	Point[] spawnPoints = new Point[teams.size()];
    	for (int i = 0; i < teams.size(); i++) {
    		spawnPoints[i] = teams.get(i).getSpawnPoint();
    	}
    	return spawnPoints;
    }
    
    public int getTypeID() { return typeID; }
    public void setTypeID(int typeID) { this.typeID = typeID; }
    
    public int getArenaType() { return arenaType; }
    public void setArenaType(int arenaType) { this.arenaType = arenaType; }
    
    public String getArenaName() { return arenaName; }
    public void setArenaName(String arenaName) { this.arenaName = arenaName; }
    
    public int getArenaCategory() { return arenaCategory; }
    public void setArenaCategory(int arenaCategory) { this.arenaCategory = arenaCategory; }
    
    public int getLevelReq() { return levelReq; }
    public void setLevelReq(int levelReq) { this.levelReq = levelReq; }
    
    public int getLevelMax() { return levelMax; }
    public void setLevelMax(int levelMax) { this.levelMax = levelMax; }
    
    public ArrayList<ArenaTeam> getTeams() { return teams; }
    public void setTeams(ArrayList<ArenaTeam> teams) { this.teams = teams; }
    
    public HashMap<String, Integer> getResourceGoals() { return resourceGoals; }
    public void setResourceGoals(HashMap<String, Integer> resourceGoals) { this.resourceGoals = resourceGoals; }
    
    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }
   
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public int getVictoryCondition() { return victoryCondition; }
    public void setVictoryCondition(int victoryCondition) { this.victoryCondition = victoryCondition; }
    
    public boolean getRaceSpecific() { return raceSpecific; }
    public void setRaceSpecific(boolean raceSpecific) { this.raceSpecific = raceSpecific; }
    
    public int getNumRounds() { return numRounds; }
    public void setNumRounds(int numRounds) { this.numRounds = numRounds; }
    
    public ArrayList<Integer>[] getSpawnIDs() { return spawnIDs; }
    public void setSpawnIDs(ArrayList<Integer>[] spawnIDs) { this.spawnIDs = spawnIDs; }
    
    public HashMap<Integer, Integer> getVictoryPayment() { return victoryPayment; }
    public void setVictoryPayment(HashMap<Integer, Integer> victoryPayment) { this.victoryPayment = victoryPayment; }
    
    public HashMap<Integer, Integer> getDefeatPayment() { return defeatPayment; }
    public void setDefeatPayment(HashMap<Integer, Integer> defeatPayment) { this.defeatPayment = defeatPayment; }
    
    public int getVictoryExp() { return victoryExp; }
    public void setVictoryExp(int victoryExp) { this.victoryExp = victoryExp; }
    
    public int getDefeatExp() { return defeatExp; }
    public void setDefeatExp(int defeatExp) { this.defeatExp = defeatExp; }
   
    public int getStartMinute() { return start_minute; }
    public void setStartMinute(int start_minute) { this.start_minute = start_minute; }
    
    public int getEndMinute() { return end_minute; }
    public void setEndMinute(int end_minute) { this.end_minute = end_minute; }
    
    public int getStartHour() { return start_hour; }
    public void setStartHour(int start_hour) { this.start_hour = start_hour; }
    
    public int getEndHour() { return end_hour; }
    public void setEndHour(int end_hour) { this.end_hour = end_hour; }
    
    public boolean getUseWeapons() { return useWeapons; }
    public void setUseWeapons(boolean useWeapons) { this.useWeapons = useWeapons; }
    
    public int getInstanceTemplateID() { return instanceTemplateID; }
    public void setInstanceTemplateID(int instanceTemplateID) { this.instanceTemplateID = instanceTemplateID; }
    
    public class ArenaTeam {
    	String name;
    	int size;
    	ArrayList<String> races;
    	int goal;
    	Point spawnPoint;
    	
    	public ArenaTeam(String name, int size, String race, int goal, Point spawnPoint) {
    		this.name = name;
    		this.size = size;
    		this.races = new ArrayList<String>();
    		if (race != null && race.equals(""))
    			races.add(race);
    		this.goal = goal;
    		this.spawnPoint = spawnPoint;
    	}
    	
    	public String getName() { return name; }
    	public void setName(String name) { this.name = name; }
    	
    	public int getSize() { return size; }
    	public void setSize(int size) { this.size = size; }
    	
    	public ArrayList<String> getRaces() { return races; }
    	public void setRaces(ArrayList<String> race) { this.races = race; }
    	
    	public int getGoal() { return goal; }
    	public void setGoal(int goal) { this.goal = goal; }
    	
    	public Point getSpawnPoint() { return spawnPoint; }
    	public void setSpawnPoint(Point spawnPoint) { this.spawnPoint = spawnPoint; }
    }
    
    private static final long serialVersionUID = 1L;
}
