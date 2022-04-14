package de.hanno.hpengine.engine

import com.artemis.Aspect
import com.artemis.BaseSystem
import com.artemis.World
import com.artemis.WorldConfigurationBuilder
import com.artemis.managers.TagManager
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import de.hanno.hpengine.engine.component.artemis.*
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.config.DebugConfig
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.entity.CycleSystem
import de.hanno.hpengine.engine.extension.*
import de.hanno.hpengine.engine.graphics.GlfwWindow
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.Window
import de.hanno.hpengine.engine.graphics.imgui.EditorCameraInputSystem
import de.hanno.hpengine.engine.graphics.imgui.primaryCamera
import de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.engine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.engine.graphics.renderer.extensions.ReflectionProbeManager
import de.hanno.hpengine.engine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.model.EntityBuffer
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.material.ProgramDescription
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.physics.PhysicsManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.WorldAABB
import de.hanno.hpengine.engine.scene.dsl.*
import de.hanno.hpengine.engine.system.Clearable
import de.hanno.hpengine.engine.system.Extractor
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.util.ressources.enhanced
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.receiveOrNull
import net.mostlyoriginal.api.SingletonPlugin
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min


class Engine(val application: KoinApplication) {

    private val koin = application.koin
    private val config = koin.get<ConfigImpl>()
    private val addResourceContext = koin.get<AddResourceContext>()
    private val window = koin.get<Window<*>>()
    private val input = koin.get<Input>()
    private val renderManager = koin.get<RenderManager>()

    private val openGlProgramManager: OpenGlProgramManager = koin.get()
    private val textureManager: TextureManager = koin.get()
    val systems = listOf(
        WorldAABB(),
        renderManager,
        ModelSystem(
            config,
            koin.get(),
            koin.get(),
            MaterialManager(config, koin.get(), koin.get()),
            koin.get(),
            EntityBuffer(),
        ),
        ComponentExtractor(),
        SkyBoxSystem(),
        EditorCameraInputSystem(),
        CycleSystem(),
        DirectionalLightSystem(),
        PointLightSystem(config, koin.get(), koin.get()).apply {
            renderManager.renderSystems.add(this)
        },
        AreaLightSystem(koin.get(), koin.get(), config).apply {
            renderManager.renderSystems.add(this)
        },
        MaterialManager(config, koin.get(), koin.get()),
        openGlProgramManager,
        textureManager,
        TagManager(),
        CustomComponentSystem(),
        SpatialComponentSystem(),
        InvisibleComponentSystem(),
        GiVolumeSystem(koin.get()),
        PhysicsManager(config, koin.get(), koin.get(), koin.get()),
        ReflectionProbeManager(config),
        KotlinComponentSystem(),
    ) + koin.getAll<BaseSystem>()

    val extractors = systems.filterIsInstance<Extractor>()

    val worldConfigurationBuilder = WorldConfigurationBuilder().with(
        *(systems.distinct().toTypedArray())
    ).run {
        register(SingletonPlugin.SingletonFieldResolver())
    }

    val world = World(
        worldConfigurationBuilder.build()
            .register(
                Kryo().apply {
                    isRegistrationRequired = false
                    instantiatorStrategy = DefaultInstantiatorStrategy(StdInstantiatorStrategy())
                }
            )
            .register(input)
    ).apply {
        renderManager.renderSystems.forEach { it.artemisWorld = this }

        process()

        loadDemoScene(config)
    }

    private var updateThreadCounter = 0
    private val updateThreadNamer: (Runnable) -> Thread =
        { Thread(it).apply { name = "UpdateThread${updateThreadCounter++}" } }
    private val updateScopeDispatcher = Executors.newFixedThreadPool(8, updateThreadNamer).asCoroutineDispatcher()

    val updateCycle = AtomicLong()

    init {
        launchEndlessLoop { deltaSeconds ->
            try {
                executeCommands()
                withContext(updateScopeDispatcher) {
                    update(deltaSeconds)
                }
                extract(deltaSeconds)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        window.pollEventsInLoop()
    }


    //    private suspend fun executeCommands() = withContext(addResourceContext.singleThreadDispatcher) {
    private suspend fun executeCommands() {
        while (!addResourceContext.channel.isEmpty) {
            val command = addResourceContext.channel.receiveOrNull() ?: break
            command.invoke()
        }
    }

    private fun extract(deltaSeconds: Float): Long {
        val currentWriteState = renderManager.renderState.currentWriteState
        currentWriteState.cycle = updateCycle.get()
        currentWriteState.time = System.currentTimeMillis()

        koin.getAll<RenderSystem>().distinct().forEach { it.extract(currentWriteState, world) }

        extractors.forEach { it.extract(currentWriteState) }

        renderManager.finishCycle(deltaSeconds)

        return updateCycle.getAndIncrement()
    }

    suspend fun update(deltaSeconds: Float) = try {
        window.invoke { input.update() }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            val config = ConfigImpl(
                directories = Directories(
                    EngineDirectory(File("C:\\workspace\\hpengine\\engine\\src\\main\\resources\\hp")),
//                    EngineDirectory(File("C:\\Users\\Tenter\\workspace\\hpengine\\engine\\src\\main\\resources\\hp")),
//                    GameDirectory(File(Directories.GAMEDIR_NAME), null)
                    GameDirectory(File("C:\\workspace\\hpengine\\newsimplegame\\src\\main\\resources\\game"), null)
                ),
                debug = DebugConfig(isEditorOverlay = true)
            )

            val configModule = module {
                single { config } binds arrayOf(Config::class, ConfigImpl::class)
            }
            val windowModule = module {
                single { GlfwWindow(get()) } bind Window::class
            }
            val application = startKoin {
                modules(configModule, windowModule, baseModule, deferredRendererModule, imGuiEditorModule)
            }

            val engine = Engine(application)
        }

    }

    fun launchEndlessLoop(actualUpdateStep: suspend (Float) -> Unit): Job = GlobalScope.launch {
        var currentTimeNs = System.nanoTime()
        val dtS = 1 / 60.0

        while (true) {
            val newTimeNs = System.nanoTime()
            val frameTimeNs = (newTimeNs - currentTimeNs).toDouble()
            var frameTimeS = frameTimeNs / 1000000000.0
            currentTimeNs = newTimeNs
            while (frameTimeS > 0.0) {
                val deltaTime = min(frameTimeS, dtS)
                val deltaSeconds = deltaTime.toFloat()

                actualUpdateStep(deltaSeconds)
                world.delta = deltaTime.toFloat()
                world.process()

                frameTimeS -= deltaTime
                yield()
            }
        }
    }
}

fun launchEndlessRenderLoop(actualUpdateStep: suspend (Float) -> Unit): Job = GlobalScope.launch {
    var currentTimeNs = System.nanoTime()
    val dtS = 1 / 60.0

    while (true) {
        val newTimeNs = System.nanoTime()
        val frameTimeNs = (newTimeNs - currentTimeNs).toDouble()
        val frameTimeS = frameTimeNs / 1000000000.0
        currentTimeNs = newTimeNs
        val deltaTime = frameTimeS//min(frameTimeS, dtS)
        val deltaSeconds = deltaTime.toFloat()

        actualUpdateStep(deltaSeconds)
    }
}

fun World.clear() {
    val entities = aspectSubscriptionManager[Aspect.all()]
        .entities

    val ids = entities.data
    var i = 0
    val s = entities.size()
    while (s > i) {
        delete(ids[i])
        i++
    }

    systems.filterIsInstance<Clearable>().forEach { it.clear() }
}

fun World.loadSponzaScene(config: ConfigImpl) {
    clear()

    edit(create()).apply {
        create(TransformComponent::class.java)
        create(ModelComponent::class.java).apply {
            modelComponentDescription = StaticModelComponentDescription("assets/models/sponza.obj", Directory.Game)
        }
        create(SpatialComponent::class.java)
        create(NameComponent::class.java).apply {
            name = "Sponza"
        }
    }
    addDirectionalLight()
    addSkyBox(config)
    addPrimaryCamera()
}
fun World.loadDemoScene(config: ConfigImpl) {
    clear()

    edit(create()).apply {
        create(TransformComponent::class.java)
        create(ModelComponent::class.java).apply {
            modelComponentDescription = StaticModelComponentDescription("assets/models/cube.obj", Directory.Engine)
        }
        create(SpatialComponent::class.java)
        create(NameComponent::class.java).apply {
            name = "Cube"
        }
    }
    addDirectionalLight()
    addSkyBox(config)
    addPrimaryCamera()
}

private fun World.addDirectionalLight() {
    edit(create()).apply {
        create(NameComponent::class.java).apply {
            name = "DirectionalLight"
        }

        create(DirectionalLightComponent::class.java).apply { }
        create(TransformComponent::class.java).apply {
            transform = Transform().apply {
                translate(Vector3f(12f, 300f, 2f))
                rotateAroundLocal(Quaternionf(AxisAngle4f(Math.toRadians(100.0).toFloat(), 1f, 0f, 0f)), 0f, 0f, 0f)
            }
        }
    }
}

fun World.addPrimaryCamera() {
    edit(create()).apply {
        create(TransformComponent::class.java)
        getSystem(TagManager::class.java).register(primaryCamera, entityId)
        create(NameComponent::class.java).apply {
            name = "PrimaryCamera"
        }
        create(CameraComponent::class.java)
    }
}

fun World.addSkyBox(config: ConfigImpl) {
    edit(create()).apply {
        create(NameComponent::class.java).apply {
            name = "SkyBox"
        }
        create(TransformComponent::class.java)
        create(SkyBoxComponent::class.java)
        create(ModelComponent::class.java).apply {
            modelComponentDescription = StaticModelComponentDescription(
                "assets/models/skybox.obj",
                Directory.Engine,
                material = Material(
                    name = "Skybox",
                    materialType = Material.MaterialType.UNLIT,
                    cullBackFaces = false,
                    isShadowCasting = false,
                    programDescription = ProgramDescription(
                        vertexShaderSource = config.EngineAsset("shaders/first_pass_vertex.glsl")
                            .toCodeSource(),
                        fragmentShaderSource = config.EngineAsset("shaders/first_pass_fragment.glsl")
                            .toCodeSource().enhanced {
                                replace(
                                    "//END",
                                    """
                            out_colorMetallic.rgb = 0.25f*textureLod(environmentMap, V, 0).rgb;
                        """.trimIndent()
                                )
                            }
                    )
                ).apply {
                    this.put(Material.MAP.ENVIRONMENT, getSystem(TextureManager::class.java).cubeMap)
                }
            )
        }
    }
}
