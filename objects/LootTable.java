package atavism.agis.objects;

import atavism.server.util.*;
import java.util.Random;
import java.util.ArrayList;

public class LootTable {
    public LootTable() {
    }
    
    public LootTable(int id, String name, ArrayList<Integer> items, ArrayList<Integer> itemCounts, ArrayList<Float> itemChances) {
    	this.id = id;
    	this.name = name;
    	this.items = items;
    	this.itemCounts = itemCounts;
    	this.itemChances = itemChances;
    }
    
    public int getID() { return id;}
    public void setID(int id) {
    	this.id = id;
    }
    
    public String getName() { return name;}
    public void setName(String name) {
    	this.name = name;
    }
    
      
    public ArrayList<Integer> getItems() { return items;}
    public void setItems(ArrayList<Integer> items) {
    	this.items = items;
    }
    
    public ArrayList<Integer> getItemCounts() { return itemCounts;}
    public void setItemCounts(ArrayList<Integer> itemCounts) {
    	this.itemCounts = itemCounts;
    }
  
    public ArrayList<Integer> getItemMaxCounts() { return itemMaxCounts;}
    public void setItemMaxCounts(ArrayList<Integer> itemMaxCounts) {
    	this.itemMaxCounts = itemMaxCounts;
    }
  
    public ArrayList<Float> getItemChances() { return itemChances;}
    public void setItemChances(ArrayList<Float> itemChances) {
    	this.itemChances = itemChances;
    }
    
    public int getTotalRollChance() {
    	int totalRollChance = 0;
    	for (int i = 0; i < itemChances.size(); i++) {
    		totalRollChance += itemChances.get(i);
    	}
    	return totalRollChance;
    }
    
   
    private ArrayList<Float> createCumulativeWeights(ArrayList<Float> itemChances) {
        ArrayList<Float> cumulativeWeights = new ArrayList<>();
        float totalChance = 0;

        for (float chance : itemChances) {
            totalChance += chance;
            cumulativeWeights.add(totalChance);
        }

        return cumulativeWeights;
    }
    public int getNewRandomItemNum() {
    	Random rand = new Random();
        ArrayList<Float> cumulativeWeights = createCumulativeWeights(itemChances);

        if (cumulativeWeights.isEmpty() || cumulativeWeights.get(cumulativeWeights.size() - 1) == 0) {
            if (Log.loggingDebug) Log.debug("LOOT: Cumulative weights list is empty or total chance is zero");
            return -1;
        }

        float randomValue = rand.nextFloat() * cumulativeWeights.get(cumulativeWeights.size() - 1);
        int selectedItemIndex = binarySearch(cumulativeWeights, randomValue);

        // Check for regular spawn chance
        if (selectedItemIndex != -1) {
            float spawnRoll = rand.nextFloat() * 100;
            if (spawnRoll <= itemChances.get(selectedItemIndex)) {
                if (Log.loggingDebug) Log.debug("LOOT: Selected item index from cumulative weights: " + selectedItemIndex);
                return selectedItemIndex;
            }
        }

        if (Log.loggingDebug) Log.debug("LOOT: No item selected or did not spawn despite being selected.");
        return -1; // No item matched or did not spawn
    }

    // Binary search method to find the index
    private int binarySearch(ArrayList<Float> cumulativeWeights, float value) {
        int low = 0;
        int high = cumulativeWeights.size() - 1;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (cumulativeWeights.get(mid) < value) {
                low = mid + 1;
            } else if (mid > 0 && cumulativeWeights.get(mid - 1) > value) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -1; // Not found
    }

    
    public int getNewRandomItemNum(float vipChance) {
        Random rand = new Random();
        
		// Adjust item chances with vipChance
		ArrayList<Float> adjustedChances = new ArrayList<>();
		for (float chance : itemChances) {
			adjustedChances.add(chance + chance * vipChance / 100F);
		}

        ArrayList<Float> cumulativeWeights = createCumulativeWeights(itemChances);

        if (cumulativeWeights.isEmpty() || cumulativeWeights.get(cumulativeWeights.size() - 1) == 0) {
            if (Log.loggingDebug) Log.debug("LOOT: Cumulative weights list is empty or total chance is zero");
            return -1;
        }

        float randomValue = rand.nextFloat() * cumulativeWeights.get(cumulativeWeights.size() - 1);
        int selectedItemIndex = binarySearch(cumulativeWeights, randomValue);

        // Check for regular spawn chance
        if (selectedItemIndex != -1) {
            float spawnRoll = rand.nextFloat() * 100;
            if (spawnRoll <= adjustedChances.get(selectedItemIndex)) {
				Log.debug("LOOT: Selected item index from weighted list with VIP: " + selectedItemIndex);
                return selectedItemIndex;
            }
        }

        if (Log.loggingDebug) Log.debug("LOOT: No item selected or did not spawn despite being selected.");
        return -1; // No item matched or did not spawn
    }
    
	public int getRandomCountOfItem(int id) {
		if (items.size() > id) {
			if (itemMaxCounts.get(id) > itemCounts.get(id)) {
				Random rand = new Random();
				return itemCounts.get(id) + rand.nextInt(itemMaxCounts.get(id) - itemCounts.get(id));
			} else {
				return itemCounts.get(id);
			}
		}
		return 0;
	}
    
    
    public String toString() {
    	return "[LootTable "+id + " " + name + " items="+items+" itemChances="+itemChances+" itemCounts="+itemCounts+" itemMaxCounts="+itemMaxCounts+"]";
    }

    int id;
    String name;
   // int itemsPerLoot = 1;
    ArrayList<Integer> items = new ArrayList<Integer>();
    ArrayList<Integer> itemCounts = new ArrayList<Integer>();
    ArrayList<Integer> itemMaxCounts = new ArrayList<Integer>();
     ArrayList<Float> itemChances = new ArrayList<Float>();

    private static final long serialVersionUID = 1L;
}
