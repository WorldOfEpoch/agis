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

public class WorldObject implements Serializable, Runnable {
    public WorldObject() {
    }
    
    public WorldObject(int id, Point loc, OID instanceOID, String model, HashMap<String, Serializable> props) {
    	this(id, loc, new Quaternion(0, 0, 0, 1), instanceOID, model, props);
    }
    
    public WorldObject(int id, Point loc, Quaternion orientation, OID instanceOID, String model,
    		HashMap<String, Serializable> props) {
    	this.id = id;
    	this.loc = loc;
    	this.orientation = orientation;
    	this.instanceOID = instanceOID;
    	this.props = props;
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
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_NAME, "WorldObject" + id);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_OBJECT_TYPE, WorldManagerClient.TEMPL_OBJECT_TYPE_STRUCTURE);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
    	//markerTemplate.put(Namespace.COMBAT, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
        markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_LOC, loc);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_ORIENT, orientation);
    	DisplayContext dc = new DisplayContext(model, true);
		dc.addSubmesh(new DisplayContext.Submesh("", ""));
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
    
    public String getModel() { return model;}
    public void setModel(String model) {
    	this.model = model;
    }
    
    public boolean getActive() { return active;}
    public void setActive(boolean active) {
    	this.active = active;
    }

    int id;
    Point loc;
    Quaternion orientation;
    String model;
    OID instanceOID;
    OID objectOID;
    HashMap<String, Serializable> props;
    boolean active;

    private static final long serialVersionUID = 1L;
}