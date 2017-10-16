package convivial.communism.server.example;

import convivial.communism.server.tcp.Server;

import java.net.UnknownHostException;

import java.nio.ByteBuffer;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;

import java.nio.channels.*;

import java.util.ArrayList;
import java.util.Iterator;

public class ChatServer extends Server
{
	private ArrayList<SocketChannel> clients;
	private CharsetDecoder asciiDecoder;
	
	public static void main(String[] args)
	{
		try
		{
			if (args.length >= 1)
				new ChatServer(args[0]);
			else
				new ChatServer();
		}
		catch (UnknownHostException uhe)
		{
			System.out.println("Couldn't create the ip by name.");
			uhe.printStackTrace();
		}
	}
	
	public ChatServer(String IPAddress) throws UnknownHostException
	{
		super(IPAddress, 12);
	}
	
	public ChatServer() throws UnknownHostException
	{
		super(12);
	}
	
	protected void init()
	{
		clients = new ArrayList<SocketChannel>(5);
		asciiDecoder = Charset.forName("US-ASCII").newDecoder();
		System.out.println("Enter 'shutdown' to quit.");
	}
	
	protected Object getAttachment()
	{
		return new StringBuffer();
	}
	
	protected void newClient(SocketChannel channel)
	{
		clients.add(channel);
		sendBroadcastMessage("login from: " + channel.socket().getInetAddress(), channel);
		sendMessage(channel, "\n\nWelcome to Chatter Bocz! There are " + clients.size() + " users online.\n");
		sendMessage(channel, "Type 'quit' to exit.\n");
	}
	
	protected void disconnection(SocketChannel channel)
	{
		clients.remove(channel);
		sendBroadcastMessage("logout: " + channel.socket().getInetAddress(), channel);
	}
	
	protected void receiveData(SelectionKey key, SocketChannel channel, long bytesRead, ByteBuffer data)
	{
		StringBuffer sb = (StringBuffer)key.attachment();
		try
		{
			String str = asciiDecoder.decode(data).toString();
			sb.append(str);
		}
		catch (CharacterCodingException cce)
		{
			cce.printStackTrace();
		}
		
		String line = sb.toString();
		if (line.contains("\n") || line.contains("\r"))
		{
			line = line.trim();
			if (line.toLowerCase().equals("quit"))
			{
				System.out.println("Got quit message from " + channel.socket().getInetAddress());
				super.closeChannel(channel);
			}
			else
			{
				sendBroadcastMessage(channel.socket().getInetAddress() + ": " + line, channel);
				sb.delete(0, sb.length());
			}
		}
	}
	
	protected void receiveConsole(String input)
	{
		if (input.toLowerCase().equals("shutdown"))
		{
			sendBroadcastMessage("<Server is shutting down. Goodbye.>", null);
			shutdown();
		}
		else
		{
			sendBroadcastMessage("server: " + input, null);
		}
	}
	
	private void sendMessage(SocketChannel channel, String msg)
	{
		prepWriteBuffer(msg);
		super.channelWrite(channel, super.writeBuffer);
	}
	
	private void sendBroadcastMessage(String msg, SocketChannel from)
	{
		System.out.println("Sending broadcast message:\n" + msg + "\n");
		prepWriteBuffer(msg);
		Iterator<SocketChannel> i = clients.iterator();
		while (i.hasNext())
		{
			SocketChannel channel = i.next();
			if (channel != from)
				super.channelWrite(channel, super.writeBuffer);
		}
	}
	
	private void prepWriteBuffer(String msg)
	{
		super.writeBuffer.clear();
		super.writeBuffer.put(msg.getBytes());
		super.writeBuffer.putChar('\n');
		super.writeBuffer.flip();
	}		
}









