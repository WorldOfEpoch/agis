package atavism.agis.objects;

import atavism.server.engine.OID;

import java.io.Serializable;

public class DrawWeaponInfo implements Serializable {
	protected String slot;
	protected String holsteringWeaponEffect;
	protected int holsteringWeaponTime;
	protected String drawWeaponEffect;
	protected int drawWeaponTime;

	public DrawWeaponInfo() {
	}

	public DrawWeaponInfo(String slot, String drawWeaponEffect, int drawWeaponTime, String holsteringWeaponEffect, int holsteringWeaponTime) {
		this.slot = slot;
		this.drawWeaponEffect = drawWeaponEffect;
		this.drawWeaponTime = drawWeaponTime;
		this.holsteringWeaponEffect = holsteringWeaponEffect;
		this.holsteringWeaponTime = holsteringWeaponTime;
	}

	public void SetDrawWeaponEffect(String drawWeaponEffect){
		this.drawWeaponEffect = drawWeaponEffect;
	}
	public String GetDrawWeaponEffect(){
		return drawWeaponEffect;
	}
	public void SetDrawWeaponTime(int drawWeaponTime){
		this.drawWeaponTime = drawWeaponTime;
	}
	public int GetDrawWeaponTime(){
		return drawWeaponTime;
	}
	public void SetHolsteringWeaponEffect(String holsteringWeaponEffect){
		this.holsteringWeaponEffect = holsteringWeaponEffect;
	}
	public String GetHolsteringWeaponEffect(){
		return holsteringWeaponEffect;
	}
	public void SetHolsteringWeaponTime(int holsteringWeaponTime){
		this.holsteringWeaponTime = holsteringWeaponTime;
	}
	public int GetHolsteringWeaponTime(){
		return holsteringWeaponTime;
	}

	
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
}
