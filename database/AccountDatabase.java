package atavism.agis.database;

import java.io.Serializable;
//import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.PreparedStatement;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;

import atavism.agis.core.Cooldown;
import atavism.agis.objects.*;
import atavism.agis.plugins.AgisInventoryPlugin;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.plugins.VoxelPlugin;
import atavism.agis.util.HelperFunctions;
import atavism.server.engine.Engine;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.objects.*;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.util.*;
import java.util.stream.Collectors;

public class AccountDatabase {

    private static AdminQueries queries;

	public AccountDatabase(boolean keepAlive) {
        if (queries == null) {
            synchronized (AccountDatabase.class) {
                if (queries == null) {
                    queries = new AdminQueries(keepAlive);
                }
            }
        }
	}

	public void deleteOldShopSpawnData() {
		try {
			String updateString = "DELETE FROM `shop_spawn_data` WHERE shop_oid not in (SELECT shop_oid FROM `player_shop`)";
			if(Log.loggingDebug)Log.debug("Delete deleteOldShopSpawnData sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
	}
	
	
	public Map<Integer, HashMap<Integer, SpawnData>> loadInstanceShopSpawnData(Collection<Integer> instanceIDs) {
	    deleteOldShopSpawnData();
	    Map<Integer, HashMap<Integer, SpawnData>> shopSpawnDataMap = new HashMap<>();
	    if (instanceIDs == null || instanceIDs.isEmpty()) {
	        return shopSpawnDataMap;
	    }

	    String placeholders = instanceIDs.stream().map(id -> "?").collect(Collectors.joining(","));
	    String sql = "SELECT * FROM shop_spawn_data WHERE instance IN (" + placeholders + ") AND isactive = 1";

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
	                    SpawnData sd = MobDatabase.readSpawnData(rs);
	                    if (sd != null) {
	                        HashMap<Integer, SpawnData> spawnDataList = shopSpawnDataMap.computeIfAbsent(instanceID, k -> new HashMap<>());
	                        spawnDataList.put(id, sd);
	                    }
	                }
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	        Log.dumpStack("loadInstanceShopSpawnData: SQLException" + e);
	    }
	    return shopSpawnDataMap;
	}

	
	public HashMap<Integer, SpawnData> loadInstanceShopSpawnData(int instanceID) {
		deleteOldShopSpawnData();
		HashMap<Integer, SpawnData> list = new HashMap<Integer, SpawnData>();
		try {
			if(Log.loggingDebug)	Log.debug("DB: loading shop spawnData from instance: " + instanceID);
			try (PreparedStatement ps = queries.prepare("SELECT * FROM shop_spawn_data where instance = " + instanceID + " AND isactive = 1")) {
    			try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					SpawnData sd = MobDatabase.readSpawnData(rs);
        					if (sd != null)
        						list.put(rs.getInt("id"), sd);
        				}
        			}
    			}
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadInstanceShopSpawnData: SQLException"+e);
		}
		return list;
	}
	
	public Map<Integer, Integer> getCountShops(Collection<Integer> instanceIDs) {
	    Map<Integer, Integer> shopCounts = new HashMap<>();
	    if (instanceIDs == null || instanceIDs.isEmpty()) {
	        return shopCounts;
	    }

	    String placeholders = instanceIDs.stream().map(id -> "?").collect(Collectors.joining(","));
	    String sql = "SELECT instance, COUNT(*) as shopCount FROM shop_spawn_data WHERE instance IN (" + placeholders + ") AND isactive = 1 GROUP BY instance";

	    try (PreparedStatement ps = queries.prepare(sql)) {
	        int index = 1;
	        for (Integer id : instanceIDs) {
	            ps.setInt(index++, id);
	        }
	        try (ResultSet rs = queries.executeSelect(ps)) {
	            while (rs.next()) {
	                int instanceID = rs.getInt("instance");
	                int count = rs.getInt("shopCount");
	                shopCounts.put(instanceID, count);
	            }
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	        Log.dumpStack("SQLException in getCountShops: " + e);
	    }
	    return shopCounts;
	}

	
	
	/**
	 * Get count of the shops in the instance
	 * @param instanceID
	 * @return
	 */
	public int getCountShops(int instanceID) {
		int spawnCount = 0;
		Log.debug("DB: get count of the shops from instance: " + instanceID);
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM shop_spawn_data where instance = " + instanceID + " AND isactive = 1")) {
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
	
	
	public int getShopSpawnDataId(OID storeOid) {
		String sql = "SELECT id, instance FROM `shop_spawn_data` WHERE shop_oid = " + storeOid.toLong();
		try (PreparedStatement ps = queries.prepare(sql)) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return rs.getInt("id");
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("getPlayerStore: SQLException: " + e.getLocalizedMessage());
		}
		return 0;
	}
	
	public void deleteShopSpawnData(OID storeOid) {
		try {
			String updateString = "DELETE FROM `shop_spawn_data` WHERE shop_oid = "+storeOid.toLong();
			if(Log.loggingDebug)Log.debug("Delete deleteShopSpawnData sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
	}
	
	public int writeShopSpawnData(SpawnData sd, Point loc, Quaternion orient, BehaviorTemplate behavTmpl, int instanceID) {
		Log.debug("Writing shop spawn data to database");
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
        String tableName = "shop_spawn_data";
        String columnNames = "category,name,mobTemplate,markerName,locX,locY,locZ,orientX,orientY,orientZ,orientW,instance,numSpawns," + 
        "spawnRadius,respawnTime,corpseDespawnTime,spawnActiveStartHour,spawnActiveEndHour,alternateSpawnMobTemplate,combat,roamRadius,startsQuests,"
        + "endsQuests,startsDialogues,otherActions,baseAction,weaponSheathed,merchantTable,patrolPath,questOpenLootTable,isChest,pickupItem"
        +",mobTemplate2,mobTemplate3,mobTemplate4,mobTemplate5,alternateSpawnMobTemplate2,alternateSpawnMobTemplate3,alternateSpawnMobTemplate4,alternateSpawnMobTemplate5,respawnTimeMax"
        +", shop_oid,shop_owner";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames 
				+ ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?,?,?,?,?,?,?,?,?)")) {
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
			
			stmt.setLong(42, ((OID)sd.getProperty("shopOid")).toLong());
			stmt.setLong(43, ((OID)sd.getProperty("shopOwner")).toLong());
			
			
			if(Log.loggingDebug)Log.debug("Spawn Data statement = " + stmt.toString());
			inserted = queries.executeInsert(stmt);
			if(Log.loggingDebug)Log.debug("Spawn Data statement inserted = " + inserted);
			} catch (SQLException e) {
			Log.error("Failed to write spawn data to database with id= " + inserted);
			Log.dumpStack(" SQLException"+e);
			return -1;
		}
		if(Log.loggingDebug)Log.debug("Wrote spawn data to database with id= " + inserted);
		return inserted;
	}

	/**
	 * Function to load shop data for player
	 * @param playerOid
	 * @param storeOid
	 * @return
	 */
	public PlayerShop getPlayerShop(OID playerOid, OID storeOid) {
		String sql = "SELECT * FROM `player_shop` WHERE player_oid = " + playerOid.toLong()+" AND shop_oid = " + storeOid.toLong();
			
		try (PreparedStatement ps = queries.prepare(sql)) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					PlayerShop _ps = new PlayerShop();
    					_ps.setId(rs.getLong("id"));
    					_ps.setCreateTime(rs.getLong("createtime"));
    					_ps.setShopOid(OID.fromLong(rs.getLong("shop_oid")));
    					_ps.setOwnerOid(OID.fromLong(rs.getLong("player_oid")));
    					_ps.setTimeout(rs.getInt("timeout"));
    					_ps.setTag(HelperFunctions.readEncodedString(rs.getBytes("tag")));
    					_ps.setTitle(HelperFunctions.readEncodedString(rs.getBytes("title")));
    					_ps.setEndPlayerOnLogout(rs.getBoolean("end_player_logout"));
    					_ps.setPlayer(rs.getBoolean("player"));
    					return _ps;
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("getPlayerStore: SQLException: " + e.getLocalizedMessage());
		}
		return null;
	}

	/**
	 * Function to load shop
 	 * @param storeOid
	 * @return
	 */
	public PlayerShop getPlayerShop( OID storeOid) {
		String sql = "SELECT * FROM `player_shop` WHERE  shop_oid = " + storeOid.toLong();
			
		try (PreparedStatement ps = queries.prepare(sql)) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					PlayerShop _ps = new PlayerShop();
    					_ps.setId(rs.getLong("id"));
    					_ps.setCreateTime(rs.getLong("createtime"));
    					_ps.setShopOid(OID.fromLong(rs.getLong("shop_oid")));
    					_ps.setOwnerOid(OID.fromLong(rs.getLong("player_oid")));
    					_ps.setTimeout(rs.getInt("timeout"));
    					_ps.setTag(HelperFunctions.readEncodedString(rs.getBytes("tag")));
    					_ps.setTitle(HelperFunctions.readEncodedString(rs.getBytes("title")));
    					_ps.setEndPlayerOnLogout(rs.getBoolean("end_player_logout"));
    					_ps.setPlayer(rs.getBoolean("player"));		
    					return _ps;
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("getPlayerStore: SQLException: " + e.getLocalizedMessage());
		}
		return null;
	}

	/**
	 * Function to load all shops for player
	 * @param playerOid
	 * @return
	 */
	public ArrayList<PlayerShop> getAllPlayerShop(OID playerOid) {
		ArrayList<PlayerShop> list = new ArrayList<PlayerShop>();
		String sql = "SELECT * FROM `player_shop` WHERE player_oid = " + playerOid.toLong();
		if(Log.loggingDebug)Log.debug("getPlayerStore: sql="+sql);
		try (PreparedStatement ps = queries.prepare(sql)) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					if(Log.loggingDebug)Log.debug("getPlayerStore: "+rs.getLong("id"));
    					PlayerShop _ps = new PlayerShop();
    					_ps.setId(rs.getLong("id"));
    					_ps.setCreateTime(rs.getLong("createtime"));
    					_ps.setShopOid(OID.fromLong(rs.getLong("shop_oid")));
    					_ps.setOwnerOid(OID.fromLong(rs.getLong("player_oid")));
    					_ps.setTimeout(rs.getInt("timeout"));
    					_ps.setTag(HelperFunctions.readEncodedString(rs.getBytes("tag")));
    					_ps.setTitle(HelperFunctions.readEncodedString(rs.getBytes("title")));
    					_ps.setEndPlayerOnLogout(rs.getBoolean("end_player_logout"));
    					_ps.setPlayer(rs.getBoolean("player"));
    						list.add(_ps);	
    					
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("getPlayerStore: SQLException: " + e.getLocalizedMessage());
		}
		if(Log.loggingDebug)Log.debug("getPlayerStore: End "+list.size());
		return list;
	}

	/**
	 * Function to retrieve count of shops for player with tag
	 * @param playerOid
	 * @param tag
	 * @return
	 */
	public int countPlayerStore(OID playerOid, String tag) {
		String sql = "SELECT count(*) as count FROM `player_shop` WHERE player_oid = " + playerOid.toLong();
		if(!tag.isEmpty())
			sql+= " AND tag like '" + tag + "'";
        try (PreparedStatement ps = queries.prepare(sql)) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return rs.getInt(1);
    				}
    			}
            }
		} catch (SQLException e) {
			Log.error("getPlayerStore: SQLException: " + e.getLocalizedMessage());
		}
		return 0;
	}

	/**
	 * Function to check if player is owner of shop
	 * @param storeOid
	 * @param playerOid
	 * @return
	 */
	public boolean isPlayerStore(OID storeOid, OID playerOid) {
		try {
			try (PreparedStatement ps = queries.prepare("SELECT * FROM `player_shop` WHERE shop_oid = " + storeOid.toLong() + " AND player_oid = " + playerOid.toLong())) {
                try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					return true;
        				}
        			}
                }
			}
		} catch (SQLException e) {
			Log.error("getPlayerStore: SQLException: " + e.getLocalizedMessage());
		}
		return false;
	}
	
	public boolean isPlayerStore(OID storeOid) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `player_shop` WHERE shop_oid = " + storeOid.toLong())) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return true;
    				}
    			}
            }
		} catch (SQLException e) {
			Log.error("getPlayerStore: SQLException: " + e.getLocalizedMessage());
		}
		return false;
	}


	public ArrayList<PlayerShopItem> getPlayerStore(OID storeOid) {
		Log.debug("getPlayerStore: start");
		ArrayList<PlayerShopItem> list = new ArrayList<PlayerShopItem>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `player_shop_items` WHERE shop_oid = "+storeOid.toLong()+"")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					OID oid = OID.fromLong(rs.getLong("item_oid"));
    					if(Log.loggingDebug)Log.debug("getPlayerStore: Item "+oid+" "+rs.getLong("id"));
    					list.add(new PlayerShopItem(rs.getLong("id"),oid,rs.getInt("currency"),rs.getLong("price"),rs.getInt("template_id"),rs.getInt("count"), rs.getBoolean("sell")));
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("getPlayerStore: SQLException: "+e.getLocalizedMessage());
		}

		return list;
	}

	/**
	 * Function to update item count in shop
	 * @param storeOid
	 * @param id
	 * @param count
	 */
	public void SavePlayerShopItem(OID storeOid, long id, int count) {
		if(Log.loggingDebug)	Log.debug("SavePlayerShopItem: time: " + System.currentTimeMillis());
		try {
			String updateString = "UPDATE `player_shop_items` SET count = " + count +"  WHERE id =" +id+" AND shop_oid="+storeOid.toLong();
			if(Log.loggingDebug)Log.debug("SavePlayerShopItem sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
	}


	/**
	 * Function to delete shop
	 * @param storeOid
	 */
	public void deletePlayerStore(OID storeOid) {
		try {
			String sql = "DELETE FROM `player_shop_items` WHERE shop_oid = " + storeOid.toLong();
			if(Log.loggingDebug)	Log.debug("Delete deletePlayerStore sql: " + sql);
			queries.executeUpdate(sql);
			sql = "DELETE FROM `player_shop` WHERE shop_oid = " + storeOid.toLong();
			if(Log.loggingDebug)Log.debug("Delete deletePlayerStore sql: " + sql);
			queries.executeUpdate(sql);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
	}

	/**
	 * Function to delete item form shop
	 * @param storeOid
	 * @param itemId
	 */
	public void deletePlayerShopItem(OID storeOid, long itemId) {
		try {
			String updateString = "DELETE FROM `player_shop_items` WHERE shop_oid = "+storeOid.toLong()+" AND id = "+itemId;
			if(Log.loggingDebug)Log.debug("Delete deletePlayerShopItem sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
	}
	
	/*public void saveEditPlayerStore(OID storeOid, OID playerOid, ArrayList<PlayerShopItem> items) {
		
		
	}*/
	
	public void saveNewPlayerStore(OID storeOid, OID playerOid, ArrayList<PlayerShopItem> items,String tag, String title, boolean end_player_logout,int timeout, boolean player) {
		if(Log.loggingDebug)	Log.debug("AccountDatabase: savePlayerStore storeOid:"+storeOid+" playerOid:"+playerOid+" items:"+items.size());
		
			try {
				String sql = "INSERT INTO `player_shop_items` (shop_oid, item_oid, currency, price, template_id, count, sell) values ";
				int sqlLength = sql.length();
				for(PlayerShopItem psi : items) {
					sql += "("+storeOid.toLong()+", "+(psi.getItemOid()!=null?psi.getItemOid().toLong():0L)+", "+psi.getCurrency()+", "+psi.getPrice()+","+psi.getTemplateId()+", "+psi.getCount()+", "+psi.getSell()+"),";
				}
				sql = sql.substring(0, sql.length() - 1);
				if(Log.loggingDebug)Log.debug("AccountDatabase: saveNewPlayerStore sql:"+sql);
				if(sql.length()> sqlLength)
				queries.executeUpdate(sql);
				
				sql = "INSERT INTO `player_shop` (shop_oid, player_oid, tag, title, end_player_logout, timeout, player, createtime) values (?,?,?,?,?,?,?,?)";
				//+storeOid.toLong()+","+playerOid.toLong()+",'"+tag+"','"+title+"',"+end_player_logout+", "+timeout+", "+player+"," + System.currentTimeMillis() + ")";
				PreparedStatement	ps = queries.prepare(sql);
				ps.setLong(1,storeOid.toLong());
				ps.setLong(2,playerOid.toLong());
				ps.setString(3,tag);
				ps.setString(4,title);
				ps.setBoolean(5,end_player_logout);
				ps.setInt(6,timeout);
				ps.setBoolean(7,player);
				ps.setLong(8,System.currentTimeMillis()) ;
				if(Log.loggingDebug)Log.debug("AccountDatabase: saveNewPlayerStore sql:"+ps.toString());
				queries.executeInsert(ps);
				//queries.executeUpdate(sql);
			} catch (SQLException e) {
				Log.exception(e);
			Log.error("savePlayerStore: SQLException: "+e.getLocalizedMessage());
			// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		 finally {
		}
		Log.debug("AccountDatabase: saveNewPlayerStore End");
	}
	
	public long getLastRankingCalculation() {
		Log.debug("getLastRankingCalculation: start");
		try {
			
		    try (PreparedStatement ps = queries.prepare("SELECT * FROM `ranking_run` WHERE world = '"+ Engine.getWorldName()+"'")) {
		        try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					return rs.getLong("last_run");
        				}
        			}
    			}
		    }
			String columnNames = "world,last_run";
			try (PreparedStatement ps = queries.prepare("INSERT INTO ranking_run (" + columnNames + ") values (?, ?)")) {
    			ps.setString(1, Engine.getWorldName());
    			ps.setLong(2, 0l);
    			queries.executeInsert(ps);
    			Log.debug("getLastRankingCalculation: inserted");
			}
			
		} catch (SQLException e) {
			Log.error("getLastRankingCalculation: SQLException: "+e.getLocalizedMessage());
			return 0l;
		}

		return 0l;
	}
	
	
	
	public boolean LogChat(OID source, OID target,int channel,String message) {
		if(Log.loggingDebug)Log.debug("AccountDatabase: log chat source:"+source+" target:"+target+" channel:"+channel+" message:"+message);
		String columnNames = "world,message,source,target,channel";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO chat_logs (" + columnNames + ") values (?, ?, ?, ?, ?)")) {
			stmt.setString(1, Engine.getWorldName());
			stmt.setString(2, message);
			stmt.setLong(3, source!=null?source.toLong():0l);
			stmt.setLong(4, target!=null?target.toLong():0l);
			stmt.setInt(5, channel);
			int s = queries.executeInsert(stmt);
			if(Log.loggingDebug)	Log.debug("AccountDatabase: log chat id:"+s);
			
		} catch (SQLException e) {
			Log.error("ChatLog: SQLException: "+e.getLocalizedMessage());
			return false;
		}

		return true;
		
	}
	
	public void saveLastRankingRun() {
		Log.debug("saveLastRankingRun: time: " + System.currentTimeMillis());
		try {
			String updateString = "UPDATE `ranking_run` SET last_run = " + System.currentTimeMillis() +"  WHERE world ='" +Engine.getWorldName()+"'";
			if(Log.loggingDebug)Log.debug("saveLastRankingRun sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
	}

	/**
	 * Function to save rankings
	 * @param rankings
	 * @return
	 */
	public boolean saveRanking(ArrayList<Ranking> rankings) {
		Log.debug("saveRanking: start");
		if(Log.loggingDebug)Log.debug("saveRanking: rankings="+rankings);
		try {
			String updateString = "DELETE FROM `rankings` ";
			if(Log.loggingDebug)Log.debug("Delete rankings sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
		try {
			String sql = "INSERT INTO rankings (pos,player,ranking,value) values ";
			for (Ranking r : rankings) {
				sql += "(" + r.getPosition() + ", " + r.getSubjectOid().toLong() + ", " + r.getType() + ", " + r.getValue() + "),";
				if (sql.length() > 50000) {
					sql = sql.substring(0, sql.length() - 1);
					if(Log.loggingDebug)Log.debug("saveRanking: sql=" + sql);
					try (PreparedStatement stmt = queries.prepare(sql)) {
    					queries.executeUpdate(stmt);
                    }
					sql = "INSERT INTO rankings (pos,player,ranking,value) values ";
				}
				// Log.debug("AccountDatabase: saveRanking id:"+s);
			}
			if (sql.length() > 60) {
				sql = sql.substring(0, sql.length() - 1);
				if(Log.loggingDebug)	Log.debug("saveRanking: sql=" + sql);
				try (PreparedStatement stmt = queries.prepare(sql)) {
				    queries.executeUpdate(stmt);
				}
			} else {
				if(Log.loggingDebug)Log.debug("saveRanking: sql lenght is to low sql=" + sql);
			}
		} catch (Exception e) {
			Log.error("saveRanking: SQLException: " + e.getLocalizedMessage());
			return false;
		}
		Log.debug("saveRanking: End");

		return true;
		
	}

	/**
	 * Function to Load All Rankings with type
	 * @param type
	 * @return
	 */
	public ArrayList<Ranking> getRanking(int type) {
		if(Log.loggingDebug)	Log.debug("AccountDatabase getRanking type="+type);
		ArrayList<Ranking> rankings = new ArrayList<Ranking>();
		String sql = "SELECT * FROM `rankings` WHERE ranking ="+type+" order by pos";
		if(Log.loggingDebug)Log.debug("getRanking: "+sql);
		try (PreparedStatement ps = queries.prepare(sql)) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Ranking ranking = new Ranking(OID.fromLong(rs.getLong("player")),rs.getInt("ranking"),rs.getInt("pos"),rs.getInt("value"));
    					Log.debug("getRanking "+ranking);
    					rankings.add(ranking);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack(e.getMessage());
		}
		if(Log.loggingDebug)	Log.debug("AccountDatabase getRanking loaded " + rankings.size());

		return rankings;
	}

	/**
	 * Function to Load all collection data
	 * @return
	 */
	public Map<OID, HashMap<Short, ArrayList<CollectionData>>> loadCollectionData() {
		Map<OID, HashMap<Short, ArrayList<CollectionData>>> cd = new ConcurrentHashMap<OID, HashMap<Short, ArrayList<CollectionData>>>();
		Log.debug("loadCollectionData getRanking");
		ArrayList<CollectionData> collection = new ArrayList<CollectionData>();
		String sql = "SELECT * FROM `achivement_data`  ";
		if(Log.loggingDebug)Log.debug("loadCollectionData: sql=" + sql);
		try (PreparedStatement ps = queries.prepare(sql)) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					OID ply = OID.fromLong(rs.getLong("playerOid"));
    					if (!cd.containsKey(ply)) {
    						cd.put(ply, new HashMap<Short, ArrayList<CollectionData>>());
    					}
    					Short type = rs.getShort("type");
    					if (!cd.get(ply).containsKey(type)) {
    						cd.get(ply).put(type, new ArrayList<CollectionData>());
    					}
    					CollectionData c = new CollectionData(ply, type, rs.getInt("value"), HelperFunctions.readEncodedString(rs.getBytes("obj")));
    					c.setId(rs.getInt("id"));
    					c.setAchievementId(rs.getInt("achievementId"));
    					c.setRankingId(rs.getInt("rankingId"));
    					c.setAcquired(rs.getBoolean("acquired"));
    					if(Log.loggingDebug)Log.debug("loadCollectionData " + c);
    					cd.get(ply).get(type).add(c);
    				}
				}
			}
		} catch (SQLException e) {
			Log.dumpStack(e.getMessage());
		}
		if(Log.loggingDebug)Log.debug("AccountDatabase loadCollectionData loaded " + cd.size());

		
		
		return cd;
	}
	
	public ArrayList<CollectionData> getCollectionData(int rankingId, int achievementId,int limit) {
		Log.debug("getCollectionData getRanking");
		if(limit < 0)
			limit = 0;
		ArrayList<CollectionData> collection = new ArrayList<CollectionData>();
		String sql = "SELECT * FROM `achivement_data`  ";
		if (rankingId > 0) {
			sql += "WHERE rankingId  =" + rankingId;
			if (achievementId > 0)
				sql += " AND achievementId =" + achievementId;
		}
		if (achievementId > 0)
			sql += "WHERE achievementId =" + achievementId;
		sql +=" order by value DESC LIMIT "+limit;
		if(Log.loggingDebug)	Log.debug("getCollectionData: sql=" + sql);
		try (PreparedStatement ps = queries.prepare(sql)) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					CollectionData c = new CollectionData(OID.fromLong(rs.getLong("playerOid")), rs.getShort("type"), rs.getInt("value"), HelperFunctions.readEncodedString(rs.getBytes("obj")));
    					c.setId(rs.getInt("id"));
    					c.setAchievementId(rs.getInt("achievementId"));
    					c.setRankingId(rs.getInt("rankingId"));
    					c.setAcquired(rs.getBoolean("acquired"));
    					Log.debug("getCollectionData " + c);
    					collection.add(c);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack(e.getMessage());
		}
		Log.debug("AccountDatabase getCollectionData loaded " + collection.size());

		return collection;
	}

	/**
	 * Function to save all collection data that was changed
	 * @param collection
	 * @return
	 */
	public boolean saveCollectionData(ArrayList<CollectionData> collection) {
		Log.debug("saveCollectionData: start");
		if(Log.loggingDebug)Log.debug("saveRanking: collection="+collection);
	/*	try {
			String updateString = "DELETE FROM `achivement_data` ";
			Log.debug("Delete saveCollectionData sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}*/
		try {
			for(CollectionData r : collection) {
				if(r.getId() == -1 && r.getValue() > 0) {
					String columnNames = "playerOid, type, obj, rankingId, achievementId, acquired, value ";
					try (PreparedStatement stmt = queries.prepare("INSERT INTO achivement_data (" + columnNames + ") values (?, ?, ?, ?, ?, ?, ?)")) {
    					stmt.setLong(1, r.getSubjectOid().toLong());
    					stmt.setInt(2, r.getType());
    					stmt.setString(3, r.getObjects());
    					stmt.setInt(4, r.getRankingId());
    					stmt.setInt(5, r.getAchievementId());
    					stmt.setBoolean(6, r.getAcquired());
    					stmt.setInt(7, r.getValue());
    					int s = queries.executeInsert(stmt);
    					r.setId(s);
                        if(Log.loggingDebug)    Log.debug("AccountDatabase: saveCollectionData inster id:"+s);
					}
				} else if(r.getId() > 0 && r.getValue() == 0 & !r.getAcquired()) {
						String updateString = "DELETE FROM `achivement_data` where id = "+r.getId();
						Log.debug("Delete saveCollectionData sql: " + updateString);
						queries.executeUpdate(updateString);
						r.setId(-1);
				} else {
					if(r.isDirty()) {
						String updateString = "UPDATE `achivement_data` SET acquired = "+r.getAcquired()+", value="+r.getValue()+" ,rankingId ="+r.getRankingId()+", achievementId="+r.getAchievementId()+" WHERE id =" +r.getId();
						if(Log.loggingDebug)	Log.debug("saveCollectionData Update sql: " + updateString);
						queries.executeUpdate(updateString);
					}
				}
				r.setDirty(false);
			
			}
		} catch (SQLException e) {
			Log.error("saveCollectionData: SQLException: "+e.getLocalizedMessage());
			return false;
		}
		Log.debug("saveCollectionData: End");

		return true;
		
	}

	/**
	 * Function to delete all collection data for player
	 * @param playerOid
	 */
	public void deletePlayerCollectionData(OID playerOid) {
		Log.debug("deleteCollectionData: start");
		try {
			String updateString = "DELETE FROM `achivement_data` WHERE playerOid = "+playerOid.toLong();
			if(Log.loggingDebug)	Log.debug("deleteCollectionData: Delete sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
		Log.debug("deleteCollectionData: end  ");

	}

	/**
	 * Function to delete all rankings for player
	 * @param playerOid
	 */
	public void deletePlayerRanking(OID playerOid) {
		Log.debug("deletePlayerRanking: start");
		try {
			String updateString = "DELETE FROM `rankings` WHERE player = "+playerOid.toLong();
			if(Log.loggingDebug)	Log.debug("deletePlayerRanking: Delete sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
		Log.debug("deletePlayerRanking: end  ");

	}

	/**
	 * Function to Load all auctions
	 * @return
	 */
	public ArrayList<Auction> getAuctions() {
		Log.debug("AccountDatabase getAuctions");
		ArrayList<Auction> auctions = new ArrayList<Auction>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `auction_house` WHERE status = 1 ORDER BY expire_date")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Auction auction = new Auction(rs.getInt("id"));
    					auction.SetCurrency(rs.getInt("currency_id"));
    					auction.SetStartBid(rs.getInt("startbid"));
    					auction.SetBid(rs.getLong("bid"));
    					auction.SetBuyout(rs.getLong("buyout"));
    					auction.SetMode(rs.getInt("mode"));
    					auction.SetStatus(rs.getInt("status"));
    					auction.SetAuctioneer((rs.getLong("auctioneer_oid")));
    					auction.SetOwnerOid(OID.fromLong(rs.getLong("owner_oid")));
    					auction.SetBidderOid(OID.fromLong(rs.getLong("bidder_oid")));
    					auction.SetItemOid(OID.fromLong(rs.getLong("item_oid")));
    					auction.SetItemCount(rs.getInt("item_count"));
    					auction.SetItemTemplateId(rs.getInt("item_template_id"));
    					auction.SetItemEnchanteLevel(rs.getInt("item_enchant_level"));
    					HashMap<Integer, SocketInfo> itemSockets = new HashMap<Integer, SocketInfo>();
    					String item_sockets_info = HelperFunctions.readEncodedString(rs.getBytes("item_sockets_info"));
    					String[] sockets_info = item_sockets_info.split(";");
    					for (int i = 0; i < sockets_info.length / 2; i++) {
    						itemSockets.put(i, new SocketInfo(i, sockets_info[i * 2], OID.parseLong(sockets_info[i * 2 + 1])));
    					}
    					auction.SetItemSockets(itemSockets);
    					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    					// LocalDate localDate =
    					// LocalDate.parse(HelperFunctions.readEncodedString(rs.getBytes("expire_date")),
    					// formatter);
    					Date date = rs.getDate("expire_date");
    					Format f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    					String s = f.format(date);
    					if(Log.loggingDebug)	Log.debug("expire_date:" + s);
    					// Date date =
    					// Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    					auction.SetExpirateDate(date);
    					Template itemTemplate = ObjectManagerClient.getTemplate(auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
    					if (itemTemplate == null)
    						Log.error("Template " + auction.GetItemTemplateId() + " is null ");
    					auction.SetItemName(itemTemplate.getName());
    
    					auctions.add(auction);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack(e.getMessage());
		}
		if(Log.loggingDebug)	Log.debug("AccountDatabase getAuctions loaded " + auctions.size());

		return auctions;
	}

	public ArrayList<Auction> getOwnOrderAuctions(OID playerOID) {
		Log.debug("AccountDatabase getOwnOrderAuctions");
		ArrayList<Auction> auctions = new ArrayList<Auction>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `auction_house` WHERE status = 1 AND mode=1 AND owner_oid = " + playerOID.toLong() + " ORDER BY expire_date")) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Auction auction = new Auction(rs.getInt("id"));
    					auction.SetCurrency(rs.getInt("currency_id"));
    					auction.SetStartBid(rs.getInt("startbid"));
    					auction.SetBid(rs.getLong("bid"));
    					auction.SetBuyout(rs.getLong("buyout"));
    					auction.SetMode(rs.getInt("mode"));
    					auction.SetStatus(rs.getInt("status"));
    					auction.SetAuctioneer(rs.getLong("auctioneer_oid"));
    					auction.SetOwnerOid(OID.fromLong(rs.getLong("owner_oid")));
    					auction.SetBidderOid(OID.fromLong(rs.getLong("bidder_oid")));
    					auction.SetItemOid(OID.fromLong(rs.getLong("item_oid")));
    					auction.SetItemCount(rs.getInt("item_count"));
    					auction.SetItemTemplateId(rs.getInt("item_template_id"));
    					auction.SetItemEnchanteLevel(rs.getInt("item_enchant_level"));
    					HashMap<Integer, SocketInfo> itemSockets = new HashMap<Integer, SocketInfo>();
    					String item_sockets_info = HelperFunctions.readEncodedString(rs.getBytes("item_sockets_info"));
    					String[] sockets_info = item_sockets_info.split(";");
    					for (int i = 0; i < sockets_info.length / 2; i++) {
    						itemSockets.put(i, new SocketInfo(i, sockets_info[i * 2], OID.parseLong(sockets_info[i * 2 + 1])));
    					}
    					auction.SetItemSockets(itemSockets);
    					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    					// LocalDate localDate =
    					// LocalDate.parse(HelperFunctions.readEncodedString(rs.getBytes("expire_date")),
    					// formatter);
    					Date date = rs.getDate("expire_date");
    					Format f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    					String s = f.format(date);
    					Log.debug("expire_date:" + s);
    					// Date date =
    					// Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    					auction.SetExpirateDate(date);
    					Template itemTemplate = ObjectManagerClient.getTemplate(auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
    					if (itemTemplate == null)
    						Log.error("Template " + auction.GetItemTemplateId() + " is null ");
    					auction.SetItemName(itemTemplate.getName());
    
    					auctions.add(auction);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack(e.getMessage());
		}
		if(Log.loggingDebug)Log.debug("AccountDatabase getOwnOrderAuctions loaded " + auctions.size());

		return auctions;
	}

	public ArrayList<Auction> getOwnSellAuctions(OID playerOID) {
		Log.debug("AccountDatabase getOwnSellAuctions");
		ArrayList<Auction> auctions = new ArrayList<Auction>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `auction_house` WHERE status = 1 AND mode=0 AND owner_oid = " + playerOID.toLong() + " ORDER BY expire_date")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Auction auction = new Auction(rs.getInt("id"));
    					auction.SetCurrency(rs.getInt("currency_id"));
    					auction.SetStartBid(rs.getInt("startbid"));
    					auction.SetBid(rs.getLong("bid"));
    					auction.SetBuyout(rs.getLong("buyout"));
    					auction.SetMode(rs.getInt("mode"));
    					auction.SetStatus(rs.getInt("status"));
    					auction.SetAuctioneer((rs.getLong("auctioneer_oid")));
    					auction.SetOwnerOid(OID.fromLong(rs.getLong("owner_oid")));
    					auction.SetBidderOid(OID.fromLong(rs.getLong("bidder_oid")));
    					auction.SetItemOid(OID.fromLong(rs.getLong("item_oid")));
    					auction.SetItemCount(rs.getInt("item_count"));
    					auction.SetItemTemplateId(rs.getInt("item_template_id"));
    					auction.SetItemEnchanteLevel(rs.getInt("item_enchant_level"));
    					HashMap<Integer, SocketInfo> itemSockets = new HashMap<Integer, SocketInfo>();
    					String item_sockets_info = HelperFunctions.readEncodedString(rs.getBytes("item_sockets_info"));
    					String[] sockets_info = item_sockets_info.split(";");
    					for (int i = 0; i < sockets_info.length / 2; i++) {
    						itemSockets.put(i, new SocketInfo(i, sockets_info[i * 2], OID.parseLong(sockets_info[i * 2 + 1])));
    					}
    					auction.SetItemSockets(itemSockets);
    					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    					// LocalDate localDate =
    					// LocalDate.parse(HelperFunctions.readEncodedString(rs.getBytes("expire_date")),
    					// formatter);
    					Date date = rs.getDate("expire_date");
    					Format f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    					String s = f.format(date);
    					if(Log.loggingDebug)	Log.debug("expire_date:" + s);
    					// Date date =
    					// Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    					auction.SetExpirateDate(date);
    					Template itemTemplate = ObjectManagerClient.getTemplate(auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
    					if (itemTemplate == null)
    						Log.error("Template " + auction.GetItemTemplateId() + " is null ");
    					auction.SetItemName(itemTemplate.getName());
    
    					auctions.add(auction);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack(e.getMessage());
		}
		if(Log.loggingDebug)	Log.debug("AccountDatabase getOwnSellAuctions loaded " + auctions.size());

		return auctions;
	}

	public ArrayList<Auction> getOwnExpiredAuctions(OID playerOID) {
		Log.debug("AccountDatabase getOwnExpierdAuctions");
		ArrayList<Auction> auctions = new ArrayList<Auction>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `auction_house_ended` WHERE (status = 3 OR status = 5) AND owner_oid = " + playerOID.toLong() + " ORDER BY expire_date")) {
			Log.debug(ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Auction auction = new Auction(rs.getInt("id"));
    					auction.SetCurrency(rs.getInt("currency_id"));
    					auction.SetStartBid(rs.getInt("startbid"));
    					auction.SetBid(rs.getLong("bid"));
    					auction.SetBuyout(rs.getLong("buyout"));
    					auction.SetMode(rs.getInt("mode"));
    					auction.SetStatus(rs.getInt("status"));
    					auction.SetAuctioneer((rs.getLong("auctioneer_oid")));
    					auction.SetOwnerOid(OID.fromLong(rs.getLong("owner_oid")));
    					auction.SetBidderOid(OID.fromLong(rs.getLong("bidder_oid")));
    					auction.SetItemOid(OID.fromLong(rs.getLong("item_oid")));
    					auction.SetItemCount(rs.getInt("item_count"));
    					auction.SetItemTemplateId(rs.getInt("item_template_id"));
    					auction.SetItemEnchanteLevel(rs.getInt("item_enchant_level"));
    					HashMap<Integer, SocketInfo> itemSockets = new HashMap<Integer, SocketInfo>();
    					String item_sockets_info = HelperFunctions.readEncodedString(rs.getBytes("item_sockets_info"));
    					String[] sockets_info = item_sockets_info.split(";");
    					for (int i = 0; i < sockets_info.length / 2; i++) {
    						itemSockets.put(i, new SocketInfo(i, sockets_info[i * 2], OID.parseLong(sockets_info[i * 2 + 1])));
    					}
    					auction.SetItemSockets(itemSockets);
    					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    					// LocalDate localDate =
    					// LocalDate.parse(HelperFunctions.readEncodedString(rs.getBytes("expire_date")),
    					// formatter);
    					Date date = rs.getDate("expire_date");
    					Format f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    					String s = f.format(date);
    					if(Log.loggingDebug)	Log.debug("expire_date:" + s);
    					// Date date =
    					// Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    					auction.SetExpirateDate(date);
    					Template itemTemplate = ObjectManagerClient.getTemplate(auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
    					if (itemTemplate == null)
    						Log.error("Template " + auction.GetItemTemplateId() + " is null ");
    					auction.SetItemName(itemTemplate.getName());
    
    					auctions.add(auction);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack(e.getMessage());
		}
		if(Log.loggingDebug)Log.debug("AccountDatabase getOwnExpierdAuctions loaded " + auctions.size());

		return auctions;
	}

	public ArrayList<Auction> getWinAuctions(OID playerOID) {
		Log.debug("AccountDatabase getWinAuctions");
		ArrayList<Auction> auctions = new ArrayList<Auction>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `auction_house_ended` WHERE  (status =2 OR status =4 ) AND mode=0 AND bidder_oid = " + playerOID.toLong() + " ORDER BY expire_date")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Auction auction = new Auction(rs.getInt("id"));
    					auction.SetCurrency(rs.getInt("currency_id"));
    					auction.SetStartBid(rs.getInt("startbid"));
    					auction.SetBid(rs.getLong("bid"));
    					auction.SetBuyout(rs.getLong("buyout"));
    					auction.SetMode(rs.getInt("mode"));
    					auction.SetStatus(rs.getInt("status"));
    					auction.SetAuctioneer((rs.getLong("auctioneer_oid")));
    					auction.SetOwnerOid(OID.fromLong(rs.getLong("owner_oid")));
    					auction.SetBidderOid(OID.fromLong(rs.getLong("bidder_oid")));
    					auction.SetItemOid(OID.fromLong(rs.getLong("item_oid")));
    					auction.SetItemCount(rs.getInt("item_count"));
    					auction.SetItemTemplateId(rs.getInt("item_template_id"));
    					auction.SetItemEnchanteLevel(rs.getInt("item_enchant_level"));
    					HashMap<Integer, SocketInfo> itemSockets = new HashMap<Integer, SocketInfo>();
    					String item_sockets_info = HelperFunctions.readEncodedString(rs.getBytes("item_sockets_info"));
    					String[] sockets_info = item_sockets_info.split(";");
    					for (int i = 0; i < sockets_info.length / 2; i++) {
    						itemSockets.put(i, new SocketInfo(i, sockets_info[i * 2], OID.parseLong(sockets_info[i * 2 + 1])));
    					}
    					auction.SetItemSockets(itemSockets);
    					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    					Date date = rs.getDate("expire_date");
    					Format f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    					String s = f.format(date);
    					if(Log.loggingDebug)	Log.debug("expire_date:" + s);
    					auction.SetExpirateDate(date);
    					Template itemTemplate = ObjectManagerClient.getTemplate(auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
    					if (itemTemplate == null)
    						Log.error("Template " + auction.GetItemTemplateId() + " is null ");
    					auction.SetItemName(itemTemplate.getName());
    
    					auctions.add(auction);
    				}
    			}
		    }
        } catch (SQLException e) {
            Log.dumpStack(e.getMessage());
		}
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `auction_house_ended` WHERE  (status =2 OR status =4 ) AND mode=1 AND owner_oid = " + playerOID.toLong() + " ORDER BY expire_date")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Auction auction = new Auction(rs.getInt("id"));
    					auction.SetCurrency(rs.getInt("currency_id"));
    					auction.SetStartBid(rs.getInt("startbid"));
    					auction.SetBid(rs.getLong("bid"));
    					auction.SetBuyout(rs.getLong("buyout"));
    					auction.SetMode(rs.getInt("mode"));
    					auction.SetStatus(rs.getInt("status"));
    					auction.SetAuctioneer((rs.getLong("auctioneer_oid")));
    					auction.SetOwnerOid(OID.fromLong(rs.getLong("owner_oid")));
    					auction.SetBidderOid(OID.fromLong(rs.getLong("bidder_oid")));
    					auction.SetItemOid(OID.fromLong(rs.getLong("item_oid")));
    					auction.SetItemCount(rs.getInt("item_count"));
    					auction.SetItemTemplateId(rs.getInt("item_template_id"));
    					auction.SetItemEnchanteLevel(rs.getInt("item_enchant_level"));
    					HashMap<Integer, SocketInfo> itemSockets = new HashMap<Integer, SocketInfo>();
    					String item_sockets_info = HelperFunctions.readEncodedString(rs.getBytes("item_sockets_info"));
    					String[] sockets_info = item_sockets_info.split(";");
    					for (int i = 0; i < sockets_info.length / 2; i++) {
    						itemSockets.put(i, new SocketInfo(i, sockets_info[i * 2], OID.parseLong(sockets_info[i * 2 + 1])));
    					}
    					auction.SetItemSockets(itemSockets);
    					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    					Date date = rs.getDate("expire_date");
    					Format f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    					String s = f.format(date);
    					if(Log.loggingDebug)Log.debug("expire_date:" + s);
    					auction.SetExpirateDate(date);
    					Template itemTemplate = ObjectManagerClient.getTemplate(auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
    					if (itemTemplate == null)
    						Log.error("Template " + auction.GetItemTemplateId() + " is null ");
    					auction.SetItemName(itemTemplate.getName());
    
    					auctions.add(auction);
    				}
    			}
		    }
		} catch (SQLException e) {
			Log.dumpStack(e.getMessage());
		}
		if(Log.loggingDebug)Log.debug("AccountDatabase getWinAuctions loaded " + auctions.size());

		return auctions;
	}

	public ArrayList<Auction> getSoldAuctions(OID playerOID) {
		Log.debug("AccountDatabase getSoldAuctions");
		ArrayList<Auction> auctions = new ArrayList<Auction>();
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `auction_house_ended` WHERE  (status =2 OR status=6 ) AND mode=0 AND owner_oid = " + playerOID.toLong() + " ORDER BY expire_date")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Auction auction = new Auction(rs.getInt("id"));
    					auction.SetCurrency(rs.getInt("currency_id"));
    					auction.SetStartBid(rs.getInt("startbid"));
    					auction.SetBid(rs.getLong("bid"));
    					auction.SetBuyout(rs.getLong("buyout"));
    					auction.SetMode(rs.getInt("mode"));
    					auction.SetStatus(rs.getInt("status"));
    					auction.SetAuctioneer((rs.getLong("auctioneer_oid")));
    					auction.SetOwnerOid(OID.fromLong(rs.getLong("owner_oid")));
    					auction.SetBidderOid(OID.fromLong(rs.getLong("bidder_oid")));
    					auction.SetItemOid(OID.fromLong(rs.getLong("item_oid")));
    					auction.SetItemCount(rs.getInt("item_count"));
    					auction.SetItemTemplateId(rs.getInt("item_template_id"));
    					auction.SetItemEnchanteLevel(rs.getInt("item_enchant_level"));
    					HashMap<Integer, SocketInfo> itemSockets = new HashMap<Integer, SocketInfo>();
    					String item_sockets_info = HelperFunctions.readEncodedString(rs.getBytes("item_sockets_info"));
    					String[] sockets_info = item_sockets_info.split(";");
    					for (int i = 0; i < sockets_info.length / 2; i++) {
    						itemSockets.put(i, new SocketInfo(i, sockets_info[i * 2], OID.parseLong(sockets_info[i * 2 + 1])));
    					}
    					auction.SetItemSockets(itemSockets);
    					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    					Date date = rs.getDate("expire_date");
    					Format f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    					String s = f.format(date);
    					if(Log.loggingDebug)Log.debug("expire_date:" + s);
    					auction.SetExpirateDate(date);
    					Template itemTemplate = ObjectManagerClient.getTemplate(auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
    					if (itemTemplate == null)
    						Log.error("Template " + auction.GetItemTemplateId() + " is null ");
    					auction.SetItemName(itemTemplate.getName());
    
    					auctions.add(auction);
    				}
    			}
			}
        } catch (SQLException e) {
            Log.dumpStack(e.getMessage());
        }
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `auction_house_ended` WHERE  (status =2 OR status=6 ) AND mode=1 AND bidder_oid = " + playerOID.toLong() + " ORDER BY expire_date")) { 
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Auction auction = new Auction(rs.getInt("id"));
    					auction.SetCurrency(rs.getInt("currency_id"));
    					auction.SetStartBid(rs.getInt("startbid"));
    					auction.SetBid(rs.getLong("bid"));
    					auction.SetBuyout(rs.getLong("buyout"));
    					auction.SetMode(rs.getInt("mode"));
    					auction.SetStatus(rs.getInt("status"));
    					auction.SetAuctioneer((rs.getLong("auctioneer_oid")));
    					auction.SetOwnerOid(OID.fromLong(rs.getLong("owner_oid")));
    					auction.SetBidderOid(OID.fromLong(rs.getLong("bidder_oid")));
    					auction.SetItemOid(OID.fromLong(rs.getLong("item_oid")));
    					auction.SetItemCount(rs.getInt("item_count"));
    					auction.SetItemTemplateId(rs.getInt("item_template_id"));
    					auction.SetItemEnchanteLevel(rs.getInt("item_enchant_level"));
    					HashMap<Integer, SocketInfo> itemSockets = new HashMap<Integer, SocketInfo>();
    					String item_sockets_info = HelperFunctions.readEncodedString(rs.getBytes("item_sockets_info"));
    					String[] sockets_info = item_sockets_info.split(";");
    					for (int i = 0; i < sockets_info.length / 2; i++) {
    						itemSockets.put(i, new SocketInfo(i, sockets_info[i * 2], OID.parseLong(sockets_info[i * 2 + 1])));
    					}
    					auction.SetItemSockets(itemSockets);
    					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    					Date date = rs.getDate("expire_date");
    					Format f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    					String s = f.format(date);
    					if(Log.loggingDebug)Log.debug("expire_date:" + s);
    					auction.SetExpirateDate(date);
    					Template itemTemplate = ObjectManagerClient.getTemplate(auction.GetItemTemplateId(), ObjectManagerPlugin.ITEM_TEMPLATE);
    					if (itemTemplate == null)
    						Log.error("Template " + auction.GetItemTemplateId() + " is null ");
    					auction.SetItemName(itemTemplate.getName());
    
    					auctions.add(auction);
    				}
    			}
		    }

		} catch (SQLException e) {
			Log.dumpStack(e.getMessage());
		}
		if(Log.loggingDebug)Log.debug("AccountDatabase getSoldAuctions loaded " + auctions.size());

		return auctions;
	}

	public void saveAuctionEnded(Auction auction) {
		if(Log.loggingDebug)Log.debug("saveAuctionEnded: updating auction id: " + auction.GetId());
		try {
			String updateString = "UPDATE `auction_house_ended` SET status = " + auction.GetStatus() + ",bid=" + auction.GetBid() + ", bidder_oid = "
					+ (auction.GetBidderOid() != null ? auction.GetBidderOid().toLong() : 0l) + ", item_count = " + auction.GetItemCount() + "  WHERE id =" + auction.GetId();
			if(Log.loggingDebug)Log.debug("saveAuctionEnded Update ended sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
	}

	public void saveAuction(Auction auction) {
		if(Log.loggingDebug)Log.debug("saveAuction: updating auction id: " + auction.GetId());
		try {
			String updateString = "UPDATE `auction_house` SET status = " + auction.GetStatus() + ",bid=" + auction.GetBid() + ", bidder_oid = "
					+ (auction.GetBidderOid() != null ? auction.GetBidderOid().toLong() : 0l) + ", item_count = " + auction.GetItemCount() + " ,`item_oid`= "
					+ (auction.GetItemOid() != null ? auction.GetItemOid().toLong() : 0l) + " WHERE id =" + auction.GetId();
			if(Log.loggingDebug)Log.debug("saveAuction Update sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
	}

	public void deleteAuction(Auction auction) {
		if(Log.loggingDebug)Log.debug("deleteAuction: start auction id: " + auction.GetId());
		try {
			String updateString = "DELETE FROM `auction_house` WHERE id =" + auction.GetId();
			if(Log.loggingDebug)Log.debug("deleteAuction Delete sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
		if(Log.loggingDebug)Log.debug("deleteAuction: end auction id: " + auction.GetId());

	}

	public void deleteOldAuction() {
		Log.debug("deleteOldAuction: start");
		try {
			String updateString = "DELETE FROM `auction_house_ended` WHERE status = 7";
			if(Log.loggingDebug)	Log.debug("deleteOldAuction: Delete sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
		Log.debug("deleteOldAuction: end  ");

	}

	public int InsertAuction(Auction auction) {
		if (Log.loggingDebug)
			Log.debug("InsertAuction Start " + auction);
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Calendar cal = Calendar.getInstance();
		cal.setTime(auction.GetExpirateDate());
		String strDate = sdfDate.format(cal.getTime());
		int inserted = -1;
		try {
			if (Log.loggingDebug)
				Log.debug("InsertAuction Insert try start");
			Log.debug("InsertAuction Insert try " + auction.GetItemSockets().size());
			String socketInfo = "";
			for (int i = 0; i < auction.GetItemSockets().size(); i++) {
				Log.debug("InsertAuction: i:" + i + " Type" + auction.GetItemSockets().get(i).GetType() + " Oid:" + auction.GetItemSockets().get(i).GetItemOid());
				socketInfo += auction.GetItemSockets().get(i).GetType() + ";"
						+ (auction.GetItemSockets().get(i).GetItemOid() != null ? auction.GetItemSockets().get(i).GetItemOid().toLong() : 0L);
				if (i < auction.GetItemSockets().size() - 1)
					socketInfo += ";";
				if (Log.loggingDebug)
					Log.debug("InsertAuction Insert try i:" + i + " socketInfo:" + socketInfo);

			}
			if (Log.loggingDebug)
				Log.debug("InsertAuction Insert try start socketInfo:" + socketInfo);
			// TODO Brak Logiki Race Group dla aukcji
			int race_group = 1;
			String insertString = "INSERT INTO `auction_house` (`id`, `startbid`, `currency_id`, `expire_date`, `auctioneer_oid`, `owner_oid`, `bidder_oid`, `race_group_id`, `bid`,"
					+ " `buyout`, `status`, `mode`, `item_oid`, `item_count`, `item_template_id`, `item_enchant_level`, `item_sockets_info`, `world_name`) VALUES (NULL, "
					+ auction.GetStartBid() + ", " + auction.GetCurrency() + ",'" + strDate + "', " +  auction.GetAuctioneer()
					+ ", " + auction.GetOwnerOid().toLong() + ", " + (auction.GetBidderOid() != null ? auction.GetBidderOid().toLong() : 0l) + ", " + race_group + "," + auction.GetBid()
					+ " ," + auction.GetBuyout() + "," + auction.GetStatus() + ", " + auction.GetMode() + "," + (auction.GetItemOid() != null ? auction.GetItemOid().toLong() : 0l) + ","
					+ auction.GetItemCount() + " ," + auction.GetItemTemplateId() + ", " + auction.GetItemEnchanteLevel() + ", '" + socketInfo + "', '" + Engine.getWorldName() + "');";
			if (Log.loggingDebug)
				Log.debug("InsertAuction Insert sql:" + insertString);
			inserted = queries.executeInsert(insertString);
		} catch (Exception e) {
			Log.dumpStack(e.toString());
			Log.dumpStack(e.getMessage());
		}
		if (Log.loggingDebug)
			Log.debug("InsertAuction End inserted id:" + inserted);

		return inserted;
	}

	public int insertAuctionEnded(Auction auction) {
		if (Log.loggingDebug)
			Log.debug("InsertAuctionEnded Start auction:" + auction);
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Calendar cal = Calendar.getInstance();
		cal.setTime(auction.GetExpirateDate());
		String strDate = sdfDate.format(cal.getTime());
		int inserted = -1;
		try {
			if (Log.loggingDebug)
				Log.debug("InsertAuctionEnded try start");
			Log.debug("InsertAuctionEnded socket size: " + auction.GetItemSockets().size());
			String socketInfo = "";
			for (int i = 0; i < auction.GetItemSockets().size(); i++) {
				socketInfo += auction.GetItemSockets().get(i).GetType() + ";"
						+ (auction.GetItemSockets().get(i).GetItemOid() != null ? auction.GetItemSockets().get(i).GetItemOid().toLong() : 0L);
				if (i < auction.GetItemSockets().size() - 1)
					socketInfo += ";";
			}
			if (Log.loggingDebug)
				Log.debug("InsertAuctionEnded socketInfo:" + socketInfo);
			int race_group = 1;
			String insertString = "INSERT INTO `auction_house_ended` (`id`, `startbid`, `currency_id`, `expire_date`, `auctioneer_oid`, `owner_oid`, `bidder_oid`, `race_group_id`, `bid`,"
					+ " `buyout`, `status`, `mode`, `item_oid`, `item_count`, `item_template_id`, `item_enchant_level`, `item_sockets_info`, `world_name`) VALUES (NULL, "
					+ auction.GetStartBid() + ", " + auction.GetCurrency() + ",'" + strDate + "', " + auction.GetAuctioneer() 
					+ ", " + auction.GetOwnerOid().toLong() + ", " + (auction.GetBidderOid() != null ? auction.GetBidderOid().toLong() : 0l) + ", " + race_group + "," + auction.GetBid()
					+ " ," + auction.GetBuyout() + "," + auction.GetStatus() + ", " + auction.GetMode() + "," + (auction.GetItemOid() != null ? auction.GetItemOid().toLong() : 0l) + ","
					+ auction.GetItemCount() + " ," + auction.GetItemTemplateId() + ", " + auction.GetItemEnchanteLevel() + ", '" + socketInfo + "', '" + Engine.getWorldName() + "');";
			if (Log.loggingDebug)
				Log.debug("InsertAuctionEnded Insert sql:" + insertString);
			inserted = queries.executeInsert(insertString);
		} catch (Exception e) {
			Log.dumpStack(e.toString());
			Log.dumpStack(e.getMessage());
		}
		if (Log.loggingDebug)
			Log.debug("InsertAuctionEnded End inserted id:" + inserted);

		return inserted;
	}

	void checkCooldowns() {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `cooldowns` ")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    
    				while (rs.next()) {
    					long duration = rs.getLong("duration");
    					long startTime = rs.getLong("startTime");
    					String cid = HelperFunctions.readEncodedString(rs.getBytes("cid"));
    					if (startTime + duration > System.currentTimeMillis()) {
    
    					} else {
    						Log.debug("Delete cooldown id " + rs.getInt("id") + " for Object:" + rs.getLong("obj_oid"));
    						String deleteString = "DELETE FROM `cooldowns` where id =" + rs.getInt("id");
    						queries.executeUpdate(deleteString);
    					}
    
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack(e.getMessage());
		}
	}

	// TODO Need Add Clean old Cooldowns from Database
	public LinkedList<Cooldown> getCooldowns(OID characterOID) {
		LinkedList<Cooldown> cooldownList = new LinkedList<Cooldown>();
		Log.debug("Character : Cooldowns character oid: " + characterOID + " " + characterOID.toLong());

			// Now check if an account entry exists in the admin database - if not - create
			// one
		try (PreparedStatement ps = queries.prepare("SELECT * FROM `cooldowns` where obj_oid=" + characterOID.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    
    				while (rs.next()) {
    					long duration = rs.getLong("duration");
    					long startTime = rs.getLong("startTime");
    					String cid = HelperFunctions.readEncodedString(rs.getBytes("cid"));
    					if (startTime + duration > System.currentTimeMillis()) {
    						Cooldown c = new Cooldown(cid, duration, startTime);
    						cooldownList.add(c);
    					} else {
    						Log.debug("Delete cooldown id " + rs.getInt("id"));
    						String deleteString = "DELETE FROM `cooldowns` where obj_oid=" + characterOID.toLong() + " and id =" + rs.getInt("id");
    						queries.executeUpdate(deleteString);
    					}
    
    				}
    			}
			}
			Log.debug("Character : Cooldowns character oid: " + characterOID + " " + cooldownList);

			// return cooldownList;
		} catch (SQLException e) {
			Log.dumpStack(e.getMessage());
		}

		return cooldownList;
	}

	public void saveCooldowns(OID characterOID, LinkedList<Cooldown> cooldownList) {

		String columnNames = "id,cid,duration,startTime,obj_oid";
		try {
			Log.debug("delete all cooldowns from db");
			String deleteString = "DELETE FROM `cooldowns` where obj_oid=" + characterOID.toLong();
			queries.executeUpdate(deleteString);

			for (int i = cooldownList.size() - 1; i >= 0; --i) {
				if (cooldownList.get(i).getStartTime() != 0 && ((cooldownList.get(i).getStartTime() + cooldownList.get(i).getDuration() < System.currentTimeMillis()) ||
						(cooldownList.get(i).getDuration() <  CombatPlugin.SAVE_COOLDOWN_LIMIT_DURATION * 1000) ||
						(cooldownList.get(i).getDuration() >=  CombatPlugin.SAVE_COOLDOWN_LIMIT_DURATION * 1000 &&  cooldownList.get(i).getStartTime() + cooldownList.get(i).getDuration() < System.currentTimeMillis() +  CombatPlugin.SAVE_COOLDOWN_LIMIT_DURATION * 1000
								))) {
					Log.debug("Cooldown.activateCooldowns remove " + cooldownList.get(i)+" StartTime="+cooldownList.get(i).getStartTime()+
							" Duration="+cooldownList.get(i).getDuration()+" Sum="+(cooldownList.get(i).getStartTime() + cooldownList.get(i).getDuration())+
							" CurentTime="+System.currentTimeMillis());

					cooldownList.remove(i);
					Log.debug("Cooldown.activateCooldowns remove " + i + " element");
				} else {
					String values = "null,'" + cooldownList.get(i).getID() + "' , " + cooldownList.get(i).getDuration() + ", " + cooldownList.get(i).getStartTime() + ", "
							+ characterOID.toLong();
					String insertString = "INSERT INTO `cooldowns` (" + columnNames + ") VALUES (" + values + ")";
					Log.debug("DB " + insertString);
					int inserted = queries.executeInsert(insertString);

				}

			}

		} catch (Exception e) {
			Log.exception("saveCooldowns SQLException ", e);;

		} finally {
		//	queries.closeStatement(ps, rs);
		}
	}

	/**
	 * Adds a newly created character to an account entry. If there is no account
	 * entry, a new one will be made.
	 * 
	 * @param accountID
	 * @param characterOID
	 * @param characterName
	 * @return
	 */
	public boolean characterCreated(OID accountID, String accountName, OID characterOID, String characterName) {
		Log.debug("ACCOUNT: attempting to add character oid: " + characterOID + " to account entry: " + accountID);
		// First add the character
		addAccountCharacter(accountID, characterOID, characterName);

		// Now check if an account entry exists in the admin database - if not - create
		// one
		try (PreparedStatement ps = queries.prepare("SELECT username FROM " + accountTableName + " where id=" + accountID.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				if (!rs.next()) {
    					createAccount(accountID, accountName, characterOID, characterName);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("characterCreated: "+e+" "+e.getMessage()+" "+e.getLocalizedMessage());
		}
		return true;
	}

	/**
	 * Creates a new account entry
	 * 
	 * @param accountID
	 * @param characterOID
	 * @param characterName
	 * @return
	 */
	public int createAccount(OID accountID, String accountName, OID characterOID, String characterName) {
		long timestamp = System.currentTimeMillis() / timeDivider;
		Log.debug("ACCOUNT: creating entry for accountID: " + accountID + " with character OID:" + characterOID + " and character name: " + characterName + " and current time: "
				+ timestamp);

		String columnNames = "id,username,status,last_login,created,coin_current,coin_total,coin_used";
		String values = accountID.toLong() + ",'" + accountName + "'," + 1 + ",FROM_UNIXTIME(" + timestamp + "),FROM_UNIXTIME(" + timestamp + ")," + 0 + "," + 0 + "," + 0;
		String insertString = "INSERT INTO `" + accountTableName + "` (" + columnNames + ") VALUES (" + values + ")";
		int inserted = queries.executeInsert(insertString);
		return inserted;
	}
	/**
	 * Assigning the oid and character name to the account id
	 * @param accountID
	 * @param characterOID
	 * @param characterName
	 * @return
	 */
	private boolean addAccountCharacter(OID accountID, OID characterOID, String characterName) {
		Log.debug("ACCOUNT: inserting character:" + characterOID + " in account: " + accountID);
		String columnNames = "characterId,characterName,accountId";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO " + characterTableName + " (" + columnNames + ") values (?, ?, ?)")) {
			stmt.setLong(1, characterOID.toLong());
			stmt.setString(2, characterName);
			stmt.setLong(3, accountID.toLong());
			queries.executeInsert(stmt);
		} catch (SQLException e) {
			Log.error("addAccountCharacter: "+e+" "+e.getMessage()+" "+e.getLocalizedMessage());
			return false;
		}

		return true;
	}

	/**
	 * Removes a deleted character from an account entry.
	 * 
	 * @param accountID
	 * @param characterOID
	 * @param characterName
	 * @return
	 */
	public boolean characterDeleted(OID accountID, OID characterOID, String characterName) {
		String deleteString = "DELETE FROM `" + characterTableName + "` WHERE characterId = " + characterOID.toLong();
		queries.executeUpdate(deleteString);
		return true;
	}

	public long getCharacterOidByName(String characterName) {
		Log.debug("ACCOUNT: getting characterOid from characterName: " + characterName);
		try (PreparedStatement ps = queries.prepare("SELECT characterId FROM " + characterTableName + " WHERE characterName = '" + characterName + "'")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					long characterId = rs.getLong("characterId");
    					Log.debug("ACCOUNT: account characterId for characterName: " + characterName + " is: " + characterId);
    					return characterId;
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("getCharacterOidByName: " + e.getMessage());
		}
		return 0l;
	}

	public String getCharacterNameByOid(OID characterId) {
		Log.debug("ACCOUNT: getting characterOid from characterId: " + characterId);
		try (PreparedStatement ps = queries.prepare("SELECT characterName FROM " + characterTableName + " WHERE characterId = " + characterId.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String characterName = HelperFunctions.readEncodedString(rs.getBytes("characterName"));
    					Log.debug("ACCOUNT: account characterName for characterId: " + characterId + " is: " + characterName);
    					return characterName;
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("getCharacterOidByName: " + e.getMessage());
		}
		return "";
	}

	public int getAccountStatus(OID accountID) {
		Log.debug("ACCOUNT: getting account status for account entry: " + accountID);
		try (PreparedStatement ps = queries.prepare("SELECT status FROM " + accountTableName + " where id=" + accountID.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int status = rs.getInt("status");
    					Log.debug("ACCOUNT: account status for account: " + accountID + " is: " + status);
    					return status;
    				}
    			}
			}
		} catch (SQLException e) {
		}
		return 1;
	}
	
	
	public void setAccountStatus(OID accountID, int status) {
		if(Log.loggingDebug)Log.debug("ACCOUNT: set status for account: " + accountID);
		try {
			String updateString = "UPDATE "+accountTableName+"  SET status = " + status + " WHERE id =" + accountID.toLong();
			if(Log.loggingDebug)Log.debug("ACCOUNT: Update sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
	}

	
	
	public int getCharacterAccountStatus(OID characterOid) {
		Log.debug("ACCOUNT: getting account status for account entry: " + characterOid);
		if(characterOid==null)
			return 0;
		String sql ="SELECT `status` FROM `account` as a Join `account_character` as ac on ac.accountId = a.id where ac.characterId = "+characterOid.toLong();
		Log.debug("ACCOUNT: sql: " + sql);
		try (PreparedStatement ps = queries.prepare(sql)) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int status = rs.getInt("status");
    					Log.debug("ACCOUNT: account status for characterOid: " + characterOid + " is: " + status);
    					return status;
    				}
    			}
			}
		} catch (SQLException e) {
		}
		return 1;
	}
	
	
	
	
	
	public OID getAccountForCharacter(OID characterOid) {
		Log.debug("ACCOUNT: getting account status for account entry: " + characterOid);
		if(characterOid==null)
			return null;
		String sql ="SELECT `accountId` FROM `account_character` Where characterId = "+characterOid.toLong();
		Log.debug("ACCOUNT: sql: " + sql);
		try (PreparedStatement ps = queries.prepare(sql)) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Long accountId = rs.getLong("accountId");
    					Log.debug("ACCOUNT: account Id for characterOid: " + characterOid + " is: " + accountId);
    					return OID.fromLong(accountId);
    				}
    			}
			}
		} catch (SQLException e) {
		}
		return null;
	}



	public int getNumIslands(OID accountID) {
		// TODO: First get account status - if GM or Admin then we have no limit?
		Log.debug("ACCOUNT: getting island limit for account entry: " + accountID);
		try (PreparedStatement ps = queries.prepare("SELECT islands_available FROM " + accountTableName + " where id=" + accountID.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int numAvailable = rs.getInt("islands_available");
    					Log.debug("ACCOUNT: account status for account: " + accountID + " is: " + numAvailable);
    					return numAvailable;
    				}
    			}
			}
		} catch (SQLException e) {
		}
		return 0;
	}

	/**
	 * Gets the number of character slots this account has. It will first check to
	 * see if the account has any unclaimed character slot purchases.
	 * 
	 * @param accountID
	 * @return
	 */
	public int getNumCharacterSlots(OID accountID) {
		Log.debug("ACCOUNT: getting account status for account entry: " + accountID);
			// Now fetch the number of character slots
		try (PreparedStatement ps = queries.prepare("SELECT character_slots FROM " + accountTableName + " where id=" + accountID.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int characterSlots = rs.getInt("character_slots");
    					return characterSlots;
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("getNumCharacterSlots Exception " + e);
		}
		return 2;
	}

	public boolean characterLoggedIn(OID accountID) {
		Log.debug("ACCOUNT: updating last login: for account: " + accountID);
		long timestamp = System.currentTimeMillis() / timeDivider;
		String updateString = "UPDATE " + accountTableName + " set last_login = FROM_UNIXTIME(" + timestamp + ") where id=" + accountID.toLong();
		queries.executeUpdate(updateString);
		return true;
	}

	public boolean characterLoggedOut(OID accountID) {
		Log.debug("ACCOUNT: updating last logout: for account: " + accountID);
		long timestamp = System.currentTimeMillis() / timeDivider;
		String updateString = "UPDATE " + accountTableName + " set last_logout = FROM_UNIXTIME(" + timestamp + ") where id=" + accountID.toLong();
		queries.executeUpdate(updateString);
		return true;
	}

	/**
	 * Gets the BlockList of the player has so it can be shown to them in their UI
	 * 
	 * @param characterOID
	 * @return
	 */
	public HashMap<OID, String> getBlockList(OID characterOID) {
		HashMap<OID, String> blockList = new HashMap<OID, String>();
		Log.debug("ACCOUNT: getting BlockList of character: " + characterOID.toLong());
		try (PreparedStatement ps = queries.prepare("SELECT * FROM character_block_list where character_id=" + characterOID.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String blockedPlayerName = HelperFunctions.readEncodedString(rs.getBytes("friend_name"));
    					OID blockedPlayerOID = OID.fromLong(rs.getLong("block_player_id"));
    					if(!blockList.containsKey(blockedPlayerOID))
    					blockList.put(blockedPlayerOID, blockedPlayerName);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("getBlockList Exception " + e);
		}
		return blockList;
	}

	/**
	 * Creates a new database entry listing a player as another players BlockList.
	 * 
	 * @param characterOID
	 * @param blockedPlayerOID
	 * @param blockedPlayerName
	 */
	public void addToBlackList(OID characterOID, OID blockedPlayerOID, String blockedPlayerName) {
		Log.debug("ACCOUNT: creating BlockList entry linking character OID:" + characterOID.toLong() + " and blocked Player name: " + blockedPlayerName);
		String tableName = "character_block_list";
		String columnNames = "character_id,block_player_id,friend_name";
		String values = "" + characterOID.toLong() + "," + blockedPlayerOID.toLong() + ",'" + blockedPlayerName + "'";
		String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
		Log.debug("addToBlackList: sql:" + insertString);
		queries.executeInsert(insertString);
	}

	/**
	 * Delete database entry listing a player as another players BlockList.
	 * 
	 * @param characterOID
	 * @param blockedPlayerOID
	 */
	public void DelFromBlackList(OID characterOID, OID blockedPlayerOID) {
		Log.debug("ACCOUNT: Deleting Blocked player entry linking character OID:" + characterOID.toLong() + " and blockedPlayerOID: " + blockedPlayerOID);
		try {
			String tableName = "character_block_list";

			String deleteString = "DELETE FROM `" + tableName + "` WHERE character_id = " + characterOID.toLong() + " AND block_player_id=" + blockedPlayerOID.toLong();
			Log.debug("DelFriend sql " + deleteString);
			queries.executeUpdate(deleteString);
		} catch (Exception e) {
			Log.dumpStack("DelFriend Exception " + e);
			// } finally {
			// queries.closeStatement(ps, rs);
		}
	}

	public boolean isOnBlackList(OID characterOID, OID blockedPlayerOID) {
		
		Log.debug("ACCOUNT: isOnBlackList player entry linking character OID:" + characterOID + " and blockedPlayerOID: " + blockedPlayerOID);
		if(blockedPlayerOID==null) {
			Log.debug("ACCOUNT: isOnBlackList character OID:" + characterOID + " and blockedPlayerOID: " + blockedPlayerOID+ " | is null return false");
			return false;
		}
		if(characterOID==null) {
			Log.debug("ACCOUNT: isOnBlackList character OID:" + characterOID + " and blockedPlayerOID: " + blockedPlayerOID+ " | is null return false");
			return false;
		}
		
			
		boolean isOnList = false;
		Log.debug("ACCOUNT: isOnBlackList getting BlockList of character: " + characterOID.toLong());
		try (PreparedStatement ps = queries.prepare("SELECT * FROM character_block_list where character_id=" + blockedPlayerOID.toLong() + " AND block_player_id=" + characterOID.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Log.debug("ACCOUNT: isOnBlackList true");
    					isOnList = true;
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("isOnBlackList "+e);
		}
		Log.debug("ACCOUNT: isOnBlackList true?:" + isOnList);

		return isOnList;

	}

	/**
	 * Gets the list of friends a player has so it can be shown to them in their UI
	 * 
	 * @param characterOID
	 * @return
	 */
	public HashMap<OID, String> getFriends(OID characterOID) {
		HashMap<OID, String> friends = new HashMap<OID, String>();
		Log.debug("ACCOUNT: getting friends of character: " + characterOID.toLong());
		try (PreparedStatement ps = queries.prepare("SELECT * FROM character_friends where character_id=" + characterOID.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String friendName = HelperFunctions.readEncodedString(rs.getBytes("friend_name"));
    					OID friendOID = OID.fromLong(rs.getLong("friend_id"));
    					friends.put(friendOID, friendName);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("getFriends "+e);
		}
		return friends;
	}

	/**
	 * Gets the list of players to notify when this person logs in or logs out.
	 * 
	 * @param characterOID
	 * @return
	 */
	public LinkedList<OID> getFriendsOf(OID characterOID) {
		LinkedList<OID> friends = new LinkedList<OID>();
		Log.debug("ACCOUNT: getting friends of character: " + characterOID.toLong());
		try (PreparedStatement ps = queries.prepare("SELECT * FROM character_friends where friend_id=" + characterOID.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					OID friendOID = OID.fromLong(rs.getLong("character_id"));
    					friends.add(friendOID);
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("getFriendsOf "+e);
		}
		return friends;
	}

	/**
	 * Creates a new database entry listing a player as another players friend.
	 * 
	 * @param characterOID
	 * @param friendOID
	 * @param friendName
	 */
	public void addFriend(OID characterOID, OID friendOID, String friendName) {
		Log.debug("ACCOUNT: creating friend entry linking character OID:" + characterOID.toLong() + " and friend name: " + friendName);
		String tableName = "character_friends";
		String columnNames = "character_id,friend_id,friend_name";
		String values = "" + characterOID.toLong() + "," + friendOID.toLong() + ",'" + friendName + "'";
		String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
		queries.executeInsert(insertString);
	}

	/**
	 * Delete database entry listing a player as another players friend.
	 * 
	 * @param characterOID
	 * @param friendOID
	 */
	public void DelFriend(OID characterOID, OID friendOID) {
		Log.debug("ACCOUNT: Deleting friend entry linking character OID:" + characterOID.toLong() + " and friend: " + friendOID);
		try {
			String tableName = "character_friends";

			String deleteString = "DELETE FROM `" + tableName + "` WHERE character_id = " + characterOID.toLong() + " AND friend_id=" + friendOID.toLong();
			queries.executeUpdate(deleteString);
		} catch (Exception e) {
			Log.dumpStack("DelFriend Exception " + e);
			// } finally {
			// queries.closeStatement(ps, rs);
		}
	}

	/**
	 * Adds the specified skin to the account. First checks if the account already
	 * has the skin.
	 * 
	 * @param accountOID
	 * @param skin
	 */
	public boolean addSkin(OID accountOID, String skin) {
		Log.debug("ACCOUNT: creating skin entry with account OID:" + accountOID.toLong() + " and skin: " + skin);
		String tableName = "character_skins";
		Log.debug("ACCOUNT: getting skins of account: " + accountOID.toLong());
		try (PreparedStatement ps = queries.prepare("SELECT * FROM " + tableName + " where account_id=" + accountOID.toLong() + " and character_skin='" + skin + "'")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return false;
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("addSkin Exception " + e);
		}
		String columnNames = "account_id,character_skin,created,source";
		String values = "" + accountOID.toLong() + ",'" + skin + "', NOW(), 'Merchant'";
		String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
		queries.executeInsert(insertString);
		return true;
	}

	/**
	 * Adds the specified item to the account. First checks if the account already
	 * has the item.
	 * 
	 * @param accountOID
	 * @param itemID
	 * @param amount
	 */
	public boolean addItem(OID accountOID, int itemID, int amount) {
		Log.debug("ACCOUNT: creating item entry with account OID:" + accountOID.toLong() + " and item: " + itemID);
		String tableName = "character_items";
		Log.debug("ACCOUNT: getting items of account: " + accountOID.toLong());
		try (PreparedStatement ps = queries.prepare("SELECT * FROM " + tableName + " where account_id=" + accountOID.toLong() + " and itemID=" + itemID)) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return false;
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("addItem Exception " + e);
		}
		String columnNames = "account_id,itemID,amount";
		String values = "" + accountOID.toLong() + "," + itemID + ", " + amount;
		String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
		queries.executeInsert(insertString);
		return true;
	}

	public void alterItemAmount(OID accountID, int itemID, int delta) {
		Log.debug("ACCOUNT: altering item amount for account " + accountID.toLong());
		String tableName = "character_items";
		String updateString = "UPDATE " + tableName + " set amount = amount + " + delta + " where account_id=" + accountID.toLong() + " and itemID = " + itemID;
		queries.executeUpdate(updateString);
		return;
	}

	/**
	 * Removes the specified items from the account.
	 * 
	 * @param accountOID
	 * @param itemID
	 */
	public boolean removeItem(OID accountOID, int itemID) {
		Log.debug("ACCOUNT: creating weapon entry with account OID:" + accountOID.toLong() + " and skin: " + itemID);
		String tableName = "character_items";
		int entryID = -1;
		Log.debug("ACCOUNT: getting items of account: " + accountOID.toLong());
		try (PreparedStatement ps = queries.prepare("SELECT id FROM " + tableName + " where account_id=" + accountOID.toLong() + " and itemID=" + itemID)) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					entryID = rs.getInt("id");
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("removeItem Exception " + e);
		}
		if (entryID == -1)
			return false;
		String deleteString = "DELETE FROM `" + tableName + "` where id = " + entryID;
		queries.executeUpdate(deleteString);
		return true;
	}

	/**
	 * Gets the items that the passed in account has access to.
	 * 
	 * @param accountOID
	 */
	public HashMap<Integer, Integer> getItems(OID accountOID) {
		HashMap<Integer, Integer> items = new HashMap<Integer, Integer>();
		Log.debug("ACCOUNT: getting items from account: " + accountOID.toLong());
		try (PreparedStatement ps = queries.prepare("SELECT * FROM character_items where account_id=" + accountOID.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					items.put(rs.getInt("itemID"), rs.getInt("amount"));
    				}
    			}
			}
		} catch (SQLException e) {
			Log.dumpStack("getItems Exception " + e);
		}
		return items;
	}

	/**
	 * Instance Template related database queries
	 */
	public HashMap<Integer, InstanceTemplate> loadInstanceTemplateData() {
		HashMap<Integer, InstanceTemplate> list = new HashMap<Integer, InstanceTemplate>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM instance_template")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					InstanceTemplate island = new InstanceTemplate();
    					island.setID(rs.getInt("id"));
    					island.setCategory(rs.getInt("category"));
    					island.setName(HelperFunctions.readEncodedString(rs.getBytes("island_name")));
    					OID administrator = OID.fromLong(rs.getLong("administrator"));
    					island.setAdministrator(administrator);
    					island.setIsPublic(rs.getBoolean("public"));
    					island.setPassword(HelperFunctions.readEncodedString(rs.getBytes("password")));
    					island.setDevelopers(loadIslandDevelopers(island.getID()));
    					island.setIslandType(rs.getInt("islandType"));
    					// Only allow createOnStartup to be read for world type instances
    					if (island.getIslandType() == InstanceTemplate.ISLAND_TYPE_WORLD) {
    						island.setCreateOnStartup(true/* rs.getBoolean("createOnStartup") */);
    					} else {
    						island.setCreateOnStartup(false);
    					}
    					island.setGlobalWaterHeight(rs.getFloat("globalWaterHeight"));
    					island.setStyle(HelperFunctions.readEncodedString(rs.getBytes("style")));
    					island.setDescription(HelperFunctions.readEncodedString(rs.getBytes("description")));
    					island.setRating(rs.getInt("rating"));
    					island.setSize(rs.getInt("size"));
    					island.setPopulationLimit(rs.getInt("populationLimit"));
    					Date subscriptionExpiration = rs.getDate("subscription");
    					Log.warn("Island " + island.getName() + " has subscription Expiration: " + subscriptionExpiration);
    					island.setContentPacks(loadIslandContentPacks(island.getID()));
    					island.setPortals(loadIslandPortals(island.getID()));
    					list.put(island.getID(), island);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadInstanceTemplateData Exception " + e);
		}
		return list;
	}

	public LinkedList<String> loadIslandContentPacks(int islandID) {
		LinkedList<String> contentPacks = new LinkedList<String>();

		return contentPacks;
	}

	public LinkedList<OID> loadIslandDevelopers(int islandID) {
		LinkedList<OID> list = new LinkedList<OID>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM island_developers where island = " + islandID)) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					OID developer = OID.fromLong(rs.getLong("developer"));
    					list.add(developer);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadIslandDevelopers Exception " + e);
		}
		return list;
	}

	public ArrayList<String> getIslandName(String islandName) {
		ArrayList<String> list = new ArrayList<String>();
	    try (PreparedStatement ps = queries.prepare("SELECT island_name FROM instance_template where island_name = '" + islandName + "'")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					list.add(HelperFunctions.readEncodedString(rs.getBytes("island_name")));
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("getIslandName Exception " + e);
		}
		return list;
	}

	public OID getIslandAdministrator(int islandID) {
		OID administrator = null;
	    try (PreparedStatement ps = queries.prepare("SELECT administrator FROM instance_template where id = " + islandID)) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					administrator = OID.fromLong(rs.getLong("administrator"));
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("getIsgetIslandAdministratorlandName Exception " + e);
		}
		return administrator;
	}

	public HashMap<String, HashMap<String, Float>> loadIslandPortals(int islandID) {
		HashMap<String, HashMap<String, Float>> list = new HashMap<String, HashMap<String, Float>>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM island_portals where island = " + islandID)) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					HashMap<String, Float> portalProps = new HashMap<String, Float>();
    					portalProps.put("id", (float) rs.getInt("id"));
    					portalProps.put("portalType", (float) rs.getInt("portalType"));
    					portalProps.put("faction", (float) rs.getInt("faction"));
    					portalProps.put("displayID", (float) rs.getInt("displayID"));
    					portalProps.put("locX", rs.getFloat("locX"));
    					portalProps.put("locY", rs.getFloat("locY"));
    					portalProps.put("locZ", rs.getFloat("locZ"));
    					portalProps.put("orientX", rs.getFloat("orientX"));
    					portalProps.put("orientY", rs.getFloat("orientY"));
    					portalProps.put("orientZ", rs.getFloat("orientZ"));
    					portalProps.put("orientW", rs.getFloat("orientW"));
    					list.put(HelperFunctions.readEncodedString(rs.getBytes("name")), portalProps);
    					Log.debug("PORTAL: loaded portal " + rs.getString("name") + " in island: " + islandID);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadIslandPortals Exception " + e);
		}
		return list;
	}

	public void writeIslandData(InstanceTemplate island, String template) {
		Log.debug("Writing island data to database");
		String tableName = "instance_template";
		String columnNames = "island_name,template,administrator,status,public,password,islandType,createOnStartup,style,recommendedLevel,description,size";
		String values = "'" + island.getName() + "','" + template + "'," + island.getAdministrator().toLong() + ",'Active'," + island.getIsPublic() + ",'',0,"
				+ island.getCreateOnStartup() + ",'',1,'',1";
		String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
		int inserted = queries.executeInsert(insertString);
		Log.debug("Wrote island data to database with inserted: " + inserted);
		return;
	}

	public HashMap<String, HashMap<String, Float>> writePortalData(int islandID, HashMap<String, HashMap<String, Float>> portals) {
		Log.debug("Writing island portal data to database");
		HashMap<String, HashMap<String, Float>> newPortals = new HashMap<String, HashMap<String, Float>>();
		String tableName = "island_portals";
		String columnNames = "island,portalType,faction,locX,locY,locZ,orientX,orientY,orientZ,orientW,displayID";
		for (String name : portals.keySet()) {
			HashMap<String, Float> portalProps = portals.get(name);
			String values = islandID + "," + 1 + "," + portalProps.get("faction") + "," + portalProps.get("locX") + "," + portalProps.get("locY") + "," + portalProps.get("locZ") + ","
					+ portalProps.get("orientX") + "," + portalProps.get("orientY") + "," + portalProps.get("orientZ") + "," + portalProps.get("orientW") + ","
					+ portalProps.get("displayID");
			String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
			int inserted = queries.executeInsert(insertString);
			Log.debug("Wrote island portal data to database with inserted: " + inserted);
			if (inserted != -1)
				newPortals.put(name, portalProps);
		}
		return newPortals;
	}

	public void editPortalData(String portalName, HashMap<String, Float> portalProps) {
		Log.debug("Writing portal data to database");
		String tableName = "island_portals";
	    try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set portalType=?, faction=?, locX=?, " + "locY=?, locZ=?, orientX=?, orientY=?, orientZ=?, orientW=?, name=?,  where id=?")) {
			stmt.setInt(1, portalProps.get("portalType").intValue());
			stmt.setInt(2, portalProps.get("faction").intValue());
			stmt.setFloat(3, portalProps.get("locX"));
			stmt.setFloat(4, portalProps.get("locY"));
			stmt.setFloat(5, portalProps.get("locZ"));
			stmt.setFloat(6, portalProps.get("orientX"));
			stmt.setFloat(7, portalProps.get("orientY"));
			stmt.setFloat(8, portalProps.get("orientZ"));
			stmt.setFloat(9, portalProps.get("orientW"));
			stmt.setInt(10, portalProps.get("displayID").intValue());
			stmt.setString(11, portalName);
			stmt.setInt(12, portalProps.get("id").intValue());
			Log.debug("ISLANDDB: updating island portal with statement: " + stmt.toString());
			queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack("editPortalData Exception " + e);
					return;
		}
	}

	/***
	 * Load World Time
	 */

	public HashMap<String, Integer> LoadWorldTime() {
		String serverName = Engine.getProperty("atavism.servername");
		HashMap<String, Integer> list = new HashMap<String, Integer>();
		try {
		    try (PreparedStatement ps = queries.prepare("SELECT * FROM world_time where world_name = '" + serverName + "'")) {
    	        try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					list.put("year", rs.getInt("year"));
        					list.put("month", rs.getInt("month"));
        					list.put("day", rs.getInt("day"));
        					list.put("hour", rs.getInt("hour"));
        					list.put("minute", rs.getInt("minute"));
        					list.put("second", rs.getInt("second"));
        				}
        			}
    	        }
		    }
            if (list.size() == 0) {
                try (PreparedStatement ps = queries.prepare("INSERT INTO world_time(world_name,year,month,day,hour,minute,second) VALUES('" + serverName + "',1,1,1,0,0,0)")) {
                    queries.executeInsert(ps);
                }
            }

		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("LoadWorldTime Exception " + e);
		}
		return list;
	}

	public void saveWorldTime(Integer year, Integer month, Integer day, Integer hour, Integer minute, Integer second) {
		String serverName = Engine.getProperty("atavism.servername");
		String sql = "UPDATE world_time " + "SET year=" + year + ", month=" + month + ",day=" + day + " ,hour=" + hour + " ,minute=" + minute + " ,second=" + second + " "
				+ "WHERE world_name='" + serverName + "'";
		if (Log.loggingDebug)
			Log.debug("DB " + sql);
		try (PreparedStatement ps = queries.prepare(sql)) {
		    queries.executeUpdate(ps);
        } catch (SQLException e) {
            e.printStackTrace();
		}
	}

	/***
	 * Island Template Database Code
	 * 
	 */

	public LinkedList<HashMap<String, Serializable>> loadTemplateIslands() {
		LinkedList<HashMap<String, Serializable>> list = new LinkedList<HashMap<String, Serializable>>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM " + instanceTemplateTableName)) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					HashMap<String, Serializable> templateProps = new HashMap<String, Serializable>();
    					templateProps.put("templateID", rs.getInt("id"));
    					templateProps.put("name", HelperFunctions.readEncodedString(rs.getBytes("island_name")));
    					templateProps.put("size", rs.getInt("size"));
    					list.add(templateProps);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadTemplateIslands Exception " + e);
		}
		return list;
	}

	public HashMap<String, HashMap<String, Float>> loadIslandTemplatePortals(int templateID) {
		HashMap<String, HashMap<String, Float>> list = new HashMap<String, HashMap<String, Float>>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM templatePortals where templateID = " + templateID)) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					HashMap<String, Float> portalProps = new HashMap<String, Float>();
    					portalProps.put("id", (float) rs.getInt("id"));
    					portalProps.put("portalType", (float) rs.getInt("portalType"));
    					portalProps.put("faction", (float) rs.getInt("faction"));
    					portalProps.put("displayID", (float) rs.getInt("displayID"));
    					portalProps.put("locX", rs.getFloat("locX"));
    					portalProps.put("locY", rs.getFloat("locY"));
    					portalProps.put("locZ", rs.getFloat("locZ"));
    					portalProps.put("orientX", rs.getFloat("orientX"));
    					portalProps.put("orientY", rs.getFloat("orientY"));
    					portalProps.put("orientZ", rs.getFloat("orientZ"));
    					portalProps.put("orientW", rs.getFloat("orientW"));
    					list.put(HelperFunctions.readEncodedString(rs.getBytes("name")), portalProps);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadIslandTemplatePortals Exception " + e);
		}
		return list;
	}

	/**
	 * Inserts a new entry into the character_mail table.
	 * 
	 * @param iMailToSend
	 *            : The email to be saved in the database.
	 * @return true if success
	 * 
	 */
	public boolean addNewMail(Mail iMailToSend) {
		Log.debug("MAILING: attempting to add mail oid: " + iMailToSend.getID());
		// Insert Mail into the SQL Database
		try (PreparedStatement stmt = queries.prepare("INSERT INTO " + mailTableName + " (mailArchive, isAccountMail, recipientId, recipientName, senderId, "
					+ "senderName, mailRead, mailSubject, mailMessage, currencyType, currencyAmount, currencyTaken, CoD, expiry, "
					+ "mailAttachmentItemId1Taken, mailAttachmentItemId1, mailAttachmentItemId2Taken, mailAttachmentItemId2, "
					+ "mailAttachmentItemId3Taken, mailAttachmentItemId3, mailAttachmentItemId4Taken, mailAttachmentItemId4, "
					+ "mailAttachmentItemId5Taken, mailAttachmentItemId5, mailAttachmentItemId6Taken, mailAttachmentItemId6, "
					+ "mailAttachmentItemId7Taken, mailAttachmentItemId7, mailAttachmentItemId8Taken, mailAttachmentItemId8, "
					+ "mailAttachmentItemId9Taken, mailAttachmentItemId9, mailAttachmentItemId10Taken, mailAttachmentItemId10"
					+ ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
			stmt.setBoolean(1, iMailToSend.getMailArchive());
			stmt.setBoolean(2, iMailToSend.isAccountMail());
			stmt.setLong(3, iMailToSend.getRecipientOID().toLong());
			stmt.setString(4, iMailToSend.getRecipientName());
			if (iMailToSend.getSenderOID() != null) {
				stmt.setLong(5, iMailToSend.getSenderOID().toLong());
			} else {
				stmt.setLong(5, 0);
			}
			stmt.setString(6, iMailToSend.getSenderName());
			stmt.setBoolean(7, iMailToSend.getMailRead());
			stmt.setString(8, iMailToSend.getSubject());
			stmt.setString(9, iMailToSend.getMessage());
			stmt.setInt(10, iMailToSend.getCurrencyType());
			stmt.setLong(11, iMailToSend.getCurrencyAmount());
			stmt.setBoolean(12, false);
			stmt.setBoolean(13, iMailToSend.getCoD());
			SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");// dd/MM/yyyy
			Calendar cal = Calendar.getInstance();
			cal.setTime(new java.util.Date());
			if (iMailToSend.getCoD()) {
				cal.add(Calendar.DATE, AgisInventoryPlugin.MAIL_COD_LIFE_DAYS);
			} else {
				cal.add(Calendar.DATE, AgisInventoryPlugin.MAIL_LIFE_DAYS);
			}
			String strDate = sdfDate.format(cal.getTime());
			stmt.setTimestamp(14, Timestamp.valueOf(strDate));

			int startingNum = 15;
			for (int i = 0; i < AgisInventoryPlugin.MAIL_ATTACHMENT_COUNT; i++) {
				stmt.setBoolean(startingNum + (i * 2), false);
				if (iMailToSend.getItems() != null && iMailToSend.getItems().size() > i && iMailToSend.getItems().get(i) != null) {
					stmt.setLong(startingNum + (i * 2) + 1, iMailToSend.getItems().get(i).toLong());
				} else {
					stmt.setLong(startingNum + (i * 2) + 1, -1);
				}
			}

			int id = queries.executeInsert(stmt);
			iMailToSend.setID(id);
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("addNewMail Exception " + e);
        }
		return true;
	}

	/**
	 * Read in all Mail Item for a given CharacterOID
	 * 
	 * @param characterOID
	 * @param accountID
	 * @return
	 */
	public ArrayList<Mail> retrieveMail(OID characterOID, OID accountID) {
		try {
			ArrayList<Mail> wMails = new ArrayList<Mail>();
			Log.debug("MAIL: clean character_mail from deleted mails " );
			String deleteString = "DELETE FROM "+mailTableName+" WHERE mailArchive = 1";
			queries.executeUpdate(deleteString);
			
			Log.debug("MAIL: retrieving all mail by char oid: " + characterOID);

			// First check if any mail the user has sent has expired
            try (PreparedStatement ps = queries.prepare("SELECT * FROM " + mailTableName + " WHERE senderId=" + characterOID.toLong() + " AND mailArchive=0 AND CoD = 1 AND expiry < now()")) {
                try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					Log.debug("MAIL: found sent expired mail");
        					if(rs.getLong("recipientId")>0L) {
        					returnMail(rs.getInt("mailId"), OID.fromLong(rs.getLong("senderId")), HelperFunctions.readEncodedString(rs.getBytes("senderName")),
        							OID.fromLong(rs.getLong("recipientId")), HelperFunctions.readEncodedString(rs.getBytes("recipientName")),
        							HelperFunctions.readEncodedString(rs.getBytes("mailSubject")), rs.getBoolean("CoD"));
        					}
        				}
        			}
    			}
            }
			// Now check if the user has any expired mail in their mailbox
            try (PreparedStatement ps = queries.prepare("SELECT * FROM " + mailTableName + " WHERE recipientId=" + characterOID.toLong() + " AND mailArchive=0 AND CoD = 1 AND expiry < now()")) {
                try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					Log.debug("MAIL: found expired mail in users inbox");
        					if(rs.getLong("recipientId")>0L) {
        					returnMail(rs.getInt("mailId"), OID.fromLong(rs.getLong("senderId")), HelperFunctions.readEncodedString(rs.getBytes("senderName")),
        							OID.fromLong(rs.getLong("recipientId")), HelperFunctions.readEncodedString(rs.getBytes("recipientName")),
        							HelperFunctions.readEncodedString(rs.getBytes("mailSubject")), rs.getBoolean("CoD"));
        					}
        				}
        			}
    			}
            }

			Log.debug("MAIL: completed expired mail check");
			// Select Mail from the SQL Database using character OID
            try (PreparedStatement ps = queries.prepare("SELECT * FROM " + mailTableName + " WHERE isAccountMail=0 AND recipientId=" + characterOID.toLong() + " AND mailArchive=0")) {
                try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					wMails.add(retrieveMailData(rs));
        				}
        			}
    			}
            }
			// And finally select and Account Mail using account OID
            try (PreparedStatement ps = queries.prepare("SELECT * FROM " + mailTableName + " WHERE isAccountMail=1 AND recipientId=" + accountID.toLong() + " AND mailArchive=0")) {
                try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				while (rs.next()) {
        					wMails.add(retrieveMailData(rs));
        				}
        			}
                }
    		}
            return wMails;
        } catch (SQLException e) {
            Log.error("MAIL: got error retreiving mail: " + e.toString());
            return null;
        }
		
	}

	/**
	 * Retrieve mail form DB using mailOID
	 * 
	 * @param mailOID
	 * @return
	 */
	public Mail retrieveSingleMail(int mailOID) {
		Log.debug("MAIL: Retrieving single email by oid: " + mailOID);
		// SelectMail from SQL Database using mailOID
		String selectString = "SELECT * FROM " + mailTableName + " WHERE mailId=" + mailOID;
        try (PreparedStatement ps = queries.prepare(selectString)) {
            try (ResultSet rs = queries.executeSelect(ps)) { // Re
        		if (rs != null) {
    				if (rs.first()) {
    					Log.debug("MAIL: Found mail");
    					Mail wMail = new Mail();
    					wMail.setID(mailOID);
    					wMail.isAccountMail(rs.getBoolean("isAccountMail"));
    					wMail.setRecipientOID(getOIDFromLongInResultSet(rs, "recipientId"));
    					wMail.setRecipientName(HelperFunctions.readEncodedString(rs.getBytes("recipientName")));
    					wMail.setSenderOID(getOIDFromLongInResultSet(rs, "senderId"));
    					wMail.setSenderName(HelperFunctions.readEncodedString(rs.getBytes("senderName")));
    					wMail.setMailRead(rs.getBoolean("mailRead"));
    					wMail.setSubject(HelperFunctions.readEncodedString(rs.getBytes("mailSubject")));
    					wMail.setMessage(HelperFunctions.readEncodedString(rs.getBytes("mailMessage")));
    					wMail.setCurrencyType(rs.getInt("currencyType"));
    					if (!rs.getBoolean("currencyTaken")) {
    						wMail.setCurrencyAmount(rs.getLong("currencyAmount"));
    					} else {
    						wMail.setCurrencyAmount(0);
    					}
    					wMail.setCoD(rs.getBoolean("CoD"));
    					Log.debug("MAIL: Retrieving attachment data");
    					// Add email Attachments
    					ArrayList<OID> attachments = new ArrayList<OID>();
    					for (int i = 1; i <= AgisInventoryPlugin.MAIL_ATTACHMENT_COUNT; i++) {
    						Long itemOid = rs.getLong("mailAttachmentItemId" + i);
    						if (itemOid != null && itemOid > 0 && !rs.getBoolean("mailAttachmentItemId" + i + "Taken")) {
    							attachments.add(OID.fromLong(itemOid));
    						} else {
    							attachments.add(null);
    						}
    					}
    					wMail.setItems(attachments);
    					Log.debug("MAIL: Added mail: " + mailOID);
    					return wMail;
    				}
        		}
    		}
        } catch (SQLException e) {
            e.printStackTrace();
            Log.dumpStack("retrieveSingleMail Exception " + e);
        }
		return null;
	}
	
	
	public Mail retrieveMailData(ResultSet rs) {
		Log.debug("MAIL: Found mail");
		Mail wMail = new Mail();
		try {
			wMail.setID(rs.getInt("mailId"));
			wMail.isAccountMail(rs.getBoolean("isAccountMail"));
			wMail.setRecipientOID(getOIDFromLongInResultSet(rs, "recipientId"));
			wMail.setRecipientName(HelperFunctions.readEncodedString(rs.getBytes("recipientName")));
			wMail.setSenderOID(getOIDFromLongInResultSet(rs, "senderId"));
			wMail.setSenderName(HelperFunctions.readEncodedString(rs.getBytes("senderName")));
			wMail.setMailRead(rs.getBoolean("mailRead"));
			wMail.setSubject(HelperFunctions.readEncodedString(rs.getBytes("mailSubject")));
			wMail.setMessage(HelperFunctions.readEncodedString(rs.getBytes("mailMessage")));
			wMail.setCurrencyType(rs.getInt("currencyType"));
			if (!rs.getBoolean("currencyTaken")) {
				wMail.setCurrencyAmount(rs.getLong("currencyAmount"));
			} else {
				wMail.setCurrencyAmount(0L);
			}
			wMail.setCoD(rs.getBoolean("CoD"));
			Log.debug("MAIL: Retrieving attachment data");
			// Add email Attachments
			ArrayList<OID> attachments = new ArrayList<OID>();
			for (int i = 1; i <= AgisInventoryPlugin.MAIL_ATTACHMENT_COUNT; i++) {
				Long itemOid = rs.getLong("mailAttachmentItemId" + i);
				if (itemOid != null && itemOid > 0 && !rs.getBoolean("mailAttachmentItemId" + i + "Taken")) {
					attachments.add(OID.fromLong(itemOid));
				} else {
					attachments.add(null);
				}
			}
			wMail.setItems(attachments);

		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("retrieveSingleMail Exception " + e);
		}
		return wMail;
	}
	
	

	/**
	 * Returns the mail back to the sender
	 * 
	 * @param mailID
	 * @param senderOid
	 * @param senderName
	 * @param recipientOid
	 * @param recipientName
	 * @param subject
	 * @param removeCurrency
	 * @return
	 */
	public boolean returnMail(int mailID, OID senderOid, String senderName, OID recipientOid, String recipientName, String subject, boolean removeCurrency) {
		Log.debug("MAILING: Deleting mail by oid: " + mailID);
		// Switch around the Sender and Recipient Mail in the SQL Database using mailOID
		try (PreparedStatement stmt = queries.prepare(
					"UPDATE " + mailTableName + " set recipientId=?, recipientName=?, senderId=?, " + "senderName=?, mailSubject=?, expiry=?, currencyTaken=?, CoD=? where mailId=?")) {
			stmt.setLong(1, senderOid != null ? senderOid.toLong() : 0L);
			stmt.setString(2, senderName);
			stmt.setLong(3, recipientOid.toLong());
			stmt.setString(4, recipientName);
			if (!subject.startsWith("Returned")) {
				stmt.setString(5, "Returned: " + subject);
			} else {
				stmt.setString(5, subject);
			}

			Calendar cal = Calendar.getInstance();
			cal.setTime(new java.util.Date());
			cal.add(Calendar.DATE, AgisInventoryPlugin.MAIL_LIFE_DAYS);
			SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");// dd/MM/yyyy
			String strDate = sdfDate.format(cal.getTime());
			stmt.setTimestamp(6, Timestamp.valueOf(strDate));
			stmt.setBoolean(7, removeCurrency);
			stmt.setBoolean(8, false);
			stmt.setInt(9, mailID);
			Log.debug("MAILING: returning mail stmt=" + stmt.toString());
			queries.executeUpdate(stmt);
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("returnMail Exception " + e);
		} catch (Exception e) {
			e.printStackTrace();
			Log.dumpStack("returnMail Exception " + e);
		}
		return true;
	}

	/**
	 * Set the mail to deleted (archived) using mailOID
	 * 
	 * @param mailOID
	 * @return
	 */
	public boolean deleteMail(int mailOID) {
		Log.debug("MAILING: Deleting mail by oid: " + mailOID);
		// Delete Mail from SQL Database using mailOID (modified to ArchiveMail)
		String updateString = "UPDATE " + mailTableName + " SET mailArchive=1 WHERE mailId=" + mailOID;
		queries.executeUpdate(updateString);
		
		return true;
	}

	/**
	 * Set mail status to read using mailOID
	 * 
	 * @param mailOID
	 * @return
	 */
	public boolean readMail(int mailOID) {
		Log.debug("MAILING: putting status read to: " + mailOID);

		// Putting read Mail from SQL Database using mailOID
		String updateString = "UPDATE " + mailTableName + " SET mailRead=1 WHERE mailId=" + mailOID;
		queries.executeUpdate(updateString);
		return true;
	}

	/**
	 * Set mail attachment to taken using mailOID and the item position
	 * 
	 * @param mailOID
	 * @param itemPos
	 * @param CoD 
	 *            if true, CoD is set to false and currencyTaken is set to true
	 * @return
	 */
	public boolean takeMailItem(int mailOID, int itemPos, boolean CoD) {
		Log.debug("MAILING: taking mail item: " + mailOID);

		// Putting read Mail from SQL Database using mailOID
		if (CoD) {
			String updateString = "UPDATE " + mailTableName + " SET mailAttachmentItemId" + (itemPos + 1) + "Taken=1, CoD=0, currencyTaken=1 WHERE mailId=" + mailOID;
			queries.executeUpdate(updateString);
		} else {
			String updateString = "UPDATE " + mailTableName + " SET mailAttachmentItemId" + (itemPos + 1) + "Taken=1 WHERE mailId=" + mailOID;
			queries.executeUpdate(updateString);
		}
		return true;
	}

	/**
	 * Set mail attachment to taken using mailOID and the item position
	 * 
	 * @param mailOID
	 * @return
	 */
	public boolean takeMailCurrency(int mailOID) {
		Log.debug("MAILING: setting currency to 0 for mail: " + mailOID);

		// Putting read Mail from SQL Database using mailOID
		String updateString = "UPDATE " + mailTableName + " SET currencyTaken=1 WHERE mailId=" + mailOID;
		queries.executeUpdate(updateString);
		return true;
	}

	public HashMap<Integer, Claim> loadClaims(int instanceID) {
		return loadClaims(instanceID, 0, -1,false);
	}

	/*
	 * Claim Functions
	 */
	public HashMap<Integer, Claim> loadClaims(int instanceID, long playerOid, long guildOid ,boolean onlychildren) {
		HashMap<Integer, Claim> claims = new HashMap<Integer, Claim>();
		String sql ="SELECT * FROM `claim` where instance=? AND isactive = 1 AND instanceOwner = ? AND instanceGuild = ? ";
		if(onlychildren)
			sql +=" AND parent > -1";
		else
			sql +=" AND parent = -1";
				
		try (PreparedStatement ps = queries.prepare(sql)) {
			ps.setInt(1, instanceID);
			ps.setLong(2, playerOid);
			ps.setLong(3, guildOid);

			Log.debug("GRID: " + ps.toString());
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Claim bg = new Claim();
    					bg.setID(rs.getInt("id"));
    					bg.setName(HelperFunctions.readEncodedString(rs.getBytes("name")));
    					AOVector p = new AOVector();
    					p.setX(rs.getFloat("locX"));
    					p.setY(rs.getFloat("locY"));
    					p.setZ(rs.getFloat("locZ"));
    					bg.setLoc(p);
    					bg.setUpgrade(rs.getInt("upgrade"));
    					bg.setSize(rs.getInt("size"));
    					bg.setSizeY(rs.getInt("sizeY"));
    					bg.setSizeZ(rs.getInt("sizeZ"));
    					bg.setClaimType(rs.getInt("claimType"));
    					Long oid = rs.getLong("owner");
    					if (oid == null || oid == -1l) {
    						bg.setOwner(null);
    					} else {
    						bg.setOwner((OID.fromLong(oid)));
    					}
    					bg.setInstanceOwner(rs.getLong("instanceOwner"));
    					bg.setInstanceGuild(rs.getLong("instanceGuild"));
    					bg.setForSale(rs.getBoolean("forSale"));
    					bg.setPermanent(rs.getBoolean("permanent"));
    					bg.setCost(rs.getLong("cost"));
    					bg.setOrgCost(rs.getLong("org_cost"));
    					bg.setOrgCurrency(rs.getInt("org_currency"));
    					bg.setCurrency(rs.getInt("currency"));
    					bg.setSellerName(rs.getString("sellerName"));
    					bg.setClaimItemTemplate(rs.getInt("claimItemTemplate"));
    					bg.setBondItemTemplate(rs.getInt("bondItemTemplate"));
    					bg.setParentId(rs.getInt("parent"));
    					bg.setProfile(rs.getInt("object_limit_profile"));
    					int taxCurrency = rs.getInt("taxCurrency");
    					long taxAmount = rs.getLong("taxAmount");
    					long taxInterval = rs.getLong("taxInterval");
    					long taxPeriodPay = rs.getLong("taxPeriodPay");
    					long taxPeriodSell = rs.getLong("taxPeriodSell");
    					bg.setTaxCurrency(taxCurrency);
    					bg.setTaxAmount(taxAmount);
    					bg.setTaxInterval(taxInterval);
    					bg.setTaxPeriodPay(taxPeriodPay);
    					bg.setTaxPeriodSell(taxPeriodSell);
    					
    					
    					
    					
    					String purchaseItemReqs = HelperFunctions.readEncodedString(rs.getBytes("purchaseItemReq"));
    					if (purchaseItemReqs != null) {
    						for (String purchaseItemReq : purchaseItemReqs.split(",")) {
    							if (!purchaseItemReq.isEmpty())
    								bg.addPurchaseItemReq(Integer.parseInt(purchaseItemReq));
    						}
    					}
    					// bg.setPurchaseItemReq(rs.getInt("purchaseItemReq")); // Comment this out for
    					// pre 2.7
    
    					// Load in claim tax due
    					Timestamp taxPaidUntil = rs.getTimestamp("taxPaidUntil");
    					if (taxPaidUntil != null) {
    						bg.setTaxPaidUntil(taxPaidUntil.getTime());
    					}
    
    					// Load in claim tax before bond
    					Timestamp bondPaidUntil = rs.getTimestamp("bondPaidUntil");
    					if (bondPaidUntil != null) {
    						bg.setBondPaidUntil(bondPaidUntil.getTime());
    					}
    
    					bg.setInstanceID(instanceID);
    					bg.setAccountDatabase(this);
    					claims.put(bg.getID(), bg);
    					// Log.debug("GRID: added grid " + bg.getID() + " to map");
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadClaims Exception " + e);
		}

		for (Claim bg : claims.values()) {
			loadClaimUpgrade(bg);
			loadClaimActions(bg);
			loadClaimObjects(bg);
			if (VoxelPlugin.USE_CLAIM_RESOURCES)
				loadClaimResources(bg);
			loadClaimPermissions(bg);
		}

		return claims;
	}
	
	/**
	 * Loading Claim Upgrades for Claim from database
	 * @param claim
	 */
	public void loadClaimUpgrade(Claim claim) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `claim_upgrade` where claimID = " + claim.getID())) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					String purchaseItemReq = HelperFunctions.readEncodedString(rs.getBytes("purchaseItemReq"));
    					float locX = rs.getFloat("locX");
    					float locY = rs.getFloat("locY");
    					float locZ = rs.getFloat("locZ");
    					int sizeX = rs.getInt("sizeX");
    					int sizeY = rs.getInt("sizeY");
    					int sizeZ = rs.getInt("sizeZ");
    					
    					long cost = rs.getLong("cost");
    					int currency = rs.getInt("currency");
    					int profile = rs.getInt("object_limit_profile");
    					int taxCurrency = rs.getInt("taxCurrency");
    					long taxAmount = rs.getLong("taxAmount");
    					long taxInterval = rs.getLong("taxInterval");
    					long taxPeriodPay = rs.getLong("taxPeriodPay");
    					long taxPeriodSell = rs.getLong("taxPeriodSell");
    					
    					claim.AddUpgradeData(id, purchaseItemReq,  new AOVector(sizeX, sizeY, sizeZ), new AOVector(locX, locY, locZ),cost, currency,profile,taxCurrency,taxAmount,taxInterval,taxPeriodPay,taxPeriodSell);
    				}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadClaimUpgrade Exception " + e);
		}
	}
	/**
	 * Loading Claim Action for Claim from database
	 * @param claim
	 */
	public void loadClaimActions(Claim claim) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `claim_action` where claimID = " + claim.getID())) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					String action = HelperFunctions.readEncodedString(rs.getBytes("action"));
    					String brushType = HelperFunctions.readEncodedString(rs.getBytes("brushType"));
    					float locX = rs.getFloat("locX");
    					float locY = rs.getFloat("locY");
    					float locZ = rs.getFloat("locZ");
    					float normalX = rs.getFloat("normalX");
    					float normalY = rs.getFloat("normalY");
    					float normalZ = rs.getFloat("normalZ");
    					int material = rs.getShort("material");
    					int sizeX = rs.getInt("sizeX");
    					int sizeY = rs.getInt("sizeY");
    					int sizeZ = rs.getInt("sizeZ");
    					claim.AddActionData(id, action, brushType, new AOVector(sizeX, sizeY, sizeZ), new AOVector(locX, locY, locZ), new AOVector(normalX, normalY, normalZ), material);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadClaimActions Exception " + e);
		}
	}
	
	/**
	 * Get count of the claim objects
	 * @return
	 */
	public int getCountClaimObjects() {
		int count = 0;
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM claim_object where isactive=1")) {
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
	 * Loading Claim Objects for Claim from database
	 * @param claim
	 */
	public void loadClaimObjects(Claim claim) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `claim_object` where claimID = " + claim.getID())) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					int templateId = rs.getInt("template");
    					int stage = rs.getInt("stage");
    					String gameObject = HelperFunctions.readEncodedString(rs.getBytes("gameObject"));
    					float locX = rs.getFloat("locX");
    					float locY = rs.getFloat("locY");
    					float locZ = rs.getFloat("locZ");
    					float orientX = rs.getFloat("orientX");
    					float orientY = rs.getFloat("orientY");
    					float orientZ = rs.getFloat("orientZ");
    					float orientW = rs.getFloat("orientW");
    					int itemID = rs.getInt("itemID");
    					String state = HelperFunctions.readEncodedString(rs.getBytes("objectState"));
    					boolean complete = rs.getBoolean("complete");
    					int parent = rs.getInt("parent");
    					String parents = HelperFunctions.readEncodedString(rs.getBytes("parents"));
    					int health = rs.getInt("health");
    					int maxHealth = rs.getInt("maxHealth");
    					
    					
    					
    					HashMap<Integer, Integer> itemCounts = new HashMap<Integer, Integer>();
    					for (int i = 1; i <= 6; i++) {
    						Integer itemId = rs.getInt("item" + i);
    						Integer itemCount = rs.getInt("item" + i + "Count");
    						if (itemId != null && itemId > 0 && itemCount != null && itemCount > 0) {
    							itemCounts.put(itemId, itemCount);
    						}
    					}
    					int lockTemplateID = rs.getInt("lockTemplateID");
    					int lockDurability = rs.getInt("lockDurability");
    					int progress = rs.getInt("progress");
    					boolean finalStage = rs.getBoolean("finalStage");
    					long taskCurrentTime = rs.getLong("taskCurrentTime");
    					long taskLastTimeUpdate = rs.getLong("taskLastTimeUpdate");
    					int ownerStat = rs.getInt("ownerStat");
    					
    					long taskPlayerOid = rs.getLong("taskPlayerOid");
    					OID playerOid = OID.fromLong(taskPlayerOid);
    					
    					
    					claim.AddClaimObject(id, templateId, stage, complete, parent, parents, gameObject, new AOVector(locX, locY, locZ), new Quaternion(orientX, orientY, orientZ, orientW), itemID,
    							state, health, maxHealth, itemCounts, lockTemplateID, lockDurability,progress,finalStage,taskCurrentTime,taskLastTimeUpdate,ownerStat,playerOid);
    				}
    			}
	        }
			
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadClaimObjects Exception " + e);
		}
	}

	
	
	
	public void loadClaimResources(Claim claim) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `claim_resource` where claimID = " + claim.getID())) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					int itemID = rs.getInt("itemID");
    					int count = rs.getInt("count");
    					claim.AddClaimResource(id, itemID, count);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadClaimResources Exception " + e);
		}
	}

	public void loadClaimPermissions(Claim claim) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM `claim_permission` where claimID = " + claim.getID())) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					OID playerOid = OID.fromLong(rs.getLong("playerOid"));
    					String playerName = HelperFunctions.readEncodedString(rs.getBytes("playerName"));
    					int permissionLevel = rs.getInt("permissionLevel");
    					claim.AddClaimPermission(playerOid, playerName, permissionLevel);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadClaimPermissions Exception " + e);
		}
	}

	public int writeClaim(Claim claim, int instanceID, long playerOid, long guildOid) {
		Log.debug("Writing claim data to database");
		// Set up the displays
		int inserted = -1;
		String tableName = "claim";
		String columnNames = "instance,locX,locY,locZ,owner,size,sizeZ,forSale,cost,currency,sellerName,name,claimItemTemplate,priority,bondItemTemplate,instanceOwner,instanceGuild,claimtype,"
				+ " taxPaidUntil, bondPaidUntil,parent,org_cost,org_currency, sizeY, taxCurrency, taxAmount, taxInterval, taxPeriodPay, taxPeriodSell";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
			stmt.setInt(1, instanceID);
			stmt.setFloat(2, claim.getLoc().getX());
			stmt.setFloat(3, claim.getLoc().getY());
			stmt.setFloat(4, claim.getLoc().getZ());
			if (claim.getOwner() != null) {
				stmt.setLong(5, claim.getOwner().toLong());
			} else {
				stmt.setLong(5, 0);
			}
			stmt.setInt(6, claim.getSize());
			stmt.setInt(7, claim.getSize());
			stmt.setBoolean(8, claim.getForSale());
			stmt.setLong(9, claim.getCost());
			stmt.setInt(10, claim.getCurrency());
			stmt.setString(11, claim.getSellerName());
			stmt.setString(12, claim.getName());
			stmt.setInt(13, claim.getClaimItemTemplate());
			stmt.setInt(14, claim.getPriority());
			stmt.setInt(15, claim.getBondItemTemplate());
			stmt.setLong(16, playerOid);
			stmt.setLong(17, guildOid);
			stmt.setLong(18, claim.getClaimType());
			// stmt.setLong(19, claim.getTaxPaidUntil());
			// stmt.setLong(20, claim.getBondPaidUntil());
			SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(claim.getTaxPaidUntil());
			String strDate = sdfDate.format(cal.getTime());
			stmt.setTimestamp(19, Timestamp.valueOf(strDate));
			cal.setTimeInMillis(claim.getBondPaidUntil());
			strDate = sdfDate.format(cal.getTime());
			stmt.setTimestamp(20, Timestamp.valueOf(strDate));
			stmt.setInt(21, claim.getParentId());
			stmt.setLong(22, claim.getOrgCost());
			stmt.setInt(23, claim.getOrgCurrency());
			stmt.setInt(24, claim.getSize());

			stmt.setInt(25, claim.getTaxCurrency());
			stmt.setLong(26, claim.getTaxAmount());
			stmt.setLong(27, claim.getTaxInterval());
			stmt.setLong(28, claim.getTaxPeriodPay());
			stmt.setLong(29, claim.getTaxPeriodSell());
			
			inserted = queries.executeInsert(stmt);
			claim.setID(inserted);
		} catch (SQLException e) {
			Log.dumpStack("writeClaim Exception " + e);
		}
		Log.debug("Wrote claim data to database");
		return inserted;
	}

	public int updateClaim(Claim claim) {
		Log.debug("Updating claim data to database");
		String tableName = "claim";
		int updated;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set name=?, owner=?, forSale=?, "
					+ "cost=?, currency=?, sellerName=?, taxPaidUntil=?, bondPaidUntil=?, bondItemTemplate=?, claimItemTemplate=?,upgrade=? where id=?")) {
			stmt.setString(1, claim.getName());
			if (claim.getOwner() != null) {
				stmt.setLong(2, claim.getOwner().toLong());
			} else {
				stmt.setLong(2, -1);
			}

			stmt.setBoolean(3, claim.getForSale());
			stmt.setLong(4, claim.getCost());
			stmt.setInt(5, claim.getCurrency());
			stmt.setString(6, claim.getSellerName());
			
			/*SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(claim.getTaxPaidUntil());
			String strDate = sdfDate.format(cal.getTime());
			stmt.setTimestamp(7, Timestamp.valueOf(strDate));*/
			stmt.setTimestamp(7, new Timestamp(claim.getTaxPaidUntil()));
			/*	cal.setTimeInMillis(claim.getBondPaidUntil());
			strDate = sdfDate.format(cal.getTime());*/
			//stmt.setTimestamp(8, Timestamp.valueOf(strDate));
			stmt.setTimestamp(8, new Timestamp(claim.getBondPaidUntil()));
			stmt.setInt(9, claim.getBondItemTemplate());
			stmt.setInt(10, claim.getClaimItemTemplate());
			stmt.setInt(11, claim.getUpgrade());
			stmt.setInt(12, claim.getID());
			Log.debug("CONTENTDB: updating claim with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack("updateClaim Exception " + e);
			return -1;
		}
		Log.debug("Wrote claim data to database");
		return updated;
	}

	public int deleteClaim(int claimID) {
		String tableName = "claim";
		String deleteString = "UPDATE  `" + tableName + "` SET isactive = 0 WHERE id = " + claimID;
		int deleted = queries.executeUpdate(deleteString);
		// Also delete all associated actions and objects
		tableName = "claim_action";
		deleteString = "UPDATE `" + tableName + "` SET isactive = 0 WHERE claimID = " + claimID;
		deleted = queries.executeUpdate(deleteString);
		tableName = "claim_object";
		deleteString = "UPDATE `" + tableName + "` SET isactive = 0 WHERE claimID = " + claimID;
		deleted = queries.executeUpdate(deleteString);
		return deleted;
	}

	public boolean checkGuildBuyClaim(int claimId, int guildId) {
		
	    try (PreparedStatement ps = queries.prepare("SELECT id FROM `claim` where instanceGuild = " + guildId+" AND parent ="+claimId+" AND isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return true;
    				}
    			}
	        }
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("checkGuildBuyClaim Exception " + e);
		}
		return false;
	}
	
	public int writeClaimAction(int claimID, String action, String brushType, AOVector size, AOVector loc, AOVector normal, int material) {
		Log.debug("Writing claim data to database");
		int inserted = -1;
		String tableName = "claim_action";
		String columnNames = "claimID,action,brushType,locX,locY,locZ,material,normalX,normalY,normalZ,sizeX,sizeY,sizeZ";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
			stmt.setInt(1, claimID);
			stmt.setString(2, action);
			stmt.setString(3, brushType);
			stmt.setFloat(4, loc.getX());
			stmt.setFloat(5, loc.getY());
			stmt.setFloat(6, loc.getZ());
			stmt.setInt(7, material);
			stmt.setFloat(8, normal.getX());
			stmt.setFloat(9, normal.getY());
			stmt.setFloat(10, normal.getZ());
			stmt.setInt(11, (int) size.getX());
			stmt.setInt(12, (int) size.getY());
			stmt.setInt(13, (int) size.getZ());
			inserted = queries.executeInsert(stmt);
		} catch (SQLException e) {
			Log.dumpStack("writeClaimAction Exception " + e);
		}
		Log.debug("Wrote claim data to database");
		return inserted;
	}

	public void deleteClaimAction(int spawnID) {
		String tableName = "claim_action";
		String deleteString = "DELETE FROM `" + tableName + "` WHERE id = " + spawnID;
		queries.executeUpdate(deleteString);
	}

	public int writeClaimObject(int claimID, int templateId, int stage, boolean complete, int parent, String gameObject, AOVector loc, Quaternion orient, int itemID, String state,
			int health, int maxHealth, HashMap<Integer, Integer> itemCounts, int lockTemplateId, String parents, long taskLastTimeUpdate, OID playerOid) {
		Log.debug("Writing claim data to database");
		int inserted = -1;
		String tableName = "claim_object";
		String columnNames = "claimID,template,stage,complete,parent,gameObject,locX,locY,locZ,orientX,orientY,orientZ,orientW,itemID,objectState,"
				+ "health,maxHealth,item1,item1Count,item2,item2Count,item3,item3Count,item4,item4Count,item5,item5Count,item6,item6Count,lockTemplateID,parents,taskLastTimeUpdate,taskPlayerOid";
		try (PreparedStatement stmt = queries
					.prepare("INSERT INTO " + tableName + " (" + columnNames + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?)")) {
			stmt.setInt(1, claimID);
			stmt.setInt(2, templateId);
			stmt.setInt(3, stage);
			stmt.setBoolean(4, complete);
			stmt.setInt(5, parent);
			stmt.setString(6, gameObject);
			stmt.setFloat(7, loc.getX());
			stmt.setFloat(8, loc.getY());
			stmt.setFloat(9, loc.getZ());
			stmt.setFloat(10, orient.getX());
			stmt.setFloat(11, orient.getY());
			stmt.setFloat(12, orient.getZ());
			stmt.setFloat(13, orient.getW());
			stmt.setInt(14, itemID);
			stmt.setString(15, state);
			stmt.setInt(16, health);
			stmt.setInt(17, maxHealth);
			int parameterIndex = 18;
			for (int item : itemCounts.keySet()) {
				stmt.setInt(parameterIndex, item);
				parameterIndex++;
				stmt.setInt(parameterIndex, itemCounts.get(item));
				parameterIndex++;
			}
			// Fill in the missing slots
			for (int i = itemCounts.size(); i < 6; i++) {
				stmt.setInt(parameterIndex, -1);
				parameterIndex++;
				stmt.setInt(parameterIndex, -1);
				parameterIndex++;
			}
			stmt.setInt(parameterIndex, lockTemplateId);
			stmt.setString(31, parents);
			stmt.setLong(32, taskLastTimeUpdate);
			if(playerOid!=null) {
				stmt.setLong(33, playerOid.toLong());
			}else {
				stmt.setLong(33, 0L);
				
			}
			inserted = queries.executeInsert(stmt);
		} catch (SQLException e) {
			Log.dumpStack("writeClaimObject Exception " + e);
		}
		Log.debug("Wrote claim data to database");
		return inserted;
	}

	public int updateClaimObjectPosition(int id, AOVector loc, Quaternion orient, int parent) {
		Log.debug("Updating claim object in database");
		String tableName = "claim_object";
		int updated;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set locX=?,locY=?,locZ=?,orientX=?,orientY=?,orientZ=?,orientW=?,parent=? where id=?")) {
			stmt.setFloat(1, loc.getX());
			stmt.setFloat(2, loc.getY());
			stmt.setFloat(3, loc.getZ());
			stmt.setFloat(4, orient.getX());
			stmt.setFloat(5, orient.getY());
			stmt.setFloat(6, orient.getZ());
			stmt.setFloat(7, orient.getW());
			stmt.setInt(8, parent);
			stmt.setInt(9, id);
			Log.debug("CONTENTDB: updating claim resource with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack("updateClaimObjectPosition Exception " + e);
				return -1;
		}
		Log.debug("Wrote claim data to database");
		return updated;
	}
	
	public int updateClaimObjectProgress(int id,  boolean complete, int progress, String gameObject, boolean finalStage,long taskCurrentTime,long taskLastTimeUpdate,int ownerStat, OID playerOid) {
		Log.debug("Updating claim object progress in database");
		String tableName = "claim_object";
		int updated;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set  complete=?, progress=?, gameObject=? , finalStage=?, taskCurrentTime=?, taskLastTimeUpdate=?, ownerStat=?, taskPlayerOid=? where id=?")) {
			stmt.setBoolean(1, complete);
			stmt.setInt(2, progress);
			stmt.setString(3, gameObject);
			stmt.setBoolean(4, finalStage);
			stmt.setLong(5, taskCurrentTime);
			stmt.setLong(6, taskLastTimeUpdate);
			stmt.setInt(7, ownerStat);
			if(playerOid!=null)
				stmt.setLong(8, playerOid.toLong());
			else
				stmt.setLong(8, 0L);
			stmt.setInt(9, id);

			Log.debug("CONTENTDB: updating claim object with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack("updateClaimObjectProgress Exception " + e);
				return -1;
		}
		Log.debug("Wrote claim data to database");
		return updated;
	}
	
	public int updateClaimObjectHealth(int id, int health, int maxHealth) {
		Log.debug("updateClaimObjectHealth in database");
		String tableName = "claim_object";
		int updated;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set  health=?, maxHealth=? where id=?")) {
			stmt.setInt(1, health);
			stmt.setInt(2, maxHealth);
			stmt.setInt(3, id);

			Log.debug("CONTENTDB: updating claim object with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack("updateClaimObjectHealth Exception " + e);
				return -1;
		}
		Log.debug("Wrote claim data to database");
		return updated;
	}

	public int updateClaimObjectState(int id, int templateID, int stage, boolean complete, String state, String gameObject, int health, int maxHealth,
			HashMap<Integer, Integer> itemCounts) {
		Log.debug("Updating claim object state in database");
		String tableName = "claim_object";
		int updated;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set template=?, stage=?, complete=?, objectState=?, "
					+ "gameObject=?, health=?, maxHealth=?, item1=?, item1Count=?, item2=?, item2Count=?, item3=?, item3Count=?, "
					+ "item4=?, item4Count=?, item5=?, item5Count=?, item6=?, item6Count=? where id=?")) {
			stmt.setInt(1, templateID);
			stmt.setInt(2, stage);
			stmt.setBoolean(3, complete);
			stmt.setString(4, state);
			stmt.setString(5, gameObject);
			stmt.setInt(6, health);
			stmt.setInt(7, maxHealth);
			int parameterIndex = 8;
			for (int item : itemCounts.keySet()) {
				stmt.setInt(parameterIndex, item);
				parameterIndex++;
				stmt.setInt(parameterIndex, itemCounts.get(item));
				parameterIndex++;
			}
			// Fill in the missing slots
			for (int i = itemCounts.size(); i < 6; i++) {
				stmt.setInt(parameterIndex, -1);
				parameterIndex++;
				stmt.setInt(parameterIndex, -1);
				parameterIndex++;
			}
			stmt.setInt(20, id);

			Log.debug("CONTENTDB: updating claim object with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack("updateClaimObjectState Exception " + e);
			return -1;
		}
		Log.debug("Wrote claim data to database");
		return updated;
	}

	public void deleteClaimObject(int objectID) {
		String tableName = "claim_object";
		String deleteString = "DELETE FROM `" + tableName + "` WHERE id = " + objectID;
		queries.executeUpdate(deleteString);
	}

	public int writeClaimResource(int claimID, int itemID, int count) {
		Log.debug("Writing claim data to database");
		int inserted = -1;
		String tableName = "claim_resource";
		String columnNames = "claimID,itemID,count";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames + ") values (?, ?, ?)")) {
			stmt.setInt(1, claimID);
			stmt.setInt(2, itemID);
			stmt.setInt(3, count);
			inserted = queries.executeInsert(stmt);
		} catch (SQLException e) {
			Log.dumpStack("writeClaimResource Exception " + e);
		}
		Log.debug("Wrote claim data to database");
		return inserted;
	}

	public int updateClaimResource(int id, int itemID, int count) {
		Log.debug("Writing claim data to database");
		String tableName = "claim_resource";
		int updated;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set count=? where id=?")) {
			stmt.setInt(1, count);
			stmt.setInt(2, id);
			Log.debug("CONTENTDB: updating claim resource with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack("updateClaimResource Exception " + e);
			return -1;
		}
		Log.debug("Wrote claim data to database");
		return updated;
	}

	public int writeClaimPermission(int claimID, OID playerOid, String playerName, int permissionLevel) {
		Log.debug("Writing claim data to database");
		int inserted = -1;
		String tableName = "claim_permission";
		String columnNames = "claimID,playerOid,playerName,permissionLevel";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames + ") values (?, ?, ?, ?)")) {
			stmt.setInt(1, claimID);
			stmt.setLong(2, playerOid.toLong());
			stmt.setString(3, playerName);
			stmt.setInt(4, permissionLevel);
			inserted = queries.executeInsert(stmt);
		} catch (SQLException e) {
			Log.dumpStack("writeClaimPermission Exception " + e);
		}
		Log.debug("Wrote claim data to database");
		return inserted;
	}

	public int updateClaimPermission(int claimID, OID playerOid, int permissionLevel) {
		Log.debug("Writing claim data to database");
		String tableName = "claim_permission";
		int updated;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set permissionLevel=? where claimID=? AND playerOid=?")) {
			stmt.setInt(1, permissionLevel);
			stmt.setInt(2, claimID);
			stmt.setLong(3, playerOid.toLong());
			Log.debug("CONTENTDB: updating claim resource with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack("updateClaimPermission Exception " + e);
			return -1;
		}
		Log.debug("Wrote claim data to database");
		return updated;
	}

	public void deleteClaimPermission(int claimID, OID playerOid) {
		String tableName = "claim_permission";
		String deleteString = "DELETE FROM `" + tableName + "` WHERE claimID = " + claimID + " AND playerOid = " + playerOid.toLong();
		queries.executeUpdate(deleteString);
	}

	/*
	 * Guild Functions
	 */

	public int GetGuildId(OID playerOid) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM guild_member WHERE memberOid = " + playerOid.toLong())) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return rs.getInt("guildID");
    				}
    			}
	        }
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("GetGuildId Exception " + e);
		}
		return -1;
	}
	
	public int GetGuildLevel(OID playerOid) {
	    try (PreparedStatement ps = queries.prepare("SELECT g.level FROM guild as g JOIN guild_member as gm ON  g.id = gm.guildID where gm.memberOid = " + playerOid.toLong())) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return rs.getInt("level");
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("GetGuildLevel Exception " + e);
		}
		return -1;
	}

	public Set<OID> getGuildMambersOid(int guildId){
		Set<OID> list = new HashSet<OID>();
	    try (PreparedStatement ps = queries.prepare("SELECT memberOid FROM guild_member WHERE guildID = " + guildId)) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					OID oid = OID.fromLong(rs.getLong("memberOid"));
    					if(oid!=null) {
    						list.add(oid);
    					}
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("getGuildMambersOid Exception " + e);
		}
		return list;
	}
	
	public HashMap<Integer, Guild> loadGuildData() {
		HashMap<Integer, Guild> list = new HashMap<Integer, Guild>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM guild where isactive = 1")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					Guild guild = new Guild();
    					guild.setAccountDatabase(this);
    					guild.setGuildID(rs.getInt("id"));
    					guild.setGuildName(HelperFunctions.readEncodedString(rs.getBytes("name")));
    					guild.setFaction(rs.getInt("faction"));
    					guild.setWarehouse(OID.fromLong(rs.getLong("warehouse")));
    					guild.setLevel(rs.getInt("level"));
    					
    					guild.setMOTD(HelperFunctions.readEncodedString(rs.getBytes("motd")));
    					guild.setOMOTD(HelperFunctions.readEncodedString(rs.getBytes("omotd")));
    					list.put(guild.getGuildID(), guild);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadGuildData Exception " + e);
		}

		// load in all guild ranks and members
		loadGuildRanks(list);
		loadGuildMembers(list);
		loadGuildResourcess(list);
		return list;
	}

	public void loadGuildRanks(HashMap<Integer, Guild> guilds) {
		try (PreparedStatement ps = queries.prepare("SELECT * FROM guild_rank")) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int rankID = rs.getInt("id");
    					int guildID = rs.getInt("guildID");
    					int rank = rs.getInt("guildRank");
    					String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					String permissionsString = HelperFunctions.readEncodedString(rs.getBytes("permissions"));
    					ArrayList<String> permissions = new ArrayList<String>();
    					if (permissionsString.length() > 0)
    						for (String permission : permissionsString.split(",")) {
    							permissions.add(permission);
    						}
    					if (guilds.containsKey(guildID))
    						guilds.get(guildID).addRank(rankID, name, rank, permissions);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadGuildRanks Exception " + e);
		}
	}

	public void loadGuildMembers(HashMap<Integer, Guild> guilds) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM guild_member")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int memberID = rs.getInt("id");
    					int guildID = rs.getInt("guildID");
    					OID memberOid = OID.fromLong(rs.getLong("memberOid"));
    					String name = HelperFunctions.readEncodedString(rs.getBytes("name"));
    					int rank = rs.getInt("guildRank");
    					int level = rs.getInt("level");
    					String note = HelperFunctions.readEncodedString(rs.getBytes("note"));
    
    					if (guilds.containsKey(guildID))
    						guilds.get(guildID).addMember(memberID, memberOid, name, rank, level, note);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadGuildMembers Exception " + e);
		}
	}
	
	public void loadGuildResourcess(HashMap<Integer, Guild> guilds) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM guild_level_resources")) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					int id = rs.getInt("id");
    					int guildId = rs.getInt("guild_id");
    					int itemId = rs.getInt("item_id");
    					int itemCount = rs.getInt("item_count");
    
    					if (guilds.containsKey(guildId))
    						guilds.get(guildId).addItem(id, itemId, itemCount);
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadGuildMembers Exception " + e);
		}
	}
	
	public void saveGuildResourcess(Guild guild) {
		String deleteString = "DELETE FROM `guild_level_resources` WHERE guild_id = " + guild.getGuildID() ;
		queries.executeUpdate(deleteString);
		String sql = "INSERT INTO guild_level_resources (guild_id, item_id, item_count) values ";
		for(int itemId : guild.getItems().keySet()) {
			sql+="("+guild.getGuildID()+","+itemId+", "+guild.getItems().get(itemId)+" ),";
		}
		sql = sql.substring(0, sql.length() - 1);
		if(guild.getItems().size() > 0)
			queries.executeUpdate(sql);
	}
	
	public void writeNewGuild(Guild guild) {
		Log.debug("GUILD: inserting guild:" + guild.getGuildName());
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Calendar cal = Calendar.getInstance();
		//cal.setTime(auction.GetExpirateDate());
		String strDate = sdfDate.format(cal.getTime());
		String columnNames = "name,faction,motd,omotd,level,updatetimestamp, warehouse";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO guild (" + columnNames + ") values (?, ?, ?, ?, ?, ?, ?)")) {
			stmt.setString(1, guild.getGuildName());
			stmt.setInt(2, guild.getFaction());
			stmt.setString(3, guild.getMOTD());
			stmt.setString(4, guild.getOMOTD());
			stmt.setInt(5, guild.getLevel());
			stmt.setString(6, strDate);
			stmt.setLong(7,  guild.getWarehouse() != null ?guild.getWarehouse().toLong():0L);
			
			guild.setGuildID(queries.executeInsert(stmt));
		} catch (SQLException e) {
			Log.dumpStack("writeNewGuild Exception " + e);
			return;
		}

		// Write guild ranks and members
		for (Guild.GuildRank rank : guild.getRanks()) {
			rank.setID(writeNewGuildRank(guild.getGuildID(), rank.getRankLevel(), rank.getRankName(), rank.getPermissions()));
		}
		for (Guild.GuildMember member : guild.getMembers()) {
			member.setID(writeNewGuildMember(guild.getGuildID(), member.getOid(), member.getName(), member.getRank(), member.getLevel(), member.getNote()));
		}
	}

	public int writeNewGuildRank(int guildID, int rank, String name, ArrayList<String> permissions) {
		Log.debug("GUILD: inserting guild rank: " + rank);
		String columnNames = "guildID,guildRank,name,permissions";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO guild_rank (" + columnNames + ") values (?, ?, ?, ?)")) {
			stmt.setInt(1, guildID);
			stmt.setInt(2, rank);
			stmt.setString(3, name);
			String permissionsString = "";
			for (String permission : permissions) {
				permissionsString += permission + ",";
			}
			stmt.setString(4, permissionsString);
			return queries.executeInsert(stmt);
		} catch (SQLException e) {
			Log.dumpStack("writeNewGuildRank Exception " + e);
				return -1;
		}
	}

	public int writeNewGuildMember(int guildID, OID memberOid, String name, int rank, int level, String note) {
		Log.debug("GUILD: inserting guild member: " + rank);
		String columnNames = "guildID,memberOid,name,guildRank,level,note";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO guild_member (" + columnNames + ") values (?, ?, ?, ?, ?, ?)")) {
			stmt.setInt(1, guildID);
			stmt.setLong(2, memberOid.toLong());
			stmt.setString(3, name);
			stmt.setInt(4, rank);
			stmt.setInt(5, level);
			stmt.setString(6, note);
			return queries.executeInsert(stmt);
		} catch (SQLException e) {
			Log.dumpStack("writeNewGuildMember Exception " + e);
					return -1;
		}
	}

	public int updateGuild(Guild guild) {
		Log.debug("GUILD: Updating guild data to database");
		String tableName = "guild";
		int updated;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set name=?, faction=?, motd=?, omotd=?, level=? , warehouse=? where id=?")) {
			stmt.setString(1, guild.getGuildName());
			stmt.setLong(2, guild.getFaction());
			stmt.setString(3, guild.getMOTD());
			stmt.setString(4, guild.getOMOTD());
			stmt.setInt(5, guild.getLevel());
			stmt.setLong(6, guild.getWarehouse()!=null?guild.getWarehouse().toLong():0L);
			stmt.setInt(7, guild.getGuildID());
				
			Log.debug("CONTENTDB: updating guild with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
			saveGuildResourcess(guild);
		} catch (SQLException e) {
			Log.dumpStack("updateGuild Exception " + e);
					return -1;
		}
		Log.debug("Wrote guild data to database");
		return updated;
	}

	public int updateGuildRank(int id, int rank, String name, ArrayList<String> permissions) {
		Log.debug("GUILD: Updating guild Rank data to database");
		String tableName = "guild_rank";
		int updated;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set name=?, guildRank=?, permissions=? where id=?")) {
			stmt.setString(1, name);
			String permissionsString = "";
			for (String permission : permissions) {
				permissionsString += permission + ",";
			}
			stmt.setInt(2, rank);
			stmt.setString(3, permissionsString);
			stmt.setInt(4, id);
			Log.debug("CONTENTDB: updating guild with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack("updateGuildRank Exception " + e);
			return -1;
		}
		Log.debug("Wrote guild data to database");
		return updated;
	}

	public int updateGuildMember(int id, String name, int rank, int level, String note) {
		Log.debug("GUILD: Updating guild member data to database");
		String tableName = "guild_member";
		int updated;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set name=?, guildRank=?, level=?, " + "note=? where id=?")) {
			stmt.setString(1, name);
			stmt.setInt(2, rank);
			stmt.setInt(3, level);
			stmt.setString(4, note);
			stmt.setInt(5, id);
			Log.debug("CONTENTDB: updating guild with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.dumpStack("updateGuildMember Exception " + e);
			return -1;
		}
		Log.debug("Wrote guild data to database");
		return updated;
	}

	public int deleteGuild(int guildID) {
		String tableName = "guild";
		String deleteString = "UPDATE `" + tableName + "` SET isactive = 0 WHERE id = " + guildID;
		int deleted = queries.executeUpdate(deleteString);
		return deleted;
	}

	public void deleteGuildRank(int rankID) {
		String tableName = "guild_rank";
		String deleteString = "DELETE FROM `" + tableName + "` WHERE id = " + rankID;
		queries.executeUpdate(deleteString);
	}

	public void deleteGuildMember(int memberID) {
		String tableName = "guild_member";
		String deleteString = "DELETE FROM `" + tableName + "` WHERE id = " + memberID;
		queries.executeUpdate(deleteString);
	}

	/**
	 * 
	 * @param rs
	 * @param wColoumnLabel
	 * @return OID
	 * @throws SQLException
	 */
	public OID getOIDFromLongInResultSet(ResultSet rs, String wColoumnLabel) throws SQLException {
		return OID.fromLong(rs.getLong(wColoumnLabel));
	}

	/**
	 * Server stats
	 */
	public void updateServerStat(String event) {
		Log.debug("SERVER: updating server event: " + event);
		long timestamp = System.currentTimeMillis() / timeDivider;
		if (event.equals("player_login")) {
			String updateString = "UPDATE " + statsTableName + " set players_online = players_online + 1" + ", last_login = FROM_UNIXTIME(" + timestamp
					+ "), logins_since_restart = logins_since_restart + 1";
			queries.executeUpdate(updateString);
		} else if (event.equals("player_logout")) {
			String updateString = "UPDATE " + statsTableName + " set players_online = players_online - 1";
			queries.executeUpdate(updateString);
		} else if (event.equals("restart")) {
			String updateString = "UPDATE " + statsTableName + " set last_restart = FROM_UNIXTIME(" + timestamp + ")" + ", players_online = 0, logins_since_restart = 0";
			queries.executeUpdate(updateString);
		}

		return;
	}
	

	
	/**
	 *
CREATE TABLE `dev1070_admin`.`memory_data_stats` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `type` VARCHAR(45) NULL,
  `value` BIGINT(64) NULL,
  `world` VARCHAR(45) NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `type_UNIQUE` (`type` ASC) VISIBLE);
	 */
	
	/**
	 * Function to save statistics for memory data
	 * @param type
	 * @param value
	 */
	public void saveMemoryDataStats(String type, long value) {
		 queries.checkConnection();
		try (Statement stmt = queries.con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
			try (ResultSet uprs = stmt.executeQuery("SELECT * FROM memory_data_stats WHERE world = '" + Engine.getWorldName() + "' AND type='" + type + "'")) {
				boolean prevSaved = uprs.first(); // has this obj been saved
				if (!prevSaved)
					uprs.moveToInsertRow();
				uprs.updateString("world", Engine.getWorldName());
				uprs.updateString("type", type);
				uprs.updateLong("value", value);
				if (prevSaved) {
					uprs.updateRow();
				} else {
					uprs.insertRow();
				}
			}
		} catch (SQLException e) {
			Log.exception("saveMemoryDataStats",e);
			e.printStackTrace();
		}
	}

	/**
	 * Function to get All saved memory data statistics for world 
	 * @return
	 */
	public HashMap<String, Long> getMemoryDataStats() {
		HashMap<String, Long > list = new HashMap<String,Long>();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM memory_data_stats WHERE world = '" + Engine.getWorldName()+"'" )) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					long value = rs.getLong("value");
    					String type = HelperFunctions.readEncodedString(rs.getBytes("type"));
    				    list.put(type,value);
    					
    				}
    			}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			Log.dumpStack("loadGuildMembers Exception " + e);
		}
	    return list;
	}
	
	/**
	 * Delete all statistics of the memory data for world
	 */
	public void deleteMemoryDataStats() {
		String deleteString = "DELETE FROM `memory_data_stats` WHERE world = '" + Engine.getWorldName() + "'";
		queries.executeUpdate(deleteString);
	}

	/**
	 * Having too many connection errors, so adding this function to help cope with
	 * it
	 */
	public void close() {
		//Log.dumpStack("AccountDababase.Close");
		queries.close();
	}

	private long timeDivider = 1000L; // 1L

	private static final String accountTableName = "account";
	private static final String characterTableName = "account_character";
	private static final String statsTableName = "server_stats";
	private static final String instanceTemplateTableName = "instance_template";
	private static final String mailTableName = "character_mail";

}
