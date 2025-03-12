package atavism.agis.objects;

import java.io.Serializable;


/**
 * The purpose of this class is to ensure only 1 object is in a square of the arena. When new objects are
 * being placed the grid block can be checked to ensure it is empty, and if not the new object cannot be placed.
 * @author Andrew
 *
 */
public class ArenaGridBlock implements Serializable {
    public ArenaGridBlock() {
    }
    
    public ArenaGridBlock(int x, int y, int z, Object object) {
    	this.x = x;
    	this.y = y;
    	this.z = z;
    	this.objectInBlock = object;
    }
    
    public boolean placeObjectInBlock(Object object) {
    	if (objectInBlock == null) {
    		objectInBlock = object;
    		return true;
    	}
    	return false;
    }

    public boolean hasObject() {
    	return objectInBlock != null;
    }

	public int getX() { return x;}
    public void setX(int x) { this.x = x; }
    
    public int getY() { return y;}
    public void setY(int y) { this.y = y; }
    
    public int getZ() { return z;}
    public void setZ(int z) { this.z = z; }
    
    public Object getObjectInBlock() { return objectInBlock; }
    public void setObjectInBlock(Object object) { this.objectInBlock = object; }

    int x;
    int y;
    int z;
    Object objectInBlock;

    private static final long serialVersionUID = 1L;
}
