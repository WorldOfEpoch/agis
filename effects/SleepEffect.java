package atavism.agis.effects;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import atavism.agis.core.*;
import atavism.agis.objects.CombatInfo;
import atavism.agis.objects.CoordinatedEffect;
import atavism.agis.objects.StatThreshold;
import atavism.agis.objects.TriggerProfile;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.util.EventMessageHelper;
import atavism.server.engine.*;
import atavism.server.plugins.*;
import atavism.server.util.Log;

/**
 * Effect child class that stops the target from moving or performing actions for a period of time.
 * Has not been tested in a while.
 * @author Andrew Harrison
 *
 */
public class SleepEffect extends AgisEffect {
    public SleepEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("SleepEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("SleepEffect: this effect is not for buildings");
			return;
		}
		effectSkillType = (int)params.get("skillType");
        
        String abilityEvent = EventMessageHelper.COMBAT_DEBUFF_GAINED;
        
		CombatInfo target = state.getTarget();

		if (target != null) {
			if (target.dead()) {
				Log.debug("SleepEffect: target is dead");
				state.wasApplied(false);
				return;
			}
		}
		CombatInfo source = state.getSource();
       
        //FIXME Stacking
     	// Stacking handling
    	int stackCase = stackCheck();
    	if (stackCase == 0) {
    		// Do not apply this effect
    		return;
    	} 
    	if(target==null) {
    		Log.error("SleepEffect.apply: target is null "+state);
    		return;
    	}
    		
    	int stackLevel = 1;
    	boolean hasThisEffect = false;
    	boolean fromThisCaster = false;
    	// AgisEffect effect = state.getEffect();
    	EffectState similarEffect = null;
    	EffectState sameCasterEffect = null;
    	for (EffectState existingState : target.getCurrentEffects()) {
    		if (existingState.getEffect().getID() == getID() && !state.equals(existingState)) {
    			hasThisEffect = true;
    			similarEffect = existingState;
    		    if (source.getOwnerOid().equals(similarEffect.getStackCaster())) {
    		    	fromThisCaster = true;
    		    	sameCasterEffect = similarEffect;
    		    }
    		}
    	}
    	Log.debug("SleepEffect.apply: target has this effect: " + hasThisEffect + "; from this caster: " + fromThisCaster + " with stackCase: " + stackCase);
    	
    	if (stackCase == 1) {
    		// If the target already has the same effect type from the same caster, remove the old one
    		if (fromThisCaster)
    			AgisEffect.removeEffect(sameCasterEffect);
    	} else if (stackCase == 2) {
    		// If the target already has the same effect type from the same caster, remove the old one and increment the stack
    		if (fromThisCaster) {
    			stackLevel = sameCasterEffect.getCurrentStack();
    			AgisEffect.removeEffect(sameCasterEffect);
    			if (stackLevel < this.stackLimit)
        		    stackLevel++;
    		}
    	} else if (stackCase == 3) {
    		// If the target already has the same effect type, remove the old one
    		if (hasThisEffect)
    			AgisEffect.removeEffect(similarEffect);
    	} else if (stackCase == 4) {
    		// If the target already has the same effect type, remove the old one and increment the stack
    		if (hasThisEffect) {
    			stackLevel = similarEffect.getCurrentStack();
    			AgisEffect.removeEffect(similarEffect);
    			if (stackLevel < this.stackLimit)
        		    stackLevel++;
    		}
    	}
    	
        
    	double sleepChance =1f;
		if(CombatPlugin.SLEEP_CHANCE_STAT!=null) {
			double statValue = source.statGetCurrentValueWithPrecision(CombatPlugin.SLEEP_CHANCE_STAT);
			if (Log.loggingDebug)
				Log.debug("SleepEffect.apply: sleepChance statValue: " + statValue + " of " + CombatPlugin.SLEEP_CHANCE_STAT);
			double calculated = 0;
			if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.SLEEP_CHANCE_STAT)) {
				StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.SLEEP_CHANCE_STAT);
				double pointsCalculated = 0;
				for (int i = 1; i <= def.getPoints().size(); i++) {
					if (Log.loggingDebug)
						Log.debug("SleepEffect.apply: sleepChance i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
					if (statValue <= def.getThresholds().get(i)) {
						Log.debug("SleepEffect.apply: sleepChance statValue < th");
						if (statValue - pointsCalculated < 0)
							break;
						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += statValue - pointsCalculated;
					} else {
						Log.debug("SleepEffect.apply: sleepChance statValue > th");
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
				Log.debug("SleepEffect.apply: sleepChance calculated: " + calculated);	
			sleepChance = calculated;
			if (Log.loggingDebug)
				Log.debug("SleepEffect.apply: sleepChance calculated="+calculated+" mod="+sleepChance);
		}
         
         
     	double sleepRes =1f;
		if(CombatPlugin.SLEEP_RESISTANCE_STAT!=null) {
			double statValue = target.statGetCurrentValueWithPrecision(CombatPlugin.SLEEP_RESISTANCE_STAT);
			if (Log.loggingDebug)
				Log.debug("SleepEffect.apply: sleepRes statValue: " + statValue + " of " + CombatPlugin.SLEEP_RESISTANCE_STAT);
			double calculated = 0;
			if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.SLEEP_RESISTANCE_STAT)) {
				StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.SLEEP_RESISTANCE_STAT);double pointsCalculated = 0;
				for (int i = 1; i <= def.getPoints().size(); i++) {
					if (Log.loggingDebug)
						Log.debug("SleepEffect.apply: sleepRes i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
					if (statValue <= def.getThresholds().get(i)) {
						Log.debug("SleepEffect.apply: sleepRes statValue < th");
						if (statValue - pointsCalculated < 0)
							break;
						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += statValue - pointsCalculated;
					} else {
						Log.debug("SleepEffect.apply: sleepRes statValue > th");
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
				Log.debug("SleepEffect.apply: sleepRes calculated: " + calculated);	
			sleepRes = calculated;
			if (Log.loggingDebug)
				Log.debug("SleepEffect.apply: sleepRes calculated="+calculated+" mod="+sleepRes);
		}
		
		if (Log.loggingDebug)
			Log.debug("SleepEffect.apply: sleepRes=" + sleepRes + " sleepChance=" + sleepChance);

		if (CombatPlugin.STAT_RANDOM_HIT_SLEEP > 0) {
			Random rand = new Random();
			sleepRes = sleepRes + sleepRes * CombatPlugin.STAT_RANDOM_HIT_SLEEP * rand.nextFloat();
			sleepChance = sleepChance + sleepChance * CombatPlugin.STAT_RANDOM_HIT_SLEEP * rand.nextFloat();
		}

		if (Log.loggingDebug)
			Log.debug("SleepEffect.apply: sleepRes=" + sleepRes + " sleepChance=" + sleepChance);
    	
    	
    	
    	
        
      /*  int sleepRes = -1;
        if (CombatPlugin.SLEEP_RESISTANCE_STAT!=null)
        	sleepRes = 	target.statGetCurrentValue(CombatPlugin.SLEEP_RESISTANCE_STAT);*/
		if (sleepRes > sleepChance) {
			Log.debug("SleepEffect.apply: apply target: " + target + " have sleep resisteance");
            EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), EventMessageHelper.COMBAT_RESISTED, state.getAbilityID(), getID(), -1, -1);
            CoordinatedEffect cE = new CoordinatedEffect("Resisted");
            cE.sendSourceOid(true);
            cE.sendTargetOid(true);
            cE.invoke(target.getOwnerOid(), target.getOwnerOid());
            //AgisEffect.removeEffect(state);
    		state.wasApplied(false);
		} else {
			Log.debug("SleepEffect.apply: apply target: " + target + " no have sleep resisteance");
			Set<EffectState> effects = state.getTarget().getCurrentEffects() != null?new HashSet<EffectState>(state.getTarget().getCurrentEffects()):new HashSet<EffectState>();
			Set<EffectState> ceffects = state.getSource().getCurrentEffects()!= null?new HashSet<EffectState>(state.getSource().getCurrentEffects()):new HashSet<EffectState>();
		 	// Triggers Calculations
			for (EffectState ef : ceffects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					 se.Calculate(TriggerProfile.Type.SLEEP, tags, 0, source, target, ef);
				}
			}
			if (!state.getSourceOid().equals(state.getTargetOid())) {
				for (EffectState ef : effects) {
					if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
						TriggerEffect se = (TriggerEffect) ef.getEffect();
						se.Calculate(TriggerProfile.Type.SLEEP, tags, 0, source, target, ef);
					}
				}
			}
			// Ability Triggers Calculations
			if(state.getAbilityID() > 0) {
				AgisAbility aa = Agis.AbilityManager.get(state.getAbilityID());
				if(aa!=null) {
					long time = (long) params.get("powerUp");
					aa.getPowerUpDefinition(time).CalculateTriggers(TriggerProfile.Type.SLEEP, tags, 0, source, target,aa);
				}
			}
			EnginePlugin.setObjectPropertyNoResponse(target.getOwnerOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, true);
			EnginePlugin.setObjectPropertyNoResponse(target.getOwnerOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, true);
			CombatClient.setCombatInfoState(target.getOid(), CombatInfo.COMBAT_STATE_INCAPACITATED);
			CombatClient.sendCombatEvent(state.getSourceOid(), false, TriggerProfile.Type.SLEEP);
			CombatClient.sendCombatEvent(state.getTargetOid(), true, TriggerProfile.Type.SLEEP);
			EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), -1, -1);
	    	// Send interrupt to target
				if ((interruption_chance == 0 && interruption_chance_max > 0) || interruption_chance > 0 || forceInterruption) {
					CombatClient.interruptAbilityMessage interruptMsg = new CombatClient.interruptAbilityMessage(target.getOwnerOid());
					interruptMsg.setForce(forceInterruption);
					float chance = interruption_chance;
					if (interruption_chance < interruption_chance_max) {
						Random rand = new Random();
						float f = rand.nextFloat();
						chance = interruption_chance + (interruption_chance_max - interruption_chance) * f;
					}
					interruptMsg.setChance(chance);
					Engine.getAgent().sendBroadcast(interruptMsg);
				}

			// If there is a pulse coord effect, run it now
			if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
				CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
				cE.sendSourceOid(true);
				cE.sendTargetOid(true);
				state.getCoordinatedEffectStates().add(cE.invoke(target.getOwnerOid(), target.getOwnerOid()));
			}
		}
    	
        
    }
    public void remove(EffectState state) {
     	CombatInfo target = state.getTarget();
     	  remove( state, target, false); 
    }
    public void remove(EffectState state, boolean removeAll) {
     	CombatInfo target = state.getTarget();
     	  remove( state, target, removeAll); 
    }
    public void remove(EffectState state, CombatInfo target) {
    	remove( state, target, false); 
    }
    // remove the effect from the object
	public void remove(EffectState state, CombatInfo target, boolean removeAll) {
		Log.debug("SleepEffect.remove: " + state.getEffectName() + " " + target);
		// CombatInfo target = state.getTarget();
		boolean anotherSleepExists = false;
		boolean stunExists = false;

		if (getTargetEffectsOfMatchingType(target).size() > 1) {
			anotherSleepExists = true;
			Log.debug("SleepEffect.remove: found another sleep effect so will not remove movement locks");
		}
	
		if (removeAll) {
			for (int i = 0; i < getTargetEffectsOfMatchingType(target).size(); i++) {
				if (!getTargetEffectsOfMatchingType(target).get(i).equals(this)) {
					getTargetEffectsOfMatchingType(target).get(i).getEffect().remove(state);
				}
			}
			Log.debug("SleepEffect.remove: remove all");
		}

		Log.debug("SleepEffect.remove: check stun exist");
		for (EffectState ef : target.getCurrentEffects()) {
			if (ef.getEffect().getClass().equals(StunEffect.class))
				stunExists = true;
		}
		Log.debug("SleepEffect.remove: check stun exist " + stunExists +" anotherSleepExists="+anotherSleepExists);

		if (!stunExists) {
			if (!anotherSleepExists || removeAll) {
				Log.debug("SleepEffect.remove: Remove nomove and noturn");
				EnginePlugin.setObjectPropertyNoResponse(target.getOwnerOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, false);
				Log.debug("SleepEffect.remove: Remove nomove and noturn 1");
				EnginePlugin.setObjectPropertyNoResponse(target.getOwnerOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
				Log.debug("SleepEffect.remove: Remove nomove and noturn 2");
				CombatClient.clearCombatInfoState(target.getOid(), CombatInfo.COMBAT_STATE_INCAPACITATED);
				Log.debug("SleepEffect.remove: Remove nomove and noturn end");
			}
		}
		Log.debug("SleepEffect.remove: Send Combat Event");
		EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), EventMessageHelper.COMBAT_DEBUFF_LOST, state.getAbilityID(), getID(), -1, -1);

		super.remove(state);
		Log.debug("SleepEffect.remove End");
	}
    
    public void unload(EffectState state, CombatInfo target) {
       	Log.debug("SleepEffect.unload: removing Effect");
        	remove(state,target);
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    }
    
 // Effect Value that needs to be removed upon effect removal
    public void setEffectVal(int effect) {
    	effectVal = effect;
    }
    public int GetEffectVal() {
        return effectVal;
    }
    public int effectVal = 0;
    
    public void setEffectType(int type) {
    	effectType = type;
    }
    public int GetEffectType() {
        return effectType;
    }
    public int effectType = 0;
    
    public void setEffectSkillType(int type) {
    	effectSkillType = type;
    }
    public int GetEffectSkillType() {
        return effectSkillType;
    }
    public int effectSkillType = 0;
    
    public String getPulseCoordEffect() { return pulseCoordEffect; }
    public void setPulseCoordEffect(String coordEffect) { pulseCoordEffect = coordEffect; }
    protected String pulseCoordEffect;
    
    private static final long serialVersionUID = 1L;
}