/**
 * 
 */
package pyro.mib.core;

import java.util.HashMap;

/**
 * @author sravan
 *
 */
public class SMSSender {
	
	public String processRequest(String http_method, String url, HashMap<String, String> params) {
		
		String source_addr = params.get("source_addr");
		String destination_addr = params.get("destination_addr");
		String message = params.get("message");
		
		ESMEService.sendSMS(source_addr, destination_addr, message);
		
		return "SUCCESS";
	}
	
}
