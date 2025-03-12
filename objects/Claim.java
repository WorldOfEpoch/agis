package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import atavism.agis.core.Agis;
import atavism.agis.database.AccountDatabase;
import atavism.agis.database.MobDatabase;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.AgisMobClient;
import atavism.agis.plugins.AgisWorldManagerClient;
import atavism.agis.plugins.AgisWorldManagerPlugin;
import atavism.agis.plugins.ClassAbilityClient;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.CombatPlugin;
import atavism.agis.plugins.GuildClient;
import atavism.agis.plugins.VoxelClient;
import atavism.agis.plugins.VoxelPlugin;
import atavism.agis.util.EventMessageHelper;
import atavism.agis.util.ExtendedCombatMessages;
import atavism.agis.util.RequirementChecker;
import atavism.msgsys.Message;
import atavism.msgsys.MessageDispatch;
import atavism.msgsys.NoRecipientsException;
import atavism.msgsys.SubjectFilter;
import atavism.server.engine.BasicWorldNode;
import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.InterpolatedWorldNode;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.engine.PlayerCache;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.objects.*;
import atavism.server.plugins.MobManagerPlugin;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.plugins.WorldManagerClient.ObjectInfo;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;
import atavism.server.util.Log;

/**
 * A claim is an area of the world that a user has control of and can perform
 * actions (such as build, dig) and place objects in.
 * 
 * @author Andrew Harrison
 *
 */
public class Claim implements Serializable, MessageDispatch {

	public Claim() {
	}

	public Claim(int id, AOVector loc, int size, OID instanceOID, int claimType, OID owner, long instanceOwner, long instanceGuild, DisplayContext dc, HashMap<String, Serializable> props, int profile) {
		this.id = id;
		this.loc = loc;
		this.instanceOID = instanceOID;
		this.claimType = claimType;
		this.owner = owner;
		this.dc = dc;
		this.props = props;
		this.instanceOwner = instanceOwner;
		this.instanceGuild = instanceGuild;
		this.claim_limit_profile = profile;
		if (dc != null)
			spawn();
	}

	public String toString() {
		return "[Claim: id=" + id + " name=" + name + " loc=" + loc + " size=" + size + " instanceOID=" + instanceOID + " claimType=" + claimType + " owner=" + owner + " instanceOwner=" + instanceOwner + "instanceGuild="
				+ instanceGuild + "]";
	}

	/**
	 * Add in an action from the database.
	 * 
	 * @param id
	 * @param action
	 * @param type
	 * @param size
	 * @param loc
	 * @param material
	 */
	public void AddUpgradeData(int id, String items, AOVector size, AOVector loc, long cost, int currency, int profile,int taxCurrency,long taxAmount,long taxInterval,long taxPeriodPay,long taxPeriodSell) {
		ClaimUpgrade claimUpgrade = new ClaimUpgrade();
		claimUpgrade.id = id;
		claimUpgrade.items = items;
		claimUpgrade.size = size;
		claimUpgrade.loc = loc;
		claimUpgrade.cost = cost;
		claimUpgrade.currency = currency;
		claimUpgrade.claim_limit_profile = profile;
		claimUpgrade.taxCurrency = taxCurrency;
		claimUpgrade.taxAmount = taxAmount;
		claimUpgrade.taxInterval = taxInterval;
		claimUpgrade.taxPeriodPay = taxPeriodPay;
		claimUpgrade.taxPeriodSell = taxPeriodSell;
		
		if (items != null) {
			for (String purchaseItemReq : items.split(",")) {
				if (!purchaseItemReq.isEmpty())
					claimUpgrade.itemReqs.add(Integer.parseInt(purchaseItemReq));
			}
		}
		upgrades.put(upgrades.size() + 1, claimUpgrade);
	}

	/**
	 * Add in an action from the database.
	 * 
	 * @param id
	 * @param action
	 * @param type
	 * @param size
	 * @param loc
	 * @param material
	 */
	public void AddActionData(int id, String action, String type, AOVector size, AOVector loc, AOVector normal, int material) {
		ClaimAction claimAction = new ClaimAction();
		claimAction.id = id;
		claimAction.action = action;
		claimAction.brushType = type;
		claimAction.size = size;
		claimAction.loc = loc;
		claimAction.normal = normal;
		claimAction.mat = material;
		actions.add(claimAction);
	}

	/**
	 * Add in an object from the database
	 * 
	 * @param id
	 * @param templateId
	 * @param stage
	 * @param complete
	 * @param gameObject
	 * @param loc
	 * @param orient
	 * @param itemID
	 * @param state
	 * @param health
	 * @param maxHealth
	 * @param itemCounts
	 * @param lockTemplateId
	 * @param lockDurability
	 * @param progress
	 */
	public void AddClaimObject(int id, int templateId, int stage, boolean complete, int parent, String parents, String gameObject, AOVector loc, Quaternion orient, int itemID, String state, int health, int maxHealth,
			HashMap<Integer, Integer> itemCounts, int lockTemplateId, int lockDurability, int progress, boolean finalStage,long taskCurrentTime,long taskLastTimeUpdate,int ownerStat,OID playerOid) {
		ClaimObject obj = new ClaimObject();
		obj.id = id;
		obj.templateId = templateId;
		obj.stage = stage;
		obj.complete = complete;
		obj.finalStage = finalStage;
		obj.parent = parent;
		obj.gameObject = gameObject;
		obj.itemID = itemID;
		obj.loc = loc;
		obj.orient = orient;
		obj.state = state;
		obj.health = health;
		obj.maxHealth = maxHealth;
		obj.itemReqs = itemCounts;
		obj.lockTemplateId = lockTemplateId;
		obj.lockDurability = lockDurability;
		obj.currentTime = taskCurrentTime;
		obj.lastTimeUpdate = taskLastTimeUpdate;
		obj.taskPlayerOid = playerOid;
		if(playerOid!=null)
			obj.users.put(playerOid, ownerStat);
		objects.add(obj);
		if (id > highestObjectID) {
			highestObjectID = id;
		}
		obj.parents = parents;
		obj.progress = progress;

		BuildObjectTemplate tmpl = VoxelPlugin.GetBuildObjectTemplate(templateId);
		if (claimObjectsList.containsKey(tmpl.getClaimObjectCategory())) {
			claimObjectsList.put(tmpl.getClaimObjectCategory(), claimObjectsList.get(tmpl.getClaimObjectCategory()) + 1);
		} else {
			claimObjectsList.put(tmpl.getClaimObjectCategory(), 1);
		}
	}

	public void AddClaimObject(ClaimObject obj) {
		objects.add(obj);
		if (obj.id > highestObjectID) {
			highestObjectID = obj.id;
		}
	}

	public LinkedList<ClaimObject> getClaimObjects() {
		return objects;
	}

	/**
	 * Add in a resource from the database.
	 * 
	 * @param id
	 * @param itemID
	 * @param count
	 */
	public void AddClaimResource(int id, int itemID, int count) {
		ClaimResource resource = new ClaimResource();
		resource.id = id;
		resource.itemID = itemID;
		resource.count = count;
		resources.put(itemID, resource);
	}

	/**
	 * Add in a permission from the database
	 * 
	 * @param playerOid
	 * @param playerName
	 * @param permissionLevel
	 */
	public void AddClaimPermission(OID playerOid, String playerName, int permissionLevel) {
		ClaimPermission permission = new ClaimPermission();
		permission.playerOid = playerOid;
		permission.playerName = playerName;
		permission.permissionLevel = permissionLevel;
		permissions.put(playerOid, permission);
	}

	/**
	 * Subscribes the instance to receive certain relevant messages that are sent to
	 * the world object created by this instance.
	 */
	public void activate() {
		if (Log.loggingDebug)Log.debug("Claim Activate active="+active+" id="+id+" instanceOID="+instanceOID+" objectOID="+objectOID+" ");
		
		SubjectFilter filter = new SubjectFilter(objectOID);
		filter.addType(ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS);
		eventSub = Engine.getAgent().createSubscription(filter, this);
		// Set the reaction radius tracker to alert the object if a player has entered
		// its draw radius
		MobManagerPlugin.getTracker(instanceOID).addReactionRadius(objectOID, VoxelPlugin.CLAIM_DRAW_RADIUS);
		active = true;
		if (Log.loggingDebug)Log.debug("CLAIM: activate claim with oid: " + objectOID + " activated in instanceOID:" + instanceOID);

		List<AtavismBuildingObject> objectsToAdd = new ArrayList<AtavismBuildingObject>();
		// Go through and spawn any NPCs needed
		for (ClaimObject cObject : objects) {
			BuildObjectTemplate tmpl = VoxelPlugin.GetBuildObjectTemplate(cObject.templateId);
			if (Log.loggingDebug)
				Log.debug("CLAIM: activate claim with oid: " + objectOID + " cObject="+cObject+" "+tmpl);
			if (cObject.complete) {
				if (Log.loggingDebug)
					Log.debug("CLAIM: activate claim with oid: " + objectOID + " cObject="+cObject+" complete");
				
				// Perform any special actions based on the objectType and data of the object
				// if (tmpl != null && tmpl.getInteractionType() != null &&
				// tmpl.getInteractionType().equals("NPC")) {}
				if (tmpl != null && tmpl.stages.get(cObject.stage).getInteractionType() != null && tmpl.stages.get(cObject.stage).getInteractionType().equals("NPC")) {
					Log.debug("BUILD: object is of type NPC");
					int spawnID = tmpl.stages.get(cObject.stage).getInteractionID();
					MobDatabase mDB = new MobDatabase(false);
					SpawnData sd = mDB.loadSpecificSpawnData(spawnID);
					if (sd == null) {
						Log.error("CLAIM: SpawnData  id:" + spawnID + " can not by loaded maybe is deleted or not active");
					} else {
						sd.setName(generateSpawnName(cObject));
						sd.setInstanceOid(instanceOID);
						sd.setLoc(new Point(AOVector.add(cObject.loc, loc)));
						sd.setOrientation(cObject.orient);
						AgisMobClient.spawnMob(sd);
						Log.debug("BUILD: sent Spawn Mob message");
					}
				}
			} else {
				if (Log.loggingDebug)
					Log.debug("CLAIM: activate claim with oid: " + objectOID + " cObject="+cObject+" not complete");
				ClaimTask task = new ClaimTask();
				
				if(cObject.stage==0) {
					task.RestoreBuildTask(tmpl, loc, cObject.orient, cObject.parent, cObject.parents, -1, null, cObject.taskPlayerOid, this, cObject);
				}else {
					float buildTime = tmpl.getStage(cObject.stage + 1).buildTimeReq;
					long endTime = (System.currentTimeMillis() + ((long) buildTime * 1000l));
					//task.StartUpgradeTask(tmpl, cObject, itemEntries, playerOid, tmpl.buildTaskRequiresPlayer(), endTime, this);
					task.RestoreUpgradeTask(tmpl, cObject, new ArrayList<ClaimObjectBuildItemEntry>(), cObject.taskPlayerOid, tmpl.buildTaskRequiresPlayer(), endTime,this) ;
				}
				task.buildProgress = cObject.progress;
				if (Log.loggingDebug)	Log.debug("buildClaimObject add task for claim " + id + " size=" + tasks.size());
				tasks.add(task);
				if (Log.loggingDebug)Log.debug("buildClaimObject size=" + tasks.size());
				//float newModTime= task.calculateTimeSpeedMod();
				task.reschedule(task.calculateTimeSpeedMod());
				
				sendObject(cObject);
			}
			if (Log.loggingDebug)
				Log.debug("CLAIM: activate claim with oid: " + objectOID + " object Coliders");
			AtavismBuildingObject abo = new AtavismBuildingObject();
			if (tmpl != null) {
				AtavismBuildingColliders abc = tmpl.stages.get(cObject.stage).getProgressColliders().get(cObject.progress);
				if (abc != null) {
					abo.colliders = abc.colliders;
					abo.setId(cObject.id);
					abo.setPosition(AOVector.add(cObject.loc, loc));
					abo.setOrientation(cObject.orient);
					objectsToAdd.add(abo);
				}
			}
		}

		if (objectsToAdd.size() > 0) {
			VoxelClient.AddDynamicObjects(instanceOID, null, objectsToAdd);
		}
		/*if (instanceOID != null && instancePlayersInRange.get(instanceOID) != null) {
			for (OID playerOid : instancePlayersInRange.get(instanceOID)) {
				sendObjectsToPlayer(playerOid);
			}
		}*/
		
		if (Log.loggingDebug)
			Log.debug("CLAIM: activate claim with oid: " + objectOID + " End");
	}

	public void deactivate(OID instanceOID) {
		Engine.getAgent().removeSubscription(eventSub);
		MobManagerPlugin.getTracker(instanceOID).removeReactionRadius(objectOID);
		active = false;
		if (Log.loggingDebug)
			Log.debug("CLAIM: deactivate claim with oid: " + objectOID + " activated in instanceOID:" + instanceOID);
		ArrayList<Integer> objectToDel = new ArrayList<Integer>();
		// Go through and despawn any NPCs needed
		for (ClaimObject cObject : objects) {
			// Perform any special actions based on the objectType and data of the object
			BuildObjectTemplate tmpl = VoxelPlugin.GetBuildObjectTemplate(cObject.templateId);
			if (tmpl.stages.get(cObject.stage).getInteractionType() != null && tmpl.stages.get(cObject.stage).getInteractionType().equals("NPC")) {
				Log.debug("BUILD: object is of type NPC");
				AgisMobClient.deleteSpawnGenerator(generateSpawnName(cObject), instanceOID);
				Log.debug("BUILD: sent remove spawn message");
			}
			objectToDel.add(cObject.id);
		}

		List<AtavismBuildingObject> objectsToAdd = new ArrayList<AtavismBuildingObject>();

		VoxelClient.AddDynamicObjects(instanceOID, objectToDel, objectsToAdd);

	}

	/**
	 * Deals with the messages the instance has picked up.
	 */
	public void handleMessage(Message msg, int flags) {
		if (active == false) {
			return;
		}

		if (msg.getMsgType() == ObjectTracker.MSG_TYPE_NOTIFY_REACTION_RADIUS) {
			ObjectTracker.NotifyReactionRadiusMessage nMsg = (ObjectTracker.NotifyReactionRadiusMessage) msg;
			if (Log.loggingDebug)
				Log.debug("Claim: myOid=" + objectOID + " objOid=" + nMsg.getSubject() + " inRadius=" + nMsg.getInRadius() + " wasInRadius=" + nMsg.getWasInRadius());

			if (nMsg.getInRadius()) {
				addPlayer(nMsg.getSubject());
			} else {
				// Remove subject from targets in range
				removePlayer(nMsg.getSubject(), false);
				// WorldManagerClient.sendObjChatMsg(nMsg.getSubject(), 2, "You have left the
				// radius for Claim: " + id);
				/*
				 * Map<String, Serializable> props = new HashMap<String, Serializable>();
				 * props.put("ext_msg_subtype", "remove_claim"); props.put("claimID", id);
				 * 
				 * TargetedExtensionMessage eMsg = new TargetedExtensionMessage(
				 * WorldManagerClient.MSG_TYPE_EXTENSION, nMsg.getSubject(), nMsg.getSubject(),
				 * false, props); Engine.getAgent().sendBroadcast(eMsg);
				 */
			}
		}
	}

	/**
	 * Add a player to the update list for this Claim. The player will receive data
	 * about the claim and any updates that occur.
	 * 
	 * @param playerOID
	 */
	public void addPlayer(OID playerOID) {
		// sendClaimData(playerOID);
		try {
			if (Log.loggingDebug)
				Log.debug("Claim: addPlayer " + id + " playerOID=" + playerOID + "  " + instancePlayersInRange + " " + instancePlayersInRange.keySet() + " " + instancePlayersInRange.values());
			ObjectInfo oi = WorldManagerClient.getObjectInfo(playerOID);
			if (Log.loggingDebug)
				Log.debug("Claim: addPlayer " + id + " playerOID=" + playerOID + "  " + oi.objType + " " + oi.name + " " + instancePlayersInRange + " " + instancePlayersInRange.keySet() + " "
						+ instancePlayersInRange.values());
			if (!oi.objType.equals(ObjectType.getObjectType("PLAYER"))) {
				if (Log.loggingDebug)
					Log.debug("Claim addPlayer: " + id + " playerOID=" + playerOID + "  " + oi.objType + " is not equals " + ObjectType.getObjectType("PLAYER"));
				return;
			}
			OID instanceOid = oi.instanceOid;
			if (Log.loggingDebug)	Log.debug("Claim: addPlayer "+id+" playerOID="+playerOID+" instanceOid="+instanceOid+" claimOidList="+claimOidList);
			if (!claimOidList.containsKey(instanceOid)) {
				if (Log.loggingDebug)
					Log.debug("Claim: addPlayer " + id + " playerOID=" + playerOID + "  claimOidList:" + claimOidList + " is not  contains instance :" + instanceOid);
				return;
			}
			instancePlayersInRange.computeIfAbsent(instanceOid, __ -> new LinkedList<OID>());
			
			if (!instancePlayersInRange.get(instanceOid).contains(playerOID)) {
				instancePlayersInRange.get(instanceOid).add(playerOID);
				if (Log.loggingDebug)
					Log.debug("Claim: addPlayer: " + id + " playerOID=" + playerOID + "  send data for instance:" + instanceOid);
				sendActionsToPlayer(playerOID);
				sendObjectsToPlayer(playerOID);
				sendClaimData(playerOID);
			}else {
				if (Log.loggingDebug)
					Log.debug("Claim: addPlayer " + id + " playerOID=" + playerOID + " in instancePlayersInRange ");
		
			}
		} catch (Exception e) {
			Log.exception("Claim: addPlayer "+playerOID,e);
			e.printStackTrace();
		}
		if (Log.loggingDebug)
			Log.debug("Claim: addPlayer " + id + " playerOID=" + playerOID + " End ");

		/*
		 * if (!playersInRange.contains(playerOID)) { playersInRange.add(playerOID);
		 * sendActionsToPlayer(playerOID); sendObjectsToPlayer(playerOID);
		 * sendClaimData(playerOID);
		 * 
		 * }
		 */
	}

	/**
	 * Removes a player from the claim. They will no longer receive updates.
	 * 
	 * @param playerOID
	 * @param removeLastID
	 */
	public void removePlayer(OID playerOID, boolean removeLastID) {
		if (Log.loggingDebug)
			Log.debug("Claim: removePlayer  " + id + " playerOID=" + playerOID + " removeLastID=" + removeLastID);
		for (OID inst : instancePlayersInRange.keySet()) {
			if (instancePlayersInRange.get(inst).contains(playerOID)) {
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "remove_claim");
				props.put("claimID", id);
				instancePlayersInRange.get(inst).remove(playerOID);
				TargetedExtensionMessage eMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOID, playerOID, false, props);
				Engine.getAgent().sendBroadcast(eMsg);
			}
		}
		if (removeLastID && playersLastIDSent.containsKey(playerOID))
			playersLastIDSent.remove(playerOID);
		if (/* removeLastID && */playersLastObjectIDSent.containsKey(playerOID))
			playersLastObjectIDSent.remove(playerOID);
		interruptBuildTask(playerOID);
        if (Log.loggingDebug)
            Log.debug("Claim: removePlayer  " + id + " playerOID=" + playerOID + " removeLastID=" + removeLastID + " return");
	}

	/**
	 * Alerts the players and owner of the Claim that its settings have been
	 * updated.
	 * 
	 * @param currentPlayer
	 */
	public void claimUpdated(OID currentPlayer) {
		if(currentPlayer == null)
			return;
		//OID instanceOid = WorldManagerClient.getObjectInfo(currentPlayer).instanceOid;
		if (instanceOID != null && instancePlayersInRange.get(instanceOID) != null)
			for (OID playerOid : instancePlayersInRange.get(instanceOID)) {
				sendClaimData(playerOid);
			}
		/*
		 * for (OID playerOid : playersInRange) { sendClaimData(playerOid); }
		 */
		sendClaimData(currentPlayer);
	}

	public void claimUpdated() {
		if (Log.loggingDebug)
			Log.debug("claimUpdated: cId=" + getID() + " playersInRange=" + playersInRange + " " + playersInRange.size());
		for (OID inst : instancePlayersInRange.keySet())
			for (OID playerOid : instancePlayersInRange.get(inst)) {
				sendClaimData(playerOid);
			}
	}

	/**
	 * Sends down the claim information to the specified player.
	 * 
	 * @param playerOID
	 */
	public void sendClaimData(OID playerOid) {
		if (Log.loggingDebug)
			Log.debug("sendClaimData: cId=" + getID() + " playerOid=" + playerOid);
		// Log.dumpStack();
		// OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid,
		// WorldManagerClient.NAMESPACE, "accountId");
		OID accountID = Engine.getDatabase().getAccountOid(playerOid);
		if (Log.loggingDebug)
			Log.debug("sendClaimData: cId=" + getID() + " playerOid=" + playerOid + " accountID=" + accountID);

		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "claim_data");
		props.put("claimID", id);
		props.put("claimName", name);
		props.put("claimLoc", loc);
		props.put("claimSizeX", size);
		props.put("claimSizeZ", sizeZ);
		props.put("claimType", claimType);
		props.put("ownerName", sellerName);
		props.put("forSale", forSale);
		props.put("taxTime", getTaxPaidUntil() - System.currentTimeMillis());
		
		int profile = getProfile();
		if (getUpgrade() > 0) {
			ClaimUpgrade cu = upgrades.get(getUpgrade());
			profile = cu.claim_limit_profile;
		}
		int limit = 0;
		if (profile > 0) {
			ClaimProfile cp = VoxelPlugin.claimProfiles.get(profile);
			if(cp==null) {
				Log.error("ClaimProfile is null for profile "+profile );
			}else {
			for (int catId : cp.getLimits().keySet()) {
				props.put("lId" + limit, catId);
				props.put("lc" + limit, cp.getLimits().get(catId));
				limit++;
			}
			}
		}
		props.put("limitnum", limit);

		int climit = 0;
		if (claimObjectsList != null) {
			for (int catId : claimObjectsList.keySet()) {
				props.put("clId" + climit, catId);
				props.put("clc" + climit, claimObjectsList.get(catId));
				climit++;
			}
		}
		props.put("climitnum", climit);
		
		int taxCurrency = getTaxCurrency();
		long taxAmount = getTaxAmount();
		long taxInterval = getTaxInterval();
		long taxPeriodPay = getTaxPeriodPay();
		long taxPeriodSell = getTaxPeriodSell();
		if (getUpgrade() > 0) {
			ClaimUpgrade cu = upgrades.get(getUpgrade());
			taxCurrency = cu.taxCurrency;
			taxAmount = cu.taxAmount;
			taxInterval = cu.taxInterval;
			taxPeriodPay = cu.taxPeriodPay;
			taxPeriodSell = cu.taxPeriodSell;
		}
		props.put("taxCurrency" , taxCurrency);
		props.put("taxAmount" , taxAmount);
		props.put("taxInterval" , taxInterval);
		props.put("taxPeriodPay" , taxPeriodPay);
		props.put("taxPeriodSell" , taxPeriodSell);
		ClaimUpgrade cu = upgrades.get(getUpgrade());
		if (cu != null) {
			props.put("usizeX", cu.size.getX());
			props.put("usizeY", cu.size.getY());
			props.put("usizeZ", cu.size.getZ());
			props.put("ulocX", cu.loc.getX());
			props.put("ulocY", cu.loc.getY());
			props.put("ulocZ", cu.loc.getZ());
		}
		// if (permissions.containsKey(playerOid))
		// props.put("permissionLevel", permissions.get(playerOid).permissionLevel);
		// else
		int permissionLevel = getPlayerPermission(playerOid, accountID);
		props.put("permissionLevel", permissionLevel);
		if (forSale) {
			props.put("cost", cost);
			props.put("currency", currency);
		}
		if (purchaseItemReqs.size() > 0) {
			props.put("purchaseItemReq", purchaseItemReqs.get(0));
		} else {
			props.put("purchaseItemReq", -1);
		}

		if (Log.loggingDebug)
			Log.debug("CLAIM: sending claim data with owner: " + owner + " and seller: " + sellerName + " for claim: " + id);
		// if (owner != null && owner.equals(accountID)) {
		if (permissionLevel >= PERMISSION_OWNER) {
			props.put("myClaim", true);
			// Also include the resources
			if (VoxelPlugin.USE_CLAIM_RESOURCES) {
				props.put("resourceCount", resources.size());
				int pos = 0;
				for (int itemID : resources.keySet()) {
					props.put("resource" + pos, itemID);
					props.put("resource" + pos + "Count", resources.get(itemID).count);
					pos++;
				}
			} else {
				props.put("resourceCount", 0);
			}
			props.put("bondDue", bondPaidUntil - System.currentTimeMillis());
			props.put("taxDue", taxPaidUntil - System.currentTimeMillis());
		} else {
			props.put("myClaim", false);
		}

		props.put("bondItemTemplate", bondItemTemplate);

		int permissionCount = 0;
		// if ((owner != null && owner.equals(accountID)) ||
		// (permissions.containsKey(playerOid) &&
		// permissions.get(playerOid).permissionLevel >= PERMISSION_ADD_USERS)) {
		if (permissionLevel >= PERMISSION_ADD_USERS) {
			for (ClaimPermission per : permissions.values()) {
				props.put("permission_" + permissionCount, per.playerName);
				props.put("permissionLevel_" + permissionCount, per.permissionLevel);
				permissionCount++;
			}
		}
		props.put("permissionCount", permissionCount);
		
		if(objectsStance.containsKey(playerOid)) {
			props.put("stance", objectsStance.get(playerOid));
		}else {
			props.put("stance", 0);
			Log.debug("sendClaimData id="+id+" no stance for "+playerOid+" "+objectsStance.size());
		}
		
		
		if (Log.loggingDebug)
			Log.debug("Claim sendClaimData: " + props);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

	public void sendClaimRemovedData(OID playerOID) {
		if (playerOID == null)
			return;
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "remove_claim_data");
		props.put("claimID", id);

		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOID, playerOID, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

	/**
	 * An external call to spawn a world object for the claim.
	 * 
	 * @param instanceOID
	 */
	public void spawn(OID instanceOID) {
		this.instanceOID = instanceOID;
		spawn();
	}

	/**
	 * Spawn a world object for the claim.
	 */
	public void spawn() {
		if (Log.loggingDebug)Log.debug("Claim.spawn "+id+" instance="+instanceOID);
		Template markerTemplate = new Template();
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_NAME, "Claim" + id);
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_OBJECT_TYPE, ObjectTypes.mob);
		markerTemplate.put(WorldManagerClient.NAMESPACE, WorldManagerClient.TEMPL_PERCEPTION_RADIUS, 75);
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_LOC, new Point(loc));
		ClaimUpgrade cu = upgrades.get(getUpgrade());
		if (cu != null) {
			if (Log.loggingDebug)Log.debug("spawn claim cu size " + cu.size + " loc " + cu.loc);
			AOVector v = new AOVector(cu.size.getX(), cu.size.getY(), cu.size.getZ());
			markerTemplate.put(WorldManagerClient.NAMESPACE, "scale", v);
			AOVector nloc = AOVector.add(getLoc(), cu.loc);
			markerTemplate.put(Namespace.WORLD_MANAGER, "diffLoc", cu.loc);
			markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_LOC, new Point(nloc));
		} else {
			markerTemplate.put(Namespace.WORLD_MANAGER, "diffLoc", new AOVector());
			AOVector v = new AOVector(size, sizeY, sizeZ);
			markerTemplate.put(WorldManagerClient.NAMESPACE, "scale", v);
			markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_LOC, new Point(loc));
		}
		DisplayContext dc = new DisplayContext(model, true);
		dc.addSubmesh(new DisplayContext.Submesh("", ""));
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
		markerTemplate.put(Namespace.WORLD_MANAGER, "model", model);
		// Put in any additional props
		if (props != null) {
			for (String propName : props.keySet()) {
				markerTemplate.put(Namespace.WORLD_MANAGER, propName, props.get(propName));
			}
		}
		// Create the object
		objectOID = ObjectManagerClient.generateObject(ObjectManagerClient.BASE_TEMPLATE_ID, ObjectManagerClient.BASE_TEMPLATE, markerTemplate);
		if (objectOID != null) {
			if (instanceOID != null)
				claimOidList.put(instanceOID, objectOID);
			// Need to create an interpolated world node to add a tracker/reaction radius to
			// the claim world object
			BasicWorldNode bwNode = WorldManagerClient.getWorldNode(objectOID);
			InterpolatedWorldNode iwNode = new InterpolatedWorldNode(bwNode);
			claimEntity = new ClaimEntity(objectOID, iwNode);
			EntityManager.registerEntityByNamespace(claimEntity, Namespace.MOB);
			MobManagerPlugin.getTracker(instanceOID).addLocalObject(objectOID, VoxelPlugin.CLAIM_DRAW_RADIUS);

			WorldManagerClient.spawn(objectOID);
			if (Log.loggingDebug)
				Log.debug("CLAIM: spawned claim at : " + loc);
			activate();
		}
	}

	/**
	 * Changes the claim owner to the buyer of the claim. Alerts all players nearby
	 * that the claim owner has changed.
	 * 
	 * @param buyerOID
	 * @param newOwner
	 * @return
	 */
	public OID changeClaimOwner(OID buyerOID, OID newOwner, OID abandoningCharacter) {
		forSale = false;
		OID oldOwner = owner;
		owner = newOwner;
		if (buyerOID != null) {
			AccountDatabase aDB = new AccountDatabase(false);
			sellerName = aDB.getCharacterNameByOid(buyerOID);
			// sellerName = WorldManagerClient.getObjectInfo(buyerOID).name;
		} else {
			sellerName = "";
		}

		// Clear all permissions
		for (OID targetOid : permissions.keySet()) {
			cDB.deleteClaimPermission(id, targetOid);
		}
		permissions.clear();

		if (newOwner == null) {
			forSale = true;
			// Claim was abandoned - remove everything and mail it to the old owner
			ArrayList<ClaimObject> objectsToRemove = new ArrayList<ClaimObject>();
			for (ClaimObject obj : objects) {
				objectsToRemove.add(obj);
				BuildObjectTemplate tmpl = VoxelClient.getBuildingTemplate(obj.templateId);
				if (obj.state != null) {
					if (tmpl.stages.get(obj.stage).getInteractionType() != null && tmpl.stages.get(obj.stage).getInteractionType().equals("Chest")) {
						// Also need to send items that are in a chest
						AgisInventoryClient.sendStorageItemsAsMail(oldOwner, OID.fromString(obj.state));
					}
				}
				if (claimObjectsList.containsKey(tmpl.getClaimObjectCategory())) {
					if (claimObjectsList.get(tmpl.getClaimObjectCategory()) > 1) {
						claimObjectsList.put(tmpl.getClaimObjectCategory(), claimObjectsList.get(tmpl.getClaimObjectCategory()) - 1);
					} else {
						claimObjectsList.remove(tmpl.getClaimObjectCategory());
					}
				}
			}

			// Next check if there is space to pick up the items
			HashMap<Integer, Integer> itemsToGivePlayer = new HashMap<Integer, Integer>();
			for (ClaimObject obj : objectsToRemove) {
				if (obj.itemID > 0) {
					if (itemsToGivePlayer.containsKey(obj.itemID)) {
						itemsToGivePlayer.put(obj.itemID, itemsToGivePlayer.get(obj.itemID) + 1);
					} else {
						itemsToGivePlayer.put(obj.itemID, 1);
					}
				}
			}

			if (itemsToGivePlayer.size() > 0) {
				// Mail out a list of items to the owner
				String message = "You recently lost your claim, but had some items and objects on it. They have been converted to items and attached to this mail.";
				AgisInventoryClient.sendAccountMail(abandoningCharacter, oldOwner, "Claim Items", message, -1, 0, itemsToGivePlayer);
			}

			// Also send claim deed item ID
			if (claimItemTemplate > 0) {
				String message = "Your claim deed from the claim you previously owned is attached.";
				HashMap<Integer, Integer> claimDeed = new HashMap<Integer, Integer>();
				claimDeed.put(claimItemTemplate, 1);
				AgisInventoryClient.sendAccountMail(abandoningCharacter, oldOwner, "Claim Deed", message, -1, 0, claimDeed);
			}

			if (bondItemTemplate > 0) {
				bondItemTemplate = -1;
				bondPaidUntil = Calendar.getInstance().getTimeInMillis();
			}

			// Check for all children and remove them as well
			for (ClaimObject obj : objectsToRemove) {

				sendRemoveObject(obj, obj.id);
				objects.remove(obj);
				cDB.deleteClaimObject(obj.id);
			}
		}
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "claim_updated");
		props.put("claimID", id);
		props.put("forSale", forSale);
		props.put("ct", claimType);
		if (forSale) {
			props.put("cost", cost);
			props.put("currency", currency);
		}
		if (purchaseItemReqs.size() > 0) {
			props.put("purchaseItemReq", purchaseItemReqs.get(0));
		} else {
			props.put("purchaseItemReq", -1);
		}
		props.put("bondItemTemplate", bondItemTemplate);
		props.put("ownerName", sellerName);
		
		int profile = getProfile();
		if (getUpgrade() > 0) {
			ClaimUpgrade cu = upgrades.get(getUpgrade());
			profile = cu.claim_limit_profile;
		}
		int limit = 0;
		if (profile > 0) {
			ClaimProfile cp = VoxelPlugin.claimProfiles.get(profile);
			for (int catId : cp.getLimits().keySet()) {
				props.put("lId" + limit, catId);
				props.put("lc" + limit, cp.getLimits().get(catId));
				limit++;
			}

		}
		props.put("limitnum", limit);

		int climit = 0;
		if (claimObjectsList != null) {
			for (int catId : claimObjectsList.keySet()) {
				props.put("clId" + climit, catId);
				props.put("clc" + climit, claimObjectsList.get(catId));
				climit++;
			}
		}
		props.put("climitnum", climit);
		//OID instanceOid = WorldManagerClient.getObjectInfo(buyerOID).instanceOid;
		if (Log.loggingDebug)
			Log.debug("changeClaimOwner: " + id + " buyerOID=" + buyerOID + " instanceOid=" + instanceOID + " instancePlayersInRange=" + instancePlayersInRange);

		// Loop through players in range and send them the update
		if (instancePlayersInRange.containsKey(instanceOID))
			for (OID playerOid : instancePlayersInRange.get(instanceOID)) {
				OID accountID = Engine.getDatabase().getAccountOid(playerOid);
				int permissionLevel = getPlayerPermission(playerOid, accountID);
				props.put("permissionLevel", permissionLevel);
				if (playerOid.equals(buyerOID)) {
					props.put("myClaim", true);
				} else {
					props.put("myClaim", false);
				}
				TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
				Engine.getAgent().sendBroadcast(msg);
			}
		/*
		 * for (OID playerOid : playersInRange) { if (playerOid.equals(buyerOID)) {
		 * props.put("myClaim", true); } else { props.put("myClaim", false); }
		 * TargetedExtensionMessage msg = new TargetedExtensionMessage(
		 * WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		 * Engine.getAgent().sendBroadcast(msg); }
		 */
		sendClaimRemovedData(oldOwner);
		if (buyerOID != null) {
			sendClaimData(buyerOID);
		}
		return oldOwner;
	}
	
	
	/**
	 * function to cancel the task if it is for a given claim object
	 * @param obj
	 */
	public void cancelTask(ClaimObject obj) {
		if (Log.loggingDebug)Log.debug("cancelTask "+obj); 
		ClaimTask todelete = null;
		for (ClaimTask task : tasks) {
			Log.debug("cancelTask task="+task);
			if (task.cObject.id == obj.id) {
				task.interrupted =true;
				todelete = task;
			}
		}
		if (Log.loggingDebug)Log.debug("cancelTask todelete="+todelete);
		if (todelete != null) {
			if(todelete.scheduledFuture!=null)
				todelete.scheduledFuture.cancel(false);
			Engine.getExecutor().remove(todelete);
			tasks.remove(todelete);
		}
		Log.debug("cancelTask END");
	}
	/**
	 * function to delete an claim object
	 * @param obj
	 */
	public void removeObject(ClaimObject obj) {
		sendRemoveObject(obj, obj.id);
		objects.remove(obj);
		cDB.deleteClaimObject(obj.id);
	}
	/**
	 * Alerts all nearby players that the claim has been deleted, then despawns the
	 * world object.
	 */
	public void claimDeleted(OID instanceOid) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "claim_deleted");
		props.put("claimID", id);

		// Loop through players in range and send them the update
		if (Log.loggingDebug)
			Log.debug("Claim claimDeleted ClaimId=" + id + " instanceOid=" + instanceOid + " instancePlayersInRange=" + instancePlayersInRange);
		if (!instancePlayersInRange.containsKey(instanceOid))
			if (Log.loggingDebug)
				Log.debug("Claim claimDeleted ClaimId=" + id + " instanceOid=" + instanceOid + " instancePlayersInRange=" + instancePlayersInRange + " instance is not in playerList");

		for (OID playerOid : instancePlayersInRange.get(instanceOid)) {
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
		}

		// despawn the object
		deactivate(instanceOid);
		WorldManagerClient.despawn(claimOidList.get(instanceOid));
	}

	public void Despawn(OID instanceOid) {
		if (Log.loggingDebug)Log.debug("Claim.Despawn instanceOid="+instanceOid);
		if (claimOidList.contains(instanceOid)) {
			WorldManagerClient.despawn(claimOidList.get(instanceOid));
			claimOidList.remove(instanceOid);
		}
	}

	public boolean SpawnedInInstance(OID instanceOid) {
		if (Log.loggingDebug)
			Log.debug("Claim SpawnedInInstance claimOidList=" + claimOidList + " check instance " + instanceOid);
		if (claimOidList.containsKey(instanceOid))
			return true;
		return false;
	}

	/**
	 * Adds a players permission to the claim. Verifies that the permission giver
	 * can give the permission and that the user does not already have a higher
	 * permission level.
	 * 
	 * @param giverOid
	 * @param targetOid
	 * @param playerName
	 * @param permissionLevel
	 */
	public void addPermission(OID giverOid, OID giverAccountID, OID targetOid, String playerName, int permissionLevel) {
		// First check if the permission giver has permission to give permission
		if (!permissions.containsKey(giverOid) && !owner.equals(giverAccountID)) {
			return;
		}
		if (permissions.containsKey(giverOid) && permissions.get(giverOid).permissionLevel < PERMISSION_ADD_USERS) {
			return;
		}

		// Check if the user already has a permission
		if (permissions.containsKey(targetOid)) {
			if (permissionLevel > permissions.get(targetOid).permissionLevel) {
				// Update their permission level
				permissions.get(targetOid).permissionLevel = permissionLevel;
				cDB.updateClaimPermission(id, targetOid, permissionLevel);
			}
		} else {
			// Add permission
			ClaimPermission permission = new ClaimPermission();
			permission.playerOid = targetOid;
			permission.playerName = playerName;
			permission.permissionLevel = permissionLevel;
			permissions.put(targetOid, permission);
			cDB.writeClaimPermission(id, targetOid, playerName, permissionLevel);
		}

		OID instanceOid = WorldManagerClient.getObjectInfo(giverOid).instanceOid;

		for (OID playerOid : instancePlayersInRange.get(instanceOid)) {
			// for (OID playerOid : playersInRange) {
			sendClaimData(playerOid);
		}
	}

	/**
	 * Removes a players permission from the claim. Verifies that the permission
	 * remover does have the permission required to remove another users permission
	 * and that the target is not the owner of the claim.
	 * 
	 * @param removerOid
	 * @param targetOid
	 */
	public void removePermission(OID removerOid, OID removerAccountID, OID targetOid) {
		// First check if the permission giver has permission to give permission
		if (!permissions.containsKey(removerOid) && !owner.equals(removerAccountID)) {
			return;
		}
		if (permissions.containsKey(removerOid) && permissions.get(removerOid).permissionLevel < PERMISSION_MANAGE_USERS) {
			return;
		}

		// Ensure the user being removed is not the owner
		if (targetOid.equals(owner)) {
			return;
		}

		if (!permissions.containsKey(targetOid) || (permissions.get(targetOid).permissionLevel >= PERMISSION_MANAGE_USERS && !owner.equals(removerAccountID))) {
			return;
		}

		permissions.remove(targetOid);
		cDB.deleteClaimPermission(id, targetOid);
		OID instanceOid = WorldManagerClient.getObjectInfo(removerOid).instanceOid;

		for (OID playerOid : instancePlayersInRange.get(instanceOid)) {
			// for (OID playerOid : playersInRange) {
			sendClaimData(playerOid);
		}
	}

	/**
	 * Returns the permission level for the player. Checks if the players account
	 * matches the owner, and failing that will see if it can find the characters
	 * permission level.
	 * 
	 * @param playerOid
	 * @param accountID
	 * @return
	 */
	public int getPlayerPermission(OID playerOid, OID accountID) {
		if (Log.loggingDebug)
			Log.debug("Clailm: getPlayerPermission: " + id + " " + instanceGuild);
		if (instanceGuild > 0) {
			try {
				Integer perm = GuildClient.getGuildClaimPermition(playerOid, (int) instanceGuild);
				return perm;
			} catch (Exception e) {
				Log.error("Claim Exception " + e.getMessage() + " " + e.getLocalizedMessage() + " " + e);
			}
		} else {
			if (accountID != null && accountID.equals(owner)) {
				if (Log.loggingDebug)
					Log.debug("Clailm: getPlayerPermission: " + id + " " + instanceGuild+" owner ");
				return PERMISSION_OWNER;
			} else if (permissions.containsKey(playerOid)) {
				if (Log.loggingDebug)
					Log.debug("Clailm: getPlayerPermission: " + id + " " + instanceGuild+" permissionLevel "+permissions.get(playerOid).permissionLevel);
				return permissions.get(playerOid).permissionLevel;
			}
		}
		return 0;
	}

	/*
	 * Action Functions
	 */

	/**
	 * Add an action to the claim. The data is stored in the database and sent down
	 * to all players within the draw/reaction radius.
	 * 
	 * @param action
	 * @param type
	 * @param size
	 * @param loc
	 * @param material
	 */
	public void performClaimAction(String action, String type, AOVector size, AOVector loc, AOVector normal, int material) {
		// Add to list
		ClaimAction claimAction = new ClaimAction();
		claimAction.action = action;
		claimAction.brushType = type;
		claimAction.size = size;
		claimAction.loc = loc;
		claimAction.normal = normal;
		claimAction.mat = material;
		actions.add(claimAction);
		// Save action to the database
		claimAction.id = cDB.writeClaimAction(id, action, type, size, loc, normal, material);
		// Send the action down to all in the radius
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "claim_action");
		props.put("action", action);
		props.put("type", type);
		props.put("size", size);
		props.put("loc", loc);
		props.put("normal", normal);
		props.put("mat", material);

		// Loop through players in range and send them the update
		// OID instanceOid = WorldManagerClient.getObjectInfo(giverOid).instanceOid;

		for (OID playerOid : instancePlayersInRange.get(instanceOID)) {
			// for (OID playerOid : playersInRange) {
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
			// Add to mapping of last action sent to the player
			playersLastIDSent.put(playerOid, claimAction.id);
		}
	}

	/**
	 * Undo the last action performed. Deletes the last entry from the database and
	 * sends the undo message to all players within the reaction radius.
	 */
	public void undoAction() {
		ClaimAction lastAction = actions.removeLast();
		cDB.deleteClaimAction(lastAction.id);

		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "claim_action");
		props.put("action", "heal");
		props.put("type", lastAction.brushType);
		props.put("size", lastAction.size);
		props.put("loc", lastAction.loc);
		props.put("normal", lastAction.normal);
		props.put("mat", lastAction.mat);

		for (OID playerOid : instancePlayersInRange.get(instanceOID)) {
			// for (OID playerOid : playersInRange) {
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
		}
	}

	public void sendActionsToPlayers() {
		if(instancePlayersInRange!=null && instanceOID!=null)
		if (instancePlayersInRange.containsKey(instanceOID)) {
			LinkedList<OID>  list = (LinkedList<OID>)instancePlayersInRange.get(instanceOID).clone();
			for (OID playerOid : list) {
				// for (OID playerOid : playersInRange) {
				sendActionsToPlayer(playerOid);
				sendObjectsToPlayer(playerOid);
			}
		}
	}

	/**
	 * Used when a player first enters the radius for the claim. Sends down all the
	 * changes. Checks the last action that was sent down to the player and will not
	 * send any more
	 * 
	 * @param playerOid
	 */
	private void sendActionsToPlayer(OID playerOid) {
		// Send 50 changes per message
		int chunkSize = 50;
		// Only send data higher than the last id that was sent to the player
		int lastIDSent = -1;
		if (playersLastIDSent.containsKey(playerOid))
			lastIDSent = playersLastIDSent.get(playerOid);
		// No need to continue if the last id sent is equal to or greater than the
		// number of actions
		if (actions.size() == 0 || lastIDSent >= actions.getLast().id)
			return;
		for (int i = 0; i < actions.size(); i += chunkSize) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "claim_action_bulk");
			int actionCount = chunkSize;
			if (actions.size() - i < chunkSize) {
				actionCount = actions.size() - i;
			}

			if (Log.loggingDebug)
				Log.debug("CLAIM: Comparing lastID: " + lastIDSent + " against last from chunk: " + actions.get(i + actionCount - 1).id);
			if (actions.get(i + actionCount - 1).id <= lastIDSent)
				continue;

			int numActions = 0;
			for (int j = 0; j < actionCount; j++) {
				if (Log.loggingDebug)
					Log.debug("CLAIM: Comparing action id: " + actions.get(j + i).id + " against lastID: " + lastIDSent);
				if (actions.get(j + i).id <= lastIDSent) {
					continue;
				}
				String actionString = actions.get(j + i).action + ";" + actions.get(j + i).brushType + ";";
				actionString += actions.get(j + i).size.getX() + "," + actions.get(j + i).size.getY() + "," + actions.get(j + i).size.getZ() + ";";
				actionString += actions.get(j + i).loc.getX() + "," + actions.get(j + i).loc.getY() + "," + actions.get(j + i).loc.getZ() + ";";
				actionString += actions.get(j + i).normal.getX() + "," + actions.get(j + i).normal.getY() + "," + actions.get(j + i).normal.getZ() + ";";
				actionString += actions.get(j + i).mat;
				props.put("action_" + j, actionString);
				// Log.debug("CLAIM: Sending actionString: " + actionString);
				/*
				 * props.put("type_" + j, actions.get(j+i).brushType); props.put("size_" + j,
				 * actions.get(j+i).size); props.put("loc_" + j, actions.get(j+i).loc);
				 * props.put("mat_" + j, actions.get(j+i).mat);
				 */
				playersLastIDSent.put(playerOid, actions.get(j + i).id);
				numActions++;
			}
			if (Log.loggingDebug)
				Log.debug("CLAIM: Sending action count: " + numActions + " to player: " + playerOid);
			props.put("numActions", numActions);
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
			// New code - only send the one chunk
			break;
		}
	}

	/*
	 * Object Functions
	 */

	boolean hasRequiredWeapon(OID playerOid, BuildObjectTemplate buildObjectTemplate) {
		if (buildObjectTemplate.weaponReq != null && !buildObjectTemplate.weaponReq.equals("") && !buildObjectTemplate.weaponReq.toLowerCase().contains("none")) {
			ArrayList<String> weaponType = new ArrayList<String>();
			try {
				Serializable wype =  EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE, "weaponType");
				if(wype !=null)
					weaponType =  (ArrayList<String>)wype;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			if (Log.loggingDebug)
				Log.debug("RESOURCE: checking weaponReq: " + buildObjectTemplate.weaponReq + " against: " + weaponType);
			if (!weaponType.contains(buildObjectTemplate.weaponReq)) {
				return false;
			}
		}
		return true;
	}

	boolean isCloseEnough(OID playerOid, BuildObjectTemplate buildObjectTemplate, AOVector loc) {
		BasicWorldNode wNode = WorldManagerClient.getWorldNode(playerOid);
		if (wNode == null) {
			Log.error("RANGE CHECK: wnode is null for builder: " + playerOid);
			return false;
		}
		Point casterLoc = wNode.getLoc();
		int distance = (int) Point.distanceTo(casterLoc, new Point(loc));
		if (distance > buildObjectTemplate.maxDistance) {
			if (Log.loggingDebug)
				Log.debug("RANGE CHECK: distance: " + distance + " is greater than: " + buildObjectTemplate.maxDistance + " with loc: " + loc);
			return false;
		}
		return true;
	}

	/**
	 * Kicks off a BuildTask to add a claim object to the claim based on the given
	 * buildObjectTemplate.
	 * 
	 * @param playerOid
	 * @param buildObjectTemplate
	 * @param loc
	 * @param orient
	 * @param itemID
	 * @param itemOid
	 * @return
	 */
	public boolean buildClaimObject(OID playerOid, BuildObjectTemplate buildObjectTemplate, AOVector loc, Quaternion orient, int parent, int itemID, OID itemOid, String parents) {
		Log.debug("buildClaimObject ");
		// Player cannot start a new task if they are currently on a task
		if (getPlayersBuildTask(playerOid) != null) {
			return false;
		}
		Log.debug("buildClaimObject |");
		if (Log.loggingDebug)	Log.debug("buildClaimObject: ClaimObjectCategory="+buildObjectTemplate.getClaimObjectCategory()+" upgrade="+upgrade+" getProfile()="+getProfile());
		
		if (upgrade > 0) {
			if (Log.loggingDebug)Log.debug("buildClaimObject: upgrade claim_limit_profile=" + upgrades.get(upgrade).claim_limit_profile);

			if (upgrades.get(upgrade).claim_limit_profile > 0) {
				if (Log.loggingDebug)Log.debug("buildClaimObject: upgrade limit=" + VoxelPlugin.claimProfiles.get(upgrades.get(upgrade).claim_limit_profile).getLimit(buildObjectTemplate.getClaimObjectCategory()));

				if (VoxelPlugin.claimProfiles.get(upgrades.get(upgrade).claim_limit_profile).getLimit(buildObjectTemplate.getClaimObjectCategory()) >= 0) {
					if (Log.loggingDebug)	Log.debug("buildClaimObject:  building with category =" + claimObjectsList.get(buildObjectTemplate.getClaimObjectCategory()));
					if (claimObjectsList.containsKey(buildObjectTemplate.getClaimObjectCategory())) {
						if (claimObjectsList.get(buildObjectTemplate.getClaimObjectCategory()) >= VoxelPlugin.claimProfiles.get(upgrades.get(upgrade).claim_limit_profile)
								.getLimit(buildObjectTemplate.getClaimObjectCategory())) {
							ExtendedCombatMessages.sendErrorMessage(playerOid, "You have reached your objects limit for category "+RequirementChecker.getNameEditorOptionChoice("Claim Object Category",buildObjectTemplate.getClaimObjectCategory()));
							return false;
							
						}
					} 
				} else {
					ExtendedCombatMessages.sendErrorMessage(playerOid,
							"You can't build object from category " + RequirementChecker.getNameEditorOptionChoice("Claim Object Category", buildObjectTemplate.getClaimObjectCategory()) + " in this claim");
					return false;

				}
			} else {
				// profile not assign
			}
		} else {
			if (getProfile() > 0) {
				if (Log.loggingDebug)Log.debug("buildClaimObject:  limit=" + VoxelPlugin.claimProfiles.get(getProfile()).getLimit(buildObjectTemplate.getClaimObjectCategory()));
				if (VoxelPlugin.claimProfiles.get(getProfile()).getLimit(buildObjectTemplate.getClaimObjectCategory()) >= 0) {
					if (Log.loggingDebug)	Log.debug("buildClaimObject:  building with category =" + claimObjectsList.get(buildObjectTemplate.getClaimObjectCategory()));
					if (claimObjectsList.containsKey(buildObjectTemplate.getClaimObjectCategory())) {
						if (claimObjectsList.get(buildObjectTemplate.getClaimObjectCategory()) >= VoxelPlugin.claimProfiles.get(getProfile()).getLimit(buildObjectTemplate.getClaimObjectCategory())) {
							ExtendedCombatMessages.sendErrorMessage(playerOid, "You have reached your objects limit for category "+RequirementChecker.getNameEditorOptionChoice("Claim Object Category",buildObjectTemplate.getClaimObjectCategory()));
							return false;
						}
					} 
				} else {
					ExtendedCombatMessages.sendErrorMessage(playerOid,
							"You can't build object from category " + RequirementChecker.getNameEditorOptionChoice("Claim Object Category", buildObjectTemplate.getClaimObjectCategory()) + " in this claim");
					return false;

				}
			} else {
				// profile not assign
			}
		}
		
		// First verify the user has the right weapon equipped, is close enough and has
		// the items required
		if (!hasRequiredWeapon(playerOid, buildObjectTemplate)) {
			if (buildObjectTemplate.weaponReq.startsWith("a") || buildObjectTemplate.weaponReq.startsWith("e") || buildObjectTemplate.weaponReq.startsWith("i") || buildObjectTemplate.weaponReq.startsWith("o")
					|| buildObjectTemplate.weaponReq.startsWith("u")) {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "An " + buildObjectTemplate.weaponReq + " is required to build this object");
			} else {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "A " + buildObjectTemplate.weaponReq + " is required to build this object");
			}
			return false;
		}
		Log.debug("buildClaimObject ||");

		if (!isCloseEnough(playerOid, buildObjectTemplate, loc)) {
			ExtendedCombatMessages.sendErrorMessage(playerOid, "You are too far away from the object to build it");
			return false;
		}
		Log.debug("buildClaimObject |||");

		LinkedList<Integer> components = new LinkedList<Integer>();
		LinkedList<Integer> componentCounts = new LinkedList<Integer>();
		for (int itemReq : buildObjectTemplate.getStage(0).itemReqs.keySet()) {
			components.add(itemReq);
			componentCounts.add(buildObjectTemplate.getStage(0).itemReqs.get(itemReq));
		}
		if (components.size() > 0) {
			boolean hasItems = AgisInventoryClient.checkComponents(playerOid, components, componentCounts);
			if (!hasItems) {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You do not have the required items to build this object");
				return false;
			}
		}
		Log.debug("buildClaimObject |V");

		// Check if the user has the required skill level
		int playerSkillLevel = ClassAbilityClient.getPlayerSkillLevel(playerOid, buildObjectTemplate.skill);
		if (buildObjectTemplate.skill > 0 && playerSkillLevel < buildObjectTemplate.skillLevelReq) {
			ExtendedCombatMessages.sendErrorMessage(playerOid, "You do not have the required skill level to build this object");
			return false;
		}
		Log.debug("buildClaimObject V");

		// Now create the build task
		ClaimTask task = new ClaimTask();
		task.StartBuildTask(buildObjectTemplate, loc, orient, parent, parents, itemID, itemOid, playerOid, this);
		if (task.startCreateClaimObject()) {
			if (Log.loggingDebug)	Log.debug("buildClaimObject add task for claim " + id + " size=" + tasks.size());
			tasks.add(task);
			Log.debug("buildClaimObject size=" + tasks.size());

			// Schedule the task completion
			if (buildObjectTemplate.getStages().get(0).buildTimeReq > 0) {
				if (buildObjectTemplate.getStages().get(0).progressGameObjects.size() > 2) {
					Set<Integer> s = buildObjectTemplate.getStages().get(0).progressGameObjects.keySet();
					Iterator<Integer> i = s.iterator();
					i.next();
					int n = i.next();
					double t = n / 100D;
					long time = (long) Math.round(buildObjectTemplate.getStages().get(0).buildTimeReq * t / task.cObject.timeModifier * 1000D);
					if (buildObjectTemplate.buildTaskFixedTime()) {
						time = (long) Math.round(buildObjectTemplate.getStages().get(0).buildTimeReq * t * 1000D);
					}
					if (Log.loggingDebug)Log.debug("ClaimTask Create Time=" + time + " next progress " + n + " buildTimeReq=" + buildObjectTemplate.getStages().get(0).buildTimeReq + " timeModifier=" + task.cObject.timeModifier + "fixedTime="
							+ buildObjectTemplate.buildTaskFixedTime());
					task.scheduledFuture = Engine.getExecutor().schedule(task, time, TimeUnit.MILLISECONDS);
					if (buildObjectTemplate.buildTaskFixedTime()) {
						task.sendStartBuildTask((float) (buildObjectTemplate.getStages().get(0).buildTimeReq));
					} else {
						task.sendStartBuildTask((float) (buildObjectTemplate.getStages().get(0).buildTimeReq / task.cObject.timeModifier));
					}
				} else {
					long time = (long) Math.round(buildObjectTemplate.getStages().get(0).buildTimeReq / task.cObject.timeModifier * 1000D);
					if (buildObjectTemplate.buildTaskFixedTime()) {
						time = (long) Math.round(buildObjectTemplate.getStages().get(0).buildTimeReq * 1000D);
					}
					if (Log.loggingDebug)Log.debug("ClaimTask Create Time" + time + " next progress 100 buildTimeReq=" + buildObjectTemplate.getStages().get(0).buildTimeReq + " timeModifier=" + task.cObject.timeModifier + "fixedTime="
							+ buildObjectTemplate.buildTaskFixedTime());
					task.scheduledFuture = Engine.getExecutor().schedule(task, time, TimeUnit.MILLISECONDS);
					if (buildObjectTemplate.buildTaskFixedTime()) {
						task.sendStartBuildTask((float) (buildObjectTemplate.getStages().get(0).buildTimeReq));
					} else {
						task.sendStartBuildTask((float) (buildObjectTemplate.getStages().get(0).buildTimeReq / task.cObject.timeModifier));
					}
				}
				return true;
			} else {
				task.run();
				return false;
			}
		}
		return false;
	}

	/**
	 * Add an object to the claim. The data is stored in the database and sent down
	 * to all players within the draw/reaction radius.
	 * 
	 * @param task
	 */
	public boolean addClaimObject(ClaimTask task) {
		// Remove item from players inventory

		Log.debug("BUILD: adding claim object from task");
		// Add to list
		ClaimObject claimObject = new ClaimObject();
		claimObject.templateId = task.template.id;
		claimObject.gameObject = task.template.getStage(0).getProgressGameObject().get(task.buildProgress);
		claimObject.loc = task.loc.sub(this.loc);
		claimObject.orient = task.orient;
		claimObject.parent = task.parent;
		claimObject.parents = task.parents;
		claimObject.stage = 0;
		claimObject.health = 0;
		claimObject.lastTimeUpdate = System.currentTimeMillis();
		claimObject.totalTime = Math.round(task.template.getStage(0).buildTimeReq * 1000);
		if (task.template.interactionType == null || !task.template.interactionType.equals("Resource")) {
			claimObject.itemID = task.itemID;
		}

		// Remove items from the creators inventory
		HashMap<Integer, Integer> itemsToRemove = new HashMap<Integer, Integer>();
		for (Integer item : task.template.getStage(0).getItemReqs().keySet()) {
			if (item > 0) {
				itemsToRemove.put(item, task.template.getStage(0).getItemReqs().get(item));
			}
		}
		if (itemsToRemove.size() > 0) {
			AgisInventoryClient.removeGenericItems(task.playerOid, itemsToRemove, false);
		} else {
			Log.debug("BUILD: no generics items to remove");
		}
		// Set itemReqs for next level
		HashMap<Integer, Integer> itemReqs = new HashMap<Integer, Integer>();
		if (task.template.getStages().size() > 1) {
			// Max health should be the maxHealth of all future items combined (if there are
			// any)
			claimObject.maxHealth = task.template.getStage(1).health;
			for (int itemReq : task.template.getStages().get(1).getItemReqs().keySet()) {
				itemReqs.put(itemReq, task.template.getStages().get(1).getItemReqs().get(itemReq));
			}
		} else {
			claimObject.maxHealth = 0;
			//claimObject.complete = true;
			// FIXME claimObject.complete =true

		}

		Log.debug("addClaimObject itemReqs=" + itemReqs);
		claimObject.itemReqs = itemReqs;
		claimObject.lockTemplateId = 0;

		objects.add(claimObject);
		// Save action to the database
		claimObject.id = cDB.writeClaimObject(id, task.template.id, claimObject.stage, claimObject.complete, claimObject.parent, claimObject.gameObject, claimObject.loc, claimObject.orient, task.itemID,
				claimObject.state, claimObject.health, claimObject.maxHealth, claimObject.itemReqs, claimObject.lockTemplateId, claimObject.parents, claimObject.lastTimeUpdate,task.playerOid);
		highestObjectID = claimObject.id;
		// Send the object down to all in the radius

		task.cObject = claimObject;

		int plyBuildSpeed = CombatClient.getPlayerStatValue(task.playerOid, VoxelPlugin.BUILD_SPEED_STAT);
		// (Integer) EnginePlugin.getObjectProperty(task.playerOid,
		// CombatClient.NAMESPACE, VoxelPlugin.BUILD_SPEED_STAT);
		claimObject.users.put(task.playerOid, plyBuildSpeed);

		Log.debug("BUILD: adding claim object from task End");
		return true;
	}

	public void completeStage(ClaimTask task) {
		Log.debug("completeStage");
		// Now handle any additional interaction mechanics
		if (task.template.stages.get(task.cObject.stage).getInteractionType() == null) {
			return;
		}
		if (Log.loggingDebug)Log.debug("completeStage | " + task.template.stages.get(task.cObject.stage).getInteractionType());

		// TODO: When chest object has locks add them here
		if (task.template.stages.get(task.cObject.stage).getInteractionType().equals("Chest")) {
			int storageSize = task.template.stages.get(task.cObject.stage).getInteractionID();
			OID storageOid = null;
			if (task.cObject.stage > 0 && task.template.stages.get(task.cObject.stage - 1).getInteractionType().equals("Chest")) {
				AgisInventoryClient.updateStorageSize(task.playerOid, OID.fromString(task.cObject.state), storageSize);
			} else {
				if (task.template.lockable) {
					storageOid = AgisInventoryClient.createStorage(task.playerOid, "Claim_" + id + "_" + task.cObject.id, storageSize, task.template.lockLimit, false);
				} else {
					storageOid = AgisInventoryClient.createStorage(task.playerOid, "Claim_" + id + "_" + task.cObject.id, storageSize, false);
				}
			}
			if (Log.loggingDebug)
				Log.debug("CHEST: got storageOid: " + storageOid);
			if (storageOid != null) {
				task.cObject.state = storageOid.toString();
				cDB.updateClaimObjectState(task.cObject.id, task.cObject.templateId, task.cObject.stage, task.cObject.complete, task.cObject.state, task.cObject.gameObject, task.cObject.health, task.cObject.maxHealth,
						task.cObject.itemReqs);
			}
		} else if (task.template.stages.get(task.cObject.stage).getInteractionType().equals("NPC")) {
			if (task.cObject.complete) {
				Log.debug("BUILD: object is of type NPC");
				int spawnID = task.template.stages.get(task.cObject.stage).getInteractionID();
				MobDatabase mDB = new MobDatabase(false);
				SpawnData sd = mDB.loadSpecificSpawnData(spawnID);
				sd.setName(generateSpawnName(task.cObject));
				sd.setInstanceOid(instanceOID);
				sd.setLoc(new Point(AOVector.add(task.cObject.loc, loc)));
				sd.setOrientation(task.orient);
				AgisMobClient.spawnMob(sd);
				Log.debug("BUILD: sent Spawn Mob message");
			}
		}
		Log.debug("completeStage ENd");

	}

	String generateSpawnName(ClaimObject cObject) {
		return "Claim" + id + "Object" + cObject.id;
	}

	public boolean interruptBuildTask(OID playerOid) {
		if (Log.loggingDebug)Log.debug("interruptBuildTask playerOid=" + playerOid);
		/*
		 * ClaimTask task = getPlayersBuildTask(playerOid); if (task != null &&
		 * task.reqPlayer) {
		 * 
		 * task.interrupt(playerOid); // Remove task from list // tasks.remove(task);
		 * return true; }
		 */

		for (ClaimTask task : tasks) {
			if(task.taskType == TaskType.REPAIR)
				continue;
			if (task != null && task.cObject!=null) {
				if (Log.loggingDebug)Log.debug("interruptBuildTask playerOid=" + playerOid  + " task.reqPlayer=" + task.reqPlayer + " task.player=" + task.playerOid
						+ " users " + task.cObject.users);
			}
			if (task != null && task.reqPlayer) {
				if ((task.playerOid != null && task.playerOid.equals(playerOid)) || task.cObject.users.containsKey(playerOid)) {
					task.removePlayerFromBuildTask(playerOid);
				}
			}else if (task != null && !task.reqPlayer) {
				if ((task.playerOid != null && !task.playerOid.equals(playerOid)) && task.cObject.users.containsKey(playerOid)) {
					task.removePlayerFromBuildTask(playerOid);
				}
			}
		}
		return false;
	}

	public void changeStatBuildTask(OID playerOid, int value) {
		//Log.error("changeStatBuildTask playerOid=" + playerOid + " value=" + value + " " + tasks.size() + " claim id " + id);
		for (ClaimTask task : tasks) {
			if(task.taskType == TaskType.REPAIR)
				continue;
			//Log.error("changeStatBuildTask task=" + task);
		/*	if (task != null)
				Log.error("changeStatBuildTask reqPlayer=" + task.reqPlayer);*/
			if (task != null) {
			//	Log.error("changeStatBuildTask task playerOid=" + task.playerOid + " in users? " + task.cObject.users.containsKey(playerOid));
				if ((task.playerOid != null && task.playerOid.equals(playerOid)) || task.cObject.users.containsKey(playerOid)) {
					task.modPlayerForBuildTask(playerOid, value);
				}
			}
		}
	}

	private ClaimTask getPlayersBuildTask(OID playerOid) {
		for (ClaimTask task : tasks) {
			if (task.playerOid != null && task.playerOid.equals(playerOid) && task.reqPlayer) {
				return task;
			}
		}
		return null;
	}

	private ClaimTask getObjectBuildTask(int objectID) {
		for (ClaimTask task : tasks) {
			if (task.cObject.id == objectID && !task.reqPlayer) {
				return task;
			}
		}
		return null;
	}

	public void sendObject(ClaimObject claimObject) {
		Log.debug("BUILD: sendObject Start");
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		BuildObjectTemplate tmpl = VoxelPlugin.GetBuildObjectTemplate(claimObject.templateId);
		props.put("ext_msg_subtype", "claim_object");
		props.put("id", claimObject.id);
		props.put("templateID", claimObject.templateId);
		props.put("gameObject", claimObject.gameObject);
		props.put("status", claimObject.status);
		props.put("loc", claimObject.loc);
		ClaimUpgrade cu = upgrades.get(getUpgrade());
		if (cu != null) {
			props.put("diffloc", cu.loc);
		} else {
			props.put("diffloc", new AOVector());
		}
		props.put("orient", claimObject.orient);
		props.put("state", claimObject.state);
		props.put("maxHealth", claimObject.maxHealth);
		props.put("health", claimObject.health);
		float healthP = 100;
		if(claimObject.maxHealth>0)
			healthP = claimObject.health * 100f/ claimObject.maxHealth;
		if (Log.loggingDebug)Log.debug("send claim_object "+claimObject.health+" / "+claimObject.maxHealth+" healthP="+healthP+" stage="+claimObject.stage+" "+tmpl.getStages().get(claimObject.stage).getDamagedGameObject());
		int maxDamagePrefab = -1;
		if (healthP < 100) {
			for (int p : tmpl.getStages().get(claimObject.stage).getDamagedGameObject().keySet()) {
				if (p > maxDamagePrefab && maxDamagePrefab < healthP) {
					maxDamagePrefab = p;
				}
			}
		}
		if (Log.loggingDebug)Log.debug("send claim_object maxDamagePrefab="+maxDamagePrefab);
		if (maxDamagePrefab > 0) {
			props.put("damage", tmpl.getStages().get(claimObject.stage).getDamagedGameObject().get(maxDamagePrefab));
		} else {
			props.put("damage", "");
		}
		
		props.put("solo",tmpl.buildTaskSolo());
		props.put("complete", claimObject.complete);
		if (Log.loggingDebug)Log.debug("sendObject claimObject.finalStage=" + claimObject.finalStage);
		props.put("fstage", claimObject.finalStage);
		props.put("interactionType", tmpl.getStages().get(claimObject.stage).interactionType);
		props.put("attackable", tmpl.getAttackable());
		props.put("repairable", tmpl.getRepairable());
		props.put("lockTemplateID", claimObject.lockTemplateId);
		props.put("lockDurability", claimObject.lockDurability);
		props.put("tTime", claimObject.totalTime);
		props.put("cTime", claimObject.currentTime);
		props.put("sTime", claimObject.timeModifier);
		props.put("buildRun", claimObject.building);
		if (claimObject.totalTime == 0) {
			props.put("dTime", 0L);
		} else {
			props.put("dTime", Math.round((System.currentTimeMillis() - claimObject.lastTimeUpdate) * claimObject.timeModifier));
		}

		props.put("claimID", id);
		// Loop through players in range and send them the update
		// OID instanceOid = WorldManagerClient.getObjectInfo(giverOid).instanceOid;
		if (Log.loggingDebug)	Log.debug("sendObject: props=" + props);
		if (instancePlayersInRange != null && instanceOID != null && instancePlayersInRange.size() > 0) {
			if (instancePlayersInRange.containsKey(instanceOID)) {
				for (OID playerOid : instancePlayersInRange.get(instanceOID)) {
					// for (OID playerOid : playersInRange)
					if (Log.loggingDebug)
						Log.debug("BUILD: sendObject playerOid=" + playerOid);
					TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
					Engine.getAgent().sendBroadcast(msg);
					// Add to mapping of last action sent to the player
					playersLastObjectIDSent.put(playerOid, claimObject.id);
				}
			}
		}
		Log.debug("BUILD: sendObject End");
	}

	public OID getChestStorageKey(int objectID) {
		for (ClaimObject cObj : objects) {
			if (cObj.id == objectID) {
				return OID.fromString(cObj.state);
			}
		}
		return null;
	}

	public ClaimObject getClaimObject(int objectID) {
		ClaimObject cObject = null;
		for (ClaimObject cObj : objects) {
			if (cObj.id == objectID) {
				cObject = cObj;
				break;
			}
		}
		return cObject;
	}

	private ArrayList<ClaimObject> getChaildObjects(ClaimObject co) {
		ArrayList<ClaimObject> objs = new ArrayList<ClaimObject>();
		if (Log.loggingDebug)Log.debug("getChaildObjects co objId=" + co.id);
		for (ClaimObject obj : objects) {
			if (Log.loggingDebug)Log.debug("getChaildObjects objId=" + obj.id + " parent=" + obj.parent);

			if (obj.parent > 0) {
				if (obj.parent == co.id) {
					objs.add(obj);
					objs.addAll(getChaildObjects(obj));
				}
			} else {
				ArrayList<Integer> parents = obj.getParents();
				if (Log.loggingDebug)	Log.debug("getChaildObjects objId=" + obj.id + " parents=" + parents + " parents str=" + obj.parents);
				if (parents.contains(co.id)) {
					if (parents.size() == 1) {
						objs.add(obj);
						objs.addAll(getChaildObjects(obj));
					} else {
						boolean found = false;
						for (Integer i : parents) {
							if (i != co.id) {
								ClaimObject _co = getClaimObject(i);
								if (Log.loggingDebug)	Log.debug("getChaildObjects _co=" + _co);
								if (_co != null) {
									found = true;
									break;
								}
							}
						}
						if (Log.loggingDebug)Log.debug("getChaildObjects found=" + found);
						if (!found) {
							objs.add(obj);
							objs.addAll(getChaildObjects(obj));
						}
					}
				}
			}
		}
		if (Log.loggingDebug)Log.debug("getChaildObjects co objId=" + co.id + " " + objs.size());
		return objs;
	}

	public void removeClaimObject(OID playerOid, int objectID, boolean confirmed) {
		if (Log.loggingDebug)Log.debug("removeClaimObject playerOid="+playerOid+" objectID="+objectID+"  confirmed"+confirmed+ " claim="+id);
		// Get the claim object
		ClaimObject cObject = getClaimObject(objectID);

		if (cObject == null) {
			Log.error("removeClaimObject ClaimObject not found");
			return;
		}

		ArrayList<ClaimObject> objectsToRemove = new ArrayList<ClaimObject>();
		objectsToRemove.add(cObject);
		/*
		 * for (ClaimObject obj : objects) { if (obj.parent == objectID) {
		 * objectsToRemove.add(obj); } }
		 */
		objectsToRemove.addAll(getChaildObjects(cObject));
		if (objectsToRemove.size() > 1) {
			if (!confirmed) {
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "remove_claim_object_confirm");
				props.put("id", cObject.id);
				props.put("claimID", id);
				int c = 0;
				for (ClaimObject obj : objectsToRemove) {
					if (obj.id != cObject.id) {
						props.put("chaildId" + c, obj.id);
						c++;
					}
				}
				props.put("num", c);
				TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
				Engine.getAgent().sendBroadcast(msg);
				return;
			}

		}

		// First check if any items are chests that contains items - if so, do not
		// remove these
		for (ClaimObject obj : objectsToRemove) {
			BuildObjectTemplate tmpl = VoxelClient.getBuildingTemplate(obj.templateId);

			if (tmpl.stages.get(obj.stage).getInteractionType() != null && tmpl.stages.get(obj.stage).getInteractionType().equals("Chest")) {
				if (Log.loggingDebug)	Log.debug("removeClaimObject: obj.state="+obj.state);
				if(obj.state!=null && obj.state.length()>0)
				if (AgisInventoryClient.getStorageContents(playerOid, OID.fromString(obj.state)).size() > 0) {
					EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.STORAGE_NOT_EMPTY, 0, "");
					return;
				}
			}
		}

		// Next check if there is space to pick up the items
		HashMap<Integer, Integer> itemsToGivePlayer = new HashMap<Integer, Integer>();
		for (ClaimObject obj : objectsToRemove) {
			BuildObjectTemplate tmpl = VoxelClient.getBuildingTemplate(obj.templateId);
			if (obj.itemID > 0 && tmpl.buildTaskRequiresPlayer()) {
				if (itemsToGivePlayer.containsKey(obj.itemID)) {
					itemsToGivePlayer.put(obj.itemID, itemsToGivePlayer.get(obj.itemID) + 1);
				} else {
					itemsToGivePlayer.put(obj.itemID, 1);
				}
			}
			if (claimObjectsList.containsKey(tmpl.getClaimObjectCategory())) {
				if (claimObjectsList.get(tmpl.getClaimObjectCategory()) > 1) {
					claimObjectsList.put(tmpl.getClaimObjectCategory(), claimObjectsList.get(tmpl.getClaimObjectCategory()) - 1);
				} else {
					claimObjectsList.remove(tmpl.getClaimObjectCategory());
				}
			}
		}
		if (VoxelPlugin.USE_CLAIM_RESOURCES) {
			HashMap<Integer, Integer> leftOverItems = AgisInventoryClient.generateItems(playerOid, itemsToGivePlayer, false);
			for (int item : leftOverItems.keySet()) {
				alterResource(playerOid, item, itemsToGivePlayer.get(item));
			}
		} else {
			if (itemsToGivePlayer.size() > 0) {
				HashMap<Integer, Integer> leftOverItems = AgisInventoryClient.generateItems(playerOid, itemsToGivePlayer, true);
				if (!leftOverItems.isEmpty()) {
					EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INVENTORY_FULL, 0, "");
					return;
				}
				// AgisInventoryClient.generateItem(playerOid, itemID, "", 1, null);
			}
		}
		ArrayList<Integer> objectToDel = new ArrayList<Integer>();
		if (Log.loggingDebug)Log.debug("removeClaimObject objectsToRemove="+objectsToRemove.size());
		
		// Check for all children and remove them as well
		for (ClaimObject obj : objectsToRemove) {
			cancelTask(obj);
			sendRemoveObject(obj, objectID);
			objects.remove(obj);
			cDB.deleteClaimObject(obj.id);

			// Perform any special actions based on the objectType and data of the object
			BuildObjectTemplate tmpl = VoxelClient.getBuildingTemplate(obj.templateId);
			if (tmpl.stages.get(obj.stage).getInteractionType() != null && tmpl.stages.get(obj.stage).getInteractionType().equals("NPC")) {
				Log.debug("BUILD: object is of type NPC");
				AgisMobClient.deleteSpawnGenerator(generateSpawnName(cObject), instanceOID);
				Log.debug("BUILD: sent remove spawn message");
			}
			objectToDel.add(obj.id);
		}
		List<AtavismBuildingObject> objectsToAdd = new ArrayList<AtavismBuildingObject>();

		objectToDel.add(cObject.id);
		sendClaimData(playerOid);
		VoxelClient.AddDynamicObjects(instanceOID, objectToDel, objectsToAdd);
		Log.debug("removeClaimObject End");
		
	}

	private void sendRemoveObject(ClaimObject cObject, int objectID) {
		Log.debug("sendRemoveObject");
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "remove_claim_object");
		props.put("id", cObject.id);
		if (cObject.id == objectID) {
			props.put("dependent", false);
		} else {
			props.put("dependent", true);
		}
		props.put("claimID", id);
		// Loop through players in range and send them the update
		// OID instanceOid = WorldManagerClient.getObjectInfo(giverOid).instanceOid;
		if (Log.loggingDebug)Log.debug("sendRemoveObject props="+props);
		if (instancePlayersInRange.containsKey(instanceOID)) {
			for (OID playerOid : instancePlayersInRange.get(instanceOID)) {
				// for (OID playerOid : playersInRange) {
				try {
					TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
					Engine.getAgent().sendBroadcast(msg);
				} catch (Exception e) {
					Log.exception("sendRemoveObject " + cObject.id, e);
					e.printStackTrace();
				}
			}
		}else {
			Log.debug("No player in instance ");
		}
		Log.debug("sendRemoveObject END");
		
	}

	public void moveClaimObject(OID playerOid, int objectID, AOVector loc, Quaternion orient, int parent, boolean confirmed) {
		ClaimObject cObject = getClaimObject(objectID);
		if (cObject == null)
			return;
		ArrayList<ClaimObject> objectsToRemove = new ArrayList<ClaimObject>();
		ArrayList<Integer> objectToDel = new ArrayList<Integer>();

		objectsToRemove.addAll(getChaildObjects(cObject));
		if (objectsToRemove.size() > 0) {
			if (!confirmed) {
				Map<String, Serializable> props = new HashMap<String, Serializable>();
				props.put("ext_msg_subtype", "move_claim_object_confirm");
				props.put("id", cObject.id);
				props.put("claimID", id);
				props.put("loc", loc);
				props.put("orient", orient);
				int c = 0;
				for (ClaimObject obj : objectsToRemove) {
					if (obj.id != cObject.id) {
						props.put("chaildId" + c, obj.id);
						c++;
					}
				}
				props.put("num", c);
				TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
				Engine.getAgent().sendBroadcast(msg);
				return;
			}

			HashMap<Integer, Integer> itemsToGivePlayer = new HashMap<Integer, Integer>();
			for (ClaimObject obj : objectsToRemove) {
				BuildObjectTemplate tmpl = VoxelClient.getBuildingTemplate(obj.templateId);
				if (obj.itemID > 0 && tmpl.buildTaskRequiresPlayer()) {
					if (itemsToGivePlayer.containsKey(obj.itemID)) {
						itemsToGivePlayer.put(obj.itemID, itemsToGivePlayer.get(obj.itemID) + 1);
					} else {
						itemsToGivePlayer.put(obj.itemID, 1);
					}
				}
			}
			if (VoxelPlugin.USE_CLAIM_RESOURCES) {
				HashMap<Integer, Integer> leftOverItems = AgisInventoryClient.generateItems(playerOid, itemsToGivePlayer, false);
				for (int item : leftOverItems.keySet()) {
					alterResource(playerOid, item, itemsToGivePlayer.get(item));
				}
			} else {
				if (itemsToGivePlayer.size() > 0) {
					HashMap<Integer, Integer> leftOverItems = AgisInventoryClient.generateItems(playerOid, itemsToGivePlayer, true);
					if (!leftOverItems.isEmpty()) {
						EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INVENTORY_FULL, 0, "");
						return;
					}
					// AgisInventoryClient.generateItem(playerOid, itemID, "", 1, null);
				}
			}

			// Check for all children and remove them as well
			for (ClaimObject obj : objectsToRemove) {
				sendRemoveObject(obj, objectID);
				objects.remove(obj);
				cDB.deleteClaimObject(obj.id);

				// Perform any special actions based on the objectType and data of the object
				BuildObjectTemplate tmpl = VoxelClient.getBuildingTemplate(obj.templateId);
				if (tmpl.stages.get(obj.stage).getInteractionType() != null && tmpl.stages.get(obj.stage).getInteractionType().equals("NPC")) {
					Log.debug("BUILD: object is of type NPC");
					AgisMobClient.deleteSpawnGenerator(generateSpawnName(cObject), instanceOID);
					Log.debug("BUILD: sent remove spawn message");
				}
				objectToDel.add(obj.id);
			}

		}

		List<AtavismBuildingObject> objectsToAdd = new ArrayList<AtavismBuildingObject>();
		BuildObjectTemplate tmpl = VoxelClient.getBuildingTemplate(cObject.templateId);
		objectToDel.add(cObject.id);
		AtavismBuildingObject abo = new AtavismBuildingObject();
		if (tmpl != null) {
			AtavismBuildingColliders abc = tmpl.stages.get(cObject.stage).getProgressColliders().get(cObject.progress);
			if (abc != null) {
				abo.colliders = abc.colliders;
				abo.setId(cObject.id);
				abo.setPosition(loc);
				abo.setOrientation(cObject.orient);
				objectsToAdd.add(abo);
			}
		}

		// Send update of colliders to Dynamic Navmesh
		VoxelClient.AddDynamicObjects(instanceOID, objectToDel, objectsToAdd);

		sendRemoveObject(cObject, objectID);
		cObject.loc = loc.sub(this.loc);
		cObject.orient = orient;
		cObject.parent = parent;
		sendObject(cObject);
		/*
		 * Map<String, Serializable> props = new HashMap<String, Serializable>();
		 * props.put("ext_msg_subtype", "move_claim_object"); props.put("id",
		 * cObject.id); props.put("loc", cObject.loc); props.put("orient", orient);
		 * props.put("claimID", id); // Loop through players in range and send them the
		 * update //OID instanceOid =
		 * WorldManagerClient.getObjectInfo(giverOid).instanceOid;
		 * 
		 * for (OID plyOid : instancePlayersInRange.get(instanceOID)) { //for (OID
		 * playerOid : playersInRange) { TargetedExtensionMessage msg = new
		 * TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, plyOid,
		 * plyOid, false, props); Engine.getAgent().sendBroadcast(msg); }
		 */
		cDB.updateClaimObjectPosition(objectID, cObject.loc, orient, cObject.parent);

		// Perform any special actions based on the objectType and data of the object

		if (cObject.complete) {

			if (tmpl.stages.get(cObject.stage).getInteractionType() != null && tmpl.stages.get(cObject.stage).getInteractionType().equals("NPC")) {
				Log.debug("BUILD: object is of type NPC");
				AgisMobClient.deleteSpawnGenerator(generateSpawnName(cObject), instanceOID);
				Log.debug("BUILD: sent remove spawn message");
				// Now send the spawn mob message again
				int spawnID = tmpl.stages.get(cObject.stage).getInteractionID();
				MobDatabase mDB = new MobDatabase(false);
				SpawnData sd = mDB.loadSpecificSpawnData(spawnID);
				sd.setName(generateSpawnName(cObject));
				sd.setInstanceOid(instanceOID);
				sd.setLoc(new Point(AOVector.add(cObject.loc, this.loc)));
				sd.setOrientation(cObject.orient);
				AgisMobClient.spawnMob(sd);
				Log.debug("BUILD: sent Spawn Mob message");
			}
		}
	}

	public void updateClaimObjectState(int objectID, String state) {
		ClaimObject cObject = getClaimObject(objectID);
		if (cObject == null)
			return;

		cObject.state = state;
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "update_claim_object_state");
		props.put("id", cObject.id);
		props.put("state", cObject.state);
		props.put("claimID", id);
		// Loop through players in range and send them the update
		// OID instanceOid = WorldManagerClient.getObjectInfo(giverOid).instanceOid;

		for (OID playerOid : instancePlayersInRange.get(instanceOID)) {
			// for (OID playerOid : playersInRange) {
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
		}

		cDB.updateClaimObjectState(objectID, cObject.templateId, cObject.stage, cObject.complete, state, cObject.gameObject, cObject.health, cObject.maxHealth, cObject.itemReqs);
	}

	public void useHelpClaimObject(OID playerOid, int objectID) {
		if (Log.loggingDebug)	Log.debug("useClaimObject playerOid=" + playerOid + " objectID=" + objectID);
		ClaimObject cObject = getClaimObject(objectID);
		if (cObject == null) {
			Log.error("useClaimObject playerOid=" + playerOid + " objectID=" + objectID + " object is null");
			return;
		}
		BuildObjectTemplate tmpl = VoxelPlugin.GetBuildObjectTemplate(cObject.templateId);
		if (Log.loggingDebug)	Log.debug("useClaimObject playerOid=" + playerOid + " objectID=" + objectID + " cObject.totalTime=" + cObject.totalTime);
		if (cObject.totalTime > 0) {
			if (!tmpl.buildTaskSolo()) {
				for (ClaimTask task : tasks) {
					if (task != null) {
						if (task.cObject.id == objectID) {
							task.addPlayerToBuildTask(playerOid);

						}
					}
				}
			}
			Log.debug("useClaimObject return");
			return;
		}
	}
	
	
	
	
	public void useClaimObject(OID playerOid, int objectID) {
		if (Log.loggingDebug)Log.debug("useClaimObject playerOid=" + playerOid + " objectID=" + objectID);
		ClaimObject cObject = getClaimObject(objectID);
		if (cObject == null) {
			Log.error("useClaimObject playerOid=" + playerOid + " objectID=" + objectID + " object is null");
			return;
		}
		BuildObjectTemplate tmpl = VoxelPlugin.GetBuildObjectTemplate(cObject.templateId);
		if (Log.loggingDebug)Log.debug("useClaimObject playerOid=" + playerOid + " objectID=" + objectID + " cObject.totalTime=" + cObject.totalTime);
		if (cObject.totalTime > 0) {
			if (!tmpl.buildTaskSolo()) {
				for (ClaimTask task : tasks) {
					if (task != null) {
						if (task.cObject.id == objectID) {
							task.addPlayerToBuildTask(playerOid);

						}
					}
				}
			}
			Log.debug("useClaimObject return");
			return;
		}
		if (Log.loggingDebug)Log.debug("useClaimObject playerOid=" + playerOid + " objectID=" + objectID + " cObject.complete=" + cObject.complete);
		// OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid, WorldManagerClient.NAMESPACE, "accountId");
		OID accountID = Engine.getDatabase().getAccountOid(playerOid);
		if (getPlayerPermission(playerOid, accountID) < Claim.PERMISSION_INTERACTION) {
			ExtendedCombatMessages.sendErrorMessage(playerOid, "Insufficient permissions");
			Log.debug("CLAIM: Insufficient permissions");
			if (Log.loggingDebug)
				Log.debug("Claim: id=" + id + " accountID=" + accountID + " playerOid=" + playerOid + " dont have permission to use object");
			return;
		}

		if (Log.loggingDebug)Log.debug("useClaimObject cObject.stage=" + cObject.stage + " getInteractionType=" + tmpl.stages.get(cObject.stage).getInteractionType());

		/*if (tmpl.stages.get(cObject.stage).getInteractionType() == null || !tmpl.stages.get(cObject.stage).getInteractionType().equals("Chest")) {
			// Run permission check
			if (getPlayerPermission(playerOid, accountID) < Claim.PERMISSION_ADD_ONLY) {
				Log.debug("CLAIM: Insufficient permissions");
				return;
			}
		}*/
		if (Log.loggingDebug)Log.debug("useClaimObject | cObject.state" + cObject.state);
		if (tmpl.stages.get(cObject.stage).getInteractionType() != null && tmpl.stages.get(cObject.stage).getInteractionType().equals("Chest")) {
			Log.debug("useClaimObject chest");
			if (!cObject.complete) {
				return;
			}
			if (tmpl.lockable) {
				AgisInventoryClient.openNonPlayerStorage(playerOid, OID.fromString(cObject.state), tmpl.lockLimit);
			} else {
				AgisInventoryClient.openNonPlayerStorage(playerOid, OID.fromString(cObject.state));
			}
			Log.debug("useClaimObject chest end");
		} else if (tmpl.stages.get(cObject.stage).getInteractionType() != null && tmpl.stages.get(cObject.stage).getInteractionType().equals("Resource")) {
			if (!cObject.complete) {
				return;
			}
			// Loot items
			Random rand = new Random();
			HashMap<Integer, Integer> itemsToAdd = new HashMap<Integer, Integer>();
			LootTable lt = Agis.LootTableManager.get(tmpl.stages.get(cObject.stage).getInteractionID());
			for (int i = 0; i < lt.getItems().size(); i++) {
				if (rand.nextInt(100) <= lt.getItemChances().get(i)) {
					if (lt.getItems().get(i) > 0) {
						int count = lt.getRandomCountOfItem(i);
						itemsToAdd.put(lt.getItems().get(i), count);
						EventMessageHelper.SendInventoryEvent(playerOid, EventMessageHelper.ITEM_HARVESTED, lt.getItems().get(i), count, "");
					}
				}
			}
			if (!itemsToAdd.isEmpty()) {
				AgisInventoryClient.generateItems(playerOid, itemsToAdd, false);
			}

			// Delete the wheat
			removeClaimObject(playerOid, cObject.id, true);

		} else if (tmpl.stages.get(cObject.stage).getInteractionType() != null && tmpl.stages.get(cObject.stage).getInteractionType().equals("Effect")) {
			if (!cObject.complete) {
				return;
			}
			Log.debug("Claim: useClaimObject effect apply");
			CombatClient.applyEffect(playerOid, tmpl.stages.get(cObject.stage).getInteractionID(), tmpl.stages.get(cObject.stage).getInteractionData1());
		} else if (tmpl.stages.get(cObject.stage).getInteractionType() != null && tmpl.stages.get(cObject.stage).getInteractionType().equals("Instance")) {
			Log.debug("Claim: useClaimObject Instance apply");
			if (!cObject.complete) {
				Log.debug("Claim: useClaimObject not complete");
				return;
			}
			Log.debug("Claim: useClaimObject send change Instance ");
			AgisWorldManagerClient.sendChangeInstance(playerOid, tmpl.stages.get(cObject.stage).getInteractionID(), tmpl.stages.get(cObject.stage).getInteractionData1(), "Claim" + id);
		} else if (tmpl.stages.get(cObject.stage).getInteractionType() != null && tmpl.stages.get(cObject.stage).getInteractionType().equals("LeaveInstance")) {
			if (!cObject.complete) {
				return;
			}
			AgisWorldManagerClient.returnToLastInstance(playerOid);
		}
		Log.debug("useClaimObject END");
	}

	public boolean repairClaimObject(OID playerOid, int objectID) {
		if (Log.loggingDebug)
			Log.debug("CLAIM: repairClaimObject: playerOid:" + playerOid + " objectID:" + objectID );//+ " itemIDs:" + itemIDs + " itemOids:" + itemOids + " counts:" + counts);
		OID accountID = Engine.getDatabase().getAccountOid(playerOid);
		if (getPlayerPermission(playerOid, accountID) < Claim.PERMISSION_ADD_DELETE) {
			if (Log.loggingDebug)
				Log.debug("CLAIM: repairClaimObject: playerOid:" + playerOid + " objectID:" + objectID + " No Permission");
			return false;
		}

		if (getPlayersBuildTask(playerOid) != null) {
			ExtendedCombatMessages.sendErrorMessage(playerOid, "You cannot perform another task yet");
			if (Log.loggingDebug)
				Log.debug("CLAIM: repairClaimObject: playerOid:" + playerOid + " objectID:" + objectID + " You cannot perform another task yet");
			return false;
		}
		ClaimObject cObject = getClaimObject(objectID);
		if (cObject == null) {
			if (Log.loggingDebug)
				Log.debug("CLAIM: repairClaimObject: playerOid:" + playerOid + " objectID:" + objectID + " cObject null");
			return false;
		}
		// Verify the object doesn't have a task
		if (getObjectBuildTask(objectID) != null) {
			if (Log.loggingDebug)
				Log.debug("CLAIM: repairClaimObject: playerOid:" + playerOid + " objectID:" + objectID + " That object is already performing an action");
			ExtendedCombatMessages.sendErrorMessage(playerOid, "That object is already performing an action");
			return false;
		}

		BuildObjectTemplate tmpl = VoxelClient.getBuildingTemplate(cObject.templateId);
		if (tmpl == null) {
			if (Log.loggingDebug)
				Log.debug("CLAIM: repairClaimObject: playerOid:" + playerOid + " objectID:" + objectID + " template is null");

			return false;
		}
		Log.debug("CLAIM: repairClaimObject: |");
		// Verify the player has the required weapon and is close enough
		if (!hasRequiredWeapon(playerOid, tmpl)) {
			if (tmpl.weaponReq.startsWith("a") || tmpl.weaponReq.startsWith("e") || tmpl.weaponReq.startsWith("i") || tmpl.weaponReq.startsWith("o") || tmpl.weaponReq.startsWith("u")) {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "An " + tmpl.weaponReq + " is required to repair this object");
			} else {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "A " + tmpl.weaponReq + " is required to repair this object");
			}
			return false;
		}
		Log.debug("CLAIM: repairClaimObject: ||");
		if (!isCloseEnough(playerOid, tmpl, AOVector.add(loc, cObject.loc))) {
			ExtendedCombatMessages.sendErrorMessage(playerOid, "You are too far away from the object to repair it");
			return false;
		}
		if (cObject.health >= cObject.maxHealth) {
			ExtendedCombatMessages.sendErrorMessage(playerOid, "There is nothing to fix");
			return false;
		}
		ClaimTask task = new ClaimTask();
		float buildTime = 0;
		boolean suitableItems = false;
		ArrayList<ClaimObjectBuildItemEntry> itemEntries = new ArrayList<ClaimObjectBuildItemEntry>();

		float healthP = ((float) cObject.maxHealth - (float) cObject.health) / (float) cObject.maxHealth;

	/*	LinkedList<Integer> components = new LinkedList<Integer>();
		LinkedList<Integer> componentCounts = new LinkedList<Integer>();
		for (int itemReq : tmpl.getStage(cObject.stage).getItemReqs().keySet()) {
			int count = (int) Math.round(Math.ceil((tmpl.getStage(cObject.stage).getItemReqs().get(itemReq) * healthP)));
			if(count>0) {
				components.add(itemReq);
				componentCounts.add(count);
			}
		}
		if (Log.loggingDebug)
			Log.debug("repairClaimObject: components="+components+" componentCounts="+componentCounts);
		if (components.size() > 0) {
			boolean hasItems = AgisInventoryClient.checkComponents(playerOid, components, componentCounts);
			if (!hasItems) {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You do not have the required items to repair this object");
				return false;
			}
		}
		HashMap<Integer, Integer> itemsToRemove = new HashMap<Integer, Integer>();
		for (Integer item : tmpl.getStage(cObject.stage).getItemReqs().keySet()) {
			if (item > 0) {
				int count = (int) Math.round(Math.ceil(tmpl.getStage(cObject.stage).getItemReqs().get(item) * healthP));
				if (count > 0)
					itemsToRemove.put(item, count);
			}
		}
		if (Log.loggingDebug)
			Log.debug("repairClaimObject: itemsToRemove="+itemsToRemove);
		if(itemsToRemove.size()>0)
			AgisInventoryClient.removeGenericItems(playerOid, itemsToRemove, false);
*/
		task.StartRepairTask(tmpl, cObject, itemEntries, playerOid, this);
		tasks.add(task);
		if (Log.loggingDebug)
			Log.debug("repairClaimObject: getting repair time for stage: " + cObject.stage + " with num stages: " + tmpl.getStages().size());
		buildTime = tmpl.getStage(cObject.stage).repairTimeReq * healthP;
		cObject.totalTime = Math.round(buildTime * 1000);
		cObject.lastTimeUpdate = System.currentTimeMillis();
		cObject.timeModifier = 1;
		if (Log.loggingDebug)
			Log.debug("repairClaimObject: getting repair time for stage: " + cObject.stage + " repairTimereq=" + tmpl.getStage(cObject.stage).repairTimeReq + " " + healthP);

		if (Log.loggingDebug)Log.debug("repairClaimObject: repair buildTime=" + buildTime);
		// Schedule the task completion
		if (buildTime > 0) {
			task.sendStartBuildTask( buildTime);
			sendObject(cObject);
		task.scheduledFuture=	Engine.getExecutor().schedule(task, (long) buildTime * 1000, TimeUnit.MILLISECONDS);
			// task.sendStartBuildTask(buildTime);
			return true;
		} else {
			task.run();
			return false;
		}
	}
	
	
	/**
	 * Adds the specified item to a claim object with the aim of upgrading it. Kicks
	 * off a BuildTask if the right item is provided.
	 * 
	 * @param playerOid
	 * @param objectID
	 * @param itemID
	 * @param itemOid
	 * @param count
	 */
	public boolean addItemToUpgradeClaimObject(OID playerOid, int objectID, ArrayList<Integer> itemIDs, ArrayList<OID> itemOids, ArrayList<Integer> counts) {
		// Player cannot start a new task if they are currently on a task
		if (Log.loggingDebug)
			Log.debug("CLAIM: addItemToUpgradeClaimObject: playerOid:" + playerOid + " objectID:" + objectID + " itemIDs:" + itemIDs + " itemOids:" + itemOids + " counts:" + counts);

		// OID accountID = (OID) EnginePlugin.getObjectProperty(playerOid,
		// WorldManagerClient.NAMESPACE, "accountId");
		OID accountID = Engine.getDatabase().getAccountOid(playerOid);
		if (getPlayerPermission(playerOid, accountID) < Claim.PERMISSION_ADD_DELETE) {
			if (Log.loggingDebug)
				Log.debug("CLAIM: addItemToUpgradeClaimObject: playerOid:" + playerOid + " objectID:" + objectID + " No Permission");
			return false;
		}

		if (getPlayersBuildTask(playerOid) != null) {
			ExtendedCombatMessages.sendErrorMessage(playerOid, "You cannot perform another task yet");
			if (Log.loggingDebug)
				Log.debug("CLAIM: addItemToUpgradeClaimObject: playerOid:" + playerOid + " objectID:" + objectID + " You cannot perform another task yet");
			return false;
		}
		ClaimObject cObject = getClaimObject(objectID);
		if (cObject == null) {
			if (Log.loggingDebug)
				Log.debug("CLAIM: addItemToUpgradeClaimObject: playerOid:" + playerOid + " objectID:" + objectID + " cObject null");
			return false;
		}
		// Verify the object doesn't have a task
		if (getObjectBuildTask(objectID) != null) {
			if (Log.loggingDebug)
				Log.debug("CLAIM: addItemToUpgradeClaimObject: playerOid:" + playerOid + " objectID:" + objectID + " That object is already performing an action");
			ExtendedCombatMessages.sendErrorMessage(playerOid, "That object is already performing an action");
			return false;
		}

		BuildObjectTemplate tmpl = VoxelClient.getBuildingTemplate(cObject.templateId);
		if (tmpl == null) {
			if (Log.loggingDebug)
				Log.debug("CLAIM: addItemToUpgradeClaimObject: playerOid:" + playerOid + " objectID:" + objectID + " template is null");

			return false;
		}
		Log.debug("CLAIM: addItemToUpgradeClaimObject: |");
		// Verify the player has the required weapon and is close enough
		if (!hasRequiredWeapon(playerOid, tmpl)) {
			if (tmpl.weaponReq.startsWith("a") || tmpl.weaponReq.startsWith("e") || tmpl.weaponReq.startsWith("i") || tmpl.weaponReq.startsWith("o") || tmpl.weaponReq.startsWith("u")) {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "An " + tmpl.weaponReq + " is required to repair this object");
			} else {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "A " + tmpl.weaponReq + " is required to repair this object");
			}
			return false;
		}
		Log.debug("CLAIM: addItemToUpgradeClaimObject: ||");
		if (!isCloseEnough(playerOid, tmpl, AOVector.add(loc, cObject.loc))) {
			ExtendedCombatMessages.sendErrorMessage(playerOid, "You are too far away from the object to repair it");
			return false;
		}
		
			
		if (cObject.state != null) {
			if (tmpl.stages.get(cObject.stage).getInteractionType() != null && tmpl.stages.get(cObject.stage).getInteractionType().equals("Chest")) {
				if (!tmpl.stages.get(cObject.stage + 1).getInteractionType().equals("Chest")) {
					// Also need to send items that are in a chest
					AgisInventoryClient.sendStorageItemsAsMail(owner, OID.fromString(cObject.state));
				}
			}
		}
		
		/*
		 if (tmpl.stages.get(cObject.stage).getInteractionType() != null && tmpl.stages.get(cObject.stage).getInteractionType().equals("Chest")) {
			if (AgisInventoryClient.getStorageContents(playerOid, OID.fromString(cObject.state)).size() > 0) {
				EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.STORAGE_NOT_EMPTY, 0, "");
				return false;
			}
		}*/

		// Is this a repair job?
		boolean repairJob = false;
		/*
		 * if (cObject.complete && (cObject.health < cObject.maxHealth ||
		 * cObject.stage+1 < tmpl.getStages().size())) repairJob = true;
		 */

		// Check if there is another stage (for non repair jobs)
		if (!repairJob && (cObject.stage + 1) >= tmpl.getStages().size()) {
			ExtendedCombatMessages.sendErrorMessage(playerOid, "That object cannot be upgraded");
			return false;
		}
		if (Log.loggingDebug)Log.debug("CLAIM: addItemToUpgradeClaimObject: ||| repairJob=" + repairJob);
		ClaimTask task = new ClaimTask();
		float buildTime = 0;

		if (repairJob) {
			boolean suitableItems = false;
			ArrayList<ClaimObjectBuildItemEntry> itemEntries = new ArrayList<ClaimObjectBuildItemEntry>();
			for (int i = 0; i < itemIDs.size(); i++) {
				if (tmpl.getStage(cObject.stage + 1).getItemReqs().containsKey(itemIDs.get(i)) && tmpl.getStage(cObject.stage + 1).getItemReqs().get(itemIDs.get(i)) > 0) {
					int itemCount = counts.get(i);
					if (itemCount > tmpl.getStage(cObject.stage + 1).getItemReqs().get(itemIDs.get(i)))
						itemCount = tmpl.getStage(cObject.stage + 1).getItemReqs().get(itemIDs.get(i));
					ClaimObjectBuildItemEntry itemEntry = new ClaimObjectBuildItemEntry(itemIDs.get(i), itemOids.get(i), itemCount);
					itemEntries.add(itemEntry);
					suitableItems = true;
				}
			}
			if (suitableItems) {
				task.StartRepairTask(tmpl, cObject, itemEntries, playerOid, this);
				tasks.add(task);
				if (Log.loggingDebug)
					Log.debug("BUILD: getting repair time for stage: " + cObject.stage + " with num stages: " + tmpl.getStages().size());
				buildTime = tmpl.getStage(cObject.stage).buildTimeReq;
			} else {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "That item cannot be used on this object");
				return false;
			}
		} else {
			boolean suitableItems = false;
			ArrayList<ClaimObjectBuildItemEntry> itemEntries = new ArrayList<ClaimObjectBuildItemEntry>();

			if (Log.loggingDebug)
				Log.debug("CLAIM: Upgrade itemReqs:" + tmpl.getStage(cObject.stage + 1).getItemReqs() + "; itemIDs:" + itemIDs + "; counts:" + counts);
			for (int i = 0; i < itemIDs.size(); i++) {
				if (tmpl.getStage(cObject.stage + 1).getItemReqs().containsKey(itemIDs.get(i)) && tmpl.getStage(cObject.stage + 1).getItemReqs().get(itemIDs.get(i)) > 0) {
					int itemCount = counts.get(i);
					if (itemCount > tmpl.getStage(cObject.stage + 1).getItemReqs().get(itemIDs.get(i)))
						itemCount = tmpl.getStage(cObject.stage + 1).getItemReqs().get(itemIDs.get(i));
					ClaimObjectBuildItemEntry itemEntry = new ClaimObjectBuildItemEntry(itemIDs.get(i), itemOids.get(i), itemCount);
					itemEntries.add(itemEntry);
					suitableItems = true;
				} else {
					EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INVALID_ITEM, itemIDs.get(i), "");
					return false;
				}
			}

			if (Log.loggingDebug)	Log.debug("CLAIM: Upgrade suitableItems=" + suitableItems+"  UPGRADE_CLAIM_OBJECT_ITEMS_FROM_INVENTORY="+ VoxelPlugin.UPGRADE_CLAIM_OBJECT_ITEMS_FROM_INVENTORY+" ONLY_UPGRADE_CLAIM_OBJECT_WITH_ALL_ITEMS="+VoxelPlugin.ONLY_UPGRADE_CLAIM_OBJECT_WITH_ALL_ITEMS);
			if (suitableItems || VoxelPlugin.UPGRADE_CLAIM_OBJECT_ITEMS_FROM_INVENTORY) {
				// If ONLY_UPGRADE_WITH_ALL_ITEMS is set to true, make sure all items are
				// present
				if (!VoxelPlugin.UPGRADE_CLAIM_OBJECT_ITEMS_FROM_INVENTORY) {
					if (VoxelPlugin.ONLY_UPGRADE_CLAIM_OBJECT_WITH_ALL_ITEMS) {
						for (int itemID : cObject.itemReqs.keySet()) {
							if (!itemIDs.contains(itemID)) {
								EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INSUFFICIENT_CLAIM_OBJECT_ITEMS, itemID, "");
								return false;
							} else {
								for (ClaimObjectBuildItemEntry itemEntry : itemEntries) {
									if (itemEntry.itemID == itemID && itemEntry.count < cObject.itemReqs.get(itemID)) {
										EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INSUFFICIENT_CLAIM_OBJECT_ITEMS, itemID, "");
										return false;
									}
								}
							}
						}
					}
					if (!tmpl.buildTaskRequiresPlayer()) {
						// Remove item from players inventory as the process starts
						for (ClaimObjectBuildItemEntry itemEntry : itemEntries) {
							if (itemEntry.itemOid != null) {
								AgisInventoryClient.removeSpecificItem(playerOid, itemEntry.itemOid, false, itemEntry.count);
							}
						}
					}
				} else {
					LinkedList<Integer> components = new LinkedList<Integer>();
					LinkedList<Integer> componentCounts = new LinkedList<Integer>();
					for (int itemReq : tmpl.getStage(cObject.stage + 1).getItemReqs().keySet()) {
						components.add(itemReq);
						componentCounts.add(tmpl.getStage(cObject.stage + 1).getItemReqs().get(itemReq));
					}
					if (Log.loggingDebug)Log.debug("CLAIM: addItemToUpgradeClaimObject:  to check components="+components+" componentCounts="+componentCounts);
					boolean hasItems = AgisInventoryClient.checkComponents(playerOid, components, componentCounts);
					if (!hasItems) {
						ExtendedCombatMessages.sendErrorMessage(playerOid, "You do not have the required items to build this object");
						return false;
					}
					if (Log.loggingDebug)Log.debug("CLAIM: addItemToUpgradeClaimObject:  to check tmpl.buildTaskRequiresPlayer()="+tmpl.buildTaskRequiresPlayer());
					
					if (!tmpl.buildTaskRequiresPlayer()) {
						HashMap<Integer, Integer> itemsToRemove = new HashMap<Integer, Integer>();
						for (Integer item : tmpl.getStage(cObject.stage + 1).getItemReqs().keySet()) {
							if (item > 0) {
								itemsToRemove.put(item, tmpl.getStage(cObject.stage + 1).getItemReqs().get(item));
							}
						}
						if (Log.loggingDebug)Log.debug("CLAIM: addItemToUpgradeClaimObject:  itemsToRemove="+itemsToRemove);
						
						AgisInventoryClient.removeGenericItems(playerOid, itemsToRemove, false);
					}
				}
				Log.debug("CLAIM: Upgrade |");
				
				
				// Schedule the task completion
				if (tmpl.getStages().get(cObject.stage + 1).buildTimeReq > 0) {
					if (tmpl.getStages().get(cObject.stage + 1).progressGameObjects.size() > 2) {
						Set<Integer> s = tmpl.getStages().get(cObject.stage + 1).progressGameObjects.keySet();
						Iterator<Integer> i = s.iterator();
						i.next();
						int n = i.next();
						double t = n / 100D;
						
						cObject.currentTime = 0L;
						cObject.totalTime = Math.round(tmpl.getStage(cObject.stage + 1).buildTimeReq * 1000);
						cObject.lastTimeUpdate = System.currentTimeMillis();	
						long endTime = (System.currentTimeMillis() +  cObject.totalTime );
						task.StartUpgradeTask(tmpl, cObject, itemEntries, playerOid, tmpl.buildTaskRequiresPlayer(), endTime, this);
						long time = (long) Math.round(tmpl.getStages().get(cObject.stage + 1).buildTimeReq * t / cObject.timeModifier * 1000D);
						if (tmpl.buildTaskFixedTime()) {
							time = (long) Math.round(tmpl.getStages().get(cObject.stage + 1).buildTimeReq * t * 1000D);
						}
						if (Log.loggingDebug)	Log.debug("ClaimTask Create Time=" + time + " next progress " + n 
								+ " buildTimeReq=" + tmpl.getStages().get(cObject.stage + 1).buildTimeReq + " timeModifier=" + cObject.timeModifier + "fixedTime="
								+ tmpl.buildTaskFixedTime());
						buildTime =time;
						//long endTime = (System.currentTimeMillis() + time );
						task.scheduledFuture = Engine.getExecutor().schedule(task, time, TimeUnit.MILLISECONDS);
						if (tmpl.buildTaskFixedTime()) {
							task.sendStartBuildTask((float) (tmpl.getStages().get(cObject.stage + 1).buildTimeReq));
						} else {
							task.sendStartBuildTask((float) (tmpl.getStages().get(cObject.stage + 1).buildTimeReq / cObject.timeModifier));
						}
					} else {
					
						cObject.currentTime = 0L;
						cObject.totalTime = Math.round(tmpl.getStage(cObject.stage + 1).buildTimeReq * 1000);
						cObject.lastTimeUpdate = System.currentTimeMillis();
						long endTime = (System.currentTimeMillis() +  cObject.totalTime );
						task.StartUpgradeTask(tmpl, cObject, itemEntries, playerOid, tmpl.buildTaskRequiresPlayer(), endTime, this);
						long time = (long) Math.round(tmpl.getStages().get(cObject.stage + 1).buildTimeReq / cObject.timeModifier * 1000D);
						if (tmpl.buildTaskFixedTime()) {
							time = (long) Math.round(tmpl.getStages().get(cObject.stage + 1).buildTimeReq * 1000D);
						}
						if (Log.loggingDebug)	Log.debug("ClaimTask Create Time" + time + " next progress 100 buildTimeReq=" + tmpl.getStages().get(cObject.stage + 1).buildTimeReq + " timeModifier=" + cObject.timeModifier + "fixedTime="
								+ tmpl.buildTaskFixedTime());
						
						buildTime =time;
						task.scheduledFuture = Engine.getExecutor().schedule(task, time, TimeUnit.MILLISECONDS);
						if (tmpl.buildTaskFixedTime()) {
							task.sendStartBuildTask((float) (tmpl.getStages().get(cObject.stage + 1).buildTimeReq));
						} else {
							task.sendStartBuildTask((float) (tmpl.getStages().get(cObject.stage + 1).buildTimeReq / cObject.timeModifier));
						}
					}
					
				}
				
				/*if (Log.loggingDebug)
					Log.error("BUILD: got buildTime: " + buildTime + " creates endTime: " + endTime + " with current: " + System.currentTimeMillis());
				*/
				tasks.add(task);
			} else {
				ExtendedCombatMessages.sendErrorMessage(playerOid, "That item cannot be used on this object");
				return false;
			}
		}
		if (Log.loggingDebug)Log.debug("CLAIM: Upgrade buildTime=" + buildTime);
		// Schedule the task completion
		if (buildTime > 0) {
			//task.scheduledFuture = Engine.getExecutor().schedule(task, (long) buildTime * 1000, TimeUnit.MILLISECONDS);
			//task.sendStartBuildTask(buildTime);
			return true;
		} else {
			task.run();
			return false;
		}
	}

	private void upgradeClaimObject(ClaimTask task) {
		Log.debug("BUILD: upgrading claim object from task");
		// Add to list
		BuildObjectTemplate tmpl = VoxelClient.getBuildingTemplate(task.cObject.templateId);
		if (tmpl == null) {
			return;
		}

		
		Log.debug("BUILD: upgraded health");
		boolean readyToUpgrade = true;
	
		if (VoxelPlugin.UPGRADE_CLAIM_OBJECT_ITEMS_FROM_INVENTORY) {
			readyToUpgrade = true;
		}
		// Check if there are any items left, if not - upgrade!
		if (readyToUpgrade) {
			if (Log.loggingDebug)
				Log.debug("BUILD: itemreqs are empty, upgrading item to : " + task.template);
			task.cObject.stage++;
			//task.cObject.gameObject = task.template.getStage(task.cObject.stage).getGameObject();
			if ((task.cObject.stage + 1) < task.template.getStages().size()) {
				// Get the next upgraded version and set the item reqs from that
				task.cObject.itemReqs = task.template.getStage(task.cObject.stage + 1).getItemReqs();
				if(task.cObject.health == task.cObject.maxHealth) {
					task.cObject.health = task.template.getStage(task.cObject.stage ).getHealth();
				}
				task.cObject.maxHealth = task.template.getStage(task.cObject.stage ).getHealth();
				//task.cObject.health = 0;
			} else {
				// We are at the final stage, set complete
				// task.cObject.itemReqs = new HashMap<Integer, Integer>();
				task.cObject.complete = true;

			}
			// Need to send down new model
			//sendRemoveObject(task.cObject, task.cObject.id);
			sendObject(task.cObject);
		}

		Log.debug("BUILD: about to update state on the database");
		// Save the updated state, remove the item from the players inventory and send
		// the info down to the player
		cDB.updateClaimObjectState(task.cObject.id, task.cObject.templateId, task.cObject.stage, task.cObject.complete, task.cObject.state, task.cObject.gameObject, task.cObject.health, task.cObject.maxHealth,
				task.cObject.itemReqs);
		if (Log.loggingDebug)
			Log.debug("BUILD: removing specific item: " + task.itemOid + " from player: " + task.playerOid);
		ExtendedCombatMessages.sendAnouncementMessage(task.playerOid, "Building improvement complete", "");
		sendObjectInfo(task.playerOid, task.cObject.id);

		if (task.cObject.complete) {
			// BuildObjectTemplate tmpl =
			// VoxelPlugin.GetBuildObjectTemplate(task.cObject.templateId);
			if (tmpl != null && tmpl.stages.get(task.cObject.stage).getInteractionType() != null && tmpl.stages.get(task.cObject.stage).getInteractionType().equals("NPC")) {
				Log.debug("BUILD: object is of type NPC");
				int spawnID = tmpl.stages.get(task.cObject.stage).getInteractionID();
				MobDatabase mDB = new MobDatabase(false);
				SpawnData sd = mDB.loadSpecificSpawnData(spawnID);
				sd.setName(generateSpawnName(task.cObject));
				sd.setInstanceOid(instanceOID);
				sd.setLoc(new Point(AOVector.add(task.cObject.loc, loc)));
				sd.setOrientation(task.cObject.orient);
				AgisMobClient.spawnMob(sd);
				Log.debug("BUILD: sent Spawn Mob message");
			}
		}
	}

	/**
	 * 
	 * @param task
	 */
	private void repairClaimObject(ClaimTask task) {
		Log.debug("BUILD: repairing claim object from task");

		if (Log.loggingDebug)
			Log.debug("BUILD: health has reached max for : " + task.template);
		task.cObject.health = task.cObject.maxHealth;

		Log.debug("BUILD: about to update state on the database");
		// Save the updated state, remove the item from the players inventory and send
		// the info down to the player
		cDB.updateClaimObjectState(task.cObject.id, task.cObject.templateId, task.cObject.stage, task.cObject.complete, task.cObject.state, task.cObject.gameObject, task.cObject.health, task.cObject.maxHealth,
				task.cObject.itemReqs);
		if (Log.loggingDebug)
			Log.debug("BUILD: removing specific item: " + task.itemOid + " from player: " + task.playerOid);
		ExtendedCombatMessages.sendAnouncementMessage(task.playerOid, "Building repair complete", "");
		sendObject(task.cObject);
	}

	/**
	 * Sends down the information about a claim object to the requesting player. The
	 * message will contain information such as what items are needed for it to be
	 * upgraded.
	 * 
	 * @param playerOid
	 * @param objectID
	 */
	public void sendObjectInfo(OID playerOid, int objectID) {
		ClaimObject cObject = getClaimObject(objectID);
		if (cObject == null)
			return;

		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "claim_object_info");
		props.put("claimID", id);
		props.put("id", cObject.id);
		props.put("health", cObject.health);
		props.put("maxHealth", cObject.maxHealth);
		props.put("complete", cObject.complete);
		props.put("fstage", cObject.finalStage);
		props.put("status", cObject.status);
		
		ClaimTask objectTask = getObjectBuildTask(cObject.id);
		if (objectTask != null) {
			props.put("timeCompleted", System.currentTimeMillis() - objectTask.startTime);
			props.put("timeLeft", objectTask.endTime - System.currentTimeMillis());
			if (Log.loggingDebug)
				Log.debug("BUILD: got timeLeft with endTime: " + objectTask.endTime + " and current: " + System.currentTimeMillis());
		} else {
			props.put("timeCompleted", 0l);
			props.put("timeLeft", 0l);
		}
		BuildObjectTemplate tmpl = VoxelPlugin.GetBuildObjectTemplate(cObject.templateId);
		float healthP = 100;
		if(cObject.maxHealth>0)
			healthP = cObject.health * 100f/ cObject.maxHealth;
		int maxDamagePrefab = -1;
		String damage="";
		if (healthP < 100) {
			for (int p : tmpl.getStages().get(cObject.stage).getDamagedGameObject().keySet()) {
				if (p > maxDamagePrefab && maxDamagePrefab < healthP) {
					maxDamagePrefab = p;
				}
			}
		}

		if (maxDamagePrefab > 0) {
			damage= tmpl.getStages().get(cObject.stage).getDamagedGameObject().get(maxDamagePrefab);
		}
		props.put("tTime", cObject.totalTime);
		props.put("cTime", cObject.currentTime);
		props.put("sTime", cObject.timeModifier);
		props.put("buildRun", cObject.building);
		if (cObject.totalTime == 0) {
			props.put("dTime", 0L);
		} else {
			props.put("dTime", Math.round((System.currentTimeMillis() - cObject.lastTimeUpdate) * cObject.timeModifier));
		}
		props.put("damage", damage);
		props.put("solo", tmpl.buildTaskSolo());
		
		props.put("interactionType", tmpl.stages.get(cObject.stage).getInteractionType());
		props.put("attackable", tmpl.getAttackable());
		props.put("repairable", tmpl.getRepairable());
		props.put("lockTemplateID", cObject.lockTemplateId);
		props.put("lockDurability", cObject.lockDurability);
		int itemCount = 0;
		if (Log.loggingDebug)
			Log.debug("CLAIM: sendObjectInfo " + cObject.toString() + "; itemReq: " + tmpl.stages.get(cObject.stage).getItemReqs());
		if (tmpl.stages.size() > cObject.stage + 1) {
			for (Integer itemID : tmpl.stages.get(cObject.stage+1).getItemReqs().keySet()) {
				if (itemID > 0) {
					props.put("item" + itemCount, itemID);
					props.put("itemCount" + itemCount, tmpl.stages.get(cObject.stage+1).getItemReqs().get(itemID));
					itemCount++;
				}
			}
		}
		props.put("itemCount", itemCount);
		if (Log.loggingDebug)	Log.debug("sendObjectInfo props=" + props);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

	/**
	 * Used when a player first enters the radius for the claim. Sends down all the
	 * changes.
	 * 
	 * @param playerOid
	 */
	private void sendObjectsToPlayer(OID playerOid) {
		if (Log.loggingDebug)Log.debug("sendObjectsToPlayer try send "+playerOid);
		// Send 50 changes per message
		int chunkSize = 50;
		// Only send data higher than the last id that was sent to the player
		int lastIDSent = -1;
		if (playersLastObjectIDSent.containsKey(playerOid))
			lastIDSent = playersLastObjectIDSent.get(playerOid);
		// No need to continue if the last id sent is equal to or greater than the
		// number of actions
		if (objects.size() == 0 || lastIDSent >= highestObjectID) {
			Log.debug("sendObjectsToPlayer try send "+playerOid+" no object "+objects.size()+" or lastIDSent >= highestObjectID "+(lastIDSent >= highestObjectID)+" lastIDSent="+lastIDSent+" highestObjectID="+highestObjectID);
			return;
		}

		for (int i = 0; i < objects.size(); i += chunkSize) {
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "claim_object_bulk");
			props.put("claimID", id);
			ClaimUpgrade cu = upgrades.get(getUpgrade());
			if (cu != null) {
				props.put("diffloc", cu.loc);
			} else {
				props.put("diffloc", new AOVector());
			}
			int actionCount = chunkSize;
			if (objects.size() - i < chunkSize) {
				actionCount = objects.size() - i;
			}

			if (Log.loggingDebug)
				Log.debug("CLAIM: Comparing object lastID: " + lastIDSent + " against last from chunk: " + objects.get(i + actionCount - 1).id);
			if (objects.get(i + actionCount - 1).id <= lastIDSent)
				continue;

			int numObjects = 0;
			for (int j = 0; j < actionCount; j++) {
				if (Log.loggingDebug)
					Log.debug("CLAIM: Comparing action id: " + objects.get(j + i).id + " against lastID: " + lastIDSent);
				if (objects.get(j + i).id <= lastIDSent) {
					continue;
				}
				BuildObjectTemplate buildTemplate = VoxelPlugin.GetBuildObjectTemplate(objects.get(j + i).templateId);
				if (Log.loggingDebug)
					Log.debug("CLAIM: buildTemplate= " + buildTemplate + " templateId=" + objects.get(j + i).templateId);
								
				float healthP = 100;
				if (objects.get(j + i).maxHealth > 0)
					healthP = objects.get(j + i).health * 100f / objects.get(j + i).maxHealth;
				int maxDamagePrefab = -1;
				String damage = "";
				if (healthP < 100) {
					for (int p : buildTemplate.getStages().get(objects.get(j + i).stage).getDamagedGameObject().keySet()) {
						if (p > maxDamagePrefab && maxDamagePrefab < healthP) {
							maxDamagePrefab = p;
						}
					}
				}

				if (maxDamagePrefab > 0) {
					damage= buildTemplate.getStages().get(objects.get(j + i).stage).getDamagedGameObject().get(maxDamagePrefab);
				}
				String actionString = objects.get(j + i).id + ";" + objects.get(j + i).templateId + ";" + objects.get(j + i).gameObject + ";" + objects.get(j + i).loc.getX() + "," + objects.get(j + i).loc.getY() + ","
						+ objects.get(j + i).loc.getZ() + ";" + objects.get(j + i).orient.getX() + "," + objects.get(j + i).orient.getY() + "," + objects.get(j + i).orient.getZ() + "," + objects.get(j + i).orient.getW()
						+ ";" + objects.get(j + i).state + ";" + objects.get(j + i).health + ";" + objects.get(j + i).maxHealth + ";" + objects.get(j + i).complete + ";" + 
						//buildTemplate.interactionType 
						buildTemplate.getStages().get(objects.get(j+i).stage).interactionType
						+ ";"+ objects.get(j + i).lockTemplateId + ";" + objects.get(j + i).lockDurability+";"+buildTemplate.getAttackable()+";"+buildTemplate.getRepairable() +";"+damage+";"+buildTemplate.buildTaskSolo()
						+";"+objects.get(j + i).status
						+";"+objects.get(j + i).totalTime
						+";"+objects.get(j + i).currentTime
						+";"+objects.get(j + i).timeModifier
						+";"+objects.get(j + i).building;
						if (objects.get(j + i).totalTime == 0) {
							actionString+=";"+ 0L;
						} else {
							actionString+=";"+ Math.round((System.currentTimeMillis() - objects.get(j + i).lastTimeUpdate) * objects.get(j + i).timeModifier);
						}
						/*+";"+cObject.building
							
						props.put("tTime", cObject.totalTime);
						props.put("cTime", cObject.currentTime);
						props.put("sTime", cObject.timeModifier);
						props.put("buildRun", cObject.building);
						if (cObject.totalTime == 0) {
							props.put("dTime", 0L);
						} else {
							props.put("dTime", Math.round((System.currentTimeMillis() - cObject.lastTimeUpdate) * cObject.timeModifier));
						}
						*/
						
						
				props.put("object_" + numObjects, actionString);
				if (Log.loggingDebug)
					Log.debug("CLAIM: Sending objectString: " + actionString);
				playersLastObjectIDSent.put(playerOid, objects.get(j + i).id);
				numObjects++;
			}
			if (Log.loggingDebug)
				Log.debug("CLAIM: id=" + id + " Sending objects count: " + numObjects + " to playerOid=" + playerOid);
			props.put("numObjects", numObjects);
			Log.debug("sendObjectsToPlayer ");
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
			// New code - only send the one chunk
			// break;
		}
	}

	public void updateHealth(ClaimObject co) {
		cDB.updateClaimObjectHealth(co.id, co.health, co.maxHealth);
	}

	public void displayDamage(ClaimObject co,int damage ) {
		
		//FIXME displayDamage
	}
	
	public void generateLoot(OID playerOid,ClaimObject co) {
		BuildObjectTemplate buildTemplate = VoxelPlugin.GetBuildObjectTemplate(co.templateId);

		AOVector aopoint = AOVector.add(co.loc, getLoc());
		HashMap<Integer, Integer> items = buildTemplate.getStages().get(co.stage).getItemReqs();
		HashMap<Integer, Integer> itemsToLoot = new HashMap<Integer, Integer>();
		Random random = new Random();
		for (Integer item : items.keySet()) {
			float roll = random.nextFloat();
			int count = (int) Math.round(Math.ceil(items.get(item) * (buildTemplate.getStages().get(co.stage).getLootMinPercentage()
					+ roll * (buildTemplate.getStages().get(co.stage).getLootMaxPercentage() - buildTemplate.getStages().get(co.stage).getLootMinPercentage())) / 100F));
			if (count > 0)
				itemsToLoot.put(item, count);
		}
		AgisInventoryClient.generateLoot(playerOid, new Point(aopoint), co.orient, getInstanceOID(), buildTemplate.getStages().get(co.stage).getLootTable(), itemsToLoot);
	}
	
	/**
	 * Starts an Attack task to deal damage to the specified claim building object.
	 * 
	 * @param playerOid
	 * @param objectID
	 */
	public void attackBuildObject(OID playerOid, int objectID) {
		ClaimObject cObject = getClaimObject(objectID);
		if (cObject == null)
			return;

		// Check if the building is complete
		if (!cObject.complete)
			return;

		BuildObjectTemplate tmpl = VoxelClient.getBuildingTemplate(cObject.templateId);
		if (!isCloseEnough(playerOid, tmpl, loc)) {
			ExtendedCombatMessages.sendErrorMessage(playerOid, "You are too far away from the object to attack it");
			return;
		}

		// Start the attack task
		ClaimTask task = new ClaimTask();
		task.StartAttackTask(cObject, playerOid, this);
		tasks.add(task);

		// Schedule the task completion
		Engine.getExecutor().schedule(task, 1000, TimeUnit.MILLISECONDS);
		task.sendStartAttackTask(1);
	}

	/**
	 * Damages a build object when an attack ClaimTask is completed. Downgrades the
	 * building or removes it (if it is stage 1) when the health is below 0.
	 * 
	 * @param task
	 */
	private void damageBuildObject(ClaimTask task) {
		Log.debug("BUILD: dealing damage to build object");
		// Get damage amount from player
		int damage = 8;
		// Subtract health from claim object. If the health goes below 0 then
		// down-grade to previous
		task.cObject.health -= damage;
		if (task.cObject.health < 0) {
			Log.debug("BUILD: objects health is less than 1, downgrade");

			BuildObjectTemplate template = VoxelClient.getBuildingTemplate(task.cObject.templateId);
			if (template == null) {
				Log.error("BUILD: no template found for id: " + task.cObject.templateId + ". Object cannot be attacked.");
				return;
			}

			if (task.cObject.stage < 1) {
				if (Log.loggingDebug)
					Log.debug("BUILD: hit stage 0 for " + template.id);
				// This is a first stage object - set health to 0
				task.cObject.health = 0;
				// removeClaimObject(task.cObject.id);
			} else {
				// Loop through subtracting the health left until we either hit the first object
				// or the health is above 0
				Log.debug("BUILD: looping through prereqs");
				// Set the health to the objects current health. It will be less than 1
				int healthLeft = task.cObject.health;
				task.cObject.stage--;
				while (healthLeft < 1) {
					// Add the health of the previous object and see if it gets above 0
					healthLeft += template.getStage(task.cObject.stage).getHealth();
					if (healthLeft < 1 && task.cObject.stage < 1) {
						// Remove object
						if (Log.loggingDebug)
							Log.debug("BUILD: hit stage 0 for " + template.id);
						// removeClaimObject(task.cObject.id);
						// return;
						healthLeft = 0;
						break;
					} else if (healthLeft < 1) {
						Log.debug("BUILD: health is still below 0 and there is still a prereq, downgrade again");
						task.cObject.stage--;
					}
				}

				if (Log.loggingDebug)
					Log.debug("BUILD: changing object " + task.cObject.templateId + " down to stage: " + task.cObject.stage);
				task.cObject.gameObject = template.getStage(task.cObject.stage).getGameObject();
				task.cObject.health = healthLeft;
				task.cObject.maxHealth = template.getStage(task.cObject.stage).getHealth();
				task.cObject.itemReqs = template.getStage(task.cObject.stage).getItemReqs();

				// Send down the model change
				sendRemoveObject(task.cObject, task.cObject.id);
				sendObject(task.cObject);
			}
		}

		// Alter object
		Log.debug("BUILD: about to update state on the database after attack");
		// Save the updated state, remove the item from the players inventory and send
		// the info down to the player
		cDB.updateClaimObjectState(task.cObject.id, task.cObject.templateId, task.cObject.stage, task.cObject.complete, task.cObject.state, task.cObject.gameObject, task.cObject.health, task.cObject.maxHealth,
				task.cObject.itemReqs);
		sendObjectInfo(task.playerOid, task.cObject.id);
	}

	public void alterResource(OID playerOid, int itemID, int count) {
		if (count > 0) {
			if (resources.containsKey(itemID)) {
				resources.get(itemID).count += count;
				cDB.updateClaimResource(resources.get(itemID).id, itemID, resources.get(itemID).count);
			} else {
				ClaimResource resource = new ClaimResource();
				resource.itemID = itemID;
				resource.count = count;
				resource.id = cDB.writeClaimResource(id, itemID, count);
				resources.put(itemID, resource);
			}
		} else if (count < 0) {
			if (resources.containsKey(itemID)) {
				resources.get(itemID).count += count;
				if (resources.get(itemID).count < 0)
					resources.get(itemID).count = 0;
				cDB.updateClaimResource(resources.get(itemID).id, itemID, resources.get(itemID).count);
			}
		}
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "claim_resource_update");
		props.put("claimID", id);
		props.put("resource", itemID);
		props.put("resourceCount", resources.get(itemID).count);

		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}

	public void takeResource(OID playerOid, int itemID) {
		if (resources.containsKey(itemID)) {
			HashMap<Integer, Integer> itemsToGenerate = new HashMap<Integer, Integer>();
			itemsToGenerate.put(itemID, resources.get(itemID).count);
			HashMap<Integer, Integer> itemsLeftOver = AgisInventoryClient.generateItems(playerOid, itemsToGenerate, true);
			if (!itemsLeftOver.containsKey(itemID)) {
				alterResource(playerOid, itemID, -resources.get(itemID).count);
			} else {
				EventMessageHelper.SendErrorEvent(playerOid, EventMessageHelper.INVENTORY_FULL, itemID, "");
			}
		}
	}

	public int getID() {
		return id;
	}

	public void setID(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getInstanceID() {
		return instanceID;
	}

	public void setInstanceID(int instanceID) {
		this.instanceID = instanceID;
	}

	public AOVector getLoc() {
		return loc;
	}

	public void setLoc(AOVector loc) {
		this.loc = loc;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public int getSizeY() {
		return sizeY;
	}

	public void setSizeY(int sizeY) {
		this.sizeY = sizeY;
	}

	public int getSizeZ() {
		return sizeZ;
	}

	public void setSizeZ(int sizeZ) {
		this.sizeZ = sizeZ;
	}

	public HashMap<String, Serializable> getProps() {
		return props;
	}

	public void setProps(HashMap<String, Serializable> props) {
		this.props = props;
	}

	public OID getInstanceOID() {
		return instanceOID;
	}

	public void setInstanceOID(OID instanceOID) {
		this.instanceOID = instanceOID;
	}

	public OID getObjectOID() {
		return objectOID;
	}

	public void setObjectOID(OID objectOID) {
		this.objectOID = objectOID;
	}

	public int getClaimType() {
		return claimType;
	}

	public void setClaimType(int claimType) {
		this.claimType = claimType;
	}

	public long getInstanceOwner() {
		return instanceOwner;
	}

	public void setInstanceOwner(long instanceOwner) {
		this.instanceOwner = instanceOwner;
	}

	public OID getOwner() {
		return owner;
	}

	public void setOwner(OID owner) {
		this.owner = owner;
	}

	public long getInstanceGuild() {
		return instanceGuild;
	}

	public void setInstanceGuild(long instanceGuild) {
		this.instanceGuild = instanceGuild;
	}

	public boolean getForSale() {
		return forSale;
	}

	public void setForSale(boolean forSale) {
		this.forSale = forSale;
	}

	public boolean getPermanent() {
		return permanent;
	}

	public void setPermanent(boolean permanent) {
		this.permanent = permanent;
	}

	public long getCost() {
		return cost;
	}

	public void setCost(long cost) {
		this.cost = cost;
	}

	public void resetCost() {
		this.cost = org_cost;
		this.currency = org_currency;
	}

	public long getOrgCost() {
		return org_cost;
	}

	public void setOrgCost(long org_cost) {
		this.org_cost = org_cost;
	}

	public int getCurrency() {
		return currency;
	}

	public void setCurrency(int currency) {
		this.currency = currency;
	}

	public int getOrgCurrency() {
		return org_currency;
	}

	public void setOrgCurrency(int org_currency) {
		this.org_currency = org_currency;
	}

	public String getSellerName() {
		return sellerName;
	}

	public void setSellerName(String sellerName) {
		this.sellerName = sellerName;
	}

	public int getClaimItemTemplate() {
		return claimItemTemplate;
	}

	public void setClaimItemTemplate(int claimItemTemplate) {
		this.claimItemTemplate = claimItemTemplate;
	}

	public int getBondItemTemplate() {
		return bondItemTemplate;
	}

	public void setBondItemTemplate(int bondItemTemplate) {
		this.bondItemTemplate = bondItemTemplate;
	}

	public void addPurchaseItemReq(int purchaseItemReq) {
		this.purchaseItemReqs.add(purchaseItemReq);
	}

	public LinkedList<Integer> getPurchaseItemReqs() {
		return purchaseItemReqs;
	}

	public void setPurchaseItemReqs(LinkedList<Integer> purchaseItemReqs) {
		this.purchaseItemReqs = purchaseItemReqs;
	}

	public long getTaxPaidUntil() {
		return taxPaidUntil;
	}

	public void setTaxPaidUntil(long taxPaidUntil) {
		this.taxPaidUntil = taxPaidUntil;
	}

	public long getBondPaidUntil() {
		return bondPaidUntil;
	}

	public void setBondPaidUntil(long bondPaidUntil) {
		this.bondPaidUntil = bondPaidUntil;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public boolean getActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public void setAccountDatabase(AccountDatabase cDB) {
		this.cDB = cDB;
	}

	public int getParentId() {
		return parenId;
	}

	public void setParentId(int parenId) {
		this.parenId = parenId;
	}

	public int getProfile() {
		return claim_limit_profile;
	}

	public void setProfile(int profile) {
		this.claim_limit_profile = profile;
	}

	public int getUpgrade() {
		return upgrade;
	}

	public void setUpgrade(int upgrade) {
		this.upgrade = upgrade;
	}

	public AOVector getUpgradeDiffloc() {
		return upgradeDiffloc;
	}

	public void setUpgradeDiffloc(AOVector upgradeDiffloc) {
		this.upgradeDiffloc = upgradeDiffloc;
	}

	public LinkedList<OID> getPlayerInRange(OID instanceOid) {
		return instancePlayersInRange.get(instanceOid);
	}

	
	public int getTaxCurrency() {
		return taxCurrency;
	}

	public void setTaxCurrency(int taxCurrency) {
		this.taxCurrency = taxCurrency;
	}

	public long getTaxAmount() {
		return taxAmount;
	}

	public void setTaxAmount(long taxAmount) {
		this.taxAmount = taxAmount;
	}

	public long getTaxInterval() {
		return taxInterval;
	}

	public void setTaxInterval(long taxInterval) {
		this.taxInterval = taxInterval;
	}

	public long getTaxPeriodPay() {
		return taxPeriodPay;
	}

	public void setTaxPeriodPay(long taxPeriodPay) {
		this.taxPeriodPay = taxPeriodPay;
	}

	public long getTaxPeriodSell() {
		return taxPeriodSell;
	}

	public void setTaxPeriodSell(long taxPeriodSell) {
		this.taxPeriodSell = taxPeriodSell;
	}
	public OID PlayerOwner() {
		if(playerOID==null && sellerName.length()>0) {
			playerOID = PlayerCache.getOidByName(sellerName);
		}
		return playerOID;
	}
	
	int parenId = -1;
	int id;
	String name;
	int instanceID;
	AOVector loc;
	AOVector upgradeDiffloc;
	int size;
	int sizeY;
	int sizeZ;
	int upgrade = 0;
	DisplayContext dc;
	OID instanceOID;
	OID objectOID;
	OID playerOID;
	ConcurrentHashMap<OID, OID> claimOidList = new ConcurrentHashMap<OID, OID>();
	HashMap<Integer, Integer> claimObjectsList = new HashMap<Integer, Integer>();
	int claim_limit_profile = -1;
	int claimType = 0;
	OID owner;
	long instanceOwner = 0;
	long instanceGuild = -1;
	boolean forSale = false;
	long cost = 0;
	boolean permanent = false;
	long org_cost = 0;
	int currency;
	int org_currency;
	String sellerName = "";
	int claimItemTemplate = -1;
	int bondItemTemplate = -1;
	LinkedList<Integer> purchaseItemReqs = new LinkedList<Integer>();
	long taxPaidUntil = 946684800;
	long bondPaidUntil = 946684800;
	 int taxCurrency=-1;
	 long taxAmount=0;
	 long taxInterval=0;
	 long taxPeriodPay=0;
	 long taxPeriodSell=0;
	int priority = 1;
	String data;
	HashMap<String, Serializable> props;
	    
	// List of the claim upgarde definitions
	public HashMap<Integer, ClaimUpgrade> upgrades = new HashMap<Integer, ClaimUpgrade>();
	LinkedList<ClaimAction> actions = new LinkedList<ClaimAction>();
	LinkedList<ClaimObject> objects = new LinkedList<ClaimObject>();
	HashMap<Integer, ClaimResource> resources = new HashMap<Integer, ClaimResource>();
	HashMap<OID, ClaimPermission> permissions = new HashMap<OID, ClaimPermission>();
	LinkedList<OID> playersInRange = new LinkedList<OID>();
	ConcurrentHashMap<OID, LinkedList<OID>> instancePlayersInRange = new ConcurrentHashMap<OID, LinkedList<OID>>();
	public ConcurrentHashMap<OID, Integer> objectsStance = new ConcurrentHashMap<OID,Integer>();
	    
	HashMap<OID, Integer> playersLastIDSent = new HashMap<OID, Integer>();
	HashMap<OID, Integer> playersLastObjectIDSent = new HashMap<OID, Integer>();
	LinkedList<ClaimTask> tasks = new LinkedList<ClaimTask>();
	boolean active;
	Long eventSub = null;
	AccountDatabase  cDB = new AccountDatabase(false);
	int highestObjectID = -1;

	ClaimEntity claimEntity;

	// The name of the prefab found in the Resources folder in Unity for the claim.
	// This is a terrible way to do it, needs changed.
	String model = "ClaimCube";

	public static final int CLAIM_EDIT_RADIUS = 30;
	private static final long serialVersionUID = 1L;

	enum TaskType {
		BUILD, UPGRADE, ATTACK, REPAIR
	}

	/**
	 * Should probably be a struct
	 * 
	 * @author Andrew
	 *
	 */
	class ClaimAction {
		public int id;
		public String action;
		public String brushType;
		public AOVector size;
		public AOVector loc;
		public AOVector normal;
		public int mat;
	}

	/**
	 * Should probably be a struct
	 * 
	 */
	public class ClaimUpgrade {
		public int id;
		public String items;
		public long cost;
		public int currency;
		public AOVector size;
		public AOVector loc;
		public int claim_limit_profile = -1;
		public int taxCurrency;
		public long taxAmount;
		public long taxInterval;
		public long taxPeriodPay;
		public long taxPeriodSell;
		// public HashMap<Integer, Integer> itemReqs;
		public LinkedList<Integer> itemReqs = new LinkedList<Integer>();

	}

	public class ClaimObject {
		public int id;
		public int templateId;
		public String gameObject;
		public AOVector loc;
		public Quaternion orient;
		public int stage;
		public boolean complete;
		public boolean finalStage = false;
		public int parent;
		public String parents;
		public int itemID;
		public String state;
		public int health;
		public int maxHealth;
		public HashMap<Integer, Integer> itemReqs;
		public int lockTemplateId;
		public int lockDurability;
		public int progress = -1;
		public long totalTime = 0L;
		public long currentTime = 0L;
		public long lastTimeUpdate = 0L;
		public double timeMultiply = 0D;
		public boolean building = false;
		public String status="";
	//	public boolean attackable = false;
		
		public ConcurrentHashMap<OID, Integer> users = new ConcurrentHashMap<OID, Integer>();
		public double timeModifier = 1D;
		public OID taskPlayerOid;
		public int taskPlayerStat;
		public ArrayList<Integer> getParents() {
			ArrayList<Integer> list = new ArrayList<Integer>();
			if (parents != null && parents.length() > 0) {
				String str[] = parents.split(";");
				for (int i = 0; i < str.length; i++) {
					list.add(Integer.parseInt(str[i]));
				}
			}
			return list;
		}
		public String toString() {
			return "[ClaimObject:"+id+"; tempId="+templateId+"; complete="+complete+"; stage="+stage+" finalStage="+finalStage+" progress="+progress+" totalTime="+totalTime+" currentTime="+currentTime+
					" lastTimeUpdate="+lastTimeUpdate+" timeMultiply="+timeMultiply+" building="+building+" timeModifier="+timeModifier+"]";
		}
	}

	class ClaimResource {
		public int id;
		public int itemID;
		public int count;
	}

	class ClaimPermission {
		public OID playerOid;
		public String playerName;
		public int permissionLevel;
	}

	class TaxDeed {
		public int id;
		public int itemID;
		public float timeLimit;
		public float timeLeft;
	}

	/**
	 * A Runnable class that adds an object to the claim when it is run.
	 * 
	 * @author Andrew Harrison
	 *
	 */
	public class ClaimTask implements Runnable {

		protected BuildObjectTemplate template;
		protected ClaimObject cObject;
		protected TaskType taskType;
		protected AOVector loc;
		protected Quaternion orient;
		protected int parent;
		protected String parents;
		protected int itemID;
		protected OID itemOid;
		protected ArrayList<ClaimObjectBuildItemEntry> buildItems;
		protected OID playerOid;
		protected boolean reqPlayer = true;
		protected Claim claim;
		protected boolean interrupted;
		protected long startTime;
		protected long endTime;
		protected long elapsedTime;
		protected int buildProgress = 0;
		public ScheduledFuture<?> scheduledFuture ;
		public ClaimTask() {

		}

		public double calculateTimeSpeedMod() {
			//Log.dumpStack("calculateTimeSpeedMod ");
			if (Log.loggingDebug)Log.debug("calculateTimeSpeedMod " + cObject.users);
			int buildSpeed = 0;
			for (Integer v : cObject.users.values()) {
				buildSpeed += v;
			}

			 if (Log.loggingDebug)
			Log.debug("CLAIM: calculateTimeSpeedMod stat: " + buildSpeed + " of " + VoxelPlugin.BUILD_SPEED_STAT);
			int buildSpeedCalculated = 0;
			if (CombatPlugin.STAT_THRESHOLDS != null && CombatPlugin.STAT_THRESHOLDS.containsKey(VoxelPlugin.BUILD_SPEED_STAT)) {
				StatThreshold def = CombatPlugin.STAT_THRESHOLDS.get(VoxelPlugin.BUILD_SPEED_STAT);
				int pointsCalculated = 0;
				for (int i = 1; i <= def.getPoints().size(); i++) {
					if (Log.loggingDebug)
						Log.debug("CLAIM: calculateTimeSpeedMod i=" + i + " pointsCalculated=" + pointsCalculated + " Th=" + def.getThresholds().get(i) + " points=" + def.getPoints().get(i) + " buildSpeedCalculated="
								+ buildSpeedCalculated);
					if (buildSpeed <= def.getThresholds().get(i)) {
						Log.debug("CLAIM: calculateTimeSpeedMod buildSpeed < th");
						if (buildSpeed - pointsCalculated < 0)
							break;
						buildSpeedCalculated += Math.round((buildSpeed - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += buildSpeed - pointsCalculated;
					} else {
						Log.debug("CLAIM: calculateTimeSpeedMod buildSpeed > th");
						buildSpeedCalculated += Math.round((def.getThresholds().get(i) - pointsCalculated) / def.getPoints().get(i));
						pointsCalculated += def.getThresholds().get(i) - pointsCalculated;
					}
				}
			} else {
				buildSpeedCalculated = buildSpeed;
			}
			double result = buildSpeedCalculated / 100D;
			if (Log.loggingDebug)	Log.debug("calculateTimeSpeedMod result=" + result);
			return result;
		}

		public void addPlayerToBuildTask(OID playerOid) {
			if (Log.loggingDebug)Log.debug("addPlayerToBuildTask: playerOid=" + playerOid);

			int plyBuildSpeed = CombatClient.getPlayerStatValue(playerOid, VoxelPlugin.BUILD_SPEED_STAT);
			if (Log.loggingDebug)Log.debug("addPlayerToBuildTask: playerOid=" + playerOid +" "+VoxelPlugin.BUILD_SPEED_STAT+"="+plyBuildSpeed);
			// (Integer) EnginePlugin.getObjectProperty(playerOid, CombatClient.NAMESPACE,
			// VoxelPlugin.BUILD_SPEED_STAT);
			cObject.users.put(playerOid, plyBuildSpeed);
			double mod = calculateTimeSpeedMod();
			if (mod == cObject.timeModifier)
				return;
			float time = reschedule(mod);

			CoordinatedEffect cE = new CoordinatedEffect("StandardBuilding");
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			if (reqPlayer) {
				cE.putArgument("length", time);
			} else {
				cE.putArgument("length", 1f);
			}
			cE.invoke(playerOid, playerOid);

		}

		public void removePlayerFromBuildTask(OID playerOid) {
			if (Log.loggingDebug)Log.debug("removePlayerFromBuildTask: playerOid=" + playerOid);

			cObject.users.remove(playerOid);
			
			double mod = calculateTimeSpeedMod();
			if (mod == cObject.timeModifier)
				return;
			float time = reschedule(mod);
			CoordinatedEffect cE = new CoordinatedEffect("StandardBuilding");
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			cE.putArgument("length", 0f);
			cE.invoke(playerOid, playerOid);
		}

		public void modPlayerForBuildTask(OID playerOid, int statValue) {
			if (Log.loggingDebug)Log.debug("modPlayerForBuildTask: playerOid=" + playerOid + " statValue=" + statValue);
			if(playerOid.equals(this.playerOid))
				cObject.taskPlayerStat = statValue;
			cObject.users.put(playerOid, statValue);
			double mod = calculateTimeSpeedMod();
			if (mod == cObject.timeModifier)
				return;
			float time = reschedule(mod);
			CoordinatedEffect cE = new CoordinatedEffect("StandardBuilding");
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			if (reqPlayer) {
				cE.putArgument("length", time);
			} else {
				cE.putArgument("length", 1f);
			}
			cE.invoke(playerOid, playerOid);
		}

		public float reschedule(double newModTime) {
			if (Log.loggingDebug)Log.debug("reschedule timeModifier="+cObject.timeModifier+" new timeModifier="+newModTime+" users="+cObject.users);
			if (scheduledFuture != null)
				scheduledFuture.cancel(false);
			Engine.getExecutor().remove(this);
			long diffTime = (System.currentTimeMillis() - cObject.lastTimeUpdate);
			if (Log.loggingDebug)Log.debug("resechedule diffTime="+diffTime+" user size="+cObject.users.size());
			if (cObject.users.size() == 0) {
				
				cObject.currentTime += Math.round(diffTime * cObject.timeModifier);
				if (Log.loggingDebug)Log.debug("reschedule currentTime=" + cObject.currentTime);
				cObject.lastTimeUpdate = System.currentTimeMillis();
				cObject.timeModifier = newModTime;
				claim.sendObject(cObject);
				return 0f;
			}
			int progressNext = buildProgress;
			int progress = buildProgress;
			Set<Integer> s = null;
			if (taskType == TaskType.BUILD)
				s = template.getStages().get(cObject.stage).progressGameObjects.keySet();
			if (taskType == TaskType.UPGRADE)
				s = template.getStages().get(cObject.stage + 1).progressGameObjects.keySet();
			Iterator<Integer> i = s.iterator();
			int a = i.next();// 0 element
			// Log.error("runNewBuild a="+a);
			int n = -1;
			int count = 1;
			while (i.hasNext()) {
				n = i.next();
				if (Log.loggingDebug)	Log.debug("reschedule n=" + n);
				if (((n > buildProgress && buildProgress == 0) || (n == buildProgress && buildProgress > 0)) && i.hasNext()) {
					progress = n;
					progressNext = i.next();
					if (Log.loggingDebug)Log.debug("reschedule progressNext=" + progressNext);
					break;
				}if(!i.hasNext()) {
					progressNext = n;
					if (Log.loggingDebug)Log.debug("reschedule no next progressNext=" + progressNext);
					break;
					
				}
			}
			if (Log.loggingDebug)Log.debug("reschedule currentTime=" + cObject.currentTime+" diffTime="+diffTime+" totalTime="+cObject.totalTime+" timeModifier="+cObject.timeModifier);

			cObject.currentTime += Math.round(diffTime * cObject.timeModifier);
			if (Log.loggingDebug)Log.debug("reschedule currentTime=" + cObject.currentTime);
			cObject.lastTimeUpdate = System.currentTimeMillis();
			cObject.timeModifier = newModTime;
			double calculatedProgress = cObject.currentTime * 100D / cObject.totalTime;
			if (Log.loggingDebug)Log.debug("reschedule calculatedProgress=" + calculatedProgress);

			double timePercentage = ((double) progressNext - calculatedProgress) / 100D;
			if (calculatedProgress < progress) {
				timePercentage = ((double) progress - calculatedProgress) / 100D;
			}
			
			long time = 0L;
			if (taskType == TaskType.BUILD) {
				if (Log.loggingDebug)Log.debug("reschedule id=" + cObject.id + " buildProgress=" + buildProgress + " n=" + n + " progressPrev=" + progress + " progressNext=" + progressNext + " timePercentage=" + timePercentage
						+ " calculatedProgress=" + calculatedProgress + " buildTimeReq=" + template.getStages().get(cObject.stage).buildTimeReq + " timeModifier=" + cObject.timeModifier);

				time = (long) Math.round(template.getStages().get(cObject.stage).buildTimeReq * timePercentage / cObject.timeModifier * 1000D);
			}
			if (taskType == TaskType.UPGRADE) {
				if (Log.loggingDebug)Log.debug("reschedule id=" + cObject.id + " buildProgress=" + buildProgress + " n=" + n + " progressPrev=" + progress + " progressNext=" + progressNext + " timePercentage=" + timePercentage
						+ " calculatedProgress=" + calculatedProgress + " buildTimeReq=" + template.getStages().get(cObject.stage + 1).buildTimeReq + " timeModifier=" + cObject.timeModifier);

				time = (long) Math.round(template.getStages().get(cObject.stage + 1).buildTimeReq * timePercentage / cObject.timeModifier * 1000D);

			}

			//long time = (long) Math.round(buildObjectTemplate.getStages().get(0).buildTimeReq / task.cObject.timeModifier * 1000D);
			
			if (Log.loggingDebug)Log.debug("reschedule id=" + cObject.id + " buildProgress=" + buildProgress + " n=" + n + " progressNext=" + progressNext + " time=" + time);

			scheduledFuture = Engine.getExecutor().schedule(this, time, TimeUnit.MILLISECONDS);
			//cObject.lastTimeUpdate = System.currentTimeMillis();
			claim.sendObject(cObject);
			return time / 1000f;
		}

		public void RestoreBuildTask(BuildObjectTemplate template, AOVector loc, Quaternion orient, int parent, String parents, int itemID, OID itemOid, OID playerOid, Claim claim, ClaimObject cObject) {
			Log.error("BUILD: RestoreBuildTask creating new build claim task");
			this.template = template;
			this.taskType = TaskType.BUILD;
			cObject.status = TaskType.BUILD.toString();
			this.loc = loc;
			this.orient = orient;
			this.parent = parent;
			this.itemID = itemID;
			this.itemOid = itemOid;
			this.playerOid = playerOid;
			this.claim = claim;
			this.parents = parents;
			this.cObject = cObject;
			this.cObject.totalTime = Math.round(template.getStage(0).buildTimeReq * 1000);
			this.reqPlayer = template.buildTaskReqPlayer;
			if(template.buildTaskReqPlayer) {
				if(playerOid!=null)
					cObject.users.put(playerOid, 0);
			}
			else if(template.fixedTime)
			{
				if(playerOid!=null)
					cObject.users.put(playerOid, 100);
			}else {
				int plyBuildSpeed = 100;
				try {
					plyBuildSpeed =CombatClient.getPlayerStatValue(playerOid, VoxelPlugin.BUILD_SPEED_STAT);
				} catch (Exception e) {
				}
				
				cObject.users.put(playerOid, plyBuildSpeed);
			}
			double mod = calculateTimeSpeedMod();
			if (Log.loggingDebug)Log.debug("RestoreBuildTask mod=" + mod);
			cObject.timeModifier = mod;
			if (claimObjectsList.containsKey(template.getClaimObjectCategory())) {
				claimObjectsList.put(template.getClaimObjectCategory(), claimObjectsList.get(template.getClaimObjectCategory()) + 1);
			} else {
				claimObjectsList.put(template.getClaimObjectCategory(), 1);
			}
		}
		public void StartBuildTask(BuildObjectTemplate template, AOVector loc, Quaternion orient, int parent, String parents, int itemID, OID itemOid, OID playerOid, Claim claim) {
			Log.debug("BUILD: creating new build claim task");
			this.template = template;
			this.taskType = TaskType.BUILD;
			this.loc = loc;
			this.orient = orient;
			this.parent = parent;
			this.itemID = itemID;
			this.itemOid = itemOid;
			this.playerOid = playerOid;
			this.claim = claim;
			this.parents = parents;
			this.reqPlayer = template.buildTaskRequiresPlayer();
			claim.addClaimObject(this);
			cObject.status = TaskType.BUILD.toString();
			// this.claim.tasks
			int plyBuildSpeed = CombatClient.getPlayerStatValue(playerOid, VoxelPlugin.BUILD_SPEED_STAT);
			if (Log.loggingDebug)Log.debug("StartBuildTask plyBuildSpeed=" + plyBuildSpeed);
			cObject.taskPlayerStat = plyBuildSpeed;
			cObject.users.put(playerOid, plyBuildSpeed);
			double mod = calculateTimeSpeedMod();
			if (Log.loggingDebug)Log.debug("StartBuildTask mod=" + mod);
			cObject.timeModifier = mod;
			if (claimObjectsList.containsKey(template.getClaimObjectCategory())) {
				claimObjectsList.put(template.getClaimObjectCategory(), claimObjectsList.get(template.getClaimObjectCategory()) + 1);
			} else {
				claimObjectsList.put(template.getClaimObjectCategory(), 1);
			}
			sendObject(cObject);
			claimUpdated();
		}
		public void RestoreUpgradeTask(BuildObjectTemplate template, ClaimObject cObject, ArrayList<ClaimObjectBuildItemEntry> buildItems, OID playerOid, boolean reqPlayer, long endTime, Claim claim) {
			Log.debug("BUILD: creating new upgrade claim task");
			this.template = template;
			this.cObject = cObject;
			this.taskType = TaskType.UPGRADE;
			cObject.status = TaskType.UPGRADE.toString();
			this.buildItems = buildItems;
			this.playerOid = playerOid;
			this.reqPlayer = reqPlayer;
			this.claim = claim;
			this.startTime = System.currentTimeMillis();
			this.endTime = endTime;
			
			if (claimObjectsList.containsKey(template.getClaimObjectCategory())) {
				claimObjectsList.put(template.getClaimObjectCategory(), claimObjectsList.get(template.getClaimObjectCategory()) + 1);
			} else {
				claimObjectsList.put(template.getClaimObjectCategory(), 1);
			}
		
		}
		public void StartUpgradeTask(BuildObjectTemplate template, ClaimObject cObject, ArrayList<ClaimObjectBuildItemEntry> buildItems, OID playerOid, boolean reqPlayer, long endTime, Claim claim) {
			Log.debug("BUILD: creating new upgrade claim task");
			this.template = template;
			this.cObject = cObject;
			this.taskType = TaskType.UPGRADE;
			cObject.status = TaskType.UPGRADE.toString();
			this.buildItems = buildItems;
			this.playerOid = playerOid;
			this.reqPlayer = reqPlayer;
			this.claim = claim;
			this.startTime = System.currentTimeMillis();
			this.endTime = endTime;
			int plyBuildSpeed = CombatClient.getPlayerStatValue(playerOid, VoxelPlugin.BUILD_SPEED_STAT);
			if (Log.loggingDebug)Log.debug("StartUpgradeTask plyBuildSpeed=" + plyBuildSpeed);
			cObject.taskPlayerStat = plyBuildSpeed;
			cObject.users.put(playerOid, plyBuildSpeed);
			double mod = calculateTimeSpeedMod();
			if (Log.loggingDebug)Log.debug("StartUpgradeTask mod=" + mod);
			cObject.timeModifier = mod;
			startUpgrade();
		}

		public void StartAttackTask(ClaimObject cObject, OID playerOid, Claim claim) {
			Log.debug("BUILD: creating new attack claim task");
			this.cObject = cObject;
			this.taskType = TaskType.ATTACK;
			this.playerOid = playerOid;
			this.claim = claim;
		}

		public void StartRepairTask(BuildObjectTemplate template, ClaimObject cObject, ArrayList<ClaimObjectBuildItemEntry> buildItems, OID playerOid, Claim claim) {
			Log.debug("BUILD: creating new repair claim task");
			this.template = template;
			this.cObject = cObject;
			this.taskType = TaskType.REPAIR;
			cObject.status = TaskType.REPAIR.toString();
			this.buildItems = buildItems;
			this.playerOid = playerOid;
			this.claim = claim;

		}

		public boolean startCreateClaimObject() {
			if (itemOid != null) {
				AgisInventoryClient.removeSpecificItem(playerOid, itemOid, false, 1);
			}
			return true;
		}

		public void sendStartBuildTask(float length) {
			Log.debug("BUILD: sending start build task");
			Map<String, Serializable> props = new HashMap<String, Serializable>();
			props.put("ext_msg_subtype", "start_build_task");
			props.put("claimID", id);
			props.put("id", template.id);
			props.put("length", length);
			props.put("go", template.getStage(cObject.stage).progressGameObjects.get(buildProgress));
			if (Log.loggingDebug)Log.debug("sendStartBuildTask: props="+props);
			TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
			Engine.getAgent().sendBroadcast(msg);
			sendObject(cObject);
			// Also send down a coord effect here
			CoordinatedEffect cE = new CoordinatedEffect("StandardBuilding");
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			if (reqPlayer) {
				cE.putArgument("length", length);
			} else {
				cE.putArgument("length", 1f);
			}
			cE.invoke(playerOid, playerOid);

			// EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE,
			// AgisWorldManagerPlugin.PROP_ACTION_STATE, "Building");
		}

		public void sendStartAttackTask(float length) {
			CoordinatedEffect cE = new CoordinatedEffect("AttackBuilding");
			cE.sendSourceOid(true);
			cE.sendTargetOid(true);
			cE.putArgument("length", length);
			cE.invoke(playerOid, playerOid);
		}

		@Override
		public void run() {
			// Check user still has items
			try {
				if (Log.loggingDebug)Log.debug("BUILD: running task " + taskType);
				// claim.tasks.remove(this);
				if (interrupted) {
					Log.debug("BUILD: task was interrupted, not completing run");
					return;
				}

				if (playerOid != null) {
					try {
						EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE, AgisWorldManagerPlugin.PROP_ACTION_STATE, "");
					} catch (NoRecipientsException e) {
						
					} catch (Exception e) {
						
					}
				}

				// Add object to the claim
				if (taskType == TaskType.BUILD) {
					runNewBuild();
				} else if (taskType == TaskType.UPGRADE) {
					runUpgrade();
				} else if (taskType == TaskType.ATTACK) {
					runAttack();
				} else if (taskType == TaskType.REPAIR) {
					runRepair();
				}
			} catch (Exception e) {
				Log.exception("Claim Task run", e);
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		void runNewBuild() {
			if (Log.loggingDebug)Log.debug("!!!!!!!!!!!!!!!!!! runNewBuild id=" + cObject.id + " buildProgress=" + buildProgress);
			if (buildProgress == 0) {

				Log.debug("BUILD: getting player skill for new build");
				int playerSkillLevel = -1;
				if (template.skill > 0)
					playerSkillLevel = ClassAbilityClient.getPlayerSkillLevel(playerOid, template.skill);
				// Do skillup chance
				if (template.skill > 0) {
					if (Log.loggingDebug)
						Log.debug("BUILD: checking skill: " + template.skill + " against playerSkillLevel: " + playerSkillLevel);
					CombatClient.abilityUsed(playerOid, template.skill);
				}

			}
			if (Log.loggingDebug)Log.debug("runNewBuild: tmpl="+template+" stages="+template.getStages().size()+" progress ="+template.getStage(cObject.stage).progressGameObjects.size()+" "+template.getStage(cObject.stage).progressGameObjects);
			if (template.getStage(cObject.stage).progressGameObjects.size() > 2) {
				// buildProgress
				int progressNext = buildProgress;
				int progress = buildProgress;
				cObject.currentTime += Math.round((System.currentTimeMillis() - cObject.lastTimeUpdate) * cObject.timeModifier);
				cObject.lastTimeUpdate = System.currentTimeMillis();
				double calculatedProgress = cObject.currentTime * 100D / cObject.totalTime;
				if (Log.loggingDebug)Log.debug("runNewBuild id=" + cObject.id + " buildProgress=" + buildProgress + " progressPrev=" + progress + " progressNext=" + progressNext + " calculatedProgress=" + calculatedProgress);
				
				Set<Integer> s = template.getStages().get(cObject.stage).progressGameObjects.keySet();
				Iterator<Integer> i = s.iterator();
				int a = i.next();// 0 element
				// Log.error("runNewBuild a="+a);
				int n = -1;
				int count = 1;
				while (i.hasNext()) {
					n = i.next();
					if (((n > buildProgress && buildProgress == 0) || (n == buildProgress && buildProgress > 0)) && i.hasNext()) {
						progress = n;
						progressNext = i.next();
						if(calculatedProgress > progressNext && i.hasNext()) {
							progress = progressNext;
							progressNext = i.next();
						}
						break;
					}
				}
				
				double timePercentage = ((double) progressNext - calculatedProgress) / 100D;
				if (Log.loggingDebug)Log.debug("runNewBuild id=" + cObject.id + " buildProgress=" + buildProgress + " n=" + n + " progressPrev=" + progress + " progressNext=" + progressNext + " timePercentage=" + timePercentage
						+ " calculatedProgress="+calculatedProgress+" buildTimeReq="+template.getStages().get(cObject.stage).buildTimeReq+" timeModifier=" + cObject.timeModifier);
				
				
				long time = (long) Math.round(template.getStages().get(cObject.stage).buildTimeReq * timePercentage / cObject.timeModifier * 1000D);
				if (template.buildTaskFixedTime()) {
					time = (long) Math.round(template.getStages().get(cObject.stage).buildTimeReq * timePercentage * 1000D);
				}
				if (Log.loggingDebug)	Log.debug("runNewBuild id=" + cObject.id + " buildProgress=" + buildProgress + " n=" + n + " progressNext=" + progressNext + " time=" + time);
				if (buildProgress < 100) {
					scheduledFuture = Engine.getExecutor().schedule(this, time, TimeUnit.MILLISECONDS);
				} else {
					cObject.complete = true;
					cObject.currentTime = 0L;
					cObject.totalTime = 0L;
					cObject.lastTimeUpdate = System.currentTimeMillis();
					cObject.health = template.getStages().get(cObject.stage).getHealth();
					cObject.maxHealth = template.getStages().get(cObject.stage).getHealth();
					cObject.status = "";
					
					claim.tasks.remove(this);
					completeStage(this);
					if(template.getStages().size() <= cObject.stage + 1) {
						cObject.finalStage = true;
					}
						
//					if (template.getStages().get(cObject.stage + 1) == null) {
//						cObject.finalStage = true;
//					}

				}
				buildProgress = progressNext;
				if (Log.loggingDebug)Log.debug("runNewBuild: "+template.id+" stages prefabs ="+template.getStages().get(cObject.stage).progressGameObjects);
				cObject.gameObject = template.getStages().get(cObject.stage).progressGameObjects.get(progress);
				cObject.progress = progress;

				// elapsedTime = template.getStages().get(cObject.stage).buildTimeReq;
				if (Log.loggingDebug)Log.debug("runNewBuild: " + cDB + " " + cObject.id + " , " + cObject.complete + ", " + cObject.progress + ", " + cObject.gameObject + ", " + cObject.finalStage + ", " + cObject.currentTime + ", "
						+ cObject.lastTimeUpdate + ", " + playerOid + " " + cObject.users);
				cDB.updateClaimObjectProgress(cObject.id, cObject.complete, cObject.progress, cObject.gameObject, cObject.finalStage, cObject.currentTime, cObject.lastTimeUpdate, (cObject.users.containsKey(playerOid)?cObject.users.get(playerOid):cObject.taskPlayerStat), playerOid);
				claim.updateHealth(cObject);
				List<AtavismBuildingObject> objectsToAdd = new ArrayList<AtavismBuildingObject>();
				if (template.getStages().get(cObject.stage).getProgressColliders().containsKey(progress)) {
					AtavismBuildingObject abc = new AtavismBuildingObject();
					abc.id = cObject.id;
					if (Log.loggingDebug)Log.debug("runNewBuild task loc=" + loc + " obj loc=" + cObject.loc + " claim loc=" + claim.loc);
					abc.setPosition(AOVector.add(cObject.loc, claim.loc));
					abc.colliders = template.getStages().get(cObject.stage).getProgressColliders().get(progress).colliders;
					abc.setOrientation(cObject.orient);
					objectsToAdd.add(abc);
				}
				ArrayList<Integer> objectToDel = new ArrayList<Integer>();
				objectToDel.add(cObject.id);

				VoxelClient.AddDynamicObjects(instanceOID, objectToDel, objectsToAdd);
				claim.sendObject(cObject);

			} else {
				claim.tasks.remove(this);
				cObject.gameObject = template.getStages().get(cObject.stage).progressGameObjects.get(100);
				cObject.progress = 100;
				cObject.complete = true;
				cObject.health = template.getStages().get(cObject.stage).getHealth();
				cObject.maxHealth = template.getStages().get(cObject.stage).getHealth();
				cObject.currentTime = 0L;
				cObject.totalTime = 0L;
				cObject.status = "";
				
				completeStage(this);
				if(template.getStages().size() <= cObject.stage + 1) {
					cObject.finalStage = true;
				}
				/*if (template.getStages().get(cObject.stage + 1) == null) {
					cObject.finalStage = true;
				}*/
				cObject.lastTimeUpdate = System.currentTimeMillis();
				if (Log.loggingDebug)Log.debug("runNewBuild: "+cObject.id+" , "+cObject.complete+", "+cObject.progress+", "+cObject.gameObject+", "+cObject.finalStage+", "+cObject.currentTime+", "+cObject.lastTimeUpdate+", "+playerOid+" "+cObject.users);
				cDB.updateClaimObjectProgress(cObject.id, cObject.complete, cObject.progress, cObject.gameObject, cObject.finalStage,cObject.currentTime,cObject.lastTimeUpdate,(cObject.users.containsKey(playerOid)?cObject.users.get(playerOid):cObject.taskPlayerStat),playerOid);
				claim.updateHealth(cObject);
				
				claim.sendObject(cObject);

				List<AtavismBuildingObject> objectsToAdd = new ArrayList<AtavismBuildingObject>();
				if (template.getStages().get(cObject.stage).getProgressColliders().containsKey(100)) {
					AtavismBuildingObject abc = new AtavismBuildingObject();
					abc.id = cObject.id;
					abc.setPosition(AOVector.add(cObject.loc, claim.loc));
					abc.colliders = template.getStages().get(cObject.stage).getProgressColliders().get(100).colliders;
					abc.setOrientation(cObject.orient);
					objectsToAdd.add(abc);
				}
				ArrayList<Integer> objectToDel = new ArrayList<Integer>();
				objectToDel.add(cObject.id);

				VoxelClient.AddDynamicObjects(instanceOID, objectToDel, objectsToAdd);
			}
			// claim.addClaimObject(this);
		}

		void startUpgrade() {

			int playerSkillLevel = 1;
			if (template.skill > 0)
				playerSkillLevel = ClassAbilityClient.getPlayerSkillLevel(playerOid, template.skill);

			Log.debug("BUILD: checking success");
			int rollMax = 100;
			int skillLevelCalc = ((playerSkillLevel / 4) + 45);
			Random rand = new Random();
			boolean removeItems = true;
			boolean failed = false;
			if (VoxelPlugin.BUILD_CAN_FAIL && skillLevelCalc < rand.nextInt(rollMax)) {
				failed = true;
				ExtendedCombatMessages.sendErrorMessage(playerOid, "You failed to improve the claim object");
				if (!VoxelPlugin.REMOVE_ITEM_ON_BUILD_FAIL) {
					removeItems = false;
				}
				// return;
			}
			if (removeItems && reqPlayer) {
				// Remove item from players inventory
				if (VoxelPlugin.UPGRADE_CLAIM_OBJECT_ITEMS_FROM_INVENTORY) {
					HashMap<Integer, Integer> itemsToRemove = new HashMap<Integer, Integer>();
					for (Integer item : template.getStage(cObject.stage + 1).getItemReqs().keySet()) {
						if (item > 0) {
							itemsToRemove.put(item, template.getStage(cObject.stage + 1).getItemReqs().get(item));
						}
					}

					LinkedList<Integer> components = new LinkedList<Integer>();
					LinkedList<Integer> componentCounts = new LinkedList<Integer>();
					for (int itemReq : template.getStage(cObject.stage + 1).getItemReqs().keySet()) {
						components.add(itemReq);
						componentCounts.add(template.getStage(cObject.stage + 1).getItemReqs().get(itemReq));
					}

					boolean hasItems = AgisInventoryClient.checkComponents(playerOid, components, componentCounts);

					if (!hasItems) {
						ExtendedCombatMessages.sendErrorMessage(playerOid, "You do not have the required items to build this object");
						return;
					}

					AgisInventoryClient.removeGenericItems(playerOid, itemsToRemove, false);

				} else {
					for (ClaimObjectBuildItemEntry itemEntry : buildItems) {

						if (itemEntry.itemOid != null) {
							AgisInventoryClient.removeSpecificItem(playerOid, itemEntry.itemOid, false, itemEntry.count);
						}
					}
				}

			}

			cObject.gameObject = template.getStages().get(cObject.stage + 1).progressGameObjects.get(0);
			cObject.progress = 0;
			
			cObject.complete = false;
			cDB.updateClaimObjectProgress(cObject.id, cObject.complete, cObject.progress, cObject.gameObject, cObject.finalStage,cObject.currentTime,cObject.lastTimeUpdate,(cObject.users.containsKey(playerOid)?cObject.users.get(playerOid):cObject.taskPlayerStat),playerOid);
			claim.sendObject(cObject);

		}

		void runUpgrade() {
			Log.debug("BUILD: getting player skill for upgrade");

			if (template.getStage(cObject.stage + 1).progressGameObjects.size() > 2) {
				// buildProgress
				int progressNext = buildProgress;
				int progress = buildProgress;
				cObject.currentTime += Math.round((System.currentTimeMillis() - cObject.lastTimeUpdate) * cObject.timeModifier);
				cObject.lastTimeUpdate = System.currentTimeMillis();
				
				if (Log.loggingDebug)Log.debug("runUpgrade id=" + cObject.id + " currentTime=" +cObject.currentTime+"  totalTime="+ cObject.totalTime);
				
				double calculatedProgress = cObject.currentTime * 100D / cObject.totalTime;
				if (Log.loggingDebug)	Log.debug("runUpgrade id=" + cObject.id + " cObject.stage="+cObject.stage+" buildProgress=" + buildProgress + " progressPrev=" + progress + " progressNext=" + progressNext + " calculatedProgress=" + calculatedProgress);
				
				Set<Integer> s = template.getStages().get(cObject.stage+1).progressGameObjects.keySet();
				Iterator<Integer> i = s.iterator();
				int a = i.next();// 0 element
				// Log.error("runNewBuild a="+a);
				int n = -1;
				int count = 1;
				while (i.hasNext()) {
					n = i.next();
					if (((n > buildProgress && buildProgress == 0) || (n == buildProgress && buildProgress > 0)) && i.hasNext()) {
						progress = n;
						progressNext = i.next();
						if(calculatedProgress > progressNext && i.hasNext()) {
							progress = progressNext;
							progressNext = i.next();
						}
						break;
					}
				}
				
				double timePercentage = ((double) progressNext - calculatedProgress) / 100D;
				if (Log.loggingDebug)Log.debug("runUpgrade id=" + cObject.id + " buildProgress=" + buildProgress + " n=" + n + " progressPrev=" + progress + " progressNext=" + progressNext + " timePercentage=" + timePercentage
						+ " calculatedProgress="+calculatedProgress+" buildTimeReq="+template.getStages().get(cObject.stage+1).buildTimeReq+" timeModifier=" + cObject.timeModifier);
					
				long time = (long) Math.round(template.getStages().get(cObject.stage+1).buildTimeReq * timePercentage / cObject.timeModifier * 1000D);
				if (template.buildTaskFixedTime()) {
					time = (long) Math.round(template.getStages().get(cObject.stage+1).buildTimeReq * timePercentage * 1000D);
				}
				if (Log.loggingDebug)	Log.debug("runUpgrade id=" + cObject.id + " buildProgress=" + buildProgress + " n=" + n + " progressNext=" + progressNext + " time=" + time);
				if (buildProgress < 100) {
					scheduledFuture = Engine.getExecutor().schedule(this, time, TimeUnit.MILLISECONDS);
					
				} else {
					cObject.complete = true;
					cObject.currentTime = 0L;
					cObject.totalTime = 0L;
					cObject.status = "";
					
					cObject.lastTimeUpdate = System.currentTimeMillis();
					claim.tasks.remove(this);
					if(template.getStages().size() <= cObject.stage + 1) {
						cObject.finalStage = true;
					}
					/*if (template.getStages().get(cObject.stage + 1) == null) {
						cObject.finalStage = true;
					}*/
				}
				buildProgress = progressNext;
				cObject.gameObject = template.getStages().get(cObject.stage + 1).progressGameObjects.get(progress);
				cObject.progress = progress;
				// elapsedTime = template.getStages().get(cObject.stage).buildTimeReq;
				cDB.updateClaimObjectProgress(cObject.id, cObject.complete, cObject.progress, cObject.gameObject, cObject.finalStage,cObject.currentTime,cObject.lastTimeUpdate,(cObject.users.containsKey(playerOid)?cObject.users.get(playerOid):cObject.taskPlayerStat),playerOid);
				List<AtavismBuildingObject> objectsToAdd = new ArrayList<AtavismBuildingObject>();
				if (template.getStages().get(cObject.stage + 1).getProgressColliders().containsKey(progress)) {
					AtavismBuildingObject abc = new AtavismBuildingObject();
					abc.id = cObject.id;
					if (Log.loggingDebug)Log.debug("runUpgrade task loc=" + loc + " obj loc=" + cObject.loc + " claim loc=" + claim.loc);
					abc.setPosition(AOVector.add(cObject.loc, claim.loc));
					abc.colliders = template.getStages().get(cObject.stage + 1).getProgressColliders().get(progress).colliders;
					abc.setOrientation(cObject.orient);
					objectsToAdd.add(abc);
				}
				ArrayList<Integer> objectToDel = new ArrayList<Integer>();
				objectToDel.add(cObject.id);

				VoxelClient.AddDynamicObjects(instanceOID, objectToDel, objectsToAdd);
				claim.sendObject(cObject);

			} else {
				claim.tasks.remove(this);
				cObject.gameObject = template.getStages().get(cObject.stage + 1).progressGameObjects.get(100);
				cObject.progress = 100;
				cObject.status = "";
				
				cObject.complete = true;
				cObject.currentTime = 0L;
				cObject.totalTime = 0L;
				if(template.getStages().size() <= cObject.stage + 1) {
					cObject.finalStage = true;
				}
			
				/*if (template.getStages().get(cObject.stage + 1) == null) {
					cObject.finalStage = true;
				}*/
				cObject.lastTimeUpdate = System.currentTimeMillis();
				cDB.updateClaimObjectProgress(cObject.id, cObject.complete, cObject.progress, cObject.gameObject, cObject.finalStage,cObject.currentTime,cObject.lastTimeUpdate,(cObject.users.containsKey(playerOid)?cObject.users.get(playerOid):cObject.taskPlayerStat),playerOid);
				claim.sendObject(cObject);

				List<AtavismBuildingObject> objectsToAdd = new ArrayList<AtavismBuildingObject>();
				if (template.getStages().get(cObject.stage + 1).getProgressColliders().containsKey(100)) {
					AtavismBuildingObject abc = new AtavismBuildingObject();
					abc.id = cObject.id;
					abc.setPosition(AOVector.add(cObject.loc, loc));
					abc.colliders = template.getStages().get(cObject.stage + 1).getProgressColliders().get(100).colliders;
					abc.setOrientation(cObject.orient);
					objectsToAdd.add(abc);
				}
				ArrayList<Integer> objectToDel = new ArrayList<Integer>();
				objectToDel.add(cObject.id);

				VoxelClient.AddDynamicObjects(instanceOID, objectToDel, objectsToAdd);
			}

			if (cObject.progress == 100) {
				// Do skillup chance
				if (template.skill > 0) {
					if (Log.loggingDebug)
						Log.debug("BUILD: checking skill: " + template.skill);
					// if (playerSkillLevel < skillLevelMax) {
					CombatClient.abilityUsed(playerOid, template.skill);
					// } else if (CraftingPlugin.GAIN_SKILL_AFTER_MAX && rand.nextInt(4) == 0) {
					// CombatClient.abilityUsed(playerOid, skill);
					// }
				}

				claim.upgradeClaimObject(this);
				completeStage(this);
				sendObjectInfo(playerOid,cObject.id);
			}
			Log.debug("runUpgrade End");
		}

		void runAttack() {
			claim.damageBuildObject(this);
		}

		void runRepair() {
			Log.debug("runRepair: getting player skill for repair");
			int playerSkillLevel = 1;
			if (template.skill > 0)
				playerSkillLevel = ClassAbilityClient.getPlayerSkillLevel(playerOid, template.skill);

			Log.debug("runRepair: checking success");

			// Remove item from players inventory
			for (ClaimObjectBuildItemEntry itemEntry : buildItems) {
				if (itemEntry.itemOid != null) {
					AgisInventoryClient.removeSpecificItem(playerOid, itemEntry.itemOid, false, itemEntry.count);
				}
			}

			// Do skillup chance
			if (template.skill > 0) {
				if (Log.loggingDebug)
					Log.debug("BUILD: checking skill: " + template.skill + " against playerSkillLevel: " + playerSkillLevel);
				// if (playerSkillLevel < skillLevelMax) {
				CombatClient.abilityUsed(playerOid, template.skill);
				// } else if (CraftingPlugin.GAIN_SKILL_AFTER_MAX && rand.nextInt(4) == 0) {
				// CombatClient.abilityUsed(playerOid, skill);
				// }
			}
			claim.tasks.remove(this);
			cObject.status = "";
			cObject.currentTime = 0L;
			cObject.totalTime = 0L;
			cObject.lastTimeUpdate = System.currentTimeMillis();
			
			claim.repairClaimObject(this);

			Log.debug("runRepair: End");
		}

		public void interrupt(OID playerOid) {
			/*
			 * interrupted = true; Map<String, Serializable> props = new HashMap<String,
			 * Serializable>(); props.put("ext_msg_subtype", "build_task_interrupted");
			 * props.put("claimID", id); props.put("id", template.id);
			 * TargetedExtensionMessage msg = new
			 * TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid,
			 * playerOid, false, props); Engine.getAgent().sendBroadcast(msg);
			 * EnginePlugin.setObjectProperty(playerOid, WorldManagerClient.NAMESPACE,
			 * AgisWorldManagerPlugin.PROP_ACTION_STATE, "");
			 */

		}
	}

	/**
	 * Sub-class needed for the interpolated world node so a perceiver can be
	 * created.
	 * 
	 * @author Andrew
	 *
	 */
	public class ClaimEntity extends ObjectStub implements EntityWithWorldNode {

		public ClaimEntity(OID oid, InterpolatedWorldNode node) {
			setWorldNode(node);
			setOid(oid);
		}

		private static final long serialVersionUID = 1L;
	}

	class ClaimObjectBuildItemEntry {
		public int itemID;
		public OID itemOid;
		public int count;

		public ClaimObjectBuildItemEntry(int itemID, OID itemOid, int count) {
			this.itemID = itemID;
			this.itemOid = itemOid;
			this.count = count;
		}
	}

	public static final int PERMISSION_INTERACTION = 1;
	public static final int PERMISSION_ADD_ONLY = 2;
	public static final int PERMISSION_ADD_DELETE = 3;
	public static final int PERMISSION_ADD_USERS = 4;
	public static final int PERMISSION_MANAGE_USERS = 5;
	public static final int PERMISSION_OWNER = 6;
}
