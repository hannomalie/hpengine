package de.hanno.hpengine.engine.graphics.buffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL43;

import java.lang.reflect.ParameterizedType;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL30.glMapBufferRange;

public class PersistentMappedBuffer<T extends Bufferable> extends AbstractPersistentMappedBuffer<T> {

    public PersistentMappedBuffer(int capacityInBytes) {
        this(capacityInBytes, GL43.GL_SHADER_STORAGE_BUFFER);
    }

    public PersistentMappedBuffer(int capacityInBytes, int target) {
        super(target);
        setCapacityInBytes(capacityInBytes);
    }

    @Override
    protected ByteBuffer mapBuffer(int capacityInBytes, int flags) {
        ByteBuffer byteBuffer = glMapBufferRange(target, 0, capacityInBytes, flags, BufferUtils.createByteBuffer(capacityInBytes));
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

    @Override
    public void put(int offset, T... bufferable) {
        if(bufferable.length == 0) { return; }

        int bytesPerObject = bufferable[0].getBytesPerObject();
        setCapacityInBytes(bytesPerObject * bufferable.length);

        buffer.position(bytesPerObject*offset);
        int currentOffset = 0;
        for (int i = 0; i < bufferable.length; i++) {
            Bufferable currentBufferable = bufferable[i];
            setCapacityInBytes(bytesPerObject*(offset+currentOffset));
            currentBufferable.putToBuffer(buffer);
            currentOffset ++;
        }
    }

}
