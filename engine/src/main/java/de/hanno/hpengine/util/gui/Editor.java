package de.hanno.hpengine.util.gui;

import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.WebTitledPanel;
import com.alee.extended.tab.WebDocumentPane;
import com.alee.extended.tree.WebCheckBoxTree;
import com.alee.laf.WebLookAndFeel;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.filechooser.WebFileChooser;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenu;
import com.alee.laf.menu.WebMenuBar;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.progressbar.WebProgressBar;
import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.splitpane.WebSplitPane;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebTextField;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import com.alee.utils.SwingUtils;
import com.alee.utils.swing.Customizer;
import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.engine.EngineImpl;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.config.SimpleConfig;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.event.EngineInitializedEvent;
import de.hanno.hpengine.engine.event.EntityAddedEvent;
import de.hanno.hpengine.engine.event.EntitySelectedEvent;
import de.hanno.hpengine.engine.event.FrameFinishedEvent;
import de.hanno.hpengine.engine.event.LightChangedEvent;
import de.hanno.hpengine.engine.event.MaterialAddedEvent;
import de.hanno.hpengine.engine.event.MaterialChangedEvent;
import de.hanno.hpengine.engine.event.MeshSelectedEvent;
import de.hanno.hpengine.engine.event.ProbeAddedEvent;
import de.hanno.hpengine.engine.event.SceneInitEvent;
import de.hanno.hpengine.engine.event.TexturesChangedEvent;
import de.hanno.hpengine.engine.graphics.light.area.AreaLight;
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem;
import de.hanno.hpengine.engine.graphics.light.point.PointLight;
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight;
import de.hanno.hpengine.engine.graphics.renderer.command.AddCubeMapCommand;
import de.hanno.hpengine.engine.graphics.renderer.command.AddTextureCommand;
import de.hanno.hpengine.engine.graphics.renderer.command.AddTextureCommand.TextureResult;
import de.hanno.hpengine.engine.graphics.renderer.command.Result;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.state.RenderSystem;
import de.hanno.hpengine.engine.model.ModelComponentSystem;
import de.hanno.hpengine.engine.model.texture.Texture;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;
import de.hanno.hpengine.engine.scene.Scene;
import de.hanno.hpengine.engine.scene.SimpleScene;
import de.hanno.hpengine.engine.threads.TimeStepThread;
import de.hanno.hpengine.util.TestSceneUtil;
import de.hanno.hpengine.util.commandqueue.FutureCallable;
import de.hanno.hpengine.util.gui.container.ReloadableScrollPane;
import de.hanno.hpengine.util.gui.container.ReloadableTabbedPane;
import de.hanno.hpengine.util.gui.input.TitledPanel;
import de.hanno.hpengine.util.gui.structure.AreaLightsTableModel;
import de.hanno.hpengine.util.gui.structure.MaterialTable;
import de.hanno.hpengine.util.gui.structure.PointLightsTableModel;
import de.hanno.hpengine.util.gui.structure.ProbesTree;
import de.hanno.hpengine.util.gui.structure.SceneTree;
import de.hanno.hpengine.util.gui.structure.TextureTable;
import de.hanno.hpengine.util.gui.structure.TubeLightsTableModel;
import de.hanno.hpengine.util.script.ScriptManager;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import net.engio.mbassy.listener.Handler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.script.ScriptException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static de.hanno.hpengine.util.gui.EditorUtilsKt.getButtons;


public class Editor implements HostComponent {

    private EngineImpl engine;
    private final SimpleConfig config;

    private WebFrame mainFrame = new WebFrame("Main");
	private WebFrame entityViewFrame = new WebFrame("Entity");
	private final WebTabbedPane tabbedPane = new ReloadableTabbedPane();
	
    JTable materialTable;
    private ReloadableScrollPane materialPane;
    JTable textureTable;
    private ReloadableScrollPane texturePane;
	private ReloadableScrollPane mainLightPane;
    JTable pointsLightsTable;
	private ReloadableScrollPane pointLightsPane;
    JTable tubeLightsTable;
	private ReloadableScrollPane tubeLightsPane;
    JTable areaLightsTable;
    private ReloadableScrollPane areaLightsPane;

    private SceneTree sceneTree;
	private ReloadableScrollPane scenePane;
    private ProbesTree probesTree;
    private ReloadableScrollPane probesPane;
    private final JTextPane output = new JTextPane();
    private ReloadableScrollPane outputPane;
    private final JTextPane infoLeft = new JTextPane();
    private final JTextPane infoRight = new JTextPane();
    WebSplitPane infoSplitPane;
	private ReloadableScrollPane infoPane;
	private WebDocumentPane<ScriptDocumentData> scriptsPane = new WebDocumentPane<>();
	private WebScrollPane mainPane;
	private RSyntaxTextArea console = new RSyntaxTextArea(
	"temp = Java.type('org.joml.Vector3f');" +
	"for each(var probe in renderer.getEnvironmentProbeManager().getProbes()) {" +
	"	probe.move(new temp(0,-10,0));" +
	"}");
	private RTextScrollPane consolePane;

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
					engine.getRenderManager().getCurrentFPS(), engine.getRenderManager().getMsPerFrame(), engine.getRenderManager().getFpsCounter().getFPS(), engine.getRenderManager().getMsPerFrame());
            frame.setTitle(titleString);
        } catch (ArrayIndexOutOfBoundsException e) { /*yea, i know...*/} catch (IllegalStateException | NullPointerException e) {
            frame.setTitle("HPEngine Renderer initializing...");
        }
    };
    private List<DirectTextureOutputItem> renderTargetTextures = new ArrayList<>();

    public Editor(EngineImpl engine, SimpleConfig config) {
        this.engine = engine;
        this.config = config;

        try {
            SwingUtils.invokeAndWait(WebLookAndFeel::install);
        } catch (InterruptedException | InvocationTargetException e) {
            e.printStackTrace();
        }

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
        createAreaLightsTab();

        engine.getEventBus().register(this);

        new TimeStepThread("DisplayTitleUpdate", 1.0f) {
            @Override
            public void update(float seconds) {
                SwingUtilities.invokeLater(getSetTitleRunnable());
            }
        }.start();

        engine.getRenderSystems().add(new RenderSystem() {

            @Override
            public void afterFrameFinished() {
                handle(new FrameFinishedEvent(engine.getRenderManager().getRenderState().getCurrentReadState().getLatestDrawResult()));
            }

            @Override
            public void render(@NotNull DrawResult result, @NotNull RenderState state) { }
        });
    }

    private void init() {

        infoSplitPane = new WebSplitPane(WebSplitPane.HORIZONTAL_SPLIT, infoLeft, infoRight);
        infoSplitPane.setOneTouchExpandable ( true );
        infoSplitPane.setPreferredSize ( new Dimension ( 250, 200 ) );
        infoSplitPane.setDividerLocation(125);
        infoSplitPane.setContinuousLayout(true);

        textureTable = new TextureTable(engine) {
            { engine.getEventBus().register(this); }
            @Subscribe
            @Handler
            public void handle(TexturesChangedEvent e) {
                revalidate();
            }
            @Subscribe
            @Handler
            public void handle(SceneInitEvent e) {
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
        pointsLightsTable = new JTable(new PointLightsTableModel(engine));
        tubeLightsTable = new JTable(new TubeLightsTableModel(engine));


        materialPane = new ReloadableScrollPane(materialTable);
        texturePane = new ReloadableScrollPane(textureTable);
        mainLightPane = new ReloadableScrollPane(new MainLightView(engine));
        pointLightsPane = new ReloadableScrollPane(pointsLightsTable);
        tubeLightsPane = new ReloadableScrollPane(tubeLightsTable);
        areaLightsPane = new ReloadableScrollPane(areaLightsTable);
        outputPane = new ReloadableScrollPane(output);
        infoPane = new ReloadableScrollPane(infoSplitPane);
        consolePane = new RTextScrollPane(console);

        sceneTree = new SceneTree(engine);
        scenePane = new ReloadableScrollPane(sceneTree) {
            { engine.getEventBus().register(this); }
            @Subscribe @Handler public void handle(EntityAddedEvent e) { sceneTree.reload();viewport.setView((sceneTree)); }
        };
        probesTree = new ProbesTree(engine);
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
	}

    private void addTabs() {
        tabbedPane.addTab("Main", mainPane);
        tabbedPane.addTab("Scene", scenePane);
        tabbedPane.addTab("Probes", probesPane);
        tabbedPane.addTab("Texture", texturePane);
        tabbedPane.addTab("SimpleMaterial", materialPane);
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
            WebMenuItem sceneLoadMenuItem = new WebMenuItem ( "Load Testscene" );

            sceneLoadMenuItem.addActionListener(e -> {
                new SwingWorkerWithProgress<Result<SimpleScene>>(this, "Load scene...", "Unable to load test scene"){
                    @Override
                    public Result<SimpleScene> doInBackground() throws Exception {
						startProgress("Loading test scene");
                        List<Entity> entities = TestSceneUtil.loadTestScene(engine.getScene().getMaterialManager(),
                                engine.getPhysicsManager(),
                                engine.getSceneManager().getScene().getEntityManager(),
                                engine.getScene().getEntitySystems().get(DirectionalLightSystem.class),
                                engine.getSceneManager().getScene(),
                                engine.getSceneManager().getScene().getComponentSystems().get(ModelComponentSystem.class),
                                engine.getConfig().getDirectories().getEngineDir());
                        throw new IllegalStateException("Doesnt work anymore, remove me");
//                        engine.getSceneManager().getScene().addAll(entities);
//                        engine.getEventBus().post(new EntityAddedEvent());
//						stopProgress();
//                        return new Result(engine.getSceneManager().getScene());
                    }

                    @Override
                    public void done(Result<SimpleScene> result) {
                        init(new EngineInitializedEvent());
                    }
                }.execute();
            });

            menuScene.add(sceneLoadMenuItem);
        }
        {
        	WebMenuItem sceneNewMenuItem = new WebMenuItem ( "New" );
        	sceneNewMenuItem.addActionListener(e -> {
	    			Scene newScene = new SimpleScene(engine);
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

                new SwingWorkerWithProgress<>(this, "Adding Probe...", "Failed to add probe") {
                    @Override
                    public Result doInBackground() {
                        engine.getGpuContext().execute("probeAddMenuItem", () -> {
                            try {
                                EnvironmentProbe probe = engine.getScene().getEnvironmentProbeManager().getProbe(new Entity("Probe_" + System.currentTimeMillis()), new Vector3f(), 50);
                                engine.getScene().getEnvironmentProbeManager().addRenderProbeCommand(probe, true);
                            } catch (Exception e1) {
                                e1.printStackTrace();
                            }

                        });
                        return new Result<>(true);
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
                CompletableFuture<Result> future = engine.getCommandQueue().addCommand(new FutureCallable<Result>() {
                    @Override
                    public Result execute() throws Exception {
                        Entity pointLightEntity = new Entity("PointLight" + System.currentTimeMillis());
                        pointLightEntity.addComponent(new PointLight(pointLightEntity, new Vector4f(1,1,1,1), 50));
                        throw new IllegalStateException("Doesnt work anymore, remove me");
//                        engine.getSceneManager().getScene().add(pointLightEntity);
//                        return new Result(true);
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
                Result<Boolean> result = engine.getGpuContext().calculate((Callable<Result<Boolean>>) () -> {
                    Entity tubeLightEntity = new Entity("TubeLight" + System.currentTimeMillis());
                    tubeLightEntity.addComponent(new TubeLight(tubeLightEntity, new Vector3f(1, 1, 1), 100, 50));
                    throw new IllegalStateException("Doesnt work anymore, remove me");
//                    engine.getSceneManager().getScene().add(tubeLightEntity);
//                    return new Result(true);
                });

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

                Result<Boolean> result = engine.getGpuContext().calculate((Callable<Result<Boolean>>) () -> {
                    Entity entity = new Entity("AreaLight" + System.currentTimeMillis());
                    entity.addComponent(new AreaLight(entity, new Vector3f(1, 1, 1), new Vector3f(50, 50, 20)));
                    throw new IllegalStateException("Doesnt work anymore, remove me");
//                    engine.getSceneManager().getScene().add(entity);
//                    return new Result(true);
                });

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
				engine.getManagers().get(ScriptManager.class).eval(console.getText());
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

            Result<Boolean> result = engine.getGpuContext().calculate((Callable<Result<Boolean>>) () -> {
                    GPUProfiler.INSTANCE.reset();
                    return new Result(true);
            });

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
        WebMenuItem loadMaterial = new WebMenuItem("Load SimpleMaterial");
        loadMaterial.addActionListener(e -> {
        	File chosenFile = WebFileChooser.showOpenDialog("./hp/assets/materials/", choser -> {
    			choser.setFileFilter(new FileNameExtensionFilter("Materials", "hpmaterial"));
    		});
    		if(chosenFile != null) {
                Result<Boolean> result = engine.getGpuContext().calculate((Callable<Result<Boolean>>) () -> {
                    engine.getScene().getMaterialManager().getMaterial(chosenFile.getName());
                    return new Result(true);
                });

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

                    TextureResult result = engine.getGpuContext().calculate((Callable<TextureResult>) () -> {
                        return new AddTextureCommand(chosenFile.getPath(), engine.getTextureManager(), engine.getConfig().getDirectories().getGameDir()).execute();
                    });

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
                    TextureResult result = engine.getGpuContext().calculate((Callable<TextureResult>) () -> {
                        return new AddTextureCommand(chosenFile.getPath(), true, engine.getTextureManager(), engine.getConfig().getDirectories().getGameDir()).execute();
                    });

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

                    TextureResult result = engine.getGpuContext().calculate((Callable<TextureResult>) () -> {
                        return new AddCubeMapCommand(chosenFile.getPath(), engine.getTextureManager(), engine.getConfig().getDirectories().getGameDir()).execute();
                    });

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
        AutoCompletion ac = new AutoCompletion(engine.getScene().getManagers().get(ScriptManager.class).getProvider());
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

        Map<String, List<JComponent>> toggleButtonsWithGroups = getButtons(config, engine);

        for(RenderTarget<?> target: engine.getGpuContext().getRegisteredRenderTargets()) {
            for(int i = 0; i < target.getTextures().size(); i++) {
                Texture<?> texture = target.getTextures().get(i);
                String name = target.getName() + " - " + i; // TODO: Revive names here
                renderTargetTextures.add(new DirectTextureOutputItem(target, name, target.getRenderedTexture(i)));
            }
        }
        WebComboBox directTextureOutputTextureIndexSelection = new WebComboBox(renderTargetTextures.toArray(new DirectTextureOutputItem[0]));
        WebTitledPanel indirectTextureOutputIndexPanel = new WebTitledPanel();
        indirectTextureOutputIndexPanel.setContent(directTextureOutputTextureIndexSelection);
        indirectTextureOutputIndexPanel.setTitle(new WebLabel("Direct texture output"));
        directTextureOutputTextureIndexSelection.addActionListener(e -> {
            int selectedIndex = directTextureOutputTextureIndexSelection.getSelectedIndex();
            DirectTextureOutputItem selected = renderTargetTextures.get(selectedIndex);
            config.getDebug().setDirectTextureOutputTextureIndex(selected.getTextureId());
        });
        toggleButtonsWithGroups.get("debug").add(indirectTextureOutputIndexPanel);

        for (Entry<String, List<JComponent>> groupAndButtons : toggleButtonsWithGroups.entrySet()) {
			Component[] toggleButtonsArray = new Component[groupAndButtons.getValue().size()];
			groupAndButtons.getValue().toArray(toggleButtonsArray);
			mainButtonElements.add(new TitledPanel(groupAndButtons.getKey(), toggleButtonsArray));
		}

        Component[] mainButtonsElementsArray = new Component[mainButtonElements.size()];
        mainButtonElements.toArray(mainButtonsElementsArray);
        GridPanel buttonGridPanel = new GridPanel(mainButtonsElementsArray.length/2, 2, 5, mainButtonsElementsArray);

        mainPane = new WebScrollPane(buttonGridPanel);
        mainPane.getVerticalScrollBar().setUnitIncrement(32);
	}

    private void initPerformanceChart() {
		if(performanceMonitor == null) {
            performanceMonitor = new PerformanceMonitor(engine);
		}
		performanceMonitor.init();
	}

    private void createPointLightsTab() {
        ListSelectionModel pointLightsCellSelectionModel = pointsLightsTable.getSelectionModel();
        pointLightsCellSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        pointLightsCellSelectionModel.addListSelectionListener(e -> {

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
                    entityViewFrame.add(new PointLightView(engine, selectedLight.getEntity()));
                    entityViewFrame.setVisible(true);
                }
            }
        });
    }

	private void createTubeLightsTab() {
		ListSelectionModel tubeLightsCellSelectionModel = tubeLightsTable.getSelectionModel();
		tubeLightsCellSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		tubeLightsCellSelectionModel.addListSelectionListener(e -> {

            int[] selectedRow = tubeLightsTable.getSelectedRows();
            int[] selectedColumns = tubeLightsTable.getSelectedColumns();

            for (int i = 0; i < selectedRow.length; i++) {
                for (int j = 0; j < selectedColumns.length; j++) {
                    TubeLight selectedLight = engine.getSceneManager().getScene().getTubeLights().get(selectedRow[i]);
                    entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                    entityViewFrame.getContentPane().removeAll();
                    entityViewFrame.pack();
                    entityViewFrame.setSize(1000, 600);
                    entityViewFrame.add(new TubeLightView(engine, selectedLight));
                    entityViewFrame.setVisible(true);
                }
            }
        });
	}

	private void createAreaLightsTab() {
		ListSelectionModel areaLightsCellSelectionModel = areaLightsTable.getSelectionModel();
		areaLightsCellSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		areaLightsCellSelectionModel.addListSelectionListener(e -> {
            int[] selectedRow = areaLightsTable.getSelectedRows();
            int[] selectedColumns = areaLightsTable.getSelectedColumns();

            for (int i = 0; i < selectedRow.length; i++) {
                for (int j = 0; j < selectedColumns.length; j++) {
                    AreaLight selectedLight = engine.getSceneManager().getScene().getAreaLights().get(selectedRow[i]);
                    entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                    entityViewFrame.getContentPane().removeAll();
                    entityViewFrame.pack();
                    entityViewFrame.setSize(1000, 600);
                    entityViewFrame.add(new AreaLightView(engine, selectedLight));
                    entityViewFrame.setVisible(true);
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
    public void handle(MeshSelectedEvent e) {
        entityViewFrame.getContentPane().removeAll();
        entityViewFrame.add(new MeshView(engine, engine.getScene().getEntities().get(e.getEntityIndex()).getComponent(ModelComponent.class).getMeshes().get(e.getMeshIndex())));
        entityViewFrame.setVisible(true);
    }

    @Subscribe
    @Handler
    public void handle(FrameFinishedEvent event) {
        if(GPUProfiler.INSTANCE.getPROFILING_ENABLED()) {
            SwingUtils.invokeLater(() -> {
				DrawResult drawResult1 = event.getDrawResult();
				String drawResult = drawResult1.toString();
				if(GPUProfiler.INSTANCE.getDUMP_AVERAGES()) {
					drawResult += GPUProfiler.INSTANCE.getCurrentAverages();
				}
				infoLeft.setText(drawResult);
				infoRight.setText(GPUProfiler.INSTANCE.getCurrentTimings());
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
