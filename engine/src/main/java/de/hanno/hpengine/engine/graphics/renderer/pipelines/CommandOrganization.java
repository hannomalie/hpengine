package de.hanno.hpengine.engine.graphics.renderer.pipelines;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer;
import de.hanno.hpengine.engine.graphics.renderer.AtomicCounterBuffer;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline.CullingPhase;
import de.hanno.hpengine.engine.model.CommandBuffer;
import de.hanno.hpengine.engine.model.CommandBuffer.DrawElementsIndirectCommand;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.IndexBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.BufferUtils.createIntBuffer;

public class CommandOrganization {
    protected final List<DrawElementsIndirectCommand> commands = new ArrayList<>();

    private final int commandBufferCapacityInBytes = 10000*new DrawElementsIndirectCommand(0,0,0,0,0,0).getBytesPerObject();
    protected final CommandBuffer commandBuffer = new CommandBuffer(commandBufferCapacityInBytes);
    IntArrayList offsets = new IntArrayList(10000);
    IndexBuffer entityOffsetBuffer = new IndexBuffer(createIntBuffer(10000));
    AtomicCounterBuffer drawCountBuffer = new AtomicCounterBuffer(1);

    Map<CullingPhase, AtomicCounterBuffer> drawCountBuffers = new HashMap<>();
    Map<CullingPhase, CommandBuffer> commandBuffers = new HashMap<>();
    Map<CullingPhase, IndexBuffer> visibilityBuffers = new HashMap<>();
    Map<CullingPhase, IndexBuffer> entityOffsetBuffers = new HashMap<>();
    Map<CullingPhase, IndexBuffer> commandOffsets = new HashMap<>();
    Map<CullingPhase, IndexBuffer> currentCompactedPointers = new HashMap<>();
    Map<CullingPhase, IndexBuffer> entityOffsetBuffersCulled = new HashMap<>();
    Map<CullingPhase, GPUBuffer<Entity>> entitiesBuffersCompacted = new HashMap<>();
    Map<CullingPhase, AtomicCounterBuffer> entitiesCompactedCounter = new HashMap<>();
    Map<CullingPhase, IndexBuffer> entitiesCounters = new HashMap<>();

    public CommandOrganization() {
        for (CullingPhase phase : CullingPhase.values()) {
            drawCountBuffers.put(phase, new AtomicCounterBuffer(1));
            commandBuffers.put(phase, new CommandBuffer(commandBufferCapacityInBytes));
            visibilityBuffers.put(phase, new IndexBuffer(createIntBuffer(10000)));
            entityOffsetBuffers.put(phase, new IndexBuffer(createIntBuffer(10000)));
            commandOffsets.put(phase, new IndexBuffer(createIntBuffer(10000)));
            currentCompactedPointers.put(phase, new IndexBuffer(createIntBuffer(10000)));
            entityOffsetBuffersCulled.put(phase, new IndexBuffer(createIntBuffer(10000)));
            entitiesBuffersCompacted.put(phase, new PersistentMappedBuffer(8000));
            entitiesCompactedCounter.put(phase, new AtomicCounterBuffer(1));
            entitiesCounters.put(phase, new IndexBuffer());
        }
    }
}