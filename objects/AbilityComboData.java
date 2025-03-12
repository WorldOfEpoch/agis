package atavism.agis.objects;

import java.io.Serializable;
import java.util.Random;

public class AbilityComboData implements Serializable {

	public AbilityComboData() {
	}

	public AbilityComboData(int abilityId, float chanceMin, float chanceMax, float time, boolean showInCenterUi, boolean replaceInSlot) {
		this.abilityId = abilityId;
		this.chanceMin = chanceMin;
		this.chanceMax = chanceMax;
		this.time = time;
		this.showInCenterUi = showInCenterUi;
		this.replaceInSlot = replaceInSlot;
	}

	public String toString() {
		return "[AbilityComboData: abilityId=" + abilityId + "; chanceMin=" + chanceMin + "; chanceMax=" + chanceMax + "; time=" + time + "; showInCenterUi=" + showInCenterUi + "; replaceInSlot=" + replaceInSlot + " ]";
	}

	public double getChance(){
		Random rand = new Random();
		double roll = rand.nextDouble();
		return getChanceMin() + roll * ( getChanceMax() - getChanceMin() );
	}
	protected int abilityId;
	protected float chanceMin;
	protected float chanceMax;
	protected float time;
	protected boolean showInCenterUi;
	protected boolean replaceInSlot;
	protected boolean checkCooldown = true;


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

	public float getTime() {
		return time;
	}

	public void setTime(float time) {
		this.time = time;
	}

	public boolean getShowInCenterUi() {
		return showInCenterUi;
	}

	public void setShowInCenterUi(boolean showInCenterUi) {
		this.showInCenterUi = showInCenterUi;
	}

	public boolean getReplaceInSlot() {
		return replaceInSlot;
	}

	public void setReplaceInSlot(boolean replaceInSlot) {
		this.replaceInSlot = replaceInSlot;
	}

	public boolean getCheckCooldown() {
		return checkCooldown;
	}

	public void setCheckCooldown(boolean checkCooldown) {
		this.checkCooldown = checkCooldown;
	}

	private static final long serialVersionUID = 1L;
}
 