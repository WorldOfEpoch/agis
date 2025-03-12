package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.core.*;
import atavism.server.util.*;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that alters the value of a steath stat on the target for a period
 * of time. Can be permanent if the effect is a passive one.
 * 
 */
public class StealthEffect extends AgisEffect {
	public StealthEffect() {

	}

	public StealthEffect(int id, String name) {
		super(id, name);
		isPeriodic(false);
		isPersistent(true);
	}

	// add the effect to the object
	public void apply(EffectState state) {
		super.apply(state);
		Log.error("StealthEffect: Start apply");
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("StealthEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("StealthEffect: this effect is not for buildings");
			return;
		}
		String abilityEvent = EventMessageHelper.COMBAT_BUFF_GAINED;

		CombatInfo caster = state.getSource();
		CombatInfo target = state.getTarget();

		boolean ignoreDead = false;
    	if (isPassive() && params.containsKey("ignoreDead"))
			ignoreDead = (boolean) params.get("ignoreDead");
		
		if (target != null) {
			if (target.dead() && !ignoreDead) {
				Log.debug("StealthEffect: target is dead");
				state.wasApplied(false);
				return;
			}
		}
		// Stacking handling
		int stackCase = stackCheck();
		if (stackCase == 0) {
			// Do not apply this effect
			Log.error("StealthEffect: stack is 0 not apply");
			return;
		}
		if (target == null) {
			Log.error("StealthEffect: target is null " + state);
			return;
		}

		int skillLevel = 0;
		Log.debug("StealthEffect: about to check for skill level");
		if (effectSkillType != -1) {
			if (!caster.getCurrentSkillInfo().getSkills().containsKey(effectSkillType))
				Log.warn("StealthEffect: player does not have this skill: " + effectSkillType);
			else
				skillLevel = caster.getCurrentSkillInfo().getSkills().get(effectSkillType).getSkillLevel();
		}
		if (Log.loggingDebug)
			Log.debug("StealthEffect: skill " + effectSkillType + " level is " + skillLevel + " skillEffectMod " + skillEffectMod.get(0));
		
		
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
				if (caster.getOwnerOid().equals(similarEffect.getStackCaster())) {
					fromThisCaster = true;
					sameCasterEffect = similarEffect;
				}
			}
		}

		if (Log.loggingDebug)
			Log.debug("StealthEffect: target has this effect: " + hasThisEffect + "; from this caster: " + fromThisCaster + " with stackCase: " + stackCase);

		if (stackCase == 1) {
			// If the target already has the same effect type from the same caster, remove
			// the old one
			if (fromThisCaster)
				AgisEffect.removeEffect(sameCasterEffect);
		} else if (stackCase == 2) {
			// If the target already has the same effect type from the same caster, increment the stack and reaplay 
			if (fromThisCaster) {
				stackLevel = sameCasterEffect.getCurrentStack();
				//AgisEffect.removeEffect(sameCasterEffect);
				if (stackLevel < this.stackLimit)
					stackLevel++;
				sameCasterEffect.setCurrentStack(stackLevel);
				sameCasterEffect.getTarget().updateEffectsProperty();
    			float statModifier = (stealthValue + (float) skillLevel * skillEffectMod.get(0));
    			// Multiply the modification by the stack level
    			statModifier = statModifier * stackLevel;
    			if (Log.loggingDebug)
    				Log.debug("StealthEffect: statModifier: " + statModifier+" "+CombatPlugin.STEALTH_STAT);
    			if (CombatPlugin.STEALTH_STAT != null) {
    				AgisStat stat = (AgisStat) target.getProperty(CombatPlugin.STEALTH_STAT);
    				stat.removeModifier("Ability");
    				stat.removePercentModifier("Ability");
    				target.statReapplyModifier(CombatPlugin.STEALTH_STAT, sameCasterEffect, (int)Math.round(Math.ceil(statModifier)), true);
    			}
    			Log.debug("StealthEffect: apply set stat");
    			target.statSendUpdate(false);
    			state.wasApplied(false);
				return;
			}
		} else if (stackCase == 3) {
			// If the target already has the same effect type, remove the old one
			if (hasThisEffect)
				AgisEffect.removeEffect(similarEffect);
		} else if (stackCase == 4) {
			// If the target already has the same effect type, remove the old one and increment the stack nd reaplay 
			if (hasThisEffect) {
				stackLevel = similarEffect.getCurrentStack();
				//AgisEffect.removeEffect(similarEffect);
				if (stackLevel < this.stackLimit)
					stackLevel++;
				similarEffect.setCurrentStack(stackLevel);
    			similarEffect.getTarget().updateEffectsProperty();
    			float statModifier = (stealthValue + (float) skillLevel * skillEffectMod.get(0));
    			// Multiply the modification by the stack level
    			statModifier = statModifier * stackLevel;
    			if (Log.loggingDebug)
    				Log.debug("StealthEffect: statModifier: " + statModifier+" "+CombatPlugin.STEALTH_STAT);
    			if (CombatPlugin.STEALTH_STAT != null) {
    				AgisStat stat = (AgisStat) target.getProperty(CombatPlugin.STEALTH_STAT);
    				stat.removeModifier("Ability");
    				stat.removePercentModifier("Ability");
    				target.statReapplyModifier(CombatPlugin.STEALTH_STAT, similarEffect, (int)Math.round(Math.ceil(statModifier)), true);
    			}
    			Log.debug("StealthEffect: apply set stat");
    			target.statSendUpdate(false);
    			state.wasApplied(false);
				return;
			}
		} else if (stackCase == 5) {
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

	
		if (Log.loggingDebug)
			Log.debug("StealthEffect: applying effect: " + getName() + " with effectVal: " + getID());

		float statModifier = (stealthValue + (float) skillLevel * skillEffectMod.get(0));
		// Multiply the modification by the stack level
		statModifier = statModifier * stackLevel;
		if (Log.loggingDebug)
			Log.debug("StealthEffect: statModifier: " + statModifier+" "+CombatPlugin.STEALTH_STAT);
		if (CombatPlugin.STEALTH_STAT != null) {
			AgisStat stat = (AgisStat) target.getProperty(CombatPlugin.STEALTH_STAT);
			stat.removeModifier("Ability");
			stat.removePercentModifier("Ability");
			target.statAddModifier(CombatPlugin.STEALTH_STAT, state, Math.round(statModifier), true);
		}
		Log.debug("StealthEffect: apply set stat");
		target.statSendUpdate(false);
		
		Log.debug("StealthEffect: apply setend update");
		
		// Set the caster oid and the stack level variables
		state.setStackCaster(caster.getOwnerOid());
		state.setCurrentStack(stackLevel);

		EventMessageHelper.SendCombatEvent(caster.getOwnerOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), -1, -1);
		Log.debug("StealthEffect: apply end");
	}

	public void remove(EffectState state) {
		CombatInfo target = state.getTarget();
		remove(state, target);
	}

	// remove the effect from the object
	public void remove(EffectState state, CombatInfo target) {
		if (target == null)
			return;
		if (Log.loggingDebug)
			Log.debug("StealthEffect: removing effect: " + this.getName());
		if (CombatPlugin.STEALTH_STAT != null) {
			target.statRemoveModifier(CombatPlugin.STEALTH_STAT, state, false);
		}
		target.statSendUpdate(false);
		if (state.getSource() != null && target != null) {
			EventMessageHelper.SendCombatEvent(state.getSource().getOwnerOid(), target.getOwnerOid(), EventMessageHelper.COMBAT_BUFF_LOST, state.getAbilityID(), getID(), -1, -1);
		}
		super.remove(state);
	}

	public void unload(EffectState state, CombatInfo target) {
		remove(state, target);
	}

	// perform the next periodic pulse for this effect on the object
	public void pulse(EffectState state) {
		super.pulse(state);
	}

	public void activate(EffectState state) {
		super.activate(state);

		CombatInfo caster = state.getSource();
		CombatInfo target = state.getTarget();
		int skillLevel = 0;
		int stackLevel = 1;
		Log.debug("StealthEffect: about to check for skill level");

		if (effectSkillType != -1) {
			if (!caster.getCurrentSkillInfo().getSkills().containsKey(effectSkillType))
				Log.warn("StealthEffect: player does not have this skill: " + effectSkillType);
			else
				skillLevel = caster.getCurrentSkillInfo().getSkills().get(effectSkillType).getSkillLevel();
		}
		float statModifier = (stealthValue + (float) skillLevel * skillEffectMod.get(0));
		// Multiply the modification by the stack level
		statModifier = statModifier * stackLevel;
		if (CombatPlugin.STEALTH_STAT != null)
			target.statAddModifier(CombatPlugin.STEALTH_STAT, state, Math.round(statModifier), false);
		target.statSendUpdate(false);
	}

	public void deactivate(EffectState state) {
		super.deactivate(state);

		CombatInfo target = state.getTarget();
		if (CombatPlugin.STEALTH_STAT != null)
			target.statRemoveModifier(CombatPlugin.STEALTH_STAT, state, false);
		target.statSendUpdate(false);
	}

	
	public void setStealthValue(int stealthValue) {
		this.stealthValue = stealthValue;
	}

	public int getStealthValue() {
		return stealthValue;
	}

	public int stealthValue = 0;

	private static final long serialVersionUID = 1L;
}