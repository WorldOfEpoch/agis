package atavism.agis.plugins;

import atavism.msgsys.MessageType;
import atavism.msgsys.SubjectMessage;
import atavism.server.util.*;
import atavism.server.engine.Engine;
import atavism.server.engine.OID;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;

public class CraftingClient {
	
	protected CraftingClient(){
		
	}
	
	
	/*public static void CraftItem(OID id, int craftType, int[] itemIds, int[] itemStacks, int recipeId) {
		CraftItemMessage msg = new CraftItemMessage(id, craftType, itemIds, itemStacks, recipeId);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CRAFTING CLIENT: Craft Item!");
	}*/
	public static void CraftItem(String item) {
		CraftItemMessage msg = new CraftItemMessage(item);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("CRAFTING CLIENT: Craft Item!");
	}
	public static class CraftItemMessage extends ExtensionMessage{
		private static final long serialVersionUID = 1L;
		public CraftItemMessage(){
			super();
		}
		public CraftItemMessage(String itemName){
			setProperty("ItemName", itemName);
		}
		/*public CraftItemMessage(OID id, int craftType, int[] itemIds, int[] itemStacks, int recipeId){
			super(id);
			
			setMsgType(MSG_TYPE_CRAFTING_CRAFT_ITEM);
			setProperty("playerId", id);
			setProperty("recipeId", recipeId);
			setProperty("craftType", craftType);
			setProperty("itemIDs", itemIds);
			setProperty("itemStacks", itemStacks);
		}*/
	}
	
	// Send a message to create a resource node from a mob (usually when they have died)
    public static void sendCreateResourceNodeFromMobMessage(OID oid, int lootTable, int skillLevelReq, int skillLevelMax,int skillExp, int skillId,String weaponReq, float harvestTime) {
    	CreateResourceNodeFromMobMessage msg = new CreateResourceNodeFromMobMessage(oid, lootTable, skillLevelReq,  skillLevelMax, skillExp,  skillId, weaponReq, harvestTime);
        Engine.getAgent().sendBroadcast(msg);
    }
	
	public static class CreateResourceNodeFromMobMessage extends SubjectMessage {
		private static final long serialVersionUID = 1L;
        public CreateResourceNodeFromMobMessage() {
            super();
        }
        
        public CreateResourceNodeFromMobMessage(OID oid, int lootTable, int skillLevelReq, int skillLevelMax,int skillExp, int skillId,String weaponReq, float harvestTime) {
        	super(MSG_TYPE_CREATE_RESOURCE_NODE_FROM_MOB, oid);
        	setLootTable(lootTable);
        	setSkillLevelReq(skillLevelReq);
        	setHarvestTime(harvestTime);
        	setSkillLevelMax(skillLevelMax);
        	setSkillExp(skillExp);
        	setSkillId(skillId);
        	setWeaponReq(weaponReq);
        }
        
        public int getLootTable() {
        	return lootTable;
        }
        public void setLootTable(int lootTable) {
        	this.lootTable = lootTable;
        }
        public int getSkillLevelReq() {
        	return skillLevelReq;
        }
        public void setSkillLevelReq(int skillLevelReq) {
        	this.skillLevelReq = skillLevelReq;
        }
        public int getSkillLevelMax() {
        	return skillLevelMax;
        }
        public void setSkillLevelMax(int skillLevelMax) {
        	this.skillLevelMax = skillLevelMax;
        }
        public int getSkillExp() {
        	return skillExp;
        }
        public void setSkillExp(int skillExp) {
        	this.skillExp = skillExp;
        }

        public int getSkillId() {
        	return skillId;
        }
        public void setSkillId(int skillId) {
        	this.skillId = skillId;
        }

        public String getWeaponReq() {
        	return weaponReq;
        }
        public void setWeaponReq(String weaponReq) {
        	this.weaponReq = weaponReq;
        }

        public float getHarvestTime() {
        	return harvestTime;
        }
        public void setHarvestTime(float harvestTime) {
        	this.harvestTime = harvestTime;
        }
        protected int lootTable;
        protected int skillLevelReq;
        protected int skillLevelMax;
        protected int skillExp;
        protected int skillId;
        protected String weaponReq;
        protected float harvestTime;
	}
	
	// Send a message to create a resource node from a mob (usually when they have died)
    public static void sendDestroyMobResourceNodeMessage(OID oid, OID instanceOid) {
    	DestroyMobResourceNodeMessage msg = new DestroyMobResourceNodeMessage(oid, instanceOid);
        Engine.getAgent().sendBroadcast(msg);
    }
	
	public static class DestroyMobResourceNodeMessage extends SubjectMessage {
		private static final long serialVersionUID = 1L;
        public DestroyMobResourceNodeMessage() {
            super();
        }
        
        public DestroyMobResourceNodeMessage(OID oid, OID instanceOid) {
        	super(MSG_TYPE_DESTROY_MOB_RESOURCE_NODE, oid);
        	setInstanceOid(instanceOid);
        }
        
        public OID getInstanceOid() {
        	return instanceOid;
        }
        public void setInstanceOid(OID instanceOid) {
        	this.instanceOid = instanceOid;
        }
        protected OID instanceOid;
	}
	
	public static final MessageType MSG_TYPE_MINIGAME_WON = MessageType.intern("crafting.MINIGAME_WON");

	public static final MessageType MSG_TYPE_HARVEST_RESOURCE = MessageType.intern("crafting.HARVEST_RESOURCE");

	public static final MessageType MSG_TYPE_GATHER_RESOURCE = MessageType.intern("crafting.GATHER_RESOURCE");

	public static final MessageType MSG_TYPE_CRAFTING_CRAFT_ITEM = MessageType.intern("crafting.CRAFT_ITEM");

	public static final MessageType MSG_TYPE_CRAFTING_GRID_UPDATED = MessageType.intern("crafting.GRID_UPDATED");

	public static final MessageType MSG_TYPE_GET_BLUEPRINTS = MessageType.intern("crafting.GET_BLUEPRINTS");

	public static final MessageType MSG_TYPE_CREATE_RESOURCE_NODE_FROM_MOB = MessageType.intern("crafting.CREATE_RESOURCE_NODE_FROM_MOB");

	public static final MessageType MSG_TYPE_DESTROY_MOB_RESOURCE_NODE = MessageType.intern("crafting.DESTROY_MOB_RESOURCE_NODE");
}
