package de.hanno.hpengine.engine.graphics.renderer;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy;
import de.hanno.hpengine.engine.model.CommandBuffer;
import de.hanno.hpengine.engine.model.CommandBuffer.DrawElementsIndirectCommand;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import org.lwjgl.BufferUtils;

import java.util.ArrayList;
import java.util.List;

public class Pipeline {

//    TODO: Refactor me
    protected final List<DrawElementsIndirectCommand> commandsStatic = new ArrayList();
    protected final List<DrawElementsIndirectCommand> commandsAnimated = new ArrayList();
    protected final IndexBuffer entityOffsetBufferStatic = new IndexBuffer(BufferUtils.createIntBuffer(100));
    protected final IndexBuffer entityOffsetBufferAnimated = new IndexBuffer(BufferUtils.createIntBuffer(100));
    protected final CommandBuffer commandBufferStatic = new CommandBuffer(1600);
    protected final CommandBuffer commandBufferAnimated = new CommandBuffer(1600);
    private IntArrayList offsetsStatic = new IntArrayList();
    private IntArrayList offsetsAnimated = new IntArrayList();

    private final boolean useBackfaceCulling;
    private final boolean useLineDrawingIfActivated;
    private final boolean useFrustumCulling;

    public Pipeline() {
        this(true, true, true);
    }

    public Pipeline(boolean useFrustumCulling, boolean useBackFaceCulling, boolean useLineDrawingIfActivated) {
        this.useFrustumCulling = useFrustumCulling;
        this.useBackfaceCulling = useBackFaceCulling;
        this.useLineDrawingIfActivated = useLineDrawingIfActivated;
    }

    public void prepareAndDraw(RenderState renderState, Program programStatic, Program programAnimated, FirstPassResult firstPassResult) {
        prepare(renderState);
        draw(renderState, programStatic, programAnimated, firstPassResult);
    }


    public void draw(RenderState renderState, Program programStatic, Program programAnimated, FirstPassResult firstPassResult) {
        GPUProfiler.start("Actual draw entities");
        if(Config.getInstance().isIndirectDrawing()) {
            GPUProfiler.start("Draw with indirect pipeline");
            programStatic.use();
            drawIndirectStatic(renderState, programStatic);
            programAnimated.use();
            drawIndirectAnimated(renderState, programAnimated);
            GPUProfiler.end();

            firstPassResult.verticesDrawn += verticesCount;
            firstPassResult.entitiesDrawn += entitiesDrawn;
        } else {
            renderDirect(renderState, firstPassResult, renderState.getRenderBatchesStatic()); //TODO Animated
        }
        GPUProfiler.end();
    }

    public void drawIndirectStatic(RenderState renderState, Program program) {
//        program.use();
        program.setUniform("entityIndex", 0);
        program.setUniform("entityBaseIndex", 0);
        program.setUniform("indirect", true);
        program.setUniform("entityCount", commandsStatic.size());
        program.bindShaderStorageBuffer(4, getEntityOffsetBufferStatic());
        program.bindShaderStorageBuffer(5, renderState.getVertexIndexBufferStatic().getVertexBuffer());
        program.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer);
        drawIndirect(renderState.getVertexIndexBufferStatic(), commandBufferStatic, commandsStatic.size());
    }

    public void drawIndirectAnimated(RenderState renderState, Program program) {
//        program.use();
        program.setUniform("entityIndex", 0);
        program.setUniform("entityBaseIndex", 0);
        program.setUniform("indirect", true);
        GPUProfiler.start("DrawInstancedIndirectBaseVertex");
        program.setUniform("entityCount", commandsAnimated.size());
        program.bindShaderStorageBuffer(4, getEntityOffsetBufferAnimated());
        program.bindShaderStorageBuffer(5, renderState.getVertexIndexBufferAnimated().getVertexBuffer());
        program.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer);
        drawIndirect(renderState.getVertexIndexBufferAnimated(), commandBufferAnimated, commandsAnimated.size());
    }

    private void drawIndirect(VertexIndexBuffer vertexIndexBuffer, CommandBuffer commandBuffer, int commandCount) {
        IndexBuffer indexBuffer = vertexIndexBuffer.getIndexBuffer();
        VertexBuffer vertexBuffer = vertexIndexBuffer.getVertexBuffer();
        if(Config.getInstance().isDrawLines() && useLineDrawingIfActivated) {
            GraphicsContext.getInstance().disable(GlCap.CULL_FACE);
            VertexBuffer.drawLinesInstancedIndirectBaseVertex(vertexBuffer, indexBuffer, commandBuffer, commandCount);
        } else {
            if(useBackfaceCulling) {
                GraphicsContext.getInstance().enable(GlCap.CULL_FACE);
            }
            VertexBuffer.drawInstancedIndirectBaseVertex(vertexBuffer, indexBuffer, commandBuffer, commandCount);
            indexBuffer.unbind();
        }
    }

    public void renderDirect(RenderState renderState, FirstPassResult firstPassResult, List<RenderBatch> renderBatches) {
        for(RenderBatch info : renderBatches) {
            if (!info.isVisibleForCamera()) {
                continue;
            }
            int currentVerticesCount = DrawStrategy.draw(renderState, info);
            firstPassResult.verticesDrawn += currentVerticesCount;
            if (currentVerticesCount > 0) {
                firstPassResult.entitiesDrawn++;
            }
        }
    }

    private int verticesCount = 0;
    private int entitiesDrawn = 0;
    public void prepare(RenderState renderState) {
        verticesCount = 0;
        entitiesDrawn = 0;
        commandsStatic.clear();
        commandsAnimated.clear();
        offsetsStatic.clear();
        offsetsAnimated.clear();
        addCommands(renderState.getRenderBatchesStatic(), commandsStatic, commandBufferStatic, entityOffsetBufferStatic, offsetsStatic);
        addCommands(renderState.getRenderBatchesAnimated(), commandsAnimated, commandBufferAnimated, entityOffsetBufferAnimated, offsetsAnimated);

    }

    private void addCommands(List<RenderBatch> renderBatches, List<DrawElementsIndirectCommand> commands, CommandBuffer commandBuffer, IndexBuffer entityOffsetBuffer, IntArrayList offsets) {
        for(int i = 0; i < renderBatches.size(); i++) {
            RenderBatch info = renderBatches.get(i);
            if(Config.getInstance().isUseFrustumCulling() && useFrustumCulling && !info.isVisibleForCamera()) {
                continue;
            }
            commands.add(info.getDrawElementsIndirectCommand());
            verticesCount += info.getVertexCount()*info.getInstanceCount();
            if (info.getVertexCount() > 0) {
                entitiesDrawn+=info.getInstanceCount();
            }
            offsets.add(info.getDrawElementsIndirectCommand().entityOffset);
        }

        entityOffsetBuffer.put(0, offsets.toArray());
        commandBuffer.put(Util.toArray(commands, DrawElementsIndirectCommand.class));
    }

    public GPUBuffer getEntityOffsetBufferStatic() {
        return entityOffsetBufferStatic;
    }

    public IndexBuffer getEntityOffsetBufferAnimated() {
        return entityOffsetBufferAnimated;
    }
}
