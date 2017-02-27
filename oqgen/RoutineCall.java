/**
 * Created by danushka on 2/26/17.
 */
public class RoutineCall {
    private String name;
    private Param[] params;

    public RoutineCall(String name) {
        this.name = name;
        params = null;
    }

    public String getName() {
        return name;
    }

    public Param[] getParams() {
        return params;
    }

    public void setParams(Param[] params) {
        this.params = params;
    }
}
