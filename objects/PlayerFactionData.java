package atavism.agis.objects;

import java.io.*;
import atavism.server.util.*;

/**
 * The FactionData class contains information about how a faction reacts to players/npcs of other factions.
 * @author Andrew Harrison
 *
 */
public class PlayerFactionData implements Serializable {
	protected int faction = -1;
	protected String name;
	protected int reputation = 0;
	protected boolean atWar = false;
	protected String group = null;
	protected int category = 0;
	
	public PlayerFactionData() {}
	
	/**
	 * Constructor
	 * @param name: The name of the faction
	 * @param reputation: the current level of reputation
	 * @param category: What category this faction belongs to.
	 */
	public PlayerFactionData(int faction, String name, int reputation, String group, int category) {
		this.faction = faction;
		this.name = name;
		this.reputation = reputation;
		this.group = group;
		this.category = category;
		Log.debug("FACTION: Player faction data created for faction " + name);
	}
	
	/**
	 * Alters the reputation by the amount given.
	 * @param delta
	 */
	public void updateReputation(int delta) {
		reputation += delta;
	}
	
	public int getFaction() { return faction; }
	public void setFaction(int faction) { this.faction = faction; }
	
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	
	public int getReputation() { return reputation; }
	public void setReputation(int reputation) { this.reputation = reputation; }
	
	public boolean getAtWar() { return atWar; }
	public void setAtWar(boolean atWar) { this.atWar = atWar; }
	
	public String getGroup() { return group; }
	public void setGroup(String group) { this.group = group; }
	
	public int getCategory() { return category; }
	public void setCategory(int category) { this.category = category; }
	
	private static final long serialVersionUID = 1L;
}