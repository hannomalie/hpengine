package de.hanno.hpengine.engine.graphics.buffer;

import de.hanno.hpengine.engine.graphics.GpuContext;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL44;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.ARBBufferStorage.GL_MAP_COHERENT_BIT;
import static org.lwjgl.opengl.ARBBufferStorage.GL_MAP_PERSISTENT_BIT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;

public abstract class AbstractPersistentMappedBuffer implements GPUBuffer {
    private final GpuContext gpuContext;
    protected int target = 0;
    private int id;
    protected volatile ByteBuffer buffer;
    private IntBuffer intBuffer;
    private boolean bound = false;

    public AbstractPersistentMappedBuffer(GpuContext gpuContext, int target) {
        this.gpuContext = gpuContext;
        this.target = target;
    }

    public ByteBuffer map(int requestedCapacityInBytes) {
        bind();
        if(requestedCapacityInBytes > buffer.capacity()){
            setCapacityInBytes(requestedCapacityInBytes);
        }

        buffer.clear();
        return buffer;
    }

    @Override
    public synchronized void setCapacityInBytes(int requestedCapacity) {
        int capacityInBytes = requestedCapacity;
        if(capacityInBytes <= 0) { capacityInBytes = 10; }

        if(buffer != null) {
            boolean needsResize = buffer.capacity()  <= capacityInBytes;
            if(needsResize) {
                gpuContext.execute(() -> {
                    bind();
                    if(GL15.glGetBufferParameteri(target, GL15.GL_BUFFER_MAPPED) == 1) {
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
            gpuContext.execute(() -> {
                bind();
                int flags = GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;
                GL44.glBufferStorage(target, finalCapacityInBytes, flags);

                buffer = mapBuffer(finalCapacityInBytes, flags);
                intBuffer = buffer.asIntBuffer();
                unbind();
            });
        }
    }

    abstract protected ByteBuffer mapBuffer(long capacityInBytes, int flags);

    @Override
    public void bind() {
//        TODO: Make this somehow possible
//        if(bound) {return;}
        gpuContext.execute(bindBufferRunnable);
        bound = true;
    }

    private Runnable bindBufferRunnable = () -> {
        if (getId() <= 0) {
            setId(glGenBuffers());
        }
        glBindBuffer(target, getId());
    };

    @Override
    public void unbind() {
        gpuContext.execute(() -> glBindBuffer(target, 0));
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
    public ByteBuffer getBuffer() {
        return buffer;
    }

    @Override
    public IntBuffer getIntBufferView() {
        return intBuffer;
    }

    public void dispose() {
        glDeleteBuffers(id);
    }
}
