package de.hanno.hpengine.engine.graphics.renderer;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer;
import de.hanno.hpengine.engine.model.CommandBuffer;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.IndexBuffer;
import org.lwjgl.BufferUtils;

import java.util.ArrayList;
import java.util.List;

public class CommandOrganization {
    protected final List<CommandBuffer.DrawElementsIndirectCommand> commands = new ArrayList<>();

    public GPUBuffer<Entity> entitiesBufferCompacted = new PersistentMappedBuffer(8000);
    public AtomicCounterBuffer entitiesCompactedCounter = new AtomicCounterBuffer(1);
    public IndexBuffer entityCounters = new IndexBuffer();

    protected final IndexBuffer entityOffsetBuffer = new IndexBuffer(BufferUtils.createIntBuffer(10000));
    protected final IndexBuffer entityOffsetBufferCulled = new IndexBuffer(BufferUtils.createIntBuffer(10000));

    protected final IndexBuffer visibilityBuffer = new IndexBuffer(BufferUtils.createIntBuffer(10000));

    final int commandBufferCapacityInBytes = 10000*new CommandBuffer.DrawElementsIndirectCommand(0,0,0,0,0,0).getBytesPerObject();
    protected final CommandBuffer commandBuffer = new CommandBuffer(commandBufferCapacityInBytes);
    protected final CommandBuffer commandBufferCulledPhase1 = new CommandBuffer(commandBufferCapacityInBytes);
    protected final CommandBuffer commandBufferCulledPhase2 = new CommandBuffer(commandBufferCapacityInBytes);
    IntArrayList offsets = new IntArrayList(10000);
    AtomicCounterBuffer drawCountBufferStatic = new AtomicCounterBuffer(1);

    AtomicCounterBuffer drawCountBufferPhase1 = new AtomicCounterBuffer(1);
    AtomicCounterBuffer drawCountBufferPhase2 = new AtomicCounterBuffer(1);

    public CommandOrganization() {
    }
}