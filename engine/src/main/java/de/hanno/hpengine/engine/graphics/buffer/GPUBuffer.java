package de.hanno.hpengine.engine.graphics.buffer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public interface GPUBuffer {
    default void setCapacityInBytes(int requestedCapacity) { throw new IllegalStateException("NOT IMPLEMENTED"); }
    void bind();

    void unbind();

    ByteBuffer getBuffer();

    default IntBuffer getIntBufferView() {
        return getBuffer().asIntBuffer();
    }

    void putValues(ByteBuffer values);

    void putValues(int offset, ByteBuffer values);

    int getId();

    int getSizeInBytes();

    void setSizeInBytes(int size);

    void putValues(float... values);

    void putValues(int offset, float... values);

}
