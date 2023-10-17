package com.ibm.wala.codeBreaker.turtle;

import java.io.IOException;

import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.EdgeType;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.TurtlePath;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;

public class Driver extends com.ibm.wala.cast.python.driver.Driver {

	public static void main(String... args) throws IllegalArgumentException, IOException, CancelException {
		
		PythonTurtleAnalysisEngine E = new PythonTurtleLibraryAnalysisEngine();
		
		NumberedLabeledGraph<TurtlePath, EdgeType> result = new Driver().runit(E, args);
		
		System.err.println(PythonTurtleAnalysisEngine.graphToJSON(result, true));
		
		result.forEach(tp -> {
			System.err.println(tp.path());
			result.getSuccNodes(tp).forEachRemaining(otp -> {
				System.err.println(" --> " + otp.path());				
			});
		});
	}

}
