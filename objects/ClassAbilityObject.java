package atavism.agis.objects;

import atavism.agis.plugins.ClassAbilityClient;
import atavism.server.engine.Engine;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.messages.PropertyMessage;
import atavism.server.objects.Entity;
import atavism.server.objects.ObjectType;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;

/**
 * 
 * This class is meant to be the generated object for the Player that holds the stats for skills/abilities that a 
 * player has. Throw-back class that is not used anymore.
 * 
 * @author Judd
 *
 */
public class ClassAbilityObject extends Entity {
	
	String playerclass;
	
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	
	public ClassAbilityObject() {
        super();
        setNamespace(Namespace.CLASSABILITY);
    }

    public ClassAbilityObject(OID objOid) {
        super(objOid);
        setNamespace(Namespace.CLASSABILITY);
    }

    public String toString() {
        return "[Entity: " + getName() + ":" + getOid() + "]";
    }
	
    public ObjectType getType() {
        return ObjectType.intern((short)11, "ClassAbilityObject");
    }
    
    public String getPlayerClass(){
    	return playerclass;
    }
    
    public void setPlayerClass(String playerclassname){
    	playerclass = playerclassname;
    }
    
     
    public void updateBaseStat(int id, int modifier){
    	AgisStat stat = (AgisStat)getProperty(id + "_exp");
    	AgisStat rank = (AgisStat)getProperty(id + "_rank");
    	if(stat == null || rank == null){
    	    log.warn("ClassAbilityObject.updateBaseStat - player does nt have the skill/ability " + id);
    	    return;
    	}
    	if (rank.base < rank.max){
	    	stat.modifyBaseValue(modifier);
	    	ClassAbilityClient.sendXPUpdate(this.getOid(), stat.getName(), stat.getCurrentValue());
	    	//((AgisStatDef)ClassAbilityPlugin.lookupStatDef(id + "_rank")).update(stat, this);
	    	statSendUpdate(false);
    	}
    }
    
    public void statSendUpdate(boolean sendAll) {
        statSendUpdate(sendAll, null);
    }

    public void statSendUpdate(boolean sendAll, OID targetOid) {
		lock.lock();
		try {
	            PropertyMessage propMsg = null;
	            TargetedPropertyMessage targetPropMsg = null;
	            if (targetOid == null)
	                propMsg = new PropertyMessage(getOid());
	            else
	                targetPropMsg =
	                    new TargetedPropertyMessage(targetOid,getOid());
	            int count = 0;
		    for (Object value : getPropertyMap().values()) {
			if (value instanceof AgisStat) {
			    AgisStat stat = (AgisStat) value;
			    if (sendAll || stat.isDirty()) {
	                        if (propMsg != null)
	                            propMsg.setProperty(stat.getName(), stat.getCurrentValue());
	                        else{
	                            targetPropMsg.setProperty(stat.getName(), stat.getCurrentValue());
	                        }
	                        if (! sendAll)
	                            stat.setDirty(false);
	                        count++;
			    }
			}
		    }
		    if (count > 0) {
			Engine.getPersistenceManager().setDirty(this);
	                if (propMsg != null)
	                    Engine.getAgent().sendBroadcast(propMsg);
	                else
	                    Engine.getAgent().sendBroadcast(targetPropMsg);
		    }
		}
		finally {
		    lock.unlock();
		}
    }

}
