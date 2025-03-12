package atavism.agis.effects;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.agis.plugins.ClassAbilityClient;
import atavism.server.util.Log;

/**
 * Effect child class that is used to unlearn a ability from a target.
 * Currently not used.
 *
 */
public class UnlearnAbilityEffect extends AgisEffect {

	public UnlearnAbilityEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(false);
    }

    public UnlearnAbilityEffect(int id, String name, int abilityID) {
    	super(id, name);
    	isPeriodic(false);
		isPersistent(false);
		setAbilityID(abilityID);
    }

    public int getAbilityID() { return abilityID; }
    public void setAbilityID(int id) { abilityID = id; }
    protected int abilityID = -1;

    public String getCategory() { return category; }
    public void setCategory(String name) { category = name; }
    protected String category = null;

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("UnlearnAbilityEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("UnlearnAbilityEffect: this effect is not for buildings");
			return;
		}
		//CombatInfo mob = state.getSource();		
		ClassAbilityClient.unlearnAbility(state.getTargetOid(), abilityID);
		//CombatPlugin.sendAbilityUpdate(mob);
    }
    private static final long serialVersionUID = 1L;
}
