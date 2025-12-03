package graphcode;

import static graphcode.MavenClassHierarchy.toAnalysisScope;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.common.io.CharStreams;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.tree.impl.LineNumberPosition;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IMethod.SourcePosition;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.DefaultIRFactory;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.IRFactory;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;
import com.ibm.wala.util.graph.labeled.SlowSparseNumberedLabeledGraph;

import javassist.bytecode.Descriptor;

public class DatasetFromPom {
	final IRFactory<IMethod> irFactory = new DefaultIRFactory();
	final AnalysisScope scope;

	public DatasetFromPom(String group, String artifact, String version) throws IOException, InterruptedException {
		scope = toAnalysisScope(group, artifact, version);
	}

	private static class Key implements Comparable<Key> {
		Set<IClass> args;
		IClass owner;
		MethodReference target;

		private Key(MethodReference target, Set<IClass> args, IClass owner) {
			super();
			this.args = args;
			this.owner = owner;
			this.target = target;
		}

		@Override
		public int hashCode() {
			return Objects.hash(args, owner, target);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Key other = (Key) obj;
			return Objects.equals(args, other.args) && Objects.equals(owner, other.owner)
					&& Objects.equals(target, other.target);
		}

		@SafeVarargs
		private final int compare(Supplier<Integer>... vs) {
			for (Supplier<Integer> v : vs) {
				int x = v.get();
				if (x != 0) {
					return x;
				}
			}
			return 0;
		}

		@Override
		public int compareTo(Key o) {
			return compare(() -> o.args.size() - args.size(), () -> args.toString().compareTo(o.args.toString()),
					() -> target.toString().compareTo(o.target.toString()),
					() -> owner.toString().compareTo(o.owner.toString()));
		}

		@Override
		public String toString() {
			return "call to " + target + " with " + args.size();
		}

	}

	private Set<IClass> interestingCall(IClass cl, TypeAbstraction[] types, SSAAbstractInvokeInstruction call) {
		IClassHierarchy cha = cl.getClassHierarchy();
		IClass owner = cha.lookupClass(call.getDeclaredTarget().getDeclaringClass());
		if (owner == null) {
			return null;
		}

		if (call.isStatic()) {
			if (call.getDeclaredTarget().getDeclaringClass().equals(cl.getReference())) {
				return null;
			}
		} else {
			int target = call.getUse(0);

			if (types[target] == TypeAbstraction.TOP) {
				return null;
			}

			IClass t = cha.lookupClass(types[target].getTypeReference());
			if (t.equals(cl) || !t.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
				return null;
			}
		}

		if (getClassNode(owner).contains("$")) {
			return null;
		}

		boolean keep = false;
		Set<IClass> args = HashSetFactory.make();
		for (int i = 0; i < call.getNumberOfUses(); i++) {
			keep |= handleArg(cha, types[call.getUse(i)].getTypeReference(), args);
		}
		if (call.hasDef()) {
			keep |= handleArg(cha, types[call.getDef()].getTypeReference(), args);
		}

		return keep ? args : null;
	}

	private boolean handleArg(IClassHierarchy cl, TypeReference argType, Set<IClass> args) {
		if (argType == null || argType.isPrimitiveType()
				|| (argType.isArrayType() && argType.getArrayElementType().isPrimitiveType())
				|| !argType.getClassLoader().equals(ClassLoaderReference.Application)) {
			return false;
		} else {
			IClass tcl = cl.lookupClass(argType);
			args.add(tcl);
			return true;
		}
	}

	public SortedMap<Key, Set<Pair<IMethod, Integer>>> analyze(String classNamePrefix)
			throws ClassHierarchyException, InvalidClassFileException {
		IClassHierarchy cha = ClassHierarchyFactory.make(scope);
		SortedMap<Key, Set<Pair<IMethod, Integer>>> candidates = new TreeMap<>();
		for (IClass cl : cha) {
			String className = getClassNode(cl);
			if (className.contains(classNamePrefix) && !className.contains("$")) {
				for (IMethod m : cl.getAllMethods()) {
					if (hasCode(m)) {
						IR ir = irFactory.makeIR(m, Everywhere.EVERYWHERE, SSAOptions.defaultOptions());
						TypeAbstraction[] types = getTypes(ir);
						if (ir.getInstructions() != null) {
							calls: for (SSAInstruction inst : ir.getInstructions()) {
								if (inst instanceof SSAAbstractInvokeInstruction) {
									SSAAbstractInvokeInstruction call = (SSAAbstractInvokeInstruction) inst;

									Set<IClass> args;
									if ((args = interestingCall(cl, types, call)) == null) {
										continue calls;
									}

									/*
									 * IClass calledClass =
									 * cha.lookupClass(call.getDeclaredTarget().getDeclaringClass()); IMethod
									 * calledMethod = calledClass.getMethod(call.getDeclaredTarget().getSelector());
									 * 
									 * if (calledMethod == null) { continue calls; }
									 */

									IClass owner = cl.getClassHierarchy()
											.lookupClass(call.getDeclaredTarget().getDeclaringClass());
									Key key = new Key(call.getDeclaredTarget(), args, owner);
									if (!candidates.containsKey(key)) {
										candidates.put(key, HashSetFactory.make());
									}
									candidates.get(key).add(Pair.make(m, inst.iIndex()));
								}
							}
						}
					}
				}
			}
			;
		}
		;

		return candidates;
	}

	private TypeAbstraction[] getTypes(IR ir) {
		TypeInference typeAnalysis = TypeInference.make(ir, true);
		typeAnalysis.solve();
		return typeAnalysis.extractAllResults();
	}

	private NumberedLabeledGraph<String, String> localContext(NumberedLabeledGraph<String, String> contextGraph, IR ir,
			SSAInstruction call, boolean useMethod) throws InvalidClassFileException, IOException {
		return computeContext(contextGraph, ir, call, useMethod, (me, it) -> it.getLastLine() < me.getFirstLine());
	}

	private NumberedLabeledGraph<String, String> selfContext(NumberedLabeledGraph<String, String> contextGraph, IR ir,
			SSAInstruction call, boolean useMethod) throws InvalidClassFileException, IOException {
		return computeContext(contextGraph, ir, call, useMethod,
				(me, it) -> it.getFirstLine() >= me.getFirstLine() && it.getLastLine() <= me.getLastLine());
	}

	private NumberedLabeledGraph<String, String> computeContext(NumberedLabeledGraph<String, String> contextGraph,
			IR ir, SSAInstruction call, boolean useMethod,
			BiPredicate<SourcePosition, SourcePosition> isRelevantContext)
			throws InvalidClassFileException, IOException {
		String root = "ROOT";
		contextGraph.addNode(root);
		IMethod walaMethod = ir.getMethod();
		SourcePosition me = walaMethod.getSourcePosition(call.iIndex());
		IClass walaClass = walaMethod.getDeclaringClass();
		Supplier<Reader> r = () -> {
			return walaClass.getClassLoader().getSource(walaClass);
		};
		URL sf = URI.create("file:" + walaClass.getSourceFileName()).toURL();

		IClass myClass = ir.getMethod().getDeclaringClass();
		String myClassNode = getClassNode(myClass);
		if (!contextGraph.containsNode(myClassNode)) {
			contextGraph.addNode(myClassNode);
			contextGraph.addEdge(root, myClassNode, "class");
			addSourceForClass(contextGraph, myClass, myClassNode);
		}

		TypeAbstraction[] types = getTypes(ir);
		if (r.get() != null) {
			for (SSAInstruction inst : ir.getInstructions()) {
				if (inst != null) {
					SourcePosition it = walaMethod.getSourcePosition(inst.iIndex());
					String instStr = new SourceBuffer(new LineNumberPosition(sf, sf, it.getFirstLine()) {
						@Override
						public Reader getReader() throws IOException {
							return r.get();
						}
					}).toString();
					if (isRelevantContext.test(me, it)) {

						if (inst instanceof SSANewInstruction) {
							IClass created = myClass.getClassHierarchy()
									.lookupClass(((SSANewInstruction) inst).getConcreteType());
							if (created != null) {
								String createdClassNode = getClassNode(created);
								contextGraph.addNode(createdClassNode);
								contextGraph.addEdge(root, createdClassNode, "new");
								addSourceForClass(contextGraph, created, createdClassNode);
							}

						} else if (inst instanceof SSAAbstractInvokeInstruction
								&& interestingCall(walaMethod.getDeclaringClass(), types,
										(SSAAbstractInvokeInstruction) call) != null) {
							SSAAbstractInvokeInstruction callInst = (SSAAbstractInvokeInstruction) inst;
							contextGraph.addNode(instStr);
							contextGraph.addEdge(root, instStr, "callsite");
							if (useMethod) {
								IMethod targetMethod = walaMethod.getClassHierarchy()
										.resolveMethod(callInst.getDeclaredTarget());
								if (targetMethod != null) {
									String methodNode = getMethodNode(targetMethod);
									if (!contextGraph.containsNode(methodNode)) {
										contextGraph.addNode(methodNode);
										addSourceForMethod(contextGraph, targetMethod.getDeclaringClass(), targetMethod,
												methodNode);
									}
									if (!contextGraph.hasEdge(instStr, methodNode, "callee")) {
										contextGraph.addEdge(instStr, methodNode, "callee");
									}
								}
							}
							IClass targetClass = walaMethod.getClassHierarchy()
									.lookupClass(callInst.getDeclaredTarget().getDeclaringClass());
							if (targetClass != null) {
								String classNode = getClassNode(targetClass);
								contextGraph.addNode(classNode);
								contextGraph.addEdge(instStr, classNode, "receiver");
								addSourceForClass(contextGraph, targetClass, classNode);
							}
						}
					}
				}
			}
		}
		return contextGraph;
	}

	private boolean addSemi(TypeReference ref) {
		if (ref.isClassType()) {
			return true;
		} else if (ref.isArrayType()) {
			return addSemi(ref.getArrayElementType());
		} else {
			return false;
		}
	}

	private String toClassName(TypeReference ref) {
		return Descriptor.toClassName(ref.getName().toString() + (addSemi(ref) ? ";" : ""));
	}

	private String toClassName(IClass cls) {
		return toClassName(cls.getReference());
	}

	private String getClassNode(IClass targetClass) {
		return toClassName(targetClass);
	}

	private String getMethodNode(IMethod targetMethod) {
		if (hasCode(targetMethod)) {
			String node = toClassName(targetMethod.getDeclaringClass()) + ":"
					+ toClassName(targetMethod.getReturnType()) + " ";

			if (!(targetMethod.isInit() || targetMethod.isClinit())) {
				node += targetMethod.getName();
			} else if (targetMethod.isInit()) {
				node += "<init>";
			} else if (targetMethod.isClinit()) {
				node += "<clinit>";
			}

			node += "(";

			boolean first = true;
			for (int i = 0; i < targetMethod.getNumberOfParameters(); i++) {
				IR ir = getIR(targetMethod);
				String[] ithParamNames = ir.getLocalNames(0, i + 1);
				if (ithParamNames != null && ithParamNames.length > 0) {
					node += (first ? "" : ",") + toClassName(ir.getParameterType(i)) + " " + ithParamNames[0];
					first = false;
				}
			}

			node += ")";

			return node;
		} else {
			return toClassName(targetMethod.getDeclaringClass()) + ":" + targetMethod.getSignature();
		}
	}

	private NumberedLabeledGraph<String, String> callingParameterContext(
			NumberedLabeledGraph<String, String> contextGraph, Key k, SSAInstruction call)
			throws InvalidClassFileException, IOException {
		for (IClass walaClass : k.args) {
			String classNode = getClassNode(walaClass);
			if (!contextGraph.containsNode(classNode)) {
				contextGraph.addNode(classNode);
			}
			for (IMethod walaMethod : walaClass.getAllMethods()) {
				String methodNode = getMethodNode(walaMethod);
				if (!contextGraph.containsNode(methodNode)) {
					contextGraph.addNode(methodNode);
					addSourceForMethod(contextGraph, walaClass, walaMethod, methodNode);
				}
				if (!contextGraph.hasEdge(classNode, methodNode)) {
					contextGraph.addEdge(classNode, methodNode, "method");
				}
			}
		}
		return contextGraph;
	}

	private void addSourceForMethod(NumberedLabeledGraph<String, String> contextGraph, IClass walaClass,
			IMethod walaMethod, String methodNode) throws IOException, InvalidClassFileException {
		if (walaMethod.getNumberOfParameters() > 0) {
			for (int i = 0; i < walaMethod.getNumberOfParameters(); i++) {
				TypeReference p = walaMethod.getParameterType(i);
				IClass pc = walaMethod.getClassHierarchy().lookupClass(p);
				if (pc != null) {
					String cn = getClassNode(pc);
					if (!contextGraph.containsNode(cn)) {
						contextGraph.addNode(cn);
						addSourceForClass(contextGraph, pc, cn);
					}
					contextGraph.addEdge(methodNode, cn, "parameters");
				}
			}
		}

		if (hasCode(walaMethod)) {
			IR ir = getIR(walaMethod);

			Reader sf = walaClass.getClassLoader().getSource(walaClass);
			if (sf != null) {
				SourceBuffer code = new SourceBuffer(new Position() {

					SourcePosition start = walaMethod.getSourcePosition(0);
					SourcePosition end = walaMethod.getSourcePosition(ir.getInstructions().length - 1);

					{
						for (int i = 0; i < ir.getInstructions().length; i++) {
							SourcePosition p = walaMethod.getSourcePosition(i);
							if (p.getFirstLine() < start.getFirstLine()) {
								start = p;
							}
							if (p.getLastLine() > end.getLastLine()) {
								end = p;
							}
						}
					}

					@Override
					public int getFirstLine() {
						return start.getFirstLine();
					}

					@Override
					public int getLastLine() {
						return end.getLastLine();
					}

					@Override
					public int getFirstCol() {
						return -1;
					}

					@Override
					public int getLastCol() {
						return -1;
					}

					@Override
					public int getFirstOffset() {
						return -1;
					}

					@Override
					public int getLastOffset() {
						return -1;
					}

					@Override
					public int compareTo(SourcePosition o) {
						throw new UnsupportedOperationException();
					}

					@Override
					public URL getURL() {
						throw new UnsupportedOperationException();
					}

					@Override
					public Reader getReader() throws IOException {
						return sf;
					}

				});

				String codeStr = code.toString();
				contextGraph.addNode(codeStr);
				contextGraph.addEdge(methodNode, codeStr, "text");
			}
		}
	}

	private static boolean hasCode(IMethod walaMethod) {
		return !(walaMethod.isAbstract() || walaMethod.isNative() || walaMethod.isSynthetic()
				|| walaMethod.isWalaSynthetic());
	}

	private IR getIR(IMethod walaMethod) {
		IR ir = irFactory.makeIR(walaMethod, Everywhere.EVERYWHERE, SSAOptions.defaultOptions());
		return ir;
	}

	private void addSourceForClass(NumberedLabeledGraph<String, String> contextGraph, IClass walaClass,
			String classNode) throws IOException, InvalidClassFileException {
		Reader sf = walaClass.getClassLoader().getSource(walaClass);
		if (sf != null) {
			String text = CharStreams.toString(sf);
			sf.close();
			contextGraph.addNode(text);
			contextGraph.addEdge(classNode, text, "text");

			IClass sc = walaClass.getSuperclass();
			if (sc != null) {
				String superNode = getClassNode(sc);
				if (!contextGraph.containsNode(superNode)) {
					contextGraph.addNode(superNode);
					contextGraph.addEdge(classNode, superNode, "superclass");
					addSourceForClass(contextGraph, sc, superNode);
				}
			}
		}

		walaClass.getDeclaredMethods().forEach(m -> {
			String methodNode = getMethodNode(m);
			if (!contextGraph.containsNode(methodNode)) {
				contextGraph.addNode(methodNode);
				try {
					addSourceForMethod(contextGraph, m.getDeclaringClass(), m, methodNode);
				} catch (IOException | InvalidClassFileException e) {
					assert false : e;
				}
			}
			contextGraph.addEdge(classNode, methodNode, "declaredMethod");
		});

	}

	static class Sentinel {
		boolean first = true;
	}

	public static void main(String... args)
			throws ClassHierarchyException, IOException, InterruptedException, InvalidClassFileException {
		Sentinel lock = new Sentinel();
		boolean useSelfContext = args[4].equals("self");
		boolean useLocalContext = useSelfContext || args[4].equals("true");
		boolean useMethodSource = args[5].equals("true");
		try (Writer writer = new OutputStreamWriter(new BZip2CompressorOutputStream(new FileOutputStream(args[6])))) {
			DatasetFromPom ds = new DatasetFromPom(args[0], args[1], args[2]);
			writer.write("[\n");
			
			Map<Key, Set<Pair<IMethod, Integer>>> analysis = ds.analyze(args[3]);
			Collection<Runnable> elts = new ArrayList<Runnable>();
			for (Key k : analysis.keySet()) {
					JSONObject key = new JSONObject();
					key.put("target", k.target.toString());
					key.put("owner", k.owner.toString());
					if (useLocalContext) {
						for (Pair<IMethod, Integer> inst : analysis.get(k)) {
							elts.add(new Runnable() {
								public void run() {
								try {
									IR ir = ds.getIR(inst.fst);
									SlowSparseNumberedLabeledGraph<String, String> cg = new SlowSparseNumberedLabeledGraph<>();
									NumberedLabeledGraph<String, String> g = useSelfContext
											? ds.selfContext(cg, ir, ir.getInstructions()[inst.snd], useMethodSource)
											: ds.localContext(cg, ir, ir.getInstructions()[inst.snd], useMethodSource);
									for (String n : g) {
										if (!cg.containsNode(n)) {
											cg.addNode(n);
										}
										g.getSuccLabels(n).forEachRemaining(l -> {
											g.getSuccNodes(n, l).forEachRemaining(s -> {
												if (!cg.hasEdge(n, s, l)) {
													cg.addEdge(n, s, l);
												}
											});
										});
									}
									JSONObject o = new JSONObject();
									o.put("inst", ir.getInstructions()[inst.snd].toString());
									o.put("context", toJSON(cg));

									SourcePosition line = ir.getMethod().getSourcePosition(inst.snd);
									o.put("line", line.getFirstLine());

									synchronized (lock) {
										if (lock.first) {
											lock.first = false;
										} else {
											writer.write(",\n");
										}

										o.write(writer, 3, 1);
									}
								} catch (IOException | InvalidClassFileException e) {
									assert false : e;
								}
								}});
						}
					} else {
						elts.add(new Runnable() {
							public void run() {
							try {
								SlowSparseNumberedLabeledGraph<String, String> cg = new SlowSparseNumberedLabeledGraph<>();
								JSONObject g = toJSON(ds.callingParameterContext(cg, k, null));
								key.put("context", g);

								synchronized (lock) {
									if (lock.first) {
										lock.first = false;
									} else {
										writer.write(",\n");
									}

									key.write(writer, 3, 1);
								}
							} catch (IOException | InvalidClassFileException e) {
								assert false : e;
							}
							}
						});
					}
				}

	BlockingQueue<Runnable> q = new ArrayBlockingQueue<>(elts.size(), true, elts);
	Thread[] threads = new Thread[10];for(
	int i = 0;i<10;i++)
	{
		(threads[i] = new Thread(new Runnable() {
			@Override
			public void run() {
				Runnable r;
				while ((r = q.poll()) != null) {
					r.run();
				}

			}
		})).start();
	}

	for(
	int i = 0;i<10;i++)
	{
		threads[i].join();
	}

	writer.write("]\n");
	}}

	private static JSONObject toJSON(NumberedLabeledGraph<String, String> c) {
		JSONArray nodes = new JSONArray();
		for (String s : c) {
			nodes.put(c.getNumber(s), s);
		}
		JSONObject edges = new JSONObject();
		c.forEach(src -> {
			if (c.getSuccNodeCount(src) > 0) {
				JSONObject dst = new JSONObject();
				edges.put(src, dst);
				c.getSuccLabels(src).forEachRemaining(l -> {
					JSONArray ldst = new JSONArray();
					dst.put(l, ldst);
					c.getSuccNodes(src, l).forEachRemaining(ldstn -> {
						ldst.put(ldstn);
					});
				});
			}
		});
		JSONObject g = new JSONObject();
		g.put("nodes", nodes);
		g.put("edges", edges);
		return g;
	}
}
