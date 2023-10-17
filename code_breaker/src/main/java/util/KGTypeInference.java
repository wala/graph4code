package util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.io.Streams;

public class KGTypeInference {
	
	static class Row {
		private String var;
		private String cls;
		private String superClass;
		private String loc;
		private String source;
		
		private String key() {
			return var + " (" + loc + ": " + source + ")";
		}
		
		public Row(String var, String cls, String superClass, String loc, String source) {
			this.var = var;
			this.cls = cls;
			this.superClass = superClass;
			this.loc = loc;
			this.source = source;
		}
	}
	
	private final RDFConnection kg;
	private final String queryProgram;
	
	public KGTypeInference(String kgUri) throws IOException {
		kg = RDFConnectionFactory.connect(kgUri);
		queryProgram = new String(Streams.inputStream2ByteArray(getClass().getClassLoader().getResourceAsStream("typeInference.sparql")));
	}

	private static final String pythonExe = "/Users/dolby/git/code_knowledge_graph/code_breaker/src/main/resources/type.sh";
	
	static private Map<String,String> aliases = HashMapFactory.make();
	
	private static String unaliasType(String uri) throws IOException, InterruptedException {
		String prefix = "http://purl.org/twc/graph4code/python/";

		if (aliases.containsKey(uri)) {
			return aliases.get(uri);
		}
		
		int clsOffset = uri.lastIndexOf('.');
		
		if (clsOffset < prefix.length()) {
			aliases.put(uri, uri);
			return uri;
		}
						
		String cls = uri.substring(clsOffset + 1);
		String pkg = uri.substring(prefix.length(), clsOffset);
		
		Process p = Runtime.getRuntime().exec(new String[] {"sh", pythonExe, pkg, cls});
		
		BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String typeName = prefix + r.readLine();
		
		p.waitFor();
		
		if ((prefix + "null").equals(typeName)) {
			typeName = uri;
		}

		if (! (uri.equals(typeName))) {
			System.err.println(uri +  " -> " + typeName);
		}
		
		aliases.put(uri, typeName);
		
		return typeName;
	}
	
	public Map<String,Set<String>> types(String program) {		
		String query = queryProgram.toString().replace("<program>", program);
		
		Set<Row> rs = HashSetFactory.make();
		kg.querySelect(query, (qs) -> {
			try {
				rs.add(new Row(
						qs.getResource("n1").getURI(), 
						unaliasType(qs.getResource("cls").getURI()),
						unaliasType(qs.getResource("super").getURI()),
//						qs.getResource("cls").getURI(),
//						qs.getResource("super").getURI(),
						qs.getLiteral("loc").getString(),
						qs.getLiteral("n1_source").getString()));
			} catch (IOException | InterruptedException e) {
				assert false : e;
			}
		});

		Map<String, Set<String>> result = filterTypes(rs);
		
		return result;
	}

	public static Map<String, Set<String>> filterTypes(Set<Row> rs) {
		Map<String,Set<String>> result = HashMapFactory.make();

		rs.forEach(row -> {
			if (result.containsKey(row.key()) ) {
				result.get(row.key()).add(row.cls);
			}  else {
				result.put(row.key(), HashSetFactory.make(Collections.singleton(row.cls)));
			}
		});
		
		rs.forEach(row -> {
			if (result.containsKey(row.key())) {
				Set<String> types = result.get(row.key());
				if (row.superClass != row.cls && types.contains(row.superClass)) {
					System.err.println("removing " + row.cls);
					types.remove(row.cls);
				}
			}
		});
		return result;
	}
	
	public static Map<String, Set<String>> inferFromFile(String file) throws JSONException, IOException, InterruptedException {
		JSONObject result = new JSONObject(new JSONTokener(new FileInputStream(file)));
		JSONArray typeRows = result.getJSONObject("results").getJSONArray("bindings");

		Set<Row> rs = HashSetFactory.make();
		for(int i = 0; i < typeRows.length(); i++) {
			JSONObject tr = typeRows.getJSONObject(i);
			rs.add(new Row(
					tr.getJSONObject("n1").getString("value"),
					unaliasType(tr.getJSONObject("cls").getString("value")),
					unaliasType(tr.getJSONObject("super").getString("value")),
					tr.getJSONObject("loc").getString("value"),
					tr.getJSONObject("n1_source").getString("value")));
		};

		return filterTypes(rs);
	}

	public static void main(String... args) throws IOException, JSONException, InterruptedException {
		if (args.length == 1) {
			inferFromFile(args[0]).entrySet().forEach(e -> { 
				System.err.println(e.getKey() + ": " + e.getValue());
			});
		} else {
			KGTypeInference infer = new KGTypeInference(args[0]);
			System.err.println(infer.types(args[1]));
		}
	}
}
