package atavism.agis.objects;

public class NpcDisplay {
    public NpcDisplay() {
    }
    
    public NpcDisplay(int id, String name, String prefab, String gender, String portrait) {
    	this.id = id;
    	this.name = name;
    	this.prefab = prefab;
    	this.gender = gender;
    	this.portrait = portrait;
    }
    
    public boolean matches(String prefab, String gender) {
    	if (!this.prefab.equals(prefab))
    		return false;
    	else if (!this.gender.equals(gender))
    		return false;
    	return true;
    }
    
    public int getID() { return id;}
    public void setID(int id) {
    	this.id = id;
    }
    
    public String getDisplayName() { return name;}
    public void setDisplayName(String name) {
    	this.name = name;
    }
    
    public String getPrefab() { return prefab;}
    public void setPrefab(String prefab) {
    	this.prefab = prefab;
    }
    
    public String getGender() { return gender;}
    public void setGender(String gender) {
    	this.gender = gender;
    }
    
    public String getPortrait() { return portrait;}
    public void setPortrait(String portrait) {
    	this.portrait = portrait;
    }
    
    public String toString() {
    	return id + ", " + name + ", race: " + ", gender: " + gender;
    }

    int id;
    String name;
    String prefab;
    String gender;
    String portrait;

    private static final long serialVersionUID = 1L;
}
