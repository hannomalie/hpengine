package de.hanno.hpengine.engine.graphics.shader;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MAP;

import java.util.EnumSet;

public enum ShaderDefine {
	
	DIFFUSE(MAP.DIFFUSE),
	NORMAL(MAP.NORMAL),
	SPECULAR(MAP.SPECULAR),
	OCCLUSION(MAP.OCCLUSION),
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

		appendWithSemicolonAndNewLine(builder, "const bool RAINEFFECT = " + (config.getRainEffect() != 0.0));
		appendWithSemicolonAndNewLine(builder, "const bool MULTIPLE_DIFFUSE_SAMPLES = " + config.isUseMultipleDiffuseSamples());
		appendWithSemicolonAndNewLine(builder, "const bool MULTIPLE_DIFFUSE_SAMPLES_PROBES = " + config.isUseMultipleDiffuseSamplesProbes());
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_DIFFUSE = " + config.isUseConetracingForDiffuse());
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_DIFFUSE_PROBES = " + config.isUseConetracingForDiffuseProbes());
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_SPECULAR = " + config.isUseConetracingForSpecular());
		appendWithSemicolonAndNewLine(builder, "const bool USE_CONETRACING_FOR_SPECULAR_PROBES = " + config.isUseConetracingForSpecularProbes());
		appendWithSemicolonAndNewLine(builder, "const bool PRECOMPUTED_RADIANCE = " + config.isUsePrecomputedRadiance());
		appendWithSemicolonAndNewLine(builder, "const bool SCATTERING = " + config.isScattering());
		appendWithSemicolonAndNewLine(builder, "const bool CALCULATE_ACTUAL_RADIANCE = " + config.isCalculateActualRadiance());
		appendWithSemicolonAndNewLine(builder, "const bool SSR_FADE_TO_SCREEN_BORDERS = " + config.isSsrFadeToScreenBorders());
		appendWithSemicolonAndNewLine(builder, "const bool SSR_TEMPORAL_FILTERING = " + config.isSsrTemporalFiltering());
		appendWithSemicolonAndNewLine(builder, "const bool USE_BLOOM = " + config.isUseBloom());
		appendWithSemicolonAndNewLine(builder, "const bool USE_PCF = " + config.isUsePcf());
		appendWithSemicolonAndNewLine(builder, "const bool USE_DPSM = " + config.isUseDpsm());
		builder.append("\n");
		return builder.toString();
	}
	
	private static StringBuilder appendWithSemicolonAndNewLine(StringBuilder builder, String toAppend) {
		builder.append(toAppend);
		builder.append(";\n");
		return builder;
	}
}
