package renderer.drawstrategy.extensions;

import camera.Camera;
import component.ModelComponent;
import engine.AppContext;
import engine.Transform;
import engine.model.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.Renderer;
import renderer.drawstrategy.FirstPassResult;
import renderer.drawstrategy.SecondPassResult;
import renderer.material.MaterialFactory;
import scene.Scene;
import shader.ComputeShaderProgram;
import shader.Program;
import shader.ProgramFactory;
import texture.TextureFactory;
import util.stopwatch.GPUProfiler;

import java.nio.FloatBuffer;

import static renderer.constants.GlCap.*;
import static renderer.constants.GlTextureTarget.TEXTURE_2D;
import static renderer.constants.GlTextureTarget.TEXTURE_3D;

public class VoxelConeTracingExtension implements RenderExtension {

    public static final boolean useVoxelConeTracing = true;
    public static final float sceneScale = 2f;
    public static final int gridSize = 256;
    public static final int gridSizeHalf = gridSize/2;
    public static final int gridSizeScaled = (int)(gridSize*sceneScale);
    public static final int gridSizeHalfScaled = (int)((gridSizeHalf)*sceneScale);
    public static final int gridTextureFormat = GL11.GL_RGBA;//GL11.GL_R;
    public static final int gridTextureFormatSized = GL11.GL_RGBA8;//GL30.GL_R32UI;

    private static Matrix4f ortho = util.Util.createOrthogonal(-gridSizeHalfScaled, gridSizeHalfScaled, gridSizeHalfScaled, -gridSizeHalfScaled, gridSizeHalfScaled, -gridSizeHalfScaled);
    private static Camera orthoCam = new Camera(ortho, gridSizeHalfScaled, -gridSizeHalfScaled, 90, 1);
    private static Matrix4f viewX;
    private static FloatBuffer viewXBuffer = BufferUtils.createFloatBuffer(16);
    private static Matrix4f viewY;
    private static FloatBuffer viewYBuffer = BufferUtils.createFloatBuffer(16);
    private static Matrix4f viewZ;
    private static FloatBuffer viewZBuffer = BufferUtils.createFloatBuffer(16);
    static {
        orthoCam.setPerspective(false);
        orthoCam.setWidth(gridSizeScaled);
        orthoCam.setHeight(gridSizeScaled);
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

    private final Program voxelizer;
    private final Program voxelConeTraceProgram;
    private final ComputeShaderProgram texture3DMipMappingComputeProgram;
    int currentVoxelTarget;
    int currentVoxelSource;

    public final int grid;
    private final int gridTwo;

    public VoxelConeTracingExtension() throws Exception {
        voxelizer = ProgramFactory.getInstance().getProgram("voxelize_vertex.glsl", "voxelize_geometry.glsl", "voxelize_fragment.glsl", ModelComponent.DEFAULTCHANNELS, true);
        texture3DMipMappingComputeProgram = ProgramFactory.getInstance().getComputeProgram("texture3D_mipmap_compute.glsl");


        grid = TextureFactory.getInstance().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_LINEAR_MIPMAP_LINEAR,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);
        gridTwo = TextureFactory.getInstance().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_LINEAR_MIPMAP_LINEAR,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);
        currentVoxelTarget = grid;
        currentVoxelSource = gridTwo;
        voxelConeTraceProgram = ProgramFactory.getInstance().getProgram("passthrough_vertex.glsl", "voxel_cone_trace_fragment.glsl");
    }

    @Override
    public void renderFirstPass(RenderExtract renderExtract, FirstPassResult firstPassResult) {

        boolean entityOrDirectionalLightHasMoved = renderExtract.anEntityHasMoved || renderExtract.directionalLightNeedsShadowMapRender;
        boolean useVoxelConeTracing = true;
        boolean clearVoxels = true;
        if(useVoxelConeTracing && entityOrDirectionalLightHasMoved) {
            if(clearVoxels) {
                GPUProfiler.start("Clear voxels");
                ARBClearTexture.glClearTexImage(currentVoxelTarget, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
//            ARBClearTexture.glClearTexImage(gridTwo, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
                GPUProfiler.end();
            }

            GPUProfiler.start("Voxelization");
            orthoCam.update(0.000001f);
            int gridSizeScaled = (int) (gridSize * sceneScale);
            OpenGLContext.getInstance().viewPort(0,0, gridSizeScaled, gridSizeScaled);

            voxelizer.use();
            GL42.glBindImageTexture(5, currentVoxelTarget, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
            OpenGLContext.getInstance().bindTexture(8, TEXTURE_3D, currentVoxelSource);
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
            voxelizer.setUniform("sceneScale", sceneScale);
            voxelizer.setUniform("inverseSceneScale", 1f/sceneScale);
            voxelizer.setUniform("gridSize",gridSize);
            voxelizer.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer1);
            voxelizer.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer1);
            voxelizer.setUniform("sceneScale", sceneScale);
            voxelizer.setUniform("inverseSceneScale", 1f/sceneScale);
            voxelizer.setUniform("gridSize",gridSize);
            OpenGLContext.getInstance().depthMask(false);
            OpenGLContext.getInstance().disable(DEPTH_TEST);
            OpenGLContext.getInstance().disable(BLEND);
            OpenGLContext.getInstance().disable(CULL_FACE);
            GL11.glColorMask(false, false, false, false);

//            GLSamplesPassedQuery query = new GLSamplesPassedQuery();
//            query.begin();

            for (Entity entity : renderExtract.entities) {
                if(entity.getComponents().containsKey("ModelComponent")) {
                    ModelComponent modelComponent = ModelComponent.class.cast(entity.getComponents().get("ModelComponent"));
                    int currentVerticesCount = modelComponent
                            .draw(renderExtract, orthoCam, null, voxelizer, AppContext.getInstance().getScene().getEntities().indexOf(entity), true, entity.isSelected());
                    firstPassResult.verticesDrawn += currentVerticesCount;
                    if(currentVerticesCount > 0) { firstPassResult.entitiesDrawn++; }
                }
            }
//            query.end();
//            System.out.println(query.getResult());
            GPUProfiler.end();

            boolean generatevoxelsMipmap = true;
            if(generatevoxelsMipmap){
                GPUProfiler.start("grid mipmap");
                int size = gridSize;
                int currentSizeSource = 2*size;
                int currentMipMapLevel = 0;

                texture3DMipMappingComputeProgram.use();
                while(currentSizeSource > 1) {
                    currentSizeSource /= 2;
                    int currentSizeTarget = currentSizeSource / 2;
                    currentMipMapLevel++;

                    GL42.glBindImageTexture(0, currentVoxelTarget, currentMipMapLevel-1, true, 0, GL15.GL_READ_ONLY, gridTextureFormatSized);
                    GL42.glBindImageTexture(1, currentVoxelTarget, currentMipMapLevel, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                    texture3DMipMappingComputeProgram.setUniform("sourceSize", currentSizeSource);
                    texture3DMipMappingComputeProgram.setUniform("targetSize", currentSizeTarget);

                    int num_groups_xyz = Math.max(currentSizeTarget / 8, 1);
                    texture3DMipMappingComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz);
                }

                switchCurrentVoxelGrid();
                GPUProfiler.end();
            }
            GL11.glColorMask(true, true, true, true);
        }
    }

    private void switchCurrentVoxelGrid() {
        if(currentVoxelTarget == grid) {
            currentVoxelTarget = gridTwo;
            currentVoxelSource = grid;
        } else {
            currentVoxelTarget = grid;
            currentVoxelSource = gridTwo;
        }
    }

    @Override
    public void renderSecondPassFullScreen(RenderExtract renderExtract, SecondPassResult secondPassResult) {

        OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, Renderer.getInstance().getGBuffer().getPositionMap());
        OpenGLContext.getInstance().bindTexture(1, TEXTURE_2D, Renderer.getInstance().getGBuffer().getNormalMap());
        OpenGLContext.getInstance().bindTexture(2, TEXTURE_2D, Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, Renderer.getInstance().getGBuffer().getMotionMap());
        OpenGLContext.getInstance().bindTexture(7, TEXTURE_2D, Renderer.getInstance().getGBuffer().getVisibilityMap());
        OpenGLContext.getInstance().bindTexture(13, TEXTURE_3D, currentVoxelSource);

        voxelConeTraceProgram.use();
        voxelConeTraceProgram.setUniform("eyePosition", renderExtract.camera.getWorldPosition());
        voxelConeTraceProgram.setUniformAsMatrix4("viewMatrix",renderExtract.camera.getViewMatrixAsBuffer());
        voxelConeTraceProgram.setUniformAsMatrix4("projectionMatrix", renderExtract.camera.getProjectionMatrixAsBuffer());
        voxelConeTraceProgram.bindShaderStorageBuffer(0, Renderer.getInstance().getGBuffer().getStorageBuffer());
        voxelConeTraceProgram.setUniform("sceneScale", sceneScale);
        voxelConeTraceProgram.setUniform("inverseSceneScale", 1f/sceneScale);
        voxelConeTraceProgram.setUniform("gridSize",gridSize);
        Renderer.getInstance().getFullscreenBuffer().draw();
    }

}
