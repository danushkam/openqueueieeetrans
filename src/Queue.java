/*
 * Queue        Representation of OpenQueue Queue.
 *
 *              This program is free software; you can redistribute it and/or
 *              modify it under the terms of the GNU General Public License
 *              as published by the Free Software Foundation; either version
 *              2 of the License, or (at your option) any later version.
 *
 * Authors:     Danushka Menikkumbura, <dmenikku@purdue.edu>
 */

public class Queue extends Entity {
    private String name = "";
    private int size = 0;
    private Statement admPrio = null;
    private Statement congestion = null;
    private Statement congAction = null;
    private Statement procPrio = null;

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

    public Statement getAdmPrio() {
        return admPrio;
    }

    public void setAdmPrio(Statement admPrio) {
        this.admPrio = admPrio;
    }

    public Statement getCongestion() {
        return congestion;
    }

    public void setCongestion(Statement congestion) {
        this.congestion = congestion;
    }

    public Statement getCongAction() {
        return congAction;
    }

    public void setCongAction(Statement congAction) {
        this.congAction = congAction;
    }

    public Statement getProcPrio() {
        return procPrio;
    }

    public void setProcPrio(Statement procPrio) {
        this.procPrio = procPrio;
    }

    public boolean isWellDefined() {
        return admPrio != null && congestion != null && congAction != null && procPrio != null;
    }
}
