package atavism.agis.effects;

import atavism.server.math.Point;
import atavism.agis.plugins.AgisWorldManagerClient;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.server.util.*;

/**
 * Effect child class that despawns an object. 
 * Can be used to despawn a pet or any other object.
 * @author Andrew Harrison
 *
 */
public class TrapEffect extends AgisEffect {

    public TrapEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		
		Log.debug("TrapEffect:  location?"+params+" "+params.keySet()+" "+params.containsKey("location"));
		
		if(params.containsKey("location")) {
			Point loc = (Point)params.get("location");
			AgisWorldManagerClient.SpawnTrap(state.getSource().getOid(), abilityId, size, time, targetType, activationTime,loc,model);
		} else {
			AgisWorldManagerClient.SpawnTrap(state.getSource().getOid(), abilityId, size, time, targetType, activationTime,null,model);
		}
    }
    
    private String model = "";
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    protected int abilityId = 0;
    public int getAbilityId() { return abilityId; }
    public void setAbilityId(int abilityId) { this.abilityId = abilityId; }
    
    protected int targetType = 0;
    public int getTargetType() { return targetType; }
    public void setTargetType(int targetType) { this.targetType = targetType; }
 
    protected float size = -1f;
    public float getSize() { return size; }
    public void setSize(float size) { this.size = size; }
    
    protected float time = 0f;
    public float getTime() { return time; }
    public void setTime(float time) { this.time = time; }
    
    protected float activationTime = 0;
    public float getActivationTime() { return activationTime; }
    public void setActivationTime(float activationTime) { this.activationTime = activationTime; }
 
    private static final long serialVersionUID = 1L;
}
