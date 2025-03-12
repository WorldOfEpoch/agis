package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;

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

public class BuildingGrid implements Serializable {
    public BuildingGrid() {
    }
    
    public BuildingGrid(int id, Point position, int type, OID owner, int layerCount, ArrayList<String> buildings, ArrayList<Float> rotations) {
    	this.id = id;
    	this.position = position;
    	this.type = type;
    	this.owner = owner;
    	this.layerCount = layerCount;
    	this.buildings = buildings;
    	this.rotations = rotations;
    }
    
    public void updateBuilding(int layer, String building, int blueprint, float rotation) {
    	// reduce layer by 1 for 0-based list system
    	layer--;
    	if (building == null || building.isEmpty()) {
    		// Remove
    		Log.debug("GRID: removing building at layer: " + layer + " with current layers: " + buildings.size());
    		while (buildings.size() > layer) {
    			Log.debug("GRID: removing layer: " + layer);
    			buildings.remove(layer);
    			blueprints.remove(layer);
    			rotations.remove(layer);
    			Log.debug("GRID: despawning: " + buildingOIDs.get(layer));
    			WorldManagerClient.despawn(buildingOIDs.get(layer));
    			buildingOIDs.remove(layer);
    		}
    	} else {
    		// Add
    		Log.debug("GRID: adding building at layer: " + layer + " with current layers: " + buildings.size());
    		if (layer >= buildings.size()) {
    			layer = buildings.size();
    			buildings.add(layer, building);
    			blueprints.add(layer, blueprint);
    			rotations.add(layer, rotation);
    		} else {
    			buildings.set(layer, building);
    			blueprints.set(layer, blueprint);
        		rotations.set(layer, rotation);
    		}
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
    
    public int getType() { return type;}
    public void setType(int type) {
    	this.type = type;
    }
    
    public OID getOwner() { return owner;}
    public void setOwner(OID owner) {
    	this.owner = owner;
    }
    
    public int getLayerCount() { return layerCount;}
    public void setLayerCount(int layerCount) {
    	this.layerCount = layerCount;
    }
    
    public float getLayerHeight() { return layerHeight;}
    public void setLayerHeight(float layerHeight) {
    	this.layerHeight = layerHeight;
    }
    
    public ArrayList<String> getBuildings() { return buildings;}
    public void setBuildings(ArrayList<String> buildings) {
    	this.buildings = buildings;
    }
    
    public ArrayList<Integer> getBlueprints() { return blueprints;}
    public void setBlueprints(ArrayList<Integer> blueprints) {
    	this.blueprints = blueprints;
    }
    
    public ArrayList<Float> getRotations() { return rotations;}
    public void setRotations(ArrayList<Float> rotations) {
    	this.rotations = rotations;
    }
    
    public ArrayList<OID> getOIDs() { return buildingOIDs; }
    
    public void spawnBuildings(OID instanceOID) {
    	if (buildings == null)
    		return;
    	//Log.debug("GRID: spawning building on tile: " + id);
    	for (int i = 1; i <= buildings.size(); i++) {
    		spawnBuilding(i, instanceOID);
    	}
    }
    
    public void spawnBuilding(int layer, OID instanceOID) {
    	// reduce layer by 1 for 0-based list system
    	layer--;
    	if (buildingOIDs.size() > layer && buildingOIDs.get(layer) != null) {
    		WorldManagerClient.despawn(buildingOIDs.get(layer));
    	}
    	if (buildings.size() <= layer || buildings.get(layer) == null || buildings.get(layer).equals(""))
			return;
		Template markerTemplate = new Template();
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_NAME, "Building" + id);
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_OBJECT_TYPE, WorldManagerClient.TEMPL_OBJECT_TYPE_STRUCTURE);
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
		//markerTemplate.put(Namespace.COMBAT, WorldManagerClient.TEMPL_INSTANCE, instanceOID);
			Point loc = new Point(); 
		loc.add(position);
		loc.setY(loc.getY() + (layerHeight * layer));
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_LOC, loc);
		Quaternion orientation = Quaternion.fromAngleAxisDegrees(rotations.get(layer), new AOVector(0, 1, 0));
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_ORIENT, orientation);
		DisplayContext dc = new DisplayContext(buildings.get(layer), true);
		dc.addSubmesh(new DisplayContext.Submesh("", ""));
		markerTemplate.put(Namespace.WORLD_MANAGER, WorldManagerClient.TEMPL_DISPLAY_CONTEXT, dc);
		markerTemplate.put(Namespace.WORLD_MANAGER, "blueprint", blueprints.get(layer));
		markerTemplate.put(Namespace.WORLD_MANAGER, "buildingID", id);
	
		OID objectOID = ObjectManagerClient.generateObject(ObjectManagerClient.BASE_TEMPLATE_ID,
            ObjectManagerClient.BASE_TEMPLATE, markerTemplate);
	
		if (objectOID != null) {
			WorldManagerClient.spawn(objectOID);
			buildingOIDs.add(layer, objectOID);
			Log.debug("GRID: spawned building: " + buildings.get(layer) + " at loc: " + position + " in instance: " + instanceOID + " in layer: " + layer);
		}
    }
    
    /*public void alterCurrencyAmount(int delta) {
    	currentAmount += delta;
    }*/

    int id;
    String instance;
    Point position;
    int type;
    OID owner;
    int layerCount;
    float layerHeight;
    ArrayList<String> buildings = new ArrayList<String>();
    ArrayList<Float> rotations = new ArrayList<Float>();
    ArrayList<Integer> blueprints = new ArrayList<Integer>();
    ArrayList<OID> buildingOIDs = new ArrayList<OID>();

    private static final long serialVersionUID = 1L;
}
