package util;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONObject;

public class GetTurtleDependencies {
	
	public static void main(String... args) throws IllegalArgumentException, IOException {
		// read the set of methods used in GitHub (all names of turtle paths) that are in methods/functions
		HashSet<String> usedMethods = new HashSet<String>();
		
		Files.lines(Paths.get(args[0])).forEach(usedMethods::add);
	      
		HashMap<String, HashMap<String, Integer>> pathsToCounts = new HashMap<String, HashMap<String, Integer>>();
		// read all possible turtle labels 

		Files.lines(Paths.get(args[1])).forEach((m) -> {
			int lastIndex = m.lastIndexOf('.');
			if (lastIndex == -1) {
				System.out.println("possible import");
				return;
			}
			String method = m.substring(0, lastIndex);
			String flowsTo = m.substring(lastIndex + 1);
			
			if (usedMethods.contains(method) || usedMethods.contains(m)) {
				if (!pathsToCounts.containsKey(method)) {
					pathsToCounts.put(method, new HashMap<String, Integer>());
				} 
				HashMap<String, Integer> flowsToCounts = pathsToCounts.get(method);
				if (!flowsToCounts.containsKey(flowsTo)) {
					flowsToCounts.put(flowsTo, 1);
				} else {
					int i = flowsToCounts.get(flowsTo);
					flowsToCounts.put(flowsTo, i + 1);
				}
			} else {
				System.out.println("cannot find method:" + m);
			}
		});
		
		JSONArray results = new JSONArray();
		pathsToCounts.forEach((method,flowsToCount) -> {
			JSONObject obj = new JSONObject();
			results.put(obj);
			JSONObject counts = new JSONObject();
			obj.put(method, counts);
			flowsToCount.forEach((flowsTo, count)-> {
				counts.put(flowsTo, count);
			});
		});
		
		FileWriter out = new FileWriter(args[2]);
		results.write(out, 2, 0);
		out.flush();
		out.close();
	}
	

}
