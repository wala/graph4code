package util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.ibm.wala.util.collections.Iterator2List;
import com.ibm.wala.util.graph.impl.GraphInverter;
import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.DFS;
import com.ibm.wala.util.graph.traverse.Topological;

public class CreateSubGraphs {
	
	protected static int numNodes = 0;

    public static JSONObject parseJSONFile(String filename) throws JSONException, IOException {
        String content = new String(Files.readAllBytes(Paths.get(filename)));
        return new JSONObject(content);
    }
    
    static Pattern sink = Pattern.compile("[a-zA-Z0-9_]+[.][a-zA-Z0-9_]+[(].*[)]");
    
	private static boolean isPlausibleSink(JSONObject turtle) {
		return
			! turtle.getJSONObject("edges").has("flowsTo") &&
			turtle.getJSONArray("reads").length() == 0 &&
			turtle.has("sourceText") &&
			sink.matcher(turtle.getString("sourceText")).matches();
	}
	
	public static void main(String[] args) throws JSONException, IOException {
		JSONObject graphAsJSON = parseJSONFile(args[0]);
		JSONArray nodes = graphAsJSON.getJSONArray("turtle_analysis");		

		SlowSparseNumberedGraph<Integer> g = SlowSparseNumberedGraph.make();
		
		Map<Integer, JSONObject> nodeIdToObject = new HashMap<Integer, JSONObject>();

		Set<Integer> sinks = new HashSet<Integer>();
		
		for (Object a : nodes) {
			if (a.equals(null)) {
				continue;
			}
			JSONObject node = (JSONObject) a;
				
			if (!g.containsNode(node.getInt("nodeNumber"))) {
				int nodeNum = node.getInt("nodeNumber");
				g.addNode(nodeNum);
				nodeIdToObject.put(nodeNum, node);
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
						g.addEdge(node.getInt("nodeNumber"), vii);
					}

				});
			} 
		}  
		
		for (Object a : nodes) {
			if (a.equals(null)) {
				continue;
			}

			JSONObject node = (JSONObject) a;
		
			if (((JSONObject) node.get("edges")).has("flowsTo")) {
				JSONObject dataflow = ((JSONObject) node.get("edges")).getJSONObject("flowsTo");

				if (dataflow.has("0")) {
					dataflow.getJSONArray("0").forEach(x -> {
						JSONObject s = nodeIdToObject.get((Integer)x);
						if (isPlausibleSink(s)) {
							sinks.add(s.getInt("nodeNumber"));
						}
					});
				}
			}
		}
		
		List<Integer> top_sorted = new LinkedList<Integer>();
		// this sort just ensures the source lines get printed out in order.
		new Iterator2List<Integer>(Topological.makeTopologicalIter(g).iterator(), top_sorted);
			
		for (Integer x : sinks) {
			if (!nodeIdToObject.get(x).has("normalizedLabel") || !nodeIdToObject.get(x).has("sourceLines")) {
				continue;
			}
			Set<Integer> backwards_flow = DFS.getReachableNodes(GraphInverter.invert(g), Collections.singletonList(x));
			
			backwards_flow = new HashSet<>(backwards_flow.stream().filter(y -> nodeIdToObject.get(y).has("sourceLines")).collect(Collectors.toList()));
			if (backwards_flow.size() < 3) {
				continue;
			}

			List<Integer> top_sorted_full = new LinkedList<Integer>(top_sorted);
			Collections.reverse(top_sorted_full);
			top_sorted_full.retainAll(backwards_flow);
			top_sorted_full.remove(x);
			
			int i = 0;
			Map<Integer, String> lineToStrings = new TreeMap<Integer, String>();
			for (Integer n : top_sorted_full) {
				JSONObject o = nodeIdToObject.get(n);
				
				assert o.getInt("nodeNumber") == n;
				
				if (!o.has("sourceLines")) {
					// System.out.println("NO SOURCE:"+"<"+ n + "> " + o.getString("path"));
					continue;
				}
				
				String line = o.getString("sourceLocation");
				Pattern p = Pattern.compile("\\[([0-9]*):([0-9]*)\\]");
				int sl = Integer.parseInt(p.matcher(line).results().iterator().next().group(1));
				/*
				int i = 0;
				for (Object obj : o.getJSONArray("sourceLines")) {
					String s = (String) obj;
					lineToStrings.put(sl+i, s);
					i++;
				}
				*/
				lineToStrings.put(i--,  o.getString("sourceText"));
			}
			
			System.out.println("SOURCE:");
			lineToStrings.values().forEach(s->System.out.println(s));
			System.out.println("SINK:");
			System.out.println(nodeIdToObject.get(x).get("sourceLines"));
			JSONArray sp = nodeIdToObject.get(x).getJSONArray("path");
			System.out.println(sp.get(sp.length()-1));
			System.out.println(nodeIdToObject.get(x));
		}
	}
}
