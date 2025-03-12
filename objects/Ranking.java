package atavism.agis.objects;

import java.io.*;
import atavism.server.engine.OID;


public class Ranking implements Serializable
{
	/**
	 * 
	 * @param subjectOid
	 * @param type - 1.Kill, 2.Exp, 3.Harvest, 4.Crafting, 5.Loot, 6.Use Ability
	 * @param pos position on list
	 * @param val
	 */
	public Ranking(OID subjectOid, Integer type,Integer pos,Integer val) {
		this.subjectOid=subjectOid;
		this.type=type;
		this.val=val;
		this.pos=pos;
	}
	
	public String  toString() {
		return "[Ranking: subjectOid="+subjectOid+"; pos="+pos+"; type="+type+"; val="+val+" ]";
	}
	
	protected OID subjectOid;
	protected Integer val;
	protected Integer pos;
	protected Integer type;
	
	 public OID getSubjectOid() { return subjectOid; }
     public void setSubjectOid(OID subjectOid) { this.subjectOid = subjectOid; }
     
     public Integer getPosition() { return pos; }
     public void setPosition(Integer pos) { this.pos = pos; }
     
     public Integer getValue() { return val; }
     public void setValue(Integer val) { this.val = val; }
     
	 public Integer getType() { return type; }
     public void setType(Integer type) { this.type = type; }
     
    
     private static final long serialVersionUID = 1L;	
}
 