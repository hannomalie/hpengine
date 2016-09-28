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
    protected volatile BUFFER_TYPE buffer;
    private boolean bound = false;

    public AbstractPersistentMappedBuffer(int target) {
        this.target = target;
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
        if(capacityInBytes <= 0) { capacityInBytes = 10; }

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
                    unbind();
                });
            } else {
                return;
            }
        }
        {
            int finalCapacityInBytes = 2*capacityInBytes;
            OpenGLContext.getInstance().execute(() -> {
                bind();
                int flags = GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;
                GL44.glBufferStorage(target, finalCapacityInBytes, flags);
                buffer = mapBuffer(finalCapacityInBytes, flags);
                unbind();
            });
        }
    }

    abstract protected BUFFER_TYPE mapBuffer(int capacityInBytes, int flags);

    @Override
    public void bind() {
//        TODO: Make this somehow possible
//        if(bound) {return;}
        OpenGLContext.getInstance().execute(() -> {
            if(id <= 0) {id = glGenBuffers(); }
            glBindBuffer(target, id);
        });
        bound = true;
    }

    @Override
    public void unbind() {
        OpenGLContext.getInstance().execute(() -> glBindBuffer(target, 0));
        bound = false;
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
