package atavism.agis.core;

import atavism.server.engine.OID;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;

/**
 * an activate hook for items that trigger adding  Vip Ponts or Expand Vip Time  
 * when the item is activated
 */
public class TalentResetActivateHook implements ActivateHook {
    public TalentResetActivateHook(boolean oadelete) {
    	super();
    	this.oadelete = oadelete;
    }

    protected boolean oadelete = false;


    public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
    	
    	ClassAbilityClient.talentReset(activatorOid);
    	//AgisInventoryClient.removeSpecificItem(activatorOid, item.getOid(), false, 1);
    	return oadelete;
    }
    
    public String toString() {
    	return "SkillResetActivateHook";
    }
    private static final long serialVersionUID = 1L;
    
}
