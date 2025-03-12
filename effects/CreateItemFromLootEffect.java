package atavism.agis.effects;

import atavism.server.engine.*;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import atavism.agis.core.*;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.server.util.Log;

/**
 * Effect child class that creates an item and gives it to the caster. 
 * Can create multiple of the same item, but only of one type.
 * @author Andrew Harrison
 *
 */
public class CreateItemFromLootEffect extends AgisEffect {

    public CreateItemFromLootEffect(int id, String name) {
        super(id, name);
    }

    // add the effect to the object
    public void apply(EffectState state) {
		super.apply(state);
		Map<String, Serializable> params = state.getParams();
		if (Log.loggingDebug)
			Log.debug("CreateItemFromLootEffect: apply effect params is: " + params);
		int claimID = -1;
		int objectID = -1;
			if (params.containsKey("claimID"))
			claimID = (int) params.get("claimID");
		if (params.containsKey("objectID"))
			objectID = (int) params.get("objectID");
		if (claimID > 0 && objectID > 0) {
			Log.debug("CreateItemFromLootEffect: this effect is not for buildings");
			return;
		}
		OID playerOid = state.getTargetOid();
        OID bagOid = playerOid;
        Log.debug("loot CreateItemFromLootEffect apply");
        

        AgisInventoryClient.generateLootEffect(playerOid, lootsChance, lootsCount);
    }

    protected int item = -1;
    public int getItem() { return this.item; }
    public void setItem(int template) { this.item = template; }
    
    protected HashMap<Integer, Float> lootsChance = new HashMap<Integer, Float>(0);
    public HashMap<Integer, Float> getLootsChance() { return this.lootsChance;}
    public void setLootsChance(HashMap<Integer, Float> lootsChance) { this.lootsChance = lootsChance; }
    
    protected HashMap<Integer, Integer> lootsCount = new HashMap<Integer, Integer>(0);
    public HashMap<Integer, Integer> getLootsCount() { return this.lootsCount;}
    public void setLootsCount(HashMap<Integer, Integer> lootsCount) { this.lootsCount = lootsCount; }    
   
    protected int numberToCreate = 1;
    public int getNumberToCreate() { return numberToCreate; }
    public void setNumberToCreate(int numberToCreate) { this.numberToCreate = numberToCreate; }
    
    private static final long serialVersionUID = 1L;
}
