package atavism.agis.objects;

import java.io.Serializable;

public class ArenaWeapon implements Serializable {
    public ArenaWeapon() {
    }
    
    public ArenaWeapon(int abilityID, String type, int displayID) {
    	uses = 10;
    	this.abilityID = abilityID;
    	this.type = type;
    	if (abilityID == ArenaAbilities.ABILITY_MELEE_ATTACK) {
    		cooldown = 1000;
    		damage = 10;
    		range = 6000;
    	} else if (abilityID == ArenaAbilities.ABILITY_RANGED_ATTACK) {
    		cooldown = 1500;
    		damage = 15;
    		range = 20000;
    	} else if (abilityID == ArenaAbilities.ABILITY_UNARMED_ATTACK) {
    		cooldown = 1000;
    		damage = 6;
    		range =6000;
    	}
    }
    
    public int weaponUsed() {
    	uses--;
    	return uses;
    }
    
	public int getUses() { return uses;}
    public void setUses(int uses) {
    	this.uses = uses;
    }
    
    public String getWeaponType() { return type;}
    public void setWeaponType(String type) {
    	this.type = type;
    }
    
    public int getWeaponID() { return weaponID;}
    public void setWeaponID(int weaponID) {
    	this.weaponID = weaponID;
    }
    
    public int getDisplayID() { return displayID;}
    public void setDisplayID(int displayID) {
    	this.displayID = displayID;
    }
    
    public int getAbilityID() { return abilityID; }
    
    public int getCooldown() { return cooldown;}
    public void setCooldown(int cooldown) {
    	this.cooldown = cooldown;
    }
    
    public int getDamage() { return damage;}
    public void setDamage(int damage) {
    	this.damage = damage;
    }
    
    public int getRange() { return range;}
    public void setRange(int range) {
    	this.range = range;
    }
    
    int uses;
    String type;
    int weaponID = -1;
    int abilityID = -1;
    int displayID = -1;
    int cooldown = 1000;
    int damage = 1;
    int range = 1000;

    private static final long serialVersionUID = 1L;
    public static final String ARENA_WEAPON_MELEE = "Melee";
    public static final String ARENA_WEAPON_RANGED = "Ranged";
    public static final String ARENA_WEAPON_UNARMED = "Unarmed";
    public static final String ARENA_WEAPON_SHIELD = "Shield";
}
