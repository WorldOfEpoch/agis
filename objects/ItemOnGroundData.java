package atavism.agis.objects;

import atavism.server.engine.Engine;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;

import java.io.Serializable;
import java.util.Random;

public class ItemOnGroundData implements Serializable {

	public ItemOnGroundData() {
	}

	public ItemOnGroundData(OID itemOID, int templateId, int stack, AOVector loc) {
		setItemOID(itemOID);
		setTemplateId(templateId);
		setStack(stack);
		setCreateTime(System.currentTimeMillis());
		setSpawnLoc(loc);
		setOid(Engine.getLootOIDManager().getNextOid());
	}

	public String toString() {
		return "[ItemOnGroundData: oid=" + oid + "; templateId=" + templateId + "; itemOID="+itemOID+"; stack=" + stack + "; createTime=" + createTime + "; spawnLoc=" + spawnLoc + " ]";
	}

	protected OID oid;
	protected int templateId ;
	protected OID itemOID = null;
	protected int stack;
	protected Long createTime;
	protected AOVector spawnLoc;



	public OID getOid() {
		return oid;
	}

	public void setOid(OID oid) {
		this.oid = oid;
	}

	public void setItemOID(OID itemOID) {
		this.itemOID = itemOID;
	}

	public OID getItemOID() {
		return itemOID;
	}

	public void setStack(int stack) {
		this.stack = stack;
	}

	public int getStack() {
		return stack;
	}

	public void setTemplateId(int templateId) {
		this.templateId = templateId;
	}

	public int getTemplateId() {
		return templateId;
	}
	public void setCreateTime(Long createTime) {
		this.createTime = createTime;
	}

	public Long getCreateTime() {
		return createTime;
	}

	public void setSpawnLoc(AOVector spawnLoc) {
		this.spawnLoc = spawnLoc;
	}

	public AOVector getSpawnLoc() {
		return spawnLoc;
	}

	private static final long serialVersionUID = 1L;
}
 