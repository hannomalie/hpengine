package de.hanno.hpengine.ressources

interface Reloadable : Loadable {
    fun reload() {
        unload()
        load()
    }

    val name: String
}