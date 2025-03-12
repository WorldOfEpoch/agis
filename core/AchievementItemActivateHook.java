package atavism.agis.core;

import atavism.server.engine.OID;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;

/**
 * an activate hook for items that trigger adding  Vip Ponts or Expand Vip Time  
 * when the item is activated
 */
public class AchievementItemActivateHook implements ActivateHook {
    public AchievementItemActivateHook() {
    	super();
    }

    public AchievementItemActivateHook(int id,boolean oadelete) {
    	super();
    	this.id = id;
     	this.oadelete = oadelete;
          }

    protected int id=0;
    protected boolean oadelete = false;

    public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
    	
//    	BonusClient.extendVip(activatorOid, points, time);
    	AchievementsClient.aquireAchievement(activatorOid,id);
    	//AgisInventoryClient.removeSpecificItem(activatorOid, item.getOid(), false, 1);
    	return oadelete;
    }
    
    public String toString() {
    	return "AchievementItemActivateHook id=" + id;
    }
    private static final long serialVersionUID = 1L;
    
}
