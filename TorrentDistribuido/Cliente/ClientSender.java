import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;

public class ClientSender extends Thread{
	private boolean shutdown_normally = false; 
    Socket peer_connection;
	ByteBuffer buf;	
	int id;	
	int peer_id;
	String filepath;
	int file_id;
	

	public ClientSender(int id, String filepath, int file_id, 
			int peer_id, InetAddress addr, int port) throws IOException{
		peer_connection = new Socket(addr, port);
		this.id = id;
		this.file_id = file_id;
		this.filepath = filepath;
		this.peer_id = peer_id;
	}

	@Override
	public void interrupt() {
		shutdown_normally = true;
		try {
			peer_connection.close();
		} catch (IOException e) {
			return;
		}
	}
	

	private void readAndSend(InputStream in, OutputStream out, 
			ByteBuffer buf) throws IOException, RuntimeException{
		
		int buf_file_id = buf.getInt(0);
		int start_index = buf.getInt(4);
		int stop_index = buf.getInt(8);
		if (buf_file_id != file_id){
			in.close();
			out.close();
			throw new RuntimeException("peticion de peer incorrecta");
		}
		int len = stop_index - start_index;
		
		//lee el achivo
		System.out.println("leyendo archivo desde: " + start_index +" hacia "+ stop_index);
		FileInputStream file_in = new FileInputStream(filepath);
		if (start_index != file_in.skip(start_index)){
			in.close();
			out.close();
			file_in.close();
			throw new IOException("Error de lectura, archivo:" + filepath);
		}
		if ((buf=Utility.readIn(file_in, len)) == null){
			in.close();
			out.close();
			file_in.close();
			throw new IOException("lectura fallida.");
		}
		byte[] fileData = buf.array();
		
		System.out.print("Enviando datos de archivo a peer: " + peer_connection.getInetAddress() +"...");
		buf = Utility.addHeader(Constants.PEER, len + 8, id); 
		buf.putInt(Constants.HEADER_LEN, file_id);
		buf.putInt(Constants.HEADER_LEN + 4, start_index);
		for (int i = 0; i < fileData.length; i++){
			buf.put(Constants.HEADER_LEN +8+ i, fileData[i]);
		}
		out.write(buf.array());
		out.flush();
		System.out.print("done!\n");
	}
	
	@Override
	public void run() {
		try {
			peer_connection.setSoTimeout(Constants.PEER_TIMEOUT);
			InputStream in = new BufferedInputStream(peer_connection.getInputStream()); 
			OutputStream out = new BufferedOutputStream(peer_connection.getOutputStream());
	    	
			buf = Utility.addHeader(Constants.PREPARE, 8, id);
			buf.putInt(Constants.HEADER_LEN, file_id);
			buf.putInt(Constants.HEADER_LEN + 4, peer_id);
			out.write(buf.array());
			out.flush();
			while(!shutdown_normally){  
				System.out.println("Esperando solicitud de Peer: " + peer_connection.getInetAddress() + "...");
				if ((buf=Utility.readIn(in, Constants.HEADER_LEN)) == null) {
					out.close();
					in.close();
					throw new IOException("lectura fallida.");
				}
				
				System.out.println("peticion de :" + peer_connection.getInetAddress() + "..recivida!");
				int flag = buf.getInt(0);
				if (flag == Constants.CLOSE_CONNECTION){
                                    //header de cierre de conexion
					if (!Utility.checkHeader(buf, Constants.CLOSE_CONNECTION, 0, peer_id)){
						in.close();
						out.close();
						throw new RuntimeException("header corrupta de peer: " + 
								peer_connection.getInetAddress());
					}
					shutdown_normally = true;
					break;
				} else if (flag == Constants.PEER){
                                    //envio de datos
					System.out.println("procesando peticion de peer...");
					if (buf.getInt(8) != peer_id){
						in.close();
						out.close();
						throw new RuntimeException("header corrupta de peer :" + 
								peer_connection.getInetAddress());
					}
					if ((buf = Utility.readIn(in, 12)) == null) {
						out.close();
						in.close();
						throw new IOException("lectura fallida.");
					}
					readAndSend(in, out, buf); //envio
				} else
					throw new RuntimeException("header corrupta del peeer :" + 
							peer_connection.getInetAddress());
			}
			out.close();
			in.close();
	    } catch (UnknownHostException e) {
			if (!shutdown_normally)
			    System.err.println("Peer - fallo de conexion " + peer_connection.getInetAddress() + 
			    		" (" + e.getMessage() + ")");
		} catch (SocketException e) {
			if (!shutdown_normally)
				System.err.println("Peer - fallo de conexion " + peer_connection.getInetAddress() + 
						" (" + e.getMessage() + ")");
		} catch (IOException e) {
			if (!shutdown_normally)
				System.err.println("Peer - fallo de conexion " + peer_connection.getInetAddress() + " (" +
						e.getMessage()+")");
		} catch (RuntimeException e) {
			if (!shutdown_normally)
				System.err.println("Peer - fallo de conexion " + peer_connection.getInetAddress() + " (" +
						e.getMessage()+")");
		} finally {
			System.out.println("Cerrando conexion de peer: " + peer_connection.getInetAddress());
			try {
				if (!peer_connection.isClosed())
					peer_connection.close();
			} catch (IOException e) {
				return;
			}
		}
	}
}

