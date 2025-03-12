package atavism.agis.effects;

import atavism.agis.core.AgisEffect;
import atavism.agis.objects.CombatInfo;
import atavism.agis.plugins.AgisMobClient;
import atavism.server.engine.BasicWorldNode;
import atavism.server.math.AOVector;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;

import java.io.Serializable;
import java.util.Map;

/**
 * Effect child class used to spawn interactive object.
 * Currently used for spawning pets, but not tested in a long time.
 * @author Andrew Harrison
 *
 */
public class SpawnInteractiveObjectEffect extends AgisEffect {

    public SpawnInteractiveObjectEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("SpawnInteractiveObjectEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("SpawnInteractiveObjectEffect: this effect is not for buildings");
			return;
		}
		if(templateId<1){
			Log.error("SpawnInteractiveObjectEffect: "+this+" interactive object Profile is not selected");
			return;
		}

//		CombatInfo obj = state.getTarget();
		CombatInfo caster = state.getSource();

		BasicWorldNode wnode = WorldManagerClient.getWorldNode(caster.getOid());
		AgisMobClient.spawnInteractiveObject(wnode.getInstanceOid(),templateId, new AOVector(wnode.getLoc()));

    }

    protected int templateId = 0;
    public int getTemplateId() { return templateId; }
    public void setTemplateId(int templateId) { this.templateId = templateId; }

    private static final long serialVersionUID = 1L;
}
