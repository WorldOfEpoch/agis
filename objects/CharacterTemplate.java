package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import atavism.agis.core.Cooldown;
import atavism.agis.plugins.*;
import atavism.agis.server.combat.DmgBaseStat;
import atavism.agis.server.combat.ExperienceStat;
import atavism.agis.util.RequirementChecker;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.objects.DisplayContext;
import atavism.server.objects.InstanceRestorePoint;
import atavism.server.objects.ObjectTypes;
import atavism.server.objects.Template;
import atavism.server.plugins.*;
import atavism.server.plugins.InstanceClient.InstanceInfo;
import atavism.server.util.Log;
import atavism.server.worldmgr.CharacterFactory;

public class CharacterTemplate extends CharacterFactory {

	int aspect;
	int race;
	HashMap<String, CharacterStatProgression> startingStats = new HashMap<String, CharacterStatProgression>();
	public ArrayList<Integer> startingSkills;
	int faction = 1;
	int instanceTemplateID;
	String portalName;
	Point spawnPoint;
	double spawnRotation = 0D;
	int respawninstanceTemplateID;
	Point respawnPoint;
	int startingLevel = 1;
	int autoAttack = -1;
	int sprint = -1;
	int dodge = -1;
	int xpProfile = -1;

	int statProfileId = -1;
	public String race_description = "";
	public String class_description = "";
	public String race_icon = "";
	public String race_icon2 = "";
	public String class_icon = "";
	public String class_icon2 = "";

	public HashMap<Integer,String> gender = new HashMap<Integer,String>();
	public HashMap<Integer,String> genderIcon = new HashMap<Integer,String>();
	public HashMap<Integer,String> genderIconPath = new HashMap<Integer,String>();
	ArrayList<CharacterStartingItem> items = new ArrayList<CharacterStartingItem>();


	@Override
	public OID createCharacter(String worldName, OID atavismID, Map properties) {
		HashMap<String, Serializable> props = (HashMap) properties;

		// First verify name is suitable and not taken
		String name = (String) props.get("characterName");
		String errorMsg = checkName(name);
		if (checkName(name) != null) {
			properties.put("errorMessage", errorMsg);
			return null;
		}

		// Name is ok, create template
		//Template player = new Template("DefaultPlayer", -1, ObjectManagerPlugin.MOB_TEMPLATE);
		//Log.error("createCharacter: name="+name);
		Template player = new Template(name, -1, ObjectManagerPlugin.MOB_TEMPLATE);

		// TODO: remove display context from the server
		String meshName = (String) props.get("prefab");
		DisplayContext dc = new DisplayContext(meshName, true);
		dc.addSubmesh(new DisplayContext.Submesh("", ""));

		player.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
		player.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_OBJECT_TYPE, ObjectTypes.player);
		player.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_PERCEPTION_RADIUS, AgisLoginPlugin.PLAYER_PERCEPTION_RADIUS);

		// General properties such as instance, race,
		OID instanceOid = InstanceClient.getInstanceOid(instanceTemplateID);
		Log.debug("POP: getting population for instance: " + instanceTemplateID + " and Oid:" + instanceOid);

		// Verify there is space in the specified instance
		InstanceInfo instanceInfo = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_ALL_INFO);
		if (instanceInfo.populationLimit != -1 && instanceInfo.playerPopulation >= instanceInfo.populationLimit) {
			Log.debug("POP: got population: " + instanceInfo.playerPopulation + " and limit: " + instanceInfo.populationLimit);
			instanceOid = ProxyPlugin.handleFullInstance(instanceInfo.templateID, instanceInfo);
			instanceInfo = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_ALL_INFO);
		}

		if (instanceOid == null) {
			Log.error("SampleFactory: no 'default' instance");
			properties.put("errorMessage", "No default instance");
			return null;
		}
		Log.debug("SPAWN: spawn marker name=" + portalName);

		player.put(CombatClient.NAMESPACE, "xpProfile", getExpProfile());
		//Marker spawnMarker = InstanceClient.getMarker(instanceOid, portalName);
		player.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_NAME, name);
		player.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_INSTANCE, instanceOid);
		player.put(CombatClient.NAMESPACE, WorldManagerClient.TEMPL_INSTANCE, instanceOid);
		;
		player.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_LOC, spawnPoint);
		player.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_ORIENT, Quaternion.fromAngleAxisDegrees(spawnRotation, new AOVector(0, 1, 0)));
		player.put(WorldManagerClient.NAMESPACE, "accountId", atavismID);
		player.put(WorldManagerClient.NAMESPACE, "model", meshName);
		player.put(WorldManagerClient.NAMESPACE, "race", race);
		player.put(CombatClient.NAMESPACE, "gender", props.get("gender"));
		//  player.put(WorldManagerClient.NAMESPACE, "genderId", props.get("genderId"));
		player.put(CombatClient.NAMESPACE, "genderId", props.get("genderId"));
		player.put(WorldManagerClient.NAMESPACE, "charactername", name);
		player.put(WorldManagerClient.NAMESPACE, "world", instanceTemplateID);
		player.put(WorldManagerClient.NAMESPACE, "category", 1);
		player.put(WorldManagerClient.NAMESPACE, "zone", "");
		player.put(WorldManagerClient.NAMESPACE, "guild", 0);

		player.put(WorldManagerClient.NAMESPACE, "hearthLoc", spawnPoint);
		player.put(WorldManagerClient.NAMESPACE, "hearthInstance", instanceTemplateID);
		AOVector scale = new AOVector(1, 1, 1);
		player.put(WorldManagerClient.NAMESPACE, "scaleFactor", scale);
		player.put(WorldManagerClient.NAMESPACE, "walk_speed", 3);
		player.put(WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_STATE, AgisWorldManagerPlugin.MOVEMENT_STATE_RUNNING);
		player.put(WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, 6);

		// TODO: Read in faction data from the db
		HashMap factionData = new HashMap();
		player.put(Namespace.FACTION, "factionData", factionData);
		player.put(Namespace.FACTION, FactionStateInfo.FACTION_PROP, faction);

		//Starting inventory
		String startingItems = "";
		for (CharacterStartingItem item : items) {
			Log.debug("ITEM: adding item: " + item.itemID + " to character");
			if (item.equipped) {
				startingItems = startingItems + "*" + item.itemID + "|1;";
			} else {
				//for (int i = 0; i < item.count; i++) {
				startingItems = startingItems + "" + item.itemID + "|" + item.count + ";";
				//}
			}
			Log.debug("ITEM: starting items is now: " + startingItems);
		}
		if (startingItems.endsWith(";"))
			startingItems = startingItems.substring(0, startingItems.length() - 1);
		Log.debug("ITEM: character is starting with items: " + startingItems);
		player.put(InventoryClient.NAMESPACE, InventoryClient.TEMPL_ITEMS, startingItems);
		player.put(InventoryClient.NAMESPACE, "isPlayer", true);

		// Other Properties
		player.put(WorldManagerClient.NAMESPACE, "busy", false);
		player.put(Namespace.QUEST, ":currentQuests", "");
		player.put(SocialClient.NAMESPACE, ":channels", "");
		player.put(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT, true);

		InstanceRestorePoint restorePoint = new InstanceRestorePoint(instanceTemplateID, spawnPoint);
		restorePoint.setFallbackFlag(true);
		LinkedList restoreStack = new LinkedList();
		restoreStack.add(restorePoint);
		player.put(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_INSTANCE_RESTORE_STACK, restoreStack);
		player.put(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_CURRENT_INSTANCE_NAME, instanceTemplateID);

		// Combat Properties/stats
		player.put(CombatClient.NAMESPACE, "aspect", aspect);
		player.put(InventoryClient.NAMESPACE, ":aspect", aspect);
		player.put(CombatClient.NAMESPACE, "aspectName", RequirementChecker.getClass(aspect));
		player.put(CombatClient.NAMESPACE, ":race", race);
		player.put(InventoryClient.NAMESPACE, ":race", race);
		player.put(CombatClient.NAMESPACE, "race", race);
		player.put(CombatClient.NAMESPACE, "raceName", RequirementChecker.getRace(race));
		player.put(CombatClient.NAMESPACE, "attackable", true);
		player.put(CombatClient.NAMESPACE, "attackType", "crush");
		player.put(CombatClient.NAMESPACE, "weaponType", new ArrayList<String>());

		player.put(CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_USERFLAG, true);
		player.put(CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE, false);
		player.put(CombatClient.NAMESPACE, CombatPlugin.PROP_HITBOX, 1);
		// Stats from the database

		Log.error(race+" "+aspect+" :statProfile"+ statProfileId);

		player.put(CombatClient.NAMESPACE, ":statProfile", statProfileId);

		if (statProfileId != 0) {
			StatProfile sp = CombatPlugin.STAT_PROFILES.get(statProfileId);
			if(sp!=null){
				for(Map.Entry<Integer,Integer> e : sp.getStats().entrySet()){
					if (Log.loggingDebug)
						Log.debug("getStartingStats: StatProfile Key="+e.getKey()+" name=" + CombatPlugin.STAT_NAMES.get(e.getKey()) + " value: " + e.getValue());
					String stat = CombatPlugin.STAT_NAMES.get(e.getKey());

					if(!stat.equals(CombatPlugin.EXPERIENCE_MAX_STAT)) {
						AgisStatDef statDef = CombatPlugin.statDefMap.get(stat);
						if(!(statDef instanceof DmgBaseStat) && !(statDef instanceof ExperienceStat)) {
							int value = e.getValue();
							player.put(CombatClient.NAMESPACE, stat, new AgisStat(statDef.getId(), stat, value));
							if (stat.equals(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED)) {
								player.put(WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, value);
							}
						} else {
							if (Log.loggingDebug)
								Log.debug("getStartingStats: StatProfile Key="+e.getKey()+" name=" + CombatPlugin.STAT_NAMES.get(e.getKey()) + " skip " );
						}
					} else {
						if (Log.loggingDebug)
							Log.debug("getStartingStats: StatProfile Key="+e.getKey()+" name=" + CombatPlugin.STAT_NAMES.get(e.getKey()) + " skip " );
					}
				}
			}
		} else {
			for (String stat : startingStats.keySet()) {
				if (Log.loggingDebug)
					Log.debug("getStartingStats:  startingStats name=" + stat);
				int value = startingStats.get(stat).baseValue;
				AgisStatDef asd = CombatPlugin.lookupStatDef(stat);
				if (!(asd instanceof DmgBaseStat) && !(asd instanceof ExperienceStat)) {
					player.put(CombatClient.NAMESPACE, stat, new AgisStat(asd.getId(), stat, value));
					if (stat.equals(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED)) {
						player.put(WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, value);
					}
				} else {
					if (Log.loggingDebug)
						Log.debug("getStartingStats: startingStats name=" + stat + " skip ");
				}
			}
		}

		player.put(CombatClient.NAMESPACE, ":minLevel", startingLevel);
		player.put(CombatClient.NAMESPACE, ":maxLevel", startingLevel);

		player.put(CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_AUTOATTACK_ABILITY, autoAttack);
		//player.put(CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_AUTOATTACK_BASE, autoAttack);

		// Respawn location
		player.put(CombatClient.NAMESPACE, ":respawnInstance", respawninstanceTemplateID);
		player.put(CombatClient.NAMESPACE, ":respawnPoint", respawnPoint);

		// Add skill list
		player.put(CombatClient.NAMESPACE, ":startingSkills", startingSkills);

		LinkedList effectsList = new LinkedList();
		player.put(CombatClient.NAMESPACE, "effects", effectsList);

		LinkedList<Cooldown> cooldownList = new LinkedList<Cooldown>();
		player.put(CombatClient.NAMESPACE, "cooldowns", cooldownList);

		// Custom properties
		//HashMap<String, Serializable> customProps = (HashMap)properties.get("customProperties");
		HashMap<String, HashMap<String, Serializable>> customPropMapping = new HashMap<String, HashMap<String, Serializable>>();
		for (String prop : props.keySet()) {
			if (prop.startsWith("custom:")) {
				String propName = prop.substring(7);
				Serializable propValue = props.get(prop);
				Log.debug("CUSTOM: got custom property: " + propName + " with value: " + propValue);
				// Check for extra layer mapping
				if (propName.contains(":")) {
					String[] mapNames = propName.split(":");
					String mapName = mapNames[0];
					Log.debug("CUSTOM: got custom mapping: " + mapName + " with property: " + mapNames[1]);
					if (customPropMapping.containsKey(mapName)) {
						HashMap<String, Serializable> mapProps = customPropMapping.get(mapName);
						if (propValue instanceof Double) {
							mapProps.put(mapNames[1], (Float)propValue);
						} else {
							mapProps.put(mapNames[1], propValue);
						}
						customPropMapping.put(mapName, mapProps);
					} else {
						HashMap<String, Serializable> mapProps = new HashMap<String, Serializable>();
						if (propValue instanceof Double) {
							mapProps.put(mapNames[1], (Float)propValue);
						} else {
							mapProps.put(mapNames[1], propValue);
						}
						customPropMapping.put(mapName, mapProps);
					}
				} else {
					if (propValue instanceof Double) {
						player.put(WorldManagerClient.NAMESPACE, propName, (Float)propValue);
					} else {
						player.put(WorldManagerClient.NAMESPACE, propName, propValue);
					}
					Log.debug("CUSTOM: added custom property: " + propName + " with value: " + propValue);
				}
			}
		}

		// Handle any custom mappings
		for (String mapName : customPropMapping.keySet()) {
			player.put(WorldManagerClient.NAMESPACE, mapName, customPropMapping.get(mapName));
			Log.debug("CUSTOM: added custom mapping: " + mapName + " with numProps: " + customPropMapping.get(mapName).size());
		}
		//Log.error("createCharacter player=" +player);
		// generate the object
		OID objOid = ObjectManagerClient.generateObject(-1, ObjectManagerPlugin.MOB_TEMPLATE, player);
		Log.debug("SampleFactory: generated obj oid=" + objOid);
		return objOid;
	}

	public String checkName (String name) {
		// check to see that the name is valid
		if (name == null || name.equals("")) {
			return "Invalid name";
		}
		if (name.length() > AgisLoginPlugin.CHARACTER_NAME_MAX_LENGTH) {
			return "Your characters name must contain less than " + (AgisLoginPlugin.CHARACTER_NAME_MAX_LENGTH+1) + " characters";
		}
		if (name.length() < AgisLoginPlugin.CHARACTER_NAME_MIN_LENGTH) {
			return "Your characters name must contain more than " + (AgisLoginPlugin.CHARACTER_NAME_MIN_LENGTH-1) + " characters";
		}
		if(name.codePoints().anyMatch(
				codepoint ->
						Character.UnicodeScript.of(codepoint) == Character.UnicodeScript.HAN)) {
			return null;
		}
		if(name.codePoints().anyMatch(
				codepoint ->
						Character.UnicodeScript.of(codepoint) == Character.UnicodeScript.ARABIC)) {
			return null;
		}
		if (AgisLoginPlugin.CHARACTER_NAME_ALLOW_SPACES) {
			if (AgisLoginPlugin.CHARACTER_NAME_ALLOW_NUMBERS) {
				if (!name.matches("[a-zA-Z0-9 ]+")) {
					return "Your characters name can only contain letters, numbers and spaces";
				}
			} else {
				if (!name.matches("[a-zA-Z ]+")) {
					return "Your characters name can only contain letters and spaces";
				}
			}
		} else {
			if (AgisLoginPlugin.CHARACTER_NAME_ALLOW_NUMBERS) {
				if (!name.matches("[a-zA-Z0-9]+")) {
					return "Your characters name can only contain letters and numbers";
				}
			} else {
				if (!name.matches("[a-zA-Z]+")) {
					return "Your characters name can only contain letters";
				}
			}
		}

		return null;
	}

	public void setAspect(int aspect) {
		this.aspect = aspect;
	}
	public int getAspect() {
		return aspect;
	}

	public void setRace(int race) {
		this.race = race;
	}
	public int getRace() {
		return race;
	}

	public void setFaction(int faction) {
		this.faction = faction;
	}

	public void setInstanceTemplateID(int instanceTemplateID) {
		this.instanceTemplateID = instanceTemplateID;
	}

	public void setPortalName(String portalName) {
		this.portalName = portalName;
	}

	public void setSpawnPoint(Point spawnPoint) {
		this.spawnPoint = spawnPoint;
	}

	public void setSpawnRotation(double rotation) {
		this.spawnRotation = rotation;
	}

	public void setRespawnInstanceTemplateID(int instanceTemplateID) {
		this.respawninstanceTemplateID = instanceTemplateID;
	}

	public void setReSpawnPoint(Point spawnPoint) {
		this.respawnPoint = spawnPoint;
	}

	public void setStartingLevel(int level) {
		this.startingLevel = level;
	}

	public void setAutoAttack(int autoAttackAbility) {
		this.autoAttack = autoAttackAbility;
	}

	public int getAutoAttack() {
		return autoAttack;
	}

	public void setSprint(int ability) {
		sprint = ability;
	}

	public int getSprint() {
		return sprint;
	}
	public void setDodge(int ability) {	dodge = ability; }

	public int getDodge() {	return dodge;}

	public void setExpProfile(int xpProfile) {
		this.xpProfile = xpProfile;
	}

	public int getExpProfile() {
		return xpProfile;
	}

	public void setStatProfileId(int statProfileId) {
		this.statProfileId = statProfileId;
	}

	public int getStatProfileId() {
		return statProfileId;
	}

	public void setStartingStats(HashMap<String, CharacterStatProgression> stats) {
		this.startingStats = stats;
	}
	public HashMap<String, CharacterStatProgression> getStartingStats() {
		return startingStats;
	}

	public void AddStatProgression(String name, int baseValue, float levelIncrease, float levelPercentIncrease) {
		CharacterStatProgression statProgress = new CharacterStatProgression();
		statProgress.baseValue = baseValue;
		statProgress.levelIncrease = levelIncrease;
		statProgress.levelPercentIncrease = levelPercentIncrease;
		startingStats.put(name, statProgress);
		//Log.debug("CHAR: adding stat progression: " + name);
	}

	public void setStartingSkills(ArrayList<Integer> skills) {
		this.startingSkills = skills;
	}

	public void addStartingItem(int itemID, int count, boolean equipped) {
		CharacterStartingItem item = new CharacterStartingItem();
		item.itemID = itemID;
		item.count = count;
		item.equipped = equipped;
		items.add(item);
	}

	public class CharacterStatProgression {
		public String statName;
		public int baseValue;
		public float levelIncrease;
		public float levelPercentIncrease;
	}

	public class CharacterStartingItem {
		public int itemID;
		public int count;
		public boolean equipped;
	}

}
