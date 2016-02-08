package shader;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

/**
 * Created by pernpeintner on 20.11.2015.
 */
public interface OpenGLBuffer {
    void bind();

    void unbind();

    FloatBuffer getValuesAsFloats();

    DoubleBuffer getValues();

    DoubleBuffer getValues(int offset, int length);

    void putValues(FloatBuffer values);

    void putValues(DoubleBuffer values);

    void putValues(int offset, FloatBuffer values);

    void putValues(int offset, DoubleBuffer values);

    int getId();

    int getSize();

    void setSize(int size);

    void putValues(float... values);

    void putValues(int offset, float... values);

    void putValues(int offset, double... values);

    <T extends Bufferable> void put(T... bufferable);

    <T extends Bufferable> void put(int offset, T... bufferable);
}
