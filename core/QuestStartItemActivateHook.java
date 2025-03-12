package atavism.agis.core;

import java.util.LinkedList;

import atavism.server.engine.OID;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;

/**
 * an activate hook for items that trigger abilities
 * when the item is activated, the mob uses the ability
 */
public class QuestStartItemActivateHook implements ActivateHook {
    public QuestStartItemActivateHook() {
    	super();
    }

    public QuestStartItemActivateHook(int questID,boolean oadelete) {
    	super();
    	setQuestID(questID);
     	this.oadelete = oadelete;
         }

    public void setQuestID(int questID) {
        if (questID < 1) {
            throw new RuntimeException("QuestStartItemActivateHook.setQuestID: bad quest");
        }
        this.questID = questID;
    }
    public int getQuestID() {
    	return questID;
    }
    protected int questID;
    protected boolean oadelete = false;

    public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
    	LinkedList<Integer> quests = new LinkedList<Integer>();
    	quests.add(questID);
    	QuestClient.offerQuestToPlayer(activatorOid, item.getOid(), quests, true);
        //return QuestClient.startQuestForPlayer(activatorOid, questID);
    	return oadelete;
    }
    
    public String toString() {
    	return "QuestStartItemActivateHook.quest=" + questID;
    }

    private static final long serialVersionUID = 1L;
}
