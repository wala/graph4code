package util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import com.ibm.wala.util.graph.NumberedEdgeManager;
import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.BFSPathFinder;

/**
 * Given a set of classes-superclass relations, compute distances in class hierarchy
 * from one class to all other connected classes
 * @author kavithasrinivas
 *
 */
public class ComputeClass2ClassDistance {

	public static void printNeighbors(SlowSparseNumberedGraph<String> graph, String node) {
		NumberedEdgeManager<String> edgeManager = graph.getEdgeManager();
		System.out.println("Preds");
		Iterator<String> it = edgeManager.getPredNodes(node);
		while (it.hasNext()) {
			System.out.println(it.next());
		}
		it = edgeManager.getSuccNodes(node);
		System.out.println("Succs");
		while (it.hasNext()) {
			System.out.println(it.next());
		}
	}
	
	public static void main(String[] args) throws Exception {
		BufferedReader classesReader = new BufferedReader(new FileReader(args[0]));

		
		String line = null;
		
		SlowSparseNumberedGraph<String> graph = SlowSparseNumberedGraph.make();
		line = classesReader.readLine(); 		// discard first line
		while ((line = classesReader.readLine()) != null) {
			StringTokenizer tokenizer = new StringTokenizer(line, ",");
			assert tokenizer.countTokens() == 2;
			String  clazz = tokenizer.nextToken();
			String superclazz = tokenizer.nextToken();
			superclazz = superclazz.substring("http://purl.org/twc/graph4code/python/".length());
			if (!graph.containsNode(clazz)) {
				graph.addNode(clazz);
			}
			if (!graph.containsNode(superclazz)) {
				graph.addNode(superclazz);
			}
			if (superclazz.equals("object")) {
			    continue;
			}
			graph.addEdge(clazz, superclazz);
			graph.addEdge(superclazz, clazz);
		}
		classesReader.close();
		
		Set<String> realClasses = new HashSet<String>();
		BufferedReader classesMapReader = new BufferedReader(new FileReader(args[2]));
		while ((line = classesMapReader.readLine()) != null) {
			StringTokenizer tokenizer = new StringTokenizer(line, " ");
			if (tokenizer.countTokens() < 2) {
			    continue;
			}
			tokenizer.nextToken();
			realClasses.add(tokenizer.nextToken());
		}
		classesMapReader.close();
		
		BufferedReader classesFailReader = new BufferedReader(new FileReader(args[3]));
		while ((line = classesFailReader.readLine()) != null) {
			line = line.trim();
			realClasses.add(line);
		}
		classesFailReader.close();
		
		Set<String> classesWithNoEdges = new HashSet<String>();
		// identify classes with no edges at all
		for (String node : graph) {
			if (!(graph.getEdgeManager().getPredNodes(node).hasNext()) && !(graph.getEdgeManager().getSuccNodes(node).hasNext())) {
				classesWithNoEdges.add(node);
			}
		}

		
		BufferedReader classes2NeighborsReader = new BufferedReader(new FileReader(args[4]));
		while ((line = classes2NeighborsReader.readLine()) != null) {
			StringTokenizer tokenizer = new StringTokenizer(line, " ");
			assert tokenizer.countTokens() == 2;
			String neighbor  = tokenizer.nextToken();
			String node = tokenizer.nextToken();
			if (!realClasses.contains(node)) {
			    continue;
			}
			if (classesWithNoEdges.contains(node)) {
				System.out.println("SKIPPING CLASS:" + node);
				continue;
			}
			BFSPathFinder finder = new BFSPathFinder(graph, neighbor, node);
			List<String> path = finder.find();
			int maxPathLengthConsidered = 11;
			if (path != null) {
			    int pathSize = path.size() - 1;
			    System.out.println(path);
			    System.out.println(neighbor + " " + node + " "  + " " + Math.max(maxPathLengthConsidered - pathSize, 0));
			} else {
				System.out.println("No path found:");
				printNeighbors(graph, node);
				System.out.println(neighbor + " " + node + " " + "0");
			}
		}
		classes2NeighborsReader.close();
	}
}
