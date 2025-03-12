package atavism.agis.effects;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.agis.objects.CombatInfo;
import atavism.agis.plugins.CombatClient;
import atavism.agis.util.EventMessageHelper;
import atavism.server.util.Log;

/**
 * Effect child class that makes the target unattackable for a period of time.
 * @author Andrew Harrison
 *
 */
public class ImmuneEffect extends AgisEffect {
    public ImmuneEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    	canApplyToImmine(true);
    	Log.debug("IMMUNEEFFECT: Create");
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Log.debug("ImmuneEffect: apply start");
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("ImmuneEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("ImmuneEffect: this effect is not for buildings");
			return;
		}	
		effectSkillType = (int)params.get("skillType");
        
        String abilityEvent = EventMessageHelper.COMBAT_BUFF_GAINED;
        
		CombatInfo target = state.getTarget();

		boolean ignoreDead = false;
    	if (isPassive() && params.containsKey("ignoreDead"))
			ignoreDead = (boolean) params.get("ignoreDead");
		
		if (target != null) {
			if (target.dead() && !ignoreDead) {
				Log.debug("ImmuneEffect: target is dead");
				state.wasApplied(false);
				return;
			}
		}
		CombatClient.setCombatInfoState(target.getOid(), CombatInfo.COMBAT_STATE_IMMUNE);
      
        EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), -1, -1);
    	Log.debug("ImmuneEffect: apply End");
    }

    // remove the effect from the object
    public void remove(EffectState state) {
    	CombatInfo target = state.getTarget();
        boolean anotherEffectExists = false;
    	Log.debug("ImmuneEffect: remove start");

        if (getTargetEffectsOfMatchingType(target).size() > 1) {
        	anotherEffectExists = true;
        	Log.debug("ImmuneEffect: found another immune effect so will not remove state");
        }
        Log.debug("ImmuneEffect: remove anotherEffectExists="+anotherEffectExists);
        if (!anotherEffectExists) {
            CombatClient.clearCombatInfoState(target.getOid(), CombatInfo.COMBAT_STATE_IMMUNE);
        }
	    super.remove(state);
    	Log.debug("ImmuneEffect: remove End");

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
    
    private static final long serialVersionUID = 1L;
}