package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.graphics.renderer.command.InitMaterialCommand.MaterialResult;
import de.hanno.hpengine.engine.model.material.MaterialManager;
import de.hanno.hpengine.engine.model.material.SimpleMaterial;

public class InitMaterialCommand implements Command<MaterialResult> {

	private SimpleMaterial material;
	private MaterialManager materialManager;

	public InitMaterialCommand(SimpleMaterial material, MaterialManager materialManager) {
		this.material = material;
		this.materialManager = materialManager;
	}

	@Override
	public MaterialResult execute() {
		return new MaterialResult(material);
	}

	public static class MaterialResult extends Result {
		public SimpleMaterial material;
		private boolean successful;
		
		public MaterialResult(SimpleMaterial material) {
			this.material = material;
			if(this.material != null) {
				successful = true;
			}
		}

		@Override
		public boolean isSuccessful() {
			return successful;
		}
	}
}
