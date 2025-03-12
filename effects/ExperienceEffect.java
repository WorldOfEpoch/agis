package atavism.agis.effects;

import atavism.agis.core.AgisEffect;
import atavism.agis.objects.CombatInfo;
import atavism.agis.objects.PlayerFactionData;
import atavism.agis.plugins.ClassAbilityClient;
import atavism.agis.plugins.ClassAbilityPlugin;
import atavism.agis.plugins.CombatClient;
import atavism.agis.util.EventMessageHelper;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.Namespace;
import atavism.server.util.Log;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Effect child class that add experience for the player.
 *
 */
public class ExperienceEffect extends AgisEffect {

    public ExperienceEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(false);
    }
    
    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("ExperienceEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
		if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("ExperienceEffect: this effect is not for buildings");
			return;
		}
		CombatInfo target = state.getTarget();

		if (experience == 0) {
			Log.debug("ExperienceEffect: value not been set. Effect name: " + getName());
			return;
		}
		Log.debug("ExperienceEffect: applying Experience alteration ");
		if (experience > 0)
			ClassAbilityPlugin.giveExp(target.getOid(), experience);
		else
			ClassAbilityPlugin.lostExp(target.getOid(), Math.abs(experience));

	}

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    }
    
    protected int experience = 0;
    public void setExperience(int value) {experience = value;}
    public int getExperience() {return experience;}

    private static final long serialVersionUID = 1L;
}