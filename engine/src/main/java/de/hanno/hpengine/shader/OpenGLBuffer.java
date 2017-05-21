package de.hanno.hpengine.shader;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public interface OpenGLBuffer<T extends Bufferable> {
    void bind();

    void unbind();

    ByteBuffer getBuffer();

    void putValues(ByteBuffer values);

    void putValues(int offset, ByteBuffer values);

    int getId();

    int getSizeInBytes();

    void setSizeInBytes(int size);

    void putValues(float... values);

    void putValues(int offset, float... values);

    default void put(T... bufferable) {
        put(0, bufferable);
    }

    void put(int offset, T... bufferable);

    /**
     * Retrieves the Bufferable at the given index i. This only works
     * for Bufferables with a static size per object, because
     * the queried instance is retrieved with a byte offset, calculated
     * as index times byte size of the bufferable.
     * @param i the index of the queried instance
     * @param target the target where to apply the retrieved data
     * @return the target instance
     */
    default T get(int i, T target) {
        if(target != null) {
            getBuffer().position(i * target.getBytesPerObject());
            target.getFromBuffer(getBuffer());
            return target;
        } else {
            throw new IllegalArgumentException("No target instance passed - don't pass null!");
        }
    }

    default void putAtIndex(int index, T source) {
        if(source == null) {
            throw new IllegalArgumentException("Don't pass a null data source!");
        }
        if(index < 0) {
            throw new IllegalArgumentException("Don't query a negative index!");
        }
        getBuffer().position(index * source.getBytesPerObject());
        source.putToBuffer(getBuffer());
    }
}
