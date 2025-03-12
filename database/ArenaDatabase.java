package atavism.agis.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;

import atavism.agis.objects.*;
import atavism.agis.objects.ArenaStats.ArenaSubTypeStats;
import atavism.agis.objects.ArenaStats.ArenaTypeStats;
import atavism.server.engine.OID;
import atavism.server.util.*;

public class ArenaDatabase {
	private static ArenaQueries queries;
	
	public ArenaDatabase() {
        if (queries == null) {
            synchronized (ArenaDatabase.class) {
                if (queries == null) {
                    queries = new ArenaQueries();
                }
            }
        }
	}

	public ArenaStats loadArenaStats(OID characterOid, String characterName) {
		try {
			Log.debug("ARENADB: attempting to load arena stats for: " + characterOid);
			String table = "base_arena_stats";
			String selectString = "SELECT * FROM " + table + " where player_oid=" + characterOid.toLong();
            try (PreparedStatement ps = queries.prepare(selectString)) {
                try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				boolean entryExists = false;
        				while (rs.next()) {
        					ArenaStats arenaStats = new ArenaStats(characterOid, characterName);
        					arenaStats.setLevel(rs.getInt("level"));
        					arenaStats.setExperience(rs.getInt("exp"));
        					arenaStats.setExperienceRequired(rs.getInt("exp_required"));
        					arenaStats.setWins(rs.getInt("wins"));
        					arenaStats.setLosses(rs.getInt("losses"));
        					arenaStats.setTotalKills(rs.getInt("kills"));
        					arenaStats.setTotalDeaths(rs.getInt("deaths"));
        					arenaStats.setObjectsConsumed(rs.getInt("objects_used"));
        					entryExists = true;
        					Log.debug("ARENA: got arena stats for character " + characterOid);
        					return arenaStats;
        				}
        				if (!entryExists) {
        					return createArenaStats(characterOid, characterName);
        				}
        			}
                }
			}
		} catch (SQLException e) {
        }
		return null;
	}
	
	public ArenaStats createArenaStats(OID characterOid, String characterName) {
		ArenaStats arenaStats = new ArenaStats(characterOid, characterName);
		arenaStats.createDefaultStats();
		Log.debug("ARENADB: attempting to create arena stats for: " + characterOid);
		String tableName = "base_arena_stats";
		String columnNames = "player_oid,player_name,level,exp,exp_required,wins,losses,kills,deaths,objects_used";
		try {
			PreparedStatement stmt = queries.prepare("INSERT INTO " + tableName + " (" + columnNames 
					+ ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			stmt.setLong(1, characterOid.toLong());
			stmt.setString(2, characterName);
			stmt.setInt(3, arenaStats.getLevel());
			stmt.setInt(4, arenaStats.getExperience());
			stmt.setInt(5, arenaStats.getExperienceRequired());
			stmt.setInt(6, arenaStats.getWins());
			stmt.setInt(7, arenaStats.getLosses());
			stmt.setInt(8, arenaStats.getTotalKills());
			stmt.setInt(9, arenaStats.getTotalDeaths());
			stmt.setInt(10, arenaStats.getObjectsConsumed());
			queries.executeInsert(stmt);
		} catch (SQLException e) {
			return null;
		}
		return arenaStats;
	}

	public int updateArenaStats(ArenaStats arenaStats) {
		Log.debug("Writing arena stats data to database");
		String tableName = "base_arena_stats";
		int updated;
		try {
			PreparedStatement stmt = queries.prepare("UPDATE " + tableName + " set level=?, exp=?, exp_required=?, " 
					+ "wins=?, losses=?, kills=?, deaths=?, objects_used=? where player_oid=?");
			stmt.setInt(1, arenaStats.getLevel());
			stmt.setInt(2, arenaStats.getExperience());
			stmt.setInt(3, arenaStats.getExperienceRequired());
			stmt.setInt(4, arenaStats.getWins());
			stmt.setInt(5, arenaStats.getLosses());
			stmt.setInt(6, arenaStats.getTotalKills());
			stmt.setInt(7, arenaStats.getTotalDeaths());
			stmt.setInt(8, arenaStats.getObjectsConsumed());
			stmt.setLong(9, arenaStats.getOid().toLong());
			Log.debug("ARENADB: updating stats with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			return -1;
		}
		
		Log.debug("Wrote arena stats data to database");
		return updated;
	}
	
	public ArenaTypeStats loadArenaTypeStats(OID characterOid, ArenaTypeStats arenaStats) {
	    try {
			Log.debug("ARENADB: attempting to load arena type stats for: " + characterOid);
			String selectString = "SELECT * FROM " + ARENA_TYPE_TABLE + " where player_oid=" + characterOid.toLong() 
			+ " and arena_type=" + arenaStats.getArenaType();
			try (PreparedStatement ps = queries.prepare(selectString)) {
                try (ResultSet rs = queries.executeSelect(ps)) {
        			if (rs != null) {
        				boolean entryExists = false;
        				while (rs.next()) {
        					Log.debug("ARENA: got arena type stats for character " + characterOid);
        					arenaStats.setWins(rs.getInt("wins"));
        					arenaStats.setLosses(rs.getInt("losses"));
        					arenaStats.setKills(rs.getInt("kills"));
        					arenaStats.setDeaths(rs.getInt("deaths"));
        					arenaStats.setRating(rs.getInt("rating"));
        					entryExists = true;
        					
        					return arenaStats;
        				}
        				if (!entryExists) {
        					Log.debug("ARENA: could not find arena type stats for character " + characterOid);
        					return createArenaTypeStats(characterOid, arenaStats);
        				}
        			}
                }
			}
		} catch (SQLException e) {
        }
		return null;
	}
	
	public ArenaTypeStats createArenaTypeStats(OID characterOid, ArenaTypeStats arenaStats) {
		Log.debug("ARENADB: attempting to create arena type stats for: " + characterOid);
		String columnNames = "player_oid,arena_type,wins,losses,rating,kills,deaths";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO " + ARENA_TYPE_TABLE + " (" + columnNames 
					+ ") values (?, ?, ?, ?, ?, ?, ?)")) {
			stmt.setLong(1, characterOid.toLong());
			stmt.setInt(2, arenaStats.getArenaType());
			stmt.setInt(3, arenaStats.getWins());
			stmt.setInt(4, arenaStats.getLosses());
			stmt.setInt(5, arenaStats.getRating());
			stmt.setInt(6, arenaStats.getKills());
			stmt.setInt(7, arenaStats.getDeaths());
			queries.executeInsert(stmt);
		} catch (SQLException e) {
			return null;
        }
		return arenaStats;
	}

	public int updateArenaTypeStats(OID characterOid, ArenaTypeStats arenaStats) {
		Log.debug("Writing arena type stats data to database");
		int updated;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + ARENA_TYPE_TABLE + " set wins=?, losses=?, "
					+ "rating=?, kills=?, deaths=? where player_oid=? and arena_type=?")) {
			stmt.setInt(1, arenaStats.getWins());
			stmt.setInt(2, arenaStats.getLosses());
			stmt.setInt(3, arenaStats.getRating());
			stmt.setInt(4, arenaStats.getKills());
			stmt.setInt(5, arenaStats.getDeaths());
			stmt.setLong(6, characterOid.toLong());
			stmt.setInt(7, arenaStats.getArenaType());
			Log.debug("ARENADB: updating stats with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			return -1;
		}
		
		Log.debug("Wrote arena stats data to database");
		return updated;
	}
	
	public ArenaSubTypeStats loadArenaSubTypeStats(OID characterOid, ArenaSubTypeStats arenaStats) {
        try {
			Log.debug("ARENADB: attempting to load arena type stats for: " + characterOid);
			String selectString = "SELECT * FROM " + ARENA_SUB_TABLE + " where player_oid=" + characterOid.toLong() 
			+ " and arena_sub_type=" + arenaStats.getArenaSubType();
			try (PreparedStatement stmt = queries.prepare(selectString)) {
                try (ResultSet rs = queries.executeSelect(stmt)) {
        			if (rs != null) {
        				boolean entryExists = false;
        				while (rs.next()) {
        					Log.debug("ARENA: got arena type stats for character " + characterOid);
        					arenaStats.setWins(rs.getInt("wins"));
        					arenaStats.setLosses(rs.getInt("losses"));
        					arenaStats.setKills(rs.getInt("kills"));
        					arenaStats.setDeaths(rs.getInt("deaths"));
        					arenaStats.setRating(rs.getInt("rating"));
        					entryExists = true;
        					return arenaStats;
        				}
        				if (!entryExists) {
        					Log.debug("ARENA: could not find arena type stats for character " + characterOid);
        					return createArenaSubTypeStats(characterOid, arenaStats);
        				}
        			}
                }
			}
		} catch (SQLException e) {
        }
		return null;
	}
	
	public ArenaSubTypeStats createArenaSubTypeStats(OID characterOid, ArenaSubTypeStats arenaStats) {
		Log.debug("ARENADB: attempting to create arena type stats for: " + characterOid);
		String columnNames = "player_oid,arena_type,arena_sub_type,wins,losses,rating,kills,deaths";
		try (PreparedStatement stmt = queries.prepare("INSERT INTO " + ARENA_SUB_TABLE + " (" + columnNames 
					+ ") values (?, ?, ?, ?, ?, ?, ?, ?)")) {
			stmt.setLong(1, characterOid.toLong());
			stmt.setInt(2, arenaStats.getArenaType());
			stmt.setInt(3, arenaStats.getArenaSubType());
			stmt.setInt(4, arenaStats.getWins());
			stmt.setInt(5, arenaStats.getLosses());
			stmt.setInt(6, arenaStats.getRating());
			stmt.setInt(7, arenaStats.getKills());
			stmt.setInt(8, arenaStats.getDeaths());
			queries.executeInsert(stmt);
		} catch (SQLException e) {
			return null;
		}
		return arenaStats;
	}

	public int updateArenaSubTypeStats(OID characterOid, ArenaSubTypeStats arenaStats) {
		Log.debug("Writing arena type stats data to database");
		int updated;
		try (PreparedStatement stmt = queries.prepare("UPDATE " + ARENA_SUB_TABLE + " set wins=?, losses=?, "
					+ "rating=?, kills=?, deaths=? where player_oid=? and arena_sub_type=?")) {
			stmt.setInt(1, arenaStats.getWins());
			stmt.setInt(2, arenaStats.getLosses());
			stmt.setInt(3, arenaStats.getRating());
			stmt.setInt(4, arenaStats.getKills());
			stmt.setInt(5, arenaStats.getDeaths());
			stmt.setLong(6, characterOid.toLong());
			stmt.setInt(7, arenaStats.getArenaSubType());
			Log.debug("ARENADB: updating stats with statement: " + stmt.toString());
			updated = queries.executeUpdate(stmt);
		} catch (SQLException e) {
			return -1;
		}
		
		Log.debug("Wrote arena stats data to database");
		return updated;
	}
	
	protected static final String ARENA_TYPE_TABLE = "arena_type_stats";
	protected static final String ARENA_SUB_TABLE = "arena_sub_stats";
	
}
