/**
 * 
 */
package pyro.mib.util;

import pyro.common.service.PyroService;
import pyro.common.util.PyroLogger;
import pyro.mib.main.AppConfigLoader;

/**
 * @author neeti
 * 
 */
public class PingService implements PyroService, Runnable {

	private static PyroLogger log = PyroLogger.getLogger(PingService.class);
	
	private static String PING_URL = AppConfigLoader.getProperty("PING_URL");

	private Thread thread;

	@Override
	public String getName() {
		return null;
	}

	public void start() {
		// TODO Auto-generated method stub

		if (thread == null) {
			thread = new Thread(this);
			thread.start();
		}
	}

	@Override
	public void run() {
		while (true) {
			try {
				Thread.sleep(60000);
				
				CVPSUtil util = new CVPSUtil();
				util.hitURL(PING_URL, 10000);
				
			} catch (Exception e) {
				log.error("Exception in PingService:run ..." + e.getMessage());
				CVPSUtil.printStackTrace(e);
				try {
					Thread.sleep(60000);
				} catch (InterruptedException e1) {
				}
			}
		}
	}
}
