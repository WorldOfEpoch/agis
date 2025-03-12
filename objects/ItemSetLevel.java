package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class ItemSetLevel implements Serializable {
	protected int id;
	//protected String name;
	protected int number_of_parts;
	HashMap<String, EnchantStat> stats = new HashMap<String, EnchantStat>();	
	ArrayList<Integer> effects = new ArrayList<Integer>();
	ArrayList<Integer> abilities = new ArrayList<Integer>();
	
	public ItemSetLevel() {
	}
	
	public ItemSetLevel(int id) {
		this.id = id;
	
	}
	public int GetId() {
		return id;
	}

	public void SetNumberOfParts(int number_of_parts){
		this.number_of_parts = number_of_parts;
	}
	public int GetNumberOfParts(){
		return number_of_parts;
	}
	
	
	public void SetStats(HashMap<String, EnchantStat> stats){
		this.stats = stats;
	}
	public HashMap<String, EnchantStat> GetStats(){
		return stats;
	}
	
	public void SetEffects(ArrayList<Integer> effects) {
		this.effects = effects;
	}
	
	public ArrayList<Integer> GetEffects() {
		return effects;
	}
	
	public ArrayList<Integer> GetAbilities() {
		return abilities;
	}
	
	public void SetAbilities(ArrayList<Integer> abilities) {
		this.abilities = abilities;
	}
	
	public String toString() {
		  return  "[ItemSetLevel: id=" + GetId() + ", number_of_parts=" + GetNumberOfParts() +  "stats=" + stats.size() + "] ";
	    }
	
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
	
}
