package atavism.agis.effects;

import atavism.agis.objects.*;
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
public class BonusEffect extends AgisEffect {
    public BonusEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }

     // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("BonusEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("BonusEffect: this effect is not for buildings");
			return;
		}
		CombatInfo target = state.getTarget();
		
		boolean ignoreDead = false;
    	if (isPassive() && params.containsKey("ignoreDead"))
			ignoreDead = (boolean) params.get("ignoreDead");
    	
		if (target != null) {
			if(target.dead() && !ignoreDead) {
				Log.debug("BonusEffect: target is dead");
				state.wasApplied(false);
				return;
			}
		}
    	//Map<String, Integer> params = state.getParams();
    	Log.debug("BonusEffect: apply start "+getBonuses());
    	BonusClient.sendBonusAdd(state.getTargetOid(), getBonuses(),"BonusEffect"+getID());
    	Log.debug("BonusEffect: apply end");
    }
    
    public void remove(EffectState state) {
    	CombatInfo target = state.getTarget();
    	remove(state, target);
    }

    // remove the effect from the object
    public void remove(EffectState state, CombatInfo target) {
		if (target == null)
			return;
		Log.debug("BonusEffect: remove "+getBonuses());
		BonusClient.sendBonusRemove(state.getTargetOid(), getBonuses(),"BonusEffect"+getID());
	    
		super.remove(state);
		Log.debug("BonusEffect: remove End");
		
    }
    
    public void unload(EffectState state, CombatInfo target) {
    	remove(state, target);
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    }
    
    public void activate(EffectState state) {
    	super.activate(state);
    	Log.debug("BonusEffect: activate");
	}
    
    public void deactivate(EffectState state) {
    	super.deactivate(state);
    	Log.debug("BonusEffect: deactivate");
    }
    
    public void setBonuses(ArrayList<BonusSettings> bonuses) {
    	this.bonuses = bonuses;
    }
    
    public ArrayList<BonusSettings> getBonuses() {
    	return bonuses;
    }
       
    protected ArrayList<BonusSettings> bonuses ;
    
    private static final long serialVersionUID = 1L;
}