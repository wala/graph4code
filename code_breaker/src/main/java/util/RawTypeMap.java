package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;

public class RawTypeMap {

	private static int prefix = "http://purl.org/twc/graph4code/python/".length();
	
	private final Map<String, Map<String, Set<String>>> turtleClasses = HashMapFactory.make();
		
	private int count = 0;
	private int lineCount = 0;
	
	private void process(InputStreamReader is) throws IOException {
		String line;
		BufferedReader lines = new BufferedReader(is);
		while ((line = lines.readLine()) != null) {
			lineCount++;
			StringTokenizer toks = new StringTokenizer(line, "\t");
			String turtle = toks.nextToken();
			if (! turtleClasses.containsKey(turtle)) {
				turtleClasses.put(turtle, HashMapFactory.make());
			}
			
			String method = toks.nextToken();

			Map<String,Set<String>> methodMap = turtleClasses.get(turtle);
			if (!methodMap.containsKey(method)) {
				methodMap.put(method, HashSetFactory.make());
			}
			
			String couldBeCalled = toks.nextToken();
			
			if (methodMap.get(method).add(couldBeCalled)) {
				count++;
			}
			
			if (count % 1000000 == 0 || lineCount % 1000000 == 0) {
				System.out.println("so far " + count + " from " + lineCount);
			}

		}
	}

	public static void main(String[] args) throws IOException {
		new RawTypeMap().process(new InputStreamReader(System.in));
	}

}
