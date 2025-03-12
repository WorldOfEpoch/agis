package atavism.agis.plugins;

import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import atavism.agis.database.AccountDatabase;
import atavism.agis.database.AuthDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.objects.CollectionData;
import atavism.agis.objects.AchievementSetting;
import atavism.agis.objects.Ranking;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.util.Log;
import atavism.server.util.Logger;

/**
 * 
 */
public class RankingPlugin extends EnginePlugin {
	public RankingPlugin() {
		super("Ranking");
		setPluginType("Ranking");
	}
	public String getName() {
		return "Ranking";
	}
	public void onActivate() {
		log.debug("RankingPlugin activate");
		registerHooks();
		log.debug("RankingPlugin activate 1");
		
		MessageTypeFilter filter = new MessageTypeFilter();
		filter.addType(AchievementsClient.MSG_TYPE_RANGING_DATA);
		filter.addType(RankingClient.MSG_TYPE_GET_RANKING);
		filter.addType(RankingClient.MSG_TYPE_GET_RANKING_LIST);
		Engine.getAgent().createSubscription(filter, this);
		log.debug("RankingPlugin activate 2");
		
		MessageTypeFilter filter2 = new MessageTypeFilter();
		filter2.addType(LoginMessage.MSG_TYPE_LOGIN);
		filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
		Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);
		log.debug("RankingPlugin activate 3");
		
		authDB = new AuthDatabase();
		ContentDatabase ctDB = new ContentDatabase(false);
		log.debug("RankingPlugin activate 4");
		
		String period = ctDB.loadGameSetting("RANKING_CALCULATION_INTERVAL");
		if (period != null) {
			RANKING_CALCULATION_INTERVAL = Integer.parseInt(period);
			Log.debug("Loaded Game Setting RANKING_CALCULATION_INTERVAL=" + RANKING_CALCULATION_INTERVAL);
		}
		settings = ctDB.GetRankingsSetting();
		//ctDB.close();
		aDB = new AccountDatabase(true);
	
		long lastRun = aDB.getLastRankingCalculation();
		if (lastRun == 0l) {
			lastRun = RANKING_CALCULATION_INTERVAL;
		} else {
			lastRun = System.currentTimeMillis() - lastRun;
			lastRun = lastRun / 60000;
			if (lastRun < 0)
				lastRun = 0;
			if (lastRun > RANKING_CALCULATION_INTERVAL) {
				lastRun = RANKING_CALCULATION_INTERVAL;
			}
		}
		
		if (Log.loggingDebug)
			log.debug("RankingPlugin lastRun="+lastRun+" RANKING_CALCULATION_PERIOD="+RANKING_CALCULATION_INTERVAL);
	
		//	Engine.registerStatusReportingPlugin(this);
		RankingCalculating rc = new RankingCalculating();
		task = Engine.getExecutor().scheduleAtFixedRate(rc, lastRun , RANKING_CALCULATION_INTERVAL, TimeUnit.MINUTES);
		if (Log.loggingDebug)
			log.debug("RankingPlugin activated");
	}

	// how to process incoming messages
	protected void registerHooks() {
		getHookManager().addHook(RankingClient.MSG_TYPE_GET_RANKING, new GetRankingHook());
		getHookManager().addHook(RankingClient.MSG_TYPE_GET_RANKING_LIST, new GetRankingListHook());
		
	}

	class GetRankingListHook implements Hook {
	        public boolean processMessage(Message msg, int flags) {
	        	ExtensionMessage message = (ExtensionMessage) msg;
	            OID playerOid = message.getSubject();
	           // int id = (int) message.getProperty("id");
	            log.debug("GetRankingListHook playerOid="+playerOid);
				TargetedExtensionMessage sMsg = new TargetedExtensionMessage(playerOid);
				sMsg.setExtensionType(RankingClient.EXTMSG_RANKING_LIST);
				int i = 0;
				for (AchievementSetting as : settings) {
					sMsg.setProperty("name"+i,as.getName());
					sMsg.setProperty("desc"+i,as.getDescription());
					sMsg.setProperty("id"+i,as.getId());
					i++;
				}
				sMsg.setProperty("num",i);
				Engine.getAgent().sendBroadcast(sMsg);
			
				log.debug("GetRankingListHook End");
	            return true;
	        }
	    }

	
	 class GetRankingHook implements Hook {
	        public boolean processMessage(Message msg, int flags) {
	        	ExtensionMessage message = (ExtensionMessage) msg;
	            OID playerOid = message.getSubject();
	            int id = (int) message.getProperty("id");
	            log.debug("GetRankingHook playerOid="+playerOid+" id="+id);
				TargetedExtensionMessage sMsg = new TargetedExtensionMessage(playerOid);
				sMsg.setExtensionType(RankingClient.EXTMSG_RANKING_UPDATE);
				int i = 0;
				ArrayList<Ranking> rankings = aDB.getRanking(id);
				for(Ranking r : rankings) {
					sMsg.setProperty("name"+i,aDB.getCharacterNameByOid(r.getSubjectOid()));
					sMsg.setProperty("value"+i,r.getValue());
					sMsg.setProperty("pos"+i,r.getPosition());
					i++;
				}
				sMsg.setProperty("id",id);
				sMsg.setProperty("num",i);
				Engine.getAgent().sendBroadcast(sMsg);
			
				log.debug("GetRankingHook End");
	            return true;
	        }
	    }

	public class RankingCalculating implements Runnable {

		public RankingCalculating() {

		}

		@Override
		public void run() {
			// Check user still has items
			Log.debug("RankingCalculating running");
			aDB.saveLastRankingRun();
			ArrayList<Ranking> rankings = new ArrayList<Ranking>();
			for (AchievementSetting as : settings) {
				log.debug("RankingCalculating "+as);
				ArrayList<CollectionData> arr = aDB.getCollectionData(as.getId(), -1, as.getValue());
				log.debug("RankingCalculating "+arr);
				int i = 0;
				for (CollectionData cd : arr) {
					log.debug("RankingCalculating "+cd);
					i++;
					Ranking r = new Ranking(cd.getSubjectOid(), (int) as.getId(), i, cd.getValue());
					rankings.add(r);
					/*if (as.getValue() <= i) {
						break;
					}*/
				}
			}
			
			aDB.saveRanking(rankings);
			Log.debug("RankingCalculating end ");
		}
	}

	ScheduledFuture task = null;
	ArrayList<AchievementSetting> settings = new ArrayList<AchievementSetting>();
	private AccountDatabase aDB;
	protected AuthDatabase authDB;

	private int RANKING_CALCULATION_INTERVAL = 1440;// in minutes
	private static final Logger log = new Logger("Ranking");
}
