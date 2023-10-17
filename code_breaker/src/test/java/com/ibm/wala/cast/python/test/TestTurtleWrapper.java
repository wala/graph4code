package com.ibm.wala.cast.python.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.ibm.wala.codeBreaker.turtle.PythonTurtleLibraryAnalysisEngine;
import com.ibm.wala.codeBreaker.turtleServer.TurtleWrapper;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;


@RunWith(Parameterized.class)
public class TestTurtleWrapper {

	static HashMap<String, Integer> errorCategories = new HashMap<String, Integer>();
	static HashMap<String, String> fileToGitRepo = new HashMap<>();
	static int total_turtles = 0;

	@Parameterized.Parameters
	public static Collection<Object[]> tests() {

		try {
			if (System.getProperty("testFileList") != null) {
				BufferedReader reader = new BufferedReader(new FileReader(System.getProperty("testFileList")));
				String line;
				List<File> tests = new LinkedList<>();
				while ((line = reader.readLine()) != null) {
					StringTokenizer tokenizer = new StringTokenizer(line, ",");
					String file = tokenizer.nextToken();
					String repo = tokenizer.nextToken();
					tests.add(new File(file));
					fileToGitRepo.put(file, repo);
				}

				Object[][] x = new Object[tests.size()][];
				int i = 0;
				for (File test : tests) {
					x[i++] = new Object[]{test};
				}
				return Arrays.asList(x);
			} else {
				return Collections.emptyList();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return Collections.emptyList();
		}
	}


	private final File testFile;

	public TestTurtleWrapper(File testFile, String repo) {
		this.testFile = testFile;
		
	}

	@Test(timeout=30000)
	public void test() throws IOException, CancelException, WalaException {
		try {
			System.err.println("starting " + testFile);
			JSONArray turtles = TurtleWrapper.analyzeRequest(testFile, () -> new PythonTurtleLibraryAnalysisEngine(), false);
			JSONObject obj = new JSONObject();
			obj.put("filename", testFile.getName());
			obj.put("repoPath", fileToGitRepo.get(testFile.getName()));
			obj.put("python_version", System.getProperty("python_version"));
			obj.put("turtle_analysis", turtles);

			assert turtles != null && turtles.length() > 0 : testFile + " has no turtles";

			if (System.getProperty("outputDir") != null && !turtles.isEmpty()) {
				String name = testFile.getName();
				name = System.getProperty("outputDir") + File.pathSeparator + name.substring(0, name.lastIndexOf('.'));
				FileWriter json_file = new FileWriter(name);
				obj.write(json_file);
			}
			total_turtles += turtles.length();
			System.err.println(turtles);
			System.err.println("success: " + testFile + " has " + turtles.length() + " turtles");
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