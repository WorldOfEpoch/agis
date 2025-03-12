package atavism.agis.objects;

import java.io.*;

import atavism.server.engine.OID;


public class CollectionData implements Serializable
{
	/**
	 * 
	 * @param subjectOid
	 * @param type - 1.Kill, 2.Exp, 3.Harvest, 4.Crafting, 5.Loot, 6.Use Ability
	 * @param val
	 * @param objects
	 */
	public CollectionData(OID subjectOid,Short type,Integer val,String objects) {
		this.subjectOid=subjectOid;
		this.type=type;
		this.val=val;
		this.objects=objects;
	}
	
	public String  toString() {
		return "[CollectionData: id="+id+" subjectOid="+subjectOid+"; type="+type+"; val="+val+" dirty="+dirty+" achievementId="+achievementId+" rankingId="+rankingId+"]";
	}
	protected int id=-1;
	protected OID subjectOid;
	protected Integer val=0;
	protected Short type=-1;
	protected String objects="";
	protected boolean dirty=false;
	protected int achievementId = -1;
	protected int rankingId = -1;
	protected boolean acquired = false;
	
    public boolean getAcquired() { return acquired; }
    public void setAcquired(boolean acquired) { this.acquired = acquired; }

	public Integer getId() { return id; }
     public void setId(Integer id) { this.id = id; }
	
	 public OID getSubjectOid() { return subjectOid; }
     public void setSubjectOid(OID subjectOid) { this.subjectOid = subjectOid; }
	
     public Integer getValue() { return val; }
     public void setValue(Integer val) { this.val = val; }
	
     public Short getType() { return type; }
     public void setType(Short type) { this.type = type; }
	
     public boolean isDirty() { return dirty; }
     public void setDirty(boolean dirty) { this.dirty = dirty; }
  
     public String getObjects() { return objects; }
     public void setObjects(String objects) { this.objects = objects; }
	
     public Integer getAchievementId() { return achievementId; }
     public void setAchievementId(Integer achievementId) { this.achievementId = achievementId; }
    
     public Integer getRankingId() { return rankingId; }
     public void setRankingId(Integer rankingId) { this.rankingId = rankingId; }
     
     private static final long serialVersionUID = 1L;	
}
 