package atavism.agis.database;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import atavism.agis.objects.BonusSettings;
import atavism.agis.objects.VipData;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.BonusPlugin;
import atavism.agis.util.HelperFunctions;
import atavism.server.engine.Engine;
import atavism.server.engine.OID;
import atavism.server.plugins.ProxyPlugin;
import atavism.server.util.*;

/**
 * Manages Server and Account information retrieval and storage for the Authentication Database. The main purpose
 * is to let the Authentication server know this server is still running and how many players are currently connected.
 * @author Andrew Harrison
 *
 */
public class AuthDatabase {
	private static AuthQueries queries;

	public AuthDatabase() {
        if (queries == null) {
            synchronized (AuthDatabase.class) {
                if (queries == null) {
                    queries = new AuthQueries();
                }
            }
        }
	}
	
	/**
	 * Retrieves the list of servers from the Authentication Database and verifies they are still online.
	 * @return ArrayList<HashMap<String, Serializable>>
	 */
	public ArrayList<HashMap<String, Serializable>> getServers() {
		ArrayList<HashMap<String, Serializable>> serverList = new ArrayList<HashMap<String, Serializable>>();
        String selectString = "SELECT * FROM " + worldServerTable;
	    try (PreparedStatement ps = queries.prepare(selectString)) {
			Log.debug("AUTH: getting server statuses");
			try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					HashMap<String, Serializable> server = new HashMap<String, Serializable>();
    					server.put("id", rs.getInt("world_id"));
    					server.put("name", HelperFunctions.readEncodedString(rs.getBytes("world_name")));
    					server.put("hostname", HelperFunctions.readEncodedString(rs.getBytes("server_name")));
    					server.put("port", rs.getInt("server_port"));
    					
    					String status = rs.getString("status");
    					if (status == null || status.equals("") || status.equals("Online")) {
    						// If the server hasn't recieved an update in the last 2 minutes, mark it as offline
    						Timestamp lastUpdate = rs.getTimestamp("last_update");
    						Calendar cal = Calendar.getInstance();
    						cal.add(Calendar.SECOND, -SERVER_TIMEOUT_SECONDS);
    						Log.debug("AUTH: comparing serverUpdate: " + lastUpdate.toString() + " against "+SERVER_TIMEOUT_SECONDS+" seconds ago: " + cal.getTime().toString());
    						if (lastUpdate.after(cal.getTime())) {
    							server.put("status", "Online");
    						} else {
    							server.put("status", "Offline");
    						}
    					} else {
    						server.put("status", status);
    					}
						int population = rs.getInt("population");
						int max_population = rs.getInt("max_population");
						int queue = rs.getInt("queue");
						server.put("queue", queue);
						server.put("load", max_population > 0 ? (int)(population*1000 / max_population) : 0);
						server.put("population", population);
						server.put("max_population", max_population);
						serverList.add(server);
    				}
    			}
			}	
		} catch (SQLException e) {
        }
		return serverList;
	}
	
	/**
     * Reads in the servers the player has characters on.
     * @param accountID
     * @return HashMap<Integer, Integer>
     */
    public HashMap<Integer, Integer> getWorldWithPlayersCharacters(int accountID) {
    	HashMap<Integer, Integer> worldIDs = new HashMap<Integer, Integer>();
		Log.debug("AUTH: getting server statuses");
        String selectString = "SELECT world_server_id, count(*) FROM " + accountCharacterTable + " where account_id=" + accountID 
        		+ " GROUP BY world_server_id";

        try (PreparedStatement ps = queries.prepare(selectString)) {
            try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					worldIDs.put(rs.getInt(1), rs.getInt(2));
    				}
    			}
			}	
		} catch (SQLException e) {
        }
		return worldIDs;
	}
	
	/**
	 * Reads in the id for this server from the world table.
	 */
	public void loadServerID() {
		// Read in the servername from the world.properties file
		String serverName = Engine.getProperty("atavism.servername");
		String selectString = "SELECT world_id FROM " + worldServerTable + " where world_name='" + serverName + "'";
		try (PreparedStatement ps = queries.prepare(selectString)) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					server_id = rs.getInt("world_id");
    					break;
    				}
    			}
			}
		} catch (SQLException e) {
		}
	}

	/**
     * Read AccountOid for playerOid in current world 
     * @param playerOid
     * @return OID
     */
	public OID getAccountOid(OID playerOid) {
		if (server_id == -1) {
			loadServerID();
			}
		String query = "SELECT account_id FROM account_character WHERE character_id = " + playerOid.toLong()+" and world_server_id = "+server_id;
		
		try (PreparedStatement ps = queries.prepare(query)) {
			try (ResultSet rs = queries.executeSelect(ps)) {
				if (rs != null) {
					if (!rs.next()) {
						Log.debug("getAccountOid: characterId = " + playerOid.toLong() + " no data");
						return null;
					}
					return OID.fromLong(rs.getLong(1));
				}
			}
		} catch (SQLException e) {
		}
		return null;
	}
	
	/**
	 * Updates the entry in the world table for this server with the current population to help the 
	 * Authentication server manage player loads.
	 * @param population
	 */
	public void sendServerStatusUpdate(int population) {
		String serverName = Engine.getProperty("atavism.servername");
		// See if row already exists for this server
		if (server_id == -1) {
			loadServerID();
			
			if (server_id == -1) {
				// Insert a row into the database
                String serverAddress = Engine.getProperty("atavism.login.bindaddress").trim();
                String serverIpAddress = Engine.getProperty("atavism.login.bindaddress").trim();
                    int port = Integer.parseInt(Engine.getProperty("atavism.login.bindport"));
                String columnNames = "world_name,server_name,server_port,population,max_population,server_ip,restriction_level";
			    try (PreparedStatement stmt = queries.prepare("INSERT INTO " + worldServerTable + " (" + columnNames 
						+ ") values (?, ?, ?, ?, ?,?,?)")) {
					stmt.setString(1, serverName);
					stmt.setString(2, serverAddress);
					stmt.setInt(3, port);
					stmt.setInt(4, population);
					stmt.setInt(5, ProxyPlugin.LOGIN_QUEUE_MAX_USERS);
					stmt.setString(6, serverIpAddress);
					stmt.setInt(7, 0);
					
						server_id = queries.executeInsert(stmt);
				} catch (SQLException e) {
					Log.error("SQLException:"+e.getMessage()+" "+e.getLocalizedMessage());
				}
			}
		}
		
		try {
			SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Calendar cal = Calendar.getInstance();
	        cal.setTime(new java.util.Date());
			String strDate = sdfDate.format(cal.getTime());
			String serverAddress = Engine.getProperty("atavism.login.bindaddress").trim();
			String serverIpAddress = Engine.getProperty("atavism.login.bindaddress").trim();
			int port = Integer.parseInt(Engine.getProperty("atavism.login.bindport"));
			
			try (PreparedStatement stmt = queries.prepare("UPDATE " + worldServerTable + " set server_name=?, server_port=?, population=?, status=?, last_update=?, max_population=?,server_ip=?,restriction_level=? where world_id=?")) {
    			stmt.setString(1, serverAddress);
    			stmt.setInt(2, port);
    			stmt.setInt(3, population);
    			stmt.setString(4, "Online");
    			stmt.setTimestamp (5, Timestamp.valueOf(strDate));
    			stmt.setInt(6, ProxyPlugin.LOGIN_QUEUE_MAX_USERS);
    			stmt.setString(7, serverIpAddress);
    			stmt.setInt(9, server_id);
    			stmt.setInt(8, ProxyPlugin.restriction_level);
    			
    			Log.debug("AUTH: updating world_server with statement: " + stmt.toString());
    			queries.executeUpdate(stmt);
			}
		} catch (SQLException e) {
			Log.error("SQLException:"+e.getMessage()+" "+e.getLocalizedMessage());
		}
	}
	
	
	public void sendServerRestrictionLevelUpdate(int restrictionLevel) {
		String serverName = Engine.getProperty("atavism.servername");
		// See if row already exists for this server
		if (server_id == -1) {
			loadServerID();
		}
		
		try (PreparedStatement stmt = queries.prepare("UPDATE " + worldServerTable + " set restriction_level=? where world_id=?")) {
			stmt.setInt(1, restrictionLevel);
			stmt.setInt(2, server_id);
			Log.debug("AUTH: updating world_server with statement: " + stmt.toString());
			queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.error("SQLException:"+e.getMessage()+" "+e.getLocalizedMessage());
		}
	}


	public void saveServerQueueUpdate(int queue) {
		String serverName = Engine.getProperty("atavism.servername");
		// See if row already exists for this server
		if (server_id == -1) {
			loadServerID();
		}

		try (PreparedStatement stmt = queries.prepare("UPDATE " + worldServerTable + " set queue=? where world_id=?")) {
			stmt.setInt(1, queue);
			stmt.setInt(2, server_id);
			Log.debug("AUTH: updating world_server with statement: " + stmt.toString());
			queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.error("SQLException:"+e.getMessage()+" "+e.getLocalizedMessage());
		}
	}


	/**
	 * Removes a deleted character from an account entry.
	 * @param accountID
	 * @return boolean
	 */
	public boolean updateAccountCurrentWorld(OID accountID) {
		if (server_id == -1) {
			loadServerID();
		}
		try (PreparedStatement stmt = queries.prepare("UPDATE " + accountTable + " set current_world_id=? where id=?")) {
			stmt.setInt(1, server_id);
			stmt.setInt(2, (int)accountID.toLong());
			Log.debug("AUTH: updating account with statement: " + stmt.toString());
			queries.executeUpdate(stmt);
		} catch (SQLException e) {
			return false;
		}
		return true;
	}
	
	/**
	 * Adds an entry into the account_character table in the Auth Database.
	 * @param accountID
	 * @param characterOID
	 * @return boolean
	 */
	public boolean addAccountCharacter(OID accountID, OID characterOID) {
		if (server_id == -1) {
			loadServerID();
		}
		Log.debug("AUTH: inserting character:" + characterOID + " in account: " + accountID);
        String columnNames = "account_id,character_id,world_server_id";
        try (PreparedStatement stmt = queries.prepare("INSERT INTO " + accountCharacterTable + " (" + columnNames + ") values (?, ?, ?)")) {
			stmt.setInt(1, (int)accountID.toLong());
			stmt.setLong(2, characterOID.toLong());
			stmt.setInt(3, server_id);
			
			queries.executeInsert(stmt);
		} catch (SQLException e) {
			Log.error("addAccountCharacter: "+e+" "+e.getMessage()+" "+e.getLocalizedMessage());
			return false;
		}
		return true;
	}

	public void setAccountStatus(OID accountID, int status) {
		if(Log.loggingDebug)Log.debug("ACCOUNT: set status for account: " + accountID);
		try {
			String updateString = "UPDATE "+accountTable+"  SET status = " + status + " WHERE id =" + accountID.toLong();
			if(Log.loggingDebug)Log.debug("ACCOUNT: Update sql: " + updateString);
			queries.executeUpdate(updateString);
		} catch (Exception e) {
			Log.dumpStack(e.getMessage());
		}
	}

	
	/**
	 * Removes a deleted character from an account entry.
	 * @param accountID
	 * @param characterOID
	 * @param characterName
	 * @return
	 */
	public boolean characterDeleted(OID accountID, OID characterOID) {
	    try (PreparedStatement stmt = queries.prepare("UPDATE " + accountCharacterTable + " set status=? where account_id=? and character_id=?")) {
			stmt.setInt(1, 0);
			stmt.setInt(2, (int) accountID.toLong());
			stmt.setLong(3, characterOID.toLong());
			Log.debug("AUTH: updating account_character with statement: " + stmt.toString());
			queries.executeUpdate(stmt);
		} catch (SQLException e) {
			return false;
		}
		return true;
	}
	
	public void checkAccountPurchases(OID characterOID, OID accountID) {
		if (server_id == -1) {
			loadServerID();
		}
		
		/*try {
			Log.debug("ACCOUNT: getting purchases for character: " + characterOID.toLong());
			ps = queries.prepare("SELECT * FROM character_purchases where used = 0 and character_id=" + characterOID.toLong());
			rs = queries.executeSelect(ps);
			if (rs != null) {
				while (rs.next()) {
					int itemID = rs.getInt("itemID");
					AgisInventoryClient.sendPurchaseMail(characterOID, false, itemID);
					String updateString = "UPDATE character_purchases set used = 1 where id=" + rs.getInt("id");
					queries.executeUpdate(updateString);
				}
			}
		} catch (SQLException e) {

		} finally {
			queries.closeStatement(ps, rs);
		}*/
		
		// Also check for account purchases
		Log.debug("ACCOUNT: getting purchases for account: " + accountID.toLong());
		try (PreparedStatement ps = queries.prepare("SELECT * FROM account_purchases where account_id=" + accountID.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					// First verify the item hasn't been claimed on this server
    					boolean alreadyClaimed = false;
    					String itemClaims = HelperFunctions.readEncodedString(rs.getBytes("itemClaims"));
    					if (itemClaims != null) {
    						String[] claimedServers = itemClaims.split(",");
    						for (String claimedServer : claimedServers) {
    							if (claimedServer.equals("") || Integer.parseInt(claimedServer) == server_id) {
    								alreadyClaimed = true;
    								break;
    							}
    						}
    					}
    					if (alreadyClaimed) {
    						continue;
    					}
    					int itemID = rs.getInt("itemID");
    					int count = rs.getInt("itemCount");
    					AgisInventoryClient.sendPurchaseMail(accountID, characterOID, true, itemID, count);
    					if (itemClaims == null) {
    						itemClaims = server_id + "";
    					} else {
    						itemClaims += "," + server_id;
    					}
    					
    					String updateString = "UPDATE account_purchases set itemClaims = '" + itemClaims + "' where id=" + rs.getInt("id");
    					queries.executeUpdate(updateString);
    				}
    			}
			}
		} catch (SQLException e) {
		}
		return;
	}
	
	public long getAccountCoinAmount(OID accountID) {
		if (accountID == null) {
			return 0l;
		}
		Log.debug("ACCOUNT: getting coin amount for account entry: " + accountID);
			// Now fetch the number of character slots
		try (PreparedStatement ps = queries.prepare("SELECT coin_current FROM " + accountTable + " where id=" + accountID.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return rs.getLong("coin_current");
    				}
    			}
			}
		} catch (SQLException e) {
		}
		return 0l;
	}

	public void alterAccountCoinAmount(OID accountID, long delta) {
		Log.debug("ACCOUNT: getting purchases for character: " + accountID.toLong());
		String updateString = "UPDATE " + accountTable + " set coin_current = coin_current + " + delta + " where id=" + accountID.toLong();
		queries.executeUpdate(updateString);
		return;
	}
	
	public HashMap<String, String> getAccountSettings(OID accountID) {
		HashMap<String, String> accountSettings = new HashMap<String, String>();
		Log.debug("ACCOUNT: getting settings for account: " + accountID.toLong());
		try (PreparedStatement ps = queries.prepare("SELECT * FROM account_setting where account_id=" + accountID.toLong())) {
		    try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					String setting = rs.getString("setting");
    					String settingValue = rs.getString("settingValue");
    					accountSettings.put(setting, settingValue);
    				}
    			}
			}
		} catch (SQLException e) {
		}
		return accountSettings;
	}
	
	
	
	
	
	public void createVip(OID characterOid, OID accountID,int points,long time) {
		
		String serverName = Engine.getProperty("atavism.servername");
		long t = 0L;
		if (time > 0) {
			t = System.currentTimeMillis() + (time * 60_000);
		} else if (BonusPlugin.VIP_USE_TIME) {
			t = 1;
		}
		Log.debug("AUTH: inserting character:" + characterOid + " in account: " + accountID);
		try {
			String columnNames = "account_id, character_oid, world, vip_level, vip_expire, vip_points ";
			PreparedStatement stmt = queries.prepare("INSERT INTO vip (" + columnNames + ") values (?, ?, ?, ?, ?, ? )");
			stmt.setInt(1, (int)accountID.toLong());
			stmt.setLong(2, characterOid.toLong());
			stmt.setString(3, serverName);
			stmt.setInt(4, 1);
			stmt.setLong(5, t);
			stmt.setInt(6, points);
			queries.executeInsert(stmt);
		} catch (SQLException e) {
			Log.error("createVip: "+e+" "+e.getMessage()+" "+e.getLocalizedMessage());
		}
	}
	
	
	public VipData getVipCharacter(OID characterOid) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM vip where character_oid=" + characterOid.toLong())) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					
    					return new VipData(rs.getInt("vip_level"),rs.getLong("vip_expire"),rs.getLong("vip_points"));
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("SQLException "+e);
		}
		return new VipData();
	}
	
	public VipData getVipAccount(OID accountID) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM vip where account_id=" + accountID.toLong())) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return new VipData(rs.getInt("vip_level"),rs.getLong("vip_expire"),rs.getLong("vip_points"));
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("SQLException "+e);
		}
		return new VipData();
	}
	
	public boolean vipUpdate(VipData vd, OID characterOid) {
		try {
			PreparedStatement stmt = queries.prepare("UPDATE vip set vip_level=?, vip_expire=?, vip_points=? where character_oid=?");
			stmt.setInt(1, vd.getLevel());
			stmt.setLong(2, vd.getExpire());
			stmt.setLong(3, vd.getPoints());
			stmt.setLong(4, characterOid.toLong());
			Log.debug("AUTH: updating world_server with statement: " + stmt.toString());
			queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.error("SQLException:" + e.getMessage() + " " + e.getLocalizedMessage());
		}
		return true;
	}

	public boolean vipAccountUpdate(VipData vd, OID accountID) {
		try {
			PreparedStatement stmt = queries.prepare("UPDATE vip set vip_level=?, vip_expire=?, vip_points=? where account_id=?");
			stmt.setInt(1, vd.getLevel());
			stmt.setLong(2, vd.getExpire());
			stmt.setLong(3, vd.getPoints());
			stmt.setLong(4, accountID.toLong());
			Log.debug("AUTH: updating world_server with statement: " + stmt.toString());
			queries.executeUpdate(stmt);
		} catch (SQLException e) {
			Log.error("SQLException:" + e.getMessage() + " " + e.getLocalizedMessage());
		}
		return true;
	}
	
	public int getVipExpireCharacter(OID characterOid) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM vip where character_oid=" + characterOid.toLong())) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return rs.getInt("vip_expire");
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("SQLException "+e);
		}
		return 0;
	}
	
	public int getVipExpireAccount(OID accountID) {
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM vip where account_id=" + accountID.toLong())) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return rs.getInt("vip_expire");
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("SQLException "+e);
		}
		return 0;
	}
	public int getVipPointsCharacter(OID characterOid) {
	    try (PreparedStatement ps = queries.prepare("SELECT sum(vip_points) as points FROM vip where character_oid=" + characterOid.toLong())) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					return rs.getInt("points");
    				}
    			}
	        }
		} catch (SQLException e) {
			Log.error("SQLException "+e);
		}
		return 0;
	}

	public HashMap<String, List<BonusSettings>> loadPlayerBonuses(OID characterOid) {
		Log.debug("loadPlayerBonuses characterOid="+characterOid);
		HashMap<String, List<BonusSettings>> out = new HashMap<String, List<BonusSettings>> ();
	    try (PreparedStatement ps = queries.prepare("SELECT * FROM bonuses where character_oid=" + characterOid.toLong())) {
	        try (ResultSet rs = queries.executeSelect(ps)) {
    			if (rs != null) {
    				while (rs.next()) {
    					if(!out.containsKey(rs.getString("code"))) {
    						out.put(rs.getString("code") ,new ArrayList<BonusSettings>());
    					}
						out.get(rs.getString("code")).add(new BonusSettings("",rs.getString("code"),rs.getInt("value"),rs.getFloat("valuep"),rs.getString("object"),rs.getInt("id")));
    				}
    			}
			}
		} catch (SQLException e) {
			Log.error("SQLException "+e);
		}
		Log.debug("loadPlayerBonuses "+out);
		return out;
	}

	public void savePlayerBonuses(OID characterOid, HashMap<String, List<BonusSettings>> bonuses) {
		savePlayerBonuses(characterOid, bonuses, new ArrayList<Integer>());
	}
	
	public void savePlayerBonuses(OID characterOid, HashMap<String, List<BonusSettings>> bonuses, List<Integer> toDelete) {
		PreparedStatement ps = null;
		ResultSet rs = null;
		if (Log.loggingDebug)
			Log.debug("Vip: savePlayerBonuses: character_oid = " + characterOid.toLong() + " bonuses=" + bonuses);
		synchronized (characterOid) {
			if (toDelete.size() > 0) {
				List<String> ids = toDelete.stream().map(i -> i.toString()).collect(Collectors.toList());
				String sql = "DELETE from bonuses where character_oid = " + characterOid.toLong() + " and id in (" + String.join(",", ids) + ")";
				if (Log.loggingDebug)Log.debug("delete bonuses SQL: "+sql);
				queries.executeUpdate(sql);
			}
			HashMap<String, List<BonusSettings>> out = new HashMap<String, List<BonusSettings>>();
			for (String code : bonuses.keySet()) {
				for (BonusSettings bs : bonuses.get(code)) {
					if (bs.getId() == -1) {
						Log.debug("Bonus add "+bs);
						try (PreparedStatement stmt = queries.prepare("INSERT INTO bonuses (character_oid, object, code, value, valuep) values (?, ?, ?, ?, ?)")) {
							stmt.setLong(1, characterOid.toLong());
							stmt.setString(2, bs.getObj());
							stmt.setString(3, bs.getSettingCode());
							stmt.setInt(4, bs.GetValue());
							stmt.setFloat(5, bs.GetValuePercentage());
							int id = queries.executeInsert(stmt);
							bs.setId(id);
						} catch (SQLException e) {
							Log.dumpStack(" SQLException: " + e);
						}
					}else {
						if(bs.isDirty()) {
							Log.debug("Bonus Dirty "+bs);
						try {
							PreparedStatement stmt = queries.prepare("UPDATE bonuses SET value=?, valuep=?, object=? where id=? and character_oid=?");
							stmt.setInt(1, bs.GetValue());
							stmt.setFloat(2, bs.GetValuePercentage());
							stmt.setString(3, bs.getObj());
							stmt.setInt(4, bs.getId());
							stmt.setLong(5, characterOid.toLong());
							Log.debug("AUTH: updating bonuses with statement: " + stmt.toString());
							queries.executeUpdate(stmt);
							bs.setDirty(false);
						} catch (SQLException e) {
							Log.dumpStack(" SQLException: " + e);
						}
						}else {
							Log.debug("Bonus not Dirty "+bs);
						}
					}
				}
			}
		}
	}

	private int server_id = -1;
	private static final String worldServerTable = "world";
	private static final String accountTable = "account";
	private static final String accountCharacterTable = "account_character";
	
	private static final int SERVER_TIMEOUT_SECONDS = 60;

}

