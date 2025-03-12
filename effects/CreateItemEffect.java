package atavism.agis.effects;

import atavism.server.engine.*;

import java.io.Serializable;
import java.util.Map;

import atavism.agis.core.*;
import atavism.server.objects.Template;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.util.Log;

/**
 * Effect child class that creates an item and gives it to the caster. 
 * Can create multiple of the same item, but only of one type.
 * @author Andrew Harrison
 *
 */
public class CreateItemEffect extends AgisEffect {

    public CreateItemEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("CreateItemEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("CreateItemEffect: this effect is not for buildings");
			return;
		}
		OID playerOid = state.getTargetOid();
        OID bagOid = playerOid;
        Log.debug("loot CreateItemEffect apply");
        	
		Template overrideTemplate = new Template();
        overrideTemplate.put(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT, true);
   //     Log.error("CreateItemEffect: "+playerOid+" ObjectManagerClient.generateObject "+overrideTemplate);
        OID itemOid = ObjectManagerClient.generateObject(item, ObjectManagerPlugin.ITEM_TEMPLATE, overrideTemplate);
//Log.error("CreateItemEffect:  player "+playerOid+" add item id "+itemOid+" InventoryClient.addItem before");
        InventoryClient.addItem(bagOid, playerOid, bagOid, itemOid);
  //      Log.error("CreateItemEffect: player "+playerOid+" add item id "+itemOid+" InventoryClient.addItem after");
    }

    protected int item = -1;
    public int getItem() { return this.item; }
    public void setItem(int template) { this.item = template; }
    
    protected int numberToCreate = 1;
    public int getNumberToCreate() { return numberToCreate; }
    public void setNumberToCreate(int numberToCreate) { this.numberToCreate = numberToCreate; }
    
    private static final long serialVersionUID = 1L;
}
