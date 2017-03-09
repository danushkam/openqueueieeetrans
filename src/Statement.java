/*
 * Statement    Base class for a statement (inline/routine) in OpenQueue.
 *
 *              This program is free software; you can redistribute it and/or
 *              modify it under the terms of the GNU General Public License
 *              as published by the Free Software Foundation; either version
 *              2 of the License, or (at your option) any later version.
 *
 * Authors:     Danushka Menikkumbura, <dmenikku@purdue.edu>
 */

public abstract class Statement {
    protected RoutineType type;

    public Statement(RoutineType type) {
        this.type = type;
    }

    abstract String getStatement();
}
