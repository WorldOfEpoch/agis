package atavism.agis.objects;

import java.io.*;

import atavism.server.engine.OID;


public class RankingData implements Serializable
{
	/**
	 * 
	 * @param subjectOid
	 * @param type - 1.Kill, 2.Exp, 3.Harvest, 4.Crafting, 5.Loot, 6.Use Ability
	 * @param val
	 */
	public RankingData(OID subjectOid,Short type,Integer val) {
		this.subjectOid=subjectOid;
		this.type=type;
		this.val=val;
	}
	
	public String  toString() {
		return "[RankingData: subjectOid="+subjectOid+"; type="+type+"; val="+val+" ]";
	}
	
	protected OID subjectOid;
	protected Integer val;
	protected Short type;
	
	 public OID getSubjectOid() { return subjectOid; }
     public void setSubjectOid(OID subjectOid) { this.subjectOid = subjectOid; }
	 public Integer getValue() { return val; }
     public void setValue(Integer val) { this.val = val; }
	 public Short getType() { return type; }
     public void setType(Short type) { this.type = type; }
    
     private static final long serialVersionUID = 1L;	
}
 