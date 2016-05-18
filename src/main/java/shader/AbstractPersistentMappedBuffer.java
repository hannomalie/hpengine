package shader;

import org.lwjgl.BufferUtils;
import renderer.OpenGLContext;

import java.lang.reflect.ParameterizedType;
import java.nio.Buffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.ARBBufferStorage.GL_MAP_COHERENT_BIT;
import static org.lwjgl.opengl.ARBBufferStorage.GL_MAP_PERSISTENT_BIT;
import static org.lwjgl.opengl.ARBBufferStorage.glBufferStorage;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL30.glMapBufferRange;

public abstract class AbstractPersistentMappedBuffer<BUFFER_TYPE extends Buffer> implements OpenGLBuffer {
    protected final int target;
    private int id;
    protected BUFFER_TYPE buffer;

    public AbstractPersistentMappedBuffer(int target) {
        this.target = target;
//        ParameterizedType superClass = (ParameterizedType) getClass().getGenericSuperclass();
//        Class type = (Class) superClass.getActualTypeArguments()[0];
//        try {
//            BUFFER_TYPE t = (BUFFER_TYPE) type.newInstance();
//        } catch (InstantiationException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        }
    }

    public BUFFER_TYPE map(int requestedCapacity) {
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
        } else if(buffer != null && buffer.capacity() >= capacity) {
            return;
        }

        OpenGLContext.getInstance().execute(() -> {
            id = glGenBuffers();
            bind();
            glBufferStorage(target, capacity * 8, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
            buffer = mapBuffer(capacity);
        });
    }

    protected BUFFER_TYPE mapBuffer(int capacity) {
        return (BUFFER_TYPE) glMapBufferRange(target, 0, capacity*8, GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT, BufferUtils.createByteBuffer(capacity*8)).asDoubleBuffer();
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
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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
    public BUFFER_TYPE getBuffer() {
        return buffer;
    }

    public abstract int getPrimitiveSize();

    public void dispose() {
        glDeleteBuffers(id);
    }
}
