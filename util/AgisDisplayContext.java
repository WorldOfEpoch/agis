package atavism.agis.util;

import java.util.*;
import atavism.agis.objects.*;
import atavism.server.objects.*;

public class AgisDisplayContext {
    /**
     * creates a full display context for the object
     * including clothing he/she has equipped
     */
    public static DisplayContext createFullDisplayContext(AgisObject obj) {
    	//
    	// get base display context from the obj
    	//
    	DisplayContext dc = obj.displayContext();
    	 if (dc == null)
            return null;

        dc = (DisplayContext) dc.clone();

        //
        // if its a mob - add equipped items, otherwise return the base dc
        //
        if (! (obj instanceof AgisMob)) {
        	return dc;
        }
        AgisMob mob = (AgisMob) obj;

        // add all the equipped items that matter
        Set<AgisItem> items = mob.getEquippedItems();
        /*if (Log.loggingDebug)
            Log.debug("createFullDisplayContext: mob=" + mob.getName() +
                      ", num items=" + items.size());*/
        for (AgisItem item : items) {
            /*if (Log.loggingDebug)
                Log.debug("createFullDisplayContext: mob=" + mob.getName() +
                          ", considering equipped item " + 
                          item.getName());*/
            DisplayContext itemDC = item.displayContext();
            String meshFile = itemDC.getMeshFile();
            if (meshFile == null) {
                // no meshfile
                continue;
            }

            // check if its an attachment (if it is, skip it)
            if (itemDC.getAttachableFlag()) {
                continue;
            }

            // add the submeshes to this event's display context
            Set<DisplayContext.Submesh> submeshes = itemDC.getSubmeshes();
            /*if (Log.loggingDebug)
                Log.debug("createFullDisplayContext: mob=" + mob.getName() +
                          ", adding submeshes for item " + 
                          item.getName() +
                          ", dc=" + dc);*/
            dc.addSubmeshes(submeshes);
            /*if (Log.loggingDebug)
                Log.debug("createFullDisplayContext: mob=" + mob.getName() +
                          ", done adding submeshes for item " + 
                          item.getName() +
                          ", dc=" + dc);*/
        }
        return dc;
    }
							  
}
