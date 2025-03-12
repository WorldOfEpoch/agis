package atavism.agis.effects;

import atavism.agis.core.*;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that is used to store the result of a previous ability. This can then be used for activation
 * requirements of other abilities (i.e. an ability that can be activated after a dodge).
 * @author Andrew Harrison
 *
 */
public class ResultEffect extends AgisEffect {
    public ResultEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }

    public void setResult(int res) {
    	result = res;
    }
    public int getResult() {
    	return result;
    }
    protected int result = 0;

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