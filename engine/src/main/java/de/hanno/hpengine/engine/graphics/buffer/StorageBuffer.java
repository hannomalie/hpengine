package de.hanno.hpengine.engine.graphics.buffer;

import de.hanno.hpengine.engine.graphics.GpuContext;
import kotlin.Unit;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

public abstract class StorageBuffer implements GPUBuffer {

    protected int id = -1;

    protected final int primitiveByteSize = 8; // 8 bytes for double primitve type

    protected ByteBuffer buffer;
    private int size = -1;

    private volatile DoubleBuffer tempBuffer;
    private final GpuContext gpuContext;

    public StorageBuffer(GpuContext gpuContext, int size) {
        this(gpuContext, BufferUtils.createDoubleBuffer(size));
    }

    public StorageBuffer(GpuContext gpuContext, DoubleBuffer data) {
        this.gpuContext = gpuContext;
        this.gpuContext.invoke(() -> {
            id = GL15.glGenBuffers();
            buffer(data);
            unbind();
//            getValues();
            return Unit.INSTANCE;
        });
    }

    private void buffer(DoubleBuffer data) {
        bind();
        gpuContext.invoke(() -> {
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, data, GL15.GL_DYNAMIC_COPY);
            setSizeInBytes(GL15.glGetBufferParameteri(GL43.GL_SHADER_STORAGE_BUFFER, GL15.GL_BUFFER_SIZE));
            return Unit.INSTANCE;
        });
    }

    @Override
    public void bind() {
        gpuContext.invoke(() -> {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
            return Unit.INSTANCE;
        });
    }

    @Override
    public void unbind() {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }




    @Override
    public ByteBuffer getBuffer() {
        return buffer;
    }

    @Override
    public void putValues(ByteBuffer values) {
        putValues(0, values);
    }

    @Override
    public void putValues(int offset, ByteBuffer values) {
        gpuContext.invoke(() -> {
            bind();
            if (offset * primitiveByteSize + values.capacity() * primitiveByteSize > size) {
                throw new IndexOutOfBoundsException(String.format("Can't put values into de.hanno.hpengine.shader storage buffer %d (size: %d, offset %d, length %d)", id, size, offset * primitiveByteSize, values.capacity() * primitiveByteSize));
            }
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, offset * primitiveByteSize, values);
            unbind();
            return Unit.INSTANCE;
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
    public int getSizeInBytes() {
        return size;
    }

    @Override
    public void setSizeInBytes(int size) {
        this.size = size;
    }

    @Override
    public void putValues(float... values) {
        putValues(0, values);
    }

    @Override
    public void putValues(int offset, float... values) {
        ByteBuffer xxx = BufferUtils.createByteBuffer(values.length * Double.BYTES);
        tempBuffer = xxx.asDoubleBuffer();
        for (int i = 0; i < values.length; i++) {
            tempBuffer.put(offset + i, values[i]);
        }
        putValues(xxx);
        tempBuffer = null;
    }
}
