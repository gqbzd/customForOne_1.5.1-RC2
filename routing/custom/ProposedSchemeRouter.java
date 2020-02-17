package routing.custom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimScenario;
import core.custom.Gauss;
import core.custom.LinearEquation;
import routing.ActiveRouter;

//自定义
public class ProposedSchemeRouter extends ActiveRouter{

	public ProposedSchemeRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}
	
	protected ProposedSchemeRouter(ActiveRouter r) {
		super(r);
		// TODO Auto-generated constructor stub
	}

	@SuppressWarnings("unlikely-arg-type")
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
		List<Message> deleteArray = new ArrayList<>();//20200211自定义
		for (Message m : messages) {
			//增加的两个判断
			if(m.getFrom() == con.getOtherNode(getHost())) continue;
			//新增的语句结束
			//20200211自定义新增语句，若是当前连接的下一个结点与理想路线（idealroute）的距离大于设定的阈值（当前设置为200m），
			//当前结点将不会转发此消息，并将此消息删除
			//点P(x0,y0)到直线 Ax+By+C=0的距离可表示为： d=|A*x0+B*y0+C|/√(A^2+B^2)
			//由于LinearEquation中存的是y=a+b*x,所以得转换一下：b*x-y+a=0,所以距离公式d=|b*x0-1*y0+a|/√(b^2+(-1)^2)=>d=|b*x0-y0+a|/|b|
			double distance = Double.MAX_VALUE;
			double minDist = Double.MAX_VALUE;
			Coord anotherLocation = con.getOtherNode(getHost()).getLocation();
			for(LinearEquation<Double,Double> linearEquation : SimScenario.linearMap.values()) {
				distance = Math.abs(linearEquation.getB()*anotherLocation.getX()-anotherLocation.getY()+linearEquation.getA())/Math.abs(linearEquation.getB());
				minDist = Math.min(distance, minDist);
			}
//			这是之前使用的笨方法，先算出与线垂直的直线，然后计算两条线的相交点，再计算点与点的距离。不仅让人看得头皮发麻，出问题了，还不容易找到问题的所在处。
//			List<Double> verticalLineB = new ArrayList<Double>(); 
//			for(LinearEquation<Double,Double> linearEquation : SimScenario.linearMap.values()) {
//				verticalLineB.add(-1/linearEquation.getB());//斜率相乘等于负一
//			}
//			List<Double> verticalLineA = new ArrayList<Double>(); 
//			DTNHost anotherHost = con.getOtherNode(getHost());
//			Coord anotherLocation = anotherHost.getLocation();
//			int lineCounts = verticalLineB.size();
//			for(int j = 0;j<lineCounts;j++) {
//				//y=a+b*x：其中a=y-b*x
//				verticalLineA.add(anotherLocation.getY()-verticalLineB.get(j)*anotherLocation.getX());
//			}
//			//以上就得到了另一个结点与理想路线互相垂直的各条直线
//			double minDist = Double.MAX_VALUE;
//			double x = Double.MAX_VALUE;
//			double y = Double.MAX_VALUE;
//			double gapY = Double.MAX_VALUE;
//			double gapX = Double.MAX_VALUE;
//			double distance = Double.MAX_VALUE;
//			//此循环用于得到两条直线相交的交点坐标，然后计算另一个结点到该交点坐标的距离，然后从中筛选出最小的距离值
//			//y=a+b*x<==>y-bx=a<==>a11=1,a12=-b,b1=a<==>可求出x1=y,x2=x
//			for(int j = 0;j<lineCounts;j++) {
//				Coord verticalLine = new Coord(-verticalLineB.get(j), verticalLineA.get(j));//这里的Coord代表一条直线,填入a12和b1对应x和y
//				LinearEquation<Double, Double> line = SimScenario.linearMap.get(j);
//				Coord idealRoute = new Coord(-line.getB(), line.getA());
//				double[] intersection = Gauss.calculate(verticalLine, idealRoute);//第1个元素是y,第2个元素是x
//				x = intersection[1];
//				y = intersection[0];
//				gapY = anotherLocation.getY()-y;
//				gapX = anotherLocation.getX()-x;
//				distance = Math.sqrt(gapY*gapY+gapX*gapX);//勾股定理
//				minDist = Math.min(distance, minDist);
//			}
			if(minDist >= SimScenario.dTh) {
				deleteArray.add(m);//这里只将需要丢弃的消息添加进需要删除的数组，等待此次循环完毕后再进行删除，若是在循环中删除会抛并发异常。
				break;
			}
			//20200211自定义结束
			
			int retVal = startTransfer(m, con); //开始传输
			if (retVal == RCV_OK) {
				return m;	// accepted a message, don't try others（一旦在这条连接上，本结点有一个消息发送成功，即返回，不再通过此连接发送其它本结点的消息）
			}
			else if (retVal > 0) { 
				return null; // should try later -> don't bother trying others
			}
		}
		//20200211自定义
		DTNHost thisHost = getHost();
		for(Message m : deleteArray) {
			messages.remove(m);//临时缓存
			thisHost.deleteMessage(m.getId(), false);//结点的真正缓存
		}
		return null;
	}
	
//	public static void main(String[] args) {
//		List<Integer> aIntegers = new ArrayList<>();
//		for(int i = 0;i<5;i++) {
//			aIntegers.add(i);
//		}
//		List<Integer> bIntegers = new ArrayList<>(aIntegers);
//		
////		aIntegers.remove(2);//alist中删除了，不会影响到blist.
//		testList(bIntegers);
//		for(int a : aIntegers) {
//			System.out.println(a);
//		}
//		for(int b : bIntegers) {
//			System.out.println(b);
//		}
//	}
//	
//	public static void testList(List<Integer> testList) {
//		testList.remove(2);
//	}
	
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
	public ProposedSchemeRouter replicate() {
		// TODO Auto-generated method stub
		return new ProposedSchemeRouter(this);
	}

}
