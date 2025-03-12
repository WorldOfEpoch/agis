package atavism.agis.behaviors;

import java.util.*;

import atavism.agis.plugins.ChatClient;
import atavism.msgsys.*;
import atavism.server.engine.Behavior;
import atavism.server.engine.Engine;

public class ChatResponseBehavior extends Behavior {
    public void initialize() {
	MessageTypeFilter filter = new MessageTypeFilter();
	filter.addType(ChatClient.MSG_TYPE_COM);
	eventSub = Engine.getAgent().createSubscription(filter, this);
    }
    
    public void activate() {
    }

    public void deactivate() {
	if (eventSub != null) {
	    Engine.getAgent().removeSubscription(eventSub);
	    eventSub = null;
	}
        //return true;
    }

    public void handleMessage(Message msg, int flags) {
        if (msg instanceof ChatClient.ComMessage) {
        	ChatClient.ComMessage comMsg = (ChatClient.ComMessage)msg;
            String response = responses.get(comMsg.getString());
            if (response != null && MessageAgent.responseExpected(flags)) {
            	ChatClient.sendChatMsg(obj.getOid(), "", 1, response);
            }
        }
    }
        
    public void addChatResponse(String trigger, String response) {
	responses.put(trigger, response);
    }

    Map<String, String> responses = new HashMap<String, String>();
    Long eventSub = null;

    private static final long serialVersionUID = 1L;
}