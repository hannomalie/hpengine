package de.hanno.hpengine.renderer;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.PerEntityInfo;
import de.hanno.hpengine.engine.model.CommandBuffer;
import de.hanno.hpengine.engine.model.CommandBuffer.DrawElementsIndirectCommand;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.renderer.constants.GlCap;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.shader.OpenGLBuffer;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL42;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL42.glMemoryBarrier;

public class Pipeline {

    protected final List<DrawElementsIndirectCommand> commands = new ArrayList();
    protected final IndexBuffer entityOffsetBuffer = new IndexBuffer(BufferUtils.createIntBuffer(1000));
    protected final CommandBuffer commandBuffer = new CommandBuffer(16000);
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

    public void prepareAndDraw(RenderState renderState, Program program, FirstPassResult firstPassResult) {
        prepare(renderState);

        draw(renderState, program, firstPassResult);
    }

    public void draw(RenderState renderState, Program program, FirstPassResult firstPassResult) {
        GPUProfiler.start("Draw with indirect pipeline");
        program.setUniform("entityIndex", 0);
        program.setUniform("entityBaseIndex", 0);
        program.setUniform("entityCount", commands.size());
        program.setUniform("indirect", true);
        commandBuffer.bind();
        GPUProfiler.start("DrawInstancedIndirectBaseVertex");
        if(Config.DRAWLINES_ENABLED && useLineDrawingIfActivated) {
            if(useBackfaceCulling) { OpenGLContext.getInstance().disable(GlCap.CULL_FACE); }
            VertexBuffer.drawLinesInstancedIndirectBaseVertex(renderState.getVertexBuffer(), renderState.getIndexBuffer(), commandBuffer.getBuffer(), commands.size());
        } else {
            if(useBackfaceCulling) { OpenGLContext.getInstance().enable(GlCap.CULL_FACE); }
            VertexBuffer.drawInstancedIndirectBaseVertex(renderState.getVertexBuffer(), renderState.getIndexBuffer(), commandBuffer.getBuffer(), commands.size());
        }
        GPUProfiler.end();
        renderState.getIndexBuffer().unbind();

        firstPassResult.verticesDrawn += verticesCount;
        firstPassResult.entitiesDrawn += entitiesDrawn;
        GPUProfiler.end();
    }

    int verticesCount = 0;
    int entitiesDrawn = 0;
    IntArrayList offsets = new IntArrayList();
    public void prepare(RenderState renderState) {
        GPUProfiler.start("Preparing indirect pipeline");
        verticesCount = 0;
        entitiesDrawn = 0;
        commands.clear();
        renderState.getIndexBuffer().bind();
        offsets.clear();
        for (PerEntityInfo info : renderState.perEntityInfos()) {
            if(useFrustumCulling && !info.isVisibleForCamera()) {
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
        GPUProfiler.end();
    }

    public OpenGLBuffer getEntityOffsetBuffer() {
        return entityOffsetBuffer;
    }
}
