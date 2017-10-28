package de.hanno.hpengine.engine.graphics.renderer;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy;
import de.hanno.hpengine.engine.graphics.shader.ComputeShaderProgram;
import de.hanno.hpengine.engine.graphics.shader.ProgramFactory;
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
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.util.List;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D;
import static de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SimpleDrawStrategy.renderHighZMap;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL42.GL_ALL_BARRIER_BITS;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;

public class Pipeline {
    protected final CommandOrganization commandOrganizationStatic = new CommandOrganization();
    protected final CommandOrganization commandOrganizationAnimated = new CommandOrganization();

    private final boolean useBackfaceCulling;
    private final boolean useLineDrawingIfActivated;
    private final boolean useFrustumCulling;
    private final ComputeShaderProgram occlusionCullingPhase1;
    private final ComputeShaderProgram occlusionCullingPhase2;

    public Pipeline() {
        this(true, true, true);
    }

    public Pipeline(boolean useFrustumCulling, boolean useBackFaceCulling, boolean useLineDrawingIfActivated) {
        this.useFrustumCulling = useFrustumCulling;
        this.useBackfaceCulling = useBackFaceCulling;
        this.useLineDrawingIfActivated = useLineDrawingIfActivated;
        this.occlusionCullingPhase1 = ProgramFactory.getInstance().getComputeProgram("occlusion_culling1_compute.glsl");
        this.occlusionCullingPhase2 = ProgramFactory.getInstance().getComputeProgram("occlusion_culling2_compute.glsl");
    }

    public void prepareAndDraw(RenderState renderState, Program programStatic, Program programAnimated, FirstPassResult firstPassResult) {
        prepare(renderState);
        draw(renderState, programStatic, programAnimated, firstPassResult);
    }


    public void draw(RenderState renderState, Program programStatic, Program programAnimated, FirstPassResult firstPassResult) {
        GPUProfiler.start("Actual draw entities");
        if(Config.getInstance().isIndirectDrawing()) {
            GPUProfiler.start("Draw with indirect pipeline");
            drawIndirectStatic(renderState, programStatic, commandOrganizationStatic, renderState.getVertexIndexBufferStatic());
            drawIndirectAnimated(renderState, programAnimated, commandOrganizationAnimated, renderState.getVertexIndexBufferAnimated());
            GPUProfiler.end();

            firstPassResult.verticesDrawn += verticesCount;
            firstPassResult.entitiesDrawn += entitiesDrawn;
        } else {
            renderDirect(renderState, firstPassResult, renderState.getRenderBatchesStatic()); //TODO Animated
        }
        GPUProfiler.end();
    }

    public void drawIndirectStatic(RenderState renderState, Program program, CommandOrganization commandOrganization, VertexIndexBuffer vertexIndexBuffer) {
        drawIndirect(renderState, program, commandOrganization, vertexIndexBuffer);
    }

    public void drawIndirectAnimated(RenderState renderState, Program program, CommandOrganization commandOrganization, VertexIndexBuffer vertexIndexBuffer) {
        drawIndirect(renderState, program, commandOrganization, vertexIndexBuffer);
    }

    protected void drawIndirect(RenderState renderState, Program program, CommandOrganization commandOrganization, VertexIndexBuffer vertexIndexBuffer) {
        if(commandOrganization.commands.isEmpty()) { return; }
        commandOrganization.drawCountBufferAfterPhase1.put(0, 0);
        commandOrganization.drawCountBufferAfterPhase2.put(0, 0);
        cull(renderState, commandOrganization, occlusionCullingPhase1);
        renderCulled(renderState, program, commandOrganization, vertexIndexBuffer);
        renderHighZMap();
        cull(renderState, commandOrganization, occlusionCullingPhase2);
        renderCulled(renderState, program, commandOrganization, vertexIndexBuffer);
//        renderHighZMap();

//        glFinish();
//        System.out.println("0: " + (commandOrganization.drawCountBuffer.getBuffer().asIntBuffer().get(0) &0xFF));
//        System.out.println("1: " + (commandOrganization.drawCountBufferAfterPhase1.getBuffer().asIntBuffer().get(0)&0xFF));
//        System.out.println("2: " + (commandOrganization.drawCountBufferAfterPhase2.getBuffer().asIntBuffer().get(0)&0xFF));
    }

    private void renderCulled(RenderState renderState, Program program, CommandOrganization commandOrganization, VertexIndexBuffer vertexIndexBuffer) {
        program.use();
        program.setUniform("entityIndex", 0);
        program.setUniform("entityBaseIndex", 0);
        program.setUniform("indirect", true);
        program.setUniform("entityCount", commandOrganization.commands.size());
        program.bindShaderStorageBuffer(4, commandOrganization.entityOffsetBuffer);
        program.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer);
        drawIndirect(vertexIndexBuffer, commandOrganization.commandBufferCulled, commandOrganization.commands.size(), commandOrganization.drawCountBuffer);
    }

    private void cull(RenderState renderState, CommandOrganization commandOrganization, ComputeShaderProgram occlusionCullingPhase) {
        commandOrganization.drawCountBuffer.put(0, 0);
        occlusionCullingPhase.use();
        occlusionCullingPhase.bindShaderStorageBuffer(2, commandOrganization.drawCountBuffer);
        occlusionCullingPhase.bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer);
        occlusionCullingPhase.bindShaderStorageBuffer(4, commandOrganization.entityOffsetBuffer);
        occlusionCullingPhase.bindShaderStorageBuffer(5, commandOrganization.commandBuffer);
        occlusionCullingPhase.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer);
        occlusionCullingPhase.bindShaderStorageBuffer(7, commandOrganization.commandBufferCulled);
        occlusionCullingPhase.bindShaderStorageBuffer(8, commandOrganization.entityOffsetBufferCulled);
        occlusionCullingPhase.bindShaderStorageBuffer(9, commandOrganization.drawCountBufferAfterPhase1);
        occlusionCullingPhase.bindShaderStorageBuffer(10, commandOrganization.drawCountBufferAfterPhase2);
        occlusionCullingPhase.setUniform("maxDrawCount", commandOrganization.commands.size());
        occlusionCullingPhase.setUniformAsMatrix4("viewProjectionMatrix", renderState.camera.getViewProjectionMatrixAsBuffer());
        occlusionCullingPhase.setUniformAsMatrix4("viewMatrix", renderState.camera.getViewMatrixAsBuffer());
        occlusionCullingPhase.setUniformAsMatrix4("projectionMatrix", renderState.camera.getProjectionMatrixAsBuffer());
        GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, Renderer.getInstance().getGBuffer().getHighZBuffer().getRenderedTexture());
        GraphicsContext.getInstance().bindImageTexture(1, Renderer.getInstance().getGBuffer().getHighZBuffer().getRenderedTexture(), 0, false, 0, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
        occlusionCullingPhase.dispatchCompute((commandOrganization.commands.size()+7)/8,1,1);
//        commandOrganization.drawCountBuffer.put(0, commandOrganization.commands.size());
        glMemoryBarrier(GL_ALL_BARRIER_BITS);
    }

    private void drawIndirect(VertexIndexBuffer vertexIndexBuffer, CommandBuffer commandBuffer, int commandCount, AtomicCounterBuffer drawCountBuffer) {
        IndexBuffer indexBuffer = vertexIndexBuffer.getIndexBuffer();
        VertexBuffer vertexBuffer = vertexIndexBuffer.getVertexBuffer();
        if(Config.getInstance().isDrawLines() && useLineDrawingIfActivated) {
            GraphicsContext.getInstance().disable(GlCap.CULL_FACE);
            VertexBuffer.drawLinesInstancedIndirectBaseVertex(vertexBuffer, indexBuffer, commandBuffer, commandCount);
        } else {
            if(useBackfaceCulling) {
                GraphicsContext.getInstance().enable(GlCap.CULL_FACE);
            }
//            VertexBuffer.multiDrawElementsIndirect(vertexBuffer, indexBuffer, commandBuffer, commandCount);
            VertexBuffer.multiDrawElementsIndirect(vertexBuffer, indexBuffer, commandBuffer, drawCountBuffer, commandCount);
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
        commandOrganizationStatic.commands.clear();
        commandOrganizationAnimated.commands.clear();
        commandOrganizationStatic.offsets.clear();
        commandOrganizationAnimated.offsets.clear();
        addCommands(renderState.getRenderBatchesStatic(), commandOrganizationStatic.commands, commandOrganizationStatic.commandBuffer, commandOrganizationStatic.entityOffsetBuffer, commandOrganizationStatic.offsets);
        addCommands(renderState.getRenderBatchesAnimated(), commandOrganizationAnimated.commands, commandOrganizationAnimated.commandBuffer, commandOrganizationAnimated.entityOffsetBuffer, commandOrganizationAnimated.offsets);

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
        return commandOrganizationStatic.entityOffsetBuffer;
    }

    public IndexBuffer getEntityOffsetBufferAnimated() {
        return commandOrganizationAnimated.entityOffsetBuffer;
    }

}
