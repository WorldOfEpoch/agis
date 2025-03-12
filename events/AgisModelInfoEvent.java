package atavism.agis.events;

import atavism.server.objects.*;
import atavism.server.network.*;
import atavism.server.util.*;
import atavism.server.engine.OID;
import atavism.server.events.*;
import atavism.agis.objects.*;
import java.util.*;

/**
 * send out what meshes to draw for the given object
 * it is a full update, so if you unequip a rigged attachment,
 * a full update is sent out
 */
public class AgisModelInfoEvent extends ModelInfoEvent {
    public AgisModelInfoEvent() {
        super();
    }

    public AgisModelInfoEvent(AOByteBuffer buf, ClientConnection con) {
	super(buf, con);
    }

    public AgisModelInfoEvent(AOObject obj) {
	super(obj);
        if (obj instanceof AgisMob) {
            // add all the equipment also
            processAgisMob((AgisMob)obj);
        }
    }

    public AgisModelInfoEvent(OID oid) {
	super(oid);
    }

    public String getName() {
        return "AgisModelInfoEvent";
    }

    // need to add all the equipment meshes also
    void processAgisMob(AgisMob mob) {
        Set<AgisItem> items = mob.getEquippedItems();

        if (Log.loggingDebug)
            log.debug("processAgisMob: mob=" + mob.getName() +
                      ", num items=" + items.size());
        for (AgisItem item : items) {
            if (Log.loggingDebug)
                log.debug("processAgisMob: mob=" + mob.getName() +
                          ", considering equipped item " + 
                          item.getName());
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
            if (Log.loggingDebug)
                log.debug("processAgisMob: mob=" + mob.getName() +
                          ", adding submeshes for item " + 
                          item.getName() +
                          ", dc=" + this.dc);
            this.dc.addSubmeshes(submeshes);
            if (Log.loggingDebug)
                log.debug("processAgisMob: mob=" + mob.getName() +
                          ", done adding submeshes for item " + 
                          item.getName() +
                          ", dc=" + this.dc);
        }
    }

    static final Logger log = new Logger("AgisModelInfoEvent");
}
