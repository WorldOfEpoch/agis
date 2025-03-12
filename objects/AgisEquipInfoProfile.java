package atavism.agis.objects;

import atavism.server.util.LockFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

/**
 * profile that stores information about equip slots that object with profile can equip
 */
public class AgisEquipInfoProfile implements Cloneable, Serializable {
	public AgisEquipInfoProfile() {
		setupTransient();
	}

	public AgisEquipInfoProfile(String name) {
		setupTransient();
		setName(name);
	}

	public String toString() {
		localLock.lock();
		try {
			String s = "[AgisEquipInfoProfile: name=" + name;
			for (AgisEquipSlot slot : equipSlots) {
				s += ", slot=" + slot;
			}
			return s +"]";
		} finally {
			localLock.unlock();
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
//		staticMapLock.lock();
		try {
			this.name = name;
		} finally {
//			staticMapLock.unlock();
		}
	}

	private String name;

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

//	public static AgisEquipInfoProfile getEquipInfo(String name) {
//		staticMapLock.lock();
//		try {
//			return equipInfoMap.get(name);
//		} finally {
//			staticMapLock.unlock();
//		}
//	}
//	public static Map<String, AgisEquipInfoProfile> getEquipInfoList() {
//		staticMapLock.lock();
//		try {
//			return new HashMap<String, AgisEquipInfoProfile>(equipInfoMap);
//		} finally {
//			staticMapLock.unlock();
//		}
//	}
//	private static Map<String, AgisEquipInfoProfile> equipInfoMap = new HashMap<String, AgisEquipInfoProfile>();

//	private static Lock staticMapLock = LockFactory.makeLock("StaticAgisEquipInfoProfile");
	transient private Lock localLock = null;

	void setupTransient() {
		localLock = LockFactory.makeLock("AgisEquipInfoProfile");
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		setupTransient();
	}
	
	

	
	

	// define the standard mob equippable slots
	public static AgisEquipInfoProfile DefaultEquipInfo = new AgisEquipInfoProfile("AgisDefaultEquipInfo");
	

	private static final long serialVersionUID = 1L;
}