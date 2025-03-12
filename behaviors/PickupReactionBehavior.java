package atavism.agis.behaviors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import atavism.agis.plugins.AgisInventoryClient;
import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.messages.*;
import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.util.Log;

/**
 * Gives players that come within the reaction radius the item listed.
 * @author Andrew
 *
 */
public class PickupReactionBehavior extends Behavior {
    public PickupReactionBehavior() {
    }
    
    public PickupReactionBehavior(SpawnData data) {
    	HashMap<String, Serializable> dataProps = (HashMap<String, Serializable>)data.getProperty("props");
    	if (dataProps != null) {
    		ArrayList<OID> targets = (ArrayList<OID>)dataProps.get("acceptableTargets");
    		if (targets != null) {
    			this.acceptableTargets = targets;
    		}
    		Log.debug("PICKUP: creating pickup behav with targets:" + acceptableTargets);
    		Integer duration = (Integer)dataProps.get("duration");
    		if (duration != null) {
    			// If the spawn has a duration property it means it will only exist for the value given.
    			Despawn despawnTimer = new Despawn();
    			Engine.getExecutor().schedule(despawnTimer, duration, TimeUnit.MILLISECONDS);
    		}
    	}
    }

    public void initialize() {
        SubscriptionManager.get().subscribe(this, obj.getOid(), ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS);
    }

    public void activate() {
    	activated = true;
    	MobManagerPlugin.getTracker(obj.getInstanceOid()).addReactionRadius(obj.getOid(), radius);
    }

    public void deactivate() {
		activated = false;
        SubscriptionManager.get().unsubscribe(this);
    }

    public void handleMessage(Message msg, int flags) {
    	if (!activated) {
    		return;
    	}
    	if (msg.getMsgType() == ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS) {
    		ObjectTracker.NotifyReactionRadiusMessage nMsg = (ObjectTracker.NotifyReactionRadiusMessage)msg;
	    	if (nMsg.getInRadius()) {
	    		reaction(nMsg);
	    	}
    	}
    }

    /**
     * Give the player the item. Despawn this mob.
     * @param nMsg
     */
    public void reaction(ObjectTracker.NotifyReactionRadiusMessage nMsg) {
    	OID player = nMsg.getSubject();
    	// First verify that it is a player picking up the object
    	if (!WorldManagerClient.getObjectInfo(player).objType.equals(ObjectTypes.player))
    		return;
    	// Check to make sure the player is allowed to pick this up
    	if (!acceptableTargets.isEmpty() && !acceptableTargets.contains(player))
    		return;
    	// Send a message to give the item to the player
    	if (!activated)
    		return;
    	ExtensionMessage pickUpMsg = new ExtensionMessage(AgisInventoryClient.MSG_TYPE_PICKUP_ITEM,
                null, player);
    	pickUpMsg.setProperty("itemID", itemID);
    	pickUpMsg.setProperty("lootTables", lootTables);
    	pickUpMsg.setProperty("count", 1);
        Engine.getAgent().sendBroadcast(pickUpMsg);
        activated = false;
    	// Send a message to be caught by the spawn generator
		PropertyMessage propMsg = new PropertyMessage(this.obj.getOid());
        propMsg.setProperty("objectEmpty", true);
        Engine.getAgent().sendBroadcast(propMsg);
    }

    public void setRadius(int radius) {
    	this.radius = radius;
    }
    public int getRadius() {
    	return radius;
    }

    public void setItemID(int itemID) {
    	this.itemID = itemID;
    }
    public int getItemID() {
    	return itemID;
    }
    
    public void setLootTables(HashMap<Integer, Float> tables) {
    	this.lootTables = tables;
    }
    public HashMap<Integer, Float> getLootTables() {
    	return lootTables;
    }
    
    public void setAcceptableTargets(ArrayList<OID> targets) {
    	this.acceptableTargets = targets;
    }
    public ArrayList<OID> getAcceptableTargets() {
    	return acceptableTargets;
    }
    public void addAcceptableTarget(OID target) {
    	acceptableTargets.add(target);
    }
    
    public class Despawn implements Runnable {
		public void run() {
			if (activated) {
				// Send a message to be caught by the spawn generator
				activated = false;
				PropertyMessage propMsg = new PropertyMessage(obj.getOid());
		        propMsg.setProperty("objectEmpty", true);
		        Engine.getAgent().sendBroadcast(propMsg);
			}
		}
	}

    protected ArrayList<OID> acceptableTargets = new ArrayList<OID>();
    protected int radius = 0;
    protected int itemID = -1;
    protected HashMap<Integer, Float> lootTables = null;
    protected boolean activated = false;
    private static final long serialVersionUID = 1L;
}
