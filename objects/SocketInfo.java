package atavism.agis.objects;

import java.io.Serializable;

import atavism.server.engine.OID;

public class SocketInfo implements Serializable {
	protected int id;
	protected String type;
	protected OID itemOid;
		
	public SocketInfo() {
	}
	public SocketInfo(int id,String type) {
	this(id,type,null);
	
	}
	public SocketInfo(int id,String type,OID itemOid) {
		this.id = id;
		this.type = type;
		this.itemOid = itemOid;
	}
	
	public void SetType(String type){
		this.type = type;
	}
	public String GetType(){
		return type;
	}
	public void SetItemOid(OID itemOid){
		this.itemOid = itemOid;
	}
	public OID GetItemOid(){
		return itemOid;
	}

	public String toString() {return "[SocketInfo: id=" + id + " type=" + type + " itemOid=" + itemOid + "]";}
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}
