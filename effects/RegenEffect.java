package atavism.agis.effects;

import java.util.Random;

import atavism.agis.core.AgisEffect;
import atavism.agis.objects.CombatInfo;
import atavism.agis.plugins.CombatPlugin;

/**
 * Effect child class that consistently restores a value stat to the target (e.g. Health or Mana).
 * Currently not used due to buggy behavior with getting regeneration working.
 * @author Andrew Harrison
 *
 */
public class RegenEffect extends AgisEffect{
	static Random random = new Random();

    public RegenEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    }

    // Regenerates 1% of health or mana per pulse
    public void pulse(EffectState state)  {
    	super.pulse(state);
        //int heal = minPulseHeal;

        CombatInfo target = state.getTarget();
        int regenAmount = 0;
        
    //    if (getHealProperty().equals(CombatPlugin.HEALTH_STAT)) {
      //  	regenAmount = target.statGetCurrentValue(CombatPlugin.HEALTH_MAX_STAT);
     //   } else {
        	regenAmount = target.statGetCurrentValue(getHealProperty());
      //  }
        
        regenAmount = (int)Math.ceil(regenAmount / 100.0);
        
	    if (regenAmount == 0) {
	    	regenAmount = 1;
	    }
        
	    target.statModifyBaseValue(getHealProperty(), regenAmount);
	    target.sendStatusUpdate();
    }

    public int getMinInstantHeal() { return minHeal; }
    public void setMinInstantHeal(int hps) { minHeal = hps; }
    protected int minHeal = 0;

    public int getMaxInstantHeal() { return maxHeal; }
    public void setMaxInstantHeal(int hps) { maxHeal = hps; }
    protected int maxHeal = 0;

    public int getMinPulseHeal() { return minPulseHeal; }
    public void setMinPulseHeal(int hps) { minPulseHeal = hps; }
    protected int minPulseHeal = 0;

    public int getMaxPulseHeal() { return maxPulseHeal; }
    public void setMaxPulseHeal(int hps) { maxPulseHeal = hps; }
    protected int maxPulseHeal = 0;

    public String getHealProperty() { return healProperty; }
    public void setHealProperty(String property) { healProperty = property; }
    protected String healProperty = CombatPlugin.HEALTH_STAT;
    
    private static final long serialVersionUID = 1L;
}
