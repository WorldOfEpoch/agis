package atavism.agis.core;

import atavism.server.engine.OID;
import atavism.server.util.*;
import java.util.*;
import java.util.concurrent.locks.*;

/**
 * the trade session stores the state and objects being traded
 */
public class TradeSession {
	/**
	 * the pair you pass in is going to set who is trader1 and trader2 in the
	 * trading list.
	 */
	public TradeSession(OID trader1, OID trader2) {
		this.trader1 = trader1;
		this.trader2 = trader2;
	}

	/**
	 * the trader is attempting to set the offer for one trader returns true if
	 * it succeeds
	 */
	public boolean setOffer(OID trader, List<OID> offer,
			HashMap<String, Integer> currencyOffer) {
		sessionLock.lock();
		try {
			if (trader.equals(trader1)) {
				offer1 = offer;
				currencyOffer1 = currencyOffer;
			} else if (trader.equals(trader2)) {
				offer2 = offer;
				currencyOffer2 = currencyOffer;
			} else {
				return false;
			}
			return true;
		} finally {
			sessionLock.unlock();
		}
	}

	/**
	 * update offer for trader. possibly reset accepted flag for other trader if
	 * offer changed
	 */
	public boolean updateOffer(OID trader, List<OID> offer,
			HashMap<String, Integer> currencyOffer, boolean accepted) {
		sessionLock.lock();
		try {
			if (!isTrader(trader)) {
				return false;
			}
			List<OID> oldOffer = getOffer(trader);
			HashMap<String, Integer> oldCurrencyOffer = getCurrencyOffer(trader);
			if (!oldOffer.equals(offer) || !oldCurrencyOffer.equals(currencyOffer)) {
				setAccepted(getPartnerOid(trader), false);
			}
			setOffer(trader, offer, currencyOffer);
			setAccepted(trader, accepted);
			return true;
		} finally {
			sessionLock.unlock();
		}
	}

	public OID getTrader1() {
		return trader1;
	}

	public OID getTrader2() {
		return trader2;
	}

	public boolean isTrader(OID trader) {
		if (trader.equals(trader1) || trader.equals(trader2)) {
			return true;
		} else {
			return false;
		}
	}

	public OID getPartnerOid(OID trader) {
		Log.debug("TradeSession.getPartnerOid: trader=" + trader + " trader1="
				+ trader1 + " trader2=" + trader2);
		if (trader.equals(trader1)) {
			return trader2;
		} else if (trader.equals(trader2)) {
			return trader1;
		} else {
			Log.error("TradeSession.getPartnerOid: trader=" + trader
					+ " not party to this session=" + this);
			throw new AORuntimeException("invalid trader");
		}
	}

	public List<OID> getOffer(OID trader) {
		sessionLock.lock();
		try {
			if (trader.equals(trader1)) {
				return offer1;
			} else if (trader.equals(trader2)) {
				return offer2;
			} else {
				Log.error("TradeSession.getOffer: trader=" + trader
						+ " not party to this session=" + this);
				throw new AORuntimeException("invalid trader");
			}
		} finally {
			sessionLock.unlock();
		}
	}
	
	public HashMap<String, Integer> getCurrencyOffer(OID trader) {
		sessionLock.lock();
		try {
			if (trader.equals(trader1)) {
				return currencyOffer1;
			} else if (trader.equals(trader2)) {
				return currencyOffer2;
			} else {
				Log.error("TradeSession.getOffer: trader=" + trader
						+ " not party to this session=" + this);
				throw new AORuntimeException("invalid trader");
			}
		} finally {
			sessionLock.unlock();
		}
	}

	public boolean getAccepted(OID trader) {
		sessionLock.lock();
		try {
			if (trader.equals(trader1)) {
				return accepted1;
			} else if (trader.equals(trader2)) {
				return accepted2;
			} else {
				Log.error("TradeSession.getAccepted: trader=" + trader
						+ " not party to this session=" + this);
				throw new AORuntimeException("invalid trader");
			}
		} finally {
			sessionLock.unlock();
		}
	}

	public void setAccepted(OID trader, boolean val) {
		sessionLock.lock();
		try {
			if (trader.equals(trader1)) {
				accepted1 = val;
			} else if (trader.equals(trader2)) {
				accepted2 = val;
			} else {
				Log.error("TradeSession.setAccepted: trader=" + trader
						+ " not party to this session=" + this);
				throw new AORuntimeException("invalid trader");
			}
		} finally {
			sessionLock.unlock();
		}
	}

	public boolean isComplete() {
		sessionLock.lock();
		try {
			return (accepted1 && accepted2);
		} finally {
			sessionLock.unlock();
		}
	}

	private OID trader1 = null;
	private OID trader2 = null;

	/**
	 * list of objects trader1 is giving
	 */
	private List<OID> offer1 = new LinkedList<OID>();

	/**
	 * list of objects trader2 is giving
	 */
	private List<OID> offer2 = new LinkedList<OID>();

	// Keys stored as strings so they can be sent to the client
	private HashMap<String, Integer> currencyOffer1 = new HashMap<String, Integer>();
	private HashMap<String, Integer> currencyOffer2 = new HashMap<String, Integer>();

	private boolean accepted1 = false;
	private boolean accepted2 = false;

	/**
	 * sometimes handlers need the lock - eg, they check for the state, do
	 * something, then set the new state
	 */
	public Lock getLock() {
		return sessionLock;
	}

	transient private Lock sessionLock = LockFactory
			.makeLock("TradeSessionLock");
}
