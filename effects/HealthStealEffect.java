package atavism.agis.effects;

import atavism.server.engine.*;
import atavism.server.util.*;
import atavism.agis.objects.*;
import atavism.agis.core.*;
import atavism.agis.plugins.*;
import atavism.agis.util.*;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class that takes health from the target and gives it to the caster. Happens instantly.
 * @author Andrew
 *
 */
public class HealthStealEffect extends AgisEffect {

	static Random random = new Random();

    public HealthStealEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("HealthStealEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("HealthStealEffect: this effect is not for buildings");
			return;
		}	
		int result = (int)params.get("result");
      //  effectSkillType = params.get("skillType");
        hitRoll =(int) params.get("hitRoll");
        
        String abilityEvent = EventMessageHelper.COMBAT_DAMAGE;
        
        CombatInfo target = state.getTarget();
        CombatInfo caster = state.getSource();

        int dmg = 0;
        
        switch (result) {
        	case 3:
        		abilityEvent = EventMessageHelper.COMBAT_MISSED;
            break;
        	case 4:	
        		abilityEvent = EventMessageHelper.COMBAT_PARRIED;
            break;
        	case 5:
        		abilityEvent = EventMessageHelper.COMBAT_BLOCKED;
            break;
            default:
            	dmg = minDmg;
	        	// Now lets take the players stats into account
	        	dmg = CombatHelper.CalcDamage(target, caster, dmg, damageType, skillEffectMod.get(0), effectSkillType, hitRoll, true,params);
	        	// Take in the effect base damage modifiers
	        	if (DamageMod != 1.0) {
	        		Log.debug("MELEESTRIKE: DamageMod: " + DamageMod + " Damage: " + dmg);
	        	    float dmgF = (float)dmg * DamageMod;
	        	    Log.debug("MELEESTRIKE: DamageFloat: " + dmgF);
	        	    dmg = Math.round(dmgF);
	        	    Log.debug("MELEESTRIKE: Damage: " + dmg);
	        	}
	        	
	        	// If this ability uses any effects to boost up dmg (and consumes the effect 
	        	// at the same time
			if (bonusDmgEffectVals != null && bonusDmgEffectVals.size() > 0) {
				Log.debug("EFFECT: effect has bonusDmgEffectVal; effects required: " + bonusDmgEffectVals.toString());
				for (int i = 0; i < bonusDmgEffectVals.size(); i++) {
	        			boolean effectPresent = false;
		    		    for (EffectState existingState : caster.getCurrentEffects()) {
		    			    if (bonusDmgEffectVals.get(i) == existingState.getEffect().getID()) {
		    				    effectPresent = true;
		    		    	}
		    		    }
	    		    	if (effectPresent) {
	    		    		AgisEffect.removeEffectByID(target, bonusEffectReq);
				    	
	    		    		dmg = dmg + bonusDmgVals.get(i);
	    		    		Log.debug("EFFECT: removed effect position: " + effectPresent + 
	    		    				"; and boosted dmg by: " + bonusDmgVals.get(i));
	    		    	}
	        	    }
	        	}
	        	
	        	switch (result) {
			        // Finally lets check for critical or blocks
			        case 6:
			        	dmg = dmg / 2;
			        	abilityEvent = EventMessageHelper.COMBAT_BLOCKED;
			        	break;
			        case 2:
		        		dmg = dmg * 2;
		        		abilityEvent = EventMessageHelper.COMBAT_DAMAGE_CRITICAL;
		        		break;
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
        		Log.debug("DUEL: steal dmg:"+dmg+"| target health "+targetHealth+" dmg mod to:"+ (targetHealth - 1));
        		dmg = targetHealth - 1;
        		ArenaClient.duelDefeat(target.getOwnerOid());
        	}
        }
        // Hopefully this will make the ai respond even if the attack was a miss etc.
        int targetHealth = target.statGetCurrentValue(getDamageProperty());
        target.statModifyBaseValue(getDamageProperty(), -dmg);
        target.sendStatusUpdate();
        
        EventMessageHelper.SendCombatEvent(caster.getOwnerOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), dmg, -1,"",damageType);
    	int threat = dmg;
		if(threat == 0)
			threat=1;
		Engine.getAgent().sendBroadcast(new CombatClient.DamageMessage(target.getOwnerOid(), state.getSource().getOwnerOid(), dmg, damageType,threat));

        if (dmg > 0) {
	     //   Engine.getAgent().sendBroadcast(new CombatClient.DamageMessage(target.getOwnerOid(), state.getSource().getOwnerOid(), dmg, damageType,dmg));
	        
	        if (dmg > targetHealth)
	        	dmg = targetHealth;
	        double healD = dmg * transferModifier;
	        int heal = (int) healD;
	        caster.statModifyBaseValue(getHealProperty(), heal);
	        caster.sendStatusUpdate();
	        
	        abilityEvent = EventMessageHelper.COMBAT_HEAL;
	        EventMessageHelper.SendCombatEvent(caster.getOwnerOid(), target.getOwnerOid(), abilityEvent, state.getAbilityID(), getID(), heal, -1,getHealProperty(),"");
        }
        
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
    
    public String getHealProperty() { return healProperty; }
    public void setHealProperty(String property) { healProperty = property; }
    protected String healProperty = CombatPlugin.HEALTH_STAT;
    
    public double getTransferModifier() { return transferModifier; }
    public void setTransferModifier(double modifier) { transferModifier = modifier; }
    protected double transferModifier = 1.0;
    
    private static final long serialVersionUID = 1L;
}
