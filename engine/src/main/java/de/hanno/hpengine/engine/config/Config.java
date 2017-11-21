package de.hanno.hpengine.engine.config;

import de.hanno.hpengine.engine.graphics.renderer.DeferredRenderer;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.engine.graphics.renderer.OpenGLContext;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.util.gui.Adjustable;
import de.hanno.hpengine.util.gui.Toggable;
import org.apache.commons.beanutils.BeanUtils;
import org.joml.Vector3f;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class Config {

	private static Config instance = new Config();
	
	static {
        final File propertiesFile = new File("hp/default.properties");
        if(propertiesFile != null && propertiesFile.exists()) {
            try {
                populateConfigurationWithProperties(instance, new FileInputStream(propertiesFile));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
	}

	public static void populateConfigurationWithProperties(Config instance, InputStream inputStream) {
        Properties properties = new Properties();
        try {
            properties.load(inputStream);

            Map propertiesMap = new HashMap<>();
            for (String key : properties.stringPropertyNames()) {
                Object value = properties.get(key);
                propertiesMap.put(key, value);
            }
            try {
                BeanUtils.populate(instance, propertiesMap);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

	private Class<? extends Renderer> rendererClass = DeferredRenderer.class;
	private String initFileName = "Init.java";
	private Class<? extends GraphicsContext> gpuContextClass = OpenGLContext.class;
    private boolean useFileReloading = true;
	private int width = 1280;
	private int height = 720;
	private Vector3f ambientLight = new Vector3f(0.1f, 0.1f, 0.11f);

	@Toggable(group = "Quality settings")
	private volatile boolean useParallax = false;
	@Toggable(group = "Quality settings")
	private volatile boolean useSteepParallax = false;
	@Toggable(group = "Quality settings")
	private volatile boolean useAmbientOcclusion = true;
	@Toggable(group = "Debug")
	private volatile boolean useFrustumCulling = false;
	@Toggable(group = "Debug")
	private volatile boolean useOcclusionCulling = true;
	private volatile boolean useInstantRadiosity = false;
	private volatile boolean forceRevoxelization = false;
	@Toggable(group = "Quality settings")
	private volatile boolean useGi = false;
	@Toggable(group = "Quality settings")
	private volatile boolean useSSR = false;
	@Toggable(group = "Quality settings")
	private volatile boolean useMultipleDiffuseSamples = true;
	@Toggable(group = "Quality settings")
	private volatile boolean useMultipleDiffuseSamplesProbes = true;
	@Toggable(group = "Quality settings")
	private volatile boolean useConetracingForDiffuse = false;
	@Toggable(group = "Quality settings")
	private volatile boolean useConetracingForDiffuseProbes = false;
	@Toggable(group = "Quality settings")
	private volatile boolean useConetracingForSpecular = false;
	@Toggable(group = "Quality settings")
	private volatile boolean useConetracingForSpecularProbes = false;
	@Toggable(group = "Quality settings")
	private volatile boolean usePrecomputedRadiance = true;
	@Toggable(group = "Quality settings")
	private volatile boolean calculateActualRadiance = false;
	@Toggable(group = "Quality settings")
	private volatile boolean ssrFadeToScreenBorders = true;
	@Toggable(group = "Quality settings")
	private volatile boolean ssrTemporalFiltering = true;
	@Toggable(group = "Quality settings")
	private volatile boolean usePcf = false;
	@Toggable(group = "Debug")
	private volatile boolean drawLines = false;
	@Toggable(group = "Debug")
	private boolean drawBoundingVolumes = true;
	@Toggable(group = "Debug")
	private volatile boolean drawScene = true;
	@Toggable(group = "Debug")
	private volatile boolean useDirectTextureOutput = false;
	private volatile int directTextureOutputTextureIndex = 0;
	@Toggable(group = "Quality settings")
	private volatile boolean continuousDrawProbes = false;
	@Toggable(group = "Debug")
	private volatile boolean debugframeEnabled = false;
	@Toggable(group = "Debug")
	private volatile boolean drawlightsEnabled = false;
	@Toggable(group = "Quality settings")
	private volatile boolean drawProbes = true;
	@Adjustable(group = "Debug")
	private volatile float cameraSpeed = 1.0f;
	@Toggable(group = "Effects")
	private volatile boolean scattering = false;
	@Adjustable(group = "Effects")
	private volatile float rainEffect = 0.0f;
	@Adjustable(group = "Effects")
	private volatile float ambientocclusionTotalStrength = 0.5f;
	@Adjustable(group = "Effects")
	private volatile float ambientocclusionRadius = 0.0250f;
	@Adjustable(group = "Effects")
	private volatile float exposure = 5f;
	@Toggable(group = "Effects")
	private volatile boolean useBloom = false;
	@Toggable(group = "Effects")
	private volatile boolean autoExposureEnabled = true;
	@Toggable(group = "Effects")
	private volatile boolean enablePostprocessing = false;
	@Toggable(group = "Quality settings")
	private volatile boolean useDpsm = false;

	@Toggable(group = "Performance")
	private boolean multithreadedRendering = true;
	@Toggable(group = "Performance")
	private boolean indirectRendering = true;
	@Toggable(group = "Performance")
	private volatile boolean lockFps = false;
	private volatile boolean vsync = true;
	@Toggable(group = "Performance")
	private volatile boolean lockUpdaterate = true;

	private boolean loadDefaultMaterials = false;

	Config() { super(); }

	public static Config getInstance() {
		return instance;
	}

	public static void setInstance(Config instance) {
		Config.instance = instance;
	}

	public boolean isUseFileReloading() {
		return useFileReloading;
	}
	public void setUseFileReloading(boolean useFileReloading) {
		this.useFileReloading = useFileReloading;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public Vector3f getAmbientLight() {
		return ambientLight;
	}

	public void setAmbientLight(Vector3f ambientLight) {
		this.ambientLight = ambientLight;
	}

	public boolean isUseParallax() {
		return useParallax;
	}

	public void setUseParallax(boolean useParallax) {
		this.useParallax = useParallax;
	}

	public boolean isUseSteepParallax() {
		return useSteepParallax;
	}

	public void setUseSteepParallax(boolean useSteepParallax) {
		this.useSteepParallax = useSteepParallax;
	}

	public boolean isUseAmbientOcclusion() {
		return useAmbientOcclusion;
	}

	public void setUseAmbientOcclusion(boolean useAmbientOcclusion) {
		this.useAmbientOcclusion = useAmbientOcclusion;
	}

	public boolean isUseFrustumCulling() {
		return useFrustumCulling;
	}

	public void setUseFrustumCulling(boolean useFrustumCulling) {
		this.useFrustumCulling = useFrustumCulling;
	}

	public boolean isUseOcclusionCulling() {
		return useOcclusionCulling;
	}

	public void setUseOcclusionCulling(boolean useOcclusionCulling) {
		this.useOcclusionCulling = useOcclusionCulling;
	}

	public boolean isUseInstantRadiosity() {
		return useInstantRadiosity;
	}

	public void setUseInstantRadiosity(boolean useInstantRadiosity) {
		this.useInstantRadiosity = useInstantRadiosity;
	}

	public boolean isForceRevoxelization() {
		return forceRevoxelization;
	}

	public void setForceRevoxelization(boolean forceRevoxelization) {
		this.forceRevoxelization = forceRevoxelization;
	}

	public boolean isUseGi() {
		return useGi;
	}

	public void setUseGi(boolean useGi) {
		this.useGi = useGi;
	}

	public boolean isUseSSR() {
		return useSSR;
	}

	public void setUseSSR(boolean useSSR) {
		this.useSSR = useSSR;
	}

	public boolean isUseMultipleDiffuseSamples() {
		return useMultipleDiffuseSamples;
	}

	public void setUseMultipleDiffuseSamples(boolean useMultipleDiffuseSamples) {
		this.useMultipleDiffuseSamples = useMultipleDiffuseSamples;
	}

	public boolean isUseMultipleDiffuseSamplesProbes() {
		return useMultipleDiffuseSamplesProbes;
	}

	public void setUseMultipleDiffuseSamplesProbes(boolean useMultipleDiffuseSamplesProbes) {
		this.useMultipleDiffuseSamplesProbes = useMultipleDiffuseSamplesProbes;
	}

	public boolean isUseConetracingForDiffuse() {
		return useConetracingForDiffuse;
	}

	public void setUseConetracingForDiffuse(boolean useConetracingForDiffuse) {
		this.useConetracingForDiffuse = useConetracingForDiffuse;
	}

	public boolean isUseConetracingForDiffuseProbes() {
		return useConetracingForDiffuseProbes;
	}

	public void setUseConetracingForDiffuseProbes(boolean useConetracingForDiffuseProbes) {
		this.useConetracingForDiffuseProbes = useConetracingForDiffuseProbes;
	}

	public boolean isUseConetracingForSpecular() {
		return useConetracingForSpecular;
	}

	public void setUseConetracingForSpecular(boolean useConetracingForSpecular) {
		this.useConetracingForSpecular = useConetracingForSpecular;
	}

	public boolean isUseConetracingForSpecularProbes() {
		return useConetracingForSpecularProbes;
	}

	public void setUseConetracingForSpecularProbes(boolean useConetracingForSpecularProbes) {
		this.useConetracingForSpecularProbes = useConetracingForSpecularProbes;
	}

	public boolean isUsePrecomputedRadiance() {
		return usePrecomputedRadiance;
	}

	public void setUsePrecomputedRadiance(boolean usePrecomputedRadiance) {
		this.usePrecomputedRadiance = usePrecomputedRadiance;
	}

	public boolean isCalculateActualRadiance() {
		return calculateActualRadiance;
	}

	public void setCalculateActualRadiance(boolean calculateActualRadiance) {
		this.calculateActualRadiance = calculateActualRadiance;
	}

	public boolean isSsrFadeToScreenBorders() {
		return ssrFadeToScreenBorders;
	}

	public void setSsrFadeToScreenBorders(boolean ssrFadeToScreenBorders) {
		this.ssrFadeToScreenBorders = ssrFadeToScreenBorders;
	}

	public boolean isSsrTemporalFiltering() {
		return ssrTemporalFiltering;
	}

	public void setSsrTemporalFiltering(boolean ssrTemporalFiltering) {
		this.ssrTemporalFiltering = ssrTemporalFiltering;
	}

	public boolean isUsePcf() {
		return usePcf;
	}

	public void setUsePcf(boolean usePcf) {
		this.usePcf = usePcf;
	}

	public boolean isDrawLines() {
		return drawLines;
	}

	public void setDrawLines(boolean drawLines) {
		this.drawLines = drawLines;
	}

	public boolean isDrawScene() {
		return drawScene;
	}

	public void setDrawScene(boolean drawScene) {
		this.drawScene = drawScene;
	}

	public boolean isUseDirectTextureOutput() {
		return useDirectTextureOutput;
	}

	public void setUseDirectTextureOutput(boolean useDirectTextureOutput) {
		this.useDirectTextureOutput = useDirectTextureOutput;
	}

	public boolean isContinuousDrawProbes() {
		return continuousDrawProbes;
	}

	public void setContinuousDrawProbes(boolean continuousDrawProbes) {
		this.continuousDrawProbes = continuousDrawProbes;
	}

	public boolean isDebugframeEnabled() {
		return debugframeEnabled;
	}

	public void setDebugframeEnabled(boolean debugframeEnabled) {
		this.debugframeEnabled = debugframeEnabled;
	}

	public boolean isDrawlightsEnabled() {
		return drawlightsEnabled;
	}

	public void setDrawlightsEnabled(boolean drawlightsEnabled) {
		this.drawlightsEnabled = drawlightsEnabled;
	}

	public boolean isDrawProbes() {
		return drawProbes;
	}

	public void setDrawProbes(boolean drawProbes) {
		this.drawProbes = drawProbes;
	}

	public float getCameraSpeed() {
		return cameraSpeed;
	}

	public void setCameraSpeed(float cameraSpeed) {
		this.cameraSpeed = cameraSpeed;
	}

	public boolean isScattering() {
		return scattering;
	}

	public void setScattering(boolean scattering) {
		this.scattering = scattering;
	}

	public float getRainEffect() {
		return rainEffect;
	}

	public void setRainEffect(float rainEffect) {
		this.rainEffect = rainEffect;
	}

	public float getAmbientocclusionTotalStrength() {
		return ambientocclusionTotalStrength;
	}

	public void setAmbientocclusionTotalStrength(float ambientocclusionTotalStrength) {
		this.ambientocclusionTotalStrength = ambientocclusionTotalStrength;
	}

	public float getAmbientocclusionRadius() {
		return ambientocclusionRadius;
	}

	public void setAmbientocclusionRadius(float ambientocclusionRadius) {
		this.ambientocclusionRadius = ambientocclusionRadius;
	}

	public float getExposure() {
		return exposure;
	}

	public void setExposure(float exposure) {
		this.exposure = exposure;
	}

	public boolean isUseBloom() {
		return useBloom;
	}

	public void setUseBloom(boolean useBloom) {
		this.useBloom = useBloom;
	}

	public boolean isAutoExposureEnabled() {
		return autoExposureEnabled;
	}

	public void setAutoExposureEnabled(boolean autoExposureEnabled) {
		this.autoExposureEnabled = autoExposureEnabled;
	}

	public boolean isEnablePostprocessing() {
		return enablePostprocessing;
	}

	public void setEnablePostprocessing(boolean enablePostprocessing) {
		this.enablePostprocessing = enablePostprocessing;
	}

	public boolean isUseDpsm() {
		return useDpsm;
	}

	public void setUseDpsm(boolean useDpsm) {
		this.useDpsm = useDpsm;
	}

	public boolean isMultithreadedRendering() {
		return multithreadedRendering;
	}

	public void setMultithreadedRendering(boolean multithreadedRendering) {
		this.multithreadedRendering = multithreadedRendering;
	}

	public boolean isIndirectRendering() {
		return indirectRendering;
	}

	public void setIndirectRendering(boolean indirectRendering) {
		this.indirectRendering = indirectRendering;
	}

	public boolean isLockFps() {
		return lockFps;
	}

	public void setLockFps(boolean lockFps) {
		this.lockFps = lockFps;
	}

	public boolean isVsync() {
		return vsync;
	}

	public void setVsync(boolean vsync) {
		this.vsync = vsync;
	}

	public boolean isLockUpdaterate() {
		return lockUpdaterate;
	}

	public void setLockUpdaterate(boolean lockUpdaterate) {
		this.lockUpdaterate = lockUpdaterate;
	}

	public void setGpuContextClass(Class<? extends GraphicsContext> gpuContextClass) {
		this.gpuContextClass = gpuContextClass;
	}

	public Class<? extends GraphicsContext> getGpuContextClass() {
		return gpuContextClass;

	}

	public Class<? extends Renderer> getRendererClass() {
		return rendererClass;
	}

	public void setRendererClass(Class <? extends Renderer> rendererClass) {
		this.rendererClass = rendererClass;
	}

	public boolean isDrawBoundingVolumes() {
		return drawBoundingVolumes;
	}

    public void setDrawBoundingVolumes(boolean drawBoundingVolumes) {
        this.drawBoundingVolumes = drawBoundingVolumes;
    }

    public boolean isLoadDefaultMaterials() {
        return loadDefaultMaterials;
    }

    public void setLoadDefaultMaterials(boolean loadDefaultMaterials) {
        this.loadDefaultMaterials = loadDefaultMaterials;
    }

	public void setDirectTextureOutputTextureIndex(int directTextureOutputTextureIndex) {
		this.directTextureOutputTextureIndex = directTextureOutputTextureIndex;
	}

	public int getDirectTextureOutputTextureIndex() {
		return directTextureOutputTextureIndex;
	}

	public String getInitFileName() {
		return initFileName;
	}

	public void setInitFileName(String initFileName) {
		this.initFileName = initFileName;
	}
}
