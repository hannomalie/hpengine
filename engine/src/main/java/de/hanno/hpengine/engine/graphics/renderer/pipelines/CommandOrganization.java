package de.hanno.hpengine.engine.graphics.renderer.pipelines;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer;
import de.hanno.hpengine.engine.graphics.renderer.AtomicCounterBuffer;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline.CullingPhase;
import de.hanno.hpengine.engine.model.CommandBuffer;
import de.hanno.hpengine.engine.model.CommandBuffer.DrawElementsIndirectCommand;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.Model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.BufferUtils.createIntBuffer;

public class CommandOrganization {
    protected final List<DrawElementsIndirectCommand> commands = new ArrayList<>();

    private final int commandBufferCapacityInBytes = 10000*new DrawElementsIndirectCommand(0,0,0,0,0,0).getBytesPerObject();
    protected final CommandBuffer commandBuffer;
    IntArrayList offsets = new IntArrayList(10000);
    IndexBuffer entityOffsetBuffer;
    AtomicCounterBuffer drawCountBuffer;

    AtomicCounterBuffer drawCountBuffers;
    CommandBuffer commandBuffers;
    IndexBuffer visibilityBuffers;
    IndexBuffer entityOffsetBuffers;
    IndexBuffer commandOffsets;
    IndexBuffer currentCompactedPointers;
    IndexBuffer entityOffsetBuffersCulled;
    GPUBuffer<ModelComponent> entitiesBuffersCompacted;
    AtomicCounterBuffer entitiesCompactedCounter;
    IndexBuffer entitiesCounters;

    public CommandOrganization(GpuContext gpuContext) {
        drawCountBuffers = new AtomicCounterBuffer(gpuContext, 1);
        commandBuffers = new CommandBuffer(gpuContext, commandBufferCapacityInBytes);
        visibilityBuffers = new IndexBuffer(gpuContext, createIntBuffer(10000));
        entityOffsetBuffers = new IndexBuffer(gpuContext, createIntBuffer(10000));
        commandOffsets = new IndexBuffer(gpuContext, createIntBuffer(10000));
        currentCompactedPointers = new IndexBuffer(gpuContext, createIntBuffer(10000));
        entityOffsetBuffersCulled = new IndexBuffer(gpuContext, createIntBuffer(10000));
        entitiesBuffersCompacted = new PersistentMappedBuffer<>(gpuContext, 8000);
        entitiesCompactedCounter = new AtomicCounterBuffer(gpuContext, 1);
        entitiesCounters = new IndexBuffer(gpuContext);

        commandBuffer = new CommandBuffer(gpuContext, commandBufferCapacityInBytes);
        entityOffsetBuffer = new IndexBuffer(gpuContext, createIntBuffer(10000));
        drawCountBuffer = new AtomicCounterBuffer(gpuContext, 1);
    }
}