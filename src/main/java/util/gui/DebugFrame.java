package util.gui;

import com.alee.extended.checkbox.CheckState;
import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.WebButtonGroup;
import com.alee.extended.tab.WebDocumentPane;
import com.alee.extended.tree.CheckStateChange;
import com.alee.extended.tree.CheckStateChangeListener;
import com.alee.extended.tree.WebCheckBoxTree;
import com.alee.extended.tree.WebCheckBoxTreeCellRenderer;
import com.alee.laf.button.WebButton;
import com.alee.laf.button.WebToggleButton;
import com.alee.laf.filechooser.WebFileChooser;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenu;
import com.alee.laf.menu.WebMenuBar;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.slider.WebSlider;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebTextField;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import com.google.common.eventbus.Subscribe;
import component.ModelComponent;
import engine.World;
import engine.model.Entity;
import event.EntitySelectedEvent;
import event.GlobalDefineChangedEvent;
import event.MaterialChangedEvent;
import octree.Octree;
import octree.Octree.Node;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector3f;
import renderer.EnvironmentSampler;
import renderer.GBuffer;
import renderer.Result;
import renderer.command.*;
import renderer.command.AddTextureCommand.TextureResult;
import renderer.light.AreaLight;
import renderer.light.PointLight;
import renderer.light.TubeLight;
import renderer.material.Material;
import renderer.material.MaterialFactory;
import scene.EnvironmentProbe;
import scene.Scene;
import texture.TextureFactory;
import util.Adjustable;
import util.Toggable;
import util.gui.input.SliderInput;
import util.gui.input.TitledPanel;
import util.script.ScriptManager;
import util.stopwatch.GPUProfiler;

import javax.script.ScriptException;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.text.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static util.Util.vectorToString;


public class DebugFrame {

	ScriptManager scriptManager;
	private WebFrame mainFrame = new WebFrame("Main");
	private WebFrame materialViewFrame = new WebFrame("Material");
	private WebFrame entityViewFrame = new WebFrame("Entity");
	private WebFrame probeViewFrame = new WebFrame("Probe");
	private WebTabbedPane tabbedPane;
	
	private JScrollPane materialPane = new JScrollPane();
	private JScrollPane texturePane = new JScrollPane();
	private JScrollPane mainLightPane = new JScrollPane();
	private JScrollPane pointLightsPane = new JScrollPane();
	private JScrollPane tubeLightsPane = new JScrollPane();
	private JScrollPane areaLightsPane = new JScrollPane();
	private JScrollPane scenePane = new JScrollPane();
	private JScrollPane probesPane = new JScrollPane();
	private JTextPane output = new JTextPane();
	private JScrollPane outputPane = new JScrollPane(output);
	
	private WebDocumentPane<ScriptDocumentData> scriptsPane = new WebDocumentPane<>();
	private WebScrollPane mainPane;
	private RSyntaxTextArea console = new RSyntaxTextArea(
	"temp = Java.type('org.lwjgl.util.vector.Vector3f');" +
	"for each(var probe in renderer.getEnvironmentProbeFactory().getProbes()) {" +
	"	probe.move(new temp(0,-10,0));" +
	"}");
	private RTextScrollPane consolePane = new RTextScrollPane(console);

//	private WebToggleButton toggleFileReload = new WebToggleButton("Hot Reload", FileMonitor.getInstance().running);
	private WebToggleButton toggleProfiler = new WebToggleButton("Profiling", GPUProfiler.PROFILING_ENABLED);
	private WebToggleButton toggleProfilerPrint = new WebToggleButton("Print Profiling", GPUProfiler.PRINTING_ENABLED);
	private WebButton dumpAverages = new WebButton("Dump Averages");
	private WebToggleButton toggleParallax = new WebToggleButton("Parallax", World.useParallax);
	private WebToggleButton toggleSteepParallax = new WebToggleButton("Steep Parallax", World.useSteepParallax);
	private WebToggleButton toggleAmbientOcclusion = new WebToggleButton("Ambient Occlusion", World.useAmbientOcclusion);
	private WebToggleButton toggleFrustumCulling = new WebToggleButton("Frustum Culling", World.useFrustumCulling);
	private WebToggleButton toggleInstantRadiosity = new WebToggleButton("Instant Radiosity", World.useInstantRadiosity);
	private WebToggleButton toggleDrawLines = new WebToggleButton("Draw Lines", World.DRAWLINES_ENABLED);
	private WebToggleButton toggleDrawScene = new WebToggleButton("Draw Scene", World.DRAWSCENE_ENABLED);
	private WebToggleButton toggleDrawOctree = new WebToggleButton("Draw Octree", Octree.DRAW_LINES);
	private WebToggleButton toggleDrawProbes = new WebToggleButton("Draw Probes", World.DRAW_PROBES);
	private WebButton forceProbeGBufferRedraw = new WebButton("Redraw Probe GBuffers");
	private WebToggleButton toggleUseGI = new WebToggleButton("GI", World.USE_GI);
	private WebToggleButton toggleUseSSR = new WebToggleButton("SSR", World.useSSR);
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
	private WebToggleButton toggleDebugDrawProbes = new WebToggleButton("Debug Draw Probes", World.DEBUGDRAW_PROBES);
	private WebToggleButton toggleDebugDrawProbesWithContent = new WebToggleButton("Debug Draw Probes Content", World.DEBUGDRAW_PROBES_WITH_CONTENT);
	private WebToggleButton toggleDebugFrame = new WebToggleButton("Debug Frame", World.DEBUGFRAME_ENABLED);
	private WebToggleButton toggleDrawLights = new WebToggleButton("Draw Lights", World.DRAWLIGHTS_ENABLED);
	private WebToggleButton toggleVSync = new WebToggleButton("VSync", World.VSYNC_ENABLED);
	private WebToggleButton toggleAutoExposure = new WebToggleButton("Auto Exposure", World.AUTO_EXPOSURE_ENABLED);

	private WebToggleButton toggleProbeDrawCountOne = new WebToggleButton("1", RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL == 1);
	private WebToggleButton toggleProbeDrawCountTwo = new WebToggleButton("2", RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL == 2);
	private WebToggleButton toggleProbeDrawCountThree = new WebToggleButton("3", RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL == 3);
	private WebToggleButton toggleProbeDrawCountFour = new WebToggleButton("4", RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL == 4);
	private WebButtonGroup probeDrawCountGroup = new WebButtonGroup(true, toggleProbeDrawCountOne, toggleProbeDrawCountTwo, toggleProbeDrawCountThree, toggleProbeDrawCountFour);
	
	WebSlider ambientOcclusionRadiusSlider = new WebSlider ( WebSlider.HORIZONTAL );
	WebSlider ambientOcclusionTotalStrengthSlider = new WebSlider ( WebSlider.HORIZONTAL );

	private WebCheckBoxTree<DefaultMutableTreeNode> scene = new WebCheckBoxTree<DefaultMutableTreeNode>();
	private WebTextField sceneViewFilterField = new WebTextField(15);
	private WebCheckBoxTree<DefaultMutableTreeNode> probes = new WebCheckBoxTree<DefaultMutableTreeNode>();
	private WebFileChooser fileChooser;
	private WebFrame addEntityFrame;
	private World world;
	private PerformanceMonitor performanceMonitor;
	
	public DebugFrame(World world) {
		World.getEventBus().register(this);
		init(world);
	}

	private void init(World world) {
		this.world = world;
	    List<Component> mainButtonElements = new ArrayList<>();
		tabbedPane = new WebTabbedPane();
		fileChooser = new WebFileChooser(new File(getClass().getResource("").getPath()));
		
		scriptManager = new ScriptManager(world);
		MaterialFactory materialFactory = world.getRenderer().getMaterialFactory();
		TextureFactory textureFactory = world.getRenderer().getTextureFactory();
		
		mainFrame.getContentPane().removeAll();
		mainFrame.setLayout(new BorderLayout(5,5));

		try {
			console.setText(FileUtils.readFileToString(new File("hp/assets/scripts/console.js")));
		} catch (IOException e2) {
			e2.printStackTrace();
		}
		console.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
		console.setCodeFoldingEnabled(true);
		AutoCompletion ac = new AutoCompletion(scriptManager.getProvider());
		ac.install(console);
		
		createMainLightsTab();
		createPointLightsTab();
		createTubeLightsTab();
		createAreaLightsTab();

		createMaterialPane(world);

		sceneViewFilterField.addActionListener(e -> {
			TreeModel model = scene.getModel();
			scene.setModel(null);
			scene.setModel(model);
		});
		addOctreeSceneObjects(world);
		
		addProbes(world);

		createInputs();
		initActionListeners(world, mainButtonElements);
        
		WebMenuBar menuBar = new WebMenuBar ();
		WebMenu menuScene = new WebMenu("Scene");
        menuBar.setUndecorated ( true );
        {
	        WebMenuItem sceneSaveMenuItem = new WebMenuItem ( "Save" );
	        sceneSaveMenuItem.addActionListener(e -> {
	        	String initialSelectionValue = world.getScene().getName() != "" ? world.getScene().getName() : "default";
				Object selection = WebOptionPane.showInputDialog( mainFrame, "Save scene as", "Save scene", WebOptionPane.QUESTION_MESSAGE, null, null, initialSelectionValue );
	        	if(selection != null) {
	        		boolean success = world.getScene().write(selection.toString());
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
        	WebMenuItem sceneLoadMenuItem = new WebMenuItem ( "Load" );
        	sceneLoadMenuItem.addActionListener(e -> {

	    		File chosenFile = WebFileChooser.showOpenDialog(".\\hp\\assets\\scenes\\", choser -> {
	    			choser.setFileFilter(new FileNameExtensionFilter("Scenes", "hpscene"));
	    		});
	    		if(chosenFile != null) {
	    			String sceneName = FilenameUtils.getBaseName(chosenFile.getAbsolutePath());
	    			Scene newScene = Scene.read(world.getRenderer(), sceneName);

	    			SynchronousQueue<Result> queue = world.getRenderer().addCommand(new Command<Result>() {
						@Override public Result execute(World world) {
							newScene.init(world);
			    			world.setScene(newScene);
			    			init(world);
			    			return new Result();
						}}
	    			);
	        		
	        		Result result = null;
					try {
						result = queue.poll(5, TimeUnit.MINUTES);
					} catch (Exception e1) {
						showError("Failed to load scene");
					}
					
					if (!result.isSuccessful()) {
						showError("Failed to load scene");
					} else {
						showSuccess("Scene loaded");
					}
	    		}
        	});

	        menuScene.add(sceneLoadMenuItem);
        }
        {
        	WebMenuItem sceneNewMenuItem = new WebMenuItem ( "New" );
        	sceneNewMenuItem.addActionListener(e -> {
	    			Scene newScene = new Scene();
	    			world.setScene(newScene);
	    			init(world);
        	});

	        menuScene.add(sceneNewMenuItem);
        }
		WebMenu menuEntity = new WebMenu("Entity");
        {
        	WebMenuItem entitiyAddMenuItem = new WebMenuItem ( "Add new" );
        	entitiyAddMenuItem.addActionListener(e -> {
        		
	    		addEntityFrame = new WebFrame("Add Entity");
	    		addEntityFrame.setSize(600, 300);
	    		addEntityFrame.add(new AddEntitiyView(world, addEntityFrame, this));
	    		addEntityFrame.setVisible(true);
	    		
        	});

        	menuEntity.add(entitiyAddMenuItem);
        }
        {
        	WebMenuItem entitiyLoadMenuItem = new WebMenuItem ( "Load existing" );
        	entitiyLoadMenuItem.addActionListener(e -> {
        		
        		world.getScene().addAll(LoadEntitiyView.showDialog(world));
        		refreshSceneTree();
        	});

        	menuEntity.add(entitiyLoadMenuItem);
        }
		WebMenu menuProbe = new WebMenu("Probe");
        {
        	WebMenuItem probeAddMenuItem = new WebMenuItem ( "Add" );
        	probeAddMenuItem.addActionListener(e -> {
        		SynchronousQueue<Result> queue = world.getRenderer().addCommand(new Command<Result>() {
					@Override
					public Result execute(World world) {
						world.getRenderer().getEnvironmentProbeFactory().getProbe(new Vector3f(), 50).draw(world.getScene().getOctree());
						return new Result() { @Override public boolean isSuccessful() { return true; } };
					}});
        		
        		Result result = null;
				try {
					result = queue.poll(5, TimeUnit.MINUTES);
				} catch (Exception e1) {
					showError("Failed to add probe");
				}
				
				if (!result.isSuccessful()) {
					showError("Failed to add probe");
				} else {
					showSuccess("Added probe");
	        		refreshProbeTab();
				}
        	});

        	menuProbe.add(probeAddMenuItem);
        }
        
		WebMenu menuLight = new WebMenu("Light");
        {
        	WebMenuItem lightAddMenuItem = new WebMenuItem ( "Add PointLight" );
        	lightAddMenuItem.addActionListener(e -> {
        		SynchronousQueue<Result> queue = world.getRenderer().addCommand(new Command<Result>() {
					@Override
					public Result execute(World world) {
						world.getRenderer().getLightFactory().getPointLight(50);
						return new Result() { @Override public boolean isSuccessful() { return true; } };
					}});
        		
        		Result result = null;
				try {
					result = queue.poll(5, TimeUnit.MINUTES);
				} catch (Exception e1) {
					showError("Failed to add light");
				}
				
				if (!result.isSuccessful()) {
					showError("Failed to add light");
				} else {
					showSuccess("Added light");
	        		refreshPointLightsTab();
				}
        		
        	});

        	menuLight.add(lightAddMenuItem);
        }
        {
        	WebMenuItem lightAddMenuItem = new WebMenuItem ( "Add TubeLight" );
        	lightAddMenuItem.addActionListener(e -> {
        		SynchronousQueue<Result> queue = world.getRenderer().addCommand(new Command<Result>() {
					@Override
					public Result execute(World world) {
						world.getRenderer().getLightFactory().getTubeLight();
						return new Result() { @Override public boolean isSuccessful() { return true; } };
					}});
        		
        		Result result = null;
				try {
					result = queue.poll(5, TimeUnit.MINUTES);
				} catch (Exception e1) {
					showError("Failed to add light");
				}
				
				if (!result.isSuccessful()) {
					showError("Failed to add light");
				} else {
					showSuccess("Added light");
	        		refreshTubeLightsTab();
				}
        		
        	});

        	menuLight.add(lightAddMenuItem);
        }
        {
        	WebMenuItem lightAddMenuItem = new WebMenuItem ( "Add AreaLight" );
        	lightAddMenuItem.addActionListener(e -> {
        		SynchronousQueue<Result> queue = world.getRenderer().addCommand(new Command<Result>() {
					@Override
					public Result execute(World world) {
						world.getRenderer().getLightFactory().getAreaLight(50,50,20);
						return new Result() { @Override public boolean isSuccessful() { return true; } };
					}});
        		
        		Result result = null;
				try {
					result = queue.poll(5, TimeUnit.MINUTES);
				} catch (Exception e1) {
					showError("Failed to add light");
				}
				
				if (!result.isSuccessful()) {
					showError("Failed to add light");
				} else {
					showSuccess("Added light");
	        		refreshAreaLightsTab();
				}
        		
        	});

        	menuLight.add(lightAddMenuItem);
        }

        WebMenuItem runScriptMenuItem = new WebMenuItem("Run Script");
        runScriptMenuItem.addActionListener(e -> {
			try {
				scriptManager.eval(console.getText());
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
    		SynchronousQueue<Result> queue = world.getRenderer().addCommand(new Command<Result>() {
				@Override
				public Result execute(World world) {
					GPUProfiler.reset();
					return new Result();
				}});
    		
    		Result result = null;
			try {
				result = queue.poll(5, TimeUnit.MINUTES);
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
    		init(world);
    	});
        WebMenuItem loadMaterial = new WebMenuItem("Load Material");
        loadMaterial.addActionListener(e -> {
        	File chosenFile = WebFileChooser.showOpenDialog(".\\hp\\assets\\materials\\", choser -> {
    			choser.setFileFilter(new FileNameExtensionFilter("Materials", "hpmaterial"));
    		});
    		if(chosenFile != null) {
				SynchronousQueue<Result> queue = world.getRenderer().addCommand(new Command<Result>() {

					@Override
					public Result execute(World world) {
						System.out.println(chosenFile.getName());
						world.getRenderer().getMaterialFactory().get(chosenFile.getName());
						return new Result();
					}
				});
				Result result = null;
				try {
					result = queue.poll(5, TimeUnit.SECONDS);
				} catch (Exception e1) {
					showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
				}
				
				if (!result.isSuccessful()) {
					showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
				} else {
					showSuccess("Added " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					refreshTextureTab();
				}
    		}
    	});

		WebMenu menuTextures = new WebMenu("Texture");
        {
        	WebMenuItem textureAddMenuItem = new WebMenuItem ( "Add 2D" );
        	textureAddMenuItem.addActionListener(e -> {
        		
	    		File chosenFile = fileChooser.showOpenDialog();
	    		if(chosenFile != null) {
					SynchronousQueue<TextureResult> queue = world.getRenderer().addCommand(new AddTextureCommand(chosenFile.getPath()));
					
					TextureResult result = null;
					try {
						result = queue.poll(5, TimeUnit.MINUTES);
					} catch (Exception e1) {
						showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					}
					
					if (!result.isSuccessful()) {
						showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					} else {
						showSuccess("Added " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
						refreshTextureTab();
					}
	    		}
        	});
        	menuTextures.add(textureAddMenuItem);
        }
        {
        	WebMenuItem textureSrgbaAddMenuItem = new WebMenuItem ( "Add 2D SRGBA" );
        	textureSrgbaAddMenuItem.addActionListener(e -> {
        		
	    		File chosenFile = fileChooser.showOpenDialog();
	    		if(chosenFile != null) {
					SynchronousQueue<TextureResult> queue = world.getRenderer().addCommand(new AddTextureCommand(chosenFile.getPath(), true));
					
					TextureResult result = null;
					try {
						result = queue.poll(5, TimeUnit.MINUTES);
					} catch (Exception e1) {
						showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					}
					
					if (!result.isSuccessful()) {
						showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					} else {
						showSuccess("Added " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
						refreshTextureTab();
					}
	    			
	    		}
	    		
        	});
        	menuTextures.add(textureSrgbaAddMenuItem);
        }
        {
        	WebMenuItem textureAddMenuItem = new WebMenuItem ( "Add Cube" );
    		textureAddMenuItem.addActionListener(e -> {
        		
	    		File chosenFile = fileChooser.showOpenDialog();
	    		if(chosenFile != null) {
					SynchronousQueue<TextureResult> queue = world.getRenderer().addCommand(new AddCubeMapCommand(chosenFile.getPath()));
					
					TextureResult result = null;
					try {
						result = queue.poll(5, TimeUnit.MINUTES);
					} catch (Exception e1) {
						showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					}
					
					if (!result.isSuccessful()) {
						showError("Failed to add " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
					} else {
						showSuccess("Added " + FilenameUtils.getBaseName(chosenFile.getAbsolutePath()));
						refreshTextureTab();
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
        menuBar.add(sceneViewFilterField);
        mainFrame.setJMenuBar(menuBar);
        
		mainFrame.add(tabbedPane);
		
		tabbedPane.addTab("Main", mainPane);
		tabbedPane.addTab("Scene", scenePane);
		tabbedPane.addTab("Probes", probesPane);
		createTexturePane(textureFactory);
		tabbedPane.addTab("Texture", texturePane);
		tabbedPane.addTab("Material", materialPane);
		tabbedPane.addTab("Main light", mainLightPane);
		tabbedPane.addTab("PointLights", pointLightsPane);
		tabbedPane.addTab("TubeLights", tubeLightsPane);
		tabbedPane.addTab("AreaLights", areaLightsPane);
		tabbedPane.addTab("Console", consolePane);
		tabbedPane.addTab("Scripts", scriptsPane);
		tabbedPane.addTab("Output", outputPane);
		
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainFrame.setSize(new Dimension(1200, 720));
		mainFrame.setVisible(true);
		initPerformanceChart();
//		redirectSystemStreams();
	}

	private void createInputs() {
		toggleProfiler = new WebToggleButton("Profiling", GPUProfiler.PROFILING_ENABLED);
		toggleProfilerPrint = new WebToggleButton("Print Profiling", GPUProfiler.PRINTING_ENABLED);
		dumpAverages = new WebButton("Dump Averages");
		toggleParallax = new WebToggleButton("Parallax", World.useParallax);
		toggleAmbientOcclusion = new WebToggleButton("Ambient Occlusion", World.useAmbientOcclusion);
		toggleFrustumCulling = new WebToggleButton("Frustum Culling", World.useFrustumCulling);
		toggleInstantRadiosity = new WebToggleButton("Instant Radiosity", World.useInstantRadiosity);
		toggleDrawLines = new WebToggleButton("Draw Lines", World.DRAWLINES_ENABLED);
		toggleDrawScene = new WebToggleButton("Draw Scene", World.DRAWSCENE_ENABLED);
		toggleDrawOctree = new WebToggleButton("Draw Octree", Octree.DRAW_LINES);
		toggleDrawProbes = new WebToggleButton("Draw Probes", World.DRAW_PROBES);
		forceProbeGBufferRedraw = new WebButton("Redraw Probe GBuffers");
		toggleUseGI = new WebToggleButton("GI", World.USE_GI);
		toggleUseSSR = new WebToggleButton("SSR", World.useSSR);
		toggleUseComputeShaderForReflections = new WebToggleButton("Computeshader reflections", GBuffer.USE_COMPUTESHADER_FOR_REFLECTIONS);
		toggleUseFirstBounceForProbeRendering = new WebToggleButton("First bounce for probes", GBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE);
		toggleUseSecondBounceForProbeRendering = new WebToggleButton("Second bounce for probes", GBuffer.RENDER_PROBES_WITH_SECOND_BOUNCE);
		toggleSampleCount2 = new WebToggleButton("2", GBuffer.IMPORTANCE_SAMPLE_COUNT == 2);
		toggleSampleCount4 = new WebToggleButton("4", GBuffer.IMPORTANCE_SAMPLE_COUNT == 4);
		toggleSampleCount8 = new WebToggleButton("8", GBuffer.IMPORTANCE_SAMPLE_COUNT == 8);
		toggleSampleCount16 = new WebToggleButton("16", GBuffer.IMPORTANCE_SAMPLE_COUNT == 16);
		toggleSampleCount32 = new WebToggleButton("32", GBuffer.IMPORTANCE_SAMPLE_COUNT == 32);
		toggleSampleCount64 = new WebToggleButton("64", GBuffer.IMPORTANCE_SAMPLE_COUNT == 64);
		sampleCountGroup = new WebButtonGroup(true, toggleSampleCount2, toggleSampleCount4, toggleSampleCount8, toggleSampleCount16, toggleSampleCount32, toggleSampleCount64);
		toggleUseDeferredRenderingForProbes = new WebToggleButton("Deferred Rendering Probes", EnvironmentSampler.deferredRenderingForProbes);
		toggleDebugDrawProbes = new WebToggleButton("Debug Draw Probes", World.DEBUGDRAW_PROBES);
		toggleDebugDrawProbesWithContent = new WebToggleButton("Debug Draw Probes Content", World.DEBUGDRAW_PROBES_WITH_CONTENT);
		toggleDebugFrame = new WebToggleButton("Debug Frame", World.DEBUGFRAME_ENABLED);
		toggleDrawLights = new WebToggleButton("Draw Lights", World.DRAWLIGHTS_ENABLED);
		toggleVSync = new WebToggleButton("VSync", World.VSYNC_ENABLED);
		toggleAutoExposure = new WebToggleButton("Auto Exposure", World.AUTO_EXPOSURE_ENABLED);
		toggleProbeDrawCountOne = new WebToggleButton("1", RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL == 1);
		toggleProbeDrawCountTwo = new WebToggleButton("2", RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL == 2);
		toggleProbeDrawCountThree = new WebToggleButton("3", RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL == 3);
		toggleProbeDrawCountFour = new WebToggleButton("4", RenderProbeCommandQueue.MAX_PROBES_RENDERED_PER_DRAW_CALL == 4);
		probeDrawCountGroup = new WebButtonGroup(true, toggleProbeDrawCountOne, toggleProbeDrawCountTwo, toggleProbeDrawCountThree, toggleProbeDrawCountFour);
		ambientOcclusionRadiusSlider = new WebSlider ( WebSlider.HORIZONTAL );
		ambientOcclusionTotalStrengthSlider = new WebSlider ( WebSlider.HORIZONTAL );
		WebCheckBoxTree<DefaultMutableTreeNode> scene = new WebCheckBoxTree<DefaultMutableTreeNode>();
		WebTextField sceneViewFilterField = new WebTextField(15);
		WebCheckBoxTree<DefaultMutableTreeNode> probes = new WebCheckBoxTree<DefaultMutableTreeNode>();
	}
	private void initActionListeners(World world,
	List<Component> mainButtonElements) {
//		toggleFileReload.addActionListener( e -> {
//			FileMonitor.getInstance().running = !FileMonitor.getInstance().running;
//			toggleFileReload.setSelected(FileMonitor.getInstance().running);
//		});
		toggleProfiler.addActionListener( e -> {
			
			SynchronousQueue<Result> queue = world.getRenderer().addCommand(new Command<Result>(){

				@Override
				public Result execute(World world) {
					GPUProfiler.PROFILING_ENABLED = !GPUProfiler.PROFILING_ENABLED;
					return new Result() { @Override public boolean isSuccessful() { return true; } };
				}
			});
			Result result = null;
			try {
				result = queue.poll(5, TimeUnit.MINUTES);
				if (!result.isSuccessful()) {
					showError("Profiling can't be switched");
				} else {
					showSuccess("Profiling switched");
				}
			} catch (Exception e1) {
				showError("Profiling can't be switched");
			}
			
		});
		toggleProfilerPrint.addActionListener( e -> {
			
			SynchronousQueue<Result> queue = world.getRenderer().addCommand(new Command<Result>(){

				@Override
				public Result execute(World world) {
					GPUProfiler.PRINTING_ENABLED = !GPUProfiler.PRINTING_ENABLED;
					return new Result();
				}
			});
			Result result = null;
			try {
				result = queue.poll(5, TimeUnit.MINUTES);
				if (!result.isSuccessful()) {
					showError("Printing can't be switched");
				} else {
					showSuccess("Printing switched");
				}
			} catch (Exception e1) {
				showError("Profiling can't be switched");
			}
			
		});
		//////////////////////////////
		Map<String, List<Component>> toggleButtonsWithGroups = new HashMap<>();
		for (Field field : World.class.getDeclaredFields()) {
			for (Annotation annotation : field.getDeclaredAnnotations()) {
				if(annotation instanceof Toggable) {
					createWebToggableButton(world, toggleButtonsWithGroups, field, annotation);
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
			world.getRenderer().addCommand(new DumpAveragesCommand(1000));
		});
		
		toggleParallax.addActionListener( e -> {
			World.useParallax = !World.useParallax;
			World.useSteepParallax = false;
		});
		
		toggleSteepParallax.addActionListener(e -> {
			World.useSteepParallax = !World.useSteepParallax;
			World.useParallax = false;
		});

		toggleAmbientOcclusion.addActionListener(e -> {
			World.useAmbientOcclusion = !World.useAmbientOcclusion;
			world.getEventBus().post(new GlobalDefineChangedEvent());
		});

		toggleFrustumCulling.addActionListener(e -> {
			World.useFrustumCulling = !World.useFrustumCulling;
		});
		toggleInstantRadiosity.addActionListener(e -> {
			World.useInstantRadiosity = !World.useInstantRadiosity;
		});

		toggleDrawLines.addActionListener(e -> {
			World.DRAWLINES_ENABLED = !World.DRAWLINES_ENABLED;
		});
		toggleDrawScene.addActionListener(e -> {
			World.DRAWSCENE_ENABLED = !World.DRAWSCENE_ENABLED;
		});

		toggleDrawOctree.addActionListener(e -> {
			Octree.DRAW_LINES = !Octree.DRAW_LINES;
		});
		forceProbeGBufferRedraw.addActionListener(e -> {
			world.getRenderer().getEnvironmentProbeFactory().getProbes().forEach(probe -> {
				probe.getSampler().resetDrawing();
			});
		});
		toggleDrawProbes.addActionListener(e -> {
			World.DRAW_PROBES = !World.DRAW_PROBES;
		});
		toggleUseGI.addActionListener(e -> {
			World.USE_GI = !World.USE_GI;
			System.out.println("XXXXXXXXXX");
		});
		toggleUseSSR.addActionListener(e -> {
			World.useSSR = !World.useSSR;
		});
		toggleUseDeferredRenderingForProbes.addActionListener(e -> {
			EnvironmentSampler.deferredRenderingForProbes = !EnvironmentSampler.deferredRenderingForProbes;
		});
		toggleUseFirstBounceForProbeRendering.addActionListener(e -> {
			GBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE = !GBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE;
			World.getEventBus().post(new MaterialChangedEvent()); // TODO: Create custom event class...should redraw probes
			World.getEventBus().post(new MaterialChangedEvent());
			World.getEventBus().post(new MaterialChangedEvent());
			World.getEventBus().post(new MaterialChangedEvent());
			World.getEventBus().post(new MaterialChangedEvent());
		});
		toggleUseSecondBounceForProbeRendering.addActionListener(e -> {
			GBuffer.RENDER_PROBES_WITH_SECOND_BOUNCE = !GBuffer.RENDER_PROBES_WITH_SECOND_BOUNCE;
			World.getEventBus().post(new MaterialChangedEvent()); // TODO: Create custom event class...should redraw probes
			World.getEventBus().post(new MaterialChangedEvent());
			World.getEventBus().post(new MaterialChangedEvent());
			World.getEventBus().post(new MaterialChangedEvent());
			World.getEventBus().post(new MaterialChangedEvent());
		});
		toggleUseComputeShaderForReflections.addActionListener(e -> {
			GBuffer.USE_COMPUTESHADER_FOR_REFLECTIONS = !GBuffer.USE_COMPUTESHADER_FOR_REFLECTIONS;
		});
		toggleDebugDrawProbes.addActionListener(e -> {
			World.DEBUGDRAW_PROBES = !World.DEBUGDRAW_PROBES;
		});
		toggleDebugDrawProbesWithContent.addActionListener(e -> {
			World.DEBUGDRAW_PROBES_WITH_CONTENT = !World.DEBUGDRAW_PROBES_WITH_CONTENT;
		});

		toggleDebugFrame.addActionListener(e -> {
			World.DEBUGFRAME_ENABLED = !World.DEBUGFRAME_ENABLED;
		});

		toggleDrawLights.addActionListener(e -> {
			World.DRAWLIGHTS_ENABLED = !World.DRAWLIGHTS_ENABLED;
		});
		toggleVSync.addActionListener(e -> {
			World.VSYNC_ENABLED = !World.VSYNC_ENABLED;
			world.getRenderer().addCommand(new Command<Result>() {

				@Override
				public Result execute(World world) {
					Display.setVSyncEnabled(World.VSYNC_ENABLED);
					return new Result();
				}
			});
		});
		toggleAutoExposure.addActionListener(e -> {
			World.AUTO_EXPOSURE_ENABLED = !World.AUTO_EXPOSURE_ENABLED;
			if(!World.AUTO_EXPOSURE_ENABLED) { World.EXPOSURE = 5; }
		});

	    ambientOcclusionRadiusSlider.setMinimum ( 0 );
	    ambientOcclusionRadiusSlider.setMaximum ( 1000 );
	    ambientOcclusionRadiusSlider.setMinorTickSpacing ( 250 );
	    ambientOcclusionRadiusSlider.setMajorTickSpacing ( 500 );
	    ambientOcclusionRadiusSlider.setValue((int) (World.AMBIENTOCCLUSION_RADIUS * 10000f));
	    ambientOcclusionRadiusSlider.setPaintTicks ( true );
	    ambientOcclusionRadiusSlider.setPaintLabels ( true );
	    ambientOcclusionRadiusSlider.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				WebSlider slider = (WebSlider) e.getSource();
				int value = slider.getValue();
				float valueAsFactor = ((float) value) / 10000;
				World.AMBIENTOCCLUSION_RADIUS = valueAsFactor;
			}
		});

	    ambientOcclusionTotalStrengthSlider.setMinimum ( 0 );
	    ambientOcclusionTotalStrengthSlider.setMaximum ( 200 );
	    ambientOcclusionTotalStrengthSlider.setMinorTickSpacing ( 20 );
	    ambientOcclusionTotalStrengthSlider.setMajorTickSpacing ( 50 );
	    ambientOcclusionTotalStrengthSlider.setValue((int) (World.AMBIENTOCCLUSION_TOTAL_STRENGTH * 100f));
	    ambientOcclusionTotalStrengthSlider.setPaintTicks ( true );
	    ambientOcclusionTotalStrengthSlider.setPaintLabels ( true );
	    ambientOcclusionTotalStrengthSlider.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				WebSlider slider = (WebSlider) e.getSource();
				int value = slider.getValue();
				float valueAsFactor = ((float) value) / 100;
				World.AMBIENTOCCLUSION_TOTAL_STRENGTH = valueAsFactor;
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
		mainButtonElements.add(new TitledPanel("Probes", forceProbeGBufferRedraw, toggleUseComputeShaderForReflections, toggleDrawProbes, probeDrawCountGroup, toggleDebugDrawProbes, toggleDebugDrawProbesWithContent));
		mainButtonElements.add(new TitledPanel("Profiling", toggleProfiler, toggleProfilerPrint, dumpAverages));
		mainButtonElements.add(new TitledPanel("Qualitiy settings", sampleCountGroup, toggleUseGI, toggleUseSSR, toggleUseDeferredRenderingForProbes, toggleUseFirstBounceForProbeRendering, toggleUseSecondBounceForProbeRendering, toggleAmbientOcclusion, toggleFrustumCulling, toggleAutoExposure, toggleVSync,
			new SliderInput("Exposure", WebSlider.HORIZONTAL, 1, 40, (int) World.EXPOSURE) {
			@Override public void onValueChange(int value, int delta) {
				World.EXPOSURE = value;
			}},
			new SliderInput("Scattering", WebSlider.HORIZONTAL, 0, 8, (int) world.getRenderer().getLightFactory().getDirectionalLight().getScatterFactor()) {
				@Override public void onValueChange(int value, int delta) {
					world.getRenderer().getLightFactory().getDirectionalLight().setScatterFactor((float)value);
				}
			},
			new SliderInput("Rainy", WebSlider.HORIZONTAL, 0, 100, (int) (100*World.RAINEFFECT)) {
				@Override public void onValueChange(int value, int delta) {
					World.RAINEFFECT = (float) value/100;
					World.getEventBus().post(new GlobalDefineChangedEvent());
				}
			},
			new SliderInput("Camera Speed", WebSlider.HORIZONTAL, 0, 100, (int) (100*World.CAMERA_SPEED)) {
				@Override public void onValueChange(int value, int delta) {
					World.CAMERA_SPEED = (float) value/100;
				}
			}
		));
//		mainButtonElements.add(toggleFileReload);
//		mainButtonElements.add(toggleParallax);
//		mainButtonElements.add(toggleSteepParallax);
//		mainButtonElements.add(toggleInstantRadiosity);
        Component[] mainButtonsElementsArray = new Component[mainButtonElements.size()];
        mainButtonElements.toArray(mainButtonsElementsArray);
        GridPanel buttonGridPanel = new GridPanel(mainButtonsElementsArray.length/2, 2, 5, mainButtonsElementsArray);
////////////////
	    
        mainPane = new WebScrollPane(buttonGridPanel);
	}

	private void createWebSlider(World world,
			Map<String, List<Component>> toggleButtonsWithGroups, Field field,
			Annotation annotation) {
		try {
			float f = field.getFloat(world);
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
			slider.setValue((int) (World.AMBIENTOCCLUSION_RADIUS * adjustable.factor()));
			slider.setPaintTicks ( true );
			slider.setPaintLabels ( true );
			slider.addChangeListener(new ChangeListener() {
				
				@Override
				public void stateChanged(ChangeEvent e) {
					try {
						float currentValue = field.getFloat(world);
					} catch (IllegalArgumentException | IllegalAccessException e2) {
						e2.printStackTrace();
					}
					
					WebSlider slider = (WebSlider) e.getSource();
					int value = slider.getValue();
					float valueAsFactor = ((float) value) / adjustable.factor();
					try {
						field.setFloat(world, valueAsFactor);
						World.getEventBus().post(new GlobalDefineChangedEvent());
					} catch (IllegalArgumentException | IllegalAccessException e1) {
						e1.printStackTrace();
					}
				}
			});
			groupList.add(new WebLabel(field.getName()));
			groupList.add(slider);
			
		} catch (IllegalArgumentException e1) {
			e1.printStackTrace();
		} catch (IllegalAccessException e1) {
			e1.printStackTrace();
		}
	}

	private void createWebToggableButton(World world,
			Map<String, List<Component>> toggleButtonsWithGroups,
			Field field, Annotation annotation) {
		try {
			boolean b = field.getBoolean(world);
			Toggable toggable = (Toggable) annotation;
			List<Component> groupList;
			if(toggleButtonsWithGroups.containsKey(toggable.group())) {
				groupList = toggleButtonsWithGroups.get(toggable.group());
			} else {
				groupList = new ArrayList<Component>();
				toggleButtonsWithGroups.put(toggable.group(), groupList);
			}
			
			WebToggleButton button = new WebToggleButton(field.getName(), b, e -> {
				boolean currentValue;
				try {
					currentValue = field.getBoolean(world);
					field.setBoolean(world, !currentValue);
					World.getEventBus().post(new GlobalDefineChangedEvent());
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			});
			groupList.add(button);
			
		} catch (IllegalArgumentException e1) {
			e1.printStackTrace();
		} catch (IllegalAccessException e1) {
			e1.printStackTrace();
		}
	}

	private void initPerformanceChart() {
		if(performanceMonitor == null) {
			performanceMonitor = new PerformanceMonitor(world.getRenderer());
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

	private void createMainLightsTab() {
		DebugFrame debugFrame = this;
		mainLightPane = new JScrollPane(new MainLightView(world, debugFrame));
	}
	
	private void createPointLightsTab() {
		DebugFrame debugFrame = this;
		TableModel pointLightsTableModel = new AbstractTableModel() {

			List<PointLight> lights = world.getRenderer().getLightFactory().getPointLights();

			public int getColumnCount() {
				return 3;
			}

			public int getRowCount() {
				return lights.size();
			}

			public Object getValueAt(int row, int col) {
				if (col == 0) {
					PointLight light = lights.get(row);
					return String.format("%s (Range %f)", light.getName(), light.getScale().x);
					
				} else if (col == 1) {
					return vectorToString(lights.get(row).getPosition());
					
				} else if (col == 2) {
					return vectorToString(lights.get(row).getColor());
					
				}
				return "";
			}

			public String getColumnName(int column) {
				if (column == 0) {
					return "Name";
				} else if (column == 1) {
					return "Position";
				} else if (column == 2) {
					return "Color";
				}
				return "Null";
			}
		};

		JTable pointsLightsTable = new JTable(pointLightsTableModel);
		
		pointLightsPane  =  new JScrollPane(pointsLightsTable);
		ListSelectionModel pointLightsCellSelectionModel = pointsLightsTable.getSelectionModel();
	    pointLightsCellSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		pointLightsCellSelectionModel.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {

				int[] selectedRow = pointsLightsTable.getSelectedRows();
				int[] selectedColumns = pointsLightsTable
						.getSelectedColumns();

				for (int i = 0; i < selectedRow.length; i++) {
					for (int j = 0; j < selectedColumns.length; j++) {
						PointLight selectedLight = world.getRenderer().getLightFactory().getPointLights().get(selectedRow[i]);
						entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
						entityViewFrame.getContentPane().removeAll();
						entityViewFrame.pack();
						entityViewFrame.setSize(1000, 600);
						entityViewFrame.add(new PointLightView(world, debugFrame, (PointLight) selectedLight));
						entityViewFrame.setVisible(true);
					}
				}
			}
		});
	}

	private void createTubeLightsTab() {
		DebugFrame debugFrame = this;
		TableModel tubeLightsTableModel = new AbstractTableModel() {

			List<TubeLight> lights = world.getRenderer().getLightFactory().getTubeLights();

			public int getColumnCount() {
				return 3;
			}

			public int getRowCount() {
				return lights.size();
			}

			public Object getValueAt(int row, int col) {
				if (col == 0) {
					TubeLight light = lights.get(row);
					return String.format("%s (Range %f)", light.getName(), light.getScale().x);
					
				} else if (col == 1) {
					return vectorToString(lights.get(row).getPosition());
					
				} else if (col == 2) {
					return vectorToString(lights.get(row).getColor());
					
				}
				return "";
			}

			public String getColumnName(int column) {
				if (column == 0) {
					return "Name";
				} else if (column == 1) {
					return "Position";
				} else if (column == 2) {
					return "Color";
				}
				return "Null";
			}
		};

		JTable tubeLightsTable = new JTable(tubeLightsTableModel);
		
		tubeLightsPane  =  new JScrollPane(tubeLightsTable);
		ListSelectionModel tubeLightsCellSelectionModel = tubeLightsTable.getSelectionModel();
		tubeLightsCellSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		tubeLightsCellSelectionModel.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {

				int[] selectedRow = tubeLightsTable.getSelectedRows();
				int[] selectedColumns = tubeLightsTable.getSelectedColumns();

				for (int i = 0; i < selectedRow.length; i++) {
					for (int j = 0; j < selectedColumns.length; j++) {
						TubeLight selectedLight = world.getRenderer().getLightFactory().getTubeLights().get(selectedRow[i]);
						entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
						entityViewFrame.getContentPane().removeAll();
						entityViewFrame.pack();
						entityViewFrame.setSize(1000, 600);
						entityViewFrame.add(new TubeLightView(world, debugFrame, (TubeLight) selectedLight));
						entityViewFrame.setVisible(true);
					}
				}
			}
		});
	}

	private void createAreaLightsTab() {
		DebugFrame debugFrame = this;
		TableModel areaLightsTableModel = new AbstractTableModel() {

			List<AreaLight> lights = world.getRenderer().getLightFactory().getAreaLights();

			public int getColumnCount() {
				return 3;
			}

			public int getRowCount() {
				return lights.size();
			}

			public Object getValueAt(int row, int col) {
				if (col == 0) {
					AreaLight light = lights.get(row);
					return String.format("%s (Range %f)", light.getName(), light.getScale().z);
					
				} else if (col == 1) {
					return vectorToString(lights.get(row).getPosition());
					
				} else if (col == 2) {
					return vectorToString(lights.get(row).getColor());
					
				}
				return "";
			}

			public String getColumnName(int column) {
				if (column == 0) {
					return "Name";
				} else if (column == 1) {
					return "Position";
				} else if (column == 2) {
					return "Color";
				}
				return "Null";
			}
		};

		JTable areaLightsTable = new JTable(areaLightsTableModel);
		
		areaLightsPane  =  new JScrollPane(areaLightsTable);
		ListSelectionModel areaLightsCellSelectionModel = areaLightsTable.getSelectionModel();
		areaLightsCellSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		areaLightsCellSelectionModel.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {

				int[] selectedRow = areaLightsTable.getSelectedRows();
				int[] selectedColumns = areaLightsTable.getSelectedColumns();

				for (int i = 0; i < selectedRow.length; i++) {
					for (int j = 0; j < selectedColumns.length; j++) {
						AreaLight selectedLight = world.getRenderer().getLightFactory().getAreaLights().get(selectedRow[i]);
						entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
						entityViewFrame.getContentPane().removeAll();
						entityViewFrame.pack();
						entityViewFrame.setSize(1000, 600);
						System.out.println(selectedLight.getName());
						System.out.println(selectedRow[0]);
						System.out.println(world.getRenderer().getLightFactory().getAreaLights().size());
						entityViewFrame.add(new AreaLightView(world, debugFrame, selectedLight));
						entityViewFrame.setVisible(true);
					}
				}
			}
		});
	}

	private void createMaterialPane(World world) {
		DebugFrame debugFrame = this;
		MaterialFactory materialFactory = world.getRenderer().getMaterialFactory();
		TableModel materialDataModel = new AbstractTableModel() {


			public int getColumnCount() {
				return 2;
			}

			public int getRowCount() {
				return world.getRenderer().getMaterialFactory().MATERIALS.size();
			}

			public Object getValueAt(int row, int col) {

				List<Object> paths = Arrays.asList(materialFactory.MATERIALS.keySet().toArray());
				
				if (col == 0) {
					return paths.get(row);
				}
				List<Object> materials = Arrays.asList(materialFactory.MATERIALS.values().toArray());
				return materials.get(row);
			}
			
			public String getColumnName(int column) {
				if (column == 0) {
					return "Path";
				} else if (column == 1) {
					return "Material";
				}
				return "Null";
			}
		};
		JTable materialTable = new JTable(materialDataModel);
		materialTable.getSelectionModel().addListSelectionListener(new ListSelectionListener(){
	        public void valueChanged(ListSelectionEvent event) {
	        	materialViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
	        	materialViewFrame.getContentPane().removeAll();
	        	materialViewFrame.pack();
	        	materialViewFrame.setSize(600, 600);
	        	WebScrollPane scrollPane = new WebScrollPane(new MaterialView(debugFrame, world, (Material) materialTable.getValueAt(materialTable.getSelectedRow(), 1)));
	        	scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
	        	materialViewFrame.add(scrollPane);
	            materialViewFrame.setVisible(true);
	        }
	    });

		materialPane =  new JScrollPane(materialTable);
	}

	private void createTexturePane(TextureFactory textureFactory) {
		TableModel textureDataModel = createTextureDataModel(textureFactory);
		JTable textureTable = new JTable(textureDataModel);
		texturePane  =  new JScrollPane(textureTable);
	}
	
	private AbstractTableModel createTextureDataModel(TextureFactory textureFactory) {
		return new AbstractTableModel() {

			public int getColumnCount() {
				return 2;
			}

			public int getRowCount() {
				return textureFactory.TEXTURES.size();
			}

			public Object getValueAt(int row, int col) {
				if (col == 0) {
					List<Object> paths = Arrays.asList(textureFactory.TEXTURES.keySet()
							.toArray());
					return paths.get(row);
				}
				List<Object> textures = Arrays.asList(textureFactory.TEXTURES.values()
						.toArray());
				texture.Texture texture = (texture.Texture) textures.get(row);
				return String.format("Texture %d x %d", texture.getImageWidth(), texture.getImageHeight());
			}

			public String getColumnName(int column) {
				if (column == 0) {
					return "Path";
				} else if (column == 1) {
					return "Texture";
				}
				return "Null";
			}
		};
	}

	private void addSceneObjects(World world) {
		DefaultMutableTreeNode top = new DefaultMutableTreeNode("Scene");
		
		for (Entity e : world.getScene().getEntities()) {
			DefaultMutableTreeNode entityNode = new DefaultMutableTreeNode(e.getName());
			
			Material material = e.getComponent(ModelComponent.class).getMaterial();
			if (material != null) {
				DefaultMutableTreeNode materialNode = new DefaultMutableTreeNode(material.getName());
				
				for (Object map: Arrays.asList(material.getMaterialInfo().maps.getTextures().keySet().toArray())) {
					DefaultMutableTreeNode textureNode = new DefaultMutableTreeNode(String.format("%S - %s", map, material.getMaterialInfo().maps.get(map)));
					materialNode.add(textureNode);
				}
				
				entityNode.add(materialNode);
			}
			
			top.add(entityNode);
		}
		
		scene = new WebCheckBoxTree<DefaultMutableTreeNode>(top);
		addCheckStateListener(scene);
		new SetSelectedListener(scene, world, this, entityViewFrame);
	}

	private void addCheckStateListener(WebCheckBoxTree<DefaultMutableTreeNode> scene) {
		scene.addCheckStateChangeListener(new CheckStateChangeListener<DefaultMutableTreeNode>() {
			
			@Override
			public void checkStateChanged(List<CheckStateChange<DefaultMutableTreeNode>> stateChanges) {
				for (CheckStateChange<DefaultMutableTreeNode> checkStateChange : stateChanges) {
					boolean checked = checkStateChange.getNewState() == CheckState.checked ? true : false;
					
					Object object = checkStateChange.getNode().getUserObject();
					if(object instanceof Entity) {
						Entity entity = (Entity) object;
						entity.setVisible(checked);
					} else if (object instanceof Node) {
						Node node = (Node) object;
						List<Entity> result = new ArrayList<>();
						node.getAllEntitiesInAndBelow(result);
						for (Entity e : result) {
							e.setVisible(checked);
						}
					}
				}
			}
		});
	}

	private void addProbes(World world) {
		List<EnvironmentProbe> probes = world.getRenderer().getEnvironmentProbeFactory().getProbes();
		DefaultMutableTreeNode top = new DefaultMutableTreeNode("Probes (" + probes.size() + ")");
		for (EnvironmentProbe environmentProbe : probes) {
			top.add(new DefaultMutableTreeNode(environmentProbe));
		}
		this.probes = new WebCheckBoxTree<DefaultMutableTreeNode>(top);
		addCheckStateListener(this.probes);
		new SetSelectedListener(this.probes, world, this, probeViewFrame);

		tabbedPane.remove(probesPane);
		probesPane = new JScrollPane(this.probes);
		tabbedPane.addTab("Probes", probesPane);
	}
	
	private void addOctreeSceneObjects(World world) {
		DefaultMutableTreeNode top = new DefaultMutableTreeNode("Scene (" + world.getScene().getEntities().size() + " entities)");
		
		addOctreeChildren(top, world.getScene().getOctree().rootNode);
		System.out.println("Added " + world.getScene().getEntities().size());
		scene = new WebCheckBoxTree<DefaultMutableTreeNode>(top);
		addCheckStateListener(scene);
		new SetSelectedListener(scene, world, this, entityViewFrame);
		scene.setCheckBoxTreeCellRenderer(new WebCheckBoxTreeCellRenderer(scene) {
            private JLabel lblNull = new JLabel("");

            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                    boolean arg2, boolean arg3, boolean arg4, int arg5, boolean arg6) {

                Component c = super.getTreeCellRendererComponent(tree, value, arg2, arg3, arg4, arg5, arg6);

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                if (matchesFilter(node)) {
                    c.setForeground(Color.BLACK);
                	c.setVisible(false);
                    return c;
                }
                else if (containsMatchingChild(node)) {
                    c.setForeground(Color.GRAY);
                	c.setVisible(false);
                    return c;
                }
                else {
                	c.setVisible(false);
                    return lblNull;
                }
            }

            private boolean matchesFilter(DefaultMutableTreeNode node) {
            	String filterText = sceneViewFilterField.getText();
                return "".equals(filterText) || (node.getUserObject().toString()).startsWith(filterText);
            }

            private boolean containsMatchingChild(DefaultMutableTreeNode node) {
                Enumeration<DefaultMutableTreeNode> e = node.breadthFirstEnumeration();
                while (e.hasMoreElements()) {
                    if (matchesFilter(e.nextElement())) {
                        return true;
                    }
                }

                return false;
            }
        });

		tabbedPane.remove(scenePane);
		scenePane = new JScrollPane(scene);
		tabbedPane.addTab("Scene", scenePane);
	}
	
	private void addOctreeChildren(DefaultMutableTreeNode parent, Node node) {
		
		List<Entity> entitiesInAndBelow = new ArrayList<Entity>();
		node.getAllEntitiesInAndBelow(entitiesInAndBelow);
		
		DefaultMutableTreeNode current = new DefaultMutableTreeNode(node.toString() + " (" + entitiesInAndBelow.size() + " Entities in/below)");
		parent.add(current);
		if(node.hasChildren()) {
			for(int i = 0; i < 8; i++) {
				addOctreeChildren(current, node.children[i]);
			}
		}
		
		for (Entity entity : node.entities) {
			if(entity.hasParent()) { continue; }
			DefaultMutableTreeNode currentEntity = new DefaultMutableTreeNode(entity);
			if(entity.hasChildren()) {
				entity.getChildren().forEach(child -> {
					currentEntity.add(new DefaultMutableTreeNode(child));
				});
			}
			current.add(currentEntity);
		}
		
		if (node.hasChildren() && node.entities.size() > 0 && !node.isRoot()) {
			System.out.println("FUUUUUUUUUUUUUUUUUUUUCK deepness is " + node.getDeepness());
		}
	}

	public void refreshSceneTree() {
		System.out.println("Refreshing");
		addOctreeSceneObjects(world);
	}

	public void refreshTextureTab() {
		System.out.println("Refreshing");
		tabbedPane.remove(texturePane);
		createTexturePane(world.getRenderer().getTextureFactory());
		tabbedPane.addTab("Texture", texturePane);
	}
	
	public void refreshMaterialTab() {
		System.out.println("Refreshing");
		tabbedPane.remove(materialPane);
		createMaterialPane(world);
		tabbedPane.addTab("Material", materialPane);
	}
	
	public void refreshPointLightsTab() {
		System.out.println("Refreshing");
		tabbedPane.remove(pointLightsPane);
		createPointLightsTab();
		tabbedPane.addTab("PointLights", pointLightsPane);
	}
	public void refreshTubeLightsTab() {
		System.out.println("Refreshing");
		tabbedPane.remove(tubeLightsPane);
		createTubeLightsTab();
		tabbedPane.addTab("TubeLights", tubeLightsPane);
	}
	public void refreshAreaLightsTab() {
		System.out.println("Refreshing");
		tabbedPane.remove(areaLightsPane);
		createAreaLightsTab();
		tabbedPane.addTab("AreaLights", areaLightsPane);
	}
	public void refreshProbeTab() {
		System.out.println("Refreshing");
		tabbedPane.remove(probesPane);
		addProbes(world);
		tabbedPane.addTab("Probes", probesPane);
	}
	
	private void showSuccess(String content) {
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(NotificationIcon.plus);
		notificationPopup.setDisplayTime( 2000 );
		notificationPopup.setContent(new WebLabel(content));
		NotificationManager.showNotification(notificationPopup);
	}

	private void showError(String content) {
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(NotificationIcon.error);
		notificationPopup.setDisplayTime( 2000 );
		notificationPopup.setContent(new WebLabel(content));
		NotificationManager.showNotification(notificationPopup);
	}
	
	@Subscribe
	public void handle(EntitySelectedEvent e) {
    	DebugFrame debugFrame = this;
    	if(!Display.isActive()) { return; }
		entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    	entityViewFrame.getContentPane().removeAll();
    	entityViewFrame.pack();
    	entityViewFrame.setSize(600, 700);
		entityViewFrame.add(new EntityView(world, debugFrame, (Entity) e.getEntity()));
    	entityViewFrame.setVisible(true);
//    	entityViewFrame.toBack();
	}

}
