package atavism.agis.core;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.objects.ObjectTypes;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.*;
import atavism.agis.objects.*;

/**
 * An acquire hook for items that turn into a building resource
 * when acquired.
 */
public class BuildingResourceAcquireHook implements AcquireHook {
    public BuildingResourceAcquireHook() {
    	super();
    }

    public BuildingResourceAcquireHook(int resourceID) {
    	super();
    	setResourceID(resourceID);
    }

    public void setResourceID(int resourceID) {
        if (resourceID == -1) {
            throw new RuntimeException("BuildingResourceAcquireHook.setResource: bad resource");
        }
        this.resourceID = resourceID;
    }
    public int getResourceID() {
    	return resourceID;
    }
    protected int resourceID;

    /**
     * Adds the item to the Building Resources map for the player and returns true telling the item to be
     * destroyed.
     */
    public boolean acquired(OID activatorOid, AgisItem item) {
        if (Log.loggingDebug)
            Log.debug("BuildingResourceAcquireHook.activate: activator=" + activatorOid + " item=" + item + " resource=" + resourceID);
        // Only convert it if it is acquired by a player
        if (WorldManagerClient.getObjectInfo(activatorOid).objType != ObjectTypes.player) {
        	return false;
        }
        int resourceAmount  = item.getStackSize();
        String resourceKey = "" + resourceID;
        // Add building resource
        HashMap<String, Integer> buildingResources = (HashMap)EnginePlugin.getObjectProperty(activatorOid, WorldManagerClient.NAMESPACE, "buildingResources");
        if (buildingResources == null) {
        	buildingResources = new HashMap<String, Integer>();
        }
        
        if (buildingResources.containsKey(resourceKey)) {
        	resourceAmount += buildingResources.get(resourceKey);
        }
        buildingResources.put(resourceKey, resourceAmount);
        EnginePlugin.setObjectProperty(activatorOid, WorldManagerClient.NAMESPACE, "buildingResources", buildingResources);
        // Now send down a message to the client
        sendBuildingResources(activatorOid, buildingResources);
        return true;
    }
    
    /**
     * Sends down the map of Building Resources the player has to the client. Static function so can be called from anywhere.
     * @param oid
     * @param resources
     */
    public static void sendBuildingResources(OID oid, HashMap<String, Integer> resources) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "buildingResources");
        int numResources = 0;
        for (String resourceID : resources.keySet()) {
        	Log.debug("RESOURCE: got currency to send: " + resourceID);
        	props.put("resource" + numResources + "ID", resourceID);
        	props.put("resource" + numResources + "Count", resources.get(resourceID));
        	numResources++;
        }
        
        props.put("numResources", numResources);
        TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION,
        		oid, oid, false, props);
	    Engine.getAgent().sendBroadcast(msg);
	    Log.debug("RESOURCES: sending down building resources message to: " + oid + " with props: " + props);
	}
    
    public String toString() {
    	return "BuildingResourceAcquireHook=" + resourceID;
    }

    private static final long serialVersionUID = 1L;
}
