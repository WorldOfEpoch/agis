package atavism.agis.objects;

import java.io.Serializable;

import atavism.server.engine.OID;

public class PlayerShop implements Serializable {
	protected OID shopOid;
	protected OID ownerOid;
	String tag = "";
	String title = "";
	boolean end_player_logout = false;
	int timeout = 0;
	protected long id = -1L;
	long createTime =0L;
	 boolean player=false;
	public PlayerShop() {
	}

	public PlayerShop(long id, OID shopOid,OID ownerOid, String title ,String tag,boolean end_player_logout ,int timeout , long createTime) {
		this.id=id;
		this.shopOid = shopOid;
		this.ownerOid = ownerOid;
		this.tag = tag;
		this.title=title;
		this.end_player_logout=end_player_logout;
		this.timeout=timeout;
		this.createTime=createTime;
	}

	public Long getId() {
		return id;
	}
	public void setId(long id) {
		 this.id = id;
	}

	public void setCreateTime(long createTime) {
		this.createTime = createTime;
	}

	public long getCreateTime() {
		return createTime;
	}

	public void setShopOid(OID shopOid) {
		this.shopOid = shopOid;
	}

	public OID getShopOid() {
		return shopOid;
	}

	public void setOwnerOid(OID ownerOid) {
		this.ownerOid = ownerOid;
	}

	public OID getOwnerOid() {
		return ownerOid;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public String getTag() {
		return tag;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getTitle() {
		return title;
	}
	
	public void setEndPlayerOnLogout(boolean v) {
		end_player_logout = v;
	}
	
	public boolean getEndPlayerOnLogout() {
		return end_player_logout;
	}
	public void setPlayer(boolean v) {
		player = v;
	}
	
	public boolean getPlayer() {
		return player;
	}
	
	
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;

}
