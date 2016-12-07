package renderer;

import component.ModelComponent;
import engine.PerEntityInfo;
import engine.model.CommandBuffer;
import engine.model.VertexBuffer;
import renderer.constants.GlCap;
import renderer.drawstrategy.FirstPassResult;
import shader.Program;
import util.Util;

import java.util.*;

public class Pipeline {

    protected final List<CommandBuffer.DrawElementsIndirectCommand> commands = new ArrayList();
    protected final Map<Integer, CommandBuffer.DrawElementsIndirectCommand> commandsMap = new HashMap();
    protected final SortedSet<Integer> keys = new TreeSet<>();
    protected final CommandBuffer commandBuffer = new CommandBuffer(16000);

    public void draw(RenderExtract renderExtract, Program program, FirstPassResult firstPassResult) {
        prepare(renderExtract, firstPassResult);

        program.setUniform("entityIndex", 0);
        program.setUniform("entityBaseIndex", 0);
        program.setUniform("entityCount", commands.size());
        OpenGLContext.getInstance().disable(GlCap.CULL_FACE);
        commandBuffer.bind();
        VertexBuffer.drawInstancedIndirectBaseVertex(ModelComponent.getGlobalVertexBuffer(), ModelComponent.getGlobalIndexBuffer(), commandBuffer.getBuffer(), commands.size());
        ModelComponent.getGlobalIndexBuffer().unbind();
        commandBuffer.unbind();
    }

    public void prepare(RenderExtract renderExtract, FirstPassResult firstPassResult) {
        ModelComponent.getGlobalIndexBuffer().bind();
        commandsMap.clear();
        for (PerEntityInfo info : renderExtract.perEntityInfos()) {
            int currentVerticesCount = info.getIndexCount() / 3;
            int count = info.getIndexCount();
            int firstIndex = info.getIndexOffset();
            int primCount = info.getInstanceCount();
            int baseVertex = info.getBaseVertex();
            int baseInstance = 0;

            CommandBuffer.DrawElementsIndirectCommand command = new CommandBuffer.DrawElementsIndirectCommand(count, primCount, firstIndex, baseVertex, baseInstance, info.getEntityBaseIndex());
            commandsMap.put(info.getEntityIndex(), command);
            firstPassResult.verticesDrawn += currentVerticesCount;
            if (currentVerticesCount > 0) {
                firstPassResult.entitiesDrawn++;
            }
        }
        keys.clear();
        keys.addAll(commandsMap.keySet());
        commands.clear();
        for (Integer key : keys) {
            commands.add(commandsMap.get(key));
        }
        ModelComponent.getGlobalEntityOffsetBuffer().put(0, commands.stream().mapToInt(c -> c.entityOffset).toArray());
        commandBuffer.put(Util.toArray(commands, CommandBuffer.DrawElementsIndirectCommand.class));
    }

}
