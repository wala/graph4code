package com.ibm.wala.cast.lsp.codeBreaker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.tdb.TDBFactory;

public class QueryStaticAnalysisStore {

    private String storeLocation = System.getProperty("RDF_STORE_LOCATION", "data/static_analysis_data");
    private String standardQueryLocation = System.getProperty("STATIC_QUERY_LOCATION", "../static_analysis_queries/generic_successors2.sparql");
    protected Dataset dataset;
    private RDFConnection connection;
    private ParameterizedSparqlString standardQuery;


    public QueryStaticAnalysisStore() {
        this.dataset = TDBFactory.createDataset(storeLocation);
        this.connection = RDFConnectionFactory.connect(dataset);
        try {
            String content = new String(Files.readAllBytes(Paths.get(standardQueryLocation)));
            this.standardQuery = new ParameterizedSparqlString();
            this.standardQuery.setCommandText(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ResultSet getSuggestion(String currentMethod) {
        System.err.println("LINE:" + currentMethod);
        this.standardQuery.setLiteral("m", currentMethod);
        // System.err.println(this.standardQuery.toString());
        Query query = this.standardQuery.asQuery();
        return connection.query(query).execSelect();
    }

    public static void main(String[] args) {
        QueryStaticAnalysisStore store = new QueryStaticAnalysisStore();
        ResultSetFormatter.out(store.getSuggestion("fit.PCA.decomposition.sklearn"));
    }

}
