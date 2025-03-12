package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;


public class PetProfile implements Serializable {
	protected int id;
	protected String name;
	public HashMap<Integer, PetProfileLevel> levels = new HashMap<Integer, PetProfileLevel>();

	public PetProfile() {}
	public PetProfile(int id) {
		this.id = id;
	}

	public String getName() {return name;}

	public void setName(String name) {this.name = name;}

	public int getId() {return id;}
	
	public void SetLevels(HashMap<Integer, PetProfileLevel>  levels){this.levels = levels;}
	public HashMap<Integer, PetProfileLevel> GetLevels(){return levels;}

	@Override
	public String toString() {
		return "[PetProfile: id=" + id + ", name=" + name + " levels=" + levels + "]";
	}

	/*
	 * Final Static properties
	 */
	private static final long serialVersionUID = 1L;
}
