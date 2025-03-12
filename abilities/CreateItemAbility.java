package atavism.agis.abilities;

import atavism.server.plugins.*;
import atavism.server.util.Log;
import atavism.server.objects.*;
import atavism.agis.core.*;
import atavism.server.engine.*;

/**
 * An ability child class that creates an item and gives it to the caster when activation is complete.
 * Not currently used.
 * 
 * @author 
 *
 */
@Deprecated
public class CreateItemAbility extends AgisAbility {
    public CreateItemAbility(String name) {
        super(name);
    }
    
    protected int item = -1;
    public int getItem() { return this.item; }
    public void setItem(int template) { this.item = template; }
    
    public void completeActivation(AgisAbilityState state) {
        super.completeActivation(state);
        OID playerOid = state.getSource().getOwnerOid();
        OID bagOid = playerOid;
        Log.debug("loot CreateItemAbility completeActivation");
        
        // Normally the persistence flag is inherited from the enclosing
        // object, but all we have are OIDs.  Assume this is only used
        // for players and players are always persistent.
        Template overrideTemplate = new Template();
        overrideTemplate.put(Namespace.OBJECT_MANAGER,
                ObjectManagerClient.TEMPL_PERSISTENT, true);

        OID itemOid = ObjectManagerClient.generateObject(item, ObjectManagerPlugin.ITEM_TEMPLATE, overrideTemplate);
        InventoryClient.addItem(bagOid, playerOid, bagOid, itemOid);
    }
}
