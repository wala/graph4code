package util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.json.simple.parser.ParseException;

public class GetUsagePatternsForClass {
	// gather all usage patterns for a specific class
	public static void main(String... args) throws IllegalArgumentException, IOException, ParseException {
		BufferedReader usageReader = new BufferedReader(new FileReader(args[0]));
		BufferedReader classesReader = new BufferedReader(new FileReader(args[1]));

		String line = null;

		Set<String> classes = new HashSet<String>();
		Map<String, String> classMap = new HashMap<String, String>();
		
		while ((line = classesReader.readLine()) != null) {
			String c = line.trim();
			classes.add(c);
		}
		
		String curr_class = null;
		HashMap<String, HashSet<String>> classesToPaths = new HashMap<String, HashSet<String>>();
		
		while ((line = usageReader.readLine()) != null) {
			String c = line.trim();
			if (c.contentEquals("")) {
				continue;
			}
			// Not sure why we have these - looks like a bug
			if (c.startsWith(".")) {
				continue;
			}
			if (classes.contains(c)) {
				//System.out.println(curr_class + " " + classesToPaths.get(curr_class));
				curr_class = c;
				if (!classesToPaths.containsKey(curr_class)) {
					classesToPaths.put(curr_class, new HashSet<String>());
				}
				continue;
			}
			if (curr_class != null && c.startsWith(curr_class) && c.length() > curr_class.length()) {
				HashSet<String> paths = classesToPaths.get(curr_class);
				// strip off the class itself and then store the rest as a 'path'
				String path = c.substring(curr_class.length() + 1);
				paths.add(path);
			}
		}
		
		HashMap<String, Integer> class2class = new HashMap<String, Integer>();
		for (String c : classesToPaths.keySet()) {
			Set<String> paths = classesToPaths.get(c);
			TreeMap<Integer, List<String>> count2class = new TreeMap<Integer, List<String>>(Collections.reverseOrder());
			
			for (String c2 : classesToPaths.keySet()) {
				Set<String> c1Paths = new HashSet<String>(paths);
				if (c2.equals(c)) {
					continue;
				}
				Set<String> c2Paths = classesToPaths.get(c2);
				c1Paths.retainAll(c2Paths);
				if (c1Paths.size() <= 1) {
					continue;
				}
				if (!count2class.containsKey(c1Paths.size())) {
					count2class.put(c1Paths.size(), new LinkedList<String>());
				}
				count2class.get(c1Paths.size()).add(c2);
			}
			if (count2class.keySet().iterator().hasNext()) {
				Integer count = count2class.keySet().iterator().next();
				
				System.out.println(c + " " + count + " " + count2class.get(count));
			}
		}
		
	}

}
