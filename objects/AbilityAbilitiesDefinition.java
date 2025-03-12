package atavism.agis.objects;

import atavism.server.util.Log;

import java.io.Serializable;
import java.util.Random;
/**
 * Object to store of the ability definition for ability that will be run to the target
 */
public class AbilityAbilitiesDefinition implements Serializable {

	public AbilityAbilitiesDefinition() {
	}
	public AbilityAbilitiesDefinition(String target, int abilityId, float chanceMin, float chanceMax, int delay){
		this.target = target;
		this.abilityId = abilityId;
		this.chanceMin = chanceMin;
		this.chanceMax = chanceMax;
		this.delay = delay;
	}

	public String toString() {
		return "[AbilityAbilitiesDefinition: abilityId=" + abilityId + "; chanceMin=" + chanceMin + "; chanceMax=" + chanceMax + "; target=" + target + " delay=" + delay + " ]";
	}

	/**
	 * Calculate Chance
	 * @return
	 */
	public double getChance(){
		if(getChanceMin() >= getChanceMax())
			return getChanceMin();
		Random rand = new Random();
		double roll = rand.nextDouble();
		return getChanceMin() + roll * ( getChanceMax() - getChanceMin() );
	}

	/**
	 * Calculate if ability can be run
	 * @return
	 */
	public boolean CanBeApply() {
		Random rand = new Random();
		double roll = rand.nextDouble() * 100D;
		double chance = getChance();
		if(Log.loggingDebug)Log.debug(this + " CanBeApply roll=" + roll + " chance=" + chance);
		return roll < chance;
	}

	protected int abilityId;
	protected float chanceMin;
	protected float chanceMax;

	protected String target = "";

	protected long delay = 0;

	public void setDelay(long delay){
		this.delay = delay;
	}
	public long getDelay(){
		return delay;
	}
	public int getAbilityId() {
		return abilityId;
	}

	public void setAbilityId(int abilityId) {
		this.abilityId = abilityId;
	}

	public float getChanceMin() {
		return chanceMin;
	}

	public void setChanceMin(float chanceMin) {
		this.chanceMin = chanceMin;
	}

	public float getChanceMax() {
		return chanceMax;
	}

	public void setChanceMax(float chanceMax) {
		this.chanceMax = chanceMax;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	private static final long serialVersionUID = 1L;
}
 