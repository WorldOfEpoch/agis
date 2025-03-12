package atavism.agis.effects;

import atavism.agis.core.*;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that is used to show the player/mob can Parry attacks.
 * @author Andrew
 *
 */
public class ParryEffect extends AgisEffect {
    public ParryEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }

    public void setWeapon(List<String> weapons) {
    	this.weapons = weapons;
    }
    public List<String> getWeapon() {
    	return weapons;
    }
    protected List<String> weapons = new ArrayList<String>();

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
	    Map<String, Serializable> params = state.getParams();
        //result = params.get("effect1");
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    }
    
    private static final long serialVersionUID = 1L;
}