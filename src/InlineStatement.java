/*
 * InlineStatement  Representation of OpenQueue inline statement.
 *
 *                  This program is free software; you can redistribute it and/or
 *                  modify it under the terms of the GNU General Public License
 *                  as published by the Free Software Foundation; either version
 *                  2 of the License, or (at your option) any later version.
 *
 * Authors:         Danushka Menikkumbura, <dmenikku@purdue.edu>
 */

public class InlineStatement extends Statement {
    private String statement;

    public InlineStatement(String statement, RoutineType type) {
        super(type);
        this.statement = statement;
    }

    @Override
    public String getStatement() {
        return statement;
    }

    /**
     * Validate an expression and return corresponding statement in C
     *  E.g. inline{Queue.length == 1024}
     *
     * @param exp Inline expression
     * @return C statement if the expression is valid or null otherwise
     */
    public static String validate(String exp) {
        if (!exp.startsWith("inline"))
            return null;

        int beginIndex = exp.indexOf('{');
        int endIndex = exp.indexOf('}');
        if (beginIndex == -1 || endIndex == -1)
            return null;

        exp = exp.substring(beginIndex + 1, endIndex);

        String[] tokens= exp.split(" ");
        String cExpression = null;

        if (tokens.length == 1) {
            cExpression = validateSubExpr(tokens[0].trim());
        } else if (tokens.length == 3) {
            String lhs = validateSubExpr(tokens[0].trim());
            if (lhs == null)
                return null;

            String rhs = validateSubExpr(tokens[2].trim());
            if (rhs == null)
                return null;

            String op = "";
            switch (tokens[1].trim()) {
                case "==":
                case "!=":
                case "<":
                case "<=":
                case ">":
                case ">=":
                    op = tokens[1].trim();
            }

            if (op.isEmpty())
                return null;

            cExpression = "(" + lhs + " " + op + " " + rhs + ")";
        }

        return cExpression;
    }

    /**
     * Vaidate a sub-expression to get the equivalent expression in C.
     * Currently we support a limited set of Queue and Packet attributes.
     *
     * @param subExpr Sub-expression to be validated
     * @return Corresponding C expression if the sub-expression is valid or null otherwise
     */
    private static String validateSubExpr(String subExpr) {
        String cExpression = null;

        if (subExpr.startsWith("Queue")) {
            String[] tokens = subExpr.split("\\.");
            if (tokens.length != 2)
                return null;

            String attr = "";
            switch (tokens[1].trim()) {
                case "max_len":
                case "len":
                case "dropped":
                case "total":
                    attr = tokens[1].trim();
            }

            if (attr.isEmpty())
                return null;

            cExpression = "queue->" + attr;
        } else if (subExpr.startsWith("Packet")) {
            String[] tokens = subExpr.split("\\.");
            if (tokens.length != 2)
                return null;

            String attr = "";
            switch (tokens[1].trim()) {
                case "tos":
                case "tot_len":
                case "id":
                case "ttl":
                case "protocol":
                case "saddr":
                case "daddr":
                    attr = tokens[1].trim();
            }

            if (attr.isEmpty())
                return null;

            cExpression = "((struct iphdr *)skb_header_pointer(skb, 0, 0, NULL))->" + attr;
        } else {
            try {
                cExpression = "";
                cExpression += (int)Double.parseDouble(subExpr.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return cExpression;
    }
}
