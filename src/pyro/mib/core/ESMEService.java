/**
 * 
 */
package pyro.mib.core;

import java.io.IOException;
import java.util.Date;

import org.apache.log4j.BasicConfigurator;
import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.DeliveryReceipt;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GeneralDataCoding;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.SMSCDeliveryReceipt;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.jsmpp.util.AbsoluteTimeFormatter;
import org.jsmpp.util.InvalidDeliveryReceiptException;
import org.jsmpp.util.TimeFormatter;

import pyro.common.util.PyroLogger;

/**
 * @author sravan
 * 
 */
public class ESMEService {

	private static PyroLogger log = PyroLogger.getLogger(ESMEService.class);

	private String smsc_ip;
	private int smsc_port;
	private String system_id;
	private String password;
	private SMPPSession smpp_session;
	private boolean smpp_connection = false;
	private Thread thread;
	private SMSCConnector connector;
	private MessageReceiverListenerImpl listener;
	private static ESMEService service;
	private static TimeFormatter timeFormatter = new AbsoluteTimeFormatter();
	private static RegisteredDelivery registeredDelivery = new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE);

	private ESMEService() {
	}

	public static ESMEService getService() {
		if (service == null) {
			service = new ESMEService();
		}
		return service;
	}

	public void start(String smsc_ip, int smsc_port, String system_id, String password) {
		
		this.smsc_ip = smsc_ip;
		this.smsc_port = smsc_port;
		this.system_id = system_id;
		this.password = password;
		
		if (thread == null) {
			connector = new SMSCConnector();
			thread = new Thread(connector);
			thread.start();
		}
	}

	public void stop() {
		smpp_session.unbindAndClose();
		smpp_session = null;
	}
	
	private void bind() {
		try {
			BasicConfigurator.configure();
			smpp_session = new SMPPSession();
			log.info("Connecting to SMSC ...");
			smpp_session.connectAndBind(smsc_ip, smsc_port, new BindParameter(BindType.BIND_TRX, system_id, password, "cp", TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, null));
			listener = new MessageReceiverListenerImpl();
			smpp_session.setMessageReceiverListener(listener);
			smpp_connection = true;
			log.info("Connected to SMSC ...");
		} catch (IOException e) {
			smpp_connection = false;
			log.error("Failed connect and bind to host");
			e.printStackTrace();
		}
	}
	
	public static void sendSMS(String source_addr, String destination_addr, String sms) {
		if(service!=null) {
			service.sendMessage(source_addr, destination_addr, sms);
		}
	}
	
	private void sendMessage(String source_addr, String destination_addr, String sms) {
		try {
			
			String dest_addr = "011-91-"+destination_addr;
			dest_addr = destination_addr;
			
			log.info("Sending SMS source_addr : " + source_addr+ ", dest_addr : "+dest_addr+", sms : "+sms);
			
			String messageId = smpp_session.submitShortMessage("CMT", TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, source_addr, TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.UNKNOWN, dest_addr, new ESMClass(), (byte) 0, (byte) 1, timeFormatter.format(new Date()), null, registeredDelivery, (byte) 0,
					new GeneralDataCoding(), (byte) 0, sms.getBytes());
			log.info("Message submitted, message_id is " + messageId);

		} catch (PDUException e) {
			log.error("Invalid PDU parameter : "+e.getMessage());
			log.printStackTrace(e);
		} catch (ResponseTimeoutException e) {
			log.error("Response timeout : "+e.getMessage());
			log.printStackTrace(e);
		} catch (InvalidResponseException e) {
			// Invalid response
			log.error("Receive invalid respose : "+e.getMessage());
			log.printStackTrace(e);
		} catch (NegativeResponseException e) {
			// Receiving negative response (non-zero command_status)
			log.error("Receive negative response : "+e.getMessage());
			log.printStackTrace(e);
		} catch (IOException e) {
			log.error("IO error occur : "+e.getMessage());
			log.printStackTrace(e);
		}
	}

	class SMSCConnector implements Runnable {

		@Override
		public void run() {
			
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e1) {
			}
			while (true) {
				try {
					if (!smpp_connection) {
						bind();
					} else {
						Thread.sleep(10000);
					}
				} catch (Exception e) {
					log.error("Exception in SMSCConnector:run ..." + e.getMessage());
					log.printStackTrace(e);
				}
			}
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
					DeliveryReceipt delReceipt = deliverSm.getShortMessageAsDeliveryReceipt();
					long id = Long.parseLong(delReceipt.getId()) & 0xffffffff;
					String messageId = Long.toString(id, 16).toUpperCase();
					log.info("Receiving delivery receipt for message '" + messageId + "' : " + delReceipt);
				} catch (InvalidDeliveryReceiptException e) {
					log.error("Failed getting delivery receipt");
					e.printStackTrace();
				}
			} else {
				// regular short message
				log.info("Receiving message : " + new String(deliverSm.getShortMessage()));
			}
		}

	}

}
