package atavism.agis.plugins;

import atavism.msgsys.*;
import atavism.server.engine.*;
import atavism.server.messages.*;
import atavism.server.network.AOByteBuffer;
import atavism.server.util.*;

/**
 * client for sending/getting messages to the ChatPlugin
 */
public class ChatClient {

	/**
	 * an object wants to send a chat msg - msg gets sent to world manager
	 * non-blocking call
	 */
	public static void sendChatMsg(OID objId, String chatterName, int channelId, String text) {
		ComReqMessage msg = new ComReqMessage(objId, chatterName, channelId, text);
		Engine.getAgent().sendBroadcast(msg);
	}

	/**
	 * sends message to the object (this is not a request message)
	 */
	public static void sendObjChatMsg(OID objOid, int channelId, String text) {
		sendObjChatMsg(objOid, "", channelId, text);
	}

	/**
	 * sends message to the object (this is not a request message)
	 */
	public static void sendObjChatMsg(OID objOid, String senderName, int channelId, String text) {
		ChatClient.TargetedComReqMessage comMsg = new ChatClient.TargetedComReqMessage(objOid, objOid, senderName, channelId, text);
		Engine.getAgent().sendBroadcast(comMsg);
	}

	/**
	 * sends system chat message to all players (this is not a request message)
	 */
	public static void sendSysChatMsg(String text) {
		ChatClient.SysChatMessage sysMsg = new ChatClient.SysChatMessage(text);
		Engine.getAgent().sendBroadcast(sysMsg);
	}

	// /////////////////////////////////////////////////////////
	//
	// begin messages
	//
	// /////////////////////////////////////////////////////////

	/**
	 * notification from proxy/mobserver regarding chat msg MSG_OID is person saying
	 * something
	 */
	public static class ComReqMessage extends SubjectMessage {
		// for bridgemessage

		public ComReqMessage() {
			super();
		}

		public ComReqMessage(OID objOid, String chatterName, int channel, String msgString) {
			super(MSG_TYPE_COM_REQ, objOid);
			setChatterName(chatterName);
			setChannel(channel);
			setString(msgString);
		}

		public String getChatterName() {
			return chatterName;
		}

		public void setChatterName(String chatterName) {
			this.chatterName = chatterName;
		}

		public String getString() {
			return msgString;
		}

		public void setString(String msgString) {
			this.msgString = msgString;
		}

		public int getChannel() {
			return channel;
		}

		public void setChannel(int channel) {
			this.channel = channel;
		}

		private String chatterName;
		int channel = -1;
		private String msgString;

		private static final long serialVersionUID = 1L;
	}

	/**
	 * notification from worldserver saying a mob said something MSG_OID is person
	 * saying something
	 */
	public static class ComMessage extends SubjectMessage implements ClientMessage {

		public ComMessage() {
			super(MSG_TYPE_COM);
		}
		public ComMessage(OID objOid) {
			super(MSG_TYPE_COM,objOid);
		}

		public ComMessage(OID objOid, String chatterName, int channel, String msgString) {
			super(MSG_TYPE_COM, objOid);
			setChatterName(chatterName);
			setChannel(channel);
			setString(msgString);
		}

		public String toString() {
			return "[ComMessage: objOid=" + getSubject() + ", channel=" + getChannel() + ", msg=" + getString() + "]";
		}

		public String getChatterName() {
			return chatterName;
		}

		public void setChatterName(String chatterName) {
			this.chatterName = chatterName;
		}

		public String getString() {
			return msgString;
		}

		public void setString(String msgString) {
			this.msgString = msgString;
		}

		public int getChannel() {
			return channel;
		}

		public void setChannel(int channel) {
			this.channel = channel;
		}

		public AOByteBuffer toBuffer() {
			AOByteBuffer buf = new AOByteBuffer(400);
			buf.putOID(this.getSubject());
			buf.putInt(3);
			buf.putString(this.getChatterName());
			buf.putInt(this.getChannel());
			buf.putString(this.getString());
			buf.flip();
			return buf;
		}

		public void fromBuffer(AOByteBuffer buf) {
			/* OID oid = */ buf.getOID();
			int msgNumber = buf.getInt();
			if (msgNumber != 3) {
				Log.error("ComMessage.fromBuffer: msgNumber " + msgNumber + " is not 3");
				return;
			}
			channel = buf.getInt();
			msgString = buf.getString();
		}

		private String chatterName;
		int channel = -1;
		private String msgString;

		private static final long serialVersionUID = 1L;
	}
	/**
	 * notification from worldserver saying a mob said something MSG_OID is person
	 * saying something
	 */
	public static class TargetedComReqMessage extends TargetMessage {

		public TargetedComReqMessage() {
			super(MSG_TYPE_COM_TARGET_REQ);
		}
		public TargetedComReqMessage(OID targetOid) {
			super(MSG_TYPE_COM_TARGET_REQ,targetOid);
		}

		public TargetedComReqMessage(OID targetOid, OID subjectOid, String chatterName, int channel, String msgString) {
			super(MSG_TYPE_COM_TARGET_REQ, targetOid, subjectOid);
			setChatterName(chatterName);
			setChannel(channel);
			setString(msgString);
		}

		public String toString() {
			return "[ComMessage: targetOid=" + getTarget() + ", subjectOid=" + getSubject() + ", channel=" + getChannel() + ", msg=" + getString() + "]";
		}

		public String getChatterName() {
			return chatterName;
		}

		public void setChatterName(String chatterName) {
			this.chatterName = chatterName;
		}

		public String getString() {
			return msgString;
		}

		public void setString(String msgString) {
			this.msgString = msgString;
		}

		public int getChannel() {
			return channel;
		}

		public void setChannel(int channel) {
			this.channel = channel;
		}

		
		private String chatterName;
		int channel = -1;
		private String msgString;

		private static final long serialVersionUID = 1L;
	}

	/**
	 * notification from worldserver saying a mob said something MSG_OID is person
	 * saying something
	 */
	public static class TargetedComMessage extends TargetMessage implements ClientMessage {

		public TargetedComMessage() {
			super(MSG_TYPE_COM);
		}

		public TargetedComMessage(OID targetOid, OID subjectOid, String chatterName, int channel, String msgString) {
			super(MSG_TYPE_COM, targetOid, subjectOid);
			setChatterName(chatterName);
			setChannel(channel);
			setString(msgString);
		}

		public String toString() {
			return "[ComMessage: targetOid=" + getTarget() + ", subjectOid=" + getSubject() + ", channel=" + getChannel() + ", msg=" + getString() + "]";
		}

		public String getChatterName() {
			return chatterName;
		}

		public void setChatterName(String chatterName) {
			this.chatterName = chatterName;
		}

		public String getString() {
			return msgString;
		}

		public void setString(String msgString) {
			this.msgString = msgString;
		}

		public int getChannel() {
			return channel;
		}

		public void setChannel(int channel) {
			this.channel = channel;
		}

		public AOByteBuffer toBuffer() {
			AOByteBuffer buf = new AOByteBuffer(400);
			buf.putOID(this.getSubject());
			buf.putInt(3);
			buf.putString(this.getChatterName());
			buf.putInt(this.getChannel());
			buf.putString(this.getString());
			buf.flip();
			return buf;
		}

		public void fromBuffer(AOByteBuffer buf) {
			subject = buf.getOID();
			int msgNumber = buf.getInt();
			if (msgNumber != 3) {
				Log.error("ComMessage.fromBuffer: msgNumber " + msgNumber + " is not 3");
				return;
			}
			chatterName = buf.getString();
			channel = buf.getInt();
			msgString = buf.getString();
		}

		private String chatterName;
		int channel = -1;
		private String msgString;

		private static final long serialVersionUID = 1L;
	}

	/**
	 * system message to be displayed in chat window for all players
	 */
	public static class SysChatMessage extends Message implements ClientMessage {

		public SysChatMessage() {
			super(MSG_TYPE_SYS_CHAT);
		}

		public SysChatMessage(String msgString) {
			super(MSG_TYPE_SYS_CHAT);
			setString(msgString);
		}

		public String getString() {
			return msgString;
		}

		public void setString(String msgString) {
			this.msgString = msgString;
		}

		public AOByteBuffer toBuffer() {
			AOByteBuffer buf = new AOByteBuffer(200);
			buf.putOID(null);
			buf.putInt(3);
			buf.putInt(0);
			buf.putString(this.getString());
			buf.flip();
			return buf;
		}

		private String msgString;

		private static final long serialVersionUID = 1L;
	}

	public static MessageType MSG_TYPE_COM_REQ = MessageType.intern("ao.COM_REQ");
	
	public static MessageType MSG_TYPE_COM_TARGET_REQ = MessageType.intern("ao.COM_TARGET_REQ");

	public static MessageType MSG_TYPE_COM = MessageType.intern("ao.COM");

	public static MessageType MSG_TYPE_SYS_CHAT = MessageType.intern("ao.SYS_CHAT");

}
