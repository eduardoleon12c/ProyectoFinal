public class TrackerFile {
	private String name;	// nombre del archivo
	private long size;		// tamaño
	private int id;			// id del archivo
	
	public TrackerFile(String name, long size, int id){
		this.name = name;
		this.size = size;
		this.id = id;
	}
	
	public String name(){
		return this.name;
	}
	
	public long size(){
		return size;
	}

	public int id(){
		return id;
	}

	public boolean Equals(Object other){
            if (other == null)
		return false;
            if (!this.getClass().equals(other.getClass()))
                return false;
            TrackerFile otherF = (TrackerFile)other;
            return (name.equals(otherF.name()) && size == otherF.size());
	}
}

