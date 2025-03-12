package atavism.agis.database;

import atavism.agis.objects.PetInfo;
import atavism.server.engine.Database;
import atavism.server.engine.Engine;
import atavism.server.engine.OID;
import atavism.server.util.AORuntimeException;
import atavism.server.util.Log;
import atavism.server.util.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class AtavismDatabase {

    Database db ;
    public AtavismDatabase(){
        db = Engine.getDatabase();
    }

    private Connection getConnection() {
        try {
            return db.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public void savePlayerPetEquip(OID player, int petProfile, OID equip) {
        if (Log.loggingDebug)
            Log.debug("savePlayerPetEquip "+player+" "+petProfile+" "+equip);
        //Log.dumpStack("saveObjectHelper");
        long stime = System.currentTimeMillis();
        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
                try (ResultSet uprs = stmt.executeQuery("SELECT * FROM player_pets WHERE world_name='" + Engine.getWorldName() + "' AND player_oid =" + player.toLong() + " AND pet_profile=" + petProfile)) {
                    boolean prevSaved = uprs.first(); // has this obj been saved
                    if (Log.loggingDebug)
                        Log.debug("Database.savePlayerPet: prevSaved: " + prevSaved);
                        uprs.updateLong("equip_bag_oid", equip.toLong());
                        uprs.updateRow();
                    }
            }
        } catch (Exception e) {
            Log.exception("savePlayerPetEquip", e);
            throw new AORuntimeException("database error", e);
        } finally {
        }
        long sttime = System.currentTimeMillis();
        log.debug("savePlayerPetEquip time="+(sttime-stime)+" ms ");
    }


    public void createPlayerPet(OID player, int petProfile) {
        if (Log.loggingDebug)
            Log.debug("createPlayerPet "+player+" "+petProfile);
        //Log.dumpStack("saveObjectHelper");
        long stime = System.currentTimeMillis();
        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
                try (ResultSet uprs = stmt.executeQuery("SELECT * FROM player_pets WHERE world_name='" + Engine.getWorldName() + "' AND player_oid =" + player.toLong() + " AND pet_profile=" + petProfile)) {

                    // set the cursor to the appropriate row
                    boolean prevSaved = uprs.first(); // has this obj been saved
                    // before
                    if (!prevSaved) {
                        uprs.moveToInsertRow();
                        if (Log.loggingDebug)
                            Log.debug("Database.savePlayerPet: obj not in database, moved to insert row: " + petProfile);

                        uprs.updateLong("player_oid", player.toLong());
                        uprs.updateInt("pet_profile", petProfile);
                        uprs.updateString("world_name", Engine.getWorldName());
                        uprs.updateLong("exp", 0);
                        uprs.updateInt("level", 1);
                        uprs.insertRow();
                    }
                    Log.debug("done with saving char to the database");
                }
            }
        } catch (Exception e) {
            Log.exception("saveObjectHelper", e);
            throw new AORuntimeException("database error", e);
        } finally {
        }
        long sttime = System.currentTimeMillis();
        log.debug("savePlayerPet time="+(sttime-stime)+" ms ");
    }


    public void savePlayerPet(OID player, int petProfile, PetInfo info) {
        if (Log.loggingDebug)
            Log.debug("savePlayerPetsExp " + player + " " + petProfile + " " + info);
        //Log.dumpStack("saveObjectHelper");
        long stime = System.currentTimeMillis();
        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
                try (ResultSet uprs = stmt.executeQuery("SELECT * FROM player_pets WHERE world_name='" + Engine.getWorldName() + "' AND player_oid =" + player.toLong() + " AND pet_profile=" + petProfile)) {

                    // set the cursor to the appropriate row
                    boolean prevSaved = uprs.first(); // has this obj been saved
                    // before
                    if (!prevSaved) {
                        uprs.moveToInsertRow();
                        if (Log.loggingDebug)
                            Log.debug("Database.savePlayerPetsExp: obj not in database, moved to insert row: " + petProfile);
                    }
                    uprs.updateString("world_name", Engine.getWorldName());
                    uprs.updateLong("player_oid", player.toLong());
                    uprs.updateInt("pet_profile", petProfile);

                    uprs.updateLong("exp", info.experience);
                    uprs.updateInt("level", info.level);
                    // save the data into the database
                    if (prevSaved) {
                        uprs.updateRow();
                    } else {
                        uprs.insertRow();
                    }
                    Log.debug("done with saving char to the database");
                }
            }
        } catch (Exception e) {
            Log.exception("savePlayerPetsExp", e);
            throw new AORuntimeException("database error", e);
        } finally {
        }
        long sttime = System.currentTimeMillis();
        log.debug("savePlayerPetsExp time=" + (sttime - stime) + " ms ");
    }
    public PetInfo loadPlayerPet(OID playerOid, int petProfile) {
        if (Log.loggingDebug)
            Log.debug("loadPlayerPet " + playerOid + " " + petProfile);
        //Log.dumpStack("saveObjectHelper");
        long stime = System.currentTimeMillis();
        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM player_pets WHERE world_name='" + Engine.getWorldName() + "' AND player_oid =" + playerOid.toLong() + " AND pet_profile=" + petProfile)) {
                    if (rs != null) {
                        if(rs.first()) {
                            PetInfo info = new PetInfo();
                            info.id = rs.getInt("pet_profile");
                            info.level = rs.getInt("level");
                            info.experience = rs.getInt("exp");
                            info.equipment = rs.getLong("equip_bag_oid");
                            return info;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.exception("loadPlayerPet", e);
            throw new AORuntimeException("loadPlayerPet database error", e);
        } finally {
            long sttime = System.currentTimeMillis();
            log.debug("loadPlayerPet time=" + (sttime - stime) + " ms ");
        }
        return null;
    }
    public ArrayList<PetInfo> loadPlayerAllPet(OID playerOid) {
        ArrayList<PetInfo> list = new ArrayList<>();
        if (Log.loggingDebug)
            Log.debug("loadPlayerAllPet " + playerOid );
        //Log.dumpStack("saveObjectHelper");
        long stime = System.currentTimeMillis();
        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM player_pets WHERE world_name='" + Engine.getWorldName() + "' AND player_oid =" + playerOid.toLong())) {
                    if (rs != null) {
                        while (rs.next()) {
                            PetInfo info = new PetInfo();
                            info.id = rs.getInt("pet_profile");
                            info.level = rs.getInt("level");
                            info.experience = rs.getInt("exp");
                            info.equipment = rs.getLong("equip_bag_oid");
                            list.add(info);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.exception("loadPlayerAllPet", e);
            throw new AORuntimeException("loadPlayerAllPet database error", e);
        } finally {
            long sttime = System.currentTimeMillis();
            log.debug("loadPlayerAllPet time=" + (sttime - stime) + " ms ");
        }
        return list;
    }

    public int loadPlayerPetLevel(OID playerOid, int petProfile) {
        if (Log.loggingDebug)
            Log.debug("loadPlayerPetLevel " + playerOid + " " + petProfile);
        //Log.dumpStack("saveObjectHelper");
        long stime = System.currentTimeMillis();
        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM player_pets WHERE world_name='" + Engine.getWorldName() + "' AND player_oid =" + playerOid.toLong() + " AND pet_profile=" + petProfile)) {
                    if (rs != null) {
                        if(rs.first()) {
                            return rs.getInt("level");
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.exception("loadPlayerPetLevel", e);
            throw new AORuntimeException("loadPlayerPetLevel database error", e);
        } finally {
            long sttime = System.currentTimeMillis();
            log.debug("loadPlayerPetLevel time=" + (sttime - stime) + " ms ");
        }
        return -1;
    }

    protected static final Logger log = new Logger("AtavismDatabase");

}
