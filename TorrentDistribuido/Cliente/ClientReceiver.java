import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;

public class ClientReceiver extends Thread{
	private boolean shutdown_normally = false;
    ByteBuffer buf;
	int id;	//id del cliente que descarga
	int peer_id; //id del cliente que envia
	String filepath;
	int file_id; //id del archivo
	Socket peer_connection;	
	public int start_index;	//index de la parte del archivo solicitada
	public int stop_index;	//index de fin de la parte solicitada
	public int bytes_written; 
	
	public ClientReceiver(int id, String filepath, int file_id, int peer_id, Socket peer) {
		this.id = id;
		this.file_id = file_id;
		this.filepath = filepath;
		this.peer_id = peer_id;
		this.peer_connection = peer;
	}
	
	public void setInterval(int start_index, int stop_index){
		this.start_index = start_index;
		this.stop_index = stop_index;
	}
	
	@Override
	public void interrupt() {
		shutdown_normally = true;
		try {
		if (!peer_connection.isClosed())
			peer_connection.close();
		} catch (IOException e) {
			return;
		}
	}
	
	@Override
	public void run() {
		
		try {
			System.out.println("Esperando peer para envio de datos: " + peer_connection.getInetAddress());
			if (stop_index - start_index == 0)
	    		throw new RuntimeException("BAD INTERVAL");
	    	peer_connection.setSoTimeout(Constants.PEER_TIMEOUT);
	    	OutputStream out = new BufferedOutputStream(peer_connection.getOutputStream());
			InputStream in = new BufferedInputStream(peer_connection.getInputStream());

			buf = Utility.addHeader(Constants.PEER, 12, id);
	    	buf.putInt(Constants.HEADER_LEN, file_id);
	    	buf.putInt(Constants.HEADER_LEN + 4, start_index);
	    	buf.putInt(Constants.HEADER_LEN + 8, stop_index);
	    	out.write(buf.array());
	    	out.flush();
	    	
			while(!shutdown_normally) {
		    	if ((buf=Utility.readIn(in, Constants.HEADER_LEN)) == null){
		    		in.close();
		    		out.close();
		    		throw new IOException("lectura fallida.");
		    	}
		    	if (buf.getInt(0) == Constants.CLOSE_CONNECTION && buf.getInt(8) != peer_id){
		    		shutdown_normally = true;
		    	} else if (buf.getInt(0) != Constants.PEER || buf.getInt(8) != peer_id) {
		    		in.close();
		    		out.close();
		    		throw new RuntimeException(
		    				"header corrupto de peer: " + peer_connection.getInetAddress());
		    	}
		    	int len = buf.getInt(4);
		    	if ((buf=Utility.readIn(in, Utility.getPaddedLength(len))) == null) {
		    		in.close();
		    		out.close();
		    		throw new IOException("lectura fallida.");
		    	}
		    	if (buf.getInt(0) != file_id || buf.getInt(4) != start_index) {
		    		in.close();
		    		out.close();
		    		throw new RuntimeException(
		    				"datos corruptos de peeer: " + peer_connection.getInetAddress());
		    	}
		    	System.out.println("datos recividos de peer: inicio: "
		    		+start_index +", stop: "+stop_index +" @" + peer_connection.getInetAddress());
		    	byte[] data = new byte[len - 8];
		    	for (int i = 0; i < len - 8; i++){
		    		data[i] = buf.get(i + 8);
		    	}
                        //descarga finalizada
		    	bytes_written = FileWriter.getInstance().writeToFile(filepath, data, start_index);
		    	shutdown_normally = true; 
			}
	    } catch (UnknownHostException e) {
			if (!shutdown_normally)
			    System.err.println("Peer - fallo de conexion " + 
			peer_connection.getInetAddress() + " (" + e.getMessage() + ")");
		} catch (SocketException e) {
			if (!shutdown_normally)
				System.err.println("Peer - fallo de conexion " + 
			peer_connection.getInetAddress() + " (" + e.getMessage() + ")");
		} catch (IOException e) {
			if (!shutdown_normally)
				System.err.println("Peer - fallo de conexion " + 
			peer_connection.getInetAddress() + " (" + e.getMessage()+")");
		} catch (RuntimeException e){
			if (!shutdown_normally)
				System.err.println("Peer - fallo de conexion " + 
						peer_connection.getInetAddress() + " (" + e.getMessage()+")");
		} finally {
			System.out.println("cerrando conexion de peer :" + peer_connection.getInetAddress());
			if (!peer_connection.isClosed())
				try {
					peer_connection.close();
				} catch (IOException e) {
					return;
				}
		}
	}
}
