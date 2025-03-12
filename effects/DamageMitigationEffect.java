package atavism.agis.effects;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.agis.objects.CombatInfo;
import atavism.agis.util.EventMessageHelper;
import atavism.server.util.Log;

/**
 * Effect child class that provides some form of reduction of damage taken. This could be a certain 
 * amount of damaged absorbed, a percent of damage blocked, or even reflected.
 * @author Andrew
 *
 */
@Deprecated
public class DamageMitigationEffect extends AgisEffect {
    public DamageMitigationEffect(int id, String name) {
    	super(id, name);
    	isPeriodic(false);
    	isPersistent(true);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Map<String, Serializable> params = state.getParams();
        effectSkillType = (int)params.get("skillType");
        CombatInfo target = state.getTarget();
        String abilityEvent = EventMessageHelper.COMBAT_BUFF_GAINED;
        
        attacksToMitigate = attacksMitigated;
        amountToMitigate = amountMitigated;
        
        EventMessageHelper.SendCombatEvent(state.getSourceOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), 
        		attacksToMitigate, amountToMitigate);
    }

    // remove the effect from the object
    public void remove(EffectState state) {
        
	    super.remove(state);
    }

    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    }
    
    public int mitigateDamage(int damageAmount) {
    	Log.debug("Mitigating damage " + damageAmount + " by " + amountMitigated);
    	damageAmount -= amountMitigated;
    	if (damageAmount < 0)
    		damageAmount = 0;
    	attacksToMitigate--;
    	return damageAmount;
    }
    
    /**
     * Checks to see if the amount of damage mitigated, or the number of hits mitigated has reached
     * its limit.
     * @return
     */
    public boolean isEffectCompleted() {
    	if (attacksToMitigate == 0 && attacksMitigated != -1)
    		return true;
    	return false;
    }
    
    // Effect Value that needs to be removed upon effect removal
    public void setEffectVal(int effect) {
    	effectVal = effect;
    }
    public int GetEffectVal() {
        return effectVal;
    }
    public int effectVal = 0;
    
    public void setEffectType(int type) {
    	effectType = type;
    }
    public int GetEffectType() {
        return effectType;
    }
    // The effect Type indicates whether this is a positive (buff) or negative (debuff) effect
    public int effectType = 0;
    
    public void setEffectSkillType(int type) {
    	effectSkillType = type;
    }
    public int GetEffectSkillType() {
        return effectSkillType;
    }
    public int effectSkillType = 0;
    
    public void setAmountMitigated(int num) {
    	amountMitigated = num;
    }
    public int GetAmountMitigated() {
        return amountMitigated;
    }
    public int amountMitigated = 1;
    
    public void setAttacksMitigated(int num) {
    	attacksMitigated = num;
    }
    public int GetAttacksMitigated() {
        return attacksMitigated;
    }
    public int attacksMitigated = 1;

    protected int attacksToMitigate = -1;
    protected int amountToMitigate = -1;
    
    public enum DamageMitigationType {
    	BLOCK,
    	ABSORB,
    	REFLECT
    }
    
    private static final long serialVersionUID = 1L;
}