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
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.lifecycle.UpdateCycle
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
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.joml.Vector3f
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ksp.generated.defaultModule
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.File
import java.util.concurrent.Executors

class Engine(
    baseSystems: List<BaseSystem>,
    private val config: Config,
    private val input: Input,
    private val window: Window,
    private val addResourceContext: AddResourceContext,
) {
    val systems = listOf(
        EntityLinkManager(),
        TagManager(),
    ) + baseSystems

    val extractors = systems.filterIsInstance<Extractor>().distinct() // TODO: bind as Extractor, inject properly, this is flawed
    val renderManager = systems.firstIsInstance<RenderManager>() // TODO: See above
    val updateCycle = systems.firstIsInstance<UpdateCycle>() // TODO: See above

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
        getSystem(EntityLinkManager::class.java).apply {
//            register(InstanceComponent::class.java, modelSystem) // TODO: Figure out what this did
        }
        process()
    }

    private var updateThreadCounter = 0
    private val updateThreadNamer: (Runnable) -> Thread = { Thread(it).apply { name = "UpdateThread${updateThreadCounter++}" } }
    private val updateScopeDispatcher = Executors.newFixedThreadPool(8, updateThreadNamer).asCoroutineDispatcher()

    fun simulate() = launchEndlessLoop { deltaSeconds ->
        // Input and window updates need to be done on the main thread, they can't be moved to
        // the base system regular update below. Same for Executing the commands
        input.update()
        window.pollEvents()
        addResourceContext.executeCommands()

        withContext(updateScopeDispatcher) {
            world.delta = deltaSeconds
            world.process()
        }
        renderManager.extract(extractors, deltaSeconds)
        updateCycle.cycle.getAndIncrement()
    }
    companion object
}
