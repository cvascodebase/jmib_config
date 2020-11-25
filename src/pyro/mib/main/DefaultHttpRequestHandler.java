/**
 * 
 */
package pyro.mib.main;

import java.util.HashMap;

import pyro.common.http.HttpRequestHandler;
import pyro.common.util.PyroLogger;
import pyro.mib.core.MIBService;

/**
 * @author neeti
 *
 */
public class DefaultHttpRequestHandler extends HttpRequestHandler {

	private static PyroLogger log = PyroLogger.getLogger(DefaultHttpRequestHandler.class);

	@Override
	public String processRequest(String http_method, String url, HashMap<String, String> params) {
		// TODO Auto-generated method stub
		
		log.info("URL params : "+params);
		
		String request_type = params.get("request_type");
		if(request_type!=null) {
			if(request_type.equals("send_sms")) {
				MIBService requestHandler = new MIBService();
				return requestHandler.processRequest(http_method, url, params, "sms");
			}else if (request_type.equals("send_smsdd")){
				MIBService requestHandler = new MIBService();
				return requestHandler.processRequest(http_method, url, params, "smsdd");
			}else if (request_type.equals("send_sms_unicode")){
				MIBService requestHandler = new MIBService();
				return requestHandler.processRequest(http_method, url, params, "unicode");
			}else if (request_type.equals("send_smsdd_unicode")){
				MIBService requestHandler = new MIBService();
				return requestHandler.processRequest(http_method, url, params, "smsdd_unicode");
			}else if(request_type.startsWith("ping")) {
				PingHandler requestHandler = new PingHandler();
				return requestHandler.processRequest(http_method, url, params);
			}
		}
		return "400:1:1";
	}
}