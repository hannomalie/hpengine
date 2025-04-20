package de.hanno.hpengine.world

import com.artemis.Aspect
import com.artemis.EntityEdit
import com.artemis.World
import de.hanno.hpengine.WorldPopulator
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.model.BoundingVolumeComponent
import de.hanno.hpengine.model.ModelComponent
import de.hanno.hpengine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.system.Clearable
import de.hanno.hpengine.transform.AABBData
import org.apache.logging.log4j.LogManager
import org.joml.AxisAngle4f
import org.joml.Vector3f

private val logger = LogManager.getLogger("World")

fun World.loadScene(block: World.() -> Unit) {
    clear()
    logger.trace("executing load scene block")
    block()
    logger.trace("finished load scene block")
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

    logger.trace("clearing world")
    systems.filterIsInstance<Clearable>().forEach { it.clear() }
    logger.trace("repopulating world")
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
    rotation: AxisAngle4f? = null,
    scale: Vector3f? = null,
    rotation1: AxisAngle4f? = null,
    rotation2: AxisAngle4f? = null,
): EntityEdit = edit(create()).apply {
    create(TransformComponent::class.java).apply {
        if(scale != null) {
            transform.scaleLocal(scale.x, scale.y, scale.z)
        }
        if(rotation != null) {
            transform.rotateLocal(rotation.angle, rotation.x, rotation.y, rotation.z)
        }
        if(rotation1 != null) {
            transform.rotateLocal(rotation1.angle, rotation1.x, rotation1.y, rotation1.z)
        }
        if(rotation2 != null) {
            transform.rotateLocal(rotation2.angle, rotation2.x, rotation2.y, rotation2.z)
        }
        transform.translateLocal(translation)
    }
    create(BoundingVolumeComponent::class.java)
    create(ModelComponent::class.java).apply {
        modelComponentDescription = StaticModelComponentDescription(path, directory)
    }
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
        create(BoundingVolumeComponent::class.java)
        create(ModelComponent::class.java).apply {
            modelComponentDescription = AnimatedModelComponentDescription(path, directory, aabbData = aabbData)
        }
        create(NameComponent::class.java).apply {
            this.name = name
        }
    }
}
