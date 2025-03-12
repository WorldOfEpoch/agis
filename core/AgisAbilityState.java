package atavism.agis.core;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import atavism.agis.abilities.FriendlyEffectAbility;
import atavism.agis.objects.*;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.server.math.AOVector;
import atavism.server.math.Quaternion;
import com.github.benmanes.caffeine.cache.*;

import atavism.agis.core.AgisAbility.AbilityResult;
import atavism.agis.core.AgisAbility.ActivationState;
import atavism.agis.core.AgisAbility.AoeType;
import atavism.agis.core.AgisAbility.TargetType;
import atavism.agis.core.AgisEffect.EffectState;
import atavism.agis.effects.StealthEffect;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.plugins.VoxelClient;
import atavism.agis.util.EventMessageHelper;
import atavism.msgsys.*;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.math.Point;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.telemetry.Prometheus;
import atavism.server.util.AORuntimeException;
import atavism.server.util.Log;

public class AgisAbilityState implements Runnable, MessageCallback, MessageDispatch {
    public AgisAbilityState(AgisAbility ability, CombatInfo source, CombatInfo target, AgisItem item, Point loc, int claimId, int claimObjId, Point destLoc) {
        if (ability.targetSubType == AgisAbility.TargetSubType.SELF) {
            target = source;
        }
        this.ability = ability;
        this.source = source;
        if (target == null) {
            this.target = source;
        } else {
			this.target = target;
			if (ability.getEnemyTargetChangeToSelf() && (ability instanceof FriendlyEffectAbility || ability.targetSubType == AgisAbility.TargetSubType.FRIEND)) {
				if (source.getAttackableTargets().containsKey(target.getOwnerOid())) {
					this.target = source;
				}
			}
		}
        this.item = item;
		this.location = loc;
		this.destLocation = destLoc;
		this.claimID = claimId;
        this.claimObjID = claimObjId;
        startTime = System.currentTimeMillis();
        if (ability.getActivationTime() > 0 || (ability.getChannelled() && !ability.getChannelledInRun())|| ability.isInterruptible()) {
	    	// Set up a message hook for when the caster is moving so we can interrupt
	    	SubjectFilter filter = new SubjectFilter(source.getOwnerOid());
	        filter.addType(CombatClient.MSG_TYPE_INTERRUPT_ABILITY);
	        sub = Engine.getAgent().createSubscription(filter, this, MessageAgent.NON_BLOCKING);
	        Log.debug("AGIS ABILITY: subscribed to interrupt message");
            Prometheus.registry().timer("start_ability_subscription", "ability",
                    ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - startTime));
            CURRENT_STATES.put(source.getOwnerOid() + "_" + ability.getName(), this);
        }
        
        if(ability.targetType == TargetType.AREA || ability.targetType == TargetType.LOCATION) {
        	this.maxChank = (int)(ability.areaOfEffectRadius / ability.chunk_length);
        	this.nextChank = 0;
        	
        }
    }

	private void removeSubscription() {
		if (sub != null) {
			Engine.getAgent().removeSubscription(sub);
			sub = null;
		}
	}

    /**
     * process network messages
     */
	public void handleMessage(Message msg, int flags) {
		if (Log.loggingDebug)
			Log.debug("AGIS ABILITY: got handleMessage " + msg.getMsgType() + " " + this);
		if (msg instanceof CombatClient.interruptAbilityMessage) {
			CombatClient.interruptAbilityMessage msgInter = (CombatClient.interruptAbilityMessage) msg;
			if (System.currentTimeMillis() - startTime < 50) {
				if (Log.loggingDebug)
					Log.debug("AGIS ABILITY: interruptAbilityMessage skip first 50 ms");
				return;
			} else {
				if (Log.loggingDebug)
					Log.debug("AGIS ABILITY: interruptAbilityMessage started " + (System.currentTimeMillis() - startTime)+" ms");
			}
			processInterrupt(msgInter.getForce(), msgInter.getMove(), msgInter.getChance());

		} else {
			Log.error("unknown msg: " + msg);
		}
	}

   /* public void dispatchMessage(Message message, int flags, MessageCallback callback)
    {
        Engine.defaultDispatchMessage(message, flags, callback);
    }*/
    
    
    private void processInterrupt(boolean force, boolean move , float chance) {
    	if(Log.loggingDebug)	Log.debug("AGIS ABILITY: got ability interrupt "+ability+ " force="+force+" move="+move+" chance="+chance+"  started " + (System.currentTimeMillis() - startTime)+" ms "+state);
    	
		if (!force && !move && ability.isInterruptible()) {
			Random _r = new Random();
			float cChance =  chance * ability.getInterruptChance() /100F / 100F;
			float _roll = _r.nextFloat();
			if (Log.loggingDebug)
				Log.debug("AGIS ABILITY: processInterrupt calculated chance=" + cChance + " roll=" + _roll);
			if(_roll > cChance) {
				Log.debug("AGIS ABILITY: processInterrupt inerruption failed !!!");
				return;
			}
			
			double interruptChance = 1f;
			if (CombatPlugin.INTERRUPTION_CHANCE_STAT != null) {
				double statValue = source.statGetCurrentValueWithPrecision(CombatPlugin.INTERRUPTION_CHANCE_STAT);
				if (Log.loggingDebug)
					Log.debug("AGIS ABILITY: interruptChance statValue: " + statValue + " of " + CombatPlugin.INTERRUPTION_CHANCE_STAT);
				double calculated = 0;
				if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.INTERRUPTION_CHANCE_STAT)) {
					StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.INTERRUPTION_CHANCE_STAT);
					double pointsCalculated = 0;
					for (int i = 1; i <= def.getPoints().size(); i++) {
						if (Log.loggingDebug)
							Log.debug("AGIS ABILITY: interruptChance i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated="
									+ calculated);
						if (statValue <= def.getThresholds().get(i)) {
							Log.debug("AGIS ABILITY: interruptChance statValue < th");
							if (statValue - pointsCalculated < 0)
								break;
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += statValue - pointsCalculated;
						} else {
							Log.debug("AGIS ABILITY: interruptChance statValue > th");
							calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
						}
					}
					/*
					 * if (pointsCalculated < statValue) { calculated += Math.round((statValue -
					 * pointsCalculated) / def.getPoints().get(def.getPoints().size())); }
					 */
				} else {
					calculated = statValue;
				}
				if (Log.loggingDebug)
					Log.debug("AGIS ABILITY: interruptChance calculated: " + calculated);
				interruptChance = calculated;
				if (Log.loggingDebug)
					Log.debug("AGIS ABILITY: interruptChance calculated=" + calculated + " mod=" + interruptChance);
			}

			double interruptRes = 1f;
			if (CombatPlugin.INTERRUPTION_RESISTANCE_STAT != null) {
				double statValue = source.statGetCurrentValueWithPrecision(CombatPlugin.INTERRUPTION_RESISTANCE_STAT);
				if (Log.loggingDebug)
					Log.debug("AGIS ABILITY: interruptRes statValue: " + statValue + " of " + CombatPlugin.INTERRUPTION_RESISTANCE_STAT);
				double calculated = 0;
				if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.INTERRUPTION_RESISTANCE_STAT)) {
					StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.INTERRUPTION_RESISTANCE_STAT);
					double pointsCalculated = 0;
					for (int i = 1; i <= def.getPoints().size(); i++) {
						if (Log.loggingDebug)
							Log.debug("AGIS ABILITY: interruptRes i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated="
									+ calculated);
						if (statValue <= def.getThresholds().get(i)) {
							Log.debug("AGIS ABILITY: interruptRes statValue < th");
							if (statValue - pointsCalculated < 0)
								break;
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += statValue - pointsCalculated;
						} else {
							Log.debug("AGIS ABILITY: interruptRes statValue > th");
							calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
							pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
						}
					}
					/*
					 * if (pointsCalculated < statValue) { calculated += Math.round((statValue -
					 * pointsCalculated) / def.getPoints().get(def.getPoints().size())); }
					 */
				} else {
					calculated = statValue;
				}
				if (Log.loggingDebug)
					Log.debug("AGIS ABILITY: interruptRes calculated: " + calculated);
				interruptRes = calculated;
				if (Log.loggingDebug)
					Log.debug("AGIS ABILITY: stunRes calculated=" + calculated + " mod=" + interruptRes);
			}
			if (Log.loggingDebug)
				Log.debug("AGIS ABILITY: interruptRes=" + interruptRes + " interruptChance=" + interruptChance);

			if (CombatPlugin.STAT_RANDOM_HIT_INTERRUPTION > 0) {
				Random rand = new Random();
				interruptRes = interruptRes + interruptRes *  CombatPlugin.STAT_RANDOM_HIT_INTERRUPTION * rand.nextFloat();
				interruptChance = interruptChance + interruptChance *  CombatPlugin.STAT_RANDOM_HIT_INTERRUPTION * rand.nextFloat();
			}
			
			if(interruptRes > interruptChance) {
				Log.debug("AGIS ABILITY: processInterrupt inerruption failed Resistance to high !!!");
				return;
			}
			
			if ((state == ActivationState.INIT || state == ActivationState.ACTIVATING || state == ActivationState.CHANNELLING)) {
				state = ActivationState.INTERRUPTED;
				updateState();
				AgisAbility.interruptAbility(this, AbilityResult.INTERRUPTED);
			}
			
		} else if (move || force) {

			if ((state == ActivationState.INIT || state == ActivationState.ACTIVATING) && ability.getCastingInRun() && move) {
				if (Log.loggingDebug)
					Log.debug("AGIS ABILITY: got ability interrupt " + ability + " getCastingInRun=" + ability.getCastingInRun());

				return;
			}

			if ((state == ActivationState.CHANNELLING) && ability.getChannelledInRun() && move) {
				if (Log.loggingDebug)
					Log.debug("AGIS ABILITY: got ability interrupt " + ability + " getChannelledInRun=" + ability.getChannelledInRun());
				return;
			}

		
			if (ability.aoeType == AoeType.LOCATION_RADIUS && (ability.targetType == TargetType.AREA || ability.targetType == TargetType.LOCATION)
					&& (state == ActivationState.ACTIVATED || state == ActivationState.COMPLETED)) {
				if (Log.loggingDebug)
					Log.debug("AGIS ABILITY: got ability interrupt " + ability + " but aoe location ability is casted");
				return;
			}
			state = ActivationState.INTERRUPTED;
			updateState();
			if (force)
				AgisAbility.interruptAbility(this, AbilityResult.FORCE_INTERRUPTED);
			else
				AgisAbility.interruptAbility(this, AbilityResult.INTERRUPTED);
		}
    }

	public ActivationState nextState() {
		ActivationState newState;

		switch (state) {
		case INIT:
			newState = ActivationState.ACTIVATING;
			break;
		case ACTIVATING:
			if (ability.getChannelled()) {
				newState = ActivationState.ACTIVATED;
				break;
			}
			if (ability.getPersistent()) {
				newState = ActivationState.ACTIVATED;
				break;
			}
			if (ability.aoeType == AoeType.LOCATION_RADIUS && (ability.targetType == TargetType.AREA || ability.targetType == TargetType.LOCATION)) {
				newState = ActivationState.ACTIVATED;
				break;
			}
			newState = ActivationState.COMPLETED;
			break;
		case CHANNELLING:
			if (ability.getPersistent()) {
				newState = ActivationState.ACTIVATED;
				break;
			}
			newState = ActivationState.COMPLETED;
			break;
		case INTERRUPTED:
			newState = ActivationState.FAILED;
			break;
		case FAILED:
			newState = ActivationState.FAILED;
			break;
			
		case COMPLETED:
			newState = ActivationState.COMPLETED;
			break;
		default:
			if (ability.getChannelled()) {
				newState = ActivationState.CHANNELLING;
				break;
			}
			if (Log.loggingDebug)
				Log.debug("AgisAbility.nextState: invalid state=" + state);
			newState = ActivationState.COMPLETED;
			break;
		}
		if (Log.loggingDebug)
			Log.debug("AgisAbility.nextState: switching from " + state + " to " + newState);
		return newState;
	}

    public void run() {
        try {
			Log.debug("AUTO: running AgisAbilityState " + this + " state=" + state + " cras=" + source.currentRunAbilities() + " run");
			if (delayed) {
				delayed = false;
				if (!ability.getSkipChecks()) {
					long t0 = System.currentTimeMillis();
					// First check cooldowns before going any further
					if (!Cooldown.checkReady(ability.cooldownMap.values(), source)) {
						if (Log.loggingDebug)
							Log.debug("AgisAbility.startAbility delayed cooldowns are not ready yet");
						ExtendedCombatMessages.sendErrorMessage(source.getOid(), "cooldownNoEnd");
						Prometheus.registry().timer("start_ability_cooldowns", "ability",
								ability.getClass().getSimpleName()).record(Duration.ofMillis(System.currentTimeMillis() - t0));
						return;
					}
				}
				updateState();
			} else {
				if (ability != null) {
					if (ability.isToggle()) {
						if (!source.currentRunAbilities().values().contains(this)) {
							Log.debug("AUTO: running AgisAbilityState " + this + " state=" + state + " this ability state should not run break");
							return;
						}
					}
				}
				updateState();
			}
		}
        catch (Exception e) {
            Log.exception("AgisAbility.State.run: got Exception", e);
        }
    }

    // this is executed when the ability needs to update
    public boolean updateState() {
        boolean result = updateStateInternal();
        if (!result || state == ActivationState.INTERRUPTED || state == ActivationState.COMPLETED
                || state == ActivationState.FAILED) {
            removeSubscription();
        }
        return result;
    }

    private boolean updateStateInternal() {
		Long startTime = System.currentTimeMillis();
		if (potentialTargets == null) {
			if (state == ActivationState.INIT) {
				potentialTargets = ability.getPotentialTargets(this);
				Collections.sort(potentialTargets, new Comparator<CombatInfo>() {
					@Override
					public int compare(CombatInfo o1, CombatInfo o2) {
						return o1.getOid().compareTo(o2.getOid());
					}
				});
			}
		}
		List<Lock> requiredLocks = new ArrayList<Lock>();
		requiredLocks.add(source.getLock());
		if (potentialTargets == null && target != null && ability.targetType != TargetType.AREA) {
			requiredLocks.add(target.getLock());
			if (Log.loggingDebug) Log.debug("LOCK: locked target: " + target.getOid());
		} else if (ability.targetType != TargetType.AREA) {

			for (CombatInfo potentialTarget : potentialTargets) {
				requiredLocks.add(potentialTarget.getLock());
				if (Log.loggingDebug) Log.debug("LOCK: locked target: " + target.getOid());
			}
		}

		try {
			//       ObjectLockManager.lockAll(requiredLocks);

			if (Log.loggingDebug)
				Log.debug("AgisAbility.updateState: got locks state:" + state + " ability:" + ability + " source:" + source + " target:" + target + " AgisAbilityState:" + this);
			double castingMod = 1f;
			if (CombatPlugin.ABILITY_CAST_TIME_MOD_STAT != null) {
				double statValue = getSource().statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_CAST_TIME_MOD_STAT);
				if (Log.loggingDebug)
					Log.debug("AgisAbility: CastTimeMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_CAST_TIME_MOD_STAT);
				double calculated = 0;
				if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_CAST_TIME_MOD_STAT)) {
					StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_CAST_TIME_MOD_STAT);
					int pointsCalculated = 0;
					for (int i = 1; i <= def.getPoints().size(); i++) {
						if (Log.loggingDebug)
							Log.debug("AgisAbility: CastTimeMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
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
						/*if (pointsCalculated < statValue) {
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						}*/
				} else {
					calculated = statValue;
				}
				if (Log.loggingDebug)
					Log.debug("AgisAbility: CastTimeMod calculated: " + calculated);
				castingMod = calculated / 100f;
				if (Log.loggingDebug)
					Log.debug("AgisAbility: CastTimeMod calculated=" + calculated + " mod=" + castingMod);

			}



			AbilityResult result;

			switch (state) {
				case INIT:
					if (ability.getWeaponMustBeDrawn()) {
						boolean weaponIsDrawn = source.getBooleanProperty("weaponIsDrawn");
						if (!weaponIsDrawn) {
							if (ability.getDrawnWeaponBefore()) {
								AbilityResult ar = ability.checkEquip(source, target, null);
								if (ar != AbilityResult.SUCCESS) {
									ExtendedCombatMessages.sendAbilityFailMessage(source, ar, ability.getID(), "");
									Log.error("AgisAbility.updateState: " + ability.getID() + " " + ability.getName() + " weapon is not drawn");
									return false;
								}
								HashMap<String, Serializable> response = AgisInventoryClient.DrawWeapon(source.getOid(), ability.weaponReq);
								if(response.containsKey("error")){
								 Log.warn("DrawWeapon return "+response.get("error"));
								} else {
									long time = (long) response.get("time");
									//delayed =true;
									scheduleDelay(time);
									return false;
								}
							} else {
								Log.error("AgisAbility.updateState: " + ability.getID() + " " + ability.getName() + " weapon is not drawn");
								return false;
							}
						}
					} else {
						if (ability.getDrawnWeaponBefore()) {
							boolean weaponIsDrawn = source.getBooleanProperty("weaponIsDrawn");
							if (!weaponIsDrawn) {

								AbilityResult ar = ability.checkEquip(source, target, null);
								if (ar != AbilityResult.SUCCESS) {
									ExtendedCombatMessages.sendAbilityFailMessage(source, ar, ability.getID(), "");
									Log.error("AgisAbility.updateState: " + ability.getID() + " " + ability.getName() + " weapon is not drawn");
									return false;
								}
								HashMap<String, Serializable> response = AgisInventoryClient.DrawWeapon(source.getOid(), ability.weaponReq);
								if(response.containsKey("error")){
									Log.warn("DrawWeapon return "+response.get("error"));
								} else {
									long time = (long) response.get("time");
									//delayed =true;
									scheduleDelay(time);
									return false;
								}
							}
						}
					}


					if (Log.loggingDebug)
						Log.debug("ABILITY: INIT " + ability + "  on source: " + source + "| target:" + target + " ");
					result = ability.checkAbility(source, target, state, this, false);

					if (Log.loggingDebug)
						Log.debug("ABILITY: INIT " + ability + "  on source: " + source + "| target:" + target + " checkAbility AbilityResult=" + result);
					if ((result == AbilityResult.OUT_OF_RANGE || result == AbilityResult.NOT_IN_FRONT) && !ability.getReqTarget() && ability.aoeType != AoeType.LOCATION_RADIUS) {
						target = source;
						result = AbilityResult.SUCCESS;
					}

					if (Log.loggingDebug)
						Log.debug("ABILITY: INIT " + ability + "  on source: " + source + "| target:" + target + " AbilityResult=" + result);
					//    boolean vis = WorldManagerClient.CheckTargetVisibility(source.getOid(), point, null, true);


					if (result != AbilityResult.SUCCESS) {
						// we run init effects when we fail -- :(
						for (CoordinatedEffect effect : ability.getPowerUpDefinition(getPowerUptime()).getCoordEffects(ActivationState.INIT)) {
							effect.putArgument("abilityID", ability.getID());
							effect.putArgument("claimID", claimID);
							effect.putArgument("claimObjID", claimObjID);
							effect.putArgument("castingMod", (int) Math.round( castingMod * 1000));
							getCoordinatedEffectStates().add(effect.invoke(getSourceOid(), getTargetOid(), location, this, getDestLocationForCoord()));
						}
						AgisAbility.interruptAbility(this, result);
						source.abilityFailed(ability.id);
						return false;
					}
					if (ability.getStealthReduce()) {
						ArrayList<EffectState> targetEffects = new ArrayList<EffectState>(source.getCurrentEffects());
						for (EffectState effect : targetEffects) {
							if (effect.getEffect() instanceof StealthEffect) {
								AgisEffect.removeEffect(effect);
							}
						}
						if (CombatPlugin.STEALTH_STAT != null) {
							//int val = source.statGetCurrentValue(CombatPlugin.STEALTH_STAT);
							if (ability.getStealthReductionAmount() != 0) {
								source.statAddModifier(CombatPlugin.STEALTH_STAT, "Ability", -ability.getStealthReductionAmount(), false);
							} else if (CombatPlugin.USE_ABILITY_STEALTH_REDUCTION != 0)
								source.statAddModifier(CombatPlugin.STEALTH_STAT, "Ability", -CombatPlugin.USE_ABILITY_STEALTH_REDUCTION, false);
							if (ability.getStealthReductionPercentage() != 0f) {
								source.statAddPercentModifier(CombatPlugin.STEALTH_STAT, "Ability", -ability.getStealthReductionPercentage(), false);
							} else if (CombatPlugin.USE_ABILITY_STEALTH_REDUCTION_PERCENTAGE != 0f)
								source.statAddPercentModifier(CombatPlugin.STEALTH_STAT, "Ability", -CombatPlugin.USE_ABILITY_STEALTH_REDUCTION_PERCENTAGE, false);
							if (ability.getStealthReductionPercentage() != 0f || ability.getStealthReductionAmount() != 0 ||
									(ability.getStealthReductionPercentage() == 0f && CombatPlugin.USE_ABILITY_STEALTH_REDUCTION_PERCENTAGE != 0f) ||
									(ability.getStealthReductionAmount() == 0 && CombatPlugin.USE_ABILITY_STEALTH_REDUCTION != 0)) {
								source.statSendUpdate(false);
								CombatPlugin.RunStealthReduceTimeOut(source.getOid(), ability);
							}
						}
					}
					if (ability.isToggle()) {

						source.getLock().lock();
						AgisAbilityState s = null;
						try {
							s = source.currentRunAbilities().put(ability.getID(), this);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							Log.exception(e);
							e.printStackTrace();
						} finally {
							source.getLock().unlock();
						}
						Log.debug("AgisAbilitystate Toggle currentRunAbilities put return " + s);
						if (s != null && s != this) {
							Log.debug("AgisAbilitystate Toggle currentRunAbilities put return is null and not same " + s);
							AgisAbility.deactivateAbility(s);
						}
						AgisAbility.sendToggleInfo(this);
					} else if (ability.getMakeBusy()) {
						source.setCurrentAction(this);
					}
					break;
				case ACTIVATING:
					if (Log.loggingDebug)
						Log.debug("ABILITY: ACTIVATING " + ability + "  on source: " + source + "| target:" + target + " ");
					boolean pulse = false;
					if(getDestLocation() != null){
						BasicWorldNode casterNode = WorldManagerClient.getWorldNode(source.getOwnerOid());
						// Point cLoc = casterNode.getLoc();
						Point _loc = new Point(0f,1f,0f);
						_loc.add(casterNode.getLoc());
						AOVector reqDir = AOVector.sub(getDestLocation(), _loc);
						float vYaw = AOVector.getLookAtYaw(reqDir);
						float vPitch = AOVector.getLookAtPitch(reqDir);
						float vRoll = AOVector.getLookAtRoll(reqDir);
						Quaternion casterQuaternion = casterNode.getOrientation();
						casterQuaternion.setEulerAngles(vPitch,vYaw,0);
						if(Log.loggingDebug) Log.debug("AgisAbilitystate ACTIVATING DestLocation vYaw="+vYaw+" vPitch="+vPitch+" vRoll="+vRoll+" casterQuaternion="+casterQuaternion+" Y="+casterQuaternion.getYAxis()+" X="+casterQuaternion.getXAxis()+" Z="+casterQuaternion.getZAxis());
						AOVector Loc2 = new AOVector(casterNode.getLoc());
						if(ability.getLineOfSight()) {
							Point visibilityResponse = WorldManagerClient.CheckTargetVisibilityReturnHitPoint(casterNode.getInstanceOid(), casterNode.getLoc(), getDestLocation());
							if (Log.loggingDebug)
								Log.debug("AgisAbility.getAoETargets DestLocation target visibilityResponse=" + visibilityResponse);
							if (visibilityResponse.equals(new Point())) {
								if(Log.loggingDebug) Log.debug("AgisAbilitystate ACTIVATING DestLocation LoS not hit range ="+((ability.targetType.equals(TargetType.AREA) && ability.aoeType.equals(AoeType.PLAYER_RADIUS)) ? ability.getAreaOfEffectRadius() : ability.maxRange));
								Loc2.add(casterQuaternion.getZAxis().multiply(((ability.targetType.equals(TargetType.AREA) && ability.aoeType.equals(AoeType.PLAYER_RADIUS)) ? ability.getAreaOfEffectRadius() : ability.maxRange)));
							} else {
								Float dist = Point.distanceTo(casterNode.getLoc(),visibilityResponse);
								if(Log.loggingDebug) Log.debug("AgisAbilitystate ACTIVATING DestLocation not LoS range dist ="+dist);
								Loc2.add(casterQuaternion.getZAxis().multiply(dist));
							}
						} else {
							if(Log.loggingDebug) Log.debug("AgisAbilitystate ACTIVATING DestLocation LoS range ="+((ability.targetType.equals(TargetType.AREA) && ability.aoeType.equals(AoeType.PLAYER_RADIUS)) ? ability.getAreaOfEffectRadius() : ability.maxRange));
							Loc2.add(casterQuaternion.getZAxis().multiply(((ability.targetType.equals(TargetType.AREA) && ability.aoeType.equals(AoeType.PLAYER_RADIUS)) ? ability.getAreaOfEffectRadius() : ability.maxRange)));
							AOVector Loc2_y = new AOVector(casterNode.getLoc());
							AOVector Loc2_x = new AOVector(casterNode.getLoc());
							Loc2_y.add(casterQuaternion.getYAxis().multiply(((ability.targetType.equals(TargetType.AREA) && ability.aoeType.equals(AoeType.PLAYER_RADIUS)) ? ability.getAreaOfEffectRadius() : ability.maxRange)));
							Loc2_x.add(casterQuaternion.getXAxis().multiply(((ability.targetType.equals(TargetType.AREA) && ability.aoeType.equals(AoeType.PLAYER_RADIUS)) ? ability.getAreaOfEffectRadius() : ability.maxRange)));
							if(Log.loggingDebug) Log.debug("AgisAbilitystate ACTIVATING DestLocation set new point Loc2="+Loc2+" Loc2_y="+Loc2_y+" Loc2_x="+Loc2_x);
						}
						if(Log.loggingDebug) Log.debug("AgisAbilitystate ACTIVATING DestLocation set new point "+Loc2);
						setDestLocation(Loc2.toPoint());
					}

					if (substate.size() == 0 || (nextSubState > 0L && substate.containsKey(nextSubState) && substate.get(nextSubState).pulse)) {
						pulse = true;
						result = ability.checkAbility(source, target, state, this, false);
						if (result != AbilityResult.SUCCESS) {
							AgisAbility.interruptAbility(this, result);
							return false;
						}

						if (nextPulse == 0) {
							if (!ability.completeActivationCasterConsume(this)) {
								state = ActivationState.FAILED;
								break;
							}
							//ability.completeActivationTargetConsume(this);
						}
					}
					if (Log.loggingDebug)
						Log.debug("ABILITY: ACTIVATING " + ability + "  on source: " + source + "| target:" + target + " nextPulse=" + nextPulse + " maxChank=" + maxChank + " substate.size=" + substate.size() + " substate=" + substate + " nextSubState=" + nextSubState + " contain " + substate.containsKey(nextSubState) + " " + (substate.containsKey(nextSubState) ? substate.get(nextSubState) : " ZBDS"));
					if (ability.getChannelled()) {
						if (Log.loggingDebug)
							Log.debug("ABILITY: ACTIVATING Channelled break");
						break;
					}
					if (ability.aoeType != AoeType.LOCATION_RADIUS) {
						if (substate.size() == 0 || (nextSubState > 0L && substate.containsKey(nextSubState) && substate.get(nextSubState).pulse)) {
							nextPulse++;
						}
						if (Log.loggingDebug)
							Log.debug("ABILITY: ACTIVATING nextPulse=" + nextPulse + " < " + ability.getChannelPulses());
						if ((ability.getChannelPulses() == -1 || nextPulse <= ability.getChannelPulses())
								&& (substate.size() == 0 || (nextSubState > 0L && substate.containsKey(nextSubState) && substate.get(nextSubState).pulse))) {
							if (Log.loggingDebug)
								Log.debug("ABILITY: ACTIVATING substate size =0 || pulse nextPulse=" + nextPulse + " nextSubState=" + nextSubState + " substate=" + substate);
							if (!ability.pulseChecking(this)) {
								Log.debug("ABILITY: ACTIVATING pulse checking failed !!");
								state = ActivationState.FAILED;
								break;
							}
							if (ability.getChannelPulses() == -1 || nextPulse < ability.getChannelPulses()) {

								schedule(ability.getChannelPulseTime());
							}
							if (substate.containsKey(nextSubState))
								substate.remove(nextSubState);

							if (Log.loggingDebug)
								Log.debug("ABILITY: ACTIVATING pulse nextPulse=" + nextPulse + " nextSubState=" + nextSubState + " substate=" + substate);

							//ability.pulseChannelling(this);
							if (maxChank == 0) {
								BasicWorldNode casterWNode = WorldManagerClient.getWorldNode(getSourceOid());
								Point casterLoc = casterWNode.getLoc();
								Point targetLoc = casterLoc;
								if (claimID > 0) {
									targetLoc = VoxelClient.getBuildingPosition(claimID, claimObjID);
								} else {
									BasicWorldNode targetWNode = WorldManagerClient.getWorldNode(getTargetOid());
									targetLoc = targetWNode.getLoc();
								}
								float distance = Point.distanceTo(casterLoc, targetLoc);
								long delay = 0L;
								if (ability.getSpeed() > 0)
									delay = (long) ((distance / ability.getSpeed()) * 1000F);
								if (Log.loggingDebug)
									Log.debug("ABILITY: ACTIVATING pulse distance=" + distance + " " + ((distance / ability.getSpeed()) * 1000F) + " delay=" + delay);
								ArrayList<CoordinatedEffectState> cess = new ArrayList<>();
								for(CoordinatedEffect effect : ability.getPowerUpDefinition(getPowerUptime()).getCoordEffects(ActivationState.ABILITY_PULSE)) {
									effect.putArgument("abilityID", ability.getID());
									effect.putArgument("claimID", claimID);
									effect.putArgument("claimObjID", claimObjID);
									effect.putArgument("castingMod", (int)Math.round( castingMod * 1000));
									CoordinatedEffectState ces = effect.invoke(getSourceOid(), getTargetOid(), location, this, getDestLocationForCoord());
									cess.add(ces);
									getCoordinatedEffectStates().add(ces);
								}
								AgisAbilityPulsState aaps = new AgisAbilityPulsState(this, nextPulse, cess);
								BasicWorldNode casterNode = WorldManagerClient.getWorldNode(source.getOwnerOid());
								if(casterNode!=null) {
									aaps.casterLoc = casterNode.getLoc();
									aaps.casterQuaternion = casterNode.getOrientation();
									aaps.casterInstance = casterNode.getInstanceOid();
								}

								ScheduledFuture<?> schedule = Engine.getExecutor().schedule(aaps, delay, TimeUnit.MILLISECONDS);
							} else if (maxChank > 0) {
								ArrayList<CoordinatedEffectState> cess = new ArrayList<>();
								for(CoordinatedEffect effect : ability.getPowerUpDefinition(getPowerUptime()).getCoordEffects(ActivationState.ABILITY_PULSE)) {
									effect.putArgument("abilityID", ability.getID());
									effect.putArgument("claimID", claimID);
									effect.putArgument("claimObjID", claimObjID);
									effect.putArgument("castingMod", (int)Math.round( castingMod * 1000));
									CoordinatedEffectState ces = effect.invoke(getSourceOid(), getTargetOid(), location, this, getDestLocationForCoord());
									cess.add(ces);
									getCoordinatedEffectStates().add(ces);
								}
								AgisAbilityPulsState aaps = new AgisAbilityPulsState(this, nextPulse, cess);
								BasicWorldNode casterNode = WorldManagerClient.getWorldNode(source.getOwnerOid());
								if(casterNode!=null) {
									aaps.casterLoc = casterNode.getLoc();
									aaps.casterQuaternion = casterNode.getOrientation();
									aaps.casterInstance = casterNode.getInstanceOid();
								}
								long delay = 0L;
								if (ability.getSpeed() > 0)
									delay = (long) (ability.areaOfEffectRadius / maxChank / ability.getSpeed() * 1000) / 2;
								ScheduledFuture<?> schedule = Engine.getExecutor().schedule(aaps, delay, TimeUnit.MILLISECONDS);
							}
							ability.pulseCaster(this);
							Log.debug("ABILITY: ACTIVATING pulse || nextPulse=" + nextPulse + " nextSubState=" + nextSubState + " substate=" + substate);

							if (ability.getChannelPulses() == -1 || nextPulse < ability.getChannelPulses()) {
								for (CoordinatedEffect effect : ability.getPowerUpDefinition(getPowerUptime()).getCoordEffects(ActivationState.COMPLETED)) {
									effect.putArgument("abilityID", ability.getID());
									effect.putArgument("claimID", claimID);
									effect.putArgument("claimObjID", claimObjID);
									effect.putArgument("castingMod", (int)Math.round( castingMod * 1000));
									getCoordinatedEffectStates().add(effect.invoke(getSourceOid(), getTargetOid(), location, this, getDestLocationForCoord()));
								}
								if (Log.loggingDebug)
									Log.debug("ABILITY: ACTIVATING  pulse END " + substate);

								return true;
							}
						} else if (substate.containsKey(nextSubState) && !substate.get(nextSubState).pulse) {
							if (Log.loggingDebug)
								Log.debug("ABILITY: ACTIVATING  !pulse " + substate);
							if (Log.loggingDebug)
								Log.debug("ABILITY: ACTIVATING  !pulse nextSubState" + nextSubState + " " + substate.get(nextSubState) + " maxChank=" + maxChank);

							Log.debug("ABILITY: ACTIVATING !pulse target start ");
							long st = System.currentTimeMillis();
							ability.getTargets(this);
							long st1 = System.currentTimeMillis();
							// ability.completeActivationTargetConsume(this);
							ability.pulseConsumeTarget(this);
							long st2 = System.currentTimeMillis();
							ability.pulseTarget(this);


							Log.debug("ABILITY: ACTIVATING !pulse target end || T1=" + (System.currentTimeMillis() - st) + "ms T2=" + (System.currentTimeMillis() - st1) + "ms T3=" + (System.currentTimeMillis() - st2) + "ms " + substate.get(nextSubState));
							if (maxChank > 1 && substate.get(nextSubState).lastChank < maxChank - 1) {
								if (Log.loggingDebug)
									Log.debug("ABILITY: ACTIVATING  !pulse  Create SubAbilityState " + substate.get(nextSubState).lastChank + "<" + maxChank);
								SubAbilityState sasc = new SubAbilityState();
								sasc.pulse = false;
								sasc.numPulse = substate.get(nextSubState).numPulse;
								sasc.createTime = startTime;//System.currentTimeMillis();
								sasc.sheduleTime = (long) (ability.areaOfEffectRadius / maxChank / ability.getSpeed() * 1000);
								sasc.lastChank = substate.get(nextSubState).lastChank + 1;
								sasc.targets = substate.get(nextSubState).targets;
								substate.put(sasc.createTime, sasc);
							}

							if (Log.loggingDebug)
								Log.debug("ABILITY: ACTIVATING  !pulse remove  nextSubState" + nextSubState + " " + substate.get(nextSubState) + " maxChank=" + maxChank);
							substate.remove(nextSubState);

							if (substate.size() > 0) {
								long lower = 0L;
								for (SubAbilityState s : substate.values()) {
									if (lower == 0L) {
										lower = s.createTime;
									} else {
										if (substate.get(lower).createTime + substate.get(lower).sheduleTime > s.createTime + s.sheduleTime) {
											lower = s.createTime;
										}
									}
								}
								nextSubState = lower;
								long delay = substate.get(lower).createTime + substate.get(lower).sheduleTime - System.currentTimeMillis();
								if (Log.loggingDebug)
									Log.debug("ABILITY: ACTIVATING  !pulse delay=" + delay);
								if (delay < 0)
									delay = 0;
								if (Log.loggingDebug)
									Log.debug("ABILITY: ACTIVATING  !pulse delay=" + delay);
								schedule(delay);
								if (Log.loggingDebug)
									Log.debug("ABILITY: ACTIVATING  !pulse end " + substate);
								return true;
							}
							if (Log.loggingDebug)
								Log.debug("ABILITY: ACTIVATING  !pulse END " + substate);

						} else if (substate.size() == 0) {
							Log.debug("ABILITY: ACTIVATING else");
							ability.pulseCaster(this);
							//ability.getTargets(this);
							if (maxChank == 0) {
								BasicWorldNode casterWNode = WorldManagerClient.getWorldNode(getSourceOid());
								Point casterLoc = casterWNode.getLoc();
								Point targetLoc = casterLoc;
								if (claimID > 0) {
									targetLoc = VoxelClient.getBuildingPosition(claimID, claimObjID);
								} else {
									BasicWorldNode targetWNode = WorldManagerClient.getWorldNode(getTargetOid());
									targetLoc = targetWNode.getLoc();
								}
								float distance = Point.distanceTo(casterLoc, targetLoc);
								SubAbilityState sasc = new SubAbilityState();
								sasc.pulse = false;
								sasc.numPulse = nextPulse;
								sasc.createTime = System.currentTimeMillis();
								sasc.sheduleTime = (long) (distance / ability.getSpeed() * 1000);
								sasc.lastChank = 0;

								substate.put(sasc.createTime, sasc);

							} else if (maxChank > 0) {
								SubAbilityState sasc = new SubAbilityState();
								sasc.pulse = false;
								sasc.numPulse = nextPulse;
								sasc.createTime = System.currentTimeMillis();
								sasc.sheduleTime = (long) (ability.areaOfEffectRadius / maxChank / ability.getSpeed() * 1000) / 2;
								sasc.lastChank = 0;
								substate.put(sasc.createTime, sasc);

							}
							if (substate.size() > 0) {
								long lower = 0L;
								for (SubAbilityState s : substate.values()) {
									if (lower == 0L) {
										lower = s.createTime;
									} else {
										if (substate.get(lower).createTime + substate.get(lower).sheduleTime > s.createTime + s.sheduleTime) {
											lower = s.createTime;
										}
									}
								}
								nextSubState = lower;
								long delay = substate.get(lower).createTime + substate.get(lower).sheduleTime - System.currentTimeMillis();

								schedule(delay);
								if (Log.loggingDebug)
									Log.debug("ABILITY: ACTIVATING  else end " + substate);
								return true;
							}
							//ability.completeActivation(this);
						}

					}
					if (Log.loggingDebug)
						Log.debug("ABILITY: ACTIVATING end " + substate);
					break;
				case CHANNELLING:
					if (Log.loggingDebug)
						Log.debug("ABILITY: CHANNELLING " + ability + "  on source: " + source + "| target:" + target + " ");
					result = ability.checkAbility(source, target, state, this, false);
					if (result != AbilityResult.SUCCESS) {
						AgisAbility.interruptAbility(this, result);
						return false;
					}
					if (!ability.pulseChecking(this)) {
						state = ActivationState.FAILED;
						if (Log.loggingDebug)
							Log.debug("ABILITY: CHANNELLING " + ability + "  on source: " + source + "| target:" + target + " Checking Failed");
						break;
					}

					if (ability.aoeType == AoeType.LOCATION_RADIUS) {
						ability.pulseCaster(this);
						ArrayList<CoordinatedEffectState> cess = new ArrayList<>();
						for(CoordinatedEffect effect : ability.getPowerUpDefinition(getPowerUptime()).getCoordEffects(ActivationState.ABILITY_PULSE)) {
							effect.putArgument("abilityID", ability.getID());
							effect.putArgument("claimID", claimID);
							effect.putArgument("claimObjID", claimObjID);
							effect.putArgument("castingMod", (int)Math.round( castingMod * 1000));
							CoordinatedEffectState ces = effect.invoke(getSourceOid(), getTargetOid(), location, this, getDestLocationForCoord());
							cess.add(ces);
							getCoordinatedEffectStates().add(ces);
						}
						AgisAbilityPulsState aaps = new AgisAbilityPulsState(this, nextPulse, cess);
						BasicWorldNode casterNode = WorldManagerClient.getWorldNode(source.getOwnerOid());
						if(casterNode!=null) {
							aaps.casterLoc = casterNode.getLoc();
							aaps.casterQuaternion = casterNode.getOrientation();
							aaps.casterInstance = casterNode.getInstanceOid();
						}

						long delay = 0L;
						if (ability.getSpeed() > 0)
							delay = (long) (ability.areaOfEffectRadius / maxChank / ability.getSpeed() * 1000) / 2;
						ScheduledFuture<?> schedule = Engine.getExecutor().schedule(aaps, delay, TimeUnit.MILLISECONDS);
					} else {
						ability.pulseChannelling(this);
					}
					for (CoordinatedEffect effect : ability.getPowerUpDefinition(getPowerUptime()).getCoordEffects(ActivationState.CHANNELLING)) {
						Log.debug("AgisAbility.State.run run CoordinatedEffect=" + effect.getEffectName());
						effect.putArgument("abilityID", ability.getID());
						effect.putArgument("castingMod", Math.round( (int)castingMod * 1000));
						if (ability.aoeType == AoeType.LOCATION_RADIUS) {
							effect.putArgument("locX", location.getX());
							effect.putArgument("locY", location.getY());
							effect.putArgument("locZ", location.getZ());
							effect.putArgument("locRadius", ability.areaOfEffectRadius);
							effect.putArgument("delay", ability.getActivationDelay());
						}
						getCoordinatedEffectStates().add(effect.invoke(getSourceOid(), getTargetOid(), location, this, getDestLocationForCoord()));
					}
					nextPulse++;
					if (nextPulse < ability.getChannelPulses()) {

						schedule(ability.getChannelPulseTime());

						if (Log.loggingDebug)
							Log.debug("ABILITY: CHANNELLING " + ability + "  on source: " + source + "| target:" + target + " return");
						return true;
					}
					ability.completeChannelling(this);
					if (Log.loggingDebug)
						Log.debug("ABILITY: CHANNELLING " + ability + "  on source: " + source + "| target:" + target + " return");
					break;
				case ACTIVATED:
					if (Log.loggingDebug)
						Log.debug("ABILITY: ACTIVATED " + ability + "  on source: " + source + "| target:" + target + " nextPulse=" + nextPulse);
					if (ability.getChannelled()) {
						if (Log.loggingDebug)
							Log.debug("ABILITY: ACTIVATED Channelled break");
						break;
					}

					if (ability.aoeType == AoeType.LOCATION_RADIUS) {
						ability.pulseCaster(this);
						ArrayList<CoordinatedEffectState> cess = new ArrayList<>();
						for(CoordinatedEffect effect : ability.getPowerUpDefinition(getPowerUptime()).getCoordEffects(ActivationState.ABILITY_PULSE)) {
							effect.putArgument("abilityID", ability.getID());
							effect.putArgument("claimID", claimID);
							effect.putArgument("claimObjID", claimObjID);
							effect.putArgument("castingMod", (int)Math.round( castingMod * 1000));
							CoordinatedEffectState ces = effect.invoke(getSourceOid(), getTargetOid(), location, this, getDestLocationForCoord());
							cess.add(ces);
							getCoordinatedEffectStates().add(ces);
						}
						AgisAbilityPulsState aaps = new AgisAbilityPulsState(this, nextPulse, cess);
						BasicWorldNode casterNode = WorldManagerClient.getWorldNode(source.getOwnerOid());
						if(casterNode!=null) {
							aaps.casterLoc = casterNode.getLoc();
							aaps.casterQuaternion = casterNode.getOrientation();
							aaps.casterInstance = casterNode.getInstanceOid();
						}

						long delay = 0L;
						if (ability.getSpeed() > 0)
							delay = (long) (ability.areaOfEffectRadius / maxChank / ability.getSpeed() * 1000) / 2;
						ScheduledFuture<?> schedule = Engine.getExecutor().schedule(aaps, delay, TimeUnit.MILLISECONDS);

						for (CoordinatedEffect effect : ability.getPowerUpDefinition(getPowerUptime()).getCoordEffects(ActivationState.CHANNELLING)) {
							Log.debug("AgisAbility.State.run run CoordinatedEffect=" + effect.getEffectName());
							effect.putArgument("abilityID", ability.getID());
							effect.putArgument("locX", location.getX());
							effect.putArgument("locY", location.getY());
							effect.putArgument("locZ", location.getZ());
							effect.putArgument("locRadius", ability.areaOfEffectRadius);
							effect.putArgument("delay", ability.getActivationDelay());
							effect.putArgument("castingMod", (int)Math.round( castingMod * 1000));
							getCoordinatedEffectStates().add(effect.invoke(getSourceOid(), getTargetOid(), location, this, getDestLocationForCoord()));
						}

						nextPulse++;
						if (ability.getChannelPulses() == -1 || nextPulse < ability.getChannelPulses()) {
							schedule(ability.getChannelPulseTime());
							if (Log.loggingDebug)
								Log.debug("ABILITY: ACTIVATED " + ability + "  on source: " + source + "| target:" + target + " return");
							return true;
						}
						break;
					} else {
						result = ability.checkAbility(source, target, state, this, false);
						if (result != AbilityResult.SUCCESS) {
							AgisAbility.interruptAbility(this, result);
							if (Log.loggingDebug)
								Log.debug("ABILITY: ACTIVATED " + ability + "  on source: " + source + "| target:" + target + "  Not success return");
							return false;
						}
						ability.pulseActivated(this);
						nextPulse++;
						schedule(ability.getActivePulseTime());
					}
					if (Log.loggingDebug)
						Log.debug("ABILITY: ACTIVATED " + ability + "  on source: " + source + "| target:" + target + " return End");
					return true;
				case CANCELLED:
					if (Log.loggingDebug)
						Log.debug("ABILITY: CANCELLED " + ability + "  on source: " + source + "| target:" + target + " break");

					break;
				case COMPLETED:
					if (maxChank == 0 && nextChank == 0) {
						ability.pulseTarget(this);
						nextChank++;
						break;
					}
					if (Log.loggingDebug)
						Log.debug("ABILITY: COMPLETED " + ability + "  on source: " + source + "| target:" + target + " break");
					break;
				case FAILED:
					if (Log.loggingDebug)
						Log.debug("ABILITY: FAILED " + ability + "  on source: " + source + "| target:" + target + " break");
					return false;

				case INTERRUPTED:
					if (Log.loggingDebug)
						Log.debug("ABILITY: INTERRUPTED " + ability + " source:" + source + "| target:" + target + " break");
					//if(Log.loggingDebug)
					//	Log.dumpStack("ABILITY: INTERRUPTED "+ability+" source:"+source+"| target:"+target+" break");
					break;
				default:
					break;
			}

			if (Log.loggingDebug) Log.debug("AgisAbilityState " + ability + " " + state + " before change");
			state = nextState();
			if (Log.loggingDebug) Log.debug("AgisAbilityState change state " + ability + " next " + state);
			nextPulse = 0;

			switch (state) {
				case ACTIVATING:
					if (Log.loggingDebug)
						Log.debug("ABILITY: ACTIVATING " + ability + " getActivePulseTime:" + ability.getActivePulseTime() + " on source: " + source + "| target:" + target);

					ability.beginActivation(this);
					Long activationTime = ability.getActivationTime();
					if (activationTime > 0) {
						String anim = ability.getCastingAnim();
						if (!anim.equals("")) {
							EnginePlugin.setObjectPropertyNoResponse(getSource().getOwnerOid(), WorldManagerClient.NAMESPACE, "casting", anim);
						}
						String affinity = ability.getCastingAffinity();
						if (!affinity.equals(""))
							EnginePlugin.setObjectPropertyNoResponse(getSource().getOwnerOid(), WorldManagerClient.NAMESPACE, "castingAffinity", affinity);
						EnginePlugin.setObjectPropertyNoResponse(getSource().getOwnerOid(), WorldManagerClient.NAMESPACE, "castingParam", 1);

					}

					// Send casting started event
					if (ability.getChannelled()) {
						long time = ability.getChannelPulses() * ability.getChannelPulseTime() + ability.getActivationDelay();
						EventMessageHelper.SendCombatEvent(source.getOwnerOid(), target.getOwnerOid(), EventMessageHelper.COMBAT_CASTING_STARTED, ability.id, -1, Math.round((Math.round(ability.getActivationTime() * castingMod) + time)), -1);

					} else {
						EventMessageHelper.SendCombatEvent(source.getOwnerOid(), target.getOwnerOid(), EventMessageHelper.COMBAT_CASTING_STARTED, ability.id, -1, Math.round(Math.round(ability.getActivationTime() * castingMod)), -1);
					}
					//ExtendedCombatMessages.sendCastingStartedMessage(getSource(), ability.getActivationTime());
					if (Log.loggingDebug)
						Log.debug("AUTO: setting duration :" + ability.getActivationTime() * castingMod);
					setDuration(Math.round(ability.getActivationTime() * castingMod));
					if (Log.loggingDebug)
						Log.debug("AUTO: scheduling duration");
					schedule(Math.round(ability.getActivationTime() * castingMod));
					if (Log.loggingDebug)
						Log.debug("AUTO: duration scheduled " + this);
					break;
				case CHANNELLING:
					if (Log.loggingDebug)
						Log.debug("ABILITY: CHANNELLING " + ability + " getActivePulseTime:" + ability.getActivePulseTime() + " on source: " + source + "| target:" + target);
					ability.beginChannelling(this);
					setDuration(ability.getChannelPulses() * ability.getChannelPulseTime());
					schedule(ability.getChannelPulseTime());
					break;
				case ACTIVATED:
					if (Log.loggingDebug)
						Log.debug("ABILITY: ACTIVATED " + ability + " getActivePulseTime:" + ability.getActivePulseTime() + " getActivationDelay=" + ability.getActivationDelay() + " on source: " + source + "| target:" + target);
					ability.beginActivated(this);
					if (Log.loggingDebug)
						Log.debug("ABILITY: ACTIVATED " + ability + " getActivePulseTime:" + ability.getActivePulseTime() + " getActivationDelay=" + ability.getActivationDelay() + " on source: " + source + "|| target:" + target);
					if (ability.getMakeBusy()) {
						source.setCurrentAction(null);
					}
					if (Log.loggingDebug)
						Log.debug("ABILITY: ACTIVATED " + ability + " getActivePulseTime:" + ability.getActivePulseTime() + " getActivationDelay=" + ability.getActivationDelay() + " on source: " + source + "||| target:" + target);
					//source.addActiveAbility(this);
					if (Log.loggingDebug)
						Log.debug("ABILITY: ACTIVATED " + ability + " getActivePulseTime:" + ability.getActivePulseTime() + " getActivationDelay=" + ability.getActivationDelay() + " on source: " + source + "|||| target:" + target);
					if (ability.aoeType == AoeType.LOCATION_RADIUS) {
						schedule(ability.getActivationDelay());
					} else {
						schedule(ability.getActivePulseTime());
					}
					if (Log.loggingDebug)
						Log.debug("ABILITY: ACTIVATED " + ability + " getActivePulseTime:" + ability.getActivePulseTime() + " getActivationDelay=" + ability.getActivationDelay() + " on source: " + source + "||||| target:" + target);

					break;
				case COMPLETED:
					if (Log.loggingDebug)
						Log.debug("ABILITY: COMPLETED " + ability + " getCurrentAction:" + source.getCurrentAction() + " on source: " + source + "| target:" + target);

					if (ability.isToggle()) {
						source.getLock().lock();
						try {
							if (source.currentRunAbilities().get(ability.getID()) == this) {
								source.currentRunAbilities().remove(ability.getID());
							}
						} catch (Exception e) {
							// TODO Auto-generated catch block
							Log.exception(e);
							e.printStackTrace();
						} finally {
							source.getLock().unlock();
						}
						AgisAbility.sendToggleInfo(this);
					} else if (source.getCurrentAction() == this) {
					/*if (maxChank == 0 && nextChank == 0) {
						if (Log.loggingDebug)
							Log.debug("ABILITY: COMPLETED " + ability + " getCurrentAction:" + source.getCurrentAction() + " on source: " + source + "| target:" + target + " maxChank=0 skip case COMPLATED");
						break;
					}*/
						source.setCurrentAction(null);

					}
					if (!ability.isToggle()) {
						ArrayList<AbilityComboData> combolist = ability.getCombos();
						AbilityComboData acd = (combolist != null && combolist.size() > 0) ? combolist.get(0) : null;
						if (acd != null) {
							if (Log.loggingDebug)
								Log.debug("Combo acd " + ability.getID() + " -> " + acd.getAbilityId());
							AgisAbility aa = Agis.AbilityManager.get(acd.getAbilityId());

							AbilityResult subResult = aa.checkAbility(source, target, ActivationState.INIT, this, !acd.getCheckCooldown());
							if (subResult == AbilityResult.SUCCESS) {
								source.addComboTime(acd.getAbilityId(), acd.getTime(), ability.getID());
								ExtendedCombatMessages.sendCombo(source.getOwnerOid(), ability.getID(), acd.getAbilityId(), acd.getTime(), false, acd.getShowInCenterUi(), acd.getReplaceInSlot());
							} else {
								if (Log.loggingDebug) Log.debug("Combo acd check result " + subResult);
							}
						} else {
							if (Log.loggingDebug) Log.debug("Combo no acd");
						}
					}
					source.removeRunAbilities(this);
					//source.removeActiveAbility(this);
					//Log.error("ABILITY: COMPLETED "+ability+" set cancel cast bar");
					EnginePlugin.setObjectPropertyNoResponse(getSource().getOwnerOid(), WorldManagerClient.NAMESPACE, "casting", "");
					EnginePlugin.setObjectPropertyNoResponse(getSource().getOwnerOid(), WorldManagerClient.NAMESPACE, "castingAffinity", "");
					EnginePlugin.setObjectPropertyNoResponse(getSource().getOwnerOid(), WorldManagerClient.NAMESPACE, "castingParam", 0);
					break;

				case FAILED:
					if (ability.isToggle()) {
						source.getLock().lock();
						try {
							if (source.currentRunAbilities().get(ability.getID()) == this)
								source.currentRunAbilities().remove(ability.getID());
						} catch (Exception e) {
							Log.exception(e);
							e.printStackTrace();
						} finally {
							source.getLock().unlock();
						}
						AgisAbility.sendToggleInfo(this);
					} else if (source.getCurrentAction() == this) {
						source.setCurrentAction(null);
					}
					if (Log.loggingDebug)
						Log.debug("ABILITY: FAILED " + ability + " set CurrentAction null on source: " + source + "| target:" + target);

					//source.removeActiveAbility(this);

					//	if(Log.loggingDebug)	Log.error("ABILITY: FAILED "+ability+" set cancel cast bar");
					EnginePlugin.setObjectPropertyNoResponse(getSource().getOwnerOid(), WorldManagerClient.NAMESPACE, "casting", "");
					EnginePlugin.setObjectPropertyNoResponse(getSource().getOwnerOid(), WorldManagerClient.NAMESPACE, "castingAffinity", "");
					EnginePlugin.setObjectPropertyNoResponse(getSource().getOwnerOid(), WorldManagerClient.NAMESPACE, "castingParam", -1);
					EventMessageHelper.SendCombatEvent(source.getOwnerOid(), target.getOwnerOid(), EventMessageHelper.COMBAT_CASTING_CANCELLED, ability.id, -1, 0, -1);
					for (CoordinatedEffectState ceState : getCoordinatedEffectStates()) {
						if (ceState != null)
							ceState.invokeCancel();
					}
					getCoordinatedEffectStates().clear();
					source.removeRunAbilities(this);
					break;
				default:
					Log.error("AgisAbility.State.run: new state invalid=" + state);
					break;
			}
			Log.debug("AgisAbility.State.run: Search for CoordEffect for stage=" + state + " and Ability Id=" + ability.getID());
			if (nextChank == 0)
				for (CoordinatedEffect effect : ability.getPowerUpDefinition(getPowerUptime()).getCoordEffects(state)) {
					if (Log.loggingDebug)
						Log.debug("AgisAbility.State.run run CoordinatedEffect=" + effect.getEffectName());
					effect.putArgument("abilityID", ability.getID());
					effect.putArgument("castingMod", (int)Math.round( castingMod * 1000));
					if (state == ActivationState.ACTIVATING && ability.getActivationTime() > 0) {
						double mod = 1f;
						if (CombatPlugin.ABILITY_CAST_TIME_MOD_STAT != null) {
							double statValue = getSource().statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_CAST_TIME_MOD_STAT);
							if (Log.loggingDebug)
								Log.debug("AgisAbility: CastTimeMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_CAST_TIME_MOD_STAT);
							double calculated = 0;
							if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_CAST_TIME_MOD_STAT)) {
								StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_CAST_TIME_MOD_STAT);
								int pointsCalculated = 0;
								for (int i = 1; i <= def.getPoints().size(); i++) {
									if (Log.loggingDebug)
										Log.debug("AgisAbility: CastTimeMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
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
							/*if (pointsCalculated < statValue) {
    							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
    						}*/
							} else {
								calculated = statValue;
							}
							if (Log.loggingDebug)
								Log.debug("AgisAbility: CastTimeMod calculated: " + calculated);
							mod = Math.round(calculated / 100f);
							if (Log.loggingDebug)
								Log.debug("AgisAbility: CastTimeMod calculated=" + calculated + " mod=" + mod);

						}
						effect.putArgument("length", (float) (ability.getActivationTime() * mod / 1000f));
					}
					if (state == ActivationState.ACTIVATED && ability.getChannelled()) {
						long time = ability.getChannelPulses() * ability.getChannelPulseTime();
						effect.putArgument("length", (float) (time / 1000f));
					}
					if (ability.aoeType == AoeType.LOCATION_RADIUS) {
						effect.putArgument("locX", location.getX());
						effect.putArgument("locY", location.getY());
						effect.putArgument("locZ", location.getZ());
						effect.putArgument("locRadius", ability.areaOfEffectRadius);
						effect.putArgument("delay", ability.getActivationDelay());
					}
					Log.debug("AgisAbility.State.run: Run CoordEffect for stage=" + state + " and Ability Id=" + ability.getID());
					if (state != ActivationState.CHANNELLING) {
						effect.putArgument("claimID", claimID);
						effect.putArgument("claimObjID", claimObjID);
						getCoordinatedEffectStates().add(effect.invoke(getSourceOid(), getTargetOid(), location, this, getDestLocationForCoord()));
					} else
						Log.debug("AgisAbility.State.run: Run CoordEffect state is CHANNELLING not send");
				}


			//Log.debug("AUTO: sending ability progress message");
			//Engine.getAgent().sendBroadcast(new CombatClient.AbilityProgressMessage(this));
			Log.debug("AgisAbility.updateState: finished ability state update stage=" + state + " and Ability Id=" + ability.getID());
			return true;
		} catch (ConcurrentModificationException e) {
			Log.error("AgisAbility.updateState ConcurrentModificationException " + e.getMessage() + " " + e.getLocalizedMessage());
		} catch (AORuntimeException e) {
			Log.error("AgisAbility.updateState AORuntimeException " + e.getMessage() + " " + e.getLocalizedMessage());
		} catch (Exception e) {
			Log.exception("AgisAbility.updateState Exception " + e.getMessage() + " " + e.getLocalizedMessage() + " " + e, e);
		} finally {
			//Log.debug("AUTO: unlocking ability state update");
			//     ObjectLockManager.unlockAll(requiredLocks);
			//Log.debug("AUTO: returning from ability state update");
			if(nextWakeupTime < System.currentTimeMillis())
				source.removeRunAbilities(this);
		}
		Log.debug("AgisAbility.State.run: END stage=" + state + " and Ability Id=" + ability.getID());
		return true;
	}

    protected void schedule(long delay) {
    	if(Log.loggingDebug)    Log.debug("AgisAbilityState.schedule setup delay:"+delay);
        setTimeRemaining(delay);
        ScheduledFuture<?> schedule = Engine.getExecutor().schedule(this, delay, TimeUnit.MILLISECONDS);
        
    	if(Log.loggingDebug)    Log.debug("AgisAbilityState.schedule isCancelled:"+(schedule==null?" is null":schedule.isCancelled())+" isDone:"+(schedule==null?" is null":schedule.isDone()));
    }
	protected void scheduleDelay(long delay) {
		if(Log.loggingDebug)    Log.debug("AgisAbilityState.schedule setup delay:"+delay);
		setTimeRemaining(delay);
		ScheduledFuture<?> schedule = Engine.getExecutor().schedule(this, delay, TimeUnit.MILLISECONDS);

		if(Log.loggingDebug)    Log.debug("AgisAbilityState.schedule isCancelled:"+(schedule==null?" is null":schedule.isCancelled())+" isDone:"+(schedule==null?" is null":schedule.isDone()));
	}
    public AgisAbility getAbility() { return ability; }
    public void setAbility(AgisAbility ability) { this.ability = ability; }
    protected AgisAbility ability;

    protected List<CombatInfo> potentialTargets = null;
    public List<CombatInfo> getPotentialTargets() {
        return potentialTargets;
    }
    public CombatInfo getSource() { return source; }
    public OID getSourceOid() { return (source == null ? null : source.getOwnerOid()); }
    public void setSource(CombatInfo source) { this.source = source; }
    protected CombatInfo source;

    public CombatInfo getTarget() { return target; }
    public OID getTargetOid() { return (target == null ? null : target.getOwnerOid()); }
    public void setTarget(CombatInfo target) { this.target = target; }
    protected CombatInfo target;

    public AgisItem getItem() { return item; }
    public void setItem(AgisItem item) { this.item = item; }
    protected AgisItem item;
	
    public int getClaimID() { return claimID; }
	public void setClaimID(int claimID) { this.claimID = claimID; }
	private int claimID = -1;

	public int getClaimObjID() { return claimObjID; }
	public void setClaimObjID(int claimObjID) { this.claimObjID = claimObjID; }
	private int claimObjID = -1;
	
    public long getNextWakeupTime() { return nextWakeupTime; }
    public long getTimeRemaining() { return nextWakeupTime - System.currentTimeMillis(); }
    public void setTimeRemaining(long time) { nextWakeupTime = System.currentTimeMillis() + time; }
    protected long nextWakeupTime;

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    protected long duration;

    public ActivationState getState() { return state; }
    public void setState(ActivationState state) { this.state = state; }
    protected ActivationState state = ActivationState.INIT;

    public int getNextPulse() { return nextPulse; }
    public void setNextPulse(int num) { nextPulse = num; }
    protected int nextPulse = 0;

    public int getNextChank() { return nextChank; }
    public void setNextChank(int num) { nextChank = num; }
    protected int nextChank = 0;
    
    public int MaxChank() { return maxChank; }
    protected int maxChank = 0;

    public int countapplyed=0;
    public boolean usedAbility = false;
    public Point getLocation() { return location; }
    public void setLocation(Point loc) { location = loc; }
	protected Point location = null;

	public Point getDestLocationForCoord() {
		if (destLocation == null) {
			if (Log.loggingDebug) Log.debug("AgisAbilitystate.getDestLocationForCoord destLocation is null");
			BasicWorldNode casterNode = WorldManagerClient.getWorldNode(source.getOwnerOid());
			Quaternion casterQuaternion = casterNode.getOrientation();
			if (Log.loggingDebug)
				Log.debug("AgisAbilitystate.getDestLocationForCoord casterQuaternion=" + casterQuaternion + " Y=" + casterQuaternion.getYAxis() + " X=" + casterQuaternion.getXAxis() + " Z=" + casterQuaternion.getZAxis());
			AOVector Loc2 = new AOVector(casterNode.getLoc());
			if (ability.getLineOfSight()) {
				AOVector Loc1 = new AOVector(casterNode.getLoc());
				Loc1.add(casterQuaternion.getZAxis().multiply(((ability.targetType.equals(TargetType.AREA) && ability.aoeType.equals(AoeType.PLAYER_RADIUS)) ? ability.getAreaOfEffectRadius() : ability.maxRange)));
				Point visibilityResponse = WorldManagerClient.CheckTargetVisibilityReturnHitPoint(casterNode.getInstanceOid(), casterNode.getLoc(), Loc1.toPoint());
				if (Log.loggingDebug)
					Log.debug("AgisAbilitystate.getDestLocationForCoord LoS visibilityResponse=" + visibilityResponse);
				if (visibilityResponse.equals(new Point())) {
					if (Log.loggingDebug)
						Log.debug("AgisAbilitystate.getDestLocationForCoord LoS not hit range =" + ((ability.targetType.equals(TargetType.AREA) && ability.aoeType.equals(AoeType.PLAYER_RADIUS)) ? ability.getAreaOfEffectRadius() : ability.maxRange));
					Loc2.add(casterQuaternion.getZAxis().multiply(((ability.targetType.equals(TargetType.AREA) && ability.aoeType.equals(AoeType.PLAYER_RADIUS)) ? ability.getAreaOfEffectRadius() : ability.maxRange)));
				} else {
					Float dist = Point.distanceTo(casterNode.getLoc(), visibilityResponse);
					if (Log.loggingDebug)
						Log.debug("AgisAbilitystate.getDestLocationForCoord LoS hit range dist =" + dist);
					Loc2.add(casterQuaternion.getZAxis().multiply(dist));
				}
			} else {
				if (Log.loggingDebug)
					Log.debug("AgisAbilitystate.getDestLocationForCoord not check LoS range =" + ((ability.targetType.equals(TargetType.AREA) && ability.aoeType.equals(AoeType.PLAYER_RADIUS)) ? ability.getAreaOfEffectRadius() : ability.maxRange));
				Loc2.add(casterQuaternion.getZAxis().multiply(((ability.targetType.equals(TargetType.AREA) && ability.aoeType.equals(AoeType.PLAYER_RADIUS)) ? ability.getAreaOfEffectRadius() : ability.maxRange)));
			}
			if (Log.loggingDebug) Log.debug("AgisAbilitystate.getDestLocationForCoord point " + Loc2);

			Loc2.add(new AOVector(0f, 1f, 0f));
			if (Log.loggingDebug) Log.debug("AgisAbilitystate.getDestLocationForCoord return point " + Loc2);
			return Loc2.toPoint();
		}
		return destLocation;
	}
	public Point getDestLocation() { return destLocation; }
	public void setDestLocation(Point loc) { destLocation = loc; }
	protected Point destLocation = null;
	public HashMap<Long, SubAbilityState> SubState() { return substate; }
	protected HashMap<Long, SubAbilityState> substate = new HashMap<Long, SubAbilityState>();
	
	public long NextSubState() { return nextSubState; }
	  
	public ArrayList<AgisEffect.EffectState> effects = new ArrayList<AgisEffect.EffectState>(); 
	protected long nextSubState = 0L;
	transient Long startTime=0L;
	transient Long sub = null;

	public void setPowerUptime(Long time){
		powerUptime = time;
	}

	public Long getPowerUptime() {
		return powerUptime;
	}

	protected Long powerUptime = 0L;
	private static final Cache<String, AgisAbilityState> CURRENT_STATES = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(30)).removalListener(AgisAbilityState::onRemoval).build();

	private static void onRemoval(String key, AgisAbilityState state, RemovalCause cause) {
		state.removeSubscription();
	}

	static {
		try {
			BeanInfo info = Introspector.getBeanInfo(AgisAbilityState.class);
			PropertyDescriptor[] propertyDescriptors = info.getPropertyDescriptors();
			for (int i = 0; i < propertyDescriptors.length; ++i) {
				PropertyDescriptor pd = propertyDescriptors[i];
				if (pd.getName().equals("sub")) {
					pd.setValue("transient", Boolean.TRUE);
				}else if (pd.getName().equals("nextSubState")) {
					pd.setValue("transient", Boolean.TRUE);
				}else if (pd.getName().equals("coordinatedEffectStates")) {
					pd.setValue("transient", Boolean.TRUE);
				}
				Log.debug("BeanInfo name="+pd.getName());
			}
		} catch (Exception e) {
			Log.error("failed beans initalization");
		}
	}

	/**
	 *
	 * @return
	 */
	public ArrayList<CoordinatedEffectState> getCoordinatedEffectStates() {
		return coordinatedEffectStates;
	}

	/**
	 *
	 * @param coordinatedEffectStates
	 */
	public void setCoordinatedEffectStates(ArrayList<CoordinatedEffectState> coordinatedEffectStates) {
		this.coordinatedEffectStates = coordinatedEffectStates;
	}

	/**
	 *
	 */
	transient protected ArrayList<CoordinatedEffectState> coordinatedEffectStates = new ArrayList<>();

	/**
	 *
	 * @param state
	 */
	public void addEffectState(EffectState state) {
		effectStates.add(state);
	}

	/**
	 *
	 */
	protected ArrayList<EffectState> effectStates = new ArrayList<>();

	public void addAbilitiesState(AgisAbilityState state) {
		abilityStates.add(state);
	}
	protected ArrayList<AgisAbilityState> abilityStates = new ArrayList<>();

	public void setDelayed() {
		delayed = true;
	}
	protected boolean delayed=false;
}

class AgisAbilityPulsState implements Runnable {
	AgisAbilityState state;
	ArrayList<CoordinatedEffectState> cess = new ArrayList<>();
	public int lastChank = 0;
	public int numPulse = 0;
	public int numAttackedTargets =0;
	public HashMap<OID, TargetsInAreaEntity> targets = new HashMap<OID, TargetsInAreaEntity>();
	public Point casterLoc;
	public Quaternion casterQuaternion;

	public OID casterInstance;
	public ArrayList<CombatBuildingTarget> buildingTargets = new ArrayList<CombatBuildingTarget>();
	public AgisAbilityPulsState(AgisAbilityState state, int pulseId, ArrayList<CoordinatedEffectState> cess) {
		Log.debug("ABILITY: AgisAbilityPulsState  Setup");
		this.state = state;
		this.numPulse = pulseId;
		this.cess = cess;

	}

	@Override
	public void run() {
		long start = System.currentTimeMillis();
		Log.debug("ABILITY: AgisAbilityPulsState  run Start "+this);

		long st = System.currentTimeMillis();
		try {
			ArrayList<CombatInfo> targets = state.getAbility().getTargets(state, this);
			Log.debug("ABILITY: AgisAbilityPulsState  run targets="+targets.size()+" buildingTargets="+buildingTargets.size());
			numAttackedTargets += targets.size();
			if(state.getAbility().getAoeTargtetsCountType() > 0  && numAttackedTargets >= state.getAbility().getAoeTargtetsCount()){
				for (CoordinatedEffectState ces : cess) {
					ces.invokeCancel();
				}
			}
			long st1 = System.currentTimeMillis();
			// ability.completeActivationTargetConsume(this);
			state.getAbility().pulseConsumeTarget(state, targets);
			Log.debug("ABILITY: AgisAbilityPulsState  run consume");

			long st2 = System.currentTimeMillis();
			state.getAbility().pulseTarget(state, targets);


			Log.debug("ABILITY: AgisAbilityPulsState T1=" + (System.currentTimeMillis() - st) + "ms T2=" + (System.currentTimeMillis() - st1) + "ms T3=" + (System.currentTimeMillis() - st2) + "ms ");
			Log.debug("ABILITY: AgisAbilityPulsState  run state.MaxChank()="+state.MaxChank()+" lastChank="+lastChank);
			if (state.MaxChank() > 1 && lastChank < state.MaxChank() - 1) {
				lastChank++;
				long delay = start + (long) (state.getAbility().areaOfEffectRadius / state.MaxChank() / state.getAbility().getSpeed() * 1000) - System.currentTimeMillis();
				if (delay < 0)
					delay = 0;
				schedule(delay);
			}
		} catch (Exception e) {
			Log.exception("AgisAbilityPulsState",e);
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Log.debug("ABILITY: AgisAbilityPulsState  run END ");
	}

	protected void schedule(long delay) {
		if (Log.loggingDebug)
			Log.debug("AgisAbilityPulsState.schedule setup delay:" + delay);
		ScheduledFuture<?> schedule = Engine.getExecutor().schedule(this, delay, TimeUnit.MILLISECONDS);
	}

	public String toString() {
		return "[SubAbilityState: numPulse=" + numPulse + " lastChank=" + lastChank + " targets=" + targets.size() + "]";
	}
}

class SubAbilityState {
	public boolean pulse = false;
	public int lastChank = 0;
	public int numPulse = 0;
	// public long hash = 0L;
	public long createTime = 0L;
	public long sheduleTime = 0L;
	public HashMap<OID, TargetsInAreaEntity> targets = new HashMap<OID, TargetsInAreaEntity>();

	public String toString() {
		return "[SubAbilityState: pulse=" + pulse + " createTime=" + createTime + " sheduleTime=" + sheduleTime + " numPulse=" + numPulse + " lastChank=" + lastChank + " targets=" + targets.size() + "]";
	}
}



