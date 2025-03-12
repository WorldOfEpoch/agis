package atavism.agis.effects;

import atavism.agis.plugins.BonusClient;
import atavism.agis.core.*;
import atavism.server.util.*;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that alters the value of a stat on the target for a period of time.
 * Can be permanent if the effect is a passive one.
 * @author Andrew Harrison
 *
 */
public class VipEffect extends AgisEffect {
    public VipEffect(int id, String name) {
    	super(id, name);
    	
    }
    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("VipEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("VipEffect: this effect is not for buildings");
			return;
		}
		BonusClient.extendVip(state.getSourceOid(), points, time);
    }
    
    public long getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    protected int points = 0;
    
    public long getTime() { return time; }
    public void setTime(int time) { this.time = time; }
    protected int time = 0;
    
    private static final long serialVersionUID = 1L;
}