package de.hanno.hpengine.util.ressources

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor
import java.io.File

abstract class OnFileChangeListener : FileAlterationListenerAdaptor() {
    override fun onFileChange(arg0: File) {
        onFileChangeAction(arg0)
    }

    abstract fun onFileChangeAction(arg0: File)
}