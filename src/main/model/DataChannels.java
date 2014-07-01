package main.model;

public enum DataChannels {
	POSITION3("in_Position", 3, 0, 0),
	COLOR("in_Color", 3, 1, POSITION3.siB()),
	TEXCOORD("in_TextureCoord", 2, 2, POSITION3.siB() + COLOR.siB()),
	NORMAL("in_Normal", 3, 3, POSITION3.siB() + COLOR.siB() + TEXCOORD.siB()),
	BINORMAL("in_Binormal", 3, 4, POSITION3.siB() + COLOR.siB() + TEXCOORD.siB() + NORMAL.siB()),
	TANGENT("in_Tangent", 3, 5, POSITION3.siB() + COLOR.siB() + TEXCOORD.siB() + NORMAL.siB() +  BINORMAL.siB());

	private String binding;
	private int size;
	private int location;
	private int offset;

	DataChannels(String binding, int size, int location, int bufferOffset) {
		this.binding = binding;
		this.size = size;
		this.location = location;
		this.offset = bufferOffset;
	}
	
	public int getSize() {
		return size;
	}
	
	// Size in Bytes per Attribute
	public int siB() {
		return 4 * size;
	}

	public int getLocation() {
		return location;
	}

	public int getOffset() {
		return offset / 4;
	}
	public int getByteOffset() {
		return offset;
	}
	
	public String getBinding() {
		return binding;
	}
	
	@Override
	public String toString() {
		return String.format("%s (%d)", getBinding(), size); 
	}
}
