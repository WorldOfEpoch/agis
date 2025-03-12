package atavism.agis.objects;

import java.io.Serializable;

public class DamageType implements Serializable {
	protected int id;
	protected String damageTypeName;
	protected String resistanceStat;
	protected String powerStat;
	protected String accuracyStat;
	protected String evasionStat;
	protected String criticChanceStat;
	protected String criticPowerStat;

	public DamageType() {
	}

	public DamageType(int id, String damageTypeName, String resistanceStat, String powerStat, String accuracyStat, String evasionStat, String criticChanceStat, String criticPowerStat) {
		this.id = id;
		this.damageTypeName = damageTypeName;
		this.resistanceStat = resistanceStat;
		this.powerStat = powerStat;
		this.accuracyStat = accuracyStat;
		this.evasionStat = evasionStat;
		this.criticChanceStat = criticChanceStat;
		this.criticPowerStat = criticPowerStat;
	}

	public void setDamageTypeName(String damageTypeName) {
		this.damageTypeName = damageTypeName;
	}

	public String getDamageTypeName() {
		return damageTypeName;
	}

	public void setResistanceStat(String resistanceStat) {
		this.resistanceStat = resistanceStat;
	}

	public String getResistanceStat() {
		return resistanceStat;
	}

	public void setPowerStat(String powerStat) {
		this.powerStat = powerStat;
	}

	public String getPowerStat() {
		return powerStat;
	}

	public void setAccuracyStat(String accuracyStat) {
		this.accuracyStat = accuracyStat;
	}

	public String getAccuracyStat() {
		return accuracyStat;
	}

	public void setEvasionStat(String evasionStat) {
		this.evasionStat = evasionStat;
	}

	public String getEvasionStat() {
		return evasionStat;
	}

	public void setCriticChanceStat(String criticChanceStat) {
		this.criticChanceStat = criticChanceStat;
	}

	public String getCriticChanceStat() {
		return criticChanceStat;
	}

	public void setCriticPowerStat(String criticPowerStat) {
		this.criticPowerStat = criticPowerStat;
	}

	public String getCriticPowerStat() {
		return criticPowerStat;
	}

	public String toString() {
		return "[DamageType: id=" + id + ", type=" + ", resistanceStat=" + resistanceStat + ", powerStat=" + powerStat + ", accuracyStat=" + accuracyStat + ", evasionStat=" + evasionStat + ", criticChanceStat="
				+ criticChanceStat + ", criticPowerStat=" + criticPowerStat + "]";
	}

	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;

}
