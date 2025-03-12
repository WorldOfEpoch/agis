package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;

public class GuildLevelSettings implements Serializable {
	protected int level = 0;
	protected int members_num = 1;
	protected HashMap<Integer,Integer> items = new HashMap<Integer, Integer>();
	protected int merchant_table = -1;
	protected int warehouse_num_slot = 0;

	public GuildLevelSettings() {
	}
	
	public void setLevel(int level){
		this.level = level;
	}
	
	public int getLevel(){
		return level;
	}

	public void setMembersNum(int num){
		this.members_num = num;
	}
	
	public int getMembersNum(){
		return members_num;
	}

	public void setMerchantTable(int id){
		this.merchant_table = id;
	}
	
	public int getMerchantTable(){
		return merchant_table;
	}

	public void setWarehouseNumSlot(int num){
		this.warehouse_num_slot = num;
	}
	
	public int getWarehouseNumSlot(){
		return warehouse_num_slot;
	}
	
	public void addItems(int itemId, int itemCount){
		items.put(itemId, itemCount);
	}
	
	public HashMap<Integer,Integer> getItems(){
		return items;
	}
	
	
	public String toString() {
		return "[GuildLevelSettings: level="+level+" members_num="+members_num+" items="+items+" ]";
	}
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}
