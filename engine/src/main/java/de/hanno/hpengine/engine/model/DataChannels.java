package de.hanno.hpengine.engine.model;

import java.util.EnumSet;

public enum DataChannels {
	POSITION3("in_Position", 3, 0, 0),
	COLOR("in_Color", 3, 1, POSITION3.siB()),
	TEXCOORD("in_TextureCoord", 2, 2, POSITION3.siB() + COLOR.siB()),
	NORMAL("in_Normal", 3, 3, POSITION3.siB() + COLOR.siB() + TEXCOORD.siB()),
	WEIGHTS("in_Weights", 4, 5, POSITION3.siB() + COLOR.siB() + TEXCOORD.siB() + NORMAL.siB()),
	JOINT_INDICES("in_JointIndices", 4, 6, POSITION3.siB() + COLOR.siB() + TEXCOORD.siB() + NORMAL.siB() + WEIGHTS.siB());
//TODO Use not only float byte size

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

    public static int totalElementsPerVertex(EnumSet<DataChannels> channels) {
        int count = 0;
        for (DataChannels channel : channels) {
            count += channel.getSize();
        }
        return count;
    }

    public static int bytesPerVertex(EnumSet<DataChannels> channels) {
        int sum = 0;
        for (DataChannels channel : channels) {
            sum += channel.getSize();
        }
        return sum * Float.BYTES;
    }

    public int getSize() {
		return size;
	}
	
	// Size in Bytes per Attribute
	public int siB() {
		return Float.BYTES * size;
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
