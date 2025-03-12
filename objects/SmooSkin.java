package atavism.agis.objects;

import java.io.Serializable;

public class SmooSkin implements Serializable {
    public SmooSkin() {
    }
    
    public SmooSkin(String name, String mesh, int currency, int cost, String requirementType, int requirement) {
    	this.name = name;
    	this.mesh = mesh;
    	this.currency = currency;
    	this.cost = cost;
    	this.requirementType = requirementType;
    	this.requirement = requirement;
    }
    
    public String getName() { return name;}
    public void setName(String name) {
    	this.name = name;
    }
    
    public String getMesh() { return mesh;}
    public void setMesh(String mesh) {
    	this.mesh = mesh;
    }
    
    public int getCurrency() { return currency;}
    public void setCurrency(int currency) {
    	this.currency = currency;
    }
    
    public int getCost() { return cost;}
    public void setCost(int cost) {
    	this.cost = cost;
    }
    
    public String getRequirementType() { return requirementType;}
    public void setRequirementType(String requirementType) {
    	this.requirementType = requirementType;
    }
    
    public int getRequirement() { return requirement;}
    public void setRequirement(int requirement) {
    	this.requirement = requirement;
    }

    String name;
    String mesh;
    int currency;
    int cost;
    String requirementType;
    int requirement;

    private static final long serialVersionUID = 1L;
}
