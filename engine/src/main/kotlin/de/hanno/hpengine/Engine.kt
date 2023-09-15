package de.hanno.hpengine

import com.artemis.*
import com.artemis.link.EntityLinkManager
import com.artemis.managers.TagManager
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.Directories
import de.hanno.hpengine.directory.EngineDirectory
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.graphics.RenderManager
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.renderer.deferred.ExtensibleDeferredRenderer
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.model.ModelComponent
import de.hanno.hpengine.opengl.openglModule
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.spatial.SpatialComponent
import de.hanno.hpengine.system.Clearable
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.transform.AABBData
import glfwModule
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.receiveOrNull
import net.mostlyoriginal.api.SingletonPlugin
import org.joml.Vector3f
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ksp.generated.defaultModule
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min


class Engine(
    modules: List<Module> = emptyList(),
    afterInit: Engine.() -> Unit = { world.loadDemoScene() }
) // TODO: Change callsites to use new method simulate to block
{
    val application = startKoin {
        modules(apiModule, glfwModule, defaultModule)
        modules(modules)
    }
    private val koin = application.koin
    private val renderStateContext = koin.get<RenderStateContext>()
    private val config = koin.get<Config>()
    private val addResourceContext = koin.get<AddResourceContext>()
    private val window = koin.get<Window>()
    private val input = koin.get<Input>()

    val entityLinkManager = EntityLinkManager()
    val systems = listOf(
        entityLinkManager,
        TagManager(),
    ) + koin.getAll<BaseSystem>()

    val extractors = systems.filterIsInstance<Extractor>() // TODO: bind as Extractor, inject properly, this is flawed

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
        // TODO: Remove and make ExtensibleDeferredRenderer initialized as a normal BaseSystem
        koin.getAll<ExtensibleDeferredRenderer>().forEach { it.world = this }
        getSystem(EntityLinkManager::class.java).apply {
//            register(InstanceComponent::class.java, modelSystem) // TODO: Figure out what this did
        }
        process()
    }


    private var updateThreadCounter = 0
    private val updateThreadNamer: (Runnable) -> Thread =
        { Thread(it).apply { name = "UpdateThread${updateThreadCounter++}" } }
    private val updateScopeDispatcher = Executors.newFixedThreadPool(8, updateThreadNamer).asCoroutineDispatcher()

    val updateCycle = AtomicLong()

    private val updating = AtomicBoolean(false)

    fun simulate() {
        launchEndlessLoop { deltaSeconds ->
            try {
                updating.getAndSet(true)

                input.update()
                window.pollEvents()

                executeCommands()
                withContext(updateScopeDispatcher) {
                    world.delta = deltaSeconds
                    world.process()
                }
                extract(deltaSeconds)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                updating.getAndSet(false)
            }
        }
    }


    private suspend fun executeCommands() {
        while (!addResourceContext.channel.isEmpty) {
            val command = addResourceContext.channel.receiveOrNull() ?: break
            command.invoke()
        }
    }

    private fun extract(deltaSeconds: Float): Long {
        val currentWriteState = renderStateContext.renderState.currentWriteState
        currentWriteState.cycle = updateCycle.get()
        currentWriteState.time = System.currentTimeMillis()

        koin.getAll<RenderSystem>().distinct().forEach { it.extract(currentWriteState) }

        extractors.forEach { it.extract(currentWriteState) }

        koin.get<RenderManager>().finishCycle(deltaSeconds)

        return updateCycle.getAndIncrement()
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            val config = Config(
                directories = Directories(
//                    EngineDirectory(File("C:\\Users\\Tenter\\workspace\\hpengine\\engine\\src\\main\\resources\\hp")),
                    EngineDirectory(File("C:\\workspace\\hpengine\\engine\\src\\main\\resources\\hp")),
//                    GameDirectory(File(Directories.GAMEDIR_NAME), null)
                    GameDirectory(File("C:\\workspace\\hpengine\\newsimplegame\\src\\main\\resources\\game"), null)
                ),
            )

            val engine = Engine(modules = listOf(
                openglModule,
                module {
                    single { config }
                    single { config.gameDir }
                    single { config.engineDir }
                }
            ))
        }

    }

    fun launchEndlessLoop(actualUpdateStep: suspend (Float) -> Unit) = runBlocking {
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

    while (true) {
        val newTimeNs = System.nanoTime()
        val frameTimeNs = (newTimeNs - currentTimeNs).toDouble()
        val frameTimeS = frameTimeNs / 1000000000.0
        currentTimeNs = newTimeNs
        val deltaSeconds = frameTimeS.toFloat()

        actualUpdateStep(deltaSeconds)
    }
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
