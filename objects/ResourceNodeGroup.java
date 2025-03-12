package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import atavism.agis.plugins.CraftingPlugin;
import atavism.msgsys.Message;
import atavism.msgsys.MessageDispatch;
import atavism.msgsys.SubjectFilter;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Engine;
import atavism.server.engine.InterpolatedWorldNode;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.objects.DisplayContext;
import atavism.server.objects.Entity;
import atavism.server.objects.EntityManager;
import atavism.server.objects.EntityWithWorldNode;
import atavism.server.objects.ObjectStub;
import atavism.server.objects.ObjectTracker;
import atavism.server.objects.ObjectTypes;
import atavism.server.objects.Template;
import atavism.server.plugins.MobManagerPlugin;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;

/**
 * A ResourceNode is an object players can gather items from. The ResourceNode randomly generates its items upon spawn
 * from the items it has been given and allows the player to gather them if they meet the requirements.
 * @author Andrew Harrison
 *
 */
public class ResourceNodeGroup implements Serializable, MessageDispatch {
    public ResourceNodeGroup() {
    }
    
    public ResourceNodeGroup(AOVector loc,  OID instanceOID) {
    	this.loc = loc;
    	this.instanceOID = instanceOID;
    }
    
    public void AddResourceNode(ResourceNode node) {
    	nodes.add(node);
    }
    
    /**
     * Subscribes the instance to receive certain relevant messages that are sent to the world object 
     * created by this instance.
     */
    public void activate() {
    	SubjectFilter filter = new SubjectFilter(objectOID);
        filter.addType(ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS);
        eventSub = Engine.getAgent().createSubscription(filter, this);
        // Set the reaction radius tracker to alert the object if a player has entered its draw radius
        MobManagerPlugin.getTracker(instanceOID).addReactionRadius(objectOID, CraftingPlugin.RESOURCE_GROUP_SIZE*4);
        active = true;
        Log.debug("RESOURCE: node with oid: " + objectOID + " activated");
    }
    
    /**
     * Deals with the messages the instance has picked up.
     */
    public void handleMessage(Message msg, int flags) {
    	if (active == false) {
    	    return;
    	}
    	if (msg.getMsgType() == ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS) {
    	    ObjectTracker.NotifyReactionRadiusMessage nMsg = (ObjectTracker.NotifyReactionRadiusMessage)msg;
     	    Log.debug("RESOURCE: myOid=" + objectOID + " objOid=" + nMsg.getSubject() + " inRadius=" + nMsg.getInRadius() + " wasInRadius=" + nMsg.getWasInRadius());
    	    if (nMsg.getInRadius()) {
    	    	addPlayer(nMsg.getSubject());
    	    } else {
    	    	// Remove subject from targets in range
    	    	removePlayer(nMsg.getSubject());
    	    }
    	}
    }
    
    /**
     * An external call to spawn a world object for the claim.
     * @param instanceOID
     */
    public void spawn(OID instanceOID) {
    	this.instanceOID = instanceOID;
    	spawn();
    }
    
    /**
     * Spawn a world object for the claim.
     */
    public void spawn() {
    	Template markerTemplate = new Template();
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_NAME, "_ign_resource" + loc.getX() + "_" + loc.getY());
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_OBJECT_TYPE, ObjectTypes.mob);
    	markerTemplate.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_PERCEPTION_RADIUS, CraftingPlugin.RESOURCE_GROUP_SIZE);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
    	//markerTemplate.put(Namespace.COMBAT, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_LOC, new Point(loc));
    	//markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_ORIENT, orientation);
    	DisplayContext dc = new DisplayContext(gameObject, true);
		dc.addSubmesh(new DisplayContext.Submesh("", ""));
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
		markerTemplate.put(Namespace.WORLD_MANAGER, "model", gameObject); 
    	// Put in any additional props
    	if (props != null) {
    		for (String propName : props.keySet()) {
    			markerTemplate.put(Namespace.WORLD_MANAGER, propName, props.get(propName));
    		}
    	}
    	// Create the object
    	objectOID = ObjectManagerClient.generateObject(ObjectManagerClient.BASE_TEMPLATE_ID, ObjectManagerClient.BASE_TEMPLATE, markerTemplate);
    	
    	if (objectOID != null) {
    		// Need to create an interpolated world node to add a tracker/reaction radius to the claim world object
    		BasicWorldNode bwNode = WorldManagerClient.getWorldNode(objectOID);
    		InterpolatedWorldNode iwNode = new InterpolatedWorldNode(bwNode);
    		resourceNodeEntity = new ResourceNodeEntity(objectOID, iwNode);
    		EntityManager.registerEntityByNamespace(resourceNodeEntity, Namespace.MOB);
    		MobManagerPlugin.getTracker(instanceOID).addLocalObject(objectOID, 100);
    		
            WorldManagerClient.spawn(objectOID);
            Log.debug("RESOURCE: spawned resource at : " + loc);
            activate();
            
            /*for(ResourceNode node : nodes) {
            	node.activateAsChildOfGroup();
            }*/
        }
    }
    
    /**
     * Add a player to the update list for this ResourceNode. The player will receive data about the node and any updates
     * that occur.
     * @param playerOID
     */
    public void addPlayer(OID playerOid) {
    	for (ResourceNode node : nodes) {
    		node.addPlayer(playerOid);
    	}
    }
    
    /**
     * Removes a player from the ResourceNode. They will no longer receive updates.
     * @param playerOID
     * @param removeLastID
     */
    public void removePlayer(OID playerOid) {
    	for (ResourceNode node : nodes) {
    		node.removePlayer(playerOid);
    	}
    }
    
    public AOVector getLoc() {
    	return loc;
    }
    
    ArrayList<ResourceNode> nodes = new ArrayList<ResourceNode>();
    
    String gameObject;
    AOVector loc;
    int respawnTime;
    OID instanceOID;
    OID objectOID;
    HashMap<String, Serializable> props;
    boolean active;
    Long eventSub = null;
    
    Long sub = null;
    ResourceNodeEntity resourceNodeEntity;
    
    
    /**
     * Sub-class needed for the interpolated world node so a perceiver can be created.
     * @author Andrew
     *
     */
	public class ResourceNodeEntity extends ObjectStub implements EntityWithWorldNode
	{

		public ResourceNodeEntity(OID oid, InterpolatedWorldNode node) {
	    	setWorldNode(node);
	    	setOid(oid);
	    }
		
		private static final long serialVersionUID = 1L;
	}
	
	private static final long serialVersionUID = 1L;
}