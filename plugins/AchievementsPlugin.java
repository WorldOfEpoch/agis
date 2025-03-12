package atavism.agis.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import atavism.agis.database.AccountDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.database.MobDatabase;
import atavism.agis.objects.CollectionData;
import atavism.agis.objects.AchievementSetting;
import atavism.agis.objects.RankingData;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.messages.PropertyMessage;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;
import atavism.server.util.Logger;

/**
 * 
 */
public class AchievementsPlugin extends EnginePlugin {
	public AchievementsPlugin() {
		super("Achievements");
		setPluginType("Achievements");
	}
	public String getName() {
		return "Achievements";
	}
	public void onActivate() {
		log.debug("AchievementsPlugin activate");
		registerHooks();
		log.debug("AchievementsPlugin activate 1");
		
		MessageTypeFilter filter = new MessageTypeFilter();
		filter.addType(AchievementsClient.MSG_TYPE_RANGING_DATA);
		filter.addType(AchievementsClient.MSG_TYPE_GET_ACHIEVEMENTS);
		filter.addType(AchievementsClient.MSG_TYPE_SET_ACHIEVEMENTS_TITLE);
		filter.addType(AchievementsClient.MSG_TYPE_SET_ACHIEVEMENT);
		Engine.getAgent().createSubscription(filter, this);
		log.debug("AchievementsPlugin activate 2");
		
		MessageTypeFilter filter2 = new MessageTypeFilter();
		filter2.addType(LoginMessage.MSG_TYPE_LOGIN);
		filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
		Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);
		log.debug("AchievementsPlugin activate 3");
		
		aDB = new AccountDatabase(true);
		achivementsData = aDB.loadCollectionData();
		loadData() ;
		ContentDatabase ctDB = new ContentDatabase(false);
		String period = ctDB.loadGameSetting("COLLECTION_SAVE_INTERVAL");
		if (period != null) {
			COLLECTION_SAVE_INTERVAL = Integer.parseInt(period);
			Log.debug("Loaded Game Setting COLLECTION_SAVE_INTERVAL=" + COLLECTION_SAVE_INTERVAL);
		}
		SavingColectionData rc = new SavingColectionData();
		task = Engine.getExecutor().scheduleAtFixedRate(rc, COLLECTION_SAVE_INTERVAL , COLLECTION_SAVE_INTERVAL, TimeUnit.SECONDS);
		//ctDB.close();
		if (Log.loggingDebug)
			log.debug("AchievementsPlugin activated");
	
		
		//	Engine.registerStatusReportingPlugin(this);
 
	}

	// how to process incoming messages
	protected void registerHooks() {
		
		getHookManager().addHook(AchievementsClient.MSG_TYPE_RANGING_DATA, new RankingDataHook());

		getHookManager().addHook(AchievementsClient.MSG_TYPE_GET_ACHIEVEMENTS, new GetAchievementHook());
		getHookManager().addHook(AchievementsClient.MSG_TYPE_SET_ACHIEVEMENTS_TITLE, new SetAchievementTitleHook());
		getHookManager().addHook(AchievementsClient.MSG_TYPE_SET_ACHIEVEMENT,new AquireAchievementHook());
	}
	
	void loadData() {
		ContentDatabase ctDB = new ContentDatabase(false);
		settings = ctDB.GetAchievementsSetting();
		rankingsettings = ctDB.GetRankingsSetting();
	}

	protected void ReloadTemplates(Message msg) {
		Log.error("AchievementsPlugin ReloadTemplates Start");
		loadData();
		Log.error("AchievementsPlugin ReloadTemplates End");
	}
	class AquireAchievementHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			PropertyMessage message = (PropertyMessage) msg;
			OID playerOid = message.getSubject();
			int id = (int) message.getProperty("id");
			log.debug("AquireAchievementHook: Start");
			AchievementSetting a = null;
			for (AchievementSetting as : settings) {
				if (as.getId().equals(id)) {
					a = as;
					break;
				}
			}
			if (a == null) {
				log.error("AquireAchievementHook not found Achievement id=" + id);
				return true;
			}
			if (!achivementsData.containsKey(playerOid)) {
				achivementsData.put(playerOid, new HashMap<Short, ArrayList<CollectionData>>());

			}
			if (achivementsData.get(playerOid).containsKey(a.getType().shortValue())) {
				boolean found = false;
				for (CollectionData cd : achivementsData.get(playerOid).get(a.getType().shortValue())) {
					if (cd.getAchievementId().equals(a.getId())) {
						if (cd.getValue() < a.getValue())
							cd.setValue(a.getValue());
						cd.setAcquired(true);
						cd.setDirty(true);
						if (a.getBonuses().size() > 0)
							BonusClient.sendBonusAdd(playerOid, a.getBonuses(), "achievement" + a.getId());
						if (a.getStats().size() > 0)
							CombatClient.modifyStats(playerOid, a.getStats(), "achievement" + a.getId());
						found=true;
						break;
					}
				}
				if(!found) {
					CollectionData cd = new CollectionData(playerOid, a.getType().shortValue(), a.getValue(), a.getObjects());
					cd.setAchievementId(a.getId());
					cd.setAcquired(true);
					cd.setDirty(true);
					if (a.getBonuses().size() > 0)
						BonusClient.sendBonusAdd(playerOid, a.getBonuses(), "achievement" + a.getId());
					if (a.getStats().size() > 0)
						CombatClient.modifyStats(playerOid, a.getStats(), "achievement" + a.getId());
					log.debug("RankingDataHook Ranking add "+cd);
					achivementsData.get(playerOid).get(a.getType().shortValue()).add(cd);
				}
				
			} else {
				achivementsData.get(playerOid).put(a.getType().shortValue(), new ArrayList<CollectionData>());
				CollectionData cd = new CollectionData(playerOid, a.getType().shortValue(), a.getValue(), a.getObjects());
				cd.setAchievementId(a.getId());
				cd.setAcquired(true);
				cd.setDirty(true);
				if (a.getBonuses().size() > 0)
					BonusClient.sendBonusAdd(playerOid, a.getBonuses(), "achievement" + a.getId());
				if (a.getStats().size() > 0)
					CombatClient.modifyStats(playerOid, a.getStats(), "achievement" + a.getId());
				log.debug("RankingDataHook Ranking add "+cd);
				achivementsData.get(playerOid).get(a.getType().shortValue()).add(cd);
			}
			sendUpdate(playerOid);
			log.debug("AquireAchievementHook End");
			return true;
		}
	}

	class SetAchievementTitleHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			log.debug("SetAchievementTitleHook: Start");
			int id = (Integer) message.getProperty("id");
			 if(Log.loggingDebug)
					log.debug("SetAchievementTitleHook id="+id+" ad="+achivementsData);
			if (id < 1)
				EnginePlugin.setObjectPropertiesNoResponse(playerOid, WorldManagerClient.NAMESPACE, "title", "");
			else
			for (AchievementSetting as : settings) {
				 if(Log.loggingDebug)
						log.debug("SetAchievementTitleHook id="+id+" as="+as);
					
				if (as.getId().equals(id)) {
					if (achivementsData.containsKey(playerOid)) {
						if (achivementsData.get(playerOid).containsKey(as.getType().shortValue())) {
							for (CollectionData cd : achivementsData.get(playerOid).get(as.getType().shortValue())) {
								if (cd.getAchievementId() == as.getId()) {
									if (cd.getAcquired()) {
										EnginePlugin.setObjectPropertiesNoResponse(playerOid, WorldManagerClient.NAMESPACE, "title", as.getName());
										break;
									}else {
										log.debug("SetAchievementTitleHook id="+id+" cd="+cd+" not aquired");
									}
								}else {
									log.debug("SetAchievementTitleHook id="+id+" AchievementId != id");
								}
							}

						}else {
							log.debug("SetAchievementTitleHook id="+id+" achivementsData  don have type="+as.getType());
						}
					}else {
						log.debug("SetAchievementTitleHook id="+id+" achivementsData not contains player");
					}
				}else {
					log.debug("SetAchievementTitleHook id="+id+" ids  !=");
				}
			}
			log.debug("SetAchievementTitleHook End");
			return true;
		}
	}

	 class GetAchievementHook implements Hook {
	        public boolean processMessage(Message msg, int flags) {
	        	ExtensionMessage message = (ExtensionMessage) msg;
	            OID playerOid = message.getSubject();
	            log.debug("GetAchievementHook: Start");
	      
				sendUpdate(playerOid);
			//	sendBonusUpdate(playerOid);
				log.debug("GetAchievementHook End");
	            return true;
	        }
	    }
	void sendUpdate(OID playerOid) {
		TargetedExtensionMessage sMsg = new TargetedExtensionMessage(playerOid);
		sMsg.setExtensionType(AchievementsClient.EXTMSG_ACHIEVEMENTS_UPDATE);
		int i = 0;
		for (AchievementSetting as : settings) {
			if (achivementsData.containsKey(playerOid)) {
				if (achivementsData.get(playerOid).containsKey(as.getType().shortValue())) {
					boolean f = false;
					boolean f2 = false;
						for (CollectionData cd : achivementsData.get(playerOid).get(as.getType().shortValue())) {
						if (cd.getAchievementId() == as.getId()) {
							f2 = true;
							if (cd.getAcquired())
								f = true;
							sMsg.setProperty("value" + i, cd.getValue());
						}
					}
						if (f) {
							sMsg.setProperty("active" + i, true);
							log.debug("sendUpdate found Collection");
						} else {
							sMsg.setProperty("active" + i, false);
							log.debug("sendUpdate not found Collection");
						}
						if (f2) {
						} else {
							sMsg.setProperty("value" + i, 0);
							//log.debug("sendUpdate not found Collection");
						}
					
					
				} else {
					log.debug("sendUpdate not found type");
					sMsg.setProperty("active" + i, false);
					sMsg.setProperty("value" + i, 0);
				}
			} else {
				log.debug("sendUpdate not found playerOid "+playerOid);
				
				sMsg.setProperty("active" + i, false);
				sMsg.setProperty("value" + i, 0);
			}
			sMsg.setProperty("id" + i, as.getId());
			sMsg.setProperty("name" + i, as.getName());
			sMsg.setProperty("desc" + i, as.getDescription());
			sMsg.setProperty("max" + i, as.getValue());
			i++;
		}
		sMsg.setProperty("num", i);
		Engine.getAgent().sendBroadcast(sMsg);

	}

	class RankingDataHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			AchievementsClient.RankingDataMessage message = (AchievementsClient.RankingDataMessage) msg;
			log.debug("RankingDataHook: Start ");
			 if(Log.loggingDebug)
						log.debug("RankingDataHook: settings="+settings+" rankingsettings="+rankingsettings);
			LinkedList<RankingData> rd = message.getRankingDatas();
			ArrayList<OID> plys = new ArrayList<OID>();
			for (RankingData r : rd) {
				if(!plys.contains(r.getSubjectOid()))
					plys.add(r.getSubjectOid());
				 if(Log.loggingDebug)
						log.debug("RankingDataHook: " + r);
				if (!achivementsData.containsKey(r.getSubjectOid())) {
					achivementsData.put(r.getSubjectOid(), new HashMap<Short, ArrayList<CollectionData>>());
				}

				if (r.getType().equals(AchievementsClient.GEAR_SCORE)) {
					log.debug("RankingDataHook: GEAR_SCORE");
					if (!achivementsData.get(r.getSubjectOid()).containsKey(r.getType())) {
						achivementsData.get(r.getSubjectOid()).put(r.getType(), new ArrayList<CollectionData>());
					}
					for (AchievementSetting as : settings) {
						 if(Log.loggingDebug)
									log.debug("RankingDataHook Achievement "+r.getType()+" "+as);
						if (as.getType().equals((int) r.getType())) {
							boolean f = false;
							for (CollectionData cd : achivementsData.get(r.getSubjectOid()).get(r.getType())) {
								 if(Log.loggingDebug)
											log.debug("RankingDataHook Achievement "+cd);

								if (cd.getAchievementId().equals(as.getId())) {
									 if(Log.loggingDebug)
												log.debug("RankingDataHook Achievement equals " + cd);
									cd.setValue(r.getValue());
									if (cd.getValue() >= as.getValue() && !cd.getAcquired()) {
										if (as.getBonuses().size() > 0)
											BonusClient.sendBonusAdd(r.getSubjectOid(), as.getBonuses(), "achievement" + as.getId());
										if (as.getStats().size() > 0)
											CombatClient.modifyStats(r.getSubjectOid(), as.getStats(), "achievement" + as.getId());
										cd.setAcquired(true);
									}

									cd.setDirty(true);
									f = true;
								} else if (cd.getAchievementId() == -1) {
									/*if (cd.getObjects().equals(as.getObjects())) {
										cd.setAchievementId(as.getId());
										cd.setDirty(true);
										f = true;
									}*/
								}
							}
							 if(Log.loggingDebug)
									log.debug("RankingDataHook Achievement f="+f);
								if (!f && r.getValue() > 0) {
								CollectionData cd = new CollectionData(r.getSubjectOid(), r.getType(), r.getValue(), as.getObjects());
								cd .setAchievementId(as.getId());
								if (cd.getValue() >= as.getValue() && !cd.getAcquired()) {
									if (as.getBonuses().size() > 0)
										BonusClient.sendBonusAdd(r.getSubjectOid(), as.getBonuses(), "achievement" + as.getId());
									if (as.getStats().size() > 0)
										CombatClient.modifyStats(r.getSubjectOid(), as.getStats(), "achievement" + as.getId());
									cd.setAcquired(true);
								}
								cd.setDirty(true);
								 if(Log.loggingDebug)
										log.debug("RankingDataHook Achievement add "+cd);
								achivementsData.get(r.getSubjectOid()).get(r.getType()).add(cd);
							}
						}else {
							 if(Log.loggingDebug)
									log.debug("RankingDataHook Achievement "+r.getType()+" != "+as.getType());
						}
					}
					for (AchievementSetting as : rankingsettings) {
						 if(Log.loggingDebug)
								log.debug("RankingDataHook Ranking "+r.getType()+" "+as);
						if (as.getType().equals((int) r.getType())) {
							boolean f1 = false;
							for (String s : as.getObjectsArray()) {
								if (s.length() > 0 && Integer.parseInt(s) == r.getValue()) {
									f1 = true;
								}
							}
							if(as.getObjects().length() == 0)
								f1 = true;
							 if(Log.loggingDebug)
										log.debug("RankingDataHook Ranking f1="+f1);
							if (f1) {
								boolean f2 = false;
								for (CollectionData cd : achivementsData.get(r.getSubjectOid()).get(r.getType())) {
									 if(Log.loggingDebug)
											log.debug("RankingDataHook Ranking "+cd);
									if (cd.getRankingId().equals(as.getId())) {
										 if(Log.loggingDebug)
													log.debug("RankingDataHook Ranking equals" + cd);
										cd.setValue( r.getValue());
										cd.setDirty(true);
										f2 = true;
									} else if (cd.getRankingId() == -1) {
										/*if (cd.getObjects().equals(as.getObjects())) {
											log.debug("RankingDataHook Ranking id -1 equals" + cd);
										//	cd.setValue(cd.getValue() + 1);
											cd.setRankingId(as.getId());
											cd.setDirty(true);
											f2 = true;
										}*/
									}
								}
								 if(Log.loggingDebug)
										log.debug("RankingDataHook Ranking f2="+f2);
									if (!f2 && r.getValue() > 0) {
									CollectionData cd = new CollectionData(r.getSubjectOid(), r.getType(), r.getValue(), as.getObjects());
									cd.setRankingId(as.getId());
									cd.setDirty(true);
									 if(Log.loggingDebug)
												log.debug("RankingDataHook Ranking add "+cd);
									achivementsData.get(r.getSubjectOid()).get(r.getType()).add(cd);
								}
							}
						}else {
							 if(Log.loggingDebug)
										log.debug("RankingDataHook Ranking "+r.getType()+" != "+as.getType());
						}
					}
				} else if (r.getType().equals(AchievementsClient.EXPERIENCE) || r.getType().equals(AchievementsClient.LOOT)) {
					log.debug("RankingDataHook: EXPERIENCE || HARVESTING");
					
					if (!achivementsData.get(r.getSubjectOid()).containsKey(r.getType())) {
						achivementsData.get(r.getSubjectOid()).put(r.getType(), new ArrayList<CollectionData>());
					}
					for (AchievementSetting as : settings) {
						 if(Log.loggingDebug)
								log.debug("RankingDataHook Achievement " + r.getType() + " " + as);
						if (as.getType().equals((int) r.getType())) {
							boolean f2 = false;
							for (CollectionData cd : achivementsData.get(r.getSubjectOid()).get(r.getType())) {
								 if(Log.loggingDebug)
										log.debug("RankingDataHook Achievement " + cd);

								if (cd.getAchievementId().equals(as.getId())) {
									 if(Log.loggingDebug)
											log.debug("RankingDataHook Achievement equals " + cd);
									cd.setValue(cd.getValue() + r.getValue());
									if (cd.getValue() >= as.getValue() && !cd.getAcquired()) {
										if (as.getBonuses().size() > 0)
											BonusClient.sendBonusAdd(r.getSubjectOid(), as.getBonuses(), "achievement" + as.getId());
										if (as.getStats().size() > 0)
											CombatClient.modifyStats(r.getSubjectOid(), as.getStats(), "achievement" + as.getId());
										cd.setAcquired(true);
									}

									cd.setDirty(true);
									f2 = true;
								} else if (cd.getAchievementId() == -1) {
								/*	if (cd.getObjects().equals(as.getObjects())) {
										cd.setAchievementId(as.getId());
										cd.setDirty(true);
										f2 = true;
									}*/
								}
								
							}
							 if(Log.loggingDebug)
									log.debug("RankingDataHook Achievement f2="+f2);
							if (!f2 && r.getValue() > 0) {
								CollectionData cd = new CollectionData(r.getSubjectOid(), r.getType(), r.getValue(), as.getObjects());
								cd.setAchievementId(as.getId());
								if (cd.getValue() >= as.getValue() && !cd.getAcquired()) {
									if (as.getBonuses().size() > 0)
										BonusClient.sendBonusAdd(r.getSubjectOid(), as.getBonuses(), "achievement" + as.getId());
									if (as.getStats().size() > 0)
										CombatClient.modifyStats(r.getSubjectOid(), as.getStats(), "achievement" + as.getId());
									cd.setAcquired(true);
								}
								cd.setDirty(true);
								 if(Log.loggingDebug)
										log.debug("RankingDataHook Achievement add " + cd);
								achivementsData.get(r.getSubjectOid()).get(r.getType()).add(cd);
							}
						}else {
							 if(Log.loggingDebug)
										log.debug("RankingDataHook Achievement "+r.getType()+" != "+as.getType());
						}
					}
					for (AchievementSetting as : rankingsettings) {
						 if(Log.loggingDebug)
									log.debug("RankingDataHook Ranking "+r.getType()+" "+as);
						if (as.getType().equals((int) r.getType())) {
							boolean f1 = false;
							for (String s : as.getObjectsArray()) {
								if (s.length() > 0 && Integer.parseInt(s) == r.getValue()) {
									f1 = true;
								}
							}
							if(as.getObjects().length() == 0)
								f1 = true;
							
							 if(Log.loggingDebug)
										log.debug("RankingDataHook Ranking f1="+f1);
								if (f1) {
								boolean f2 = false;
								for (CollectionData cd : achivementsData.get(r.getSubjectOid()).get(r.getType())) {
									 if(Log.loggingDebug)
												log.debug("RankingDataHook Ranking "+cd);

									if (cd.getRankingId().equals(as.getId())) {
										 if(Log.loggingDebug)
												log.debug("RankingDataHook Ranking equals" + cd);
										cd.setValue(cd.getValue() + r.getValue());
										cd.setDirty(true);
										f2 = true;
									} else if (cd.getRankingId() == -1) {
										/*if (cd.getObjects().equals(as.getObjects())) {
											log.debug("RankingDataHook Ranking id -1 equals" + cd);
										//	cd.setValue(cd.getValue() + 1);
											cd.setRankingId(as.getId());
											cd.setDirty(true);
											f2 = true;
										}*/
									}
									
								}
								 if(Log.loggingDebug)
										log.debug("RankingDataHook Ranking f2="+f2);
									if (!f2 && r.getValue() > 0) {
									CollectionData cd = new CollectionData(r.getSubjectOid(), r.getType(), r.getValue(), as.getObjects());
									cd.setRankingId(as.getId());
									cd.setDirty(true);
									 if(Log.loggingDebug)
											log.debug("RankingDataHook Ranking add "+cd);
										achivementsData.get(r.getSubjectOid()).get(r.getType()).add(cd);
								}
							}
						}else {
							 if(Log.loggingDebug)
									log.debug("RankingDataHook Ranking "+r.getType()+" != "+as.getType());
						}
					}
					
				} else if (r.getType().equals(AchievementsClient.KILL)|| r.getType().equals(AchievementsClient.HARVEST) || r.getType().equals(AchievementsClient.FINAL_BLOW) || r.getType().equals(AchievementsClient.USE_ABILITY)
						|| r.getType().equals(AchievementsClient.CRAFT)) {
					log.debug("RankingDataHook: KILLING || FINAL_BLOW || USE_ABILITY || CRAFTING");
					if (!achivementsData.get(r.getSubjectOid()).containsKey(r.getType())) {
						achivementsData.get(r.getSubjectOid()).put(r.getType(), new ArrayList<CollectionData>());
					}
					for (AchievementSetting as : settings) {
						 if(Log.loggingDebug)
									log.debug("RankingDataHook Achievement " + r.getType() + " " + as);
						if (as.getType().equals((int) r.getType())) {
							boolean f1 = false;
							for (String s : as.getObjectsArray()) {
								if (s.length() > 0 && Integer.parseInt(s) == r.getValue()) {
									f1 = true;
								}
							}
							if (as.getObjects().length() == 0)
								f1 = true;

							 if(Log.loggingDebug)
									log.debug("RankingDataHook Achievement f1=" + f1);
							if (f1) {
								boolean f2 = false;
								for (CollectionData cd : achivementsData.get(r.getSubjectOid()).get(r.getType())) {
									 if(Log.loggingDebug)
												log.debug("RankingDataHook Achievement " + cd);
									
									if (cd.getAchievementId().equals(as.getId())) {
										 if(Log.loggingDebug)
													log.debug("RankingDataHook Achievement equals " + cd);
										cd.setValue(cd.getValue() + 1);
										if (cd.getValue() >= as.getValue() && !cd.getAcquired()) {
											if (as.getBonuses().size() > 0)
												BonusClient.sendBonusAdd(r.getSubjectOid(), as.getBonuses(), "achievement" + as.getId());
											if (as.getStats().size() > 0)
												CombatClient.modifyStats(r.getSubjectOid(), as.getStats(), "achievement" + as.getId());
											cd.setAcquired(true);
										}

										cd.setDirty(true);
										f2 = true;
									} else if (cd.getAchievementId() == -1) {
									/*	if (cd.getObjects().equals(as.getObjects())) {
											cd.setAchievementId(as.getId());
											cd.setDirty(true);
											f2 = true;
										}*/
									}
								}
								 if(Log.loggingDebug)
											log.debug("RankingDataHook Achievement f2=" + f2);

								if (!f2) {
									CollectionData cd = new CollectionData(r.getSubjectOid(), r.getType(), 1, as.getObjects());
									cd.setAchievementId(as.getId());
									if (cd.getValue() >= as.getValue() && !cd.getAcquired()) {
										if (as.getBonuses().size() > 0)
											BonusClient.sendBonusAdd(r.getSubjectOid(), as.getBonuses(), "achievement" + as.getId());
										if (as.getStats().size() > 0)
											CombatClient.modifyStats(r.getSubjectOid(), as.getStats(), "achievement" + as.getId());
										cd.setAcquired(true);
									}
									cd.setDirty(true);
									 if(Log.loggingDebug)
												log.debug("RankingDataHook Achievement add " + cd);
									achivementsData.get(r.getSubjectOid()).get(r.getType()).add(cd);
								}
							}
						} else {
							 if(Log.loggingDebug)
										log.debug("RankingDataHook Achievement " + r.getType() + " != " + as.getType());
						}
					}
					for (AchievementSetting as : rankingsettings) {
						 if(Log.loggingDebug)
								log.debug("RankingDataHook Ranking "+r.getType()+" "+as);
						if (as.getType().equals((int) r.getType())) {
							boolean f1 = false;
							for (String s : as.getObjectsArray()) {
								if (s.length() > 0 && Integer.parseInt(s) == r.getValue()) {
									f1 = true;
								}
							}
							if(as.getObjects().length() == 0)
								f1 = true;
							 if(Log.loggingDebug)
									log.debug("RankingDataHook Ranking f1="+f1);
							
							if (f1) {
								boolean f2 = false;
								for (CollectionData cd : achivementsData.get(r.getSubjectOid()).get(r.getType())) {
									 if(Log.loggingDebug)
												log.debug("RankingDataHook Ranking " + cd);

									if (cd.getRankingId().equals(as.getId())) {
										 if(Log.loggingDebug)
												log.debug("RankingDataHook Ranking equals" + cd);
										cd.setValue(cd.getValue() + 1);
										cd.setDirty(true);
										f2 = true;
									} else if (cd.getRankingId() == -1) {
									/*	if (cd.getObjects().equals(as.getObjects())) {
											log.debug("RankingDataHook Ranking id -1 equals" + cd);
										//	cd.setValue(cd.getValue() + 1);
											cd.setRankingId(as.getId());
											cd.setDirty(true);
											f2 = true;
										}*/
									}
								}
								 if(Log.loggingDebug)
											log.debug("RankingDataHook Ranking f2="+f2);
								
								if (!f2) {
									CollectionData cd = new CollectionData(r.getSubjectOid(), r.getType(), 1, as.getObjects());
									cd.setRankingId(as.getId());
									cd.setDirty(true);
									 if(Log.loggingDebug)
											log.debug("RankingDataHook Ranking add "+cd);
									achivementsData.get(r.getSubjectOid()).get(r.getType()).add(cd);
								}
							}
						}else {
							 if(Log.loggingDebug)
									log.debug("RankingDataHook Ranking "+r.getType()+" != "+as.getType());
						}
					}
				}else {
					 if(Log.loggingDebug)
							log.debug("RankingDataHook: unknown type "+r.getType());
				}
				//ExtendedCombatMessages.sendAnouncementSpecialMessage(r.getSubjectOid(), AnouncementMessage, "");
				//BonusClient.sendBonusAdd(state.getSourceOid(), getBonuses(),"BonusEffect"+getID());
			}
			// if(Log.loggingDebug)
			//		log.debug("RankingDataHook achivementsData=" +achivementsData);
			for(OID plyOid : plys) {
				sendUpdate(plyOid);
			}
			
			log.debug("RankingDataHook: End");

			return true;
		}
	}
	
	public class SavingColectionData implements Runnable {

		public SavingColectionData() {

		}

		@Override
		public void run() {
			// Check user still has items
			Log.debug("SavingColectionData running");
			for (OID oid : achivementsData.keySet()) {
				 if(Log.loggingDebug)
							Log.debug("SavingColectionData oid="+oid);
				for (Short s : achivementsData.get(oid).keySet()) {
					 if(Log.loggingDebug)
							Log.debug("SavingColectionData type="+s);
					aDB.saveCollectionData(achivementsData.get(oid).get(s));
				}
			}
			Log.debug("SavingColectionData end");
		}
	}

	ArrayList<AchievementSetting> settings = new ArrayList<AchievementSetting>();
	ArrayList<AchievementSetting> rankingsettings = new ArrayList<AchievementSetting>();
	ScheduledFuture task = null;
    private Map<OID, HashMap<Short, ArrayList<CollectionData>>> achivementsData = new ConcurrentHashMap<OID, HashMap<Short, ArrayList<CollectionData>>>();
    protected AccountDatabase aDB;
    private int COLLECTION_SAVE_INTERVAL = 60;//secound
    
    private static final Logger log = new Logger("Achievement");
    public static String AnouncementMessage = "You have Achievement";
}
