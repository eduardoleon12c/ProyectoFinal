import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


public class TrackerListener extends Thread{
	int port_num; //puerto del tracker
	ServerSocket serverSocket; //socket server principal
	private boolean shutdown_normally = false; 
	List<Thread> threadpool; //para manejar independientemente las conexiones
	public static List<ClientObj> clients; //todos los clientes

	public TrackerListener(int port_num){
		this.port_num = port_num;
		clients = new ArrayList<ClientObj>();
		clients = java.util.Collections.synchronizedList(clients);
		threadpool = new ArrayList<Thread>();
	}
	
	@Override
	public void interrupt() {
		shutdown_normally = true;
		System.out.println("Desconectando Tracker...");
		for (Thread t : threadpool)
			t.interrupt();
		System.out.println("finalizando conexiones actuales...");
		try {
			serverSocket.close();
		} catch (IOException e) {
			return;
		}	
	}
	

	@Override
	public void run() {//espera por nuevas conexiones y las une a los threadpool
	    try {
		    serverSocket = new ServerSocket(port_num);  
		    System.out.println("Tracker - Listo para conexiones en puerto: " + port_num);
			System.out.println("Tracker - A espera de conexiones...");
			while(!shutdown_normally){  
				
				byte[] receiveData = new byte[Utility.getPaddedLength(Constants.HEADER_LEN+Constants.GREETING.length())];
				Socket clientSocket = serverSocket.accept();//espera que alguien se una
				System.out.print(" nueva conexion, procesando...");
				OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream());
				InputStream in = new BufferedInputStream(clientSocket.getInputStream());
				ByteBuffer buf = Utility.readIn(in, receiveData.length);
				if (buf == null)
					continue;
                                
				if (!Utility.checkHeader(buf, Constants.OPEN_CONNECTION, Constants.GREETING.length(), 0))
		        	throw new RuntimeException("Conexion fallida! (Header corrupto)");
		        byte[] greeting_bytes = Constants.GREETING.getBytes();
		        for (int i =0; i < Constants.GREETING.length(); i++)
		        	if (buf.get(Constants.HEADER_LEN + i) != greeting_bytes[i])
		        		throw new RuntimeException("Conexion fallida! (Header corrupto)");        
		        
		        int client_id = clientSocket.getInetAddress().hashCode() * clientSocket.getPort();
		        ClientObj client = new ClientObj(null, 0, client_id);
		        
		        // envia la respuesta al cliente
		        buf = Utility.addHeader(Constants.OPEN_CONNECTION, 8, 0);
		        buf.putInt(Constants.HEADER_LEN, client_id);
			        int thread_port = Utility.getValidPort();
			        try{
			        	ServerSocket thread_socket = new ServerSocket(thread_port);
			        	buf.putInt(Constants.HEADER_LEN + 4, thread_port);
				        out.write(buf.array());
				        out.flush();
				        in.close();
				        out.close();
				        clientSocket.close();
			        	Thread clientProcess = new Thread(new TrackerProcessConnection(thread_socket, clients, client));
			        	threadpool.add(clientProcess); //agrega el cliente al pool de clientes
			        	clientProcess.start();
			        	System.out.print("hecho.\n");
			        } catch (IOException e) {
			        	System.out.println("conexion fallida...!");
			        }
			} //se repite para agregar mas clientes
	    } catch (Exception e){
			if (!shutdown_normally){
				System.err.println("Fallo en el Tracker! (" + e.getMessage() + ")");
			}
			System.exit(1);
		} finally {
			try {
			if (serverSocket != null && !serverSocket.isClosed())
				 serverSocket.close();
			} catch (IOException e){
				System.exit(0);
			}
		}
	}	
}

