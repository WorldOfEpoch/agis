package atavism.agis.effects;

import atavism.agis.objects.*;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.server.util.Log;

/**
 * Effect child class that permanently alters the level of a skill.
 * Not tested in a long time.
 * @author Andrew Harrison
 *
 */
public class TeachSkillEffect extends AgisEffect {

    public TeachSkillEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Log.debug("TeachSkillEffect.apply: Start");
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("TeachSkillEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("TeachSkillEffect: this effect is not for buildings");
			return;
		}
		CombatInfo obj = state.getTarget();
		
		if (skillType != -1 && !obj.getCurrentSkillInfo().getSkills().keySet().contains(skillType)) {
			//ClassAbilityClient.skillIncreased(obj.getOwnerOid(), skillType);
			SkillInfo.learnSkill(obj.getCurrentSkillInfo(), skillType, obj.aspect(), obj);
		} if (skillType != -1 && obj.getCurrentSkillInfo().getSkills().keySet().contains(skillType) && obj.getCurrentSkillInfo().getSkills().get(skillType).getSkillLevel() == 0) {
			SkillInfo.learnSkill(obj.getCurrentSkillInfo(), skillType, obj.aspect(), obj);
		}else {
			EventMessageHelper.SendErrorEvent(obj.getOwnerOid(), EventMessageHelper.SKILL_ALREADY_KNOWN, skillType, "");
		}
	    ExtendedCombatMessages.sendSkills(obj.getOwnerOid(), obj.getCurrentSkillInfo());
	    Log.debug("TeachSkillEffect.apply: End");
		  
    }

    public int getSkillType() { return skillType; }
    public void setSkillType(int skillType) { this.skillType = skillType; }
    protected int skillType = -1;
    
    public int getAlterValue() { return alterValue; }
    public void setAlterValue(int alterValue) { this.alterValue = alterValue; }
    protected int alterValue = -1;
    
    private static final long serialVersionUID = 1L;
}
