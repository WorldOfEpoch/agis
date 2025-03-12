package atavism.agis.objects;

import atavism.server.util.Log;

import java.io.Serializable;
import java.util.Random;

/**
 * Object to store of the effect definition for ability that will be apply to the target
 */
public class AbilityEffectDefinition implements Serializable {

	public AbilityEffectDefinition() {
	}
	public AbilityEffectDefinition(String target,int effectId, float chanceMin, float chanceMax, int delay){
		this.target = target;
		this.effectId = effectId;
		this.chanceMin = chanceMin;
		this.chanceMax = chanceMax;
		this.delay = delay;
	}

	public String toString() {
		return "[AbilityEffectDefinition: effectId=" + effectId + "; chanceMin=" + chanceMin + "; chanceMax=" + chanceMax + "; target=" + target + " ]";
	}

	/**
	 * Function to calculate Chance
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
	 * Function to check if effect can be apply
	 * @return
	 */
	public boolean CanBeApply() {
		Random rand = new Random();
		double roll = rand.nextDouble() * 100D;
		double chance = getChance();
		if(Log.loggingDebug)Log.debug(this + " CanBeApply roll=" + roll + " chance=" + chance);
		return roll < chance;
	}
	protected int effectId;
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
	public int getEffectId() {
		return effectId;
	}

	public void setEffectId(int effectId) {
		this.effectId = effectId;
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
 