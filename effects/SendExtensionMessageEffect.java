package atavism.agis.effects;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import atavism.server.engine.*;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;
import atavism.agis.objects.*;
import atavism.agis.core.*;

/**
 * Effect child class used to send an ExtensionMessage to the player. 
 * Not sure if this is a good idea.
 * @author Andrew Harrison
 *
 */
@Deprecated
public class SendExtensionMessageEffect extends AgisEffect {

    public SendExtensionMessageEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("SendExtensionMessageEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("SendExtensionMessageEffect: this effect is not for buildings");
			return;
		}	
		CombatInfo obj = state.getTarget();
		CombatInfo caster = state.getSource();
		
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", messageType);

		TargetedExtensionMessage eMsg = new TargetedExtensionMessage(
			WorldManagerClient.MSG_TYPE_EXTENSION, caster.getOwnerOid(), 
			caster.getOwnerOid(), false, props);
		Engine.getAgent().sendBroadcast(eMsg);
    }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    protected String messageType = "";
}
