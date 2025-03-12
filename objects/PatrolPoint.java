package atavism.agis.objects;

import atavism.server.math.Point;

public class PatrolPoint {
    public PatrolPoint(int id, Point loc, float lingerTime) {
    	this.id = id;
    	this.loc = loc;
    	this.lingerTime = lingerTime;
    }
    
    public int id;
    public String name;
    public Point loc;
    public float lingerTime = 0f;
    public int nextPoint = -1;
    public boolean startingPoint = false;
    public boolean travelReverse = false;
}