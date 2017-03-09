/*
 * Port	        Representation of OpenQueue Port.
 *
 *              This program is free software; you can redistribute it and/or
 *              modify it under the terms of the GNU General Public License
 *              as published by the Free Software Foundation; either version
 *              2 of the License, or (at your option) any later version.
 *
 * Authors:     Danushka Menikkumbura, <dmenikku@purdue.edu>
 */

import java.util.ArrayList;

public class Port extends Entity {
    private String name = "";
    private Statement queueSelect = null;
    private Statement schedPrio = null;
    private ArrayList<String> queues = new ArrayList();

    public Port(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Statement getQueueSelect() {
        return queueSelect;
    }

    public void setQueueSelect(Statement queueSelect) {
        this.queueSelect = queueSelect;
    }

    public Statement getSchedPrio() {
        return schedPrio;
    }

    public void setSchedPrio(Statement schedPrio) {
        this.schedPrio = schedPrio;
    }

    public void addQueue(String queue) {
        queues.add(queue);
    }

    public boolean isWellDefined() {
        return queueSelect != null && schedPrio != null && !queues.isEmpty();
    }
}
