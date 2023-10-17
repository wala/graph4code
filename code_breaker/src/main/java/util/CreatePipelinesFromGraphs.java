package util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;
import com.ibm.wala.util.graph.labeled.SlowSparseNumberedLabeledGraph;

public class CreatePipelinesFromGraphs {

	protected static int numNodes = 0;
	protected static Map<Integer, JSONObject> nodeIdToObject = new HashMap<Integer, JSONObject>();
	protected static List<String[]> interestingClasses = new LinkedList<String[]>();

	public static JSONObject parseJSONFile(String filename) throws JSONException, IOException, CompressorException {
		String content;
		if (filename.endsWith(".bz2")) {
			FileInputStream fin = new FileInputStream(filename);
			BufferedInputStream bis = new BufferedInputStream(fin);
			CompressorInputStream input = new CompressorStreamFactory().createCompressorInputStream(bis);
			BufferedReader br2 = new BufferedReader(new InputStreamReader(input));
			content = br2.lines().collect(Collectors.joining(System.lineSeparator()));
		} else {
			content = new String(Files.readAllBytes(Paths.get(filename)));
		}
		return new JSONObject(content);
	}

	private static boolean isInterestingClass(Integer node) {
		JSONArray arr = ((JSONObject) nodeIdToObject.get(node)).getJSONArray("path");

		System.out.println(nodeIdToObject.get(node).getJSONArray("path"));
		if (arr.length() == 1) {
			return false;
		}

		boolean found = findClass(arr) != null ? true : false;
		if (found) {
			return true;
		} else {
			return false;
		}
	}

	private static String findClass(JSONArray arr) {
		String foundKlass = null;
		for (String[] klass : interestingClasses) {
			List<String> importantMatches = new LinkedList<String>();
			importantMatches.add(klass[0]);
			importantMatches.add(klass[klass.length - 1]);
			int overlap = 0;
			for (String k : importantMatches) {
				for (int j = 0; j < arr.length(); j++) {
					if (arr.getString(j).equals(k)) {
						overlap++;
					}
				}
			}
			if (overlap == 2) {
				StringBuffer joiner = new StringBuffer();
				for (int i = 0; i < klass.length; i++) {
					joiner.append(klass[i]);
					if (i < klass.length - 1) {
						joiner.append(".");
					}
				}

				foundKlass = joiner.toString();
			}
		}
		return foundKlass;
	}

	private static boolean filterEdge(SlowSparseNumberedLabeledGraph<Integer, String> g, Set<Integer> processedNodes) {
		List<Pair<Integer, Integer>> removedEdges = new LinkedList<Pair<Integer, Integer>>();
		int currentNode = -1;
		Set<Integer> substitutePreds = new HashSet<Integer>();
		Set<Integer> notSubstitutePreds = new HashSet<Integer>();
		for (Integer n : g) {
			if (processedNodes.contains(n)) {
				continue;
			}
			processedNodes.add(n);
			Iterator<Integer> preds = g.getPredNodes(n);
			
			currentNode = n;
			while (preds.hasNext()) {
				int pred = preds.next();
				System.out.println("pred:" + nodeIdToObject.get(pred).getJSONArray("path"));
				Pair<Integer, Integer> edge = Pair.make(n, pred);
				String klass1 = findClass(nodeIdToObject.get(n).getJSONArray("path"));
				String klass2 = findClass(nodeIdToObject.get(pred).getJSONArray("path"));
				if (klass1.equals(klass2)) {
					substitutePreds.add(pred);
					System.out.println(nodeIdToObject.get(n).getJSONArray("path") + " has same class as " + nodeIdToObject.get(pred).getJSONArray("path"));
					Iterator<Integer> succs = g.getSuccNodes(n);
					while (succs.hasNext()) {
						int succ = succs.next();
						if (pred == succ) {
							continue;
						}
						// System.out.println("Adding edge:" + nodeIdToObject.get(pred).get("path") + "
						// " + nodeIdToObject.get(succ).get("path"));
						if (!g.hasEdge(pred, succ)) {
							g.addEdge(pred, succ, "flowsTo");
							System.out.println("Adding edge:" + nodeIdToObject.get(pred).get("path") + "-> " + nodeIdToObject.get(succ).get("path"));
						}
					}
					removedEdges.add(edge);
				} else {
					notSubstitutePreds.add(pred);
				}
			}
			if (removedEdges.size() > 0) {
				for (Pair<Integer, Integer> removedEdge : removedEdges) {
					g.removeEdge(removedEdge.fst, removedEdge.snd);
				}
				break;
			}
		}
		
		Set<Integer> removedPreds = new HashSet<Integer>();
		for (Pair<Integer, Integer> edge : removedEdges) {
			removedPreds.add(edge.fst);
		}
		
		if (removedEdges.size() > 0) {
			assert currentNode != -1;
			if (removedPreds.size() == g.getPredNodeCount(currentNode) || g.getSuccNodeCount(currentNode) == 0) {
				g.removeNodeAndEdges(currentNode);
				System.out.println("removing node:" + currentNode);
			} else if (removedPreds.size() != g.getPredNodeCount(currentNode)) {
				for (Integer n : notSubstitutePreds) {
					for (Integer s : substitutePreds) {
						g.addEdge(n, s, "flowsTo");
					}
				}
				g.removeNodeAndEdges(currentNode);
				System.out.println("removing node:" + currentNode);
			}
		}

		return removedEdges.size() > 0;
	}

	private static boolean filterNode(SlowSparseNumberedLabeledGraph<Integer, String> g, Set<Integer> processedNodes,
			Function<Integer, Boolean> f) {
		boolean modified = false;
		Integer removedNode = -1;
		for (Integer n : g) {
			if (processedNodes.contains(n)) {
				continue;
			}

			if (!f.apply(n)) {
				Iterator<Integer> preds = g.getPredNodes(n);
				while (preds.hasNext()) {
					int pred = preds.next();
					Iterator<Integer> succs = g.getSuccNodes(n);
					while (succs.hasNext()) {
						int succ = succs.next();
						if (pred == succ) {
							continue;
						}
						assert g.containsNode(pred);
						assert g.containsNode(succ);
						
						// System.out.println("Adding edge:" + nodeIdToObject.get(pred).get("path") + "
						// " + nodeIdToObject.get(succ).get("path"));
						if (!g.hasEdge(pred, succ)) {
							System.out.println("adding edge:" + pred + "->" + succ);
							g.addEdge(pred, succ, "flowsTo");
						}
					}
				}
				modified = true;
				removedNode = n;
				break;
			}
			processedNodes.add(n);
		}
		if (modified) {
			assert removedNode > -1;
			g.removeNodeAndEdges(removedNode);
		}
		return modified;
	}

	private static void removeAllUninterestingNodes(SlowSparseNumberedLabeledGraph<Integer, String> g, Function<Integer, Boolean> f) {
		boolean iterate = true;
		Set<Integer> processedNodes = new HashSet<Integer>();
		while (iterate) {
			iterate = filterNode(g, processedNodes, f);
		}
	}
	
	private static void removeAllUninterestingEdges(SlowSparseNumberedLabeledGraph<Integer, String> g) {
		boolean iterate = true;
		Set<Integer> processedNodes = new HashSet<Integer>();
		while (iterate) {
			iterate = filterEdge(g, processedNodes);
		}
	}

	private static void printNodes(SlowSparseNumberedLabeledGraph<Integer, String> g, Integer n, Set<Integer> reachableNodes,
			Set<Integer> visitedNodes) {
		if (g.getSuccNodeCount(n) == 0) {
			return;
		}
		Iterator<Integer> succs = g.getSuccNodes(n);
		System.out.println("Node:" + n + " " + nodeIdToObject.get(n).get("path"));
		while (succs.hasNext()) {
			Integer s = succs.next();
			if (!reachableNodes.contains(s)) {
				continue;
			}
			if (nodeIdToObject.get(s).get("path").toString().equals(nodeIdToObject.get(n).get("path").toString())) {
				continue;
			}
			System.out.println("\t-->" + s + " " + nodeIdToObject.get(s).get("path"));
		}
		visitedNodes.add(n);
		succs = g.getSuccNodes(n);
		while (succs.hasNext()) {
			Integer s = succs.next();
			if (!reachableNodes.contains(s)) {
				continue;
			}
			if (!visitedNodes.contains(s)) {
				printNodes(g, s, reachableNodes, visitedNodes);
			}
		}
	}

	public static void main(String[] args) throws JSONException, IOException, CompressorException {
		JSONObject graphAsJSON = parseJSONFile(args[0]);
		JSONArray nodes = graphAsJSON.getJSONArray("turtle_analysis");

		JSONObject classes = parseJSONFile(args[1]);
		Iterator<String> keys = classes.keys();
		while (keys.hasNext()) {
			String k = keys.next();
			String[] arr = k.split("[.]");
			interestingClasses.add(arr);
		}

		SlowSparseNumberedLabeledGraph<Integer, String> g = new SlowSparseNumberedLabeledGraph<Integer, String>();

		Set<Integer> startNodes = new HashSet<Integer>();

		Map<Pair<Integer, String>, Integer> containerFieldMap = HashMapFactory.make();

		for (Object a : nodes) {
			if (a.equals(null)) {
				continue;
			}
			JSONObject node = (JSONObject) a;

			nodeIdToObject.put(node.getInt("nodeNumber"), node);
			String normalizedKey = node.getString("normalizedLabel");
			if (!g.containsNode(node.getInt("nodeNumber"))) {
				int nodeNum = node.getInt("nodeNumber");
				g.addNode(nodeNum);
				numNodes = Math.max(numNodes, nodeNum);
			}
		}
		
		HashMap<String, Set<String>> nodes2scoring = HashMapFactory.make();

		for (Object a : nodes) {
			if (a.equals(null)) {
				continue;
			}

			JSONObject node = (JSONObject) a;
			if (((JSONObject) node.get("edges")).has("flowsTo")) {
				JSONObject dataflow = ((JSONObject) node.get("edges")).getJSONObject("flowsTo");

				dataflow.keys().forEachRemaining(v -> {
					JSONArray dfs = dataflow.getJSONArray(v);
					for (Object vi : dfs) {
						Integer vii = (Integer) vi;
						g.addEdge(node.getInt("nodeNumber"), vii, v);
					}
				});
			}
			

		}
		
		for (Object a: nodes) {
			if (a.equals(null)) {
				continue;
			}
			JSONObject node = (JSONObject) a;

			JSONObject constants = (JSONObject) node.get("constant_named_args");
			if (constants.has("scoring")) {
				String measure = constants.getString("scoring");
				Iterator<Integer> it = g.getPredNodes(node.getInt("nodeNumber"), "1");
				
				JSONObject pred = nodeIdToObject.get(it.next());
				if (!(nodes2scoring.containsKey(pred.getString("path_end")))) {
					nodes2scoring.put(pred.getString("path_end"), new HashSet<String>());
				}
				nodes2scoring.get(pred.getString("path_end")).add(measure);
			}
		}
		
		
		Set<Integer> imports = new HashSet<Integer>();
		
		for (Integer n : g) {
			if (nodeIdToObject.get(n).getBoolean("is_import")) {
				imports.add(n);
			}
		}
		
		for (Integer n : imports) {
			g.removeNodeAndEdges(n);
		}
		
		removeAllUninterestingNodes(g, CreatePipelinesFromGraphs::isInterestingClass);
		
		System.out.println("BEFORE REMOVAL");
		Set<Integer> rootNodes = new HashSet<Integer>();
		for (Integer n : g) {
			if (g.getPredNodeCount(n) == 0 && g.getSuccNodeCount(n) > 0) {
				rootNodes.add(n);
			}
		}


		for (Integer r : rootNodes) {
			System.out.println("--------Root subgraph:" + nodeIdToObject.get(r).getJSONArray("path") + "-------------------");
			Set<Integer> visitedNodes = new HashSet<Integer>();
			Set<Integer> reachableNodes = com.ibm.wala.util.graph.traverse.DFS.getReachableNodes(g,
					Collections.singleton(r));
			printNodes(g, r, reachableNodes, visitedNodes);
		}


		// removeAllUninterestingEdges(g);
		
		System.out.println("AFTER REMOVAL");

		rootNodes = new HashSet<Integer>();
		for (Integer n : g) {
			if (g.getPredNodeCount(n) == 0) {
				rootNodes.add(n);
			}
		}

		for (Integer r : rootNodes) {
			System.out.println("Root subgraph:" + nodeIdToObject.get(r).getJSONArray("path"));
			Set<Integer> visitedNodes = new HashSet<Integer>();
			Set<Integer> reachableNodes = com.ibm.wala.util.graph.traverse.DFS.getReachableNodes(g,
					Collections.singleton(r));
			printNodes(g, r, reachableNodes, visitedNodes);
		}
		JSONObject result = new JSONObject();
		JSONArray filtered_nodes = new JSONArray();
		result.put("nodes", filtered_nodes);

		for (Integer n : g) {
			JSONObject o = createNewNode(n);
			filtered_nodes.put(o);
			Iterator<Integer> succs = g.getSuccNodes(n);
			while (succs.hasNext()) {
				int succ = succs.next();
				o.getJSONArray("flowsTo").put(succ);
			}
		}
		
		JSONArray class2scores = new JSONArray();
		result.put("class2scores", class2scores);
		
		for (String key : nodes2scoring.keySet()) {
			JSONObject o = new JSONObject();
			class2scores.put(o);
			o.put("class", key);
			JSONArray arr = new JSONArray();
			o.put("scoring_measure", arr);
			for (String v : nodes2scoring.get(key)) {
				arr.put(v);
			}
		}

		if (!result.getJSONArray("nodes").isEmpty()) {
			FileWriter file = new FileWriter(args[2]);
			file.write(result.toString(4));
			file.close();
		}

	}

	private static JSONObject createNewNode(Integer n) {
		JSONObject c = nodeIdToObject.get(n);
		JSONObject o = new JSONObject();
		if (c.has("sourceText")) {
			o.put("sourceText", c.getString("sourceText"));
		}
		o.put("nodeNumber", c.getInt("nodeNumber"));
		o.put("path", c.getJSONArray("path"));
		String klass = findClass(c.getJSONArray("path"));
		o.put("class", klass);
		JSONArray arr = new JSONArray();
		o.put("flowsTo", arr);
		return o;
	}

}
