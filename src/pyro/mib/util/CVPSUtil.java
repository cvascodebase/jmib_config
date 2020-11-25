/**
 * 
 */
package pyro.mib.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;

import pyro.common.util.PyroLogger;

/**
 * @author neeti
 *
 */
public class CVPSUtil {

	private static PyroLogger log = PyroLogger.getLogger(CVPSUtil.class);
	
	public String hitURL(String url, int timeout) {

		String resp = "1:1:ERROR";

		long req_time = System.currentTimeMillis();

		try {

			log.info("URL:" + url);

			URL urlObj = new URL(url);
			URLConnection con = urlObj.openConnection();

			con.setDoOutput(true);

			if (timeout != -1) {
				con.setConnectTimeout(timeout);
				con.setReadTimeout(timeout);
			}

			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();

			// print result
			resp = response.toString();
			log.info("URL RESP: " + (System.currentTimeMillis() - req_time) + " ms : " + resp);

		} catch (Exception e) {
			log.error("Exception in hitURL : " + e.getMessage());
			e.printStackTrace();
			resp = "1:1:ERROR";
		}
		return resp;
	}
	
	public static void printStackTrace(Exception e) {
		StringWriter writer = new StringWriter();
		PrintWriter out = new PrintWriter(writer);
		e.printStackTrace(out);
		String output = writer.toString();
		log.error(output);
	}
	
}
