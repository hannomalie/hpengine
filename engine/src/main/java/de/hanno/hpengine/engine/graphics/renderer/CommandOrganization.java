package de.hanno.hpengine.engine.graphics.renderer;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.model.CommandBuffer;
import de.hanno.hpengine.engine.model.IndexBuffer;
import org.lwjgl.BufferUtils;

import java.util.ArrayList;
import java.util.List;

public class CommandOrganization {
    protected final List<CommandBuffer.DrawElementsIndirectCommand> commands = new ArrayList();
    protected final IndexBuffer entityOffsetBuffer = new IndexBuffer(BufferUtils.createIntBuffer(100));
    protected final IndexBuffer entityOffsetBufferCulled = new IndexBuffer(BufferUtils.createIntBuffer(100));
    protected final CommandBuffer commandBuffer = new CommandBuffer(1600);
    protected final CommandBuffer commandBufferCulled = new CommandBuffer(1600);
    IntArrayList offsets = new IntArrayList();
    AtomicCounterBuffer drawCountBuffer = new AtomicCounterBuffer(1);
    AtomicCounterBuffer drawCountBufferAfterPhase1 = new AtomicCounterBuffer(1);
    AtomicCounterBuffer drawCountBufferAfterPhase2 = new AtomicCounterBuffer(1);

    public CommandOrganization() {
    }
}