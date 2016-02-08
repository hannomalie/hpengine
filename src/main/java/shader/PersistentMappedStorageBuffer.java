package shader;

import org.apache.commons.lang.NotImplementedException;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL43;
import renderer.OpenGLContext;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.ARBBufferStorage.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL30.glMapBufferRange;

public class PersistentMappedStorageBuffer implements OpenGLBuffer{

    private final int target = GL43.GL_SHADER_STORAGE_BUFFER;

    protected int id;

    protected DoubleBuffer buffer;

    public PersistentMappedStorageBuffer(int capacity) {
        setCapacity(capacity);
    }

    public DoubleBuffer map(int requestedCapacity) {
        bind();
        if(requestedCapacity > buffer.capacity()){
            setCapacity(requestedCapacity);
        }

        buffer.clear();
        return buffer;
    }

    protected synchronized void setCapacity(int requestedCapacity) {
        int capacity = requestedCapacity;

        if(buffer != null && buffer.capacity() < capacity){
            OpenGLContext.getInstance().execute(() -> {
                bind();
                glUnmapBuffer(target);
                glDeleteBuffers(id);
            });
        }

        OpenGLContext.getInstance().execute(() -> {
            id = glGenBuffers();
            bind();
            glBufferStorage(target, capacity, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
            buffer = glMapBufferRange(target, 0, capacity, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT, BufferUtils.createByteBuffer(capacity*4)).asDoubleBuffer();
        });
    }

    @Override
    public void bind() {
        OpenGLContext.getInstance().execute(() -> {
            glBindBuffer(target, id);
        });
    }

    @Override
    public void unbind() {
        OpenGLContext.getInstance().execute(() -> {
            glBindBuffer(target, 0);
        });
    }

    @Override
    public FloatBuffer getValuesAsFloats() {
        throw new NotImplementedException();
    }

    @Override
    public DoubleBuffer getValues() {
        return buffer;
    }

    @Override
    public DoubleBuffer getValues(int offset, int length) {
        throw new NotImplementedException();
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
        DoubleBuffer doubleBuffer = BufferUtils.createDoubleBuffer(values.capacity());
        values.reset();
        for(int i = offset; i < values.capacity(); i++) {
            doubleBuffer.put(i, values.get(i));
        }
        doubleBuffer.reset();
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
    public int getId() {
        return id;
    }

    @Override
    public int getSize() {
        return buffer.capacity();
    }

    @Override
    public void setSize(int size) {
        setCapacity(size);
    }

    @Override
    public synchronized <T extends Bufferable> void put(T... bufferable) {
        put(0, bufferable);
    }

    @Override
    public synchronized <T extends Bufferable> void put(int offset, T... bufferable) {
        if(bufferable.length == 0) { return; }
        setCapacity(bufferable[0].getSizePerObject() * 8 * bufferable.length);

        buffer.rewind();
        for (int i = 0; i < bufferable.length; i++) {
            Bufferable currentBufferable = bufferable[i];
            int currentOffset = i * currentBufferable.getSizePerObject();
            double[] currentBufferableArray = currentBufferable.get();
            for (int z = 0; z < currentBufferableArray.length; z++) {
                buffer.put(currentOffset + z, currentBufferableArray[z]);
            }
        }
        putValues(offset, buffer);
    }

    public void dispose() {
        glDeleteBuffers(id);
    }
}
