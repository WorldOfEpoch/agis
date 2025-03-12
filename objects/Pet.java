package atavism.agis.objects;

import atavism.server.objects.*;
import atavism.server.plugins.*;
import atavism.server.util.*;
import atavism.server.engine.*;

import java.io.*;

public abstract class Pet extends Entity implements Serializable
{
    public Pet() {
    	super();
    	  }
    public Pet(String entityName) {
    	super(entityName);
    	  }
    
    public Pet(String entityName, int mobTemplateID, String mobName, OID ownerOid) {
    	super(entityName);
    	this.mobName = mobName;
    	this.ownerOid = ownerOid;
    }
    
    public boolean despawnPet() {
    	//mobObj.despawn();
    	WorldManagerClient.despawn(mobObj);
    	return true;
    }

    /*public boolean activate() {
        if (Log.loggingDebug)
            log.debug("in activate: this " + this);
        // Clear the old subscribers
        sub = null;
        sub2 = null;
        // subscribe for some messages
        SubjectFilter filter = new SubjectFilter(getPlayerOid());
        //filter.addType(InventoryClient.MSG_TYPE_INV_UPDATE);
        filter.addType(AgisInventoryClient.MSG_TYPE_QUEST_ITEMS_LIST);
        filter.addType(CombatClient.MSG_TYPE_COMBAT_MOB_DEATH);
        filter.addType(QuestClient.MSG_TYPE_QUEST_TASK_UPDATE);
        sub = Engine.getAgent().createSubscription(filter, this);
        if (sub == null)
        	Log.debug("QUEST: sub is null");
        
        SubjectFilter filter2 = new SubjectFilter(getPlayerOid());
        filter2.addType(QuestClient.MSG_TYPE_CONCLUDE_QUEST);
        sub2 = Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);
        makeDeliveryItems();
        updateQuestLog();
        //TODO: maybe uncomment the next line?
        //boolean test = checkInventory(true);
        //boolean test = true;
        boolean test = updateObjectiveStatus();
        // updateQuestObjectives();
        //log.debug("BasicQuestState for quest: " + getQuestRef() + "activated");
        return true;
    }*/

   /* public void deactivate() {
        //if (Log.loggingDebug)
        //    log.debug("BasicQuestState.deactivate: playerOid=" + getPlayerOid() + " questRef=" + getQuestRef());
        if (sub != null) {
        	//log.debug("BasicQuestState.deactivate: (1)removed sub for playerOid=" + getPlayerOid() + " questRef=" + getQuestRef());
            Engine.getAgent().removeSubscription(sub);
            //log.debug("BasicQuestState.deactivate: (2)removed sub for playerOid=" + getPlayerOid() + " questRef=" + getQuestRef());
            sub = null;
	    }
        if (sub2 != null) {
            Engine.getAgent().removeSubscription(sub2);
            //log.debug("BasicQuestState.deactivate: removed sub2 for playerOid=" + getPlayerOid() + " questRef=" + getQuestRef());
            sub2 = null;
	    }
    }*/
    
    /**
     * process network messages
     */
    /*public void handleMessage(Message msg, int flags) {
        if (msg instanceof InventoryClient.InvUpdateMessage) {
            processInvUpdate((InventoryClient.InvUpdateMessage) msg);
        }
    	if (msg instanceof AgisInventoryClient.QuestItemsListMessage) {
            processInvUpdate((AgisInventoryClient.QuestItemsListMessage) msg);
        }
        else {
            log.error("unknown msg: " + msg);
        }
        //return true;
    }*/
    
    /*protected boolean processInvUpdate(AgisInventoryClient.QuestItemsListMessage msg) {
        //if (Log.loggingDebug)
        //    log.debug("processInvUpdate: player=" + getPlayerOid() + ", itemList=" + msg);
        HashMap<String, Integer> itemList = msg.getItemList();
        //checkInventory(false, itemList);
        return true;
    }*/

    static final Logger log = new Logger("Pet");

    //transient Long sub = null;
    //transient Long sub2 = null;
    //Long sub = null;
    //Long sub2 = null;
    
    public String getMobName() {
    	return mobName;
    }
    public void setMobName(String mobName) {
    	this.mobName = mobName;
    }
    public int getMobTemplateID() {
    	return mobTemplateID;
    }
    public void setMobTemplateID(int mobTemplateID) {
    	this.mobTemplateID = mobTemplateID;
    }
    public OID getMobObj() {
    	return mobObj;
    }
    public void setMobObj(OID mobObj) {
    	this.mobObj = mobObj;
    }
    public boolean getSpawned() {
    	return isSpawned;
    }
    public void setSpawned(boolean isSpawned) {
    	this.isSpawned = isSpawned;
    }
    public OID getOwnerOid() {
    	return ownerOid;
    }
    public void setOwnerOid(OID ownerOid) {
    	this.ownerOid = ownerOid;
    }

    protected String mobName = "";
    protected int mobTemplateID;
    protected OID mobObj = null;
    protected boolean isSpawned = false;
    protected OID ownerOid = null;
    
    private static final long serialVersionUID = 1L;
}
