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
import java.util.stream.Collectors;
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

public class VehicleDatabase {
    private static Queries queries;

    public VehicleDatabase() {
        queries = new Queries(true); // Assuming 'true' signifies keeping the connection alive
    }
/*
    // Method to load vehicle data from the database
    public Vehicle loadVehicle(String classname) {
        String selectString = "    SELECT\r\n"
        		+ "	vehicle_data.objectID, \r\n"
        		+ "	vehicle_data.objectUID, \r\n"
        		+ "	vehicle_data.instance, \r\n"
        		+ "	vehicle_data.classname, \r\n"
        		+ "	vehicle_data.datestamp, \r\n"
        		+ "	vehicle_data.characterID, \r\n"
        		+ "	vehicle_data.worldspace, \r\n"
        		+ "	vehicle_data.inventory, \r\n"
        		+ "	vehicle_data.hitpoints, \r\n"
        		+ "	vehicle_data.fuel, \r\n"
        		+ "	vehicle_data.damage, \r\n"
        		+ "	vehicle_data.last_updated, \r\n"
        		+ "	vehicle_classes.vehicleModel, \r\n"
        		+ "	vehicle_classes.type\r\n"
        		+ "FROM\r\n"
        		+ "	vehicle_classes\r\n"
        		+ "	INNER JOIN\r\n"
        		+ "	vehicle_data\r\n"
        		+ "	ON \r\n"
        		+ "		vehicle_classes.VehicleName = vehicle_data.Classname WHERE ObjectID = ?";
        try (PreparedStatement ps = queries.prepare(selectString)) {
            ps.setString(1, classname);
            try (ResultSet rs = queries.executeSelect(ps)) {
                if (rs != null && rs.next()) {
                    return new Vehicle(
                        rs.getString("classname"),
                        rs.getString("worldspace"),
                        rs.getString("inventory"),
                        rs.getString("hitpoints"),
                        rs.getDouble("fuel"),
                        rs.getDouble("damage"),
                        rs.getInt("OobjectID")
                    );
                }
            }
        } catch (SQLException e) {
            Log.error("VehicleDatabase.loadVehicle SQLException: " + e);
        }
        return null;
    }

    public boolean updateVehicle(Vehicle vehicle) {
        String updateString = "UPDATE vehicle_data SET worldspace = ?, inventory = ?, hitpoints = ? " +
                              "WHERE classname = ?";
        try (PreparedStatement ps = queries.prepare(updateString)) {
            ps.setString(1, vehicle.getWorldspace());
            ps.setString(2, vehicle.getInventory());
            ps.setString(3, vehicle.getHitpoints());
            ps.setString(4, vehicle.getClassname());
            return queries.executeUpdate(ps) > 0;
        } catch (SQLException e) {
            Log.error("VehicleDatabase.updateVehicle SQLException: " + e);
            return false;
        }
    }*/
    
public HashMap<Integer, SpawnData> loadInstanceSpawnData(int instanceID) {
        HashMap<Integer, SpawnData> list = new HashMap<>();
        Log.error("DB: vehicle loadInstanceSpawnData for instance: " + instanceID);
        String query = "SELECT\r\n"
        		+ "	vehicle_classes.*,\r\n"
        		+ "	vehicle_data.* \r\n"
        		+ "FROM\r\n"
        		+ "	vehicle_classes\r\n"
        		+ "	NATURAL LEFT JOIN vehicle_data \r\n"
        		+ "WHERE\r\n"
        		+ "	vehicle_classes.isactive = 1\r\n"
        		+ "	and vehicle_data.instance = ?";
        try (PreparedStatement ps = queries.prepare(query)) {
            ps.setInt(1, instanceID);
            try (ResultSet rs = queries.executeSelect(ps)) {
                while (rs != null && rs.next()) {
                    int spawnDataId = rs.getInt("id");
                    SpawnData sd = loadSpecificSpawnData(spawnDataId);
                    if (sd != null) {
                        list.put(spawnDataId, sd);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Log.dumpStack("loadInstanceSpawnData: SQLException" + e);
        }
        return list;
    }


    public SpawnData loadSpecificSpawnData(int spawnDataID) {
        String query = "    SELECT\r\n"
        		+ "	vehicle_data.objectID, \r\n"
        		+ "	vehicle_data.objectUID, \r\n"
        		+ "	vehicle_data.instance, \r\n"
        		+ "	vehicle_data.classname, \r\n"
        		+ "	vehicle_data.datestamp, \r\n"
        		+ "	vehicle_data.characterID, \r\n"
        		+ "	vehicle_data.worldspace, \r\n"
        		+ "	vehicle_data.inventory, \r\n"
        		+ "	vehicle_data.hitpoints, \r\n"
        		+ "	vehicle_data.fuel, \r\n"
        		+ "	vehicle_data.damage, \r\n"
        		+ "	vehicle_data.last_updated, \r\n"
        		+ "	vehicle_classes.vehicleModel, \r\n"
        		+ "	vehicle_classes.type\r\n"
        		+ "FROM\r\n"
        		+ "	vehicle_classes\r\n"
        		+ "	INNER JOIN\r\n"
        		+ "	vehicle_data\r\n"
        		+ "	ON \r\n"
        		+ "		vehicle_classes.vehicleName = vehicle_data.classname WHERE objectID = ?";
        try (PreparedStatement ps = queries.prepare(query)) {
            ps.setInt(1, spawnDataID);

            try (ResultSet rs = queries.executeSelect(ps)) {
                if (rs != null && rs.next()) {
                	SpawnData spawnData = new SpawnData();
        			if(Log.loggingDebug)	Log.debug("DB: read vehcilespawnData for tamplateId: " + rs.getInt("id"));

                    // Assuming these are the appropriate fields in your SpawnData class
                    spawnData.setInstanceOid(OID.fromString(rs.getString("objectID")));
                    spawnData.setClassName(rs.getString("classname"));
                    
                    
                    String worldspace = rs.getString("worldspace");
                    spawnData.setLoc(stringToPoint(worldspace));
                    spawnData.setOrientation(stringToQuaternion(worldspace));
                    spawnData.setProperty("id", rs.getInt("objectID"));

        			
        			String markerName = HelperFunctions.readEncodedString(rs.getBytes("markerName"));
        			if (markerName == null || markerName.equals("")) {
        				spawnData.setProperty("markerName", "");
        			} else {
        				spawnData.setProperty("markerName", markerName);
        			}
    				
                    return spawnData;
                }
            }
        } catch (SQLException e) {
            Log.error("SQLException in loadSpecificSpawnData: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

	
    /**
     * Converts a worldspace string to a Quaternion.
     * Expected format: "(x,y,z),(q1,q2,q3,q4)"
     */
    public static Quaternion stringToQuaternion(String worldspace) {
        worldspace = worldspace.trim();
        
        // Find the start of the quaternion part (after the first set).
        int firstParenClose = worldspace.indexOf(")");
        int secondParenOpen = worldspace.indexOf("(", firstParenClose);
        int secondParenClose = worldspace.indexOf(")", secondParenOpen);
        if (secondParenOpen < 0 || secondParenClose < 0) {
            throw new IllegalArgumentException("Invalid worldspace format: missing quaternion part.");
        }
        
        String quatString = worldspace.substring(secondParenOpen + 1, secondParenClose);
        String[] parts = quatString.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid quaternion format: " + quatString);
        }
        
        float x = Float.parseFloat(parts[0].trim());
        float y = Float.parseFloat(parts[1].trim());
        float z = Float.parseFloat(parts[2].trim());
        float w = Float.parseFloat(parts[3].trim());
        return new Quaternion(x, y, z, w);
    }
    
    /**
     * Converts a worldspace string to a Point.
     * Expected format: "(x,y,z),(q1,q2,q3,q4)"
     */
    public static Point stringToPoint(String worldspace) {
        // Remove extra whitespace if any.
        worldspace = worldspace.trim();
        
        // Find the closing parenthesis for the point part.
        int firstParenClose = worldspace.indexOf(")");
        if (firstParenClose < 0) {
            throw new IllegalArgumentException("Invalid worldspace format: missing closing parenthesis for point coordinates.");
        }
        
        // Extract the substring for point coordinates (skip the first "(").
        String pointString = worldspace.substring(1, firstParenClose);
        String[] parts = pointString.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid point coordinate format: " + pointString);
        }
        
        float x = Float.parseFloat(parts[0].trim());
        float y = Float.parseFloat(parts[1].trim());
        float z = Float.parseFloat(parts[2].trim());
        return new Point(x, y, z);
    }
    
    
    // Updated method to get vehicles by a specific spawn type
    public List<Vehicle> getVehiclesBySpawnType(String spawnType) throws SQLException {
        String query = 
        		"    SELECT\r\n"
                		+ "	vehicle_data.objectID, \r\n"
                		+ "	vehicle_data.objectUID, \r\n"
                		+ "	vehicle_data.instance, \r\n"
                		+ "	vehicle_data.classname, \r\n"
                		+ "	vehicle_data.datestamp, \r\n"
                		+ "	vehicle_data.characterID, \r\n"
                		+ "	vehicle_data.worldspace, \r\n"
                		+ "	vehicle_data.inventory, \r\n"
                		+ "	vehicle_data.hitpoints, \r\n"
                		+ "	vehicle_data.fuel, \r\n"
                		+ "	vehicle_data.damage, \r\n"
                		+ "	vehicle_data.last_updated, \r\n"
                		+ "	vehicle_classes.vehicleModel, \r\n"
                		+ "	vehicle_classes.type\r\n"
                		+ "FROM\r\n"
                		+ "	vehicle_classes\r\n"
                		+ "	INNER JOIN\r\n"
                		+ "	vehicle_data\r\n"
                		+ "	ON \r\n"
                		+ "		vehicle_classes.vehicleName = vehicle_data.classname WHERE vehicle_classes.type = ?";
        List<Vehicle> vehicles = new ArrayList<>();
        
        try (PreparedStatement stmt = queries.prepare(query)) {
            stmt.setString(1, spawnType);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    vehicles.add(new Vehicle(
                        rs.getString("classname"),
                        rs.getString("worldspace"),
                        rs.getString("inventory"),
                        rs.getString("hitpoints"),
                        rs.getDouble("fuel"),
                        rs.getDouble("damage"),
                        rs.getInt("objectID")
                    ));
                }
            }
        }
        return vehicles;
    }

    public List<Vehicle> getAllVehicles() {
    	Log.error("getAllVehicles: Started the process.");
        String query ="    SELECT\r\n"
        		+ "	vehicle_data.objectID, \r\n"
        		+ "	vehicle_data.objectUID, \r\n"
        		+ "	vehicle_data.instance, \r\n"
        		+ "	vehicle_data.classname, \r\n"
        		+ "	vehicle_data.datestamp, \r\n"
        		+ "	vehicle_data.characterID, \r\n"
        		+ "	vehicle_data.worldspace, \r\n"
        		+ "	vehicle_data.inventory, \r\n"
        		+ "	vehicle_data.hitpoints, \r\n"
        		+ "	vehicle_data.fuel, \r\n"
        		+ "	vehicle_data.damage, \r\n"
        		+ "	vehicle_data.last_updated, \r\n"
        		+ "	vehicle_classes.vehicleModel, \r\n"
        		+ "	vehicle_classes.type\r\n"
        		+ "FROM\r\n"
        		+ "	vehicle_classes\r\n"
        		+ "	INNER JOIN\r\n"
        		+ "	vehicle_data\r\n"
        		+ "	ON \r\n"
        		+ "		vehicle_classes.vehicleName = vehicle_data.classname";
        List<Vehicle> vehicles = new ArrayList<>();

        try (PreparedStatement stmt = queries.prepare(query)) {
            Log.error("getAllVehicles: Prepared statement created successfully.");

            try (ResultSet rs = stmt.executeQuery()) {
                Log.error("getAllVehicles: Executed query successfully.");

                while (rs.next()) {
                    Log.error("getAllVehicles: Processing a row from the result set.");
                    
                    Vehicle vehicle = new Vehicle(
                        rs.getString("classname"),
                        rs.getString("worldspace"),
                        rs.getString("inventory"),
                        rs.getString("hitpoints"),
                        rs.getDouble("fuel"),
                        rs.getDouble("damage"),
                        rs.getInt("objectID")
                    );

                    Log.error("getAllVehicles: Created vehicle - " + vehicle.getClassname());
                    vehicles.add(vehicle);
                }

                Log.error("getAllVehicles: Finished processing result set.");
            }
        } catch (SQLException e) {
            Log.error("getAllVehicles: SQLException occurred - " + e.toString());
        }

        Log.error("getAllVehicles: Returning list of vehicles. Size: " + vehicles.size());
        return vehicles;
    }
    
    public static ArrayList<HashMap<String, Serializable>> getVehicleTemplates(int category) {
        ArrayList<HashMap<String, Serializable>> list = new ArrayList<HashMap<String, Serializable>>();
        String query = "SELECT vehicle_classes.*, vehicle_data.* " +
                       "FROM vehicle_classes " +
                       "LEFT JOIN vehicle_data ON vehicle_classes.vehicleName = vehicle_data.classname " +
                       "WHERE vehicle_classes.category = ? " +
                       "AND vehicle_classes.Isactive = 1";

        try (PreparedStatement ps = queries.prepare(query)) {
            ps.setInt(1, category);
            try (ResultSet rs = queries.executeSelect(ps)) {
                if (rs != null) {
                    while (rs.next()) {
                        HashMap<String, Serializable> map = new HashMap<String, Serializable>();
                        int vehicleID = rs.getInt("objectID"); // Assuming 'ObjectID' is the unique identifier for the vehicle
                        String vehicleName = rs.getString("vehicleName"); // Assuming 'VehicleName' is the name of the vehicle
                        
                        map.put("id", vehicleID);
                        map.put("name", vehicleName);
                        // ... other fields from your vehicle data
                        map.put("model", rs.getString("vehicleModel"));
                        map.put("type", rs.getString("type"));
                        map.put("speedMin", rs.getFloat("speedMin"));
                        map.put("speedMax", rs.getFloat("speedMax"));
                        map.put("isAttackable", rs.getBoolean("attackable"));
                        map.put("isTargetable", rs.getBoolean("targetable"));
                        // ... more properties based on your vehicle data structure

                        list.add(map);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Log.dumpStack("SQLException in getVehicleTemplates: " + e.getMessage());
        }

        return list;
    }

    
    public ArrayList<Template> loadVehicleTemplates(int instanceID) {
        ArrayList<Template> list = new ArrayList<Template>();
        String query = "SELECT\r\n"
        		+ "	vehicle_data.objectID, \r\n"
        		+ "	vehicle_data.objectUID, \r\n"
        		+ "	vehicle_data.instance, \r\n"
        		+ "	vehicle_data.classname, \r\n"
        		+ "	vehicle_data.datestamp, \r\n"
        		+ "	vehicle_data.characterID, \r\n"
        		+ "	vehicle_data.worldspace, \r\n"
        		+ "	vehicle_data.inventory, \r\n"
        		+ "	vehicle_data.hitpoints, \r\n"
        		+ "	vehicle_data.fuel, \r\n"
        		+ "	vehicle_data.damage, \r\n"
        		+ "	vehicle_data.last_updated, \r\n"
        		+ "	vehicle_classes.vehicleName, \r\n"
        		+ "	vehicle_classes.vehicleModel, \r\n"
        		+ "	vehicle_classes.chance, \r\n"
        		+ "	vehicle_classes.maxNum, \r\n"
        		+ "	vehicle_classes.damage, \r\n"
        		+ "	vehicle_classes.type, \r\n"
        		+ "	vehicle_classes.attackable, \r\n"
        		+ "	vehicle_classes.targetable, \r\n"
        		+ "	vehicle_classes.speedMin, \r\n"
        		+ "	vehicle_classes.speedMax, \r\n"
        		+ "	vehicle_classes.isactive, \r\n"
        		+ "	vehicle_classes.scale, \r\n"
        		+ "	vehicle_classes.tags, \r\n"
        		+ "	vehicle_classes.hitbox \r\n"
        		+ "FROM\r\n"
        		+ "	vehicle_classes\r\n"
        		+ "	INNER JOIN\r\n"
        		+ "	vehicle_data\r\n"
        		+ "	ON \r\n"
        		+ "		vehicle_classes.vehicleName = vehicle_data.classname\r\n"
        		+ "WHERE\r\n"
        		+ "	vehicle_classes.isactive = 1";// AND vehicle_data.Instance = ?";

        try (PreparedStatement ps = queries.prepare(query)) {
            //ps.setInt(1, instanceID);
            try (ResultSet rs = queries.executeSelect(ps)) {
                while (rs != null && rs.next()) {
                	
                	
                    String vehicleName = rs.getString("VehicleName");
                    Log.error("loadVehicleTemplates: Loading template for vehicle: " + vehicleName);
                    int vehicleID = rs.getInt("objectID");
                    Template tmpl = new Template(vehicleName, rs.getInt("objectID"), ObjectManagerPlugin.VEHICLE_TEMPLATE);

                    // Populate the template with vehicle properties
                    //tmpl.put(WorldManagerClient.NAMESPACE, "model", rs.getString("VehicleModel"));

                    tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_ID, vehicleID);
                    tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_OBJECT_TYPE, ObjectTypes.vehicle);
                    tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_PERCEPTION_RADIUS, 75);

					tmpl.put(WorldManagerClient.NAMESPACE, "nameDisplay", true);
					tmpl.put(WorldManagerClient.NAMESPACE, "targetable", true);
					
					
					
					
					LinkedList<String> displays = new LinkedList<String>();
					displays.add(HelperFunctions.readEncodedString(rs.getBytes("vehicleModel")));

					tmpl.put(WorldManagerClient.NAMESPACE, "displays", displays);
                    DisplayContext dc = new DisplayContext(rs.getString("vehicleModel"), true);
                    tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
                    
                    
					float scaleVal = rs.getFloat("scale"); // add in the scale
					//AOVector v = new AOVector(scaleVal, scaleVal, scaleVal);
					AOVector v = new AOVector(scaleVal,scaleVal,scaleVal);
					
					tmpl.put(WorldManagerClient.NAMESPACE, /*WorldManagerClient.TEMPL_SCALE*/ "scale", v);
					tmpl.put(CombatClient.NAMESPACE, CombatPlugin.PROP_HITBOX, rs.getInt("hitBox"));


					tmpl.put(CombatClient.NAMESPACE, "attackable", rs.getBoolean("attackable"));

					tmpl.put(CombatClient.NAMESPACE, WorldManagerClient.TEMPL_ID, vehicleID);
					

					
					tmpl.put(WorldManagerClient.NAMESPACE, "speed_walk", rs.getFloat("speedMin"));
					tmpl.put(WorldManagerClient.NAMESPACE, "speed_run", rs.getFloat("speedMax"));
					tmpl.put(CombatClient.NAMESPACE, ":speed_run", rs.getFloat("speedMax"));
					
					
   					String equipment = "";

					tmpl.put(InventoryClient.NAMESPACE, InventoryClient.TEMPL_ITEMS, equipment);
					if(Log.loggingDebug)Log.debug("loadMobTemplates vehicle " + vehicleName + " now has equipment: " + equipment);
    					
    					
					String tags = HelperFunctions.readEncodedString(rs.getBytes("tags"));
					tmpl.put(CombatClient.NAMESPACE, "tags",tags);
					//Integer behavior_profile_id = rs.getInt("behavior_profile_id");
					//tmpl.put(CombatClient.NAMESPACE, "behavProfile",behavior_profile_id);
		
                    
                    tmpl.put(WorldManagerClient.NAMESPACE, "speedMin", rs.getFloat("speedMin"));
                    tmpl.put(WorldManagerClient.NAMESPACE, "speedMax", rs.getFloat("speedMax"));
                    tmpl.put(WorldManagerClient.NAMESPACE, "isAttackable", rs.getBoolean("attackable"));
                    tmpl.put(WorldManagerClient.NAMESPACE, "isTargetable", rs.getBoolean("targetable"));	                

                    
                    // Register the template with ObjectManager (example logging)
                    Log.error("loadVehicleTemplates: Registering template with ObjectManager for vehicle: " + vehicleName);

                    list.add(tmpl);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Log.error("SQLException in loadVehicleTemplates: " + e.getMessage());
        }
        return list;
    }

    public static Template getVehicleTemplate(int templateID) {
        // Query to select vehicle template details for the given templateID.
        // (Adjust the field names as needed to match your schema.)
        String query = 
            "SELECT vehicle_classes.vehicleName, " +
            "       vehicle_classes.vehicleModel, " +
            "       vehicle_classes.speedMin, " +
            "       vehicle_classes.speedMax, " +
            "       vehicle_classes.attackable, " +
            "       vehicle_classes.targetable, " +
            "       vehicle_data.ObjectID " +
            "FROM vehicle_classes " +
            "INNER JOIN vehicle_data ON vehicle_classes.cehicleName = vehicle_data.classname " +
            "WHERE vehicle_data.objectID = ?";
        try (PreparedStatement ps = queries.prepare(query)) {
            ps.setInt(1, templateID);
            try (ResultSet rs = queries.executeSelect(ps)) {
                if (rs != null && rs.next()) {
                    String vehicleName = rs.getString("vehicleName");
                    int objectID = rs.getInt("ObjectID");
                    // Create a new Template for vehicles.
                    Template tmpl = new Template(vehicleName, objectID, "vehicle");
                    
                    // Put vehicle-specific properties into the template.
                    tmpl.put(WorldManagerClient.NAMESPACE, "model", rs.getString("vehicleModel"));
                    tmpl.put(WorldManagerClient.NAMESPACE, "speedMin", rs.getFloat("speedMin"));
                    tmpl.put(WorldManagerClient.NAMESPACE, "speedMax", rs.getFloat("speedMax"));
                    tmpl.put(WorldManagerClient.NAMESPACE, "isAttackable", rs.getBoolean("attackable"));
                    tmpl.put(WorldManagerClient.NAMESPACE, "isTargetable", rs.getBoolean("targetable"));
                    
                    // Create and add a display context (for example, using the vehicle model)
                    DisplayContext dc = new DisplayContext(rs.getString("vehicleModel"), true);
                    tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
                    
                    return tmpl;
                }
            }
        } catch (SQLException e) {
            Log.error("SQLException in getVehicleTemplate: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    
    public static class Vehicle {
    	private int objectid;
        private String classname;
        private String classmodel;
        private String worldspace;
        private String inventory;
        private String hitpoints;
        private double fuel;
        private double damage;

        public Vehicle(String classname, String worldspace, String inventory, String hitpoints, double fuel, double damage, int objectid) {
            this.classname = classname;
            this.worldspace = worldspace;
            this.inventory = inventory;
            this.hitpoints = hitpoints;
            this.fuel = fuel;
            this.damage = damage;
            this.objectid = objectid;
        }

        // Getters
        public int getObjectId() { return objectid; }
        public String getClassname() { return classname; }
        public String getWorldspace() { return worldspace; }
        public String getInventory() { return inventory; }
        public String getHitpoints() { return hitpoints; }
        public double getFuel() { return fuel; }
        public double getDamage() { return damage; }
    }


	public int writeVehicleData(int category, String name, int speed, int fuelCapacity, int maxPassengers) {
		// TODO Auto-generated method stub
		return 0;
	}

	public void editVehicleData(int templateID, String name, int speed, int fuelCapacity, int maxPassengers) {
		// TODO Auto-generated method stub
		
	}
	
	public Map<Integer, Integer> getSpawnCounts(Collection<Integer> instanceIDs) {
	    Map<Integer, Integer> spawnCounts = new HashMap<>();
	    if (instanceIDs == null || instanceIDs.isEmpty()) {
	        return spawnCounts;
	    }

	    String placeholders = instanceIDs.stream().map(id -> "?").collect(Collectors.joining(","));
	    String sql = "SELECT instance, COUNT(*) as spawnCount FROM vehicle_data WHERE instance IN (" + placeholders + ") GROUP BY instance";

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
	
	
	public int getSpawnCount(int instanceID) {
		int spawnCount = 0;
		Log.debug("DB: getSpawnCount loading vehicle_data count from instance: " + instanceID);
		try (PreparedStatement ps = queries.prepare("SELECT COUNT(*) FROM vehicle_data where instance = " + instanceID)) {
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

}