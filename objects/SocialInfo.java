package atavism.agis.objects;

import atavism.agis.plugins.SocialClient;
import atavism.server.objects.*;
import atavism.server.engine.*;

import java.util.*;


/**
 * Information related to the inventory system. Any object that wants to carry items or
 * currency requires one of these.
 */
public class SocialInfo extends Entity {
	public SocialInfo() {
		super();
		setNamespace(SocialClient.NAMESPACE);
	}

	public SocialInfo(OID objOid) {
		super(objOid);
		setNamespace(SocialClient.NAMESPACE);
	}

    public String toString() {
        return "[Entity: " + getName() + ":" + getOid() + "]";
    }

    public ObjectType getType() {
        return ObjectTypes.unknown;
    }
	
	public int getID() {
		return id;
	}
	public void setID(int id) {
		this.id = id;
	}
	private int id;
	
	
	public void addFriend(OID oid, String name) {
		friends.put(oid, name);
		Engine.getPersistenceManager().setDirty(this);
	}
	public void removeFriend(OID oid) {
		friends.remove(oid);
		Engine.getPersistenceManager().setDirty(this);
	}
	public HashMap<OID, String> getFriends() {
		return new HashMap<OID, String>(friends);
	}
	public void setFriends(HashMap<OID, String> friends) {
		this.friends = new HashMap<OID, String>(friends);
	}
	public void addIgnore(OID oid, String name) {
		ignores.put(oid, name);
		Engine.getPersistenceManager().setDirty(this);
	}
	public void removeIgnore(OID oid) {
		ignores.remove(oid);
		Engine.getPersistenceManager().setDirty(this);
	}
	public HashMap<OID, String> getIgnores() {
		return new HashMap<OID, String>(ignores);
	}
	public void setIgnores(HashMap<OID, String> ignores) {
		this.ignores = new HashMap<OID, String>(ignores);
	}

	private HashMap<OID, String> friends = new HashMap<OID, String>();
	private HashMap<OID, String> ignores = new HashMap<OID, String>();
	
	public void addChannel(String name) {
		channels.add(name);
		Engine.getPersistenceManager().setDirty(this);
	}
	public void removeChannel(String name) {
		channels.remove(name);
		Engine.getPersistenceManager().setDirty(this);
	}
	public ArrayList<String> getChannels() {
		return new ArrayList<String>(channels);
	}
	public void setChannels(ArrayList<String> channels) {
		this.channels = new ArrayList<String>(channels);
	}
	private ArrayList<String> channels = new ArrayList<String>();
	
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}
