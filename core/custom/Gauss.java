package core.custom;

import java.util.Scanner;

import core.Coord;

/**
 * a11*x1+a12*x2=b1
 * a21*x1+a22*x2=b2
 * @author Bermuda
 *
 */
public class Gauss {
	/**
      * @列主元高斯消去法
      */
     static double x[];
     static double a[][];
     static double b[];
     static double m;
     static int n;
     //选主元
     public static void SelectAndChangeLine(int k){
         int maxline=k;
         for(int i=k+1;i<n;i++){
             if(Math.abs(a[i][k])>a[maxline][k]){
                 maxline=i;
             }
         }
         if(maxline!=k){
             for(int j=0;j<n+1;j++){
                 b[j]=a[k][j];
                 a[k][j]=a[maxline][j];
                 a[maxline][j]=b[j];
             }
         }        
     }    
     //消元计算
     public static void Elimination(int k){
         for(int i=k+1;i<n;i++){
             m=a[i][k]/a[k][k];
             a[i][k]=0;
             for(int j=k+1;j<n+1;j++){
                 a[i][j]=a[i][j]-m*a[k][j];
                 //System.out.println("tt="+m*a[k][j]);
             }
         }
     }    
     //回代计算
     public static void BacksSubstitution(){
         for(int i=n-1;i>=0;i--){
             for(int j=n-1;j>i;j--){
                 a[i][n]=a[i][n]-x[j]*a[i][j];
             }
//             System.out.println(a[i][n]);
             x[i]=a[i][n]/a[i][i];
         }
     }    
     //打印行
     public static void PrintLine(double[] args){
         for(int j=0;j<args.length;j++){
             System.out.print(args[j]+" ");
         }          
     }    
     //打印矩阵
     public static void PrintMatrix(double[][] args){
         for(int i=0;i<args.length;i++){
             for(int j=0;j<args[i].length;j++){
                 System.out.print(args[i][j]+" ");
             }  
             System.out.println();
         }                   
     }
     public static double[] calculate(Coord c1,Coord c2) {
    	 n = 2;
    	 a=new double[n][n+1];
         b=new double[n+1];
         x=new double[n];
         for(int i=0;i<n;i++){
        	 a[i][0] = 1;
         }
         a[0][1] = c1.getX();
    	 a[0][2] = c1.getY();
    	 a[1][1] = c2.getX();
    	 a[1][2] = c2.getY();
    	 for(int i=0;i<n-1;i++){
             SelectAndChangeLine(i);
             Elimination(i);
         }
         BacksSubstitution();
    	 return x;
     }
     public static void main(String[] args) {
         Scanner as=new Scanner(System.in);
         System.out.println("输入方程组的元数：");
         n=as.nextInt();
         System.out.println("输入方程组的系数矩阵a：");
         a=new double[n][n+1];
         b=new double[n+1];
         x=new double[n];
         for(int i=0;i<n;i++){
             for(int j=0;j<n+1;j++){
                 a[i][j]=as.nextDouble();
             }               
         }
         as.close();
         for(int i=0;i<n-1;i++){
             SelectAndChangeLine(i);
             System.out.println("第"+(i+1)+"次换主元");
             PrintMatrix(a);
             Elimination(i);
             System.out.println("第"+(i+1)+"次消元");
             PrintMatrix(a);
         }
         BacksSubstitution();
         PrintLine(x);
     }
}
