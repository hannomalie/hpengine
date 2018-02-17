package de.hanno.hpengine.util.gui;

import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.WebButtonGroup;
import com.alee.extended.tab.WebDocumentPane;
import com.alee.extended.tree.WebCheckBoxTree;
import com.alee.laf.button.WebButton;
import com.alee.laf.button.WebToggleButton;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.filechooser.WebFileChooser;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenu;
import com.alee.laf.menu.WebMenuBar;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.progressbar.WebProgressBar;
import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.slider.WebSlider;
import com.alee.laf.splitpane.WebSplitPane;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebTextField;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import com.alee.utils.SwingUtils;
import com.alee.utils.swing.Customizer;
import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.container.Octree;
import de.hanno.hpengine.engine.event.*;
import de.hanno.hpengine.engine.graphics.light.AreaLight;
import de.hanno.hpengine.engine.graphics.light.PointLight;
import de.hanno.hpengine.engine.graphics.light.TubeLight;
import de.hanno.hpengine.engine.graphics.renderer.command.AddCubeMapCommand;
import de.hanno.hpengine.engine.graphics.renderer.command.AddTextureCommand;
import de.hanno.hpengine.engine.graphics.renderer.command.AddTextureCommand.TextureResult;
import de.hanno.hpengine.engine.graphics.renderer.command.RenderProbeCommandQueue;
import de.hanno.hpengine.engine.graphics.renderer.command.Result;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.GBuffer;
import de.hanno.hpengine.engine.graphics.renderer.environmentsampler.EnvironmentSampler;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.model.texture.TextureManager;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;
import de.hanno.hpengine.engine.scene.Scene;
import de.hanno.hpengine.engine.threads.TimeStepThread;
import de.hanno.hpengine.util.TestSceneUtil;
import de.hanno.hpengine.util.commandqueue.FutureCallable;
import de.hanno.hpengine.util.gui.container.ReloadableScrollPane;
import de.hanno.hpengine.util.gui.container.ReloadableTabbedPane;
import de.hanno.hpengine.util.gui.input.SliderInput;
import de.hanno.hpengine.util.gui.input.TitledPanel;
import de.hanno.hpengine.util.gui.structure.*;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import net.engio.mbassy.listener.Handler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.joml.Vector3f;

import javax.script.ScriptException;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.lwjgl.glfw.GLFW.glfwSwapInterval;


public class DebugFrame implements HostComponent {

    private Engine engine;

    private WebFrame mainFrame = new WebFrame("Main");
	private WebFrame entityViewFrame = new WebFrame("Entity");
	private final WebTabbedPane tabbedPane = new ReloadableTabbedPane();
	
    JTable materialTable;
    private final ReloadableScrollPane materialPane;
    JTable textureTable;
    private final ReloadableScrollPane texturePane;
	private final ReloadableScrollPane mainLightPane;
    JTable pointsLightsTable;
	private final ReloadableScrollPane pointLightsPane;
    JTable tubeLightsTable;
	private final ReloadableScrollPane tubeLightsPane;
    JTable areaLightsTable;
    private final ReloadableScrollPane areaLightsPane;

    private SceneTree sceneTree;
	private ReloadableScrollPane scenePane;
    private ProbesTree probesTree;
    private ReloadableScrollPane probesPane;
    private final JTextPane output = new JTextPane();
    private final ReloadableScrollPane outputPane = new ReloadableScrollPane(output);
    private final JTextPane infoLeft = new JTextPane();
    private final JTextPane infoRight = new JTextPane();
    WebSplitPane infoSplitPane = new WebSplitPane(WebSplitPane.HORIZONTAL_SPLIT, infoLeft, infoRight);
	private final ReloadableScrollPane infoPane = new ReloadableScrollPane(infoSplitPane);
	private WebDocumentPane<ScriptDocumentData> scriptsPane = new WebDocumentPane<>();
	private WebScrollPane mainPane;
	private RSyntaxTextArea console = new RSyntaxTextArea(
	"temp = Java.type('org.joml.Vector3f');" +
	"for each(var probe in renderer.getEnvironmentProbeManager().getProbes()) {" +
	"	probe.move(new temp(0,-10,0));" +
	"}");
	private RTextScrollPane consolePane = new RTextScrollPane(console);

	private WebToggleButton toggleProfiler = new WebToggleButton("Profiling", GPUProfiler.PROFILING_ENABLED);
	private WebToggleButton toggleProfilerPrint = new WebToggleButton("Print Profiling", GPUProfiler.PRINTING_ENABLED);
	private WebToggleButton dumpAverages = new WebToggleButton("Dump Averages");
	private WebToggleButton toggleParallax = new WebToggleButton("Parallax", Config.getInstance().isUseParallax());
	private WebToggleButton toggleSteepParallax = new WebToggleButton("Steep Parallax", Config.getInstance().isUseSteepParallax());
	private WebToggleButton toggleAmbientOcclusion = new WebToggleButton("Ambient Occlusion", Config.getInstance().isUseAmbientOcclusion());
    private WebToggleButton toggleFrustumCulling = new WebToggleButton("CPU Frustum Culling", Config.getInstance().isUseCpuFrustumCulling());
    private WebToggleButton toggleOcclusionCulling = new WebToggleButton("Occlusion Culling", Config.getInstance().isUseGpuOcclusionCulling());
	private WebToggleButton toggleInstantRadiosity = new WebToggleButton("Instant Radiosity", Config.getInstance().isUseInstantRadiosity());
	private WebToggleButton toggleForceRevoxelization = new WebToggleButton("Force Revoxelization", Config.getInstance().isForceRevoxelization());
	private WebToggleButton toggleDrawLines = new WebToggleButton("Draw Lines", Config.getInstance().isDrawLines());
	private WebToggleButton toggleDrawScene = new WebToggleButton("Draw Scene", Config.getInstance().isDrawScene());
	private WebToggleButton toggleDrawOctree = new WebToggleButton("Draw Octree", Octree.DRAW_LINES);
	private WebToggleButton toggleDrawProbes = new WebToggleButton("Draw Probes", Config.getInstance().isDrawProbes());
	private WebButton forceProbeGBufferRedraw = new WebButton("Redraw Probe GBuffers");
	private WebToggleButton toggleUseGI = new WebToggleButton("GI", Config.getInstance().isUseGi());
	private WebToggleButton toggleUseSSR = new WebToggleButton("SSR", Config.getInstance().isUseSSR());
	private WebToggleButton toggleUseComputeShaderForReflections = new WebToggleButton("Computeshader reflections", GBuffer.USE_COMPUTESHADER_FOR_REFLECTIONS);
	private WebToggleButton toggleUseFirstBounceForProbeRendering = new WebToggleButton("First bounce for probes", GBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE);
	private WebToggleButton toggleUseSecondBounceForProbeRendering = new WebToggleButton("Second bounce for probes", GBuffer.RENDER_PROBES_WITH_SECOND_BOUNCE);

	private WebToggleButton toggleSampleCount2 = new WebToggleButton("2", GBuffer.IMPORTANCE_SAMPLE_COUNT == 2);
	private WebToggleButton toggleSampleCount4 = new WebToggleButton("4", GBuffer.IMPORTANCE_SAMPLE_COUNT == 4);
	private WebToggleButton toggleSampleCount8 = new WebToggleButton("8", GBuffer.IMPORTANCE_SAMPLE_COUNT == 8);
	private WebToggleButton toggleSampleCount16 = new WebToggleButton("16", GBuffer.IMPORTANCE_SAMPLE_COUNT == 16);
	private WebToggleButton toggleSampleCount32 = new WebToggleButton("32", GBuffer.IMPORTANCE_SAMPLE_COUNT == 32);
	private WebToggleButton toggleSampleCount64 = new WebToggleButton("64", GBuffer.IMPORTANCE_SAMPLE_COUNT == 64);
	private WebButtonGroup sampleCountGroup = new WebButtonGroup(true, toggleSampleCount2, toggleSampleCount4, toggleSampleCount8, toggleSampleCount16, toggleSampleCount32, toggleSampleCount64);
	
	private WebToggleButton toggleUseDeferredRenderingForProbes = new WebToggleButton("Deferred Rendering Probes", EnvironmentSampler.deferredRenderingForProbes);
	private WebToggleButton toggleDirectTextureOutput = new WebToggleButton("Direct Texture Output", Config.getInstance().isUseDirectTextureOutput());
    private WebComboBox directTextureOutputTextureIndexBoxGBuffer;
    private WebComboBox directTextureOutputTextureIndexBoxLABuffer;
    private WebToggleButton toggleDebugFrame = new WebToggleButton("Debug Frame", Config.getInstance().isDebugframeEnabled());
	private WebToggleButton toggleDrawLights = new WebToggleButton("Draw Lights", Config.getInstance().isDrawlightsEnabled());
	private WebToggleButton toggleVSync = new WebToggleButton("VSync", Config.getInstance().isVsync());
	private WebToggleButton toggleAutoExposure = new WebToggleButton("Auto Exposure", Config.getInstance().isAutoExposureEnabled());

	private WebToggleButton toggleProbeDrawCountOne = new WebToggleButton("1", RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL == 1);
	private WebToggleButton toggleProbeDrawCountTwo = new WebToggleButton("2", RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL == 2);
	private WebToggleButton toggleProbeDrawCountThree = new WebToggleButton("3", RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL == 3);
	private WebToggleButton toggleProbeDrawCountFour = new WebToggleButton("4", RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL == 4);
	private WebButtonGroup probeDrawCountGroup = new WebButtonGroup(true, toggleProbeDrawCountOne, toggleProbeDrawCountTwo, toggleProbeDrawCountThree, toggleProbeDrawCountFour);
	
	WebSlider ambientOcclusionRadiusSlider = new WebSlider ( WebSlider.HORIZONTAL );
	WebSlider ambientOcclusionTotalStrengthSlider = new WebSlider ( WebSlider.HORIZONTAL );

	private WebCheckBoxTree<DefaultMutableTreeNode> scene = new WebCheckBoxTree<>();
	private static WebTextField sceneViewFilterField = new WebTextField(15);
	private WebCheckBoxTree<DefaultMutableTreeNode> probes = new WebCheckBoxTree<DefaultMutableTreeNode>();
    private WebFrame addEntityFrame;
	private PerformanceMonitor performanceMonitor;
	private WebProgressBar progressBar = new WebProgressBar();
	private JFrame frame = mainFrame;
    private Runnable setTitleRunnable = () -> {
        try {
            String titleString = String.format("Render %03.0f fps | %03.0f ms - Update %03.0f fps | %03.0f ms",
					engine.getRenderer().getCurrentFPS(), engine.getRenderer().getMsPerFrame(), engine.getFpsCounter().getFPS(), engine.getFpsCounter().getMsPerFrame());
            frame.setTitle(titleString);
        } catch (ArrayIndexOutOfBoundsException e) { /*yea, i know...*/} catch (IllegalStateException | NullPointerException e) {
            frame.setTitle("HPEngine Renderer initializing...");
        }
    };

    public DebugFrame(Engine engine) {
        this.engine = engine;

        init();

        mainFrame.setLayout(new BorderLayout(5,5));
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(new Dimension(1200, 720));
        mainFrame.setVisible(true);

        mainFrame.setJMenuBar(getWebMenuBar());
        mainFrame.add(tabbedPane);

        initActionListeners();
        addTabs();

        initConsole();
        createPointLightsTab();

        infoSplitPane.setOneTouchExpandable ( true );
        infoSplitPane.setPreferredSize ( new Dimension ( 250, 200 ) );
        infoSplitPane.setDividerLocation(125);
        infoSplitPane.setContinuousLayout(true);
		engine.getEventBus().register(this);

        new TimeStepThread("DisplayTitleUpdate", 1.0f) {
            @Override
            public void update(float seconds) {
                SwingUtilities.invokeLater(getSetTitleRunnable());
            }
        }.start();
        materialPane = new ReloadableScrollPane(materialTable);
        texturePane = new ReloadableScrollPane(textureTable);
        mainLightPane = new ReloadableScrollPane(new MainLightView(this.engine));
        pointLightsPane = new ReloadableScrollPane(pointsLightsTable);
        tubeLightsPane = new ReloadableScrollPane(tubeLightsTable);
        areaLightsPane = new ReloadableScrollPane(areaLightsTable);
    }

    private void init() {
        textureTable = new JTable(new TextureTableModel(engine)) {
            { engine.getEventBus().register(this); }
            @Subscribe
            @Handler
            public void handle(TexturesChangedEvent e) {
                revalidate();
            }
        };

        areaLightsTable = new JTable(new AreaLightsTableModel(engine)) {
            { engine.getEventBus().register(this); }
            @Subscribe
            @Handler
            public void handle(LightChangedEvent e) {
                revalidate();
            }
        };
        materialTable = new MaterialTable(engine) {
            { engine.getEventBus().register(this); }
            @Subscribe
            @Handler
            public void handle(MaterialChangedEvent e) {
                revalidate();
            }

            @Subscribe
            @Handler
            public void handle(MaterialAddedEvent e) {
                revalidate();
            }

            @Subscribe
            @Handler
            public void handle(SceneInitEvent e) {
                revalidate();
            }
        };
        pointsLightsTable = new JTable(new PointLightsTableModel(this.engine));
        tubeLightsTable = new JTable(new TubeLightsTableModel(this.engine));
        directTextureOutputTextureIndexBoxGBuffer = new WebComboBox(Arrays.stream(engine.getRenderer().getGBuffer().getgBuffer().getRenderedTextures()).mapToObj(integer -> "GBuffer " + integer).collect(Collectors.toList()).toArray());
        directTextureOutputTextureIndexBoxLABuffer = new WebComboBox(Arrays.stream(engine.getRenderer().getGBuffer().getlaBuffer().getRenderedTextures()).mapToObj(integer -> "LABuffer " + integer).collect(Collectors.toList()).toArray());
        sceneTree = new SceneTree(this.engine);
        scenePane = new ReloadableScrollPane(sceneTree) {
            { engine.getEventBus().register(this); }
            @Subscribe @Handler public void handle(EntityAddedEvent e) { sceneTree.reload();viewport.setView((sceneTree)); }
        };
        probesTree = new ProbesTree(this.engine);
        probesPane = new ReloadableScrollPane(probesTree) {
            { engine.getEventBus().register(this); }
            @Subscribe @Handler public void handle(ProbeAddedEvent e) { probesTree.reload();viewport.setView((sceneTree)); }
        };
    }

    @Subscribe
    @Handler
	private void init(EngineInitializedEvent engineInitializedEvent) {
		createTubeLightsTab();
		createAreaLightsTab();

		initPerformanceChart();
//		redirectSystemStreams();
	}

    private void addTabs() {
        tabbedPane.addTab("Main", mainPane);
        tabbedPane.addTab("Scene", scenePane);
        tabbedPane.addTab("Probes", probesPane);
        tabbedPane.addTab("Texture", texturePane);
        tabbedPane.addTab("Material", materialPane);
        tabbedPane.addTab("Main light", mainLightPane);
        tabbedPane.addTab("PointLights", pointLightsPane);
        tabbedPane.addTab("TubeLights", tubeLightsPane);
        tabbedPane.addTab("AreaLights", areaLightsPane);
        tabbedPane.addTab("Console", consolePane);
        tabbedPane.addTab("Scripts", scriptsPane);
        tabbedPane.addTab("Output", outputPane);
		tabbedPane.addTab("Info", infoPane);
    }

    private WebMenuBar getWebMenuBar() {
        WebMenuBar menuBar = new WebMenuBar ();
        WebMenu menuScene = new WebMenu("Scene");
        menuBar.setUndecorated ( true );
        {
	        WebMenuItem sceneSaveMenuItem = new WebMenuItem ( "Save" );
	        sceneSaveMenuItem.addActionListener(e -> {
	        	String initialSelectionValue = engine.getSceneManager().getScene().getName() != "" ? engine.getSceneManager().getScene().getName() : "default";
				Object selection = WebOptionPane.showInputDialog( mainFrame, "Save scene as", "Save scene", WebOptionPane.QUESTION_MESSAGE, null, null, initialSelectionValue );
	        	if(selection != null) {
	        		boolean success = engine.getSceneManager().getScene().write(selection.toString());
	        		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
	                notificationPopup.setIcon(NotificationIcon.clock);
	                notificationPopup.setDisplayTime( 2000 );
	                notificationPopup.setContent(new WebLabel(success + ": Saved scene as " + selection));
	                NotificationManager.showNotification(notificationPopup);
	        	}
	        });

	        menuScene.add(sceneSaveMenuItem);
        }
        {
            WebMenuItem sceneLoadMenuItem = new WebMenuItem ( "Load Testscene" );

            sceneLoadMenuItem.addActionListener(e -> {
                new SwingWorkerWithProgress<Result<Scene>>(this, "Load de.hanno.hpengine.scene...", "Unable to load test de.hanno.hpengine.scene"){
                    @Override
                    public Result<Scene> doInBackground() throws Exception {
						startProgress("Loading test de.hanno.hpengine.scene");
                        engine.getSceneManager().getScene().addAll(TestSceneUtil.loadTestScene(engine.getMaterialManager(), engine.getPhysicsManager(), engine.getSceneManager().getScene().getEntityManager(), engine.getLightManager(), engine.getSceneManager().getScene(), engine.getModelComponentSystem()));
                        engine.getEventBus().post(new EntityAddedEvent());
						stopProgress();
                        return new Result(engine.getSceneManager().getScene());
                    }

                    @Override
                    public void done(Result<Scene> result) {
                        init(new EngineInitializedEvent());
                    }
                }.execute();
            });

            menuScene.add(sceneLoadMenuItem);
        }
        {
        	WebMenuItem sceneNewMenuItem = new WebMenuItem ( "New" );
        	sceneNewMenuItem.addActionListener(e -> {
	    			Scene newScene = new Scene(engine);
	    			engine.getSceneManager().setScene(newScene);
	    			init(new EngineInitializedEvent());
        	});

	        menuScene.add(sceneNewMenuItem);
        }
        WebMenu menuEntity = new WebMenu("Entity");
        {
        	WebMenuItem entityAddMenuItem = new WebMenuItem ( "Add new" );
        	entityAddMenuItem.addActionListener(e -> {

                SwingUtilities.invokeLater(() -> {
                    addEntityFrame = new WebFrame("Add Entity");
                    addEntityFrame.setSize(600, 300);
                    addEntityFrame.add(new AddEntityView(engine, addEntityFrame, this));
                    addEntityFrame.setVisible(true);
                });
			});

        	menuEntity.add(entityAddMenuItem);
        }
        WebMenu menuProbe = new WebMenu("Probe");
        {
        	WebMenuItem probeAddMenuItem = new WebMenuItem ( "Add" );
        	probeAddMenuItem.addActionListener(e -> {

                new SwingWorkerWithProgress<Result>(this, "Adding Probe...", "Failed to add probe") {
					@Override
					public Result doInBackground() throws Exception {
                        CompletableFuture<Result> future = engine.getGpuContext().execute(new FutureCallable() {
                            @Override
                            public Result execute() throws Exception {
                                // TODO: Remove this f***
                                EnvironmentProbe probe = engine.getSceneManager().getScene().getEnvironmentProbeManager().getProbe(new Vector3f(), 50);
                                engine.getRenderer().addRenderProbeCommand(probe, true);
                                return new Result(true);
                            }
                        });

						return future.get(5, TimeUnit.MINUTES);
					}

					@Override
					public void done(Result result) {
					}

				}.execute();
        	});

        	menuProbe.add(probeAddMenuItem);
        }

        WebMenu menuLight = new WebMenu("Light");
        {
        	WebMenuItem lightAddMenuItem = new WebMenuItem ( "Add PointLight" );
        	lightAddMenuItem.addActionListener(e -> {
                CompletableFuture<Result> future = engine.getGpuContext().execute(new FutureCallable() {
                    @Override
                    public Result execute() throws Exception {
                        engine.getSceneManager().getScene().addPointLight(engine.getLightManager().getPointLight(50));
                        return new Result(true);
                    }
                });

        		Result result = null;
				try {
					result = future.get(5, TimeUnit.SECONDS);
				} catch (Exception e1) {
					showError("Failed to add light");
				}

				if (result == null || !result.isSuccessful()) {
					showError("Failed to add light");
				} else {
					showSuccess("Added light");
	        		pointLightsPane.reload();
				}

        	});

        	menuLight.add(lightAddMenuItem);
        }
        {
        	WebMenuItem lightAddMenuItem = new WebMenuItem ( "Add TubeLight" );
        	lightAddMenuItem.addActionListener(e -> {
                CompletableFuture<Result<Boolean>> future = engine.getGpuContext().execute(new FutureCallable() {
                    @Override
                    public Result<Boolean> execute() throws Exception {
                        engine.getSceneManager().getScene().addTubeLight(engine.getLightManager().getTubeLight());
                        return new Result(true);
                    }
                });

        		Result result = null;
				try {
					result = future.get(5, TimeUnit.MINUTES);
				} catch (Exception e1) {
					showError("Failed to add light");
				}

				if (!result.isSuccessful()) {
					showError("Failed to add light");
				} else {
					showSuccess("Added light");
	        		tubeLightsPane.reload();
				}

        	});

        	menuLight.add(lightAddMenuItem);
        }
        {
        	WebMenuItem lightAddMenuItem = new WebMenuItem ( "Add AreaLight" );
        	lightAddMenuItem.addActionListener(e -> {
                CompletableFuture<Result> future = engine.getGpuContext().execute(new FutureCallable() {
                    @Override
                    public Result execute() throws Exception {
                        engine.getSceneManager().getScene().getAreaLights().add(engine.getLightManager().getAreaLight(50, 50, 20));
                        return new Result(true);
                    }
                });

        		Result result = null;
				try {
					result = future.get(5, TimeUnit.MINUTES);
				} catch (Exception e1) {
					showError("Failed to add light");
				}

				if (!result.isSuccessful()) {
					showError("Failed to add light");
				} else {
					showSuccess("Added light");
	        		areaLightsPane.reload();
				}

        	});

        	menuLight.add(lightAddMenuItem);
        }

        WebMenuItem runScriptMenuItem = new WebMenuItem("Run Script");
        runScriptMenuItem.addActionListener(e -> {
			try {
				engine.getScriptManager().eval(console.getText());
			} catch (ScriptException e1) {
				showError("Line " + e1.getLineNumber() + " contains errors.");
				e1.printStackTrace();
			}
		});

        WebMenuItem saveScriptMenuItem = new WebMenuItem("Save Script");
        saveScriptMenuItem.addActionListener(e -> {
			try {
				FileUtils.writeStringToFile(new File("hp/assets/scripts/console.js"), console.getText());
			} catch (IOException e1) {
				showError(e1.toString());
				e1.printStackTrace();
			}
		});

        WebMenuItem resetProfiling = new WebMenuItem("Reset Profiling");
        resetProfiling.addActionListener(e -> {
            CompletableFuture<Result> future = engine.getGpuContext().execute(new FutureCallable() {
                @Override
                public Result execute() throws Exception {
                    GPUProfiler.reset();
                    return new Result(true);
                }
            });

    		Result result = null;
			try {
				result = future.get(5, TimeUnit.MINUTES);
			} catch (Exception e1) {
				showError("Failed to reset profiler");
			}

			if (!result.isSuccessful()) {
				showError("Failed to reset profiler");
			} else {
				showSuccess("Reset profiler");
			}

    	});
        WebMenuItem refreshAll = new WebMenuItem("Refresh");
        refreshAll.addActionListener(e -> {
            init(new EngineInitializedEvent());
        });
        WebMenuItem toggleFPS = new WebMenuItem("Toggle FPS");
        toggleFPS.addActionListener(e -> {
            if(performanceMonitor == null) {
                // TODO: Remove this somehow
                initPerformanceChart();
            }
            performanceMonitor.toggleVisibility();
        });
        WebMenuItem loadMaterial = new WebMenuItem("Load Material");
        loadMaterial.addActionListener(e -> {
        	File chosenFile = WebFileChooser.showOpenDialog("./hp/assets/materials/", choser -> {
    			choser.setFileFilter(new FileNameExtensionFilter("Materials", "hpmaterial"));
    		});
    		if(chosenFile != null) {
                CompletableFuture<Result> future = engine.getGpuContext().execute(new FutureCallable() {
                    @Override
                    public Result execute() throws Exception {
                        engine.getMaterialManager().getMaterial(chosenFile.getName());
                        return new Result(true);
                    }
                });
				Result result = null;
				try {
					result = future.get(5, TimeUnit.SECONDS);
				} catch (Exception e1) {
					showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
				}

				if (!result.isSuccessful()) {
					showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
				} else {
					showSuccess("Added " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					texturePane.reload();
				}
    		}
    	});

        WebMenu menuTextures = new WebMenu("Texture");
        {
        	WebMenuItem textureAddMenuItem = new WebMenuItem ( "Add 2D" );
        	textureAddMenuItem.addActionListener(e -> {

				Customizer<WebFileChooser> customizer = arg0 -> {};
				File chosenFile = WebFileChooser.showOpenDialog("./hp/assets/models/textures", customizer);
	    		if(chosenFile != null) {
                    CompletableFuture<TextureResult> future = engine.getGpuContext().execute(new FutureCallable() {
                        @Override
                        public TextureResult execute() throws Exception {
                            return new AddTextureCommand(chosenFile.getPath()).execute(engine);
                        }
                    });
					TextureResult result = null;
					try {
						result = future.get(5, TimeUnit.MINUTES);
					} catch (Exception e1) {
						showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					}

					if (!result.isSuccessful()) {
						showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					} else {
						showSuccess("Added " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
						texturePane.reload();
					}
	    		}
        	});
        	menuTextures.add(textureAddMenuItem);
        }
        {
        	WebMenuItem textureSrgbaAddMenuItem = new WebMenuItem ( "Add 2D SRGBA" );
        	textureSrgbaAddMenuItem.addActionListener(e -> {

				Customizer<WebFileChooser> customizer = arg0 -> {};
				File chosenFile = WebFileChooser.showOpenDialog("./hp/assets/models/textures", customizer);
	    		if(chosenFile != null) {
                    CompletableFuture<TextureResult> future = engine.getGpuContext().execute(new FutureCallable() {
                        @Override
                        public TextureResult execute() throws Exception {
                            return new AddTextureCommand(chosenFile.getPath(), true).execute(engine);
                        }
                    });
					TextureResult result = null;
					try {
						result = future.get(5, TimeUnit.MINUTES);
					} catch (Exception e1) {
						showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					}

					if (!result.isSuccessful()) {
						showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					} else {
						showSuccess("Added " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
						texturePane.reload();
					}

	    		}

        	});
        	menuTextures.add(textureSrgbaAddMenuItem);
        }
        {
        	WebMenuItem textureAddMenuItem = new WebMenuItem ( "Add Cube" );
    		textureAddMenuItem.addActionListener(e -> {

				Customizer<WebFileChooser> customizer = arg0 -> {};
				File chosenFile = WebFileChooser.showOpenDialog("./hp/assets/models/textures", customizer);
	    		if(chosenFile != null) {
                    CompletableFuture<TextureResult> future = engine.getGpuContext().execute(new FutureCallable() {
                        @Override
                        public TextureResult execute() throws Exception {
                            return new AddCubeMapCommand(chosenFile.getPath()).execute(engine);
                        }
                    });

					TextureResult result = null;
					try {
						result = future.get(5, TimeUnit.MINUTES);
					} catch (Exception e1) {
						showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					}

					if (!result.isSuccessful()) {
						showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					} else {
						showSuccess("Added " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
						texturePane.reload();
					}

	    		}

        	});
        	menuTextures.add(textureAddMenuItem);
        }

        menuBar.add(menuScene);
        menuBar.add(menuEntity);
        menuBar.add(menuProbe);
        menuBar.add(menuLight);
        menuBar.add(menuTextures);
        menuBar.add(loadMaterial);
        menuBar.add(runScriptMenuItem);
        menuBar.add(saveScriptMenuItem);
        menuBar.add(resetProfiling);
        menuBar.add(refreshAll);
        menuBar.add(toggleFPS);
        menuBar.add(sceneViewFilterField);
        menuBar.add(progressBar);
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setString ( "Nothing to do..." );

        sceneViewFilterField.addActionListener(e -> {
            TreeModel model = scene.getModel();
            scene.setModel(null);
            scene.setModel(model);
        });

        return menuBar;
    }

    private void initConsole() {
        try {
            console.setText(FileUtils.readFileToString(new File("hp/assets/scripts/console.js")));
        } catch (IOException e2) {
            e2.printStackTrace();
        }
        console.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        console.setCodeFoldingEnabled(true);
        console.addKeyListener(new KeyListener() {
            private volatile boolean saving = false;
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {

            }

            @Override
            public void keyReleased(KeyEvent e) {
                if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_S) {
                    if(saving) { return; }
                    saving = true;
                    System.out.println("Saving...");
                    try {
                        FileUtils.write(new File("hp/assets/scripts/console.js"), console.getText());
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    } finally {
                        saving = false;
                    }
                }
            }
        });
        AutoCompletion ac = new AutoCompletion(engine.getScriptManager().getProvider());
        ac.install(console);
    }

    public void startProgress(String label) {
		if(label == null) {return; }
		progressBar.setIndeterminate(true);
		progressBar.setStringPainted(true);
		progressBar.setString(label);
	}

	public void stopProgress() {
		progressBar.setIndeterminate(false);
		progressBar.setString("Idle");
	}

	private void initActionListeners() {
        List<Component> mainButtonElements = new ArrayList<>();

		toggleProfiler.addActionListener( e -> {

            Boolean result = engine.getGpuContext().calculate(() -> {
				GPUProfiler.PROFILING_ENABLED = !GPUProfiler.PROFILING_ENABLED;
				return true;
			});
			try {
				if (result.equals(Boolean.TRUE)) {
					showSuccess("Profiling switched");
				} else {
					showError("Profiling not switched");
				}
			} catch (Exception e1) {
				showError("Profiling can't be switched");
			}
			
		});
		toggleProfilerPrint.addActionListener( e -> {

            CompletableFuture<Boolean> future = engine.getGpuContext().execute(new FutureCallable() {
                @Override
                public Boolean execute() throws Exception {
                    GPUProfiler.PRINTING_ENABLED = !GPUProfiler.PRINTING_ENABLED;
                    return true;
                }
            });
			Boolean result = null;
			try {
				result = future.get(5, TimeUnit.MINUTES);
				if (result.equals(Boolean.TRUE)) {
					showSuccess("Printing switched");
				} else {
					showError("Printing can't be switched");
				}
			} catch (Exception e1) {
				showError("Profiling can't be switched");
			}
			
		});
		//////////////////////////////
		Map<String, List<Component>> toggleButtonsWithGroups = new HashMap<>();
		for (Field field : Config.class.getDeclaredFields()) {
			for (Annotation annotation : field.getDeclaredAnnotations()) {
				if(annotation instanceof Toggable) {
					createWebToggableButton(toggleButtonsWithGroups, field, annotation);
				} else if(annotation instanceof Adjustable) {
// TODO: FEINSCHLIFF
//					createWebSlider(world, toggleButtonsWithGroups, field, annotation);
				}
			}
		}
		for (Entry<String, List<Component>> groupAndButtons : toggleButtonsWithGroups.entrySet()) {
			Component[] toggleButtonsArray = new Component[groupAndButtons.getValue().size()];
			groupAndButtons.getValue().toArray(toggleButtonsArray);
			mainButtonElements.add(new TitledPanel(groupAndButtons.getKey(), toggleButtonsArray));
		}
		/////////////////////
		
		dumpAverages.addActionListener(e -> {
            engine.getGpuContext().execute(() -> {
                GPUProfiler.DUMP_AVERAGES = ! GPUProfiler.DUMP_AVERAGES;
			});
		});
		
		toggleParallax.addActionListener( e -> {
			Config.getInstance().setUseParallax(!Config.getInstance().isUseParallax());
			Config.getInstance().setUseSteepParallax(false);
		});
		
		toggleSteepParallax.addActionListener(e -> {
			Config.getInstance().setUseSteepParallax(!Config.getInstance().isUseSteepParallax());
			Config.getInstance().setUseParallax(false);
		});

		toggleAmbientOcclusion.addActionListener(e -> {
			Config.getInstance().setUseAmbientOcclusion(!Config.getInstance().isUseAmbientOcclusion());
//			de.hanno.hpengine.engine.getEventBus().post(new GlobalDefineChangedEvent());
		});

        toggleFrustumCulling.addActionListener(e -> {
            Config.getInstance().setUseCpuFrustumCulling(!Config.getInstance().isUseCpuFrustumCulling());
        });
        toggleOcclusionCulling.addActionListener(e -> {
            Config.getInstance().setUseGpuOcclusionCulling(!Config.getInstance().isUseGpuOcclusionCulling());
        });
		toggleInstantRadiosity.addActionListener(e -> {
			Config.getInstance().setUseInstantRadiosity(!Config.getInstance().isUseInstantRadiosity());
		});
		toggleForceRevoxelization.addActionListener(e -> {
			Config.getInstance().setForceRevoxelization(!Config.getInstance().isForceRevoxelization());
		});

		toggleDrawLines.addActionListener(e -> {
			Config.getInstance().setDrawLines(!Config.getInstance().isDrawLines());
		});
		toggleDrawScene.addActionListener(e -> {
			Config.getInstance().setDrawScene(!Config.getInstance().isDrawScene());
		});

		toggleDrawOctree.addActionListener(e -> {
			Octree.DRAW_LINES = !Octree.DRAW_LINES;
		});
		forceProbeGBufferRedraw.addActionListener(e -> {
            engine.getSceneManager().getScene().getEnvironmentProbeManager().getProbes().forEach(probe -> {
				probe.getSampler().resetDrawing();
			});
		});
		toggleDrawProbes.addActionListener(e -> {
			Config.getInstance().setDrawProbes(!Config.getInstance().isDrawProbes());
		});
		toggleUseGI.addActionListener(e -> {
			Config.getInstance().setUseGi(!Config.getInstance().isUseGi());
		});
		toggleUseSSR.addActionListener(e -> {
			Config.getInstance().setUseSSR(!Config.getInstance().isUseSSR());
		});
		toggleUseDeferredRenderingForProbes.addActionListener(e -> {
			EnvironmentSampler.deferredRenderingForProbes = !EnvironmentSampler.deferredRenderingForProbes;
		});
		toggleUseFirstBounceForProbeRendering.addActionListener(e -> {
			GBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE = !GBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE;
			engine.getEventBus().post(new MaterialChangedEvent()); // TODO: Create custom de.hanno.hpengine.event class...should redraw probes
		});
		toggleUseSecondBounceForProbeRendering.addActionListener(e -> {
			GBuffer.RENDER_PROBES_WITH_SECOND_BOUNCE = !GBuffer.RENDER_PROBES_WITH_SECOND_BOUNCE;
			engine.getEventBus().post(new MaterialChangedEvent()); // TODO: Create custom de.hanno.hpengine.event class...should redraw probes
		});
		toggleUseComputeShaderForReflections.addActionListener(e -> {
			GBuffer.USE_COMPUTESHADER_FOR_REFLECTIONS = !GBuffer.USE_COMPUTESHADER_FOR_REFLECTIONS;
		});
		toggleDirectTextureOutput.addActionListener(e -> {
			Config.getInstance().setUseDirectTextureOutput(!Config.getInstance().isUseDirectTextureOutput());
		});
        directTextureOutputTextureIndexBoxGBuffer.addActionListener(e -> {
            int selectedIndex = directTextureOutputTextureIndexBoxGBuffer.getSelectedIndex();
            RenderTarget renderTarget = engine.getRenderer().getGBuffer().getgBuffer();
            Config.getInstance().setDirectTextureOutputTextureIndex(renderTarget.getRenderedTexture(Math.min(selectedIndex, renderTarget.getRenderedTextures().length)));
        });
        directTextureOutputTextureIndexBoxLABuffer.addActionListener(e -> {
            int selectedIndex = directTextureOutputTextureIndexBoxLABuffer.getSelectedIndex();
            RenderTarget renderTarget = engine.getRenderer().getGBuffer().getlaBuffer();
            Config.getInstance().setDirectTextureOutputTextureIndex(renderTarget.getRenderedTexture(Math.min(selectedIndex, renderTarget.getRenderedTextures().length)));
        });

		toggleDebugFrame.addActionListener(e -> {
			Config.getInstance().setDebugframeEnabled(!Config.getInstance().isDebugframeEnabled());
		});

		toggleDrawLights.addActionListener(e -> {
			Config.getInstance().setDrawlightsEnabled(!Config.getInstance().isDrawlightsEnabled());
		});
		toggleVSync.addActionListener(e -> {
			Config.getInstance().setVsync(!Config.getInstance().isVsync());
            engine.getGpuContext().execute(() -> {
				if(Config.getInstance().isVsync()) {
					glfwSwapInterval(1);
				} else {
					glfwSwapInterval(0);
				}
			});
		});
		toggleAutoExposure.addActionListener(e -> {
			Config.getInstance().setAutoExposureEnabled(!Config.getInstance().isAutoExposureEnabled());
			if(!Config.getInstance().isAutoExposureEnabled()) { Config.getInstance().setExposure(5); }
		});

	    ambientOcclusionRadiusSlider.setMinimum ( 0 );
	    ambientOcclusionRadiusSlider.setMaximum ( 1000 );
	    ambientOcclusionRadiusSlider.setMinorTickSpacing ( 250 );
	    ambientOcclusionRadiusSlider.setMajorTickSpacing ( 500 );
	    ambientOcclusionRadiusSlider.setValue((int) (Config.getInstance().getAmbientocclusionRadius() * 10000f));
	    ambientOcclusionRadiusSlider.setPaintTicks ( true );
	    ambientOcclusionRadiusSlider.setPaintLabels ( true );
	    ambientOcclusionRadiusSlider.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				WebSlider slider = (WebSlider) e.getSource();
				int value = slider.getValue();
				float valueAsFactor = ((float) value) / 10000;
				Config.getInstance().setAmbientocclusionRadius(valueAsFactor);
			}
		});

		ambientOcclusionTotalStrengthSlider.setMinimum ( 0 );
		ambientOcclusionTotalStrengthSlider.setMaximum ( 200 );
		ambientOcclusionTotalStrengthSlider.setMinorTickSpacing ( 20 );
		ambientOcclusionTotalStrengthSlider.setMajorTickSpacing ( 50 );
		ambientOcclusionTotalStrengthSlider.setValue((int) (Config.getInstance().getAmbientocclusionTotalStrength() * 100f));
		ambientOcclusionTotalStrengthSlider.setPaintTicks ( true );
		ambientOcclusionTotalStrengthSlider.setPaintLabels ( true );
		ambientOcclusionTotalStrengthSlider.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				WebSlider slider = (WebSlider) e.getSource();
				int value = slider.getValue();
				float valueAsFactor = ((float) value) / 100;
				Config.getInstance().setAmbientocclusionTotalStrength(valueAsFactor);
			}
		});

////////////////
	    
	    toggleSampleCount2.addActionListener(e -> { GBuffer.IMPORTANCE_SAMPLE_COUNT = Integer.valueOf(toggleSampleCount2.getLabel()); });
	    toggleSampleCount4.addActionListener(e -> { GBuffer.IMPORTANCE_SAMPLE_COUNT = Integer.valueOf(toggleSampleCount4.getLabel()); });
	    toggleSampleCount8.addActionListener(e -> { GBuffer.IMPORTANCE_SAMPLE_COUNT = Integer.valueOf(toggleSampleCount8.getLabel()); });
	    toggleSampleCount16.addActionListener(e -> { GBuffer.IMPORTANCE_SAMPLE_COUNT = Integer.valueOf(toggleSampleCount16.getLabel()); });
	    toggleSampleCount32.addActionListener(e -> { GBuffer.IMPORTANCE_SAMPLE_COUNT = Integer.valueOf(toggleSampleCount32.getLabel()); });
	    toggleSampleCount64.addActionListener(e -> { GBuffer.IMPORTANCE_SAMPLE_COUNT = Integer.valueOf(toggleSampleCount64.getLabel()); });
		
	    toggleProbeDrawCountOne.addActionListener(e -> { RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL = Integer.valueOf(toggleProbeDrawCountOne.getLabel()); });
	    toggleProbeDrawCountTwo.addActionListener(e -> { RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL = Integer.valueOf(toggleProbeDrawCountTwo.getLabel()); });
	    toggleProbeDrawCountThree.addActionListener(e -> { RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL = Integer.valueOf(toggleProbeDrawCountThree.getLabel()); });
	    toggleProbeDrawCountFour.addActionListener(e -> { RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL = Integer.valueOf(toggleProbeDrawCountFour.getLabel()); });
		mainButtonElements.add(new TitledPanel("Debug Drawing", toggleDrawLines, toggleDrawScene, toggleDrawOctree, toggleDrawLights, toggleDebugFrame));
		mainButtonElements.add(new TitledPanel("Probes", forceProbeGBufferRedraw, toggleUseComputeShaderForReflections, toggleDrawProbes, probeDrawCountGroup, toggleDirectTextureOutput, directTextureOutputTextureIndexBoxGBuffer, directTextureOutputTextureIndexBoxLABuffer));
		mainButtonElements.add(new TitledPanel("Profiling", toggleProfiler, toggleProfilerPrint, dumpAverages));
		mainButtonElements.add(new TitledPanel("Qualitiy settings", sampleCountGroup, toggleUseGI, toggleUseSSR, toggleUseDeferredRenderingForProbes, toggleUseFirstBounceForProbeRendering, toggleUseSecondBounceForProbeRendering, toggleAmbientOcclusion, toggleFrustumCulling, toggleOcclusionCulling, toggleForceRevoxelization, toggleAutoExposure, toggleVSync,
			new SliderInput("Exposure", WebSlider.HORIZONTAL, 1, 40, (int) Config.getInstance().getExposure()) {
			@Override public void onValueChange(int value, int delta) {
				Config.getInstance().setExposure(value);
			}},
			new SliderInput("Scattering", WebSlider.HORIZONTAL, 0, 8, 1) {
				@Override public void onValueChange(int value, int delta) {
                    engine.getLightManager().directionalLight.setScatterFactor((float)value);
				}
			},
			new SliderInput("Rainy", WebSlider.HORIZONTAL, 0, 100, (int) (100* Config.getInstance().getRainEffect())) {
				@Override public void onValueChange(int value, int delta) {
					Config.getInstance().setRainEffect((float) value/100);
					engine.getEventBus().post(new GlobalDefineChangedEvent());
				}
			},
			new SliderInput("Camera Speed", WebSlider.HORIZONTAL, 0, 100, (int) (100* Config.getInstance().getCameraSpeed())) {
				@Override public void onValueChange(int value, int delta) {
					Config.getInstance().setCameraSpeed((float) value/100);
				}
			},
			new SliderInput("Texture Streaming Threshold (MS)", WebSlider.HORIZONTAL, 0, 10000, (int) (TextureManager.TEXTURE_UNLOAD_THRESHOLD_IN_MS)) {
				@Override public void onValueChange(int value, int delta) {
					TextureManager.TEXTURE_UNLOAD_THRESHOLD_IN_MS = value;
				}
			}
		));
        Component[] mainButtonsElementsArray = new Component[mainButtonElements.size()];
        mainButtonElements.toArray(mainButtonsElementsArray);
        GridPanel buttonGridPanel = new GridPanel(mainButtonsElementsArray.length/2, 2, 5, mainButtonsElementsArray);
////////////////
	    
        mainPane = new WebScrollPane(buttonGridPanel);
        mainPane.getVerticalScrollBar().setUnitIncrement(32);
	}

	private void createWebSlider(Engine engine,
                                 Map<String, List<Component>> toggleButtonsWithGroups, Field field,
                                 Annotation annotation) {
		try {
			float f = field.getFloat(engine);
			Adjustable adjustable = (Adjustable) annotation;
			
			List<Component> groupList;
			if(toggleButtonsWithGroups.containsKey(adjustable.group())) {
				groupList = toggleButtonsWithGroups.get(adjustable.group());
			} else {
				groupList = new ArrayList<Component>();
				toggleButtonsWithGroups.put(adjustable.group(), groupList);
			}
			
			WebSlider slider = new WebSlider(WebSlider.HORIZONTAL);
			slider.setMinimum ( adjustable.minimum() );
			slider.setMaximum ( adjustable.maximum() );
			slider.setMinorTickSpacing ( adjustable.minorTickSpacing() );
			slider.setMajorTickSpacing ( adjustable.majorTickSpacing() );
			slider.setValue((int) (Config.getInstance().getAmbientocclusionRadius() * adjustable.factor()));
			slider.setPaintTicks ( true );
			slider.setPaintLabels ( true );
			slider.addChangeListener(new ChangeListener() {
				
				@Override
				public void stateChanged(ChangeEvent e) {
					try {
						float currentValue = field.getFloat(engine);
					} catch (IllegalArgumentException | IllegalAccessException e2) {
						e2.printStackTrace();
					}
					
					WebSlider slider = (WebSlider) e.getSource();
					int value = slider.getValue();
					float valueAsFactor = ((float) value) / adjustable.factor();
					try {
						field.setFloat(engine, valueAsFactor);
						engine.getEventBus().post(new GlobalDefineChangedEvent());
					} catch (IllegalArgumentException | IllegalAccessException e1) {
						e1.printStackTrace();
					}
				}
			});
			groupList.add(new WebLabel(field.getName()));
			groupList.add(slider);
			
		} catch (IllegalArgumentException | IllegalAccessException e1) {
			e1.printStackTrace();
		}
	}

	private void createWebToggableButton(Map<String, List<Component>> toggleButtonsWithGroups,
										 Field field, Annotation annotation) {
		try {
			field.setAccessible(true);
			boolean b = field.getBoolean(Config.getInstance());
			Toggable toggable = (Toggable) annotation;
			List<Component> groupList;
			if(toggleButtonsWithGroups.containsKey(toggable.group())) {
				groupList = toggleButtonsWithGroups.get(toggable.group());
			} else {
				groupList = new ArrayList<>();
				toggleButtonsWithGroups.put(toggable.group(), groupList);
			}
			
			WebToggleButton button = new WebToggleButton(field.getName(), b, e -> {
				boolean currentValue;
				try {
					currentValue = field.getBoolean(Config.getInstance());
					field.setBoolean(Config.getInstance(), !currentValue);
					engine.getEventBus().post(new GlobalDefineChangedEvent());
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			});
			groupList.add(button);
			
		} catch (IllegalArgumentException | IllegalAccessException e1) {
			e1.printStackTrace();
		}
	}

	private void initPerformanceChart() {
		if(performanceMonitor == null) {
            performanceMonitor = new PerformanceMonitor(engine, engine.getRenderer());
		}
		performanceMonitor.init();
	}
	
	private void redirectSystemStreams() {
		outputPane.setBorder(new LineBorder(Color.black, 1));
		DefaultCaret caret = (DefaultCaret) output.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		output.setEditable(false);
		output.setBackground(Color.BLACK);
		output.setForeground(Color.WHITE);
		final Font currFont = consolePane.getFont();
		output.setFont(new Font("Courier New", currFont.getStyle(), currFont.getSize()));
		
		  OutputStream out = new OutputStream() {
			  StyleContext sc = StyleContext.getDefaultStyleContext();
			    javax.swing.text.AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, Color.GREEN);

			    
			    @Override
			    public void write(int b) throws IOException {

			    	Document doc = output.getDocument();
			    	shrinkDocument(doc);
			    	try {
						doc.insertString(doc.getLength(), String.valueOf((char) b), aset);
					} catch (BadLocationException e) {
						e.printStackTrace();
					}
			    }

				private void shrinkDocument(Document doc) {
					if(doc.getLength() > 10000) {
			    		try {
							doc.remove(0, 5000);
						} catch (BadLocationException e) {
							e.printStackTrace();
						}
			    	}
				}
			 
			    @Override
			    public void write(byte[] b, int off, int len) throws IOException {
			    	Document doc = output.getDocument();
			    	shrinkDocument(doc);
			    	try {
						doc.insertString(doc.getLength(), new String(b, off, len), aset);
					} catch (BadLocationException e) {
						e.printStackTrace();
					}
			    }
			 
			    @Override
			    public void write(byte[] b) throws IOException {
			      write(b, 0, b.length);
			    }
			  };
		  
		  OutputStream outErr = new OutputStream() {
			  StyleContext sc = StyleContext.getDefaultStyleContext();
			    javax.swing.text.AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, Color.RED);

			    
			    @Override
			    public void write(int b) throws IOException {
			    	Document doc = output.getDocument();
			    	shrinkDocument(doc);
			    	try {
						doc.insertString(doc.getLength(), String.valueOf((char) b), aset);
					} catch (BadLocationException e) {
						e.printStackTrace();
					}
			    }
			 
			    @Override
			    public void write(byte[] b, int off, int len) throws IOException {
			    	Document doc = output.getDocument();
			    	shrinkDocument(doc);
			    	try {
						doc.insertString(doc.getLength(), new String(b, off, len), aset);
					} catch (BadLocationException e) {
						e.printStackTrace();
					}
			    }
			 
			    @Override
			    public void write(byte[] b) throws IOException {
			      write(b, 0, b.length);
			    }

				private void shrinkDocument(Document doc) {
					if(doc.getLength() > 10000) {
			    		try {
							doc.remove(0, 5000);
						} catch (BadLocationException e) {
							e.printStackTrace();
						}
			    	}
				}
			  };
		  		 
		  System.setOut(new PrintStream(out, true));
		  System.setErr(new PrintStream(outErr, true));
		}

    private void createPointLightsTab() {
        DebugFrame debugFrame = this;

        ListSelectionModel pointLightsCellSelectionModel = pointsLightsTable.getSelectionModel();
        pointLightsCellSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        pointLightsCellSelectionModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {

                int[] selectedRow = pointsLightsTable.getSelectedRows();
                int[] selectedColumns = pointsLightsTable
                        .getSelectedColumns();

                for (int i = 0; i < selectedRow.length; i++) {
                    for (int j = 0; j < selectedColumns.length; j++) {
                        PointLight selectedLight = engine.getSceneManager().getScene().getPointLights().get(selectedRow[i]);
                        entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                        entityViewFrame.getContentPane().removeAll();
                        entityViewFrame.pack();
                        entityViewFrame.setSize(1000, 600);
                        entityViewFrame.add(new PointLightView(engine, debugFrame, (PointLight) selectedLight));
                        entityViewFrame.setVisible(true);
                    }
                }
            }
        });
    }

	private void createTubeLightsTab() {
		DebugFrame debugFrame = this;
		ListSelectionModel tubeLightsCellSelectionModel = tubeLightsTable.getSelectionModel();
		tubeLightsCellSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		tubeLightsCellSelectionModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {

                int[] selectedRow = tubeLightsTable.getSelectedRows();
                int[] selectedColumns = tubeLightsTable.getSelectedColumns();

                for (int i = 0; i < selectedRow.length; i++) {
                    for (int j = 0; j < selectedColumns.length; j++) {
                        TubeLight selectedLight = engine.getSceneManager().getScene().getTubeLights().get(selectedRow[i]);
                        entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                        entityViewFrame.getContentPane().removeAll();
                        entityViewFrame.pack();
                        entityViewFrame.setSize(1000, 600);
                        entityViewFrame.add(new TubeLightView(engine, debugFrame, (TubeLight) selectedLight));
                        entityViewFrame.setVisible(true);
                    }
                }
            }
        });
	}

	private void createAreaLightsTab() {
		DebugFrame debugFrame = this;

		ListSelectionModel areaLightsCellSelectionModel = areaLightsTable.getSelectionModel();
		areaLightsCellSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		areaLightsCellSelectionModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {

                int[] selectedRow = areaLightsTable.getSelectedRows();
                int[] selectedColumns = areaLightsTable.getSelectedColumns();

                for (int i = 0; i < selectedRow.length; i++) {
                    for (int j = 0; j < selectedColumns.length; j++) {
                        AreaLight selectedLight = engine.getSceneManager().getScene().getAreaLights().get(selectedRow[i]);
                        entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                        entityViewFrame.getContentPane().removeAll();
                        entityViewFrame.pack();
                        entityViewFrame.setSize(1000, 600);
                        entityViewFrame.add(new AreaLightView(engine, debugFrame, selectedLight));
                        entityViewFrame.setVisible(true);
                    }
                }
            }
        });
	}

	public void showSuccess(String content) {
		if(content == null) {return; }
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(NotificationIcon.plus);
		notificationPopup.setDisplayTime(2000);
		notificationPopup.setContent(new WebLabel(content));
		NotificationManager.showNotification(mainFrame, notificationPopup);
	}

	public void showError(String content) {
		if(content == null) {return; }
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(NotificationIcon.error);
		notificationPopup.setDisplayTime( 2000 );
		notificationPopup.setContent(new WebLabel(content));
		NotificationManager.showNotification(mainFrame, notificationPopup);
	}

	@Subscribe
    @Handler
	public void handle(EntitySelectedEvent e) {
		entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		entityViewFrame.getContentPane().removeAll();
		entityViewFrame.pack();
		entityViewFrame.setSize(600, 700);
		entityViewFrame.add(new EntityView(engine, e.getEntity()));
		entityViewFrame.setVisible(true);
	}

    @Subscribe
    @Handler
    public void handle(FrameFinishedEvent event) {
        if(GPUProfiler.PROFILING_ENABLED) {
            SwingUtils.invokeLater(() -> {
				DrawResult drawResult1 = event.getDrawResult();
				String drawResult = drawResult1.toString();
				if(GPUProfiler.DUMP_AVERAGES) {
					drawResult += new String(GPUProfiler.getAveragesString());
				}
				infoLeft.setText(drawResult);
				infoRight.setText(event.getLatestGPUProfilingResult());
            });
        }
    }

    public static String getCurrentFilter() {
        return sceneViewFilterField.getText();
    }

    public Runnable getSetTitleRunnable() {
        return setTitleRunnable;
    }
}
