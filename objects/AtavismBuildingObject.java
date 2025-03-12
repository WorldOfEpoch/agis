package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import atavism.server.math.AOVector;
import atavism.server.math.Quaternion;

public class AtavismBuildingObject implements Serializable {

	public AtavismBuildingObject() {

	}

	int id = -1;

	public void setId(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	public List<AtavismCollider> colliders = new ArrayList<AtavismCollider>();

	public void setColliders(List<AtavismCollider> colliders) {
		this.colliders = colliders;
	}

	public List<AtavismCollider> getColliders() {
		return colliders;
	}

	public List<Long> collidersId = new ArrayList<Long>();

	public void setPosition(AOVector position) {
		this.position = position;
	}

	public AOVector getPosition() {
		return position;
	}

	AOVector position;

	public void setOrientation(Quaternion orient) {
		this.orient = orient;
	}

	
	public Quaternion getOrientation() {
		return this.orient;
	}

	Quaternion orient;
	
	public String toString() {
		return "[ AtavismBuildingObject: id=" + id + "; colliders:" + colliders + "; pos=" + position + "]";
	}
}