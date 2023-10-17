package com.ibm.wala.codeBreaker.turtle;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.python.client.PythonAnalysisEngine;
import com.ibm.wala.cast.python.loader.PythonLoaderFactory;
import com.ibm.wala.cast.python.util.PythonInterpreter;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.util.Util;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SourceURLModule;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.EdgeType;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.TurtlePath;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.labeled.LabeledGraph;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;
import com.ibm.wala.util.io.FileUtil;

public class PythonTurtleCoverage  {

	static {
		try {
			Class<?> j3 = Class.forName("com.ibm.wala.cast.python.loader.Python3LoaderFactory");
			PythonAnalysisEngine.setLoaderFactory((Class<? extends PythonLoaderFactory>) j3);
			Class<?> i3 = Class.forName("com.ibm.wala.cast.python.util.Python3Interpreter");
			PythonInterpreter.setInterpreter((PythonInterpreter)i3.newInstance());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
			try {
				Class<?> j2 = Class.forName("com.ibm.wala.cast.python.loader.Python2LoaderFactory");			
				PythonAnalysisEngine.setLoaderFactory((Class<? extends PythonLoaderFactory>) j2);
				Class<?> i2 = Class.forName("com.ibm.wala.cast.python.util.Python2Interpreter");
				PythonInterpreter.setInterpreter((PythonInterpreter)i2.newInstance());
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e1) {
				assert false : e.getMessage() + ", then " + e1.getMessage();
			}
		}
	}
    public static String getMethodInfo(AstMethod m) {
        CAstSourcePositionMap.Position position = ((AstMethod)m).debugInfo().getCodeBodyPosition();
        return m.getDeclaringClass().getName().toString() + "_" + position.getFirstLine() + "_" + position.getFirstOffset() +
                "_" + position.getLastLine() + "_" + position.getLastOffset();
    }

    private static Set<Position> countInvokes(CallGraph CG, CGNode node, boolean checkTarget) {
    	Set<Position> result =HashSetFactory.make();
    	Iterator<CallSiteReference> callSites = node.getIR().iterateCallSites();
    	while(callSites.hasNext()) {
    		CallSiteReference site = callSites.next();

            if (CG.getPossibleTargets(node, site).isEmpty()) {
                System.err.println("EMPTY TARGET:" + site);
            } else {
                CG.getPossibleTargets(node, site).forEach((n) -> {
                    System.err.println("NONEMPTY TARGET:" + n.getMethod().getDeclaringClass().getName());
                });
            }

            if (!checkTarget || !CG.getPossibleTargets(node, site).isEmpty()) {
                //SSACFG cfg = node.getIR().getControlFlowGraph();
                //ControlDependenceGraph<ISSABasicBlock> cdg = new ControlDependenceGraph<>(cfg);

                result.add(((AstMethod)node.getIR().getMethod()).debugInfo().getInstructionPosition(node.getIR().getCalls(site)[0].iIndex()));
			}
    	}
    	
    	return result;
    }
    
    private static int allMethodsCount = 0;
    private static int allReachableMethodsCount = 0;
    private static int allCallSitesCount = 0;
    private static int allTargetCallSitesCount = 0;
    private static int allTurtleCallSitesCount = 0;

    public static void main(String[] args) throws IllegalArgumentException, CancelException, IOException {        
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(2); 
        for (String main : args) {
			File top = new File(main);
			if (top.exists() && top.isDirectory()) {
				FileUtil.recurseFiles(f -> {
					try {
						System.err.println("analyze " + f);
						timed_analyze(executor, f.toURI().toURL().toString());
					} catch (Throwable e) {
						System.err.println(e);
					}
				}, f -> f.getName().endsWith(".py"), top);
			} else {
				try {
					timed_analyze(executor, main);
				} catch (Throwable e) {
					System.err.println(e);
				}
			}
        }
		executor.shutdown();

        System.out.println("allMethodsCount: " + allMethodsCount);
        System.out.println("allReachableMethodsCount: " + allReachableMethodsCount);
        System.out.println("allCallSitesCount: " + allCallSitesCount);
        System.out.println("allTargetCallSitesCount: " + allTargetCallSitesCount);
        System.out.println("allTurtleCallSitesCount: " + allTurtleCallSitesCount);
    }

    private static void timed_analyze(ScheduledExecutorService executor, String main) {
		 final Future<Void> handler = executor.submit(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				 analyze(main);
				 return null;
			} 
		 });
		 try {
			try { 
				handler.get(10000, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				handler.cancel(true);
			}
		} catch (InterruptedException | ExecutionException | CancellationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
	private static void analyze(String main)
			throws CancelException, IOException, CallGraphBuilderCancelException, WalaException {
		{
			PythonAnalysisEngine<NumberedLabeledGraph<TurtlePath, EdgeType>> E = new PythonTurtleLibraryAnalysisEngine();
			E.setModuleFiles(Collections.singleton(new SourceURLModule(new URL(main))));

            CallGraphBuilder<? super InstanceKey> builder = E.defaultCallGraphBuilder();
            Util.checkForFrontEndErrors(builder.getClassHierarchy());
            CallGraph CG = builder.makeCallGraph(E.getOptions(), new NullProgressMonitor());
            LinkedList<String> reachableMethods = new LinkedList<String>();
            Set<Position> cgCalls = HashSetFactory.make();
            Set<Position> cgTargetCalls = HashSetFactory.make();
            for(CGNode node : CG) {

                IMethod m = node.getMethod();
                if (m instanceof AstMethod) {
                    reachableMethods.add(getMethodInfo((AstMethod)m));
                   	cgCalls.addAll(countInvokes(CG, node, false));
                	cgTargetCalls.addAll(countInvokes(CG, node, true));
                }
            };


            List<String> allMethods = new LinkedList<String>();

            CG.getClassHierarchy().forEach((cl)->{
                if (cl.getDeclaredMethods() != null) {
                    cl.getDeclaredMethods().forEach((m) -> {
                        if (m instanceof AstMethod) {
                            allMethods.add(getMethodInfo((AstMethod) m));
                        }
                    });
                }
            });

            System.err.println("*************************************");
            List<String> analyzedMethods= new LinkedList<String>();

            Set<Position> turtleTargetCalls = HashSetFactory.make();
            NumberedLabeledGraph<TurtlePath, EdgeType> analysis = E.performAnalysis((SSAPropagationCallGraphBuilder)builder);
            for(TurtlePath tp : analysis) {
                Statement stmt = tp.statement();
                if (stmt.getKind() == Statement.Kind.NORMAL && (((NormalStatement)stmt).getInstruction() instanceof SSAAbstractInvokeInstruction)) {
                    IMethod m = stmt.getNode().getMethod();
                    if (m instanceof AstMethod) {
                    	SSAInstruction inst = ((NormalStatement)stmt).getInstruction();
                    	turtleTargetCalls.add(((AstMethod)stmt.getNode().getIR().getMethod()).debugInfo().getInstructionPosition(inst.iIndex()));
                        analyzedMethods.add(getMethodInfo((AstMethod)m));
                    }
                }
            };

            Set<String> allUniqueMethods = new HashSet<String>(allMethods);
            Set<String> reachableUniqueMethods = new HashSet<String>(reachableMethods);
            Set<String> analyzedUniqueMethods = new HashSet<String>(analyzedMethods);

            assert allUniqueMethods.size() == allMethods.size();
            assert reachableUniqueMethods.size() == reachableMethods.size();
            assert analyzedUniqueMethods.size() == analyzedMethods.size();

   
            allMethodsCount += allUniqueMethods.size();
            allReachableMethodsCount += reachableUniqueMethods.size();
            
            // new SourceBuffer(cgTargetCalls.iterator().next()).toString();
            
            Set<String> tmp = new HashSet<String>(allUniqueMethods);
            tmp.removeAll(reachableUniqueMethods);

            System.err.println(main + ": Number of all methods:" + allUniqueMethods.size());
            System.err.println(main + ": Number of reachable methods:" + reachableUniqueMethods.size());
            System.err.println(main + ": Number of analyzed methods:" + analyzedUniqueMethods.size());
            System.err.println(main + ": Number of methods not reached at all:" + tmp.size());

            tmp = new HashSet<String>(reachableUniqueMethods);
            tmp.removeAll(analyzedUniqueMethods);
            System.err.println(main + ": Number of methods not analyzed from reachable methods:" + tmp.size());

            System.err.println(main + ": Call site counts:" + cgCalls.size());
            System.err.println(main + ": Call site with target counts:" + cgTargetCalls.size());
            System.err.println(main + ": Turtle call sites with targets counts:" + turtleTargetCalls.size());

            allCallSitesCount += cgCalls.size();
            allTargetCallSitesCount += cgTargetCalls.size();
            allTurtleCallSitesCount += turtleTargetCalls.size();
		}
	}
}
