import java.io.*;
import java.util.Map;
import java.util.HashMap;

/**
 * Created by danushka on 2/20/17.
 */
public class OQGen {
    private static final String OQ_QSEL_FUNC = "@oq_qsel_func";
    private static final String OQ_CONG_FUNC = "@oq_cong_func";
    private static final String OQ_CONG_ACT_FUNC = "@oq_cong_act_func";
    private static final String OQ_ADMN_FUNC = "@oq_admn_func";
    private static final String OQ_PROC_FUNC = "@oq_proc_func";
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
                        String[] tokens = line.split(" ");
                        if (tokens.length != 3) {
                            showError("Invalid assignment statement: " + line, lineNumber);
                            return false;
                        }

                        String lhs = tokens[0];
                        String rhs = tokens[2];
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
     * Validate if a given funciton name is correct in terms of both name and type
     *
     * @param entity Entity the function attached to (Queue/Port)
     * @param rhs Name
     * @param functionType Type
     * @return True if it is a correct assignment or false otherwise
     */
    private boolean validateFunctionAssignment(Entity entity, String rhs, RoutineType functionType) {
        RoutineCall routineCall;

        if (rhs.startsWith("inline")) {
            routineCall = null;
        } else {
            // Resolve function name
            String funcName = rhs;
            if (funcName.contains("("))
                funcName = funcName.substring(0, funcName.indexOf('('));

            Routine routine = routines.get(funcName);
            if ((routine == null) || (routine.getType() != functionType))
                return false;
            routineCall = new RoutineCall(funcName);

            // Set parameters (if any)
            int paramCount = 0;
            String[] paramTokens = null;
            if (rhs.contains("(") && rhs.contains(")")) {
                String params = rhs.substring(rhs.indexOf('(') + 1, rhs.indexOf(')'));
                paramTokens = params.trim().split(",");
                paramCount = paramTokens.length;
            }

            if (paramCount != routine.getParamCount())
                return false;

            if (paramCount != 0) {
                Param[] params = getRoutineParameters(paramTokens, routine.getParamTypes());
                if (params == null)
                    return false;

                routineCall.setParams(params);
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
     * Deserialize routine parameters as defined in the policy
     *
     * @param paramTokens Arry of parameters
     * @param paramTypes Corresponding types as per routine signature
     * @return List of Param objects
     */
    private Param[] getRoutineParameters(String[] paramTokens, ParamType[] paramTypes) {
        Param[] paramArray = new Param[paramTokens.length];

        for (int i = 0; i < paramTokens.length; i++) {
            String paramToken = paramTokens[i].trim();

            switch (paramTypes[i]) {
                case BOOL:
                    if (!paramToken.equalsIgnoreCase("true") && !paramToken.equalsIgnoreCase("false"))
                        return null;
                    paramArray[i] = new Param(ParamType.BOOL, paramToken);
                    break;
                case CHAR:
                    if (paramToken.length() != 3 || paramToken.charAt(0) != '\'' || paramToken.charAt(2) != '\'')
                        return null;
                    paramArray[i] = new Param(ParamType.CHAR, paramToken.substring(1, 2));
                    break;
                case INT:
                    try {
                        Integer.parseInt(paramToken);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    paramArray[i] = new Param(ParamType.INT, paramToken);
                    break;
                case LONG:
                    try {
                        Long.parseLong(paramToken);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    paramArray[i] = new Param(ParamType.LONG, paramToken);
                    break;
                case DOUBLE:
                    try {
                        Double.parseDouble(paramToken);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    paramArray[i] = new Param(ParamType.DOUBLE, paramToken);
                    break;
                default:
                    return null;
            }
        }

        return paramArray;
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
     * E.g. int select_admission_queue(struct Qdisc* sch, struct sk_buff* skb);
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
        if (paramTokens.length < 2)
            return null;

        String[] param1Tokens = paramTokens[0].trim().split(" ");
        if ((param1Tokens.length != 3) || !param1Tokens[0].trim().equals("struct") ||
                !param1Tokens[1].trim().equals("Qdisc*"))
            return null;

        String[] param2Tokens = paramTokens[1].trim().split(" ");
        if ((param2Tokens.length != 3) || !param2Tokens[0].trim().equals("struct") ||
                !param2Tokens[1].trim().equals("sk_buff*"))
            return null;

        Routine routine = new Routine(RoutineType.QUEUE_SELECTOR, funcName);

        // Add additional parameters
        if (2 < paramTokens.length) {
            if (!validateAdditionalParameters(routine, paramTokens, 2))
                return null;
        }

        return routine;
    }

    /**
     * Check if a Congestion function has the correct signature
     * E.g. bool my_congestion_condition(struct oq_queue* queue);
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
        if (paramTokens.length == 0)
            return null;

        String[] param1Tokens = paramTokens[0].trim().split(" ");
        if ((param1Tokens.length != 3) || !param1Tokens[0].trim().equals("struct") ||
                !param1Tokens[1].trim().equals("oq_queue*"))
            return null;

        Routine routine = new Routine(RoutineType.CONGESTION_CONDITION, funcName);

        // Add additional parameters
        if (1 < paramTokens.length) {
            if (!validateAdditionalParameters(routine, paramTokens, 1))
                return null;
        }

        return routine;
    }

    /**
     * Check if a Congestion Action function has the correct signature
     * E.g. int drop_tail(struct oq_queue* queue, struct sk_buff* skb);
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
        if (paramTokens.length < 2)
            return null;

        String[] param1Tokens = paramTokens[0].trim().split(" ");
        if ((param1Tokens.length != 3) || !param1Tokens[0].trim().equals("struct") ||
                !param1Tokens[1].trim().equals("oq_queue*"))
            return null;

        String[] param2Tokens = paramTokens[1].trim().split(" ");
        if ((param2Tokens.length != 3) || !param2Tokens[0].trim().equals("struct") ||
                !param2Tokens[1].trim().equals("sk_buff*"))
            return null;

        Routine routine = new Routine(RoutineType.CONGESTION_ACTION, funcName);

        // Add additional parameters
        if (2 < paramTokens.length) {
            if (!validateAdditionalParameters(routine, paramTokens, 2))
                return null;
        }

        return routine;
    }

    /**
     * Check if a Admission function has the correct signature
     * E.g. unsigned long my_adm_prio(struct sk_buff* skb);
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
        if (paramTokens.length == 0)
            return null;

        String[] param1Tokens = paramTokens[0].trim().split(" ");
        if ((param1Tokens.length != 3) || !param1Tokens[0].trim().equals("struct") ||
                !param1Tokens[1].trim().equals("sk_buff*"))
            return null;

        Routine routine = new Routine(RoutineType.ADMISSION_PRIORITY, funcName);

        // Add additional parameters
        if (1 < paramTokens.length) {
            if (!validateAdditionalParameters(routine, paramTokens, 1))
                return null;
        }

        return routine;
    }

    /**
     * Check if a Processing function has the correct signature
     * E.g. unsigned long my_pro_prio(struct sk_buff *skb);
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
        if (paramTokens.length == 0)
            return null;

        String[] param1Tokens = params.trim().split(" ");
        if ((param1Tokens.length != 3) || !param1Tokens[0].trim().equals("struct") ||
                !param1Tokens[1].trim().equals("sk_buff*"))
            return null;

        Routine routine = new Routine(RoutineType.PROCESSING_PRIORITY, funcName);

        // Add additional parameters
        if (1 < paramTokens.length) {
            if (!validateAdditionalParameters(routine, paramTokens, 1))
                return null;
        }

        return routine;
    }

    /**
     * Check if a Scheduling function has the correct signature
     * E.g. int my_schd_prio(struct Qdisc *sch);
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
        if (paramTokens.length == 0)
            return null;

        String[] param1Tokens = params.trim().split(" ");
        if ((param1Tokens.length != 3) || !param1Tokens[0].trim().equals("struct") ||
                !param1Tokens[1].trim().equals("Qdisc*"))
            return null;

        Routine routine = new Routine(RoutineType.SCHEDULING_PRIORITY, funcName);

        // Add additional parameters
        if (1 < paramTokens.length) {
            if (!validateAdditionalParameters(routine, paramTokens, 1))
                return null;
        }

        return routine;
    }

    /**
     * Validate and add additional parameters to the routine.
     *
     * @param routine Routine instance
     * @param paramTokens Parameters
     * @param startIndex Index where additional parameters start
     * @return True if all parameters are validated fine or false otherwise
     */
    private boolean validateAdditionalParameters(Routine routine, String[] paramTokens, int startIndex) {
        for (int i = startIndex; i < paramTokens.length; i++) {
            String param = paramTokens[i].trim();

            String[] tokens = param.split(" ");
            if (tokens.length != 2)
                return false;

            switch (tokens[0]) {
                case "bool":
                    routine.setNextParamType(ParamType.BOOL);
                    break;
                case "char":
                    routine.setNextParamType(ParamType.CHAR);
                    break;
                case "int":
                    routine.setNextParamType(ParamType.INT);
                    break;
                case "long":
                    routine.setNextParamType(ParamType.LONG);
                    break;
                case "double":
                    routine.setNextParamType(ParamType.DOUBLE);
                    break;
                default:
                    return false;
            }
        }

        return true;
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
     * Generate code
     */
    private boolean generateCode() {
        // Generate constants header file
        String constFileName = "include/gen/oq_defines.h";
        if (!generateDefines(constFileName)) {
            showError("Error while generating file: " + constFileName, 0);
            return false;
        }

        // Generate port initialization function
        String initFileName = "qdisc/gen/oq_init_port.c";
        if (!generateInitPort(initFileName)) {
            showError("Error while generating file: " + constFileName, 0);
            return false;
        }

        return true;
    }

    /**
     * Generate constants file
     *
     * @param fileName Name of the file
     * @return true if successful or false otherwise
     */
    private boolean generateDefines(String fileName) {
        boolean status = false;

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(new File(fileName)));

            bw.write("/*\n" +
                    " * oq_defines.h  OpenQueue constant declaration (Generated code).\n" +
                    " *\n" +
                    " *              This program is free software; you can redistribute it and/or\n" +
                    " *              modify it under the terms of the GNU General Public License\n" +
                    " *              as published by the Free Software Foundation; either version\n" +
                    " *              2 of the License, or (at your option) any later version.\n" +
                    " */\n" +
                    "\n" +
                    "#pragma once\n\n");

            // Number of queues
            bw.write("#define TCQ_OQ_NO_QUEUES ");
            bw.write(Integer.toString(queues.size()));

            // Function calls

            bw.flush();
            bw.close();

            status = true;
        } catch (IOException e) {
            showError("Error while generating file: " + e.getMessage(), 0);
        }

        return status;
    }

    /**
     * Generate init file
     *
     * @param fileName Name of the file
     * @return true if successful or false otherwise
     */
    private boolean generateInitPort(String fileName) {
        boolean status = false;

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(new File(fileName)));

            bw.write("/*\n" +
                    " * oq_init_port.c  OpenQueue port initialization (Generated code).\n" +
                    " *\n" +
                    " *              This program is free software; you can redistribute it and/or\n" +
                    " *              modify it under the terms of the GNU General Public License\n" +
                    " *              as published by the Free Software Foundation; either version\n" +
                    " *              2 of the License, or (at your option) any later version.\n" +
                    " */\n\n");

            bw.write("#include \"../include/sch_openqueue.h\"\n" +
                    "#include \"../include/routine/routines.h\"\n" +
                    "\n" +
                    "extern int init_queue(struct oq_queue *queue, const char* name, int max_len, \n" +
                    "               \t     oq_cong_func cong_fn, oq_cong_act_func cong_act_fn, \n" +
                    "               \t     oq_admn_func admn_fn, oq_proc_func proc_fn);" +
                    "\n\n");

            bw.write("int init_port(struct oq_priv *priv)\n" +
                    "{\n");

            int i = 0;
            for (Map.Entry<String, Queue> entry : queues.entrySet()) {
                String name = entry.getKey();
                Queue queue = entry.getValue();

                String code = String.format("\tif (init_queue(&priv->queues[%d], \"%s\", %d, \n" +
                        "\t\t%s, %s, %s, %s) != 0)\n" +
                        "\t\treturn -ENOMEM;\n", i, name, queue.getSize(), queue.getCongestion().getName(),
                        queue.getCongAction().getName(), queue.getAdmPrio().getName(), queue.getProcPrio().getName());

                bw.write(code);

                i++;
            }

            String str = String.format("\n" +
                    "\tpriv->q_select = %s;\n" +
                    "\tpriv->sched_fn = %s;\n" +
                    "\tstrncpy(priv->port_name, \"%s\", TCQ_OQ_NAME_LEN);\n" +
                    "\n", port.getQueueSelect().getName(), port.getSchedPrio().getName(), port.getName());
            bw.write(str);


            bw.write("\treturn 0;\n" +
                    "}");

            bw.flush();
            bw.close();

            status = true;
        } catch (IOException e) {
            showError("Error while generating file: " + e.getMessage(), 0);
        }

        return status;
    }

    /**
     * Show info log message
     *
     * @param msg Message to be shown
     */
    private static void showInfo(String msg) {
        System.out.println("[Info] " + msg);
    }

    /**
     * Show error log message
     *
     * @param msg Message to be shown
     * @param lineNumber Line number at which the error occurred
     */
    private static void showError(String msg, int lineNumber) {
        System.out.println("[Error] " + msg + (lineNumber != 0 ? " (Line: " + lineNumber + ")" : ""));
    }
}
