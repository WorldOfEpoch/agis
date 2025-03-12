package atavism.agis.effects;

import atavism.server.engine.*;
import atavism.server.objects.Marker;
import atavism.agis.objects.*;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.agis.plugins.AgisWorldManagerClient;
import atavism.agis.plugins.ArenaClient;
import atavism.server.math.*;
import atavism.server.plugins.InstanceClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;

/**
 * Effect child class that teleports the target somewhere. Also handles the special case of item based 
 * teleportation (such as hearth stones).
 * @author Andrew Harrison
 *
 */
public class TeleportEffect extends AgisEffect {

    public TeleportEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("TeleportEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("TeleportEffect: this effect is not for buildings");
			return;
		}
		CombatInfo obj = state.getTarget();
	    BasicWorldNode wnode = WorldManagerClient.getWorldNode(obj.getOid());
	    OID instanceOid = null;
	    Point loc = location;
    	if (instanceID < 1) {
    		instanceOid = wnode.getInstanceOid();
    	}
	    if (teleportType.equals("Item")) {
	    	AgisItem item = state.getItem();
	    	if (item == null)
	    		return;
	    	loc = (Point) item.getProperty("teleportLoc");
	    	wnode.setLoc(loc);
	    	int hearthInstance = (Integer) item.getProperty("teleportInstance");
	    	if (hearthInstance > 1) {
	    		AgisWorldManagerClient.sendChangeInstance(obj.getOid(), hearthInstance, loc);
	    		return;
	    	}
	    } else if (teleportType.equals("To Target")) {
	    	// Get the loc of the target and then set the target to the source and teleport them
	    	loc = wnode.getLoc();
	    	instanceOid = wnode.getInstanceOid();
	    	obj = state.getSource();
	    	wnode = WorldManagerClient.getWorldNode(obj.getOid());
	    	wnode.setLoc(loc);
	    	wnode.setInstanceOid(instanceOid);
	    } else if(teleportType.equals("Target To Caster")) {
	    	loc = wnode.getLoc();
	    	instanceOid = wnode.getInstanceOid();
	    	obj = state.getSource();
	    	BasicWorldNode wnodeC = WorldManagerClient.getWorldNode(obj.getOid());
	    	wnodeC.setLoc(wnode.getLoc());
	    	wnodeC.setInstanceOid(instanceOid);
	    	
	    	 if (instanceID < 1 || instanceOid == null || instanceOid.equals(wnode.getInstanceOid())) {
	 	    	wnode.setInstanceOid(wnode.getInstanceOid());
	 	    	WorldManagerClient.updateWorldNode(obj.getOid(), wnode, true);
	 	    } else {
	 	    	AgisWorldManagerClient.sendChangeInstance(obj.getOid(), instanceID, loc);
	 	    }
	    	return;
	    } else if(teleportType.equals("Forward")) {
	    	loc = wnode.getLoc();
	    	Quaternion dir =wnode.getOrientation();
	    	AOVector vecrot = Quaternion.multiply(dir, new AOVector(0,0,1));
	    	//vecrot.
	    //	Point newLoc = dir. * (new Point(0f,0f,1f));
	    	wnode.setLoc(location);
	    	loc = location;
	    	if (instanceID < 1) {
	    		WorldManagerClient.updateWorldNode(obj.getOid(), wnode, true);
	    	} else {
	    		AgisWorldManagerClient.sendChangeInstance(obj.getOid(), instanceID, loc);
	    	}
	    	return;
	    } else if (teleportType.equals("Standard")) {
	    	wnode.setLoc(location);
	    	loc = location;
	    	if (instanceID < 1) {
	    		WorldManagerClient.updateWorldNode(obj.getOid(), wnode, true);
	    	} else {
	    		AgisWorldManagerClient.sendChangeInstance(obj.getOid(), instanceID, loc);
	    	}
	    	return;
	    } else {
	    	Marker m = InstanceClient.getMarker(instanceOid, teleportType);
	    	loc = m.getPoint();
	    	wnode.setLoc(loc);
	    }
	    
	    if (instanceID < 1 || instanceOid == null || instanceOid.equals(wnode.getInstanceOid())) {
	    	wnode.setInstanceOid(wnode.getInstanceOid());
	    	WorldManagerClient.updateWorldNode(obj.getOid(), wnode, true);
	    } else {
	    	AgisWorldManagerClient.sendChangeInstance(obj.getOid(), instanceID, loc);
	    }
	    // They have left the arena
	 //   ArenaClient.removePlayer(obj.getOid());
    }


    public Point getTeleportLocation() { return location; }
    public void setTeleportLocation(Point loc) { location = loc; }
    protected Point location = null;
    
    public String getTeleportType() { return teleportType; }
    public void setTeleportType(String teleportType) { this.teleportType = teleportType; }
    protected String teleportType = null;
    
    public int getInstance() { return instanceID; }
    public void setInstance(int instanceID) { this.instanceID = instanceID; }
    protected int instanceID = -1;
    
    private static final long serialVersionUID = 1L;
}
