package atavism.agis.effects;

import atavism.agis.core.AgisEffect;
import atavism.agis.objects.CombatInfo;
import atavism.agis.plugins.ClassAbilityPlugin;
import atavism.agis.plugins.CombatClient;
import atavism.server.util.Log;

import java.io.Serializable;
import java.util.Map;

/**
 * Effect child class that add skill experience for the player.
 *
 */
public class SkillExperienceEffect extends AgisEffect {

    public SkillExperienceEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(false);
    }
    
    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("SkillExperienceEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
		if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("SkillExperienceEffect: this effect is not for buildings");
			return;
		}

		if (experience == 0) {
			Log.debug("SkillExperienceEffect: value not been set. Effect name: " + getName());
			return;
		}
		Log.debug("SkillExperienceEffect: applying Experience alteration ");
		CombatClient.abilityUsed(state.getTargetOid(), skill, experience, 1);

	}

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    }

	protected int skill = 0;
	public void setSkill(int skill) {this.skill = skill;}
	public int getSkill() {return skill;}

	protected int experience = 0;
    public void setExperience(int value) {experience = value;}
    public int getExperience() {return experience;}

    private static final long serialVersionUID = 1L;
}