import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

public class Utility {
	static Random r = new Random();		//utilizaremos puertos random

	public static ByteBuffer readIn(InputStream in, int len){ //va a leer el input stream para el numero especificado de bytes (len)
		try{
			ByteBuffer buf = ByteBuffer.allocate(len);
			byte[] receiveData = new byte[len];
			int read;
			int left_to_read = len;
			while (left_to_read > 0 && (read = in.read(receiveData, len - left_to_read, left_to_read)) != -1)
				left_to_read -= read;
			if (left_to_read != 0)
				return null;
			buf.put(receiveData);
			return buf;
		} catch (IOException e){
			return null;
		}
	}
	
	public static int getValidPort() { //nos da un puerto valido, el cliclo continuara hasta que este uno disponible
		int thread_port;
		while (true){
			thread_port = r.nextInt(20000) + 40000;
	        try{
	        	ServerSocket try_socket = new ServerSocket(thread_port);
	        	try_socket.close();
	        	return thread_port;
	        } catch (IOException e) {
	        	continue;
	        }
        }
	}
	
	public static boolean checkHeader(ByteBuffer packet, int flag, int len, int id){ //verifica que el paquete que llega utiliza el header adecuadamente
		int buf_flag = packet.getInt(0);
		int buf_len = packet.getInt(4);
		int buf_id = packet.getInt(8);

		if (	buf_len != len ||
				buf_flag > Constants.NUM_FLAGS || 
				buf_flag < 0 || 
				buf_len < 0 ||
				buf_id != id) {
			return false;
		}
		
		if ((packet.capacity() - Constants.HEADER_LEN) != getPaddedLength(buf_len))
			return false;
		
		return true;	
	}

	public static ByteBuffer addHeader(int flag, int len, int id){ //agrega el header al paquete
		ByteBuffer b = ByteBuffer.allocate(getPaddedLength(len) + Constants.HEADER_LEN);
		b.order(ByteOrder.BIG_ENDIAN);
		b.putInt(0, flag);
		b.putInt(4, len);
		b.putInt(8, id);
		return b;
	}
	

	public static int getPaddedLength(int len){ //hace divisible los datos entre 4 para el envio
		if (len % 4 != 0)
			len += (4 - (len % 4));
		return len;
	}
}

