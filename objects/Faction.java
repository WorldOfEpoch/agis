package atavism.agis.objects;

import java.io.*;
import java.util.*;

import atavism.agis.plugins.FactionPlugin;
import atavism.server.engine.*;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.*;

/*
 * Current value sets:
 * -2000 - -3999: Hated (-2)
 * -1000 - -1999 Disliked (-1)
 * -999 - 0: Neutral (1)
 * 0-999: Friendly (2)
 * 1000-2999: Honoured (3)
 */

/**
 * The Faction class contains information about how a faction reacts to players/npcs of other factions.
 * @author Andrew Harrison
 *
 */
public class Faction implements Serializable {
	protected int id = -1;
	protected String name = null;
	protected String group = null;
	protected int category = 0;
	protected boolean isPublic = false;
	protected int defaultStance;
	HashMap<Integer, Integer> defaultStances = new HashMap<Integer, Integer>();
	
	public Faction(int id, String name, String group, int category) {
		setID(id);
		setName(name);
		setGroup(group);
		setCategory(category);
		Log.debug("FACTION: creating faction: " + name);
	}
	
	public int getDefaultReputation(int factionID) {
		int stance = FactionPlugin.Neutral;
		if (defaultStances.containsKey(factionID)) {
			stance =  defaultStances.get(factionID);
		} else {
			stance = defaultStance;
		}
		
		if (stance == FactionPlugin.Hated) {
			return FactionPlugin.HatedRep;
		} else if (stance == FactionPlugin.Disliked) {
			return FactionPlugin.DislikedRep;
		} else if (stance == FactionPlugin.Neutral) {
			return FactionPlugin.NeutralRep;
		} else if (stance == FactionPlugin.Friendly) {
			return FactionPlugin.FriendlyRep;
		} else if (stance == FactionPlugin.Honoured) {
			return FactionPlugin.HonouredRep;
		} else if (stance == FactionPlugin.Exalted) {
			return FactionPlugin.ExaltedRep;
		} else {
			return FactionPlugin.NeutralRep;
		}
	}
	
	/**
	 * 
	 * @param targetOid: The oid of the player
	 * @param oid: The oid of the mob whose faction data we are getting
	 * @param faction
	 */
	public static PlayerFactionData addFactionToPlayer(OID targetOid, Faction faction, int playerFaction, HashMap<Integer, PlayerFactionData> pfdMap) {
		if (pfdMap == null) {
			Log.error("FACTION: pfdMap is null in addFactionToPlayer with player " + targetOid);
			return null;
		} else if (faction == null) {
			//Log.error("FACTION: faction is null in addFactionToPlayer with player " + targetOid);
			return null;
		}
		// Better make sure the faction isn't already added
		if (pfdMap.containsKey(faction.getName())) {
			Log.error("FACTION: tried adding faction " + faction.getName() + " to player " + targetOid + " but player already has it");
			return pfdMap.get(faction.getName());
		}
		
		int reputation = faction.getDefaultReputation(playerFaction);
		PlayerFactionData newFactionData = new PlayerFactionData(faction.getID(), faction.getName(), reputation, 
				faction.getGroup(), faction.getCategory());
		return newFactionData;
	}
	
	public static void sendFactionData(OID oid) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "reputations");
		// Check for type and fill props here

		TargetedExtensionMessage msg = new TargetedExtensionMessage(
				WorldManagerClient.MSG_TYPE_EXTENSION, oid, 
				oid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}
	
	public int getID() { return id; }
    public void setID(int id) { this.id = id; }
    
    public int getCategory() { return category; }
	public void setCategory(int category) { this.category = category; }
	
	public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getGroup() { return group; }
	public void setGroup(String group) { this.group = group; }
	
	public boolean getIsPublic() { return isPublic; }
	public void setIsPublic(boolean isPublic) { this.isPublic = isPublic; }
	
	public int getDefaultStance() { return defaultStance; }
	public void setDefaultStance(int defaultStance) { this.defaultStance = defaultStance; }
	
	public HashMap<Integer, Integer> getDefaultStances() { return defaultStances; }
	public void setDefaultStances(HashMap<Integer, Integer> defaultStances) { this.defaultStances = defaultStances; }
	
	private static final long serialVersionUID = 1L;
}
