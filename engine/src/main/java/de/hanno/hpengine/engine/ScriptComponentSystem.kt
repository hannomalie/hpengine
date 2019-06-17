package de.hanno.hpengine.engine

import de.hanno.hpengine.engine.component.ScriptComponent
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.util.ressources.ReloadOnFileChangeListener
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.monitor.FileAlterationMonitor
import org.apache.commons.io.monitor.FileAlterationObserver
import java.io.File

class ScriptComponentSystem(val engine: Engine<*>): SimpleComponentSystem<ScriptComponent>(ScriptComponent::class.java, factory = { TODO("not implemented")}) {

    private val directoryObservers = mutableMapOf<File, FileAlterationObserver>()
    private val monitor = FileAlterationMonitor(500).apply {
        start()
    }

    override fun addComponent(component: ScriptComponent) = with(component) {
        if (codeSource.isFileBased) {
            val fileObserver = directoryObservers.getOrPut(codeSource.file.parentFile) {
                FileAlterationObserver(codeSource.file.parent).apply {
                    initialize()
                    monitor.addObserver(this)
                }
            }

            val listener: ReloadOnFileChangeListener<ScriptComponent> = object: ReloadOnFileChangeListener<ScriptComponent>(this) {
                override fun shouldReload(changedFile: File): Boolean {
                    val fileName = FilenameUtils.getBaseName(changedFile.absolutePath)
                    return codeSource.isFileBased && codeSource.filename.startsWith(fileName)
                }
            }

            fileObserver.addListener(listener)
        }
        super.addComponent(component)
    }
}