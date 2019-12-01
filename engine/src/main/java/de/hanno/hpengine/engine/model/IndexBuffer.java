package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.buffer.AbstractPersistentMappedBuffer;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;

public class IndexBuffer extends AbstractPersistentMappedBuffer {

    public IndexBuffer(GpuContext gpuContext) {
        this(gpuContext, GL_ELEMENT_ARRAY_BUFFER);
        ensureCapacityInBytes(4*3*50);
    }

    public IndexBuffer(GpuContext gpuContext, int target) {
        super(gpuContext, target);
    }

    public IndexBuffer(GpuContext gpuContext, IntBuffer intBuffer) {
        this(gpuContext);
        put(intBuffer);
    }

    public void put(int[] values) {
        put(0, values);
    }
    public void put(int offset, int[] values) {
        ensureCapacityInBytes((values.length+offset)*Integer.BYTES);
        IntBuffer intBuffer = getBuffer().asIntBuffer();
        intBuffer.position(offset);
        intBuffer.put(values);
        getBuffer().rewind();
    }

    /**
     *
     * @param offset
     * @param nonOffsetIndices indices as if no other indices were before in the index buffer
     */
    public void appendIndices(int offset, int... nonOffsetIndices) {
        getBuffer().rewind();
        ensureCapacityInBytes((nonOffsetIndices.length+offset)*Integer.BYTES);
        if(offset == 0) {
            put(nonOffsetIndices);
        } else {
            for(int i = 0; i < nonOffsetIndices.length; i++) {
                put(offset+i, nonOffsetIndices[i]);
            }
        }
    }

    public void put(IntBuffer indices) {
        getBuffer().rewind();
        indices.rewind();
        getBuffer().asIntBuffer().put(indices);
    }

    public void put(int index, int value) {
        getBuffer().rewind();
        getBuffer().asIntBuffer().put(index, value);
    }

    public int getSize() {
        return getSizeInBytes() / Integer.BYTES;
    }
}
