package atavism.agis.abilities;

import atavism.agis.core.*;

/**
 * Ability child class used to teleport the caster somewhere. 
 * Do not use at the moment, use the FriendlyEffectAbility instead.
 * @author Andrew Harrison
 *
 */
@Deprecated
public class TeleportAbility extends AgisAbility {
	
	protected AgisEffect activationEffect;
	protected AgisEffect channelEffect;
	protected AgisEffect activeEffect;
	
    public TeleportAbility(String name) {
        // TODO: Give these a proper default. Null is kind of unsafe
        this(name, null, null, null);
    }
    
    public TeleportAbility(String name, AgisEffect activationEffect, AgisEffect channelEffect, AgisEffect activeEffect) {
    	super(name);
        this.activationEffect = activationEffect;
        this.channelEffect = channelEffect;
        this.activeEffect = activeEffect;
    }

    public AgisEffect getActivationEffect() { return activationEffect; }
    public void setActivationEffect(AgisEffect effect) { this.activationEffect = effect; }

    public AgisEffect getChannelEffect() { return channelEffect; }
    public void setChannelEffect(AgisEffect effect) { this.channelEffect = effect; }

    public AgisEffect getActiveEffect() { return activeEffect; }
    public void setActiveEffect(AgisEffect effect) { this.activeEffect = effect; }

    public void completeActivation(AgisAbilityState state) {
        super.completeActivation(state);
        AgisEffect.applyEffect(activationEffect, state.getSource(), state.getTarget(), getID());
    }
}