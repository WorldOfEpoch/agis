package atavism.agis.behaviors;

import java.util.*;

import atavism.agis.database.AccountDatabase;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;
import atavism.msgsys.*;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ObjectManagerPlugin;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedPropertyMessage;
import atavism.server.messages.*;
import atavism.server.objects.*;
import atavism.server.engine.*;
import atavism.server.util.*;

public class ShopBehavior extends Behavior implements Runnable {

    public void initialize() {

    	OID mobOid = this.getObjectStub().getOid();
        if (Log.loggingDebug)
            log.debug("ShopBehavior.initialize: my moboid=" + mobOid);

       // SubjectFilter filter = new SubjectFilter(mobOid);
      //  filter.addType(WorldManagerClient.MSG_TYPE_UPDATE_OBJECT);
       // filter.addType(AgisInventoryClient.MSG_TYPE_REQ_OPEN_MOB);
       // filter.addType(AgisMobClient.MSG_TYPE_DESPAWN_PLAYER_SHOP);
       //   eventSub = Engine.getAgent().createSubscription(filter, this);
        //Log.debug("QpenBehavior: created subject filter for oid=" + mobOid);
        
        MessageTypeFilter filter2 = new MessageTypeFilter();
        filter2.addType(AgisMobClient.MSG_TYPE_DESPAWN_PLAYER_SHOP);
        eventSub2 = Engine.getAgent().createSubscription(filter2, this);

        // Subscribe to all state status change messages. This is inefficient, but it works.
        //MessageTypeFilter statusFilter = new MessageTypeFilter(QuestClient.MSG_TYPE_QUEST_STATE_STATUS_CHANGE);
        //statusSub = Engine.getAgent().createSubscription(statusFilter, this);
    }

    public void activate() {
    	 log.debug("ShopBehavior.activate");
    	//if(shopOid!=null) {
    	 AccountDatabase adb = new AccountDatabase(false);
    	 PlayerShop ps = adb.getPlayerShop(shopOwner, shopOid);
    	 log.debug("ShopBehavior.activate shopOwner="+shopOwner+" shopOid="+shopOid+" ps="+ps);
    	if(ps!=null) {
    	 shopMessage = ps.getTitle();
    	 shopTimeOut = ps.getTimeout();
    	 shopDestroyOnLogOut = ps.getEndPlayerOnLogout();
    	 shopCreateTime = ps.getCreateTime();
    	 log.debug("ShopBehavior.activate "+System.currentTimeMillis()+" "+shopCreateTime);
    		OID myOid = getObjectStub().getOid();
    		EnginePlugin.setObjectPropertyNoResponse(myOid, WorldManagerClient.NAMESPACE, "shopTitle", shopMessage);
    		EnginePlugin.setObjectPropertyNoResponse(myOid, WorldManagerClient.NAMESPACE, "plyShopId", shopOid);
        	EnginePlugin.setObjectPropertyNoResponse(myOid, WorldManagerClient.NAMESPACE, "playerShop", true);
    		AgisMobPlugin.AddShopBehavior(myOid, this);
    	//}
    	}else {
    		log.error("ShopBehavior shop not exist despawn");
    		WorldManagerClient.despawn(getObjectStub().getOid());
    	}
    		 log.debug("ShopBehavior.activate END");
    }

    public void deactivate() {
        lock.lock();
        try {
          /*  if (eventSub != null) {
                Engine.getAgent().removeSubscription(eventSub);
                eventSub = null;
            }*/
            if (eventSub2 != null) {
            	Engine.getAgent().removeSubscription(eventSub2);
                eventSub2 = null;
            }
         /*   if (statusSub != null) {
                Engine.getAgent().removeSubscription(statusSub);
                statusSub = null;
            }*/
        } finally {
            lock.unlock();
        }
        OID myOid = getObjectStub().getOid();
    	AgisMobPlugin.RemoveShopBehavior(myOid);
    	
    }

    public void handleMessage(Message msg, int flags) {
    	log.debug("ShopBehavior: "+msg);
    	if(msg instanceof AgisMobClient.DespawnPlayerShopMessage ) {
    		AgisMobClient.DespawnPlayerShopMessage dmsg = (AgisMobClient.DespawnPlayerShopMessage) msg;
    		log.debug("ShopBehavior: "+dmsg.getShopOid()+" "+shopOid);
    		if(dmsg.getShopOid()!=null &&dmsg.getShopOid().equals(shopOid)) {
    			log.debug("ShopBehavior: == despawn");
    			OID myOid = getObjectStub().getOid();
    			WorldManagerClient.despawn(myOid);
    		}
    		
    	}/*else if (msg instanceof WorldManagerClient.UpdateMessage) {
            WorldManagerClient.UpdateMessage updateMsg = (WorldManagerClient.UpdateMessage) msg;
                processUpdateMsg(updateMsg);
        } else if (msg instanceof AgisInventoryClient.RequestOpenMobMessage) {
        	AgisInventoryClient.RequestOpenMobMessage reqMsg = (AgisInventoryClient.RequestOpenMobMessage) msg;
        	processReqOpenMobMsg(reqMsg);
        } else if (msg instanceof QuestClient.StateStatusChangeMessage) {
            QuestClient.StateStatusChangeMessage nMsg = (QuestClient.StateStatusChangeMessage) msg;
            processStateStatusChangeMsg(nMsg);
        } else if (msg instanceof QuestClient.QuestItemUpdateMessage) {
        	//Log.debug("OPEN: Got quest item update");
        	QuestClient.QuestItemUpdateMessage nMsg = (QuestClient.QuestItemUpdateMessage) msg;
        	processQuestItemUpdateMsg(nMsg);
        } else {
            log.error("onMessage: got unknown msg: " + msg);
            return; //return false;
        }*/
        //return true;
    }
    
    private void processQuestItemUpdateMsg(QuestClient.QuestItemUpdateMessage msg) {
    	OID myOid = getObjectStub().getOid();
    	OID playerOid = msg.getSubject();
        List<Integer> itemsRequired = msg.getItemsRequired();
        boolean hasAvailableItem = false;
        for (int item : itemsRequired) {
        	for (int itemHeld : itemsHeld) {
        		//Log.debug("OPEN: comparing items: " + item + "-" + itemHeld);
        		if (item == itemHeld) {
        			//Log.debug("OPEN: found item match, setting has item to true");
        			hasAvailableItem = true;
        			break;
        		}
        	}
        }
        
        //Log.debug("OPEN: sending hasitemavailable property update for player: " 
        //		+ playerOid + " and mob: " + myOid + "value = " + hasAvailableItem);
        TargetedPropertyMessage propMsg = new TargetedPropertyMessage(playerOid, myOid);
        propMsg.setProperty(AgisStates.ItemAvailable.toString(), hasAvailableItem);
        Engine.getAgent().sendBroadcast(propMsg);
    }

    private void processStateStatusChangeMsg(QuestClient.StateStatusChangeMessage msg) {
    	OID playerOid = msg.getSubject();
        int questRef = msg.getQuestRef();
        //if (Log.loggingDebug)
        //    log.debug("processStateStatusChangeMsg: myOid=" + getObjectStub().getOid()
        //              + " playerOid=" + playerOid + " questRef=" + questRef);
        
        handleQuestState(playerOid);
    }
     
    protected void giveItemsToPlayer(OID myOid, OID playerOid, HashMap<Integer, QuestState> activeQuests) {
    	for (int item : itemsHeld) {
    		for (QuestState qs : activeQuests.values()) {
            	if (!(qs instanceof BasicQuestState))
            		continue;
            	BasicQuestState bqs = (BasicQuestState) qs;
            	List<BasicQuestState.CollectionGoalStatus> cgsList = bqs.getGoalsStatus();
            	for (BasicQuestState.CollectionGoalStatus cgs: cgsList) {
            		if (itemsHeld.contains(cgs.templateName)) {
            			if (cgs.currentCount < cgs.targetCount) {
            				giveItemToPlayer(playerOid, item);
            				break;
            			}
            		}
            	}
            }
    	}
    	if (itemLimit != 0) {
    		numItems--;
    		if (numItems < 1) {
    			//this.obj.despawn();
    			//Engine.getExecutor().schedule(this, respawnTime, TimeUnit.MILLISECONDS);
    			// Send a message to be caught by the spawn generator
    			PropertyMessage propMsg = new PropertyMessage(this.obj.getOid());
    	        propMsg.setProperty("objectEmpty", true);
    	        Engine.getAgent().sendBroadcast(propMsg);
    			/*TargetedPropertyMessage propMsg2 = new TargetedPropertyMessage(playerOid, myOid);
    	        propMsg2.setProperty(AgisStates.ItemAvailable.toString(), false);
    	        Engine.getAgent().sendBroadcast(propMsg2);*/
    		}
    	}
    }

    public void run() {
    	//this.obj.spawn();
    	numItems = itemLimit;
    }
     
    /* Gives the item to the player */
    protected void giveItemToPlayer(OID playerOid, int itemID) {
        lock.lock();
        try {
        	Template overrideTemplate = new Template();
            overrideTemplate.put(Namespace.OBJECT_MANAGER, ObjectManagerClient.TEMPL_PERSISTENT, true);
            OID itemOid = ObjectManagerClient.generateObject(itemID, ObjectManagerPlugin.ITEM_TEMPLATE, overrideTemplate);
            // add to inventory
            OID bagOid = playerOid;
            if (Log.loggingDebug)
                Log.debug("processReqConcludedMsg: createitem: oid=" + itemOid + ", bagOid=" + bagOid + ", adding to inventory");
            boolean rv = InventoryClient.addItem(bagOid, playerOid, bagOid, itemOid);
            if (Log.loggingDebug)
                Log.debug("processReqConcludedMsg: createitem: oid=" + itemOid + ", added, rv=" + rv);
        }
        finally {
            lock.unlock();
        }
    }

    private void processReqOpenMobMsg(AgisInventoryClient.RequestOpenMobMessage reqMsg) {
    	OID myOid = getObjectStub().getOid();
    	OID playerOid = reqMsg.getPlayerOid();

        HashMap<Integer, QuestState> activeQuests = QuestClient.getActiveQuests(playerOid);
        giveItemsToPlayer(myOid, playerOid, activeQuests);
    }
 
    public void processUpdateMsg(WorldManagerClient.UpdateMessage msg) {
    	OID myOid = msg.getSubject();
    	OID playerOid = msg.getTarget();

        handleQuestState(playerOid);
    }

    protected void handleQuestState(OID playerOid) {
    	OID myOid = getObjectStub().getOid();
    	HashMap<Integer, QuestState> activeQuests = QuestClient.getActiveQuests(playerOid);

        boolean hasAvailableItem = false;
        
        for (QuestState qs : activeQuests.values()) {
        	if (!(qs instanceof BasicQuestState))
        		continue;
        	BasicQuestState bqs = (BasicQuestState) qs;
        	List<BasicQuestState.CollectionGoalStatus> cgsList = bqs.getGoalsStatus();
        	for (BasicQuestState.CollectionGoalStatus cgs: cgsList) {
        		if (itemsHeld.contains(cgs.templateName)) {
        			if (cgs.currentCount < cgs.targetCount)
        				hasAvailableItem = true;
        		}
        	}
        }
        
        Log.debug("OPEN: sending hasitemavailable property update for player: " 
        		+ playerOid + " and mob: " + myOid + "value = " + hasAvailableItem);
        TargetedPropertyMessage propMsg = new TargetedPropertyMessage(playerOid, myOid);
        propMsg.setProperty(AgisStates.ItemAvailable.toString(), hasAvailableItem);
        Engine.getAgent().sendBroadcast(propMsg);
    }
    
    public void ValidateShop() {
    	long time = System.currentTimeMillis();
    	Log.debug("ShopBehavior: ValidateShop time="+time+" shopCreateTime="+shopCreateTime+" shopTimeOut="+shopTimeOut+" shopOwner="+shopOwner+" shopDestroyOnLogOut="+shopDestroyOnLogOut+" shopOid="+shopOid);
    	if(shopTimeOut>0 && time > shopCreateTime + shopTimeOut * 60000) {
    		AgisInventoryClient.DeleteShop(shopOid);
    	}
    }
    
    public void PlayerLogOut(OID plyOid) {
    	long time = System.currentTimeMillis();
    	Log.debug("ShopBehavior: PlayerLogOottime="+time+" shopCreateTime="+shopCreateTime+" shopTimeOut="+shopTimeOut+" shopOwner="+shopOwner+" shopDestroyOnLogOut="+shopDestroyOnLogOut+" shopOid="+shopOid);
    	if(shopDestroyOnLogOut && plyOid == shopOwner) {
    		AgisInventoryClient.DeleteShop(shopOid);
    		//AgisMobClient.DespawnPlayerShop(shopOid);
    	}
    }
    
  
    
    
    public void setItemsHeld(ArrayList<Integer> items) {
    	itemsHeld = items;
    }
    public List getItemsHeld() {
    	return itemsHeld;
    }
    
    public void setItemLimit(int itemLimit) {
    	this.itemLimit = itemLimit;
    }
    public int getItemLimit() {
    	return itemLimit;
    }
    
    public void setNumItems(int numItems) {
    	this.numItems = numItems;
    }
    public int getNumItems() {
    	return numItems;
    }
    
    public void setRespawnTime(int time) {
    	this.respawnTime = time;
    }

	public int getRespawnTime() {
		return respawnTime;
	}

	public void setShopOid(OID shopOid) {
		this.shopOid = shopOid;
	}

	public OID getShopOid() {
		return shopOid;
	}

	public void setShopOwner(OID owner) {
		this.shopOwner = owner;
	}

	public OID getShopOwner() {
		return shopOwner;
	}

	public void setShopMessage(String message) {
		shopMessage = message;
	}

	public String getShopMessage() {
		return shopMessage;
	}

	public void setShopTimeOut(int time) {
		this.shopTimeOut = time;
	}

	public int getShopTimeOut() {
		return shopTimeOut;
	}

	public void setShopDestroyOnLogOut(boolean v) {
		shopDestroyOnLogOut = v;
	}

	public boolean getShopDestroyOnLogOut() {
		return shopDestroyOnLogOut;
	}
	
	public void setShopCreateTime(long v) {
		shopCreateTime = v;
	}

	public long getShopCreateTime() {
		return shopCreateTime;
	}
	
	long shopCreateTime=0L;
	OID shopOwner = null;
	boolean shopDestroyOnLogOut = false;
	int shopTimeOut = 0;
	OID shopOid = null;
	String shopMessage = "";

//	Long eventSub = null;
	Long eventSub2 = null;
//	Long statusSub = null;
    List<Integer> itemsHeld = new ArrayList<Integer>();
    int itemLimit = 0;
    int numItems = 0;
    int respawnTime = 30000;
    static final Logger log = new Logger("OpenBehavior");
    private static final long serialVersionUID = 1L;
}
