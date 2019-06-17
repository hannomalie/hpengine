package de.hanno.hpengine.util.ressources


import de.hanno.hpengine.engine.config.SimpleConfig
import org.apache.commons.io.monitor.FileAlterationMonitor
import org.apache.commons.io.monitor.FileAlterationObserver

class FileMonitor constructor(interval: Int) {

    var monitor: FileAlterationMonitor
    @Volatile
    var running = false

    init {
        monitor = FileAlterationMonitor(interval.toLong())
    }

    fun add(observer: FileAlterationObserver) {
        if (!SimpleConfig.isUseFileReloadXXX) {
            return
        }
        if (running) {
            try {
                observer.initialize()
                monitor.addObserver(observer)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

    fun checkAndNotify() {
        if (!SimpleConfig.isUseFileReloadXXX) {
            return
        }
        if (!FileMonitor.instance!!.running) {
            return
        }
        monitor.observers.forEach { o -> o.checkAndNotify() }
    }

    companion object {

        @JvmField var instance: FileMonitor? = null

        init {
            instance = FileMonitor(500)
            try {
                if (SimpleConfig.isUseFileReloadXXX) {
                    instance!!.monitor.start()
                    instance!!.running = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

}
