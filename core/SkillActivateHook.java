package atavism.agis.core;

import atavism.server.engine.OID;
import atavism.server.util.*;
import atavism.agis.objects.*;

/**
 * an activate hook attached to skill scrolls.
 * when the scroll is activated, the mob gets the skill added to his list
 */
public class SkillActivateHook implements ActivateHook {
    public SkillActivateHook() {
    }

    /**
     * what is the skill you get for this
     */
    public SkillActivateHook(AgisSkill skill,boolean oadelete) {
        setSkill(skill);
     	this.oadelete = oadelete;
          }

    public void setSkill(AgisSkill skill) {
        this.skill = skill;
    }
    public AgisSkill getSkill() {
        return skill;
    }

    /**
     * returns whether the item was successfully activated
     */
    public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
        if (Log.loggingDebug)
            Log.debug("SkillActivateHook.activate: activator=" + activatorOid +
                      ", skill=" + getSkill().getName());
//         player.addSkill(getSkill());
//         player.sendServerInfo("You have learned the skill " + 
//                               getSkill().getName());

        // destroy the item
//         AgisItem item = player.findItem(item.getTemplate());
//         if (item == null) {
//             throw new AORuntimeException("SkillActivateHook.activate: could not find the item with matching template");
//         }
//         if (! player.destroyItem(item)) {
//             throw new AORuntimeException("SkillActivateHook.activate: destroyItem failed");
//         }
        return oadelete;
    }

    protected AgisSkill skill = null;
    protected boolean oadelete = false;

    private static final long serialVersionUID = 1L;
}
