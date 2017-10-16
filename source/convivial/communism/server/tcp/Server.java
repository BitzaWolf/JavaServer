package convivial.communism.server.tcp;

import java.io.*;

import java.net.*;

import java.nio.ByteBuffer;
import java.nio.channels.*;

import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

/**
**	Runs a Server. This class is meant to be extended and run stand-alone. It consumes the thread that ran
**		creates it, so don't create a Server and expect to keep on doing other tasks unless you build another
**		thread to just run the server.
**	
**	<p>Server simplifies a TCP-based server, accepting connections, handling disconnections, and managing
**		input received from connections to the server. This allows the subclass to simply override
**		the few nessecary methods and be able to run a full server doing whatever you want with the
**		the data being received and sending data out.</p>
**	
**	<p>For an example, see <code>convivial.communism.server.example.ChatServer</code> source.</p>
**	
**	<br>
**	<h2>Import these:</h2>
**	<code>
**	import java.nio.channels.*;<br>
**	import java.nio.ByteBuffer;
**	</code>
**	<br>
**	<h2>Override these:</h2>
**	<code>
**	<ul>
**	<li>protected void init()</li>
**  <li>protected Object getAttachment()</li>
**	<li>protected void newClient(SocketChannel channel) </li>
**	<li>protected void disconnection(SocketChannel channel) </li>
**	<li>protected void receiveData(SelectionKey key, SocketChannel channel, long bytesRead, ByteBuffer data) </li>
**	<li>protected void receiveConsole(String input)</li>
**	</ul>
**	</code>
**/
public abstract class Server
{
	/** A ByteBuffer of size 255 bytes for use in any way the subclass wants. **/
	protected ByteBuffer writeBuffer;
	
	private ByteBuffer readBuffer;
	private ServerSocketChannel sSockChan;
	private Selector readSelector;
	private Thread acceptThread, readThread;
	private boolean running;
	
	private static final int CHANNEL_NAP_TIME = 10; // 100 fps
	
	/**
	**	Creates a new server that runs on port 10997.
	**/
	public Server() throws UnknownHostException
	{
		this(10997);
	}
	
	/**
	**	Creates a new server using the specified port number.
	**	
	**	@param port Network port to run the server on.
	**/
	public Server(int port) throws UnknownHostException
	{
		this(InetAddress.getLocalHost(), port);
	}
	
	/**
	**	Creates a new server running on the specified IPAddress and Port number. This
	**		is a convienience method so subclasses won't have to import and handle
	**		the <code>java.net.IneAddress</code> class and can instead pass a simple
	**		string representing an Internet Address.
	**	
	**	@param ipName InternetAddress in a String format, like 10.0.0.8
	**	@param port Port number to accept connections from.
	**	
	**	@throws UnknownHostException if <code>java.net.InetAddress</code> doesn't like the passed <code>ipName</code>.
	**/
	public Server(String ipName, int port) throws UnknownHostException
	{
		this(InetAddress.getByName(ipName), port);
	}
	
	/**
	**	Starts up the server using the InetAddress and Port to receieve message from.
	**	
	**	@param ip Internet Address to run the server on.
	**	@param port Port number to accept and expect messages from.
	**/
	public Server(InetAddress ip, int port)
	{
		writeBuffer = ByteBuffer.allocateDirect(255);
		readBuffer = ByteBuffer.allocateDirect(255);
		
		try
		{
			sSockChan = ServerSocketChannel.open();
			sSockChan.socket().bind(new InetSocketAddress(ip, port));
			
			readSelector = Selector.open();
			
			System.out.println("Starting server at address: " + ip.getHostAddress() + " on port: " + port);
		}
		catch (IOException ioe)
		{
			ioe.printStackTrace();
			return;
		}
		
		init();
		
		running = true;
		
		acceptThread = new Thread(new AcceptConnections());
		acceptThread.start();
		readThread = new Thread(new ReadMessages());
		readThread.start();
		
		terminalInput();
	}
	
	/**
	**	Handles initialization procedures needed by the sub-class. Generally these calls
	**		would happen in the constructor, but this class is set up to never run past
	**		this server's constructor, so this method is needed.
	**/
	protected abstract void init();
	
	/**
	**	Returns the object to attach to a newly accepted channel. Each SelectionKey can have
	**		any object as an attachment, and this method gets called whenever a new acception
	**		is made to get the Object to attach to the new channel. Later on, in
	**		<code>receivedData(SelectionKey key, SocketChannel channel, long bytesRead, ByteBuffer data)</code>
	**		call <code>key.attachment();</code> to retrieve the attachment and then use it.
	**		It's a useful way to attach data to a particular channel (maybe like an incomplete message).
	**	
	**	<p>Generally, have this method return the object you want to be attached to each channel.</p>
	**	
	**	@return Object to attach to each channel.
	**/
	protected abstract Object getAttachment();
	
	/**
	**	Manages a reaction to a new client being connected.
	**	
	**	@param channel The client that was just accepted by the server.
	**/
	protected abstract void newClient(SocketChannel channel);
	
	/**
	**	Manages a reaction to a client being disconnected.
	**	
	**	@param channel Client who was just disconnected.
	**/
	protected abstract void disconnection(SocketChannel channel);
	
	/**
	**	Manipulates data received by the server from some client. What the server does with
	**		this information/data depends on the server.
	**	
	**	@param key The client who sent the data.
	**	@param channel Client's SocketChannel.
	**	@param bytesRead Number of bytes read from the channel.
	**	@param data Actual information that was sent.
	**/
	protected abstract void receiveData(SelectionKey key, SocketChannel channel, long bytesRead, ByteBuffer data);
	
	/**
	**	Handles commands being typed in from the console. Implement commands
	**		for the server here, generally at least one command that will
	**		will shutdown the server (via a call to <code>shutdown()</code>).
	**	
	**	@param input Message typed into the server's console.
	**/
	protected abstract void receiveConsole(String input);
	
	/**
	**	Shuts the server down.
	**/
	protected void shutdown()
	{
		running = false;
		acceptThread.interrupt();
		readThread.interrupt();
	}
	
	/**
	**	Writes out the passed ByteBuffer of data to the specified channel.
	**		Make sure to call <code>flip()</code> on the writeBuffer before calling
	**		this method.
	**	
	**	@param channel Client to send data to.
	**	@param writeBuffer Data to send.
	**/
	protected void channelWrite(SocketChannel channel, ByteBuffer writeBuffer)
	{
		long bytesWritten = 0;
		long bytesToWrite = writeBuffer.remaining();
		
		try
		{
			while (bytesWritten != bytesToWrite)
			{
				bytesWritten += channel.write(writeBuffer);
				try
				{
					Thread.sleep(CHANNEL_NAP_TIME);
				}
				catch (InterruptedException ie)
				{
					ie.printStackTrace();
				}
			}
		}
		catch (ClosedChannelException cce)
		{
			cce.printStackTrace();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		writeBuffer.rewind();
	}
	
	private void terminalInput()
	{
		Scanner console = new Scanner(System.in);
		String input;
		while (running)
		{
			input = console.nextLine();
			receiveConsole(input);
		}
	}
	
	protected void closeChannel(SocketChannel channel)
	{
		try
		{
			channel.close();
		}
		catch (IOException ioe)
		{
			ioe.printStackTrace();
		}
		disconnection(channel);
	}
	
	private class AcceptConnections implements Runnable
	{
		public void run()
		{
			while (running)
			{
				try
				{
					SocketChannel channel = sSockChan.accept();
					channel.configureBlocking(false);
					readSelector.wakeup();
					channel.register(readSelector, SelectionKey.OP_READ, getAttachment());
					newClient(channel);
				}
				catch (NotYetBoundException nybe)
				{
					nybe.printStackTrace();
					shutdown();
				}
				catch (ClosedByInterruptException cbie)
				{
					System.out.println("AcceptConnections has been interrupted");
				}
				catch (ClosedChannelException cce)
				{
					cce.printStackTrace();
				}
				catch (IOException ioe)
				{
					ioe.printStackTrace();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}
	}
	
	private class ReadMessages implements Runnable
	{
		public void run()
		{
			while (running)
			{
				try
				{
					readSelector.select();
					Thread.sleep(CHANNEL_NAP_TIME); // Some odd bug where this method runs too fast and nothing gets handled...... : [
					Set<SelectionKey> readyKeys = readSelector.selectedKeys();
					Iterator<SelectionKey> i = readyKeys.iterator();
					while (i.hasNext())
					{
						SelectionKey key = i.next();
						i.remove();
						SocketChannel channel = (SocketChannel) key.channel();
						readBuffer.clear();
						long bytesRead = -1;
						try
						{
							bytesRead = channel.read(readBuffer);
						}
						catch (IOException ioe)
						{
							key.cancel();
							closeChannel(channel);
							continue;
						}
						if (bytesRead == -1)
							closeChannel(channel);
						else
						{
							readBuffer.flip();
							receiveData(key, channel, bytesRead, readBuffer);
							readBuffer.clear();
						}
					}
				}
				catch (IOException ioe)
				{
					ioe.printStackTrace();
				}
				catch (ClosedSelectorException cse)
				{
					cse.printStackTrace();
				}
				catch (InterruptedException ie)
				{
					
				}
			}
		}
	}
}






