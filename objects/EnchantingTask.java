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

public class EnchantingTask implements Runnable, MessageDispatch {

	protected AgisItem item;
	OID playerOid;
	float chance = 100f;
	float vipChance = 0f;
	boolean interrupted = false;
	transient Long sub = null;
	int currency = 1;
	long amountCurrency =0;
	float creationTime =0;
	EnchantProfile ep;
	QualityInfo qi;
	public EnchantingTask(OID oid, AgisItem item, EnchantProfile ep, float creationTime, int currency, long amountCurrency, QualityInfo qi,float vipChance) {
		Log.debug("EnchantingTask: Create task for player: " + oid + " item:" + item + " creationTime:" + creationTime + " EnchantProfile:" + ep+" QualityInfo="+qi+" vipChance="+vipChance+" currency="+currency+" amountCurrency="+amountCurrency);
		this.ep = ep;
		this.item = item;
		playerOid = oid;
		this.amountCurrency = amountCurrency;
		this.currency = currency;
		this.creationTime = creationTime;
		this.qi = qi;
		this.vipChance=vipChance;
		if (creationTime > 0) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "EnchantingMsg");
			props.put("PluginMessageType", "EnchantingStarted");
			props.put("creationTime", creationTime);
			CoordinatedEffect cE = new CoordinatedEffect("EnchantingStartEffect");
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			cE.invoke(playerOid, playerOid);
			TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(playerMsg);
			setupMessageSubscription();
			EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "currentTask", AgisInventoryPlugin.TASK_ENCHANTING);
		}
	}

	void setupMessageSubscription() {
		// Set up a message hook for when the caster is moving so we can interrupt
		SubjectFilter filter = new SubjectFilter(playerOid);
		filter.addType(CombatClient.MSG_TYPE_INTERRUPT_ABILITY);
		sub = Engine.getAgent().createSubscription(filter, this);
		Log.debug("EnchantingTask: subscribed to interrupt message");
	}

	public void handleMessage(Message msg, int flags) {
		if (msg instanceof CombatClient.interruptAbilityMessage) {
			processInterrupt();
		} else {
			Log.debug("EnchantingTask: unknown msg: " + msg);
		}
	}

	private void processInterrupt() {
		Log.debug("EnchantingTask: got interrupt");
		interrupted = true;
		if (sub != null)
			Engine.getAgent().removeSubscription(sub);
		Log.debug("EnchantingTask TASK: processing interrupt ");
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "EnchantingMsg");
		props.put("PluginMessageType", "EnchantingInterrupted");
		TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(playerMsg);
		EnginePlugin.setObjectPropertyNoResponse(playerOid, WorldManagerClient.NAMESPACE, "currentTask", "");
		EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.ENCHANTING_INTERRUPT, 0, "");
		CoordinatedEffect cE = new CoordinatedEffect("EnchantingInterruptEffect");
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
		Log.debug("EnchantingTask: running enchanting task ");
		if (sub != null)
			Engine.getAgent().removeSubscription(sub);

		if (interrupted)
			return;

		// Reset currentTask
		String currentTask = (String) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "currentTask");
		if (currentTask.equals(AgisInventoryPlugin.TASK_ENCHANTING)) {
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
		
		// Get price from Player
		AgisInventoryClient.alterCurrency(playerOid, currency, amountCurrency);
		int enchantLevel = (int) item.getProperty("enchantLevel");
		Log.debug("EnchantingTask: enchantLevel:"+enchantLevel);
		chance = ep.GetLevels().get(enchantLevel+1).GetChance();
		Log.debug("EnchantingTask: chance:"+(chance * qi.chance + chance * qi.chance * vipChance )+" roll"+roll);	
		if ((chance * qi.chance + chance * qi.chance * vipChance / 100D)> roll) {
			//Log.error("EnchantingTask: socketing task keys :"+keys);
			//Log.error("EnchantingTask: running socketing task socket :"+i + " socketType:"+socketType+" type:"+itemSockets.get(i).GetType());
					// WorldManagerClient.sendObjChatMsg(playerOid, 2, "Success ");
				item.setProperty("enchantLevel", enchantLevel + 1);
					EventMessageHelper.SendGeneralEvent(playerOid, EventMessageHelper.ENCHANTING_SUCCESS, 0, "");
					CoordinatedEffect cE = new CoordinatedEffect("EnchantingSuccessEffect");
					cE.sendSourceOid(true);
					cE.sendTargetOid(true);
					cE.invoke(playerOid, playerOid);
					//item.setProperty("sockets", itemSockets);
				//	ObjectManagerClient.saveObject(item.getOid());
				//	Engine.getPersistenceManager().setDirty(item);
					
				//	break;

		} else {
			//Log.error("EnchantingTask: running socketing task socket: failed itemOid:"+item.getOid()+" failedClear:"+failedClear);
//				socketItem.setProperty("sockets", itemSockets);
	//	
			
			if (ep.GetLevels().get(enchantLevel+1).GetLowerTo() > -1) {
				if(ep.GetLevels().get(enchantLevel+1).GetLowerTo() < enchantLevel) {
					item.setProperty("enchantLevel", ep.GetLevels().get(enchantLevel+1).GetLowerTo());
					Log.debug("EnchantingTask: failed lower to :"+enchantLevel);
					
				}else {
					if ((enchantLevel - ep.GetLevels().get(enchantLevel+1).GetLowerBy()) < 0)
						item.setProperty("enchantLevel", 0);
					else
						item.setProperty("enchantLevel", enchantLevel - ep.GetLevels().get(enchantLevel+1).GetLowerBy());
				}
			}else {
				if ((enchantLevel - ep.GetLevels().get(enchantLevel+1).GetLowerBy()) < 0)
					item.setProperty("enchantLevel", 0);
				else
					item.setProperty("enchantLevel", enchantLevel - ep.GetLevels().get(enchantLevel+1).GetLowerBy());
			}
		//	ObjectManagerClient.saveObject(item.getOid());

			EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.ENCHANTING_FAILED, 0, "");
			EventMessageHelper.SendGeneralEvent(playerOid, EventMessageHelper.ENCHANTING_FAILED, 0, "");
			CoordinatedEffect cE = new CoordinatedEffect("EnchantingFailEffect");
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			cE.invoke(playerOid, playerOid);
		}
		
		HashMap<String, Integer> enchantStats = new HashMap<String, Integer>((HashMap<String, Integer>)item.getProperty("bonusStats"));
		String itemType = (String) item.getProperty("itemType");
		if (itemType.equals("Weapon")) {
			int damage = (Integer) item.getProperty("damage");
	       //	obj.statAddModifier("dmg-base", ai.getOid(), damage, false); // Damage is added on
	      	 //   Log.debug("Equip Item: "+obj.getName()+" dmg-base "+damage +" to equip "+ ai.getName());
	    	int damageMax = (Integer) item.getProperty("damageMax");
	    	if (damageMax < damage) damageMax = damage;
	    //	obj.statAddModifier("dmg-max", ai.getOid(), damageMax, false); // Damage is added on
	    	enchantStats.put("dmg-base", damage);
	    	enchantStats.put("dmg-max", damageMax);
	    	int gearScore = (Integer) item.getProperty("gearScore");
		    enchantStats.put("gearScore", gearScore);
		}
		for (String statName: enchantStats.keySet()) {
			int value = enchantStats.get(statName);
		//	Log.error("LOAD ITEM: item:"+item+" base stat: " + statName + " by: " + value);
		}
		enchantLevel = (int) item.getProperty("enchantLevel");
		if (enchantLevel > 0) {
			EnchantProfile ep = ObjectManagerClient.getEnchantProfile((int) item.getProperty("enchantProfileId"));
			if (ep.GetLevels().containsKey(enchantLevel)) {
				for (int e = 1; e <= enchantLevel; e++) {
					enchantStats.replace("gearScore",enchantStats.get("gearScore")+ep.GetLevels().get(e).GetGearScoreValue()+(int)(enchantStats.get("gearScore") * ep.GetLevels().get(e).GetGearScoreValuePercentage()));
					if (ep.GetLevels().get(e).GetAllStats()) {
						for (String stat : enchantStats.keySet()) {
							if (ep.GetLevels().get(e).GetPercentage()) {
								enchantStats.replace(stat, enchantStats.get(stat) + (int)(enchantStats.get(stat) * ep.GetLevels().get(e).GetStatValue()));
							} else {
								enchantStats.replace(stat, enchantStats.get(stat) + (int)ep.GetLevels().get(e).GetStatValue());
							}
						}
					} else {
						HashMap<String, EnchantStat> enchLevStats = ep.GetLevels().get(e).GetStats();
						for (String stat : enchLevStats.keySet()) {
							if (ep.GetLevels().get(e).GetAddNotExist() && !enchantStats.containsKey(stat)) {
								enchantStats.put(stat, enchLevStats.get(stat).GetValue() + (int)(enchLevStats.get(stat).GetValue() * enchLevStats.get(stat).GetValuePercentage()));
							} else if (enchantStats.containsKey(stat)) {
								enchantStats.replace(stat, enchantStats.get(stat) + enchLevStats.get(stat).GetValue()
										+  (int)((enchantStats.get(stat) + enchLevStats.get(stat).GetValue()) * enchLevStats.get(stat).GetValuePercentage()));
							}/* else if (!ep.GetLevels().get(e).GetAddNotExist() && !enchantStats.containsKey(stat)&&(stat.equals("dmg-base")||stat.equals("dmg-base"))) {
							
								enchantStats.replace(stat, enchantStats.get(stat) + enchLevStats.get(stat).GetValue()
										+ (enchantStats.get(stat) + enchLevStats.get(stat).GetValue()) * enchLevStats.get(stat).GetValuePercentage());
							}*/
						}
					}
				}
			}
		}
		for (String statName: enchantStats.keySet()) {
			int value = enchantStats.get(statName);
		//	Log.error("LOAD ITEM: item:"+item+" base + enchant  stat: " + statName + " by: " + value);
		}
		item.setProperty("enchantStats", enchantStats);
		
		ObjectManagerClient.saveObject(item.getOid());
		Engine.getPersistenceManager().setDirty(item);

		
		
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "EnchantingMsg");
		props.put("PluginMessageType", "EnchantingCompleted");
		TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(playerMsg);
		AgisInventoryClient.SendInventoryUpdateMessage invUpdateMsg = new AgisInventoryClient.SendInventoryUpdateMessage(playerOid);
		Engine.getAgent().sendBroadcast(invUpdateMsg);
	}
}
