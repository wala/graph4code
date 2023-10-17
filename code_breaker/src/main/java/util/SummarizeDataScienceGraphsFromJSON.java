package util;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.Iterator2List;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.impl.GraphInverter;
import com.ibm.wala.util.graph.labeled.SlowSparseNumberedLabeledGraph;
import com.ibm.wala.util.graph.traverse.DFS;
import com.ibm.wala.util.graph.traverse.Topological;

public class SummarizeDataScienceGraphsFromJSON {
	
	public static List<String> libNames = Arrays.asList("sklearn", "pyearth", "torch", "tensorflow", "xgboost", "lightgbm", "statsmodels");
	protected static int numNodes = 0;

    public static JSONObject parseJSONFile(String filename) throws JSONException, IOException {
        String content = new String(Files.readAllBytes(Paths.get(filename)));
        return new JSONObject(content);
    }
    
    public static boolean filterSubgraph(Set<Integer> subgraph, Map<Integer, JSONObject> nodeIdToObject) {
    	for (Integer x : subgraph) {
    		JSONArray arr = nodeIdToObject.get(x).getJSONArray("path");
    		if (libNames.contains(arr.get(0))) {
    			return false;
    		}
    	}
    	return true;
    }
	
	public static void main(String[] args) throws JSONException, IOException {
		JSONObject graphAsJSON = parseJSONFile(args[0]);
		JSONArray nodes = graphAsJSON.getJSONArray("turtle_analysis");		

		SlowSparseNumberedLabeledGraph<Integer, String> g = new SlowSparseNumberedLabeledGraph<Integer, String>(
				"dataflow");
		
		Map<Integer, JSONObject> nodeIdToObject = new HashMap<Integer, JSONObject>();

		Set<Integer> startNodes = new HashSet<Integer>();
		
		
		Map<Pair<Integer, String>, Integer> containerFieldMap = HashMapFactory.make();
		
		for (Object a : nodes) {
			if (a.equals(null)) {
				continue;
			}
			JSONObject node = (JSONObject) a;
			
			nodeIdToObject.put(node.getInt("nodeNumber"),node);
			String normalizedKey = node.getString("normalizedLabel");
			
			if (normalizedKey.equals("pandas.core.frame.DataFrame")) {
				startNodes.add(node.getInt("nodeNumber"));
			}
			if (!g.containsNode(node.getInt("nodeNumber"))) {
				int nodeNum = node.getInt("nodeNumber");
				g.addNode(nodeNum);
				numNodes = Math.max(numNodes, nodeNum);
			}
		}
		
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
						g.addEdge(node.getInt("nodeNumber"), vii, "dataFlow");
					}

				});
			}
			
			
			JSONArray reads = node.getJSONArray("reads");
			
			handleReadsWrites(g, nodeIdToObject, containerFieldMap, node, reads, "read");
			
			JSONArray writes = node.getJSONArray("writes");
			
			handleReadsWrites(g, nodeIdToObject, containerFieldMap, node, writes, "write");
			
		}  
		List<Integer> top_sorted = new LinkedList<Integer>();
		// this sort just ensures the source lines get printed out in order.
		new Iterator2List<Integer>(Topological.makeTopologicalIter(g).iterator(), top_sorted);
	

		Set<Integer> endNodes = DFS.getReachableNodes(g, startNodes);

		Set<Integer> filteredNodes = new HashSet<Integer>();
			for (Integer s : endNodes) {
				if (g.getSuccNodeCount(s) == 0) {
					filteredNodes.add(s);
				}
		}
			
		JSONObject all = new JSONObject();	
		
		for (Integer x : filteredNodes) {
			if (!nodeIdToObject.get(x).has("normalizedLabel")) {
				continue;
			}
			Set<Integer> backwards_flow = DFS.getReachableNodes(GraphInverter.invert(g), Collections.singletonList(x));
			if (filterSubgraph(backwards_flow, nodeIdToObject)) {
				continue;
			}
			//System.out.println("GRAPH FOR:" + nodeIdToObject.get(x).getString("normalizedLabel"));

			List<Integer> top_sorted_full = new LinkedList<Integer>(top_sorted);
			top_sorted_full.retainAll(backwards_flow);
			JSONArray source = new JSONArray();

			JSONArray relevantSubgraph = new JSONArray();		
			for (Integer n : top_sorted_full) {
				JSONObject o = nodeIdToObject.get(n);
				
				assert o.getInt("nodeNumber") == n;
				relevantSubgraph.put(o);
				if (!o.has("sourceLines")) {
					// System.out.println("NO SOURCE:"+"<"+ n + "> " + o.getString("path"));
					continue;
				}
				
				for (Object obj : o.getJSONArray("sourceLines")) {
						//System.out.println(obj);
						source.put(obj);
				}
				
			}
			if (relevantSubgraph.length() == 0) {
				continue;
			}
			//System.out.println(relevantSubgraph.length());
			//System.out.println("--------------------");
			JSONObject subg = new JSONObject();
			subg.put("subgraph", relevantSubgraph);
			subg.put("source", source);
			all.put(nodeIdToObject.get(x).getString("normalizedLabel"), subg);
		}
		if (!all.isEmpty()) {
	        FileWriter file = new FileWriter(args[1]);
	        file.write(all.toString(4));
	        file.close();
		}

	}

	private static int handleReadsWrites(SlowSparseNumberedLabeledGraph<Integer, String> g,
			Map<Integer, JSONObject> nodeIdToObject, Map<Pair<Integer, String>, Integer> containerFieldMap,
			JSONObject node, JSONArray readsWrites, String readWrite) {
		for (Object v : readsWrites) {
			JSONObject vi = (JSONObject) v;
			JSONArray containers = vi.getJSONArray("container");
			for (Object k : containers) {
				Integer containerId = (Integer) k;
				String field;
				if (vi.get("field") instanceof Integer) {
					field = Integer.toString(vi.getInt("field"));
				} else {
					field = vi.getString("field");
				}
				Pair<Integer, String> key = Pair.make(containerId, field);
				if (!containerFieldMap.containsKey(key)) {
					numNodes++;
					containerFieldMap.put(key, numNodes);
					JSONObject z = new JSONObject();
					z.put("nodeNumber", numNodes);
					z.put("container", containerId);
					z.put("field", field);
					z.put("sourceLine", "");
					z.put("sourceText", "");
					JSONArray p = new JSONArray();
					p.put(nodeIdToObject.get(containerId).getString("normalizedLabel") + "#" + field);
					z.put("path", p);
					nodeIdToObject.put(numNodes, z);
					g.addNode(numNodes);
				}
				g.addEdge(numNodes, node.getInt("nodeNumber"), readWrite);
				
			}
		}
		return numNodes;
	}
}
