package com.ibm.wala.cast.python.test;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.sparql.resultset.ResultsFormat;
import org.apache.jena.tdb.TDBFactory;

public class QueryTrainGraph {

    public static void main(String[] args) throws Exception {
        Dataset dataset = TDBFactory.createDataset("/Users/kavithasrinivas/ibmcode/code_knowledge_graph/data/static_analysis_data");
        String sparql = null;
        if (args[0].equals("check_edge")) {
            sparql = new String(Files.readAllBytes(Paths.get("../static_analysis_queries/debug_data_flow.sparql")));
            sparql = sparql.replaceAll("[?]source", args[1]);
            sparql = sparql.replaceAll("[?]target", args[2]);

        } else if (args[0].equals("start_constructor")) {
            sparql = new String(Files.readAllBytes(Paths.get("../static_analysis_queries/constructor_successors.sparql")));
            String rep = "\"" + args[1] + "\"";
            sparql = sparql.replaceAll("[?]m", rep);
        } else if (args[0].equals("start_source")) {
            sparql = new String(Files.readAllBytes(Paths.get("../static_analysis_queries/generic_successors.sparql")));
            sparql = sparql.replaceAll("[?]x", args[1]);
        } else if (args[0].equals("end_source")) {
            sparql = new String(Files.readAllBytes(Paths.get("../static_analysis_queries/generic_predecessors.sparql")));
            sparql = sparql.replaceAll("[?]x", args[1]);
        }

        QueryExecution exec = QueryExecutionFactory.create(sparql, dataset);
        ResultSet results = exec.execSelect();

        ResultSetFormatter.output(
                System.out,
                results,
                ResultsFormat.FMT_RS_CSV);

    }

}
