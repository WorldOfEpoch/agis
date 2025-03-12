package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AtavismBuildingColliders  implements Serializable{

	public AtavismBuildingColliders() {

	}

	public List<AtavismCollider> colliders = new ArrayList<AtavismCollider>();

	public void setColliders(List<AtavismCollider> colliders) {
		this.colliders = colliders;
	}
	public String toString() {
		return "[ AtavismBuildingColliders: colliders:"+colliders+" ]";
	}
}