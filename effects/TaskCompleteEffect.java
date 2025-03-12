package atavism.agis.effects;

import atavism.agis.plugins.*;
import atavism.server.engine.*;
import atavism.server.util.Log;
import atavism.agis.objects.*;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;

/**
 * Effect child class that sends a TaskUpdateMessage. This is commonly used to mark objectives complete for quests.
 * Usage examples could be when a player has entered a certain area or has finished an escort.
 * @author Andrew Harrison
 *
 */
public class TaskCompleteEffect extends AgisEffect {

	public TaskCompleteEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("TaskCompleteEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("TaskCompleteEffect: this effect is not for buildings");
			return;
		}
		CombatInfo obj = state.getTarget();
		CombatInfo caster = state.getSource();
		
		QuestClient.TaskUpdateMessage msg = new QuestClient.TaskUpdateMessage(obj.getOwnerOid(), taskID, 1);
		Engine.getAgent().sendBroadcast(msg);
    }

    public int getTaskID() { return taskID; }
    public void setTaskID(int taskID) { this.taskID = taskID; }
    protected int taskID = -1;
    
    private static final long serialVersionUID = 1L;
}
