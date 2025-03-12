package atavism.agis.objects;

import atavism.server.objects.*;

public class AgisPermissionFactory implements PermissionFactory {
    /**
     * from PermissionFactory interface - pass in the object this permission
     * is for.
     */
    public PermissionCallback createPermission(AOObject obj) {
	return new AgisPermissionCallback(obj);
    }
}
