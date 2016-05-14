package config;

import engine.AppContext;
import event.HeadlessChangedEvent;
import org.lwjgl.util.vector.Vector3f;
import util.Adjustable;
import util.Toggable;

public final class Config {
	
	public static int WIDTH = 1280;
	public static int HEIGHT = 720;
	public static Vector3f AMBIENT_LIGHT = new Vector3f(0.1f, 0.1f, 0.11f);

	@Toggable(group = "Quality settings") public static volatile boolean useParallax = false;
	@Toggable(group = "Quality settings") public static volatile boolean useSteepParallax = false;
	@Toggable(group = "Quality settings") public static volatile boolean useAmbientOcclusion = true;
	@Toggable(group = "Debug") public static volatile boolean useFrustumCulling = true;
	public static volatile boolean useInstantRadiosity = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_GI = false;
	@Toggable(group = "Quality settings") public static volatile boolean useSSR = false;
	@Toggable(group = "Quality settings") public static volatile boolean MULTIPLE_DIFFUSE_SAMPLES = true;
	@Toggable(group = "Quality settings") public static volatile boolean MULTIPLE_DIFFUSE_SAMPLES_PROBES = true;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_DIFFUSE = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_DIFFUSE_PROBES = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_SPECULAR = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_CONETRACING_FOR_SPECULAR_PROBES = false;
	@Toggable(group = "Quality settings") public static volatile boolean PRECOMPUTED_RADIANCE = true;
	@Toggable(group = "Quality settings") public static volatile boolean CALCULATE_ACTUAL_RADIANCE = true;
	@Toggable(group = "Quality settings") public static volatile boolean SSR_FADE_TO_SCREEN_BORDERS = true;
	@Toggable(group = "Quality settings") public static volatile boolean SSR_TEMPORAL_FILTERING = true;
	@Toggable(group = "Quality settings") public static volatile boolean USE_PCF = false;
	@Toggable(group = "Debug") public static volatile boolean DRAWLINES_ENABLED = false;
	@Toggable(group = "Debug") public static volatile boolean DRAWSCENE_ENABLED = true;
	@Toggable(group = "Debug") public static volatile boolean DEBUGDRAW_PROBES = false;
	@Toggable(group = "Debug") public static volatile boolean DEBUGDRAW_PROBES_WITH_CONTENT = false;
	@Toggable(group = "Quality settings") public static volatile boolean CONTINUOUS_DRAW_PROBES = false;
	@Toggable(group = "Debug") public static volatile boolean DEBUGFRAME_ENABLED = false;
	@Toggable(group = "Debug") public static volatile boolean DRAWLIGHTS_ENABLED = false;
	@Toggable(group = "Quality settings") public static volatile boolean DRAW_PROBES = true;
	@Toggable(group = "Debug") public static volatile boolean LOCK_FPS = false;
	@Adjustable(group = "Debug") public static volatile float CAMERA_SPEED = 1.0f;
	@Toggable(group = "Effects") public static volatile boolean SCATTERING = true;
	@Adjustable(group = "Effects") public static volatile float RAINEFFECT = 0.0f;
	@Adjustable(group = "Effects") public static volatile float AMBIENTOCCLUSION_TOTAL_STRENGTH = 0.5f;
	@Adjustable(group = "Effects") public static volatile float AMBIENTOCCLUSION_RADIUS = 0.0250f;
	@Adjustable(group = "Effects") public static volatile float EXPOSURE = 8f;
	@Toggable(group = "Effects") public static volatile boolean USE_BLOOM = true;
	@Toggable(group = "Effects") public static volatile boolean AUTO_EXPOSURE_ENABLED = true;
	@Toggable(group = "Effects") public static volatile boolean ENABLE_POSTPROCESSING = false;
	@Toggable(group = "Quality settings") public static volatile boolean USE_DPSM = false;
    private static volatile boolean headless = false;

    public static volatile int currentModelLod = 0;

    private Config() { super(); }

    public static boolean isHeadless() {
        return headless;
    }

    public static void setHeadless(boolean headless) {
        Config.headless = headless;
        AppContext.getEventBus().post(new HeadlessChangedEvent());
    }
}
