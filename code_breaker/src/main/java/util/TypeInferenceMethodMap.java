package util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import com.ibm.wala.util.collections.HashMapFactory;

public class TypeInferenceMethodMap {

	public static void main(String... args) throws FileNotFoundException, IOException {
		String line;
		
		Map<String, String> functionMap = HashMapFactory.make();
		
		String mapFile = args[0];
		BufferedReader mapLines = new BufferedReader(new FileReader(mapFile));
		while ((line = mapLines.readLine()) !=  null) {
			StringTokenizer lt = new StringTokenizer(line);
			String from = lt.nextToken();
			if (lt.hasMoreTokens()) {
				String to = lt.nextToken();
				functionMap.put(from,  to);
			}
		}
		mapLines.close();
		
		String inferenceFile = args[1];
		BZip2CompressorInputStream inferenceIn = new BZip2CompressorInputStream(new FileInputStream(inferenceFile));
		BufferedReader inferenceLines = new BufferedReader(new InputStreamReader(inferenceIn));
		while ((line = inferenceLines.readLine()) !=  null) {
			StringTokenizer lt = new StringTokenizer(line);
			String function = lt.nextToken();
			if (functionMap.containsKey(function)) {
				function = functionMap.get(function);
			}
			
			String count = lt.nextToken();
			String type = lt.nextToken();

			System.out.println(function + " " + count + " " + type);
		}
	}
}
