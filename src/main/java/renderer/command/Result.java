package renderer.command;

public class Result<RETURN_TYPE> {

    private RETURN_TYPE object;

    public Result(){ }

    public Result(RETURN_TYPE object) {
        this.object = object;
    }
	public boolean isSuccessful() { return true; };

    public RETURN_TYPE get() {
        return object;
    }

}
