package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.config.NoUnloading
import de.hanno.hpengine.config.Unloading
import de.hanno.hpengine.engine.graphics.imgui.floatInput
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.texture.*
import imgui.ImGui
import java.util.concurrent.TimeUnit

fun textureManagerGrid(
    config: Config,
    graphicsApi: GraphicsApi,
    textureManagerBaseSystem: TextureManagerBaseSystem
) {
    floatInput("Mip bias decrease per second", config.performance::mipBiasDecreasePerSecond, 0.1f, 20f)

    floatInput("Unload bias in seconds", config.performance::unloadBiasInSeconds, 0.1f, 20f)
    floatInput("Unload distance", config.performance::unloadDistance, 1f, 300f)
    ImGui.text("-----")

    graphicsApi.pixelBufferObjectPool.buffers.forEachIndexed { index, it ->
        ImGui.text("PBO $index uploading: ${it.uploading}")
    }
    ImGui.text("Currently loading: ${graphicsApi.pixelBufferObjectPool.currentJobs.size}")
    graphicsApi.pixelBufferObjectPool.currentJobs.forEach { (key, _) ->
        if(key is DynamicFileBasedTexture2D) {
            ImGui.text(key.path)
        }
    }
    ImGui.text("-----")

    if (ImGui.beginCombo("Texture unload strategy", config.performance.textureUnloadStrategy.toString())) {
        (listOf(NoUnloading) + (0..<6).map { Unloading(it) }).forEach {
            val selected = config.performance.textureUnloadStrategy == it
            if (ImGui.selectable(it.toString(), selected)) {
                config.performance.textureUnloadStrategy = it
            }
            if (selected) {
                ImGui.setItemDefaultFocus()
            }
        }
        ImGui.endCombo()
    }

    ImGui.text("-----")

    textureManagerBaseSystem.fileBasedTextures.values.filterIsInstance<DynamicFileBasedTexture2D>().forEach {
        val postfix = when (val usageTimeStamp = graphicsApi.getHandleUsageTimeStamp(it)) {
            null -> "(never used)"
            else -> {
                val notUsedForNanos = System.nanoTime() - usageTimeStamp
                "(unused ${TimeUnit.NANOSECONDS.toMillis(notUsedForNanos)} ms)"
            }
        }
        val postfixDistance = when (val distance = graphicsApi.getHandleUsageDistance(it)) {
            null -> ""
            else -> {
                "($distance)"
            }
        }

        ImGui.text("${it.file.nameWithoutExtension}$postfix$postfixDistance")
    }

    ImGui.text("-----")

    if (ImGui.button("Reload all dynamic handles")) {
        textureManagerBaseSystem.fileBasedTextures.values.filterIsInstance<DynamicHandle<*>>().forEach { texture ->
            texture.uploadState = UploadState.Unloaded
        }
    }
    if (ImGui.button("Reload all static handles")) {
        textureManagerBaseSystem.fileBasedTextures.values.filterIsInstance<StaticHandle<*>>().forEach { texture ->
            texture.uploadState = UploadState.Unloaded
        }
    }
    if (ImGui.button("Reload all dynamic handles (${config.performance.textureUnloadStrategy})")) {
        textureManagerBaseSystem.fileBasedTextures.values.filterIsInstance<DynamicHandle<*>>().forEach { texture ->
            texture.uploadState = UploadState.Unloaded
        }
    }

    ImGui.text("Texture Pool:")
    textureManagerBaseSystem.texturePool.forEach {
        ImGui.text(it.path)
    }
    ImGui.text("Texture with handle:")
    textureManagerBaseSystem.fileBasedTextures.values.filterIsInstance<DynamicFileBasedTexture2D>().filter { it.texture != null }.forEach {
        ImGui.text(it.path)
    }
}