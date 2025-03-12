package atavism.agis.plugins;

import atavism.msgsys.MessageType;
import atavism.server.engine.Namespace;

/**
 * This class is responsible for sending out messages associated with the Auction System. 
 * The majority (if not all) of the messages should be caught by the AuctionPlugin class.
 *
 */
public class AuctionClient {
	protected AuctionClient() {
		
	}
	
	
	public static final MessageType MSG_TYPE_AUCTION_CANCELL = MessageType.intern("auction.cancel");
	public static final MessageType MSG_TYPE_AUCTION_SELL = MessageType.intern("auction.createSell");
	public static final MessageType MSG_TYPE_AUCTION_BUY  = MessageType.intern("auction.buy");
	public static final MessageType MSG_TYPE_AUCTION_ORDER  = MessageType.intern("auction.order");
	public static final MessageType MSG_TYPE_AUCTION_LIST  = MessageType.intern("auction.list");
	public static final MessageType MSG_TYPE_AUCTION_OWNER_LIST  = MessageType.intern("auction.ownerList");
	public static final MessageType MSG_TYPE_AUCTION_SEARCH  = MessageType.intern("auction.search");
	public static final MessageType MSG_TYPE_AUCTION_GET_AUCTIONS_LIST = MessageType.intern("auction.getAuctionList");
	public static final MessageType MSG_TYPE_AUCTION_GET_FOR_GROUP = MessageType.intern("auction.getAuctionForGroup");
	public static final MessageType MSG_TYPE_AUCTION_TAKE_ALL = MessageType.intern("auction.takeAll");
	
	public static Namespace NAMESPACE = null;

}