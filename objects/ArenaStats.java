package atavism.agis.objects;

import java.io.*;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

import atavism.agis.database.ArenaDatabase;
import atavism.agis.plugins.ArenaPlugin;
import atavism.agis.plugins.ChatClient;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;
import atavism.server.util.Log;

/**
 * Keeps track of a players stats in the arena.
 * @author Andrew
 *
 */
public class ArenaStats implements Serializable {
	
	OID oid;
    String name;
    int level;
    int experience;
    int experienceRequired;
    int wins;
    int losses;
    int totalKills = 0;
    int totalDeaths = 0;
    int objectsConsumed;
    HashMap<Integer, ArenaTypeStats> arenaTypesMap;
    HashMap<Integer, ArenaSubTypeStats> arenaSubTypesMap;
    
    public ArenaStats(OID oid, String name) {
    	this.oid = oid;
    	this.totalKills = 0;
    	this.totalDeaths = 0;
    	this.wins = 0;
    	this.losses = 0;
    	this.name = name;
    	this.objectsConsumed = 0;
    	this.arenaTypesMap = new HashMap<Integer, ArenaTypeStats>();
    	this.arenaSubTypesMap = new HashMap<Integer, ArenaSubTypeStats>();
    }
    
    public void createDefaultStats() {
    	this.level = 1;
    	this.experience = 0;
    	this.experienceRequired = 1500;
    }
    
    private int getGamesPlayed(OID playerOid) {
    	int gamesPlayed = 0;
	    Calendar cal = Calendar.getInstance();
	    cal.set(Calendar.HOUR_OF_DAY, 0);
	    cal.set(Calendar.MINUTE, 0);
	    long startOfDay = cal.getTimeInMillis();
	    Date d = new Date();
	    long currentTime = d.getTime();
	    LinkedList<Long> lastPlayed = (LinkedList) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "lastGames");
	    for (int k = 0; k < lastPlayed.size(); k++) {
	    	if (lastPlayed.get(k) > startOfDay) {
	    		gamesPlayed++;
	    	}
	    }
	    lastPlayed.addFirst(currentTime);
	    if (lastPlayed.size() > 3)
	    	lastPlayed.removeLast();
	    EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "lastGames", lastPlayed);
	    return gamesPlayed;
    }
    
    /**
     * Updates the players stats based on the performance in the arena battle.
     * @param kills
     * @param deaths
     */
    public void updateStats(int arenaType, int arenaSubType, int kills, int deaths, boolean wonArena, 
    		int expAwarded, int ratingAdjustment) {
    	totalKills += kills;
    	totalDeaths += deaths;
    	if (wonArena)
    		wins++;
    	else
    		losses++;
    	experience += expAwarded;
    	ExtendedCombatMessages.sendCombatText(oid, "+" + expAwarded + "xp", 17);
    	// Add games played for the day bonus
    	int gamesPlayed = getGamesPlayed(oid);
    	if (gamesPlayed == 0) {
    		int expBonus = 1000;
    		experience += expBonus;
    		ExtendedCombatMessages.sendCombatText(oid, "1st Daily Game: +" + expBonus + "xp", 17);
    	} else if (gamesPlayed == 1) {
    		int expBonus = 500;
    		experience += expBonus;
    		ExtendedCombatMessages.sendCombatText(oid, "2nd Daily Game: +" + expBonus + "xp", 17);
    	} else if (gamesPlayed == 2) {
    		int expBonus = 200;
    		experience += expBonus;
    		ExtendedCombatMessages.sendCombatText(oid, "3rd Daily Game: +" + expBonus + "xp", 17);
    	}
    	if (experience > experienceRequired)
    		levelUp();
    	ArenaDatabase aDB = new ArenaDatabase();
    	aDB.updateArenaStats(this);
    	sendArenaStatUpdate();
    	ArenaTypeStats typeStats = getArenaTypeStats(arenaType);
    	typeStats.updateStats(kills, deaths, wonArena, expAwarded, ratingAdjustment);
    	aDB.updateArenaTypeStats(oid, typeStats);
    	ArenaSubTypeStats subTypeStats = getArenaSubTypeStats(arenaType, arenaSubType);
    	subTypeStats.updateStats(kills, deaths, wonArena, expAwarded, ratingAdjustment);
    	aDB.updateArenaSubTypeStats(oid, subTypeStats);
    }
    
    public void alterExp(int delta) {
    	experience += delta;
    	ExtendedCombatMessages.sendCombatText(oid, "+" + delta + "xp", 17);
    	if (experience > experienceRequired)
    		levelUp();
    	ArenaDatabase aDB = new ArenaDatabase();
    	aDB.updateArenaStats(this);
    	sendArenaStatUpdate();
    }
    
    private void levelUp() {
    	ExtendedCombatMessages.sendCombatText(oid, "Level Up!", 17);
    	level++;
    	experience = experience - experienceRequired;
    	experienceRequired = level * 350 + experienceRequired;
    	if (experience > experienceRequired)
    		levelUp();
    }
    
    public void sendArenaStatUpdate() {
    	EnginePlugin.setObjectPropertyNoResponse(oid, CombatClient.NAMESPACE, "level", level);
        TargetedPropertyMessage tPropMsg = new TargetedPropertyMessage(oid, oid);
        tPropMsg.setProperty("arena_exp", experience);
        tPropMsg.setProperty("arena_exp_req", experienceRequired);
        Engine.getAgent().sendBroadcast(tPropMsg);
        EnginePlugin.setObjectPropertyNoResponse(oid, WorldManagerClient.NAMESPACE, "arena_exp", experience);
        EnginePlugin.setObjectPropertyNoResponse(oid, WorldManagerClient.NAMESPACE, "arena_exp_req", experienceRequired);
    }
    
    public ArenaTypeStats getArenaTypeStats(int arenaType) {
    	if (!arenaTypesMap.containsKey(arenaType)) {
    		ArenaDatabase aDB = new ArenaDatabase();
    		ArenaTypeStats typeStats = new ArenaTypeStats(arenaType);
    		typeStats = aDB.loadArenaTypeStats(oid, typeStats);
    		arenaTypesMap.put(arenaType, typeStats);
    	}
    	return arenaTypesMap.get(arenaType);
    }
    
    public ArenaSubTypeStats getArenaSubTypeStats(int arenaType, int arenaSubType) {
    	if (!arenaSubTypesMap.containsKey(arenaSubType)) {
    		ArenaDatabase aDB = new ArenaDatabase();
    		ArenaSubTypeStats typeStats = new ArenaSubTypeStats(arenaType, arenaSubType);
    		typeStats = aDB.loadArenaSubTypeStats(oid, typeStats);
    		arenaSubTypesMap.put(arenaType, typeStats);
    	}
    	return arenaSubTypesMap.get(arenaType);
    }
    
    public OID getOid() { return oid;}
    public void setOid(OID oid) {
    	this.oid = oid;
    }
    
    public String getName() { return name;}
    public void setName(String name) {
    	this.name = name;
    }
    
    public int getLevel() { return level;}
    public void setLevel(int level) {
    	this.level = level;
    }
    
    public int getExperience() { return experience;}
    public void setExperience(int experience) {
    	this.experience = experience;
    }
    
    public int getExperienceRequired() { return experienceRequired;}
    public void setExperienceRequired(int experienceRequired) {
    	this.experienceRequired = experienceRequired;
    }
    
    public int getWins() { return wins;}
    public void setWins(int wins) {
    	this.wins = wins;
    }
    
    public int getLosses() { return losses;}
    public void setLosses(int losses) {
    	this.losses = losses;
    }
    
    public int getTotalKills() { return totalKills;}
    public void setTotalKills(int totalKills) {
    	this.totalKills = totalKills;
    }
    
    public int getTotalDeaths() { return totalDeaths;}
    public void setTotalDeaths(int totalDeaths) {
    	this.totalDeaths = totalDeaths;
    }
    
    public int getObjectsConsumed() { return objectsConsumed;}
    public void setObjectsConsumed(int objectsConsumed) {
    	this.objectsConsumed = objectsConsumed;
    }
    
    public HashMap<Integer, ArenaTypeStats> getArenaTypesMap() { return arenaTypesMap;}
    public void setArenaTypesMap(HashMap<Integer, ArenaTypeStats> arenaTypesMap) {
    	this.arenaTypesMap = arenaTypesMap;
    }
    
    public HashMap<Integer, ArenaSubTypeStats> getArenaSubTypesMap() { return arenaSubTypesMap;}
    public void setArenaSubTypesMap(HashMap<Integer, ArenaSubTypeStats> arenaSubTypesMap) {
    	this.arenaSubTypesMap = arenaSubTypesMap;
    }
    
    public String toString() {
    	return "Arena Stats: character " + name + " has kills: " + totalKills + " deaths: " + totalDeaths;
    }
    
    public class ArenaTypeStats {
    	int arenaType;
        int wins;
        int losses;
        int rating;
        int kills;
        int deaths;
        int objectsConsumed;
        
        public ArenaTypeStats(int arenaType) {
        	this.arenaType = arenaType;
        	this.kills = 0;
        	this.deaths = 0;
        	this.wins = 0;
        	this.losses = 0;
        	this.rating = initialRating;
        }

		public void updateStats(int kills, int deaths, boolean wonArena, int expAwarded, int ratingAdjustment) {
        	this.kills += kills;
        	this.deaths += deaths;
        	if (wonArena)
        		wins++;
        	else
        		losses++;
        	rating += ratingAdjustment;
        }
		
		public int getArenaType() { return arenaType;}
	    public void setArenaType(int arenaType) {
	    	this.arenaType = arenaType;
	    }
		
		public int getWins() { return wins;}
	    public void setWins(int wins) {
	    	this.wins = wins;
	    }
	    
	    public int getLosses() { return losses;}
	    public void setLosses(int losses) {
	    	this.losses = losses;
	    }
        
        public int getRating() { return rating;}
        public void setRating(int rating) {
        	this.rating = rating;
        }
        
        public int getKills() { return kills;}
        public void setKills(int kills) {
        	this.kills = kills;
        }
        
        public int getDeaths() { return deaths;}
        public void setDeaths(int deaths) {
        	this.deaths = deaths;
        }
    }
    
    public class ArenaSubTypeStats {
    	int arenaType;
    	int arenaSubType;
        int wins;
        int losses;
        int rating;
        int kills;
        int deaths;
        int objectsConsumed;
        
        public ArenaSubTypeStats(int arenaType, int arenaSubType) {
        	this.arenaType = arenaType;
        	this.arenaSubType = arenaSubType;
        	this.kills = 0;
        	this.deaths = 0;
        	this.wins = 0;
        	this.losses = 0;
        	this.rating = initialRating;
        }

		public void updateStats(int kills, int deaths, boolean wonArena, int expAwarded, int ratingAdjustment) {
        	this.kills += kills;
        	this.deaths += deaths;
        	if (wonArena)
        		wins++;
        	else
        		losses++;
        	rating += ratingAdjustment;
        }
		
		public int getArenaType() { return arenaType;}
	    public void setArenaType(int arenaType) {
	    	this.arenaType = arenaType;
	    }
	    
	    public int getArenaSubType() { return arenaSubType;}
	    public void setArenaSubType(int arenaSubType) {
	    	this.arenaSubType = arenaSubType;
	    }
		
		public int getWins() { return wins;}
	    public void setWins(int wins) {
	    	this.wins = wins;
	    }
	    
	    public int getLosses() { return losses;}
	    public void setLosses(int losses) {
	    	this.losses = losses;
	    }
        
        public int getRating() { return rating;}
        public void setRating(int rating) {
        	this.rating = rating;
        }
        
        public int getKills() { return kills;}
        public void setKills(int kills) {
        	this.kills = kills;
        }
        
        public int getDeaths() { return deaths;}
        public void setDeaths(int deaths) {
        	this.deaths = deaths;
        }
    }
    
    public static HashMap<OID, Integer> CalculateRatings(int arenaType, int arenaSubType, ArenaTeam[] teams, int winningTeam, 
    		HashMap<Integer, Integer> victoryPayments, HashMap<Integer, Integer> defeatPayments,
    		int victoryExp, int defeatExp) {
    	HashMap<OID, Integer> ratingAdjustments = new HashMap<OID, Integer>();
    	int numTeams = teams.length;
    	//ArrayList<Integer> teamRatings[] = new ArrayList[numTeams];
		int avgRatings[] = new int[numTeams];
		Log.debug("ARENA: rating calcs 1");
		
		for (int i = 0; i < numTeams; i++) {
			//ArrayList<Integer> cumulativeRatings = new ArrayList<Integer>();
			int avgRating = 0;
			for (ArenaMember member : teams[i].getTeamMembers()) {
				ArenaStats stats = ArenaPlugin.getPlayerArenaStats(member.getOid());
				if (stats!=null) {
		    	ArenaTypeStats typeStats = stats.getArenaTypeStats(arenaType);
		    	//cumulativeRatings.add(stats.getRating());
			    avgRating += typeStats.getRating();
			    }
		    }
		    //teamRatings[i] = cumulativeRatings;
		    avgRatings[i] = avgRating / teams[i].getTeamSize();
		}
		Log.debug("ARENA: rating calcs 2");
		//avgRatings = avgRatings / (numTeams - 1);
		int losersRating = 0;
		for (int i = 0; i < avgRatings.length; i++) {
			if (i != winningTeam) 
				losersRating += avgRatings[i];
		}
		losersRating = losersRating / (avgRatings.length - 1);
		int ratingDif = 0;
		if (winningTeam != -1) {
			ratingDif = losersRating - avgRatings[winningTeam];
		}
		// For every 25 points difference the adjustment is increased by 1
		// If the winning team is greatly above the losing team the adjustment will be less
		int ratingAdjustment = (ratingDif / 25) + 15;
		// TODO: improve the handling of draws for rating adjustments
		if (winningTeam == -1) {
			ratingAdjustment = 0;
		}
		
		Log.debug("ARENA: rating calcs 3 with arena adjustment: " + ratingAdjustment);
		// Now dish out rewards
		for (int i = 0; i < numTeams; i++) {
			for (ArenaMember member : teams[i].getTeamMembers()) {
    			OID oid = member.getOid();
			    int expRewarded = 0;
			    boolean wonArena = false;
			    int ratingDelta = 0;
			    if (i == winningTeam) {
			    	for (int currency : victoryPayments.keySet()) {
			    		AgisInventoryClient.alterCurrency(oid, currency, victoryPayments.get(currency));
			    		String message = "You have received " + victoryPayments.get(currency) + " Coins";
			    		ChatClient.sendObjChatMsg(oid, 2, message);
			    	}
			    	// Give exp to winning players
			    	expRewarded = victoryExp;
			    	wonArena = true;
			    	ratingDelta = ratingAdjustment;
			    	ratingAdjustments.put(oid, ratingDelta);
				} else {
					for (int currency : defeatPayments.keySet()) {
						AgisInventoryClient.alterCurrency(oid, currency, defeatPayments.get(currency));
						String message = "You have received " + defeatPayments.get(currency) + " Coins";
						ChatClient.sendObjChatMsg(oid, 2, message);
			    	}
					expRewarded = defeatExp;
					ratingDelta = -ratingAdjustment;
					ratingAdjustments.put(oid, ratingDelta);
				}
			    ArenaPlugin.updateArenaStats(arenaType, arenaSubType, oid, member.getKills(), member.getDeaths(), wonArena, 
			    		expRewarded, ratingDelta);
		    }
		}
		Log.debug("ARENA: rating calcs 4");
		return ratingAdjustments;
    }

    protected static final int initialRating = 5000;
    private static final long serialVersionUID = 1L;
}
