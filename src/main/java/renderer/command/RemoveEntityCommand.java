package renderer.command;

import engine.AppContext;
import engine.model.Entity;

public class RemoveEntityCommand implements Command {

	private Entity entity;

	public RemoveEntityCommand(Entity entity) {
		this.entity = entity;
	}
	
	@Override
	public Result execute(AppContext appContext) {
		boolean result = appContext.getScene().removeEntity(entity);
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
