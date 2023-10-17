package util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Collections;
import java.util.Set;
import java.util.StringTokenizer;

import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.DFS;

public class GraphsExtractor {

	public static void main(String... args) throws Exception {
		Graph<String> rdf = SlowSparseNumberedGraph.make();
		
		BufferedReader lines = new BufferedReader(new FileReader(args[0]));
		String line;
		while ((line = lines.readLine()) != null) {
			StringTokenizer toks = new StringTokenizer(line);
			String source = toks.nextToken();
			String pred = toks.nextToken();
			String target = toks.nextToken();
			if ("<http://edge/dataflow>".equals(pred)) {
				if (! rdf.containsNode(source)) {
					rdf.addNode(source);
				}
				if (! rdf.containsNode(target)) {
					rdf.addNode(target);
				}
				if (! rdf.hasEdge(source, target)) {
					rdf.addEdge(source, target);
				}
			}
		}
		
		rdf.forEach((node) -> {
			if (rdf.getPredNodeCount(node) == 0 && rdf.getSuccNodeCount(node) > 0) {
				Set<String> nodes = DFS.getReachableNodes(rdf, Collections.singleton(node));
				System.err.println(node + ":" + nodes);
			}
		});
	}
}
