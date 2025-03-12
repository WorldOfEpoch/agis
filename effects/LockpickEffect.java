package atavism.agis.effects;

import java.io.Serializable;
import java.util.*;

import atavism.agis.core.AgisEffect;
import atavism.agis.objects.CombatInfo;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.server.engine.*;
import atavism.server.util.Log;

public class LockpickEffect extends AgisEffect {

	public LockpickEffect(int id, String name) {
		super(id, name);
	}

	public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("LockpickEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("LockpickEffect: this effect is not for buildings");
			return;
		}	
		int stealingSkillID = 16;
		CombatInfo caster = state.getSource();
		OID targetOid = state.getTargetOid();
		int stealingSkillLevel = 1;
		if(caster.getCurrentSkillInfo().getSkills().containsKey(stealingSkillID)) {
			stealingSkillLevel = caster.getCurrentSkillInfo().getSkills().get(stealingSkillID).getSkillLevel();
		}
		Log.debug("EFFECT: Stealing skill is: " + stealingSkillLevel);

		AgisInventoryClient.pickLock(caster.getOwnerOid(), targetOid, stealingSkillLevel);
	}
	
	private static final long serialVersionUID = 1L;
}
