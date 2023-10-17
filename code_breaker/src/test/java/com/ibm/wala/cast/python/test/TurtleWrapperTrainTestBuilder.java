package com.ibm.wala.cast.python.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.ibm.wala.codeBreaker.turtle.PythonTurtleLibraryAnalysisEngine;
import com.ibm.wala.codeBreaker.turtleServer.TurtleWrapper;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;

public class TurtleWrapperTrainTestBuilder {

    private static double test_fraction = 0.10;
    private static int CROSS_VALIDATION = 10;

    public static void main(String[] args) throws IOException, CancelException, WalaException {
        File[] tests = new File(args[0]).listFiles();
        List<File> l = Arrays.asList(tests);
        int total_turtles = 0;
        Map<File, Integer> filesToTurtleCounts = new HashMap<File, Integer>();

        for (File testFile : l) {
            JSONArray turtles = null;
            try {
                turtles = TurtleWrapper.analyzeRequest(testFile, () -> new PythonTurtleLibraryAnalysisEngine(), true);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            if (turtles == null || turtles.length() == 0) {
                continue;
            }
            boolean hasEdges = false;
            for (Object obj : turtles) {
                JSONObject jsonObj = (JSONObject) obj;
                JSONObject edges = (JSONObject) jsonObj.get("edges");
                if (edges.has("CONTROL") || edges.has("DATA")) {
                    hasEdges = true;
                }
            }
            if (!hasEdges) {
                continue;
            }

            assert !filesToTurtleCounts.containsKey(testFile);
            filesToTurtleCounts.put(testFile, turtles.length());
            total_turtles += turtles.length();
        }


        System.out.println("Finished turtle run");
        Collections.shuffle(l);
        List<File> li = l;
        assert filesToTurtleCounts != null;
        int i = 0;
        // Shuffle once and split into a set of test files, trying to perform a k fold cross validation
        // Since we can't just split the dataframe into 10 sets because we want to optimize the total number of turtles chosen
        // basically return
        while (!li.isEmpty()) {
            li = createTestSet(li, total_turtles, filesToTurtleCounts, i);
            i++;
        }

    }

    private static List<File> createTestSet(List<File> l, int total_turtles, Map<File, Integer> filesToTurtleCounts, int dataset_for_test) throws IOException {
        List<File> li = new LinkedList<>(l);
        assert filesToTurtleCounts != null;
        int num_test_turtles = (int) Math.round(test_fraction * total_turtles);
        BufferedWriter writer = Files.newBufferedWriter(Paths.get("/tmp/test" + dataset_for_test));
        int total_test_turtles = 0;

        List<File> test_files = new LinkedList<>();
        for (File f : l) {
            if (!filesToTurtleCounts.containsKey(f)) {
                li.remove(f);
                continue;
            }
            int turtle_count = filesToTurtleCounts.get(f);
            if (total_test_turtles + turtle_count <= num_test_turtles) {
                test_files.add(f);
                total_test_turtles += turtle_count;
                li.remove(f);
            }
        }

        System.out.println("Total number of turtles:" + total_turtles);
        System.out.println("Expected number of turtles for test:" + num_test_turtles);
        System.out.println("Got turtles:" + total_test_turtles);
        System.out.println("Number of total files:" + filesToTurtleCounts.size());
        System.out.println("Number of test files:" + test_files.size());
        System.out.println("Files chosen:");

        for (File f : test_files) {
            writer.write(f.getName() + "\n");
        }
        writer.close();
        return li;
    }
}
