package atavism.agis.objects;

import java.io.Serializable;
import java.util.HashMap;


public class StatThreshold implements Serializable {
	protected int id;
	protected HashMap<Integer, Integer> thresholds = new HashMap<Integer, Integer>();
	protected HashMap<Integer, Integer> points = new HashMap<Integer, Integer>();
		
	public StatThreshold() {
	}
	public StatThreshold(String  stat) {
		setStat(stat);
	}
	
	String stat ="";
	public void setStat(String stat) { this.stat=stat; }
	public String getStat() {return stat;}
	
	public void setThresholds(HashMap<Integer, Integer>  thresholds){
		this.thresholds = thresholds;
	}
	
	public HashMap<Integer, Integer> getThresholds(){
		return thresholds;
	}
	public void setPoints(HashMap<Integer, Integer>  points){
		this.points = points;
	}
	
	public HashMap<Integer, Integer> getPoints(){
		return points;
	}
	
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}
