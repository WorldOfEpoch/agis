package atavism.agis.effects;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import atavism.agis.plugins.*;
import atavism.server.engine.*;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.agis.objects.*;
import atavism.agis.core.*;

/**
 * Effect child class that starts the build of the specified build
 * object for the caster.
 * @author Andrew Harrison
 *
 */
public class BuildObjectEffect extends AgisEffect {

	public BuildObjectEffect(int id, String name) {
		super(id, name);
	}

	// add the effect to the object
	public void apply(EffectState state) {
		super.apply(state);
		CombatInfo obj = state.getTarget();
		CombatInfo caster = state.getSource();

		// Get Build Object Template from the Voxel Plugin
		BuildObjectTemplate tmpl = VoxelClient.getBuildingTemplate(buildObjectTemplateID);

		if (tmpl == null)
			return;
		// Check user has required items
		LinkedList<Integer> components = new LinkedList<Integer>();
		LinkedList<Integer> componentCounts = new LinkedList<Integer>();
		for (int itemReq : tmpl.getStage(0).getItemReqs().keySet()) {
			components.add(itemReq);
			componentCounts.add(tmpl.getStage(0).getItemReqs().get(itemReq));
		}

		boolean hasItems = AgisInventoryClient.checkComponents(caster.getOwnerOid(), components, componentCounts);
		if (!hasItems) {
			return;
		}

		// Send message to client
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "start_build_object");
		props.put("buildObjectTemplate", tmpl.getId());
		props.put("gameObject", tmpl.getStage(0).getGameObject());
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, caster.getOwnerOid(), caster.getOwnerOid(), false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

    public int getBuildObjectTemplateID() { return buildObjectTemplateID; }
    public void setBuildObjectTemplateID(int buildObjectTemplateID) { this.buildObjectTemplateID = buildObjectTemplateID; }
    protected int buildObjectTemplateID = -1;
    
    private static final long serialVersionUID = 1L;
}
