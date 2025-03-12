package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import atavism.server.engine.Engine;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.objects.DisplayContext;
import atavism.server.objects.Template;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;

public class ArenaObject implements Serializable, Runnable {
    public ArenaObject() {
    }
    
    public ArenaObject(int id, Point loc, OID instanceOID, String objectType, DisplayContext dc, HashMap<String, Serializable> props) {
    	this(id, loc, new Quaternion(0, 0, 0, 1), instanceOID, objectType, dc, props);
    }
    
    public ArenaObject(int id, Point loc, Quaternion orientation, OID instanceOID, String objectType, DisplayContext dc, 
    		HashMap<String, Serializable> props) {
    	this.id = id;
    	this.loc = loc;
    	this.orientation = orientation;
    	this.instanceOID = instanceOID;
    	this.objectType = objectType;
    	this.dc = dc;
    	this.props = props;
    	this.teamToReactTo = -1;
    	if (dc != null)
    		spawn();
    }
    
    public void respawn(int time) {
    	Engine.getExecutor().schedule(this, time, TimeUnit.SECONDS);
    }
    
    /**
     * Respawns the object
     */
    public void run() {
    	spawn();
    }
    
    protected void spawn() {
    	Template markerTemplate = new Template();
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_NAME, objectType + id);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_OBJECT_TYPE, WorldManagerClient.TEMPL_OBJECT_TYPE_STRUCTURE);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
    	//markerTemplate.put(Namespace.COMBAT, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_LOC, loc);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_ORIENT, orientation);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
    	// Put in any additional props
    	if (props != null) {
    		for (String propName : props.keySet()) {
    			markerTemplate.put(Namespace.WORLD_MANAGER, propName, props.get(propName));
    		}
    	}
    	objectOID = ObjectManagerClient.generateObject(ObjectManagerClient.BASE_TEMPLATE_ID,
                ObjectManagerClient.BASE_TEMPLATE, markerTemplate);
        if (objectOID != null) {
            WorldManagerClient.spawn(objectOID);
            //Log.debug("ARENA: spawned dot at : " + loc);
            active = true;
        }
    }

	public int getID() { return id;}
    public void setID(int id) {
    	this.id = id;
    }
    
    public Point getLoc() { return loc;}
    public void setLoc(Point loc) {
    	this.loc = loc;
    }
    
    public Quaternion getOrientation() { return orientation;}
    public void setOrientation(Quaternion orientation) {
    	this.orientation = orientation;
    }
    
    public HashMap<String, Serializable> getProps() { return props;}
    public void setProps(HashMap<String, Serializable> props) {
    	this.props = props;
    }
    
    public OID getInstanceOID() { return instanceOID;}
    public void setInstanceOID(OID instanceOID) {
    	this.instanceOID = instanceOID;
    }
    
    public OID getObjectOID() { return objectOID;}
    public void setObjectOID(OID objectOID) {
    	this.objectOID = objectOID;
    }
    
    public String getObjectType() { return objectType;}
    public void setObjectType(String objectType) {
    	this.objectType = objectType;
    }
    
    public String getData() { return data;}
    public void setData(String data) {
    	this.data = data;
    }
    
    public int getTeamToReactTo() { return teamToReactTo;}
    public void setTeamToReactTo(int teamToReactTo) {
    	this.teamToReactTo = teamToReactTo;
    }
    
    public boolean getActive() { return active;}
    public void setActive(boolean active) {
    	this.active = active;
    }

    int id;
    Point loc;
    Quaternion orientation;
    DisplayContext dc;
    OID instanceOID;
    OID objectOID;
    String objectType;
    String data;
    int teamToReactTo;
    HashMap<String, Serializable> props;
    boolean active;

    private static final long serialVersionUID = 1L;
    public static final String ARENA_OBJECT_GATE = "Gate";
    public static final String ARENA_OBJECT_DOT = "Dot";
    public static final String ARENA_OBJECT_ABILITY = "Star";
    public static final String ARENA_OBJECT_TRAP = "Trap";
    public static final String ARENA_OBJECT_BOMB = "Bomb";
    public static final String ARENA_OBJECT_POWERUP = "Powerup";
    public static final String ARENA_OBJECT_DESTRUCTABLE_WALL = "Wall";
    public static final String ARENA_OBJECT_INDESTRUCTABLE_WALL = "Indestructable Wall";
    public static final String ARENA_OBJECT_FLAG = "Flag";
    public static final String ARENA_OBJECT_FLAG_PLATFORM = "Flag Platform";
    public static final String ARENA_OBJECT_MACHINE = "Machine";
    public static final String ARENA_OBJECT_MELEE_WEAPON = "Melee Weapon";
    public static final String ARENA_OBJECT_RANGED_WEAPON = "Ranged Weapon";
    public static final String ARENA_OBJECT_HEALTH = "Health";
}
