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

    void put(T... bufferable);

    void put(int offset, T... bufferable);
}
