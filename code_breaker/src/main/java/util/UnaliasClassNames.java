package util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;

public class UnaliasClassNames {

	private static final String prefix = "http://purl.org/twc/graph4code/python/";

	public static void main(String... args) throws IOException {
		Map<String,String> unalias = HashMapFactory.make();
		try (BufferedReader map = new BufferedReader(new FileReader(args[1]))) {
			map.lines().forEach((l) -> {
				String[] m = l.split(" ");
				if (m.length == 2) {
					unalias.put(m[0], m[1]);
				}
			});
		}

		Set<String> fail = HashSetFactory.make();
		try (BufferedReader map = new BufferedReader(new FileReader(args[2]))) {
			map.lines().forEach((l) -> {
				fail.add(prefix + l);
			});
		}

		int top = Integer.MAX_VALUE;
		if (args.length > 3) {
			top = Integer.parseInt(args[3]);
		}
		
		FileReader fr = new FileReader(args[0]);
		JSONTokener reader = new JSONTokener(fr);
		JSONObject fun  = null;
		do {			
			reader.nextClean();
			if (! reader.more()) {
				break;
			}

			fun = (JSONObject)reader.nextValue();
			String name = fun.getString("method");
			NavigableMap<Integer,Set<String>> realTypes = new TreeMap<>();
			JSONArray types = fun.getJSONArray("possible_types");
			
			Map<String, Set<String>> supers = HashMapFactory.make();
			for(int j = 0; j < types.length(); j++) {
				JSONObject type = types.getJSONObject(j);

				String superUri = type.getString("superclass");
				String scls = superUri.substring(prefix.length());
				if (unalias.containsKey(scls)) {
					superUri = prefix + unalias.get(scls);
				}

				String su = superUri;
				Function<String,Void> addSuper = (classUri) -> {
					String cls = classUri.substring(prefix.length());
					if (unalias.containsKey(cls)) {
						classUri = prefix + unalias.get(cls);
					}
					if (! supers.containsKey(classUri)) {
						supers.put(classUri, HashSetFactory.make());
					}
					supers.get(classUri).add(su);
					return null;
				};
				
				if (type.has("classes")) {
					JSONArray classes = type.getJSONArray("classes");
					for(int i = 0; i < classes.length(); i++ ) {
						addSuper.apply(classes.getString(i));
					}
				}
				
				if (type.has("class")) {
					addSuper.apply(type.getString("class"));
				}
			}
			
			for(int j = 0; j < types.length(); j++) {
				JSONObject type = types.getJSONObject(j);
				int classCount = type.getInt("count");
				
				Function<String,Void> addType = (classUri) -> {
					String cls = classUri.substring(prefix.length());
					if (unalias.containsKey(cls)) {
						classUri = prefix + unalias.get(cls);
					}

					if (! fail.contains(classUri)) {
						if (realTypes.containsKey(classCount) ) {
							realTypes.get(classCount).add(classUri);
						} else {
							Set<String> xx = HashSetFactory.make();
							xx.add(classUri);
							realTypes.put(classCount, xx);
						}
					}
					return null;
				};
				
				if (type.has("classes")) {
					JSONArray classes = type.getJSONArray("classes");
					for(int i = 0; i < classes.length(); i++ ) {
						String classUri = classes.getString(i);
						addType.apply(classUri);
					}
				}
				
				if (type.has("class")) {
					addType.apply(type.getString("class"));
				}
			}
			
			/*
			realTypes.entrySet().forEach(es -> {
				for(Iterator<String> tss = es.getValue().iterator(); tss.hasNext(); ) {
					String type = tss.next();
					if (!Collections.disjoint(es.getValue(), supers.get(type))) {
						tss.remove();
					}
				}
			});
			*/
			
			int i = 0;
			Entry<Integer, Set<String>> e = realTypes.lastEntry();
			while (i++ < top && e != null) {
				int count = e.getKey();
				e.getValue().forEach((cls) -> { 
					System.out.println(name + " " + count + " " + cls);
				});
				e = realTypes.lowerEntry(e.getKey());
			}
			
		} while(fun != null);
	}
}
