package de.hanno.hpengine.shader;

import de.hanno.hpengine.renderer.GraphicsContext;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

public abstract class StorageBuffer implements OpenGLBuffer {

    protected int id = -1;

    protected final int primitiveByteSize = 8; // 8 bytes for double primitve type

    protected ByteBuffer buffer;
    private int size = -1;

    private volatile DoubleBuffer tempBuffer;

    public StorageBuffer(int size) {
        this(BufferUtils.createDoubleBuffer(size));
    }

    public StorageBuffer(DoubleBuffer data) {
        GraphicsContext.getInstance().execute(() -> {
            id = GL15.glGenBuffers();
            buffer(data);
            unbind();
//            getValues();
        });
    }

    private void buffer(DoubleBuffer data) {
        bind();
        GraphicsContext.getInstance().execute(() -> {
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, data, GL15.GL_DYNAMIC_COPY);
            setSizeInBytes(GL15.glGetBufferParameter(GL43.GL_SHADER_STORAGE_BUFFER, GL15.GL_BUFFER_SIZE));
        });
    }

    @Override
    public void bind() {
        GraphicsContext.getInstance().execute(() -> {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
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
        GraphicsContext.getInstance().execute(() -> {
            bind();
            if (offset * primitiveByteSize + values.capacity() * primitiveByteSize > size) {
                throw new IndexOutOfBoundsException(String.format("Can't put values into de.hanno.hpengine.shader storage buffer %d (size: %d, offset %d, length %d)", id, size, offset * primitiveByteSize, values.capacity() * primitiveByteSize));
            }
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, offset * primitiveByteSize, values);
            unbind();
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

    @Override
    public void put(int offset, Bufferable... bufferable) {
        if(bufferable.length == 0) { return; }
        GraphicsContext.getInstance().execute(() -> {
            ByteBuffer tempByteBuffer = BufferUtils.createByteBuffer(20 * 4 * bufferable.length); // TODO Estimate better
            tempBuffer = tempByteBuffer.asDoubleBuffer();
            for (int i = 0; i < bufferable.length; i++) {
                Bufferable currentBufferable = bufferable[i];
                currentBufferable.putToBuffer(tempByteBuffer);
            }
            putValues(offset, tempByteBuffer);
        });
    }
}
