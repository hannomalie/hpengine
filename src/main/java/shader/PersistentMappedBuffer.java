package shader;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL43;
import renderer.OpenGLContext;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.ARBBufferStorage.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL30.glMapBufferRange;

public class PersistentMappedBuffer extends AbstractPersistentMappedBuffer<DoubleBuffer> {

    public PersistentMappedBuffer(int capacity) {
        this(capacity, GL43.GL_SHADER_STORAGE_BUFFER);
    }

    public PersistentMappedBuffer(int capacity, int target) {
        super(target);
        setCapacity(capacity);
    }

    @Override
    public FloatBuffer getValuesAsFloats() {
        FloatBuffer result = BufferUtils.createFloatBuffer(buffer.capacity()/ getPrimitiveSize());
        for(int i = 0; i < buffer.capacity() / getPrimitiveSize(); i++) {
            result.put(i, (float) buffer.get(i));
        }

        result.rewind();
        return result;
    }

    @Override
    public int getPrimitiveSize() {
        return 8;
    }

    @Override
    public DoubleBuffer getValues() {
        return getValues(0, buffer.capacity() / getPrimitiveSize());
    }

    @Override
    public DoubleBuffer getValues(int offset, int length) {
        DoubleBuffer result = BufferUtils.createDoubleBuffer(length);
        for(int i = 0; i < length; i++) {
            result.put(i, buffer.get(offset+i));
        }

        result.rewind();
        return result;
    }

    @Override
    public synchronized void putValues(FloatBuffer values) {
        putValues(0, values);
    }

    @Override
    public synchronized void putValues(DoubleBuffer values) {
        putValues(0, values);
    }

    @Override
    public synchronized void putValues(int offset, FloatBuffer values) {
        if(values.capacity() > getSize()) { setSize(values.capacity());}
        bind();
        values.rewind();
        for(int i = offset; i < values.capacity(); i++) {
            buffer.put(i, values.get(i));
        }
        buffer.reset();
    }

    @Override
    public synchronized void putValues(int offset, DoubleBuffer values) {
        if(values == buffer) { return; }
        if(values.capacity() > getSize()) { setSize(values.capacity());}
        bind();
        values.rewind();
        buffer.position(offset);
        buffer.put(values);
    }

    @Override
    public synchronized void putValues(float... values) {
        putValues(0, values);
    }

    @Override
    public synchronized void putValues(int offset, float... values) {
        bind();
        buffer.position(offset);
        double[] doubleValues = new double[values.length];
        for(int i = 0; i < values.length; i++) {
            doubleValues[i] = values[i];
        }
        buffer.put(doubleValues);
    }

    @Override
    public void putValues(int offset, double... values) {
        bind();
        buffer.position(offset);
        buffer.put(values);
    }

    @Override
    public synchronized void put(Bufferable... bufferable) {
        put(0, bufferable);
    }

    @Override
    public synchronized void put(int offset, Bufferable... bufferable) {
        if(bufferable.length == 0) { return; }
        setCapacity(bufferable[0].getSizePerObject() * getPrimitiveSize() * bufferable.length);

        buffer.rewind();
        for (int i = 0; i < bufferable.length; i++) {
            Bufferable currentBufferable = bufferable[i];
            int currentOffset = i * currentBufferable.getSizePerObject();
            double[] currentBufferableArray = currentBufferable.get();
            for (int z = 0; z < currentBufferableArray.length; z++) {
                buffer.put(offset+currentOffset + z, currentBufferableArray[z]);
            }
        }
//        putValues(offset, buffer);
    }

}
