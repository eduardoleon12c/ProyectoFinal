import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class TrackerProcessConnection extends Thread {
	static ByteBuffer buf;
	int port; //puerto del cliente de este thread
	ClientObj client;
	boolean shutdown_normally = false;
	ServerSocket serverTcpSocket;
	Socket clientSocket;
	OutputStream out;
	InputStream in; 
	List<ClientObj> clients; //array de todos los lcientes
	List<TrackerFwding> peer_threads; //los threads para los clientes
	
	public TrackerProcessConnection(ServerSocket socket, 
			List<ClientObj> clients, ClientObj client) {
		this.client = client;
		this.clients = clients;
	    serverTcpSocket = socket;
	    peer_threads = new ArrayList<TrackerFwding>();
	}
	

	@Override
	public void run(){
		InetAddress IPAddress = serverTcpSocket.getInetAddress();
		for (ClientObj c: clients)
			if (c.getId() == client.getId()){
				System.out.print(IPAddress + " cliente conectado!");
				return;
			}
		try{
			// abre la conexion
		    clientSocket = null;
		    Socket clientListener = null;
		    // espera la conexion de otro cliente
	    	System.out.println (" Esperando conexiones.....");
	    	clientSocket = serverTcpSocket.accept();
	    	clientListener = serverTcpSocket.accept();
	    	client.setListenerSocket(clientListener);
			IPAddress = clientSocket.getInetAddress();
			int recv_port = clientSocket.getPort();
			System.out.println(IPAddress + ": cliente conectado, puerto: " + recv_port);

			clientSocket.setSoTimeout(Constants.ACK_TIMEOUT);
		    out = new BufferedOutputStream(clientSocket.getOutputStream());
			in = new BufferedInputStream(clientSocket.getInputStream());
			client.setAddress(clientSocket.getInetAddress());
			client.setPort(clientSocket.getPort());
		    clients.add(client);
			
		    // manejo de peticiones
			while (!shutdown_normally) {
				// primero lee el header y despues redirecciona a cada metodo
				if ((buf = Utility.readIn(in, Constants.HEADER_LEN)) == null)
					break;
				int flag = buf.getInt(0);
				int len2 = buf.getInt(4);
				int id = buf.getInt(8);
				if (flag < Constants.OPEN_CONNECTION || 
						flag > Constants.NUM_FLAGS || 
						id != client.getId() || 
						len2 < 0){
					out.close(); 
					in.close(); 
					clientSocket.close(); 
					serverTcpSocket.close();
					clients.remove(client);
					throw new RuntimeException(
							"conexion fallida, header corrupta");
				}
				
				// lee el paquete
				if ((buf = Utility.readIn(in, Utility.getPaddedLength(len2))) == null)
					throw new IOException("lectura fallida.");
				// verifica la ip
				clients.get(clients.indexOf(client)).setAddress(clientSocket.getInetAddress());
				client.setAddress(clientSocket.getInetAddress());
				switch (flag){
					case Constants.ACK:
						break;
					case Constants.VIEW_REQ:
						System.out.println(client.getAddress() + 
								": procesando peticion de archivos");
						ViewFiles(buf);
						break;
					case Constants.DOWNLOAD_REQ:
						System.out.println(client.getAddress() + 
								": procesando peticion de descarga");
						Download(buf);
						break;
					case Constants.UPLOAD_REQ:
						System.out.println(client.getAddress() + 
								": procesando peticion de subida");
						Upload(buf);
						break;
					case Constants.DOWNLOAD_ACK:
						System.out.println(client.getAddress() + 
								": procesando verificacion de archivo");
						DownloadCompleted(buf);
						break;
					case Constants.CLOSE_CONNECTION:
						shutdown_normally = true;
					default:
						break;
				}
			}
			//finaliza la conexion
		    out.close(); 
		    in.close(); 
		    clientSocket.close(); 
		    serverTcpSocket.close();
		} catch (SocketTimeoutException e) {
			System.err.println(IPAddress + ": esperando respuestas.");
		} catch (UnknownHostException e) {
			System.err.println("sin cliente en " + IPAddress);
		} catch (IOException e) {
			System.err.println("no se pueden obtener Streams de I/O  " +
					IPAddress);
		} catch (RuntimeException e){
			System.err.println(IPAddress + ": conexion fallida (" + e.getMessage() + ")");
		} finally {
			// elimina el peer de los activos
			System.out.println(IPAddress + ": conexion fallida");
			clients.remove(client);
			try {
				Socket clientConnection = client.getListenerSocket();
				OutputStream out_c = new BufferedOutputStream(clientConnection.getOutputStream());
				buf = Utility.addHeader(Constants.CLOSE_CONNECTION, 0, client.getId());
				out_c.write(buf.array());
				out_c.flush();
				out_c.close();
				clientConnection.close();

				if (out != null)
					out.close();
				if (in != null)
					in.close();
				if (!clientSocket.isClosed())
					clientSocket.close();
				if (!serverTcpSocket.isClosed())
					serverTcpSocket.close();
			} catch (IOException e){
				return;
			}
		}
   }

	private void DownloadCompleted(ByteBuffer packet) throws RuntimeException, IOException{ //verifica la descarga completa del peer que la solicito y lo agrega como seeder
		if (packet.capacity() < 4)
			throw new RuntimeException(
					"archivo no especificado correctamente");
		int file_id = packet.getInt(0);
		for (TrackerFile f : totalFiles().keySet()){
			if (f.id() == file_id){
				client.addUpload(f);
				// construct response
				String send_name = f.name() + '\0';
				buf = Utility.addHeader(Constants.DOWNLOAD_ACK, send_name.length(), client.getId());
				
				for (int i = 0; i < send_name.length(); i++){
					buf.put(Constants.HEADER_LEN + i, send_name.getBytes()[i]);
				}
				out.write(buf.array());
				out.flush();
				System.out.println(client.getAddress() + 
						": descarga procesada correctamente para: " + file_id);
				return;
			}
		}
		throw new RuntimeException("file id incorrecto");
	}
	
	private void ViewFiles(ByteBuffer packet) throws IOException{ //le devuelve al cliente la lista de archivos disponibles para descargar
		Map<TrackerFile, Integer> files = totalFiles();
		if (packet.capacity() < 4)
			throw new RuntimeException(
					"pagina incorrecta");
		
		int page = packet.getInt(0);
		
		int num_to_send = files.size();
		if (files.size() > (Constants.PAGE_SIZE * page))
			num_to_send = Constants.PAGE_SIZE;
		else if (files.size() > (Constants.PAGE_SIZE * (page - 1)))
			num_to_send = files.size() % Constants.PAGE_SIZE;
		else {
			num_to_send = files.size() % Constants.PAGE_SIZE;
			page = (files.size() / Constants.PAGE_SIZE) + 1;
		}
			
		buf = Utility.addHeader(1, 8, client.getId());
		buf.putInt(Constants.HEADER_LEN, num_to_send);
		buf.putInt(Constants.HEADER_LEN + 4, files.size());
		byte[] sendData = buf.array();
		out.write(sendData);
		out.flush();
		Iterator<TrackerFile> it = files.keySet().iterator();
		for (int count = 0; count < ((page - 1) * Constants.PAGE_SIZE); count++)
			it.next();
		for (int count = 0; count < num_to_send; count++) {
			TrackerFile f = it.next();
			String sent_name = f.name() + '\0';
			buf = Utility.addHeader(1, sent_name.length() + 16, client.getId());
			buf.putLong(Constants.HEADER_LEN, f.size());
			buf.putInt(Constants.HEADER_LEN + 8, files.get(f)); 
			buf.putInt(Constants.HEADER_LEN + 12, f.id());
			byte[] sent_name_bytes = sent_name.getBytes();
			for (int i = 0; i < sent_name.length(); i++)
				buf.put(Constants.HEADER_LEN + 16 + i, sent_name_bytes[i]);
			sendData = buf.array();
			out.write(sendData);
			out.flush();
		}		
		System.out.println(client.getAddress() + ": peticion de archivo procesada correctamente");
	}

	private void Upload(ByteBuffer packet) throws RuntimeException, IOException{ //verifica la subida de archivos y el rol de cada cliente en dicho archivo
		if (packet.capacity() < 4)
			throw new RuntimeException(
					"nombre de archivo subido incorrecto");
		long buf_size = packet.getLong(0);
		String buf_name = "";
		for (int i = 0; i < buf.capacity(); i++){
			if (buf.get(8 +i) == '\0')
				break;
			buf_name += (char)buf.get(8 + i);
		}
		if (buf_name.length() == 0)
			throw new RuntimeException(
					"nombre de archivo subido incorrecto...");
		
		int id = buf_name.hashCode() + (int)buf_size;
		boolean add = true;
		for (ClientObj c : clients)
			if (c.hasFile(id)) // verifica que no este el id del archivo, si esta duplicado entonces ya no lo agrega
				add = false;
		byte exists =  0;
		if (add)
			client.addUpload(new TrackerFile(buf_name, buf_size, id));
		else {
			for (TrackerFile f : totalFiles().keySet())
				if (f.id() == id)
					client.addUpload(f);
			exists = 1;
		}
		System.out.println(client.getAddress() + ": peticion de subida procesada correctamente " + buf_name 
				+ " tamaño: " + buf_size + " bytes, id: " + id);
		
		buf = Utility.addHeader(3, 5, client.getId());
		buf.put(Constants.HEADER_LEN, exists);
		buf.putInt(Constants.HEADER_LEN + 1, id);
		byte[] sendData = buf.array();
		out.write(sendData);
		out.flush();
	}

	private void Download(ByteBuffer packet) throws IOException { //maneja las peticiones de descarga de los clientes, redireccionando con los seeders disponibles
		for (TrackerFwding f : peer_threads){
			if (f.isAlive())
				f.interrupt();
		}
		peer_threads.clear();
		if (packet.capacity() < 4)
			throw new RuntimeException(
					"peticion de descarga incorrecta");
		
		int buf_id = packet.getInt(0);
		
		Map<ClientObj, Integer> clients_with_file = new HashMap<ClientObj, Integer>();
		for (ClientObj c : clients){
			if (c.hasFile(buf_id) && c.getId() != client.getId())
				clients_with_file.put(c, 0);
		}
		
		// prepara los puertos de los clientes para verificar disponibilidad
		for (ClientObj c : clients_with_file.keySet()) {
			Socket peerConnection = c.getListenerSocket();
			OutputStream out_c = new BufferedOutputStream(peerConnection.getOutputStream());
			
			//construye el paquete de informacion
			int peer_server_port = Utility.getValidPort();
			buf = Utility.addHeader(Constants.PREPARE, 12, c.getId());
			buf.putInt(Constants.HEADER_LEN, buf_id);
			buf.putInt(Constants.HEADER_LEN + 4, client.getId());
			buf.putInt(Constants.HEADER_LEN + 8, peer_server_port);
			byte[] sendData = buf.array();
			out_c.write(sendData);
			out_c.flush();
			System.out.println(client.getAddress() + 
					": enviando datos a peer (puerto: " +peer_server_port+").");
			
			try {
				int client_server_port = Utility.getValidPort();
				TrackerFwding peer_thread = new TrackerFwding(
						client_server_port, c.getId(), peer_server_port, buf_id);
				peer_thread.start();
				
				// envia la informacion del seeder
				buf = Utility.addHeader(Constants.DOWNLOAD_REQ, 8, client.getId());
				buf.putInt(Constants.HEADER_LEN, client_server_port);
				buf.putInt(Constants.HEADER_LEN + 4, c.getId());
				sendData = buf.array();
				out.write(sendData);
				out.flush();
				System.out.println(client.getAddress() + 
						": enviando datos a peer (puerto: " +client_server_port+").");
				peer_threads.add(peer_thread);
			} catch (IOException e){
				System.err.println(client.getAddress()+ 
						": conexion fallida " + e.getMessage());
			} catch (RuntimeException e){
				System.err.println(client.getAddress()+ 
						": conexion fallida " + e.getMessage());
			}
		}
		
		buf = Utility.addHeader(Constants.DOWNLOAD_REQ, 8, client.getId());
		buf.putInt(Constants.HEADER_LEN, 0);
		buf.putInt(Constants.HEADER_LEN + 4, 0);
		out.write(buf.array());
		out.flush();
		
		System.out.println(client.getAddress() + 
				": peticion de descarga procesada correctamente " + buf_id);
	}
	
	@Override
	public void interrupt() { //cierra las conexiones
		shutdown_normally = true;
		try {
			for (TrackerFwding f : peer_threads){
				if (f.isAlive())
					f.interrupt();
			}
			
			if (clients.contains(client)){
				Socket clientConnection = client.getListenerSocket();
				OutputStream out_c = new BufferedOutputStream(clientConnection.getOutputStream());
				buf = Utility.addHeader(Constants.CLOSE_CONNECTION, 0, client.getId());
				out_c.write(buf.array());
				out_c.flush();
				out_c.close();
				clientConnection.close();
				clients.remove(client);
			}
			clientSocket.close();
			serverTcpSocket.close();
		} catch (IOException e) {
			System.err.println(client.getAddress() + ": IOExection");
		}
	}

	private Map<TrackerFile, Integer> totalFiles(){ //calcula el total de archivos para manejar la lista de estos
		Map<TrackerFile, Integer> ret = new HashMap<TrackerFile, Integer>();
		for (ClientObj c : clients){
			for (TrackerFile f : c.getFiles()){
				if (!ret.containsKey(f))
					ret.put(f, 1);
				else
					ret.put(f, ret.get(f) + 1);
			}
		}
		return ret;
	}
}

