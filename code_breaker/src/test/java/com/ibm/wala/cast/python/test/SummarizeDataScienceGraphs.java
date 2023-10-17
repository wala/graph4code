package com.ibm.wala.cast.python.test;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDFS;

import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.DFS;
import com.ibm.wala.util.graph.traverse.FloydWarshall;
import com.ibm.wala.util.graph.traverse.FloydWarshall.GetPaths;

public class SummarizeDataScienceGraphs {
	static String graph4codeNamespace = "http://purl.org/twc/graph4code/";

	public static void main(String[] args) {
		Dataset ds = RDFDataMgr.loadDataset(args[0]);
		Iterator<String> graphs = ds.listNames();
		while (graphs.hasNext()) {
			Map<String, String> node2Label = new HashMap<String, String>();
			Model m = ds.getNamedModel(graphs.next());
			String dataFlow =graph4codeNamespace + "flowsTo";
			String label = RDFS.label.getURI();
			
			SlowSparseNumberedGraph<String> g = SlowSparseNumberedGraph.make();

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
					g.addEdge(subject, object);
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
			System.out.println("nodes:" + nodes);
			System.out.println("end nodes:" + filteredNodes);
			
			System.out.println("Paths");
			GetPaths<String> paths = FloydWarshall.allPairsShortestPaths(g);
			for (String start : nodes) {
				for (String end : filteredNodes) {
					try {
						Set<List<String>> p = paths.getPaths(start, end);
						for (List<String> x : p) {
							System.out.println(x);
						}
					} catch (UnsupportedOperationException e) {
						
					}
				}
			}
			
		}
	}
}
