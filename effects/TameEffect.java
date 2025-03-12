package atavism.agis.effects;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.objects.*;
import atavism.agis.plugins.*;
import atavism.server.util.Log;
import atavism.agis.core.*;
//import atavism.server.util.Log;

/**
 * Effect child class used to Tame a mob giving the player control of it.
 * Has not been tested in a long time.
 * @author Andrew Harrison
 *
 */
public class TameEffect extends AgisEffect {

    public TameEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("TameEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("TameEffect: this effect is not for buildings");
			return;
		}
		CombatInfo obj = state.getTarget();
        CombatInfo caster = state.getSource();
        int skillType = (int)params.get("skillType");
	    AgisMobClient.tameBeast(caster.getOwnerOid(), obj.getOwnerOid(), skillType);
    }
    
   
    private static final long serialVersionUID = 1L;
}
