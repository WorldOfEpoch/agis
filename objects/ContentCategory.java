package atavism.agis.objects;

import java.io.*;
import java.util.*;


/**
 * The ContentCategory class stores all the information about the content relating to a category. 
 * @author Andrew Harrison
 *
 */
public class ContentCategory implements Serializable {
	protected int id;
	protected HashMap<Integer, AgisBasicQuest> quests = new HashMap<Integer, AgisBasicQuest>();
    public HashMap<Integer, Currency> currencies = new HashMap<Integer, Currency>();

    public ContentCategory(int id) {
    	this.id = id;
    } 
    
    public int getID() { return id; }
    public void setID(int id) { this.id = id; }
    
    public HashMap<Integer, AgisBasicQuest> getQuests() { return quests; }
    public void setQuests(HashMap<Integer, AgisBasicQuest> quests) { 
    	this.quests = quests; 
    }
    
    public HashMap<Integer, Currency> getCurrencies() { return currencies; }
    public void setCurrencies(HashMap<Integer, Currency> currencies) { this.currencies = currencies; }
    
    private static final long serialVersionUID = 1L;
}
