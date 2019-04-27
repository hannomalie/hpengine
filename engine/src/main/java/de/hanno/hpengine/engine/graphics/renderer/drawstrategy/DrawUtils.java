package de.hanno.hpengine.engine.graphics.renderer.drawstrategy;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline;
import de.hanno.hpengine.engine.graphics.shader.ComputeShaderProgram;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import org.lwjgl.opengl.GL15;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D;
import static org.lwjgl.opengl.GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;

public class DrawUtils {

    private DrawUtils() {
        super();
    }

    public static int draw(GpuContext gpuContext, RenderState renderState, RenderBatch renderBatch, Program program) {
        return draw(gpuContext, renderState.getVertexIndexBufferStatic().getVertexBuffer(), renderState.getVertexIndexBufferStatic().getIndexBuffer(), renderBatch, program, !renderBatch.isVisible() || !renderBatch.isVisibleForCamera(), true);
    }

    public static int draw(GpuContext gpuContext, VertexBuffer vertexBuffer, IndexBuffer indexBuffer, RenderBatch renderBatch, Program program, boolean invisible, boolean drawLinesIfEnabled) {
        if(invisible) {
            return 0;
        }

        if (program == null) {
            return 0;
        }

        program.setUniform("entityBaseIndex", 0);
        program.setUniform("entityIndex", renderBatch.getEntityBufferIndex());
        program.setUniform("indirect", false);


//        if(material.getMaterialType().equals(SimpleMaterial.MaterialType.FOLIAGE))
        {
            gpuContext.disable(GlCap.CULL_FACE);
        }

        if (Config.getInstance().isDrawLines() && drawLinesIfEnabled) {
            return vertexBuffer.drawLinesInstancedBaseVertex(indexBuffer, renderBatch.getIndexCount(), renderBatch.getInstanceCount(), renderBatch.getIndexOffset(), renderBatch.getBaseVertex());
        } else {
            return vertexBuffer
                .drawInstancedBaseVertex(indexBuffer, renderBatch.getIndexCount(), renderBatch.getInstanceCount(), renderBatch.getIndexOffset(), renderBatch.getBaseVertex());
        }
    }

    public static void renderHighZMap(GpuContext gpuContext, int baseDepthTexture, int baseWidth, int baseHeight, int highZTexture, ComputeShaderProgram highZProgram) {
        GPUProfiler.start("HighZ map calculation");
        highZProgram.use();
        int lastWidth = baseWidth;
        int lastHeight = baseHeight;
        int currentWidth = lastWidth /2;
        int currentHeight = lastHeight/2;
        int mipMapCount = Util.calculateMipMapCount(currentWidth, currentHeight);
        for(int mipmapTarget = 0; mipmapTarget < mipMapCount; mipmapTarget++ ) {
            highZProgram.setUniform("width", currentWidth);
            highZProgram.setUniform("height", currentHeight);
            highZProgram.setUniform("lastWidth", lastWidth);
            highZProgram.setUniform("lastHeight", lastHeight);
            highZProgram.setUniform("mipmapTarget", mipmapTarget);
            if(mipmapTarget == 0) {
                gpuContext.bindTexture(0, TEXTURE_2D, baseDepthTexture);
            } else {
                gpuContext.bindTexture(0, TEXTURE_2D, highZTexture);
            }
            gpuContext.bindImageTexture(1, highZTexture, mipmapTarget, false, 0, GL15.GL_READ_WRITE, Pipeline.Companion.getHIGHZ_FORMAT());
            int num_groups_x = Math.max(1, (currentWidth + 7) / 8);
            int num_groups_y = Math.max(1, (currentHeight + 7) / 8);
            highZProgram.dispatchCompute(num_groups_x, num_groups_y, 1);
            lastWidth = currentWidth;
            lastHeight = currentHeight;
            currentWidth /= 2;
            currentHeight /= 2;
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
//            glMemoryBarrier(GL_ALL_BARRIER_BITS);
        }
        GPUProfiler.end();
    }


    //    TODO: Reimplement this functionality
//    private void debugDrawProbes(Camera de.hanno.hpengine.camera, RenderState extract) {
//        Entity probeBoxEntity = Renderer.getInstance().getGBuffer().getProbeBoxEntity();
//
//        probeFirstpassProgram.use();
//        EnvironmentProbeManager.getInstance().bindEnvironmentProbePositions(probeFirstpassProgram);
//        OpenGLContext.getInstance().activeTexture(8);
//        EnvironmentProbeManager.getInstance().getEnvironmentMapsArray(3).bind();
//        probeFirstpassProgram.setUniform("showContent", Config.getInstance().DEBUGDRAW_PROBES_WITH_CONTENT);
//
//        Vector3f oldMaterialColor = new Vector3f(probeBoxEntity.getComponent(ModelComponent.class).getMaterials().getDiffuse());
//
//        for (EnvironmentProbe probe : EnvironmentProbeManager.getInstance().getProbes()) {
//            Transform transform = new Transform();
//            transform.setPosition(probe.getCenter());
//            transform.setScale(probe.getSize());
//            Vector3f colorHelper = probe.getDebugColor();
//            probeBoxEntity.getComponent(ModelComponent.class).getMaterials().setDiffuse(colorHelper);
//            probeBoxEntity.setTransform(transform);
//            probeBoxEntity.update(0);
//            probeFirstpassProgram.setUniform("probeCenter", probe.getCenter());
//            probeFirstpassProgram.setUniform("probeIndex", probe.getIndex());
//            probeBoxEntity.getComponent(ModelComponent.class).draw(extract, de.hanno.hpengine.camera, probeBoxEntity.getModelMatrixAsBuffer(), -1);
//        }
//
//        probeBoxEntity.getComponent(ModelComponent.class).getMaterials().getDiffuse().x = oldMaterialColor.x;
//        probeBoxEntity.getComponent(ModelComponent.class).getMaterials().getDiffuse().y = oldMaterialColor.y;
//        probeBoxEntity.getComponent(ModelComponent.class).getMaterials().getDiffuse().z = oldMaterialColor.z;
//
//    }

}
