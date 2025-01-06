import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;


public class ClientObj {
	private List<TrackerFile> uploaded;	// lista de archivos que subio el cliente
	private InetAddress IPAddress;		// ip del cliente
	private int portNumber;				//puerto del cliente
	private Socket connection_socket;	// socket del cliente
	private int id;						// id del cliente
	
	public ClientObj(InetAddress IPAddress, int portNumber, int id){
		this.IPAddress = IPAddress;
		this.portNumber = portNumber;
		this.id = id;
		uploaded = new ArrayList<TrackerFile>();
		connection_socket = null;
	}
	

	public List<TrackerFile> getFiles(){
		return uploaded;
	}
	

	public void addUpload(TrackerFile file){
		uploaded.add(file);
	}

	public boolean hasFile(int id){
		for (TrackerFile f : uploaded){
			if (f.id() == id)
				return true;
		}
		return false;
	}
	
	public InetAddress getAddress(){
		return IPAddress;
	}

	public Socket getListenerSocket(){
		return connection_socket;
	}
	
	public void setListenerSocket(Socket connection_socket) throws SocketException{
		this.connection_socket = connection_socket;
		this.connection_socket.setSoTimeout(0);
		this.connection_socket.setKeepAlive(true);
	}
	
	public int getPort(){
		return portNumber;
	}
        
	public void setAddress(InetAddress addr){
		IPAddress = addr;
	}

	public void setPort(int port){
		portNumber = port;
	}

	public int getId(){
		return id;
	}

	public boolean Equals(Object other){
            if (other == null)
                    return false;
            if (!this.getClass().equals(other.getClass()))
                return false;
            ClientObj otherF = (ClientObj)other;
            return (id == otherF.getId());
	}
}
