package renderer.command;

import engine.World;
import engine.model.Entity;

public class RemoveEntityCommand implements Command {

	private Entity entity;

	public RemoveEntityCommand(Entity entity) {
		this.entity = entity;
	}
	
	@Override
	public Result execute(World world) {
		boolean result = world.getScene().removeEntity(entity);
		if (result) {
			return new Result() {
				@Override
				public boolean isSuccessful() {
					return true;
				}
			};
		} else {
			return new Result() {
				@Override
				public boolean isSuccessful() {
					return false;
				}
			};
		}
	}
}
