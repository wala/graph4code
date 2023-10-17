package util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.DFS;

public class MassageTypeInferenceData {

	private static final class OrderComparator implements Comparator<String> {
		private final Map<String, Integer> methodCounts;

		private OrderComparator(Map<String, Integer> methodCounts) {
			this.methodCounts = methodCounts;
		}

		@Override
		public int compare(String o1, String o2) {
			return methodCounts.get(o2) - methodCounts.get(o1);
		}
	}

	private static final class SuperComparator implements Comparator<String> {
		private final Map<String, Integer> topSupers;

		private SuperComparator(Map<String, Integer> topSupers) {
			this.topSupers = topSupers;
		}

		@Override
		public int compare(String o1, String o2) {
			int v22 = (topSupers.containsKey(o2)? topSupers.get(o2): 0);
			int v12 = (topSupers.containsKey(o1)? topSupers.get(o1): 0);
			return v22 - v12;
		}
	}

	private static String prefix = "http://purl.org/twc/graph4code/python/";

	private static Graph<String> supers = SlowSparseNumberedGraph.make();
	
	public static void main(String... args) throws FileNotFoundException, IOException {
		String line;		
		
		String superClassesFile = args[0];
		BZip2CompressorInputStream superClassesIn = new BZip2CompressorInputStream(new FileInputStream(superClassesFile));
		BufferedReader in = new BufferedReader(new InputStreamReader(superClassesIn));
		in.readLine();
		while((line = in.readLine()) != null) {
			StringTokenizer toks = new StringTokenizer(line, ",");
			String cls = toks.nextToken();
			if (! supers.containsNode(cls)) {
				supers.addNode(cls);
			}
			String scls = toks.nextToken().substring(prefix.length());
			if (! supers.containsNode(scls)) {
				supers.addNode(scls);
			}
			if (! "object".equals(scls)) {
				supers.addEdge(cls, scls);
			}
		}

		String inferenceFile = args[1];
		BZip2CompressorInputStream inferenceIn = new BZip2CompressorInputStream(new FileInputStream(inferenceFile));
		BufferedReader inferenceLines = new BufferedReader(new InputStreamReader(inferenceIn));
		String old = null;
		Set<Pair<String,Integer>> lines = HashSetFactory.make();
		while ((line = inferenceLines.readLine()) !=  null) {
			StringTokenizer lt = new StringTokenizer(line);
			String function = lt.nextToken();
			int count = new Integer(lt.nextToken());
			String type = lt.nextToken();
			if (old == null) {
				old = function;
			}
			if (! old.equals(function)) {
				process(old, lines);		
				old = function;
				lines.clear();
			} 
			lines.add(Pair.make(type.substring(prefix.length()), count));
		}
	}

	static private String module(String name) {
		if (name.contains(".")) {
			return name.substring(0, name.indexOf('.'));
		} else {
			return null;
		}
	}
	
	static private void process(String function, Set<Pair<String,Integer>> types) {
		
		/*
		boolean same = false;
		String module = module(function);
		if (module != null) {
			for(Pair<String,Integer> type : types) {
				String tm = module(type.fst);
				if (module.equals(tm)) {
					same = true;
					break;
				}
			}
			if (same) {
				Iterator<Pair<String, Integer>> ts = types.iterator();
				while (ts.hasNext()) {
					Pair<String,Integer> t = ts.next();
					if (! module.equals(module(t.fst))) {
						ts.remove();
					}
				}
			}
		}
		*/
		
		Map<String,Integer> superCounts = HashMapFactory.make();
		for(Pair<String, Integer> line : types) {
			String cls = line.fst;
			
			if (supers.containsNode(cls)) {
				DFS.getReachableNodes(supers, Collections.singleton(cls)).forEach((s) -> { 
					if (! superCounts.containsKey(s)) {
						superCounts.put(s, 1);
					} else {
						superCounts.put(s, 1 + superCounts.get(s));
					}
				});
			}
		}
		
		Map<String,Integer> topSupers = HashMapFactory.make();
		for(Pair<String, Integer> line : types) {
			String cls = line.fst;
			topSupers.put(cls, superCounts.containsKey(cls)? superCounts.get(cls): 0);
		}

		Map<String,Integer> methodCounts = HashMapFactory.make();
		types.forEach(x -> methodCounts.put(x.fst, x.snd));
		
		
		List<String> orderedByCount = new ArrayList<>();
		types.forEach(x -> orderedByCount.add(x.fst));
		
		OrderComparator orderComparator = new OrderComparator(methodCounts);
		Collections.sort(orderedByCount, orderComparator);

		List<String> orderedBySupers = new ArrayList<>();
		types.forEach(x -> orderedBySupers.add(x.fst));
		
		SuperComparator superComparator = new SuperComparator(topSupers);
		Collections.sort(orderedBySupers, superComparator);

		List<String> orderedByBoth = new ArrayList<>();
		types.forEach(x -> { 
			if (! orderedByBoth.contains(x.fst)) {
				orderedByBoth.add(x.fst);
			}
		});
		Collections.sort(orderedByBoth, (a, b) ->
			orderComparator.compare(a, b) != 0?
					orderComparator.compare(a, b): 
					superComparator.compare(a, b));

		for(Pair<String, Integer> type : types) {
			if (function.equals(type.fst)) {
				System.err.println(function + " " + type.fst + " " + methodCounts.get(type.fst) + " " + topSupers.get(type.fst));
				return;
			}
		}
		
		int count = 3;
		for(String type : orderedByBoth) {
			if (--count <= 0) {
				break;
			}
			
			System.err.println(function + " " + type + " " + methodCounts.get(type) + " " + topSupers.get(type));
		}

		/*
		Set<String> typesDone = HashSetFactory.make();
		int count = 3;
		for(String type : orderedByCount) {
			if (--count <= 0) {
				break;
			}
			
			typesDone.add(type);
			System.err.println(function + " " + type + " " + methodCounts.get(type) + " " + topSupers.get(type));
		}
		int count2 = 3;
		for(String type : orderedBySupers) {
			if (! typesDone.contains(type)) {
				if (--count2 <= 0) {	
					break;
				}
			
				System.err.println(function + " " + type + " " + methodCounts.get(type) + " " + topSupers.get(type));
			}
		}
		*/
	}
}
