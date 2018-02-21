package de.hanno.hpengine.engine.graphics.buffer;

import java.nio.ByteBuffer;

public interface Bufferable {
    void putToBuffer(ByteBuffer buffer);

    default void getFromBuffer(ByteBuffer buffer) {
        throw new IllegalStateException("Not yet implemented");
    }
    default String debugPrintFromBuffer(ByteBuffer buffer) {
        throw new IllegalStateException("Not yet implemented");
    }

    int getBytesPerObject();

    /**
     * Populates this instance with data from the given buffer.
     * This only works with Bufferables with fixed byte size, because
     * byte size multiplied with index i is the used byte offset.
     * @param i the index the data should be retrieved from
     * @param buffer the buffer the data should be retrieved from
     */
    default void getFromIndex(int i, ByteBuffer buffer) {
        buffer.position(i * getBytesPerObject());
        getFromBuffer(buffer);
    }
}
