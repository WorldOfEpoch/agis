package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;

public class ItemSetProfile implements Serializable {
	protected int id=-1;
	protected String name="";
	protected ArrayList<Integer> items = new ArrayList<Integer>() ;
	protected ArrayList<ItemSetLevel> levels = new ArrayList<ItemSetLevel>() ;
	protected Long date = 0L;
	public ItemSetProfile() {
	}
	public ItemSetProfile(int id,String name) {
		this.id = id;
		this.name = name;
	}
	
	public int GetId() {
		return id;
	}
	
	public void SetName(String  name){
		this.name = name;
	}
	public String GetName(){
		return name;
	}
	public void SetItems(ArrayList<Integer>  items){
		this.items = items;
	}
	public ArrayList<Integer> GetItems(){
		return items;
	}
	
	public void SetLevels(ArrayList<ItemSetLevel>  levels){
		this.levels = levels;
	}
	public ArrayList<ItemSetLevel> GetLevels(){
		return levels;
	}
	public void SetDate(Long  date){
		this.date = date;
	}
	public Long GetDate(){
		return date;
	}
	
	

	  public String toString() {
		  return  "[ItemSetProfile: name=" + GetName() + ", id=" + GetId() + ", items=" + GetItems() +  ", levels=" + GetLevels() +  "] ";
	    }
	/*
	 * Final Static properties
	 */

	
	private static final long serialVersionUID = 1L;
}
