package shader;

import java.nio.Buffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

public interface OpenGLBuffer<BUFFERTYPE extends Buffer> {
    void bind();

    void unbind();

    FloatBuffer getValuesAsFloats();

    BUFFERTYPE getValues();

    BUFFERTYPE getValues(int offset, int length);

    BUFFERTYPE getBuffer();

    void putValues(FloatBuffer values);

    void putValues(DoubleBuffer values);

    void putValues(int offset, FloatBuffer values);

    void putValues(int offset, DoubleBuffer values);

    int getId();

    int getSizeInBytes();

    void setSizeInBytes(int size);

    void putValues(float... values);

    void putValues(int offset, float... values);

    void putValues(int offset, double... values);

    <T extends Bufferable> void put(T... bufferable);

    <T extends Bufferable> void put(int offset, T... bufferable);
}
