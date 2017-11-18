package de.hanno.hpengine.engine.graphics.renderer;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.model.CommandBuffer;
import de.hanno.hpengine.engine.model.IndexBuffer;
import org.lwjgl.BufferUtils;

import java.util.ArrayList;
import java.util.List;

public class CommandOrganization {
    protected final List<CommandBuffer.DrawElementsIndirectCommand> commands = new ArrayList<>();
    protected final IndexBuffer entityOffsetBuffer = new IndexBuffer(BufferUtils.createIntBuffer(10000));
    protected final IndexBuffer entityOffsetBufferCulled = new IndexBuffer(BufferUtils.createIntBuffer(10000));
    final int commandBufferCapacityInBytes = 10000*new CommandBuffer.DrawElementsIndirectCommand(0,0,0,0,0,0).getBytesPerObject();
    protected final CommandBuffer commandBuffer = new CommandBuffer(commandBufferCapacityInBytes);
    protected final CommandBuffer commandBufferCulledPhase1 = new CommandBuffer(commandBufferCapacityInBytes);
    protected final CommandBuffer commandBufferCulledPhase2 = new CommandBuffer(commandBufferCapacityInBytes);
    IntArrayList offsets = new IntArrayList(10000);
    AtomicCounterBuffer drawCountBufferStatic = new AtomicCounterBuffer(1);

    AtomicCounterBuffer drawCountBufferStaticPhase1 = new AtomicCounterBuffer(1);
    AtomicCounterBuffer drawCountBufferAnimatedPhase1 = new AtomicCounterBuffer(1);
    AtomicCounterBuffer drawCountBufferStaticPhase2 = new AtomicCounterBuffer(1);
    AtomicCounterBuffer drawCountBufferAnimatedPhase2 = new AtomicCounterBuffer(1);

    AtomicCounterBuffer drawCountBufferAfterPhase1 = new AtomicCounterBuffer(1);
    AtomicCounterBuffer drawCountBufferAfterPhase2 = new AtomicCounterBuffer(1);

    public CommandOrganization() {
    }
}