package atavism.agis.objects;

import atavism.server.engine.OID;
import atavism.server.math.Point;

import java.io.Serializable;

public class TargetsInAreaEntity implements Serializable
{

	public TargetsInAreaEntity() {
    }

    public TargetsInAreaEntity(OID oid, float distance, Point loc) {
		this.oid=oid;
		this.distance=distance;
		this.loc=loc;
	}
	public String  toString() {
		return "[TargetsInAreaEntity: oid="+oid+"; distance="+distance+"; loc="+loc+" ]";
	}
	protected OID oid;
	protected Float distance;
	protected Point loc;
	
	 public OID getOid() { return oid; }
     public void setOid(OID oid) { this.oid = oid; }
	 public Float getDistance() { return distance; }
     public void setDistance(float distance) { this.distance = distance; }
	 public Point getLoc() { return loc; }
     public void setLoc(Point loc) { this.loc = loc; }
    
     private static final long serialVersionUID = 1L;	
}
 