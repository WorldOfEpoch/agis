package atavism.agis.plugins;

import java.io.Serializable;
import java.io.File;
import java.io.FileWriter;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedList;

import atavism.msgsys.Message;
import atavism.msgsys.MessageAgent;
import atavism.msgsys.MessageTypeFilter;
import atavism.msgsys.ResponseMessage;

import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.Hook;
import atavism.server.engine.OID;

import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.plugins.WorldManagerClient;

import atavism.agis.database.AdminQueries;
import atavism.agis.database.AccountDatabase;
import atavism.agis.objects.Achievement;
import atavism.agis.plugins.DataLoggerClient;
import atavism.agis.plugins.DataLoggerClient.DataLogMessage;

import atavism.server.util.AORuntimeException;
import atavism.server.util.Log;

/**
 * Handles logging the events that occur. Also deals with storing player stats and updating achievements.
 * @author Andrew
 *
 */
public class DataLoggerPlugin extends EnginePlugin {
	public String getName() {
		return PLUGIN_NAME;
	}

    public static final String PLUGIN_NAME = "DataLogger";

    protected static final String LOGS_DIR = System.getProperty("atavism.logs");
    protected static FileWriter out = null;

    //protected Database db = null;
    protected static AdminQueries logQueries = new AdminQueries(true);
    protected static AccountDatabase accDB = new AccountDatabase(true);

    public DataLoggerPlugin() {
        super(PLUGIN_NAME);
        setPluginType(PLUGIN_NAME);
        if (out == null) {
            try {
                out = new FileWriter(new File(LOGS_DIR + "/datalogger_db_fallback.out"));
            } catch (Exception e) {
            }
        }
        /*db = new Database(Engine.getDBDriver());
        Log.debug("connecting to " + this.getDBHostname() + "user = " + this.getDBUser() + " passwd=" + this.getDBPassword());
        db.connect(this.getDBUrl(), this.getDBUser(), this.getDBPassword());*/
    }

    public void onActivate() {
        try {
            registerHooks();

            MessageTypeFilter filter = new MessageTypeFilter();
            filter.addType(DataLoggerClient.MSG_TYPE_EVENT_LOG);
            filter.addType(DataLoggerClient.MSG_TYPE_DATA_LOG);
            filter.addType(DataLoggerClient.MSG_TYPE_CHARACTER_CREATED);
            filter.addType(DataLoggerClient.MSG_TYPE_CHARACTER_DELETED);
            Engine.getAgent().createSubscription(filter, this);
            
            // Create responder subscription
    	 	MessageTypeFilter filter2 = new MessageTypeFilter();
    	 	filter2.addType(LoginMessage.MSG_TYPE_LOGIN);
    	 	filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
    	 	Engine.getAgent().createSubscription(filter2, this,
    	 			MessageAgent.RESPONDER);
    	 	
    	 	accDB.updateServerStat("restart");
        } catch (Exception e) {
            throw new AORuntimeException("onActivate failed", e);
        }
    }

    public void registerHooks() {
        getHookManager().addHook(DataLoggerClient.MSG_TYPE_DATA_LOG, new DataLogMessageHook());
        getHookManager().addHook(DataLoggerClient.MSG_TYPE_EVENT_LOG, new LogEventHook());
        getHookManager().addHook(DataLoggerClient.MSG_TYPE_CHARACTER_CREATED, new CharacterCreatedHook());
        getHookManager().addHook(DataLoggerClient.MSG_TYPE_CHARACTER_DELETED, new CharacterDeletedHook());
        // Hook to process login/logout messages
		getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
		getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
    }
    
    /*protected void loadAchievements() {
    	ItemDatabase iDB = new ItemDatabase(false);
    	achievements = iDB.loadAchievements();
    }*/

    protected void writeData(String worldName, String dataName, long timestamp, OID sourceOid, OID targetOid, OID accountId, String data) {
        String tableName = "data_logs";
        String columnNames = "world_name,data_name,data_timestamp,source_oid,target_oid,account_id,additional_data";
        if(timestamp > 9999999999L)
        	timestamp = timestamp / 1000L;
        String values = "'" + worldName + "','" + dataName + "',FROM_UNIXTIME(" + timestamp + ")," + (sourceOid == null ? 0 : sourceOid.toLong()) + "," + (targetOid == null ? 0 : targetOid.toLong()) + "," + (accountId == null ? 0 : accountId.toLong()) + "," + ((data.length() > 0) ? ("'" + data.replace("'","\'") + "'") : "NULL") + "";
        
        String insertString = "INSERT INTO `" + tableName + "` (" + columnNames + ") VALUES (" + values + ")";
        Log.debug("writeData sql="+insertString);
        if (logQueries.executeUpdate(insertString) <= 0) {
            String logString = worldName + "," + encode(dataName) + "," + timestamp + "," + (sourceOid == null ? 0 : sourceOid.toLong()) + "," + (targetOid == null ? 0 : targetOid.toLong()) + "," + (accountId == null ? 0 : accountId.toLong()) + "," + ((data.length() > 0) ? (data.replace("'","\'")) : "") + "," + System.currentTimeMillis();
            try {
                out.write(logString + "\n");
                out.flush();
            } catch (Exception e) {
                Log.exception("Exception: trying to write '" + logString + "'", e);
            }
        }
        String logString = worldName + "," + encode(dataName) + "," + timestamp + "," + (sourceOid == null ? 0 : sourceOid.toLong()) + "," + (targetOid == null ? 0 : targetOid.toLong()) + "," + (accountId == null ? 0 : accountId.toLong()) + "," + ((data.length() > 0) ? (data.replace("'","\'")) : "") + "," + System.currentTimeMillis();
        try {
            out.write(logString + "\n");
            out.flush();
        } catch (Exception e) {
            Log.exception("Exception: trying to write '" + logString + "'", e);
        }
    }

    public class DataLogMessageHook implements Hook {
        protected String worldName = null;
        protected String dataName = null;
        protected long timestamp   = 0L;
        protected OID sourceOid   = null;
        protected OID targetOid   = null;
        protected OID accountId   = null;
        protected HashMap<String,Serializable> map  = null;
        protected String data = null;
        public boolean processMessage(Message m, int flags) {
            processData(m);
            writeData(worldName, dataName, timestamp, sourceOid, targetOid, accountId, data);
            return true;
        }
        protected void processData(Message m) {
            DataLogMessage msg = (DataLogMessage)m;
            worldName = msg.getWorldName();
            dataName  = msg.getDataName();
            timestamp = msg.getTimestamp();
            sourceOid = msg.getSourceOid();
            targetOid = msg.getTargetOid();
            accountId    = msg.getAccountId();
            map       = msg.getMap();
            data      = new String();
            if (map == null) {
                return;
            }
            if (dataName.equals("ITEM_LOOTED_EVENT") && map.containsKey("item")) {
            	data += encode((String)map.get("item"));
            }else if (dataName.equals("ITEM_LOOTED_FAILED_EVENT") && map.containsKey("item")) {
            	data += encode((String)map.get("item"));
            }else if (dataName.equals("NEW_OBJECT_EVENT") && map.containsKey("name") && map.containsKey("type")) {
                data += encode((String)map.get("name"));
                data += ",";
                data += encode((String)map.get("type"));
            }else if (dataName.equals("CURRENCY_CHANGED_EVENT") && map.containsKey("name") && map.containsKey("reason")) {
                data += encode((String)map.get("name"));
                data += ",";
                data += encode((String)map.get("reason"));
                data += ",";
                data += encode((String)map.get("value"));
            }else if (dataName.equals("CHAT_MESSAGE_EVENT") && (map.containsKey("chat"))) {
                data += encode((String)map.get("chat"));
            }else if (dataName.equals("KEYBOARD_EVENT") && map.containsKey("keycode")) {
                data += (Integer)map.get("keycode");
                data += ",";
                data += ((map.containsKey("shift") ? (Boolean)map.get("shift") : false) ? "true" : "false");  // shift key down
                data += ",";
                data += ((map.containsKey("control") ? (Boolean)map.get("control") : false) ? "true" : "false");  // control key down
                data += ",";
                data += ((map.containsKey("alt") ? (Boolean)map.get("alt") : false) ? "true" : "false");  // alt key down
            }else if (dataName.equals("GESTURE_EVENT") && map.containsKey("name")) {
                data += encode((String)map.get("name"));
            }else  if (dataName.equals("ABILITY_TRIGGERED_EVENT") && map.containsKey("name")) {
                data += encode((String)map.get("name"));
            }else  if (dataName.equals("LOCATION_EVENT")) {
                //AOVector dir = (AOVector)map.get("dir");
                Quaternion quat = (Quaternion)map.get("orient");  // dir.getRotationTo(new AOVector(0, 0, -1));
                AOVector axis = new AOVector();
                double degrees = 0.0f;
                if (quat != null) {
                    degrees = quat.getAngleAxisDegrees(axis);
                } else {
                    degrees = 180.0f;
                }
                if (axis.getY() > 0) {
                    degrees = 360.0f - degrees;
                }
                degrees += 90.0f;
                if (degrees > 360.0f) {
                    degrees -= 360.0f;
                }
                data += (Point)map.get("loc");  // position in mm
                data += ",";
                data += (Quaternion)map.get("orient");  // orientation in quaternion
                data += ",";
                data += String.format("%.2f", degrees);  // AOVector convert to degrees // orientation in decimal degrees
                data += ",";
                if (map.get("camera_loc") != null) {
                    data += (Point)map.get("camera_loc");  // camera position -- "(x,y,z)" for 3d
                } else {
                    data += "()";  // camera position -- "()" for flash
                }
                data += ",";
                if (map.get("camera_orient") != null) {
                    data += (Quaternion)map.get("camera_orient");  // camera facing -- "(t,u,v,w)" for 3d
                } else {
                    data += "()";  // camera facing -- "()" for flash
                }
                data += ",";
                data += (LinkedList<Long>)map.get("list_10m");  // OIDs of PCs/NPCs <= 10m
                data += ",";
                data += (LinkedList<Long>)map.get("list_30m");  // OIDs of PCs/NPCs > 10m and <= 30m
            }else  if (dataName.equals("SYSTEM_INFO_EVENT:") && map.containsKey("memory") && map.containsKey("os")) {
                data += (Integer)map.get("memory");
                data += ",";
                data += encode((String)map.get("os"));
            }else if (dataName.equals("CHARACTER_DELETED") && (map.containsKey("aspect"))) {
                data += encode((String)map.get("aspect"));
            }else if (dataName.equals("CHARACTER_CREATED") && (map.containsKey("aspect"))) {
                data += encode((String)map.get("aspect"));
            }else  if (dataName.equals("PLAYER_LOGGED_IN_EVENT") && (map.containsKey("ipaddress"))) {
                data += encode((String)map.get("ipaddress"));
            }else  if (dataName.equals("USER_BANNED")) {
                data += encode((String)map.get("hours"));
                data += encode(", " + (String)map.get("banExpires"));
            }else  if (dataName.equals("PLAYER_JOINED_ARENA_QUEUE")) {
                data += encode((String)map.get("arenaTypes"));
            }else if (dataName.equals("PLAYER_LEFT_ARENA_QUEUE")) {
            	int arenaType = (Integer)map.get("arenaType");
                data += encode("" + arenaType);
            }else if (dataName.equals("PLAYER_JOINED_ARENA")) {
            	int arenaType = (Integer)map.get("arenaType");
                data += encode("" + arenaType);
            }else  if (dataName.equals("PLAYER_LEFT_ARENA")) {
            	int arenaID = (Integer)map.get("arenaID");
                data += encode("" + arenaID);
            }else  if (dataName.equals("ARENA_STARTED")) {
            	String arenaName = (String)map.get("arenaName");
                data += encode(arenaName);
            }else  if (dataName.equals("ARENA_RESULT")) {
                String arenaName = (String)map.get("arenaName");
                int winningTeam = (Integer)map.get("winningTeam");
                int numTeams = (Integer)map.get("numTeams");
                int timeLeft = (Integer)map.get("timeLeft");
                data += encode(arenaName);
                data += ",";
                data += winningTeam;
                data += ",";
                data += numTeams;
                data += ",";
                data += timeLeft;
            }else {
            	for(String s : map.keySet()) {
					data += s + "=" + map.get(s) + ";";
            	}
            }
        }
    }

    public String encode(String inStr) {
        String encStr = new String();
        try {
            encStr = URLEncoder.encode(inStr, "UTF-8");
        } catch (Exception e) {
        }
        return encStr;
    }
    
    /**
     * Handles an event, determining the type and the data associated with it, then updating the involved players
     * stats and achievements.
     * @author Andrew
     *
     */
    public class LogEventHook implements Hook {
        public boolean processMessage(Message m, int flags) {
            
            return true;
        }
    }
    
    class CharacterCreatedHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			DataLoggerClient.CharacterCreatedMessage getMsg = (DataLoggerClient.CharacterCreatedMessage) msg;
			OID characterOID = getMsg.getSourceOid();
			OID accountID = getMsg.getAccountId();
			String characterName = getMsg.getCharacterName();
			String accountName = getMsg.getAccountName();
			accDB.characterCreated(accountID, accountName, characterOID, characterName);
			return true;
		}
    }
    
    class CharacterDeletedHook implements Hook {
		public boolean processMessage(atavism.msgsys.Message msg, int flags) {
			DataLoggerClient.CharacterDeletedMessage getMsg = (DataLoggerClient.CharacterDeletedMessage) msg;
			OID characterOID = getMsg.getSourceOid();
			OID accountID = getMsg.getAccountId();
			String characterName = getMsg.getCharacterName();
			accDB.characterDeleted(accountID, characterOID, characterName);
			return true;
		}
    }
    
    /**
	 * The hook for when players login. Gets their account status from the account db.
	 *
	 */
    class LoginHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LoginMessage message = (LoginMessage) msg;
            OID playerOid = message.getSubject();
            //DataLoggerClient.logData("PLAYER_LOGGED_IN_EVENT", playerOid, null, null, null);
            try {
				OID accountID = accDB.getAccountForCharacter(playerOid);
				if (accountID == null) {
					accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.ACCOUNT_PROPERTY);
				}
				if(accountID == null) {
					Log.error("DataLogger Login can not get account id for "+playerOid);
					// commented out because AgisInventoryPlugin and DataLoggerPlugin are running in the same process of the objmgr.
		            // the proxy waits only for one response from a given server (process) 
		            // AgisInventoryPlugin will send response. 
				//  Engine.getAgent().sendResponse(new ResponseMessage(message));
					return true;
				}
				int accountStatus = accDB.getAccountStatus(accountID);
				try {
					EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "adminLevel", accountStatus);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Log.debug("ACCOUNT: set account status for: " + playerOid + " to: " + accountStatus);
				accDB.characterLoggedIn(accountID);
				accDB.updateServerStat("player_login");
			} catch (Exception e) {
				Log.exception("DataLogger Login playerOid="+playerOid+" Excpetion ",e);
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            // commented out because AgisInventoryPlugin and DataLoggerPlugin are running in the same process of the objmgr.
            // the proxy waits only for one response from a given server (process) 
            // AgisInventoryPlugin will send response. 
          //  Engine.getAgent().sendResponse(new ResponseMessage(message));
            return true;
        }
    }
    
	/**
	 * The hook for when players logout (or disconnect). This will remove the player from
	 * any arenas and queues they are in.
	 *
	 */
    class LogoutHook implements Hook {
        public boolean processMessage(Message msg, int flags) {
            LogoutMessage message = (LogoutMessage) msg;
            Log.debug("LOGOUT: datalogger logout started");
            OID playerOid = message.getSubject();
            Engine.getAgent().sendResponse(new ResponseMessage(message));
            DataLoggerClient.logData("PLAYER_LOGGED_OUT_EVENT", playerOid, null, null, null);
            //OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, WorldManagerClient.ACCOUNT_PROPERTY);
            //accDB.characterLoggedOut(accountID);
            accDB.updateServerStat("player_logout");
            Log.debug("LOGOUT: datalogger logout finished");
            return true;
        }
    }
    
    
    
    protected HashMap<Integer, Achievement> achievements = new HashMap<Integer, Achievement>();
}
