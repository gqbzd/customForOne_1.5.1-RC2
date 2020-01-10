/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import input.EventQueue;
import input.ExternalEvent;
import input.ScheduledUpdatesQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.sun.javafx.collections.MappingChange.Map;

/**
 * World contains all the nodes and is responsible for updating their
 * location and connections.
 */
public class World {
	/** name space of optimization settings ({@value})*/
	public static final String OPTIMIZATION_SETTINGS_NS = "Optimization";

	/**
	 * Should the order of node updates be different (random) within every 
	 * update step -setting id ({@value}). Boolean (true/false) variable. 
	 * Default is @link {@link #DEF_RANDOMIZE_UPDATES}.
	 */
	public static final String RANDOMIZE_UPDATES_S = "randomizeUpdateOrder";
	/** should the update order of nodes be randomized -setting's default value
	 * ({@value}) */
	public static final boolean DEF_RANDOMIZE_UPDATES = true;
	
	/**
	 * Should the connectivity simulation be stopped after one round 
	 * -setting id ({@value}). Boolean (true/false) variable. 
	 */
	public static final String SIMULATE_CON_ONCE_S = "simulateConnectionsOnce";

	private int sizeX;
	private int sizeY;
	private List<EventQueue> eventQueues;
	private double updateInterval;
	private SimClock simClock;
	private double nextQueueEventTime;
	private EventQueue nextEventQueue;
	/** list of nodes; nodes are indexed by their network address */
	private List<DTNHost> hosts;
	private boolean simulateConnections;
	/** nodes in the order they should be updated (if the order should be 
	 * randomized; null value means that the order should not be randomized) */
	private ArrayList<DTNHost> updateOrder;
	/** is cancellation of simulation requested from UI */
	private boolean isCancelled;
	private List<UpdateListener> updateListeners;
	/** Queue of scheduled update requests */
	private ScheduledUpdatesQueue scheduledUpdates;
	private boolean simulateConOnce;
	private boolean isConSimulated;
	
	//自定义，单次计数结点之间重复收到的消息（我收到了A，你也收到了A，只计算一次）
	public static Set<String> singleCountDeliveredMessages;
	static {
		singleCountDeliveredMessages = new HashSet<>();
	}

	/**
	 * Constructor.
	 */
	public World(List<DTNHost> hosts, int sizeX, int sizeY, 
			double updateInterval, List<UpdateListener> updateListeners,
			boolean simulateConnections, List<EventQueue> eventQueues) {
		this.hosts = hosts;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.updateInterval = updateInterval;
		this.updateListeners = updateListeners;
		this.simulateConnections = simulateConnections;
		this.eventQueues = eventQueues;
		
		this.simClock = SimClock.getInstance();
		this.scheduledUpdates = new ScheduledUpdatesQueue();
		this.isCancelled = false;
		this.isConSimulated = false;

		setNextEventQueue();
		initSettings();
	}

	/**
	 * Initializes settings fields that can be configured using Settings class
	 */
	private void initSettings() {
		Settings s = new Settings(OPTIMIZATION_SETTINGS_NS);
		boolean randomizeUpdates = DEF_RANDOMIZE_UPDATES;

		if (s.contains(RANDOMIZE_UPDATES_S)) {
			randomizeUpdates = s.getBoolean(RANDOMIZE_UPDATES_S);
		}
		simulateConOnce = s.getBoolean(SIMULATE_CON_ONCE_S, false);
		
		if(randomizeUpdates) {
			// creates the update order array that can be shuffled
			this.updateOrder = new ArrayList<DTNHost>(this.hosts);
		}
		else { // null pointer means "don't randomize"
			this.updateOrder = null;
		}
	}

	/**
	 * Moves hosts in the world for the time given time initialize host 
	 * positions properly. SimClock must be set to <CODE>-time</CODE> before
	 * calling this method.
	 * @param time The total time (seconds) to move
	 */
	public void warmupMovementModel(double time) {
		if (time <= 0) {
			return;
		}

		while(SimClock.getTime() < -updateInterval) {
			moveHosts(updateInterval);
			simClock.advance(updateInterval);
		}

		double finalStep = -SimClock.getTime();

		moveHosts(finalStep);
		simClock.setTime(0);	
	}

	/**
	 * Goes through all event Queues and sets the 
	 * event queue that has the next event.
	 */
	public void setNextEventQueue() {
		EventQueue nextQueue = scheduledUpdates;
		double earliest = nextQueue.nextEventsTime();

		/* find the queue that has the next event */
		for (EventQueue eq : eventQueues) {
			if (eq.nextEventsTime() < earliest){
				nextQueue = eq;	
				earliest = eq.nextEventsTime();
			}
		}

		this.nextEventQueue = nextQueue;
		this.nextQueueEventTime = earliest;
	}

	/** 
	 * 消息的创建、转发、接收，连接的建立、撤消都可以视为事件
	 * Update (move, connect, disconnect etc.) all hosts in the world.
	 * Runs all external events that are due between the time when
	 * this method is called and after one update interval.
	 */
	public void update () {
		double runUntil = SimClock.getTime() + this.updateInterval;

		setNextEventQueue();//从事件队列中找到下次事件是什么,并设置下次事件发生的时间

		/* process all events that are due until next interval update 
		 * 每个间隔都处理所有到期的事件，
		 * 使用MessageEventGenerator自动创建消息，只有创建消息事件MessageCreateEvent在world.update的processEvent被处理，
		 * 消息转发、接收则是在相关路由器的update被处理。
		 * 值得注意的是，当一个新消息被创建时，会触发消息监听器MessageListener，MessageListener是一个接口，
		 * 实现该接口主要是一些reports。通俗理解就是消息被创建后，
		 * 通知相关reports更新统计信息（如CreatedMessagesReport, MessageStatsReport）
		 * */
		while (this.nextQueueEventTime <= runUntil) {
			simClock.setTime(this.nextQueueEventTime);
			ExternalEvent ee = this.nextEventQueue.nextEvent();//如果nextEventQueue是MessageEventGenerator,就会触发真正的事件"消息创建事件"MessageCreateEvent
			ee.processEvent(this);
			updateHosts(); // update all hosts after every event
			setNextEventQueue();
		}
		//更新节点移动
		moveHosts(this.updateInterval);
		simClock.setTime(runUntil);
		//更新节点连接情况
		updateHosts();

		/* inform all update listeners */
		for (UpdateListener ul : this.updateListeners) {
			ul.updated(this.hosts);
		}
	}

	/**
	 * Updates all hosts (calls update for every one of them). If update
	 * order randomizing is on (updateOrder array is defined), the calls
	 * are made in random order.
	 */
	private void updateHosts() {
		//分两种情况，根据节点是否随机顺序，依次更新每一个节点
		//按网络地址network address排序的
		if (this.updateOrder == null) { // randomizing is off
			for (int i=0, n = hosts.size();i < n; i++) {
				if (this.isCancelled) {
					break;
				}
				//simulateConnections用于标识网络层network layer是否需要被更新，
				hosts.get(i).update(simulateConnections);
			}
		}
		else { // update order randomizing is on
			assert this.updateOrder.size() == this.hosts.size() : 
				"Nrof hosts has changed unexpectedly";
			//随机顺序。默认是随机顺序,可在设置文件中更改设置
			Random rng = new Random(SimClock.getIntTime());
			Collections.shuffle(this.updateOrder, rng); 
			for (int i=0, n = hosts.size();i < n; i++) {
				if (this.isCancelled) {
					break;
				}
				this.updateOrder.get(i).update(simulateConnections);
			}
//			System.out.println("network layer update success");
		}
		//simulateConOnce标识连接是否只更新一次，默认为false,可以在配置文件中更改
		if (simulateConOnce && simulateConnections) {
			simulateConnections = false;
		}
	}

	/**
	 * Moves all hosts in the world for a given amount of time
	 * @param timeIncrement The time how long all nodes should move
	 */
	private void moveHosts(double timeIncrement) {
		for (int i=0,n = hosts.size(); i<n; i++) {
			DTNHost host = hosts.get(i);
			host.move(timeIncrement);			
		}		
	}

	/**
	 * Asynchronously cancels the currently running simulation
	 */
	public void cancelSim() {
		this.isCancelled = true;
	}

	/**
	 * Returns the hosts in a list
	 * @return the hosts in a list
	 */
	public List<DTNHost> getHosts() {
		return this.hosts;
	}

	/**
	 * Returns the x-size (width) of the world 
	 * @return the x-size (width) of the world 
	 */
	public int getSizeX() {
		return this.sizeX;
	}

	/**
	 * Returns the y-size (height) of the world 
	 * @return the y-size (height) of the world 
	 */
	public int getSizeY() {
		return this.sizeY;
	}

	/**
	 * Returns a node from the world by its address
	 * @param address The address of the node
	 * @return The requested node or null if it wasn't found
	 */
	public DTNHost getNodeByAddress(int address) {
		if (address < 0 || address >= hosts.size()) {
			throw new SimError("No host for address " + address + ". Address " +
					"range of 0-" + (hosts.size()-1) + " is valid");
		}

		DTNHost node = this.hosts.get(address);
		assert node.getAddress() == address : "Node indexing failed. " + 
			"Node " + node + " in index " + address;

		return node; 
	}

	/**
	 * Schedules an update request to all nodes to happen at the specified 
	 * simulation time.
	 * @param simTime The time of the update
	 */
	public void scheduleUpdate(double simTime) {
		scheduledUpdates.addUpdate(simTime);
	}
}
