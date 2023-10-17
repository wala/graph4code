package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.json.JSONArray;
import org.json.JSONObject;

import com.ibm.wala.codeBreaker.turtle.PythonTurtleLibraryAnalysisEngine;
import com.ibm.wala.codeBreaker.turtleServer.TurtleWrapper;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;

public class RunTurtleAnalysis {

	static HashMap<String, Integer> errorCategories = new HashMap<String, Integer>();
	static int total_turtles = 0;

	public static void main(String... args) throws IOException, CancelException, WalaException {
		BufferedReader reader = new BufferedReader(new FileReader(System.getProperty("testFileList")));
		String line;
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(2); 
		while ((line = reader.readLine()) != null) {
			StringTokenizer tokenizer = new StringTokenizer(line, ",");
			String file = tokenizer.nextToken();
			String repo = tokenizer.nextToken();
			 final Future handler = executor.submit(new Callable() {
				@Override
				public Object call() throws Exception {
					 new RunTurtleAnalysis(new File(file), repo).test(); 
					 return null;
				} 
			 });
			 try {
				try { 
					handler.get(10000, TimeUnit.MILLISECONDS);
				} catch (TimeoutException e) {
					handler.cancel(true);
				}
			} catch (InterruptedException | ExecutionException | CancellationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		executor.shutdown();
		reader.close();
	}	


	private final File testFile;
	private final String repo;
	
	public RunTurtleAnalysis(File testFile, String repo) {
		this.testFile = testFile;
		this.repo = repo;
		
	}

	public JSONObject test() throws IOException, CancelException, WalaException {
		try {
			System.err.println("starting " + testFile);
			JSONArray turtles = TurtleWrapper.analyzeRequest(testFile, () -> new PythonTurtleLibraryAnalysisEngine(), false);
			JSONObject obj = new JSONObject();
			obj.put("filename", testFile.getName());
			obj.put("repoPath", repo);
			obj.put("python_version", System.getProperty("python_version"));
			obj.put("turtle_analysis", turtles);

			assert turtles != null && turtles.length() > 0 : testFile + " has no turtles";

			if (System.getProperty("outputDir") != null && !turtles.isEmpty()) {
				String name = testFile.getName();
				name = System.getProperty("outputDir") + File.separator + name.substring(0, name.lastIndexOf('.'));
				System.err.println("writing to " + name);
				FileWriter json_file = new FileWriter(name);
				obj.write(json_file);
				json_file.close();
			}
			total_turtles += turtles.length();
			System.err.println(turtles);
			System.err.println("success: " + testFile + " has " + turtles.length() + " turtles");
			return obj;
		} catch (Throwable e) {
			System.err.println("failure: " + testFile);
			String key = e.toString().split(":")[0];
			if (!errorCategories.containsKey(key)) {
				errorCategories.put(key, 1);
			} else {
				errorCategories.put(key, errorCategories.get(key) + 1);
			}
			System.err.println(e.toString());
			throw e;
		} finally {
			System.err.println("ERROR CATEGORIES");
			System.err.println(errorCategories);
			System.err.println("Total number of turtles:" + total_turtles);
		}
	}


}