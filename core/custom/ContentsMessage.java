package core.custom;

import core.DTNHost;
import core.Message;

//自定义 用作可以传输内容的消息标识
public class ContentsMessage extends Message{
	

	public ContentsMessage(DTNHost from, DTNHost to, String id, int size) {
		super(from, to, id, size);
		// TODO Auto-generated constructor stub
	}

	public ContentsMessage(DTNHost from, DTNHost to, String id, int size,String contents) {
		super(from, to, id, size);
		setContents(contents);
		// TODO Auto-generated constructor stub
	}

}
