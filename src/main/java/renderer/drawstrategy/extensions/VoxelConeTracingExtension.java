package renderer.drawstrategy.extensions;

import camera.Camera;
import component.ModelComponent;
import config.Config;
import engine.AppContext;
import engine.Transform;
import engine.model.Entity;
import engine.model.EntityFactory;
import jdk.nashorn.internal.runtime.Logging;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
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
    public static final int gridSize = 256;
    public static final int gridSizeHalf = gridSize/2;
    public static final int gridTextureFormat = GL11.GL_RGBA;//GL11.GL_R;
    public static final int gridTextureFormatSized = GL11.GL_RGBA8;//GL30.GL_R32UI;
    private Transform viewXTransform;
    private Transform viewYTransform;
    private Transform viewZTransform;

    public float sceneScale = 2;

    private Matrix4f ortho = util.Util.createOrthogonal(-getGridSizeHalfScaled(), getGridSizeHalfScaled(), getGridSizeHalfScaled(), -getGridSizeHalfScaled(), getGridSizeHalfScaled(), -getGridSizeHalfScaled());
    private Camera orthoCam = new Camera(ortho, getGridSizeHalfScaled(), -getGridSizeHalfScaled(), 90, 1);
    private Matrix4f viewX;
    public FloatBuffer viewXBuffer = BufferUtils.createFloatBuffer(16);
    private Matrix4f viewY;
    public FloatBuffer viewYBuffer = BufferUtils.createFloatBuffer(16);
    private Matrix4f viewZ;
    public FloatBuffer viewZBuffer = BufferUtils.createFloatBuffer(16);

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
    private final ComputeShaderProgram texture3DMipMapAlphaBlendComputeProgram;
    private final ComputeShaderProgram texture3DMipMapComputeProgram;
    private final ComputeShaderProgram clearDynamicVoxelsComputeProgram;
    private final ComputeShaderProgram injectLightComputeProgram;
    private final int albedoGrid;
    int normalGrid;
    int currentVoxelTarget;
    int currentVoxelSource;

    public final int grid;
    private final int gridTwo;

    public VoxelConeTracingExtension() throws Exception {
        initOrthoCam();
        initViewXBuffer();
        initViewYBuffer();
        initViewZBuffer();

        voxelizer = ProgramFactory.getInstance().getProgram("voxelize_vertex.glsl", "voxelize_geometry.glsl", "voxelize_fragment.glsl", ModelComponent.DEFAULTCHANNELS, true);
        texture3DMipMapAlphaBlendComputeProgram = ProgramFactory.getInstance().getComputeProgram("texture3D_mipmap_alphablend_compute.glsl");
        texture3DMipMapComputeProgram = ProgramFactory.getInstance().getComputeProgram("texture3D_mipmap_compute.glsl");
        clearDynamicVoxelsComputeProgram = ProgramFactory.getInstance().getComputeProgram("texture3D_clear_dynamic_voxels_compute.glsl");
        injectLightComputeProgram = ProgramFactory.getInstance().getComputeProgram("texture3D_inject_light_compute.glsl");


        grid = TextureFactory.getInstance().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_LINEAR_MIPMAP_LINEAR,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);
        gridTwo = TextureFactory.getInstance().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_LINEAR_MIPMAP_LINEAR,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);
        albedoGrid = TextureFactory.getInstance().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_LINEAR_MIPMAP_LINEAR,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);
        normalGrid = TextureFactory.getInstance().getTexture3D(gridSize, gridTextureFormatSized,
                GL11.GL_NEAREST,
                GL11.GL_LINEAR,
                GL12.GL_CLAMP_TO_EDGE);

        currentVoxelTarget = grid;
        currentVoxelSource = gridTwo;
        voxelConeTraceProgram = ProgramFactory.getInstance().getProgram("passthrough_vertex.glsl", "voxel_cone_trace_fragment.glsl");
    }

    private void initViewZBuffer() {
        viewZTransform = new Transform();
        viewZTransform.rotate(new Vector3f(0,1,0), 180f);
        viewZ = Matrix4f.mul(ortho, viewZTransform.getViewMatrix(), null);
        viewZBuffer.rewind();
        viewZ.store(viewZBuffer);
        viewZBuffer.rewind();
    }

    private void initViewYBuffer() {
        viewYTransform = new Transform();
        viewYTransform.rotate(new Vector3f(1, 0, 0), 90f);
        viewY = Matrix4f.mul(ortho, viewYTransform.getViewMatrix(), null);
        viewYBuffer.rewind();
        viewY.store(viewYBuffer);
        viewYBuffer.rewind();
    }

    private void initViewXBuffer() {
        viewXTransform = new Transform();
        viewXTransform.rotate(new Vector3f(0,1,0), 90f);
        viewX = Matrix4f.mul(ortho, viewXTransform.getViewMatrix(), null);
        viewXBuffer.rewind();
        viewX.store(viewXBuffer);
        viewXBuffer.rewind();
    }

    private void initOrthoCam() {
        ortho = util.Util.createOrthogonal(-getGridSizeHalfScaled(), getGridSizeHalfScaled(), getGridSizeHalfScaled(), -getGridSizeHalfScaled(), getGridSizeHalfScaled(), -getGridSizeHalfScaled());
        orthoCam = new Camera(ortho, getGridSizeHalfScaled(), -getGridSizeHalfScaled(), 90, 1);

        orthoCam.setPerspective(false);
        orthoCam.setWidth(getGridSizeScaled());
        orthoCam.setHeight(getGridSizeScaled());
        orthoCam.setFar(-5000);
        orthoCam.update(0.000001f);
    }

    @Override
    public void renderFirstPass(RenderExtract renderExtract, FirstPassResult firstPassResult) {
        GPUProfiler.start("VCT first pass");
        boolean entityOrDirectionalLightHasMoved = renderExtract.anEntityHasMoved || renderExtract.directionalLightNeedsShadowMapRender;
        boolean useVoxelConeTracing = true;
        boolean clearVoxels = true;
        int bounces = 14;
        Integer lightInjectedFramesAgo = (Integer) firstPassResult.getProperty("vctLightInjectedFramesAgo");
        if((useVoxelConeTracing && entityOrDirectionalLightHasMoved) || !renderExtract.sceneInitiallyDrawn || (lightInjectedFramesAgo != null && lightInjectedFramesAgo < bounces))
        {
            if(clearVoxels && lightInjectedFramesAgo == null) {
                GPUProfiler.start("Clear voxels");
                if(!renderExtract.sceneInitiallyDrawn) {
                    ARBClearTexture.glClearTexImage(currentVoxelTarget, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
                    ARBClearTexture.glClearTexImage(normalGrid, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
                    ARBClearTexture.glClearTexImage(albedoGrid, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
                } else {
                    clearDynamicVoxelsComputeProgram.use();
                    int num_groups_xyz = Math.max(gridSize / 8, 1);
                    GL42.glBindImageTexture(0, albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                    GL42.glBindImageTexture(1, normalGrid, 0, true, 0, GL15.GL_READ_WRITE, gridTextureFormatSized);
//                GL42.glBindImageTexture(2, currentVoxelSource, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                    GL42.glBindImageTexture(3, currentVoxelTarget, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
                    OpenGLContext.getInstance().bindTexture(4, TEXTURE_3D, normalGrid);
                    clearDynamicVoxelsComputeProgram.dispatchCompute(num_groups_xyz,num_groups_xyz,num_groups_xyz);
                }
                GPUProfiler.end();
            }

            GPUProfiler.start("Voxelization");
            orthoCam.update(0.000001f);
            int gridSizeScaled = (int) (gridSize * getSceneScale(renderExtract));
            OpenGLContext.getInstance().viewPort(0,0, gridSizeScaled, gridSizeScaled);
            voxelizer.use();
            GL42.glBindImageTexture(3, normalGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
            GL42.glBindImageTexture(5, albedoGrid, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
            OpenGLContext.getInstance().bindTexture(8, TEXTURE_3D, currentVoxelSource);
            Scene scene = AppContext.getInstance().getScene();
            if(scene != null) {
                voxelizer.setUniformAsMatrix4("shadowMatrix", renderExtract.directionalLight.getViewProjectionMatrixAsBuffer());
                voxelizer.setUniform("lightDirection", renderExtract.directionalLight.getDirection());
                voxelizer.setUniform("lightColor", renderExtract.directionalLight.getColor());
            }
            voxelizer.bindShaderStorageBuffer(1, MaterialFactory.getInstance().getMaterialBuffer());
            voxelizer.bindShaderStorageBuffer(3, EntityFactory.getInstance().getEntitiesBuffer());
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
            voxelizer.setUniform("sceneScale", getSceneScale(renderExtract));
            voxelizer.setUniform("inverseSceneScale", 1f/getSceneScale(renderExtract));
            voxelizer.setUniform("gridSize",gridSize);
            voxelizer.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer1);
            voxelizer.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer1);
            OpenGLContext.getInstance().depthMask(false);
            OpenGLContext.getInstance().disable(DEPTH_TEST);
            OpenGLContext.getInstance().disable(BLEND);
            OpenGLContext.getInstance().disable(CULL_FACE);
            GL11.glColorMask(false, false, false, false);

            for (Entity entity :  renderExtract.entities) {
                if(entity.getComponents().containsKey("ModelComponent")) {
                    boolean isStatic = entity.getUpdate().equals(Entity.Update.STATIC);
                    if(renderExtract.sceneInitiallyDrawn && isStatic) { continue; }
                    ModelComponent modelComponent = ModelComponent.class.cast(entity.getComponents().get("ModelComponent"));
                    voxelizer.setUniform("isStatic", isStatic ? 1 : 0);
                    int currentVerticesCount = modelComponent
                            .draw(renderExtract, orthoCam, null, voxelizer, AppContext.getInstance().getScene().getEntities().indexOf(entity), true, entity.isSelected());
                    firstPassResult.verticesDrawn += currentVerticesCount;
                    if(currentVerticesCount > 0) {
                        firstPassResult.entitiesDrawn++;
                    } else if(currentVerticesCount < 0){
                        firstPassResult.notYetUploadedVertexBufferDrawn = true;
                    }
                }
            }
            GPUProfiler.end();

            GPUProfiler.start("grid shading");
            GL42.glBindImageTexture(0, currentVoxelTarget, 0, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
            OpenGLContext.getInstance().bindTexture(1, TEXTURE_3D, albedoGrid);
            OpenGLContext.getInstance().bindTexture(2, TEXTURE_3D, normalGrid);
            OpenGLContext.getInstance().bindTexture(3, TEXTURE_3D, currentVoxelSource);
            injectLightComputeProgram.use();
            injectLightComputeProgram.setUniform("sceneScale", getSceneScale(renderExtract));
            injectLightComputeProgram.setUniform("inverseSceneScale", 1f/getSceneScale(renderExtract));
            injectLightComputeProgram.setUniform("gridSize",gridSize);
            if(scene != null) {
                injectLightComputeProgram.setUniformAsMatrix4("shadowMatrix", renderExtract.directionalLight.getViewProjectionMatrixAsBuffer());
                injectLightComputeProgram.setUniform("lightDirection", renderExtract.directionalLight.getCamera().getViewDirection());
                injectLightComputeProgram.setUniform("lightColor", renderExtract.directionalLight.getColor());
            }
            int num_groups_xyz = Math.max(gridSize / 8, 1);

            if(lightInjectedFramesAgo == null) {
                injectLightComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz);
                lightInjectedFramesAgo = -1;
                System.out.println(lightInjectedFramesAgo);
            } else if(lightInjectedFramesAgo < bounces ) {
                injectLightComputeProgram.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz);
                System.out.println(lightInjectedFramesAgo);
            } else {
                lightInjectedFramesAgo = null;
            }

            if(lightInjectedFramesAgo != null) {
                firstPassResult.setProperty("vctLightInjectedFramesAgo", ++lightInjectedFramesAgo);
            }
            GPUProfiler.end();

            boolean generatevoxelsMipmap = true;
            if(generatevoxelsMipmap){
                GPUProfiler.start("grid mipmap");

                mipmapGrid(currentVoxelTarget, texture3DMipMapAlphaBlendComputeProgram);
//                mipmapGrid(normalGrid, texture3DMipMapComputeProgram);

                GPUProfiler.end();
            }
            GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
            GL11.glColorMask(true, true, true, true);
        } else {
            firstPassResult.setProperty("vctLightInjectedFramesAgo", null);
        }
        GPUProfiler.end();
    }

    private void mipmapGrid(int texture3D, ComputeShaderProgram shader) {
        shader.use();
        int size = gridSize;
        int currentSizeSource = (2*size);//(int) (sceneScale*size);
        int currentMipMapLevel = 0;

        while(currentSizeSource > 1) {
            currentSizeSource /= 2;
            int currentSizeTarget = currentSizeSource / 2;
            currentMipMapLevel++;

            GL42.glBindImageTexture(0, texture3D, currentMipMapLevel-1, true, 0, GL15.GL_READ_ONLY, gridTextureFormatSized);
            GL42.glBindImageTexture(1, texture3D, currentMipMapLevel, true, 0, GL15.GL_WRITE_ONLY, gridTextureFormatSized);
            GL42.glBindImageTexture(3, normalGrid, currentMipMapLevel-1, true, 0, GL15.GL_READ_ONLY, gridTextureFormatSized);
            shader.setUniform("sourceSize", currentSizeSource);
            shader.setUniform("targetSize", currentSizeTarget);

            int num_groups_xyz = Math.max(currentSizeTarget / 8, 1);
            shader.dispatchCompute(num_groups_xyz, num_groups_xyz, num_groups_xyz);
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

        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
        GPUProfiler.start("VCT second pass");
        OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, Renderer.getInstance().getGBuffer().getPositionMap());
        OpenGLContext.getInstance().bindTexture(1, TEXTURE_2D, Renderer.getInstance().getGBuffer().getNormalMap());
        OpenGLContext.getInstance().bindTexture(2, TEXTURE_2D, Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, Renderer.getInstance().getGBuffer().getMotionMap());
        OpenGLContext.getInstance().bindTexture(7, TEXTURE_2D, Renderer.getInstance().getGBuffer().getVisibilityMap());
        OpenGLContext.getInstance().bindTexture(12, TEXTURE_3D, albedoGrid);
        OpenGLContext.getInstance().bindTexture(13, TEXTURE_3D, currentVoxelTarget);
        OpenGLContext.getInstance().bindTexture(14, TEXTURE_3D, normalGrid);

        voxelConeTraceProgram.use();
        voxelConeTraceProgram.setUniform("eyePosition", renderExtract.camera.getWorldPosition());
        voxelConeTraceProgram.setUniformAsMatrix4("viewMatrix", renderExtract.camera.getViewMatrixAsBuffer());
        voxelConeTraceProgram.setUniformAsMatrix4("projectionMatrix", renderExtract.camera.getProjectionMatrixAsBuffer());
        voxelConeTraceProgram.bindShaderStorageBuffer(0, Renderer.getInstance().getGBuffer().getStorageBuffer());
        voxelConeTraceProgram.setUniform("sceneScale", getSceneScale(renderExtract));
        voxelConeTraceProgram.setUniform("inverseSceneScale", 1f / getSceneScale(renderExtract));
        voxelConeTraceProgram.setUniform("gridSize", gridSize);
        voxelConeTraceProgram.setUniform("useAmbientOcclusion", Config.useAmbientOcclusion);
        Renderer.getInstance().getFullscreenBuffer().draw();
        boolean entityOrDirectionalLightHasMoved = renderExtract.anEntityHasMoved || renderExtract.directionalLightNeedsShadowMapRender;
        if(entityOrDirectionalLightHasMoved) {
//            if only second bounce, clear current target texture
            switchCurrentVoxelGrid();
//            ARBClearTexture.glClearTexImage(currentVoxelSource, 0, gridTextureFormat, GL11.GL_UNSIGNED_BYTE, zeroBuffer);
        }
        GPUProfiler.end();
    }

    Vector4f maxExtents = new Vector4f();
    public float getSceneScale(RenderExtract renderExtract) {
        maxExtents.setX(Math.max(Math.abs(renderExtract.sceneMin.x), Math.abs(renderExtract.sceneMax.x)));
        maxExtents.setY(Math.max(Math.abs(renderExtract.sceneMin.y), Math.abs(renderExtract.sceneMax.y)));
        maxExtents.setZ(Math.max(Math.abs(renderExtract.sceneMin.z), Math.abs(renderExtract.sceneMax.z)));
        float max = Math.max(Math.max(maxExtents.x, maxExtents.y), maxExtents.z);
        float sceneScale = max / (float) gridSizeHalf;
        sceneScale = Math.max(sceneScale, 2.0f);
        boolean sceneScaleChanged = this.sceneScale != sceneScale;
        if(sceneScaleChanged) {
            initOrthoCam();
            initViewXBuffer();
            initViewYBuffer();
            initViewZBuffer();
        }
        this.sceneScale = sceneScale;
        return sceneScale;
    }

    private int getGridSizeScaled() {
        return (int)(gridSize*sceneScale);
    }

    private int getGridSizeHalfScaled() {
        return (int)((gridSizeHalf)*sceneScale);
    }
}
