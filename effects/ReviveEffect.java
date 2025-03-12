package atavism.agis.effects;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.agis.objects.CombatInfo;
import atavism.agis.objects.VitalityStatDef;
import atavism.agis.plugins.AgisWorldManagerClient;
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
public class ReviveEffect extends AgisEffect {
    public ReviveEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("ReviveEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("ReviveEffect: this effect is not for buildings");
			return;
		}	
		effectSkillType = (int)params.get("skillType");
        
        String abilityEvent = EventMessageHelper.COMBAT_REVIVED;
        
        CombatInfo target = state.getTarget();
        if (!target.dead()) {
        	Log.debug("REVIVE: returning as target is not dead");
        	return;
        }
        if(target.getDeathPermanently()) {
        	Log.debug("REVIVE: returning as target has Death Permanently");
        	return;
        }
        	
        target.setDeadState(false);
        target.clearState(CombatInfo.COMBAT_STATE_SPIRIT);
        EnginePlugin.setObjectPropertyNoResponse(target.getOwnerOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOMOVE, false);
        EnginePlugin.setObjectPropertyNoResponse(target.getOwnerOid(), WorldManagerClient.NAMESPACE, WorldManagerClient.WORLD_PROP_NOTURN, false);
        
        CombatPlugin.setReleaseStatValues(target);
        if (CombatPlugin.lookupStatDef(healthStat) != null && CombatPlugin.lookupStatDef(healthStat) instanceof VitalityStatDef) {
        	VitalityStatDef vitalStatDef = (VitalityStatDef) CombatPlugin.lookupStatDef(healthStat);
        	target.statSetBaseValue(healthStat, target.statGetCurrentValue(vitalStatDef.getMaxStat()) * healthPercent / 100);
        }
        if (CombatPlugin.lookupStatDef(vitalityStat) != null && CombatPlugin.lookupStatDef(vitalityStat) instanceof VitalityStatDef) {
        	VitalityStatDef vitalStatDef = (VitalityStatDef) CombatPlugin.lookupStatDef(vitalityStat);
        	target.statSetBaseValue(vitalityStat, target.statGetCurrentValue(vitalStatDef.getMaxStat()) * vitalityPercent / 100);
        }
        if (CombatPlugin.lookupStatDef(vitalityStat2) != null && CombatPlugin.lookupStatDef(vitalityStat2) instanceof VitalityStatDef) {
        	VitalityStatDef vitalStatDef = (VitalityStatDef) CombatPlugin.lookupStatDef(vitalityStat2);
        	target.statSetBaseValue(vitalityStat2, target.statGetCurrentValue(vitalStatDef.getMaxStat()) * vitalityPercent2 / 100);
        }
        target.sendStatusUpdate();
        AgisWorldManagerClient.sendRevived(target.getOwnerOid());
        EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), -1, -1);
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    }
    
    public void setHealthStat(String stat) {
    	healthStat = stat;
    }
    public String getHealthStat() {
    	return healthStat;
    }
    String healthStat = "";
    
    public void setHealthPercent(int healthPercent) {
    	this.healthPercent = healthPercent;
    }
    public int GetHealthPercent() {
        return healthPercent;
    }
    public int healthPercent = 0;
    
    public void setVitalityStat(String stat) {
    	vitalityStat = stat;
    }
    public String getVitalityStat() {
    	return vitalityStat;
    }
    String vitalityStat = "";
    
    public void setVitalityPercent(int vitalityPercent) {
    	this.vitalityPercent = vitalityPercent;
    }
    public int GetVitalityPercent() {
        return vitalityPercent;
    }
    public int vitalityPercent = 0;
    
    public void setVitalityStat2(String stat) {
    	vitalityStat2 = stat;
    }
    public String getVitalityStat2() {
    	return vitalityStat2;
    }
    String vitalityStat2 = "";
    
    public void setVitalityPercent2(int vitalityPercent2) {
    	this.vitalityPercent2 = vitalityPercent2;
    }
    public int GetVitalityPercent2() {
        return vitalityPercent2;
    }
    public int vitalityPercent2 = 0;
    
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
    
    private static final long serialVersionUID = 1L;
}