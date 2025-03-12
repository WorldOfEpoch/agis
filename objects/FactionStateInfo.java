package atavism.agis.objects;

import atavism.server.objects.*;
import atavism.server.engine.*;


/**
 * Information related to the combat system. Any object that wants to be involved
 * in combat needs one of these.
 */
public class FactionStateInfo extends Entity {
	public FactionStateInfo() {
		super();
		setNamespace(Namespace.FACTION);
	}

	public FactionStateInfo(OID objOid) {
		super(objOid);
		setNamespace(Namespace.FACTION);
	}

    public String toString() {
        return "[Entity: " + getName() + ":" + getOid() + "]";
    }
 
    public boolean isPlayer() {
    	return getBooleanProperty("isPlayer");
    }
    public void isPlayer(boolean isPlayer) {
    	setProperty("isPlayer", isPlayer);
    }
    public boolean isPet() {
    	return getBooleanProperty("pet");
    }
    public void isPet(boolean isPet) {
    	setProperty("pet", isPet);
    }
    
    protected int aggroRadius = 17;
    public void setAggroRadius(int radius) {
    	aggroRadius = radius;
    }
    public int getAggroRadius() {
    	return aggroRadius;
    }
    
    
    public boolean isDead() {
    	Boolean isDead = (Boolean) getBooleanProperty("isDead");
    	if (isDead == null) {
    		isDead = false;
    	}
    	return isDead;
    }
    public void isDead(boolean isDead) {
    	setProperty("isDead", isDead);
    }
	
	/*
	 * Final Static properties
	 */
	public static final String FACTION_PROP = "faction";
	public static final String AGGRO_RADIUS = ":aggroRadius";
	public static final String TEMPORARY_FACTION_PROP = "temporaryFaction";

	private static final long serialVersionUID = 1L;
}
