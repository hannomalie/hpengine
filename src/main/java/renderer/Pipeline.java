package renderer;

import component.ModelComponent;
import config.Config;
import engine.PerEntityInfo;
import engine.model.CommandBuffer;
import engine.model.CommandBuffer.DrawElementsIndirectCommand;
import engine.model.IndexBuffer;
import engine.model.VertexBuffer;
import org.lwjgl.BufferUtils;
import renderer.constants.GlCap;
import renderer.drawstrategy.FirstPassResult;
import shader.OpenGLBuffer;
import shader.Program;
import util.Util;

import java.util.ArrayList;
import java.util.List;

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

    public void prepareAndDraw(RenderExtract renderExtract, Program program, FirstPassResult firstPassResult) {
        prepare(renderExtract);

        draw(program, firstPassResult);
    }

    public void draw(Program program, FirstPassResult firstPassResult) {
        program.setUniform("entityIndex", 0);
        program.setUniform("entityBaseIndex", 0);
        program.setUniform("entityCount", commands.size());
        commandBuffer.bind();
        if(Config.DRAWLINES_ENABLED && useLineDrawingIfActivated) {
            if(useBackfaceCulling) { OpenGLContext.getInstance().disable(GlCap.CULL_FACE); }
            VertexBuffer.drawLinesInstancedIndirectBaseVertex(ModelComponent.getGlobalVertexBuffer(),ModelComponent.getGlobalIndexBuffer(), commandBuffer.getBuffer(), commands.size());
        } else {
            if(useBackfaceCulling) { OpenGLContext.getInstance().enable(GlCap.CULL_FACE); }
            VertexBuffer.drawInstancedIndirectBaseVertex(ModelComponent.getGlobalVertexBuffer(),ModelComponent.getGlobalIndexBuffer(), commandBuffer.getBuffer(), commands.size());
        }
        ModelComponent.getGlobalIndexBuffer().unbind();


        firstPassResult.verticesDrawn += verticesCount;
        firstPassResult.entitiesDrawn += entitiesDrawn;
    }

    int verticesCount = 0;
    int entitiesDrawn = 0;
    public void prepare(RenderExtract renderExtract) {
        verticesCount = 0;
        entitiesDrawn = 0;
        commands.clear();
        ModelComponent.getGlobalIndexBuffer().bind();
        for (PerEntityInfo info : renderExtract.perEntityInfos()) {
            if(useFrustumCulling && !info.isVisibleForCamera()) {
                continue;
            }
            commands.add(info.getDrawElementsIndirectCommand());
            verticesCount += info.getVertexCount();
            if (info.getVertexCount() > 0) {
                entitiesDrawn++;
            }
        }
        entityOffsetBuffer.put(0, commands.stream().mapToInt(c -> c.entityOffset).toArray());
        commandBuffer.put(Util.toArray(commands, DrawElementsIndirectCommand.class));
    }

    public OpenGLBuffer getEntityOffsetBuffer() {
        return entityOffsetBuffer;
    }
}
