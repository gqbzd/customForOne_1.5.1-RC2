package reports;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class AutoCountData {

	public static final String CUSTOM_PATH = "-p";
	public static final String RESULT_DIRECTORY_NAME = "summary";
	public static final String DELIMITER = "\\";//	\
	private static String[] fileArray = new String[] {"100","200","400","800","1000"};
	
	public static void process(String targetPath,String targetFileNamePrefix,String resultFileName) {
		String rootPath = "";
		if(!targetPath.equals("")) {
			rootPath = targetPath+DELIMITER;
		}
		
		//"D:\Program Files (x86)\eclipse for java\eclipse for storing\one_1.5.1-RC2\reports\task_1_16"+"\"+"summary"+"\"+"test.txt"
		File resultFile = new File(rootPath+RESULT_DIRECTORY_NAME+DELIMITER+resultFileName+".txt");
		try {
			if(!resultFile.getParentFile().exists()) {
				resultFile.getParentFile().mkdirs();
			}
			resultFile.createNewFile();//if the file that has same name exists, override it.  
		} catch (IOException e) {
			throw new RuntimeException("create result file error");
		}
		int len = fileArray.length;
//		Java7的try-with-resources可以优雅关闭文件，异常时自动关闭文件
		try(BufferedWriter writer = new BufferedWriter(new FileWriter(resultFile))){
			for(int i = 0; i<len;i++) {
				try (//"D:\Program Files (x86)\eclipse for java\eclipse for storing\one_1.5.1-RC2\reports\task_1_16"+"\"+"test_"+"100.txt"
						FileReader targetFile = new FileReader(rootPath+targetFileNamePrefix+"_"+fileArray[i]+".txt");
						BufferedReader reader = new BufferedReader(targetFile)
					){
					String prevLine = null;//save the last line
					String nextLine = null;
					while((nextLine = reader.readLine())!=null) {
						prevLine = nextLine;
					}
					prevLine = prevLine.trim();
					while (prevLine.startsWith("　")) {//这里判断是不是全角空格
						prevLine = prevLine.substring(1, prevLine.length()).trim();
					}
					while (prevLine.endsWith("　")) {
						prevLine = prevLine.substring(0, prevLine.length() - 1).trim();
					}
					String[] prevLineArray = prevLine.split(" ");
					String deliveryRate = prevLineArray[prevLineArray.length-1];
					writer.write(fileArray[i]+" "+deliveryRate+"\r\n");
					writer.flush();
					
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		
		
	}
	
	public static void main(String[] args) throws Exception {
		if(args.length <= 0) {
			throw new RuntimeException("you have to give the parameter about \n"
					+"1.if you need customize the file path '-p'. \n"
					+"2.the filename of target file has to be set. \n"
					+"3.the filename of file which will save the result data need to be set.");
		}else {
			String targetPath = "";
			String targetFileNamePrefix = null;
			String resultFileName = null;
			if(args[0].equals(CUSTOM_PATH)) {
				targetPath = args[1];//.replace("\\", "\\\\");//	\ -> \\
				targetFileNamePrefix = args[2];
				resultFileName = args[3];
			}else {
				//default root path is in the program of the ONE.
				targetFileNamePrefix = args[0];
				resultFileName = args[1];
			}
			
			process(targetPath,targetFileNamePrefix,resultFileName);
			System.out.println("execute successfully （‐＾▽＾‐）");
		}
		
		
		

	}

}
