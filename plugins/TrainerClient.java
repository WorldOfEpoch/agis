package atavism.agis.plugins;

import atavism.msgsys.MessageType;
import atavism.server.engine.Namespace;

public class TrainerClient {
    //Message types used by the Trainer Plugin
    public static final MessageType MSG_TYPE_REQ_TRAINER_INFO = MessageType.intern("ao.REQ_TRAINER_INFO");
    public static final MessageType MSG_TYPE_REQ_SKILL_TRAINING = MessageType.intern("ao.REQ_SKILL_TRAINING");
    public static final MessageType MSG_TYPE_TRAINING_INFO = MessageType.intern("ao.TRAINING_INFO");
	
    public static Namespace NAMESPACE = null;
	
    private TrainerClient(){}
	
}
