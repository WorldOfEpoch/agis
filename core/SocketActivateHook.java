package atavism.agis.core;

import atavism.agis.objects.AgisItem;
import atavism.agis.plugins.AgisInventoryPlugin;
import atavism.server.engine.Engine;
import atavism.server.engine.OID;
import atavism.server.plugins.InventoryPlugin;
import atavism.server.util.Log;

public class SocketActivateHook implements ActivateHook {
	public SocketActivateHook() {
		super();
	}

	public SocketActivateHook(String type, boolean oadelete) {
		this();
		setType(type);
		this.oadelete = oadelete;
	}

	public void setType(String type) {
		if (type.length() > 1) {
			throw new RuntimeException("SocketActivateHook.type: bad type:" + type);
		}
		this.type = type;
	}

	public String getType() {
		return type;
	}

	protected String type;
	protected boolean oadelete = false;

	public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
		AgisInventoryPlugin invPlugin = (AgisInventoryPlugin) Engine.getPlugin(InventoryPlugin.INVENTORY_PLUGIN_NAME);
		if (Log.loggingDebug)
			Log.debug("SocketActivateHook: calling invPlugin, item=" + item + ", activatorOid=" + activatorOid + ", targetOid=" + targetOid);

		return oadelete;
	}

	public String toString() {
		return "SocketActivateHook";
	}

	private static final long serialVersionUID = 1L;
}
