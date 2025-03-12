package atavism.agis.objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import atavism.agis.core.Agis;
import atavism.agis.core.AgisAbility;
import atavism.agis.core.AgisSkill;
import atavism.server.util.Log;

/*
 * The ProfessionObject is setup to
 */
public class ProfessionObject {

    HashMap<Integer, AgisAbility> classabilities = new HashMap<Integer, AgisAbility>();
    HashMap<Integer, AgisAbility> defaultabilities = new HashMap<Integer, AgisAbility>();
    HashMap<Integer, AgisSkill> classskills = new HashMap<Integer, AgisSkill>();
    HashMap<Integer, AgisSkill> defaultskills = new HashMap<Integer, AgisSkill>();

    LevelingMap lm = new LevelingMap();

    HashMap<String, LevelingMap> statslm = new HashMap<String, LevelingMap>();

    ArrayList<String> basestats = new ArrayList<String>();

    String name;

    public ProfessionObject(String name){
        setName(name);
    }

    /**
     * This method allows you to pass an already build ability map for this profession.
     *
     * NOTE: This will override the existing ability map.
     *
     * @param abilityMap
     */
    public void addAbilityMap(HashMap<Integer, AgisAbility> abilityMap) {
        this.classabilities = abilityMap;
    }

    /**
     * This method provides the ability to pass an already built default abilities map for this profession.
     *
     * NOTE: This will override the existing ability map.
     *
     * @param defaultmap
     */
    public void addDefaultAbilityMap(HashMap<Integer, AgisAbility> defaultmap) {
        this.defaultabilities = defaultmap;
    }

    /**
     * This method provides a shortcut for passing ability map as well as default ability map to the profession.
     *
     * NOTE: This will override the existing ability maps.
     *
     * @param abilityMap
     * @param defaultMap
     */
    public void addAbilityMaps(HashMap<Integer, AgisAbility> abilityMap, HashMap<Integer, AgisAbility> defaultMap){
        addAbilityMap(abilityMap);
        addDefaultAbilityMap(defaultMap);
    }

    /**
     * Adds an ability to this profession's list of abilitlies as well as add to default if needed.
     *
     * @param abilityName
     * @param isdefault
     */
    public void addAbility(int abilityID, boolean isdefault) {
        Log.debug("Adding ability to profession object: " + abilityID + " : " + Agis.AbilityManager.get(abilityID));
        if (Agis.AbilityManager.get(abilityID) != null) {
            this.classabilities.put(abilityID, Agis.AbilityManager.get(abilityID));

            if (isdefault) {
                this.defaultabilities.put(abilityID, Agis.AbilityManager.get(abilityID));
            }
        }
    }

    public void addAbility(int abilityID) {
        addAbility(abilityID, false);
    }

    /**
     * Remove the ability from the abilities list and the default list.
     */
    public boolean removeAbility(int abilityID) {
        if (this.classabilities.get(abilityID) == null) { return false; }
        if (this.defaultabilities.get(abilityID) != null) {
            this.defaultabilities.remove(abilityID);
        }
        this.classabilities.remove(abilityID);
        return true;
    }

    /**
     * Method for finding out if ability is available.
     */
    public boolean hasAbility(Integer abilityID) {
        if (this.classabilities.containsKey(abilityID)) {
            return true;
        }
        return false;
    }

    /**
     * Retrieves ability.
     *
     * NOTE: This does not check the validity of the request.
     */
    public AgisAbility getAbility(Integer abilityID) {
        return classabilities.get(abilityID);
    }

    /**
     * Retrieves ability map.
     *
     * NOTE: This does not check the validity of the request.
     */
    public HashMap<Integer, AgisAbility> getAbilityMap() {
        return classabilities;
    }

    /**
     * Retrieves the default ability map.
     *
     * NOTE: This does not check the validity of the request.
     */
    public HashMap<Integer, AgisAbility> getDefaultAbilityMap() {
        return defaultabilities;
    }

    /**
     * This method allows passing an already built skillmap to the profession.
     *
     * NOTE: All existing skills in the skill map will be removed.
     */
    public void addSkillMap(HashMap<Integer, AgisSkill> skillMap) {
        this.classskills = skillMap;
    }

    /**
     * This method allows passing an already built default skill map for the profession.
     *
     * NOTE: All existing skills in this skill map will be removed.
     */
    public void addDefaultSkillMap(HashMap<Integer, AgisSkill> defaultSkillMap) {
        this.defaultskills = defaultSkillMap;
    }

    /**
     * This method allows passing already built skill maps in for the profession.
     *
     * NOTE: All existing skill maps will be removed.
     */
    public void addSkillMaps(HashMap<Integer, AgisSkill> skillMap, HashMap<Integer, AgisSkill> defaultSkillMap) {
        addSkillMap(skillMap);
        addDefaultSkillMap(defaultSkillMap);
    }

    /**
     * Adds a skill to this profession, and places it into default if necessary.
     */
    public void addSkill(int skillID, boolean isdefault) {
        Log.debug("Adding skill to profession object: " + skillID + " : " + Agis.SkillManager.get(skillID));
        if (Agis.SkillManager.get(skillID) != null) {
            //this.classskills.put(skillID, Agis.SkillManager.get(skillID));

            /*if (isdefault) {
                this.defaultskills.put(skillID, Agis.SkillManager.get(skillID));
            }*/
        }
    }

    public void addSkill(int skillID) {
        addSkill(skillID, false);
    }

    /**
     * This method is for removing a skill from this profession.
     */
    public boolean removeSkill(int skillID) {
        if (this.classskills.get(skillID) == null) { return false; }
        if (this.defaultskills.get(skillID) != null) {
            this.defaultskills.remove(skillID);
        }

        this.classskills.remove(skillID);
        return true;
    }

    /**
     * Returns whether this profession has this skill in it's list.
     */
    public boolean hasSkill(Integer skillID) {
        if (this.classskills.containsKey(skillID)) { return true; }
        return false;
    }

    /**
     * Retrieves the skill.
     *
     * NOTE: This does not check the validity of the request.
     */
    public AgisSkill getSkill(Integer skillID) {
        return classskills.get(skillID);
    }

    /**
     * Retrieves entire skill map.
     *
     * NOTE: This does not check the validity of the request.
     */
    public HashMap<Integer, AgisSkill> getSkillMap() {
        return classskills;
    }

    /**
     * Retrieves the default skill map.
     *
     * NOTE: This does not check the validity of the request.
     */
    public HashMap<Integer, AgisSkill> getDefaultSkillMap() {
        return defaultskills;
    }

    /**
     * Sets the profession name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the profession name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Method overriding java's base object toString method.
     */
    public String toString() {
        String str = "";

        str += "[ ProfessionObject: " + getName();
        str += ", Abilities: " + this.classabilities.toString();
        str += ", Default Abilities: " + this.defaultabilities.toString();
        str += ", Skills: " + this.classskills.toString();
        str += ", Default Skills: " + this.defaultskills.toString();
        str += ", Base Stats: " + this.basestats;
        str += ", " + this.lm;
        str += ", Stat Level Maps: ";
        Set<String> keys = statslm.keySet();
        for (String s : keys) {
            str += " " + s + " : " + statslm.get(s).toString();
        }
        str += " ]";

        return str;
    }


    public void applyLevelingMap(LevelingMap lm) {
        this.lm = lm;
    }

    public LevelingMap getLevelingMap() {
        return lm;
    }

    public void applyStatsLevelingMap(String statname, LevelingMap lm) {
        statslm.put(statname.toLowerCase(), lm);
    }

    public LevelingMap getStatsLevelingMap(String statname) {
        return statslm.get(statname.toLowerCase());
    }

    public boolean hasStatLevelModification(String statname, int lvl) {
        if (statslm.containsKey(statname.toLowerCase())) {
            LevelingMap tmp = statslm.get(statname.toLowerCase());
            return tmp.hasLevelModification(lvl);
        }
        return false;
    }

    public void addBaseStat(String statname) {
        if (!basestats.contains(statname.toLowerCase())) {
            basestats.add(statname.toLowerCase());
        }
    }

    public boolean isBaseStat(String statname) {
        return basestats.contains(statname.toLowerCase());
    }
}
