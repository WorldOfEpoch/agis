package atavism.agis.objects;

import java.io.Serializable;

import atavism.server.engine.OID;

public class PlayerShopItem implements Serializable {
	protected OID itemOid;
	protected int currency = 0;
	protected long price = 0L;
	protected long id = -1L;
	
	protected int template_id =-1;
	protected int count =0;
	protected boolean sell=false;
	
	public PlayerShopItem() {
	}

	public PlayerShopItem(long id, OID itemOid, int currency, long price, int template_id, int count, boolean sell) {
		this.id=id;
		this.itemOid = itemOid;
		this.currency = currency;
		this.price = price;
		this.template_id = template_id;
		this.count = count;
		this.sell = sell;
	}

	public Long getId() {
		return id;
	}
	public void setCurrency(int currency) {
		this.currency = currency;
	}

	public int getCurrency() {
		return currency;
	}

	public void setItemOid(OID itemOid) {
		this.itemOid = itemOid;
	}

	public OID getItemOid() {
		return itemOid;
	}

	public void setPrices(long price) {
		this.price = price;
	}

	public long getPrice() {
		return price;
	}
	
	public void setTemplateId(int templateId) {
		this.template_id = templateId;
	}

	public int getTemplateId() {
		return template_id;
	}
	public void setCount(int count) {
		this.count = count;
	}

	public int getCount() {
		return count;
	}
	
	public void setSell(boolean sell) {
		this.sell = sell;
	}

	public boolean getSell() {
		return sell;
	}

	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;

}
