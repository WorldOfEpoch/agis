package atavism.agis.core;

import atavism.server.engine.OID;
import atavism.server.util.Log;
import atavism.agis.objects.*;
import atavism.agis.plugins.*;

/**
 * an activate hook for items that trigger Start Creation of the Player Shop
 * when the item is activated
 */
public class PlayerShopActivateHook implements ActivateHook {
	public PlayerShopActivateHook() {
		super();
	}

	public PlayerShopActivateHook(String model, String tag, int numShop, int slots, boolean destroyOnLogOut, boolean oadelete, int mobTemplate, int shopTimeOut) {
		super();

		this.model = model;
		this.tag = tag;
		this.slots = slots;
		this.numShop = numShop;
		this.destroyOnLogOut = destroyOnLogOut;
		this.oadelete = oadelete;
		this.mobTemplate = mobTemplate;
		this.shopTimeOut =shopTimeOut;
		Log.debug(this + " Create");
	}

	public boolean activate(OID activatorOid, AgisItem item, OID targetOid) {
		Log.debug("PlayerShopActivateHook: activate activatorOid=" + activatorOid + " item=" + item + " targetOid=" + targetOid);
		AgisInventoryClient.StartPlayerShop(activatorOid, model, tag, numShop, slots, destroyOnLogOut, mobTemplate,shopTimeOut);
		return oadelete;
	}

	public String toString() {
		return "PlayerShopActivateHook: model=" + model + " tag=" + tag + " numShop=" + numShop + " slots=" + slots + " destroyOnLogOut=" + destroyOnLogOut;
	}

	protected int shopTimeOut=0;
	protected String model = "";
	protected int mobTemplate = -1;
	protected String tag = "";
	protected int slots = 1;
	protected int numShop = 1;
	protected boolean destroyOnLogOut = false;
	protected boolean oadelete = false;

	private static final long serialVersionUID = 1L;

}
