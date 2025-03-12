package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.core.*;
import atavism.agis.util.*;
import atavism.server.util.Log;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that takes health from the caster and gives it to the target. Happens instantly.
 * @author Andrew Harrison
 *
 */
public class HealthTransferEffect extends AgisEffect {
	
	static Random random = new Random();

    public HealthTransferEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("HealthTransferEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("HealthTransferEffect: this effect is not for buildings");
			return;
		}	
		String dmgType =  (String)params.get("dmgType");
		  
		int heal = minHeal;
        if (maxHeal > minHeal) {
        	heal += random.nextInt(maxHeal - minHeal);
        }
        
        VitalityStatDef vitalStatDef = (VitalityStatDef) CombatPlugin.lookupStatDef(getHealProperty());
		float percentage = minPercentageHeal;
		if (maxPercentageHeal > minPercentageHeal) {
			percentage = minPercentageHeal + random.nextFloat() * (maxPercentageHeal - minPercentageHeal);
		}
		heal += state.getTarget().statGetCurrentValue(vitalStatDef.getMaxStat()) * percentage / 100;
		
        heal = CombatHelper.CalcHeal(state.getTarget(), state.getSource(), heal, skillEffectMod.get(0), effectSkillType,dmgType,params);
        
        String abilityEvent = EventMessageHelper.COMBAT_HEALTH_TRANSFER;
        
        CombatInfo caster = state.getSource();
        int casterHealth = caster.statGetCurrentValue(getHealProperty());
        if (heal > casterHealth)
        	heal = casterHealth - 1;

        CombatInfo target = state.getTarget();
		if (heal < 1) {
		    return;
		}
		caster.statModifyBaseValue(getHealProperty(), -heal);
        caster.sendStatusUpdate();
        double healModified = heal * transferModifier;
        int newHeal = (int) healModified;
        target.statModifyBaseValue(getHealProperty(), newHeal);
        target.sendStatusUpdate();
        
        EventMessageHelper.SendCombatEvent(caster.getOwnerOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), heal, -1);
    }

    // perform the next periodic pulse for this effect on the object
	public void pulse(EffectState state) {
		super.pulse(state);
		int heal = minPulseHeal;
		Map<String, Serializable> params = state.getParams();
		String abilityEvent = EventMessageHelper.COMBAT_HEALTH_TRANSFER;

		CombatInfo caster = state.getSource();
		int casterHealth = caster.statGetCurrentValue(getHealProperty());

		Set<EffectState> effects = state.getTarget().getCurrentEffects() != null ? new HashSet<EffectState>(state.getTarget().getCurrentEffects()) : new HashSet<EffectState>();
		Set<EffectState> ceffects = state.getSource().getCurrentEffects() != null ? new HashSet<EffectState>(state.getSource().getCurrentEffects()) : new HashSet<EffectState>();
		 // Triggers Calculations
		Log.debug("HealthTransferEffect.pulse: Check Triggets on caster");
		for (EffectState ef : ceffects) {
			if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
				TriggerEffect se = (TriggerEffect) ef.getEffect();
				heal = se.Calculate(TriggerProfile.Type.HEAL, tags, heal, state.getSource(), state.getTarget(), ef);
			}
		}
		Log.debug("HealthTransferEffect.pulse: Check Triggets on target");
		if (!state.getSourceOid().equals(state.getTargetOid())) {
			for (EffectState ef : effects) {
				if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
					TriggerEffect se = (TriggerEffect) ef.getEffect();
					heal = se.Calculate(TriggerProfile.Type.HEAL, tags, heal, state.getSource(), state.getTarget(), ef);
				}
			}
		}
		// Ability Triggers Calculations
		if(state.getAbilityID() > 0) {
			AgisAbility aa = Agis.AbilityManager.get(state.getAbilityID());
			if(aa!=null) {
				long time = (long) params.get("powerUp");
				heal = aa.getPowerUpDefinition(time).CalculateTriggers(TriggerProfile.Type.HEAL, tags, heal, state.getSource(), state.getTarget(),aa);
			}

		}
		CombatClient.sendCombatEvent(state.getSourceOid(), false, TriggerProfile.Type.HEAL);
		CombatClient.sendCombatEvent(state.getTargetOid(), true, TriggerProfile.Type.HEAL);
		Log.debug("HealthTransferEffect.pulse: Check Triggetson END heal="+heal);
		
		if (heal > casterHealth)
			heal = casterHealth - 1;
		if (heal < 1) {
			return;
		}

		caster.statModifyBaseValue(getHealProperty(), -heal);
		caster.sendStatusUpdate();
		double healModified = heal * transferModifier;
		int newHeal = (int) healModified;
		CombatInfo target = state.getTarget();
		target.statModifyBaseValue(getHealProperty(), newHeal);
		target.sendStatusUpdate();

        EventMessageHelper.SendCombatEvent(caster.getOwnerOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), heal, -1);
    }

    public int getMinInstantHeal() { return minHeal; }
    public void setMinInstantHeal(int hps) { minHeal = hps; }
    protected int minHeal = 0;

    public int getMaxInstantHeal() { return maxHeal; }
    public void setMaxInstantHeal(int hps) { maxHeal = hps; }
    protected int maxHeal = 0;

    public int getMinPulseHeal() { return minPulseHeal; }
    public void setMinPulseHeal(int hps) { minPulseHeal = hps; }
    protected int minPulseHeal = 0;

    public int getMaxPulseHeal() { return maxPulseHeal; }
    public void setMaxPulseHeal(int hps) { maxPulseHeal = hps; }
    protected int maxPulseHeal = 0;

    public String getHealProperty() { return healProperty; }
    public void setHealProperty(String property) { healProperty = property; }
    protected String healProperty = CombatPlugin.HEALTH_STAT;
    
    public double getTransferModifier() { return transferModifier; }
    public void setTransferModifier(double modifier) { transferModifier = modifier; }
    protected double transferModifier = 1.0;
    
    public float getMinPercentageHeal() { return minPercentageHeal; }
    public void setMinPercentageHeal(float val) { minPercentageHeal = val; }
    protected float minPercentageHeal = 0f;
   
    public float getMaxPercentageHeal() { return maxPercentageHeal; }
    public void setMaxPercentageHeal(float val) { maxPercentageHeal = val; }
    protected float maxPercentageHeal = 0f;

    private static final long serialVersionUID = 1L;
}
