package atavism.agis.plugins;

import atavism.msgsys.Message;
import atavism.msgsys.MessageAgent;
import atavism.msgsys.MessageTypeFilter;
import atavism.msgsys.ResponseMessage;
import atavism.server.engine.*;
import atavism.server.messages.*;
import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;
import atavism.server.telemetry.Prometheus;
import atavism.server.util.Log;
import atavism.server.util.Logger;
import atavism.agis.core.Agis;
import atavism.agis.database.ContentDatabase;
import atavism.agis.database.MobDatabase;
import atavism.agis.objects.*;
import atavism.agis.plugins.FactionClient.getStanceMessage;
import atavism.agis.util.EventMessageHelper;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.benmanes.caffeine.cache.*;
/**
 * Handles faction and attitude related messages, calculates the interaction
 * state between the objects involved and sends out the messages to alert
 * other parts of the server of the interaction state.
 * @author Andrew
 *
 */
public class FactionPlugin extends EnginePlugin {

	public FactionPlugin() {
        super("Faction");
        SubscriptionManager.start(WorldManagerClient.MSG_TYPE_PERCEPTION_INFO);
        setPluginType("Faction");
    }
	
	public String getName()
	{
		return FACTION_PLUGIN_NAME;
	}
	
    /*
     * Properties
     */
	private static final Logger log = new Logger("Faction");
	public static String FACTION_PLUGIN_NAME = "FactionPlugin";

    /*
     * Events
     */ 
    
    public void onActivate() {
    	Log.info("MOB: onActivate faction");
    	loadData();
        // register message hooks
		getHookManager().addHook(ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS, new ResolveInteractionStateHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_DESPAWNED, new DespawnedHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
		getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(PropertyMessage.MSG_TYPE_PROPERTY, new PropertyHook());
		getHookManager().addHook(FactionClient.MSG_TYPE_UPDATE_PVP_STATE, new SetPvPHook());
		getHookManager().addHook(FactionClient.MSG_TYPE_SAVE_PVP_TIMER, new SavePVPTimerHook());
		getHookManager().addHook(FactionClient.MSG_TYPE_ALTER_REPUTATION, new AlterReputationHook());

		getHookManager().addHook(WorldManagerClient.MSG_TYPE_PERCEPTION_INFO, new PerceptionHook());

		getHookManager().addHook(WorldManagerClient.MSG_TYPE_UPDATE_OBJECT, new UpdateObjectHook());
		getHookManager().addHook(FactionClient.MSG_TYPE_GET_STANCE, new GetObjectStanceHook());
		getHookManager().addHook(FactionClient.MSG_TYPE_GET_STANCE_TARGETS, new GetObjectsStanceHook());
		        // setup message filters
        MessageTypeFilter filter = new MessageTypeFilter();
        //filter.addType(ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS);
        filter.addType(CombatClient.MSG_TYPE_FACTION_UPDATE);
        filter.addType(WorldManagerClient.MSG_TYPE_SPAWNED);
        filter.addType(WorldManagerClient.MSG_TYPE_DESPAWNED);
        filter.addType(PropertyMessage.MSG_TYPE_PROPERTY);
        filter.addType(FactionClient.MSG_TYPE_UPDATE_PVP_STATE);
        filter.addType(FactionClient.MSG_TYPE_SAVE_PVP_TIMER);
        filter.addType(FactionClient.MSG_TYPE_ALTER_REPUTATION);
        filter.addType(WorldManagerClient.MSG_TYPE_UPDATE_OBJECT);
        Engine.getAgent().createSubscription(filter, this);
        
        //setup responder message filters
        MessageTypeFilter responderFilter = new MessageTypeFilter();
        responderFilter.addType(FactionClient.MSG_TYPE_GET_STANCE);
        responderFilter.addType(FactionClient.MSG_TYPE_GET_STANCE_TARGETS);
        responderFilter.addType(LoginMessage.MSG_TYPE_LOGIN);
        responderFilter.addType(LogoutMessage.MSG_TYPE_LOGOUT);
        Engine.getAgent().createSubscription(responderFilter, this, MessageAgent.RESPONDER);
        
        registerLoadHook(Namespace.FACTION, new FactionStateLoadHook());
        //registerSaveHook(Namespace.FACTION, new FactionStateSaveHook());
        registerUnloadHook(Namespace.FACTION, new FactionStateUnloadHook());
        registerPluginNamespace(Namespace.FACTION, new FactionSubObjectHook());
        
       
     	
     	TargetTypesTick timeTick = new TargetTypesTick();
        Engine.getExecutor().scheduleAtFixedRate(timeTick, 5000, 50, TimeUnit.MILLISECONDS);
    	Log.info("MOB: onActivate faction");
    	Engine.registerStatusReportingPlugin(this);
           }
    
    void loadData() {
    	MobDatabase mobDataBase = new MobDatabase(true);
     	
    	ArrayList<Faction> factions = mobDataBase.loadFactions(1);
    	Log.info("MOB: onActivate faction="+factions);
        
    	for (Faction faction: factions) {
    		Agis.FactionManager.register(faction.getID(), faction);
        	Log.info("MOB: loaded faction: [" + faction.getName() + "]");
    	}
    	 // Load settings
     	ContentDatabase cDB = new ContentDatabase(false);

     	String aggroRadius = cDB.loadGameSetting("AGGRO_RADIUS");
     	if (aggroRadius != null)
     		AGGRO_RADIUS = Integer.parseInt(aggroRadius);
     	Log.debug("Load Game Setting SET AGGRO_RADIUS "+AGGRO_RADIUS);
     
     	String factionHatedRep = cDB.loadGameSetting("FACTION_HATED_REP");
     	if (factionHatedRep != null)
     		HatedRep = Integer.parseInt(factionHatedRep);
     	Log.debug("Load Game Setting SET HatedRep "+HatedRep);
     
    	String factionDislikedRep = cDB.loadGameSetting("FACTION_DISLIKE_REP");
     	if (factionDislikedRep != null)
     		DislikedRep = Integer.parseInt(factionDislikedRep);
     	Log.debug("Load Game Setting SET DislikedRep "+DislikedRep);
    	
     	String factionNeutralRep = cDB.loadGameSetting("FACTION_NEUTRAL_REP");
     	if (factionNeutralRep != null)
     		NeutralRep = Integer.parseInt(factionNeutralRep);
     	Log.debug("Load Game Setting SET NeutralRep "+NeutralRep);
    	
     	String factionFriendlyRep = cDB.loadGameSetting("FACTION_FRIENDLY_REP");
     	if (factionFriendlyRep != null)
     		FriendlyRep = Integer.parseInt(factionFriendlyRep);
     	Log.debug("Load Game Setting SET FriendlyRep "+FriendlyRep);
    	
     	String factionHonouredRep = cDB.loadGameSetting("FACTION_HONOURED_REP");
     	if (factionHonouredRep != null)
     		HonouredRep = Integer.parseInt(factionHonouredRep);
     	Log.debug("Load Game Setting SET HonouredRep "+HonouredRep);
     	
    	String factionExaltedRep = cDB.loadGameSetting("FACTION_EXALTED_REP");
     	if (factionExaltedRep != null)
     		ExaltedRep = Integer.parseInt(factionExaltedRep);
     	Log.debug("Load Game Setting SET ExaltedRep "+ExaltedRep);
     
	}

    protected void ReloadTemplates(Message msg) {
		Log.error("Factionlugin ReloadTemplates Start");
		loadData();
		Log.error("Factionlugin ReloadTemplates Recalculate Stance");
		ArrayList<OID> objs = new ArrayList<OID>(objectsInRange.keySet());
		for (OID objOid : objs) {
		 ArrayList<OID> objectsToRecalculate = new ArrayList<OID>(objectsInRange.getOrDefault(objOid, Collections.emptySet()));
			for (OID target : objectsToRecalculate) {
				calculateInteractionState(objOid, target);
			}
		}
		Log.error("FactionPlugin ReloadTemplates End");
	}
    
	  public Map<String, String> getStatusMap() {
	 		Map<String, String> status = new HashMap<String, String>();
	 	//	status.put("instances", Integer.toString(instances.size()));
	 		status.put("entities", Integer.toString(EntityManager.getEntityCount()));
	 		//status.put("list", Integer.toString(EntityManager.getEntityCount()));
	 		status.put("EntList",EntityManager.getEntityNamespaceCount());
	 		return status;
	 	}
  
    public static FactionStateInfo getFactionStateInfo(OID oid) {
		return (FactionStateInfo)EntityManager.getEntityByNamespace(oid, Namespace.FACTION);
	}
	
	public static void registerFactionStateInfo(FactionStateInfo qsInfo) {
		EntityManager.registerEntityByNamespace(qsInfo, Namespace.FACTION);
	}
	
	class FactionStateLoadHook implements LoadHook {
    	public void onLoad(Entity e) {
    		FactionStateInfo qsInfo = (FactionStateInfo) e;
    	}
    }
	
	class FactionStateUnloadHook implements UnloadHook {
    	public void onUnload(Entity e) {
    		FactionStateInfo qsInfo = (FactionStateInfo) e;
    	}
    }
    
    public class FactionSubObjectHook extends GenerateSubObjectHook
    {
    	public FactionSubObjectHook() {
    	    super(FactionPlugin.this);
    	}
    	public SubObjData generateSubObject(Template template, Namespace name, OID masterOid)
    	{
			if (Log.loggingDebug)
				Log.debug("FactionPlugin::GenerateSubObjectHook::generateSubObject()");
			if (masterOid == null) {
				Log.error("GenerateSubObjectHook: no master oid");
				return null;
			}
			if (Log.loggingDebug)
				Log.debug("GenerateSubObjectHook: masterOid=" + masterOid + ", template=" + template);

			Map<String, Serializable> props = template.getSubMap(Namespace.FACTION);

			// generate the subobject
			FactionStateInfo qsInfo = new FactionStateInfo(masterOid);
			qsInfo.setName(template.getName());

			Boolean persistent = (Boolean) template.get(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT);
			if (persistent == null)
				persistent = false;
			qsInfo.setPersistenceFlag(persistent);

			if (props != null) {
				// copy properties from template to object
				for (Map.Entry<String, Serializable> entry : props.entrySet()) {
					String key = entry.getKey();
					Serializable value = entry.getValue();
					log.debug("GenerateSubObjectHook: set param key="+key+" value="+value);
					if (!key.startsWith(":")) {
						qsInfo.setProperty(key, value);
					}
				}
			}
			if (props.containsKey(FactionStateInfo.AGGRO_RADIUS)) {
				qsInfo.setAggroRadius((int)props.get(FactionStateInfo.AGGRO_RADIUS));
			}
			if (props.containsKey("factionData")) {
				qsInfo.isPlayer(true);
				Log.debug("GenerateSubObjectHook: setting isPlayer to true");
			} else {
				qsInfo.isPlayer(false);
			}
			if (props.containsKey("pet")) {
				qsInfo.isPet(true);
				Log.debug("GenerateSubObjectHook: setting isPet to true");
			} else {
				qsInfo.isPet(false);
			}

			if (Log.loggingDebug)
				Log.debug("GenerateSubObjectHook: created entity " + qsInfo);

			// register the entity
			registerFactionStateInfo(qsInfo);

			if (persistent)
				Engine.getPersistenceManager().persistEntity(qsInfo);

			// send a response message
			return new SubObjData();
    	}
    }
    
	class UpdateObjectHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.UpdateMessage updateReq = (WorldManagerClient.UpdateMessage) msg;
			OID subjectOid = updateReq.getSubject();
			OID targetOid = updateReq.getTarget();

			// is the update object spawned?
			FactionStateInfo info = getFactionStateInfo(subjectOid);
			if (info == null) {
				return false;
			}

			// send over properties
			if (Log.loggingDebug)
				log.debug("UpdateObjectHook.processMessage: sending properties for subjectOid=" + info.getName());

			WorldManagerClient.TargetedPropertyMessage propMessage = new WorldManagerClient.TargetedPropertyMessage(targetOid, subjectOid);
			for (Map.Entry<String, Serializable> kvp : info.getPropertyMap().entrySet()) {
				if (!(kvp.getValue() instanceof AgisStat))
					propMessage.setProperty(kvp.getKey(), kvp.getValue(), true);
				// send reputations
				if (kvp.getKey().equals("factionData")) {
					HashMap<Integer, PlayerFactionData> pfdMap = (HashMap) kvp.getValue();
					List<Integer> factionIDs = Agis.FactionManager.keyList();
					Integer subjectFaction = (Integer) info.getProperty(FactionStateInfo.FACTION_PROP);
					int count = 0;
					// for all factions
					for (Integer i : factionIDs) {
						Faction f = Agis.FactionManager.get(i);
						// if faction has reputation
						if (f.getIsPublic()) {
							if (!pfdMap.containsKey(i)) {
								pfdMap.put(i, Faction.addFactionToPlayer(subjectOid, f, subjectFaction, pfdMap));
							}

							PlayerFactionData pfd = pfdMap.get(i);
							String key = "Reputation" + i;
							String value = i + " " + f.getName() + " " + pfd.getReputation();
							propMessage.setProperty(key, value);
							count++;
						}
					}
				}
			}

			if (info.getProperty("pvpTimer") != null) {
				long time = (Long) info.getProperty("pvpTimer");
				long timeLeft = time - Calendar.getInstance().getTimeInMillis();
				if (Log.loggingDebug)
					log.debug("PVP: timeLeft = " + timeLeft);
				if (timeLeft > 0) {
					propMessage.setProperty("pvpTimer", timeLeft);
				} else {
					log.debug("PVP: Timer is up!");
					propMessage.setProperty("pvpTimer", 0L);
				}
			} else {
				log.debug("PVP: Timer is null!");
				propMessage.setProperty("pvpTimer", 0L);
			}

			Engine.getAgent().sendBroadcast(propMessage);

			return true;
		}
	}
	  class LoginHook implements Hook {
	        public boolean processMessage(Message msg, int flags) {
	            LoginMessage message = (LoginMessage) msg;
	            OID playerOid = message.getSubject();
	            logLogin.put(playerOid, System.nanoTime());
	            Engine.getAgent().sendResponse(new ResponseMessage(message));
	            return true;
	        }
	    }

    class LogoutHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LogoutMessage message = (LogoutMessage) msg;
            OID playerOid = message.getSubject();
			if(Log.loggingDebug)Log.debug("FactionPlugin.LogoutHook "+playerOid);
            logLogin.put(playerOid, System.nanoTime());
            Engine.getAgent().sendResponse(new ResponseMessage(message));
            // Find all of the objects the player had in range then remove reactionMappings and target settings
            return true;
        }
    }
    
    class SpawnedHook implements Hook  {
    	public boolean processMessage(Message msg, int flags) {
    		WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;
            OID objOid = spawnedMsg.getSubject();
            logSpawn.put(objOid, System.nanoTime());
            FactionStateInfo factionInfo = getFactionStateInfo(objOid);
            if(Log.loggingDebug)     Log.debug("SPAWNED: got FactionInfo: " + factionInfo + " for objOid: " + objOid);
            if (factionInfo == null)
            	return true;
            
            //if (factionInfo.isPlayer()) {
            	// Create a tracker node
            if(Log.loggingDebug) 	Log.debug("SPAWNED: creating tracker for player: " + objOid);
            //}
            if(Log.loggingDebug) 	Log.debug("SPAWNED: set instanceOid="+ spawnedMsg.getInstanceOid()+" for player: " + objOid);
                	
            factionInfo.setProperty("instanceOid", spawnedMsg.getInstanceOid());
            SubscriptionManager.get().subscribe(FactionPlugin.this, objOid, WorldManagerClient.MSG_TYPE_PERCEPTION_INFO);
            
            return true;
    	}
    }
    
	class DespawnedHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			WorldManagerClient.DespawnedMessage despawnedMsg = (WorldManagerClient.DespawnedMessage) msg;
			OID objOid = despawnedMsg.getSubject();
			logDespawn.put(objOid, System.nanoTime());
	        SubscriptionManager.get().unsubscribe(FactionPlugin.this, objOid);
			removeObjectInRange(objOid, despawnedMsg.getInstanceOid());

			return true;
		}
	}
    
    class SavePVPTimerHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ExtensionMessage timerMsg = (ExtensionMessage)msg;
    		OID playerOid = timerMsg.getSubject();
    		
    		float hours = (Float)timerMsg.getProperty("time");
    		
    		FactionStateInfo info = getFactionStateInfo(playerOid);
    		Calendar cal = Calendar.getInstance();
    		cal.add(Calendar.MILLISECOND, (int)Math.floor(hours * 3600 * 1000));
    		
    		info.setProperty("pvpTimer", cal.getTimeInMillis());
    		
    		Engine.getPersistenceManager().setDirty(info);
    		
    		return false;
    	}
    }
    
    /**
     * PerceptionHook processes PerceptionMessages, which incorporate lists
     * of perceived objects gained and lost by the target object.
     */
    class PerceptionHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            PerceptionMessage perceptionMessage = (PerceptionMessage)msg;
            OID perceiverOid = perceptionMessage.getTarget();
            List<PerceptionMessage.ObjectNote> gain = perceptionMessage.getGainObjects();
            List<PerceptionMessage.ObjectNote> lost = perceptionMessage.getLostObjects();
            List<PerceptionMessage.ObjectNote> sGain = perceptionMessage.getStealthedGainObjects();
			List<PerceptionMessage.ObjectNote> sLost = perceptionMessage.getStealthedLostObjects();
            if (Log.loggingDebug)
                Log.debug("FactionPlugin.PerceptionHook.processMessage: perceiverOid " + perceiverOid + " " +
                		((gain==null)?0:gain.size()) + " gain and " +
                        ((lost==null)?0:lost.size()) + " lost and " +
                        ((sGain==null)?0:sGain.size()) + " sGgain and " +
                        ((sLost==null)?0:sLost.size()) + " sLost " 
                        );
            if(Log.loggingDebug) {	
            	Log.debug("FactionPlugin.PerceptionHook.processMessage: gain=" + (gain == null ? "" : gain+""));
            	Log.debug("FactionPlugin.PerceptionHook.processMessage: lost=" + (lost == null ? "" : lost+""));
            	Log.debug("FactionPlugin.PerceptionHook.processMessage: sGain=" + (sGain == null ? "" : sGain+""));
            	Log.debug("FactionPlugin.PerceptionHook.processMessage: sLost=" + (sLost == null ? "" : sLost+""));
            }
              if (gain != null) {
                for (PerceptionMessage.ObjectNote note : gain)
                    processNote(perceiverOid, note, true);
            }
            if (lost != null) {
                for (PerceptionMessage.ObjectNote note : lost)
                    processNote(perceiverOid, note, false);
            }
			if (sGain != null) {
				for (PerceptionMessage.ObjectNote note : sGain) {
					OID perceivedOid = note.getSubject();
					FactionStateInfo subjectInfo = getFactionStateInfo(perceiverOid);
					FactionStateInfo targetInfo = getFactionStateInfo(perceivedOid);
					if(subjectInfo.isPlayer()) {
						if(Log.loggingDebug) {
							Log.debug("FactionPlugin.PerceptionHook.processMessage: subjectOid="+perceiverOid+" objectsTargetType contain subject "+objectsTargetType.containsKey(perceiverOid));
							if(objectsTargetType.containsKey(perceiverOid)) {
								Log.debug("FactionPlugin.PerceptionHook.processMessage: subjectOid="+perceiverOid+" objectsTargetType contain target "+objectsTargetType.get(perceiverOid).containsKey(perceivedOid));
							}
						}
						if (objectsTargetType.getOrDefault(perceiverOid, Collections.emptyMap()).containsKey(perceivedOid)) {
							Log.debug("FactionPlugin.PerceptionHook.processMessage: send update");
							sendTargetUpdate(perceiverOid, perceivedOid, subjectInfo.isPlayer());
						}else {
							Log.debug("FactionPlugin.PerceptionHook.processMessage: calculate and send update");
							processNote(perceiverOid, note, true);
						}
					}
					if(targetInfo.isPlayer()) {
                        if(Log.loggingDebug) {
    						Log.debug("FactionPlugin.PerceptionHook.processMessage: subjectOid="+perceivedOid+" objectsTargetType contain subject "+objectsTargetType.containsKey(perceivedOid));
    						if(objectsTargetType.containsKey(perceivedOid)) {
    							Log.debug("FactionPlugin.PerceptionHook.processMessage: subjectOid="+perceivedOid+" objectsTargetType contain target "+objectsTargetType.get(perceivedOid).containsKey(perceiverOid));
    						}
                        }
						if (objectsTargetType.getOrDefault(perceivedOid, Collections.emptyMap()).containsKey(perceiverOid)) {
							Log.debug("FactionPlugin.PerceptionHook.processMessage: send update");
							sendTargetUpdate(perceivedOid, perceiverOid, targetInfo.isPlayer());
						}else {
							Log.debug("FactionPlugin.PerceptionHook.processMessage: calculate and send update");
							processNote(perceiverOid, note, true);
						}
					}
					
					//sendTargetUpdate(perceiverOid, perceivedOid, subjectInfo.isPlayer());
					//sendTargetUpdate(perceivedOid, perceiverOid, targetInfo.isPlayer());
				}
			}
			 Log.debug("FactionPlugin.PerceptionHook.processMessage: END");
            return true;
        }
        
        protected void processNote(OID perceiverOid, PerceptionMessage.ObjectNote note, boolean add) {
            OID perceivedOid = note.getSubject();
            if (add) {
            	if(Log.loggingDebug) Log.debug("FACTION: calculating states between perceived nodes: " + perceiverOid + " - " + perceivedOid+"  objectsTargetType has? "+objectsTargetType.containsKey(perceiverOid)+"; objectsStance has? "+objectsStance.containsKey(perceiverOid)+
            			";  objectsInRange has? "+objectsInRange.containsKey(perceiverOid));
				calculateInteractionState(perceiverOid, perceivedOid);
            	calculateInteractionState(perceivedOid, perceiverOid);
            	//addObjectInRange(perceiverOid, perceivedOid); - I don't think this is needed?
            } else {
            	AtomicBoolean wasPerceived = new AtomicBoolean();
    			objectsInRange.computeIfPresent(perceiverOid, (k, v) -> {
    			    if (v.remove(perceivedOid)) {
                        wasPerceived.set(true);
    			    }
    			    return v;
    			});
                objectsInRange.computeIfPresent(perceivedOid, (k, v) -> {
                    if (v.remove(perceiverOid)) {
                        wasPerceived.set(true);
                    }
                    return v;
                });
            		
				objectsTargetType.remove(perceiverOid);
				objectsStance.remove(perceiverOid);

				if (wasPerceived.get()) {
            		if(Log.loggingDebug) 	Log.debug("FACTION: removing states between perceived nodes: " + perceiverOid + " - " + perceivedOid);
            	/*	FactionStateInfo subjectInfo = getFactionStateInfo(perceiverOid);
            		FactionStateInfo targetInfo = getFactionStateInfo(perceivedOid);
            		if (subjectInfo == null || targetInfo == null) {
            			Log.debug("FACTION: subject or target faction was null");
            			return;
            		}
        		
            		// The instanceOid check is to make sure the objects have loaded in fully.
            		OID instanceOid = null;
            		if (subjectInfo.getProperty("instanceOid") != null) {
            			instanceOid = (OID) subjectInfo.getProperty("instanceOid");
            		} else if (targetInfo.getProperty("instanceOid") != null) {
            			instanceOid = (OID) targetInfo.getProperty("instanceOid");
            		}
            	*/
            		//if (instanceOid != null) {
						addToTargetTypeLists(perceiverOid, perceivedOid, Deleted);
						addToTargetTypeLists(perceivedOid, perceiverOid, Deleted);
            			//sendTargetUpdate(perceiverOid, perceivedOid, Neither, Neutral, subjectInfo.isPlayer(), instanceOid);
            			//sendTargetUpdate(perceivedOid, perceiverOid, Neither, Neutral, targetInfo.isPlayer(), instanceOid);
            		//}
            	}
            }
            //}
        }
    }
    
    /**
     * Handles the NotifyReactionRadiusMessage. Works out the interaction states between the two objects 
     * that are now in range of each other. This is needed for mobs to resolve their interaction states
     * as sometimes the mobs aren't ready to be processed when the PerceptionHook runs.
     * @author Andrew
     *
     */
    class ResolveInteractionStateHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		ObjectTracker.NotifyReactionRadiusMessage nMsg = (ObjectTracker.NotifyReactionRadiusMessage)msg;
    	    OID subjectOid = nMsg.getSubject();
    	    OID targetOid = nMsg.getTarget();
    	    if(Log.loggingDebug) Log.debug("FACTION: get Attitude caught: " + nMsg);
			if (nMsg.getInRadius()) {
				if (objectsInRange.getOrDefault(subjectOid, Collections.emptySet()).contains(targetOid)) {
					// No need to re-calculate
					return true;
				}
				if (objectsInRange.getOrDefault(targetOid, Collections.emptySet()).contains(subjectOid)) {
					// No need to re-calculate
					return true;
				}
				calculateInteractionState(subjectOid, targetOid);
				calculateInteractionState(targetOid, subjectOid);
			} else {
				if(Log.loggingDebug) 	Log.debug("FACTION: target: " + targetOid + " is no longer in radius of " + subjectOid);
	    		objectsInRange.computeIfPresent(subjectOid, (k, v) -> {
	    		    v.remove(targetOid);
	    		    return v;
	    		});
                objectsInRange.computeIfPresent(targetOid, (k, v) -> {
                    v.remove(subjectOid);
                    return v;
                });
			}
			Log.debug("FACTION: get Attitude completed");
    	    return true;
    	}
    }
    
    class PropertyHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			PropertyMessage propMsg = (PropertyMessage) msg;
			OID objOid = propMsg.getSubject();
			Integer faction = (Integer)propMsg.getProperty(FactionStateInfo.FACTION_PROP);
			String temporaryFaction = (String)propMsg.getProperty(FactionStateInfo.TEMPORARY_FACTION_PROP);
			if (faction != null || temporaryFaction != null) {
			//	if(Log.loggingDebug)
			//		Log.debug("PROPERTY: got faction property update for subject: " + objOid + " who has objectsInRange: " + objectsInRange.get(objOid));
				// There has been a faction change, we need to re-check the interaction state
				// between the subject and all objects in their range.
                ArrayList<OID> objectsToRecalculate = new ArrayList<OID>(objectsInRange.getOrDefault(objOid, Collections.emptySet()));
    			for (OID target : objectsToRecalculate) {
    				calculateInteractionState(objOid, target);
    				calculateInteractionState(target, objOid);
    			}
				return true;
            }
			
			Boolean dead = (Boolean) propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
			if (dead != null && dead) {
				if(Log.loggingDebug)Log.debug("AOE: got dead mob: " + objOid);
				FactionStateInfo playerInfo = getFactionStateInfo(objOid);
				playerInfo.isDead(true);
				Engine.getPersistenceManager().setDirty(playerInfo);
				
				//System.out.println("Faction: subject "+objOid+" is dead");
				
				// Go through and remove dead characters from attackable and healable lists
                ArrayList<OID> objectsToRecalculate = new ArrayList<OID>();
                objectsToRecalculate.addAll(objectsInRange.getOrDefault(objOid, Collections.emptySet()));
				if (!objectsToRecalculate.isEmpty()) {
					if(Log.loggingDebug) Log.debug("AOE: dead mob is in objectsInRange");
	    			for (OID target : objectsToRecalculate) {
	    				//Log.debug("AOE: trying to send target update to target: " + target);
	    				FactionStateInfo targetInfo = getFactionStateInfo(target);
	    				if (targetInfo == null) 
	    					continue;
	    		    	//if (targetInfo.isPlayer())
	    		    	//	Log.debug("AOE: trying to send target update to a player");
	    		    	// Don't send the target update if both are players, as we need to know who is friendly for revive spells
	    		    	if (targetInfo.isPlayer() && playerInfo.isPlayer())
	    		    		continue;
	    		    	
	    		    	OID instanceOid = (OID)playerInfo.getProperty("instanceOid");
	    				sendTargetUpdate(objOid, target, Neither, Neutral, playerInfo.isPlayer(), instanceOid);
	    				sendTargetUpdate(target, objOid, Neither, Neutral, targetInfo.isPlayer(), instanceOid);
	    				// Remove the target
	    				if (!playerInfo.isPlayer()) {
	    				    objectsInRange.computeIfPresent(target, (__, old) -> {
	    				        old.remove(objOid);
	    				        return old; 
	    				    });
	    				}
	    			}
				}

				objectsTargetType.remove(objOid);
				objectsStance.remove(objOid);
				return true;
			} else if (dead != null && !dead) {
				// Not sure if this is needed
				if(Log.loggingDebug)Log.debug("AOE: got alive mob: " + objOid);
				FactionStateInfo playerInfo = getFactionStateInfo(objOid);
				playerInfo.isDead(false);
				Engine.getPersistenceManager().setDirty(playerInfo);
    			for (OID target : objectsInRange.getOrDefault(objOid, Collections.emptySet())) {
    				calculateInteractionState(objOid, target);
    				calculateInteractionState(target, objOid);
    			}
				return true;
			}
            return true;
        }
    }
    
    class SetPvPHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			ExtensionMessage cimsg = (ExtensionMessage)msg;
			OID playerOid = cimsg.getSubject();
			if(Log.loggingDebug) Log.debug("PVP: got setPvP Message for "+ playerOid.toString()+" pvpState="+cimsg.getProperty("pvpState"));
			
			FactionStateInfo factionInfo = getFactionStateInfo(playerOid);
			if(factionInfo==null) {
				log.warn("Try load FactionStateInfo form Database");
				Entity obj =  Engine.getDatabase().loadEntity(playerOid, Namespace.FACTION);
				if(obj!=null) {
					factionInfo = (FactionStateInfo)obj;
				}
			}
			if(factionInfo==null){
				log.warn("PVP: not found FactionStateInfo for player "+playerOid);
				return true;
			}
			boolean pvpState = (Boolean) cimsg.getProperty("pvpState");
			if (pvpState) {
				factionInfo.setProperty(FactionStateInfo.TEMPORARY_FACTION_PROP, "pvp_" + playerOid.toString());
			} else {
				factionInfo.setProperty(FactionStateInfo.TEMPORARY_FACTION_PROP, "");
			}
			// Set as dirty so the FactionStateInfo will be saved
			Engine.getPersistenceManager().setDirty(factionInfo);
			
			// Broadcast the property
			PropertyMessage propMessage = new PropertyMessage(playerOid);
			propMessage.setProperty(FactionStateInfo.TEMPORARY_FACTION_PROP, factionInfo.getProperty(FactionStateInfo.TEMPORARY_FACTION_PROP));
			Engine.getAgent().sendBroadcast(propMessage);
			
			return true;
		}
    }
    
    class AlterReputationHook implements Hook {
    	public boolean processMessage(Message msg, int flags) {
    		FactionClient.AlterReputationMessage nMsg = (FactionClient.AlterReputationMessage)msg;
    	    OID subjectOid = nMsg.getSubject();
    	    int factionID = nMsg.getFaction();
    	    int repChange = nMsg.getRepChange();
    		if(Log.loggingDebug)	log.debug("FACTION: AlterReputation caught: " + nMsg);
			// Get the Player Faction Data for this faction
			FactionStateInfo subjectInfo = getFactionStateInfo(subjectOid);
    		HashMap<Integer, PlayerFactionData> pfdMap = (HashMap) subjectInfo.getProperty("factionData");
    		Faction f = Agis.FactionManager.get(factionID);
	    	if (!pfdMap.containsKey(factionID)) {
    			// Player has not yet met this faction
	    		if(Log.loggingDebug)	log.debug("FACTION: player " + subjectOid + " has not met faction " + factionID);
				pfdMap.put(factionID, Faction.addFactionToPlayer(subjectOid, f, (Integer) subjectInfo.getProperty(FactionStateInfo.FACTION_PROP), pfdMap));
			}
	    	
    		PlayerFactionData pfd = pfdMap.get(factionID);
    		pfd.updateReputation(repChange);
    		pfdMap.put(factionID, pfd);
    		if(Log.loggingDebug)log.debug("FACTION: set reputation of faction: " + factionID + " to: " + pfd.getReputation());
    		// Set as dirty so the FactionStateInfo will be saved
    		Engine.getPersistenceManager().setDirty(subjectInfo);
			
			EventMessageHelper.SendReputationChangedEvent(subjectOid, EventMessageHelper.REPUTATION_CHANGED, factionID, pfd.getName(), pfd.getReputation() + " " + repChange);
		/*	if (objectsInRange != null && objectsInRange.containsKey(subjectOid)) {
				ArrayList<OID> objectsToRecalculate = new ArrayList<OID>();
				objectsToRecalculate.addAll(objectsInRange.get(subjectOid));
				for (OID target : objectsToRecalculate) {
					WorldManagerClient.updateObject(target, subjectOid);
				}
			}
			*/
			WorldManagerClient.TargetedPropertyMessage propMessage = new WorldManagerClient.TargetedPropertyMessage(subjectOid, subjectOid);
			String key = "Reputation" + factionID;
			String value = factionID + " " + f.getName() + " " + pfd.getReputation();
			propMessage.setProperty(key, value);
			Engine.getAgent().sendBroadcast(propMessage);
			
		//	removeObjectInRange(subjectOid, despawnedMsg.getInstanceOid());
        	AgisMobClient.DialogCheck(subjectOid);
        	// WorldManagerClient.updateObject(subjectOid, subjectOid);

        //	sendTargetUpdate(subjectOid, targetOid, targetType, standing, subjectInfo.isPlayer(), instanceOid);
    		
    		// Finally add the pairing to objects in range
    	//	addObjectInRange(subjectOid, targetOid);

			//Log.debug("FACTION: get Attitude completed");
    	    return true;
    	}
    }
    
    class GetObjectsStanceHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			getStanceMessage message = (getStanceMessage) msg;
			OID plyOid = message.getSubject();
			LinkedList<OID> targetOids = message.getTargetOids();
			HashMap<OID,Integer> map = new HashMap<OID,Integer>();
			if (Log.loggingDebug)
				log.debug("FactionPlugin.GetObjectsStanceHook: obj="+plyOid+" targets="+targetOids);
			if (Log.loggingDebug)
				log.debug("FactionPlugin.GetObjectsStanceHook: obj="+plyOid+" objectsStance has obj :"+objectsStance.containsKey(plyOid));
			if (!objectsStance.containsKey(plyOid)) {
				if (Log.loggingDebug)
					log.debug("FactionPlugin.GetObjectsStanceHook: obj="+plyOid+" targets="+targetOids.size());
					for(OID target: targetOids) {
						if (Log.loggingTrace)log.trace("FactionPlugin.GetObjectsStanceHook: obj="+plyOid+" target="+target+" run calculateInteractionState");
						calculateInteractionState(plyOid,target);
					}
				}
			if (Log.loggingDebug)
				log.debug("FactionPlugin.GetObjectsStanceHook: obj="+plyOid+" || objectsStance has obj :"+objectsStance.containsKey(plyOid));
			
			if (objectsStance.containsKey(plyOid)) {
				/*if (Log.loggingDebug)
						log.debug("FactionPlugin.GetObjectsStanceHook: obj="+plyOid+" objectsStance for obj :"+objectsStance.get(plyOid));*/
				for (OID targetOid : targetOids) {
				/*	if (objectsStance.get(plyOid).containsKey(targetOid)) {
						map.put(targetOid, objectsStance.get(plyOid).get(targetOid));
					}*/
					FactionStateInfo plyFsi = null;
					int count = 0;
					while (plyFsi == null && count < 1000000) {
						plyFsi = getFactionStateInfo(plyOid);
						count++;
					}

				//	if (Log.loggingTrace)
					if (Log.loggingDebug)
						log.debug("GetObjectsStanceHook: ----------------------------------- count" + count + " plyFsi:" + plyFsi);
					int plyFaction = 0;
					if (plyFsi != null) {
						plyFaction = (Integer) plyFsi.getProperty(FactionStateInfo.FACTION_PROP);
					} else {
						log.debug("GetObjectsStanceHook: plyFsi is null");
					}
					FactionStateInfo mobFsi = getFactionStateInfo(targetOid);
					int mobFaction = 0;
					if (mobFsi != null) {
						mobFaction = (Integer) mobFsi.getProperty(FactionStateInfo.FACTION_PROP);
					} else {
						log.debug("GetObjectsStanceHook: mobFsi is null");
					}
					if (Log.loggingTrace)
						log.trace("GetObjectsStanceHook:  --Start-- ");

					int subjectFaction = -1;
					int targetFaction = -1;
					try {
						subjectFaction = (Integer) plyFsi.getProperty(FactionStateInfo.FACTION_PROP);
					} catch (NullPointerException e) {
						log.debug("GetObjectsStanceHook FACTION: subject faction was null");
						//	Engine.getAgent().sendIntegerResponse(message, Unknown);
							continue;
					}
					try {
						targetFaction = (Integer) mobFsi.getProperty(FactionStateInfo.FACTION_PROP);
					} catch (NullPointerException e) {
						log.debug("GetObjectsStanceHook FACTION:  target faction was null");
					//	Engine.getAgent().sendIntegerResponse(message, Unknown);
						continue;
					}

					// The instanceOid check is to make sure the objects have loaded in fully.
					OID instanceOid = null;
					if (plyFsi.isPlayer() && plyFsi.getProperty("instanceOid") != null) {
						instanceOid = (OID) plyFsi.getProperty("instanceOid");
					} else if (mobFsi.isPlayer() && mobFsi.getProperty("instanceOid") != null) {
						instanceOid = (OID) mobFsi.getProperty("instanceOid");
					} else {
						if ( plyFsi.getProperty("instanceOid") != null) {
							instanceOid = (OID) plyFsi.getProperty("instanceOid");
						} else if ( mobFsi.getProperty("instanceOid") != null) {
							instanceOid = (OID) mobFsi.getProperty("instanceOid");
						}
					}

					if (instanceOid == null) {
						log.error("GetObjectsStanceHook FACTION: no instanceOid found");
					//	Engine.getAgent().sendIntegerResponse(message, Unknown);
						continue;
					}

					// Log.debug("FACTION: got subjectFaction: " + subjectFaction + " and targetFaction: " + targetFaction);
					String subjectTempFaction = plyFsi.getStringProperty(FactionStateInfo.TEMPORARY_FACTION_PROP); 
					// (String) EnginePlugin.getObjectProperty(subjectOid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP);
					String targetTempFaction = mobFsi.getStringProperty(FactionStateInfo.TEMPORARY_FACTION_PROP); 
					// (String) EnginePlugin.getObjectProperty(targetOid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP);
					// First, how will the target react to the subject - Friendly: 1, neutral: 0, or aggressive: -1 (likely to attack if the subject gets close)
					int reaction = Neutral;
					// Check for temporary factions
					if (Log.loggingTrace)
						log.trace("GetObjectsStanceHook: FACTION: " + subjectTempFaction + " = " + targetTempFaction);
					if (subjectTempFaction != null && !subjectTempFaction.equals("") && targetTempFaction != null && !targetTempFaction.equals("")) {
						if (subjectTempFaction.equals(targetTempFaction)) {
							if (Log.loggingTrace)
								log.trace("GetObjectsStanceHook FACTION: " + plyFsi.getName() + " ( " + plyFsi.getOid() + " ) is friendly with " + mobFsi.getName() + " ( " + mobFsi.getOid()
									+ " ) ");
							reaction = Friendly;
						} else {
							if (Log.loggingTrace)
								log.trace("GetObjectsStanceHook FACTION: " + plyFsi.getName() + " ( " + plyFsi.getOid() + " ) is enemies with " + mobFsi.getName() + " ( " + mobFsi.getOid()
									+ " ) ");
							reaction = Hated;
						}
					} else {
						if (Log.loggingTrace)
							log.trace("GetObjectsStanceHook:   determineFactionStanding");
						reaction = determineFactionStanding(plyOid, plyFsi, targetOid, mobFsi);
					}
					//if (Log.loggingTrace)
					if (Log.loggingDebug)
							log.debug("GetObjectsStanceHook:  subjectOid:" + plyOid + ",subjectInfo.isPlayer:" + plyFsi.isPlayer() + " targetOid:" + targetOid + " reaction:" + reaction);
					
					map.put(targetOid, reaction);
					
					
					
				}

			}
				
				
				
				
			if (Log.loggingDebug)
				log.debug("FactionPlugin.GetObjectsStanceHook: obj="+plyOid+" map="+map);
			Engine.getAgent().sendObjectResponse(message, map);
			return true;
		}
	}
    
	class GetObjectStanceHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			getStanceMessage message = (getStanceMessage) msg;
			OID plyOid = message.getSubject();
			OID targetOid = message.getTargetOid();
			int faction = message.getFaction();
			if (Log.loggingTrace)
				log.trace("GetObjectStanceHook: Start plyOid:" + plyOid + " targetOid:" + targetOid + " faction:" + faction + "   --------------------------------------");
			if (targetOid != null) {
				FactionStateInfo plyFsi = null;
			//	int count = 0;
			//	while (plyFsi == null && count < 100000) {
					plyFsi = getFactionStateInfo(plyOid);
			//		count++;
			//	}
			//	if(99999 < count) {
				if (Log.loggingInfo)
					log.info("not get stance for "+plyOid+" loginTime="+logLogin.get(plyOid)+"; logoutTime="+logLogout.get(plyOid)+"; spawnTime="+logSpawn.get(plyOid)+"; despawnTime="+logDespawn.get(plyOid)+" currentTime="+System.nanoTime());
			//	}
				if(plyFsi==null) {
					log.warn("Try load FactionStateInfo form Database");
					 Entity obj =  Engine.getDatabase().loadEntity(plyOid, Namespace.FACTION);
					 if(obj!=null) {
						 plyFsi = (FactionStateInfo)obj;
					 }
				}
				
				if (plyFsi == null) {
					log.error("GetObjectStanceHook FACTION: subject is null"); 
					Engine.getAgent().sendIntegerResponse(message, Unknown);
					return true;
				}
				int plyFaction = 0;
				if (plyFsi != null) {
					plyFaction = (Integer) plyFsi.getProperty(FactionStateInfo.FACTION_PROP);
				} else {
					log.debug("GetObjectStanceHook: plyFsi is null");
				}
				FactionStateInfo mobFsi = getFactionStateInfo(targetOid);
				if(mobFsi==null) {
					 Entity obj =  Engine.getDatabase().loadEntity(targetOid, Namespace.FACTION);
					 if(obj!=null) {
						 mobFsi = (FactionStateInfo)obj;
					 }
				}
				int mobFaction = 0;
				if (mobFsi != null) {
					mobFaction = (Integer) mobFsi.getProperty(FactionStateInfo.FACTION_PROP);
				} else {
					log.debug("GetObjectStanceHook: mobFsi is null");
				}
				if (Log.loggingTrace)
					log.trace("GetObjectStanceHook:  --Start-- ");

				int subjectFaction = -1;
				int targetFaction = -1;
				try {
					subjectFaction = (Integer) plyFsi.getProperty(FactionStateInfo.FACTION_PROP);
					targetFaction = (Integer) mobFsi.getProperty(FactionStateInfo.FACTION_PROP);
				} catch (NullPointerException e) {
					log.error("GetObjectStanceHook FACTION: subject or target faction was null plyFaction="+subjectFaction+" targetFaction="+targetFaction+" plyOid="+plyOid+" targetOid="+targetOid);
					Engine.getAgent().sendIntegerResponse(message, Unknown);
					return false;
				}

				// The instanceOid check is to make sure the objects have loaded in fully.
				OID instanceOid = null;
				if (plyFsi.isPlayer() && plyFsi.getProperty("instanceOid") != null) {
					instanceOid = (OID) plyFsi.getProperty("instanceOid");
				} else if (mobFsi.isPlayer() && mobFsi.getProperty("instanceOid") != null) {
					instanceOid = (OID) mobFsi.getProperty("instanceOid");
				} else {
				/*	ObjectStub subjectStub = (ObjectStub) EntityManager.getEntityByNamespace(plyOid, Namespace.MOB);
					if (subjectStub != null && subjectStub.getWorldNode() != null) {
						instanceOid = subjectStub.getInstanceOid();
					} else {
						ObjectStub targetStub = (ObjectStub) EntityManager.getEntityByNamespace(targetOid, Namespace.MOB);
						if (targetStub != null && targetStub.getWorldNode() != null) {
							instanceOid = targetStub.getInstanceOid();
						}
					}*/
					
					if ( plyFsi.getProperty("instanceOid") != null) {
						instanceOid = (OID) plyFsi.getProperty("instanceOid");
					} else if ( mobFsi.getProperty("instanceOid") != null) {
						instanceOid = (OID) mobFsi.getProperty("instanceOid");
					}
				}

				if (instanceOid == null) {
					log.debug("GetObjectStanceHook FACTION: no instanceOid found");
					Engine.getAgent().sendIntegerResponse(message, Unknown);
					return false;
				}

				// Log.debug("FACTION: got subjectFaction: " + subjectFaction + " and
				// targetFaction: " + targetFaction);
				String subjectTempFaction = plyFsi.getStringProperty(FactionStateInfo.TEMPORARY_FACTION_PROP); // (String) EnginePlugin.getObjectProperty(subjectOid, Namespace.FACTION,
																												// FactionStateInfo.TEMPORARY_FACTION_PROP);
				String targetTempFaction = mobFsi.getStringProperty(FactionStateInfo.TEMPORARY_FACTION_PROP); // (String) EnginePlugin.getObjectProperty(targetOid, Namespace.FACTION,
																												// FactionStateInfo.TEMPORARY_FACTION_PROP);
				// First, how will the target react to the subject - Friendly: 1, neutral: 0,
				// or aggressive: -1 (likely to attack if the subject gets close)
				int reaction = Neutral;
				// Check for temporary factions
				if (Log.loggingTrace)
					log.trace("GetObjectStanceHook: FACTION: " + subjectTempFaction + " = " + targetTempFaction);
				if (subjectTempFaction != null && !subjectTempFaction.equals("") && targetTempFaction != null && !targetTempFaction.equals("")) {
					if (subjectTempFaction.equals(targetTempFaction)) {
						if (Log.loggingTrace)
							log.trace("GetObjectStanceHook FACTION: " + plyFsi.getName() + " ( " + plyFsi.getOid() + " ) is friendly with " + mobFsi.getName() + " ( " + mobFsi.getOid()
								+ " ) ");
						reaction = Friendly;
					} else {
						if (Log.loggingTrace)
							log.trace("GetObjectStanceHook FACTION: " + plyFsi.getName() + " ( " + plyFsi.getOid() + " ) is enemies with " + mobFsi.getName() + " ( " + mobFsi.getOid()
								+ " ) ");
						reaction = Hated;
					}
				} else {
					if (Log.loggingTrace)
						log.trace("GetObjectStanceHook:   determineFactionStanding");
					reaction = determineFactionStanding(plyOid, plyFsi, targetOid, mobFsi);
				}
				if (Log.loggingTrace)
					log.trace("GetObjectStanceHook:  subjectOid:" + plyOid + ",subjectInfo.isPlayer:" + plyFsi.isPlayer() + " targetOid:" + targetOid + " reaction:" + reaction);
				if (Log.loggingTrace)
					log.trace("GetObjectStanceHook:  --End-- ");

				/*
				 * Faction playerFaction = Agis.FactionManager.get(plyFaction); int
				 * plyReputation = 0; if (playerFaction != null) { plyReputation =
				 * playerFaction.getDefaultReputation(mobFaction); } else {
				 * log.error("GetObjectStanceHook: playerFaction is null"); } int plyRep =
				 * calculateStanding(plyReputation);
				 */
				// log.error("GetObjectStanceHook: end plyFaction:" + plyFaction + "
				// mobFaction:" + mobFaction + " plyReputation:" + plyReputation + " plyRep:" +
				// plyRep);
				// Engine.getAgent().sendIntegerResponse(message, plyRep);
				Engine.getAgent().sendIntegerResponse(message, reaction);
				ArrayList<OID> objectsToRecalculate = new ArrayList<OID>(objectsInRange.getOrDefault(plyOid, Collections.emptySet()));
				for (OID target : objectsToRecalculate) {
					calculateInteractionState(plyOid, target);
					//WorldManagerClient.updateObject(plyOid, target);
				}
			} else {
				FactionStateInfo fsi = getFactionStateInfo(plyOid);
				int subjectFaction = 0;
				if (fsi != null) {
					subjectFaction = (Integer) fsi.getProperty(FactionStateInfo.FACTION_PROP);
				} else {
					log.debug("GetObjectStanceHook: fsi is null");
				}
				Faction newFaction = Agis.FactionManager.get(subjectFaction);
				int reputation = 0;
				if (newFaction != null) {
					reputation = newFaction.getDefaultReputation(faction);
				} else {
					log.debug("GetObjectStanceHook: newFaction is null");
				}
				int rep = calculateStanding(reputation);
				if (Log.loggingTrace)
					log.trace("GetObjectStanceHook: end plyFaction:" + subjectFaction + " plyReputation:" + reputation + " plyRep:" + rep + " ");
//New
				Faction nFaction = Agis.FactionManager.get(faction);
				if (nFaction==null) {
					log.debug("GetObjectStanceHook: nFaction is null");
				}else if (!newFaction.getIsPublic()) {
	    			rep=  calculateStanding(nFaction.getDefaultReputation(subjectFaction));
	    			if (Log.loggingTrace)
	    				log.trace("GetObjectStanceHook: !newFaction.getIsPublic() rep:"+rep);
	    			}
				if (fsi != null) {
					// We get the players faction data
					HashMap<Integer, PlayerFactionData> pfdMap = (HashMap) fsi.getProperty("factionData");
					if (!pfdMap.containsKey(faction)) {
						// Player has not yet met this faction
						if (Log.loggingTrace)
							log.trace("GetObjectStanceHook: FACTION: player " + plyOid + " has not met faction " + faction);
						pfdMap.put(faction, Faction.addFactionToPlayer(plyOid, newFaction, subjectFaction, pfdMap));
						Engine.getPersistenceManager().setDirty(fsi);
					}
					// Log.debug("FACTION: getting target faction: " + targetFaction + " from players FactionDataMap");
					PlayerFactionData pfd = pfdMap.get(faction);
					 Log.debug("FACTION: got faction from players FactionDataMap");
					reputation = pfd.getReputation();
				}
				if (Log.loggingTrace)
					log.trace("GetObjectStanceHook: FACTION: players reputation with faction in question: " + reputation);
				rep = calculateStanding(reputation);

				if (Log.loggingTrace)
					log.trace("GetObjectStanceHook: --end--  plyRep:" + rep );

				Engine.getAgent().sendIntegerResponse(message, rep);
			}
			return true;
		}
	}

    /**
     * Calculates the interaction state between two objects. Both how the target will react to the subject and
     * what the subject can do to the target are calculated with the results being sent out for other
     * areas of the server to use.
     * 
     * Smoo Online uses the following flow to determine interaction states:
     * 1: Temporary factions - if both subject and target have temp factions then if they are the same, they are friendly,
     * if different - hated
     * 2: Group ID - if both users have a group ID and they are the same, they are instantly friendly
     * 3: Dome ID - if both users have a dome ID then they are hated
     * 4: Else - set to null
     */
    private void calculateInteractionState(OID subjectOid, OID targetOid) {
    //	long start = System.nanoTime();//FIXME ZBDS
    	int subjectFaction = -1;
		int targetFaction = -1;
		FactionStateInfo subjectInfo = getFactionStateInfo(subjectOid);
		FactionStateInfo targetInfo = getFactionStateInfo(targetOid);
		if (subjectInfo == null || targetInfo == null) {
			Log.debug("FACTION: subject or target faction was null");
			return;
		}
		try {
			subjectFaction = (Integer) subjectInfo.getProperty(FactionStateInfo.FACTION_PROP);
			targetFaction = (Integer) targetInfo.getProperty(FactionStateInfo.FACTION_PROP);
		} catch (NullPointerException e) {
			Log.debug("FACTION: subject or target faction was null |");
			return;
		}
		if (subjectFaction == -1 || targetFaction == -1) {
			Log.debug("FACTION: subject or target faction was -1");
			return;
		}
		if ((subjectInfo.isDead() && !subjectInfo.isPlayer()) || (targetInfo.isDead() && !targetInfo.isPlayer())) {
			Log.debug("FACTION: subject is dead? " + subjectInfo.isDead() + " or target is dead? " + targetInfo.isDead());
			if(targetInfo.isDead()) {
			//	System.out.println("Faction: subject "+subjectOid+" is dead");
				
			}
			if(subjectInfo.isDead()) {
			//	System.out.println("Faction: target "+targetOid+" is dead");
			}
			return;
		}
		
		// The instanceOid check is to make sure the objects have loaded in fully.
		OID instanceOid = null;
		if(Log.loggingDebug)
			log.debug("FACTION: subject="+subjectOid+" subjectInfo="+subjectInfo+" instanceOid="+subjectInfo.getProperty("instanceOid")+" target="+targetOid+" targetInfo="+targetInfo+" instanceOid="+targetInfo.getProperty("instanceOid"));
		if (subjectInfo.isPlayer() && subjectInfo.getProperty("instanceOid") != null) {
			instanceOid = (OID) subjectInfo.getProperty("instanceOid");
		} else if (targetInfo.isPlayer() && targetInfo.getProperty("instanceOid") != null) {
			instanceOid = (OID) targetInfo.getProperty("instanceOid");
		} else {
		/*	ObjectStub subjectStub = (ObjectStub) EntityManager.getEntityByNamespace(subjectOid, Namespace.MOB);
			log.debug("FACTION: subjectStub="+subjectStub);
			try {
				log.debug("FACTION: subjectStub="+subjectStub.getWorldNode());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
				if (subjectStub != null && subjectStub.getWorldNode() != null) {
				instanceOid = subjectStub.getInstanceOid();
			} 
			else 
			{
				ObjectStub targetStub = (ObjectStub) EntityManager.getEntityByNamespace(targetOid, Namespace.MOB);
				log.debug("FACTION: targetStub="+targetStub);
				try {
					log.debug("FACTION: targetStub="+targetStub.getWorldNode());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if (targetStub != null&& targetStub.getWorldNode() != null) {
					instanceOid = targetStub.getInstanceOid();
				}
			} */
			if ( subjectInfo.getProperty("instanceOid") != null) {
				instanceOid = (OID) subjectInfo.getProperty("instanceOid");
			} else if ( targetInfo.getProperty("instanceOid") != null) {
				instanceOid = (OID) targetInfo.getProperty("instanceOid");
			}
		}
		
		if (instanceOid == null) {
			Log.debug("FACTION: no instanceOid found");
			return;
		}
		
		//Log.debug("FACTION: got subjectFaction: " + subjectFaction + " and targetFaction: " + targetFaction);
		String subjectTempFaction = subjectInfo.getStringProperty(FactionStateInfo.TEMPORARY_FACTION_PROP); //(String) EnginePlugin.getObjectProperty(subjectOid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP);
    	String targetTempFaction = targetInfo.getStringProperty(FactionStateInfo.TEMPORARY_FACTION_PROP); //(String) EnginePlugin.getObjectProperty(targetOid, Namespace.FACTION, FactionStateInfo.TEMPORARY_FACTION_PROP);
    	// First, how will the target react to the subject - Friendly: 1, neutral: 0, 
		// or aggressive: -1 (likely to attack if the subject gets close)
    	int reaction = Neutral;
		// Check for temporary factions
    	if(Log.loggingDebug)log.debug("FACTION: Temporary Factions: subject=" + subjectTempFaction + " ; target=" + targetTempFaction);
    	if (subjectTempFaction != null && !subjectTempFaction.equals("") 
    			&& targetTempFaction != null && !targetTempFaction.equals("")) {
    		if (subjectTempFaction.equals(targetTempFaction)) {
    			if(Log.loggingDebug)	log.debug("FACTION: " + subjectInfo.getName() + " ( "+subjectInfo.getOid()+" ) is friendly with " + targetInfo.getName() + " ( "+targetInfo.getOid()+" ) ");
    			reaction = Friendly;
    		} else {
    			if(Log.loggingDebug)	log.debug("FACTION: " + subjectInfo.getName() + " ( "+subjectInfo.getOid()+" ) is enemies with " + targetInfo.getName()+ " ( "+targetInfo.getOid()+" ) ");
    			reaction = Hated;
    		}
    	} else {
    		reaction = determineFactionStanding(subjectOid, subjectInfo, targetOid, targetInfo);
    	}
    	// Send reaction message to the subject so they know what to expect from the target
    	if(Log.loggingDebug)	log.debug("calculateInteractionState:  subjectOid:"+subjectOid+",subjectInfo.isPlayer:"+subjectInfo.isPlayer()+" targetOid:"+targetOid+" reaction:"+reaction );
    	
    /*	if (subjectInfo.isPlayer())
    		sendTargetReaction(subjectOid, targetOid, reaction);
    	*/
		// Now what can the subject do to the target.
		int standing = -1;
		boolean needsFactionCheck = true;
		boolean canBeHealed = true;
		// If the target is in a duel/arena etc. then we can't heal them unless we are in the same temp faction
		if (targetTempFaction != null && !targetTempFaction.equals("")) {
			if (subjectTempFaction != null && !subjectTempFaction.equals("")) {
				if (targetTempFaction.equals(subjectTempFaction)) {
					standing = Friendly;
					if(Log.loggingDebug)	log.debug("FACTION: " + subjectInfo.getName()+ " ( "+subjectInfo.getOid()+" )  cannot attack " + targetInfo.getName() + " ( "+targetInfo.getOid()+" ) ");
					needsFactionCheck = false;
				} else {
					standing = Hated;
					if(Log.loggingDebug)	log.debug("FACTION: " + subjectInfo.getName() + " ( "+subjectInfo.getOid()+" )  can attack " + targetInfo.getName() + " ( "+targetInfo.getOid()+" ) ");
					needsFactionCheck = false;
				}
			}
			canBeHealed = false;
		}
		if (needsFactionCheck) {
			standing = determineFactionStanding(subjectOid, subjectInfo, targetOid, targetInfo);
		}
		int targetType = Neither;
		if(Log.loggingDebug)log.debug("calculateInteractionState:  subjectOid:"+subjectOid+", targetOid:"+targetOid+" standing:"+standing +" canBeHealed:"+canBeHealed);
		if (!canBeHealed && standing == Friendly) {
			targetType = Neither;
		} else if (standing > Neutral) {
			targetType = Healable; // Healable
		} else { /*if (standing != Neutral)*/
			targetType = Attackable; // Attackable 
		}
		// Send target type message
	//	sendTargetUpdate(subjectOid, targetOid, targetType, standing, subjectInfo.isPlayer(), instanceOid);
		sendTargetUpdate(subjectOid, targetOid, targetType, standing, subjectInfo.isPlayer(), instanceOid, true, reaction);
		
		// Finally add the pairing to objects in range
		addObjectInRange(subjectOid, targetOid);
	//	long end = System.nanoTime();
    //	log.error("FACTION: calculateInteractionState Time "+start+" "+end+" "+(end-start)+" ns");
    }
    
    /**
     * Sends a targeted property message to the matching client letting the user know how
     * a target is likely to react to them.
     * @param subjectOid
     * @param targetOid
     * @param reaction
     */
    private void sendTargetReaction(OID subjectOid, OID targetOid, int reaction) {
    	TargetedPropertyMessage propMsg = new TargetedPropertyMessage(subjectOid, targetOid);
        propMsg.setProperty("reaction", reaction);
        Engine.getAgent().sendBroadcast(propMsg);
        //Log.debug("ATTITUDE: setting reaction for target: " + targetOid + " against: " + subjectOid + " to " + reaction);
    }
    
    /**
     * Send notification massage of the reaction and target type to the player after leaving stealth mode  
     * @param subjectOid
     * @param targetOid
     * @param subjectIsPlayer
     */
    
	private void sendTargetUpdate(OID subjectOid, OID targetOid, boolean subjectIsPlayer) {
		Log.debug("sendTargetUpdate: subjectOid="+subjectOid+" targetOid="+targetOid+" subjectIsPlayer="+subjectIsPlayer);
		if (subjectIsPlayer) {
			boolean send = false;
			TargetedPropertyMessage propMsg = new TargetedPropertyMessage(subjectOid, targetOid);
			if (Log.loggingDebug) {
			    Log.debug("sendTargetUpdate: subjectOid="+subjectOid+" objectsTargetType contain subject "+objectsTargetType.containsKey(subjectOid));
	            if(objectsTargetType.containsKey(subjectOid))
	                Log.debug("sendTargetUpdate: subjectOid="+subjectOid+" objectsTargetType contain target "+objectsTargetType.get(subjectOid).containsKey(targetOid));
			}
			Integer targetType = objectsTargetType.getOrDefault(subjectOid, Collections.emptyMap()).get(targetOid); 
			if (targetType != null) {
				propMsg.setProperty("targetType", targetType);
				send = true;
			}
            if (Log.loggingDebug) {
    			Log.debug("sendTargetUpdate: subjectOid="+subjectOid+" objectsStance="+objectsTargetType.containsKey(subjectOid));
    			if(objectsStance.containsKey(subjectOid))
    				Log.debug("sendTargetUpdate: subjectOid="+subjectOid+" objectsStance contain target "+objectsStance.get(subjectOid).containsKey(targetOid));
            }
			
			Integer reaction = objectsStance.getOrDefault(subjectOid, Collections.emptyMap()).get(targetOid);
			if (reaction != null) {
				propMsg.setProperty("reaction", reaction);
				send = true;
			}
			if (send)
				Engine.getAgent().sendBroadcast(propMsg);
		}else {
			Log.debug("sendTargetUpdate: subjectOid="+subjectOid+" not player");
			
		}
		Log.debug("sendTargetUpdate: END");
	}
    
    /**
     * Collects details about the target and sends out the target update message.
     * @param subjectOid
     * @param targetOid
     * @param targetType
     */
	
    private void sendTargetUpdate(OID subjectOid, OID targetOid, int targetType, int standing, boolean subjectIsPlayer, OID instanceOid) {
    	sendTargetUpdate(subjectOid, targetOid, targetType, standing, subjectIsPlayer, instanceOid, false, 0);
    }
    
	private void sendTargetUpdate(OID subjectOid, OID targetOid, int targetType, int standing, boolean subjectIsPlayer, OID instanceOid, boolean send_reaction, int reaction) {
		// CombatClient.setTargetType(subjectOid, targetOid, targetType, "");
	//	if (targetType != Neither) {
			// Only add attackable or healable targets
			addToTargetTypeLists(subjectOid, targetOid, targetType);
			if(standing == Deleted){
				return;
			}
	//	}
	//	if (subjectIsPlayer) {
		boolean send = false;
		TargetedPropertyMessage propMsg = new TargetedPropertyMessage(subjectOid, targetOid);
        AtomicBoolean targetTypeChanged = new AtomicBoolean();
        if (targetOid == null) {
            targetTypeChanged.set(true);
        } else {
            objectsTargetType.computeIfAbsent(subjectOid, __ -> new ConcurrentHashMap<>()).compute(targetOid, (k, v) -> {
                if (v == null || v.intValue() != targetType) {
                    targetTypeChanged.set(true);
                }
                return targetType;
            });
            
        }
        if (targetTypeChanged.get()) {
            if (subjectIsPlayer) {
                propMsg.setProperty("targetType", targetType);
                send = true;
            }
        }
		if (send_reaction) {
            AtomicBoolean reactionChanged = new AtomicBoolean();
            if (targetOid == null) {
                reactionChanged.set(true);
            } else {
    			objectsStance.computeIfAbsent(subjectOid, __ -> new ConcurrentHashMap<>()).compute(targetOid, (k, v) -> {
    			    if (v == null || v.intValue() != reaction) {
    			        reactionChanged.set(true);
    			    }
    			    return reaction;
    			});
            }

			if (reactionChanged.get()) {
                if (subjectIsPlayer) {
                    propMsg.setProperty("reaction", reaction);
                    send = true;
                }
			}			
		}
		if (send)
			Engine.getAgent().sendBroadcast(propMsg);
		//}
		if(Log.loggingDebug)Log.debug("ATTITUDE: sent target type update for subject: " + subjectOid + " who is player? " + subjectIsPlayer + " and has standing: " + standing + " towards target: " + targetOid);
		if (!subjectIsPlayer) {
		    int aggroRadius = -1;
			if (standing < Neutral) {
				if(Log.loggingDebug)Log.debug("FACTION: adding aggro radius of : " + subjectOid + " for " + targetOid);
				//MobManagerPlugin.getTracker(instanceOid).addAggroRadius(subjectOid, targetOid, AGGRO_RADIUS);
				FactionStateInfo info = getFactionStateInfo(subjectOid);
				if(info!=null) {
					Log.debug("FACTION: sendTargetUpdate FactionStateInfo is null set default AGGRO_RADIUS=" + AGGRO_RADIUS);
					aggroRadius = info.getAggroRadius();
				} else {
				    aggroRadius = AGGRO_RADIUS;
				}
			}
            if(Log.loggingDebug)Log.debug("FACTION: removing aggro radius of : " + subjectOid + " for " + targetOid);
            //MobManagerPlugin.getTracker(instanceOid).removeAggroRadius(subjectOid, targetOid);
            setAggroRadius(subjectOid, targetOid, instanceOid, aggroRadius);
		}
	}

    private void setAggroRadius(OID subjectOid, OID targetOid, OID instanceOid, int aggroRadius) {
        AggroRadiusCacheKey key = new AggroRadiusCacheKey(instanceOid, subjectOid, targetOid);
        Integer cached = aggroRadiusCache.getIfPresent(key);
        if (cached == null || cached != aggroRadius) {
            aggroRadiusCache.put(key, aggroRadius);
            MobManagerClient.setAggroRadius(instanceOid, subjectOid, targetOid, aggroRadius);
        } else {
            Prometheus.registry().counter("duplicate_set_aggro_radius").increment();
        }
    }

    /**
     * Returns the faction standing between the targets faction and the subject.
     * @param subjectOid
     * @param targetOid
     * @return
     */
    private int determineFactionStanding(OID subjectOid, FactionStateInfo subjectInfo, OID targetOid, FactionStateInfo targetInfo) {
    	// Firstly - if the factions are the same, return friendly
    	int subjectFaction = (Integer) subjectInfo.getProperty(FactionStateInfo.FACTION_PROP);
    	int targetFaction = (Integer) targetInfo.getProperty(FactionStateInfo.FACTION_PROP);
    	String subjectFaction_temp = (String) subjectInfo.getProperty(FactionStateInfo.TEMPORARY_FACTION_PROP);
    	String targetFaction_temp = (String) targetInfo.getProperty(FactionStateInfo.TEMPORARY_FACTION_PROP);
    // TODO: remove the < 1 check
    	if(Log.loggingDebug)	log.debug("determineFactionStanding: subjectOid:"+subjectOid+" subjectFaction="+subjectFaction+ " subjectFaction_temp="+subjectFaction_temp+
    			" | targetOid:"+targetOid+" targetFaction="+targetFaction+" targetFaction_temp="+targetFaction_temp);
    	if(Log.loggingDebug) log.debug("determineFactionStanding subjectOid"+subjectOid+" "+subjectInfo.isPet()+" targetOid:"+targetOid+" "+targetInfo.isPet());
    	if ((subjectFaction == targetFaction && (subjectFaction_temp==null || subjectFaction_temp.equals("")) && (targetFaction_temp==null || targetFaction_temp.equals(""))) 
    	 || ( subjectFaction == targetFaction && subjectFaction_temp!=null && !subjectFaction_temp.equals("") && targetFaction_temp!=null && !targetFaction_temp.equals("")
    	 	&& subjectFaction_temp.equals(targetFaction_temp)) 
        			|| subjectFaction < 1 || targetFaction < 1)
    		return Friendly;
    	
    	//Log.debug("FACTI: subjectIsPlayer: " + subjectInfo.isPlayer() + ", subjectFaction = " + subjectFaction + ", target = " + targetFaction);
    	if (subjectInfo.isPlayer() /*|| subjectInfo.isPet()*/) {
    		Faction newFaction = Agis.FactionManager.get(targetFaction);
    		if (!newFaction.getIsPublic()) {
    			return calculateStanding(newFaction.getDefaultReputation(subjectFaction));
    		}
    		// We get the players faction data
    		HashMap<Integer, PlayerFactionData> pfdMap = (HashMap) subjectInfo.getProperty("factionData");
    		if (!pfdMap.containsKey(targetFaction)) {
    			// Player has not yet met this faction
    			if(Log.loggingDebug) Log.debug("FACTION: player " + subjectOid + " has not met faction " + targetFaction);
    			pfdMap.put(targetFaction, Faction.addFactionToPlayer(subjectOid, newFaction, subjectFaction, pfdMap));
    			Engine.getPersistenceManager().setDirty(subjectInfo);
    		}
    		//Log.debug("FACTION: getting target faction: " + targetFaction + " from players FactionDataMap");
    		PlayerFactionData pfd = pfdMap.get(targetFaction);
    		//Log.debug("FACTION: got faction from players FactionDataMap");
    		int reputation = pfd.getReputation();
    		if(Log.loggingDebug) Log.debug("FACTION: players reputation with faction in question: " + reputation);
			return calculateStanding(reputation);
    	} else if (targetInfo.isPlayer()/* || targetInfo.isPet()*/) {
    		Faction newFaction = Agis.FactionManager.get(subjectFaction);
    		if (!newFaction.getIsPublic()) {
    			return calculateStanding(newFaction.getDefaultReputation(targetFaction));
    		}
    		// We get the players faction data
    		HashMap<Integer, PlayerFactionData> pfdMap = (HashMap) targetInfo.getProperty("factionData");
    		if (!pfdMap.containsKey(subjectFaction)) {
    			// Player has not yet met this faction
    			if(Log.loggingDebug) Log.debug("FACTION: player " + subjectOid + " has not met faction " + subjectFaction);
    			pfdMap.put(subjectFaction, Faction.addFactionToPlayer(targetOid, newFaction, targetFaction, pfdMap));
    			Engine.getPersistenceManager().setDirty(targetInfo);
    		}
    		//Log.debug("FACTION: getting subject faction: " + subjectFaction + " from players FactionDataMap");
    		PlayerFactionData pfd = pfdMap.get(subjectFaction);
    		//Log.debug("FACTION: got faction from players FactionDataMap");
    		int reputation = pfd.getReputation();
    		if(Log.loggingDebug) Log.debug("FACTION: players reputation with faction in question: " + reputation);
			return calculateStanding(reputation);
    	} else {
    		Faction newFaction = Agis.FactionManager.get(subjectFaction);
    		int reputation = newFaction.getDefaultReputation(targetFaction);
    		if(Log.loggingDebug) log.debug("determineFactionStanding:  subjectOid:"+subjectOid+" reputation="+reputation + " targetOid:"+targetOid);
    		return calculateStanding(reputation);
    	}
    }
    
    /**
     * Adds an object in range to the objects in range Map.
     * @param subject
     * @param target
     */
    private void addObjectInRange(OID subject, OID target) {
     // 	log.error("FactionPlugion: addObjectInRange subject="+subject+" target="+target);
        objectsInRange.computeIfAbsent(subject, __ -> ConcurrentHashMap.newKeySet()).add(target);
    }
    
    /**
     * Removes an object from objectsInRange map.
     * @param subject
     * @param instanceOid
     */
    private void removeObjectInRange(OID subject, OID instanceOid) {
     //	log.error("FactionPlugion: removeObjectInRange subject="+subject+" instanceOid="+instanceOid);
		for (OID target : objectsInRange.getOrDefault(subject, Collections.emptySet())) {
		    objectsInRange.computeIfPresent(target, (k, v) -> {
		        v.remove(subject);
                //sendTargetUpdate(target, subject, Deleted, Deleted, true, instanceOid);
				addToTargetTypeLists(target, subject, Deleted);
		        return v;
		    });
		}
		objectsInRange.remove(subject);
    }
    
    /**
     * Adding Stance between subject ant target to list to distribute to plugins
     * @param subjectOid
     * @param targetOid
     * @param targetType
     */
	protected void addToTargetTypeLists(OID subjectOid, OID targetOid, int targetType) {
	    log.debug("FactionPlugion: addToTargetTypeLists subjectOid="+subjectOid+" targetOid="+targetOid+" targetType="+targetType);
		targetTypes.computeIfAbsent(new TargetTypeKey(subjectOid, targetOid), __ -> new TargetType(subjectOid, targetOid, targetType)).setTargetType(targetType);
	}
	
    /**
     * TargetTypesTick is Scheduler to distribute stance to plugins 
     *
     */
	class TargetTypesTick implements Runnable {
		public void run() {
			log.debug("FactionPlugion: TargetTypesTick subjectOids.size="+_targetTypes.size());
			LinkedList<TargetType> list = new LinkedList<TargetType>();
			for (Iterator<TargetType> i = targetTypes.values().iterator(); i.hasNext();) {
			    list.add(i.next());
			    i.remove();
			}
			if (Log.loggingDebug)
				Log.debug("TARGET: sending target Types message with num target types: " + list.size() + " " + list);
			if (!list.isEmpty()) {
			    CombatClient.setTargetType(list);
			}
		}
	}
    
    public static int calculateStanding(int reputation) {
    	if (reputation < DislikedRep) {
    		return Hated;
    	}else if (reputation < NeutralRep) {
    		return Disliked;
    	} else if (reputation < FriendlyRep) {
    		return Neutral;
    	} else if (reputation < HonouredRep) {
    		return Friendly;
    	} else if (reputation < ExaltedRep) {
    		return Honoured;
    	} else {
    		return Exalted;
    	}
    }
    
  
    // Use a map to keep track of what has already been sorted so the code won't run again
   // protected HashMap<OID, ArrayList<OID>> objectsInRange = new HashMap<OID, ArrayList<OID>>();
    protected Map<OID, Set<OID>> objectsInRange = new ConcurrentHashMap<>();
    protected Map<OID, Map<OID,Integer>> objectsStance = new ConcurrentHashMap<>();
    protected Map<OID, Map<OID,Integer>> objectsTargetType = new ConcurrentHashMap<>();
    
    protected ConcurrentHashMap<OID,Long> logLogin = new ConcurrentHashMap<OID,Long>();
    protected ConcurrentHashMap<OID,Long> logLogout = new ConcurrentHashMap<OID,Long>();
    protected ConcurrentHashMap<OID,Long> logSpawn = new ConcurrentHashMap<OID,Long>();
    protected ConcurrentHashMap<OID,Long> logDespawn = new ConcurrentHashMap<OID,Long>();
    
    protected Map<TargetTypeKey, TargetType> targetTypes = new ConcurrentHashMap<>();
    protected List<TargetType> _targetTypes = Collections.synchronizedList(new LinkedList<TargetType>()); 
    
    public static int HatedRep = -3000;
    public static int DislikedRep = -1500;
    public static int NeutralRep = 0;
    public static int FriendlyRep = 500;
    public static int HonouredRep = 1500;
    public static int ExaltedRep = 3000;
    public static final int Hated = -2;
    public static final int Disliked = -1;
    public static final int Neutral = 0;
    public static final int Friendly = 1;
    public static final int Honoured = 2;
    public static final int Exalted = 3;
	public static final int Unknown = 999;
	public static final int Deleted = 998;

	public static final int Attackable = -1;
    public static final int Healable = 1;
    public static final int Neither = 0;
    
    public static int AGGRO_RADIUS = 18;
    
    private static class AggroRadiusCacheKey {
        private final OID instanceOid;
        private final OID subjectOid;
        private final OID targetOid;

        public AggroRadiusCacheKey(OID instanceOid, OID subjectOid, OID targetOid) {
            this.instanceOid = instanceOid;
            this.subjectOid = subjectOid;
            this.targetOid = targetOid;
        }

        @Override
        public int hashCode() {
            return Objects.hash(instanceOid, subjectOid, targetOid);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            AggroRadiusCacheKey other = (AggroRadiusCacheKey) obj;
            return Objects.equals(instanceOid, other.instanceOid) && Objects.equals(subjectOid, other.subjectOid)
                    && Objects.equals(targetOid, other.targetOid);
        }

    }

    private Cache<AggroRadiusCacheKey, Integer> aggroRadiusCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(2))
            .maximumSize(500_000).build();
    
}