package com.ibm.wala.codeBreaker.turtleServer;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.jena.query.Dataset;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.python.client.PythonAnalysisEngine;
import com.ibm.wala.cast.python.ipa.summaries.TurtleSummary;
import com.ibm.wala.cast.python.loader.PythonLoaderFactory;
import com.ibm.wala.cast.python.util.PythonInterpreter;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.SourceFileModule;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.EdgeType;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.TurtlePath;
import com.ibm.wala.codeBreaker.turtleServer.TurtleWrapper.Entries;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;
import com.ibm.wala.util.io.TemporaryFile;

import spark.Request;
import util.ExpressionGenerator;
import util.RunTurtleAnalysis;

import static spark.Spark.post;

public class TurtleWrapper {

	static {
		try {
			Class<?> j3 = Class.forName("com.ibm.wala.cast.python.loader.Python3ReflectiveFieldLoaderFactory");
			PythonAnalysisEngine.setLoaderFactory((Class<? extends PythonLoaderFactory>) j3);
			Class<?> i3 = Class.forName("com.ibm.wala.cast.python.util.Python3Interpreter");
			PythonInterpreter.setInterpreter((PythonInterpreter)i3.newInstance());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
			try {
				Class<?> j2 = Class.forName("com.ibm.wala.cast.python.loader.Python2LoaderFactory");			
				PythonAnalysisEngine.setLoaderFactory((Class<? extends PythonLoaderFactory>) j2);
				Class<?> i2 = Class.forName("com.ibm.wala.cast.python.util.Python2Interpreter");
				PythonInterpreter.setInterpreter((PythonInterpreter)i2.newInstance());
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e1) {
				assert false : e.getMessage() + ", then " + e1.getMessage();
			}
		}
	}

	private static final Set<byte[]> covered = HashSetFactory.make();
	
	public static final class FilteringTurtleAnalysisEngine extends PythonTurtleAnalysisEngine {
		private final Filter filter;
		private final Entries entries;

		public FilteringTurtleAnalysisEngine(Filter filter, Entries entries) {
			this.filter = filter;
			this.entries = entries;
		}

		@Override
		protected Iterable<Entrypoint> makeDefaultEntrypoints(AnalysisScope scope, IClassHierarchy cha) {
			Set<Entrypoint> es = HashSetFactory.make();
		
			if (entries.equals(Entries.BOTH) || entries.equals(Entries.WHOLE)) {
				super.makeDefaultEntrypoints(scope, cha).forEach((e) -> {
					es.add(e);
				});
			}
			
			if (entries.equals(Entries.BOTH) || entries.equals(Entries.LIBRARY)) {
				TurtleSummary.turtleEntryPoints(cha).forEach((e) -> {
					es.add(e);
				});
			}
			
			if (! filter.equals(Filter.NONE)) {
				try {
					MessageDigest x = MessageDigest.getInstance("SHA-1");
					Set<byte[]> hashes = filter.equals(Filter.GLOBAL)? covered: HashSetFactory.make();
					Set<Entrypoint> es2 = HashSetFactory.make();
					for(Entrypoint e : es) {
						if (e.getMethod() instanceof AstMethod) {
							SourceBuffer sb = new SourceBuffer(((AstMethod)e.getMethod()).debugInfo().getCodeBodyPosition());
							byte[] raw = x.digest(sb.toString().getBytes());
							if (hashes.add(raw)) {
								es2.add(e);
							}
						}
					}
					return es2;
				} catch (NoSuchAlgorithmException | IOException e) {
					assert false : e;
				}
			}
			
			return es;
		}
	}

	public enum Entries {
		WHOLE, LIBRARY, BOTH
	}
	
	public enum Filter {
		GLOBAL, FILE, NONE
	}

	
    public static void main(String[] args) {
		post("/index", (request, response) -> handleExpressionsRequest(request));
		post("/analyze_code", (request, response) -> handleAnalysisRequest(request));
		post("/batchIndex", (request, response) -> handleBatchedExpressionsRequest(request));
        /* post("/analyze_code/:entries/:filter", (req, res) -> {
        	Filter filter = Filter.valueOf(req.params(":filter").toUpperCase());
        	Entries entries = Entries.valueOf(req.params(":entries").toUpperCase());
         	return analyzeRequest(getFile(req), () -> { 
        		return new FilteringTurtleAnalysisEngine(filter, entries);
        	}, true);
        }); */
    }

	private static File getFile(String req) throws IOException {
		File F = File.createTempFile("turtle", ".py");
		// F.deleteOnExit();
		TemporaryFile.stringToFile(F, req.replaceAll("^[%]", "#"));
		return F;
	}

	public static JSONArray handleExpressionsRequest(Request req) throws IOException, WalaException, CancelException {
		JSONObject analyzed = handleAnalysisRequest(req);
		System.out.println("finished analysis");
		JSONTokener tokener = new JSONTokener(new StringReader(req.body()));
		JSONObject obj = new JSONObject(tokener);
		String indexName = obj.getString("indexName");
		String fileName = obj.getString("repo");
		return ExpressionGenerator.index(analyzed, fileName, indexName);
	}

	public static JSONObject handleAnalysisRequest(Request req) throws IOException, WalaException, CancelException {
		JSONTokener tokener = new JSONTokener(new StringReader(req.body()));
		JSONObject obj = new JSONObject(tokener);
		return new RunTurtleAnalysis(getFile(obj.getString("source")), obj.getString("repo")).test();
	}

	public static String handleBatchedExpressionsRequest(Request req) throws IOException, WalaException, CancelException {
		JSONTokener tokener = new JSONTokener(new StringReader(req.body()));
		JSONObject obj = new JSONObject(tokener);
		JSONArray arr = obj.getJSONArray("sources");
		for (Object o : arr) {
			JSONObject x  = ((JSONObject) o);
			JSONObject analyzed = new RunTurtleAnalysis(getFile(x.getString("source")), x.getString("repo")).test();
			String indexName = x.getString("indexName");
			String fileName = x.getString("repo");
			ExpressionGenerator.index(analyzed, fileName, indexName);
		}
		return "Success";
	}

    public static JSONArray analyzeRequest(File F, Supplier<PythonAnalysisEngine<NumberedLabeledGraph<TurtlePath,
			EdgeType>>> makeEngine, boolean detail)
    		throws IOException, MalformedURLException, CancelException, CallGraphBuilderCancelException, WalaException {
    	try {

    		PythonAnalysisEngine<NumberedLabeledGraph<TurtlePath, EdgeType>> E = makeEngine.get();

    		CallGraphBuilder<? super InstanceKey> builder = doAnalysis(F, E);

    		JSONArray json = PythonTurtleAnalysisEngine.graphToJSON(E.performAnalysis((SSAPropagationCallGraphBuilder)builder), detail);
 
    		return json;
    	} catch (IOException | CancelException e) {
    		throw e;
    	}
    }

	public static CallGraphBuilder<? super InstanceKey> doAnalysis(File F,
			PythonAnalysisEngine<NumberedLabeledGraph<TurtlePath, EdgeType>> E)
			throws MalformedURLException, IOException, CallGraphBuilderCancelException {
		E.setModuleFiles(Collections.singleton(new SourceFileModule(F, F.getAbsolutePath(), null)));

		CallGraphBuilder<? super InstanceKey> builder = E.defaultCallGraphBuilder();
		
		builder.makeCallGraph(E.getOptions(), new NullProgressMonitor());
		return builder;
	}
    
    public static Dataset analyzeRequest(String repoFileName, File F, Supplier<PythonAnalysisEngine<NumberedLabeledGraph<TurtlePath, EdgeType>>> makeEngine)
    		throws IOException, MalformedURLException, CancelException, CallGraphBuilderCancelException, WalaException {
    	try {


    		PythonAnalysisEngine<NumberedLabeledGraph<TurtlePath, EdgeType>> E = makeEngine.get();

    		CallGraphBuilder<? super InstanceKey> builder = doAnalysis(F, E);

    		NumberedLabeledGraph<TurtlePath, EdgeType> analysis = E.performAnalysis((SSAPropagationCallGraphBuilder)builder);

       		if (PythonTurtleAnalysisEngine.DEBUG) System.err.println(PythonTurtleAnalysisEngine.graphToJSON(analysis, false).toString(2));

    		Dataset ds = PythonTurtleAnalysisEngine.RDFFromJSON(analysis, repoFileName, false);
    		return ds;
    	} catch (IOException | CancelException e) {
    		throw e;
    	}
    }
}
