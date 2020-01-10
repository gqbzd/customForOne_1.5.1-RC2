package report.custom;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.World;
import report.Report;

/**
 * 自定义报告类，打印：无论有多少个目的结点收到了相同的Msg,都只记录一次
 * @author Bermuda
 *
 */
public class SingleCountDeliveryReport extends Report implements MessageListener{

	public static String HEADER="# time  created  singleCountDelivered  singleCountDelivered/created";
	public static int created;
	
	public SingleCountDeliveryReport() {
		init();
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public void init() {
		super.init();
		created = 0;
		write(HEADER);
	}

	// nothing to implement for the rest
	@Override
	public void messageDeleted(Message m, DTNHost where, boolean dropped) {}
	@Override
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {}
	@Override
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}

	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
		if (firstDelivery && !isWarmup() && !isWarmupID(m.getId())) {
			reportValues();
		}
		
	}
	
	private void reportValues() {
		int singleCountDelivered = World.singleCountDeliveredMessages.size();
		double prob = (1.0 * singleCountDelivered) / created;
		write(format(getSimTime()) + " " + created + " " + singleCountDelivered + 
				" " + format(prob));
	}
	
	@Override
	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getId());
			return;
		}
		created++;
		reportValues();
	}

}
