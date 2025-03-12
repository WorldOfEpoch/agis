package atavism.agis.plugins;

import java.io.Serializable;
import java.util.HashMap;

import atavism.msgsys.MessageType;
import atavism.server.messages.PropertyMessage;

import atavism.server.engine.Engine;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;

public class DataLoggerClient {
	protected DataLoggerClient() {
	}
	//public static Namespace NAMESPACE = Namespace.intern("ns.DATALOGGER");
	public static Namespace NAMESPACE = null;
	public static String WORLDNAME = null;

	public static final MessageType MSG_TYPE_DATA_LOG = MessageType.intern("ao.DATA_LOG");
	public static final MessageType MSG_TYPE_EVENT_LOG = MessageType.intern("ao.EVENT_LOG");
	public static final MessageType MSG_TYPE_CHARACTER_CREATED = MessageType.intern("ao.CHARACTER_CREATED");
	public static final MessageType MSG_TYPE_CHARACTER_DELETED = MessageType.intern("ao.CHARACTER_DELETED");
	
	public static void logEvent(int eventID, OID sourceOid, OID targetOid, int eventData, int eventCount) {
		LogEventMessage elogMsg = new LogEventMessage(eventID, sourceOid, targetOid, eventData, eventCount);
		Engine.getAgent().sendBroadcast(elogMsg);
	}

	public static class LogEventMessage extends PropertyMessage {
		public LogEventMessage() {
			super();
			setMsgType(MSG_TYPE_EVENT_LOG);
		}
		public LogEventMessage(int eventID, OID sourceOid, OID targetOid, int eventData, int eventCount) {
			super();
			setMsgType(MSG_TYPE_EVENT_LOG);
			setEventID(eventID);
			setSourceOid(sourceOid);
			setTargetOid(targetOid);
			setEventData(eventData);
			setEventCount(eventCount);
		}
		protected int eventID = -1;
		public void setEventID(int eventID) {
			this.eventID = eventID;
		}
		public int getEventID() {
			return this.eventID;
		}
		protected OID sourceOid = null;
		public void setSourceOid(OID sourceOid) {
			this.sourceOid = sourceOid;
		}
		public OID getSourceOid() {
			return this.sourceOid;
		}
		protected OID targetOid = null;
		public void setTargetOid(OID targetOid) {
			this.targetOid = targetOid;
		}
		public OID getTargetOid() {
			return this.targetOid;
		}
		protected int eventData = -1;
		public void setEventData(int eventData) {
			this.eventData = eventData;
		}
		public int getEventData() {
			return this.eventData;
		}
		protected int eventCount = -1;
		public void setEventCount(int eventCount) {
			this.eventCount = eventCount;
		}
		public int getEventCount() {
			return this.eventCount;
		}
		
		private static final long serialVersionUID = 1L;
	}

	public static void logData(String dataName, OID sourceOid, OID targetOid, OID accountId, HashMap<String,Serializable> map) {
		DataLogMessage elogMsg = new DataLogMessage(Engine.getWorldName(), dataName, System.currentTimeMillis(), sourceOid, targetOid, accountId, map);
		Engine.getAgent().sendBroadcast(elogMsg);
	}

	public static class DataLogMessage extends PropertyMessage {
		public DataLogMessage() {
			super();
			setMsgType(MSG_TYPE_DATA_LOG);
		}
		public DataLogMessage(String worldName, String dataName, long timestamp, OID sourceOid, OID targetOid, OID accountId, HashMap<String,Serializable> map) {
			super();
			setMsgType(MSG_TYPE_DATA_LOG);
			setWorldName(worldName);
			setDataName(dataName);
			setTimestamp(timestamp);
			setSourceOid(sourceOid);
			setTargetOid(targetOid);
			setAccountId(accountId);
			setMap(map);
		}
		protected String worldName = null;
		public void setWorldName(String worldName) {
			this.worldName = worldName;
		}
		public String getWorldName() {
			return this.worldName;
		}
		protected String dataName = null;
		public void setDataName(String dataName) {
			this.dataName = dataName;
		}
		public String getDataName() {
			return this.dataName;
		}
		protected long timestamp = 0;
		public void setTimestamp(long timestamp) {
			this.timestamp = timestamp;
		}
		public long getTimestamp() {
			return this.timestamp;
		}
		protected OID sourceOid = null;
		public void setSourceOid(OID sourceOid) {
			this.sourceOid = sourceOid;
		}
		public OID getSourceOid() {
			return this.sourceOid;
		}
		protected OID targetOid = null;
		public void setTargetOid(OID targetOid) {
			this.targetOid = targetOid;
		}
		public OID getTargetOid() {
			return this.targetOid;
		}
		protected OID accountId = null;
		public void setAccountId(OID accountId) {
			this.accountId = accountId;
		}
		public OID getAccountId() {
			return this.accountId;
		}
		protected HashMap<String,Serializable> map = null;
		public void setMap(HashMap<String,Serializable> map) {
			this.map = map;
		}
		public HashMap<String,Serializable> getMap() {
			return this.map;
		}
		
		private static final long serialVersionUID = 1L;
	}
	
	public static void characterCreated(OID accountId, String accountName, OID sourceOid, String characterName) {
		CharacterCreatedMessage elogMsg = new CharacterCreatedMessage(accountId, accountName, sourceOid, characterName);
		Engine.getAgent().sendBroadcast(elogMsg);
	}

	public static class CharacterCreatedMessage extends PropertyMessage {
		public CharacterCreatedMessage() {
			super();
			setMsgType(MSG_TYPE_CHARACTER_CREATED);
		}
		public CharacterCreatedMessage(OID accountId, String accountName, OID sourceOid, String characterName) {
			super();
			setMsgType(MSG_TYPE_CHARACTER_CREATED);
			setCharacterName(characterName);
			setSourceOid(sourceOid);
			setAccountName(accountName);
			setAccountId(accountId);
		}
		protected String characterName = null;
		public void setCharacterName(String characterName) {
			this.characterName = characterName;
		}
		public String getCharacterName() {
			return this.characterName;
		}
		protected OID sourceOid = null;
		public void setSourceOid(OID sourceOid) {
			this.sourceOid = sourceOid;
		}
		public OID getSourceOid() {
			return this.sourceOid;
		}
		protected String accountName = null;
		public void setAccountName(String accountName) {
			this.accountName = accountName;
		}
		public String getAccountName() {
			return this.accountName;
		}
		protected OID accountId = null;
		public void setAccountId(OID accountId) {
			this.accountId = accountId;
		}
		public OID getAccountId() {
			return this.accountId;
		}
		
		private static final long serialVersionUID = 1L;
	}
	
	public static void characterDeleted(OID accountId, OID sourceOid, String characterName) {
		CharacterDeletedMessage elogMsg = new CharacterDeletedMessage(accountId, sourceOid, characterName);
		Engine.getAgent().sendBroadcast(elogMsg);
	}

	public static class CharacterDeletedMessage extends PropertyMessage {
		public CharacterDeletedMessage() {
			super();
			setMsgType(MSG_TYPE_CHARACTER_DELETED);
		}
		public CharacterDeletedMessage(OID accountId, OID sourceOid, String characterName) {
			super();
			setMsgType(MSG_TYPE_CHARACTER_DELETED);
			setCharacterName(characterName);
			setSourceOid(sourceOid);
			setAccountId(accountId);
		}
		protected String characterName = null;
		public void setCharacterName(String characterName) {
			this.characterName = characterName;
		}
		public String getCharacterName() {
			return this.characterName;
		}
		protected OID sourceOid = null;
		public void setSourceOid(OID sourceOid) {
			this.sourceOid = sourceOid;
		}
		public OID getSourceOid() {
			return this.sourceOid;
		}
		protected OID accountId = null;
		public void setAccountId(OID accountId) {
			this.accountId = accountId;
		}
		public OID getAccountId() {
			return this.accountId;
		}
		
		private static final long serialVersionUID = 1L;
	}
}
