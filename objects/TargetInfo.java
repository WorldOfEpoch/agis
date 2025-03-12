package atavism.agis.objects;

import java.io.*;
import atavism.server.engine.OID;


/**
 * Stores information about a target for a player/mob.
 * @author Andrew Harrison
 *
 */
public class TargetInfo implements Serializable {

	protected OID oid;
	protected String species;
	
	public TargetInfo() {
		
	}
	
	public TargetInfo(OID oid, String species) {
		this.oid = oid;
		this.species = species;
	}
	
	public OID getOid() {
		return oid;
	}
	public void setOid(OID oid) {
		this.oid = oid;
	}
	
	public String getSpecies() {
		return species;
	}
	public void setSpecies(String species) {
		this.species = species;
	}
	
	private static final long serialVersionUID = 1L;

}