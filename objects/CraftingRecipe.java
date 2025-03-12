package atavism.agis.objects;

import java.util.ArrayList;
import java.util.LinkedList;

import atavism.server.util.Log;

/**
 * Contains all of the information needed to define a recipe for crafting an item.
 * @author Andrew Harrison
 *
 */
public class CraftingRecipe {
	protected int id;
	protected String name;
	protected String iconName;
	
	protected boolean isHiddenRecipe;
	
	protected String stationReq;
	protected boolean mustMatchLayout;
	
	protected int skillID;
	protected int requiredSkillLevel;
	protected ArrayList<Integer> resultItemIds;
	protected ArrayList<Integer> resultItemCounts;
	protected float resultItemChance;
	
	protected ArrayList<Integer> resultItemIds2;
	protected ArrayList<Integer> resultItemCounts2;
	protected float resultItemChance2;
	
	protected ArrayList<Integer> resultItemIds3;
	protected ArrayList<Integer> resultItemCounts3;
	protected float resultItemChance3;
	
	protected ArrayList<Integer> resultItemIds4;
	protected ArrayList<Integer> resultItemCounts4;
	protected float resultItemChance4;
	
	protected int recipeItemId;
	protected boolean qualityChangeable;
	protected boolean allowDyes;
	protected boolean allowEssences;
	protected int experience =0;
	protected int creationTime = 0;
	protected long date=0L;
	
	protected LinkedList<LinkedList<CraftingComponent>> requiredCraftingComponents = new LinkedList<LinkedList<CraftingComponent>>();
	int gridSize = 4;
	
	public CraftingRecipe(int id, String name) {
		this.id = id;
		this.name = name;
	}
	
	public String getIconName() {
		return iconName;
	}
	public void setIconName(String icon) {
		iconName = icon;
	}
	public long GetDate() {
		return date;
	}
	public void SetDate(long date) {
		this.date = date;
	}
	public String getStationReq() {
		return stationReq;
	}
	public void setStationReq(String req) {
		stationReq = req;
	}
	public int getExperience()
	{
		return experience;
	}
	public void setExperience(int exp) {
		experience = exp;
	}
	
	public int getCreationTime()
	{
		return creationTime;
	}
	public void setCreationTime(int time) {
		creationTime = time;
	}
	
	public boolean getMustMatchLayout(){
		return mustMatchLayout;
	}
	public void setMustMatchLayout(Boolean layoutReq){
		mustMatchLayout = layoutReq;
	}
	
	public boolean getIsHiddenRecipe(){
		return isHiddenRecipe;
	}
	public void setIsHiddenRecipe(Boolean hidden){
		isHiddenRecipe = hidden;
	}
	
	public int getSkillID(){
		return skillID;
	}
	public void setSkillID(int skill){
		skillID = skill;
	}
	
	public int getRequiredSkillLevel(){
		return requiredSkillLevel;
	}
	public void setRequiredSkillLevel(int level){
		requiredSkillLevel = level;
	}
	
	
	public float getResultItemChance(){
		return resultItemChance;
	}
	public void setResultItemChance(float chance){
		resultItemChance = chance;
	}
	public float getResultItemChance2(){
		return resultItemChance2;
	}
	public void setResultItemChance2(float chance){
		resultItemChance2 = chance;
	}
	public float getResultItemChance3(){
		return resultItemChance3;
	}
	public void setResultItemChance3(float chance){
		resultItemChance3 = chance;
	}
	public float getResultItemChance4(){
		return resultItemChance4;
	}
	public void setResultItemChance4(float chance){
		resultItemChance4 = chance;
	}


	public ArrayList<Integer> getResultItemIds(){
		return resultItemIds;
	}
	public void setResultItemIds(ArrayList<Integer> ids){
		resultItemIds = ids;
	}
	public ArrayList<Integer> getResultItemIds2(){
		return resultItemIds2;
	}
	public void setResultItemIds2(ArrayList<Integer> ids){
		resultItemIds2 = ids;
	}
	public ArrayList<Integer> getResultItemIds3(){
		return resultItemIds3;
	}
	public void setResultItemIds3(ArrayList<Integer> ids){
		resultItemIds3 = ids;
	}
	public ArrayList<Integer> getResultItemIds4(){
		return resultItemIds4;
	}
	public void setResultItemIds4(ArrayList<Integer> ids){
		resultItemIds4 = ids;
	}

	public ArrayList<Integer> getResultItemCounts() {
		return resultItemCounts;
	}
	public void setResultItemCounts(ArrayList<Integer> counts) {
		resultItemCounts = counts;
	}
	public ArrayList<Integer> getResultItemCounts2() {
		return resultItemCounts2;
	}
	public void setResultItemCounts2(ArrayList<Integer> counts) {
		resultItemCounts2 = counts;
	}
	public ArrayList<Integer> getResultItemCounts3() {
		return resultItemCounts3;
	}
	public void setResultItemCounts3(ArrayList<Integer> counts) {
		resultItemCounts3 = counts;
	}
	public ArrayList<Integer> getResultItemCounts4() {
		return resultItemCounts4;
	}
	public void setResultItemCounts4(ArrayList<Integer> counts) {
		resultItemCounts4 = counts;
	}
	
	public int getRecipeItemId(){
		return recipeItemId;
	}
	public void setRecipeItemId(int id){
		recipeItemId = id;
	}
	
	public boolean getQualityChangeable(){
		return qualityChangeable;
	}
	public void setQualityChangeable(boolean changeable){
		qualityChangeable = changeable;
	}
	
	public boolean getAllowDyes(){
		return allowDyes;
	}
	public void setAllowDyes(boolean allow){
		allowDyes = allow;
	}
	
	public boolean getAllowEssences(){
		return allowEssences;
	}
	public void setAllowEssences(boolean allow){
		allowEssences = allow;
	}
	
	public String getName(){
		return name;
	}
	
	public int getID() {
		return id;
	}
	
	public LinkedList<LinkedList<CraftingComponent>> getRequiredCraftingComponents(){
		return requiredCraftingComponents;
	}
	public void addCraftingComponentRow(LinkedList<CraftingComponent> defs){
		requiredCraftingComponents.add(defs);
	}
	
	public LinkedList<Integer> getRequiredItems() {
		LinkedList<Integer> requiredItems = new LinkedList<Integer>();
		for (int i = 0; i < requiredCraftingComponents.size(); i++) {
			for (int j = 0; j < requiredCraftingComponents.size(); j++) {
				if (requiredCraftingComponents.get(i).get(j).itemId != -1) {
					requiredItems.add(requiredCraftingComponents.get(i).get(j).itemId);
				}
			}
		}
		return requiredItems;
	}
	
	public LinkedList<Integer> getRequiredItemCounts() {
		LinkedList<Integer> requiredCounts = new LinkedList<Integer>();
		for (int i = 0; i < requiredCraftingComponents.size(); i++) {
			for (int j = 0; j < requiredCraftingComponents.size(); j++) {
				if (requiredCraftingComponents.get(i).get(j).itemId != -1) {
					requiredCounts.add(requiredCraftingComponents.get(i).get(j).count);
				}
			}
		}
		return requiredCounts;
	}
	
	public boolean DoesRecipeMatch(LinkedList<LinkedList<CraftingComponent>> components, String stationType) {
		// First check station type
		Log.debug("CRAFTING: recipeMatch station: " + stationType + " against required station: " + stationReq);
		if (!stationReq.equals(stationType) && !stationReq.contains("none")) {
			return false;
		}
		Log.debug("DoesRecipeMatch: mustMatchLayout:"+mustMatchLayout+" recipe "+this.getID());
		if (!mustMatchLayout) {
			
				LinkedList<CraftingComponent> componentIds = new LinkedList<CraftingComponent>();
				for (int i = 0; i < requiredCraftingComponents.size(); i++) {
					for (int j = 0; j < requiredCraftingComponents.get(i).size(); j++) {
						//Dragonsan Skip components with Id = -1
						if(requiredCraftingComponents.get(i).get(j).itemId>0)
						componentIds.add(requiredCraftingComponents.get(i).get(j));
					}
				}
				//FIXME Dragonsan check number items placed and recipe number required items 
				int count=0;
				for (int i = 0; i < components.size(); i++) {
					for (int j = 0; j < components.get(i).size(); j++) {	
						if (components.get(i).get(j).itemId > 0)
							count++;
					}
				}
				
				if (componentIds.size()!=count) {
					Log.debug("DoesRecipeMatch: componentIds.size():"+componentIds.size()+" != "+count+"  false recipe "+this.getID());
					return false;
				}
					
				// Loop through all required component Ids and see if they are provided
				for (CraftingComponent component : componentIds) {
					boolean componentFound = false;
					for (int i = 0; i < components.size(); i++) {
						LinkedList<CraftingComponent> rowComponents = components.get(i);
						for (int j = 0; j < rowComponents.size(); j++) {
							if (rowComponents.get(j).itemId == component.itemId && rowComponents.get(j).count >= component.count) {
								componentFound = true;
								Log.debug("DoesRecipeMatch: componentFound:"+componentFound+" break for");
								break;
							}
						}
					}
					
					if (!componentFound) {
						Log.debug("DoesRecipeMatch: componentFound:"+componentFound+"  not math "+this);
						return false;
					}
				}
				Log.debug("DoesRecipeMatch: reciepe math "+this);
				return true;
			
		/*	
		  
		  
		  LinkedList<CraftingComponent> reqComponentIds = new LinkedList<CraftingComponent>();
		 
			LinkedList<Integer> uReqComponentIds = new LinkedList<Integer>();
			for (int i = 0; i < requiredCraftingComponents.size(); i++) {
				for (int j = 0; j < requiredCraftingComponents.get(i).size(); j++) {
					//Dragonsan Skip components with Id = -1
					//componentIds.contains(requiredCraftingComponents.get(i).get(j)
					if(requiredCraftingComponents.get(i).get(j).itemId>0)
					if (uReqComponentIds.contains(requiredCraftingComponents.get(i).get(j).itemId)){
						for (CraftingComponent component : reqComponentIds) {
							if (component.itemId == requiredCraftingComponents.get(i).get(j).itemId) {
								component.count += requiredCraftingComponents.get(i).get(j).count;
								continue;
							}
						}
					}else {
						reqComponentIds.add(requiredCraftingComponents.get(i).get(j));
						uReqComponentIds.add(requiredCraftingComponents.get(i).get(j).itemId);
							
					}
				}
			}
			
			LinkedList<CraftingComponent> placedComponentIds = new LinkedList<CraftingComponent>();
			LinkedList<Integer> uPlacedComponentIds = new LinkedList<Integer>();
			for (int i = 0; i < components.size(); i++) {
				for (int j = 0; j < components.get(i).size(); j++) {
					if (uPlacedComponentIds.contains(components.get(i).get(j).itemId)){
						for (CraftingComponent component : placedComponentIds) {
							if (component.itemId == components.get(i).get(j).itemId) {
								component.count += components.get(i).get(j).count;
								continue;
							}
						}
					}else {
						placedComponentIds.add(components.get(i).get(j));
						uPlacedComponentIds.add(components.get(i).get(j).itemId);
							
					}
				}
			}
			if (reqComponentIds.size()!=placedComponentIds.size())
				return false;
			// Loop through all required component Ids and see if they are provided
			for (CraftingComponent component : reqComponentIds) {
				boolean componentFound = false;
				for (CraftingComponent placedComponent : placedComponentIds) {
					if (placedComponent.itemId == component.itemId && placedComponent.count >= component.count) {
						componentFound = true;
						break;
					}
				}
			
				
				
				
				if (!componentFound) {
					return false;
				}
			}
			
			return true;
			/**/
		}
		
		for (int i = 0; i < requiredCraftingComponents.size(); i++) {
			LinkedList<CraftingComponent> reqRowComponents = requiredCraftingComponents.get(i);
			LinkedList<CraftingComponent> rowComponents = null;
			if (components.size() > i)
				rowComponents = components.get(i);
			for (int j = 0; j < reqRowComponents.size(); j++) {
				CraftingComponent reqComponent = reqRowComponents.get(j);
				if (reqComponent != null && reqComponent.itemId != -1) {
					if (rowComponents == null || rowComponents.size() <= j) {
						Log.debug("CRAFTING: item in row: " + i + " slot: " + j + " is null");
						return false;
					}
					if (reqComponent.itemId != rowComponents.get(j).itemId) {
						Log.debug("CRAFTING: item in row: " + i + " slot: " + j + " does not match item: " + reqComponent.itemId + " got: " + rowComponents.get(j).itemId);
						return false;
					}
					if (reqComponent.count > rowComponents.get(j).count) {
						Log.debug("CRAFTING: item in row: " + i + " slot: " + j + " does not match count for item: " + reqComponent.itemId + " got: " + rowComponents.get(j).count);
						return false;
					}
				} else {
					// If the slot should be null, but the player placed an item in, we also return false
					if (rowComponents != null && rowComponents.size() > j 
							&& rowComponents.get(j) != null && rowComponents.get(j).itemId != -1) {
						Log.debug("CRAFTING: item in row: " + i + " slot: " + j + " should be null but isn't, got item: " + rowComponents.get(j).itemId);
						return false;
					}
				}
			}
		}
		
		return true;
	}
}
