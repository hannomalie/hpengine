package renderer.command;

import engine.World;
import renderer.Result;
import util.stopwatch.GPUProfiler;

public class DumpAveragesCommand implements Command {

	int sampleCount;

	public DumpAveragesCommand() {
	}
	public DumpAveragesCommand(int sampleCount) {
		this.sampleCount = sampleCount;
	}
	
	@Override
	public Result execute(World world) {
		GPUProfiler.dumpAverages(sampleCount);
		
		return new Result() {
			@Override
			public boolean isSuccessful() {
				return true;
			}
		};
	}

}