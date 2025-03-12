package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.util.EventMessageHelper;
import atavism.server.util.Log;
import atavism.agis.core.*;

import java.io.Serializable;
import java.util.*;

public class HealEffect extends AgisEffect {

	static Random random = new Random();

    public HealEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("HealEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("HealEffect: this effect is not for buildings");
			return;
		}	
        int heal = minHeal;

        if (maxHeal > minHeal) {
            heal += random.nextInt(maxHeal - minHeal);
        }

        CombatInfo obj = state.getTarget();
		if (heal == 0) {
		    return;
		}
        obj.statModifyBaseValue(getHealProperty(), heal);
        obj.sendStatusUpdate();
        
        String abilityEvent = EventMessageHelper.COMBAT_HEAL;
        EventMessageHelper.SendCombatEvent(state.getSourceOid(), obj.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), heal, -1,getHealProperty(),"");
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
		super.pulse(state);
        int heal = minPulseHeal;
        Map<String, Serializable> params = state.getParams();
        if (maxPulseHeal > minPulseHeal) {
            heal += random.nextInt(maxPulseHeal - minPulseHeal);
        }
        Set<EffectState> effects = state.getTarget().getCurrentEffects() != null?new HashSet<EffectState>(state.getTarget().getCurrentEffects()):new HashSet<EffectState>();
		Set<EffectState> ceffects = state.getSource().getCurrentEffects()!= null?new HashSet<EffectState>(state.getSource().getCurrentEffects()):new HashSet<EffectState>();
	  //Triggers Calculations
        Log.debug("HealEffect.pulse: Check Triggetson caster");
        for (EffectState ef : ceffects) {
			if (ef.getEffect().getClass().equals(TriggerEffect.class)) {
				TriggerEffect se = (TriggerEffect) ef.getEffect();
				heal = se.Calculate(TriggerProfile.Type.HEAL, tags, heal, state.getSource(), state.getTarget(), ef);
			}
		}
        Log.debug("HealEffect.pulse: Check Triggetson target");
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
		CombatClient.sendCombatEvent(state.getSourceOid(), false,TriggerProfile.Type.HEAL);
		CombatClient.sendCombatEvent(state.getTargetOid(),true, TriggerProfile.Type.HEAL);
		if (heal == 0) {
		    return;
		}
		
        CombatInfo obj = state.getTarget();
        obj.statModifyBaseValue(getHealProperty(), heal);
        obj.sendStatusUpdate();
        
        String abilityEvent = EventMessageHelper.COMBAT_HEAL;
        EventMessageHelper.SendCombatEvent(state.getSourceOid(), obj.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), heal, -1,getHealProperty(),"");
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
    
    private static final long serialVersionUID = 1L;
}
