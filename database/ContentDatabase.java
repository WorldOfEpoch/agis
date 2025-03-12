package atavism.agis.database;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.parsers.*;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import atavism.agis.objects.*;
import atavism.agis.objects.Currency;
import atavism.agis.plugins.*;
import atavism.agis.plugins.WeatherPlugin.WeatherProfile;
import atavism.agis.util.*;
import atavism.server.engine.*;
import atavism.server.math.*;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

public class ContentDatabase {
	private  static Queries queries;
	


	public ContentDatabase(boolean keepAlive) {
		if(Log.loggingDebug)Log.debug("ContentDatabase queries="+queries);
        if (queries == null) {
            synchronized (ContentDatabase.class) {
                if (queries == null) {
                    queries = new Queries(keepAlive);
                }
            }
        }
	}
	
	public ConcurrentHashMap<Integer, GuildLevelSettings> GetGuildLevelSetting(){
	    ConcurrentHashMap<Integer, GuildLevelSettings> list = new ConcurrentHashMap<>();
	    List<Integer> levels = new ArrayList<>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `guild_level_settings`")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    GuildLevelSettings as = new GuildLevelSettings();
	                    int level = rs.getInt("level");
	                    as.setLevel(level);
	                    as.setMembersNum(rs.getInt("members_num"));
	                    as.setMerchantTable(rs.getInt("merchant_table"));
	                    as.setWarehouseNumSlot(rs.getInt("warehouse_num_slots"));
						if(Log.loggingDebug) Log.debug("GetGuildLevelSetting " + as);
	                    list.put(level, as);
	                    levels.add(level);
	                }
	            }
	        }
	    } catch (SQLException e) {
	        Log.error("GetGuildLevelSetting Exception " + e);
	        e.printStackTrace();
	    }
	    // Batch load guild level requirements
	    loadGuildLevelRequireItems(list, levels);
	    return list;
	}
	

	public void loadGuildLevelRequireItems(ConcurrentHashMap<Integer, GuildLevelSettings> guildLevels, List<Integer> levels) {
	    if (levels.isEmpty()) {
	        return;
	    }
	    StringBuilder placeholders = new StringBuilder();
	    for (int i = 0; i < levels.size(); i++) {
	        placeholders.append("?");
	        if (i < levels.size() - 1) {
	            placeholders.append(",");
	        }
	    }
	    String sql = "SELECT * FROM guild_level_requirements WHERE level IN (" + placeholders + ") ORDER BY level, item_id ASC";
	    try (PreparedStatement ps = queries.prepare(sql)) {
	        for (int i = 0; i < levels.size(); i++) {
	            ps.setInt(i + 1, levels.get(i));
	        }
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    int level = rs.getInt("level");
	                    int item = rs.getInt("item_id");
	                    int itemCount = rs.getInt("count");
	                    GuildLevelSettings gls = guildLevels.get(level);
	                    if (gls != null) {
	                        gls.addItems(item, itemCount);
	                        if (Log.loggingDebug)
	                            Log.debug("loadGuildLevelRequireItems: level=" + level + " adding item=" + item + " itemCount=" + itemCount);
	                    }
	                }
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	}

/**
 * Loads in the list of currencies from the database.
 * @return
 */
public HashMap<String, Serializable> loadGlobalEventsPrefabData() {
	HashMap<String, Serializable> props = new HashMap<String, Serializable>();
	int c = 0;
	try (PreparedStatement ps = queries.prepare("SELECT * FROM `global_events` where isactive = 1")) {
	    try (ResultSet rs = queries.executeSelect(ps)) {
    		if (rs != null) {
    			while (rs.next()) {
    				int ID = rs.getInt("id");
    				Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
    				long date = 0L;
    				if (taxPaidUntil != null) {
    					date = taxPaidUntil.getTime();
    				}
					if(Log.loggingDebug)Log.debug("loadGlobalEventsPrefabData: id=" + ID + " new ");
    				String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    				String Icon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
    				String Icon2 = HelperFunctions.readEncodedString(rs.getBytes("icon2"));
    				String Description = HelperFunctions.readEncodedString(rs.getBytes("description"));
    				if (Description == null)
    					Description = "";
    
    				props.put("i" + c + "date", date);
    				props.put("i" + c + "id", ID);
    				props.put("i" + c + "name", name);
    				props.put("i" + c + "icon", Icon);
    				props.put("i" + c + "icon2", Icon2);
    				props.put("i" + c + "desc", Description);
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
	
	public HashMap<Integer,GlobalEventSettings> loadGlobalEvents() {
		HashMap<Integer,GlobalEventSettings> list = new HashMap<Integer,GlobalEventSettings>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `global_events` where isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					GlobalEventSettings as = new GlobalEventSettings();
    					as.setId(rs.getInt("id"));
    					as.setStartYear(rs.getInt("start_year"));
    					as.setStartMonth(rs.getInt("start_month"));
    					as.setStartDay(rs.getInt("start_day"));
    					as.setStartHour(rs.getInt("start_hour"));
    					as.setStartMinute(rs.getInt("start_minute"));
    					as.setEndYear(rs.getInt("end_year"));
    					as.setEndMonth(rs.getInt("end_month"));
    					as.setEndDay(rs.getInt("end_day"));
    					as.setEndHour(rs.getInt("end_hour"));
    					as.setEndMinute(rs.getInt("end_minute"));
    					
    					as.setName(HelperFunctions.readEncodedString(rs.getBytes("name")));
    					as.setDescription(HelperFunctions.readEncodedString(rs.getBytes("description")));
    
    					as.setIcon(HelperFunctions.readEncodedString(rs.getBytes("icon")));
    					as.setIconData(HelperFunctions.readEncodedString(rs.getBytes("icon2")));
    					
    					as.setBonuses(getGlobalEventBonuses(as.getId()));
						if(Log.loggingDebug)Log.debug("GetAchievementsSetting "+as);
    					list.put(rs.getInt("id"),as);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return list;
	}
	
	ArrayList<BonusSettings> getGlobalEventBonuses(int id){
		ArrayList<BonusSettings> list =new ArrayList<BonusSettings>();
	    try (PreparedStatement ps = queries.prepare("SELECT ab.value, ab.valuep, bs.name as sname, bs.code FROM  global_events_bonuses as ab JOIN bonuses_settings as bs ON  ab.bonus_settings_id = bs.id WHERE ab.global_event_id="+id)) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					BonusSettings bs = new BonusSettings(HelperFunctions.readEncodedString(rs.getBytes("sname")), HelperFunctions.readEncodedString(rs.getBytes("code")), rs.getInt("value"), rs.getFloat("valuep"),-1);
    					list.add( bs);
						if(Log.loggingDebug)Log.debug("getGlobalEventBonuses "+bs);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.exception(e);
		}
		
		return list;
		
	}
	
	public ArrayList<AchievementSetting> GetAchievementsSetting(){
		ArrayList<AchievementSetting> list = new ArrayList<AchievementSetting>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `achievement_settings` where isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					AchievementSetting as = new AchievementSetting();
    					as.setId(rs.getInt("id"));
    					as.setType(rs.getInt("type"));
    					as.setValue(rs.getInt("value"));
    					as.setName(HelperFunctions.readEncodedString(rs.getBytes("name")));
    					as.setDescription(HelperFunctions.readEncodedString(rs.getBytes("description")));
    					as.setObjects(HelperFunctions.readEncodedString(rs.getBytes("objects")));
    					as.setStats(getAchievementStats(as.getId()));
    					as.setBonuses(getAchievementBonuses(as.getId()));
						if(Log.loggingDebug)Log.debug("GetAchievementsSetting "+as);
    					list.add(as);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return list;
	}
	
	ArrayList<EnchantStat> getAchievementStats(int id){
		ArrayList<EnchantStat> list =new ArrayList<EnchantStat>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `achievement_stats` where achievement_id = "+id)) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					EnchantStat as = new EnchantStat();
    					String stat = HelperFunctions.readEncodedString(rs.getBytes("stat"));
    					Integer value = rs.getInt("value");
    					Float valuep = rs.getFloat("valuep");
    					list.add(new EnchantStat(stat, value, valuep/*/100f*/));
						if(Log.loggingDebug)Log.debug("getAchievementStats "+as);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return list;
		
	}
	ArrayList<BonusSettings> getAchievementBonuses(int id){
		ArrayList<BonusSettings> list =new ArrayList<BonusSettings>();
	    try (PreparedStatement ps = queries.prepare("SELECT ab.value, ab.valuep, bs.name as sname, bs.code FROM  achievement_bonuses as ab JOIN bonuses_settings as bs ON  ab.bonus_settings_id = bs.id WHERE ab.achievement_id="+id)) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					BonusSettings bs = new BonusSettings(HelperFunctions.readEncodedString(rs.getBytes("sname")), HelperFunctions.readEncodedString(rs.getBytes("code")), rs.getInt("value"), rs.getFloat("valuep"),-1);
    					list.add( bs);
						if(Log.loggingDebug)Log.debug("getAchievementBonuses "+bs);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return list;
		
	}
	
	public int getCountAchievements() {
		int count = 0;
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM achievement_settings where isactive = 1")) {
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
	
	
	public ArrayList<AchievementSetting> GetRankingsSetting(){
		ArrayList<AchievementSetting> list = new ArrayList<AchievementSetting>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `ranking_settings` where isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					AchievementSetting as = new AchievementSetting();
    					as.setId(rs.getInt("id"));
    					as.setType(rs.getInt("type"));
    					as.setValue(rs.getInt("count"));
    					as.setName(HelperFunctions.readEncodedString(rs.getBytes("name")));
    					as.setDescription(HelperFunctions.readEncodedString(rs.getBytes("description")));
    					//as.setObjects(HelperFunctions.readEncodedString(rs.getBytes("objects")));
						if(Log.loggingDebug)Log.debug("GetAchievementsSetting "+as);
    					list.add(as);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return list;
	}
	
	public int getCountRankings() {
		int count = 0;
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM ranking_settings where isactive = 1")) {
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
	
	
	public HashMap<Integer, WeatherProfile> loadWeatherProfiles() {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `weather_profile` where isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				HashMap<Integer, WeatherProfile> profiles = new HashMap<Integer, WeatherProfile>();
    				while (rs.next()) {
    					WeatherProfile wp = new WeatherProfile();
    					wp.SetID(rs.getInt("id"));
    					wp.SetName(HelperFunctions.readEncodedString(rs.getBytes("name")));
    					wp.SetTemperatureMin(rs.getFloat("temperature_min"));
    					wp.SetTemperatureMax(rs.getFloat("temperature_max"));
    					wp.SetHumidityMin(rs.getFloat("humidity_min"));
    					wp.SetHumidityMax(rs.getFloat("humidity_max"));
    					wp.SetWindDirectionMin(rs.getFloat("wind_direction_min"));
    					wp.SetWindDirectionMax(rs.getFloat("wind_direction_max"));
    					wp.SetWindSpeedMin(rs.getFloat("wind_speed_min"));
    					wp.SetWindSpeedMax(rs.getFloat("wind_speed_max"));
    					wp.SetWindTurbulenceMin(rs.getFloat("wind_turbulence_min"));
    					wp.SetWindTurbulenceMax(rs.getFloat("wind_turbulence_max"));
    					wp.SetFogHeightPowerMin(rs.getFloat("fog_height_power_min"));
    					wp.SetFogHeightPowerMax(rs.getFloat("fog_height_power_max"));
    					wp.SetFogHeightMax(rs.getFloat("fog_height_max"));
    					wp.SetFogDistancePowerMin(rs.getFloat("fog_distance_power_min"));
    					wp.SetFogDistancePowerMax(rs.getFloat("fog_distance_power_max"));
    					wp.SetFogDistanceMax(rs.getFloat("fog_distance_max"));
    					wp.SetRainPowerMin(rs.getFloat("rain_power_min"));
    					wp.SetRainPowerMax(rs.getFloat("rain_power_max"));
    					wp.SetRainPowerTerrainMin(rs.getFloat("rain_power_terrain_min"));
    					wp.SetRainPowerTerrainMax(rs.getFloat("rain_power_terrain_max"));
    					wp.SetRainMinHeight(rs.getFloat("rain_min_height"));
    					wp.SetRainMaxHeight(rs.getFloat("rain_max_height"));
    					wp.SetHailPowerMin(rs.getFloat("hail_power_min"));
    					wp.SetHailPowerMax(rs.getFloat("hail_power_max"));
    					wp.SetHailPowerTerrainMin(rs.getFloat("hail_power_terrain_min"));
    					wp.SetHailPowerTerrainMax(rs.getFloat("hail_power_terrain_max"));
    					wp.SetHailMinHeight(rs.getFloat("hail_min_height"));
    					wp.SetHailMaxHeight(rs.getFloat("hail_max_height"));
    					wp.SetSnowPowerMin(rs.getFloat("snow_power_min"));
    					wp.SetSnowPowerMax(rs.getFloat("snow_power_max"));
    					wp.SetSnowPowerTerrainMin(rs.getFloat("snow_power_terrain_min"));
    					wp.SetSnowPowerTerrainMax(rs.getFloat("snow_power_terrain_max"));
    					wp.SetSnowMinHeight(rs.getFloat("snow_min_height"));
    					wp.SetSnowAgeMin(rs.getFloat("snow_age_min"));
    					wp.SetSnowAgeMax(rs.getFloat("snow_age_max"));
    					wp.SetThunderPowerMin(rs.getFloat("thunder_power_min"));
    					wp.SetThunderPowerMax(rs.getFloat("thunder_power_max"));
    					wp.SetCloudPowerMin(rs.getFloat("cloud_power_min"));
    					wp.SetCloudPowerMax(rs.getFloat("cloud_power_max"));
    					wp.SetCloudMinHeight(rs.getFloat("cloud_min_height"));
    					wp.SetCloudMaxHeight(rs.getFloat("cloud_max_height"));
    					wp.SetCloudSpeedMin(rs.getFloat("cloud_speed_min"));
    					wp.SetCloudSpeedMax(rs.getFloat("cloud_speed_max"));
    					wp.SetMoonPhaseMin(rs.getFloat("moon_phase_min"));
    					wp.SetMoonPhaseMax(rs.getFloat("moon_phase_max"));
    				//	wp.SetSeason(rs.getFloat("season"));
    					profiles.put(wp.GetID(),wp);
    				}
    				return profiles;
    			}
			}
		} catch (SQLException e) {
			Log.error("loadWeatherProfiles: "+e.getMessage()+" "+e.getLocalizedMessage());
		}
		return null;
	}
	
	public ArrayList<Integer> GetWeatherProfilesForInstance2(Integer instId, int month) {
		if(Log.loggingDebug)Log.debug("GetWeatherProfilesForInstance: instId=" + instId + "  month =" + month);
		ArrayList<Integer> list = new ArrayList<Integer>();
		try {
			String sql = "SELECT weather_profile_id, priority FROM `weather_instance` where instance_id = " + instId + (month > 0 ? " AND month" + month + " = 1" : "");
			if(Log.loggingDebug)Log.debug("GetWeatherProfilesForInstance: sql=" + sql);
			try (PreparedStatement ps = queries.prepare(sql)) {
			    try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					// for (int i =0;i<rs.getInt("priority");i++) {
        					list.add(rs.getInt("weather_profile_id"));
        					// }
        				}
        			}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return list;
	}
	
	
	public ArrayList<Integer> GetWeatherProfilesForInstance(Integer instId,Integer month){
		if(Log.loggingDebug)Log.debug("GetWeatherProfilesForInstance: instId="+instId+ " month="+month);
		ArrayList<Integer> list = new ArrayList<Integer>();
		try {
			String sql = "SELECT weather_profile_id,priority FROM `weather_instance` where instance_id = "+instId+" AND month"+month+" = 1";
			if(Log.loggingDebug)Log.debug("GetWeatherProfilesForInstance: sql="+sql);
			try (PreparedStatement ps = queries.prepare(sql)) {
			    try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					for (int i =0;i<rs.getInt("priority");i++) {
        						list.add(rs.getInt("weather_profile_id"));
        						}
        				}
        			}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return list;
	}
	
	public Integer GetSeasonForInstance(Integer instId,Integer month){
	    try (PreparedStatement ps = queries.prepare("SELECT season FROM `weather_season` where instance_id = "+instId+" AND month"+month+" = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    						return rs.getInt("season");
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("weather_season stack");
			//e.printStackTrace();
		}
		return 0;
	}
	
	
	public String loadGameSetting(String settingName) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `game_setting` where name = '" + settingName + "'  AND isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return HelperFunctions.readEncodedString(rs.getBytes("value"));
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public HashMap<Long, AuctionProfile> loadAuctionProfile() {
		HashMap<Long, AuctionProfile> list = new HashMap<Long, AuctionProfile>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `auction_profile` where isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					long id = rs.getInt("id");
    					String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					int cost_price_value = rs.getInt("cost_price_value");
    					float cost_price_percentage = rs.getFloat("cost_price_value_percentage");
    					int currency = rs.getInt("currency");
    					int duration = rs.getInt("duration");
    					int display_limit = rs.getInt("display_limit");
    					int own_limit = rs.getInt("own_limit");
    					int start_price_value = rs.getInt("start_price_value");
    					float start_price_percentage = rs.getFloat("start_price_percentage");
    					AuctionProfile ap = new AuctionProfile();
    					ap.id = id;
    					ap.name = name;
    					ap.cost_price_value = cost_price_value;
    					ap.cost_price_percentage = cost_price_percentage;
    					ap.currency = currency;
    					ap.duration = duration;
    					ap.display_limit = display_limit;
    					ap.own_limit = own_limit;
    					ap.start_price_value = start_price_value;
    					ap.start_price_percentage = start_price_percentage;
    
    					list.put(id, ap);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		if(Log.loggingDebug)Log.debug("loadAuctionProfile: "+list);
		return list;
	}
	
	
	public int getCountAuctions() {
		int count = 0;
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM achievement_settings where isactive = 1")) {
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
	 * Loads in the options and choices from the Atavism Editor window. Saves a copy of the editor options map
	 * to the RequirementChecker class.
	 */
	public void loadEditorOptions() {
	    try {
    	    try (PreparedStatement ps = queries.prepare("SELECT * FROM editor_option where isactive = 1")) {
    	        try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					
        					EditorOptionMapping editorOption = new EditorOptionMapping(rs.getInt("id"), 
        							HelperFunctions.readEncodedString(rs.getBytes("optionType")));
        					editor_options.put(rs.getInt("id"), editorOption);
        				}
        			}
    			}
    	    }
    	    try (PreparedStatement ps = queries.prepare("SELECT * FROM editor_option_choice where isactive = 1")) {
    	            try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					EditorOptionMapping editorOption = editor_options.get(rs.getInt("optionTypeID"));
        					if (editorOption != null)
        						editorOption.choiceMapping.put(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("choice")));
        					else
        						Log.error("Editor option chice category "+rs.getInt("optionTypeID")+" is disabled and choice "+rs.getInt("id")+":"+HelperFunctions.readEncodedString(rs.getBytes("choice"))+" is not disabled");
        				}
        			}
	            }
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		RequirementChecker.setEditorOptions(editor_options);
	}
	
	public HashMap<String, CharacterTemplate> loadCharacterFactoryTemplates() {
		HashMap<String, CharacterTemplate> templates = new HashMap<>();
		Map<Integer, CharacterTemplate> templatesById = new HashMap<>();
		if(Log.loggingDebug)Log.debug("Starting Loading Character templates");
	    List<Integer> templateIds = new ArrayList<>();
		    try (PreparedStatement ps = queries.prepare("SELECT * FROM `character_create_template` where isactive = 1")) {
		        try (ResultSet rs = queries.executeSelect(ps)) {
	    			if (rs != null) {
	    				while (rs.next()) {
	    					int templateID = rs.getInt("id");
	    					int race = rs.getInt("race");
	    					int aspect = rs.getInt("aspect");
	    					int faction = rs.getInt("faction");
	    					int instanceID = rs.getInt("instance");
							int sprint = rs.getInt("sprint");
							int dodge = rs.getInt("dodge");
							if(Log.loggingDebug)Log.debug("Character template "+templateID+" race="+race+" class="+aspect);
	    					float pos_x = rs.getFloat("pos_x");
	    					float pos_y = rs.getFloat("pos_y");
	    					float pos_z = rs.getFloat("pos_z");
	    					float orientation = rs.getFloat("orientation");
	    					int respawnInstanceID = rs.getInt("respawnInstance");
	    					float respawnPosX = rs.getFloat("respawnPosX");
	    					float respawnPosY = rs.getFloat("respawnPosY");
	    					float respawnPosZ = rs.getFloat("respawnPosZ");
	    					int xpProfile = rs.getInt("xpProfile");
	    					int startingLevel = rs.getInt("startingLevel");
							int autoAttackID = rs.getInt("autoAttack");
							int stat_profile_id = rs.getInt("stat_profile_id");
							CharacterTemplate tmpl = new CharacterTemplate();

	    					tmpl.race_description = HelperFunctions.readEncodedString(rs.getBytes("race_description"));
	    					tmpl.race_icon = HelperFunctions.readEncodedString(rs.getBytes("race_icon"));
	    					tmpl.race_icon2 = HelperFunctions.readEncodedString(rs.getBytes("race_icon2"));
	    					tmpl.class_description = HelperFunctions.readEncodedString(rs.getBytes("class_description"));
	    					tmpl.class_icon = HelperFunctions.readEncodedString(rs.getBytes("class_icon"));
	    					tmpl.class_icon2 = HelperFunctions.readEncodedString(rs.getBytes("class_icon2"));

	    					tmpl.setRace(race);
	    					tmpl.setAspect(aspect);
	    					tmpl.setFaction(faction);
	    					tmpl.setInstanceTemplateID(instanceID);
							tmpl.setSprint(sprint);
							tmpl.setDodge(dodge);
							//tmpl.setPortalName("spawn");
	    					tmpl.setSpawnPoint(new Point(pos_x, pos_y, pos_z));
	    					tmpl.setSpawnRotation(orientation);
	    					tmpl.setRespawnInstanceTemplateID(respawnInstanceID);
	    					tmpl.setReSpawnPoint(new Point(respawnPosX, respawnPosY, respawnPosZ));
	    					tmpl.setStartingLevel(startingLevel);
	    					tmpl.setExpProfile(xpProfile);
	    					tmpl.setAutoAttack(autoAttackID);
//							tmpl.setStatProfileId(stat_profile_id);
							tmpl.setStatProfileId(-templateID);
							//loadCharacterFactoryStats(tmpl, templateID);
	    					//tmpl.setStartingSkills(loadCharacterFactorySkills(templateID));
	    					//loadCharacterStartingItems(tmpl, templateID);
	    					//loadCharacterGender(tmpl, templateID);
    						templates.put(race + " " + aspect, tmpl);
		                    templatesById.put(templateID, tmpl);
		                    templateIds.add(templateID);
							if(Log.loggingDebug)Log.debug("CHARTMPL: added character template: " + race + aspect);
	    				}
	    			}
				}
			} catch (SQLException e) {
				Log.exception("Loading Character Templates ",e);
				e.printStackTrace();
			}

		    // Batch load stats, starting skills, items, and genders
		    loadAllCharacterFactoryStats(templatesById, templateIds);
		    loadAllCharacterFactorySkills(templatesById, templateIds);
		    loadAllCharacterStartingItems(templatesById, templateIds);
		    loadAllCharacterGender(templatesById, templateIds);
			return templates;
		}

	public void loadAllCharacterStartingItems(Map<Integer, CharacterTemplate> templatesById, List<Integer> templateIds) {
	    if (templateIds.isEmpty()) {
	        return;
	    }
	    String placeholders = String.join(",", Collections.nCopies(templateIds.size(), "?"));
	    String sql = "SELECT * FROM `character_create_items` WHERE character_create_id IN (" + placeholders + ") AND isactive = 1";
	    try (PreparedStatement ps = queries.prepare(sql)) {
	        for (int i = 0; i < templateIds.size(); i++) {
	            ps.setInt(i + 1, templateIds.get(i));
	        }
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    int templateId = rs.getInt("character_create_id");
	                    int item = rs.getInt("item_id");
	                    int count = rs.getInt("count");
	                    boolean equipped = rs.getBoolean("equipped");
	                    CharacterTemplate tmpl = templatesById.get(templateId);
	                    if (tmpl != null) {
							if(Log.loggingDebug) Log.debug("CHARTMPL: added item " + item + " to template " + templateId);
	                        tmpl.addStartingItem(item, count, equipped);
	                    }
	                }
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	}

	public void loadAllCharacterGender(Map<Integer, CharacterTemplate> templatesById, List<Integer> templateIds) {
	    if (templateIds.isEmpty()) {
	        return;
	    }
	    String placeholders = String.join(",", Collections.nCopies(templateIds.size(), "?"));
	    String sql = "SELECT * FROM `character_create_gender` WHERE character_create_id IN (" + placeholders + ") AND isactive = 1";
	    try (PreparedStatement ps = queries.prepare(sql)) {
	        for (int i = 0; i < templateIds.size(); i++) {
	            ps.setInt(i + 1, templateIds.get(i));
	        }
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    int templateId = rs.getInt("character_create_id");
	                    String model = HelperFunctions.readEncodedString(rs.getBytes("model"));
	                    String icon = HelperFunctions.readEncodedString(rs.getBytes("icon2"));
	                    String iconPath = HelperFunctions.readEncodedString(rs.getBytes("icon"));
	                    int gender = rs.getInt("gender");
	                    CharacterTemplate tmpl = templatesById.get(templateId);
	                    if (tmpl != null) {
	                        tmpl.gender.put(gender, model);
	                        tmpl.genderIcon.put(gender, icon);
	                        tmpl.genderIconPath.put(gender, iconPath);
							if(Log.loggingDebug)Log.debug("loadCharacterGender: added gender " + gender + " with model: " + model + " to character template " + templateId);
	                    }
	                }
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	}

	public void loadAllCharacterFactoryStats(Map<Integer, CharacterTemplate> templatesById, List<Integer> templateIds) {
	    if (templateIds.isEmpty()) {
	        return;
	    }
	    String placeholders = String.join(",", Collections.nCopies(templateIds.size(), "?"));
	    String sql = "SELECT * FROM `character_create_stats` WHERE character_create_id IN (" + placeholders + ") AND isactive = 1";
	    try (PreparedStatement ps = queries.prepare(sql)) {
	        for (int i = 0; i < templateIds.size(); i++) {
	            ps.setInt(i + 1, templateIds.get(i));
	        }
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    int templateId = rs.getInt("character_create_id");
	                    String stat = HelperFunctions.readEncodedString(rs.getBytes("stat"));
	                    int baseValue = rs.getInt("value");
	                    float levelIncrease = rs.getFloat("levelIncrease");
	                    float levelPercentIncrease = rs.getFloat("levelPercentIncrease");
	                    CharacterTemplate tmpl = templatesById.get(templateId);
	                    if (tmpl != null) {
	                        tmpl.AddStatProgression(stat, baseValue, levelIncrease, levelPercentIncrease);
							if(Log.loggingDebug)Log.debug("loadAllCharacterFactoryStats: added stat " + stat + " with value: " + baseValue + " to character template " + templateId);
	                    }
	                }
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	}



	public void loadAllCharacterFactorySkills(Map<Integer, CharacterTemplate> templatesById, List<Integer> templateIds) {
	    if (templateIds.isEmpty()) {
	        return;
	    }
	    String placeholders = String.join(",", Collections.nCopies(templateIds.size(), "?"));
	    String sql = "SELECT * FROM `character_create_skills` WHERE character_create_id IN (" + placeholders + ") AND isactive = 1";
	    Map<Integer, ArrayList<Integer>> skillsByTemplateId = new HashMap<>();
	    try (PreparedStatement ps = queries.prepare(sql)) {
	        for (int i = 0; i < templateIds.size(); i++) {
	            ps.setInt(i + 1, templateIds.get(i));
	        }
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    int templateId = rs.getInt("character_create_id");
	                    int skill = rs.getInt("skill");
	                    skillsByTemplateId.computeIfAbsent(templateId, k -> new ArrayList<>()).add(skill);
						if(Log.loggingDebug)Log.debug("CHARTMPL: added skill " + skill + " to template " + templateId);
	                }
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	    // Now, set the starting skills in the templates
	    for (Map.Entry<Integer, CharacterTemplate> entry : templatesById.entrySet()) {
	        int templateId = entry.getKey();
	        CharacterTemplate tmpl = entry.getValue();
	        ArrayList<Integer> skills = skillsByTemplateId.get(templateId);
	        if (skills != null) {
	            tmpl.setStartingSkills(skills);
	        } else {
	            tmpl.setStartingSkills(new ArrayList<>());
	        }
	    }
	}

	public void loadCharacterGender(CharacterTemplate tmpl, int id) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `character_create_gender` where character_create_id = " + id + " AND isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String model = HelperFunctions.readEncodedString(rs.getBytes("model"));
						String icon = HelperFunctions.readEncodedString(rs.getBytes("icon2"));
						String iconPath = HelperFunctions.readEncodedString(rs.getBytes("icon"));
						int gender = rs.getInt("gender");

						if(Log.loggingDebug)Log.debug("loadCharacterGender: added gender " + gender + " with model: " + model + " to character template");
    					tmpl.gender.put(gender, model);
						tmpl.genderIcon.put(gender, icon);
						tmpl.genderIconPath.put(gender, iconPath);
    				}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void loadCharacterFactoryStats(CharacterTemplate tmpl, int id) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `character_create_stats` where character_create_id = " + id + " AND isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String stat = HelperFunctions.readEncodedString(rs.getBytes("stat"));
    					int baseValue = rs.getInt("value");
    					float levelIncrease = rs.getFloat("levelIncrease");
    					float levelPercentIncrease = rs.getFloat("levelPercentIncrease");
						if(Log.loggingDebug)Log.debug("CHARTMPL: added stat " + stat + " with value: " + baseValue + " to character template");
    					tmpl.AddStatProgression(stat, baseValue, levelIncrease, levelPercentIncrease);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	
	
	public ArrayList<Integer> loadCharacterFactorySkills(int id) {
		ArrayList<Integer> skills = new ArrayList<Integer>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `character_create_skills` where character_create_id = " + id + " AND isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int skill = rs.getInt("skill");
						if(Log.loggingDebug)Log.debug("CHARTMPL: added skill " + skill);
    					skills.add(skill);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return skills;
	}
	
	public void loadCharacterStartingItems(CharacterTemplate tmpl, int id) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `character_create_items` where character_create_id = " + id + " AND isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int item = rs.getInt("item_id");
    					int count = rs.getInt("count");
    					boolean equipped = rs.getBoolean("equipped");
						if(Log.loggingDebug)Log.debug("CHARTMPL: added item " + item);
    					tmpl.addStartingItem(item, count, equipped);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public HashMap<Integer, LootTable> loadLootTables(int category) {
	    HashMap<Integer, LootTable> lootTableMap = new HashMap<>();
	    List<Integer> lootTableIds = new ArrayList<>();

	    String sql = category == -1
	            ? "SELECT * FROM loot_tables WHERE isactive = 1"
	            : "SELECT * FROM loot_tables WHERE category = ? AND isactive = 1";

	    try (PreparedStatement ps = queries.prepare(sql)) {
	        if (category != -1) {
	            ps.setInt(1, category);
	        }

	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    int lootTableId = rs.getInt("id");
						if(Log.loggingDebug)Log.debug("LOOT: Loading loot table: " + lootTableId);

	                    LootTable lootTable = new LootTable();
	                    lootTable.setID(lootTableId);
	                    lootTable.setName(HelperFunctions.readEncodedString(rs.getBytes("name")));

	                    // Initialize item lists
	                    lootTable.setItems(new ArrayList<>());
	                    lootTable.setItemCounts(new ArrayList<>());
	                    lootTable.setItemMaxCounts(new ArrayList<>());
	                    lootTable.setItemChances(new ArrayList<>());

	                    lootTableMap.put(lootTableId, lootTable);
	                    lootTableIds.add(lootTableId);

						if(Log.loggingDebug)Log.debug("LOOT: Added loot table: " + lootTableId);
	                }
	            }
	        }
	    } catch (SQLException e) {
	        Log.error("Error loading loot tables: " + e.getMessage());
	        e.printStackTrace();
	    }

	    // Batch load loot table items
	    loadLootTableItems(lootTableMap, lootTableIds);

	    return lootTableMap;
	}
	public void loadLootTableItems(HashMap<Integer, LootTable> lootTables, List<Integer> lootTableIds) {
	    if (lootTableIds.isEmpty()) {
	        return;
	    }

	    StringBuilder placeholders = new StringBuilder();
	    for (int i = 0; i < lootTableIds.size(); i++) {
	        placeholders.append("?");
	        if (i < lootTableIds.size() - 1) {
	            placeholders.append(",");
	        }
	    }

	    String sql = "SELECT * FROM loot_table_items WHERE loot_table_id IN (" + placeholders.toString() + ") ORDER BY loot_table_id ASC, chance ASC";

	    try (PreparedStatement ps = queries.prepare(sql)) {
	        for (int i = 0; i < lootTableIds.size(); i++) {
	            ps.setInt(i + 1, lootTableIds.get(i));
	        }

	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    int lootTableId = rs.getInt("loot_table_id");
	                    LootTable lootTable = lootTables.get(lootTableId);

	                    if (lootTable == null) {
	                        continue;
	                    }

	                    int item = rs.getInt("item");
	                    int itemCount = rs.getInt("count");
	                    int itemMaxCount = rs.getInt("count_max");
	                    float itemChance = rs.getFloat("chance");

	                    lootTable.getItems().add(item);
	                    lootTable.getItemCounts().add(itemCount);
	                    lootTable.getItemMaxCounts().add(itemMaxCount);
	                    lootTable.getItemChances().add(itemChance);

	                    if (Log.loggingDebug) {
	                        Log.debug("LOOT: Loot table ID=" + lootTableId + " adding item=" + item + " itemCount=" + itemCount
	                                + " itemMaxCount=" + itemMaxCount + " itemChance=" + itemChance);
	                    }
	                }
	            }
	        }
	    } catch (SQLException e) {
	        Log.error("Error loading loot table items: " + e.getMessage());
	        e.printStackTrace();
	    }
	}

	public HashMap<Integer, ArrayList<Graveyard>> loadGraveyards() {
		if(Log.loggingDebug)Log.debug("GRAVEYARD: loading graveyards");
		HashMap<Integer, ArrayList<Graveyard>> nodes = new HashMap<Integer, ArrayList<Graveyard>>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `graveyard` where isactive = 1")) {
			if(Log.loggingDebug)Log.debug("GRAVEYARD: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					int instanceID = rs.getInt("instance");
    					Point loc = new Point();
    					loc.setX(rs.getFloat("locX"));
    					loc.setY(rs.getFloat("locY"));
    					loc.setZ(rs.getFloat("locZ"));
    					
    					Graveyard obj = new Graveyard(id, HelperFunctions.readEncodedString(rs.getBytes("name")), 
    							loc, rs.getInt("factionReq"), rs.getInt("factionRepReq"));
    					
    					if (nodes.containsKey(instanceID)) {
    						nodes.get(instanceID).add(obj);
    					} else {
    						ArrayList<Graveyard> graveyardList = new ArrayList<Graveyard>();
    						graveyardList.add(obj);
    						nodes.put(instanceID, graveyardList);
    					}
						if(Log.loggingDebug)Log.debug("GRAVEYARD: added node " + obj.getID() + " to map");
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return nodes;
	}
	
	public HashMap<Integer, VolumetricRegion> loadRegions(int instanceID, OID instanceOid) {
		if(Log.loggingDebug)Log.debug("REGION: loading regions for instance: " + instanceID);
		HashMap<Integer, VolumetricRegion> nodes = new HashMap<Integer, VolumetricRegion>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `region` where instance=? and isactive = 1")) {
			ps.setInt(1, instanceID);
			if(Log.loggingDebug)Log.debug("REGION: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					AOVector loc = new AOVector();
    					loc.setX(rs.getFloat("locX"));
    					loc.setY(rs.getFloat("locY"));
    					loc.setZ(rs.getFloat("locZ"));
    					VolumetricRegion obj = new VolumetricRegion(id,loc, instanceOid);
    					obj.setName(HelperFunctions.readEncodedString(rs.getBytes("name")));
    					obj.setRegionType(HelperFunctions.readEncodedString(rs.getBytes("regionType")));
    					obj.setActionID(rs.getInt("actionID"));
    					obj.setActionData1(HelperFunctions.readEncodedString(rs.getBytes("actionData1")));
    					obj.setActionData2(HelperFunctions.readEncodedString(rs.getBytes("actionData2")));
    					obj.setActionData3(HelperFunctions.readEncodedString(rs.getBytes("actionData3")));
    					//loadRegionShapes(obj);
    					
    					nodes.put(obj.getID(), obj);
						if(Log.loggingDebug)Log.debug("REGION: added node " + obj.getID() + " to map");
    					//obj.spawn();
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		for (VolumetricRegion region : nodes.values()) {
			loadRegionShapes(region);
			region.spawn();
		}
		return nodes;
	}
	
	public void loadRegionShapes(VolumetricRegion region) {
		if(Log.loggingDebug)Log.debug("REGION: loading interactive objects for region: " + region.getID());
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `region_shape` where regionID=? and isactive = 1")) {
			ps.setInt(1, region.getID());
			if(Log.loggingDebug)Log.debug("REGION: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					AOVector loc = new AOVector();
    					loc.setX(rs.getFloat("locX"));
    					loc.setY(rs.getFloat("locY"));
    					loc.setZ(rs.getFloat("locZ"));
    					String shapeType = HelperFunctions.readEncodedString(rs.getBytes("shape"));
    					AOVector loc2 = new AOVector();
    					loc2.setX(rs.getFloat("loc2X"));
    					loc2.setY(rs.getFloat("loc2Y"));
    					loc2.setZ(rs.getFloat("loc2Z"));
    					Quaternion orient = new Quaternion();
    					orient.setX(rs.getFloat("orientX"));
    					orient.setY(rs.getFloat("orientY"));
    					orient.setZ(rs.getFloat("orientZ"));
    					orient.setW(rs.getFloat("orientW"));
    					region.addShape(shapeType, loc, loc2, orient, rs.getFloat("size1"), rs.getFloat("size2"), rs.getFloat("size3"));
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public HashMap<String, Serializable> loadInteractiveObjectProfilesPrefabData() {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int c = 0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `interactive_object` where instance=-1 and isactive = 1")) {
			if(Log.loggingDebug)Log.debug("loadInteractiveObjectProfilesPrefabData: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						int id = rs.getInt("id");
						Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
						long date = 0L;
						if (taxPaidUntil != null) {
							date = taxPaidUntil.getTime();
						}
						props.put("i" + c + "date", date);
						if(Log.loggingDebug)Log.debug("loadInteractiveObjectProfilesPrefabData: id=" + id + " new");
						props.put("i" + c + "id", id);
						props.put("i" + c + "name", HelperFunctions.readEncodedString(rs.getBytes("name")));
						props.put("i" + c + "ce",HelperFunctions.readEncodedString(rs.getBytes("coordEffect")));
						props.put("i" + c + "it",HelperFunctions.readEncodedString(rs.getBytes("interactionType")));
						props.put("i" + c + "iid",rs.getInt("interactionID"));
						props.put("i" + c + "id1",HelperFunctions.readEncodedString(rs.getBytes("interactionData1")));
						props.put("i" + c + "id2",HelperFunctions.readEncodedString(rs.getBytes("interactionData2")));
						props.put("i" + c + "id3",HelperFunctions.readEncodedString(rs.getBytes("interactionData3")));

						props.put("i" + c + "rt",rs.getInt("respawnTime"));
						props.put("i" + c + "irt",rs.getFloat("interactTimeReq"));
						props.put("i" + c + "r",rs.getFloat("interactDistance"));

						props.put("i" + c + "dd",rs.getFloat("despawnDelay"));
						props.put("i" + c + "dt",rs.getFloat("despawnTime"));
						props.put("i" + c + "mb",rs.getBoolean("makeBusy"));
						props.put("i" + c + "ul",rs.getInt("useLimit"));

						//Require
						props.put("i" + c + "qr",rs.getInt("questReqID"));

						props.put("i" + c + "ml",rs.getInt("maxLevel"));
						props.put("i" + c + "nl",rs.getInt("minLevel"));

						props.put("i" + c + "ir",rs.getInt("itemReq"));
						props.put("i" + c + "icr",rs.getInt("itemCountReq"));
						props.put("i" + c + "irg",rs.getBoolean("itemReqGet"));
						props.put("i" + c + "cr",rs.getInt("currencyReq"));
						props.put("i" + c + "ccr",rs.getInt("currencyCountReq"));
						props.put("i" + c + "crg",rs.getBoolean("currencyReqGet"));

						String icon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
						String icon2 = HelperFunctions.readEncodedString(rs.getBytes("icon2"));
						props.put("i" + c + "icon2", icon2);
						props.put("i" + c + "icon", icon);
						c++;
					}
				}
			}
			props.put("num", c);
			if(Log.loggingDebug)Log.debug("loadInteractiveObjectProfilesPrefabData " + props);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return props;
	}

	public HashMap<Integer, InteractiveObject> loadInteractiveObjectProfiles() {
		if(Log.loggingDebug)Log.debug("loadInteractiveObjectProfiles: loading interactive object profiles");
		HashMap<Integer, InteractiveObject> profiles = new HashMap<Integer, InteractiveObject>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `interactive_object` where instance=-1 and isactive = 1")) {
			if(Log.loggingDebug)Log.debug("loadInteractiveObjectProfiles: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						int id = rs.getInt("id");
						AOVector p = new AOVector();
						InteractiveObject obj = new InteractiveObject(id, p, null);
						obj.setName(HelperFunctions.readEncodedString(rs.getBytes("name")));
						obj.setGameObject(HelperFunctions.readEncodedString(rs.getBytes("gameObject")));
						obj.setCoordEffect(HelperFunctions.readEncodedString(rs.getBytes("coordEffect")));
						obj.setQuestIDReq(rs.getInt("questReqID"));
						obj.setInteractionType(HelperFunctions.readEncodedString(rs.getBytes("interactionType")));
						obj.setInteractionID(rs.getInt("interactionID"));
						obj.setInteractionData1(HelperFunctions.readEncodedString(rs.getBytes("interactionData1")));
						obj.setInteractionData2(HelperFunctions.readEncodedString(rs.getBytes("interactionData2")));
						obj.setInteractionData3(HelperFunctions.readEncodedString(rs.getBytes("interactionData3")));
						obj.setRespawnTime(rs.getInt("respawnTime"));
						obj.setHarvestTimeReq(rs.getFloat("interactTimeReq"));
						obj.setInteractionDistance(rs.getFloat("interactDistance"));

						obj.setDespawnDelay(rs.getFloat("despawnDelay"));
						obj.setDespawnTime(rs.getFloat("despawnTime"));
						obj.setMakeBusy(rs.getBoolean("makeBusy"));
						obj.setUseLimit(rs.getInt("useLimit"));
						obj.setMinLevel(rs.getInt("minLevel"));
						obj.setMaxLevel(rs.getInt("maxLevel"));
						obj.setItemReq(rs.getInt("itemReq"));
						obj.setItemReqGet(rs.getBoolean("itemReqGet"));
						obj.setItemCountReq(rs.getInt("itemCountReq"));
						obj.setCurrencyReq(rs.getInt("currencyReq"));
						obj.setCurrencyReqGet(rs.getBoolean("currencyReqGet"));
						obj.setCurrencyCountReq(rs.getInt("currencyCountReq"));
						loadInteractiveObjectCoordEffects(obj);
						profiles.put(obj.getID(), obj);
						if(Log.loggingDebug)Log.debug("loadInteractiveObjectProfiles: added profile " + obj.getID());
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return profiles;
	}

	public HashMap<Integer, InteractiveObject> loadInteractiveObjects(int instanceID, OID instanceOid) {
		if(Log.loggingDebug)Log.debug("INTERACTIVE: loading interactive objects for instance: " + instanceID);
		HashMap<Integer, InteractiveObject> nodes = new HashMap<Integer, InteractiveObject>();
		int maxId = -1;
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `interactive_object` where instance=? and isactive = 1")) {
			ps.setInt(1, instanceID);
			if(Log.loggingDebug)Log.debug("GRID: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
						if(id > maxId)
							maxId = id;
    					AOVector p = new AOVector();
    					p.setX(rs.getFloat("locX"));
    					p.setY(rs.getFloat("locY"));
    					p.setZ(rs.getFloat("locZ"));
    					InteractiveObject obj = new InteractiveObject(id,p, instanceOid);
    					obj.setName(HelperFunctions.readEncodedString(rs.getBytes("name")));
						int profileId = rs.getInt("profileId");
						obj.setProfileId(profileId);
						if(profileId > 0){
							InteractiveObject profile = AgisMobPlugin.interactiveObjectProfiles.get(profileId);
							if(profile != null){
								obj.setGameObject(profile.getGameObject());
								obj.setCoordEffect(profile.getCoordEffect());
								obj.setQuestIDReq(profile.getQuestIDReq());
								obj.setInteractionType(profile.getInteractionType());
								obj.setInteractionID(profile.getInteractionID());
								obj.setInteractionData1(profile.getInteractionData1());
								obj.setInteractionData2(profile.getInteractionData2());
								obj.setInteractionData3(profile.getInteractionData3());
								obj.setRespawnTime(profile.getRespawnTime());
								obj.setHarvestTimeReq(profile.getHarvestTimeReq());
								obj.setInteractionDistance(profile.getInteractionDistance());

								obj.setDespawnDelay(profile.getDespawnDelay());
								obj.setDespawnTime(profile.getDespawnTime());
								obj.setMakeBusy(profile.getMakeBusy());
								obj.setUseLimit(profile.getUseLimit());
								obj.setMaxLevel(profile.getMaxLevel());
								obj.setMinLevel(profile.getMinLevel());
								obj.setItemReq(profile.getItemReq());
								obj.setItemReqGet(profile.getItemReqGet());
								obj.setItemCountReq(profile.getItemCountReq());
								obj.setCurrencyReq(profile.getCurrencyReq());
								obj.setCurrencyReqGet(profile.getCurrencyReqGet());
								obj.setCurrencyCountReq(profile.getCurrencyCountReq());
								obj.setCoordinatedEffectsList(profile.getCoordinatedEffectsList());
							} else {
								obj.setGameObject(HelperFunctions.readEncodedString(rs.getBytes("gameObject")));
								obj.setCoordEffect(HelperFunctions.readEncodedString(rs.getBytes("coordEffect")));
								obj.setQuestIDReq(rs.getInt("questReqID"));
								obj.setInteractionType(HelperFunctions.readEncodedString(rs.getBytes("interactionType")));
								obj.setInteractionID(rs.getInt("interactionID"));
								obj.setInteractionData1(HelperFunctions.readEncodedString(rs.getBytes("interactionData1")));
								obj.setInteractionData2(HelperFunctions.readEncodedString(rs.getBytes("interactionData2")));
								obj.setInteractionData3(HelperFunctions.readEncodedString(rs.getBytes("interactionData3")));
								obj.setRespawnTime(rs.getInt("respawnTime"));
								obj.setHarvestTimeReq(rs.getFloat("interactTimeReq"));
								obj.setInteractionDistance(rs.getFloat("interactDistance"));


								obj.setDespawnDelay(rs.getFloat("despawnDelay"));
								obj.setDespawnTime(rs.getFloat("despawnTime"));
								obj.setMakeBusy(rs.getBoolean("makeBusy"));
								obj.setUseLimit(rs.getInt("useLimit"));
								obj.setMinLevel(rs.getInt("minLevel"));
								obj.setMaxLevel(rs.getInt("maxLevel"));
								obj.setItemReq(rs.getInt("itemReq"));
								obj.setItemReqGet(rs.getBoolean("itemReqGet"));
								obj.setItemCountReq(rs.getInt("itemCountReq"));
								obj.setCurrencyReq(rs.getInt("currencyReq"));
								obj.setCurrencyReqGet(rs.getBoolean("currencyReqGet"));
								obj.setCurrencyCountReq(rs.getInt("currencyCountReq"));
								loadInteractiveObjectCoordEffects(obj);
							}
						}else {
							obj.setGameObject(HelperFunctions.readEncodedString(rs.getBytes("gameObject")));
							obj.setCoordEffect(HelperFunctions.readEncodedString(rs.getBytes("coordEffect")));
							obj.setQuestIDReq(rs.getInt("questReqID"));
							obj.setInteractionType(HelperFunctions.readEncodedString(rs.getBytes("interactionType")));
							obj.setInteractionID(rs.getInt("interactionID"));
							obj.setInteractionData1(HelperFunctions.readEncodedString(rs.getBytes("interactionData1")));
							obj.setInteractionData2(HelperFunctions.readEncodedString(rs.getBytes("interactionData2")));
							obj.setInteractionData3(HelperFunctions.readEncodedString(rs.getBytes("interactionData3")));
							obj.setRespawnTime(rs.getInt("respawnTime"));
							obj.setHarvestTimeReq(rs.getFloat("interactTimeReq"));
							obj.setInteractionDistance(rs.getFloat("interactDistance"));


							obj.setDespawnDelay(rs.getFloat("despawnDelay"));
							obj.setDespawnTime(rs.getFloat("despawnTime"));
							obj.setMakeBusy(rs.getBoolean("makeBusy"));
							obj.setUseLimit(rs.getInt("useLimit"));
							obj.setMinLevel(rs.getInt("minLevel"));
							obj.setMaxLevel(rs.getInt("maxLevel"));
							obj.setItemReq(rs.getInt("itemReq"));
							obj.setItemReqGet(rs.getBoolean("itemReqGet"));
							obj.setItemCountReq(rs.getInt("itemCountReq"));
							obj.setCurrencyReq(rs.getInt("currencyReq"));
							obj.setCurrencyReqGet(rs.getBoolean("currencyReqGet"));
							obj.setCurrencyCountReq(rs.getInt("currencyCountReq"));
							loadInteractiveObjectCoordEffects(obj);
						}
    					nodes.put(obj.getID(), obj);
						if(Log.loggingDebug)Log.debug("INTERACTIVE: added node " + obj.getID() + " to map");
    					try {
							obj.spawn();
						}catch (Exception e){
							e.printStackTrace();
							Log.exception("loadInteractiveObjects " + id+" Exception ",e);
						}
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		AgisMobPlugin.interactiveObjectInstanceMaxId.put(instanceOid, new AtomicInteger(maxId));

		return nodes;
	}

	public void loadInteractiveObjectCoordEffects(InteractiveObject io) {
		//HashMap<Integer, List<Integer[]>> resourceDrops = loadResourceDrops();
		HashMap<String, ResourceNodeGroup> nodeGroupMap = new HashMap<String, ResourceNodeGroup>();

		try (PreparedStatement ps = queries.prepare("SELECT * FROM `interactive_object_coordeffects` where objId=?")) {
			ps.setInt(1, io.getID());
			if(Log.loggingDebug)Log.debug("loadInteractiveObjectCoordEffects: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					while (rs.next()) {
						int id = rs.getInt("id");
						io.AddCoordEffect(HelperFunctions.readEncodedString(rs.getBytes("coordEffect")));
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		//return nodes;
	}


	public String loadCoordEffect(String coordEffectName) {
		if(Log.loggingDebug)Log.debug("loadCoordEffect: coordEffectName="+coordEffectName );
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `coordinated_effects` where name='" + coordEffectName + "'")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int coordID = rs.getInt("id");
    					String coordType = HelperFunctions.readEncodedString(rs.getBytes("prefab"));
						if(Log.loggingDebug)Log.debug("loadCoordEffect: coordType is: " + coordType);
    					return coordType;
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("loadCoordEffect: SQLException:" + e);
			e.printStackTrace();
		}
		if(Log.loggingDebug)Log.debug("loadCoordEffect: no definition for coordEffectName="+coordEffectName );
		return null;
	}
	
	
	public HashMap<Integer, ResourceNodeProfile> loadResourceNodeProfile() {
	    HashMap<Integer, ResourceNodeProfile> profiles = new HashMap<>();
	    List<Integer> profileIds = new ArrayList<>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `resource_node_profile`")) {
			if(Log.loggingDebug) Log.debug("loadResourceNodeProfile: " + ps.toString());
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    ResourceNodeProfile rnp = new ResourceNodeProfile();
	                    int id = rs.getInt("id");
	                    String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
	                    rnp.setId(id);
	                    rnp.setName(name);
    					rnp.setSpawnPecentage(rs.getFloat("spawnPercentage"));
    					rnp.setSpawnPecentageMax(rs.getFloat("spawnPecentageMax"));
    					rnp.setDistance(rs.getFloat("maxHarvestDistance"));
	                    profiles.put(id, rnp);
	                    profileIds.add(id);
	                }
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	    // Batch load profile settings
	    loadResourceNodeProfileSettings(profiles, profileIds);
	    return profiles;
	}
	
	public void loadResourceNodeProfileSettings(HashMap<Integer, ResourceNodeProfile> profiles, List<Integer> profileIds) {
	    if (profileIds.isEmpty()) {
	        return;
	    }
	    String placeholders = profileIds.stream().map(id -> "?").collect(Collectors.joining(","));
	    String sql = "SELECT * FROM `resource_node_sub_profile` WHERE profileId IN (" + placeholders + ")";
	    Map<Integer, List<ResourceNodeProfileSettings>> settingsByProfileId = new HashMap<>();
	    try (PreparedStatement ps = queries.prepare(sql)) {
	        for (int i = 0; i < profileIds.size(); i++) {
	            ps.setInt(i + 1, profileIds.get(i));
	        }
			if(Log.loggingDebug)Log.debug("loadResourceNodeProfileSettings: " + ps.toString());
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    int profileId = rs.getInt("profileId");
	                    int id = rs.getInt("id");
	                    ResourceNodeProfileSettings rnps = new ResourceNodeProfileSettings();
    					rnps.setID(id);
    					rnps.setGameObject(HelperFunctions.readEncodedString(rs.getBytes("gameObject")));
    					
    					rnps.setPriority(rs.getInt("priority"));
    					rnps.setPriorityMax(rs.getInt("priorityMax"));
    					
    					rnps.setHarvestCoordEffect(loadCoordEffect(HelperFunctions.readEncodedString(rs.getBytes("harvestCoordEffect"))));
    					rnps.setActivateCoordEffect(loadCoordEffect(HelperFunctions.readEncodedString(rs.getBytes("activateCoordeffect"))));
    					rnps.setDeactivateCoordEffect(loadCoordEffect(HelperFunctions.readEncodedString(rs.getBytes("deactivateCoordeffect"))));
    					rnps.setSkill(rs.getInt("skill"));
    					rnps.setSkillLevelReq(rs.getInt("skillLevel"));
    					rnps.setSkillLevelMax(rs.getInt("skillLevelMax"));
    					rnps.setSkillExp(rs.getInt("skillExp"));
    					rnps.setWeaponReq(HelperFunctions.readEncodedString(rs.getBytes("weaponReq")));
    					rnps.setEquippedReq(rs.getBoolean("equipped"));
    					rnps.setRespawnTime(rs.getInt("respawnTime"));
    					rnps.setRespawnTimeMax(rs.getInt("respawnTimeMax"));
    					rnps.setHarvestCount(rs.getInt("harvestCount"));
    					rnps.setHarvestTimeReq(rs.getFloat("harvestTimeReq"));
    					rnps.setCooldown(rs.getFloat("cooldown"));
    					rnps.setLootMaxCount(rs.getInt("lootCount"));
    					rnps.setEnsureLoot(rs.getBoolean("ensureLoot"));
    					
    					rnps.setDeactivationDelay(rs.getFloat("deactivationDelay"));
	                    settingsByProfileId.computeIfAbsent(profileId, k -> new ArrayList<>()).add(rnps);
	                }
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	    // Map settings back to profiles
	    for (Map.Entry<Integer, List<ResourceNodeProfileSettings>> entry : settingsByProfileId.entrySet()) {
	        ResourceNodeProfile profile = profiles.get(entry.getKey());
	        if (profile != null) {
	            for (ResourceNodeProfileSettings setting : entry.getValue()) {
	                profile.getSettings().put(profile.getSettings().size(), setting);
	            }
	        }
	    }
	    // Batch load resource drops
	    List<Integer> settingIds = settingsByProfileId.values().stream()
	            .flatMap(List::stream)
	            .map(ResourceNodeProfileSettings::getID)
	            .collect(Collectors.toList());
	    loadResourceDrops(settingsByProfileId, settingIds);
	}
	
	public HashMap<String, Serializable> loadResourceNodeIcons() {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
	    try (PreparedStatement ps = queries.prepare("SELECT id,profileId,cursorIcon,cursorIcon2,selectedIcon,selectedIcon2,updatetimestamp FROM `resource_node_sub_profile` ORDER BY profileId ASC, id ASC")) {
			if(Log.loggingDebug)Log.debug("loadResourceNodeIcons: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			int profile = -1;
    			int setting = 0;
    			int num = 0;
    			if (rs != null) {
    				while (rs.next()) {
    					int profileId = rs.getInt("profileId");
    					if (profile != profileId) {
    						if(profile > 0) {
    							props.put("p" + profile + "_num",setting);
    						}
    						profile = profileId;
    						setting = 0;
    						props.put("p"+num ,profile);
    						num++;
    					}
    					Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
    					long date = 0L;
    					if (taxPaidUntil != null) {
    						date = taxPaidUntil.getTime();
    					}
    					String cicon = HelperFunctions.readEncodedString(rs.getBytes("cursorIcon"));
    					String cicon2 = HelperFunctions.readEncodedString(rs.getBytes("cursorIcon2"));
    					String sicon = HelperFunctions.readEncodedString(rs.getBytes("selectedIcon"));
    					String sicon2 = HelperFunctions.readEncodedString(rs.getBytes("selectedIcon2"));
    
    					props.put("p" + profile + "_date", date);
    					props.put("p" + profile + "_" + setting + "iconc", cicon);
    					props.put("p" + profile + "_" + setting + "iconc2", cicon2);
    					props.put("p" + profile + "_" + setting + "icons", sicon);
    					props.put("p" + profile + "_" + setting + "icons2", sicon2);
    					setting++;
    				}
    				if(profile > 0) {
    					props.put("p" + profile + "_num",setting);
    				}

    			}
    			props.put("num",num);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return props;
	}
	
	public int getCountResourceNodes(int instanceID) {
		int spawnCount = 0;
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM resource_node_template where instance="+instanceID)) {
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
	
	
	public HashMap<Integer, ResourceNode> loadResourceNodes(int instanceID, OID instanceOid, HashMap<OID, HashMap<String, ResourceNodeGroup>> resourceNodeGroups) {
		//HashMap<Integer, List<Integer[]>> resourceDrops = loadResourceDrops();
		HashMap<Integer, ResourceNode> nodes = new HashMap<Integer, ResourceNode>();
		HashMap<String, ResourceNodeGroup> nodeGroupMap = new HashMap<String, ResourceNodeGroup>();
		HashMap<Integer,List<Integer>> profileGroup = new HashMap<Integer,List<Integer>>();  
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `resource_node_template` where instance=?")) {
			ps.setInt(1, instanceID);
			if(Log.loggingDebug)Log.debug("loadResourceNodes: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					int profileId = rs.getInt("profileId");
    					if(!profileGroup.containsKey(profileId))
    						profileGroup.put(profileId, new ArrayList<Integer>());
    					profileGroup.get(profileId).add(id);
    					
    					AOVector p = new AOVector();
    					p.setX(rs.getFloat("locX"));
    					p.setY(rs.getFloat("locY"));
    					p.setZ(rs.getFloat("locZ"));
    					ResourceNode resourceNode;
    					String weaponReq = HelperFunctions.readEncodedString(rs.getBytes("weaponReq"));
						if(Log.loggingDebug)Log.debug("loadResourceNodes: weaponReq: " + weaponReq);
    					if(weaponReq != null && weaponReq.equals("FishingRod")) {
    						resourceNode = new FishingResourceNode(id, p, instanceOid);
    					} else {
    						resourceNode = new ResourceNode(id,p, instanceOid);
    					}
    					resourceNode.setProfileId(profileId);
    					resourceNode.setName(HelperFunctions.readEncodedString(rs.getBytes("name")));
    					resourceNode.setGameObject(HelperFunctions.readEncodedString(rs.getBytes("gameObject")));
    					
    					nodes.put(resourceNode.getID(), resourceNode);
						if(Log.loggingDebug)Log.debug("loadResourceNodes: added node " + resourceNode.getID() + " to map");
    					if (CraftingPlugin.USE_RESOURCE_GROUPS) {
    						// Round position to nearest half of group size
    						int locX = ((int)p.getX()+CraftingPlugin.RESOURCE_GROUP_SIZE/2)/CraftingPlugin.RESOURCE_GROUP_SIZE * CraftingPlugin.RESOURCE_GROUP_SIZE;
    						int locZ = ((int)p.getZ()+CraftingPlugin.RESOURCE_GROUP_SIZE/2)/CraftingPlugin.RESOURCE_GROUP_SIZE * CraftingPlugin.RESOURCE_GROUP_SIZE;
    						int locY = ((int)p.getY()+CraftingPlugin.RESOURCE_GROUP_SIZE/2)/CraftingPlugin.RESOURCE_GROUP_SIZE * CraftingPlugin.RESOURCE_GROUP_SIZE;
    						
    						AOVector newLoc = new AOVector(locX, locY, locZ);
    						// See if a HashMap exists for this rounded position
    						if (!nodeGroupMap.containsKey(newLoc.toString())) {
								if(Log.loggingDebug)Log.debug("loadResourceNodes: creating new node group at loc " + newLoc + " to map");
    							// If not, create group
    							ResourceNodeGroup nodeGroup = new ResourceNodeGroup(newLoc, instanceOid);
    							// Add node to node group
    							nodeGroup.AddResourceNode(resourceNode);
    							nodeGroupMap.put(newLoc.toString(), nodeGroup);
    						} else {
    							// Add node to node group
    							nodeGroupMap.get(newLoc.toString()).AddResourceNode(resourceNode);
								if(Log.loggingDebug)Log.debug("loadResourceNodes: adding node to node group at loc " + newLoc);
    						}
    					} else {
    						//resourceNode.spawn();
    					}
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		if (CraftingPlugin.USE_RESOURCE_GROUPS) {
			for (ResourceNodeGroup group : nodeGroupMap.values()) {
				group.spawn(instanceOid);
			}
			resourceNodeGroups.put(instanceOid, nodeGroupMap);
		}
		Random random = new Random();
		for (int id : profileGroup.keySet()) {
			if (id > 0) {
				ResourceNodeProfile rnp = CraftingPlugin.recourceNodeProfile.get(id);
				List<Integer> list = profileGroup.get(id);
				Collections.shuffle(list);
				double percentage = rnp.getSpawnPecentage();
				if (rnp.getSpawnPecentageMax() > rnp.getSpawnPecentage()) {
					percentage += random.nextDouble() * (rnp.getSpawnPecentageMax() - rnp.getSpawnPecentage());
				}
				percentage /= 100D;
				int count = (int) Math.round(percentage * list.size());
				if(Log.loggingDebug)Log.debug("loadResourceNodes list.size=" + list.size() + " percentage=" + percentage + " count=" + count + " List=" + list);
				HashMap<Integer, Integer> priority = new HashMap<Integer, Integer>();
				int sum = 0;
				for (int i = 0; i < rnp.settingsCount(); i++) {
					priority.put(i, rnp.getPriority(i));
					sum += priority.get(i);
				}
				if(Log.loggingDebug)Log.debug("loadResourceNodes: sum=" + sum + " count settinhs=" + rnp.settingsCount());
				Object[] prioritySorted = priority.entrySet().toArray();
				Arrays.sort(prioritySorted, new Comparator() {
					public int compare(Object o1, Object o2) {
						return ((Map.Entry<Integer, Integer>) o1).getValue() - (((Map.Entry<Integer, Integer>) o2).getValue());
					}
				});

				int j = 0;
				int last=0;
				for (Object e : prioritySorted) {
					int settingId = ((Map.Entry<Integer, Integer>) e).getKey();
					int priorityVal = ((Map.Entry<Integer, Integer>) e).getValue();
					if(Log.loggingDebug)Log.debug("loadResourceNodes: Next Setting " + settingId + " " + priorityVal + " j=" + j);
					while (j <= (count-1) * (last + priorityVal) / sum) {

						if(Log.loggingDebug)Log.debug("loadResourceNodes: Next Setting j=" + j+" "+list.get(j));

						ResourceNode rn = nodes.get(list.get(j));

						ResourceNodeProfileSettings rnps = rnp.getSetting(settingId);
						rn.setSettingId(settingId);
						rn.setHarvestCoordEffect(rnps.getHarvestCoordEffect());
						rn.setActivateCoordEffect(rnps.getActivateCoordEffect());
						rn.setDeactivateCoordEffect(rnps.getDeactivateCoordEffect());
						rn.setSkill(rnps.getSkill());
						rn.setSkillLevelReq(rnps.getSkillLevelReq());
						rn.setSkillLevelMax(rnps.getSkillLevelMax());
						rn.setSkillExp(rnps.getSkillExp());
						rn.setWeaponReq(rnps.getWeaponReq());
						rn.setEquippedReq(rnps.getEquippedReq());
						rn.setRespawnTime(rnps.getRespawnTime());
						rn.setRespawnTimeMax(rnps.getRespawnTimeMax());
						rn.setHarvestCount(rnps.getHarvestCount());
						rn.setHarvestTimeReq(rnps.getHarvestTimeReq());
						rn.setMaxHarvestDistance(rnp.getDistance());
						rn.setCooldown(rnps.getCooldown());
						rn.setDeactivationDelay(rnps.getDeactivationDelay());
						rn.setResourceDrops(rnps.getResourceDrops());
						rn.setLootMaxCount(rnps.getLootMaxCount());
						rn.setEnsureLoot(rnps.getEnsureLoot());
						
						if (CraftingPlugin.USE_RESOURCE_GROUPS) {
							rn.activateAsChildOfGroup();
						}else {
							rn.spawn();
						}
						j++;
					}
					last+=priorityVal;
				}
				for(int k=j;k<list.size();k++) {
					nodes.get(list.get(k)).deactivateAsChildOfGroup();
				}
			}

		}
		return nodes;
	}


	public void loadResourceDrops(Map<Integer, List<ResourceNodeProfileSettings>> settingsByProfileId, List<Integer> settingIds) {
	    if (settingIds.isEmpty()) {
	        return;
	    }
	    String placeholders = settingIds.stream().map(id -> "?").collect(Collectors.joining(","));
	    String sql = "SELECT * FROM `resource_drop` WHERE resourceSubProfileId IN (" + placeholders + ") ORDER BY chanceMax ASC";
	    try (PreparedStatement ps = queries.prepare(sql)) {
	        for (int i = 0; i < settingIds.size(); i++) {
	            ps.setInt(i + 1, settingIds.get(i));
	        }
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            if (rs != null) {
	                while (rs.next()) {
	                    int subProfileId = rs.getInt("resourceSubProfileId");
	                    int item = rs.getInt("item");
	                    int min = rs.getInt("min");
	                    int max = rs.getInt("max");
	                    float chance = rs.getFloat("chance");
	                    float chanceMax = rs.getFloat("chanceMax");
    					if (max < min)
    						max = min;
    					if (min > 0) {
    						if (chanceMax < chance)
    							chanceMax = chance;
						}
	                    // Map back to the appropriate setting
	                    for (List<ResourceNodeProfileSettings> settingsList : settingsByProfileId.values()) {
	                        for (ResourceNodeProfileSettings rnps : settingsList) {
	                            if (rnps.getID() == subProfileId) {
	                                rnps.AddResourceDrop(item, min, max, chance, chanceMax);
	                                break;
	                            }
	                        }
	                    }
	                }
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	}


	public HashMap<String, Serializable> loadBuildObjectPrefabData() {
		HashMap<Integer, BuildObjectStage> stages = loadBuildObjectStages();
		HashMap<Integer, BuildObjectTemplate> grids = new HashMap<Integer, BuildObjectTemplate>();
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int c = 0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `build_object_template` where isactive = 1")) {
			if(Log.loggingDebug)Log.debug("BUILD: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
    					long date = 0L;
    					if (taxPaidUntil != null) {
    						date = taxPaidUntil.getTime();
    					}
						if(Log.loggingDebug)Log.debug("loadBuildObjectPrefabData: id=" + id + " new");
    					props.put("i" + c + "id", id);
    					props.put("i" + c + "name", HelperFunctions.readEncodedString(rs.getBytes("name")));
    					props.put("i" + c + "skill", rs.getInt("skill"));
    					props.put("i" + c + "skillreqlev", rs.getInt("skillLevelReq"));
    					props.put("i" + c + "cat", rs.getInt("category"));
    					String reqWeapon = HelperFunctions.readEncodedString(rs.getBytes("weaponReq"));
    					if (reqWeapon == null)
    						reqWeapon = "";
    					props.put("i" + c + "reqWeapon", reqWeapon);
    					props.put("i" + c + "distreq", (int) (rs.getFloat("distanceReq") * 1000f));
    					props.put("i" + c + "taskreqply", rs.getBoolean("buildTaskReqPlayer"));
    					//props.put("i" + c + "claimtype", rs.getInt("validClaimType"));
    					props.put("i" + c + "claimtype", HelperFunctions.readEncodedString(rs.getBytes("validClaimType")));
    					props.put("i" + c + "objCat", HelperFunctions.readEncodedString(rs.getBytes("claim_object_category")));
    					
    					props.put("i" + c + "itemOnly", rs.getBoolean("availableFromItemOnly"));
    					props.put("i" + c + "date", date);
    					int fstageID = rs.getInt("firstStageID");
    					int stageID = fstageID; 
    					boolean first = false;
    					String item = "";
    					String itemCount = "";
    					String items = "";
    					BuildObjectStage bos = null;
    					while (stageID > 0) {
    						if (stages.containsKey(stageID)) {
    							if (!first) {
    								for (Integer i : stages.get(stageID).getItemReqs().keySet()) {
    									item += i + ";";
    									itemCount += stages.get(stageID).getItemReqs().get(i) + ";";
    								}
    								first = true;
    							} else {
    								for (Integer i : stages.get(stageID).getItemReqs().keySet()) {
    									items += i + ";";
    								}
    							}
    							bos = stages.get(stageID);
    							stageID = stages.get(stageID).getNextStageID();
    						} else {
    							stageID = -1;
    						}
    					}
    					if (item.length() > 0)
    						item = item.substring(0, item.length() - 1);
    					if (itemCount.length() > 0)
    						itemCount = itemCount.substring(0, itemCount.length() - 1);
    					if (items.length() > 0)
    						items = items.substring(0, items.length() - 1);
    					props.put("i" + c + "reqitem", item);
    					props.put("i" + c + "reqitemC", itemCount);
    					props.put("i" + c + "upditems", items);
    					if (bos != null)
    						props.put("i" + c + "lstage", (bos.getProgressGameObject().containsKey(100)?bos.getProgressGameObject().get(100):""));
    					else
    						props.put("i" + c + "lstage", "");
    					
    					
    					props.put("i" + c + "buildSolo", rs.getBoolean("buildSolo"));
    					props.put("i" + c + "fixedTime", rs.getBoolean("fixedTime"));
    					props.put("i" + c + "bTime", (int)(stages.get(fstageID).getBuildTimeReq()*1000f));
    					
    					String icon = HelperFunctions.readEncodedString(rs.getBytes("icon"));
    					String icon2 = HelperFunctions.readEncodedString(rs.getBytes("icon2"));
    					props.put("i" + c + "icon2", icon2);
    					props.put("i" + c + "icon", icon);
    					c++;
    				}
    			}
			}
			props.put("num", c);
			if(Log.loggingDebug)Log.debug("loadBuildObjectPrefabData " + props);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return props;
	}
	
	@Deprecated
	public 	Map<String, Serializable> loadBuildObjectPrefabData( HashMap<Integer,Long> dates) {
		// Load in build stages first
		HashMap<Integer, BuildObjectStage> stages = loadBuildObjectStages();
		HashMap<Integer, BuildObjectTemplate> grids = new HashMap<Integer, BuildObjectTemplate>();
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		//props.put("ext_msg_subtype", "BuildObjPrefabData");
		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(dates); 
		int c = 0;
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `build_object_template` where isactive = 1")) {
			if(Log.loggingDebug)
				Log.debug("BUILD: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					Timestamp taxPaidUntil = rs.getTimestamp("updatetimestamp");
    					long date = 0L;
    					if (taxPaidUntil != null) {
    						date = taxPaidUntil.getTime();
    					}
    					if (dates.containsKey(id)) {
    						objs.remove(id);
    						if (date != dates.get(id)) {
    							if(Log.loggingDebug)
    								Log.debug("loadBuildObjectPrefabData: id=" + id+" newer date" );
    							props.put("i" + c + "id", id);
    							props.put("i" + c + "name", HelperFunctions.readEncodedString(rs.getBytes("name")));
    							props.put("i" + c + "skill", rs.getInt("skill"));
    							props.put("i" + c + "skillreqlev", rs.getInt("skillLevelReq"));
    							props.put("i" + c + "cat", rs.getInt("claim_object_category"));
    							//props.put("i" + c + "reqWeapon", HelperFunctions.readEncodedString(rs.getBytes("weaponReq")));
    							String reqWeapon = HelperFunctions.readEncodedString(rs.getBytes("weaponReq"));
    							if (reqWeapon == null)
    								reqWeapon = "";
    							props.put("i" + c + "reqWeapon", reqWeapon);
    							props.put("i" + c + "distreq", (int)(rs.getFloat("distanceReq")*1000f));
    							props.put("i" + c + "taskreqply", rs.getBoolean("buildTaskReqPlayer"));
    							props.put("i" + c + "buildSolo", rs.getBoolean("buildSolo"));
    							props.put("i" + c + "fixedTime", rs.getBoolean("fixedTime"));
    							
    							
    							props.put("i" + c + "claimtype", HelperFunctions.readEncodedString(rs.getBytes("validClaimType")));
    							//props.put("i" + c + "fstage", rs.getInt("firstStageID"));
    							props.put("i" + c + "itemOnly", rs.getBoolean("availableFromItemOnly"));
    							//props.put("i" + c + "interType", HelperFunctions.readEncodedString(rs.getBytes("interactionType")));
    							//props.put("i" + c + "interId", rs.getInt("interactionID"));
    							//props.put("i" + c + "interData", HelperFunctions.readEncodedString(rs.getBytes("interactionData1")));
    						//	props.put("i" + c + "lstage", date);
    							
    							props.put("i" + c + "date", date);
    							int fstageID = rs.getInt("firstStageID");
    							int stageID = fstageID; 
    							boolean first = false;
    							String item = ""; 
    							String itemCount = "";
    							String items="";
    							BuildObjectStage bos = null; 
    							int laststage=-1;
    							while (stageID > 0) {
    								if (stages.containsKey(stageID)) {
    									if(!first) {
    										for(Integer i : stages.get(stageID).getItemReqs().keySet()) {
    											item += i+";";
    											itemCount += stages.get(stageID).getItemReqs().get(i)+";";
    										}
    										first = true;
    									}else {
    										for(Integer i : stages.get(stageID).getItemReqs().keySet()) {
    											items += i+";";
    										}
    									}
    									bos = stages.get(stageID);
    									if(Log.loggingDebug)
    										Log.debug("loadBuildObjectPrefabData id="+id+" stageID="+stageID);
    									laststage=stageID;
    									stageID = stages.get(stageID).getNextStageID();
    								} else {
    									stageID = -1;
    								}	
    							}
    							if (item.length() > 0)
    								item = item.substring(0, item.length() - 1);
    							if (itemCount.length() > 0)
    								itemCount = itemCount.substring(0, itemCount.length() - 1);
    							if (items.length() > 0)
    								items = items.substring(0, items.length() - 1);
    							props.put("i" + c + "reqitem", item);
    							props.put("i" + c + "reqitemC", itemCount);
    							props.put("i" + c + "upditems", items);
    							if(Log.loggingDebug)
    								Log.debug("loadBuildObjectPrefabData id="+id+" stageID="+laststage+" fstageID="+fstageID+" containsKey="+stages.containsKey(fstageID)+" last BuildObjectStage="+bos);
    							props.put("i" + c + "bTime", (int)(stages.get(fstageID).getBuildTimeReq()*1000f));
    							if(Log.loggingDebug)
    									Log.debug("loadBuildObjectPrefabData id="+id+" stageID="+laststage+" last BuildObjectStage="+bos);
    							if(bos!=null) {
    								props.put("i" + c + "lstage", (bos.getProgressGameObject().containsKey(100)?bos.getProgressGameObject().get(100):""));//getGameObject());
    										
    							} else {
    								props.put("i" + c + "lstage", "");
    								}
    							c++;
    						}
    					} else {
    						if(Log.loggingDebug)
    								Log.debug("loadBuildObjectPrefabData: id=" + id+" new" );
    							props.put("i" + c + "id", id);
    						props.put("i" + c + "name", HelperFunctions.readEncodedString(rs.getBytes("name")));
    						props.put("i" + c + "skill", rs.getInt("skill"));
    						props.put("i" + c + "skillreqlev", rs.getInt("skillLevelReq"));
    						props.put("i" + c + "cat", rs.getInt("claim_object_category"));
    						//props.put("i" + c + "reqWeapon", HelperFunctions.readEncodedString(rs.getBytes("weaponReq")));
    						String reqWeapon = HelperFunctions.readEncodedString(rs.getBytes("weaponReq"));
    						if (reqWeapon == null)
    							reqWeapon = "";
    						props.put("i" + c + "reqWeapon", reqWeapon);
    						props.put("i" + c + "distreq", (int)(rs.getFloat("distanceReq")*1000f));
    						props.put("i" + c + "taskreqply", rs.getBoolean("buildTaskReqPlayer"));
    						props.put("i" + c + "buildSolo", rs.getBoolean("buildSolo"));
    						props.put("i" + c + "fixedTime", rs.getBoolean("fixedTime"));
    						props.put("i" + c + "claimtype", HelperFunctions.readEncodedString(rs.getBytes("validClaimType")));
    						//props.put("i" + c + "fstage", rs.getInt("firstStageID"));
    						props.put("i" + c + "itemOnly", rs.getBoolean("availableFromItemOnly"));
    						//props.put("i" + c + "interType", HelperFunctions.readEncodedString(rs.getBytes("interactionType")));
    						//props.put("i" + c + "interId", rs.getInt("interactionID"));
    						//props.put("i" + c + "interData", HelperFunctions.readEncodedString(rs.getBytes("interactionData1")));
    						props.put("i" + c + "date", date);
    						int fstageID = rs.getInt("firstStageID");
    						
    						int stageID = fstageID; 
    						boolean first = false;
    						String item = ""; 
    						String itemCount = "";
    						String items="";
    						BuildObjectStage bos = null; 
    						int laststage=-1;
    						while (stageID > 0) {
    							if (stages.containsKey(stageID)) {
    								if(!first) {
    									for(Integer i : stages.get(stageID).getItemReqs().keySet()) {
    										item += i+";";
    										itemCount += stages.get(stageID).getItemReqs().get(i)+";";
    									}
    									first = true;
    								}else {
    									for(Integer i : stages.get(stageID).getItemReqs().keySet()) {
    										items += i+";";
    									}
    								}
    								if(Log.loggingDebug)
    									Log.debug("loadBuildObjectPrefabData id="+id+" stageID="+stageID);
    								bos = stages.get(stageID);
    								laststage=stageID;
    								stageID = stages.get(stageID).getNextStageID();
    							} else {
    								stageID = -1;
    							}	
    						}
    						if (item.length() > 0)
    							item = item.substring(0, item.length() - 1);
    						if (itemCount.length() > 0)
    							itemCount = itemCount.substring(0, itemCount.length() - 1);
    						if (items.length() > 0)
    							items = items.substring(0, items.length() - 1);
    						props.put("i" + c + "reqitem", item);
    						props.put("i" + c + "reqitemC", itemCount);
    						props.put("i" + c + "upditems", items);
    						if(Log.loggingDebug)
    							Log.debug("loadBuildObjectPrefabData id="+id+" stageID="+laststage+" fstageID="+fstageID+" containsKey="+stages.containsKey(fstageID)+" last BuildObjectStage="+bos);
    						props.put("i" + c + "bTime", (int)(stages.get(fstageID).getBuildTimeReq()*1000f));
    						if(bos!=null)
    							props.put("i" + c + "lstage", (bos.getProgressGameObject().containsKey(100)?bos.getProgressGameObject().get(100):""));// bos.getGameObject());
    						else
    							props.put("i" + c + "lstage", "");
    						c++;
    					}
    					/*if(c > 20) {
    						props.put("all", false);
    						props.put("num",c);
    						Log.error("loadBuildObjectPrefabData send part message");
    						TargetedExtensionMessage emsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
    						Engine.getAgent().sendBroadcast(emsg);
    						 try {
    							Thread.sleep(200);
    						} catch (InterruptedException e) {
    							// TODO Auto-generated catch block
    						//	e.printStackTrace();
    						}
    						props.clear();
    						props.put("ext_msg_subtype", "BuildObjPrefabData");
    						c=0;	
    					}*/
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
			if(Log.loggingDebug)
			Log.debug("loadBuildObjectPrefabData "+props);
		} catch (SQLException e) {
			Log.exception("loadBuildObjectPrefabData",e);
			e.printStackTrace();
		}
		return props;
	}
	/**
	 * Loading Claim Profile definition
	 * @return
	 */
	public HashMap<Integer, ClaimProfile> loadClaimProfiles() {
		// Load in build stages first
		HashMap<Integer, ClaimProfile> list = new HashMap<Integer, ClaimProfile>();

	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `claim_profile` where isactive = 1")) {
			if(Log.loggingDebug)
				Log.debug("BUILD Limits: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					ClaimProfile cp = new ClaimProfile();
    					int id = rs.getInt("id");
    					String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					cp.setId(id);
    					cp.setName(name);
    					list.put(id, cp);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		for (ClaimProfile _cp : list.values()) {
			loadClaimProfileLimits(_cp);
		}
		return list;
	}

	/**
	 * Loading claim object category limits for claim profile
	 * 
	 * @param profile
	 */
	public void loadClaimProfileLimits(ClaimProfile profile) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `build_object_limits` where isactive = 1 and profile_id = " + profile.getId())) {
			if(Log.loggingDebug)
				Log.debug("BUILD Limits: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    
    					int category = rs.getInt("object_category");
    					int limit = rs.getInt("count");
    					profile.addLimit(category, limit);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Building System Database Functions
	 */

	public HashMap<Integer, BuildObjectTemplate> loadBuildObjectTemplates() {
		// Load in build stages first
		HashMap<Integer, BuildObjectStage> stages = loadBuildObjectStages();
		HashMap<Integer, BuildObjectTemplate> grids = new HashMap<Integer, BuildObjectTemplate>();
		
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `build_object_template` where isactive = 1")) {
			if(Log.loggingDebug)
				Log.debug("BUILD: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					BuildObjectTemplate bg = new BuildObjectTemplate(rs.getInt("id"), HelperFunctions.readEncodedString(rs.getBytes("name")),
    							rs.getInt("skill"), rs.getInt("skillLevelReq"), HelperFunctions.readEncodedString(rs.getBytes("weaponReq")),
    							rs.getFloat("distanceReq"), rs.getBoolean("buildTaskReqPlayer"));
    					bg.buildTaskSolo(rs.getBoolean("buildSolo"));
    					bg.buildTaskFixedTime(rs.getBoolean("fixedTime"));
    					bg.setClaimObjectCategory(rs.getInt("claim_object_category"));
    					
    					bg.setInteractionType(HelperFunctions.readEncodedString(rs.getBytes("interactionType")));
    					bg.setInteractionID(rs.getInt("interactionID"));
    					bg.setInteractionData1(HelperFunctions.readEncodedString(rs.getBytes("interactionData1")));
    					bg.setLockable(rs.getBoolean("lockable"));
    					bg.setLockLimit(rs.getInt("lockLimit"));
    					bg.setAttackable(rs.getBoolean("attackable"));
    					bg.setRepairable(rs.getBoolean("repairable"));
    					
    					String claimType =HelperFunctions.readEncodedString(rs.getBytes("validClaimType"));
    					if(claimType.length()>0) {
    						String[] ids = claimType.split(";");
    						for(String s : ids) {
    							bg.addValidClaimType(Integer.parseInt(s));
    						}
    						
    					}
    					
    					//bg.setValidClaimType(rs.getInt("validClaimType"));
    					// Set stages
    					int stageID = rs.getInt("firstStageID");
    					while (stageID > 0) {
    						if (stages.containsKey(stageID)) {
    							bg.addStage(stages.get(stageID));
    							stageID = stages.get(stageID).getNextStageID();
    						} else {
    							stageID = -1;
    						}	
    					}
    					
    					// Load in items and get their health property and add to the total health for the object
    					int health = 0;
    					for (int i = 1; i < bg.getStages().size(); i++) {
    						health += bg.getStage(i).getHealth();
    					}
    					bg.setMaxHealth(health);
    					grids.put(bg.getId(), bg);
    					//Log.debug("GRID: added grid " + bg.getID() + " to map");
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return grids;
	}
	
	public HashMap<Integer, BuildObjectStage> loadBuildObjectStages() {
		HashMap<Integer, BuildObjectStage> stages = new HashMap<Integer, BuildObjectStage>();
		
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `build_object_stage` where isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int health = 0;
    					HashMap<Integer, Integer> itemReqs = new HashMap<Integer, Integer>();
    					/*for (int i = 1; i <= 6; i++) {
    						Integer itemID = rs.getInt("itemReq" + i);
    						Integer itemCount = rs.getInt("itemReq" + i + "Count");
    						if (itemID != null && itemID > 0 && itemCount != null && itemCount > 0) {
    							itemReqs.put(itemID, itemCount);
    							health += getItemHealthValue(itemID) * itemCount;
    						}
    					}*/
    					BuildObjectStage bg = new BuildObjectStage(HelperFunctions.readEncodedString(rs.getBytes("gameObject")),
    							rs.getFloat("buildTimeReq"), itemReqs, health, rs.getInt("nextStage"));
    					bg.setInteractionType(HelperFunctions.readEncodedString(rs.getBytes("interactionType")));
    					bg.setInteractionID(rs.getInt("interactionID"));
    					bg.setInteractionData1(HelperFunctions.readEncodedString(rs.getBytes("interactionData1")));
    					bg.setHealth(rs.getInt("health"));
    					bg.setLootTable(rs.getInt("lootTable"));
    					bg.setLootMinPercentage(rs.getFloat("lootMinPercentage"));
    					bg.setLootMaxPercentage(rs.getFloat("lootMaxPercentage"));
    					bg.setRepairTimeReq(rs.getFloat("repairTimeReq"));
    						// Load in items and get their health property and add to the total health for the object
    					stages.put(rs.getInt("id"), bg);
    					
    					//Log.debug("GRID: added grid " + bg.getID() + " to map");
    				}
    			}
	        }
			
			for(Integer id : stages.keySet()) {
				stages.get(id).setDamagedGameObject(getDamagedStage(id));
				stages.get(id).setProgressGameObject(getProgresStage(id));
				stages.get(id).setDamagedColliders(getDamagedStageColliders(id));
				stages.get(id).setProgressColliders(getProgresStageColliders(id));
				stages.get(id).setItemReqs(getReqItemsStage(id));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return stages;
	}

	public LinkedHashMap<Integer, String> getProgresStage(int stageId) {
		LinkedHashMap<Integer, String> list = new LinkedHashMap<Integer, String>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM build_object_stage_progress where stage_id = " + stageId + " ORDER BY progress ASC")) {
			if(Log.loggingDebug)
					Log.debug("BUILD: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String prefab = HelperFunctions.readEncodedString(rs.getBytes("prefab"));
    					Integer id = rs.getInt("id");
    					Integer progress = rs.getInt("progress");
    					if(Log.loggingDebug)
    						Log.debug("getProgresStage stage="+stageId+" id="+id+" go="+progress);
    					list.put(progress, prefab);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.exception("getProgresStage",e);
			e.printStackTrace();
		}
		if(Log.loggingDebug)
			Log.debug("stageId="+stageId+" getProgresStage "+list.toString());
		return list;
	}
	
	public LinkedHashMap<Integer, AtavismBuildingColliders> getProgresStageColliders(int stageId) {
		LinkedHashMap<Integer, AtavismBuildingColliders> list = new LinkedHashMap<Integer, AtavismBuildingColliders>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM build_object_stage_progress where stage_id = " + stageId + " ORDER BY progress ASC")) {
			if(Log.loggingDebug)
				Log.debug("BUILD: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					try {
    						String trimesh = HelperFunctions.readEncodedString(rs.getBytes("trimesh"));
    						
    						Integer id = rs.getInt("id");
    						Integer progress = rs.getInt("progress");
    						if(trimesh!=null) {
    						byte[] xmlByte = trimesh.getBytes();
    						AtavismBuildingColliders abc = new AtavismBuildingColliders();
    						
    						ByteArrayInputStream input = new ByteArrayInputStream(xmlByte);
    						DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
    						  DocumentBuilder bu = fac.newDocumentBuilder();
    							Document d2 =  bu.parse(input);
    							d2.getDocumentElement().normalize();
    							List<AtavismCollider> acList = new ArrayList<AtavismCollider>();
    //				        System.out.println("Root element: " + d2.getDocumentElement().getNodeName());
    						    NodeList nList = d2.getElementsByTagName("AtavismCollider");
    						    for (int i = 0; i < nList.getLength(); i++) {
    						        Node nNode = nList.item(i);
    //				            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" "+nNode.getNodeType());
    						        AtavismCollider ac = new AtavismCollider();
    						        Element elem = (Element) nNode;
    						        Node ntype = elem.getElementsByTagName("type").item(0);
    //				            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" t:"+ntype);
    						        String type = ntype.getTextContent();
    						        ac.type = type;
    //				            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" type="+type);
    						        Node nposition = elem.getElementsByTagName("position").item(0);
    //							System.out.println("\nCurrent Element: " + nNode.getNodeName()+" nposition="+nposition.getNodeName()+" "+nposition.getNodeType());
    						        Element epos = (Element) nposition;
    						        Node posx = epos.getElementsByTagName("x").item(0); 
    						        String sposx = posx.getTextContent();
    						        float px = Float.parseFloat(sposx);
    						        Node posy = epos.getElementsByTagName("y").item(0);
    						        String sposy = posy.getTextContent();
    						        float py = Float.parseFloat(sposy);
    						        Node posz = epos.getElementsByTagName("z").item(0);
    						        String sposz = posz.getTextContent();
    						        float pz = Float.parseFloat(sposz);
    //					        System.out.println("\nCurrent Element: " + nNode.getNodeName()+" nposition="+nposition.getNodeName()+" x="+sposx+" y="+sposy+" z="+sposz);
    						        AOVector paov =new AOVector(px,py,pz);
    						        ac.position = paov;
    						        Node nlhalfEdges = elem.getElementsByTagName("halfEdges").item(0);
    //				            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" nlhalfEdges="+nlhalfEdges.getNodeName()+" "+nlhalfEdges.getNodeType());
    						        Element ehe = (Element) nlhalfEdges;
    						        NodeList nListHE = ehe.getElementsByTagName("AOVector");
    						        for (int j = 0; j < nListHE.getLength(); j++) {
    						            Node nNodeHE = nListHE.item(j);
    						            Element elemHE = (Element) nNodeHE;
    						            Node heposx = elemHE.getElementsByTagName("x").item(0); 
    						            String sheposx = heposx.getTextContent();
    						            float x = Float.parseFloat(sheposx);
    						            Node heposy = elemHE.getElementsByTagName("y").item(0);
    						            String sheposy = heposy.getTextContent();
    						            float y = Float.parseFloat(sheposy);
    						            Node heposz = elemHE.getElementsByTagName("z").item(0);
    						            String sheposz = heposz.getTextContent();
    						            float z = Float.parseFloat(sheposz);
    //					            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" "+ nNodeHE.getNodeName()+" halfEdges vector x="+sheposx+" y="+sheposy+" z="+sheposz);
    						            AOVector aov =new AOVector(x,y,z);
    						            ac.halfEdges.add(aov);
    						        }
    						        Node nradius = elem.getElementsByTagName("radius").item(0);
    						        String sradius = nradius.getTextContent();
    //				            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" sradius="+sradius);
    						        float radius = Float.parseFloat(sradius);
    						        ac.radius = radius;
    //				            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" radius="+radius);
    						        acList.add(ac);      
    						    }
    						    abc.colliders =acList;
    						    if(Log.loggingDebug)  Log.debug("getProgresStageColliders: abc="+abc);
    						    list.put(progress, abc);
    						}else {
    								Log.warn("Building Stage Progress: Colliders are not saved in database for stage "+id);
    						}
    					} catch (NumberFormatException | DOMException | ParserConfigurationException | SAXException | IOException e) {
    						Log.debug("getProgresStageColliders: " + e);
    						// TODO Auto-generated catch block
    						e.printStackTrace();
    					}
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return list;
	}

	public LinkedHashMap<Integer, String> getDamagedStage(int stageId) {
		LinkedHashMap<Integer, String> list = new LinkedHashMap<Integer, String>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM build_object_stage_damaged where stage_id = " + stageId+" ORDER BY progress ASC")) {
			if(Log.loggingDebug)
				Log.debug("BUILD: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String prefab = HelperFunctions.readEncodedString(rs.getBytes("prefab"));
    					Integer progress = rs.getInt("progress");
    					list.put(progress, prefab);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return list;
	}

	public LinkedHashMap<Integer, AtavismBuildingColliders> getDamagedStageColliders(int stageId) {
		LinkedHashMap<Integer, AtavismBuildingColliders> list = new LinkedHashMap<Integer, AtavismBuildingColliders>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM build_object_stage_damaged where stage_id = " + stageId+" ORDER BY progress ASC")) {
			if(Log.loggingDebug)
				Log.debug("BUILD: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					try {
    						String trimesh = HelperFunctions.readEncodedString(rs.getBytes("trimesh"));
    						Integer id = rs.getInt("id");
    						Integer progress = rs.getInt("progress");
    						if(trimesh!=null) {
    						byte[] xmlByte = trimesh.getBytes();
    						AtavismBuildingColliders abc = new AtavismBuildingColliders();
    						
    						ByteArrayInputStream input = new ByteArrayInputStream(xmlByte);
    						DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
    						  DocumentBuilder bu = fac.newDocumentBuilder();
    							Document d2 =  bu.parse(input);
    							d2.getDocumentElement().normalize();
    							List<AtavismCollider> acList = new ArrayList<AtavismCollider>();
    //				        System.out.println("Root element: " + d2.getDocumentElement().getNodeName());
    						    NodeList nList = d2.getElementsByTagName("AtavismCollider");
    						    for (int i = 0; i < nList.getLength(); i++) {
    						        Node nNode = nList.item(i);
    //				            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" "+nNode.getNodeType());
    						        AtavismCollider ac = new AtavismCollider();
    						        Element elem = (Element) nNode;
    						        Node ntype = elem.getElementsByTagName("type").item(0);
    //				            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" t:"+ntype);
    						        String type = ntype.getTextContent();
    						        ac.type = type;
    //				            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" type="+type);
    						        Node nposition = elem.getElementsByTagName("position").item(0);
    //							System.out.println("\nCurrent Element: " + nNode.getNodeName()+" nposition="+nposition.getNodeName()+" "+nposition.getNodeType());
    						        Element epos = (Element) nposition;
    						        Node posx = epos.getElementsByTagName("x").item(0); 
    						        String sposx = posx.getTextContent();
    						        float px = Float.parseFloat(sposx);
    						        Node posy = epos.getElementsByTagName("y").item(0);
    						        String sposy = posy.getTextContent();
    						        float py = Float.parseFloat(sposy);
    						        Node posz = epos.getElementsByTagName("z").item(0);
    						        String sposz = posz.getTextContent();
    						        float pz = Float.parseFloat(sposz);
    //					        System.out.println("\nCurrent Element: " + nNode.getNodeName()+" nposition="+nposition.getNodeName()+" x="+sposx+" y="+sposy+" z="+sposz);
    						        AOVector paov =new AOVector(px,py,pz);
    						        ac.position = paov;
    						        Node nlhalfEdges = elem.getElementsByTagName("halfEdges").item(0);
    //				            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" nlhalfEdges="+nlhalfEdges.getNodeName()+" "+nlhalfEdges.getNodeType());
    						        Element ehe = (Element) nlhalfEdges;
    						        NodeList nListHE = ehe.getElementsByTagName("AOVector");
    						        for (int j = 0; j < nListHE.getLength(); j++) {
    						            Node nNodeHE = nListHE.item(j);
    						            Element elemHE = (Element) nNodeHE;
    						            Node heposx = elemHE.getElementsByTagName("x").item(0); 
    						            String sheposx = heposx.getTextContent();
    						            float x = Float.parseFloat(sheposx);
    						            Node heposy = elemHE.getElementsByTagName("y").item(0);
    						            String sheposy = heposy.getTextContent();
    						            float y = Float.parseFloat(sheposy);
    						            Node heposz = elemHE.getElementsByTagName("z").item(0);
    						            String sheposz = heposz.getTextContent();
    						            float z = Float.parseFloat(sheposz);
    //					            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" "+ nNodeHE.getNodeName()+" halfEdges vector x="+sheposx+" y="+sheposy+" z="+sheposz);
    						            AOVector aov =new AOVector(x,y,z);
    						            ac.halfEdges.add(aov);
    						        }
    						        Node nradius = elem.getElementsByTagName("radius").item(0);
    						        String sradius = nradius.getTextContent();
    //				            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" sradius="+sradius);
    						        float radius = Float.parseFloat(sradius);
    						        ac.radius = radius;
    //				            System.out.println("\nCurrent Element: " + nNode.getNodeName()+" radius="+radius);
    						        acList.add(ac);      
    						    }
    						    abc.colliders =acList;
    							if(Log.loggingDebug)
    								 Log.debug("getProgresStageColliders: abc="+abc);
    						list.put(progress, abc);
    					}else {
    							Log.error("Building Stage Damage: Colliders are not saved id database for stage "+id);
    						}
    					} catch (NumberFormatException | DOMException | ParserConfigurationException | SAXException | IOException e) {
    						Log.debug("getProgresStageColliders: " + e);
    						// TODO Auto-generated catch block
    						e.printStackTrace();
    					} 
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return list;
	}

	public HashMap<Integer, Integer> getReqItemsStage(int stageId) {
		HashMap<Integer, Integer> list = new HashMap<Integer, Integer>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM build_object_stage_items where stage_id = " + stageId)) {
			if(Log.loggingDebug)
				Log.debug("BUILD: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Integer item = rs.getInt("item");
    					Integer count = rs.getInt("count");
    					list.put(item, count);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		if(Log.loggingDebug)
			Log.debug("getReqItemsStage "+list);
		return list;
	}
	
	
	public int getItemHealthValue(int itemID) {
		try (PreparedStatement ps = queries.prepare("SELECT * FROM " + ItemDatabase.ITEM_TABLE + " where id = " + itemID)) {
			if(Log.loggingDebug)
					Log.debug("BUILD: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					for (int i = 1; i <= 12; i++) {
    						String itemEffectType = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "type"));
    						if (itemEffectType != null && itemEffectType.equals("BuildingMaterial")) {
    							String itemEffectValue = HelperFunctions.readEncodedString(rs.getBytes("effect" + i + "value"));
    							if (itemEffectValue != null && !itemEffectValue.equals("")) {
    								return Integer.parseInt(itemEffectValue);
    							}
    						}
    						
    					}
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return 0;
	}
	
	/**
	 * Having too many connection errors, so adding this function to help cope with it
	 */
	public void close() {
		Log.dumpStack("ContentDatabase.Close");
		queries.close();
	}
	
	HashMap<Integer, EditorOptionMapping> editor_options = new HashMap<Integer, EditorOptionMapping>();
}