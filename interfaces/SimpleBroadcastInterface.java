/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package interfaces;

import java.util.Collection;

import javax.sound.midi.Soundbank;

import core.CBRConnection;
import core.Connection;
import core.NetworkInterface;
import core.Settings;
import core.SimScenario;

/**
 * A simple Network Interface that provides a constant bit-rate service, where
 * one transmission can be on at a time.
 */
public class SimpleBroadcastInterface extends NetworkInterface {
	
	/**
	 * Reads the interface settings from the Settings file
	 */
	public SimpleBroadcastInterface(Settings s)	{
		super(s);
	}
		
	/**
	 * Copy constructor
	 * @param ni the copied network interface object
	 */
	public SimpleBroadcastInterface(SimpleBroadcastInterface ni) {
		super(ni);
	}

	public NetworkInterface replicate()	{
		return new SimpleBroadcastInterface(this);
	}

	/**
	 * Tries to connect this host to another host. The other host must be
	 * active and within range of this host for the connection to succeed. 
	 * 连接当前的接口与传进来的参数接口
	 * @param anotherInterface The interface to connect to
	 */
	public boolean connect(NetworkInterface anotherInterface) {
//		if(this.host.toString().equals("s0")) {
//			System.out.println("s0->"+anotherInterface.getHost().toString());
//		}
		if (isScanning()  
				&& anotherInterface.getHost().isRadioActive() 
				&& isWithinRange(anotherInterface) 
				&& !isConnected(anotherInterface)
				&& (this != anotherInterface)) {
			// new contact within range
			// connection speed is the lower one of the two speeds 
			//传输速度选择两个接口中速度较小的
			int conSpeed = anotherInterface.getTransmitSpeed();
			if (conSpeed > this.transmitSpeed) {
				conSpeed = this.transmitSpeed; 
			}

			Connection con = new CBRConnection(this.host, this, 
					anotherInterface.getHost(), anotherInterface, conSpeed);
//			if(this.host.getLocation().getX()==450 &&this.host.getLocation().getY()==50) {
//				System.out.println("break");
//			}
			connect(con,anotherInterface);
			return true;
		}
		return false;
	}

	/**
	 * Updates the state of current connections (i.e. tears down connections
	 * that are out of range and creates new ones).
	 * 更新目前连接状态（断开超出范围的连接，并创建新的连接）
	 */
	public void update() {
		if (optimizer == null) {
			return; /* nothing to do */
		}
		
		// First break the old ones
		optimizer.updateLocation(this);//更新结点移动后在不同单元中进行转移
//		自定义，有消息才能主动建立连接，可以被动建立连接,这行代码之前写在了World类updateHosts()方法的循环里，
//		让我找了好久的bug,bug是源结点不能建立连接，因为上行代码没有执行，也就没有更新结点在单元空间的位置，邻居结点获得不到导致的连接建立不了
		if(getHost().getMessageCollection().size() == 0) {
			return;
		}
		for (int i=0; i<this.connections.size(); ) {
			Connection con = this.connections.get(i);
			NetworkInterface anotherInterface = con.getOtherInterface(this);

			
			// all connections should be up at this stage
			assert con.isUp() : "Connection " + con + " was down!";

			if (!isWithinRange(anotherInterface)) {
				disconnect(con,anotherInterface);
				connections.remove(i);
			}
			else {
				i++;
			}
		}
		// Then find new possible connections
		Collection<NetworkInterface> interfaces =
			optimizer.getNearInterfaces(this);
		int count = 0;
		for (NetworkInterface i : interfaces) {
			if(connect(i)) {
				count++;
			}
		}
		
	}

	/** 
	 * Creates a connection to another host. This method does not do any checks
	 * on whether the other node is in range or active 
	 * @param anotherInterface The interface to create the connection to
	 */
	public void createConnection(NetworkInterface anotherInterface) {
		if (!isConnected(anotherInterface) && (this != anotherInterface)) {    			
			// connection speed is the lower one of the two speeds 
			int conSpeed = anotherInterface.getTransmitSpeed();
			if (conSpeed > this.transmitSpeed) {
				conSpeed = this.transmitSpeed; 
			}

			Connection con = new CBRConnection(this.host, this, 
					anotherInterface.getHost(), anotherInterface, conSpeed);
			connect(con,anotherInterface);
		}
	}

	/**
	 * Returns a string representation of the object.
	 * @return a string representation of the object.
	 */
	public String toString() {
		return "SimpleBroadcastInterface " + super.toString();
	}

}
