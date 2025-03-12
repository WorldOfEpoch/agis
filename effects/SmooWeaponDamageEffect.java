package atavism.agis.effects;

import atavism.server.engine.*;
import atavism.server.util.*;
import atavism.agis.objects.*;
import atavism.agis.core.*;
import atavism.agis.plugins.*;
import atavism.agis.util.*;

import java.io.Serializable;
import java.util.*;

/*
 * This effect should only be used for the actual HIT from using a melee ability.
 * It should never have a duration, persistence or periodic values.
 */
@Deprecated
public class SmooWeaponDamageEffect extends AgisEffect {
    static Random random = new Random();

    public SmooWeaponDamageEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
	    Map<String, Serializable> params = state.getParams();
	    Log.debug("RESULT: effect params is: " + params);
        int result = (int)params.get("result");
        
        CombatInfo target = state.getTarget();
        CombatInfo caster = state.getSource();
        int dmgtype = 1;
        int dmg = 0;
        String damageType = "";
        
        switch (result) {
        	case 3:
        		dmg = 0;
        		damageType = "(Miss)";
            	dmgtype = 3;
            break;
        	case 4:	
        		dmg = 0;
        		damageType = "(Parry)";
            	dmgtype = 4;
            break;
        	case 5:
        		dmg = 0;
        		damageType = "(Dodge)";
            	dmgtype = 5;
            break;
            default:
            	dmg = minDmg;
            	// Get level difference
            	int levelAlteration = 0;
            	// Randomise number
            	Random random = new Random();
            	int value = random.nextInt(100);
            	
            	// Determine damage amount
            	if (value > (70 + levelAlteration)) {
            		dmg = dmg * 1;
            		damageType = "(Strong)";
            	} else if (value > (30 + levelAlteration)) {
            		dmg = dmg / 2;
            		damageType = "";
            	} else {
            		dmg = dmg / 4;
            		damageType = "(Weak)";
            	}
            	int mitigatedDamage = checkDamageMitigation(state, dmg);
            	if (mitigatedDamage < dmg) {
            		damageType = "(" + (dmg - mitigatedDamage) + " Blocked)";
            		dmg = mitigatedDamage;
            	}
        	
	        	if (Log.loggingDebug)
	                Log.debug("DamageEffect.apply: doing instant damage to obj=" + state.getTarget() +
	    		      " from=" + state.getSource());
        }
                
        // Check if the attack was duel based
        int duelID = getDuelEffect();
        if (duelID != -1 && getDamageProperty().equals(CombatPlugin.HEALTH_STAT)) {
        	// This effect is a duel effect, make sure it doesn't kill the target
        	int targetHealth = target.statGetCurrentValue(CombatPlugin.HEALTH_STAT);
        	if (dmg >= targetHealth) {
        		// Lower the damage so it doesn't kill the target, and send a defeat/removal message
        		Log.debug("DUEL:  dmg:"+dmg+"| target health"+targetHealth +" dmg mod to:" + (targetHealth - 1) );
        		dmg = targetHealth - 1;
        		ArenaClient.duelDefeat(target.getOwnerOid());
        	}
        }
        // Hopefully this will make the ai respond even if the attack was a miss etc.
        target.statModifyBaseValue(getDamageProperty(), -dmg);
        target.statSendUpdate(true);
        
        //if (dmg > 0)
	    Engine.getAgent().sendBroadcast(new CombatClient.DamageMessage(target.getOwnerOid(),
	    		caster.getOwnerOid(), dmg, damageType,dmg));
        
        ExtendedCombatMessages.sendCombatText(target.getOwnerOid(), "" + dmg + " " + damageType, dmgtype);
        ExtendedCombatMessages.sendCombatText2(caster.getOwnerOid(), target.getOwnerOid(), "" + dmg + " " + damageType, dmgtype);
	
        //Engine.getAgent().sendBroadcast(new CombatClient.DamageMessage(obj.getOwnerOid(),
        //        state.getCaster().getOwnerOid(), dmg, damageType));
    }

    public int getMinInstantDamage() { return minDmg; }
    public void setMinInstantDamage(int hps) { minDmg = hps; }
    protected int minDmg = 0;

    public int getMaxInstantDamage() { return maxDmg; }
    public void setMaxInstantDamage(int hps) { maxDmg = hps; }
    protected int maxDmg = 0;

    public int getMinPulseDamage() { return minPulseDmg; }
    public void setMinPulseDamage(int hps) { minPulseDmg = hps; }
    protected int minPulseDmg = 0;

    public int getMaxPulseDamage() { return maxPulseDmg; }
    public void setMaxPulseDamage(int hps) { maxPulseDmg = hps; }
    protected int maxPulseDmg = 0;

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
    
    public void setHitRoll(int roll) {
    	hitRoll = roll;
    }
    public int GetHitRoll() {
        return hitRoll;
    }
    public int hitRoll = 0;
}
