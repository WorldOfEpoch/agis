package atavism.agis.plugins;

import java.util.LinkedList;

import atavism.agis.objects.RankingData;
import atavism.server.engine.*;
import atavism.server.util.Log;
import atavism.msgsys.*;

public class RankingClient {

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
   
    
    public static final MessageType MSG_TYPE_RANGING_DATA = MessageType.intern("ao.RANKING_DATA");
    public static final MessageType MSG_TYPE_GET_RANKING = MessageType.intern("ao.GET_RANKING");
    public static final MessageType MSG_TYPE_GET_RANKING_LIST = MessageType.intern("ao.GET_RANKING_LIST");
    public static final String EXTMSG_RANKING_UPDATE = "ao.RANKING_UPDATE";
    public static final String EXTMSG_RANKING_LIST = "ao.RANKING_LIST";
    
    public static Short KILLING = 1;
    public static Short EXPERIENCE = 2;
    public static Short HARVESTING = 3;
    public static Short CRAFTING = 4;
    public static Short LOOTING = 5;
    public static Short USE_ABILITY = 6;
    public static Short FINAL_BLOW = 7;
    
}
