package de.hanno.hpengine.engine.manager

interface Registry {
    fun <T> get(clazz: Class<T>) : ComponentSystem<T>
    fun getMangers(): List<ComponentSystem<*>>
    fun update(deltaSeconds: Float)
    fun <COMPONENT_TYPE, T: ComponentSystem<COMPONENT_TYPE>> register(manager: T) = register(manager.javaClass, manager)!!
    fun <COMPONENT_TYPE, T: ComponentSystem<COMPONENT_TYPE>> register(clazz: Class<T>, manager: T): T
}

interface ComponentSystem<T> {
    fun update(deltaSeconds: Float)
    val components: List<T>
}

class SimpleRegistry: Registry {

    override fun getMangers(): List<ComponentSystem<*>> = managers.values.toList()

    private val managers = mutableMapOf<Class<*>, ComponentSystem<*>>()

    override fun <COMPONENT_TYPE, T : ComponentSystem<COMPONENT_TYPE>> register(clazz: Class<T>, manager: T): T {
        managers.put(clazz, manager)
        return manager
    }

    override fun <T> get(clazz: Class<T>): ComponentSystem<T> {
        if(!managers.contains(clazz)) { throw IllegalStateException("Requested manager of clazz $clazz, but no manager registered.")}
        return (managers[clazz] as ComponentSystem<T>?)!!
    }
    override fun update(deltaSeconds: Float) {
        managers.values.forEach { it.update(deltaSeconds) }
    }

}