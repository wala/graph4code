package util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.labeled.SlowSparseNumberedLabeledGraph;
import com.ibm.wala.util.graph.traverse.DFS;
import com.ibm.wala.util.graph.traverse.FloydWarshall;
import com.ibm.wala.util.graph.traverse.FloydWarshall.GetPaths;

public class SummarizeDataScienceGraphs {
	static String graph4codeNamespace = "http://purl.org/twc/graph4code/";
	static JSONObject docstringObj;
	static JSONObject analysisObj;
	static Map<String, String> classMap = new HashMap<String, String>();
	static boolean turnOffTypeReduction = false;
	

    public static JSONObject parseJSONFile(String filename) throws JSONException, IOException {
        String content = new String(Files.readAllBytes(Paths.get(filename)));
        return new JSONObject(content);
    }
    
	private static void createInferenceMaps(String baseDir) throws JSONException, IOException {
		docstringObj = parseJSONFile(baseDir + File.separator + "docstr_func2inf_types.json");
		analysisObj = parseJSONFile(baseDir + File.separator + "stat_analy_func2types.json");
		Stream<String> stream = Files.lines(Paths.get(baseDir + File.separator + "classes.map"));
		stream.forEach(x -> {
			String[] arr = x.split(" "); 
			if (arr.length == 2) {
				classMap.put(arr[0], arr[1]);
			} else {
				classMap.put(arr[0], arr[0]);
			}
		});
		stream.close();
	}
	
	private static String getNodeLabel(Map<String, String> normalizedPaths, String nodeLabel) {
		if (turnOffTypeReduction) {
			return nodeLabel;
		}
		String[] arr = nodeLabel.split("[.]");
		if (arr.length < 2) {
			return nodeLabel;
		}
		String key = arr[0] + '.' +  arr[1];
		
		normalizeNode(normalizedPaths, key);

		for (int i = 2; i < arr.length; i++) {
			String val = normalizedPaths.get(key);
			if (val != null) {
				key =  val + "." + arr[i];
			} else {
				key =  key + "." + arr[i];
			}
			normalizeNode(normalizedPaths, key);
		}
		return key;
	}

	private static void normalizeNode(Map<String, String> normalizedPaths, String key) {

		if (normalizedPaths.containsKey(key)) {
			return;
		}
		
		Set<String> docStringTypes = getTypes(key, docstringObj);
		Set<String> analysisTypes = getTypes(key, analysisObj);
		Set<String> intersect;
		
		if (!docStringTypes.isEmpty() && !analysisTypes.isEmpty()) {
			intersect = new HashSet<String>(docStringTypes);
			intersect.retainAll(analysisTypes);
		} else if (analysisTypes.isEmpty()) {
			intersect = docStringTypes;
		} else {
			intersect = analysisTypes;
		}
		
		if (!intersect.isEmpty()) {
			String val = intersect.iterator().next();
			normalizedPaths.put(key, val);
		}
	}

	private static Set<String> getTypes(String key, JSONObject typesMap) {
		Set<String> retTypes = new HashSet<String>();
		if (!typesMap.has(key)) {
			return retTypes;
		}
		Object obj = typesMap.get(key);
		if (obj instanceof JSONArray) {
			JSONArray docstringTypes = (JSONArray) typesMap.get(key);
			for (Object a : docstringTypes) {
				retTypes.add((String) a);
			}
		} else if (obj instanceof JSONObject) {
			JSONObject statTypes = (JSONObject) typesMap.get(key);
			Iterator<String> it = statTypes.keys();
			int max = 0;
			while (it.hasNext()) {
				int k = Integer.parseInt(it.next());
				if (k > max) {
					max = k;
				}
			}
			
			JSONArray arr = (JSONArray) statTypes.get(Integer.toString(max));
			for (Object a : arr) {
				String str = (String) a;
				if (classMap.containsKey(str)) {
					retTypes.add(classMap.get(str));
				} 
			}
		} else {
			assert false: "Unexpected object:" + obj;
		}
		String str = classMap.containsKey(key) ? classMap.get(key) : key;
		if (retTypes.size() > 1 && retTypes.contains(str)) {
			retTypes = new HashSet<String>();
			retTypes.add(str);
		}
		return retTypes;
	}
	
	private static boolean filterRead(String y, Map<String, Map<String, ReadWriteAnnotation>> readwrites, Map<String, String> node2Label) {
		if (readwrites.containsKey(y) && readwrites.get(y).size() == 1) {
			ReadWriteAnnotation ra = readwrites.get(y).values().iterator().next();
			if (ra.toMethodString(node2Label).equals(handleNodeLabel(node2Label.get(y)))) {
				return true;
			}
		} 
		return false;
	}
	
	public static void main(String[] args) throws JSONException, IOException {
		Dataset ds = RDFDataMgr.loadDataset(args[0]);
		
		String baseDirTypeInferenceData = args[1];
		createInferenceMaps(baseDirTypeInferenceData);

		Iterator<String> graphs = ds.listNames();

		int allPaths = 0;
		int fw_paths = 0;
		Set<String> allUniquePaths = new HashSet<String>();
		
		JSONObject all = new JSONObject();
		while (graphs.hasNext()) {

			String graph = graphs.next();
			Map<String, String> node2Label = new HashMap<String, String>();
			Model m = ds.getNamedModel(graph);
			String dataFlow = graph4codeNamespace + "flowsTo";

			String label = RDFS.label.getURI();

			SlowSparseNumberedLabeledGraph<String, String> g = new SlowSparseNumberedLabeledGraph<String, String>(
					"dataflow");

			StmtIterator stmts = m.listStatements();
			while (stmts.hasNext()) {
				Statement statement = stmts.next();
				Triple t = statement.asTriple();
				String subject = t.getSubject().toString();
				String object = t.getObject().toString();

				Node pred = t.getPredicate();
				if (pred.getURI().equals(label)) {
					node2Label.put(subject, object);
				}
				if (pred.getURI().equals(dataFlow)) {
					if (!g.containsNode(subject)) {
						g.addNode(subject);
					}
					if (!g.containsNode(object)) {
						g.addNode(object);
					}
					g.addEdge(subject, object, "dataflow");
				}
			}
			List<String> nodes = new LinkedList<String>();
			for (String node : g) {
				if (!node2Label.containsKey(node)) {
					continue;
				}
				String key = node2Label.get(node);
				// System.out.println(key);
				if (key.equals("\"pandas.read_csv\"") || key.equals("\"csv.reader\"")) {
					nodes.add(node);
				}
			}

			Set<String> endNodes = DFS.getReachableNodes(g, nodes);

			Set<String> filteredNodes = new HashSet<String>();
			for (String s : endNodes) {
				if (g.getSuccNodeCount(s) == 0) {
					filteredNodes.add(s);
				}
			}
			System.out.println("graph:" + graph);
			System.out.println("Paths");
			GetPaths<String> paths = FloydWarshall.allPairsShortestPaths(g);
			
			List<Pair<String, String>> edgesForAnnotations = new LinkedList<Pair<String, String>>();
			Map<String, String> normalizedPaths = new HashMap<String, String>();
			
			// test the node label thing
			Map<String, String> node2NormalizedLabels = new HashMap<String, String>();

			for (String start : nodes) {
				for (String end : filteredNodes) {
					try {
						Set<List<String>> p = paths.getPaths(start, end);
						assert p != null && !p.isEmpty();
						
						for (List<String> x : p) {
							
							String prev = start;
							String str = getNodeLabel(normalizedPaths, handleNodeLabel(node2Label.get(prev)));
							assert str != null;
							
							node2NormalizedLabels.put(prev, str);
							for (String y : x) {
								str = getNodeLabel(normalizedPaths, handleNodeLabel(node2Label.get(y)));
								node2NormalizedLabels.put(y, str);
								assert str != null;
								edgesForAnnotations.add(Pair.make(prev, y));
								prev = y;
							}
							str = getNodeLabel(normalizedPaths, handleNodeLabel(node2Label.get(end)));
							assert str != null;
							node2NormalizedLabels.put(end, str);
							edgesForAnnotations.add(Pair.make(prev, end));
						}
					} catch (UnsupportedOperationException e) {

					}
				}
			}
			Map<Pair<String, String>, Map<String, EdgeAnnotation>> annotations = gatherEdgeAnnotations(m, edgesForAnnotations);
			Map<String, Map<String, ConstantAnnotation>> constants = gatherConstantsFlow(m, edgesForAnnotations);
			Map<String, Map<String, ReadWriteAnnotation>> readwrites = gatherReadsAndWrites(m, edgesForAnnotations, node2Label);

			JSONArray res = new JSONArray();

			for (String start : nodes) {
				for (String end : filteredNodes) {
					try {
						Set<List<String>> p = paths.getPaths(start, end);
						for (List<String> x : p) {
							String prev = start;
							if (filterRead(start, readwrites, node2Label)) {
								continue;
							} 
							
							JSONArray path = new JSONArray();

							StringBuffer pathStr = new StringBuffer();
							pathStr.append(printNode(node2Label, normalizedPaths, node2NormalizedLabels, constants, readwrites, start));
							
							for (String y : x) {
								// if this y is a function read get rid of it from the path
								// the way we tell this is check if the read annotation and node label are the same
								if (filterRead(y, readwrites, node2Label)) {
									continue;
								} 
								Pair<String, String> key = Pair.make(prev, y);
								
								printEdge(node2Label, normalizedPaths, node2NormalizedLabels, annotations, constants,
										readwrites, pathStr, key);
								path.put(printEdgeToJSON(node2Label, normalizedPaths, node2NormalizedLabels, annotations, constants, readwrites, pathStr, key));
								prev = y;

							}
							Pair<String, String> key = Pair.make(prev, end);
							path.put(printEdgeToJSON(node2Label, normalizedPaths, node2NormalizedLabels, annotations, constants, readwrites, pathStr, key));

							printEdge(node2Label, normalizedPaths, node2NormalizedLabels, annotations, constants,
									readwrites, pathStr, key);
							
							if (!allUniquePaths.contains(pathStr.toString().trim())) {
								res.put(path);
								System.out.println(pathStr.toString());
							}
							allUniquePaths.add(pathStr.toString().trim());
							
						}
					} catch (UnsupportedOperationException e) {

					}
					

				}
			}
			all.put(graph, res);
		}
        FileWriter file = new FileWriter(args[2]);
        file.write(all.toString(4));
        file.close();

		System.out.println("All paths:" + allPaths);
		System.out.println("FW paths:" + fw_paths);
		System.out.println("Unique paths:" + allUniquePaths.size());

		
	}

	private static void printEdge(Map<String, String> node2Label, Map<String, String> normalizedPaths,
			Map<String, String> node2NormalizedLabels,
			Map<Pair<String, String>, Map<String, EdgeAnnotation>> annotations,
			Map<String, Map<String, ConstantAnnotation>> constants,
			Map<String, Map<String, ReadWriteAnnotation>> readwrites, StringBuffer pathStr,
			Pair<String, String> key) {
		if (annotations.containsKey(key)) {
			// order annotations before adding it to pathstr - note here we pass in all annotations of a particular edge in the path
			// instead of adding new ones for each
			Set<String> edgeAnnSort = new TreeSet<String>();
			for (EdgeAnnotation ann : annotations.get(key).values()) {
				edgeAnnSort.add("-" + ann + "-> ");

			}
			for (String s : edgeAnnSort) {
				pathStr.append(s.trim());
			}
			
			constants.get(key.snd);
			pathStr.append(printNode(node2Label, normalizedPaths, node2NormalizedLabels, constants, readwrites, key.snd));
		}
	}

	private static String printNode(Map<String, String> node2Label, Map<String, String> normalizedPaths,
			Map<String, String> node2NormalizedLabels, Map<String, Map<String, ConstantAnnotation>> constants,
			Map<String, Map<String, ReadWriteAnnotation>> readwrites, String start) {
		StringBuffer pathStr = new StringBuffer();
		pathStr.append(node2NormalizedLabels.get(start));
		pathStr.append(dumpAnnotations(constants, start, (z)-> z.toString()));
		pathStr.append(dumpAnnotations(readwrites, start, (z)-> z.toLabelString(node2Label, normalizedPaths)));
		pathStr.append("\n");
		return pathStr.toString();
	}
	
	private static JSONObject printNodeToJSON(Map<String, String> node2Label, Map<String, String> normalizedPaths,
			Map<String, String> node2NormalizedLabels, Map<String, Map<String, ConstantAnnotation>> constants,
			Map<String, Map<String, ReadWriteAnnotation>> readwrites, String start) {
		JSONObject node = new JSONObject();
		node.put("label", node2NormalizedLabels.get(start));
		node.put("constant arguments", dumpAnnotationsToJSON(constants, start, node2Label));
		node.put("reads", dumpAnnotationsToJSON(readwrites, start, node2Label));
		return node;
	}
	
	private static JSONObject printEdgeToJSON(Map<String, String> node2Label, Map<String, String> normalizedPaths,
			Map<String, String> node2NormalizedLabels,
			Map<Pair<String, String>, Map<String, EdgeAnnotation>> annotations,
			Map<String, Map<String, ConstantAnnotation>> constants,
			Map<String, Map<String, ReadWriteAnnotation>> readwrites, StringBuffer pathStr,
			Pair<String, String> key) {
		JSONObject edge = new JSONObject();
		edge.put("src", printNodeToJSON(node2Label, normalizedPaths, node2NormalizedLabels, constants, readwrites, key.fst));
		JSONArray anns = new JSONArray();
		if (annotations.containsKey(key)) {
			// order annotations before adding it to pathstr - note here we pass in all annotations of a particular edge in the path
			// instead of adding new ones for each
			for (EdgeAnnotation ann : annotations.get(key).values()) {
				anns.put(ann.toJSON(node2Label));
			}
		}
		edge.put("edge annotations", anns);
		edge.put("target", printNodeToJSON(node2Label, normalizedPaths, node2NormalizedLabels, constants, readwrites, key.snd));
		return edge;
	}
	
	private static <X extends Annotation> JSONArray dumpAnnotationsToJSON(Map<String, Map<String, X>> annotationMap, String start, Map<String, String> node2Label) {
		if (!annotationMap.containsKey(start)) {
			return null;
		}
		JSONArray arr = new JSONArray();
		for (X ca : annotationMap.get(start).values()) {
			arr.put(ca.toJSON(node2Label));
		}
		return arr;
	}

	
	private static <X> String dumpAnnotations(Map<String, Map<String, X>> annotationMap, String start, Function<X, String> func) {
		if (!annotationMap.containsKey(start)) {
			return "";
		}
		StringBuffer buf = new StringBuffer();
		X x = annotationMap.get(start).values().iterator().next();
		char sep = x instanceof ReadWriteAnnotation ? '<' : '[';
		buf.append(" " + sep);
		SortedSet<String> uniqAnns = new TreeSet<String>();
		for (X ca : annotationMap.get(start).values()) {
			uniqAnns.add(func.apply(ca));
		}
		for (String a : uniqAnns) {
			buf.append(a).append(",");
		}
		sep = x instanceof ReadWriteAnnotation ? '>' : ']';

		buf.append(sep + " ");
		return buf.toString();
	}

	private static Map<String, Map<String, ReadWriteAnnotation>> gatherReadsAndWrites(Model m,
			List<Pair<String, String>> allnodesInPath, Map<String, String> nodeToLabel) {
		Map<String, Map<String, ReadWriteAnnotation>> ret = new HashMap<String, Map<String, ReadWriteAnnotation>>();
		List<String> nodes = gatherNodesInPath(allnodesInPath);
		String read = "<http://purl.org/twc/graph4code/read>";
		String write = "<http://purl.org/twc/graph4code/write>";
		String flowsTo = "<http://purl.org/twc/graph4code/flowsTo>";
		String hasExpression = "http://semanticscience.org/resource/SIO_000420";
		String hasValue = "http://semanticscience.org/resource/SIO_000300";
		String isPartOf = "http://semanticscience.org/resource/SIO_000068";
		String readNode = "<http://semanticscience.org/resource/SIO_000649>"; 


		String queryString = "select ?src ?res ?p  ?value where { {?src " + read + " ?res .} " + " UNION " + "{?src "
				+ write + " ?res .} " + "?res  ?p ?value .} ";
		StringBuffer values = new StringBuffer();
		for (String node : nodes) {
			values.append("(<").append(node).append("> )\n");
		}
		String valuesClause = "VALUES (?src)\n" + "{ \n" + values + "\n}";

		queryString = queryString + valuesClause;

		Query query = QueryFactory.create(queryString);
		try (QueryExecution qexec = QueryExecutionFactory.create(query, m)) {
			ResultSet results = qexec.execSelect();
			for (; results.hasNext();) {
				QuerySolution soln = results.nextSolution();
				String src = soln.get("src").toString();
				String x = soln.get("p").toString(); // Get a result variable by name.
				RDFNode y = soln.get("value");
				String res = soln.get("res").toString();

				if (!ret.containsKey(src)) {
					Map<String, ReadWriteAnnotation> k = new HashMap<String, ReadWriteAnnotation>();
					ret.put(src, k);
				}
				
				Map<String, ReadWriteAnnotation> l = ret.get(src);
				if (!l.containsKey(res)) {
					ReadWriteAnnotation e = new ReadWriteAnnotation();
					l.put(res, e);
				}
				ReadWriteAnnotation e = l.get(res);
				if (x.equals(isPartOf)) {
					e.container = y.toString();
				} else if (x.equals(hasExpression)) {
					e.expression = y.toString();
					String flowsToQuery = "select ?x where {?x " + flowsTo + " <" + y.asResource().getURI() + "> . ?z " + flowsTo + " ?x . ?z <" + RDF.type.getURI() + "> " + readNode + " . }"; 
					Query fquery = QueryFactory.create(flowsToQuery);
					try (QueryExecution fqexec = QueryExecutionFactory.create(fquery, m)) {
						ResultSet fresults = fqexec.execSelect();
						for (; fresults.hasNext();) {
							QuerySolution fsoln = fresults.nextSolution();
							String fx = fsoln.get("x").toString();
							e.reads.add(nodeToLabel.get(fx));
						}
					}
				} else if (x.equals(hasValue)) {
					Literal li = y.asLiteral();
					e.field = li.getLexicalForm();
				}
			}
		}

		return ret;
	}

	private static Map<String, Map<String, ConstantAnnotation>> gatherConstantsFlow(Model m,
			List<Pair<String, String>> allnodesInPath) {
		Map<String, Map<String, ConstantAnnotation>> ret = new HashMap<String, Map<String, ConstantAnnotation>>();
		List<String> nodes = gatherNodesInPath(allnodesInPath);
		String hasValue = "http://semanticscience.org/resource/SIO_000300";
		String nameProp = "http://semanticscience.org/resource/SIO_000116";
		String ordinalPosition = "http://semanticscience.org/resource/SIO_000613";

		String hasInput = "<http://semanticscience.org/resource/SIO_000230>";

		String queryString = "select ?src ?param ?p  ?value where {?param ?p ?value . " + "?src " + hasInput
				+ " ?param ." + " FILTER NOT EXISTS {?param <http://www.w3.org/ns/prov#isSpecializationOf> ?z}}";

		StringBuffer values = new StringBuffer();
		for (String node : nodes) {
			values.append("(<").append(node).append("> )\n");
		}
		String valuesClause = "VALUES (?src)\n" + "{ \n" + values + "\n}";

		queryString = queryString + valuesClause;

		Query query = QueryFactory.create(queryString);
		try (QueryExecution qexec = QueryExecutionFactory.create(query, m)) {
			ResultSet results = qexec.execSelect();
			for (; results.hasNext();) {
				QuerySolution soln = results.nextSolution();
				String src = soln.get("src").toString();
				String x = soln.get("p").toString(); // Get a result variable by name.
				RDFNode y = soln.get("value");
				String param = soln.get("param").toString();

				if (!ret.containsKey(src)) {
					Map<String, ConstantAnnotation> k = new HashMap<String, ConstantAnnotation>();
					ret.put(src, k);
				}
				Map<String, ConstantAnnotation> l = ret.get(src);
				if (!l.containsKey(param)) {
					ConstantAnnotation e = new ConstantAnnotation();
					l.put(param, e);
				}
				ConstantAnnotation e = l.get(param);
				if (x.equals(nameProp)) {
					e.name = y.toString();
				} else if (x.equals(ordinalPosition)) {
					e.ordinalPosition = ((Literal) y).getInt();
				} else if (x.equals(hasValue)) {
					Literal li = y.asLiteral();
					e.value = li.getLexicalForm();
				}
			}
		}

		return ret;

	}

	private static List<String> gatherNodesInPath(List<Pair<String, String>> allnodesInPath) {
		LinkedList<String> nodes = new LinkedList<String>();
		for (Pair<String, String> p : allnodesInPath) {
			nodes.add(p.fst);
			nodes.add(p.snd);
		}
		return nodes;
	}

	private static Map<Pair<String, String>, Map<String, EdgeAnnotation>> gatherEdgeAnnotations(Model m,
			List<Pair<String, String>> startEndPairs) {

		Map<Pair<String, String>, Map<String, EdgeAnnotation>> edgeToAnnotations = new HashMap<Pair<String, String>, Map<String, EdgeAnnotation>>();

		String hasInput = "<http://semanticscience.org/resource/SIO_000230>";
		String nameProp = "http://semanticscience.org/resource/SIO_000116";
		String ordinalPosition = "http://semanticscience.org/resource/SIO_000613";
		String isSpecializationOf = "<http://www.w3.org/ns/prov#isSpecializationOf>";

		String queryString = "select ?src ?target ?param ?p  ?q where {?src " + hasInput + " ?param . " + "?param "
				+ isSpecializationOf + " ?target ." + "?param  ?p  ?q ." + "} ";

		StringBuffer values = new StringBuffer();
		for (Pair<String, String> p : startEndPairs) {
			values.append("(<").append(p.fst).append("> <").append(p.snd).append(">) \n");
		}
		String valuesClause = "VALUES (?src ?target)\n" + "{ \n" + values + "\n}";

		queryString = queryString + valuesClause;

		Query query = QueryFactory.create(queryString);
		try (QueryExecution qexec = QueryExecutionFactory.create(query, m)) {
			ResultSet results = qexec.execSelect();
			for (; results.hasNext();) {
				QuerySolution soln = results.nextSolution();
				String src = soln.get("src").toString();
				String tgt = soln.get("target").toString();
				String param = soln.get("param").toString();
				String x = soln.get("p").toString(); // Get a result variable by name.
				RDFNode y = soln.get("q");

				Pair<String, String> key = Pair.make(src, tgt);
				
				if (!edgeToAnnotations.containsKey(key)) {
					Map<String, EdgeAnnotation> k = new HashMap<String, EdgeAnnotation>();
					edgeToAnnotations.put(key, k);
				}
				
				Map<String, EdgeAnnotation> l = edgeToAnnotations.get(key);
				
				if (!l.containsKey(param)) {
					l.put(param, new EdgeAnnotation());
				}
				EdgeAnnotation e = l.get(param);
				if (x.equals(nameProp)) {
					e.name = y.toString();
				} else if (x.equals(ordinalPosition)) {
					e.ordinalPosition = ((Literal) y).getInt();
				}
			}
		}
		return edgeToAnnotations;
	}
	
	static String handleNodeLabel(String name) {
		return name.replace("\"","");
	}
	
	interface Annotation {
		public JSONObject toJSON(Map<String, String> node2Label);
	}

	static final class ConstantAnnotation implements Annotation {
		String name;
		int ordinalPosition;
		Object value;

		public String toString() {
			String suffix = name != null ? name : Integer.toString(ordinalPosition);
			return suffix + ":" + value;
		}
		
		
		public JSONObject toJSON(Map<String, String> node2Label) {
			JSONObject obj = new JSONObject();
			if (name != null) {
				obj.put("name", name);
			} 
			if (ordinalPosition != -1) {
				obj.put("ordinalPosition", ordinalPosition);
			}
			obj.put("value", value);
			return obj;
		}
	}

	static final class EdgeAnnotation implements Annotation {
		String name;
		int ordinalPosition = -1;

		public String toString() {
			return name != null ? name : Integer.toString(ordinalPosition);
		}
		
		public JSONObject toJSON(Map<String, String> node2Label) {
			JSONObject obj = new JSONObject();
			if (name != null) {
				obj.put("name", name);
			} 
			if (ordinalPosition != -1) {
				obj.put("ordinalPosition", ordinalPosition);
			}
			return obj;
		}
		
	}
	
	static final class ReadWriteAnnotation implements Annotation {
		String container;
		String field;
		String expression;
		Set<String> reads = new HashSet<String>();
		
		public String toMethodString(Map<String, String> node2Label) {
			String suffix = field != null ? field : getExpression(node2Label);
			return handleNodeLabel(node2Label.get(container)) + "." + suffix;
		}
		
		private String getExpression(Map<String, String> node2Label) {
			if (!reads.isEmpty()) {
				return node2Label.get(expression) + " reads:" + reads;
			} else {
				return node2Label.get(expression);
			}
		}
		
		private JSONObject getExpressionToJSON(Map<String, String> node2Label) {
			JSONObject obj = new JSONObject();
			obj.put("name", node2Label.get(expression));
			if (!reads.isEmpty()) {
				
				JSONArray arr = new JSONArray();
				for (String s : reads) {
					arr.put(s);
				}
				obj.put("depends_on", arr);
			}
			return obj;
		}
		
		public String toLabelString(Map<String, String> node2Label, Map<String, String> normalizedPaths) {
			String suffix = field != null ? field : getExpression(node2Label);
			return getNodeLabel(normalizedPaths, handleNodeLabel(node2Label.get(container))) + ":" + suffix;
		}
		
		public String toString() {
			return expression != null ? container + "." + expression : container + ":" + field + " reads:" + reads;
		}
		
		public JSONObject toJSON(Map<String, String> node2Label) {
			JSONObject obj = new JSONObject();
			obj.put("container", handleNodeLabel(node2Label.get(container)));
			obj.put("field", field);
			if (expression != null) {
				obj.put("expression", getExpressionToJSON(node2Label));
			}
			return obj;
		}
	}
}
