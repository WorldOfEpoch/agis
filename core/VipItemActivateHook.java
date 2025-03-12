package atavism.agis.core;

import atavism.server.engine.OID;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;

/**
 * an activate hook for items that trigger adding  Vip Ponts or Expand Vip Time  
 * when the item is activated
 */
public class VipItemActivateHook implements ActivateHook {
    public VipItemActivateHook() {
    	super();
    }

    public VipItemActivateHook(int points,int time,boolean oadelete) {
    	super();
    	this.points = points;
    	this.time   = time;
    	this.oadelete = oadelete;
    }

    protected int points=0;
    protected int time=0;
    protected boolean oadelete = false;

    public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
    	
    	BonusClient.extendVip(activatorOid, points, time);
    	//AgisInventoryClient.removeSpecificItem(activatorOid, item.getOid(), false, 1);
    	return oadelete;
    }
    
    public String toString() {
    	return "VipItemActivateHook points" + points+" time="+time;
    }
    private static final long serialVersionUID = 1L;
    
}
