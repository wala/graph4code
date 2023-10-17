package util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.json.JSONArray;
import org.json.JSONObject;

import com.ibm.wala.cast.ipa.callgraph.CAstCallGraphUtil;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.EdgeType;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.TurtlePath;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleLibraryAnalysisEngine;
import com.ibm.wala.codeBreaker.turtleServer.TurtleWrapper;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointsToSetVariable;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.types.MemberReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;
import com.ibm.wala.util.graph.traverse.DFS;

public class RunTurtleSingleAnalysis {
	private boolean DEBUG = PythonTurtleAnalysisEngine.DEBUG;
	private boolean DUMP_IR = true;
	
	private final TarArchiveOutputStream archive;
	
	protected final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2); 

	protected void rec(File F, String repo, String path) {
		if (F.isDirectory()) {
			for (File ff : F.listFiles())  {
				rec(ff, repo + File.separator + F.getName(), path + File.separator + ff.getName());
			}
		} else if (F.getName().endsWith(".py")) {
			processPythonFile(F, repo, path); 
		}
	}

	protected void processPythonFile(File F, String repo, String path) {
		try {
			 final Future<Void> handler = executor.submit(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						RunTurtleSingleAnalysis engine = make(F, repo, path);
						if (System.getProperty("outputDir") != null) {
							engine.test();
						}
						if (System.getProperty("quadFile") != null) {
							engine.test2();
						}
						return null;
					} 
				 });
				 try {
					try { 
						handler.get(60000, TimeUnit.MILLISECONDS);
					} catch (TimeoutException e) {
						handler.cancel(true);
					}
				} catch (InterruptedException | ExecutionException | CancellationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		} catch (Exception | Error e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String... args) throws IOException, CancelException, WalaException {
		RunTurtleSingleAnalysis analyzer = new RunTurtleSingleAnalysis();
		analyzer.rec(new File(args[0]), args[1], args[2]);
		analyzer.executor.shutdown();
	}

	protected final File testFile;
	private final String repo;
	private final String repoPath;
	
	protected RunTurtleSingleAnalysis() throws FileNotFoundException, IOException {
		this(null, null, null);
	}

	public RunTurtleSingleAnalysis make(File testFile, String repo, String repoPath) throws FileNotFoundException, IOException {
		return new RunTurtleSingleAnalysis(testFile, repo, repoPath);
	}

	public RunTurtleSingleAnalysis(File testFile, String repo, String repoPath) throws FileNotFoundException, IOException {
		this.testFile = testFile;		
		this.repo = repo;
		this.repoPath = repoPath;
		this.archive = 
			System.getProperty("outputArchive") != null?
				new TarArchiveOutputStream(new BZip2CompressorOutputStream(new FileOutputStream(System.getProperty("outputArchive")))):
				null;
	}

	protected void process(NumberedLabeledGraph<TurtlePath, EdgeType> result, PropagationCallGraphBuilder builder) {
		if (DEBUG || DUMP_IR) {
			CAstCallGraphUtil.AVOID_DUMP = false;
			CAstCallGraphUtil.dumpCG((SSAContextInterpreter) builder.getContextInterpreter(), builder.getPointerAnalysis(), builder.getCallGraph());
			System.err.println(builder.getCallGraph());
		}
		
		Set<TurtlePath> fitCalls = xCalls(result, "fit");
		//System.err.println(fitCalls);

		Set<TurtlePath> readCalls = xCalls(result, "read_csv");
		//System.err.println(readCalls);
		
		readCalls.forEach(read -> {
			Set<TurtlePath> flow = DFS.getReachableNodes(result, Collections.singleton(read));
			flow.retainAll(fitCalls);
			System.err.println(flow);
		});
	}

	private Set<TurtlePath> xCalls(NumberedLabeledGraph<TurtlePath, EdgeType> result, String f) {
		Set<TurtlePath> fitCalls = HashSetFactory.make();
		result.forEach(t -> { 
			List<MemberReference> p = t.path();
			MemberReference elt = p.get(p.size()-1);
			String name = elt.getName().toString();
			if (elt instanceof MethodReference && f.equals(name)) {
				fitCalls.add(t);
			}
		});
		return fitCalls;
	}

	public void test2() throws IOException, CancelException, WalaException, NoSuchAlgorithmException {
		if (DEBUG) System.err.println("starting " + testFile);
		String path = "http://github/" + repo + "/" + repoPath;
		Dataset ds = TurtleWrapper.analyzeRequest(path, testFile, 
				() -> new PythonTurtleLibraryAnalysisEngine() {
					@Override
					public NumberedLabeledGraph<TurtlePath, EdgeType> performAnalysis(PropagationCallGraphBuilder builder)
							throws CancelException {
						
						if (PythonTurtleAnalysisEngine.DEBUG) {
							CAstCallGraphUtil.AVOID_DUMP = false;
							CAstCallGraphUtil.dumpCG((SSAContextInterpreter) builder.getContextInterpreter(), builder.getPointerAnalysis(), builder.getCallGraph());
							System.err.println(builder.getCallGraph());
						}
						
						NumberedLabeledGraph<TurtlePath, EdgeType> result = super.performAnalysis(builder);
						RunTurtleSingleAnalysis.this.process(result, builder);
						return result;
					}
				});
		Lang language = Lang.NQ;
		if (System.getProperty("turtle") != null) {
			language = Lang.TURTLE;
		}
		if (System.getProperty("quadFile") != null) {
			try (FileOutputStream fo = new FileOutputStream(System.getProperty("quadFile") + File.separator + md5Name() + ".nq.bz2")) {
				try (BZip2CompressorOutputStream bs = new BZip2CompressorOutputStream(fo)) {
					RDFDataMgr.write(bs, ds, language) ;		
					bs.flush();	
					fo.flush();
				}
			}
		} else {
			RDFDataMgr.write(System.out, ds, language);
		}
	}
	
	public void test() throws NoSuchAlgorithmException, IOException, CancelException, WalaException {
		try {
			System.err.println("starting " + testFile);
			JSONArray turtles = TurtleWrapper.analyzeRequest(testFile, 
					() -> new PythonTurtleLibraryAnalysisEngine() {
						@Override
						public NumberedLabeledGraph<TurtlePath, EdgeType> performAnalysis(PropagationCallGraphBuilder builder)
								throws CancelException {
							NumberedLabeledGraph<TurtlePath, EdgeType> result = super.performAnalysis(builder);
							RunTurtleSingleAnalysis.this.process(result, builder);
							
							CAstCallGraphUtil.AVOID_DUMP = false;
							CAstCallGraphUtil.dumpCG((SSAContextInterpreter) builder.getContextInterpreter(), builder.getPointerAnalysis(), builder.getCallGraph());
							System.err.println(builder.getCallGraph());
							
							if (Boolean.getBoolean("FullGraph")) {
							CallGraph CG = builder.getCallGraph();
							Graph<PointsToSetVariable> ptr_G = builder.getPropagationSystem().getFlowGraphIncludingImplicitConstraints();

							try {
								String name;
								String jsonName;
								jsonName = md5Name();
								name = System.getProperty("outputDir") + File.separator + jsonName + ".full.json.bz2";
								writeFullGraph(name, builder.getPointerKeyFactory(), builder.getPointerAnalysis(), CG, ptr_G );
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
								assert false : e;
							}
							}
							
							return result;
						}
					}, true);
			JSONObject obj = new JSONObject();
			obj.put("filename", testFile.getAbsolutePath());
			obj.put("repo", repo);
			obj.put("repoPath", repoPath);
			obj.put("python_version", System.getProperty("python_version"));
			obj.put("turtle_analysis", turtles);

			assert turtles != null && turtles.length() > 0 : testFile + " has no turtles";

			if (archive != null || System.getProperty("outputDir") != null) {
				String name;
				String jsonName = md5Name();
				name = System.getProperty("outputDir") + File.separator + jsonName + ".json.bz2";
				if (DEBUG) System.err.println("writing to " + name);
				
				File f = new File(name);
				try (FileOutputStream fo = new FileOutputStream(f)) {
					try (BZip2CompressorOutputStream bs = new BZip2CompressorOutputStream(fo)) {
						try (OutputStreamWriter w = new OutputStreamWriter(bs, "UTF-8")) {
							obj.write(w, 2, 0);
							w.flush();
							bs.flush();
							fo.flush();
						}
					}
				}
			}
			
			//System.err.println(turtles);
			System.err.println("success: " + testFile + " has " + turtles.length() + " turtles");
		} catch (Throwable e) {
			System.err.println(e.toString());
			if (e.toString().contains("front end errors:")) {
				System.err.println("front end errors for " + testFile);
			}
			System.err.println("failure: " + testFile);
			throw e;
		} 
	}

	private String md5Name() throws IOException, NoSuchAlgorithmException {
		byte[] content = java.nio.file.Files.readAllBytes(testFile.toPath());
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(content);
		byte[] digits = md.digest();
		String jsonName= "";
		for(byte x : digits) {
			jsonName += ("0x" + Integer.toHexString(Byte.toUnsignedInt(x)));
		}
		return jsonName;
	}


}