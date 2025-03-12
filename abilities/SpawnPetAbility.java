package atavism.agis.abilities;

import atavism.agis.core.*;
import atavism.agis.objects.AgisItem;
import atavism.agis.plugins.AgisMobClient;
import atavism.server.engine.BasicWorldNode;
import atavism.server.plugins.WorldManagerClient;

/**
 * Ability child class used to spawn a pet. 
 * Hasn't been tested in a long time.
 * @author Andrew
 *
 */
@Deprecated
public class SpawnPetAbility extends AgisAbility {
	
    public SpawnPetAbility(String name) {
        // TODO: Give these a proper default. Null is kind of unsafe
        super(name);
    }

    public void completeActivation(AgisAbilityState state) {
        super.completeActivation(state);
        AgisItem item = state.getItem();
        String petRef = (String) item.getProperty("petRef");
    	BasicWorldNode wnode = WorldManagerClient.getWorldNode(state.getSource().getOid());
		AgisMobClient.spawnPet(state.getSource().getOid(), wnode.getInstanceOid(), petRef, 4, 0l, -1, skillType);
    }
    
}