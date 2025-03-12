package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;


public class EnchantProfile implements Serializable {
	protected int id;
	protected HashMap<Integer, EnchantProfileLevel> levels = new HashMap<Integer, EnchantProfileLevel>();
		
	public EnchantProfile() {
	}
	public EnchantProfile(int id) {
		this.id = id;
	}
	
	public int getId() {
		return id;
	}
	
	public void SetLevels(HashMap<Integer, EnchantProfileLevel>  levels){
		this.levels = levels;
	}
	public HashMap<Integer, EnchantProfileLevel> GetLevels(){
		return levels;
	}
	
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}
