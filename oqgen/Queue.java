/**
 * Created by danushka on 2/22/17.
 */
public class Queue extends Entity {
    private String name = "";
    private int size = 0;
    private RoutineCall admPrio = null;
    private RoutineCall congestion = null;
    private RoutineCall congAction = null;
    private RoutineCall procPrio = null;

    public Queue(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public RoutineCall getAdmPrio() {
        return admPrio;
    }

    public void setAdmPrio(RoutineCall admPrio) {
        this.admPrio = admPrio;
    }

    public RoutineCall getCongestion() {
        return congestion;
    }

    public void setCongestion(RoutineCall congestion) {
        this.congestion = congestion;
    }

    public RoutineCall getCongAction() {
        return congAction;
    }

    public void setCongAction(RoutineCall congAction) {
        this.congAction = congAction;
    }

    public RoutineCall getProcPrio() {
        return procPrio;
    }

    public void setProcPrio(RoutineCall procPrio) {
        this.procPrio = procPrio;
    }

    public boolean isWellDefined() {
        return admPrio != null && congestion != null && congAction != null && procPrio != null;
    }
}
