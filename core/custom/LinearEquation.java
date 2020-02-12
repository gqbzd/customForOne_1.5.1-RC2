package core.custom;

/**
 *自定义的直线方程类
 * y=a+b*x<==>x1+a*x2=b
 * 其中a为横坐标，b为纵坐标,a11=1,a12=a,b1=b
 * 求解出x1为截距，x2为斜率，
 * x1对应a,x2对应b
 * @author Bermuda
 *
 * @param <A>
 * @param <B>
 */
public class LinearEquation<A extends Object,B extends Object> {

	private A a;
	private B b;
	public A getA() {
		return a;
	}
	public void setA(A a) {
		this.a = a;
	}
	public B getB() {
		return b;
	}
	public void setB(B b) {
		this.b = b;
	}
	
	public LinearEquation(A a,B b){
		this.a = a;
		this.b = b;
	}
	
}
