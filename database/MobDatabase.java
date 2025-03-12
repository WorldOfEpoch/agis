package atavism.agis.database;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.sql.PreparedStatement;

import atavism.agis.core.Agis;
import atavism.agis.core.AgisEffect;
import atavism.agis.objects.*;
import atavism.agis.objects.AgisBasicQuest.CollectionGoal;
import atavism.agis.objects.AgisBasicQuest.KillGoal;
import atavism.agis.objects.Dialogue.DialogueOption;
import atavism.agis.plugins.ClassAbilityPlugin;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.plugins.PrefabPlugin;
import atavism.agis.plugins.AgisMobPlugin;
import atavism.agis.plugins.AgisWorldManagerPlugin;
import atavism.agis.util.HelperFunctions;
import atavism.server.engine.Behavior;
import atavism.server.engine.Engine;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.math.*;
import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.*;
import java.util.stream.Collectors;

public class MobDatabase {
	private static Queries queries;
	
	public MobDatabase(boolean keepAlive) {
		Log.debug("MobDatabase queries="+queries);
        if (queries == null) {
            synchronized (MobDatabase.class) {
                if (queries == null) {
                    queries = new Queries(keepAlive);
                }
            }
        }
	}
	
	public Map<Integer, HashMap<Integer, SpawnData>> loadInstanceSpawnData(Collection<Integer> instanceIDs) {
	    Map<Integer, HashMap<Integer, SpawnData>> instanceSpawnDataMap = new HashMap<>();
	    if (instanceIDs == null || instanceIDs.isEmpty()) {
	        return instanceSpawnDataMap;
	    }

	    String placeholders = instanceIDs.stream().map(id -> "?").collect(Collectors.joining(","));
	    String sql = "SELECT * FROM spawn_data WHERE instance IN (" + placeholders + ") AND isactive = 1";
	    if(Log.loggingDebug) Log.debug("loadInstanceSpawnData sql=SELECT * FROM spawn_data WHERE instance IN (" + placeholders + ") AND isactive = 1");
	    try (PreparedStatement ps = queries.prepare(sql)) {
	        int index = 1;
	        for (Integer id : instanceIDs) {
	            ps.setInt(index++, id);
	        }
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    int instanceID = rs.getInt("instance");
	                    int id = rs.getInt("id");
	                    SpawnData sd = readSpawnData(rs);
	                    if (sd != null) {
	                        HashMap<Integer, SpawnData> spawnDataList = instanceSpawnDataMap.computeIfAbsent(instanceID, k -> new HashMap<>());
	                        spawnDataList.put(id, sd);
	                    }
	                }
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	        Log.dumpStack("loadInstanceSpawnData: SQLException" + e);
	    }
	    return instanceSpawnDataMap;
	}

	
	public SpawnData loadSpecificSpawnData(int spawnID) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `spawn_data` where id = " + spawnID + " AND isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					SpawnData sd = readSpawnData(rs);
    					return sd;
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		return null;
	}
	
	public static SpawnData readSpawnData(ResultSet rs) {
		SpawnData sd = new SpawnData();
		try {
			if(Log.loggingDebug)	Log.debug("DB: read spawnData for tamplateId: " + rs.getInt("id"));
			String markerName = HelperFunctions.readEncodedString(rs.getBytes("markerName"));
			if (markerName == null || markerName.equals("")) {
				sd.setProperty("markerName", "");
				sd.setLoc(new Point(rs.getFloat("locX"), rs.getFloat("locY"), rs.getFloat("locZ")));
				sd.setOrientation(new Quaternion(rs.getFloat("orientX"), rs.getFloat("orientY"), rs.getFloat("orientZ"), rs.getFloat("orientW")));
			} else {
				sd.setProperty("markerName", markerName);
			}
			
			sd.setProperty("instance", rs.getInt("instance"));
			sd.setCategory(rs.getInt("category"));
			boolean mobTempSet = false;
			int mobID = rs.getInt("mobTemplate");
			if (mobID > 0) {
				mobTempSet = true;
				sd.setTemplateID(mobID);
			}
			mobID = rs.getInt("mobTemplate2");
			if (mobID > 0) {
				mobTempSet = true;
				sd.setTemplateID(mobID);
			}
			mobID = rs.getInt("mobTemplate3");
			if (mobID > 0) {
				mobTempSet = true;
				sd.setTemplateID(mobID);
			}
			mobID = rs.getInt("mobTemplate4");
			if (mobID > 0) {
				mobTempSet = true;
				sd.setTemplateID(mobID);
			}
			mobID = rs.getInt("mobTemplate5");
			if (mobID > 0) {
				mobTempSet = true;
				sd.setTemplateID(mobID);
			}
			if (!mobTempSet)
				return null;
			Log.debug("DB: loading spawnData with template: " + mobID);
			sd.setNumSpawns(rs.getInt("numSpawns"));
			sd.setSpawnRadius(rs.getInt("spawnRadius"));
			sd.setRespawnTime(rs.getInt("respawnTime"));
			sd.setRespawnTimeMax(rs.getInt("respawnTimeMax"));
			sd.setCorpseDespawnTime(rs.getInt("corpseDespawnTime"));
			// Spawn active/deactive times
			int spawnActiveStartHour = rs.getInt("spawnActiveStartHour");
			int spawnActiveEndHour = rs.getInt("spawnActiveEndHour");
			if (spawnActiveStartHour != -1 && spawnActiveEndHour != -1) {
				sd.setProperty("spawnActiveStartHour", spawnActiveStartHour);
				sd.setProperty("spawnActiveEndHour", spawnActiveEndHour);
				int alternateSpawnMobTemplate = rs.getInt("alternateSpawnMobTemplate");
				int alternateSpawnMobTemplate2 = rs.getInt("alternateSpawnMobTemplate2");
				int alternateSpawnMobTemplate3 = rs.getInt("alternateSpawnMobTemplate3");
				int alternateSpawnMobTemplate4 = rs.getInt("alternateSpawnMobTemplate4");
				int alternateSpawnMobTemplate5 = rs.getInt("alternateSpawnMobTemplate5");
				if(alternateSpawnMobTemplate>0)
					sd.setTemplateAlterID(alternateSpawnMobTemplate);
				if(alternateSpawnMobTemplate2>0)
					sd.setTemplateAlterID(alternateSpawnMobTemplate2);
				if(alternateSpawnMobTemplate3>0)
					sd.setTemplateAlterID(alternateSpawnMobTemplate3);
				if(alternateSpawnMobTemplate4>0)
					sd.setTemplateAlterID(alternateSpawnMobTemplate4);
				if(alternateSpawnMobTemplate5>0)
					sd.setTemplateAlterID(alternateSpawnMobTemplate5);
				if (alternateSpawnMobTemplate != -1) {
					sd.setProperty("alternateSpawnMobTemplate", alternateSpawnMobTemplate);
				}
			}
			
			sd.setProperty("id", rs.getInt("id"));

			// Spawn Behaviour
			Log.debug("DB: loading mobBehaviour with template: " + mobID);
			BehaviorTemplate behavTmpl = new BehaviorTemplate(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
			String baseAction = HelperFunctions.readEncodedString(rs.getBytes("baseAction"));
			if (baseAction != null && !baseAction.equals(""))
				behavTmpl.setBaseAction(baseAction);
			behavTmpl.setWeaponsSheathed(rs.getBoolean("weaponSheathed"));

			// Load in patrol data
			Integer patrolPath = rs.getInt("patrolPath");
			if (patrolPath != null) {
				behavTmpl.setPatrolPathID(patrolPath);
				setPatrolPath(behavTmpl);
			//	behavTmpl.setPatrolSwinging(rs.getBoolean("patrolSwinging"));
				
			}
			behavTmpl.setHasCombat(rs.getBoolean("combat"));
			//behavTmpl.setAggroRadius(rs.getInt("aggroRadius"));
			Log.debug("DB: loading quests with template: " + mobID);
			// Load in the string for starts quests and split to get the quest IDs
			String startsQuests = HelperFunctions.readEncodedString(rs.getBytes("startsQuests"));
			ArrayList<Integer> questList = new ArrayList<Integer>();
			if (startsQuests != null && !startsQuests.equals("")) {
			    String[] questNums = startsQuests.split(",");
			    Log.debug("MOB: num start quests: " + questNums.length);
			    for (int i = 0; i < questNums.length; i++) {
				    String questID = questNums[i].replace(",", "");
				    Log.debug("MOB: added start quest " + questID + " to behav: " + behavTmpl.getName());
				    if (!questList.equals(""))
				        questList.add(Integer.parseInt(questID));
			    }
			}
			behavTmpl.setStartsQuests(questList);
			Log.debug("MOB: moving onto end quests");
			// Load in the string for ends quests and split to get the quest IDs
			String endsQuests = HelperFunctions.readEncodedString(rs.getBytes("endsQuests"));
			questList = new ArrayList<Integer>();
			if (endsQuests != null && !endsQuests.equals("")) {
			    String[] questNums = endsQuests.split(",");
			    Log.debug("MOB: num end quests: " + questNums.length);
			    for (int i = 0; i < questNums.length; i++) {
				    String questID = questNums[i].replace(",", "");
				    Log.debug("MOB: added end quest " + questID + " to behav: " + behavTmpl.getName());
				    if (!questList.equals(""))
				        questList.add(Integer.parseInt(questID));
			    }
			}
			behavTmpl.setEndsQuests(questList);
			// Load in the string for starts dialogues and split to get the dialogue IDs
			String startsDialogues = HelperFunctions.readEncodedString(rs.getBytes("startsDialogues"));
			ArrayList<Integer> dialogueList = new ArrayList<Integer>();
			if (startsDialogues != null && !startsDialogues.equals("")) {
			    String[] dialogueNums = startsDialogues.split(",");
			    Log.debug("MOB: num start dialogues: " + dialogueNums.length);
			    for (int i = 0; i < dialogueNums.length; i++) {
				    String dialogueID = dialogueNums[i].replace(",", "");
				    if (!dialogueID.equals("")) {
				    	dialogueList.add(Integer.parseInt(dialogueID));
				    	Log.debug("MOB: added start dialogue " + dialogueID + " to behav: " + behavTmpl.getName());
				    }
			    }
			}
			behavTmpl.setStartsDialogues(dialogueList);
			// Merchant Table
			behavTmpl.setMerchantTable(rs.getInt("merchantTable"));
			// Other Actions
			String otherActions = HelperFunctions.readEncodedString(rs.getBytes("otherActions"));
			ArrayList<String> actionList = new ArrayList<String>();
			if (otherActions != null && !otherActions.equals("")) {
			    String[] actions = otherActions.split(",");
			    Log.debug("MOB: num start actions: " + actions.length);
			    for (int i = 0; i < actions.length; i++) {
				    String action = actions[i].replace(",", "");
				    if (!action.equals("")) {
				    	actionList.add(action);
				    	Log.debug("MOB: added start dialogue " + action + " to behav: " + behavTmpl.getName());
				    }
			    }
			}
			behavTmpl.setOtherActions(actionList);
			// Quest item open table
			Integer questOpenLootTable = rs.getInt("questOpenLootTable");
			if (questOpenLootTable != null) {
			    behavTmpl.setQuestOpenLoot(questOpenLootTable);
			}
			Boolean isChest = rs.getBoolean("isChest");
			behavTmpl.setIsChest(isChest);
			Integer pickupItem = rs.getInt("pickupItem");
			if (pickupItem != null) {
			    behavTmpl.setPickupItem(pickupItem);
			}
			
			Long soid = rs.getLong("shop_oid");
			if(Log.loggingDebug)	Log.debug("DB: spawnData ShopOid: " + soid);

			if (soid > 0) {
				sd.setProperty("shopTimeOut", 0);
				sd.setProperty("shopOid", OID.fromLong(soid));
				sd.setProperty("shopMessage", "");
				sd.setProperty("destroyOnLogOut", false);
				Long oid = rs.getLong("shop_owner");
				sd.setProperty("shopOwner", OID.fromLong(oid));
				behavTmpl.setIsPlayerShop(true);
			} else {
				behavTmpl.setRoamRadius(rs.getInt("roamRadius"));
				behavTmpl.setRoamDelayMin((long) (rs.getFloat("roamDelayMin") * 1000));
				behavTmpl.setRoamDelayMax((long) (rs.getFloat("roamDelayMax") * 1000));
				behavTmpl.setRoamRollTimeEachTime(rs.getBoolean("roamRollTimeEachTime"));
			}

			//behavTmpl.setOtherUse(HelperFunctions.readEncodedString(rs.getBytes("otherUse"));
			
			// Put the behaviour template in the spawn data
			sd.setProperty(AgisMobPlugin.BEHAVIOR_TMPL_PROP, behavTmpl);
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("readSpawnData SQLException"+e);
		}
		return sd;
	}
	
	public static void setPatrolPath(BehaviorTemplate behavTmpl) {
		int patrolPath = behavTmpl.getPatrolPathID();
		
		HashMap<Integer, PatrolPoint>  patrolPoints = loadPatrolPathPoints();
		if (patrolPoints.containsKey(patrolPath)) {
			behavTmpl.addPatrolPoint(patrolPoints.get(patrolPath));
			boolean travelReverse = false;
			if (patrolPoints.get(patrolPath).travelReverse)
				travelReverse = true;
			
			while (patrolPoints.get(patrolPath).nextPoint > 0) {
				patrolPath =patrolPoints.get(patrolPath).nextPoint;
				behavTmpl.addPatrolPoint(patrolPoints.get(patrolPath));
			}
			
			if (travelReverse)
				behavTmpl.travelReverse();
		}
	}
	
	public int getSpawnCount(int instanceID) {
		int spawnCount = 0;
		Log.debug("DB: loading spawnData count for instance: " + instanceID);
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM spawn_data where instance = " + instanceID + " AND isactive = 1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				rs.next();
    				spawnCount = rs.getInt(1);
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		return spawnCount;
	}
	
	public Map<Integer, Integer> getSpawnCounts(Collection<Integer> instanceIDs) {
	    Map<Integer, Integer> spawnCounts = new HashMap<>();
	    if (instanceIDs == null || instanceIDs.isEmpty()) {
	        return spawnCounts;
	    }

	    String placeholders = instanceIDs.stream().map(id -> "?").collect(Collectors.joining(","));
	    String sql = "SELECT instance, COUNT(*) as spawnCount FROM spawn_data WHERE instance IN (" + placeholders + ") AND isactive = 1 GROUP BY instance";

	    try (PreparedStatement ps = queries.prepare(sql)) {
	        int index = 1;
	        for (Integer id : instanceIDs) {
	            ps.setInt(index++, id);
	        }
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            while (rs.next()) {
	                int instanceID = rs.getInt("instance");
	                int count = rs.getInt("spawnCount");
	                spawnCounts.put(instanceID, count);
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	        Log.dumpStack("SQLException in getSpawnCounts: " + e);
	    }
	    return spawnCounts;
	}
	
	
	public HashMap<Integer, Integer> loadMobAggroRadius(int category) {
		HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
		try (PreparedStatement ps = queries.prepare("SELECT id, aggro_radius FROM `mob_templates` where category=" + category + " AND isactive = 1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int mobID = rs.getInt("id");
    					int aggro = rs.getInt("aggro_radius");
    					if (map.containsKey(mobID))
    						map.replace(mobID, aggro);
    					else
    						map.put(mobID, aggro);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return map;
	}


//    public ArrayList<Template> mobTemplateslist;

    // Modify your existing loadMobTemplates method
    public ArrayList<Template> loadMobTemplates(int category) {
        ArrayList<Template> list = new ArrayList<Template>();
//        mobTemplateslist = new ArrayList<Template>();
        String sql = "SELECT * FROM mob_templates WHERE category=? AND isactive=1";
		if (Log.loggingDebug)Log.debug("loadMobTemplates sql=SELECT * FROM mob_templates WHERE category="+category+" AND isactive=1");
        try (PreparedStatement ps = queries.prepare(sql)) {
            ps.setInt(1, category);
            try (ResultSet rs = queries.executeSelect(ps)) {
                while (rs.next()) {
                    int mobID = rs.getInt("id");
                    String mobName = HelperFunctions.readEncodedString(rs.getBytes("name")).trim();
					if (Log.loggingDebug)
						Log.debug("loadMobTemplates " + mobName);

                    Template tmpl = new Template(mobName, mobID, ObjectManagerPlugin.MOB_TEMPLATE);

                    // Populate tmpl with data from rs
                    String displayName = HelperFunctions.readEncodedString(rs.getBytes("displayName")).trim();
                    tmpl.put(WorldManagerClient.NAMESPACE, "displayName", displayName);

                    tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_ID, mobID);
                    tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_OBJECT_TYPE, ObjectTypes.mob);
                    tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_PERCEPTION_RADIUS, 75);
    					String subTitle = HelperFunctions.readEncodedString(rs.getBytes("subTitle"));
    					if (subTitle == null) {
    						tmpl.put(WorldManagerClient.NAMESPACE, "subTitle", "");
    					} else {
    						tmpl.put(WorldManagerClient.NAMESPACE, "subTitle", subTitle);
    					}
    					
    					int mobType = rs.getInt("mobType");
    					tmpl.put(CombatClient.NAMESPACE, "mobType", mobType);
    					if (mobType == 1) {
    						// It is an object - remove name display?
    						tmpl.put(WorldManagerClient.NAMESPACE, "nameDisplay", false);
    						tmpl.put(WorldManagerClient.NAMESPACE, "targetable", false);
    					}
    					LinkedList<String> displays = new LinkedList<String>();
    					if (HelperFunctions.readEncodedString(rs.getBytes("display1")) != null && !HelperFunctions.readEncodedString(rs.getBytes("display1")).equals(""))
    						displays.add(HelperFunctions.readEncodedString(rs.getBytes("display1")));
    					if (HelperFunctions.readEncodedString(rs.getBytes("display2")) != null && !HelperFunctions.readEncodedString(rs.getBytes("display2")).equals(""))
    						displays.add(HelperFunctions.readEncodedString(rs.getBytes("display2")));
    					if (HelperFunctions.readEncodedString(rs.getBytes("display3")) != null && !HelperFunctions.readEncodedString(rs.getBytes("display3")).equals(""))
    						displays.add(HelperFunctions.readEncodedString(rs.getBytes("display3")));
    					if (HelperFunctions.readEncodedString(rs.getBytes("display4")) != null && !HelperFunctions.readEncodedString(rs.getBytes("display4")).equals(""))
    						displays.add(HelperFunctions.readEncodedString(rs.getBytes("display4")));
    					if (displays.isEmpty()) {
    						Log.error("loadMobTemplates No displays found for: " + mobName);
    					}
    					tmpl.put(WorldManagerClient.NAMESPACE, "displays", displays);
    					float scaleVal = rs.getFloat("scale");
    					AOVector v = new AOVector(scaleVal, scaleVal, scaleVal);
    					tmpl.put(WorldManagerClient.NAMESPACE, /*WorldManagerClient.TEMPL_SCALE*/ "scale", v);
    					tmpl.put(CombatClient.NAMESPACE, CombatPlugin.PROP_HITBOX, rs.getInt("hitBox"));
    					tmpl.put(CombatClient.NAMESPACE, "race",  rs.getInt("race_id"));
    					tmpl.put(CombatClient.NAMESPACE, "aspect",  rs.getInt("class_id"));
    					tmpl.put(CombatClient.NAMESPACE, "genderId",  rs.getInt("gender_id"));
						tmpl.put(CombatClient.NAMESPACE, ":statProfile", rs.getInt("stat_profile_id"));

    					
    					tmpl.put(WorldManagerClient.NAMESPACE, "animationState", rs.getInt("baseAnimationState"));
    					
    					tmpl.put(Namespace.FACTION, FactionStateInfo.FACTION_PROP, rs.getInt("faction"));
    					tmpl.put(Namespace.FACTION, FactionStateInfo.AGGRO_RADIUS, rs.getInt("aggro_radius"));
    					tmpl.put(CombatClient.NAMESPACE,Behavior.LINKED_AGGRO_SEND, rs.getBoolean("send_link_aggro"));
    					tmpl.put(CombatClient.NAMESPACE,Behavior.LINKED_AGGRO_GET, rs.getBoolean("get_link_aggro"));
    					tmpl.put(CombatClient.NAMESPACE,Behavior.LINKED_AGGRO_RADIUS, rs.getInt("link_aggro_range"));
						tmpl.put(CombatClient.NAMESPACE,Behavior.LINKED_CHASING_DISTANCE, rs.getInt("chasing_distance"));
						//Pet
						tmpl.put(CombatClient.NAMESPACE,"petCountStat", rs.getInt("pet_count_stat"));

						tmpl.put(CombatClient.NAMESPACE, "attackable", rs.getBoolean("attackable"));
    					int minLevel = rs.getInt("minLevel");
    					// TODO: level randomising getting in the maxLevel as well
    					//tmpl.put(CombatClient.NAMESPACE, "level", new AgisStat("level", level));
    					tmpl.put(CombatClient.NAMESPACE, ":minLevel", minLevel);
    					tmpl.put(CombatClient.NAMESPACE, ClassAbilityPlugin.MIN_MOB_LEVEL, minLevel);
    					int maxLevel = rs.getInt("maxLevel");
    					tmpl.put(CombatClient.NAMESPACE, ":maxLevel", maxLevel);
    					int dmg_base = rs.getInt("minDmg");
						AgisStatDef statDef1 = CombatPlugin.lookupStatDef("dmg-base");
						AgisStatDef statDef2 = CombatPlugin.lookupStatDef("dmg-max");
    					tmpl.put(CombatClient.NAMESPACE, "dmg-base", new AgisStat(statDef1.getId(),"dmg-base", dmg_base));
    					int dmg_max = rs.getInt("maxDmg");
    					if (dmg_max < dmg_base) dmg_max = dmg_base;
    					tmpl.put(CombatClient.NAMESPACE, "dmg-max", new AgisStat(statDef2.getId(),"dmg-max", dmg_max));
    					String attackType = HelperFunctions.readEncodedString(rs.getBytes("dmgType"));
    					tmpl.put(CombatClient.NAMESPACE, "attackType", attackType);
    					tmpl.put(CombatClient.NAMESPACE, "weaponType",new ArrayList<String>());
    					
    					// NEW
    					tmpl.put(CombatClient.NAMESPACE, "attackDistance", rs.getFloat("attackDistance"));
    					//tmpl.put(CombatClient.NAMESPACE, "exp", rs.getInt("exp"));
    					
    					//float attackspeed = rs.getFloat("attackSpeed") * 1000f;
    					//tmpl.put(CombatClient.NAMESPACE, "attack_speed", new AgisStat("attack_speed", (int)attackspeed));
    					int exp_val = rs.getInt("exp");
    					int addexp_val = rs.getInt("addExplev");
						//	tmpl.put(CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_AUTOATTACK_ABILITY, rs.getInt("autoAttack"));
						tmpl.put(CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_AUTOATTACK_ABILITY, -1);

						// NEW Read in additional abilities data
    				/*	for (int i = 0; i < 3; i++) {
    						Integer abilityID = rs.getInt("ability" + i);
    						if (abilityID != null && abilityID > 0) {
    							String statReq = rs.getString("abilityStatReq" + i);
    							int statPercent = rs.getInt("abilityStatPercent" + i);
    							tmpl.put(CombatClient.NAMESPACE, "ability" + i, abilityID);
    							tmpl.put(CombatClient.NAMESPACE, "abilityStatReq" + i, statReq);
    							tmpl.put(CombatClient.NAMESPACE, "abilityStatPercent" + i, statPercent);
    						}
    					}*/
    					tmpl.put(CombatClient.NAMESPACE, "combat.mobflag", true);
    					tmpl.put(CombatClient.NAMESPACE, ClassAbilityPlugin.KILL_EXP_STAT, exp_val);
    					tmpl.put(CombatClient.NAMESPACE, ClassAbilityPlugin.KILL_ADD_EXP_LEV_STAT, addexp_val);
    					tmpl.put(CombatClient.NAMESPACE, WorldManagerClient.TEMPL_ID, mobID);
    					
    					// Effects
    					LinkedList<Integer> effectsList = new LinkedList<Integer>();
    					tmpl.put(CombatClient.NAMESPACE, "effects", effectsList);
    					
    					tmpl.put(WorldManagerClient.NAMESPACE, "species", HelperFunctions.readEncodedString(rs.getBytes("species")));
    					tmpl.put(WorldManagerClient.NAMESPACE, "subSpecies", HelperFunctions.readEncodedString(rs.getBytes("subSpecies")));
    					tmpl.put(WorldManagerClient.NAMESPACE, "speed_walk", rs.getFloat("speed_walk"));
    					tmpl.put(WorldManagerClient.NAMESPACE, "speed_run", rs.getFloat("speed_run"));
    					tmpl.put(CombatClient.NAMESPACE, ":speed_run", rs.getFloat("speed_run"));
    					
    					String questCategory = HelperFunctions.readEncodedString(rs.getBytes("questCategory"));
    					if (questCategory != null) {
    						LinkedList<String> questCategories = new LinkedList<String>();
    						for(String s: questCategory.split(",")){
    							questCategories.add(s);
    						}
    						//questCategories.add(questCategory);
    						tmpl.put(WorldManagerClient.NAMESPACE, "questCategories", questCategories);
    					}
    					String equipment = "";
    				/*	Integer primaryWeapon = rs.getInt("primaryWeapon");
    					if (primaryWeapon != null && primaryWeapon > 0) {
    						equipment = equipment + "*" + primaryWeapon + "|1; ";
    					}
    					Integer secondaryWeapon = rs.getInt("secondaryWeapon");
    					if (secondaryWeapon != null && secondaryWeapon > 0) {
    						equipment = equipment + "*" + secondaryWeapon + "|1; ";
    					}*/
    					tmpl.put(InventoryClient.NAMESPACE, InventoryClient.TEMPL_ITEMS, equipment);
					if(Log.loggingDebug)Log.debug("loadMobTemplates mob " + tmpl.getName() + " now has equipment: " + equipment);
    					
    					String specialUse = HelperFunctions.readEncodedString(rs.getBytes("specialUse"));
    					if (specialUse != null && !specialUse.equals("")) {
							tmpl.put(WorldManagerClient.NAMESPACE, "specialUse", specialUse);
    					}
    					
    					
    					String tags = HelperFunctions.readEncodedString(rs.getBytes("tags"));
    					tmpl.put(CombatClient.NAMESPACE, "tags",tags);
    					Integer behavior_profile_id = rs.getInt("behavior_profile_id");
    					tmpl.put(CombatClient.NAMESPACE, "behavProfile",behavior_profile_id);
    					
    					// Skinning
    					if (SKINNING_ENABLED) {
    						Integer skinningLootTable = rs.getInt("skinningLootTable");
    						if (skinningLootTable!= null && skinningLootTable > 0) {
    							tmpl.put(WorldManagerClient.NAMESPACE, "skinningLootTable", skinningLootTable);
    							Integer skinningLevelReq = rs.getInt("skinningLevelReq");
    							tmpl.put(WorldManagerClient.NAMESPACE, "skinningLevelReq", skinningLevelReq);
    							Integer skinningLevelMax = rs.getInt("skinningLevelMax");
    							tmpl.put(WorldManagerClient.NAMESPACE, "skinningLevelMax", skinningLevelMax);
    							Integer skinningSkillId = rs.getInt("skinningSkillId");
    							tmpl.put(WorldManagerClient.NAMESPACE, "skinningSkillId", skinningSkillId);
    							Integer skinningSkillExp = rs.getInt("skinningSkillExp");
    							tmpl.put(WorldManagerClient.NAMESPACE, "skinningSkillExp", skinningSkillExp);
    							String skinningWeaponReq =  HelperFunctions.readEncodedString(rs.getBytes("skinningWeaponReq"));
    							tmpl.put(WorldManagerClient.NAMESPACE, "skinningWeaponReq", skinningWeaponReq);
    							float skinningHarvestTime = rs.getFloat("skinningHarvestTime");
    							tmpl.put(WorldManagerClient.NAMESPACE, "skinningHarvestTime", skinningHarvestTime);
    						}
    					}
                    list.add(tmpl);
//                    mobTemplateslist.add(tmpl);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Log.dumpStack("SQLException: " + e);
        }

        // Collect mob IDs
        List<Integer> mobIDs = list.stream()
                .map(Template::getTemplateID)
                .collect(Collectors.toList());

        // Batch load additional data
//        Map<Integer, HashMap<String, Integer>> mobStats = loadMobStats(mobIDs);
//        Map<Integer, HashMap<String, Integer>> wmgrMobStats = loadWmgrMobStats(mobIDs);
        Map<Integer, HashMap<Integer, MobLootTable>> mobLoots = loadMobLoot(mobIDs);
        if (mobLoots == null)
        	mobLoots = new HashMap<>();
        // Assign additional data to templates
        for (Template tmpl : list) {
            int mobID = tmpl.getTemplateID();

//            HashMap<String, Integer> combatStatOverrides = mobStats.get(mobID);
//            if (combatStatOverrides == null) {
//                combatStatOverrides = new HashMap<>();
//                Log.error("loadMobTemplates No combat stat overrides found for mobID " + mobID);
//            }
//
//            HashMap<String, Integer> wmgrStatOverrides = wmgrMobStats.get(mobID);
//            if (wmgrStatOverrides == null) {
//                wmgrStatOverrides = new HashMap<>();
//                Log.error("loadMobTemplates No world manager stat overrides found for mobID " + mobID);
//            }

//            tmpl.put(CombatClient.NAMESPACE, ":statOverrides", (Serializable) combatStatOverrides);
//            tmpl.put(WorldManagerClient.NAMESPACE, ":statOverrides", (Serializable) wmgrStatOverrides);
		    tmpl.put(CombatClient.NAMESPACE, ":statOverrides",  new HashMap<String, Integer>());
		    tmpl.put(WorldManagerClient.NAMESPACE, ":statOverrides",  new HashMap<String, Integer>());
            tmpl.put(InventoryClient.NAMESPACE, "lootTables", (Serializable) mobLoots.get(mobID));

//            Log.error("loadMobTemplates Assigned statOverrides to template for mobID " + mobID + ": " + combatStatOverrides);
//            Log.error("loadMobTemplates Assigned wmgrStatOverrides to template for mobID " + mobID + ": " + wmgrStatOverrides);
        }
        return list;
    }


	public ArrayList<HashMap<String, Serializable>> getMobTemplates(int category, int baseCategory) {
		Log.debug("getMobTemplates: getting mob templates");
		ArrayList<HashMap<String, Serializable>> list = new ArrayList<HashMap<String, Serializable>>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `mob_templates` where ( category=" + category + 
					" OR category=" + baseCategory  + " ) AND isactive = 1")) {
			
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					HashMap<String, Serializable> map = new HashMap<String, Serializable>();
    					int mobID = rs.getInt("id");
    					map.put("id", mobID);
    					String mobName = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					mobName.trim();
    					String displayName = HelperFunctions.readEncodedString(rs.getBytes("displayName"));
    					displayName.trim();
    					
    					map.put("displayName", displayName);
    					
    					map.put("name", mobName);
						if(Log.loggingDebug)Log.debug("getMobTemplates: found mob template: " + mobName);
    					map.put("subTitle", HelperFunctions.readEncodedString(rs.getBytes("subTitle")));
    					map.put("mobType", rs.getInt("mobType"));
						if(Log.loggingDebug)Log.debug("getMobTemplates: has mob type: " + rs.getInt("mobType"));
    					LinkedList<String> displays = new LinkedList<String>();
    					if (HelperFunctions.readEncodedString(rs.getBytes("display1")) != null && !HelperFunctions.readEncodedString(rs.getBytes("display1")).equals(""))
    						displays.add(HelperFunctions.readEncodedString(rs.getBytes("display1")));
    					if (HelperFunctions.readEncodedString(rs.getBytes("display2")) != null && !HelperFunctions.readEncodedString(rs.getBytes("display2")).equals(""))
    						displays.add(HelperFunctions.readEncodedString(rs.getBytes("display2")));
    					if (HelperFunctions.readEncodedString(rs.getBytes("display3")) != null && !HelperFunctions.readEncodedString(rs.getBytes("display3")).equals(""))
    						displays.add(HelperFunctions.readEncodedString(rs.getBytes("display3")));
    					if (HelperFunctions.readEncodedString(rs.getBytes("display4")) != null && !HelperFunctions.readEncodedString(rs.getBytes("display4")).equals(""))
    						displays.add(HelperFunctions.readEncodedString(rs.getBytes("display4")));
    					Log.debug("MOB: about to check if any displays were found");
    					if (displays.isEmpty()) {
    						Log.error("No displays found for: " + mobName);
    					}
    					map.put("displays", displays);
    					map.put("scale", rs.getFloat("scale"));
    					
    					map.put("level", rs.getInt("minLevel"));
    					map.put("attackable", rs.getBoolean("attackable"));
    					
    					map.put(FactionStateInfo.AGGRO_RADIUS, rs.getInt("aggro_radius"));
    					map.put(Behavior.LINKED_AGGRO_SEND, rs.getInt("send_link_aggro"));
    					map.put(Behavior.LINKED_AGGRO_GET, rs.getInt("get_link_aggro"));
    					map.put(Behavior.LINKED_AGGRO_RADIUS, rs.getInt("link_aggro_range"));
    						map.put("faction", rs.getInt("faction"));
    					map.put("species", HelperFunctions.readEncodedString(rs.getBytes("species")));
    					map.put("subSpecies", HelperFunctions.readEncodedString(rs.getBytes("subSpecies")));
    					//map.put("aggro_range", rs.getInt("aggro_range"));
    					
    					//map.put("gender", HelperFunctions.readEncodedString(rs.getBytes("gender"));
    					
    					
    					//map.put("equipment", loadMobEquipment(mobID));
    					//map.put("lootTables", loadMobLoot(mobID));
						if(Log.loggingDebug)Log.debug("getMobTemplates: added mob template: " + mobName);
    					list.add(map);
    				}
    			}
		    }
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		if(Log.loggingDebug)Log.debug("getMobTemplates: returning mob template list with " + list.size() + " templates");
		return list;
	}
	

    // Modified loadMobStats method
//    private Map<Integer, HashMap<String, Integer>> loadMobStats(Collection<Integer> mobIDs) {
//        Map<Integer, HashMap<String, Integer>> mobStatsMap = new HashMap<>();
//        if (mobIDs == null || mobIDs.isEmpty()) {
//            return mobStatsMap; // Return empty map if mobIDs is null or empty
//        }
//
//        // Convert mobIDs to a comma-separated string for the SQL IN clause
//        String mobIDsString = mobIDs.stream()
//                                    .map(String::valueOf)
//                                    .collect(Collectors.joining(","));
//
//        Log.error("loadMobStats: loading in stat data for mobs: " + mobIDsString);
//        String sql = "SELECT * FROM `mob_stat` WHERE mobTemplate IN (" + mobIDsString + ") AND isactive = 1";
//        Log.error("loadMobStats: sql: " + sql);
//
//        try (PreparedStatement ps = queries.prepare(sql)) {
//            try (ResultSet rs = queries.executeSelect(ps)) {
//                if (rs != null) {
//                    while (rs.next()) {
//                        int mobID = rs.getInt("mobTemplate");
//                        String stat = HelperFunctions.readEncodedString(rs.getBytes("stat"));
//                        int value = rs.getInt("value");
//
//                        HashMap<String, Integer> statOverrides = mobStatsMap.get(mobID);
//                        if (statOverrides == null) {
//                            statOverrides = new HashMap<>();
//                            mobStatsMap.put(mobID, statOverrides);
//                        }
//                        statOverrides.put(stat, value);
//                        Log.error("Loaded stat for mobID " + mobID + ": " + stat + " = " + value);
//
//                    }
//                }
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//            Log.dumpStack("SQLException: " + e);
//        }
//        Log.error("loadMobStats: finished loading in stat data for mobs.");
//        return mobStatsMap;
//    }
//    // Modified loadWmgrMobStats method
//    private Map<Integer, HashMap<String, Integer>> loadWmgrMobStats(Collection<Integer> mobIDs) {
//        Map<Integer, HashMap<String, Integer>> wmgrMobStatsMap = new HashMap<>();
//        if (mobIDs == null || mobIDs.isEmpty()) {
//            return wmgrMobStatsMap; // Return empty map if mobIDs is null or empty
//        }
//
//        String mobIDsString = mobIDs.stream()
//                                    .map(String::valueOf)
//                                    .collect(Collectors.joining(","));
//
//        Log.error("loadWmgrMobStats: loading in Wmgr stat data for mobs: " + mobIDsString);
//        String sql = "SELECT * FROM `mob_stat` WHERE mobTemplate IN (" + mobIDsString + ") AND isactive = 1";
//        Log.error("loadWmgrMobStats: sql: " + sql);
//
//        try (PreparedStatement ps = queries.prepare(sql)) {
//            try (ResultSet rs = queries.executeSelect(ps)) {
//                if (rs != null) {
//                    while (rs.next()) {
//                        int mobID = rs.getInt("mobTemplate");
//                        String stat = HelperFunctions.readEncodedString(rs.getBytes("stat"));
//                        int value = rs.getInt("value");
//
//                        Log.error("loadWmgrMobStats stat " + stat + " settings " +
//                                AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED + " " +
//                                CombatPlugin.STEALTH_STAT + " " +
//                                CombatPlugin.PERCEPTION_STEALTH_STAT);
//
//                        // Initialize statOverrides if necessary
//                        HashMap<String, Integer> statOverrides = wmgrMobStatsMap.get(mobID);
//                        if (statOverrides == null) {
//                            statOverrides = new HashMap<>();
//                            wmgrMobStatsMap.put(mobID, statOverrides);
//                        }
//
//                        // Check and assign values for each stat independently
//                        if (AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED != null && stat.equals(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED)) {
//                            statOverrides.put(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, value);
//                            Log.error("Assigned PROP_MOVEMENT_SPEED for mobID " + mobID + " with value " + value);
//                        }
//                        if (CombatPlugin.STEALTH_STAT != null && stat.equals(CombatPlugin.STEALTH_STAT)) {
//                            statOverrides.put(CombatPlugin.STEALTH_STAT, value);
//                            Log.error("Assigned STEALTH_STAT for mobID " + mobID + " with value " + value);
//                        }
//                        if (CombatPlugin.PERCEPTION_STEALTH_STAT != null && stat.equals(CombatPlugin.PERCEPTION_STEALTH_STAT)) {
//                            statOverrides.put(CombatPlugin.PERCEPTION_STEALTH_STAT, value);
//                            Log.error("Assigned PERCEPTION_STEALTH_STAT for mobID " + mobID + " with value " + value);
//                        }
//
//                        Log.error("loadWmgrMobStats for mobID " + mobID + ": " + statOverrides);
//                    }
//                }
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//            Log.dumpStack("SQLException: " + e);
//        }
//        Log.error("loadWmgrMobStats: finished loading in Wmgr stat data for mobs.");
//        return wmgrMobStatsMap;
//    }

	public static HashMap<Integer, PatrolPoint> loadPatrolPathPoints() {
		HashMap<Integer, PatrolPoint> patrolPoints = new HashMap<Integer, PatrolPoint>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `patrol_path` where isactive = 1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Point p = new Point(rs.getFloat("locX"), rs.getFloat("locY"), rs.getFloat("locZ"));
    					Float lingerTime = rs.getFloat("lingerTime");
    					PatrolPoint point = new PatrolPoint(rs.getInt("id"), p, lingerTime);
    					
    					point.startingPoint = rs.getBoolean("startingPoint");
    					if (point.startingPoint) {
    						point.name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    						if (rs.getBoolean("travelReverse")) {
    							point.travelReverse = rs.getBoolean("travelReverse"); 
    						}
    					}
    					point.nextPoint = rs.getInt("nextPoint");
    					
    					patrolPoints.put(rs.getInt("id"), point);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		return patrolPoints;
	}
	

    // Modified loadMobLoot method
    private Map<Integer, HashMap<Integer, MobLootTable>> loadMobLoot(Collection<Integer> mobIDs) {
        Map<Integer, HashMap<Integer, MobLootTable>> mobLootMap = new HashMap<>();
        if (mobIDs == null || mobIDs.isEmpty()) {
            return mobLootMap; // Return empty map if mobIDs is null or empty
        }

        String mobIDsString = mobIDs.stream()
                                    .map(String::valueOf)
                                    .collect(Collectors.joining(","));

        if(Log.loggingDebug)Log.debug("loadMobLoot: loading in loot data for mobs: " + mobIDsString);
        String sql = "SELECT * FROM `mob_loot` WHERE mobTemplate IN (" + mobIDsString + ") AND isactive = 1 ORDER BY mobTemplate, dropChance ASC";
		if(Log.loggingDebug) Log.debug("loadMobLoot: sql: " + sql);
        try (PreparedStatement ps = queries.prepare(sql)) {
            try (ResultSet rs = queries.executeSelect(ps)) {
                if (rs != null) {
                    while (rs.next()) {
                        int mobID = rs.getInt("mobTemplate");
                        HashMap<Integer, MobLootTable> lootTables = mobLootMap.get(mobID);
                        if (lootTables == null) {
                            lootTables = new HashMap<>();
                            mobLootMap.put(mobID, lootTables);
                        }

                        int lootTable = rs.getInt("lootTable");
                        int count = rs.getInt("count");
                        float tableChance = rs.getFloat("dropChance");

                        MobLootTable mlt = new MobLootTable();
                        mlt.setID(lootTable);
                        mlt.setChances(tableChance);
                        mlt.setItemsPerLoot(count);

                        int id = lootTables.size();
                        lootTables.put(id, mlt);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Log.dumpStack("SQLException: " + e);
        }
		if(Log.loggingDebug) Log.debug("loadMobLoot: finished loading in loot data for mobs.");
        return mobLootMap;
    }
	
	/**
	 * Loading Mob Behavior Profiles
	 * @return
	 */
	public Map<Integer,MobBehaviorProfile> loadBehaviorProfile() {
		Map<Integer,MobBehaviorProfile> list = new HashMap<Integer,MobBehaviorProfile>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM mob_behavior_profile where isactive = 1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					MobBehaviorProfile mbp = new MobBehaviorProfile();
    					int id = rs.getInt("id");
    					String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					mbp.setId(id);
    					mbp.setName(name);
    					list.put(id,mbp);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadBehaviorProfile SQLException"+e);
		}
		
		for(MobBehaviorProfile mbp : list.values()) {
			
			loadProfileBehaviors(mbp,mbp.getId());
			// Load stances
			//f.setDefaultStances(loadFactionStances(f.getID()));
		}
		
		return list;
	}
	
	/**
	 * Loading Behaviors for Mob Behavior profile
	 * @param mbp
	 * @param id
	 */
	public void loadProfileBehaviors(MobBehaviorProfile mbp, int id) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM mob_behaviors where profile_id=" + id +" ORDER BY behavior_order ASC")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int bid = rs.getInt("id");//0-Melee; 1-Ranged Offensive; 2-Ranged Defensive; 3-Defend; 4-Flee; 5-Heal
    					int type = rs.getInt("type");//0-Melee; 1-Ranged Offensive; 2-Ranged Defensive; 3-Defend; 4-Flee; 5-Heal
    					int weapon = rs.getInt("weapon");
    					int flee_type = rs.getInt("flee_type");
    					//float  x = rs.getFloat("flee_loc_x");
    					//float  y = rs.getFloat("flee_loc_y");
    					//float  z = rs.getFloat("flee_loc_z");
    					int ability_interval = rs.getInt("ability_interval");
    					int mob_tag = rs.getInt("mob_tag");
    					boolean ignore_chase_distance = rs.getBoolean("ignore_chase_distance");


						if(Log.loggingDebug)Log.debug("loadProfileBehaviors profile id=" +id + " adding behavior type=" +  type + " weapon="+weapon+" flee_type=" + flee_type + " ability_interval="+ability_interval+" mob_tag="+mob_tag+" ignore_chase_distance="+ignore_chase_distance);
    					MobBehavior mb = new MobBehavior();
    					mb.id = bid;
    					mb.setType(type);
    					mb.setFleeType(flee_type);
    				//	mb.setFleePoint(new Point(x,y,z) );
    					mb.setAbilityInterval(ability_interval);
    					mb.setMobTag(mob_tag);
    					mb.setIgnoreChaseDistance(ignore_chase_distance);
    					mb.setWeapon(weapon);
    					mbp.addBehavior(mb);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	    for(MobBehavior mb : mbp.behaviors) {
	    	loadBehaviorsConditions(mb);
	    	loadBehaviorsAbilities(mb);
	    	loadBehaviorsPoints(mb);
	    }
	}
	
	
	/**
	 * Loading Flee points for Mob Behavior
	 * @param mb
	 */
	public void loadBehaviorsPoints(MobBehavior mb) {
		ArrayList<Integer> list = new ArrayList<Integer>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM mob_behavior_points where behavior_id=" + mb.id)) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int mid = rs.getInt("id");
    					float  x = rs.getFloat("loc_x");
    					float  y = rs.getFloat("loc_y");
    					float  z = rs.getFloat("loc_z");
    					mb.addFleePoint(new Point(x,y,z) );
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	    
	}
	
	
	/**
	 * Loading Abilities for Mob Behavior
	 * @param mb
	 */
	public void loadBehaviorsAbilities(MobBehavior mb) {
		ArrayList<Integer> list = new ArrayList<Integer>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM mob_ability where behavior_id=" + mb.id +" ORDER BY mob_ability_order ASC")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int mid = rs.getInt("id");
    					
						String abilities = HelperFunctions.readEncodedString(rs.getBytes("abilities"));
						float minAbilityRangePercentage = rs.getFloat("minAbilityRangePercentage");
						float maxAbilityRangePercentage = rs.getFloat("maxAbilityRangePercentage");
						int mobAbilityType = rs.getInt("mob_ability_type");
						MobAbility ma = new MobAbility();
						ma.id = mid;
						ma.abilities = abilities;
						ma.minAbilityRangePercentage = minAbilityRangePercentage;
						ma.maxAbilityRangePercentage = maxAbilityRangePercentage;
						switch (mobAbilityType) {
						case 0:
							mb.abilities.add(ma);
							break;
						case 1:
							mb.startAbilities.add(ma);
							break;
						case 2:
    						mb.endAbilities.add(ma);
    						break;
    						
    					}
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	    
	    for(MobAbility ma : mb.abilities) {
	    	loadBehaviorAbilityConditions(ma);
	    }
	    for(MobAbility ma : mb.startAbilities) {
	    	loadBehaviorAbilityConditions(ma);
	    }
	    for(MobAbility ma : mb.endAbilities) {
	    	loadBehaviorAbilityConditions(ma);
	    }
	    
	    
	}
	
	/**
	 * Loading Conditions for Mob Behavior
	 * @param mb
	 */
	public void loadBehaviorsConditions(MobBehavior mb) {
		ArrayList<Integer> list = new ArrayList<Integer>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM behavior_conditions_group where behavior_id=" + mb.id +" ORDER BY group_order ASC")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int gid = rs.getInt("id");
    					
    					list.add(gid);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	    
	    for(int gid : list) {
	    	BehaviorConditionGroupSettings bcgs = new BehaviorConditionGroupSettings();
		    try (PreparedStatement ps = queries.prepare("SELECT * FROM behavior_conditions where conditions_group_id=" + gid )) {
		        try (ResultSet rs = queries.executeSelect(ps)) {
	    			if (rs != null) {
	    				while (rs.next()) {
	    					int type = rs.getInt("type");//0-Event; 1-Distance; 2-Stat; 3-Effect; 4-CombatState; 5-DeathState; 6-NumberOfTargets
	    					float distance = rs.getFloat("distance");
	    					boolean less = rs.getBoolean("less");
	    					String statName = HelperFunctions.readEncodedString(rs.getBytes("stat_name"));
	    					float statValue = rs.getFloat("stat_value");
	    					boolean statVitalityPercentage = rs.getBoolean("stat_vitality_percentage");
	    					int target = rs.getInt("target");//0-Caster; 1-Target
	    					int effectTag = rs.getInt("effect_tag_id");
	    					boolean onTarget = rs.getBoolean("on_target");
	    					boolean combatState = rs.getBoolean("combat_state");
	    					boolean deathState = rs.getBoolean("death_state");
	    					int triggerEvent = rs.getInt("trigger_event_Id");//0-Parry; 1-Dodge; 2-Miss; 3-Damage; 4-Heal; 5-Critical; 6-Kill; 7-Stun; 8-Sleep
	    					int target_number = rs.getInt("target_number");
	    					boolean target_ally = rs.getBoolean("target_ally");
	    					BehaviorConditionSettings bcs = new BehaviorConditionSettings();
	    					bcs.type = type;
	    					bcs.distance = distance;
	    					bcs.less =less;
	    					bcs.statName = statName;
	    					bcs.statValue = statValue;
	    					bcs.statVitalityPercentage = statVitalityPercentage;
	    					bcs.target=target;
	    					bcs.effectTag=effectTag;
	    					bcs.effectTag=effectTag;
	    					bcs.onTarget=onTarget;
	    					bcs.combatState=combatState;
	    					bcs.deathState=deathState;
	    					
	    					switch(triggerEvent) {
	    					case 0:
	    						bcs.triggerEvent=TriggerProfile.Type.PARRY;
	    						break;
	    					case 1:
	    						bcs.triggerEvent=TriggerProfile.Type.DODGE;
	    						break;
	    					case 2:
	    						bcs.triggerEvent=TriggerProfile.Type.MISS;
	    						break;
	    					case 3:
	    						bcs.triggerEvent=TriggerProfile.Type.DAMAGE;
	    						break;
	    					case 4:
	    						bcs.triggerEvent=TriggerProfile.Type.HEAL;
	    						break;
	    					case 5:
	    						bcs.triggerEvent=TriggerProfile.Type.CRITICAL;
	    						break;
	    					case 6:
	    						bcs.triggerEvent=TriggerProfile.Type.KILL;
	    						break;
	    					case 7:
	    						bcs.triggerEvent=TriggerProfile.Type.STUN;
	    						break;
	    					case 8:
	    						bcs.triggerEvent=TriggerProfile.Type.SLEEP;
	    						break;
	    					}
	    					
	    					bcs.target_number=target_number;
	    					bcs.target_ally=target_ally;
	    					
	    					bcgs.conditions.add(bcs);
	    				}
	    			}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
	    	mb.conditionsGroup.add(bcgs);
	    	
	    }
	    
	    
	}
	/**
	 * Loading Conditions for Mob Behavior Abilities
	 * @param ma
	 */
	public void loadBehaviorAbilityConditions(MobAbility ma) {
		ArrayList<Integer> list = new ArrayList<Integer>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM mob_ability_conditions_group where mob_ability_id=" + ma.id +" ORDER BY group_order ASC")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int gid = rs.getInt("id");
    					
    					list.add(gid);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	    
	    for(int gid : list) {
	    	BehaviorConditionGroupSettings bcgs = new BehaviorConditionGroupSettings();
		    try (PreparedStatement ps = queries.prepare("SELECT * FROM mob_ability_conditions where conditions_group_id=" + gid )) {
		        try (ResultSet rs = queries.executeSelect(ps)) {
	    			if (rs != null) {
	    				while (rs.next()) {
	    					int type = rs.getInt("type");//0-Event; 1-Distance; 2-Stat; 3-Effect; 4-CombatState; 5-DeathState; 6-NumberOfTargets
	    					float distance = rs.getFloat("distance");
	    					boolean less = rs.getBoolean("less");
	    					String statName = HelperFunctions.readEncodedString(rs.getBytes("stat_name"));
	    					float statValue = rs.getFloat("stat_value");
	    					boolean statVitalityPercentage = rs.getBoolean("stat_vitality_percentage");
	    					int target = rs.getInt("target");//0-Caster; 1-Target
	    					int effectTag = rs.getInt("effect_tag_id");
	    					boolean onTarget = rs.getBoolean("on_target");
	    					boolean combatState = rs.getBoolean("combat_state");
	    					boolean deathState = rs.getBoolean("death_state");
	    					int triggerEvent = rs.getInt("trigger_event_Id");//0-Parry; 1-Dodge; 2-Miss; 3-Damage; 4-Heal; 5-Critical; 6-Kill; 7-Stun; 8-Sleep
	    					int target_number = rs.getInt("target_number");
	    					boolean target_ally = rs.getBoolean("target_ally");
	    					BehaviorConditionSettings bcs = new BehaviorConditionSettings();
	    					bcs.type = type;
	    					bcs.distance = distance;
	    					bcs.less =less;
	    					bcs.statName = statName;
	    					bcs.statValue = statValue;
	    					bcs.statVitalityPercentage = statVitalityPercentage;
	    					bcs.target=target;
	    					bcs.effectTag=effectTag;
	    					bcs.effectTag=effectTag;
	    					bcs.onTarget=onTarget;
	    					bcs.combatState=combatState;
	    					bcs.deathState=deathState;
	    					switch(triggerEvent) {
	    					case 0:
	    						bcs.triggerEvent=TriggerProfile.Type.PARRY;
	    						break;
	    					case 1:
	    						bcs.triggerEvent=TriggerProfile.Type.DODGE;
	    						break;
	    					case 2:
	    						bcs.triggerEvent=TriggerProfile.Type.MISS;
	    						break;
	    					case 3:
	    						bcs.triggerEvent=TriggerProfile.Type.DAMAGE;
	    						break;
	    					case 4:
	    						bcs.triggerEvent=TriggerProfile.Type.HEAL;
	    						break;
	    					case 5:
	    						bcs.triggerEvent=TriggerProfile.Type.CRITICAL;
	    						break;
	    					case 6:
	    						bcs.triggerEvent=TriggerProfile.Type.KILL;
	    						break;
	    					case 7:
	    						bcs.triggerEvent=TriggerProfile.Type.STUN;
	    						break;
	    					case 8:
	    						bcs.triggerEvent=TriggerProfile.Type.SLEEP;
	    						break;
	    					}
	    					
	    					bcs.target_number=target_number;
	    					bcs.target_ally=target_ally;
	    					
	    					bcgs.conditions.add(bcs);
	    				}
	    			}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
	    	ma.conditionsGroup.add(bcgs);
	    	
	    }
	    
	    
	}
	
	
	/**
	 * Load factions definitions for category
	 * @param category
	 * @return
	 */
	public ArrayList<Faction> loadFactions(int category) {
		ArrayList<Faction> list = new ArrayList<Faction>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM factions where category=" + category + " AND isactive = 1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Faction f = new Faction(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")), HelperFunctions.readEncodedString(rs.getBytes("factionGroup")),
    							rs.getInt("category"));
    					f.setIsPublic(rs.getBoolean("public"));
    					f.setDefaultStance(rs.getInt("defaultStance"));
    					list.add(f);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		
		for(Faction f : list) {
			// Load stances
			f.setDefaultStances(loadFactionStances(f.getID()));
		}
		
		return list;
	}
	
	/**
	 * Loading stance between factions for faction
	 * @param factionID
	 * @return
	 */
	public HashMap<Integer, Integer> loadFactionStances(int factionID) {
		HashMap<Integer, Integer> factionStances = new HashMap<Integer, Integer>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `faction_stances` where factionID = " + factionID + " AND isactive = 1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					factionStances.put(rs.getInt("otherFaction"), rs.getInt("defaultStance"));
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		return factionStances;
	}

	/**
	 * Get count of the quest definitions
	 * @return
	 */
	public int getCountQuests() {
		int count = 0;
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM quests where isactive=1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				rs.next();
    				count = rs.getInt(1);
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		return count;
	}
	
	/**
	 * Loading Quest Definitions
	 * @param category
	 * @return
	 */
	public HashMap<Integer, AgisBasicQuest> loadQuests(int category) {
		HashMap<Integer, AgisBasicQuest> list = new HashMap<Integer, AgisBasicQuest>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM quests where category=" + category + " AND isactive = 1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					AgisBasicQuest q = new AgisBasicQuest();
    					int questID = rs.getInt("id");
    					Log.debug("loadQuests: Loading Quest Id="+questID);
    					q.setID(questID);
    					q.setName(HelperFunctions.readEncodedString(rs.getBytes("name")));
    					q.setFaction(rs.getInt("faction"));
    					q.setRepeatable(rs.getBoolean("repeatable"));
    					q.setSecondaryGrades(rs.getInt("numGrades")-1);
    					q.setDesc(HelperFunctions.readEncodedString(rs.getBytes("description")));
    					q.setObjective(HelperFunctions.readEncodedString(rs.getBytes("objectiveText")));
    					q.setProgressText(HelperFunctions.readEncodedString(rs.getBytes("progressText")));
    					int deliveryItem = rs.getInt("deliveryItem1");
    					if (deliveryItem != -1) {
    						q.addDeliveryItem(deliveryItem);
    					}
    					deliveryItem = rs.getInt("deliveryItem2");
    					if (deliveryItem != -1) {
    						q.addDeliveryItem(deliveryItem);
    					}
    					deliveryItem = rs.getInt("deliveryItem3");
    					if (deliveryItem != -1) {
    						q.addDeliveryItem(deliveryItem);
    					}
    					// Requirements
    					int questPrereq = rs.getInt("questPrereq");
    					if (questPrereq != -1)
    					    q.addQuestPrereq(questPrereq);
    					int questStartedReq = rs.getInt("questStartedReq");
    					if (questStartedReq != -1)
    					    q.setQuestStartedReq(questStartedReq);
    					
    					//Load Rewards
    					//loadQuestRewards(q, questID);
    					int rewardLevel = 0;
    					q.setCompletionText(rewardLevel, HelperFunctions.readEncodedString(rs.getBytes("completionText")));
    					q.setXpReward(rewardLevel, rs.getInt("experience"));
    				/*	for (int i = 1; i < 9; i++) {
    						int item = rs.getInt("item" + i);
    						if (item != -1) {
    							int itemCount = rs.getInt("item" + i + "count");
    							if (itemCount > 0)
    								q.addReward(rewardLevel, item, itemCount);
    						}
    						int itemToChoose = rs.getInt("chooseItem" + i);
    						if (itemToChoose != -1) {
    							int itemCount = rs.getInt("chooseItem" + i + "count");
    							if (itemCount > 0)
    								q.addRewardToChoose(rewardLevel, itemToChoose, itemCount);
    						}
    					}*/
    					for (int i = 1; i < 3; i++) {
    						int currency = rs.getInt("currency" + i);
    						if (currency > 0) {
    							int currencyCount = rs.getInt("currency" + i + "count");
    							q.setCurrencyReward(rewardLevel, currency, currencyCount);
    						}
    						int faction = rs.getInt("rep" + i);
    						if (faction > 0) {
    							int repCount = rs.getInt("rep" + i + "gain");
    							q.setRepReward(rewardLevel, faction, repCount);
    						}
    					}
    					Log.debug("QDB: loaded quest rewards for quest: " + questID + ". Has experience:" 
    							+ q.getXpReward().get(rewardLevel) + ". and completionText: " + q.getCompletionText());
    					list.put(questID, q);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		
		for (AgisBasicQuest q : list.values()) {
			//Load Requirements
			q.setRequirements(getQuestRequirements(q.getID()));
			
			//Load Objectives
			loadQuestObjectives(q, q.getID());
			loadQuestItems(q,q.getID());
		}
		return list;
	}
	
	/**
	 * Loading Quest Prefab Definitions 
	 * @param category
	 * @return
	 */
	public HashMap<String, Serializable> loadQuestsPrefrabData(int category) {
		HashMap<Integer, AgisBasicQuest> list = loadQuests(1);
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int c = 0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM quests where category=" + category + " AND isactive = 1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					
    					int id = rs.getInt("id");
    					Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
    					long date = 0L;
    					if (taxPaidUntil != null) {
    						date = taxPaidUntil.getTime();
    					}
    					Log.debug("loadQuestsPrefrabData: id=" + id + " new");
    					props.put("i" + c + "id", id);
    					props.put("i" + c + "name", HelperFunctions.readEncodedString(rs.getBytes("name")));
    				//	props.put("i" + c + "faction", rs.getInt("faction"));
    				//	props.put("i" + c + "repeatable", rs.getBoolean("repeatable"));
    					props.put("i" + c + "numGrades", rs.getInt("numGrades"));
    					props.put("i" + c + "description", HelperFunctions.readEncodedString(rs.getBytes("description")));
    					props.put("i" + c + "objectiveText", HelperFunctions.readEncodedString(rs.getBytes("objectiveText")));
    					props.put("i" + c + "progressText", HelperFunctions.readEncodedString(rs.getBytes("progressText")));
    					int deliveryItem = rs.getInt("deliveryItem1");
    					int it=0;
    					if (deliveryItem != -1) {
    						props.put("i" + c + "deliveryItem"+(it++),deliveryItem);
    						
    					}
    					deliveryItem = rs.getInt("deliveryItem2");
    					if (deliveryItem != -1) {
    						props.put("i" + c + "deliveryItem"+(it++),deliveryItem);
    						
    					}
    					deliveryItem = rs.getInt("deliveryItem3");
    					if (deliveryItem != -1) {
    						props.put("i" + c + "deliveryItem"+(it++),deliveryItem);
    						
    					}
    					props.put("i" + c + "deliveryItems",it);

    				//	props.put("i" + c + "questPrereq", rs.getInt("questPrereq"));
    				//	props.put("i" + c + "questStartedReq", rs.getInt("questStartedReq"));

						int rewardLevel = 0;
						//props.put("i" + c + "rewardLevels", rewardLevel);
						props.put("i" + c + "completionText" + rewardLevel, HelperFunctions.readEncodedString(rs.getBytes("completionText")));
						props.put("i" + c + "experience" + rewardLevel, rs.getInt("experience"));
						int curr = 0;
						int fac = 0;
						for (int i = 1; i < 3; i++) {
							int currency = rs.getInt("currency" + i);
							if (currency > 0) {
								int currencyCount = rs.getInt("currency" + i + "count");
								props.put("i" + c + "currency" + rewardLevel + "_" + curr, currency);
								props.put("i" + c + "currencyC" + rewardLevel + "_" + (curr++), currencyCount);
							}
							int faction = rs.getInt("rep" + i);
							if (faction > 0) {
								Faction f = Agis.FactionManager.get(faction);
								int repCount = rs.getInt("rep" + i + "gain");
								props.put("i" + c + "faction" + rewardLevel + "_" + fac, f.getName());
								props.put("i" + c + "factionR" + rewardLevel + "_" + (fac++), repCount);
							}
						}

						props.put("i" + c + "currencies" + rewardLevel, curr);
						props.put("i" + c + "factions" + rewardLevel, fac);
						props.put("i" + c + "date", date);

						loadQuestObjectivesPrefabData(c, id, props);
						loadQuestItemsPrefabData(c, id, props);
						c++;

    				}
    			}
			}
		    props.put("num", c);
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		
		return props;
	}
	
	/**
	 * Loading Quests Requirements
	 * @param questID
	 * @return
	 */
	private HashMap<Integer, HashMap<String, Integer>> getQuestRequirements(int questID) {
		HashMap<Integer, HashMap<String, Integer>> requirementMap = new HashMap<Integer, HashMap<String, Integer>>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM quest_requirement where quest_id=" + questID + " AND isactive=1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int requirementType = rs.getInt("editor_option_type_id");
    					String requirementOption = HelperFunctions.readEncodedString(rs.getBytes("editor_option_choice_type_id"));
    					int requiredValue = rs.getInt("required_value");
    					Log.debug("getQuestRequirements qID="+questID+" requirementType="+requirementType+" requirementOption="+requirementOption+" requiredValue="+requiredValue);
    					if (requirementMap.containsKey(requirementType)) {
    						HashMap<String, Integer> requirementOptions = requirementMap.get(requirementType);
    						requirementOptions.put(requirementOption, requiredValue);
    						requirementMap.put(requirementType, requirementOptions);
    					} else {
    						HashMap<String, Integer> requirementOptions = new HashMap<String, Integer>();
    						requirementOptions.put(requirementOption, requiredValue);
    						requirementMap.put(requirementType, requirementOptions);
    					}
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		Log.debug("getQuestRequirements qID="+questID+" requirementMap="+requirementMap);
		
		return requirementMap;
	}

	/**
	 * Loading Quest Objectives
	 * @param q
	 * @param questID
	 */
	public void loadQuestObjectives(AgisBasicQuest q, int questID) {
		int order=0;
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM quest_objectives where questID=" + questID + " AND isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String objectiveType = HelperFunctions.readEncodedString(rs.getBytes("objectiveType"));
    					boolean primaryObjective = rs.getBoolean("primaryObjective");
    					int objectiveLevel = 0;
    					if (!primaryObjective)
    						objectiveLevel = 1;
    					int target = rs.getInt("target");
    				//	String targets = HelperFunctions.readEncodedString(rs.getBytes("targets"));
    					int targetCount = rs.getInt("targetCount");
    					String targetText = HelperFunctions.readEncodedString(rs.getBytes("targetText"));
    					String targets = HelperFunctions.readEncodedString(rs.getBytes("targets"));
    						if (objectiveType.equals("mob")) {
    						AgisBasicQuest.KillGoal goal = new AgisBasicQuest.KillGoal(objectiveLevel, target, targetText, targetCount, order);//,targets);
    						q.addKillGoal(goal);
    					} else if (objectiveType.equals("item")) {
    						AgisBasicQuest.CollectionGoal goal = new AgisBasicQuest.CollectionGoal(objectiveLevel, target, targetText, targetCount,order);
    						q.addCollectionGoal(goal);
    					} else if (objectiveType.equals("mobCategory")) {
    						AgisBasicQuest.CategoryKillGoal goal = new AgisBasicQuest.CategoryKillGoal(objectiveLevel, targets, targetText, targetCount,order);
    						q.addCategoryKillGoal(goal);
    					} else if (objectiveType.equals("task")) {
    						AgisBasicQuest.TaskGoal goal = new AgisBasicQuest.TaskGoal(objectiveLevel, target, targetText, targetCount,order);
    						q.addTaskGoal(goal);
    					}
    						order++;
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
	}
	
	/**
	 * Load Quest Objectives for prefab data
	 * @param c
	 * @param questID
	 * @param props
	 */
	public void loadQuestObjectivesPrefabData(int c, int questID, Map<String, Serializable> props) {
		int order = 0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM quest_objectives where questID=" + questID + " AND isactive = 1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
				
					while (rs.next()) {
						String objectiveType = HelperFunctions.readEncodedString(rs.getBytes("objectiveType"));
						boolean primaryObjective = rs.getBoolean("primaryObjective");
						int objectiveLevel = 0;
						if (!primaryObjective)
							objectiveLevel = 1;
						int target = rs.getInt("target");
						int targetCount = rs.getInt("targetCount");
						String targetText = HelperFunctions.readEncodedString(rs.getBytes("targetText"));
						String targets = HelperFunctions.readEncodedString(rs.getBytes("targets"));
						props.put("i" + c + "objectiveT"+order, objectiveType);
						props.put("i" + c + "objectiveTe"+order, targetText);
						props.put("i" + c + "objectiveC"+order, targetCount);
						//props.put("i" + c + "objectiveO"+order, order);
						order++;
					}
				}
			}
			props.put("i" + c + "objectives" ,order);
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException" + e);
		}
	}
	
	/**
	 * Load Items Rewards for Quests
	 * @param q
	 * @param questID
	 */
	public void loadQuestItems(AgisBasicQuest q, int questID) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM quest_items where quest_id=" + questID /* + " AND isactive = 1" */)) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int rewardLevel = rs.getInt("rewardLevel");
    					int item = rs.getInt("item");
    					int itemCount = rs.getInt("count");
    					boolean choose = rs.getBoolean("choose");
    					Log.debug("QUEST questId=" + questID + " adding to rewards item=" + item + " itemCount=" + itemCount + " choose=" + choose);
    					if (choose) {
    						if (itemCount > 0)
    						q.addRewardToChoose(rewardLevel, item, itemCount);
    					}else {
    						if (itemCount > 0)
    						q.addReward(rewardLevel, item, itemCount);
    					}
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Loading Items Rewards for prefab data of the quests
	 * @param c
	 * @param questID
	 * @param props
	 */
	public void loadQuestItemsPrefabData(int c, int questID, Map<String, Serializable> props) {
		props.put("i" + c + "rewards" + 0, 0);
		props.put("i" + c + "rewardsC" + 0, 0);
		try (PreparedStatement ps = queries.prepare("SELECT * FROM quest_items where quest_id=" + questID )) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					HashMap<Integer, Integer> it = new HashMap<Integer, Integer>();
					HashMap<Integer, Integer> itC = new HashMap<Integer, Integer>();
					while (rs.next()) {
						int rewardLevel = rs.getInt("rewardLevel");
						int item = rs.getInt("item");
						int itemCount = rs.getInt("count");
						boolean choose = rs.getBoolean("choose");
						it.computeIfAbsent(rewardLevel, __ -> 0);
						itC.computeIfAbsent(rewardLevel, __ -> 0);
							Log.debug("QUEST questId=" + questID + " adding to rewards item=" + item + " itemCount=" + itemCount + " choose=" + choose);
						if (!choose) {
							if (itemCount > 0) {
								props.put("i" + c + "reward" + rewardLevel + "_" + it.get(rewardLevel), item);
								props.put("i" + c + "rewardc" + rewardLevel + "_" + it.get(rewardLevel), itemCount);
								it.put(rewardLevel, it.get(rewardLevel) + 1);
							}
						} else {
							if (itemCount > 0) {
								props.put("i" + c + "rewardC" + rewardLevel + "_" + itC.get(rewardLevel), item);
								props.put("i" + c + "rewardCc" + rewardLevel + "_" + itC.get(rewardLevel), itemCount);
								itC.put(rewardLevel, itC.get(rewardLevel) + 1);
							}
						}
					}
					for (Entry<Integer, Integer> e : it.entrySet()) {
						props.put("i" + c + "rewards" + e.getKey(), e.getValue());
					}
					for (Entry<Integer, Integer> e : itC.entrySet()) {
						props.put("i" + c + "rewardsC" + e.getKey(), e.getValue());
					}

				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	

	/**
	 * Loads in the list of currencies from the database.
	 * @param category
	 * @return
	 */
	public HashMap<String, Serializable> loadCurrenciesPrefabData() {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int c = 0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `currencies` where isactive = 1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					// Currency newCurrency = new Currency();
    					int currencyID = rs.getInt("id");
    					Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
    					long date = 0L;
    					if (taxPaidUntil != null) {
    						date = taxPaidUntil.getTime();
    					}
    					Log.debug("loadCurrenciesPrefabData: id=" + currencyID + " new ");
    					String currencyName = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					String currencyIcon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
    					String currencyIcon2 = HelperFunctions.readEncodedString(rs.getBytes("icon2"));
    						String currencyDescription = HelperFunctions.readEncodedString(rs.getBytes("description"));
    					if (currencyDescription == null)
    						currencyDescription = "";
    					long currencyMax = rs.getLong("maximum");
    					int currencyGroup = rs.getInt("currencyGroup");
    					int currencyPosition = rs.getInt("currencyPosition");
    					boolean external = rs.getBoolean("external");
    
    					props.put("i" + c + "date", date);
    					props.put("i" + c + "id", currencyID);
    					props.put("i" + c + "name", currencyName);
    					props.put("i" + c + "icon", currencyIcon);
    					props.put("i" + c + "icon2", currencyIcon2);
    					props.put("i" + c + "desc", currencyDescription);
    					props.put("i" + c + "max", currencyMax);
    					props.put("i" + c + "group", currencyGroup);
    					props.put("i" + c + "pos", currencyPosition);
    					loadCurrencyConversionsPrefabData(currencyID, props, c);
    					c++;
    				}
    			}
			}
			props.put("num", c);
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException" + e);
		}
		return props;
	}
	

	@Deprecated
	public Map<String, Serializable> loadIconsCurrenciesPrefabData(String objs) {
		ArrayList<Currency> currencies = new ArrayList<Currency>();
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		//props.put("ext_msg_subtype", "InvCurrIcon");
		int c=0;
		int count = 0;
		String sql ="SELECT id,icon,icon2 FROM `currencies` where isactive = 1 and id in ("+objs+")";
		Log.debug("loadIconsCurrenciesPrefabData objs="+objs+"  sql="+sql);
		try (PreparedStatement ps = queries.prepare(sql)) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					//Currency newCurrency = new Currency();
    					int id = rs.getInt("id");
    					String icon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
    					String icon2 = HelperFunctions.readEncodedString(rs.getBytes("icon2"));
    					count += icon2.length();
    					Log.debug("loadIconsCurrenciesPrefabData id="+id+" icon length="+icon2.length());
    					props.put("i"+c+"id", id);
    					props.put("i"+c+"icon2", icon2);
    					props.put("i"+c+"icon", icon);
    					c++;	
    				/*	if(count > 100000) {
    						props.put("all", false);
    						props.put("num",c);
    						Log.error("loadIconsCurrenciesPrefabData send part message");
    						TargetedExtensionMessage emsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
    						Engine.getAgent().sendBroadcast(emsg);
    						props.clear();
    						props.put("ext_msg_subtype", "InvCurrIcon");
    						count=0;
    						c=0;
    					}*/
    				}
    			}
			}
			props.put("num",c);
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		props.put("all", true);
	/*	TargetedExtensionMessage emsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(emsg);*/
		
		
		return props;
	}
	@Deprecated
	public Map<String, Serializable> loadCurrenciesPrefabData(HashMap<Integer, Long> dates) {
		ArrayList<Currency> currencies = new ArrayList<Currency>();
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(dates); 
		
		int c=0;
			//if (category == -1)
			//	ps = queries.prepare("SELECT * FROM `currencies`");
			//else
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `currencies` where isactive = 1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					// Currency newCurrency = new Currency();
    					int currencyID = rs.getInt("id");
    					Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
    					long date = 0L;
    					if (taxPaidUntil != null) {
    						date = taxPaidUntil.getTime();
    					}
    					if (dates.containsKey(currencyID)) {
    						objs.remove(currencyID);
    						if (date != dates.get(currencyID)) {
    							Log.debug("loadCurrenciesPrefabData: id=" + currencyID+" newer date" );
    							String currencyName = HelperFunctions.readEncodedString(rs.getBytes("name"));
    							String currencyIcon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
    							String currencyDescription = HelperFunctions.readEncodedString(rs.getBytes("description"));
    							if (currencyDescription == null)
    								currencyDescription = "";
    							long currencyMax = rs.getLong("maximum");
    							int currencyGroup = rs.getInt("currencyGroup");
    							int currencyPosition = rs.getInt("currencyPosition");
    							boolean external = rs.getBoolean("external");
    
    							props.put("i" + c + "date", date);
    							props.put("i" + c + "id", currencyID);
    							props.put("i" + c + "name", currencyName);
    							// props.put("i"+c+"icon", currencyIcon);
    							props.put("i" + c + "desc", currencyDescription);
    							props.put("i" + c + "max", currencyMax);
    							props.put("i" + c + "group", currencyGroup);
    							props.put("i" + c + "pos", currencyPosition);
    							// Currency newCurrency = new Currency();
    							// newCurrency.setCurrencyID(currencyID);
    							loadCurrencyConversionsPrefabData(currencyID, props, c);
    							c++;
    						}
    					} else {
    						Log.debug("loadCurrenciesPrefabData: id=" + currencyID+" new " );
    						
    						String currencyName = HelperFunctions.readEncodedString(rs.getBytes("name"));
    						String currencyIcon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
    						String currencyDescription = HelperFunctions.readEncodedString(rs.getBytes("description"));
    						if (currencyDescription == null)
    							currencyDescription = "";
    						long currencyMax = rs.getLong("maximum");
    						int currencyGroup = rs.getInt("currencyGroup");
    						int currencyPosition = rs.getInt("currencyPosition");
    						boolean external = rs.getBoolean("external");
    
    						props.put("i" + c + "date", date);
    						props.put("i" + c + "id", currencyID);
    						props.put("i" + c + "name", currencyName);
    						// props.put("i"+c+"icon", currencyIcon);
    						props.put("i" + c + "desc", currencyDescription);
    						props.put("i" + c + "max", currencyMax);
    						props.put("i" + c + "group", currencyGroup);
    						props.put("i" + c + "pos", currencyPosition);
    						// Currency newCurrency = new Currency();
    						// newCurrency.setCurrencyID(currencyID);
    						loadCurrencyConversionsPrefabData(currencyID, props, c);
    						c++;
    					}
    					if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
    						props.put("all", false);
    						props.put("num", c);
    						return props;
    					}
    				}
    			}
			}
			if(objs.size()>0) {
				StringJoiner sj = new StringJoiner(";");
				for (Integer s : objs.keySet()) {
					sj.add(s.toString());
				}
				props.put("toRemove",sj.toString());
			}
			props.put("num", c);
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException" + e);
		}
		
		
		return props;
	}
	/**
	 * Loads in conversion information from one currency to others
	 * @param currencyId
	 * @return
	 */
	public void loadCurrencyConversionsPrefabData(int currencyId, Map<String, Serializable> props, int c) {
		boolean found = false;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `currency_conversion` where currencyID=" + currencyId + " AND isactive = 1 limit 1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int currencyToID = rs.getInt("currencyToID");
    					int amountReq = rs.getInt("amount");
    					props.put("i"+c+"convId",currencyToID);
    					props.put("i"+c+"convAmo",amountReq);
    					found = true;
    				}
    			}
			}
			if(!found) {
				props.put("i"+c+"convId",-1);
				props.put("i"+c+"convAmo",1);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		
	}
	
	
	
	/**
	 * Loads in the list of currencies from the database.
	 * @param category
	 * @return
	 */
	public ArrayList<Currency> loadCurrencies(int category) {
		ArrayList<Currency> currencies = new ArrayList<Currency>();
			//if (category == -1)
			//	ps = queries.prepare("SELECT * FROM `currencies`");
			//else
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `currencies` where isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Currency newCurrency = new Currency();
    					int currencyID = rs.getInt("id");
    					newCurrency.setCurrencyID(currencyID);
    					String currencyName = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					newCurrency.setCurrencyName(currencyName);
    					String currencyIcon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
    					newCurrency.setCurrencyIcon(currencyIcon);
    					String currencyDescription = HelperFunctions.readEncodedString(rs.getBytes("description"));
    					newCurrency.setCurrencyDescription(currencyDescription);
    					long currencyMax = rs.getLong("maximum");
    					newCurrency.setCurrencyMax(currencyMax);
    					boolean external = rs.getBoolean("external");
    					newCurrency.setExternal(external);
    					currencies.add(newCurrency);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		
		for (Currency c : currencies) {
			loadCurrencyConversions(c);
		}
		
		return currencies;
	}
	
	/**
	 * Loads in conversion information from one currency to others
	 * @param currencyId
	 * @return
	 */
	public void loadCurrencyConversions(Currency currency) {
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `currency_conversion` where currencyID=" + currency.getCurrencyID() + " AND isactive = 1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int currencyToID = rs.getInt("currencyToID");
    					int amountReq = rs.getInt("amount");
    					boolean autoConversion = rs.getBoolean("autoConverts");
    					currency.addConversionOption(currencyToID, amountReq, autoConversion);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
	}
	
	public int writeQuest(int category, AgisBasicQuest q) {
		Log.debug("Writing quest data to database");
		int questPrereq = -1;
		if (q.getQuestPrereqs().size() > 0)
			questPrereq = q.getQuestPrereqs().get(0);
		int inserted = -1;
		String tableName = "quests";
		String columnNames = "category,name,faction,numGrades,repeatable,description,objectiveText,progressText,deliveryItem1,questPrereq,levelReq";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames 
					+ ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
			stmt.setInt(1, category);
			stmt.setString(2, q.getName());
			stmt.setInt(3, q.getFaction());
			stmt.setInt(4, q.getSecondaryGrades());
			stmt.setBoolean(5, q.getRepeatable());
			stmt.setString(6, q.getDesc());
			stmt.setString(7, q.getObjective());
			stmt.setString(8, q.getProgressText());
			stmt.setInt(9, q.getDeliveryItems().get(0));
			stmt.setInt(10, questPrereq);
			stmt.setInt(11, q.getQuestLevelReq());
			inserted = queries.executeInsert(stmt);
		} catch (SQLException e) {
			Log.dumpStack(" SQLException"+e);
			return -1;
		}
		if (inserted == -1)
			return inserted;
		
		writeQuestObjectives(inserted, q);
		// Write the base for the rewards entry
		tableName = "questRewards";
		columnNames = "questID,rewardLevel,completionText,experience";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames 
					+ ") values (?, ?, ?, ?)")) {
			stmt.setInt(1, inserted);
			stmt.setInt(2, 0);
			stmt.setString(3, q.getCompletionText().get(0));
			stmt.setInt(4, q.getXpReward().get(0));
			queries.executeInsert(stmt);
		} catch (SQLException e) {
			Log.dumpStack(" SQLException"+e);
			return -1;
		}
		// Now update it with item/currency/rep rewards
		writeQuestRewards(inserted, q);
		
		Log.debug("Wrote quest data to database");
		return inserted;
	}
	
	public int editQuest(int questID, AgisBasicQuest q) {
		Log.debug("Writing quest data to database");
		int questPrereq = -1;
		if (q.getQuestPrereqs().size() > 0)
			questPrereq = q.getQuestPrereqs().get(0);
		String tableName = "quests";
		int updated;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set name=?, faction=?, numGrades=?, " 
					+ "repeatable=?, description=?, objectiveText=?, progressText=?, deliveryItem1=?, "
					+ "questPrereq=?, levelReq=? where id=?")) {
			stmt.setString(1, q.getName());
			stmt.setInt(2, q.getFaction());
			stmt.setInt(3, q.getSecondaryGrades());
			stmt.setBoolean(4, q.getRepeatable());
			stmt.setString(5, q.getDesc());
			stmt.setString(6, q.getObjective());
			stmt.setString(7, q.getProgressText());
			stmt.setInt(8, q.getDeliveryItems().get(0));
			stmt.setInt(9, questPrereq);
			stmt.setInt(10, q.getQuestLevelReq());
			stmt.setInt(11, questID);
			Log.debug("QUESTDB: updating quest with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack(" SQLException"+e);
			return -1;
		}
		if (updated == -1)
			return updated;
		
		// To handle objectives we first delete all the existing objectives then insert new ones
		tableName = "questObjectives";
		String deleteString = "DELETE FROM `" + tableName + "` WHERE questID = " + questID;
		queries.executeUpdate(deleteString);

		writeQuestObjectives(questID, q);
		
		// Write in new progress text etc.
		tableName = "questRewards";
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set completionText = ?, experience = ? where questID=? AND rewardLevel=0")) {
			stmt.setString(1, q.getCompletionText().get(0));
			stmt.setInt(2, q.getXpReward().get(0));
			stmt.setInt(3, questID);
			Log.debug("QUESTDB: placing item stmt=" + stmt.toString());
			queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack(" SQLException"+e);
			return -1;
		}
		writeQuestRewards(questID, q);
		
		Log.debug("Wrote quest data to database");
		return updated;
	}
	
	public void writeQuestObjectives(int questID, AgisBasicQuest q) {
		String tableName = "questObjectives";
		List<CollectionGoal> cGoals = q.getCollectionGoals();
		for (int i = 1; i <= cGoals.size(); i++) {
			CollectionGoal cGoal = cGoals.get(i-1);
            String columnNames = "questID,primaryObjective,objectiveType,target,targetCount,targetText";
			try (PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames 
						+ ") values (?, ?, ?, ?, ?, ?)")) {
				stmt.setInt(1, questID);
				stmt.setInt(2, 1);
				stmt.setString(3, "item");
				stmt.setInt(4, cGoal.templateID);
				stmt.setInt(5, cGoal.num);
				stmt.setString(6, cGoal.templateName);
				queries.executeInsert(stmt);
			} catch (SQLException e) {
				Log.dumpStack(" SQLException"+e);
				return;
			}
		}
		List<KillGoal> kGoals = q.getKillGoals();
		for (int i = 1; i <= kGoals.size(); i++) {
			KillGoal kGoal = kGoals.get(i-1);
            String columnNames = "questID,primaryObjective,objectiveType,target,targetCount,targetText";			
			try (PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames 
						+ ") values (?, ?, ?, ?, ?, ?)")) {
				stmt.setInt(1, questID);
				stmt.setInt(2, 1);
				stmt.setString(3, "mob");
				stmt.setInt(4, kGoal.mobID);
				stmt.setInt(5, kGoal.num);
				stmt.setString(6, kGoal.mobName);
				queries.executeInsert(stmt);
			} catch (SQLException e) {
				Log.dumpStack(" SQLException"+e);
				return;
			}
		}
	}
	
	public void writeQuestRewards(int questID, AgisBasicQuest q) {
		String tableName = "questRewards";
		int numRewards = 1;
		if (q.getRewards().containsKey(0)) {
			HashMap<Integer, Integer> rewards = q.getRewards().get(0);
			for (int item: rewards.keySet()) {
			    try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set item" + numRewards + " = ?, item" + numRewards 
							+ "count = ? where questID=? AND rewardLevel=0")) {
					stmt.setInt(1, item);
					stmt.setInt(2, rewards.get(item));
					stmt.setInt(3, questID);
					Log.debug("QUESTDB: placing item stmt=" + stmt.toString());
					queries.executeUpdate(stmt);
				} catch (SQLException e) {
					Log.dumpStack(" SQLException"+e);
					return;
				}
				numRewards++;
			}
		}
		if (q.getRewardsToChoose().containsKey(0)) {
			HashMap<Integer, Integer> rewards = q.getRewardsToChoose().get(0);
			numRewards = 1;
			for (int item: rewards.keySet()) {
			    try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set itemToChoose" + numRewards 
							+ " = ?, itemToChoose" + numRewards + "count = ? where questID=? AND rewardLevel=0")) {
					stmt.setInt(1, item);
					stmt.setInt(2, rewards.get(item));
					stmt.setInt(3, questID);
					Log.debug("QUESTDB: placing itemToChoose stmt=" + stmt.toString());
					queries.executeUpdate(stmt);
				} catch (SQLException e) {
					Log.dumpStack(" SQLException"+e);
					return;
				}
				numRewards++;
			}
		}
		HashMap<Integer, Integer> currencyRewards = q.getCurrencyRewards().get(0);
		int numCurrencyRewards = 1;
		for (int currency: currencyRewards.keySet()) {
		    try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set currency" + numCurrencyRewards 
						+ " = ?, currency" + numCurrencyRewards + "count = ? where questID=? AND rewardLevel=0")) {
				stmt.setInt(1, currency);
				stmt.setInt(2, currencyRewards.get(currency));
				stmt.setInt(3, questID);
				Log.debug("QUESTDB: placing currency stmt=" + stmt.toString());
				queries.executeUpdate(stmt);
			} catch (SQLException e) {
				Log.dumpStack(" SQLException"+e);
				return;
			}
			numCurrencyRewards++;
		}
		HashMap<Integer, Integer> repRewards = q.getRepRewards().get(0);
		int numRepRewards = 1;
		for (int faction: repRewards.keySet()) {
		    try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set reputation" + numRepRewards 
						+ " = ?, reputation" + numRepRewards + "count = ? where questID=? AND rewardLevel=0")) {
				stmt.setInt(1, faction);
				stmt.setInt(2, repRewards.get(faction));
				stmt.setInt(3, questID);
				Log.debug("QUESTDB: placing rep stmt=" + stmt.toString());
				queries.executeUpdate(stmt);
			} catch (SQLException e) {
				Log.dumpStack(" SQLException"+e);
				return;
			}
			numRepRewards++;
		}
	}

	public HashMap<Integer, PetProfile> LoadPetProfiles(){
		HashMap<Integer,PetProfile> list = new HashMap<>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `pet_profile` where isactive = 1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						int id = rs.getInt("id");
						String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
						PetProfile pp = new PetProfile(id);
						pp.setName(name);
						pp.SetLevels(LoadPetProfileLevels(pp));
						list.put(id,pp);
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		return list;
	}

	private HashMap<Integer, PetProfileLevel> LoadPetProfileLevels(PetProfile pp) {
		HashMap<Integer,PetProfileLevel> list = new HashMap<>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `pet_profile_level` where profile_id="+pp.getId()+" order by level ASC")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						int id = rs.getInt("id");
//						String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
						int level = rs.getInt("level");
						PetProfileLevel ppl = new PetProfileLevel(id,level);
						ppl.setExperience(rs.getInt("exp"));
						ppl.setTemplateId(rs.getInt("template_id"));
						String effect = HelperFunctions.readEncodedString(rs.getBytes("coordEffect"));
						ppl.setLevelUpCoordEffect(effect);
						ppl.setSlotsProfileId(rs.getInt("slot_profile_id"));
						list.put(level,ppl);
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		return list;

	}


	public int writeSpawnData(SpawnData sd, Point loc, Quaternion orient, BehaviorTemplate behavTmpl, int instanceID) {
		Log.debug("Writing spawn data to database");
		String startsQuests = "";
		for (int questID : behavTmpl.getStartsQuests()) {
			Log.debug("Starts quests:" + behavTmpl.getStartsQuests());
			startsQuests += "" + questID + ",";
		}
		String endsQuests = "";
		for (int questID : behavTmpl.getEndsQuests()) {
			Log.debug("Ends quests:" + behavTmpl.getEndsQuests());
			endsQuests += "" + questID + ",";
		}
		String startsDialogues = "";
		for (int dialogueID : behavTmpl.getStartsDialogues()) {
			startsDialogues += "" + dialogueID + ",";
		}
		String otherActions = "";
		for (String action : behavTmpl.getOtherActions()) {
			otherActions += "" + action + ",";
		}
		int inserted = -1;
        String tableName = "spawn_data";
        String columnNames = "category,name,mobTemplate,markerName,locX,locY,locZ,orientX,orientY,orientZ,orientW,instance,numSpawns," + 
        "spawnRadius,respawnTime,corpseDespawnTime,spawnActiveStartHour,spawnActiveEndHour,alternateSpawnMobTemplate,combat,roamRadius,startsQuests,"
        + "endsQuests,startsDialogues,otherActions,baseAction,weaponSheathed,merchantTable,patrolPath,questOpenLootTable,isChest,pickupItem"
        +",mobTemplate2,mobTemplate3,mobTemplate4,mobTemplate5,alternateSpawnMobTemplate2,alternateSpawnMobTemplate3,alternateSpawnMobTemplate4,alternateSpawnMobTemplate5,respawnTimeMax,roamDelayMin,roamDelayMax,roamRollTimeEachTime";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames 
				+ ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
			stmt.setInt(1, sd.getCategory());
			stmt.setString(2, "spawn");
			//stmt.setInt(3, sd.getTemplateID());
			stmt.setString(4, "");
			stmt.setFloat(5, loc.getX());
			stmt.setFloat(6, loc.getY());
			stmt.setFloat(7, loc.getZ());
			stmt.setFloat(8, orient.getX());
			stmt.setFloat(9, orient.getY());
			stmt.setFloat(10, orient.getZ());
			stmt.setFloat(11, orient.getW());
			stmt.setInt(12, instanceID);
			stmt.setInt(13, sd.getNumSpawns());
			stmt.setInt(14, sd.getSpawnRadius());
			stmt.setInt(15, sd.getRespawnTime());
			stmt.setInt(16, sd.getCorpseDespawnTime());
			if (sd.getProperty("spawnActiveStartHour") != null) {
				stmt.setInt(17, (Integer)sd.getProperty("spawnActiveStartHour"));
			} else {
				stmt.setInt(17, -1);
			}
			if (sd.getProperty("spawnActiveEndHour") != null) {
				stmt.setInt(18, (Integer)sd.getProperty("spawnActiveEndHour"));
			} else {
				stmt.setInt(18, -1);
			}
			/*if (sd.getProperty("alternateSpawnMobTemplate") != null) {
				stmt.setInt(19, (Integer)sd.getProperty("alternateSpawnMobTemplate"));
			} else {
				stmt.setInt(19, -1);
			}*/
			if(sd.getTemplateIDs().size()>0) {
				ArrayList<Integer> list = sd.getTemplates();
				stmt.setInt(3, list.get(0));
				if(list.size()>1)
					stmt.setInt(33, list.get(1));
				else
					stmt.setInt(33, -1);
				if(list.size()>2)
					stmt.setInt(34, list.get(2));
				else
					stmt.setInt(34, -1);
				if(list.size()>3)
					stmt.setInt(35, list.get(3));
				else
					stmt.setInt(35, -1);
				if(list.size()>4)
					stmt.setInt(36, list.get(4));
				else
					stmt.setInt(36, -1);
			}else {
				stmt.setInt(3, -1);
				stmt.setInt(33, -1);
				stmt.setInt(34, -1);
				stmt.setInt(35, -1);
				stmt.setInt(36, -1);
			}
	
			if(sd.getTemplateAlterIDs().size()>0) {
				ArrayList<Integer> list = sd.getTemplatesAlter();
				stmt.setInt(19, list.get(0));
				if(list.size()>1)
					stmt.setInt(37, list.get(1));
				else
					stmt.setInt(37, -1);
				if(list.size()>2)
					stmt.setInt(38, list.get(2));
				else
					stmt.setInt(38, -1);
				if(list.size()>3)
					stmt.setInt(39, list.get(3));
				else
					stmt.setInt(39, -1);
				if(list.size()>4)
					stmt.setInt(40, list.get(4));
				else
					stmt.setInt(40, -1);
			}else {
				stmt.setInt(19, -1);
				stmt.setInt(37, -1);
				stmt.setInt(38, -1);
				stmt.setInt(39, -1);
				stmt.setInt(40, -1);
			}
			stmt.setInt(41, sd.getRespawnTimeMax());
			stmt.setBoolean(20, behavTmpl.getHasCombat());
			stmt.setInt(21, behavTmpl.getRoamRadius());
			stmt.setString(22, startsQuests);
			stmt.setString(23, endsQuests);
			stmt.setString(24, startsDialogues);
			stmt.setString(25, otherActions);
			stmt.setString(26, behavTmpl.getBaseAction());
			stmt.setBoolean(27, behavTmpl.getWeaponsSheathed());
			stmt.setInt(28, behavTmpl.getMerchantTable());
			stmt.setInt(29, behavTmpl.getPatrolPathID());
			stmt.setInt(30, behavTmpl.getQuestOpenLoot());
			stmt.setBoolean(31, behavTmpl.getIsChest());
			stmt.setInt(32, behavTmpl.getPickupItem());
			stmt.setFloat(42, behavTmpl.getRoamDelayMin());
			stmt.setFloat(43, behavTmpl.getRoamDelayMax());
			stmt.setBoolean(44, behavTmpl.getRoamRollTimeEachTime());
			Log.debug("Spawn Data statement = " + stmt.toString());
			inserted = queries.executeInsert(stmt);
			Log.debug("Spawn Data statement inserted = " + inserted);
		} catch (SQLException e) {
		    Log.error("Failed to write spawn data to database with id= " + inserted);
		    Log.dumpStack(" SQLException"+e);
		    return -1;
		}
		Log.debug("Wrote spawn data to database with id= " + inserted);
		return inserted;
	}
	
	public int editSpawnData(SpawnData sd, int spawnID, Point loc, Quaternion orient, BehaviorTemplate behavTmpl) {
		Log.debug("Editing spawn data to database");
		int updated;
		String startsQuests = "";
		for (int questID : behavTmpl.getStartsQuests()) {
			Log.debug("Starts quests:" + behavTmpl.getStartsQuests());
			startsQuests += "" + questID + ",";
		}
		String endsQuests = "";
		for (int questID : behavTmpl.getEndsQuests()) {
			Log.debug("Ends quests:" + behavTmpl.getEndsQuests());
			endsQuests += "" + questID + ",";
		}
		String startsDialogues = "";
		for (int dialogueID : behavTmpl.getStartsDialogues()) {
			startsDialogues += "" + dialogueID + ",";
		}
		String otherActions = "";
		for (String action : behavTmpl.getOtherActions()) {
			otherActions += "" + action + ",";
		}
		String tableName = "spawn_data";
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set mobTemplate=?, locX=?, locY=?, locZ=?, orientX=?, orientY=?, "
					+ "orientZ=?, orientW=?, numSpawns=?, spawnRadius=?, respawnTime=?, corpseDespawnTime=?, spawnActiveStartHour=?, "
					+ "spawnActiveEndHour=?, alternateSpawnMobTemplate=?, combat=?, roamRadius=?, startsQuests=?, endsQuests=?, startsDialogues=?, "
					+ "otherActions=?, baseAction=?, weaponSheathed=?, merchantTable=?, patrolPath=?, questOpenLootTable=?, isChest=?, "
					+ "pickupItem=? "
					+",mobTemplate2=?,mobTemplate3=?,mobTemplate4=?,mobTemplate5=?,alternateSpawnMobTemplate2=?,alternateSpawnMobTemplate3=?,alternateSpawnMobTemplate4=?,alternateSpawnMobTemplate5=?,respawnTimeMax=?"
				    +",roamDelayMin=?, roamDelayMax=?, roamRollTimeEachTime=?"
					+" where id=?")) {
			//stmt.setInt(1, sd.getTemplateID());
			stmt.setFloat(2, loc.getX());
			stmt.setFloat(3, loc.getY());
			stmt.setFloat(4, loc.getZ());
			stmt.setFloat(5, orient.getX());
			stmt.setFloat(6, orient.getY());
			stmt.setFloat(7, orient.getZ());
			stmt.setFloat(8, orient.getW());
			stmt.setInt(9, sd.getNumSpawns());
			stmt.setInt(10, sd.getSpawnRadius());
			stmt.setInt(11, sd.getRespawnTime());
			stmt.setInt(12, sd.getCorpseDespawnTime());
			if (sd.getProperty("spawnActiveStartHour") != null) {
				stmt.setInt(13, (Integer)sd.getProperty("spawnActiveStartHour"));
			} else {
				stmt.setInt(13, -1);
			}
			if (sd.getProperty("spawnActiveEndHour") != null) {
				stmt.setInt(14, (Integer)sd.getProperty("spawnActiveEndHour"));
			} else {
				stmt.setInt(14, -1);
			}
			if (sd.getProperty("alternateSpawnMobTemplate") != null) {
				stmt.setInt(15, (Integer)sd.getProperty("alternateSpawnMobTemplate"));
			} else {
				stmt.setInt(15, -1);
			}
			stmt.setBoolean(16, behavTmpl.getHasCombat());
			stmt.setInt(17, behavTmpl.getRoamRadius());
			stmt.setString(18, startsQuests);
			stmt.setString(19, endsQuests);
			stmt.setString(20, startsDialogues);
			stmt.setString(21, otherActions);
			stmt.setString(22, behavTmpl.getBaseAction());
			stmt.setBoolean(23, behavTmpl.getWeaponsSheathed());
			stmt.setInt(24, behavTmpl.getMerchantTable());
			stmt.setInt(25, behavTmpl.getPatrolPathID());
			stmt.setInt(26, behavTmpl.getQuestOpenLoot());
			stmt.setBoolean(27, behavTmpl.getIsChest());
			stmt.setInt(28, behavTmpl.getPickupItem());
			
			if(sd.getTemplateIDs().size()>0) {
				ArrayList<Integer> list = sd.getTemplates();
				stmt.setInt(1, list.get(0));
				if(list.size()>1)
					stmt.setInt(29, list.get(1));
				else
					stmt.setInt(29, -1);
				if(list.size()>2)
					stmt.setInt(30, list.get(2));
				else
					stmt.setInt(30, -1);
				if(list.size()>3)
					stmt.setInt(31, list.get(3));
				else
					stmt.setInt(31, -1);
				if(list.size()>4)
					stmt.setInt(32, list.get(4));
				else
					stmt.setInt(32, -1);
			}else {
				stmt.setInt(1, -1);
				stmt.setInt(29, -1);
				stmt.setInt(30, -1);
				stmt.setInt(31, -1);
				stmt.setInt(32, -1);
			}
	
			if(sd.getTemplateAlterIDs().size()>0) {
				ArrayList<Integer> list = sd.getTemplatesAlter();
				stmt.setInt(15, list.get(0));
				if(list.size()>1)
					stmt.setInt(33, list.get(1));
				else
					stmt.setInt(33, -1);
				if(list.size()>2)
					stmt.setInt(34, list.get(2));
				else
					stmt.setInt(34, -1);
				if(list.size()>3)
					stmt.setInt(35, list.get(3));
				else
					stmt.setInt(35, -1);
				if(list.size()>4)
					stmt.setInt(36, list.get(4));
				else
					stmt.setInt(36, -1);
			}else {
				stmt.setInt(15, -1);
				stmt.setInt(33, -1);
				stmt.setInt(34, -1);
				stmt.setInt(35, -1);
				stmt.setInt(36, -1);
			}
			stmt.setInt(37, sd.getRespawnTimeMax());
			stmt.setFloat(38, behavTmpl.getRoamDelayMin());
			stmt.setFloat(39, behavTmpl.getRoamDelayMax());
			stmt.setBoolean(40, behavTmpl.getRoamRollTimeEachTime());
			stmt.setInt(41, spawnID);
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			e.printStackTrace();
			updated = -1;
			Log.dumpStack(" SQLException"+e);
		}
		
		Log.debug("Edited spawn data to database");
		return updated;
	}
	
	public void deleteSpawnData(int spawnID) {
		String tableName = "spawn_data";
		String deleteString = "DELETE FROM `" + tableName + "` WHERE id = " + spawnID;
		queries.executeUpdate(deleteString);
	}
	
	public int writePatrolPath(ArrayList<PatrolPoint> points, boolean travelReverse,String name) {
		String tableName = "patrol_path";
		int lastInsertedID = -1;
		for (int i = points.size()-1; i >= 0; i--) {
			points.get(i).nextPoint = lastInsertedID;
			String columnNames = "name,startingPoint,travelReverse,locX,locY,locZ,lingerTime,nextPoint";
			try (PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames 
						+ ") values (?, ?, ?, ?, ?, ?, ?, ?)")) {
				stmt.setString(1, name);
				stmt.setBoolean(2, i == 0 ? true : false);
				stmt.setBoolean(3, travelReverse);
				stmt.setFloat(4, points.get(i).loc.getX());
				stmt.setFloat(5, points.get(i).loc.getY());
				stmt.setFloat(6, points.get(i).loc.getZ ());
				stmt.setFloat(7, points.get(i).lingerTime);
				stmt.setInt(8, points.get(i).nextPoint);
				lastInsertedID = queries.executeInsert(stmt);
				points.get(i).id = lastInsertedID;
			} catch (SQLException e) {
				Log.dumpStack(" SQLException"+e);
				return -1;
			}
		}
		return lastInsertedID;
	}
	
	public int writeNpcDisplayData(String name, String race, String gender) {
		Log.debug("Writing npc appearance data to database");
		String tableName = "npcDisplay";
		String columnNames = "name,race,gender,skinColour";
		String values = "'" + name + "','" + race + "','" + gender;
		String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
		int inserted = queries.executeInsert(insertString);
		Log.debug("Wrote npc appearance data to database");
		return inserted;
	}
	
	public int writeMobData(int category, String name, String subtitle, int mobType, int soundSet, 
			LinkedList<Integer> displays, int animState, float scale, int offset, int hitBox, int runThreshold, String gender,
			int level, boolean attackable, int faction, String species, String subSpecies, String questCategory) {
		Log.debug("Writing mob data to database");
		// Set up the displays
		int inserted = -1;
		String tableName = "mob_templates";
		String columnNames = "category,name,subTitle,mobType,soundSet,display1,display2,display3,display4,baseAnimationState,scale," +
		"overheadOffset,hitBox,runThreshold,gender,level,attackable,faction,species,subSpecies,questCategory";
	    try (PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames 
				+ ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
			stmt.setInt(1, category);
			stmt.setString(2, name);
			stmt.setString(3, subtitle);
			stmt.setInt(4, mobType);
			stmt.setInt(5, soundSet);
			for (int i = 0; i <= 4; i++) {
				if (displays.size() > i)
					stmt.setInt(6 + i, displays.get(i));
				else
					stmt.setInt(6 + i, -1);
			}
			stmt.setInt(10, animState);
			stmt.setFloat(11, scale);
			stmt.setInt(12, offset);
			stmt.setInt(13, hitBox);
			stmt.setInt(14, runThreshold);
			stmt.setString(15, gender);
			stmt.setInt(16, level);
			stmt.setBoolean(17, attackable);
			stmt.setInt(18, faction);
			stmt.setString(19, species);
			stmt.setString(20, subSpecies);
			stmt.setString(21, questCategory);
			inserted = queries.executeInsert(stmt);
		} catch (SQLException e) {
			Log.dumpStack(" SQLException"+e);
		}
		Log.debug("Wrote mob data to database");
		return inserted;
	}
	
	public int writeMobCombatData(int mobID, int health, String attackType) {
		Log.debug("Writing mob combat data to database");
		String tableName = "mobCombatStats";
		String columnNames = "id,health,attackType";
		String values = mobID + "," + health + ",'" + attackType + "'";
		String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
		int inserted = queries.executeInsert(insertString);
		Log.debug("Wrote mob combat data to database");
		return inserted;
	}
	
	public int writeMobEquipmentData(int category, int mobID, int item) {
		Log.debug("Writing mob equip data to database");
		String tableName = "mobEquipment";
		String columnNames = "category,mobTemplate,item";
		String values = category + "," + mobID + "," + item;
		String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
		int inserted = queries.executeInsert(insertString);
		Log.debug("Wrote mob equip data to database");
		return inserted;
	}
	
	public void writeMobLootTables(int category, int mobTemplate, HashMap<Integer, Float> lootTables) {
		Log.debug("Writing mob loot tables to database");
		String tableName = "mobLoot";
		// First delete all entries for this mob. I know this is not very efficient for the 
		// database but I don't have time to write it properly
		String deleteString = "DELETE FROM `" + tableName + "` WHERE mobTemplate = " + mobTemplate;
		queries.executeUpdate(deleteString);
		
		String columnNames = "category,mobTemplate,lootTable,dropChance";
		for (int tableID : lootTables.keySet()) {
		    try (PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames 
					+ ") values (?, ?, ?, ?)")) {
				stmt.setInt(1, category);
				stmt.setInt(2, mobTemplate);
				stmt.setInt(3, tableID);
				stmt.setFloat(4, lootTables.get(tableID));
				queries.executeInsert(stmt);
			} catch (SQLException e) {
				Log.dumpStack(" SQLException"+e);
			}
		}
		Log.debug("Wrote mob loot tables to database");
		return;
	}
	
	public int editMobData(int templateID, String name, String subtitle, int mobType, int soundSet, 
			LinkedList<Integer> displays, int animState, float scale, int offset, int hitBox, int runThreshold, String gender,
			int level, boolean attackable, int faction, String species, String subSpecies, String questCategory) {
		Log.debug("Writing mob data to database");
		int updated = -1;
		String tableName = "mobTemplates";
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set name=?, subTitle=?, mobType=?, soundSet=?, display1=?,"
					+ "display2=?, display3=?, display4=?, baseAnimationState=?, scale=?, overheadOffset=?, hitBox=?,"
					+ "runThreshold=?, gender=?, level=?, attackable=?, faction=?, species=?, subSpecies=?, questCategory=? where id=?")) {
			stmt.setString(1, name);
			stmt.setString(2, subtitle);
			stmt.setInt(3, mobType);
			stmt.setInt(4, soundSet);
			for (int i = 0; i <= 4; i++) {
				if (displays.size() > i)
					stmt.setInt(5 + i, displays.get(i));
				else
					stmt.setInt(5 + i, -1);
			}
			stmt.setInt(9, animState);
			stmt.setFloat(10, scale);
			stmt.setInt(11, offset);
			stmt.setInt(12, hitBox);
			stmt.setInt(13, runThreshold);
			stmt.setString(14, gender);
			stmt.setInt(15, level);
			stmt.setBoolean(16, attackable);
			stmt.setInt(17, faction);
			stmt.setString(18, species);
			stmt.setString(19, subSpecies);
			stmt.setString(20, questCategory);
			stmt.setInt(21, templateID);
			Log.debug("MOBDB: placing mob stmt=" + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack(" SQLException"+e);
		}
		Log.debug("Wrote mob data to database");
		return updated;
	}
	
	public int editMobCombatData(int mobID, int health, String attackType) {
		Log.debug("Writing mob combat data to database");
		String tableName = "mobCombatStats";
		String updateString = "UPDATE `" + tableName + "` set health=" + health + ", attackType='" + attackType + 
		 "' where id=" + mobID;
		int updated = queries.executeUpdate(updateString);
		Log.debug("Wrote mob combat data to database");
		return updated;
	}
	
	public int deleteMobEquipmentData(int mobID, int item) {
		Log.debug("Deleting mob equip data to database");
		String tableName = "mobEquipment";
		String deleteString = "DELETE FROM `" + tableName + "` WHERE mobTemplate = " + mobID + " AND item = " + item;
		int deleted = queries.executeUpdate(deleteString);
		Log.debug("Deleting mob equip data to database");
		return deleted;
	}
	
	public int writeFactionData(int category, String name, String group, boolean isPublic, int defaultStance) {
		Log.debug("Writing faction data to database");
		String tableName = "factions";
		String columnNames = "category,name,factionGroup,public,defaultStance";
		String values = category + ",'" + name + "','" + group + "'," + isPublic + "," + defaultStance;
		String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
		int inserted = queries.executeInsert(insertString);
		Log.debug("Wrote faction data to database");
		return inserted;
	}
	
	public int writeFactionStanceData(int factionID, int otherFaction, int defaultStance) {
		Log.debug("Writing faction stance data to database");
		String tableName = "faction_stances";
		String columnNames = "factionID,otherFaction,defaultStance";
		String values = factionID + "," + otherFaction + "," + defaultStance;
		String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
		int inserted = queries.executeInsert(insertString);
		Log.debug("Wrote faction stance data to database");
		return inserted;
	}
	
	public int writeLootTable(int category, LootTable lTbl) {
		Log.debug("Writing loot table data to database");
		String tableName = "lootTables";
		String columnNames = "category,name";
		String values = category + ",'" + lTbl.getName() + "'";
		String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
		int inserted = queries.executeInsert(insertString);
		Log.debug("Wrote loot table data to database");
		return inserted;
	}
	
	public int editLootTable(int tableID, LootTable lTable) {
		Log.debug("Writing loot table data to database");
		String tableName = "lootTables";
		String updateString = "UPDATE `" + tableName + "` set name='" + lTable.getName() + 
		 "' where id=" + tableID;
		int updated = queries.executeUpdate(updateString);
		// Now delete all the old items from the table to insert the new ones
		tableName = "lootTableDrops";
		updateString = "DELETE from " + tableName + " where lootTable=" + tableID;
		queries.executeUpdate(updateString);
		Log.debug("Wrote loot table data to database");
		return updated;
	}
	
	public int writeLootTableDrops(int tableID, int item, int itemCount, float dropChance) {
		Log.debug("Writing loot table data to database");
		String tableName = "lootTableDrops";
		String columnNames = "lootTable,item,itemCount,dropChance";
		String values = tableID + "," + item + "," + itemCount + "," + dropChance;
		String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
		int inserted = queries.executeInsert(insertString);
		Log.debug("Wrote loot table data to database");
		return inserted;
	}
	
	/**
	 * Unused.
	 * @param instance
	 * @return
	 */
	public HashMap<Integer, ResourceGrid> loadResourceGrids(String instance) {
		HashMap<Integer, ResourceGrid> grids = new HashMap<Integer, ResourceGrid>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `resource_grids` where instance=?")) {
			ps.setString(1, instance);
			Log.debug("GRID: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					ResourceGrid bg = new ResourceGrid();
    					bg.setID(rs.getInt("id"));
    					bg.setInstance(instance);
    					Point p = new Point();
    					p.setX(rs.getFloat("locX"));
    					p.setY(rs.getFloat("locY"));
    					p.setZ(rs.getFloat("locZ"));
    					bg.setPosition(p);
    					bg.setResourceType(HelperFunctions.readEncodedString(rs.getBytes("type")));
    					bg.setCount(rs.getInt("count"));
    					bg.setRotation(rs.getFloat("rotation"));
    					grids.put(bg.getID(), bg);
    					//Log.debug("GRID: added grid " + bg.getID() + " to map");
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		return grids;
	}
	
	public int resourceGridUpdated(ResourceGrid grid) {
		Log.debug("GRID: Updating resource grid in the database");
		String tableName = "resource_grids";
		int updated = -1;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set count=? where id=?")) {
			stmt.setInt(1, grid.getCount());
			stmt.setInt(2, grid.getID());
			Log.debug("MOBDB: placing resource grid stmt=" + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack(" SQLException"+e);
		}
		Log.debug("GRID: Updated resource grid data in the database");
		return updated;
	}
	
	/**
	 * Loads in the Dialogues in the World Content Database
	 * @return
	 */
	public HashMap<Integer, Dialogue> loadDialogues() {
		HashMap<Integer, Dialogue> dialogues = new HashMap<Integer, Dialogue>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM dialogue where isactive = 1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					Dialogue d = new Dialogue(id, HelperFunctions.readEncodedString(rs.getBytes("name")), HelperFunctions.readEncodedString(rs.getBytes("text")));
    					d.setOpeningDialogue(rs.getBoolean("openingDialogue"));
    					d.setRepeatable(rs.getBoolean("repeatable"));
    					
    					d.setPrereqFaction( rs.getInt("prereqFaction"));
    					d.setPrereqFactionStance(rs.getInt("prereqFactionStance"));
    					d.setPrereqQuest(rs.getInt("prereqQuest"));
    					d.setPrereqDialogue(rs.getInt("prereqDialogue"));
    					
    					String s= HelperFunctions.readEncodedString(rs.getBytes("audioClip"));
    					d.setAudioClip(s);
    					if(Log.loggingDebug)
    						Log.debug("loadDialogues: dialogue="+d);
    					// Load in options
    				/*	for (int i = 1; i < 8; i++) {
    						String action = HelperFunctions.readEncodedString(rs.getBytes("option" + i + "action"));
    						int actionID = rs.getInt("option" + i + "actionID");
    						// Only add the option if it has an action and actionID defined
    						if (action != null && ((!action.equals("") && (actionID > 0)) || action.equals("Repair"))) {
    							String text = HelperFunctions.readEncodedString(rs.getBytes("option" + i + "text"));
    							int itemReq = rs.getInt("option" + i + "itemReq");
    							d.addOption(text, action, actionID, itemReq);
    						}
    					}*/
    					
    					dialogues.put(id, d);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException" + e);
		}
		
		for(Dialogue d : dialogues.values())
			loadDialogueActions(d);
		
		
		return dialogues;
	}
	
	public void loadDialogueActions(Dialogue dialog) {
		try (PreparedStatement ps = queries.prepare("SELECT * FROM dialogue_actions where isactive = 1 AND dialogueID=" + dialog.getID()+"  ORDER BY actionOrder ASC")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String action = HelperFunctions.readEncodedString(rs.getBytes("action"));
    					int actionID = rs.getInt("actionID");
    					if(Log.loggingDebug)
    						Log.debug("loadDialogueActions: dialogue="+dialog+" action="+action+" actionID="+actionID);
    					// Only add the option if it has an action and actionID defined
    					if (action != null && ((!action.equals("") && (actionID > 0)) || action.equals("GuildWarehouse")|| action.equals("GuildMerchant") || action.equals("Repair")|| action.equals("Auction")|| action.equals("GearModification")|| action.equals("Mail")||(actionID >= 0 && action.equals("Bank")))) {
    						String text = HelperFunctions.readEncodedString(rs.getBytes("text"));
    						int id = rs.getInt("id");
    						int itemReq = rs.getInt("itemReq");
    						boolean itemConsume = rs.getBoolean("itemReqConsume");
    						int currency = rs.getInt("currency");
    						int currencyAmount = rs.getInt("currencyAmount");
    						String audioClip = HelperFunctions.readEncodedString(rs.getBytes("audioClip"));
    						int reqOpenedQuest= rs.getInt("reqOpenedQuest");
    						int reqCompletedQuest= rs.getInt("reqCompletedQuest");
    						int excludingQuest = rs.getInt("excludingQuest");
    						dialog.addOption(id, text, action, actionID, itemReq, itemConsume, currency, currencyAmount, audioClip, reqOpenedQuest, reqCompletedQuest, excludingQuest);
    					}
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadDialogueActions SQLException" + e);
		}
		for(DialogueOption d : dialog.getOptions()) {
				d.requirements = getDialogueActionRequirements(d.id);
		}
		
		
		
	}
	
	private HashMap<Integer, HashMap<String, Integer>> getDialogueActionRequirements(int actionId) {
		HashMap<Integer, HashMap<String, Integer>> requirementMap = new HashMap<Integer, HashMap<String, Integer>>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM dialogue_actions_requirement where dialogue_action_id=" + actionId + " AND isactive=1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int requirementType = rs.getInt("editor_option_type_id");
    					String requirementOption = HelperFunctions.readEncodedString(rs.getBytes("editor_option_choice_type_id"));
    					int requiredValue = rs.getInt("required_value");
    					Log.debug("getDialogueActionRequirements aID="+actionId+" requirementType="+requirementType+" requirementOption="+requirementOption+" requiredValue="+requiredValue);
    					if (requirementMap.containsKey(requirementType)) {
    						HashMap<String, Integer> requirementOptions = requirementMap.get(requirementType);
    						requirementOptions.put(requirementOption, requiredValue);
    						requirementMap.put(requirementType, requirementOptions);
    					} else {
    						HashMap<String, Integer> requirementOptions = new HashMap<String, Integer>();
    						requirementOptions.put(requirementOption, requiredValue);
    						requirementMap.put(requirementType, requirementOptions);
    					}
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		Log.debug("getDialogueActionRequirements aID="+actionId+" requirementMap="+requirementMap);
		
		return requirementMap;
	}
	
	
	/**
	 * Having "too many connections" errors, so adding this function to help cope with it
	 */
	public void close() {
	//	Log.dumpStack("MobDababase.Close");
		queries.close();
	}

	//private static final boolean SKINNING_ENABLED = false;
	private static final boolean SKINNING_ENABLED = true;
}
