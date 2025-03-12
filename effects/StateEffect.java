package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.plugins.CombatClient;
import atavism.agis.util.EventMessageHelper;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.server.engine.*;
import atavism.server.util.*;

/**
 * Effect child class that sets a property on the target. 
 * The property will revert back to its current setting when the effect has finished.
 * @author Andrew Harrison
 *
 */
public class StateEffect extends AgisEffect {
    public StateEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("StateEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("StateEffect: this effect is not for buildings");
			return;
		}
    	String abilityEvent = EventMessageHelper.COMBAT_BUFF_GAINED;
    	
		CombatInfo target = state.getTarget();

		boolean ignoreDead = false;
    	if (isPassive() && params.containsKey("ignoreDead"))
			ignoreDead = (boolean) params.get("ignoreDead");
		
		if (target != null) {
			if (target.dead() && !ignoreDead) {
				Log.debug("StateEffect: target is dead");
				state.wasApplied(false);
				return;
			}
		}
    	if (propertyName.equals("")) {
    		Log.debug("STATEEFFECT: property value or name has not been set. Effect name: " + 
    				this.getName());
    		return;
    	}
    	
    	// Now we need to check priorities and if there is a Property Effect with the same propertyName then
    	// we need to do a priority comparison.
    	boolean applyProperty = true;
		for (AgisEffect.EffectState eState: getTargetEffectsOfMatchingType(target)) {
			AgisEffect e = eState.getEffect();
			StateEffect pEffect = (StateEffect) e;
			String pName = pEffect.getPropertyName();
			if (pName.equals(propertyName))
				if (pEffect.getPriority() > priority)
					applyProperty = false;
		}
		if (applyProperty == true)
			EnginePlugin.setObjectPropertyNoResponse(target.getOid(), CombatClient.NAMESPACE, propertyName, true);
    	
    	Log.debug("STATEEFFECT: set property " + propertyName + " to true");
    	
    	EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), -1, -1);
    }

    // remove the effect from the object
    public void remove(EffectState state) {
		CombatInfo target = state.getTarget();
		// We need to go through each effect the player has on them and see if they have a PropertyEffect with
		// the same property name. If they do, we need to check priorities and get the one with the highest priority.
		for (AgisEffect.EffectState eState: getTargetEffectsOfMatchingType(target)) {
			AgisEffect e = eState.getEffect();
			StateEffect pEffect = (StateEffect) e;
			String pName = pEffect.getPropertyName();
			if (pName.equals(propertyName) && !eState.equals(state)) {
				Log.debug("STATEEFFECT: not setting property " + propertyName + " back to false as another effect was found: " + eState.getEffectName());
				// Don't set property to false if other effects are still active
				return;
			}
		}
		EnginePlugin.setObjectPropertyNoResponse(target.getOid(), CombatClient.NAMESPACE, propertyName, false);
		Log.debug("STATEEFFECT: set property " + propertyName + " back to false");
		super.remove(state);
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    }
    
    protected String propertyName = "";
    protected int priority = 0;
    public void setPropertyName(String name) {
    	propertyName = name;
    }
    public String getPropertyName() {
    	return propertyName;
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