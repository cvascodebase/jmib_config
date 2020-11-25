/**
 * 
 */
package pyro.mib.core;

import org.jsmpp.extra.SessionState;
import org.jsmpp.session.Session;
import org.jsmpp.session.SessionStateListener;

import pyro.common.util.PyroLogger;

/**
 * @author sravan
 * 
 */

public class StateChange implements SessionStateListener {

	private static PyroLogger log = PyroLogger.getLogger(StateChange.class);

	private Session s;

	@Override
	public void onStateChange(SessionState arg0, SessionState arg1, Object arg2) {
		// arg0 = new State
		// arg1 = old State

		if (!arg0.isBound() && arg1.isBound()) {
			log.info("State changed from " + arg1 + " to " + arg0 + " on " + arg2);
		}
	}

}

