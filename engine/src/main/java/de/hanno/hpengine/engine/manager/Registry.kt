package de.hanno.hpengine.engine.manager

interface Registry {
    fun <T> register(clazz: Class<T>, manager: ComponentSystem<T>): ComponentSystem<*>
    fun <T> get(clazz: Class<T>) : ComponentSystem<T>
    fun getMangers(): List<ComponentSystem<*>>
}

interface ComponentSystem<T>

class SimpleRegistry: Registry {
    override fun getMangers(): List<ComponentSystem<*>> = managers.values.toList()

    private val managers = mutableMapOf<Class<*>, ComponentSystem<*>>()

    override fun <T> register(clazz: Class<T>, manager: ComponentSystem<T>) = managers.put(clazz, manager)!!

    override fun <T> get(clazz: Class<T>): ComponentSystem<T> {
        if(!managers.contains(clazz)) { throw IllegalStateException("Requested manager of clazz $clazz, but no manager registered.")}
        return (managers[clazz] as ComponentSystem<T>?)!!
    }

}