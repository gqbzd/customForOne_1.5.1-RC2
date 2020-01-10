/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import routing.MessageRouter;

/**
 * A constant bit-rate connection between two DTN nodes.
 */
public class CBRConnection extends Connection {
	private int speed;
	private double transferDoneTime;

	/**
	 * Creates a new connection between nodes and sets the connection
	 * state to "up".
	 * @param fromNode The node that initiated the connection
	 * @param fromInterface The interface that initiated the connection
	 * @param toNode The node in the other side of the connection
	 * @param toInterface The interface in the other side of the connection
	 * @param connectionSpeed Transfer speed of the connection (Bps) when 
	 *  the connection is initiated
	 */
	public CBRConnection(DTNHost fromNode, NetworkInterface fromInterface, 
			DTNHost toNode,	NetworkInterface toInterface, int connectionSpeed) {
		super(fromNode, fromInterface, toNode, toInterface);
		this.speed = connectionSpeed;
		this.transferDoneTime = 0;

	}

	/**
	 * Sets a message that this connection is currently transferring. If message
	 * passing is controlled by external events, this method is not needed
	 * (but then e.g. {@link #finalizeTransfer()} and 
	 * {@link #isMessageTransferred()} will not work either). Only a one message
	 * at a time can be transferred using one connection.
	 * @param from The host sending the message
	 * @param m The message
	 * @return The value returned by 
	 * {@link MessageRouter#receiveMessage(Message, DTNHost)}
	 */
	public int startTransfer(DTNHost from, Message m) {
		assert this.msgOnFly == null : "Already transferring " + 
			this.msgOnFly + " from " + this.msgFromNode + " to " + 
			this.getOtherNode(this.msgFromNode) + ". Can't " + 
			"start transfer of " + m + " from " + from;

		this.msgFromNode = from;
		Message newMessage = m.replicate();
		//获取到与from结点所边接到的另一个结点,然后调用另一个结点的接收消息方法,其中是将此消息放到了incomingMessages的缓冲区
		//注意是模拟,所以只要from结点将消息从结点内部完全发送至结点外部(传输时间),那么另一个结点便可马上收到本结点所发消息,不计算信道延迟
		//传输时间在下面的if判断里进行计算
		int retVal = getOtherNode(from).receiveMessage(newMessage, from);

		if (retVal == MessageRouter.RCV_OK) {
			this.msgOnFly = newMessage;//将正在发送的消息赋给父类的msgOnFly对象
			this.transferDoneTime = SimClock.getTime() + 
			(1.0*m.getSize()) / this.speed;
		}

		return retVal;
	}

	/**
	 * Aborts the transfer of the currently transferred message.
	 */
	public void abortTransfer() {
		assert msgOnFly != null : "No message to abort at " + msgFromNode;
		getOtherNode(msgFromNode).messageAborted(this.msgOnFly.getId(),
				msgFromNode,getRemainingByteCount());
		clearMsgOnFly();
		this.transferDoneTime = 0;
	}

	/**
	 * Gets the transferdonetime
	 */
	public double getTransferDoneTime() {
		return transferDoneTime;
	}
	
	/**
	 * Returns true if the current message transfer is done.
	 * @return True if the transfer is done, false if not
	 */
	public boolean isMessageTransferred() {
		return getRemainingByteCount() == 0;
	}

	/**
	 * returns the current speed of the connection
	 */
	public double getSpeed() {
		return this.speed;
	}

	/**
	 * Returns the amount of bytes to be transferred before ongoing transfer
	 * is ready or 0 if there's no ongoing transfer or it has finished
	 * already
	 * @return the amount of bytes to be transferred
	 */
	public int getRemainingByteCount() {
		int remaining;

		if (msgOnFly == null) {
			return 0;
		}

		remaining = (int)((this.transferDoneTime - SimClock.getTime()) 
				* this.speed);

		return (remaining > 0 ? remaining : 0);
	}

	/**
	 * Returns a String presentation of the connection.
	 */
	public String toString() {
		return super.toString() + (isTransferring() ?  
				" until " + String.format("%.2f", this.transferDoneTime) : "");
	}

}
