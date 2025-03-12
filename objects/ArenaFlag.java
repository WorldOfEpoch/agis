package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import atavism.agis.arenas.*;
import atavism.server.engine.Engine;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.objects.DisplayContext;
import atavism.server.objects.Template;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;

public class ArenaFlag extends ArenaObject {
    public ArenaFlag() {
    }
    
    public ArenaFlag(int id, Point loc, OID instanceOID, String objectType, 
    		HashMap<String, Serializable> props, int team, boolean reactToAllTeams, CaptureTheFlagArena arena) {
    	this(id, loc, new Quaternion(0, 0, 0, 1), instanceOID, objectType, props, team, 
    			reactToAllTeams, arena);
    }
    
    public ArenaFlag(int id, Point loc, Quaternion orientation, OID instanceOID, String objectType, 
    		HashMap<String, Serializable> props, int team, boolean reactToAllTeams, CaptureTheFlagArena arena) {
    	this.id = id;
    	this.loc = loc;
    	this.orientation = orientation;
    	this.instanceOID = instanceOID;
    	this.objectType = objectType;
    	this.arena = arena;
    	this.dc = getFlagDC(team);
    	this.props = props;
    	this.teamToReactTo = -1;
    	this.team = team;
    	this.reactToAllTeams = reactToAllTeams;
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
    	markerTemplate.put(WorldManagerClient.NAMESPACE, "targetable", false);
    	// Put in any additional props
    	if (props != null) {
    		for (String propName : props.keySet()) {
    			markerTemplate.put(Namespace.WORLD_MANAGER, propName, props.get(propName));
    		}
    	}
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
    	markerTemplate.put(Namespace.WORLD_MANAGER, "StaticAnim", "base");
    	objectOID = ObjectManagerClient.generateObject(ObjectManagerClient.BASE_TEMPLATE_ID,
                ObjectManagerClient.BASE_TEMPLATE, markerTemplate);
        if (objectOID != null) {
            WorldManagerClient.spawn(objectOID);
            active = true;
        }
        Engine.getExecutor().schedule(this, 500, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Set flag clickable
     */
    public void run() {
    	Log.debug("FLAG: about to mark flag as clickable with team: " + team);
    	arena.setFlagClickable(this, reactToAllTeams);
    }
    
    public int getTeam() { return team; }
    
    protected int team;
    protected boolean reactToAllTeams;
    protected CaptureTheFlagArena arena;
    
    public DisplayContext getFlagDC(int team) {
    	DisplayContext flagDC = new DisplayContext(arena.getFlagMesh(), true);	  
    	flagDC.setDisplayID(arena.getFlagDisplayID(team));
    	return flagDC;
    }
    
    private static final long serialVersionUID = 1L;
}