package convivial.communism.server.example;

import convivial.communism.server.ServerListener;
import convivial.communism.server.tcp.ServerConnection;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Scanner;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;

/**
**	This is an example class that implements <code>ServerListener</code> and uses
**		a <code>ServerConnection</code> to read and send chat messages to other
**		<code>ChatClient</code>s. See the source code for more information.
**/
public class ChatClient implements ServerListener
{	
	private static boolean inClient = true;
	private static boolean connected = false;
	private static ServerConnection serverConn;
	private static Scanner console = new Scanner(System.in);
	private static ByteBuffer writeBuffer = ByteBuffer.allocateDirect(255);
	private static CharsetDecoder asciiDecoder = Charset.forName("US-ASCII").newDecoder();
	private static ChatClient listener = new ChatClient();
	
	/**
	**	Starts up the ChatClient
	**/
	public static void main(String[] args)
	{
		System.out.println("Hello, and welcome to chatter box client!\n");
		
		while (inClient)
		{
			connectToServer();
			while (connected)
			{
				processConsoleInput();
			}
		}
		
		System.out.println("\n\nThanks for playing! Goodbye.");
	}
	
	/**
	**	Connects to the server, which needs to be entered as well as a specific
	**		port via terminal input.
	**/
	public static void connectToServer()
	{
		System.out.print("Please enter the IPAddress to connect to (or 'quit' to exit): ");
		String IP = console.nextLine();
		
		if (IP.toLowerCase().equals("quit"))
		{
			inClient = false;
			return;
		}
		
		System.out.print("Please enter the Port number: ");
		String portStr = console.nextLine();
		
		int port = 0;
		try
		{
			port = Integer.parseInt(portStr);
		}
		catch (NumberFormatException nfe)
		{
			System.out.println("Only enter a whole number for the port...");
			return;
		}
		
		try
		{
			serverConn = new ServerConnection(IP, port, new StringBuffer(), listener);
		}
		catch (UnknownHostException uhe)
		{
			System.out.println("Could not find the host specified, " + IP);
			return;
		}
		catch (IOException ioe)
		{
			System.out.println("Unknown error:");
			ioe.printStackTrace();
			return;
		}
		
		connected = true;
	}
	
	/**
	**	Performs commands based on what the user typed in. Only one command
	**		exists: <code>quit</code>, which causes a disconnection from the server
	**		thus returning to the loop in main where a connection to a server
	**		is asked for (or the user can enter quit again and close ChatClient).
	**/
	public static void processConsoleInput()
	{
		String input = console.nextLine();
		if (!connected) // it's possible to be blocked waiting for console, then the server shutdown.
			return;
		
		if (input.toLowerCase().equals("quit"))
		{
			serverConn.disconnect();
		}
		else if (input.equals(""))
			return;
		else
		{
			writeBuffer.clear();
			writeBuffer.put(input.getBytes());
			writeBuffer.putChar('\n');
			writeBuffer.flip();
			serverConn.channelWrite(writeBuffer);
		}
	}
	
	/**
	**	A method from the <code>ServerListener</code> interface, handles
	**		being disconnected from the server (either by the server or
	**		from a call to <code>disconnect</code> from the
	**		<code>ServerConnection</code>
	**/
	public void disconnected()
	{
		System.out.println("Disconnected from server.");
		connected = false;
	}
	
	/**
	**	The other method from <code>ServerListener</code> and handles
	**		receiving data from the server. This method takes the
	**		byte data, puts it into a string, checks to see if the string
	**		contains <code>\n</code>, indicating a complete message.
	**	
	**	@param key The Server's channel and its attached StringBuffer.
	**	@param channel The server's channel.
	**	@param bytesRead Number of bytes read into the ByteBuffer.
	**	@param data Actual data transmitted (plain string).
	**/
	public void receiveData(SelectionKey key, SocketChannel channel, long bytesRead, ByteBuffer data)
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
			System.out.println(line);
			sb.delete(0, sb.length());
		}
	}
}













