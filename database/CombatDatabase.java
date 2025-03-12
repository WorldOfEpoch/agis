 package atavism.agis.database;

import java.io.Serializable;
import java.sql.*;
import java.util.*;

import atavism.agis.abilities.*;
import atavism.agis.arenas.ArenaCategory;
import atavism.agis.core.*;
import atavism.agis.effects.*;
import atavism.agis.objects.*;
import atavism.agis.objects.Currency;
import atavism.agis.plugins.*;
import atavism.agis.server.combat.DmgBaseStat;
import atavism.agis.server.combat.DmgModifierStat;
import atavism.agis.server.combat.ExperienceStat;
import atavism.agis.util.*;
import atavism.server.engine.*;
import atavism.server.math.Point;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

public class CombatDatabase {
	private static Queries queries;

	public CombatDatabase(boolean keepAlive) {
		if(Log.loggingDebug)Log.debug("CombatDatabase queries="+queries);
        if (queries == null) {
            synchronized (CombatDatabase.class) {
                if (queries == null) {
                    queries = new Queries(keepAlive);
                }
            }
        }
	}

	/**
	 * Get count of the statistic definitions
	 * @return
	 */
	public int getCountStats() {
		int count = 0;
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM stat where isactive=1")) {
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
	 * Loading definitions of the statistics
	 * @return
	 */
	public LinkedList<String> LoadStats() {
		LinkedList<String> statlist = new LinkedList<String>();
		// First load in base stats
		try {
	        try (PreparedStatement ps = queries.prepare("SELECT * FROM stat where type = 0 AND isactive = 1")) {
                try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					String statname = HelperFunctions.readEncodedString(rs.getBytes("name"));
							int id = rs.getInt("id");
        					// Base stat
							CombatPlugin.STAT_NAMES.put(id, statname);
							CombatPlugin.STAT_IDS.put(statname, id);
							BaseStatDef baseStatDef = new BaseStatDef(statname);
							baseStatDef.setId(id);
							baseStatDef.setSendToClient(rs.getBoolean("sendToClient"));
							baseStatDef.setServerPresent(rs.getBoolean("serverPresent"));
							baseStatDef.setMobStartingValue(rs.getInt("mob_base"));
							baseStatDef.addPrecision(rs.getInt("stat_precision"));
        					baseStatDef.setMobLevelIncrease(rs.getInt("mob_level_increase"));
        					baseStatDef.setMobLevelPercentIncrease(rs.getFloat("mob_level_percent_increase"));
							if(Log.loggingDebug)Log.debug("STAT: registering stat: " + statname + " "+rs.getInt("mob_base")+" "+rs.getInt("mob_level_increase")+" "+rs.getInt("mob_level_percent_increase"));
        					CombatPlugin.registerStat(baseStatDef);
        					statlist.add(statname);
        					// Check for purpose of stat
        					String statFunction = HelperFunctions.readEncodedString(rs.getBytes("stat_function"));
        					if (statFunction == null) {
        						// Do nothing
        					
        					} else if (statFunction.equals("Parry")) {
        						CombatPlugin.PARRY_STAT = statname;
        					} else if (statFunction.equals("Sleep Resistance")) {
        						CombatPlugin.SLEEP_RESISTANCE_STAT = statname;
        					} else if (statFunction.equals("Stun Resistance")) {
        						CombatPlugin.STUN_RESISTANCE_STAT = statname;
        					} else if (statFunction.equals("Sleep Chance")) {
        						CombatPlugin.SLEEP_CHANCE_STAT = statname;
        					} else if (statFunction.equals("Stun Chance")) {
        						CombatPlugin.STUN_CHANCE_STAT = statname;
        					} else if (statFunction.equals("Slowdown Resistance")) {
        						CombatPlugin.SLOWDOWN_RESISTANCE_STAT = statname;
        					} else if (statFunction.equals("Immobilize Resistance")) {
        						CombatPlugin.IMMOBILIZE_RESISTANCE_STAT = statname;
        					} else if (statFunction.equals("Movement Speed")) {
        						AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED = statname;
        					} else if (statFunction.equals("Attack Speed")) {
        						CombatPlugin.ATTACK_SPEED_STAT = statname;
        					} else if (statFunction.equals("Ability Cost")) {
        						CombatPlugin.ABILITY_COST_MOD_STAT = statname;
        					} else if (statFunction.equals("Ability Cast Time")) {
        						CombatPlugin.ABILITY_CAST_TIME_MOD_STAT = statname;
        					} else if (statFunction.equals("Ability Cooldown")) {
        						CombatPlugin.ABILITY_COOLDOWN_MOD_STAT = statname;
        					} else if (statFunction.equals("Ability Global Cooldown")) {
        						CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT = statname;
        					} else if (statFunction.equals("Ability Weapon Cooldown")) {
        						CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT = statname;
        					} else if (statFunction.equals("Ability Health Receive")) {
        						CombatPlugin.ABILITY_HEALTH_RECEIVE_MOD_STAT = statname;
        					} else if (statFunction.equals("Ability Health Dealt")) {
        						CombatPlugin.ABILITY_HEALTH_DEALT_MOD_STAT = statname;
        					} else if (statFunction.equals("Ability Damage Receive")) {
        						CombatPlugin.ABILITY_DAMAGE_RECEIVE_MOD_STAT = statname;
        					} else if (statFunction.equals("Ability Damage Dealt")) {
        						CombatPlugin.ABILITY_DAMAGE_DEALT_MOD_STAT = statname;
        					} else if (statFunction.equals("Ability Range")) {
        						CombatPlugin.ABILITY_RANGE_MOD_STAT = statname;
        					} else if (statFunction.equals("Stealth")) {
        						CombatPlugin.STEALTH_STAT = statname;
        					} else if (statFunction.equals("Perception Stealth")) {
        						CombatPlugin.PERCEPTION_STEALTH_STAT = statname;
        					}  else if (statFunction.equals("Interruption Resistance")) {
        						CombatPlugin.INTERRUPTION_RESISTANCE_STAT = statname;
        					} else if (statFunction.equals("Interruption Chance")) {
        						CombatPlugin.INTERRUPTION_CHANCE_STAT = statname;
        					} else if (statFunction.equals("Build Speed")) {
								VoxelPlugin.BUILD_SPEED_STAT = statname;
							}else if (statFunction.equals("exp")) {

							} else if (!statFunction.equals("~ none ~") && !statFunction.equals("")) {
        						Log.error("STAT: type = 0 Unknown stat Function " + statFunction);
        					}
							if(Log.loggingDebug)Log.debug("STAT: added base stat:" + statname);
        					
        				}
        			}
                }
			}

			// Now load in resistance stats
			try (PreparedStatement ps = queries.prepare("SELECT * FROM stat where type = 1 AND isactive = 1")) {
                try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					String statname = HelperFunctions.readEncodedString(rs.getBytes("name"));
							int id = rs.getInt("id");
							CombatPlugin.STAT_NAMES.put(id, statname);
							CombatPlugin.STAT_IDS.put(statname, id);
// Resistance stat (armor stat)
        					ResistanceStatDef statDef = new ResistanceStatDef(statname);
							statDef.setId(id);
							statDef.setSendToClient(rs.getBoolean("sendToClient"));
							statDef.setServerPresent(rs.getBoolean("serverPresent"));
							statDef.addPrecision(rs.getInt("stat_precision"));
							statDef.setMobStartingValue(rs.getInt("mob_base"));
        					statDef.setMobLevelIncrease(rs.getInt("mob_level_increase"));
        					statDef.setMobLevelPercentIncrease(rs.getFloat("mob_level_percent_increase"));
							if(Log.loggingDebug)Log.debug("STAT: added resistance stat:" + statname);
        					CombatPlugin.registerStat(statDef);
        					String statFunction = HelperFunctions.readEncodedString(rs.getBytes("stat_function"));
        					if (statFunction == null) {
        						
        					} else if (!statFunction.equals("~ none ~") && !statFunction.equals("")) {
        						Log.error("STAT: type = 1 Unknown stat Function " + statFunction);
        					}
        					statlist.add(statname);
        				}
        			}
                }
			}

			// Finally load in vitality stats
			try (PreparedStatement ps = queries.prepare("SELECT * FROM stat where type = 2 AND isactive = 1")) {
                try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					String statname = HelperFunctions.readEncodedString(rs.getBytes("name"));
							int id = rs.getInt("id");
							CombatPlugin.STAT_NAMES.put(id, statname);
							CombatPlugin.STAT_IDS.put(statname, id);

							String maxStat = HelperFunctions.readEncodedString(rs.getBytes("maxStat"));
        					AgisStatDef asd = CombatPlugin.lookupStatDef(maxStat);
        					if (asd == null) {
        						Log.error("STAT: maxStat " + maxStat + " for stat " + statname + " is not defined");
        					} else {
        						VitalityStatDef statDef = new VitalityStatDef(statname, CombatPlugin.lookupStatDef(maxStat));
								statDef.addPrecision(rs.getInt("stat_precision"));
								statDef.setId(id);
								setVitalityStatSettings(statDef, rs);
								statDef.setSendToClient(rs.getBoolean("sendToClient"));
								statDef.setServerPresent(rs.getBoolean("serverPresent"));
        						statDef.setMobStartingValue(rs.getInt("mob_base"));
        						statDef.setMobLevelIncrease(rs.getInt("mob_level_increase"));
        						statDef.setMobLevelPercentIncrease(rs.getFloat("mob_level_percent_increase"));
        						CombatPlugin.registerStat(statDef, false, maxStat);
        
        						statlist.add(statname);
        						String statFunction = HelperFunctions.readEncodedString(rs.getBytes("stat_function"));
        						if (statFunction == null) {
        
        						} else if (statFunction.equals("Weight")) {
        							CombatPlugin.WEIGHT_STAT = statname;
        						} else if (statFunction.equals("Health")) {
        							CombatPlugin.HEALTH_STAT = statname;
        							CombatPlugin.HEALTH_MAX_STAT = maxStat;
        						} else if (!statFunction.equals("~ none ~") && !statFunction.equals("")) {
        							Log.error("STAT: type = 2 Unknown stat Function " + statFunction);
        						}
        					}
        				}
        			}
                }
			}

			// Exp stat
			try (PreparedStatement ps = queries.prepare("SELECT * FROM stat where type = 3 AND isactive = 1")) {
				try (ResultSet rs = queries.executeSelect(ps)) {
					if (rs != null) {
						while (rs.next()) {
							String statname = HelperFunctions.readEncodedString(rs.getBytes("name"));
							int id = rs.getInt("id");
							CombatPlugin.STAT_NAMES.put(id, statname);
							CombatPlugin.STAT_IDS.put(statname, id);

							String maxStat = HelperFunctions.readEncodedString(rs.getBytes("maxStat"));
							AgisStatDef asd = CombatPlugin.lookupStatDef(maxStat);
							if (asd == null) {
								Log.error("STAT: maxStat " + maxStat + " for stat " + statname + " is not defined");
							} else {
								ExperienceStat statDef = new ExperienceStat(statname);
								statDef.setId(id);
								statDef.setSendToClient(rs.getBoolean("sendToClient"));
								statDef.setServerPresent(rs.getBoolean("serverPresent"));
								statDef.setMaxStatName(maxStat);
								CombatPlugin.EXPERIENCE_STAT = statname;
								CombatPlugin.EXPERIENCE_MAX_STAT = maxStat;
								CombatPlugin.registerStat(statDef, false, maxStat);
								//statlist.add(statname);
							}
						}
					}
				}
			}
			// DmgBaseStat
			try (PreparedStatement ps = queries.prepare("SELECT * FROM stat where type = 4 AND isactive = 1")) {
				try (ResultSet rs = queries.executeSelect(ps)) {
					if (rs != null) {
						while (rs.next()) {
							String statname = HelperFunctions.readEncodedString(rs.getBytes("name"));
							int id = rs.getInt("id");
							CombatPlugin.STAT_NAMES.put(id, statname);
							CombatPlugin.STAT_IDS.put(statname, id);
							DmgBaseStat statDef = new DmgBaseStat(statname);
							statDef.setMin(rs.getInt("min"));
							statDef.setMax(rs.getInt("max"));
							statDef.setSendToClient(rs.getBoolean("sendToClient"));
							statDef.setServerPresent(rs.getBoolean("serverPresent"));
							statDef.setId(id);
							CombatPlugin.registerStat(statDef);
							//statlist.add(statname);

						}
					}
				}
			}


			// Pet Count
			try (PreparedStatement ps = queries.prepare("SELECT * FROM stat where type = 5 AND isactive = 1")) {
				try (ResultSet rs = queries.executeSelect(ps)) {
					if (rs != null) {
						while (rs.next()) {
							String statname = HelperFunctions.readEncodedString(rs.getBytes("name"));
							int id = rs.getInt("id");
							CombatPlugin.STAT_NAMES.put(id, statname);
							CombatPlugin.STAT_PETS_COUNT.put(id,statname);
							CombatPlugin.STAT_IDS.put(statname, id);
							BaseStatDef statDef = new BaseStatDef(statname);
							statDef.setId(id);
							CombatPlugin.registerStat(statDef);
							statlist.add(statname);

						}
					}
				}
			}
			// Pet Global Count
			try (PreparedStatement ps = queries.prepare("SELECT * FROM stat where type = 6 AND isactive = 1")) {
				try (ResultSet rs = queries.executeSelect(ps)) {
					if (rs != null) {
						while (rs.next()) {
							String statname = HelperFunctions.readEncodedString(rs.getBytes("name"));
							int id = rs.getInt("id");
							CombatPlugin.STAT_NAMES.put(id, statname);
							CombatPlugin.STAT_IDS.put(statname, id);
							CombatPlugin.PET_GLOBAL_COUNT_STAT = statname;
							BaseStatDef statDef = new BaseStatDef(statname);
							statDef.setId(id);
							CombatPlugin.registerStat(statDef);
							statlist.add(statname);

						}
					}
				}
			}

		} catch (SQLException e) {
			Log.dumpStack("LoadStats: SQLException:"+e);
			e.printStackTrace();
		}  catch (Exception e) {
			Log.dumpStack("LoadStats: Exception:"+e);
			e.printStackTrace();
		}
		
		// Load in stat links
		for(String statname : statlist) {
			LoadStatLinks(CombatPlugin.lookupStatDef(statname));
		}
				
		return statlist;
	}

	public HashMap<String, Serializable> LoadStatsPrefabData() {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int c = 0;
		try {
			try (PreparedStatement ps = queries.prepare("SELECT * FROM stat where isactive = 1")) {
				try (ResultSet rs = queries.executeSelect(ps)) {
					if (rs != null) {
						while (rs.next()) {
							int id = rs.getInt("id");
							int prec = rs.getInt("stat_precision");
							Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
							long date = 0L;
							if (taxPaidUntil != null) {
								date = taxPaidUntil.getTime();
							}
							if(Log.loggingDebug)Log.debug("LoadStatsPrefabData: id=" + id );
							props.put("s" + c + "id", id);
							props.put("s" + c + "prec", prec);
							props.put("s" + c + "name", HelperFunctions.readEncodedString(rs.getBytes("name")));
							props.put("s" + c + "date", date);
							c++;
						}
					}
				}
			}
			props.put("num", c);
		} catch (SQLException e) {
			Log.dumpStack("LoadStats: SQLException:"+e);
			e.printStackTrace();
		}  catch (Exception e) {
			Log.dumpStack("LoadStats: Exception:"+e);
			e.printStackTrace();
		}

		return props;
	}


	/**
	 * Set the properties for the VitalityStatDef. Loads in the values from the
	 * ResultSet.
	 * 
	 * @param statDef
	 * @param rs
	 */
	public void setVitalityStatSettings(VitalityStatDef statDef, ResultSet rs) {
		try {
			int min = rs.getInt("min");
			int shiftTarget = rs.getInt("shiftTarget");
			int shiftValue = rs.getInt("shiftValue");
			int shiftReverseValue = rs.getInt("shiftReverseValue");
			int shiftInterval = rs.getInt("shiftInterval");
			boolean isShiftPercent = rs.getBoolean("isShiftPercent");
			String onMaxHit = HelperFunctions.readEncodedString(rs.getBytes("onMaxHit"));
			String onMinHit = HelperFunctions.readEncodedString(rs.getBytes("onMinHit"));
			String onThreshold = HelperFunctions.readEncodedString(rs.getBytes("onThreshold"));
			String onThreshold2 = HelperFunctions.readEncodedString(rs.getBytes("onThreshold2"));
			String onThreshold3 = HelperFunctions.readEncodedString(rs.getBytes("onThreshold3"));
			String onThreshold4 = HelperFunctions.readEncodedString(rs.getBytes("onThreshold4"));
			String onThreshold5 = HelperFunctions.readEncodedString(rs.getBytes("onThreshold5"));
			boolean canExceedMax = rs.getBoolean("canExceedMax");
			Float threshold = rs.getFloat("threshold");
			Float threshold2 = rs.getFloat("threshold2");
			Float threshold3 = rs.getFloat("threshold3");
			Float threshold4 = rs.getFloat("threshold4");
			if (threshold > 0)
				statDef.setThreshold(threshold / 100f);
			if (threshold2 > 0)
				statDef.setThreshold2(threshold2 / 100f);
			if (threshold3 > 0)
				statDef.setThreshold3(threshold3 / 100f);
			if (threshold4 > 0)
				statDef.setThreshold4(threshold4 / 100f);
			String shiftModStat = HelperFunctions.readEncodedString(rs.getBytes("shiftModStat"));
			statDef.setShiftModStat(shiftModStat);
			statDef.setMin(min);
			statDef.setMax(100);
			statDef.setShiftTarget(shiftTarget);
			statDef.setShiftValue(shiftValue);
			statDef.setReverseShiftValue(shiftReverseValue);
			statDef.setShiftInterval(shiftInterval);
			statDef.isShiftPercent(isShiftPercent);
			
			if (onMaxHit != null && !onMaxHit.isEmpty()
					&& !onMaxHit.equals("~ none ~")) {
				statDef.setOnMaxHit(onMaxHit);
			}
			
			if (onMinHit != null && !onMinHit.isEmpty()
					&& !onMinHit.equals("~ none ~")) {
				statDef.setOnMinHit(onMinHit);
			}
			
			if (onThreshold != null && !onThreshold.isEmpty()
					&& !onThreshold.equals("~ none ~")) {
				statDef.setOnThresholdHit(onThreshold);
			}
			
			if (onThreshold2 != null && !onThreshold2.isEmpty()
					&& !onThreshold2.equals("~ none ~")) {
				statDef.setOnThreshold2Hit(onThreshold2);
			}
			
			if (onThreshold3 != null && !onThreshold3.isEmpty()
					&& !onThreshold3.equals("~ none ~")) {
				statDef.setOnThreshold3Hit(onThreshold3);
			}
			
			if (onThreshold4 != null && !onThreshold4.isEmpty()
					&& !onThreshold4.equals("~ none ~")) {
				statDef.setOnThreshold4Hit(onThreshold4);
			}
			
			if (onThreshold5 != null && !onThreshold5.isEmpty()
					&& !onThreshold5.equals("~ none ~")) {
				statDef.setOnThreshold5Hit(onThreshold5);
			}
			
			// Requirements
			String shiftReq = HelperFunctions.readEncodedString(rs.getBytes("shiftReq1"));
			boolean shiftReqState = rs.getBoolean("shiftReq1State");
			boolean shiftReqSetReverse = rs.getBoolean("shiftReq1SetReverse");
			if (shiftReq != null && !shiftReq.isEmpty() && !shiftReq.equals("~ none ~")) {
				statDef.addShiftRequirement(shiftReq, shiftReqState,
						shiftReqSetReverse);
			}
			shiftReq = HelperFunctions.readEncodedString(rs.getBytes("shiftReq2"));
			shiftReqState = rs.getBoolean("shiftReq2State");
			shiftReqSetReverse = rs.getBoolean("shiftReq2SetReverse");
			if (shiftReq != null && !shiftReq.isEmpty() && !shiftReq.equals("~ none ~")) {
				statDef.addShiftRequirement(shiftReq, shiftReqState,
						shiftReqSetReverse);
			}
			shiftReq = HelperFunctions.readEncodedString(rs.getBytes("shiftReq3"));
			shiftReqState = rs.getBoolean("shiftReq3State");
			shiftReqSetReverse = rs.getBoolean("shiftReq3SetReverse");
			if (shiftReq != null && !shiftReq.isEmpty() && !shiftReq.equals("~ none ~")) {
				statDef.addShiftRequirement(shiftReq, shiftReqState, shiftReqSetReverse);
			}
			
			// Death and Release reset settings
			statDef.setStartPercent(rs.getInt("startPercent"));
			statDef.setDeathResetPercent(rs.getInt("deathResetPercent"));
			statDef.setReleaseResetPercent(rs.getInt("releaseResetPercent"));
						
			// Can the stat exceed max?
			statDef.setCanExceedMax(canExceedMax);

		} catch (SQLException e) {
			Log.dumpStack("setVitalityStatSettings: SQLException:"+e);
				e.printStackTrace();
		}
	}
	
	public void LoadStatLinks(AgisStatDef statDef) {
		// First load in base stats
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM stat_link where stat = '" + statDef.getName() + "' AND isactive = 1")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					statDef.addStatLink(rs.getString("statTo"), rs.getInt("pointsForChange"), (int) rs.getFloat("changePerPoint"));
    					statDef.addDependent(CombatPlugin.lookupStatDef(rs.getString("statTo")));
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadStatLinks: SQLException:"+e);
			e.printStackTrace();
		}
	}
	
	
	public HashMap<String ,StatThreshold> LoadStatThresholds() {
		
		HashMap<String ,StatThreshold> list = new HashMap<String ,StatThreshold>(); 
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM stat_thresholds order by  stat_function ASC, threshold ASC;")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String statFunction = HelperFunctions.readEncodedString(rs.getBytes("stat_function"));
    					String statname = "";
    					if(statFunction==null) {
    						
    					
    					} else if (statFunction.equals("Parry")) {
    						 statname = CombatPlugin.PARRY_STAT ;
    					} else if (statFunction.equals("Sleep Resistance")) {
    						 statname = CombatPlugin.SLEEP_RESISTANCE_STAT ;
    					} else if (statFunction.equals("Stun Resistance")) {
    						 statname = CombatPlugin.STUN_RESISTANCE_STAT ;
    					}  else if (statFunction.equals("Sleep Chance")) {
    						 statname = CombatPlugin.SLEEP_CHANCE_STAT ;
    					} else if (statFunction.equals("Stun Chance")) {
    						 statname = CombatPlugin.STUN_CHANCE_STAT ;
    					} else if (statFunction.equals("Slowdown Resistance")) {
    						 statname = CombatPlugin.SLOWDOWN_RESISTANCE_STAT ;
    					} else if (statFunction.equals("Immobilize Resistance")) {
    						 statname = CombatPlugin.IMMOBILIZE_RESISTANCE_STAT ;
    					} else if (statFunction.equals("Movement Speed")) {
    						 statname = AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED ;
    					} else if (statFunction.equals("Attack Speed")) {
    						 statname = CombatPlugin.ATTACK_SPEED_STAT ;
    					} else if (statFunction.equals("Ability Cost")) {
    						 statname = CombatPlugin.ABILITY_COST_MOD_STAT ;
    					} else if (statFunction.equals("Ability Cast Time")) {
    						 statname = CombatPlugin.ABILITY_CAST_TIME_MOD_STAT ;
    					} else if (statFunction.equals("Ability Cooldown")) {
    						 statname = CombatPlugin.ABILITY_COOLDOWN_MOD_STAT ;
    					} else if (statFunction.equals("Ability Global Cooldown")) {
    						 statname = CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT ;
    					} else if (statFunction.equals("Ability Weapon Cooldown")) {
    						 statname = CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT ;
    					
    					} else if (statFunction.equals("Ability Health Receive")) {
    						 statname = CombatPlugin.ABILITY_HEALTH_RECEIVE_MOD_STAT ;
    					} else if (statFunction.equals("Ability Health Dealt")) {
    						 statname = CombatPlugin.ABILITY_HEALTH_DEALT_MOD_STAT ;
    					} else if (statFunction.equals("Ability Damage Receive")) {
    						 statname = CombatPlugin.ABILITY_DAMAGE_RECEIVE_MOD_STAT ;
    					} else if (statFunction.equals("Ability Damage Dealt")) {
    						 statname = CombatPlugin.ABILITY_DAMAGE_DEALT_MOD_STAT ;
    					}  else if (statFunction.equals("Ability Range")) {
    						 statname = CombatPlugin.ABILITY_RANGE_MOD_STAT ;
    					} else if (statFunction.equals("Interruption Resistance")) {
    						 statname = CombatPlugin.INTERRUPTION_RESISTANCE_STAT ;
    					}  else if (statFunction.equals("Interruption Chance")) {
    						 statname = CombatPlugin.INTERRUPTION_CHANCE_STAT ;
    					}  else if (statFunction.equals("Build Speed")) {
    						 statname = VoxelPlugin.BUILD_SPEED_STAT ;
    					} else  {
   						 statname = statFunction ;
   						 }
    					/*else if (statFunction.equals("Ability ")) {
    						 statname = CombatPlugin.PHYSICAL_CRITIC_POWER_STAT ;
    					}*/
    					 
    					 
    					 
    					 if(statname != null && !statname.equals("")) {
    						 
    						 if(!list.containsKey(statname)) {
    							 list.put(statname, new StatThreshold(statname));
    						 }
    							 list.get(statname).getThresholds().put(list.get(statname).getThresholds().size()+1,rs.getInt("threshold"));
    							 list.get(statname).getPoints().put(list.get(statname).getPoints().size()+1,rs.getInt("num_per_point"));
    						 
    						// (rs.getInt("threshold"),rs.getInt("num_per_pont"));
    							
    						 
    					 }
    					
    					
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadStatLinks: SQLException:"+e);
			e.printStackTrace();
		}
		return list;
	}

	public HashMap<Integer, StatProfile> LoadStatProfiles() {
		// First load in base stats

		HashMap<Integer, StatProfile> profiles = new HashMap<Integer, StatProfile>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM stat_profile where isactive = 1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						int profile_id = rs.getInt("id");
						String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
						StatProfile sp = new StatProfile();
						sp.setId(profile_id);
						sp.setName(name);
						profiles.put(profile_id,sp);
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadStatLinks: SQLException:"+e);
			e.printStackTrace();
		}
		for(StatProfile profile : profiles.values()) {
			LoadStatProfileStats(profile);
		}
		LoadCharacterStatsAsStatProfile(profiles);
		return profiles;
	}

	public void LoadStatProfileStats(StatProfile profile) {
		// First load in base stats
		try (PreparedStatement ps = queries.prepare("SELECT * FROM stat_profile_stats where profile_id = " + profile.getId())) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						int stat_id = rs.getInt("stat_id");
						int value = rs.getInt("value");
						float levelIncrease = rs.getFloat("level_increase");
						float levelPercentIncrease = rs.getFloat("level_percent_increase");
						boolean override_values = rs.getBoolean("override_values");
						boolean send_to_client = rs.getBoolean("send_to_client");
						boolean serverPresent = rs.getBoolean("serverPresent");
						if(override_values) {
							profile.getStats().put(stat_id, value);
							profile.getStatsLevelIncrease().put(stat_id, levelIncrease);
							profile.getStatsLevelPercentIncrease().put(stat_id, levelPercentIncrease);
						}
						if(!serverPresent)
							profile.addStatsToDelete(CombatPlugin.STAT_NAMES.get(stat_id));
						if(send_to_client)
							profile.addStatsSendToClient(CombatPlugin.STAT_NAMES.get(stat_id));
						else
							profile.addStatsNotSendToClient(CombatPlugin.STAT_NAMES.get(stat_id));
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadStatProfileStats: SQLException:"+e);
			e.printStackTrace();
		}
	}


	public void LoadCharacterStatsAsStatProfile(HashMap<Integer, StatProfile> profiles ) {
		// First load in base stats

		HashMap<Integer, StatProfile> _profiles = new HashMap<Integer, StatProfile>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM character_create_template where isactive = 1")) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						int profile_id = -rs.getInt("id");
						int race_id = rs.getInt("race");
						int class_id = rs.getInt("aspect");
//						String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
						StatProfile sp = new StatProfile();
						sp.setId(profile_id);
						sp.setName(race_id+"_"+class_id);
						_profiles.put(profile_id,sp);
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadCharacterStatsAsStatProfile: SQLException:"+e);
			e.printStackTrace();
		}


		for(StatProfile profile : _profiles.values()) {
			LoadCharacterStatsForStatProfile(profile);
			profiles.put(profile.getId(),profile);
		}
	}


	private void LoadCharacterStatsForStatProfile( StatProfile profile){
		try (PreparedStatement ps = queries.prepare("SELECT * FROM character_create_stats where character_create_id = " + (-profile.getId()))) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						String statname = HelperFunctions.readEncodedString(rs.getBytes("stat"));
//						int stat_id = rs.getInt("stat_id");
						int stat_id = CombatPlugin.STAT_IDS.get(statname);
						int value = rs.getInt("value");
						float levelIncrease = rs.getFloat("levelIncrease");
						float levelPercentIncrease = rs.getFloat("levelPercentIncrease");
						boolean serverPresent = rs.getBoolean("serverPresent");
						boolean send_to_client = rs.getBoolean("sendToClient");
						if(serverPresent) {
							if(CombatPlugin.STAT_LIST.contains(statname)) {
								profile.getStats().put(stat_id, value);
								profile.getStatsLevelIncrease().put(stat_id, levelIncrease);
								profile.getStatsLevelPercentIncrease().put(stat_id, levelPercentIncrease);
							}
						}else{
							profile.addStatsToDelete(CombatPlugin.STAT_NAMES.get(stat_id));
						}
						if(send_to_client)
							profile.addStatsSendToClient(CombatPlugin.STAT_NAMES.get(stat_id));
						else
							profile.addStatsNotSendToClient(CombatPlugin.STAT_NAMES.get(stat_id));
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadCharacterStatsForStatProfile: SQLException:"+e);
			e.printStackTrace();
		}

	}


	/**
	 * Reads in the stats that will be shared/shown to other group members.
	 * @return
	 */
	public LinkedList<String> LoadGroupSharedStats() {
		LinkedList<String> statlist = new LinkedList<String>();
		// First load in base stats
	    try (PreparedStatement ps = queries.prepare("SELECT name FROM stat where sharedWithGroup = 1 AND isactive = 1")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String statname = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					statlist.add(statname);
    				}
    			}
            }
		} catch (SQLException e) {
			Log.dumpStack("LoadGroupSharedStats: SQLException:"+e);
				e.printStackTrace();
		}
		
		return statlist;
	}

	public HashMap<String, DamageType> LoadDamageTypes() {
		HashMap<String, DamageType> damageTypesMap = new HashMap<String, DamageType>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM damage_type where isactive = 1")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					//int id = rs.getInt("id");
    					String damageTypeName = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					String resistanceStat = HelperFunctions.readEncodedString(rs.getBytes("resistance_stat"));
    					String powerStat = HelperFunctions.readEncodedString(rs.getBytes("power_stat"));
    					String accuracyStat = HelperFunctions.readEncodedString(rs.getBytes("accuracy_stat"));
    					String evasionStat = HelperFunctions.readEncodedString(rs.getBytes("evasion_stat"));
    					String criticChanceStat = HelperFunctions.readEncodedString(rs.getBytes("critic_chance_stat"));
    					String criticPowerStat = HelperFunctions.readEncodedString(rs.getBytes("critic_power_stat"));
    					DamageType dt= new DamageType(-1,damageTypeName,resistanceStat,powerStat,accuracyStat,evasionStat,criticChanceStat,criticPowerStat);
    					damageTypesMap.put(damageTypeName, dt);
						if(Log.loggingDebug)Log.debug("DMGTYPE: added damage type: " + damageTypeName
    							+ " with resistance stat: " + resistanceStat);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadDamageTypes: SQLException:"+e);
				e.printStackTrace();
		}
		return damageTypesMap;
	}

	public HashMap<Integer, HashMap<Integer, LevelExpRequirement>> loadLevelExpRequirements() {
		HashMap<Integer, HashMap<Integer, LevelExpRequirement>> levelExpRequirements = new HashMap<Integer, HashMap<Integer, LevelExpRequirement>>();		
		try (PreparedStatement ps = queries
					.prepare("SELECT * FROM `level_xp_requirements` where isactive = 1")) {
            try (ResultSet rs = queries.executeSelect(ps)) 
            {
    			if (rs != null) 
    			{
    				while (rs.next()) 
    				{
    					LevelExpRequirement requirement = new LevelExpRequirement();    					
    					requirement.profile = rs.getInt("xpProfile");
    					requirement.level = rs.getInt("level");
    					requirement.expRequired = rs.getInt("xpRequired");
    					requirement.reward_template_id = rs.getInt("reward_template_id");
    					
    					if (requirement.reward_template_id > 0) 
    					{	
    						loadLevelExpRequirementsRewardTemplates(requirement);
    					}
    					
    					HashMap<Integer, LevelExpRequirement> requirements = new HashMap<Integer, LevelExpRequirement>();  
    					if (levelExpRequirements.containsKey(requirement.profile)) {
    						requirements = levelExpRequirements.get(requirement.profile);
    					}    					
    					requirements.put(requirement.level, requirement);    					
    					levelExpRequirements.put(requirement.profile, requirements);
    				}
    			}
            }
		} catch (SQLException e) {
			Log.dumpStack("loadLevelExpRequirements: SQLException:"+e);
				e.printStackTrace();
		}
		return levelExpRequirements;
	}
	
	private LevelExpRequirement loadLevelExpRequirementsRewardTemplates(LevelExpRequirement requirement)
	{	
		try (PreparedStatement ps = queries
					.prepare("SELECT * FROM `level_xp_requirements_reward_templates` where reward_template_id = ? and isactive = 1"))	{
			ps.setInt(1, requirement.reward_template_id);
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) 
    			{	
    				while (rs.next()) 
					{
    					LevelExpRequirement.RewardTemplate rewardTemplate = requirement.new RewardTemplate();
    					rewardTemplate.reward_template_id = rs.getInt("reward_template_id"); 
    					rewardTemplate.mailMessage = HelperFunctions.readEncodedString(rs.getBytes("reward_mail_message"));
    					rewardTemplate.mailSubject = HelperFunctions.readEncodedString(rs.getBytes("reward_mail_subject"));
    					loadLevelExpRequirementsRewards(requirement, rewardTemplate);
					}
    			}
            }
		} catch (SQLException e) {
			Log.dumpStack("loadLevelExpRequirementsRewards: SQLException:"+e);
				e.printStackTrace();
		}
		return requirement;
	}
	
	private void loadLevelExpRequirementsRewards(LevelExpRequirement requirement, LevelExpRequirement.RewardTemplate rewardTemplate)
	{	
		try (PreparedStatement ps = queries
				.prepare("SELECT * FROM `level_xp_requirements_rewards` where reward_template_id = ? and isactive = 1"))	{
			ps.setInt(1, requirement.reward_template_id);
	        try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) 
				{	
					while (rs.next()) 
					{
						LevelExpRequirement.Reward reward = requirement.new Reward();   					
						reward.reward_id = rs.getInt("reward_id");
						switch (rs.getString("reward_type")) 
						{
							case "ITEM":
								reward.reward_type = LevelExpRequirement.RewardType.ITEM;
								break;
							case "ITEM_MAIL":
								reward.reward_type = LevelExpRequirement.RewardType.ITEM_MAIL;
								break;
							case "SKILL_POINT":
								reward.reward_type = LevelExpRequirement.RewardType.SKILL_POINT;
								break;
							case "TALENT_POINT":
								reward.reward_type = LevelExpRequirement.RewardType.TALENT_POINT;
								break;   
							case "ABILITY":
								reward.reward_type = LevelExpRequirement.RewardType.ABILITY;
								break;
							case "EFFECT":
								reward.reward_type = LevelExpRequirement.RewardType.EFFECT;
								break;
						}
						
						if (reward.reward_type == null) {
							Log.error("loadLevelExpRequirementsRewardTemplates id="+ reward.reward_id + " Reward Type is null");
							continue;
						}
						
						reward.reward_value = rs.getInt("reward_value");
						reward.reward_amount = rs.getInt("reward_amount");
						reward.give_once = rs.getBoolean("give_once");
						reward.on_level_down = rs.getBoolean("on_level_down");
						rewardTemplate.addReward(reward);
					}
					requirement.rewardTemplate = rewardTemplate;
				}
	        }
		} catch (SQLException e) {
			Log.dumpStack("loadLevelExpRequirementsRewardTemplates: SQLException:"+e);
				e.printStackTrace();
		}
	}
	

	/**
	 * Get count of the effect definitions
	 * @return
	 */
	public int getCountEffects() {
		int count = 0;
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM effects where isactive=1")) {
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
	 * Loads in all of the combat effects from the effects table. It gets the
	 * effectMainType field for each property then calls the matching function
	 * to read in and set the data needed for the effect type.
	 * 
	 * @return
	 */
	public ArrayList<AgisEffect> loadCombatEffects() {
		
		ArrayList<AgisEffect> list = new ArrayList<AgisEffect>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM effects where isactive = 1")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String effectMainType = HelperFunctions.readEncodedString(rs.getBytes("effectMainType"));
						if(Log.loggingDebug)Log.debug("LoadCombatEffects "+effectMainType);
    					if (effectMainType.equals("Damage")) {
    						AgisEffect effect = loadDamageEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Restore")) {
    						AgisEffect effect = loadRestorationEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Revive")) {
    						AgisEffect effect = loadReviveEffect(rs);
    						if (effect != null)
    							list.add(effect);
//    					} else if (effectMainType.equals("Damage Mitigation")) {
//    						AgisEffect effect = loadDamageMitigationEffect(rs);
//    						if (effect != null)
//    							list.add(effect);
    					} else if (effectMainType.equals("Stat")) {
    						AgisEffect effect = loadStatEffect(rs);
    						if (effect != null)
    							list.add(effect);
//    					} else if (effectMainType.equals("Property")) {
//    						AgisEffect effect = loadPropertyEffect(rs);
//    						if (effect != null)
//    							list.add(effect);
    					} else if (effectMainType.equals("State")) {
    						AgisEffect effect = loadStateEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Morph")) {
    						AgisEffect effect = loadMorphEffect(rs);
    						if (effect != null)
    							list.add(effect);
//    					} else if (effectMainType.equals("Cooldown")) {
//    						AgisEffect effect = loadCooldownEffect(rs);
//    						if (effect != null)
//    							list.add(effect);
    					} else if (effectMainType.equals("Stun")) {
    						AgisEffect effect = loadStunEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Sleep")) {
    						AgisEffect effect = loadSleepEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Immune")) {
    						AgisEffect effect = loadImmuneEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Teleport")) {
    						AgisEffect effect = loadTeleportEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					}else if (effectMainType.equals("CreateItemFromLoot")) {
    						 	AgisEffect effect = loadCreateItemFromLootEffect(rs);
    					  if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("CreateItem")) {
    							AgisEffect effect = loadCreateItemEffect(rs);
    								if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Task")) {
    						AgisEffect effect = loadTaskEffect(rs);
    						if (effect != null)
    							list.add(effect);
//    					} else if (effectMainType.equals("Extension Message")) {
//    						AgisEffect effect = loadExtensionMessageEffect(rs);
//    						if (effect != null)
//    							list.add(effect);
    					} else if (effectMainType.equals("Spawn")) {
    						AgisEffect effect = loadSpawnEffect(rs);
    						if (effect != null)
    							list.add(effect);
//    					} else if (effectMainType.equals("Despawn")) {
//    						AgisEffect effect = loadDespawnEffect(rs);
//    						if (effect != null)
//    							list.add(effect);
//    					} else if (effectMainType.equals("Alter Skill Level")) {
//    						AgisEffect effect = loadAlterSkillCurrentEffect(rs);
//    						if (effect != null)
//    							list.add(effect);
    					} else if (effectMainType.equals("Teach Skill")) {
    						AgisEffect effect = loadTeachSkillEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					}  else if (effectMainType.equals("Teach Ability")) {
    						AgisEffect effect = loadTeachAbilityEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					}  else if (effectMainType.equals("Unlearn Ability")) {
    						AgisEffect effect = loadUnlearnAbilityEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Mount")) {
    						AgisEffect effect = loadMountEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Threat")) {
    						AgisEffect effect = loadThreatEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Dispel")) {
    						AgisEffect effect = loadDispelEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Set Respawn Location")) {
    						AgisEffect effect = loadSetRespawnLocationEffect(rs);
    						if (effect != null)
    							list.add(effect);
//    					} else if (effectMainType.equals("Lockpick")) {
//    						AgisEffect effect = loadLockpickEffect(rs);
//    						if (effect != null)
//    							list.add(effect);
    					} else if (effectMainType.equals("Other") || effectMainType.equals("Build Object") ) {
    						AgisEffect effect = loadOtherEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					}else if (effectMainType.equals("Bonus")) {
    						AgisEffect effect = loadBonusesEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Vip")) {
    						AgisEffect effect = loadVipEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Trap")) {
    						AgisEffect effect = loadTrapEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					}  else if (effectMainType.equals("Stealth")) {
    						AgisEffect effect = loadStealthEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					}  else if (effectMainType.equals("Shield")) {
    						AgisEffect effect = loadShieldEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("Trigger")) {
    						AgisEffect effect = loadTriggerEffect(rs);
    						if (effect != null)
    							list.add(effect);
    					} else if (effectMainType.equals("ChangeClass")) {
    						AgisEffect effect = loadChangeClassEffect(rs);
    						if (effect != null)
    							list.add(effect);
						} else if (effectMainType.equals("SpawnInteractiveObject")){
							AgisEffect effect = loadSpawnInteractiveObjectEffect(rs);
							if(effect != null)
								list.add(effect);
						} else if (effectMainType.equals("Experience")) {
							AgisEffect effect = loadExperienceEffect(rs);
							if (effect != null)
								list.add(effect);
						} else if (effectMainType.equals("SkillExperience")) {
							AgisEffect effect = loadSkillExperienceEffect(rs);
							if (effect != null)
								list.add(effect);
						}
					}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadCombatEffects: SQLException:"+e);
			e.printStackTrace();
		}
		return list;
	}
	
	
	
	public HashMap<String, Serializable> loadEffectsPrefabData() {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();

		int c = 0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM effects where isactive = 1 ")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
    					long date = 0L;
    					if (taxPaidUntil != null) {
    						date = taxPaidUntil.getTime();
    					}
						if(Log.loggingDebug)Log.debug("loadEffectsPrefabData: id=" + id + " new");
    					props.put("i" + c + "id", id);
    					props.put("i" + c + "name", HelperFunctions.readEncodedString(rs.getBytes("name")));
    					String tooltip = HelperFunctions.readEncodedString(rs.getBytes("tooltip"));
    					if (tooltip == null)
    						tooltip = "";
    					props.put("i" + c + "tooltip", tooltip);
    					props.put("i" + c + "buff", rs.getBoolean("isBuff"));
    					props.put("i" + c + "stackL", rs.getInt("stackLimit"));
    					props.put("i" + c + "stackT", rs.getBoolean("stackTime"));
    					props.put("i" + c + "aM", rs.getBoolean("allowMultiple"));
    					props.put("i" + c + "date", date);
    					String icon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
    					String icon2 = HelperFunctions.readEncodedString(rs.getBytes("icon2"));
    					props.put("i" + c + "id", id);
    					props.put("i" + c + "icon2", icon2);
    					props.put("i" + c + "icon", icon);
    					props.put("i" + c + "show", rs.getBoolean("show_effect"));
    					
    					c++;
    				}
    			}
			}
			props.put("num", c);
		} catch (SQLException e) {
			Log.dumpStack("loadEffectsPrefabData: SQLException:" + e);
			e.printStackTrace();
		}
		return props;
	}

	/**
	 * Load in the specific data for a Experience Effect.
	 * @param rs
	 * @return
	 */
	public AgisEffect loadExperienceEffect(ResultSet rs) {
		if(Log.loggingDebug)Log.debug("loadExperienceEffect");

		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("ExperienceEffect")) {
				ExperienceEffect effect = new ExperienceEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				int Experience = rs.getInt("intValue1");
				if(Log.loggingDebug)Log.debug("loadExperienceEffect Experience="+Experience);
				effect.setExperience(Experience);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadExperienceEffect: SQLException:" + e);
			e.printStackTrace();
		}
		if(Log.loggingDebug)Log.debug("loadExperienceEffect END");

		return null;
	}

	/**
	 * Load in the specific data for a Skill Experience Effect.
	 * @param rs
	 * @return
	 */
	public AgisEffect loadSkillExperienceEffect(ResultSet rs) {
		if(Log.loggingDebug)Log.debug("loadSkillExperienceEffect");

		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("SkillExperienceEffect")) {
				SkillExperienceEffect effect = new SkillExperienceEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				int skill = rs.getInt("intValue1");
				int experience = rs.getInt("intValue2");
				if(Log.loggingDebug)Log.debug("loadSkillExperienceEffect Skill="+skill+" Experience="+experience);
				effect.setExperience(experience);
				effect.setSkill(skill);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadSkillExperienceEffect: SQLException:" + e);
			e.printStackTrace();
		}
		if(Log.loggingDebug)Log.debug("loadSkillExperienceEffect END");

		return null;
	}


	/**
	 * Load in the specific data for a Spawn Interactive Object Effect.
	 * @param rs
	 * @return
	 */
	public AgisEffect loadSpawnInteractiveObjectEffect(ResultSet rs) {
		if(Log.loggingDebug)Log.debug("loadSpawnInteractiveObjectEffect");

		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("SpawnInteractiveObjectEffect")) {
				SpawnInteractiveObjectEffect effect = new SpawnInteractiveObjectEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				int templateId = rs.getInt("intValue1");
				if(Log.loggingDebug)Log.debug("loadSpawnInteractiveObjectEffect templateId="+templateId);
				effect.setTemplateId(templateId);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadSpawnInteractiveObjectEffect: SQLException:" + e);
			e.printStackTrace();
		}
		if(Log.loggingDebug)Log.debug("loadSpawnInteractiveObjectEffect END");

		return null;
	}

	/**
	 * Load in the specific data for a Change Class Effect.
	 * @param rs
	 * @return
	 */
	public AgisEffect loadChangeClassEffect(ResultSet rs) {
		if(Log.loggingDebug)Log.debug("loadChangeClassEffect");

		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("ChangeClassEffect")) {
				ChangeClassEffect effect = new ChangeClassEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				//effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				int classId = rs.getInt("intValue1");
				boolean resetAbility = rs.getBoolean("boolValue1");
				boolean resetSkillStatBonus = rs.getBoolean("boolValue2");
				if(Log.loggingDebug)Log.debug("loadChangeClassEffect classId="+classId+" resetAbility="+resetAbility+" resetSkillStatBonus="+resetSkillStatBonus);
				effect.setNewClass(classId);
				effect.setResetAbility(resetAbility);
				effect.setSkillStatBonus(resetSkillStatBonus);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadChangeClassEffect: SQLException:" + e);
			e.printStackTrace();
		}
		if(Log.loggingDebug)Log.debug("loadChangeClassEffect END");

		return null;
	}
	
	
	/**
	 * Called by each loadEffect function to read in and set the tags effect properties.
	 * @param effect
	 * @param rs
	 */
	protected void loadTagsForEffect(AgisEffect effect, ResultSet rs) {
		try {
			String tags = HelperFunctions.readEncodedString(rs.getBytes("group_tags"));
			if(tags.length()>0) {
				String[] tagsArray =tags.split(";"); 
				for (int i = 0; i < tagsArray.length; i++) {
					if(tagsArray[i].length() > 0) {
						int tag = Integer.parseInt(tagsArray[i]);
						if(tag > 0)
							effect.addTag(tag);
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadTagsForEffect: SQLException:"+e);
				e.printStackTrace();
		}
	}
	
	/**
	 * Called by each loadEffect function to read in and set the bonus effect properties.
	 * @param effect
	 * @param rs
	 */
	protected void loadBonusEffect(AgisEffect effect, ResultSet rs) {
		try {
			int bonusEffectReq = rs.getInt("bonusEffectReq");
			boolean bonusEffectReqConsumed = rs.getBoolean("bonusEffectReqConsumed");
			int bonusEffect = rs.getInt("bonusEffect");
			if (bonusEffect != -1) {
				effect.setBonusEffectReq(bonusEffectReq);
				effect.setBonusEffectReqConsumed(bonusEffectReqConsumed);
				effect.setBonusEffect(bonusEffect);
				effect.removeBonusWhenEffectRemoved(rs.getBoolean("removeBonusWhenEffectRemoved"));
			}
		} catch (SQLException e) {
			Log.dumpStack("loadBonusEffect: SQLException:"+e);
				e.printStackTrace();
		}
	}
	/**
	 * Load in the specific data for a Bonuses Effect.
	 * @param rs
	 * @return
	 */
	public AgisEffect loadBonusesEffect(ResultSet rs) {
		if(Log.loggingDebug)Log.debug("loadBonusesEffect");
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("BonusEffect")) {
				BonusEffect effect = new BonusEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				// effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				if (rs.getBoolean("passive")) {
					effect.isPassive(true);
					effect.isContinuous(true);
				} else if (effect.getDuration() > 0) {
					effect.isPersistent(true);
					effect.setNumPulses(1);
					if (rs.getBoolean("boolValue2")) {
						effect.isContinuous(true);
					}
				}
				effect.isBuff(rs.getBoolean("isBuff"));
				effect.setStackLimit(1);
				effect.setStackTime(rs.getBoolean("stackTime"));
				effect.setMultipleCopies(false);
				
//				effect.setForceInterruption(rs.getBoolean("interruption_all"));
//				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
//				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				

				ArrayList<BonusSettings> bonuses = new ArrayList<BonusSettings>();
				for (int i = 1; i < 6; i++) {
					String statName = HelperFunctions.readEncodedString(rs.getBytes("stringValue" + i));
					int value = rs.getInt("intValue" + i);
					float valuep = rs.getFloat("floatValue" + i);
					if (statName != null && statName.length()>2)
						bonuses.add(new BonusSettings("", statName, value, valuep, -1));
				}
				effect.setBonuses(bonuses);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadBonusesEffect: SQLException:" + e);
			e.printStackTrace();
		}
		Log.debug("loadBonusesEffect END");
		return null;
	}
	
	public ArrayList<TriggerAction> LoadTriggerActions(int id) {
		ArrayList<TriggerAction> actions = new ArrayList<TriggerAction>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM effects_triggers_actions where effects_triggers_id = "+id)) {
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					
    					int _t = rs.getInt("target");
    					TriggerAction.Target target = TriggerAction.Target.ALL;
    					if(_t==1)
    						target = TriggerAction.Target.CASTER;
    					else if(_t==2)
    						target = TriggerAction.Target.TARGET;
    					TriggerAction ta= new TriggerAction(target, rs.getInt("ability"),rs.getInt("effect"),rs.getInt("mod_v"),rs.getFloat("mod_p"),rs.getFloat("chance_min"),rs.getFloat("chance_max"));
    					
    					actions.add(ta);
						if(Log.loggingDebug)Log.debug("TriggerAction Load for trigger profile "+id+" "+ta);
    				}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadStatEffect: SQLException:" + e);
			e.printStackTrace();
		}
		return actions;
	}

	public TriggerProfile LoadTriggerProfile(int id) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM effects_triggers where isactive = 1 and id ="+id)) {
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
						if(Log.loggingDebug)Log.debug("TriggerProfile Load  "+id);
    					String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					TriggerProfile tp = new TriggerProfile(rs.getInt("id"), name);
    					int type = rs.getInt("event_type");
    					// TriggerProfile.Type etype;
    					switch (type) {
    					case 0:
    						tp.SetType(TriggerProfile.Type.DODGE);
    						break;
    					case 1:
    						tp.SetType(TriggerProfile.Type.MISS);
    						break;
    					case 2:
    						tp.SetType(TriggerProfile.Type.DAMAGE);
    						break;
    					case 3:
    						tp.SetType(TriggerProfile.Type.HEAL);
    						break;
    					case 4:
    						tp.SetType(TriggerProfile.Type.CRITICAL);
    						break;
    					case 5:
    						tp.SetType(TriggerProfile.Type.KILL);
    						break;
    					case 6:
    						tp.SetType(TriggerProfile.Type.PARRY);
    						break;
    					case 7:
    						tp.SetType(TriggerProfile.Type.SLEEP);
    						break;
    					case 8:
    						tp.SetType(TriggerProfile.Type.STUN);
    						break;
    					}
    					
    					int atype = rs.getInt("action_type");
    					switch (atype) {
    					case 0:
    						tp.SetActionType(TriggerProfile.ActionType.DEALT);
    						break;
    					case 1:
    						tp.SetActionType(TriggerProfile.ActionType.RECEIVED);
    						break;
    					}
    					tp.SetRace(rs.getInt("race"));
    					tp.SetClass(rs.getInt("class"));
    					tp.SetChanceMin(rs.getFloat("chance_min"));
    					tp.SetChanceMax(rs.getFloat("chance_max"));
    
    					String tags = HelperFunctions.readEncodedString(rs.getBytes("tags"));
    					if (tags.length() > 0) {
    						String[] tagsArray = tags.split(";");
    						for (int j = 0; j < tagsArray.length; j++) {
    							if (tagsArray[j].length() > 0) {
    								tp.addTag(Integer.parseInt(tagsArray[j]));
    							}
    						}
    					}
    					tp.SetActions(LoadTriggerActions(id));
    					return tp;
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadStatEffect: SQLException:" + e);
			e.printStackTrace();
		}
		return null;
	}
	
	
	/**
	 * Load the specific data for a Trigger Effect.
	 * @param rs
	 * @return
	 */
	public AgisEffect loadTriggerEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("TriggerEffect")) {
				String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
				if(Log.loggingDebug)Log.debug("TriggerEffect Load " + name);
				TriggerEffect effect = new TriggerEffect(rs.getInt("id"), name);

				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);

				String triggers = HelperFunctions.readEncodedString(rs.getBytes("stringValue1"));
				if (triggers.length() > 0) {
					String[] tagsArray = triggers.split(";");
					for (int j = 0; j < tagsArray.length; j++) {
						if (tagsArray[j].length() > 0) {
							effect.addProfile(LoadTriggerProfile(Integer.parseInt(tagsArray[j])));
						}
					}
				}

				if (effect.getDuration() > 0) {
					effect.isPersistent(true);

				}

				effect.isBuff(rs.getBoolean("isBuff"));

				loadTagsForEffect(effect, rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadStatEffect: SQLException:" + e);
			e.printStackTrace();
		}
		return null;
	}

	
	
	/**
	 * Load in the specific data for a Trap Effect.
	 * @param rs
	 * @return
	 */
	public AgisEffect loadTrapEffect(ResultSet rs) {
		Log.debug("loadTrapEffect");

		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("TrapEffect")) {
				TrapEffect effect = new TrapEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				int abilityId = rs.getInt("intValue1");
				int targetType = rs.getInt("intValue2");
				float size = rs.getFloat("floatValue1");
				float time = rs.getFloat("floatValue2");// s
				float atime = rs.getFloat("floatValue3");// s

				effect.setAbilityId(abilityId);
				effect.setSize(size);
				effect.setTime(time);
				effect.setTargetType(targetType);
				effect.setActivationTime(atime);
				effect.setModel(HelperFunctions.readEncodedString(rs.getBytes("stringValue1")));
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadTrapEffect: SQLException:" + e);
			e.printStackTrace();
		}
		Log.debug("loadTrapEffect END");

		return null;
	}
	
	/**
	 * Load in the specific data for a Vip Effect.
	 * @param rs
	 * @return
	 */
	public AgisEffect loadVipEffect(ResultSet rs) {
		Log.debug("loadVipEffect");
		
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("VipEffect")) {
				VipEffect effect = new VipEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				int time = rs.getInt("intValue1");
				int points = rs.getInt("intValue2");
				effect.setTime(time);
				effect.setPoints(points);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadVipEffect: SQLException:" + e);
			e.printStackTrace();
		}
		Log.debug("loadVipEffect END");
		
		return null;
	}
	
	
	/**
	 * Load in the specific data for a Damage Effect.
	 * @param rs
	 * @return
	 */
	public AgisEffect loadDamageEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("FlatDamageEffect")) {
				DamageEffect effect = new DamageEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setMinInstantDamage(rs.getInt("intValue1"));
				effect.setDamageProperty(HelperFunctions.readEncodedString(rs.getBytes("stringValue1")));
				String damageType = HelperFunctions.readEncodedString(rs.getBytes("stringValue2"));
				if (damageType != null)
					effect.setDamageType(damageType);
				//effect.setDamageMod(rs.getFloat("floatValue1"));
				//effect.setEffectSkillType(rs.getInt("skillType"));
				//effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				effect.setPulseCoordEffect(rs.getString("pulseCoordEffect"));
				effect.setNumPulses(1);
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				effect.setTransferModifier(rs.getFloat("floatValue2"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			} else if (effectType.equals("AttackEffect")) {
				AttackEffect effect = new AttackEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setMinInstantDamage(rs.getInt("intValue1"));
				effect.setDamageProperty(HelperFunctions.readEncodedString(rs.getBytes("stringValue1")));
				String damageType = HelperFunctions.readEncodedString(rs.getBytes("stringValue2"));
				if (damageType != null)
					effect.setDamageType(damageType);
				effect.setDamageMod(rs.getFloat("floatValue1"));
				effect.setEffectSkillType(rs.getInt("skillType"));
				effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				effect.addBonusDmgEffectVal(rs.getInt("intValue2"));
				effect.addBonusDmgVal(rs.getInt("intValue3"));
				effect.setPulseCoordEffect(rs.getString("pulseCoordEffect"));
				
				effect.setUseWeaponDamage(rs.getBoolean("boolValue1"));
				
				effect.setNumPulses(1);
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				effect.setTransferModifier(rs.getFloat("floatValue2"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			} else if (effectType.equals("AttackDotEffect")) {
				AttackDotEffect effect = new AttackDotEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setEffectVal(rs.getInt("id"));
				effect.setEffectName(HelperFunctions.readEncodedString(rs.getBytes("displayName")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setMinDamage(rs.getInt("intValue1"));
				effect.setDamageProperty(HelperFunctions.readEncodedString(rs.getBytes("stringValue1")));
				String damageType = HelperFunctions.readEncodedString(rs.getBytes("stringValue2"));
				if (damageType != null)
					effect.setDamageType(damageType);
				effect.setDamageMod(rs.getFloat("floatValue1"));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				effect.setNumPulses(rs.getInt("pulseCount"));
				effect.setDOT(true);
				effect.setPulseCoordEffect(rs.getString("pulseCoordEffect"));
		
				effect.isPersistent(true);
				effect.isPeriodic(true);
				effect.setStackLimit(rs.getInt("stackLimit"));
				effect.setStackTime(rs.getBoolean("stackTime"));
				
				effect.setUseWeaponDamage(rs.getBoolean("boolValue1"));
				
				effect.setMultipleCopies(rs.getBoolean("allowMultiple"));
				effect.setEffectSkillType(rs.getInt("skillType"));
				effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				effect.setTransferModifier(rs.getFloat("floatValue2"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			} else if (effectType.equals("HealthStealEffect")) {
				HealthStealEffect effect = new HealthStealEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setMinInstantDamage(rs.getInt("intValue1"));
				effect.setDamageProperty(HelperFunctions.readEncodedString(rs.getBytes("stringValue1")));
				String damageType = HelperFunctions.readEncodedString(rs.getBytes("stringValue2"));
				if (damageType != null)
					effect.setDamageType(damageType);
				effect.setDamageMod(rs.getFloat("floatValue1"));
				effect.setEffectSkillType(rs.getInt("skillType"));
				effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				effect.addBonusDmgEffectVal(rs.getInt("intValue2"));
				effect.addBonusDmgVal(rs.getInt("intValue3"));
				effect.setTransferModifier(rs.getFloat("floatValue2"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			} else if (effectType.equals("HealthStealDotEffect")) {
				HealthStealDotEffect effect = new HealthStealDotEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setEffectVal(rs.getInt("id"));
				effect.setEffectName(HelperFunctions.readEncodedString(rs.getBytes("displayName")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setMinDamage(rs.getInt("intValue1"));
				String damageType = HelperFunctions.readEncodedString(rs.getBytes("stringValue2"));
				if (damageType != null)
					effect.setDamageType(damageType);
				effect.setDamageMod(rs.getFloat("floatValue1"));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				effect.setDOT(true);
				effect.setNumPulses(rs.getInt("pulseCount"));
				effect.setPulseCoordEffect(rs.getString("pulseCoordEffect"));
				effect.isPersistent(true);
				effect.isPeriodic(true);
				effect.setStackLimit(rs.getInt("stackLimit"));
				effect.setStackTime(rs.getBoolean("stackTime"));
				effect.setEffectSkillType(rs.getInt("skillType"));
				effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				effect.setTransferModifier(rs.getFloat("floatValue2"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadDamageEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadRestorationEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("HealInstantEffect")) {
				Log.debug("Adding heal effect of type: HealInstantEffect");
				HealInstantEffect effect = new HealInstantEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setEffectVal(rs.getInt("id"));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setMinInstantHeal(rs.getInt("intValue1"));
				effect.setMaxInstantHeal(rs.getInt("intValue2"));
				effect.setMinPercentageHeal(rs.getFloat("floatValue4"));
				effect.setMaxPercentageHeal(rs.getFloat("floatValue5"));
				effect.setHealProperty(HelperFunctions.readEncodedString(rs.getBytes("stringValue1")));
				effect.setEffectSkillType(rs.getInt("skillType"));
				effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				effect.setPulseCoordEffect(rs.getString("pulseCoordEffect"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				if(Log.loggingDebug)Log.debug("Added heal effect: " + effect.effectVal);
				return effect;
			} else if (effectType.equals("HealOverTimeEffect")) {
				HealOverTimeEffect effect = new HealOverTimeEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setEffectVal(rs.getInt("id"));
				effect.setDisplayName(HelperFunctions.readEncodedString(rs.getBytes("displayName")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setMinHeal(rs.getInt("intValue1"));
				effect.setMaxHeal(rs.getInt("intValue2"));
				effect.setMinPercentageHeal(rs.getFloat("floatValue4"));
				effect.setMaxPercentageHeal(rs.getFloat("floatValue5"));
				effect.setHealProperty(HelperFunctions.readEncodedString(rs.getBytes("stringValue1")));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				effect.setNumPulses(rs.getInt("pulseCount"));
				effect.setDOT(true);
				effect.setPulseCoordEffect(rs.getString("pulseCoordEffect"));
			/*	if (rs.getBoolean("passive")) {
					effect.isPassive(true);
					effect.isContinuous(true);
				}*/
				effect.isPersistent(true);
				effect.isPeriodic(true);
				effect.setMultipleCopies(rs.getBoolean("allowMultiple"));
				effect.setStackLimit(rs.getInt("stackLimit"));
				effect.setStackTime(rs.getBoolean("stackTime"));
				effect.setEffectSkillType(rs.getInt("skillType"));
				effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			} else if (effectType.equals("HealthTransferEffect")) {
				HealthTransferEffect effect = new HealthTransferEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setMinInstantHeal(rs.getInt("intValue1"));
				effect.setMaxInstantHeal(rs.getInt("intValue2"));
				effect.setMinPercentageHeal(rs.getFloat("floatValue4"));
				effect.setMaxPercentageHeal(rs.getFloat("floatValue5"));
				effect.setHealProperty(HelperFunctions.readEncodedString(rs.getBytes("stringValue1")));
				effect.setEffectSkillType(rs.getInt("skillType"));
				effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				effect.setTransferModifier(rs.getFloat("floatValue1"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadRestorationEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadReviveEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("ReviveEffect")) {
				ReviveEffect effect = new ReviveEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setEffectVal(rs.getInt("id"));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				// effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				effect.setHealthPercent(rs.getInt("intValue1"));
				effect.setHealthStat(HelperFunctions.readEncodedString(rs.getBytes("stringValue1")));
				String vitalityStat = HelperFunctions.readEncodedString(rs.getBytes("stringValue2"));
				if (vitalityStat != null && vitalityStat != "" && !vitalityStat.contains("~ none ~")) {
					effect.setVitalityPercent(rs.getInt("intValue2"));
					effect.setVitalityStat(vitalityStat);
				}
				String vitalityStat2 = HelperFunctions.readEncodedString(rs.getBytes("stringValue3"));
				if (vitalityStat2 != null && vitalityStat2 != "" && !vitalityStat2.contains("~ none ~")) {
					effect.setVitalityPercent2(rs.getInt("intValue3"));
					effect.setVitalityStat2(vitalityStat2);
				}
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadReviveEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadDamageMitigationEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("DamageMitigationEffect")) {
				DamageMitigationEffect effect = new DamageMitigationEffect(
						rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setEffectVal(rs.getInt("id"));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				// effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				effect.setAmountMitigated(rs.getInt("intValue1"));
				effect.setAttacksMitigated(rs.getInt("intValue2"));
				effect.isBuff(true);
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadDamageMitigationEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadStatEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("StatEffect")) {
				StatEffect effect = new StatEffect(rs.getInt("id"),
						HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setDisplayName(HelperFunctions.readEncodedString(rs.getBytes("displayName")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setModifyPercentage(rs.getBoolean("boolValue1"));
				for (int i = 1; i < 6; i++) {
					String statName = HelperFunctions.readEncodedString(rs.getBytes("stringValue" + i));
					float statValue = rs.getFloat("floatValue" + i);
					if (statName != null && !statName.isEmpty() && statValue != 0)
						effect.setStat(statName, statValue);
				}
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				effect.setNumPulses(rs.getInt("pulseCount"));
				effect.setPulseCoordEffect(rs.getString("pulseCoordEffect"));
				if (rs.getBoolean("passive")) {
					effect.isPassive(true);
					effect.isContinuous(true);
				} else if (effect.getDuration() > 0) {
					effect.isPersistent(true);
					// effect.isPeriodic(true);
					if (rs.getBoolean("boolValue2")) {
						effect.isContinuous(true);
					}
					effect.setNumPulses(1);
				}
				effect.setEffectSkillType(rs.getInt("skillType"));
				effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				effect.isBuff(rs.getBoolean("isBuff"));
				effect.setStackLimit(rs.getInt("stackLimit"));
				effect.setStackTime(rs.getBoolean("stackTime"));
				effect.setMultipleCopies(rs.getBoolean("allowMultiple"));
				effect.setChance(rs.getFloat("chance"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadStatEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadShieldEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("ShieldEffect")) {
				ShieldEffect effect = new ShieldEffect(rs.getInt("id"),
						HelperFunctions.readEncodedString(rs.getBytes("name")));
				//effect.setDisplayName(HelperFunctions.readEncodedString(rs.getBytes("displayName")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
			//	effect.setNumPulses(rs.getInt("pulseCount"));
				
				for (int i = 1; i <= 4; i++) {
					ArrayList<Integer> tagList = new ArrayList<Integer>();
					String tags = HelperFunctions.readEncodedString(rs.getBytes("stringValue" + i));
					if (tags.length() > 0) {
						String[] tagsArray = tags.split(";");
						//Log.debug("ShieldEffect "+tags+" "+tagsArray+" "+Arrays.toString(tagsArray));
						for (int j = 0; j < tagsArray.length; j++) {
							//Log.debug("ShieldEffect length="+tagsArray[j].length());
							if (tagsArray[j].length() > 0) {
								tagList.add(Integer.parseInt(tagsArray[j]));
							}
						}
					}
					int value = rs.getInt("intValue"+i);
					float valuep = rs.getFloat("floatValue" + i);
					boolean reflect = rs.getBoolean("boolvalue" + i);
				//	Log.debug("ShieldEffect "+tags+" "+tagList+" v="+value+" v%="+valuep+" ref="+reflect);
					effect.addSetting(tagList, value, valuep, reflect);
				}
				
				effect.setShieldAmount(rs.getInt("intValue5"));
				effect.setHitCount((int)rs.getFloat("floatValue5"));
				
				effect.setPulseCoordEffect(rs.getString("pulseCoordEffect"));
				if (rs.getBoolean("passive")) {
					effect.isPassive(true);
					effect.isContinuous(true);
				} else if (effect.getDuration() > 0) {
					effect.isPersistent(true);
					// effect.isPeriodic(true);
					if (rs.getBoolean("boolValue5")) {
						effect.isContinuous(true);
					}
				//	effect.setNumPulses(1);
				}
				
				effect.setEffectSkillType(rs.getInt("skillType"));
				effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				effect.isBuff(rs.getBoolean("isBuff"));
				effect.setStackLimit(rs.getInt("stackLimit"));
				effect.setMultipleCopies(rs.getBoolean("allowMultiple"));
				effect.setChance(rs.getFloat("chance"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadStatEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}


	public AgisEffect loadStealthEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("StealthEffect")) {
				StealthEffect effect = new StealthEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				//effect.setDisplayName(HelperFunctions.readEncodedString(rs.getBytes("displayName")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
			//	effect.setModifyPercentage(rs.getBoolean("boolValue1"));
				
				int statValue = rs.getInt("intValue1");
				effect.setStealthValue(statValue);
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				//effect.setNumPulses(rs.getInt("pulseCount"));
				if (rs.getBoolean("passive")) {
					effect.isPassive(true);
					effect.isContinuous(true);
				} else if (effect.getDuration() > 0) {
					effect.isPersistent(true);
					// effect.isPeriodic(true);
					if (rs.getBoolean("boolValue2")) {
						effect.isContinuous(true);
					}
					//effect.setNumPulses(1);
				}
				effect.setEffectSkillType(rs.getInt("skillType"));
				effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				effect.isBuff(rs.getBoolean("isBuff"));
				effect.setStackLimit(rs.getInt("stackLimit"));
				effect.setStackTime(rs.getBoolean("stackTime"));
				effect.setMultipleCopies(rs.getBoolean("allowMultiple"));
				effect.setChance(rs.getFloat("chance"));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadStatEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}
	public AgisEffect loadPropertyEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("PropertyEffect")) {
				PropertyEffect effect = new PropertyEffect(rs.getInt("id"),
						HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				String property = HelperFunctions.readEncodedString(rs.getBytes("stringValue1"));
				effect.setPropertyName(property);
				String propertyType = HelperFunctions.readEncodedString(rs.getBytes("stringValue2"));
				String propertyValue = HelperFunctions.readEncodedString(rs.getBytes("stringValue3"));
				String propertyDefault = HelperFunctions.readEncodedString(rs.getBytes("stringValue4"));
				if (propertyType.equals("Boolean") && propertyValue.equals("True")) {
					effect.setPropertyValue(true);
					if (propertyDefault.equals("True"))
						effect.setPropertyDefault(true);
					else
						effect.setPropertyDefault(false);
				} else if (propertyType.equals("Boolean") && propertyValue.equals("False")) {
					effect.setPropertyValue(false);
					if (propertyDefault.equals("True"))
						effect.setPropertyDefault(true);
					else
						effect.setPropertyDefault(false);
				} else if (propertyType.equals("Integer")) {
					int propVal = Integer.parseInt(propertyValue);
					effect.setPropertyValue(propVal);
					int propDef = Integer.parseInt(propertyDefault);
					effect.setPropertyDefault(propDef);
				} else if (propertyType.equals("Long")) {
					Long propVal = Long.parseLong(propertyValue);
					effect.setPropertyValue(propVal);
					Long propDef = Long.parseLong(propertyDefault);
					effect.setPropertyDefault(propDef);
				} else if (propertyType.equals("Double")) {
					double propVal = Double.parseDouble(propertyValue);
					effect.setPropertyValue(propVal);
					double propDef = Double.parseDouble(propertyDefault);
					effect.setPropertyDefault(propDef);
				} else {
					effect.setPropertyValue(propertyValue);
					effect.setPropertyDefault(propertyDefault);
				}
				effect.setPropertyType(propertyType);
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				if (rs.getBoolean("passive")) {
					if(Log.loggingDebug)Log.debug("CDB: setting effect " + effect.getName()
							+ " to passive");
					effect.isPassive(true);
					effect.isContinuous(true);
				} else {
					effect.setNumPulses(1);
				}
				effect.isBuff(rs.getBoolean("isBuff"));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadPropertyEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}
	
	public AgisEffect loadStateEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("StateEffect")) {
				StateEffect effect = new StateEffect(rs.getInt("id"),
						HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				String propName = HelperFunctions.readEncodedString(rs.getBytes("stringValue1"));
				effect.setPropertyName(propName);
				
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				// Set it to continuous so it isn't removed on logout/in
				effect.isContinuous(true);
				if (duration < 1) {
					// Set persistent = false when we have no duration so this lasts until cancelled
					effect.isPersistent(false);
					effect.isPassive(true);
				}
				effect.isBuff(rs.getBoolean("isBuff"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadStateEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}
	
	public AgisEffect loadMorphEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("MorphEffect")) {
				MorphEffect effect = new MorphEffect(rs.getInt("id"),
						HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				String model = HelperFunctions.readEncodedString(rs.getBytes("stringValue1"));
				effect.setModel(model);
				effect.setSwitchToActionBar(rs.getInt("intValue1"));
				effect.removeOtherMorphs(rs.getBoolean("boolValue1"));
				effect.setMorphType(rs.getInt("intValue2"));
				
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				// Set it to continuous so it isn't removed on logout/in
				effect.isContinuous(true);
				if (duration < 1) {
					// Set persistent = false when we have no duration so this lasts until cancelled
					effect.isPersistent(false);
				} else {
					effect.setNumPulses(1);
				}
				effect.isBuff(rs.getBoolean("isBuff"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadMorphEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadCooldownEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("CooldownEffect")) {
				CooldownEffect effect = new CooldownEffect(rs.getInt("id"),
						HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				String cooldown = HelperFunctions.readEncodedString(rs.getBytes("stringValue1"));
				effect.addCooldownToAlter(cooldown);
				boolean resetCooldown = rs.getBoolean("boolValue1");
				if (resetCooldown) {
					effect.setCooldownOffset(-1l);
				} else {
					int cooldownOffset = rs.getInt("intValue1");
					effect.setCooldownOffset((long) cooldownOffset);
				}
				effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				effect.isBuff(rs.getBoolean("isBuff"));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadCooldownEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadStunEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("StunEffect")) {
				StunEffect effect = new StunEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setEffectVal(rs.getInt("id"));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				//effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				effect.setNumPulses(1);
				effect.setPulseCoordEffect(rs.getString("pulseCoordEffect"));
				effect.setChance(rs.getFloat("chance"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadStunEffect: SQLException:" + e);
			e.printStackTrace();
		}
		return null;
	}
	
	public AgisEffect loadSleepEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("SleepEffect")) {
				SleepEffect effect = new SleepEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setEffectVal(rs.getInt("id"));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				//effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				effect.setNumPulses(1);
				effect.setPulseCoordEffect(rs.getString("pulseCoordEffect"));
				effect.setChance(rs.getFloat("chance"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadSleepEffect: SQLException:" + e);
			e.printStackTrace();
		}
		return null;
	}
	
	public AgisEffect loadImmuneEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("ImmuneEffect")) {
				ImmuneEffect effect = new ImmuneEffect(rs.getInt("id"),
						HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setEffectVal(rs.getInt("id"));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
			//	effect.setSkillEffectMod(rs.getFloat("skillLevelMod"));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				if (rs.getBoolean("passive")) {
					effect.isPassive(true);
				} else {
					effect.setNumPulses(1);
				}
				effect.isBuff(rs.getBoolean("isBuff"));
				effect.setChance(rs.getFloat("chance"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadImmuneEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadTeleportEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("TeleportEffect")) {
				TeleportEffect effect = new TeleportEffect(rs.getInt("id"),
						HelperFunctions.readEncodedString(rs.getBytes("name")));
				// effect.setEffectName(rs.getString("displayName"));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				//String marker = rs.getString("marker");

				//if (marker == null) {
					effect.setTeleportType(HelperFunctions.readEncodedString(rs.getBytes("stringValue1")));
					float locX = rs.getFloat("floatValue1");
					float locY = rs.getFloat("floatValue2");
					float locZ = rs.getFloat("floatValue3");
					
					Point p = new Point(locX, locY, locZ);
					effect.setTeleportLocation(p);
				/*} else {
					effect.setMarkerName(marker);
				}*/
					
				effect.setInstance(rs.getInt("intValue1"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadTeleportEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadCreateItemEffect(ResultSet rs) {
		 	try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("CreateItemEffect")) {
				CreateItemEffect effect = new CreateItemEffect(rs.getInt("id"),
						HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setItem(rs.getInt("intValue1"));
				effect.setNumberToCreate(rs.getInt("intValue2"));
				effect.setChance(rs.getFloat("chance"));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadCreateItemEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadCreateItemFromLootEffect(ResultSet rs) {
		
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("CreateItemFromLootEffect")) {
				CreateItemFromLootEffect effect = new CreateItemFromLootEffect(rs.getInt("id"),
						HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				//effect.setItem(rs.getInt("intValue1"));
				//effect.setNumberToCreate(rs.getInt("intValue2"));
				//effect.setChance(rs.getFloat("chance"));
				HashMap<Integer, Float> lootsChance = new HashMap<Integer, Float>();
				HashMap<Integer, Integer> lootsCount = new HashMap<Integer, Integer>();
				if (rs.getInt("intValue1") > 0) {
					lootsChance.put(rs.getInt("intValue1"), rs.getFloat("floatValue1"));
					String scount = HelperFunctions.readEncodedString(rs.getBytes("stringValue1"));
					if (scount != null && scount.length() > 0) {
						lootsCount.put(rs.getInt("intValue1"), Integer.parseInt(scount));
					} else {
						lootsCount.put(rs.getInt("intValue1"), 1);
					}
				}
				if (rs.getInt("intValue2") > 0) {
					lootsChance.put(rs.getInt("intValue2"), rs.getFloat("floatValue2"));
					String scount = HelperFunctions.readEncodedString(rs.getBytes("stringValue2"));
					if (scount != null && scount.length() > 0) {
						lootsCount.put(rs.getInt("intValue2"), Integer.parseInt(scount));
					} else {
						lootsCount.put(rs.getInt("intValue2"), 1);
					}
				}
				if (rs.getInt("intValue3") > 0) {
					lootsChance.put(rs.getInt("intValue3"), rs.getFloat("floatValue3"));
					String scount = HelperFunctions.readEncodedString(rs.getBytes("stringValue3"));
					if (scount != null && scount.length() > 0) {
						lootsCount.put(rs.getInt("intValue3"), Integer.parseInt(scount));
					} else {
						lootsCount.put(rs.getInt("intValue3"), 1);
					}
				}
				if (rs.getInt("intValue4") > 0) {
					lootsChance.put(rs.getInt("intValue4"), rs.getFloat("floatValue4"));
					String scount = HelperFunctions.readEncodedString(rs.getBytes("stringValue4"));
					if (scount != null && scount.length() > 0) {
						lootsCount.put(rs.getInt("intValue4"), Integer.parseInt(scount));
					} else {
						lootsCount.put(rs.getInt("intValue4"), 1);
					}
				}
				if (rs.getInt("intValue5") > 0) {
					lootsChance.put(rs.getInt("intValue5"), rs.getFloat("floatValue5"));
					String scount = HelperFunctions.readEncodedString(rs.getBytes("stringValue5"));
					if (scount != null && scount.length() > 0) {
						lootsCount.put(rs.getInt("intValue5"), Integer.parseInt(scount));
					} else {
						lootsCount.put(rs.getInt("intValue5"), 1);
					}
				}
				effect.setLootsChance(lootsChance);
				effect.setLootsCount(lootsCount);
					
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
					return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadCreateItemFromLootEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	
	
	
	public AgisEffect loadTaskEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("TaskCompleteEffect")) {
				TaskCompleteEffect effect = new TaskCompleteEffect(
						rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setTaskID(rs.getInt("intValue1"));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadTaskEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadExtensionMessageEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("MessageEffect")) {
				SendExtensionMessageEffect effect = new SendExtensionMessageEffect(
						rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setMessageType(HelperFunctions.readEncodedString(rs.getBytes("stringValue1")));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadExtensionMessageEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadSpawnEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("SpawnEffect")) {
				SpawnEffect effect = new SpawnEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setMobID(rs.getInt("intValue1"));
				effect.setSpawnType(rs.getInt("intValue2"));
				effect.setPassiveEffect(rs.getInt("intValue3"));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadSpawnEffect: SQLException:" + e);
			e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadDespawnEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("DespawnEffect")) {
				DespawnEffect effect = new DespawnEffect(rs.getInt("id"),
						HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setMobID(rs.getInt("intValue1"));
				effect.setDespawnType(rs.getInt("intValue2"));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadDespawnEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadAlterSkillCurrentEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("AlterSkillCurrentEffect")) {
				AlterSkillCurrentEffect effect = new AlterSkillCurrentEffect(
						rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setSkillType(rs.getInt("intValue1"));
				effect.setAlterValue(rs.getInt("intValue2"));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadAlterSkillCurrentEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}
	
	public AgisEffect loadUnlearnAbilityEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("UnlearnAbilityEffect")) {
				UnlearnAbilityEffect effect = new UnlearnAbilityEffect(
						rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setAbilityID(rs.getInt("intValue1"));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);				
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadUnlearnAbilityEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}
	
	public AgisEffect loadTeachAbilityEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("TeachAbilityEffect")) {
				TeachAbilityEffect effect = new TeachAbilityEffect(
						rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setAbilityID(rs.getInt("intValue1"));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadTeachAbilityEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}
	
	public AgisEffect loadTeachSkillEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("TeachSkillEffect")) {
				TeachSkillEffect effect = new TeachSkillEffect(
						rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setSkillType(rs.getInt("intValue1"));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadTeachSkillEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}
	
	public AgisEffect loadMountEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("MountEffect")) {
				MountEffect effect = new MountEffect(
						rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setMountType(rs.getInt("intValue1"));
				effect.setMountSpeedIncrease(rs.getInt("intValue2"));
				effect.setModel(HelperFunctions.readEncodedString(rs.getBytes("stringValue1")));
				String statName = HelperFunctions.readEncodedString(rs.getBytes("stringValue2"));
				if (statName != null && !statName.isEmpty()) {
					effect.setStatName(statName);
					effect.setStatChange(rs.getFloat("floatValue1"));
				}
				effect.isContinuous(true);
				effect.isPersistent(false);
				effect.isPassive(true);
				effect.isBuff(rs.getBoolean("isBuff"));
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadMountEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}
	
	public AgisEffect loadThreatEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("ThreatEffect")) {
				ThreatEffect effect = new ThreatEffect(
						rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				effect.setAlterValue(rs.getInt("intValue1"));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadThreatEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}
	
	public AgisEffect loadDispelEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("DispelEffect")) {
				DispelEffect effect = new DispelEffect(rs.getInt("id"),
						HelperFunctions.readEncodedString(rs.getBytes("name")));
				// effect.setEffectName(rs.getString("displayName"));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
					
				effect.setEffectsToRemove(rs.getInt("intValue1"));
				effect.setDispelType(HelperFunctions.readEncodedString(rs.getBytes("stringValue1")));
				String tags = HelperFunctions.readEncodedString(rs.getBytes("stringValue2"));
				if(tags.length()>0) {
					String[] tagsArray =tags.split(";"); 
					for (int i = 0; i < tagsArray.length; i++) {
						if(tagsArray[i].length() > 0) {
							effect.addDispelTag(Integer.parseInt(tagsArray[i]));
						}
					}
				}
				
				effect.setForceInterruption(rs.getBoolean("interruption_all"));
				effect.setInterruptionChance(rs.getFloat("interruption_chance"));
				effect.setInterruptionChanceMax(rs.getFloat("interruption_chance_max"));
				
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadDispelEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}
	
	public AgisEffect loadSetRespawnLocationEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("SetRespawnLocationEffect")) {
				SetRespawnLocationEffect effect = new SetRespawnLocationEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				// effect.setEffectName(rs.getString("displayName"));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				float locX = rs.getInt("floatValue1");
				float locY = rs.getInt("floatValue2");
				float locZ = rs.getInt("floatValue3");
				Point p = new Point(locX, locY, locZ);
				effect.setRespawnLocation(p);
				effect.setInstance(rs.getInt("intValue1"));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadSetRespawnLocationEffect: SQLException:" + e);
			e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadLockpickEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("LockpickEffect")) {
				LockpickEffect effect = new LockpickEffect(rs.getInt("id"),
						HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadLockpickEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}

	public AgisEffect loadOtherEffect(ResultSet rs) {
		try {
			String effectType = HelperFunctions.readEncodedString(rs.getBytes("effectType"));
			if (effectType.equals("TameEffect")) {
				TameEffect effect = new TameEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			} else if (effectType.equals("ResultEffect")) {
				ResultEffect effect = new ResultEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			} else if (effectType.equals("CreateClaimEffect")) {
				CreateClaimEffect effect = new CreateClaimEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				loadBonusEffect(effect, rs);
				loadTagsForEffect(effect,rs);
				return effect;
			} else if (effectType.equals("BuildObjectEffect")) {
				BuildObjectEffect effect = new BuildObjectEffect(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")));
				effect.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
				float duration = 1000f * rs.getFloat("duration");
				effect.setDuration((int) duration);
				effect.setBuildObjectTemplateID(rs.getInt("intValue1"));
				loadTagsForEffect(effect,rs);
				loadBonusEffect(effect, rs);
				return effect;
			}
		} catch (SQLException e) {
			Log.dumpStack("loadOtherEffect: SQLException:"+e);
				e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Loading prefab definition of the abilities
	 * @return
	 */
	public HashMap<String, Serializable> LoadAbilityPrefabData() {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int c = 0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM abilities where isactive = 1")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
    					long date = 0L;
    					if (taxPaidUntil != null) {
    						date = taxPaidUntil.getTime();
    					}

						if(Log.loggingDebug)Log.debug("LoadAbilityPrefabData: id=" + id + " new");
    					props.put("i" + c + "id", id);
    					props.put("i" + c + "name", HelperFunctions.readEncodedString(rs.getBytes("name")));
    					String tooltip = HelperFunctions.readEncodedString(rs.getBytes("tooltip"));
    					if (tooltip == null)
    						tooltip = "";
    					props.put("i" + c + "tooltip", tooltip);
    					props.put("i" + c + "cost", rs.getInt("activationCost"));
    					props.put("i" + c + "costP", (int) (rs.getFloat("activationCostPercentage") * 1000f));
    					props.put("i" + c + "costProp", HelperFunctions.readEncodedString(rs.getBytes("activationCostType")));
    					props.put("i" + c + "pcost", rs.getInt("pulseCost"));
    					props.put("i" + c + "pcostP", (int) (rs.getFloat("pulseCostPercentage") * 1000f));
    					props.put("i" + c + "pcostProp", HelperFunctions.readEncodedString(rs.getBytes("pulseCostType")));
    					props.put("i" + c + "globancd", rs.getBoolean("globalCooldown"));
    					props.put("i" + c + "weaponcd", rs.getBoolean("weaponCooldown"));
    					String cool = HelperFunctions.readEncodedString(rs.getBytes("cooldown1Type"));
    					if (cool == null)
    						cool = "";
    					props.put("i" + c + "cooldT", cool);
    					int cooldL = (int) (rs.getFloat("cooldown1Duration") * 1000f);
    					props.put("i" + c + "cooldL", cooldL);
    					String weap = HelperFunctions.readEncodedString(rs.getBytes("weaponRequired"));
    					if (weap == null)
    						weap = "";
    					String[] sw  = weap.split(";");
    					StringJoiner sj = new StringJoiner(" or ");
    					for (String s : sw) {
    						if (s.length() > 0) {
    							String type = RequirementChecker.getNameEditorOptionChoice("Weapon Type", Integer.parseInt(s));
    							if (type != "")
    									sj.add(type);
    						}
    					}
    					props.put("i" + c + "reqWeap", sj.toString());
    					props.put("i" + c + "reqReag", rs.getInt("reagentRequired"));
    					props.put("i" + c + "dist", rs.getInt("maxRange"));
    					props.put("i" + c + "maxRange", rs.getInt("maxRange"));
    					props.put("i" + c + "minRange", rs.getInt("minRange"));
    					props.put("i" + c + "aoeRadius", rs.getInt("aoeRadius"));
    					props.put("i" + c + "castInRun", rs.getBoolean("castingInRun"));
						props.put("i" + c + "targetType", HelperFunctions.readEncodedString(rs.getBytes("targetType")));
						props.put("i" + c + "targetSubType", HelperFunctions.readEncodedString(rs.getBytes("targetSubType")));
    					String aoet = HelperFunctions.readEncodedString(rs.getBytes("aoeType"));
    					if (aoet == null)
    						aoet = "";
    					props.put("i" + c + "aoeType", aoet);
    					String aoe = HelperFunctions.readEncodedString(rs.getBytes("aoePrefab"));
    					if (aoe == null)
    						aoe = "";
    					props.put("i" + c + "aoePrefab", aoe);
    					props.put("i" + c + "castTime", (int) (rs.getFloat("activationLength") * 1000f));
    					props.put("i" + c + "passive", rs.getBoolean("passive"));
    					props.put("i" + c + "toggle", rs.getBoolean("toggle"));
    					props.put("i" + c + "date", date);
						props.put("i" + c + "speed",(int)(rs.getFloat("speed") * 1000F));
    					String icon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
    					String icon2 = HelperFunctions.readEncodedString(rs.getBytes("icon2"));
    					props.put("i" + c + "icon2", icon2);
    					props.put("i" + c + "icon", icon);
    					String effects=""; 
//						for (int i = 1; i <= 6; i++) {
//							Integer activationEffect = rs.getInt("activationEffect" + i);
//							String target = rs.getString("activationTarget" + i);
//							if(activationEffect>0) {
//								effects+=target+":"+activationEffect+"|";							}
//						}
    					if(effects.length()>0) {
    						effects = effects.substring(0, effects.length()-1);
    					}
						props.put("i" + c + "ef", effects);

//						String tags = HelperFunctions.readEncodedString(rs.getBytes("tags"));
//						props.put("i" + c + "tags", tags);
//						tags = HelperFunctions.readEncodedString(rs.getBytes("tags_on_caster"));
//						props.put("i" + c + "tags", tags);
//						tags = HelperFunctions.readEncodedString(rs.getBytes("tags_on_target"));
//						props.put("i" + c + "tags", tags);
//						tags = HelperFunctions.readEncodedString(rs.getBytes("tags_not_on_caster"));
//						props.put("i" + c + "tags", tags);
//						tags = HelperFunctions.readEncodedString(rs.getBytes("tags_not_on_target"));
//						props.put("i" + c + "tags", tags);
//						props.put("i" + c + "sra",rs.getInt("stealth_reduction_amount"));
//						props.put("i" + c + "arp",rs.getFloat("stealth_reduction_percentage"));
//						props.put("i" + c + "srt",rs.getLong("stealth_reduction_timeout"));
//
//						props.put("i" + c + "sup",rs.getInt("skill_up_chance"));
//						props.put("i" + c + "mc",rs.getFloat("miss_chance"));
						props.put("i" + c + "ic",rs.getBoolean("is_child"));
						props.putAll(LoadAbilitiesPowerUpPrefabData(c,id));
    					c++;
    				}
    			}
			}
			props.put("num", c);
		} catch (SQLException e) {
			Log.dumpStack("LoadAbilityPrefabData: SQLException:" + e);
			e.printStackTrace();
		}
		return props;
	}
	
	/**
	 * Get count of the ability definitions
	 * @return
	 */
	public int getCountAbilities() {
		int count = 0;
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM abilities where isactive=1")) {
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
	 * Loading definition of the abilities
	 */
	public ArrayList<AgisAbility> loadAbilities() {
		ArrayList<AgisAbility> list = new ArrayList<AgisAbility>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM abilities where isactive = 1")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Log.debug("ABILITY: Reading in another ability.");
    					String abilityType = HelperFunctions.readEncodedString(rs.getBytes("abilityType"));
						if(Log.loggingDebug)Log.debug("ABILITY: spot abilityType>"+abilityType+"<");
    				if (abilityType.equals("AttackAbility")) {
    						Log.debug("ABILITY: spot 1");
    						AttackAbility ability = new AttackAbility(
    								HelperFunctions.readEncodedString(rs.getBytes("name")));
    						setAbilityData(rs, ability);
    						list.add(ability);
						if(Log.loggingDebug)Log.debug("ABILITY: added " + ability.getName()
    								+ " to the template list.");
    					} else if (abilityType.equals("FriendlyEffectAbility")) {
    						Log.debug("ABILITY: spot 1");
    						FriendlyEffectAbility ability = new FriendlyEffectAbility(
    								HelperFunctions.readEncodedString(rs.getBytes("name")));
						if(Log.loggingDebug)Log.debug("ABILITY: spot 2 "+ability);
    							setAbilityData(rs, ability);
						if(Log.loggingDebug)Log.debug("ABILITY: spot 3 "+ability);
    						list.add(ability);
						if(Log.loggingDebug)Log.debug("ABILITY: added " + ability.getName()
    								+ " to the template list.");
    					} else if (abilityType.equals("EffectAbility")) {
						if(Log.loggingDebug)Log.debug("ABILITY: spot 1");
    						EffectAbility ability = new EffectAbility(
    								HelperFunctions.readEncodedString(rs.getBytes("name")));
    						setAbilityData(rs, ability);
    						ability.setChance(rs.getFloat("chance"));
    						
    					
						list.add(ability);
						if(Log.loggingDebug)Log.debug("ABILITY: added " + ability.getName()
    								+ " to the template list.");
    					}
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadAbilities: SQLException:"+e);
			e.printStackTrace();
		}
		return list;
	}

	public void setAbilityData(ResultSet rs, AgisAbility ability) {
		try {
			if(Log.loggingDebug)Log.debug("Getting ability Data: " + rs.getInt("id"));
			ability.setID(rs.getInt("id"));
			ability.setDamageType(HelperFunctions.readEncodedString(rs.getBytes("damageType")));
			ability.setSkillType(rs.getInt("skill"));
			ability.setSkillExp(rs.getInt("exp"));

			if (rs.getBoolean("passive")) {
				ability.setAbilityType(2);
				//	Log.error("setAbilityData ability.setAbilityType(2); "+ability.getName());
			} else {
				//	Log.error("setAbilityData ability.setAbilityType(1); "+ability.getName());
				ability.setAbilityType(1);
			}

			ability.setInterruptible(rs.getBoolean("interruptible"));
			ability.setInterruptChance(rs.getFloat("interruption_chance"));

			ability.isToggle(rs.getBoolean("toggle"));

			if (ability.isToggle()) {
				ability.setTagToDisable(rs.getInt("tag_disable"));
				ability.setTagToDisableCount(rs.getInt("tag_count"));


			}

			String tags = HelperFunctions.readEncodedString(rs.getBytes("tags"));
			if (tags.length() > 0) {
				String[] tagsArray = tags.split(";");
				for (int i = 0; i < tagsArray.length; i++) {
					if (tagsArray[i].length() > 0) {
						int tag = Integer.parseInt(tagsArray[i]);
						if (tag > 0)
							ability.addTag(tag);
					}
				}
			}
			try {
				tags = HelperFunctions.readEncodedString(rs.getBytes("tags_on_caster"));
				if (tags.length() > 0) {
					String[] tagsArray = tags.split(";");
					for (int i = 0; i < tagsArray.length; i++) {
						if (tagsArray[i].length() > 0) {
							int tag = Integer.parseInt(tagsArray[i]);
							if (tag > 0)
								ability.getEffectTagsOnCaster().add(tag);
						}
					}
				}
				tags = HelperFunctions.readEncodedString(rs.getBytes("tags_on_target"));
				if (tags.length() > 0) {
					String[] tagsArray = tags.split(";");
					for (int i = 0; i < tagsArray.length; i++) {
						if (tagsArray[i].length() > 0) {
							int tag = Integer.parseInt(tagsArray[i]);
							if (tag > 0)
								ability.getEffectTagsOnTarget().add(tag);
						}
					}
				}

				tags = HelperFunctions.readEncodedString(rs.getBytes("tags_not_on_caster"));
				if (tags.length() > 0) {
					String[] tagsArray = tags.split(";");
					for (int i = 0; i < tagsArray.length; i++) {
						if (tagsArray[i].length() > 0) {
							int tag = Integer.parseInt(tagsArray[i]);
							if (tag > 0)
								ability.getEffectTagsNotOnCaster().add(tag);
						}
					}
				}

				tags = HelperFunctions.readEncodedString(rs.getBytes("tags_not_on_target"));
				if (tags.length() > 0) {
					String[] tagsArray = tags.split(";");
					for (int i = 0; i < tagsArray.length; i++) {
						if (tagsArray[i].length() > 0) {
							int tag = Integer.parseInt(tagsArray[i]);
							if (tag > 0)
								ability.getEffectTagsNotOnTarget().add(tag);
						}
					}
				}
			} catch (SQLException e) {
				Log.exception("Load abilities setAbilityData", e);
			}

			ability.setStealthReductionAmount(rs.getInt("stealth_reduction_amount"));
			ability.setStealthReductionPercentage(rs.getFloat("stealth_reduction_percentage"));
			ability.setStealthReductionTimeout(rs.getLong("stealth_reduction_timeout"));

			ability.setSkillUpChance(rs.getInt("skill_up_chance"));
			ability.setMissChance(rs.getFloat("miss_chance"));
			ability.setLineOfSight(rs.getBoolean("line_of_sight"));

			ability.setAttackBuilding(rs.getBoolean("attack_building"));
			ability.setActivationCost(rs.getInt("activationCost"));
			ability.setActivationCostPercentage(rs.getFloat("activationCostPercentage"));

			ability.setCostProperty(HelperFunctions.readEncodedString(rs.getBytes("activationCostType")));
			float attack_time = rs.getFloat("attack_time") * 1000f;
			ability.setAttackTime((int) attack_time);
			float activationTime = rs.getFloat("activationLength") * 1000f;
			ability.setActivationTime((int) activationTime);
			float activationDelay = rs.getFloat("activationDelay") * 1000f;
			ability.setActivationDelay((int) activationDelay);
			ability.setChannelled(rs.getBoolean("channelling"));
			ability.setChannelledInRun(rs.getBoolean("channelling_in_run"));

			//ability.setChannelCost(rs.getInt("channelling_cost"));
			ability.setSkipChecks(rs.getBoolean("skipChecks"));
			ability.setStealthReduce(rs.getBoolean("stealth_reduce"));
			ability.setPredictionMode(rs.getInt("prediction"));
			ability.setAoeTargetsCount(rs.getInt("aoe_target_count"));
			ability.setAoeTargetsCountType(rs.getInt("aoe_target_count_type"));

			//ability.setUseEnterCombatState(rs.getBoolean("use_enter_combat_state"));

			ability.setChannelPulses(rs.getInt("channelling_pulse_num"));
			float channellingTime = rs.getFloat("channelling_pulse_time") * 1000f;

			ability.setChannelPulseTime((int) channellingTime);


			ability.setPulseCost(rs.getInt("pulseCost"));
			ability.setPulseCostPercentage(rs.getFloat("pulseCostPercentage"));

			ability.setPulseCostProperty(HelperFunctions.readEncodedString(rs.getBytes("pulseCostType")));

			int pulseCasterEffectRequired = rs.getInt("pulseCasterEffectRequired");
			if (pulseCasterEffectRequired > 0) {
				ability.addPulseAttackerEffectReq(pulseCasterEffectRequired);
				if (rs.getBoolean("pulseCasterEffectConsumed"))
					ability.addPulseAttackerEffectConsumption(pulseCasterEffectRequired);
			}
			int pulseTargetEffectRequired = rs.getInt("pulseTargetEffectRequired");
			if (pulseTargetEffectRequired > 0) {
				ability.addPulseTargetEffectReq(pulseTargetEffectRequired);
				if (rs.getBoolean("pulseTargetEffectConsumed"))
					ability.addPulseTargetEffectConsumption(pulseTargetEffectRequired);
			}


			int pulseRreagentRequired = rs.getInt("pulseReagentRequired");
			if (pulseRreagentRequired > 0) {
				Integer reagentCount = rs.getInt("pulseReagentCount");
				if (reagentCount == null) {
					reagentCount = 1;
				}
				ability.addPulseReagent(pulseRreagentRequired, reagentCount);
				if (rs.getBoolean("pulseReagentConsumed"))
					ability.addPulseConsumeReagent(pulseRreagentRequired, reagentCount);
			}
			int pulseRreagent2Required = rs.getInt("pulseReagent2Required");
			if (pulseRreagent2Required > 0) {
				Integer reagent2Count = rs.getInt("pulseReagent2Count");
				if (reagent2Count == null) {
					reagent2Count = 1;
				}
				ability.addPulseReagent(pulseRreagent2Required, reagent2Count);
				if (rs.getBoolean("pulseReagent2Consumed"))
					ability.addPulseConsumeReagent(pulseRreagent2Required, reagent2Count);
			}
			int pulseRreagent3Required = rs.getInt("pulseReagent3Required");
			if (pulseRreagent3Required > 0) {
				Integer reagent3Count = rs.getInt("pulseReagent3Count");
				if (reagent3Count == null) {
					reagent3Count = 1;
				}
				ability.addPulseReagent(pulseRreagent3Required, reagent3Count);
				if (rs.getBoolean("pulseReagent3Consumed"))
					ability.addPulseConsumeReagent(pulseRreagent3Required, reagent3Count);
			}
			if(Log.loggingDebug)Log.debug("Ability puls reagent consume : " + rs.getBoolean("pulseReagentConsumed") + " " + rs.getBoolean("pulseReagent2Consumed") + " " + rs.getBoolean("pulseReagent3Consumed") + " set " + ability.getPulseConsumeReagentList());
				
			/*	if (rs.getBoolean("pulsReagent2Consumed"))
					ability.setPulsConsumeReagent(true);
				if (rs.getBoolean("pulsReagent3Consumed"))
					ability.setPulsConsumeReagent(true);
				*/
			Integer pulseAmmoUsed = rs.getInt("pulseAmmoUsed");
			if (pulseAmmoUsed != null) {
				ability.setPulseAmmoReq(pulseAmmoUsed);
			}
			////////////////////////////////////////////////////////

			String castingAnim = HelperFunctions.readEncodedString(rs.getBytes("activationAnimation"));
			if (castingAnim == null)
				castingAnim = "";
			ability.setCastingAnim(castingAnim);
			String castingParticles = HelperFunctions.readEncodedString(rs.getBytes("activationParticles"));
			if (castingParticles == null)
				castingParticles = "";
			ability.setCastingAffinity(castingParticles);
			// ability.setVigor(rs.getInt("vigor"));
			// Log.debug("Got vigor: " + rs.getInt("vigor"));
			int casterEffectRequired = rs.getInt("casterEffectRequired");
			if (casterEffectRequired > 0) {
				ability.addAttackerEffectReq(casterEffectRequired);
				if (rs.getBoolean("casterEffectConsumed"))
					ability.addAttackerEffectConsumption(casterEffectRequired);
			}
			int targetEffectRequired = rs.getInt("targetEffectRequired");
			if (targetEffectRequired > 0) {
				ability.addTargetEffectReq(targetEffectRequired);
				if (rs.getBoolean("targetEffectConsumed"))
					ability.addTargetEffectConsumption(targetEffectRequired);
			}
			String weaponRequired = HelperFunctions.readEncodedString(rs.getBytes("weaponRequired"));
			if (weaponRequired != null && !weaponRequired.isEmpty()
					// .equals("")
					&& !weaponRequired.contains("none") && !weaponRequired.contains("None")) {
				String[] wepontypes = weaponRequired.split(";");
				for (String wt : wepontypes) {
					if (wt.length() > 0) {
						String type = RequirementChecker.getNameEditorOptionChoice("Weapon Type", Integer.parseInt(wt));
						if (type != "")
							ability.addWeaponReq(type);
					}
				}
			}
			// if (rs.getBoolean("decrementWeaponUses"))
			// ability.setDecrementWeaponUses(true);
			int reagentRequired = rs.getInt("reagentRequired");
			if (reagentRequired > 0) {
				Integer reagentCount = rs.getInt("reagentCount");
				if (reagentCount == null) {
					reagentCount = 1;
				}
				ability.addReagent(reagentRequired, reagentCount);
				if (rs.getBoolean("reagentConsumed"))
					ability.addConsumeReagent(reagentRequired, reagentCount);
			}
			int reagent2Required = rs.getInt("reagent2Required");
			if (reagent2Required > 0) {
				Integer reagent2Count = rs.getInt("reagent2Count");
				if (reagent2Count == null) {
					reagent2Count = 1;
				}
				ability.addReagent(reagent2Required, reagent2Count);
				if (rs.getBoolean("reagent2Consumed"))
					ability.addConsumeReagent(reagent2Required, reagent2Count);
			}
			int reagent3Required = rs.getInt("reagent3Required");
			if (reagent3Required > 0) {
				Integer reagent3Count = rs.getInt("reagent3Count");
				if (reagent3Count == null) {
					reagent3Count = 1;
				}
				ability.addReagent(reagent3Required, reagent3Count);
				if (rs.getBoolean("reagent3Consumed"))
					ability.addConsumeReagent(reagent3Required, reagent3Count);
			}
			/*if (rs.getBoolean("reagentConsumed"))
				ability.setConsumeReagents(true);
			if (rs.getBoolean("reagent2Consumed"))
				ability.setConsumeReagents(true);
			if (rs.getBoolean("reagent3Consumed"))
				ability.setConsumeReagents(true);*/
			if(Log.loggingDebug)Log.debug("Ability reagent consume : " + rs.getBoolean("reagentConsumed") + " " + rs.getBoolean("reagent2Consumed") + " " + rs.getBoolean("reagent3Consumed") + " set " + ability.getConsumeReagentList());

			ability.setConsumeOnActivation(rs.getBoolean("consumeOnActivation"));

			Integer ammoUsed = rs.getInt("ammoUsed");
			if (ammoUsed != null) {
				ability.setAmmoReq(ammoUsed);
			}
			ability.setMaxRange(rs.getInt("maxRange"));
			ability.setMinRange(rs.getInt("minRange"));
			ability.setAreaOfEffectRadius(rs.getInt("aoeRadius"));
			ability.setAreaOfEffectAngle(rs.getFloat("aoeAngle") / 2f);
			ability.setSpeed(rs.getFloat("speed"));
			ability.setRangeChunkLength(rs.getFloat("chunk_length"));
			ability.setCastingInRun(rs.getBoolean("castingInRun"));

			String targetType = HelperFunctions.readEncodedString(rs.getBytes("targetType"));
			String targetSubType = HelperFunctions.readEncodedString(rs.getBytes("targetSubType"));
			if(Log.loggingDebug)Log.debug("Setting target type: " + targetType+" target sub type: "+targetSubType);

			if (targetType.equals("Single Target")) {
				ability.setTargetType(AgisAbility.TargetType.SINGLE_TARGET);
				if(Log.loggingDebug)Log.debug("ABILITY: targetType for ability " + ability.getName() + " to Single Target");
			}else if (targetType.startsWith("Location")) {
				ability.setTargetType(AgisAbility.TargetType.LOCATION);
				if(Log.loggingDebug)Log.debug("ABILITY: targetType for ability " + ability.getName() + " to Location");
			} else if (targetType.equals("AoE")) {
				ability.setTargetType(AgisAbility.TargetType.AREA);
				if(Log.loggingDebug)Log.debug("ABILITY: targetType for ability " + ability.getName() + " to AoE");
			} else if (targetType.equals("Group")) {
				ability.setTargetType(AgisAbility.TargetType.GROUP);
				if(Log.loggingDebug)Log.debug("ABILITY: targetType for ability " + ability.getName() + " to Group");
			}

			if (targetSubType.equals("Enemy")) {
				ability.setTargetSubType(AgisAbility.TargetSubType.ENEMY);
				if(Log.loggingDebug)Log.debug("ABILITY: targetSubType for ability " + ability.getName() + " to Enemy");
			} else if (targetSubType.equals("Self")) {
				ability.setTargetSubType(AgisAbility.TargetSubType.SELF);
				if(Log.loggingDebug)Log.debug("ABILITY: targetSubType for ability " + ability.getName() + " to Self");
//			} else if (targetType.equals("Self Location")) {
//				ability.setTargetType(AgisAbility.TargetType.SELF_LOCATION);
//				Log.debug("ABILITY: targetType for ability " + ability.getName() + " to Self Location");
			} else if (targetSubType.equals("Friendly")) {
				ability.setTargetSubType(AgisAbility.TargetSubType.FRIEND);
				if(Log.loggingDebug)Log.debug("ABILITY: targetSubType for ability " + ability.getName() + " to Friend");
			} else if (targetSubType.equals("Friend Not Self")) {
				ability.setTargetSubType(AgisAbility.TargetSubType.FRIEND_NOT_SELF);
				if(Log.loggingDebug)Log.debug("ABILITY: targetSubType for ability " + ability.getName() + " to Friend Not Self");
			} else if (targetSubType.equals("Friendly or Enemy")) {
				ability.setTargetSubType(AgisAbility.TargetSubType.FRIEND_OR_ENEMY);
				if(Log.loggingDebug)Log.debug("ABILITY: targetSubType for ability " + ability.getName() + " to Friend Or Enemy");
//			} else if (targetType.equals("Group")) {
//				ability.setTargetType(AgisAbility.TargetType.GROUP);
//				Log.debug("ABILITY: targetType for ability " + ability.getName() + " to Group");
//			} else if (targetType.equals("AoE Enemy")) {
//				ability.setTargetType(AgisAbility.TargetType.AREA_ENEMY);
//				Log.debug("ABILITY: targetType for ability " + ability.getName() + " to AoE Enemy");
//			} else if (targetType.equals("AoE Friendly")) {
//				ability.setTargetType(AgisAbility.TargetType.AREA_FRIENDLY);
//				Log.debug("ABILITY: targetType for ability " + ability.getName() + " to AoE Friendly");
			} else {
				ability.setTargetSubType(AgisAbility.TargetSubType.ANY);
				if(Log.loggingDebug)Log.debug("ABILITY: targetSubType for ability " + ability.getName() + " to Any");
			}


			String aoeType = HelperFunctions.readEncodedString(rs.getBytes("aoeType"));
			if (aoeType == null) {
				ability.setAoETypeType(AgisAbility.AoeType.NONE);
			} else if (ability.getTargetType().equals(AgisAbility.TargetType.GROUP) || ability.getTargetType().equals(AgisAbility.TargetType.LOCATION)
					|| ability.getTargetType().equals(AgisAbility.TargetType.AREA)) {
				if (aoeType.equals("PlayerRadius")) {
					ability.setAoETypeType(AgisAbility.AoeType.PLAYER_RADIUS);
				} else if (aoeType.equals("TargetRadius")) {
					ability.setAoETypeType(AgisAbility.AoeType.TARGET_RADIUS);
				} else if (aoeType.equals("LocationRadius")) {
					ability.setAoETypeType(AgisAbility.AoeType.LOCATION_RADIUS);
				}
			} else {
				ability.setAoETypeType(AgisAbility.AoeType.NONE);
			}

			ability.setCombatState(rs.getInt("combatState"));

			ability.setInterceptType(rs.getInt("interceptType"));
			ability.setReqTarget(rs.getBoolean("reqTarget"));
			ability.setReqFacingTarget(rs.getBoolean("reqFacingTarget"));
			//ability.autoRotateToTarget(rs.getBoolean("autoRotateToTarget"));
			ability.setPositional(rs.getInt("relativePositionReq"));


			// Targetable Species
			String targetableSpecies = HelperFunctions.readEncodedString(rs.getBytes("speciesTargetReq"));
			if (targetableSpecies == null || targetableSpecies.equals("") || targetableSpecies.equals("Any")) {
				ability.addTargetableSpecies(AgisAbility.TargetSpecies.ANY);
			} else if (targetableSpecies.equals("Beast")) {
				ability.addTargetableSpecies(AgisAbility.TargetSpecies.BEAST);
			} else if (targetableSpecies.equals("Humanoid")) {
				ability.addTargetableSpecies(AgisAbility.TargetSpecies.HUMANOID);
			} else if (targetableSpecies.equals("Elemental")) {
				ability.addTargetableSpecies(AgisAbility.TargetSpecies.ELEMENTAL);
			} else if (targetableSpecies.equals("Undead")) {
				ability.addTargetableSpecies(AgisAbility.TargetSpecies.UNDEAD);
			} else if (targetableSpecies.equals("Player")) {
				ability.addTargetableSpecies(AgisAbility.TargetSpecies.PLAYER);
			} else if (targetableSpecies.equals("Non Player")) {
				ability.addTargetableSpecies(AgisAbility.TargetSpecies.NONPLAYER);
			} else if (targetableSpecies.equals("Dragon")) {
				ability.addTargetableSpecies(AgisAbility.TargetSpecies.DRAGON);
			} else {
				ability.addTargetableSpecies(AgisAbility.TargetSpecies.UNINIT);
			}
			String specificTarget = HelperFunctions.readEncodedString(rs.getBytes("specificTargetReq"));
			if (specificTarget != null && !specificTarget.equals("")) {
				LinkedList<String> specificTargets = new LinkedList<String>();
				specificTargets.add(specificTarget);
				ability.setSpecificTargets(specificTargets);
			}

			ability.setTargetDeath(rs.getInt("targetState"));
			ability.setCasterDeath(rs.getInt("casterState"));

			if (rs.getBoolean("globalCooldown")) {
				ability.addCooldown(new Cooldown("GLOBAL", Math.round(CombatPlugin.GLOBAL_COOLDOWN * 1000D)));
				if (Log.loggingDebug)
					Log.debug("GLOBAL_COOLDOWN " + ability.getID() + " => " + Math.round(CombatPlugin.GLOBAL_COOLDOWN * 1000D));

			}

			// TODO: Change the weapon cooldown to be a property for the ability
			// that adds a cooldown that is the same as the equipped weapon
			// cooldown
			if (rs.getBoolean("weaponCooldown"))
				ability.addCooldown(new Cooldown("WEAPON", Math.round(CombatPlugin.WEAPON_COOLDOWN * 1000D)));
			String cooldown = HelperFunctions.readEncodedString(rs.getBytes("cooldown1Type"));
			float cooldownDuration = rs.getFloat("cooldown1Duration") * 1000f;
			if (cooldownDuration > 0) {
				if (cooldown != null && !cooldown.equals("")) {
					ability.addCooldown(new Cooldown(cooldown,
							(int) cooldownDuration));
				} else {
					ability.addCooldown(new Cooldown("" + ability.getID(),
							(int) cooldownDuration));
				}
			}
			if (rs.getBoolean("startCooldownsOnActivation")) {
				ability.startCooldownsOnActivation(true);
			}
			/*
			 * cooldown = rs.getString("cooldown2Type"); if (cooldown != null &&
			 * !cooldown.equals("")) ability.addCooldown(new Cooldown(cooldown,
			 * rs.getInt("cooldown2Duration")));
			 */
			// ability.setTooltip(rs.getString("tooltip"));
			// Coordinated Effects


			ability.setChild(rs.getBoolean("is_child"));
			ability.setCheckBusy(rs.getBoolean("checkBusy"));
			ability.setMakeBusy(rs.getBoolean("makeBusy"));
			ability.setEnemyTargetChangeToSelf(rs.getBoolean("enemyTargetChangeToSelf"));;
			ability.setWeaponMustBeDrawn(rs.getBoolean("weaponMustBeDrawn"));
			ability.setDrawnWeaponBefore(rs.getBoolean("drawnWeaponBefore"));

			LoadComboData(ability);
			String powerUpCoordEffect = HelperFunctions.readEncodedString(rs.getBytes("powerUpCoordEffect"));
			if (powerUpCoordEffect != null) {
				CoordinatedEffect coordEffect = loadCoordEffect(powerUpCoordEffect);
				if (coordEffect != null) {
					ability.setPowerUpCoordEffect(coordEffect);
				}
			}
			LoadAbilitiesPowerUp(ability);

			if(ability.getPowerUpDefinitions().size() == 0)
				Log.error("ABILITY: " + ability.getName() + " it will not work without a defined PowerUp Definition !!!!!!");
		/*	String coordinatedEffect = HelperFunctions.readEncodedString(rs.getBytes("aoeCoordEffect"));
			if (coordinatedEffect != null) {	
				CoordinatedEffect coordEffect = loadCoordEffect(coordinatedEffect);
				if (coordEffect != null) {
					ability.addCoordEffect(AgisAbility.ActivationState.AOE_LOCATION, coordEffect);
				}
			}*/

		} catch (SQLException e) {
			Log.dumpStack("setAbilityData: SQLException:" + e);

		}
	}

	private void LoadComboData(AgisAbility ability) {
		String sql ="SELECT * FROM ability_combos where ability_parent_id = "+ability.getID();
		//Log.error("LoadComboData  sql="+sql);
		try (PreparedStatement ps = queries.prepare(sql)) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						//Currency newCurrency = new Currency();
						int ability_sub_id = rs.getInt("ability_sub_id");
						float chance_min = rs.getInt("chance_min");
						float chance_max = rs.getInt("chance_max");
						float time = rs.getFloat("time");
						boolean show_in_center_ui = rs.getBoolean("show_in_center_ui");
						boolean replace_in_slot = rs.getBoolean("replace_in_slot");
						boolean check_cooldown = rs.getBoolean("check_cooldown");
						AbilityComboData acd = new AbilityComboData(ability_sub_id,chance_min,chance_max,time,show_in_center_ui,replace_in_slot);
						acd.setCheckCooldown(check_cooldown);
						ability.addComboData(acd);
					//	Log.error("loadIconSkillPrefabData id="+id+" icon length="+icon2.length());
					}
				}
			}
			//props.put("num",c);
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException"+e);
		}

	}

	private void LoadAbilitiesPowerUp(AgisAbility ability) {

		try (PreparedStatement ps = queries.prepare("SELECT * FROM `abilities_powerup_settings` where ability_id = " + ability.getID())) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						Integer id = rs.getInt("id");
						Float thresholdMaxTime = rs.getFloat("thresholdMaxTime");
						AbilityPowerUpData apud = new AbilityPowerUpData();
						apud.setID(id);
						thresholdMaxTime = thresholdMaxTime * 1000F;
						apud.setThresholdMaxTime(thresholdMaxTime.longValue());
						ability.addPowerUpDefinition(apud);
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadAbilitiesEffects: SQLException:"+e);
			e.printStackTrace();
		}
		for (AbilityPowerUpData apud : ability.getPowerUpDefinitions().values()) {
			LoadAbilitiesEffects(apud);
			LoadAbilityAbilities(apud);
			LoadAbilityTriggers(apud);
			LoadAbilityCoordEffects(apud, ability);

		}
	}

	private HashMap<String, Serializable> LoadAbilitiesPowerUpPrefabData(int c, int abilityId) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int t=0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `abilities_powerup_settings` where ability_id = " + abilityId)) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						Integer id = rs.getInt("id");
						Float thresholdMaxTime = rs.getFloat("thresholdMaxTime");
						thresholdMaxTime = thresholdMaxTime * 1000F;
						if(Log.loggingDebug)Log.debug("ability "+abilityId+" powerup "+id+" thresholdMaxTime="+thresholdMaxTime);
						props.put("i" + c + "pu" + t + "t", thresholdMaxTime.longValue());
						props.putAll(LoadAbilitiesEffectsPrefabData("i" + c + "pu" + t, id));
						props.putAll(LoadAbilityAbilitiesPrefabData("i" + c + "pu" + t, id));

						t++;
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadAbilitiesEffects: SQLException:"+e);
			e.printStackTrace();
		}
		if(Log.loggingDebug)Log.debug("ability "+abilityId+" powerup t="+t+" c="+c);
		props.put("i" + c + "punum" ,t);
		return props;
	}


	private void LoadAbilityCoordEffects(AbilityPowerUpData data, AgisAbility ability) {

		try (PreparedStatement ps = queries.prepare("SELECT * FROM `abilities_coordeffects` where ability_power_id = " + data.getID())) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						String coordinatedEffect = HelperFunctions.readEncodedString(rs.getBytes("coordEffect"));
						if (coordinatedEffect != null) {
							CoordinatedEffect coordEffect = loadCoordEffect(coordinatedEffect);
							if (coordEffect != null) {
								String stateString = HelperFunctions.readEncodedString(rs.getBytes("coordEffectEvent"));
								if (stateString != null) {
									AgisAbility.ActivationState state = AgisAbility.ActivationState.ACTIVATING;

									if (stateString.equals("activating")) {
										state = AgisAbility.ActivationState.ACTIVATING;
									} else if (stateString.equals("completed")) {
										state = AgisAbility.ActivationState.COMPLETED;
									} else if (stateString.equals("activated")) {
										state = AgisAbility.ActivationState.ACTIVATED;
									} else if (stateString.equals("initializing")) {
										state = AgisAbility.ActivationState.INIT;
									} else if (stateString.equals("channelling")) {
										state = AgisAbility.ActivationState.CHANNELLING;
									} else if (stateString.equals("interrupted")) {
										state = AgisAbility.ActivationState.INTERRUPTED;
									} else if (stateString.equals("failed")) {
										state = AgisAbility.ActivationState.FAILED;
									} else if (stateString.equals("ability_pulse")) {
										state = AgisAbility.ActivationState.ABILITY_PULSE;
									}
									data.addCoordEffect(state, coordEffect);
									if(Log.loggingDebug)Log.debug("LoadAbilityCoordEffects: added coord effect: " + coordEffect.getEffectName() + " to state: " + state + " of AbilityPowerUpData: " + data.getID()+" for ability "+ability.getName());
								} else {
									Log.error("LoadAbilityCoordEffects: coordEffectEvent is not sellected for coord effect: " + coordEffect.getEffectName() + " of AbilityPowerUpData: " + data.getID()+" for ability "+ability.getName());
								}
							}
						}
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadAbilityCoordEffects: SQLException:"+e);
			e.printStackTrace();
		}
	}


	private void LoadAbilitiesEffects(AbilityPowerUpData data) {

		try (PreparedStatement ps = queries.prepare("SELECT * FROM `ability_effects` where ability_power_id = " + data.getID())) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						Integer effectId = rs.getInt("effect");
						if (effectId != null && effectId > 0) {
							int delay = rs.getInt("delay");
							float chance_min = rs.getFloat("chance_min");
							float chance_max = rs.getFloat("chance_max");
							String target = HelperFunctions.readEncodedString(rs.getBytes("target"));
							AbilityEffectDefinition ed = new AbilityEffectDefinition(target, effectId, chance_min, chance_max, delay);
							if(Log.loggingDebug)Log.debug("LoadAbilitiesEffects: adding effect " +effectId+" to AbilityPowerUpData " + data.getID() +" for target "+target+ " with chance: (" + chance_min+" ,"+chance_max+")");
							data.addEffectDefinition(ed);
						}
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadAbilitiesEffects: SQLException:"+e);
			e.printStackTrace();
		}
	}
	private HashMap<String, Serializable> LoadAbilitiesEffectsPrefabData(String s, int id) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		String effects="";
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `ability_effects` where ability_power_id = " + id)) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						Integer effectId = rs.getInt("effect");
						if (effectId != null && effectId > 0) {
							int delay = rs.getInt("delay");
							float chance_min = rs.getFloat("chance_min");
							float chance_max = rs.getFloat("chance_max");
							String target = HelperFunctions.readEncodedString(rs.getBytes("target"));
							effects += target + ":" + effectId + ":" + delay + ":" + chance_min + ":" + chance_max + "|";
						}
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadAbilitiesEffects: SQLException:"+e);
			e.printStackTrace();
		}
		if(effects.length()>0) {
			effects = effects.substring(0, effects.length()-1);
		}
		if(Log.loggingDebug)Log.debug("ability  powerup "+id+" effects="+effects);
		props.put(s+"ef", effects);
		return props;
	}

	private void LoadAbilityAbilities(AbilityPowerUpData data) {

		try (PreparedStatement ps = queries.prepare("SELECT * FROM `ability_abilities` where ability_power_id = " + data.getID())) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						Integer abilityId = rs.getInt("ability");
						if (abilityId != null && abilityId > 0) {
							int delay = rs.getInt("delay");
							float chance_min = rs.getFloat("chance_min");
							float chance_max = rs.getFloat("chance_max");
							String target = HelperFunctions.readEncodedString(rs.getBytes("target"));
							AbilityAbilitiesDefinition ad = new AbilityAbilitiesDefinition(target, abilityId, chance_min, chance_max, delay);
							if(Log.loggingDebug)Log.debug("LoadAbilityAbilities: adding ability " +abilityId+" to AbilityPowerUpData " + data.getID() +" for target "+target+ " with chance: (" + chance_min+" ,"+chance_max+")");
							data.addAbilityDefinition(ad);
						}
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadAbilitiesEffects: SQLException:"+e);
			e.printStackTrace();
		}
	}
	private HashMap<String, Serializable> LoadAbilityAbilitiesPrefabData(String s, int id) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		String abilities="";
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `ability_abilities` where ability_power_id = " + id)) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						Integer abilityId = rs.getInt("ability");
						if (abilityId != null && abilityId > 0) {
							int delay = rs.getInt("delay");
							float chance_min = rs.getFloat("chance_min");
							float chance_max = rs.getFloat("chance_max");
							String target = HelperFunctions.readEncodedString(rs.getBytes("target"));
							abilities += target + ":" + abilityId + ":" + delay + ":" + chance_min + ":" + chance_max + "|";
						}
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadAbilitiesEffects: SQLException:"+e);
			e.printStackTrace();
		}
		if(abilities.length()>0) {
			abilities = abilities.substring(0, abilities.length()-1);
		}
		if(Log.loggingDebug)Log.debug("ability  powerup "+id+" abilities="+abilities);
		props.put(s+"ab", abilities);
		return props;
	}


public void LoadAbilityTriggers(AbilityPowerUpData data) {
	try (PreparedStatement ps = queries.prepare("SELECT * FROM abilities_triggers where ability_power_id = " + data.getID())) {
		try (ResultSet rs = queries.executeSelect(ps)) {
			if (rs != null) {
				while (rs.next()) {
					int id = rs.getInt("trigger_id");
					if (id > 0)
						data.addTriggerProfile(LoadAbilityTriggerProfile(id));
				}
			}
		}
	} catch (SQLException e) {
		Log.dumpStack("LoadAbilityTriggerActions: SQLException:" + e);
		e.printStackTrace();
	}
}
	public ArrayList<TriggerAction> LoadAbilityTriggerActions(int id) {
		ArrayList<TriggerAction> actions = new ArrayList<TriggerAction>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM abilities_triggers_actions where abilities_triggers_id = "+id)) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {

						int _t = rs.getInt("target");
						TriggerAction.Target target = TriggerAction.Target.ALL;
						if(_t==1)
							target = TriggerAction.Target.CASTER;
						else if(_t==2)
							target = TriggerAction.Target.TARGET;
						TriggerAction ta= new TriggerAction(target, rs.getInt("ability"),rs.getInt("effect"),rs.getInt("mod_v"),rs.getFloat("mod_p"),rs.getFloat("chance_min"),rs.getFloat("chance_max"));

						actions.add(ta);
						if(Log.loggingDebug)Log.debug("LoadAbilityTriggerActions Load for trigger profile "+id+" "+ta);
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadAbilityTriggerActions: SQLException:" + e);
			e.printStackTrace();
		}
		return actions;
	}

	public TriggerProfile LoadAbilityTriggerProfile(int id) {
		try (PreparedStatement ps = queries.prepare("SELECT * FROM abilities_triggers_profile where isactive = 1 and id ="+id)) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						if(Log.loggingDebug)Log.debug("LoadAbilityTriggerProfile id="+id);
						String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
						TriggerProfile tp = new TriggerProfile(rs.getInt("id"), name);
						int type = rs.getInt("event_type");
						// TriggerProfile.Type etype;
						switch (type) {
							case 0:
								tp.SetType(TriggerProfile.Type.DODGE);
								break;
							case 1:
								tp.SetType(TriggerProfile.Type.MISS);
								break;
							case 2:
								tp.SetType(TriggerProfile.Type.DAMAGE);
								break;
							case 3:
								tp.SetType(TriggerProfile.Type.HEAL);
								break;
							case 4:
								tp.SetType(TriggerProfile.Type.CRITICAL);
								break;
							case 5:
								tp.SetType(TriggerProfile.Type.KILL);
								break;
							case 6:
								tp.SetType(TriggerProfile.Type.PARRY);
								break;
							case 7:
								tp.SetType(TriggerProfile.Type.SLEEP);
								break;
							case 8:
								tp.SetType(TriggerProfile.Type.STUN);
								break;
						}

						int atype = rs.getInt("action_type");
						switch (atype) {
							case 0:
								tp.SetActionType(TriggerProfile.ActionType.DEALT);
								break;
							case 1:
								tp.SetActionType(TriggerProfile.ActionType.RECEIVED);
								break;
						}
						tp.SetRace(rs.getInt("race"));
						tp.SetClass(rs.getInt("class"));
						tp.SetChanceMin(rs.getFloat("chance_min"));
						tp.SetChanceMax(rs.getFloat("chance_max"));

						String tags = HelperFunctions.readEncodedString(rs.getBytes("tags"));
						if (tags.length() > 0) {
							String[] tagsArray = tags.split(";");
							for (int j = 0; j < tagsArray.length; j++) {
								if (tagsArray[j].length() > 0) {
									tp.addTag(Integer.parseInt(tagsArray[j]));
								}
							}
						}
						tp.SetActions(LoadAbilityTriggerActions(id));
						return tp;
					}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadAbilityTriggerProfile: SQLException:" + e);
			e.printStackTrace();
		}
		return null;
	}

	public Map<String, Serializable> loadIconSkillPrefabData(int id, int c) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		String sql = "SELECT id,icon,icon2 FROM `skills` where isactive = 1 and id =" + id;
		if(Log.loggingDebug)Log.debug("loadIconSkillPrefabData  sql=" + sql);
		try (PreparedStatement ps = queries.prepare(sql)) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String icon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
    					String icon2 = HelperFunctions.readEncodedString(rs.getBytes("icon2"));
						if(Log.loggingDebug)Log.debug("loadIconSkillPrefabData id=" + id + " icon length=" + icon2.length());
    					props.put("i" + c + "icon2", icon2);
    					props.put("i" + c + "icon", icon);
    				}
    			}
            }
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack(" SQLException" + e);
		}
		return props;
	}
	

	
	/**
	 * get count of the skills definitions
	 * @return
	 */
	public int getCountSkills() {
		int count = 0;
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM skills where isactive=1")) {
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
	
	
	public HashMap<Integer, SkillTemplate> loadSkills() {
		HashMap<Integer, SkillTemplate> skills = new HashMap<Integer, SkillTemplate>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `skills` where isactive = 1")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int skillID = rs.getInt("id");
    					String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					int aspect = rs.getInt("aspect");
    					int oppositeAspect = rs.getInt("oppositeAspect");
    					boolean talent = rs.getBoolean("talent");
    					boolean mainAspectOnly = rs.getBoolean("mainAspectOnly");
    					String stat1 = HelperFunctions.readEncodedString(rs.getBytes("primaryStat"));
    					String stat2 = HelperFunctions.readEncodedString(rs.getBytes("secondaryStat"));
    					String stat3 = HelperFunctions.readEncodedString(rs.getBytes("thirdStat"));
    					String stat4 = HelperFunctions.readEncodedString(rs.getBytes("fourthStat"));
    					int primaryStatValue= rs.getInt("primaryStatValue");
						int primaryStatInterval = rs.getInt("primaryStatInterval");
						int secondaryStatValue = rs.getInt("secondaryStatValue");
						int secondaryStatInterval = rs.getInt("secondaryStatInterval");
						int thirdStatValue = rs.getInt("thirdStatValue");
						int thirdStatInterval = rs.getInt("thirdStatInterval");
						int fourthStatValue = rs.getInt("fourthStatValue");
						int fourthStatInterval = rs.getInt("fourthStatInterval");
						if(Log.loggingDebug)Log.debug("Setup Skill stat1="+stat1+" stat2="+stat2+" stat3="+stat3+" stat4="+stat4);
						boolean autoLearn = rs.getBoolean("automaticallyLearn");
						SkillTemplate skillTmpl = new SkillTemplate(skillID, name, aspect, oppositeAspect, mainAspectOnly, stat1,primaryStatValue,primaryStatInterval, stat2,secondaryStatValue,secondaryStatInterval, stat3,thirdStatValue,thirdStatInterval, stat4,fourthStatValue,fourthStatInterval, autoLearn, talent);


						skillTmpl.setType(rs.getInt("type"));
    					
    					// Load in other properties
    					skillTmpl.setSkillPointCost(rs.getInt("skillPointCost"));
    					skillTmpl.setMaxLevel(rs.getInt("maxLevel"));
    
    					Integer parentSkill = rs.getInt("parentSkill");
    					if (parentSkill != null && parentSkill != -1) {
    						skillTmpl.setParentSkill(parentSkill);
    						skillTmpl.setParentSkillLevelReq(rs.getInt("parentSkillLevelReq"));
    					}
    
    					// Load skill pre-reqs
    					int prereqSkillID = rs.getInt("prereqSkill1");
    					if (prereqSkillID != -1) {
    						int prereqSkillLevel = rs.getInt("prereqSkill1Level");
    						skillTmpl.setPrereqSkill1(prereqSkillID);
    						skillTmpl.setPrereqSkill1Level(prereqSkillLevel);
    					}
    					prereqSkillID = rs.getInt("prereqSkill2");
    					if (prereqSkillID != -1) {
    						int prereqSkillLevel = rs.getInt("prereqSkill2Level");
    						skillTmpl.setPrereqSkill2(prereqSkillID);
    						skillTmpl.setPrereqSkill2Level(prereqSkillLevel);
    					}
    					prereqSkillID = rs.getInt("prereqSkill3");
    					if (prereqSkillID != -1) {
    						int prereqSkillLevel = rs.getInt("prereqSkill3Level");
    						skillTmpl.setPrereqSkill3(prereqSkillID);
    						skillTmpl.setPrereqSkill3Level(prereqSkillLevel);
    					}
    					skillTmpl.setPlayerLevelReq(rs.getInt("playerLevelReq"));
    					skillTmpl.setSkillProfileID(rs.getInt("skill_profile_id"));
    						// Load skill abilities
    					LoadSkillAbilities(skillTmpl);
    					Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
    					long date = 0L;
    					if (taxPaidUntil != null) {
    						date = taxPaidUntil.getTime();
    					}
    					skillTmpl.setDate(date);
    					skills.put(skillID, skillTmpl);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadSkills: SQLException:"+e);
			e.printStackTrace();
		}
		return skills;
	}

	/**
	 * Loads in the Abilities the Skill gives the player and adds them to the
	 * Skill Template.
	 * 
	 * @param skillTmpl
	 */
	private void LoadSkillAbilities(SkillTemplate skillTmpl) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `skill_ability_gain` where skillID = "
						+ skillTmpl.getSkillID()  + " AND isactive = 1")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Integer abilityID = rs.getInt("abilityID");
    					if (abilityID != null && abilityID > 0) {
    						int level = rs.getInt("skillLevelReq");
    						String abilityName = getAbilityName(abilityID);
    						boolean autoLearn = rs.getBoolean("automaticallyLearn");
    						skillTmpl.addSkillAbility(level, abilityID,abilityName, autoLearn);
							if(Log.loggingDebug)Log.debug("SKILL: adding ability " + abilityID
    								+ " to skill: " + skillTmpl.getSkillID()
    								+ " with skill level: " + level);
    					}
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("LoadSkillAbilities: SQLException:"+e);
			e.printStackTrace();
		}
	}

	/**
	 * Gets the name for the Ability specified.
	 * 
	 * @param abilityID
	 * @return
	 */
	private String getAbilityName(int abilityID) {
	    try (PreparedStatement ps = queries.prepare("SELECT name FROM `abilities` where id=" + abilityID + "")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					return name;
    				}
    			}
            }
		} catch (SQLException e) {
			Log.dumpStack("getAbilityName: SQLException:"+e);
			e.printStackTrace();
		}
		return null;
	}

	public HashMap<Integer, SkillProfileTemplate> loadSkillProfiles() {
		Log.debug("loadSkillProfiles: start load ");
			HashMap<Integer, SkillProfileTemplate> skills = new HashMap<Integer, SkillProfileTemplate>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `skill_profile` where isactive = 1")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int profileID = rs.getInt("id");
    					String name = HelperFunctions.readEncodedString(rs.getBytes("profile_name"));
    					String level_diff = HelperFunctions.readEncodedString(rs.getBytes("level_diff"));
    					SkillProfileTemplate skillTmpl = new SkillProfileTemplate(profileID, name);
    					if (level_diff.length()>0)
    						if (level_diff.contains(";")) {
    							int ii=1;
    							String[] levsd = level_diff.split(";");
    							for (String s : levsd) {
    								skillTmpl.addLevelDiff(ii++, Float.parseFloat(s));
    							}
    						}else {
    							skillTmpl.addLevelDiff(1, Float.parseFloat(level_diff));
    						}
    					
    					skillTmpl.setLevelExp(loadSkillProfileLevels(profileID));
    					skills.put(profileID, skillTmpl);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadSkillProfiles: SQLException:"+e);
		}

		if(Log.loggingDebug)Log.debug("loadSkillProfiles: loaded "+skills.size());
		return skills;
	}

	private HashMap<Integer, Integer> loadSkillProfileLevels(int ID) {
		if(Log.loggingDebug)Log.debug("loadSkillProfileLevels: start for profile:"+ID);
				HashMap<Integer, Integer> levelExp = new HashMap<Integer, Integer>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `skill_profile_levels` where profile_id =" + ID + " AND isactive=1")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int level = rs.getInt("level");
    					int required_xp = rs.getInt("required_xp");
    					levelExp.put(level, required_xp);
    				}
    			}
    		}
		} catch (SQLException e) {
			Log.dumpStack("loadSkillProfileLevels: SQLException:"+e);
			e.printStackTrace();
		}
		if(Log.loggingDebug)Log.debug("loadSkillProfileLevels: end for profile:"+ID+" size:"+levelExp.size());
		return levelExp;
	}
	
	
	public CoordinatedEffect loadCoordEffect(String coordEffectName) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `coordinated_effects` where name='"
						+ coordEffectName + "'")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int coordID = rs.getInt("id");
    					String coordType = HelperFunctions.readEncodedString(rs.getBytes("prefab"));
						if(Log.loggingDebug)Log.debug("COORD: coordType is: " + coordType);
    					// Custom co-ordinated effect
    					CoordinatedEffect coordEffect = new CoordinatedEffect(
    							coordType);
    					coordEffect.sendSourceOid(true);
    					coordEffect.sendTargetOid(true);
    					coordEffect.putArgument("result", "success");
						if(Log.loggingDebug)Log.debug("COORD: returning coordEffect: " + coordID);
    					return coordEffect;
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadCoordEffect: SQLException:"+e);
			e.printStackTrace();
		}
		return null;
	}

	public ArrayList<ArenaCategory> loadArenaCategories() {
		ArrayList<ArenaCategory> list = new ArrayList<ArenaCategory>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM arena_categories where isactive = 1")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
						if(Log.loggingDebug)Log.debug("ARENADB: loading in classic arena: " + id);
    
    					ArrayList<String> skins = new ArrayList<String>();
    					for (int i = 1; i <= 4; i++) {
    						if (HelperFunctions.readEncodedString(rs.getBytes("skin" + i)) != null)
    							skins.add(HelperFunctions.readEncodedString(rs.getBytes("skin" + i)));
    					}
    					ArenaCategory category = new ArenaCategory(id, skins);
    					list.add(category);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadArenaCategories: SQLException:"+e);
			e.printStackTrace();
		}
		return list;
	}

	public ArrayList<ArenaTemplate> loadArenaTemplates() {
		ArrayList<ArenaTemplate> list = new ArrayList<ArenaTemplate>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM arena_templates where isactive = 1")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int arenaType = rs.getInt("arenaType");
    					int id = rs.getInt("id");
						if(Log.loggingDebug)Log.debug("ARENADB: loading in classic arena: " + id);
    					String arenaName = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					int arenaCategory = rs.getInt("arenaCategory");
    					int instanceTemplateID = rs.getInt("arenaInstanceID");
    					//String worldFile = HelperFunctions.readEncodedString(rs.getBytes("worldFile"));
    					int duration = rs.getInt("length");
    					int victoryCondition = rs.getInt("defaultWinner");
    					boolean raceSpecific = false;
    					int numRounds = 1;
    					ArrayList<ArrayList<Integer>> spawns = new ArrayList<ArrayList<Integer>>();
    					ArenaTemplate tmpl = new ArenaTemplate(id, arenaType,
    							arenaCategory, duration, victoryCondition,
    							instanceTemplateID, arenaName, raceSpecific, numRounds,
    							spawns);
    					for (int i = 1; i <= 4; i++) {
    						int teamID = rs.getInt("team" + i);
    						if (teamID != -1) {
    							loadArenaTeam(teamID, tmpl);
    						}
    					}
    					tmpl.setLevelReq(rs.getInt("levelReq"));
    					tmpl.setLevelMax(rs.getInt("levelMax"));
    					tmpl.setDescription(HelperFunctions.readEncodedString(rs.getBytes("description")));
    						int victoryCurrency = rs.getInt("victoryCurrency");
    					int victoryPayment = rs.getInt("victoryPayment");
    					HashMap<Integer, Integer> victoryPayments = new HashMap<Integer, Integer>();
    					victoryPayments.put(victoryCurrency, victoryPayment);
    					tmpl.setVictoryPayment(victoryPayments);
    					int defeatCurrency = rs.getInt("defeatCurrency");
    					int defeatPayment = rs.getInt("defeatPayment");
    					HashMap<Integer, Integer> defeatPayments = new HashMap<Integer, Integer>();
    					defeatPayments.put(defeatCurrency, defeatPayment);
    					tmpl.setDefeatPayment(defeatPayments);
    					int victoryExp = rs.getInt("victoryExp");
    					int defeatExp = rs.getInt("defeatExp");
    					tmpl.setVictoryExp(victoryExp);
    					tmpl.setDefeatExp(defeatExp);
    					int start_minute = rs.getInt("start_minute");
    					int end_minute = rs.getInt("end_minute");
    					int start_hour = rs.getInt("start_hour");
    					int end_hour = rs.getInt("end_hour");
    					tmpl.setStartMinute(start_minute);
    					tmpl.setEndMinute(end_minute);
    					tmpl.setStartHour(start_hour);
    					tmpl.setEndHour(end_hour);
    					tmpl.setUseWeapons(false);
    					//tmpl.setUseWeapons(rs.getBoolean("useWeapons"));
    					list.add(tmpl);
    				}
    			}
            }
		} catch (SQLException e) {
			Log.dumpStack("loadArenaTemplates: SQLException:"+e);
			e.printStackTrace();
		}
		return list;
	}

	public void loadArenaTeam(int teamID, ArenaTemplate tmpl) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM arena_teams where id = " + teamID)) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
						if(Log.loggingDebug)Log.debug("ARENADB: loading in arena team: " + teamID);
    					String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					int size = rs.getInt("size");
    					String race = HelperFunctions.readEncodedString(rs.getBytes("race"));
    					int goal = rs.getInt("goal");
    					Point spawnPoint = new Point(rs.getFloat("spawnX"), rs.getFloat("spawnY"), rs.getFloat("spawnZ"));
    					tmpl.addTeam(name, size, race, goal, spawnPoint);
    				}
    			}
            }
		} catch (SQLException e) {
			Log.dumpStack("loadArenaTeam: SQLException:"+e);
			e.printStackTrace();
		}
	}

	/**
	 * Having too many connection errors, so adding this function to help cope
	 * with it
	 */
	public void close() {
		Log.dumpStack("CombatDababase.Close");
		queries.close();
	}
}
