package de.hanno.hpengine.ressources

import org.apache.commons.io.monitor.FileAlterationMonitor
import org.apache.commons.io.monitor.FileAlterationObserver
import java.io.File


object FileMonitor {
    private val directoryObservers = mutableMapOf<File, FileAlterationObserver>()
    val monitor: FileAlterationMonitor = FileAlterationMonitor(500).apply {
        start()
    }

    fun addOnFileChangeListener(file: File, overwriteShouldReload: (File) -> Boolean = { false }, action: (File) -> Unit): OnFileChangeListener {
        val fileObserver = directoryObservers.getOrPut(file.parentFile) {
            FileAlterationObserver(file.parent).apply {
                initialize()
                monitor.addObserver(this)
            }
        }

        val listener: OnFileChangeListener = object: OnFileChangeListener() {
            fun shouldReload(changedFile: File): Boolean {
                return "${file.name}.${file.extension}".startsWith("${changedFile.name}.${changedFile.extension}")
            }

            override fun onFileChangeAction(arg0: File) {
                if(overwriteShouldReload(arg0) || shouldReload(arg0)) {
                    action(arg0)
                }
            }
        }
        fileObserver.addListener(listener)
        return listener
    }
}