package engine.model;

import org.lwjgl.BufferUtils;
import shader.AbstractPersistentMappedBuffer;
import shader.Bufferable;

import java.nio.Buffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL30.glMapBufferRange;

public class IndexBuffer extends AbstractPersistentMappedBuffer<IntBuffer>{

    public IndexBuffer() {
        this(GL_ELEMENT_ARRAY_BUFFER);
        setCapacityInBytes(4*3*50);
    }

    public IndexBuffer(int target) {
        super(target);
    }

    public IndexBuffer(IntBuffer intBuffer) {
        this();
        put(intBuffer);
    }

    @Override
    public FloatBuffer getValuesAsFloats() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public IntBuffer getValues() {
        buffer.rewind();
        return buffer;
    }

    @Override
    public Buffer getValues(int offset, int length) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(FloatBuffer values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(DoubleBuffer values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(int offset, FloatBuffer values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(int offset, DoubleBuffer values) {
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
        setCapacityInBytes((offset + values.length)* getPrimitiveSizeInBytes());
        buffer.position(offset);
        buffer.put(values);
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
//                put(offset+i, nonOffsetIndices[i] + offset);
                put(offset+i, nonOffsetIndices[i]);
            }
        }
    }

    @Override
    public void putValues(int offset, float... values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void putValues(int offset, double... values) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void put(int offset, Bufferable[] bufferable) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void put(Bufferable[] bufferable) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    protected IntBuffer mapBuffer(int capacityInBytes, int flags) {
        IntBuffer newBuffer = glMapBufferRange(target, 0, capacityInBytes, flags, BufferUtils.createByteBuffer(capacityInBytes)).asIntBuffer();
        if(buffer != null) {
            buffer.rewind();
            newBuffer.put(buffer);
            newBuffer.rewind();
        }
        return newBuffer;
    }

    @Override
    public int getPrimitiveSizeInBytes() {
        return 4;
    }

    public void put(IntBuffer indices) {
        setCapacityInBytes(indices.capacity() * 4);
        buffer.rewind();
        indices.rewind();
        buffer.put(indices);
    }

    public void put(int index, int value) {
        setCapacityInBytes(getPrimitiveSizeInBytes()*index+getPrimitiveSizeInBytes());
        buffer.rewind();
        buffer.put(index, value);
    }
}
