package com.ibm.wala.cast.python.test;

import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;
import com.ibm.wala.util.graph.labeled.LabeledGraph;
import com.ibm.wala.util.graph.labeled.SlowSparseNumberedLabeledGraph;
import com.ibm.wala.util.graph.traverse.BFSPathFinder;
import com.ibm.wala.util.graph.traverse.DFSAllPathsFinder;
import com.ibm.wala.util.graph.traverse.DFSPathFinder;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.function.Predicate;

public class PathPatternSummarizer {


    public static String getPath(Graph<String> g, String root, HashMap<String, String> nodeToTurtles) {
        List<String> l = new LinkedList<>();
        l.add(root);
        System.out.println("Root:" + root);
        accumulateSuccessors(g, root, l);

        StringBuffer buf = new StringBuffer();
        for (String node : l) {
            assert nodeToTurtles.containsKey(node);
            buf.append(nodeToTurtles.get(node)).append("->");
        }
        return buf.toString();
    }

    private static void accumulateSuccessors(Graph<String> g, String source, List<String> l) {
        if (g.getSuccNodeCount(source) == 0) {
            return;
        }
        assert g.getSuccNodeCount(source) == 1 :  g;
        String target = g.getSuccNodes(source).next();
        l.add(target);
        accumulateSuccessors(g, target, l);
    }

    public static void addToSummary(Graph<String> g, String x, String y, int arg, HashMap<String, Integer> edgeCounts) {
        if (!g.containsNode(x)) {
            g.addNode(x);
        }
        String y_fixed = y;
        if (arg == 0) {
            String[] arr = y.split("[.]");
            y_fixed = arr[arr.length - 1];
        }
        if (!g.containsNode(y_fixed)) {
            g.addNode(y_fixed);
        }

        g.addEdge(x, y_fixed);
        String e = x + "->" + y;
        if (!edgeCounts.containsKey(e)) {
            edgeCounts.put(e, 1);
        } else {
            edgeCounts.put(e, edgeCounts.get(e) + 1);
        }
    }


    public static void main(String... args) throws Exception {

        TreeMap<String, Integer> pathsToCount = new TreeMap<String, Integer>();

        BufferedReader lines = new BufferedReader(new FileReader(args[0]));
        String line;
        Graph<String> paths = SlowSparseNumberedGraph.make();

        HashMap<String, String> nodeToTurtles = new HashMap<>();
        String key = null;

        Graph<String> summaryGraph = SlowSparseNumberedGraph.make();
        HashMap<String, Integer> edgeToCounts = new HashMap<>();

        while ((line = lines.readLine()) != null) {
            StringTokenizer toks = new StringTokenizer(line, "|");
            if (toks.countTokens() == 1) {
                continue;
            }
            String source = toks.nextToken().trim();
            String read = toks.nextToken().trim();
            String fit = toks.nextToken().trim();
            String curr_key = source + "|" + read + "|" + fit;
            if (curr_key.equals("g|read|fit")) {
                continue;
            }
            if (key == null) {
                key = curr_key;
            }
            String x = toks.nextToken().trim();
            String y = toks.nextToken().trim();

            toks.nextToken();
            toks.nextToken();

            String x_turtle_path = toks.nextToken().trim();
            String y_turtle_path = toks.nextToken().trim();
            String str = toks.nextToken().trim();
            int arg = 1;            // set arg to 1 so if its not 0, it gets parsed correctly by the summary
            if (!str.equals("")) {
                arg = Integer.parseInt(str);
            }

            addToSummary(summaryGraph, x_turtle_path, y_turtle_path, arg, edgeToCounts);


            if (!curr_key.equals(key)) {
                //System.out.println("processing key:" + key);
                // read all paths from paths -- TBD
                processGraph(pathsToCount, paths, nodeToTurtles, key);
                key = curr_key;

                System.out.println("Finished:" + source);
                paths = SlowSparseNumberedGraph.make();
                nodeToTurtles = new HashMap<>();
            }

            nodeToTurtles.put(x, x_turtle_path);

            if (!y_turtle_path.equals("")) {
                nodeToTurtles.put(y, y_turtle_path);
            }
            if (!paths.containsNode(x)) {
                paths.addNode(x);
            }
            if (!paths.containsNode(y) && !y.equals("")) {
                paths.addNode(y);
            }

            if (!y.equals("")) {
                paths.addEdge(x, y);
            }
        }

        processGraph(pathsToCount, paths, nodeToTurtles, key);
        for (String path : pathsToCount.keySet()) {
            System.out.println("Path:" + path + ";" + pathsToCount.get(path));
        }
        /*
        System.out.println(summaryGraph);
        for (HashMap.Entry<String, Integer> entry : edgeToCounts.entrySet()) {
            System.out.println(entry.getKey() + " " + entry.getValue());
        } */
    }

    private static void processGraph(TreeMap<String, Integer> pathsToCount, Graph<String> paths, HashMap<String, String> nodeToTurtles, String key) {
        StringTokenizer tokenizer = new StringTokenizer(key, "|");
        tokenizer.nextToken();
        String oldRoot = tokenizer.nextToken();
        String oldFit = tokenizer.nextToken();
        //DFSAllPathsFinder<String> pathFinder = new DFSAllPathsFinder<String>(paths, oldRoot, oldFit::equals);
        BFSPathFinder<String> pathFinder = new BFSPathFinder<String>(paths, oldRoot, oldFit::equals);
        List<String> path;
        //System.out.println(nodeToTurtles.size());
        //System.out.println("GRAPH:" + paths);
        while ((path = pathFinder.find()) != null) {
            StringBuffer buf = new StringBuffer();
            for (int i = path.size() - 1; i >=0; i--) {
                String e = path.get(i);
                assert (nodeToTurtles.containsKey(e));
                String curr_turtle = nodeToTurtles.get(e).replaceAll("\"","");
                buf.append(curr_turtle).append('|');
            }
            String p = buf.toString();
            System.out.println("One path:" + p);
            if (!pathsToCount.containsKey(p)) {
                pathsToCount.put(p, 1);
            } else {
                pathsToCount.put(p, pathsToCount.get(p) + 1);
            }
        }
    }
}
