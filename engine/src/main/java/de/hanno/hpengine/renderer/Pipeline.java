package de.hanno.hpengine.renderer;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.engine.PerMeshInfo;
import de.hanno.hpengine.engine.model.CommandBuffer;
import de.hanno.hpengine.engine.model.CommandBuffer.DrawElementsIndirectCommand;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.renderer.constants.GlCap;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.renderer.state.RenderState;
import de.hanno.hpengine.shader.OpenGLBuffer;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import org.lwjgl.BufferUtils;

import java.util.ArrayList;
import java.util.List;

public class Pipeline {

    protected final List<DrawElementsIndirectCommand> commands = new ArrayList();
    protected final IndexBuffer entityOffsetBuffer = new IndexBuffer(BufferUtils.createIntBuffer(100));
    protected final CommandBuffer commandBuffer = new CommandBuffer(1600);
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
        GPUProfiler.start("DrawInstancedIndirectBaseVertex");
        if(Config.getInstance().isDrawLines() && useLineDrawingIfActivated) {
            GraphicsContext.getInstance().disable(GlCap.CULL_FACE);
            VertexBuffer.drawLinesInstancedIndirectBaseVertex(renderState.getVertexBuffer(), renderState.getIndexBuffer(), commandBuffer, commands.size());
        } else {
            if(useBackfaceCulling) {
                GraphicsContext.getInstance().enable(GlCap.CULL_FACE);
            }
            VertexBuffer.drawInstancedIndirectBaseVertex(renderState.getVertexBuffer(), renderState.getIndexBuffer(), commandBuffer, commands.size());
        }
        GPUProfiler.end();
        renderState.getIndexBuffer().unbind();

        firstPassResult.verticesDrawn += verticesCount;
        firstPassResult.entitiesDrawn += entitiesDrawn;
        GPUProfiler.end();
    }

    private int verticesCount = 0;
    private int entitiesDrawn = 0;
    private IntArrayList offsets = new IntArrayList();
    public void prepare(RenderState renderState) {
        verticesCount = 0;
        entitiesDrawn = 0;
        commands.clear();
        renderState.getIndexBuffer().bind();
        offsets.clear();
        for(int i = 0; i < renderState.perEntityInfos().size(); i++) {
            PerMeshInfo info = renderState.perEntityInfos().get(i);
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

    public OpenGLBuffer getEntityOffsetBuffer() {
        return entityOffsetBuffer;
    }
}
