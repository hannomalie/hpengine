package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.model.material.Material.MAP
import java.util.EnumSet
import java.lang.StringBuilder

enum class ShaderDefine(private val map: MAP) {
    DIFFUSE(MAP.DIFFUSE), NORMAL(MAP.NORMAL), SPECULAR(MAP.SPECULAR), OCCLUSION(MAP.DISPLACEMENT), HEIGHT(MAP.HEIGHT), REFLECTION(
        MAP.REFLECTION
    ),
    ROUGHNESS(MAP.ROUGHNESS);

    val defineText: String = "#define use_" + map.shaderVariableName

    companion object {
        fun getDefineString(defines: EnumSet<ShaderDefine>?): String {
            if (defines == null) {
                return ""
            }
            val builder = StringBuilder()
            for (shaderDefine in defines) {
                builder.append(shaderDefine.defineText)
                builder.append("\n")
            }
            return builder.toString()
        }

        fun getGlobalDefinesString(config: Config): String {
            val builder = StringBuilder()
            appendWithSemicolonAndNewLine(
                builder,
                "const bool RAINEFFECT = " + (config.effects.rainEffect.toDouble() != 0.0)
            )
            appendWithSemicolonAndNewLine(
                builder,
                "const bool MULTIPLE_DIFFUSE_SAMPLES = " + config.quality.isUseMultipleDiffuseSamples
            )
            appendWithSemicolonAndNewLine(
                builder,
                "const bool MULTIPLE_DIFFUSE_SAMPLES_PROBES = " + config.quality.isUseMultipleDiffuseSamplesProbes
            )
            appendWithSemicolonAndNewLine(
                builder,
                "const bool USE_CONETRACING_FOR_DIFFUSE = " + config.quality.isUseConetracingForDiffuse
            )
            appendWithSemicolonAndNewLine(
                builder,
                "const bool USE_CONETRACING_FOR_DIFFUSE_PROBES = " + config.quality.isUseConetracingForDiffuseProbes
            )
            appendWithSemicolonAndNewLine(
                builder,
                "const bool USE_CONETRACING_FOR_SPECULAR = " + config.quality.isUseConetracingForSpecular
            )
            appendWithSemicolonAndNewLine(
                builder,
                "const bool USE_CONETRACING_FOR_SPECULAR_PROBES = " + config.quality.isUseConetracingForSpecularProbes
            )
            appendWithSemicolonAndNewLine(
                builder,
                "const bool PRECOMPUTED_RADIANCE = " + config.quality.isUsePrecomputedRadiance
            )
            appendWithSemicolonAndNewLine(builder, "const bool SCATTERING = " + config.effects.isScattering)
            appendWithSemicolonAndNewLine(
                builder,
                "const bool CALCULATE_ACTUAL_RADIANCE = " + config.quality.isCalculateActualRadiance
            )
            appendWithSemicolonAndNewLine(
                builder,
                "const bool SSR_FADE_TO_SCREEN_BORDERS = " + config.quality.isSsrFadeToScreenBorders
            )
            appendWithSemicolonAndNewLine(
                builder,
                "const bool SSR_TEMPORAL_FILTERING = " + config.quality.isSsrTemporalFiltering
            )
            appendWithSemicolonAndNewLine(builder, "const bool USE_BLOOM = " + config.effects.isUseBloom)
            appendWithSemicolonAndNewLine(builder, "const bool USE_PCF = " + config.quality.isUsePcf)
            appendWithSemicolonAndNewLine(builder, "const bool USE_DPSM = " + config.quality.isUseDpsm)
            builder.append("\n")
            return builder.toString()
        }

        private fun appendWithSemicolonAndNewLine(builder: StringBuilder, toAppend: String): StringBuilder {
            builder.append(toAppend)
            builder.append(";\n")
            return builder
        }
    }

}