package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import atavism.server.math.AOVector;

public class AtavismCollider  implements Serializable{

	public AtavismCollider() {

	}

	public void setType(String type) {
		this.type = type;
	}

	public String type;

	public void setPosition(AOVector position) {
		this.position = position;
	}

	public AOVector position;

	public void setHalfEdges(List<AOVector> halfEdges) {
		this.halfEdges = halfEdges;
	}

	public List<AOVector> halfEdges = new ArrayList<AOVector>();

	public void setRadius(float radius) {
		this.radius = radius;
	}

	public float radius;

	public void setTriangles(List<Integer> triangles) {
		this.triangles = triangles;
	}

	public List<Integer> triangles = new ArrayList<Integer>();

	public void setbounds(List<Float> bounds) {
		this.bounds = bounds;
	}

	public List<Float> bounds = new ArrayList<Float>();
	public String toString() {
		return "[ AtavismCollider: type:"+type+"; position:"+position+"; halfEdges:"+halfEdges+" radius="+radius+" ]";
	}
}