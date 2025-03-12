package atavism.agis.objects;

import atavism.agis.plugins.CombatPlugin;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * The ClaimProfile class stores all the information needed about a limits
 * building object.
 * 
 */
public class StatProfile implements Serializable {
	protected int id;
	protected String name;
	protected HashMap<Integer, Integer> stats = new HashMap<Integer, Integer>();
	protected HashMap<Integer, Float> statsLevelIncrease = new HashMap<Integer, Float>();
	protected HashMap<Integer, Float> statsLevelPercentIncrease = new HashMap<Integer, Float>();
	protected Set<String> statsSendToClient = new HashSet<String>();
	protected Set<String> statsNotSendToClient = new HashSet<String>();
	protected Set<String> statsToDelete = new HashSet<String>();

	public StatProfile() {

	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public HashMap<Integer, Integer> getStats() {
		return stats;
	}

	public void setStats(HashMap<Integer, Integer> stats) {
		this.stats = stats;
	}

	public void addStats(Integer stat, Integer value) {
		this.stats.put(stat, value);
	}

	public void setStatsSendToClient(Set<String> statsSendToClient) {
		this.statsSendToClient = statsSendToClient;
	}

	public void addStatsSendToClient(String stat) {
		this.statsSendToClient.add(stat) ;
	}
	public Set<String> getStatsSendToClient() {
		return statsSendToClient;
	}

	public void setStatsNotSendToClient(Set<String> statsNotSendToClient) {
		this.statsNotSendToClient = statsNotSendToClient;
	}

	public void addStatsNotSendToClient(String stat) {
		this.statsNotSendToClient.add(stat) ;
	}
	public Set<String> getStatsNotSendToClient() {

		return statsNotSendToClient;
	}


	public void setStatsLevelIncrease(HashMap<Integer, Float> statsLevelIncrease) {
		this.statsLevelIncrease = statsLevelIncrease;
	}

	public HashMap<Integer, Float> getStatsLevelIncrease() {
		return statsLevelIncrease;
	}

	public void setStatsLevelPercentIncrease(HashMap<Integer, Float> statsLevelPercentIncrease) {
		this.statsLevelPercentIncrease = statsLevelPercentIncrease;
	}

	public HashMap<Integer, Float> getStatsLevelPercentIncrease() {
		return statsLevelPercentIncrease;
	}

	public void setStatsToDelete(Set<String> statsToDelete) {
		this.statsToDelete = statsToDelete;
	}

	public void addStatsToDelete(String stat) {
		this.statsToDelete.add(stat) ;
	}
	public Set<String> getStatsToDelete() {

		return statsToDelete;
	}


	private static final long serialVersionUID = 1L;
}

