package util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.ibm.wala.util.collections.HashMapFactory;

public class MergeModules {

	static class Key {
		String klass;
		String function;
		public Key(String klass, String function) {
			this.klass = klass;
			this.function = function;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((function == null) ? 0 : function.hashCode());
			result = prime * result + ((klass == null) ? 0 : klass.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Key other = (Key) obj;
			if (function == null) {
				if (other.function != null)
					return false;
			} else if (!function.equals(other.function))
				return false;
			if (klass == null) {
				if (other.klass != null)
					return false;
			} else if (!klass.equals(other.klass))
				return false;
			return true;
		}
		
	}
	
	private static void readTree(File root, Map<Key,JSONObject> functions) throws FileNotFoundException {
		if (root.isDirectory()) {
			for (File f : root.listFiles()) {
				readTree(f, functions);
			}
		} else if (root.getName().endsWith(".json")) {
			JSONTokener toks = new JSONTokener(new FileInputStream(root));
			JSONArray objs = new JSONArray(toks);
			for(int i = 0; i < objs.length(); i++) {
				JSONObject obj = objs.getJSONObject(i);
				String klass = obj.has("klass")? obj.getString("klass"): null;
				String fun = obj.has("function")? obj.getString("function"): null;
				Key k = new Key(klass, fun);
				assert !functions.containsKey(k);
				functions.put(k, obj);
			}
		}
	}
	
	public static void main(String[] args) throws FileNotFoundException {
		File leftFile = new File(args[0]);
		Map<Key,JSONObject> left = HashMapFactory.make();
		readTree(leftFile, left);
		
		File rightFile = new File(args[1]);
		Map<Key,JSONObject> right = HashMapFactory.make();
		readTree(rightFile, right);
		
		Map<Key,JSONObject> merge = HashMapFactory.make(left);
		right.forEach((k, v) -> {
			if (! merge.containsKey(k)) {
				merge.put(k, v);
			}
		});
		
		JSONArray out = new JSONArray();
		merge.values().forEach((v) -> { out.put(v); });
		
		PrintWriter pw = new PrintWriter(System.out);
		out.write(pw);
		pw.flush();
		pw.close();
	}

}
