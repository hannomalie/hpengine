package de.hanno.hpengine.util.ressources

import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.monitor.FileAlterationMonitor
import org.apache.commons.io.monitor.FileAlterationObserver
import java.io.File


object FileMonitor {
    private val directoryObservers = mutableMapOf<File, FileAlterationObserver>()
    val monitor: FileAlterationMonitor = FileAlterationMonitor(500).apply {
        start()
    }

    @JvmOverloads
    fun addOnFileChangeListener(file: File, overwriteShouldReload: (File) -> Boolean = { false }, action: (File) -> Unit) {
        val fileObserver = directoryObservers.getOrPut(file.parentFile) {
            FileAlterationObserver(file.parent).apply {
                initialize()
                monitor.addObserver(this)
            }
        }

        val listener: OnFileChangeListener = object: OnFileChangeListener() {
            fun shouldReload(changedFile: File): Boolean {
                val fileName = FilenameUtils.getBaseName(changedFile.absolutePath)
                return file.name.startsWith(fileName)
            }

            override fun onFileChangeAction(arg0: File) {
                if(overwriteShouldReload(arg0) || shouldReload(arg0)) {
                    action(arg0)
                }
            }
        }
        fileObserver.addListener(listener)
    }
}