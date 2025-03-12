package atavism.agis.effects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
public class StunEffect extends AgisEffect {
    public StunEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("StunEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("StunEffect: this effect is not for buildings");
			return;
		}
		effectSkillType = (int)params.get("skillType");
    	Log.debug("StunEffect.apply: effectSkillType="+effectSkillType);
        
        String abilityEvent = EventMessageHelper.COMBAT_DEBUFF_GAINED;

		CombatInfo target = state.getTarget();

		if (target != null) {
			if (target.dead()) {
				Log.debug("StunEffect: target is dead");
				state.wasApplied(false);
				return;
			}
		}
		CombatInfo source = state.getSource();
        
        //FIXME Stacking
     	// Stacking handling
    	int stackCase = stackCheck();
      	Log.debug("StunEffect.apply: stackCase="+stackCase);
        if (stackCase == 0) {
    		// Do not apply this effect
    		return;
    	} 
    	if(target==null) {
    		Log.error("StunEffect.apply: target is null "+state);
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
    	Log.debug("StunEffect.apply: target has this effect: " + hasThisEffect + "; from this caster: " + fromThisCaster + " with stackCase: " + stackCase);
    	
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
    	
        // int stunRes = -1;
    	double stunChance =1f;
		if(CombatPlugin.STUN_CHANCE_STAT!=null) {
			double statValue = source.statGetCurrentValueWithPrecision(CombatPlugin.STUN_CHANCE_STAT);
			if (Log.loggingDebug)
				Log.debug("StunEffect.apply: stunChance statValue: " + statValue + " of " + CombatPlugin.STUN_CHANCE_STAT);
			double calculated = 0;
			if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.STUN_CHANCE_STAT)) {
				StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.STUN_CHANCE_STAT);
				int pointsCalculated = 0;
				for (int i = 1; i <= def.getPoints().size(); i++) {
					if (Log.loggingDebug)
						Log.debug("StunEffect.apply: stunChance i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
					if (statValue <= def.getThresholds().get(i)) {
						Log.debug("StunEffect.apply: stunChance statValue < th");
						if (statValue - pointsCalculated < 0)
							break;
						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += statValue - pointsCalculated;
					} else {
						Log.debug("StunEffect.apply: stunChance statValue > th");
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
				Log.debug("StunEffect.apply: stunChance calculated: " + calculated);	
			stunChance = calculated;
			if (Log.loggingDebug)
				Log.debug("StunEffect.apply: stunChance calculated="+calculated+" mod="+stunChance);
		}
         
         
     	double stunRes =1f;
		if(CombatPlugin.STUN_RESISTANCE_STAT!=null) {
			double statValue = target.statGetCurrentValueWithPrecision(CombatPlugin.STUN_RESISTANCE_STAT);
			if (Log.loggingDebug)
				Log.debug("StunEffect.apply: stunRes statValue: " + statValue + " of " + CombatPlugin.STUN_RESISTANCE_STAT);
			double calculated = 0;
			if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.STUN_RESISTANCE_STAT)) {
				StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.STUN_RESISTANCE_STAT);
				int pointsCalculated = 0;
				for (int i = 1; i <= def.getPoints().size(); i++) {
					if (Log.loggingDebug)
						Log.debug("StunEffect.apply: stunRes i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
					if (statValue <= def.getThresholds().get(i)) {
						Log.debug("StunEffect.apply: stunRes statValue < th");
						if (statValue - pointsCalculated < 0)
							break;
						calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += statValue - pointsCalculated;
					} else {
						Log.debug("StunEffect.apply: stunRes statValue > th");
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
				Log.debug("StunEffect.apply: stunRes calculated: " + calculated);	
			stunRes = calculated;
			if (Log.loggingDebug)
				Log.debug("StunEffect.apply: stunRes calculated="+calculated+" mod="+stunRes);
		}

		if (Log.loggingDebug)
			Log.debug("StunEffect.apply: stunRes=" + stunRes + " stunChance=" + stunChance);

		if (CombatPlugin.STAT_RANDOM_HIT_STUN > 0) {
			Random rand = new Random();
			stunRes = stunRes + stunRes * CombatPlugin.STAT_RANDOM_HIT_STUN * rand.nextFloat();
			stunChance = stunChance + stunChance * CombatPlugin.STAT_RANDOM_HIT_STUN * rand.nextFloat();
		}

		if (Log.loggingDebug)
			Log.debug("StunEffect.apply: stunRes=" + stunRes + " stunChance=" + stunChance);

      /* if (CombatPlugin.STUN_RESISTANCE_STAT!=null)
        	stunRes = target.statGetCurrentValue(CombatPlugin.STUN_RESISTANCE_STAT);*/
        if (stunRes > stunChance) {
        	Log.debug("StunEffect.apply: apply target: "+target+" have stun resisteance");
            EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), EventMessageHelper.COMBAT_RESISTED, state.getAbilityID(), getID(), -1, -1);
            CoordinatedEffect cE = new CoordinatedEffect("Resisted");
            cE.sendSourceOid(true);
            cE.sendTargetOid(true);
            cE.invoke(target.getOwnerOid(), target.getOwnerOid());
            //AgisEffect.removeEffect(state);
    		state.wasApplied(false);
        }else {
			Log.debug("StunEffect.apply: set nomove and no turn");
			Set<EffectState> effects = state.getTarget().getCurrentEffects() != null?new HashSet<EffectState>(state.getTarget().getCurrentEffects()):new HashSet<EffectState>();
			Set<EffectState> ceffects = state.getSource().getCurrentEffects()!= null?new HashSet<EffectState>(state.getSource().getCurrentEffects()):new HashSet<EffectState>();
		 // Triggers Calculations
			for (EffectState ef : ceffects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					 se.Calculate(TriggerProfile.Type.STUN, tags, 0, source, target, ef);
				}
			}
			if (!state.getSourceOid().equals(state.getTargetOid())) {
				for (EffectState ef : effects) {
					if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
						TriggerEffect se = (TriggerEffect) ef.getEffect();
						se.Calculate(TriggerProfile.Type.STUN, tags, 0, source, target, ef);
					}
				}
			}
			// Ability Triggers Calculations
			if(state.getAbilityID() > 0) {
				AgisAbility aa = Agis.AbilityManager.get(state.getAbilityID());
				if(aa!=null) {
					long time = (long) params.get("powerUp");
					aa.getPowerUpDefinition(time).CalculateTriggers(TriggerProfile.Type.STUN, tags, 0, source, target, aa);
				}
			}
			List<Integer> listToRemove = new ArrayList<Integer>();
			for (EffectState ef : effects) {
				Log.debug("StunEffect.apply: Check is SleepEffect " + ef.getEffectName());
				if (ef.getEffect().getClass().equals(SleepEffect.class)) {
					Log.debug("StunEffect.apply: add to remove SleepEffect " + ef.getEffectName());
					if (!listToRemove.contains(ef.getEffect().getID()))
						listToRemove.add(ef.getEffect().getID());
				}
			}
			Log.debug("StunEffect.apply Removing Sleep Effects");
			for (Integer id : listToRemove) {
				AgisEffect.removeEffectByID(target, id);
			}
            Log.debug("StunEffect.apply Removed Sleep Effects");
            
        	EnginePlugin.setObjectPropertyNoResponse(target.getOwnerOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, true);
        	EnginePlugin.setObjectPropertyNoResponse(target.getOwnerOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, true);
        	Log.debug("StunEffect.apply: set incapacitated ");
            CombatClient.setCombatInfoState(target.getOid(), CombatInfo.COMBAT_STATE_INCAPACITATED);
            CombatClient.sendCombatEvent(state.getSourceOid(),false, TriggerProfile.Type.STUN);
   			CombatClient.sendCombatEvent(state.getTargetOid(), true, TriggerProfile.Type.STUN);
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
    	remove(state, target);
    }

    // remove the effect from the object
	public void remove(EffectState state, CombatInfo target) {
		Log.debug("StunEffect.remove: " + state.getEffectName());
		// CombatInfo target = state.getTarget();
		boolean anotherStunExists = false;
		boolean sleepExists = false;
		if (getTargetEffectsOfMatchingType(target).size() > 1) {
			anotherStunExists = true;
			Log.debug("StunEffect.remove: found another stun effect so will not remove movement locks");
		}

		Log.debug("StunEffect.remove: check sleep exist");
		for (EffectState ef : target.getCurrentEffects()) {
			if (ef.getEffect().getClass().equals(SleepEffect.class))
				sleepExists = true;
		}
		Log.debug("StunEffect.remove: check sleep exist " + sleepExists+" anotherStunExists="+anotherStunExists);

		if (!sleepExists) {
			if (!anotherStunExists) {
			   	Log.debug("StunEffect.remove: set nomove and no turn to false");
			    EnginePlugin.setObjectPropertyNoResponse(target.getOwnerOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, false);
				EnginePlugin.setObjectPropertyNoResponse(target.getOwnerOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
				Log.debug("StunEffect.remove: set incapacitated to false");
		        CombatClient.clearCombatInfoState(target.getOid(), CombatInfo.COMBAT_STATE_INCAPACITATED);
			}
		}
		EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), EventMessageHelper.COMBAT_DEBUFF_LOST, state.getAbilityID(), getID(), -1, -1);

		super.remove(state);
		Log.debug("StunEffect.remove End");
			}
    
    public void unload(EffectState state, CombatInfo target) {
       	Log.debug("StunEffect.unload: removing Effect");
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