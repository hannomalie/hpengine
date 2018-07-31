package de.hanno.hpengine.engine.graphics.buffer;

import de.hanno.hpengine.engine.graphics.GpuContext;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.libc.LibCStdlib;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL30.glMapBufferRange;

public class PersistentMappedBuffer extends AbstractPersistentMappedBuffer {

    public PersistentMappedBuffer(GpuContext gpuContext, int capacityInBytes) {
        this(gpuContext, capacityInBytes, GL43.GL_SHADER_STORAGE_BUFFER);
    }

    public PersistentMappedBuffer(GpuContext gpuContext, int capacityInBytes, int target) {
        super(gpuContext, target);
        setCapacityInBytes(capacityInBytes);
    }

    @Override
    protected ByteBuffer mapBuffer(long capacityInBytes, int flags) {
        ByteBuffer byteBuffer = glMapBufferRange(target, 0, capacityInBytes, flags, LibCStdlib.malloc(capacityInBytes));//BufferUtils.createByteBuffer(capacityInBytes));
        if(buffer != null) {
            byteBuffer.put(buffer);
            byteBuffer.rewind();
        }
        return byteBuffer;
    }

    @Override
    public void putValues(ByteBuffer values) {
        putValues(0, values);
    }

    @Override
    public void putValues(int byteOffset, ByteBuffer values) {
        if(values.capacity() > getSizeInBytes()) { setSizeInBytes(values.capacity());}
        bind();
        values.rewind();
        buffer.rewind();
        buffer.position(byteOffset);
        buffer.put(values);
        buffer.rewind();
    }

    @Override
    public void putValues(float... values) {
        putValues(0, values);
    }

    @Override
    public void putValues(int offset, float... values) {
        bind();
        buffer.position(offset);
        double[] doubleValues = new double[values.length];
        for(int i = 0; i < values.length; i++) {
            doubleValues[i] = values[i];
        }
        buffer.asDoubleBuffer().put(doubleValues);
    }
}
