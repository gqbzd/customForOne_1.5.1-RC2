package routing.custom;

import java.util.List;

import core.Connection;
import core.Message;
import core.Settings;
import routing.ActiveRouter;

//自定义
public class CheckMsgRouter extends ActiveRouter{

	public CheckMsgRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}
	
	protected CheckMsgRouter(ActiveRouter r) {
		super(r);
		// TODO Auto-generated constructor stub
	}

	@Override
	/**
	 * 重写该方法,多两个判断
	 * 1,此连接的另一个结点和消息的源sender相同，便可以不用再转发回去。
	 * 2,与当前结点连接的下一个结点已经有了该消息,便不用再次转发
	 * 分两种情况:另一个结点已经持有的消息,和另一个结点正在发送的消息
	 * 在查看api后,发现只有获得连接(connection)对象,才有办法判断与当前结点连接的另一个结点是否正在传输消息isMessageTransferred()
	 * 进而得到连接中正在传送的消息,才能用当前结点将要发送的消息与得到另外一个结点连接中正在发送的消息进行比较,
	 * 确定下一个结点是否可以接收当前结点将要发送的消息.然而的源程序是不允许当前结点获得下一个结点的连接的,所以第二种情况不行
	 */
	protected Message tryAllMessages(Connection con, List<Message> messages) {
		for (Message m : messages) {
			//增加的两个判断
			if(m.getFrom() == con.getOtherNode(getHost())) continue;
			for(Message otherNodeMsg : con.getOtherNode(getHost()).getMessageCollection()) {
				if(m == otherNodeMsg) continue;
			}
			//新增的语句结束
			int retVal = startTransfer(m, con); //开始传输
			if (retVal == RCV_OK) {
				return m;	// accepted a message, don't try others
			}
			else if (retVal > 0) { 
				return null; // should try later -> don't bother trying others
			}
		}
		return null;
	}
	
	
	@Override
	public void update() {
		// TODO Auto-generated method stub
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		
		// then try any/all message to any/all connection
		this.tryAllMessagesToAllConnections();
	}
	
	@Override
	public CheckMsgRouter replicate() {
		// TODO Auto-generated method stub
		return new CheckMsgRouter(this);
	}

}
