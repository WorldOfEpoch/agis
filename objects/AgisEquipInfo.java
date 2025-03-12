package atavism.agis.objects;

import atavism.server.util.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.io.*;

/**
 * stores information about how to handle equipping. says what equipslots are
 * valid. says what socket the equipslot maps to. agismobs all refer to an
 * object like this
 */
public class AgisEquipInfo implements Cloneable, Serializable {
	public AgisEquipInfo() {
		setupTransient();
	}

	public AgisEquipInfo(String name) {
		setupTransient();
		setName(name);
	}

	public String toString() {
		localLock.lock();
		try {
			String s = "[AgisEquipInfo: name=" + name+" all="+all;
			for (AgisEquipSlot slot : equipSlots) {
				s += ", slot=" + slot;
			}
			return s + " equipInfoMap="+equipInfoMap.size()+"]";
		} finally {
			localLock.unlock();
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		staticMapLock.lock();
		try {
			this.name = name;
			equipInfoMap.put(name, this);
		} finally {
			staticMapLock.unlock();
		}
	}

	private String name;

	/*public void setTypeId(int type) {
		this.type = type;
	}

	public int getTypeId() {
		return type;
	}

	public String getTypeName() {
		return RequirementChecker.getNameEditorOptionChoice("Item Slot Type", type);
	}

	private int type = -1;*/

	public void setAll(boolean all) {
		this.all = all;
	}

	public boolean getAll() {
		return all;
	}
	
	private boolean all = false;

	public int slotsCount() {
		localLock.lock();
		try {
			return equipSlots.size();
		} finally {
			localLock.unlock();
		}
	}
	
	public void addEquipSlot(AgisEquipSlot slot) {
		localLock.lock();
		try {
			equipSlots.add(slot);
		} finally {
			localLock.unlock();
		}
	}

	public List<AgisEquipSlot> getEquippableSlots() {
		localLock.lock();
		try {
			return new ArrayList<AgisEquipSlot>(equipSlots);
		} finally {
			localLock.unlock();
		}
	}

	public void setEquippableSlots(List<AgisEquipSlot> slots) {
		localLock.lock();
		try {
			equipSlots = new ArrayList<AgisEquipSlot>(slots);
		} finally {
			localLock.unlock();
		}
	}

	List<AgisEquipSlot> equipSlots = new ArrayList<AgisEquipSlot>();

	public static AgisEquipInfo getEquipInfo(String name) {
		staticMapLock.lock();
		try {
			return equipInfoMap.get(name);
		} finally {
			staticMapLock.unlock();
		}
	}
	public static Map<String, AgisEquipInfo> getEquipInfoList() {
		staticMapLock.lock();
		try {
			return new HashMap<String, AgisEquipInfo>(equipInfoMap);
		} finally {
			staticMapLock.unlock();
		}
	}
	private static Map<String, AgisEquipInfo> equipInfoMap = new HashMap<String, AgisEquipInfo>();

	private static Lock staticMapLock = LockFactory.makeLock("StaticAgisEquipInfo");
	transient private Lock localLock = null;

	void setupTransient() {
		localLock = LockFactory.makeLock("AgisEquipInfo");
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		setupTransient();
	}
	
	

	
	

	// define the standard mob equippable slots
	public static AgisEquipInfo DefaultEquipInfo = new AgisEquipInfo("AgisDefaultEquipInfo");
	

	private static final long serialVersionUID = 1L;
}