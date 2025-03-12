package atavism.agis.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * Holds information about a Dialogue (conversation). NPC's can be given dialogues for players
 * to read and make choices based on the actions a Dialogue has.
 * @author Andrew Harrison
 *
 */
public class Dialogue implements Serializable {
    public Dialogue() {
    }
    
    public Dialogue(int id, String name, String text) {
    	this.id = id;
    	this.name = name;
    	this.text = text;
    }
    
    public int getID() { return id;}
    public void setID(int id) {
    	this.id = id;
    }

	public String getName() { return name;}
    public void setName(String name) {
    	this.name = name;
    }
    
    public boolean getOpeningDialogue() { return openingDialogue;}
    public void setOpeningDialogue(boolean openingDialogue) {
    	this.openingDialogue = openingDialogue;
    }
    
    public boolean getRepeatable() { return repeatable;}
    public void setRepeatable(boolean repeatable) {
    	this.repeatable = repeatable;
    }
    
    public int getPrereqDialogue() { return prereqDialogue;}
    public void setPrereqDialogue(int prereqDialogue) {
    	this.prereqDialogue = prereqDialogue;
    }
    
    public int getPrereqQuest() { return prereqQuest;}
    public void setPrereqQuest(int prereqQuest) {
    	this.prereqQuest = prereqQuest;
    }
    
    public int getPrereqFaction() { return prereqFaction;}
    public void setPrereqFaction(int prereqFaction) {
    	this.prereqFaction = prereqFaction;
    }
    
    public int getPrereqFactionStance() { return prereqFactionStance;}
    public void setPrereqFactionStance(int prereqFactionStance) {
    	this.prereqFactionStance = prereqFactionStance;
    }
    
    public String getText() { return text;}
    public void setText(String text) {
    	this.text = text;
    }
    public String getAudioClip() { return audioClip;}
    public void setAudioClip(String audioClip) {
    	this.audioClip = audioClip;
    }
    
    public ArrayList<DialogueOption> getOptions() { return options;}
    public void setOptions(ArrayList<DialogueOption> options) {
    	this.options = options;
    }
    public String toString() {
    	String s = "";
    	for(DialogueOption _do : options)
    		s+=_do;
    	return "[Dialogue: id="+id+" name="+name+" Actions={"+s+"}]";
    }
    
    public void addOption(int id, String text, String action, int actionID, int itemReq,boolean itemConsume, int currency, int amount, String audioClip,int reqOpenedQuest, int reqCompletedQuest, int excludingQuest) {
    	DialogueOption option = new DialogueOption();
    	option.id =id;
    	option.text = text;
    	option.action = action;
    	option.actionID = actionID;
    	
    	option.itemReq = itemReq;
    	option.itemConsume = itemConsume;
    	option.currency = currency;
    	option.amount = amount;
    	option.audioClip = audioClip;		
    	option.reqOpenedQuest =reqOpenedQuest;
    	option.reqCompletedQuest = reqCompletedQuest;
    	option.excludingQuest = excludingQuest;
    	options.add(option);
    }
    
    int id;
    String name;
    boolean openingDialogue;
    boolean repeatable;
    int prereqDialogue;
    int prereqQuest;
    int prereqFaction;
    int prereqFactionStance;
    boolean reactionAutoStart;
    String text;
    String audioClip = "";
    ArrayList<DialogueOption> options = new ArrayList<DialogueOption>();
    
    /**
     * An option a player can choose from this Dialogue.
     * @author Andrew Harrison
     *
     */
	public class DialogueOption {
		public int id = -1;
		public String text;
		public String action;
		public int actionID;
		public int itemReq = -1;
		public boolean itemConsume = true;
		public int currency = -1;
		public long amount = 0;
		public String audioClip = "";
		public int reqOpenedQuest = -1;
		public int reqCompletedQuest = -1;
		public int excludingQuest = -1;
		public HashMap<Integer, HashMap<String, Integer>> requirements = new HashMap<Integer, HashMap<String, Integer>>();
		
		public String toString() {
	    	return "[DialogueOption: id="+id+" text="+text+" action="+action+" actionID="+actionID+" currency="+currency+" audioClip="+audioClip+" amount="+amount+" reqOpenedQuest="+reqOpenedQuest+" reqCompletedQuest="+reqCompletedQuest+" excludingQuest="+excludingQuest+" requirements="+requirements+"]";
	    }
	}
    
    private static final long serialVersionUID = 1L;
}
