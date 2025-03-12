package atavism.agis.objects;

import java.io.Serializable;


public class BonusSettings implements Serializable {
	protected int id =-1;
	protected String settingName;
	protected String settingCode;
	protected int value=0;
	protected float valuePercentage=0f;
	protected String obj="";
	protected boolean dirty = false;
	
	public BonusSettings() {
	}
	
	public BonusSettings( String settingCode) {
		 this.settingCode = settingCode;
	}
	
	public BonusSettings(String settingName, String settingCode, int value, float valuePercentage, int id) {
		this.settingName = settingName;
		this.settingCode = settingCode;
		this.value = value;
		this.valuePercentage = valuePercentage;
		this.id = id;
		}
	
	public BonusSettings(String settingName, String settingCode, int value, float valuePercentage, String obj, int id) {
		this.settingName = settingName;
		this.settingCode = settingCode;
		this.value = value;
		this.valuePercentage = valuePercentage;
		this.obj = obj;
		this.id = id;
			}
	
	public BonusSettings Clone() {
		return new BonusSettings(settingName, settingCode, value, valuePercentage, -1);

	}
	
	public boolean equals(BonusSettings b) {
		if(this.obj.equals(b.getObj()) && this.getSettingCode().equals(b.getSettingCode())) {
			return true;
		}
		return false;
	}
	
	public void setSettingName(String settingName){
		this.settingName = settingName;
	}
	
	public String getSettingName(){
		return settingName;
	}
	
	public void setSettingCode(String settingCode){
		this.settingCode = settingCode;
	}
	
	public String getSettingCode(){
		return settingCode;
	}
	
	public void setObj(String obj){
		this.obj = obj;
	}
	
	public String getObj(){
		return obj;
	}
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
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
	
	public boolean isDirty() {
		return dirty;
	}
	
	public void setDirty(boolean v) {
		dirty = v;
	}
	
	public String toString() {
		return "[BonusSettings: id="+id+" name="+settingName+" code="+settingCode+" obj="+obj+" value="+value+" valuePercentage="+valuePercentage+" dirty "+dirty+"]";
	}
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}
