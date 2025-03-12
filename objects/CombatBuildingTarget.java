package atavism.agis.objects;

import java.io.Serializable;

public class CombatBuildingTarget implements Serializable {
	protected int claimId = -1;
	protected int objectId = -1;
	protected float distance = -1;

	public CombatBuildingTarget() {
	}

	public CombatBuildingTarget(int claimId, int objectId, float distance) {
		this.claimId = claimId;
		this.objectId = objectId;
		this.distance = distance;
	}
	public String toString() {
		return "[CombatBuildingTarget: Claim: "+claimId+" Object: "+objectId+" distance: "+distance+"]";
	}
	
	public void setClaimId(int claimId) {
		this.claimId = claimId;
	}

	public int getClaimId() {
		return claimId;
	}

	public void setObjectId(int objectId) {
		this.objectId = objectId;
	}

	public int getObjectId() {
		return objectId;
	}

	public void setDistance(float distance) {

		this.distance = distance;
	}

	public float getDistance() {
		return distance;
	}

	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;

}
