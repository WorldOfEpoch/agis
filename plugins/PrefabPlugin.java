package atavism.agis.plugins;

import atavism.agis.objects.*;
import atavism.agis.util.HelperFunctions;
import atavism.server.util.*;
import atavism.server.worldmgr.CharacterGenerator;
import atavism.agis.core.Agis;
import atavism.agis.database.CombatDatabase;
import atavism.agis.database.ContentDatabase;
import atavism.agis.database.ItemDatabase;
import atavism.agis.database.MobDatabase;
import atavism.agis.objects.SkillTemplate.SkillAbility;
import atavism.agis.util.RequirementChecker;
import atavism.msgsys.Message;
import atavism.msgsys.MessageAgent;
import atavism.msgsys.MessageTypeFilter;
import atavism.msgsys.ResponseMessage;
import atavism.server.engine.*;
import atavism.server.network.*;
import atavism.server.objects.Entity;
import atavism.server.objects.EntityManager;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.messages.LoginMessage;
import atavism.server.messages.LogoutMessage;
import atavism.server.messages.PropertyMessage;
import java.io.*;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrefabPlugin extends EnginePlugin implements TcpAcceptCallback {
	public PrefabPlugin() {
		super("Prefab");
		setPluginType("Prefab");

		// set the static SecureToken boolean based on properties file
		// if it is set in there
		String secureTokenString = Engine.getProperty("atavism.security.secure_token");
		Log.debug("constructor: secureTokenString=" + secureTokenString);
		if (secureTokenString != null) {
			Boolean secureToken = Boolean.parseBoolean(secureTokenString);
			PrefabPlugin.SecureToken = secureToken;
		}
	}
	protected static final Logger log = new Logger("PrefabPlugin");
	public static final int MSGCODE_PREFAB_REQEST = 15;
	public static final int MSGCODE_PREFAB_RESPONSE = 16;
	public static final int MSGCODE_ICON_PREFAB_REQEST = 17;
	public static final int MSGCODE_ICON_PREFAB_RESPONSE = 18;
	
	public static HashMap<String, HashMap<String,Serializable>> prefabsData = new HashMap<String, HashMap<String,Serializable>>(); 

	// Timeout on client connections
	// ## remove " * 100" once client supports reconnect
	public static final int LOGIN_IDLE_TIMEOUT = 20000 * 100;

	/**
	 * maximum incoming message length
	 */
	public static int PREFAB_MAX_INCOMING_MESSAGE_LENGTH = 300_000;

	
	public static int PREFAB_LOAD_ELEMENT_LIMIT = 100;
	/**
	 * This method connects to the database. It assumes the host, user, and port
	 * have already been set.
	 */
	public void dbConnect() {
		if (Engine.getDatabase() == null) {
			Log.debug("Setting Database in WorldManager.dbConnect");
			Engine.setDatabase(new Database(Engine.getDBDriver()));
		}
		// Log.debug("About to call Engnie.getDatabase in WorldManager.dbConnect");
		Engine.getDatabase().connect(Engine.getDBUrl(), Engine.getDBUser(), Engine.getDBPassword(), Engine.getMaxConnectionPoolSize());
	}

	/**
	 * Set the port the prefab plugin will listen to for incoming tcp connection.
	 * 
	 * @param port
	 *            the port number used for incoming tcp connections
	 */
	public void setTCPPort(int port) {
		this.tcpPort = port;
	}

	/**
	 * This method returns the port the prefab plugin will listen to for incoming tcp
	 * connection.
	 * 
	 * @see #setTCPPort(int)
	 * @return the tcp port number
	 */
	public int getTCPPort() {
		if (tcpPort == null) {
			  String propStr;
		        propStr = Engine.getProperty("atavism.prefab.bindport");
		        int port = 5566;
		        if (propStr != null) {
		            port = Integer.parseInt(propStr.trim());
		        }
		        return port;
		}
		return tcpPort;
	}
	
	MobDatabase mDB;
	ItemDatabase iDB;
	ContentDatabase cDB;
	CombatDatabase ctDB;
	static Map<String, CharacterTemplate> characterTemplates = new HashMap<String, CharacterTemplate>();
	ArrayList<OID> Oids = new ArrayList<OID>();
	
	public void onActivate() {
		try {
			log.debug("onActivate");
			getHookManager().addHook(LoginMessage.MSG_TYPE_LOGIN, new LoginHook());
			getHookManager().addHook(LogoutMessage.MSG_TYPE_LOGOUT, new LogoutHook());
			MessageTypeFilter filter2 = new MessageTypeFilter();
			filter2.addType(LoginMessage.MSG_TYPE_LOGIN);
			filter2.addType(LogoutMessage.MSG_TYPE_LOGOUT);
			Engine.getAgent().createSubscription(filter2, this, MessageAgent.RESPONDER);
			loadData();
			
			// start the prefab thread
			prefabListener = new TcpServer(getTCPPort());
			prefabListener.registerAcceptCallback(this);
			prefabListener.start();
		
		} catch (Exception e) {
			log.exception("PrefabPlugin.onActivate caught exception", e);
			System.exit(1);
		}
		setPluginInfo("host=" + prefabListener.getAddress() + ",port=" + prefabListener.getPort()+",build="+ServerVersion.getVersionString());
		log.debug("Registering Prefab plugin");
		Engine.registerStatusReportingPlugin(this);

	}
	
	void loadData() {
		//if (cDB == null)
			cDB = new ContentDatabase(false);
		cDB.loadEditorOptions();
		String loadElementlimit = cDB.loadGameSetting("PREFAB_LOAD_ELEMENT_LIMIT");
		if (loadElementlimit != null)
			PREFAB_LOAD_ELEMENT_LIMIT = Integer.parseInt(loadElementlimit);
		log.debug("Game Settings set PREFAB_LOAD_ELEMENT_LIMIT=" + PREFAB_LOAD_ELEMENT_LIMIT);
		
		String messageLenght = cDB.loadGameSetting("PREFAB_MAX_INCOMING_MESSAGE_LENGTH");
		if (messageLenght != null)
			PREFAB_MAX_INCOMING_MESSAGE_LENGTH = Integer.parseInt(messageLenght);
		log.debug("Game Settings set PREFAB_MAX_INCOMING_MESSAGE_LENGTH=" + PREFAB_MAX_INCOMING_MESSAGE_LENGTH);
	
		
		
		cDB.loadEditorOptions();
		characterTemplates = cDB.loadCharacterFactoryTemplates();
		//if (mDB == null)
			mDB = new MobDatabase(false);
	//	if (iDB == null)
			iDB = new ItemDatabase(false);
		//if (mDB == null)
			ctDB = new CombatDatabase(false);

		ArrayList<Faction> factions = mDB.loadFactions(1);
	    for (Faction faction: factions) {
	    	Agis.FactionManager.register(faction.getID(), faction);
	    	 if (Log.loggingDebug)Log.debug("MOB: loaded faction: [" + faction.getName() + "]");
	    }

		// Load Prefabs Data
		prefabsData.put("Item", iDB.loadItemPrefabData());
		prefabsData.put("ItemAudio", iDB.getItemAudioProfilePrefabData());
		prefabsData.put("ItemSet", loadItemSetPrefabData());
		prefabsData.put("Currency", mDB.loadCurrenciesPrefabData());
		prefabsData.put("CraftingRecipe", loadCraftingRecipePrefabData());
		prefabsData.put("Skill", loadSkillPrefabData());
		prefabsData.put("Ability", ctDB.LoadAbilityPrefabData());
		prefabsData.put("Effect", ctDB.loadEffectsPrefabData());
		prefabsData.put("BuildingObject", cDB.loadBuildObjectPrefabData());
		prefabsData.put("ResourceNode", cDB.loadResourceNodeIcons());
		prefabsData.put("GlobalEvents", cDB.loadGlobalEventsPrefabData());
		prefabsData.put("Quest", mDB.loadQuestsPrefrabData(1));
		prefabsData.put("WeaponProfile",iDB.loadWeaponProfilePrefabData());
		prefabsData.put("Stats",ctDB.LoadStatsPrefabData());
		prefabsData.put("InteractiveObjects", cDB.loadInteractiveObjectProfilesPrefabData());
		prefabsData.put("SlotsProfile", iDB.getSlotsProfilePrefabData());

		// Load Prefabs Data End

	}

	/**
	 * 
	 */
	protected void ReloadTemplates(Message msg) {
		Log.debug("PrefabPlugin ReloadTemplates Start");
		loadData();
		
		//Send to clients info that prefab data was reloaded 
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "ao.reloaded");
		if (Log.loggingDebug)
			log.debug("PrefabPlugin: send props " + props);
		for (int i = Oids.size() - 1; i >= 0; i--) {
			OID objOid = Oids.get(i);
			TargetedExtensionMessage _msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, objOid, objOid, false, props);
			if(Log.loggingDebug)log.debug("PrefabPlugin send msg "+_msg+" to "+objOid);
			Engine.getAgent().sendBroadcast(_msg);
		}
		Log.debug("PrefabPlugin ReloadTemplates End");
	}

	/**
	 * The hook for when players logged in. This will add the player
	 * to the list of the players.
	 *
	 */class LoginHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LoginMessage message = (LoginMessage) msg;
			OID playerOid = message.getSubject();
			OID instanceOid = message.getInstanceOid();
			if(Log.loggingDebug)Log.debug("LOGIN: PrefabPlugin login player " + playerOid );
			// Adding the player from any queues they may have been in
			if (!Oids.contains(playerOid)) {
				Oids.add(playerOid);
			} else {
				Log.debug("LOGIN: PrefabPlugin logout player " + playerOid + " on the list");
			}
			Engine.getAgent().sendResponse(new ResponseMessage(message));
			return true;
		}
	}
	/**
	 * The hook for when players logout (or disconnect). This will remove the player
	 * from the list of the players.
	 *
	 */
	class LogoutHook implements Hook {
		public boolean processMessage(Message msg, int flags) {
			LogoutMessage message = (LogoutMessage) msg;
			OID playerOid = message.getSubject();
			if (Log.loggingDebug)
				Log.debug("LOGOUT: PrefabPlugin logout started for: " + playerOid);
			// Remove the player from any queues they may have been in
			if (Oids.contains(playerOid)) {
				Oids.remove(playerOid);
			} else {
				if(Log.loggingDebug)Log.debug("LOGOUT: PrefabPlugin logout player " + playerOid + " not on list");
			}
			// Response
			Engine.getAgent().sendResponse(new ResponseMessage(message));
			if (Log.loggingDebug)
				Log.debug("LOGOUT: PrefabPlugin logout finished for: " + playerOid);
			return true;
		}
	}
	
	
	private static String socketToString(SocketChannel channel) {
		java.net.Socket socket = channel.socket();
		return "remote=" + socket.getRemoteSocketAddress() + " local=" + socket.getLocalSocketAddress();
	}

	public void onTcpAccept(SocketChannel clientSocket) {
		try {
			log.info("CONNECTION " + socketToString(clientSocket));
			threadPool.execute(new SocketHandler(clientSocket));
		} catch (IOException e) {
			Log.exception("PrefabListener: ", e);
		}
	}

	protected class SocketHandler implements Runnable {
		public SocketHandler(SocketChannel socket) throws IOException {
			clientSocket = socket;
			selector = Selector.open();
			clientSelection = clientSocket.register(selector, SelectionKey.OP_READ);
		}

		private SocketChannel clientSocket = null;
		private Selector selector = null;
		private SelectionKey clientSelection = null;
		private OID accountId = null;
		private String accountName = null;
		private List<Map<String, Serializable>> characterInfo = null;

		public SocketAddress getRemoteSocketAddress() {
			return clientSocket.socket().getRemoteSocketAddress();
		}

		public void setAccountId(OID accountId) {
			this.accountId = accountId;
		}

		public OID getAccountId() {
			return accountId;
		}

		public void setAccountName(String accountName) {
			this.accountName = accountName;
		}

		public String getAccountName() {
			return accountName;
		}

		public void setCharacterInfo(List<Map<String, Serializable>> charInfo) {
			characterInfo = charInfo;
		}

		public List<Map<String, Serializable>> getCharacterInfo() {
			return characterInfo;
		}

		private int fillBuffer(SocketChannel socket, ByteBuffer buffer) throws IOException {
			clientSelection.interestOps(SelectionKey.OP_READ);
			while (buffer.remaining() > 0) {
				int nReady = selector.select(LOGIN_IDLE_TIMEOUT);
				if (nReady == 1) {
					selector.selectedKeys().clear();
					int nBytes = socket.read(buffer);
					if (nBytes == -1)
						break;
				} else {
					log.debug("Connection timeout while reading");
					break;
				}
			}
			buffer.flip();
			return buffer.limit();
		}

		private boolean writeBuffer(ByteBuffer buffer) throws IOException {
			clientSelection.interestOps(SelectionKey.OP_WRITE);
			while (buffer.hasRemaining()) {
				int nReady = selector.select(LOGIN_IDLE_TIMEOUT);
				if (nReady == 1) {
					selector.selectedKeys().clear();
					if (clientSocket.write(buffer) == 0)
						break;
				} else {
					log.debug("Connection timeout while writing");
					break;
				}
			}
			return !buffer.hasRemaining();
		}

		public void run() {
			try {
				ByteBuffer header = ByteBuffer.allocate(8);
				while (true) {
					int nBytes = fillBuffer(clientSocket, header);
					if (nBytes == 0) {
						log.info("PrefabPlugin: DISCONNECT " + socketToString(clientSocket));
						break;
					}
					if (nBytes < 8) {
						log.error("PrefabPlugin: reading header nBytes " + nBytes);
						break;
					}
					int messageLength = header.getInt();
					int messageCode = header.getInt();
					header.clear();
					if (Log.loggingDebug)
						log.debug("PrefabPlugin: code " + messageCode + " (" + messageLength + " bytes)");
					if (messageLength > PREFAB_MAX_INCOMING_MESSAGE_LENGTH) {
						log.error("PrefabPlugin: max message length "+messageLength+" exceeded "+PREFAB_MAX_INCOMING_MESSAGE_LENGTH);
						break;
					} 
					
					if (messageLength < 0) {
						log.error("PrefabPlugin: invalid message length");
						break;
					}

					if (messageLength == 4) {
						log.error("PrefabPlugin: invalid message length (possibly an old client)");
						break;
					}

					ByteBuffer message = null;
					if (messageLength > 4) {
						try {
							message = ByteBuffer.allocate(messageLength - 4);
						} catch (OutOfMemoryError e) {

							log.error("OOM Server can't create buffer with size " + messageLength);
							break;
						}
						nBytes = fillBuffer(clientSocket, message);
						if (nBytes == -1 || nBytes != messageLength - 4) {
							log.error("PrefabPlugin: error reading message body");
							byte[] readdata = message.array();
							Log.error("PrefabPlugin packed data in hex: " + bytesToHex(readdata));
							String data = new String(readdata, "UTF-8");
							Log.error("PrefabPlugin packed data in sring: "+data);
							break;
						}
					}

					ByteBuffer responseBuf;
					responseBuf = dispatchMessage(messageCode, message, this);
					if (responseBuf != null) {
						if (!writeBuffer(responseBuf))
							break;
					} else
						break;
				}
			} catch (InterruptedIOException e) {
				log.exception("PrefabPlugin: closed connection due to timeout ",e);
			} catch (IOException e) {
				log.exception("PrefabPlugin.SocketHandler: ", e);
			} catch (AORuntimeException e) {
				log.exception("PrefabPlugin.SocketHandler: ", e);
			} catch (Exception e) {
				Log.exception("PrefabPlugin.SocketHandler: ", e);
			}

			try {
				clientSelection.cancel();
				clientSocket.close();
				selector.close();
			} catch (Exception ignore) {
				/* ignore */ }
		}

        private final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
        public  String bytesToHex(byte[] bytes) {
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0; j < bytes.length; j++) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = HEX_ARRAY[v >>> 4];
                hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            return new String(hexChars);
        }

		ByteBuffer dispatchMessage(int messageCode, ByteBuffer messageBuf, SocketHandler clientSocket) throws IOException {
			ByteBuffer responseBuf = null;
		 if (messageCode == MSGCODE_PREFAB_REQEST) {
				GetPrefabMessage msg = new GetPrefabMessage();
				AOByteBuffer aoBuf = new AOByteBuffer(messageBuf);
				msg.msgType = aoBuf.getString();
				msg.props = PropertyMessage.unmarshallProperyMap(aoBuf);
				if (Log.loggingDebug)
					log.debug("PrefabPlugin: GetPrefabMessage Type="+msg.msgType+" prop count=" + msg.props.size());
				GetPrefabResponseMessage response;
				response = handleGetPrefabMessage(msg, clientSocket);
				responseBuf = response.getEncodedMessage();
			} else if (messageCode == MSGCODE_ICON_PREFAB_REQEST) {
				GetIconPrefabMessage msg = new GetIconPrefabMessage();
				AOByteBuffer aoBuf = new AOByteBuffer(messageBuf);
				msg.msgType = aoBuf.getString();
				msg.props = PropertyMessage.unmarshallProperyMap(aoBuf);
				if (Log.loggingDebug)
					log.debug("PrefabPlugin: GetIconPrefabMessage Type="+msg.msgType+" prop count=" + msg.props.size());
				GetIconPrefabResponseMessage response;
				response = handleGetIconPrefabMessage(msg, clientSocket);
				responseBuf = response.getEncodedMessage();
			} else {
				Log.error("Unknown message code " + messageCode);
			}
			return responseBuf;
		}
	}

	/**
	 * Message to Request Prefab data using the given properties.
	 */
	public static class GetPrefabMessage {
		/**
		 * Get the requested character properties.
		 */
		public Map<String, Serializable> getProperties() {
			return props;
		}
		
		public String getMsgtype() {
			return msgType;
		}
	
		private String msgType;
		private Map<String, Serializable> props;
	}
	
	
	/**
	 * Message to return Prefab Data properties. 
	 */
	public static class GetPrefabResponseMessage {
		/**
		 * Set the new Prefab properties.
		 */
		public void setType(String msgType) {
			this.msgType = msgType;
		}
		/**
		 * Set the new Prefab properties.
		 */
		public void setProperties(Map<String, Serializable> props) {
			this.props = props;
		}

		/**
		 * Get the new prefab properties. Defaults to null.
		 */
		public Map<String, Serializable> getProperties() {
			return props;
		}

		ByteBuffer getEncodedMessage() {
			if (Log.loggingDebug)
				Log.debug("PrefabPlugin: Prefab response:" + " nProps=" + ((props == null) ? 0 : props.size()));

			AOByteBuffer buffer = new AOByteBuffer(1024);
			buffer.putInt(0); // length
			buffer.putInt(MSGCODE_PREFAB_RESPONSE); // message code
			buffer.putString(msgType);
			if (props == null) {
				buffer.putInt(0);
			} else {
				List<String> propStrings = new ArrayList<String>();
				int nProps = PropertyMessage.createPropertyString(propStrings, props, "");
				buffer.putInt(nProps);
				for (String s : propStrings) {
					buffer.putString(s);
				}
			}

			// patch the message length
			int len = buffer.position();
			buffer.getNioBuf().rewind();
			buffer.putInt(len - 4);
			buffer.position(len);

			return (ByteBuffer) buffer.getNioBuf().flip();
		}

		public void decodeBuffer(AOByteBuffer buffer) {
			// message code has already been read
			props = PropertyMessage.unmarshallProperyMap(buffer);
		}
		private String msgType;
		private Map<String, Serializable> props;
	}

	public static class GetIconPrefabMessage {
		/**
		 * Get the requested Icon properties.
		 */
		public Map<String, Serializable> getProperties() {
			return props;
		}
		
		public String getMsgtype() {
			return msgType;
		}
	
		private String msgType;
		private Map<String, Serializable> props;
	}
	
	/**
	 * Message to return Icon Prefab Data properties. 
	 */
	public static class GetIconPrefabResponseMessage {
		/**
		 * Set the new Prefab properties.
		 */
		public void setType(String msgType) {
			this.msgType = msgType;
		}
		/**
		 * Set the new Prefab properties.
		 */
		public void setProperties(Map<String, Serializable> props) {
			this.props = props;
		}

		/**
		 * Get the new prefab properties. Defaults to null.
		 */
		public Map<String, Serializable> getProperties() {
			return props;
		}

		ByteBuffer getEncodedMessage() {
			if (Log.loggingDebug)
				Log.debug("PrefabPlugin: Prefab Icon response:" + " nProps=" + ((props == null) ? 0 : props.size()));

			AOByteBuffer buffer = new AOByteBuffer(1024);
			buffer.putInt(0); // length
			buffer.putInt(MSGCODE_ICON_PREFAB_RESPONSE); // message code
			buffer.putString(msgType);
			if (props == null) {
				buffer.putInt(0);
			} else {
				List<String> propStrings = new ArrayList<String>();
				int nProps = PropertyMessage.createPropertyString(propStrings, props, "");
				buffer.putInt(nProps);
				for (String s : propStrings) {
					buffer.putString(s);
				}
			}

			// patch the message length
			int len = buffer.position();
			buffer.getNioBuf().rewind();
			buffer.putInt(len - 4);
			buffer.position(len);

			return (ByteBuffer) buffer.getNioBuf().flip();
		}

		public void decodeBuffer(AOByteBuffer buffer) {
			// message code has already been read
			props = PropertyMessage.unmarshallProperyMap(buffer);
		}
		private String msgType;
		private Map<String, Serializable> props;
	}

	
	/**
	 * Respond to a prefab data request from the client. The {@code message}
	 * contains the desired prefab properties.
	 * <p>
	 * 
	 * Implementations must not read or write data to the {@code clientSocket}.
	 */
	protected GetPrefabResponseMessage handleGetPrefabMessage(GetPrefabMessage message, SocketHandler clientSocket) {
		GetPrefabResponseMessage response = new GetPrefabResponseMessage();
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		if(Log.loggingDebug)
			 log.debug("handlePrefabMessage: Client=" + clientSocket.getRemoteSocketAddress() + " start");

		if (message.getMsgtype().equals("Race")) {
			props.putAll(loadRaceData(message, clientSocket));
			response.setType("Race");
		} else if (message.getMsgtype().equals("Item")) {
			props.putAll(loadItemPrefabData(message, clientSocket));
			response.setType("Item");
		} else if (message.getMsgtype().equals("ItemAudioProfile")) {
			props.putAll(loadItemAudioPrefabData(message, clientSocket));
			response.setType("ItemAudioProfile");
		} else if (message.getMsgtype().equals("ItemSet")) {
			props.putAll(loadItemSetPrefabData(message, clientSocket));
			response.setType("ItemSet");
		} else if (message.getMsgtype().equals("CraftingRecipe")) {
			props.putAll(loadCraftingRecipePrefabData(message, clientSocket));
			response.setType("CraftingRecipe");
		} else if (message.getMsgtype().equals("Currency")) {
			props.putAll(loadCurrencyPrefabData(message, clientSocket));
			response.setType("Currency");
		} else if (message.getMsgtype().equals("Skill")) {
			props.putAll(loadSkillPrefabData(message, clientSocket));
			response.setType("Skill");
		}else if (message.getMsgtype().equals("Ability")) {
			props.putAll(loadAbilityPrefabData(message, clientSocket));
			response.setType("Ability");
		}else if (message.getMsgtype().equals("Effect")) {
			props.putAll(loadEffectPrefabData(message, clientSocket));
			response.setType("Effect");
		}else if (message.getMsgtype().equals("BuildingObject")) {
			props.putAll(loadBuildingObjectPrefabData(message, clientSocket));
			response.setType("BuildingObject");
		}else if (message.getMsgtype().equals("ResourceNode")) {
			props.putAll(loadResourceNodePrefabData(message, clientSocket));
			response.setType("ResourceNode");
		}else if (message.getMsgtype().equals("Quest")) {
			props.putAll(loadQuestPrefabData(message, clientSocket));
			response.setType("Quest");
		}else if (message.getMsgtype().equals("WeaponProfile")) {
			props.putAll(loadWeaponProfilePrefabData(message, clientSocket));
			response.setType("WeaponProfile");
		} else if (message.getMsgtype().equals("ActionSettings")) {
			props.putAll(loadActionSettingsPrefabData(message, clientSocket));
			response.setType("ActionSettings");
		} else if (message.getMsgtype().equals("GameSettings")) {
				props.putAll(loadGameSettingsPrefabData(message, clientSocket));
				response.setType("GameSettings");
		} else if(message.getMsgtype().equals("GetCountData")) {
			props.putAll(getCountData(message, clientSocket));
			response.setType("GetCountData");
		} else if (message.getMsgtype().equals("Stats")) {
			props.putAll(loadStatsPrefabData(message, clientSocket));
			response.setType("Stats");
		} else if (message.getMsgtype().equals("GetVersion")) {
			props.put("v", ServerVersion.ServerMajorVersion);
			props.put("d", ServerVersion.getBuildDate());
			response.setType("ServerVersion");
		} else if (message.getMsgtype().equals("InteractiveObjects")) {
			props.putAll(loadInteractiveObjectsPrefabData(message, clientSocket));
			response.setType("InteractiveObjects");
		} else if (message.getMsgtype().equals("SlotsProfile")) {
			props.putAll(loadSlotsProfilePrefabData(message, clientSocket));
			response.setType("SlotsProfile");
		}
		response.setProperties(props);
		if(Log.loggingDebug)
			log.debug("handlePrefabMessage: Client=" + clientSocket.getRemoteSocketAddress() + " End");

		return response;
	}

	protected Map<String, Serializable> getCountData(GetPrefabMessage message, SocketHandler clientSocket) {
		log.debug("getCountData: Start");
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		HashMap<String, Serializable> prefabData = prefabsData.get("Item");
		int numData = (int) prefabData.get("num");
		props.put("Item", numData);

		prefabData = prefabsData.get("CraftingRecipe");
		numData = (int) prefabData.get("num");
		props.put("CraftingRecipe", numData);

		prefabData = prefabsData.get("Currency");
		numData = (int) prefabData.get("num");
		props.put("Currency", numData);

		prefabData = prefabsData.get("ItemSet");
		numData = (int) prefabData.get("num");
		props.put("ItemSet", numData);

		prefabData = prefabsData.get("Skill");
		numData = (int) prefabData.get("num");
		props.put("Skill", numData);

		prefabData = prefabsData.get("Ability");
		numData = (int) prefabData.get("num");
		props.put("Ability", numData);

		prefabData = prefabsData.get("Effect");
		numData = (int) prefabData.get("num");
		props.put("Effect", numData);

		prefabData = prefabsData.get("BuildingObject");
		numData = (int) prefabData.get("num");
		props.put("BuildingObject", numData);

		prefabData = prefabsData.get("ResourceNode");
		numData = (int) prefabData.get("num");
		props.put("ResourceNode", numData);

		props.put("Race", characterTemplates.size());

		prefabData = prefabsData.get("GlobalEvents");
		numData = (int) prefabData.get("num");
		props.put("GlobalEvents", numData);

		prefabData = prefabsData.get("Quest");
		numData = (int) prefabData.get("num");
		props.put("Quests", numData);

		prefabData = prefabsData.get("WeaponProfile");
		numData = (int) prefabData.get("num");
		props.put("WeaponProfile", numData);

		prefabData = prefabsData.get("ItemAudio");
		numData = (int) prefabData.get("num");
		props.put("ItemAudioProfile", numData);

		prefabData = prefabsData.get("Stats");
		numData = (int) prefabData.get("num");
		props.put("Stats", numData);

		prefabData = prefabsData.get("InteractiveObjects");
		numData = (int) prefabData.get("num");
		props.put("InteractiveObjects", numData);

		prefabData = prefabsData.get("SlotsProfile");
		numData = (int) prefabData.get("num");
		props.put("SlotsProfile", numData);
		log.debug("getCountData: end");
		return props;

	}

	protected Map<String, Serializable> loadActionSettingsPrefabData(GetPrefabMessage message, SocketHandler clientSocket){
		log.debug("loadActionSettingsPrefabData: Start");
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();

		HashMap<Integer, String> data = RequirementChecker.getEditorOptionChoice("Weapon Actions");
		Map<Integer, String> actions = new TreeMap<Integer, String>(data);
		if(Log.loggingDebug)
			log.debug("loadActionSettingsPrefabData: objs=" + actions );
		int c = 0;
		for (Map.Entry action : actions.entrySet()) {
			props.put("k" + c, (Integer)action.getKey());
			props.put("a" + c, (String)action.getValue());
            c++;
		}
		props.put("aNum", c);
		log.debug("loadActionSettingsPrefabData: count "+c+" "+props);
		log.debug("loadActionSettingsPrefabData: end");
		return props;

	}

	protected Map<String, Serializable> loadGameSettingsPrefabData(GetPrefabMessage message, SocketHandler clientSocket){
		log.debug("loadGameSettingsPrefabData: Start");
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();

		HashMap<Integer, String> data = RequirementChecker.getEditorOptionChoice("Item Quality");
		Map<Integer, String> actions = new TreeMap<Integer, String>(data);
		if(Log.loggingDebug)
			log.debug("loadGameSettingsPrefabData: objs=" + actions );
		int c = 0;
		for (Map.Entry action : actions.entrySet()) {
			props.put("i" + c, (Integer)action.getKey());
			props.put("c" + c, (String)action.getValue());
			c++;
		}
		props.put("num", c);
		log.debug("loadGameSettingsPrefabData: count "+c+" "+props);
		log.debug("loadGameSettingsPrefabData: end");
		return props;

	}

	/**
	 * Prepare response data of the slots profile definition
	 * @param message
	 * @param clientSocket
	 * @return
	 */
	protected Map<String, Serializable> loadSlotsProfilePrefabData(GetPrefabMessage message, SocketHandler clientSocket) {
		log.debug("loadSlotsProfilePrefabData: Start");
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if (Log.loggingDebug)
			log.debug("loadSlotsProfilePrefabData: objs=" + plyItems);
		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
		HashMap<String, Serializable> prefabData = prefabsData.get("SlotsProfile");
		if (Log.loggingDebug)
			Log.debug("loadSlotsProfilePrefabData plyItems=" + plyItems.size() + " prefabData=" + prefabData.size());
		int numData = (int) prefabData.get("num");
		if (Log.loggingDebug) Log.debug("loadSlotsProfilePrefabData numData=" + numData);

		int c = 0;

		try {
			for (int i = 0; i < numData; i++) {
				int id = (int) prefabData.get("i" + i + "id");
				long date = (long) prefabData.get("i" + i + "date");

				if (plyItems.containsKey(id)) {
					objs.remove(id);
					if (date != plyItems.get(id)) {
						if (Log.loggingDebug) Log.debug("loadSlotsProfilePrefabData: id=" + id + " newer date");
						props.put("i" + c + "id", id);
						props.put("i" + c + "date", date);
						props.put("i" + c + "name", prefabData.get("i" + i + "name"));
						int sNum = (int) prefabData.get("i" + i + "num");
						for (int j = 0; j < sNum; j++) {
							props.put("i" + c + "s" + j, prefabData.get("i" + i + "s" + j));
						}
						props.put("i" + c + "num", sNum);
						c++;
					}

				} else {
					if (Log.loggingDebug) Log.debug("loadSlotsProfilePrefabData: id=" + id + " new ");
					props.put("i" + c + "id", id);
					props.put("i" + c + "date", date);
					props.put("i" + c + "name", prefabData.get("i" + i + "name"));
					int sNum = (int) prefabData.get("i" + i + "num");
					for (int j = 0; j < sNum; j++) {
						props.put("i" + c + "s" + j, prefabData.get("i" + i + "s" + j));
					}
					props.put("i" + c + "num", sNum);
					c++;
				}
				if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
					props.put("all", false);
					props.put("num", c);
					if (Log.loggingDebug) log.debug("loadSlotsProfilePrefabData: end send items " + c);
					return props;
				}
			}
		} catch (Exception e) {
			log.exception(e);
			e.printStackTrace();
		}
		if (objs.size() > 0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove", sj.toString());
		}
		props.put("num", c);
		props.put("all", true);
		if (Log.loggingDebug) log.debug("loadSlotsProfilePrefabData: end send items " + c);
		return props;
	}


	/**
	 * Prepare response data of the stats definition
	 * @param message
	 * @param clientSocket
	 * @return
	 */
	protected Map<String, Serializable> loadStatsPrefabData(GetPrefabMessage message, SocketHandler clientSocket){
		log.debug("loadStatsPrefabData: Start");
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if (Log.loggingDebug)
			log.debug("loadStatsPrefabData: objs=" + plyItems);
		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
		HashMap<String, Serializable> prefabData = prefabsData.get("Stats");
		if (Log.loggingDebug)
			Log.debug("loadStatsPrefabData plyItems=" + plyItems.size() + " prefabData=" + prefabData.size());
		int numData = (int) prefabData.get("num");
		if (Log.loggingDebug) Log.debug("loadStatsPrefabData numData=" + numData);

		int c = 0;

		try {
			for (int i = 0; i < numData; i++) {
				int id = (int) prefabData.get("s" + i + "id");
				long date = (long) prefabData.get("s" + i + "date");

				if (plyItems.containsKey(id)) {
					objs.remove(id);
					if (date != plyItems.get(id)) {
						if (Log.loggingDebug) Log.debug("loadStatsPrefabData: id=" + id + " newer date");
						props.put("s" + c + "date", date);
						props.put("s" + c + "name", prefabData.get("s" + i + "name"));
						props.put("s" + c + "prec", prefabData.get("s" + i + "prec"));
						props.put("s" + c + "id", id);
						c++;
					}

				} else {
					if (Log.loggingDebug) Log.debug("loadStatsPrefabData: id=" + id + " new ");
					props.put("s" + c + "date", date);
					props.put("s" + c + "name", prefabData.get("s" + i + "name"));
					props.put("s" + c + "prec", prefabData.get("s" + i + "prec"));
					props.put("s" + c + "id", id);
					c++;
				}
				if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
					props.put("all", false);
					props.put("num", c);
					if (Log.loggingDebug) log.debug("loadStatsPrefabData: end send items " + c);
					return props;
				}
			}
		} catch (Exception e) {
			log.exception(e);
			e.printStackTrace();
		}
		if (objs.size() > 0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove", sj.toString());
		}
		props.put("num", c);
		props.put("all", true);
		if (Log.loggingDebug) log.debug("loadStatsPrefabData: end send items " + c);
		return props;

	}

	protected Map<String, Serializable> loadRaceData(GetPrefabMessage message, SocketHandler clientSocket){
		log.debug("loadRaceData: Start");
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		  int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if(Log.loggingDebug)
			log.debug("loadRaceData: objs=" + plyItems );
		int c = 0;
		for (CharacterTemplate ct : characterTemplates.values()) {
			props.put("raceId" + c, ct.getRace());
			props.put("raceName" + c, RequirementChecker.getRace(ct.getRace()));
			props.put("raceDesc" + c, ct.race_description);
			props.put("raceIconP" + c, ct.race_icon);
			props.put("raceIcon" + c, ct.race_icon2);
			props.put("classId" + c, ct.getAspect());
			props.put("className" + c, RequirementChecker.getClass(ct.getAspect()));
			props.put("classDesc" + c, ct.class_description);
			props.put("classIconP" + c, ct.class_icon);
			props.put("classIcon" + c, ct.class_icon2);
			int g = 0;
			for (int key : ct.gender.keySet()) {
				props.put("genId" + c + "_" + g, key);
				props.put("genName" + c + "_" + g, RequirementChecker.getNameEditorOptionChoice("Gender", key));
				props.put("genPrefab" + c + "_" + g, ct.gender.get(key));
				props.put("genIcon" + c + "_" + g, ct.genderIcon.get(key));
				props.put("genIconP" + c + "_" + g, ct.genderIconPath.get(key));
				g++;
			}
			props.put("gennum" + c, g);
			c++;
		}
		props.put("num", c);
		
	
		props.put("all", true);
		log.debug("loadRaceData: end");
		return props;
	 
 }
	/**
	 * New Method to return Item prefabs data do client
	 * @param message
	 * @param clientSocket
	 * @return
	 */
		
		protected Map<String, Serializable> loadItemPrefabData(GetPrefabMessage message, SocketHandler clientSocket) {
			HashMap<String, Serializable> props = new HashMap<String, Serializable>();
			int num = (int) message.getProperties().get("c");
			HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
			for (int i = 0; i < num; i++) {
				plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
			}
			if (Log.loggingDebug)
				log.debug("loadItemPrefabData: objs=" + plyItems);
			HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
			HashMap<String, Serializable> prefabData = prefabsData.get("Item");
			if(Log.loggingDebug)Log.debug("loadItemPrefabData plyItems="+plyItems.size()+" prefabData="+prefabData.size());
			int numData = (int) prefabData.get("num");
			if(Log.loggingDebug)	Log.debug("loadItemPrefabData numData="+numData);
			
			int c = 0;
			
			try {
				for (int i = 0; i < numData; i++) {
					int id = (int) prefabData.get("i" + i + "id");
					long date = (long) prefabData.get("i" + i + "date");

					if (plyItems.containsKey(id)) {
						objs.remove(id);
						if (date != plyItems.get(id)) {
							if(Log.loggingDebug)	Log.debug("GetItemPrefabDataHook: id=" + id + " newer date");

							props.put("i" + c + "date", date);
							props.put("i" + c + "name", prefabData.get("i" + i + "name"));
							props.put("i" + c + "id", id);
							props.put("i" + c + "tooltip", prefabData.get("i" + i + "tooltip"));
							props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
							props.put("i" + c + "icon2", ""/* HelperFunctions.readEncodedString(rs.getBytes("icon2")) */);
							props.put("i" + c + "itemType", prefabData.get("i" + i + "itemType"));
							props.put("i" + c + "subType", prefabData.get("i" + i + "subType"));
							props.put("i" + c + "slot", prefabData.get("i" + i + "slot"));
							props.put("i" + c + "quality", prefabData.get("i" + i + "quality"));
							props.put("i" + c + "currType", prefabData.get("i" + i + "currType"));
							props.put("i" + c + "cost", prefabData.get("i" + i + "cost"));
							props.put("i" + c + "binding", prefabData.get("i" + i + "binding"));
							props.put("i" + c + "sellable", prefabData.get("i" + i + "sellable"));
							props.put("i" + c + "dValue", prefabData.get("i" + i + "dValue"));
							props.put("i" + c + "dMValue", prefabData.get("i" + i + "dMValue"));
							props.put("i" + c + "setId", prefabData.get("i" + i + "setId"));
							props.put("i" + c + "enchId", prefabData.get("i" + i + "enchId"));
							props.put("i" + c + "wSpeed", prefabData.get("i" + i + "wSpeed"));
							props.put("i" + c + "sLimit", prefabData.get("i" + i + "sLimit"));
							props.put("i" + c + "aH", prefabData.get("i" + i + "aH"));
							props.put("i" + c + "unique", prefabData.get("i" + i + "unique"));
							props.put("i" + c + "gear_score", prefabData.get("i" + i + "gear_score"));

							props.put("i" + c + "durability", prefabData.get("i" + i + "durability"));
							props.put("i" + c + "sockettype", prefabData.get("i" + i + "sockettype"));
							props.put("i" + c + "parry", prefabData.get("i" + i + "parry"));
							props.put("i" + c + "weight", prefabData.get("i" + i + "weight"));
							props.put("i" + c + "deathLoss", prefabData.get("i" + i + "deathLoss"));
							props.put("i" + c + "autoattack", prefabData.get("i" + i + "autoattack"));
							props.put("i" + c + "ammotype", prefabData.get("i" + i + "ammotype"));
							props.put("i" + c + "repairable", prefabData.get("i" + i + "repairable"));
							props.put("i" + c + "wp", prefabData.get("i" + i + "wp"));
							props.put("i" + c + "apid", prefabData.get("i" + i + "apid"));
							props.put("i" + c + "gp", prefabData.get("i" + i + "gp"));

							int effnum = (int) prefabData.get("i" + i + "enum");
							for (int j = 0; j < effnum; j++) {
								props.put("i" + c + "eT" + j, prefabData.get("i" + i + "eT" + j));
								props.put("i" + c + "eN" + j, prefabData.get("i" + i + "eN" + j));
								props.put("i" + c + "eV" + j, prefabData.get("i" + i + "eV" + j));

							}
							props.put("i" + c + "enum", effnum);
							props.put("i" + c + "rnum", 0);

							props.put("i" + c + "reqType", prefabData.get("i" + i + "reqType"));
							props.put("i" + c + "reqNames", prefabData.get("i" + i + "reqNames"));
							props.put("i" + c + "reqValues", prefabData.get("i" + i + "reqValues"));
							c++;
						}

					} else {
						if(Log.loggingDebug)	Log.debug("GetItemPrefabDataHook: id=" + id + " new ");
						props.put("i" + c + "date", date);
						props.put("i" + c + "name", prefabData.get("i" + i + "name"));
						props.put("i" + c + "id", id);
						props.put("i" + c + "tooltip", prefabData.get("i" + i + "tooltip"));
						props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
						props.put("i" + c + "icon2", ""/* HelperFunctions.readEncodedString(rs.getBytes("icon2")) */);
						props.put("i" + c + "itemType", prefabData.get("i" + i + "itemType"));
						props.put("i" + c + "subType", prefabData.get("i" + i + "subType"));
						props.put("i" + c + "slot", prefabData.get("i" + i + "slot"));
						props.put("i" + c + "quality", prefabData.get("i" + i + "quality"));
						props.put("i" + c + "currType", prefabData.get("i" + i + "currType"));
						props.put("i" + c + "cost", prefabData.get("i" + i + "cost"));
						props.put("i" + c + "binding", prefabData.get("i" + i + "binding"));
						props.put("i" + c + "sellable", prefabData.get("i" + i + "sellable"));
						props.put("i" + c + "dValue", prefabData.get("i" + i + "dValue"));
						props.put("i" + c + "dMValue", prefabData.get("i" + i + "dMValue"));
						props.put("i" + c + "setId", prefabData.get("i" + i + "setId"));
						props.put("i" + c + "enchId", prefabData.get("i" + i + "enchId"));
						props.put("i" + c + "wSpeed", prefabData.get("i" + i + "wSpeed"));
						props.put("i" + c + "sLimit", prefabData.get("i" + i + "sLimit"));
						props.put("i" + c + "aH", prefabData.get("i" + i + "aH"));
						props.put("i" + c + "unique", prefabData.get("i" + i + "unique"));
						props.put("i" + c + "gear_score", prefabData.get("i" + i + "gear_score"));

						props.put("i" + c + "durability", prefabData.get("i" + i + "durability"));
						props.put("i" + c + "sockettype", prefabData.get("i" + i + "sockettype"));
						props.put("i" + c + "parry", prefabData.get("i" + i + "parry"));
						props.put("i" + c + "weight", prefabData.get("i" + i + "weight"));
						props.put("i" + c + "deathLoss", prefabData.get("i" + i + "deathLoss"));
						props.put("i" + c + "autoattack", prefabData.get("i" + i + "autoattack"));
						props.put("i" + c + "ammotype", prefabData.get("i" + i + "ammotype"));
						props.put("i" + c + "repairable", prefabData.get("i" + i + "repairable"));
						props.put("i" + c + "wp", prefabData.get("i" + i + "wp"));
						props.put("i" + c + "apid", prefabData.get("i" + i + "apid"));
						props.put("i" + c + "gp", prefabData.get("i" + i + "gp"));

						int effnum = (int) prefabData.get("i" + i + "enum");
						for (int j = 0; j < effnum; j++) {
							props.put("i" + c + "eT" + j, prefabData.get("i" + i + "eT" + j));
							props.put("i" + c + "eN" + j, prefabData.get("i" + i + "eN" + j));
							props.put("i" + c + "eV" + j, prefabData.get("i" + i + "eV" + j));

						}
						
						props.put("i" + c + "enum", effnum);
						props.put("i" + c + "rnum", 0);

						props.put("i" + c + "reqType", prefabData.get("i" + i + "reqType"));
						props.put("i" + c + "reqNames", prefabData.get("i" + i + "reqNames"));
						props.put("i" + c + "reqValues", prefabData.get("i" + i + "reqValues"));
						c++;
					}
					if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
						props.put("all", false);
						props.put("num", c);
						log.debug("loadItemPrefabData: end send items "+c);
						return props;
					}
				}
			} catch (Exception e) {
				log.exception(e);
				e.printStackTrace();
			}
			if (objs.size() > 0) {
				StringJoiner sj = new StringJoiner(";");
				for (Integer s : objs.keySet()) {
					sj.add(s.toString());
				}
				props.put("toRemove", sj.toString());
			}
			props.put("num", c);

			props.put("all", true);
			log.debug("loadItemPrefabData: end send items "+c);
			return props;
		}
	/**
	 * New Method to return Item prefabs data do client
	 * @param message
	 * @param clientSocket
	 * @return
	 */

	protected Map<String, Serializable> loadWeaponProfilePrefabData(GetPrefabMessage message, SocketHandler clientSocket) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if (Log.loggingDebug)
			log.debug("loadWeaponProfilePrefabData: objs=" + plyItems);
		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
		HashMap<String, Serializable> prefabData = prefabsData.get("WeaponProfile");
		if (Log.loggingDebug)
			Log.debug("loadWeaponProfilePrefabData plyItems=" + plyItems.size() + " prefabData=" + prefabData.size());
		int numData = (int) prefabData.get("num");
		if (Log.loggingDebug) Log.debug("loadWeaponProfilePrefabData numData=" + numData);

		int c = 0;

		try {
			for (int i = 0; i < numData; i++) {
				int id = (int) prefabData.get("i" + i + "id");
				long date = (long) prefabData.get("i" + i + "date");

				if (plyItems.containsKey(id)) {
					objs.remove(id);
					if (date != plyItems.get(id)) {
						if (Log.loggingDebug) Log.debug("loadWeaponProfilePrefabData: id=" + id + " newer date");

						props.put("i" + c + "date", date);
						props.put("i" + c + "name", prefabData.get("i" + i + "name"));
						props.put("i" + c + "id", id);
						int sNum = (int) prefabData.get("i" + i + "sNum");
						for (int j = 0; j < sNum; j++) {
							props.put("i" + c + "s" + j + "ac", prefabData.get("i" + i + "s" + j + "ac"));
							props.put("i" + c + "s" + j + "ab", prefabData.get("i" + i + "s" + j + "ab"));
							props.put("i" + c + "s" + j + "s", prefabData.get("i" + i + "s" + j + "s"));
							props.put("i" + c + "s" + j + "z", prefabData.get("i" + i + "s" + j + "z"));
							props.put("i" + c + "s" + j + "c", prefabData.get("i" + i + "s" + j + "c"));
						}
						props.put("i" + c + "sNum", sNum);
						c++;
					}

				} else {
					if (Log.loggingDebug) Log.debug("loadWeaponProfilePrefabData: id=" + id + " new ");
					props.put("i" + c + "date", date);
					props.put("i" + c + "name", prefabData.get("i" + i + "name"));
					props.put("i" + c + "id", id);
					int sNum = (int) prefabData.get("i" + i + "sNum");
					for (int j = 0; j < sNum; j++) {
						props.put("i" + c + "s" + j + "ac", prefabData.get("i" + i + "s" + j + "ac"));
						props.put("i" + c + "s" + j + "ab", prefabData.get("i" + i + "s" + j + "ab"));
						props.put("i" + c + "s" + j + "z", prefabData.get("i" + i + "s" + j + "z"));
						props.put("i" + c + "s" + j + "s", prefabData.get("i" + i + "s" + j + "s"));
						props.put("i" + c + "s" + j + "c", prefabData.get("i" + i + "s" + j + "c"));
					}
					props.put("i" + c + "sNum", sNum);
					c++;
				}
				if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
					props.put("all", false);
					props.put("num", c);
					if (Log.loggingDebug) log.debug("loadWeaponProfilePrefabData: end send items " + c);
					return props;
				}
			}
		} catch (Exception e) {
			log.exception(e);
			e.printStackTrace();
		}
		if (objs.size() > 0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove", sj.toString());
		}
		props.put("num", c);

		props.put("all", true);
		if (Log.loggingDebug) log.debug("loadWeaponProfilePrefabData: end send items " + c);
		return props;
	}

	/**
	 * New Method to return Item prefabs data do client
	 * @param message
	 * @param clientSocket
	 * @return
	 */

	protected Map<String, Serializable> loadInteractiveObjectsPrefabData(GetPrefabMessage message, SocketHandler clientSocket) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if (Log.loggingDebug)
			log.debug("loadInteractiveObjectsPrefabData: objs=" + plyItems);
		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
		HashMap<String, Serializable> prefabData = prefabsData.get("InteractiveObjects");
		if (Log.loggingDebug)
			Log.debug("loadWeaponProfilePrefabData plyItems=" + plyItems.size() + " prefabData=" + prefabData.size());
		int numData = (int) prefabData.get("num");
		if (Log.loggingDebug) Log.debug("loadInteractiveObjectsPrefabData numData=" + numData);

		int c = 0;

		try {
			for (int i = 0; i < numData; i++) {
				int id = (int) prefabData.get("i" + i + "id");
				long date = (long) prefabData.get("i" + i + "date");

				if (plyItems.containsKey(id)) {
					objs.remove(id);
					if (date != plyItems.get(id)) {
						if (Log.loggingDebug) Log.debug("loadInteractiveObjectsPrefabData: id=" + id + " newer date");

						props.put("i" + c + "date", date);
						props.put("i" + c + "name", prefabData.get("i" + i + "name"));
						props.put("i" + c + "id", id);
						props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
						props.put("i" + c + "icon2", prefabData.get("i" + i + "icon2"));
						props.put("i" + c + "ce", prefabData.get("i" + i + "ce"));
						props.put("i" + c + "qr",prefabData.get("i" + i + "qr"));
						props.put("i" + c + "it",prefabData.get("i" + i + "it"));
						props.put("i" + c + "iid",prefabData.get("i" + i + "iid"));
//						props.put("i" + c + "id1",prefabData.get("i" + i + "id1"));
//						props.put("i" + c + "id2",prefabData.get("i" + i + "id2"));
//						props.put("i" + c + "id3",prefabData.get("i" + i + "id3"));
						props.put("i" + c + "rt",prefabData.get("i" + i + "rt"));
						props.put("i" + c + "irt",prefabData.get("i" + i + "irt"));

						props.put("i" + c + "dd",prefabData.get("i" + i + "dd"));
						props.put("i" + c + "dt",prefabData.get("i" + i + "dt"));
						props.put("i" + c + "mb",prefabData.get("i" + i + "mb"));
						props.put("i" + c + "ul",prefabData.get("i" + i + "ul"));
						props.put("i" + c + "ml",prefabData.get("i" + i + "ml"));
						props.put("i" + c + "nl",prefabData.get("i" + i + "nl"));

						props.put("i" + c + "ir",prefabData.get("i" + i + "ir"));
						props.put("i" + c + "irg",prefabData.get("i" + i + "irg"));
						props.put("i" + c + "icr",prefabData.get("i" + i + "icr"));
						props.put("i" + c + "cr",prefabData.get("i" + i + "cr"));
						props.put("i" + c + "crg",prefabData.get("i" + i + "crg"));
						props.put("i" + c + "ccr",prefabData.get("i" + i + "ccr"));
						c++;
					}

				} else {
					if (Log.loggingDebug) Log.debug("loadInteractiveObjectsPrefabData: id=" + id + " new ");
					props.put("i" + c + "date", date);
					props.put("i" + c + "name", prefabData.get("i" + i + "name"));
					props.put("i" + c + "id", id);
					props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
					props.put("i" + c + "icon2", prefabData.get("i" + i + "icon2"));
					props.put("i" + c + "ce", prefabData.get("i" + i + "ce"));
					props.put("i" + c + "qr",prefabData.get("i" + i + "qr"));
					props.put("i" + c + "it",prefabData.get("i" + i + "it"));
					props.put("i" + c + "iid",prefabData.get("i" + i + "iid"));
//						props.put("i" + c + "id1",prefabData.get("i" + i + "id1"));
//						props.put("i" + c + "id2",prefabData.get("i" + i + "id2"));
//						props.put("i" + c + "id3",prefabData.get("i" + i + "id3"));
					props.put("i" + c + "rt",prefabData.get("i" + i + "rt"));
					props.put("i" + c + "irt",prefabData.get("i" + i + "irt"));

					props.put("i" + c + "dd",prefabData.get("i" + i + "dd"));
					props.put("i" + c + "dt",prefabData.get("i" + i + "dt"));
					props.put("i" + c + "mb",prefabData.get("i" + i + "mb"));
					props.put("i" + c + "ul",prefabData.get("i" + i + "ul"));
					props.put("i" + c + "ml",prefabData.get("i" + i + "ml"));
					props.put("i" + c + "nl",prefabData.get("i" + i + "nl"));

					props.put("i" + c + "ir",prefabData.get("i" + i + "ir"));
					props.put("i" + c + "irg",prefabData.get("i" + i + "irg"));
					props.put("i" + c + "icr",prefabData.get("i" + i + "icr"));
					props.put("i" + c + "cr",prefabData.get("i" + i + "cr"));
					props.put("i" + c + "crg",prefabData.get("i" + i + "crg"));
					props.put("i" + c + "ccr",prefabData.get("i" + i + "ccr"));
					c++;
				}
				if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
					props.put("all", false);
					props.put("num", c);
					if (Log.loggingDebug) log.debug("loadInteractiveObjectsPrefabData: end send items " + c);
					return props;
				}
			}
		} catch (Exception e) {
			log.exception(e);
			e.printStackTrace();
		}
		if (objs.size() > 0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove", sj.toString());
		}
		props.put("num", c);

		props.put("all", true);
		if (Log.loggingDebug) log.debug("loadInteractiveObjectsPrefabData: end send items " + c);
		return props;
	}
	/**
	 * New Method to return Item Audio Profile Prefab data to the client
	 * @param message
	 * @param clientSocket
	 * @return
	 */
	protected Map<String, Serializable> loadItemAudioPrefabData(GetPrefabMessage message, SocketHandler clientSocket) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if (Log.loggingDebug)
			log.debug("loadItemAudioPrefabData: objs=" + plyItems);

		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
		HashMap<String, Serializable> prefabData = prefabsData.get("ItemAudio");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			long date = (long) prefabData.get("i" + i + "date");
			int currencyID = (int) prefabData.get("i" + i + "id");
			if (plyItems.containsKey(currencyID)) {
				objs.remove(currencyID);
				if (date != plyItems.get(currencyID)) {
					if(Log.loggingDebug)Log.debug("loadItemAudioPrefabData: id=" + currencyID + " newer date");

					props.put("i" + c + "date", date);
					props.put("i" + c + "id", currencyID);
					props.put("i" + c + "name", prefabData.get("i" + i + "name"));
					props.put("i" + c + "u", prefabData.get("i" + i + "u"));
					props.put("i" + c + "db", prefabData.get("i" + i + "db"));
					props.put("i" + c + "de", prefabData.get("i" + i + "de"));
					props.put("i" + c + "d", prefabData.get("i" + i + "d"));
					props.put("i" + c + "b", prefabData.get("i" + i + "b"));
					props.put("i" + c + "pu", prefabData.get("i" + i + "pu"));
					props.put("i" + c + "f", prefabData.get("i" + i + "f"));
					props.put("i" + c + "dr", prefabData.get("i" + i + "dr"));
					c++;
				}
			} else {
				if(Log.loggingDebug)Log.debug("loadItemAudioPrefabData: id=" + currencyID + " new ");
				props.put("i" + c + "date", date);
				props.put("i" + c + "id", currencyID);
				props.put("i" + c + "name", prefabData.get("i" + i + "name"));
				props.put("i" + c + "u", prefabData.get("i" + i + "u"));
				props.put("i" + c + "db", prefabData.get("i" + i + "db"));
				props.put("i" + c + "de", prefabData.get("i" + i + "de"));
				props.put("i" + c + "d", prefabData.get("i" + i + "d"));
				props.put("i" + c + "b", prefabData.get("i" + i + "b"));
				props.put("i" + c + "pu", prefabData.get("i" + i + "pu"));
				props.put("i" + c + "f", prefabData.get("i" + i + "f"));
				props.put("i" + c + "dr", prefabData.get("i" + i + "dr"));
				c++;
			}
			if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
				props.put("all", false);
				props.put("num", c);
				return props;
			}
		}
		if (objs.size() > 0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove", sj.toString());
		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadItemAudioPrefabData: end");
		return props;

	}

	/**
	 * Loading Prefab Data From Database and store it
	 * 
	 * @return
	 */
	protected HashMap<String, Serializable> loadCraftingRecipePrefabData() {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		if (iDB == null)
			iDB = new ItemDatabase(false);
		HashMap<Integer, CraftingRecipe> recipes = iDB.loadCraftingRecipes();
		int c = 0;
		for (Integer i : recipes.keySet()) {
			CraftingRecipe rc = recipes.get(i);

			if (Log.loggingDebug)
				Log.debug("loadCraftingRecipePrefabData: id=" + rc.getID() + " new");
			props.put("i" + c + "date", rc.GetDate());
			props.put("i" + c + "id", rc.getID());
			props.put("i" + c + "name", rc.getName());
			props.put("i" + c + "skillid", rc.getSkillID());
			props.put("i" + c + "skillreqlev", rc.getRequiredSkillLevel());
			props.put("i" + c + "statreq", rc.getStationReq());
			props.put("i" + c + "ctime", rc.getCreationTime());
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : rc.getResultItemIds()) {
				sj.add(s.toString());
			}
			props.put("i" + c + "items1", sj.toString());
			sj = new StringJoiner(";");
			for (Integer s : rc.getResultItemIds2()) {
				sj.add(s.toString());
			}
			props.put("i" + c + "items2", sj.toString());
			sj = new StringJoiner(";");
			for (Integer s : rc.getResultItemIds3()) {
				sj.add(s.toString());
			}
			props.put("i" + c + "items3", sj.toString());
			sj = new StringJoiner(";");
			for (Integer s : rc.getResultItemIds4()) {
				sj.add(s.toString());
			}
			props.put("i" + c + "items4", sj.toString());
			sj = new StringJoiner(";");
			for (Integer s : rc.getResultItemCounts()) {
				sj.add(s.toString());
			}
			props.put("i" + c + "items1c", sj.toString());
			sj = new StringJoiner(";");
			for (Integer s : rc.getResultItemCounts2()) {
				sj.add(s.toString());
			}
			props.put("i" + c + "items2c", sj.toString());
			sj = new StringJoiner(";");
			for (Integer s : rc.getResultItemCounts3()) {
				sj.add(s.toString());
			}
			props.put("i" + c + "items3c", sj.toString());
			sj = new StringJoiner(";");
			for (Integer s : rc.getResultItemCounts4()) {
				sj.add(s.toString());
			}
			props.put("i" + c + "items4c", sj.toString());
			ArrayList<Integer> Items = new ArrayList<Integer>();
			ArrayList<Integer> ItemCounts = new ArrayList<Integer>();
			for (LinkedList<CraftingComponent> lis : rc.getRequiredCraftingComponents()) {
				for (CraftingComponent cc : lis) {
					if (cc.getItemId() > 0) {
						Items.add(cc.getItemId());
						ItemCounts.add(cc.getCount());
					}
				}
			}
			if (Log.loggingDebug)
				log.debug("loadCraftingRecipePrefabData: Req Items:" + Items + " ItemCounts:" + ItemCounts);

			sj = new StringJoiner(";");
			for (Integer s : Items) {
				sj.add(s.toString());
			}
			if (Log.loggingDebug)
				log.debug("loadCraftingRecipePrefabData: req:" + sj.toString());

			props.put("i" + c + "itemsreq", sj.toString());
			sj = new StringJoiner(";");
			for (Integer s : ItemCounts) {
				sj.add(s.toString());
			}
			if (Log.loggingDebug)
				log.debug("loadCraftingRecipePrefabData: reqc:" + sj.toString());
			props.put("i" + c + "itemsreqc", sj.toString());
			props.put("i" + c + "name", rc.getName());

			props.putAll(iDB.loadIconCraftingRecipesPrefabData(rc.getID(), c));
			c++;
		}
		props.put("num", c);
		log.debug("loadCraftingRecipePrefabData: end");
		return props;
	}
	
	/**
	 * New Method to return Crafting Recipe Prefab data to client
	 * @param message
	 * @param clientSocket
	 * @return
	 */

	protected Map<String, Serializable> loadCraftingRecipePrefabData(GetPrefabMessage message, SocketHandler clientSocket) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if (Log.loggingDebug)
			log.debug("loadCraftingRecipePrefabData: objs=" + plyItems);
		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);

		HashMap<String, Serializable> prefabData = prefabsData.get("CraftingRecipe");
		
		
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			int id = (int) prefabData.get("i" + i + "id");
			long date = (long) prefabData.get("i" + i + "date");

			if (plyItems.containsKey(id)) {
				objs.remove(id);
				if (date != plyItems.get(id)) {
					if(Log.loggingDebug)
						Log.debug("loadCraftingRecipePrefabData: id=" +id +" newer date " );
					props.put("i" + c + "date", date);
					props.put("i" + c + "id", id);
					props.put("i" + c + "name", prefabData.get("i" + i + "name"));
					props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
					props.put("i" + c + "skillid", prefabData.get("i" + i + "skillid"));
					props.put("i" + c + "skillreqlev", prefabData.get("i" + i + "skillreqlev"));
					props.put("i" + c + "statreq", prefabData.get("i" + i + "statreq"));
					props.put("i" + c + "ctime", prefabData.get("i" + i + "ctime"));
					props.put("i" + c + "items1", prefabData.get("i" + i + "items1"));
					props.put("i" + c + "items2", prefabData.get("i" + i + "items2"));
					props.put("i" + c + "items3", prefabData.get("i" + i + "items3"));
					props.put("i" + c + "items4", prefabData.get("i" + i + "items4"));
					props.put("i" + c + "items1c", prefabData.get("i" + i + "items1c"));
					props.put("i" + c + "items2c", prefabData.get("i" + i + "items2c"));
					props.put("i" + c + "items3c", prefabData.get("i" + i + "items3c"));
					props.put("i" + c + "items4c", prefabData.get("i" + i + "items4c"));
					props.put("i" + c + "itemsreq", prefabData.get("i" + i + "itemsreq"));
					props.put("i" + c + "itemsreqc", prefabData.get("i" + i + "itemsreqc"));
					c++;
				}
			} else {
				if(Log.loggingDebug)
					Log.debug("loadCraftingRecipePrefabData: id=" +id +" new" );
				props.put("i" + c + "date", date);
				props.put("i" + c + "id", id);
				props.put("i" + c + "name", prefabData.get("i" + i + "name"));
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				props.put("i" + c + "skillid", prefabData.get("i" + i + "skillid"));
				props.put("i" + c + "skillreqlev", prefabData.get("i" + i + "skillreqlev"));
				props.put("i" + c + "statreq", prefabData.get("i" + i + "statreq"));
				props.put("i" + c + "ctime", prefabData.get("i" + i + "ctime"));
				props.put("i" + c + "items1", prefabData.get("i" + i + "items1"));
				props.put("i" + c + "items2", prefabData.get("i" + i + "items2"));
				props.put("i" + c + "items3", prefabData.get("i" + i + "items3"));
				props.put("i" + c + "items4", prefabData.get("i" + i + "items4"));
				props.put("i" + c + "items1c", prefabData.get("i" + i + "items1c"));
				props.put("i" + c + "items2c", prefabData.get("i" + i + "items2c"));
				props.put("i" + c + "items3c", prefabData.get("i" + i + "items3c"));
				props.put("i" + c + "items4c", prefabData.get("i" + i + "items4c"));
				props.put("i" + c + "itemsreq", prefabData.get("i" + i + "itemsreq"));
				props.put("i" + c + "itemsreqc", prefabData.get("i" + i + "itemsreqc"));
				c++;
			}
			
			if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
				props.put("all", false);
				props.put("num", c);
				return props;
			}
		}
		if(objs.size()>0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove",sj.toString());
		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadCraftingRecipePrefabData: end");
		return props;
 }
	
	
	/**
	 * New Method to return Currency Prefab data to the client
	 * @param message
	 * @param clientSocket
	 * @return
	 */
	protected Map<String, Serializable> loadCurrencyPrefabData(GetPrefabMessage message, SocketHandler clientSocket) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if (Log.loggingDebug)
			log.debug("loadCurrencyPrefabData: objs=" + plyItems);

		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
		HashMap<String, Serializable> prefabData = prefabsData.get("Currency");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			long date = (long) prefabData.get("i" + i + "date");
			int currencyID = (int) prefabData.get("i" + i + "id");
			if (plyItems.containsKey(currencyID)) {
				objs.remove(currencyID);
				if (date != plyItems.get(currencyID)) {
					if(Log.loggingDebug)Log.debug("loadCurrenciesPrefabData: id=" + currencyID + " newer date");

					props.put("i" + c + "date", date);
					props.put("i" + c + "id", currencyID);
					props.put("i" + c + "name", prefabData.get("i" + i + "name"));
					props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
					props.put("i" + c + "desc", prefabData.get("i" + i + "desc"));
					props.put("i" + c + "max", prefabData.get("i" + i + "max"));
					props.put("i" + c + "group", prefabData.get("i" + i + "group"));
					props.put("i" + c + "pos", prefabData.get("i" + i + "pos"));
					props.put("i" + c + "convId", prefabData.get("i" + i + "convId"));
					props.put("i" + c + "convAmo", prefabData.get("i" + i + "convAmo"));
					c++;
				}
			} else {
				if(Log.loggingDebug)Log.debug("loadCurrenciesPrefabData: id=" + currencyID + " new ");
				props.put("i" + c + "date", date);
				props.put("i" + c + "id", currencyID);
				props.put("i" + c + "name", prefabData.get("i" + i + "name"));
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				props.put("i" + c + "desc", prefabData.get("i" + i + "desc"));
				props.put("i" + c + "max", prefabData.get("i" + i + "max"));
				props.put("i" + c + "group", prefabData.get("i" + i + "group"));
				props.put("i" + c + "pos", prefabData.get("i" + i + "pos"));
				props.put("i" + c + "convId", prefabData.get("i" + i + "convId"));
				props.put("i" + c + "convAmo", prefabData.get("i" + i + "convAmo"));
				c++;
			}
			if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
				props.put("all", false);
				props.put("num", c);
				return props;
			}
		}
		if (objs.size() > 0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove", sj.toString());
		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadCurrencyPrefabData: end");
		return props;

	}
	
	
	/**
	 * Function to Load Item Set Prefab Data and Store it
	 * @return
	 */
		protected HashMap<String, Serializable> loadItemSetPrefabData() {
			HashMap<String, Serializable> props = new HashMap<String, Serializable>();
			if (iDB == null)
				iDB = new ItemDatabase(false);
			HashMap<Integer, ItemSetProfile> profile = iDB.loadItemSets();
			int c = 0;
			for (Integer i : profile.keySet()) {
				ItemSetProfile isp = profile.get(i);
				if (Log.loggingDebug)
					Log.debug("loadItemSetPrefabData: id=" + isp.GetId() + " new");
				props.put("i" + c + "date", isp.GetDate());
				props.put("i" + c + "id", isp.GetId());
				props.put("i" + c + "name", isp.GetName());
				StringBuilder sbString = new StringBuilder("");
				for (Integer it : isp.GetItems()) {
					sbString.append(it).append(";");
				}
				String strList = sbString.toString();
				// remove last comma from String if you want
				if (strList.length() > 0)
					strList = strList.substring(0, strList.length() - 1);
				props.put("i" + c + "items", strList);
				int l = 0;
				for (ItemSetLevel isl : isp.GetLevels()) {
					props.put("i" + c + "l" + l + "numParts", isl.GetNumberOfParts());
					props.put("i" + c + "l" + l + "id", isl.GetId());
					int s = 0;
					for (EnchantStat es : isl.GetStats().values()) {
						props.put("i" + c + "l" + l + "s" + s + "n", es.GetStatName());
						props.put("i" + c + "l" + l + "s" + s + "v", es.GetValue());
						props.put("i" + c + "l" + l + "s" + s + "p", (int) (es.GetValuePercentage() * 1000f));
						s++;
					}
					props.put("i" + c + "l" + l + "snum", s);
					
					//Add Effects
					int ef = 0;
					for (Integer effect : isl.GetEffects()) 
					{
						props.put("i" + c + "l" + l + "ef" + ef + "v", effect);
						ef++;
					}
					props.put("i" + c + "l" + l + "efnum", ef);
					
					//Add Abilities
					int ab = 0;
					for (Integer ability : isl.GetAbilities()) 
					{
						props.put("i" + c + "l" + l + "ab" + ab + "v", ability);
						ab++;
					}
					props.put("i" + c + "l" + l + "abnum", ab);
					l++;
				}
				props.put("i" + c + "lnum", l);
				c++;
			}

			props.put("num", c);
			log.debug("loadItemSetPrefabData: end");
			return props;

		}

		/**
		 * New Method to return Item Set Prefab Data to the client
		 * 
		 * @param message
		 * @param clientSocket
		 * @return
		 */
		protected Map<String, Serializable> loadItemSetPrefabData(GetPrefabMessage message, SocketHandler clientSocket) {
			HashMap<String, Serializable> props = new HashMap<String, Serializable>();
			int num = (int) message.getProperties().get("c");
			HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
			for (int i = 0; i < num; i++) {
				plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
			}
			if (Log.loggingDebug)
				log.debug("loadItemSetPrefabData: objs=" + plyItems);

			HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
			HashMap<String, Serializable> prefabData = prefabsData.get("ItemSet");
			int numData = (int) prefabData.get("num");
			int c = 0;
			for (int i = 0; i < numData; i++) {
				long date = (long) prefabData.get("i" + i + "date");
				int id = (int) prefabData.get("i" + i + "id");

				if (plyItems.containsKey(id)) {
					objs.remove(id);
					if (date != plyItems.get(id)) {
						if (Log.loggingDebug)
							Log.debug("loadItemSetPrefabData: id=" + id + " newer date");
						props.put("i" + c + "date", prefabData.get("i" + i + "date"));
						props.put("i" + c + "id", prefabData.get("i" + i + "id"));
						props.put("i" + c + "name", prefabData.get("i" + i + "name"));
						props.put("i" + c + "items", prefabData.get("i" + i + "items"));
						int cLevel = (int) prefabData.get("i" + i + "lnum");
						for (int l = 0; l < cLevel; l++) {
							props.put("i" + c + "l" + l + "numParts", prefabData.get("i" + i + "l" + l + "numParts"));
							props.put("i" + c + "l" + l + "id", prefabData.get("i" + i + "l" + l + "id"));
							int cStat = (int) prefabData.get("i" + i + "l" + l + "snum");
							for (int s = 0; s < cStat; s++) {
								props.put("i" + c + "l" + l + "s" + s + "n", prefabData.get("i" + i + "l" + l + "s" + s + "n"));
								props.put("i" + c + "l" + l + "s" + s + "v", prefabData.get("i" + i + "l" + l + "s" + s + "v"));
								props.put("i" + c + "l" + l + "s" + s + "p", prefabData.get("i" + i + "l" + l + "s" + s + "p"));

							}
							props.put("i" + c + "l" + l + "snum", prefabData.get("i" + i + "l" + l + "snum"));

							//Add effects
							int efnum = (int) prefabData.get("i" + i + "l" + l + "efnum");
							for (int ef = 0; ef < efnum; ef++) 
							{
								props.put("i" + c + "l" + l + "ef" + ef + "v", prefabData.get("i" + i + "l" + l + "ef" + ef + "v"));
							}
							props.put("i" + c + "l" + l + "efnum", prefabData.get("i" + i + "l" + l + "efnum"));		
							//Add Abilities
							int abnum = (int) prefabData.get("i" + i + "l" + l + "abnum");
							for (int ab = 0; ab < abnum; ab++) 
							{
								props.put("i" + c + "l" + l + "ab" + ab + "v", prefabData.get("i" + i + "l" + l + "ab" + ab + "v"));
							}
							props.put("i" + c + "l" + l + "abnum", prefabData.get("i" + i + "l" + l + "abnum"));
						}
						props.put("i" + c + "lnum", prefabData.get("i" + i + "lnum"));
						c++;
					}
				} else {
					if (Log.loggingDebug)
						Log.debug("loadItemSetPrefabData: id=" + id + " new");
					props.put("i" + c + "date", prefabData.get("i" + i + "date"));
					props.put("i" + c + "id", prefabData.get("i" + i + "id"));
					props.put("i" + c + "name", prefabData.get("i" + i + "name"));
					props.put("i" + c + "items", prefabData.get("i" + i + "items"));
					int cLevel = (int) prefabData.get("i" + i + "lnum");
					for (int l = 0; l < cLevel; l++) {
						props.put("i" + c + "l" + l + "numParts", prefabData.get("i" + i + "l" + l + "numParts"));
						props.put("i" + c + "l" + l + "id", prefabData.get("i" + i + "l" + l + "id"));
						int cStat = (int) prefabData.get("i" + i + "l" + l + "snum");
						for (int s = 0; s < cStat; s++) {
							props.put("i" + c + "l" + l + "s" + s + "n", prefabData.get("i" + i + "l" + l + "s" + s + "n"));
							props.put("i" + c + "l" + l + "s" + s + "v", prefabData.get("i" + i + "l" + l + "s" + s + "v"));
							props.put("i" + c + "l" + l + "s" + s + "p", prefabData.get("i" + i + "l" + l + "s" + s + "p"));

						}
						props.put("i" + c + "l" + l + "snum", prefabData.get("i" + i + "l" + l + "snum"));
						//Add effects
						int efnum = (int) prefabData.get("i" + i + "l" + l + "efnum");
						for (int ef = 0; ef < efnum; ef++) 
						{
							props.put("i" + c + "l" + l + "ef" + ef + "v", prefabData.get("i" + i + "l" + l + "ef" + ef + "v"));
						}
						props.put("i" + c + "l" + l + "efnum", prefabData.get("i" + i + "l" + l + "efnum"));
						//Add Abilities
						int abnum = (int) prefabData.get("i" + i + "l" + l + "abnum");
						for (int ab = 0; ab < abnum; ab++) 
						{
							props.put("i" + c + "l" + l + "ab" + ab + "v", prefabData.get("i" + i + "l" + l + "ab" + ab + "v"));
						}
						props.put("i" + c + "l" + l + "abnum", prefabData.get("i" + i + "l" + l + "abnum"));

					}
					props.put("i" + c + "lnum", prefabData.get("i" + i + "lnum"));
					c++;
				}
				if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
					props.put("all", false);
					props.put("num", c);
					return props;
				}
			}
			if (objs.size() > 0) {
				StringJoiner sj = new StringJoiner(";");
				for (Integer s : objs.keySet()) {
					sj.add(s.toString());
				}
				props.put("toRemove", sj.toString());
			}
			props.put("num", c);
			props.put("all", true);
			log.debug("loadItemSetPrefabData: end");
			return props;

		}
		
	
		
	/**
	 * Loading Skill Prefab Data from Database and store it
	 * @return
	 */

	protected HashMap<String, Serializable> loadSkillPrefabData(){
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		if(ctDB == null)
			ctDB = new CombatDatabase(false);
		HashMap<Integer, SkillTemplate> skills = ctDB.loadSkills();
		int c = 0;
		for (Integer i : skills.keySet()) {
			SkillTemplate isp = skills.get(i);
				if(Log.loggingDebug)
				Log.debug("loadSkillPrefabData: id=" + isp.getSkillID()+" new" );
				props.put("i" + c + "date", isp.getDate());
				props.put("i" + c + "id", isp.getSkillID());
				props.put("i" + c + "name", isp.getSkillName());
				props.put("i" + c + "mAsp", isp.getAspect());
				props.put("i" + c + "mAspO", isp.mainAspectOnly());
				props.put("i" + c + "opose", isp.getOppositeAspect());
				props.put("i" + c + "pskill", isp.getParentSkill());
				props.put("i" + c + "pskilllevreq", isp.getParentSkillLevelReq());
				props.put("i" + c + "plylevreq", isp.getPlayerLevelReq());
				props.put("i" + c + "pcost", isp.getSkillPointCost());
				props.put("i" + c + "talent", isp.isTalent());
				props.put("i" + c + "type", isp.getType());
				
				StringBuilder sbString = new StringBuilder("");
				StringBuilder sbString2 = new StringBuilder("");
				for (SkillAbility it : isp.getAbilities()) {
					sbString.append(it.abilityID).append(";");
					sbString2.append(it.skillLevelReq).append(";");
				}
				String strList = sbString.toString();
				String strList2 = sbString2.toString();
				// remove last comma from String if you want
				if (strList.length() > 0)
					strList = strList.substring(0, strList.length() - 1);
				props.put("i" + c + "abilites", strList);

				if (strList2.length() > 0)
					strList2 = strList2.substring(0, strList2.length() - 1);
				props.put("i" + c + "abilitesLev", strList2);
				props.putAll(ctDB.loadIconSkillPrefabData(isp.getSkillID(),c));
				c++;
			}
		props.put("num", c);
		log.debug("loadSkillPrefabData: end");
		return props;
	 
 }
	
	
	/**
	 * New Method to return Skill Prefab Data to the client
	 * @param message
	 * @param clientSocket
	 * @return
	 */

	protected Map<String, Serializable> loadSkillPrefabData(GetPrefabMessage message, SocketHandler clientSocket){
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		  int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if(Log.loggingDebug)
			log.debug("loadSkillPrefabData: objs=" + plyItems );
		
		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
		
		
		
		HashMap<String, Serializable> prefabData = prefabsData.get("Skill");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			long date = (long) prefabData.get("i" + i + "date");
			int id = (int) prefabData.get("i" + i + "id");

			if (plyItems.containsKey(id)) {
				objs.remove(id);
				if (Log.loggingDebug)
					Log.debug("loadSkillPrefabData: id=" + id + " date="+date+" "+plyItems.get(id));
				if (date != plyItems.get(id)) {
					if (Log.loggingDebug)
						Log.debug("loadSkillPrefabData: id=" + id + " newer date "+prefabData.get("i" + i + "abilites") );
					props.put("i" + c + "date", prefabData.get("i" + i + "date"));
					props.put("i" + c + "id", prefabData.get("i" + i + "id"));
					props.put("i" + c + "name", prefabData.get("i" + i + "name"));
					props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
					props.put("i" + c + "mAsp", prefabData.get("i" + i + "mAsp"));
					props.put("i" + c + "mAspO", prefabData.get("i" + i + "mAspO"));
					props.put("i" + c + "opose", prefabData.get("i" + i + "opose"));
					props.put("i" + c + "pskill", prefabData.get("i" + i + "pskill"));
					props.put("i" + c + "pskilllevreq", prefabData.get("i" + i + "pskilllevreq"));
					props.put("i" + c + "plylevreq", prefabData.get("i" + i + "plylevreq"));
					props.put("i" + c + "pcost", prefabData.get("i" + i + "pcost"));
					props.put("i" + c + "talent", prefabData.get("i" + i + "talent"));
					props.put("i" + c + "type", prefabData.get("i" + i + "type"));
					props.put("i" + c + "abilites", prefabData.get("i" + i + "abilites"));
					props.put("i" + c + "abilitesLev", prefabData.get("i" + i + "abilitesLev"));
					c++;
				}
			} else {
				if(Log.loggingDebug)
				Log.debug("loadSkillPrefabData: id=" + id+" new" );
				props.put("i" + c + "date", prefabData.get("i" + i + "date"));
				props.put("i" + c + "id", prefabData.get("i" + i + "id"));
				props.put("i" + c + "name", prefabData.get("i" + i + "name"));
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				props.put("i" + c + "mAsp", prefabData.get("i" + i + "mAsp"));
				props.put("i" + c + "mAspO", prefabData.get("i" + i + "mAspO"));
				props.put("i" + c + "opose", prefabData.get("i" + i + "opose"));
				props.put("i" + c + "pskill", prefabData.get("i" + i + "pskill"));
				props.put("i" + c + "pskilllevreq", prefabData.get("i" + i + "pskilllevreq"));
				props.put("i" + c + "plylevreq", prefabData.get("i" + i + "plylevreq"));
				props.put("i" + c + "pcost", prefabData.get("i" + i + "pcost"));
				props.put("i" + c + "talent", prefabData.get("i" + i + "talent"));
				props.put("i" + c + "type", prefabData.get("i" + i + "type"));
				props.put("i" + c + "abilites", prefabData.get("i" + i + "abilites"));
				props.put("i" + c + "abilitesLev", prefabData.get("i" + i + "abilitesLev"));
				c++;
			}
			
			if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
				props.put("all", false);
				props.put("num", c);
				return props;
			}
		}
		if(objs.size()>0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove",sj.toString());
		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadSkillPrefabData: end");
		return props;
	 
 }

	
	/**
	 * New Method to return Ability Prefab Data to the client
	 * 
	 * @param message
	 * @param clientSocket
	 * @return
	 */

	protected Map<String, Serializable> loadAbilityPrefabData(GetPrefabMessage message, SocketHandler clientSocket) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if (Log.loggingDebug)
			log.debug("loadAbilityPrefabData: objs=" + plyItems);

		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
		HashMap<String, Serializable> prefabData = prefabsData.get("Ability");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			long date = (long) prefabData.get("i" + i + "date");
			int id = (int) prefabData.get("i" + i + "id");

			if (plyItems.containsKey(id)) {
				objs.remove(id);
				if (date != plyItems.get(id)) {
					if (Log.loggingDebug) Log.debug("LoadAbilityPrefabData: id=" + id + " newer date");
					props.put("i" + c + "id", id);
					props.put("i" + c + "name", prefabData.get("i" + i + "name"));
					props.put("i" + c + "tooltip", prefabData.get("i" + i + "tooltip"));
					props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
					props.put("i" + c + "cost", prefabData.get("i" + i + "cost"));
					props.put("i" + c + "costP", prefabData.get("i" + i + "costP"));
					props.put("i" + c + "costProp", prefabData.get("i" + i + "costProp"));
					props.put("i" + c + "pcost", prefabData.get("i" + i + "pcost"));
					props.put("i" + c + "pcostP", prefabData.get("i" + i + "pcostP"));
					props.put("i" + c + "pcostProp", prefabData.get("i" + i + "pcostProp"));
					props.put("i" + c + "globancd", prefabData.get("i" + i + "globancd"));
					props.put("i" + c + "weaponcd", prefabData.get("i" + i + "weaponcd"));
					props.put("i" + c + "cooldT", prefabData.get("i" + i + "cooldT"));
					props.put("i" + c + "cooldL", prefabData.get("i" + i + "cooldL"));
					props.put("i" + c + "reqWeap", prefabData.get("i" + i + "reqWeap"));
					props.put("i" + c + "reqReag", prefabData.get("i" + i + "reqReag"));
					props.put("i" + c + "dist", prefabData.get("i" + i + "dist"));
					props.put("i" + c + "maxRange", prefabData.get("i" + i + "maxRange"));
					props.put("i" + c + "minRange", prefabData.get("i" + i + "minRange"));
					props.put("i" + c + "aoeRadius", prefabData.get("i" + i + "aoeRadius"));
					props.put("i" + c + "castInRun", prefabData.get("i" + i + "castInRun"));
					props.put("i" + c + "targetType", prefabData.get("i" + i + "targetType"));
					props.put("i" + c + "targetSubType", prefabData.get("i" + i + "targetSubType"));
					props.put("i" + c + "aoeType", prefabData.get("i" + i + "aoeType"));
					props.put("i" + c + "aoePrefab", prefabData.get("i" + i + "aoePrefab"));
					props.put("i" + c + "castTime", prefabData.get("i" + i + "castTime"));
					props.put("i" + c + "passive", prefabData.get("i" + i + "passive"));
					props.put("i" + c + "toggle", prefabData.get("i" + i + "toggle"));
					props.put("i" + c + "ic", prefabData.get("i" + i + "ic"));
					props.put("i" + c + "speed", prefabData.get("i" + i + "speed"));
					props.put("i" + c + "date", prefabData.get("i" + i + "date"));
					int punum = (int) prefabData.get("i" + i + "punum");
					for (int t = 0; t < punum; t++) {
						props.put("i" + c + "pu" + t + "t", prefabData.get("i" + i + "pu" + t + "t"));
						props.put("i" + c + "pu" + t + "ef", prefabData.get("i" + i + "pu" + t + "ef"));
						props.put("i" + c + "pu" + t + "ab", prefabData.get("i" + i + "pu" + t + "ab"));
					}
					props.put("i" + c + "punum", punum);

					c++;
				}
			} else {
				if (Log.loggingDebug) Log.debug("LoadAbilityPrefabData: id=" + id + " new");
				props.put("i" + c + "id", id);
				props.put("i" + c + "name", prefabData.get("i" + i + "name"));
				props.put("i" + c + "tooltip", prefabData.get("i" + i + "tooltip"));
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				props.put("i" + c + "cost", prefabData.get("i" + i + "cost"));
				props.put("i" + c + "costP", prefabData.get("i" + i + "costP"));
				props.put("i" + c + "costProp", prefabData.get("i" + i + "costProp"));
				props.put("i" + c + "pcost", prefabData.get("i" + i + "pcost"));
				props.put("i" + c + "pcostP", prefabData.get("i" + i + "pcostP"));
				props.put("i" + c + "pcostProp", prefabData.get("i" + i + "pcostProp"));
				props.put("i" + c + "globancd", prefabData.get("i" + i + "globancd"));
				props.put("i" + c + "weaponcd", prefabData.get("i" + i + "weaponcd"));
				props.put("i" + c + "cooldT", prefabData.get("i" + i + "cooldT"));
				props.put("i" + c + "cooldL", prefabData.get("i" + i + "cooldL"));
				props.put("i" + c + "reqWeap", prefabData.get("i" + i + "reqWeap"));
				props.put("i" + c + "reqReag", prefabData.get("i" + i + "reqReag"));
				props.put("i" + c + "dist", prefabData.get("i" + i + "dist"));
				props.put("i" + c + "maxRange", prefabData.get("i" + i + "maxRange"));
				props.put("i" + c + "minRange", prefabData.get("i" + i + "minRange"));
				props.put("i" + c + "aoeRadius", prefabData.get("i" + i + "aoeRadius"));
				props.put("i" + c + "castInRun", prefabData.get("i" + i + "castInRun"));
				props.put("i" + c + "targetType", prefabData.get("i" + i + "targetType"));
				props.put("i" + c + "targetSubType", prefabData.get("i" + i + "targetSubType"));
				props.put("i" + c + "aoeType", prefabData.get("i" + i + "aoeType"));
				props.put("i" + c + "aoePrefab", prefabData.get("i" + i + "aoePrefab"));
				props.put("i" + c + "castTime", prefabData.get("i" + i + "castTime"));
				props.put("i" + c + "passive", prefabData.get("i" + i + "passive"));
				props.put("i" + c + "toggle", prefabData.get("i" + i + "toggle"));
				props.put("i" + c + "ic", prefabData.get("i" + i + "ic"));
				props.put("i" + c + "speed", prefabData.get("i" + i + "speed"));
				props.put("i" + c + "date", prefabData.get("i" + i + "date"));
				int punum = (int) prefabData.get("i" + i + "punum");
				for (int t = 0; t < punum; t++) {
					props.put("i" + c + "pu" + t + "t", prefabData.get("i" + i + "pu" + t + "t"));
					props.put("i" + c + "pu" + t + "ef", prefabData.get("i" + i + "pu" + t + "ef"));
					props.put("i" + c + "pu" + t + "ab", prefabData.get("i" + i + "pu" + t + "ab"));
				}
				props.put("i" + c + "punum", punum);
				c++;
			}

			if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
				props.put("all", false);
				props.put("num", c);
				return props;
			}
		}

		if (objs.size() > 0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove", sj.toString());
		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadAbilityPrefabData: end");
		return props;
	 
 }
	
	/**
	 * New Method to return Effect Prefad Data to the Client
	 * 
	 * @param message
	 * @param clientSocket
	 * @return
	 */
	protected Map<String, Serializable> loadEffectPrefabData(GetPrefabMessage message, SocketHandler clientSocket) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if (Log.loggingDebug)
			log.debug("loadEffectPrefabData: objs=" + plyItems);

		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
		HashMap<String, Serializable> prefabData = prefabsData.get("Effect");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			long date = (long) prefabData.get("i" + i + "date");
			int id = (int) prefabData.get("i" + i + "id");

			if (plyItems.containsKey(id)) {
				objs.remove(id);
				if (date != plyItems.get(id)) {
					if(Log.loggingDebug)Log.debug("loadEffectsPrefabData: id=" + id + " newer date");
					props.put("i" + c + "id", id);
					props.put("i" + c + "name", prefabData.get("i" + i + "name"));
					props.put("i" + c + "tooltip", prefabData.get("i" + i + "tooltip"));
					props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
					props.put("i" + c + "buff", prefabData.get("i" + i + "buff"));
					props.put("i" + c + "stackL", prefabData.get("i" + i + "stackL"));
					props.put("i" + c + "stackT", prefabData.get("i" + i + "stackT"));
					props.put("i" + c + "aM", prefabData.get("i" + i + "aM"));
					props.put("i" + c + "date", prefabData.get("i" + i + "date"));
					props.put("i" + c + "show",  prefabData.get("i" + i + "show"));
					
					c++;
				}
			} else {
				if(Log.loggingDebug)Log.debug("loadEffectsPrefabData: id=" + id + " new");
				props.put("i" + c + "id", id);
				props.put("i" + c + "name", prefabData.get("i" + i + "name"));
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				props.put("i" + c + "tooltip", prefabData.get("i" + i + "tooltip"));
				props.put("i" + c + "buff", prefabData.get("i" + i + "buff"));
				props.put("i" + c + "stackL", prefabData.get("i" + i + "stackL"));
				props.put("i" + c + "stackT", prefabData.get("i" + i + "stackT"));
				props.put("i" + c + "aM", prefabData.get("i" + i + "aM"));
				props.put("i" + c + "date", prefabData.get("i" + i + "date"));
				props.put("i" + c + "show",  prefabData.get("i" + i + "show"));
				c++;
			}

			if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
				props.put("all", false);
				props.put("num", c);
				return props;
			}
		}

		if (objs.size() > 0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove", sj.toString());
		}
		props.put("num", c);

		props.put("all", true);
		log.debug("loadEffectPrefabData: end");
		return props;

	}
	
	
	/**
	 * New Method to return Building Object Prefab data to the client
	 * 
	 * @param message
	 * @param clientSocket
	 * @return
	 */
	protected Map<String, Serializable> loadBuildingObjectPrefabData(GetPrefabMessage message, SocketHandler clientSocket) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if (Log.loggingDebug)
			log.debug("loadBuildingObjectPrefabData: objs=" + plyItems);

		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
		HashMap<String, Serializable> prefabData = prefabsData.get("BuildingObject");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			long date = (long) prefabData.get("i" + i + "date");
			int id = (int) prefabData.get("i" + i + "id");

			if (plyItems.containsKey(id)) {
				objs.remove(id);
				if (date != plyItems.get(id)) {
					if(Log.loggingDebug)Log.debug("loadBuildObjectPrefabData: id=" + id + " newer date");
					props.put("i" + c + "id", id);
					props.put("i" + c + "name", prefabData.get("i" + i + "name"));
					props.put("i" + c + "skill", prefabData.get("i" + i + "skill"));
					props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
					props.put("i" + c + "skillreqlev", prefabData.get("i" + i + "skillreqlev"));
					props.put("i" + c + "cat", prefabData.get("i" + i + "cat"));
					props.put("i" + c + "objCat", prefabData.get("i" + i + "objCat"));
					props.put("i" + c + "reqWeapon", prefabData.get("i" + i + "reqWeapon"));
					props.put("i" + c + "distreq", prefabData.get("i" + i + "distreq"));
					props.put("i" + c + "taskreqply", prefabData.get("i" + i + "taskreqply"));
					props.put("i" + c + "claimtype", prefabData.get("i" + i + "claimtype"));
					props.put("i" + c + "itemOnly", prefabData.get("i" + i + "itemOnly"));
					props.put("i" + c + "date", prefabData.get("i" + i + "date"));

					props.put("i" + c + "reqitem", prefabData.get("i" + i + "reqitem"));
					props.put("i" + c + "reqitemC", prefabData.get("i" + i + "reqitemC"));
					props.put("i" + c + "upditems", prefabData.get("i" + i + "upditems"));
					props.put("i" + c + "lstage", prefabData.get("i" + i + "lstage"));
					
					props.put("i" + c + "buildSolo", prefabData.get("i" + i + "buildSolo"));
					props.put("i" + c + "fixedTime", prefabData.get("i" + i + "fixedTime"));
					props.put("i" + c + "bTime", prefabData.get("i" + i + "bTime"));
				
					c++;
				}
			} else {
				if(Log.loggingDebug)Log.debug("loadBuildObjectPrefabData: id=" + id + " new");
				props.put("i" + c + "id", id);
				props.put("i" + c + "name", prefabData.get("i" + i + "name"));
				props.put("i" + c + "skill", prefabData.get("i" + i + "skill"));
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				props.put("i" + c + "skillreqlev", prefabData.get("i" + i + "skillreqlev"));
				props.put("i" + c + "cat", prefabData.get("i" + i + "cat"));
				props.put("i" + c + "objCat", prefabData.get("i" + i + "objCat"));
				props.put("i" + c + "reqWeapon", prefabData.get("i" + i + "reqWeapon"));
				props.put("i" + c + "distreq", prefabData.get("i" + i + "distreq"));
				props.put("i" + c + "taskreqply", prefabData.get("i" + i + "taskreqply"));
				props.put("i" + c + "claimtype", prefabData.get("i" + i + "claimtype"));
				props.put("i" + c + "itemOnly", prefabData.get("i" + i + "itemOnly"));
				props.put("i" + c + "date", prefabData.get("i" + i + "date"));

				props.put("i" + c + "reqitem", prefabData.get("i" + i + "reqitem"));
				props.put("i" + c + "reqitemC", prefabData.get("i" + i + "reqitemC"));
				props.put("i" + c + "upditems", prefabData.get("i" + i + "upditems"));
				props.put("i" + c + "lstage", prefabData.get("i" + i + "lstage"));
				
				props.put("i" + c + "buildSolo", prefabData.get("i" + i + "buildSolo"));
				props.put("i" + c + "fixedTime", prefabData.get("i" + i + "fixedTime"));
				props.put("i" + c + "bTime", prefabData.get("i" + i + "bTime"));
				c++;
			}

			if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
				props.put("all", false);
				props.put("num", c);
				return props;
			}
		}

		if (objs.size() > 0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove", sj.toString());
		}
		props.put("num", c);

		props.put("all", true);
		log.debug("loadBuildingObjectPrefabData: end");
		return props;

	}
	
	
	protected Map<String, Serializable> loadResourceNodePrefabData(GetPrefabMessage message, SocketHandler clientSocket) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		
		int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if (Log.loggingDebug)
			log.debug("loadResourceNodePrefabData: objs=" + plyItems);

		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
		
		HashMap<String, Serializable> prefabData = prefabsData.get("ResourceNode");
		int c = 0;
		int numData = (int) prefabData.get("num");
		if(Log.loggingDebug)log.debug("resource node profile num="+num);
		for (int i = 0; i < numData; i++) {
			int id = (int)prefabData.get("p" + i );
			if(Log.loggingDebug)log.debug("resource node profile "+id);
			if (prefabData.containsKey("p" + id + "_date")) {
				objs.remove(id);
				long date = (long) prefabData.get("p" + id + "_date");
				if (plyItems.containsKey(id)) {
					if (date != plyItems.get(id)) {
						int spnum = (int) prefabData.get("p" + id + "_num");
						for (int settingId = 0; settingId < spnum; settingId++) {

							props.put("p" + c + "pid", id);
							props.put("p" + c + "set", settingId);
							props.put("p" + c + "iconc2", prefabData.get("p" + id + "_" + settingId + "iconc2"));
							props.put("p" + c + "iconc", prefabData.get("p" + id + "_" + settingId + "iconc"));
							props.put("p" + c + "icons2", prefabData.get("p" + id + "_" + settingId + "icons2"));
							props.put("p" + c + "icons", prefabData.get("p" + id + "_" + settingId + "icons"));
							props.put("p" + c + "date", date);
							c++;
						}
					}
				} else {
					int spnum = (int) prefabData.get("p" + id + "_num");
					for (int settingId = 0; settingId < spnum; settingId++) {

						props.put("p" + c + "pid", id);
						props.put("p" + c + "set", settingId);
						props.put("p" + c + "iconc2", prefabData.get("p" + id + "_" + settingId + "iconc2"));
						props.put("p" + c + "iconc", prefabData.get("p" + id + "_" + settingId + "iconc"));
						props.put("p" + c + "icons2", prefabData.get("p" + id + "_" + settingId + "icons2"));
						props.put("p" + c + "icons", prefabData.get("p" + id + "_" + settingId + "icons"));
						props.put("p" + c + "date", date);
						c++;
					}
				}
			}else {
				log.error("resource node profile not found "+id);
			}
			if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
				props.put("all", false);
				props.put("num", c);
				return props;
			}

		}
		
		if (objs.size() > 0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove", sj.toString());
		}
		props.put("num", c);
		props.put("all", true);

		
		log.debug("loadResourceNodePrefabData: end");
		return props;
	}
	
	
	
	protected Map<String, Serializable> loadQuestPrefabData(GetPrefabMessage message, SocketHandler clientSocket) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if (Log.loggingDebug)
			log.debug("loadQuestPrefabData: objs=" + plyItems);

		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
		HashMap<String, Serializable> prefabData = prefabsData.get("Quest");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			long date = (long) prefabData.get("i" + i + "date");
			int id = (int) prefabData.get("i" + i + "id");

			if (plyItems.containsKey(id)) {
				objs.remove(id);
				if (date != plyItems.get(id)) {
					if (Log.loggingDebug)
						Log.debug("loadQuestPrefabData: id=" + id + " newer date");
					props.put("i" + c + "id", id);
					props.put("i" + c + "name", prefabData.get("i" + i + "name"));
					// props.put("i" + c + "faction", prefabData.get("i" + i + "faction"));
					// props.put("i" + c + "repeatable", prefabData.get("i" + i + "repeatable"));
					props.put("i" + c + "numGrades", prefabData.get("i" + i + "numGrades"));
					props.put("i" + c + "description", prefabData.get("i" + i + "description"));
					props.put("i" + c + "objectiveText", prefabData.get("i" + i + "objectiveText"));
					props.put("i" + c + "progressText", prefabData.get("i" + i + "progressText"));
					/*
					 * props.put("i" + c + "deliveryItems", prefabData.get("i" + i + "deliveryItems"));
					 * int deliveryItems = (int) prefabData.get("i" + i + "deliveryItems");
					 * for (int k = 0; k < deliveryItems; k++) { 
					 *    props.put("i" + c + "deliveryItems" + k, prefabData.get("i" + i + "deliveryItems" + k));
					 * }
					 */

					// props.put("i" + c + "rewardLevels", prefabData.get("i" + i + "rewardLevels"));
					int rewardLevel = (int) prefabData.get("i" + i + "numGrades");
					for (int k = 0; k < rewardLevel; k++) {

						props.put("i" + c + "completionText" + k, prefabData.get("i" + i + "completionText" + k));
						props.put("i" + c + "experience" + k, prefabData.get("i" + i + "experience" + k));

						props.put("i" + c + "currencies" + k, prefabData.get("i" + i + "currencies" + k));
						int currencies = (int) prefabData.get("i" + i + "currencies" + k);
						for (int j = 0; j < currencies; j++) {
							props.put("i" + c + "currency" + k + "_" + j, prefabData.get("i" + i + "currency" + k + "_" + j));
							props.put("i" + c + "currencyC" + k + "_" + j, prefabData.get("i" + i + "currencyC" + k + "_" + j));
						}
						props.put("i" + c + "factions" + k, prefabData.get("i" + i + "factions" + k));
						int factions = (int) prefabData.get("i" + i + "factions" + k);
						for (int j = 0; j < factions; j++) {
							props.put("i" + c + "faction" + k + "_" + j, prefabData.get("i" + i + "faction" + k + "_" + j));
							props.put("i" + c + "factionR" + k + "_" + j, prefabData.get("i" + i + "factionR" + k + "_" + j));
						}
						props.put("i" + c + "rewards" + k, prefabData.get("i" + i + "rewards" + k));
						int rewards = (int) prefabData.get("i" + i + "rewards" + k);
						for (int j = 0; j < rewards; j++) {
							props.put("i" + c + "reward" + k + "_" + j, prefabData.get("i" + i + "reward" + k + "_" + j));
							props.put("i" + c + "rewardc" + k + "_" + j, prefabData.get("i" + i + "rewardc" + k + "_" + j));
						}
						props.put("i" + c + "rewardsC" + k, prefabData.get("i" + i + "rewardsC" + k));
						int rewardsC = (int) prefabData.get("i" + i + "rewardsC" + k);
						for (int j = 0; j < rewardsC; j++) {
							props.put("i" + c + "rewardC" + k + "_" + j, prefabData.get("i" + i + "rewardC" + k + "_" + j));
							props.put("i" + c + "rewardCc" + k + "_" + j, prefabData.get("i" + i + "rewardCc" + k + "_" + j));
						}
					}

					props.put("i" + c + "objectives", prefabData.get("i" + i + "objectives"));
					int objectives = (int) prefabData.get("i" + i + "objectives");
					for (int j = 0; j < objectives; j++) {
						props.put("i" + c + "objectiveT" + j, prefabData.get("i" + i + "objectiveT" + j));
						props.put("i" + c + "objectiveTe" + j, prefabData.get("i" + i + "objectiveTe" + j));
						props.put("i" + c + "objectiveC" + j, prefabData.get("i" + i + "objectiveC" + j));
					}

					// props.put("i" + c + "questPrereq", prefabData.get("i" + i + "questPrereq"));
					// props.put("i" + c + "questStartedReq", prefabData.get("i" + i + "questStartedReq"));
					props.put("i" + c + "date", prefabData.get("i" + i + "date"));

					c++;
				}
			} else {
				if (Log.loggingDebug)
					Log.debug("loadQuestPrefabData: id=" + id + " new");
				props.put("i" + c + "id", id);
				props.put("i" + c + "name", prefabData.get("i" + i + "name"));
				// props.put("i" + c + "faction", prefabData.get("i" + i + "faction"));
				// props.put("i" + c + "repeatable", prefabData.get("i" + i + "repeatable"));
				props.put("i" + c + "numGrades", prefabData.get("i" + i + "numGrades"));
				props.put("i" + c + "description", prefabData.get("i" + i + "description"));
				props.put("i" + c + "objectiveText", prefabData.get("i" + i + "objectiveText"));
				props.put("i" + c + "progressText", prefabData.get("i" + i + "progressText"));
				/*
				 * props.put("i" + c + "deliveryItems", prefabData.get("i" + i + "deliveryItems")); 
				 * int deliveryItems = (int) prefabData.get("i" + i + "deliveryItems");
				 * for (int k = 0; k < deliveryItems; k++) { 
				 * props.put("i" + c + "deliveryItems" + k, prefabData.get("i" + i + "deliveryItems" + k)); 
				 * }
				 */

				// props.put("i" + c + "rewardLevels", prefabData.get("i" + i + "rewardLevels"));
				int rewardLevel = (int) prefabData.get("i" + i + "numGrades");
				for (int k = 0; k < rewardLevel; k++) {

					props.put("i" + c + "completionText" + k, prefabData.get("i" + i + "completionText" + k));
					props.put("i" + c + "experience" + k, prefabData.get("i" + i + "experience" + k));

					props.put("i" + c + "currencies" + k, prefabData.get("i" + i + "currencies" + k));
					int currencies = (int) prefabData.get("i" + i + "currencies" + k);
					for (int j = 0; j < currencies; j++) {
						props.put("i" + c + "currency" + k + "_" + j, prefabData.get("i" + i + "currency" + k + "_" + j));
						props.put("i" + c + "currencyC" + k + "_" + j, prefabData.get("i" + i + "currencyC" + k + "_" + j));
					}
					props.put("i" + c + "factions" + k, prefabData.get("i" + i + "factions" + k));
					int factions = (int) prefabData.get("i" + i + "factions" + k);
					for (int j = 0; j < factions; j++) {
						props.put("i" + c + "faction" + k + "_" + j, prefabData.get("i" + i + "faction" + k + "_" + j));
						props.put("i" + c + "factionR" + k + "_" + j, prefabData.get("i" + i + "factionR" + k + "_" + j));
					}
					props.put("i" + c + "rewards" + k, prefabData.get("i" + i + "rewards" + k));
					int rewards = (int) prefabData.get("i" + i + "rewards" + k);
					for (int j = 0; j < rewards; j++) {
						props.put("i" + c + "reward" + k + "_" + j, prefabData.get("i" + i + "reward" + k + "_" + j));
						props.put("i" + c + "rewardc" + k + "_" + j, prefabData.get("i" + i + "rewardc" + k + "_" + j));
					}
					props.put("i" + c + "rewardsC" + k, prefabData.get("i" + i + "rewardsC" + k));
					int rewardsC = (int) prefabData.get("i" + i + "rewardsC" + k);
					for (int j = 0; j < rewardsC; j++) {
						props.put("i" + c + "rewardC" + k + "_" + j, prefabData.get("i" + i + "rewardC" + k + "_" + j));
						props.put("i" + c + "rewardCc" + k + "_" + j, prefabData.get("i" + i + "rewardCc" + k + "_" + j));
					}
				}

				props.put("i" + c + "objectives", prefabData.get("i" + i + "objectives"));
				int objectives = (int) prefabData.get("i" + i + "objectives");
				for (int j = 0; j < objectives; j++) {
					props.put("i" + c + "objectiveT" + j, prefabData.get("i" + i + "objectiveT" + j));
					props.put("i" + c + "objectiveTe" + j, prefabData.get("i" + i + "objectiveTe" + j));
					props.put("i" + c + "objectiveC" + j, prefabData.get("i" + i + "objectiveC" + j));
				}

				// props.put("i" + c + "questPrereq", prefabData.get("i" + i + "questPrereq"));
				// props.put("i" + c + "questStartedReq", prefabData.get("i" + i + "questStartedReq"));
				props.put("i" + c + "date", prefabData.get("i" + i + "date"));

				c++;
			}

			if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
				props.put("all", false);
				props.put("num", c);
				return props;
			}
		}

		if (objs.size() > 0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove", sj.toString());
		}
		props.put("num", c);

		props.put("all", true);

		log.debug("loadQuestPrefabData: end");
		return props;
	}
	
	
////////////////////////////////ICONS /////////////////////////////////////////////////
	
	
	protected GetIconPrefabResponseMessage handleGetIconPrefabMessage(GetIconPrefabMessage message, SocketHandler clientSocket) {
		GetIconPrefabResponseMessage response = new GetIconPrefabResponseMessage();
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
	//	props.put("status", Boolean.FALSE);
	//	props.put("errorMessage", "character creation is not supported");
		if(Log.loggingDebug)
			log.debug("handleGetIconPrefabMessage: Client=" + clientSocket.getRemoteSocketAddress() + " start");

		if (message.getMsgtype().equals("ItemIcon")) {
			props.putAll(loadItemIconPrefabData(message, clientSocket));
			response.setType("ItemIcon");
		}  else if (message.getMsgtype().equals("CraftingRecipeIcon")) {
			props.putAll(loadCraftingRecipeIconPrefabData(message, clientSocket));
			response.setType("CraftingRecipeIcon");
		} else if (message.getMsgtype().equals("CurrencyIcon")) {
			props.putAll(loadCurrencyIconPrefabData(message, clientSocket));
			response.setType("CurrencyIcon");
		} else if (message.getMsgtype().equals("SkillIcon")) {
			props.putAll(loadSkillIconPrefabData(message, clientSocket));
			response.setType("SkillIcon");
		}else if (message.getMsgtype().equals("AbilityIcon")) {
			props.putAll(loadAbilityIconPrefabData(message, clientSocket));
			response.setType("AbilityIcon");
		}else if (message.getMsgtype().equals("EffectIcon")) {
			props.putAll(loadEffectIconPrefabData(message, clientSocket));
			response.setType("EffectIcon");
		}else if (message.getMsgtype().equals("BuildingObjectIcon")) {
			props.putAll(loadBuildingObjectIconPrefabData(message, clientSocket));
			response.setType("BuildingObjectIcon");
		}else if (message.getMsgtype().equals("ResourceNodeIcon")) {
			props.putAll(loadResourceNodeIconPrefabData(message, clientSocket));
			response.setType("ResourceNodeIcon");
		}else if (message.getMsgtype().equals("GlobalEvents")) {
			props.putAll(loadGlobalEventIconPrefabData(message, clientSocket));
			response.setType("GlobalEventIcons");
		}
		response.setProperties(props);
		if(Log.loggingDebug)
			log.debug("handleGetIconPrefabMessage: Client=" + clientSocket.getRemoteSocketAddress() + " End");

		return response;
	}
	
	/**
	 * New Method to return icons for items
	 * @param message
	 * @param clientSocket
	 * @return
	 */
	
	protected Map<String, Serializable> loadItemIconPrefabData(GetIconPrefabMessage message, SocketHandler clientSocket) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		String list = (String) message.getProperties().get("objs");
		if (Log.loggingDebug)
			Log.debug("loadItemIconPrefabData list " + list);
		if (list.length() > 0)
			list = list.substring(0, list.length() - 1);
		ArrayList<Integer> ids = new ArrayList<Integer>();
		String[] sids = list.split(";");
		for (String s : sids) {
			if (s.length() > 0)
				ids.add(Integer.parseInt(s));
		}
		HashMap<String, Serializable> prefabData = prefabsData.get("Item");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			int id = (int) prefabData.get("i" + i + "id");
			if (ids.contains(id)) {
				props.put("i" + c + "id", id);
				props.put("i" + c + "icon2", prefabData.get("i" + i + "icon2"));
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				c++;
			}

		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadItemIconPrefabData: end");
		return props;

	}
	
	
	/**
	 * New Method to return icons for Cleafting Recipe
	 * 
	 * @param message
	 * @param clientSocket
	 * @return
	 */
	protected Map<String, Serializable> loadCraftingRecipeIconPrefabData(GetIconPrefabMessage message, SocketHandler clientSocket) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		String list = (String) message.getProperties().get("objs");
		if (Log.loggingDebug)
			Log.debug("loadCraftingRecipeIconPrefabData list " + list);
		if (list.length() > 0)
			list = list.substring(0, list.length() - 1);
		ArrayList<Integer> ids = new ArrayList<Integer>();
		String[] sids = list.split(";");
		for (String s : sids) {
			if (s.length() > 0)
				ids.add(Integer.parseInt(s));
		}
		HashMap<String, Serializable> prefabData = prefabsData.get("CraftingRecipe");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			int id = (int) prefabData.get("i" + i + "id");
			if (ids.contains(id)) {
				props.put("i" + c + "id", id);
				props.put("i" + c + "icon2", prefabData.get("i" + i + "icon2"));
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				c++;
			}

		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadCraftingRecipeIconPrefabData: end");
		return props;

	}

	
	/**
	 *  New Method to return icons for Currency
	 * @param message
	 * @param clientSocket
	 * @return
	 */
	protected Map<String, Serializable> loadCurrencyIconPrefabData(GetIconPrefabMessage message, SocketHandler clientSocket){
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		String list = (String) message.getProperties().get("objs");
		if(Log.loggingDebug)
			Log.debug("loadCurrencyIconPrefabData list " + list);
		if (list.length() > 0)
			list = list.substring(0, list.length() - 1);
		ArrayList<Integer> ids = new ArrayList<Integer>();
		String[] sids = list.split(";");
		for (String s : sids) {
			if (s.length() > 0)
				ids.add(Integer.parseInt(s));
		}
		HashMap<String, Serializable> prefabData = prefabsData.get("Currency");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			int id = (int) prefabData.get("i" + i + "id");
			if (ids.contains(id)) {
				props.put("i" + c + "id", id);
				props.put("i" + c + "icon2", prefabData.get("i" + i + "icon2"));
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				c++;
			}

		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadCurrencyIconPrefabData: end");
		return props;
	 
 }
	
	
		
	/**
	 *  New Method to return icons for Skills
	 * @param message
	 * @param clientSocket
	 * @return
	 */
	protected Map<String, Serializable> loadSkillIconPrefabData(GetIconPrefabMessage message, SocketHandler clientSocket){
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		String list = (String) message.getProperties().get("objs");
		if(Log.loggingDebug)
			Log.debug("loadSkillIconPrefabData list " + list);
		if (list.length() > 0)
			list = list.substring(0, list.length() - 1);
		ArrayList<Integer> ids = new ArrayList<Integer>();
		String[] sids = list.split(";");
		for (String s : sids) {
			if (s.length() > 0)
				ids.add(Integer.parseInt(s));
		}
		HashMap<String, Serializable> prefabData = prefabsData.get("Skill");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			int id = (int) prefabData.get("i" + i + "id");
			if (ids.contains(id)) {
				props.put("i" + c + "id", id);
				props.put("i" + c + "icon2", prefabData.get("i" + i + "icon2"));
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				c++;
			}

		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadSkillIconPrefabData: end");
		return props;
	 
 }
	
	
	/**
	 *  New Method to return icons for Ability
	 * @param message
	 * @param clientSocket
	 * @return
	 */
	protected Map<String, Serializable> loadAbilityIconPrefabData(GetIconPrefabMessage message, SocketHandler clientSocket){
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		String list = (String) message.getProperties().get("objs");
		if(Log.loggingDebug)
			Log.debug("loadAbilityIconPrefabData list " + list);
		if (list.length() > 0)
			list = list.substring(0, list.length() - 1);
		ArrayList<Integer> ids = new ArrayList<Integer>();
		String[] sids = list.split(";");
		for (String s : sids) {
			if (s.length() > 0)
				ids.add(Integer.parseInt(s));
		}
		HashMap<String, Serializable> prefabData = prefabsData.get("Ability");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			int id = (int) prefabData.get("i" + i + "id");
			if (ids.contains(id)) {
				props.put("i" + c + "id", id);
				props.put("i" + c + "icon2", prefabData.get("i" + i + "icon2"));
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				c++;
			}

		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadAbilityIconPrefabData: end");
		return props;
	 
 }
	
	
	/**
	 *  New Method to return icons for Effect
	 * @param message
	 * @param clientSocket
	 * @return
	 */

	protected Map<String, Serializable> loadEffectIconPrefabData(GetIconPrefabMessage message, SocketHandler clientSocket){
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		String list = (String) message.getProperties().get("objs");
		if(Log.loggingDebug)
				Log.debug("loadEffectIconPrefabData list " + list);
		if (list.length() > 0)
			list = list.substring(0, list.length() - 1);
		ArrayList<Integer> ids = new ArrayList<Integer>();
		String[] sids = list.split(";");
		for (String s : sids) {
			if (s.length() > 0)
				ids.add(Integer.parseInt(s));
		}
		HashMap<String, Serializable> prefabData = prefabsData.get("Effect");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			int id = (int) prefabData.get("i" + i + "id");
			if (ids.contains(id)) {
				props.put("i" + c + "id", id);
				props.put("i" + c + "icon2", prefabData.get("i" + i + "icon2"));
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				c++;
			}

		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadEffectIconPrefabData: end");
		return props;
	 
 }

		
	/**
	 *  New Method to return icons for Building Object
	 * @param message
	 * @param clientSocket
	 * @return
	 */
	
	protected Map<String, Serializable> loadBuildingObjectIconPrefabData(GetIconPrefabMessage message, SocketHandler clientSocket){
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		String list = (String) message.getProperties().get("objs");
		if(Log.loggingDebug)
			Log.debug("loadBuildingObjectIconPrefabData list " + list);
		if (list.length() > 0)
			list = list.substring(0, list.length() - 1);
		ArrayList<Integer> ids = new ArrayList<Integer>();
		String[] sids = list.split(";");
		for (String s : sids) {
			if (s.length() > 0)
				ids.add(Integer.parseInt(s));
		}
		HashMap<String, Serializable> prefabData = prefabsData.get("BuildingObject");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			int id = (int) prefabData.get("i" + i + "id");
			if (ids.contains(id)) {
				props.put("i" + c + "id", id);
				props.put("i" + c + "icon2", prefabData.get("i" + i + "icon2"));
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				c++;
			}

		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadBuildingObjectIconPrefabData: end");
		return props;
	}
	
	
	protected Map<String, Serializable> loadResourceNodeIconPrefabData(GetIconPrefabMessage message, SocketHandler clientSocket) {
		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
		String list = (String) message.getProperties().get("objs");
		if (Log.loggingDebug)
			Log.debug("loadResourceNodeIconPrefabData list " + list);
		if (list.length() > 0)
			list = list.substring(0, list.length() - 1);
		ArrayList<Integer> ids = new ArrayList<Integer>();
		String[] sids = list.split(";");
		HashMap<String, Serializable> prefabData = prefabsData.get("ResourceNode");
		int c = 0;
		for (String s : sids) {
			if (s.length() > 0) {
				String[] sids2 = s.split("\\|");
				if(Log.loggingDebug)	log.debug("loadResourceNodeIconPrefabData: "+Arrays.toString(sids2));
				int profilId = Integer.parseInt(sids2[0]);
				int settingId = Integer.parseInt(sids2[1]);
				if (prefabData.containsKey("p" + profilId + "_" + settingId + "iconc")) {
					props.put("p" + c + "pid", profilId);
					props.put("p" + c + "set", settingId);
					props.put("p" + c + "iconc2", prefabData.get("p" + profilId + "_" + settingId + "iconc2"));
					props.put("p" + c + "iconc", prefabData.get("p" + profilId + "_" + settingId + "iconc"));
					props.put("p" + c + "icons2", prefabData.get("p" + profilId + "_" + settingId + "icons2"));
					props.put("p" + c + "icons", prefabData.get("p" + profilId + "_" + settingId + "icons"));
					props.put("p" + c + "date", prefabData.get("p" + profilId + "_date"));
					c++;
				}
			}
		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadResourceNodeIconPrefabData: end");
		return props;
	}
	
	protected Map<String, Serializable> loadGlobalEventIconPrefabData(GetIconPrefabMessage message, SocketHandler clientSocket) {
		int num = (int) message.getProperties().get("c");
		HashMap<Integer, Long> plyItems = new HashMap<Integer, Long>();
		for (int i = 0; i < num; i++) {
			plyItems.put((int) message.getProperties().get("iId" + i), (long) message.getProperties().get("iDate" + i));
		}
		if (Log.loggingDebug)
			log.debug("loadGlobalEventIconPrefabData: objs=" + plyItems);

		HashMap<String, Serializable> props = new HashMap<String, Serializable>();
	
		HashMap<Integer, Long> objs = new HashMap<Integer, Long>(plyItems);
		HashMap<String, Serializable> prefabData = prefabsData.get("GlobalEvents");
		int numData = (int) prefabData.get("num");
		int c = 0;
		for (int i = 0; i < numData; i++) {
			long date = (long) prefabData.get("i" + i + "date");
			int eventId = (int) prefabData.get("i" + i + "id");
			if (plyItems.containsKey(eventId)) {
				objs.remove(eventId);
				if (date != plyItems.get(eventId)) {
					if(Log.loggingDebug)Log.debug("loadGlobalEventIconPrefabData: id=" + eventId + " newer date");

					props.put("i" + c + "date", date);
					props.put("i" + c + "id", eventId);
					props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
					props.put("i" + c + "icon2", prefabData.get("i" + i + "icon2"));
					c++;
				}
			} else {
				if(Log.loggingDebug)	Log.debug("loadGlobalEventIconPrefabData: id=" + eventId + " new ");
				props.put("i" + c + "date", date);
				props.put("i" + c + "id", eventId);
				props.put("i" + c + "icon", prefabData.get("i" + i + "icon"));
				props.put("i" + c + "icon2", prefabData.get("i" + i + "icon2"));
							c++;
			}
			if (c == PrefabPlugin.PREFAB_LOAD_ELEMENT_LIMIT) {
				props.put("all", false);
				props.put("num", c);
				return props;
			}
		}
		if (objs.size() > 0) {
			StringJoiner sj = new StringJoiner(";");
			for (Integer s : objs.keySet()) {
				sj.add(s.toString());
			}
			props.put("toRemove", sj.toString());
		}
		props.put("num", c);
		props.put("all", true);
		log.debug("loadGlobalEventIconPrefabData: end");
		return props;
	}
	
	/**
	 * Use the information in the plugin status's info field to tell the client what
	 * the external address is for the proxy plugin.
	 * 
	 * @param props
	 * @param proxy
	 * @return
	 */
	protected boolean setProxyProperties(Map<String, Serializable> props, PluginStatus proxy) {
		if (proxy == null)
			return false;

		Map<String, String> info = Engine.makeMapOfString(proxy.info);
		String hostname = info.get("host");
		int port;
		try {
			hostname = info.get("host");
			port = Integer.parseInt(info.get("port"));
		} catch (Exception e) {
			Log.exception("setProxyProperties: proxy " + proxy.plugin_name + " invalid port number: " + info.get("port"), e);
			return false;
		}

		props.put("proxyHostname", hostname);
		props.put("proxyPort", port);

		if (Log.loggingDebug) {
			Log.debug("PrefabPlugin: assigned proxy " + proxy.plugin_name + " host=" + proxy.host_name + " port=" + port);
		}

		return true;
	}
	
	private static ExecutorService threadPool = Executors.newCachedThreadPool(new NamedThreadFactory("PrefabConnection", true));

	private TcpServer prefabListener = null;

	private Integer tcpPort = null;

	private Object characterCreateLock = new Object();

	/**
	 * The master server sends us the account id in a secure manner by default. If
	 * clients bypass the master server, set this to false. This allows people to
	 * masquerade as others and should only be used for development purposes.
	 */
	public static boolean SecureToken = true;
	public static long TokenValidTime = 60000L;

	/**
	 * If WorldId is set, the PrefabPlugin only accepts master tokens that specify
	 * the correct world id.
	 */
	public static Integer WorldId = null;

	private static CharacterGenerator characterGenerator = new CharacterGenerator();
}
