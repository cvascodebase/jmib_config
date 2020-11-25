/**
 * 
 */
package pyro.mib.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.BasicConfigurator;
import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.CancelSm;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.DeliveryReceipt;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GSMSpecificFeature;
import org.jsmpp.bean.GeneralDataCoding;
import org.jsmpp.bean.MessageClass;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.OptionalParameters;
import org.jsmpp.bean.QuerySm;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.ReplaceSm;
import org.jsmpp.bean.SMSCDeliveryReceipt;
import org.jsmpp.bean.SubmitMulti;
import org.jsmpp.bean.SubmitMultiResult;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.QuerySmResult;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.ServerMessageReceiverListener;
import org.jsmpp.session.Session;
import org.jsmpp.session.SessionStateListener;
import org.jsmpp.util.AbsoluteTimeFormatter;
import org.jsmpp.util.InvalidDeliveryReceiptException;
import org.jsmpp.util.MessageId;
import org.jsmpp.util.TimeFormatter;

import pyro.common.db.pool.ObjectCache;
import pyro.common.util.PyroLogger;
import pyro.mib.csv.CSV;
import pyro.mib.main.AppConfigLoader;
import pyro.mib.util.CVPSUtil;

/**
 * @author neeti
 * 
 */
public class SMSCHandler {

	private static PyroLogger log = PyroLogger.getLogger(SMSCHandler.class);

	private String account_id;
	private String password;
	private String smsc_ip;
	private int smsc_port;
	private String smsc_system_id;
	private String smsc_password;
	private boolean deliverSM_notif_flag = true;

	private boolean smpp_connection = false;
	private Thread thread;
	private SMPPSession smpp_session;
	private SMSCConnector connector;
	private MessageReceiverListenerImpl listener;

	private RegisteredDelivery registeredDelivery = new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE);

	private static ObjectCache objectCache_SubmitSM_details = new ObjectCache(1000);
	private SessionCleaner sCleaner;

	private static String DELIVER_SM_URL = AppConfigLoader.getProperty("DELIVER_SM_URL");
	private static String INPUT_SMS_URL = AppConfigLoader.getProperty("INPUT_SMS_URL");
	private static String INPUT_SMS_RECONSENT_URL = AppConfigLoader.getProperty("INPUT_SMS_RECONSENT_URL");
	private static String SMSDD_URL = AppConfigLoader.getProperty("SMSDD_URL");
	private static String DATA_CODING_str = AppConfigLoader.getProperty("DATA_CODING");
	private static int DATA_CODING = Integer.parseInt(DATA_CODING_str);

	public boolean checkPassword(String pswd) {
		boolean flag = false;
		if (pswd != null) {
			if (pswd.equals(password)) {
				flag = true;
			}
		}
		return flag;
	}

	public void start(String smsc_ip, int smsc_port, String smsc_system_id, String smsc_password, String account_id,
			String password, boolean deliverSM_notif_flag, ObjectCache map_submit_smid) {

		this.smsc_ip = smsc_ip;
		this.smsc_port = smsc_port;
		this.smsc_system_id = smsc_system_id;
		this.smsc_password = smsc_password;
		this.account_id = account_id;
		this.password = password;
		this.deliverSM_notif_flag = deliverSM_notif_flag;

		objectCache_SubmitSM_details = map_submit_smid;

		if (thread == null) {
			connector = new SMSCConnector();
			thread = new Thread(connector);
			thread.start();
		}

		if (sCleaner == null) {
			sCleaner = new SessionCleaner();
			sCleaner.start();
		}
	}

	public void stop() {
		smpp_session.unbindAndClose();
		smpp_session = null;
	}

	private void bind() {

		try {
			if (smpp_session != null) {
				stop();
			}
		} catch (Exception e) {
			log.printStackTrace(e);
		}

		try {

			BasicConfigurator.configure();
			smpp_session = new SMPPSession();
			log.info(account_id + " Connecting to SMSC ...");
			smpp_session.setTransactionTimer(60000);
			smpp_session.connectAndBind(smsc_ip, smsc_port, new BindParameter(BindType.BIND_TRX, smsc_system_id,
					smsc_password, "cp", TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, null));
			listener = new MessageReceiverListenerImpl();
			smpp_session.setMessageReceiverListener(listener);
			smpp_connection = true;
			log.info(account_id + " Connected to SMSC ...");

			smpp_session.addSessionStateListener(new SessionStateListener() {
				@Override
				public void onStateChange(SessionState arg0, SessionState arg1, Object arg2) {
					log.info("State changed from " + arg1 + " to " + arg0 + " on " + arg2);
					smpp_connection = false;
				}

			});

		} catch (IOException e) {
			smpp_connection = false;
			log.error(account_id + " Failed connect and bind to host");
			log.printStackTrace(e);
		} catch (Exception e) {
			smpp_connection = false;
			log.error(account_id + " Failed connect and bind to host Ex");
			log.printStackTrace(e);
		} catch (Throwable e) {
			smpp_connection = false;
			log.error(account_id + " Failed connect and bind to host Ex");
		}
	}

	class SMSCConnector implements Runnable {

		@Override
		public void run() {

			try {
				Thread.sleep(500);
			} catch (InterruptedException e1) {
			}
			while (true) {
				try {
					if (!smpp_connection) {
						bind();
						Thread.sleep(60000);
					} else {
						Thread.sleep(20000);
					}
				} catch (Exception e) {
					log.error(account_id + " Exception in SMSCConnector:run ..." + e.getMessage());
					log.printStackTrace(e);
				}
			}
		}

	}

	class ServerMessageReceiverListenerX implements ServerMessageReceiverListener {

		@Override
		public DataSmResult onAcceptDataSm(DataSm arg0, Session arg1) throws ProcessRequestException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void onAcceptCancelSm(CancelSm arg0, SMPPServerSession arg1) throws ProcessRequestException {
			// TODO Auto-generated method stub

		}

		@Override
		public QuerySmResult onAcceptQuerySm(QuerySm arg0, SMPPServerSession arg1) throws ProcessRequestException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void onAcceptReplaceSm(ReplaceSm arg0, SMPPServerSession arg1) throws ProcessRequestException {
			// TODO Auto-generated method stub

		}

		@Override
		public SubmitMultiResult onAcceptSubmitMulti(SubmitMulti arg0, SMPPServerSession arg1)
				throws ProcessRequestException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public MessageId onAcceptSubmitSm(SubmitSm arg0, SMPPServerSession arg1) throws ProcessRequestException {
			// TODO Auto-generated method stub
			return null;
		}

	}

	class MessageReceiverListenerImpl implements MessageReceiverListener {

		@Override
		public DataSmResult onAcceptDataSm(DataSm dataSm, Session session) throws ProcessRequestException {

			return null;
		}

		@Override
		public void onAcceptAlertNotification(AlertNotification notification) {

		}

		@Override
		public void onAcceptDeliverSm(DeliverSm deliverSm) throws ProcessRequestException {

			if (MessageType.SMSC_DEL_RECEIPT.containedIn(deliverSm.getEsmClass())) {
				// delivery receipt
				try {
					DeliveryReceipt deliveryReceipt = deliverSm.getShortMessageAsDeliveryReceipt();
					// long id = Long.parseLong(deliveryReceipt.getId()) &
					// 0xffffffff;
					// String messageId = Long.toString(id, 16).toUpperCase();

					String submit_sm_id = deliveryReceipt.getId();
					String error = deliveryReceipt.getError();
					String deliver_text = deliveryReceipt.getText();

					log.info(account_id + " Receiving delivery receipt for submit_sm_id : " + submit_sm_id
							+ " : error : " + error + " : deliver_text : " + deliver_text + " : " + deliveryReceipt);

					if (deliverSM_notif_flag) {
						processDeliverSm(submit_sm_id, error, deliver_text);
					}

					// deliveryReceipt.getError()

				} catch (InvalidDeliveryReceiptException e) {
					log.error(account_id + " Failed getting delivery receipt");
					log.printStackTrace(e);
				}
			} else {
				// regular short message

				String destination_addr = deliverSm.getDestAddress();
				String source_addr = deliverSm.getSourceAddr();

				if (destination_addr.equals("59752")) {

					processSMSDD(source_addr, deliverSm.getShortMessage());
				} else {

					String message = new String(deliverSm.getShortMessage());

					log.info(account_id + " Receiving message : destination_addr : " + destination_addr
							+ " : source_addr : " + source_addr + " : message : " + message);

					processIncommingSMS(destination_addr, source_addr, message);
				}
			}
		}

	}

	private void processSMSDD(String msisdn, byte[] msgbytes) {
		char[] smsddChar = Hex.encodeHex(msgbytes);
		String smsddString = new String(smsddChar);

		log.info(account_id + " Receiving SMSDD request in Bytes : smsddString :==> " + smsddString + " msisdn : "
				+ msisdn);

		String url = SMSDD_URL.replace("{msisdn}", msisdn);

		String message_mfd = null;
		if (smsddString != null) {
			message_mfd = smsddString.replace(" ", "%20");
		}
		url = url.replace("{msg}", message_mfd);

		CVPSUtil util = new CVPSUtil();
		util.hitURL(url, 120000);
	}

	private void processIncommingSMS(String destination_addr, String msisdn, String message) {

		log.info(account_id + " : " + msisdn + " processing incomming SMS : destination_addr : " + destination_addr
				+ " : msisdn : " + msisdn + " : message : " + message);

		String url = INPUT_SMS_URL.replace("{msisdn}", msisdn);
		if(message!=null&&(message.equalsIgnoreCase("CONT"))){
			url = INPUT_SMS_RECONSENT_URL.replace("{msisdn}", msisdn);
		}
		url = url.replace("{shortcode}", destination_addr);
		String message_mfd = null;
		if (message != null) {
			message_mfd = message.replace(" ", "%20");
		}
		url = url.replace("{message}", message_mfd);

		CVPSUtil util = new CVPSUtil();
		util.hitURL(url, 120000);

	}

	private void processDeliverSm(String submit_sm_id, String error, String deliver_text) {

		if (submit_sm_id != null) {
			String sender_tid = (String) objectCache_SubmitSM_details.get(submit_sm_id);
			if (sender_tid != null) {

				String deliver_text2 = deliver_text;
				if (deliver_text2 == null) {
					deliver_text2 = "NULL";
				}

				log.info(sender_tid + " : " + account_id + " Sending deliverySM : submit_sm_id : " + submit_sm_id
						+ " : error : " + error + " : deliver_text : " + deliver_text);
				CSV.print(sender_tid + "," + submit_sm_id + "," + account_id + ",deliversm");

				String error_mfd = error;
				if (error_mfd != null) {
					if (error_mfd.length() > 3) {
						error_mfd = error_mfd.substring(0, 3);
					}
				}

				String resp = null;
				if (deliverSM_notif_flag) {
					String url = DELIVER_SM_URL.replace("{tid}", sender_tid);
					url = url.replace("{submitsm_id}", submit_sm_id);
					url = url.replace("{status}", deliver_text2.replace(" ", "%20"));
					url = url.replace("{reason}", error_mfd);
					url = url.replace("{failurereason}", error_mfd);
					CVPSUtil util = new CVPSUtil();
					resp = util.hitURL(url, 120000);
				}

				log.info(sender_tid + " : " + account_id + " deliverySM Responce : " + resp);

				objectCache_SubmitSM_details.remove(submit_sm_id);
			} else {
				log.info(account_id + " processDeliverSm : sender_tid is null : submit_sm_id : " + submit_sm_id
						+ " : error : " + error + " : deliver_text : " + deliver_text);

				sender_tid = "NULL";

				String deliver_text2 = deliver_text;
				if (deliver_text2 == null) {
					deliver_text2 = "NULL";
				}

				log.info(sender_tid + " : " + account_id + " Sending deliverySM : submit_sm_id : " + submit_sm_id
						+ " : error : " + error + " : deliver_text : " + deliver_text);
				CSV.print(sender_tid + "," + submit_sm_id + "," + account_id + ",deliversm");

				String error_mfd = error;
				if (error_mfd != null) {
					if (error_mfd.length() > 3) {
						error_mfd = error_mfd.substring(0, 3);
					}
				}

				String resp = null;
				if (deliverSM_notif_flag) {
					String url = DELIVER_SM_URL.replace("{tid}", sender_tid);
					url = url.replace("{submitsm_id}", submit_sm_id);
					url = url.replace("{status}", deliver_text2);
					url = url.replace("{reason}", error_mfd);
					url = url.replace("{failurereason}", error_mfd);
					CVPSUtil util = new CVPSUtil();
					resp = util.hitURL(url, 120000);
				}

				log.info(sender_tid + " : " + account_id + " deliverySM Responce : " + resp);
			}
		}

	}

	public static byte[] hexStringToByteArray(String s) {
		byte[] b = new byte[s.length() / 2];
		for (int i = 0; i < b.length; i++) {
			int index = i * 2;
			int v = Integer.parseInt(s.substring(index, index + 2), 16);
			b[i] = (byte) v;
		}
		return b;
	}

	public String sendSMS(String sender_tid, String source_addr, String destination_addr, String sms, String diff) {

		String resp = null;
		String error_msg = null;
		String submit_sm_id = null;

		try {

			// String dest_addr = "011-91-" + destination_addr;
			String dest_addr = destination_addr;

			log.info(sender_tid + " : " + account_id + " Sending SMS : source_addr : " + source_addr + ", dest_addr : "
					+ dest_addr + ", sms : " + sms);

			TimeFormatter timeFormatter = new AbsoluteTimeFormatter();

			long validity_time = System.currentTimeMillis();
			validity_time = validity_time + (24 * 3600000); // 4 hr
			Date vtime = new Date(validity_time);

			// String submit_sm_id = smpp_session.submitShortMessage("CMT",
			// TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN,
			// source_addr, TypeOfNumber.INTERNATIONAL,
			// NumberingPlanIndicator.UNKNOWN,
			// dest_addr, new ESMClass(), (byte) 0, (byte) 1,
			// timeFormatter.format(new Date()), timeFormatter.format(vtime),
			// registeredDelivery, (byte) 0, new GeneralDataCoding(), (byte) 0,
			// sms.getBytes());
			Random random = new Random();
			if (diff.equalsIgnoreCase("sms")) {
				OptionalParameters.deserialize((short)0x1401, "559890000034185".getBytes());
				OptionalParameter opt=OptionalParameters.deserialize((short)0x1401,"559890000034185".getBytes());
				OptionalParameter sarMsgRefNum = OptionalParameters.newSarMsgRefNum((short)random.nextInt());
			    OptionalParameter sarTotalSegments = OptionalParameters.newSarTotalSegments(3);

				log.info("Request required for sms " + diff + " MSISDN: " + dest_addr+"  OptionalParam-"+opt+" - Serialize"+ new String(opt.serialize()));
				submit_sm_id = smpp_session.submitShortMessage("CMT", TypeOfNumber.UNKNOWN,
						NumberingPlanIndicator.UNKNOWN, source_addr, TypeOfNumber.INTERNATIONAL,
						NumberingPlanIndicator.UNKNOWN, dest_addr, new ESMClass(), (byte) 0, (byte) 1, null,
						timeFormatter.format(vtime), registeredDelivery, (byte) 0, new GeneralDataCoding(), (byte) 0,
						sms.getBytes(),OptionalParameters.deserialize((short)0x1401, "559890000034185".getBytes()),
						sarMsgRefNum,sarTotalSegments);
			} else if (diff.equalsIgnoreCase("unicode")) {
				GeneralDataCoding dataCoding = new GeneralDataCoding(false, false, MessageClass.CLASS0,
						Alphabet.ALPHA_UCS2);
				log.info("Request required for sms unicode:: " + diff + " MSISDN: " + dest_addr);
				/*
				 * submit_sm_id = smpp_session.submitShortMessage("CMT",
				 * TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN,
				 * source_addr, TypeOfNumber.INTERNATIONAL,
				 * NumberingPlanIndicator.UNKNOWN, dest_addr, new ESMClass(),
				 * (byte) 0, (byte) 1, null, timeFormatter.format(vtime),
				 * registeredDelivery, (byte) 0, dataCoding, (byte) 0,
				 * sms.getBytes("UTF-16BE"));
				 */

				submit_sm_id = smpp_session.submitShortMessage("CMT", TypeOfNumber.UNKNOWN,
						NumberingPlanIndicator.UNKNOWN, source_addr, TypeOfNumber.INTERNATIONAL,
						NumberingPlanIndicator.UNKNOWN, dest_addr, new ESMClass(), (byte) 0, (byte) 1, null,
						timeFormatter.format(vtime), registeredDelivery, (byte) 0, dataCoding, (byte) 0, new byte[0],
						new OptionalParameter.COctetString((short) 0x0424, sms.getBytes("UTF-16BE")));
			} else if (diff.equalsIgnoreCase("smsdd_unicode")) {
				//New Configuration done by Akshay to change the GeneralData encoding type on 07-12-2017
				GeneralDataCoding dataCoding=null;
				byte[] byteData=null;
				if(AppConfigLoader.getVal("DATACODING").equalsIgnoreCase("ALPHA")){
					dataCoding = new GeneralDataCoding(false, false, MessageClass.CLASS1,Alphabet.ALPHA_UCS2);
					  byteData = sms.getBytes("UTF-16BE");
				}else if(AppConfigLoader.getVal("DATACODING").equalsIgnoreCase("EIGHTBIT")){
					dataCoding = new GeneralDataCoding(false, false, MessageClass.CLASS1,Alphabet.ALPHA_8_BIT);
					byteData = sms.getBytes("UTF-16BE");
				}else{
					dataCoding=new GeneralDataCoding(DATA_CODING);
					byteData = hexStringToByteArray(sms);
				}
				log.info(" :: CMTKEY === "+AppConfigLoader.getVal("CMTKEY"));
				//GeneralDataCoding dataCoding=new GeneralDataCoding(DATA_CODING);
				log.info("Request required for sms unicode:: " + diff + " MSISDN: " + dest_addr);
				/*submit_sm_id = smpp_session.submitShortMessage(AppConfigLoader.getVal("CMTKEY"), TypeOfNumber.UNKNOWN,
						NumberingPlanIndicator.UNKNOWN, source_addr, TypeOfNumber.INTERNATIONAL,
						NumberingPlanIndicator.UNKNOWN, dest_addr, new ESMClass(), (byte) 0, (byte) 1, null,
						timeFormatter.format(vtime), registeredDelivery, (byte) 0, dataCoding, (byte) 0,hexStringToByteArray(sms));*/
				
				submit_sm_id = smpp_session.submitShortMessage(AppConfigLoader.getVal("CMTKEY"), TypeOfNumber.UNKNOWN,
						NumberingPlanIndicator.UNKNOWN, source_addr, TypeOfNumber.INTERNATIONAL,
						NumberingPlanIndicator.UNKNOWN, dest_addr, new ESMClass(), (byte) 0, (byte) 1, null,
						timeFormatter.format(vtime), registeredDelivery, (byte) 0, dataCoding, (byte) 0, byteData);
				
			} else {
				log.info("Request required for smsdd " + diff + " MSISDN: " + dest_addr);
				// sms = new String(Base64.decodeBase64(sms.getBytes()));
				log.info("For smsdd: message is " + sms + " MSISDN: " + dest_addr);
				// submit_sm_id = sendSMSDD(sms, dest_addr, source_addr);
				submit_sm_id = smpp_session.submitShortMessage(null, TypeOfNumber.UNKNOWN, NumberingPlanIndicator.ISDN,
						source_addr, TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN, dest_addr, new ESMClass(),
						(byte) 127, (byte) 0, null, timeFormatter.format(vtime), registeredDelivery, (byte) 0,
						new GeneralDataCoding(DATA_CODING), (byte) 0, hexStringToByteArray(sms));
			}
			log.info("Message submitted, message_id is " + submit_sm_id + " tid is " + sender_tid);

			CSV.print(sender_tid + "," + submit_sm_id + "," + account_id + ",submitsm");

			if (deliverSM_notif_flag) {
				objectCache_SubmitSM_details.put(submit_sm_id, sender_tid);
			}

			resp = sender_tid + "::SUCCESS::" + submit_sm_id;

		} catch (PDUException e) {
			error_msg = e.getMessage();
			log.error(account_id + " Invalid PDU parameter : tid : " + sender_tid + " : msisdn : " + destination_addr
					+ " : " + e.getMessage());
			log.printStackTrace(e);
		} catch (ResponseTimeoutException e) {
			error_msg = e.getMessage();
			log.error(account_id + " Response timeout : tid : " + sender_tid + " : msisdn : " + destination_addr + " : "
					+ e.getMessage());
			log.printStackTrace(e);
		} catch (InvalidResponseException e) {
			// Invalid response
			error_msg = e.getMessage();
			log.error(account_id + " Receive invalid respose : tid : " + sender_tid + " : msisdn : " + destination_addr
					+ " : " + e.getMessage());
			log.printStackTrace(e);
		} catch (NegativeResponseException e) {
			// Receiving negative response (non-zero command_status)
			error_msg = e.getMessage();
			log.error(account_id + " Receive negative response : tid : " + sender_tid + " : msisdn : "
					+ destination_addr + " : " + e.getMessage());
			log.printStackTrace(e);

			String sub = null;
			int n1 = error_msg.indexOf("Negative response ");
			int n2 = error_msg.indexOf(" found");
			if ((n1 != -1) && (n2 != -1)) {
				if (n1 < n2) {
					sub = error_msg.substring(n1 + 18, n2);
				}
			}
			int mt_error_code = 96;
			try {
				mt_error_code = Integer.parseInt(sub);
				resp = sender_tid + "::FAILED::" + mt_error_code;
			} catch (Exception ex1) {
			}

		} catch (IOException e) {
			error_msg = e.getMessage();
			log.error(account_id + " IO error occur : tid : " + sender_tid + " : msisdn : " + destination_addr + " : "
					+ e.getMessage());
			log.printStackTrace(e);
		} catch (Exception e) {
			error_msg = e.getMessage();
			log.error(account_id + " Exception : tid : " + sender_tid + " : msisdn : " + destination_addr + " : "
					+ e.getMessage());
			log.printStackTrace(e);
		}

		if (resp == null) {
			// resp = sender_tid + "::FAILED::server_busy";
			resp = sender_tid + "::FAILED::69";
		}

		log.info(sender_tid + " : " + account_id + " SubmitSM Responce : " + resp + " : error_msg : " + error_msg);

		return resp;
	}

	@SuppressWarnings("unused")
	private String sendSMSDD(String sms, String dest_addr, String source_addr) throws PDUException,
			ResponseTimeoutException, InvalidResponseException, NegativeResponseException, IOException {

		Random random = new Random();
		int smslength = sms.length();
		int totalSegments;
		List strings = new ArrayList();
		int index = 0;
		String message_id = null;

		OptionalParameter sarMsgRefNum = OptionalParameters.newSarMsgRefNum((short) random.nextInt());
		OptionalParameter sarTotalSegments;
		OptionalParameter sarSegmentSeqnum;

		TimeFormatter timeFormatter = new AbsoluteTimeFormatter();

		long validity_time = System.currentTimeMillis();
		validity_time = validity_time + (24 * 3600000); // 4 hr
		Date vtime = new Date(validity_time);

		if (smslength > 250) {

			while (index < sms.length()) {
				strings.add(sms.substring(index, Math.min(index + 250, sms.length())));
				index += 250;
			}
			for (int i = 0; i < strings.size(); i++) {
				log.info("After sppliting " + strings.get(i));
				String str = (String) strings.get(i);

				sarSegmentSeqnum = OptionalParameters.newSarSegmentSeqnum(i + 1);
				totalSegments = strings.size();

				sarTotalSegments = OptionalParameters.newSarTotalSegments(totalSegments);

				message_id = smpp_session.submitShortMessage(null, TypeOfNumber.UNKNOWN, NumberingPlanIndicator.ISDN,
						source_addr, TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN, dest_addr, new ESMClass(),
						(byte) 127, (byte) 0, null, timeFormatter.format(vtime), registeredDelivery, (byte) 0,
						new GeneralDataCoding(16), (byte) 0, str.getBytes(), sarMsgRefNum, sarSegmentSeqnum,
						sarTotalSegments);

				log.info("Message submitted, for " + i + " message_id is " + message_id + " for msisdn " + dest_addr);

			}
		}

		return message_id;
	}

	class SessionCleaner extends Thread {
		@Override
		public void run() {
			try {
				Thread.sleep(60000 * 15); // 15 mins
			} catch (InterruptedException e1) {
			}
			try {
				int resp = objectCache_SubmitSM_details.removeOlderThan(60 * 60 * 25); // 25
																						// hours
				log.info(account_id + " SessionCleaner : " + resp);
			} catch (Exception e) {
			}
		}
	}
}
