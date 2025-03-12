package atavism.agis.effects;

import atavism.agis.objects.*;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.agis.plugins.ClassAbilityClient;
import atavism.server.util.Log;

/**
 * Effect child class that permanently alters the level of a skill.
 * Not tested in a long time.
 * @author Andrew Harrison
 *
 */
@Deprecated
public class AlterSkillCurrentEffect extends AgisEffect {

    public AlterSkillCurrentEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("AlterSkillCurrentEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("AlterSkillCurrentEffect: this effect is not for buildings");
			return;
		}
		CombatInfo obj = state.getTarget();
		
		if (skillType == -1) {
			// Update all skills
    	    for (int skillID : obj.getCurrentSkillInfo().getSkills().keySet()) {
    	    	ClassAbilityClient.skillAlterCurrent(obj.getOwnerOid(), skillID, alterValue, false);
    	    }
		} else {
			ClassAbilityClient.skillAlterCurrent(obj.getOwnerOid(), skillType, alterValue, false);
		}
    }

    public int getSkillType() { return skillType; }
    public void setSkillType(int skillType) { this.skillType = skillType; }
    protected int skillType = -1;
    
    public int getAlterValue() { return alterValue; }
    public void setAlterValue(int alterValue) { this.alterValue = alterValue; }
    protected int alterValue = -1;
    
    private static final long serialVersionUID = 1L;
}
