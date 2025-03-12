package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.util.EventMessageHelper;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.server.math.Point;
import atavism.server.util.*;

/**
 * Effect child class that sets a property on the target. 
 * The property will revert back to its current setting when the effect has finished.
 * @author Andrew Harrison
 *
 */
public class SetRespawnLocationEffect extends AgisEffect {
    public SetRespawnLocationEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("SetRespawnLocationEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("SetRespawnLocationEffect: this effect is not for buildings");
			return;
		}	
    	String abilityEvent = EventMessageHelper.COMBAT_BUFF_GAINED;
    	
    	CombatInfo target = state.getTarget();
    	
    	target.setRespawnPosition(location);
    	target.setRespawnInstance(instanceID);
    	
    	Log.debug("SetRespawnLocationEffect: set respawn location to " + location + " to true");
    	
    	EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), -1, -1);
    }

    // remove the effect from the object
    public void remove(EffectState state) {
		// Do nothing - just call parent
		super.remove(state);
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    }
    
    public Point getRespawnLocation() { return location; }
    public void setRespawnLocation(Point loc) { location = loc; }
    protected Point location = null;
    
    public int getInstance() { return instanceID; }
    public void setInstance(int instanceID) { this.instanceID = instanceID; }
    protected int instanceID = -1;
    
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