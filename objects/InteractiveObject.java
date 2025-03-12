package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import atavism.agis.plugins.*;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.msgsys.Message;
import atavism.msgsys.MessageDispatch;
import atavism.msgsys.SubjectFilter;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.InterpolatedWorldNode;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.objects.DisplayContext;
import atavism.server.objects.Entity;
import atavism.server.objects.EntityManager;
import atavism.server.objects.EntityWithWorldNode;
import atavism.server.objects.ObjectStub;
import atavism.server.objects.ObjectTracker;
import atavism.server.objects.ObjectTypes;
import atavism.server.objects.Template;
import atavism.server.plugins.MobManagerClient;
import atavism.server.plugins.MobManagerPlugin;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

/**
 * An Interactive Object is an object players can interact with. The Object can have different interaction types, along with
 * requirements to be able to interact with it.
 * @author Andrew Harrison
 *
 */
public class InteractiveObject implements Serializable, MessageDispatch, Runnable {
	private ScheduledFuture<?> despawnTimer;

	public InteractiveObject() {
    }
    
    public InteractiveObject(int id, AOVector loc,  OID instanceOID) {
    	this.id = id;
    	this.loc = loc;
    	this.instanceOID = instanceOID;
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
        MobManagerPlugin.getTracker(instanceOID).addReactionRadius(objectOID, 100);
        active = true;
        Log.debug("INTERACTIVE: node with oid: " + objectOID + " id:"+id+" instanceOID: "+instanceOID+" activated ");
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
     	    Log.debug("INTERACTIVE: myOid=" + objectOID + " objOid=" + nMsg.getSubject()
     		      + " inRadius=" + nMsg.getInRadius() + " wasInRadius=" + nMsg.getWasInRadius());
    	    if (nMsg.getInRadius()) {
    	    	addPlayer(nMsg.getSubject());
    	    } else {
    	    	// Remove subject from targets in range
    	    	removePlayer(nMsg.getSubject());
    	    }
    	} else if (msg instanceof CombatClient.interruptAbilityMessage) {
			CombatClient.interruptAbilityMessage nMsg = (CombatClient.interruptAbilityMessage)msg;
            interruptInteractTask(nMsg.getSubject());
        }
    }
    
    @Override
	public void run() {

    	active = true;
//		used.set(0);

    	if (interactionType.equals("Chest")) {
    		
    	}

		// Loop through players in range and send them the update
		for (OID playerOid : playersInRange) {
			sendState(playerOid);
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
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_NAME, (dynamicObject?"":"_ign_")+"interactive_" + id);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_OBJECT_TYPE, ObjectTypes.mob);
    	markerTemplate.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_PERCEPTION_RADIUS, AgisMobPlugin.INTERACTIVE_OBJECT_PERCEPTION_RADIUS);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
    	markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_LOC, new Point(loc));
    	//markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_ORIENT, orientation);
    	DisplayContext dc = new DisplayContext(gameObject, true);
		dc.addSubmesh(new DisplayContext.Submesh("", ""));
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
		markerTemplate.put(Namespace.WORLD_MANAGER, "model", gameObject);
		markerTemplate.put(Namespace.WORLD_MANAGER, "ioid", id);
		markerTemplate.put(Namespace.WORLD_MANAGER, "pid", profileId);


		// Put in any additional props
    	if (props != null) {
    		for (String propName : props.keySet()) {
    			markerTemplate.put(Namespace.WORLD_MANAGER, propName, props.get(propName));
    		}
    	}
    	// Create the object
    	objectOID = ObjectManagerClient.generateObject(ObjectManagerClient.BASE_TEMPLATE_ID,
                ObjectManagerClient.BASE_TEMPLATE, markerTemplate);
    	
    	if (objectOID != null) {
    		// Need to create an interpolated world node to add a tracker/reaction radius to the claim world object
    		BasicWorldNode bwNode = WorldManagerClient.getWorldNode(objectOID);
    		InterpolatedWorldNode iwNode = new InterpolatedWorldNode(bwNode);
    		resourceNodeEntity = new InteractiveObjectEntity(objectOID, iwNode);
    		EntityManager.registerEntityByNamespace(resourceNodeEntity, Namespace.MOB);
    		MobManagerPlugin.getTracker(instanceOID).addLocalObject(objectOID, AgisMobPlugin.INTERACTIVE_OBJECT_PERCEPTION_RADIUS);
    		
            WorldManagerClient.spawn(objectOID);
            Log.debug("INTERACTIVE: spawned resource at : " + loc);
            activate();
        }

		if(despawnTime > 0){
			Log.debug("Interactive "+id+" make despawn in s "+despawnTime);
			despawnTimer = Engine.getExecutor().schedule(new DespawnInteractiveObject(this), (long) despawnTime * 1000, TimeUnit.MILLISECONDS);
		}
    }
    
    /**
     * Add a player to the update list for this ResourceNode. The player will receive data about the node and any updates
     * that occur.
     * @param playerOid
     */
    public void addPlayer(OID playerOid) {
    	Log.debug("INTERACTIVE: added player: " + playerOid);
    	// Send down the state to the player
    	
			
		if (!playersInRange.contains(playerOid)) {
	    	playersInRange.add(playerOid);
			sendSpawn(playerOid);
	    }
    }
    
    /**
     * Removes a player from the ResourceNode. They will no longer receive updates.
     * @param playerOid
     */
    public void removePlayer(OID playerOid) {
    	if (playersInRange.contains(playerOid))
    		playersInRange.remove(playerOid);
    }
    
    /**
     * Checks whether the player can gather items from this resource. Checks their skill level
     * and weapon.
     * @param playerOid
	 * @param checkSkillAndWeapon
     * @return
     */
    boolean playerCanUse(OID playerOid, boolean checkSkillAndWeapon) {
    	// No one else is currently gathering are they?
   	 	if (makeBusy && task != null && !interactionType.equals("InstancePortal") && !interactionType.equals("LeaveInstance")) {
   	 		//FIXME Disable Check Enter or Exit Instance
   	 		Log.debug("INTERACTIVE: task="+task+" interactionType="+interactionType+" skip");
    		ExtendedCombatMessages.sendErrorMessage(playerOid, "The object is currently being used");
    		return false;
    	}
    	// location check
    	Point p = WorldManagerClient.getObjectInfo(playerOid).loc;
    	// Player must be within 4 meters of the node (16 for squared)
    	if(Log.loggingDebug)
    		Log.debug("INTERACTIVE: Ply Loc="+p+" Obj Loc="+loc+" distanceToSquared="+Point.distanceToSquared(p, new Point(loc))+" limit="+Math.pow(getInteractionDistance(),2)+" radius="+getInteractionDistance());
    	if (Point.distanceToSquared(p, new Point(loc)) > Math.pow(getInteractionDistance(),2) ) {
			Log.debug("INTERACTIVE: to far away skip "+getID());
    		ExtendedCombatMessages.sendErrorMessage(playerOid, "You are too far away from the object to use it");
    		return false;
    	}
    	
    	if (questIDReq > 0) {
    		boolean onQuest = false;
    		HashMap<Integer, QuestState> activeQuests = QuestClient.getActiveQuests(playerOid);
    		for (int key : activeQuests.keySet()) {
        		if (key == questIDReq) {
        			onQuest = true;
        		}
    		}
    		
    		if (!onQuest) {
				Log.debug("INTERACTIVE: don have req quest skip "+getID());
    			return false;
    		}
    	}

		if(itemReq > 0 && itemCountReq > 0){
			HashMap<Integer, Integer> items = new HashMap<>();
			items.put(itemReq, itemCountReq);
			if(AgisInventoryClient.checkComponents(playerOid, items)){

			} else {
				Log.debug("INTERACTIVE: don have items skip "+getID());
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You dont have required items");
				return false;
			}
		}

		if(currencyReq>0 && currencyCountReq > 0){
			if(AgisInventoryClient.checkCurrency(playerOid, currencyReq, currencyCountReq)){

			} else {
				Log.debug("INTERACTIVE: don have currency skip "+getID());
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You dont have required items");
				return false;
			}
		}
		if(minLevel > -1 && maxLevel > -1){
			AgisStat lev = (AgisStat) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, "level");
			//Log.error("GUILD: ply level : "+lev.toString());
			int level = lev.getCurrentValue();
			if(Log.loggingDebug)Log.debug("INTERACTIVE: level="+level+" minLevel="+minLevel+" maxLevel="+maxLevel);
			if(level >= minLevel && level <= maxLevel){
			Log.debug("INTERACTIVE: ");
			} else{
				Log.debug("INTERACTIVE: don have req level skip "+getID());
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You dont have required level");
				return false;
			}
		}

		//Check Level


    	if (checkSkillAndWeapon) {
    		// skill check
    	}
		Log.debug("INTERACTIVE: Can use "+getID());
    	return true;
    }
    
    public void tryUseObject(OID playerOid, String state) {
    	Log.debug("INTERACTIVE: got player "+playerOid+" trying to interact with object with state: " + state);
    	if (!playerCanUse(playerOid, true)) {
    		return;
    	}

		if(useLimit>0) {
			if(useLimit <= used.get()){
				Log.debug("INTERACTIVE: "+id+":"+name+" reach use limit");
				return;
			}
		}
		used.incrementAndGet();

		task = new InteractTask();
	    task.StartInteractTask(loc, Quaternion.Identity, playerOid, this, state);
	    
	    if (harvestTimeReq > 0) {
    		Engine.getExecutor().schedule(task, (long) harvestTimeReq * 1000, TimeUnit.MILLISECONDS);
			tasks.put(playerOid, task);
    		task.sendStartInteractTask(harvestTimeReq);
    		// Register for player movement to interrupt the gathering
    		SubjectFilter filter = new SubjectFilter(playerOid);
	        filter.addType(CombatClient.MSG_TYPE_INTERRUPT_ABILITY);
	        sub = Engine.getAgent().createSubscription(filter, this);
			if(!makeBusy){
				subs.put(playerOid, sub);
			}
    	} else {
    		task.run();
    	}
    }
    
    void interruptInteractTask(OID oid) {
		if(!makeBusy) {
			InteractTask task = tasks.getOrDefault(oid,new InteractTask());
			task.interrupt();
			tasks.remove(oid);
			Long sub = subs.getOrDefault(oid,-1L);
			if(sub > -1L) {
				Engine.getAgent().removeSubscription(sub);
			}
			subs.remove(oid);
		} else {
			if (task != null) {
				task.interrupt();
				task = null;
				if (sub != null)
					Engine.getAgent().removeSubscription(sub);
			}
		}
    }
    
	void interactComplete(InteractTask task) {

		if(itemReq > 0 && itemCountReq > 0 && itemReqGet){
			HashMap<Integer, Integer> items = new HashMap<>();
			items.put(itemReq, itemCountReq);
			AgisInventoryClient.removeGenericItems(task.playerOid, items,false);
		}

		if(currencyReq>0 && currencyCountReq > 0 && currencyReqGet){
			AgisInventoryClient.alterCurrency(task.playerOid, currencyReq, -currencyCountReq);
		}

		Log.debug("INTERACTIVE: interaction complete interactionType="+interactionType);
		if (interactionType.equals("ApplyEffect")) {
			CombatClient.applyEffect(task.playerOid, interactionID);
		} else if (interactionType.equals("Ability")) {
			CombatClient.startAbility(interactionID, task.playerOid, task.playerOid, null, new Point(loc));
		} else if (interactionType.equals("CompleteTask")) {
			QuestClient.TaskUpdateMessage msg = new QuestClient.TaskUpdateMessage(task.playerOid, interactionID, 1);
			Engine.getAgent().sendBroadcast(msg);
		} else if (interactionType.equals("InstancePortal")) {
			AgisWorldManagerClient.sendChangeInstance(task.playerOid, interactionID, new Point(Float.parseFloat(interactionData1), Float.parseFloat(interactionData2), Float.parseFloat(interactionData3)));
		} else if (interactionType.equals("LeaveInstance")) {
			AgisWorldManagerClient.returnToLastInstance(task.playerOid);
		} else if (interactionType.equals("StartQuest")) {
			LinkedList<Integer> quests = new LinkedList<Integer>();
			quests.add(interactionID);
			QuestClient.offerQuestToPlayer(task.playerOid, objectOID, quests, false);
		} else if (interactionType.equals("Chest")) {

		} else //if (task.state != null && !task.state.equals(""))
		{
			Log.debug("INTERACTIVE: sending down state: " + state+" task.state="+task.state);
			// Send down state
			state = MoveToNextState();
			Log.debug("INTERACTIVE: sending down state: " + state+" task.state="+task.state);

			for (OID playerOid : playersInRange) {
				sendState(playerOid);
			}
		}
    	if(useLimit > 0  && used.get()>= useLimit) {
			if (despawnDelay > 0) {
				Engine.getExecutor().schedule(new DespawnInteractiveObject(this), (long) despawnDelay * 1000L, TimeUnit.MILLISECONDS);
			}else{
				despawn();
			}
		} else {
			if (despawnDelay > 0) {
				Engine.getExecutor().schedule(new DeactivateInteractiveObject(this), (long) despawnDelay * 1000L, TimeUnit.MILLISECONDS);
			} else {
				if (respawnTime > 0) {
					despawnResource();
				}
			}

		}
    }

	String MoveToNextState()
	{
		Log.debug("INTERACTIVE: MoveToNextState: " + state+" coordinatedEffectsList="+coordinatedEffectsList.size());

		int nextPos = 0;
		for (String ce : coordinatedEffectsList)
		{
			Log.debug("INTERACTIVE: MoveToNextState: " +ce+" "+nextPos);
			nextPos++;
			if (ce != null)
				if (ce == state || state == "" || state == null)
				{
					if (nextPos == coordinatedEffectsList.size())
					{
						nextPos = 0;
					}
					Log.debug("INTERACTIVE: MoveToNextState: " +ce+" "+nextPos+" -> "+coordinatedEffectsList.get(nextPos));
					return coordinatedEffectsList.get(nextPos);
				}
		}
		Log.debug("INTERACTIVE: MoveToNextState: return empty");
		return "";
	}

	public void despawn() {
		Log.debug("INTERACTIVE: despawning");
		active = false;
		if(despawnTimer!=null)
			despawnTimer.cancel(false);
		// Loop through players in range and send them the update
		for (OID playerOid : playersInRange) {
			sendState(playerOid);
		}

		if(Log.loggingDebug)
			Log.debug("INTERACTIVE: id=" + id + " despawn objectOID=" + objectOID);

		if (objectOID != null) {
			deactivate();
			MobManagerClient.setReactionRadius(instanceOID, objectOID, -1);
			MobManagerPlugin.getTracker(instanceOID).removeLocalObject(objectOID);
			WorldManagerClient.despawn(objectOID);
			EntityManager.removeEntityByNamespace(resourceNodeEntity, Namespace.MOB);
			ObjectManagerClient.deleteObject(objectOID);

		}
		if (dynamicObject){
			AgisMobPlugin.RemoveInteractiveObject(instanceOID, id);
		}
		if(Log.loggingDebug)
			Log.debug("INTERACTIVE:  id=" + id + " despawn END");
	}

	public boolean deactivate() {
		if(Log.loggingDebug)
			Log.debug("INTERACTIVE: id=" + id + " statrting deactivate " + name + " objectOID=" + objectOID);

		active = false;
		Log.debug("INTERACTIVE: deactivating");
		MobManagerPlugin.getTracker(instanceOID).removeReactionRadius(objectOID);
		if (sub != null) {
			Engine.getAgent().removeSubscription(sub);
			sub = null;
			Log.debug("INTERACTIVE: removing sub");
		}
		if (eventSub != null) {
			Engine.getAgent().removeSubscription(eventSub);
			eventSub = null;
			Log.debug("INTERACTIVE: removing sub 2");
		}
		if(Log.loggingDebug)
			Log.debug("INTERACTIVE: id=" + id + " END deactivate " + name + " objectOID=" + objectOID);

		return true;
	}


	public void despawnResource() {
    	Log.debug("INTERACTIVE: despawning resource");
    	active = false;

		// Loop through players in range and send them the update
		for (OID playerOid : playersInRange) {
			sendState(playerOid);
		}
		
		// Schedule the respawn
		Engine.getExecutor().schedule(this, respawnTime, TimeUnit.SECONDS);
    }
    
    void sendState(OID playerOid) {
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "interactive_object_state");
		props.put("nodeID", id);
		props.put("active", active);
		props.put("state", state);
		Log.debug("INTERACTIVE:send state "+props+" to "+playerOid);
		TargetedExtensionMessage msg = new TargetedExtensionMessage( WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
    }

	void sendSpawn(OID playerOid) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "interactive_object_spawn");
		props.put("nodeID", id);
		props.put("active", active);
		props.put("go", getGameObject());
		props.put("loc", getLoc());
		props.put("state", state);
		Log.debug("INTERACTIVE:send state "+props+" to "+playerOid);
		TargetedExtensionMessage msg = new TargetedExtensionMessage( WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

	public int getID() { return id; }
    public void setID(int id) {
    	this.id = id;
    }
    
    public String getName() { return name; }
    public void setName(String name) {
    	this.name = name;
    }
    
    public String getGameObject() { return gameObject; }
    public void setGameObject(String gameObject) {
    	this.gameObject = gameObject;
    }
    
    public String getCoordEffect() { return coordinatedEffect; }
    public void setCoordEffect(String coordinatedEffect) {
    	this.coordinatedEffect = coordinatedEffect;
    }
    
    public AOVector getLoc() { return loc; }
    public void setLoc(AOVector loc) {
    	this.loc = loc;
    }
    
    public HashMap<String, Serializable> getProps() { return props; }
    public void setProps(HashMap<String, Serializable> props) {
    	this.props = props;
    }
    
    public OID getInstanceOID() { return instanceOID; }
    public void setInstanceOID(OID instanceOID) {
    	this.instanceOID = instanceOID;
    }
    
    public OID getObjectOID() { return objectOID; }
    public void setObjectOID(OID objectOID) {
    	this.objectOID = objectOID;
    }
    
    public int getQuestIDReq() { return questIDReq; }
    public void setQuestIDReq(int questIDReq) {
    	this.questIDReq = questIDReq;
    }
    
    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) {
    	this.interactionType = interactionType;
    }
    
    public int getInteractionID() { return interactionID; }
    public void setInteractionID(int interactionID) {
    	this.interactionID = interactionID;
    }
    
    public String getInteractionData1() { return interactionData1; }
    public void setInteractionData1(String interactionData1) {
    	this.interactionData1 = interactionData1;
    }
    
    public String getInteractionData2() { return interactionData2; }
    public void setInteractionData2(String interactionData2) {
    	this.interactionData2 = interactionData2;
    }
    
    public String getInteractionData3() { return interactionData3; }
    public void setInteractionData3(String interactionData3) {
    	this.interactionData3 = interactionData3;
    }

    public boolean getActive() { return active; }
    public void setActive(boolean active) {
    	this.active = active;
    }
    
    public int getRespawnTime() { return respawnTime; }
    public void setRespawnTime(int respawnTime) {
    	this.respawnTime = respawnTime;
    }
    
    public float getHarvestTimeReq() { return harvestTimeReq; }
    public void setHarvestTimeReq(float harvestTimeReq) {
    	this.harvestTimeReq = harvestTimeReq;
    }

	public boolean getDynamicObject() { return dynamicObject; }
	public void setDynamicObject(boolean dynamicObject) {
		this.dynamicObject = dynamicObject;
	}

	public int getUseLimit() {return useLimit;}
	public void setUseLimit(int useLimit) {this.useLimit = useLimit;}

	public boolean getMakeBusy() {return makeBusy;}
	public void setMakeBusy(boolean makeBusy) {this.makeBusy = makeBusy;}

	public float getDespawnTime() {return despawnTime;}
	public void setDespawnTime(float despawnTime) {this.despawnTime = despawnTime;}

	public float getDespawnDelay() {return despawnDelay;}
	public void setDespawnDelay(float despawnDelay) {this.despawnDelay = despawnDelay;}

	public int getMinLevel() {return minLevel;}
	public void setMinLevel(int minLevel) {this.minLevel = minLevel;}

	public int getMaxLevel() {return maxLevel;}
	public void setMaxLevel(int maxLevel) {this.maxLevel = maxLevel;}

	public int getItemReq() {return itemReq;}

	public void setItemReq(int itemReq) {this.itemReq = itemReq;}

	public int getItemCountReq() {return itemCountReq;}
	public void setItemCountReq(int itemCountReq) {this.itemCountReq = itemCountReq;}

	public boolean getItemReqGet() {return itemReqGet;}
	public void setItemReqGet(boolean itemReqGet) {this.itemReqGet = itemReqGet;}

	public int getCurrencyReq() {return currencyReq;}
	public void setCurrencyReq(int currencyReq) {this.currencyReq = currencyReq;}

	public int getCurrencyCountReq() {return currencyCountReq;}
	public void setCurrencyCountReq(int currencyCountReq) {this.currencyCountReq = currencyCountReq;}

	public boolean getCurrencyReqGet() {return currencyReqGet;}
	public void setCurrencyReqGet(boolean currencyReqGet) {this.currencyReqGet = currencyReqGet;}

	public float getInteractionDistance() {return interactionDistance;}
	public void setInteractionDistance(float interactionDistance) {this.interactionDistance = interactionDistance;}

	public void AddCoordEffect(String effect) {coordinatedEffectsList.add(effect);}

	public int getProfileId() {return profileId;}
	public void setProfileId(int profileId) {this.profileId = profileId;}

	public LinkedList<String> getCoordinatedEffectsList() {return coordinatedEffectsList;}
	public void setCoordinatedEffectsList(LinkedList<String> coordinatedEffectsList) {this.coordinatedEffectsList = coordinatedEffectsList;}

	int id;
	int profileId = -1;
	boolean dynamicObject = false;
	String name;
    int questIDReq;
    String interactionType;
    int interactionID;
    String interactionData1;
    String interactionData2;
    String interactionData3;
    String gameObject;
    String coordinatedEffect;
	LinkedList<String> coordinatedEffectsList = new LinkedList<>();
    String state;
    AOVector loc;
    int respawnTime;
	float interactionDistance = 9F;

	float despawnDelay = 0;
	float despawnTime = 0;
	boolean makeBusy = true;
	int useLimit = -1;
	int minLevel = -1;
	int maxLevel = 99;

	int itemReq = -1;
	boolean itemReqGet = true;
	int itemCountReq = 0;
	int currencyReq =-1;
	boolean currencyReqGet =true;
	int currencyCountReq = 0;
	AtomicInteger used = new AtomicInteger();


	OID instanceOID;
    OID objectOID;
    HashMap<String, Serializable> props;
    float harvestTimeReq = 0;
    boolean active;
    Long eventSub = null;
    LinkedList<OID> playersInRange = new LinkedList<OID>();

	InteractTask task;
	ConcurrentHashMap<OID,InteractTask> tasks = new ConcurrentHashMap<>();
	Long sub = null;
	ConcurrentHashMap<OID,Long> subs = new ConcurrentHashMap<>();
	InteractiveObjectEntity resourceNodeEntity;




	public class DeactivateInteractiveObject implements Runnable {
		protected InteractiveObject obj;

		public DeactivateInteractiveObject(InteractiveObject obj) {
			this.obj = obj;
		}

		@Override
		public void run() {
			Log.debug("INTERACTIVE: run deactivate task "+obj.getID());
			obj.despawnResource();
		}
	}

	public class DespawnInteractiveObject implements Runnable {
		protected InteractiveObject obj;

		public DespawnInteractiveObject(InteractiveObject obj) {
			this.obj = obj;
		}

		@Override
		public void run() {
			Log.debug("INTERACTIVE: run despwan "+obj.getID());
			obj.despawn();
		}
	}

    /**
     * A Runnable class that adds an object to the claim when it is run. 
     * @author Andrew Harrison
     *
     */
    public class InteractTask implements Runnable {
    	
    	protected AOVector loc;
    	protected Quaternion orient;
    	protected OID playerOid;
    	protected int playerSkillLevel;
    	protected InteractiveObject obj;
    	protected String state;
    	protected boolean interrupted;
    	protected CoordinatedEffectState coordinatedEffectState;
    	public InteractTask() {
    		
    	}
    	
    	public void StartInteractTask(AOVector loc, Quaternion orient, OID playerOid, InteractiveObject obj, String state) {
    		Log.debug("INTERACTIVE: creating new interactive task");
    		this.loc = loc;
    		this.orient = orient;
    		this.playerOid = playerOid;
    		this.obj = obj;
    		this.state = state;
    	}
    	
    	public void sendStartInteractTask(float length) {
			Log.debug("INTERACTIVE: sending start interactive task");
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "start_interactive_task");
			props.put("intObjId", obj.getID());
			props.put("length", length);
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);

			// Send animation
			CoordinatedEffect cE = new CoordinatedEffect(obj.coordinatedEffect);
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			cE.putArgument("interObjId", obj.getID());
			cE.putArgument("length", length);
			coordinatedEffectState = cE.invoke(playerOid, playerOid);
		}
  	
		@Override
		public void run() {
			Log.debug("INTERACTIVE: run interactive task "+obj.getID());
			if(makeBusy) {
				if (obj.sub != null)
					Engine.getAgent().removeSubscription(obj.sub);
				if (interrupted) {
					Log.debug("INTERACTIVE: task was interrupted, not completing run");
					obj.task = null;
					return;
				}
				obj.interactComplete(this);
				obj.task = null;
				coordinatedEffectState = null;
			} else {
				long sub = subs.getOrDefault(playerOid,-1L);
				if (sub > -1L)
					Engine.getAgent().removeSubscription(sub);
				subs.remove(playerOid);
				if (interrupted) {
					Log.debug("INTERACTIVE: task was interrupted, not completing run");
					tasks.remove(playerOid);
					return;
				}
				obj.interactComplete(this);
				tasks.remove(playerOid);
				coordinatedEffectState = null;
			}
		}
		
		public void interrupt() {
			interrupted = true;
			if(used.get()>0)
				used.decrementAndGet();
		 	EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "castingParam", -1);
			if(coordinatedEffectState != null)
				coordinatedEffectState.invokeCancel();
			Map<String, Serializable> props = new HashMap<String, Serializable>();
        	props.put("ext_msg_subtype", "interactive_task_interrupted");
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
	  		Engine.getAgent().sendBroadcast(msg);
		}
    }
    

    /**
     * Sub-class needed for the interpolated world node so a perceiver can be created.
     * @author Andrew
     *
     */
	public class InteractiveObjectEntity extends ObjectStub implements EntityWithWorldNode
	{

		public InteractiveObjectEntity(OID oid, InterpolatedWorldNode node) {
	    	setWorldNode(node);
	    	setOid(oid);
	    }
		
		public InterpolatedWorldNode getWorldNode() { return node; }
	    public void setWorldNode(InterpolatedWorldNode node) { this.node = node; }
	    InterpolatedWorldNode node;

		@Override
		public void setDirLocOrient(BasicWorldNode bnode) {
			if (node != null)
	            node.setDirLocOrient(bnode);
		}

		@Override
		public Entity getEntity() {
			return (Entity)this;
		}
		
		private static final long serialVersionUID = 1L;
	}
	
	private static final long serialVersionUID = 1L;

	
}
