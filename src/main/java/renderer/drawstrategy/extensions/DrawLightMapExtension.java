package renderer.drawstrategy.extensions;

import component.ModelComponent;
import config.Config;
import engine.AppContext;
import engine.PerEntityInfo;
import engine.Transform;
import engine.model.CommandBuffer;
import engine.model.EntityFactory;
import engine.model.VertexBuffer;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.ReadableVector3f;
import org.lwjgl.util.vector.Vector3f;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.Renderer;
import renderer.constants.GlCap;
import renderer.drawstrategy.DrawStrategy;
import renderer.drawstrategy.FirstPassResult;
import renderer.drawstrategy.SimpleDrawStrategy;
import renderer.material.MaterialFactory;
import renderer.rendertarget.ColorAttachmentDefinition;
import renderer.rendertarget.RenderTarget;
import renderer.rendertarget.RenderTargetBuilder;
import shader.Program;
import shader.ProgramFactory;
import texture.TextureFactory;
import util.stopwatch.GPUProfiler;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static renderer.constants.GlCap.CULL_FACE;
import static renderer.constants.GlCap.DEPTH_TEST;

public class DrawLightMapExtension implements RenderExtension {

    private static boolean DRAW_LIGHTMAP = false;
    private final Program lightMapProgram;
    private final FloatBuffer identityMatrix44Buffer;

    private List<CommandBuffer.DrawElementsIndirectCommand> commands = new ArrayList();
    private Map<Integer, CommandBuffer.DrawElementsIndirectCommand> commandsMap = new HashMap();
    private SortedSet<Integer> keys = new TreeSet<>();
    CommandBuffer globalCommandBuffer = new CommandBuffer(16000);

    private final RenderTarget lightMapTarget = new RenderTargetBuilder()
            .setWidth(1000)
            .setHeight(1000)
            .removeDepthAttachment()
            .add(new ColorAttachmentDefinition().setInternalFormat(GL30.GL_RGBA16F))
            .build();

    public DrawLightMapExtension() throws Exception {
        identityMatrix44Buffer = new Transform().getTransformationBuffer();
        lightMapProgram = ProgramFactory.getInstance().getProgram("lightmap_vertex.glsl", "lightmap_fragment.glsl");
    }

    @Override
    public void renderFirstPass(RenderExtract renderExtract, FirstPassResult firstPassResult) {
        if(DRAW_LIGHTMAP && renderExtract.directionalLightNeedsShadowMapRender) {
            lightMapTarget.use(true);
            OpenGLContext openGLContext = OpenGLContext.getInstance();
            openGLContext.disable(CULL_FACE);
            openGLContext.depthMask(false);
            openGLContext.disable(DEPTH_TEST);

            lightMapProgram.use();

            lightMapProgram.bindShaderStorageBuffer(1, MaterialFactory.getInstance().getMaterialBuffer());
            lightMapProgram.bindShaderStorageBuffer(3, EntityFactory.getInstance().getEntitiesBuffer());
            lightMapProgram.bindShaderStorageBuffer(4, ModelComponent.getGlobalEntityOffsetBuffer());

            lightMapProgram.setUniformAsMatrix4("shadowMatrix", renderExtract.directionalLight.getViewProjectionMatrixAsBuffer());
            lightMapProgram.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer);
            lightMapProgram.setUniformAsMatrix4("viewMatrix", renderExtract.camera.getViewMatrixAsBuffer());
            lightMapProgram.setUniformAsMatrix4("projectionMatrix", renderExtract.camera.getProjectionMatrixAsBuffer());
            lightMapProgram.setUniform("lightDirection", renderExtract.directionalLight.getDirection());
            lightMapProgram.setUniform("lightDiffuse", renderExtract.directionalLight.getColor());


            GPUProfiler.start("Actual draw entities");
            ModelComponent.getGlobalIndexBuffer().bind();
            commandsMap.clear();
            for(PerEntityInfo info : renderExtract.perEntityInfos()) {
                OpenGLContext.getInstance().disable(GlCap.CULL_FACE);
                int currentVerticesCount = info.getIndexCount()/3;
                if(!SimpleDrawStrategy.INDIRECT_DRAWING) {
                    currentVerticesCount = DrawStrategy.draw(info);
                } else {
//                    info.getMaterial().setTexturesUsed();
                    int count = info.getIndexCount();
                    int firstIndex = info.getIndexOffset();
                    int primCount = info.getInstanceCount();
                    int baseVertex = info.getBaseVertex();
                    int baseInstance = 0;

                    CommandBuffer.DrawElementsIndirectCommand command = new CommandBuffer.DrawElementsIndirectCommand(count, primCount, firstIndex, baseVertex, baseInstance, info.getEntityBaseIndex());
                    commandsMap.put(info.getEntityIndex(), command);
                }

                firstPassResult.verticesDrawn += currentVerticesCount;
                if (currentVerticesCount > 0) {
                    firstPassResult.entitiesDrawn++;
                }
            }
            if(SimpleDrawStrategy.INDIRECT_DRAWING) {
                keys.clear();
                keys.addAll(commandsMap.keySet());
                commands.clear();
                for (Integer key : keys) {
                    commands.add(commandsMap.get(key));
                }
                lightMapProgram.setUniform("entityIndex", 0);
                lightMapProgram.setUniform("entityBaseIndex", 0);
                lightMapProgram.setUniform("entityCount", commands.size());
                ModelComponent.getGlobalEntityOffsetBuffer().put(0, commands.stream().mapToInt(c -> c.entityOffset).toArray());
                globalCommandBuffer.put(util.Util.toArray(commands, CommandBuffer.DrawElementsIndirectCommand.class));
                globalCommandBuffer.bind();
                VertexBuffer.drawInstancedIndirectBaseVertex(ModelComponent.getGlobalVertexBuffer(),ModelComponent.getGlobalIndexBuffer(), globalCommandBuffer.getBuffer(), commands.size());
                ModelComponent.getGlobalIndexBuffer().unbind();
                globalCommandBuffer.unbind();
            }
            openGLContext.enable(CULL_FACE);
            openGLContext.depthMask(true);
            openGLContext.enable(DEPTH_TEST);

//            TextureFactory.getInstance().blur2DTextureRGBA16F(lightMapTarget.getRenderedTexture(), lightMapTarget.getWidth(), lightMapTarget.getHeight(), 0, 0);
        }
    }

    public RenderTarget getLightMapTarget() {
        return lightMapTarget;
    }

}
