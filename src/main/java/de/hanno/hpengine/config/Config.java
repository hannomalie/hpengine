package de.hanno.hpengine.config;

import de.hanno.hpengine.renderer.lodstrategy.ModelLod;
import de.hanno.hpengine.util.Adjustable;
import de.hanno.hpengine.util.Toggable;
import org.lwjgl.util.vector.Vector3f;

public final class Config {

    public static final boolean FILE_RELOADING = true;
	public static int WIDTH = 1280;
	public static int HEIGHT = 720;
	public static Vector3f AMBIENT_LIGHT = new Vector3f(0.1f, 0.1f, 0.11f);

	@Toggable(group = "Quality settings") public static volatile boolean useParallax = false;
	@Toggable(group = "Quality settings") public static volatile boolean useSteepParallax = false;
	@Toggable(group = "Quality settings") public static volatile boolean useAmbientOcclusion = true;
	@Toggable(group = "Debug") public static volatile boolean useFrustumCulling = true;
	public static volatile boolean useInstantRadiosity = false;
	public static volatile boolean forceRevoxelization = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_GI = false;
	@Toggable(group = "Quality settings") public static volatile boolean useSSR = false;
	@Toggable(group = "Quality settings") public static volatile boolean MULTIPLE_DIFFUSE_SAMPLES = true;
	@Toggable(group = "Quality settings") public static volatile boolean MULTIPLE_DIFFUSE_SAMPLES_PROBES = true;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_DIFFUSE = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_DIFFUSE_PROBES = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_SPECULAR = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_SPECULAR_PROBES = false;
	@Toggable(group = "Quality settings") public static volatile boolean PRECOMPUTED_RADIANCE = true;
	@Toggable(group = "Quality settings") public static volatile boolean CALCULATE_ACTUAL_RADIANCE = false;
	@Toggable(group = "Quality settings") public static volatile boolean SSR_FADE_TO_SCREEN_BORDERS = true;
	@Toggable(group = "Quality settings") public static volatile boolean SSR_TEMPORAL_FILTERING = true;
	@Toggable(group = "Quality settings") public static volatile boolean USE_PCF = false;
	@Toggable(group = "Debug") public static volatile boolean DRAWLINES_ENABLED = false;
	@Toggable(group = "Debug") public static volatile boolean DRAWSCENE_ENABLED = true;
	@Toggable(group = "Debug") public static volatile boolean DIRECT_TEXTURE_OUTPUT = false;
	@Toggable(group = "Quality settings") public static volatile boolean CONTINUOUS_DRAW_PROBES = false;
	@Toggable(group = "Debug") public static volatile boolean DEBUGFRAME_ENABLED = false;
	@Toggable(group = "Debug") public static volatile boolean DRAWLIGHTS_ENABLED = false;
	@Toggable(group = "Quality settings") public static volatile boolean DRAW_PROBES = true;
	@Adjustable(group = "Debug") public static volatile float CAMERA_SPEED = 1.0f;
	@Toggable(group = "Effects") public static volatile boolean SCATTERING = false;
	@Adjustable(group = "Effects") public static volatile float RAINEFFECT = 0.0f;
	@Adjustable(group = "Effects") public static volatile float AMBIENTOCCLUSION_TOTAL_STRENGTH = 0.5f;
	@Adjustable(group = "Effects") public static volatile float AMBIENTOCCLUSION_RADIUS = 0.0250f;
	@Adjustable(group = "Effects") public static volatile float EXPOSURE = 40f;
	@Toggable(group = "Effects") public static volatile boolean USE_BLOOM = false;
	@Toggable(group = "Effects") public static volatile boolean AUTO_EXPOSURE_ENABLED = true;
	@Toggable(group = "Effects") public static volatile boolean ENABLE_POSTPROCESSING = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_DPSM = false;

	@Toggable(group = "Performance") public static boolean MULTITHREADED_RENDERING = true;
	@Toggable(group = "Performance") public static boolean INDIRECT_DRAWING = true;
	@Toggable(group = "Performance") public static volatile boolean VSYNC = true;
	@Toggable(group = "Performance") public static volatile boolean LOCK_UPDATERATE = true;

    public static ModelLod.ModelLodStrategy MODEL_LOD_STRATEGY = ModelLod.ModelLodStrategy.CONSTANT_LEVEL;

    private Config() { super(); }
}
