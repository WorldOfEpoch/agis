package atavism.agis.objects;

import atavism.server.messages.LogoutMessage;
import atavism.server.messages.PropertyMessage;
import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.math.Point;
import atavism.agis.behaviors.BaseBehavior;
import atavism.agis.behaviors.NonCombatPetBehavior;
import atavism.agis.plugins.*;
import atavism.msgsys.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class NonCombatPet extends Pet implements Serializable, MessageDispatch
{
    public NonCombatPet() {
    	super();
    }

    public NonCombatPet(int mobTemplateID, OID mobObj, boolean isSpawned,Long duration, OID ownerOid) {
    	super("NonCombatPet"+ownerOid);
    	this.mobTemplateID = mobTemplateID;
    //	this.mobObj = mobObj;
    	this.isSpawned = isSpawned;
    	this.ownerOid = ownerOid;
    	this.despawnTime = duration;
    	summonPet() ;
    }
    
public void summonPet() {
    // Do we need to make sure the template has been registered?
    Log.debug("PET: summon pet hit");
    if (isSpawned == true) {
        Log.debug("PET: pet is already spawned");
        boolean wasSpawned = despawnPet();
    	}

    // Get the template for the pet
    Template tmpl = ObjectManagerClient.getTemplate(mobTemplateID, ObjectManagerPlugin.MOB_TEMPLATE);
    String meshName = "";
    LinkedList<String> displays = (LinkedList) tmpl.get(WorldManagerClient.NAMESPACE, "displays");
    if (displays != null && displays.size() > 0) {
        meshName = displays.get(0);
        Log.debug("MOB: got display: " + meshName);
    }
    DisplayContext dc = new DisplayContext(meshName, true);
    dc.addSubmesh(new DisplayContext.Submesh("", ""));
    tmpl.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
    tmpl.put(WorldManagerClient.NAMESPACE, "model", meshName);
    ObjectManagerClient.registerTemplate(tmpl);

    // Create MobFactory
    MobFactory mobFactory = new MobFactory(mobTemplateID);

    // Add BaseBehavior using a supplier
    mobFactory.addBehavSupplier((obj, spawnData) -> new BaseBehavior());

    // Add NonCombatPetBehavior using a supplier
    mobFactory.addBehavSupplier((obj, spawnData) -> {
        NonCombatPetBehavior ncpBehav = new NonCombatPetBehavior();
        try {
            float movement_speed = (Integer) EnginePlugin.getObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED);
			AgisStatDef asd = CombatPlugin.lookupStatDef(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED);
			if(asd.getPrecision()>1)
				movement_speed = movement_speed/asd.getPrecision();
            ncpBehav.setMovementSpeed((movement_speed * 1.2f));
        } catch (Exception e) {
            log.error("SummonPet: owner has no movement speed property");
        }
        ncpBehav.setOwnerOid(ownerOid);
        return ncpBehav;
    });

    // Prepare spawn data
    BasicWorldNode bwNode = WorldManagerClient.getWorldNode(ownerOid);
    SpawnData spawnData = new SpawnData();
    spawnData.setTemplateID(mobTemplateID);
    spawnData.setSpawnRadius(1);

    log.debug("NonCombatPet summonPet: spawnData:" + spawnData);

    // Spawn the pet
    ObjectStub obj = mobFactory.makeObject(spawnData, bwNode.getInstanceOid(), bwNode.getLoc());
    obj.spawn();
    InterpolatedWorldNode iwNode = obj.getWorldNode();

    Log.debug("PET: pet " + mobName + " spawned at: " + iwNode.getLoc() + " in instance: " + iwNode.getInstanceOid());
    Log.debug("PET: owner is at: " + bwNode.getLoc() + " in instance: " + bwNode.getInstanceOid());

    // Set up the pet display
    String gender = "Male"; // You can modify this logic as needed
    isSpawned = true;
    mobObj = obj.getOid();
    Log.debug("NonCombatPet summonPet: Pet OID " + mobObj);
    AgisMobPlugin.setDisplay(mobObj, gender);

    // Activate the pet
    boolean activated = activate();

    // Set owner properties
    EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "nonCombatPet", this.getOid());
    EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "aP", mobObj);

    // Copy the owner's factions to the pet
    Integer faction = (Integer) EnginePlugin.getObjectProperty(ownerOid, Namespace.FACTION, FactionStateInfo.FACTION_PROP);
    String tempFaction = (String) EnginePlugin.getObjectProperty(ownerOid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP);
    EnginePlugin.setObjectProperty(mobObj, Namespace.FACTION, FactionStateInfo.FACTION_PROP, faction);
    EnginePlugin.setObjectProperty(mobObj, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP, tempFaction);
    EnginePlugin.setObjectProperty(mobObj, WorldManagerClient.NAMESPACE, "pet", true);

    // Send pet command update
    AgisMobClient.petCommandUpdate(mobObj, -2, null);

    // Update pet stats
    updatePetStats();

    // Handle despawn timing
    log.debug("NonCombatPet summonPet Done " + this.despawnTime);
    if (this.despawnTime > 0) {
        log.debug("NonCombatPet summonPet scheduling despawn in " + this.despawnTime + " milliseconds");
        DespawnPet despawnPet = new DespawnPet();
        scheduledExecutioner = Engine.getExecutor().schedule(despawnPet, this.despawnTime, TimeUnit.MILLISECONDS);
    }

    // Schedule distance checking
    DistancePet distancePet = new DistancePet();
    scheduledDistanceExecutioner = Engine.getExecutor().scheduleAtFixedRate(distancePet, 10, 10, TimeUnit.SECONDS);
}
 
    /**
     * Sends out a message to update the combat stats of the spawned pet mob.
     */
    private void updatePetCombatStats() {
    	AgisMobClient.updatePetStats(ownerOid, mobObj, currentLevel, 20);
    }
    
    
    public boolean despawnPet() {
    	boolean wasSpawned = false;
    	
    	//mobObj.despawn();
    	Log.debug("PET: despawn hit with isSpawned: " + isSpawned);
    	if (isSpawned) {
    		if (scheduledExecutioner != null) {
    			scheduledExecutioner.cancel(true);
    			scheduledExecutioner =null;
    		}
    		if (scheduledDistanceExecutioner != null) {
				scheduledDistanceExecutioner.cancel(true);
				scheduledDistanceExecutioner = null;
			}
    		try {
    			Log.debug("PET: despawning pet: " + mobObj);
    	        WorldManagerClient.despawn(mobObj);
    	        Log.debug("PET: despawned pet: " + mobObj);
    		} catch (NoRecipientsException e) {
    			Log.debug("PET: no recipients found for despawn pet.");
    		}
    		isSpawned = false;
    		boolean deactivated = deactivate();
    		  EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "nonCombatPet", null);
    		//  EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "pet", false);
        	    wasSpawned = true;
    	}
    	return wasSpawned;
    }
    
    /**
     * Activates the message subscriptions so this pet object can pick up relevant messages.
     * @return
     */
    public boolean activate() {
        log.debug("PET: in activate: this " + this);
        // Clear the old subscribers
        sub = null;
        sub2 = null;
        // subscribe for some messages
        SubjectFilter filter = new SubjectFilter(mobObj);
        //filter.addType(InventoryClient.MSG_TYPE_INV_UPDATE);
        filter.addType(PropertyMessage.MSG_TYPE_PROPERTY);
     //   filter.addType(AgisMobClient.MSG_TYPE_PET_TARGET_LOST);
        sub = Engine.getAgent().createSubscription(filter, this);
        if (sub == null)
        	log.debug("PET: sub is null");
        
        SubjectFilter filter2 = new SubjectFilter(ownerOid);
        filter2.addType(PropertyMessage.MSG_TYPE_PROPERTY);
        filter2.addType(AgisMobClient.MSG_TYPE_SEND_PET_COMMAND);
        filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
        filter2.addType(WorldManagerClient.MSG_TYPE_DESPAWNED);
             //sub2 = Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);
        sub2 = Engine.getAgent().createSubscription(filter2, this);
        log.debug("PET: set up subscription for pet owner: " + ownerOid);
        return true;
    }
    /**
     * Deactivates the message subscriptions so this pet object will no longer pick up any messages.
     * @return
     */
    public boolean deactivate() {
	   Log.debug("PET: deactivating");
        if (sub != null) {
            Engine.getAgent().removeSubscription(sub);
            sub = null;
            Log.debug("PET: removing sub");
	    }
        if (sub2 != null) {
            Engine.getAgent().removeSubscription(sub2);
            sub2 = null;
            Log.debug("PET: removing sub 2");
	    }
        return true;
    }
   
    /**
     * Sends out a message to update the combat stats of the spawned pet mob.
     */
    private void updatePetStats() {
    	AgisMobClient.updatePetStats(ownerOid, mobObj, currentLevel, 20);
    }
    
    /**
     * process network messages
     */
    public void handleMessage(Message msg, int flags) {
    	//Log.debug("PET: got message with type: " + msg.getMsgType());
    	if (msg instanceof PropertyMessage) {
    		PropertyMessage propMsg = (PropertyMessage) msg;
    		OID subject = propMsg.getSubject();
    		if (subject.equals(mobObj)) {
    			handlePetPropertyUpdate(propMsg);
    		} else if (subject.equals(ownerOid)){
    			handleOwnerPropertyUpdate(propMsg);
    		}
    	}else if(msg.getMsgType() == LogoutMessage.MSG_TYPE_LOGOUT ) { 
    		LogoutMessage logoutMsg = (LogoutMessage) msg;
            OID playerOid = logoutMsg.getSubject();
            if (playerOid.equals(ownerOid)) {
            	log.debug("CombatPet owner:"+ownerOid+" is logout");
            	DespawnPet despawnPet = new DespawnPet();
    	    	Engine.getExecutor().schedule(despawnPet, 500, TimeUnit.MILLISECONDS);
    	    	}
    	}else if(msg.getMsgType() == WorldManagerClient.MSG_TYPE_DESPAWNED ) { 
    		WorldManagerClient.DespawnedMessage logoutMsg = (WorldManagerClient.DespawnedMessage) msg;
            OID playerOid = logoutMsg.getSubject();
            if (playerOid.equals(ownerOid)) {
            	log.debug("CombatPet owner:"+ownerOid+" left instance despawn pet");
            	DespawnPet despawnPet = new DespawnPet();
    	    	Engine.getExecutor().schedule(despawnPet, 500, TimeUnit.MILLISECONDS);
    	    	}
    	}else if (msg.getMsgType() == AgisMobClient.MSG_TYPE_SEND_PET_COMMAND) {
    		Log.debug("PET: got send pet command message");
    		AgisMobClient.sendPetCommandMessage spcMsg = (AgisMobClient.sendPetCommandMessage) msg;
    		String command = spcMsg.getCommand();
    		OID targetOid = spcMsg.getTargetOid();
    		handleCommand(command, targetOid);
    	} /*else if (msg.getMsgType() == AgisMobClient.MSG_TYPE_PET_TARGET_LOST) {
    		Log.debug("PET: got send pet command message");
    		AgisMobClient.petTargetLostMessage spcMsg = (AgisMobClient.petTargetLostMessage) msg;
    		handleTargetLost();
    	} */else {
            log.error("PET: unknown msg: " + msg);
        }
        //return true;
    }
    /**
     * Processes commands, which will either update the pets attitude, the current command, or activate
     * an ability that the pet has.
     * @param command
     */
    public void handleCommand(String command, OID targetOid) {
    	if (!isSpawned) {
    		Log.debug("PET: command issued to pet that is not spawned");
    		return;
    	}
    	Log.debug("PET: issuing pet command: " + command);
    	/*if (command.equals("passive")) {
    		updateAttitude(1);
    	} else if (command.equals("defensive")) {
    		updateAttitude(2);
    	} else if (command.equals("aggressive")) {
    		updateAttitude(3);
    	} else if (command.equals("stay")) {
    		updateCommand(-1, targetOid);
    	} else if (command.equals("follow")) {
    		updateCommand(-2, targetOid);
    	} else if (command.equals("attack")) {
    		if (targetOid.equals(ownerOid) || targetOid.equals(mobObj)) {
    			ExtendedCombatMessages.sendErrorMessage(ownerOid, "Your pet cannot attack that target");
    		} else {
    			updateCommand(-3, targetOid);
    		}
    	}else*/ 
    	if (command.equals("despawn")) {
    		//if ( targetOid.equals(mobObj)) {
    			DespawnPet despawnPet = new DespawnPet();
    			Engine.getExecutor().schedule(despawnPet, 200, TimeUnit.MILLISECONDS);
	    	//	}
	    	}
    }
    
    /**
     * Deals with the different property updates that have occurred for the pet.
     * @param propMsg
     */
    protected void handlePetPropertyUpdate(PropertyMessage propMsg) {
    	Boolean dead = (Boolean)propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
	    if (dead != null && dead) {
	    	Log.debug("PET: got pet death, despawning");
	    	DespawnPet despawnPet = new DespawnPet();
	    	Engine.getExecutor().schedule(despawnPet, 2000, TimeUnit.MILLISECONDS);
	    }
    }
    
    /**
     * Deals with the different property updates that have occurred for the owner of the pet.
     * @param propMsg
     */
    protected void handleOwnerPropertyUpdate(PropertyMessage propMsg) {
    	Log.debug("");
    	Boolean dead = (Boolean)propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
	    if (dead != null && dead) {
	    	Log.debug("PET: got owner death, despawning");
	    	//despawnPet();
	    	DespawnPet despawnPet = new DespawnPet();
	    	Engine.getExecutor().schedule(despawnPet, 2000, TimeUnit.MILLISECONDS);
	    	return;
	    }
	    //TODO: if faction update, update the pets
    }
    
    /**
     * A runnable class that will despawn the spawned pet mob when run.
     * @author Andrew
     *
     */
    class DespawnPet implements Runnable, Serializable {
    	public void run() {
    		Log.debug("PET: despawning pet: " + mobObj);
  	         if (isSpawned) {
        		try {
        			Log.debug("PET: despawning pet: " + mobObj);
        	        WorldManagerClient.despawn(mobObj);
        	        Log.debug("PET: despawned pet: " + mobObj);
        		} catch (NoRecipientsException e) {
        			Log.debug("PET: no recipients found for despawn pet.");
        		}
        		isSpawned = false;
        		boolean deactivated = deactivate();
      		  EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "nonCombatPet", null);
    		//  EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "pet", false);
    }
    	}
    	private static final long serialVersionUID = 1L;
    }

    class DistancePet implements Runnable, Serializable {
		public void run() {
			Log.debug("PET: Timer check pet:" + mobObj + " distance from owner:" + ownerOid);
			BasicWorldNode bwNodeOwner = WorldManagerClient.getWorldNode(ownerOid);
			BasicWorldNode bwNodePet = WorldManagerClient.getWorldNode(mobObj);
			Point owPoint = bwNodeOwner.getLoc();
			Point petPoint = bwNodePet.getLoc();
			float dist = Point.distanceTo(owPoint, petPoint);
			Log.debug("PET: Timer check pet:" + mobObj + " distance from owner:" + ownerOid+" distance:"+dist);
			if (dist > CombatPlugin.PET_DISTANCE_DESPAWN) {
				try {
					Log.debug("PET: Timer distance try despawning pet: " + mobObj);
					WorldManagerClient.despawn(mobObj);
					Log.debug("PET: Timer distance despawned pet: " + mobObj);
				} catch (NoRecipientsException e) {
					Log.debug("PET: no recipients found for distance despawn pet.");
				}
				isSpawned = false;
				boolean deactivated = deactivate();
				EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "activePet", null);
				EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "aP", null);
					EnginePlugin.setObjectProperty(ownerOid, WorldManagerClient.NAMESPACE, "hasPet", false);
				log.debug("Distance Noncombat Set hasPet false ownerOid="+ownerOid+" mobObj="+mobObj);
				}
		}

		private static final long serialVersionUID = 1L;
	}
    
    /*protected boolean processInvUpdate(AgisInventoryClient.QuestItemsListMessage msg) {
        //if (Log.loggingDebug)
        //    log.debug("processInvUpdate: player=" + getPlayerOid() + ", itemList=" + msg);
        HashMap<String, Integer> itemList = msg.getItemList();
        //checkInventory(false, itemList);
        return true;
    }*/
    public Long getDespawnTime() {
    	return despawnTime;
    }
    public void setDespawnTime(Long despawnTime) {
    	this.despawnTime = despawnTime;
    }
    
    static final Logger log = new Logger("NonCombatPet");
    
    private int currentLevel = 1;
    private Long despawnTime;
    private Long sub;
    private Long sub2;
    protected ScheduledFuture<?> scheduledExecutioner;
    protected ScheduledFuture<?> scheduledDistanceExecutioner;
       private static final long serialVersionUID = 1L;
}
