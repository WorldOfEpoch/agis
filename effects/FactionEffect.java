package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.plugins.CombatClient;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.core.*;
import atavism.server.engine.*;
import atavism.server.util.*;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that sets the level of reputation for a faction for the player/mob.
 * @author Andrew
 *
 */
public class FactionEffect extends AgisEffect {
	
    public FactionEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }
    
    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("FactionEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("FactionEffect: this effect is not for buildings");
			return;
		}	
		Log.debug("FACTIONEFFECT: applying faction alteration for faction: " + faction);
    	//CombatInfo caster = state.getCaster();
    	CombatInfo target = state.getTarget();
    	
    	if (repValue == -1 || faction == -1) {
    		Log.debug("FACTIONEFFECT: rep value or name has not been set. Effect name: " + effectName);
    		return;
    	}
    	
    	// Get the Player Faction Data for this faction
    	HashMap<Integer, PlayerFactionData> pfdMap = (HashMap) EnginePlugin.getObjectProperty(target.getOid(), Namespace.FACTION, "factionData");
		PlayerFactionData pfd = pfdMap.get(faction);
		
    	// First we need to check for the rep default. If it is empty, then we need to get the current property
    	// value and set it as the default. However this gets trickier... if the player already has a property effect
    	// of the same property type, we need to get that property default, as the current property value will be 
    	// set to whatever the other effect set it to
    	if (repDefault == -1) {
    		for (AgisEffect.EffectState eState: getTargetEffectsOfMatchingType(target)) {
    			AgisEffect e = eState.getEffect();
    			FactionEffect pEffect = (FactionEffect) e;
    			int pFaction = pEffect.getFaction();
    			if (pFaction == faction)
    				repDefault = pEffect.getRepDefault();
    		}
    		// If the propertyDefault is still empty we will now get it from the current property value
    		if (repDefault == -1)
    			repDefault = pfd.getReputation();
    	}
    	
    	// Now we need to check priorities and if there is a Property Effect with the same propertyName then
    	// we need to do a priority comparison.
    	boolean applyProperty = true;
		for (AgisEffect.EffectState eState: getTargetEffectsOfMatchingType(target)) {
			AgisEffect e = eState.getEffect();
			FactionEffect pEffect = (FactionEffect) e;
			int pFaction = pEffect.getFaction();
			if (pFaction == faction)
				if (pEffect.getPriority() > priority)
					applyProperty = false;
		}
		if (applyProperty == true) {
			pfd.updateReputation(repValue);
			pfdMap.put(faction, pfd);
			EnginePlugin.setObjectPropertyNoResponse(target.getOid(), Namespace.FACTION, "factionData", pfdMap);
		}
    	
    	CombatClient.FactionUpdateMessage fMsg = new CombatClient.FactionUpdateMessage(target.getOid());
    	Engine.getAgent().sendBroadcast(fMsg);
    	
    	String abilityEvent = EventMessageHelper.COMBAT_REPUTATION_CHANGED;
    	EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), faction, repValue);
    	
    	Log.debug("FACTIONEFFECT: applied faction alteration for faction: " + faction);
    }

    // remove the effect from the object
    public void remove(EffectState state) {
    	Log.debug("FACTIONEFFECT: removing faction alteration for faction: " + faction);
		CombatInfo target = state.getTarget();
		// We need to go through each effect the player has on them and see if they have a PropertyEffect with
		// the same property name. If they do, we need to check priorities and get the one with the highest priority.
		int value = repDefault;
		int highestPriority = 0;
		for (AgisEffect.EffectState eState: getTargetEffectsOfMatchingType(target)) {
			AgisEffect e = eState.getEffect();
			FactionEffect pEffect = (FactionEffect) e;
			int pFaction = pEffect.getFaction();
			if (pFaction == faction)
				if (pEffect.getPriority() > highestPriority)
					value = pEffect.getRepValue();
		}
		
		HashMap<Integer, PlayerFactionData> pfdMap = (HashMap) EnginePlugin.getObjectProperty(target.getOid(), Namespace.FACTION, "factionData");
		PlayerFactionData pfd = pfdMap.get(faction);
		pfd.updateReputation(value);
		pfdMap.put(faction, pfd);
		EnginePlugin.setObjectPropertyNoResponse(target.getOid(), Namespace.FACTION, "factionData", pfdMap);
		
		CombatClient.FactionUpdateMessage fMsg = new CombatClient.FactionUpdateMessage(target.getOid());
    	Engine.getAgent().sendBroadcast(fMsg);
    	
    	String abilityEvent = EventMessageHelper.COMBAT_REPUTATION_CHANGED;
    	EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), faction, -repValue);

		super.remove(state);
		Log.debug("FACTIONEFFECT: removed faction alteration for faction: " + faction);
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    }
    
    protected int faction = -1;
    protected int repValue = -1;
    protected int repDefault = -1;
    protected int priority = 0;
    public void setFaction(int factionID) { faction = factionID;}
    public int getFaction() {return faction;}
    public void setRepValue(int value) {repValue = value;}
    public int getRepValue() {return repValue;}
    public void setRepDefault(int defaultValue) {repDefault = defaultValue;}
    public int getRepDefault() {return repDefault;}
    public void setPriority(int priority) {this.priority = priority;}
    public int getPriority() {return priority;}
    
    // Effect Value that needs to be removed upon effect removal
    public void setEffectVal(int effect) {
    	effectVal = effect;
    }
    public int GetEffectVal() {
        return effectVal;
    }
    public int effectVal = 0;
    
    public void setEffectName(String eName) {
    	effectName = eName;
    }
    public String getEffectName() {
    	return effectName;
    }
    protected String effectName = "";
    
    public void setEffectType(int type) {
    	effectType = type;
    }
    public int GetEffectType() {
        return effectType;
    }
    public int effectType = 0;
    
    private static final long serialVersionUID = 1L;
}