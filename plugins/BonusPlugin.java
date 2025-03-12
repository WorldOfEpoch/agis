package atavism.agis.plugins;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.io.Serializable;

import atavism.agis.database.AuthDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.database.ItemDatabase;
import atavism.agis.objects.BonusSettings;
import atavism.agis.objects.GlobalEventSettings;
import atavism.agis.objects.VipData;
import atavism.agis.objects.VipLevel;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.plugins.ObjectManagerClient;
import atavism.agis.plugins.BonusClient.AddBonusMessage;
import atavism.agis.plugins.BonusClient.ExtendVipMessage;
import atavism.agis.plugins.BonusClient.RemoveBonusMessage;
import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.Log;
import atavism.server.util.Logger;

public class BonusPlugin extends EnginePlugin {
	public BonusPlugin() {
		super("Bonus");
		setPluginType("Bonus");
	}

	public String getName() {
		return "Bonus";
	}

	public void onActivate() {
		log.debug("BonusPlugin activate");
		registerHooks();
		log.debug("BonusPlugin activate 1");

		MessageTypeFilter filter = new MessageTypeFilter();
		filter.addType(AgisWorldManagerClient.MSG_TYPE_SERVER_TIME);
		filter.addType(BonusClient.MSG_TYPE_BONUS_ADD);
		filter.addType(BonusClient.MSG_TYPE_BONUS_REMOVE);
		filter.addType(BonusClient.MSG_TYPE_EXTEND_VIP);
		filter.addType(BonusClient.MSG_TYPE_GET_VIP);
		filter.addType(BonusClient.MSG_TYPE_GET_ALL_VIP);
		Engine.getAgent().createSubscription(filter, this);
		log.debug("BonusPlugin activate 2");

		MessageTypeFilter filter2 = new MessageTypeFilter();
		filter2.addType(LoginMessage.MSG_TYPE_LOGIN);
		filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
		Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);
		log.debug("BonusPlugin activate 3");

		authDB = new AuthDatabase();
		 loadData();
		//ctDB.close();
		if (Log.loggingDebug)
			log.debug("BonusPlugin activated");

		// Log.debug("Registering Bonus plugin");
		// Engine.registerStatusReportingPlugin(this);

	}

	// how to process incoming messages
	protected void registerHooks() {

		getHookManager().addHook(BonusClient.MSG_TYPE_BONUS_ADD, new AddBonusHook());
		getHookManager().addHook(BonusClient.MSG_TYPE_BONUS_REMOVE, new RemoveBonusHook());
		getHookManager().addHook(BonusClient.MSG_TYPE_EXTEND_VIP, new ExtendVipHook());
		getHookManager().addHook(BonusClient.MSG_TYPE_GET_VIP, new GetVipHook());
		getHookManager().addHook(BonusClient.MSG_TYPE_GET_ALL_VIP, new GetAllVipHook());
		getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
		getHookManager().addHook(AgisWorldManagerClient.MSG_TYPE_SERVER_TIME, new ServerTimeHook());
		
	}

	
	void loadData() {
		ContentDatabase ctDB = new ContentDatabase(false);
		log.debug("BonusPlugin activate 4");

		String vipPerCharacter = ctDB.loadGameSetting("VIP_PER_CHARACTER");
		if (vipPerCharacter != null) {
			VIP_PER_CHARACTER = Boolean.parseBoolean(vipPerCharacter);
			Log.debug("Loaded Game Setting VIP_PER_CHARACTER=" + VIP_PER_CHARACTER);
		}
		String viptime = ctDB.loadGameSetting("VIP_USE_TIME");
		if (viptime != null) {
			VIP_USE_TIME = Boolean.parseBoolean(viptime);
			Log.debug("Loaded Game Setting VIP_USE_TIME=" + VIP_USE_TIME);
		}
	
		globalEventsDefinition = ctDB.loadGlobalEvents();
	}

	protected void ReloadTemplates(Message msg) {
		Log.debug("BonusPlugin ReloadTemplates Start");
			loadData();
		Log.debug("BonusPlugin ReloadTemplates End");
	}
    
	
	
	/**
	 * Handles the ServerTimeMessage. Passes the server time to the spawn generator
	 * so it can enable or disable any spawn generators that are affected by the
	 * change in time.
	 * 
	 * @author Andrew Harrison
	 *
	 */
	class ServerTimeHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			AgisWorldManagerClient.ServerTimeMessage tMsg = (AgisWorldManagerClient.ServerTimeMessage) msg;
			if(Log.loggingDebug)	log.debug("TIME: got server time message with hour: " + tMsg.getHour());
			globalEventsUpdate(tMsg.getHour(), tMsg.getMinute(), tMsg.getDay(), tMsg.getMonth(), tMsg.getYear());
			return true;
		}
	}

	
	public void globalEventsUpdate(int wHour, int wMinute, int wDay, int wMonth, int wYear) {
		try {
			log.debug("globalEventsUpdate");
			boolean sendUpdate = false;
			Calendar currCal = Calendar.getInstance();
			if(Log.loggingDebug)Log.debug("globalEventsUpdate: server ? "+currCal.getTime());
			currCal.set(wYear, wMonth, wDay, wHour, wMinute);
			if(Log.loggingDebug)Log.debug("globalEventsUpdate: Weather Time "+currCal.getTime());
			for (GlobalEventSettings ges : globalEventsDefinition.values()) {
				Calendar startCal = Calendar.getInstance();
				startCal.set(ges.getStartYear() == -1 ? wYear : ges.getStartYear(), ges.getStartMonth() == -1 ? wMonth : ges.getStartMonth(), ges.getStartDay() == -1 ? wDay : ges.getStartDay(),
						ges.getStartHour() == -1 ? wHour : ges.getStartHour(), ges.getStartMinute() == -1 ? wMinute : ges.getStartMinute());

				Calendar endCal = Calendar.getInstance();
				endCal.set(ges.getEndYear() == -1 ? wYear : ges.getEndYear(), ges.getEndMonth() == -1 ? wMonth : ges.getEndMonth(), ges.getEndDay() == -1 ? wDay : ges.getEndDay(),
						ges.getEndHour() == -1 ? wHour : ges.getEndHour(), ges.getEndMinute() == -1 ? wMinute : ges.getEndMinute());
				if(Log.loggingDebug)log.debug("globalEventsUpdate ges " + ges + " currentTime=" + currCal + " " + startCal + " " + endCal + " " + currCal.compareTo(startCal) + " " + currCal.compareTo(endCal));
				if (currCal.compareTo(startCal) >= 0 && currCal.compareTo(endCal) < 0) {
					if(Log.loggingDebug)	log.debug("globalEventsUpdate in time alredy contains? " + activeGlobalEvents.contains(ges.getId()));
					// Apply
					if (!activeGlobalEvents.contains(ges.getId())) {
						sendUpdate = true;
						activeGlobalEvents.add(ges.getId());
					}
				} else {
					if(Log.loggingDebug)	log.debug("globalEventsUpdate out time alredy contains? " + activeGlobalEvents.contains(ges.getId()));
					// Remove
					if (activeGlobalEvents.contains(ges.getId())) {
						sendUpdate = true;
						activeGlobalEvents.remove(ges.getId());
					}

				}
			}
			if (sendUpdate) {
				HashMap<String, BonusSettings> bonuses = new HashMap<String, BonusSettings>();
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "GlobalEventList");
				int count = 0;
				for (int id : activeGlobalEvents) {
					GlobalEventSettings ges = globalEventsDefinition.get(id);
					for (BonusSettings bs : ges.getBonuses()) {
						if (!bonuses.containsKey(bs.getSettingCode()))
							bonuses.put(bs.getSettingCode(), new BonusSettings(bs.getSettingCode()));
						if (Log.loggingDebug)
							log.debug("globalEventsUpdate: bs=" + bs);
						bonuses.get(bs.getSettingCode()).SetValue(bonuses.get(bs.getSettingCode()).GetValue() + bs.GetValue());
						bonuses.get(bs.getSettingCode()).SetValuePercentage(bonuses.get(bs.getSettingCode()).GetValuePercentage() + bs.GetValuePercentage());
					}
					props.put("id" + count, ges.getId());
					props.put("name" + count, ges.getName());
					props.put("desc" + count, ges.getDescription());
				//	props.put("icon" + count, ges.getIconData());
					count++;

				}
				props.put("num", count);
				BonusClient.sendGlobalEventBonusesUpdate(bonuses);

				if (Log.loggingDebug)
					log.debug("globalEventsUpdate send:" + props);
				//Send Global Event Update to active players 
				Iterator<OID> plys = bonusesArray.keySet().iterator();
				while (plys.hasNext()) {
					OID playerOid = plys.next();
					TargetedExtensionMessage tmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
					Engine.getAgent().sendBroadcast(tmsg);

				}
			}

		} catch (Exception e) {
			log.exception("globalEventsUpdate:", e);
			e.printStackTrace();
		}

		hour = wHour;
		minute = wMinute;
		log.debug("globalEventsUpdate End");
	}
	
	class AddBonusHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			AddBonusMessage message = (AddBonusMessage) msg;
			OID playerOid = message.getSubject();
			ArrayList<BonusSettings> _bss = message.getBonuses();
			String obj = message.getObj();
			if (Log.loggingDebug)
				log.debug("AddBonusHook _bss=" + _bss);
			if (_bss.size() == 0) {
				log.error("AddBonusHook list size is 0 break");
				return true;
			}

			addBonuses(playerOid, _bss, obj);

			sendBonusUpdate(playerOid);
			authDB.savePlayerBonuses(playerOid, bonusesArray.get(playerOid));
			return true;
		}
	}

	void addBonuses(OID playerOid, ArrayList<BonusSettings> list, String obj) {
		if (Log.loggingDebug)
			log.debug("addbonuses playerOid=" + playerOid + " list=" + list+" object "+obj);
		if (!bonusesArray.containsKey(playerOid)) {
			if (Log.loggingDebug)
				log.debug("addbonuses playerOid=" + playerOid + " is not in bonusesArray");
			bonusesArray.put(playerOid, authDB.loadPlayerBonuses(playerOid));
		}
		for (BonusSettings _bs : list) {
			BonusSettings bs = _bs.Clone();
			bs.setObj(obj);
			if (Log.loggingDebug)
				log.debug("addbonuses bs=" + bs);
			if (!bonusesArray.get(playerOid).containsKey(bs.getSettingCode())) {
				if (Log.loggingDebug)
					log.debug("addbonuses playerOid=" + playerOid + " code=" + bs.getSettingCode() + " is not in array for player");
				bonusesArray.get(playerOid).put(bs.getSettingCode(), new ArrayList<BonusSettings>());
				bonusesArray.get(playerOid).get(bs.getSettingCode()).add(bs);
			} else {
				boolean added = false;
				for (BonusSettings bss : bonusesArray.get(playerOid).get(bs.getSettingCode())) {
					if (bss.getObj().equals(obj)) {
						bss.SetValue(bs.GetValue());
						bss.SetValuePercentage(bs.GetValuePercentage());
						bss.setDirty(true);
						added = true;
					}
				}
				if (Log.loggingDebug)
					log.debug("addbonuses playerOid=" + playerOid + " bonuse " +bs.getSettingCode()+ " edited " +added );
				if (!added) {
					bonusesArray.get(playerOid).get(bs.getSettingCode()).add(bs);
				}
			}
		}
		if (Log.loggingDebug)
			log.debug("addbonuses END playerOid=" + playerOid + " bonusesArray=" + bonusesArray);

	}

	class RemoveBonusHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			RemoveBonusMessage message = (RemoveBonusMessage) msg;
			OID playerOid = message.getSubject();
			ArrayList<BonusSettings> _bss = message.getBonuses();
			String obj = message.getObj();
			ArrayList<Integer> toDelete = removeBonuses(playerOid, _bss, obj);

			sendBonusUpdate(playerOid);
			authDB.savePlayerBonuses(playerOid, bonusesArray.get(playerOid), toDelete);
			return true;
		}
	}

	ArrayList<Integer> removeBonuses(OID playerOid, ArrayList<BonusSettings> list, String obj) {
		if (Log.loggingDebug)
			log.debug("removeBonuses playerOid=" + playerOid);
		ArrayList<Integer> toDelete = new ArrayList<Integer>();
		if (!bonusesArray.containsKey(playerOid)) {
			bonusesArray.put(playerOid, authDB.loadPlayerBonuses(playerOid));
		}

		for (String s : bonusesArray.get(playerOid).keySet()) {
			ArrayList<BonusSettings> rem_bs = new ArrayList<BonusSettings>();
			for (BonusSettings bbs : bonusesArray.get(playerOid).get(s)) {
				if (bbs.getObj().equals(obj)) {
					rem_bs.add(bbs);
				}
			}
			for (BonusSettings bbs : rem_bs) {
				if(bbs.getId()>0) {
					toDelete.add(bbs.getId());
				}
				bonusesArray.get(playerOid).get(s).remove(bbs);
			}
		}

		if (Log.loggingDebug)
			log.debug("removeBonuses END playerOid=" + playerOid + " to Delete "+toDelete);
		return toDelete;
	}

	void sendBonusUpdate(OID playerOid) {
		HashMap<String, BonusSettings> bonuses = new HashMap<String, BonusSettings>();

		if (bonusesArray.containsKey(playerOid)) {
			for (String code : bonusesArray.get(playerOid).keySet()) {
				bonuses.put(code, new BonusSettings(code));
				ArrayList<BonusSettings> bss = new ArrayList<BonusSettings>(bonusesArray.get(playerOid).get(code));
				for (BonusSettings bs : bss) {
					if (Log.loggingDebug)
						log.debug("sendBonusUpdate: bs=" + bs);
					bonuses.get(code).SetValue(bonuses.get(code).GetValue() + bs.GetValue());
					bonuses.get(code).SetValuePercentage(bonuses.get(code).GetValuePercentage() + bs.GetValuePercentage());
				}
			}
			if (Log.loggingDebug)
				log.debug("sendBonusUpdate: bonuses=" + bonuses);

			BonusClient.sendBonusesUpdate(playerOid, bonuses);
		} else {
			if (Log.loggingDebug)
				log.debug("Dont have bonuses for player " + playerOid);
		}
	}

	class GetVipHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.ACCOUNT_PROPERTY);
			VipData vipData = null;
			if (VIP_PER_CHARACTER) {
				vipData = authDB.getVipCharacter(playerOid);
			} else {
				vipData = authDB.getVipAccount(accountID);
			}
			long now = System.currentTimeMillis();
			VipLevel vip = null;
			long vipmpoints = 0l;
			if (vipData.getLevel() > 0) {
				vip = ObjectManagerClient.getVipLevel(vipData.getLevel());
				vipmpoints = (long) vip.getPointsMax();
			}

			TargetedExtensionMessage sMsg = new TargetedExtensionMessage(playerOid);
			sMsg.setExtensionType(BonusClient.EXTMSG_VIP_UPDATE);
			if (vip != null) {
				sMsg.setProperty("lev", vipData.getLevel());
				sMsg.setProperty("points", vipData.getPoints());
				sMsg.setProperty("mpoints", vipmpoints);
				// sMsg.setProperty("time",vipData.getExpire());
				sMsg.setProperty("name", vip.getName());
				sMsg.setProperty("desc", vip.getDescription());
				sMsg.setProperty("time", vipData.getExpire() > 0 ? vipData.getExpire() - now : vipData.getExpire());
				int b = 0;
				for (BonusSettings bs : vip.GetSettings().values()) {
					sMsg.setProperty("b" + b, bs.getSettingName() + "|" + bs.GetValue() + "|" + bs.GetValuePercentage());
					b++;
				}
				sMsg.setProperty("bNum", b);
			} else {
				sMsg.setProperty("lev", 0);
				sMsg.setProperty("points", 0l);
				sMsg.setProperty("mpoints", 0l);
				// sMsg.setProperty("time",vipData.getExpire());
				sMsg.setProperty("name", "");
				sMsg.setProperty("desc", "");
				sMsg.setProperty("time", 0l);
				sMsg.setProperty("bNum", 0);
			}
			sMsg.setProperty("bNum", 0);
			sMsg.setProperty("vut", VIP_USE_TIME);
			Engine.getAgent().sendBroadcast(sMsg);

			sendBonusUpdate(playerOid);
			log.debug("GetVipHook End");
			return true;
		}
	}

	class GetAllVipHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			OID playerOid = message.getSubject();
			
			log.debug("GetAllVipHook Start");
			ItemDatabase iDB = new ItemDatabase(false);
			HashMap<Integer, VipLevel> levels = iDB.loadVipLevels();
			//iDB.close();
			TargetedExtensionMessage sMsg = new TargetedExtensionMessage(playerOid);
			sMsg.setExtensionType(BonusClient.EXTMSG_ALL_VIP_UPDATE);
			int v = 0;
			for (Integer i : levels.keySet()) {
				sMsg.setProperty("lev" + v, levels.get(i).getLevel());
				// sMsg.setProperty("points", vipData.getPoints());
				// sMsg.setProperty("mpoints", vipmpoints);
				// sMsg.setProperty("time",vipData.getExpire());
				sMsg.setProperty("name" + v, levels.get(i).getName());
				sMsg.setProperty("desc" + v, levels.get(i).getDescription());
				// sMsg.setProperty("time", vipData.getExpire() - now);

				int b = 0;
				for (BonusSettings bs : levels.get(i).GetSettings().values()) {
					sMsg.setProperty("b" + v + "_" + b, bs.getSettingName() + "|" + bs.GetValue() + "|" + bs.GetValuePercentage());
					b++;
				}
				sMsg.setProperty("bNum" + v, b);
				v++;
			}
			sMsg.setProperty("levNum", v);
			sMsg.setProperty("vut", VIP_USE_TIME);
			Engine.getAgent().sendBroadcast(sMsg);

			// sendBonusUpdate(playerOid);
			log.debug("GetAllVipHook End");
			return true;
		}
	}

	class ExtendVipHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtendVipMessage message = (ExtendVipMessage) msg;
			OID playerOid = message.getPlayerOid();
			int points = message.getPoints();
			long time = message.getTime();
			OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.ACCOUNT_PROPERTY);
			ArrayList<Integer> toDelete = new ArrayList<Integer>();
			VipData vipData = null;
			if (VIP_PER_CHARACTER) {
				vipData = authDB.getVipCharacter(playerOid);
			} else {
				vipData = authDB.getVipAccount(accountID);
			}
			long now = System.currentTimeMillis();
			long vipmpoints = 0;
			if (Log.loggingDebug)
				log.debug("ExtendVipHook: playerOid=" + playerOid + " points=" + points + " time=" + time + " accountID=" + accountID + " vipLevel=" + vipData.getLevel() + " vipExpire=" + vipData.getExpire()
						+ " vippoints=" + vipData.getPoints() + " now=" + now);
			VipLevel vip = null;
			if (vipData.getLevel() > 0) {
				vip = ObjectManagerClient.getVipLevel(vipData.getLevel());
				if (Log.loggingDebug)
					log.debug("ExtendVipHook: Bonuses=" + vip.GetSettings());
				vipmpoints = (long) vip.getPointsMax();
				if (time > 0l) {
					if (vipData.getExpire() > now) {
						vipData.setExpire(vipData.getExpire() + 60_000 * time);
					} else {
						vipData.setExpire(System.currentTimeMillis() + (time * 60_000));
					}
					addBonuses(playerOid, new ArrayList<BonusSettings>(vip.GetSettings().values()), "VIP" + vipData.getLevel());
				}
				if (points != 0) {
					if (vipmpoints > 0)
						if (points + vipData.getPoints() >= vipmpoints) {
							toDelete.addAll(removeBonuses(playerOid, new ArrayList<BonusSettings>(vip.GetSettings().values()), "VIP" + vipData.getLevel()));
							vipData.setLevel(vipData.getLevel() + 1);
							vipData.setPoints(points + vipData.getPoints() - vipmpoints);
							vip = ObjectManagerClient.getVipLevel(vipData.getLevel());
							if(vip==null) {
								vip = ObjectManagerClient.getVipLevel(vipData.getLevel()-1);
								vipData.setLevel(vipData.getLevel() - 1);
								vipData.setPoints(vipmpoints);
							}
							if(VIP_USE_TIME && vipData.getExpire() - System.currentTimeMillis() > 1_000L) {
								addBonuses(playerOid, new ArrayList<BonusSettings>(vip.GetSettings().values()), "VIP" + vipData.getLevel());
							}
							vipmpoints = (long) vip.getPointsMax();
						} else {
							vipData.setPoints(points + vipData.getPoints());
						}
				}
				if (VIP_PER_CHARACTER) {
					authDB.vipUpdate(vipData, playerOid);
				} else {
					authDB.vipAccountUpdate(vipData, accountID);
				}
			} else if (vipData.getLevel() == 0) {
				authDB.createVip(playerOid, accountID, points, time);
				vipData = authDB.getVipCharacter(playerOid);
				vip = ObjectManagerClient.getVipLevel(vipData.getLevel());
				if(VIP_USE_TIME && vipData.getExpire() - System.currentTimeMillis() > 1_000L)
				addBonuses(playerOid, new ArrayList<BonusSettings>(vip.GetSettings().values()), "VIP" + vipData.getLevel());
			}
			TargetedExtensionMessage sMsg = new TargetedExtensionMessage(playerOid);
			sMsg.setExtensionType(BonusClient.EXTMSG_VIP_UPDATE);
			sMsg.setProperty("lev", vipData.getLevel());
			sMsg.setProperty("points", vipData.getPoints());
			sMsg.setProperty("mpoints", vipmpoints);
			sMsg.setProperty("name", vip.getName());
			sMsg.setProperty("desc", vip.getDescription());
			sMsg.setProperty("time", vipData.getExpire() > 0 ? vipData.getExpire() - now : vipData.getExpire());
			int b = 0;
			for (BonusSettings bs : vip.GetSettings().values()) {
				sMsg.setProperty("b" + b, bs.getSettingName() + "|" + bs.GetValue() + "|" + bs.GetValuePercentage());
				b++;
			}
			sMsg.setProperty("bNum", b);
			sMsg.setProperty("vut", VIP_USE_TIME);
			Engine.getAgent().sendBroadcast(sMsg);

			long expireTime = vipData.getExpire() - now;
			if (expireTime > 1_000L) {
				if (tasks.containsKey(playerOid)) {
					tasks.get(playerOid).cancel(true);
					tasks.remove(playerOid);
				}
				VipTimer timer = new VipTimer(playerOid);
				ScheduledFuture sf = Engine.getExecutor().schedule(timer, expireTime, TimeUnit.MILLISECONDS);
				tasks.put(playerOid, sf);
			}else {
				if(VIP_USE_TIME) {
					toDelete.addAll(removeBonuses(playerOid, new ArrayList<BonusSettings>(vip.GetSettings().values()), "VIP" + vipData.getLevel()));
				}
			}
			sendBonusUpdate(playerOid);
			authDB.savePlayerBonuses(playerOid, bonusesArray.get(playerOid), toDelete);
			log.debug("ExtendVipHook End");
			return true;
		}
	}

	// Log the login information and send a response
	class LoginHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LoginMessage message = (LoginMessage) msg;
			OID playerOid = message.getSubject();
			HashMap<String, List<BonusSettings>> bonus = authDB.loadPlayerBonuses(playerOid);
			if (Log.loggingDebug)
				log.debug("LoginHook  playerOid=" + playerOid + " bonus=" + bonus);
			if (bonusesArray.containsKey(playerOid))
				bonusesArray.remove(playerOid);
			bonusesArray.put(playerOid, bonus);
			ArrayList<Integer> toDelete = new ArrayList<Integer>();
			VipData vipData = null;
			if(VIP_PER_CHARACTER) {
				vipData = authDB.getVipCharacter(playerOid);
			}else {
				OID accountID = authDB.getAccountOid(playerOid);
				vipData = authDB.getVipAccount(accountID);
			}
			if (vipData != null) {
				VipLevel vip = ObjectManagerClient.getVipLevel(vipData.getLevel());
				if (vip != null) {
					toDelete.addAll(removeBonuses(playerOid, new ArrayList<BonusSettings>(vip.GetSettings().values()), "VIP" + vipData.getLevel()));
					addBonuses(playerOid, new ArrayList<BonusSettings>(vip.GetSettings().values()), "VIP" + vipData.getLevel());
					if (vipData.getExpire() > 0l) {
						long now = System.currentTimeMillis();
						long time = vipData.getExpire() - now;
						if (Log.loggingDebug)
							log.debug("LoginHook playerOid=" + playerOid + " Expire=" + vipData.getExpire() + " now=" + now + " time=" + time);
						if (time > 0l) {
							VipTimer timer = new VipTimer(playerOid);
							ScheduledFuture sf = Engine.getExecutor().schedule(timer, time, TimeUnit.MILLISECONDS);
							tasks.put(playerOid, sf);
						} else if (vipData.getExpire() != 0) {
							// VipLevel vip = ObjectManagerClient.getVipLevel(vipData.getLevel());
							toDelete.addAll(removeBonuses(playerOid, new ArrayList<BonusSettings>(vip.GetSettings().values()), "VIP" + vipData.getLevel()));
							authDB.savePlayerBonuses(playerOid, bonusesArray.get(playerOid), toDelete);
						}
					}
				}
			}
			// Send Global Events
			HashMap<String, BonusSettings> bonuses = new HashMap<String, BonusSettings>();
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "GlobalEventList");
			int count = 0;
			for (int id : activeGlobalEvents) {
				GlobalEventSettings ges = globalEventsDefinition.get(id);
				props.put("id" + count, ges.getId());
				props.put("name" + count, ges.getName());
				props.put("desc" + count, ges.getDescription());
				count++;

			}
			props.put("num", count);

			if (Log.loggingDebug)
				log.debug("LoginHook send:" + props);

			// Send Global Event Update to player
			TargetedExtensionMessage tmsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(tmsg);

			Engine.getAgent().sendResponse(new ResponseMessage(message));
			sendBonusUpdate(playerOid);
			if (Log.loggingDebug)
				log.debug("LoginHook END playerOid=" + playerOid + " bonusesArray=" + bonusesArray);

			return true;
		}
	}

	// Log the login information and send a response
	class LogoutHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LogoutMessage message = (LogoutMessage) msg;
			OID playerOid = message.getSubject();
			if (Log.loggingDebug)
				log.debug("LogoutHook  playerOid=" + playerOid);
			if (bonusesArray.containsKey(playerOid))
				bonusesArray.remove(playerOid);
			if (tasks.containsKey(playerOid)) {
				tasks.get(playerOid).cancel(true);
				tasks.remove(playerOid);
			}

			Engine.getAgent().sendResponse(new ResponseMessage(message));
			if (Log.loggingDebug)
				log.debug("LogoutHook End playerOid=" + playerOid);

			return true;
		}
	}

	public class VipTimer implements Runnable {

		protected OID playerOid;

		public VipTimer(OID playerOid) {
			log.debug("VipTimer Create");
			this.playerOid = playerOid;
		}

		@Override
		public void run() {
			// Check user still has items
			if (Log.loggingDebug)
				Log.debug("Vip: running Vip Expire task for " + playerOid);
			tasks.remove(playerOid);
			VipData vipData = null;
			if (VIP_PER_CHARACTER) {
				vipData = authDB.getVipCharacter(playerOid);
			} else {
				OID accountID = authDB.getAccountOid(playerOid);
				vipData = authDB.getVipAccount(accountID);
			}
			VipLevel vip = ObjectManagerClient.getVipLevel(vipData.getLevel());
			ArrayList<Integer> toDelete = removeBonuses(playerOid, new ArrayList<BonusSettings>(vip.GetSettings().values()), "VIP" + vipData.getLevel());
			sendBonusUpdate(playerOid);
			authDB.savePlayerBonuses(playerOid, bonusesArray.get(playerOid), toDelete);

			long now = System.currentTimeMillis();
			TargetedExtensionMessage sMsg = new TargetedExtensionMessage(playerOid);
			sMsg.setExtensionType(BonusClient.EXTMSG_VIP_UPDATE);

			sMsg.setProperty("lev", vipData.getLevel());
			sMsg.setProperty("points", vipData.getPoints());
			sMsg.setProperty("mpoints", vip.getPointsMax());
			// sMsg.setProperty("time",vipData.getExpire());
			sMsg.setProperty("name", vip.getName());
			sMsg.setProperty("desc", vip.getDescription());
			sMsg.setProperty("time", vipData.getExpire() - now);
			int b = 0;
			for (BonusSettings bs : vip.GetSettings().values()) {
				sMsg.setProperty("b" + b, bs.getSettingName() + "|" + bs.GetValue() + "|" + bs.GetValuePercentage());
				b++;
			}
			sMsg.setProperty("bNum", b);
			sMsg.setProperty("vut", VIP_USE_TIME);
			Engine.getAgent().sendBroadcast(sMsg);

			if (Log.loggingDebug)
				Log.debug("Vip: removed bonuses from vip for playerOid=" + playerOid);

		}
	}

	HashMap<OID, ScheduledFuture> tasks = new HashMap<OID, ScheduledFuture>();
	private HashMap<Integer,GlobalEventSettings> globalEventsDefinition = new HashMap<Integer,GlobalEventSettings>();
	private ArrayList<Integer> activeGlobalEvents = new ArrayList<Integer>();
	private Map<OID, HashMap<String, List<BonusSettings>>> bonusesArray = new ConcurrentHashMap<OID, HashMap<String, List<BonusSettings>>>();
	protected AuthDatabase authDB;
	protected static int hour = 0;
	protected static int minute = 0;
	private boolean VIP_PER_CHARACTER = true;
	public static boolean VIP_USE_TIME = true;
	private static final Logger log = new Logger("Bonuses");
}
