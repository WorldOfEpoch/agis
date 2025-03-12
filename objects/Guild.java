package atavism.agis.objects;

import java.io.*;
import java.util.*;

import atavism.agis.database.AccountDatabase;
import atavism.agis.plugins.AgisInventoryClient;
import atavism.agis.plugins.ChatClient;
import atavism.agis.plugins.CombatClient;
import atavism.agis.plugins.GroupClient;
import atavism.agis.plugins.GuildPlugin;
import atavism.agis.plugins.VoxelClient;
import atavism.agis.util.EventMessageHelper;
import atavism.server.util.*;
import atavism.server.engine.*;
import atavism.server.plugins.*;
import atavism.server.plugins.WorldManagerClient.TargetedExtensionMessage;

/**
 * The Guild class handles an instance of a guild. It keeps track of the guilds members and ranks.
 * @author Andrew Harrison
 *
 */
public class Guild implements Serializable {
	private int guildID;
	private String guildName;
	private int factionID;
	private ArrayList<GuildRank> ranks;
	private ArrayList<GuildMember> members;
	private String motd;
	private String omotd;
	private AccountDatabase aDB;
	private int level = 1;
	private OID warehouse = OID.fromLong(0L);
	private HashMap<Integer,Integer> items = new HashMap<Integer,Integer>();
	private ArrayList<Integer> itemIds = new ArrayList<Integer>();
	
	public Guild() {
		this.ranks = new ArrayList<GuildRank>();
		this.members = new ArrayList<GuildMember>();
	}
	
	public Guild(int guildID, String guildName, int factionID, ArrayList<String> rankNames, 
			ArrayList<ArrayList<String>> rankPermissions, OID leaderOid, ArrayList<OID> initiates) {
		this.guildID = guildID;
		this.guildName = guildName;
		this.factionID = factionID;
		this.ranks = new ArrayList<GuildRank>();
		for (int i = 0; i < rankNames.size(); i++) {
			GuildRank newRank = new GuildRank(i, rankNames.get(i), rankPermissions.get(i));
			ranks.add(newRank);
		}
		this.members = new ArrayList<GuildMember>();
		GuildMember leader = new GuildMember(leaderOid, 0);
		leader.status = MEMBER_STATUS_ONLINE;
		members.add(leader);
		for (int i = 0; i < initiates.size(); i++) {
			GuildMember initiate = new GuildMember(initiates.get(i), ranks.size()-1);
			members.add(initiate);
		}
		this.motd = "Welcome to the guild.";
		this.omotd = "Welcome to the guild.";
		
		// Send down guild info to each member
		for (GuildMember member : members) {
			sendGuildData(member.oid);
		}
	}
	
	public void addItem(int id, int itemId, int count) {
		Log.debug("Guild.addItem: items="+items+" ids="+itemIds);
		if (items.containsKey(itemId)) {
			items.put(itemId, items.get(itemId) + count);
		} else {
			items.put(itemId, count);
		}
		if (id > 0) {
			if (!itemIds.contains(itemId))
				itemIds.add(itemId);
		}
		Log.debug("Guild.addItem: after items="+items+" ids="+itemIds);

	}
	
	public OID getWarehouse() {
		return warehouse;
	}
	
	public void setWarehouse(OID oid) {
		warehouse = oid;
	}
	
	public HashMap<Integer,Integer> getItems(){
		return items;
	}
	
	
	/**
	 * Adds a rank from the data in the database. Should be called during the loading phase of the Guild.
	 * @param rankID
	 * @param rankName
	 * @param rankLevel
	 * @param permissions
	 */
	public void addRank(int rankID, String rankName, int rankLevel, ArrayList<String> permissions) {
		// Have to check that all previous ranks have been created, along with the new one to be added
		//while (ranks.size() <= rankLevel) {
			ranks.add(new GuildRank(rankLevel, rankName, permissions));
		//}
		// Update the rank data
		GuildRank rank = ranks.get(ranks.size()-1);
		rank.setID(rankID);
		rank.setRankName(rankName);
		rank.setPermissions(permissions);
	}
	
	/**
	 * Adds a member from the data in the database. Should be called during the loading phase of the Guild.
	 * @param memberID
	 * @param memberOid
	 * @param name
	 * @param rank
	 * @param level
	 * @param note
	 */
	public void addMember(int memberID, OID memberOid, String name, int rank, int level, String note) {
		GuildMember member = new GuildMember();
		member.setID(memberID);
		member.setOid(memberOid);
		member.setName(name);
		member.setRank(rank);
		member.setLevel(level);
		member.setNote(note);
		members.add(member);
	}
	
	public void addNewMember(OID memberOid, String name, int level) {
		GuildMember newMember = new GuildMember();
		newMember.setOid(memberOid);
		newMember.setName(name);
		newMember.setRank(ranks.size()-1);
		newMember.setLevel(level);
		newMember.setNote("");
		newMember.setStatus(1);
		members.add(newMember);
		if( aDB ==null )
			aDB = new AccountDatabase(false);
		int memberID = aDB.writeNewGuildMember(guildID, memberOid, name, newMember.getRank(), level, newMember.getNote());
		newMember.setID(memberID);
		EnginePlugin.setObjectProperty(memberOid, WorldManagerClient.NAMESPACE, GuildPlugin.GUILD_PROP, guildID);
		EnginePlugin.setObjectProperty(memberOid, WorldManagerClient.NAMESPACE, GuildPlugin.GUILD_NAME_PROP, guildName);
		// Send guild data to new member and a message to all other members that this player is now a member of the guild
		sendGuildData(memberOid);
		sendMemberData(newMember, "Add");
		// Also send out a chat message letting everyone know that a new member joined
		for(GuildMember member : members) {
			if (member.status != MEMBER_STATUS_OFFLINE) {
				EventMessageHelper.SendGeneralEvent(member.oid, EventMessageHelper.GUILD_MEMBER_JOINED, 0, name);
			}
		}
	}
	
	public GuildMember getGuildMember(OID memberOid) {
		for(GuildMember member : members) {
			//Log.debug("GUILD: comparing member oid: " + member.oid + " against: " + memberOid);
			if (member.oid.equals(memberOid)) {
				return member;
			}
		}
		return null;
	}
	
	public boolean memberLoggedIn(OID memberOid) {
		Log.debug("GUILD: memberLoggedIn ply Oid:" + memberOid);
		GuildMember member = getGuildMember(memberOid);
		if (member == null) {
			return false;
		} else if (member.status == MEMBER_STATUS_OFFLINE) {
			Log.debug("GUILD: got member Logged in: " + memberOid);
			member.status = MEMBER_STATUS_ONLINE;
			sendGuildData(memberOid);
			
			// Send message to all other members that this player is now online
			for (GuildMember gMember : members) {
				// Only send the message if the member is online
				if (gMember.status != MEMBER_STATUS_OFFLINE) 
					SendTargetedGuildMessage(gMember.oid, member.name + " is online");
			}
			sendMemberData(member, "Update");
		}else {
			Log.debug("GUILD: memberLoggedIn but is online in guild : " + memberOid);
		}
		return true;
	}
	
	
	public void memberLoggedOut(OID memberOid) {
		Log.debug("GUILD: memberLoggedOut ply Oid:" + memberOid);
		GuildMember member = getGuildMember(memberOid);
		if (member == null) {
			return;
		}
		
		member.status = MEMBER_STATUS_OFFLINE;
		for (GuildMember gMember : members) {
			// Only send the message if the member is online
			if (gMember.status != MEMBER_STATUS_OFFLINE) 
				SendTargetedGuildMessage(gMember.oid, member.name + " is offline");
		}
		// Send message to all other members that this player is now offline
		sendMemberData(member, "Update");
	}
	
	public void memberLevel(OID memberOid,int level) {
		GuildMember member = getGuildMember(memberOid);
		if (member == null) {
			return;
		}
		
		member.setLevel(level);
		// Send message to all other members that this player is now offline
		sendMemberData(member, "Update");
	}
	
	
	/**
	 * This is the core function of the Guild class. Whenever someone issues a guild command
	 * this function will take it, along with some form of data and then decide which 
	 * function should be run from there.
	 * @param commandType: a string identifying the type of command
	 * @param commandData: data needed to carry out the command
	 */
	public void handleCommand(OID oid, String commandType, OID targetOid, String commandData) {
		// If the command is to quit the guild, deal with it before doing the permission check
		if (commandType.equals("quit")) {
			processGuildQuit(oid);
			return;
		}
		
		// The permission check
		if (!hasPermission(oid, commandType)) {
			EventMessageHelper.SendErrorEvent(oid, EventMessageHelper.ERROR_INSUFFICIENT_PERMISSION, 0, "");
			return;
		}
		if( aDB ==null )
			aDB = new AccountDatabase(false);
		if (commandType.equals("invite")) {
			if (targetOid == null) {
				String targetName = commandData;
				try {
					targetOid = GroupClient.getPlayerByName(targetName);
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (targetOid == null) {
					return;
				}
			}
			
			if (targetOid != null) {
				inviteNewMember(targetOid, oid);
			}
		} else if (commandType.equals("addRank")) {
			String rankName = commandData;
			ArrayList<String> permissions = new ArrayList<String>();
			GuildRank rank =  ranks.get(ranks.size()-1);
			addRank(rank.rankLevel+1, rankName, permissions);
			sendRankData();
		}else if (commandType.equals("delRank")) {
			int rankId = Integer.parseInt(commandData);
			boolean res = delRank(rankId);
			if (!res)
				EventMessageHelper.SendErrorEvent(oid, EventMessageHelper.ERROR_GUILD_RANK_NO_DELETE_IS_MEMBER, 0, "");
			sendRankData();
		}else if (commandType.equals("editRank")) {
			String[] data = commandData.split(";");
			if (data.length >0) {
				int id = Integer.parseInt(data[0]);
				if (id > 0) {
					GuildRank rank =  getRankByLevel(id);
					if(rank!=null) {
//						Log.error("GUILD: Edit Rank "+commandData+" | "+rank.getID()+"|"+rank.rankLevel+"|"+rank.rankName+"|"+rank.permissions);
					
				//	if (data.length >3) {
						ArrayList<String> permissions = rank.getPermissions();;
						for (int i = 0; i < (data.length - 1) / 2; i++) {
							if (data[i * 2 + 1].equals("chat")) {
								int val = Integer.parseInt(data[i * 2 + 2]);
								if (permissions.contains(GuildPlugin.PERMISSION_CHAT)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_CHAT);
								} else if (val == 1)
									permissions.add(GuildPlugin.PERMISSION_CHAT);

							} else if (data[i * 2 + 1].equals("invite")) {
								int val = Integer.parseInt(data[i * 2 + 2]);
								if (permissions.contains(GuildPlugin.PERMISSION_INVITE)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_INVITE);
								} else if (val == 1)
									permissions.add(GuildPlugin.PERMISSION_INVITE);

							} else if (data[i * 2 + 1].equals("kick")) {
								int val = Integer.parseInt(data[i * 2 + 2]);
								if (permissions.contains(GuildPlugin.PERMISSION_KICK)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_KICK);
								} else if (val == 1)
									permissions.add(GuildPlugin.PERMISSION_KICK);

							} else if (data[i * 2 + 1].equals("promote")) {
								int val = Integer.parseInt(data[i * 2 + 2]);
								if (permissions.contains(GuildPlugin.PERMISSION_PROMOTE)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_PROMOTE);
								} else if (val == 1)
									permissions.add(GuildPlugin.PERMISSION_PROMOTE);

							} else if (data[i * 2 + 1].equals("demote")) {
								int val = Integer.parseInt(data[i * 2 + 2]);
								if (permissions.contains(GuildPlugin.PERMISSION_DEMOTE)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_DEMOTE);
								} else if (val == 1)
									permissions.add(GuildPlugin.PERMISSION_DEMOTE);

							} else if (data[i * 2 + 1].equals("setmotd")) {
								int val = Integer.parseInt(data[i * 2 + 2]);
								if (permissions.contains(GuildPlugin.PERMISSION_SET_MOTD)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_SET_MOTD);
								} else if (val == 1)
									permissions.add(GuildPlugin.PERMISSION_SET_MOTD);

							} else if (data[i * 2 + 1].equals("claimAdd")) {
								int val = Integer.parseInt(data[i * 2 + 2]);
								if (permissions.contains(GuildPlugin.PERMISSION_ADD_CLAIM)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_ADD_CLAIM);
								} else if (val == 1) {
									permissions.add(GuildPlugin.PERMISSION_ADD_CLAIM);
									if (!permissions.contains(GuildPlugin.PERMISSION_ACTION_CLAIM)) {
										permissions.add(GuildPlugin.PERMISSION_ACTION_CLAIM);
									}
								}
								if (permissions.contains(GuildPlugin.PERMISSION_EDIT_CLAIM)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_EDIT_CLAIM);
								}
								
								
								VoxelClient.SendClaimUpdate(guildID);
							} else if (data[i * 2 + 1].equals("claimEdit")) {
								int val = Integer.parseInt(data[i * 2 + 2]);
								if (permissions.contains(GuildPlugin.PERMISSION_EDIT_CLAIM)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_EDIT_CLAIM);
								} else if (val == 1) {
									permissions.add(GuildPlugin.PERMISSION_EDIT_CLAIM);
									if (!permissions.contains(GuildPlugin.PERMISSION_ADD_CLAIM)) {
										permissions.add(GuildPlugin.PERMISSION_ADD_CLAIM);
									}
									if (!permissions.contains(GuildPlugin.PERMISSION_ACTION_CLAIM)) {
										permissions.add(GuildPlugin.PERMISSION_ACTION_CLAIM);
									}
								}
								
								VoxelClient.SendClaimUpdate(guildID);
							} else if (data[i * 2 + 1].equals("claimAction")) {
								int val = Integer.parseInt(data[i * 2 + 2]);
								if (permissions.contains(GuildPlugin.PERMISSION_ACTION_CLAIM)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_ACTION_CLAIM);
								} else if (val == 1)
									permissions.add(GuildPlugin.PERMISSION_ACTION_CLAIM);
								VoxelClient.SendClaimUpdate(guildID);
							} else if (data[i * 2 + 1].equals("levelUp")) {
								int val = Integer.parseInt(data[i * 2 + 2]);
								if (permissions.contains(GuildPlugin.PERMISSION_LEVEL_UP)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_LEVEL_UP);
								} else if (val == 1)
									permissions.add(GuildPlugin.PERMISSION_LEVEL_UP);
							} else if (data[i * 2 + 1].equals("whAdd")) {
								int val = Integer.parseInt(data[i * 2 + 2]);
								if (permissions.contains(GuildPlugin.PERMISSION_WAREHOUSE_ADD)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_WAREHOUSE_ADD);
								} else if (val == 1)
									permissions.add(GuildPlugin.PERMISSION_WAREHOUSE_ADD);
								
								if (permissions.contains(GuildPlugin.PERMISSION_WAREHOUSE_GET)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_WAREHOUSE_GET);
								}
							} else if (data[i * 2 + 1].equals("whGet")) {
								int val = Integer.parseInt(data[i * 2 + 2]);
								if (permissions.contains(GuildPlugin.PERMISSION_WAREHOUSE_GET)) {
									if (val == 0)
										permissions.remove(GuildPlugin.PERMISSION_WAREHOUSE_GET);
								} else if (val == 1) {
									permissions.add(GuildPlugin.PERMISSION_WAREHOUSE_GET);
								}
								if (!permissions.contains(GuildPlugin.PERMISSION_WAREHOUSE_ADD)) {
									if (val == 1)
										permissions.add(GuildPlugin.PERMISSION_WAREHOUSE_ADD);
								} 
							}
						}
						rank.setPermissions(permissions);
						
//					}else 
						
						if (data[1].equals("rename"))
						rank.setRankName(data[2]);
//						Log.error("GUILD: Edit Rank "+commandData+" | "+rank.getID()+"|"+rank.rankName+"|"+rank.permissions);
						aDB.updateGuildRank(rank.getID(),rank.rankLevel, rank.getRankName(), rank.getPermissions());
					
				}
				
				sendRankData();
			}else {
				EventMessageHelper.SendErrorEvent(oid, EventMessageHelper.ERROR_INSUFFICIENT_PERMISSION, 0, "");
			}
				}
		} else if (commandType.equals("promote")) {
			promoteMember(targetOid, oid);
		} else if (commandType.equals("demote")) {
			demoteMember(targetOid, oid);
		} else if (commandType.equals("kick")) {
			kickMember(targetOid, oid);
		} else if (commandType.equals("levelUp")) {
			levelup(oid);
		} else if (commandType.equals("setmotd")) {
			motd = (String) commandData;
			aDB.updateGuild(this);
			// Send new MOTD down to all members
			sendMOTD();
		} else if (commandType.equals("chat")) {
			sendGuildChat(oid, commandData);
		}
	}
	
	/**
	 * This function checks the players rank to see if they can perform the
	 * requested command.
	 * @param oid: the identifier of the player who issued the command
	 * @param command: the command issued by the player
	 * @return
	 */
	public boolean hasPermission(OID oid, String command) {
		int rankNum = -1;
		for (int i = 0; i < members.size(); i++) {
			OID memberOid = members.get(i).oid;
			if (memberOid.equals(oid)) {
				rankNum = members.get(i).rank;
				break;
			}
		}
		
		if (rankNum == -1) {
			Log.error("GUILD: Command issuer has no rank in this guild.");
			return false;
		}
		
		GuildRank rank = ranks.get(rankNum);
		if (rank.permissions.contains(command))
		    return true;
		return false;
	}
	
	private void inviteNewMember(OID targetOid, OID inviterOid) {
		// First verify that  already in a Guild
		if(Log.loggingDebug)
		Log.debug("Guild inviteNewMember: "+guildName+" inviterOid="+inviterOid+" targetOid="+targetOid+" level="+level+" member limit="+(GuildPlugin.guildLevelSettings.containsKey(level)?GuildPlugin.guildLevelSettings.get(level).getMembersNum():"")+" members="+members.size());
		if(GuildPlugin.guildLevelSettings.containsKey(level) && GuildPlugin.guildLevelSettings.get(level).getMembersNum() <= members.size()) {
			EventMessageHelper.SendErrorEvent(inviterOid, EventMessageHelper.ERROR_GUILD_MEMBER_LIMIT, 0, "");
			return;
		}
		if( aDB ==null )
			aDB = new AccountDatabase(false);
		boolean isOnBlockList = aDB.isOnBlackList(inviterOid, targetOid);
		if (isOnBlockList) {
		//	AccountDatabase aDB = new AccountDatabase(false);
	    	String targetName = aDB.getCharacterNameByOid(targetOid);
			//String targetName = WorldManagerClient.getObjectInfo(targetOid).name;
			EventMessageHelper.SendErrorEvent(inviterOid, EventMessageHelper.ERROR_PLAYER_ON_BLOCK_LIST, 0, targetName);
			return;
		}else {
			isOnBlockList = aDB.isOnBlackList(targetOid, inviterOid);
			if (isOnBlockList) {
				String targetName = aDB.getCharacterNameByOid(targetOid);
				//String targetName = WorldManagerClient.getObjectInfo(targetOid).name;
				EventMessageHelper.SendErrorEvent(inviterOid, EventMessageHelper.ERROR_PLAYER_ON_YOUR_BLOCK_LIST, 0, targetName);
				return;
				}
		}
		// First verify the target is not already in a Guild
		
		int targetGuild = -1;
		try {
			targetGuild = (Integer) EnginePlugin.getObjectProperty(targetOid, WorldManagerClient.NAMESPACE, GuildPlugin.GUILD_PROP);
		} catch (NullPointerException e1) {
		}
		if (targetGuild > 0) {
			//Let the inviter know that the target is already in a Guild
			String targetName = aDB.getCharacterNameByOid(targetOid);
			//String targetName = WorldManagerClient.getObjectInfo(targetOid).name;
			EventMessageHelper.SendErrorEvent(inviterOid, EventMessageHelper.ERROR_ALREADY_IN_GUILD, 0, targetName);
			return;
		}
		
		// Send the invite request to the target
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "guildInvite");
    	props.put("guildName", guildName);
    	props.put("guildID", guildID);
    	String inviterName = WorldManagerClient.getObjectInfo(inviterOid).name;
    	props.put("inviterOid", inviterOid);
    	props.put("inviterName", inviterName);
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, targetOid, targetOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}
	
	private void processGuildQuit(OID memberOid) {
		GuildMember oldMember = getGuildMember(memberOid);
		if (oldMember == null) {
			return;
		}
		
		// First verify the quitter is not the guild leader
		if (oldMember.rank == 0) {
			EventMessageHelper.SendErrorEvent(memberOid, EventMessageHelper.GUILD_MASTER_NO_LEAVE, 0, "");
			return;
		}
		if( aDB ==null )
			aDB = new AccountDatabase(false);
		aDB.deleteGuildMember(oldMember.id);
		sendMemberData(oldMember, "Remove");
		members.remove(oldMember);
		sendRemovedFromGuildData(memberOid);
		
		EnginePlugin.setObjectProperty(memberOid, WorldManagerClient.NAMESPACE, GuildPlugin.GUILD_PROP, -1);
		EnginePlugin.setObjectProperty(memberOid, WorldManagerClient.NAMESPACE, GuildPlugin.GUILD_NAME_PROP, null);
		
		for(GuildMember member : members) {
			if (member.status != MEMBER_STATUS_OFFLINE) {
				EventMessageHelper.SendGeneralEvent(member.oid, EventMessageHelper.GUILD_MEMBER_LEFT, 0, oldMember.name);
			}
		}
	}
	
	public boolean processGuildDisband(OID memberOid) {
		GuildMember oldMember = getGuildMember(memberOid);
		if (oldMember == null) {
			return false;
		}
		
		// First verify the player requesting the disband is the leader
		if (oldMember.rank != 0) {
			EventMessageHelper.SendErrorEvent(memberOid, EventMessageHelper.ERROR_INSUFFICIENT_PERMISSION, 0, "");
			return false;
		}
		if( aDB ==null )
			aDB = new AccountDatabase(false);
		// Delete the guild from the database
		aDB.deleteGuild(guildID);
		// Update all properties for players that are currently logged in
		for(GuildMember member : members) {
			if (member.status != MEMBER_STATUS_OFFLINE) {
				sendRemovedFromGuildData(member.getOid());
				EnginePlugin.setObjectProperty(member.getOid(), WorldManagerClient.NAMESPACE, GuildPlugin.GUILD_PROP, -1);
				EnginePlugin.setObjectProperty(member.getOid(), WorldManagerClient.NAMESPACE, GuildPlugin.GUILD_NAME_PROP, null);
			}
			aDB.deleteGuildMember(member.id);
			
		}
		members.removeAll(getMembers());
		return true;
	}
	
	private void addRank(int rankID, String rankName, ArrayList<String> permissions) {
		GuildRank newRank = new GuildRank(rankID, rankName, permissions);
		if( aDB ==null )
			aDB = new AccountDatabase(false);
		int _rankId = aDB.writeNewGuildRank(guildID, rankID, rankName, permissions);
//		Log.error("GUILD: addRank "+rankName+"|"+rankID+"|"+_rankId+"|"+guildID);
		if (_rankId>0)
			newRank.setID(_rankId);
		ranks.add(newRank);
		// Send a new copy of rank data down to all members
		sendRankData();
	}
	private boolean delRank(int rankID) {
		
		GuildRank _rank = getRankByLevel(rankID);
		for(GuildMember member : members) {
			//Log.debug("GUILD: comparing member oid: " + member.oid + " against: " + memberOid);
			if (member.getRank()==rankID) {
				return false;
			}
		}
		if( aDB ==null )
			aDB = new AccountDatabase(false);
		if (_rank!=null)
		ranks.remove(_rank);
		Log.debug("GUILD: delRank "+_rank.getID());
		aDB.deleteGuildRank(_rank.getID());
		// Send a new copy of rank data down to all members
		sendRankData();
		return true;
	}

	public GuildRank getRank(int rankID) {
		for(GuildRank rank : ranks) {
			if (rank.getID()==rankID) {
				return rank;
			}
		}
		return null;
	}
	public GuildRank getRankByLevel(int rankID) {
		for(GuildRank rank : ranks) {
			if (rank.getRankLevel()==rankID) {
				return rank;
			}
		}
		return null;
	}
	
	public int getPromoteRank(int rankID) {
		int id = 0;
		for(GuildRank rank : ranks) {
			if (rank.getID() < rankID && rank.getID() > id) {
				id = rank.getID();
			}
		}
		return id;
	}
	
	public int getDemoteRank(int rankID) {
		int id = 99999;
		for(GuildRank rank : ranks) {
			if (rank.getID() > rankID && rank.getID() < id) {
				id = rank.getID();
			}
		}
		return id;
	}

	
	private void promoteMember(OID memberOid, OID promoterOid) {
		GuildMember member = getGuildMember(memberOid);
		if (member == null) {
			return;
		}
		if (member.rank == 0) {
			return;
		}
		if( aDB ==null )
			aDB = new AccountDatabase(false);
		GuildMember promoter = getGuildMember(promoterOid);
		if (member.rank <= promoter.rank) {
			EventMessageHelper.SendErrorEvent(promoterOid, EventMessageHelper.GUILD_NO_PROMOTE, 0, member.name);
					return;
		}
		if (member.rank == 1 & promoter.rank == 0) {
			Log.error("GUILD: "+promoter.name+" transfer Master Rank to "+member.name);
		//	member.rank = 0;
			promoter.rank = 1;
			aDB.updateGuildMember(promoter.id, promoter.name, promoter.rank, promoter.level, promoter.note);
			sendMemberData(promoter, "Update");
		}
		member.rank = member.rank - 1;
		aDB.updateGuildMember(member.id, member.name, member.rank, member.level, member.note);
		sendMemberData(member, "Update");
	}
	
	private void demoteMember(OID memberOid, OID demoterOid) {
		GuildMember member = getGuildMember(memberOid);
		if (member == null) {
			return;
		}
		if ((member.rank+1) == ranks.size()) {
			return;
		}
		GuildMember demoter = getGuildMember(demoterOid);
		if (member.rank <= demoter.rank) {
			EventMessageHelper.SendErrorEvent(demoterOid, EventMessageHelper.GUILD_NO_DEMOTE, 0, member.name);
			return;
		}
		member.rank = member.rank + 1;
		if( aDB ==null )
			aDB = new AccountDatabase(false);
		aDB.updateGuildMember(member.id, member.name, member.rank, member.level, member.note);
		sendMemberData(member, "Update");
	}

	private void kickMember(OID memberOid, OID kickerOid) {
		GuildMember oldMember = getGuildMember(memberOid);
		if (oldMember == null) {
			return;
		}
		// Check the member being kicked is lower rank than the kicker
		GuildMember kicker = getGuildMember(kickerOid);
		if (kicker.rank >= oldMember.rank) {
			EventMessageHelper.SendErrorEvent(kickerOid, EventMessageHelper.ERROR_INSUFFICIENT_PERMISSION, 0, "");
			return;
		}
		if( aDB ==null )
			aDB = new AccountDatabase(false);
		aDB.deleteGuildMember(oldMember.id);
		sendMemberData(oldMember, "Remove");
		members.remove(oldMember);
		sendRemovedFromGuildData(memberOid);
		
		if (oldMember.status == MEMBER_STATUS_ONLINE) {
			EnginePlugin.setObjectProperty(memberOid, WorldManagerClient.NAMESPACE, GuildPlugin.GUILD_PROP, -1);
			EnginePlugin.setObjectProperty(memberOid, WorldManagerClient.NAMESPACE, GuildPlugin.GUILD_NAME_PROP, null);
		}
		for(GuildMember member : members) {
			if (member.status != MEMBER_STATUS_OFFLINE) {
				EventMessageHelper.SendGeneralEvent(member.oid, EventMessageHelper.GUILD_MEMBER_LEFT, 0, oldMember.name);
			}
		}
	}
	
	void levelup(OID oid) {
		if(GuildPlugin.guildLevelSettings.containsKey(level)) {
			GuildLevelSettings gls = GuildPlugin.guildLevelSettings.get(level);
			boolean allitems = false;
			for(int itemId : gls.getItems().keySet()) {
				if(items.containsKey(itemId)) {
					if(items.get(itemId).equals(gls.getItems().get(itemId))) {
						allitems=true;
					}else {
						allitems=false;
						break;
					}
				}else {
					allitems=false;
					break;
				}
			}
			if(allitems) {
				level++;
				items.clear();
				if( aDB ==null )
					aDB = new AccountDatabase(false);
				aDB.updateGuild(this);
				sendResourceUpdate();
				GuildLevelSettings ngls = GuildPlugin.guildLevelSettings.get(level);
				if(ngls.getWarehouseNumSlot() > 0 && warehouse != null) {
					AgisInventoryClient.updateStorageSize(oid, warehouse, ngls.getWarehouseNumSlot() );
				}
				
			}
		}
	}
	public void handleAddResources(OID oid , int itemId, int count) {
		if(GuildPlugin.guildLevelSettings.containsKey(level)) {
			GuildLevelSettings gls = GuildPlugin.guildLevelSettings.get(level);
			if(gls.getItems().containsKey(itemId)) {
				int reqItems = gls.getItems().get(itemId);
				int itemCount = 0;
				if(items.containsKey(itemId)) {
					itemCount = items.get(itemId);
				}
				if(reqItems - itemCount < count)
					count = reqItems - itemCount;
				HashMap<Integer,Integer> itemsToAdd = new HashMap<Integer,Integer>();
				itemsToAdd.put(itemId, count);
				if(!AgisInventoryClient.checkComponents(oid, itemsToAdd)) {
					Log.debug("Guild: User doesn't have the required items in their Inventory!");
					
					Map<String, Serializable> props = new HashMap<String, Serializable>();
				/*	props.put("ext_msg_subtype", "CraftingMsg");
					props.put("PluginMessageType", "CraftingFailed");
					props.put("ErrorMsg", "You do not have the required Components to craft this Recipe!");
					TargetedExtensionMessage playerMsg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, playerOid, playerOid, false, props);
		
					Engine.getAgent().sendBroadcast(playerMsg);*/
					return;
				}else {
					AgisInventoryClient.removeGenericItems(oid, itemsToAdd, false);
					items.put(itemId, itemCount + count);
					if( aDB ==null )
						aDB = new AccountDatabase(false);
					aDB.updateGuild(this);
				}
				sendResourceUpdate();
			}
		}
		
	}
    
    public void sendGuildData(OID targetOid) {
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "sendGuildData");
    	props.put("guildName", guildName);
		props.put("motd", motd);
		props.put("level", level);
		String _items="";
		for(int i : items.keySet()) {
			_items+=i+"|"+items.get(i)+"|";
		}
		props.put("resource", _items);
		String _itemsReq="";
		if(GuildPlugin.guildLevelSettings.containsKey(level)) {
			GuildLevelSettings gls = GuildPlugin.guildLevelSettings.get(level);
			props.put("memberNum",gls.getMembersNum());
			for(int i : gls.items.keySet()) {
				_itemsReq+=i+"|"+gls.items.get(i)+"|";
			}
		}
		props.put("req", _itemsReq);
		props.put("omotd", omotd);
		props.put("numMembers", members.size());
		for (int i = 0; i < members.size(); i++) {
			GuildMember member = members.get(i);
			props.put("memberOid" + i, member.oid);
			props.put("memberName" + i, member.name);
			props.put("memberRank" + i, member.rank);
			props.put("memberLevel" + i, member.level);
			props.put("memberZone" + i, member.zone);
			props.put("memberNote" + i, member.note);
			props.put("memberStatus" + i, member.status);
		}
		props.put("numRanks", ranks.size());
		for (int i = 0; i < ranks.size(); i++) {
    		GuildRank rank = ranks.get(i);
    		props.put("rankLevel" + i, rank.getRankLevel());
    		props.put("rankName" + i, rank.rankName);
    		props.put("rankNumPermissions" + i, rank.permissions.size());
    		for (int j = 0; j < rank.permissions.size(); j++) {
    			props.put("rankNum" + i + "Permission" + j, rank.permissions.get(j));
    		}
		}
		Log.debug("GUILD: sending guild data message");
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, targetOid, targetOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}
    
	public void sendResourceUpdate() {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "guildResource");
		String _items = "";
		for (int i : items.keySet()) {
			_items += i + "|" + items.get(i) + "|";
		}
		props.put("resource", _items);
		String _itemsReq = "";
		if (GuildPlugin.guildLevelSettings.containsKey(level)) {
			GuildLevelSettings gls = GuildPlugin.guildLevelSettings.get(level);
			props.put("memberNum",gls.getMembersNum());
			for (int i : gls.items.keySet()) {
				_itemsReq += i + "|" + gls.items.get(i) + "|";
			}
		}
		props.put("req", _itemsReq);
		props.put("level", level);
		for (GuildMember member : members) {
			if (member.status != MEMBER_STATUS_OFFLINE) {
				TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, member.getOid(), member.getOid(), false, props);
				Engine.getAgent().sendBroadcast(msg);
			}
		}
		
	}
    
    
	public void sendRemovedFromGuildData(OID targetOid) {
		Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "sendGuildData");
		props.put("guildName", null);
		Log.debug("GUILD: sending removed from guild data message");
		TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, targetOid, targetOid, false, props);
		Engine.getAgent().sendBroadcast(msg);
	}
    
    public void sendMemberData(GuildMember updatedMember, String action) {
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "guildMemberUpdate");
		props.put("action", action);
		props.put("memberOid", updatedMember.oid);
		//props.put("memberID", member.memberID);
		props.put("memberName", updatedMember.name);
		props.put("memberRank", updatedMember.rank);
		props.put("memberLevel", updatedMember.level);
		props.put("memberZone", updatedMember.zone);
		props.put("memberNote", updatedMember.note);
		props.put("memberStatus", updatedMember.status);
		
		// Any Rank changes will need to be sent to all members of the Guild
		for (GuildMember member : members) {
			// Only send the message if the member is online
			
			if (action.equals("Add") && member.equals(updatedMember)) {
				continue;
			}
			if (member.status != MEMBER_STATUS_OFFLINE) {
				
				
				TargetedExtensionMessage msg = new TargetedExtensionMessage(
						WorldManagerClient.MSG_TYPE_EXTENSION, member.oid, 
						member.oid, false, props);
				Engine.getAgent().sendBroadcast(msg);
			}
		}
    }
    
    public void sendRankData() {
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "guildRankUpdate");
		// Easier just to send all ranks, it will never be much data
		props.put("numRanks", ranks.size());
		for (int i = 0; i < ranks.size(); i++) {
    		GuildRank rank = ranks.get(i);
    		props.put("rankLevel" + i, rank.getRankLevel());
    		props.put("rankName" + i, rank.rankName);
    		props.put("rankNumPermissions" + i, rank.permissions.size());
    		for (int j = 0; j < rank.permissions.size(); j++) {
    			props.put("rankNum" + i + "Permission" + j, rank.permissions.get(j));
    		}
		}
	    
		// Any Rank changes will need to be sent to all members of the Guild
		for (GuildMember member : members) {
			// Only send the message if the member is online
			if (member.status != MEMBER_STATUS_OFFLINE) {
				TargetedExtensionMessage msg = new TargetedExtensionMessage(
						WorldManagerClient.MSG_TYPE_EXTENSION, member.oid, 
						member.oid, false, props);
				Engine.getAgent().sendBroadcast(msg);
			}
		}
    }

    public void sendMOTD() {
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
    	props.put("ext_msg_subtype", "guildMotd");
    	props.put("motd", motd);
    	props.put("omotd", omotd);
	//	Log.error("send guild update motd "+motd);
		// Any Rank changes will need to be sent to all members of the Guild
		for (GuildMember member : members) {
			// Only send the message if the member is online
			if (member.status != MEMBER_STATUS_OFFLINE) {
				TargetedExtensionMessage msg = new TargetedExtensionMessage(
						WorldManagerClient.MSG_TYPE_EXTENSION, member.oid, 
						member.oid, false, props);
				Engine.getAgent().sendBroadcast(msg);
			}
		}
    }
    
    public void sendGuildChat(OID senderOid, String message) {
    	GuildMember sender = getGuildMember(senderOid);
    	
    	Map<String, Serializable> props = new HashMap<String, Serializable>();
		props.put("ext_msg_subtype", "guildChat");
		props.put("senderOid", senderOid);
		props.put("senderName", sender.name);
		props.put("message", message);
		
		// Any Rank changes will need to be sent to all members of the Guild
		for (GuildMember member : members) {
			// Only send the message if the member is online
			if (member.status != MEMBER_STATUS_OFFLINE) {
				SendTargetedGuildMessage(member.oid,message);
				TargetedExtensionMessage msg = new TargetedExtensionMessage(WorldManagerClient.MSG_TYPE_EXTENSION, member.oid, member.oid, false, props);
				Engine.getAgent().sendBroadcast(msg);
			}
		}
    }
	
	public class GuildRank implements Serializable {
		protected int id;
		protected int rankLevel;
		protected String rankName;
		protected ArrayList<String> permissions;
		
		public GuildRank(int rankID, String rankName, ArrayList<String> permissions) {
			this.rankLevel = rankID;
			this.rankName = rankName;
			this.permissions = permissions;
		}
		
		public void setID(int id) { this.id = id; }
		public int getID() { return id; }
		
		public void setRankLevel(int rankLevel) { this.rankLevel = rankLevel; }
		public int getRankLevel() { return rankLevel; }
		
		public void setRankName(String rankName) { this.rankName = rankName; }
		public String getRankName() { return rankName; }
		
		public void setPermissions(ArrayList<String> permissions) { this.permissions = permissions; }
		public ArrayList<String> getPermissions() { return permissions; }
		
		private static final long serialVersionUID = 1L;
	}
	
	  protected void SendTargetedGuildMessage(OID target, String message){
		  ChatClient.TargetedComReqMessage comMessage = new ChatClient.TargetedComReqMessage(target);
	        comMessage.setString(message);
	        comMessage.setChannel(5); //Guild channel
	        comMessage.setTarget(target);
	        Engine.getAgent().sendBroadcast(comMessage);
	    }
	
	public class GuildMember implements Serializable {
		protected int id;
		protected OID oid;
		protected String name;
		protected int rank;
		protected int level;
		protected String zone;
		protected String note;
		protected int status; // 0: Offline; 1: Online; 2: AFK?
		// This variable, when set to true, allows guild members to see
		// when the player is on another character on the same account
		//protected boolean altNotify;
		
		public GuildMember() {
		}
		
		public GuildMember(OID oid, int rank) {
			this.oid = oid;
			if( aDB ==null )
				aDB = new AccountDatabase(false);
			this.name = aDB.getCharacterNameByOid(oid);
			
			//this.name = WorldManagerClient.getObjectInfo(oid).name;
			this.rank = rank;
			this.zone = (String) EnginePlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "zone");
			this.note = "";
			this.status = MEMBER_STATUS_OFFLINE;
			try {
            	AgisStat lev = (AgisStat) EnginePlugin.getObjectProperty(oid, CombatClient.NAMESPACE, "level");
            	//Log.error("GUILD: ply level : "+lev.toString());
            	this.level = lev.getCurrentValue();
            } catch (NullPointerException e) {
            	Log.warn("GUILD PLUGIN: player " + oid + " does not have an level property");
            }  
			//this.level = (Integer)CombatPlugin.getObjectProperty(oid, WorldManagerClient.NAMESPACE, "level");
			//this.altNotify = false;
		}
		 
		public void setID(int id) { this.id = id; }
		public int getID() { return id; }
		
		public void setOid(OID oid) { this.oid = oid; }
		public OID getOid() { return oid; }
		
		public void setName(String name) { this.name = name; }
		public String getName() { return name; }
		
		public void setRank(int rank) { this.rank = rank; }
		public int getRank() { return rank; }
		
		public void setLevel(int level) { this.level = level; }
		public int getLevel() { return level; }
		
		public void setZone(String zone) { this.zone = zone; }
		public String getZone() { return zone; }
		
		public void setNote(String note) { this.note = note; }
		public String getNote() { return note; }
		
		public void setStatus(int status) { this.status = status; }
		public int getStatus() { return status; }
		
		private static final long serialVersionUID = 1L;
	}
	
	public void setGuildID(int guildID) { this.guildID = guildID; }
	public int getGuildID() { return guildID; }
	
	public void setGuildName(String guildName) { this.guildName = guildName; }
	public String getGuildName() { return guildName; }
	
	public void setFaction(int factionID) { this.factionID = factionID; }
	public int getFaction() { return factionID; }
	
	public void setRanks(ArrayList<GuildRank> ranks) { this.ranks = ranks; }
	public ArrayList<GuildRank> getRanks() { return ranks; }
	
	public void setMembers(ArrayList<GuildMember> members) { this.members = members; }
	public ArrayList<GuildMember> getMembers() { return members; }
	
	public void setMOTD(String motd) { this.motd = motd; }
	public String getMOTD() { return motd; }
	
	public void setOMOTD(String omotd) { this.omotd = omotd; }
	public String getOMOTD() { return omotd; }
	
	public void setLevel(int level) { this.level = level; }
	public int getLevel() { return level; }
	
	

	
	
	public void setAccountDatabase(AccountDatabase aDB) { this.aDB = aDB; }
	public AccountDatabase getAccountDatabase() { return aDB; }
	
	private final int MEMBER_STATUS_OFFLINE = 0;
	private final int MEMBER_STATUS_ONLINE = 1;
	private final int MEMBER_STATUS_AWAY = 2;
	
	private static final long serialVersionUID = 1L;
}