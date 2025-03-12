package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Random;

import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.objects.DisplayContext;
import atavism.server.objects.Template;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;

public class ArenaWeaponObject extends ArenaObject {
    public ArenaWeaponObject() {
    }
    
    public ArenaWeaponObject(int id, Point loc, OID instanceOID, DisplayContext meleeDC, DisplayContext rangedDC, 
    		HashMap<String, Serializable> props) {
    	this(id, loc, new Quaternion(0, 0, 0, 1), instanceOID, meleeDC, rangedDC, props);
    }
    
    public ArenaWeaponObject(int id, Point loc, Quaternion orientation, OID instanceOID, DisplayContext meleeDC, 
    		DisplayContext rangedDC, HashMap<String, Serializable> props) {
    	this.id = id;
    	this.loc = loc;
    	this.orientation = orientation;
    	this.instanceOID = instanceOID;
    	this.props = props;
    	this.teamToReactTo = -1;
    	this.meleeDC = meleeDC;
    	this.rangedDC = rangedDC;
    	spawn();
    }
    
    protected void spawn() {
    	// Each time this is going to spawn randomise the weapon type
    	Random rand = new Random();
    	if (rand.nextBoolean()) {
    		dc = meleeDC;
    		objectType = meleeObjectType;
    	} else {
    		dc = rangedDC;
    		objectType = rangedObjectType;
    	}
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
    
    protected DisplayContext meleeDC;
    protected String meleeObjectType = ArenaObject.ARENA_OBJECT_MELEE_WEAPON;
    
    protected DisplayContext rangedDC;
    protected String rangedObjectType = ArenaObject.ARENA_OBJECT_RANGED_WEAPON;
    
    private static final long serialVersionUID = 1L;
}
