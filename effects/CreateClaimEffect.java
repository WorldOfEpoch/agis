package atavism.agis.effects;

import atavism.agis.plugins.*;
import atavism.server.engine.*;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.util.Log;
import atavism.agis.objects.*;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;

/**
 * Effect child class that creates a Claim at the casters location.
 * @author Andrew Harrison
 *
 */
public class CreateClaimEffect extends AgisEffect {

	public CreateClaimEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("CreateClaimEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("CreateClaimEffect: this effect is not for buildings");
			return;
		}
		CombatInfo obj = state.getTarget();
		CombatInfo caster = state.getSource();
		
		ExtensionMessage createMsg = new ExtensionMessage(caster.getOwnerOid());
		createMsg.setMsgType(VoxelClient.MSG_TYPE_CREATE_CLAIM);
		Point loc = WorldManagerClient.getObjectInfo(caster.getOwnerOid()).loc;
		createMsg.setProperty("loc", new AOVector(loc));
        Engine.getAgent().sendBroadcast(createMsg);
    }
    
    private static final long serialVersionUID = 1L;
}
