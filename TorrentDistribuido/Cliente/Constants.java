
public interface Constants {
	
	final static int PEER_TIMEOUT = 5000; 		// 5 segundos para la comunicacion constante del peer y su verificacion
	final static int PAGE_SIZE = 10;			// para controlar el numero de elementos mostrados 
	final static int HEADER_LEN = 12; 			// longitud del campo header
	final static String GREETING = "HEADER\0"; 	// el encabezado principal para saber que es un header en la peticion
	static final int ACK_TIMEOUT = 10000;		// tiempo de espera del cliente, si no hay respuesta se cierra conexion
	
	
	// flags
	final static int NUM_FLAGS = 8;			// numero de flags
	final static int PEER = 8;				// para realizar la conexion p2p
	final static int PREPARE = 7;			// se prepara para la conexion
	final int ACK = 6;						// estatus de cliente
	final static int CLOSE_CONNECTION = 5;	// para el cierre de conexion 
	final static int DOWNLOAD_ACK = 4;		// para saber que es el reconocimeinto del cliente
	final static int UPLOAD_REQ = 3;		// para la descarga
	final static int DOWNLOAD_REQ = 2;		// para la carga
	final static int VIEW_REQ = 1;			// para ver los archivos del tracker
	final static int OPEN_CONNECTION = 0;	// para la apertura de conexion
}
