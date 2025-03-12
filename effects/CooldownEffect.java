package atavism.agis.effects;

import atavism.agis.objects.*;
import atavism.agis.core.*;
import atavism.agis.util.*;
import atavism.server.util.Log;

import java.io.Serializable;
import java.util.*;

/**
 * Effect child class the alters the duration of a cooldown.
 * This effect should only be used for the actual HIT from using a melee ability.
 * It should never have a duration, persistence or periodic values.
 */
@Deprecated
public class CooldownEffect extends AgisEffect {
    static Random random = new Random();

    public CooldownEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
    	super.apply(state);
    	Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("CooldownEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("CooldownEffect: this effect is not for buildings");
			return;
		}
		int result = (int)params.get("result");
        effectSkillType = (int)params.get("skillType");
        // if (!result.equals("normal"))
            //WorldManagerClient.sendObjChatMsg(state.getCaster().getOwnerOid(), 1,
		    //"Damage result is: " + result);
        
        CombatInfo target = state.getTarget();
        CombatInfo source = state.getSource();
        String abilityEvent = EventMessageHelper.COMBAT_COOLDOWN_EXTENDED;
        
        switch (result) {
        	case 3:
        		abilityEvent = EventMessageHelper.COMBAT_MISSED;
            break;
        	case 4:	
        		abilityEvent = EventMessageHelper.COMBAT_PARRIED;
            break;
        	case 5:
        		abilityEvent = EventMessageHelper.COMBAT_DODGED;
            break;
            default:
	        	Map<String, Cooldown.State> cooldowns = target.getCooldownMap();
                Set<Map.Entry<String, Cooldown.State>> cooldownSet = cooldowns.entrySet();
                Iterator<Map.Entry<String, Cooldown.State>> iter = cooldownSet.iterator();
                while (iter.hasNext()) {
                	Map.Entry<String, Cooldown.State> e = iter.next();
                	String cooldownName = e.getKey();
                	if (cooldownsToAlter.contains(cooldownName) || ((cooldownsToAlter.contains("ALL") && !cooldownName.equals("GLOBAL")))) {
                		Cooldown.State cState = e.getValue();
                		cState.timeAdjustment(cooldownOffset);
                	}
                }
        }

        EventMessageHelper.SendCombatEvent(source.getOwnerOid(), target.getOwnerOid(), abilityEvent, -1, getID(), -1, -1);
    }
    
    public void setEffectSkillType(int type) {
    	effectSkillType = type;
    }
    public int GetEffectSkillType() {
        return effectSkillType;
    }
    public int effectSkillType = 0;
    
    public void addCooldownToAlter(String cooldown) {
    	cooldownsToAlter.add(cooldown);
    }
    public void setCooldownsToAlter(ArrayList<String> cooldowns) {
    	cooldownsToAlter = cooldowns;
    }
    public ArrayList<String> getCooldownsToAlter() {
    	return cooldownsToAlter;
    }
    public ArrayList<String> cooldownsToAlter = new ArrayList<String>();
    
    public void setCooldownOffset(Long offset) {
    	cooldownOffset = offset;
    }
    public Long GetCooldownOffset() {
        return cooldownOffset;
    }
    public Long cooldownOffset = 0l; // A value of -1 means reset it
    
    private static final long serialVersionUID = 1L;
}
