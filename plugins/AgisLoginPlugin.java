package atavism.agis.plugins;


import atavism.agis.core.Cooldown;
import atavism.agis.database.AccountDatabase;
import atavism.agis.database.AuthDatabase;
import atavism.agis.database.CombatDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.objects.AgisStat;
import atavism.agis.objects.CharacterTemplate;
import atavism.agis.objects.CombatInfo;
import atavism.agis.objects.StatThreshold;
import atavism.agis.util.RequirementChecker;
import atavism.msgsys.GenericMessage;
import atavism.msgsys.Message;
import atavism.server.engine.*;
import atavism.server.util.Log;
import atavism.server.util.SecureToken;
import atavism.server.util.SecureTokenManager;
import atavism.server.util.AORuntimeException;
import atavism.server.objects.Entity;
import atavism.server.objects.DisplayContext;
import atavism.server.objects.AOObject;
import atavism.server.network.AOByteBuffer;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.ProxyPlugin;
import atavism.server.worldmgr.LoginPlugin;
import atavism.server.worldmgr.LoginQueueManager.QueueResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import java.util.concurrent.TimeUnit;


/** Agis LoginPlugin implementation.  Reads and stores characters using
    the {@link atavism.server.engine.Engine} database.

*/
public class AgisLoginPlugin extends LoginPlugin {
	
	public void onActivate() {
		loadCharacterFactoryTemplatesFromDatabase();
		super.onActivate();

		aDB = new AccountDatabase(true);
		authDB = new AuthDatabase();
		QueueSizeTask qst = new QueueSizeTask();
		Engine.getExecutor().scheduleAtFixedRate(qst, 5, 15, TimeUnit.SECONDS);
	}

	protected class QueueSizeTask implements Runnable{
		public QueueSizeTask(){

		}
		public void run(){
			try {
				authDB.saveServerQueueUpdate(queueManager.getQueuesSize());
			}catch (Exception e){
				Log.exception("QueueSizeTask Exception",e);
			}
		}
	}
	/**
	 * Loads in Character Creation Templates from the Content Database.
	 */
	public void loadCharacterFactoryTemplatesFromDatabase() {
    	ContentDatabase cDB = new ContentDatabase(false);
        characterTemplates = cDB.loadCharacterFactoryTemplates();
        cDB.loadEditorOptions();
     
        String minLength = cDB.loadGameSetting("CHARACTER_NAME_MIN_LENGTH");
        if (minLength != null)
        	CHARACTER_NAME_MIN_LENGTH = Integer.parseInt(minLength);
        String maxLength = cDB.loadGameSetting("CHARACTER_NAME_MAX_LENGTH");
        if (maxLength != null)
        	CHARACTER_NAME_MAX_LENGTH = Integer.parseInt(maxLength);
        
        String allowSpaces = cDB.loadGameSetting("CHARACTER_NAME_ALLOW_SPACES");
        if (allowSpaces != null)
        	CHARACTER_NAME_ALLOW_SPACES = Boolean.parseBoolean(allowSpaces);
        String allowNumbers = cDB.loadGameSetting("CHARACTER_NAME_ALLOW_NUMBERS");
        if (allowNumbers != null)
        	CHARACTER_NAME_ALLOW_NUMBERS = Boolean.parseBoolean(allowNumbers);
        
        String playerPerceptionRadius = cDB.loadGameSetting("PLAYER_PERCEPTION_RADIUS");
        if (playerPerceptionRadius != null)
        	PLAYER_PERCEPTION_RADIUS = Integer.parseInt(playerPerceptionRadius);

		// Load Game settings for Login Queue
		String loginQueueRate = cDB.loadGameSetting("LOGIN_QUEUE_RATE");
		if (loginQueueRate != null) {
			LOGIN_QUEUE_RATE = Float.parseFloat(loginQueueRate);
			Log.info("Game Settings LOGIN_QUEUE_RATE set to " + LOGIN_QUEUE_RATE);
		}

		String loginQueueMaxUsers = cDB.loadGameSetting("LOGIN_QUEUE_MAX_USERS");
		if (loginQueueMaxUsers != null) {
			LOGIN_QUEUE_MAX_USERS = Integer.parseInt(loginQueueMaxUsers);
			Log.info("Game Settings LOGIN_QUEUE_MAX_USERS set to " + LOGIN_QUEUE_MAX_USERS);
		}
		String loginQueueMaxInterval = cDB.loadGameSetting("LOGIN_QUEUE_MAX_INTERVAL");
		if (loginQueueMaxInterval != null) {
			LOGIN_QUEUE_MAX_INTERVAL = Long.parseLong(loginQueueMaxInterval);
			Log.info("Game Settings LOGIN_QUEUE_MAX_INTERVAL set to " + LOGIN_QUEUE_MAX_INTERVAL);
		}

		String devMode = cDB.loadGameSetting("SERVER_DEVELOPMENT_MODE");
		if (devMode != null) {
			EnginePlugin.DevMode = Boolean.parseBoolean(devMode);
			Log.info("Game Settings EnginePlugin.DevMode set to " + EnginePlugin.DevMode);
		}
		   CombatDatabase cbDB = new CombatDatabase(false);
		if(!Engine.isAIO()) {
			CombatPlugin.STAT_LIST = cbDB.LoadStats();
			CombatPlugin.STAT_PROFILES = cbDB.LoadStatProfiles();
		}
       // cDB.close();
	}

	protected void ReloadTemplates(Message msg) {
		Log.error("AgisLoginPlugin ReloadTemplates Start");
		loadCharacterFactoryTemplatesFromDatabase();

		Log.error("AgisLoginPlugin ReloadTemplates End");
	}

    /** Return character list.  For each character, the following
        properties are returned:
        <li>Long characterId: character oid</li>
        <li>String characterName</li>
        <li>String race</li>
        <li>String gender</li>
        <li>String model</li>
        <li>String world</li>
        <p>
        This implementation does not truly authorize the auth token.
        The auth token should contain a 32-bit integer which is taken as
        the account id.  (bitwise negated if SecureToken is true).
        <p>
        The returned world token is a fixed place holder.
        @param message Character request message.
        @param clientSocket Socket to the client.
    */
	protected CharacterResponseMessage handleCharacterRequestMessage(CharacterRequestMessage message, SocketHandler clientSocket) {
		AOByteBuffer authToken = message.getAuthToken();

		CharacterResponseMessage response = new CharacterResponseMessage();
		response.setWorldFilesUrl(Engine.getProperty("atavism.world_files_url"));
		OID uid = null;
		String accountName = null;
		byte version = authToken.getByte();
		authToken.rewind();
		if (version == 7) {
			Log.debug("LOGIN: got version 7");
			authToken.getByte();
			uid = OID.fromLong(authToken.getLong());
			clientSocket.setAccountId(uid);
		} else if (clientSocket.getAccountId() == null) {
			SecureToken token = SecureTokenManager.getInstance().importToken(authToken);
			boolean valid = true;

			Log.debug("SecureToken: " + token);

			if (LoginPlugin.SecureToken) {
				valid = token.getValid();
			}
			if (valid && LoginPlugin.WorldId != null && token.getProperty("world_id").equals(LoginPlugin.WorldId)) {
				valid = false;
			}
			if (valid && token.getIssuerId() == null || (!token.getIssuerId().equals("master") && !token.getIssuerId().contains("proxy"))) {
				valid = false;
			}

			if (!valid) {
				response.setErrorMessage("invalid master token");
				return response;
			}

			Serializable uidObj = token.getProperty("account_id");
			if (uidObj instanceof Integer) {
				uid = OID.fromLong(((Integer) uidObj).longValue());
			}
			if (uidObj instanceof Long) {
				uid = OID.fromLong(((Long) uidObj).longValue());
			}
			if (uidObj instanceof String) {
				uid = OID.fromLong(Long.parseLong((String) uidObj));
			}
			accountName = (String) token.getProperty("account_name");

			clientSocket.setAccountId(uid);
			clientSocket.setAccountName(accountName);
		} else {
			uid = clientSocket.getAccountId();
			accountName = clientSocket.getAccountName();
		}

		if(sockets.containsKey(uid)) {
			SocketHandler sh = sockets.get(uid);
			if(!sh.equals(clientSocket)) {
				sh.Close();
				sockets.remove(uid);
			}
		}
		if(!sockets.containsKey(uid)) {
			sockets.put(uid, clientSocket);
		}
		response.setAccount(uid);
        QueueResult q = queueManager.checkCCUQueue(uid);
        if (q.queuePosition != 0) {
            Log.debug("Player " + uid + " waiting in CCU queue at position " + q.queuePosition);
            response.setPositionInQueue(q.queuePosition);
            return response;
        }
        queueManager.addPlayerAccount(uid);

        // Let the proxy know that an account is logging in so if the account is already logged in it will be disconnected
		Log.debug("About to send account login with accountId=" + uid);
		GenericMessage accountLoginMessage = new GenericMessage(ProxyPlugin.MSG_TYPE_ACCOUNT_LOGIN);
		accountLoginMessage.setProperty("accountId", uid);
		Engine.getAgent().sendBroadcast(accountLoginMessage);

	      /*AOByteBuffer authToken = message.getAuthToken();
        int token = authToken.getInt();
        OID uid = OID.fromLong(LoginPlugin.SecureToken ? ~token : token);

        if (Log.loggingDebug)
            Log.debug("AgisLoginPlugin: handleCharacterRequestMessage: "+
                        "read in token=" + token + ", uid=" + uid +
                        ", SecureToken=" + LoginPlugin.SecureToken);

        // TODO: verify token
        Log.warn("need to verify token");

        CharacterResponseMessage response = new CharacterResponseMessage();
        response.setWorldToken("fakeEncryptionToken");*/

        // get character data out of DB
		Database db = Engine.getDatabase();
		String worldName = Engine.getWorldName();
		List<OID> charIds = db.getGameIDs(worldName, uid);

		int characterCount = 0;
		String characterNames = "";
		for (OID oid : charIds) {
			if (Log.loggingDebug)
				Log.debug("AgisLoginPlugin: character oid: " + oid);

			// load the user from the database
			Entity entity = Engine.getDatabase().loadEntity(oid, Namespace.WORLD_MANAGER);
			CombatInfo entity2 = (CombatInfo) Engine.getDatabase().loadEntity(oid, Namespace.COMBAT);

			if (Log.loggingDebug)
				Log.debug("AgisLoginPlugin: loaded combatinfo " + entity2);
			if (Log.loggingDebug)
				Log.debug("AgisLoginPlugin: loaded character from db: " + entity);

			// prepare character info
			// OID and name
			Map<String, Serializable> charInfo = new HashMap<String, Serializable>();
			charInfo.put("characterId", entity.getOid());
			charInfo.put("characterName", entity.getName());
			Log.debug("LOGIN: props: " + entity.getPropertyMap());

			// get a few more properties
			int world = entity.getIntProperty("world");
			if (aDB.getIslandAdministrator(world) != null && aDB.getIslandAdministrator(world).equals(uid)) {
				charInfo.put("worldAdmin", true);
			} else {
				charInfo.put("worldAdmin", false);
			}

			// World Manager props
			for (String prop : entity.getPropertyMap().keySet()) {
				if (entity.getProperty(prop) instanceof String || entity.getProperty(prop) instanceof Integer || entity.getProperty(prop) instanceof Float) {
					charInfo.put(prop, entity.getProperty(prop));
				} else if (entity.getProperty(prop) instanceof HashMap) {
					HashMap<Object, Serializable> mapProps = (HashMap<Object, Serializable>) entity.getProperty(prop);
					Log.debug("PROPS: mapProps: " + mapProps);
					for (Object key : mapProps.keySet()) {
						if (!(key instanceof String)) {
							continue;
						}
						String sKey = (String) key;
						if (mapProps.get(sKey) instanceof Double) {
							double val = (Double) mapProps.get(sKey);
							charInfo.put("custom:" + prop + ":" + sKey, (float) val);
							Log.debug("CV: converted double " + sKey + " to float");
						} else {
							charInfo.put("custom:" + prop + ":" + sKey, mapProps.get(sKey));
						}
					}
				}
			}

			// Combat props
			if(entity2!=null) {
				for (String prop : entity2.getPropertyMap().keySet()) {
					if (entity2.getProperty(prop) instanceof String || entity2.getProperty(prop) instanceof Integer || entity2.getProperty(prop) instanceof Float) {
						charInfo.put(prop, entity2.getProperty(prop));
					} else if (entity2.getProperty(prop) instanceof AgisStat) {
						charInfo.put(prop, entity2.statGetCurrentValue(prop));
					}
				}

				// Race, Class and level
				int raceID = (Integer) entity.getProperty("race");
				int classID = (Integer) entity2.getProperty("aspect");
				if (!entity2.getPropertyMap().containsKey("genderId")) {
					int genderId = RequirementChecker.getIdEditorOptionChoice("Gender", (String) entity.getProperty("gender"));
					charInfo.put("genderId", genderId);
				}

				charInfo.put("race", RequirementChecker.getRace(raceID));
				charInfo.put("raceId", raceID);
				charInfo.put("aspect", RequirementChecker.getClass(classID));
				charInfo.put("aspectId", classID);
				charInfo.put("level", entity2.statGetCurrentValue("level"));
				charInfo.put("deathPerma", entity2.getDeathPermanently());
			}
			charInfo.put("accountId", uid);
			characterNames += entity.getName() + "(" + entity.getOid() + "),";
			characterCount++;

			// Get the display context
			DisplayContext displayContext = getDisplayContext(entity);
			if (displayContext != null) {
				charInfo.put("displayContext", marshallDisplayContext(displayContext));
			}

			setCharacterProperties(charInfo, entity);
			// apply Cooldowns
			LinkedList<Cooldown> cooldownList = CombatPlugin.getCooldowns(oid);
			if (cooldownList.size() > 0) {
				Log.debug("Login: Apply Cooldowns " + cooldownList.size() + " to " + oid + " " + entity.getName());
				double cooldownMod = 100;
				if(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT!=null) {
					double statValue = entity2.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("Login: CooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_COOLDOWN_MOD_STAT);
						int pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("Login: CooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("Login: CooldownMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("Login: CooldownMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						/*if (pointsCalculated < statValue) {
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						}*/
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("Login: CooldownMod calculated: " + calculated);	
					cooldownMod = calculated;//Math.round(calculated/100f);
					if (Log.loggingDebug)
						Log.debug("Login: CooldownMod calculated="+calculated+" mod="+cooldownMod);
					
					
				}
				double cooldownGlobalMod = 100;
				if(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT!=null) {
					double statValue = entity2.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("Login: GlobalCooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_GLOBAL_COOLDOWN_MOD_STAT);
						int pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("Login: GlobalCooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("Login: GlobalCooldownMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("Login: GlobalCooldownMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						/*if (pointsCalculated < statValue) {
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						}*/
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("Login: GlobalCooldownMod calculated: " + calculated);	
					cooldownGlobalMod = calculated;//Math.round(calculated/100f);
					if (Log.loggingDebug)
						Log.debug("Login: GlobalCooldownMod calculated="+calculated+" mod="+cooldownGlobalMod);
					
					
				}
				double cooldownWeaponMod = 100;
				if(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT!=null) {
					double statValue = entity2.statGetCurrentValueWithPrecision(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
					if (Log.loggingDebug)
						Log.debug("Login: WeaponCooldownMod statValue: " + statValue + " of " + CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
					double calculated = 0;
					if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT)) {
						StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(CombatPlugin.ABILITY_WEAPON_COOLDOWN_MOD_STAT);
						int pointsCalculated = 0;
						for (int i = 1; i <= def.getPoints().size(); i++) {
							if (Log.loggingDebug)
								Log.debug("Login: WeaponCooldownMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " calculated=" + calculated);
							if (statValue <= def.getThresholds().get(i)) {
								Log.debug("Login: WeaponCooldownMod statValue < th");
								if (statValue - pointsCalculated < 0)
									break;
								calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += statValue - pointsCalculated;
							} else {
								Log.debug("Login: WeaponCooldownMod statValue > th");
								calculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
								pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
							}
						}
						/*if (pointsCalculated < statValue) {
							calculated += Math.round((statValue - pointsCalculated) / def.getPoints().get(def.getPoints().size()));
						}*/
					} else {
						calculated = statValue;
					}
					if (Log.loggingDebug)
						Log.debug("Login: WeaponCooldownMod calculated: " + calculated);	
					cooldownWeaponMod = calculated;//Math.round(calculated/100f);
					if (Log.loggingDebug)
						Log.debug("Login: WeaponCooldownMod calculated="+calculated+" mod="+cooldownWeaponMod);
					
					
				}
				//Log.error("AgisLoginManager: ATTACK_SPEED_STAT="+CombatPlugin.ATTACK_SPEED_STAT);
				Cooldown.activateCooldowns(cooldownList, entity2, cooldownGlobalMod, cooldownWeaponMod, cooldownMod, entity2.statGetBaseValue(CombatPlugin.ATTACK_SPEED_STAT));
				//Cooldown.activateCooldowns(cooldownList, entity2, 100);
			} else {
				Log.debug("Login: No Cooldowns ");
			}
			//Log.error("AgisLoginManager: ATTACK_SPEED_STAT="+CombatPlugin.ATTACK_SPEED_STAT);
			response.addCharacter(charInfo);
		}

		response.setCharacterSlots(aDB.getNumCharacterSlots(uid));

		Log.info("LoginPlugin: GET_CHARACTERS remote=" + clientSocket.getRemoteSocketAddress() + " account=" + uid + " accountName=" + clientSocket.getAccountName() + " count=" + characterCount + " names=" + characterNames);

		clientSocket.setCharacterInfo(response.getCharacters());

		// Update account table to indicate this is the last server the player connected to
		authDB.updateAccountCurrentWorld(uid);

		return response;
    }

    /** Delete a character.  On success, the returned
        properties is the supplied properties plus new properties:
        <li>Boolean status: TRUE</li>
        <li>whatever the character factory adds to the properties</li>
        <p>
        On an internal failure, the returned properties contain only:
        <li>Boolean status: FALSE</li>
        <li>String errorMessage</li>
        @param message Character delete message.
        @param clientSocket Socket to the client.
    */
	protected CharacterDeleteResponseMessage handleCharacterDeleteMessage(CharacterDeleteMessage message, SocketHandler clientSocket)  {
		CharacterDeleteResponseMessage response = new CharacterDeleteResponseMessage();

        Map<String,Serializable> props = message.getProperties();
        Map<String,Serializable> errorProps = new HashMap<String,Serializable>();

		if (Log.loggingDebug) {
			Log.debug("AgisLoginPlugin: delete character properties: ");
			for (Map.Entry<String, Serializable> entry : props.entrySet()) {
				Log.debug("character property " + entry.getKey() + "=" + entry.getValue());
			}
		}

		errorProps.put("status", Boolean.FALSE);
		response.setProperties(errorProps);

		if (clientSocket.getAccountId() == null) {
			errorProps.put("errorMessage", "Permission denied");
			return response;
		}

		OID uid = clientSocket.getAccountId();

		OID oid = (OID) props.get("characterId");

		Database db = Engine.getDatabase();
		List<OID> characterOids = null;
		try {
			characterOids = db.getGameIDs(Engine.getWorldName(), uid);
		} catch (AORuntimeException ex) {
			errorProps.put("errorMessage", ex.toString());
			return response;
		}

		if (!characterOids.contains(oid)) {
			errorProps.put("errorMessage", "Character does not exist.");
			return response;
		}

	/*	CharacterFactory factory= getCharacterGenerator().getCharacterFactory();
        if (factory == null) {
            Log.error("AgisLoginPlugin: missing character factory");
            errorProps.put("errorMessage", "Missing character factory.");
            return response;
        }

		String errorMessage;
		try {
			errorMessage = factory.deleteCharacter(Engine.getWorldName(), uid, oid, props);
		} catch (Exception ex) {
			Log.exception("Exception deleting character", ex);
			errorProps.put("errorMessage", ex.toString());
			return response;
		}

        if (errorMessage != null) {
            errorProps.put("errorMessage", errorMessage);
            return response;
        }
*/
        String characterName = db.getObjectName(oid,
            Namespace.OBJECT_MANAGER);

        try {
            db.deletePlayerCharacter(oid);
        }
        catch (Exception ex) {
            errorProps.put("errorMessage", ex.toString());
            return response;
        }

        try {
            db.deleteObjectData(oid);
        }
        catch (Exception ex) { }

        Log.info("LoginPlugin: CHARACTER_DELETE remote=" + clientSocket.getRemoteSocketAddress() +
            " account=" + uid +
            " accountName=" + clientSocket.getAccountName() +
            " oid=" + oid + " name=" + characterName);
        
        // Log it
        OID accountID = uid;
        HashMap<String, Serializable> logData = new HashMap<String, Serializable>();
        logData.put("aspect", props.get("aspect"));
        DataLoggerClient.logData("CHARACTER_DELETED", oid, null, accountID, logData);
        // Delete character to account db
        
        DataLoggerClient.characterDeleted(accountID, oid, characterName);
        authDB.characterDeleted(accountID, oid);
        aDB.deletePlayerCollectionData(oid);
        aDB.deletePlayerRanking(oid);
        props.put("status", Boolean.TRUE);

        response.setProperties(props);
        return response;
    }

    /** Create a character.  The character properties are passed to the
        global character generator.  On success, the returned
        properties is the supplied properties plus new properties:
        <li>Boolean status: TRUE</li>
        <li>Long characterId: new character's oid</li>
        <li>whatever the character factory adds to the properties</li>
        <p>
	If the properties contains "errorMessage" after calling the
	character factory, the character is not saved, and the
	properties are returned to the client.
        <p>
        On an internal failure, the returned properties contain only:
        <li>Boolean status: FALSE</li>
        <li>String errorMessage</li>
        @param message Character create message.
        @param clientSocket Socket to the client.
    */
	protected CharacterCreateResponseMessage handleCharacterCreateMessage(CharacterCreateMessage message, SocketHandler clientSocket) {
		Log.debug("AgisLoginPlugin: Create Character");
		CharacterCreateResponseMessage response = new CharacterCreateResponseMessage();

		Map<String, Serializable> props = message.getProperties();

		if (clientSocket.getAccountId() == null) {
			props.clear();
			props.put("status", Boolean.FALSE);
			props.put("errorMessage", "Permission denied");
			response.setProperties(props);
			return response;
		}

		OID uid = clientSocket.getAccountId();

		String propertyText = "";
		for (Map.Entry<String, Serializable> entry : props.entrySet()) {
			propertyText += "[" + entry.getKey() + "=" + entry.getValue() + "] ";
		}
		props.put("accountName", clientSocket.getAccountName());
		Log.info("LoginPlugin: CHARACTER_CREATE remote=" + clientSocket.getRemoteSocketAddress() + " account=" + uid + " accountName=" + clientSocket.getAccountName() + " properties=" + propertyText);

		// try to create default character
		Database db = Engine.getDatabase();
		String worldName = Engine.getWorldName();

		String race = (String) props.get("race");
		String aspect = (String) props.get("aspect");
		// Get the ids of the race and aspect
		int raceID = RequirementChecker.getRaceID(race);
		int classID = RequirementChecker.getClassID(aspect);
		
		String[] forbiddenKeys = new String[] {"adminlevel","accountId"}; 
		for(String key : forbiddenKeys) {
			if(props.containsKey(key)) {
				props.remove(key);
			}
			if(props.containsKey("custom:"+key)) {
				props.remove("custom:"+key);
			}
		}
		
		if (/* getCharacterGenerator().getCharacterFactory() != null */characterTemplates.containsKey(raceID + " " + classID)) {
			if (Log.loggingDebug)
				Log.debug("AgisLoginPlugin: creating character");
			OID oid = null;
			try {
				// oid =
				// getCharacterGenerator().getCharacterFactory().createCharacter(worldName, uid,
				// props);
				String name = (String) props.get("characterName");
				CharacterTemplate factory = characterTemplates.get(raceID + " " + classID);
				String nameCheckResult = factory.checkName(name);
				if (nameCheckResult != null) {
					props.put("errorMessage", nameCheckResult);
				} else {
					nameCheckResult = isNameTaken(name);
					if (!nameCheckResult.equals("")) {
						props.put("errorMessage", nameCheckResult);
					} else {
						oid = factory.createCharacter(worldName, uid, props);
					}
				}
			} catch (Exception e) {
				Log.exception("Caught exception in character factory: ", e);
				props.clear();
				props.put("errorMessage", "Internal error. Please check server logs");
			}

			if (oid == null) {
				Log.error("Character factory returned null OID");
				if (props.get("errorMessage") == null)
					props.put("errorMessage", "Internal error. Please check server logs");
			}

			if (props.get("errorMessage") != null) {
				Log.error("AgisLoginPlugin: character creation failed, account=" + uid + " errorMessage=" + props.get("errorMessage") + " characterName=" + props.get("characterName"));
				props.put("status", Boolean.FALSE);
				response.setProperties(props);
				return response;
			}

			if (Log.loggingDebug)
				Log.debug("AgisLoginPlugin: saving oid " + oid);
			boolean success = false;
			if (oid != null)
				success = ObjectManagerClient.saveObject(oid);

			if (success) {
				if (Log.loggingDebug)
					Log.debug("AgisLoginPlugin: saved oid " + oid);

				// map the ao id to the game id
				db.mapAtavismID(worldName, uid, oid);

				props.put("status", Boolean.TRUE);
				props.put("characterId", oid);

				// Load the entity to get additional properties
				Entity entity = Engine.getDatabase().loadEntity(oid, Namespace.WORLD_MANAGER);
				CombatInfo entity2 = (CombatInfo) Engine.getDatabase().loadEntity(oid, Namespace.COMBAT);

				// World Manager props
				for (String prop : entity.getPropertyMap().keySet()) {
					if (entity.getProperty(prop) instanceof String || entity.getProperty(prop) instanceof Integer || entity.getProperty(prop) instanceof Float) {
						props.put(prop, entity.getProperty(prop));
					} else if (entity.getProperty(prop) instanceof HashMap) {
						HashMap<Object, Serializable> mapProps = (HashMap<Object, Serializable>) entity.getProperty(prop);
						Log.debug("PROPS: mapProps: " + mapProps);
						for (Object key : mapProps.keySet()) {
							if (!(key instanceof String)) {
								continue;
							}
							String sKey = (String) key;
							if (mapProps.get(sKey) instanceof Double) {
								double val = (Double) mapProps.get(sKey);
								props.put("custom:" + prop + ":" + sKey, (float) val);
								Log.debug("CV: converted double " + sKey + " to float");
							} else {
								props.put("custom:" + prop + ":" + sKey, mapProps.get(sKey));
							}
						}
					}
				}

				// Combat props
				for (String prop : entity2.getPropertyMap().keySet()) {
					if (entity2.getProperty(prop) instanceof String || entity2.getProperty(prop) instanceof Integer || entity2.getProperty(prop) instanceof Float) {
						props.put(prop, entity2.getProperty(prop));
					} else if (entity2.getProperty(prop) instanceof AgisStat) {
						props.put(prop, entity2.statGetCurrentValue(prop));
					}
				}
				
				
				props.put("characterName", entity.getName());
				// Send down the world the character is set to join
				props.put("world", entity.getIntProperty("world"));
				props.put("model", entity.getStringProperty("model"));
				props.put("level", entity2.statGetCurrentValue("level"));
				int _raceID = (Integer) entity.getProperty("race");
				int _classID = (Integer) entity2.getProperty("aspect");
				props.put("race", RequirementChecker.getRace(_raceID));
				props.put("aspect", RequirementChecker.getClass(_classID));
				props.put("raceId", _raceID);
				props.put("aspectId", _classID);
				if (aDB.getIslandAdministrator(entity.getIntProperty("world")) != null && aDB.getIslandAdministrator(entity.getIntProperty("world")).equals(uid)) {
					props.put("worldAdmin", true);
				} else {
					props.put("worldAdmin", false);
				}
				DisplayContext displayContext = getDisplayContext(entity);
				if (displayContext != null) {
					props.put("displayContext", marshallDisplayContext(displayContext));
				}

				setCharacterProperties(props, entity);

				clientSocket.getCharacterInfo().add(props);

				Log.info("LoginPlugin: CHARACTER_CREATE remote=" + clientSocket.getRemoteSocketAddress() + " account=" + uid + " accountName=" + clientSocket.getAccountName() + " oid=" + oid + " name=" + entity.getName());
				// Log it
				OID accountID = uid;
				HashMap<String, Serializable> logData = new HashMap<String, Serializable>();
				logData.put("aspect", props.get("aspect"));
				DataLoggerClient.logData("CHARACTER_CREATED", oid, null, accountID, logData);
				// Add character to account db
				//String characterName = aDB.getCharacterNameByOid(oid);
				//String characterName = WorldManagerClient.getObjectInfo(oid).name;
				DataLoggerClient.characterCreated(accountID, clientSocket.getAccountName(), oid, entity.getName());
				authDB.addAccountCharacter(accountID, oid);
			} else {
				if (Log.loggingTrace)
					Log.trace("AgisLoginPlugin: failed to save oid " + oid);
				props.clear();
				props.put("status", Boolean.FALSE);
				props.put("errorMessage", "Failed to save new character");
			}
		} else {
			if (Log.loggingTrace)
				Log.trace("AgisLoginPlugin: missing character factory");
			props.clear();
			props.put("status", Boolean.FALSE);
			props.put("errorMessage", "Could not find Character Template for the Race and Class specified");
		}

        response.setProperties(props);
        return response;
    }
    
    /** Returns a list of servers that the player can connect to.
    	The returned world token is a fixed place holder.
    	@param message Server List request message.
    	@param clientSocket Socket to the client.
    */
    protected ServerListResponseMessage handleServerListRequestMessage(
            ServerListRequestMessage message, SocketHandler clientSocket)
    {
    	//AOByteBuffer authToken = message.getAuthToken();
    	ServerListResponseMessage response = new ServerListResponseMessage();
    	OID uid = clientSocket.getAccountId();
    	
    	// get number of characters player has on each server
    	HashMap<Integer, Integer> serverCharacters = authDB.getWorldWithPlayersCharacters((int)uid.toLong());

    	// send the list of servers back to the client
    	int serverCount = 0;
    	String serverNames = "";
    	for (HashMap<String, Serializable> serverProps : authDB.getServers()) {
    		serverNames += serverProps.get("name") +"("+serverProps.get("server_address")+"),";
    		serverCount++;
    		
    		int serverID = (Integer)serverProps.get("id");
    		if (serverCharacters.containsKey(serverID)) {
    			serverProps.put("character_count", serverCharacters.get(serverID));
    		} else {
    			serverProps.put("character_count", 0);
    		}
    		response.addServer(serverProps);
    	}

		Log.info("LoginPlugin: GET_CHARACTERS remote=" + clientSocket.getRemoteSocketAddress() + " account=" + uid + " accountName=" + clientSocket.getAccountName() + " count=" + serverCount + " names=" + serverNames);

    	return response;
    }
    
    /*private ArrayList<HashMap<String, Serializable>> getServerList(OID accountOid) {
    	ArrayList<HashMap<String, Serializable>> serverList = new ArrayList<HashMap<String, Serializable>>();
    	
    	HashMap<String, Serializable> server_one = new HashMap<String, Serializable>();
    	server_one.put("name", "Server One");
    	server_one.put("hostname", "localhost");
    	server_one.put("port", 5040);
    	serverList.add(server_one);
    	
    	HashMap<String, Serializable> server_two = new HashMap<String, Serializable>();
    	server_two.put("name", "Server Two");
    	server_two.put("hostname", "192.168.1.102");
    	server_two.put("port", 5040);
    	serverList.add(server_two);
    	
    	return serverList;
    }*/
    
    /**
     * Checks if the name is already used by another player. Will be updated
     * to also check a list of banned names in the future.
     * @param name
     * @return
     */
    public String isNameTaken(String name) {
    	Database db = Engine.getDatabase();
    	boolean taken = db.characterNameTaken(name);
    	if (taken) {
    		return "That name is already in use.  Please pick another.";
    	}
    	// TODO: add in checks for list of reserved/banned names
    	return "";
    }

    /**
     * Gets the Display Context for the player.
     * Note: The plan is to remove the display content system from Atavism.
     * @param entity
     * @return
     */
    protected DisplayContext getDisplayContext(Entity entity)
    {
        DisplayContext displayContext = null;
        AOObject aoObject =
            (AOObject) Engine.getDatabase().loadEntity(entity.getOid(), Namespace.WORLD_MANAGER);
        displayContext = aoObject.displayContext();

        if (displayContext != null) {
            if (Log.loggingDebug)
                Log.debug("Display context for '"+entity.getName()+"': "+
                    displayContext);
        }

        return displayContext;
    }

    /**
     * Converts the display context into a String for sending to the client.
     * @param displayContext
     * @return
     */
    protected String marshallDisplayContext(DisplayContext displayContext)
    {
        String result = displayContext.getMeshFile();
        for (DisplayContext.Submesh submesh : displayContext.getSubmeshes()) {
            result += "\002" + submesh.getName() + "\002" + submesh.getMaterial();
        }
        Map<String, DisplayContext> childDCs=displayContext.getChildDCMap();
        for (Map.Entry<String,DisplayContext> entry : childDCs.entrySet()) {
            DisplayContext childDC = entry.getValue();
            result += "\001" + entry.getKey() + "\002" + childDC.getMeshFile();
            for (DisplayContext.Submesh submesh : childDC.getSubmeshes()) {
                result += "\002" + submesh.getName() + "\002" + submesh.getMaterial();
            }
        }
        return result;
    }

    /**
     * Fills the props Map with properties from the character.
     * @param props
     * @param entity
     */
    protected void setCharacterProperties(Map<String,Serializable> props, Entity entity) {
    	for (Map.Entry<Namespace, Set<String>> entry : characterProps.entrySet()) {
    		Namespace namespace = entry.getKey();
    		Entity subObj = Engine.getDatabase().loadEntity(entity.getOid(), namespace);
    		for (String propName : entry.getValue()) {
    			Serializable propValue = subObj.getProperty(propName);
    			if (propValue != null) {
    				props.put(propName, propValue);
    			}
    		}
    	}
    }

    /**
     * Registers a property into the characterProps Map.
     * @param namespace
     * @param propName
     */
    public static void registerCharacterProperty(Namespace namespace, String propName) {
    	Set<String> propSet = characterProps.get(namespace);
    	if (propSet == null) {
    		propSet = new HashSet<String>();
    		characterProps.put(namespace, propSet);
    	}
    	propSet.add(propName);
    }
    protected static Map<Namespace, Set<String>> characterProps = new HashMap<Namespace, Set<String>>();
    
    /**
     * A CharacterTemplate Map that has keys based on Race and Class (combined into one String).
     */
    public static Map<String, CharacterTemplate> characterTemplates = new HashMap<String, CharacterTemplate>();
    
    protected AccountDatabase aDB;
    protected AuthDatabase authDB;
    public static ConcurrentHashMap<OID, SocketHandler> sockets = new ConcurrentHashMap<OID, SocketHandler>(); 
    // Account levels
    public static final int ACCOUNT_ADMIN = 5;
	public static final int ACCOUNT_GM = 3;
	public static final int ACCOUNT_NORMAL = 1;
	
	// Other Game Settings
	public static int CHARACTER_NAME_MIN_LENGTH = 3;
	public static int CHARACTER_NAME_MAX_LENGTH = 14;
	public static boolean CHARACTER_NAME_ALLOW_SPACES = true;
	public static boolean CHARACTER_NAME_ALLOW_NUMBERS = true;
	public static int PLAYER_PERCEPTION_RADIUS = 75;
}

