package atavism.agis.objects;

import java.io.Serializable;

public class QualityInfo implements Serializable {
	protected int id=-1;
	protected String name="";
	protected float cost =1f;
	protected float chance=1f;
	public QualityInfo() {
	}
	public QualityInfo(int id,String name) {
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
	public void SetCost(float  cost){
		this.cost = cost;
	}
	public float GetCost(){
		return cost;
	}
	public void SetChance(float  chance){
		this.chance = chance;
	}
	public float GetChance(){
		return chance;
	}
	

	  public String toString() {
		  return  "[QualityInfo: name=" + GetName() + ", id=" + GetId() + ", cost=" + GetCost() +  ", chance=" + GetChance() + "] ";
	    }
	/*
	 * Final Static properties
	 */

	
	private static final long serialVersionUID = 1L;
}
