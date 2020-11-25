/**
 * 
 */
package pyro.mib.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;

import pyro.common.db.pool.ObjectCache;
import pyro.common.http.PyroHttpServer;
import pyro.common.service.ServiceManager;
import pyro.common.util.PyroLogger;
import pyro.mib.core.MIBService;
import pyro.mib.db.CVPSDao;
import pyro.mib.util.PingService;

/**
 * @author neeti
 * 
 */
public class MIB_Server {

	private PyroHttpServer httpServer;
	private ServiceManager sm;
	private static PyroLogger log = AppConfigLoader.getLogger(MIB_Server.class);
	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

	public MIB_Server() {
	}

	private void startServer() {

		// Database intialization

		log.info("Intializing Database .......");

		String dbtag = AppConfigLoader.getProperty("DATABASE_TAG");
		String dbtags = AppConfigLoader.getProperty("DATABASE_TAG");
		String configPath = AppConfigLoader.getProperty("DATABASE_CONFIG_FILE");

		//CVPSDao.init(dbtag, dbtags, configPath);

		log.info("Database Intialization Completed .......");
		
		String csv_loading = AppConfigLoader.getProperty("CSV_LOADING");

		int mib_service_id = AppConfigLoader.getIntegerProperty("MIB_SERVICE_ID");
		//loadSMSCAccounts(mib_service_id, csv_loading);

		// Http Server Staring

		log.info("Intializing HTTP Server .......");

		int http_port = AppConfigLoader.getIntegerProperty("HTTP_PORT");
		httpServer = new PyroHttpServer(http_port);
		// httpServer.setContextPathFilter(new DefaultContextPathFilter());
		httpServer.setDefaultHttpRequestHandler(new DefaultHttpRequestHandler());
		httpServer.start();

		log.info("HTTP Server Intialization Completed .......");

		PingService pingService = new PingService();
		pingService.start();
		ServiceManager.addService("PingService", pingService);

	}

	private void loadSMSCAccounts(int mib_service_id, String csv_loading) {

		CVPSDao dao = new CVPSDao();

		ArrayList acc_list = dao.getSMSCAccounts("", mib_service_id);

		HashMap map = new HashMap();
		if(csv_loading!=null) {
			if(csv_loading.equalsIgnoreCase("YES")) {
				map = getCSVData();
			}
		}else {
			map = getCSVData();
		}

		for (int i = 0; i < acc_list.size(); i++) {

			HashMap smscacc_map = (HashMap) acc_list.get(i);

			String smsc_ip = (String) smscacc_map.get("SMSC_IP");
			int smsc_port = (Integer) smscacc_map.get("SMSC_PORT");
			String smsc_system_id = (String) smscacc_map.get("SMSC_ID");
			String smsc_password = (String) smscacc_map.get("SMSC_PASSWORD");
			String account_id = (String) smscacc_map.get("MIB_ID");
			String password = (String) smscacc_map.get("MIB_PASSWORD");
			int dsm_flag = (Integer) smscacc_map.get("DELIVER_SM_FLAG");
			boolean deliverSM_notif_flag = false;
			if (dsm_flag == 1) {
				deliverSM_notif_flag = true;
			}

			ObjectCache map_session = (ObjectCache) map.get(account_id);
			if (map_session == null) {
				map_session = new ObjectCache();
			}

			MIBService.addSmscAccount(smsc_ip, smsc_port, smsc_system_id, smsc_password, account_id, password, deliverSM_notif_flag, map_session);
		}

	}

	private HashMap getCSVData() {

		File folder = null;
		ArrayList list_files = new ArrayList();
		HashMap map = new HashMap();

		try {

			String folderPath = "logs/csv/";
			folder = new File(folderPath);
			Date date = new Date(System.currentTimeMillis() - 86400000);
			String dateStr = sdf.format(date);

			for (final File fileEntry : folder.listFiles()) {
				if (!fileEntry.isDirectory()) {
					String filename = fileEntry.getName();
					if (filename.equalsIgnoreCase("out.log")) {
						list_files.add(folderPath + filename);
					} else {
						if (filename.contains(dateStr)) {
							list_files.add(folderPath + filename);
						}
					}
				}
			}

			for (int i = 0; i < list_files.size(); i++) {
				String filename = (String) list_files.get(i);
				log.info("filename : " + filename);
				readFile(filename, map);
			}

		} catch (Exception e) {
			log.printStackTrace(e);
		}

		return map;
	}

	private void readFile(String filename, HashMap map) {

		BufferedReader br = null;

		try {

			String line;

			br = new BufferedReader(new FileReader(new File(filename)));

			while ((line = br.readLine()) != null) {

				String mdf_line = line;

				int index = mdf_line.lastIndexOf("-");
				mdf_line = mdf_line.substring(index + 1);
				mdf_line = mdf_line.trim();
				// log.info(mdf_line);

				String[] arr = mdf_line.split(",");
				String sid = arr[0];
				String tid = arr[1];
				String acc = arr[2];
				String type = arr[3];

				// log.info(sid+":"+tid+":"+acc);

				ObjectCache map_session = (ObjectCache) map.get(acc);

				if (map_session == null) {
					map_session = new ObjectCache();
					map.put(acc, map_session);
				}

				if (type.equalsIgnoreCase("deliversm")) {
					map_session.remove(tid);
				} else {
					map_session.put(tid, sid);
				}

			}

		} catch (IOException e) {
			log.printStackTrace(e);
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (IOException ex) {
			}
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		log.info("************** STARTING MIB SERVER ******************");
		try {
			log.info("Loading Properties ...");
			AppConfigLoader.loadPropertiesFile("cfg/mib.cfg");
			log.info("Intializing Properties ...");
			if (AppConfigLoader.init()) {
				log.info("Staring Server ...");
				MIB_Server server = new MIB_Server();
				server.startServer();
			}
		} catch (Exception e) {
			log.error("Exception in main:" + e.getMessage());
			e.printStackTrace();
		}
	}
}
