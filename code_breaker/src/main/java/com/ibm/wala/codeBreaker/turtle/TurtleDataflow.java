package com.ibm.wala.codeBreaker.turtle;

import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.EdgeType;
import com.ibm.wala.codeBreaker.turtle.PythonTurtleAnalysisEngine.TurtlePath;
import com.ibm.wala.dataflow.graph.IKilldallFramework;
import com.ibm.wala.dataflow.graph.ITransferFunctionProvider;
import com.ibm.wala.fixpoint.IVariable;
import com.ibm.wala.util.graph.labeled.NumberedLabeledGraph;

public interface TurtleDataflow<V extends IVariable<V>> extends IKilldallFramework<TurtlePath, V> {
	
	@Override
	public NumberedLabeledGraph<TurtlePath, EdgeType> getFlowGraph();

	@Override
	public ITransferFunctionProvider<TurtlePath, V> getTransferFunctionProvider();

}
