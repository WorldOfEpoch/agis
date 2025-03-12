package atavism.agis.objects;

import java.io.Serializable;
import java.util.*;

import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.AgisInventoryPlugin;
import atavism.agis.plugins.CombatClient;
import atavism.agis.util.EventMessageHelper;
import atavism.msgsys.Message;
import atavism.msgsys.MessageCallback;
import atavism.msgsys.SubjectFilter;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.plugins.InventoryClient;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

public class SocketingTask implements Runnable, MessageCallback {

	protected AgisItem socketItem;
	protected AgisItem item;
	OID playerOid;
	float creationTime = 0;
	float chance = 100f;
	boolean interrupted = false;
	transient Long sub = null;
	boolean failedClear = false;
	int currency = 1;
	long amountCurrency =0;
	public SocketingTask(OID oid, AgisItem socketItem, AgisItem item, float creationTime, float chance, boolean failedClear,int currency,long amountCurrency) {
		Log.debug("SOCKETING: Create task for player: " + oid + " socketItem:" + socketItem + " item:" + item + " creationTime:" + creationTime + " chance:" + chance);
		this.socketItem = socketItem;
		this.item = item;
		playerOid = oid;
		this.creationTime = creationTime;
		this.chance = chance;
		this.failedClear = failedClear;
		this.amountCurrency = amountCurrency;
		this.currency = currency;
		if (creationTime > 0) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "SocketingMsg");
			props.put("PluginMessageType", "SocketingStarted");
			props.put("creationTime", creationTime);
			CoordinatedEffect cE = new CoordinatedEffect("SocketingStartEffect");
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			cE.invoke(playerOid, playerOid);
			TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(playerMsg);
			setupMessageSubscription();
			EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "currentTask", AgisInventoryPlugin.TASK_SOCKETING);
		}
	}

	void setupMessageSubscription() {
		// Set up a message hook for when the caster is moving so we can interrupt
		SubjectFilter filter = new SubjectFilter(playerOid);
		filter.addType(CombatClient.MSG_TYPE_INTERRUPT_ABILITY);
		sub = Engine.getAgent().createSubscription(filter, this);
		Log.debug("SOCKETING: subscribed to interrupt message");
	}

	public void handleMessage(Message msg, int flags) {
		if (msg instanceof CombatClient.interruptAbilityMessage) {
			processInterrupt();
		} else {
			Log.error("Socketing: unknown msg: " + msg);
		}
	}

	private void processInterrupt() {
		Log.debug("SOCKETING: got interrupt");
		interrupted = true;
		if (sub != null)
			Engine.getAgent().removeSubscription(sub);
		Log.debug("SOCKETING TASK: processing interrupt ");
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "SocketingMsg");
		props.put("PluginMessageType", "SocketingInterrupted");
		TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(playerMsg);
		EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "currentTask", "");
		EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.SOCKETING_INTERRUPT, 0, "");
		CoordinatedEffect cE = new CoordinatedEffect("SocketingInterruptEffect");
		cE.sendSourceOid(true);
		cE.sendTargetOid(true);
		cE.invoke(playerOid, playerOid);
	}

	public void PlayCoordinatedEffect(String coordEffect) {
		CoordinatedEffect cE = new CoordinatedEffect(coordEffect);
		cE.sendSourceOid(true);
		cE.sendTargetOid(true);
		cE.putArgument("length", (float) creationTime);
		cE.invoke(playerOid, playerOid);
	}

	@Override
	public void run() {
		Log.debug("SOCKETING: running socketing task ");
		if (sub != null)
			Engine.getAgent().removeSubscription(sub);

		if (interrupted)
			return;

		// Reset currentTask
		String currentTask = (String) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "currentTask");
		if (currentTask.equals(AgisInventoryPlugin.TASK_SOCKETING)) {
			EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "currentTask", "");
		}

		// Dead check
		boolean dead = (Boolean) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, CombatInfo.COMBAT_PROP_DEADSTATE);
		if (dead) {
			return;
		}

		Random random = new Random();
		float roll = random.nextInt(10000) / 100f;
		// Remo
		OID itemOid;
		if (item.getStackSize() > 1) {
			Log.debug("SOCKETING: item stack altering ");
			item.alterStackSize(playerOid, -1);
			itemOid = AgisInventoryClient.generateItem(playerOid, item.getTemplateID(), item.getName(), 1);
			Log.debug("SOCKETING: new item generated itemOid:" + itemOid);
			InventoryClient.removeItem(playerOid, itemOid);
			ObjectManagerClient.saveObject(itemOid);
			Engine.getPersistenceManager().setDirty(item);
		
		} else {
			InventoryClient.removeItem(playerOid, item.getOid());
			itemOid = item.getOid();
		}
		// Get price from Player
		AgisInventoryClient.alterCurrency(playerOid, currency, amountCurrency);

		String socketType = (String) item.getProperty("SocketType");
		HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) socketItem.getProperty("sockets");
		Set<Integer> keys = itemSockets.keySet();
		Log.debug("SOCKETING: chance:"+(chance )+" roll"+roll);	
		if (chance > roll) {
			Log.debug("SOCKETING: socketing task keys :"+keys);
			for (Integer i : keys) {
				Log.debug("SOCKETING: running socketing task socket :"+i + " socketType:"+socketType+" type:"+itemSockets.get(i).GetType());
				if (itemSockets.get(i).GetType().equals(socketType) && itemSockets.get(i).GetItemOid() == null) {
					itemSockets.get(i).SetItemOid(itemOid);
					Log.debug("SOCKETING: running socketing task socket:"+i+" itemOid:"+item.getOid());
					
					// WorldManagerClient.sendObjChatMsg(playerOid, 2, "Success ");
					EventMessageHelper.SendGeneralEvent(playerOid, EventMessageHelper.SOCKETING_SUCCESS, 0, "");
					CoordinatedEffect cE = new CoordinatedEffect("SocketingSuccessEffect");
					cE.sendSourceOid(true);
					cE.sendTargetOid(true);
					cE.invoke(playerOid, playerOid);
					socketItem.setProperty("sockets", itemSockets);
					ObjectManagerClient.saveObject(socketItem.getOid());
					Engine.getPersistenceManager().setDirty(socketItem);
					break;
				}
			}

		} else {
			Log.debug("SOCKETING: running socketing task socket: failed itemOid:"+item.getOid()+" failedClear:"+failedClear);
			if (failedClear) {
				
				for (Integer i : keys) {
					itemSockets.get(i).SetItemOid(OID.fromLong(0l));
				}
				
				socketItem.setProperty("sockets", itemSockets);
				ObjectManagerClient.saveObject(socketItem.getOid());

			}
			EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.SOCKETING_FAILED, 0, "");
			EventMessageHelper.SendGeneralEvent(playerOid, EventMessageHelper.SOCKETING_FAILED, 0, "");
			CoordinatedEffect cE = new CoordinatedEffect("SocketingFailEffect");
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			cE.invoke(playerOid, playerOid);

		}
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "SocketingMsg");
		props.put("PluginMessageType", "SocketingCompleted");
		TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(playerMsg);
		AgisInventoryClient.SendInventoryUpdateMessage invUpdateMsg = new AgisInventoryClient.SendInventoryUpdateMessage(playerOid);
		Engine.getAgent().sendBroadcast(invUpdateMsg);
	}
}
