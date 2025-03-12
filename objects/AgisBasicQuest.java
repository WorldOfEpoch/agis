package atavism.agis.objects;

import java.io.*;
import java.util.*;

import atavism.server.engine.OID;

public class AgisBasicQuest extends AgisQuest {
    public AgisBasicQuest() {
        super();
    }

    /**
     * a list of items that the quest gives to the player
     * when the player accepts the quest
     */
    public void setDeliveryItems(List<Integer> items) {
        lock.lock();
        try {
            deliveryItems = new LinkedList<Integer>(items);
        }
        finally {
            lock.unlock();
        }
    }
    public List<Integer> getDeliveryItems() {
        lock.lock();
        try {
            return new LinkedList<Integer>(deliveryItems);
        }
        finally {
            lock.unlock();
        }
    }
    public void addDeliveryItem(int templateID) {
        lock.lock();
        try {
            deliveryItems.add(templateID);
        }
        finally {
            lock.unlock();
        }
    }
    
    public int clearGoals() {
    	lock.lock();
    	int numGoals = 0;
    	try {
    		numGoals += collectionGoals.size();
    		this.collectionGoals.clear();
    		numGoals += killGoals.size();
    		this.killGoals.clear();
    		numGoals += categoryKillGoals.size();
    		this.categoryKillGoals.clear();
    		numGoals += taskGoals.size();
    		this.taskGoals.clear();
    	} finally {
    		lock.unlock();
    	}
    	return numGoals;
    }

    public void setCollectionGoals(List<CollectionGoal> goals) {
        lock.lock();
        try {
            this.collectionGoals = new LinkedList<CollectionGoal>(goals);
        }
        finally {
            lock.unlock();
        }
    }
    public List<CollectionGoal> getCollectionGoals() {
        lock.lock();
        try {
            return new LinkedList<CollectionGoal>(collectionGoals);
        }
        finally {
            lock.unlock();
        }
    }
    public void addCollectionGoal(CollectionGoal goal) {
        lock.lock();
        try {
        	collectionGoals.add(goal);
        }
        finally {
            lock.unlock();
        }
    }
    
    public void setKillGoals(List<KillGoal> goals) {
        lock.lock();
        try {
            this.killGoals = new LinkedList<KillGoal>(goals);
        }
        finally {
            lock.unlock();
        }
    }
    public List<KillGoal> getKillGoals() {
        lock.lock();
        try {
            return new LinkedList<KillGoal>(killGoals);
        }
        finally {
            lock.unlock();
        }
    }
    public void addKillGoal(KillGoal goal) {
        lock.lock();
        try {
        	killGoals.add(goal);
        }
        finally {
            lock.unlock();
        }
    }
    
    public void setCategoryKillGoals(List<CategoryKillGoal> goals) {
        lock.lock();
        try {
            this.categoryKillGoals = new LinkedList<CategoryKillGoal>(goals);
        }
        finally {
            lock.unlock();
        }
    }
    public List<CategoryKillGoal> getCategoryKillGoals() {
        lock.lock();
        try {
            return new LinkedList<CategoryKillGoal>(categoryKillGoals);
        }
        finally {
            lock.unlock();
        }
    }
    public void addCategoryKillGoal(CategoryKillGoal goal) {
        lock.lock();
        try {
        	categoryKillGoals.add(goal);
        }
        finally {
            lock.unlock();
        }
    }
    
    public void setTaskGoals(List<TaskGoal> goals) {
        lock.lock();
        try {
            this.taskGoals = new LinkedList<TaskGoal>(goals);
        }
        finally {
            lock.unlock();
        }
    }
    public List<TaskGoal> getTaskGoals() {
        lock.lock();
        try {
            return new LinkedList<TaskGoal>(taskGoals);
        }
        finally {
            lock.unlock();
        }
    }
    public void addTaskGoal(TaskGoal goal) {
        lock.lock();
        try {
        	taskGoals.add(goal);
        }
        finally {
            lock.unlock();
        }
    }
    
    public HashMap<Integer, List<String>> getObjectives() {
        lock.lock();
        try {
        	HashMap<Integer, List<String>> objectivesMap = new HashMap<Integer, List<String>>();
            
            for (int i = 0; i <= grades; i++) {
            	List<String> l = new LinkedList<String>();
                Iterator<CollectionGoal> iter = collectionGoals.iterator();
                while (iter.hasNext()) {
                    CollectionGoal status = iter.next();
                    if (status.getTier() == i) {
                        String itemName = status.getTemplateName();
                        int numNeeded = status.num;
                
                        String objective = "Collect " + numNeeded + " " + itemName;
                        l.add(objective);
                    }
                }
            
                Iterator<KillGoal> iter2 = killGoals.iterator();
                while (iter2.hasNext()) {
                    KillGoal status = iter2.next();
                    if (status.getTier() == i) {
                        String mobName = status.getMobName();
                        int numNeeded = status.num;
                
                        String objective = "Slay " + numNeeded + " " + mobName;
                        l.add(objective);
                    }
                }
            
                Iterator<CategoryKillGoal> iter3 = categoryKillGoals.iterator();
                while (iter3.hasNext()) {
                    CategoryKillGoal status = iter3.next();
                    if (status.getTier() == i) {
                        String name = status.getName();
                        int numNeeded = status.num;
                
                        String objective = "Slay " + numNeeded + " " + name;
                        l.add(objective);
                    }
                }
                
                Iterator<TaskGoal> iter4 = taskGoals.iterator();
                while (iter4.hasNext()) {
                	TaskGoal status = iter4.next();
                    if (status.getTier() == i) {
                        String name = status.getTaskText();
                        int numNeeded = status.num;
                        
                        String objective = name;
                        if (numNeeded > 1)
                        	objective += " x" + numNeeded;
                        l.add(objective);
                    }
                }
                objectivesMap.put(i, l);
            }
            return objectivesMap;
        }
        finally {
            lock.unlock();
        }
    }
    
    public List<String> getGradeObjectives(int grade) {
        lock.lock();
        try {
        	List<String> objectivesList = new LinkedList<String>();
            
                Iterator<CollectionGoal> iter = collectionGoals.iterator();
                while (iter.hasNext()) {
                    CollectionGoal status = iter.next();
                    if (status.getTier() == grade) {
                        String itemName = status.getTemplateName();
                        int numNeeded = status.num;
                
                        String objective = "Collect " + numNeeded + " " + itemName;
                        objectivesList.add(objective);
                    }
                }
            
                Iterator<KillGoal> iter2 = killGoals.iterator();
                while (iter2.hasNext()) {
                    KillGoal status = iter2.next();
                    if (status.getTier() == grade) {
                        String mobName = status.getMobName();
                        int numNeeded = status.num;
                
                        String objective = "Slay " + numNeeded + " " + mobName;
                        objectivesList.add(objective);
                    }
                }
            
                Iterator<CategoryKillGoal> iter3 = categoryKillGoals.iterator();
                while (iter3.hasNext()) {
                    CategoryKillGoal status = iter3.next();
                    if (status.getTier() == grade) {
                        String name = status.getName();
                        int numNeeded = status.num;
                
                        String objective = "Slay " + numNeeded + " " + name;
                        objectivesList.add(objective);
                    }
                }
                
                Iterator<TaskGoal> iter4 = taskGoals.iterator();
                while (iter4.hasNext()) {
                	TaskGoal status = iter4.next();
                    if (status.getTier() == grade) {
                        String name = status.getTaskText();
                        int numNeeded = status.num;
                        
                        String objective = name;
                        if (numNeeded > 1)
                        	objective += " x" + numNeeded;
                        objectivesList.add(objective);
                    }
                }
            return objectivesList;
        }
        finally {
            lock.unlock();
        }
    }

	public QuestState generate(OID playerOid) {
		lock.lock();
		try {
			List<BasicQuestState.CollectionGoalStatus> goalsStatus = new LinkedList<BasicQuestState.CollectionGoalStatus>();

			// go through all the collection goals and make a list of
			// CollectionGoalStatus objects
			BasicQuestState qs = new BasicQuestState(this, playerOid);
			Iterator<CollectionGoal> iter = collectionGoals.iterator();
			while (iter.hasNext()) {
				CollectionGoal goal = iter.next();
				BasicQuestState.CollectionGoalStatus status = new BasicQuestState.CollectionGoalStatus(goal);
				goalsStatus.add(status);
			}
			qs.setGoalsStatus(goalsStatus);

			// Now do the same for kill Goals
			List<BasicQuestState.KillGoalStatus> killgoalsStatus = new LinkedList<BasicQuestState.KillGoalStatus>();

            // go through all the kill goals and make a list of
            // KillGoalStatus objects
			Iterator<KillGoal> iter2 = killGoals.iterator();
			while (iter2.hasNext()) {
				KillGoal goal = iter2.next();
				BasicQuestState.KillGoalStatus status = new BasicQuestState.KillGoalStatus(goal);
				killgoalsStatus.add(status);
			}
			qs.setKillGoalsStatus(killgoalsStatus);

			// Now do the same for category kill Goals
			List<BasicQuestState.CategoryKillGoalStatus> categoryKillgoalsStatus = new LinkedList<BasicQuestState.CategoryKillGoalStatus>();

			// go through all the category kill goals and make a list of
			// CategoryKillGoalStatus objects
			Iterator<CategoryKillGoal> iter3 = categoryKillGoals.iterator();
			while (iter3.hasNext()) {
				CategoryKillGoal goal = iter3.next();
				BasicQuestState.CategoryKillGoalStatus status = new BasicQuestState.CategoryKillGoalStatus(goal);
				categoryKillgoalsStatus.add(status);
			}
			qs.setCategoryKillGoalsStatus(categoryKillgoalsStatus);

			// Now do the same for task Goals
			List<BasicQuestState.TaskGoalStatus> taskgoalsStatus = new LinkedList<BasicQuestState.TaskGoalStatus>();

			// go through all the task goals and make a list of
			// TaskGoalStatus objects
			Iterator<TaskGoal> iter4 = taskGoals.iterator();
			while (iter4.hasNext()) {
				TaskGoal goal = iter4.next();
				BasicQuestState.TaskGoalStatus status = new BasicQuestState.TaskGoalStatus(goal);
				taskgoalsStatus.add(status);
			}
			qs.setTaskGoalsStatus(taskgoalsStatus);

			// set the delivery item list
			qs.setDeliveryItems(deliveryItems);
            
            // Add progress/completion text
            qs.setQuestProgressText(progressText);
            qs.setQuestCompletionText(completionText);
            
            // Add the item rewards
            if (itemRewards != null)
                qs.setRewards(itemRewards);
            if (itemRewardsToChoose != null)
                qs.setRewardsToChoose(itemRewardsToChoose);
            
            qs.setXpRewards(xpRewards);
            qs.setRepeatable(repeatable);
            return qs;
        }
        finally {
            lock.unlock();
        }
    }

	
	public void AppyGoals(BasicQuestState qs) {
		lock.lock();
		try {
			List<BasicQuestState.CollectionGoalStatus> goalsStatus = new LinkedList<BasicQuestState.CollectionGoalStatus>();

			// go through all the collection goals and make a list of CollectionGoalStatus objects
			Iterator<CollectionGoal> iter = collectionGoals.iterator();
			while (iter.hasNext()) {
				CollectionGoal goal = iter.next();
				BasicQuestState.CollectionGoalStatus status = new BasicQuestState.CollectionGoalStatus(goal);
				goalsStatus.add(status);
			}
			qs.setGoalsStatus(goalsStatus);

			// Now do the same for kill Goals
			List<BasicQuestState.KillGoalStatus> killgoalsStatus = new LinkedList<BasicQuestState.KillGoalStatus>();

            // go through all the kill goals and make a list of KillGoalStatus objects
			Iterator<KillGoal> iter2 = killGoals.iterator();
			while (iter2.hasNext()) {
				KillGoal goal = iter2.next();
				BasicQuestState.KillGoalStatus status = new BasicQuestState.KillGoalStatus(goal);
				killgoalsStatus.add(status);
			}
			qs.setKillGoalsStatus(killgoalsStatus);

			// Now do the same for category kill Goals
			List<BasicQuestState.CategoryKillGoalStatus> categoryKillgoalsStatus = new LinkedList<BasicQuestState.CategoryKillGoalStatus>();

			// go through all the category kill goals and make a list of CategoryKillGoalStatus objects
			Iterator<CategoryKillGoal> iter3 = categoryKillGoals.iterator();
			while (iter3.hasNext()) {
				CategoryKillGoal goal = iter3.next();
				BasicQuestState.CategoryKillGoalStatus status = new BasicQuestState.CategoryKillGoalStatus(goal);
				categoryKillgoalsStatus.add(status);
			}
			qs.setCategoryKillGoalsStatus(categoryKillgoalsStatus);

			// Now do the same for task Goals
			List<BasicQuestState.TaskGoalStatus> taskgoalsStatus = new LinkedList<BasicQuestState.TaskGoalStatus>();

			// go through all the task goals and make a list of
			// TaskGoalStatus objects
			Iterator<TaskGoal> iter4 = taskGoals.iterator();
			while (iter4.hasNext()) {
				TaskGoal goal = iter4.next();
				BasicQuestState.TaskGoalStatus status = new BasicQuestState.TaskGoalStatus(goal);
				taskgoalsStatus.add(status);
			}
			qs.setTaskGoalsStatus(taskgoalsStatus);

			// set the delivery item list
			qs.setDeliveryItems(deliveryItems);
            
            // Add progress/completion text
            qs.setQuestProgressText(progressText);
            qs.setQuestCompletionText(completionText);
            
            // Add the item rewards
            if (itemRewards != null)
                qs.setRewards(itemRewards);
            if (itemRewardsToChoose != null)
                qs.setRewardsToChoose(itemRewardsToChoose);
            
            qs.setXpRewards(xpRewards);
            qs.setRepeatable(repeatable);
           // return qs;
        }
        finally {
            lock.unlock();
        }
    }

	
	
	
	
	
	
    public static class CollectionGoal implements Serializable {
        public CollectionGoal() {
        }

        public CollectionGoal(int grade, int templateID, String templateName, int num, int order) {
        	setTemplateID(templateID);
        	setTemplateName(templateName);
            setNum(num);
            setTier(grade);
            this.order = order;
        }

        public void setTemplateID(int templateID) {
            this.templateID = templateID;
        }
        public int getTemplateID() {
            return templateID;
        }
        
        public void setTemplateName(String templateName) {
            this.templateName = templateName;
        }
        public String getTemplateName() {
            return templateName;
        }

        public void setNum(int num) {
            this.num = num;
        }
        public int getNum() {
            return num;
        }
        
        public void setTier(int tier) {
            this.tier = tier;
        }
        public int getTier() {
            return tier;
        }

        public int templateID = -1;
        public String templateName = null;
        public int num = 0;
        public int tier = 0;
        public int order=0;
        
        private static final long serialVersionUID = 1L;
    }
    
    public static class KillGoal implements Serializable {
        public KillGoal() {
        }
        public KillGoal(int grade, int mobID, String mobName, int num,int  order) {
       //public KillGoal(int grade, int mobID, String mobName, int num, String mobs) {
        	setMobID(mobID);
            setMobName(mobName);
            setNum(num);
            setTier(grade);
            this.order = order;
        }

        public void setMobID(int mobID) {
            this.mobID = mobID;
        }
        public int getMobID() {
            return mobID;
        }
        
        public void setMobName(String mobName) {
            this.mobName = mobName;
        }
        public String getMobName() {
            return mobName;
        }

        public void setNum(int num) {
            this.num = num;
        }
        public int getNum() {
            return num;
        }
        
        public void setTier(int tier) {
            this.tier = tier;
        }
        public int getTier() {
            return tier;
        }

        public int mobID = -1;
        public String mobName = null;
        public int num = 0;
        public int tier = 0;
        public int order=0;

        private static final long serialVersionUID = 1L;
    }
    
    public static class CategoryKillGoal implements Serializable {
        public CategoryKillGoal() {
        }

        public CategoryKillGoal(int grade, String mobCategory, String name, int num,int order) {
        	setMobCategory(mobCategory);
        	setName(name);
            setNum(num);
            setTier(grade);
            this.order = order;
        }

        public void setMobCategory(String mobCategory) { this.mobCategory = mobCategory; }
        public String getMobCategory() { return mobCategory; }
        
        public void setName(String name) { this.name = name; }
        public String getName() { return name; }

        public void setNum(int num) { this.num = num; }
        public int getNum() { return num; }
        
        public void setTier(int tier) { this.tier = tier; }
        public int getTier() { return tier; }

        public String mobCategory = null;
        public String name = null;
        public int num = 0;
        public int tier = 0;
        public int order=0;
        
        private static final long serialVersionUID = 1L;
    }
    
    public static class TaskGoal implements Serializable {
        public TaskGoal() {
        }

        public TaskGoal(int grade, int taskID, String taskText, int num,int  order) {
        	setTaskID(taskID);
            setTaskText(taskText);
            setNum(num);
            setTier(grade);
            this.order = order;
        }

        public void setTaskID(int taskID) {
            this.taskID = taskID;
        }
        public int getTaskID() {
            return taskID;
        }

        public void setTaskText(String taskText) {
            this.taskText = taskText;
        }
        public String getTaskText() {
            return taskText;
        }
        
        public void setNum(int num) {
            this.num = num;
        }
        public int getNum() {
            return num;
        }
        
        public void setTier(int tier) {
            this.tier = tier;
        }
        public int getTier() {
            return tier;
        }

        public int taskID = -1;
        public String taskText = null;
        public int num = 0;
        public int tier = 0;
        public int order =0;
        
        private static final long serialVersionUID = 1L;
    }

    List<CollectionGoal> collectionGoals = new LinkedList<CollectionGoal>();
    List<KillGoal> killGoals = new LinkedList<KillGoal>();
    List<CategoryKillGoal> categoryKillGoals = new LinkedList<CategoryKillGoal>();
    List<TaskGoal> taskGoals = new LinkedList<TaskGoal>();
    List<Integer> deliveryItems = new LinkedList<Integer>();
    private static final long serialVersionUID = 1L;
}
