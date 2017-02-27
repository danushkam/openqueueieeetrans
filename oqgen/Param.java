/**
 * Created by danushka on 2/26/17.
 */
public class Param {
    private ParamType type;
    private String value;

    public Param(ParamType type, String value) {
        this.type = type;
        this.value = value;
    }

    public ParamType getType() {
        return type;
    }

    public void setType(ParamType type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
