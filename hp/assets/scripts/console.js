Vector3f = Java.type('org.lwjgl.util.vector.Vector3f');
Quaternion = Java.type('org.lwjgl.util.vector.Quaternion');
Profiler = Java.type('main.util.stopwatch.GPUProfiler');
Command = Java.type('main.renderer.command.Command');
Result = Java.type('main.renderer.Result');
World = Java.type('main.World');
//for each(var probe in renderer.getEnvironmentProbeFactory().getProbes()) {	probe.move(new Vector3f(0,-10,0));}

var Timer = Java.type('java.util.Timer');
var eventLoop0 = new Timer('jsEventLoop0', false);
var eventLoop1 = new Timer('jsEventLoop1', false);
var eventLoop2 = new Timer('jsEventLoop2', false);

this.setTimeout = function(fn, afterfn, millis, times, eventLoop) {
	if(times >= 0) {
		fn(1);
	} else {
		if(afterfn) { afterfn(); }
		eventLoop.cancel();
		eventLoop.purge();
		return;
	}
 
	eventLoop.schedule(function() {
		setTimeout(fn, afterfn, millis, times-1, eventLoop);
	}, millis);
};

function moveForward(i) {
	world.getActiveCamera().move(new Vector3f(0,0,i));
}
function rotateLeft(i) {
	world.getActiveCamera().rotate(new Vector3f(0,1,0), i);
}
function toggleProfiling() {
	renderer.addCommand(new Command() {
		execute: function(world){
			Profiler.PRINTING_ENABLED = !Profiler.PRINTING_ENABLED;
			Profiler.PROFILING_ENABLED = !Profiler.PROFILING_ENABLED;
			return new Result() {
				isSuccessful : function() { return true; }
			};
		}
	});
}
function dumpAverages() {
	renderer.addCommand(new Command() {
		execute: function(world){
			Profiler.dumpAverages(1000);
			return new Result() {
				isSuccessful : function() { return true; }
			};
		}
	});
}
function rotateLight(i) {
	World.light.rotate(new Vector3f(1,0,0), i/200.0);
}

function finishBenchmark() {
	print('finished');
	toggleProfiling();
	dumpAverages();
}

function benchmark() {
	Profiler.resetCollectedTimes();
	
	world.getActiveCamera().setPosition(new Vector3f(-89, -2, 38))
	world.getActiveCamera().setOrientation(new Quaternion(0,1,0,0))
	toggleProfiling();
	setTimeout(moveForward, function() {}, 10, 9000, eventLoop0);
	setTimeout(rotateLeft, function() {}, 10, 9000, eventLoop1);
	setTimeout(rotateLight, finishBenchmark, 10, 9000, eventLoop2);
}

//benchmark();

