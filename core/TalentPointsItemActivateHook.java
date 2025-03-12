package atavism.agis.core;

import atavism.server.engine.OID;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;

/**
 * an activate hook for items that trigger adding  Vip Ponts or Expand Vip Time  
 * when the item is activated
 */
public class TalentPointsItemActivateHook implements ActivateHook {
    public TalentPointsItemActivateHook() {
    	super();
    }

    public TalentPointsItemActivateHook(int points,boolean oadelete) {
    	super();
    	this.points = points;
     	this.oadelete = oadelete;
         }

    protected int points=0;
    protected boolean oadelete = false;

    public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
    	
//    	BonusClient.extendVip(activatorOid, points, time);
    	ClassAbilityClient.addTalentPoints(activatorOid,points);
    	//AgisInventoryClient.removeSpecificItem(activatorOid, item.getOid(), false, 1);
    	return oadelete;
    	   
    }
    
    public String toString() {
    	return "TalentPointsItemActivateHook points" + points;
    }
    private static final long serialVersionUID = 1L;
    
}
