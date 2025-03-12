package atavism.agis.objects;

import java.io.Serializable;

public class MobLootTable implements Serializable {
	public MobLootTable() {
	}

	public int getID() {
		return id;
	}

	public void setID(int id) {
		this.id = id;
	}

	public int getItemsPerLoot() {
		return itemsPerLoot;
	}

	public void setItemsPerLoot(int itemsPerLoot) {
		this.itemsPerLoot = itemsPerLoot;
	}

	public Float getChances() {
		return chance;
	}

	public void setChances(Float chance) {
		this.chance = chance;
	}

	public String toString() {
		return "[MobLootTable: id="+id + ", chance=" + chance+" itemsPerLoot="+itemsPerLoot+"]";
	}

	int id;
	//String name;
	int itemsPerLoot = 1;
	float chance = 0;

	private static final long serialVersionUID = 1L;
}
