package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.ressources.FileMonitor
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor
import java.io.File


data class ProgramChangeListener(val program: Program<*>, val listener: FileAlterationListenerAdaptor)

class ProgramChangeListenerManager(
    private val fileMonitor: FileMonitor,
) {

    private val programChangeListeners: MutableList<ProgramChangeListener> = mutableListOf()

    fun Program<*>.reregisterListener(onReload: (File) -> Unit) {
        removeOldListeners()
        registerListener(onReload)
    }

    fun Program<*>.registerListener(onReload: (File) -> Unit) {
        val shaderFiles = shaders
            .map { it.source }
            .filterIsInstance<FileBasedCodeSource>()
            .flatMap { listOf(it.file) + it.includedFiles}

        val listener = fileMonitor.registerFileChangeListener(
            shaderFiles,
//            { file -> file.name.startsWith("global") }
        ) { file ->
            println("""Reloading ${shaders.joinToString { it.name }}""")
            onReload(file)
        }
        programChangeListeners.add(ProgramChangeListener(this, listener))
    }

    fun Program<*>.removeOldListeners() {
        val listeners = programChangeListeners.filter { it.program == this }
        listeners.forEach {
            fileMonitor.unregisterListener(it.listener)
        }
        programChangeListeners.removeAll(listeners)
    }
}
