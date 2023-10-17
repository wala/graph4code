package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class CleansedTypeInference {
	protected Map<String, String> normalizedPaths = new HashMap<String, String>();
	protected Map<String, String> returnMap = new HashMap<String, String>();
	protected Map<String, String> classFunctionMap = new HashMap<String, String>();
	List<String> builtins = Arrays.asList("str","string","integer", "int", "bool", "boolean", "float", "list", 
			"tuple", "set", "iterator", "dict", "map");


	public CleansedTypeInference(String analysisFile, String docstringsFile, String classMap, String functionMap) {
		try {
			readFiles(analysisFile);
			readFiles(docstringsFile);
			readMaps(classMap);
			readMaps(functionMap);
		} catch (Exception e) {
			e.printStackTrace();
		}
		;
	}
	

	private String findUsefulKey(String key) {
		// normalize key to some standard class/function name
		if (classFunctionMap.containsKey(key)) {
			key = classFunctionMap.get(key);
		
			if (returnMap.containsKey(key)) {	
				String debug = returnMap.get(key);
				
				return debug;
			}
		}
		// try with the un-normalized key just in case
		if (returnMap.containsKey(key)) {			
			return returnMap.get(key);
		}
		return null;
	}

	public String getNodeLabel(String nodeLabel) {
		if (normalizedPaths.containsKey(nodeLabel)) {
			return normalizedPaths.get(nodeLabel);
		}
		String[] arr = nodeLabel.split("[.]");
		if (arr.length < 2) {
			return nodeLabel;
		}
		String key = arr[0] + '.' + arr[1];

		String val = findUsefulKey(key);
		if (val == null) {
			return nodeLabel;
		}

		if (arr.length > 2) {
			for (int i = 2; i < arr.length; i++) {
				key = val + "." + arr[i];
				String tmp  = findUsefulKey(key);
				if (tmp == null) {
					StringBuffer buf = new StringBuffer();
					buf.append(val).append('.');
					for (int j = i; j < arr.length; j++) {
						buf.append(arr[j]);
						if (j < arr.length - 1) {
							buf.append('.');
						}
					}
					key = buf.toString();
					break;
				} else {
					val = tmp;
				}
			}
		} else if (arr.length == 2){
			key = val;
		}
		
		if (!normalizedPaths.containsKey(nodeLabel)) {
			normalizedPaths.put(nodeLabel, key);
		}
		return key;
	}

	private void readFiles(String arg) throws IOException {
		try (InputStream resource = CleansedTypeInference.class.getClassLoader().getResourceAsStream(arg)) {
			new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).lines().forEach((x) -> {
				StringTokenizer tokenizer = new StringTokenizer(x);
				String p1 = tokenizer.nextToken();
				String p2 = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : p1;
				if (!returnMap.containsKey(p1) || builtins.contains(p2)) {
					returnMap.put(p1, p2);
				}

			});
		}
	}
	
	private void readMaps(String arg) throws IOException {
		try (InputStream resource = CleansedTypeInference.class.getClassLoader().getResourceAsStream(arg)) {
			new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).lines().forEach((x) -> {
				StringTokenizer tokenizer = new StringTokenizer(x);
				
				if (tokenizer.countTokens() < 2) {
					String p1 = tokenizer.nextToken();
					assert !classFunctionMap.containsKey(p1);
					classFunctionMap.put(p1, p1);
					
				} else {
					String p1 = tokenizer.nextToken();
					String p2 = tokenizer.nextToken();
					assert !classFunctionMap.containsKey(p1);
					classFunctionMap.put(p1, p2);
				}
			});
		}
	}
	
	public static void main(String[] args) {
		CleansedTypeInference typeInference = new CleansedTypeInference("cleansed_static_types.txt", "cleansed_docstr.txt", "functions.map", "classes.map");
		System.out.println(typeInference.getNodeLabel("pandas.read_csv"));
		System.out.println(typeInference.getNodeLabel("pandas.read_csv.drop.drop"));
		System.out.println(typeInference.getNodeLabel("pandas.read_csv.drop.loc.drop.drop"));
	}

	
}
