package atavism.agis.objects;

import java.util.ArrayList;

public class LevelExpRequirement 
{
	public int profile;
	public int level;
	public int expRequired;	
	public int reward_template_id;
	
	public RewardTemplate rewardTemplate;
	
	public class RewardTemplate 
	{
		public int reward_template_id;
		public String mailSubject;
		public String mailMessage;
		
		private ArrayList<Reward> rewards = new ArrayList<Reward>();
		
		public ArrayList<Reward> getRewards() {
			return rewards;
		}
		
		public void addReward(Reward reward) {
			rewards.add(reward);
		}
		public String toString() {return "[LevelExpRequirement:RewardTemplate  reward_template_id="+reward_template_id+" mailSubject="+mailSubject+" mailMessage="+mailMessage+" rewards="+rewards+" ]";}
	}
	
	public class Reward
	{	
		public int reward_id;
		public RewardType reward_type;
		public int reward_value;
		public int reward_amount;
		public boolean give_once; //Indicates if the reward will only be given out once if a level has already been gained before.
		public boolean on_level_down; //Indicates if the reward will be given out on level down (true = down, false = up).
		
		public String toString() {return "[LevelExpRequirement:Reward reward_id="+reward_id+" reward_type="+reward_type+" reward_value="+reward_value+" reward_amount="+reward_amount+" give_once="+give_once+" on_level_down="+on_level_down+"]";}
	}

	public enum RewardType 
	{
		ITEM,
		ITEM_MAIL,
		SKILL_POINT,
		TALENT_POINT,
		ABILITY,
		EFFECT
	}
	
	public String toString() {return "[LevelExpRequirement  profile="+profile+" level="+level+" expRequired="+expRequired+" reward_template_id="+reward_template_id+" ]";}
	
}
