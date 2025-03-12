package atavism.agis.plugins;

import java.util.LinkedList;

import atavism.agis.objects.RankingData;
import atavism.server.engine.*;
import atavism.server.util.Log;
import atavism.msgsys.*;
import atavism.server.messages.*;

public class AchievementsClient {
	public static void aquireAchievement(OID oid, int id) {
		PropertyMessage msg = new PropertyMessage();
		msg.setMsgType(MSG_TYPE_SET_ACHIEVEMENT);
		msg.setSubject(oid);
		msg.setProperty("id", id);
		Engine.getAgent().sendBroadcast(msg);
		// Log.debug("TARGET: sent target message rankingData="+rankingData);
	}

    public static void sendRankingData(LinkedList<RankingData> rankingData) {
    	RankingDataMessage msg = new RankingDataMessage(rankingData);
        Engine.getAgent().sendBroadcast(msg);
        Log.debug("TARGET: sent target message rankingData="+rankingData);
    }

    public static class RankingDataMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
		
        public RankingDataMessage() {
            super();
        }
        public RankingDataMessage(LinkedList<RankingData> rankingData) {
            super(MSG_TYPE_RANGING_DATA);
            setRankingDatas(rankingData);
        }
        
        public LinkedList<RankingData> getRankingDatas() {
        	return rankingData;
        }
        public void setRankingDatas(LinkedList<RankingData> rankingData) {
        	this.rankingData = rankingData;
        }
       
        protected LinkedList<RankingData> rankingData;
	}
    // Enumerated values of QuestStatus
    
    public static final MessageType MSG_TYPE_RANGING_DATA = MessageType.intern("ao.RANKING_DATA");
    public static final MessageType MSG_TYPE_GET_ACHIEVEMENTS = MessageType.intern("ao.GET_ACHIEVEMENTS");
    public static final MessageType MSG_TYPE_SET_ACHIEVEMENTS_TITLE = MessageType.intern("ao.SET_ACHIEVEMENTS_TITLE");
    public static final String EXTMSG_ACHIEVEMENTS_UPDATE = "ao.ACHIEVEMENTS_UPDATE";
    public static final MessageType MSG_TYPE_SET_ACHIEVEMENT = MessageType.intern("ao.SET_ACHIEVEMENT");
    
    public static Short KILL = 1;
    public static Short EXPERIENCE = 2;
    public static Short HARVEST = 3;
    public static Short CRAFT = 4;
    public static Short CRAFT_SKILL = 9;
    public static Short LOOT = 5;
    public static Short USE_ABILITY = 6;
    public static Short FINAL_BLOW = 7;
    public static Short GEAR_SCORE = 8;
    
}
