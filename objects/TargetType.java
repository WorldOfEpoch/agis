package atavism.agis.objects;

import java.io.*;

import atavism.server.engine.OID;

public class TargetType implements Serializable
{
    
	public TargetType() {
    }

    public TargetType(OID subjectOid,OID targetOid,Integer targetType) {
		this.subjectOid=subjectOid;
		this.targetOid=targetOid;
		this.targetType=targetType;
	}
	public String  toString() {
		return "[TargetType: subjectOid="+subjectOid+"; targetOid="+targetOid+"; targetType="+targetType+" ]";
	}
	protected OID subjectOid;
	protected OID targetOid;
	protected Integer targetType;
	
	 public OID getSubjectOid() { return subjectOid; }
     public void setSubjectOid(OID subjectOid) { this.subjectOid = subjectOid; }
	 public OID getTargetOid() { return targetOid; }
     public void setTargetOid(OID targetOid) { this.targetOid = targetOid; }
	 public Integer getTargetType() { return targetType; }
     public void setTargetType(int targetType) { this.targetType = targetType; }
    
     private static final long serialVersionUID = 1L;	
}
 