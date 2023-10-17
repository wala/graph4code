package com.ibm.wala.codeBreaker.turtle;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.cha.IClassHierarchy;

public class PythonTurtleLibraryAnalysisEngine extends PythonTurtleAnalysisEngine {

	@Override
	protected void addBypassLogic(IClassHierarchy cha, AnalysisOptions options) {
		super.addBypassLogic(cha, options);
		new TurtleSummary(this).analyzeWithTurtles(options);
	}

	@Override
	protected Iterable<Entrypoint> makeDefaultEntrypoints(AnalysisScope scope, IClassHierarchy cha) {
		return TurtleSummary.turtleEntryPoints(cha);
	}


}
