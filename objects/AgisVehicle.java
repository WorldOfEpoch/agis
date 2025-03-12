package atavism.agis.objects;

import java.io.File;
import java.io.Serializable;
import java.util.HashSet;
import atavism.agis.plugins.AgisVehicleClient;
import atavism.server.engine.WorldCollectionLoaderContext;
import atavism.server.engine.WorldLoaderOverride;
import atavism.server.engine.WorldNode;
import atavism.server.engine.OID;
import atavism.server.objects.ObjectTypes;
import atavism.server.util.FileUtil;
import atavism.server.util.Log;

public class AgisVehicle extends AgisObject {
    // Vehicle-specific properties
    private int maxPassengers;
    private int currentPassengers;
    private double maxSpeed;
    private double fuelCapacity;
    private double currentFuel;
    
    private HashSet<OID> passengers = new HashSet<>();
    private OID driverOid; // current driver
    private String factoryName;
    
    // Instance property fields (for grouping, player ownership, etc.)
    private OID groupOid;
    private OID playerOid;
    private int guildId;
    
    // New fields for "world" and initialization support
    // These fields support loading external vehicle data (e.g., 3D models, physics, etc.)
    private WorldLoaderOverride worldLoaderOverride;
    private String worldLoaderOverrideName = "default";
    private String worldFileName;
    private WorldCollectionLoaderContext loaderContext;
    private String initScriptFileName;
    private String loadScriptFileName;
    
    // Vehicle "state" (for example, available, loading, etc.)
    private int state;
    
    public AgisVehicle() {
        super();
        initVehicle();
    }
    
    public AgisVehicle(String name) {
        super();
        setName(name);
        initVehicle();
    }
    
    private void initVehicle() {
        // Set a default type (you may later change this as needed)
        setType(ObjectTypes.unknown);
        if (Log.loggingDebug)
            Log.error("AgisVehicle.init: name=" + getName() + ", perceiver=" + perceiver());
            
        // Initialize vehicle-specific properties
        this.maxPassengers = 4;   // Default maximum passengers
        this.currentPassengers = 0;
        this.maxSpeed = 100.0;    // Default max speed
        this.fuelCapacity = 50.0; // Default fuel capacity
        this.currentFuel = this.fuelCapacity;
    }
    
    // === Basic property setters and getters ===
    
    // Set the vehicle's state (for example, Instance.STATE_AVAILABLE)
    public void setState(int state) {
        this.state = state;
    }
    
    public int getState() {
        return this.state;
    }
    
    // Set and get groupOid.
    public void setGroupOid(OID groupOid) {
        this.groupOid = groupOid;
    }
    
    public OID getGroupOid() {
        return this.groupOid;
    }
    
    // Set and get playerOid.
    public void setPlayerOid(OID playerOid) {
        this.playerOid = playerOid;
    }
    
    public OID getPlayerOid() {
        return this.playerOid;
    }
    
    // Set and get guildId.
    public void setGuildId(int guildId) {
        this.guildId = guildId;
    }
    
    public int getGuildId() {
        return this.guildId;
    }
    
    // === World Loader and Initialization Support Functions ===
    
    // Get the name of the world loader override.
    public String getWorldLoaderOverrideName() {
        return this.worldLoaderOverrideName;
    }
    
    // Set the world loader override.
    public void setWorldLoaderOverride(WorldLoaderOverride override) {
        this.worldLoaderOverride = override;
    }
    
    // Get the world loader override.
    public WorldLoaderOverride getWorldLoaderOverride() {
        return this.worldLoaderOverride;
    }
    
    // Get and set the world file name.
    public String getWorldFileName() {
        return this.worldFileName;
    }
    
    public void setWorldFileName(String worldFileName) {
        this.worldFileName = worldFileName;
    }
    
    // Set the world collection loader context.
    public void setWorldCollectionLoaderContext(WorldCollectionLoaderContext context) {
        this.loaderContext = context;
    }
    
    // Get the world collection loader context.
    public WorldCollectionLoaderContext getWorldCollectionLoaderContext() {
        return this.loaderContext;
    }
    
    // Get and set the initialization script filename.
    public String getInitScriptFileName() {
        return this.initScriptFileName;
    }
    
    public void setInitScriptFileName(String initScriptFileName) {
        this.initScriptFileName = initScriptFileName;
    }
    
    // Get and set the load script filename.
    public String getLoadScriptFileName() {
        return this.loadScriptFileName;
    }
    
    public void setLoadScriptFileName(String loadScriptFileName) {
        this.loadScriptFileName = loadScriptFileName;
    }
    
    // Dummy implementation of loading the "world" data.
    // In a real system, this might load a 3D model, physics parameters, etc.
    public boolean loadWorldData() {
        if (this.worldFileName != null && !this.worldFileName.isEmpty()) {
            Log.error("AgisVehicle.loadWorldData: Loading world data from " + this.worldFileName);
            // ... Insert actual loading code here ...
            return true;
        }
        // If no world file is specified, assume no extra data is needed.
        return true;
    }
    
    // Dummy implementation for loading additional world collections.
    public boolean loadWorldCollections() {
        if (this.loaderContext != null) {
            Log.error("AgisVehicle.loadWorldCollections: Loading collections using context " + this.loaderContext);
            // ... Insert actual collection loading code here ...
            return true;
        }
        return true;
    }
    
    // Dummy implementation for running the initialization script.
    public boolean runInitScript() {
        if (this.initScriptFileName != null && !this.initScriptFileName.isEmpty()) {
            Log.error("AgisVehicle.runInitScript: Running init script " + this.initScriptFileName);
            // ... Insert actual script execution code here ...
            return true;
        }
        return true;
    }
    
    // === Other Vehicle-Specific Methods (as before) ===
    
    public void drive() {
        if (this.currentFuel > 0) {
            // Implement driving logic, e.g., update position, decrease fuel.
        } else {
            // Handle no fuel condition.
        }
    }
    
    public boolean changeDriver(OID newDriverOid) {
        if (passengers.contains(newDriverOid)) {
            this.driverOid = newDriverOid;
            return true;
        }
        return false;
    }
    
    public boolean enterVehicle(OID passengerOid) {
        if (this.currentPassengers < this.maxPassengers) {
            this.currentPassengers++;
            passengers.add(passengerOid);
            if (this.driverOid == null) {
                this.driverOid = passengerOid;
            }
            return true;
        }
        return false;
    }
    
    public boolean exitVehicle(OID passengerOid) {
        if (this.currentPassengers > 0 && passengers.contains(passengerOid)) {
            this.currentPassengers--;
            passengers.remove(passengerOid);
            if (passengerOid.equals(this.driverOid)) {
                if (!passengers.isEmpty()) {
                    this.driverOid = passengers.iterator().next();
                } else {
                    this.driverOid = null;
                }
            }
            return true;
        }
        return false;
    }
    
    public boolean setDriver(OID playerOid) {
        this.driverOid = playerOid;
        return true;
    }
    
    public void drive(Serializable movementCommand) {
        if (this.driverOid != null && this.currentFuel > 0) {
            // Implement driving logic based on movementCommand.
        }
    }
    
    public void stopDriving() {
        // Implement logic to stop the vehicle.
    }
    
    public OID getDriverOid() {
        return this.driverOid;
    }
    
    @Override
    public void worldNode(WorldNode worldNode) {
        super.worldNode(worldNode);
        // When the vehicle's world node is updated, update the location for all passengers.
        for (OID passengerOid : passengers) {
            AgisVehicleClient.updatePlayerLocation(passengerOid, worldNode);
        }
    }

    /*     */   public void setFactoryName(String factoryName) {
    	/*     */     this.factoryName = factoryName;
    	/*     */   }
}
