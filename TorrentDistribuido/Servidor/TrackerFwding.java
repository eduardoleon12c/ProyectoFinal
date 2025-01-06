import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

public class TrackerFwding extends Thread {
	Socket peer_connection;	
	ServerSocket peer_tracker;	
	Socket client_download_connection;
	ServerSocket client_tracker;
	ByteBuffer buf;	
	int client_tracker_port;
	int peer_id;
	int client_id;	
	int peer_port;	
	int file_id;	

	public TrackerFwding(int client_tracker_port, int id, int peer_port, int file_id)  {
		this.client_tracker_port = client_tracker_port;
		this.peer_port = peer_port;
		this.peer_id = id;
		this.file_id = file_id;
	}
	
	private void closeConnection(InputStream in, OutputStream out, Socket connection, int id) throws IOException {
		buf = Utility.addHeader(Constants.CLOSE_CONNECTION, 0, id);
		out.write(buf.array());
		out.flush();
		out.close();
		in.close();
		connection.close();
		System.out.println(" (envio) cerrando conexion.");
	}
	
	@Override
	public void run() { //envio de peers para su comunicacion
		try {
			//espera la conexion de el peer con el tracker
			peer_tracker = new ServerSocket(peer_port);
			client_tracker = new ServerSocket(client_tracker_port);
			peer_tracker.setSoTimeout(Constants.PEER_TIMEOUT);
			client_tracker.setSoTimeout(Constants.PEER_TIMEOUT);
			
			try {
				peer_connection = peer_tracker.accept();
			} catch(SocketTimeoutException e){
				// termina el tiempo de espera y finaliza la conexion
				client_download_connection = client_tracker.accept();
				InputStream in_c = new BufferedInputStream(
						client_download_connection.getInputStream()); 
				OutputStream out_c = new BufferedOutputStream(
						client_download_connection.getOutputStream());
				closeConnection(in_c, out_c, client_download_connection, peer_id);
				throw e;
			}
			// recive la verificacion del cliente (ack)
			OutputStream out_p = new BufferedOutputStream(peer_connection.getOutputStream());
			InputStream in_p = new BufferedInputStream(peer_connection.getInputStream());
			
			if ((buf = Utility.readIn(in_p, Constants.HEADER_LEN + 8)) == null){
				closeConnection(in_p, out_p, peer_connection, client_id);
				throw new IOException("lectura fallida");
			}

			if (!Utility.checkHeader(buf, Constants.PREPARE, 8, peer_id)){
				closeConnection(in_p, out_p, peer_connection, client_id);
				throw new RuntimeException("header corrupta");
			}
			if (file_id != buf.getInt(Constants.HEADER_LEN)){
				closeConnection(in_p, out_p, peer_connection, client_id);
				throw new RuntimeException("archivo incorrecto recivido");
			}
			try {
				client_download_connection = client_tracker.accept();
			} catch (SocketTimeoutException e){
				closeConnection(in_p, out_p, peer_connection, client_id);
				throw e;
			}
			InputStream in_c = new BufferedInputStream(
					client_download_connection.getInputStream()); 
			OutputStream out_c = new BufferedOutputStream(
					client_download_connection.getOutputStream());
			
			// Cliente conectado comienza el envio de paquetes
			System.out.println(" (envio) peticion de: " + client_id + 
					" al peer " + peer_id);
			if ((buf =Utility.readIn(in_c, Constants.HEADER_LEN + 12)) == null){
				closeConnection(in_p, out_p, peer_connection, client_id);
				closeConnection(in_c, out_c, client_download_connection, peer_id);
				throw new IOException("lectura fallida");
			}
			client_id = buf.getInt(8);
			out_p.write(buf.array());
			out_p.flush();	
			System.out.println(" (envio) peticion de: " + peer_id + 
					" al peer " + client_id);
			if ((buf =Utility.readIn(in_p, Constants.HEADER_LEN)) == null){
				closeConnection(in_p, out_p, peer_connection, client_id);
				closeConnection(in_c, out_c, client_download_connection, peer_id);
				throw new IOException("lectura fallida");
			}
			out_c.write(buf.array());
			if ((buf =Utility.readIn(in_p, buf.getInt(4))) == null){
				closeConnection(in_p, out_p, peer_connection, client_id);
				closeConnection(in_c, out_c, client_download_connection, peer_id);
				throw new IOException("lectura fallida");
			}
			out_c.write(buf.array());
			out_c.flush();
			
			// cierra conexiones
			closeConnection(in_p, out_p, peer_connection, client_id);
			
			closeConnection(in_c, out_c, client_download_connection, peer_id);
		} catch (IOException e) {
			System.err.println(" (envio) conexion fallida de peer: " + e.getMessage());
		} catch (RuntimeException e) {
			System.err.println(" (envio) conexion fallida de peer: " + e.getMessage());
		} finally {
			try {
				if (!client_tracker.isClosed())	
					client_tracker.close();
				if (!peer_tracker.isClosed())
					peer_tracker.close();
			} catch (IOException e) {
				return;
			}
		}
	}
}
