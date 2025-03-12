package atavism.agis.objects;

import java.io.Serializable;
import java.util.*;

import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.AgisInventoryPlugin;
import atavism.agis.plugins.CombatClient;
import atavism.agis.util.EventMessageHelper;
import atavism.msgsys.Message;
import atavism.msgsys.MessageDispatch;
import atavism.msgsys.SubjectFilter;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.OID;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

public class SocketResetTask implements Runnable, MessageDispatch {

	protected AgisItem socketItem;
//	protected AgisItem item;
	OID playerOid;
	float creationTime = 0;
	float chance = 100f;
	boolean interrupted = false;
	transient Long sub = null;
//	boolean failedClear = false;
	int currency = 1;
	long amountCurrency =0;
	public SocketResetTask(OID oid, AgisItem socketItem,  float creationTime, float chance, int currency,long amountCurrency) {
		Log.error("SOCKETRESET: Create task for player: " + oid + " socketItem:" + socketItem + " creationTime:" + creationTime + " chance:" + chance);
		this.socketItem = socketItem;
	//	this.item = item;
		playerOid = oid;
		this.creationTime = creationTime;
		this.chance = chance;
	//	this.failedClear = failedClear;
		this.amountCurrency = amountCurrency;
		this.currency = currency;
		if (creationTime > 0) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "SocketResetMsg");
			props.put("PluginMessageType", "SocketResetStarted");
			props.put("creationTime", creationTime);
			CoordinatedEffect cE = new CoordinatedEffect("SocketResetStartEffect");
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			cE.invoke(playerOid, playerOid);
			TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(playerMsg);
			setupMessageSubscription();
			EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "currentTask", AgisInventoryPlugin.TASK_SOCKET_RESET);
		}
	}

	void setupMessageSubscription() {
		// Set up a message hook for when the caster is moving so we can interrupt
		SubjectFilter filter = new SubjectFilter(playerOid);
		filter.addType(CombatClient.MSG_TYPE_INTERRUPT_ABILITY);
		sub = Engine.getAgent().createSubscription(filter, this);
		Log.debug("SOCKETRESET: subscribed to interrupt message");
	}

	public void handleMessage(Message msg, int flags) {
		if (msg instanceof CombatClient.interruptAbilityMessage) {
			processInterrupt();
		} else {
			Log.error("Socketing: unknown msg: " + msg);
		}
	}

	private void processInterrupt() {
		Log.error("SOCKETRESET: got interrupt");
		interrupted = true;
		if (sub != null)
			Engine.getAgent().removeSubscription(sub);
		Log.error("SOCKETRESET TASK: processing interrupt ");
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "SocketResetMsg");
		props.put("PluginMessageType", "SocketResetInterrupted");
		TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(playerMsg);
		EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "currentTask", "");
		EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.SOCKET_RESET_INTERRUPT, 0, "");
		CoordinatedEffect cE = new CoordinatedEffect("SocketResetInterruptEffect");
		cE.sendSourceOid(true);
		cE.sendTargetOid(true);
		cE.invoke(playerOid, playerOid);}

	public void PlayCoordinatedEffect(String coordEffect) {
		CoordinatedEffect cE = new CoordinatedEffect(coordEffect);
		cE.sendSourceOid(true);
		cE.sendTargetOid(true);
		cE.putArgument("length", (float) creationTime);
		cE.invoke(playerOid, playerOid);
	}

	@Override
	public void run() {
		Log.debug("SOCKETRESET: running socketing task ");
		if (sub != null)
			Engine.getAgent().removeSubscription(sub);

		if (interrupted)
			return;

		// Reset currentTask
		String currentTask = (String) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "currentTask");
		if (currentTask.equals(AgisInventoryPlugin.TASK_SOCKET_RESET)) {
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
	//	OID itemOid;
	
		// Get price from Player
		AgisInventoryClient.alterCurrency(playerOid, currency, amountCurrency);
		Log.debug("SOCKETRESET: chance:"+(chance )+" roll"+roll);	
		if (chance > roll) {
		//	Log.debug("SOCKETRESET: socketing task keys :"+keys);
		HashMap<Integer, SocketInfo> itemSockets = (HashMap<Integer, SocketInfo>) socketItem.getProperty("sockets");
		Set<Integer> keys = itemSockets.keySet();
			Log.debug("SOCKETRESET: socketing task keys :"+keys);
			for (Integer i : keys) {
				Log.debug("SOCKETRESET: running socketing task socket :"+i + "  type:"+itemSockets.get(i).GetType());
			//	if (itemSockets.get(i).GetType().equals(socketType) && itemSockets.get(i).GetItemOid() == null) {
					itemSockets.get(i).SetItemOid(null);
					
					// WorldManagerClient.sendObjChatMsg(playerOid, 2, "Success ");
					EventMessageHelper.SendGeneralEvent(playerOid, EventMessageHelper.SOCKET_RESET_SUCCESS, 0, "");
					CoordinatedEffect cE = new CoordinatedEffect("SocketResetSuccessEffect");
					cE.sendSourceOid(true);
					cE.sendTargetOid(true);
					cE.invoke(playerOid, playerOid);
					socketItem.setProperty("sockets", itemSockets);
					ObjectManagerClient.saveObject(socketItem.getOid());
					//break;
				}
			//}

		} else {
		//	Log.error("SOCKETING: running socketing task socket: failed itemOid:"+item.getOid()+" failedClear:"+failedClear);
		/*	if (failedClear) {
				
				for (Integer i : keys) {
					itemSockets.get(i).SetItemOid(OID.fromLong(0l));
				}
				
				socketItem.setProperty("sockets", itemSockets);
				ObjectManagerClient.saveObject(socketItem.getOid());

			}*/
			EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.SOCKET_RESET_FAILED, 0, "");
			EventMessageHelper.SendGeneralEvent(playerOid, EventMessageHelper.SOCKET_RESET_FAILED, 0, "");
			CoordinatedEffect cE = new CoordinatedEffect("SocketResetFailEffect");
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			cE.invoke(playerOid, playerOid);

		}
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "SocketResetMsg");
		props.put("PluginMessageType", "SocketResetCompleted");
		TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(playerMsg);
		AgisInventoryClient.SendInventoryUpdateMessage invUpdateMsg = new AgisInventoryClient.SendInventoryUpdateMessage(playerOid);
		Engine.getAgent().sendBroadcast(invUpdateMsg);
	}
}
