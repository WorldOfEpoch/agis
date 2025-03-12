package atavism.agis.core;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.objects.ObjectTypes;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.*;
import atavism.agis.objects.*;
import atavism.agis.util.ExtendedCombatMessages;

/**
 * An acquire hook for items that turn into a building resource
 * when acquired.
 */
public class RecipeItemActivateHook implements ActivateHook {
    public RecipeItemActivateHook() {
    	super();
    }

    public RecipeItemActivateHook(int recipeID, boolean oadelete) {
    	super();
    	setRecipeID(recipeID);
     	this.oadelete = oadelete;
           }

    public void setRecipeID(int recipeID) {
        if (recipeID == -1) {
            throw new RuntimeException("RecipeItemActivateHook.setResource: bad resource");
        }
        this.recipeID = recipeID;
    }
    public int getRecipeID() {
    	return recipeID;
    }
    protected int recipeID;
    protected boolean oadelete = false;

    /**
     * Adds the item to the Building Resources map for the player and returns true telling the item to be
     * destroyed.
     */
    public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
        if (Log.loggingDebug)
            Log.debug("RecipeItemActivateHook.activate: activator=" + activatorOid + " item=" + item + " recipe=" + recipeID);
        // Only convert it if it is activated by a player
        if (WorldManagerClient.getObjectInfo(activatorOid).objType != ObjectTypes.player) {
        	return false;
        }
        // Add building resource
        LinkedList<String> recipes = (LinkedList)EnginePlugin.getObjectProperty(activatorOid, WorldManagerClient.NAMESPACE, "recipes");
        if (recipes == null) {
        	recipes = new LinkedList<String>();
        } else if (recipes.contains("" + recipeID)) {
        	ExtendedCombatMessages.sendErrorMessage(activatorOid, "You already know that recipe");
        	return false;
        }
        recipes.add("" + recipeID);
        EnginePlugin.setObjectProperty(activatorOid, WorldManagerClient.NAMESPACE, "recipes", recipes);
        ExtendedCombatMessages.sendCombatText(activatorOid, "Learned new Blueprint", 16);
        ExtendedCombatMessages.sendAnouncementMessage(activatorOid, "You have learned a new recipe", "Skill");
        // Need to remove the item
      //  AgisInventoryClient.removeSpecificItem(activatorOid, item.getOid(), true, 1);
        return oadelete;
    }
    
    /**
     * Sends down the map of Building Resources the player has to the client. Static function so can be called from anywhere.
     * @param oid
     * @param resources
     */
    public static void sendRecipes(OID oid, LinkedList<String> recipes) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put("ext_msg_subtype", "recipes");
        int numResources = 0;
        for (String resourceID : recipes) {
        	Log.debug("RESOURCE: got currency to send: " + resourceID);
        	props.put("resource" + numResources + "ID", resourceID);
        	numResources++;
        }
        
        props.put("numRecipes", numResources);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, oid, oid, false, props);
	    Engine.getAgent().sendBroadcast(msg);
	    Log.debug("RECIPES: sending down recipes message to: " + oid + " with props: " + props);
	}
    
    public String toString() {
    	return "RecipeItemActivateHook=" + recipeID;
    }

    private static final long serialVersionUID = 1L;
}
