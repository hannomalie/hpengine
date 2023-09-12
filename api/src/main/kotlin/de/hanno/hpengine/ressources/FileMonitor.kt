package de.hanno.hpengine.ressources

import de.hanno.hpengine.config.Config
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor
import org.apache.commons.io.monitor.FileAlterationMonitor
import org.apache.commons.io.monitor.FileAlterationObserver
import org.koin.core.annotation.Single
import java.io.File


@Single
class FileMonitor(val config: Config) {

    private val monitor = FileAlterationMonitor(500).apply {
        start()
    }

    private val engineDirObserver = FileAlterationObserver(config.directories.engineDir._baseDir).apply {
        initialize()
        monitor.addObserver(this)
    }
    private val gameDirObserver = FileAlterationObserver(config.directories.gameDir._baseDir).apply {
        initialize()
        monitor.addObserver(this)
    }

    fun registerFileChangeListener(
        files: List<File>,
        overwriteShouldReload: (File) -> Boolean = { false },
        action: (File) -> Unit
    ): FileAlterationListenerAdaptor {
        val listener = object : FileAlterationListenerAdaptor() {
            fun shouldReload(changedFile: File): Boolean = files.any { file ->
                "${file.name}.${file.extension}".startsWith("${changedFile.name}.${changedFile.extension}")
            }

            override fun onFileChange(arg0: File) {
                if (overwriteShouldReload(arg0) || shouldReload(arg0)) {
                    action(arg0)
                }
            }
        }
        // TODO: Is there a way to not register both cases somehow?
        engineDirObserver.addListener(listener)
        gameDirObserver.addListener(listener)
        return listener
    }

    fun registerFileChangeListener(
        file: File,
        overwriteShouldReload: (File) -> Boolean = { false },
        action: (File) -> Unit
    ): FileAlterationListenerAdaptor {

        val listener = object : FileAlterationListenerAdaptor() {
            fun shouldReload(changedFile: File): Boolean {
                return "${file.name}.${file.extension}".startsWith("${changedFile.name}.${changedFile.extension}")
            }

            override fun onFileChange(arg0: File) {
                if (overwriteShouldReload(arg0) || shouldReload(arg0)) {
                    action(arg0)
                }
            }
        }
        // TODO: Is there a way to not register both cases somehow?
        engineDirObserver.addListener(listener)
        gameDirObserver.addListener(listener)
        return listener
    }

    fun unregisterListener(listener: FileAlterationListenerAdaptor) {
        engineDirObserver.removeListener(listener)
        gameDirObserver.removeListener(listener)
    }
}