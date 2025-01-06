import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClientConnection extends Thread{
	private boolean shutdown_normally = false;
	List<ClientSender> threadpool;
    Socket tracker_connection;
    ByteBuffer buf;
	int id;
	private Map<Integer, String> uploaded_files;

	public void setId(int id){
		this.id = id;
	}

	public ClientConnection(Map<Integer, String> uploaded_files) {
		threadpool = new ArrayList<ClientSender>();
		id = 0;
		this.uploaded_files = uploaded_files;
	}

	public void setupConnection(InetAddress addr, int port) throws IOException {
		tracker_connection = new Socket(addr, port);
	}
	
	@Override
	public void interrupt() {
		shutdown_normally = true;
		for (ClientSender c : threadpool){
			c.interrupt();
		}
		try {
			tracker_connection.close();
		} catch (IOException e) {
			return;
		}
	}
	

	@Override
	public void run() {
		try {   
			tracker_connection.setSoTimeout(0);
			OutputStream out = new BufferedOutputStream(tracker_connection.getOutputStream());
			InputStream in = new BufferedInputStream(tracker_connection.getInputStream());
			while(!shutdown_normally){  
				if ((buf = Utility.readIn(in, Constants.HEADER_LEN)) == null) {
					continue;
				}
				if (buf.getInt(0) == Constants.CLOSE_CONNECTION && buf.getInt(8) == id){
					//cierra conexion
					break;
				}
				if (buf.getInt(0) == Constants.PREPARE && buf.getInt(8) == id){
					// lee
					if ((buf = Utility.readIn(in, 12)) == null) {
						out.close();
						in.close();
						throw new IOException("lectura fallida.");
					}
					int file_id = buf.getInt(0);
					int peer_id = buf.getInt(4);
					int tracker_port = buf.getInt(8);

					// thead de escritura/lectura
					String filepath = uploaded_files.get(file_id);
					if (filepath == null){
						in.close();
						out.close();
						throw new RuntimeException("Archivo no disponible por peers.");
					}
					ClientSender c1 = new ClientSender(
							id, filepath, file_id, peer_id, tracker_connection.getInetAddress(), tracker_port);
					threadpool.add(c1);
					c1.start();
					System.out.println("abriendo conexion para subir archivo con id: " + 
							file_id + " puerto: " + tracker_port);
				}	
			}
			in.close();
			out.close();
	    } catch (Exception e) {
			if (!shutdown_normally)
			    System.err.println("Lectura - fallida! (" + e.getMessage() + ")");
			throw new RuntimeException(e.getMessage()); // cierra el cliente
		} finally {
			try{
				if (tracker_connection != null)
					tracker_connection.close();
			} catch (IOException e){
				return;
			}
		}
	}
}

