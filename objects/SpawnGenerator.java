package atavism.agis.objects;

import atavism.msgsys.*;
import atavism.agis.behaviors.CombatBehavior;
import atavism.agis.plugins.AgisMobPlugin;
import atavism.agis.plugins.CombatClient;
import atavism.server.objects.*;
import atavism.server.engine.*;
import atavism.server.math.*;
import atavism.server.messages.*;
import atavism.server.util.*;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.telemetry.Prometheus;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

// spawn generators are objects that can be placed - these special objects
// spawn mobs and also keep track of when the mob dies so it can respawn them

public class SpawnGenerator
    implements MessageDispatch, Runnable
{
    
    public SpawnGenerator(SpawnData data) {
        initExecutor();
        initialize(data);
    }
    
    private static synchronized void initExecutor() {
        if (EXECUTOR == null) {
            EXECUTOR = new ThreadPoolExecutor(Engine.SpawnGeneratorThreadPoolSize, Engine.SpawnGeneratorThreadPoolSize, 0, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(), new NamedThreadFactory("SpawnGenerator", true));
        }
    }

    public void initialize(SpawnData data) {
        Log.debug("SpawnGenerator.initialize "+data.getName());
        setSpawnData(data);
        setName(data.getName());
        setSpawnID((Integer)data.getProperty("id"));
        setInstanceOid(data.getInstanceOid());
        setLoc(data.getLoc());
        setOrientation(data.getOrientation());
        setSpawnRadius(data.getSpawnRadius());
        setNumSpawns(data.getNumSpawns());
        setRespawnTime(data.getRespawnTime());
        setRespawnTimeMax(data.getRespawnTimeMax());
         if (data.getCorpseDespawnTime() != null)
            setCorpseDespawnTime(data.getCorpseDespawnTime());
        if (data.getProperty("spawnActiveStartHour") != null) {
        	startHour = (Integer)data.getProperty("spawnActiveStartHour");
        }
        if (data.getProperty("spawnActiveEndHour") != null) {
        	endHour = (Integer)data.getProperty("spawnActiveEndHour");
        }
        if(data.getTemplateAlterIDs().size()>0) {
        	alternateSpawnMobTemplate = data.getRandomTemplateAlterID();
        	standardSpawnMobTemplate = data.getRandomTemplateID();
        }
        if (data.getProperty("alternateSpawnMobTemplate") != null) {
        	alternateSpawnMobTemplate = (Integer)data.getProperty("alternateSpawnMobTemplate");
        	standardSpawnMobTemplate = data.getTemplateID();
        }
        Log.debug("SpawnGenerator.initialize End");
    }

    public String ToString() {
    	return "SpawnGanarator: "+spawnData.getName()+" "+instanceOid;
    }
    public void activate() {
    	if (startHour == -1 && endHour == -1 /*&& !isSpawnInDisabledArea(instanceOid, loc)*/) {
         	spawnMobs();
    	 }
    }
    
    public void spawnMobs() {
    	try {
    		active = true;
            spawns = ConcurrentHashMap.newKeySet();
            for (int i = 0; i < numSpawns; i++) {
                run();
            }
        }
        catch (Exception e) {
            throw new AORuntimeException("activate failed", e);
        }
    }

    public void deactivate(boolean cleanup)
    {

        if (spawns == null)
            return;

        for (ObjectStub obj : spawns) {
            try {
				long tStart = System.nanoTime();
				if (Log.loggingTrace) Log.trace("SpawnGenerator.deactivate() "+instanceOid+" "+obj.getOid());
                //obj.unload();
            	obj.despawn(cleanup);
				long tStart2 = System.nanoTime();
                if(!cleanup) ObjectManagerClient.unloadObject(obj.getOid());
				long tStart3 = System.nanoTime();
                removeDeathWatch(obj.getOid());
				if (Log.loggingTrace)Log.trace("SpawnGenerator.deactivate() "+instanceOid+" "+obj.getOid()+" T1="+(System.nanoTime() - tStart)+" T2="+(System.nanoTime() - tStart2)+" T3="+(System.nanoTime() - tStart3));
            }
            catch (Exception e) {
                Log.exception("SpawnGenerator.deactivate()", e);
            }
        }
        spawns = null;
    }
    
    public void deactivateNotInCombat() {
    	 if (Log.loggingTrace) Log.trace("SpawnGenerator.deactivateNotInCombat standardSpawnMobTemplate:"+standardSpawnMobTemplate+ " alternateSpawnMobTemplate:"+alternateSpawnMobTemplate);
    	active = false;
    	if (spawns == null)
            return;
        for (ObjectStub obj : spawns) {
        	try {
        		// Check if it has a combat behavior and if it is in combat
        		boolean deactivate = true;
        		for (Behavior behav : obj.getBehaviors()) {
        			if (behav instanceof CombatBehavior) {
        				CombatBehavior cBehav = (CombatBehavior) behav;
        				 if (Log.loggingTrace)	Log.trace("SpawnGenerator.deactivateNotInCombat cBehav.getInCombat():"+cBehav.getInCombat()+" CombatPlugin.MOB_FORCE_DESPAWN_IN_COMBAT:"+AgisMobPlugin.MOB_FORCE_DESPAWN_IN_COMBAT);
        	        	if (cBehav.getInCombat()) {
        	        		
        	        		if(AgisMobPlugin.MOB_FORCE_DESPAWN_IN_COMBAT) {
        	        			CombatClient.autoAttack(obj.getOid(), null, false);
        	        		}else {
        	        	      cBehav.setDeactivateOutOfCombat(true);
        	        	      deactivate = false;
        	        	      break;
        	        		}
        				}
        			}
        		}
        		 if (Log.loggingTrace)	Log.trace("SpawnGenerator.deactivateNotInCombat deactivate:"+deactivate);
        		
        		if (deactivate) {
        			obj.despawn(false);
        			ObjectManagerClient.unloadObject(obj.getOid());
        			removeDeathWatch(obj.getOid());
        		}else{
        			
        		}
        	} catch (Exception e) {
                Log.exception("SpawnGenerator.deactivate()", e);
            }
        }
        if (Log.loggingTrace)   Log.trace("SpawnGenerator.deactivateNotInCombat End");
    	
    }

    public void handleMessage(Message msg, int flags) {
        try {
			if (msg instanceof PropertyMessage) {
			    PropertyMessage propMsg = (PropertyMessage) msg;
			    OID oid = propMsg.getSubject();
			    Boolean dead = (Boolean)propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
			    Random rand = new Random();
				if (dead != null && dead) {
					 if (Log.loggingDebug) Log.debug("SpawnGenerator.handleMessage: "+oid+" dead");
			        removeDeathWatch(oid);
			        ObjectStub obj = (ObjectStub) EntityManager.getEntityByNamespace(oid, Namespace.MOB);
			        if ((obj != null) && (corpseDespawnTime != -1)) {
						Engine.getExecutor().schedule(new CorpseDespawner(obj), corpseDespawnTime, TimeUnit.MILLISECONDS);
			        }
			        if (respawnTime != -1) {
			        	int time =	respawnTime;
			        	if(respawnTimeMax>0 &&respawnTimeMax>respawnTime)
			        		time = respawnTime + rand.nextInt(respawnTimeMax-respawnTime);
			        	
			        	Engine.getExecutor().schedule(this, time, TimeUnit.MILLISECONDS);
			        }
			        return;
			    }
			    Boolean empty = (Boolean)propMsg.getProperty("objectEmpty");
			    if (empty != null && empty) {
			    	   if (Log.loggingDebug)Log.debug("SPAWNGEN: mob is empty, despawning");
			    	removeDeathWatch(oid);
			        ObjectStub obj = (ObjectStub) EntityManager.getEntityByNamespace(oid, Namespace.MOB);
			        //if ((obj != null) && (corpseDespawnTime != -1))
			        //    Engine.getExecutor().schedule(new CorpseDespawner(obj), corpseDespawnTime, TimeUnit.MILLISECONDS);
			        // Despawn in 0.5 seconds
			        if ((obj != null))
			                Engine.getExecutor().schedule(new CorpseDespawner(obj), 500, TimeUnit.MILLISECONDS);
			        if (respawnTime != -1) {
			        	int time =	respawnTime;
			        	if(respawnTimeMax>0 &&respawnTimeMax>respawnTime)
			        		time = respawnTime + rand.nextInt(respawnTimeMax-respawnTime);
			        	Engine.getExecutor().schedule(this, time, TimeUnit.MILLISECONDS);
			        }
			        return;
			    }	
			    Boolean tamed = (Boolean)propMsg.getProperty("tamed");
			    if (tamed != null && tamed) {
			    	   if (Log.loggingDebug)	Log.debug("SPAWNGEN: mob is tamed, despawning");
			    	removeDeathWatch(oid);
			        ObjectStub obj = (ObjectStub) EntityManager.getEntityByNamespace(oid, Namespace.MOB);
			        if ((obj != null) && (corpseDespawnTime != -1)) {
			            Engine.getExecutor().schedule(new CorpseDespawner(obj), corpseDespawnTime, TimeUnit.MILLISECONDS);
			        }
			        if (respawnTime != -1) {
			        	int time =	respawnTime;
			        	if(respawnTimeMax>0 &&respawnTimeMax>respawnTime)
			        		time = respawnTime + rand.nextInt(respawnTimeMax-respawnTime);
			       	Engine.getExecutor().schedule(this, time, TimeUnit.MILLISECONDS);
			        }
			        return;
			    }
			    Boolean despawn = (Boolean)propMsg.getProperty("despawn");
			    if (despawn != null && despawn) {
			    	   if (Log.loggingDebug) Log.debug("SPAWNGEN: despawning");
			        ObjectStub obj = (ObjectStub) EntityManager.getEntityByNamespace(oid, Namespace.MOB);
			        obj.despawn(false);
			        ObjectManagerClient.unloadObject(obj.getOid());
			        removeDeathWatch(oid);
			        return;
			    }
			}
		} catch (Exception e) {
			Log.exception("Spawngenerator.handleMessage",e);
			//e.printStackTrace();
		}
    }

	protected void spawnObject() {
		Log.debug("SpawnGenerator spawnObject Name ="+getName());
		long tStart = System.nanoTime();
		if (spawns == null)
			return;
		Point spawnPoint = getLoc();
		if (spawnPoint == null) {
			Log.error("SpawnGenerator.spawnObject: getLoc() returned null ");
			return;
		}
		Point loc = Points.findNearby(spawnPoint, spawnRadius);
		ObjectStub obj = null;
		obj = factory.makeObject(spawnData, instanceOid, loc);
		if (obj == null) {
			Log.error("SpawnGenerator: Factory.makeObject failed, returned null, factory=" + factory);
			return;
		}
		if (Log.loggingDebug)
			Log.debug("SpawnGenerator.spawnObject: name=" + getName() + ", created object " + obj + " at loc=" + loc + " template:" + spawnData.getTemplateID());
		addDeathWatch(obj.getOid());
        long tMakeObject = System.nanoTime();
		obj.spawn();
        long tSpawn = System.nanoTime();
		spawns.add(obj);
		updateObjectProperties(obj);
        long tUpdateProps = System.nanoTime();
		if (Log.loggingDebug)
			Log.debug("SpawnGenerator.spawnObject: name=" + getName() + ", spawned obj " + obj);
		// Log.debug("SPAWN: mob has combat property: " +
		// EnginePlugin.getObjectProperty(obj.getOid(), CombatClient.NAMESPACE, "combatstate"));
		Log.debug("SpawnGenerator spawnObject End");
        io.micrometer.core.instrument.Timer.builder("spawn_object").tag("phase", "total").register(Prometheus.registry())
        .record(Duration.ofNanos(System.nanoTime() - tStart));
        io.micrometer.core.instrument.Timer.builder("spawn_object").tag("phase", "make").register(Prometheus.registry())
        .record(Duration.ofNanos(tMakeObject - tStart));
        io.micrometer.core.instrument.Timer.builder("spawn_object").tag("phase", "spawn").register(Prometheus.registry())
        .record(Duration.ofNanos(tSpawn - tMakeObject));
        io.micrometer.core.instrument.Timer.builder("spawn_object").tag("phase", "props").register(Prometheus.registry())
        .record(Duration.ofNanos(tUpdateProps - tSpawn));
		
	}

    /**
     * Sets certain properties for the object that was just spawned.
     * @param obj
     */
    @SuppressWarnings("unchecked")
    protected void updateObjectProperties(ObjectStub obj) {
        Map<String, Serializable>  copy = new HashMap<>();
        Map<String, Serializable> props = (Map<String, Serializable>) spawnData.getProperty("props");
    	if (props != null) {
    	    copy.putAll(props);
    	}
    	String baseAction = (String) spawnData.getProperty("baseAction");
    	if (baseAction != null) {
    	    copy.put("currentAction", baseAction);
    	}
    	/*boolean weaponsSheathed = spawnData.getBooleanProperty("weaponsSheathed");
    	EnginePlugin.setObjectProperty(obj.getOid(), WorldManagerClient.NAMESPACE, "weaponsSheathed", weaponsSheathed);*/
    	// Merchant
    	Integer merchantTable = (Integer) spawnData.getProperty("merchantTable");
    	if (merchantTable != null) {
            copy.put("merchantTable", merchantTable);
    	}
    	if (!copy.isEmpty()) {
    	    EnginePlugin.setObjectProperties(obj.getOid(), WorldManagerClient.NAMESPACE, new HashMap<>(props));
    	}
    //	WorldManagerClient.LoadSubObjectMessage loadSubMsg = new WorldManagerClient.LoadSubObjectMessage(obj.getOid(), Namespace.MOB,new Point(),instanceOid);
		//Engine.getAgent().sendRPCReturnBoolean(loadSubMsg);
	
    	/*String otherUse = (String) spawnData.getProperty("otherUse");
    	if (otherUse != null) {
    		if (otherUse.equals("Arena Master"))
    		    EnginePlugin.setObjectProperty(obj.getOid(), WorldManagerClient.NAMESPACE, "arenaMaster", true);
    	}*/
    	// Display Updates
    	/*String gender = (String) spawnData.getProperty("genderOptions");
    	if (gender.contains("Either")) {
    		Random random = new Random();
    		if (random.nextInt(2) == 0)
    			gender = "Male";
    		else
    			gender = "Female";
    	}
    		WorldManagerClient.LoadSubObjectMessage loadSubMsg = new WorldManagerClient.LoadSubObjectMessage(oid, Namespace.MOB,new Point(),instanceOid);
			Engine.getAgent().sendRPCReturnBoolean(loadSubMsg);
		
    	AgisMobPlugin.setDisplay(obj.getOid(), gender);*/
    }

    protected void spawnObject(int millis) {
		if (Log.loggingDebug)Log.debug("SpawnGenerator:  "+millis);
        if (spawns == null)
            return;
        Log.debug("SpawnGenerator: adding spawn timer");
        Engine.getExecutor().schedule(this, millis, TimeUnit.MILLISECONDS);
    }

    // Called by scheduled executor
    public void run() {
        EXECUTOR.execute(() -> {
            try {
                spawnObject();
            } catch (AORuntimeException e) {
                Log.exception("SpawnGenerator.run caught exception: ", e);
            } catch (Exception e) {
                Log.exception("SpawnGenerator.run caught exception: ", e);
            }
        });
    }

    protected void addDeathWatch(OID oid) {
        if (Log.loggingDebug)
            Log.debug("SpawnGenerator.addDeathWatch: oid=" + oid);
        SubscriptionManager.get().subscribe(this, oid, PropertyMessage.MSG_TYPE_PROPERTY);
    }
    
    protected void removeDeathWatch(OID oid) {
        SubscriptionManager.get().unsubscribe(this);
    }
    
    public int getSpawnId()
    {
    	return spawnID;
    }
    
    public void setSpawnID(int spawnID)
    {
    	this.spawnID = spawnID;
    }

    public OID getInstanceOid()
    {
        return instanceOid;
    }

    public void setInstanceOid(OID oid)
    {
        if (instanceOid == null) {
            instanceOid = oid;
            addInstanceContent(this);
        }
        else
            throw new AORuntimeException("Cannot change SpawnGenerator instanceOid, from="+instanceOid + " to="+oid);
    }

    public void setName(String name) { this.name = name; }
    public String getName() { return name; }

    public void setLoc(Point p) { loc = p; }
    public Point getLoc() { return loc; }

    public void setOrientation(Quaternion o) { orient = o; }
    public Quaternion getOrientation() { return orient; }

    public int getSpawnRadius() { return spawnRadius; }
    public void setSpawnRadius(int radius) { spawnRadius = radius; }

    // how long after death does it take to respawn
    public int getRespawnTime() { return respawnTime; }
    public void setRespawnTime(int milliseconds) { respawnTime = milliseconds; }
    public int getRespawnTimeMax() { return respawnTimeMax; }
    public void setRespawnTimeMax(int milliseconds) { respawnTimeMax = milliseconds; }

    public int getNumSpawns() { return numSpawns; }
    public void setNumSpawns(int num) { numSpawns = num; }

    public int getCorpseDespawnTime() { return corpseDespawnTime; }
    public void setCorpseDespawnTime(int time) { corpseDespawnTime = time; }

    public ObjectFactory getObjectFactory() { return factory; }
    public void setObjectFactory(ObjectFactory factory) { this.factory = factory; }
    

    public SpawnData getSpawnData() { return spawnData; }

    public void setSpawnData(SpawnData spawnData) { this.spawnData = spawnData; }

    protected class CorpseDespawner implements Runnable {
    	public CorpseDespawner(ObjectStub obj) {
	    this.obj = obj;
	    if (Log.loggingDebug) 	Log.debug("CorpseDespawner: "+obj.getOid());
    	}
    	protected ObjectStub obj;

    	public void run() {
    		if (Log.loggingDebug) 	Log.debug("CorpseDespawner.run: "+obj.getOid());
            if (spawns == null)
                return;
            spawns.remove(obj);
            try {
                // ObjectStub.despawn() does a local unload then a
                // WM despawn.  The mob does not have a mob sub-object,
                // so the object manager unload won't affect the mob
                // manager local data structures.
        		if (Log.loggingDebug) 	Log.debug("CorpseDespawner.run: "+obj.getOid()+" despawn");
            	obj.despawn(false);
            	ObjectManagerClient.unloadObject(obj.getOid());
            	
            } catch (AORuntimeException e) {
            	Log.exception("SpawnGenerator.CorpseDespawner: exception: ", e);
	    	}
            if (Log.loggingDebug) Log.debug("CorpseDespawner.run: "+obj.getOid()+" End");

    	}
    }

    /**
     * Adds a SpawnGenerator to the instanceContent map.
     * @param spawnGen
     */
    private static void addInstanceContent(SpawnGenerator spawnGen)
	{

		if (Log.loggingDebug) {
			Log.debug("addInstanceContent " + spawnGen);
		}
		Map<String, SpawnGenerator> spawnGenList = instanceContent.computeIfAbsent(spawnGen.getInstanceOid(), __ -> new ConcurrentHashMap<>());
		if(Log.loggingDebug) {
			Set<String> keys = spawnGenList.keySet();
			Log.debug("SpawnGenerator.addInstanceContent: keys="+keys);
		}
		if (spawnGen.getName() != null) {
			if (spawnGenList.containsKey(spawnGen.getName())) {
				spawnGenList.put(spawnGen.getName() + "_" + System.nanoTime(), spawnGen);
			} else {
				spawnGenList.put(spawnGen.getName(), spawnGen);
			}
		} else {
			spawnGenList.put("InstanceSpawn_" + spawnGen.getSpawnId(), spawnGen);
		}
		if(Log.loggingDebug) {
			Set<String> keys = spawnGenList.keySet();
			Log.debug("SpawnGenerator.addInstanceContent: End keys="+keys);
		}
	}

    /**
     * Deactivates all SpawnGenerators belonging to the instanceOid specified. 
     * @param instanceOid
     */
    public static void cleanupInstance(OID instanceOid)
    {
		if (Log.loggingDebug)Log.debug("cleanupInstance "+instanceOid);
    	Map<String, SpawnGenerator> spawnGenList =  instanceContent.remove(instanceOid);
        if (spawnGenList != null) {
            for (SpawnGenerator spawnGen : spawnGenList.values()) {
                spawnGen.deactivate(true);
            }
        }
    }
    
    /**
     * Deactivates and removes the SpawnGenerator based on the spawnName and instanceOid given. 
     * @param instanceOid
     * @param spawnName
     */
	public static void removeSpawnGenerator(OID instanceOid, String spawnName) {
		Map<String, SpawnGenerator> spawnGenList = instanceContent.get(instanceOid);
		if (Log.loggingDebug) {
			Log.debug("AJ: instanceContent - " + instanceContent);
			Log.debug("AJ: spawnGenList - " + spawnGenList);
		}
		boolean removed = false;
		if (spawnGenList != null) {
			Set<String> keys = spawnGenList.keySet();
			List<String> keysToRemove = new ArrayList<String>();
			for (String key : keys) {
				if(Log.loggingDebug)Log.debug("removeSpawnGenerator: spawnName="+spawnName+" key="+key);
				if (key.startsWith(spawnName)) {
					keysToRemove.add(key);
				}
			}
			if(Log.loggingDebug)Log.debug("removeSpawnGenerator: spawnName="+spawnName+" keysToRemove="+keysToRemove);
			for (String key : keysToRemove) {
				SpawnGenerator sg = spawnGenList.remove(key);
				if (sg != null) {
					sg.deactivate(false);
					removed = true;
				}
			}
		}
		if (!removed) {
			Log.debug("CORPSE: spawnName not found: " + spawnName);
		}
	}

    /**
     * Deactivates and removes the SpawnGenerator based on the spawnID and instanceOid given.
     * @param instanceOid
     * @param spawnID
     */
    public static void removeSpawnGeneratorByID(OID instanceOid, int spawnID)
    {
        if (Log.loggingDebug) {
            Log.debug("DESPAWN: removeSpawnGeneratorByID: spawnID="+spawnID+" "+instanceContent.containsKey(instanceOid));
        }
    	Map<String, SpawnGenerator> spawnGenList = instanceContent.get(instanceOid);
        if (Log.loggingDebug) {
            Log.debug("AJ: instanceContent - " + instanceContent);
            Log.debug("AJ: spawnGenList - " + spawnGenList);
        }
        if (spawnGenList != null) {
        	for (SpawnGenerator sg : spawnGenList.values()) {
                if (Log.loggingDebug) {
                    Log.debug("DESPAWN: comparing spawnGen: " + sg.getSpawnId() + " against id: " + spawnID);
                }
        		if (sg.getSpawnId() == spawnID) {
        			sg.deactivate(false);
        		}
        	}
        }
        if (Log.loggingDebug) {
            Log.debug("DESPAWN: removeSpawnGeneratorByID: spawnID="+spawnID+" END");
        }
    }
    
    /**
     * Goes through all SpawnGenerators in the instanceOid and respawns all mobs that
     * match the mob template ID.
     * @param instanceOid
     * @param mobID
     */
    public static void respawnMatchingMobs(OID instanceOid, int mobID)
    {
        Map<String, SpawnGenerator> spawnGenList = instanceContent.get(instanceOid);
        if (Log.loggingDebug) {
            Log.debug("AJ: instanceContent - " + instanceContent);
            Log.debug("AJ: spawnGenList - " + spawnGenList);
        }
        if (spawnGenList != null) {
            for (SpawnGenerator sg : spawnGenList.values()) {
            	if (sg.getObjectFactory().getTemplateID() == mobID) {
            		sg.deactivate(false);
            		sg.activate();
            	}
            }
        }
    }
    
    /**
     * Goes through all SpawnGenerators in the instanceOid and respawns all vehicles 
     * that match the vehicle template ID.
     * @param instanceOid The instance to check for matching vehicles.
     * @param vehicleID The ID of the vehicle template to respawn.
     */
    public static void respawnMatchingVehicles(OID instanceOid, int vehicleID) {
        Map<String, SpawnGenerator> spawnGenList = instanceContent.get(instanceOid);
        
        if (Log.loggingDebug) {
            Log.debug("AJ: instanceContent - " + instanceContent);
            Log.debug("AJ: spawnGenList - " + spawnGenList);
        }
        
        if (spawnGenList != null) {
            for (SpawnGenerator sg : spawnGenList.values()) {
                if (sg.getObjectFactory().getTemplateID() == vehicleID) {
                    sg.deactivate(false);
                    sg.activate();
                }
            }
        }
    }

    
    /**
     * Goes through all spawn generators and activates/deactivates them based on their active times.
     * @param hour
     * @param minute
     */
    public static void serverTimeUpdate(int hour, int minute) {
    	if (Log.loggingTrace)
			Log.trace("SpawnGenerator.serverTimeUpdate hour:"+hour+" minute:"+minute );
    	for (Map<String, SpawnGenerator> generators : instanceContent.values()) {
    		Set<SpawnGenerator> spawnGenerators = new HashSet<SpawnGenerator>(generators.values());
    		for(SpawnGenerator generator : spawnGenerators) {
    			if (generator.startHour == -1 || generator.endHour == -1)
    				continue;
    			
    			boolean withinTime = false;
    			if (generator.endHour < generator.startHour) {
					if (hour < generator.endHour || hour >= generator.startHour)
						withinTime = true;
				} else {
					if (hour >= generator.startHour && hour < generator.endHour)
						withinTime = true;
				}
    			if (generator.alternateState != 0 && !withinTime) {
    				if (Log.loggingTrace)
    					Log.trace("SpawnGenerator.serverTimeUpdate hour:"+hour+" minute:"+minute );
    				if (Log.loggingTrace)
    					Log.trace("SpawnGenerator.serverTimeUpdate alternateState:"+generator.alternateState+" withinTime:"+withinTime);
    				if (Log.loggingTrace)
    					Log.trace("TIME: deactivating spawnGenerator");
    				// Deactivate
    				generator.deactivateNotInCombat();
    				if (Log.loggingTrace)
    					Log.trace("SpawnGenerator.serverTimeUpdate alternateSpawnMobTemplate:"+generator.alternateSpawnMobTemplate);
    				   if (generator.alternateSpawnMobTemplate != -1) {
    					 
    					HashMap<Integer, Integer> templateIDs = new HashMap<Integer, Integer>();
    					templateIDs.put(generator.alternateSpawnMobTemplate, 100);
    					if (Log.loggingTrace)
        					Log.trace("SpawnGenerator.serverTimeUpdate  set alternateSpawnMobTemplate:"+generator.alternateSpawnMobTemplate);
    	    		    generator.spawnData.setTemplateIDs(templateIDs);
    					generator.spawnMobs();
    				}
    				generator.alternateState = 0;
    			} else if (generator.alternateState != 1 && withinTime) {
    				if (Log.loggingTrace)
    					Log.trace("SpawnGenerator.serverTimeUpdate hour:"+hour+" minute:"+minute );
    				if (Log.loggingTrace)
    					Log.trace("SpawnGenerator.serverTimeUpdate alternateState:"+generator.alternateState+" withinTime:"+withinTime);
    	    			// Activate
    				if (Log.loggingTrace)
    					Log.trace("TIME: activating spawnGenerator");
    				if (Log.loggingTrace)
    					Log.trace("SpawnGenerator.serverTimeUpdate 2 alternateSpawnMobTemplate:"+generator.alternateSpawnMobTemplate);
    				if (generator.alternateSpawnMobTemplate != -1) {
    					generator.deactivateNotInCombat();
    					HashMap<Integer, Integer> templateIDs = new HashMap<Integer, Integer>();
    					templateIDs.put(generator.standardSpawnMobTemplate, 100);
    					if (Log.loggingTrace)
    						Log.trace("SpawnGenerator.serverTimeUpdate  set standardSpawnMobTemplate:"+generator.standardSpawnMobTemplate);
    		    		generator.spawnData.setTemplateIDs(templateIDs);
    				}
    				generator.spawnMobs();
    				generator.alternateState = 1;
    			}
    		}
    	}
    }
    
    /**
     * Goes through all SpawnGenerators in the instanceOid and deactivates any that are
     * within the disabled area.
     * @param instanceOid
	 * @param loc
	 * @param radius
	 */
    public static void disableSpawnsInArea(OID instanceOid, Point loc, float radius)
    {
    	Map<Point, Float> instanceDisabledAreas = disabledSpawnAreas.computeIfAbsent(instanceOid, __ -> new ConcurrentHashMap<>());
    	instanceDisabledAreas.put(loc, radius);
    	
    	Map<String, SpawnGenerator> spawnGenList = instanceContent.get(instanceOid);
        if (Log.loggingDebug) {
            Log.debug("AJ: instanceContent - " + instanceContent);
            Log.debug("AJ: spawnGenList - " + spawnGenList);
        }
        if (spawnGenList != null) {
            for (SpawnGenerator sg : spawnGenList.values()) {
            	if (Point.distanceTo(sg.getLoc(), loc) < radius) {
            		sg.deactivate(false);
            	}
            }
        }
    }
    
    /**
     * Checks if the spawn generator is located within a disabled area.
     * @param instanceOid
     * @param loc
     * @return
     */
	public static boolean isSpawnInDisabledArea(OID instanceOid, Point loc) {
		Map<Point, Float> disabled = disabledSpawnAreas.get(instanceOid);
        if (disabled == null) {
            return false;
        }
        for (Entry<Point, Float> e : disabled.entrySet()) {
            Point instanceDisabledArea = e.getKey(); 
			if (Point.distanceTo(instanceDisabledArea, loc) < e.getValue()) {
				return true;
			}
		}
		return false;
	}

	protected int spawnID = -1;
	protected OID instanceOid = null;
	protected String name = null;
	protected Point loc = null;
	protected Quaternion orient = null;
	protected int spawnRadius = 0;
	protected int respawnTime = 0;
	protected int respawnTimeMax = 0;
	protected int numSpawns = 3;
	protected int corpseDespawnTime = -1;
	protected int startHour = -1;
	protected int endHour = -1;
	protected int alternateSpawnMobTemplate = -1;
	protected int standardSpawnMobTemplate = -1;
	protected int alternateState = -1;
	protected boolean active = false;
	protected SpawnData spawnData = null;
	protected ObjectFactory factory = null;
//	protected Map<OID, Long> deathWatchMap = new HashMap<OID, Long>();
	protected Set<ObjectStub> spawns;
	private static Map<OID, Map<String, SpawnGenerator>> instanceContent = new ConcurrentHashMap<>();
	private static Map<OID, Map<Point, Float>> disabledSpawnAreas = new ConcurrentHashMap<>();
    private static ThreadPoolExecutor EXECUTOR;

    private static final long serialVersionUID = 1L;
    
}
