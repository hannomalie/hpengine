package de.hanno.hpengine

import com.artemis.BaseSystem
import com.artemis.World
import com.artemis.WorldConfigurationBuilder
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderManager
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.lifecycle.Termination
import de.hanno.hpengine.lifecycle.UpdateCycle
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.system.PrioritySystem
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import net.mostlyoriginal.api.SingletonPlugin
import org.apache.logging.log4j.LogManager
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.objenesis.strategy.StdInstantiatorStrategy
import java.util.concurrent.Executors
import kotlin.system.exitProcess

class Engine(
    baseSystems: List<BaseSystem>,
    val config: Config,
    private val input: Input,
    private val window: Window,
    private val addResourceContext: AddResourceContext,
) {
    private val logger = LogManager.getLogger(Engine::class.java)

    val systems = baseSystems.sortedByDescending {
        (it as? PrioritySystem)?.priority ?: -1
    }

    val extractors = systems.filterIsInstance<Extractor>().distinct() // TODO: bind as Extractor, inject properly, this is flawed
    val renderManager = systems.firstIsInstance<RenderManager>() // TODO: See above
    val updateCycle = systems.firstIsInstance<UpdateCycle>() // TODO: See above
    val termination = systems.firstIsInstance<Termination>() // TODO: See above
    val graphicsApi = systems.firstIsInstance<GraphicsApi>()

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
    ).apply {
        process()
    }

    init {
        logger.info("Registered: ${systems.size} systems, ${extractors.size} extractors")

        window.gpuExecutor.perFrameAction = renderManager::frame
        window.gpuExecutor.afterLoop = {
            window.close()
            exitProcess(0)
        }
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                logger.info("Termination requested")
                termination.terminationRequested.set(true)
            }
        })
    }
    private var updateThreadCounter = 0
    private val updateThreadNamer: (Runnable) -> Thread = { Thread(it).apply { name = "UpdateThread${updateThreadCounter++}" } }
    private val updateScopeDispatcher = Executors.newFixedThreadPool(8, updateThreadNamer).asCoroutineDispatcher()

    fun simulate() {
        launchEndlessLoop({
            !termination.terminationRequested.get()
        }) { deltaSeconds ->
            // Input and window updates need to be done on the main thread, they can't be moved to
            // the base system regular update below. Same for Executing the commands
            input.update()
            window.pollEvents()

            addResourceContext.executeCommands()

            withContext(updateScopeDispatcher) {
                world.delta = deltaSeconds
                world.process()
            }
            renderManager.extract(extractors, world.delta)
            updateCycle.cycle.getAndIncrement()
        }
        logger.info("Termination allowed")
        termination.terminationAllowed.set(true)
    }
    companion object
}
