package util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleLibraryAnalysisEngine;
import com.ibm.wala.codeBreaker.turtleServer.TurtleWrapper;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;

public class MeasureGraphsCoverage extends RunTurtleSingleAnalysis {
	
	@Override
	public void test2() throws IOException, CancelException, WalaException {
		// do nothing
	}

	@Override
	public void test() throws NoSuchAlgorithmException, IOException, CancelException, WalaException {
		TurtleWrapper.analyzeRequest(testFile, 
			() -> new PythonTurtleLibraryAnalysisEngine() {
				@Override
				public NumberedLabeledGraph<TurtlePath, EdgeType> performAnalysis(PropagationCallGraphBuilder builder)
						throws CancelException {
					try {
						NumberedLabeledGraph<TurtlePath, EdgeType> result = super.performAnalysis(builder);
						String buf = new String(java.nio.file.Files.readAllBytes(testFile.toPath()));
						int len = buf.length();
						List<String> lines = java.nio.file.Files.readAllLines(testFile.toPath());
						
						StringBuffer sb1 = new StringBuffer(buf.replaceAll("[^\\n]", " "));
						result.forEach(t -> {
							try {
								if (t.statement().getKind() == Kind.NORMAL) {
									NormalStatement ns = (NormalStatement)t.statement();
									IMethod m = ns.getNode().getMethod();
									if (m instanceof AstMethod) {
										Position p = ((AstMethod)m).debugInfo().getInstructionPosition(ns.getInstructionIndex());
										if (p != null && p.getFirstOffset() >= 0) {
											String text = new SourceBuffer(p).toString();
											if (p.getFirstLine() == p.getLastLine()) {
												System.err.println("<"+(text=lines.get(p.getFirstLine()-1).substring(p.getFirstCol(), Math.min(p.getLastCol(), lines.get(p.getFirstLine()-1).length())))+">");
											}

											sb1.replace(p.getFirstOffset(), p.getLastOffset(), text);
										}
									}
								}
							} catch (IOException e) { 
								assert false : e;
							}
						});

						StringBuffer sb2 = new StringBuffer(buf.replaceAll("[^\\n]", " "));
						for(int i = 0; i < len; i++) sb2.append(' ');
						builder.getCallGraph().forEach(cgn -> {
							IMethod m = cgn.getMethod();
							if (m instanceof AstMethod) {
								cgn.getIR().iterateAllInstructions().forEachRemaining(inst -> { 
									try {
										if (inst.iIndex() >= 0) {
										Position p = ((AstMethod)m).debugInfo().getInstructionPosition(inst.iIndex());
										if (p != null && p.getFirstOffset() >= 0) {
											String text;
											System.err.println(inst + " (" + p.getFirstOffset() + "," + p.getLastOffset() + ") " + p.getFirstLine() + ":" + p.getFirstCol() + " " + p.getLastLine() + ":" + p.getLastCol() + " |" + (text=new SourceBuffer(p).toString() + "|"));
											if (p.getFirstLine() == p.getLastLine()) {
												System.err.println("<"+(text=lines.get(p.getFirstLine()-1).substring(p.getFirstCol(), Math.min(p.getLastCol(), lines.get(p.getFirstLine()-1).length())))+">");
											}

											sb2.replace(p.getFirstOffset(), p.getLastOffset(), text);
										}
										}
									} catch (IOException e) { 
									assert false : e;
									}
								});
							}
						});
						
						try (FileWriter fw = new FileWriter(testFile.getName() + "turtles")) {
							fw.write(sb1.toString());
						}
						
						try (FileWriter fw = new FileWriter(testFile.getName() + "code")) {
							fw.write(sb2.toString());
						}
												
						return result;
					} catch (IOException e) {
						assert false : e;
						return null;
					}
					
				}
			}, true);
	}

	public RunTurtleSingleAnalysis make(File testFile, String repo, String repoPath) throws FileNotFoundException, IOException {
		return new MeasureGraphsCoverage(testFile, repo, repoPath);
	}

	public MeasureGraphsCoverage() throws FileNotFoundException, IOException {
		super();
	}

	public MeasureGraphsCoverage(File testFile, String repo, String repoPath)
			throws FileNotFoundException, IOException {
		super(testFile, repo, repoPath);
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
		MeasureGraphsCoverage analyzer = new MeasureGraphsCoverage();
		analyzer.rec(new File(args[0]), args[1], args[2]);
		analyzer.executor.shutdown();
	}

}
