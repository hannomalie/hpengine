package renderer.drawstrategy.extensions;

import camera.Camera;
import component.ModelComponent;
import engine.AppContext;
import engine.Transform;
import engine.model.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBClearTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL42;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.Renderer;
import renderer.drawstrategy.FirstPassResult;
import renderer.drawstrategy.GBuffer;
import renderer.material.MaterialFactory;
import scene.Scene;
import shader.ComputeShaderProgram;
import shader.Program;
import shader.ProgramFactory;
import util.stopwatch.GPUProfiler;

import java.nio.FloatBuffer;

import static renderer.constants.GlCap.*;
import static renderer.constants.GlTextureTarget.TEXTURE_2D;

public class VoxelConeTracingExtension implements AfterFirstPassExtension {

    private Program voxelizer;
    private ComputeShaderProgram texture3DMipMappingComputeProgram;

    private static Matrix4f ortho = util.Util.createOrthogonal(-GBuffer.gridSizeHalfScaled, GBuffer.gridSizeHalfScaled, GBuffer.gridSizeHalfScaled, -GBuffer.gridSizeHalfScaled, GBuffer.gridSizeHalfScaled, -GBuffer.gridSizeHalfScaled);
    private static Camera orthoCam = new Camera(ortho, GBuffer.gridSizeHalfScaled, -GBuffer.gridSizeHalfScaled, 90, 1);
    private static Matrix4f viewX;
    private static FloatBuffer viewXBuffer = BufferUtils.createFloatBuffer(16);
    private static Matrix4f viewY;
    private static FloatBuffer viewYBuffer = BufferUtils.createFloatBuffer(16);
    private static Matrix4f viewZ;
    private static FloatBuffer viewZBuffer = BufferUtils.createFloatBuffer(16);
    static {
        orthoCam.setPerspective(false);
        orthoCam.setWidth(GBuffer.gridSizeScaled);
        orthoCam.setHeight(GBuffer.gridSizeScaled);
        orthoCam.setFar(-5000);
        orthoCam.update(0.000001f);
        {
            Transform view = new Transform();
            view.rotate(new Vector3f(0,1,0), 90f);
            viewX = Matrix4f.mul(ortho, view.getViewMatrix(), null);
            viewXBuffer.rewind();
            viewX.store(viewXBuffer);
            viewXBuffer.rewind();
        }
        {
            Transform view = new Transform();
            view.rotate(new Vector3f(1, 0, 0), 90f);
            viewY = Matrix4f.mul(ortho, view.getViewMatrix(), null);
            viewYBuffer.rewind();
            viewY.store(viewYBuffer);
            viewYBuffer.rewind();
        }
        {
            Transform view = new Transform();
            view.rotate(new Vector3f(0,1,0), 180f);
            viewZ = Matrix4f.mul(ortho, view.getViewMatrix(), null);
            viewZBuffer.rewind();
            viewZ.store(viewZBuffer);
            viewZBuffer.rewind();
        }
    }

    static FloatBuffer zeroBuffer = BufferUtils.createFloatBuffer(4);
    static {
        zeroBuffer.put(0);
        zeroBuffer.put(0);
        zeroBuffer.put(0);
        zeroBuffer.put(0);
        zeroBuffer.rewind();
    }

    public VoxelConeTracingExtension() {
        voxelizer = ProgramFactory.getInstance().getProgram("voxelize_vertex.glsl", "voxelize_geometry.glsl", "voxelize_fragment.glsl", ModelComponent.DEFAULTCHANNELS, true);
        texture3DMipMappingComputeProgram = ProgramFactory.getInstance().getComputeProgram("texture3D_mipmap_compute.glsl");
    }

    @Override
    public void run(RenderExtract renderExtract, FirstPassResult firstPassResult) {

        boolean entityOrDirectionalLightHasMoved = renderExtract.anEntityHasMoved || renderExtract.directionalLightNeedsShadowMapRender;
        boolean useVoxelConeTracing = true;
        boolean clearVoxels = true;
        if(useVoxelConeTracing && clearVoxels && entityOrDirectionalLightHasMoved) {
            GPUProfiler.start("Clear voxels");
            ARBClearTexture.glClearTexImage(Renderer.getInstance().getGBuffer().grid, 0, GBuffer.gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
            GPUProfiler.end();
        }
        if(useVoxelConeTracing && entityOrDirectionalLightHasMoved) {
            orthoCam.update(0.000001f);
            int gridSizeScaled = (int) (Renderer.getInstance().getGBuffer().gridSize * Renderer.getInstance().getGBuffer().sceneScale);
            OpenGLContext.getInstance().viewPort(0,0, gridSizeScaled, gridSizeScaled);

            voxelizer.use();
            GL42.glBindImageTexture(5, Renderer.getInstance().getGBuffer().grid, 0, true, 0, GL15.GL_READ_WRITE, GBuffer.gridTextureFormatSized);
            Scene scene = AppContext.getInstance().getScene();
            if(scene != null) {
                voxelizer.setUniformAsMatrix4("shadowMatrix", scene.getDirectionalLight().getViewProjectionMatrixAsBuffer());
                voxelizer.setUniform("lightDirection", scene.getDirectionalLight().getDirection());
                voxelizer.setUniform("lightColor", scene.getDirectionalLight().getColor());
            }
            voxelizer.bindShaderStorageBuffer(1, MaterialFactory.getInstance().getMaterialBuffer());
            voxelizer.bindShaderStorageBuffer(3, AppContext.getInstance().getScene().getEntitiesBuffer());
            voxelizer.setUniformAsMatrix4("u_MVPx", viewXBuffer);
            voxelizer.setUniformAsMatrix4("u_MVPy", viewYBuffer);
            voxelizer.setUniformAsMatrix4("u_MVPz", viewZBuffer);
            FloatBuffer viewMatrixAsBuffer1 = renderExtract.camera.getViewMatrixAsBuffer();
            voxelizer.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer1);
            FloatBuffer projectionMatrixAsBuffer1 = renderExtract.camera.getProjectionMatrixAsBuffer();
            voxelizer.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer1);
            voxelizer.setUniform("u_width", 256);
            voxelizer.setUniform("u_height", 256);

            voxelizer.setUniform("writeVoxels", true);
            voxelizer.setUniform("sceneScale", Renderer.getInstance().getGBuffer().sceneScale);
            voxelizer.setUniform("inverseSceneScale", 1f/Renderer.getInstance().getGBuffer().sceneScale);
            voxelizer.setUniform("gridSize",Renderer.getInstance().getGBuffer().gridSize);
            voxelizer.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer1);
            voxelizer.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer1);
            voxelizer.setUniform("sceneScale", Renderer.getInstance().getGBuffer().sceneScale);
            voxelizer.setUniform("inverseSceneScale", 1f/Renderer.getInstance().getGBuffer().sceneScale);
            voxelizer.setUniform("gridSize",Renderer.getInstance().getGBuffer().gridSize);
            OpenGLContext.getInstance().depthMask(false);
            OpenGLContext.getInstance().disable(DEPTH_TEST);
            OpenGLContext.getInstance().disable(BLEND);
            OpenGLContext.getInstance().disable(CULL_FACE);
            GL11.glColorMask(false, false, false, false);

//            GLSamplesPassedQuery query = new GLSamplesPassedQuery();
//            query.begin();

            for (Entity entity : renderExtract.entities) {
                if(entity.getComponents().containsKey("ModelComponent")) {
                    int currentVerticesCount = ModelComponent.class.cast(entity.getComponents().get("ModelComponent"))
                            .draw(orthoCam, null, voxelizer, AppContext.getInstance().getScene().getEntities().indexOf(entity), entity.isVisible(), entity.isSelected());
                    firstPassResult.verticesDrawn += currentVerticesCount;
                    if(currentVerticesCount > 0) { firstPassResult.entitiesDrawn++; }
                }
            }
//            query.end();
//            System.out.println(query.getResult());

            boolean generatevoxelsMipmap = true;
            if(useVoxelConeTracing && generatevoxelsMipmap && entityOrDirectionalLightHasMoved){// && Renderer.getInstance().getFrameCount() % 4 == 0) {
                GPUProfiler.start("grid mipmap");
                int size = Renderer.getInstance().getGBuffer().gridSize;
                int currentSizeSource = 2*size;
                int currentMipMapLevel = 0;

                texture3DMipMappingComputeProgram.use();
                while(currentSizeSource > 1) {
                    currentSizeSource /= 2;
                    int currentSizeTarget = currentSizeSource / 2;
                    currentMipMapLevel++;

                    GL42.glBindImageTexture(0, Renderer.getInstance().getGBuffer().grid, currentMipMapLevel-1, true, 0, GL15.GL_READ_ONLY, GBuffer.gridTextureFormatSized);
                    GL42.glBindImageTexture(1, Renderer.getInstance().getGBuffer().grid, currentMipMapLevel, true, 0, GL15.GL_WRITE_ONLY, GBuffer.gridTextureFormatSized);
                    texture3DMipMappingComputeProgram.setUniform("sourceSize", currentSizeSource);
                    texture3DMipMappingComputeProgram.setUniform("targetSize", currentSizeTarget);

                    int num_groups_xyz = Math.max(currentSizeTarget / 8, 1);
                    texture3DMipMappingComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz);
                }

                GPUProfiler.end();
            }
            GL11.glColorMask(true, true, true, true);
        }
    }
}
