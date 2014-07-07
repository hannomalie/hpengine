package main.renderer.command;

import main.World;
import main.renderer.Result;
import main.renderer.command.InitMaterialCommand.MaterialResult;
import main.renderer.material.Material;
import main.renderer.material.MaterialFactory.MaterialInfo;

public class InitMaterialCommand implements Command<MaterialResult> {

	private Material material;
	
	public InitMaterialCommand(Material material) {
		this.material = material;
	}

	@Override
	public MaterialResult execute(World world) {
		material.init(world.getRenderer());
		MaterialResult result = new MaterialResult(material);
		return result;
	}

	public static class MaterialResult extends Result {
		public Material material;
		private boolean successful;
		
		public MaterialResult(Material material) {
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
