package atavism.agis.objects;

import java.io.Serializable;


public class EnchantStat implements Serializable {
	protected String statName;
	protected int value;
	protected float valuePercentage;
		
	public EnchantStat() {
	}
	
	public EnchantStat(String statName,int value,float valuePercentage) {
		this.statName = statName;
		this.value = value;
		this.valuePercentage = valuePercentage;
		}
	
	public void SetType(String statName){
		this.statName = statName;
	}
	public String GetStatName(){
		return statName;
	}
	
	public void SetValue(int value){
		this.value = value;
	}
	public int GetValue(){
		return value;
	}
	
	public void SetValuePercentage(float valuePercentage){
		this.valuePercentage = valuePercentage;
	}
	public float GetValuePercentage(){
		return valuePercentage;
	}
	
	public String  toString() {
		return "[EnchantStat: statName="+statName+" value="+value+"; valuePercentage="+valuePercentage+"]";
	}
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}
