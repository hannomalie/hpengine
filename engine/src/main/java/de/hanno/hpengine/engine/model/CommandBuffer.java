package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.graphics.GpuContext;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL40;
import de.hanno.hpengine.engine.graphics.buffer.AbstractPersistentMappedBuffer;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import org.lwjgl.system.libc.LibCStdlib;

import java.nio.*;

import static de.hanno.hpengine.engine.model.CommandBuffer.DrawElementsIndirectCommand.sizeInBytes;
import static de.hanno.hpengine.engine.model.CommandBuffer.DrawElementsIndirectCommand.getSizeInInts;
import static org.lwjgl.opengl.GL30.glMapBufferRange;

public class CommandBuffer extends AbstractPersistentMappedBuffer {

    public CommandBuffer(GpuContext gpuContext, int capacityInBytes) {
        super(gpuContext, GL40.GL_DRAW_INDIRECT_BUFFER);
        setCapacityInBytes(capacityInBytes);
    }

    @Override
    protected ByteBuffer mapBuffer(long capacityInBytes, int flags) {
        ByteBuffer byteBuffer = glMapBufferRange(target, 0, capacityInBytes, flags, LibCStdlib.malloc(capacityInBytes));//BufferUtils.createByteBuffer(capacityInBytes * Integer.BYTES));

        if(buffer != null) {
            byteBuffer.put(buffer);
            byteBuffer.rewind();
        }
        return byteBuffer;
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

    @Override
    public void putValues(int offset, float... values) {
        throw new IllegalStateException("Not implemented");
    }

    public final static class DrawElementsIndirectCommand implements Bufferable {
        public int count;
        public int primCount;
        public int firstIndex;
        public int baseVertex;
        public int baseInstance;
        public int entityOffset;
        private int[] asInts = new int[6];

        public DrawElementsIndirectCommand() {

        }

        public DrawElementsIndirectCommand(int count, int primCount, int firstIndex, int baseVertex, int baseInstance, int entityBaseIndex) {
            init(count, primCount, firstIndex, baseVertex, baseInstance, entityBaseIndex);
        }

        public void init(int count, int primCount, int firstIndex, int baseVertex, int baseInstance, int entityBaseIndex) {
            this.count = count;
            this.primCount = primCount;
            this.firstIndex = firstIndex;
            this.baseVertex = baseVertex;
            this.baseInstance = baseInstance;
            entityOffset = entityBaseIndex;
            asInts[0] = count;
            asInts[1] = primCount;
            asInts[2] = firstIndex;
            asInts[3] = baseVertex;
            asInts[4] = baseInstance;
        }

        public int[] getAsInts() {
            return asInts;
        }

        public static int sizeInBytes() {
            return getSizeInInts() * 4;
        }
        public static int getSizeInInts() {
            return 5;
        }

        @Override
        public void putToBuffer(ByteBuffer buffer) {
            for(int current : getAsInts()) {
                buffer.putInt(current);
            }
        }

        @Override
        public int getBytesPerObject() {
            return 6;
        }
    }
}
