package atavism.agis.behaviors;


import atavism.msgsys.*;
import atavism.server.objects.*;
import atavism.server.engine.*;
import atavism.server.util.*;
import atavism.server.messages.*;
import atavism.agis.plugins.*;
import atavism.agis.objects.*;

public class NonCombatPetBehavior extends Behavior {
    public NonCombatPetBehavior() {
    			 Log.debug("NonCombatBehavior:() start");
    		      }

    public NonCombatPetBehavior(SpawnData data) {
    	super(data);
    	 Log.debug("NonCombatBehavior: start");
    	 String value = (String)data.getProperty("combat.movementSpeed");
    	if (value != null) {
    		setMovementSpeed(Integer.valueOf(value));
    	}
    }

    public void initialize() {
        Log.debug("NonCombatBehavior.initialize: start "+obj.getOid());
        SubscriptionManager.get().subscribe(this, obj.getOid(), CombatClient.MSG_TYPE_DAMAGE, PropertyMessage.MSG_TYPE_PROPERTY,
              CombatClient.MSG_TYPE_COMBAT_LOGOUT, AgisMobClient.MSG_TYPE_PET_COMMAND_UPDATE);

        // Another filter to pick up when a players faction rep changes
        SubscriptionManager.get().subscribe(this, ownerOid, CombatClient.MSG_TYPE_FACTION_UPDATE);
        Log.debug("NonCombatBehavior.initialize: end");
    }

    public void activate() {
	    activated = true;
	    Log.debug("NonCombatBehavior.activate: adding reaction radius "+obj.getOid());
	    Engine.getAgent().sendBroadcast(new BaseBehavior.FollowCommandMessage(obj, new EntityHandle(ownerOid), speed, hitBoxRange));
    }
    
    public void deactivate() {
	    activated = false;
    }

    public void handleMessage(Message msg, int flags) {
	    lock.lock();
	    try {
	    if (activated == false)
		    return;
	    if (msg instanceof PropertyMessage) {
		    PropertyMessage propMsg = (PropertyMessage) msg;
		    Boolean dead = (Boolean)propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
		    if (dead != null && dead) {
                if (Log.loggingDebug)
                    Log.debug("NonCombatBehavior.onMessage: obj=" + obj + " got death=" + propMsg.getSubject());
		        if (propMsg.getSubject() == obj.getOid()) {
			        Log.debug("NonCombatBehavior.onMessage: mob died, deactivating all behaviors");
			        for(Behavior behav : obj.getBehaviors()) {
			            behav.deactivate();
			            obj.removeBehavior(behav);
			        }
		        }
		    }
	    }else if (msg.getMsgType() == AgisMobClient.MSG_TYPE_PET_COMMAND_UPDATE) {
			AgisMobClient.petCommandUpdateMessage pcuMsg = (AgisMobClient.petCommandUpdateMessage) msg;
			int commandVal = pcuMsg.getCommand();
			Log.debug("CombatPetBehavior.onMessage: " + obj.getOid() + " owner:" + ownerOid + " got petCommandUpdateMessage commandVal:" + commandVal);
			if (commandVal == -2) {
				Engine.getAgent().sendBroadcast(new BaseBehavior.FollowCommandMessage(obj, new EntityHandle(ownerOid), speed, hitBoxRange));
				//currentCommand = -2;
			}
			
			
	    }
	    //return true;
	    } finally {
	        lock.unlock();
	    }
    }
    
    public void run() {
    	if (activated == false) {
    	    return;
    	}
    }
    
    public void setMovementSpeed(float speed) { this.speed = speed; }
    public float getMovementSpeed() { return speed; }
    protected float speed = 12f;
    
    public void setHitBoxRange(float radius) { hitBoxRange = radius; }
    public float getHitBoxRange() { return hitBoxRange; }
    float hitBoxRange = 5;
    
    public void setOwnerOid(OID ownerOid) { this.ownerOid = ownerOid; }
    public OID getOwnerOid() { return ownerOid; }

    OID ownerOid = null;
    protected boolean activated = false;
    private static final long serialVersionUID = 1L;
}
