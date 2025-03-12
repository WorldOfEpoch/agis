package atavism.agis.objects;

import java.io.*;
import atavism.server.engine.OID;


public class AchievementData implements Serializable
{

	public AchievementData() {
	}
	/**
	 * @param id
	 * @param subjectOid
	 * @param type - 1.Kill, 2.Exp, 3.Harvest, 4.Crafting, 5.Loot, 6.Use Ability
	 * @param val
	 */
	public AchievementData(int id, OID subjectOid,Short type,Integer val) {
		this.subjectOid=subjectOid;
		this.type=type;
		this.val=val;
		dirty=true;
	}
	/**
	 *
	 * @param subjectOid
	 * @param type - 1.Kill, 2.Exp, 3.Harvest, 4.Crafting, 5.Loot, 6.Use Ability
	 * @param val
	 */
	public AchievementData(OID subjectOid,Short type,Integer val) {
		this.subjectOid=subjectOid;
		this.type=type;
		this.val=val;
		dirty=true;
	}
	
	public String  toString() {
		return "[AchievementData: subjectOid="+subjectOid+"; type="+type+"; val="+val+" ]";
	}
	protected int id = -1;
	protected OID subjectOid;
	protected String da;
	protected Integer val;
	protected Short type;
	protected boolean dirty;
	
	 public OID getSubjectOid() { return subjectOid; }
     public void setSubjectOid(OID subjectOid) { this.subjectOid = subjectOid; }
	 public Integer getValue() { return val; }
     public void setValue(Integer val) { this.val = val; }
     public Short getType() { return type; }
     public void setType(Short type) { this.type = type; }
     public boolean isDirty() { return dirty; }
     public void setDirty(boolean dirty) { this.dirty = dirty; }
    
     private static final long serialVersionUID = 1L;	
}
 