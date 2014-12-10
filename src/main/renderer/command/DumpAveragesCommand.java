package main.renderer.command;

import main.World;
import main.renderer.Result;
import main.util.stopwatch.GPUProfiler;

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
