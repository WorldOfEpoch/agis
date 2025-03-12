package atavism.agis.objects;

import java.io.Serializable;
import atavism.server.math.Point;

/**
 * Defines what is required to and what is rewarded for completing an achievement. Contains a criteria
 * sub class that defines what is needed to complete the achievement.
 * @author Andrew
 *
 */
public class Graveyard implements Serializable {
    public Graveyard() {
    }
    
    public Graveyard(int id, String name, Point loc, int factionReq, int factionRepReq) {
    	this.id = id;
    	this.name = name;
    	this.loc = loc;
    	this.factionReq = factionReq;
    	this.factionRepReq = factionRepReq;
    }

	public int getID() { return id;}
    public void setID(int id) {
    	this.id = id;
    }
    
    public String getName() { return name;}
    public void setName(String name) {
    	this.name = name;
    }
    
    public Point getLoc() { return loc;}
    public void setLoc(Point loc) {
    	this.loc = loc;
    }
    
    public int getFactionReq() { return factionReq;}
    public void setFactionReq(int factionReq) {
    	this.factionReq = factionReq;
    }
    public int getFactionRepReq() { return factionRepReq;}
    public void setFactionRepReq(int factionRepReq) {
    	this.factionRepReq = factionRepReq;
    }

    int id;
    String name;
    Point loc;
    int factionReq;
    int factionRepReq;

    private static final long serialVersionUID = 1L;
}
