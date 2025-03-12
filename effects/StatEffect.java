package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.core.*;
import atavism.server.util.*;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that alters the value of a stat on the target for a period of time.
 * Can be permanent if the effect is a passive one.
 * @author Andrew Harrison
 *
 */
public class StatEffect extends AgisEffect {
	public StatEffect() {
		
	}
    public StatEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }

    protected Map<String, Float> statMap = new HashMap<String, Float>();
    public void setStat(String stat, float adj) {
    	statMap.put(stat, adj);
    }
    public Float getStat(String stat) {
    	return statMap.get(stat);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Map<String, Serializable> params = state.getParams();
    	
    	boolean ignoreDead = false;
    	if (isPassive() && params.containsKey("ignoreDead"))
			ignoreDead = (boolean) params.get("ignoreDead");
    	
    	String abilityEvent = EventMessageHelper.COMBAT_BUFF_GAINED;
    	
    	CombatInfo caster = state.getSource();
		CombatInfo target = state.getTarget();

		if (target != null) {
			if (target.dead() && !ignoreDead) {
				Log.debug("StatEffect: target is dead");
				state.wasApplied(false);
				return;
			}
		}
		if(Log.loggingDebug)
			Log.debug("STATEFFECT: apply "+getName()+" "+state+" caster="+caster+" target="+target+" ");
    	// Stacking handling
    	int stackCase = stackCheck();
    	if (stackCase == 0) {
    		// Do not apply this effect
    		Log.debug("STATEFFECT: stack is 0 not apply");
    		  state.wasApplied(false);
    		return;
    	} 
    	if(target==null) {
    		Log.debug("STATEFFECT: target is null "+state);
    		  state.wasApplied(false);
    		return;
    	}
    	
    	int skillLevel = 0;
		Log.debug("STATEFFECT: about to check for skill level");
		if (effectSkillType != -1) {
	        if (!caster.getCurrentSkillInfo().getSkills().containsKey(effectSkillType))
	    	    Log.warn("STATEFFECT: player does not have this skill: " + effectSkillType);
	        else
	        	skillLevel = caster.getCurrentSkillInfo().getSkills().get(effectSkillType).getSkillLevel();
		}
		if(Log.loggingDebug)
			Log.debug("STATEFFECT: skill " + effectSkillType + " level is " + skillLevel + " skillEffectMod " + skillEffectMod.get(0));
		
    	
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
    		    if (similarEffect != null && caster.getOwnerOid().equals(similarEffect.getStackCaster())) {
    		    	fromThisCaster = true;
    		    	sameCasterEffect = similarEffect;
    		    }
    		}
    	}
    	
    	if(Log.loggingDebug)
				Log.debug("STATEFFECT: target has this effect: " + hasThisEffect + "; from this caster: " + fromThisCaster + " with stackCase: " + stackCase);
    	
    	if (stackCase == 1) {
    		// If the target already has the same effect type from the same caster, remove the old one
    		if (fromThisCaster)
    			AgisEffect.removeEffect(sameCasterEffect);
    	} else if (stackCase == 2) {
    		// If the target already has the same effect type from the same caster, increment the stack and reapply
    		if (fromThisCaster) {
    			stackLevel = sameCasterEffect.getCurrentStack();
    			//AgisEffect.removeEffect(sameCasterEffect);
    			if (stackLevel < this.stackLimit)
        		    stackLevel++;
    			sameCasterEffect.setCurrentStack(stackLevel);
    			similarEffect.setTimeUntilEnd(getDuration());
    			similarEffect.reschedule(getDuration());
				sameCasterEffect.getTarget().updateEffectsProperty();
    			
    			if(Log.loggingDebug)
    				Log.debug("STATEFFECT: applying effect: " + getName() + " with effectVal: " + getID());
    	    	for (Map.Entry<String, Float> entry : statMap.entrySet()) {
    	    		if(Log.loggingDebug)
    	    				Log.debug("STATEFFECT: adding stat modifier: " + entry.getKey() + "=" + entry.getValue() + " to: " + target.getOwnerOid());
					float statModifier = entry.getValue() + Math.signum(entry.getValue()) * (float)skillLevel * skillEffectMod.get(0);
    	    		// Multiply the modification by the stack level
    	    		statModifier = statModifier * stackLevel;
    	    		if(Log.loggingDebug)
    	    			Log.debug("STATEFFECT: statModifier: " + statModifier);
    	    		if (modifyPercentage) {
    	    			target.statReapplyPercentModifier(entry.getKey(), sameCasterEffect, statModifier, false);
    	    		} else {
    	    			target.statReapplyModifier(entry.getKey(), sameCasterEffect,  (int)Math.round(Math.ceil(statModifier)), false);
    	    		}
    	    	}
    	    	target.statSendUpdate(false);
    	    	state.wasApplied(false);
				return;
    			
    		}
    	} else if (stackCase == 3) {
    		// If the target already has the same effect type, remove the old one
    		if (hasThisEffect) {
    			//AgisEffect.removeEffect(similarEffect);
    			if(!isPassive()) {
    			similarEffect.setTimeUntilEnd(getDuration());
    			similarEffect.reschedule(getDuration());
    			similarEffect.getTarget().updateEffectsProperty();
        		target.statSendUpdate(false);
    			}
    	    	state.wasApplied(false);
    	    	return;
    		}
    	} else if (stackCase == 4) {
    		// If the target already has the same effect type, increment the stack and reapply
    		if (hasThisEffect) {
    			stackLevel = similarEffect.getCurrentStack();
    			//AgisEffect.removeEffect(similarEffect);
    			if (stackLevel < this.stackLimit)
        		    stackLevel++;
    			similarEffect.setCurrentStack(stackLevel);
    			similarEffect.setTimeUntilEnd(getDuration());
    			similarEffect.reschedule(getDuration());
    			similarEffect.getTarget().updateEffectsProperty();
    			
    			if(Log.loggingDebug)
    				Log.debug("STATEFFECT: applying effect: " + getName() + " with effectVal: " + getID());
    	    	for (Map.Entry<String, Float> entry : statMap.entrySet()) {
    	    		if(Log.loggingDebug)
    	    				Log.debug("STATEFFECT: adding stat modifier: " + entry.getKey() + "=" + entry.getValue() + " to: " + target.getOwnerOid());
					float statModifier = entry.getValue() + Math.signum(entry.getValue()) * (float)skillLevel * skillEffectMod.get(0);
    	    		// Multiply the modification by the stack level
    	    		statModifier = statModifier * stackLevel;
    	    		if(Log.loggingDebug)
    	    			Log.debug("STATEFFECT: statModifier: " + statModifier);
    	    		if (modifyPercentage) {
    	    			target.statReapplyPercentModifier(entry.getKey(), similarEffect, statModifier, false);
    	    		} else {
    	    			target.statReapplyModifier(entry.getKey(), similarEffect,  (int)Math.round(Math.ceil(statModifier)), false);
    	    		}
    	    	}
    	    	target.statSendUpdate(false);
    	    	state.wasApplied(false);
				return;
    			
    		}
    	}else if (stackCase == 5) {
			//Time stacking
    		if (fromThisCaster) {
    			stackLevel = sameCasterEffect.getCurrentStack();
    			if (stackLevel < this.stackLimit)
        		    stackLevel++;
    			sameCasterEffect.setCurrentStack(stackLevel);
    			sameCasterEffect.getTarget().updateEffectsProperty();
       		    state.wasApplied(false);
    		    return;
    		}
    	} else if (stackCase == 6) {
			//Time stacking
    		if (hasThisEffect) {
    			stackLevel = similarEffect.getCurrentStack();
    			if (stackLevel < this.stackLimit)
        		    stackLevel++;
    			similarEffect.setCurrentStack(stackLevel);
    			similarEffect.getTarget().updateEffectsProperty();
    		    state.wasApplied(false);
    		    return;
    		}
    	}
    	
    	// if (!result.equals("normal"))
    	//WorldManagerClient.sendObjChatMsg(state.getCaster().getOwnerOid(), 1, "Effect value is: " + effectNum);
    	
    
		if(Log.loggingDebug)
			Log.debug("STATEFFECT: applying effect: " + getName() + " with effectVal: " + getID());
    	for (Map.Entry<String, Float> entry : statMap.entrySet()) {
    		if(Log.loggingDebug)
    				Log.debug("STATEFFECT: adding stat modifier: " + entry.getKey() + "=" + entry.getValue() + " to: " + target.getOwnerOid());
			float statModifier = entry.getValue() + Math.signum(entry.getValue()) * (float)skillLevel * skillEffectMod.get(0);
    		// Multiply the modification by the stack level
    		statModifier = statModifier * stackLevel;
    		if(Log.loggingDebug)
    			Log.debug("STATEFFECT: statModifier: " + statModifier);
    		if (modifyPercentage) {
    			target.statAddPercentModifier(entry.getKey(), state, statModifier, false);
    		} else {
    			target.statAddModifier(entry.getKey(), state, (int)statModifier, false);
    		}
    	}
    	target.statSendUpdate(false);
    	
    	// Set the caster oid and the stack level variables
    	state.setStackCaster(caster.getOwnerOid());
    	state.setCurrentStack(stackLevel);
    	/*  if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
          	CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
      	    cE.sendSourceOid(true);
      	    cE.sendTargetOid(true);
      	    cE.invoke(target.getOwnerOid(), target.getOwnerOid());
          }*/
    	if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
			CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			state.getCoordinatedEffectStates().add(cE.invoke(state.getTarget().getOwnerOid(), state.getTarget().getOwnerOid()));
		}
    	
    	EventMessageHelper.SendCombatEvent(caster.getOwnerOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), -1, -1);
    	Log.debug("STATEFFECT: apply end");
    }
    
    public void remove(EffectState state) {
    	CombatInfo target = state.getTarget();
    	remove(state, target);
    }

    // remove the effect from the object
    public void remove(EffectState state, CombatInfo target) {
		if (target == null)
			return;
		if(Log.loggingDebug)
			Log.debug("STATEFFECT: removing statEffect: " + this.getName());
		for (Map.Entry<String, Float> entry : statMap.entrySet()) {
			if(Log.loggingDebug)
				Log.debug("STATEFFECT: removing stat effect stat: " + entry.getKey() + " from: " + target.getOwnerOid());
			if (modifyPercentage) {
				target.statRemovePercentModifier(entry.getKey(), state, false);
			} else {
				target.statRemoveModifier(entry.getKey(), state, false);
			}
		}
		target.statSendUpdate(false);
		if (state.getSource() != null && target != null) {
			EventMessageHelper.SendCombatEvent(state.getSource().getOwnerOid(), target.getOwnerOid(), 
					EventMessageHelper.COMBAT_BUFF_LOST, state.getAbilityID(), getID(), -1, -1);
		}
		super.remove(state);
    }
    
    public void unload(EffectState state, CombatInfo target) {
    	remove(state, target);
    }

    // perform the next periodic pulse for this effect on the object
	public void pulse(EffectState state) {
		Log.debug("StatEffect.pulse");
		super.pulse(state);
		if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
			CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			state.getCoordinatedEffectStates().add(cE.invoke(state.getTarget().getOwnerOid(), state.getTarget().getOwnerOid()));
		}
	}
    
    public void activate(EffectState state) {
    	super.activate(state);
    	
    	CombatInfo caster = state.getSource();
    	CombatInfo target = state.getTarget();
    	int skillLevel = 0;
    	int stackLevel = 1;
		Log.debug("COMBATHELPER: about to check for skill level");
		
		if (effectSkillType != -1) {
	        if (!caster.getCurrentSkillInfo().getSkills().containsKey(effectSkillType))
	    	    Log.warn("COMBAT HELPER: player does not have this skill: " + effectSkillType);
	        else
	        	skillLevel = caster.getCurrentSkillInfo().getSkills().get(effectSkillType).getSkillLevel();
		}
		
    	for (Map.Entry<String, Float> entry : statMap.entrySet()) {
    		if(Log.loggingDebug)
    			Log.debug("STATEFFECT: activating stat modifier: " + entry.getKey() + "=" + entry.getValue() + " to: " + target.getOwnerOid());
			float statModifier = entry.getValue() + Math.signum(entry.getValue()) * (float)skillLevel * skillEffectMod.get(0);
    		// Multiply the modification by the stack level
    		statModifier = statModifier * stackLevel;
    		if(Log.loggingDebug)
    				Log.debug("STATEFFECT: statModifier: " + statModifier);
    		if (modifyPercentage) {
    			target.statAddPercentModifier(entry.getKey(), state, statModifier, false);
    		} else {
    			target.statAddModifier(entry.getKey(), state, (int)statModifier, false);
    		}
    	}
    	target.statSendUpdate(false);
    }
    
    public void deactivate(EffectState state) {
    	super.deactivate(state);
    	
    	CombatInfo target = state.getTarget();
    	for (Map.Entry<String, Float> entry : statMap.entrySet()) {
    		if(Log.loggingDebug)
    			Log.debug("STATEFFECT: deactivating stat effect stat: " + entry.getKey() + " from: " + target.getOwnerOid());
			if (modifyPercentage) {
				target.statRemovePercentModifier(entry.getKey(), state, false);
			} else {
				target.statRemoveModifier(entry.getKey(), state, false);
			}
		}
		target.statSendUpdate(false);
    }
    
    public String getPulseCoordEffect() { return pulseCoordEffect; }
    public void setPulseCoordEffect(String coordEffect) { pulseCoordEffect = coordEffect; }
    protected String pulseCoordEffect;
    
    public void setDisplayName(String eName) {
    	displayName = eName;
    }
    public String getDisplayName() {
    	return displayName;
    }
    protected String displayName = "";
    
    public void setEffectType(int type) {
    	effectType = type;
    }
    public int GetEffectType() {
        return effectType;
    }
    public int effectType = 0;
    
    public void setModifyPercentage(boolean modifyPercentage) {
    	this.modifyPercentage = modifyPercentage;
    }
    public boolean getModifyPercentage() {
    	return modifyPercentage;
    }
    public boolean modifyPercentage = false;
    
    private static final long serialVersionUID = 1L;
}
