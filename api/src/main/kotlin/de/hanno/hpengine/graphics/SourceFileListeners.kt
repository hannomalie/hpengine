package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.shader.AbstractProgram
import de.hanno.hpengine.graphics.shader.Uniforms
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.ressources.FileMonitor
import de.hanno.hpengine.ressources.Reloadable

context(FileMonitor, GraphicsApi)
fun <T: Uniforms> AbstractProgram<T>.createFileListeners() {
    val sources: List<FileBasedCodeSource> = shaders.map { it.source }.filterIsInstance<FileBasedCodeSource>()
    replaceOldListeners(sources, object: Reloadable {
        override val name = this@createFileListeners.name
        override fun load() {
            this@createFileListeners.load()
            println("Reloaded ${this@createFileListeners.name}")
        }
    })
}

context(FileMonitor)
fun List<FileBasedCodeSource>.registerFileChangeListeners(reloadable: Reloadable) = map {
    it.registerFileChangeListener(reloadable)
}

context(FileMonitor)
fun FileBasedCodeSource.registerFileChangeListener(reloadable: Reloadable) = addOnFileChangeListener(
    file, { file -> file.name.startsWith("global")}
) {
    reloadable.reload()
}

context(FileMonitor)
fun AbstractProgram<*>.replaceOldListeners(sources: List<FileBasedCodeSource>, reloadable: Reloadable) {
    removeOldListeners()
    fileListeners.addAll(sources.registerFileChangeListeners(reloadable))
}

context(FileMonitor)
fun AbstractProgram<*>.removeOldListeners() {
    monitor.observers.forEach { observer ->
        fileListeners.forEach { listener ->
            observer.removeListener(listener)
        }
    }
    fileListeners.clear()
}
