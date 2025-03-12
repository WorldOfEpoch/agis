package atavism.agis.core;

import atavism.agis.objects.LevelingMap;
import atavism.server.util.*;
import java.io.*;

public class AgisSkill implements Serializable {

    public static AgisSkill NullSkill = 
        new AgisSkill(-1, "NullSkill");

    public AgisSkill() {
    }

    public AgisSkill(int id, String name) {
    	setID(id);
        setName(name);
    }

    public String toString() {
        return "[AgisSkill: " + getName() + "]";
    }

    public boolean equals(Object other) {
        AgisSkill otherSkill = (AgisSkill) other;
        boolean val = getName().equals(otherSkill.getName());
        return val;
    }

    public int hashCode() {
        return getName().hashCode();
    }
    
    public void setID(int id) {
        this.id = id;
    }
    public int getID() {
        return id;
    }
    int id = -1;

    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
    String name = null;

    public void setSkillCostMultiplier(int c) {
        skillCost = c;
    }
    public int getSkillCostMultiplier() {
        return skillCost;
    }
    int skillCost = 1;

    /**
     * each level costs 1000 xp
     */
    public void setLevelCostMultiplier(int c) {
        levelCost = c;
    }
    public int getLevelCostMultiplier() {
        return levelCost;
    }
    int levelCost = 1000;

    /**
     * returns the amount of total xp required to be at level 'skillLevel'
     * for this skill
     */
    public int xpRequired(int level) {
        return (level * (level + 1)) / 2 * levelCost * skillCost;
    }

    /**
     * returns the level you have in this skill if you have the xp passed in
     */
    public int getLevel(int xp) {
        // do real math here sometime
        int i=0;
        while (xpRequired(i+1) < xp) {
            i++;
        }
        if (Log.loggingDebug)
            Log.debug("AgisSkill.getLevel: skill=" + getName() + 
                      ", level=" + i);
        return i;
    }

    int defaultAbility = -1;
    int exp_per_use = 0;
    LevelingMap lm = new LevelingMap();
    int exp_max = 100;
    int rank_max = 3;

    public void setDefaultAbility(int ability) {
        defaultAbility = ability;
    }

    public int getDefaultAbility() {
        return defaultAbility;
    }
    
     /**
     * -Experience system component-
     * 
     * Returns the amount of experience to be gained after a successful use of
     * this skill.
     */
    public int getExperiencePerUse() {
        return exp_per_use;
    }

    /**
     * -Experience system component-
     * 
     * Sets the amount of experience that should be gained after a successful
     * use of this skill.
     * <p>
     * NOTE: Skill increases are meant to be minimal since there will generally
     * be many abilities increasing the skill level.
     */
    public void setExperiencePerUse(int xp) {
        exp_per_use = xp;
    }

    public void setLevelingMap(LevelingMap lm) {
        this.lm = lm;
    }

    public LevelingMap getLevelingMap() {
        return this.lm;
    }

    /**
     * -Experience system component-
     * 
     * Returns the default max experience required before increasing this skills
     * level.
     */
    public int getBaseExpThreshold() {
        return exp_max;
    }

    /**
     * -Experience system component-
     * 
     * Sets the default max experience required to increase the skills level.
     */
    public void setBaseExpThreshold(int max) {
        exp_max = max;
    }

    /**
     * -Experience system component-
     * 
     * Returns the max possible rank for this skill.
     */
    public int getMaxRank() {
        return rank_max;
    }

    /**
     * -Experience system component-
     * 
     * Sets the max possible rank for this skill.
     */
    public void setMaxRank(int rank) {
        rank_max = rank;
    }


    private static final long serialVersionUID = 1L;
}
