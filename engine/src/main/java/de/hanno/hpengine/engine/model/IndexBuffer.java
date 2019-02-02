package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.buffer.AbstractPersistentMappedBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.libc.LibCStdlib;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL30.glMapBufferRange;

public class IndexBuffer extends AbstractPersistentMappedBuffer {

    public IndexBuffer(GpuContext gpuContext) {
        this(gpuContext, GL_ELEMENT_ARRAY_BUFFER);
        setCapacityInBytes(4*3*50);
    }

    public IndexBuffer(GpuContext gpuContext, int target) {
        super(gpuContext, target);
    }

    public IndexBuffer(GpuContext gpuContext, IntBuffer intBuffer) {
        this(gpuContext);
        put(intBuffer);
    }

    @Override
    public void putValues(ByteBuffer values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(int offset, ByteBuffer values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(float... values) {
        throw new IllegalStateException("Not implemented");
    }

    public void put(int[] values) {
        put(0, values);
    }
    public void put(int offset, int[] values) {
        setCapacityInBytes((offset + values.length)* Integer.BYTES);
        IntBuffer intBuffer = buffer.asIntBuffer();
        intBuffer.position(offset);
        intBuffer.put(values);
        buffer.rewind();
    }

    /**
     *
     * @param offset
     * @param nonOffsetIndices indices as if no other indices were before in the index buffer
     */
    public void appendIndices(int offset, int... nonOffsetIndices) {
        buffer.rewind();
        if(offset == 0) {
            put(nonOffsetIndices);
        } else {
            for(int i = 0; i < nonOffsetIndices.length; i++) {
                put(offset+i, nonOffsetIndices[i]);
            }
        }
    }

    @Override
    public void putValues(int offset, float... values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    protected ByteBuffer mapBuffer(long capacityInBytes, int flags) {
        ByteBuffer targetBuffer = BufferUtils.createByteBuffer((int) capacityInBytes);
        ByteBuffer byteBuffer = glMapBufferRange(target, 0, capacityInBytes, flags, targetBuffer);
        if(buffer != null) {
            buffer.rewind();
            byteBuffer.put(buffer);
            byteBuffer.rewind();
        }
        return byteBuffer;
    }

    public void put(IntBuffer indices) {
        setCapacityInBytes(indices.capacity() * Integer.BYTES);
        buffer.rewind();
        indices.rewind();
        buffer.asIntBuffer().put(indices);
    }

    public void put(int index, int value) {
        setCapacityInBytes(Integer.BYTES*index+Integer.BYTES);
        buffer.rewind();
        buffer.asIntBuffer().put(index, value);
    }
}
