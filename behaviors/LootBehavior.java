package atavism.agis.behaviors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import atavism.agis.objects.MobLootTable;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.AgisMobPlugin;
import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.messages.*;
import atavism.server.objects.*;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;
import atavism.server.util.Log;

/**
 * Gives players that come within the reaction radius the item listed.
 * @author Andrew
 *
 */
public class LootBehavior extends Behavior {
    public LootBehavior() {
    }
    
    public LootBehavior(SpawnData data) {
    	HashMap<String, Serializable> dataProps = (HashMap<String, Serializable>)data.getProperty("props");
    	if (dataProps != null) {
    		ArrayList<OID> targets = (ArrayList<OID>)dataProps.get("acceptableTargets");
    		if (targets != null) {
    			this.acceptableTargets = targets;
    		}
    		Log.debug("LOOT: creating loot behav with targets:" + acceptableTargets);
    		//Integer duration = (Integer)dataProps.get("duration");
    		//if (duration != null) {
    			// If the spawn has a duration property it means it will only exist for the value given.
    			Despawn despawnTimer = new Despawn();
    			Engine.getExecutor().schedule(despawnTimer, AgisMobPlugin.lootObjectDespawn, TimeUnit.SECONDS);
    		//}
    	}
    }

    public void initialize() {
        /*SubjectFilter filter = new SubjectFilter(obj.getOid());
        filter.addType(ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS);
        eventSub = Engine.getAgent().createSubscription(filter, this);*/
        SubscriptionManager.get().subscribe(this, obj.getOid(), PropertyMessage.MSG_TYPE_PROPERTY);
    }

    public void activate() {
    	activated = true;
    	//MobManagerPlugin.getTracker(obj.getInstanceOid()).addReactionRadius(obj.getOid(), radius);
    	if (acceptableTargets != null) {
    		for(OID target : acceptableTargets) {
    			TargetedPropertyMessage propMsg = new TargetedPropertyMessage(target, obj.getOid());
    			propMsg.setProperty("lootable", true);
    			Engine.getAgent().sendBroadcast(propMsg);
    			Log.debug("LOOT: set lootable to player: " + target + " for obj: " + obj.getOid());
    		}
    		AgisInventoryClient.generateLoot(obj.getOid(), null);
    	}
    }

    public void deactivate() {
		activated = false;
        SubscriptionManager.get().unsubscribe(this);
    }

    public void handleMessage(Message msg, int flags) {
    	if (activated == false) {
    		return;
    	}
    	/*if (msg.getMsgType() == ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS) {
    		ObjectTracker.NotifyReactionRadiusMessage nMsg = (ObjectTracker.NotifyReactionRadiusMessage)msg;
	    	if (nMsg.getInRadius()) {
	    		reaction(nMsg);
	    	}
    	}*/
    	 if (msg instanceof PropertyMessage) {
 	    	PropertyMessage propMsg = (PropertyMessage) msg;
 		    OID subject = propMsg.getSubject();
 		    LinkedList<OID> loot = (LinkedList<OID>)propMsg.getProperty("loot");
 		    if (loot != null && loot.isEmpty()) {
 		    	if (activated) {
 		    		Log.debug("LOOT: despawning loot object as it is now empty");
 					// Send a message to be caught by the spawn generator
 					activated = false;
 					PropertyMessage newPropMsg = new PropertyMessage(obj.getOid());
 					newPropMsg.setProperty("objectEmpty", true);
 			        Engine.getAgent().sendBroadcast(newPropMsg);
 				}
 		    }
    	 }
    }

    /*
     * Give the player the item. Despawn this mob.
     * @param nMsg
     */
    /*public void reaction(ObjectTracker.NotifyReactionRadiusMessage nMsg) {
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
    }*/

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
    public void setAcceptableTargets(ArrayList<OID> targets) {
    	this.acceptableTargets = targets;
    }
    public ArrayList<OID> getAcceptableTargets() {
    	return acceptableTargets;
    }
    public void addAcceptableTarget(OID target) {
    	acceptableTargets.add(target);
    }

    public void setLootTables(HashMap<Integer, MobLootTable> lootTables) {
        this.lootTables = lootTables;
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
    protected HashMap<Integer, MobLootTable> lootTables = null;
    protected boolean activated = false;
    private static final long serialVersionUID = 1L;
}
