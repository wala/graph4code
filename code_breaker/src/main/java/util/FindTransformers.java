package util;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.compress.compressors.CompressorException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FindTransformers {
	public static void main(String[] args) throws JSONException, IOException, CompressorException {
		File dir = new File(args[0]);
		File[] directoryListing = dir.listFiles();
		Set<String> transformers = new HashSet<String>();
		
		for (File child : directoryListing) {
			try {
				System.out.println("Processing:" + child.getName());
				JSONObject graphAsJSON = CreatePipelinesFromGraphs.parseJSONFile(child.getAbsolutePath());
				JSONArray nodes = graphAsJSON.getJSONArray("turtle_analysis");
	
				for (Object a : nodes) {
					if (a.equals(null)) {
						continue;
					}
					JSONObject node = (JSONObject) a;
					if (node.getString("path_end").equals("transform") || node.getString("path_end").equals("fit_transform")) {
						JSONArray arr = node.getJSONArray("path");
						StringBuffer buf = new StringBuffer();
						for (int i = 0; i < arr.length() - 1; i++) {
							buf.append(arr.getString(i));
							if (i < arr.length() - 1) {
								buf.append(".");
							}
						}
						transformers.add(buf.toString());
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		for (String t : transformers) {
			System.out.println(t);
		}

	}

}
