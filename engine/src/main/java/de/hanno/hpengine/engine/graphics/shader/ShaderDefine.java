package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.model.material.Material.MAP;

import java.util.EnumSet;

public enum ShaderDefine {
	
	DIFFUSE(MAP.DIFFUSE),
	NORMAL(MAP.NORMAL),
	SPECULAR(MAP.SPECULAR),
	OCCLUSION(MAP.DISPLACEMENT),
	HEIGHT(MAP.HEIGHT),
	REFLECTION(MAP.REFLECTION),
	ROUGHNESS(MAP.ROUGHNESS);

	private String defineString;
	private MAP map;
	
	public String getDefineText() {
		return defineString;
	};
	
	ShaderDefine(MAP map) {
		this.map = map;
		this.defineString = "#define use_" + map.getShaderVariableName();
	}
	
	public static String getDefineString(EnumSet<ShaderDefine> defines) {
		if (defines == null) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		
		for (ShaderDefine shaderDefine : defines) {
			builder.append(shaderDefine.getDefineText());
			builder.append("\n");
		}
		return builder.toString();
	}

	public static String getGlobalDefinesString(Config config) {
		StringBuilder builder = new StringBuilder();

		appendWithSemicolonAndNewLine(builder, "const bool RAINEFFECT = " + (config.getEffects().getRainEffect() != 0.0));
		appendWithSemicolonAndNewLine(builder, "const bool MULTIPLE_DIFFUSE_SAMPLES = " + config.getQuality().isUseMultipleDiffuseSamples());
		appendWithSemicolonAndNewLine(builder, "const bool MULTIPLE_DIFFUSE_SAMPLES_PROBES = " + config.getQuality().isUseMultipleDiffuseSamplesProbes());
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_DIFFUSE = " + config.getQuality().isUseConetracingForDiffuse());
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_DIFFUSE_PROBES = " + config.getQuality().isUseConetracingForDiffuseProbes());
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_SPECULAR = " + config.getQuality().isUseConetracingForSpecular());
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_SPECULAR_PROBES = " + config.getQuality().isUseConetracingForSpecularProbes());
		appendWithSemicolonAndNewLine(builder, "const bool PRECOMPUTED_RADIANCE = " + config.getQuality().isUsePrecomputedRadiance());
		appendWithSemicolonAndNewLine(builder, "const bool SCATTERING = " + config.getEffects().isScattering());
		appendWithSemicolonAndNewLine(builder, "const bool CALCULATE_ACTUAL_RADIANCE = " + config.getQuality().isCalculateActualRadiance());
		appendWithSemicolonAndNewLine(builder, "const bool SSR_FADE_TO_SCREEN_BORDERS = " + config.getQuality().isSsrFadeToScreenBorders());
		appendWithSemicolonAndNewLine(builder, "const bool SSR_TEMPORAL_FILTERING = " + config.getQuality().isSsrTemporalFiltering());
		appendWithSemicolonAndNewLine(builder, "const bool USE_BLOOM = " + config.getEffects().isUseBloom());
		appendWithSemicolonAndNewLine(builder, "const bool USE_PCF = " + config.getQuality().isUsePcf());
		appendWithSemicolonAndNewLine(builder, "const bool USE_DPSM = " + config.getQuality().isUseDpsm());
		builder.append("\n");
		return builder.toString();
	}
	
	private static StringBuilder appendWithSemicolonAndNewLine(StringBuilder builder, String toAppend) {
		builder.append(toAppend);
		builder.append(";\n");
		return builder;
	}
}
