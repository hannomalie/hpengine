package de.hanno.hpengine.engine.model;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL40;
import de.hanno.hpengine.shader.AbstractPersistentMappedBuffer;
import de.hanno.hpengine.shader.Bufferable;

import java.nio.*;

import static de.hanno.hpengine.engine.model.CommandBuffer.DrawElementsIndirectCommand.sizeInBytes;
import static de.hanno.hpengine.engine.model.CommandBuffer.DrawElementsIndirectCommand.getSizeInInts;
import static org.lwjgl.opengl.GL30.glMapBufferRange;

public class CommandBuffer extends AbstractPersistentMappedBuffer<CommandBuffer.DrawElementsIndirectCommand> {

    public CommandBuffer(int capacityInBytes) {
        super(GL40.GL_DRAW_INDIRECT_BUFFER);
        setCapacityInBytes(capacityInBytes);
    }

    @Override
    protected ByteBuffer mapBuffer(int capacityInBytes, int flags) {
        ByteBuffer byteBuffer = glMapBufferRange(target, 0, capacityInBytes, flags, BufferUtils.createByteBuffer(capacityInBytes * Integer.BYTES));

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

    @Override
    public void put(int offset, DrawElementsIndirectCommand[] bufferable) {
        if(bufferable.length == 0) { return; }

        setCapacityInBytes((offset + bufferable.length) * sizeInBytes());

        buffer.rewind();
        for (int i = 0; i < bufferable.length; i++) {
            Bufferable currentBufferable = bufferable[i];
            DrawElementsIndirectCommand command = (DrawElementsIndirectCommand) currentBufferable;
            int currentOffset = i * getSizeInInts();
            int[] currentBufferablesValues = command.getAsInts();
            for (int z = 0; z < currentBufferablesValues.length; z++) {
                buffer.asIntBuffer().put(offset+currentOffset + z, currentBufferablesValues[z]);
            }
        }
    }

    @Override
    public void put(DrawElementsIndirectCommand[] bufferable) {
        put(0, bufferable);
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
