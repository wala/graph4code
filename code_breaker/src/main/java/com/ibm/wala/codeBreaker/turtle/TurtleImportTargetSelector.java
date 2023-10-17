package com.ibm.wala.codeBreaker.turtle;

import java.util.Map;

import com.ibm.wala.cast.python.ipa.summaries.PythonSummarizedFunction;
import com.ibm.wala.cast.python.ipa.summaries.PythonSummary;
import com.ibm.wala.cast.python.ir.PythonLanguage;
import com.ibm.wala.cast.python.types.PythonTypes;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.MethodTargetSelector;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.core.util.strings.Atom;

public class TurtleImportTargetSelector implements MethodTargetSelector {

	private static final Atom imp0rt = Atom.findOrCreateUnicodeAtom("import");

	private final Map<TypeReference, IMethod> turtles = HashMapFactory.make();
	
	private final MethodTargetSelector base;
	
	private final TurtleSummary turtleSummary;
	
	TurtleImportTargetSelector(TurtleSummary turtles, MethodTargetSelector base) {
		this.base = base;
		this.turtleSummary = turtles;
	}
	
	@Override
	public IMethod getCalleeTarget(CGNode caller, CallSiteReference site, IClass receiver) {
		MethodReference target = site.getDeclaredTarget();
		if (target.getName().equals(imp0rt) && target.getNumberOfParameters()==0) {
			TypeReference type = target.getReturnType();
			if (! turtles.containsKey(type)) {
				String rootTurtleTypeName = "L" + type.getName().toUnicodeString().substring(1);
				TypeReference rootTurtleType = TypeReference.findOrCreate(PythonTypes.pythonLoader, rootTurtleTypeName);
				IClass C = turtleSummary.ensureTurtleClass(rootTurtleType);
				MethodReference turtleMethodRef = MethodReference.findOrCreate(rootTurtleType, imp0rt, target.getDescriptor());
				PythonSummary S = new PythonSummary(turtleMethodRef, 0);
				NewSiteReference ns = new NewSiteReference(0, TurtleSummary.turtleClassRef);
				S.addStatement(PythonLanguage.Python.instructionFactory().NewInstruction(0, 1, ns));
				S.addStatement(PythonLanguage.Python.instructionFactory().ReturnInstruction(1, 1, false));
				turtles.put(type, new PythonSummarizedFunction(turtleMethodRef, S, C));
			}
			
			return turtles.get(type);
		} else {
			return base.getCalleeTarget(caller, site, receiver);
		}
	}

}
