package de.hanno.hpengine

import com.artemis.*
import com.artemis.link.EntityLinkManager
import com.artemis.managers.TagManager
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import de.hanno.hpengine.artemis.*
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.config.ConfigImpl
import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.entity.CycleSystem
import de.hanno.hpengine.graphics.RenderManager
import de.hanno.hpengine.graphics.Window
import de.hanno.hpengine.graphics.imgui.editor.EditorCameraInputSystem
import de.hanno.hpengine.graphics.light.area.AreaLightSystem
import de.hanno.hpengine.graphics.light.directional.DirectionalLightSystem
import de.hanno.hpengine.graphics.light.point.PointLightSystem
import de.hanno.hpengine.graphics.renderer.extensions.ReflectionProbeManager
import de.hanno.hpengine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.material.MaterialManager
import de.hanno.hpengine.model.texture.TextureManager
import de.hanno.hpengine.physics.PhysicsManager
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.scene.WorldAABB
import de.hanno.hpengine.system.Clearable
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.transform.AABBData
import de.hanno.hpengine.extension.*
import de.hanno.hpengine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.receiveOrNull
import net.mostlyoriginal.api.SingletonPlugin
import org.joml.Vector3f
import org.koin.core.context.startKoin
import org.koin.dsl.binds
import org.koin.dsl.module
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min


class Engine(config: ConfigImpl, afterInit: Engine.() -> Unit = { world.loadDemoScene() }) {
    private val configModule = module {
        single { config } binds arrayOf(Config::class, ConfigImpl::class)
    }
    val application = startKoin {
        modules(configModule, baseModule, deferredRendererModule, imGuiEditorModule)
    }
    private val koin = application.koin
    val config = koin.get<Config>()
    private val addResourceContext = koin.get<AddResourceContext>()
    private val window = koin.get<Window<*>>()
    private val input = koin.get<Input>()
    private val renderManager = koin.get<RenderManager>()

    private val openGlProgramManager: OpenGlProgramManager = koin.get()
    private val textureManager: TextureManager = koin.get()
    val modelSystem = ModelSystem(
        config,
        koin.get(),
        koin.get(),
        MaterialManager(config, koin.get(), koin.get()),
        koin.get(),
        EntityBuffer(),
    )
    val entityLinkManager = EntityLinkManager()
    val systems = listOf(
        entityLinkManager,
        WorldAABB(),
        renderManager,
        modelSystem,
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
        koin.get<KotlinComponentSystem>(),
        CameraSystem(),
    ) + koin.getAll<BaseSystem>()

    val extractors = systems.filterIsInstance<Extractor>()
    val worldPopulators = systems.filterIsInstance<WorldPopulator>()

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
            .register(config)
    ).apply {
        renderManager.renderSystems.forEach { it.artemisWorld = this }
        getSystem(EntityLinkManager::class.java).apply {
            register(InstanceComponent::class.java, modelSystem)
        }
        process()
    }


    private var updateThreadCounter = 0
    private val updateThreadNamer: (Runnable) -> Thread =
        { Thread(it).apply { name = "UpdateThread${updateThreadCounter++}" } }
    private val updateScopeDispatcher = Executors.newFixedThreadPool(8, updateThreadNamer).asCoroutineDispatcher()

    val updateCycle = AtomicLong()

    private val updating = AtomicBoolean(false)
    init {
        afterInit()

        launchEndlessLoop { deltaSeconds ->
            try {
                updating.getAndSet(true)
                executeCommands()
                withContext(updateScopeDispatcher) {
                    update(deltaSeconds)
                }
                world.delta = deltaSeconds
                try {
                    world.process()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                extract(deltaSeconds)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                updating.getAndSet(false)
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

    fun update(deltaSeconds: Float) = try {
        window.invoke { input.update() }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            val config = ConfigImpl(
                directories = Directories(
//                    EngineDirectory(File("C:\\Users\\Tenter\\workspace\\hpengine\\engine\\src\\main\\resources\\hp")),
                    EngineDirectory(File("C:\\workspace\\hpengine\\engine\\src\\main\\resources\\hp")),
//                    GameDirectory(File(Directories.GAMEDIR_NAME), null)
                    GameDirectory(File("C:\\workspace\\hpengine\\newsimplegame\\src\\main\\resources\\game"), null)
                ),
            )

            val engine = Engine(config)
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

interface WorldPopulator {
    fun World.populate()
}

fun World.loadScene(block: World.() -> Unit) {
    clear()
    block()
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
    systems.filterIsInstance<WorldPopulator>().forEach {
        it.run { populate() }
    }
}

fun World.loadDemoScene() = loadScene {
    addStaticModelEntity("Cube", "assets/models/cube.obj", Directory.Engine)

}
fun World.addStaticModelEntity(
    name: String,
    path: String,
    directory: Directory = Directory.Game,
    translation: Vector3f = Vector3f(),
): EntityEdit = edit(create()).apply {
    create(TransformComponent::class.java).apply {
        transform.translation(translation)
    }
    create(ModelComponent::class.java).apply {
        modelComponentDescription = StaticModelComponentDescription(path, directory)
    }
    create(SpatialComponent::class.java)
    create(NameComponent::class.java).apply {
        this.name = name
    }
}

fun World.addAnimatedModelEntity(
    name: String,
    path: String,
    aabbData: AABBData,
    directory: Directory = Directory.Game
) {
    edit(create()).apply {
        create(TransformComponent::class.java)
        create(ModelComponent::class.java).apply {
            modelComponentDescription = AnimatedModelComponentDescription(path, directory, aabbData = aabbData)
        }
        create(SpatialComponent::class.java)
        create(NameComponent::class.java).apply {
            this.name = name
        }
    }
}
