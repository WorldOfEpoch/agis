package atavism.agis.arenas;

import java.io.*;
import java.util.*;

import atavism.server.util.*;

/**
 * The ArenaCategory class stores all the information needed about an arena type. 
 * @author Andrew Harrison
 *
 */
public class ArenaCategory implements Serializable {
	protected int categoryID;
	protected ArrayList<String> skins;

   /**
    * The constructor for the ArenaCategory. Fills all the data for the class as needed.
     */
    public ArenaCategory(int categoryID, ArrayList<String> skins) {
    	Log.debug("ARENA TEMPLATE: starting arenaTemplate creation");
    	this.categoryID = categoryID;
    	this.skins = skins;
	    Log.debug("ARENA TEMPLATE: finished arenaTemplate creation");
    }
    
    public int getCategoryID() { return categoryID; }
    public void setCategoryID(int categoryID) { this.categoryID = categoryID; }
    
    public ArrayList<String> getSkins() { return skins; }
    public void setSkins(ArrayList<String> skins) { this.skins = skins; }
    
    private static final long serialVersionUID = 1L;
}
 