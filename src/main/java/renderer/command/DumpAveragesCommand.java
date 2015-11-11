package renderer.command;

import engine.AppContext;
import util.stopwatch.GPUProfiler;

public class DumpAveragesCommand implements Command {

	int sampleCount;

	public DumpAveragesCommand() {
        this(1000);
	}
	public DumpAveragesCommand(int sampleCount) {
		this.sampleCount = sampleCount;
	}
	
	@Override
	public Result execute(AppContext appContext) {
		GPUProfiler.dumpAverages(sampleCount);
		
		return new Result() {
			@Override
			public boolean isSuccessful() {
				return true;
			}
		};
	}

}
