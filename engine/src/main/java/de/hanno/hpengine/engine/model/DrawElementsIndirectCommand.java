package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.graphics.buffer.Bufferable;

import java.nio.ByteBuffer;

public final class DrawElementsIndirectCommand implements Bufferable {
    public int count;
    public int primCount;
    public int firstIndex;
    public int baseVertex;
    public int baseInstance;
    public int entityOffset;
    private int[] asInts = new int[6];

    public DrawElementsIndirectCommand() {

    }

    public DrawElementsIndirectCommand(int count, int primCount, int firstIndex, int baseVertex, int baseInstance, int entityBaseIndex) {
        init(count, primCount, firstIndex, baseVertex, baseInstance, entityBaseIndex);
    }

    public void init(int count, int primCount, int firstIndex, int baseVertex, int baseInstance, int entityBaseIndex) {
        this.count = count;
        this.primCount = primCount;
        this.firstIndex = firstIndex;
        this.baseVertex = baseVertex;
        this.baseInstance = baseInstance;
        entityOffset = entityBaseIndex;
        asInts[0] = count;
        asInts[1] = primCount;
        asInts[2] = firstIndex;
        asInts[3] = baseVertex;
        asInts[4] = baseInstance;
    }

    public int[] getAsInts() {
        return asInts;
    }

    public static int sizeInBytes() {
        return getSizeInInts() * 4;
    }
    public static int getSizeInInts() {
        return 5;
    }

    @Override
    public void putToBuffer(ByteBuffer buffer) {
        for(int current : getAsInts()) {
            buffer.putInt(current);
        }
    }

    @Override
    public int getBytesPerObject() {
        return 6;
    }
}
