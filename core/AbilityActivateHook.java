package atavism.agis.core;

import atavism.server.engine.OID;
import atavism.server.util.*;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;

/**
 * an activate hook for items that trigger abilities when the item is activated,
 * the mob uses the ability
 */
public class AbilityActivateHook implements ActivateHook {
	public AbilityActivateHook() {
		super();
	}

	public AbilityActivateHook(AgisAbility ability, boolean oadelete) {
		super();
		Log.debug("AJ: creating abilityactivatehook with ability: " + ability.getID());
		setAbilityID(ability.getID());
		this.oadelete = oadelete;
	}

	public AbilityActivateHook(int abilityID, boolean oadelete) {
		super();
		setAbilityID(abilityID);
		this.oadelete = oadelete;
	}

	public void setAbilityID(int abilityID) {
		if (abilityID == -1) {
			Log.error("AbilityActivateHook.setAbility: bad ability");
		}
		Log.debug("AJ: setting abilityID to: " + abilityID);
		this.abilityID = abilityID;
	}

	public int getAbilityID() {
		return abilityID;
	}

	protected int abilityID;
	protected boolean oadelete = false;

	public AgisAbility getAbility() {
		if (abilityID == -1)
			return null;
		return Agis.AbilityManager.get(abilityID);
	}

	public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
		if (Log.loggingDebug)
			Log.debug("AbilityActivateHook.activate: activator=" + activatorOid + " item=" + item + " ability=" + abilityID + " target=" + targetOid);
		// CombatClient.startAbility(abilityName, activatorOid, targetOid,
		// item.getOid());
		boolean rv = false;
		try {
			if(abilityID > 0)
				rv = CombatClient.startAbilityResponse(abilityID, activatorOid, targetOid, item, null);
		}catch (Exception e){
			Log.exception("AbilityActivateHook: Exception",e);
		}
		if (Log.loggingDebug)
			Log.debug("AbilityActivateHook.activate: activator=" + activatorOid + " item=" + item + " ability=" + abilityID + " target=" + targetOid+" response="+rv);
		
		return oadelete && rv;
	}

	public String toString() {
		return "AbilityActivateHook:ability=" + abilityID;
	}

	private static final long serialVersionUID = 1L;
}
