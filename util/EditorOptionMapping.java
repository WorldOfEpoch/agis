package atavism.agis.util;

import java.util.*;

/**
 * Contains a mapping of editor option choice ids to names. Used for situations such as 
 * checking requirements where getting the name of the requirement type is needed.
 * @author Andrew Harrison
 *
 */
public class EditorOptionMapping {
	
    public EditorOptionMapping(int id, String name) {
    	this.id = id;
    	this.name = name;
    }
    
    int id;
    String name;
    public HashMap<Integer, String> choiceMapping = new HashMap<Integer, String>();
    public String toString() {
    	return "[EditorOptionMapping id="+id+" name="+name+" choiceMap="+choiceMapping+"]";
    }
}