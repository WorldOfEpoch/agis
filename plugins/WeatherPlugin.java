package atavism.agis.plugins;

import atavism.agis.database.AccountDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.objects.AgisItem;
import atavism.agis.plugins.WeatherClient.SetWeatherProfileMessage;
import atavism.agis.plugins.WeatherClient.SetWorldTimeMessage;
import atavism.msgsys.Message;
import atavism.msgsys.MessageAgent;
import atavism.msgsys.MessageType;
import atavism.msgsys.MessageTypeFilter;
import atavism.msgsys.ResponseMessage;
import atavism.server.engine.*;
import atavism.server.messages.LogoutMessage;
import atavism.server.objects.Entity;
import atavism.server.objects.EntityManager;
import atavism.server.objects.ObjectTypes;
import atavism.server.plugins.InstanceClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;
import atavism.server.plugins.WorldManagerClient.ObjectInfo;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;
import atavism.server.util.Logger;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.io.Serializable;

public class WeatherPlugin extends EnginePlugin {

	public WeatherPlugin() {
		super(WEATHER_PLUGIN_NAME);
		setPluginType("Weather");
	}

	ArrayList<OID> Oids = new ArrayList<OID>();
	HashMap<Integer, WeatherProfile> profiles = new HashMap<Integer, WeatherProfile>();
	HashMap<Integer, WeatherInstanceSettings> instancesWeather = new HashMap<Integer, WeatherInstanceSettings>();
	HashMap<Integer, WeatherInstanceSettings> instancesWeatherSend = new HashMap<Integer, WeatherInstanceSettings>();

	public String getName() {
		return WEATHER_PLUGIN_NAME;
	}

	LinkedList<HashMap<String, Serializable>> instances;

	public void onActivate() {
		loadData();

		if (Log.loggingDebug)
			log.debug("WEATHER PLUGIN: activated");
		registerHooks();

		Log.debug("Registering Weather plugin");
		Engine.registerStatusReportingPlugin(this);

		TimeZone tz = TimeZone.getTimeZone(WORLD_TIME_ZONE);
		// log.error("Activate: "+tz.getID()+" "+tz.getDisplayName()+"
		// "+tz.getDSTSavings()+" "+tz.getRawOffset());

		tz = TimeZone.getTimeZone("PST");
		// log.error("Activate: "+tz.getID()+" "+tz.getDisplayName()+"
		// "+tz.getDSTSavings()+" "+tz.getRawOffset());

	}

	protected void registerHooks() {
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_SPAWNED, new SpawnedHook());
		getHookManager().addHook(WorldManagerClient.MSG_TYPE_GET_WEATHER, new GetWeatherHook());
		// getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
		getHookManager().addHook(WeatherClient.MSG_TYPE_SET_WORLD_TIME, new SetWorldTimeHook());
		getHookManager().addHook(WeatherClient.MSG_TYPE_GET_WEATHER_PROFILE, new GetWeatherProfilesHook());
		getHookManager().addHook(WeatherClient.MSG_TYPE_SET_WEATHER_PROFILE, new SetWeatherProfilesHook());
			
		
		MessageTypeFilter filter = new MessageTypeFilter();
		filter.addType(WorldManagerClient.MSG_TYPE_SPAWNED);
		filter.addType(WeatherClient.MSG_TYPE_SET_WORLD_TIME);
		filter.addType(WeatherClient.MSG_TYPE_GET_WEATHER_PROFILE);
		filter.addType(WeatherClient.MSG_TYPE_SET_WEATHER_PROFILE);
			Engine.getAgent().createSubscription(filter, this);

		MessageTypeFilter filter2 = new MessageTypeFilter();
		// filter2.addType(LoginMessage.MSG_TYPE_LOGIN);
		filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
		Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);

		ChangeWeather cw = new ChangeWeather();
		Engine.getExecutor().scheduleAtFixedRate(cw, 0, WEATHER_UPDATE_PERIOD, TimeUnit.SECONDS);
		if (Log.loggingDebug)
			log.debug("WEATHER PLUGIN: registerHooks ChangeWeather add scheduleAtFixedRate");
		// Load Time from Database
		HashMap<String, Integer> time = aDB.LoadWorldTime();
		if (time.containsKey("year"))
			serverYear = time.get("year");
		if (time.containsKey("month"))
			serverMonth = time.get("month");
		if (time.containsKey("day"))
			serverDay = time.get("day");
		if (time.containsKey("hour"))
			serverHour = time.get("hour");
		if (time.containsKey("minute"))
			serverMinute = time.get("minute");
		if (time.containsKey("second"))
			serverSeconds = time.get("second");

		// Run WorldTimeTick every minute of "world" time
		WorldTimeTick timeTick = new WorldTimeTick();
		Engine.getExecutor().scheduleAtFixedRate(timeTick, 0, 1, TimeUnit.SECONDS);

		// Run SaveWorldTime every 5 minute of real Time
		SaveWorldTime swt = new SaveWorldTime();
		Engine.getExecutor().scheduleAtFixedRate(swt, 0, 5, TimeUnit.MINUTES);
	}
	
	void loadData() {
		aDB = new AccountDatabase(true);
		cDB = new ContentDatabase(true);
		instances = aDB.loadTemplateIslands();
		profiles = cDB.loadWeatherProfiles();
		for (HashMap<String, Serializable> instance : instances) {
			instancesWeather.put((Integer) instance.get("templateID"), new WeatherInstanceSettings());
			instancesWeatherSend.put((Integer) instance.get("templateID"), new WeatherInstanceSettings());
		}
		String weatherUpdatePeriod = cDB.loadGameSetting("WEATHER_UPDATE_PERIOD");
		if (weatherUpdatePeriod != null)
			WEATHER_UPDATE_PERIOD = Integer.parseInt(weatherUpdatePeriod);

		String weatherMonthServerTime = cDB.loadGameSetting("WEATHER_MONTH_SERVER_TIME");
		if (weatherMonthServerTime != null)
			WEATHER_MONTH_SERVER_TIME = Boolean.parseBoolean(weatherMonthServerTime);
		String worldTimeSpeed = cDB.loadGameSetting("WORLD_TIME_SPEED");
		if (worldTimeSpeed != null)
			WORLD_TIME_SPEED = Float.parseFloat(worldTimeSpeed);
		String worldTimeZone = cDB.loadGameSetting("WORLD_TIME_ZONE");
		if (worldTimeZone != null)
			WORLD_TIME_ZONE = worldTimeZone;
	}
	
	   
    	protected void ReloadTemplates(Message msg) {
			Log.error("WeatherPlugin ReloadTemplates Start");
			loadData();
			Log.error("WeatherPlugin ReloadTemplates End");
		}
	
	class GetWeatherProfilesHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			ExtensionMessage message = (ExtensionMessage) msg;
			if (Log.loggingDebug)
				Log.debug("SetWorldTimeHook started ");
			OID playerOid = message.getSubject();
			OID instanceOid = WorldManagerClient.getObjectInfo(playerOid).instanceOid;
			int instanceID = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
			ArrayList<Integer> instanceProfiles = cDB.GetWeatherProfilesForInstance2(instanceID, serverMonth);
			int count=0;
			if (instanceProfiles.size() > 0) {
				HashMap props = new HashMap();
				props.put("ext_msg_subtype", "ao.weather_Profiles");
				for (Integer p : instanceProfiles) {
					WeatherProfile wp = profiles.get(p);
					props.put("id"+count, wp.GetID());
					props.put("name"+count, wp.GetName());
					count++;
				}
				props.put("num", count);
				if (Log.loggingDebug)
					log.debug(" props to send " + props);
				TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
				Engine.getAgent().sendBroadcast(_msg);
			
			}
			if (Log.loggingDebug)
				Log.debug("SetWorldTimeHook finished ");
			return true;
		}
	}
	class SetWeatherProfilesHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			SetWeatherProfileMessage message = (SetWeatherProfileMessage) msg;
			if (Log.loggingDebug)
				Log.debug("SetWeatherProfilesHook started ");
			int profileID = (int) message.getProperty("profil");
			OID playerOid = (OID) message.getProperty("ply");
			OID instanceOid = WorldManagerClient.getObjectInfo(playerOid).instanceOid;
			int instanceID = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
			RandomSettings(instanceID,profileID);
			WeatherSync();
			if (Log.loggingDebug)
				Log.debug("SetWeatherProfilesHook finished ");
			return true;
		}
	}

	class SetWorldTimeHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			SetWorldTimeMessage message = (SetWorldTimeMessage) msg;
			if (Log.loggingDebug)
				Log.debug("SetWorldTimeHook started ");

			serverYear = (int) message.getProperty("year");

			int month = (int) message.getProperty("month");
			if (month < 1)
				month = 1;
			serverMonth = month;
			int day = (int) message.getProperty("day");
			if (day < 1)
				day = 1;
			serverDay = day;
			serverHour = (int) message.getProperty("hour");
			serverMinute = (int) message.getProperty("minute");
			aDB.saveWorldTime(serverYear, serverMonth, serverDay, serverHour, serverMinute, (int) serverSeconds);
			
			HashMap props = new HashMap();
			props.put("ext_msg_subtype", "ao.weather_sync");
			props.put("year", serverYear);
			props.put("month", serverMonth);
			props.put("day", serverDay);
			props.put("hour", serverHour);
			props.put("minute", serverMinute);
			props.put("second", (int) serverSeconds);
			for (int i = Oids.size() - 1; i >= 0; i--) {

				OID objOid = Oids.get(i);
				boolean isPlayer = false;
				try {
					isPlayer = GuildClient.getPlayerIsOnline(objOid);
				} catch (IOException e1) {
					log.error("WeatherSync getPlayerIsOnline " + e1);
				}
				if (isPlayer) {
					TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, objOid, objOid, false, props);
					Engine.getAgent().sendBroadcast(_msg);
				}
			}
			if (Log.loggingDebug)
				Log.debug("SetWorldTimeHook finished ");
			return true;
		}
	}

	/**
	 * The hook for when players logout (or disconnect). This will remove the player
	 * from any arenas and queues they are in.
	 *
	 */
	class LogoutHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LogoutMessage message = (LogoutMessage) msg;
			OID playerOid = message.getSubject();
			if (Log.loggingDebug)
				Log.debug("LOGOUT: Weather logout started for: " + playerOid);
			// Remove the player from any queues they may have been in
			if (Oids.contains(playerOid)) {
				Oids.remove(playerOid);
			} else {
				Log.debug("LOGOUT: Weather logout player " + playerOid + " not on list");
			}
			// Response
			Engine.getAgent().sendResponse(new ResponseMessage(message));
			if (Log.loggingDebug)
				Log.debug("LOGOUT: Weather logout finished for: " + playerOid);
			return true;
		}
	}

	class GetWeatherHook implements Hook {
		@Override
		public boolean processMessage(Message msg, int arg1) {

			// TODO Auto-generated method stub
			ExtensionMessage spawnedMsg = (ExtensionMessage) msg;
			OID objOid = spawnedMsg.getSubject();
			if (Log.loggingDebug)
				log.debug("GetWeatherHook " + objOid + " " + spawnedMsg + " " + spawnedMsg.getSenderName());
			ObjectInfo objInfo = WorldManagerClient.getObjectInfo(objOid);
			if (objInfo.objType != ObjectTypes.player)
				return true;
			boolean isPlayerOnline = false;
			try {
				isPlayerOnline = GuildClient.getPlayerIsOnline(objOid);
			} catch (IOException e1) {
			}
			if (!isPlayerOnline)
				return true;
			OID instanceOid = WorldManagerClient.getObjectInfo(objOid).instanceOid;
			int instanceID = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
			HashMap props = new HashMap();
			props.put("ext_msg_subtype", "ao.weather_sync");
			props.put("year", serverYear);
			props.put("month", serverMonth);
			props.put("day", serverDay);
			props.put("hour", serverHour);
			props.put("minute", serverMinute);
			props.put("second", (int) serverSeconds);
			props.put("worldTimeSpeed", WORLD_TIME_SPEED);
			// Temperature
			props.put("temperature", instancesWeather.get(instanceID).GetTemperature());
			// Humidity
			props.put("humidity", instancesWeather.get(instanceID).GetHumidity());
			// WindDirection
			props.put("windDirection", instancesWeather.get(instanceID).GetWindDirection());
			// WindSpeed
			props.put("windSpeed", instancesWeather.get(instanceID).GetWindSpeed());
			// WindTurbulence
			props.put("windTurbulence", instancesWeather.get(instanceID).GetWindTurbulence());
			// FogHeightPower
			props.put("fogHeightPower", instancesWeather.get(instanceID).GetFogHeightPower());
			// FogHeightMax
			props.put("fogHeightMax", instancesWeather.get(instanceID).GetFogHeightMax());
			// FogDistancePower
			props.put("fogDistancePower", instancesWeather.get(instanceID).GetFogDistancePower());
			// FogDistanceMax
			props.put("fogDistanceMax", instancesWeather.get(instanceID).GetFogDistanceMax());
			// RainPower
			props.put("rainPower", instancesWeather.get(instanceID).GetRainPower());
			// RainPowerTerrain
			props.put("rainPowerTerrain", instancesWeather.get(instanceID).GetRainPowerTerrain());
			// RainMinHeight
			props.put("rainMinHeight", instancesWeather.get(instanceID).GetRainMinHeight());
			// RainMaxHeight
			props.put("rainMaxHeight", instancesWeather.get(instanceID).GetRainMaxHeight());
			// HailPower
			props.put("hailPower", instancesWeather.get(instanceID).GetHailPower());
			// HailPowerTerrain
			props.put("hailPowerTerrain", instancesWeather.get(instanceID).GetHailPowerTerrain());
			// HailMinHeight
			props.put("hailMinHeight", instancesWeather.get(instanceID).GetHailMinHeight());
			// HailMaxHeight
			props.put("hailMaxHeight", instancesWeather.get(instanceID).GetHailMaxHeight());
			// SnowPower
			props.put("snowPower", instancesWeather.get(instanceID).GetSnowPower());
			// SnowPowerTerrain
			props.put("snowPowerTerrain", instancesWeather.get(instanceID).GetSnowPowerTerrain());
			// SnowMinHeight
			props.put("snowMinHeight", instancesWeather.get(instanceID).GetSnowMinHeight());
			// SnowAge
			props.put("snowAge", instancesWeather.get(instanceID).GetSnowAge());
			// ThunderPower
			props.put("thunderPower", instancesWeather.get(instanceID).GetThunderPower());
			// CloudPower
			props.put("cloudPower", instancesWeather.get(instanceID).GetCloudPower());
			// CloudMinHeight
			props.put("cloudMinHeight", instancesWeather.get(instanceID).GetCloudMinHeight());
			// CloudMaxHeight
			props.put("cloudMaxHeight", instancesWeather.get(instanceID).GetCloudMaxHeight());
			// CloudSpeed
			props.put("cloudSpeed", instancesWeather.get(instanceID).GetCloudSpeed());
			// MoonPhase
			props.put("moonPhase", instancesWeather.get(instanceID).GetMoonPhase());
			// Season
			props.put("season", instancesWeather.get(instanceID).GetSeason());
			props.put("profile", instancesWeather.get(instanceID).GetProfileName());
			if (Log.loggingDebug)
				log.debug(" props to send " + props);
			TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, objOid, objOid, false, props);
			Engine.getAgent().sendBroadcast(_msg);
			return true;
		}
	}

	class SpawnedHook implements Hook {
		@Override
		public boolean processMessage(Message msg, int arg1) {
			// TODO Auto-generated method stub
			WorldManagerClient.SpawnedMessage spawnedMsg = (WorldManagerClient.SpawnedMessage) msg;
			OID objOid = spawnedMsg.getSubject();
			if (spawnedMsg.getType() == null || !spawnedMsg.getType().isPlayer())
				return true;
			boolean isPlayerOnline = false;
			try {
				isPlayerOnline = GuildClient.getPlayerIsOnline(objOid);
			} catch (IOException e1) {
			}
			if (!isPlayerOnline)
				return true;
			if (!Oids.contains(objOid))
				Oids.add(objOid);
			SpawnSendWeather ssw = new SpawnSendWeather(objOid, 0);
			Engine.getExecutor().schedule(ssw, 2, TimeUnit.SECONDS);
			return true;
		}
	}

	class SpawnSendWeather implements Runnable {
		OID plyOid;
		int count = 1;

		SpawnSendWeather(OID PlyOid, int count) {
			this.plyOid = PlyOid;
			this.count = count;
		}

		@Override
		public void run() {
			if (Log.loggingDebug)
				log.debug(" weather start send from spawn to " + plyOid);
			OID instanceOid = WorldManagerClient.getObjectInfo(plyOid).instanceOid;
			int instanceID = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
			HashMap props = new HashMap();
			props.put("ext_msg_subtype", "ao.weather_sync");
			props.put("year", serverYear);
			props.put("month", serverMonth);
			props.put("day", serverDay);
			props.put("hour", serverHour);
			props.put("minute", serverMinute);
			props.put("second", (int) serverSeconds);
			props.put("worldTimeSpeed", WORLD_TIME_SPEED);
			TimeZone tz = TimeZone.getTimeZone(WORLD_TIME_ZONE);
			props.put("zone", tz.getRawOffset() / 3600000);
			// Temperature
			props.put("temperature", instancesWeather.get(instanceID).GetTemperature());
			// Humidity
			props.put("humidity", instancesWeather.get(instanceID).GetHumidity());
			// WindDirection
			props.put("windDirection", instancesWeather.get(instanceID).GetWindDirection());
			// WindSpeed
			props.put("windSpeed", instancesWeather.get(instanceID).GetWindSpeed());
			// WindTurbulence
			props.put("windTurbulence", instancesWeather.get(instanceID).GetWindTurbulence());
			// FogHeightPower
			props.put("fogHeightPower", instancesWeather.get(instanceID).GetFogHeightPower());
			// FogHeightMax
			props.put("fogHeightMax", instancesWeather.get(instanceID).GetFogHeightMax());
			// FogDistancePower
			props.put("fogDistancePower", instancesWeather.get(instanceID).GetFogDistancePower());
			// FogDistanceMax
			props.put("fogDistanceMax", instancesWeather.get(instanceID).GetFogDistanceMax());
			// RainPower
			props.put("rainPower", instancesWeather.get(instanceID).GetRainPower());
			// RainPowerTerrain
			props.put("rainPowerTerrain", instancesWeather.get(instanceID).GetRainPowerTerrain());
			// RainMinHeight
			props.put("rainMinHeight", instancesWeather.get(instanceID).GetRainMinHeight());
			// RainMaxHeight
			props.put("rainMaxHeight", instancesWeather.get(instanceID).GetRainMaxHeight());
			// HailPower
			props.put("hailPower", instancesWeather.get(instanceID).GetHailPower());
			// HailPowerTerrain
			props.put("hailPowerTerrain", instancesWeather.get(instanceID).GetHailPowerTerrain());
			// HailMinHeight
			props.put("hailMinHeight", instancesWeather.get(instanceID).GetHailMinHeight());
			// HailMaxHeight
			props.put("hailMaxHeight", instancesWeather.get(instanceID).GetHailMaxHeight());
			// SnowPower
			props.put("snowPower", instancesWeather.get(instanceID).GetSnowPower());
			// SnowPowerTerrain
			props.put("snowPowerTerrain", instancesWeather.get(instanceID).GetSnowPowerTerrain());
			// SnowMinHeight
			props.put("snowMinHeight", instancesWeather.get(instanceID).GetSnowMinHeight());
			// SnowAge
			props.put("snowAge", instancesWeather.get(instanceID).GetSnowAge());
			// ThunderPower
			props.put("thunderPower", instancesWeather.get(instanceID).GetThunderPower());
			// CloudPower
			props.put("cloudPower", instancesWeather.get(instanceID).GetCloudPower());
			// CloudMinHeight
			props.put("cloudMinHeight", instancesWeather.get(instanceID).GetCloudMinHeight());
			// CloudMaxHeight
			props.put("cloudMaxHeight", instancesWeather.get(instanceID).GetCloudMaxHeight());
			// CloudSpeed
			props.put("cloudSpeed", instancesWeather.get(instanceID).GetCloudSpeed());
			// MoonPhase
			props.put("moonPhase", instancesWeather.get(instanceID).GetMoonPhase());
			// Season
			props.put("season", instancesWeather.get(instanceID).GetSeason());
			props.put("profile", instancesWeather.get(instanceID).GetProfileName());
			if (Log.loggingDebug)
				log.debug(" props to send " + props);
			TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, plyOid, plyOid, false, props);
			Engine.getAgent().sendBroadcast(_msg);
			if (count > 0) {
				SpawnSendWeather ssw = new SpawnSendWeather(plyOid, count - 1);
				Engine.getExecutor().schedule(ssw, 2, TimeUnit.SECONDS);
			}
			// return true;
		}

	}

	
	
	
	class ChangeWeather implements Runnable {
		@Override
		public void run() {
			try {
				int dMonth = serverMonth;
				if (!WEATHER_MONTH_SERVER_TIME) {
					Calendar cal = Calendar.getInstance();
					dMonth = cal.get(Calendar.MONTH)+1;
				}
				if (Log.loggingDebug)
					log.debug("Start Weather calculation month: " + dMonth);
				Set<Integer> weatherKeys = instancesWeather.keySet();
				WeatherProfile wp = new WeatherProfile();
				for (int k : weatherKeys) {
					ArrayList<Integer> instanceProfiles = cDB.GetWeatherProfilesForInstance(k, dMonth);
					if (instanceProfiles.size() > 0) {
						int p =-1;
						if (instanceProfiles.size() > 1) {
							Random random = new Random();
							int r = random.nextInt(instanceProfiles.size());
							 p = instanceProfiles.get(r);
							/*if (profiles.containsKey(p)) {
								wp = profiles.get(p);
							} else {
								log.error("Weather Profile not loaded " + p);
							}*/
						} else {
							 p = instanceProfiles.get(0);
							/*if (profiles.containsKey(p)) {
								wp = profiles.get(p);
							} else {
								log.error("Weather Profile not loaded " + p);
							}*/
						}
						RandomSettings(k,p);
/*						if (wp.GetID() > 0) {

							instancesWeather.get(k).SetProfileName(wp.GetName());
							// Temperature
							if (wp.GetTemperatureMin() == wp.GetTemperatureMax()) {
								instancesWeather.get(k).SetTemperature(wp.GetTemperatureMin());
							} else {
								if (wp.GetTemperatureMin() > wp.GetTemperatureMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data Temperature");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetTemperatureMax() - wp.GetTemperatureMin()) * 100)) / 100f + wp.GetTemperatureMin();
									instancesWeather.get(k).SetTemperature(v);
								}
							}
							// Humidity
							if (wp.GetHumidityMin() == wp.GetHumidityMax()) {
								instancesWeather.get(k).SetHumidity(wp.GetHumidityMin());
							} else {
								if (wp.GetHumidityMin() > wp.GetHumidityMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data Humidity");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetHumidityMax() - wp.GetHumidityMin()) * 100)) / 100f + wp.GetHumidityMin();
									instancesWeather.get(k).SetHumidity(v);
								}
							}
							// WindDirection
							if (wp.GetWindDirectionMin() == wp.GetWindDirectionMax()) {
								instancesWeather.get(k).SetWindDirection(wp.GetWindDirectionMin());
							} else {
								if (wp.GetWindDirectionMin() > wp.GetWindDirectionMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data WindDirection");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetWindDirectionMax() - wp.GetWindDirectionMin()) * 100)) / 100f + wp.GetWindDirectionMin();
									instancesWeather.get(k).SetWindDirection(v);
								}
							}
							// WindSpeed
							if (wp.GetWindSpeedMin() == wp.GetWindSpeedMax()) {
								instancesWeather.get(k).SetWindSpeed(wp.GetWindSpeedMin());
							} else {
								if (wp.GetWindSpeedMin() > wp.GetWindSpeedMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data WindSpeed");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetWindSpeedMax() - wp.GetWindSpeedMin()) * 100)) / 100f + wp.GetWindSpeedMin();
									instancesWeather.get(k).SetWindSpeed(v);
								}
							}
							// WindTurbulence
							if (wp.GetWindTurbulenceMin() == wp.GetWindTurbulenceMax()) {
								instancesWeather.get(k).SetWindTurbulence(wp.GetWindTurbulenceMin());
							} else {
								if (wp.GetWindTurbulenceMin() > wp.GetWindTurbulenceMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data WindTurbulence");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetWindTurbulenceMax() - wp.GetWindTurbulenceMin()) * 100)) / 100f + wp.GetWindTurbulenceMin();
									instancesWeather.get(k).SetWindTurbulence(v);
								}
							}
							// FogHeightPower
							if (wp.GetFogHeightPowerMin() == wp.GetFogHeightPowerMax()) {
								instancesWeather.get(k).SetFogHeightPower(wp.GetFogHeightPowerMin());
							} else {
								if (wp.GetFogHeightPowerMin() > wp.GetFogHeightPowerMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data FogHeightPower");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetFogHeightPowerMax() - wp.GetFogHeightPowerMin()) * 100)) / 100f + wp.GetFogHeightPowerMin();
									instancesWeather.get(k).SetFogHeightPower(v);
								}
							}
							// FogHeightMax
							instancesWeather.get(k).SetFogHeightMax(wp.GetFogHeightMax());
							// FogDistancePower
							if (wp.GetFogDistancePowerMin() == wp.GetFogDistancePowerMax()) {
								instancesWeather.get(k).SetFogDistancePower(wp.GetFogDistancePowerMin());
							} else {
								if (wp.GetFogHeightPowerMin() > wp.GetFogHeightPowerMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data FogDistancePower");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetFogDistancePowerMax() - wp.GetFogDistancePowerMin()) * 100)) / 100f + wp.GetFogDistancePowerMin();
									instancesWeather.get(k).SetFogDistancePower(v);
								}
							}
							// FogDistanceMax
							instancesWeather.get(k).SetFogDistanceMax(wp.GetFogDistanceMax());

							// RainPower
							if (wp.GetRainPowerMin() == wp.GetRainPowerMax()) {
								instancesWeather.get(k).SetRainPower(wp.GetRainPowerMin());
							} else {
								if (wp.GetRainPowerMin() > wp.GetRainPowerMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data RainPower");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetRainPowerMax() - wp.GetRainPowerMin()) * 100)) / 100f + wp.GetRainPowerMin();
									instancesWeather.get(k).SetRainPower(v);
								}
							}
							// RainPowerTerrain
							if (wp.GetRainPowerTerrainMin() == wp.GetRainPowerTerrainMax()) {
								instancesWeather.get(k).SetRainPowerTerrain(wp.GetRainPowerTerrainMin());
							} else {
								if (wp.GetRainPowerTerrainMin() > wp.GetRainPowerTerrainMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data RainPowerTerrain");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetRainPowerTerrainMax() - wp.GetRainPowerTerrainMin()) * 100)) / 100f + wp.GetRainPowerTerrainMin();
									instancesWeather.get(k).SetRainPowerTerrain(v);
								}
							}
							// RainMinHeight
							instancesWeather.get(k).SetRainMinHeight(wp.GetRainMinHeight());
							// RainMaxHeight
							instancesWeather.get(k).SetRainMaxHeight(wp.GetRainMaxHeight());
							// HailPower
							if (wp.GetHailPowerMin() == wp.GetHailPowerMax()) {
								instancesWeather.get(k).SetHailPower(wp.GetHailPowerMin());
							} else {
								if (wp.GetHailPowerMin() > wp.GetHailPowerMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data HailPower");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetHailPowerMax() - wp.GetHailPowerMin()) * 100)) / 100f + wp.GetHailPowerMin();
									instancesWeather.get(k).SetHailPower(v);
								}
							}
							// HailPowerTerrain
							if (wp.GetHailPowerTerrainMin() == wp.GetHailPowerTerrainMax()) {
								instancesWeather.get(k).SetHailPowerTerrain(wp.GetHailPowerTerrainMin());
							} else {
								if (wp.GetHailPowerTerrainMin() > wp.GetHailPowerTerrainMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data HailPowerTerrain");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetHailPowerTerrainMax() - wp.GetHailPowerTerrainMin()) * 100)) / 100f + wp.GetHailPowerTerrainMin();
									instancesWeather.get(k).SetHailPowerTerrain(v);
								}
							}
							// HailMinHeight
							instancesWeather.get(k).SetHailMinHeight(wp.GetHailMinHeight());
							// HailMaxHeight
							instancesWeather.get(k).SetHailMaxHeight(wp.GetHailMaxHeight());
							// SnowPower
							if (wp.GetSnowPowerMin() == wp.GetSnowPowerMax()) {
								instancesWeather.get(k).SetSnowPower(wp.GetSnowPowerMin());
							} else {
								if (wp.GetSnowPowerMin() > wp.GetSnowPowerMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data SnowPower");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetSnowPowerMax() - wp.GetSnowPowerMin()) * 100)) / 100f + wp.GetSnowPowerMin();
									instancesWeather.get(k).SetSnowPower(v);
								}
							}
							// SnowPowerTerrain
							if (wp.GetSnowPowerTerrainMin() == wp.GetSnowPowerTerrainMax()) {
								instancesWeather.get(k).SetSnowPowerTerrain(wp.GetSnowPowerTerrainMin());
							} else {
								if (wp.GetSnowPowerTerrainMin() > wp.GetSnowPowerTerrainMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data SnowPowerTerrain");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetSnowPowerTerrainMax() - wp.GetSnowPowerTerrainMin()) * 100)) / 100f + wp.GetSnowPowerTerrainMin();
									instancesWeather.get(k).SetSnowPowerTerrain(v);
								}
							}
							// SnowMinHeight
							instancesWeather.get(k).SetSnowMinHeight(wp.GetSnowMinHeight());
							// SnowAge
							if (wp.GetSnowAgeMin() == wp.GetSnowAgeMax()) {
								instancesWeather.get(k).SetSnowAge(wp.GetSnowAgeMin());
							} else {
								if (wp.GetSnowAgeMin() > wp.GetSnowAgeMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data SnowAge");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetSnowAgeMax() - wp.GetSnowAgeMin()) * 100)) / 100f + wp.GetSnowAgeMin();
									instancesWeather.get(k).SetSnowAge(v);
								}
							}
							// ThunderPower
							if (wp.GetThunderPowerMin() == wp.GetThunderPowerMax()) {
								instancesWeather.get(k).SetSnowAge(wp.GetThunderPowerMin());
							} else {
								if (wp.GetThunderPowerMin() > wp.GetThunderPowerMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data ThunderPower");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetThunderPowerMax() - wp.GetThunderPowerMin()) * 100)) / 100f + wp.GetThunderPowerMin();
									instancesWeather.get(k).SetThunderPower(v);
								}
							}
							// CloudPower
							if (wp.GetCloudPowerMin() == wp.GetCloudPowerMax()) {
								instancesWeather.get(k).SetCloudPower(wp.GetCloudPowerMin());
							} else {
								if (wp.GetCloudPowerMin() > wp.GetCloudPowerMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data CloudPower");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetCloudPowerMax() - wp.GetCloudPowerMin()) * 100)) / 100f + wp.GetCloudPowerMin();
									instancesWeather.get(k).SetCloudPower(v);
								}
							}
							// CloudMinHeight
							instancesWeather.get(k).SetCloudMinHeight(wp.GetCloudMinHeight());
							// CloudMaxHeight
							instancesWeather.get(k).SetCloudMaxHeight(wp.GetCloudMaxHeight());
							// CloudSpeed
							if (wp.GetCloudSpeedMin() == wp.GetCloudSpeedMax()) {
								instancesWeather.get(k).SetCloudSpeed(wp.GetCloudSpeedMin());
							} else {
								if (wp.GetCloudSpeedMin() > wp.GetCloudSpeedMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data CloudSpeed");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetCloudSpeedMax() - wp.GetCloudSpeedMin()) * 100)) / 100f + wp.GetCloudSpeedMin();
									instancesWeather.get(k).SetCloudSpeed(v);
								}
							}
							// MoonPhase
							if (wp.GetMoonPhaseMin() == wp.GetMoonPhaseMax()) {
								instancesWeather.get(k).SetMoonPhase(wp.GetMoonPhaseMin());
							} else {
								if (wp.GetMoonPhaseMin() > wp.GetMoonPhaseMax()) {
									log.error("Weather Profile " + wp.GetID() + " Erroneous data MoonPhase");
								} else {
									Random random = new Random();
									float v = random.nextInt((int) ((wp.GetMoonPhaseMax() - wp.GetMoonPhaseMin()) * 100)) / 100f + wp.GetMoonPhaseMin();
									instancesWeather.get(k).SetMoonPhase(v);
								}
							}
							// Season
							instancesWeather.get(k).SetSeason(cDB.GetSeasonForInstance(k, dMonth));

						} else {
							log.error("Warther Profil is empty");
						}
*/
					}
				}
				if (Log.loggingDebug)
					log.debug("Change Weather end");
				WeatherSync();
				if (Log.loggingDebug)
					log.debug("Change Weather end sync");
			} catch (Exception e) {
				log.error("ChangeWeather " + e);
			}
		}
	}

	
	void RandomSettings(int instanceID, int profileID) {
		WeatherProfile wp = new WeatherProfile();

		if (profiles.containsKey(profileID)) {
			wp = profiles.get(profileID);
		} else {
			log.error("Weather Profile not loaded " + profileID);
		}

		if (wp.GetID() > 0) {

			instancesWeather.get(instanceID).SetProfileName(wp.GetName());
			// Temperature
			if (wp.GetTemperatureMin() == wp.GetTemperatureMax()) {
				instancesWeather.get(instanceID).SetTemperature(wp.GetTemperatureMin());
			} else {
				if (wp.GetTemperatureMin() > wp.GetTemperatureMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data Temperature");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetTemperatureMax() - wp.GetTemperatureMin()) * 100)) / 100f + wp.GetTemperatureMin();
					instancesWeather.get(instanceID).SetTemperature(v);
				}
			}
			// Humidity
			if (wp.GetHumidityMin() == wp.GetHumidityMax()) {
				instancesWeather.get(instanceID).SetHumidity(wp.GetHumidityMin());
			} else {
				if (wp.GetHumidityMin() > wp.GetHumidityMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data Humidity");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetHumidityMax() - wp.GetHumidityMin()) * 100)) / 100f + wp.GetHumidityMin();
					instancesWeather.get(instanceID).SetHumidity(v);
				}
			}
			// WindDirection
			if (wp.GetWindDirectionMin() == wp.GetWindDirectionMax()) {
				instancesWeather.get(instanceID).SetWindDirection(wp.GetWindDirectionMin());
			} else {
				if (wp.GetWindDirectionMin() > wp.GetWindDirectionMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data WindDirection");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetWindDirectionMax() - wp.GetWindDirectionMin()) * 100)) / 100f + wp.GetWindDirectionMin();
					instancesWeather.get(instanceID).SetWindDirection(v);
				}
			}
			// WindSpeed
			if (wp.GetWindSpeedMin() == wp.GetWindSpeedMax()) {
				instancesWeather.get(instanceID).SetWindSpeed(wp.GetWindSpeedMin());
			} else {
				if (wp.GetWindSpeedMin() > wp.GetWindSpeedMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data WindSpeed");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetWindSpeedMax() - wp.GetWindSpeedMin()) * 100)) / 100f + wp.GetWindSpeedMin();
					instancesWeather.get(instanceID).SetWindSpeed(v);
				}
			}
			// WindTurbulence
			if (wp.GetWindTurbulenceMin() == wp.GetWindTurbulenceMax()) {
				instancesWeather.get(instanceID).SetWindTurbulence(wp.GetWindTurbulenceMin());
			} else {
				if (wp.GetWindTurbulenceMin() > wp.GetWindTurbulenceMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data WindTurbulence");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetWindTurbulenceMax() - wp.GetWindTurbulenceMin()) * 100)) / 100f + wp.GetWindTurbulenceMin();
					instancesWeather.get(instanceID).SetWindTurbulence(v);
				}
			}
			// FogHeightPower
			if (wp.GetFogHeightPowerMin() == wp.GetFogHeightPowerMax()) {
				instancesWeather.get(instanceID).SetFogHeightPower(wp.GetFogHeightPowerMin());
			} else {
				if (wp.GetFogHeightPowerMin() > wp.GetFogHeightPowerMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data FogHeightPower");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetFogHeightPowerMax() - wp.GetFogHeightPowerMin()) * 100)) / 100f + wp.GetFogHeightPowerMin();
					instancesWeather.get(instanceID).SetFogHeightPower(v);
				}
			}
			// FogHeightMax
			instancesWeather.get(instanceID).SetFogHeightMax(wp.GetFogHeightMax());
			// FogDistancePower
			if (wp.GetFogDistancePowerMin() == wp.GetFogDistancePowerMax()) {
				instancesWeather.get(instanceID).SetFogDistancePower(wp.GetFogDistancePowerMin());
			} else {
				if (wp.GetFogHeightPowerMin() > wp.GetFogHeightPowerMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data FogDistancePower");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetFogDistancePowerMax() - wp.GetFogDistancePowerMin()) * 100)) / 100f + wp.GetFogDistancePowerMin();
					instancesWeather.get(instanceID).SetFogDistancePower(v);
				}
			}
			// FogDistanceMax
			instancesWeather.get(instanceID).SetFogDistanceMax(wp.GetFogDistanceMax());

			// RainPower
			if (wp.GetRainPowerMin() == wp.GetRainPowerMax()) {
				instancesWeather.get(instanceID).SetRainPower(wp.GetRainPowerMin());
			} else {
				if (wp.GetRainPowerMin() > wp.GetRainPowerMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data RainPower");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetRainPowerMax() - wp.GetRainPowerMin()) * 100)) / 100f + wp.GetRainPowerMin();
					instancesWeather.get(instanceID).SetRainPower(v);
				}
			}
			// RainPowerTerrain
			if (wp.GetRainPowerTerrainMin() == wp.GetRainPowerTerrainMax()) {
				instancesWeather.get(instanceID).SetRainPowerTerrain(wp.GetRainPowerTerrainMin());
			} else {
				if (wp.GetRainPowerTerrainMin() > wp.GetRainPowerTerrainMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data RainPowerTerrain");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetRainPowerTerrainMax() - wp.GetRainPowerTerrainMin()) * 100)) / 100f + wp.GetRainPowerTerrainMin();
					instancesWeather.get(instanceID).SetRainPowerTerrain(v);
				}
			}
			// RainMinHeight
			instancesWeather.get(instanceID).SetRainMinHeight(wp.GetRainMinHeight());
			// RainMaxHeight
			instancesWeather.get(instanceID).SetRainMaxHeight(wp.GetRainMaxHeight());
			// HailPower
			if (wp.GetHailPowerMin() == wp.GetHailPowerMax()) {
				instancesWeather.get(instanceID).SetHailPower(wp.GetHailPowerMin());
			} else {
				if (wp.GetHailPowerMin() > wp.GetHailPowerMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data HailPower");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetHailPowerMax() - wp.GetHailPowerMin()) * 100)) / 100f + wp.GetHailPowerMin();
					instancesWeather.get(instanceID).SetHailPower(v);
				}
			}
			// HailPowerTerrain
			if (wp.GetHailPowerTerrainMin() == wp.GetHailPowerTerrainMax()) {
				instancesWeather.get(instanceID).SetHailPowerTerrain(wp.GetHailPowerTerrainMin());
			} else {
				if (wp.GetHailPowerTerrainMin() > wp.GetHailPowerTerrainMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data HailPowerTerrain");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetHailPowerTerrainMax() - wp.GetHailPowerTerrainMin()) * 100)) / 100f + wp.GetHailPowerTerrainMin();
					instancesWeather.get(instanceID).SetHailPowerTerrain(v);
				}
			}
			// HailMinHeight
			instancesWeather.get(instanceID).SetHailMinHeight(wp.GetHailMinHeight());
			// HailMaxHeight
			instancesWeather.get(instanceID).SetHailMaxHeight(wp.GetHailMaxHeight());
			// SnowPower
			if (wp.GetSnowPowerMin() == wp.GetSnowPowerMax()) {
				instancesWeather.get(instanceID).SetSnowPower(wp.GetSnowPowerMin());
			} else {
				if (wp.GetSnowPowerMin() > wp.GetSnowPowerMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data SnowPower");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetSnowPowerMax() - wp.GetSnowPowerMin()) * 100)) / 100f + wp.GetSnowPowerMin();
					instancesWeather.get(instanceID).SetSnowPower(v);
				}
			}
			// SnowPowerTerrain
			if (wp.GetSnowPowerTerrainMin() == wp.GetSnowPowerTerrainMax()) {
				instancesWeather.get(instanceID).SetSnowPowerTerrain(wp.GetSnowPowerTerrainMin());
			} else {
				if (wp.GetSnowPowerTerrainMin() > wp.GetSnowPowerTerrainMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data SnowPowerTerrain");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetSnowPowerTerrainMax() - wp.GetSnowPowerTerrainMin()) * 100)) / 100f + wp.GetSnowPowerTerrainMin();
					instancesWeather.get(instanceID).SetSnowPowerTerrain(v);
				}
			}
			// SnowMinHeight
			instancesWeather.get(instanceID).SetSnowMinHeight(wp.GetSnowMinHeight());
			// SnowAge
			if (wp.GetSnowAgeMin() == wp.GetSnowAgeMax()) {
				instancesWeather.get(instanceID).SetSnowAge(wp.GetSnowAgeMin());
			} else {
				if (wp.GetSnowAgeMin() > wp.GetSnowAgeMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data SnowAge");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetSnowAgeMax() - wp.GetSnowAgeMin()) * 100)) / 100f + wp.GetSnowAgeMin();
					instancesWeather.get(instanceID).SetSnowAge(v);
				}
			}
			// ThunderPower
			if (wp.GetThunderPowerMin() == wp.GetThunderPowerMax()) {
				instancesWeather.get(instanceID).SetSnowAge(wp.GetThunderPowerMin());
			} else {
				if (wp.GetThunderPowerMin() > wp.GetThunderPowerMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data ThunderPower");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetThunderPowerMax() - wp.GetThunderPowerMin()) * 100)) / 100f + wp.GetThunderPowerMin();
					instancesWeather.get(instanceID).SetThunderPower(v);
				}
			}
			// CloudPower
			if (wp.GetCloudPowerMin() == wp.GetCloudPowerMax()) {
				instancesWeather.get(instanceID).SetCloudPower(wp.GetCloudPowerMin());
			} else {
				if (wp.GetCloudPowerMin() > wp.GetCloudPowerMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data CloudPower");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetCloudPowerMax() - wp.GetCloudPowerMin()) * 100)) / 100f + wp.GetCloudPowerMin();
					instancesWeather.get(instanceID).SetCloudPower(v);
				}
			}
			// CloudMinHeight
			instancesWeather.get(instanceID).SetCloudMinHeight(wp.GetCloudMinHeight());
			// CloudMaxHeight
			instancesWeather.get(instanceID).SetCloudMaxHeight(wp.GetCloudMaxHeight());
			// CloudSpeed
			if (wp.GetCloudSpeedMin() == wp.GetCloudSpeedMax()) {
				instancesWeather.get(instanceID).SetCloudSpeed(wp.GetCloudSpeedMin());
			} else {
				if (wp.GetCloudSpeedMin() > wp.GetCloudSpeedMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data CloudSpeed");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetCloudSpeedMax() - wp.GetCloudSpeedMin()) * 100)) / 100f + wp.GetCloudSpeedMin();
					instancesWeather.get(instanceID).SetCloudSpeed(v);
				}
			}
			// MoonPhase
			if (wp.GetMoonPhaseMin() == wp.GetMoonPhaseMax()) {
				instancesWeather.get(instanceID).SetMoonPhase(wp.GetMoonPhaseMin());
			} else {
				if (wp.GetMoonPhaseMin() > wp.GetMoonPhaseMax()) {
					log.error("Weather Profile " + wp.GetID() + " Erroneous data MoonPhase");
				} else {
					Random random = new Random();
					float v = random.nextInt((int) ((wp.GetMoonPhaseMax() - wp.GetMoonPhaseMin()) * 100)) / 100f + wp.GetMoonPhaseMin();
					instancesWeather.get(instanceID).SetMoonPhase(v);
				}
			}
			// Season
			instancesWeather.get(instanceID).SetSeason(cDB.GetSeasonForInstance(instanceID, serverMonth));

		} else {
			log.error("Warther Profil is empty");
		}

	}
	
	
	
	class WorldTimeTick implements Runnable {
		int minute =0;
		int hour=0;
		public void run() {
			if (Log.loggingDebug)
				log.debug("World Time Tick");
			Calendar cal = Calendar.getInstance();
			// serverSeconds = (cal.get(Calendar.HOUR_OF_DAY) * 3600) +
			// (cal.get(Calendar.MINUTE) * 60) + cal.get(Calendar.SECOND);
			serverSeconds = serverSeconds + WORLD_TIME_SPEED;
			if (serverSeconds >= 60) {
				int min = (int) serverSeconds / 60;
				serverMinute = serverMinute + min;
				serverSeconds -= min * 60;
			}
			if (serverMinute >= 60) {
				int hour = serverMinute / 60;
				serverHour = serverHour + hour;
				serverMinute -= hour * 60;
			}
			if (serverHour >= 24) {
				int day = serverHour / 24;
				serverDay = serverDay + day;
				serverHour -= day * 24;
			}
			if (serverDay > 31 && (serverMonth == 1 || serverMonth == 3 || serverMonth == 4 || serverMonth == 5 || serverMonth == 7 || serverMonth == 8 || serverMonth == 10 || serverMonth == 12)) {
				serverMonth++;
				serverDay = serverDay - 31;
			}
			if (serverDay > 28 && serverMonth == 2) {
				serverMonth++;
				serverDay = serverDay - 28;
			}
			if (serverDay > 30 && (serverMonth == 4 || serverMonth == 6 || serverMonth == 9 || serverMonth == 11)) {
				serverMonth++;
				serverDay = serverDay - 30;
			}
			if (serverMonth > 12) {
				serverYear++;
				serverMonth = serverMonth - 12;
			}
			if (Log.loggingDebug)
				log.debug("Time " + serverYear + " - " + serverMonth + " - " + serverDay + "  " + serverHour + " : " + serverMinute + " : " + (int) serverSeconds);

			if (minute != serverMinute || hour != serverHour) {
				AgisWorldManagerClient.sendServerTime(serverHour, serverMinute, serverDay, serverMonth, serverYear);
				minute = serverMinute;
				hour = serverHour;
			}
		}
	}

	class SaveWorldTime implements Runnable {
		public void run() {
			if (Log.loggingDebug)
				log.debug("Save World Time");
			aDB.saveWorldTime(serverYear, serverMonth, serverDay, serverHour, serverMinute, (int) serverSeconds);
		}
	}

	void WeatherSync() {
		if (Log.loggingDebug)
			log.debug("WeatherSync start");
		try {
			for (int i = Oids.size() - 1; i >= 0; i--) {

				OID objOid = Oids.get(i);
				boolean isPlayer = false;
				try {
					isPlayer = GuildClient.getPlayerIsOnline(objOid);
				} catch (IOException e1) {
					log.error("WeatherSync getPlayerIsOnline " + e1);
				}
				if (isPlayer) {
					if (Log.loggingDebug)
						log.debug("WeatherSync for " + objOid);
					ObjectInfo oi = null;
					try {
						oi = WorldManagerClient.getObjectInfo(objOid);
					} catch (Exception e) {
						log.error("WeatherSync Exeption " + e.getMessage() + " " + e.getLocalizedMessage());
					}
					if (oi != null) {
						OID instanceOid = WorldManagerClient.getObjectInfo(objOid).instanceOid;
						int instanceID = InstanceClient.getInstanceInfo(instanceOid, InstanceClient.FLAG_TEMPLATE_ID).templateID;
						HashMap props = new HashMap();
						props.put("ext_msg_subtype", "ao.weather_sync");
						// Temperature
						if (instancesWeather.get(instanceID).GetTemperature() != instancesWeatherSend.get(instanceID).GetTemperature()) {
							props.put("temperature", instancesWeather.get(instanceID).GetTemperature());
							// instancesWeatherSend.get(instanceID).SetTemperature(instancesWeather.get(instanceID).GetTemperature());
						}
						// Humidity
						if (instancesWeather.get(instanceID).GetHumidity() != instancesWeatherSend.get(instanceID).GetHumidity()) {
							props.put("humidity", instancesWeather.get(instanceID).GetHumidity());
							// instancesWeatherSend.get(instanceID).SetHumidity(instancesWeather.get(instanceID).GetHumidity());
						}
						// WindDirection
						if (instancesWeather.get(instanceID).GetWindDirection() != instancesWeatherSend.get(instanceID).GetWindDirection()) {
							props.put("windDirection", instancesWeather.get(instanceID).GetWindDirection());
							// instancesWeatherSend.get(instanceID).SetWindDirection(instancesWeather.get(instanceID).GetWindDirection());
						}
						// WindSpeed
						if (instancesWeather.get(instanceID).GetWindSpeed() != instancesWeatherSend.get(instanceID).GetWindSpeed()) {
							props.put("windSpeed", instancesWeather.get(instanceID).GetWindSpeed());
							// instancesWeatherSend.get(instanceID).SetWindSpeed(instancesWeather.get(instanceID).GetWindSpeed());
						}
						// WindTurbulence
						if (instancesWeather.get(instanceID).GetWindTurbulence() != instancesWeatherSend.get(instanceID).GetWindTurbulence()) {
							props.put("windTurbulence", instancesWeather.get(instanceID).GetWindTurbulence());
							// instancesWeatherSend.get(instanceID).SetWindTurbulence(instancesWeather.get(instanceID).GetWindTurbulence());
						}
						// FogHeightPower
						if (instancesWeather.get(instanceID).GetFogHeightPower() != instancesWeatherSend.get(instanceID).GetFogHeightPower()) {
							props.put("fogHeightPower", instancesWeather.get(instanceID).GetFogHeightPower());
							// instancesWeatherSend.get(instanceID).SetFogHeightPower(instancesWeather.get(instanceID).GetFogHeightPower());
						}
						// FogHeightMax
						if (instancesWeather.get(instanceID).GetFogHeightMax() != instancesWeatherSend.get(instanceID).GetFogHeightMax()) {
							props.put("fogHeightMax", instancesWeather.get(instanceID).GetFogHeightMax());
							// instancesWeatherSend.get(instanceID).SetFogHeightMax(instancesWeather.get(instanceID).GetFogHeightMax());
						}
						// FogDistancePower
						if (instancesWeather.get(instanceID).GetFogDistancePower() != instancesWeatherSend.get(instanceID).GetFogDistancePower()) {
							props.put("fogDistancePower", instancesWeather.get(instanceID).GetFogDistancePower());
							// instancesWeatherSend.get(instanceID).SetFogDistancePower(instancesWeather.get(instanceID).GetFogDistancePower());
						}
						// FogDistanceMax
						if (instancesWeather.get(instanceID).GetFogDistanceMax() != instancesWeatherSend.get(instanceID).GetFogDistanceMax()) {
							props.put("fogDistanceMax", instancesWeather.get(instanceID).GetFogDistanceMax());
							// instancesWeatherSend.get(instanceID).SetFogDistanceMax(instancesWeather.get(instanceID).GetFogDistanceMax());
						}
						// RainPower
						if (instancesWeather.get(instanceID).GetRainPower() != instancesWeatherSend.get(instanceID).GetRainPower()) {
							props.put("rainPower", instancesWeather.get(instanceID).GetRainPower());
							// instancesWeatherSend.get(instanceID).SetRainPower(instancesWeather.get(instanceID).GetRainPower());
						}
						// RainPowerTerrain
						if (instancesWeather.get(instanceID).GetRainPowerTerrain() != instancesWeatherSend.get(instanceID).GetRainPowerTerrain()) {
							props.put("rainPowerTerrain", instancesWeather.get(instanceID).GetRainPowerTerrain());
							// instancesWeatherSend.get(instanceID).SetRainPowerTerrain(instancesWeather.get(instanceID).GetRainPowerTerrain());
						}
						// RainMinHeight
						if (instancesWeather.get(instanceID).GetRainMinHeight() != instancesWeatherSend.get(instanceID).GetRainMinHeight()) {
							props.put("rainMinHeight", instancesWeather.get(instanceID).GetRainMinHeight());
							// instancesWeatherSend.get(instanceID).SetRainMinHeight(instancesWeather.get(instanceID).GetRainMinHeight());
						}
						// RainMaxHeight
						if (instancesWeather.get(instanceID).GetRainMaxHeight() != instancesWeatherSend.get(instanceID).GetRainMaxHeight()) {
							props.put("rainMaxHeight", instancesWeather.get(instanceID).GetRainMaxHeight());
							// instancesWeatherSend.get(instanceID).SetRainMaxHeight(instancesWeather.get(instanceID).GetRainMaxHeight());
						}
						// HailPower
						if (instancesWeather.get(instanceID).GetHailPower() != instancesWeatherSend.get(instanceID).GetHailPower()) {
							props.put("hailPower", instancesWeather.get(instanceID).GetHailPower());
							// instancesWeatherSend.get(instanceID).SetHailPower(instancesWeather.get(instanceID).GetHailPower());
						}
						// HailPowerTerrain
						if (instancesWeather.get(instanceID).GetHailPowerTerrain() != instancesWeatherSend.get(instanceID).GetHailPowerTerrain()) {
							props.put("hailPowerTerrain", instancesWeather.get(instanceID).GetHailPowerTerrain());
							// instancesWeatherSend.get(instanceID).SetHailPowerTerrain(instancesWeather.get(instanceID).GetHailPowerTerrain());
						}
						// HailMinHeight
						if (instancesWeather.get(instanceID).GetHailMinHeight() != instancesWeatherSend.get(instanceID).GetHailMinHeight()) {
							props.put("hailMinHeight", instancesWeather.get(instanceID).GetHailMinHeight());
							// instancesWeatherSend.get(instanceID).SetHailMinHeight(instancesWeather.get(instanceID).GetHailMinHeight());
						}
						// HailMaxHeight
						if (instancesWeather.get(instanceID).GetHailMaxHeight() != instancesWeatherSend.get(instanceID).GetHailMaxHeight()) {
							props.put("hailMaxHeight", instancesWeather.get(instanceID).GetHailMaxHeight());
							// instancesWeatherSend.get(instanceID).SetHailMaxHeight(instancesWeather.get(instanceID).GetHailMaxHeight());
						}
						// SnowPower
						if (instancesWeather.get(instanceID).GetSnowPower() != instancesWeatherSend.get(instanceID).GetSnowPower()) {
							props.put("snowPower", instancesWeather.get(instanceID).GetSnowPower());
							// instancesWeatherSend.get(instanceID).SetSnowPower(instancesWeather.get(instanceID).GetSnowPower());
						}
						// SnowPowerTerrain
						if (instancesWeather.get(instanceID).GetSnowPowerTerrain() != instancesWeatherSend.get(instanceID).GetSnowPowerTerrain()) {
							props.put("snowPowerTerrain", instancesWeather.get(instanceID).GetSnowPowerTerrain());
							// instancesWeatherSend.get(instanceID).SetSnowPowerTerrain(instancesWeather.get(instanceID).GetSnowPowerTerrain());
						}
						// SnowMinHeight
						if (instancesWeather.get(instanceID).GetSnowMinHeight() != instancesWeatherSend.get(instanceID).GetSnowMinHeight()) {
							props.put("snowMinHeight", instancesWeather.get(instanceID).GetSnowMinHeight());
							// instancesWeatherSend.get(instanceID).SetSnowMinHeight(instancesWeather.get(instanceID).GetSnowMinHeight());
						}
						// SnowAge
						if (instancesWeather.get(instanceID).GetSnowAge() != instancesWeatherSend.get(instanceID).GetSnowAge()) {
							props.put("snowAge", instancesWeather.get(instanceID).GetSnowAge());
							// instancesWeatherSend.get(instanceID).SetSnowAge(instancesWeather.get(instanceID).GetSnowAge());
						}
						// ThunderPower
						if (instancesWeather.get(instanceID).GetThunderPower() != instancesWeatherSend.get(instanceID).GetThunderPower()) {
							props.put("thunderPower", instancesWeather.get(instanceID).GetThunderPower());
							// instancesWeatherSend.get(instanceID).SetThunderPower(instancesWeather.get(instanceID).GetThunderPower());
						}
						// CloudPower
						if (instancesWeather.get(instanceID).GetCloudPower() != instancesWeatherSend.get(instanceID).GetCloudPower()) {
							props.put("cloudPower", instancesWeather.get(instanceID).GetCloudPower());
							// instancesWeatherSend.get(instanceID).SetCloudPower(instancesWeather.get(instanceID).GetCloudPower());
						}
						// CloudMinHeight
						if (instancesWeather.get(instanceID).GetCloudMinHeight() != instancesWeatherSend.get(instanceID).GetCloudMinHeight()) {
							props.put("cloudMinHeight", instancesWeather.get(instanceID).GetCloudMinHeight());
							// instancesWeatherSend.get(instanceID).SetCloudMinHeight(instancesWeather.get(instanceID).GetCloudMinHeight());
						}
						// CloudMaxHeight
						if (instancesWeather.get(instanceID).GetCloudMaxHeight() != instancesWeatherSend.get(instanceID).GetCloudMaxHeight()) {
							props.put("cloudMaxHeight", instancesWeather.get(instanceID).GetCloudMaxHeight());
							// instancesWeatherSend.get(instanceID).SetCloudMaxHeight(instancesWeather.get(instanceID).GetCloudMaxHeight());
						}
						// CloudSpeed
						if (instancesWeather.get(instanceID).GetCloudSpeed() != instancesWeatherSend.get(instanceID).GetCloudSpeed()) {
							props.put("cloudSpeed", instancesWeather.get(instanceID).GetCloudSpeed());
							// instancesWeatherSend.get(instanceID).SetCloudSpeed(instancesWeather.get(instanceID).GetCloudSpeed());
						}
						// MoonPhase
						if (instancesWeather.get(instanceID).GetMoonPhase() != instancesWeatherSend.get(instanceID).GetMoonPhase()) {
							props.put("moonPhase", instancesWeather.get(instanceID).GetMoonPhase());
							// instancesWeatherSend.get(instanceID).SetMoonPhase(instancesWeather.get(instanceID).GetMoonPhase());
						}
						// Season
						if (instancesWeather.get(instanceID).GetSeason() != instancesWeatherSend.get(instanceID).GetSeason()) {
							props.put("season", instancesWeather.get(instanceID).GetSeason());
							// instancesWeatherSend.get(instanceID).SetSeason(instancesWeather.get(instanceID).GetSeason());
						}
						props.put("profile", instancesWeather.get(instanceID).GetProfileName());

						// props.put("second", serverSeconds);
						props.put("year", serverYear);
						props.put("month", serverMonth);
						props.put("day", serverDay);
						props.put("hour", serverHour);
						props.put("minute", serverMinute);
						props.put("second", (int) serverSeconds);
						props.put("worldTimeSpeed", WORLD_TIME_SPEED);
						TimeZone tz = TimeZone.getTimeZone(WORLD_TIME_ZONE);
						props.put("zone", tz.getRawOffset() / 3600000);

						if (Log.loggingDebug)
							log.debug("Weather: send props " + props);
						TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, objOid, objOid, false, props);
						Engine.getAgent().sendBroadcast(_msg);
					} else {
						if (Log.loggingDebug)
							log.debug("Weather: player " + Oids.get(i) + " ObjectInfo is null");
					}
				} else {
					if (Log.loggingDebug)
						log.debug("Weather: player is not online " + Oids.get(i));
					Oids.remove(i);

				}
			}
		} catch (Exception e) {
			log.error("WeatherSync sending to players " + e);
		}

		try {
			Set<Integer> weatherKeys = instancesWeather.keySet();
			for (int k : weatherKeys) {
				instancesWeatherSend.get(k).SetProfileName(instancesWeather.get(k).GetProfileName());
				// Temperature
				instancesWeatherSend.get(k).SetTemperature(instancesWeather.get(k).GetTemperature());
				// Humidity
				instancesWeatherSend.get(k).SetHumidity(instancesWeather.get(k).GetHumidity());
				// WindDirection
				instancesWeatherSend.get(k).SetWindDirection(instancesWeather.get(k).GetWindDirection());
				// WindSpeed
				instancesWeatherSend.get(k).SetWindSpeed(instancesWeather.get(k).GetWindSpeed());
				// WindTurbulence
				instancesWeatherSend.get(k).SetWindTurbulence(instancesWeather.get(k).GetWindTurbulence());
				// FogHeightPower
				instancesWeatherSend.get(k).SetFogHeightPower(instancesWeather.get(k).GetFogHeightPower());
				// FogHeightMax
				instancesWeatherSend.get(k).SetFogHeightMax(instancesWeather.get(k).GetFogHeightMax());
				// FogDistancePower
				instancesWeatherSend.get(k).SetFogDistancePower(instancesWeather.get(k).GetFogDistancePower());
				// FogDistanceMax
				instancesWeatherSend.get(k).SetFogDistanceMax(instancesWeather.get(k).GetFogDistanceMax());
				// RainPower
				instancesWeatherSend.get(k).SetRainPower(instancesWeather.get(k).GetRainPower());
				// RainPowerTerrain
				instancesWeatherSend.get(k).SetRainPowerTerrain(instancesWeather.get(k).GetRainPowerTerrain());
				// RainMinHeight
				instancesWeatherSend.get(k).SetRainMinHeight(instancesWeather.get(k).GetRainMinHeight());
				// RainMaxHeight
				instancesWeatherSend.get(k).SetRainMaxHeight(instancesWeather.get(k).GetRainMaxHeight());
				// HailPower
				instancesWeatherSend.get(k).SetHailPower(instancesWeather.get(k).GetHailPower());
				// HailPowerTerrain
				instancesWeatherSend.get(k).SetHailPowerTerrain(instancesWeather.get(k).GetHailPowerTerrain());
				// HailMinHeight
				instancesWeatherSend.get(k).SetHailMinHeight(instancesWeather.get(k).GetHailMinHeight());
				// HailMaxHeight
				instancesWeatherSend.get(k).SetHailMaxHeight(instancesWeather.get(k).GetHailMaxHeight());
				// SnowPower
				instancesWeatherSend.get(k).SetSnowPower(instancesWeather.get(k).GetSnowPower());
				// SnowPowerTerrain
				instancesWeatherSend.get(k).SetSnowPowerTerrain(instancesWeather.get(k).GetSnowPowerTerrain());
				// SnowMinHeight
				instancesWeatherSend.get(k).SetSnowMinHeight(instancesWeather.get(k).GetSnowMinHeight());
				// SnowAge
				instancesWeatherSend.get(k).SetSnowAge(instancesWeather.get(k).GetSnowAge());
				// ThunderPower
				instancesWeatherSend.get(k).SetThunderPower(instancesWeather.get(k).GetThunderPower());
				// CloudPower
				instancesWeatherSend.get(k).SetCloudPower(instancesWeather.get(k).GetCloudPower());
				// CloudMinHeight
				instancesWeatherSend.get(k).SetCloudMinHeight(instancesWeather.get(k).GetCloudMinHeight());
				// CloudMaxHeight
				instancesWeatherSend.get(k).SetCloudMaxHeight(instancesWeather.get(k).GetCloudMaxHeight());
				// CloudSpeed
				instancesWeatherSend.get(k).SetCloudSpeed(instancesWeather.get(k).GetCloudSpeed());
				// MoonPhase
				instancesWeatherSend.get(k).SetMoonPhase(instancesWeather.get(k).GetMoonPhase());
				// Season
				instancesWeatherSend.get(k).SetSeason(instancesWeather.get(k).GetSeason());
			}
		} catch (Exception e) {
			log.error("WeatherSync set sended settings " + e);
		}
		if (Log.loggingDebug)
			log.debug(" SendEnd");
	}

	public static class WeatherInstanceSettings {
		public WeatherInstanceSettings() {
		}

		protected String profile = "";

		public void SetProfileName(String t) {
			this.profile = t;
		}

		public String GetProfileName() {
			return this.profile;
		}

		protected float temperature = 0f;

		public void SetTemperature(float t) {
			this.temperature = t;
		}

		public float GetTemperature() {
			return this.temperature;
		}

		protected float humidity = 0f;

		public void SetHumidity(float v) {
			this.humidity = v;
		}

		public float GetHumidity() {
			return this.humidity;
		}

		protected float windDirection = 0f;

		public void SetWindDirection(float v) {
			this.windDirection = v;
		}

		public float GetWindDirection() {
			return this.windDirection;
		}

		protected float windSpeed = 0f;

		public void SetWindSpeed(float v) {
			this.windSpeed = v;
		}

		public float GetWindSpeed() {
			return this.windSpeed;
		}

		protected float windTurbulence = 0f;

		public void SetWindTurbulence(float v) {
			this.windTurbulence = v;
		}

		public float GetWindTurbulence() {
			return this.windTurbulence;
		}

		protected float fogHeightPower = 0f;

		public void SetFogHeightPower(float v) {
			this.fogHeightPower = v;
		}

		public float GetFogHeightPower() {
			return this.fogHeightPower;
		}

		protected float fogHeightMax = 0f;

		public void SetFogHeightMax(float v) {
			this.fogHeightMax = v;
		}

		public float GetFogHeightMax() {
			return this.fogHeightMax;
		}

		protected float fogDistancePower = 0f;

		public void SetFogDistancePower(float v) {
			this.fogDistancePower = v;
		}

		public float GetFogDistancePower() {
			return this.fogDistancePower;
		}

		protected float fogDistanceMax = 0f;

		public void SetFogDistanceMax(float v) {
			this.fogDistanceMax = v;
		}

		public float GetFogDistanceMax() {
			return this.fogDistanceMax;
		}

		protected float rainPower = 0f;

		public void SetRainPower(float v) {
			this.rainPower = v;
		}

		public float GetRainPower() {
			return this.rainPower;
		}

		protected float rainPowerTerrain = 0f;

		public void SetRainPowerTerrain(float v) {
			this.rainPowerTerrain = v;
		}

		public float GetRainPowerTerrain() {
			return this.rainPowerTerrain;
		}

		protected float rainMinHeight = 0f;

		public void SetRainMinHeight(float v) {
			this.rainMinHeight = v;
		}

		public float GetRainMinHeight() {
			return this.rainMinHeight;
		}

		protected float rainMaxHeight = 0f;

		public void SetRainMaxHeight(float v) {
			this.rainMaxHeight = v;
		}

		public float GetRainMaxHeight() {
			return this.rainMaxHeight;
		}

		protected float hailPower = 0f;

		public void SetHailPower(float v) {
			this.hailPower = v;
		}

		public float GetHailPower() {
			return this.hailPower;
		}

		protected float hailPowerTerrain = 0f;

		public void SetHailPowerTerrain(float v) {
			this.hailPowerTerrain = v;
		}

		public float GetHailPowerTerrain() {
			return this.hailPowerTerrain;
		}

		protected float hailMinHeight = 0f;

		public void SetHailMinHeight(float v) {
			this.hailMinHeight = v;
		}

		public float GetHailMinHeight() {
			return this.hailMinHeight;
		}

		protected float hailMaxHeight = 0f;

		public void SetHailMaxHeight(float v) {
			this.hailMaxHeight = v;
		}

		public float GetHailMaxHeight() {
			return this.hailMaxHeight;
		}

		protected float snowPower = 0f;

		public void SetSnowPower(float v) {
			this.snowPower = v;
		}

		public float GetSnowPower() {
			return this.snowPower;
		}

		protected float snowPowerTerrain = 0f;

		public void SetSnowPowerTerrain(float v) {
			this.snowPowerTerrain = v;
		}

		public float GetSnowPowerTerrain() {
			return this.snowPowerTerrain;
		}

		protected float snowMinHeight = 0f;

		public void SetSnowMinHeight(float v) {
			this.snowMinHeight = v;
		}

		public float GetSnowMinHeight() {
			return this.snowMinHeight;
		}

		protected float snowAge = 0f;

		public void SetSnowAge(float v) {
			this.snowAge = v;
		}

		public float GetSnowAge() {
			return this.snowAge;
		}

		protected float thunderPower = 0f;

		public void SetThunderPower(float v) {
			this.thunderPower = v;
		}

		public float GetThunderPower() {
			return this.thunderPower;
		}

		protected float cloudPower = 0f;

		public void SetCloudPower(float v) {
			this.cloudPower = v;
		}

		public float GetCloudPower() {
			return this.cloudPower;
		}

		protected float cloudMinHeight = 0f;

		public void SetCloudMinHeight(float v) {
			this.cloudMinHeight = v;
		}

		public float GetCloudMinHeight() {
			return this.cloudMinHeight;
		}

		protected float cloudMaxHeight = 0f;

		public void SetCloudMaxHeight(float v) {
			this.cloudMaxHeight = v;
		}

		public float GetCloudMaxHeight() {
			return this.cloudMaxHeight;
		}

		protected float cloudSpeed = 0f;

		public void SetCloudSpeed(float v) {
			this.cloudSpeed = v;
		}

		public float GetCloudSpeed() {
			return this.cloudSpeed;
		}

		protected float moonPhase = 0f;

		public void SetMoonPhase(float v) {
			this.moonPhase = v;
		}

		public float GetMoonPhase() {
			return this.moonPhase;
		}

		protected float season = 0f;

		public void SetSeason(float v) {
			this.season = v;
		}

		public float GetSeason() {
			return this.season;
		}

	}

	public static class WeatherProfile {
		public WeatherProfile() {
		}

		protected Integer id;

		public void SetID(Integer id) {
			this.id = id;
		}

		public Integer GetID() {
			return this.id;
		}

		protected String name;

		public void SetName(String name) {
			this.name = name;
		}

		public String GetName() {
			return this.name;
		}

		protected float temperature_min = 0f;

		public void SetTemperatureMin(float t) {
			this.temperature_min = t;
		}

		public float GetTemperatureMin() {
			return this.temperature_min;
		}

		protected float temperature_max = 0f;

		public void SetTemperatureMax(float t) {
			this.temperature_max = t;
		}

		public float GetTemperatureMax() {
			return this.temperature_max;
		}

		protected float humidity_min = 0f;

		public void SetHumidityMin(float v) {
			this.humidity_min = v;
		}

		public float GetHumidityMin() {
			return this.humidity_min;
		}

		protected float humidity_max = 0f;

		public void SetHumidityMax(float v) {
			this.humidity_max = v;
		}

		public float GetHumidityMax() {
			return this.humidity_max;
		}

		protected float windDirection_min = 0f;

		public void SetWindDirectionMin(float v) {
			this.windDirection_min = v;
		}

		public float GetWindDirectionMin() {
			return this.windDirection_min;
		}

		protected float windDirection_max = 360f;

		public void SetWindDirectionMax(float v) {
			this.windDirection_max = v;
		}

		public float GetWindDirectionMax() {
			return this.windDirection_max;
		}

		protected float windSpeed_min = 0f;

		public void SetWindSpeedMin(float v) {
			this.windSpeed_min = v;
		}

		public float GetWindSpeedMin() {
			return this.windSpeed_min;
		}

		protected float windSpeed_max = 0f;

		public void SetWindSpeedMax(float v) {
			this.windSpeed_max = v;
		}

		public float GetWindSpeedMax() {
			return this.windSpeed_max;
		}

		protected float windTurbulence_min = 0f;

		public void SetWindTurbulenceMin(float v) {
			this.windTurbulence_min = v;
		}

		public float GetWindTurbulenceMin() {
			return this.windTurbulence_min;
		}

		protected float windTurbulence_max = 0f;

		public void SetWindTurbulenceMax(float v) {
			this.windTurbulence_max = v;
		}

		public float GetWindTurbulenceMax() {
			return this.windTurbulence_max;
		}

		protected float fogHeightPower_min = 0f;

		public void SetFogHeightPowerMin(float v) {
			this.fogHeightPower_min = v;
		}

		public float GetFogHeightPowerMin() {
			return this.fogHeightPower_min;
		}

		protected float fogHeightPower_max = 0f;

		public void SetFogHeightPowerMax(float v) {
			this.fogHeightPower_max = v;
		}

		public float GetFogHeightPowerMax() {
			return this.fogHeightPower_max;
		}

		protected float fogHeightMax = 0f;

		public void SetFogHeightMax(float v) {
			this.fogHeightMax = v;
		}

		public float GetFogHeightMax() {
			return this.fogHeightMax;
		}

		protected float fogDistancePower_min = 0f;

		public void SetFogDistancePowerMin(float v) {
			this.fogDistancePower_min = v;
		}

		public float GetFogDistancePowerMin() {
			return this.fogDistancePower_min;
		}

		protected float fogDistancePower_max = 0f;

		public void SetFogDistancePowerMax(float v) {
			this.fogDistancePower_max = v;
		}

		public float GetFogDistancePowerMax() {
			return this.fogDistancePower_max;
		}

		protected float fogDistanceMax = 0f;

		public void SetFogDistanceMax(float v) {
			this.fogDistanceMax = v;
		}

		public float GetFogDistanceMax() {
			return this.fogDistanceMax;
		}

		protected float rainPower_min = 0f;

		public void SetRainPowerMin(float v) {
			this.rainPower_min = v;
		}

		public float GetRainPowerMin() {
			return this.rainPower_min;
		}

		protected float rainPower_max = 0f;

		public void SetRainPowerMax(float v) {
			this.rainPower_max = v;
		}

		public float GetRainPowerMax() {
			return this.rainPower_max;
		}

		protected float rainPowerTerrain_min = 0f;

		public void SetRainPowerTerrainMin(float v) {
			this.rainPowerTerrain_min = v;
		}

		public float GetRainPowerTerrainMin() {
			return this.rainPowerTerrain_min;
		}

		protected float rainPowerTerrain_max = 0f;

		public void SetRainPowerTerrainMax(float v) {
			this.rainPowerTerrain_max = v;
		}

		public float GetRainPowerTerrainMax() {
			return this.rainPowerTerrain_max;
		}

		protected float rainMinHeight = 0f;

		public void SetRainMinHeight(float v) {
			this.rainMinHeight = v;
		}

		public float GetRainMinHeight() {
			return this.rainMinHeight;
		}

		protected float rainMaxHeight = 0f;

		public void SetRainMaxHeight(float v) {
			this.rainMaxHeight = v;
		}

		public float GetRainMaxHeight() {
			return this.rainMaxHeight;
		}

		protected float hailPower_min = 0f;

		public void SetHailPowerMin(float v) {
			this.hailPower_min = v;
		}

		public float GetHailPowerMin() {
			return this.hailPower_min;
		}

		protected float hailPower_max = 0f;

		public void SetHailPowerMax(float v) {
			this.hailPower_max = v;
		}

		public float GetHailPowerMax() {
			return this.hailPower_max;
		}

		protected float hailPowerTerrain_min = 0f;

		public void SetHailPowerTerrainMin(float v) {
			this.hailPowerTerrain_min = v;
		}

		public float GetHailPowerTerrainMin() {
			return this.hailPowerTerrain_min;
		}

		protected float hailPowerTerrain_max = 0f;

		public void SetHailPowerTerrainMax(float v) {
			this.hailPowerTerrain_max = v;
		}

		public float GetHailPowerTerrainMax() {
			return this.hailPowerTerrain_max;
		}

		protected float hailMinHeight = 0f;

		public void SetHailMinHeight(float v) {
			this.hailMinHeight = v;
		}

		public float GetHailMinHeight() {
			return this.hailMinHeight;
		}

		protected float hailMaxHeight = 0f;

		public void SetHailMaxHeight(float v) {
			this.hailMaxHeight = v;
		}

		public float GetHailMaxHeight() {
			return this.hailMaxHeight;
		}

		protected float snowPower_min = 0f;

		public void SetSnowPowerMin(float v) {
			this.snowPower_min = v;
		}

		public float GetSnowPowerMin() {
			return this.snowPower_min;
		}

		protected float snowPower_max = 0f;

		public void SetSnowPowerMax(float v) {
			this.snowPower_max = v;
		}

		public float GetSnowPowerMax() {
			return this.snowPower_max;
		}

		protected float snowPowerTerrain_min = 0f;

		public void SetSnowPowerTerrainMin(float v) {
			this.snowPowerTerrain_min = v;
		}

		public float GetSnowPowerTerrainMin() {
			return this.snowPowerTerrain_min;
		}

		protected float snowPowerTerrain_max = 0f;

		public void SetSnowPowerTerrainMax(float v) {
			this.snowPowerTerrain_max = v;
		}

		public float GetSnowPowerTerrainMax() {
			return this.snowPowerTerrain_max;
		}

		protected float snowMinHeight = 0f;

		public void SetSnowMinHeight(float v) {
			this.snowMinHeight = v;
		}

		public float GetSnowMinHeight() {
			return this.snowMinHeight;
		}

		protected float snowAge_min = 0f;

		public void SetSnowAgeMin(float v) {
			this.snowAge_min = v;
		}

		public float GetSnowAgeMin() {
			return this.snowAge_min;
		}

		protected float snowAge_max = 0f;

		public void SetSnowAgeMax(float v) {
			this.snowAge_max = v;
		}

		public float GetSnowAgeMax() {
			return this.snowAge_max;
		}

		protected float thunderPower_min = 0f;

		public void SetThunderPowerMin(float v) {
			this.thunderPower_min = v;
		}

		public float GetThunderPowerMin() {
			return this.thunderPower_min;
		}

		protected float thunderPower_max = 0f;

		public void SetThunderPowerMax(float v) {
			this.thunderPower_max = v;
		}

		public float GetThunderPowerMax() {
			return this.thunderPower_max;
		}

		protected float cloudPower_min = 0f;

		public void SetCloudPowerMin(float v) {
			this.cloudPower_min = v;
		}

		public float GetCloudPowerMin() {
			return this.cloudPower_min;
		}

		protected float cloudPower_max = 0f;

		public void SetCloudPowerMax(float v) {
			this.cloudPower_max = v;
		}

		public float GetCloudPowerMax() {
			return this.cloudPower_max;
		}

		protected float cloudMinHeight = 0f;

		public void SetCloudMinHeight(float v) {
			this.cloudMinHeight = v;
		}

		public float GetCloudMinHeight() {
			return this.cloudMinHeight;
		}

		protected float cloudMaxHeight = 0f;

		public void SetCloudMaxHeight(float v) {
			this.cloudMaxHeight = v;
		}

		public float GetCloudMaxHeight() {
			return this.cloudMaxHeight;
		}

		protected float cloudSpeed_min = 0f;

		public void SetCloudSpeedMin(float v) {
			this.cloudSpeed_min = v;
		}

		public float GetCloudSpeedMin() {
			return this.cloudSpeed_min;
		}

		protected float cloudSpeed_max = 0f;

		public void SetCloudSpeedMax(float v) {
			this.cloudSpeed_max = v;
		}

		public float GetCloudSpeedMax() {
			return this.cloudSpeed_max;
		}

		protected float moonPhase_min = 0f;

		public void SetMoonPhaseMin(float v) {
			this.moonPhase_min = v;
		}

		public float GetMoonPhaseMin() {
			return this.moonPhase_min;
		}

		protected float moonPhase_max = 0f;

		public void SetMoonPhaseMax(float v) {
			this.moonPhase_max = v;
		}

		public float GetMoonPhaseMax() {
			return this.moonPhase_max;
		}

		/*
		 * protected float season = 0f; public void SetSeason(float v) {this.season =
		 * v;} public float GetSeason() {return this.season;}
		 */
	}

	/*
	 * float Hour; int startTime;
	 * 
	 * float Milisecond = 1; float SECOND = 1; float MINUTE = SECOND * 60; float
	 * HOUR = MINUTE * 60; float DAY = HOUR * 24; float seconds = 1; int Month=1;
	 * int Day; int Year; int weather = 8; float Temperature = 0f; public static int
	 * CYCLE_SPEED = 7200;
	 */
	protected static final Logger log = new Logger("Weather");
	public static String WEATHER_PLUGIN_NAME = "Weather";
	AccountDatabase aDB;
	ContentDatabase cDB;
	// static Random random = new Random();
	protected long serverStartTime;
	protected long currentSecondsRunning = 0;
	protected int serverYear = 1;
	protected int serverMonth = 1;
	protected int serverDay = 1;
	protected int serverHour = 0;
	protected int serverMinute = 0;
	protected float serverSeconds = 0;
	protected static float WORLD_TIME_SPEED = 1f;
	protected static String WORLD_TIME_ZONE = "UTC";
	static long WEATHER_UPDATE_PERIOD = 600;// In Seconds default 600 (10 minute)
	static boolean WEATHER_MONTH_SERVER_TIME = true;
	public static MessageType MSG_TYPE_GET_WEATHER = MessageType.intern("ao.GET_WAEATHER");

}
