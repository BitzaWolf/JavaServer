package convivial.communism.server;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;

/**
**	Represents a class wanting to listen to input from a server. Be sure to also see ServerConnection in either
**		the server.tcp package or the udp (if it ever gets made).
**	
**	<h3>Imports:</h3>
**	<ul>
**		<li>import java.nio.channels.SelectionKey;</li>
**		<li>import java.nio.channels.SocketChannel;</li>
**		<li>import java.nio.ByteBuffer;</li>
**	</ul>
**/
public interface ServerListener
{
	/**
	**	Handles the result of being disconnected, either from a Server's end or from the Client's.
	**/
	public void disconnected();
	
	/**
	**	Manages recently arrived data from the server. Call <code>key.attachment()</code> to
	**		obtain the Object attached to the channel.
	**	
	**	@param key The Server's channel and its attachment.
	**	@param channel The server's channel.
	**	@param bytesRead Number of bytes read into the ByteBuffer.
	**	@param data Actual data transmitted.
	**/
	public void receiveData(SelectionKey key, SocketChannel channel, long bytesRead, ByteBuffer data);
}