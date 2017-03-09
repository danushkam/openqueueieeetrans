/*
 * Routine      Representation of external routine call.
 *
 *              This program is free software; you can redistribute it and/or
 *              modify it under the terms of the GNU General Public License
 *              as published by the Free Software Foundation; either version
 *              2 of the License, or (at your option) any later version.
 *
 * Authors:     Danushka Menikkumbura, <dmenikku@purdue.edu>
 */

public class Routine {
    private RoutineType type;
    private String name;

    public Routine(RoutineType type, String name) {
        this.type = type;
        this.name = name;
    }

    public RoutineType getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}
