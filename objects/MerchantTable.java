package atavism.agis.objects;

import java.util.ArrayList;

public class MerchantTable {
    public MerchantTable() {
    }
    
    public MerchantTable(int id, String name) {
    	this.id = id;
    	this.name = name;
    }
    
    public int getID() { return id;}
    public void setID(int id) {
    	this.id = id;
    }
    
    public String getName() { return name;}
    public void setName(String name) {
    	this.name = name;
    }
    
    public ArrayList<Integer> getItems() { return items;}
    public void setItems(ArrayList<Integer> items) {
    	this.items = items;
    }
    public ArrayList<Integer> getItemCounts() { return itemCounts;}
    public void setItemCounts(ArrayList<Integer> itemCounts) {
    	this.itemCounts = itemCounts;
    }
    public ArrayList<Integer> getItemRespawns() { return itemRespawns;}
    public void setItemRespawns(ArrayList<Integer> itemRespawns) {
    	this.itemRespawns = itemRespawns;
    }
    
    public String toString() {
    	return id + ", " + name;
    }

    int id;
    String name;
    ArrayList<Integer> items = new ArrayList<Integer>();
    ArrayList<Integer> itemCounts = new ArrayList<Integer>();
    ArrayList<Integer> itemRespawns = new ArrayList<Integer>();

    private static final long serialVersionUID = 1L;
}
