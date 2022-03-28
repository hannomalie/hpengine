package de.hanno.hpengine.engine.container

import com.artemis.Entity
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.transform.AABB
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.engine.transform.isInFrustum
import de.hanno.hpengine.util.stopwatch.StopWatch
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import java.io.Serializable
import java.util.*
import java.util.concurrent.*
import java.util.logging.Logger
import java.util.stream.Collectors

class Octree @JvmOverloads constructor(
    center: Vector3f = Vector3f(),
    size: Float = DEFAULT_SIZE,
    maxDeepness: Int = DEFAULT_MAX_DEEPNESS
) : Updatable, Serializable {
    companion object {
        private const val serialVersionUID = 1L
        private val executor = Executors.newFixedThreadPool(8)
        private var executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        private val LOGGER = Logger.getLogger(Octree::class.java.name)
        private var matrix44Buffer = BufferUtils.createFloatBuffer(16).apply {
            rewind()
            modelMatrix.identity()
            modelMatrix[this]
            rewind()
        }
        private val modelMatrix = Matrix4f()
        const val DEFAULT_SIZE = 1000f
        const val DEFAULT_MAX_DEEPNESS = 6
        var DRAW_LINES = false
    }

    private val center = Vector3f()
    private val size = DEFAULT_SIZE
    val maxDeepness = DEFAULT_MAX_DEEPNESS
    private var currentDeepness = 0
    val rootNode = Node(this, center, size).apply { span() }

    @Transient
    private val entityNodeMappings: MutableMap<Entity, Node> = ConcurrentHashMap()

    constructor(center: Vector3f, maxDeepness: Int) : this(center, DEFAULT_SIZE, maxDeepness) {}

    fun getEntitiesForNode(node: Node): List<Entity> {
        return Collections.unmodifiableList(
            entityNodeMappings.entries.stream().filter { (_, value): Map.Entry<Entity?, Node?> -> value == node }
                .map { (key): Map.Entry<Entity?, Node?> -> key }
                .collect(Collectors.toList()))
    }

    fun add(entity: Entity) {
        if (entity.hasChildren()) {
            add(entity.getChildren())
        }
        val insertedInto = rootNode.insert(entity)
        if (insertedInto == null) {
            entityNodeMappings[entity] = rootNode
        } else {
            entityNodeMappings[entity] = insertedInto
        }
        rootNode.optimize()
        //	   rootNode.optimizeThreaded();
    }

    fun insertWithoutOptimize(entity: Entity) {
        if (entity.hasChildren()) {
            add(entity.getChildren())
        }
        val insertedInto = rootNode.insert(entity)
        if (insertedInto == null) {
            entityNodeMappings[entity] = rootNode
        } else {
            entityNodeMappings[entity] = insertedInto
        }
    }

    fun add(entities: List<Entity>?) {
        if (entities == null) {
            return
        }
        for (Entity in entities) {
            insertWithoutOptimize(Entity)
        }
        val start = System.currentTimeMillis()
        rootNode.optimize()
        optimize()
        //		rootNode.optimizeThreaded();
        val end = System.currentTimeMillis()
        LOGGER.info("Took " + (end - start) + " ms to optimize.")
    }

    fun getVisible(camera: Camera?): List<Entity?> {
        StopWatch.getInstance().start("Octree get visible")
        var result: List<Entity?>? = ArrayList()
        //		result.addAll(getEntitiesForNode(rootNode));
//		rootNode.getVisible(de.hanno.hpengine.camera, result);
//		rootNode.getVisibleThreaded(de.hanno.hpengine.camera, result);
        result = entities.filter {
            TODO("Implement culling here")
        }
        StopWatch.getInstance().stopAndPrintMS()
        return ArrayList(result)
    }

    fun optimize() {
        if (rootNode.hasChildren()) {
            for (node in rootNode.children) {
                node!!.optimize()
            }
        }
    }

    private fun batchLines(linePoints: List<Vector3fc>, node: Node?) {
        if (node!!.hasChildren()) {
            for (child in node.children) {
                batchLines(linePoints, child)
            }
        } else if (node.hasEntities()) {
            linePoints.addAABBLines(node.looseAabb.min, node.looseAabb.max)
        }
    }

    /**
     * children: index is clockwise 0-3 for top: left front, left back, right back, right front and 4-7 bottom: right back, right front, left front, left back
     *
     *
     */
    class Node @JvmOverloads constructor(
        var octree: Octree,
        var center: Vector3f,
        var size: Float,
        val deepness: Int = 0
    ) : Serializable {
        var parent: Node? = null
        var children = arrayOfNulls<Node>(8)
        var aabb: AABB
        var looseAabb: AABB
        private var hasChildren = false
        fun span() {
            if (deepness < octree.maxDeepness) {
                if (!hasChildren()) {
                    for (i in 0..7) {
                        children[i] = Node(this, i)
                        children[i]!!.span()
                    }
                    setHasChildren(true)
                }
            }
        }

        val isLeaf: Boolean
            get() = deepness == octree.maxDeepness
        val isRoot: Boolean
            get() = octree.rootNode === this

        fun getVisible(camera: Camera, result: MutableList<Entity>) {
            if (isRoot || isVisible(camera)) {
                if (hasChildren) {
                    for (i in 0..7) {
                        children[i]!!.getVisible(camera, result)
                    }
                } else {
                    result.addAll(octree.getEntitiesForNode(this))
                }
            }
        }

        private fun getVisibleThreaded(camera: Camera, result: MutableList<Entity>): List<Entity> {

//			StopWatch.getInstance().start("Octree collects");
            val ecs = ExecutorCompletionService<List<Entity>>(executorService)
            val toGather: MutableList<Future<List<Entity>>> = ArrayList()
            if (isRoot) {
                for (i in 0..7) {
                    val worker: Callable<List<Entity>> = CollectVisibleCallable(camera, children[i])
                    val submit: Future<List<Entity>> = ecs.submit(worker)
                    toGather.add(submit)
                }
            }
            //			StopWatch.getInstance().stopAndPrintMS();

//			StopWatch.getInstance().start("Octree merge collected");
            for (future in toGather) {
                try {
                    result.addAll(future.get())
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                } catch (e: ExecutionException) {
                    e.printStackTrace()
                }
            }

//			collectExecutor.shutdown();
//			StopWatch.getInstance().stopAndPrintMS();
            return result
        }

        val entities: Collection<Entity>
            get() = octree.getEntitiesForNode(this)

        internal class CollectVisibleCallable(private val camera: Camera, private val node: Node?) :
            Callable<List<Entity>> {
            override fun call(): List<Entity> {
                val result: MutableList<Entity> = ArrayList()
                node!!.getVisible(camera, result)
                return result
            }
        }

        fun getAllEntitiesInAndBelow(result: MutableList<Entity>) {
            if (hasChildren) {
                for (i in 0..7) {
                    children[i]!!.getAllEntitiesInAndBelow(result)
                }
            }
            result.addAll(octree.getEntitiesForNode(this))
        }

        fun isVisible(camera: Camera?): Boolean {
            return looseAabb.isInFrustum(camera!!)
        }

        private val allEntitiesInAndBelowThreaded: List<Entity?>
            private get() {
                val ecs = ExecutorCompletionService<List<Entity>>(executorService)
                val toGather: MutableList<Future<List<Entity>>> = ArrayList()
                val result: MutableList<Entity> = ArrayList()
                if (hasChildren) {
                    for (i in 0..7) {
                        val worker = CollectEntitiesInAndBelowCallable(children[i]!!)
                        val submit: Future<List<Entity>> = ecs.submit(worker)
                        toGather.add(submit)
                    }
                }
                for (future in toGather) {
                    try {
                        result.addAll(future.get())
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    } catch (e: ExecutionException) {
                        e.printStackTrace()
                    }
                }
                result.addAll(octree.getEntitiesForNode(this))
                return result
            }

        internal class CollectEntitiesInAndBelowCallable(private val node: Node) : Callable<List<Entity>> {
            override fun call(): List<Entity> {
                val result: MutableList<Entity> = ArrayList()
                //					result.addAll(node.getAllEntitiesInAndBelow());
                node.getAllEntitiesInAndBelow(result)
                return result
            }
        }

        fun getPointsForLineDrawing(camera: Camera): List<FloatArray> {
            val arrays: MutableList<FloatArray> = ArrayList()
            if (!isVisible(camera)) {
                return arrays
            }
            val temp: MutableList<Entity> = ArrayList()
            getVisible(camera, temp)
            if (temp.isEmpty()) {
                return arrays
            }
            arrays.add(points)
            if (hasChildren()) {
                for (i in 0..7) {
                    arrays.addAll(children[i]!!.getPointsForLineDrawing(camera))
                }
            }
            return arrays
        }

        val pointsForLineDrawing: List<FloatArray?>
            get() {
                val arrays: MutableList<FloatArray?> = ArrayList()
                arrays.add(points)
                if (hasChildren()) {
                    for (i in 0..7) {
                        arrays.addAll(children[i]!!.pointsForLineDrawing)
                    }
                }
                return arrays
            }

        // index is clockwise 0-3 for top: left front, left back, right back, right front
        // and 4-7 bottom: right back, right front, left front, left back 
        private constructor(parent: Node, index: Int) : this(
            parent.octree,
            getCenterForNewChild(parent, index),
            parent.size / 2,
            parent.deepness + 1
        ) {
        }

        // returns the node the entity was inserted into or null, if no insertion point was found
        fun insert(entity: Entity): Node? {
//			LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("Inserting %s ...", entity));
            val minMaxWorld: AABB = entity.getBoundingVolume()
            if (isLeaf) {
                return if (contains(minMaxWorld)) {
                    octree.entityNodeMappings[entity] = this
                    this
                } else {
                    null
                }
            }
            return if (hasChildren()) {
                for (i in 0..7) {
                    val node = children[i]
                    if (node!!.contains(minMaxWorld)) {
                        if (node.contains(entity.getTransform().center)) {
                            if (node.insert(entity) != null) {
                                return node
                            }
                        }
                    }
                }

                // Wasn't able to add entity to children
                octree.entityNodeMappings[entity] = this
                this
            } else {
                octree.entityNodeMappings[entity] = this
                this
            }
        }

        private operator fun contains(position: Vector3f): Boolean {
            //return aabb.contains(new Vector4f(position.x, position.y, position.z, 1));
            return looseAabb.contains(Vector4f(position.x, position.y, position.z, 1f))
        }

        fun optimizeThreaded() {
            if (hasChildren()) {
                for (i in 0..7) {
                    val node = children[i]
                    val worker: Runnable = OptimizeRunnable(node)
                    executor.execute(worker)
                }
                try {
                    executor.shutdown()
                    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                LOGGER.info("Finished all threads")
            }
        }

        fun optimize() {
            if (hasChildren()) {
                if (hasEntities() && !isRoot) {
                    val collected = collectAllEntitiesFromChildren()
                    for (toInsert in collected) {
                        octree.entityNodeMappings[toInsert] = this
                    }
                    setHasChildren(false)
                } else {
                    for (i in 0..7) {
                        val node = children[i]
                        node!!.optimize()
                    }
                }
            }
        }

        internal class OptimizeRunnable(private val node: Node?) : Runnable {
            override fun run() {
                if (node!!.hasEntities() && node.hasChildren()) {
                    node.addAll(node.collectAllEntitiesFromChildren())
                    node.setHasChildren(false)
                    LOGGER.info("Optimized...")
                    //					return;
                }
                node.optimize()
            }
        }

        private fun addAll(entities: List<Entity>) {
            for (toAdd in entities) {
                octree.entityNodeMappings[toAdd] = this
            }
        }

        private fun collectAllEntitiesFromChildren(): List<Entity> {
            val result: MutableList<Entity> = ArrayList()
            for (i in 0..7) {
                val node = children[i]
                if (!node!!.hasChildren()) {
                    result.addAll(node.entities)
                } else {
                    result.addAll(node.entities)
                    result.addAll(node.collectAllEntitiesFromChildren())
                }
            }
            return result
        }

        private operator fun contains(minMaxWorld: AABB): Boolean {
            val min = Vector3f(minMaxWorld.min)
            val max = Vector3f(minMaxWorld.max)
            return if (looseAabb.contains(min) && looseAabb.contains(max)) {
//				LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("(%.2f, %.2f, %.2f) is in %s", min.x, min.y, min.z, aabb));
//				LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("(%.2f, %.2f, %.2f) is in %s", max.x, max.y, max.z, aabb));
                true
            } else false

//			LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("(%.2f, %.2f, %.2f) not in %s", min.x, min.y, min.z, aabb));
//			LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("(%.2f, %.2f, %.2f) not in %s", max.x, max.y, max.z, aabb));
        }

        fun hasChildren(): Boolean {
            return hasChildren // && !hasEntities();
        }

        fun setHasChildren(hasChildren: Boolean) {
            this.hasChildren = hasChildren
        }

        fun hasEntities(): Boolean {
            return !entities.isEmpty()
        }

        fun hasEntitiesInChildNodes(): Boolean {
            if (hasChildren) {
                for (i in children.indices) {
                    if (children[i]!!.hasEntitiesInChildNodes()) {
                        return true
                    }
                }
            }
            return false
        }

        override fun toString(): String {
            return String.format("Node(%.2f) @ (%.2f, %.2f, %.2f)", size, center.x, center.y, center.z)
        }

        fun getMaxDeepness(): Int {
            return if (hasChildren()) {
                var childrenMaxDeepness = deepness
                for (i in children.indices) {
                    val d = children[i]!!.getMaxDeepness()
                    childrenMaxDeepness = if (d > childrenMaxDeepness) d else childrenMaxDeepness
                }
                childrenMaxDeepness
            } else {
                deepness
            }
        }

        private val points: FloatArray
            get() = aabb.getPointsAsArray()

        private fun isValid() = hasChildren() && entities.isEmpty() || isRoot || !hasChildren()

        fun remove(entity: Entity?): Boolean {
            if (hasChildren) {
                for (i in children.indices) {
                    if (children[i]!!.entities.contains(entity)) {
                        return children[i]!!.remove(entity)
                    }
                }
            }
            return octree.entityNodeMappings.remove(entity, this)
        }

        companion object {
            private const val serialVersionUID = 1L
            private fun getCenterForNewChild(parent: Node, index: Int): Vector3f {
                val newNodeCenter = Vector3f(parent.center)
                val offset = parent.size / 4
                when (index) {
                    0 -> {
                        newNodeCenter.x -= offset
                        newNodeCenter.y += offset
                        newNodeCenter.z += offset
                    }
                    1 -> {
                        newNodeCenter.x -= offset
                        newNodeCenter.y += offset
                        newNodeCenter.z -= offset
                    }
                    2 -> {
                        newNodeCenter.x += offset
                        newNodeCenter.y += offset
                        newNodeCenter.z -= offset
                    }
                    3 -> {
                        newNodeCenter.x += offset
                        newNodeCenter.y += offset
                        newNodeCenter.z += offset
                    }
                    4 -> {
                        newNodeCenter.x += offset
                        newNodeCenter.y -= offset
                        newNodeCenter.z -= offset
                    }
                    5 -> {
                        newNodeCenter.x += offset
                        newNodeCenter.y -= offset
                        newNodeCenter.z += offset
                    }
                    6 -> {
                        newNodeCenter.x -= offset
                        newNodeCenter.y -= offset
                        newNodeCenter.z += offset
                    }
                    7 -> {
                        newNodeCenter.x -= offset
                        newNodeCenter.y -= offset
                        newNodeCenter.z -= offset
                    }
                    else -> {
                    }
                }
                return newNodeCenter
            }
        }

        init {
            if (octree.currentDeepness < deepness) {
                octree.currentDeepness = deepness
            }
            aabb = AABB(center, size)
            looseAabb = AABB(center, 2 * size)

//			LOGGER.de.hanno.hpengine.log(Level.INFO, "Created " + this.toString() + " with " + this.aabb.toString());
        }
    }

    val entities: List<Entity>
        get() = CopyOnWriteArrayList(entityNodeMappings.keys)

    fun clear() {
        entityNodeMappings.clear()
    }

    fun getCurrentDeepness(): Int {
        return rootNode.getMaxDeepness()
    }

    fun remove(entity: Entity?): Boolean {
        entityNodeMappings.remove(entity)
        return rootNode.remove(entity)
    }
}

private fun Entity.getTransform(): Transform {
    TODO("Not yet implemented")
}

private fun Entity.getBoundingVolume(): AABB {
    TODO("Not yet implemented")
}

private fun <E> List<E>.addAABBLines(min: E, max: E) {
    TODO("Not yet implemented")
}

private fun Entity.getChildren(): List<Entity> {
    TODO("Not yet implemented")
}

private fun Entity.hasChildren(): Boolean {
    TODO("Not yet implemented")
}
