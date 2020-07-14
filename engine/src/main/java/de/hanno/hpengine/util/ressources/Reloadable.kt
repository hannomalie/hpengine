package de.hanno.hpengine.util.ressources

interface Reloadable : Loadable {
    fun reload() {
        unload()
        load()
    }

    val name: String
}