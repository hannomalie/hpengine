package shader;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL44;
import renderer.OpenGLContext;

import java.nio.Buffer;

import static org.lwjgl.opengl.ARBBufferStorage.GL_MAP_COHERENT_BIT;
import static org.lwjgl.opengl.ARBBufferStorage.GL_MAP_PERSISTENT_BIT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;

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

    public BUFFER_TYPE map(int requestedCapacityInBytes) {
        bind();
        if(requestedCapacityInBytes > buffer.capacity()){
            setCapacityInBytes(requestedCapacityInBytes);
        }

        buffer.clear();
        return buffer;
    }

    protected void setCapacityInBytes(int requestedCapacity) {
        int capacityInBytes = requestedCapacity;

        if(buffer != null) {
            boolean needsResize = buffer.capacity() * getPrimitiveSizeInBytes() < capacityInBytes;
            if(needsResize) {
                OpenGLContext.getInstance().execute(() -> {
                    bind();
                    if(GL15.glGetBufferParameter(target, GL15.GL_BUFFER_MAPPED) == 1) {
                        glUnmapBuffer(target);
                    }
                    if(id > 0) {
                        glDeleteBuffers(id);
                        id = -1;
                    }
                });
            } else {
                return;
            }
        }
        {
            OpenGLContext.getInstance().execute(() -> {
                bind();
                int flags = GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;
                GL44.glBufferStorage(target, capacityInBytes, flags);
                buffer = mapBuffer(capacityInBytes, flags);

            });
        }
    }

    abstract protected BUFFER_TYPE mapBuffer(int capacity, int flags);

    @Override
    public void bind() {
        OpenGLContext.getInstance().execute(() -> {
            if(id <= 0) {id = glGenBuffers(); }
            glBindBuffer(target, id);
        });
    }

    @Override
    public void unbind() {
        OpenGLContext.getInstance().execute(() -> glBindBuffer(target, 0));
    }

    @Override
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int getSizeInBytes() {
        return buffer.capacity();
    }

    @Override
    public void setSizeInBytes(int size) {
        setCapacityInBytes(size);
    }

    @Override
    public BUFFER_TYPE getBuffer() {
        return buffer;
    }

    public abstract int getPrimitiveSizeInBytes();

    public void dispose() {
        glDeleteBuffers(id);
    }
}
