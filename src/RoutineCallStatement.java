/*
 * RoutineCallStatement Representation of OpenQueue external routine call.
 *
 *                      This program is free software; you can redistribute it and/or
 *                      modify it under the terms of the GNU General Public License
 *                      as published by the Free Software Foundation; either version
 *                      2 of the License, or (at your option) any later version.
 *
 * Authors:             Danushka Menikkumbura, <dmenikku@purdue.edu>
 */

public class RoutineCallStatement extends Statement {
    private String name;
    private double[] params;

    public RoutineCallStatement(String name, RoutineType type) {
        super(type);
        this.name = name;
        params = null;
    }

    public String getName() {
        return name;
    }

    public double[] getParams() {
        return params;
    }

    public void setParams(double[] params) {
        this.params = params;
    }

    @Override
    public String getStatement() {
        String statement = "";

        switch (type) {
            case CONGESTION_CONDITION:
                statement += name + "(queue";
                break;
            case CONGESTION_ACTION:
                statement += name + "(queue, skb";
                break;
            case ADMISSION_PRIORITY:
                statement += name + "(skb";
                break;
            case PROCESSING_PRIORITY:
                statement += name + "(skb";
                break;
            case QUEUE_SELECTOR:
                statement += name + "(sch, skb";
                break;
            case SCHEDULING_PRIORITY:
                statement += name + "(sch";
                break;
        }

        if (!statement.isEmpty()) {
            if (params != null && params.length != 0) {
                statement += ", " + params.length;
                for (int i = 0; i < params.length; i++)
                    statement += ", " + params[i];
                statement += ")";
            } else {
                statement += ", 0)";
            }
        }

        return statement;
    }
}
