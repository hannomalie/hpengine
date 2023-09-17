package de.hanno.hpengine.world

import com.artemis.Aspect
import com.artemis.EntityEdit
import com.artemis.World
import de.hanno.hpengine.WorldPopulator
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.model.ModelComponent
import de.hanno.hpengine.scene.dsl.AnimatedModelComponentDescription
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.spatial.SpatialComponent
import de.hanno.hpengine.system.Clearable
import de.hanno.hpengine.transform.AABBData
import org.joml.Vector3f

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
