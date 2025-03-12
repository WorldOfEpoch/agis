package atavism.agis.plugins;

import java.io.IOException;
import java.util.ArrayList;
import atavism.server.engine.*;
import atavism.server.util.Log;
import atavism.msgsys.*;
import atavism.server.plugins.WorldManagerClient.ExtensionMessage;

public class SocialClient {

	private SocialClient() {
	}

	public static Namespace NAMESPACE = null;//Namespace.intern("social");

	public static boolean isOnBlockList(OID subject, OID target) {
		Log.debug("SocialClient.isOnBlockList: subject:" + subject + "  target:" + target);

		ExtensionMessage message = new ExtensionMessage(GroupClient.MSG_TYPE_IS_ON_BLOCK_LIST, "ao.IS_ON_BLOCK_LIST", subject);
		message.setProperty("targetOid", target);
		return Engine.getAgent().sendRPCReturnBoolean(message);

	}

	/**
	 * Sends the groupInviteByNameMessage which will attempt to invite the named
	 * member to the group.
	 */
	public static void InviteByName(OID oid, String name) throws IOException {
		InviteByNameMessage msg = new InviteByNameMessage(oid, name);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("SocialClient: InviteByNameMessage hit 2");
	}

	public static class InviteByNameMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;

		public InviteByNameMessage() {
			super();
			Log.debug("SocialClient: InviteByNameMessage hit 1");
		}

		public InviteByNameMessage(OID oid, String name) {
			super();
			setMsgType(MSG_TYPE_SOCIAL_INVITE_BY_NAME);
			setProperty("inviterOid", oid);
			setProperty("inviteeName", name);
			Log.debug("SocialClient: InviteByNameMessage hit 1");
		}
	}

	public static void ApplyBlackList(OID targetOid, ArrayList<OID> blockList) {
		BlockListMessage msg = new BlockListMessage(targetOid, blockList);
		Engine.getAgent().sendBroadcast(msg);
	}

	public static class BlockListMessage extends SubjectMessage {

		public BlockListMessage() {
			super(MSG_TYPE_BLOCK_LIST);
		}

		public BlockListMessage(OID targetOid, ArrayList<OID> blockList) {
			super(MSG_TYPE_BLOCK_LIST, targetOid);
			setBlockList(blockList);
		}

		public void setBlockList(ArrayList<OID> blockList) {
			this.blockList = blockList;
		}

		public ArrayList<OID> getBlockList() {
			return blockList;
		}

		protected ArrayList<OID> blockList;

		private static final long serialVersionUID = 1L;
	}

	public static final MessageType MSG_TYPE_SOCIAL_INVITE_BY_NAME = MessageType.intern("social.INVITE_FRIEND_BY_NAME");
	public static final MessageType MSG_TYPE_SOCIAL_PRIVATE_INVITE = MessageType.intern("social.PRIVATE_INVITE");
	public static final MessageType MSG_TYPE_SOCIAL_PRIVATE_INVITE_RESPONSE = MessageType.intern("social.PRIVATE_INVITE_RESPONSE");
	public static final MessageType MSG_TYPE_ADD_FRIEND = MessageType.intern("social.ADD_FRIEND");
	public static final MessageType MSG_TYPE_DEL_FRIEND = MessageType.intern("social.DEL_FRIEND");
	public static final MessageType MSG_TYPE_GET_FRIENDS = MessageType.intern("social.GET_FRIENDS");
	public static final String EXTMSG_SOCIAL_CANCEL_INVITE_REQUEST = "social.CANCEL_FRIENDS";
	public static final String EXTMSG_SOCIAL_INVITE_REQUEST = "social.INVITE_FRIENDS";
	public static final MessageType MSG_SOCIAL_INVITE_RESPONSE = MessageType.intern("social.INVITE_RESPONSE");
	public static final MessageType MSG_TYPE_BLOCK_LIST = MessageType.intern("ao.BLOCKLIST");
	public static final MessageType MSG_TYPE_IS_ON_BLOCK_LIST = MessageType.intern("ao.IS_ON_BLOCK_LIST");

}
