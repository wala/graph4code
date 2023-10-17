package com.ibm.wala.cast.python.test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Test;

import com.ibm.wala.cast.ir.ssa.AstPropertyRead;
import com.ibm.wala.cast.ir.ssa.AstPropertyWrite;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.python.client.PythonAnalysisEngine;
import com.ibm.wala.cast.python.ssa.PythonInstructionVisitor;
import com.ibm.wala.cast.python.ssa.PythonInvokeInstruction;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.EdgeType;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.TurtlePath;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.graph.labeled.LabeledGraph;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;
import com.ibm.wala.util.graph.labeled.SlowSparseNumberedLabeledGraph;

public class TestPythonGraphGeneration extends TestPythonTurtleCallGraphShape  {
    private SlowSparseNumberedLabeledGraph<String, String> graph = new SlowSparseNumberedLabeledGraph<>("default");

    public TestPythonGraphGeneration() {
        super(true);            // KAVITHA: Need this so we can get a CFG per method
    }

    public void processGraph(String main) throws Exception {
        TestPythonGraphGeneration driver = new TestPythonGraphGeneration() {

        };
        PythonAnalysisEngine<NumberedLabeledGraph<TurtlePath, EdgeType>> E = driver.makeEngine(main);

        CallGraphBuilder<? super InstanceKey> builder = E.defaultCallGraphBuilder();
        CallGraph CG = builder.makeCallGraph(E.getOptions(), new NullProgressMonitor());

        for (CGNode node : CG) {
            processCFG(node);
        }
    }

    public LabeledGraph<String, String> getGraph() {
        return graph;
    }

    public String getMethodInfo(AstMethod m) {
        Position position = ((AstMethod)m).debugInfo().getCodeBodyPosition();
        return m.getDeclaringClass().getName().toString() + "_" + position.getFirstLine() + "_" + position.getFirstOffset() +
                "_" + position.getLastLine() + "_" + position.getLastOffset();
    }


    private void convertBasicBlock(ISSABasicBlock b, SymbolTable symbolTable) {
        Iterator<SSAInstruction> instructions = b.iterator();
        while (instructions.hasNext()) {
            SSAInstruction inst = instructions.next();
            inst.visit(new PythonInstructionVisitor() {
                private String INSTRUCTION_TYPE = "instruction type";
                private String GOTO = "goto";
                private String OP = "op";
                private String TARGET = "target";
                private String ARRAY_LOAD = "array load";
                private String ARRAY_STORE = "array store";
                private String ARRAY_REF = "array reference";
                private String ARRAY_INDEX = "array index";
                private String VALUE = "value";
                private String DEF_VALUE = "def value";
                private String BINARY_OP = "binary op";
                private String UNARY_OP = "unary op";
                private String CONVERSION = "conversion";
                private String FROM_TYPE = "from type";
                private String TO_TYPE = "to type";
                private String COMPARISON = "comparison";
                private String CONDITIONAL_BRANCH = "conditional branch";
                private String TYPE_REF = "type ref";
                private String SWITCH = "switch";
                private String CASE = "case";
                private String LABEL = "label";
                private String DEFAULT_LABEL = "default label";
                private String RETURN = "return";
                private String IS_RETURN_PRIMITIVE = "is return primitive";
                private String READ_FIELD = "read field";
                private String FIELD_REF = "field ref";
                private String IS_STATIC = "is static";
                private String WRITE_FIELD = "write field";
                private String ALLOCATION = "allocation";
                private String ALLOCATION_TYPE = "allocation type";
                private String ARRAY_LENGTH = "array length";
                private String INSTANCE_OF = "instance of";
                private String PHI = "phi";
                private String INVOKE = "invoke";
                private String ARG = "arg_";
                private String CALL_SITE_REF = "call site ref";
                private String RECEIVER_REF = "receiver ref";
                private String VALUE_CONSTANT = "value constant";
                private String PROPERTY_READ = "property read";
                private String PROPERTY_WRITE = "property store";
                private String ARRAY_LOCATION = "array location";

                private Map<SSAPhiInstruction, Integer> phiNodes = new HashMap<>();

                private String getValue(int value) {
                    return "value_" + Integer.toString(value);
                }

                private String getInstruction(int iindex) {
                    return "instruction_" + Integer.toString(iindex);
                }

                private String getPhiNode(SSAPhiInstruction phi) {
                    if (!phiNodes.containsKey(phi)) {
                        int id = phiNodes.size() + 1;
                        phiNodes.put(phi, id);
                    }
                    return "phi_node_" + phiNodes.get(phi);
                }

                // do common instruction tasks
                private void helper(SSAInstruction instruction, boolean addDef, boolean addUses) {
                    String instruction_iri;
                    if (instruction instanceof SSAPhiInstruction) {
                        instruction_iri = getPhiNode((SSAPhiInstruction) instruction);
                    } else {
                        instruction_iri = getInstruction(instruction.iIndex());
                    }
                    graph.addNode(instruction_iri);
                    if (addDef) {
                        graph.addNode(getValue(instruction.getDef()));
                        graph.addEdge(instruction_iri, getValue(instruction.getDef()), DEF_VALUE);
                        addValueConstant(instruction.getDef(), getValue(instruction.getDef()));
                    }
                    if (addUses) {
                        for (int i = 0; i < instruction.getNumberOfUses(); i++) {
                            graph.addNode(getValue(instruction.getUse(i)));
                            graph.addEdge(instruction_iri, getValue(instruction.getUse(i)), VALUE);
                            addValueConstant(instruction.getUse(i), getValue(instruction.getUse(i)));
                        }
                    }
                }

                private void addValueConstant(int valueID, String value_iri) {
                    String constant = symbolTable.getValueString(valueID);
                    graph.addNode(constant);
                    graph.addEdge(value_iri, constant, VALUE_CONSTANT);
                }

                @Override
                public void visitPythonInvoke(PythonInvokeInstruction instruction) {
                    helper(instruction, false, false);
                    graph.addNode(INVOKE);
                    graph.addEdge(getInstruction(instruction.iIndex()), INVOKE, INSTRUCTION_TYPE);
                    for (int i = 1; i < instruction.getNumberOfPositionalParameters(); i++) {
                        String value = getValue(instruction.getUse(i));
                        graph.addNode(value);
                        System.out.println(i);
                        graph.addEdge(getInstruction(instruction.iIndex()), value, ARG + i);
                        addValueConstant(instruction.getUse(i), value);
                    }

                    for (String keyword : instruction.getKeywords()) {
                        String value = getValue(instruction.getUse(keyword));
                        graph.addNode(value);
                        graph.addEdge(getInstruction(instruction.iIndex()), value, ARG + keyword);
                        addValueConstant(instruction.getUse(keyword), value);

                    }

                    for (int i = 0; i < instruction.getNumberOfReturnValues(); i++) {
                        String value = getValue(instruction.getReturnValue(i));
                        graph.addNode(value);
                        graph.addEdge(getInstruction(instruction.iIndex()), value, RETURN + "_" + i);
                        addValueConstant(instruction.getReturnValue(i), value);
                    }

                    String callsite = instruction.getCallSite().getDeclaredTarget().toString();
                    graph.addNode(callsite);
                    graph.addEdge(getInstruction(instruction.iIndex()), callsite, CALL_SITE_REF);

                    String receiver = getValue(instruction.getReceiver());
                    graph.addNode(receiver);
                    graph.addEdge(getInstruction(instruction.iIndex()), receiver, RECEIVER_REF);
                    addValueConstant(instruction.getReceiver(), receiver);
                }

                @Override
                public void visitGoto(SSAGotoInstruction instruction) {
                    helper(instruction, false, false);
                    graph.addNode(GOTO);
                    graph.addNode(getInstruction(instruction.getTarget()));
                    graph.addEdge(getInstruction(instruction.iIndex()), GOTO, INSTRUCTION_TYPE);
                    graph.addEdge(getInstruction(instruction.iIndex()), getInstruction(instruction.getTarget()), TARGET);
                }

                @Override
                public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
                    helper(instruction, true, false);
                    graph.addNode(ARRAY_LOAD);
                    graph.addNode(getValue(instruction.getArrayRef()));
                    graph.addNode(Integer.toString(instruction.getIndex()));
                    graph.addEdge(getInstruction(instruction.iIndex()), ARRAY_LOAD, INSTRUCTION_TYPE);
                    graph.addEdge(getInstruction(instruction.iIndex()), getValue(instruction.getArrayRef()), ARRAY_REF);
                    graph.addEdge(getInstruction(instruction.iIndex()), Integer.toString(instruction.getIndex()), ARRAY_INDEX);

                }

                @Override
                public void visitArrayStore(SSAArrayStoreInstruction instruction) {
                    helper(instruction, false, false);
                    graph.addNode(ARRAY_STORE);
                    graph.addNode(getValue(instruction.getValue()));
                    graph.addNode(getValue(instruction.getArrayRef()));
                    graph.addNode(Integer.toString(instruction.getIndex()));
                    graph.addEdge(getInstruction(instruction.iIndex()), ARRAY_STORE, INSTRUCTION_TYPE);
                    graph.addEdge(getInstruction(instruction.iIndex()), getValue(instruction.getArrayRef()), ARRAY_REF);
                    graph.addEdge(getInstruction(instruction.iIndex()), Integer.toString(instruction.getIndex()), ARRAY_INDEX);
                    graph.addEdge(getInstruction(instruction.iIndex()), getValue(instruction.getValue()), VALUE);

                }

                @Override
                public void visitBinaryOp(SSABinaryOpInstruction instruction) {
                    helper(instruction, true, true);
                    graph.addNode(BINARY_OP);
                    graph.addNode(instruction.getOperator().toString());
                    graph.addEdge(getInstruction(instruction.iIndex()), BINARY_OP, INSTRUCTION_TYPE);
                    graph.addEdge(getInstruction(instruction.iIndex()), instruction.getOperator().toString(), OP);
                }

                @Override
                public void visitUnaryOp(SSAUnaryOpInstruction instruction) {
                    helper(instruction, true, true);
                    graph.addNode(UNARY_OP);
                    graph.addNode(instruction.getOpcode().toString());
                    graph.addEdge(getInstruction(instruction.iIndex()), UNARY_OP, INSTRUCTION_TYPE);
                    graph.addEdge(getInstruction(instruction.iIndex()), instruction.getOpcode().toString(), OP);

                }

                @Override
                public void visitConversion(SSAConversionInstruction instruction) {
                    helper(instruction, true, true);
                    graph.addNode(CONVERSION);
                    graph.addEdge(getInstruction(instruction.iIndex()), CONVERSION, INSTRUCTION_TYPE);
                    graph.addEdge(getInstruction(instruction.iIndex()), instruction.getFromType().toString(), FROM_TYPE);
                    graph.addEdge(getInstruction(instruction.iIndex()), instruction.getFromType().toString(), TO_TYPE);
                }

                @Override
                public void visitComparison(SSAComparisonInstruction instruction) {
                    helper(instruction, true, true);
                    graph.addNode(COMPARISON);
                    graph.addNode(instruction.getOperator().toString());
                    graph.addEdge(getInstruction(instruction.iIndex()), COMPARISON, INSTRUCTION_TYPE);
                    graph.addEdge(getInstruction(instruction.iIndex()), instruction.getOperator().toString(), OP);
                }

                @Override
                public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {
                    helper(instruction, false, true);
                    graph.addNode(CONDITIONAL_BRANCH);
                    graph.addNode(getInstruction(instruction.getTarget()));
                    graph.addEdge(getInstruction(instruction.iIndex()), CONDITIONAL_BRANCH, INSTRUCTION_TYPE);
                    graph.addEdge(getInstruction(instruction.iIndex()), getInstruction(instruction.getTarget()), TARGET);
                    if (instruction.getType() != null) {
                        graph.addNode(instruction.getType().toString());
                        graph.addEdge(getInstruction(instruction.iIndex()), instruction.getType().toString(), TYPE_REF);
                    }

                }

                @Override
                public void visitSwitch(SSASwitchInstruction instruction) {
                    helper(instruction, false, false);
                    graph.addNode(SWITCH);
                    graph.addEdge(getInstruction(instruction.iIndex()), SWITCH, INSTRUCTION_TYPE);
                    int[] casesAndLabels = instruction.getCasesAndLabels();
                    for (int i = 0; i < casesAndLabels.length; i += 2) {
                        graph.addEdge(getInstruction(instruction.iIndex()), Integer.toString(casesAndLabels[i]), CASE);
                        graph.addEdge(getInstruction(instruction.iIndex()), getInstruction(casesAndLabels[i+1]), LABEL);
                    }
                    graph.addEdge(getInstruction(instruction.iIndex()), getInstruction(instruction.getDefault()), DEFAULT_LABEL);
                }

                @Override
                public void visitReturn(SSAReturnInstruction instruction) {
                    helper(instruction, false, false);
                    graph.addNode(RETURN);
                    graph.addNode(Boolean.toString(instruction.returnsPrimitiveType()));
                    graph.addNode(getValue(instruction.getResult()));
                    graph.addEdge(getInstruction(instruction.iIndex()), RETURN, INSTRUCTION_TYPE);
                    graph.addEdge(getInstruction(instruction.iIndex()), Boolean.toString(instruction.returnsPrimitiveType()), IS_RETURN_PRIMITIVE);
                    graph.addEdge(getInstruction(instruction.iIndex()), getValue(instruction.getResult()), VALUE);
                }

                @Override
                public void visitGet(SSAGetInstruction instruction) {
                    helper(instruction, true, false);
                    graph.addNode(READ_FIELD);
                    graph.addNode(Boolean.toString(instruction.isStatic()));
                    graph.addNode(instruction.getDeclaredField().toString());
                    graph.addEdge(getInstruction(instruction.iIndex()), READ_FIELD, INSTRUCTION_TYPE);
                    graph.addEdge(getInstruction(instruction.iIndex()), Boolean.toString(instruction.isStatic()), IS_STATIC);
                    graph.addEdge(getInstruction(instruction.iIndex()), instruction.getDeclaredField().toString(), FIELD_REF);
                }

                @Override
                public void visitPut(SSAPutInstruction instruction) {
                    helper(instruction, false, false);
                    graph.addNode(WRITE_FIELD);
                    graph.addNode(Boolean.toString(instruction.isStatic()));
                    graph.addNode(getValue(instruction.getVal()));
                    graph.addNode(instruction.getDeclaredField().toString());
                    graph.addEdge(getInstruction(instruction.iIndex()), WRITE_FIELD, INSTRUCTION_TYPE);
                    graph.addEdge(getInstruction(instruction.iIndex()), Boolean.toString(instruction.isStatic()), IS_STATIC);
                    graph.addEdge(getInstruction(instruction.iIndex()), instruction.getDeclaredField().toString(), FIELD_REF);
                    graph.addEdge(getInstruction(instruction.iIndex()), getValue(instruction.getVal()), VALUE);
                }


                @Override
                public void visitNew(SSANewInstruction instruction) {
                    helper(instruction, true, true);
                    graph.addNode(ALLOCATION);
                    graph.addNode(instruction.getConcreteType().toString());
                    graph.addEdge(getInstruction(instruction.iIndex()), ALLOCATION, INSTRUCTION_TYPE);
                    graph.addEdge(getInstruction(instruction.iIndex()), instruction.getConcreteType().toString(), ALLOCATION_TYPE);
                }

                @Override
                public void visitArrayLength(SSAArrayLengthInstruction instruction) {
                    helper(instruction, true, true);
                    graph.addNode(ARRAY_LENGTH);
                    graph.addEdge(getInstruction(instruction.iIndex()), ARRAY_LENGTH, INSTRUCTION_TYPE);
                }


                @Override
                public void visitInstanceof(SSAInstanceofInstruction instruction) {
                    helper(instruction, true, true);
                    graph.addNode(INSTANCE_OF);
                    graph.addEdge(getInstruction(instruction.iIndex()), INSTANCE_OF, INSTRUCTION_TYPE);

                }

                @Override
                public void visitPhi(SSAPhiInstruction instruction) {
                    helper(instruction, true, true);
                    graph.addNode(PHI);
                    graph.addEdge(getPhiNode(instruction), PHI, INSTRUCTION_TYPE);
                }

                @Override
                public void visitPropertyWrite(AstPropertyWrite instruction) {
                    helper(instruction, false, false);
                    graph.addNode(PROPERTY_WRITE);
                    graph.addEdge(getInstruction(instruction.iIndex()), PROPERTY_WRITE, INSTRUCTION_TYPE);


                    graph.addNode(getValue(instruction.getUse(0)));
                    graph.addNode(getValue(instruction.getUse(1)));
                    graph.addNode(getValue(instruction.getUse(2)));
                    graph.addEdge(getInstruction(instruction.iIndex()), getValue(instruction.getUse(0)), RECEIVER_REF);
                    addValueConstant(instruction.getUse(1), getValue(instruction.getUse(0)));
                    graph.addEdge(getInstruction(instruction.iIndex()), getValue(instruction.getUse(1)), FIELD_REF);
                    addValueConstant(instruction.getUse(1), getValue(instruction.getUse(1)));
                    graph.addEdge(getInstruction(instruction.iIndex()), getValue(instruction.getUse(2)), VALUE);
                    addValueConstant(instruction.getUse(2), getValue(instruction.getUse(2)));

                }

                @Override
                public void visitPropertyRead(AstPropertyRead instruction) {
                    helper(instruction, true, false);
                    graph.addNode(PROPERTY_READ);
                    graph.addEdge(getInstruction(instruction.iIndex()), PROPERTY_READ, INSTRUCTION_TYPE);

                    graph.addNode(getValue(instruction.getUse(0)));
                    graph.addNode(getValue(instruction.getUse(1)));
                    graph.addNode(getValue(instruction.getDef()));
                    graph.addEdge(getInstruction(instruction.iIndex()), getValue(instruction.getDef()), DEF_VALUE);
                    addValueConstant(instruction.getUse(0), getValue(instruction.getUse(0)));
                    graph.addEdge(getInstruction(instruction.iIndex()), getValue(instruction.getUse(0)), RECEIVER_REF);
                    addValueConstant(instruction.getUse(1), getValue(instruction.getUse(1)));
                    graph.addEdge(getInstruction(instruction.iIndex()), getValue(instruction.getUse(1)), FIELD_REF);
                }
            });
        }
    }

    private void processCFG(CGNode node) {
        IMethod m = node.getMethod();
        System.out.println("****at method:" + m);
        // System.out.println(node.getIR());
        // System.out.println("_________________________");
        if (m instanceof AstMethod) {
            System.out.println("****analyzing method:" + m);
            SSACFG cfg = node.getIR().getControlFlowGraph();
            SymbolTable symbolTable = node.getIR().getSymbolTable();
            Iterator<ISSABasicBlock> it = cfg.iterator();
            while (it.hasNext()) {
                ISSABasicBlock block = it.next();
                convertBasicBlock(block, symbolTable);
                Iterator<ISSABasicBlock> succs = cfg.getSuccNodes(block);

                while (succs.hasNext()) {
                    ISSABasicBlock s = succs.next();
                    convertBasicBlock(s, symbolTable);
                }
            }
        }

        // ControlDependenceGraph<ISSABasicBlock> cdg = new ControlDependenceGraph<>(cfg);
    }

    public static void main(String[] args) throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
        TestPythonGraphGeneration gen = new TestPythonGraphGeneration();
        for (String main : args) {
            try {
                gen.processGraph(main);
                System.out.println("Graph:" + gen.getGraph());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void runTest(String s) throws ClassHierarchyException, CancelException, IOException {
        String[] args = new String[1];
        args[0] = s;
        main(args);
    }

    @Test
    public void testInvoke() throws Exception {
        runTest("src/test/resources/invoke.py");
    }

    @Test
    public void branch() throws Exception {
        runTest("src/test/resources/branch.py");
    }

    @Test
    public void arrays() throws Exception {
        runTest("src/test/resources/arrays.py");
    }

    @Test
    public void classes() throws Exception {
        runTest("src/test/resources/classes.py");
    }

    @Test
    public void fit_transform() throws Exception {
        runTest("src/test/resources/fit_transform.py");
    }
}
