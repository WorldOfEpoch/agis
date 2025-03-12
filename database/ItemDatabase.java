package atavism.agis.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.stream.Collectors;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.util.*;
import atavism.agis.plugins.AgisInventoryPlugin;
import atavism.agis.plugins.CraftingPlugin;
import atavism.agis.util.HelperFunctions;
import atavism.agis.util.RequirementChecker;
import atavism.server.engine.Engine;
import atavism.server.engine.OID;
import atavism.server.objects.Template;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;
import atavism.agis.core.*;
import atavism.agis.objects.*;
import atavism.agis.objects.Currency;

/**
 * Contains functions for reading and writing item related content information to/from
 * the content database.
 * @author Andrew Harrison
 *
 */
public class ItemDatabase {

    private static Queries queries;
	
	public ItemDatabase(boolean keepAlive) {
		Log.debug("ItemDatabase queries="+queries);
		if (queries == null) {
		    synchronized (ItemDatabase.class) {
		        if (queries == null) {
		            queries = new Queries(keepAlive);
		        }
            }
		}
	}

	public HashMap<Integer, ItemSetProfile> loadItemSets() {
		Log.debug("ITEM: loading ItemSetProfile");
		//ArrayList<EnchantProfile> list = new ArrayList<EnchantProfile>();
		HashMap<Integer, ItemSetProfile> profiles = new HashMap<Integer, ItemSetProfile>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM item_set_profile WHERE isactive = 1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
    					long date = 0L;
    					if (taxPaidUntil != null) {
    						date = taxPaidUntil.getTime();
    					}
    					ItemSetProfile tmpl = new ItemSetProfile(id, HelperFunctions.readEncodedString(rs.getBytes("name")));
    					tmpl.SetItems(loadItemSetItems(id));
    					tmpl.SetLevels(loadItemSetLevals(id));
    					tmpl.SetDate(date);
    					profiles.put(id,tmpl);
					}
				}
		    }
		} catch (SQLException e) {
			Log.dumpStack("ItemSetProfile: Error SQl "+e.getMessage());
			e.printStackTrace();
		}
		Log.debug("ITEM: loading ItemSetProfile size:"+profiles.size());
			return profiles;
	
		
	}
	
	private ArrayList<Integer> loadItemSetItems(Integer setId) {
		Log.debug("ITEM: loading loadItemSetItems for set: "+setId);
		//ArrayList<EnchantProfile> list = new ArrayList<EnchantProfile>();
		ArrayList<Integer> items = new ArrayList<Integer>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM item_set_items WHERE  isactive = 1 AND set_id = "+setId)) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int tmplId = rs.getInt("template_id");
    					items.add(tmplId);
					}
				}
		    }
		} catch (SQLException e) {
			Log.dumpStack("loadItemSetItems: Error SQl "+e.getMessage());
			e.printStackTrace();
		}
		Log.debug("ITEM: loading loadItemSetItems for set:"+setId+" items size:"+items.size());
			return items;
	}
	
	private Integer loadItemSetForTmplate(Integer templateId) {
		Log.debug("ITEM: loading loadItemSetForTmplate for set: "+templateId);
		int setlId = 0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM item_set_items WHERE template_id = "+templateId)) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					 setlId = rs.getInt("set_id");
					}
				}
		    }
		} catch (SQLException e) {
			Log.dumpStack("loadItemSetForTmplate: Error SQl "+e.getMessage());
			e.printStackTrace();
		}
		Log.debug("ITEM: loading loadItemSetForTmplate for templateId:"+templateId+" setId:"+setlId);
			return setlId;
	}
	


	
	
	public ArrayList<ItemSetLevel> loadItemSetLevals(Integer setId) {
		Log.debug("ITEM: loading loadItemSetLevals");
		//ArrayList<EnchantProfile> list = new ArrayList<EnchantProfile>();
		ArrayList<ItemSetLevel> levels = new ArrayList<ItemSetLevel>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM item_set_level WHERE set_id = "+setId)) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					Log.debug("ITEM: loading loadItemSetLevals id:"+id);
    						ItemSetLevel tmpl = new ItemSetLevel(id);
    					tmpl.SetNumberOfParts(rs.getInt("number_of_parts"));
    					HashMap<String, EnchantStat> stats = new HashMap<String, EnchantStat>();
    					ArrayList<Integer> effects = new ArrayList<Integer>();
    					ArrayList<Integer> abilities = new ArrayList<Integer>();
    					for (int i = 1; i <= 32; i++) {
    					//	Log.error("loadItemSetLevals: 4");
    							String stat = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "name"));
    							Integer value = rs.getInt("effect" + i + "value");
    							Integer valuep = rs.getInt("effect" + i + "valuep");
    							
    							if (stat.equals("effect")) {
    								effects.add(value);    								
    							} else if (stat.equals("ability")) {
    								abilities.add(value);
								} else if (stat != null && !stat.isEmpty())  {
    								stats.put(stat, new EnchantStat(stat, value, valuep));
    							}
    						
    					}
    					Log.debug("loadItemSetLevals: 5");
    					stats.put("dmg-base", new EnchantStat("dmg-base", rs.getInt("damage"), rs.getInt("damagep")));
    					stats.put("dmg-max", new EnchantStat("dmg-max", rs.getInt("damage"), rs.getInt("damagep")));
    					tmpl.SetStats(stats);
    					tmpl.SetEffects(effects);
    					tmpl.SetAbilities(abilities);
    					levels.add(tmpl);
					}
    
				}
		    }
		} catch (SQLException e) {
			Log.dumpStack("loadItemSetLevals: Error SQl "+e.getMessage());
			e.printStackTrace();
		}
		Log.debug("ITEM: loading loadItemSetLevals size:"+levels.size());
			return levels;
	
		
	}
	
	public HashMap<Integer, QualityInfo> loadItemQualitySettings() {
		Log.debug("ITEM: loading item Quality Settings");
		//ArrayList<EnchantProfile> list = new ArrayList<EnchantProfile>();
		HashMap<Integer, QualityInfo> qualitys = new HashMap<Integer, QualityInfo>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM item_quality")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					QualityInfo tmpl = new QualityInfo(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
    					tmpl.SetCost(rs.getFloat("cost")/100f);
    					tmpl.SetChance(rs.getFloat("chance")/100f);
    					qualitys.put(id,tmpl);
					}
				}
		    }
		} catch (SQLException e) {
			Log.dumpStack("Error SQl "+e.getMessage());
			e.printStackTrace();
		}
		Log.debug("ITEM: loading Quality size:"+qualitys.size());
			return qualitys;
	
		
	}

	
	public HashMap<Integer, EnchantProfile> loadItemEnchantProfiles() {
		Log.debug("loadItemEnchantProfiles: loading item EnchantProfile");
		//ArrayList<EnchantProfile> list = new ArrayList<EnchantProfile>();
		HashMap<Integer, EnchantProfile> profiles = new HashMap<Integer, EnchantProfile>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM item_enchant_profile where isactive = 1 ")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Log.debug("loadItemEnchantProfiles: 1");
    					int id = rs.getInt("id");
    					if (!profiles.containsKey(id)) {
    						Log.debug("ITEM: add EnchantProfile");
    						profiles.put(id, new EnchantProfile(id));
    					}
    				//	Log.error("loadItemEnchantProfiles: 2");
    					
    					int levels = profiles.get(id).GetLevels().size();
    					int level = rs.getInt("level");
    					for (int l = levels + 1; l <= level; l++) {
    						//Log.error("loadItemEnchantProfiles: 3");
    						
    						EnchantProfileLevel tmpl = new EnchantProfileLevel(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")), l);
    						tmpl.SetPercentage(rs.getBoolean("percentage"));
    						tmpl.SetAllStats(rs.getBoolean("all_stats"));
    						tmpl.SetAddNotExist(rs.getBoolean("add_not_exist"));
    						if(rs.getBoolean("percentage")) {
    							tmpl.SetStatValue(rs.getInt("stat_value")/100f);
    						}else {
    							tmpl.SetStatValue(rs.getInt("stat_value"));
    						}
    						tmpl.SetLowerBy(rs.getInt("lower_by"));
    						tmpl.SetLowerTo(rs.getInt("lower_to"));
    						tmpl.SetGearScoreValue(rs.getInt("gear_score"));
    						tmpl.SetGearScoreValuePercentage(rs.getInt("gear_scorep") / 100f);
    						tmpl.SetChance(rs.getFloat("chance"));
    						tmpl.SetCost(rs.getInt("cost"));
    						tmpl.SetCurrency(rs.getInt("currency"));
    						HashMap<String, EnchantStat> stats = new HashMap<String, EnchantStat>();
    						ArrayList<Integer> effects = new ArrayList<Integer>();
        					ArrayList<Integer> abilities = new ArrayList<Integer>();
    						for (int i = 1; i <= 32; i++) 
    						{
    						//	Log.error("loadItemEnchantProfiles: 4");
    								String stat = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "name"));
    								Integer value = rs.getInt("effect" + i + "value");
    								Integer valuep = rs.getInt("effect" + i + "valuep");
    								
    								if (stat.equals("effect")) {
    									effects.add(value);    								
    								} else if (stat.equals("ability")) {
    									abilities.add(value);
    								} else if (stat != null && !stat.isEmpty()) {    									
    									stats.put(stat, new EnchantStat(stat, value, valuep/100f));
    								}
    						}
    						//Log.error("loadItemEnchantProfiles: 5");
    						stats.put("dmg-base", new EnchantStat("dmg-base", rs.getInt("damage"), rs.getInt("damagep")/100f));
    						stats.put("dmg-max", new EnchantStat("dmg-max", rs.getInt("damage"), rs.getInt("damagep")/100f));
    					
    						tmpl.SetStats(stats);
    						tmpl.SetEffects(effects);
    						tmpl.SetAbilities(abilities);
    						profiles.get(id).GetLevels().put(l, tmpl);
    					}
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadItemEnchantProfiles: Error SQl "+e.getMessage());
			e.printStackTrace();
		}
		Log.debug("ITEM: loading item EnchantProfile size:"+profiles.size());
		return profiles;
	}
	
	

	public HashMap<Integer, VipLevel> loadVipLevels() {
		Log.debug("loadVipLevels: loading item VipLevel");
		HashMap<Integer, VipLevel> levels = new HashMap<Integer, VipLevel>();
		try (PreparedStatement ps = queries.prepare(
					"SELECT vl.description, vl.id, vl.name as lname, vl.level, vls.value, vls.valuep, vs.name as sname, vs.code, vl.max_points FROM vip_level as vl "
					+ "JOIN vip_level_bonuses as vls ON vl.id = vls.vip_level_id  JOIN bonuses_settings as vs ON  vls.bonus_settings_id = vs.id")) {

		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Log.debug("loadVipLevels: 1");
    					int level = rs.getInt("level");
    					if (!levels.containsKey(level)) {
    						Log.debug("loadVipLevels: add VipLevel");
    						levels.put(level, new VipLevel(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("lname")), rs.getInt("level"), rs.getLong("max_points"), HelperFunctions.readEncodedString(rs.getBytes("description"))));
    					}
    					levels.get(level).GetSettings().put(HelperFunctions.readEncodedString(rs.getBytes("code")),
    							new BonusSettings(HelperFunctions.readEncodedString(rs.getBytes("sname")), HelperFunctions.readEncodedString(rs.getBytes("code")), rs.getInt("value"), rs.getFloat("valuep"),-1));
    					
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadVipLevels: Error SQl " + e.getMessage());
			e.printStackTrace();
		}
		Log.debug("ITEM: loading VipLevels size:" + levels.size());
		return levels;
	}
	
    public HashMap<String, Serializable> loadItemPrefabData() {
        HashMap<String, Serializable> props = new HashMap<>();
        int c = 0;

    // Map to store item IDs to their index 'c' in props
    Map<Integer, Integer> itemIdToIndex = new HashMap<>();
        List<Integer> templateIds = new ArrayList<>();

        try (PreparedStatement ps = queries.prepare("SELECT * FROM " + ITEM_TABLE + " WHERE isactive = 1")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
                if (rs != null) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                    itemIdToIndex.put(id, c); // Map item ID to index 'c'
                        templateIds.add(id); // Collect templateIds for batch processing later

                        Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
                        long date = (taxPaidUntil != null) ? taxPaidUntil.getTime() : 0L;

                        Log.debug("GetItemPrefabDataHook: id=" + id + " new ");

                        props.put("i" + c + "id", id);
                        props.put("i" + c + "date", date);
                        props.put("i" + c + "name", HelperFunctions.readEncodedString(rs.getBytes("name")));
                        String tooltip = HelperFunctions.readEncodedString(rs.getBytes("tooltip"));
                        if (tooltip == null)
                            tooltip = "";
                        props.put("i" + c + "tooltip", tooltip);
                        props.put("i" + c + "icon", HelperFunctions.readEncodedString(rs.getBytes("icon")));
                        props.put("i" + c + "icon2", HelperFunctions.readEncodedString(rs.getBytes("icon2")));
                        props.put("i" + c + "itemType", HelperFunctions.readEncodedString(rs.getBytes("itemType")));
                        props.put("i" + c + "subType", HelperFunctions.readEncodedString(rs.getBytes("subType")));
                        props.put("i" + c + "slot", HelperFunctions.readEncodedString(rs.getBytes("slot")));
                        props.put("i" + c + "quality", rs.getInt("itemQuality"));
                        props.put("i" + c + "currType", rs.getInt("purchaseCurrency"));
                        props.put("i" + c + "cost", rs.getLong("purchaseCost"));
                        props.put("i" + c + "binding", rs.getInt("binding"));
                        props.put("i" + c + "sellable", rs.getBoolean("sellable"));
                        props.put("i" + c + "dValue", rs.getInt("damage"));
                        props.put("i" + c + "dMValue", rs.getInt("damageMax"));
                        // We'll assign setId later after batch loading 
                        //props.put("i" + c + "setId", loadItemSetForTmplate(id));// FIXTHIS
                        props.put("i" + c + "enchId", rs.getInt("enchant_profile_id"));
                        props.put("i" + c + "wSpeed", (int) rs.getFloat("delay") * 1000);
                        props.put("i" + c + "sLimit", rs.getInt("stackLimit"));
                        props.put("i" + c + "aH", rs.getBoolean("auctionHouse"));
                        props.put("i" + c + "unique", rs.getBoolean("isUnique"));
                        props.put("i" + c + "gear_score", rs.getInt("gear_score"));

                        props.put("i" + c + "durability", rs.getInt("durability"));
                        props.put("i" + c + "sockettype", HelperFunctions.readEncodedString(rs.getBytes("socket_type")));
                        props.put("i" + c + "parry", rs.getBoolean("parry"));
                        props.put("i" + c + "weight", rs.getInt("weight"));
                        props.put("i" + c + "deathLoss", rs.getInt("death_loss"));
                        props.put("i" + c + "autoattack", rs.getInt("autoattack"));
                        props.put("i" + c + "ammotype", rs.getInt("ammotype"));
                        props.put("i" + c + "repairable", rs.getBoolean("repairable"));
                        props.put("i" + c + "wp", rs.getInt("weapon_profile_id"));
                        props.put("i" + c + "apid", rs.getInt("audio_profile_id"));
                        props.put("i" + c + "gp", HelperFunctions.readEncodedString(rs.getBytes("ground_prefab")));

                        int effnum = 0;
                        for (int i = 1; i <= 32; i++) {
                            String effectType = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "type"));
                            if (effectType != null && !effectType.equals("")) {
                                props.put("i" + c + "eT" + effnum, effectType);
                                String ename = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "name"));
                                if (ename == null)
                                    ename = "";
                                props.put("i" + c + "eN" + effnum, ename);
                                String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
                                if (value == null)
                                    value = "";
                                props.put("i" + c + "eV" + effnum, value);
                                effnum++;
                            }
                        }
                        props.put("i" + c + "enum", effnum);
                        props.put("i" + c + "rnum", 0);
                        // We may need to batch load item requirements as well
                        //props.putAll(getItemRequirementsPrefabData(id, c));// FIXTHIS
                        c++;
                    }
                }
                props.put("num", c);
            }
        } catch (SQLException e) {
            Log.dumpStack("ITEM: loaded item template Exception:" + e);
            e.printStackTrace();
            return null;
        }

        // After loading items, batch load item set data
        Map<Integer, Integer> itemSetMap = loadItemSetForTmplate(templateIds);// FIXTHIS

    // Batch load item requirements
    Map<Integer, Map<String, Serializable>> itemRequirementsMap = batchGetItemRequirementsPrefabData(templateIds, itemIdToIndex);

        // Now, assign setId and item requirements to props
        int index = 0;
        for (Integer templateId : templateIds) {
            Integer setId = itemSetMap.get(templateId);
            if (setId != null) {
                props.put("i" + index + "setId", setId);
            } else {
                props.put("i" + index + "setId", 0); // Or any default value
            }

            Map<String, Serializable> itemRequirements = itemRequirementsMap.get(templateId);
            if (itemRequirements != null) {
                props.putAll(itemRequirements);
            }

            index++;
        }

        return props;
    }

  
    private Map<Integer, Map<String, Serializable>> batchGetItemRequirementsPrefabData(List<Integer> itemIds, Map<Integer, Integer> itemIdToIndex) {
        Map<Integer, Map<String, Serializable>> itemRequirementsMap = new HashMap<>();
        if (itemIds.isEmpty()) {
            return itemRequirementsMap;
        }
        String placeholders = String.join(",", Collections.nCopies(itemIds.size(), "?"));
        String sql = "SELECT * FROM item_templates_options WHERE item_id IN (" + placeholders + ") AND isactive=1";

        try (PreparedStatement ps = queries.prepare(sql)) {
            for (int i = 0; i < itemIds.size(); i++) {
                ps.setInt(i + 1, itemIds.get(i));
            }
            try (ResultSet rs = queries.executeSelect(ps)) {
                if (rs != null) {
                    // Maps to collect requirement data per item ID
                    Map<Integer, StringBuilder> reqTypeMap = new HashMap<>();
                    Map<Integer, StringBuilder> reqNamesMap = new HashMap<>();
                    Map<Integer, StringBuilder> reqValuesMap = new HashMap<>();

                    while (rs.next()) {
                        int itemID = rs.getInt("item_id");
                        int requirementType = rs.getInt("editor_option_type_id");
                        String ReqName = RequirementChecker.getRequirementTypeText(requirementType);
                        String requirementOption = HelperFunctions.readEncodedString(rs.getBytes("editor_option_choice_type_id"));
                        int requiredValue = rs.getInt("required_value");

                        // Append requirement data to corresponding item ID
                        switch (requirementType) {
                            case 74: // Level
                                appendToMap(reqTypeMap, itemID, ReqName + ";");
                                appendToMap(reqNamesMap, itemID, requirementOption + ";");
                                appendToMap(reqValuesMap, itemID, requiredValue + ";");
                                break;
                            case 75: // Skill Level
                                appendToMap(reqTypeMap, itemID, ReqName + ";");
                                appendToMap(reqNamesMap, itemID, GetSkillName(requirementOption) + ";");
                                appendToMap(reqValuesMap, itemID, requiredValue + ";");
                                break;
                            case 76: // Race
                                appendToMap(reqTypeMap, itemID, ReqName + ";");
                                appendToMap(reqNamesMap, itemID, RequirementChecker.getRace(Integer.parseInt(requirementOption)) + ";");
                                appendToMap(reqValuesMap, itemID, requiredValue + ";");
                                break;
                            case 77: // Class
                                appendToMap(reqTypeMap, itemID, ReqName + ";");
                                appendToMap(reqNamesMap, itemID, RequirementChecker.getClass(Integer.parseInt(requirementOption)) + ";");
                                appendToMap(reqValuesMap, itemID, requiredValue + ";");
                                break;
                            case 78: // Stat
                                appendToMap(reqTypeMap, itemID, ReqName + ";");
                                appendToMap(reqNamesMap, itemID, requirementOption + ";");
                                appendToMap(reqValuesMap, itemID, requiredValue + ";");
                                break;
                            default:
                                break;
                        }
                    }

                    // Construct requirement data for each item
                    for (Integer itemID : itemIds) {
                        int c = itemIdToIndex.get(itemID);
                        String reqType = reqTypeMap.containsKey(itemID) ? reqTypeMap.get(itemID).toString() : "";
                        String reqNames = reqNamesMap.containsKey(itemID) ? reqNamesMap.get(itemID).toString() : "";
                        String reqValues = reqValuesMap.containsKey(itemID) ? reqValuesMap.get(itemID).toString() : "";

                        if (reqType.endsWith(";"))
                            reqType = reqType.substring(0, reqType.length() - 1);
                        if (reqNames.endsWith(";"))
                            reqNames = reqNames.substring(0, reqNames.length() - 1);
                        if (reqValues.endsWith(";"))
                            reqValues = reqValues.substring(0, reqValues.length() - 1);

                        Map<String, Serializable> props = new HashMap<>();
                        props.put("i" + c + "reqType", reqType);
                        props.put("i" + c + "reqNames", reqNames);
                        props.put("i" + c + "reqValues", reqValues);

                        itemRequirementsMap.put(itemID, props);
                    }
                }
            }
        } catch (SQLException e) {
            Log.dumpStack("batchGetItemRequirementsPrefabData: loaded Exception:" + e);
            e.printStackTrace();
        }
        return itemRequirementsMap;
    }

    // Helper method to append values to StringBuilder in a Map
    private void appendToMap(Map<Integer, StringBuilder> map, int key, String value) {
        StringBuilder sb = map.computeIfAbsent(key, k -> new StringBuilder());
        sb.append(value);
    }

    // Modified loadItemSetForTmplate function
    private Map<Integer, Integer> loadItemSetForTmplate(Collection<Integer> templateIds) {
        Map<Integer, Integer> itemSetMap = new HashMap<>();
        if (templateIds == null || templateIds.isEmpty()) {
            return itemSetMap; // Return empty map if templateIds is null or empty
        }

        // Convert templateIds to a comma-separated string for the SQL IN clause
        String templateIdsString = templateIds.stream()
                                              .map(String::valueOf)
                                              .collect(Collectors.joining(","));

        Log.debug("ITEM: loading item sets for templates: " + templateIdsString);
        String sql = "SELECT template_id, set_id FROM item_set_items WHERE template_id IN (" + templateIdsString + ")";

        try (PreparedStatement ps = queries.prepare(sql)) {
            try (ResultSet rs = queries.executeSelect(ps)) {
                if (rs != null) {
                    while (rs.next()) {
                        int templateId = rs.getInt("template_id");
                        int setId = rs.getInt("set_id");
                        itemSetMap.put(templateId, setId);
                    }
                }
            }
        } catch (SQLException e) {
            Log.dumpStack("loadItemSetForTmplate: SQLException: " + e.getMessage());
            e.printStackTrace();
        }
        Log.debug("ITEM: finished loading item sets for templates.");
        return itemSetMap;
    }



	/**
	 * Load Item Audio profile Data
	 *
	 * @returnMSG_TYPE_LOOT_GROUND_ITEM
	 */
	public HashMap<String, Serializable> getItemAudioProfilePrefabData() {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int i = 0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM item_audio_profile where isactive=1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						int id = rs.getInt("id");
						Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
						long date = 0L;
						if (taxPaidUntil != null) {
							date = taxPaidUntil.getTime();
						}
						Log.debug("getItemAudioProfilePrefabData: id=" + id+" new " );
						props.put("i"+i+"id", id);
						props.put("i"+i+"date", date);
						props.put("i"+i+"name", HelperFunctions.readEncodedString(rs.getBytes("name")));
						props.put("i"+i+"u", HelperFunctions.readEncodedString(rs.getBytes("use_event")));
						props.put("i"+i+"db", HelperFunctions.readEncodedString(rs.getBytes("drag_begin_event")));
						props.put("i"+i+"de", HelperFunctions.readEncodedString(rs.getBytes("drag_end_event")));
						props.put("i"+i+"d", HelperFunctions.readEncodedString(rs.getBytes("delete_event")));
						props.put("i"+i+"b", HelperFunctions.readEncodedString(rs.getBytes("broke_event")));
						props.put("i"+i+"pu", HelperFunctions.readEncodedString(rs.getBytes("pick_up_event")));
						props.put("i"+i+"f", HelperFunctions.readEncodedString(rs.getBytes("fall_event")));
						props.put("i"+i+"dr", HelperFunctions.readEncodedString(rs.getBytes("drop_event")));
						i++;
					}
				}
			}
			props.put("num", i);
		} catch (SQLException e) {
			Log.dumpStack("ITEM: getItemAudioProfilePrefabData loaded Exception:" + e);
			e.printStackTrace();
		}
		return props;
	}

	/**
	 * Loading Weapon Profile Prefab Data
	 */
	public HashMap<String, Serializable> loadWeaponProfilePrefabData() {

		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int c = 0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM weapon_templates_profile where isactive = 1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {

						int id = rs.getInt("id");
						Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
						long date = 0L;
						if (taxPaidUntil != null) {
							date = taxPaidUntil.getTime();
						}
						Log.debug("loadWeaponProfilePrefabData: id=" + id + " new ");
						props.put("i" + c + "id", id);
						props.put("i" + c + "date", date);
						props.put("i" + c + "name", HelperFunctions.readEncodedString(rs.getBytes("name")));
						props.putAll(getWeaponProfileActionsPrefabData(id, c));
						c++;
					}
				}
			}
			props.put("num", c);

		} catch (SQLException e) {
			Log.dumpStack("ITEM: loaded item template Exception:" + e);
			e.printStackTrace();
			return null;
		}

		return props;
	}

	private Map<String, Serializable> getWeaponProfileActionsPrefabData(int profileId, int c) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		int i = 0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM weapon_action_settings where profile_id=" + profileId + " AND isactive=1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						int action_id = rs.getInt("action_id");
						String slot = HelperFunctions.readEncodedString(rs.getBytes("slot"));
						int ability_id = rs.getInt("ability_id");
						int abtion_type = rs.getInt("action_type");
						boolean zoom = abtion_type == 1;
						String coordeffect = HelperFunctions.readEncodedString(rs.getBytes("coordeffect"));
						props.put("i" + c + "s" + i + "ac", RequirementChecker.getNameEditorOptionChoice("Weapon Actions", action_id));
						props.put("i" + c + "s" + i + "s", slot != null ? slot : "");
						props.put("i" + c + "s" + i + "z", zoom);
						props.put("i" + c + "s" + i + "ab", ability_id);
						props.put("i" + c + "s" + i + "c", coordeffect != null ? coordeffect : "");
						i++;
					}
				}
			}
			props.put("i" + c + "sNum", i);
		} catch (SQLException e) {
			Log.dumpStack("ITEM: getWeaponProfileActionsPrefabData loaded Exception:" + e);
			e.printStackTrace();
		}
		return props;
	}

	/**
	 * Get count of the Item definitions
	 * @return
	 */
	public int getCountItems() {
		int count = 0;
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM "+ITEM_TABLE+" where isactive = 1")) {
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
	 * Loads in the item templates from the content database.
	 * @return
	 */
	public ArrayList<Template> loadItemTemplates(LinkedList<Integer> storeItems) {
		Log.debug("ITEM: loading item templates");
    ArrayList<Template> list = new ArrayList<>();
    Map<Integer, Template> templatesById = new HashMap<>();
    List<Integer> templateIds = new ArrayList<>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM " + ITEM_TABLE + " where isactive = 1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
                    int templateId = rs.getInt("id");
                    Template tmpl = new Template(HelperFunctions.readEncodedString(rs.getBytes("name")), templateId, ObjectManagerPlugin.ITEM_TEMPLATE);
    					if(Log.loggingDebug)
    							Log.debug("ITEM: loading item template " + rs.getInt("id"));
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "baseName", HelperFunctions.readEncodedString(rs.getBytes("name")));
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "category", HelperFunctions.readEncodedString(rs.getBytes("category")));
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "subcategory", HelperFunctions.readEncodedString(rs.getBytes("subcategory")));
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "itemID", rs.getInt("id"));
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "enchantProfileId", rs.getInt("enchant_profile_id"));
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ICON, HelperFunctions.readEncodedString(rs.getBytes("icon")));
    				//	tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ICON2, HelperFunctions.readEncodedString(rs.getBytes("icon2")));
    						String itemType = HelperFunctions.readEncodedString(rs.getBytes("itemType"));
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "itemType", itemType);
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "subType", HelperFunctions.readEncodedString(rs.getBytes("subType")));
    					boolean oadelete = rs.getBoolean("oadelete");
    					
    					
    					int shopSlots = rs.getInt("shopSlots");
    					if(Log.loggingDebug)
    						Log.debug("ITEM: loading item templates shopSlots="+shopSlots);
    					if (shopSlots > 0) {
    						String model = HelperFunctions.readEncodedString(rs.getBytes("shopModel"));
    						int mobtemplate = rs.getInt("shopMobTemplate");
    						String shhopTag = HelperFunctions.readEncodedString(rs.getBytes("shopTag"));
    						int numShops = rs.getInt("numShops");
    						int shopTimeOut = rs.getInt("shopTimeOut");
    						boolean destroyOnLogOut = rs.getBoolean("shopDestroyOnLogOut");
    						PlayerShopActivateHook psah = new PlayerShopActivateHook(model, shhopTag, numShops, shopSlots, destroyOnLogOut, oadelete, mobtemplate,shopTimeOut);
    						ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    						if (ah == null) {
    							ah = new ArrayList<ActivateHook>();
    						}
    						ah.add(psah);
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    					}
//						tmpl.put(InventoryClient.ITEM_NAMESPACE, "drawWeaponEffect", HelperFunctions.readEncodedString(rs.getBytes("drawWeaponEffect")));
//						tmpl.put(InventoryClient.ITEM_NAMESPACE, "holsteringWeaponEffect", HelperFunctions.readEncodedString(rs.getBytes("holsteringWeaponEffect")));
//						tmpl.put(InventoryClient.ITEM_NAMESPACE, "drawWeaponTime", rs.getInt("drawWeaponTime"));
//						tmpl.put(InventoryClient.ITEM_NAMESPACE, "holsteringWeaponTime", rs.getInt("holsteringWeaponTime"));

						HashMap<String,DrawWeaponInfo> drawData = new HashMap<String,DrawWeaponInfo>();
						for (int i = 1; i <= 10; i++) {
							String slotName = HelperFunctions.readEncodedString(rs.getBytes("slot" + i));
							if (slotName != null && slotName.length() > 0) {
								String drawWeaponEffect = HelperFunctions.readEncodedString(rs.getBytes("drawWeaponEffect" + i));
								String holsteringWeaponEffect = HelperFunctions.readEncodedString(rs.getBytes("holsteringWeaponEffect" + i));
								int drawWeaponTime = rs.getInt("drawWeaponTime" + i);
								int holsteringWeaponTime = rs.getInt("holsteringWeaponTime" + i);
								DrawWeaponInfo dwi = new DrawWeaponInfo(slotName, drawWeaponEffect, drawWeaponTime, holsteringWeaponEffect, holsteringWeaponTime);
								drawData.put(slotName, dwi);
							}
						}
						tmpl.put(InventoryClient.ITEM_NAMESPACE, "drawData", drawData);

						if (itemType.equals("Weapon") || itemType.equals("Armor") || itemType.equals("Tool")) {
    						AgisEquipInfo eqInfo = getEquipInfo(HelperFunctions.readEncodedString(rs.getBytes("slot")));
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "slot", HelperFunctions.readEncodedString(rs.getBytes("slot")));
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_EQUIP_INFO, eqInfo);
    						//tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new EquipActivateHook());
    						
    						ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    						if(ah==null) {
    							ah = new ArrayList<ActivateHook>();
    						}
    						ah.add(new EquipActivateHook());
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						
    						
    						String displayID = HelperFunctions.readEncodedString(rs.getBytes("display"));
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "displayVal", displayID);
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "skillExp", rs.getInt("skillExp"));
    					}
    					
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "itemGrade", rs.getInt("itemQuality"));
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "binding", rs.getInt("binding"));
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "isUnique", rs.getBoolean("isUnique"));
    					if (itemType.equals("Bag") || itemType.equals("Container")) {
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "numSlots", rs.getInt("stackLimit"));
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "stackLimit", 1);
    					} else {
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "stackLimit", rs.getInt("stackLimit"));
    					}
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "purchaseCurrency", rs.getInt("purchaseCurrency"));
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "purchaseCost", rs.getLong("purchaseCost"));
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "sellable", rs.getBoolean("sellable"));
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "auctionHouse", rs.getBoolean("auctionHouse"));
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "gearScore", rs.getInt("gear_score"));
    					
    					if (itemType.equals("Weapon") || itemType.equals("Armor") || itemType.equals("Tool")) {
    						int durability = rs.getInt("durability");
    						boolean repairable = rs.getBoolean("repairable");
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "durability", durability);
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "maxDurability", durability);
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "repairable", repairable);
    						if(Log.loggingDebug)
    								Log.debug("ITEM: added durability property with value: " + durability+" repairable="+repairable);
    						
    						if (rs.getBoolean("parry"))
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_PARRY_EFFECT, true);
    						else
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_PARRY_EFFECT, false);	
    					}
    					
    					String stype = HelperFunctions.readEncodedString(rs.getBytes("socket_type"));
    					if(Log.loggingDebug)
    						Log.debug("Load Template socket type:" + stype + " name:" + tmpl.getName());
    					if (!stype.equals("") && !stype.equals("~ none ~"))
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "SocketType", stype);
    					
    					int weight = rs.getInt("weight");
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "weight", weight);
    					if(Log.loggingDebug)
    							Log.debug("ITEM: added weight property with value: " + weight);
    
    					int death_loss = rs.getInt("death_loss");
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "deathLossChance", death_loss);
    					if(Log.loggingDebug)
    						Log.debug("ITEM: added deathLossChance property with value: " + death_loss);
    					
    					int passive_ability = rs.getInt("passive_ability");
    					if(Log.loggingDebug)
    							Log.debug("ITEM: added pabilityID property with value: " + death_loss);
    					
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "pabilityID", passive_ability);
    					  
    					if (itemType.equals("Weapon")) {
    
    						int abilityID = rs.getInt("autoattack");
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "autoAttack", abilityID);
    
    						int ammoTypeID = rs.getInt("ammotype");
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, AgisItem.AMMO_TYPE, ammoTypeID);
    
    					}
    					HashMap<String, Integer> itemStats =  new HashMap<String, Integer>();
    					HashMap<Integer,SocketInfo> itemSockets = new HashMap<Integer,SocketInfo>();
    					ArrayList<BonusSettings> bonuses = new ArrayList<BonusSettings>();
    					itemStats.put("gearScore", 0);
    						// Item Effects
    					// Add code here to handle new Item Effect types you have added
    					for (int i = 1; i <= 32; i++) {
    						String effectType = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "type"));
    						if(Log.loggingDebug)
    								Log.debug("Load Item id="+rs.getInt("id")+" i="+i+" effectType=>"+effectType+"<");
    						if (effectType == null || effectType.equals("")) {
    							//break;
    						} else if (effectType.equals("Stat")) {
    							// Stats can only exist on weapons and armor
    						//	if (itemType.equals("Weapon") || itemType.equals("Armor")) {
    								String stat = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "name"));
    								String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    								if (stat != null && !stat.isEmpty()) {
    									itemStats.put(stat, Integer.parseInt(value));
    							//	}
    							}
    						} else if (effectType.equals("UseAbility")) {
    							Log.debug("USEABILITY: got useAbility Item Effect");
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int abilityID = Integer.parseInt(value);
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, "abilityID", abilityID);
    						  //  tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new AbilityActivateHook(abilityID));
    							ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<ActivateHook>();
    							}
    							ah.add(new AbilityActivateHook(abilityID,oadelete));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    							if(Log.loggingDebug)
    								  Log.debug("USEABILITY: added Item Effect: " + abilityID);
    						    // We also need to get the cooldown(s) for this associated ability
    						    /*LinkedList<String> cooldowns = getAbilityCooldowns(abilityID);
    						    for (int j = 0; j < cooldowns.size(); j++) {
    						    	tmpl.put(InventoryClient.ITEM_NAMESPACE, "cooldown_" + j, cooldowns.get(j));
    						    }*/
    						}/* else if (effectType.equals("PassiveAbility")) {
    							Log.debug("PassiveAbility: got PassiveAbility Item Effect");
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int abilityID = Integer.parseInt(value);
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, "pabilityID", abilityID);
    						    Log.debug("PassiveAbility: added Item Effect: " + abilityID);
    						} */
    						/*else if (effectType.equals("AutoAttack")) {
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int abilityID = Integer.parseInt(value);
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, "autoAttack", abilityID);
    						} */ else if (effectType.equals("CreateClaim")) {
    							// Used to turn an item into a Building Resource when acquired
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int size = Integer.parseInt(value);
    							String name = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "name"));
    							int type = 1;
    							if (name.length()>0)
    								type = Integer.parseInt(name);
    							else
    								type=1;
    							//tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new CreateClaimActivateHook(size,type));
    							ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<ActivateHook>();
    							}
    							ah.add(new CreateClaimActivateHook(size,type,oadelete));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						} else if (effectType.equals("Currency")) {
    							// Used to turn an item into a Currency when activated
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int currencyID = Integer.parseInt(value);
    							ArrayList<AcquireHook> ah = (ArrayList<AcquireHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_ACQUIRE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<AcquireHook>();
    							}
    							ah.add(new CurrencyItemAcquireHook(currencyID));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_ACQUIRE_HOOK, ah);
    //							tmpl.put(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_ACQUIRE_HOOK, new CurrencyItemAcquireHook(currencyID));
    						} else if (effectType.equals("CurrencyItem")) {
    							// Used to turn an item into a Currency when activated
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int currencyID = Integer.parseInt(value);
    							//tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new CurrencyItemActivateHook(currencyID));
    							ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<ActivateHook>();
    							}
    							ah.add(new CurrencyItemActivateHook(currencyID,oadelete));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						} else if (effectType.equals("BuildingMaterial")) {
    							// Used to turn an item into a Building Resource when acquired
    							//String value = rs.getString("effect" + i + "value");
    							//int resourceID = Integer.parseInt(value);
    							//tmpl.put(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_ACQUIRE_HOOK, new BuildingResourceAcquireHook(rs.getInt("id")));
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int buildHealthValue = Integer.parseInt(value);
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, "buildHealthValue", buildHealthValue);
    						} else if (effectType.equals("Blueprint")) {
    							// Used to turn an item into a recipe when acquired
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int recipeID = Integer.parseInt(value);
    							//tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new RecipeItemActivateHook(recipeID));
    							ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<ActivateHook>();
    							}
    							ah.add(new RecipeItemActivateHook(recipeID,oadelete));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						} else if (effectType.equals("StartQuest")) {
    							// Starts a quest for the player when the item is activated
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int questID = Integer.parseInt(value);
    							//tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new QuestStartItemActivateHook(questID));
    							ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<ActivateHook>();
    							}
    							ah.add(new QuestStartItemActivateHook(questID,oadelete));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						} else if (effectType.equals("SpawnMob")) {
    							// Used to turn an item into a Mob when activated
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int mobTemplateID = Integer.parseInt(value);
    							ArrayList<AcquireHook> ah = (ArrayList<AcquireHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_ACQUIRE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<AcquireHook>();
    							}
    							ah.add(new SpawnMobAcquireHook(mobTemplateID));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_ACQUIRE_HOOK, ah);
    							//tmpl.put(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_ACQUIRE_HOOK, new SpawnMobAcquireHook(mobTemplateID));
    						}/* else if (effectType.equals("UseAmmo")) {
    							String ammoType = rs.getString("effect" + i + "name");
    							int ammoTypeID = Integer.parseInt(ammoType);
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, AgisItem.AMMO_TYPE, ammoTypeID);
    							//Log.debug("AMMO: set UseAmmo type to: " + ammoTypeID);
    							
    						}*//* else if (effectType.equals("Durability")) {
    							// Gives an item a durability amount
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int durability = Integer.parseInt(value);
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, "durability", durability);
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, "maxDurability", durability);
    						} *//*else if (effectType.equals("Weight")) {
    							// Gives an item a weight value
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int weight = Integer.parseInt(value);
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, "weight", weight);
    							Log.debug("ITEM: added weight property with value: " + weight);
    						} *//*else if (effectType.equals("DeathLossChance")) {
    							// Gives an item a deathLossChance value
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int chance = Integer.parseInt(value);
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, "deathLossChance", chance);
    							Log.debug("ITEM: added deathLossChance property with value: " + chance);
    						} */else if (effectType.equals("AccountEffect")) {
    							// Gives an item an account effect which will be applied to the users account when they acquire the item
    							String effect = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "name"));
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int effectValue = Integer.parseInt(value);
    						//	tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new AccountEffectItemAcquireHook(effect, effectValue));
    							ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<ActivateHook>();
    							}
    							ah.add(new AccountEffectItemActivateHook(effect, effectValue,oadelete));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						} else if (effectType.equals("ItemStore")) {
    							// Adds an item to the ItemStore list which can be sent down to players to purchase items without a merchant
    							storeItems.add(rs.getInt("id"));
    						}/* else if (effectType.equals("SocketsEffect")) {
    							//if (!itemType.equals("Weapon") && !itemType.equals("Armor")) {
    							// On activate starts soceting action but not on weapon
    							String type = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "name"));
    						//	String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    						//	int effectValue = Integer.parseInt(value);
    							Log.debug("Load Template socket type:"+type+" name:"+tmpl.getName());
    							tmpl.put(InventoryClient.ITEM_NAMESPACE,"SocketType",type);
    							//tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new SocketActivateHook(type));
    						//	}
    						}*/ else if (effectType.equals("Sockets")) {
    							
    							String type = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "name"));
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int numberSockets = Integer.parseInt(value);
    							
    							if(Log.loggingDebug)
    								Log.debug("Load Item Sockets type="+type+" numberSockets="+numberSockets);
    							
    							for (int ii=0;ii<numberSockets;ii++) {
    								itemSockets.put(itemSockets.size(),new SocketInfo(itemSockets.size(),type));
    							} 
    						}/* else if (effectType.equals("Parry")) {
    							// Used to turn an item into a Mob when activated
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							if (value.length()>0)
    								tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_PARRY_EFFECT, true);
    							else
    								tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_PARRY_EFFECT, false);
    						}*/ else if (effectType.equals("VipPoints")) {
    							// Starts a quest for the player when the item is activated
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int points = Integer.parseInt(value);
    							//tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new VipItemActivateHook(points,0));
    							ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<ActivateHook>();
    							}
    							ah.add(new VipItemActivateHook(points,0,oadelete));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						} else if (effectType.equals("VipTime")) {
    							// Starts a quest for the player when the item is activated
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int time = Integer.parseInt(value);
    							//tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new VipItemActivateHook(0,time));
    							ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<ActivateHook>();
    							}
    							ah.add(new VipItemActivateHook(0,time,oadelete));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						} else if (effectType.equals("Bonus")) {
    							// Starts a quest for the player when the item is activated
    							String type = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "name"));
    							String values = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int value = 0;
    							float valuep = 0f;
    							if(values.contains("|")) {
    								String[] v = values.split("\\|");
    								if(Log.loggingDebug)
    									Log.debug("item bonus "+v);
    								value = Integer.parseInt(v[0]);
    								valuep = Float.parseFloat(v[1]);
    							}
    							bonuses.add(new BonusSettings("",type,value,valuep,-1));
    //							
    						} else if (effectType.equals("SkillPoints")) {
    							// Starts a quest for the player when the item is activated
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int points = 1;
    							
    							try {
    								points = Integer.parseInt(value);
    							} catch (NumberFormatException e) {
    							}	
    							//tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new SkillPointsItemActivateHook(points));
    							ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<ActivateHook>();
    							}
    							ah.add(new  SkillPointsItemActivateHook(points,oadelete));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						} else if (effectType.equals("TalentPoints")) {
    							// Starts a quest for the player when the item is activated
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int points = 1;
    							try {
    								points = Integer.parseInt(value);
    							} catch (NumberFormatException e) {
    							}
    						//	tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new TalentPointsItemActivateHook(points));
    							ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<ActivateHook>();
    							}
    							ah.add(new TalentPointsItemActivateHook(points,oadelete));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						} else if (effectType.equals("SkillReset")) {
    						//	tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new SkillResetActivateHook());
    							ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<ActivateHook>();
    							}
    							ah.add(new SkillResetActivateHook(oadelete));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						} else if (effectType.equals("TalentReset")) {
    						//	tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new TalentResetActivateHook());
    							ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<ActivateHook>();
    							}
    							ah.add(new TalentResetActivateHook(oadelete));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						}else if (effectType.equals("Achievement")) {
    							// Starts a quest for the player when the item is activated
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int id = 1;
    							try {
    								id = Integer.parseInt(value);
    							} catch (NumberFormatException e) {
    							}
    							//tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new AchievementItemActivateHook(id));
    							ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    							if(ah==null) {
    								ah = new ArrayList<ActivateHook>();
    							}
    							ah.add(new AchievementItemActivateHook(id,oadelete));
    							tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						} else if (effectType.equals("SocketEffect")) {
    							//String type = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "name"));
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int effectID = -1;
    							try {
    								effectID = Integer.parseInt(value);
    								ArrayList<Integer> effects = (ArrayList<Integer>) tmpl.get(InventoryClient.ITEM_NAMESPACE,"socketEffects");
        							if (effects == null) {
        								effects = new ArrayList<Integer>();
        							}
        							effects.add(effectID);        							
        							tmpl.put(InventoryClient.ITEM_NAMESPACE,"socketEffects", effects);
    							} catch (NumberFormatException e) {
    							}
    						} else if (effectType.equals("SocketAbility")) {
    							//String type = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "name"));
    							String value = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							int abilityID = -1;
    							try {
    								abilityID = Integer.parseInt(value);
    								ArrayList<Integer> abilities = (ArrayList<Integer>) tmpl.get(InventoryClient.ITEM_NAMESPACE,"socketAbilities");
        							if (abilities == null) {
        								abilities = new ArrayList<Integer>();
        							}
        							abilities.add(abilityID);        							
        							tmpl.put(InventoryClient.ITEM_NAMESPACE,"socketAbilities", abilities);
    							} catch (NumberFormatException e) {
    							}
    						}
    					}
    					if(Log.loggingDebug)
    							Log.debug("Load Item Sockets itemSockets="+itemSockets);
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "bonuses", bonuses);
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "bonusStats", itemStats);
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "sockets", itemSockets);
    					tmpl.put(InventoryClient.ITEM_NAMESPACE, "enchantLevel", 0);
    								
    					// Weapon damage
    					if (itemType.equals("Weapon")) {
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "damage", rs.getInt("damage"));
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "damageMax", rs.getInt("damageMax"));
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "damageType", HelperFunctions.readEncodedString(rs.getBytes("damageType")));
    						float delay = rs.getFloat("delay") * 1000;
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "delay", (int)delay);
    					} else if (itemType.equals("Ammo")) {
    						// Set ammo activate hook
    						String value = HelperFunctions.readEncodedString(rs.getBytes("slot"));
    						int ammoTypeID = Integer.parseInt(value);
    						//TODO: read in ammo effect
    						//tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, new AmmoItemActivateHook(ammoTypeID));
    						ArrayList<ActivateHook> ah = (ArrayList<ActivateHook>) tmpl.get(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK);
    						if(ah==null) {
    							ah = new ArrayList<ActivateHook>();
    						}
    						ah.add(new AmmoItemActivateHook(ammoTypeID,oadelete));
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, InventoryClient.TEMPL_ACTIVATE_HOOK, ah);
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "damage", rs.getInt("damage"));
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, "damageMax", rs.getInt("damageMax"));
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, AgisItem.AMMO_TYPE, ammoTypeID);
    						tmpl.put(InventoryClient.ITEM_NAMESPACE, AgisItem.TEMPL_UNACQUIRE_HOOK, new AmmoItemUnacquireHook());
    					}
    					//tmpl.put(InventoryClient.ITEM_NAMESPACE, "actionBarAllowed", rs.getBoolean("actionBarAllowed"));
    					//tmpl.put(InventoryClient.ITEM_NAMESPACE, "tooltip", rs.getString("tooltip"));
    					list.add(tmpl);
                    templatesById.put(templateId, tmpl);
                    templateIds.add(templateId);
    					if(Log.loggingDebug)
    						Log.debug("ITEM: loaded item template " + rs.getInt("id") + " with name: " + HelperFunctions.readEncodedString(rs.getBytes("name")));
    				}
    			}
		    }
		} catch (SQLException e) {
			Log.dumpStack("ITEM: loaded item template Exception:"+e);
			e.printStackTrace();
		}
    
    // Batch load item requirements and item sets
    Map<Integer, HashMap<Integer, HashMap<String, Integer>>> itemRequirements = batchGetItemRequirements(templateIds);
    Map<Integer, Integer> itemSetIds = batchLoadItemSetForTemplates(templateIds);
    
    // Update templates with the pre-loaded data
    for (Template tmpl : list) {
        int templateId = tmpl.getTemplateID();
        tmpl.put(InventoryClient.ITEM_NAMESPACE, "requirements", itemRequirements.get(templateId));
        tmpl.put(InventoryClient.ITEM_NAMESPACE, "item_set", itemSetIds.get(templateId));
    }
		return list;
	}
	
	private Map<Integer, HashMap<Integer, HashMap<String, Integer>>> batchGetItemRequirements(List<Integer> templateIds) {
	    Map<Integer, HashMap<Integer, HashMap<String, Integer>>> itemRequirements = new HashMap<>();
	    if (templateIds.isEmpty()) {
	        return itemRequirements;
	    }
	    String placeholders = String.join(",", Collections.nCopies(templateIds.size(), "?"));
	    String sql = "SELECT * FROM item_templates_options WHERE item_id IN (" + placeholders + ") AND isactive=1";
	    try (PreparedStatement ps = queries.prepare(sql)) {
	        for (int i = 0; i < templateIds.size(); i++) {
	            ps.setInt(i + 1, templateIds.get(i));
	        }
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    int itemID = rs.getInt("item_id");
	                    int requirementType = rs.getInt("editor_option_type_id");
	                    String requirementOption = HelperFunctions.readEncodedString(rs.getBytes("editor_option_choice_type_id"));
	                    int requiredValue = rs.getInt("required_value");
	                    
	                    itemRequirements.computeIfAbsent(itemID, k -> new HashMap<>());
	                    HashMap<Integer, HashMap<String, Integer>> requirementMap = itemRequirements.get(itemID);
	                    requirementMap.computeIfAbsent(requirementType, k -> new HashMap<>());
	                    HashMap<String, Integer> requirementOptions = requirementMap.get(requirementType);
	                    requirementOptions.put(requirementOption, requiredValue);
	                }
	            }
	        }
	    } catch (SQLException e) {
	        Log.dumpStack("ITEM: batchGetItemRequirements loaded Exception:" + e);
	        e.printStackTrace();
	    }
	    
        for (Integer templateId : templateIds)
            itemRequirements.computeIfAbsent(templateId, k -> new HashMap<>());
        
	    return itemRequirements;
	}

	
	private Map<Integer, Integer> batchLoadItemSetForTemplates(List<Integer> templateIds) {
	    Map<Integer, Integer> itemSetIds = new HashMap<>();
	    if (templateIds.isEmpty()) {
	        return itemSetIds;
	    }
	    String placeholders = String.join(",", Collections.nCopies(templateIds.size(), "?"));
	    String sql = "SELECT template_id, set_id FROM item_set_items WHERE template_id IN (" + placeholders + ")";
	    try (PreparedStatement ps = queries.prepare(sql)) {
	        for (int i = 0; i < templateIds.size(); i++) {
	            ps.setInt(i + 1, templateIds.get(i));
	        }
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    int templateId = rs.getInt("template_id");
	                    int setId = rs.getInt("set_id");
	                    itemSetIds.put(templateId, setId);
	                }
	            }
	        }
	    } catch (SQLException e) {
	        Log.dumpStack("batchLoadItemSetForTemplates: Error SQL " + e.getMessage());
	        e.printStackTrace();
	    }
	    
        for (Integer templateId : templateIds) {
            if (!itemSetIds.containsKey(templateId))
                itemSetIds.put(templateId, 0); // Or any default value
        }
        
	    return itemSetIds;
	}

	
	String GetSkillName(String id) {
		
		int c=0;
		String sql ="SELECT id,name FROM `skills` where isactive = 1 and id ="+id;
//			Log.error("GetSkillName sql="+sql);
		try (PreparedStatement ps = queries.prepare(sql)) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return HelperFunctions.readEncodedString(rs.getBytes("name"));
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}
		return "";
	}
	private Map<String, Serializable> getItemRequirementsPrefabData(int itemID, int c) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		HashMap<Integer, HashMap<String, Integer>> requirementMap = new HashMap<Integer, HashMap<String, Integer>>();
		String reqType="";
		String reqNames="";
		String reqValues="";
		try (PreparedStatement ps = queries.prepare("SELECT * FROM item_templates_options where item_id=" + itemID + " AND isactive=1")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int requirementType = rs.getInt("editor_option_type_id");
    					String ReqName = RequirementChecker.getRequirementTypeText(requirementType);
    					String requirementOption = HelperFunctions.readEncodedString(rs.getBytes("editor_option_choice_type_id"));
    					int requiredValue = rs.getInt("required_value");
    					if(requirementType==74) {//Level
    						reqType +=ReqName+";";
    						reqNames +=requirementOption+";";
    						reqValues +=requiredValue+";";
    					}else if(requirementType==75) {//Skill Level
    						reqType +=ReqName+";";
    						reqNames +=GetSkillName(requirementOption)+";";
    						reqValues +=requiredValue+";";
    				
    					}else if(requirementType==76) {//Race
    						reqType +=ReqName+";";
    						reqNames +=RequirementChecker.getRace(Integer.parseInt(requirementOption))+";";
    						reqValues +=requiredValue+";";
    				
    					}else if(requirementType==77) {//Class
    						reqType +=ReqName+";";
    						reqNames +=RequirementChecker.getClass(Integer.parseInt(requirementOption))+";";
    						reqValues +=requiredValue+";";
    				
    					}else if(requirementType==78) {//Stat
    						reqType +=ReqName+";";
    						reqNames +=requirementOption+";";
    						reqValues +=requiredValue+";";
    				
    					} 
    				}
    			}
		    }
			if (reqType.length() > 0)
				reqType = reqType.substring(0, reqType.length() - 1);
			if (reqNames.length() > 0)
				reqNames = reqNames.substring(0, reqNames.length() - 1);
			if (reqValues.length() > 0)
				reqValues = reqValues.substring(0, reqValues.length() - 1);
			props.put("i"+c+"reqType", reqType);
			props.put("i"+c+"reqNames", reqNames);
			props.put("i"+c+"reqValues", reqValues);
		} catch (SQLException e) {
			Log.dumpStack("ITEM: getItemRequirements loaded Exception:"+e);
					e.printStackTrace();
		}
		return props;
	}
	private HashMap<Integer, HashMap<String, Integer>> getItemRequirements(int itemID) {
		HashMap<Integer, HashMap<String, Integer>> requirementMap = new HashMap<Integer, HashMap<String, Integer>>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM item_templates_options where item_id=" + itemID + " AND isactive=1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int requirementType = rs.getInt("editor_option_type_id");
    					String requirementOption = HelperFunctions.readEncodedString(rs.getBytes("editor_option_choice_type_id"));
    					int requiredValue = rs.getInt("required_value");
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
			Log.dumpStack("ITEM: getItemRequirements loaded Exception:"+e);
					e.printStackTrace();
		}
		return requirementMap;
	}
	
	private LinkedList<String> getAbilityCooldowns(int abilityID) {
		LinkedList<String> list = new LinkedList<String>();
	    try (PreparedStatement ps = queries.prepare("SELECT cooldown1Type FROM abilities where id=" + abilityID)) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					if(Log.loggingDebug)
    							Log.debug("COOLDOWN: Reading in cooldown data for ability: " + abilityID);
    					String cooldown = HelperFunctions.readEncodedString(rs.getBytes("cooldown1Type"));
    					if (cooldown != null && !cooldown.equals(""))
    						list.add(cooldown);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("getAbilityCooldowns: loaded Exception:"+e);
				e.printStackTrace();
		}
		Log.debug("COOLDOWN: " + abilityID + " has cooldowns: " + list);
		return list;
	}

	public void LoadSlotsDefinition() {
		Log.debug("LoadSlotsDefinition: Start");
		AgisEquipInfo.DefaultEquipInfo.setEquippableSlots(new ArrayList<AgisEquipSlot>());
		
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM item_slots where isactive=1 ")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					int id = rs.getInt("id");
    					//int type = rs.getInt("type");
    					String stypes = HelperFunctions.readEncodedString(rs.getBytes("type"));
    					String[] types = stypes.split(";");
    					
    					//int socket = rs.getInt("socket");
						if(Log.loggingDebug)Log.debug("LoadSlotsDefinition: id=" + id + " name=" + name + " type=" + stypes /*+ " socket=" + socket*/);
    					AgisEquipSlot s = new AgisEquipSlot(name);
    					s.setId(id);
    					for(String i : types) {
    						if(i.length()>0) {
    							s.addTypeId(Integer.parseInt(i));
    						}
    					}
    					if(AgisInventoryPlugin.EQUIP_SLOTS_COUNT <= id)
    						AgisInventoryPlugin.EQUIP_SLOTS_COUNT = id+1;
    					//s.setSocketId(socket);
    					AgisEquipInfo.DefaultEquipInfo.addEquipSlot(s);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadSlotsDefinition: loaded Exception:" + e);
			e.printStackTrace();
		}
	    
		LoadSlotsGroupDefinition();
		Log.debug("LoadSlotsDefinition: End");
	}
	

	public void LoadSlotsGroupDefinition() {
		Log.debug("LoadSlotsDefinition: Start");
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM item_slots_group where isactive=1 ")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    				//	if (Log.loggingDebug)
    				//		Log.debug("");
    					String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					int id = rs.getInt("id");
    					boolean all = rs.getBoolean("all_slots");

						if(Log.loggingDebug)	Log.debug("LoadSlotsGroupDefinition: id=" + id + " name=" + name + " all=" + all);
    					AgisEquipInfo s = new AgisEquipInfo();
    					s.setName(name);
    					//s.setId(id);
    					s.setAll(all);
    					LoadSlotsGroupsSlots(id,s);
    					
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadSlotsDefinition: loaded Exception:" + e);
			e.printStackTrace();
		}
		Map<String, AgisEquipSlot> list = AgisEquipSlot.getSlots();
		for(AgisEquipSlot aes : list.values()) {
			AgisEquipInfo s = new AgisEquipInfo();
			s.setName(aes.getName());
			s.addEquipSlot(aes);
		}
		Log.debug("LoadSlotsDefinition: End");
	}

	public void LoadSlotsGroupsSlots(int id, AgisEquipInfo aesg) {
		Log.debug("LoadSlotsGroupsSlots: Start");
		try (PreparedStatement ps = queries.prepare("SELECT name FROM item_slots_in_group JOIN item_slots on item_slots_in_group.slot_id = item_slots.id where slot_group_id=" + id)) {
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {

    					String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					AgisEquipSlot aes = AgisEquipSlot.getSlotByName(name);
    					aesg.addEquipSlot(aes);
    					//aesg.getTypeIds();
    					//aes.getTypeId());
						if(Log.loggingDebug)Log.debug("LoadSlotsGroupsSlots: id=" + id + "add slot name=" + name);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadSlotsGroupsSlots: loaded Exception:" + e);
			e.printStackTrace();
		}

		Log.debug("LoadSlotsGroupsSlots: End");
	}

	public void LoadSlotsSets() {
		Log.debug("LoadSlotsSets: Start");
		HashMap<Integer, String> choice = RequirementChecker.getEditorOptionChoice("Slots Sets");
		if(choice == null)
			return;
		HashMap<String, SlotsSet> list = new HashMap<String, SlotsSet>();
		List<Integer> keys = new ArrayList<Integer>(choice.keySet());
		Collections.sort(keys);
		for (Integer set_id: keys ) {
			try (PreparedStatement ps = queries.prepare("SELECT * FROM item_slots_sets join item_slots on item_slots_sets.slot_id = item_slots.id where set_id=" + set_id)) {
				try (ResultSet rs = queries.executeSelect(ps)) {
					if (rs != null) {
						while (rs.next()) {
							SlotsSet ss = list.computeIfAbsent(choice.get(set_id),__ -> new SlotsSet());
							int id = rs.getInt("id");
							int slot_id = rs.getInt("slot_id");
							ss.setName(choice.get(set_id));
							String slot_name = HelperFunctions.readEncodedString(rs.getBytes("name"));
							AgisEquipSlot aes = AgisEquipSlot.getSlotByName(slot_name);
							int race_id = rs.getInt("race");
							int class_id = rs.getInt("class");
							ss.getSlotsForRaceClass(race_id,class_id).add(aes);
							if(Log.loggingDebug)Log.debug("LoadSlotsSets: id=" + id + " add slot "+slot_name+" set_id=" + set_id+":"+choice.get(set_id)+" race_id="+race_id+" class_id="+class_id);
							AgisEquipSlot set_slot = AgisEquipSlot.getSlotByName(choice.get(set_id)+"_"+slot_name);
							if(set_slot == null) {
								AgisEquipSlot s = new AgisEquipSlot("Set_"+choice.get(set_id) + "_" + slot_name);
								int sid =AgisInventoryPlugin.EQUIP_SLOTS_COUNT;
								AgisInventoryPlugin.EQUIP_SLOTS_COUNT++;
								s.setId(sid);
								for (int i : aes.getTypeIds()) {
									s.addTypeId(i);
								}
								if(Log.loggingDebug)Log.debug("LoadSlotsSets: id=" + id + " add slot "+slot_name+" set_id=" + set_id+":"+choice.get(set_id)+" race_id="+race_id+" class_id="+class_id+" slot="+s);
								AgisEquipInfo.DefaultEquipInfo.addEquipSlot(s);
							}
						}
					}
				}
			} catch (SQLException e) {
				Log.dumpStack("LoadSlotsSets: loaded Exception:" + e);
				e.printStackTrace();
			}
		}
		AgisInventoryPlugin.slotsSets = list;
		Log.debug("LoadSlotsSets: End");
	}

	/**
	 * Function to load slots profiles
	 */
	public void LoadSlotsProfileDefinition() {
		Log.debug("LoadSlotsProfileDefinition: Start");
		try (PreparedStatement ps = queries.prepare("SELECT * FROM slots_profile where isactive=1 ")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
						int id = rs.getInt("id");

						if(Log.loggingDebug)	Log.debug("LoadSlotsProfileDefinition: id=" + id + " name=" + name );
						AgisEquipInfoProfile s = new AgisEquipInfoProfile();
						s.setName(name);
						LoadSlotsInProfile(id,s);
						AgisInventoryPlugin.slotsProfiles.put(id, s);
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadSlotsProfileDefinition: loaded Exception:" + e);
			e.printStackTrace();
		}
		Log.debug("LoadSlotsProfileDefinition: End");
	}


	/**
	 * Function to load slots for slots profile
	 * @param id
	 * @param aesg
	 */
	public void LoadSlotsInProfile(int id, AgisEquipInfoProfile aesg) {
		Log.debug("LoadSlotsInProfile: Start");
		try (PreparedStatement ps = queries.prepare("SELECT name FROM slots_in_profile JOIN item_slots on slots_in_profile.slot_id = item_slots.id where slot_profile_id=" + id)) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
						AgisEquipSlot aes = AgisEquipSlot.getSlotByName(name);
						aesg.addEquipSlot(aes);
						if(Log.loggingDebug)Log.debug("LoadSlotsInProfile: id=" + id + "add slot name=" + name);
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadSlotsInProfile: loaded Exception:" + e);
			e.printStackTrace();
		}

		Log.debug("LoadSlotsInProfile: End");
	}

	
	private AgisEquipInfo getEquipInfo(String slot) {
		AgisEquipInfo aei = AgisEquipInfo.getEquipInfo(slot);
		Log.debug("getEquipInfo: "+aei);
		return aei;
	}

	public HashMap<String, Serializable> getSlotsProfilePrefabData() {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int i = 0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM slots_profile where isactive=1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						int id = rs.getInt("id");
						Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
						long date = 0L;
						if (taxPaidUntil != null) {
							date = taxPaidUntil.getTime();
						}
						Log.debug("getSlotsProfilePrefabData: id=" + id+" new " );
						props.put("i"+i+"id", id);
						props.put("i"+i+"date", date);
						props.put("i"+i+"name", HelperFunctions.readEncodedString(rs.getBytes("name")));
						LoadSlotsInProfilePrefabData(i,id,props);
						i++;
					}
				}
			}
			props.put("num", i);
		} catch (SQLException e) {
			Log.dumpStack("ITEM: getSlotsProfilePrefabData loaded Exception:" + e);
			e.printStackTrace();
		}
		return props;
	}

	/**
	 * Loading slots for prefab data of the slots profile
	 * @param c
	 * @param profileId
	 * @param props
	 */
	public void LoadSlotsInProfilePrefabData(int c, int profileId, Map<String, Serializable> props) {
		int i = 0;
		try (PreparedStatement ps = queries.prepare("SELECT name FROM slots_in_profile JOIN item_slots on slots_in_profile.slot_id = item_slots.id where slot_profile_id=" + profileId )) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
						props.put("i" + c + "s" + i, name);
						i++;
					}
				}
			}
			props.put("i" + c + "num", i);
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}


	public HashMap<Integer, MerchantTable> loadMerchantTables() {
		HashMap<Integer, MerchantTable> map = new HashMap<Integer, MerchantTable>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM merchant_tables where isactive = 1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int tableID = rs.getInt("id");
    				    MerchantTable m = new MerchantTable(tableID, HelperFunctions.readEncodedString(rs.getBytes("name")));
    				    loadMerchantItems(m);
    				    map.put(tableID, m);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadMerchantTables: loaded Exception:"+e);
				e.printStackTrace();
		}
		return map;
	}
	
	public void loadMerchantItems(MerchantTable mTbl) {
		ArrayList<Integer> items = new ArrayList<Integer>();
		ArrayList<Integer> counts = new ArrayList<Integer>();
		ArrayList<Integer> refreshTimes = new ArrayList<Integer>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM merchant_item where tableID=" + mTbl.getID() + " AND isactive = 1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					items.add(rs.getInt("itemID"));
    					counts.add(rs.getInt("count"));
    					refreshTimes.add(rs.getInt("refreshTime"));
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadMerchantItems: loaded Exception:"+e);
				e.printStackTrace();
		}
		mTbl.setItems(items);
		mTbl.setItemCounts(counts);
		mTbl.setItemRespawns(refreshTimes);
	}
	
//	public Map<String, Serializable> loadIconCraftingRecipesPrefabData(String objs, OID playerOid) {
//		ArrayList<Currency> currencies = new ArrayList<Currency>();
//		Map<String, Serializable> props = new HashMap<String, Serializable>();
//		props.put("ext_msg_subtype", "CraftingRecipeIcon");
//		int c=0;
//		int count = 0;
//		String sql ="SELECT id,icon,icon2 FROM `crafting_recipes` where isactive = 1 and id in ("+objs+")";
//		if(Log.loggingDebug)
//				Log.debug("loadIconCraftingRecipesPrefabData objs="+objs+"  sql="+sql);
//		try (PreparedStatement ps = queries.prepare(sql)) {
//			try (ResultSet rs = queries.executeSelect(ps)) {
//    			if (rs != null) {
//    				while (rs.next()) {
//    					//Currency newCurrency = new Currency();
//    					int id = rs.getInt("id");
//    					String icon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
//    					String icon2 = HelperFunctions.readEncodedString(rs.getBytes("icon2"));
//    					count += icon2.length();
//    					if(Log.loggingDebug)
//    						Log.debug("loadIconCraftingRecipesPrefabData id="+id+" icon length="+icon2.length());
//    					props.put("i"+c+"id", id);
//    					props.put("i"+c+"icon2", icon2);
//    					props.put("i"+c+"icon", icon);
//    					c++;
//    					if(count > 100000) {
//    						props.put("all", false);
//    						props.put("num",c);
//    						Log.debug("loadIconCraftingRecipesPrefabData send part message");
//    						TargetedExtensionMessage emsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
//    						Engine.getAgent().sendBroadcast(emsg);
//    						props.clear();
//    						props.put("ext_msg_subtype", "CraftingRecipeIcon");
//    						count=0;
//    						c=0;
//    					}
//    				}
//    			}
//			}
//			props.put("num",c);
//		} catch (SQLException e) {
//			e.printStackTrace();
//			Log.dumpStack(" SQLException"+e);
//		}
//		props.put("all", true);
//		TargetedExtensionMessage emsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
//		Engine.getAgent().sendBroadcast(emsg);
//		return props;
//	}
//	public Map<String, Serializable> loadIconCraftingRecipesPrefabData(String objs) {
//		ArrayList<Currency> currencies = new ArrayList<Currency>();
//		Map<String, Serializable> props = new HashMap<String, Serializable>();
//		//props.put("ext_msg_subtype", "CraftingRecipeIcon");
//		int c=0;
//		int count = 0;
//		String sql ="SELECT id,icon,icon2 FROM `crafting_recipes` where isactive = 1 and id in ("+objs+")";
//		if(Log.loggingDebug)
//				Log.debug("loadIconCraftingRecipesPrefabData objs="+objs+"  sql="+sql);
//		try (PreparedStatement ps = queries.prepare(sql)) {
//			try (ResultSet rs = queries.executeSelect(ps)) {
//    			if (rs != null) {
//    				while (rs.next()) {
//    					//Currency newCurrency = new Currency();
//    					int id = rs.getInt("id");
//    					String icon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
//    					String icon2 = HelperFunctions.readEncodedString(rs.getBytes("icon2"));
//    					count += icon2.length();
//    					if(Log.loggingDebug)
//    						Log.debug("loadIconCraftingRecipesPrefabData id="+id+" icon length="+icon2.length());
//    					props.put("i"+c+"id", id);
//    					props.put("i"+c+"icon2", icon2);
//    					props.put("i"+c+"icon", icon);
//    					c++;
//    					/*if(count > 100000) {
//    						props.put("all", false);
//    						props.put("num",c);
//    						Log.error("loadIconCraftingRecipesPrefabData send part message");
//    						TargetedExtensionMessage emsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
//    						Engine.getAgent().sendBroadcast(emsg);
//    						props.clear();
//    						props.put("ext_msg_subtype", "CraftingRecipeIcon");
//    						count=0;
//    						c=0;
//    					}*/
//    				}
//    			}
//			}
//			props.put("num",c);
//		} catch (SQLException e) {
//			e.printStackTrace();
//			Log.dumpStack(" SQLException"+e);
//		}
//		props.put("all", true);
//	/*	TargetedExtensionMessage emsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
//		Engine.getAgent().sendBroadcast(emsg);*/
//		return props;
//	}
	
	
	public Map<String, Serializable> loadIconCraftingRecipesPrefabData(int id, int c) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();

		String sql = "SELECT id,icon,icon2 FROM `crafting_recipes` where isactive = 1 and id =" + id;
		Log.debug("loadIconCraftingRecipesPrefabData   sql=" + sql);
		try (PreparedStatement ps = queries.prepare(sql)) {
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					// Currency newCurrency = new Currency();
    					String icon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
    					String icon2 = HelperFunctions.readEncodedString(rs.getBytes("icon2"));
    					Log.debug("loadIconCraftingRecipesPrefabData id=" + id + " icon length=" + icon2.length());
    					props.put("i" + c + "id", id);
    					props.put("i" + c + "icon2", icon2);
    					props.put("i" + c + "icon", icon);
    					c++;
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException" + e);
		}
		return props;
	}
	
	
	
	
	public HashMap<Integer, CraftingRecipe> loadCraftingRecipes() {
		HashMap<Integer, CraftingRecipe> list = new HashMap<Integer, CraftingRecipe>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM crafting_recipes where isactive = 1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					if(Log.loggingDebug)
    							Log.debug("CRAFTING: loading recipe:" + rs.getInt("id"));
    					CraftingRecipe recipe = new CraftingRecipe(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
    					ArrayList<Integer> resultItems = new ArrayList<Integer>();
    					ArrayList<Integer> resultItemCounts = new ArrayList<Integer>();
    					resultItems.add(rs.getInt("resultItemID"));
    					resultItemCounts.add(rs.getInt("resultItemCount"));
    					for (int i = 2; i <= 4; i++) {
    						Integer resultItem = rs.getInt("resultItem" + i + "ID");
    						if (resultItem != null && resultItem > 0) {
    							resultItems.add(resultItem);
    							resultItemCounts.add(rs.getInt("resultItem" + i + "Count"));
    						}
    					}
    					recipe.setResultItemIds(resultItems);
    					recipe.setResultItemCounts(resultItemCounts);
    					ArrayList<Integer> resultItems2 = new ArrayList<Integer>();
    					ArrayList<Integer> resultItemCounts2 = new ArrayList<Integer>();
    					for (int i = 5; i <= 8; i++) {
    						Integer resultItem2 = rs.getInt("resultItem" + i + "ID");
    						if (resultItem2 != null && resultItem2 > 0) {
    							resultItems2.add(resultItem2);
    							resultItemCounts2.add(rs.getInt("resultItem" + i + "Count"));
    						}
    					}
    					recipe.setResultItemIds2(resultItems2);
    					recipe.setResultItemCounts2(resultItemCounts2);
    					ArrayList<Integer> resultItems3 = new ArrayList<Integer>();
    					ArrayList<Integer> resultItemCounts3 = new ArrayList<Integer>();
    					for (int i = 9; i <= 12; i++) {
    						Integer resultItem3 = rs.getInt("resultItem" + i + "ID");
    						if (resultItem3 != null && resultItem3 > 0) {
    							resultItems3.add(resultItem3);
    							resultItemCounts3.add(rs.getInt("resultItem" + i + "Count"));
    						}
    					}
    					recipe.setResultItemIds3(resultItems3);
    					recipe.setResultItemCounts3(resultItemCounts3);
    					ArrayList<Integer> resultItems4 = new ArrayList<Integer>();
    					ArrayList<Integer> resultItemCounts4 = new ArrayList<Integer>();
    					for (int i = 13; i <= 16; i++) {
    						Integer resultItem4 = rs.getInt("resultItem" + i + "ID");
    						if (resultItem4 != null && resultItem4 > 0) {
    							resultItems4.add(resultItem4);
    							resultItemCounts4.add(rs.getInt("resultItem" + i + "Count"));
    						}
    					}
    					recipe.setResultItemIds4(resultItems4);
    					recipe.setResultItemCounts4(resultItemCounts4);
    					recipe.setResultItemChance(rs.getFloat("chance"));
    					recipe.setResultItemChance2(rs.getFloat("chance2"));
    					recipe.setResultItemChance3(rs.getFloat("chance3"));
    					recipe.setResultItemChance4(rs.getFloat("chance4"));
    					
    					recipe.setSkillID(rs.getInt("skillID"));
    					recipe.setRequiredSkillLevel(rs.getInt("skillLevelReq"));
    					recipe.setRecipeItemId(rs.getInt("recipeItemID"));
    					recipe.setQualityChangeable(rs.getBoolean("qualityChangeable"));
    					recipe.setAllowDyes(rs.getBoolean("allowDyes"));
    					recipe.setAllowEssences(rs.getBoolean("allowEssences"));
    					recipe.setStationReq(HelperFunctions.readEncodedString(rs.getBytes("stationReq")));
    					recipe.setCreationTime(rs.getInt("creationTime"));
    					recipe.setMustMatchLayout(rs.getBoolean("layoutReq"));
    					recipe.setExperience(rs.getInt("crafting_xp"));
    					for (int i = 0; i < CraftingPlugin.GRID_SIZE; i++) {
    						LinkedList<CraftingComponent> componentRow = new LinkedList<CraftingComponent>();
    						for (int j = 0; j < CraftingPlugin.GRID_SIZE; j++) {
    							CraftingComponent component = new CraftingComponent("", 
    									rs.getInt("component" + (i*CraftingPlugin.GRID_SIZE+j+1) + "Count"), rs.getInt("component" + (i*CraftingPlugin.GRID_SIZE+j+1)));
    							componentRow.add(component);
    							if(Log.loggingTrace)
    								Log.trace("CRAFTING: adding item: " + component.getItemId() + " to row: " + i + " in column: " + j + " with count: " + component.getCount());
    						}
    						recipe.addCraftingComponentRow(componentRow);
    					}
    					
    					Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
    					long date = 0L;
    					if (taxPaidUntil != null) {
    						date = taxPaidUntil.getTime();
    					}
    					recipe.SetDate(date);
    					
    					
    					list.put(recipe.getID(), recipe);
    					if(Log.loggingDebug)
    					Log.debug("CRAFTING: put recipe:" + recipe.getID());
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadCraftingRecipes: loaded Exception:"+e);
				e.printStackTrace();
		}
		return list;
	}

	/**
	 * Searches through the objstore table to get the oids of all player
	 * characters.
	 * 
	 * @return: A list of all player character oids
	 */
	public List<Long> getCharacterOids() {
		List<Long> list = new ArrayList<Long>();
		try (PreparedStatement ps = queries.prepare("SELECT DISTINCT obj_id FROM objstore WHERE type = 'PLAYER'")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
    			while (rs.next()) {
    				list.add(rs.getLong("obj_id"));
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("getCharacterOids: loaded Exception:"+e);
				e.printStackTrace();
		}
		return list;
	}
	
	/**
	 * Having too many connection errors, so adding this function to help cope with it
	 */
	public void close() {
		Log.dumpStack("ItemDababase.Close");
		queries.close();
	}
	
	public static final String ITEM_TABLE = "item_templates";
}
