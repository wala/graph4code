package util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.NumberedGraph;
import com.ibm.wala.util.graph.impl.GraphInverter;
import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.DFS;

public abstract class TrainingGraphs {
	
	protected abstract void write(String dir, Set<Pair<NumberedGraph<JSONObject>, String[]>> out) throws IOException;
	
	public void run(String[] args) throws IOException {
		Set<Pair<NumberedGraph<JSONObject>,String[]>> out = HashSetFactory.make();
		JSONObject result = new JSONObject(new JSONTokener(new FileInputStream(args[0])));
		JSONArray graphs = result.getJSONObject("results").getJSONArray("bindings");
		int count = graphs.length();
		boolean trim = false;
		if (args.length > 2 && Integer.valueOf(args[2]) > 0) {
			count = Math.min(count, Integer.valueOf(args[2]));
			if (args.length > 3) {
				trim = Boolean.valueOf(args[3]);
			}
		}
		
		count = count - (count % 10);
		for(int i = 0; i < count; i++) {
			JSONObject elt = graphs.getJSONObject(i);

			String source = elt.getJSONObject("g").getString("value");

			String fitNode = elt.getJSONObject("fit").getString("value");

			String classifier = elt.getJSONObject("fitName").getString("value");
			
			String graphStr = elt.getJSONObject("graph").getString("value");
			JSONObject graph = new JSONObject(new JSONTokener(graphStr));
			
			Graph<JSONObject> G = SlowSparseNumberedGraph.make();
			graph.keys().forEachRemaining(s -> {	
				JSONObject n = graph.getJSONObject(s);
				n.put("name", s);
				G.addNode(n);
			});
			graph.keys().forEachRemaining(s -> {
				graph.getJSONObject(s).getJSONArray("to").forEach(t -> {
					if (graph.has((String)t)) {
						JSONObject from = graph.getJSONObject(s);
						JSONObject to = graph.getJSONObject((String)t);
						if (G.containsNode(from) && G.containsNode(to)) {
							G.addEdge(from, to);
						}
					}
				});
			});
			
			Set<JSONObject> bad = HashSetFactory.make();
			Set<JSONObject> good = HashSetFactory.make();
			Graph<JSONObject> back = GraphInverter.invert(G);
			graph.keys().forEachRemaining(s -> {
				JSONObject p = graph.getJSONObject(s);
				JSONArray ss = p.getJSONArray("to");
				JSONArray si = p.getJSONArray("idxs");
				for(int idx = 0; idx < ss.length(); idx++) {
					if (ss.getString(idx).equals(fitNode)) {
						if (si.getInt(idx) == 0) {
							bad.addAll(DFS.getReachableNodes(back, Collections.singleton(p)));
						} else {
							good.addAll(DFS.getReachableNodes(back, Collections.singleton(p)));
						}
					}
				}
			});
			bad.removeAll(good);
			bad.forEach(n -> G.removeNodeAndEdges(n));
			
			if (trim) {
				while (G.getNumberOfNodes() > 0) {
					out.add(Pair.make(SlowSparseNumberedGraph.duplicate(G), new String[] {classifier, source}));
					JSONObject x = null;
					int nn = G.getNumberOfNodes()+1;
					for(JSONObject n : G) {
						if (G.getSuccNodeCount(n) < nn) {
							nn = G.getSuccNodeCount(n);
							x = n;
						}
					}
					G.removeNodeAndEdges(x);
				}
			} else if (G.getNumberOfNodes() > 0){
				out.add(Pair.make(SlowSparseNumberedGraph.duplicate(G), new String[] {classifier, source}));				
			}
		}
		
		testTrainSplitFiles("10fold_idx", out.size(), null);
		write(args[1], out);
	}

	static void testTrainSplitFiles(String dir, int numberOfGraphs, List<Integer> map) throws IOException {
		File d = new File(dir);
		if (!d.exists()) {
			d.mkdir();
		}
		assert d.isDirectory();
		
		ArrayList<Integer> graphs = new ArrayList<>(numberOfGraphs);
		for(int i = 0; i < numberOfGraphs; i++) {
			graphs.add(i, map==null? i: map.get(i));
		}
		Collections.shuffle(graphs);
		
		int chunk = numberOfGraphs / 10;
		for(int i = 0; i < 10; i++) {
			
			// test
			try (FileWriter f = new FileWriter(d.getAbsolutePath() + File.separator + "test_idx-" + (i+1) + ".txt")) {
				for(int n = i*chunk; n < (i+1)*chunk; n++) {
					f.write(graphs.get(n) + "\n");
				}
			}
			
			// train
			try (FileWriter f = new FileWriter(d.getAbsolutePath() + File.separator + "train_idx-" + (i+1) + ".txt")) {
				for(int n = 0; n < i*chunk; n++) {
					f.write(graphs.get(n) + "\n");
				}
				for(int n = (i+1)*chunk; n < numberOfGraphs; n++) {
					f.write(graphs.get(n) + "\n");
				}
			} 
		}
	}

	public static class Multi extends TrainingGraphs {

		private static Map<String, Set<String>> key(NumberedGraph<JSONObject> G) {
			Map<String, Set<String>> result = HashMapFactory.make();
			G.forEach(n -> { 
				String key = n.getString("name");
				Set<String> ss = HashSetFactory.make();
				G.getSuccNodes(n).forEachRemaining(s -> {
					ss.add(s.getString("name"));
				});
				result.put(key, ss);
			});
			return result;
		}
		
		@Override
		protected void write(String name, Set<Pair<NumberedGraph<JSONObject>, String[]>> out) throws IOException {
			int limit = Integer.parseInt(System.getProperty("codebreaker.limit", "12"));
			
			Map<Map<String, Set<String>>, Set<Pair<NumberedGraph<JSONObject>, String[]>>> groups = HashMapFactory.make();
			out.forEach(g -> { 
				NumberedGraph<JSONObject> graph = g.fst;
				Map<String, Set<String>> key = key(graph);
				if (! groups.containsKey(key)) {
					groups.put(key, HashSetFactory.make());
				}
				groups.get(key).add(g);
			});
			
			JSONArray graphs = new JSONArray();
			
			Map<String,Integer> counts = HashMapFactory.make();
			groups.values().forEach(group -> { 
				Set<String> y = HashSetFactory.make();
				group.iterator().forEachRemaining(g -> {
					String label = g.snd[0];
					if (! y.contains(label)) {
						y.add(label);
						if (! counts.containsKey(label)) {
							counts.put(label, 0);
						}
						counts.put(label, 1 + (Integer) counts.get(label));
					}
				});
			});
				
			Set<Pair<NumberedGraph<JSONObject>, String[]>> gntk = HashSetFactory.make();
			groups.values().forEach(group -> { 
				JSONArray labels = new JSONArray();
				Set<String> y = HashSetFactory.make();
				group.iterator().forEachRemaining(g -> {
					String label = g.snd[0];
					if (! y.contains(label)) {
						labels.put(label);
						y.add(label);
					}
				});
				
				for(int i = 0; i < labels.length(); i++) {
					if (counts.get(labels.get(i)) < limit) {
						labels.remove(i);
						i--;
					}
				}
					
				if (labels.length() == 0) {
					return;
				}
				
				JSONObject JG = new JSONObject();
				NumberedGraph<JSONObject> G = group.iterator().next().fst;
				G.iterator().forEachRemaining(n -> { 
					JG.put(n.getString("name"), n);
				});
				
				JSONObject x = new JSONObject();
				x.put("labels", labels);
				x.put("graph", JG);
				
				graphs.put(x);
				gntk.add(group.iterator().next());
			});
			
			(new GNTK()).write(name + "_gntk", gntk);
			
			try (FileWriter f = new FileWriter(name + ".txt")) {
				graphs.write(f, 2, 0);
			}
		}
		
		public static void main(String... args) throws IOException {
			(new Multi()).run(args);
		}
	}
	
	public static class GNTK extends TrainingGraphs {

		@Override
		protected void write(String name, Set<Pair<NumberedGraph<JSONObject>, String[]>> graphs) throws IOException {
			boolean readable = Boolean.getBoolean("codebreaker.readable");
			int limit = Integer.parseInt(System.getProperty("codebreaker.limit", "0"));
			
			Map<String,Integer> labels = HashMapFactory.make();
			Map<String,Integer> features = HashMapFactory.make();

			try (FileWriter f = new FileWriter(name + ".txt")) {

				f.write(graphs.size() + "\n");
				for(Pair<NumberedGraph<JSONObject>, String[]> gl : graphs) {
					String label = gl.snd[0];
					if (! labels.containsKey(label)) {
						labels.put(label, labels.size());
					}

					if (readable) {
						f.write(gl.snd[1] + "\n");
					}

					NumberedGraph<JSONObject> G = gl.fst;

					f.write(G.getNumberOfNodes() + " " + (readable? label: labels.get(label)) + "\n");

					for(JSONObject n : G) {
						String feature = n.getString("path");
						if (feature.contains(".")) {
							feature = feature.substring(feature.lastIndexOf(".") + 1);
						}
						if (! features.containsKey(feature)) {
							features.put(feature, features.size());
						}

						f.write((readable? feature: features.get(feature)) + " " + G.getSuccNodeCount(n) + " ");

						for(Iterator<JSONObject> s = G.getSuccNodes(n); s.hasNext(); ) {
							f.write(G.getNumber(s.next()) + " ");
						}

						if (readable && G.getSuccNodeCount(n) > 0 && G.getSuccNodeCount(n) < limit) {
							f.write(" ( ");
							for(Iterator<JSONObject> s = G.getSuccNodes(n); s.hasNext(); ) {
								f.write(s.next().getString("path") + " ");
							}
							f.write(")");
						}
						
						f.write("\n");
					}
				}
			}
			
			try (FileWriter f = new FileWriter(name + ".features.txt")) {
				features.forEach((feature, number) -> {
					try {
						f.write(feature + "\t" + number + "\n");
					} catch (IOException e) {
						assert false : e;
					}
				});
			}

			try (FileWriter f = new FileWriter(name + ".labels.txt")) {
				labels.forEach((label, number) -> {
					try {
						f.write(label + "\t" + number + "\n");
					} catch (IOException e) {
						assert false : e;
					}
				});
			}
		}	
		
		public static void main(String... args) throws IOException {
			(new GNTK()).run(args);
		}
	}
}
