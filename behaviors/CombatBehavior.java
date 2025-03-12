package atavism.agis.behaviors;

import java.io.Serializable;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

//import com.esotericsoftware.minlog.Log;

import atavism.agis.effects.SleepEffect;
import atavism.agis.effects.StunEffect;
import atavism.msgsys.*;
import atavism.server.objects.*;
import atavism.server.engine.*;
import atavism.server.util.*;
import io.micrometer.core.instrument.Timer;
import atavism.server.plugins.*;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;
import atavism.server.telemetry.Prometheus;
import atavism.server.math.*;
import atavism.server.messages.*;
import atavism.agis.plugins.*;
import atavism.agis.plugins.GroupClient.GroupInfo;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.core.Agis;
import atavism.agis.core.AgisAbility;
import atavism.agis.core.AgisEffect;
import atavism.agis.core.Cooldown;
import atavism.agis.core.Cooldown.State;
import atavism.agis.objects.*;

public class CombatBehavior extends Behavior {
    public CombatBehavior() {
    }

    public CombatBehavior(SpawnData data) {
        super(data);
        String value = (String)data.getProperty("combat.reactionRadius");
        if (value != null) {
            setReactionRadius(Integer.valueOf(value));
        }
        
    }
    
    private void safeScheduleCheckDistance(Runnable cdt, long delay, TimeUnit unit) {
        lock.lock();
        try {
            if (scheduledCheck != null && !scheduledCheck.isDone()) {
                scheduledCheck.cancel(false);
            }
            scheduledCheck = Engine.getExecutor().schedule(cdt, delay, unit);
            Log.debug("CombatBehavior.safeScheduleCheckDistance: Scheduled check in " + delay + " " + unit);
        } finally {
            lock.unlock();
        }
    }

    public void initialize() {
    	  if (Log.loggingDebug)  Log.debug("CombatBehavior.initialize: Start "+obj.getOid());
        SubscriptionManager.get().subscribe(this, obj.getOid(), CombatClient.MSG_TYPE_DAMAGE, PropertyMessage.MSG_TYPE_PROPERTY,
                ObjectTracker.MSG_TYPE_NOTIFY_AGGRO_RADIUS, CombatClient.MSG_TYPE_AUTO_ATTACK_COMPLETED,
                ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS, CombatClient.MSG_TYPE_ALTER_THREAT, AgisMobClient.MSG_TYPE_INVALID_PATH, 
                EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK, Behavior.MSG_TYPE_LINKED_AGGRO, Behavior.MSG_TYPE_EVENT, CombatClient.MSG_TYPE_COMBAT_EVENT);
        if (Log.loggingDebug)  Log.debug("CombatBehavior.initialize: End "+obj.getOid());
    }

        
    protected void subscribeForTarget(OID oid) {
        if (Log.loggingDebug)
            Log.debug("subscribeForTarget: obj="+obj.getOid()+" oid=" + oid);
        if (activated) {
            SubscriptionManager.get().subscribe(this, oid, CombatClient.MSG_TYPE_COMBAT_LOGOUT,CombatClient.MSG_TYPE_DAMAGE,
                    WorldManagerClient.MSG_TYPE_DESPAWNED, EnginePlugin.MSG_TYPE_SET_PROPERTY, PropertyMessage.MSG_TYPE_PROPERTY,
                    EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK);
        }
    }

    protected void unsubscribeForTarget(OID oid) {
        try {
			if (Log.loggingDebug)
			    Log.debug("unsubscribeForTarget: obj="+obj.getOid()+" oid=" + oid);
			if (activated) {
			    SubscriptionManager.get().unsubscribe(this, oid, CombatClient.MSG_TYPE_COMBAT_LOGOUT,CombatClient.MSG_TYPE_DAMAGE,
			            WorldManagerClient.MSG_TYPE_DESPAWNED, EnginePlugin.MSG_TYPE_SET_PROPERTY);
			    if (!oid.equals(obj.getOid())) {
			        SubscriptionManager.get().unsubscribe(this, oid, PropertyMessage.MSG_TYPE_PROPERTY,
			                EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK);
			    }
			}
		} catch (Exception e) {
			Log.exception(e);
		}
        propMap.remove(oid);
    }
    
    /**
     * Called when a mob respawns, clear all behaviour variables here or they will carry over to new spawns
     */
    public void activate() {
        activated = true;
        if (Log.loggingDebug)
            Log.debug("CombatBehavior.activate: adding reaction radius "+obj.getOid()+" instance="+obj.getInstanceOid());
        MobManagerPlugin.getTracker(obj.getInstanceOid()).addReactionRadius(obj.getOid(), reactionRadius);
        threatMap.clear();
        threatMapTemp.clear();
        damageMap.clear();
        propMap.clear();
        items.clear();
        reactionTargets.clear();
        tagOwner = null;
        lastDamage = null;
        evade = false;
        currentTarget = null;
        isDead=false;
        defaultSpeed = (float) CombatClient.getPlayerStatValue(obj.getOid(), AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED);
        hitBoxRange = (Integer)EnginePlugin.getObjectProperty(obj.getOid(), CombatClient.NAMESPACE, CombatPlugin.PROP_HITBOX);
        checkThreatTask = Engine.getExecutor().scheduleAtFixedRate(new CheckThreatUpdated(), 1000L,1000L, TimeUnit.MILLISECONDS);
        
        behaviorSheduler();
        if (Log.loggingDebug) Log.debug("CombatBehavior.activate: adding reaction radius "+obj.getOid()+" END");
        	
    }
    
    
	public void behaviorSheduler() {
		if (behaviorThreatTask != null) {
			if (Log.loggingDebug)
				Log.debug("CombatBehavior.behaviorSheduler remove old behavior task ");
			boolean wyn = behaviorThreatTask.cancel(true);
			if (Log.loggingDebug)
				Log.debug("CombatBehavior.behaviorSheduler behaviorThreatTask remove " + wyn + " for " + obj.getOid());
			behaviorThreatTask = null;
		}
		if(moveThreatTask!=null) {
			boolean wyn = moveThreatTask.cancel(true);
			if (Log.loggingDebug)
				Log.debug("CombatBehavior.behaviorSheduler moveThreatTask remove " + wyn + " for " + obj.getOid());
			moveThreatTask = null;
		}
		selectedBehavior = null;
		if (Log.loggingDebug)
			Log.debug("CombatBehavior.behaviorSheduler behaviorId=" + behaviorId + " behaviors=" + behaviors.size() + " for " + obj.getOid());

		if (Log.loggingDebug)
			Log.debug("CombatBehavior.behaviorSheduler  run Thread Task to select definition of the behavior for " + obj.getOid() + " interval " + AgisMobPlugin.MOB_COMBAT_BEHAVIOR_SELECT_INTERVAL);
		behaviorThreatTask = Engine.getExecutor().scheduleAtFixedRate(new BehaviorSelector(), 1000L, AgisMobPlugin.MOB_COMBAT_BEHAVIOR_SELECT_INTERVAL, TimeUnit.MILLISECONDS);
		moveThreatTask = Engine.getExecutor().scheduleAtFixedRate(new MoveDestinationCheckTask(), 1000L, AgisMobPlugin.MOB_COMBAT_BEHAVIOR_MOVE_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
	}
    
    public void deactivate() {
        if(Log.loggingDebug)Log.debug("CombatBehavior.deactivate: adding reaction radius "+obj.getOid()+" instance="+obj.getInstanceOid());

        lock.lock();
        try {
            activated = false;
            SubscriptionManager.get().unsubscribe(this);
            if (checkThreatTask != null) {
                checkThreatTask.cancel(false);
                checkThreatTask = null;
            }
            if(behaviorThreatTask!=null) {
            	behaviorThreatTask.cancel(false);
            	behaviorThreatTask =null;
            }
            if(moveThreatTask!=null) {
            	moveThreatTask.cancel(false);
            	moveThreatTask =null;
            }
            if(abilitySelectorTask!=null){
                abilitySelectorTask.cancel(false);
                abilitySelectorTask=null;
            }
        }
        finally {
            lock.unlock();
        }
    }

    void sendLinkedAggro(OID target) {
        if(Log.loggingTrace)
            Log.trace("CombatBehavior.sendLinkedAggro obj="+obj.getOid()+" Start " + reactionTargets + " linkedAggroRadius=" + linkedAggroRadius);
        if(Log.loggingDebug)
            Log.debug("CombatBehavior.sendLinkedAggro obj="+obj.getOid()+" Start " + reactionTargets.size()+ " linkedAggroRadius=" + linkedAggroRadius);
        if (sendLinkedAggro) {
            LinkedList<OID> targets = new LinkedList<OID>();
            targets.addAll(reactionTargets);
            
            HashMap<OID, Integer> map = FactionClient.getStance(obj.getOid(), targets);
            if(Log.loggingDebug)
                Log.debug("CombatBehavior.sendLinkedAggro map="+map);
            for (OID oid : map.keySet()) {
                if (map.get(oid) > FactionPlugin.Neutral) {
                    float dist = getDistanceToTarget(oid);
                    if(Log.loggingDebug)
                        Log.debug("CombatBehavior.sendLinkedAggro target="+oid+" distance="+dist);
                    
                    if (dist <= linkedAggroRadius) {
                        Behavior.SendLinkedAggro(oid, target);
                    }
                }
            }
        }
        Log.debug("CombatBehavior.sendLinkedAggro End");
    }
    
    public void handleMessage(Message msg, int flags) {
        
        Prometheus.registry().counter("combat_behav_message", "message", msg.getClass().getSimpleName()).increment();
        if(Log.loggingDebug)
            Log.debug("CombatBehavior.handleMessage: msg="+msg+" Type="+msg.getMsgType());
        //lock.lock();
        if (!activated) {
            return;
        }
        if (msg.getMsgType() == EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK || msg.getMsgType() == EnginePlugin.MSG_TYPE_SET_PROPERTY) {
            EnginePlugin.SetPropertyMessage rMsg = (EnginePlugin.SetPropertyMessage) msg;
            if(Log.loggingDebug)Log.debug("CombatBehavior.handleMessage: rMsg = "+rMsg);
            handleSetProperty(rMsg);
        } else if (msg.getMsgType() == Behavior.MSG_TYPE_LINKED_AGGRO) {
            if(Log.loggingDebug)
                Log.debug("CombatBehavior.handleMessage got "+msg.getMsgType()+" inCombat="+inCombat+" getLinkedAggro="+getLinkedAggro+" sendLinkedAggro="+sendLinkedAggro);
                LinkedAggroMessage message = (LinkedAggroMessage)msg;
        /*  
          if(sendLinkedAggro) {
                sendLinkedAggro( message.getTarget());
            }
        */
            handleLinkedAggro(message); 
        } else if (msg instanceof CombatClient.DamageMessage) {
            CombatClient.DamageMessage dmgMsg = (CombatClient.DamageMessage) msg;
            handleDamage(dmgMsg);
        } else if (msg.getMsgType() == ObjectTracker.MSG_TYPE_NOTIFY_AGGRO_RADIUS) {
            ObjectTracker.NotifyAggroRadiusMessage nMsg = (ObjectTracker.NotifyAggroRadiusMessage) msg;
                // check if target is Immune or a Spirit
            //TODO: Uncomment this - but also do it in a more efficient way
            /*String state = (String)EnginePlugin.getObjectProperty(subjectOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_STATE);
            if (state == null || state.equals(CombatInfo.COMBAT_STATE_IMMUNE) || state.equals(CombatInfo.COMBAT_STATE_SPIRIT)) {
                return;
            }*/
            handleNotifyAggroRadius(nMsg);
        } else if (msg.getMsgType() == ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS) {
            ObjectTracker.NotifyReactionRadiusMessage nMsg = (ObjectTracker.NotifyReactionRadiusMessage) msg;
            handleNotifyReactionRadius(nMsg);
        } else if (msg instanceof PropertyMessage) {
            PropertyMessage propMsg = (PropertyMessage) msg;
            handleProperty(propMsg);
        } else  if (msg.getMsgType() == WorldManagerClient.MSG_TYPE_DESPAWNED) {
            WorldManagerClient.DespawnedMessage despawnedMsg = (WorldManagerClient.DespawnedMessage) msg;
            handleDespawn(despawnedMsg);
        } else  if (msg.getMsgType() == CombatClient.MSG_TYPE_COMBAT_LOGOUT) {
            CombatClient.CombatLogoutMessage clMsg = (CombatClient.CombatLogoutMessage) msg;
            handleLogout(clMsg);
            //attackTarget(null);
        } else  if (msg.getMsgType() == AgisMobClient.MSG_TYPE_INVALID_PATH) {
            //TODO: fill this area in correctly
            handleInvalidPath();
        } else if (msg instanceof CombatClient.AutoAttackCompletedMessage) {
            Log.debug("CombatBehavior.handleMessage: AUTO: got auto attack completed");
        } else  if (msg.getMsgType() == CombatClient.MSG_TYPE_ALTER_THREAT) {
            CombatClient.AlterThreatMessage clMsg = (CombatClient.AlterThreatMessage) msg;
            handleAlterThreat(clMsg);
        } else  if (msg.getMsgType() == EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK) {
            EnginePlugin.SetPropertyMessage rMsg = (EnginePlugin.SetPropertyMessage) msg;
            handleSetPropertyNonBlock(msg, rMsg);

		} else if (msg.getMsgType() == Behavior.MSG_TYPE_EVENT) {
			String event = ((Behavior.EventMessage) msg).getEvent();
			Log.debug("CombatBehavior: handleMessage: event=" + event + " oid=" + obj.getOid());
			if (event.equals(BaseBehavior.MSG_EVENT_TYPE_ARRIVED)) {
				handleArrived();
			} else {

			}
		} else if (msg.getMsgType() == CombatClient.MSG_TYPE_COMBAT_EVENT) {
			
			
		}
        Log.debug("CombatBehavior.handleMessage end");

    }

    private void handleProperty(PropertyMessage propMsg) {
        OID subject = propMsg.getSubject();
        if(Log.loggingDebug)
        for (String s : propMsg.getPropertyMapRef().keySet()) {
            Log.debug("CombatBehavior.handleMessage: Property " + s + "=" + propMsg.getProperty(s) + " for "+subject);
        }
        Boolean dead = (Boolean) propMsg.getProperty(CombatInfo.COMBAT_PROP_DEADSTATE);
        if(Log.loggingDebug)
        	Log.debug("CombatBehavior.handleMessage: Property obj=" +obj+ " " + CombatInfo.COMBAT_PROP_DEADSTATE + " " + dead + " subject=" + subject);
        if(propMsg.containsKey("combatEvent")) {
        	handleCombatEvent(propMsg, subject);
        }else if (dead != null && dead) {
            handleDeath(propMsg, subject);
        } else if (dead != null && !dead && !subject.equals(obj.getOid())) {
            handleRevival(propMsg, subject);
        } else {
            handleStealth(propMsg, subject);
        }
        
        handleMovement(propMsg, subject);
        handleCombatStats(propMsg, subject);
        handlePropState(propMsg);
        handlePropGM(propMsg);
        Log.debug("CombatBehavior.handleMessage: PropertyMessage END");
    }
    
	private void handleCombatEvent(PropertyMessage propMsg, OID subject) {
		TriggerProfile.Type type = (TriggerProfile.Type) propMsg.getProperty("combatEvent");
		boolean reviced = (boolean) propMsg.getProperty("reviced");
		Log.debug("CombatBehavior: handleCombatEvent: combat event=" + type + " reviced=" + reviced + " oid=" + obj.getOid());
		if (reviced) {
			events.computeIfPresent(type, (k, v) -> {
				v = System.currentTimeMillis();
				return v;
			});
			events.computeIfAbsent(type, __ -> System.currentTimeMillis());
		}
	}

    private void handleDeath(PropertyMessage propMsg, OID subject) {
        lock.lock();
        try {
            long tStart = System.nanoTime();
            if(Log.loggingDebug)    Log.debug("CombatBehavior.handleMessage: obj=" + obj + " got death=" + propMsg.getSubject() + " currentTarget=" + currentTarget);
            if (subject.equals(obj.getOid())) {
                handleDeath(lastDamage);
                Log.debug("CombatBehavior.handleMessage: mob died, deactivating all behaviors");
                for (Behavior behav : obj.getBehaviors()) {
                    behav.deactivate();
                    obj.removeBehavior(behav);
                }
            } else {
                if (currentTarget != null && currentTarget.equals(subject)) {
                    Log.debug("CombatBehavior.handleMessage: target died, setting current target to null");
                    currentTarget = null;
                    if(Log.loggingDebug)    Log.debug("CombatBehavior.handleMessage: set current target: " + subject + " to null");
                    // attackTarget(null);
                }
                if (removeTargetFromThreatMap(subject)) {
                    // setTargetThreat(subject, -1); // Set threat to -1 for dead targets
                    TargetedPropertyMessage newPropMsg = new TargetedPropertyMessage(subject, obj.getOid());
                    if(Log.loggingDebug)    Log.debug("CombatBehavior.checkDistance: send aggressive=false to "+subject);
                    newPropMsg.setProperty("aggressive", false);
                    Engine.getAgent().sendBroadcast(newPropMsg);
                    if(Log.loggingDebug)Log.debug("CombatBehavior.handleMessage: removed: " + subject + " from threatmap");
                } else {
                    attackTarget(null);
                }
            }
            io.micrometer.core.instrument.Timer.builder("handle_property").tag("section", "death")
                    .register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tStart));
        } finally {
            lock.unlock();
        }
    }

    private void handleStealth(PropertyMessage propMsg, OID subject) {
        long tStart = System.nanoTime();
        AtomicBoolean update = new AtomicBoolean();
        propMap.compute(subject, (__, _obj) -> {
            if (_obj == null) {
                _obj = new ConcurrentHashMap<>();
            }
            if(CombatPlugin.STEALTH_STAT != null) {
                if(propMsg.containsKey(CombatPlugin.STEALTH_STAT)) {
                    _obj.put(CombatPlugin.STEALTH_STAT, propMsg.getProperty(CombatPlugin.STEALTH_STAT));
                    update.set(true);
                }
            }
            if(CombatPlugin.PERCEPTION_STEALTH_STAT != null) {
                if(propMsg.containsKey(CombatPlugin.PERCEPTION_STEALTH_STAT)) {
                    _obj.put(CombatPlugin.PERCEPTION_STEALTH_STAT, propMsg.getProperty(CombatPlugin.PERCEPTION_STEALTH_STAT));
                    update.set(true);
                }
            }
            for(String key : CombatPlugin.STAT_LIST) {
            	if(propMsg.containsKey(key))
            		_obj.put(key, propMsg.getProperty(key));
            }

            if(propMsg.containsKey(WorldManagerClient.WORLD_PROP_NOTURN)) {
                _obj.put(WorldManagerClient.WORLD_PROP_NOTURN, propMsg.getProperty(WorldManagerClient.WORLD_PROP_NOTURN));
                update.set(true);
            }
            if(propMsg.containsKey(WorldManagerClient.WORLD_PROP_NOMOVE)) {
                _obj.put(WorldManagerClient.WORLD_PROP_NOMOVE, propMsg.getProperty(WorldManagerClient.WORLD_PROP_NOMOVE));
                update.set(true);
            }
            if(Log.loggingDebug)
				Log.trace("CombatBehavior.handleMessage: props = " + _obj + " for oid=" + subject);
            return _obj;
        });
        if(update.get())
            threatUpdated();
        io.micrometer.core.instrument.Timer.builder("handle_property").tag("section", "else")
                .register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tStart));
        
    }

    private void handleRevival(PropertyMessage propMsg, OID subject) {
        long tStart = System.nanoTime();
        if(Log.loggingDebug)Log.debug("CombatBehavior.handleMessage: REVIVE: obj=" + obj + " got life=" + propMsg.getSubject() + " currentTarget=" + currentTarget);
        // Someone just came alive, add to thread map?
        if (threatMap.containsKey(subject)) {
            // Do a distance check to see if they are within aggro range
            if(Log.loggingDebug) {
                Log.debug("CombatBehavior.handleMessage: AGGRO: getting distance from wnode: " + obj.getWorldNode().getLoc() + " and aggro radius: " + aggroRange);
            }
            if (getDistanceToTarget(subject) < aggroRange)
                setTargetThreat(subject, 0);
        }
        io.micrometer.core.instrument.Timer.builder("handle_property").tag("section", "nondeath")
        .register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tStart));
    }

    private void handleMovement(PropertyMessage propMsg, OID subject) {
        // Handle movement speed property change
        if(!propMsg.containsKey(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED)){
            return;
        }
        float movementSpeed =  (Integer) propMsg.getProperty(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED);
        AgisStatDef asd = CombatPlugin.lookupStatDef(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED);
        if(asd.getPrecision()>1)
             movementSpeed = movementSpeed/asd.getPrecision();
        if(Log.loggingDebug)Log.debug("CombatBehavior.handleMessage: PropertyMessage movementSpeed="+movementSpeed);
        if ( subject.equals(obj.getOid())) {
            long tStart = System.nanoTime();
            speed = movementSpeed;
            if(Log.loggingDebug)    Log.debug("CombatBehavior.handleMessage: SPEED: set mob: " + obj.getOid() + " speed to: " + speed);
            io.micrometer.core.instrument.Timer.builder("handle_property").tag("section", "movement")
            .register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tStart));
        }
    }

    private void handleCombatStats(PropertyMessage propMsg, OID subject) {
        if(!propMsg.containsKey(CombatInfo.COMBAT_PROP_COMBATSTATE)){
            return;
        }
        Boolean combatstate = (Boolean) propMsg.getProperty(CombatInfo.COMBAT_PROP_COMBATSTATE);
        if(Log.loggingDebug)Log.debug("CombatBehavior.handleMessage: state="+combatstate);
        if (combatstate != null && !subject.equals(obj.getOid())) {
        	if (Log.loggingDebug)Log.debug("CombatBehavior.handleMessage: state="+combatstate);
            long tStart = System.nanoTime();
            if (combatstate) {
                if (currentTarget != null) {
                    attackTarget(currentTarget);
                }
                Log.debug("CombatBehavior.handleMessage: STATE: got mob state set back to null");
            }else {
                currentTarget = null;
            }
            io.micrometer.core.instrument.Timer.builder("handle_property").tag("section", "attack1")
            .register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tStart));
        }
    }

    private void handlePropState(PropertyMessage propMsg) {
        if(!propMsg.containsKey(CombatInfo.COMBAT_PROP_STATE)){
            return;
        }
        String state = (String) propMsg.getProperty(CombatInfo.COMBAT_PROP_STATE);
        if(Log.loggingDebug)    Log.debug("CombatBehavior.handleMessage: state="+state);
        if ("".equals(state)) {
            long tStart = System.nanoTime();
            if (currentTarget != null) {
                attackTarget(currentTarget);
            }
            Log.debug("CombatBehavior.handleMessage: STATE: got mob state set back to null");
            io.micrometer.core.instrument.Timer.builder("handle_property").tag("section", "attack2")
            .register(Prometheus.registry()).record(Duration.ofNanos(System.nanoTime() - tStart));
        }
    }
    
	private void handlePropGM(PropertyMessage propMsg) {
		OID oid = propMsg.getSubject();
		if (Log.loggingDebug)
			Log.debug("handlePropGM: Set Property " + oid + " " + obj.getOid());
		if (propMsg.getProperty("gm") != null) {
			propMap.compute(oid, (__, _obj) -> {
				if (_obj == null) {
					_obj = new ConcurrentHashMap<>();
				}
				_obj.put("gm", propMsg.getProperty("gm"));
				if (Log.loggingDebug)
					Log.trace("CombatBehavior.handleMessage: props = " + _obj + " for oid=" + oid);
				return _obj;
			});
			threatUpdated();
		}
	}

    private void handleSetPropertyNonBlock(Message msg, EnginePlugin.SetPropertyMessage rMsg) {
        OID oid = rMsg.getSubject();
        if (Log.loggingDebug)
            Log.debug("CombatBehavior.handleMessage: obj.oid = " + obj.getOid() + " oid="+oid+" ; getMsgType=" + msg.getMsgType() + " == EnginePlugin.MSG_TYPE_SET_PROPERTY_NONBLOCK  ");
        if (oid.equals(obj.getOid())) {
            if (rMsg.containsKey(WorldManagerClient.WORLD_PROP_NOMOVE)) {
                //boolean nm = nomove;
                nomove = (Boolean) rMsg.getProperty(WorldManagerClient.WORLD_PROP_NOMOVE);
                if (Log.loggingDebug)
                    Log.debug("CombatBehavior.handleMessage: obj.oid = " + obj.getOid() + " oid="+oid+" ; set nomove=" + nomove );
                if(!nomove) {
                    threatUpdated();
                }
            }
        }
    }

    private void handleAlterThreat(CombatClient.AlterThreatMessage clMsg) {
        addTargetToThreatMap(clMsg.getAttackerOid(), clMsg.getThreatChange());
    }

	private void handleInvalidPath() {
		Prometheus.registry().counter("combat_behavior", "method", "invalid_path").increment();

        threatMapTemp.putAll(threatMap);
        threatMap.clear();
        damageMap.clear();
        tagOwner = null;
        attackTarget(null);
        Prometheus.registry().counter("combat_behavior", "method", "chase_distance").increment();

        CombatClient.sendResetAttacker(obj.getOid());


		// Set evade to true for 10 seconds
		Log.debug("CombatBehavior.handleInvalidPath: Evade set to true 1");
		InterpolatedWorldNode wnode = obj.getWorldNode();
		if (wnode == null) {
			Log.error("CombatBehavior.handleInvalidPath: got null wnode during distance check for oid: " + obj.getOid());
			return;
		}
		inCombat = false;
       if(Log.loggingDebug) Log.debug("CombatBehavior.handleInvalidPath: oid: " + obj.getOid()+" wnode="+wnode);
		Point loc = wnode.getCurrentLoc();
        if (loc == null) {
            Log.error("CombatBehavior.handleInvalidPath: got null loc during distance check for oid: " + obj.getOid());
            return;
        }
		float distance = Point.distanceTo(loc, centerLoc);
		if (distance > 0.5f) {
			evade = true;
        // Schedule checkDistance with a short delay instead of 10 seconds
        safeScheduleCheckDistance(cdt, 5000, TimeUnit.MILLISECONDS);

			Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, centerLoc, speed));
			Log.debug("CombatBehavior.handleInvalidPath: Evade set to true 2");
			CombatClient.setCombatInfoState(obj.getOid(), CombatInfo.COMBAT_STATE_RESET);
		}
	}


    void handleLogout(CombatClient.CombatLogoutMessage clMsg) {
        OID subjectOid = clMsg.getSubject();
        OID playerOid = clMsg.getPlayerOid();
        if(Log.loggingDebug)    Log.debug("CombatBehavior.handleMessage: Logout reaction. Obj: " + obj.getOid() + "; target: " + playerOid + "; subject: " + subjectOid);
        removeTargetFromThreatMap(playerOid);
        if(currentTarget!=null && currentTarget.equals(playerOid)) {
            currentTarget =null;
        }
    }

    void handleDespawn(WorldManagerClient.DespawnedMessage despawnedMsg) {
        OID objOid = despawnedMsg.getSubject();
        if(Log.loggingDebug)Log.debug("CombatBehavior.handleMessage: DESPAWNED: got target despawned: " + objOid);
        removeTargetFromThreatMap(objOid); 
        if(currentTarget!=null && currentTarget.equals(objOid)) {
            currentTarget =null;
        }
    }

    private void handleNotifyReactionRadius(ObjectTracker.NotifyReactionRadiusMessage nMsg) {
        if(Log.loggingDebug)    Log.debug("CombatBehavior.handleMessage: Reaction myOid=" + obj.getOid() + " subject=" + nMsg.getSubject() + " inRadius=" + nMsg.getInRadius());
        // Remove target from threat map if they are no longer in radius
        if (nMsg.getInRadius()) {
            reactionTargets.add(nMsg.getSubject());
            
        } else {
            reactionTargets.remove(nMsg.getSubject());
            removeTargetFromThreatMap(nMsg.getSubject());
            threatMapTemp.remove(nMsg.getSubject());
        }
    }

    private void handleNotifyAggroRadius(ObjectTracker.NotifyAggroRadiusMessage nMsg) {
        if(Log.loggingDebug)Log.debug("CombatBehavior.handleMessage: got in aggro range message=" + nMsg);
        OID subjectOid = nMsg.getSubject();
        if (evade) {
            Log.debug("CombatBehavior.handleMessage: evade state is true");
            threatMapTemp.compute(subjectOid, (key, oldValue) -> {
                return 0 ; 
            }); 
            return;
        }
        // Check if target is dead
        if (!CombatPlugin.isPlayerAlive(subjectOid)) {
            Log.debug("CombatBehavior.handleMessage: target died aggro radius");
            setTargetThreat(subjectOid, -10);
        } else {
            Log.debug("CombatBehavior.handleMessage: adding target to threat map");
            Integer threat = threatMap.get(subjectOid);
            if (threat == null) {
                addTargetToThreatMap(subjectOid, 0);
            } else if (threat < 0) {
                setTargetThreat(subjectOid, 0);
            }
        }
        Log.debug("CombatBehavior.handleMessage: got in aggro range end");
    }

	private void handleDamage(CombatClient.DamageMessage dmgMsg) {

		if (obj.getOid().equals(dmgMsg.getAttackerOid())) {
			Log.debug("CombatBehavior.handleDamage: the received damage message is ignored because the damage is self-inflicted");
			return;
		}
		if (Log.loggingDebug)
			Log.debug("CombatBehavior.handleDamage: handleDamage: obj="+obj.getOid()+" attacker: " + dmgMsg.getAttackerOid() + " attack " + dmgMsg.getTargetOid() + " " + subscribed+" "+subscribed.contains(dmgMsg.getTargetOid()));

		if (subscribed.contains(dmgMsg.getTargetOid())) {
			if (Log.loggingDebug)
				Log.debug("CombatBehavior.handleDamage: handleDamage: obj="+obj.getOid()+" attacker: " + dmgMsg.getAttackerOid() + " attack " + dmgMsg.getTargetOid() + " " + subscribed);

		} else {
			if (obj.getOid().equals(dmgMsg.getSubject())) {
				//Get self damage
				lastDamage = dmgMsg.getAttackerOid();
				if (tagOwner == null && dmgMsg.getAttackerOid() != null) {
					if (Log.loggingDebug)
						Log.debug("CombatBehavior.handleDamage: TAG: setting tagOwner to: " + dmgMsg.getAttackerOid());
					// tagOwner should only be a players character - check if it is a mob or not
					ObjectStub objStub = null;
					Serializable petOwner = null;
					try {
						objStub = (ObjectStub) EntityManager.getEntityByNamespace(dmgMsg.getAttackerOid(), Namespace.MOB);
						petOwner = EnginePlugin.getObjectProperty(dmgMsg.getAttackerOid(), WorldManagerClient.NAMESPACE, "petOwner");
					} catch (Exception e) {
					}

					if (objStub != null && objStub.getTemplateID() == -1 || petOwner != null) {
						// The players templateID is -1
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.handleDamage: TAG: setting set tagOwner to player: " + dmgMsg.getAttackerOid());
						tagOwner = dmgMsg.getAttackerOid();
					}
				}
				addTargetToThreatMap(dmgMsg.getAttackerOid(), dmgMsg.getThreat());
				if (AgisMobPlugin.EXP_BASED_ON_DAMAGE_DEALT || AgisMobPlugin.LOOT_BASED_ON_DAMAGE_DEALT) {
					addDamageToMap(dmgMsg.getAttackerOid(), dmgMsg.getDmg());
				}
			} else {
				// Damage not self
				Serializable petOwner = null;
				try {
					petOwner = EnginePlugin.getObjectProperty(obj.getOid(), WorldManagerClient.NAMESPACE, "petOwner");
				} catch (Exception e) {
				}
				if (petOwner != null ) {
					OID petOwnerOid = (OID)petOwner;
					if (Log.loggingDebug)
						Log.debug("CombatBehavior.handleDamage: self petOwner is player: " + petOwnerOid);
					if(petOwnerOid.equals(dmgMsg.getSubject())) {
						addTargetToThreatMap(dmgMsg.getAttackerOid(), dmgMsg.getThreat());
					}
				}else {
					if (Log.loggingDebug)
						Log.debug("CombatBehavior.handleDamage:  obj is not pet of " + dmgMsg.getSubject());
					
				}
				
			}
		}
	}

    private void handleLinkedAggro(LinkedAggroMessage message) {
        if(inCombat) {
            return;
        }
        if(!getLinkedAggro)
            return;
        if (message.getTarget() != null) {
            if(Log.loggingDebug)Log.debug("CombatBehavior.handleMessage threatMap="+threatMap);
            Integer threat = threatMap.get(message.getTarget());
            if (threat == null) {
                addTargetToThreatMap(message.getTarget(), 0);
            } else if (threat < 0) {
                setTargetThreat(message.getTarget(), 0);
            }
            attackTarget(message.getTarget());
        }
    }

    private void handleSetProperty(EnginePlugin.SetPropertyMessage rMsg) {
    	
        OID oid = rMsg.getSubject();
        if (Log.loggingDebug)Log.debug("Set Property "+oid+" "+obj.getOid());
        propMap.compute(oid, (__, _obj) -> {
            if (_obj == null) {
                _obj = new ConcurrentHashMap<>();
            }
            for(String key : CombatPlugin.STAT_LIST) {
            	if(rMsg.getPropMap().containsKey(key))
            		_obj.put(key, rMsg.getProperty(key));
            }
            if(Log.loggingDebug)
                Log.trace("CombatBehavior.handleMessage: props = "+_obj+" for oid="+oid);
            return _obj;
        });
        //threatUpdated();
    }

    
	private void findAggroTarget() {
		if (Log.loggingDebug)
			Log.debug("findAggroTarget: Start");
		InterpolatedWorldNode wn = obj.getWorldNode();
		if (wn != null) {
			Point mloc = wn.getCurrentLoc();
			if (Log.loggingDebug)
				Log.debug("findTarget: targets=" + threatMapTemp.size());
			for (OID playerOid : threatMapTemp.keySet()) {
				float dist = getDistanceToTarget(playerOid);
				if (Log.loggingDebug)
					Log.debug("findTarget: oid=" + playerOid + " dist=" + dist);

				if (dist < aggroRange)
					setTargetThreat(playerOid, 0);
			}
		}
		if (Log.loggingDebug)
			Log.debug("findAggroTarget: End");
	}

    private void handleArrived() {
    	Log.debug("CombatBehavior.handleArrived");
    	
    	if (Log.loggingDebug)Log.debug("CombatBehavior.handleMessage: MSG_EVENT_TYPE_ARRIVED oid="+obj.getOid()+" command="+command+" reloacatingForAbility="+reloacatingForAbility+"; selectedAbilityGroup="+selectedAbilityGroup+";  selectedAbility="+selectedAbility+"; currentTarget="+currentTarget+"");
		if(command == "GoReturn") {
			
			findAggroTarget();
			return;
		}
    	if(command == "Goto" || command == "GoReturn")
		if (selectedBehavior != null) {
			if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: behavior type "+selectedBehavior.behaviorType);
			switch (selectedBehavior.behaviorType) {
			case 0://melee
				if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: melee reloacatingForAbility="+reloacatingForAbility+" selectedAbility="+selectedAbility);
				if (reloacatingForAbility) {
					if (selectedAbility > 0) {
					//	selectedAbilityGroup = null;

						if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Start Ability " + selectedAbility);
						long delay = selectedBehavior.abilityinterval;
						AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
						long castingTime =  calculateCastingTime(aa.getActivationTime());
                        if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                            castingTime += aa.getAttackTime();
                        }

                        if (castingTime > delay)
							delay = castingTime;
						selectedAbilityGroup = null;
						if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Start Ability " + selectedAbility);
//						runAbility(selectedAbility);
						BehaviorLogicTaskSchedule(delay);
                        if(currentTarget!=null) {
                            InterpolatedWorldNode curWnode = obj.getWorldNode();
                            EntityHandle eHandle = new EntityHandle(currentTarget);
                            ObjectStub followObj = (ObjectStub) eHandle.getEntity(Namespace.MOB);
                            if (followObj != null) {
                                if (followObj.getWorldNode() != null) {
                                    BasicWorldNode updateNode = new BasicWorldNode(curWnode);
                                    Point followLoc = followObj.getWorldNode().getLoc();
                                    Point _loc = new Point(0f, 0, 0f);
                                    _loc.add(curWnode.getLoc());
                                    AOVector reqDir = AOVector.sub(followLoc, _loc);
                                    float vYaw = AOVector.getLookAtYaw(reqDir);
                                    if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: Start Ability oid="+obj.getOid()+" vYaw=" + vYaw+" getYaw="+curWnode.getOrientation().getYaw()+" _loc="+_loc+" followLoc="+followLoc);
                                    if(AgisMobPlugin.MOB_DEBUG_PLAYER!=null) {
                                        //Debug
                                        Quaternion casterQuaternion2 = new Quaternion().setEulerAngles(curWnode.getOrientation().getPitch(), vYaw, curWnode.getOrientation().getRoll());
                                        AOVector testLoc1 = new AOVector(curWnode.getLoc());
                                        testLoc1.add(curWnode.getOrientation().getXAxis().multiply(4f));
                                        AOVector testLoc2 = new AOVector(curWnode.getLoc());
                                        testLoc2.add(casterQuaternion2.getZAxis().multiply(4f));
                                        Map<String, Serializable> props = new HashMap<String, Serializable>();
                                        props.put("ext_msg_subtype", "behavior_target");
                                        props.put("locX", curWnode.getLoc().getX());
                                        props.put("locY", curWnode.getLoc().getY());
                                        props.put("locZ", curWnode.getLoc().getZ());
                                        props.put("locX1", testLoc1.getX());
                                        props.put("locY1", testLoc1.getY());
                                        props.put("locZ1", testLoc1.getZ());

                                        props.put("locX2", testLoc2.getX());
                                        props.put("locY2", testLoc2.getY());
                                        props.put("locZ2", testLoc2.getZ());
                                        TargetedExtensionMessage extmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, AgisMobPlugin.MOB_DEBUG_PLAYER, AgisMobPlugin.MOB_DEBUG_PLAYER, false, props);
                                        Engine.getAgent().sendBroadcast(extmsg);
                                        //endDebug
                                    }
                                    Quaternion cQuaternion = obj.getWorldNode().getOrientation();
                                    cQuaternion.setEulerAngles(curWnode.getOrientation().getPitch(), vYaw, curWnode.getOrientation().getRoll());
                                    obj.getWorldNode().setOrientation(cQuaternion);
                                    obj.updateWorldNode();

                                    CombatClient.startAbility(selectedAbility, obj.getOid(), currentTarget, null, false, -1, -1);
                                }
                            }
                        }
						selectedAbility = -1;
					}
				}
				
				
				
				break;
			case 1://ranger offensive
				if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: reanged off reloacatingForAbility="+reloacatingForAbility+" selectedAbility="+selectedAbility);
				if (reloacatingForAbility) {
					if (selectedAbility > 0) {
					//	selectedAbilityGroup = null;

						if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Start Ability " + selectedAbility);
						long delay = selectedBehavior.abilityinterval;
						AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
						long castingTime =  calculateCastingTime(aa.getActivationTime());
                        if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                            castingTime += aa.getAttackTime();
                        }

                        if (castingTime > delay)
							delay = castingTime;
						selectedAbilityGroup = null;
						if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Start Ability " + selectedAbility+" currentTarget="+currentTarget);
						BehaviorLogicTaskSchedule(delay);
						if(currentTarget != null) {
                            InterpolatedWorldNode curWnode = obj.getWorldNode();
                            EntityHandle eHandle = new EntityHandle(currentTarget);
                            ObjectStub followObj = (ObjectStub) eHandle.getEntity(Namespace.MOB);
                            if(followObj!=null) {
                                if (followObj.getWorldNode() != null) {
                                    BasicWorldNode updateNode = new BasicWorldNode(curWnode);
                                    Point followLoc = followObj.getWorldNode().getLoc();
                                    Point _loc = new Point(0f, 0, 0f);
                                    _loc.add(curWnode.getLoc());
                                    AOVector reqDir = AOVector.sub(followLoc, _loc);
                                    float vYaw = AOVector.getLookAtYaw(reqDir);
                                    if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Start Ability oid="+obj.getOid()+" vYaw=" + vYaw+" getYaw="+curWnode.getOrientation().getYaw()+" _loc="+_loc+" followLoc="+followLoc);
                                    if(AgisMobPlugin.MOB_DEBUG_PLAYER!=null) {
                                        //Debug
                                        Quaternion casterQuaternion2 = new Quaternion().setEulerAngles(curWnode.getOrientation().getPitch(), vYaw, curWnode.getOrientation().getRoll());
                                        AOVector testLoc1 = new AOVector(curWnode.getLoc());
                                        testLoc1.add(curWnode.getOrientation().getXAxis().multiply(4f));
                                        AOVector testLoc2 = new AOVector(curWnode.getLoc());
                                        testLoc2.add(casterQuaternion2.getZAxis().multiply(4f));
                                        Map<String, Serializable> props = new HashMap<String, Serializable>();
                                        props.put("ext_msg_subtype", "behavior_target");
                                        props.put("locX", curWnode.getLoc().getX());
                                        props.put("locY", curWnode.getLoc().getY());
                                        props.put("locZ", curWnode.getLoc().getZ());
                                        props.put("locX1", testLoc1.getX());
                                        props.put("locY1", testLoc1.getY());
                                        props.put("locZ1", testLoc1.getZ());

                                        props.put("locX2", testLoc2.getX());
                                        props.put("locY2", testLoc2.getY());
                                        props.put("locZ2", testLoc2.getZ());
                                        TargetedExtensionMessage extmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, AgisMobPlugin.MOB_DEBUG_PLAYER, AgisMobPlugin.MOB_DEBUG_PLAYER, false, props);
                                        Engine.getAgent().sendBroadcast(extmsg);
                                        //endDebug
                                    }
                                    Quaternion cQuaternion = obj.getWorldNode().getOrientation();
                                    cQuaternion.setEulerAngles(curWnode.getOrientation().getPitch(), vYaw, curWnode.getOrientation().getRoll());
                                    obj.getWorldNode().setOrientation(cQuaternion);
                                    obj.updateWorldNode();

                                    CombatClient.startAbility(selectedAbility, obj.getOid(), currentTarget, null, false, -1, -1);
                                }
                            }
                        }
						selectedAbility = -1;
					}
				}
				

				break;
			case 2://ranged defend
				if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: reanged def reloacatingForAbility="+reloacatingForAbility+" selectedAbility="+selectedAbility+" selectedAbilityGroup="+selectedAbilityGroup);
				if (reloacatingForAbility) {
					if (selectedAbility > 0) {
						//selectedAbilityGroup = null;

						if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Start Ability " + selectedAbility);
						long delay = selectedBehavior.abilityinterval;
						AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
						long castingTime =  calculateCastingTime(aa.getActivationTime());
                        if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                            castingTime += aa.getAttackTime();
                        }

                        if (castingTime > delay)
							delay = castingTime;
						
						if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Start Ability " + selectedAbility);
//						runAbility(selectedAbility);
						
						boolean rv = runAbility(selectedAbility);
						if(rv) {
							selectedAbilityGroup = null;
							BehaviorLogicTaskSchedule(delay);
							selectedAbility = -1;
						}
					}else {
						if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived  selectedAbility <1");
					}
				}else {
					if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived reloacatingForAbility is flase");
				}
				if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived ranged def end");
				break;
			case 3://defend
				if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: defend  reloacatingForAbility="+reloacatingForAbility+" selectedAbility="+selectedAbility+" selectedAbilityGroup="+selectedAbilityGroup);
				
				if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived behavior defend  defendedTarget="+defendedTarget+" currentTarget="+currentTarget);
				if (defendedTarget != null && currentTarget == null) {
					InterpolatedWorldNode wn = obj.getWorldNode();
					if (wn != null) {
						Point mloc = wn.getCurrentLoc();
						EntityHandle mHandle = new EntityHandle(defendedTarget);
						ObjectStub tObj = (ObjectStub) mHandle.getEntity(Namespace.MOB);
						boolean dead = false;
						boolean combatFound = false;
						for (Behavior behav : tObj.getBehaviors()) {
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: oid " + defendedTarget + " behav " + behav);
							if (behav instanceof CombatBehavior) {
								CombatBehavior _cb = ((CombatBehavior) behav);
								//List<Integer> tags = _cb.getTags();
								if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: obj="+obj.getOid()+" oid " + defendedTarget + " _cb.isDead="+_cb.isDead);
								dead = _cb.isDead;
								combatFound=true;
							} else {
								if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: oid " + defendedTarget + " no combat behavior ");
							}
						}
						if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived behavior defend  defendedTarget="+defendedTarget+" currentTarget="+currentTarget+" combatBehavior found "+combatFound+" num behav "+tObj.getBehaviors().size());
						if(!combatFound) {
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived behavior defend  defendedTarget="+defendedTarget+" dont have combat behavior num behav "+tObj.getBehaviors().size()+" reset defend target"); 
							defendedTarget = null;
						}else {
						Point tLoc = tObj.getWorldNode().getLoc();
						AOVector dir = tObj.getWorldNode().getDir();
						EntityHandle eHandle = new EntityHandle(defendedTarget);
						float distance = Point.distanceTo(mloc, tLoc);
						if (distance > 2 + eHandle.getHitBox() + hitBoxRange) {
							Point _followLoc = new Point(tLoc);
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: oid = " + obj.getOid() + " _followLoc=" + _followLoc + " ");
							_followLoc.sub(mloc);
							//_followLoc.setY(0);
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: oid = " + obj.getOid() + " _followLoc=" + _followLoc + " sub my loc ");
							float len = (float) Math.sqrt(_followLoc.getX() * _followLoc.getX() + _followLoc.getY() * _followLoc.getY() + _followLoc.getZ() * _followLoc.getZ());
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: oid = " + obj.getOid() + " len=" + len);
							_followLoc.multiply(1 / len);
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * 1/len ");
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: oid = " + obj.getOid() + " _followLoc=" + _followLoc + " distance=" + distance);

							float m = distance - 2 - eHandle.getHitBox() - hitBoxRange;

							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: oid = " + obj.getOid() + "  m= " + m + " hitBoxRange=" + hitBoxRange + " eHandle.getHitBox()=" + eHandle.getHitBox());
							_followLoc.multiply(m);
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * followDist - hitbox ");
							_followLoc.add(mloc);
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: oid = " + obj.getOid() + "; myLoc = " + mloc + "; followLoc=" + tLoc + " _followLoc=" + _followLoc + " followDist=" + distance + " len=" + len
									+ " target hitBox=" + eHandle.getHitBox() + " speed=" + speed + " distance=" + distance);
							// Point destLoc = _followLoc;
							float distance2 = Point.distanceTo(mloc, _followLoc);
							float distance3 = Point.distanceTo(tLoc, _followLoc);
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: Math.abs(distance2 - distance) " + Math.abs(distance2 - distance) + " distance2=" + distance2 + " distance3=" + distance3 + " distance="
									+ distance);

							command = "Goto";
							goreturn = false;

							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: move to position " + _followLoc+" oid="+obj.getOid());
								Point newPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), _followLoc, obj.getInstanceOid());
								if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: move to newPoint " + newPoint);
								
								if (distance > m) {
									if (gotoLoc == null || (Point.distanceTo(gotoLoc, newPoint) > 0.5f && Point.distanceTo(mloc, newPoint) > 0.5f)) {

										gotoLoc = newPoint;
										if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: defendedTarget oid " + defendedTarget + " isMob? " + tObj.getType().isMob());
										float targetSpeed = speed;
										for (Behavior behav : tObj.getBehaviors()) {
											if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: defendedTarget " + defendedTarget + " behav " + behav);
											if (behav instanceof BaseBehavior) {
												BaseBehavior _cb = ((BaseBehavior) behav);
												if(_cb.activated) {
													targetSpeed = _cb.mobSpeed;
												}else {
													targetSpeed = AgisMobClient.sendGetActorSpeed(tObj.getOid(),obj.getInstanceOid());
												}
												if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: defendedTarget " + defendedTarget + " target speed " + _cb.mobSpeed+" targetSpeed="+targetSpeed);

											} else {
												if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: defendedTarget " + defendedTarget + " no Base behavior ");
											}
										}
										if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: defendedTarget " + defendedTarget + " distance2="+distance2+" targetSpeed="+targetSpeed+" st2="+(targetSpeed*1.2f)+" speed="+speed+" tDir lenght="+dir.length());
										
										/*if (distance2 < 2 && !Float.isNaN(targetSpeed)) {
											Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, targetSpeed ));
										} else */if (distance2 < 5 && !Float.isNaN(targetSpeed)) {
											Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, targetSpeed ));
										} else {
											Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, speed));

										}
										
									}
								}
						}
						}
					} else {
					if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: got null wnode during check ability for oid: " + obj.getOid());
				}

			} else if (currentTarget != null) {
						if (Log.loggingDebug)Log.debug("moveDestinationCheck behavior defend  currentTarget=" + currentTarget);
						if (!reloacatingForAbility || selectedAbility < 1) {
							if (selectedAbilityGroup == null) {
								for (AbilityGroup am : selectedBehavior.abilities) {
									if (!am.Calculate()) {
										if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defensive obj=" + obj + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate failed break");

										continue;
									}
									selectedAbilityGroup = am;

									if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defensive obj=" + obj.getOid() + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate OK " + am.ids);

									selectedAbility = am.getAbility();
									if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defensive obj=" + obj.getOid() + " targetOid=" + currentTarget + " Abilityid= " + selectedAbility);
									// selectedAbility = aid;
									long delay = selectedBehavior.abilityinterval;
									AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
									long castingTime =  calculateCastingTime(aa.getActivationTime());
                                    if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                                        castingTime += aa.getAttackTime();
                                    }

                                    if (castingTime > delay)
										delay = castingTime;
									boolean result = runAbility(selectedAbility);
									if (result) {
										BehaviorLogicTaskSchedule(delay);
									} else {
										// BehaviorLogicTaskSchedule( delay);
									}
									break;
								}
							}
						} else if (reloacatingForAbility) {
							if (selectedAbility > 0) {
								// selectedAbilityGroup = null;

								if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Start Ability " + selectedAbility);
								long delay = selectedBehavior.abilityinterval;
								AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
								long castingTime =  calculateCastingTime(aa.getActivationTime());
                                if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                                    castingTime += aa.getAttackTime();
                                }

                                if (castingTime > delay)
									delay = castingTime;

								if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Start Ability " + selectedAbility);
								// runAbility(selectedAbility);

								boolean rv = runAbility(selectedAbility);
								if (rv) {
									selectedAbilityGroup = null;
									BehaviorLogicTaskSchedule(delay);
									selectedAbility = -1;
								}
							} else {
								if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived  selectedAbility <1");
							}
						} else {
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived reloacatingForAbility is flase");
						}
					}
				if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived defend End");
				break;
			case 4://flee
				if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Flee obj="+obj.getOid()+" phase="+phase+" currentTarget="+currentTarget+" ");
					if (phase > 0) {
						// Mob Arrived to destination point
						InterpolatedWorldNode wnode = obj.getWorldNode();
						if (wnode == null) {
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Flee got null wnode during check ability for oid: " + obj.getOid());
							break;
						}
						// get mob location
						Point loc = wnode.getCurrentLoc();
						boolean arrivedDest = false;

						if (gotoLoc != null) {
							float distance = Point.distanceTo(loc, gotoLoc);
							float distance2 = Point.distanceToXZ(loc, gotoLoc);
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Flee obj="+obj.getOid()+" loc="+loc+" gotoLoc="+gotoLoc+" distance="+distance+" distance2="+distance2);
							if (distance < 1) {
								arrivedDest = true;
								gotoLoc = null;
							}
						}
						if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Flee obj="+obj.getOid()+" arrivedDest="+arrivedDest);
						if (arrivedDest) {
							if(currentTarget!=null && sendLinkedAggro)
								sendLinkedAggro(currentTarget);
							
							int selectedability = -1;
							// long delay = selectedBehavior.abilityinterval;
							Random rand = new Random();
							for (AbilityGroup am : selectedBehavior.endAbilities) {
								if (am.Calculate()) {
									selectedability = am.getAbility();
								}

							}
							if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Flee obj="+obj.getOid()+" selectedability="+selectedability);
							if (selectedability > 0) {
								AgisAbility aa = Agis.AbilityManager.get(selectedability);
								if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived flee Start Ability " + selectedAbility);
								//boolean rv = runAbility(selectedAbility);
								//if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Flee obj="+obj.getOid()+" runAbility="+rv);
								 CombatClient.startAbility(selectedability, obj.getOid(), obj.getOid(), null, false, -1, -1);
								//long castingTime =  calculateCastingTime(aa.getActivationTime());
							}
						} else {
							if (Log.loggingDebug)
								Log.debug("CombatBehavior.handleArrived Flee obj=" + obj.getOid() + " not arrivedDest " + selectedAbility);
							if (phase == 2) {
								if (gotoLoc != null) {
									Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, gotoLoc, speed));
								}
							} else {
								if (selectedAbility > 0) {
									long delay = selectedBehavior.abilityinterval;
									AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
									long castingTime = calculateCastingTime(aa.getActivationTime());
                                    if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                                        castingTime += aa.getAttackTime();
                                    }

                                    if (castingTime > delay)
										delay = castingTime;
									if (Log.loggingDebug)
										Log.debug("CombatBehavior.handleArrived flee Start Ability " + selectedAbility);
									boolean rv = runAbility(selectedAbility);

									if (rv) {
										selectedAbilityGroup = null;
										BehaviorLogicTaskSchedule(delay);
										selectedAbility = -1;
									}
									BehaviorLogicTaskSchedule(delay);
									break;
								}
							}
						}
					}
					if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Flee obj="+obj.getOid()+" END");
					break;
			case 5://heal
				if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: Heal selectedAbility="+selectedAbility);
				if (selectedAbility > 0) {
					long delay = selectedBehavior.abilityinterval;
					AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
					long castingTime = calculateCastingTime(aa.getActivationTime());
                    if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                        castingTime += aa.getAttackTime();
                    }

                    if (castingTime > delay)
						delay = castingTime;
					if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Start Ability " + selectedAbility);
					boolean rv = runAbility(selectedAbility);
					if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Start Ability " + selectedAbility +" rv="+rv);
					if(rv) {
						selectedAbilityGroup = null;
						BehaviorLogicTaskSchedule(delay);
						selectedAbility = -1;
					}
					if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: Heal End");
					//CombatClient.startAbility(selectedAbility, obj.getOid(), currentTarget, null, false, -1, -1);
					break;
				}
				BehaviorLogic();
				break;
			}
		} else {
			if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived: Selected behavior is null");
		}
        Log.debug("CombatBehavior.handleArrived: PropertyMessage END");
    }

    
    /**
     * Tells the mob to attack the specified target (or to stop attacking if there is none).
     * @param targetOid
     */
    OID lastAttacked = null;
protected void attackTarget(OID targetOid) {
    lock.lock();
    try {
        if (Log.loggingDebug)
            Log.debug("CombatBehavior.attackTarget: obj=" + obj.getOid() + " targetOid=" + targetOid + " currentTarget=" + currentTarget + " lastAttacked=" + lastAttacked + " inCombat=" + inCombat + " behavior=" + selectedBehavior);
        if (lastAttacked == null && targetOid == null) {
            Log.debug("CombatBehavior.attackTarget: lastAttacked and new target is null");
            return;
        }
        if (isDead) {
            Log.debug("CombatBehavior.attackTarget: mob is dead");
            return;
        }
        if (selectedBehavior != null && selectedBehavior.behaviorType == 5) {
            Log.debug("CombatBehavior.attackTarget: behavior is heal ignore target from threat calculation ");
            return;
        }
        lastAttacked = targetOid;

        if (targetOid != null) {

            // Play Aggro coord effect
            if (!inCombat) {
                if (Log.loggingDebug)
                    Log.debug("CombatBehavior.attackTarget: Playing AggroEffect.");
                CombatClient.sendStartCombatState(obj.getOid());
                CoordinatedEffect cE = new CoordinatedEffect("AggroEffect");
                cE.sendSourceOid(true);
                cE.sendTargetOid(true);
                cE.invoke(obj.getOid(), targetOid);

                if (combatStartLoc == null) {
                    combatStartLoc = obj.getWorldNode().getLoc();
                }
            }

            // Get Self Location
            if (!targetOid.equals(currentTarget)) {
                currentTarget = targetOid;
                lastTryUseAbility = 0;
                BehaviorLogicTaskSchedule(0L);
            } else {
                currentTarget = targetOid;
            }

            inCombat = true;
            goreturn = false;
            if (Log.loggingDebug)
                Log.debug("CombatBehavior.attackTarget: obj=" + obj.getOid() + " scheduledCheck=" + (scheduledCheck == null ? "null" : scheduledCheck.isDone() + " " + scheduledCheck.getDelay(TimeUnit.MILLISECONDS) + " " + scheduledCheck.isCancelled()));
            if (scheduledCheck == null) {
                if (Log.loggingDebug)
                    Log.debug("CombatBehavior.attackTarget: obj=" + obj.getOid() + " Schedule next check in 200ms.");
                safeScheduleCheckDistance(cdt, 200, TimeUnit.MILLISECONDS);
            } else {
                if (Log.loggingDebug)
                    Log.debug("CombatBehavior.attackTarget: obj=" + obj.getOid() + " already scheduled.");
            }
            for (Behavior behav : obj.getBehaviors()) {
                if (behav instanceof PatrolBehavior) {
                    PatrolBehavior cBehav = (PatrolBehavior) behav;
                    cBehav.wasInCombat = true;
                }
            }
            if (selectedBehavior != null) {
                // Get mob to face target
                setTarget(currentTarget);
            }
        } else {
            currentTarget = targetOid;
            Log.debug("CombatBehavior.attackTarget: currentTarget is null");
            Point loc = obj.getWorldNode().getLoc();
            if (Log.loggingDebug)
                Log.debug("CombatBehavior.attackTarget: loc " + loc + " combatStartLoc=" + combatStartLoc + " centerLoc=" + centerLoc);
            boolean roamOrPatrol = false;
            boolean petBehavior = false;
            for (Behavior behav : obj.getBehaviors()) {
                if (behav instanceof PatrolBehavior || behav instanceof RadiusRoamBehavior) {
                    roamOrPatrol = true;
                    break;
                } else if (behav instanceof CombatPetBehavior) {
                    petBehavior = true;
                }
            }
            if (Log.loggingDebug) Log.debug("CombatBehavior.attackTarget: roamOrPatrol="+roamOrPatrol+" petBehavior="+petBehavior);

            if (behaviorId == -1) {
                // Do nothing or specific logic
            } else if (roamOrPatrol && combatStartLoc != null && Point.distanceTo(loc, combatStartLoc) > 0.5) {
                Log.debug("CombatBehavior.attackTarget: currentTarget is null, initiating GoReturn.");
                command = "GoReturn";
                goreturn = true;
                currentTarget = null;
                if (sendCommand == null) {
                    sendCommand = new SendCommand();
                    Engine.getExecutor().schedule(sendCommand, commandDelayMilliseconds, TimeUnit.MILLISECONDS);
                }
            } else if (centerLoc != null && Point.distanceTo(loc, centerLoc) > 0.5) {
                Log.debug("CombatBehavior.attackTarget: currentTarget is null, initiating Goto.");
                command = "Goto";
                goreturn = false;
                if (sendCommand == null) {
                    sendCommand = new SendCommand();
                    Engine.getExecutor().schedule(sendCommand, commandDelayMilliseconds, TimeUnit.MILLISECONDS);
                }
            } else if (centerLoc == null) {
                Log.debug("CombatBehavior.attackTarget: centerLoc is null, initiating Stop.");
                command = "Stop";
            } else {
                Log.debug("CombatBehavior.attackTarget: Initiating Stop.");
                command = "Stop";
                Engine.getAgent().sendBroadcast(new BaseBehavior.StopCommandMessage(obj.getOid()));
                Engine.getAgent().sendBroadcast(new BaseBehavior.ArrivedEventMessage(obj));
            }
            if (Log.loggingDebug)
                Log.debug("CombatBehavior.attackTarget: command is " + command);
            inCombat = false;
            if (Log.loggingDebug)
                Log.debug("CombatBehavior.attackTarget: set current target: " + currentTarget + " to null");
            if (deactivateOutOfCombat) {
                // Despawn this mob
                PropertyMessage propMsg = new PropertyMessage(obj.getOid());
                propMsg.setProperty("despawn", true);
                propMsg.setProperty("facing", null);
                Engine.getAgent().sendBroadcast(propMsg);
                return;
            } else {
                setTarget(null);
            }
        }
    } finally {
        lock.unlock();
    }
    if (targetOid != null) {
        SubscriptionManager.get().subscribe(this, targetOid, CombatClient.MSG_TYPE_COMBAT_LOGOUT, WorldManagerClient.MSG_TYPE_DESPAWNED);
    }
}

	/**
	 * Calculating casting time using casting modify statistic and thresholds
	 * @param time
	 * @return modified time
	 */
	public long calculateCastingTime(long time) {
		float mod = 1f;
		if (CombatPlugin.ABILITY_CAST_TIME_MOD_STAT != null) {
			Serializable castStat = propMap.computeIfAbsent(obj.getOid(), __ -> new ConcurrentHashMap<>()).get(CombatPlugin.ABILITY_CAST_TIME_MOD_STAT);
			if (castStat != null) {

				int statValue = (int) castStat;
				if (Log.loggingDebug)
					Log.debug("AgisAbility: CastTimeMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_CAST_TIME_MOD_STAT);
				int calculated = 0;
				if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_CAST_TIME_MOD_STAT)) {
					StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_CAST_TIME_MOD_STAT);
					int pointsCalculated = 0;
					for (int i = 1; i <= def.getPoints().size(); i++) {
						if (Log.loggingDebug)
							Log.debug(
									"AgisAbility: CastTimeMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
						if (statValue <= def.getThresholds().get(i)) {
							Log.debug("AgisAbility: CastTimeMod statValue < th");
							if (statValue - pointsCalculated < 0)
								break;
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += statValue - pointsCalculated;
						} else {
							Log.debug("AgisAbility: CastTimeMod statValue > th");
							calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
						}
					}
				} else {
					calculated = statValue;
				}
				if (Log.loggingDebug)
					Log.debug("AgisAbility: CastTimeMod calculated: " + calculated);
				mod = calculated / 100f;
				if (Log.loggingDebug)
					Log.debug("AgisAbility: CastTimeMod calculated=" + calculated + " mod=" + mod);
			}
		}
        Long dTime = (long)Math.round(time * mod);
        if(Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: dTime="+dTime);
		return dTime;
	}
    
    
    
	boolean runAbility(int id) {
		if (Log.loggingDebug)Log.debug("runAbility id "+id+" oid "+obj.getOid());
		InterpolatedWorldNode wnode = obj.getWorldNode();
		if (wnode == null) {
			Log.error("CombatBehavior.runAbility: got null wnode during check ability for oid: " + obj.getOid());
			return false;
		}
		 
		if (lastUsedAbility > 0) {
			AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
			if (!lastAA.getCastingInRun()) {
				long castingTime = calculateCastingTime(lastAA.getActivationTime());
                if(!lastAA.getCastingInRun() && lastAA.getActivationTime()>0) {
                    castingTime += lastAA.getAttackTime();
                }

                if (castingTime + lastUseAbility > System.currentTimeMillis()) {
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility " + id + " mob casting ability no move or run ability "+(castingTime + lastUseAbility - System.currentTimeMillis() )+"ms");
					return false;
				}
			}

		}
		if (lastTryUseAbility == 0) {
			lastTryUseAbility = System.currentTimeMillis();
		} else {
			if (lastTryUseAbility > 0) {
				if (System.currentTimeMillis() - lastTryUseAbility > AgisMobPlugin.MOB_COMBAT_BEHAVIOR_USE_ABILITY_TIMEOUT) {
					if (Log.loggingDebug)Log.debug("runAbility time for use ability is out random new ability");
					if(selectedBehavior!=null && selectedBehavior.behaviorType == 1) {
						if (combatStartLoc != null) {
							command = "GoReturn";
							goreturn = true;
							currentTarget = null;
							if (sendCommand == null) {
								sendCommand = new SendCommand();
								Engine.getExecutor().schedule(sendCommand, commandDelayMilliseconds, TimeUnit.MILLISECONDS);
							}
							setTarget(null);
						}else {
							if (selectedAbilityGroup != null) {
								selectedAbility = selectedAbilityGroup.getAbility();
								lastTryUseAbility = 0;
							}
						}
	                    return false;
					}else {
                    
					if (selectedAbilityGroup != null) {
						selectedAbility = selectedAbilityGroup.getAbility();
						lastTryUseAbility = 0;
					}
					}
				}

			}
		}
		if(selectedAbilityGroup==null) {
			if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility " + id + " selectedAbilityGroup is null");
			return false;
		}
		if(selectedBehavior==null) {
			if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility " + id + " selectedBehavior is null");
			return false;
		}
	
		Point loc = wnode.getCurrentLoc();
		EntityHandle eHandle = new EntityHandle(currentTarget);
		
		AgisAbility aa = Agis.AbilityManager.get(id);
		if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility "+id+ " "+aa+" currentTarget="+currentTarget+" eHandle="+eHandle);
        if (aa == null) {
            if (Log.loggingDebug) Log.debug("CombatBehavior.runAbility " + id + " aa is null");
            return false;
        }
		float minrange = aa.getMinRange();
		float maxrange = aa.getMaxRange();

		selectedAbility = id;
		ObjectStub followObj = (ObjectStub) eHandle.getEntity(Namespace.MOB);
		if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: obj=" + obj.getOid() + " followObj="+followObj);
        if(followObj==null) {
            if(Log.loggingDebug)Log.debug("CombatBehavior.runAbility: got null ObjectStub of the target during check ability for oid: " + obj.getOid()+" for "+currentTarget);
            return false;
        }
        if(followObj.getWorldNode() == null) {
            if(Log.loggingDebug)Log.debug("CombatBehavior.runAbility: worldNode is null of the target for mob oid: " + obj.getOid() + " and target " + currentTarget);
            return false;
        }
        if(followObj.getWorldNode() == null) {
            Log.debug("CombatBehavior.runAbility: worldNode is null of the target for mob oid: " + obj.getOid() + " and target " + currentTarget);
            return false;
        }
		Point followLoc = followObj.getWorldNode().getLoc();
		if (loc == null || followLoc == null) {
			Log.warn("Undefined position: loc=" + loc + "followLoc=" + followLoc);
            return false;
		}
		float distance = Point.distanceTo(loc, followLoc);
		float targetHitBoxRange = eHandle.getHitBox();
        if(aa.getTargetType().equals(AgisAbility.TargetType.AREA) && aa.getAoEType().equals(AgisAbility.AoeType.PLAYER_RADIUS)){
            minrange = - hitBoxRange - targetHitBoxRange;
            maxrange = aa.getAreaOfEffectRadius() - hitBoxRange - targetHitBoxRange;
        }
		Point respPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), followLoc, obj.getInstanceOid());
		ArrayList<AOVector> path = AgisMobPlugin.GetPath(obj.getOid(), followLoc, obj.getInstanceOid());
		
		if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: obj=" + obj.getOid() + " targetOid=" + currentTarget + " !!!!!!!!!!!!!!!!!!!!!!!!! respPoint "+respPoint+" path="+path);
		
		if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: obj=" + obj.getOid() + " distance=" + distance + " minrange="+minrange+" maxrange="+maxrange+" hitBoxRange="+hitBoxRange+" eHandle.getHitBox()="+targetHitBoxRange+" behaviorType="+(selectedBehavior!=null?selectedBehavior.behaviorType:"n/d")+" phase="+phase+" selectedAbility="+selectedAbility+" selectedAbilityGroup="+selectedAbilityGroup);
		
		if(selectedAbilityGroup==null) {
			if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility " + id + " selectedAbilityGroup is null");
			return false;
		}
		if(selectedBehavior==null) {
			if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility " + id + " selectedBehavior is null");
			return false;
		}
		if (distance  >= minrange + targetHitBoxRange + hitBoxRange && distance  <= maxrange + targetHitBoxRange + hitBoxRange) {
			if (selectedBehavior!=null && (selectedBehavior.behaviorType == 0 || selectedBehavior.behaviorType == 1 || selectedBehavior.behaviorType == 3  || selectedBehavior.behaviorType == 5)) {
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: obj=" + obj.getOid() + " Last use ability "+(System.currentTimeMillis() - lastUseAbility));
				
				if(System.currentTimeMillis() - lastUseAbility <  selectedBehavior.abilityinterval) {
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: obj=" + obj.getOid() + " in range but ability should not be useed now then move ");
					Point _followLoc = new Point(followLoc);
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc );
					_followLoc.sub(loc);
					//if(selectedBehavior.behaviorType == 4) {
					//	_followLoc.setY(0);
					//}
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " sub my loc ");
					float len = (float) Math.sqrt(_followLoc.getX() * _followLoc.getX() + _followLoc.getY() * _followLoc.getY() + _followLoc.getZ() * _followLoc.getZ());
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + " len=" + len);
					_followLoc.multiply(1 / len);
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * 1/len ");
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " distance="+distance+" minrange="+minrange+" maxrange="+maxrange+" minAbilityRangePercentage="+
					selectedAbilityGroup.minAbilityRangePercentage+" maxAbilityRangePercentage="+selectedAbilityGroup.maxAbilityRangePercentage);
					
					float m = distance;
					Random r = new Random();
					float range = r.nextFloat();
					range = range * (selectedAbilityGroup.maxAbilityRangePercentage-selectedAbilityGroup.minAbilityRangePercentage)+selectedAbilityGroup.minAbilityRangePercentage;
					m = distance - ((maxrange - minrange) * range / 100f + minrange) - targetHitBoxRange - hitBoxRange;
					float min = ((maxrange - minrange) * selectedAbilityGroup.minAbilityRangePercentage / 100f + minrange) + targetHitBoxRange + hitBoxRange;
					float max = ((maxrange - minrange) * selectedAbilityGroup.maxAbilityRangePercentage / 100f + minrange) + targetHitBoxRange + hitBoxRange;
					
					//m = distance - ((maxrange - minrange) * selectedAbilityGroup.minAbilityRangePercentage / 100f + minrange) - targetHitBoxRange - hitBoxRange;
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + "  m= " + m + " hitBoxRange=" + hitBoxRange + " eHandle.getHitBox()=" + targetHitBoxRange+" min="+min+" max="+max+" percentage range "+range);
					_followLoc.multiply(m);
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * followDist - hitbox ");
					_followLoc.add(loc);
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + "; myLoc = " + loc + "; followLoc=" + followLoc + " _followLoc=" + _followLoc + " followDist=" + distance + " len=" + len
							+ " target hitBox=" + targetHitBoxRange + " speed=" + speed + " distance=" + distance);
					// Point destLoc = _followLoc;
					float distance2 = Point.distanceTo(loc, _followLoc);
					float distance3 = Point.distanceTo(followLoc, _followLoc);
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  in ability range obj=" + obj.getOid() + " targetOid=" + currentTarget + " send command goto mob_loc=" + loc + " target_loc=" + followLoc + " distance=" + distance + " calculated loc="
								+ _followLoc + " speed " + speed + " mob distance to follow point " + distance2 + " target dostance to follow point " + distance3);
					if ((distance < min || distance > max) && speed > 0) {
						command = "Goto";
						goreturn = false;
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  in ability range obj=" + obj.getOid() + " move to position " + _followLoc);
						Point newPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), _followLoc, obj.getInstanceOid());
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  in ability range obj=" + obj.getOid() + " NavMesh position correction new position " + newPoint);

						CombatClient.sendStartCombatState(obj.getOid());
						Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, speed));
					}  else {
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  in ability range obj=" + obj.getOid() + " mob is between min and max ");
					}
					return false;
				} else {

                            Point _loc = new Point(0f, 0, 0f);
                            _loc.add(wnode.getLoc());
                            AOVector reqDir = AOVector.sub(followLoc, _loc);
                            float vYaw = AOVector.getLookAtYaw(reqDir);
                    if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Start Ability oid="+obj.getOid()+" vYaw=" + vYaw+" getYaw="+wnode.getOrientation().getYaw()+" _loc="+_loc+" followLoc="+followLoc);
                    if(AgisMobPlugin.MOB_DEBUG_PLAYER!=null) {
                        //Debug
                        Quaternion casterQuaternion1 = new Quaternion().setEulerAngles(wnode.getOrientation().getPitch(), wnode.getOrientation().getYaw(), wnode.getOrientation().getRoll());
                        Quaternion casterQuaternion2 = new Quaternion().setEulerAngles(wnode.getOrientation().getPitch(), vYaw, wnode.getOrientation().getRoll());
                        AOVector testLoc1 = new AOVector(wnode.getLoc());
                        testLoc1.add(wnode.getOrientation().getXAxis().multiply(4f));
                        AOVector testLoc2 = new AOVector(wnode.getLoc());
                        testLoc2.add(casterQuaternion2.getZAxis().multiply(4f));
                        Map<String, Serializable> props = new HashMap<String, Serializable>();
                        props.put("ext_msg_subtype", "behavior_target");
                        props.put("locX", wnode.getLoc().getX());
                        props.put("locY", wnode.getLoc().getY());
                        props.put("locZ", wnode.getLoc().getZ());
                        props.put("locX1", testLoc1.getX());
                        props.put("locY1", testLoc1.getY());
                        props.put("locZ1", testLoc1.getZ());

                        props.put("locX2", testLoc2.getX());
                        props.put("locY2", testLoc2.getY());
                        props.put("locZ2", testLoc2.getZ());
                        TargetedExtensionMessage extmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, AgisMobPlugin.MOB_DEBUG_PLAYER, AgisMobPlugin.MOB_DEBUG_PLAYER, false, props);
                        Engine.getAgent().sendBroadcast(extmsg);
                        //endDebug
                    }
                    Quaternion cQuaternion = obj.getWorldNode().getOrientation();
                    cQuaternion.setEulerAngles(wnode.getOrientation().getPitch(), vYaw, wnode.getOrientation().getRoll());
                    obj.getWorldNode().setOrientation(cQuaternion);
                    obj.updateWorldNode();
                    if (!aa.getCastingInRun() && aa.getActivationTime() > 0L) {
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility: in ability range obj=" + obj.getOid() + " targetOid=" + currentTarget + " ability not casting in run send stop ");
						command = "Stop";
                        CombatClient.sendStartCombatState(obj.getOid());
						Engine.getAgent().sendBroadcast(new BaseBehavior.StopCommandMessage(obj.getOid()));
					} else {
						Point _followLoc = new Point(followLoc);
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc);
						_followLoc.sub(loc);
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " sub my loc ");
						float len = (float) Math.sqrt(_followLoc.getX() * _followLoc.getX() + _followLoc.getY() * _followLoc.getY() + _followLoc.getZ() * _followLoc.getZ());
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + " len=" + len);
						_followLoc.multiply(1 / len);
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * 1/len ");
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " distance=" + distance + " minrange=" + minrange + " maxrange=" + maxrange
									+ " minAbilityRangePercentage=" +(selectedAbilityGroup!=null?selectedAbilityGroup.minAbilityRangePercentage:"n/d") + " maxAbilityRangePercentage=" + (selectedAbilityGroup!=null?selectedAbilityGroup.maxAbilityRangePercentage:"n/d"));

						float m = distance;
						Random r = new Random();
						float range = r.nextFloat();
						range = range * (selectedAbilityGroup.maxAbilityRangePercentage - selectedAbilityGroup.minAbilityRangePercentage)+selectedAbilityGroup.minAbilityRangePercentage;
						m = distance - ((maxrange - minrange) * range / 100f + minrange) - targetHitBoxRange - hitBoxRange;
						float min = ((maxrange - minrange) * selectedAbilityGroup.minAbilityRangePercentage / 100f + minrange) + targetHitBoxRange + hitBoxRange;
						float max = ((maxrange - minrange) * selectedAbilityGroup.maxAbilityRangePercentage / 100f + minrange) + targetHitBoxRange + hitBoxRange;
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + "  m= " + m + " hitBoxRange=" + hitBoxRange + " eHandle.getHitBox()=" + targetHitBoxRange+" min="+min+" max="+max+" percentage range "+range);
						_followLoc.multiply(m);
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * followDist - hitbox ");
						_followLoc.add(loc);
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  in ability range oid = " + obj.getOid() + "; myLoc = " + loc + "; followLoc=" + followLoc + " _followLoc=" + _followLoc + " followDist=" + distance
									+ " len=" + len + " target hitBox=" + targetHitBoxRange + " speed=" + speed + " distance=" + distance);
						float distance2 = Point.distanceTo(loc, _followLoc);
						float distance3 = Point.distanceTo(followLoc, _followLoc);
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  in ability range obj=" + obj.getOid() + " targetOid=" + currentTarget + " send command goto mob_loc=" + loc + " target_loc=" + followLoc + " distance="
									+ distance + " calculated loc=" + _followLoc + " speed " + speed + " mob distance to follow point " + distance2 + " target dostance to follow point " + distance3);
						if ((distance < min || distance > max)&& speed > 0) {
							command = "Goto";
							goreturn = false;
							if (Log.loggingDebug)
								Log.debug("CombatBehavior.runAbility:  in ability range obj=" + obj.getOid() + " move to position " + _followLoc);
							Point newPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), _followLoc, obj.getInstanceOid());
							CombatClient.sendStartCombatState(obj.getOid());
							Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, speed));
						} else {
							if (Log.loggingDebug)
								Log.debug("CombatBehavior.runAbility:  in ability range obj=" + obj.getOid() + " mob is between min and max ");
						}
					}

					if (Log.loggingDebug)
						Log.debug("CombatBehavior.runAbility:  in ability range obj=" + obj.getOid() + " targetOid=" + currentTarget + " use ability " + id+" distance="+distance);
					reloacatingForAbility = false;
					if (Log.loggingDebug)
						Log.debug("CombatBehavior.runAbility:  in ability range set reloacatingForAbility to false ");
					// selectedAbilityGroup = null;
					if (selectedAbility > 0) {
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  in ability range run ability " + selectedAbility);
						CombatClient.startAbility(selectedAbility, obj.getOid(), currentTarget, null, false, -1, -1);
						lastUseAbility = System.currentTimeMillis();
						lastUsedAbility = selectedAbility;
						lastUsedAbilityGroup = selectedAbilityGroup;
						lastTryUseAbility=0;
//                        if (!aa.getCastingInRun() && aa.getActivationTime() > 0L) {
//                            if (Log.loggingDebug)
//                                Log.error("CombatBehavior.runAbility: obj=" + obj.getOid() + " WORLD_PROP_NOTURN to true");
//                            EnginePlugin.setObjectPropertyNoResponse(obj.getOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, true);
//                        }
					}
					selectedAbility = -1;
					if (Log.loggingDebug)
						Log.debug("runAbility  in ability range " + id + " End ");
				}
			} else if (selectedBehavior!=null && selectedBehavior.behaviorType == 2) {
				// Ranged defensive
				Point _followLoc = new Point(followLoc);
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  Ranged defensive oid = " + obj.getOid() + " _followLoc=" + _followLoc + " ");
				_followLoc.sub(loc);
				_followLoc.setY(0);
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  Ranged defensive oid = " + obj.getOid() + " _followLoc=" + _followLoc + " sub my loc ");
				float len = (float) Math.sqrt(_followLoc.getX() * _followLoc.getX() + _followLoc.getY() * _followLoc.getY() + _followLoc.getZ() * _followLoc.getZ());
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  Ranged defensive oid = " + obj.getOid() + " len=" + len);
				_followLoc.multiply(1 / len);
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  Ranged defensive oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * 1/len ");
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  Ranged defensive oid = " + obj.getOid() + " _followLoc=" + _followLoc + " distance="+distance+" minrange="+minrange+" maxrange="+maxrange+" minAbilityRangePercentage="+
						(selectedAbilityGroup!=null?selectedAbilityGroup.minAbilityRangePercentage:"n/d")+" maxAbilityRangePercentage="+(selectedAbilityGroup!=null?selectedAbilityGroup.maxAbilityRangePercentage:"n/d"));
				
				if(selectedAbilityGroup==null) {
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility " + id + " selectedAbilityGroup is null");
					return false;
				}
				float m = distance;
				m = distance - ((maxrange - minrange) * selectedAbilityGroup.minAbilityRangePercentage / 100f + minrange) - targetHitBoxRange - hitBoxRange;
				float min = ((maxrange - minrange) * selectedAbilityGroup.minAbilityRangePercentage / 100f + minrange) + targetHitBoxRange + hitBoxRange;
				float max = ((maxrange - minrange) * selectedAbilityGroup.maxAbilityRangePercentage / 100f + minrange) + targetHitBoxRange + hitBoxRange;
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  Ranged defensive oid = " + obj.getOid() + "  m= " + m + " hitBoxRange=" + hitBoxRange + " eHandle.getHitBox()=" + targetHitBoxRange+" min="+min+" max="+max);
				_followLoc.multiply(m);
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  Ranged defensive oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * followDist - hitbox ");
				_followLoc.add(loc);
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  Ranged defensive oid = " + obj.getOid() + "; myLoc = " + loc + "; followLoc=" + followLoc + " _followLoc=" + _followLoc + " followDist=" + distance + " len=" + len
						+ " target hitBox=" + targetHitBoxRange + " speed=" + speed + " distance=" + distance);
				float distance2 = Point.distanceTo(loc, _followLoc);
				float distance3 = Point.distanceTo(followLoc, _followLoc);
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Ranged defensive  Math.abs(distance2 - distance) "+Math.abs(distance2 - distance)+" distance2="+distance2+" distance3="+distance3+" distance="+distance);
				if (distance < min || distance > max || System.currentTimeMillis() - lastUseAbility <  selectedBehavior.abilityinterval) {

					Random r = new Random();
					float range = r.nextFloat();
					range = range * (selectedAbilityGroup.maxAbilityRangePercentage - selectedAbilityGroup.minAbilityRangePercentage) + selectedAbilityGroup.minAbilityRangePercentage;
					m = distance - ((maxrange - minrange) * range / 100f + minrange) - targetHitBoxRange - hitBoxRange;
					
					reloacatingForAbility = true;
					command = "Goto";
					goreturn = false;
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Ranged defensive  move to position "+_followLoc);
					Point newPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), _followLoc, obj.getInstanceOid());
					
					CombatClient.sendStartCombatState(obj.getOid());
					Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, speed));
					return false;
				} else {
					//
					if (System.currentTimeMillis() - lastUseAbility < selectedBehavior.abilityinterval) {
						Point nfollowLoc = new Point(followLoc);
						nfollowLoc.sub(loc);
						nfollowLoc.setY(0);
						nfollowLoc.multiply(1 / len);
						m = distance - max - 0.1f;
						nfollowLoc.multiply(m);
						nfollowLoc.add(loc);
						Point newPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), _followLoc, obj.getInstanceOid());
						CombatClient.sendStartCombatState(obj.getOid());
						Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, speed));
						return false;
					} else {
						if (!aa.getCastingInRun() && aa.getActivationTime() > 0L) {
							if (Log.loggingDebug)
								Log.debug("CombatBehavior.runAbility:  Ranged defensive obj=" + obj.getOid() + " targetOid=" + currentTarget + " send stop ");
							command = "Stop";
							Engine.getAgent().sendBroadcast(new BaseBehavior.StopCommandMessage(obj.getOid()));
						}
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  Ranged defensive obj=" + obj.getOid() + " targetOid=" + currentTarget + " use ability " + id + " distance=" + distance);
						reloacatingForAbility = false;
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.runAbility:  Ranged defensive set reloacatingForAbility to false ");
						selectedAbilityGroup = null;
						if (selectedAbility > 0) {
							if (Log.loggingDebug)
								Log.debug("CombatBehavior.runAbility:  Ranged defensive run ability " + selectedAbility);
                            if(followObj!=null) {
                                if (followObj.getWorldNode() != null) {
                                    Point _loc = new Point(0f, 0, 0f);
                                    _loc.add(wnode.getLoc());
                                    AOVector reqDir = AOVector.sub(followLoc, _loc);
                                    float vYaw = AOVector.getLookAtYaw(reqDir);
                                    if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Start Ability oid="+obj.getOid()+" vYaw=" + vYaw+" getYaw="+wnode.getOrientation().getYaw()+" _loc="+_loc+" followLoc="+followLoc);
                                    if(AgisMobPlugin.MOB_DEBUG_PLAYER!=null) {
                                        //Debug
                                        Quaternion casterQuaternion1 = new Quaternion().setEulerAngles(wnode.getOrientation().getPitch(), wnode.getOrientation().getYaw(), wnode.getOrientation().getRoll());
                                        Quaternion casterQuaternion2 = new Quaternion().setEulerAngles(wnode.getOrientation().getPitch(), vYaw, wnode.getOrientation().getRoll());
                                        AOVector testLoc1 = new AOVector(wnode.getLoc());
                                        testLoc1.add(wnode.getOrientation().getXAxis().multiply(4f));
                                        AOVector testLoc2 = new AOVector(wnode.getLoc());
                                        testLoc2.add(casterQuaternion2.getZAxis().multiply(4f));
                                        Map<String, Serializable> props = new HashMap<String, Serializable>();
                                        props.put("ext_msg_subtype", "behavior_target");
                                        props.put("locX", wnode.getLoc().getX());
                                        props.put("locY", wnode.getLoc().getY());
                                        props.put("locZ", wnode.getLoc().getZ());
                                        props.put("locX1", testLoc1.getX());
                                        props.put("locY1", testLoc1.getY());
                                        props.put("locZ1", testLoc1.getZ());

                                        props.put("locX2", testLoc2.getX());
                                        props.put("locY2", testLoc2.getY());
                                        props.put("locZ2", testLoc2.getZ());
                                        TargetedExtensionMessage extmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, AgisMobPlugin.MOB_DEBUG_PLAYER, AgisMobPlugin.MOB_DEBUG_PLAYER, false, props);
                                        Engine.getAgent().sendBroadcast(extmsg);
                                        //endDebug
                                    }
                                    Quaternion cQuaternion = obj.getWorldNode().getOrientation();
                                    cQuaternion.setEulerAngles(wnode.getOrientation().getPitch(), vYaw, wnode.getOrientation().getRoll());
                                    obj.getWorldNode().setOrientation(cQuaternion);
                                    obj.updateWorldNode();
                                    CombatClient.startAbility(selectedAbility, obj.getOid(), currentTarget, null, false, -1, -1);
                                }
                            }

							lastUseAbility = System.currentTimeMillis();
							lastUsedAbility = selectedAbility;
							lastUsedAbilityGroup = selectedAbilityGroup;
							lastTryUseAbility=0;
						}
					}
				}

				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility:  Ranged defensive END ");
			}
			
			return true;
		} else {
			//Out of ability Range
			if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Out of ability Range obj=" + obj.getOid() + " targetOid=" + currentTarget + " target not in range of ability " + id + " behaviorType=" + selectedBehavior.behaviorType);
			//if (behaviorType == 0 || behaviorType == 1) {
				if(selectedAbilityGroup!=null){
				reloacatingForAbility = true;
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Out of ability Range set reloacatingForAbility to true !!!!! ");
				Point _followLoc = new Point(followLoc);
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Out of ability Range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " ");
				_followLoc.sub(loc);
				_followLoc.setY(0);
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Out of ability Range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " sub my loc ");
				float len = (float) Math.sqrt(_followLoc.getX() * _followLoc.getX() + _followLoc.getY() * _followLoc.getY() + _followLoc.getZ() * _followLoc.getZ());
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Out of ability Range oid = " + obj.getOid() + " len=" + len);
				_followLoc.multiply(1 / len);
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Out of ability Range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * 1/len ");
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Out of ability Range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " distance="+distance+" minrange="+minrange+" maxrange="+maxrange+" minAbilityRangePercentage="+
						(selectedAbilityGroup!=null?selectedAbilityGroup.minAbilityRangePercentage:"n/d")+" maxAbilityRangePercentage="+(selectedAbilityGroup!=null?selectedAbilityGroup.maxAbilityRangePercentage:"n/d"));
				
				float m = distance;
				Random r = new Random();
				float range = r.nextFloat();
				range = range * (selectedAbilityGroup.maxAbilityRangePercentage-selectedAbilityGroup.minAbilityRangePercentage)+selectedAbilityGroup.minAbilityRangePercentage;
				m = distance - ((maxrange - minrange) * range / 100f + minrange) - targetHitBoxRange - hitBoxRange;
				float min = distance - ((maxrange - minrange) * selectedAbilityGroup.minAbilityRangePercentage / 100f + minrange) - targetHitBoxRange - hitBoxRange;
				float max = distance - ((maxrange - minrange) * selectedAbilityGroup.maxAbilityRangePercentage / 100f + minrange) - targetHitBoxRange - hitBoxRange;
			
				/*if (distance < minrange+ targetHitBoxRange + hitBoxRange) {
					m = distance - ((maxrange - minrange) * selectedAbilityGroup.minAbilityRangePercentage / 100f + minrange) - targetHitBoxRange - hitBoxRange;
				} else if (distance > maxrange) {
					m = distance - ((maxrange - minrange) * selectedAbilityGroup.maxAbilityRangePercentage / 100f + minrange) - targetHitBoxRange - hitBoxRange;
				}*/
				

				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Out of ability Range oid = " + obj.getOid() + "  m= " + m + " hitBoxRange=" + hitBoxRange + " eHandle.getHitBox()=" + targetHitBoxRange+" min="+min+" max="+max+" percentage range "+range);
				_followLoc.multiply(m);
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Out of ability Range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * followDist - hitbox ");
				_followLoc.add(loc);
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Out of ability Range oid = " + obj.getOid() + "; myLoc = " + loc + "; followLoc=" + followLoc + " _followLoc=" + _followLoc + " followDist=" + distance + " len=" + len
						+ " target hitBox=" + targetHitBoxRange + " speed=" + speed + " distance=" + distance);
				float distance2 = Point.distanceTo(loc, _followLoc);
				float distance3 = Point.distanceTo(followLoc, _followLoc);
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Out of ability Range obj=" + obj + " targetOid=" + currentTarget + " send command goto mob_loc=" + loc + " target_loc=" + followLoc + " distance=" + distance + " calculated loc="
						+ _followLoc + " speed " + speed + " mob distance to follow point " + distance2 + " target dostance to follow point " + distance3);
				command = "Goto";
				goreturn = false;
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: Out of ability Range move to position "+_followLoc);
				Point newPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), _followLoc, obj.getInstanceOid());
				CombatClient.sendStartCombatState(obj.getOid());
				Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, speed));
				} else {
					if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility selectedAbilityGroupis null id"+id+ " End ");
				}
				if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility Out of ability Range ability id"+id+ " End ");
				return false;
		}
	}
	
	
    /**
     * Sets the current target for the mob. Currently just sets the facing property so the mob will continue
     * to face the target on players clients.
     * @param targetOid
     */
    protected void setTarget(OID targetOid) {
        PropertyMessage propMsg = new PropertyMessage(obj.getOid());
        propMsg.setProperty("facing", targetOid);
        Engine.getAgent().sendBroadcast(propMsg);
    }
    
    /**
     * Handles the dead of the mob. Sends out the exp and loot messages 
     * @param killer
     */
    protected void handleDeath(OID killer) {
        if (Log.loggingDebug)
                Log.debug("DEATH: got handleDeath with killer: " + killer+" of oid "+obj.getOid());
        inCombat = false;
        currentTarget = null;
        evade = false;
        isDead =true;
        if (Log.loggingDebug)
              Log.debug("TAG: got tagOwner: " + tagOwner);
        if (Log.loggingDebug)
            Log.debug("DEATH: AgisMobPlugin.MOB_DEATH_EXP="+AgisMobPlugin.MOB_DEATH_EXP+" AgisMobPlugin.EXP_BASED_ON_DAMAGE_DEALT="+AgisMobPlugin.EXP_BASED_ON_DAMAGE_DEALT);
        
        // Stop mob movement
        command = "Stop";
        Engine.getAgent().sendBroadcast(new BaseBehavior.StopCommandMessage(obj.getOid()));
        
        // Send mob killed message
        ExtensionMessage killedMsg = new ExtensionMessage(AgisMobClient.MSG_TYPE_MOB_KILLED, null, obj.getOid());
        killedMsg.setProperty("killer", killer);
        killedMsg.setProperty("mobType", 0);
        Engine.getAgent().sendBroadcast(killedMsg);
        // Set mob not attackable - So players can't target it with abilities even though it is dead
        EnginePlugin.setObjectPropertyNoResponse(obj.getOid(), CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_ATTACKABLE, false);
        if(damageMap.isEmpty() && killer != null ) {
       		damageMap.put(killer, 1);
        }
        HashMap<OID,Integer> attackersForLoot = new HashMap<OID,Integer>(damageMap); 
        // Send alter exp amount message
        if (AgisMobPlugin.MOB_DEATH_EXP) {
            if (AgisMobPlugin.EXP_BASED_ON_DAMAGE_DEALT) {
                Log.debug("EXP: getting groups of damage dealers");
                // Work out if any members are part of the same group
                HashMap<OID, OID> groupMapping = new HashMap<OID, OID>();
                Set<OID> attackers = new HashSet<OID>(damageMap.keySet());
                
                for (OID attacker : attackers) {
                    Serializable petOwner = null;

                    try {
                        petOwner = EnginePlugin.getObjectProperty(attacker, WorldManagerClient.NAMESPACE, "petOwner");
                    } catch (Exception e) {
                        Log.debug("DEATH:  Tag Owner cant get petOwner");
                    }
                    if (Log.loggingDebug)
                        Log.debug("DEATH:  Tag Owner: " + tagOwner + "; " + attacker + " have petOwner: " + petOwner);
                    if (petOwner != null) {
                        if (damageMap.containsKey((OID) petOwner)) {
                            if (Log.loggingDebug)
                                Log.debug("DEATH: Pet Owner: " + petOwner + "  is in damage map");
                            damageMap.put((OID) petOwner, damageMap.get(attacker) + damageMap.get((OID) petOwner));
                            damageMap.remove(attacker);
                            continue;
                        } else {
                            if (Log.loggingDebug)
                                Log.debug("DEATH: Pet Owner: " + petOwner + " is not in damage map");
                            damageMap.put((OID) petOwner, damageMap.get(attacker));
                            damageMap.remove(attacker);
                            attacker = (OID) petOwner;
                        }
                        if (Log.loggingDebug)
                            Log.debug("DEATH: Pet Owner:" + attacker + " check is in Group ");
                    }
                    Entity e = EntityManager.getEntityByNamespace(attacker, Namespace.MOB);
					if (e!=null && e.getType().isPlayer()) {
                    GroupInfo gInfo = GroupClient.GetGroupMemberOIDs(attacker);
                    if (gInfo != null && gInfo.groupOid != null) {
                        if (groupMapping.containsKey(gInfo.groupOid)) {
                            int totalGroupDamage = damageMap.get(groupMapping.get(gInfo.groupOid)) + damageMap.get(attacker);
                            damageMap.put(groupMapping.get(gInfo.groupOid), totalGroupDamage);
                            damageMap.remove(attacker);
                        } else {
                            groupMapping.put(gInfo.groupOid, attacker);
                        }
                    }
                    }
                }
                Log.debug("DEATH: Send rewardExpForKill");
                ClassAbilityClient.rewardExpForKill(obj.getOid(), new HashMap<>(damageMap));
            } else if (tagOwner != null) {

                HashMap<OID, Integer> attackers = new HashMap<OID, Integer>();
                   Serializable petOwner = EnginePlugin.getObjectProperty(tagOwner, WorldManagerClient.NAMESPACE, "petOwner");
                    if (Log.loggingDebug)
                         Log.debug("DEATH:  "+tagOwner+" have petOwner: " + petOwner);
                   if (petOwner!=null) {
                       attackers.put((OID)petOwner, 1);
                   } else {
                       attackers.put(tagOwner, 1);
                   }
                    Log.debug("DEATH: Send rewardExpForKill");
                    ClassAbilityClient.rewardExpForKill(obj.getOid(), attackers);
            }else {
                if (Log.loggingDebug)
                      Log.debug("DEATH:  "+tagOwner+" is null");
                  
            }
        }
       damageMap.clear();
       if (tagOwner == null) {
        Log.debug("DEATH: tagownar is null return no LOOT ");
        return;
       }
    Log.debug("DEATH:  LOOT check");
        if(lootTables.isEmpty()) {
            Log.debug("DEATH: number of loot Tables is 0. Dont analyze loot");
            //  return;   
        }
        
        if (AgisMobPlugin.lootObjectTmpl == -1) {
            Log.debug("DEATH: sending generateLoot property");
            // If there is no loot object prefab, just send the generateLoot message
                OID lootTarget = null;
            if (AgisMobPlugin.MOB_DEATH_EXP) {
                ArrayList<OID> _attakers = new ArrayList<OID>(attackersForLoot.keySet());
                //TODO: work out how to get group who did most damage to then become the looter
                if (AgisMobPlugin.LOOT_BASED_ON_DAMAGE_DEALT) {
                    Set<OID> attackers = new HashSet<OID>(attackersForLoot.keySet());
                    for (OID attacker : attackers) {
                        
                          Serializable petOwner = EnginePlugin.getObjectProperty(attacker, WorldManagerClient.NAMESPACE, "petOwner");
                            if (Log.loggingDebug)
                                  Log.debug("DEATH: LOOT:   "+attacker+" have petOwner: " + petOwner);
                         if (petOwner!=null) {
                             if (attackersForLoot.containsKey((OID)petOwner)) {
                                if (Log.loggingDebug)
                                     Log.debug("DEATH: Pet Owner: "+petOwner+"  is in damage map");
                                attackersForLoot.put((OID)petOwner, attackersForLoot.get(attacker)+attackersForLoot.get((OID)petOwner)); 
                                attackersForLoot.remove(attacker);
                                attacker = (OID)petOwner;
                             }else {
                                if (Log.loggingDebug)
                                     Log.debug("DEATH: Pet Owner: "+petOwner+ " is not in damage map");
                                attackersForLoot.put((OID)petOwner, attackersForLoot.get(attacker)); 
                                attackersForLoot.remove(attacker);
                                    attacker = (OID)petOwner;
                             }
                            if (Log.loggingDebug)
                                 Log.debug("DEATH: LOOT: Pet Owner:"+attacker+" check have height amount damage ");
                         }
                                
                        if (lootTarget == null) {
                            lootTarget = attacker;
                        } else if (attackersForLoot.get(lootTarget) < attackersForLoot.get(attacker)) {
                            lootTarget = attacker;
                            }
                    }
                    if (Log.loggingDebug)
                            Log.debug("DEATH: LOOT: Loot based on damage for player "+lootTarget);
                } else {
                       Serializable petOwner = EnginePlugin.getObjectProperty(tagOwner, WorldManagerClient.NAMESPACE, "petOwner");
                        if (Log.loggingDebug)
                             Log.debug("DEATH: LOOT:  "+tagOwner+" have petOwner: " + petOwner);
                       if (petOwner!=null) {
                           lootTarget = (OID)petOwner;
                       } else {
                           lootTarget = tagOwner;
                       }
                        if (Log.loggingDebug)
                              Log.debug("DEATH:LOOT:  "+tagOwner+" have petOwner: " + petOwner+" lootTarget="+lootTarget);
                }
                if (Log.loggingDebug)
                      Log.debug("DEATH: LOOT:  generate loot for lootTarget="+lootTarget);
                  if(AgisMobPlugin.LOOT_FOR_ALL) {
                   AgisInventoryClient.generateLoot(obj.getOid(),lootTarget, _attakers);
                  }else {
                   AgisInventoryClient.generateLoot(obj.getOid(), lootTarget);
                  }
                   
            } else {
                if (Log.loggingDebug)
                     Log.debug("DEATH: LOOT:  generate loot for tagOwner="+tagOwner);
                AgisInventoryClient.generateLoot(obj.getOid(), tagOwner);
            }
                return;
        }
        
        // Send message to spawn loot mobs for the targets to pick up
        SpawnData spawnData = new SpawnData();
        spawnData.setProperty("id", 1);
        spawnData.setTemplateID(AgisMobPlugin.lootObjectTmpl);
        spawnData.setInstanceOid(obj.getInstanceOid());
        BasicWorldNode node = WorldManagerClient.getWorldNode(obj.getOid());
        Point spawnLoc = node.getLoc();
        spawnLoc.add(0f, 1f, 0f);
        spawnData.setLoc(spawnLoc);
        spawnData.setOrientation(node.getOrientation());
        spawnData.setNumSpawns(1);
        spawnData.setSpawnRadius(0);
        spawnData.setRespawnTime(-1); // Don't respawn;
        spawnData.setCorpseDespawnTime(500);
        spawnData.setProperty("lootTables", new HashMap<>(lootTables));
        
        HashMap<String, Serializable> spawnProps = new HashMap<String, Serializable>();
        ArrayList<OID> acceptableTargets = new ArrayList<OID>();
        acceptableTargets.add(tagOwner);
        spawnProps.put("acceptableTargets", acceptableTargets);
        //spawnProps.put("duration", 30000); // exist for up to 30 seconds
        spawnData.setProperty("props", spawnProps);
        ExtensionMessage spawnMsg = new ExtensionMessage();
        spawnMsg.setMsgType(AgisMobClient.MSG_TYPE_SPAWN_DOME_MOB);
        spawnMsg.setProperty("spawnData", spawnData);
        spawnMsg.setProperty("spawnType", -4 /*Dome.MOBTYPE_LOOT*/);
        spawnMsg.setProperty("roamRadius", 0);
        Engine.getAgent().sendBroadcast(spawnMsg);
        
        // Clear threatmap
        threatMap.clear();
        
    }
    
    public void alertMobDeathStateChange(OID targetOid, boolean dead) {
        lock.lock();
        try {
            if (dead) {
                if (currentTarget != null && currentTarget.equals(targetOid)) {
                    Log.debug("CombatBehavior.onMessage: target died, setting current target to null");
                    currentTarget = null;
                    if (Log.loggingDebug)
                        Log.debug("THREAT: set current target: " + targetOid + " to null");
                    //attackTarget(null);
                }

                if (removeTargetFromThreatMap(targetOid)) {
                    //setTargetThreat(subject, -1); // Set threat to -1 for dead targets
                    TargetedPropertyMessage newPropMsg = new TargetedPropertyMessage(targetOid, obj.getOid());
                    if (Log.loggingDebug)
                        Log.debug("CombatBehavior.checkDistance: send aggressive=false to "+targetOid);
                    
                    newPropMsg.setProperty("aggressive", false);
                    Engine.getAgent().sendBroadcast(newPropMsg);
                    if (Log.loggingDebug)
                        Log.debug("THREAT: removed: " + targetOid + " from threatmap");
                } else {
                    attackTarget(null);
                }
            } else {
                if (Log.loggingDebug)
                    Log.debug("REVIVE: obj=" + obj + " got life=" + targetOid + " currentTarget=" + currentTarget);
                // Someone just came alive, add to thread map?
                if (threatMap.containsKey(targetOid)) {
                    // Do a distance check to see if they are within aggro range
                    if (Log.loggingDebug) {
                        Log.debug("AGGRO: getting distance from wnode: " + obj.getWorldNode().getLoc() + " and aggro radius: " + FactionPlugin.AGGRO_RADIUS);
                    }
                    if (getDistanceToTarget(targetOid) < FactionPlugin.AGGRO_RADIUS)
                        setTargetThreat(targetOid, 0);
                }
            }
        } finally {
            lock.unlock();
        }
    }
    
    public void alertMobOfDespawn(OID targetOid) {
        if (Log.loggingDebug)
            Log.debug("DESPAWNED: got target despawned: " + targetOid);
        removeTargetFromThreatMap(targetOid); 
    }
    
    /**
     * Checks the distance the mob has travelled from its starting point. If it is further than the
     * chase distance, it will return to its starting point and go into evade mode.
     */
private void checkDistance() {
    scheduledCheck = null;
    Log.debug("CombatBehavior.checkDistance called for obj=" + obj.getOid());

    if (!activated) {
        Log.debug("CombatBehavior.checkDistance: Not activated.");
        return;
    }

    if (behaviorId == -1) {
        if (currentTarget != null) {
            InterpolatedWorldNode ownode = obj.getWorldNode();
            Point loc = ownode.getCurrentLoc();
            float distance = Point.distanceTo(loc, centerLoc);
            float distance2 = Point.distanceTo(loc, combatStartLoc);

            EntityHandle eHandle = new EntityHandle(currentTarget);
            ObjectStub currentTargetObj = (ObjectStub) eHandle.getEntity(Namespace.MOB);

            if (Log.loggingDebug)
                Log.debug("CombatBehavior.checkDistance: obj=" + obj.getOid() + " followObj=" + currentTargetObj);
            if (currentTargetObj != null) {
                Point currentTargetLoc = currentTargetObj.getWorldNode().getLoc();
                float distance3 = Point.distanceTo(loc, currentTargetLoc);

                if (Log.loggingDebug)
                    Log.debug("CombatBehavior.checkDistance: obj=" + obj.getOid() + " distance=" + distance + " distance2=" + distance2 + " distance3=" + distance3);
                if (distance3 > chaseDistance) {
                    if (Log.loggingDebug)
                        Log.debug("CombatBehavior.checkDistance: Target out of range. Removing target.");
                    removeTargetFromThreatMap(currentTarget);
                    inCombat = false;
                    evade = true;
                    CombatClient.sendResetAttacker(obj.getOid());
                    CombatClient.setCombatInfoState(obj.getOid(), CombatInfo.COMBAT_STATE_RESET);

                    if (deactivateOutOfCombat) {
                        // Despawn this mob immediately
                        PropertyMessage propMsg = new PropertyMessage(obj.getOid());
                        propMsg.setProperty("despawn", true);
                        propMsg.setProperty("facing", null);
                        Engine.getAgent().sendBroadcast(propMsg);
                    } else {
                        setTarget(null);
                    }

                    return; // Exit the method immediately after handling
                } else {
                    if (scheduledCheck == null) {
                        Log.debug("CombatBehavior.checkDistance: Scheduling next check in 200ms.");
                        safeScheduleCheckDistance(cdt, 200, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }
    }

    if (!activated || centerLoc == null) {
        Log.debug("CombatBehavior.checkDistance: Not activated or centerLoc is null.");
        return;
    }

    InterpolatedWorldNode wnode = obj.getWorldNode();
    if (wnode == null) {
        Log.error("CombatBehavior.checkDistance: Null wnode for oid: " + obj.getOid());
        return;
    }
    Point loc = wnode.getCurrentLoc();

    float distance = Point.distanceTo(loc, centerLoc);

    if (combatStartLoc != null)
        distance = Point.distanceTo(loc, combatStartLoc);

    if (Log.loggingDebug)
        Log.debug("CombatBehavior.checkDistance: distance from centerLoc=" + centerLoc + " or combatStartLoc=" + combatStartLoc + " is " + distance + " chaseDistance=" + chaseDistance + " evade=" + evade + " command=" + command);
    if ((distance > chaseDistance) && (!evade) && (selectedBehavior != null && !selectedBehavior.ignoreChaceDistance)) {
        if (Log.loggingDebug)
            Log.debug("CombatBehavior.checkDistance: Mob exceeded max distance: " + chaseDistance);
        for (OID playerOid : threatMap.keySet()) {
            if (Log.loggingDebug)
                Log.debug("CombatBehavior.checkDistance: send aggressive=false to " + playerOid);
            TargetedPropertyMessage newPropMsg = new TargetedPropertyMessage(playerOid, obj.getOid());
            newPropMsg.setProperty("aggressive", false);
            Engine.getAgent().sendBroadcast(newPropMsg);
        }
        if (Log.loggingDebug)
            Log.debug("CombatBehavior.checkDistance: clear threatMap=" + threatMap + " damageMap=" + damageMap + " tagOwner=" + tagOwner);
        threatMapTemp.putAll(threatMap);
        threatMap.clear();
        damageMap.clear();
        tagOwner = null;
        attackTarget(null);
        // Set evade to true for 10 seconds
        Log.debug("CombatBehavior.checkDistance: Setting evade=true.");
        Prometheus.registry().counter("combat_behavior", "method", "chase_distance").increment();

        evade = true;
        inCombat = false;
        CombatClient.sendResetAttacker(obj.getOid());
        Log.debug("CombatBehavior.checkDistance: Evade set to true.");
        CombatClient.setCombatInfoState(obj.getOid(), CombatInfo.COMBAT_STATE_RESET);
        if (deactivateOutOfCombat) {
            // Despawn this mob
            PropertyMessage propMsg = new PropertyMessage(obj.getOid());
            propMsg.setProperty("despawn", true);
            propMsg.setProperty("facing", null);
            Engine.getAgent().sendBroadcast(propMsg);
            return;
        } else {
            setTarget(null);
        }
    } else if (evade) {
        Log.debug("CombatBehavior.checkDistance: Evade is true. Resetting evade state.");
        evade = false;
        CombatClient.setCombatInfoState(obj.getOid(), "");
        Log.debug("CombatBehavior.checkDistance: Evade set to false.");
        // Now see if there is anyone else to aggro
        threatUpdated();
    } else {
        if (scheduledCheck == null) {
            Log.debug("CombatBehavior.checkDistance: Scheduling next check in 200ms.");
            safeScheduleCheckDistance(cdt, 200, TimeUnit.MILLISECONDS);
        }
    }
}

    /**
     * A runnable class to check if the mob has chased a target too far from its original location.
     * @author Andrew
     *
     */
    class CheckDistanceTravelled implements Runnable {
        public void run() {
            if(Log.loggingDebug)
                Log.debug("CheckDistanceTravelled Start instance="+obj.getInstanceOid()+" "+obj.getOid());
            if (activated == false) {
                return;
            }
            checkDistance();
        }
    }
    
	void sendCommand() {
		sendCommand = null;
		InterpolatedWorldNode wnode = obj.getWorldNode();
		Point loc = null;
		if (wnode != null) {
			loc = wnode.getCurrentLoc();
		}
		if(Log.loggingDebug)Log.debug("sendCommand "+command);
		if (command.equals("GoReturn")) {
			if(Log.loggingDebug)Log.debug("CombatBehavior.sendCommand GoReturn " + obj.getOid() + " loc=" + loc + " dist="+(loc!=null && combatStartLoc !=null?Point.distanceTo(loc, combatStartLoc):"bd")+ " combatStartLoc=" + combatStartLoc + " speed=" + speed);
			if(combatStartLoc!=null) {
				Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, combatStartLoc, speed));
			} else {
				if(Log.loggingDebug)Log.debug("CombatBehavior.sendCommand GoReturn " + obj.getOid() + " combatStartLoc cant be null");
				
			}
			// combatStartLoc=null;
		} else if (command.equals("Goto")) {
			if (Log.loggingDebug)
				Log.debug("Goto centerLoc=" + centerLoc);

			if(Log.loggingDebug)Log.debug("CombatBehavior.sendCommand Goto " + obj.getOid() + " loc=" + loc+ " dist="+(loc!=null && centerLoc !=null?Point.distanceTo(loc, centerLoc):"bd") + " centerLoc=" + centerLoc + " speed=" + speed);
			Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, centerLoc, speed));
		} else if (command.equals("Follow")) {
			if (currentTarget != null) {
				if(Log.loggingDebug)Log.debug("CombatBehavior.sendCommand Follow " + obj.getOid() + " loc=" + loc + " currentTarget=" + currentTarget + " speed=" + speed + " attackdist=" + (attackDistance + hitBoxRange) + " attackDistance="
						+ attackDistance + " hitBoxRange=" + hitBoxRange);
				EntityHandle eHandle = new EntityHandle(currentTarget);
				Engine.getAgent().sendBroadcast(new BaseBehavior.FollowCommandMessage(obj, eHandle, speed, attackDistance + hitBoxRange));
			}
		}
	}

    class SendCommand implements Runnable {
        public void run() {
            if (activated == false) {
                return;
            }
            if(Log.loggingDebug)Log.debug("SendCommand Start instance="+obj.getInstanceOid()+" "+obj.getOid());
            sendCommand();

        }
    }
    
    class CheckThreatUpdated implements Runnable {
        public void run() {
            if(Log.loggingDebug)Log.debug("CheckThreatUpdated Start instance="+obj.getInstanceOid()+" "+obj.getOid());
            if (activated == false) {
                return;
            }
            threatUpdated();
            Log.debug("CheckThreatUpdated End");
        }
    }
    
    
    public void setMovementSpeed(float speed) { 
        if (Log.loggingDebug)
            Log.debug("SPEED: mob set speed to: " + speed);
        this.speed = speed; 
    }
    public float getMovementSpeed() { return speed; }
    protected float speed = 6f;
    public void setDefaultMovementSpeed(float speed) { this.defaultSpeed = speed; }
    protected Float defaultSpeed = 6f;

    public void setReactionRadius(int radius) { reactionRadius = radius; }
    public int getReactionRadius() { return reactionRadius.intValue(); }
    protected Integer reactionRadius = 70;
    
    public void setCenterLoc(Point loc) {centerLoc = loc; }
    public Point getCenterLoc() { return centerLoc; }
    protected Point centerLoc = null;
    public Point combatStartLoc = null;
    protected Point combatStartTargetLoc = null;
      
    public void setchaseDistance(int distance) { chaseDistance = distance; }
    public int getchaseDistance() { return chaseDistance; }
    int chaseDistance = 60; // How far the mob will chase a player in meters
    
    CheckDistanceTravelled cdt = new CheckDistanceTravelled();
    
    public void setAggroRange(int radius) { aggroRange = radius; }
    public int getAggroRange() { return aggroRange; }
    int aggroRange = 5;
    
    public void setHitBoxRange(float radius) { hitBoxRange = radius; }
    public float getHitBoxRange() { return hitBoxRange; }
    float hitBoxRange = 2;
    
    public void setAttackDistance(float radius) { attackDistance = radius; }
    public float getAttackDistance() { return attackDistance; }
    float attackDistance = 2;
    
    public void setSendLinkedAggro(boolean value) { sendLinkedAggro = value; }
    public boolean getSendLinkedAggro() { return sendLinkedAggro; }
   
    public void setReciveLinkedAggro(boolean value) { getLinkedAggro = value; }
    public boolean getReciveLinkedAggro() { return getLinkedAggro; }
   
    public void setLinkedAggroRadius(int radius) { linkedAggroRadius = radius; }
    public int getLinkedAggroRadius() { return linkedAggroRadius; }
   
      /**
     * Add the amount of threat for the given oid to the threatMap. If the oid already
     * has an entry then we just add to it.
     * @param targetOid
     * @param threatAmount
     */
    protected void addTargetToThreatMap(OID targetOid, int threatAmount) {
        if (Log.loggingDebug)
                Log.debug("THREAT: adding " + targetOid + " to " + obj.getOid() + "'s threat map with threatAmount: " + threatAmount);
        if (targetOid != null) {
            AtomicBoolean newTarget = new AtomicBoolean(); 
            threatMap.compute(targetOid, (key, oldValue) -> {
                newTarget.set(oldValue == null);
                return oldValue == null ? threatAmount : oldValue + threatAmount; 
            });
            if (newTarget.get()) {
                subscribeForTarget(targetOid);
                TargetedPropertyMessage propMsg = new TargetedPropertyMessage(targetOid, obj.getOid());
                propMsg.setProperty("aggressive", true);
                if (Log.loggingDebug)
                    Log.debug("CombatBehavior.checkDistance: send aggressive=true to "+targetOid);
                
                Engine.getAgent().sendBroadcast(propMsg);
                Serializable owner = EnginePlugin.getObjectProperty(targetOid, WorldManagerClient.NAMESPACE, "petOwner");
                if(Log.loggingDebug)
                    Log.debug("CombatBehavior.addTargetToThreatMap: check pet owner="+owner);
                if(owner!=null) {
                    OID ownerOId = (OID)owner;
                    threatMap.compute(ownerOId, (key, oldValue) -> {
                        return oldValue == null ? threatAmount : oldValue + threatAmount; 
                    });
                    propMsg = new TargetedPropertyMessage(ownerOId, obj.getOid());
                    if (Log.loggingDebug)
                            Log.debug("CombatBehavior.checkDistance: send aggressive=true to "+ownerOId);
                    
                    propMsg.setProperty("aggressive", true);
                    Engine.getAgent().sendBroadcast(propMsg);
                }
                /*SubjectFilter filter = new SubjectFilter(targetOid);
                filter.addType(PropertyMessage.MSG_TYPE_PROPERTY);
                filter.addType(WorldManagerClient.MSG_TYPE_DESPAWNED);
                targetSubs.put(targetOid, Engine.getAgent().createSubscription(filter, this));*/
                AgisMobPlugin.addMobToAlertOnDeath(targetOid, this);
                
            }
            if (!evade && (currentTarget == null || threatAmount > 0))
                threatUpdated();
        }
    }
    
    /**
     * Removes the target from the threatMap, then runs the threatUpdated() function again. Also removes the target
     * from the damageMap.
     * @param targetOid
     */
    protected boolean removeTargetFromThreatMap(OID targetOid) {
        if (Log.loggingDebug)
                Log.debug("THREAT: removing target: " + targetOid + " from threatMap of: " + obj.getOid());
        if (targetOid != null) { 
            // Also remove target from damageMap
            damageMap.remove(targetOid);
            unsubscribeForTarget(targetOid);
            if (threatMap.remove(targetOid) != null) {
                //Engine.getAgent().removeSubscription(targetSubs.get(targetOid));
                //targetSubs.remove(targetOid);
                threatUpdated();
                if (tagOwner != null && tagOwner.equals(targetOid)) {
                    tagOwner = null;
                    if (Log.loggingDebug)
                        Log.debug("TAG: tagOwner removed: " + targetOid);
                }
                
                TargetedPropertyMessage newPropMsg = new TargetedPropertyMessage(targetOid, obj.getOid());
                if (Log.loggingDebug)
                        Log.debug("CombatBehavior.checkDistance: send aggressive=false to "+targetOid);
                
                newPropMsg.setProperty("aggressive", false);
                Engine.getAgent().sendBroadcast(newPropMsg);
                
                return false;
            }
            return true;
        }
        return false;
    }
    
    /**
     * Use when a player has died to set threat to -1.
     * @param targetOid
     * @param amount
     */
    public void setTargetThreat(OID targetOid, int amount) {
    	if(Log.loggingDebug)Log.debug("CombatBehavior.setTargetThreat: obj="+obj.getOid()+" targetOid="+targetOid+" amount="+amount);
        threatMap.put(targetOid, amount);
        threatUpdated();
    }
    
    /**
     * Run after every threat level change, this function works out who the mob should be attacking 
     * based on which target has the most threat.
     */
    void threatUpdated() {
        long tStart = System.nanoTime();
       // lock.lock();
        try {
            threatUpdatedInternal();
        } finally {
           // lock.unlock();
        }
        threatUpdateTimer.record(Duration.ofNanos(System.nanoTime() - tStart));
    }

    void threatUpdatedInternal() {
        if (evade)
            return;
        OID currentTarget = this.currentTarget;
        
        if(Log.loggingDebug)Log.debug("CombatBehavior.threatUpdated: THREAT: threat updated for: " + obj.getOid() + " with target count: " + threatMap.size());

		if (selectedBehavior != null && selectedBehavior.behaviorType == 3) {
			InterpolatedWorldNode wn = obj.getWorldNode();
			if (defendedTarget != null) {
				Entity e = EntityManager.getEntityByNamespace(defendedTarget, Namespace.MOB);
				if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid " + defendedTarget + " isMob? " + e.getType().isMob());

				ObjectStub os = (ObjectStub) e;
				OID DefendAttackTarget = null;
				boolean foundCombat =false;
				for (Behavior behav : os.getBehaviors()) {
					if (Log.loggingDebug)Log.debug("threatUpdatedInternal: doid " + defendedTarget + " behav " + behav);
					if (behav instanceof CombatBehavior) {
						CombatBehavior _cb = ((CombatBehavior) behav);
						DefendAttackTarget = _cb.currentTarget;
						foundCombat =true;
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.threatUpdatedInternal defend set target " + DefendAttackTarget);
						if (DefendAttackTarget != null) {
							if (!DefendAttackTarget.equals(currentTarget) || currentTarget == null) {
								if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid=" + obj.getOid() + " defendedTarget " + defendedTarget + " DefendAttackTarget" + DefendAttackTarget + " != currentTarget " + currentTarget);
								if (lastSetDefendTarget + AgisMobPlugin.MOB_COMBAT_BEHAVIOR_DEFEND_CHANGE_TARGET_INTERVAL < System.currentTimeMillis()) {
									if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid= "+ obj.getOid() + " defendedTarget " + defendedTarget + " timeout reached");
									AtomicBoolean newTarget = new AtomicBoolean();
									threatMap.compute(DefendAttackTarget, (key, oldValue) -> {
										newTarget.set(oldValue == null);
										return oldValue == null ? 1 : oldValue + 1;
									});
									if (newTarget.get()) {
										subscribeForTarget(DefendAttackTarget);
										TargetedPropertyMessage propMsg = new TargetedPropertyMessage(DefendAttackTarget, obj.getOid());
										propMsg.setProperty("aggressive", true);
										if (Log.loggingDebug)
											Log.debug("CombatBehavior.threatUpdatedInternal: send aggressive=true to " + DefendAttackTarget);

										Engine.getAgent().sendBroadcast(propMsg);
									}
									lastSetDefendTarget = System.currentTimeMillis();
									attackTarget(DefendAttackTarget);
									return;
								} else {
									if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid=" + obj.getOid() +" defendedTarget " + defendedTarget + " cant change defend target because timeout not reached"); 
									AtomicBoolean newTarget = new AtomicBoolean();
									threatMap.compute(DefendAttackTarget, (key, oldValue) -> {
										newTarget.set(oldValue == null);
										return oldValue == null ? 1 : oldValue + 1;
									});
									if (newTarget.get()) {
										subscribeForTarget(DefendAttackTarget);
										TargetedPropertyMessage propMsg = new TargetedPropertyMessage(DefendAttackTarget, obj.getOid());
										propMsg.setProperty("aggressive", true);
										if (Log.loggingDebug)
											Log.debug("CombatBehavior.threatUpdatedInternal: send aggressive=true to " + DefendAttackTarget);
										Engine.getAgent().sendBroadcast(propMsg);
									}
									return;
								}
							} else {
								if (Log.loggingDebug)Log.debug("threatUpdatedInternal: doid " + defendedTarget + " same target ");
								threatMap.compute(DefendAttackTarget, (key, oldValue) -> {
									return oldValue == null ? 1 : oldValue + 1;
								});
								// attackTarget(DefendAttackTarget);
								return;
							}
						}
					}
				} // for
				
				if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid=" + obj.getOid() + " doid " + defendedTarget + " foundCombat="+foundCombat+" Defend"
						+ "AttackTarget="+DefendAttackTarget+" lastSetDefendTarget=" + lastSetDefendTarget +
						" MOB_COMBAT_BEHAVIOR_DEFEND_CHANGE_TARGET_INTERVAL="+ AgisMobPlugin.MOB_COMBAT_BEHAVIOR_DEFEND_CHANGE_TARGET_INTERVAL+" ");
				
					if (currentTarget==null||(currentTarget!=null && lastSetDefendTarget + AgisMobPlugin.MOB_COMBAT_BEHAVIOR_DEFEND_CHANGE_TARGET_INTERVAL < System.currentTimeMillis())) {
						if (wn != null) {
							Point mloc = wn.getCurrentLoc();
							List<OID> list = AgisMobPlugin.getFriendly(obj.getOid());
							OID nearestTarget = null;
							OID nearestTargetCurrentTarget = null;
							float nearestTargetDist = Float.MAX_VALUE;

							for (OID oid : list) {
								if (oid.equals(defendedTarget)) {
									continue;
								}
								e = EntityManager.getEntityByNamespace(oid, Namespace.MOB);
								if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid " + oid + " isMob? " + e.getType().isMob());

								os = (ObjectStub) e;
								for (Behavior behav : os.getBehaviors()) {
									if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid " + oid + " behav " + behav);
									if (behav instanceof CombatBehavior) {
										CombatBehavior _cb = ((CombatBehavior) behav);
										List<Integer> tags = _cb.getTags();
										if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid " + oid + " tags " + tags);

										if (((CombatBehavior) behav).getTags().contains(selectedBehavior.mobTag)) {
											if (_cb.currentTarget != null) {

												EntityHandle mHandle = new EntityHandle(oid);
												ObjectStub tObj = (ObjectStub) mHandle.getEntity(Namespace.MOB);
												Point tLoc = tObj.getWorldNode().getLoc();
												float dist = Point.distanceTo(mloc, tLoc);
												if (dist < nearestTargetDist) {
													nearestTargetCurrentTarget = _cb.currentTarget;
													nearestTargetDist = dist;
													nearestTarget = oid;
												}
											}
										}
									} else {
										if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid " + oid + " no combat behavior ");
									}
								}
							}

							// for (OID oid : locations.keySet()) {
							if (nearestTarget != null) {
								subscribeForTarget(nearestTarget);
								//defendedTarget = nearestTarget;
								AtomicBoolean newTarget = new AtomicBoolean();
								threatMap.compute(nearestTargetCurrentTarget, (key, oldValue) -> {
									newTarget.set(oldValue == null);
									return oldValue == null ? 1 : oldValue + 1;
								});
								if (newTarget.get()) {
									subscribeForTarget(nearestTargetCurrentTarget);
									TargetedPropertyMessage propMsg = new TargetedPropertyMessage(nearestTargetCurrentTarget, obj.getOid());
									propMsg.setProperty("aggressive", true);
									if (Log.loggingDebug)
										Log.debug("CombatBehavior.threatUpdatedInternal: send aggressive=true to " + nearestTargetCurrentTarget);

									Engine.getAgent().sendBroadcast(propMsg);
								}
								lastSetDefendTarget = System.currentTimeMillis();
								attackTarget(nearestTargetCurrentTarget);
								return;
							}
						} else {
							if (Log.loggingDebug)Log.debug("threatUpdatedInternal: got null wnode during check ability for oid: " + obj.getOid());
						}

					} else {
						if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid=" + obj.getOid() + " cant change defend target because timeout not reached");
					}

				

			} else {

				if (wn != null) {
					Point mloc = wn.getCurrentLoc();
					List<OID> list = AgisMobPlugin.getFriendly(obj.getOid());
					OID nearestTarget = null;
					float nearestTargetDist = Float.MAX_VALUE;
					OID nearestTargetCurrentTarget = null;
					for (OID oid : list) {
						Entity e = EntityManager.getEntityByNamespace(oid, Namespace.MOB);
						if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid " + oid + " isMob? " + e.getType().isMob());

						ObjectStub os = (ObjectStub) e;
						for (Behavior behav : os.getBehaviors()) {
							if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid " + oid + " behav " + behav);
							if (behav instanceof CombatBehavior) {
								CombatBehavior _cb = ((CombatBehavior) behav);
								List<Integer> tags = _cb.getTags();
								if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid " + oid + " tags " + tags);

								if (((CombatBehavior) behav).getTags().contains(selectedBehavior.mobTag)) {
									if (_cb.currentTarget != null) {
										EntityHandle mHandle = new EntityHandle(oid);
										ObjectStub tObj = (ObjectStub) mHandle.getEntity(Namespace.MOB);
										Point tLoc = tObj.getWorldNode().getLoc();
										float dist = Point.distanceTo(mloc, tLoc);
										if (dist < nearestTargetDist) {
											nearestTargetCurrentTarget = _cb.currentTarget;
											nearestTargetDist = dist;
											nearestTarget = oid;
										}
									}
								}
							} else {
								if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid " + oid + " no combat behavior ");
							}
						}
					}
					if (Log.loggingDebug)Log.debug("threatUpdatedInternal: oid=" + obj.getOid() + " doid " + defendedTarget +" Defend"
							+ "nearestTargetCurrentTarget="+nearestTargetCurrentTarget+" lastSetDefendTarget=" + lastSetDefendTarget +
							" MOB_COMBAT_BEHAVIOR_DEFEND_CHANGE_TARGET_INTERVAL="+ AgisMobPlugin.MOB_COMBAT_BEHAVIOR_DEFEND_CHANGE_TARGET_INTERVAL+" ");
					// for (OID oid : locations.keySet()) {
					if (nearestTarget != null) {
						subscribeForTarget(nearestTarget);
						AtomicBoolean newTarget = new AtomicBoolean();
						threatMap.compute(nearestTargetCurrentTarget, (key, oldValue) -> {
							newTarget.set(oldValue == null);
							return oldValue == null ? 1 : oldValue + 1;
						});
						if (newTarget.get()) {
							subscribeForTarget(nearestTargetCurrentTarget);
							TargetedPropertyMessage propMsg = new TargetedPropertyMessage(nearestTargetCurrentTarget, obj.getOid());
							propMsg.setProperty("aggressive", true);
							if (Log.loggingDebug)
								Log.debug("CombatBehavior.threatUpdatedInternal: send aggressive=true to " + nearestTargetCurrentTarget);

							Engine.getAgent().sendBroadcast(propMsg);
						}
						lastSetDefendTarget = System.currentTimeMillis();
						attackTarget(nearestTargetCurrentTarget);
						return;
					}
				} else {
					if (Log.loggingDebug)Log.debug("threatUpdatedInternal: got null wnode during check ability for oid: " + obj.getOid());
				}

			}

		}
		 if(Log.loggingDebug)
			 Log.debug("CombatBehavior.threatUpdated obj="+obj.getOid()+" currentTarget is "+currentTarget);
        if (threatMap.isEmpty() && currentTarget == null) {
            return;
        }
        if (threatMap.isEmpty()) {
            attackTarget(null);
            return;
        }
        int stealth_perception = 0;
        if (CombatPlugin.PERCEPTION_STEALTH_STAT != null) {
            Map<String, Serializable> props = propMap.get(obj.getOid());
            if (props != null && props.containsKey(CombatPlugin.PERCEPTION_STEALTH_STAT)) {
                stealth_perception = (int) props.get(CombatPlugin.PERCEPTION_STEALTH_STAT);
            } else {
                LinkedList<String> param = new LinkedList<String>();
                // param.add(CombatPlugin.STEALTH_STAT);
                param.add(CombatPlugin.PERCEPTION_STEALTH_STAT);
                HashMap<String, Serializable> targetParams = CombatClient.getCombatInfoParams(obj.getOid(), param);
                propMap.put(obj.getOid(), targetParams);
            }
            // stealth_perception =
            // (int)propMap.get(obj.getOid()).get(CombatPlugin.PERCEPTION_STEALTH_STAT);
        }
        if (Log.loggingDebug)
            Log.debug("CombatBehavior.threatUpdated: stealth_perception = " + stealth_perception);
        // Update the current target to the person with the highest threat
        OID highestThreatOid = currentTarget != null && threatMap.containsKey(currentTarget) ? currentTarget : null;
        if (CombatPlugin.STEALTH_STAT != null && currentTarget != null) {
            int stealth = 0;
            Map<String, Serializable> props = propMap.get(currentTarget);
            if (props != null && props.containsKey(CombatPlugin.STEALTH_STAT)) {
                stealth = (int) props.get(CombatPlugin.STEALTH_STAT);
                if (Log.loggingDebug)
                    Log.debug("CombatBehavior.threatUpdated: currentTarget=" + currentTarget + " stealth = " + stealth);
            } else {
                if (Log.loggingDebug)
                    Log.debug("CombatBehavior.threatUpdated: dont have stealth get form combat");

                // don have stat in prop map
                LinkedList<String> param = new LinkedList<String>();
                param.add(CombatPlugin.STEALTH_STAT);
                // param.add(CombatPlugin.PERCEPTION_STEALTH_STAT);
                try {
                    HashMap<String, Serializable> targetParams = CombatClient.getCombatInfoParams(currentTarget, param);
                    if (targetParams != null) {
                        propMap.put(currentTarget, targetParams);
                        stealth = (int) targetParams.get(CombatPlugin.STEALTH_STAT);
                        if (Log.loggingDebug)
                            Log.debug("CombatBehavior.threatUpdated: stealth = " + stealth);
                    }
                } catch (NoRecipientsException e) {
                }
            }
            if (Log.loggingDebug)
                    Log.debug("CombatBehavior.threatUpdated: currentTarget=" + currentTarget + " stealth = " + stealth);
            if (stealth_perception < stealth) {
                if (Log.loggingDebug)
                    Log.debug("CombatBehavior.threatUpdated: currentTarget=" + currentTarget + " in the stealth mode reset highestThreatOid");
                highestThreatOid = null;
                currentTarget = null;
            }

        }

        
        int highestThreat = -1;
        float highestThreatDistance = Float.MAX_VALUE;
        if (currentTarget != null && threatMap.containsKey(currentTarget)) {
            highestThreat = threatMap.get(currentTarget);
            highestThreatDistance = getDistanceToTarget(currentTarget);
        }
      
        Object[] threatArray = threatMap.keySet().toArray();
        
        for (Object target : threatArray) {
            OID targetOid = (OID)target;
            
            if (CombatPlugin.STEALTH_STAT != null && targetOid != null) {
                int stealth = 0;
                Map<String, Serializable> props = propMap.get(targetOid);
                if (props!=null && props.containsKey(CombatPlugin.STEALTH_STAT)) {
                    stealth = (int) props.get(CombatPlugin.STEALTH_STAT);
                    if (Log.loggingDebug)
                        Log.debug("CombatBehavior.threatUpdated: targetOid=" + targetOid + " stealth = " + stealth);
                } else {
                    // don have stat in prop map
                    LinkedList<String> param = new LinkedList<String>();
                    param.add(CombatPlugin.STEALTH_STAT);
                    // param.add(CombatPlugin.PERCEPTION_STEALTH_STAT);
                    try {
                        HashMap<String, Serializable> targetParams = CombatClient.getCombatInfoParams(targetOid, param);
                        if (targetParams != null) {
                            propMap.put(targetOid, targetParams);
                            stealth = (int) targetParams.get(CombatPlugin.STEALTH_STAT);
                        }else {
                            Log.warn("failed to get stealth stat");
                        }
                    } catch (NoRecipientsException e) {
                        threatMap.remove(targetOid);
                        continue;
                    } finally {
                        if (Log.loggingDebug)
                            Log.debug("CombatBehavior.threatUpdated: targetOid=" + targetOid + " stealth = " + stealth);
                    }
                }
                if (Log.loggingDebug)
                    Log.debug("CombatBehavior.threatUpdated: targetOid=" + targetOid + " stealth = " + stealth);
                if (stealth_perception < stealth) {
                    if (Log.loggingDebug)
                        Log.debug("CombatBehavior.threatUpdated: targetOid=" + targetOid + " in the stealth mode skip target ");
                    continue;
                }

            }
            
            Serializable gm = propMap.computeIfAbsent(obj.getOid(), __ -> new ConcurrentHashMap<>()).get("gm");
			if (gm == null) {
				try {
					gm = EnginePlugin.getObjectProperty(targetOid, WorldManagerClient.NAMESPACE, "gm");
				} catch (Exception e) {
					Log.debug("CombatBehavior.threatUpdated: cant get gm param");
				}
				if(gm == null)
					gm = false;
				propMap.computeIfAbsent(obj.getOid(), __ -> new ConcurrentHashMap<>()).put("gm", gm);
			}
			if (gm != null) {
				if ((boolean) gm) {
					Log.debug("CombatBehavior.threatUpdated: target have gm status not attack");
					continue;
				}
			}

         /* 
            LinkedList<String> param = new LinkedList<String>();
            param.add(CombatPlugin.STEALTH_STAT);
            param.add(CombatPlugin.PERCEPTION_STEALTH_STAT);
            HashMap<String,Serializable> inviteeParams = CombatClient.getCombatInfoParams(targetOid, param );
            
            */
            if (Log.loggingDebug)
                    Log.debug("CombatBehavior.threatUpdated: targetOid=" + targetOid + "  currentTarget="+currentTarget);
            
            // Threat level was already set for current target
            if (targetOid.equals(currentTarget))
                continue;
            if (Log.loggingDebug)
                Log.debug("CombatBehavior.threatUpdated: targetOid=" + targetOid + "  threatMap.get(targetOid) ="+threatMap.get(targetOid) );
                // Ignore targets with less than 0 threat
            int targetThreat = threatMap.getOrDefault(targetOid, -1);
            if (targetThreat < 0) {
                continue;
            }
            
            // If there is no highest threat target, set this target as it
            if (highestThreatOid == null) {
                float distance = getDistanceToTarget(targetOid);
                if (Log.loggingDebug)
                    Log.debug("CombatBehavior.threatUpdated: targetOid=" + targetOid + "  distance="+distance+" aggroRange="+aggroRange);
                if (distance == -1 || Float.isNaN(distance)|| (distance > aggroRange && threatMap.get(targetOid) <= 0)) {
                    continue;
                }
                if(Log.loggingDebug)Log.debug("CombatBehavior.threatUpdated: obj="+obj.getOid()+" targetOid="+targetOid+" distance="+distance+" chaseDistance="+chaseDistance+" behaviorId="+behaviorId);
				if (distance > chaseDistance && behaviorId < 1) {
					removeTargetFromThreatMap(targetOid);
					continue;
				}
                highestThreatOid = targetOid;
                highestThreat = targetThreat;
                highestThreatDistance = distance;
                continue;
            }
            
            // Also get distance from the mob
            int threatNeeded = highestThreat;
            float distance = getDistanceToTarget(targetOid);
            if (Log.loggingDebug)
                    Log.debug("CombatBehavior.threatUpdated: targetOid=" + targetOid + " | distance="+distance+" aggroRange="+aggroRange);
            if (distance == -1 || (distance > aggroRange && threatMap.get(targetOid) <= 0)) {
                continue;
            }
            if(Log.loggingDebug)Log.debug("CombatBehavior.threatUpdated: obj="+obj.getOid()+" targetOid="+targetOid+" distance="+distance+" chaseDistance="+chaseDistance+" behaviorId="+behaviorId);
			if (distance > chaseDistance && behaviorId < 1) {
				removeTargetFromThreatMap(targetOid);
				continue;
			}
            
            if (distance < AgisMobPlugin.MOB_AGGRO_CLOSE_RANGE_CHECK) {
                // Target is melee range
                if (highestThreatOid.equals(currentTarget)) {
                    // Need a bit more threat than current target to overtake it
                    highestThreat *= AgisMobPlugin.MOB_AGGRO_MELEE_THREAT_THRESHOLD;
                }
            } else {
                // Target is outside melee range
                if (highestThreatOid.equals(currentTarget)) {
                    // Need a bit more threat than current target to overtake it
                    if (highestThreatDistance < AgisMobPlugin.MOB_AGGRO_CLOSE_RANGE_CHECK) {
                        // Current highest threat is within melee range
                        highestThreat *= AgisMobPlugin.MOB_AGGRO_RANGED_THREAT_THRESHOLD;
                    } else {
                        // Current highest threat is also outside melee range
                        highestThreat *= AgisMobPlugin.MOB_AGGRO_MELEE_THREAT_THRESHOLD;
                    }
                } else if (highestThreatDistance < AgisMobPlugin.MOB_AGGRO_CLOSE_RANGE_CHECK) {
                    // As the target is still outside melee range and the highest threat is inside, it should still require extra threat
                    highestThreat *= AgisMobPlugin.MOB_AGGRO_RANGED_THREAT_THRESHOLD;
                }
            }
            
            if (targetThreat > threatNeeded) {
                highestThreatOid = targetOid;
                highestThreat = targetThreat;
                highestThreatDistance = distance;
            } else if (targetThreat == threatNeeded && distance < highestThreatDistance) {
                // If the threat level is the same, check if the new target is closer
                highestThreatOid = targetOid;
                highestThreat = targetThreat;
                highestThreatDistance = distance;
            }
            //Log.debug("THREAT: Comparing threat: " + threatMap.get(playerOid) + " against highest: " + highestThreat);
        }
        if (Log.loggingDebug)
            Log.debug("CombatBehavior.threatUpdated: THREAT: highest threat: " + highestThreatOid + " against currentTarget: " + currentTarget);
        if (highestThreatOid == null && !goreturn) {
            attackTarget(null);
        } else if (highestThreatOid != currentTarget) {
            if(currentTarget==null) {
                
                sendLinkedAggro(highestThreatOid);
            }
            currentTarget = highestThreatOid;
            
            attackTarget(currentTarget);
        }
    }
    
    private float getDistanceToTarget(OID targetOid) {
        try {
            EntityWithWorldNode perceiver = (EntityWithWorldNode) EntityManager.getEntityByNamespace(targetOid, Namespace.MOB);
            if (perceiver == null) {
                return Float.MAX_VALUE;
            }
            InterpolatedWorldNode perceiverNode = perceiver.getWorldNode();
            if (perceiverNode == null) {
                Log.warn("REACT: percieverNode is null for: " + perceiver.getOid());
                return Float.MAX_VALUE;
            }
            Point perceiverLocation = perceiverNode.getLoc();
            if (checkYDistance) {
            /*  if (Math.abs(perceiverLocation.getY() - obj.getWorldNode().getLoc().getY()) > 5) {
                    return -1;
                }*/
                return Point.distanceTo(perceiverLocation, obj.getWorldNode().getLoc());
            } else {
                return Point.distanceToXZ(perceiverLocation, obj.getWorldNode().getLoc());
            }
        } catch (Exception e) {
            if (Log.loggingDebug)
                Log.debug("CombatBehavior.getDistanceToTarget: "+obj+" Exception:"+e.getMessage()+e.getLocalizedMessage());
        return 999;
        }
    }
    
    /**
     * storage value of damage received from the attacker
     * @param targetOid (attacker)
     * @param amount of damage
     */
    public void addDamageToMap(OID targetOid, int amount) {
        damageMap.compute(targetOid, (__, previous) -> {
           if (previous != null) {
               return previous + amount;
           }
           return amount;
        });
    }

    public boolean getInCombat() {
        return inCombat;
    }
    
    public void setDeactivateOutOfCombat(boolean deactivate) {
        deactivateOutOfCombat = deactivate;
    }

    public void setLootTables(HashMap<Integer, MobLootTable> lootTables) {
        this.lootTables.clear();
        this.lootTables.putAll(lootTables);
    }
    
    public void setTags(String t) {
		for (String s : t.split(",")) {
			if (s != null && s.length() > 0) {
				if (Log.loggingDebug)Log.debug("CombatBehavior.setTags add " + s);
				tags.add(Integer.parseInt(s));
			} else {
				if (Log.loggingDebug)Log.debug("CoombatBehavior.setTags not add " + s);
			}
		}
	}
    
	public List<Integer> getTags() {
		if (Log.loggingDebug)Log.debug("combatBehavior.getTags  " + tags);
		return tags;

	}

	private final Map<OID, Integer> threatMap = new ConcurrentHashMap<>();
	private final Map<OID, Integer> threatMapTemp = new ConcurrentHashMap<>();
	private final Map<OID, Integer> damageMap = new ConcurrentHashMap<OID, Integer>();
	private final Map<Integer, MobLootTable> lootTables = new ConcurrentHashMap<Integer, MobLootTable>();
	private final Map<OID, Map<String, Serializable>> propMap = new ConcurrentHashMap<>();
	private final Set<OID> reactionTargets = ConcurrentHashMap.newKeySet();

	private List<AbilityGroup> abilities = new ArrayList<AbilityGroup>();
	boolean nomove = false;
	public boolean evade = false;

	boolean inCombat = false;
	public boolean isDead = false;
	public boolean goreturn = false;
	boolean deactivateOutOfCombat = false;
	protected volatile OID currentTarget = null;
	protected OID lastDamage = null;
	protected OID tagOwner = null;
	protected boolean activated = false;
	private static final long serialVersionUID = 1L;
	private Point gotoLoc = null;
	boolean checkYDistance = true;
	private static final Timer threatUpdateTimer = io.micrometer.core.instrument.Timer.builder("threat_update").register(Prometheus.registry());

	private ScheduledFuture<?> scheduledCheck = null;
	private ScheduledFuture<?> checkThreatTask;
	private ScheduledFuture<?> behaviorThreatTask;
	private ScheduledFuture<?> moveThreatTask;
	private ScheduledFuture<?> abilityThreatTask;

	SendCommand sendCommand = null;
	String command = "";
	int commandDelayMilliseconds = 250;

	private Map<TriggerProfile.Type, Long> events = new ConcurrentHashMap<>();

	public List<CombatBehaviorEntry> behaviors = new ArrayList<CombatBehaviorEntry>();
	public CombatBehaviorEntry selectedBehavior = null;

	long lastTryUseAbility = 0;

    
	boolean sendLinkedAggro = false;
	boolean getLinkedAggro = false;
	int linkedAggroRadius = 1;

	Point fleeDestLoc = null;

	OID defendedTarget = null;
	/*
	 * This param is to determinate phase of flee behavior
	 */
	int phase = 0;

	ScheduledFuture<?> abilitySelectorTask = null;
	BehaviorLogicTask abilitySelector = new BehaviorLogicTask();

	public int behaviorId = -1;
	/**
	 * Tags defined for mob to deteminate in logic
	 */
	List<Integer> tags = new ArrayList<Integer>();
	List<OID> subscribed = new ArrayList<OID>();
	ConcurrentHashMap<OID, Float> defendTargets = new ConcurrentHashMap<OID, Float>();

	/**
	 * Param to store oid of items created by behaviors
	 */
	HashMap<Integer, OID> items = new HashMap<Integer, OID>();
	
	AbilityGroup selectedAbilityGroup = null;

	int selectedAbility = -1;

	boolean reloacatingForAbility = false;

	public long lastUseAbility = 0L;
	public int lastUsedAbility=-1;
	AbilityGroup lastUsedAbilityGroup = null;
	
	long lastSetDefendTarget = 0;

    /**
     * Definition of the ability conditions 
     *
     */
	public class AbilityGroup {
		//public AbilityGroup () {}
		public List<Integer> ids = new ArrayList<Integer>();
		public HashMap<Integer,Integer> priority = new HashMap<Integer,Integer>();
		public int sumPriority=0;
		
		public float minAbilityRangePercentage=50F;
		public float maxAbilityRangePercentage=50F;
		public List<GroupBehaviorConditions> conditions = new ArrayList<GroupBehaviorConditions>();
		
		/**
		 * Rando
		 * @return id of Ability 
		 */
		public int getAbility() {

			Map<String, State> cooldowns = new HashMap<String, State>(); 
			try {
				cooldowns = CombatClient.getPlayerCooldowns(obj.getOid());
			} catch (Exception e) {
				
			}
			List<Integer> availableAbilityIds = new ArrayList<Integer>();
			if (Log.loggingDebug)
				Log.debug("AbilityGroup getAbility: cooldowns=" + cooldowns);
			availableAbilityIds.clear();
			availableAbilityIds.addAll(ids);
			List<Integer> abilityIdsToRemove = new ArrayList<Integer>();
			for (int id : availableAbilityIds) {
				AgisAbility ab = Agis.AbilityManager.get(id);
				if (!Cooldown.checkReady(ab.getCooldownMap().values(), cooldowns)) {
					abilityIdsToRemove.add(id);
				}
			}
			if (Log.loggingDebug)
				Log.debug("AbilityGroup getAbility: abilityIdsRemove=" + abilityIdsToRemove);
			availableAbilityIds.removeAll(abilityIdsToRemove);
			sumPriority = 0;
			for (int id : availableAbilityIds) {
				sumPriority += priority.get(id);
			}
			if (Log.loggingDebug)
				Log.debug("AbilityGroup getAbility: availableAbilityIds=" + availableAbilityIds + " sumPriority=" + sumPriority);
			return drawAbility(cooldowns,availableAbilityIds);
		}

		/**
		 * Draw id of the ability from available
		 */
		int drawAbility(Map<String, State> cooldowns,List<Integer> availableAbilityIds) {
			Random rand = new Random();
			if (sumPriority > 0) {
				int index = rand.nextInt(sumPriority);
				int calcSumPriority = 0;
				if (Log.loggingDebug)
					Log.debug("AbilityGroup drawAbility: random index " + index + " from ids=" + ids + " priority " + priority + " sumPriority=" + sumPriority);

				if (Log.loggingDebug)
					Log.debug("AbilityGroup drawAbility: random index " + index + " from ids=" + ids + " priority " + priority + " sumPriority=" + sumPriority + " cooldowns=" + cooldowns);
				for (int id : availableAbilityIds) {
					if (index < calcSumPriority + priority.get(id)) {
						if (Log.loggingDebug)
							Log.debug("AbilityGroup drawAbility: selected ability " + id);
						// AgisAbility ab = Agis.AbilityManager.get(id);
						// if (Cooldown.checkReady(ab.getCooldownMap().values(), cooldowns)) {
						if (Log.loggingDebug)
							Log.debug("AbilityGroup drawAbility: selected ability " + id + " cooldown ok");
						return id;
						// } else {
						// if (Log.loggingDebug)
						// Log.debug("AbilityGroup getAbility: selected ability " + id + " cooldown not
						// ok");
						// return randomAbility(cooldowns);
						// }
					} else {
						calcSumPriority += priority.get(id);
						if (Log.loggingDebug)
							Log.debug("AbilityGroup drawAbility: no select ability " + id + "  add priority calcSumPriority=" + calcSumPriority);
					}

				}
			}
			if (Log.loggingDebug)
				Log.debug("AbilityGroup drawAbility: not selected ability End");
			return -1;
		}

		public String toString() {
			return "[AbilityGroup: ids=" + ids + " priority="+priority+" sumPriority="+sumPriority+" minAbilityRangePercentage:" + minAbilityRangePercentage + " maxAbilityRangePercentage:" + maxAbilityRangePercentage + " conditions:" + conditions+"]";
		}
		
		
		public boolean Calculate() {
			
			if(conditions.size()==0)
				return true;
			long tStart = System.nanoTime();			
			for (GroupBehaviorConditions gbc : conditions) {
				if(gbc.Calculate())
				 {
					if (Log.loggingDebug)Log.debug("AbilityGroup Calculate: GroupBehaviorConditions gbc" + gbc + " true");
					 long tEnd = System.nanoTime();
				        io.micrometer.core.instrument.Timer.builder("combat_behavior").tags("conditions_calculate","ability")
				                .register(Prometheus.registry()).record(Duration.ofNanos(tEnd - tStart));
					return true;
				} else {
					if (Log.loggingDebug)Log.debug("AbilityGroup Calculate: GroupBehaviorConditions gbc" + gbc + " false");
				}
			}
			if (Log.loggingDebug)Log.debug("AbilityGroup Calculate: End false");
			 long tEnd = System.nanoTime();
			 io.micrometer.core.instrument.Timer.builder("combat_behavior").tags("conditions_calculate","ability")
		                .register(Prometheus.registry()).record(Duration.ofNanos(tEnd - tStart));
			return false;
		}
	}
	
	
	/**
	 * Definition of group of behaviors conditions 
	 * Conditions in one group treat as AND separators and between groups of conditions treat as OR separator   
	 *
	 */
	
	public class GroupBehaviorConditions{
		public List<BehaviorCondition> conditions = new ArrayList<BehaviorCondition>();
		
		public boolean Calculate() {
			 long tStart = System.nanoTime();
			for(BehaviorCondition bc : conditions) {
				if(!bc.Calculate()) {
					if (Log.loggingDebug)Log.debug("GroupBehaviorConditions Calculate bc="+bc+" false");
					return false;
				}else {
					if (Log.loggingDebug)Log.debug("GroupBehaviorConditions Calculate bc="+bc+" true");
				}
			}
			return true;
		}
		
	}
	
	public class BehaviorCondition {
		public float distance = 0f;
		public boolean less = true;
		
		public String toString() {
			return "[BehaviorCondition: distance="+distance+" less="+less+"]";
		}

		public boolean Calculate() {

			if (currentTarget != null) {
				InterpolatedWorldNode wn = obj.getWorldNode();
				if (wn != null) {
					Point mloc = wn.getCurrentLoc();
					EntityHandle mHandle = new EntityHandle(currentTarget);
					ObjectStub tObj = (ObjectStub) mHandle.getEntity(Namespace.MOB);
					Point tLoc = tObj.getWorldNode().getLoc();
					float dist = Point.distanceTo(mloc, tLoc);
					if (dist > distance) {
						if (less) {
							return false;
						} else {
							return true;
						}
					} else {
						if (less) {
							return true;
						} else {
							return false;
						}
					}
				}

			}
			return false;
		}
		
	}

	public class StatBehaviorCondition extends BehaviorCondition{
		public String statName = "";
		public float statValue = 0;
		public boolean vitalityPercentage = true;
		/**
		 * 0 - Caster
		 * 1 - target
		 */
		public int target = 0;
		public String toString() {
			return "[StatBehaviorCondition: target="+target+" statName="+statName+" statValue="+statValue+" vitalityPercentage="+vitalityPercentage+"]";
		}
		public boolean Calculate() {
			if (Log.loggingDebug)Log.debug("StatBehaviorCondition Calculate");
		
			Map<String, Serializable> props = null;
			OID targetOid = null;
			if (target == 0) {
				targetOid = obj.getOid();
			} else if (target == 1 && currentTarget != null) {
				targetOid = currentTarget;
			}
			if (targetOid == null)
				return false;
			props = propMap.get(targetOid);
			if (props == null) {
				return false;
			}
			if (Log.loggingDebug)Log.debug("StatBehaviorCondition.Calculate: obj=" + obj.getOid() + " targetOid=" + currentTarget + " target=" + target + " props= " + props);
			try {
                boolean found = false;
                if (!props.containsKey(statName)) {
                    LinkedList<String> param = new LinkedList<String>();
                    param.add(statName);
                    AgisStatDef asd = CombatPlugin.statDefMap.get(statName);
                    if (asd instanceof VitalityStatDef) {
                        VitalityStatDef vsd = (VitalityStatDef) asd;
                        String maxStat = vsd.getMaxStat();
                        param.add(maxStat);
                    }

                    if (Log.loggingDebug)Log.debug("StatBehaviorCondition.Calculate: obj=" + obj.getOid() + " targetOid=" + currentTarget + " target=" + target + " send for stats param= " + param);

                    HashMap<String, Serializable> targetParams = CombatClient.getCombatInfoParams(targetOid, param);
                    if (Log.loggingDebug)Log.debug("StatBehaviorCondition.Calculate: obj=" + obj.getOid() + " targetOid=" + currentTarget + " target=" + target + " response targetParams=" + targetParams);
                    props.putAll(targetParams);
                    if (!propMap.containsKey(obj.getOid())) {
                        propMap.put(obj.getOid(), targetParams);
                    } else {
                        propMap.get(obj.getOid()).putAll(targetParams);
                    }
                }
                if (Log.loggingDebug)Log.debug("StatBehaviorCondition.Calculate: obj=" + obj.getOid() + " targetOid=" + currentTarget + " target=" + target + " stats props= " + props);
                if (props.containsKey(statName)) {
                    int value = (Integer) props.get(statName);
                    int maxValue = -1;
                    AgisStatDef asd = CombatPlugin.statDefMap.get(statName);
                    if (asd instanceof VitalityStatDef) {
                        VitalityStatDef vsd = (VitalityStatDef) asd;
                        String maxStat = vsd.getMaxStat();
                        if (props.containsKey(maxStat)) {
                            maxValue = (Integer) props.get(maxStat);

                        }
                    }
                    if (Log.loggingDebug)Log.debug("StatBehaviorCondition.Calculate: obj=" + obj.getOid() + " targetOid=" + currentTarget + " target=" + target + " vitalityPercentage=" + vitalityPercentage + " statValue=" + statValue + " less=" + less + " value=" + value + " maxValue=" + maxValue + " " + ((float) value / (float) maxValue * 100F));
                    if (vitalityPercentage && maxValue != -1) {
                        if (maxValue > 0) {
                            if (less) {
                                if (statValue > ((float) value / (float) maxValue * 100F)) {
                                    return true;
                                }
                            } else {
                                if (statValue < ((float) value / (float) maxValue * 100F)) {
                                    return true;
                                }

                            }
                        }
                    } else {
                        if (less) {
                            if (statValue > value) {
                                return true;
                            }
                        } else {
                            if (statValue < value) {
                                return true;
                            }

                        }
                    }

                }
            } catch (Exception e){
                Log.exception("StatBehaviorCondition.Calculate Exception " + this, e);
            }
			return false;
		}
	}
	
	
	public class EffectBehaviorCondition extends BehaviorCondition{
		/**
		 * Id of the effect tag
		 */
		public int  tagId = -1;
		/**
		 * 0 - Self
		 * 1 - Target
		 */
		public int target = 0;
		public boolean onTarget = true;
		public String toString() {
			return "[EffectBehaviorCondition: target="+target+" onTarget="+onTarget+" effect tagId="+tagId+"]";
		}
		@SuppressWarnings("unchecked")
		public boolean Calculate() {
			OID targetOid = null;
			if (target == 0) {
				targetOid = obj.getOid();
			} else if (target == 1 && currentTarget != null) {
				targetOid = currentTarget;
			}
			if (Log.loggingDebug)
				Log.debug("EffectBehaviorCondition.Calculate: oid=" + obj.getOid() + " target=" + targetOid);
			if (targetOid == null)
				return false;

			LinkedList<String> effects = (LinkedList<String>) EnginePlugin.getObjectProperty(targetOid, CombatClient.NAMESPACE, "effects");
			if (Log.loggingDebug)
				Log.debug("EffectBehaviorCondition.Calculate: effects=" + effects);
			for (String effect : effects) {
				String[] ef = effect.split(",");
				int effectID = Integer.parseInt(ef[0]);
				if (Log.loggingDebug)
					Log.debug("EffectBehaviorCondition.Calculate: effectID=" + effectID);
				AgisEffect ae = Agis.EffectManager.get(effectID);
				if (Log.loggingDebug)
					Log.debug("EffectBehaviorCondition.Calculate: AgisEffect=" + ae + " targs=" + ae.tags + " tag=" + tagId);
				if (ae.tags.contains(tagId)) {
					if (onTarget) {
						return true;
					} else {
						return false;
					}
				}

			}
			if (onTarget)
				return false;
			return true;
		}

	}

	/**
	 * 
	 *
	 */
	public class TargetCombatStateBehaviorCondition extends BehaviorCondition {
		public boolean combatState = true;
		public String toString() {
			return "[TargetCombatStateBehaviorCondition: combatState="+combatState+"]";
		}
		public boolean Calulate() {
			if (currentTarget != null) {
				return (boolean) EnginePlugin.getObjectProperty(currentTarget, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_COMBATSTATE);
			}
			return false;
		}
	}

		/**
		 * 
		 *
		 */
	public class TargetDeathStateBehaviorCondition extends BehaviorCondition {
		public boolean deathState = true;
		
		public String toString() {
			return "[TargetDeathStateBehaviorCondition: deathState="+deathState+"]";
		}
		public boolean Calculate() {
			if (currentTarget != null) {
				return (boolean) EnginePlugin.getObjectProperty(currentTarget, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
			}
			return false;
		}
	}
	
	public class EventBehaviorCondition extends BehaviorCondition{
		
		public TriggerProfile.Type eventId = TriggerProfile.Type.DAMAGE;
		/**
		 * 0 - Caster
		 * 1 - target
		 */
		public int target = 0;
		public String toString() {
			return "[EventBehaviorCondition: target="+target+" eventId="+eventId+"]";
		}
		public boolean Calculate() {
			AtomicBoolean was = new AtomicBoolean();
			events.computeIfPresent(eventId,(k,v)->{
				if (Log.loggingDebug)Log.debug("EventBehaviorCondition: oid: " + obj.getOid()+" type="+k+" time "+v+" "+(System.currentTimeMillis() - v));
				if(System.currentTimeMillis() - v < AgisMobPlugin.MOB_COMBAT_BEHAVIOR_EVENT_TIMEOUT) {
					 was.set(true);
				}
				if (Log.loggingDebug)Log.debug("EventBehaviorCondition: oid: " + obj.getOid()+" ? "+was.get() );
				return v;
			} );
			if (Log.loggingDebug)Log.debug("EventBehaviorCondition: oid: " + obj.getOid()+" no event "+was.get() );
			return was.get();
		}
	}
	
	public class NumberOfTargetsStateBehaviorCondition extends BehaviorCondition {
		public int number = 1;
		public boolean allay = true;

		public String toString() {
			return "[NumberOfTargetsStateBehaviorCondition: number="+number+" allay="+allay+" less="+less+"]";
		}
		public boolean Calculate() {
			InterpolatedWorldNode wn = obj.getWorldNode();
			if (wn == null) {
				if (Log.loggingDebug)Log.debug("NumberOfTargetsStateBehaviorCondition: got null wnode during check ability for oid: " + obj.getOid());
				return false;
			}
			// get mob location
			Point mloc = wn.getCurrentLoc();
			List<OID> list = new ArrayList<OID>();
			if (allay) {
				list = AgisMobPlugin.getFriendly(obj.getOid());
			} else {
				list = AgisMobPlugin.getEnemy(obj.getOid());
			}
			
			if (Log.loggingDebug)Log.debug("NumberOfTargetsStateBehaviorCondition.Calculate list of the objects " + list);
			List<OID> destList = new ArrayList<OID>();
			for (OID oid : list) {
				EntityHandle mHandle = new EntityHandle(oid);
				ObjectStub tObj = (ObjectStub) mHandle.getEntity(Namespace.MOB);
				Point tLoc = tObj.getWorldNode().getLoc();
				float dist = Point.distanceTo(mloc, tLoc);
				if (dist < AgisMobPlugin.MOB_COMBAT_BEHAVIOR_NUMBER_TARGETS_CHECK_MAX_DISTANCE) {
					destList.add(oid);
				}
			}
			
			if (Log.loggingDebug)Log.debug("NumberOfTargetsStateBehaviorCondition.Calculate list of the objects in range " + destList);
			if (destList.size() > number) {
				if (less) {
					return false;
				} else {
					return true;
				}
			} else {
				if (!less) {
					return false;
				} else {
					return true;
				}
				
				
			}
		}
	}
	
	
	/**
	 * Combat Behaviors Definitions with conditions  
	 *
	 */
	public class CombatBehaviorEntry {
		/**
		 * Behaviors types 
		 * 0 - melee; 
		 * 1 - ranged offensive; 
		 * 2 - ranged defensive; 
		 * 3 - defend; 
		 * 4 - flee; 
		 * 5 - heal;
		 */
		public int behaviorType = 0;

		public int weapon=-1;
		public List<GroupBehaviorConditions> conditions = new ArrayList<GroupBehaviorConditions>();

		
		//List of abilities 
		public List<AbilityGroup> abilities = new ArrayList<AbilityGroup>();
		//List of abilities used when start flee 
		public List<AbilityGroup> startAbilities = new ArrayList<AbilityGroup>();
		//List of abilities used when end flee
		public List<AbilityGroup> endAbilities = new ArrayList<AbilityGroup>();
		//Flee 
		/* Fly Type
		 *  0 - opposite direction
		 *  1 - defined position
		 *  2 - to group friendly mobs
		 */
		public int fleeType =0;
		// Defined point that mob will go
		public Point fleepoint = new Point();
		public List<Point> fleepoints = new ArrayList<Point>();
		//interval time in milisecound
		public long abilityinterval=0L;
		//Tag assigned for mob
		public int mobTag = -1;
		
		public boolean ignoreChaceDistance = false;
		
		public String toString() {
			return "[CombatBehaviorEntry: type="+behaviorType+" weapon="+weapon+" fleeType="+fleeType+" fleepoints="+fleepoints+"]";
		}
		
		public Point getPoint() {
			Random rand = new Random();
			int index = rand.nextInt(fleepoints.size());
			if (Log.loggingDebug)Log.debug("CombatBehaviorEntry getPoint: selected point index="+index+" "+fleepoints.get(index) );
			return fleepoints.get(index);
		}

		public boolean Calculate() {
			long tStart = System.nanoTime();
			if (Log.loggingDebug)Log.debug("CombatBehaviors Calculate  "+this+" conditions "+conditions.size());
			if(conditions.size()==0) {
				if (Log.loggingDebug)Log.debug("CombatBehaviors Calculate size 0 conditions");
				return true;
			}
			for (GroupBehaviorConditions gbc : conditions) {

				if (gbc.Calculate()) {

					if (Log.loggingDebug)Log.debug("CombatBehaviors Calculate: GroupBehaviorConditions gbc" + gbc + " true");
					 long tEnd = System.nanoTime();
					 io.micrometer.core.instrument.Timer.builder("combat_behavior").tags("conditions_calculate","behavior")
				                .register(Prometheus.registry()).record(Duration.ofNanos(tEnd - tStart));
					return true;
				} else {
					if (Log.loggingDebug)Log.debug("CombatBehaviors Calculate: GroupBehaviorConditions gbc" + gbc + " false");
				}

			}
			if (Log.loggingDebug)Log.debug("CombatBehaviors Calculate: End false");
			 long tEnd = System.nanoTime();
			 io.micrometer.core.instrument.Timer.builder("combat_behavior").tags("conditions_calculate","behavior")
		                .register(Prometheus.registry()).record(Duration.ofNanos(tEnd - tStart));
			return false;
		}

	}
	
	
	/**
	 * Sheduler 
	 * 
	 *
	 */
	public class BehaviorLogicTask implements Runnable {
		public BehaviorLogicTask() {
		}

		@Override
		public void run() {
			if (Log.loggingDebug)
				Log.debug("BehaviorLogicTask oid=" + obj.getOid() + " Start instance="+obj.getInstanceOid());
            if (activated == false) {
                return;
            }
			if(isDead) {
				if (Log.loggingDebug)
					Log.debug("BehaviorLogicTask oid=" + obj.getOid() + " mob is Dead");
				return;
			}
			try {
				BehaviorLogic();
			} catch (Exception e) {
				Log.exception("BehaviorLogicTask",e);
				e.printStackTrace();
			}
			if (Log.loggingDebug)
				Log.debug("BehaviorLogicTask obj=" + obj.getOid() + " End");
		}
	}

	void BehaviorLogic() {
		if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid()+" start "+ (selectedBehavior!=null?selectedBehavior.behaviorType+"":"bd")+" currentTarget="+currentTarget+" inCombat="+inCombat);
        if(!activated) {
            if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic !!!!!! obj="+obj.getOid()+" activated="+activated+" Cancel");
            return;
        }
		if(selectedBehavior!=null) {
			long delay2 = selectedBehavior.abilityinterval;
		switch (selectedBehavior.behaviorType) {
		case 0:// melee
			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: melee obj=" + obj.getOid() +" AbilityGroup count ="+selectedBehavior.abilities.size());
            if (lastUsedAbility > 0) {
                AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
                if (!lastAA.getCastingInRun()) {
                    long castingTime = calculateCastingTime(lastAA.getActivationTime());
                    if(!lastAA.getCastingInRun() && lastAA.getActivationTime()>0) {
                        castingTime += lastAA.getAttackTime();
                    }

                    if (castingTime + lastUseAbility < System.currentTimeMillis()) {
                        LinkedList<String> effects = (LinkedList<String>) EnginePlugin.getObjectProperty(obj.getOid(), CombatClient.NAMESPACE, "effects");
                        if (Log.loggingDebug)
                            Log.debug("CombatBehavior.BehaviorLogic: effects=" + effects);
                        boolean foundEffect = false;
                        for (String effect : effects) {
                            String[] ef = effect.split(",");
                            int effectID = Integer.parseInt(ef[0]);
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: effectID=" + effectID);
                            AgisEffect ae = Agis.EffectManager.get(effectID);
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: AgisEffect=" + ae );
                            if (ae instanceof StunEffect || ae instanceof SleepEffect) {
                                foundEffect = true;
                            }

                        }
                        if (!foundEffect) {
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid() + " WORLD_PROP_NOTURN to false");

                            EnginePlugin.setObjectPropertyNoResponse(obj.getOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
                        }
                    }
                }
            }
			for (AbilityGroup am : selectedBehavior.abilities) {
				if (!am.Calculate()) {
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: melee obj=" + obj + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate failed break");

					continue;
				}
				selectedAbilityGroup = am;

					if (Log.loggingDebug)
						Log.debug("CombatBehavior.BehaviorLogic: melee obj=" + obj.getOid() + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate OK " + am.ids);
					if (selectedAbility < 1) {
						selectedAbility = am.getAbility();
					}
					if (selectedAbility < 1) {
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.BehaviorLogic: melee obj=" + obj.getOid() + " selectedAbility is " + selectedAbility + " reshedule 100ms");
						
						if (lastUsedAbility > 0) {
							AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
							if (lastAA!=null && !lastAA.getCastingInRun()) {
								long castingTime = calculateCastingTime(lastAA.getActivationTime());
                                if(!lastAA.getCastingInRun() && lastAA.getActivationTime()>0) {
                                    castingTime += lastAA.getAttackTime();
                                }

                                if (castingTime + lastUseAbility < System.currentTimeMillis()) {
									InterpolatedWorldNode wnode = obj.getWorldNode();
									Point loc = wnode.getCurrentLoc();
									EntityHandle eHandle = new EntityHandle(currentTarget);

                                    if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic " + lastUsedAbility + " " + lastAA + " currentTarget=" + currentTarget + " eHandle=" + eHandle);

									float minrange = lastAA.getMinRange();
									float maxrange = lastAA.getMaxRange();
									Map<String, Serializable> props = propMap.get(obj.getOid());
									ObjectStub followObj = (ObjectStub) eHandle.getEntity(Namespace.MOB);
									if (Log.loggingDebug)
										Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid() + " followObj=" + followObj);
									if (followObj == null) {
                                        if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: got null ObjectStub during check ability for oid: " + obj.getOid() + " for " + currentTarget);
										return;
									} else {
										Point followLoc = followObj.getWorldNode().getLoc();
										float distance = Point.distanceTo(loc, followLoc);
										float targetHitBoxRange = eHandle.getHitBox();
                                        if(lastAA.getTargetType().equals(AgisAbility.TargetType.AREA) && lastAA.getAoEType().equals(AgisAbility.AoeType.PLAYER_RADIUS)){
                                            minrange = - hitBoxRange - targetHitBoxRange;
                                            maxrange = lastAA.getAreaOfEffectRadius() - hitBoxRange - targetHitBoxRange;
                                        }

										Point _followLoc = new Point(followLoc);
										if (Log.loggingDebug)
											Log.debug("CombatBehavior.BehaviorLogic:  Ranged defensive oid = " + obj.getOid() + " _followLoc=" + _followLoc + " ");
										_followLoc.sub(loc);
										///_followLoc.setY(0);
										if (Log.loggingDebug)
											Log.debug("CombatBehavior.BehaviorLogic:  Ranged defensive oid = " + obj.getOid() + " _followLoc=" + _followLoc + " sub my loc ");
										float len = (float) Math.sqrt(_followLoc.getX() * _followLoc.getX() + _followLoc.getY() * _followLoc.getY() + _followLoc.getZ() * _followLoc.getZ());
										if (Log.loggingDebug)
											Log.debug("CombatBehavior.BehaviorLogic:  Ranged defensive oid = " + obj.getOid() + " len=" + len);
										_followLoc.multiply(1 / len);
										if (Log.loggingDebug)
											Log.debug("CombatBehavior.BehaviorLogic:  Ranged defensive oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * 1/len ");
										if (Log.loggingDebug)
											Log.debug("CombatBehavior.BehaviorLogic:  Ranged defensive oid = " + obj.getOid() + " _followLoc=" + _followLoc + " distance=" + distance + " minrange=" + minrange + " maxrange=" + maxrange
													+ " minAbilityRangePercentage=" + selectedAbilityGroup.minAbilityRangePercentage + " maxAbilityRangePercentage="
													+ selectedAbilityGroup.maxAbilityRangePercentage);

										float m = distance;
										m = distance - ((maxrange - minrange) * selectedAbilityGroup.minAbilityRangePercentage / 100f + minrange) - targetHitBoxRange - hitBoxRange;
										float min = distance - ((maxrange - minrange) * selectedAbilityGroup.minAbilityRangePercentage / 100f + minrange) - targetHitBoxRange - hitBoxRange;
										float max = distance - ((maxrange - minrange) * selectedAbilityGroup.maxAbilityRangePercentage / 100f + minrange) - targetHitBoxRange - hitBoxRange;
										if (Log.loggingDebug)
											Log.debug("CombatBehavior.BehaviorLogic:  Ranged defensive oid = " + obj.getOid() + "  m= " + m + " hitBoxRange=" + hitBoxRange + " eHandle.getHitBox()=" + targetHitBoxRange + " min=" + min
													+ " max=" + max);
										_followLoc.multiply(m);
										if (Log.loggingDebug)
											Log.debug("CombatBehavior.BehaviorLogic:  Ranged defensive oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * followDist - hitbox ");
										_followLoc.add(loc);
										if (Log.loggingDebug)
											Log.debug("CombatBehavior.BehaviorLogic:  Ranged defensive oid = " + obj.getOid() + "; myLoc = " + loc + "; followLoc=" + followLoc + " _followLoc=" + _followLoc + " followDist=" + distance
													+ " len=" + len + " target hitBox=" + targetHitBoxRange + " speed=" + speed + " distance=" + distance);
										// Point destLoc = _followLoc;
										float distance2 = Point.distanceTo(loc, _followLoc);
										float distance3 = Point.distanceTo(followLoc, _followLoc);
										if (Log.loggingDebug)
											Log.debug("CombatBehavior.BehaviorLogic: Ranged defensive  Math.abs(distance2 - distance) " + Math.abs(distance2 - distance) + " distance2=" + distance2 + " distance3=" + distance3
													+ " distance=" + distance);
										// if(Math.abs(distance2 ) > 0.1 ) {
										if (distance < m || distance > maxrange + targetHitBoxRange + hitBoxRange || System.currentTimeMillis() - lastUseAbility < selectedBehavior.abilityinterval) {

											Random r = new Random();
											float range = r.nextFloat();
											range = range * (selectedAbilityGroup.maxAbilityRangePercentage - selectedAbilityGroup.minAbilityRangePercentage);
											m = distance - ((maxrange - minrange) * range / 100f + minrange) - targetHitBoxRange - hitBoxRange;

											reloacatingForAbility = true;
											command = "Goto";
											goreturn = false;
											if (Log.loggingDebug)
												Log.debug("CombatBehavior.BehaviorLogic: Ranged defensive  move to position " + _followLoc);
											Point newPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), _followLoc, obj.getInstanceOid());

											CombatClient.sendStartCombatState(obj.getOid());
											Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, speed));
										}
									}
									
									
								}
							}

						}
						
						
						BehaviorLogicTaskSchedule(selectedBehavior.abilityinterval-(System.currentTimeMillis()-lastUseAbility));
						break;
					}
					if (Log.loggingDebug)
						Log.debug("CombatBehavior.BehaviorLogic: melee obj=" + obj.getOid() + " targetOid=" + currentTarget + " Abilityid= " + selectedAbility);
				
				long delay = selectedBehavior.abilityinterval;
				AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
				long castingTime = calculateCastingTime(aa.getActivationTime());
                if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                    castingTime += aa.getAttackTime();
                }

                if (castingTime > delay)
					delay = castingTime;
				boolean result = runAbility(selectedAbility);
					if (result) {
						if (castingTime > 0 && !aa.getCastingInRun()) {
							BehaviorLogicTaskSchedule(castingTime+50);

						} else {
							BehaviorLogicTaskSchedule(delay);
						}
					} else {
						delay = selectedBehavior.abilityinterval - (System.currentTimeMillis() - lastUseAbility);
						BehaviorLogicTaskSchedule(delay > 0 ? delay : selectedBehavior.abilityinterval);
					}
					break;
				}
			delay2 = selectedBehavior.abilityinterval - (System.currentTimeMillis() - lastUseAbility);
			BehaviorLogicTaskSchedule(delay2 > 0 ? delay2 : selectedBehavior.abilityinterval);
			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: melee obj=" + obj.getOid() + " end");
			break;
		case 1:// ranged offensive
			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: ranged offensive obj=" + obj.getOid()+"  ranged offencive "+ selectedBehavior.abilities.size());
            if (lastUsedAbility > 0) {
                AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
                if (!lastAA.getCastingInRun()) {
                    long castingTime = calculateCastingTime(lastAA.getActivationTime());
                    if(!lastAA.getCastingInRun() && lastAA.getActivationTime()>0) {
                        castingTime += lastAA.getAttackTime();
                    }

                    if (castingTime + lastUseAbility < System.currentTimeMillis()) {
                        LinkedList<String> effects = (LinkedList<String>) EnginePlugin.getObjectProperty(obj.getOid(), CombatClient.NAMESPACE, "effects");
                        if (Log.loggingDebug)
                            Log.debug("CombatBehavior.BehaviorLogic: effects=" + effects);
                        boolean foundEffect = false;
                        for (String effect : effects) {
                            String[] ef = effect.split(",");
                            int effectID = Integer.parseInt(ef[0]);
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: effectID=" + effectID);
                            AgisEffect ae = Agis.EffectManager.get(effectID);
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: AgisEffect=" + ae );
                            if (ae instanceof StunEffect || ae instanceof SleepEffect) {
                                foundEffect = true;
                            }

                        }
                        if (!foundEffect) {
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid() + " WORLD_PROP_NOTURN to false");

                            EnginePlugin.setObjectPropertyNoResponse(obj.getOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
                        }
                    }
                }
            }
			for (AbilityGroup am : selectedBehavior.abilities) {
				if (!am.Calculate()) {
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: ranged offensive obj=" + obj + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate failed break");

					continue;
				}
				selectedAbilityGroup = am;

					if (Log.loggingDebug)
						Log.debug("CombatBehavior.BehaviorLogic: ranged offensive obj=" + obj.getOid() + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate OK " + am.ids);
					if (selectedAbility <1) {
						selectedAbility = am.getAbility();
					}
					if (selectedAbility <1) {
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.BehaviorLogic: ranged offensive obj=" + obj.getOid() + " selectedAbility is " + selectedAbility + " reshedule " + selectedBehavior.abilityinterval + "ms");
						if (lastUsedAbility > 0) {
							AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
							if (!lastAA.getCastingInRun()) {

								long castingTime = calculateCastingTime(lastAA.getActivationTime());
                                if(!lastAA.getCastingInRun() && lastAA.getActivationTime()>0) {
                                    castingTime += lastAA.getAttackTime();
                                }

                                if (castingTime + lastUseAbility < System.currentTimeMillis()) {

									InterpolatedWorldNode wnode = obj.getWorldNode();
									Point loc = wnode.getCurrentLoc();
									EntityHandle eHandle = new EntityHandle(currentTarget);

									AgisAbility aa = Agis.AbilityManager.get(lastUsedAbility);
                                    if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic " + lastUsedAbility + " " + aa + " currentTarget=" + currentTarget + " eHandle=" + eHandle);

									float minrange = aa.getMinRange();
									float maxrange = aa.getMaxRange();
									Map<String, Serializable> props = propMap.get(obj.getOid());
									ObjectStub followObj = (ObjectStub) eHandle.getEntity(Namespace.MOB);
									if (Log.loggingDebug)
										Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid() + " followObj=" + followObj);
									if (followObj == null) {
										Log.debug("CombatBehavior.BehaviorLogic: got null ObjectStub during check ability for oid: " + obj.getOid() + " for " + currentTarget);
										return;
									} else {
										Point followLoc = followObj.getWorldNode().getLoc();
										float distance = Point.distanceTo(loc, followLoc);
										float targetHitBoxRange = eHandle.getHitBox();
                                        if(aa.getTargetType().equals(AgisAbility.TargetType.AREA) && aa.getAoEType().equals(AgisAbility.AoeType.PLAYER_RADIUS)){
                                            minrange = - hitBoxRange - targetHitBoxRange;
                                            maxrange = aa.getAreaOfEffectRadius() - hitBoxRange - targetHitBoxRange;
                                        }
										if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid() + " in range but ability should not be useed now then move ");
										Point _followLoc = new Point(followLoc);
										if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc );
										_followLoc.sub(loc);
										//if(selectedBehavior.behaviorType == 4) {
										//	_followLoc.setY(0);
										//}
										if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " sub my loc ");
										float len = (float) Math.sqrt(_followLoc.getX() * _followLoc.getX() + _followLoc.getY() * _followLoc.getY() + _followLoc.getZ() * _followLoc.getZ());
										if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic:  in ability range oid = " + obj.getOid() + " len=" + len);
										_followLoc.multiply(1 / len);
										if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * 1/len ");
										if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " distance="+distance+" minrange="+minrange+" maxrange="+maxrange+" minAbilityRangePercentage="+
										selectedAbilityGroup.minAbilityRangePercentage+" maxAbilityRangePercentage="+selectedAbilityGroup.maxAbilityRangePercentage);
										
										float m = distance;
										Random r = new Random();
										float range = r.nextFloat();
										range = range * (selectedAbilityGroup.maxAbilityRangePercentage-selectedAbilityGroup.minAbilityRangePercentage)+selectedAbilityGroup.minAbilityRangePercentage;
										m = distance - ((maxrange - minrange) * range / 100f + minrange) - targetHitBoxRange - hitBoxRange;
										float min = ((maxrange - minrange) * selectedAbilityGroup.minAbilityRangePercentage / 100f + minrange) + targetHitBoxRange + hitBoxRange;
										float max = ((maxrange - minrange) * selectedAbilityGroup.maxAbilityRangePercentage / 100f + minrange) + targetHitBoxRange + hitBoxRange;
										
										//m = distance - ((maxrange - minrange) * selectedAbilityGroup.minAbilityRangePercentage / 100f + minrange) - targetHitBoxRange - hitBoxRange;
										if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic:  in ability range oid = " + obj.getOid() + "  m= " + m + " hitBoxRange=" + hitBoxRange + " eHandle.getHitBox()=" + targetHitBoxRange+" min="+min+" max="+max+" percentage range "+range);
										_followLoc.multiply(m);
										if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic:  in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * followDist - hitbox ");
										_followLoc.add(loc);
										if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic:  in ability range oid = " + obj.getOid() + "; myLoc = " + loc + "; followLoc=" + followLoc + " _followLoc=" + _followLoc + " followDist=" + distance + " len=" + len
												+ " target hitBox=" + targetHitBoxRange + " speed=" + speed + " distance=" + distance);
										// Point destLoc = _followLoc;
										float distance2 = Point.distanceTo(loc, _followLoc);
										float distance3 = Point.distanceTo(followLoc, _followLoc);
										if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic:  in ability range obj=" + obj.getOid() + " targetOid=" + currentTarget + " send command goto mob_loc=" + loc + " target_loc=" + followLoc + " distance=" + distance + " calculated loc="
													+ _followLoc + " speed " + speed + " mob distance to follow point " + distance2 + " target dostance to follow point " + distance3);
										if (distance < min || distance > max) {
											command = "Goto";
											goreturn = false;
											if (Log.loggingDebug)
												Log.debug("CombatBehavior.BehaviorLogic:  in ability range obj=" + obj.getOid() + " move to position " + _followLoc);
											Point newPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), _followLoc, obj.getInstanceOid());
											if (Log.loggingDebug)
												Log.debug("CombatBehavior.BehaviorLogic:  in ability range obj=" + obj.getOid() + " NavMesh position correction new position " + newPoint);

											CombatClient.sendStartCombatState(obj.getOid());
											Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, speed));
										}  else {
											if (Log.loggingDebug)
												Log.debug("CombatBehavior.BehaviorLogic:  in ability range obj=" + obj.getOid() + " mob is between min and max ");
										}
									}
									
									
								}
							}

							
						}
						BehaviorLogicTaskSchedule(selectedBehavior.abilityinterval);
						break;
					}
				if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: ranged offensive obj=" + obj.getOid() + " targetOid=" + currentTarget + " Abilityid= " + selectedAbility);
				
				long delay = selectedBehavior.abilityinterval;
				AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
                if(aa!=null) {
                    long castingTime = calculateCastingTime(aa.getActivationTime());
                    if (!aa.getCastingInRun() && aa.getActivationTime() > 0) {
                        castingTime += aa.getAttackTime();
                    }

                    if (Log.loggingDebug)
                        Log.debug("CombatBehavior.BehaviorLogic: ranged offensive obj=" + obj.getOid() + " delay=" + delay + " castingTime=" + castingTime);
                    if (castingTime > delay)
                        delay = castingTime;
                    boolean result = runAbility(selectedAbility);
                    if (result) {
                        if (castingTime > 0 && !aa.getCastingInRun()) {
                            BehaviorLogicTaskSchedule(castingTime + 50);

                        } else {
                            BehaviorLogicTaskSchedule(delay);
                        }
                    } else {
                        delay = selectedBehavior.abilityinterval - (System.currentTimeMillis() - lastUseAbility);
                        BehaviorLogicTaskSchedule(delay > 0 ? delay : selectedBehavior.abilityinterval);
                    }
                    break;
                }
            }
			delay2 = selectedBehavior.abilityinterval - (System.currentTimeMillis() - lastUseAbility);
			BehaviorLogicTaskSchedule(delay2 > 0 ? delay2 : selectedBehavior.abilityinterval);
			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: ranged offencive obj=" + obj.getOid() + " end");
			break;
		case 2:// ranged defensive
			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid()+"  ranged defensive "+ selectedBehavior.abilities.size());
            if (lastUsedAbility > 0) {
                AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
                if (!lastAA.getCastingInRun()) {

                    long castingTime = calculateCastingTime(lastAA.getActivationTime());
                    if(!lastAA.getCastingInRun() && lastAA.getActivationTime()>0) {
                        castingTime += lastAA.getAttackTime();
                    }

                    if (castingTime + lastUseAbility < System.currentTimeMillis()) {
                        LinkedList<String> effects = (LinkedList<String>) EnginePlugin.getObjectProperty(obj.getOid(), CombatClient.NAMESPACE, "effects");
                        if (Log.loggingDebug)
                            Log.debug("CombatBehavior.BehaviorLogic: effects=" + effects);
                        boolean foundEffect = false;
                        for (String effect : effects) {
                            String[] ef = effect.split(",");
                            int effectID = Integer.parseInt(ef[0]);
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: effectID=" + effectID);
                            AgisEffect ae = Agis.EffectManager.get(effectID);
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: AgisEffect=" + ae );
                            if (ae instanceof StunEffect || ae instanceof SleepEffect) {
                                foundEffect = true;
                            }

                        }
                        if (!foundEffect) {
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid() + " WORLD_PROP_NOTURN to false");

                            EnginePlugin.setObjectPropertyNoResponse(obj.getOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
                        }
                    }
                }
            }


			for (AbilityGroup am : selectedBehavior.abilities) {
				if (!am.Calculate()) {
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: ranged defensive obj=" + obj + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate failed break");

					continue;
				}
				selectedAbilityGroup = am;

				if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: ranged defensive obj=" + obj.getOid() + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate OK " + am.ids);
				selectedAbility = am.getAbility();
				if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: ranged defensive obj=" + obj.getOid() + " targetOid=" + currentTarget + " Abilityid= " + selectedAbility);
					if (selectedAbility == -1) {
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.BehaviorLogic: ranged defensive obj=" + obj.getOid() + " selectedAbility is " + selectedAbility + " reshedule ");

						InterpolatedWorldNode wnode = obj.getWorldNode();
						Point loc = wnode.getCurrentLoc();
						EntityHandle eHandle = new EntityHandle(currentTarget);

						AgisAbility aa = Agis.AbilityManager.get(lastUsedAbility);
                        if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility " + lastUsedAbility + " " + aa + " currentTarget=" + currentTarget + " eHandle=" + eHandle);

						float minrange = aa.getMinRange();
						float maxrange = aa.getMaxRange();
						Map<String, Serializable> props = propMap.get(obj.getOid());
						ObjectStub followObj = (ObjectStub) eHandle.getEntity(Namespace.MOB);
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.BehaviorLogic: defensive obj=" + obj.getOid() + " followObj=" + followObj);
						if (followObj == null) {
							Log.debug("CombatBehavior.BehaviorLogic: defensive got null ObjectStub during check ability for oid: " + obj.getOid() + " for " + currentTarget);
							return;
						} else {
							Point followLoc = followObj.getWorldNode().getLoc();
							float distance = Point.distanceTo(loc, followLoc);
							float targetHitBoxRange = eHandle.getHitBox();
                            if(aa.getTargetType().equals(AgisAbility.TargetType.AREA) && aa.getAoEType().equals(AgisAbility.AoeType.PLAYER_RADIUS)){
                                minrange = - hitBoxRange - targetHitBoxRange;
                                maxrange = aa.getAreaOfEffectRadius() - hitBoxRange - targetHitBoxRange;
                            }
							if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defensive obj=" + obj.getOid() + " in range but ability should not be useed now then move ");
							Point _followLoc = new Point(followLoc);
							if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defensive in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc );
							_followLoc.sub(loc);
							//if(selectedBehavior.behaviorType == 4) {
							//	_followLoc.setY(0);
							//}
							if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defensive in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " sub my loc ");
							float len = (float) Math.sqrt(_followLoc.getX() * _followLoc.getX() + _followLoc.getY() * _followLoc.getY() + _followLoc.getZ() * _followLoc.getZ());
							if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defensive in ability range oid = " + obj.getOid() + " len=" + len);
							_followLoc.multiply(1 / len);
							if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defensive in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * 1/len ");
							if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defensive in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " distance="+distance+" minrange="+minrange+" maxrange="+maxrange+" minAbilityRangePercentage="+
									selectedAbilityGroup.minAbilityRangePercentage+" maxAbilityRangePercentage="+selectedAbilityGroup.maxAbilityRangePercentage);
							
							float m = distance;
							Random r = new Random();
							float range = 0.9f * (selectedAbilityGroup.maxAbilityRangePercentage - selectedAbilityGroup.minAbilityRangePercentage) + selectedAbilityGroup.minAbilityRangePercentage;
							m = distance - ((maxrange - minrange) * range / 100f + minrange) - targetHitBoxRange - hitBoxRange;
							float min = ((maxrange - minrange) * selectedAbilityGroup.minAbilityRangePercentage / 100f + minrange) + targetHitBoxRange + hitBoxRange;
							float max = ((maxrange - minrange) * selectedAbilityGroup.maxAbilityRangePercentage / 100f + minrange) + targetHitBoxRange + hitBoxRange;
	
							//m = distance - ((maxrange - minrange) * selectedAbilityGroup.minAbilityRangePercentage / 100f + minrange) - targetHitBoxRange - hitBoxRange;
							if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defensive in ability range oid = " + obj.getOid() + "  m= " + m + " hitBoxRange=" + hitBoxRange + " eHandle.getHitBox()=" + targetHitBoxRange+" min="+min+" max="+max+" percentage range "+range);
							_followLoc.multiply(m);
							if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defensive in ability range oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * followDist - hitbox ");
							_followLoc.add(loc);
							if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defensive in ability range oid = " + obj.getOid() + "; myLoc = " + loc + "; followLoc=" + followLoc + " _followLoc=" + _followLoc + " followDist=" + distance + " len=" + len
									+ " target hitBox=" + targetHitBoxRange + " speed=" + speed + " distance=" + distance);
							// Point destLoc = _followLoc;
							float distance2 = Point.distanceTo(loc, _followLoc);
							float distance3 = Point.distanceTo(followLoc, _followLoc);
							if (Log.loggingDebug)Log.debug("CombatBehavior.runAbility: defensive in ability range obj=" + obj.getOid() + " targetOid=" + currentTarget + " send command goto mob_loc=" + loc + " target_loc=" + followLoc + " distance=" + distance + " calculated loc="
										+ _followLoc + " speed " + speed + " mob distance to follow point " + distance2 + " target dostance to follow point " + distance3);
							if (distance < min || distance > max) {
								command = "Goto";
								goreturn = false;
								if (Log.loggingDebug)
									Log.debug("CombatBehavior.BehaviorLogic: defensive in ability range obj=" + obj.getOid() + " move to position " + _followLoc);
								Point newPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), _followLoc, obj.getInstanceOid());
								if (Log.loggingDebug)
									Log.debug("CombatBehavior.BehaviorLogic: defensive in ability range obj=" + obj.getOid() + " NavMesh position correction new position " + newPoint);

								CombatClient.sendStartCombatState(obj.getOid());
								Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, speed));
							}  else {
								if (Log.loggingDebug)
									Log.debug("CombatBehavior.BehaviorLogic: defensive in ability range obj=" + obj.getOid() + " mob is between min and max ");
							}
						}
						
						BehaviorLogicTaskSchedule(selectedBehavior.abilityinterval);
						break;
					}
			//	selectedAbility = aid;
				long delay = selectedBehavior.abilityinterval;
				AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
				long castingTime = calculateCastingTime(aa.getActivationTime());
                if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                    castingTime += aa.getAttackTime();
                }

                if (castingTime > delay)
					delay = castingTime;
				boolean result = runAbility(selectedAbility);
				if (result) {
					if (castingTime > 0 && !aa.getCastingInRun()) {
						BehaviorLogicTaskSchedule(castingTime+50);

					} else {
						BehaviorLogicTaskSchedule(delay);
					}
				} else {
					delay = selectedBehavior.abilityinterval - (System.currentTimeMillis() - lastUseAbility);
					BehaviorLogicTaskSchedule(delay > 0 ? delay : selectedBehavior.abilityinterval);
				}
				break;
			}
			
			delay2 = selectedBehavior.abilityinterval - (System.currentTimeMillis() - lastUseAbility);
			BehaviorLogicTaskSchedule(delay2 > 0 ? delay2 : selectedBehavior.abilityinterval);
			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: ranged defensive obj=" + obj.getOid() + " end");
			break;
		case 3:// defend
			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic behavior defend defendedTarget="+defendedTarget);
            if (lastUsedAbility > 0) {
                AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
                if (!lastAA.getCastingInRun()) {
                    long castingTime = calculateCastingTime(lastAA.getActivationTime());
                    if(!lastAA.getCastingInRun() && lastAA.getActivationTime()>0) {
                        castingTime += lastAA.getAttackTime();
                    }

                    if (castingTime + lastUseAbility < System.currentTimeMillis()) {
                        LinkedList<String> effects = (LinkedList<String>) EnginePlugin.getObjectProperty(obj.getOid(), CombatClient.NAMESPACE, "effects");
                        if (Log.loggingDebug)
                            Log.debug("CombatBehavior.BehaviorLogic: effects=" + effects);
                        boolean foundEffect = false;
                        for (String effect : effects) {
                            String[] ef = effect.split(",");
                            int effectID = Integer.parseInt(ef[0]);
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: effectID=" + effectID);
                            AgisEffect ae = Agis.EffectManager.get(effectID);
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: AgisEffect=" + ae );
                            if (ae instanceof StunEffect || ae instanceof SleepEffect) {
                                foundEffect = true;
                            }

                        }
                        if (!foundEffect) {
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid() + " WORLD_PROP_NOTURN to false");

                            EnginePlugin.setObjectPropertyNoResponse(obj.getOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
                        }
                    }
                }
            }
			if(defendedTarget==null) {
				InterpolatedWorldNode wn = obj.getWorldNode();
				if (wn != null) {
					Point mloc = wn.getCurrentLoc();
					List<OID> list = AgisMobPlugin.getFriendly(obj.getOid());
//					HashMap<OID, Point> locations = new HashMap<OID, Point>();
					//Point dLoc = new Point();
					OID nearestTarget=null;
					float nearestTargetDist= Float.MAX_VALUE;
					
					for (OID oid : list) {
						Entity e = EntityManager.getEntityByNamespace(oid, Namespace.MOB);
						if (Log.loggingDebug)Log.debug("BehaviorLogic: oid " + oid + " isMob? " + e.getType().isMob());
						
						ObjectStub os = (ObjectStub) e;
						for (Behavior behav : os.getBehaviors()) {
							if (Log.loggingDebug)Log.debug("BehaviorLogic: oid " + oid + " behav " + behav);
							if (behav instanceof CombatBehavior) {
								CombatBehavior _cb = ((CombatBehavior) behav);
								List<Integer> tags = _cb.getTags();
								if (Log.loggingDebug)Log.debug("BehaviorLogic: oid " + oid + " tags " + tags);

								if (((CombatBehavior) behav).getTags().contains(selectedBehavior.mobTag)) {
									
									EntityHandle mHandle = new EntityHandle(oid);
									ObjectStub tObj = (ObjectStub) mHandle.getEntity(Namespace.MOB);
									Point tLoc = tObj.getWorldNode().getLoc();
									float dist = Point.distanceTo(mloc, tLoc);
										if(dist<nearestTargetDist) {
											nearestTargetDist = dist;
											nearestTarget = oid;
										}
								}
							} else {
								if (Log.loggingDebug)Log.debug("BehaviorLogic: oid " + oid + " no combat behavior ");
							}
						}
					}
					
					//for (OID oid : locations.keySet()) {
					if(nearestTarget!=null)	{
						subscribeForTarget(nearestTarget);
						defendedTarget = nearestTarget;
					}
				}else {
					if (Log.loggingDebug)Log.debug("BehaviorLogic: got null wnode during check ability for oid: " + obj.getOid());
				}
			}
			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic behavior defend defendedTarget=" + defendedTarget + " currentTarget=" + currentTarget);
				if (defendedTarget != null && currentTarget == null) {
					InterpolatedWorldNode wn = obj.getWorldNode();
					if (wn != null) {
						Point mloc = wn.getCurrentLoc();
						EntityHandle tHandle = new EntityHandle(defendedTarget);
						ObjectStub tObj = (ObjectStub) tHandle.getEntity(Namespace.MOB);
						Point tLoc = tObj.getWorldNode().getLoc();
						float targetSpeed = speed;
						boolean dead = false;
						boolean combatFound = false;
						for (Behavior behav : tObj.getBehaviors()) {
							if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget " + defendedTarget + " behav " + behav);
							if (behav instanceof BaseBehavior) {
								BaseBehavior _bb = ((BaseBehavior) behav);
								if(_bb.activated) {
									targetSpeed = _bb.mobSpeed;
								}else {
									targetSpeed = AgisMobClient.sendGetActorSpeed(tObj.getOid(),obj.getInstanceOid());
								}
								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget " + defendedTarget + " target speed " + _bb.mobSpeed+" "+targetSpeed);

							} else if (behav instanceof CombatBehavior) {
								CombatBehavior _cb = ((CombatBehavior) behav);
								combatFound = true;
								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget _cb.isDead=" + _cb.isDead);
								dead = _cb.isDead;
							} else {
								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget " + defendedTarget + " no Base behavior ");
							}
						}
						if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic behavior defend  defendedTarget=" + defendedTarget + " dead " + dead + " combat behavior found " + combatFound);
						if (!combatFound) {
							if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic behavior defend  defendedTarget=" + defendedTarget + " isDead reset target num behav " + tObj.getBehaviors().size());
							defendedTarget = null;
						} else {

							// EntityHandle eHandle = new EntityHandle(defendedTarget);
							float distance = Point.distanceTo(mloc, tLoc);
							if (distance > 2 + tHandle.getHitBox() + hitBoxRange) {
								Point _followLoc = new Point(tLoc);
								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " ");
								_followLoc.sub(mloc);
								//_followLoc.setY(0);
								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " sub my loc ");
								float len = (float) Math.sqrt(_followLoc.getX() * _followLoc.getX() + _followLoc.getY() * _followLoc.getY() + _followLoc.getZ() * _followLoc.getZ());
								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget oid = " + obj.getOid() + " len=" + len);
								_followLoc.multiply(1 / len);
								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * 1/len ");
								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " distance=" + distance + " minAbilityRangePercentage="
										+ (selectedAbilityGroup!=null? selectedAbilityGroup.minAbilityRangePercentage:"") + " maxAbilityRangePercentage=" + (selectedAbilityGroup!=null?selectedAbilityGroup.maxAbilityRangePercentage:""));

								float m = distance - 2 - tHandle.getHitBox() - hitBoxRange;

								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget oid = " + obj.getOid() + "  m= " + m + " hitBoxRange=" + hitBoxRange + " eHandle.getHitBox()=" + tHandle.getHitBox());
								_followLoc.multiply(m);
								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * followDist - hitbox ");
								_followLoc.add(mloc);
								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget oid = " + obj.getOid() + "; myLoc = " + mloc + "; followLoc=" + tLoc + " _followLoc=" + _followLoc + " followDist=" + distance + " len=" + len
										+ " target hitBox=" + tHandle.getHitBox() + " speed=" + speed + " distance=" + distance);
								// Point destLoc = _followLoc;
								float distance2 = Point.distanceTo(mloc, _followLoc);
								float distance3 = Point.distanceTo(tLoc, _followLoc);
								if (Log.loggingDebug)Log.debug(
										"CombatBehavior.BehaviorLogic: Math.abs(distance2 - distance) " + Math.abs(distance2 - distance) + " distance2=" + distance2 + " distance3=" + distance3 + " distance=" + distance);

								// reloacatingForAbility = true;
								command = "Goto";
								goreturn = false;
								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget move to position " + _followLoc);
								Point newPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), _followLoc, obj.getInstanceOid());
								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget move to position " + newPoint+" speed="+speed+" targetSpeed="+targetSpeed);
								
								if (gotoLoc == null || Point.distanceTo(gotoLoc, newPoint) > 0.5f) {
									if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: defendedTarget oid " + defendedTarget + " isMob? " + tObj.getType().isMob());
									CombatClient.sendStartCombatState(obj.getOid());
									gotoLoc = newPoint;
									
									
									
									
									/*if (distance2 < 2 && !Float.isNaN(targetSpeed)) {
										Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, targetSpeed ));
									} else*/ if (distance2 < 5 && !Float.isNaN(targetSpeed)) {
										Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, targetSpeed ));
									} else {
										Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, speed));
										}
								}
							}
						}
				} else {
					if (Log.loggingDebug)Log.debug("BehaviorLogic: got null wnode during check ability for oid: " + obj.getOid());
				}

				} else if (currentTarget != null) {
					if (Log.loggingDebug)
						Log.debug("CombatBehavior.BehaviorLogic behavior defend currentTarget= " + currentTarget);
					for (AbilityGroup am : selectedBehavior.abilities) {
						if (!am.Calculate()) {
							if (Log.loggingDebug)
								Log.debug("CombatBehavior.BehaviorLogic: ranged defensive obj=" + obj + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate failed break");

							continue;
						}
						selectedAbilityGroup = am;

						if (Log.loggingDebug)
							Log.debug("CombatBehavior.BehaviorLogic: ranged defensive obj=" + obj.getOid() + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate OK " + am.ids);

						selectedAbility = am.getAbility();
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.BehaviorLogic: ranged defensive obj=" + obj.getOid() + " targetOid=" + currentTarget + " Abilityid= " + selectedAbility);
						// selectedAbility = aid;
						if (selectedAbility == -1) {
							if (Log.loggingDebug)
								Log.debug("CombatBehavior.BehaviorLogic: ranged offensive obj=" + obj.getOid() + " selectedAbility is " + selectedAbility + " reshedule " + selectedBehavior.abilityinterval + "ms");
							BehaviorLogicTaskSchedule(selectedBehavior.abilityinterval);
							break;
						}
						long delay = selectedBehavior.abilityinterval;
						AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
						long castingTime = calculateCastingTime(aa.getActivationTime());
                        if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                            castingTime += aa.getAttackTime();
                        }

                        if (castingTime > delay)
							delay = castingTime;
						boolean result = runAbility(selectedAbility);
						if (result) {
							BehaviorLogicTaskSchedule(delay);
						} else {
							// BehaviorLogicTaskSchedule( delay);
						}
						break;
					}
					delay2 = selectedBehavior.abilityinterval - (System.currentTimeMillis() - lastUseAbility);
					BehaviorLogicTaskSchedule(delay2 > 0 ? delay2 : selectedBehavior.abilityinterval);
				}

				if (Log.loggingDebug)
					Log.debug("CombatBehavior.BehaviorLogic behavior defend End");
				break;
		case 4:// flee
			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid() + " targetOid=" + currentTarget + " behaviorType= " + selectedBehavior.behaviorType + " phase=" + phase);
            if (lastUsedAbility > 0) {
                AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
                if (!lastAA.getCastingInRun()) {
                    long castingTime = calculateCastingTime(lastAA.getActivationTime());
                    if(!lastAA.getCastingInRun() && lastAA.getActivationTime()>0) {
                        castingTime += lastAA.getAttackTime();
                    }

                    if (castingTime + lastUseAbility < System.currentTimeMillis()) {
                        LinkedList<String> effects = (LinkedList<String>) EnginePlugin.getObjectProperty(obj.getOid(), CombatClient.NAMESPACE, "effects");
                        if (Log.loggingDebug)
                            Log.debug("CombatBehavior.BehaviorLogic: effects=" + effects);
                        boolean foundEffect = false;
                        for (String effect : effects) {
                            String[] ef = effect.split(",");
                            int effectID = Integer.parseInt(ef[0]);
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: effectID=" + effectID);
                            AgisEffect ae = Agis.EffectManager.get(effectID);
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: AgisEffect=" + ae );
                            if (ae instanceof StunEffect || ae instanceof SleepEffect) {
                                foundEffect = true;
                            }

                        }
                        if (!foundEffect) {
                            if (Log.loggingDebug)
                                Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid() + " WORLD_PROP_NOTURN to false");

                            EnginePlugin.setObjectPropertyNoResponse(obj.getOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
                        }
                    }
                }
            }
			if (phase == 0) {
				//Run Start Ability
				int startability = -1;
				long delay = selectedBehavior.abilityinterval;
				Random rand = new Random();
				for (AbilityGroup am : selectedBehavior.startAbilities) {

						if (startability == -1) {
							if (am.Calculate()) {
								startability = am.getAbility();
							}
						}
				}
				CombatClient.sendStartCombatState(obj.getOid());
				if (startability > 0) {
					AgisAbility aa = Agis.AbilityManager.get(startability);
					// if (Log.loggingDebug)Log.debug("CombatBehavior.handleArrived Start Ability " + startability);
					// runAbility(startability);
					CombatClient.startAbility(startability, obj.getOid(), obj.getOid(), null, false, -1, -1);
					long castingTime = calculateCastingTime( aa.getActivationTime());
                    if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                        castingTime += aa.getAttackTime();
                    }

                    if (castingTime > delay)
						delay = castingTime;
				}
				if(currentTarget!=null && sendLinkedAggro)
					sendLinkedAggro(currentTarget);
				//if (selectedBehavior.fleeType = 2) {
					phase = 1;
				//}
				BehaviorLogicTaskSchedule(delay);
			} else if (phase == 1) {
				//run to point  depends on flee type
				switch (selectedBehavior.fleeType) {
				case 0:
					InterpolatedWorldNode wnode = obj.getWorldNode();
					if (wnode == null) {
						if (Log.loggingDebug)Log.debug("BehaviorLogic: got null wnode during check ability for oid: " + obj.getOid());
						break;
					}
					// get mob location
					Point loc = wnode.getCurrentLoc();
						if (currentTarget != null) {
					EntityHandle eHandle = new EntityHandle(currentTarget);
					ObjectStub targetObj = (ObjectStub) eHandle.getEntity(Namespace.MOB);
							if (targetObj != null) {
					// get target location
					Point targetLoc = targetObj.getWorldNode().getLoc();

					float distance = Point.distanceTo(loc, targetLoc);

					Point destLoc = new Point(loc);
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee oid = " + obj.getOid() + " destLoc=" + destLoc + " ");
					destLoc.sub(targetLoc);
					destLoc.setY(0);
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee oid = " + obj.getOid() + " destLoc=" + destLoc + " sub my loc ");
					// Normalize vector
					float len = (float) Math.sqrt(destLoc.getX() * destLoc.getX() + destLoc.getY() * destLoc.getY() + destLoc.getZ() * destLoc.getZ());
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee oid = " + obj.getOid() + " len=" + len);
					destLoc.multiply(1 / len);
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee oid = " + obj.getOid() + " destLoc=" + destLoc + " * 1/len ");
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee oid = " + obj.getOid() + "  distance= " + 10 + " hitBoxRange=" + hitBoxRange + " eHandle.getHitBox()=" + eHandle.getHitBox());
					destLoc.multiply(10f);
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee oid = " + obj.getOid() + " destLoc=" + destLoc + "  ");
					destLoc.add(loc);
								if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee oid = " + obj.getOid() + "; myLoc = " + loc + "; targetLoc=" + targetLoc + " destLoc=" + destLoc + " followDist=" + distance + " len="
											+ len + " target hitBox=" + eHandle.getHitBox() + " speed=" + speed + " distance=" + distance);
					// Point destLoc = _followLoc;
					float distance2 = Point.distanceTo(loc, destLoc);
					float distance3 = Point.distanceTo(targetLoc, destLoc);
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee oid = " + obj.getOid() + "; myLoc = " + loc + "; targetLoc=" + targetLoc + " destLoc=" + destLoc + " followDist=" + distance
							+ " distance2 mob to new loc=" + distance2 + " distance3 target to nwe loc=" + distance3);
					reloacatingForAbility = true;
					command = "Goto";
					goreturn = false;
					
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee move to position " + destLoc);
					Point newPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), destLoc, obj.getInstanceOid());
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee move to position " + newPoint);
					CombatClient.sendStartCombatState(obj.getOid());
					
					Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, speed));
					gotoLoc = newPoint;
							} else {
								BehaviorLogicTaskSchedule(selectedBehavior.abilityinterval);
							}
						} else {
							BehaviorLogicTaskSchedule(selectedBehavior.abilityinterval);
						}

					break;
				case 1:
					if (Log.loggingDebug)Log.debug("BehaviorLogic: flee oid " + obj.getOid() + " phase 1 move to defined point " + selectedBehavior.fleepoint + " ");
					command = "Stop";
					Engine.getAgent().sendBroadcast(new BaseBehavior.StopCommandMessage(obj.getOid()));
					CombatClient.sendStartCombatState(obj.getOid());
					command = "Goto";
					goreturn = false;
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee move to position " + selectedBehavior.fleepoint);
					Point newDestinationPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), selectedBehavior.fleepoint, obj.getInstanceOid());
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee move to position " + newDestinationPoint);
					Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj,newDestinationPoint, speed));
					gotoLoc = newDestinationPoint;
					
					BehaviorLogicTaskSchedule(selectedBehavior.abilityinterval);

					break;
				case 2:
					InterpolatedWorldNode wn = obj.getWorldNode();
					if (wn == null) {
						if (Log.loggingDebug)Log.debug("BehaviorLogic: got null wnode during check ability for oid: " + obj.getOid());
						break;
					}
					// get mob location
					Point mloc = wn.getCurrentLoc();

					List<OID> list = AgisMobPlugin.getFriendly(obj.getOid());
					HashMap<OID, Point> locations = new HashMap<OID, Point>();
					Point dLoc = new Point();
                    if (Log.loggingDebug)Log.debug("BehaviorLogic: flee oid " + obj.getOid() + "  friends " + list + " ");
                    HashMap<Float, ArrayList<OID>> gtarget  =  new HashMap<>();
                    for (OID oid : list) {
						// Entity e = EntityManager.getEntityByNamespace(oid, Namespace.MOB);
						EntityHandle mHandle = new EntityHandle(oid);
						ObjectStub tObj = (ObjectStub) mHandle.getEntity(Namespace.MOB);
						Point tLoc = tObj.getWorldNode().getLoc();
						float dist = Point.distanceTo(mloc, tLoc);
                        if (Log.loggingDebug)Log.debug("BehaviorLogic: flee oid " + obj.getOid() + " friend " + oid + " dist="+dist+" MOB_COMBAT_BEHAVIOR_FLEE_ALLIES_CHECK_MAX_DISTANCE="+AgisMobPlugin.MOB_COMBAT_BEHAVIOR_FLEE_ALLIES_CHECK_MAX_DISTANCE);
                        if (dist < AgisMobPlugin.MOB_COMBAT_BEHAVIOR_FLEE_ALLIES_CHECK_MAX_DISTANCE) {
							locations.put(oid, tLoc);
							dLoc.add(tLoc);
                            AOVector reqDir1 = AOVector.sub(tLoc, mloc);
                            float yaw = AOVector.getLookAtYaw(reqDir1);
                            if(yaw < 0)
                                yaw += 360;
                            if(gtarget.size() == 0) {
                                gtarget.computeIfAbsent(yaw , __->new ArrayList<OID>()).add(oid);
                            } else {

                            boolean found =false;
                            Set<Float> yaws = new HashSet<>(gtarget.keySet());
                                for (float _yaw : yaws){
                                    if(_yaw < 30 &&( 360 - _yaw < yaw || _yaw + 30 >yaw)){
                                        gtarget.computeIfAbsent(yaw , __->new ArrayList<OID>()).add(oid);
                                        found = true;
                                    } else if(_yaw > 330 &&( _yaw - 30 < yaw || 360 - _yaw + 30 > yaw)){
                                        gtarget.computeIfAbsent(yaw , __->new ArrayList<OID>()).add(oid);
                                        found = true;
                                    } else if(( _yaw - 30 < yaw && _yaw + 30 > yaw)){
                                        gtarget.computeIfAbsent(yaw , __->new ArrayList<OID>()).add(oid);
                                        found = true;
                                    }
                                }
                                if(!found) {
                                    gtarget.computeIfAbsent(yaw , __->new ArrayList<OID>()).add(oid);
                                }
                            }

						}

                        if (Log.loggingDebug)Log.debug("BehaviorLogic: flee oid " + obj.getOid() + " friend " + oid + " dloc="+dLoc+" tLoc="+tLoc);

                    }
                    if (Log.loggingDebug)Log.debug("BehaviorLogic: flee oid " + obj.getOid() + " friends locations=" + locations+" gtarget="+gtarget);
                    ArrayList <OID> targets = new ArrayList<>();
                    for (float _yaw : gtarget.keySet()) {
                        if(targets.size()<gtarget.get(_yaw).size()){
                            targets = gtarget.get(_yaw);
                        }
                    }
                    if (Log.loggingDebug)Log.debug("BehaviorLogic: flee oid " + obj.getOid() + " friends locations=" + locations);
					dLoc.multiply(1F / locations.size());

                    if (Log.loggingDebug)Log.debug("BehaviorLogic: flee oid " + obj.getOid() + " phase 1 move to friends " + dLoc + " ");
                    if(targets.size() > 0) {
                        dLoc = locations.get(targets.get(0));
                    }
					if (Log.loggingDebug)Log.debug("BehaviorLogic: flee oid " + obj.getOid() + " phase 1 move to friends Loc " + dLoc + " ");
					command = "Stop";
					Engine.getAgent().sendBroadcast(new BaseBehavior.StopCommandMessage(obj.getOid()));
					CombatClient.sendStartCombatState(obj.getOid());
					command = "Goto";
					goreturn = false;
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee move to position " + dLoc);
					Point newDestPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), dLoc, obj.getInstanceOid());
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: flee move to position " + newDestPoint);
					Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newDestPoint, speed));
					gotoLoc = newDestPoint;
					BehaviorLogicTaskSchedule(selectedBehavior.abilityinterval);
					break;

				}

				phase = 2;
			} else if (phase == 2) {
				if(currentTarget!=null&& sendLinkedAggro)
					sendLinkedAggro(currentTarget);

				int selectedability = -1;
				long delay = selectedBehavior.abilityinterval;
				Random rand = new Random();
				for (AbilityGroup am : selectedBehavior.abilities) {
					if (am.Calculate()) {
					
						selectedability = am.getAbility();
					}

				}
				if (selectedability > 0) {
					AgisAbility aa = Agis.AbilityManager.get(selectedability);
					CombatClient.startAbility(selectedability, obj.getOid(), obj.getOid(), null, false, -1, -1);
					long castingTime = calculateCastingTime(aa.getActivationTime());
                    if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                        castingTime += aa.getAttackTime();
                    }

                    if (castingTime > delay)
						delay = castingTime;
				}
				// shedule intervalasdasd
				BehaviorLogicTaskSchedule(delay);
			} else if (phase == 3) {

			}

			break;
		case 5:// heal
			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: heal obj=" + obj.getOid() + " Heal Start");
            if (lastUsedAbility > 0) {
                AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
                if (!lastAA.getCastingInRun()) {
                    long castingTime = calculateCastingTime(lastAA.getActivationTime());
                    if(!lastAA.getCastingInRun() && lastAA.getActivationTime()>0) {
                        castingTime += lastAA.getAttackTime();
                    }
                    if (castingTime + lastUseAbility < System.currentTimeMillis()) {
                        if (Log.loggingDebug)
                            Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid() + " WORLD_PROP_NOTURN to false");

                        EnginePlugin.setObjectPropertyNoResponse(obj.getOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
                    }
                }
            }
			List<OID> list = AgisMobPlugin.getFriendly(obj.getOid());
			if (Log.loggingDebug)Log.debug("BCombatBehavior.BehaviorLogic: heal friendly list " + list);
			List<OID> objectWithTag = new ArrayList<OID>();
			for (OID oid : list) {
				Entity e = EntityManager.getEntityByNamespace(oid, Namespace.MOB);
				if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: heal obj="+obj.getOid()+"  oid " + oid + " isMob? " + e.getType().isMob());
				// if (e.getType().isMob()) {
				ObjectStub os = (ObjectStub) e;
				for (Behavior behav : os.getBehaviors()) {
					if (Log.loggingDebug)Log.debug("BehaviorLogic: oid " + oid + " behav " + behav);
					if (behav instanceof CombatBehavior) {
						CombatBehavior cb = ((CombatBehavior) behav);
						List<Integer> tags = cb.getTags();
						if (Log.loggingDebug)Log.debug("BehaviorLogic: oid " + oid + " tags " + tags);

						if (((CombatBehavior) behav).getTags().contains(selectedBehavior.mobTag)) {
							objectWithTag.add(oid);
						}
					} else {
						if (Log.loggingDebug)Log.debug("BehaviorLogic: oid " + oid + " no combat behavior ");
					}
				}
			

			}

			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: heal  obj="+ obj.getOid()+" objectWithTag " + objectWithTag + " with tag " + selectedBehavior.mobTag);
			LinkedList<String> param = new LinkedList<String>();
			param.add(CombatPlugin.HEALTH_STAT);
			param.add(CombatPlugin.HEALTH_MAX_STAT);
			OID selectedObjetToHeal = null;
			float selectedObjetHeathToHeal = 1;

			for (OID oid : objectWithTag) {
				if (subscribed.contains(oid)) {
					subscribeForTarget(oid);
					subscribed.add(oid);
				}

				HashMap<String, Serializable> targetParams = CombatClient.getCombatInfoParams(oid, param);
				if (Log.loggingDebug)Log.debug("BehaviorLogic: heal param for target " + oid + " " + targetParams);
				int health = (Integer) targetParams.get(CombatPlugin.HEALTH_STAT);
				int healthMax = (Integer) targetParams.get(CombatPlugin.HEALTH_MAX_STAT);
					float healthPercentage = health / healthMax;
					if (selectedObjetHeathToHeal > healthPercentage) {
						if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid() + " selectedObjetHeathToHeal="+selectedObjetHeathToHeal+" healthPercentage="+healthPercentage+" selected "+oid);
						selectedObjetToHeal = oid;
						selectedObjetHeathToHeal = health / healthMax;
				}
			}
			
			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: heal obj=" + obj.getOid() + " selectedObjetToHeal="+selectedObjetToHeal+" selectedObjetHeathToHeal="+selectedObjetHeathToHeal);
			if (selectedObjetToHeal != null) {
                if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: heal obj=" + obj.getOid() + " currentTarget="+currentTarget+" set currentTarget to "+selectedObjetToHeal+" ");
				currentTarget = selectedObjetToHeal;
				 setTarget(currentTarget);
				boolean foundAbilityGroup =false;
				for (AbilityGroup am : selectedBehavior.abilities) {

					if (!am.Calculate()) {
						if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: heal obj=" + obj.getOid() + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate failed break");

						continue;
					}
					selectedAbilityGroup = am;
					foundAbilityGroup = true;
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: heal obj=" + obj.getOid() + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate OK");
				
					selectedAbility = am.getAbility();
					if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: heal obj=" + obj.getOid() + " targetOid=" + currentTarget + " Abilityid= " + selectedAbility);
					
					if (selectedAbility == -1) {
						if (Log.loggingDebug)
							Log.debug("CombatBehavior.BehaviorLogic: heal obj=" + obj.getOid() + " selectedAbility is " + selectedAbility + " reshedule " + selectedBehavior.abilityinterval + "ms");
						BehaviorLogicTaskSchedule(selectedBehavior.abilityinterval);
						break;
					}
					
					long delay = selectedBehavior.abilityinterval;
					AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
					long castingTime = calculateCastingTime(aa.getActivationTime());
                    if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                        castingTime += aa.getAttackTime();
                    }
					if (castingTime > delay)
						delay = castingTime;
					boolean result = runAbility(selectedAbility);
					if (result) {
						BehaviorLogicTaskSchedule(delay);
					} else {
						// BehaviorLogicTaskSchedule( delay);
					}
					

				}
				if(!foundAbilityGroup) {
					delay2 = selectedBehavior.abilityinterval - (System.currentTimeMillis() - lastUseAbility);
					BehaviorLogicTaskSchedule(delay2 > 0 ? delay2 : selectedBehavior.abilityinterval);
				}
				
				
			} else {
				currentTarget = selectedObjetToHeal;
				BehaviorLogicTaskSchedule(selectedBehavior.abilityinterval);
			}
			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: heal obj=" + obj.getOid() + " currentTarget="+currentTarget+" "+selectedBehavior.abilities.size());
			
			
			if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: heal obj=" + obj + " heal End");

			break;
		

		}
	} else {
		if(currentTarget!=null) {
			
			Engine.getAgent().sendBroadcast(new BaseBehavior.StopCommandMessage(obj.getOid()));
		}
	}
		if (Log.loggingDebug)Log.debug("CombatBehavior.BehaviorLogic: obj=" + obj.getOid()+" end ");

	}
	
	/**
	 * Function to schedule  
	 * @param delay
	 */
	protected void BehaviorLogicTaskSchedule(long delay) {
		if (Log.loggingDebug)Log.debug("BehaviorLogicTaskSchedule !!!!!! obj="+obj.getOid()+" delay="+delay+" activated="+activated);
        if(!activated) {
            if (Log.loggingDebug)Log.debug("BehaviorLogicTaskSchedule !!!!!! obj="+obj.getOid()+" delay="+delay+" activated="+activated+" Cancel");
            return;
        }
		if(abilitySelectorTask!=null) {
			long d = abilitySelectorTask.getDelay(TimeUnit.NANOSECONDS);
			if(d>0)
				abilitySelectorTask.cancel(false);
		}
		abilitySelectorTask = Engine.getExecutor().schedule(abilitySelector, delay, TimeUnit.MILLISECONDS);
		if (Log.loggingDebug)Log.debug("BehaviorLogicTaskSchedule !!!!!! obj="+obj.getOid()+" End");

	}
	
	public class BehaviorSelector implements Runnable {

		public BehaviorSelector() {
		}

		@Override
		public void run() {
			if (Log.loggingDebug)
                Log.debug("BehaviorSelector obj="+obj.getOid()+" Start selectedBehavior="+(selectedBehavior != null ? selectedBehavior.behaviorType + "" : "")+" activated="+activated+" instance="+obj.getInstanceOid());
			if (activated == false) {
				return;
			}
			if(isDead) {
				if (Log.loggingDebug)
					Log.debug("BehaviorSelector oid=" + obj.getOid() + " mob is Dead");
				selectedBehavior = null;
				return;
			}
			try {
				for (CombatBehaviorEntry cb : behaviors) {
					if (cb.Calculate()) {
						if (Log.loggingDebug)
							Log.debug("BehaviorSelector true " + cb);

						if (selectedBehavior != null) {
							if (!selectedBehavior.equals(cb)) {
								if (Log.loggingDebug)
									Log.debug("BehaviorSelector selected same");

								//subscribed.forEach((o) -> unsubscribeForTarget(o));
								for (ListIterator<OID> it = subscribed.listIterator(); it.hasNext();){
									unsubscribeForTarget(it.next());
								}
								subscribed.clear();
								if (cb.behaviorType == 4) {
									if (cb.fleeType == 1) {
										cb.fleepoint = cb.getPoint();
									} else if (cb.fleeType == 2) {

										InterpolatedWorldNode wn = obj.getWorldNode();
										if (wn != null) {
											Point mloc = wn.getCurrentLoc();
											List<OID> list = AgisMobPlugin.getFriendly(obj.getOid());
											HashMap<OID, Point> locations = new HashMap<OID, Point>();
											Point dLoc = new Point();
											for (OID oid : list) {
												// Entity e = EntityManager.getEntityByNamespace(oid, Namespace.MOB);
												EntityHandle mHandle = new EntityHandle(oid);
												ObjectStub tObj = (ObjectStub) mHandle.getEntity(Namespace.MOB);
												Point tLoc = tObj.getWorldNode().getLoc();
												float dist = Point.distanceTo(mloc, tLoc);
												if (dist <  AgisMobPlugin.MOB_COMBAT_BEHAVIOR_FLEE_ALLIES_CHECK_MAX_DISTANCE) {
													locations.put(oid, tLoc);
													dLoc.add(tLoc);
												}

											}
											locations.forEach((k, v) -> {
												subscribeForTarget(k);
												subscribed.add(k);
											});

										} else {

											if (Log.loggingDebug)
												Log.debug("BehaviorSelector: got null wnode during check ability for oid: " + obj.getOid());

										}
										// get mob location

									}
								} else if (cb.behaviorType == 3) {
									InterpolatedWorldNode wn = obj.getWorldNode();
									if (wn != null) {
										Point mloc = wn.getCurrentLoc();
										List<OID> list = AgisMobPlugin.getFriendly(obj.getOid());
										HashMap<OID, Point> locations = new HashMap<OID, Point>();
										// Point dLoc = new Point();
										OID nearestTarget = null;
										float nearestTargetDist = Float.MAX_VALUE;

										for (OID oid : list) {
											Entity e = EntityManager.getEntityByNamespace(oid, Namespace.MOB);
											if (Log.loggingDebug)
												Log.debug("BehaviorLogic: oid " + oid + " isMob? " + e.getType().isMob());

											ObjectStub os = (ObjectStub) e;
											for (Behavior behav : os.getBehaviors()) {
												if (Log.loggingDebug)
													Log.debug("BehaviorLogic: oid " + oid + " behav " + behav);
												if (behav instanceof CombatBehavior) {
													CombatBehavior _cb = ((CombatBehavior) behav);
													List<Integer> tags = _cb.getTags();
													if (Log.loggingDebug)
														Log.debug("BehaviorLogic: oid " + oid + " tags " + tags);

													if (((CombatBehavior) behav).getTags().contains(cb.mobTag)) {

														EntityHandle mHandle = new EntityHandle(oid);
														ObjectStub tObj = (ObjectStub) mHandle.getEntity(Namespace.MOB);
														Point tLoc = tObj.getWorldNode().getLoc();
														float dist = Point.distanceTo(mloc, tLoc);
														locations.put(oid, tLoc);
														if (dist < nearestTargetDist) {
															nearestTargetDist = dist;
															nearestTarget = oid;
														}
														defendTargets.put(oid, dist);
													}
												} else {
													if (Log.loggingDebug)
														Log.debug("BehaviorLogic: oid " + oid + " no combat behavior ");
												}
											}
										}

										locations.forEach((k, v) -> {
											subscribeForTarget(k);
											subscribed.add(k);
										});
										if (nearestTarget != null) {

											defendedTarget = nearestTarget;
										}
										// }
									} else {

										if (Log.loggingDebug)
											Log.debug("BehaviorSelector: got null wnode during check ability for oid: " + obj.getOid());

									}
								} else if (cb.behaviorType == 5) {

									BehaviorLogicTaskSchedule(cb.abilityinterval);

								}
								phase = 0;
							//	ChatClient.sendChatMsg(obj.getOid(), "Mob_" + obj.getOid(), 2, "Mob_" + obj.getOid() + " Selected behavior profile " + behaviorId + " selected " + cb.behaviorType);
								EnginePlugin.setObjectProperty(obj.getOid(), Namespace.WORLD_MANAGER, "MobBehav", cb.behaviorType);
								try {
									if (selectedBehavior.weapon > 0) {
										if (selectedBehavior.weapon != cb.weapon) {
											if (items.containsKey(selectedBehavior.weapon)) {
												boolean unequiped = AgisInventoryClient.unequipeItem(obj.getOid(), items.get(selectedBehavior.weapon));
												if (Log.loggingDebug)
													Log.debug("BehaviorSelector: oid: " + obj.getOid() + " unequiped=" + unequiped + " weapon=" + cb.weapon + " itemOid=" + items.get(cb.weapon));
											}
										} else {
											if (Log.loggingDebug)
												Log.debug("BehaviorSelector: oid: " + obj.getOid() + " same weapon");
										}
									}
									if (cb.weapon > 0) {
										if (items.containsKey(cb.weapon)) {
											if (Log.loggingDebug)
												Log.debug("BehaviorSelector: oid: " + obj.getOid() + " found item in hashmap about to equip");
											boolean equiped = AgisInventoryClient.equipeItem(obj.getOid(), items.get(cb.weapon));
											if (Log.loggingDebug)
												Log.debug("BehaviorSelector: oid: " + obj.getOid() + " equiped=" + equiped + " weapon=" + cb.weapon + " itemOid=" + items.get(cb.weapon));

										} else {
											HashMap<Integer, Integer> newitems = new HashMap<Integer, Integer>();
											newitems.put(cb.weapon, 1);
											OID itemOid = InventoryClient.findItem(obj.getOid(), cb.weapon);
											if (Log.loggingDebug)
												Log.debug("BehaviorSelector: oid: " + obj.getOid() + " find item " + itemOid);
											if (itemOid == null) {
												HashMap<Integer, Integer> rv = AgisInventoryClient.generateItems(obj.getOid(), newitems, true);
												if (Log.loggingDebug)
													Log.debug("BehaviorSelector: oid: " + obj.getOid() + " create " + newitems + " result " + rv);
												if (rv.size() == 0) {
													itemOid = InventoryClient.findItem(obj.getOid(), cb.weapon);
													if (Log.loggingDebug)
														Log.debug("BehaviorSelector: oid: " + obj.getOid() + " created find item " + itemOid + " add to items hashmap |");
													items.put(cb.weapon, itemOid);
												}
											} else {
												items.put(cb.weapon, itemOid);
												if (Log.loggingDebug)
													Log.debug("BehaviorSelector: oid: " + obj.getOid() + " created find item " + itemOid + " add to items hashmap ||");
											}
											if (items.containsKey(cb.weapon)) {
												boolean equiped = AgisInventoryClient.equipeItem(obj.getOid(), items.get(cb.weapon));
												if (Log.loggingDebug)
													Log.debug("BehaviorSelector: oid: " + obj.getOid() + " ww=" + equiped + " weapon=" + cb.weapon + " itemOid=" + items.get(cb.weapon));
											} else {
												if (Log.loggingDebug)
													Log.debug("BehaviorSelector: oid: " + obj.getOid() + " weapon not weapon=" + cb.weapon + " itemOid=" + items.get(cb.weapon) + " not found ");
											}
										}
									}
								} catch (Exception e) {
									// TODO Auto-generated catch block
									Log.exception("BehaviorSelector oid=" + obj.getOid(), e);
									e.printStackTrace();
								}
							} else {
								if (Log.loggingDebug)
									Log.debug("BehaviorSelector selected same");
								if (abilitySelectorTask == null) {
									BehaviorLogicTaskSchedule(cb.abilityinterval);
								}
							}
						} else {

							if (cb.behaviorType == 3) {
								InterpolatedWorldNode wn = obj.getWorldNode();
								if (wn != null) {
									Point mloc = wn.getCurrentLoc();
									List<OID> list = AgisMobPlugin.getFriendly(obj.getOid());
									HashMap<OID, Point> locations = new HashMap<OID, Point>();
									// Point dLoc = new Point();
									OID nearestTarget = null;
									float nearestTargetDist = Float.MAX_VALUE;

									for (OID oid : list) {
										Entity e = EntityManager.getEntityByNamespace(oid, Namespace.MOB);
										if (Log.loggingDebug)
											Log.debug("BehaviorLogic: oid " + oid + " isMob? " + e.getType().isMob());

										ObjectStub os = (ObjectStub) e;
										for (Behavior behav : os.getBehaviors()) {
											if (Log.loggingDebug)
												Log.debug("BehaviorLogic: oid " + oid + " behav " + behav);
											if (behav instanceof CombatBehavior) {
												CombatBehavior _cb = ((CombatBehavior) behav);
												List<Integer> tags = _cb.getTags();
												if (Log.loggingDebug)
													Log.debug("BehaviorLogic: oid " + oid + " tags " + tags);

												if (((CombatBehavior) behav).getTags().contains(cb.mobTag)) {

													EntityHandle mHandle = new EntityHandle(oid);
													ObjectStub tObj = (ObjectStub) mHandle.getEntity(Namespace.MOB);
													Point tLoc = tObj.getWorldNode().getLoc();
													float dist = Point.distanceTo(mloc, tLoc);
													locations.put(oid, tLoc);
													if (dist < nearestTargetDist) {
														nearestTargetDist = dist;
														nearestTarget = oid;
													}
													defendTargets.put(oid, dist);
												}
											} else {
												if (Log.loggingDebug)
													Log.debug("BehaviorLogic: oid " + oid + " no combat behavior ");
											}
										}
									}

									locations.forEach((k, v) -> {
										subscribeForTarget(k);
										subscribed.add(k);
									});
									if (nearestTarget != null) {

										defendedTarget = nearestTarget;
									}
									// }
								} else {

									if (Log.loggingDebug)
										Log.debug("BehaviorSelector: got null wnode during check ability for oid: " + obj.getOid());

								}
							}
							try {
								//ChatClient.sendChatMsg(obj.getOid(), "Mob_" + obj.getOid(), 2, "Mob_" + obj.getOid() + " Selected behavior profile " + behaviorId + " selected " + cb.behaviorType);
								EnginePlugin.setObjectProperty(obj.getOid(), Namespace.WORLD_MANAGER, "MobBehav", cb.behaviorType);
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
								Log.exception("BehaviorSelector set MobBehav for " + obj.getOid(), e);
							}
							if (Log.loggingDebug)
								Log.debug("BehaviorSelector selectedBehavior is null");
							if (cb.weapon > 0) {
								if (items.containsKey(cb.weapon)) {
									if (Log.loggingDebug)
										Log.debug("BehaviorSelector: oid: " + obj.getOid() + " found item in hashmap about to equip");
									boolean equiped = AgisInventoryClient.equipeItem(obj.getOid(), items.get(cb.weapon));
									if (Log.loggingDebug)
										Log.debug("BehaviorSelector: oid: " + obj.getOid() + " equiped=" + equiped + " weapon=" + cb.weapon + " itemOid=" + items.get(cb.weapon));

								} else {
									HashMap<Integer, Integer> newitems = new HashMap<Integer, Integer>();
									newitems.put(cb.weapon, 1);
									OID itemOid = InventoryClient.findItem(obj.getOid(), cb.weapon);
									if (Log.loggingDebug)
										Log.debug("BehaviorSelector: oid: " + obj.getOid() + " find item " + itemOid);
									if (itemOid == null) {
										HashMap<Integer, Integer> rv = AgisInventoryClient.generateItems(obj.getOid(), newitems, true);
										if (Log.loggingDebug)
											Log.debug("BehaviorSelector: oid: " + obj.getOid() + " create " + newitems + " result " + rv);
										if (rv != null && rv.size() == 0) {
											itemOid = InventoryClient.findItem(obj.getOid(), cb.weapon);
											if (Log.loggingDebug)
												Log.debug("BehaviorSelector: oid: " + obj.getOid() + " created find item " + itemOid + " add to items hashmap |");
											items.put(cb.weapon, itemOid);
										}
									} else {
										items.put(cb.weapon, itemOid);
										if (Log.loggingDebug)
											Log.debug("BehaviorSelector: oid: " + obj.getOid() + " created find item " + itemOid + " add to items hashmap ||");
									}
									if (items.containsKey(cb.weapon)) {
										boolean equiped = AgisInventoryClient.equipeItem(obj.getOid(), items.get(cb.weapon));
										if (Log.loggingDebug)
											Log.debug("BehaviorSelector: oid: " + obj.getOid() + " equiped=" + equiped + " weapon=" + cb.weapon + " itemOid=" + items.get(cb.weapon));
									} else {
										if (Log.loggingDebug)
											Log.debug("BehaviorSelector: oid: " + obj.getOid() + " weapon not weapon=" + cb.weapon + " itemOid=" + items.get(cb.weapon) + " not found ");
									}
								}
							}
							if (cb.behaviorType == 5) {

								BehaviorLogicTaskSchedule(cb.abilityinterval);

							}
						}

						selectedBehavior = cb;
						if (Log.loggingDebug)
							Log.debug("BehaviorSelector break selectedBehavior="+selectedBehavior+" currentTarget="+currentTarget);
                        if(currentTarget != null && selectedBehavior != null) {
                            if (abilitySelectorTask != null) {
                                long d = abilitySelectorTask.getDelay(TimeUnit.NANOSECONDS);
                                if (Log.loggingDebug)
                                    Log.debug("BehaviorSelector abilitySelectorTask scheduled delay="+d+" currentTarget="+currentTarget);
                                if (d < 0) {
                                    BehaviorLogicTaskSchedule(0);
                                }
                            } else {
                                BehaviorLogicTaskSchedule(0);
                            }
                        }
						break;
					} else {
						if (Log.loggingDebug)
							Log.debug("BehaviorSelector false " + cb);
					}
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				Log.exception("BehaviorSelector " + obj.getOid(), e);
			}

			if (Log.loggingDebug)
				Log.debug("BehaviorSelector obj=" + obj.getOid() + " End");

		}
	}
	
	void moveDestinationCheck() {
		boolean result = false;
		if (Log.loggingDebug)Log.debug("moveDestinationCheck Start obj="+obj.getOid()+" behavior "+(selectedBehavior!=null?selectedBehavior.behaviorType+"":"bd"));
		if(selectedBehavior!=null)
		switch (selectedBehavior.behaviorType) {
		case 0:// melee
			if (selectedAbility > 0) {
				long delay = selectedBehavior.abilityinterval;
				AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
				long castingTime =calculateCastingTime( aa.getActivationTime());
                if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                    castingTime += aa.getAttackTime();
                }

                if (castingTime > delay)
					delay = castingTime;
				result = runAbility(selectedAbility);
				if (result) {
					BehaviorLogicTaskSchedule(delay);
				} else {
					// BehaviorLogicTaskSchedule( delay);
				}
			}
			break;
		case 1:// ranged offensive
            if (lastUsedAbility > 0) {
                AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
                if (!lastAA.getCastingInRun()) {
                    long castingTime = calculateCastingTime(lastAA.getActivationTime());
                    if(!lastAA.getCastingInRun() && lastAA.getActivationTime()>0) {
                        castingTime += lastAA.getAttackTime();
                    }

                    if (castingTime + lastUseAbility > System.currentTimeMillis()) {
                        return;
                    }
                }
            }
			if (selectedAbility > 0) {

				long delay = selectedBehavior.abilityinterval;
				AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
				long castingTime =calculateCastingTime( aa.getActivationTime());
                if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                    castingTime += aa.getAttackTime();
                }

                if (castingTime > delay)
					delay = castingTime;
				result = runAbility(selectedAbility);
				if (result) {
					BehaviorLogicTaskSchedule(delay);
				} else {
					// BehaviorLogicTaskSchedule( delay);
				}
			}
			break;
		case 2:// ranged defensive
            if (lastUsedAbility > 0) {
                AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
                if (!lastAA.getCastingInRun()) {
                    long castingTime = calculateCastingTime(lastAA.getActivationTime());
                    if(!lastAA.getCastingInRun() && lastAA.getActivationTime()>0) {
                        castingTime += lastAA.getAttackTime();
                    }

                    if (castingTime + lastUseAbility > System.currentTimeMillis()) {
                        return;
                    }
                }
            }
			if (selectedAbility > 0) {

				long delay = selectedBehavior.abilityinterval;
				AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
				long castingTime = calculateCastingTime( aa.getActivationTime());
                if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                    castingTime += aa.getAttackTime();
                }

                if (castingTime > delay)
					delay = castingTime;
				result = runAbility(selectedAbility);
				if (result) {
					BehaviorLogicTaskSchedule(delay);
				} else {
					// BehaviorLogicTaskSchedule( delay);
				}
			}
			break;
		case 3:// defend
			if (Log.loggingDebug)Log.debug("moveDestinationCheck behavior defend obj="+obj.getOid()+" defendedTarget="+defendedTarget+" currentTarget="+currentTarget);
            if (lastUsedAbility > 0) {
                AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
                if (!lastAA.getCastingInRun()) {
                    long castingTime = calculateCastingTime(lastAA.getActivationTime());
                    if(!lastAA.getCastingInRun() && lastAA.getActivationTime()>0) {
                        castingTime += lastAA.getAttackTime();
                    }

                    if (castingTime + lastUseAbility > System.currentTimeMillis()) {
                        return;
                    }
                }
            }
				if (defendedTarget == null) {
					InterpolatedWorldNode wn = obj.getWorldNode();
					if (wn != null) {
						Point mloc = wn.getCurrentLoc();
						List<OID> list = AgisMobPlugin.getFriendly(obj.getOid());
						HashMap<OID, Point> locations = new HashMap<OID, Point>();
						// Point dLoc = new Point();
						OID nearestTarget = null;
						float nearestTargetDist = Float.MAX_VALUE;

						for (OID oid : list) {
							Entity e = EntityManager.getEntityByNamespace(oid, Namespace.MOB);

							if (Log.loggingDebug)Log.debug("moveDestinationCheck: oid " + oid + " isMob? " + e.getType().isMob());

							ObjectStub tObj = (ObjectStub) e;
							boolean combatFound=false;
							for (Behavior behav : tObj.getBehaviors()) {
								if (Log.loggingDebug)Log.debug("moveDestinationCheck: oid " + oid + " behav " + behav);
								if (behav instanceof CombatBehavior) {
									CombatBehavior _cb = ((CombatBehavior) behav);
									combatFound=true;
									List<Integer> tags = _cb.getTags();
									if (Log.loggingDebug)Log.debug("moveDestinationCheck: obj="+obj.getOid()+" oid " + oid + " tags " + tags+" _cb.isDead="+_cb.isDead);
									//Check if Mob is dead and have mob Tag
									if (!_cb.isDead && _cb.getTags().contains(selectedBehavior.mobTag)) {

										Point tLoc = tObj.getWorldNode().getLoc();
										locations.put(oid, tLoc);
										if (tLoc != null) {
											float dist = Point.distanceTo(mloc, tLoc);
											if (dist < nearestTargetDist) {
												nearestTargetDist = dist;
												nearestTarget = oid;
											}
										}
									}
								} else {
									if (Log.loggingDebug)Log.debug("moveDestinationCheck: oid " + oid + " no combat behavior ");
								}
							}
						
						if (Log.loggingDebug)Log.debug("moveDestinationCheck: oid " + oid + "  combat behavior found "+combatFound);
						}
						 for (OID oid : locations.keySet()) {
							 if(!subscribed.contains(oid)) {
								 subscribed.add(oid);
								 subscribeForTarget(oid);
							 }
						 }
						if (nearestTarget != null) {
							defendedTarget = nearestTarget;
						}
					} else {
						if (Log.loggingDebug)Log.debug("moveDestinationCheck: got null wnode during check ability for oid: " + obj.getOid());
					}
				}else {
					List<OID> list = AgisMobPlugin.getFriendly(obj.getOid());
					HashMap<OID, Point> locations = new HashMap<OID, Point>();
					// Point dLoc = new Point();

					for (OID oid : list) {
						Entity e = EntityManager.getEntityByNamespace(oid, Namespace.MOB);

						if (Log.loggingDebug)Log.debug("moveDestinationCheck: oid " + oid + " isMob? " + e.getType().isMob());

						ObjectStub tObj = (ObjectStub) e;
						for (Behavior behav : tObj.getBehaviors()) {
							if (Log.loggingDebug)Log.debug("moveDestinationCheck: oid " + oid + " behav " + behav);
							if (behav instanceof CombatBehavior) {
								CombatBehavior _cb = ((CombatBehavior) behav);
								List<Integer> tags = _cb.getTags();
								if (Log.loggingDebug)Log.debug("moveDestinationCheck: obj="+obj.getOid()+" oid " + oid + " tags " + tags+" _cb.isDead="+_cb.isDead);
								//Check if Mob is dead and have mob Tag
								if (!_cb.isDead && _cb.getTags().contains(selectedBehavior.mobTag)) {

									locations.put(oid, null);
								}
							} else {
								if (Log.loggingDebug)Log.debug("moveDestinationCheck: oid " + oid + " no combat behavior ");
							}
						}
					
					if (Log.loggingDebug)Log.debug("moveDestinationCheck: oid " + oid + "  combat behavior found "+locations.size());
					}
					 for (OID oid : locations.keySet()) {
						 if(!subscribed.contains(oid)) {
							 subscribed.add(oid);
							 subscribeForTarget(oid);
						 }
					 }
				}
				
			if (Log.loggingDebug)Log.debug("moveDestinationCheck behavior defend  defendedTarget="+defendedTarget+" currentTarget="+currentTarget);
				if (defendedTarget != null && currentTarget == null) {
					InterpolatedWorldNode wn = obj.getWorldNode();
					if (wn != null) {
						Point mloc = wn.getCurrentLoc();
						EntityHandle eHandle = new EntityHandle(defendedTarget);
						ObjectStub tObj = (
						ObjectStub) eHandle.getEntity(Namespace.MOB);
						if (tObj != null) {
							float targetSpeed = speed;
							boolean dead = false;
							boolean combatFound=false;
							for (Behavior behav : tObj.getBehaviors()) {
								if (Log.loggingDebug)Log.debug("moveDestinationCheck: defendedTarget " + defendedTarget + " behav " + behav);
								if (behav instanceof BaseBehavior) {
									BaseBehavior _bb = ((BaseBehavior) behav);
									if(_bb.activated) {
										targetSpeed = _bb.mobSpeed;
									}else {
										targetSpeed = AgisMobClient.sendGetActorSpeed(tObj.getOid(),obj.getInstanceOid());
									}
									if (Log.loggingDebug)Log.debug("moveDestinationCheck: defendedTarget " + defendedTarget + " target speed " + _bb.mobSpeed+" "+targetSpeed);

								} else if (behav instanceof CombatBehavior) {
									CombatBehavior _cb = ((CombatBehavior) behav);
									combatFound=true;
									if (Log.loggingDebug)Log.debug("moveDestinationCheck: defendedTarget _cb.isDead="+_cb.isDead);
									dead = _cb.isDead;
								} else {
									if (Log.loggingDebug)Log.debug("moveDestinationCheck: defendedTarget " + defendedTarget + " no Base behavior ");
								}
							}
							if (Log.loggingDebug)Log.debug("moveDestinationCheck behavior defend  defendedTarget=" + defendedTarget + " dead "+dead+" combat behavior found "+combatFound); 
							if(!combatFound) {
								if (Log.loggingDebug)Log.debug("moveDestinationCheck behavior defend  defendedTarget=" + defendedTarget + " isDead reset target num behav "+tObj.getBehaviors().size());
								defendedTarget = null;
							} else if (tObj.getWorldNode() != null) {

								Point tLoc = tObj.getWorldNode().getLoc();
								AOVector tDir  = tObj.getWorldNode().getDir();
								float distance = Point.distanceTo(mloc, tLoc);
								if (distance > 2 + eHandle.getHitBox() + hitBoxRange) {
									Point _followLoc = new Point(tLoc);
									if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " tDir="+(tDir!=null?tDir.length():"bd"));
									_followLoc.sub(mloc);
									//_followLoc.setY(0);
									if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " sub my loc ");
									float len = (float) Math.sqrt(_followLoc.getX() * _followLoc.getX() + _followLoc.getY() * _followLoc.getY() + _followLoc.getZ() * _followLoc.getZ());
									if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defendedTarget oid = " + obj.getOid() + " len=" + len);
									_followLoc.multiply(1 / len);
									if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * 1/len ");
									if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " distance=" + distance);

									
									float m = distance - 2 - eHandle.getHitBox() - hitBoxRange;

									if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defendedTarget oid = " + obj.getOid() + "  m= " + m + " hitBoxRange=" + hitBoxRange + " eHandle.getHitBox()=" + eHandle.getHitBox());
									_followLoc.multiply(m);
									if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * followDist - hitbox ");
									_followLoc.add(mloc);
									if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defendedTarget oid = " + obj.getOid() + "; myLoc = " + mloc + "; followLoc=" + tLoc + " _followLoc=" + _followLoc + " followDist=" + distance + " len="
											+ len + " target hitBox=" + eHandle.getHitBox() + " speed=" + speed + " distance=" + distance);
									// Point destLoc = _followLoc;
									float distance2 = Point.distanceTo(mloc, _followLoc);
									float distance3 = Point.distanceTo(tLoc, _followLoc);
									if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defendedTarget Math.abs(distance2 - distance) " + Math.abs(distance2 - distance) + " distance2=" + distance2 + " distance3=" + distance3 + " distance="
											+ distance);

									command = "Goto";
									goreturn = false;

									if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defendedTarget move to position " + _followLoc);
									Point newPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), _followLoc, obj.getInstanceOid());
									if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defendedTarget move to position " + newPoint +" speed="+speed+" targetSpeed="+targetSpeed);
									if (distance > m) {
										if (gotoLoc == null || (Point.distanceTo(gotoLoc, newPoint) > 0.3f && Point.distanceTo(mloc, newPoint) > 0.3f)) {
											gotoLoc = newPoint;
										    if (Log.loggingDebug)Log.debug("moveDestinationCheck: defendedTarget oid " + defendedTarget + " isMob? " + tObj.getType().isMob());
											
										  //  if (distance2 < 2 && !Float.isNaN(targetSpeed)) {
											//	Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, targetSpeed ));
										//	} else 
										    if (distance2 < 6 && !Float.isNaN(targetSpeed)) {
												Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, targetSpeed ));
											} else {
												Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, speed));
											}
										}
									}
								}
							}else {
								if (Log.loggingDebug)Log.debug("moveDestinationCheck behavior defend  defendedTarget=" + defendedTarget + " WorldNode of the object is null");
							}
							
						} else {
							if (Log.loggingDebug)Log.debug("moveDestinationCheck behavior defend  defendedTarget=" + defendedTarget + " object is null reset target");
							subscribeForTarget(defendedTarget);
							defendedTarget = null;
						}
					} else {
						if (Log.loggingDebug)Log.debug("BehaviorLogic: got null wnode during check ability for oid: " + obj.getOid());
					}

				} else if (currentTarget != null) {
					if (Log.loggingDebug)Log.debug("moveDestinationCheck behavior defend  currentTarget=" + currentTarget);
					for (AbilityGroup am : selectedBehavior.abilities) {
						if (!am.Calculate()) {
							if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defensive obj=" + obj + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate failed break");

							continue;
						}
						selectedAbilityGroup = am;

						if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defensive obj=" + obj.getOid() + " targetOid=" + currentTarget + " AbilityGroup= " + am + " calculate OK " + am.ids);

						selectedAbility = am.getAbility();
						if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defensive obj=" + obj.getOid() + " targetOid=" + currentTarget + " Abilityid= " + selectedAbility);
						// selectedAbility = aid;
						if(selectedAbility<1) {
							if (lastUsedAbility > 0) {
								AgisAbility lastAA = Agis.AbilityManager.get(lastUsedAbility);
								if (!lastAA.getCastingInRun()) {
									long castingTime = calculateCastingTime(lastAA.getActivationTime());
                                    if(!lastAA.getCastingInRun() && lastAA.getActivationTime()>0) {
                                        castingTime += lastAA.getAttackTime();
                                    }

                                    if (castingTime + lastUseAbility > System.currentTimeMillis()) {
										if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck lastUsedAbility " + lastUsedAbility + " mob casting ability no move "+(castingTime + lastUseAbility - System.currentTimeMillis() )+"ms");
									break;
									}
								}

							}
							InterpolatedWorldNode wn = obj.getWorldNode();
							if (wn != null) {
								Point mloc = wn.getCurrentLoc();
								EntityHandle eHandle = new EntityHandle(currentTarget);
								ObjectStub tObj = (
								ObjectStub) eHandle.getEntity(Namespace.MOB);
								if (tObj != null) {
									float targetSpeed = speed;
									boolean dead = false;
									boolean combatFound=false;
									for (Behavior behav : tObj.getBehaviors()) {
										if (Log.loggingDebug)Log.debug("moveDestinationCheck: defend currentTarget " + currentTarget + " behav " + behav);
										if (behav instanceof BaseBehavior) {
											BaseBehavior _bb = ((BaseBehavior) behav);
											if(_bb.activated) {
												targetSpeed = _bb.mobSpeed;
											}else {
												targetSpeed = AgisMobClient.sendGetActorSpeed(tObj.getOid(),obj.getInstanceOid());
											}
											if (Log.loggingDebug)Log.debug("moveDestinationCheck: defend currentTarget " + currentTarget + " target speed " + _bb.mobSpeed+" "+targetSpeed);

										} else if (behav instanceof CombatBehavior) {
											CombatBehavior _cb = ((CombatBehavior) behav);
											combatFound=true;
											if (Log.loggingDebug)Log.debug("moveDestinationCheck: defend currentTarget _cb.isDead="+_cb.isDead);
											dead = _cb.isDead;
										} else {
											if (Log.loggingDebug)Log.debug("moveDestinationCheck: defend currentTarget " + currentTarget + " no Base behavior ");
										}
									}
									if (Log.loggingDebug)Log.debug("moveDestinationCheck behavior defend currentTarget=" + currentTarget + " dead "+dead+" combat behavior found "+combatFound); 
									if(!combatFound) {
										if (Log.loggingDebug)Log.debug("moveDestinationCheck behavior defend  currentTarget=" + currentTarget + " isDead reset target num behav "+tObj.getBehaviors().size());
										defendedTarget = null;
									} else if (tObj.getWorldNode() != null) {

										Point tLoc = tObj.getWorldNode().getLoc();
										AOVector tDir  = tObj.getWorldNode().getDir();
										float distance = Point.distanceTo(mloc, tLoc);
										if (distance > 2 + eHandle.getHitBox() + hitBoxRange) {
											Point _followLoc = new Point(tLoc);
											if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " tDir="+(tDir!=null?tDir.length():"bd"));
											_followLoc.sub(mloc);
											//_followLoc.setY(0);
											if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " sub my loc ");
											float len = (float) Math.sqrt(_followLoc.getX() * _followLoc.getX() + _followLoc.getY() * _followLoc.getY() + _followLoc.getZ() * _followLoc.getZ());
											if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defendedTarget oid = " + obj.getOid() + " len=" + len);
											_followLoc.multiply(1 / len);
											if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * 1/len ");
											if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " distance=" + distance);

											
											float m = distance - 2 - eHandle.getHitBox() - hitBoxRange;

											if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defendedTarget oid = " + obj.getOid() + "  m= " + m + " hitBoxRange=" + hitBoxRange + " eHandle.getHitBox()=" + eHandle.getHitBox());
											_followLoc.multiply(m);
											if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defendedTarget oid = " + obj.getOid() + " _followLoc=" + _followLoc + " * followDist - hitbox ");
											_followLoc.add(mloc);
											if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defendedTarget oid = " + obj.getOid() + "; myLoc = " + mloc + "; followLoc=" + tLoc + " _followLoc=" + _followLoc + " followDist=" + distance + " len="
													+ len + " target hitBox=" + eHandle.getHitBox() + " speed=" + speed + " distance=" + distance);
											// Point destLoc = _followLoc;
											float distance2 = Point.distanceTo(mloc, _followLoc);
											float distance3 = Point.distanceTo(tLoc, _followLoc);
											if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defendedTarget Math.abs(distance2 - distance) " + Math.abs(distance2 - distance) + " distance2=" + distance2 + " distance3=" + distance3 + " distance="
													+ distance);

											command = "Goto";
											goreturn = false;

											if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend defendedTarget move to position " + _followLoc);
											Point newPoint = AgisMobPlugin.findNearestPoint(obj.getOid(), _followLoc, obj.getInstanceOid());
											if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend efendedTarget move to position " + newPoint +" speed="+speed+" targetSpeed="+targetSpeed);
											if (distance > m) {
												if (gotoLoc == null || (Point.distanceTo(gotoLoc, newPoint) > 0.3f && Point.distanceTo(mloc, newPoint) > 0.3f)) {
													gotoLoc = newPoint;
												    if (Log.loggingDebug)Log.debug("moveDestinationCheck: defend currentTarget oid " + currentTarget + " isMob? " + tObj.getType().isMob());
													
												  //  if (distance2 < 2 && !Float.isNaN(targetSpeed)) {
													//	Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, targetSpeed ));
												//	} else 
												    if (distance2 < 6 && !Float.isNaN(targetSpeed)) {
														Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, targetSpeed ));
													} else {
														Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, newPoint, speed));
													}
												}
											}
										}
									}else {
										if (Log.loggingDebug)Log.debug("moveDestinationCheck behavior defend  defendedTarget=" + defendedTarget + " WorldNode of the object is null");
									}
									
								} else {
									if (Log.loggingDebug)Log.debug("moveDestinationCheck behavior defend  defendedTarget=" + defendedTarget + " object is null reset target");
									subscribeForTarget(defendedTarget);
									defendedTarget = null;
								}
							} else {
								if (Log.loggingDebug)Log.debug("BehaviorLogic: got null wnode during check ability for oid: " + obj.getOid());
							}
							
							break;
						}
						long delay = selectedBehavior.abilityinterval;
						AgisAbility aa = Agis.AbilityManager.get(selectedAbility);
						long castingTime = calculateCastingTime( aa.getActivationTime());
                        if(!aa.getCastingInRun() && aa.getActivationTime()>0) {
                            castingTime += aa.getAttackTime();
                        }

                        if (castingTime > delay)
							delay = castingTime;
						result = runAbility(selectedAbility);
						if (result) {
							BehaviorLogicTaskSchedule(delay);
						} else {
							// BehaviorLogicTaskSchedule( delay);
						}
						break;
					}
			}
			if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: defend end");
			break;
		case 4:// flee
			if (selectedBehavior.fleeType == 0) {

			} else if (selectedBehavior.fleeType == 2) {
//				InterpolatedWorldNode wn = obj.getWorldNode();
//				if (wn == null) {
//					if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck:  BehaviorLogic: got null wnode during check ability for oid: " + obj.getOid());
//					break;
//				}
//				// get mob location
//				Point mloc = wn.getCurrentLoc();
//
//				List<OID> list = AgisMobPlugin.getFriendly(obj.getOid());
//				HashMap<OID, Point> locations = new HashMap<OID, Point>();
//				Point dLoc = new Point();
//
//                if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: BehaviorLogic: flee oid " + obj.getOid() + "  friends " + list + " ");
//                HashMap<Float, ArrayList<OID>> gtarget  =  new HashMap<>();
//                for (OID oid : list) {
//                    // Entity e = EntityManager.getEntityByNamespace(oid, Namespace.MOB);
//                    EntityHandle mHandle = new EntityHandle(oid);
//                    ObjectStub tObj = (ObjectStub) mHandle.getEntity(Namespace.MOB);
//                    Point tLoc = tObj.getWorldNode().getLoc();
//                    float dist = Point.distanceTo(mloc, tLoc);
//                    if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: BehaviorLogic: flee oid " + obj.getOid() + " friend " + oid + " dist="+dist+" MOB_COMBAT_BEHAVIOR_FLEE_ALLIES_CHECK_MAX_DISTANCE="+AgisMobPlugin.MOB_COMBAT_BEHAVIOR_FLEE_ALLIES_CHECK_MAX_DISTANCE);
//                    if (dist < AgisMobPlugin.MOB_COMBAT_BEHAVIOR_FLEE_ALLIES_CHECK_MAX_DISTANCE) {
//                        locations.put(oid, tLoc);
//                        dLoc.add(tLoc);
//                        AOVector reqDir1 = AOVector.sub(tLoc, mloc);
//                        float yaw = AOVector.getLookAtYaw(reqDir1);
//                        if(yaw < 0)
//                            yaw += 360;
//                        if(gtarget.size() == 0) {
//                            gtarget.computeIfAbsent(yaw , __->new ArrayList<OID>()).add(oid);
//                        } else {
//
//                            boolean found =false;
//                            Set<Float> yaws = new HashSet<>(gtarget.keySet());
//                            for (float _yaw : yaws){
//                                if(_yaw < 30 &&( 360 - _yaw < yaw || _yaw + 30 >yaw)){
//                                    gtarget.computeIfAbsent(_yaw , __->new ArrayList<OID>()).add(oid);
//                                    found = true;
//                                } else if(_yaw > 330 &&( _yaw - 30 < yaw || 360 - _yaw + 30 > yaw)){
//                                    gtarget.computeIfAbsent(_yaw , __->new ArrayList<OID>()).add(oid);
//                                    found = true;
//                                } else if(( _yaw - 30 < yaw && _yaw + 30 > yaw)){
//                                    gtarget.computeIfAbsent(_yaw , __->new ArrayList<OID>()).add(oid);
//                                    found = true;
//                                }
//                            }
//                            if(!found) {
//                                gtarget.computeIfAbsent(yaw , __->new ArrayList<OID>()).add(oid);
//                            }
//                        }
//
//                    }
//
//                    if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: BehaviorLogic: flee oid " + obj.getOid() + " friend " + oid + " dloc="+dLoc+" tLoc="+tLoc);
//
//                }
//                if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: BehaviorLogic: flee oid " + obj.getOid() + " friends locations=" + locations+" gtarget="+gtarget);
//                ArrayList <OID> targets = new ArrayList<>();
//                for (float _yaw : gtarget.keySet()) {
//                    if(targets.size()<gtarget.get(_yaw).size()){
//                        targets = gtarget.get(_yaw);
//                    }
//                }
//                if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: BehaviorLogic: flee oid " + obj.getOid() + " friends locations=" + locations);
//                dLoc.multiply(1F / locations.size());
//
//                if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: BehaviorLogic: flee oid " + obj.getOid() + " phase 1 move to friends " + dLoc + " ");
//                if(targets.size() > 0) {
//                    dLoc = locations.get(targets.get(0));
//                }
//                if (Log.loggingDebug)Log.debug("CombatBehavior.moveDestinationCheck: BehaviorLogic: flee oid " + obj.getOid() + " phase 1 move to friends Loc " + dLoc + " ");
//				command = "Stop";
//				Engine.getAgent().sendBroadcast(new BaseBehavior.StopCommandMessage(obj.getOid()));
//				CombatClient.sendStartCombatState(obj.getOid());
//				command = "Goto";
//				goreturn = false;
//
//				Engine.getAgent().sendBroadcast(new BaseBehavior.GotoCommandMessage(obj, dLoc, speed));
//				gotoLoc = dLoc;
			}
			break;
		case 5:// heal
			if (Log.loggingDebug)Log.debug("moveDestinationCheck heaql Start obj="+obj.getOid());
			
			BehaviorLogic();
			if (Log.loggingDebug)Log.debug("moveDestinationCheck heaql End obj="+obj.getOid());
			break;
		}
		
		if (Log.loggingDebug)Log.debug("moveDestinationCheck End");
	}

	public class MoveDestinationCheckTask implements Runnable {
		public MoveDestinationCheckTask() {
		}

		@Override
		public void run() {

				if (Log.loggingDebug)
                    Log.debug("MoveDestinationCheckTask.run " + obj.getOid() + " activated=" + activated+" selectedAbility="+selectedAbility+" Instance="+obj.getInstanceOid());
			long start = System.currentTimeMillis();
			try {
				//lock.lock();
				if (activated == false) {
					return;
				}
				if(isDead) {
					if (Log.loggingDebug)
						Log.debug("MoveDestinationCheckTask oid=" + obj.getOid() + " mob is Dead");
					selectedBehavior = null;
					return;
				}

				if (Log.loggingDebug)
					if (Log.loggingDebug)Log.debug("MoveDestinationCheckTask.run || " + obj.getOid()+" command "+ command+" abilitySelectorTask time="+(abilitySelectorTask!=null?abilitySelectorTask.getDelay(TimeUnit.MILLISECONDS):" no abilitySelectorTask") );
				try {
					if (command == "Goto") {
						if (abilitySelectorTask != null) {
							long d = abilitySelectorTask.getDelay(TimeUnit.NANOSECONDS);
							if (d < 5_000_000) {
								if (!abilitySelectorTask.isDone()) {
									long end = System.currentTimeMillis();
									if (Log.loggingDebug)
										if (Log.loggingDebug)Log.debug("MoveDestinationCheckTask.run abilitySelectorTask not end d="+d+"ns End ");
									return;
								}
							}
						}
						moveDestinationCheck();
					} else if (command == "Stop") {
						if(selectedBehavior!=null && selectedBehavior.behaviorType == 3) {
							moveDestinationCheck();
						}
						
						if(selectedBehavior!=null && currentTarget!=null &&  System.currentTimeMillis() - lastUseAbility >  selectedBehavior.abilityinterval 
								&& (abilitySelectorTask==null ||(abilitySelectorTask!=null && abilitySelectorTask.getDelay(TimeUnit.MILLISECONDS)<0L))) {
							//moveDestinationCheck();
							if (Log.loggingDebug)
								if (Log.loggingDebug)Log.debug("MoveDestinationCheckTask.run || " +obj.getOid()+"  reshedule? bhav type= "+selectedBehavior.behaviorType);
							
						}
					} else {
						if (Log.loggingDebug)Log.debug("MoveDestinationCheckTask.run: invalid command");
						if(selectedBehavior!=null && ( selectedBehavior.behaviorType == 3 || selectedBehavior.behaviorType == 5)) {
							moveDestinationCheck();
						}
					}

				} catch (Exception e) {
					Log.exception("MoveDestinationCheckTask.run caught exception "+ obj.getOid()+" raised during run for command = " + command, e);
					//throw new RuntimeException(e);
				}
			} finally {
				//lock.unlock();
			}
			long end = System.currentTimeMillis();
			if (Log.loggingDebug)
				if (Log.loggingDebug)Log.debug("MoveDestinationCheckTask.run || End " + (end - start) + " ms");
		}
	}
	
}
