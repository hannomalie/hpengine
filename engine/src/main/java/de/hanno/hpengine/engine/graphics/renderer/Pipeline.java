package de.hanno.hpengine.engine.graphics.renderer;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder;
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
import org.lwjgl.opengl.*;

import java.util.List;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D;
import static de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SimpleDrawStrategy.renderHighZMap;
import static de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.VoxelConeTracingExtension.ZERO_BUFFER;
import static de.hanno.hpengine.engine.model.VertexBuffer.*;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;

public class Pipeline {
    protected final CommandOrganization commandOrganizationStatic = new CommandOrganization();
    protected final CommandOrganization commandOrganizationAnimated = new CommandOrganization();

    public static int HIGHZ_FORMAT = GL30.GL_RGBA32F;
    private RenderTarget highZBuffer;

    private final boolean useBackfaceCulling;
    private final boolean useLineDrawingIfActivated;
    private final boolean useFrustumCulling;
    private Program occlusionCullingPhase1Vertex;
    private Program occlusionCullingPhase2Vertex;

    public Pipeline() {
        this(true, true, true);
    }

    public Pipeline(boolean useFrustumCulling, boolean useBackFaceCulling, boolean useLineDrawingIfActivated) {
        this.useFrustumCulling = useFrustumCulling;
        this.useBackfaceCulling = useBackFaceCulling;
        this.useLineDrawingIfActivated = useLineDrawingIfActivated;
        try {
            this.occlusionCullingPhase1Vertex = ProgramFactory.getInstance().getProgram("occlusion_culling1_vertex.glsl", null);//"append_drawcommands_fragment.glsl");
            this.occlusionCullingPhase2Vertex = ProgramFactory.getInstance().getProgram("occlusion_culling2_vertex.glsl", null);//"append_drawcommands_fragment.glsl");
        } catch (Exception e) {
            System.exit(-1);
            e.printStackTrace();
        }

        highZBuffer = new RenderTargetBuilder().setWidth(Config.getInstance().getWidth()/2).setHeight(Config.getInstance().getHeight()/2)
                .add(new ColorAttachmentDefinition().setInternalFormat(HIGHZ_FORMAT)
                        .setTextureFilter(GL11.GL_NEAREST_MIPMAP_NEAREST))
                .build();
    }

    public void prepareAndDraw(RenderState renderState, Program programStatic, Program programAnimated, FirstPassResult firstPassResult) {
        prepare(renderState);
        draw(renderState, programStatic, programAnimated, firstPassResult);
    }


    public void draw(RenderState renderState, Program programStatic, Program programAnimated, FirstPassResult firstPassResult) {
        GPUProfiler.start("Actual draw entities");
        drawStaticAndAnimated(new DrawDescription(renderState, programStatic, commandOrganizationStatic, renderState.getVertexIndexBufferStatic()),
                              new DrawDescription(renderState, programAnimated, commandOrganizationAnimated, renderState.getVertexIndexBufferAnimated()));

        firstPassResult.verticesDrawn += verticesCount;
        firstPassResult.entitiesDrawn += entitiesDrawn;
        GPUProfiler.end();
    }

    protected void drawStaticAndAnimated(DrawDescription drawDescriptionStatic, DrawDescription drawDescriptionAnimated) {
        if(Config.getInstance().isUseOcclusionCulling()) {

            ARBClearTexture.glClearTexImage(highZBuffer.getRenderedTexture(), 0, GL_RGBA, GL11.GL_UNSIGNED_BYTE, ZERO_BUFFER);

            CommandOrganization commandOrganizationStatic = drawDescriptionStatic.getCommandOrganization();
            CommandOrganization commandOrganizationAnimated = drawDescriptionAnimated.getCommandOrganization();
            commandOrganizationStatic.drawCountBufferAfterPhase1.put(0,0);
            commandOrganizationStatic.drawCountBufferAfterPhase2.put(0,0);
            commandOrganizationAnimated.drawCountBufferAfterPhase1.put(0,0);
            commandOrganizationAnimated.drawCountBufferAfterPhase2.put(0,0);

            GPUProfiler.start("Cull&Render Phase1");
            cullAndRender(drawDescriptionStatic.getRenderState(), drawDescriptionStatic.getProgram(), commandOrganizationStatic, drawDescriptionStatic.getVertexIndexBuffer(), occlusionCullingPhase1Vertex, true, commandOrganizationStatic.drawCountBufferStaticPhase1, commandOrganizationStatic.commandBufferCulledPhase1);
            cullAndRender(drawDescriptionAnimated.getRenderState(), drawDescriptionAnimated.getProgram(), commandOrganizationAnimated, drawDescriptionAnimated.getVertexIndexBuffer(), occlusionCullingPhase1Vertex, false, commandOrganizationAnimated.drawCountBufferAnimatedPhase1, commandOrganizationAnimated.commandBufferCulledPhase1);
            renderHighZMap(Renderer.getInstance().getGBuffer().getVisibilityMap(), Config.getInstance().getWidth(), Config.getInstance().getHeight(), highZBuffer.getRenderedTexture(), ProgramFactory.getInstance().getHighZProgram());
            GPUProfiler.end();

            GPUProfiler.start("Cull&Render Phase2");
            cullAndRender(drawDescriptionStatic.getRenderState(), drawDescriptionStatic.getProgram(), commandOrganizationStatic, drawDescriptionStatic.getVertexIndexBuffer(), occlusionCullingPhase2Vertex, true, commandOrganizationStatic.drawCountBufferStaticPhase2, commandOrganizationStatic.commandBufferCulledPhase2);
            cullAndRender(drawDescriptionAnimated.getRenderState(), drawDescriptionAnimated.getProgram(), commandOrganizationAnimated, drawDescriptionAnimated.getVertexIndexBuffer(), occlusionCullingPhase2Vertex, false, commandOrganizationAnimated.drawCountBufferAnimatedPhase2, commandOrganizationAnimated.commandBufferCulledPhase2);
            GPUProfiler.end();
            renderHighZMap(Renderer.getInstance().getGBuffer().getVisibilityMap(), Config.getInstance().getWidth(), Config.getInstance().getHeight(), highZBuffer.getRenderedTexture(), ProgramFactory.getInstance().getHighZProgram());

            printDebugOutput(commandOrganizationStatic, commandOrganizationAnimated);
        } else {
            drawDescriptionStatic.getCommandOrganization().drawCountBufferStatic.put(0, commandOrganizationStatic.commands.size());
            render(drawDescriptionStatic.getRenderState(), drawDescriptionStatic.getProgram(), commandOrganizationStatic, drawDescriptionStatic.getVertexIndexBuffer(), true, commandOrganizationStatic.drawCountBufferStatic, commandOrganizationStatic.commandBuffer, commandOrganizationStatic.entityOffsetBuffer);
            drawDescriptionStatic.getCommandOrganization().drawCountBufferStatic.put(0, commandOrganizationStatic.commands.size());
            render(drawDescriptionAnimated.getRenderState(), drawDescriptionAnimated.getProgram(), commandOrganizationAnimated, drawDescriptionAnimated.getVertexIndexBuffer(), false, commandOrganizationStatic.drawCountBufferStatic, commandOrganizationStatic.commandBuffer, commandOrganizationAnimated.entityOffsetBuffer);
        }
    }

    private void cullAndRender(RenderState renderState, Program program, CommandOrganization commandOrganization, VertexIndexBuffer vertexIndexBuffer, Program occlusionCullingPhase, boolean staticRendering, AtomicCounterBuffer drawCountBuffer, CommandBuffer targetCommandBuffer) {
        drawCountBuffer.put(0, 0);
        if(commandOrganization.commands.isEmpty()) { return; }

        cullPhase(renderState, commandOrganization, occlusionCullingPhase, drawCountBuffer, targetCommandBuffer);
        render(renderState, program, commandOrganization, vertexIndexBuffer, staticRendering, drawCountBuffer, targetCommandBuffer, commandOrganization.entityOffsetBufferCulled);
    }

    private void cullPhase(RenderState renderState, CommandOrganization commandOrganization, Program occlusionCullingPhaseProgram, AtomicCounterBuffer drowCountBuffer, CommandBuffer targetCommandBuffer) {
        GPUProfiler.start("Culling Phase");
        cull(renderState, commandOrganization, occlusionCullingPhaseProgram, targetCommandBuffer);

        drowCountBuffer.put(0, 0);
        Program appendProgram = ProgramFactory.getInstance().getAppendDrawCommandProgram();
        appendProgram.use();
        appendProgram.bindShaderStorageBuffer(2, drowCountBuffer);
        appendProgram.bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer);
        appendProgram.bindShaderStorageBuffer(4, commandOrganization.entityOffsetBuffer);
        appendProgram.bindShaderStorageBuffer(5, commandOrganization.commandBuffer);
        appendProgram.bindShaderStorageBuffer(7, targetCommandBuffer);
        appendProgram.bindShaderStorageBuffer(8, commandOrganization.entityOffsetBufferCulled);
        appendProgram.setUniform("maxDrawCommands", commandOrganization.commands.size());
        commandOrganization.commandBufferCulledPhase1.setSizeInBytes(commandOrganization.commandBuffer.getSizeInBytes());
        commandOrganization.commandBufferCulledPhase2.setSizeInBytes(commandOrganization.commandBuffer.getSizeInBytes());
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, ((commandOrganization.commands.size() + 2) / 3 * 3), 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT | GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);
        GPUProfiler.end();
    }

    private void render(RenderState renderState, Program program, CommandOrganization commandOrganization, VertexIndexBuffer vertexIndexBuffer, boolean staticRendering, AtomicCounterBuffer drawCountBuffer, CommandBuffer commandBuffer, IndexBuffer offsetBuffer) {
        GPUProfiler.start("Actually render");
        program.use();
        if(staticRendering) { // Just to avoid the cost of a lambda....
            beforeDrawStatic(renderState, program);
        } else {
            beforeDrawAnimated(renderState, program);
        }
        program.setUniform("entityIndex", 0);
        program.setUniform("entityBaseIndex", 0);
        program.setUniform("indirect", true);
        program.bindShaderStorageBuffer(4, offsetBuffer);
        program.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer);
        if(Config.getInstance().isIndirectRendering()) {
            drawIndirect(vertexIndexBuffer, commandBuffer, commandOrganization.commands.size(), drawCountBuffer);
        } else {
            for(int i = 0; i < commandOrganization.commands.size(); i++) {
                DrawElementsIndirectCommand command = commandOrganization.commands.get(i);
                program.setUniform("entityIndex", commandOrganization.offsets.get(i));
                program.setUniform("entityBaseIndex", 0);
                program.setUniform("indirect", false);
                vertexIndexBuffer.getVertexBuffer()
                        .drawInstancedBaseVertex(vertexIndexBuffer.getIndexBuffer(), command.count, command.primCount, command.firstIndex, command.baseVertex);
            }
        }
        GPUProfiler.end();
    }

    private void cull(RenderState renderState, CommandOrganization commandOrganization, Program occlusionCullingPhase, CommandBuffer targetCommandBuffer) {
        occlusionCullingPhase.use();
        occlusionCullingPhase.bindShaderStorageBuffer(3, renderState.entitiesState.entitiesBuffer);
        occlusionCullingPhase.bindShaderStorageBuffer(4, commandOrganization.entityOffsetBuffer);
        occlusionCullingPhase.bindShaderStorageBuffer(5, commandOrganization.commandBuffer);
        occlusionCullingPhase.bindShaderStorageBuffer(6, renderState.entitiesState.jointsBuffer);
        occlusionCullingPhase.bindShaderStorageBuffer(7, targetCommandBuffer);
        occlusionCullingPhase.bindShaderStorageBuffer(8, commandOrganization.entityOffsetBufferCulled);
        occlusionCullingPhase.bindShaderStorageBuffer(9, commandOrganization.drawCountBufferAfterPhase1);
        occlusionCullingPhase.bindShaderStorageBuffer(10, commandOrganization.drawCountBufferAfterPhase2);
        occlusionCullingPhase.setUniform("maxDrawCommands", commandOrganization.commands.size());
        occlusionCullingPhase.setUniformAsMatrix4("viewProjectionMatrix", renderState.camera.getViewProjectionMatrixAsBuffer());
        occlusionCullingPhase.setUniformAsMatrix4("viewMatrix", renderState.camera.getViewMatrixAsBuffer());
        occlusionCullingPhase.setUniformAsMatrix4("projectionMatrix", renderState.camera.getProjectionMatrixAsBuffer());
        GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, highZBuffer.getRenderedTexture());
        GraphicsContext.getInstance().bindImageTexture(1, highZBuffer.getRenderedTexture(), 0, false, 0, GL15.GL_WRITE_ONLY, HIGHZ_FORMAT);
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, ((commandOrganization.commands.size() + 2) / 3 * 3), 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private void drawIndirect(VertexIndexBuffer vertexIndexBuffer, CommandBuffer commandBuffer, int commandCount, AtomicCounterBuffer drawCountBuffer) {
        IndexBuffer indexBuffer = vertexIndexBuffer.getIndexBuffer();
        VertexBuffer vertexBuffer = vertexIndexBuffer.getVertexBuffer();
        if(Config.getInstance().isDrawLines() && useLineDrawingIfActivated) {
            GraphicsContext.getInstance().disable(GlCap.CULL_FACE);
            drawLinesInstancedIndirectBaseVertex(vertexBuffer, indexBuffer, commandBuffer, commandCount);
        } else {
            if(useBackfaceCulling) {
                GraphicsContext.getInstance().enable(GlCap.CULL_FACE);
            }
            if(Config.getInstance().isUseOcclusionCulling()) {
                multiDrawElementsIndirectCount(vertexBuffer, indexBuffer, commandBuffer, drawCountBuffer, commandCount);
            } else {
                multiDrawElementsIndirect(vertexBuffer, indexBuffer, commandBuffer, commandCount);
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
            if(/*info.isVisible() ||*/ (Config.getInstance().isUseFrustumCulling() && useFrustumCulling && !info.isVisibleForCamera())) {
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

    protected void beforeDrawStatic(RenderState renderState, Program program) {}
    protected void beforeDrawAnimated(RenderState renderState, Program program) {}

    private void printDebugOutput(CommandOrganization commandOrganizationStatic, CommandOrganization commandOrganizationAnimated) {
        final boolean debugOutput = false;
        if(debugOutput) {
            glFinish();
            System.out.println("######################");
            System.out.println("Static commands came in: " + commandOrganizationStatic.commands.size());
            System.out.println("Animated commands came in: " + commandOrganizationAnimated.commands.size());
            int drawCountBufferStaticPhase1 = Byte.toUnsignedInt(commandOrganizationStatic.drawCountBufferStaticPhase1.getBuffer().get(0));
            int drawCountBufferStaticPhase2 = Byte.toUnsignedInt(commandOrganizationStatic.drawCountBufferStaticPhase2.getBuffer().get(0));
            System.out.println("drawCountBufferStaticPhase1: " + drawCountBufferStaticPhase1);
            System.out.println("drawCountBufferStaticPhase2: " + (Byte.toUnsignedInt(commandOrganizationStatic.drawCountBufferStaticPhase2.getBuffer().get(0))));
            System.out.println("Static false positives: " + (Byte.toUnsignedInt(commandOrganizationStatic.drawCountBufferAfterPhase2.getBuffer().get(0))));
            int drawCountBufferAnimatedPhase1 = Byte.toUnsignedInt(commandOrganizationAnimated.drawCountBufferAnimatedPhase1.getBuffer().get(0));
            System.out.println("drawCountBufferAnimatedPhase1: " + drawCountBufferAnimatedPhase1);
            System.out.println("drawCountBufferAnimatedPhase2: " + (Byte.toUnsignedInt(commandOrganizationAnimated.drawCountBufferAnimatedPhase2.getBuffer().get(0))));
            System.out.println("Anim   false positives: " + (Byte.toUnsignedInt(commandOrganizationAnimated.drawCountBufferAfterPhase2.getBuffer().get(0))));
            boolean printBuffers = false;
            if(printBuffers) {
                System.out.println("Command buffer static:");
                Util.printIntBuffer(commandOrganizationStatic.commandBuffer.getBuffer().asIntBuffer(), 5, commandOrganizationStatic.commands.size());
                System.out.println("Command buffer culled static phase 1:");
                Util.printIntBuffer(commandOrganizationStatic.commandBufferCulledPhase1.getBuffer().asIntBuffer(), 5, drawCountBufferStaticPhase1);
                System.out.println("Command buffer culled static phase 2:");
                Util.printIntBuffer(commandOrganizationStatic.commandBufferCulledPhase2.getBuffer().asIntBuffer(), 5, drawCountBufferStaticPhase2);
                System.out.print("Static Offsets ");
                Util.printIntBuffer(commandOrganizationStatic.entityOffsetBuffer.getBuffer().asIntBuffer(), commandOrganizationStatic.commands.size(), 1);
                System.out.print("Static Offsets culled ");
                Util.printIntBuffer(commandOrganizationStatic.entityOffsetBufferCulled.getBuffer().asIntBuffer(), commandOrganizationStatic.commands.size(), 1);
                System.out.println("Command buffer animated:");
                Util.printIntBuffer(commandOrganizationAnimated.commandBuffer.getBuffer().asIntBuffer(), 5, commandOrganizationAnimated.commands.size());
                System.out.println("Command buffer culled animated:");
                Util.printIntBuffer(commandOrganizationAnimated.commandBufferCulledPhase1.getBuffer().asIntBuffer(), 5, drawCountBufferAnimatedPhase1);
                System.out.print("Animated Offsets ");
                Util.printIntBuffer(commandOrganizationAnimated.entityOffsetBuffer.getBuffer().asIntBuffer(), commandOrganizationAnimated.commands.size(), 1);
                System.out.print("Animated Offsets culled ");
                Util.printIntBuffer(commandOrganizationAnimated.entityOffsetBufferCulled.getBuffer().asIntBuffer(),  commandOrganizationAnimated.commands.size(), 1);
            }
        }
    }
}
