package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;


public class PetInfo {
	public int id;
	public int level;
	public long experience;
	public long equipment;
	@Override
	public String toString() {
		return "[PetInfo: id=" + id + ", level=" + level + ", experience="+experience+" ]";
	}
}
