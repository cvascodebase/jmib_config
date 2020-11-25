/**
 * 
 */
package pyro.mib.main;

import pyro.common.util.ConfigLoader;
import pyro.common.util.PyroLogger;

/**
 * @author neeti
 *
 */
public class AppConfigLoader extends ConfigLoader {
	
	private static PyroLogger log = null;
	
	static {
		try {
			loadProperties("cfg/pyro_logger.cfg");
			log = PyroLogger.getLogger(AppConfigLoader.class);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	static boolean init() {
		// TODO Auto-generated method stub
		return true;
	}
	
	static void loadPropertiesFile(String filename) {
		try {
			loadProperties(filename);
		} catch (Exception ex) {
			ex.printStackTrace();
			log.info("Exception in AppConfigLoader:loadPropertiesFile:"+ex.getMessage());
		}
	}
	
	private static boolean check(String key) {

		String value = getProperty(key);
		if (value == null) {
			log.info("Exception - Value not present for the KEY : "+key);
			return false;
		}

		return false;
	}
	
	private static boolean check(String key, String type) {

		String value = getProperty(key);
		if (value == null) {
			log.info("Exception - Value not present for the KEY : "+key);
			return false;
		}

		if (type.equalsIgnoreCase("STRING")) {
			return true;
		} else if (type.equalsIgnoreCase("INT")) {
			try {
				int num = Integer.parseInt(value);
			} catch (NumberFormatException e) {
				log.info("Exception - NumberFormatException for the KEY : "+key);
				return false;
			}
			return true;
		}

		return false;
	}
	
	public static int getIntegerProperty(String key) {
		String value = properties.getProperty(key);
		return Integer.parseInt(value);
	}
	
	static PyroLogger getLogger(Class c) {
		return PyroLogger.getLogger(c);
	}
	public static String getVal(String key){
		return properties.getProperty(key);
	}
}
