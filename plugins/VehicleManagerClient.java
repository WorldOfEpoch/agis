package atavism.agis.plugins;

import atavism.server.engine.Engine;
import atavism.server.engine.EnginePlugin;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.objects.SpawnData;
import atavism.server.util.Log;

import java.io.Serializable;

import atavism.msgsys.Message;
import atavism.msgsys.MessageType;
import atavism.msgsys.SubjectMessage;

public class VehicleManagerClient {
	
    private VehicleManagerClient()
    {
    }
    
    // Methods for creating and managing vehicle spawn generators
    public static boolean createVehicleSpawnGenerator(SpawnData spawnData) {
        Log.error("createVehicleSpawnGenerator: " + spawnData);
        CreateVehicleSpawnGeneratorMessage message = new CreateVehicleSpawnGeneratorMessage(spawnData);
        return Engine.getAgent().sendRPCReturnBoolean(message).booleanValue();
    }

    public static class CreateVehicleSpawnGeneratorMessage extends SubjectMessage {
        private SpawnData spawnData;
        
        public CreateVehicleSpawnGeneratorMessage(SpawnData spawnData) {
            super(MSG_TYPE_CREATE_VEHICLE_SPAWN_GEN);
            this.spawnData = spawnData;
            setSubject(spawnData.getInstanceOid());
        }
        
        public SpawnData getSpawnData() {
            return this.spawnData;
        }
    }

    // Methods for deleting vehicle spawn generators
    public static boolean deleteVehicleSpawn(OID instanceOid, int spawnerId) {
        Log.error("VehicleManagerClient: deleteVehicleSpawn: instanceOid=" + instanceOid + ", spawnerId=" + spawnerId);
        DeleteVehicleSpawnMessage message = new DeleteVehicleSpawnMessage(instanceOid, spawnerId);
        return Engine.getAgent().sendRPCReturnBoolean(message).booleanValue();
    }

    public static class DeleteVehicleSpawnMessage extends SubjectMessage {
        private int spawnerId;
        
        public DeleteVehicleSpawnMessage(OID instanceOid, int spawnerId) {
            super(MSG_TYPE_REMOVE_VEHICLE_SPAWN);
            this.spawnerId = spawnerId;
            setSubject(instanceOid);
        }
        
        public int getSpawnId() {
            return this.spawnerId;
        }
    }

    // Example for setting a vehicle-specific attribute, e.g., fuel capacity
    public static void setVehicleAttribute(OID vehicleId, String attributeName, Serializable attributeValue) {
        // Implementation for setting vehicle-specific attributes
    }
    
	public static boolean deleteSpawnGenerator(OID instanceOid, int spawnerId) {
		Log.error("VehicleManagerClient: deleteSpawnGenerator instanceOid="+instanceOid+" spawnerId="+spawnerId);
		DeleteSpawnGeneratorMessage message = new DeleteSpawnGeneratorMessage(instanceOid,spawnerId);

		return Engine.getAgent().sendRPCReturnBoolean(message);
	}

	public static class DeleteSpawnGeneratorMessage extends SubjectMessage {
		public DeleteSpawnGeneratorMessage() {
		}

		public DeleteSpawnGeneratorMessage(OID instanceOid, int spawnerId) {
			super(MSG_TYPE_REMOVE_VEHICLE_SPAWN);
			Log.error("VehicleManagerClient: DeleteSpawnGeneratorMessage InstanceOid="+instanceOid);
			
			setSpawnId(spawnerId);
			setSubject(instanceOid);
		}

		public int getSpawnId() {
			return spawnerId;
		}

		public void setSpawnId(int spawnerId) {
			this.spawnerId = spawnerId;
		}

		private int spawnerId;

		private static final long serialVersionUID = 1L;
	}
	
	public static boolean createSpawnGenerator(SpawnData spawnData) {
		Log.error("VehicleManagerClient: createSpawnGenerator "+spawnData);
		CreateSpawnGeneratorMessage message = new CreateSpawnGeneratorMessage(spawnData);

		return Engine.getAgent().sendRPCReturnBoolean(message);
	}

	public static class CreateSpawnGeneratorMessage extends SubjectMessage {
		public CreateSpawnGeneratorMessage() {
		}

		public CreateSpawnGeneratorMessage(SpawnData spawnData) {
			super(MSG_TYPE_CREATE_VEHICLE_SPAWN_GEN);
			Log.error("VehicleManagerClient: CreateSpawnGeneratorMessage InstanceOid="+spawnData.getInstanceOid());
			
			setSpawnData(spawnData);
			setSubject(spawnData.getInstanceOid());
		}

		public SpawnData getSpawnData() {
			return spawnData;
		}

		public void setSpawnData(SpawnData spawnData) {
			this.spawnData = spawnData;
		}

		private SpawnData spawnData;

		private static final long serialVersionUID = 1L;
	}

    // Message types specific to vehicle management
    public static MessageType MSG_TYPE_REMOVE_VEHICLE_SPAWN = MessageType.intern("vehicle.REMOVE_VEHICLE_SPAWN");
    public static MessageType MSG_TYPE_CREATE_VEHICLE_SPAWN = MessageType.intern("vehicle.CREATE_VEHICLE_SPAWN");
	public static MessageType MSG_TYPE_DESPAWN_REQ = MessageType.intern("ao.DESPAWN_REQ");

	
	public static Namespace INSTANCE_NAMESPACE = null;
	public static final String TEMPL_VEHICLE_NAME = ":vehicleserverName";

    // Namespace for vehicle management
    public static Namespace NAMESPACE = null;
}
