package util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import com.ibm.wala.cast.python.analysis.ap.AccessPath;
import com.ibm.wala.cast.python.analysis.ap.IAccessPath;
import com.ibm.wala.cast.python.ssa.PythonInstructionVisitor;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.EdgeClass;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.EdgeType;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.TurtlePath;
import com.ibm.wala.codeBreaker.turtle.TurtleDataflow;
import com.ibm.wala.dataflow.graph.AbstractMeetOperator;
import com.ibm.wala.dataflow.graph.DataflowSolver;
import com.ibm.wala.dataflow.graph.ITransferFunctionProvider;
import com.ibm.wala.fixpoint.IVariable;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.NumberedGraph;
import com.ibm.wala.util.graph.impl.GraphInverter;
import com.ibm.wala.util.graph.impl.NodeWithNumber;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;
import com.ibm.wala.util.graph.traverse.DFS;

public class RunTurtleSingleFitTargets extends RunTurtleSingleAnalysis {

	class AccessPathSetVariable extends NodeWithNumber implements IVariable<AccessPathSetVariable> {
		private Set<IAccessPath> paths;
		private int orderNumber;
		
		@Override
		public int getOrderNumber() {
			return orderNumber;
		}

		@Override
		public void setOrderNumber(int i) {
			orderNumber = i;
		}
		
		@Override
		public void copyState(AccessPathSetVariable v) {
			if (v.paths != null) {
				paths = HashSetFactory.make(v.paths);
			} else {
				paths = null;
			}
		}
		
		public boolean add(IAccessPath ap) {
			if (paths == null) {
				paths = HashSetFactory.make();
			}
			
			return paths.add(ap);
		}
	}
	
	
	@Override
	protected void process(NumberedLabeledGraph<TurtlePath, EdgeType> G, PropagationCallGraphBuilder builder) {
		super.process(G, builder);
		
		TurtleDataflow<AccessPathSetVariable> df = new TurtleDataflow<AccessPathSetVariable>() {

			@Override
			public NumberedLabeledGraph<TurtlePath, EdgeType> getFlowGraph() {
				return G;
			}

			@Override
			public ITransferFunctionProvider<TurtlePath, AccessPathSetVariable> getTransferFunctionProvider() {
				return new ITransferFunctionProvider<TurtlePath, AccessPathSetVariable>() {

					@Override
					public UnaryOperator<AccessPathSetVariable> getNodeTransferFunction(TurtlePath node) {
						return new UnaryOperator<AccessPathSetVariable>() {

							@Override
							public byte evaluate(AccessPathSetVariable lhs, AccessPathSetVariable rhs) {
								Statement ns = node.statement();
								if (ns instanceof NormalStatement) {
									((NormalStatement)ns).getInstruction().visit(new PythonInstructionVisitor() {

										@Override
										public void visitGet(SSAGetInstruction instruction) {
											String f = instruction.getDeclaredField().getName().toString();
											IAccessPath fap = AccessPath.fieldAccess(((LocalPointerKey)node.value()).getValueNumber(), f);
											if (rhs.paths == null || rhs.paths.isEmpty()) {
												lhs.add(fap);
											} else {
												rhs.paths.forEach(ap -> lhs.add(AccessPath.append(ap, AccessPath.suffix(fap))));
											}
										}

										
									});
								}
								// TODO Auto-generated method stub
								return 0;
							}

							@Override
							public int hashCode() {
								// TODO Auto-generated method stub
								return 0;
							}

							@Override
							public boolean equals(Object o) {
								// TODO Auto-generated method stub
								return false;
							}

							@Override
							public String toString() {
								// TODO Auto-generated method stub
								return null;
							}	
						};
					}

					@Override
					public boolean hasNodeTransferFunctions() {
						return true;
					}

					@Override
					public UnaryOperator<AccessPathSetVariable> getEdgeTransferFunction(TurtlePath src, TurtlePath dst) {
						assert hasEdgeTransferFunctions();
						return null;
					}

					@Override
					public boolean hasEdgeTransferFunctions() {
						return false;
					}

					@Override
					public AbstractMeetOperator<AccessPathSetVariable> getMeetOperator() {
						return new AbstractMeetOperator<AccessPathSetVariable>() {

							@Override
							public byte evaluate(AccessPathSetVariable lhs, AccessPathSetVariable[] rhs) {
								boolean changed = false;
								for(AccessPathSetVariable v : rhs) {
									if (v != null && v.paths != null) {
										for (IAccessPath ap : v.paths) {
											changed |= lhs.add(ap);
										}
									}
								}
								
								return changed? DataflowSolver.CHANGED: DataflowSolver.NOT_CHANGED;
							}

							@Override
							public int hashCode() {
								return System.identityHashCode(this);
							}

							@Override
							public boolean equals(Object o) {
								return o == this;
							}

							@Override
							public String toString() {
								return "MEET";
							}
						};
					}
					
				};
			}
			
		};
		
		DataflowSolver<TurtlePath,AccessPathSetVariable> solver = new DataflowSolver<TurtlePath,AccessPathSetVariable>(df) {

			@Override
			protected AccessPathSetVariable makeNodeVariable(TurtlePath n, boolean IN) {
				return new AccessPathSetVariable();
			}

			@Override
			protected AccessPathSetVariable makeEdgeVariable(TurtlePath src, TurtlePath dst) {
				return new AccessPathSetVariable();
			}

			@Override
			protected AccessPathSetVariable[] makeStmtRHS(int size) {
				return new AccessPathSetVariable[size];
			}
		};
		
		try {
			boolean paths = solver.solve(new NullProgressMonitor());
			System.err.println("solver result");
			G.forEach(n -> { 
				AccessPathSetVariable s = solver.getOut(n);
				if (s.paths != null && ! s.paths.isEmpty()) {
					System.err.println(n.path() + " " + n.position() + ": " + s.paths);
				}
			});
		} catch (CancelException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		NumberedGraph<TurtlePath> back = GraphInverter.invert(G);
		G.forEach(tp -> {
			if (tp.path().get(0).getName().toString().equals("fit")) {
				Set<TurtlePath> target = HashSetFactory.make();
				G.getPredNodes(tp, new EdgeType(EdgeClass.DATA, 2)).forEachRemaining(p -> {
					target.add(p);
				});
				
				Set<TurtlePath> targetPaths = DFS.getReachableNodes(back, target);
				System.err.println(targetPaths.stream()
						.filter(p -> {
							Statement st = p.statement();
							if (st.getKind() == Statement.Kind.NORMAL 
									&& 
								((NormalStatement)st).getInstruction() instanceof SSAGetInstruction)
							{
								Iterator<? extends EdgeType> e = G.getSuccLabels(p);
								while (e.hasNext()) {
									EdgeType et = e.next();
									if (et.data() && et.index != 0) {
										Iterator<? extends TurtlePath> ss = G.getSuccNodes(p, et);
										while (ss.hasNext()) {
											Statement sst = ss.next().statement();
											System.err.println(sst);
											if (sst.getKind() == Statement.Kind.NORMAL 
													&& 
											    ((NormalStatement)sst).getInstruction() instanceof SSAAbstractInvokeInstruction)
											{
												return true;
											}
										}
									}
								}
							} 
							
							return false;
						})
						.map(p -> p.path().get(0).getName().toString())
						.collect(Collectors.toSet()));
			}
		});
	}

	public RunTurtleSingleFitTargets(File testFile, String repo, String repoPath) throws FileNotFoundException, IOException {
		super(testFile, repo, repoPath);
	}

	public RunTurtleSingleAnalysis make(File testFile, String repo, String repoPath) throws FileNotFoundException, IOException {
		return new RunTurtleSingleFitTargets(testFile, repo, repoPath);
	}

	private RunTurtleSingleFitTargets() throws FileNotFoundException, IOException {
		super();
	}
	
	public static void main(String... args) throws IOException, CancelException, WalaException {
		new RunTurtleSingleFitTargets().rec(new File(args[0]), args[1], args[2]);
	}
}
