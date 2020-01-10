/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package interfaces;

import java.util.Collection;

import core.NetworkInterface;

/**
 * A superclass for schemes for optimizing the location of possible contacts
 * with network interfaces of a specific range
 */
abstract public class ConnectivityOptimizer {

	/**
	 * Adds a network interface to the optimizer (unless it is already present)
	 */
	abstract public void addInterface(NetworkInterface ni);

	/**
	 * Adds a collection of network interfaces to the optimizer (except of those
	 * already added
	 */
	abstract public void addInterfaces(Collection<NetworkInterface> interfaces);

	/**
	 * Updates a network interface's location
	 */
	abstract public void updateLocation(NetworkInterface ni);

	/**
	 * Finds all network interfaces that might be located so that they can be
	 * connected with the network interface
	 * 通过初始化时DTNHost,也就是创建主机结点时,所有的接口位置都已经被保存进了虚拟化的地图单元,
	 * 所以,此时只需要计算传进来的接口参数的附近单元,即可得到邻居接口,也就是邻居结点
	 * @param ni network interface that needs to be connected
	 * @return A collection of network interfaces within proximity
	 */
	abstract public Collection<NetworkInterface> getNearInterfaces(
			NetworkInterface ni);

	/**
	 * Finds all other interfaces that are registered to the
	 * ConnectivityOptimizer
	 */
	abstract public Collection<NetworkInterface> getAllInterfaces();
}
