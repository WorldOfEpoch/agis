package atavism.agis.objects;

import java.io.*;
import java.util.Random;

public class ResourceDrop implements Serializable {
	public int item;
	public int min;
	public int max;
	public float chance;
	public float chanceMax;
	
	public ResourceDrop(int item, int min, int max, float chance, float chanceMax) {
		this.item = item;
		this.min = min;
		this.max = max;
		this.chance = chance;
		this.chanceMax = chanceMax;
	}
	public float Chance (){
	
		Random rand = new Random();
		if(chanceMax>chance)
			return chance + rand.nextFloat()*(chanceMax-chance);
		return chance;
		
	}
	private static final long serialVersionUID = 1L;
}