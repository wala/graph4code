package com.ibm.wala.cast.python.test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.Pair;

public class CrossValidatePaths2 {

    public static final class QueryHolder {
        String countSuccessors;
        String countNoSuccessors;
        String testGraphQuery;
        List<String> pairs;
        int pathLength;
    }


    public static void main(String[] args) {

        List<QueryHolder> queries = prepareQueryHolder();

        Dataset ds = RDFDataMgr.loadDataset(args[0]);

        File testFilesDirectory = new File(args[1]);
        String[] test_files = testFilesDirectory.list();
        for (String test : test_files) {
            if (test.startsWith("test_graph_sample")) {
                Model m = RDFDataMgr.loadModel(testFilesDirectory + "/" + test);
                ds.addNamedModel("http://" + test, m);
            }
        }

        for (QueryHolder queryHolder : queries) {
            Map<String, Integer> counts = computeTrainGraphCounts(ds, queryHolder);
            Set<String> countsWithoutSuccessors = createCountsWithoutSuccessors(ds, queryHolder);
            evaluateTestGraphs(ds, queryHolder, counts, countsWithoutSuccessors);
        }

    }

    private static List<QueryHolder> prepareQueryHolder() {
        List<QueryHolder> queries = new LinkedList<>();
        QueryHolder queryHolder = new QueryHolder();
        // one edge context
        queryHolder.countSuccessors = "select (count(distinct ?p) as ?c) ?p1 where { " +
                "  ?s1 <http://edge/dataflow> ?s2 ; <http://path> ?p1 . " +
                "  ?s2 <http://path> ?p . } group by ?p1";
        queryHolder.countNoSuccessors = "select ?p1 where { " +
                "             ?s1 <http://path> ?p1 .  } ";
        queryHolder.testGraphQuery = "select ?g ?p1 where { " +
                "  graph ?g { ?s1 <http://path> ?p1 . } } ";
        List<String> pairs = new ArrayList<String>();
        pairs.add("p1");
        queryHolder.pairs = pairs;
        queryHolder.pathLength = 1;
        queries.add(queryHolder);

        // two edge context
        queryHolder = new QueryHolder();
        queryHolder.countSuccessors = "select (count(distinct ?p) as ?c) ?p1 ?p2 where { " +
                "  ?s1 <http://edge/dataflow> ?s2 ; <http://path> ?p1 . " +
                "  ?s2 <http://edge/dataflow> ?s3 ; <http://path> ?p2 . " +
                "  ?s3 <http://path> ?p . } group by ?p1 ?p2";
        queryHolder.countNoSuccessors = "select ?p1 ?p2 where { " +
                "  ?s1 <http://edge/dataflow> ?s2 ; <http://path> ?p1 . " +
                "             ?s2 <http://path> ?p2 .  } ";
        queryHolder.testGraphQuery = "select ?g ?p1 ?p2 where { " +
                "  graph ?g { ?s1 <http://edge/dataflow> ?s2 ; <http://path> ?p1 . " +
                "             ?s2 <http://path> ?p2 . } } ";
        pairs = new ArrayList<String>();
        pairs.add("p1");
        pairs.add("p2");
        queryHolder.pairs = pairs;
        queryHolder.pathLength = 2;
        queries.add(queryHolder);

        // three edge context
        queryHolder = new QueryHolder();
        queryHolder.countSuccessors = "select (count(distinct ?p) as ?c) ?p1 ?p2 ?p3 where { " +
                "  ?s1 <http://edge/dataflow> ?s2 ; <http://path> ?p1 . " +
                "  ?s2 <http://edge/dataflow> ?s3 ; <http://path> ?p2 . " +
                "  ?s3 <http://edge/dataflow> ?s4 ; <http://path> ?p3 . " +
                "  ?s4 <http://path> ?p . } group by ?p1 ?p2 ?p3";
        queryHolder.countNoSuccessors = "select ?p1 ?p2 ?p3 where { " +
                "  ?s1 <http://edge/dataflow> ?s2 ; <http://path> ?p1 . " +
                "  ?s2 <http://edge/dataflow> ?s3 ; <http://path> ?p2 . " +
                "             ?s3 <http://path> ?p3 .  } ";
        queryHolder.testGraphQuery = "select ?g ?p1 ?p2 ?p3 where { " +
                "  graph ?g { ?s1 <http://edge/dataflow> ?s2 ; <http://path> ?p1 . " +
                "?s2 <http://edge/dataflow> ?s3 ; <http://path> ?p2 . " +
                "             ?s3 <http://path> ?p3 . } } ";
        pairs = new ArrayList<String>();
        pairs.add("p1");
        pairs.add("p2");
        pairs.add("p3");
        queryHolder.pairs = pairs;
        queryHolder.pathLength = 3;
        queries.add(queryHolder);

        System.out.println("num queries:" + queries.size());

        return queries;
    }

    private static String createKey(List<String> keys, QuerySolution qs) {
        StringBuffer buffer = new StringBuffer();
        for (String k : keys) {
            buffer.append(qs.get(k).asLiteral().getString()).append("-");
        }
        return buffer.toString();
    }

    private static void evaluateTestGraphs(Dataset ds, QueryHolder queryHolder, Map<String, Integer> counts,
                                           Set<String> countsWithoutSuccessors) {
        QueryExecution exec = QueryExecutionFactory.create(queryHolder.testGraphQuery, ds);
        ResultSet results = exec.execSelect();

        Map<Pair<String, Integer>, Integer> bins = HashMapFactory.make();
        List<Integer> allSuccs = new ArrayList<Integer>();
        int count_positives = 0;
        int count_total = 0;
        int count_positives_terminal = 0;
        while (results.hasNext()) {
            QuerySolution qs = results.next();
            count_total += 1;
            String countKey = createKey(queryHolder.pairs, qs);
            // System.out.println(countKey);
            if (!counts.containsKey(countKey)) {
                if (countsWithoutSuccessors.contains(countKey)) {
                    count_positives_terminal += 1;
                }
                continue;
            }
            count_positives += 1;
            int c = counts.get(countKey);
            allSuccs.add(c);
            Pair<String, Integer> binKey = Pair.make(qs.get("g").toString(), c);
            if (bins.containsKey(binKey)) {
                bins.put(binKey, bins.get(binKey) + 1);
            } else {
                bins.put(binKey, 1);
            }
        }

        //System.err.println(counts);
        //System.err.println(bins);

        System.out.println("Paths found at length " + queryHolder.pathLength + ": " + count_total);
        System.out.println("Percentage of paths found at length " + queryHolder.pathLength + " with successors:" + count_positives / (double) count_total);
        System.out.println("Percentage of paths found at length "  + queryHolder.pathLength + " with no successors:" + count_positives_terminal / (double) count_total);
        calculateDescriptiveStats(allSuccs);
    }

    public static void calculateDescriptiveStats(List<Integer> arr) {
        int sum = 0;

        for (int i : arr) {
            sum += i;
        }
        double mean = sum / arr.size();
        System.out.println("mean successors:" + mean);
        System.out.println("min successors:" + Collections.min(arr));
        System.out.println("max successors:" + Collections.max(arr));


        double[] sumSquares = new double[arr.size()];

        for (int i = 0; i < arr.size(); i++) {
            sumSquares[i] = Math.pow(arr.get(0) - mean, 2);
        }

        double sum_sq = 0.0;
        for (int i = 0; i < sumSquares.length; i++) {
            sum_sq += sumSquares[i];
        }

        double std = Math.sqrt(sum_sq / sumSquares.length);
        System.out.println("std:" + std);

    }

    private static Set<String> createCountsWithoutSuccessors(Dataset ds, QueryHolder queryHolder) {
        Set<String> countsWithoutSuccessors = new HashSet<>();

        QueryExecution exec = QueryExecutionFactory.create(queryHolder.countNoSuccessors, ds);
        ResultSet results = exec.execSelect();

        while (results.hasNext()) {
            QuerySolution qs = results.next();
            String key = createKey(queryHolder.pairs, qs);

            if (!countsWithoutSuccessors.contains(key)) {
                countsWithoutSuccessors.add(key);
            } else {
                countsWithoutSuccessors.add(key);
            }
        }
        return countsWithoutSuccessors;
    }

    private static Map<String, Integer> computeTrainGraphCounts(Dataset ds, QueryHolder queryHolder) {

        QueryExecution exec = QueryExecutionFactory.create(queryHolder.countSuccessors, ds);
        ResultSet results = exec.execSelect();

        Map<String, Integer> counts = HashMapFactory.make();

        while (results.hasNext()) {
            QuerySolution qs = results.next();
            String key = createKey(queryHolder.pairs, qs);
            System.out.println(key);

            int count = qs.get("c").asLiteral().getInt();
            if (!counts.containsKey(key)) {
                counts.put(key, count);
            } else {
                counts.put(key, count + counts.get(key));
            }
        }
        return counts;
    }

}

/*"select (count(distinct ?p2) as ?n) ?c where {" + 
" { select (count(distinct ?p) as ?c) ?p2 where { " + 
"     graph ?g { ?s1 <http://edge/dataflow> ?s2 ; <http://path> ?p1 . " + 
"                ?s2 <http://path> ?p2 . } " + 
"     ?s3 <http://edge/dataflow> ?s4 ; <http://path> ?p1 . " + 
"     ?s4 <http://path> ?p2 ; <http://edge/dataflow> ?s . " + 
"     ?s <http://path> ?p  " + 
"} group by ?p2 } } group by ?c";*/
