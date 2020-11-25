/**
 * 
 */
package pyro.mib.core;

import java.util.HashMap;

import pyro.common.db.pool.ObjectCache;
import pyro.common.util.PyroLogger;

/**
 * @author sravan
 * 
 */
public class MIBService {

	private static PyroLogger log = PyroLogger.getLogger(MIBService.class);
	private static HashMap<String, SMSCHandler> map_accounts = new HashMap<String, SMSCHandler>();

	public static void init() {
	}

	public static void addSmscAccount(String smsc_ip, int smsc_port, String smsc_system_id, String smsc_password, String account_id, String password, boolean deliverSM_notif_flag, ObjectCache map) {
		
		log.info("Adding smsc account : "+smsc_ip+" : "+smsc_port+" : "+smsc_system_id+" : "+smsc_password+" : "+account_id+" : "+password+" : "+deliverSM_notif_flag+" : old_sessions : "+map.getSize());
		
		SMSCHandler smscHandler = new SMSCHandler();
		smscHandler.start(smsc_ip, smsc_port, smsc_system_id, smsc_password, account_id, password, deliverSM_notif_flag, map);
		map_accounts.put(account_id, smscHandler);
	}

	public String processRequest(String http_method, String url, HashMap<String, String> params, String diff) {

		String resp = null;
		
		String account_id = params.get("account_id");
		String password = params.get("password");
		String tid = params.get("tid");
		String source_addr = params.get("source_addr");
		String destination_addr = params.get("destination_addr");
		if(destination_addr==null) {
			destination_addr = params.get("address");
		}
		String sms = params.get("message");
		
		resp = tid+"::FAILED::NULL";

		SMSCHandler smscHandler = map_accounts.get(account_id);
		
		if (smscHandler != null) {
			if (smscHandler.checkPassword(password)) {
				resp = smscHandler.sendSMS(tid, source_addr, destination_addr, sms, diff);
			}else {
				log.info("SMSCHandler password failed for account_id "+account_id+" : password : "+password+" : tid : "+tid);
			}
		}else {
			log.info("SMSCHandler not fount for account_id "+account_id+" : password : "+password+" : tid : "+tid);
		}

		return resp;
	}

}
