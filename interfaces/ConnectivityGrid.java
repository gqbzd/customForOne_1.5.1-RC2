/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package interfaces;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import movement.MovementModel;

import core.Coord;
import core.DTNSim;
import core.NetworkInterface;
import core.Settings;
import core.SettingsError;
import core.World;

/**
 * <P>
 * Overlay grid of the world where each interface is put on a cell depending
 * of its location. This is used in cell-based optimization of connecting
 * the interfaces.</P>
 * 
 * <P>The idea in short:<BR>
 * Instead of checking for every interface if some of the other interfaces are close
 * enough (this approach obviously doesn't scale) we check only interfaces that
 * are "close enough" to be possibly connected. Being close enough is
 * determined by keeping track of the approximate location of the interfaces 
 * by storing them in overlay grid's cells and updating the cell information
 * every time the interfaces move. If two interfaces are in the same cell or in 
 * neighboring cells, they have a chance of being close enough for
 * connection. Then only that subset of interfaces is checked for possible
 * connectivity. 
 * </P>
 * <P>
 * <strong>Note:</strong> this class does NOT support negative
 * coordinates. Also, it makes sense to normalize the coordinates to start
 * from zero to conserve memory. 
 */
public class ConnectivityGrid extends ConnectivityOptimizer {

	/**
	 * Cell based optimization cell size multiplier -setting id ({@value}).
	 * Used in {@link World#OPTIMIZATION_SETTINGS_NS} name space.
	 * Single ConnectivityCell's size is the biggest radio range times this.
	 * Larger values save memory and decrease startup time but may result in
	 * slower simulation.
	 * Default value is {@link #DEF_CON_CELL_SIZE_MULT}.
	 * Smallest accepted value is 1.
	 */
	public static final String CELL_SIZE_MULT_S = "cellSizeMult";
	/** default value for cell size multiplier ({@value}) */
//	public static final int DEF_CON_CELL_SIZE_MULT = 5;
	/**自定义 修改默认值5为1，这个值决定单元的大小，单元大小=transmitRange * 本参数
	 * 这个参数还会影响之前的修改：让不同接口之间也可以连接。因为如果本来有范围很大的，结果搜索到的邻居可能很少，完全不能包括真正的邻居结点。
	 * 所以解决办法是，在本类中设置一个静态的最大的maxRange类变量，每次初始化单元空间时都要进行对比，以最大单元边长为最优先选择。
	 * */
	public static final int DEF_CON_CELL_SIZE_MULT = 1;
	
	private GridCell[][] cells;
	private HashMap<NetworkInterface, GridCell> ginterfaces;
	private int cellSize;
	private int rows;
	private int cols;
	private static int worldSizeX;
	private static int worldSizeY;
	private static int cellSizeMultiplier;
	
	static HashMap<Integer,ConnectivityGrid> gridobjects;

	static {
		DTNSim.registerForReset(ConnectivityGrid.class.getCanonicalName());
		reset();
	}
	
	public static void reset() {
		gridobjects = new HashMap<Integer, ConnectivityGrid>();
		//这个bug就是我花了三天时间才找到的bug，因为地图单元所在的空间没有重置，导致的连接con信息错误，这次实验的结点与上一次实验的结点相连接，导致的混乱杂交程序错误
		newgrid = null;

		Settings s = new Settings(MovementModel.MOVEMENT_MODEL_NS);
		int [] worldSize = s.getCsvInts(MovementModel.WORLD_SIZE,2);
		worldSizeX = worldSize[0];
		worldSizeY = worldSize[1];
		
		s.setNameSpace(World.OPTIMIZATION_SETTINGS_NS);		
		if (s.contains(CELL_SIZE_MULT_S)) {
			cellSizeMultiplier = s.getInt(CELL_SIZE_MULT_S);
		}
		else {
			cellSizeMultiplier = DEF_CON_CELL_SIZE_MULT;
		}
		if (cellSizeMultiplier < 1) {
			throw new SettingsError("Too small value (" + cellSizeMultiplier +
					") for " + World.OPTIMIZATION_SETTINGS_NS + 
					"." + CELL_SIZE_MULT_S);
		}
	}

	/**
	 * Creates a new overlay connectivity grid
	 * @param cellSize Cell's edge's length (must be larger than the largest
	 * 	radio coverage's diameter)
	 * （对这个说法有疑问，个人觉得，单元的边长只需要比半径大即可，即最小边长即半径(官方上面自己也说了最小接受值是1,这里却说必须大于直径,莫明其妙),于是决定更改DEF_CON_CELL_SIZE_MULT=1）
	 * cellSize单元边长（必须大于最大无线电覆盖范围的直径）cellSize = transmitRange * 5
	 */
	private ConnectivityGrid(int cellSize) {
		//举例，世界为边长10的正方形，单元边长为3，除完之后等于3个单元，而实际上还剩了边长为1的空间，所以得多1个单元来存储，所以是4*4的单元空间
		this.rows = worldSizeY/cellSize + 1;
		this.cols = worldSizeX/cellSize + 1;
		// leave empty cells on both sides to make neighbor search easier 
		//用上面的例子举例，如果取到最左上角的单元格，在取其周围的邻居，即八个格子其中的左边格子和上边格子时，会超出bound，所以要加一行一列，
		//同样的，取到最右下角的单元格，取其周围的邻居，即八个格子其中的右边格子和下边格子时，也会超出bound，所以又要加一行一列，
		//因此，一共要加两行两列，才能保证不超出bound。所以实际上的单元空间为6*6的矩阵，当然只有中间的4*4是装了结点的
		this.cells = new GridCell[rows+2][cols+2];
		this.cellSize = cellSize;

		
		for (int i=0; i<rows+2; i++) {
			for (int j=0; j<cols+2; j++) {
				this.cells[i][j] = new GridCell();
			}
		}
		ginterfaces = new HashMap<NetworkInterface,GridCell>();
	}

	/**
	 * Returns a connectivity grid object based on a hash value
	 * @param key A hash value that separates different interfaces from each other
	 * @param maxRange Maximum range used by the radio technology using this 
	 *  connectivity grid. 
	 * @return The connectivity grid object for a specific interface
	 */
	private static ConnectivityGrid newgrid;
	public static ConnectivityGrid ConnectivityGridFactory(int key, 
			double maxRange) {
//		if (gridobjects.containsKey((Integer)key)) {
//			return (ConnectivityGrid)gridobjects.get((Integer)key);
//		} else {
//			ConnectivityGrid newgrid = 
//				new ConnectivityGrid((int)Math.ceil(maxRange * 
//						cellSizeMultiplier));//transmitRange * 5
//			gridobjects.put((Integer)key,newgrid);
//			return newgrid;
//		}
		//自定义custom
		if(null == newgrid) {
			newgrid = new ConnectivityGrid((int)Math.ceil(maxRange * cellSizeMultiplier));
		}
		return newgrid;
	}

	/**
	 * Adds a network interface to the overlay grid
	 * @param ni The new network interface
	 */
	public void addInterface(NetworkInterface ni) {
		GridCell c = cellFromCoord(ni.getLocation());
		c.addInterface(ni);
		ginterfaces.put(ni,c);
	}

	/** 
	 * Removes a network interface from the overlay grid 
	 * @param ni The interface to be removed
	 */
	public void removeInterface(NetworkInterface ni) {
		GridCell c = ginterfaces.get(ni);
		if (c != null) {
			c.removeInterface(ni);
		}
		ginterfaces.remove(ni);
	}

	/**
	 * Adds interfaces to overlay grid
	 * @param interfaces Collection of interfaces to add
	 */
	public void addInterfaces(Collection<NetworkInterface> interfaces) {
		for (NetworkInterface n : interfaces) {
			addInterface(n);
		}
	}

	/**
	 * Checks and updates (if necessary) interface's position in the grid
	 * @param ni The interface to update
	 */
	public void updateLocation(NetworkInterface ni) {
		GridCell oldCell = (GridCell)ginterfaces.get(ni);
		GridCell newCell = cellFromCoord(ni.getLocation());

		if (newCell != oldCell) {
			oldCell.moveInterface(ni, newCell);
			ginterfaces.put(ni,newCell);
		}
	}

	/**
	 * Finds all neighboring cells and the cell itself based on the coordinates
	 * @param c The coordinates
	 * @return Array of neighboring cells 
	 */
	private GridCell[] getNeighborCellsByCoord(Coord c) {
		// +1 due empty cells on both sides of the matrix
		int row = (int)(c.getY()/cellSize) + 1;
		int col = (int)(c.getX()/cellSize) + 1;
		return getNeighborCells(row,col);
	}

	/**
	 * Returns an array of Cells that contains the neighbors of a certain
	 * cell and the cell itself.
	 * 以单元格计算的三阶矩阵（一维数组储存），意为在此范围内即为邻居单元
	 * @param row Row index of the cell
	 * @param col Column index of the cell
	 * @return Array of neighboring Cells(返回以单位为网格单元来作为邻居表示)
	 */
	private GridCell[] getNeighborCells(int row, int col) {
		return new GridCell[] {
			cells[row-1][col-1],cells[row-1][col],cells[row-1][col+1],//1st row
			cells[row][col-1],cells[row][col],cells[row][col+1],//2nd row
			cells[row+1][col-1],cells[row+1][col],cells[row+1][col+1]//3rd row
		};
	}

	/**
	 * Get the cell having the specific coordinates
	 * @param c Coordinates
	 * @return The cell
	 */
	private GridCell cellFromCoord(Coord c) {
		// +1 due empty cells on both sides of the matrix
		//因为初始化时在外层加了两行两列,而这两行两列是不能放值的,只是为了在取邻居时,不抛数组超出bound的异常,而添加的两行两列,
		//所以这里要在原来的基础上加1,就相当于计算后,本来是该存放进(0,0)空间的,但是左边和上边分别多了一列一行,为了保证只有中间的4*4存结点,
		//所以得挪到(1,1)的位置,这样,最边上的一圈就不会存结点了.
		int row = (int)(c.getY()/cellSize) + 1; 
		int col = (int)(c.getX()/cellSize) + 1;

		assert row > 0 && row <= rows && col > 0 && col <= cols : "Location " + 
		c + " is out of world's bounds";
		
		return this.cells[row][col];
	}

	/**
	 * Returns all interfaces that use the same technology and channel
	 */
	public Collection<NetworkInterface> getAllInterfaces() {
		return (Collection<NetworkInterface>)ginterfaces.keySet();
	}

	/**
	 * Returns all interfaces that are "near" (i.e., in neighboring grid cells) 
	 * and use the same technology and channel as the given interface 
	 * 通过初始化时DTNHost,也就是创建主机结点时,所有的接口位置都已经被保存进了虚拟化的地图单元,
	 * 所以,此时只需要计算传进来的接口参数的附近单元,即可得到邻居接口,也就是邻居结点，
	 * 只不过这个“邻居”有点多，之后还会对每个“邻居”进行距离的判断，才能确定真正的邻居
	 * @param ni The interface whose neighboring interfaces are returned
	 * @return List of near interfaces
	 */
	public Collection<NetworkInterface> getNearInterfaces(
			NetworkInterface ni) {
		ArrayList<NetworkInterface> niList = new ArrayList<NetworkInterface>();
		GridCell loc = (GridCell)ginterfaces.get(ni);
		if (loc != null) {
			GridCell[] neighbors = 
				getNeighborCellsByCoord(ni.getLocation());//例:ni.getLocation()=(50.00,450.00)
			for (int i=0; i < neighbors.length; i++) {
				niList.addAll(neighbors[i].getInterfaces());
			}
		}
		
		return niList;
	}


	/**
	 * Returns a string representation of the ConnectivityCells object
	 * @return a string representation of the ConnectivityCells object
	 */
	public String toString() {
		return getClass().getSimpleName() + " of size " + 
			this.cols + "x" + this.rows + ", cell size=" + this.cellSize;
	}

	/**
	 * A single cell in the cell grid. Contains the interfaces that are 
	 * currently in that part of the grid.
	 * 单元格网格中的单个单元格。 此单元格包含当前在该部分中的接口。
	 */
	public class GridCell {
		// how large array is initially chosen
		private static final int EXPECTED_INTERFACE_COUNT = 5;
		private ArrayList<NetworkInterface> interfaces;

		private GridCell() {
			this.interfaces = new ArrayList<NetworkInterface>(
					EXPECTED_INTERFACE_COUNT);
		}

		/**
		 * Returns a list of of interfaces in this cell
		 * @return a list of of interfaces in this cell
		 */
		public ArrayList<NetworkInterface> getInterfaces() {
			return this.interfaces;
		}

		/**
		 * Adds an interface to this cell
		 * @param ni The interface to add
		 */
		public void addInterface(NetworkInterface ni) {
			this.interfaces.add(ni);
		}

		/**
		 * Removes an interface from this cell
		 * @param ni The interface to remove
		 */
		public void removeInterface(NetworkInterface ni) {
			this.interfaces.remove(ni);
		}

		/**
		 * Moves a interface in a Cell to another Cell
		 * @param ni The interface to move
		 * @param to The cell where the interface should be moved to
		 */
		public void moveInterface(NetworkInterface ni, GridCell to) {
			to.addInterface(ni);
			boolean removeOk = this.interfaces.remove(ni); 
			assert removeOk : "interface " + ni + 
				" not found from cell with " + interfaces.toString();
		}

		/**
		 * Returns a string representation of the cell
		 * @return a string representation of the cell
		 */
		public String toString() {
			return getClass().getSimpleName() + " with " + 
				this.interfaces.size() + " interfaces :" + this.interfaces;
		}
	}
	
}