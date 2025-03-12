package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class EnchantProfileLevel implements Serializable {
	protected int id;
	protected String name;
	protected int level;
	protected boolean percentage;
	protected boolean all_stats;
	protected boolean add_not_exist;
	protected float stat_value = 0;
	protected int lower_by =  0;
	protected int lower_to = -1;
	protected float chance = 100f;
	protected int cost = 0;
	protected int currency = 1;
	protected int gear_score = 0;
	protected float gear_scorep = 0;
	protected ArrayList<Integer> effects = new ArrayList<Integer>();
	protected ArrayList<Integer> abilities = new ArrayList<Integer>();

	HashMap<String, EnchantStat> stats = new HashMap<String, EnchantStat>();	
	public EnchantProfileLevel() {
	}
	
	public EnchantProfileLevel(int id,String name, int level) {
		this.id = id;
		this.name = name;
		this.level = level;
	}
	
	public void SetName(String name){
		this.name = name;
	}
	public String GetName(){
		return name;
	}
	public void SetCost(int cost){
		this.cost = cost;
	}
	public int GetCost(){
		return cost;
	}
	public void SetCurrency(int currency){
		this.currency = currency;
	}
	public int GetCurrency(){
		return currency;
	}
	
	
	public void SetLevel(int level){
		this.level = level;
	}
	public int GetLevel(){
		return level;
	}
	
	public void SetPercentage(boolean percentage){
		this.percentage = percentage;
	}
	public boolean GetPercentage(){
		return percentage;
	}

	public void SetAllStats(boolean all_stats){
		this.all_stats = all_stats;
	}
	public boolean GetAllStats(){
		return all_stats;
	}
	public void SetAddNotExist(boolean add_not_exist){
		this.add_not_exist = add_not_exist;
	}
	public boolean GetAddNotExist(){
		return add_not_exist;
	}
	public void SetStatValue(float stat_value){
		this.stat_value = stat_value;
	}
	public float GetStatValue(){
		return stat_value;
	}
	public void SetLowerBy(int lower_by){
		this.lower_by = lower_by;
	}
	public int GetLowerBy(){
		return lower_by;
	}
	public void SetLowerTo(int lower_to){
		this.lower_to = lower_to;
	}
	public int GetLowerTo(){
		return lower_to;
	}
	
	public void SetStats(HashMap<String, EnchantStat> stats){
		this.stats = stats;
	}
	public HashMap<String, EnchantStat> GetStats(){
		return stats;
	}
	
	public void SetChance(float chance) {
		this.chance = chance;
	}
	public float GetChance() {
		return chance;
	}
	
	public void SetGearScoreValue(int gear_score){
		this.gear_score = gear_score;
	}
	public int GetGearScoreValue(){
		return gear_score;
	}	
	
	public void SetGearScoreValuePercentage(float gear_scorep){
		this.gear_scorep = gear_scorep;
	}
	public float GetGearScoreValuePercentage(){
		return gear_scorep;
	}	
	
	public void SetEffects(ArrayList<Integer> effects) {
		this.effects = effects;
	}
	
	public ArrayList<Integer> GetEffects() {
		return effects;
	}
	
	public ArrayList<Integer> GetAbilities() {
		return abilities;
	}
	
	public void SetAbilities(ArrayList<Integer> abilities) {
		this.abilities = abilities;
	}
	
	
	/*
	 * Final Static properties
	 */

	private static final long serialVersionUID = 1L;
	
}
