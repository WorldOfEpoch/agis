package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.core.*;
import atavism.server.engine.*;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.*;

import java.io.*;

/**
 * Effect child class that sets a property on the target. 
 * The property will revert back to its current setting when the effect has finished.
 * @author Andrew Harrison
 *
 */
@Deprecated
public class PropertyEffect extends AgisEffect {
    public PropertyEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	
    	String abilityEvent = EventMessageHelper.COMBAT_BUFF_GAINED;
    	
    	CombatInfo target = state.getTarget();
    	
    	if (propertyValue == null || propertyName.equals("")) {
    		Log.error("PROPERTYEFFECT: property value or name has not been set. Effect name: " + 
    				this.getName());
    		return;
    	}
    	defaultValue = null;
    	// First we need to check for the property default. If it is empty, then we need to get the current property
    	// value and set it as the default. However this gets trickier... if the player already has a property effect
    	// of the same property type, we need to get that property default, as the current property value will be 
    	// set to whatever the other effect set it to
    	if (propertyDefault == null) {
    		Log.debug("PROPEFFECT: default was null");
    		for (AgisEffect.EffectState eState : getTargetEffectsOfMatchingType(target)) {
    			if (state.equals(eState))
    				continue;
    			String pName = eState.getDefaultName();
    			Serializable stateDefault = eState.getDefaultValue();
    			if (pName.equals(propertyName) && stateDefault != null) {
    				defaultValue = stateDefault;
    				Log.debug("PROPEFFECT: getting default from existing effect: " + defaultValue);
    			}
    		}
    		Log.debug("PROPEFFECT: before null check default is: " + defaultValue);
    		// If the propertyDefault is still empty we will now get it from the current property value
    		if (defaultValue == null) {
    			defaultValue = EnginePlugin.getObjectProperty(target.getOid(), WorldManagerClient.NAMESPACE, propertyName);
    			Log.debug("PROPEFFECT: stored default was: " + defaultValue);
    		}
    	} else {
    		defaultValue = propertyDefault;
    	}
    	
    	// Now we need to check priorities and if there is a Property Effect with the same propertyName then
    	// we need to do a priority comparison.
    	boolean applyProperty = true;
		for (AgisEffect.EffectState eState: getTargetEffectsOfMatchingType(target)) {
			AgisEffect e = eState.getEffect();
			PropertyEffect pEffect = (PropertyEffect) e;
			String pName = pEffect.getPropertyName();
			if (pName.equals(propertyName))
				if (pEffect.getPriority() > priority)
					applyProperty = false;
		}
		if (applyProperty == true)
			EnginePlugin.setObjectPropertyNoResponse(target.getOid(), WorldManagerClient.NAMESPACE, propertyName, propertyValue);
    	
    	Log.debug("PROPERTYEFFECT: applied property " + propertyName + " with value " + propertyValue 
    			+ " and default: " + defaultValue);
    	state.setDefaultName(propertyName);
    	state.setDefaultValue(defaultValue);
    	
    	EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), -1, -1);
    }

    // remove the effect from the object
    public void remove(EffectState state) {
		CombatInfo target = state.getTarget();
		// We need to go through each effect the player has on them and see if they have a PropertyEffect with
		// the same property name. If they do, we need to check priorities and get the one with the highest priority.
		Serializable value = defaultValue;
		int highestPriority = 0;
		for (AgisEffect.EffectState eState: getTargetEffectsOfMatchingType(target)) {
			AgisEffect e = eState.getEffect();
			PropertyEffect pEffect = (PropertyEffect) e;
			String pName = pEffect.getPropertyName();
			if (pName.equals(propertyName))
				if (pEffect.getPriority() > highestPriority)
					value = pEffect.getPropertyValue();
		}
		EnginePlugin.setObjectPropertyNoResponse(target.getOid(), WorldManagerClient.NAMESPACE, propertyName, value);
		Log.debug("PROPERTYEFFECT: set property " + propertyName + " back to default: " + defaultValue);
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    }
    
    protected String propertyName = "";
    protected Serializable propertyValue = null;
    protected String propertyType = "";
    protected Serializable propertyDefault = null;
    protected Serializable defaultValue = null;
    protected int priority = 0;
    public void setPropertyName(String name) {
    	propertyName = name;
    }
    public String getPropertyName() {
    	return propertyName;
    }
    public void setPropertyValue(Serializable value) {
    	propertyValue = value;
    }
    public Serializable getPropertyValue() {
    	return propertyValue;
    }
    public void setPropertyType(String type) {
    	propertyType = type;
    }
    public String getPropertyType() {
    	return propertyType;
    }
    public void setPropertyDefault(Serializable defaultValue) {
    	propertyDefault = defaultValue;
    }
    public Serializable getPropertyDefault() {
    	return propertyDefault;
    }
    public void setPriority(int priority) {
    	this.priority = priority;
    }
    public int getPriority() {
    	return priority;
    }
    
    public Serializable getDefaultValue() {
    	return defaultValue;
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
    
    private static final long serialVersionUID = 1L;
    
}