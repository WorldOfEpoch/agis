package atavism.agis.effects;

import atavism.server.engine.*;
import atavism.server.util.Log;
import atavism.agis.objects.*;
import atavism.agis.core.*;
import atavism.agis.plugins.*;
import atavism.agis.util.*;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that takes health from the target and gives it to the caster over a period of time.
 * @author Andrew Harrison
 *
 */
public class HealthStealDotEffect extends AgisEffect {
    static Random random = new Random();

    public HealthStealDotEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("HealthStealDotEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("HealthStealDotEffect: this effect is not for buildings");
			return;
		}	
		//   effectSkillType = params.get("skillType");
        hitRoll = (int)params.get("hitRoll");
        
        String abilityEvent = EventMessageHelper.COMBAT_DEBUFF_GAINED;
        
        CombatInfo target = state.getTarget();
        CombatInfo source = state.getSource();
        
        int dmg = minDmg;
        if (maxDmg > minDmg) {
            dmg += random.nextInt(maxDmg - minDmg);
        }
        
        dmg = CombatHelper.CalcDamage(target, source, dmg, damageType, skillEffectMod.get(0), effectSkillType, hitRoll, false,params);
        
        // Divide the total damage by the number of pulses
        pulseDamage = dmg / this.numPulses;
        
        EventMessageHelper.SendCombatEvent(source.getOwnerOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), -1, -1);
    }
    
    // perform the next periodic pulse for this effect on the object
    public void pulse(EffectState state) {
    	super.pulse(state);
    	
    	String abilityEvent = EventMessageHelper.COMBAT_DAMAGE;

    	CombatInfo target = state.getTarget();
        CombatInfo source = state.getSource();
        if (pulseDamage > 0) {
            
        	int targetHealth = target.statGetCurrentValue(getDamageProperty());
        	target.statModifyBaseValue(getDamageProperty(), -pulseDamage);
        	target.sendStatusUpdate();
        	int threat = pulseDamage;
			if (threat == 0)
				threat = 1;
			Engine.getAgent().sendBroadcast(new CombatClient.DamageMessage(target.getOwnerOid(), state.getSource().getOwnerOid(), pulseDamage, damageType, threat));
		  
	         
	        EventMessageHelper.SendCombatEvent(source.getOwnerOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), pulseDamage, -1,"",damageType);
	        
	        if (pulseDamage > targetHealth)
	        	pulseDamage = targetHealth;
	        double healD = pulseDamage * transferModifier;
	        int heal = (int) healD;
	        source.statModifyBaseValue(getHealProperty(), heal);
	        source.sendStatusUpdate();
	        
	        // If there is a pulse coord effect, run it now
	        if (pulseCoordEffect != null && !pulseCoordEffect.isEmpty()) {
	        	CoordinatedEffect cE = new CoordinatedEffect(pulseCoordEffect);
	    	    cE.sendSourceOid(true);
	    	    cE.sendTargetOid(true);
                state.getCoordinatedEffectStates().add(cE.invoke(source.getOwnerOid(), target.getOwnerOid()));
	        }
	        
	        abilityEvent = EventMessageHelper.COMBAT_HEAL;
	        EventMessageHelper.SendCombatEvent(source.getOwnerOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), heal, -1,getHealProperty(),"");
        }
    }

    public int getMinDamage() { return minDmg; }
    public void setMinDamage(int hps) { minDmg = hps; }
    protected int minDmg = 0;

    public int getMaxDamage() { return maxDmg; }
    public void setMaxDamage(int hps) { maxDmg = hps; }
    protected int maxDmg = 0;
    
    protected int pulseDamage = 0;
    
    public String getPulseCoordEffect() { return pulseCoordEffect; }
    public void setPulseCoordEffect(String coordEffect) { pulseCoordEffect = coordEffect; }
    protected String pulseCoordEffect;

    public String getDamageProperty() { return damageProperty; }
    public void setDamageProperty(String property) { damageProperty = property; }
    protected String damageProperty = CombatPlugin.HEALTH_STAT;
    
    public float getDamageMod() { return DamageMod; }
    public void setDamageMod(float hps) { DamageMod = hps; }
    protected float DamageMod = 1.0f;
    
    // List of bonus damage effect values
    public void addBonusDmgEffectVal(int effect) {
    	bonusDmgEffectVals.add(effect);
    }
    public LinkedList<Integer> GetBonusDmgEffectVal() {
        return bonusDmgEffectVals;
    }
    public LinkedList<Integer> bonusDmgEffectVals = new LinkedList<Integer>();
    
    // List of bonus damage effect values
    public void addBonusDmgVal(int val) {
    	bonusDmgVals.add(val);
    }
    public LinkedList<Integer> GetBonusDmgVal() {
        return bonusDmgVals;
    }
    public LinkedList<Integer> bonusDmgVals = new LinkedList<Integer>();
    
    public void setEffectSkillType(int type) {
    	effectSkillType = type;
    }
    public int GetEffectSkillType() {
        return effectSkillType;
    }
    public int effectSkillType = 0;
    
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
    
    public void setHitRoll(int roll) {
    	hitRoll = roll;
    }
    public int GetHitRoll() {
        return hitRoll;
    }
    public int hitRoll = 0;
    
    public String getHealProperty() { return healProperty; }
    public void setHealProperty(String property) { healProperty = property; }
    protected String healProperty = CombatPlugin.HEALTH_STAT;
    
    public double getTransferModifier() { return transferModifier; }
    public void setTransferModifier(double modifier) { transferModifier = modifier; }
    protected double transferModifier = 1.0;
    
    private static final long serialVersionUID = 1L;
}
