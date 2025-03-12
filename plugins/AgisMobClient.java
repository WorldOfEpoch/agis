package atavism.agis.plugins;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;

import atavism.msgsys.*;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.messages.PropertyMessage;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.objects.InstanceTemplate;
import atavism.server.objects.SpawnData;


/**
 * AGIS-specific calls for sending/getting messages to the AgisInventoryPlugin
 */
public class AgisMobClient {
	
	/*public static InstanceTemplate getInstanceTemplate(int instanceTemplateID) {
		GetInstanceTemplateMessage msg = new GetInstanceTemplateMessage(instanceTemplateID);
		InstanceTemplate tmpl = (InstanceTemplate) Engine.getAgent().sendRPCReturnObject(msg);
    	return tmpl;
    }
	
	public static class GetInstanceTemplateMessage extends GenericMessage {
    	private static final long serialVersionUID = 1L;
    	public int instanceTemplateID;
    	
    	public GetInstanceTemplateMessage() {
    		super(MSG_TYPE_GET_INSTANCE_TEMPLATE);
    	}
    	
    	public GetInstanceTemplateMessage(int instanceTemplateID) {
    		super(MSG_TYPE_GET_INSTANCE_TEMPLATE);
    		this.instanceTemplateID = instanceTemplateID;
    	}
    }
	*/
	
	/*public static String createMobFactory(SpawnData sd) {
		
		Engine.getAgent().sendRPCReturnString(message)
	}
	
	public static class createMobFactoryMessage extends SubjectMessage {
    	private static final long serialVersionUID = 1L;
    	public SpawnData sd;
    	public int spawnDataID;
    	public Point loc;
    	public Quaternion orient;
    	public OID instanceOid;
    	public boolean oneOffSpawn = false;
    	
    	public SpawnMobMessage() {
    		super(MSG_TYPE_SPAWN_MOB);
    	}
    	
    	public SpawnMobMessage(SpawnData sd) {
    		super(MSG_TYPE_SPAWN_MOB);
    		this.setSubject(sd.getInstanceOid());
    		this.sd = sd;
    	}
    	
    	public SpawnMobMessage(int spawnDataID, Point loc, Quaternion orient, OID instanceOid) {
    		super(MSG_TYPE_SPAWN_MOB);
    		this.spawnDataID = spawnDataID;
    		this.loc = loc;
    		this.orient = orient;
    		this.instanceOid = instanceOid;
    		this.setSubject(instanceOid);
    	}
    	
    	public SpawnMobMessage(int spawnDataID, Point loc, Quaternion orient, OID instanceOid, boolean oneOff) {
    		super(MSG_TYPE_SPAWN_MOB);
    		this.spawnDataID = spawnDataID;
    		this.loc = loc;
    		this.orient = orient;
    		this.instanceOid = instanceOid;
    		this.oneOffSpawn = oneOff;
    		this.setSubject(instanceOid);
    	    }
    	
    }*/


	public static void sendPetLevelUpdate(OID oid) {
		PetLevelUpMessage msg = new PetLevelUpMessage(oid);
		Log.debug("sendPetLevelUpdate oid="+oid);
		Engine.getAgent().sendBroadcast(msg);
	}

	public static void sendPetLevelUpdate(OID oid, int profile) {
		PetLevelUpMessage msg = new PetLevelUpMessage(oid);
		msg.setProperty("profile", profile);
		Log.debug("sendPetLevelUpdate oid="+oid);
		Engine.getAgent().sendBroadcast(msg);
	}

	public static class PetLevelUpMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;

		public PetLevelUpMessage() {
			super(MSG_TYPE_PET_LEVELUP);
		}

		public PetLevelUpMessage(OID oid) {
			super(MSG_TYPE_PET_LEVELUP,oid);
		}

	}


	public static void spawnInteractiveObject(OID instanceOid, int interactiveObjectTemplate , AOVector position){
		SpawnInteractiveObjectMessage msg = new SpawnInteractiveObjectMessage(instanceOid, interactiveObjectTemplate, position);
		Log.debug("spawnInteractiveObject: "+instanceOid+" "+interactiveObjectTemplate+" "+position);
		Engine.getAgent().sendBroadcast(msg);
	}


	public static class SpawnInteractiveObjectMessage extends SubjectMessage {
		private static final long serialVersionUID = 1L;
		protected int interactiveObjectTemplate;
		protected AOVector position;
		protected OID instanceOid;

		public OID getInstanceOid() {
			return instanceOid;
		}

		public void setInstanceOid(OID instanceOid) {
			this.instanceOid = instanceOid;
		}

		public int getInteractiveObjectTemplate() {
			return interactiveObjectTemplate;
		}

		public void setInteractiveObjectTemplate(int interactiveObjectTemplate) {
			this.interactiveObjectTemplate = interactiveObjectTemplate;
		}

		public AOVector getPosition() {
			return position;
		}

		public void setPosition(AOVector position) {
			this.position = position;
		}

		public SpawnInteractiveObjectMessage() {
			super(MSG_TYPE_SPAWN_INTERACTIVE_OBJECT);
		}

		public SpawnInteractiveObjectMessage(OID instanceOid, int interactiveObjectTemplate , AOVector position) {
			super(MSG_TYPE_SPAWN_INTERACTIVE_OBJECT);
			this.interactiveObjectTemplate = interactiveObjectTemplate;
			this.instanceOid = instanceOid;
			this.position = position;
			this.setSubject(instanceOid);
		}

	}



	public static void spawnInstanceObjects(InstanceTemplate tmpl, OID instanceOid) {
		SpawnInstanceMobsMessage msg = new SpawnInstanceMobsMessage(tmpl, instanceOid);
		Log.debug("spawnInstanceObjects: "+tmpl+" "+instanceOid);
		Engine.getAgent().sendBroadcast(msg);
	}

	public static class SpawnInstanceMobsMessage extends SubjectMessage {
		private static final long serialVersionUID = 1L;
		public InstanceTemplate tmpl;
		public OID instanceOid;

		public SpawnInstanceMobsMessage() {
			super(MSG_TYPE_SPAWN_INSTANCE_MOBS);
		}

		public SpawnInstanceMobsMessage(InstanceTemplate tmpl, OID instanceOid) {
			super(MSG_TYPE_SPAWN_INSTANCE_MOBS);
			this.tmpl = tmpl;
			this.instanceOid = instanceOid;
			this.setSubject(instanceOid);
		}

	}

	public static void spawnMob(SpawnData sd) {
		SpawnMobMessage msg = new SpawnMobMessage(sd);
		Log.debug("run Agis.MobClient.spawnMob");
		Engine.getAgent().sendBroadcast(msg);
    }
	
	public static void spawnMob(int spawnDataID, Point loc, Quaternion orient, OID instanceOid, boolean oneOffSpawn) {
		SpawnMobMessage msg = new SpawnMobMessage(spawnDataID, loc, orient, instanceOid, oneOffSpawn);
		Engine.getAgent().sendBroadcast(msg);
    }
	
	public static class SpawnMobMessage extends SubjectMessage {
    	private static final long serialVersionUID = 1L;
    	public SpawnData sd;
    	public int spawnDataID;
    	public Point loc;
    	public Quaternion orient;
    	public OID instanceOid;
    	public boolean oneOffSpawn = false;
    	
    	public SpawnMobMessage() {
    		super(MSG_TYPE_SPAWN_MOB);
    	}
    	
    	public SpawnMobMessage(SpawnData sd) {
    		super(MSG_TYPE_SPAWN_MOB);
    		if(Log.loggingDebug)
    		Log.debug("SpawnMobMessage instance="+sd.getInstanceOid()+" "+sd.getName());
    		this.setSubject(sd.getInstanceOid());
    		this.sd = sd;
    	}
    	
    	public SpawnMobMessage(int spawnDataID, Point loc, Quaternion orient, OID instanceOid) {
    		super(MSG_TYPE_SPAWN_MOB);
    		this.spawnDataID = spawnDataID;
    		this.loc = loc;
    		this.orient = orient;
    		this.instanceOid = instanceOid;
    		this.setSubject(instanceOid);
    	}
    	
    	public SpawnMobMessage(int spawnDataID, Point loc, Quaternion orient, OID instanceOid, boolean oneOff) {
    		super(MSG_TYPE_SPAWN_MOB);
    		this.spawnDataID = spawnDataID;
    		this.loc = loc;
    		this.orient = orient;
    		this.instanceOid = instanceOid;
    		this.oneOffSpawn = oneOff;
    		this.setSubject(instanceOid);
    	    }
    	
    }
	
	public static void deleteSpawnGenerator(String spawnGeneratorName, OID instanceOid) {
		DeleteSpawnGeneratorMessage msg = new DeleteSpawnGeneratorMessage(spawnGeneratorName, instanceOid);
		Engine.getAgent().sendBroadcast(msg);
    }
	
	public static class DeleteSpawnGeneratorMessage extends SubjectMessage {
    	private static final long serialVersionUID = 1L;
    	public String spawnGeneratorName;
    	public OID instanceOid;
    	
    	public DeleteSpawnGeneratorMessage() {
    		super(MSG_TYPE_DELETE_SPAWN_GENERATOR);
    	}
    	
    	public DeleteSpawnGeneratorMessage(String spawnGeneratorName, OID instanceOid) {
    		super(MSG_TYPE_DELETE_SPAWN_GENERATOR,instanceOid);
    		this.spawnGeneratorName = spawnGeneratorName;
    		this.instanceOid = instanceOid;
    	}
    }
	
	/**
	 * Sends the createAvatarCloneMessage which will create a clone NPC of the 
	 * player.
	 * Has not been tested recently.
	 */
	public static void createAvatarClone(Long oid, Long instanceOid) {
		createAvatarCloneMessage msg = new createAvatarCloneMessage(oid, instanceOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("AGISMOB CLIENT: createAvatarCloneMessage hit 2");
	}

	/**
	 * Message indicating that a clone of the player is to be created
	 * in the specified instance.
	 * @author Andrew Harrison
	 *
	 */
	public static class createAvatarCloneMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;
        public createAvatarCloneMessage() {
            super();
            Log.debug("AGISMOB CLIENT: createAvatarCloneMessage hit 1");
        }
        public createAvatarCloneMessage(Long oid, Long instanceOid) {
        	super();
        	setMsgType(MSG_TYPE_CREATE_AVATAR_CLONE);
        	setProperty("clonerOid", oid);
        	setProperty("instanceOid", instanceOid);
        	Log.debug("AGISMOB CLIENT: createAvatarCloneMessage hit 1");
        }
	}
	
	/**
	 * Sends the spawnArenaCreatureMessage which will spawn a mob based on the spawnData
	 * specified in the arena instance.
	 */
	public static void spawnArenaCreature(int spawnDataID, int arenaID, OID instanceOid) {
		spawnArenaCreatureMessage msg = new spawnArenaCreatureMessage(spawnDataID, arenaID, instanceOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("AGISMOB CLIENT: spawnArenaCreatureMessage hit 2");
	}

	/**
	 * Message used to spawn a creature in an Arena Instance.
	 * @author Andrew Harrison
	 *
	 */
	public static class spawnArenaCreatureMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public spawnArenaCreatureMessage() {
            super();
        }
        public spawnArenaCreatureMessage(int spawnDataID, int arenaID, OID instanceOid) {
        	super();
        	setMsgType(MSG_TYPE_SPAWN_ARENA_CREATURE);
        	setProperty("spawnDataID", spawnDataID);
        	setProperty("arenaID", arenaID);
        	setProperty("instanceOid", instanceOid);
        	Log.debug("AGISMOB CLIENT: spawnArenaCreatureMessage hit 1");
        }
	}
	
	/**
	 * Sends the spawnPetMessage which will spawn a pet for the player in question.
	 */
	public static void spawnPet(OID oid, OID instanceOid, Serializable mobName, int petType, Long duration, int passiveEffect, int skillType) {
		spawnPetMessage msg = new spawnPetMessage(oid,instanceOid, mobName, petType, duration, passiveEffect, skillType);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("AGISMOB CLIENT: spawnPetMessage hit 2");
	}

	/**
	 * Message used to spawn a pet for a player
	 * @author Andrew Harrison
	 *
	 */
	public static class spawnPetMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public spawnPetMessage() {
            super();
        }
        public spawnPetMessage(OID oid,OID instanceOid, Serializable mobName, int petType, Long duration, int passiveEffect, int skillType) {
        	super(MSG_TYPE_SPAWN_PET, instanceOid);
        	setProperty("mobID", mobName);
        	setProperty("petType", petType);
        	setProperty("plyOid", oid);
        	setProperty("duration", duration);
        	setProperty("passiveEffect", passiveEffect);
        	setProperty("skillType", skillType);
        	Log.debug("AGISMOB CLIENT: spawnPetMessage hit 1");
        }
	}
	
	/**
	 * Sends the TameBeastMessage which indicates the player has tamed
	 * a beast.
	 * @param oid
	 * @param mobOid
	 * @param skillType
	 */
	public static void tameBeast(OID oid, OID mobOid, int skillType) {
		tameBeastMessage msg = new tameBeastMessage(oid, mobOid, skillType);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("AGISMOB CLIENT: tameBeastMessage hit 2");
	}

	/**
	 * Message used to indicate a beast has been tamed.
	 * @author Andrew
	 *
	 */
	public static class tameBeastMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public tameBeastMessage() {
            super();
        }
        public tameBeastMessage(OID oid, OID mobOid, int skillType) {
        	super(MSG_TYPE_TAME_BEAST, oid);
        	setProperty("mobOid", mobOid);
        	setProperty("skillType", skillType);
        	Log.debug("AGISMOB CLIENT: tameBeastMessage hit 1");
        }
	}
	
	/**
	 * Sends the petCommandUpdateMessage which will update the pets behaviour.
	 * Usually called from the Pet class.
	 */
	public static void petCommandUpdate(OID oid, int command, OID target) {
		petCommandUpdateMessage msg = new petCommandUpdateMessage(oid, command, target);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("AGISMOB CLIENT: petCommandUpdateMessage hit 2");
	}

	/**
	 * Message used to update the behaviour for the pet.
	 * @author Andrew Harrison
	 *
	 */
	public static class petCommandUpdateMessage extends SubjectMessage {
		private static final long serialVersionUID = 1L;
        public petCommandUpdateMessage() {
            super();
            Log.debug("AGISMOB CLIENT: petCommandUpdateMessage hit 1");
        }
        public petCommandUpdateMessage(OID oid, int command, OID target) {
        	super(MSG_TYPE_PET_COMMAND_UPDATE, oid);
        	setCommand(command);
        	setTarget(target);
        	Log.debug("AGISMOB CLIENT: petCommandUpdateMessage hit 1");
        }
        
        public int getCommand() {
            return command;
        }
        public void setCommand(int command) {
            this.command = command;
        }
        int command;
        
        public OID getTarget() {
            return target;
        }
        public void setTarget(OID target) {
            this.target = target;
        }
        OID target;
	}
	
	/**
	 * Sends the sendPetCommandMessage which will try to update the summoned 
	 * pets command. Usually called from a client command.
	 */
	public static void sendPetCommand(OID oid, OID targetOid, String command) {
		sendPetCommandMessage msg = new sendPetCommandMessage(oid, targetOid, command);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("AGISMOB CLIENT: sendPetCommandMessage hit 2");
	}

	/**
	 * Message used to update the summoned pets command.
	 * @author Andrew Harrison
	 *
	 */
	public static class sendPetCommandMessage extends SubjectMessage {
		private static final long serialVersionUID = 1L;
        public sendPetCommandMessage() {
            super();
        }
        public sendPetCommandMessage(OID oid, OID targetOid, String command) {
        	super(MSG_TYPE_SEND_PET_COMMAND, oid);
        	setTargetOid(targetOid);
        	setCommand(command);
        	Log.debug("AGISMOB CLIENT: sendPetCommandMessage hit 1");
        }
        
        public void setTargetOid(OID targetOid) {
        	this.targetOid = targetOid;
        }
        public OID getTargetOid() {
        	return targetOid;
        }
        public void setCommand(String command) {
        	this.command = command;
        }
        public String getCommand() {
        	return command;
        }
        OID targetOid;
        String command;
	}
	
	/**
	 * Sends the UpdatePetStatsMessage.
	 * Not too sure where it goes from here.
	 * @param oid
	 * @param mobOid
	 * @param level
	 * @param baseStat
	 */
	public static void updatePetStats(OID oid, OID mobOid, int level, int baseStat) {
		updatePetStatsMessage msg = new updatePetStatsMessage(oid, mobOid, level, baseStat);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("AGISMOB CLIENT: updatePetStatsMessage hit 2");
	}

	/**
	 * Message used to update the stats for a pet based on its level.
	 * This Message does not appear to be caught anywhere, needs investigating.
	 * @author Andrew Harrison
	 *
	 */
	public static class updatePetStatsMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public updatePetStatsMessage() {
            super();
        }
        public updatePetStatsMessage(OID oid, OID petOid, int level, int baseStat) {
        	super(MSG_TYPE_UPDATE_PET_STATS, oid);
        	setProperty("petOid", petOid);
        	setProperty("level", level);
        	setProperty("baseStat", baseStat);
        	Log.debug("AGISMOB CLIENT: updatePetStatsMessage hit 1");
        }
	}
	
	/**
	 * Sends the petTargetLostMessage which will cause the pet to follow the 
	 * owner if it was in attack mode.
	 */
	public static void petTargetLost(OID oid) {
		petTargetLostMessage msg = new petTargetLostMessage(oid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("AGISMOB CLIENT: petTargetLostMessage hit 2");
	}

	/**
	 * Message used to indicate the Pet has lost its target and should change
	 * its current action.
	 * @author Andrew Harrison
	 *
	 */
	public static class petTargetLostMessage extends SubjectMessage {
		private static final long serialVersionUID = 1L;
        public petTargetLostMessage() {
            super();
        }
        public petTargetLostMessage(OID oid) {
        	super(MSG_TYPE_PET_TARGET_LOST, oid);
        	Log.debug("AGISMOB CLIENT: petTargetLostMessage hit 1");
        }
	}
	
	/**
	 * Obsolete, to be deleted.
	 * @param oid
	 * @param targetOid
	 * @param distance
	 */
	public static void targetInReactionRange(OID oid, OID targetOid, float distance) {
		targetInReactionRangeMessage msg = new targetInReactionRangeMessage(oid, targetOid, distance);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("AGISMOB CLIENT: targetInReactionRangeMessage hit 2");
	}

	/**
	 * Obsolete, to be deleted.
	 * @author Andrew
	 *
	 */
	public static class targetInReactionRangeMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public targetInReactionRangeMessage() {
            super();
        }
        public targetInReactionRangeMessage(OID oid, OID targetOid, float distance) {
        	super(MSG_TYPE_TARGET_IN_REACTION_RANGE, oid);
        	setProperty("targetOid", targetOid);
        	setProperty("distance", distance);
        	Log.debug("AGISMOB CLIENT: targetInReactionRangeMessage hit 1");
        }
	}
	
	/**
	 * Obsolete, to be deleted.
	 * @param oid
	 * @param targetOid
	 */
	public static void addTargetToCheck(OID oid, OID targetOid) {
		addTargetToCheckMessage msg = new addTargetToCheckMessage(oid, targetOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("AGISMOB CLIENT: addTargetToCheckMessage hit 2");
	}

	/**
	 * Obsolete, to be deleted.
	 * @author Andrew
	 *
	 */
	public static class addTargetToCheckMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public addTargetToCheckMessage() {
            super();
        }
        public addTargetToCheckMessage(OID oid, OID targetOid) {
        	super(MSG_TYPE_ADD_TARGET_TO_CHECK, oid);
        	setProperty("targetOid", targetOid);
        	Log.debug("AGISMOB CLIENT: addTargetToCheckMessage hit 1");
        }
	}
	
	/**
	 * Obsolete, to be deleted.
	 * @param oid
	 * @param targetOid
	 */
	public static void removeTargetToCheck(OID oid, OID targetOid) {
		removeTargetToCheckMessage msg = new removeTargetToCheckMessage(oid, targetOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("AGISMOB CLIENT: removeTargetToCheckMessage hit 2");
	}

	/**
	 * Obsolete, to be deleted.
	 * @author Andrew
	 *
	 */
	public static class removeTargetToCheckMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public removeTargetToCheckMessage() {
            super();
        }
        public removeTargetToCheckMessage(OID oid, OID targetOid) {
        	super(MSG_TYPE_REMOVE_TARGET_TO_CHECK, oid);
        	setProperty("targetOid", targetOid);
        	Log.debug("AGISMOB CLIENT: removeTargetToCheckMessage hit 1");
        }
	}
	
	/**
	 * Sends the getIslandsDataMessage which results in a list of islands 
	 * being sent to the player.
	 * Wants renamed to instances or worlds.
	 */
	public static void getIslandsData(OID oid) {
		getIslandsDataMessage msg = new getIslandsDataMessage(oid);
		Engine.getAgent().sendBroadcast(msg);
	}

	/**
	 * Message used to request the list of islands to be sent down to the player.
	 * Wants renamed to instances or worlds.
	 * @author Andrew Harrison.
	 *
	 */
	public static class getIslandsDataMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public getIslandsDataMessage() {
            super();
        }
        public getIslandsDataMessage(OID oid) {
        	super(MSG_TYPE_GET_ISLANDS_DATA, oid);
        }
	}
	
	/**
	 * Sends the categoryUpdatedMessage which lets subobjects know to change 
	 * their current category setting.
	 */
	public static void categoryUpdated(OID oid, int category) {
		categoryUpdatedMessage msg = new categoryUpdatedMessage(oid, category);
		Engine.getAgent().sendBroadcast(msg);
	}

	/**
	 * Message used to indicate the category of islands/worlds the player is in
	 * has changed.
	 * @author Andrew Harrison
	 *
	 */
	public static class categoryUpdatedMessage extends PropertyMessage {
		private static final long serialVersionUID = 1L;
        public categoryUpdatedMessage() {
            super();
        }
        public categoryUpdatedMessage(OID oid, int category) {
        	super(MSG_TYPE_CATEGORY_UPDATED, oid);
        	setProperty("category", category);
        }
	}
	
	/**
	 * Sends the GetNpcInteractionsMessage which requests a list of possible
	 * interactions with the specified NPC to be sent to the client.
	 * @param oid
	 * @param playerOid
	 */
	public static void getNpcInteractions(OID oid, OID playerOid) {
		GetNpcInteractionsMessage msg = new GetNpcInteractionsMessage(oid, playerOid);
		Engine.getAgent().sendBroadcast(msg);
	}

	/**
	 * Message used for requesting the list of interactions the NPC specified
	 * has available to be sent to the client.
	 * @author Andrew Harrison
	 *
	 */
	public static class GetNpcInteractionsMessage extends SubjectMessage {

        public GetNpcInteractionsMessage() {
            super(MSG_TYPE_GET_INTERACTION_OPTIONS);
        }

        GetNpcInteractionsMessage(OID npcOid, OID playerOid) {
            super(MSG_TYPE_GET_INTERACTION_OPTIONS, npcOid);
            setPlayerOid(playerOid);
        }
        
        OID playerOid = null;

        public OID getPlayerOid() {
            return playerOid;
        }

        public void setPlayerOid(OID playerOid) {
            this.playerOid = playerOid;
        }

        private static final long serialVersionUID = 1L;
    }
	
	/**
	 * Sends the StartNpcInteractionMessage alerting the NPC to start the 
	 * specified interaction.
	 */
	public static void startNpcInteraction(OID oid, OID playerOid, int interactionID, String interactionType) {
		StartNpcInteractionMessage msg = new StartNpcInteractionMessage(oid, playerOid, interactionID, interactionType);
		Engine.getAgent().sendBroadcast(msg);
	}

	/**
	 * Message used to start the specified NPC interaction.
	 * @author Andrew Harrison
	 *
	 */
	public static class StartNpcInteractionMessage extends SubjectMessage {

        public StartNpcInteractionMessage() {
            super(MSG_TYPE_START_INTERACTION);
        }

        StartNpcInteractionMessage(OID npcOid, OID playerOid, int interactionID, String interactionType) {
            super(MSG_TYPE_START_INTERACTION, npcOid);
            setPlayerOid(playerOid);
            setInteractionID(interactionID);
            setInteractionType(interactionType);
        }
        
        OID playerOid = null;
        int interactionID;
        String interactionType;

        public OID getPlayerOid() {
            return playerOid;
        }
        public void setPlayerOid(OID playerOid) {
            this.playerOid = playerOid;
        }
        
        public int getInteractionID() {
            return interactionID;
        }
        public void setInteractionID(int interactionID) {
            this.interactionID = interactionID;
        }
        
        public String getInteractionType() {
            return interactionType;
        }
        public void setInteractionType(String interactionType) {
            this.interactionType = interactionType;
        }

        private static final long serialVersionUID = 1L;
    }
	
	/**
	 * Sends DialogueOptionChosenMessage alerting the NPC that the player has 
	 * chosen a dialogue option.
	 */
	public static void chooseDialogueOption(OID oid, OID playerOid, int dialogueID, int actionID, String interactionType) {
		DialogueOptionChosenMessage msg = new DialogueOptionChosenMessage(oid, playerOid, dialogueID, actionID, interactionType);
		Engine.getAgent().sendBroadcast(msg);
	}

	/**
	 * Message used to indicate what option a player has chosen from a dialogue.
	 * @author Andrew Harrison
	 *
	 */
	public static class DialogueOptionChosenMessage extends SubjectMessage {

        public DialogueOptionChosenMessage() {
            super(MSG_TYPE_DIALOGUE_OPTION_CHOSEN);
        }

        DialogueOptionChosenMessage(OID npcOid, OID playerOid, int dialogueID, int actionID, String interactionType) {
            super(MSG_TYPE_DIALOGUE_OPTION_CHOSEN, npcOid);
            setPlayerOid(playerOid);
            setDialogueID(dialogueID);
            setActionID(actionID);
            setInteractionType(interactionType);
        }
        
        OID playerOid = null;
        int dialogueID;
        int actionID;
        String interactionType;

        public OID getPlayerOid() {
            return playerOid;
        }
        public void setPlayerOid(OID playerOid) {
            this.playerOid = playerOid;
        }
        
        public int getDialogueID() {
            return dialogueID;
        }
        public void setDialogueID(int dialogueID) {
            this.dialogueID = dialogueID;
        }
        
        public int getActionID() {
            return actionID;
        }
        public void setActionID(int actionID) {
            this.actionID = actionID;
        }
        
        public String getInteractionType() {
            return interactionType;
        }
        public void setInteractionType(String interactionType) {
            this.interactionType = interactionType;
        }

        private static final long serialVersionUID = 1L;
    }
	
	public static void sendInvalidPath(OID mobOid) {
		InvalidPathMessage msg = new InvalidPathMessage(mobOid);
		Engine.getAgent().sendBroadcast(msg);
	}
	
	/**
	 * Message used to indicate what option a player has chosen from a dialogue.
	 * @author Andrew Harrison
	 *
	 */
	public static class InvalidPathMessage extends SubjectMessage {

        public InvalidPathMessage() {
            super(MSG_TYPE_INVALID_PATH);
        }

        public InvalidPathMessage(OID npcOid) {
            super(MSG_TYPE_INVALID_PATH, npcOid);
        }


        private static final long serialVersionUID = 1L;
    }
	
	
		public static LinkedList<OID> GetPlayersOnline() {
			GetPlayersOnlineMessage msg = new GetPlayersOnlineMessage();
	    	LinkedList<OID> contents = (LinkedList) Engine.getAgent().sendRPCReturnObject(msg);
	    	return contents;
		}
		public static LinkedList<OID> GetPlayersOnline(OID instanceOid) {
			GetPlayersOnlineMessage msg = new GetPlayersOnlineMessage();
			msg.setInstanceOid(instanceOid);
	    	LinkedList<OID> contents = (LinkedList) Engine.getAgent().sendRPCReturnObject(msg);
	    	return contents;
		}
	
	    public static class GetPlayersOnlineMessage extends SubjectMessage {
	    	public GetPlayersOnlineMessage() {
	            super(MSG_TYPE_GET_PLAYERS_ONLINE);
	        }
	        
	     /*   public GetPlayersOnlineMessage(OID playerOid) {
	            super(MSG_TYPE_GET_PLAYERS_ONLINE, playerOid);
	            //setStorageOid(storageOid);
	        }
	     
	       */
	        public OID getInstanceOid() {
	            return instanceOid;
	        }
	        public void setInstanceOid(OID instanceOid) {
	            this.instanceOid = instanceOid;
	        }
	        OID instanceOid;
	        
	      	       
	        private static final long serialVersionUID = 1L;
	    }
	    
	    public static void DialogCheck(OID playerOid) {
	    	DialogCheckMessage msg = new DialogCheckMessage(playerOid);
	    	  if (Log.loggingTrace) Log.trace("Send Dialog Check Message");
			Engine.getAgent().sendBroadcast(msg);
	    }
	    
	    public static class DialogCheckMessage extends SubjectMessage {
	    	public DialogCheckMessage() {
	            super(MSG_TYPE_DIALOG_CHECK);
	        }
	        
	        public DialogCheckMessage(OID playerOid) {
	            super(MSG_TYPE_DIALOG_CHECK, playerOid);
	            if (Log.loggingTrace) Log.trace("DialogCheckMessage");
				  }
	     
	        private static final long serialVersionUID = 1L;
	    }
	    
	    
	public static void CreateShopSpawn(OID playerOid, OID shopOid, int mobTemplate, int minutes, String message, boolean destroyOnLogOut) {
		CreateShopSpawnMessage msg = new CreateShopSpawnMessage(playerOid);
		msg.setShopOid(shopOid);
		msg.setMobTemplate(mobTemplate);
		msg.setShopTimeOut(minutes);
		msg.setShopMessage(message);
		msg.setShopDestroyOnLogOut(destroyOnLogOut);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("INSTANCE: CreateShopSpawn hit 2");
	}

	public static class CreateShopSpawnMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;

		public CreateShopSpawnMessage() {
			super();
		}

		public CreateShopSpawnMessage(OID playerOid) {
			setMsgType(MSG_TYPE_CREATE_SHOP_SPAWN);
			setPlayerOid(playerOid);
			Log.debug("INSTANCE: CreateShopSpawn hit 1");
		}

		public void setPlayerOid(OID playerOid) {
			this.playerOid = playerOid;
		}

		public OID getPlayerOid() {
			return this.playerOid;
		}

		OID playerOid;

		public void setMobTemplate(int mobTemplate) {
			this.mobTemplate = mobTemplate;
		}

		public int getMobTemplate() {
			return this.mobTemplate;
		}

		int mobTemplate = -1;

		public void setShopOid(OID shopOid) {
			this.shopOid = shopOid;
		}

		public OID getShopOid() {
			return this.shopOid;
		}

		OID shopOid;

		public void setShopTimeOut(int minutes) {
			this.shopTimeOut = minutes;
		}

		public int getShopTimeOut() {
			return this.shopTimeOut;
		}

		int shopTimeOut = -1;

		public void setShopMessage(String message) {
			shopMessage = message;
		}

		public String getShopMessage() {
			return shopMessage;
		}
		
		String shopMessage = "";

		public void setShopDestroyOnLogOut(boolean destroyOnLogOut) {
			this.destroyOnLogOut = destroyOnLogOut;
		}

		public boolean getShopDestroyOnLogOut() {
			return destroyOnLogOut;
		}

		boolean destroyOnLogOut = false;
	}
	
	public static void DeleteShopSpawn(OID shopOid) {
		DeleteShopSpawnMessage msg = new DeleteShopSpawnMessage(shopOid);

		Engine.getAgent().sendBroadcast(msg);
		Log.debug("INSTANCE: DeleteShopSpawnMessage hit 2");
	}

	public static class DeleteShopSpawnMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;

		public DeleteShopSpawnMessage() {
			super();
		}

		public DeleteShopSpawnMessage(OID shopOid) {
			setMsgType(MSG_TYPE_DELETE_SHOP_SPAWN);
			setShopOid(shopOid);
			Log.debug("INSTANCE: DeleteShopSpawnMessage hit 1");
		}

		public void setShopOid(OID shopOid) {
			this.shopOid = shopOid;
		}

		public OID getShopOid() {
			return this.shopOid;
		}

		OID shopOid;

	}
	
	public static void DespawnPlayerShop(OID shopOid) {
		DespawnPlayerShopMessage msg = new DespawnPlayerShopMessage(shopOid);
		msg.setShopOid(shopOid);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("INSTANCE: CreateShopSpawn hit 2");
	}

	public static class DespawnPlayerShopMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;

		public DespawnPlayerShopMessage() {
			super();
		}

		public DespawnPlayerShopMessage(OID shopOid) {
			setMsgType(MSG_TYPE_DESPAWN_PLAYER_SHOP);
			setShopOid(shopOid);
			Log.debug("INSTANCE: DespawnPlayerShop hit 1");
		}

		public void setShopOid(OID shopOid) {
			this.shopOid = shopOid;
		}

		public OID getShopOid() {
			return this.shopOid;
		}

		OID shopOid;

	}
	
	public static float sendGetActorSpeed(OID oid, OID InstanceOID) {
		PropertyMessage msg = new PropertyMessage(MSG_TYPE_MOB_GET_ACTOR_SPEED, oid);
		msg.setProperty("instanceOID", InstanceOID);
		Log.debug("AgisMobClient.sendGetActorSpeed " +oid +" InstanceOID "+InstanceOID);
		return (float) Engine.getAgent().sendRPCReturnObject(msg);
	}
	
	
	public static final MessageType MSG_TYPE_DESPAWN_PLAYER_SHOP = MessageType.intern("ao.DESPAWN_PLAYER_SHOP");
	public static MessageType MSG_TYPE_CREATE_SHOP_SPAWN = MessageType.intern("ao.CREATE_SHOP_SPAWN");
	public static MessageType MSG_TYPE_DELETE_SHOP_SPAWN = MessageType.intern("ao.DELETE_SHOP_SPAWN");

	public static final MessageType MSG_TYPE_GET_PLAYERS_ONLINE = MessageType.intern("ao.GET_PLAYERS_ONLINE");
	public static final MessageType MSG_TYPE_PET_LEVELUP = MessageType.intern("ao.PET_LEVELUP");
	public static final MessageType MSG_TYPE_SPAWN_INTERACTIVE_OBJECT = MessageType.intern("ao.SPAWN_INTERACTIVE_OBJECT");
	public static final MessageType MSG_TYPE_GET_INSTANCE_TEMPLATE = MessageType.intern("ao.GET_INSTANCE_TEMPLATE");
	public static final MessageType MSG_TYPE_SPAWN_INSTANCE_MOBS = MessageType.intern("ao.SPAWN_INSTANCE_MOBS");
	public static final MessageType MSG_TYPE_SPAWN_MOB = MessageType.intern("ao.SPAWN_MOB");
	public static final MessageType MSG_TYPE_DELETE_SPAWN_GENERATOR = MessageType.intern("ao.DELETE_SPAWN_GENERATOR");
	public static final MessageType MSG_TYPE_CREATE_AVATAR_CLONE = MessageType.intern("mob.CREATE_AVATAR_CLONE");
	public static final MessageType MSG_TYPE_CREATE_NYTS_CLONES = MessageType.intern("mob.CREATE_NYTS_CLONES");
	public static final MessageType MSG_TYPE_PERFORM_GESTURE = MessageType.intern("mob.PERFORM_GESTURE");
	public static final MessageType MSG_TYPE_DESPAWN_CLONES = MessageType.intern("mob.DESPAWN_CLONES");
	public static final MessageType MSG_TYPE_SPAWN_ARENA_CREATURE = MessageType.intern("mob.SPAWN_ARENA_CREATURE");
	//public static final MessageType MSG_TYPE_SPAWN_ARENA_OBJECTS = MessageType.intern("mob.SPAWN_ARENA_OBJECTS");
	public static final MessageType MSG_TYPE_SPAWN_PET = MessageType.intern("mob.SPAWN_PET");
	public static final MessageType MSG_TYPE_TAME_BEAST = MessageType.intern("mob.TAME_BEAST");
	public static final MessageType MSG_TYPE_PET_COMMAND_UPDATE = MessageType.intern("mob.PET_COMMAND_UPDATE");
	public static final MessageType MSG_TYPE_SEND_PET_COMMAND = MessageType.intern("mob.SEND_PET_COMMAND");
	public static final MessageType MSG_TYPE_UPDATE_PET_STATS = MessageType.intern("mob.UPDATE_PET_STATS");
	public static final MessageType MSG_TYPE_PET_TARGET_LOST = MessageType.intern("mob.PET_TARGET_LOST");
	public static final MessageType MSG_TYPE_TARGET_IN_REACTION_RANGE = MessageType.intern("mob.TARGET_IN_REACTION_RANGE");
	public static final MessageType MSG_TYPE_ADD_TARGET_TO_CHECK = MessageType.intern("mob.ADD_TARGET_TO_CHECK");
	public static final MessageType MSG_TYPE_REMOVE_TARGET_TO_CHECK = MessageType.intern("mob.REMOVE_TARGET_TO_CHECK");
	
	public static final MessageType MSG_TYPE_MOB_KILLED = MessageType.intern("ao.MOB_KILLED");
	public static final MessageType MSG_TYPE_OBJECT_ACTIVATED = MessageType.intern("ao.OBJECT_ACTIVATED");
	public static final MessageType MSG_TYPE_DETECT_BUILDING_GRIDS = MessageType.intern("ao.DETECT_BUILDING_GRIDS");
	public static final MessageType MSG_TYPE_GET_BUILDING_GRID_DATA = MessageType.intern("ao.GET_BUILDING_GRID_DATA");
	public static final MessageType MSG_TYPE_PURCHASE_BUILDING_GRID = MessageType.intern("ao.PURCHASE_BUILDING_GRID");
	public static final MessageType MSG_TYPE_CREATE_BUILDING = MessageType.intern("ao.CREATE_BUILDING");
	public static final MessageType MSG_TYPE_USE_TRAP_DOOR = MessageType.intern("ao.USE_TRAP_DOOR");
	public static final MessageType MSG_TYPE_HARVEST_RESOURCE_GRID = MessageType.intern("ao.HARVEST_RESOURCE_GRID");
	public static final MessageType MSG_TYPE_SET_BLOCK = MessageType.intern("ao.SET_BLOCK");
	public static final MessageType MSG_TYPE_GET_INTERACTION_OPTIONS = MessageType.intern("ao.GET_INTERACTION_OPTIONS");
	public static final MessageType MSG_TYPE_START_INTERACTION = MessageType.intern("ao.START_INTERACTION");
	public static final MessageType MSG_TYPE_DIALOGUE_OPTION_CHOSEN = MessageType.intern("ao.DIALOGUE_OPTION_CHOSEN");
	public static final MessageType MSG_TYPE_START_DIALOGUE = MessageType.intern("ao.START_DIALOGUE");
	public static final MessageType MSG_TYPE_INTERACT_WITH_OBJECT = MessageType.intern("ao.INTERACT_WITH_OBJECT");
	public static final MessageType MSG_TYPE_INVALID_PATH = MessageType.intern("ao.INVALID_PATH");
	
	public static final MessageType MSG_TYPE_ADD_NM_OBJECT = MessageType.intern("ao.ADD_NM_OBJECT");
	public static final MessageType MSG_TYPE_DEBUG_NM = MessageType.intern("ao.DEBUG_NM");
	public static final MessageType MSG_TYPE_ADD_DYNAMIC_NM_OBJECT = MessageType.intern("ao.ADD_DYNAMIC_NM_OBJECT");
	public static final MessageType MSG_TYPE_DEBUG_MOB = MessageType.intern("ao.DEBUG_MOB");


	// World building message types
	public static final MessageType MSG_TYPE_GET_TEMPLATES = MessageType.intern("mob.GET_TEMPLATES");
	public static final MessageType MSG_TYPE_CREATE_MOB_SPAWN = MessageType.intern("mob.CREATE_MOB_SPAWN");
	public static final MessageType MSG_TYPE_CREATE_QUEST = MessageType.intern("ao.CREATE_QUEST");
	public static final MessageType MSG_TYPE_EDIT_QUEST = MessageType.intern("ao.EDIT_QUEST");
	public static final MessageType MSG_TYPE_GET_ISLANDS_DATA = MessageType.intern("mob.GET_ISLANDS_DATA");
	public static final MessageType MSG_TYPE_VERIFY_ISLAND_ACCESS = MessageType.intern("ao.VERIFY_ISLAND_ACCESS");
	public static final MessageType MSG_TYPE_ENTER_WORLD = MessageType.intern("ao.ENTER_WORLD");
	public static final MessageType MSG_TYPE_CATEGORY_UPDATED = MessageType.intern("ao.CATEGORY_UPDATED");
	public static final MessageType MSG_TYPE_REQUEST_DEVELOPER_ACCESS = MessageType.intern("ao.REQUEST_DEVELOPER_ACCESS");
	public static final MessageType MSG_TYPE_CREATE_ISLAND = MessageType.intern("ao.CREATE_ISLAND");
	public static final MessageType MSG_TYPE_VIEW_MARKERS = MessageType.intern("ao.VIEW_MARKERS");
	public static final MessageType MSG_TYPE_REQUEST_SPAWN_DATA = MessageType.intern("ao.REQUEST_SPAWN_DATA");
	public static final MessageType MSG_TYPE_EDIT_SPAWN_MARKER = MessageType.intern("ao.EDIT_SPAWN_MARKER");
	public static final MessageType MSG_TYPE_DELETE_SPAWN_MARKER = MessageType.intern("ao.DELETE_SPAWN_MARKER");
	public static final MessageType MSG_TYPE_CREATE_MOB = MessageType.intern("ao.CREATE_MOB");
	public static final MessageType MSG_TYPE_EDIT_MOB = MessageType.intern("ao.EDIT_MOB");
	public static final MessageType MSG_TYPE_CREATE_FACTION = MessageType.intern("ao.CREATE_FACTION");
	public static final MessageType MSG_TYPE_EDIT_FACTION = MessageType.intern("ao.EDIT_FACTION");
	public static final MessageType MSG_TYPE_CREATE_LOOT_TABLE = MessageType.intern("ao.CREATE_LOOT_TABLE");
	
	
	public static final MessageType MSG_TYPE_SPAWN_DOME_MOB = MessageType.intern("ao.SPAWN_DOME_MOB");
	public static final MessageType MSG_TYPE_DOME_ENQUIRY = MessageType.intern("ao.DOME_ENQUIRY");
	public static final MessageType MSG_TYPE_DOME_ENTRY_REQUEST = MessageType.intern("ao.DOME_ENTRY_REQUEST");
	public static final MessageType MSG_TYPE_DOME_LEAVE_REQUEST = MessageType.intern("ao.DOME_LEAVE_REQUEST");
	public static final MessageType MSG_TYPE_ACTIVATE_DOME_ABILITY = MessageType.intern("ao.ACTIVATE_DOME_ABILITY");
	
	// Temporary messages, these need deleted or moved elsewhere
	public static final MessageType MSG_TYPE_PLAY_COORD_EFFECT = MessageType.intern("ao.PLAY_COORD_EFFECT");
	
	public static final MessageType MSG_TYPE_DIALOG_CHECK = MessageType.intern("ao.DIALOG_CHECK");
	public static final MessageType MSG_TYPE_MOB_GET_ACTOR_SPEED = MessageType.intern("ao.GET_ACTOR_SPEED");
	
	
	
}