package com.ibm.wala.cast.lsp.codeBreaker;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

import org.eclipse.lsp4j.DiagnosticSeverity;
import org.json.JSONArray;

import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.lsp.AnalysisError;
import com.ibm.wala.cast.lsp.WALAServerCore;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.EdgeType;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.TurtlePath;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleLibraryAnalysisEngine;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.labeled.LabeledGraph;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;

public class WALATurtleServer extends WALAServerCore {

	public enum Comments { Ignore, Use, Require };
	
	public static String getComments(TurtlePath turtle) {
		Statement stmt = turtle.statement();
		if (stmt.getKind() == Statement.Kind.NORMAL) {
			IMethod m = stmt.getNode().getMethod();
			int inst = ((NormalStatement)stmt).getInstruction().iIndex();
			if (m instanceof AstMethod) {
				try {
					String comment = ((AstMethod)m).debugInfo().getFollowingComment(inst);
					if (comment != null) {
						return comment;
					}
					
				} catch (IOException e) {
					assert false : e;
				}
			}
			
		}
		return null;
	}
	
	public class TurtleHandler implements AnalysisError {
		private final TurtlePath turtle;
		private final Comments comments;

		protected TurtleHandler(TurtlePath turtle, Comments comments2) {
			this.turtle = turtle;
			this.comments = comments2;
		}

		@Override
		public String source() {
			return "CodeBreaker";
		}

		@Override
		public String toString(boolean useMarkdown) {
			JSONArray dat = turtle.toJSON(true).getJSONArray("path");
			String out = "";
			out += dat.get(0);
			for(int i = 1; i < dat.length(); i++) {
				out += "." + dat.get(i);
			}
			
			if (! Comments.Ignore.equals(comments)) {
				String comment = getComments(turtle);
				if (comment != null) {
					out += comment;
				}
			}
			
			return out;	
		}

		@Override
		public Position position() {
			return turtle.position();
		}

		@Override
		public Iterable<Pair<Position, String>> related() {
			return Collections.emptyList();
		}

		@Override
		public DiagnosticSeverity severity() {
			return DiagnosticSeverity.Information;
		}

		@Override
		public Kind kind() {
			return Kind.CodeLens;
		}

		@Override
		public String repair() {
			// TODO Auto-generated method stub
			return null;
		}
	}

	public NumberedLabeledGraph<TurtlePath, EdgeType> analyzeInternal(
			Collection<Module> sources) throws CancelException, IOException, CallGraphBuilderCancelException {
		PythonTurtleLibraryAnalysisEngine E = new PythonTurtleLibraryAnalysisEngine();
		E.setModuleFiles(sources);
		CallGraphBuilder<? super InstanceKey> builder = E.defaultCallGraphBuilder();
		CallGraph CG = builder.makeCallGraph(E.getOptions(), new NullProgressMonitor());
		NumberedLabeledGraph<TurtlePath, EdgeType> analysis = E.performAnalysis((SSAPropagationCallGraphBuilder)builder);
		return analysis;
	}

	protected AnalysisError create(TurtlePath turtle, Comments comments) {
		return new TurtleHandler(turtle, comments);
	}

	protected void handle(Comments comments, Consumer<AnalysisError> callback, TurtlePath turtle) {
		if (Comments.Require.equals(comments)) {
			if (getComments(turtle) == null) {
				return;
			}
		}
		
		callback.accept(create(turtle, comments));
	}

	public WALATurtleServer(Comments comments) {
		super(false);
		addAnalysis("python", new WALAServerAnalysis() {
			public String source() {
				return "CodeBreaker";
			}

			public void analyze(Collection<Module> sources, Consumer<AnalysisError> callback) {
				try {
					NumberedLabeledGraph<TurtlePath, PythonTurtleAnalysisEngine.EdgeType> analysis = analyzeInternal(sources);
					analysis.forEach((turtle) -> handle(comments, callback, turtle));
				} catch (Exception e) {

				}
			}
		});			
	}
}