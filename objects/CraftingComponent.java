package atavism.agis.objects;

/**
 * The information about a component used in a crafting recipe.
 * 
 *
 */
public class CraftingComponent {
	protected String name;
	
	protected int count;
	
	protected int itemId;
	
	public CraftingComponent(String name, Integer id){
		this.name = name;
		count = 1;
		itemId = id;
	}
	public CraftingComponent(String name, int count, Integer id){
		this.name = name;
		this.count = count;
		itemId = id;
	}

	public String getName(){
		return name;
	}
	public int getCount(){
		return count;
	}
	public int getItemId(){
		return itemId;
	}
}
