import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Scanner;


public class Client {
	static InetAddress IPAddress;	 			// Direccion del Tracker
	static int port;	// puerto del Tracker
	static ByteBuffer buf;						// para el manejo de datos
	static int id;								// id de cada cliente
	static int page_number = 0;					// numero de paginas de archivos
	static Socket tcpSocket;					// socket del tracker
	static int num_files_on_tracker = 0;			// numero de torrents que hay en el tracker
	static boolean shutdown_normally = false;	// para verificar si se cerro correctamente la coneccion
	static Timer timer;							// tiempo de conexion activa
	static Random r = new Random();				// Genera el puerto aleatorio del cliente (tal vez aqui pueda crashear debido a que no se sabe si el puerto esta ocupado)
	static ClientConnection sideListener;		// manejaremos las conexiones con los otros peers independienetes
        
	private static Map<Integer, String> uploaded_files;		// archivos: <idArchivo, filepath>
	private static Map<Integer, Integer> retrieved_files;	// ids de archivos: <id , idArchivo> 
	private static Map<Integer, Long> retrieved_file_sizes;	// tamaños de archivos: <idArchivo, tamaño>
	
	
	public static void main(String args[]) {
        Scanner inDatos = new Scanner(System.in);
        uploaded_files = Collections.synchronizedMap(new HashMap<>());
        sideListener = new ClientConnection(uploaded_files);
        try {
            System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
            System.out.println("\u2551    Bienvenido al Cliente BitTorrent    \u2551");
            System.out.println("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");

            System.out.print("Dame la direccion del Tracker: ");
            IPAddress = InetAddress.getByName(inDatos.next());
            System.out.print("Dame el puerto del Tracker: ");
            int port_number = inDatos.nextInt();

            System.out.println("Conectando con el Tracker...");
            tcpSocket = new Socket(IPAddress, port_number);
            tcpSocket.setSoTimeout(10000);
            OutputStream out = new BufferedOutputStream(tcpSocket.getOutputStream());
            InputStream in = new BufferedInputStream(tcpSocket.getInputStream());

            buf = Utility.addHeader(Constants.OPEN_CONNECTION, Constants.GREETING.length(), 0);
            byte[] greetng_bytes = Constants.GREETING.getBytes();
            for (int i = 0; i < Constants.GREETING.length(); i++)
                buf.put(Constants.HEADER_LEN + i, greetng_bytes[i]);
            out.write(buf.array());
            out.flush();
            System.out.println("Enviando... listo!");

            if ((buf = Utility.readIn(in, Constants.HEADER_LEN + 8)) == null) {
                out.close();
                in.close();
                tcpSocket.close();
                throw new IOException("Lectura fallida.");
            }

            if (!Utility.checkHeader(buf, Constants.OPEN_CONNECTION, 8, 0)) {
                out.close();
                in.close();
                tcpSocket.close();
                throw new RuntimeException("Header corrupto desde el tracker!");
            }

            id = buf.getInt(Constants.HEADER_LEN);
            int tcp_port = buf.getInt(Constants.HEADER_LEN + 4);
            out.close();
            in.close();
            tcpSocket.close();

            System.out.println("Estableciendo conexiones con el Tracker... listo!");
            tcpSocket = new Socket(IPAddress, tcp_port);
            sideListener.setupConnection(IPAddress, tcp_port);
            sideListener.setId(id);
            sideListener.start();
            out = new BufferedOutputStream(tcpSocket.getOutputStream());
            in = new BufferedInputStream(tcpSocket.getInputStream());
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

            System.out.println("ID asignado por el Tracker: " + id);
            mostrarMenu();

            while (sideListener.isAlive()) {
                timer = new Timer();
                Acker ack = new Acker(out, tcpSocket, id);
                timer.scheduleAtFixedRate(ack, 0, Constants.ACK_TIMEOUT / 2);
                String userInput = stdIn.readLine().trim();
                timer.cancel();

                switch (userInput.toLowerCase()) {
					case "4":
                    case "exit":
                        shutdown_normally = true;
                        break;
					case "1":
					case "archivos":
                        retrieveFiles(out, in, 1);
                        page_number = 1;
                        break;
                    case "next":
                        if (num_files_on_tracker > page_number * Constants.PAGE_SIZE) {
                            page_number++;
                            retrieveFiles(out, in, page_number);
                        } else {
                            System.out.println("No hay más páginas disponibles.");
                        }
                        break;
                    case "ant":
                        if (page_number > 1) {
                            page_number--;
                            retrieveFiles(out, in, page_number);
                        } else {
                            System.out.println("No hay páginas anteriores.");
                        }
                        break;
                    default:
                        if (userInput.startsWith("subir")) {
                            manejarSubida(userInput, out, in);
                        } else if (userInput.startsWith("descargar")) {
                            manejarDescarga(userInput, out, in);
                        } else {
                            System.out.println("Opción no válida. Inténtalo de nuevo.");
                        }
                }
                if (shutdown_normally) break;
                mostrarMenu();
            }

            System.out.println("Cerrando conexión.");
            buf = Utility.addHeader(Constants.CLOSE_CONNECTION, 0, id);
            out.write(buf.array());
            out.flush();
            sideListener.interrupt();
            in.close();
            out.close();
        } catch (Exception e) {
            if (!shutdown_normally) {
                System.err.println("Error: " + e.getMessage());
            }
        } finally {
            if (sideListener.isAlive()) sideListener.interrupt();
            try {
                if (!tcpSocket.isClosed()) tcpSocket.close();
            } catch (IOException ignored) {
            }
        }
		
	}

	private static void mostrarMenu() {
        System.out.println("\n========================= MENÚ DE OPCIONES ========================");
        System.out.println("1. archivos     		- Ver archivos disponibles en la red de peers.");
        System.out.println("2. subir <ruta> 		- Subir un archivo al tracker.");
        System.out.println("3. descargar <número> 		- Descargar un archivo de la red.");
        System.out.println("4. exit         		- Salir del cliente.");
        System.out.println("=====================================================================");
        System.out.print("Seleccione una opción: ");
    }

	private static void mostrarArchivos(OutputStream out, InputStream in) {
        try {
            retrieveFiles(out, in, 1);
            page_number = 1;
            while (true) {
                if (num_files_on_tracker > Constants.PAGE_SIZE) {
                    System.out.println("\n================== NAVEGACIÓN ==================");
                    System.out.println("1. next  - Ver la siguiente página de archivos.");
                    System.out.println("2. ant   - Ver la página anterior de archivos.");
                    System.out.println("3. salir - Regresar al menú principal.");
                    System.out.println("===============================================");
                    System.out.print("Seleccione una opción: ");
                    Scanner scanner = new Scanner(System.in);
                    String opcion = scanner.nextLine().trim().toLowerCase();

                    if (opcion.equals("next")) {
                        if (num_files_on_tracker > page_number * Constants.PAGE_SIZE) {
                            page_number++;
                            retrieveFiles(out, in, page_number);
                        } else {
                            System.out.println("No hay más páginas disponibles.");
                        }
                    } else if (opcion.equals("ant")) {
                        if (page_number > 1) {
                            page_number--;
                            retrieveFiles(out, in, page_number);
                        } else {
                            System.out.println("No hay páginas anteriores.");
                        }
                    } else if (opcion.equals("salir")) {
                        break;
                    } else {
                        System.out.println("Opción no válida. Inténtalo de nuevo.");
                    }
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("Error al recuperar los archivos: " + e.getMessage());
        }
    }

    private static void manejarSubida(String userInput, OutputStream out, InputStream in) {
        if (userInput.length() < 6) {
            System.out.println("Especifica la ruta del archivo.");
            return;
        }
        String filepath = System.getProperty("user.dir") + "/" + userInput.substring(6);
        if (uploaded_files != null && uploaded_files.containsValue(filepath)) {
            System.out.println("El archivo ya ha sido subido.");
        } else {
            try {
                uploadFile(filepath, out, in);
            } catch (IOException e) {
                System.out.println("Error al subir el archivo: " + e.getMessage());
            }
        }
    }

    private static void manejarDescarga(String userInput, OutputStream out, InputStream in) {
        if (retrieved_files == null) {
            System.out.println("No hay archivos disponibles para descargar.");
            return;
        }
        try {
            String[] splitted = userInput.split(" ");
            if (splitted.length != 2) throw new NumberFormatException();
            int number = Integer.parseInt(splitted[1]);
            Integer file_id = retrieved_files.get(number);
            if (uploaded_files != null && uploaded_files.containsKey(file_id)) {
                System.out.println("El archivo ya está descargado.");
            } else if (file_id != null) {
                downloadFile(file_id, out, in, retrieved_file_sizes.get(file_id));
            } else {
                System.out.println("Número de archivo incorrecto.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Entrada inválida. Usa el formato: descargar <número>");
        } catch (IOException e) {
            System.out.println("Error al descargar el archivo: " + e.getMessage());
        }
    }

	private static void uploadFile(String filepath, OutputStream out, 
			InputStream in) throws IOException{	
		File upload = new File(filepath);

		if (!upload.exists()){
			 System.out.println("direccion de archivo invalida: " + filepath);
			return;
		}
		System.out.println("Conectando con el Tracker...");
		String name = upload.getName() + '\0';
		buf = Utility.addHeader(Constants.UPLOAD_REQ, name.length() + 12, id);
		buf.putLong(Constants.HEADER_LEN, upload.length());
		byte[] name_bytes = name.getBytes();
		for (int i = 0; i < name.length(); i++){
			buf.put(Constants.HEADER_LEN + 8 + i, name_bytes[i]);
		}
		out.write(buf.array());
		out.flush();
		
		// respuesta
		if ((buf=Utility.readIn(in, Constants.HEADER_LEN + Utility.getPaddedLength(5))) == null)
			throw new IOException("lectura fallida.");
		byte exists = buf.get(Constants.HEADER_LEN);
		int file_id = buf.getInt(Constants.HEADER_LEN + 1);
		if (exists == 0){
			System.out.println(name + " subido correctamente.");
		} else {
			System.out.println(name + " el archivo ya esta subido");
		}
		uploaded_files.put(file_id, filepath);
	}
	
	private static void downloadFile(int file_id, OutputStream out, 
			InputStream in, long file_size) throws IOException, RuntimeException{
		
		File result = new File(file_id + ".data");
		result.createNewFile();
		
		// lista de peers
		List<ClientReceiver> peers = getPeers(file_id, out, in, result.getAbsolutePath()); 
		if (peers.size() == 0){
			System.out.println("Ningun peer disponible!");
			result.delete();
			return;
		}
		
		// particion piezas y manejo de peers
		List<int[]> unwritten_pieces = new ArrayList<int[]>();
		int piece_size = (int)file_size / peers.size();
		if ((int)file_size % peers.size() != 0)
			piece_size++;
		int count = 0;
		for (ClientReceiver peer : peers){
			peer.setInterval(piece_size*count, piece_size*(count + 1));
			int[] interval = {piece_size*count, piece_size*(count + 1)};
			unwritten_pieces.add(interval);
			peer.start();
			count++;
		}
		
		int tries = 0;
		// verificacion de descarga
		while (tries < 10 && unwritten_pieces.size() != 0){
			tries++;
			while (peers.size() != 0){
				for (ClientReceiver peer : peers){
					if (!peer.isAlive()) {
						int[] interval = {peer.start_index, peer.stop_index};
						for (int i=0; i < unwritten_pieces.size(); i++){
							if (unwritten_pieces.get(i)[1] == interval[1] && 
									unwritten_pieces.get(i)[0] == interval[0]){
								unwritten_pieces.remove(i);
							}
						}
						if (peer.bytes_written == piece_size){
							// descarga de ese peer completa
							peers.remove(peers.indexOf(peer));
							if (unwritten_pieces.size() == 0){
								completeDownload(out, in, file_id, result);
								return;
							}
							break;
						} else {
							// descarga fallida de ese peer
							int[] pieceIncomplete = {peer.start_index + peer.bytes_written, peer.stop_index};
							unwritten_pieces.add(pieceIncomplete);
							peers.remove(peers.indexOf(peer));
							break;
						}
					}
				}
			}
			
			// si se tienen piezas incompletas
			if (unwritten_pieces.size() > 0){
				peers = getPeers(file_id, out, in, result.getAbsolutePath());
				if (peers.size() == 0){
					System.out.println("Abortando descarga, sin peers.");
					result.delete();
					return;
				}
				int incomplete_count = 0;
				for (ClientReceiver peer : peers){
					int[] interval = unwritten_pieces.get(incomplete_count);
					peer.setInterval(interval[0], interval[1]);
					peer.start();
					if (incomplete_count < unwritten_pieces.size() - 1)
						incomplete_count++;
				}
			}
		}
		//cerrar conexion peers
		for (ClientReceiver cr : peers)
			if (cr.isAlive())
				cr.interrupt();
		
		if (tries == 10){
			System.out.println("Abortando descarga, intentos sobrepasados");
			result.delete();
		}
		else
			completeDownload(out, in, file_id, result);
	}
	
	private static void completeDownload(OutputStream out, InputStream in, int file_id, File result) throws IOException{
		timer.cancel(); 
		System.out.println("finalizando descarga...");
		
		buf = Utility.addHeader(Constants.DOWNLOAD_ACK, 4, id);
		buf.putInt(Constants.HEADER_LEN, file_id);
		out.write(buf.array());
		out.flush();

		if ((buf = Utility.readIn(in, Constants.HEADER_LEN)) == null){
			result.delete();
			throw new IOException("lectura fallida.");
		}
		if (buf.getInt(0) != Constants.DOWNLOAD_ACK || buf.getInt(8) != id){
			result.delete();
			throw new RuntimeException("header corrupta del Tracker.");
		}
		int payload_len = buf.getInt(4);
		if ((buf = Utility.readIn(in, Utility.getPaddedLength(payload_len))) == null){
			result.delete();
			throw new IOException("lectura fallida.");
		}
		File name = new File(getNameFromBuf(0, buf));
                //guardar y compartir
		result.renameTo(name);
		uploaded_files.put(file_id, name.getAbsolutePath());
		System.out.println("Descarga completa en: " + name.getAbsolutePath());
	}
	
	private static List<ClientReceiver> getPeers(int file_id, 
		OutputStream out, InputStream in, String path) throws IOException, RuntimeException{
		timer.cancel();
		
		System.out.println("conectando con el Tracker...");
		buf = Utility.addHeader(Constants.DOWNLOAD_REQ, 4, id);
		buf.putInt(Constants.HEADER_LEN, file_id);
		out.write(buf.array());
		out.flush();
		
                timer = new Timer();
                Acker ack = new Acker(out, tcpSocket, id);
                timer.scheduleAtFixedRate(ack, 0, Constants.ACK_TIMEOUT / 2);
		
		// abrir conexion con cada peer
		List<ClientReceiver> peers = new ArrayList<ClientReceiver>();
		
		while (true) {

			if ((buf = Utility.readIn(in, Constants.HEADER_LEN)) == null)
				throw new IOException("lectura fallida.");
			if (buf.getInt(0) != Constants.DOWNLOAD_REQ || buf.getInt(8) != id)
				throw new RuntimeException("header corrupta!");
			int payload_len = buf.getInt(4);
			
			if ((buf = Utility.readIn(in, Utility.getPaddedLength(payload_len))) == null)
					throw new IOException();
			int port_num = buf.getInt(0);
			int peer_id = buf.getInt(4);
                        //verificar si hay mas peers
			if (port_num == 0 && peer_id == 0)
				break;
			
			// crear peer y agregarlo a la lista para descargar
			InetAddress addr = tcpSocket.getInetAddress();
			Socket peer;
			System.out.println("conectando con: " + addr + " puerto:  " + port_num +"!");
			try {
				peer = new Socket(addr, port_num);
			} catch (IOException e){
				System.out.println("conexion fallida.");
				continue;
			}
			ClientReceiver c = new ClientReceiver(id, path, file_id, peer_id, peer);
			peers.add(c);
			System.out.println("conectado a: " + addr + " puerto: " + port_num +"!");
		}
		
		if (peers.size() == 0)
			System.out.println("Sin peers para este archivo.");
		
		return peers;
	}

	private static void retrieveFiles(OutputStream out, InputStream in, int page) throws IOException, RuntimeException{
		System.out.println("conectano con el Tracker...");
		
		buf = Utility.addHeader(Constants.VIEW_REQ, 4, id);
		buf.putInt(Constants.HEADER_LEN, page);
		out.write(buf.array());
		out.flush();

		if ((buf=Utility.readIn(in, Constants.HEADER_LEN + 8)) == null)
			throw new IOException("lectura fallida.");
		
		if (!Utility.checkHeader(buf, Constants.VIEW_REQ, 8, id)){
			throw new RuntimeException("header corrupta del tracker!");
		}
		int num_files = buf.getInt(Constants.HEADER_LEN);
		num_files_on_tracker = buf.getInt(Constants.HEADER_LEN + 4);
		System.out.println("obtenidos " + num_files +"/" +num_files_on_tracker+
				" archivos registrados en el tracker: (pagina #"+page+" de " +(num_files_on_tracker / Constants.PAGE_SIZE + 1)+"):");
		
		//archivos desde el Tracker
		retrieved_files = new HashMap<Integer, Integer>();
		retrieved_file_sizes = new HashMap<Integer, Long>();
		int flag, payload_len, buf_id, num_peers;
		long file_size;
		String name;
		for (int i=0; i < num_files; i++){
			if ((buf=Utility.readIn(in, Constants.HEADER_LEN)) == null)
				throw new IOException("lectura fallida.");
			flag = buf.getInt(0);
			payload_len = buf.getInt(4);
			buf_id = buf.getInt(8);
			if (id != buf_id || flag != Constants.VIEW_REQ)
				throw new RuntimeException("header corrupta desde el Tracker!");
			if ((buf = Utility.readIn(in, Utility.getPaddedLength(payload_len))) == null)
					throw new IOException("lectura fallida.");
			file_size = buf.getLong(0);
			num_peers = buf.getInt(8);
			buf_id = buf.getInt(12);
			name = getNameFromBuf(16, buf);
			if (name.length() == 0)
				throw new RuntimeException(
						"conexion - fallida! (nombre de archivo no se ha subido correctamente)");
			System.out.println((i+1) + ". "+name +", ("+file_size+" bytes), " +num_peers+" peers.");
			retrieved_files.put(i+1, buf_id);
			retrieved_file_sizes.put(buf_id, file_size);
		}
		System.out.println("listo!");
	}

	private static String getNameFromBuf(int index, ByteBuffer buf){
		String name = "";
		for (int j = 0; j < buf.capacity(); j++){
			if (buf.get(index +j) == '\0')
				break;
			name += (char)buf.get(index + j);
		}
		return name;
	}
}

class Acker extends TimerTask{
	OutputStream out;
	int id;		

	public Acker(OutputStream out, Socket tcpSocket, int id){
		super();
		this.out = out;
		this.id = id;
	}

	@Override
	public void run(){
		ByteBuffer buf = Utility.addHeader(Constants.ACK, 0, id);
		try {
			out.write(buf.array());
			out.flush();
		} catch (IOException e){
			this.cancel();
		}
	}
}
