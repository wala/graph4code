package com.ibm.wala.codeBreaker.turtle;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UTFDataFormatException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.jena.atlas.lib.IRILib;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.ibm.wala.cast.ipa.callgraph.AstCFAPointerKeys;
import com.ibm.wala.cast.ir.ssa.AstGlobalRead;
import com.ibm.wala.cast.ir.ssa.AstLexicalAccess.Access;
import com.ibm.wala.cast.ir.ssa.AstLexicalRead;
import com.ibm.wala.cast.ir.ssa.AstPropertyRead;
import com.ibm.wala.cast.ir.ssa.AstPropertyWrite;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.python.client.PythonAnalysisEngine;
import com.ibm.wala.cast.python.ipa.callgraph.PythonSSAPropagationCallGraphBuilder;
import com.ibm.wala.cast.python.ssa.PythonInvokeInstruction;
import com.ibm.wala.cast.python.types.PythonTypes;
import com.ibm.wala.cast.python.util.PythonInterpreter;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.tree.impl.LineNumberPosition;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.DelegatingContext;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.propagation.AbstractFieldPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.ConcreteTypeKey;
import com.ibm.wala.ipa.callgraph.propagation.ConstantKey;
import com.ibm.wala.ipa.callgraph.propagation.FilteredPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceFieldKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKeyFactory;
import com.ibm.wala.ipa.callgraph.propagation.PointsToSetVariable;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.ReceiverTypeContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallerSiteContext;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.PhiStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ReflectiveMemberAccess;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MemberReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.EmptyIterator;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2List;
import com.ibm.wala.util.collections.IteratorPlusOne;
import com.ibm.wala.util.collections.MapIterator;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.AbstractGraph;
import com.ibm.wala.util.graph.EdgeManager;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.graph.NodeManager;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;
import com.ibm.wala.util.graph.labeled.SlowSparseNumberedLabeledGraph;
import com.ibm.wala.util.graph.traverse.SlowDFSFinishTimeIterator;
import com.ibm.wala.util.intset.EmptyIntSet;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.OrdinalSet;

import util.CleansedTypeInference;

public class PythonTurtleAnalysisEngine extends PythonAnalysisEngine<NumberedLabeledGraph<PythonTurtleAnalysisEngine.TurtlePath,PythonTurtleAnalysisEngine.EdgeType>> {

	public static boolean DEBUG = false;
	static String graph4codeNamespace = "http://purl.org/twc/graph4code/";
	static String sioNamespace = "http://semanticscience.org/resource/";

	static CleansedTypeInference typeInference = new CleansedTypeInference("cleansed_static_types.txt", "cleansed_docstr.txt", "functions.map", "classes.map");

	public enum EdgeClass {
		DATA, CONTROL
	}

	public static class EdgeType { 
		final EdgeClass edgeClass;
		public int index;
		public String name;

		public EdgeType(EdgeClass edgeClass, int i) {
			this.edgeClass = edgeClass;
			this.index = i;
		}

		public EdgeType(EdgeClass edgeClass, String name) {
			this.edgeClass = edgeClass;
			this.name = name;
		}

		public boolean data() {
			return EdgeClass.DATA.equals(edgeClass);
		}

		public boolean isNamed() {
			return name != null;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((edgeClass == null) ? 0 : edgeClass.hashCode());
			result = prime * result + index;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
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
			EdgeType other = (EdgeType) obj;
			if (edgeClass != other.edgeClass)
				return false;
			if (index != other.index)
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}

	}

	protected TurtleSummary turtles;

	protected int timeLimitMS = 300000;


	public static Dataset graphToRDF(String graphURI, NumberedLabeledGraph<TurtlePath, EdgeType> analysis) {
		Dataset ds = DatasetFactory.create();
		String sioNamespace = "http://semanticscience.org/resource/";
		String graph4codeNamespace = "http://purl.org/twc/graph4code/";

		Model model = ModelFactory.createDefaultModel();
		Property immediatelyPrecedes = model.createProperty(sioNamespace + "SIO_000250");

		List<org.apache.jena.rdf.model.Statement> statements = new LinkedList<org.apache.jena.rdf.model.Statement>();

		for(TurtlePath tp : analysis) {
			StmtIterator stmts = tp.toRDFStatements();
			while (stmts.hasNext()) {
				statements.add(stmts.next());
			}
		}

		model.add(statements);

		Property dataflow = model.createProperty(graph4codeNamespace + "flowsTo");
		Property ordinalPosition = model.createProperty(sioNamespace + "SIO_000613");
		Resource parameter = model.createResource(sioNamespace + "SIO_000144");
		Property name_prop = model.createProperty(sioNamespace + "SIO_000116");
		Property hasInput = model.createProperty(sioNamespace  + "SIO_000230");
		Property isSpecializationOf = model.createProperty("http://www.w3.org/ns/prov#isSpecializationOf");


		analysis.forEach((TurtlePath src) -> {
			analysis.getSuccLabels(src).forEachRemaining((label) -> {
				analysis.getSuccNodes(src, label).forEachRemaining((dst) -> {
					Resource source = model.getResource(graph4codeNamespace + "se" + src.id());
					Resource dest = model.getResource(graph4codeNamespace + "se" + dst.id());
					if (label.edgeClass==EdgeClass.CONTROL) {
						source.addProperty(immediatelyPrecedes, dest);
					} else {
						assert label.edgeClass == EdgeClass.DATA;
						source.addProperty(dataflow, dest);
						Resource blank = model.createResource(AnonId.create());
						blank.addProperty(RDF.type, parameter);
						source.addProperty(hasInput, blank);
						if (label.isNamed()) {
							blank.addProperty(name_prop, model.createTypedLiteral(label.name));							
						} else {
							blank.addProperty(ordinalPosition, model.createTypedLiteral(label.index));
						}
						blank.addProperty(isSpecializationOf, dest);

					}
				});
			});
		});

		ds.addNamedModel(Normalizer.normalize(IRILib.encodeUriPath(graphURI), Normalizer.Form.NFKC), model);
		return ds;
	}


	public static Dataset RDFFromJSON(NumberedLabeledGraph<TurtlePath, EdgeType> analysis, String graphURI, boolean detail) {
		Dataset ds = DatasetFactory.create();

		Model model = ModelFactory.createDefaultModel();

		Property immediatelyPrecedes = model.createProperty(sioNamespace + "SIO_000250");
		JSONArray data = graphToJSON(analysis, detail);

		for (Object n : data) {
			if (JSONObject.NULL.equals(n)) {
				continue;
			}
			JSONObject node = (JSONObject) n;
			// handle node's attributes toRDF
			Resource source = nodeToRDF(node, model);

			JSONObject edges = node.getJSONObject("edges");
			if (edges.has("flowsTo")) {
				JSONObject flows = edges.getJSONObject("flowsTo");
				handleDataFlow(model, source, flows);
			}
			if (edges.has("immediatelyPrecedes")) {
				JSONArray control = edges.getJSONArray("immediatelyPrecedes");
				for (Object a : control) {
					int ai = (int) a;
					source.addProperty(immediatelyPrecedes, model.getResource(graph4codeNamespace + "se" + ai));
				}
			}
		}

		ds.addNamedModel(Normalizer.normalize(IRILib.encodeUriPath(graphURI), Normalizer.Form.NFKC), model);
		return ds;
	} 

	private static String convertJSONArrayToString(JSONArray arr, String sep) {
		StringBuffer buf = new StringBuffer();
		int i = 0;
		for (Object a : arr) {
			buf.append((String) a);
			if (i < arr.length() - 1) {
				buf.append(sep);
			}
		}
		return buf.toString();
	}

	public static Resource nodeToRDF(JSONObject node, Model model) {
		Resource source = model.getResource(graph4codeNamespace + "se" + node.getInt("nodeNumber"));
		Property name_prop = model.createProperty(sioNamespace + "SIO_000116");
		Property hasInput = model.createProperty(sioNamespace  + "SIO_000230");
		Property hasOrdinalPosition = model.createProperty(sioNamespace + "SIO_000613");

		Property isLocatedIn = model.createProperty(sioNamespace + "SIO_000061");
		Property sourceText = model.createProperty("http://schema.org/text");
		Property sourceLines = model.createProperty(graph4codeNamespace + "sourceLines");
		Property valueNames = model.createProperty(graph4codeNamespace + "valueNames");
		Property normalizedLabel = model.createProperty(graph4codeNamespace + "normalizedLabel");

		Property about = model.createProperty("http://schema.org/about");
		source.addProperty(about, model.createLiteral(node.getJSONArray("path").getString(node.getJSONArray("path").length() - 1)));

		source.addProperty(RDFS.label, model.createLiteral(convertJSONArrayToString(node.getJSONArray("path"), ".")));
		if (node.has("sourceLocation")) {
			source.addProperty(isLocatedIn, model.createLiteral(node.get("sourceLocation").toString()));
		}
		if (node.has("sourceText")) {
			source.addProperty(sourceText, model.createLiteral(node.getString("sourceText")));
		}
		if (node.has("sourceLines")) {
			source.addProperty(sourceLines, model.createLiteral(convertJSONArrayToString(node.getJSONArray("sourceLines"), "\n")));
		}
		if (node.has("normalizedLabel")) {
			source.addProperty(normalizedLabel, model.createLiteral(node.getString("normalizedLabel")));
		}

		if (node.has("value_names")) {
			for (Object a : node.getJSONArray("value_names")) {
				source.addProperty(valueNames, model.createLiteral((String) a));
			}
		}
		Resource isImport = model.createResource(graph4codeNamespace + "Imported");
		if (node.getBoolean("is_import")) {
			source.addProperty(RDF.type, isImport);
		}

		JSONObject args = node.getJSONObject("constant_positional_args");

		handleArgs(model, source, hasInput, hasOrdinalPosition, args);

		args = node.getJSONObject("constant_named_args");

		handleArgs(model, source, hasInput, name_prop, args);

		JSONArray reads = node.getJSONArray("reads");
		Property read = model.createProperty(graph4codeNamespace + "read");
		handleAccesses(model, source, read, reads);
		JSONArray writes = node.getJSONArray("writes");
		Property write = model.createProperty(graph4codeNamespace + "write");
		handleAccesses(model, source, write, writes);
		return source;
	}

	private static void handleDataFlow(Model model, Resource source, final JSONObject flow) {
		Property flowsTo = model.createProperty(graph4codeNamespace + "flowsTo");
		Property ordinalPosition = model.createProperty(sioNamespace + "SIO_000613");

		flow.keys().forEachRemaining((x) -> {
			JSONArray arr = flow.getJSONArray(x);
			for (Object a : arr) {
				int ai = (int) a;
				org.apache.jena.rdf.model.Statement stmt =  model.createStatement(source, flowsTo, model.createResource(graph4codeNamespace + "se" + ai));
				Resource res = model.createResource(stmt);
				res.addProperty(ordinalPosition, model.createLiteral(x));
			}
		});
	}

	private static void handleArgs(Model model, Resource source, Property hasInput, Property hasOrdinalPosition,
			final JSONObject args) {
		args.keys().forEachRemaining((x) -> {
			org.apache.jena.rdf.model.Statement stmt =  model.createStatement(source, hasInput, model.createLiteral(args.get(x).toString()));
			Resource res = model.createResource(stmt);
			res.addProperty(hasOrdinalPosition, model.createLiteral(x));
		});
	}


	private static void handleAccesses(Model model, Resource source, Property read, JSONArray reads) {
		Property hasExpression = model.createProperty(sioNamespace + "SIO_000420");

		for (Object a : reads) {
			JSONObject r = (JSONObject) a;
			Object val = r.get("field");
			RDFNode field;
			if (val instanceof Integer) {
				field = model.createResource(graph4codeNamespace + "se" + val);
			} else {
				field = model.createLiteral(val.toString());
			}
			JSONArray containers = r.getJSONArray("container");
			for (Object c : containers) {
				Resource container = model.createResource(graph4codeNamespace + "se" + c.toString());
				org.apache.jena.rdf.model.Statement stmt =  model.createStatement(source, read, field);
				Resource res = model.createResource(stmt);
				res.addProperty(hasExpression, field);
			}
		}
	}

	public static JSONArray graphToJSON(NumberedLabeledGraph<TurtlePath, EdgeType> analysis, boolean detail) {
		JSONArray stuff = new JSONArray();
		for(TurtlePath tp : analysis) {
			JSONObject elt = tp.toJSON(detail);
			elt.put("edges", new JSONObject());
			elt.put("nodeNumber", analysis.getNumber(tp));
			stuff.put(analysis.getNumber(tp), elt);
		}
		analysis.forEach((TurtlePath src) -> {
			analysis.getSuccLabels(src).forEachRemaining((label) -> {
				JSONArray succ = new JSONArray();
				analysis.getSuccNodes(src, label).forEachRemaining((TurtlePath dst) -> {
					succ.put(analysis.getNumber(dst));
				});
				JSONObject turtle = (JSONObject)((JSONObject)stuff.get(analysis.getNumber(src))).get("edges");
				String key = label.edgeClass == EdgeClass.CONTROL ? "immediatelyPrecedes" : "flowsTo";
				if (label.edgeClass==EdgeClass.CONTROL) {
					turtle.put(key, succ);
				} else {
					if (! turtle.has(key)) {
						turtle.put(key, new JSONObject());
					}

					JSONObject arr = (JSONObject)turtle.get(key);
					String innerLabel = String.valueOf(label.isNamed()? label.name: label.index);

					arr.put(innerLabel, succ);
				}
			});
		});
		return stuff;
	}

	@Override
	protected PythonSSAPropagationCallGraphBuilder getCallGraphBuilder(IClassHierarchy cha, AnalysisOptions options, IAnalysisCacheView cache2) {
		PythonSSAPropagationCallGraphBuilder builder = super.getCallGraphBuilder(cha, options, cache2);

		builder.setContextSelector(new ContextSelector() {
			private final ContextSelector base = builder.getContextSelector();
			private final ReceiverTypeContextSelector recvr = new ReceiverTypeContextSelector();

			@Override
			public Context getCalleeTarget(CGNode caller, CallSiteReference site, IMethod callee,
					InstanceKey[] actualParameters) {
				Context baseContext = base.getCalleeTarget(caller, site, callee, actualParameters);
				if (turtles.isTurtleMethod(callee)) {
					if (DEBUG) System.out.println("tc for " + callee);
					return new DelegatingContext(
							recvr.getCalleeTarget(caller, site, callee, actualParameters),
							new DelegatingContext(
									new CallerSiteContext(caller, site),
									baseContext));
				} else {
					return baseContext;
				}
			}

			@Override
			public IntSet getRelevantParameters(CGNode caller, CallSiteReference site) {
				return base.getRelevantParameters(caller, site);
			} 
		});

		return builder;
	}
	
	@Override
	protected PythonSSAPropagationCallGraphBuilder makeBuilder(IClassHierarchy cha, AnalysisOptions options,
			IAnalysisCacheView cache) {
		return new PythonSSAPropagationCallGraphBuilder(cha, options, cache, new AstCFAPointerKeys()) {
			private final FilteredPointerKey.SingleClassFilter filter = new FilteredPointerKey.SingleClassFilter(cha.lookupClass(TurtleSummary.turtleClassRef));
			
			@Override
			protected boolean fixpointCallback() {
				Set<IClass> turtleSupers = HashSetFactory.make();
				
				PointerAnalysis<InstanceKey> PA = getPointerAnalysis();
				Iterator<PointerKey> pks = getPropagationSystem().iteratePointerKeys();
				while (pks.hasNext()) {
					PointerKey pk = pks.next();
					if (pk instanceof InstanceFieldKey) {
						InstanceFieldKey ifk = (InstanceFieldKey)pk;
						if (ifk.getField().getName().toString().startsWith("missing_")) {
							InstanceKey containerCls = ifk.getInstanceKey();
							OrdinalSet<InstanceKey> pts = PA.getPointsToSet(ifk);
							for(InstanceKey ik : pts) {
								IClass cls = ik.getConcreteType();
								while (cls != null) {
									if (turtles.isTurtleClass(cls)) {
										turtleSupers.add(containerCls.getConcreteType());
									}
									cls = cls.getSuperclass();
								}
							}
						}
					}
				}
						
				System.err.println("turtle supers: " + turtleSupers);
				
				Map<PointerKey, InstanceKey> add = HashMapFactory.make();
				Iterator<PointerKey> allPtrKeys = getPropagationSystem().iteratePointerKeys();
				while (allPtrKeys.hasNext()) {
					PointerKey ptrKey = allPtrKeys.next();
					if (ptrKey instanceof InstanceFieldKey) {
						InstanceKey containerKey = ((InstanceFieldKey)ptrKey).getInstanceKey();
						IClass containerType = containerKey.getConcreteType();
						if (turtleSupers.contains(containerType)) {
							for(IField f : containerType.getAllFields()) {
								PointerKey fieldKey = pointerKeyFactory.getPointerKeyForInstanceField(containerKey, f);
								OrdinalSet<InstanceKey> ptrs = PA.getPointsToSet(fieldKey);
								if (ptrs.isEmpty()) {
									TypeReference fieldTurtle = TypeReference.findOrCreate(containerType.getClassLoader().getReference(), containerType.getName() + "#" + f.getName());
									IClass tc = turtles.ensureTurtleClass(fieldTurtle);
									add.put(fieldKey, new ConcreteTypeKey(tc));
									
									MethodReference ctor = MethodReference.findOrCreate(containerType.getReference(), AstMethodReference.fnSelector);
									callGraph.getNodes(ctor).forEach(cn -> { 
										PointerKey ret = pointerKeyFactory.getPointerKeyForReturnValue(cn);
										PA.getPointsToSet(ret).forEach(tk -> { 
											PointerKey fk = pointerKeyFactory.getPointerKeyForInstanceField(tk, f);
											add.put(fk, new ConcreteTypeKey(tc));
										});
									});
								}
							}
						}
					}
				}
				
				add.forEach((p, i) -> { 
					system.newConstraint(p, i);
				});
				
				return !add.isEmpty();
			}
				  
			@Override
			public PythonConstraintVisitor makeVisitor(CGNode node) {
				return new PythonConstraintVisitor(this, node) {

					@Override
					protected ReflectedFieldAction fieldReadAction(PointerKey lhs) {
						System.err.println("creating field read action for " + lhs);
						(new Throwable()).printStackTrace(System.err);
						return new FieldReadAction(lhs) {
							@Override
							public void action(AbstractFieldPointerKey fieldKey, PointerKey objectKey) {
								IClass parentClass = fieldKey.getInstanceKey().getConcreteType();
								System.err.println("checking field " + fieldKey + " of " + parentClass);
								if (turtles.isTurtleClass(parentClass)) {
									TypeReference parent = parentClass.getReference();
									if (fieldKey instanceof InstanceFieldKey) {
										Atom nm = ((InstanceFieldKey) fieldKey).getField().getName();
										String fieldName = parent.getName().toUnicodeString();
										if (nm.toString().equals("loc")) {
											system.newConstraint(lhs, assignOperator, objectKey);
										} else if (!fieldName.endsWith("#" + nm) && !fieldName.contains("#" + nm + "#")) {
											TypeReference fieldTurtle = TypeReference.findOrCreate(parent.getClassLoader(), parent.getName() + "#" + nm);
											if (DEBUG) System.out.println(fieldTurtle);
											system.newConstraint(lhs, new ConcreteTypeKey(turtles.ensureTurtleClass(fieldTurtle)));
										} else {
											system.newConstraint(lhs, new ConcreteTypeKey(parentClass));
										}
									} else {
										system.newConstraint(lhs, new ConcreteTypeKey(parentClass));
									}
								} else {
									super.action(fieldKey, objectKey);
								}
							} 
						};
					}

					@Override
					public void visitBinaryOp(SSABinaryOpInstruction instruction) {
						super.visitBinaryOp(instruction);
						propagateTurtles(instruction);
					}

					protected void propagateTurtles(SSAInstruction instruction) {
						PointerKey lhs = getPointerKeyForLocal(instruction.getDef());
						if (! system.isImplicit(lhs)) {
							for(int i = 0; i < instruction.getNumberOfUses(); i++) {
								PointerKey rhs = getFilteredPointerKeyForLocal(instruction.getUse(i), filter);
								if (!system.isImplicit(rhs)) {
									class Propagate extends UnaryOperator<PointsToSetVariable> {
										private PointerKey lhs() {
											return lhs;
										}

										@Override
										public byte evaluate(PointsToSetVariable lhs, PointsToSetVariable rhs) {
											MutableIntSet nv = IntSetUtil.make();
											if (rhs.getValue() != null) {
												rhs.getValue().foreach(idx -> { 
													InstanceKey obj = system.getInstanceKey(idx);
													assert turtles.isTurtleClass(obj.getConcreteType()) : "should filter for turtles";
													if (obj.getClass().getName().endsWith("/expr")) {
														nv.add(idx);
													} else {
														TypeReference ref = obj.getConcreteType().getReference();
														TypeReference expr = TypeReference.findOrCreate(ref.getClassLoader(), ref.getName()+ "/expr");
														IClass ecls = turtles.ensureTurtleClass(expr);
														InstanceKey exprk = getInstanceKeyForAllocation(NewSiteReference.make(instruction.iIndex(), ecls.getReference()));
														assert exprk != null;
														nv.add(system.findOrCreateIndexForInstanceKey(exprk));
													}
												});
											}
											return lhs.addAll(nv)? CHANGED: NOT_CHANGED;
										}

										@Override
										public int hashCode() {
											return lhs.hashCode();
										}

										@Override
										public boolean equals(Object o) {
											return (o instanceof Propagate) && ((Propagate)o).lhs() == lhs;
										}

										@Override
										public String toString() {
											// TODO Auto-generated method stub
											return null;
										}
									}

									system.newConstraint(lhs, new Propagate(), rhs);
								}
							}
						}
					}

					@Override
					public void visitUnaryOp(SSAUnaryOpInstruction instruction) {
						super.visitUnaryOp(instruction);
						propagateTurtles(instruction);
					}

					@Override
					public void visitComparison(SSAComparisonInstruction instruction) {
						super.visitComparison(instruction);
						propagateTurtles(instruction);
					}

					@Override
					public void visitArrayStore(SSAArrayStoreInstruction inst) {
						super.visitArrayStore(inst);
						propagateTurtles(inst);
					}					
				};
			}
		};
	}


	@Override
	protected void addBypassLogic(IClassHierarchy cha, AnalysisOptions options) {
		super.addBypassLogic(cha, options);

		turtles = new TurtleSummary(this);

		//addSummaryBypassLogic(options, "ai4code_turtles.xml");
		options.setSelector(new TurtleImportTargetSelector(turtles, options.getMethodTargetSelector()));

		turtles.analyzeWithTurtles(options);
	}

	public static boolean isSyntheticTurtle(MemberReference ref) {
		return "turtle".endsWith(toPathElement(ref));
	}

	public static String toPathElement(MemberReference ref) {
		try {
			return ref.getName().toUnicodeString();
		} catch (UTFDataFormatException e) {
			assert false : e;
			return null;
		}

	}

	private static Object readData(Supplier<Object> getValue, Supplier<List<List<MemberReference>>> getPath) {
		Object val = getValue.get();
		if (val != null) {
			return val;
		} else {
			JSONArray arg = new JSONArray();
			getPath.get().forEach((elt) -> {
				JSONArray eltJson = new JSONArray();
				elt.forEach((name) -> {
					eltJson.put(toPathElement(name));
				});
				arg.put(eltJson);
			});
			return arg;
		}
	}

	public static interface TurtlePath {
		int id();
		Statement statement();
		PointerKey value();
		List<MemberReference> path();
		Position position();
		Position argumentPosition(int use);
		List<List<MemberReference>> argumentPath(int i);
		Object argumentValue(int i);
		List<List<MemberReference>> namePath(String name);
		Object nameValue(String name);
		int arguments();
		Iterator<String> names();
		boolean isImport();
		boolean isSlice();
		Collection<Pair<TurtlePath, Either<String, Supplier<TurtlePath>>>> readsFrom();
		Collection<Pair<TurtlePath, Either<String, Supplier<TurtlePath>>>> writesTo();
		Iterable<Pair<Either<String, List<Either<String,TurtlePath>>>, Either<Object, List<TurtlePath>>>> updates();
		
		default List<Boolean> pathFields() {
			return path().stream().map(ref -> ref instanceof FieldReference).collect(Collectors.toList());
		}
		
		default StmtIterator toRDFStatements() {
			String sioNamespace = "http://semanticscience.org/resource/";
			String graph4codeNamespace = "http://purl.org/twc/graph4code/";
			Model model = ModelFactory.createDefaultModel();
			StringBuffer buf = new StringBuffer();
			List<MemberReference> paths = path();
			boolean first = true;
			for(MemberReference ref : paths) {
				if (! isSyntheticTurtle(ref)) {
					if (first) {
						first = false;
					} else {
						buf.append('.');
					}
					buf.append(toPathElement(ref));
				}
			}
			MemberReference end = paths.get(0);
			Resource turtle = model.createResource(graph4codeNamespace + "se"+id());
			Property ordinalPosition = model.createProperty(sioNamespace + "SIO_000613");
			Resource parameter = model.createResource(sioNamespace + "SIO_000144");
			Property hasInput = model.createProperty(sioNamespace  + "SIO_000230");

			Resource softwareExecution = model.createResource("http://semanticscience.org/resource/SIO_000667"); 
			Resource informationProcessing = model.createResource("http://semanticscience.org/resource/SIO_000649");
			turtle.addProperty(RDF.type, 
					statement().getKind()==Kind.NORMAL && 
					((NormalStatement)statement()).getInstruction() instanceof SSAAbstractInvokeInstruction // &&
					/* callSiteHas(CG, statement().getNode(), (SSAAbstractInvokeInstruction)((NormalStatement)statement()).getInstruction(), n -> n.toString().contains("turtle")) */?
							softwareExecution:
								informationProcessing);

			Literal path = model.createLiteral(buf.toString());
			Property about = model.createProperty("http://schema.org/about");
			turtle.addProperty(RDFS.label, path);
			turtle.addProperty(about, model.createLiteral(toPathElement(end)));
			Position position = position();

			Property isLocatedIn = model.createProperty(sioNamespace + "SIO_000061");
			Resource pos = recordPosition(graph4codeNamespace, model, position);
			if (pos != null) {
				turtle.addProperty(isLocatedIn, pos);
			}

			for(int i = 0; i < arguments(); i++) {
				Position ap = argumentPosition(i);
				if (ap != null) {
					Resource arg = model.createResource(AnonId.create());
					arg.addProperty(RDF.type, parameter);
					arg.addLiteral(ordinalPosition, model.createTypedLiteral(i));
					Resource argpos = recordPosition(graph4codeNamespace, model, ap);
					arg.addProperty(isLocatedIn, argpos);
					pos.addProperty(hasInput, arg);
				}
			}

			Property text = model.createProperty("http://schema.org/text");
			try {
				if (position != null) {
					String sourceText = new SourceBuffer(position).toString();
					if (sourceText != null && sourceText.length() < 50) {
						turtle.addProperty(text, model.createTypedLiteral(sourceText));
					}
				}
			} catch (IOException e) {
				assert false;
			}

			if (isImport()) {
				Resource isImport = model.createResource(graph4codeNamespace + "Imported");
				turtle.addProperty(RDF.type, isImport);
			}

			Property isPartOf = model.createProperty(sioNamespace + "SIO_000068");

			PointerKey v = value();
			if (v instanceof LocalPointerKey) {
				turtle.addLiteral(isPartOf, model.createLiteral(((LocalPointerKey) v).getNode().getMethod().getDeclaringClass().getName().toUnicodeString()));
			}

			Property hasValue = model.createProperty(sioNamespace + "SIO_000300");
			Property name_prop = model.createProperty(sioNamespace + "SIO_000116");
			Property flowsTo = model.createProperty(graph4codeNamespace  + "flowsTo");

			for(int i = 0; i < arguments(); i++) {
				if (argumentValue(i) != null) {
					Resource arg = model.createResource(AnonId.create());
					arg.addProperty(RDF.type, parameter);
					arg.addLiteral(ordinalPosition, model.createTypedLiteral(i));
					turtle.addProperty(hasInput, arg);

					arg.addLiteral(hasValue, model.createTypedLiteral(argumentValue(i)));
					arg.addProperty(flowsTo, turtle);
				}				
			}

			names().forEachRemaining(name -> {
				Resource arg = model.createResource(AnonId.create());	
				if (nameValue(name) != null) {
					arg.addProperty(RDF.type, parameter);
					arg.addLiteral(hasValue, model.createLiteral(String.valueOf(nameValue(name))));
					arg.addLiteral(name_prop, model.createTypedLiteral(name));
					turtle.addProperty(hasInput, arg);
					arg.addProperty(flowsTo, turtle);
				}
			});

			Property read = model.createProperty(graph4codeNamespace + "read");
			Property hasExpression = model.createProperty(sioNamespace + "SIO_000420");

			readsFrom().forEach(p -> {
				Resource readContainer = model.createResource(graph4codeNamespace + "se" + p.fst.id());
				Resource readResource = model.createResource(AnonId.create());
				turtle.addProperty(read, readResource);
				readResource.addProperty(isPartOf, readContainer);
				if (p.snd.isLeft()) {
					Literal fieldName = model.createLiteral(p.snd.getLeft());
					readResource.addProperty(hasValue, fieldName);
				} else {
					if (p.snd.getRight().get() != null) {
						Resource expression = model.createResource(graph4codeNamespace + "se" + p.snd.getRight().get().id());
						readResource.addProperty(hasExpression, expression);
					}
				}

			});

			Property write = model.createProperty(graph4codeNamespace + "write");
			writesTo().forEach(p -> {
				Resource writeContainer = model.createResource(graph4codeNamespace + "se" + p.fst.id());
				Resource writeResource = model.createResource(AnonId.create());
				turtle.addProperty(write, writeResource);
				writeResource.addProperty(isPartOf, writeContainer);
				if (p.snd.isLeft()) {
					Literal fieldName = model.createLiteral(p.snd.getLeft());
					writeResource.addProperty(hasValue, fieldName);
				} else {
					if (p.snd.getRight().get() != null) {
						Resource expression = model.createResource(graph4codeNamespace + "se" + p.snd.getRight().get().id());
						writeResource.addProperty(hasExpression, expression);
					}
				}
			});

			return model.listStatements();
		}

		default Resource recordPosition(String graph4codeNamespace, Model model, Position position) {
			if (position != null) {
				Resource pos = model.createResource(AnonId.create());			
				Property firstLine = model.createProperty(graph4codeNamespace + "firstLine");
				Property lastLine = model.createProperty(graph4codeNamespace + "lastLine");
				Property firstCol = model.createProperty(graph4codeNamespace + "firstCol");
				Property lastCol = model.createProperty(graph4codeNamespace + "lastCol");
				Property firstOffset = model.createProperty(graph4codeNamespace + "firstOffset");
				Property lastOffset = model.createProperty(graph4codeNamespace + "lastOffset");
				if (position.getFirstCol() != -1) 
					pos.addLiteral(firstCol, model.createTypedLiteral(position.getFirstCol()));
				if (position.getLastCol() != -1)
					pos.addLiteral(lastCol, model.createTypedLiteral(position.getLastCol()));
				if (position.getFirstLine() != -1)
					pos.addLiteral(firstLine, model.createTypedLiteral(position.getFirstLine()));
				if (position.getLastLine() != -1)
					pos.addLiteral(lastLine, model.createTypedLiteral(position.getLastLine()));
				if (position.getFirstOffset() != -1)
					pos.addLiteral(firstOffset, model.createTypedLiteral(position.getFirstOffset()));
				if (position.getLastOffset() != -1)
					pos.addLiteral(lastOffset, model.createTypedLiteral(position.getLastOffset()));

				Property text = model.createProperty("http://schema.org/text");
				try {
					String sourceText = new SourceBuffer(position).toString();
					if (sourceText != null && sourceText.length() < 150) {
						pos.addProperty(text, model.createTypedLiteral(sourceText));
					}
				} catch (IOException e) {
					assert false;
				}

				return pos;
			} else {
				return null;
			}
		}

		default JSONArray pathArray() {
			boolean first = true;
			JSONArray path = new JSONArray();
			for(MemberReference ref : path()) {
				String elt = toPathElement(ref);
				path.put(first? elt.substring(1): elt);
				first = false;
			}
			return path;
		}

		default String pathToString(JSONArray array) {
			StringBuffer buf = new StringBuffer();
			int i = 0;
			for (Object a : array) {
				String s = (String) a;
				buf.append(s);
				if (i < array.length() - 1) {
					buf.append('.');
				}
				i++;
			}
			return buf.toString();
		}


		default JSONObject toJSON(boolean detail) {

			JSONObject json = new JSONObject();
			JSONArray path = pathArray();

			json.put("path", path);
			//if (detail) {
				json.put("path_end", path.get(path.length()-1));
			//}

			String l = pathToString(path);

			json.put("normalizedLabel", typeInference.getNodeLabel(l));

			PointerKey v = value();

			Position position = position();
			if (position != null && position.getURL() != null) {
				JSONObject loc = positionAsJSON(position);
				json.put("sourceLocation", loc);
				try {
					JSONArray lines = new JSONArray();
					for(int i = position.getFirstLine(); i <= position.getLastLine(); i++) {
						String line = new SourceBuffer(new LineNumberPosition(position.getURL(), position.getURL(), i) {

							@Override
							public Reader getReader() throws IOException {
								return position.getReader();
							} 

						}).toString();
						if (line.contains("\n")) {
							line = line.substring(0, line.indexOf('\n'));
						}	
						lines.put(line);
					}
					json.put("sourceLines", lines);
					String sourceString = new SourceBuffer(position).toString();
					json.put("sourceText", sourceString);
											
					if (v instanceof LocalPointerKey) {
						Set<String> names = HashSetFactory.make();
						LocalPointerKey lv = (LocalPointerKey)v;
						CGNode node = ((LocalPointerKey) v).getNode();
						int vn = lv.getValueNumber();
						DefUse du = node.getDU();
						IR ir = node.getIR();
					
						IteratorPlusOne.make(du.getUses(vn), du.getDef(vn)).forEachRemaining(x -> {
							String ss[] = ir.getLocalNames(x.iIndex(), vn);
							if (ss != null) {
								for(String s : ss) {
									names.add(s);
								}
							}
						});
						if (! names.isEmpty()) {
							JSONArray arr = new JSONArray();
							for (String n : names) {
								arr.put(n);
							}
							json.put("value_names", arr);
						}
					}
					
				} catch (JSONException | IOException e) {
					assert false;
				}
			}

			json.put("is_import", isImport());

			json.put("is_slice", isSlice());

			if (v instanceof LocalPointerKey) {
				if (detail) {
					CGNode node = ((LocalPointerKey) v).getNode();
					json.put("cgnode_number", node.getGraphNodeId());
					LocalPointerKey lv = (LocalPointerKey)v;
					json.put("cgnode", node.getMethod().getDeclaringClass().getName().toUnicodeString());
					int vn = lv.getValueNumber();
					json.put("vn", vn);
				}
			}


			JSONObject args = new JSONObject();
			for(int i = 0; i < arguments(); i++) {
				int ii = i;
				args.put(Integer.toString(i), readData(() -> { return argumentValue(ii); }, () -> { return argumentPath(ii); }));
			}
			json.put("constant_positional_args", args);

			JSONObject named = new JSONObject();
			names().forEachRemaining((str) -> { named.put(str, readData(() -> { return nameValue(str); }, () -> { return namePath(str); })); });
			json.put("constant_named_args",  named);

			handleAccesses(json, "reads", readsFrom());
			handleAccesses(json, "writes", writesTo());

			Statement s = statement();
			if (s != null && Statement.Kind.NORMAL.equals(s.getKind())) {
				NormalStatement ns = (NormalStatement)s;
				SSAInstruction inst = ns.getInstruction();
				if (inst instanceof SSABinaryOpInstruction) {
					json.put("op", ((SSABinaryOpInstruction)inst).getOperator().toString());
				} else if (inst instanceof SSAUnaryOpInstruction) {
					json.put("op", ((SSAUnaryOpInstruction)inst).getOpcode().toString());
				}if (inst instanceof SSAConditionalBranchInstruction) {
					json.put("op", ((SSAConditionalBranchInstruction)inst).getOperator().toString());
				}
				
			}
			
			JSONArray updates = new JSONArray();
			updates().forEach(u -> {
				JSONArray elt = new JSONArray();
				if (u.fst.isLeft()) {
					elt.put(0, u.fst.getLeft());
				} else {
					JSONArray key = new JSONArray();
					elt.put(0, key);
					u.fst.getRight().stream()
					  .map(x -> x.isLeft()? x.getLeft(): x.getRight().id())
					  .forEach(x -> key.put(x));
				}
				
				if (u.snd.isRight()) {
					System.err.println("right: " + u.snd.getRight());
					JSONArray vs = new JSONArray();
					elt.put(1, vs);
					u.
					  snd.
					  getRight().
					  forEach(
						x -> vs.put(x.id()));
				} else {
					elt.put(1, u.snd.getLeft());
				}
				
				updates.put(elt);
			});
			if (updates.length() > 0) {
				json.put("updates", updates);
			}
			
			return json;
		}

		default void handleAccesses(JSONObject json, String field, Collection<Pair<TurtlePath, Either<String, Supplier<TurtlePath>>>> accessors) {
			Map<Object, JSONArray> fields = HashMapFactory.make();
			accessors.forEach(p -> {
				Object f = p.snd.isLeft()? p.snd.getLeft(): p.snd.getRight().get().id();
				if (! fields.containsKey(f)) {
					fields.put(f, new JSONArray());
				}
				fields.get(f).put(p.fst.id());
			});
			JSONArray reads = new JSONArray();
			fields.entrySet().forEach(es -> { 
				JSONObject x = new JSONObject();
				x.put("field",  es.getKey());
				x.put("container", es.getValue());
				reads.put(x);
			});
			json.put(field, reads);
		}

		default boolean hasSuffix(List<MemberReference> suffix) {
			List<MemberReference> path = path();
			if (suffix.size() > path.size()) {
				return false;
			} else {
				int d = path.size() - suffix.size();
				for(int i = suffix.size()-1; i >= 0; i--) {
					if (! (suffix.get(i).equals(path.get(i+d)))) {
						return false;
					}
				}

				return true;
			}
		}
	}

	public static boolean match(Iterator<MemberReference> path, Iterator<String> pattern) {
		if (! path.hasNext() && !pattern.hasNext()) {
			return true;
		} else if (! path.hasNext() || !pattern.hasNext()) {
			return false;
		} else {
			MemberReference pe = path.next();
			String head = toPathElement(pe);
			String pat = pattern.next();
			if ("*".equals(pat) || head.equals(pat)) {
				return match(path, pattern);
			} if ("**".equals(pat)) {

				Iterator2List<MemberReference> pathList = Iterator2List.toList(path);
				pathList.add(0, pe);

				Iterator2List<String> patternList = Iterator2List.toList(pattern);

				do {
					if (match(pathList.iterator(), patternList.iterator())) {
						return true;
					}
					pathList.remove(0);
				} while (pathList.size() > 0);
			}
		}

		return false;
	}

	protected static SSAInstruction caller(TurtlePath path) {
		PointerKey result = path.value();
		if (result instanceof LocalPointerKey) {
			LocalPointerKey lpk = (LocalPointerKey) result;
			return lpk.getNode().getDU().getDef(lpk.getValueNumber());
		} else {
			return null;
		}
	}

	private static JSONObject positionAsJSON(Position position) {
		JSONObject loc = new JSONObject();
		loc.put("url", position.getURL().toExternalForm());
		loc.put("firstLine",  position.getFirstLine());
		loc.put("lastLine",  position.getLastLine());
		loc.put("firstCol",  position.getFirstCol());
		loc.put("lastCol",  position.getLastCol());
		loc.put("firstOffset",  position.getFirstOffset());
		loc.put("lastOffset",  position.getLastOffset());
		return loc;
	}


	private boolean isImportCall(SSAInstruction def) {
		if (def instanceof SSAAbstractInvokeInstruction) {
			MethodReference target = ((SSAAbstractInvokeInstruction)def).getDeclaredTarget();
			if (target.getName().toString().equals("import")) {

				/*
				String name = target.getDeclaringClass().getName().toString().substring(1);
				for(String s : AbstractParser.defaultImportNames) {
					if (s.equals(name)) {
						return false;
					}
				}
				 */

				return true;
			}
		}

		return false;
	}

	protected void writeFullGraph(String name, PointerKeyFactory pkf, PointerAnalysis<InstanceKey> PA, CallGraph CG,
			Graph<PointsToSetVariable> ptr_G) {
		try (FileOutputStream fo = new FileOutputStream(name)) {
			try (BZip2CompressorOutputStream bs = new BZip2CompressorOutputStream(fo)) {
				try (OutputStreamWriter s = new OutputStreamWriter(bs, "UTF-8")) {
					JSONArray nodes = new JSONArray();
					ptr_G.forEach(n -> {
						String feature = null;
						JSONObject pos = null;
						PointerKey k = n.getPointerKey();
						String text = "";
						JSONArray names = new JSONArray();
						if (k instanceof LocalPointerKey) {
							CGNode cgn = ((LocalPointerKey) k).getNode();
							int vn = ((LocalPointerKey) k).getValueNumber();
							DefUse du = cgn.getDU();
							SSAInstruction inst = du.getDef(vn);
							SymbolTable ST = cgn.getIR().getSymbolTable();
							if (inst != null && inst.iIndex() >= 0) {
								IMethod m = cgn.getMethod();

								if (inst instanceof SSABinaryOpInstruction) {
									feature = ((SSABinaryOpInstruction) inst).getOperator().toString();
								} else if (inst instanceof SSAUnaryOpInstruction) {
									feature = ((SSAUnaryOpInstruction) inst).getOpcode().toString();
								} else if (inst instanceof SSAConditionalBranchInstruction) {
									feature = ((SSAConditionalBranchInstruction) inst).getOperator().toString();
								} else if (inst instanceof SSAAbstractInvokeInstruction) {
									MethodReference callee = ((SSAAbstractInvokeInstruction) inst).getDeclaredTarget();
									CallSiteReference site = ((SSAAbstractInvokeInstruction) inst).getCallSite();
									feature = "call to " + (callee.getSelector().equals(AstMethodReference.fnSelector)
											? CG.getPossibleTargets(cgn, site).stream().map(cle -> {
												IClass cls = cle.getMethod().getDeclaringClass();
												if (turtles.isTurtleClass(cls)) {
													String nm = cle.getContext().get(ContextKey.RECEIVER).toString();
													return nm.substring(nm.lastIndexOf('#') + 1);
												} else {
													return cls.getName().toString();
												}
											}).collect(Collectors.toList()).toString()
													: callee.getName().toString());
								} else if (inst instanceof SSANewInstruction) {
									feature = ((SSANewInstruction) inst).getConcreteType().getName().toString();
									if (feature.contains("/")) {
										feature = feature.substring(feature.lastIndexOf('/') + 1);
									}
									if (feature.matches(".*@[0-9]*:[a-zA-Z_]*$")) {
										feature = feature.substring(feature.lastIndexOf(':') + 1);
									}
									if (feature.matches(".*[@][0-9]*$")) {
										feature = "anonymous";
									}
									feature = "allocate " + feature;
								} else if (inst instanceof AstPropertyRead) {
									int fieldsVn = ((AstPropertyRead) inst).getMemberRef();
									if (ST.isStringConstant(fieldsVn)) {
										Object x = ST.getConstantValue(fieldsVn);
										if (x instanceof String) {
											String fieldName = (String) x;
											feature = "field read of " + fieldName;
										}
									} else {
										PointerKey pk = pkf.getPointerKeyForLocal(cgn,
												((AstPropertyRead) inst).getMemberRef());
										OrdinalSet<InstanceKey> ptrs = PA.getPointsToSet(pk);
										for (InstanceKey ik : ptrs) {
											if (ik instanceof ConstantKey
													&& ((ConstantKey<?>) ik).getValue() instanceof String) {
												if (!"".equals(feature))
													feature += ", ";
												feature += "field read of " + ((ConstantKey<String>) ik).getValue();
											}
										}
									}
								} else if (inst instanceof AstLexicalRead) {
									feature = "lexical read of ";
									for (Access a : ((AstLexicalRead) inst).getAccesses()) {
										feature += a.variableName;
									}
								} else if (inst instanceof AstGlobalRead) {
									feature = "read of " + ((AstGlobalRead) inst).getGlobalName();
								} else if (inst instanceof SSAGetInstruction) {
									feature = "field read of "
											+ ((SSAGetInstruction) inst).getDeclaredField().getName().toString();
								} else if (inst instanceof SSACheckCastInstruction) {
									feature = "filter for " + Arrays
											.toString(((SSACheckCastInstruction) inst).getDeclaredResultTypes());
								} else {
									feature = inst.getClass().toString() + ":" + inst;
								}

								if (m instanceof AstMethod) {
									Position p = ((AstMethod) m).debugInfo().getInstructionPosition(inst.iIndex());
									if (p.getURL() != null) {
										pos = positionAsJSON(p);
									}
									try {
										text = new SourceBuffer(p).toString();
									} catch (IOException e) {
										assert false : e;
									}
								}

								IR ir = cgn.getIR();
								IteratorPlusOne.make(du.getUses(vn), du.getDef(vn)).forEachRemaining(x -> {
									String ss[] = ir.getLocalNames(x.iIndex(), vn);
									if (ss != null) {
										for (String str : ss) {
											names.put(str);
										}
									}
								});

							} else if (vn <= ST.getNumberOfParameters()) {
								feature = "parameter" + (vn - 1);
							} else if (ST.isConstant(vn)) {
								feature = "value " + ST.getConstantValue(vn);
							} else if (cgn.getMethod().isWalaSynthetic()) {
								feature = "turtle code";
							} else {
								feature = "key " + k;
							}
						}
						int id = n.getGraphNodeId();
						JSONObject jn = new JSONObject();
						jn.put("id", id);
						if (!"".equals(text)) {
							jn.put("text", text);
						}

						JSONArray succs = new JSONArray();
						ptr_G.getSuccNodes(n).forEachRemaining(sn -> {
							succs.put(sn.getGraphNodeId());
						});
						if (succs.length() > 0) {
							jn.put("edges", succs);
						}

						JSONArray preds = new JSONArray();
						ptr_G.getPredNodes(n).forEachRemaining(sn -> {
							preds.put(sn.getGraphNodeId());
						});
						if (preds.length() > 0) {
							jn.put("back_edges", preds);
						}

						if (feature != null) {
							jn.put("feature", feature);
						}
						if (pos != null) {
							jn.put("sourcePosition", pos);
						}
						if (names.length() > 0) {
							jn.put("valueNames", names);
						}
						nodes.put(jn);
					});

					JSONObject out = new JSONObject();
					out.put("nodes", nodes);

					Set<String> scripts = CG.getEntrypointNodes().stream()
							.filter(n -> n.getMethod() instanceof AstMethod).map(n -> ((AstMethod) n.getMethod())
									.debugInfo().getCodeBodyPosition().getURL().toExternalForm())
							.collect(Collectors.toSet());
					JSONArray scriptNames = new JSONArray(scripts);
					out.put("scripts", scriptNames);

					out.write(s, 0, 2);
				} catch (IOException e1) {
					assert false : e1;
				}
			}
		} catch (IOException e2) {
			assert false : e2;
		}
	}
	
	@Override
	public NumberedLabeledGraph<TurtlePath, EdgeType> performAnalysis(PropagationCallGraphBuilder builder) throws CancelException {
		class TimeoutException extends RuntimeException {
			private static final long serialVersionUID = 1L;

		}

		try {


			CallGraph CG = builder.getCallGraph();
			HeapModel H = builder.getPointerAnalysis().getHeapModel();
			PointerAnalysis<InstanceKey> PA = builder.getPointerAnalysis();
			Graph<PointsToSetVariable> ptr_G = builder.getPropagationSystem().getFlowGraphIncludingImplicitConstraints();

			Map<Pair<Integer,CGNode>,List<TurtlePath>> allTurtles = HashMapFactory.make();

			@SuppressWarnings("serial")
			Function<PointerKey, Set<PointerKey>> reachable = start -> {
				Set<PointerKey> result = HashSetFactory.make();
				result.add(start);
				if (! builder.getPropagationSystem().isImplicit(start)) {
					PointsToSetVariable sv = builder.getPropagationSystem().findOrCreatePointsToSet(start);
					if (ptr_G.containsNode(sv))
						new SlowDFSFinishTimeIterator<PointsToSetVariable>(ptr_G, sv) {
						@Override
						protected Iterator<PointsToSetVariable> getConnected(PointsToSetVariable n) {
							
							if (n != sv && n.getPointerKey() instanceof LocalPointerKey) {
								LocalPointerKey lpk = (LocalPointerKey) n.getPointerKey();
								Pair<Integer,CGNode> key = Pair.make(lpk.getValueNumber(), lpk.getNode());
								if (allTurtles.containsKey(key)) {
									System.err.println("reject " + lpk);
									return EmptyIterator.instance();
								}
							}
							
							Set<PointsToSetVariable> result = HashSetFactory.make();
							ptr_G.getSuccNodes(n).forEachRemaining(s -> { 
								PointerKey p = s.getPointerKey();
								if (p instanceof LocalPointerKey) {
									if (! turtles.isTurtleMethod(((LocalPointerKey) p).getNode().getMethod())) {
										result.add(s);
									}
								} else if (p instanceof AbstractFieldPointerKey) {
									if (! turtles.isTurtleClass(((AbstractFieldPointerKey)p).getInstanceKey().getConcreteType())) {
										result.add(s);
									}
								} else {
									result.add(s);
								}
							});
							return result.iterator();  
						}
					}.forEachRemaining(s -> result.add(s.getPointerKey()));
				}
				return result;
			};

			CG.forEach(new Consumer<CGNode>() {
				private int id  = 0;

				private List<List<MemberReference>> turtles(CGNode n, int vn) {
					List<List<MemberReference>> result = new LinkedList<>();
					OrdinalSet<InstanceKey> objs = PA.getPointsToSet(H.getPointerKeyForLocal(n, vn));
					for(InstanceKey o : objs) {
						if (turtles.isTurtleClass(o.getConcreteType())) {
							List<MemberReference> turtlePath = new LinkedList<>();
							String path = o.getConcreteType().getName().toUnicodeString();
							for (String part : path.split("/")) {
								String[] elts = part.split("#");	
								turtlePath.add(MethodReference.findOrCreate(TurtleSummary.turtleClassRef, Atom.findOrCreateUnicodeAtom(elts[0]), AstMethodReference.fnDesc));
								for(int i = 1; i < elts.length; i++) {
									turtlePath.add(FieldReference.findOrCreate(TurtleSummary.turtleClassRef, Atom.findOrCreateUnicodeAtom(elts[i]), PythonTypes.Root));
								}
							}
							result.add(turtlePath);
						}
					}
					return result;
				}

				@Override
				public void accept(CGNode cgnode) {
					if (cgnode.getMethod() instanceof AstMethod) {
						IR callerIR = cgnode.getIR();
						DefUse DU = cgnode.getDU();
						for(int vn = 1 ; vn <= callerIR.getSymbolTable().getMaxValueNumber(); vn++) {
							int hack_vn = vn;
							SSAInstruction inst = DU.getDef(vn);
		//					if (inst == null) {
		//						continue;
		//					}
							Pair<Integer, CGNode> loc = Pair.make(vn, cgnode);
							List<TurtlePath> vnPaths = new LinkedList<>();
							for(List<MemberReference> path : turtles(cgnode, vn)) {
								vnPaths.add(new TurtlePath() {
									private int my_id = id++;

									public String toString() {
										return Arrays.toString(pathArray().toList().toArray());
									}

									@Override
									public int id() {
										return my_id;
									}

									@Override
									public Statement statement() {
										if (inst instanceof SSAPhiInstruction) {
											return new PhiStatement(cgnode, (SSAPhiInstruction) inst);
										} else if (inst != null) {
											return new NormalStatement(cgnode, inst.iIndex());
										} else {
											return null;
										}
									}

									@Override
									public PointerKey value() {
										if (inst != null && inst.getDef() > 0) {
											return builder.getPointerKeyForLocal(cgnode, inst.getDef());
										} else {
											return null;
										}
									}

									private Object getValue(int useVn) {
										return getValue(useVn, EmptyIntSet.instance);
									}

									private Object getValue(int useVn, IntSet stack) {
										LocalPointerKey lk = (LocalPointerKey) value();									
										DefUse du = lk.getNode().getDU();
										SymbolTable S = lk.getNode().getIR().getSymbolTable();
										if (S.isBooleanConstant(useVn) || 
												S.isNumberConstant(useVn) || 
												S.isStringConstant(useVn)) 
										{
											return S.getConstantValue(useVn);
										} else {
											boolean hasAnything = false;
											JSONObject result = new JSONObject();
											for(Iterator<SSAInstruction> uses = du.getUses(useVn); uses.hasNext(); ) {
												SSAInstruction use = uses.next();
												int val, ref;
												Object field;
												if (use instanceof SSAPutInstruction) {
													val = ((SSAPutInstruction)use).getVal();
													ref = ((SSAPutInstruction)use).getRef();
													field = ((SSAPutInstruction)use).getDeclaredField().getName();
												} else if (use instanceof AstPropertyWrite) {
													val = ((AstPropertyWrite)use).getValue();
													ref = ((AstPropertyWrite)use).getObjectRef();
													if (S.isConstant(((AstPropertyWrite)use).getMemberRef())) {
														field = S.getConstantValue(((AstPropertyWrite)use).getMemberRef());
													} else {
														continue;
													}
												} else {
													continue;
												}
												if (ref != useVn) {
													continue;
												}

												if (S.isConstant(val)) {
													result.put(field.toString(), S.getConstantValue(val));
													hasAnything = true;
												} else {
													if (du.getDef(val) !=  null && lk.getNode().getMethod() instanceof AstMethod && du.getDef(val).iIndex() != -1) {
														Position p = ((AstMethod)lk.getNode().getMethod()).debugInfo().getInstructionPosition(du.getDef(val).iIndex());
														try {
															SourceBuffer b = new SourceBuffer(p);
															String expr = b.toString();
															Integer pyv = PythonInterpreter.interpretAsInt(expr);
															if (pyv != null) {
																result.put(String.valueOf(field), pyv);
																hasAnything = true;
																continue;
															}
														} catch (IOException e) {
															// not able to evaluate
														}
													} 

													if (! stack.contains(val)) {
														MutableIntSet x = IntSetUtil.makeMutableCopy(stack);
														x.add(val);
														Object value = getValue(val, x);
														if (value != null) {
															result.put(String.valueOf(field), value);
															hasAnything = true;		
														}
													}
												}	
											}
											if (hasAnything) {
												return checkIfList(result);
											}
										}
										return null;
									}

									private Object checkIfList(JSONObject result) {
										MutableIntSet vals = IntSetUtil.make();
										result.keys().forEachRemaining(s -> { 
											try {
												int x = Integer.parseInt(s);
												vals.add(x);
											} catch (NumberFormatException e) {
												// just ignore it
											}
										});
										
										// stuff other than numbers as keys
										if (vals.size() != result.keySet().size()) {
											return result;
										}
										
										// vals not a dense set of numbers 0..n
										if (vals.max() > vals.size()-1) {
											return result;
										}
										
										JSONArray lst = new JSONArray();
										vals.foreach(i -> { 
											if (i >= 0) {
												lst.put(i, result.get("" + i));
											}
										});
										
										return lst;
									}

									@Override
									public Object argumentValue(int i) {
										if (i <= inst.getNumberOfUses()) {
											int useVn = inst.getUse(i);
											return getValue(useVn);
										} else {
											return null;
										}
									}

									@Override
									public Object nameValue(String name) {
										if (inst instanceof PythonInvokeInstruction) {
											PythonInvokeInstruction pi = (PythonInvokeInstruction) inst;
											int useVn = pi.getUse(name);
											if (useVn != -1) {
												return getValue(useVn);
											}
										}

										return null;	
									}

									@Override
									public Position position() {
										if (inst == null || inst.iIndex() == -1) {
											return null;
										} else {
											return ((AstMethod)cgnode.getMethod()).debugInfo().getInstructionPosition(inst.iIndex());
										}
									}

									@Override
									public int arguments() {
										if (inst != null) {
											return inst.getNumberOfUses();
										} else {
											return 0;
										}
									}

									@Override
									public Iterator<String> names() {
										if (inst instanceof PythonInvokeInstruction) {
											return ((PythonInvokeInstruction)inst).getKeywords().iterator();
										} else {
											return EmptyIterator.instance();
										}
									}

									private boolean isImport(SSAInstruction inst) {
										if (isImportCall(inst)) {
											return true;
										} else if (inst instanceof SSAGetInstruction && !((SSAGetInstruction)inst).isStatic()) {
											return isImport(DU.getDef(inst.getUse(0)));
										} else {
											return false;
										}
									}

									@Override
									public boolean isImport() {
										return isImport(inst);
									}

									@Override
									public boolean isSlice() {
										if (inst instanceof SSAAbstractInvokeInstruction) {
											CallSiteReference site = ((SSAAbstractInvokeInstruction)inst).getCallSite();
											Set<CGNode> called = CG.getPossibleTargets(cgnode, site);
											return called.stream().filter(n -> n.getMethod().getDeclaringClass().getName().toString().contains("wala/builtin/slice")).iterator().hasNext();
										} else {
											return false;
										}
									}

									@Override
									public Position argumentPosition(int use) {
										if (inst.iIndex() >= 0) {
											return ((AstMethod)cgnode.getMethod()).debugInfo().getOperandPosition(inst.iIndex(), use);
										} else {
											return null;
										}
									}

									private List<List<MemberReference>> path(int i) {
										Pair<CGNode, Integer> key = Pair.make(cgnode, i);
										if (allTurtles.containsKey(key)) {
											return allTurtles.get(key)
													.stream()
													.reduce(new LinkedList<>(), 
															(l, t) -> { l.add(t.path()); return l; },
															(a, b) -> { a.addAll(b); return a; } );
										} else {
											return Collections.emptyList();
										}
									}

									@Override
									public List<List<MemberReference>> argumentPath(int i) {
										return path(inst.getUse(i));
									}

									@Override
									public List<List<MemberReference>> namePath(String name) {
										if (inst instanceof PythonInvokeInstruction) {
											PythonInvokeInstruction pi = (PythonInvokeInstruction) inst;
											int useVn = pi.getUse(name);
											if (useVn != -1) {
												return path(useVn);
											}
										}

										return Collections.emptyList();	
									}

									@Override
									public List<MemberReference> path() {
										return path;
									}


									private Collection<Pair<TurtlePath, Either<String, Supplier<TurtlePath>>>> accesses(SSAInstruction access) {
										ReflectiveMemberAccess read = (ReflectiveMemberAccess) access;
										int object = read.getObjectRef();
										List<TurtlePath> objPaths = allTurtles.get(Pair.make(object, cgnode));

										if (objPaths == null) {
											return Collections.emptyList();
										}

										int member = read.getMemberRef();
										if (cgnode.getIR().getSymbolTable().isStringConstant(member)) {
											Either<String, Supplier<TurtlePath>> field = Either.forLeft(cgnode.getIR().getSymbolTable().getStringValue(member));
											return objPaths.stream()
													.map(p -> Pair.make(p, field))
													.collect(Collectors.toSet());
										} 

										Pair<Integer, CGNode> memberTurtles = Pair.make(member, cgnode);
										if (allTurtles.containsKey(memberTurtles)) {
											List<TurtlePath> memberPaths = allTurtles.get(memberTurtles);
											Optional<Stream<Pair<TurtlePath, Either<String, Supplier<TurtlePath>>>>> stuff = objPaths.stream()
													.map(p -> {
														Either<String, Supplier<TurtlePath>> field = Either.forRight(() -> p);
														return memberPaths.stream().map(m -> Pair.make(p,  field));
													}).reduce((s, t) -> Stream.concat(s, t));
											if (stuff.isPresent()) {
												return stuff
														.get()
														.collect(Collectors.toSet());
											}
										}

										return Collections.emptyList();
									}

									@Override
									public Collection<Pair<TurtlePath, Either<String, Supplier<TurtlePath>>>> readsFrom() {
										return inst instanceof AstPropertyRead? accesses(inst): Collections.emptyList();
									}

									@Override
									public Collection<Pair<TurtlePath, Either<String, Supplier<TurtlePath>>>>  writesTo() {
										Set<Pair<TurtlePath, Either<String, Supplier<TurtlePath>>>> result = HashSetFactory.make();
											cgnode.getDU().getUses(hack_vn).forEachRemaining(useInst -> { 
												if (useInst instanceof AstPropertyWrite && (inst == null || inst.getDef()==((AstPropertyWrite)useInst).getValue())) {
													result.addAll(accesses(useInst));
												}
											});
										return result;
									}
									
									@Override
									public Iterable<Pair<Either<String, List<Either<String, TurtlePath>>>, Either<Object, List<TurtlePath>>>> updates() {
										System.err.println("updates for " + this);
										PointerKey pk = value();
										List<Pair<Either<String, List<Either<String, TurtlePath>>>, Either<Object, List<TurtlePath>>>> result = new LinkedList<>(); 
										if (pk instanceof LocalPointerKey) {
											System.err.println("updates for " + pk);
											int vn = ((LocalPointerKey)pk).getValueNumber();
											DU.getUses(vn).forEachRemaining(use -> { 
												if (use.getNumberOfUses() > 2 &&
													use.getUse(0) == vn &&
													use instanceof AstPropertyWrite) 
												{
													System.err.println("updates for " + use);
													SymbolTable ST = cgnode.getIR().getSymbolTable();
																										
  													int val = ((AstPropertyWrite)use).getValue();
  													
  													if (!ST.isConstant(val) && !allTurtles.containsKey(Pair.make(val, cgnode))) {
  														return;
  													}
  													
  													Either<Object,List<TurtlePath>> x =
														ST.isConstant(val)?
														Either.forLeft(ST.getConstantValue(val)):
														Either.forRight(allTurtles.get(Pair.make(val, cgnode)));	
  													System.err.println("update value " + x + " " + ST.isConstant(val));
  													
  													int field = ((AstPropertyWrite)use).getMemberRef();
													if (ST.isStringConstant(field)) {
														result.add(Pair.make(Either.forLeft(ST.getStringValue(field)), x));
													} else {
														SSAInstruction d = cgnode.getDU().getDef(field);
														if (d instanceof SSANewInstruction) {
															SSANewInstruction n = (SSANewInstruction)d;
															TypeReference t = n.getNewSite().getDeclaredType();
															if (t.equals(PythonTypes.dict) ||
																t.equals(PythonTypes.tuple) || 
																t.equals(PythonTypes.list))
															{
																System.err.println("updates field is " + t);
																List<Either<String,TurtlePath>> lst = new LinkedList<>();
																cgnode.getDU().getUses(field).forEachRemaining(fUse -> { 
																	if (fUse.getNumberOfUses() > 2 &&
																		fUse.getUse(0) == field &&	
																		fUse instanceof AstPropertyWrite) 
																	{
																		System.err.println("updates with " + fUse);
																		int ev = ((AstPropertyWrite)fUse).getValue();
																		if (ST.isStringConstant(ev)) {
																			lst.add(Either.forLeft(ST.getStringValue(ev)));
																		} else if (allTurtles.containsKey(Pair.make(ev, cgnode))) {
																			for(TurtlePath p : allTurtles.get(Pair.make(ev, cgnode))) {
																				if (p != null) {
																					lst.add(Either.forRight(p));
																				}
																			}
																		}
																	}
																});
																if (lst.size() > 0) {
																	result.add(Pair.make(Either.forRight(lst),  x));
																}
															}
														}
						
													}
												}
											});
											return result;
										} else {
											return Collections.emptyList();
										}
									}

								});
							}
							if (! vnPaths.isEmpty()) {
								allTurtles.put(loc, vnPaths);
							}
						}
					}
				}
			});

			NumberedLabeledGraph<TurtlePath,EdgeType> G = new SlowSparseNumberedLabeledGraph<>(new EdgeType(EdgeClass.CONTROL, -1));
			allTurtles.values().forEach(ts -> ts.forEach(t -> G.addNode(t)));

			allTurtles.values().forEach(ts -> ts.forEach(t -> { 
				
				class Link {
					CGNode du;
					Link(CGNode du) {
						this.du = du;
					}
					private final MutableIntSet seen = IntSetUtil.make();
					private void rec(int v, Consumer<Pair<Integer, CGNode>> f) {
						if (! seen.contains(v)) {
							seen.add(v);
							Pair<Integer, CGNode> key = Pair.make(v, du);
							f.accept(key);
							
							du.getDU().getUses(v).forEachRemaining(use -> { 
								if (use instanceof AstPropertyWrite  && v == use.getUse(2)) {
									int obj = use.getUse(0);
									SSAInstruction d = du.getDU().getDef(obj);
									if (d instanceof SSANewInstruction) {
										SSANewInstruction n = (SSANewInstruction)d;
										TypeReference t = n.getNewSite().getDeclaredType();
										if (t.equals(PythonTypes.dict) ||
											t.equals(PythonTypes.tuple) || 
											t.equals(PythonTypes.list))
										{
											rec(obj, f);
											du.getDU().getUses(obj).forEachRemaining(objUse -> { 
												if (objUse instanceof AstPropertyRead) {
													rec(objUse.getDef(), f);
												}
											});
										}
									}
								}
							});
						}
					}
				};
				
				if (t.value() != null) {
					Set<PointerKey> flow = reachable.apply(t.value());
					System.err.println("######## " + t.value() + " --> " + flow);
					flow.forEach(pk -> { 
						if (pk instanceof LocalPointerKey) {
							LocalPointerKey lpk = (LocalPointerKey)pk;
							DefUse du = lpk.getNode().getDU();
							new Link(lpk.getNode()).rec(lpk.getValueNumber(), tk -> { 
								du.getUses(tk.fst).forEachRemaining(use -> { 
									Pair<Integer, CGNode> targetk = Pair.make(use.getDef(), lpk.getNode());
									if (allTurtles.containsKey(targetk)) {
										for(int i = 0; i < use.getNumberOfUses(); i++) {
											int yuck = i;
											if (use.getUse(i) == tk.fst) {
												allTurtles.get(targetk).forEach(tgt -> G.addEdge(t, tgt, new EdgeType(EdgeClass.DATA, yuck)));
												break;
											}
										}
									}
								});
							});
						}
					});
				}
			}));
			
			System.err.println("step a");
			
			allTurtles.values().forEach(ts -> ts.forEach(t -> { 
				Statement s = t.statement();
				SSAInstruction inst =
						s instanceof NormalStatement?
								((NormalStatement)s).getInstruction():
									s instanceof PhiStatement?
											((PhiStatement)s).getPhi():
												null;
				if (inst != null) {
					class Bad { boolean done = false; }
					Bad bad = new Bad();
					if (inst instanceof SSAAbstractInvokeInstruction) {
						Set<CGNode> targets = CG.getPossibleTargets(s.getNode(), ((SSAAbstractInvokeInstruction) inst).getCallSite());
						for(int i = 0; i < inst.getNumberOfUses(); i++) {
							Pair<Integer,CGNode> src = Pair.make(inst.getUse(i), s.getNode());
							if (allTurtles.containsKey(src)) {
								PointerKey ak = builder.getPointerKeyForLocal(s.getNode(), inst.getUse(i));
								if (! builder.getPropagationSystem().isImplicit(ak)) {
									PointsToSetVariable arg = builder.getPropagationSystem().findOrCreatePointsToSet(ak);
									ptr_G.getSuccNodes(arg).forEachRemaining(ptr_arg -> {
										PointerKey dest = ptr_arg.getPointerKey();
										if (dest instanceof LocalPointerKey && targets.contains(((LocalPointerKey)ptr_arg.getPointerKey()).getNode())) { 
											LocalPointerKey destlk = (LocalPointerKey) dest;
											destlk.getNode().getDU().getUses(destlk.getValueNumber()).forEachRemaining(s_inst -> { 
												Pair<Integer,CGNode> destk = Pair.make(s_inst.getDef(), destlk.getNode());
												if (allTurtles.containsKey(destk)) {
													allTurtles.get(destk).forEach(dt -> {
														for(int u = 0; u < s_inst.getNumberOfUses(); u++) {
															if (s_inst.getUse(u) == destlk.getValueNumber()) {
																bad.done = true;
																G.addEdge(t, dt, new EdgeType(EdgeClass.DATA, u));
															}
														}
													});
												}
											});
										}
									});
								}
							}
						}

						for(CGNode callee : targets) {
							callee.getIR().iterateAllInstructions().forEachRemaining(r_inst -> { 
								if (r_inst instanceof SSAReturnInstruction) {
									Pair<Integer,CGNode> retKey = Pair.make(((SSAReturnInstruction)r_inst).getResult(), callee);
									if (allTurtles.containsKey(retKey)) {
										s.getNode().getDU().getUses(inst.getDef()).forEachRemaining(use -> { 
											for(int u = 0; u < use.getNumberOfUses(); u++) {
												int uv = u;
												if (use.getUse(u) == inst.getDef()) {
													Pair<Integer,CGNode> useKey = Pair.make(use.getDef(), s.getNode());
													if (allTurtles.containsKey(useKey)) {
														allTurtles.get(useKey).forEach(use_t -> { 
															allTurtles.get(retKey).forEach(rk -> {
																bad.done = true;
																G.addEdge(rk, use_t, new EdgeType(EdgeClass.DATA, uv));
															});
														});
													}
												}
											}
										});
									}
								}
							});
						}

					} if (! bad.done) { 
						for(int i = 0; i < inst.getNumberOfUses(); i++) {
							int arg = i;				
							class Link {
								private final MutableIntSet seen = IntSetUtil.make();
								void rec(int v) {
									if (! seen.contains(v)) {
										seen.add(v);
										CGNode du = s.getNode();
										Pair<Integer, CGNode> key = Pair.make(v, du);
										if (allTurtles.containsKey(key)) {
											allTurtles.get(key).forEach(src -> {
												G.addEdge(src, t, new EdgeType(EdgeClass.DATA, arg));
											});
										} else {
											SSAInstruction d = du.getDU().getDef(v);
											if (d instanceof SSANewInstruction) {
												SSANewInstruction n = (SSANewInstruction)d;
												TypeReference t = n.getNewSite().getDeclaredType();
												if (t.equals(PythonTypes.dict) ||
														t.equals(PythonTypes.tuple) || 
														t.equals(PythonTypes.list))
												{
													du.getDU().getUses(v).forEachRemaining(use -> { 
														if (use instanceof AstPropertyWrite) {
															rec(((AstPropertyWrite)use).getValue());
														}
													});
												}
											}
										}
									}
								}
							}
							new Link().rec(inst.getUse(i));
						}
					}
				}
			}));

			System.err.println("step b");
			
			boolean more = true;
			while (more) {
				more = false;
				System.err.println("step b.1");
				Set<Pair<Integer, CGNode>> kill = HashSetFactory.make();
				for (Pair<Integer, CGNode> key : allTurtles.keySet()) {
					SSAInstruction inst = key.snd.getDU().getDef(key.fst);
					if (inst instanceof AstPropertyRead) {
						assert inst.getDef() != -1;
						Iterator<SSAInstruction> uses = key.snd.getDU().getUses(inst.getDef());
						if (uses.hasNext()) {
							SSAInstruction use = uses.next();
							Pair<Integer, CGNode> useKey = Pair.make(use.getDef(), key.snd);
							if (use instanceof SSAAbstractInvokeInstruction && 
									!uses.hasNext() &&
									use.getUse(0) == inst.getDef() &&
									!kill.contains(key) &&
									allTurtles.containsKey(useKey))
							{
								kill.add(key);
								allTurtles.get(key).forEach(read_t -> { 
									Set<TurtlePath> x = HashSetFactory.make();
									G.getPredNodes(read_t, new EdgeType(EdgeClass.DATA, 0)).forEachRemaining(p -> { 
										x.add(p);
									});

									x.forEach(y -> G.removeEdge(y, read_t));

									allTurtles.get(useKey).forEach(use_t -> { 
										G.removeEdge(read_t, use_t);
										x.forEach(y -> G.addEdge(y, use_t, new EdgeType(EdgeClass.DATA, 0)));								
									});
								});

								more = true;								
							}
						}
					}
				}
				kill.forEach(k -> { 
					allTurtles.get(k).forEach(t -> G.removeNodeAndEdges(t));
					allTurtles.remove(k); 
				});
			}
			
			System.err.println("step c");
			
			ExplodedInterproceduralCFG ipcfg = ExplodedInterproceduralCFG.make(CG);
	
			Function<TurtlePath, BasicBlockInContext<IExplodedBasicBlock>> toBlock = (srcPath) -> {
				LocalPointerKey rk = (LocalPointerKey) srcPath.value();
				if (rk != null) {
					int idx = rk.getNode().getDU().getDef(rk.getValueNumber()).iIndex();
					if (idx != -1) {
						IExplodedBasicBlock rbb = ipcfg.getCFG(rk.getNode()).getBlockForInstruction(idx);
						BasicBlockInContext<IExplodedBasicBlock> src = new BasicBlockInContext<>(rk.getNode(), rbb);
						assert ipcfg.containsNode(src) : src;
						return src;
					}
				}
				
				return null;
			};

			Map<BasicBlockInContext<IExplodedBasicBlock>, TurtlePath> ipcfgNodes = HashMapFactory.make();
			allTurtles.values().forEach(ts -> ts.forEach(srcPath ->
				{
					BasicBlockInContext<IExplodedBasicBlock> block = toBlock.apply(srcPath);
					if (block != null) {
						ipcfgNodes.put(block, srcPath);
					}
				}));
			Graph<BasicBlockInContext<IExplodedBasicBlock>> ipcfgSlice = GraphSlicer.project(ipcfg, (x) -> { return ipcfgNodes.containsKey(x); });

			class GraphMapper<F, T> extends AbstractGraph<F> {
				private final Graph<T> graph;
				private final Function<F, T> map;
				private final Function<T, F> reverseMap;

				public GraphMapper(Graph<T> graph, Function<F, T> map, Function<T, F> reverseMap) {
					this.graph = graph;
					this.map = map;
					this.reverseMap = reverseMap;
				}

				@Override
				protected NodeManager<F> getNodeManager() {
					return new NodeManager<F>() {

						@Override
						public Stream<F> stream() {
							Iterable<F> iterable = () -> iterator();
							return StreamSupport.stream(iterable.spliterator(), false);
						}

						@Override
						public Iterator<F> iterator() {
							return new MapIterator<>(graph.iterator(), reverseMap::apply);
						}

						@Override
						public int getNumberOfNodes() {
							return graph.getNumberOfNodes();
						}

						@Override
						public void addNode(F n) {
							graph.addNode(map.apply(n));
						}

						@Override
						public void removeNode(F n) throws UnsupportedOperationException {
							graph.removeNode(map.apply(n));
						}

						@Override
						public boolean containsNode(F n) {
							return graph.containsNode(map.apply(n));
						}

					};
				}

				@Override
				protected EdgeManager<F> getEdgeManager() {
					return new EdgeManager<F>() {

						@Override
						public Iterator<F> getPredNodes(F n) {
							return new MapIterator<>(graph.getPredNodes(map.apply(n)), reverseMap::apply);
						}

						@Override
						public int getPredNodeCount(F n) {
							return graph.getPredNodeCount(map.apply(n));
						}

						@Override
						public Iterator<F> getSuccNodes(F n) {
							return new MapIterator<>(graph.getSuccNodes(map.apply(n)), reverseMap::apply);
						}

						@Override
						public int getSuccNodeCount(F n) {
							return graph.getSuccNodeCount(map.apply(n));
						}

						@Override
						public void addEdge(F src, F dst) {
							graph.hasEdge(map.apply(src), map.apply(dst));
						}

						@Override
						public void removeEdge(F src, F dst) throws UnsupportedOperationException {
							graph.removeEdge(map.apply(src), map.apply(dst));
						}

						@Override
						public void removeAllIncidentEdges(F node) throws UnsupportedOperationException {
							graph.removeAllIncidentEdges(map.apply(node));
						}

						@Override
						public void removeIncomingEdges(F node) throws UnsupportedOperationException {
							graph.removeIncomingEdges(map.apply(node));
						}

						@Override
						public void removeOutgoingEdges(F node) throws UnsupportedOperationException {
							graph.removeOutgoingEdges(map.apply(node));
						}

						@Override
						public boolean hasEdge(F src, F dst) {
							return graph.hasEdge(map.apply(src), map.apply(dst));
						}

					};
				}
			};

			Graph<TurtlePath> turtleCfg = new GraphMapper<>(ipcfgSlice, toBlock, ipcfgNodes::get);

			turtleCfg.forEach((src) -> {
				turtleCfg.getSuccNodes(src).forEachRemaining((dst) -> {
					G.addEdge(src, dst, new EdgeType(EdgeClass.CONTROL, -1));
				});
			});

			System.err.println("step done");	

			return G;
			
	} catch (TimeoutException e) {
		e.printStackTrace(System.err);
		return null;

	}
}

}
