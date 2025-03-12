package atavism.agis.objects;

import java.io.*;
import java.util.HashMap;
import java.util.Random;

import atavism.server.util.*;


public class ResourceNodeProfile implements Serializable {
	protected int id = -1;
	protected String name;
	protected double spawn_percentage = 0;
	protected double spawn_pecentage_max = 0;
	//protected HashMap<Integer, Integer> priority = new HashMap<Integer, Integer>();
	//protected HashMap<Integer, Integer> priorityMax = new HashMap<Integer, Integer>();

	protected HashMap<Integer, ResourceNodeProfileSettings> settings = new HashMap<Integer, ResourceNodeProfileSettings>();
	protected float distance = 1;
	
	// ArrayList<Integer> settingChances = new ArrayList<Integer>();

	public ResourceNodeProfile() {}
	
	/**
	 * Constructor
	 * @param name: The name of the faction
	 * @param reputation: the current level of reputation
	 * @param category: What category this faction belongs to.
	 */
	public ResourceNodeProfile(int id, String name, float spawn_percentage, float spawn_pecentage_max) {
		this.id = id;
		this.name = name;
		this.spawn_percentage = spawn_percentage;
		this.spawn_pecentage_max = spawn_pecentage_max;
		
		Log.debug("FACTION: Player faction data created for faction " + name);
	}
	
	
	public int getId() { return id; }
	public void setId(int id) { this.id = id; }
	
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	
	public double getSpawnPecentage() { return spawn_percentage; }
	public void setSpawnPecentage(double spawn_percentage) { this.spawn_percentage = spawn_percentage; }
	
	public double getSpawnPecentageMax() { return spawn_pecentage_max; }
	public void setSpawnPecentageMax(double spawn_pecentage_max) { this.spawn_pecentage_max = spawn_pecentage_max; }
	
	public float getDistance() { return distance; }
	public void setDistance(float distance) { this.distance = distance; }
	
	public int settingsCount() {
		return settings.size();
	}

	public int getPriority(int id) {
		Random rand = new Random();
		if(settings.get(id).priorityMax>settings.get(id).priority)
			return settings.get(id).priority + rand.nextInt(settings.get(id).priorityMax - settings.get(id).priority);
		return settings.get(id).priority;
	}

	public ResourceNodeProfileSettings getSetting(int settingId) {
		return settings.get(settingId);
	}

	public HashMap<Integer, ResourceNodeProfileSettings> getSettings() {
		return settings;
	}
	
	/*public int getTotalRollChance() {
		int totalRollChance = 0;
		for (int i = 0; i < settingChances.size(); i++) {
			totalRollChance += settingChances.get(i);
		}
		return totalRollChance;
	}

	public int getRandomSettingNum() {
		Random rand = new Random();
		int roll = rand.nextInt(getTotalRollChance());
		int currentTotal = 0;
		for (int i = 0; i < settingChances.size(); i++) {
			currentTotal += settingChances.get(i);
			Log.debug("ResourceNodeProfile: currentTotal for Chance: " + i + " is: " + currentTotal);
			if (currentTotal >= roll)
				return i;
		}
		return -1;
	}*/

	private static final long serialVersionUID = 1L;
}