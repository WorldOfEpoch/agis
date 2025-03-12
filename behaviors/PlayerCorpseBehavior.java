package atavism.agis.behaviors;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import atavism.agis.objects.SpawnGenerator;
import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.messages.*;
import atavism.server.objects.ObjectTracker;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.MobManagerPlugin;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;
import atavism.server.util.Log;

/**
 * Gives players that come within the reaction radius the item listed.
 * @author Andrew
 *
 */
public class PlayerCorpseBehavior extends Behavior {
    public PlayerCorpseBehavior() {
    }

    public void initialize() {
        SubscriptionManager.get().subscribe(this, obj.getOid(), ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS,
                PropertyMessage.MSG_TYPE_PROPERTY);
    }

    public void activate() {
    	Log.error("PlayerCorpseBehavior activate ");
    	activated = true;
    	Log.debug("TEMP: PlayerCorpseBehavior activating loot behav with targets:" + acceptableTargets);
    	OpenAccess openAccessTimer = new OpenAccess();
    	Log.error("PlayerCorpseBehavior safeDuration="+safeDuration+" corpseDuration="+corpseDuration);
    	Engine.getExecutor().schedule(openAccessTimer, safeDuration, TimeUnit.SECONDS);
    	Despawn despawnTimer = new Despawn();
		Engine.getExecutor().schedule(despawnTimer, corpseDuration, TimeUnit.SECONDS);
    	
    	
    	MobManagerPlugin.getTracker(obj.getInstanceOid()).addReactionRadius(obj.getOid(), radius);
    	if (loot != null) {
    		for(OID itemOid : loot) {
    			InventoryClient.addItem(obj.getOid(), obj.getOid(), obj.getOid(), itemOid);
    		}
    		EnginePlugin.setObjectProperty(obj.getOid(), InventoryClient.NAMESPACE, "loot", loot);
    	}
    	if(loot_curr!=null) {
    	    EnginePlugin.setObjectProperty(obj.getOid(), InventoryClient.NAMESPACE, "loot_curr", loot_curr);
    	}
    	if (acceptableTargets != null) {
    		for(OID target : acceptableTargets) {
    			TargetedPropertyMessage propMsg = new TargetedPropertyMessage(target, obj.getOid());
    			propMsg.setProperty("lootable", true);
    			Engine.getAgent().sendBroadcast(propMsg);
    			Log.debug("LOOT: set lootable to player: " + target + " for obj: " + obj.getOid());
    		}
    	}
    	Log.error("PlayerCorpseBehavior activate end");
        
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
	    		addPlayerInRadius(nMsg.getSubject());
	    	} else if (nMsg.getWasInRadius()) {
	    		removePlayerInRadius(nMsg.getSubject());
	    	}
    	}
    	 if (msg instanceof PropertyMessage) {
 	    	PropertyMessage propMsg = (PropertyMessage) msg;
 		    OID subject = propMsg.getSubject();
 		    LinkedList<OID> loot = (LinkedList<OID>)propMsg.getProperty("loot");
 		    if (loot != null && loot.isEmpty()) {
 		    	if (activated) {
 		    		Log.debug("LOOT: despawning loot object as it is now empty");
 					// Send a message to be caught by the spawn generator
 					activated = false;
 					SpawnGenerator.removeSpawnGenerator(obj.getInstanceOid(), spawnerKey);
 				}
 		    }
    	}
    }
    
    void addPlayerInRadius(OID playerOid) {
    	acceptableTargets.add(playerOid);
    	if (openAccess || playerOid.equals(corpseOwner)) {
    		TargetedPropertyMessage propMsg = new TargetedPropertyMessage(playerOid, obj.getOid());
			propMsg.setProperty("lootable", true);
			Engine.getAgent().sendBroadcast(propMsg);
    	}
    }
    
    void removePlayerInRadius(OID playerOid) {
    	acceptableTargets.remove(playerOid);
    	TargetedPropertyMessage propMsg = new TargetedPropertyMessage(playerOid, obj.getOid());
		propMsg.setProperty("lootable", false);
		Engine.getAgent().sendBroadcast(propMsg);
    }
    
    public void setCorpseOwner(OID owner) {
    	corpseOwner = owner;
    }
    
    public void setCorpseDuration(int corpseDuration) {
    	this.corpseDuration = corpseDuration;
    }
    public int getCorpseDuration() {
    	return corpseDuration;
    }
    
    public void setSafeDuration(int safeDuration) {
    	this.safeDuration = safeDuration;
    }
    public int getSafeDuration() {
    	return safeDuration;
    }

    public void setRadius(int radius) {
    	this.radius = radius;
    }
    public int getRadius() {
    	return radius;
    }
    
    public void setLoot(LinkedList<OID> loot) {
    	this.loot = loot;
    }
    public LinkedList<OID> getLoot() {
    	return loot;
    }
    public void setLootCurrency(LinkedList<String> loot) {
    	this.loot_curr = loot;
    }
    public LinkedList<String> getLootCurrency() {
    	return loot_curr;
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
    
    public void setSpawnerKey(String key) {
    	Log.debug("CORPSE: set spawner key: " + key);
    	spawnerKey = key;
    }
    
    public class OpenAccess implements Runnable {
		public void run() {
			if (activated) {
				// Set lootable for all players
				openAccess = true;
				for (OID target : acceptableTargets) {
					TargetedPropertyMessage propMsg = new TargetedPropertyMessage(target, obj.getOid());
	    			propMsg.setProperty("lootable", true);
	    			Engine.getAgent().sendBroadcast(propMsg);
				}
			}
		}
	}
    
    public class Despawn implements Runnable {
		public void run() {
			Log.debug("CORPSE: running despawn with key: " + spawnerKey);
			if (activated) {
				activated = false;
 				SpawnGenerator.removeSpawnGenerator(obj.getInstanceOid(), spawnerKey);
			}
		}
	}

    protected OID corpseOwner = null;
    protected ArrayList<OID> acceptableTargets = new ArrayList<OID>();
    protected int corpseDuration = 300; // Seconds
    protected int safeDuration = 180; // Seconds - how long only the player themselves can loot the corpse
    protected int radius = 75;
    protected boolean openAccess = false;
    protected LinkedList<OID> loot = null;
    protected LinkedList<String> loot_curr = null;
    protected String spawnerKey = "";
    protected boolean activated = false;
    private static final long serialVersionUID = 1L;
}
