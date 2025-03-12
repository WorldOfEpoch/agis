package atavism.agis.abilities;

import atavism.agis.core.*;
import atavism.agis.plugins.ArenaClient;
import atavism.server.engine.*;

/**
 * Not currently Used
 * @author Andrew
 *
 */
@Deprecated
public class CaptureBattleNodeAbility extends AgisAbility {
	
    public CaptureBattleNodeAbility(String name) {
        // TODO: Give these a proper default. Null is kind of unsafe
        super(name);
    }

    public void completeActivation(AgisAbilityState state) {
        super.completeActivation(state);
        OID casterOid = state.getSource().getOwnerOid();
        ArenaClient.resourceNodeAssaulted(casterOid, state.getTarget().getOwnerOid());
    }
    
}