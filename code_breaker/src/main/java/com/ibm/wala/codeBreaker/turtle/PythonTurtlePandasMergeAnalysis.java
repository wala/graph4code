package com.ibm.wala.codeBreaker.turtle;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.json.JSONObject;

import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.python.analysis.ap.AccessPath;
import com.ibm.wala.cast.python.analysis.ap.IAccessPath;
import com.ibm.wala.cast.python.analysis.ap.IAccessPath.Kind;
import com.ibm.wala.cast.python.analysis.ap.ListAP;
import com.ibm.wala.cast.python.analysis.ap.LocalAP;
import com.ibm.wala.cast.python.analysis.ap.PropertyPathElement;
import com.ibm.wala.cast.python.ssa.PythonInvokeInstruction;
import com.ibm.wala.cast.python.ssa.PythonPropertyWrite;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IMergeFunction;
import com.ibm.wala.dataflow.IFDS.IPartiallyBalancedFlowFunctions;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.dataflow.IFDS.IdentityFlowFunction;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationProblem;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationSolver;
import com.ibm.wala.dataflow.IFDS.PathEdge;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MemberReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Collection;
import com.ibm.wala.util.collections.IteratorPlusOne;
import com.ibm.wala.util.collections.MapIterator;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.labeled.LabeledGraph;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.MutableMapping;

public class PythonTurtlePandasMergeAnalysis extends PythonTurtleAnalysisEngine {

	class DataFrameState {
		private final String fileName;
		private final String sheetName;
		private final Iterable<String> columnNames;
		
		public DataFrameState(String fileName, String sheetName, Iterable<String> columnNames) {
			super();
			this.fileName = fileName;
			this.sheetName = sheetName;
			this.columnNames = columnNames;
		}

		public DataFrameState(String fileName, String sheetName) {
			this(fileName, sheetName, HashSetFactory.make());
		}

		private DataFrameState extend(String column) {
			return new DataFrameState(fileName, sheetName, new Iterable<String>() {
				@Override
				public Iterator<String> iterator() {
					return IteratorPlusOne.make(columnNames.iterator(), column);
				}
			});
		}
		
		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer(fileName).append(":").append(sheetName).append(":");
			columnNames.forEach((s) -> { sb.append(s).append(" "); });
			return sb.toString();
		}
	}

	private class ValueState extends Pair<IAccessPath, Iterable<DataFrameState>> {

		/**
		 * 
		 */
		private static final long serialVersionUID = 6805184260842121048L;

		protected ValueState(IAccessPath fst, Iterable<DataFrameState> snd) {
			super(fst, snd);
		}
	
		@SuppressWarnings("unused")
		private ValueState copy(IAccessPath to) {
			return new ValueState(to, snd);
		}
		
		private ValueState assign(String columnName) {
			return new ValueState(fst,
				new Iterable<DataFrameState>() {
					@Override
					public Iterator<DataFrameState> iterator() {
						return new MapIterator<>(snd.iterator(), (df) -> { return df.extend(columnName ); });
					}
			});
		}
		
		public String toString() {
			StringBuffer sb = new StringBuffer(fst.toString()).append(":");
			snd.forEach((df) -> { sb.append(df).append(" "); });
			return sb.toString();
		}
 	}
	
	@Override
	public NumberedLabeledGraph<TurtlePath, EdgeType> performAnalysis(PropagationCallGraphBuilder builder) throws CancelException {
		NumberedLabeledGraph<TurtlePath, EdgeType> paths =  super.performAnalysis(builder);
		
		Map<SSAInstruction, DataFrameState> initialFrames = HashMapFactory.make();
		Map<SSAInstruction,Set<String>> merges = HashMapFactory.make();
		
		// 1. find data frame reads
		List<String> readPattern = Arrays.asList("read_excel", "pandas");
		for(TurtlePath p : paths) {
			List<MemberReference> path = p.path();
			if (match(path.iterator(), readPattern.iterator())) {
				String fileName = (String) p.argumentValue(1);
				String sheetName = (String) p.argumentValue(2);
				DataFrameState df = new DataFrameState(fileName, sheetName);
				PointerKey pk = p.value();
				if (pk instanceof LocalPointerKey) {
					LocalPointerKey lpk = (LocalPointerKey)pk;
					SSAInstruction inst = lpk.getNode().getDU().getDef(lpk.getValueNumber());
					initialFrames.put(inst, df);
				}
			}
		}

		// 2. find data frame creations
		List<String> dataFramePattern = Arrays.asList("DataFrame", "pandas");
		for(TurtlePath p : paths) {
			List<MemberReference> path = p.path();
			if (match(path.iterator(), dataFramePattern.iterator())) {
				DataFrameState df = new DataFrameState("", "", Iterator2Collection.toSet(((JSONObject)p.argumentValue(1)).keys()));
				PointerKey pk = p.value();
				if (pk instanceof LocalPointerKey) {
					LocalPointerKey lpk = (LocalPointerKey)pk;
					SSAInstruction inst = lpk.getNode().getDU().getDef(lpk.getValueNumber());
					initialFrames.put(inst, df);
				}
			}
		}

		// 2. find merge calls
		List<String> mergePattern = Arrays.asList("merge", "pandas");
		for(TurtlePath p : paths) {
			List<MemberReference> path = p.path();
			if (match(path.iterator(), mergePattern.iterator())) {
				PointerKey pk = p.value();
				if (pk instanceof LocalPointerKey) {
					LocalPointerKey lpk = (LocalPointerKey)pk;
					SSAInstruction inst = lpk.getNode().getDU().getDef(lpk.getValueNumber());
					Object on = p.nameValue("on");
					if (on instanceof String) {
						merges.put(inst, Collections.singleton((String)on));
					} else if (on instanceof JSONObject) {
						Set<String> keys = HashSetFactory.make();
						(((JSONObject)on).keys()).forEachRemaining((v) -> { keys.add((String)(((JSONObject)on).get(v))); });
						merges.put(inst, keys);
					}
				}
			}
		}
		
		ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph = ICFGSupergraph.make(builder.getCallGraph());

		class PandasFrameDomain extends MutableMapping<ValueState>
			implements TabulationDomain<ValueState, BasicBlockInContext<IExplodedBasicBlock>> {

			/**
			 * 
			 */
			private static final long serialVersionUID = -4689363113660781889L;

			@Override
			public boolean hasPriorityOver(PathEdge<BasicBlockInContext<IExplodedBasicBlock>> p1,
					PathEdge<BasicBlockInContext<IExplodedBasicBlock>> p2) {
				// no worklist priorities
				return false;
			}
		}

		PandasFrameDomain domain = new PandasFrameDomain();
		
		class PandasFrameFlowFunctions implements IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>> {

			@Override
			public IUnaryFlowFunction getNormalFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
					BasicBlockInContext<IExplodedBasicBlock> dest) {
				SSAInstruction ci = src.getLastInstruction();
				if (src.getMethod() instanceof AstMethod && (ci instanceof PythonPropertyWrite || ci instanceof SSAPutInstruction)) {
					String colName;
					int ref;
					int val;
					
					if (ci instanceof PythonPropertyWrite) {
						PythonPropertyWrite wi = (PythonPropertyWrite)ci;
						ref = wi.getObjectRef();
						int col = wi.getMemberRef();
						val = wi.getValue();
						SymbolTable ST = src.getNode().getIR().getSymbolTable();
						if (ST.isConstant(col)) {
							colName = ST.getValue(col).toString();
						} else {
							colName = null;
						}
					} else {
						SSAPutInstruction pi = (SSAPutInstruction)ci;
						ref = pi.getRef();
						val = pi.getVal();
						colName = pi.getDeclaredField().getName().toString();
					}
					
					
					return new IUnaryFlowFunction() {
						@Override
						public IntSet getTargets(int d1) {
							ValueState vs = domain.getMappedObject(d1);
							if (AccessPath.isRootedAtLocal(ref, vs.fst)) {
								ValueState nvs = vs.assign(colName==null? "*": colName);
								System.err.println("found " + nvs);
								if (! domain.hasMappedIndex(nvs)) {
									domain.add(nvs);
								}
								return IntSetUtil.make(new int[] {domain.getMappedIndex(nvs)});
							} else if (AccessPath.isRootedAtLocal(val, vs.fst)) {
								IAccessPath aap = 
										colName == null?
											AccessPath.appendUnknown(AccessPath.localAP(ref)):
											AccessPath.append(AccessPath.localAP(ref), Collections.singletonList(PropertyPathElement.createFieldPathElement(colName)));
								ValueState nvs = new ValueState(aap, vs.snd);
								if (! domain.hasMappedIndex(nvs)) {
									domain.add(nvs);
								}
								System.err.println("found " + nvs);
								return IntSetUtil.make(new int[] {d1, domain.getMappedIndex(nvs)});								
							} else {
								return IntSetUtil.make(new int[] {d1});
							}
						}
					};
				} else {
					return IdentityFlowFunction.identity();
				}
			}

			@Override
			public IUnaryFlowFunction getCallFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
					BasicBlockInContext<IExplodedBasicBlock> dest, BasicBlockInContext<IExplodedBasicBlock> ret) {
				SSAInstruction ci = src.getLastInstruction();
				if (ci instanceof PythonInvokeInstruction) {
					PythonInvokeInstruction pyCall = (PythonInvokeInstruction)ci;
					return new IUnaryFlowFunction() {
						
						@Override
						public IntSet getTargets(int d1) {
							MutableIntSet result = IntSetUtil.make();
							ValueState flow = domain.getMappedObject(d1);
							for(int i = 0; i < pyCall.getNumberOfPositionalParameters(); i++) {
								if (AccessPath.isRootedAtLocal(pyCall.getUse(i), flow.fst)) {

									ValueState adapt = new ValueState(
											flow.fst.getKind()==Kind.LIST? 
												ListAP.createListAP(LocalAP.createLocalAP(i+1), ((ListAP)flow.fst).getPath()):
												LocalAP.createLocalAP(i+1), 
											flow.snd);
									if (! domain.hasMappedIndex(adapt)) {
										int idx = domain.add(adapt);
										System.err.println(adapt + " is " + idx);
									}
									result.add(domain.getMappedIndex(adapt));
								}
							}
							if (result.isEmpty()) {
								result.add(d1);
							}
							return result;
						}
					};
				} else {
					return IdentityFlowFunction.identity();
				}
			}

			@Override
			public IFlowFunction getReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> call,
					BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dest) {
				// TODO Auto-generated method stub
				return IdentityFlowFunction.identity();
			}

			@Override
			public IUnaryFlowFunction getCallToReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
					BasicBlockInContext<IExplodedBasicBlock> dest) {
				// TODO Auto-generated method stub
				return IdentityFlowFunction.identity();
			}

			@Override
			public IUnaryFlowFunction getCallNoneToReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
					BasicBlockInContext<IExplodedBasicBlock> dest) {
				// TODO Auto-generated method stub
				return IdentityFlowFunction.identity();
			}

			@Override
			public IFlowFunction getUnbalancedReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
					BasicBlockInContext<IExplodedBasicBlock> dest) {
				// TODO Auto-generated method stub
				return IdentityFlowFunction.identity();
			}

		}

		PandasFrameFlowFunctions flowFunctions = new PandasFrameFlowFunctions();
		
		class PandaMergeProblem implements
		PartiallyBalancedTabulationProblem<BasicBlockInContext<IExplodedBasicBlock>, CGNode, ValueState> {

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

			private Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> collectInitialSeeds() {
				Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> result = HashSetFactory.make();
				for (BasicBlockInContext<IExplodedBasicBlock> bb : supergraph) {
					IExplodedBasicBlock ebb = bb.getDelegate();
					SSAInstruction instruction = ebb.getInstruction();
					if (initialFrames.containsKey(instruction)) {
						final CGNode cgNode = bb.getNode();
						ValueState vs = new ValueState(AccessPath.localAP(instruction.getDef()), Collections.singleton(initialFrames.get(instruction)));
						int factNum = domain.add(vs);
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
			public TabulationDomain<ValueState, BasicBlockInContext<IExplodedBasicBlock>> getDomain() {
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
		
		try {
			PartiallyBalancedTabulationSolver<BasicBlockInContext<IExplodedBasicBlock>, CGNode, ValueState> solver = PartiallyBalancedTabulationSolver
				.createPartiallyBalancedTabulationSolver(new PandaMergeProblem(), null);	
			TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, ValueState> result = solver.solve();
			
			SortedSet<BasicBlockInContext<IExplodedBasicBlock>> stmts = new TreeSet<>(new Comparator<BasicBlockInContext<IExplodedBasicBlock>>() {
				private int index(BasicBlockInContext<IExplodedBasicBlock> o) {
					return (o.getLastInstruction() == null)? Integer.MAX_VALUE: o.getLastInstruction().iIndex();
				}
				@Override
				public int compare(BasicBlockInContext<IExplodedBasicBlock> o1,
						BasicBlockInContext<IExplodedBasicBlock> o2) {
					if (! o1.getNode().equals(o2.getNode())) {
						return o1.getNode().toString().compareTo(o2.getNode().toString());
					} else {
						return index(o1) - index(o2);
					}
				} 
			});
			stmts.addAll(result.getSupergraphNodesReached());
			IMethod currentMethod = null;
			for(BasicBlockInContext<IExplodedBasicBlock> bbic : stmts) {
				if (bbic.getLastInstruction() != null) {
					if (bbic.getMethod() != currentMethod) {
						System.err.println("method " + currentMethod);
						currentMethod = bbic.getMethod();
					}
					System.err.println(bbic.getLastInstruction().toString(bbic.getNode().getIR().getSymbolTable()));
					result.getResult(bbic).foreach((i) -> {
						System.err.println(domain.getMappedObject(i));
					});
				}
			}
			
			for(BasicBlockInContext<IExplodedBasicBlock> bbic : stmts) {
				SSAInstruction inst = bbic.getLastInstruction();
				if (merges.containsKey(inst)) {
					System.err.println(merges.get(inst));
					result.getResult(bbic).foreach((i) -> {
						ValueState val = domain.getMappedObject(i);
						System.err.println(val);
						
					});					
				}
			}
			
		} catch (CancelException e) {
			
		}
		
		return paths;
	}
	

}
