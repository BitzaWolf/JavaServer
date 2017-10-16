package convivial.communism.server.tcp;

import convivial.communism.server.ServerListener;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Set;
import java.util.Iterator;

/**
**	Represents a connection to a tcp-based server. Note that in order for this class to be useful you
**		need to implement a <code>ServerListener</code>, unless you only want outgoing messages (and even then could
**		not tell when the connection has been lost).
**/
public class ServerConnection implements Runnable
{
	private static final int WRITE_NAP_TIME = 10;
	
	private ByteBuffer readBuffer;
	private boolean running;
	private SocketChannel channel;
	private Selector readSelector;
	private Thread readThread;
	private ServerListener sl;
	
	/**
	** Gets ready to connect to a server, but does not connect to one yet.
	**		Note that this constructor also does not set any <code>ServerListener</code>.
	**/
	public ServerConnection()
	{
		readBuffer = ByteBuffer.allocateDirect(255);
	}
	
	/**
	**	Gets ready to connect to a server, but does not connect to one yet.
	**	
	**	@param sl Object to receieve incoming data messages and disconnect notifications.
	**/
	public ServerConnection(ServerListener sl)
	{
		this();
		this.sl = sl;
	}
	
	/**
	**	Establishes a new connection to the indicated server using the intended port.
	**	
	**	@param host Internet Address, in a string, representing the server to connect to.
	**	@param port Port to connect to.
	**	@param attachment Object to have the channel hold on to.
	**	@param sl Object to receieve messages from the channel, may be null.
	**	
	**	@throws UnknownHostException If the Internet Address cannot be found.
	**	@throws IOException If a general I/O error has occured... Could be a lot things.
	**/
	public ServerConnection(String host, int port, Object attachment, ServerListener sl) throws UnknownHostException, IOException
	{
		this(sl);
		setListener(sl);
		connect(host, port, attachment);
	}
	
	/**
	**	Sets the passed <code>ServerListener</code> to handle incomming messages from the server as well
	**		as disconnections.
	**	
	**	@param sl <code>ServerListener</code> to handle incoming messages and server disconnection messages.
	**/
	public void setListener(ServerListener sl)
	{
		this.sl = sl;
	}
	
	/**
	**	Connects to the intended server; does nothing if the <code>ServerConnection</code> is already
	**		connected to a server. If this class is already connected, it throws an IllegalStateException.
	**	
	**	@param host IP address to connect to, probably in a numerical format: 10.0.0.4
	**	@param port Port to connect to.
	**	@param attachment Object to associate with this connection/channel.
	**	
	**	@throws UnknownHostException If the Internet Address cannot be found.
	**	@throws IOException If a general I/O error has occured... Could be a lot things.
	**	@throws IllegalStateException If this <code>ServerConnection</code> is already connected to a server.
	**	
	**	@see convivial.communism.server.ServerListener
	**/
	public void connect(String host, int port, Object attachment) throws UnknownHostException, IOException
	{
		if (running == true)
			throw new IllegalStateException("Cannot connect to a new server; already connected to a server. Call disconnect() first.");
		
		readSelector = Selector.open();
		InetAddress addr = InetAddress.getByName(host);
		channel = SocketChannel.open(new InetSocketAddress(addr, port));
		channel.configureBlocking(false);
		channel.register(readSelector, SelectionKey.OP_READ, attachment);
		
		readThread = new Thread(this);
		readThread.start();
	}
	
	/**
	**	<font color=RED><b>DO NOT CALL</b></font>; automatically invoked by a new Thread. This class runs a thread to sit
	**		and wait for input from the server.
	**/
	public void run()
	{
		if (running)
			throw new IllegalStateException("Cannot run(); already connected to a server. Call disconnect() first.");
		
		running = true;
		
		while (running)
		{
			try
			{
				readSelector.select();
				
				Set<SelectionKey> readyKeys = readSelector.selectedKeys();
				Iterator<SelectionKey> i = readyKeys.iterator();
				while (i.hasNext())
				{
					SelectionKey key = i.next();
					i.remove();
					SocketChannel channel = (SocketChannel) key.channel();
					readBuffer.clear();
					
					long nbytes = 0;
					try
					{
						nbytes = channel.read(readBuffer);
					}
					catch (ClosedChannelException cce)
					{
						break;
					}
					catch (IOException ioe)
					{
						key.cancel();
						disconnect();
					}
					
					if (nbytes == -1)
						disconnect();
					else
					{
						readBuffer.flip();
						sl.receiveData(key, channel, nbytes, readBuffer);
					}
				}
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
	
	/**
	**	Tells the ServerListener the connection is disconnecting, then
	**		closes the channel and ends the thread that's been blocking on input from the server.
	**		The ServerListener is notified before anything is closed, so it is possible to send
	**		outgoing data before everything is shutdown.
	**	
	**	<p>The <code>ServerConnection</code> is ready to connect to a new server after this
	**		method finishes.</p>
	**/
	public void disconnect()
	{
		if (!running)
			throw new IllegalStateException("Not connected to any server.");
		
		sl.disconnected();
		running = false;
		try
		{
			channel.close();
			readThread.interrupt();
			readSelector.close();
		}
		catch (IOException ioe)
		{
			ioe.printStackTrace();
		}
	}
	
	/**
	**	Writes out data to the server. This method <i><b>could</b></i>
	**		take a long while depending on how fast data gets written down the channel
	**		and how much data there is to write, and the calling thread performs this
	**		operation. Create a new thread to write out data if this is a problem.
	**	
	**	@param writeBuffer Data to send to the server. Make sure to call flip(); on the buffer prior to this method.
	**/
	public void channelWrite(ByteBuffer writeBuffer)
	{
		long nbytes = 0;
		long toWrite = writeBuffer.remaining();
		
		try
		{
			while (nbytes != toWrite)
			{
				nbytes += channel.write(writeBuffer);
			}
			
			try
			{
				Thread.sleep(WRITE_NAP_TIME);
			}
			catch (InterruptedException ie)
			{
				
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
}









