package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * The ClaimProfile class stores all the information needed about a limits
 * building object.
 * 
 */
public class SlotsSet implements Serializable {

	protected String name;
	protected HashMap<String, ArrayList<AgisEquipSlot>> slots = new HashMap<String, ArrayList<AgisEquipSlot>>();

	public SlotsSet() {

	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public ArrayList<AgisEquipSlot> getSlotsForRaceClass(int race_id, int class_id) {
		return slots.computeIfAbsent(race_id + "_" + class_id, __ -> new ArrayList<AgisEquipSlot>());
	}

	public HashMap<String, ArrayList<AgisEquipSlot>> getSlots() {
		return slots;
	}

	public void setSlots(HashMap<String, ArrayList<AgisEquipSlot>> slots) {
		this.slots = slots;
	}

	public String toString(){
		return "[SlotsSet:"+name+" slots="+slots+"]";
	}

	private static final long serialVersionUID = 1L;
}

