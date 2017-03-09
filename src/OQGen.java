/*
 * OQGen	    Main class that drives the whole code generation operation.
 *
 *              This program is free software; you can redistribute it and/or
 *              modify it under the terms of the GNU General Public License
 *              as published by the Free Software Foundation; either version
 *              2 of the License, or (at your option) any later version.
 *
 * Authors:     Danushka Menikkumbura, <dmenikku@purdue.edu>
 */

import java.io.*;
import java.util.Map;
import java.util.HashMap;

public class OQGen {
    private static final String OQ_CONG_FUNC = "@oq_cong_func";
    private static final String OQ_CONG_ACT_FUNC = "@oq_cong_act_func";
    private static final String OQ_ADMN_FUNC = "@oq_admn_func";
    private static final String OQ_PROC_FUNC = "@oq_proc_func";
    private static final String OQ_QSEL_FUNC = "@oq_qsel_func";
    private static final String OQ_SCHD_FUNC = "@oq_schd_func";

    private Map<String, Routine> routines = new HashMap<>();
    private Map<String, Queue> queues = new HashMap<>();
    private Port port;

    /**
     * Main method
     *
     * @param args Commandline arguments to the program
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: OQGen <OpenQueue Policy File>");
            return;
        }

        OQGen oqGen = new OQGen();

        // Parse policy file
        if (!oqGen.parsePolicyFile(args[0])) {
            showError("Error while parsing file: " + args[0], 0);
            return;
        }

        // Validate policy
        if (!oqGen.isWellDefined()) {
            showError("Policy is not well-defined: " + args[0], 0);
            return;
        }

        // Generate code
        if (!oqGen.generateCode()) {
            showError("Error while generating code for file: " + args[0], 0);
        }
    }

    /**
     * Parser OpenQueue policy file
     *
     * @param fileName Policy filename
     * @return true if parsed successfully or false otherwise
     */
    private boolean parsePolicyFile(String fileName) {
        String line;
        int lineNumber = 0;

        try {
            BufferedReader br = new BufferedReader(new FileReader(new File(fileName)));

            while ((line = br.readLine()) != null) {
                line = line.trim();
                lineNumber++;

                // Ignore comments
                if (line.startsWith("//") || line.isEmpty())
                    continue;

                // Process module imports
                if (line.startsWith("import")) {
                    String[] importLineTokens = line.split("\"");
                    if (importLineTokens.length != 2 || !importLineTokens[0].trim().equals("import")) {
                        showError("Invalid import statement: " + line, lineNumber);
                        return false;
                    }

                    if (!processImport(importLineTokens[1].trim())) {
                        showError("Error while processing import: " + line, lineNumber);
                        return false;
                    }
                } else { // Process rules
                    // Semicolon at the end
                    if (line.charAt(line.length() - 1) != ';') {
                        showError("';' expected at the end of line: " + line, lineNumber);
                        return false;
                    }

                    if (line.startsWith("Queue")) {
                        Queue queue;
                        if ((queue = validateQueueDeclaration(line)) == null) {
                            showError("Invalid Queue declaration: " + line, lineNumber);
                            return false;
                        }
                        queues.put(queue.getName(), queue);
                    } else if (line.startsWith("Port")) {
                        if ((port = validatePortDeclaration(line)) == null) {
                            showError("Invalid Port declaration: " + line, lineNumber);
                            return false;
                        }
                    } else {
                        String[] tokens = line.split(" = ");
                        if (tokens.length != 2) {
                            showError("Invalid assignment statement: " + line, lineNumber);
                            return false;
                        }

                        String lhs = tokens[0].trim();
                        String rhs = tokens[1].trim();
                        rhs = rhs.replace(';', ' ').trim();

                        tokens = lhs.split("\\.");
                        if (tokens.length != 2) {
                            showError("Invalid assignment statement: " + line, lineNumber);
                            return false;
                        }

                        String objName = tokens[0];
                        String attr = tokens[1];
                        Queue queue;

                        if ((queue = queues.get(objName)) != null) { // Queue
                            if (attr.equals("congestion")) {
                                if (!validateFunctionAssignment(queue, rhs, RoutineType.CONGESTION_CONDITION)) {
                                    showError("Invalid function assignment: " + line, lineNumber);
                                    return false;
                                }
                            } else if (attr.equals("congAction")) {
                                if (!validateFunctionAssignment(queue, rhs, RoutineType.CONGESTION_ACTION)) {
                                    showError("Invalid function assignment: " + line, lineNumber);
                                    return false;
                                }
                            } else if (attr.equals("admPrio")) {
                                if (!validateFunctionAssignment(queue, rhs, RoutineType.ADMISSION_PRIORITY)) {
                                    showError("Invalid function assignment: " + line, lineNumber);
                                    return false;
                                }
                            } else if (attr.equals("procPrio")) {
                                if (!validateFunctionAssignment(queue, rhs, RoutineType.PROCESSING_PRIORITY)) {
                                    showError("Invalid function assignment: " + line, lineNumber);
                                    return false;
                                }
                            } else {
                                showError("Invalid assignment statement: " + line, lineNumber);
                                return false;
                            }
                        } else if (port != null && port.getName().equals(objName)) { // Port
                            if (attr.equals("queueSelect")) {
                                if (!validateFunctionAssignment(port, rhs, RoutineType.QUEUE_SELECTOR)) {
                                    showError("Invalid function assignment: " + line, lineNumber);
                                    return false;
                                }
                            } else if (attr.equals("schedPrio")) {
                                if (!validateFunctionAssignment(port, rhs, RoutineType.SCHEDULING_PRIORITY)) {
                                    showError("Invalid function assignment: " + line, lineNumber);
                                    return false;
                                }
                            } else {
                                showError("Invalid assignment statement: " + line, lineNumber);
                                return false;
                            }
                        } else {
                            showError("Invalid assignment statement: " + line, lineNumber);
                            return false;
                        }
                    }
                }
            }

            return true;
        } catch (FileNotFoundException e) {
            showError("File not found: " + fileName, lineNumber);
        } catch (IOException e) {
            showError("Error while reading policy file: " + e.getMessage(), lineNumber);
        }

        return false;
    }

    /**
     * Check if the policy is well-defined
     *
     * @return True if well-defined false otherwise
     */
    private boolean isWellDefined() {
        if (port == null || !port.isWellDefined()) {
            showError("Port not well-defined", 0);
            return false;
        }

        for (Queue queue : queues.values()) {
            if (!queue.isWellDefined()) {
                showError("Queue not well-defined: " + queue.getName(), 0);
                return false;
            }
        }

        return true;
    }

    /**
     * Validate if a given funciton name is correct in terms of both name and type
     *
     * @param entity Entity the function attached to (Queue/Port)
     * @param rhs Name
     * @param functionType Type
     * @return True if it is a correct assignment or false otherwise
     */
    private boolean validateFunctionAssignment(Entity entity, String rhs, RoutineType functionType) {
        Statement routineCall;

        if (rhs.startsWith("inline")) {
            String statement = InlineStatement.validate(rhs);
            if (statement == null)
                return false;

            routineCall = new InlineStatement(statement, functionType);
        } else {
            // Resolve function name
            String funcName = rhs;
            if (funcName.contains("("))
                funcName = funcName.substring(0, funcName.indexOf('('));

            Routine routine = routines.get(funcName);
            if ((routine == null) || (routine.getType() != functionType))
                return false;

            routineCall = new RoutineCallStatement(funcName, functionType);

            // Set parameters (if any)
            if (rhs.contains("(") && rhs.contains(")")) {
                String params = rhs.substring(rhs.indexOf('(') + 1, rhs.indexOf(')'));
                String[] paramTokens = params.trim().split(",");

                if (paramTokens.length != 0) {
                    double[] paramVals = new double[paramTokens.length];

                    for (int i = 0; i < paramTokens.length; i++) {
                        try {
                            paramVals[i] = Double.parseDouble(paramTokens[i].trim());
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }

                    ((RoutineCallStatement)routineCall).setParams(paramVals);
                }
            }
        }

        if (functionType == RoutineType.CONGESTION_CONDITION)
            ((Queue) entity).setCongestion(routineCall);
        else if (functionType == RoutineType.CONGESTION_ACTION)
            ((Queue) entity).setCongAction(routineCall);
        else if (functionType == RoutineType.ADMISSION_PRIORITY)
            ((Queue) entity).setAdmPrio(routineCall);
        else if (functionType == RoutineType.PROCESSING_PRIORITY)
            ((Queue) entity).setProcPrio(routineCall);
        else if (functionType == RoutineType.QUEUE_SELECTOR)
            ((Port) entity).setQueueSelect(routineCall);
        else if (functionType == RoutineType.SCHEDULING_PRIORITY)
            ((Port) entity).setSchedPrio(routineCall);

        return true;
    }

    /**
     * Validate a queue declaration
     * E.g. Queue q1 = Queue(128);
     *
     * @param line Declaration
     * @return Valid Queue instance if valid or null otherwise
     */
    private Queue validateQueueDeclaration(String line) {
        String[] tokens = line.split("=");
        if (tokens.length != 2)
            return null;

        String lhs = tokens[0].trim();
        String rhs = tokens[1].trim();

        // LHS
        tokens = lhs.split(" ");
        if (tokens.length != 2)
            return null;

        if (!tokens[0].equals("Queue"))
            return null;

        // Name
        String name = tokens[1].trim();
        if (name.isEmpty())
            return null;

        // RHS
        tokens = rhs.split("\\(");
        if (tokens.length != 2)
            return null;

        if (!tokens[0].trim().equals("Queue"))
            return null;

        tokens = tokens[1].split("\\)");
        if (tokens.length != 2)
            return null;

        int size;
        try {
            size = Integer.parseInt(tokens[0].trim());
        } catch (NumberFormatException e) {
            return null;
        }

        Queue queue = new Queue(name);
        queue.setSize(size);

        return queue;
    }

    /**
     * Validate a port declaration
     * E.g. Port p1 = Port(q1, q2);
     *
     * @param line Declaration
     * @return Valid Port instance if valid or null otherwise
     */
    private Port validatePortDeclaration(String line) {
        String[] tokens = line.split("=");
        if (tokens.length != 2)
            return null;

        String lhs = tokens[0].trim();
        String rhs = tokens[1].trim();

        // LHS
        tokens = lhs.split(" ");
        if (tokens.length != 2)
            return null;

        if (!tokens[0].equals("Port"))
            return null;

        // Name
        String name = tokens[1].trim();
        if (name.isEmpty())
            return null;

        // RHS
        tokens = rhs.split("\\(");
        if (tokens.length != 2)
            return null;

        if (!tokens[0].trim().equals("Port"))
            return null;

        tokens = tokens[1].split("\\)");
        if (tokens.length != 2)
            return null;

        Port port = new Port(name);

        // Queue names
        String queueNames = tokens[0];
        tokens = queueNames.split(",");
        if (tokens.length == 0)
            return null;

        for (int i = 0; i < tokens.length; i++) {
            String queueName = tokens[i].trim();

            if (queues.get(queueName) == null)
                return null;

            port.addQueue(queueName);
        }

        return port;
    }

    /**
     * Process "import" statement
     *
     * @param fileName Name of the file
     * @return true if parsed successfully or false otherwise
     */
    private boolean processImport(String fileName) {
        String line;
        int lineNumber = 0;

        String[] tokens = fileName.split("\\.");
        if (tokens.length != 2)
            return false;

        if (!tokens[1].equals("h")) // Not a C header file
            return false;

        try {
            BufferedReader br = new BufferedReader(new FileReader(new File(fileName)));
            RoutineType nextRoutineType = RoutineType.UNDEFINED;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                lineNumber++;

                // Figure out next function type if this is an annotated line
                if (line.startsWith("//"))
                    nextRoutineType = getNextRoutineType(line);

                // Ignore comments and empty lines
                if (line.startsWith("//") || line.isEmpty())
                    continue;

                Routine routine = null;
                if (nextRoutineType == RoutineType.QUEUE_SELECTOR) {
                    routine = validateQueueSelectFunction(line);
                } else if (nextRoutineType == RoutineType.CONGESTION_CONDITION) {
                    routine = validateCongestionFunction(line);
                } else if (nextRoutineType == RoutineType.CONGESTION_ACTION) {
                    routine = validateCongestionActionFunction(line);
                } else if (nextRoutineType == RoutineType.ADMISSION_PRIORITY) {
                    routine = validateAdmissionFunction(line);
                } else if (nextRoutineType == RoutineType.PROCESSING_PRIORITY) {
                    routine = validateProcessingFunction(line);
                } else if (nextRoutineType == RoutineType.SCHEDULING_PRIORITY) {
                    routine = validateSchedulingFunction(line);
                }

                // Validate and store routine
                if (nextRoutineType != RoutineType.UNDEFINED) {
                    if (routine == null) {
                        showError("Invalid function: " + line, lineNumber);
                        return false;
                    }

                    if (routines.get(routine.getName()) != null) {
                        showError("Function with the same name already exists: " + line, lineNumber);
                        return false;
                    }

                    routines.put(routine.getName(), routine);
                    nextRoutineType = RoutineType.UNDEFINED;
                }
            }

            return true;
        } catch (FileNotFoundException e) {
            showError("File not found: " + fileName, lineNumber);
        } catch (IOException e) {
            showError("Error while reading policy file: " + e.getMessage(), lineNumber);
        }

        return false;
    }

    /**
     * Check if a Queue Select function has the correct signature
     * E.g. int select_admission_queue(struct Qdisc* sch, struct sk_buff* skb, int argc, ...);
     *
     * @param line Line of code
     * @return Valid Routine instance if its correct or null otherwise
     */
    private Routine validateQueueSelectFunction(String line) {
        String[] tokens = line.split("\\(");
        if (tokens.length != 2)
            return null;

        String typeName = tokens[0].trim();
        String[] typeNameTokens = typeName.split(" ");
        if ((typeNameTokens.length != 2) || (!typeNameTokens[0].trim().equals("int")))
            return null;

        String funcName = typeNameTokens[1];

        tokens = tokens[1].split("\\)");
        if (tokens.length != 2)
            return null;

        String params = tokens[0];
        String[] paramTokens = params.split(",");
        if (paramTokens.length != 4)
            return null;

        // Qdisc*
        String[] param1Tokens = paramTokens[0].trim().split(" ");
        if ((param1Tokens.length != 3) || !param1Tokens[0].trim().equals("struct") ||
                !param1Tokens[1].trim().equals("Qdisc*"))
            return null;

        // sk_buff*
        String[] param2Tokens = paramTokens[1].trim().split(" ");
        if ((param2Tokens.length != 3) || !param2Tokens[0].trim().equals("struct") ||
                !param2Tokens[1].trim().equals("sk_buff*"))
            return null;

        // Argc
        String[] param3Tokens = paramTokens[2].trim().split(" ");
        if ((param3Tokens.length != 2) || !param3Tokens[0].trim().equals("int"))
            return null;

        // ...
        if (!paramTokens[3].trim().equals("..."))
            return null;

        return new Routine(RoutineType.QUEUE_SELECTOR, funcName);
    }

    /**
     * Check if a Congestion function has the correct signature
     * E.g. bool my_congestion_condition(struct oq_queue* queue, int argc, ...);
     *
     * @param line Line of code
     * @return Valid Routine instance if its correct or null otherwise
     */
    private Routine validateCongestionFunction(String line) {
        String[] tokens = line.split("\\(");
        if (tokens.length != 2)
            return null;

        String typeName = tokens[0].trim();
        String[] typeNameTokens = typeName.split(" ");
        if ((typeNameTokens.length != 2) || (!typeNameTokens[0].trim().equals("bool")))
            return null;

        String funcName = typeNameTokens[1];

        tokens = tokens[1].split("\\)");
        if (tokens.length != 2)
            return null;

        String params = tokens[0];
        String[] paramTokens = params.split(",");
        if (paramTokens.length != 3)
            return null;

        // oq_queue*
        String[] param1Tokens = paramTokens[0].trim().split(" ");
        if ((param1Tokens.length != 3) || !param1Tokens[0].trim().equals("struct") ||
                !param1Tokens[1].trim().equals("oq_queue*"))
            return null;

        // Argc
        String[] param2Tokens = paramTokens[1].trim().split(" ");
        if ((param2Tokens.length != 2) || !param2Tokens[0].trim().equals("int"))
            return null;

        // ...
        if (!paramTokens[2].trim().equals("..."))
            return null;

        return new Routine(RoutineType.CONGESTION_CONDITION, funcName);
    }

    /**
     * Check if a Congestion Action function has the correct signature
     * E.g. int drop_tail(struct oq_queue* queue, struct sk_buff* skb, int argc, ...);
     *
     * @param line Line of code
     * @return Valid Routine instance if its correct or null otherwise
     */
    private Routine validateCongestionActionFunction(String line) {
        String[] tokens = line.split("\\(");
        if (tokens.length != 2)
            return null;

        String typeName = tokens[0].trim();
        String[] typeNameTokens = typeName.split(" ");
        if ((typeNameTokens.length != 2) || (!typeNameTokens[0].trim().equals("int")))
            return null;

        String funcName = typeNameTokens[1];

        tokens = tokens[1].split("\\)");
        if (tokens.length != 2)
            return null;

        String params = tokens[0];
        String[] paramTokens = params.split(",");
        if (paramTokens.length != 4)
            return null;

        // oq_queue*
        String[] param1Tokens = paramTokens[0].trim().split(" ");
        if ((param1Tokens.length != 3) || !param1Tokens[0].trim().equals("struct") ||
                !param1Tokens[1].trim().equals("oq_queue*"))
            return null;

        // sk_buff*
        String[] param2Tokens = paramTokens[1].trim().split(" ");
        if ((param2Tokens.length != 3) || !param2Tokens[0].trim().equals("struct") ||
                !param2Tokens[1].trim().equals("sk_buff*"))
            return null;

        // Argc
        String[] param3Tokens = paramTokens[2].trim().split(" ");
        if ((param3Tokens.length != 2) || !param3Tokens[0].trim().equals("int"))
            return null;

        // ...
        if (!paramTokens[3].trim().equals("..."))
            return null;

        return new Routine(RoutineType.CONGESTION_ACTION, funcName);
    }

    /**
     * Check if a Admission function has the correct signature
     * E.g. unsigned long my_adm_prio(struct sk_buff* skb, int argc, ...);
     *
     * @param line Line of code
     * @return Valid Routine instance if its correct or null otherwise
     */
    private Routine validateAdmissionFunction(String line) {
        String[] tokens = line.split("\\(");
        if (tokens.length != 2)
            return null;

        String typeName = tokens[0].trim();
        String[] typeNameTokens = typeName.split(" ");
        if ((typeNameTokens.length != 3) || !typeNameTokens[0].trim().equals("unsigned") ||
                !typeNameTokens[1].trim().equals("long"))
            return null;

        String funcName = typeNameTokens[2];

        tokens = tokens[1].split("\\)");
        if (tokens.length != 2)
            return null;

        String params = tokens[0];
        String[] paramTokens = params.split(",");
        if (paramTokens.length != 3)
            return null;

        // sk_buff*
        String[] param1Tokens = paramTokens[0].trim().split(" ");
        if ((param1Tokens.length != 3) || !param1Tokens[0].trim().equals("struct") ||
                !param1Tokens[1].trim().equals("sk_buff*"))
            return null;

        // Argc
        String[] param2Tokens = paramTokens[1].trim().split(" ");
        if ((param2Tokens.length != 2) || !param2Tokens[0].trim().equals("int"))
            return null;

        // ...
        if (!paramTokens[2].trim().equals("..."))
            return null;

        return new Routine(RoutineType.ADMISSION_PRIORITY, funcName);
    }

    /**
     * Check if a Processing function has the correct signature
     * E.g. unsigned long my_pro_prio(struct sk_buff *skb, int argc, ...);
     *
     * @param line Line of code
     * @return Valid Routine instance if its correct or null otherwise
     */
    private Routine validateProcessingFunction(String line) {
        String[] tokens = line.split("\\(");
        if (tokens.length != 2)
            return null;

        String typeName = tokens[0].trim();
        String[] typeNameTokens = typeName.split(" ");
        if ((typeNameTokens.length != 3) || !typeNameTokens[0].trim().equals("unsigned") ||
                !typeNameTokens[1].trim().equals("long"))
            return null;

        String funcName = typeNameTokens[2];

        tokens = tokens[1].split("\\)");
        if (tokens.length != 2)
            return null;

        String params = tokens[0];
        String[] paramTokens = params.split(",");
        if (paramTokens.length != 3)
            return null;

        // sk_buff*
        String[] param1Tokens = paramTokens[0].trim().split(" ");
        if ((param1Tokens.length != 3) || !param1Tokens[0].trim().equals("struct") ||
                !param1Tokens[1].trim().equals("sk_buff*"))
            return null;

        // Argc
        String[] param2Tokens = paramTokens[1].trim().split(" ");
        if ((param2Tokens.length != 2) || !param2Tokens[0].trim().equals("int"))
            return null;

        // ...
        if (!paramTokens[2].trim().equals("..."))
            return null;

        return new Routine(RoutineType.PROCESSING_PRIORITY, funcName);
    }

    /**
     * Check if a Scheduling function has the correct signature
     * E.g. int my_schd_prio(struct Qdisc *sch, int argc, ...);
     *
     * @param line Line of code
     * @return Valid Routine instance if its correct or null otherwise
     */
    private Routine validateSchedulingFunction(String line) {
        String[] tokens = line.split("\\(");
        if (tokens.length != 2)
            return null;

        String typeName = tokens[0].trim();
        String[] typeNameTokens = typeName.split(" ");
        if ((typeNameTokens.length != 2) || (!typeNameTokens[0].trim().equals("int")))
            return null;

        String funcName = typeNameTokens[1];

        tokens = tokens[1].split("\\)");
        if (tokens.length != 2)
            return null;

        String params = tokens[0];
        String[] paramTokens = params.split(",");
        if (paramTokens.length != 3)
            return null;

        // Qdisc*
        String[] param1Tokens = paramTokens[0].trim().split(" ");
        if ((param1Tokens.length != 3) || !param1Tokens[0].trim().equals("struct") ||
                !param1Tokens[1].trim().equals("Qdisc*"))
            return null;

        // Argc
        String[] param2Tokens = paramTokens[1].trim().split(" ");
        if ((param2Tokens.length != 2) || !param2Tokens[0].trim().equals("int"))
            return null;

        // ...
        if (!paramTokens[2].trim().equals("..."))
            return null;

        return new Routine(RoutineType.SCHEDULING_PRIORITY, funcName);
    }

    /**
     * Get annotation type
     *
     * @param annotationLine Line that defines the annotation
     * @return Annotation type
     */
    private RoutineType getNextRoutineType(String annotationLine) {
        if (annotationLine.contains(OQ_QSEL_FUNC))
            return RoutineType.QUEUE_SELECTOR;
        if (annotationLine.contains(OQ_CONG_FUNC))
            return RoutineType.CONGESTION_CONDITION;
        if (annotationLine.contains(OQ_CONG_ACT_FUNC))
            return RoutineType.CONGESTION_ACTION;
        if (annotationLine.contains(OQ_ADMN_FUNC))
            return RoutineType.ADMISSION_PRIORITY;
        if (annotationLine.contains(OQ_PROC_FUNC))
            return RoutineType.PROCESSING_PRIORITY;
        if (annotationLine.contains(OQ_SCHD_FUNC))
            return RoutineType.SCHEDULING_PRIORITY;

        return RoutineType.UNDEFINED;
    }

    /**
     * Generate policy module code
     */
    private boolean generateCode() {
        String modName = getModuleName(port.getName());
        showInfo("Generating policy module " + modName + " (" + port.getName() + ") ...");

        // Init module
        if (!initModule(modName)) {
            showError("Error while initializing policy module: " + modName, 0);
            return false;
        }

        // Generate module code
        String modFileName = "policy/" + modName + "/mod_" + modName + ".c";
        if (!generateModule(modName, modFileName)) {
            showError("Error while generating file: " + modFileName, 0);
            return false;
        }

        return true;
    }

    /**
     * Get qualified name of the module
     *
     * @param name Port name as defined in the policy file
     * @return Qualified name of the module
     */
    private String getModuleName(String name) {
        String modName = "oqp_";

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            modName += Character.isUpperCase(c) ? ("_" + Character.toLowerCase(c)) : c;
        }

        return modName;
    }

    /**
     * Initialize policy module. Create directory and Makefile.
     *
     * @param modName Qualified name of module
     * @return True if successful or false otherwise
     */
    private boolean initModule(String modName) {
        // Create policy directory if it does not exist
        String policyDir = "policy/" + modName;
        File dir = new File(policyDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                showError("Error while creating module directory: " + policyDir, 0);
                return false;
            }

            showInfo("Created policy directory \"" + policyDir + "\"");
        }

        // Create makefile
        boolean status = false;

        try {
            String fileName = policyDir + "/Makefile";
            BufferedWriter bw = new BufferedWriter(new FileWriter(new File(fileName)));

            bw.write("obj-m += " + modName +".o\n" +
                    modName + "-objs := mod_" + modName + ".o ../../routine/routines.o\n");

            bw.flush();
            bw.close();

            status = true;
        } catch (IOException e) {
            showError("Error while generating Makefile: " + e.getMessage(), 0);
        }

        return status;
    }

    /**
     * Generate policy module source
     *
     * @param modName Module name
     * @param fileName Filename of the policy module
     * @return True if the module is generated successfully or false otherwise
     */
    private boolean generateModule(String modName, String fileName) {
        boolean status = false;

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(new File(fileName)));

            // Header
            bw.write(generateHeader(modName));
            // Congestion function
            bw.write(generateCongFn(modName));
            // Congestion action function
            bw.write(generateCongActFn(modName));
            // Admission function
            bw.write(generateAdmnFn(modName));
            // Processing function
            bw.write(generateProcFn(modName));
            // Queue select function
            bw.write(generateQselcFn(modName));
            // Scheduling function
            bw.write(generateSchdFn(modName));
            // Init queue function
            bw.write(generateInitQueueFn(modName));
            // Init port function
            bw.write(generateInitPortFn(modName));
            // Footer
            bw.write(generateFooter(modName));

            bw.flush();
            bw.close();

            status = true;
        } catch (IOException e) {
            showError("Error while generating Makefile: " + e.getMessage(), 0);
        }

        return status;
    }

    /**
     * Generate header code
     *
     * @param modName Module name
     * @return Generated code
     */
    private String generateHeader(String modName) {
        String code = "";

        code += "/*\n" +
                " * mod_" + modName + ".c    OpenQueue policy " + port.getName() + "\n" +
                " *\n" +
                " *                  This program is free software; you can redistribute it and/or\n" +
                " *                  modify it under the terms of the GNU General Public License\n" +
                " *                  as published by the Free Software Foundation; either version\n" +
                " *                  2 of the License, or (at your option) any later version.\n" +
                " *\n" +
                " * Authors:         Danushka Menikkumbura, <dmenikku@purdue.edu>\n" +
                " */\n" +
                "\n" +
                "#include <linux/module.h>\n" +
                "#include <linux/kernel.h>\n" +
                "#include <linux/init.h>\n" +
                "#include <net/pkt_sched.h>\n" +
                "\n" +
                "#include \"../../include/qdisc/sch_openqueue.h\"\n" +
                "#include \"../../include/routine/routines.h\"\n" +
                "\n" +
                "#define TCQ_OQ_NO_QUEUES\t" + queues.size() + "\n" +
                "\n";

        return code;
    }

    /**
     * Generate congestion function
     *
     * @param modName Module name
     * @return Generated code
     */
     private String generateCongFn(String modName) {
         String code = "";

         code += "/* Congestion condition*/\n" +
                 "bool " + modName + "_cong_func(struct oq_queue *queue)\n" +
                 "{\n" +
                 "    bool cond = false;\n" +
                 "\n";

         for (Queue queue : queues.values()) {
             code += "    if (strncmp(queue->name, \"" + queue.getName() +"\", TCQ_OQ_NAME_LEN) == 0)\n" +
                     "        cond = " + queue.getCongestion().getStatement() + ";\n";
         }

         code += "\n" +
                 "    return cond;\n" +
                 "}\n\n";

         return code;
     }

    /**
     * Generate congestion action
     *
     * @param modName Module name
     * @return Generated code
     */
    private String generateCongActFn(String modName) {
        String code = "";

        code += "/* Congestion action */\n" +
                "int " + modName + "_cong_act_func(struct oq_queue *queue, struct sk_buff *skb)\n" +
                "{\n" +
                "    int status = 0;\n" +
                "\n";

        for (Queue queue : queues.values()) {
            code += "    if (strncmp(queue->name, \"" + queue.getName() + "\", TCQ_OQ_NAME_LEN) == 0)\n" +
                    "        status = " + queue.getCongAction().getStatement() + ";\n";
        }

        code += "\n" +
                "    return status;\n" +
                "}\n\n";

        return code;
    }

    /**
     * Generate admission function
     *
     * @param modName Module name
     * @return Generated code
     */
    private String generateAdmnFn(String modName) {
        String code = "";

        code += "/* Admission priority */\n" +
                "unsigned long " + modName + "_admn_func(struct oq_queue *queue, struct sk_buff *skb)\n" +
                "{\n" +
                "    unsigned long key = 0;\n" +
                "\n";

        for (Queue queue : queues.values()) {
            code += "    if (strncmp(queue->name, \"" + queue.getName() +"\", TCQ_OQ_NAME_LEN) == 0)\n" +
                    "        key = " + queue.getAdmPrio().getStatement() + ";\n";
        }

        code += "\n" +
                "    return key;\n" +
                "}\n\n";

        return code;
    }

    /**
     * Generate processing function
     *
     * @param modName Module name
     * @return Generated code
     */
    private String generateProcFn(String modName) {
        String code = "";

        code += "/* Processing priority */\n" +
                "unsigned long " + modName + "_proc_func(struct oq_queue *queue, struct sk_buff *skb)\n" +
                "{\n" +
                "    unsigned long key = 0;\n" +
                "\n";

        for (Queue queue : queues.values()) {
            code += "    if (strncmp(queue->name, \"" + queue.getName() + "\", TCQ_OQ_NAME_LEN) == 0)\n" +
                    "        key = " + queue.getProcPrio().getStatement() + ";\n";
        }

        code += "\n" +
                "    return key;\n" +
                "}\n\n";

        return code;
    }

    /**
     * Generate queue selection function
     *
     * @param modName Module name
     * @return Generated code
     */
    private String generateQselcFn(String modName) {
        String code = "";

        code += "/* Queue selection priority */\n" +
                "int " + modName +"_qselc_func(struct Qdisc *sch, struct sk_buff *skb)\n" +
                "{\n" +
                "    return " + port.getQueueSelect().getStatement() + ";\n" +
                "}\n\n";

        return code;
    }

    /**
     * Generate scheduling function
     *
     * @param modName Module name
     * @return Generated code
     */
    private String generateSchdFn(String modName) {
        String code = "";

        code += "/* Scheduling priority */\n" +
                "int " + modName + "_schd_func(struct Qdisc *sch)\n" +
                "{\n" +
                "    return " + port.getSchedPrio().getStatement() + ";\n" +
                "}\n\n";

        return code;
    }

    /**
     * Generate init queue function
     *
     * @param modName Module name
     * @return Generated code
     */
    private String generateInitQueueFn(String modName) {
        String code = "";

        code += "/* Initialize queue */\n" +
                "int init_queue(struct oq_queue *queue, const char* name, int max_len)\n" +
                "{\n" +
                "    if ((btree_init(&queue->admn_q) != 0) || (btree_init(&queue->proc_q) != 0))\n" +
                "        return -1;\n" +
                "\n" +
                "    queue->max_len = max_len;\n" +
                "    queue->len = 0;\n" +
                "    queue->dropped = 0;\n" +
                "    queue->total = 0;\n" +
                "    strncpy(queue->name, name, TCQ_OQ_NAME_LEN);\n" +
                "\n" +
                "    return 0;\n" +
                "}\n\n";

        return code;
    }

    /**
     * Generate init port function
     *
     * @param modName Module name
     * @return Generated code
     */
    private String generateInitPortFn(String modName) {
        String code = "";

        code += "/* Initialize policy */\n" +
                "int " + modName + "_init_port(struct oq_priv *priv)\n" +
                "{\n";

        int i = 0;
        for (Queue queue : queues.values()) {
            code += "    if (init_queue(&priv->queues[" + i +"], \"" + queue.getName() + "\", "
                    + queue.getSize() +") != 0)\n" +
                    "        return -ENOMEM;\n";
            i++;
        }

        code += "\n" +
                "    priv->num_q = TCQ_OQ_NO_QUEUES;\n" +
                "    strncpy(priv->port_name, \"" + port.getName() + "\", TCQ_OQ_NAME_LEN);\n" +
                "\n" +
                "    priv->cong_fn = " + modName + "_cong_func;\n" +
                "    priv->cong_act_fn = " + modName + "_cong_act_func;\n" +
                "    priv->admn_fn = " + modName + "_admn_func;\n" +
                "    priv->proc_fn = " + modName + "_proc_func;\n" +
                "    priv->q_select = " + modName + "_qselc_func;\n" +
                "    priv->sched_fn = " + modName + "_schd_func;\n" +
                "\n" +
                "    return 0;\n" +
                "}\n\n";

        return code;
    }

    /**
     * Generate footer code
     *
     * @param modName Module name
     * @return Generated code
     */
    private String generateFooter(String modName) {
        String code = "";

        code += "/* Initialize policy */\n" +
                "static int __init " + modName + "_init(void)\n" +
                "{\n" +
                "    printk(KERN_INFO \"Registered OpenQueue policy " + modName + "\\n\");\n" +
                "\n" +
                "    return oq_register_policy(\""+ port.getName() +"\", " + modName + "_init_port);\n" +
                "}\n" +
                "\n" +
                "/* Exit policy */\n" +
                "static void __exit " + modName + "_exit(void)\n" +
                "{\n" +
                "    printk(KERN_INFO \"Unregistered OpenQueue policy " + modName + "\\n\");\n" +
                "}\n" +
                "\n" +
                "module_init(" + modName + "_init);\n" +
                "module_exit(" + modName + "_exit);\n" +
                "MODULE_LICENSE(\"GPL\");\n";

        return code;
    }

    /**
     * Show info message
     *
     * @param msg Message to be shown
     */
    private static void showInfo(String msg) {
        System.out.println("INFO| " + msg);
    }

    /**
     * Show error message
     *
     * @param msg Message to be shown
     * @param lineNumber Line number at which the error occurred
     */
    private static void showError(String msg, int lineNumber) {
        System.out.println("ERROR| " + msg + (lineNumber != 0 ? " (Line: " + lineNumber + ")" : ""));
    }
}
