package pyro.mib.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
/**
 * @author neeti
 *
 */
public class Test {
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String msg = "%E0%A4%AA%E0%A5%8D%E0%A4%B0%E0%A4%BF%E0%A4%AF%20%E0%A4%97%E0%A5%8D%E0%A4%B0%E0%A4%BE%E0%A4%B9%E0%A4%95%2C%20%E0%A4%86%E0%A4%AA%E0%A4%95%E0%A5%8B<days>%E0%A4%95%E0%A5%80%20%E0%A4%B5%E0%A5%88%E0%A4%A7%E0%A4%A4%E0%A4%BE%20%E0%A4%95%E0%A5%87%20%E0%A4%B2%E0%A4%BF%E0%A4%8F<service>%E0%A4%95%E0%A5%80%20%E0%A4%B8%E0%A4%A6%E0%A4%B8%E0%A5%8D%E0%A4%AF%E0%A4%A4%E0%A4%BE%20%E0%A4%95%E0%A5%87%20%E0%A4%B2%E0%A4%BF%E0%A4%8F%20%E0%A4%85%E0%A4%A8%E0%A5%81%E0%A4%B0%E0%A5%8B%E0%A4%A7%20%E0%A4%95%E0%A4%BF%E0%A4%AF%E0%A4%BE%20%E0%A4%97%E0%A4%AF%E0%A4%BE%20%E0%A4%B9%E0%A5%88%E0%A5%A4%20%E0%A4%AF%E0%A4%A6%E0%A4%BF%20%E0%A4%86%E0%A4%AA%2057171%20%E0%A4%B8%E0%A5%87%20%E0%A4%8F%E0%A4%B8%E0%A4%8F%E0%A4%AE%E0%A4%8F%E0%A4%B8%20%E0%A4%AD%E0%A5%87%E0%A4%9C%E0%A5%87%E0%A4%82%20%E0%A4%AD%E0%A5%87%E0%A4%9C%E0%A5%87%E0%A4%82%20%E0%A4%B8%E0%A4%95%E0%A5%8D%E0%A4%B0%E0%A4%BF%E0%A4%AF%20%E0%A4%95%E0%A4%B0%E0%A4%A8%E0%A4%BE%20%E0%A4%9A%E0%A4%BE%E0%A4%B9%E0%A4%A4%E0%A5%87%20%E0%A4%B9%E0%A5%88%E0%A4%82";
		String finalmsg = msg.replace("<days>", URLEncoder.encode("3 दिनों ")).replace("<service>",
				URLEncoder.encode("नौकरियां अलर्ट"));
		System.out.println("::MSG::" + finalmsg);
		String url = "http://localhost:20040/jmib?request_type=send_sms_unicode&account_id=57171&password=57171&tid=501032919411764999&source_addr=57171&destination_addr=919410615541&message=$msg";
		// String encmsg = URLEncoder.encode(finalmsg);
		url = url.replace("$msg", finalmsg);
		System.out.println("::URL::" + url);
		hitURL(url, 10000);
	}

	static String hitURL(String url, int timeout) {
		String resp = "1:1:ERROR";
		long req_time = System.currentTimeMillis();
		try {
			System.out.println("URL:" + url);
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
			resp = response.toString();
			System.out.println("URL RESP: " + (System.currentTimeMillis() - req_time) + " ms : " + resp);
		} catch (Exception e) {
			System.out.println("Exception in hitURL : " + e.getMessage());
			e.printStackTrace();
			resp = "1:1:ERROR";
		}
		return resp;
	}
}
