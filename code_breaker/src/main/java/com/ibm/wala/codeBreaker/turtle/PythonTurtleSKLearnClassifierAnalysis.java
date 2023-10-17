package com.ibm.wala.codeBreaker.turtle;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.python.ssa.PythonInvokeInstruction;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IFlowFunctionMap;
import com.ibm.wala.dataflow.IFDS.IMergeFunction;
import com.ibm.wala.dataflow.IFDS.IPartiallyBalancedFlowFunctions;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.dataflow.IFDS.IdentityFlowFunction;
import com.ibm.wala.dataflow.IFDS.KillEverything;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationProblem;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationSolver;
import com.ibm.wala.dataflow.IFDS.PathEdge;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MemberReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.impl.GraphInverter;
import com.ibm.wala.util.graph.labeled.LabeledGraph;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.MutableMapping;

public class PythonTurtleSKLearnClassifierAnalysis extends PythonTurtleAnalysisEngine {

	private enum State {
		FRESH, FIT
	}

	private static class ClassifierState {
		private final CGNode node;
		private final int vn;
		private final State state;

		public ClassifierState(CGNode node, int vn, State state) {
			this.node = node;
			this.vn = vn;
			this.state = state;
		}

		public String toString() {
			return "[" + node.getMethod().getDeclaringClass().getName() + "(" + node.getGraphNodeId() + "):" + vn + " - " + state + "]";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((node == null) ? 0 : node.hashCode());
			result = prime * result + ((state == null) ? 0 : state.hashCode());
			result = prime * result + vn;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ClassifierState other = (ClassifierState) obj;
			if (node == null) {
				if (other.node != null)
					return false;
			} else if (!node.equals(other.node))
				return false;
			if (state != other.state)
				return false;
			if (vn != other.vn)
				return false;
			return true;
		}
	}

	@Override
	public NumberedLabeledGraph<TurtlePath, EdgeType> performAnalysis(PropagationCallGraphBuilder builder) throws CancelException {
		NumberedLabeledGraph<TurtlePath, EdgeType> paths =  super.performAnalysis(builder);

		// 1. find objects with appropriate fit method.
		List<String> fitPattern = Arrays.asList("fit", "**", "sklearn");
		Map<SSAInstruction,List<MemberReference>> fitCalls = HashMapFactory.make();
		Set<List<MemberReference>> fitPaths = HashSetFactory.make();
		for(TurtlePath p : paths) {
			List<MemberReference> path = p.path();
			if (match(path.iterator(), fitPattern.iterator())) {
				List<MemberReference> obj = new LinkedList<>(path);
				obj.remove(0);
				fitPaths.add(obj);
				fitCalls.put(caller(p), obj);
			}
		}

		// 2. find objects with appropriate predict method.
		List<String> predictPattern = Arrays.asList("predict", "**", "sklearn");
		Set<List<MemberReference>> predictPaths = HashSetFactory.make();
		Set<SSAInstruction> predictCalls = HashSetFactory.make();
		for(TurtlePath p : paths) {
			List<MemberReference> path = p.path();
			if (match(path.iterator(), predictPattern.iterator())) {
				List<MemberReference> obj = new LinkedList<>(path);
				obj.remove(0);
				predictPaths.add(obj);
				predictCalls.add(caller(p));
			}
		}

		// 3. find suitable classifier objects
		Set<TurtlePath> objs = HashSetFactory.make();
		Map<SSAInstruction, List<MemberReference>> seeds = HashMapFactory.make();
		for(TurtlePath p : paths) {
			List<MemberReference> path = p.path();
			if (fitPaths.contains(path) && predictPaths.contains(path)) {
				objs.add(p);
				seeds.put(caller(p), p.path());
			}
		}

		objs.forEach((tp) -> {
			System.out.println(" " + tp.position() + " " + tp.value());
		});

		ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph = ICFGSupergraph.make(builder.getCallGraph());

		class ClassifierDomain 
		extends MutableMapping<ClassifierState>
		implements TabulationDomain<ClassifierState, BasicBlockInContext<IExplodedBasicBlock>> {

			/**
			 * 
			 */
			private static final long serialVersionUID = 8638705094350579109L;

			@Override
			public boolean hasPriorityOver(PathEdge<BasicBlockInContext<IExplodedBasicBlock>> p1,
					PathEdge<BasicBlockInContext<IExplodedBasicBlock>> p2) {
				// no worklist priorities
				return false;
			}
		}

		ClassifierDomain domain = new ClassifierDomain();

		class ClassifierFlowFunctions implements IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>> {

			private boolean relevantFitCall(SSAInstruction pyCall, BasicBlockInContext<IExplodedBasicBlock> src, ClassifierState flow) {
				int vn = -1;
				SSAInstruction read = src.getNode().getDU().getDef(pyCall.getUse(0));
				if (read instanceof SSAGetInstruction) {
					vn = ((SSAGetInstruction)read).getRef();
				}
				return 
						flow.vn==vn && 
						src.getNode()==flow.node && 
						fitCalls.containsKey(pyCall);
			}

			@Override
			public IFlowFunction getUnbalancedReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
					BasicBlockInContext<IExplodedBasicBlock> dest) {
				return IdentityFlowFunction.identity();
			}

			// pass stuff normally
			@Override
			public IUnaryFlowFunction getCallFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
					BasicBlockInContext<IExplodedBasicBlock> dest, BasicBlockInContext<IExplodedBasicBlock> ret) {
				SSAInstruction ci = src.getLastInstruction();
					if (ci instanceof PythonInvokeInstruction) {
					PythonInvokeInstruction pyCall = (PythonInvokeInstruction)ci;
					return new IUnaryFlowFunction() {

						@Override
						public IntSet getTargets(int d1) {
							boolean skip = false;
							MutableIntSet result = IntSetUtil.make();
							ClassifierState flow = domain.getMappedObject(d1);
							for(int i = 0; i < pyCall.getNumberOfPositionalParameters(); i++) {
								if (i == 1 && relevantFitCall(pyCall, src, flow)) {
									skip = true;
									continue;
								}
								if (pyCall.getUse(i) == flow.vn) {
									ClassifierState adapt = new ClassifierState(dest.getNode(), i+1, flow.state);
									if (! domain.hasMappedIndex(adapt)) {
										int idx = domain.add(adapt);
										System.err.println(adapt + " is " + idx);
									}
									result.add(domain.getMappedIndex(adapt));
								}
							}
							if (!skip && result.isEmpty()) {
								result.add(d1);
							}
							return result;
						}
					};
				} else {
					return IdentityFlowFunction.identity();
				}
			}

			// return stuff normally
			@Override
			public IFlowFunction getReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> call,
					BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dest) {
				SSAInstruction ci = call.getLastInstruction();
				if (ci instanceof PythonInvokeInstruction) {
					PythonInvokeInstruction pyCall = (PythonInvokeInstruction)ci;
					return new IUnaryFlowFunction() {
						@Override
						public IntSet getTargets(int d1) {
							MutableIntSet result = IntSetUtil.make();
							ClassifierState flow = domain.getMappedObject(d1);
							supergraph.getPredNodes(src).forEachRemaining((pb) -> {
								SSAInstruction ret = pb.getLastInstruction();
								if (ret instanceof SSAReturnInstruction) {
									if (ret.getNumberOfUses() > 0 && flow.vn == ret.getUse(0) && flow.node == src.getNode()) {
										ClassifierState adapt = new ClassifierState(dest.getNode(), pyCall.getDef(0), flow.state);
										if (! domain.hasMappedIndex(adapt)) {
											int i = domain.add(adapt);
											System.err.println(adapt + " is " + i);
										}
										result.add(domain.getMappedIndex(adapt));
									}
								}
							});
							if (result.isEmpty()) {
								result.add(d1);
							}

							return result;
						}
					};
				}

				return IdentityFlowFunction.identity();
			}

			// just pretend unknown calls are no-ops, since this is far from a sound model
			@Override
			public IUnaryFlowFunction getCallNoneToReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
					BasicBlockInContext<IExplodedBasicBlock> dest) {
				// if we're missing callees, just keep what information we have
				return IdentityFlowFunction.identity();
			}

			// facts flow into callee and out again, if they survive
			@Override
			public IUnaryFlowFunction getCallToReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
					BasicBlockInContext<IExplodedBasicBlock> dest) {
				SSAInstruction ci = src.getLastInstruction();
				if (ci instanceof PythonInvokeInstruction && fitCalls.containsKey(ci)) {
					PythonInvokeInstruction pyCall = (PythonInvokeInstruction)ci;
					return new IUnaryFlowFunction() {
						@Override
						public IntSet getTargets(int d1) {
							ClassifierState flow = domain.getMappedObject(d1);
							if (relevantFitCall(pyCall, src, flow)) {
								MutableIntSet ds = IntSetUtil.make();
								ClassifierState fit = new ClassifierState(flow.node, flow.vn, State.FIT);
								if (! domain.hasMappedIndex(fit)) {
									domain.add(fit);
								}
								ds.add(domain.getMappedIndex(fit));
								/*
								fit = new ClassifierState(flow.node, ci.getDef(), State.FIT);
								if (! domain.hasMappedIndex(fit)) {
									domain.add(fit);
								}
								ds.add(domain.getMappedIndex(fit));
								 */
								return ds;
							} else {
								return null;
							}
						}
					};
				} else {
					return KillEverything.singleton();
				}
			}

			@Override
			public IUnaryFlowFunction getNormalFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
					BasicBlockInContext<IExplodedBasicBlock> dest) {
				return IdentityFlowFunction.identity();
			}

		}

		ClassifierFlowFunctions flowFunctions = new ClassifierFlowFunctions();

		class ClassifierProblem implements
		PartiallyBalancedTabulationProblem<BasicBlockInContext<IExplodedBasicBlock>, CGNode, ClassifierState> {

			/**
			 * path edges corresponding to all putstatic instructions, used as seeds for the analysis
			 */
			private Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> initialSeeds = collectInitialSeeds();

			/**
			 * we use the entry block of the CGNode as the fake entry when propagating from callee to caller with unbalanced parens
			 */
			@Override
			public BasicBlockInContext<IExplodedBasicBlock> getFakeEntry(BasicBlockInContext<IExplodedBasicBlock> node) {
				final CGNode cgNode = node.getNode();
				return getFakeEntry(cgNode);
			}

			/**
			 * we use the entry block of the CGNode as the "fake" entry when propagating from callee to caller with unbalanced parens
			 */
			private BasicBlockInContext<IExplodedBasicBlock> getFakeEntry(final CGNode cgNode) {
				BasicBlockInContext<IExplodedBasicBlock>[] entriesForProcedure = supergraph.getEntriesForProcedure(cgNode);
				assert entriesForProcedure.length == 1;
				return entriesForProcedure[0];
			}

			/**
			 * collect the putstatic instructions in the call graph as {@link PathEdge} seeds for the analysis
			 */
			private Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> collectInitialSeeds() {
				Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> result = HashSetFactory.make();
				for (BasicBlockInContext<IExplodedBasicBlock> bb : supergraph) {
					IExplodedBasicBlock ebb = bb.getDelegate();
					SSAInstruction instruction = ebb.getInstruction();
					if (seeds.containsKey(instruction)) {
						final CGNode cgNode = bb.getNode();
						ClassifierState fact = new ClassifierState(bb.getNode(), instruction.getDef(), State.FRESH);
						int factNum = domain.add(fact);
						BasicBlockInContext<IExplodedBasicBlock> fakeEntry = getFakeEntry(cgNode);
						// note that the fact number used for the source of this path edge doesn't really matter
						result.add(PathEdge.createPathEdge(fakeEntry, factNum, bb, factNum));
					}
				}
				return result;
			}

			@Override
			public IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>> getFunctionMap() {
				return flowFunctions;
			}

			@Override
			public TabulationDomain<ClassifierState, BasicBlockInContext<IExplodedBasicBlock>> getDomain() {
				return domain;
			}

			/**
			 * we don't need a merge function; the default unioning of tabulation works fine
			 */
			@Override
			public IMergeFunction getMergeFunction() {
				return null;
			}

			@Override
			public ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> getSupergraph() {
				return supergraph;
			}

			@Override
			public Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> initialSeeds() {
				return initialSeeds;
			}
		}

		PartiallyBalancedTabulationSolver<BasicBlockInContext<IExplodedBasicBlock>, CGNode, ClassifierState> solver = PartiallyBalancedTabulationSolver
				.createPartiallyBalancedTabulationSolver(new ClassifierProblem(), null);	
		TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, ClassifierState> result = solver.solve();

		Graph<BasicBlockInContext<IExplodedBasicBlock>> sg = GraphInverter.invert(result.getProblem().getSupergraph());

		IFlowFunctionMap<BasicBlockInContext<IExplodedBasicBlock>> functionMap = result.getProblem().getFunctionMap();

		class FactPair extends Pair<BasicBlockInContext<IExplodedBasicBlock>, ClassifierState> {
			private static final long serialVersionUID = -4732473289945726380L;

			private FactPair(BasicBlockInContext<IExplodedBasicBlock> fst, ClassifierState snd) {
				super(fst, snd);
			}			
		}

		class Backtrace {
			private boolean hasCallee(BasicBlockInContext<IExplodedBasicBlock> returnSite) {
				// if the supergraph says returnSite has a predecessor which indicates a
				// return edge, then we say this return site has a callee.
				for (Iterator<? extends BasicBlockInContext<IExplodedBasicBlock>> it = supergraph.getPredNodes(returnSite); it.hasNext();) {
					BasicBlockInContext<IExplodedBasicBlock> pred = it.next();
					if (!supergraph.getProcOf(pred).equals(supergraph.getProcOf(returnSite))) {
						// an interprocedural edge. there must be a callee.
						return true;
					}
				}
				return false;
			}

			private IntSet check(IUnaryFlowFunction f, BasicBlockInContext<IExplodedBasicBlock> pred, int d2) {
				MutableIntSet rr = IntSetUtil.make();
				result.getResult(pred).foreach((d1) -> {
					IntSet targets = f.getTargets(d1);
					if (targets != null && targets.contains(d2)) {
						rr.add(d1);
					}
				});
				return rr;
			}

			Iterable<FactPair> step(FactPair f) {
				Set<FactPair> result = HashSetFactory.make();
				sg.getSuccNodes(f.fst).forEachRemaining((p) -> {
					switch (supergraph.classifyEdge(p, f.fst)) {
					case ISupergraph.CALL_EDGE: {
						IUnaryFlowFunction ff = functionMap.getCallFlowFunction(p, f.fst, null);
						check(ff, p, domain.getMappedIndex(f.snd)).foreach((d1) -> {
							result.add(new FactPair(p, domain.getMappedObject(d1))); 
						});
						break;
					}
					case ISupergraph.RETURN_EDGE: {
						Iterator<? extends BasicBlockInContext<IExplodedBasicBlock>> calls = supergraph.getCallSites(f.fst, p.getNode());
						while (calls.hasNext()) {
							IUnaryFlowFunction ff = (IUnaryFlowFunction) functionMap.getReturnFlowFunction(calls.next(), p, f.fst);
							check(ff, p, domain.getMappedIndex(f.snd)).foreach((d1) -> {
								result.add(new FactPair(p, domain.getMappedObject(d1))); 
							});
						};
						break;
					}
					case ISupergraph.CALL_TO_RETURN_EDGE: {
						IUnaryFlowFunction ff = null;
						if (hasCallee(f.fst)) {
							ff = functionMap.getCallToReturnFlowFunction(p, f.fst);
						} else {
							ff = functionMap.getCallNoneToReturnFlowFunction(p, f.fst);
						}
						check(ff, p, domain.getMappedIndex(f.snd)).foreach((d1) -> {
							result.add(new FactPair(p, domain.getMappedObject(d1))); 
						});
						break;
					}
					case ISupergraph.OTHER: {
						IUnaryFlowFunction ff = functionMap.getNormalFlowFunction(p, f.fst);
						check(ff, p, domain.getMappedIndex(f.snd)).foreach((d1) -> {
							result.add(new FactPair(p, domain.getMappedObject(d1))); 
						});
						break;
					}
					}
				});
				return result;
			}

			Iterable<FactPair> trace(BasicBlockInContext<IExplodedBasicBlock> node, ClassifierState state) {
				FactPair fp = new FactPair(node, state);
				Set<FactPair> result = HashSetFactory.make();
				result.add(fp);
				boolean changed = false;
				do {
					changed = false;
					for (FactPair sfp : HashSetFactory.make(result)) {
						for (FactPair nfp : step(sfp)) {
							if (! result.contains(nfp)) {
								changed |= result.add(nfp);
							};
						}
					};
				} while (changed);
				return result;
			}
		}

		Backtrace bt = new Backtrace();
		result.getSupergraphNodesReached().forEach((bbic) -> {
			SSAInstruction inst = bbic.getLastInstruction();
			if (predictCalls.contains(inst)) {
				int vn = bbic.getNode().getDU().getDef(inst.getUse(0)).getUse(0);
				result.getResult(bbic).foreach((n) -> { 		
					ClassifierState s = domain.getMappedObject(n);
					if (s.vn == vn && s.node == bbic.getNode()) {
						System.err.println(((AstMethod)bbic.getMethod()).debugInfo().getInstructionPosition(inst.iIndex()) + " : " + (s.state.equals(State.FIT)? "is fitted": "not fitted")); 
						SortedSet<Position> trace = new TreeSet<Position>();
						bt.trace(bbic, s).forEach((fp) -> {
							if (fp.fst.getMethod() instanceof AstMethod && fp.fst.getLastInstruction() != null) {
								trace.add(((AstMethod)fp.fst.getMethod()).debugInfo().getInstructionPosition(fp.fst.getLastInstructionIndex())); 
							}
						});
						System.err.println(trace);
					}
				});
			}
		});
		
		return paths;
	}
}