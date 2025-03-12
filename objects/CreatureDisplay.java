package atavism.agis.objects;


public class CreatureDisplay {
    public CreatureDisplay() {
    }
    
    public CreatureDisplay(int id, String name, String species, String subspecies, String gender, String model) {
    	this.id = id;
    	this.name = name;
    	this.species = species;
    	this.subspecies = subspecies;
    	this.gender = gender;
    	this.model = model;
    }
    
    public int getID() { return id;}
    public void setID(int id) {
    	this.id = id;
    }
    
    public String getDisplayName() { return name;}
    public void setDisplayName(String name) {
    	this.name = name;
    }
    
    public String getSpecies() { return species;}
    public void setSpecies(String species) {
    	this.species = species;
    }
    
    public String getSubSpecies() { return subspecies;}
    public void setSubSpecies(String subspecies) {
    	this.subspecies = subspecies;
    }
    
    public String getGender() { return gender;}
    public void setGender(String gender) {
    	this.gender = gender;
    }
    
    public String getModel() { return model;}
    public void setModel(String model) {
    	this.model = model;
    }
    
    public String toString() {
    	return id + ", " + name + ", race: " + ", gender: " + gender;
    }

    int id;
    String name;
    String species;
    String subspecies;
    String model;
    String gender;

    private static final long serialVersionUID = 1L;
}
