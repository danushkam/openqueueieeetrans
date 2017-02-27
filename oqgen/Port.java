import java.util.ArrayList;

/**
 * Created by danushka on 2/22/17.
 */
public class Port extends Entity {
    private String name = "";
    private RoutineCall queueSelect = null;
    private RoutineCall schedPrio = null;
    private ArrayList<String> queues = new ArrayList();

    public Port(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public RoutineCall getQueueSelect() {
        return queueSelect;
    }

    public void setQueueSelect(RoutineCall queueSelect) {
        this.queueSelect = queueSelect;
    }

    public RoutineCall getSchedPrio() {
        return schedPrio;
    }

    public void setSchedPrio(RoutineCall schedPrio) {
        this.schedPrio = schedPrio;
    }

    public void addQueue(String queue) {
        queues.add(queue);
    }

    public boolean isWellDefined() {
        return queueSelect != null && schedPrio != null;
    }
}
