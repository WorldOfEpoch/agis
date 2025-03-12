package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.plugins.AgisWorldManagerClient;
import atavism.agis.plugins.AgisWorldManagerPlugin;
import atavism.agis.plugins.CombatClient;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.agis.core.*;
import atavism.server.engine.*;
import atavism.server.objects.DisplayContext.Submesh;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

/**
 * Effect child class that sets a property on the target. 
 * The property will revert back to its current setting when the effect has finished.
 * 
 */
public class PlayerShopEffect extends AgisEffect {
    public PlayerShopEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("PlayerShopEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("PlayerShopEffect: this effect is not for buildings");
			return;
		}	
    	String abilityEvent = EventMessageHelper.COMBAT_BUFF_GAINED;
    	CombatInfo target = state.getTarget();
    	String previousDefaultValue = null;
    	if (removeOtherMorphs) {
    		// First see if the user has another morph 
        	EffectState similarEffect = null;
        	boolean hasThisEffect = false;
        	for (EffectState existingState : target.getCurrentEffects()) {
        		if (existingState.getEffect() instanceof PlayerShopEffect && !existingState.equals(state)) {
        			similarEffect = existingState;
        			if (existingState.getEffectID() == getID()) {
        				hasThisEffect = true;
        			}
        			Log.debug("PlayerShopEffect: found similar effect, it is identical? " + hasThisEffect);
        			break;
        		}
        	}
        	
        	if (hasThisEffect) {
        		// Remove the old one and then abort this one
        		AgisEffect.removeEffect(similarEffect);
        		//AgisEffect.removeEffect(state);
        		state.wasApplied(false);
        		return;
        	} else if (similarEffect != null) {
        		// Remove the old one (easy enough)
        		previousDefaultValue = (String) similarEffect.getDefaultValue();
        		AgisEffect.removeEffect(similarEffect);
        	}
    	}
    	super.apply(state);
    	
    	defaultValue = previousDefaultValue;
    	
    	Log.debug("PlayerShopEffect: default was null");
    	for (AgisEffect.EffectState eState : getTargetEffectsOfMatchingType(target)) {
    		if (state.equals(eState))
    			continue;
    		String pName = eState.getDefaultName();
    		String stateDefault = (String)eState.getDefaultValue();
    		if (pName.equals(propertyName) && stateDefault != null) {
    			defaultValue = stateDefault;
    			Log.debug("PlayerShopEffect: getting default from existing effect: " + defaultValue);
    		}
    	}
    	Log.debug("PlayerShopEffect: before null check default is: " + defaultValue);
    	// If the propertyDefault is still empty we will now get it from the current property value
    	if (defaultValue == null) {
    		defaultValue = (String)EnginePlugin.getObjectProperty(target.getOid(), WorldManagerClient.NAMESPACE, propertyName);
    		Log.debug("PlayerShopEffect: stored default was: " + defaultValue);
    	}
    	
    	// Now we need to check priorities and if there is a Property Effect with the same propertyName then
    	// we need to do a priority comparison.
    	boolean applyProperty = true;
		for (AgisEffect.EffectState eState: getTargetEffectsOfMatchingType(target)) {
			AgisEffect e = eState.getEffect();
			PlayerShopEffect pEffect = (PlayerShopEffect) e;
			if (pEffect.getPriority() > priority)
				applyProperty = false;
		}
		if (applyProperty == true) {
			
			EnginePlugin.setObjectProperty(target.getOid(), WorldManagerClient.NAMESPACE, propertyName, model);
			ArrayList<Submesh> submeshes = new ArrayList<Submesh>();
			WorldManagerClient.modifyDisplayContext(target.getOid(), WorldManagerClient.modifyDisplayContextActionReplace, model, submeshes);
			if (switchToActionBar > -1 && switchToActionBar != target.getCurrentActionBar()) {
				target.setCurrentActionBar(switchToActionBar);
				ExtendedCombatMessages.sendActions(target.getOid(), target.getCurrentActions(), switchToActionBar);
			}
		}
    	// Send interrupt to target
			if ((interruption_chance == 0 && interruption_chance_max > 0) || interruption_chance > 0 || forceInterruption) {
				CombatClient.interruptAbilityMessage interruptMsg = new CombatClient.interruptAbilityMessage(target.getOwnerOid());
				interruptMsg.setForce(forceInterruption);
				float chance = interruption_chance;
				if (interruption_chance < interruption_chance_max) {
					Random rand = new Random();
					float f = rand.nextFloat();
					chance = interruption_chance + (interruption_chance_max - interruption_chance) * f;
				}
				interruptMsg.setChance(chance);
				Engine.getAgent().sendBroadcast(interruptMsg);
			}
    	Log.debug("PlayerShopEffect: applied property " + propertyName + " with value " + model 
    			+ " and default: " + defaultValue);
    	state.setDefaultName(propertyName);
    	state.setDefaultValue(defaultValue);
    	
    	if (morphType == 2) {
    		AgisWorldManagerClient.sendSetMovementStateMessage(target.getOid(), AgisWorldManagerPlugin.MOVEMENT_STATE_FLYING);
    	}
    	
    	EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), -1, -1);
    }

    // remove the effect from the object
    public void remove(EffectState state) {
		CombatInfo target = state.getTarget();
		Log.debug("PlayerShopEffect: removing effect from target: " + target + " with targetOid: " + state.getTargetOid());
		// We need to go through each effect the player has on them and see if they have a PropertyEffect with
		// the same property name. If they do, we need to check priorities and get the one with the highest priority.
		String value = (String) state.getDefaultValue();
		int highestPriority = 0;
		for (AgisEffect.EffectState eState: getTargetEffectsOfMatchingType(target)) {
			AgisEffect e = eState.getEffect();
			PlayerShopEffect pEffect = (PlayerShopEffect) e;
			if (pEffect.getPriority() > highestPriority) {
				value = pEffect.getModel();
				Log.debug("PlayerShopEffect: found existing effect so setting " + propertyName + " to: " + value);
			}
		}
		EnginePlugin.setObjectProperty(state.getTargetOid(), WorldManagerClient.NAMESPACE, propertyName, value);
		ArrayList<Submesh> submeshes = new ArrayList<Submesh>();
		WorldManagerClient.modifyDisplayContext(target.getOid(), WorldManagerClient.modifyDisplayContextActionReplace, value, submeshes);
		if (switchToActionBar > -1) {
			target.setCurrentActionBar(0);
			ExtendedCombatMessages.sendActions(target.getOid(), target.getCurrentActions(), 0);
		}
		Log.debug("PlayerShopEffect: set property " + propertyName + " back to default: " + value);
		
		if (morphType == 2) {
    		AgisWorldManagerClient.sendSetMovementStateMessage(target.getOid(), AgisWorldManagerPlugin.MOVEMENT_STATE_RUNNING);
    	}
		
		super.remove(state);
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    }
    
    protected String propertyName = "model";
    protected String model = null;
    protected String defaultValue = null;
    protected int switchToActionBar = 0;
    protected int defaultActionBar = 0;
    protected int morphType = 1;
    protected boolean removeOtherMorphs = true;
    protected int priority = 0;
    
    public void setModel(String value) {
    	model = value;
    }
    public String getModel() {
    	return model;
    }
    public void setSwitchToActionBar(int switchToActionBar) {
    	this.switchToActionBar = switchToActionBar;
    }
    public int getSwitchToActionBar() {
    	return switchToActionBar;
    }
    public void setMorphType(int morphType) {
    	this.morphType = morphType;
    }
    public int getMorphType() {
    	return morphType;
    }
    public void removeOtherMorphs(boolean removeOtherMorphs) {
    	this.removeOtherMorphs = removeOtherMorphs;
    }
    public boolean removeOtherMorphs() {
    	return removeOtherMorphs;
    }
    public void setPriority(int priority) {
    	this.priority = priority;
    }
    public int getPriority() {
    	return priority;
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