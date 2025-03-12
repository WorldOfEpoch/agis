package atavism.agis.abilities;

import atavism.server.util.*;
import atavism.agis.core.*;
import atavism.agis.plugins.CombatPlugin;
import java.util.*;

/**
 * Old Ability class that is no longer used. Replaced by CombatMeleeAbility etc.
 * Do not use.
 * @author
 *
 */
@Deprecated
public class CombatAbility extends AgisAbility {
    public CombatAbility(String name) {
        super(name);
    }

    public HashMap resolveHit(AgisAbilityState state) {
	return new HashMap();
    }

    public AgisEffect getActivationEffect() { return activationEffect; }
    public void setActivationEffect(AgisEffect effect) { this.activationEffect = effect; }
    protected AgisEffect activationEffect = null;

    public void completeActivation(AgisAbilityState state) {
        super.completeActivation(state);

        //Add attacker to target's list of attackers
        CombatPlugin.addAttacker(state.getTargetOid(), state.getSourceOid());
        state.getSource().setCombatState(true);        
        
        HashMap params = resolveHit(state);
        Log.debug("CombatAbility.completeActivation: params=" + params);
        AgisEffect.applyEffect(activationEffect, state.getSource(), state.getTarget(), getID(), params);
    }
}