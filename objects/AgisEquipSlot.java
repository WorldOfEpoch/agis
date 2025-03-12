package atavism.agis.objects;

import java.util.*;
import java.io.*;

import atavism.agis.util.RequirementChecker;
import atavism.server.util.*;
import java.util.concurrent.locks.*;

public class AgisEquipSlot implements Serializable {
	public AgisEquipSlot() {
	}

	public AgisEquipSlot(String slotName) {
		this.name = slotName;
		mapLock.lock();
		try {
			slotNameMapping.put(slotName, this);
		} finally {
			mapLock.unlock();
		}
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	private String name = null;

	public void setId(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	private int id = -1;

/*	public void setTypeId(int type) {
		this.type = type;
	}*/

	public void addTypeId(int type) {
		this.types.add(type);
	}

	public ArrayList<Integer> getTypeIds() {
		return types;
	}

	public String getTypeName() {
		String s = "";
		
		for (int type : types) {
			Log.debug("Socket type "+ type+" for "+name);
			s += RequirementChecker.getNameEditorOptionChoice("Item Slot Type", type) + ";";
		}
		Log.debug("Socket types "+ s+" for "+name);
		if (s.length() > 0)
			s = s.substring(0, s.length() - 1);
		return s;
	}

	private ArrayList<Integer> types = new ArrayList<Integer>();

	/*
	public void setSocketId(int socket) {
		this.socket = socket;
	}

	public int getSocketId() {
		return socket;
	}

	public String getSocketName() {
		return RequirementChecker.getNameEditorOptionChoice("Item Slot Socket", socket);
	}

	private int socket = -1;
*/
	public int hashCode() {
		return name.hashCode();
	}

	public boolean equals(Object other) {
		if (other instanceof AgisEquipSlot) {
			AgisEquipSlot otherSlot = (AgisEquipSlot) other;
			return otherSlot.getName().equals(name);
		}
		return false;
	}

	public String toString() {
		return "[AgisEquipSlot name=" + getName()  +":"+id+ " types="+types+" ]";
	}

	public static AgisEquipSlot getSlotByName(String slotName) {
		mapLock.lock();
		try {
			return slotNameMapping.get(slotName);
		} finally {
			mapLock.unlock();
		}
	}
	public static AgisEquipSlot getSlotById(int slotId) {
		mapLock.lock();
		try {
			for (AgisEquipSlot aes : slotNameMapping.values()) {
				if (aes.getId() == slotId)
					return aes;
			}
		} finally {
			mapLock.unlock();
		}
		return null;
	}
	public static List<AgisEquipSlot> getSlotByType(String type) {
		int t = RequirementChecker.getIdEditorOptionChoice("Item Slot Type", type);
		ArrayList<AgisEquipSlot> list = new ArrayList<AgisEquipSlot>();
		mapLock.lock();
		try {
			for (AgisEquipSlot aes : slotNameMapping.values()) {
				if (aes.getTypeIds().contains(t))
					list.add(aes);
			}

		} finally {
			mapLock.unlock();
		}
		return list;
	}

	public static ArrayList<AgisEquipSlot> getSlotByType(int type) {
		ArrayList<AgisEquipSlot> list = new ArrayList<AgisEquipSlot>();
		mapLock.lock();
		try {
			for (AgisEquipSlot aes : slotNameMapping.values()) {
				if (aes.getTypeIds().contains(type))
					list.add(aes);
			}

		} finally {
			mapLock.unlock();
		}
		return list;
	}

	public static ArrayList<AgisEquipSlot> getSlotsList() {
		ArrayList<AgisEquipSlot> list = new ArrayList<AgisEquipSlot>();
		mapLock.lock();
		try {
			for (AgisEquipSlot aes : slotNameMapping.values()) {
				list.add(aes);
			}

		} finally {
			mapLock.unlock();
		}
		return list;
	}

	public static Map<String, AgisEquipSlot> getSlots() {
		mapLock.lock();
		try {
			return new HashMap<String, AgisEquipSlot>(slotNameMapping);
		} finally {
			mapLock.unlock();
		}
	}
	
	
	private static Map<String, AgisEquipSlot> slotNameMapping = new HashMap<String, AgisEquipSlot>();

	private static Lock mapLock = LockFactory.makeLock("AgisEquipSlot");

	
//	public static AgisEquipSlot PRIMARYWEAPON = new AgisEquipSlot("primaryWeapon");
/*	public static AgisEquipSlot SHIRT = new AgisEquipSlot("shirt");
	public static AgisEquipSlot CHEST = new AgisEquipSlot("chest");
	public static AgisEquipSlot LEGS = new AgisEquipSlot("legs");
	public static AgisEquipSlot HEAD = new AgisEquipSlot("head");
	public static AgisEquipSlot FEET = new AgisEquipSlot("feet");
	public static AgisEquipSlot HANDS = new AgisEquipSlot("hands");
	public static AgisEquipSlot SHOULDER = new AgisEquipSlot("shoulder");
	public static AgisEquipSlot BACK = new AgisEquipSlot("back");
	public static AgisEquipSlot CAPE = new AgisEquipSlot("cape");
	public static AgisEquipSlot WAIST = new AgisEquipSlot("waist");*/
//	public static AgisEquipSlot SECONDARYWEAPON = new AgisEquipSlot("secondaryWeapon");
/*	public static AgisEquipSlot PRIMARYRING = new AgisEquipSlot("primaryRing");
	public static AgisEquipSlot SECONDARYRING = new AgisEquipSlot("secondaryRing");*/
/*	public static AgisEquipSlot PRIMARYEARRING = new AgisEquipSlot("primaryEarring");
	public static AgisEquipSlot SECONDARYEARRING = new AgisEquipSlot("secondaryEarring");*/
//	public static AgisEquipSlot NECK = new AgisEquipSlot("neck");
//	public static AgisEquipSlot RANGEDWEAPON = new AgisEquipSlot("rangedWeapon");
//	public static AgisEquipSlot UNKNOWN = new AgisEquipSlot("unknown");
	/*public static AgisEquipSlot SLOT1 = new AgisEquipSlot("slot1");
	public static AgisEquipSlot SLOT2 = new AgisEquipSlot("slot2");
	public static AgisEquipSlot SLOT3 = new AgisEquipSlot("slot3");
	public static AgisEquipSlot SLOT4 = new AgisEquipSlot("slot4");
	public static AgisEquipSlot SLOT5 = new AgisEquipSlot("slot5");
	public static AgisEquipSlot SLOT6 = new AgisEquipSlot("slot6");
	public static AgisEquipSlot SLOT7 = new AgisEquipSlot("slot7");
	public static AgisEquipSlot SLOT8 = new AgisEquipSlot("slot8");
	public static AgisEquipSlot SLOT9 = new AgisEquipSlot("slot9");
	public static AgisEquipSlot SLOT10 = new AgisEquipSlot("slot10");
	public static AgisEquipSlot SLOT11 = new AgisEquipSlot("slot11");
	public static AgisEquipSlot SLOT12 = new AgisEquipSlot("slot12");
	public static AgisEquipSlot SLOT13 = new AgisEquipSlot("slot13");
	public static AgisEquipSlot SLOT14 = new AgisEquipSlot("slot14");
	public static AgisEquipSlot SLOT15 = new AgisEquipSlot("slot15");
	public static AgisEquipSlot SLOT16 = new AgisEquipSlot("slot16");
	public static AgisEquipSlot SLOT17 = new AgisEquipSlot("slot17");
	public static AgisEquipSlot SLOT18 = new AgisEquipSlot("slot18");
	public static AgisEquipSlot SLOT19 = new AgisEquipSlot("slot19");
	public static AgisEquipSlot SLOT20 = new AgisEquipSlot("slot20");
	public static AgisEquipSlot FASHION = new AgisEquipSlot("fashion");*/

	private static final long serialVersionUID = 1L;
}