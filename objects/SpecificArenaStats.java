package atavism.agis.objects;

import java.util.*;
import java.io.*;
import atavism.server.engine.OID;

public class SpecificArenaStats implements Serializable {
    public SpecificArenaStats() {
    }
    
    public SpecificArenaStats(OID oid, String name, int startingRating) {
    	this.oid = oid;
    	//this.totalKills = 0;
    	//this.totalDeaths = 0;
    	//this.killsByDay;
    	//this.deathsByDay;
    	this.name = name;
    	this.rating = startingRating;
    	this.bestRating = startingRating;
    }
    
    /**
     * Increases the totalKills and the current days kills by 1.
     */
    public void addKill() {
    	totalKills++;
    }
    
    /**
     * Increases the totalDeaths and the current days kills by 1.
     */
    public void addDeath() {
    	totalDeaths++;
    }
    
    /**
     * Updates the players stats based on the performance in the arena battle.
     * @param newRating
     * @param kills
     * @param deaths
     */
    public void updateStats(int newRating, int newBest, int kills, int deaths) {
    	rating = newRating;
    	//if (rating > bestRating)
    	//	bestRating = rating;
    	bestRating = newBest;
    	totalKills += kills;
    	totalDeaths += deaths;
    }
    
    public OID getOid() { return oid;}
    public void setOid(OID oid) {
    	this.oid = oid;
    }
    
    public int getTotalKills() { return totalKills;}
    public void setTotalKills(int totalKills) {
    	this.totalKills = totalKills;
    }
    
    public int getRating() { return rating;}
    public void setRating(int rating) {
    	this.rating = rating;
    }
    
    public int getBestRating() { return bestRating;}
    public void setBestRating(int bestRating) {
    	this.bestRating = bestRating;
    }
    
    public String getName() { return name;}
    public void setName(String name) {
    	this.name = name;
    }
    
    public int getTotalDeaths() { return totalDeaths;}
    public void setTotalDeaths(int totalDeaths) {
    	this.totalDeaths = totalDeaths;
    }
    
    public HashMap<String, Integer> getKillsByDay() { return killsByDay;}
    public void setKillsByDay(HashMap<String, Integer> killsByDay) {
    	this.killsByDay = killsByDay;
    }
    
    public HashMap<String, Integer> getDeathsByDay() { return deathsByDay;}
    public void setDeathsByDay(HashMap<String, Integer> deathsByDay) {
    	this.deathsByDay = deathsByDay;
    }
    
    public String toString() {
    	return "Arena Stats: character " + name + " has rating: " + rating + "; kills: " + totalKills + " deaths: " + totalDeaths;
    }

    OID oid;
    String name;
    int totalKills = 0;
    int totalDeaths = 0;
    int rating;
    int bestRating;
    HashMap<String, Integer> killsByDay = new HashMap<String, Integer>();
    HashMap<String, Integer> deathsByDay = new HashMap<String, Integer>();

    private static final long serialVersionUID = 1L;
}
