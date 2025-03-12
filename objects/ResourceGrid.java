package atavism.agis.objects;

import java.io.Serializable;
import atavism.server.engine.Namespace;
import atavism.server.engine.OID;
import atavism.server.math.AOVector;
import atavism.server.math.Point;
import atavism.server.math.Quaternion;
import atavism.server.objects.DisplayContext;
import atavism.server.objects.Template;
import atavism.server.plugins.ObjectManagerClient;
import atavism.server.plugins.WorldManagerClient;
import atavism.server.util.*;

public class ResourceGrid implements Serializable {
    public ResourceGrid() {
    }
    
    public ResourceGrid(int id, Point position, int resourceCount, String resourceType, float rotation) {
    	this.id = id;
    	this.position = position;
    	this.count = resourceCount;
    	this.resourceType = resourceType;
    	this.rotation = rotation;
    }
    
    public void harvestResource() {
    	count--;
    	if (count < 1) {
    		WorldManagerClient.despawn(resourceOID);
    	}
    }

	public int getID() { return id;}
    public void setID(int id) {
    	this.id = id;
    }
    
    public String getInstance() { return instance;}
    public void setInstance(String instance) {
    	this.instance = instance;
    }
    
    public Point getPosition() { return position;}
    public void setPosition(Point position) {
    	this.position = position;
    }
    
    public int getCount() { return count;}
    public void setCount(int count) {
    	this.count = count;
    }
    
    public float getLayerHeight() { return layerHeight;}
    public void setLayerHeight(float layerHeight) {
    	this.layerHeight = layerHeight;
    }
    
    public String getResourceType() { return resourceType;}
    public void setResourceType(String resourceType) {
    	this.resourceType = resourceType;
    }
    
    /*public ArrayList<Integer> getBlueprints() { return blueprints;}
    public void setBlueprints(ArrayList<Integer> blueprints) {
    	this.blueprints = blueprints;
    }*/
    
    public float getRotation() { return rotation;}
    public void setRotation(float rotation) {
    	this.rotation = rotation;
    }
    
    public OID getOID() { return resourceOID; }
    
    public void spawnResource(OID instanceOID) {
    	if (count == 0)
    		return;
    	//TODO: Choose resource type if the type is a base type
		Template markerTemplate = new Template();
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_NAME, "Resource" + id);
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_OBJECT_TYPE, WorldManagerClient.TEMPL_OBJECT_TYPE_STRUCTURE);
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_LOC, position);
		Quaternion orientation = Quaternion.fromAngleAxisDegrees(rotation, new AOVector(0, 1, 0));
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_ORIENT, orientation);
		DisplayContext dc = new DisplayContext("RockDungeonSpawns", true);
		dc.addSubmesh(new DisplayContext.Submesh("", ""));
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
		markerTemplate.put(Namespace.WORLD_MANAGER, "resourceID", id);
		markerTemplate.put(Namespace.WORLD_MANAGER, "resourceMesh", "rockgravel1");
		markerTemplate.put(Namespace.WORLD_MANAGER, "resourceCount", count);
	
		OID objectOID = ObjectManagerClient.generateObject(ObjectManagerClient.BASE_TEMPLATE_ID,
            ObjectManagerClient.BASE_TEMPLATE, markerTemplate);
	
		if (objectOID != null) {
			WorldManagerClient.spawn(objectOID);
			resourceOID = objectOID;
			Log.debug("GRID: spawned resource: " + resourceType + " at loc: " + position + " in instance: " + instanceOID);
		}
    }

    int id;
    String instance;
    Point position;
    float layerHeight;
    String resourceType;
    int count;
    float rotation;
    OID resourceOID;

    private static final long serialVersionUID = 1L;
}
