/**
 * 
 */
package pyro.mib.db;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

import pyro.common.db.ConnectionAdaptor;
import pyro.common.db.DataSource;
import pyro.common.db.DataSourceX;
import pyro.common.db.QueryBuilder;
import pyro.common.util.ConfigLoader;
import pyro.common.util.PyroLogger;
import pyro.mib.util.CVPSUtil;

/**
 * @author neeti
 * 
 */
public class CVPSDao {

	private static PyroLogger log = PyroLogger.getLogger(CVPSDao.class);

	private static String sms_table_name = ConfigLoader.getProperty("SMS_TABLE_NAME");

	private static DataSource dataSource;

	public static void init(String name, String tagsText, String configPath) {
		DataSourceX.init(tagsText, configPath);
		dataSource = DataSourceX.getDataSource(name);
	}

	// -------------------------

	public ArrayList getSMSCAccounts(String log_header, int mib_service_id) {

		ArrayList list = new ArrayList();

		ConnectionAdaptor conn = null;
		Statement stmt = null;

		try {

			log.info(log_header + " Fetching getSMSCAccounts ...");

			conn = new ConnectionAdaptor(dataSource);

			String select_query = "SELECT * FROM TBL_MIB_SMSCACCOUNT_MASTER WHERE STATUS = 1 AND MIB_SERVICE_ID = "+mib_service_id;

			log.info(log_header + " Query : " + select_query);

			stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(select_query);

			while (rs.next()) {

				HashMap map = new HashMap();

				map.put("MIB_ID", rs.getString("MIB_ID"));
				map.put("MIB_PASSWORD", rs.getString("MIB_PASSWORD"));
				map.put("SMSC_ID", rs.getString("SMSC_ID"));
				map.put("SMSC_PASSWORD", rs.getString("SMSC_PASSWORD"));
				map.put("SMSC_IP", rs.getString("SMSC_IP"));
				map.put("SMSC_PORT", rs.getInt("SMSC_PORT"));
				map.put("BIND_TYPE", rs.getInt("BIND_TYPE"));
				map.put("SMSC_SYSTEM_TYPE", rs.getString("SMSC_SYSTEM_TYPE"));
				map.put("DELIVER_SM_FLAG", rs.getInt("DELIVER_SM_FLAG"));

				list.add(map);
			}

		} catch (Exception e) {
			log.error(log_header + " Exception in getSMSCAccounts:" + e.getMessage());
			CVPSUtil.printStackTrace(e);
		} finally {
			conn.close();
		}

		return list;
	}

	public HashMap getAppProperties(String log_header) {

		HashMap map = new HashMap();

		ConnectionAdaptor conn = null;
		Statement stmt = null;

		try {

			log.info(log_header + " Fetching App Properties ...");

			conn = new ConnectionAdaptor(dataSource);

			ResultSet rs = null;

			String select_query = "SELECT * FROM TBL_APP_PROPERTIES";

			log.info(log_header + " Query : " + select_query);

			stmt = conn.createStatement();
			rs = stmt.executeQuery(select_query);

			while (rs.next()) {
				String key = rs.getString("PROPERTY_KEY");
				String value = rs.getString("PROPERTY_VALUE");
				map.put(key, value);
			}

		} catch (Exception e) {
			map = null;
			log.error(log_header + " Exception in getAppProperties:" + e.getMessage());
			CVPSUtil.printStackTrace(e);
		} finally {
			conn.close();
		}

		return map;
	}

	// -------------------------

	private static final String SYSDATE = "SYSDATE";

	private void setDate(QueryBuilder qb, String columnname, String value) {

		if (value == null) {
			qb.setString(columnname, value);
		} else if (value.equals(SYSDATE)) {
			qb.setDBFunction(columnname, value);
		} else if (value.contains(SYSDATE)) {
			qb.setDBFunction(columnname, value);
		} else {
			qb.setDBFunction(columnname, "TO_DATE('" + value + "', 'YYYY-MM-DD HH24:MI:SS')");
		}
	}

}
