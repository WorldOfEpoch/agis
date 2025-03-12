package atavism.agis.core;

import java.io.*;

public class AgisCombatSkill extends AgisSkill implements Serializable {

    /*public static AgisCombatSkill Brawling = new AgisCombatSkill("Brawling");
    public static AgisCombatSkill Sword = new AgisCombatSkill("Sword");
    public static AgisCombatSkill Spear = new AgisCombatSkill("Spear");
    public static AgisCombatSkill Axe = new AgisCombatSkill("Axe");
    public static AgisCombatSkill Dagger = new AgisCombatSkill("Dagger");*/

    public AgisCombatSkill() {
    }

    public AgisCombatSkill(int id, String name) {
        super(id, name);
    }

    public String toString() {
        return "[AgisCombatSkill: " + getName() + "]";
    }

    private static final long serialVersionUID = 1L;
}
