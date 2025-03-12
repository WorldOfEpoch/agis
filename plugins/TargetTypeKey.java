package atavism.agis.plugins;

import java.util.Objects;

import atavism.server.engine.OID;

public class TargetTypeKey {

    private final OID subjectOid;
    private final OID targetOid;

    public TargetTypeKey(OID subjectOid, OID targetOid) {
        this.subjectOid = subjectOid;
        this.targetOid = targetOid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectOid, targetOid);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TargetTypeKey other = (TargetTypeKey) obj;
        return Objects.equals(subjectOid, other.subjectOid) && Objects.equals(targetOid, other.targetOid);
    }

}
