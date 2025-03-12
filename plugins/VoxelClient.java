package atavism.agis.plugins;

import java.util.ArrayList;
import java.util.List;

import atavism.agis.objects.AtavismBuildingObject;
import atavism.agis.objects.BuildObjectTemplate;
import atavism.agis.objects.CombatBuildingTarget;
import atavism.msgsys.*;
import atavism.server.messages.PropertyMessage;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;


/**
 * AGIS-specific calls for sending/getting messages to the VoxelPlugin
 */
public class VoxelClient {
	
	
	public static Point getBuildingPosition(int claimId, int objectId) {
		GetBuildingPositionMessage msg = new GetBuildingPositionMessage(claimId, objectId);
		Point point = (Point) Engine.getAgent().sendRPCReturnObject(msg);
		Log.debug("VOXEL CLIENT: GetBuildingPositionMessage hit");
		return point;
	}

	public static class GetBuildingPositionMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;

		public GetBuildingPositionMessage() {
			super();
		}

		public GetBuildingPositionMessage(int claimId, int objectId) {
			super();
			setMsgType(MSG_TYPE_GET_BUILDING_LOC);
			setClaimID(claimId);
			setObjectID(objectId);
		}

		public int getClaimID() {
			return claimId;
		}

		public void setClaimID(int claimId) {
			this.claimId = claimId;
		}

		int claimId;

		public int getObjectID() {
			return objectId;
		}

		public void setObjectID(int objectId) {
			this.objectId = objectId;
		}

		int objectId;
	}
	
	public static boolean getBuildingIsAttackable(int claimId, int objectId, OID playerOID) {
		GetBuildingIsAttackableMessage msg = new GetBuildingIsAttackableMessage(claimId, objectId, playerOID);
		boolean point = (boolean) Engine.getAgent().sendRPCReturnObject(msg);
		Log.debug("VOXEL CLIENT: getBuildingIsAttackable hit");
		return point;
	}

	public static class GetBuildingIsAttackableMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;

		public GetBuildingIsAttackableMessage() {
			super();
		}

		public GetBuildingIsAttackableMessage(int claimId, int objectId, OID playerOID) {
			super();
			setMsgType(MSG_TYPE_GET_BUILDING_IS_ATTACKABLE);
			setClaimID(claimId);
			setObjectID(objectId);
			setPlayerOID(playerOID);
		}

		public int getClaimID() {
			return claimId;
		}

		public void setClaimID(int claimId) {
			this.claimId = claimId;
		}

		int claimId;

		public int getObjectID() {
			return objectId;
		}

		public void setObjectID(int objectId) {
			this.objectId = objectId;
		}

		int objectId;
		
		public OID getPlayerOID() {
			return playerOID;
		}

		public void setPlayerOID(OID playerOID) {
			this.playerOID = playerOID;
		}

		OID playerOID;
	}
	
	
	
	
	public static void sendBuildingDamage(OID playerOid,int claimId, int objectId, int damage) {
		BuildingDamageMessage msg = new BuildingDamageMessage(playerOid,claimId, objectId, damage);
		 Engine.getAgent().sendBroadcast(msg);
		Log.debug("VOXEL CLIENT: GetBuildingPositionMessage hit");
		
	}
	
	public static class BuildingDamageMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;

		public BuildingDamageMessage() {
			super();
		}

		public BuildingDamageMessage(OID playerOid,int claimId, int objectId, int damage) {
			super();
			setMsgType(MSG_TYPE_BUILDING_DAMAGE);
			setClaimID(claimId);
			setObjectID(objectId);
			setDamage(damage);
			setAttacker(playerOid);
		}
		
		public OID getAttacker() {
			return playerOid;
		}

		public void setAttacker(OID playerOid) {
			this.playerOid = playerOid;
		}
		
		OID playerOid;
		
		public int getClaimID() {
			return claimId;
		}

		public void setClaimID(int claimId) {
			this.claimId = claimId;
		}

		int claimId;

		public int getObjectID() {
			return objectId;
		}

		public void setObjectID(int objectId) {
			this.objectId = objectId;
		}

		int objectId;

		public int getdamage() {
			return damage;
		}

		public void setDamage(int damage) {
			this.damage = damage;
		}

		int damage;
	}
	
	public static ArrayList<CombatBuildingTarget> getAoEBuildingTargetsInArea(OID caster, Point loc,Quaternion quaternion, float minRange, float maxRange,float angle, int claimId, int objectId) {
		BuildingAoETargetsInAreaMessage msg = new BuildingAoETargetsInAreaMessage(caster,claimId, objectId, minRange, maxRange, loc,quaternion,angle);
		ArrayList<CombatBuildingTarget> list = (ArrayList<CombatBuildingTarget>) Engine.getAgent().sendRPCReturnObject(msg);
		//if (Log.loggingDebug)Log.dumpStack("VOXEL CLIENT: getAoEBuildingTargetsInArea hit "+list);
		return list;

	}

	public static class BuildingAoETargetsInAreaMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;

		public BuildingAoETargetsInAreaMessage() {
			super();
		}

		public BuildingAoETargetsInAreaMessage(OID caster,int claimId, int objectId, float minRange, float maxRange, Point loc,Quaternion quaternion,float angle) {
			super(MSG_TYPE_GET_BUILDING_IN_AREA,caster);
			//setMsgType(MSG_TYPE_GET_BUILDING_IN_AREA);
			//setCaster(caster);
			setClaimID(claimId);
			setObjectID(objectId);
			setLoc(loc);
			setMinRange(minRange);
			setMaxRange(maxRange);
			setAngle(angle);
			setQuaternion(quaternion);
		}

		public OID getCaster() {
			return caster;
		}

		public void setCaster(OID caster) {
			this.caster = caster;
		}

		OID caster;

		
		public int getClaimID() {
			return claimId;
		}

		public void setClaimID(int claimId) {
			this.claimId = claimId;
		}

		int claimId;

		public int getObjectID() {
			return objectId;
		}

		public void setObjectID(int objectId) {
			this.objectId = objectId;
		}

		int objectId;

		public float getMinRange() {
			return minRange;
		}

		public void setMinRange(float minRange) {
			this.minRange = minRange;
		}

		float minRange;

		public float getMaxRange() {
			return maxRange;
		}

		public void setMaxRange(float maxRange) {
			this.maxRange = maxRange;
		}

		float maxRange;

		public float getAngle() {
			return angle;
		}

		public void setAngle(float angle) {
			this.angle = angle;
		}

		float angle;

		public Point getLoc() {
			return loc;
		}

		public void setLoc(Point loc) {
			this.loc = loc;
		}

		Point loc;
		
		public Quaternion getQuaternion() {
			return quaternion;
		}

		public void setQuaternion(Quaternion quaternion) {
			this.quaternion = quaternion;
		}

		Quaternion quaternion;

	}
	
	/**
	 * Function to retrive Building Definition
	 * @param templateID
	 * @return
	 */
	
	public static BuildObjectTemplate getBuildingTemplate(int templateID) {
		GetBuildingTemplateMessage msg = new GetBuildingTemplateMessage(templateID);
		BuildObjectTemplate template = (BuildObjectTemplate) Engine.getAgent().sendRPCReturnObject(msg);
		Log.debug("VOXEL CLIENT: GetBuildingTemplateMessage hit");
		return template;
	}
	
	public static class GetBuildingTemplateMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public GetBuildingTemplateMessage() {
            super();
        }
        
        public GetBuildingTemplateMessage(int templateID) {
        	super();
        	setMsgType(MSG_TYPE_GET_BUILDING_TEMPLATE);
        	setTemplateID(templateID);
        }
        
        public int getTemplateID() {
        	return templateID;
        }
        public void setTemplateID(int templateID) {
        	this.templateID = templateID;
        }
        int templateID;
	}
	
	public static OID getChestStorageOid(int chestID) {
		GetChestStorageOidMessage msg = new GetChestStorageOidMessage(chestID);
		OID storageOid = (OID) Engine.getAgent().sendRPCReturnObject(msg);
		Log.debug("VOXEL CLIENT: GetBuildingTemplateMessage hit");
		return storageOid;
	}
	
	public static class GetChestStorageOidMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public GetChestStorageOidMessage() {
            super();
        }
        
        public GetChestStorageOidMessage(int chestID) {
        	super();
        	setMsgType(MSG_TYPE_GET_CHEST_STORAGE_OID);
        	setChestID(chestID);
        }
        
        public int getChestID() {
        	return chestID;
        }
        public void setChestID(int chestID) {
        	this.chestID = chestID;
        }
        int chestID;
	}
	
	public static int SendClaimUpdate(int guildId) {
		SendClaimUpdateMessage msg = new SendClaimUpdateMessage(guildId);
		Log.debug("GUILD CLIENT: SendClaimUpdateMessage hit 2 "+guildId);
		return Engine.getAgent().sendBroadcast(msg);
	}

	public static class SendClaimUpdateMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
        public SendClaimUpdateMessage() {
            super();
        }
        
        public SendClaimUpdateMessage(int guildId) {
        	super();
        	setMsgType(MSG_TYPE_SEND_UPDATE_CLAIM);
          	setProperty("guildId", guildId);
          	Log.debug("GUILD CLIENT: SendClaimUpdateMessage hit 1");
        }
	}
	
	
	public static class AddDynamicObjectMessage extends SubjectMessage {
		private static final long serialVersionUID = 1L;
        public AddDynamicObjectMessage() {
            super();
        }
        
        public AddDynamicObjectMessage(OID instanceOid, List<Integer> objectToDestroy, List<AtavismBuildingObject> objectsToAdd) {
        	super(MSG_TYPE_ADD_DYNAMIC_NM_OBJECT,instanceOid);
        	setObjectToDestroy(objectToDestroy);
        	setOobjectsToAdd(objectsToAdd);
        }
        
        public List<Integer> getObjectToDestroy() {
        	return objectToDestroy;
        }
        public void setObjectToDestroy(List<Integer> objectToDestroy) {
        	this.objectToDestroy = objectToDestroy;
        }
        List<Integer> objectToDestroy;
        
        public List<AtavismBuildingObject> getObobjectsToAdd() {
        	return objectsToAdd;
        }
        public void setOobjectsToAdd(List<AtavismBuildingObject> objectsToAdd) {
        	this.objectsToAdd = objectsToAdd;
        }
        List<AtavismBuildingObject> objectsToAdd;
	}
	
	public static int AddDynamicObjects(OID instanceOid, List<Integer> objectToDestroy, List<AtavismBuildingObject> objectsToAdd) {
		AddDynamicObjectMessage msg = new AddDynamicObjectMessage(instanceOid,objectToDestroy,objectsToAdd);
		Log.debug("Claim: AddDynamicObjectMessage hit 2 ");
		return Engine.getAgent().sendBroadcast(msg);
	}
	
	
	public static final MessageType MSG_TYPE_ADD_DYNAMIC_NM_OBJECT = MessageType.intern("ao.ADD_DYNAMIC_NM_OBJECT");

	public static final MessageType MSG_TYPE_CREATE_CLAIM = MessageType.intern("voxel.CREATE_CLAIM");
	public static final MessageType MSG_TYPE_SEND_UPDATE_CLAIM = MessageType.intern("voxel.SEND_UPDATE_CLAIM");
	public static final MessageType MSG_TYPE_EDIT_CLAIM = MessageType.intern("voxel.EDIT_CLAIM");
	public static final MessageType MSG_TYPE_UPGRADE_CLAIM = MessageType.intern("voxel.UPGRADE_CLAIM");
	public static final MessageType MSG_TYPE_PURCHASE_CLAIM = MessageType.intern("voxel.PURCHASE_CLAIM");
	public static final MessageType MSG_TYPE_SELL_CLAIM = MessageType.intern("voxel.SELL_CLAIM");
	public static final MessageType MSG_TYPE_DELETE_CLAIM = MessageType.intern("voxel.DELETE_CLAIM");
	public static final MessageType MSG_TYPE_PAY_TAX_CLAIM= MessageType.intern("voxel.PAY_TAX_CLAIM");
	public static final MessageType MSG_TYPE_CLAIM_PERMISSION = MessageType.intern("voxel.CLAIM_PERMISSION");
	public static final MessageType MSG_TYPE_CLAIM_ACTION = MessageType.intern("voxel.CLAIM_ACTION");
	public static final MessageType MSG_TYPE_PLACE_CLAIM_OBJECT = MessageType.intern("voxel.PLACE_CLAIM_OBJECT");
	public static final MessageType MSG_TYPE_EDIT_CLAIM_OBJECT = MessageType.intern("voxel.EDIT_CLAIM_OBJECT");
	public static final MessageType MSG_TYPE_GET_RESOURCES = MessageType.intern("voxel.GET_RESOURCES");
	public static final MessageType MSG_TYPE_NO_BUILD_CLAIM_TRIGGER = MessageType.intern("voxel.NO_BUILD_CLAIM_TRIGGER");
	public static final MessageType MSG_TYPE_UPGRADE_BUILDING_OBJECT = MessageType.intern("voxel.UPGRADE_BUILDING_OBJECT");
	public static final MessageType MSG_TYPE_GET_BUILDING_TEMPLATE = MessageType.intern("voxel.GET_BUILDING_TEMPLATE");
	public static final MessageType MSG_TYPE_GET_BUILDING_LOC = MessageType.intern("voxel.GET_BUILDING_LOC");
	public static final MessageType MSG_TYPE_BUILDING_DAMAGE = MessageType.intern("voxel.BUILDING_DAMAGE");
	public static final MessageType MSG_TYPE_GET_BUILDING_IN_AREA = MessageType.intern("voxel.GET_BUILDING_IN_AREA");
	public static final MessageType MSG_TYPE_GET_BUILDING_IS_ATTACKABLE = MessageType.intern("voxel.GET_BUILDING_IS_ATTACKABLE");
	public static final MessageType MSG_TYPE_GET_CLAIM_OBJECT_INFO = MessageType.intern("voxel.GET_CLAIM_OBJECT_INFO");
	public static final MessageType MSG_TYPE_ATTACK_BUILDING_OBJECT = MessageType.intern("voxel.ATTACK_BUILDING_OBJECT");
	public static final MessageType MSG_TYPE_TAKE_CLAIM_RESOURCE = MessageType.intern("voxel.TAKE_CLAIM_RESOURCE");
	public static final MessageType MSG_TYPE_GET_CHEST_STORAGE_OID = MessageType.intern("voxel.GET_CHEST_STORAGE_OID");
}