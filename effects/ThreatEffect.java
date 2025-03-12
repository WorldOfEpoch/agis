package atavism.agis.effects;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.agis.plugins.CombatClient;
import atavism.server.util.Log;

/**
 * Effect child class that permanently alters the level of a skill.
 * Not tested in a long time.
 * @author Andrew Harrison
 *
 */
public class ThreatEffect extends AgisEffect {

    public ThreatEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
	public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("ThreatEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("ThreatEffect: this effect is not for buildings");
			return;
		}
		CombatClient.sendAlterThreat(state.getTargetOid(), state.getSourceOid(), alterValue);
		
    }
    
    public int getAlterValue() { return alterValue; }
    public void setAlterValue(int alterValue) { this.alterValue = alterValue; }
    protected int alterValue = 0;
    
    private static final long serialVersionUID = 1L;
}
