package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.plugins.AgisWorldManagerClient;
import atavism.agis.plugins.AgisWorldManagerPlugin;
import atavism.agis.util.EventMessageHelper;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.server.engine.*;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.*;

/**
 * Effect child class that sets the mount property on the target. 
 * The targets speed and model property will be reset when the effect has finished.
 * @author Andrew Harrison
 *
 */
public class MountEffect extends AgisEffect {
    public MountEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	if(Log.loggingDebug)
    		Log.debug("MountEffect.apply");
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("MountEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("MountEffect: this effect is not for buildings");
			return;
		}	
    	String abilityEvent = EventMessageHelper.COMBAT_BUFF_GAINED;
    	CombatInfo target = state.getTarget();
    	
    	// First see if the user has another mount 
    	EffectState similarEffect = null;
    	boolean hasThisEffect = false;
    	for (EffectState existingState : target.getCurrentEffects()) {
    		if (existingState.getEffect() instanceof MountEffect /*&& !existingState.equals(state)*/) {
    			similarEffect = existingState;
    			if (existingState.getEffectID() == getID()) {
    				hasThisEffect = true;
    			}
    			Log.debug("MOUNT: found similar effect, it is identical? " + hasThisEffect);
    			break;
    		}
    	}
    	Log.debug("MOUNT: hasThisEffect=" + hasThisEffect+" similarEffect="+similarEffect);
    	if (hasThisEffect) {
    		// Remove the old one and then abort this one
    		AgisEffect.removeEffect(similarEffect);
    		//AgisEffect.removeEffect(state);
    		state.wasApplied(false);
    		Log.debug("MOUNT: apply END");
    		return;
    	} else if (similarEffect != null) {
    		// Remove the old one (easy enough)
    		AgisEffect.removeEffect(similarEffect);
    	}

    	//EnginePlugin.setObjectPropertyNoResponse(target.getOid(), WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_MOVEMENT_STATE, mountType);
		EnginePlugin.setObjectPropertyNoResponse(target.getOid(), WorldManagerClient.NAMESPACE, MOUNT_PROP, model);
    	target.statAddPercentModifier(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, state, mountSpeedIncrease, true);
    	
    	if (statName != null && statChange != 0) {
    		target.statAddModifier(statName, state, (int) statChange, true);
    	}
    	
    	EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), -1, -1);
    	Log.debug("MOUNT: set player mounted");
    	
    	if (mountType == 2) {
    		AgisWorldManagerClient.sendSetMovementStateMessage(target.getOid(), AgisWorldManagerPlugin.MOVEMENT_STATE_FLYING);
    	}
    }
    public void resume(EffectState state) {
    	if(Log.loggingDebug)
    		Log.debug("MountEffect.resume");
    	if (mountType == 2) {
    //		AgisWorldManagerClient.sendSetMovementStateMessage(state.getTargetOid(), AgisWorldManagerPlugin.MOVEMENT_STATE_FLYING);
    	}
    }
    // remove the effect from the object
    public void remove(EffectState state) {
		CombatInfo target = state.getTarget();
		if(Log.loggingDebug)
    		Log.debug("MountEffect.remove");
		target.statRemovePercentModifier(AgisWorldManagerPlugin.PROP_MOVEMENT_SPEED, state, true);
		if (statName != null && statChange != 0) {
			target.statRemoveModifier(statName, state, true);
		}
		EnginePlugin.setObjectPropertyNoResponse(target.getOid(), WorldManagerClient.NAMESPACE, MOUNT_PROP, "");
		Log.debug("MOUNT: set player unmounted");
		
		if (mountType == 2) {
    		AgisWorldManagerClient.sendSetMovementStateMessage(target.getOid(), AgisWorldManagerPlugin.MOVEMENT_STATE_RUNNING);
    	}
		
		EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), EventMessageHelper.COMBAT_BUFF_LOST, state.getAbilityID(), getID(), -1, -1);
		super.remove(state);
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    	if(Log.loggingDebug)
    		Log.debug("MountEffect.pulse");
    }
    
    protected int mountType = 0;
    protected int mountSpeedIncrease = 7;
    protected String model = "";
    protected String statName = "";
    protected float statChange = 0;
    
    public void setMountType(int mountType) {
    	this.mountType = mountType;
    }
    public int getMountType() {
    	return mountType;
    }
    public void setMountSpeedIncrease(int mountSpeedIncrease) {
    	this.mountSpeedIncrease = mountSpeedIncrease;
    }
    public int getMountSpeedIncrease() {
    	return mountSpeedIncrease;
    }
    
    public void setModel(String model) {
    	this.model = model;
    }
    public String getModel() {
    	return model;
    }
    
    public void setStatName(String statName) {
    	this.statName = statName;
    }
    public String getStatName() {
    	return statName;
    }
    
    public void setStatChange(float statChange) {
    	this.statChange = statChange;
    }
    public float getStatChange() {
    	return statChange;
    }
    
    public void setDisplayName(String eName) {
    	displayName = eName;
    }
    public String getDisplayName() {
	return displayName;
    }
    protected String displayName = "";
    
    public void setEffectType(int type) {
    	effectType = type;
    }
    public int GetEffectType() {
        return effectType;
    }
    public int effectType = 0;
    
    public static final String MOUNT_PROP = "mount";
    
    private static final long serialVersionUID = 1L;
    
}