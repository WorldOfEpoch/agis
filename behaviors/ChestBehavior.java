package atavism.agis.behaviors;

import java.util.*;

import atavism.agis.objects.*;
import atavism.agis.plugins.*;
import atavism.msgsys.*;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;
import atavism.server.messages.*;
import atavism.server.objects.*;
import atavism.server.engine.*;
import atavism.server.util.*;

public class ChestBehavior extends Behavior implements Runnable {

    public void initialize() {

        OID mobOid = this.getObjectStub().getOid();
        if (Log.loggingDebug)
            log.debug("QuestBehavior.initialize: my moboid=" + mobOid);

        SubscriptionManager.get().subscribe(this, mobOid, WorldManagerClient.MSG_TYPE_UPDATE_OBJECT,
                AgisInventoryClient.MSG_TYPE_REQ_OPEN_MOB, PropertyMessage.MSG_TYPE_PROPERTY);
    }

    public void activate() {
    	if (!singleItemPickup)
    		AgisInventoryClient.generateLoot(getObjectStub().getOid(), null);
    }

    public void deactivate() {
        SubscriptionManager.get().unsubscribe(this);
    }

    public void handleMessage(Message msg, int flags) {
        if (msg instanceof AgisInventoryClient.RequestOpenMobMessage) {
        	AgisInventoryClient.RequestOpenMobMessage reqMsg = (AgisInventoryClient.RequestOpenMobMessage) msg;
        	processReqOpenMobMsg(reqMsg);
        } else if (msg instanceof WorldManagerClient.UpdateMessage) {
            WorldManagerClient.UpdateMessage updateMsg = (WorldManagerClient.UpdateMessage) msg;
            processUpdateMsg(updateMsg);
        }else if (msg instanceof PropertyMessage) {
		    PropertyMessage propMsg = (PropertyMessage) msg;
		    Boolean lootable = (Boolean)propMsg.getProperty("lootable");
		    if (lootable != null) {
		    	if (!lootable) {
		    		Log.debug("CHEST: got lootable prop: " + lootable);
		    		PropertyMessage propMsg2 = new PropertyMessage(this.obj.getOid());
		    		propMsg2.setProperty("objectEmpty", true);
		    		Engine.getAgent().sendBroadcast(propMsg2);
		    	}
		    }
        } else {
            log.error("onMessage: got unknown msg: " + msg);
            return; //return false;
        }
        //return true;
    }
    
    public void processUpdateMsg(WorldManagerClient.UpdateMessage msg) {
        OID myOid = msg.getSubject();
        OID playerOid = msg.getTarget();
        //Log.debug("CHEST: got update message for player: " + playerOid);
        //if (Log.loggingDebug)
        //    log.debug("processUpdateMsg: myOid=" + myOid + ", playerOid=" + playerOid);

        //if (!myOid.equals(this.getObjectStub().getOid())) {
        //    log.debug("processUpdateMsg: oids dont match!");
        //}

        TargetedPropertyMessage propMsg = new TargetedPropertyMessage(playerOid, myOid);
        propMsg.setProperty(AgisStates.ItemAvailable.toString(), true);
        Engine.getAgent().sendBroadcast(propMsg);
    }
     
    protected void giveItemsToPlayer(OID myOid, OID playerOid) {
    	for (int item : itemsHeld) {
            giveItemToPlayer(playerOid, item);
    	}
    	if (itemLimit != 0) {
    		numItems--;
    		if (numItems < 1) {
    			//this.obj.despawn();
    			//Engine.getExecutor().schedule(this, respawnTime, TimeUnit.MILLISECONDS);
    			// Send a message to be caught by the spawn generator
    			PropertyMessage propMsg = new PropertyMessage(this.obj.getOid());
    	        propMsg.setProperty("objectEmpty", true);
    	        Engine.getAgent().sendBroadcast(propMsg);
    			TargetedPropertyMessage propMsg2 = new TargetedPropertyMessage(playerOid, myOid);
    	        propMsg2.setProperty(AgisStates.ItemAvailable.toString(), false);
    	        Engine.getAgent().sendBroadcast(propMsg2);
    		}
    	}
    }

    public void run() {
    	//this.obj.spawn();
    	numItems = itemLimit;
    }
     
    /* Gives the item to the player */
    protected void giveItemToPlayer(OID playerOid, int item) {
        lock.lock();
        try {
        	Template overrideTemplate = new Template();
            overrideTemplate.put(Namespace.OBJECT_MANAGER,
                    ObjectManagerClient.TEMPL_PERSISTENT, true);
            OID itemOid = ObjectManagerClient.generateObject(item, ObjectManagerPlugin.ITEM_TEMPLATE, overrideTemplate);
            // add to inventory
            OID bagOid = playerOid;
            if (Log.loggingDebug)
                Log.debug("processReqConcludedMsg: createitem: oid=" + itemOid + ", bagOid=" + bagOid + ", adding to inventory");
            boolean rv = InventoryClient.addItem(bagOid, playerOid, bagOid, itemOid);
            if (Log.loggingDebug)
                Log.debug("processReqConcludedMsg: createitem: oid=" + itemOid + ", added, rv=" + rv);
            //AgisItem itemObj = (AgisItem)EntityManager.getEntityByNamespace(playerOid, Namespace.AGISITEM);
            atavism.agis.plugins.ChatClient.sendObjChatMsg(playerOid, 2, "You have received something... ");
        }
        finally {
            lock.unlock();
        }
    }

    private void processReqOpenMobMsg(AgisInventoryClient.RequestOpenMobMessage reqMsg) {
    	OID myOid = getObjectStub().getOid();
    	OID playerOid = reqMsg.getPlayerOid();
    	if (singleItemPickup) {
    		giveItemsToPlayer(myOid, playerOid);
    	} else {
    		AgisInventoryClient.getLootList(playerOid, myOid);
    	}
        //InventoryClient.lootAll(playerOid, myOid);
        /*PropertyMessage propMsg = new PropertyMessage(this.obj.getOid());
        propMsg.setProperty("objectEmpty", true);
        Engine.getAgent().sendBroadcast(propMsg);*/
    }
    
    public void setItemsHeld(ArrayList<Integer> items) {
    	itemsHeld = items;
    }
    public List<Integer> getItemsHeld() {
    	return itemsHeld;
    }
    
    public void setItemLimit(int itemLimit) {
    	this.itemLimit = itemLimit;
    }
    public int getItemLimit() {
    	return itemLimit;
    }
    
    public void setNumItems(int numItems) {
    	this.numItems = numItems;
    }
    public int getNumItems() {
    	return numItems;
    }
    
    public void setRespawnTime(int time) {
    	this.respawnTime = time;
    }
    public int getRespawnTime() {
    	return respawnTime;
    }
    
    public void setSingleItemPickup(boolean singleItemPickup) {
    	this.singleItemPickup = singleItemPickup;
    }
    public boolean getSingleItemPickup() {
    	return singleItemPickup;
    }
    
    List<Integer> itemsHeld = new ArrayList<Integer>();
    int itemLimit = 0;
    int numItems = 0;
    boolean singleItemPickup = false;
    int respawnTime = 300000;
    static final Logger log = new Logger("ChestBehavior");
    private static final long serialVersionUID = 1L;
}
