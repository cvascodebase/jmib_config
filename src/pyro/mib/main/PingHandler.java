/**
 * 
 */
package pyro.mib.main;

import java.util.HashMap;

import pyro.common.util.PyroLogger;

/**
 * @author neeti
 * 
 */
public class PingHandler {

	private static PyroLogger log = PyroLogger.getLogger(PingHandler.class);

	public String processRequest(String http_method, String url, HashMap<String, String> params) {
		String resp = "SUCCESS";
		try {
			log.info("Ping test ...");
		} catch (Exception e) {
			resp = "PING_ERROR";
			log.error("Exception in SMS_InterfaceHandler : processRequest:" + e.getMessage());
			log.printStackTrace(e);
		}
		return resp;
	}
}
