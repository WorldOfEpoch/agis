package atavism.agis.objects;


public class ItemWeight  {
    public ItemWeight() {
    }
    
    public ItemWeight(int id, String name, String stat1, int weight1, String stat2, 
    		int weight2, String stat3, int weight3, boolean prefix) {
    	this.id = id;
    	this.name = name;
    	this.stat1 = stat1;
    	this.weight1 = weight1;
    	this.stat2 = stat2;
    	this.weight2 = weight2;
    	this.stat3 = stat3;
    	this.weight3 = weight3;
    	this.isPrefix = prefix;
    }
    
    public int getTotalStatWeight() {
    	return weight1 + weight2 + weight3;
    }
    
    public int getItemWeightID() { return id;}
    public void setItemWeightID(int id) {
    	this.id = id;
    }
    
    public String getItemWeightName() { return name;}
    public void setItemWeightName(String name) {
    	this.name = name;
    }
    
    public String getStat1() { return stat1;}
    public void setStat1(String stat1) {
    	this.stat1 = stat1;
    }
    
    public int getWeight1() { return weight1;}
    public void setWeight1(int weight1) {
    	this.weight1 = weight1;
    }
    
    public String getStat2() { return stat2;}
    public void setStat2(String stat2) {
    	this.stat2 = stat2;
    }
    
    public int getWeight2() { return weight2;}
    public void setWeight2(int weight2) {
    	this.weight2 = weight2;
    }
    
    public String getStat3() { return stat3;}
    public void setStat3(String stat3) {
    	this.stat3 = stat3;
    }
    
    public int getWeight3() { return weight3;}
    public void setWeight3(int weight3) {
    	this.weight3 = weight3;
    }
    
    public boolean getIsPrefix() { return isPrefix;}
    public void setIsPrefix(boolean isPrefix) {
    	this.isPrefix = isPrefix;
    }

    int id;
    String name;
    String stat1 = "";
    int weight1 = 0;
    String stat2 = "";
    int weight2 = 0;
    String stat3 = "";
    int weight3 = 0;
    boolean isPrefix;

    private static final long serialVersionUID = 1L;
}
