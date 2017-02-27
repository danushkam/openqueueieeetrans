/**
 * Created by danushka on 2/22/17.
 */
public class Routine {
    public static final int MAX_PARAM = 10;

    private RoutineType type;
    private String name;
    private ParamType[] paramTypes = new ParamType[MAX_PARAM];
    private int paramCount;

    public Routine(RoutineType type, String name) {
        this.type = type;
        this.name = name;
        paramCount = 0;
    }

    public RoutineType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setNextParamType(ParamType type) {
        paramTypes[paramCount++] = type;
    }

    public ParamType[] getParamTypes() {
        return paramTypes;
    }

    public int getParamCount() {
        return paramCount;
    }
}
