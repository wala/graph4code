package com.ibm.wala.cast.python.test;

import java.io.IOException;

import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.python.client.PythonAnalysisEngine;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.EdgeType;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.TurtlePath;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.graph.labeled.LabeledGraph;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;

public class TestPythonTurtleComments extends TestPythonTurtleCallGraphShape {

	public TestPythonTurtleComments() {
		super(false);
	}

	public TestPythonTurtleComments(boolean library) {
		super(library);
	}

	public static void main(String[] args) throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
		TestPythonTurtleComments driver = new TestPythonTurtleComments() {

		};

		for (String main : args) {
			PythonAnalysisEngine<NumberedLabeledGraph<TurtlePath, EdgeType>> E = driver.makeEngine(main);

			CallGraphBuilder<? super InstanceKey> builder = E.defaultCallGraphBuilder();
			CallGraph CG = builder.makeCallGraph(E.getOptions(), new NullProgressMonitor());

			NumberedLabeledGraph<TurtlePath, EdgeType> analysis = E.performAnalysis((SSAPropagationCallGraphBuilder)builder);
			analysis.iterator().forEachRemaining((tp) -> {
				Statement stmt = tp.statement();
				if (stmt.getKind() == Statement.Kind.NORMAL) {
					IMethod m = stmt.getNode().getMethod();
					int inst = ((NormalStatement)stmt).getInstruction().iIndex();
					if (m instanceof AstMethod) {
						try {
							String comment = ((AstMethod)m).debugInfo().getFollowingComment(inst);
							if (comment != null) {
								System.err.println(tp);
								System.err.println(comment.substring(1));
							}
							
						} catch (IOException e) {
							assert false : e;
						}
					}
				}
			});
	}
	}
}
