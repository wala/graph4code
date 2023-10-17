package com.ibm.wala.codeBreaker.turtle;

import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;

import com.ibm.wala.analysis.reflection.JavaTypeContext;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.cast.ir.ssa.AstInstructionFactory;
import com.ibm.wala.cast.loader.AstFunctionClass;
import com.ibm.wala.cast.loader.DynamicCallSiteReference;
import com.ibm.wala.cast.python.ipa.summaries.PythonSummarizedFunction;
import com.ibm.wala.cast.python.ipa.summaries.PythonSummary;
import com.ibm.wala.cast.python.ir.PythonLanguage;
import com.ibm.wala.cast.python.loader.PythonLoader;
import com.ibm.wala.cast.python.ssa.PythonInvokeInstruction;
import com.ibm.wala.cast.python.types.PythonTypes;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.classLoader.SyntheticMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.ClassTargetSelector;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.MethodTargetSelector;
import com.ibm.wala.ipa.callgraph.impl.AbstractRootMethod;
import com.ibm.wala.ipa.callgraph.impl.FakeRootMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.BypassSyntheticClassLoader;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.core.util.strings.Atom;

public class TurtleSummary {
	public static final TypeReference turtleClassRef = TypeReference.findOrCreate(PythonTypes.pythonLoader, "Lturtle");
	public static final MethodReference turtleMethodRef = MethodReference.findOrCreate(turtleClassRef, Atom.findOrCreateUnicodeAtom("turtle"), AstMethodReference.fnDesc);
	public static final FieldReference turtleFieldRef = FieldReference.findOrCreate(turtleClassRef, Atom.findOrCreateUnicodeAtom("turtle"), PythonTypes.Root);

	public static final MethodReference turtleCallbackMethodRef = MethodReference.findOrCreate(turtleClassRef, Atom.findOrCreateUnicodeAtom("callback"), AstMethodReference.fnDesc);

	private final PythonTurtleAnalysisEngine E;
	
	private class TurtleField implements IField {
		private final Atom name;
		
		private TurtleField(Atom name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return "turtle_field_" + name;
		}
		
		@Override
		public IClass getDeclaringClass() {
			return turtleClass;
		}

		@Override
		public Atom getName() {
			return getReference().getName();
		}

		@Override
		public Collection<Annotation> getAnnotations() {
			return Collections.emptySet();
		}

		@Override
		public IClassHierarchy getClassHierarchy() {
			return E.getClassHierarchy();
		}

		@Override
		public TypeReference getFieldTypeReference() {
			return getReference().getFieldType();
		}

		@Override
		public FieldReference getReference() {
			return FieldReference.findOrCreate(turtleClassRef, name, turtleClassRef);
		}

		@Override
		public boolean isFinal() {
			return false;
		}

		@Override
		public boolean isPrivate() {
			return false;
		}

		@Override
		public boolean isProtected() {
			return false;
		}

		@Override
		public boolean isPublic() {
			return true;
		}

		@Override
		public boolean isStatic() {
			return false;
		}

		@Override
		public boolean isVolatile() {
			return false;
		}
	};
	
	 abstract class BaseTurtleClass implements IClass {

		 @Override
		 public IMethod getMethod(Selector arg0) {
			 assert false;
			 return null;
		 }

		@Override
		public IClassHierarchy getClassHierarchy() {
			return E.getClassHierarchy();
		}

		@Override
		public IClassLoader getClassLoader() {
			return E.getClassHierarchy().getLoader(PythonTypes.pythonLoader);
		}

		@Override
		public boolean isInterface() {
			return false;
		}

		@Override
		public boolean isAbstract() {
			return false;
		}

		@Override
		public boolean isPublic() {
			return true;
		}

		@Override
		public boolean isPrivate() {
			return false;
		}

		@Override
		public boolean isSynthetic() {
			return true;
		}

		@Override
		public int getModifiers() throws UnsupportedOperationException {
			return 0;
		}

		@Override
		public Collection<? extends IClass> getDirectInterfaces() {
			return Collections.emptySet();
		}

		@Override
		public Collection<IClass> getAllImplementedInterfaces() {
			return Collections.emptySet();
		}

		private Map<Atom,TurtleField> fields =HashMapFactory.make();
		
		@Override
		public IField getField(Atom name) {
			if (! fields.containsKey(name)) {
				fields.put(name, new TurtleField(name));
			}
			return fields.get(name);
		}

		@Override
		public IField getField(Atom name, TypeName type) {
			return getField(name);
		}

		@Override
		public String getSourceFileName() throws NoSuchElementException {
			throw new NoSuchElementException();
		}

		@Override
		public Reader getSource() throws NoSuchElementException {
			throw new NoSuchElementException();
		}

		@Override
		public IMethod getClassInitializer() {
			return null;
		}

		@Override
		public boolean isArrayClass() {
			return false;
		}

		@Override
		public Collection<? extends IMethod> getDeclaredMethods() {
			return Collections.emptySet();
		}

		@Override
		public Collection<IField> getAllInstanceFields() {
			return Collections.emptySet();
		}

		@Override
		public Collection<IField> getAllStaticFields() {
			return Collections.emptySet();
		}

		@Override
		public Collection<IField> getAllFields() {
			return Collections.emptySet();
		}

		@Override
		public Collection<? extends IMethod> getAllMethods() {
			return Collections.emptySet();
		}

		@Override
		public Collection<IField> getDeclaredInstanceFields() {
			return Collections.emptySet();
		}

		@Override
		public Collection<IField> getDeclaredStaticFields() {
			return Collections.emptySet();
		}

		@Override
		public TypeName getName() {
			return getReference().getName();
		}

		@Override
		public boolean isReferenceType() {
			return true;
		}

		@Override
		public Collection<Annotation> getAnnotations() {
			return Collections.emptySet();
		}	
		
		@Override
		public String toString() {
			return "turtle_class:" + getReference().getName().toString();
		}
	};
	
	private class RootTurtleClass extends BaseTurtleClass {
		
		@Override
		public IClass getSuperclass() {
			return E.getClassHierarchy().lookupClass(PythonTypes.Root);
		}

		@Override
		public TypeReference getReference() {
			return turtleClassRef;
		}
	}
	
	private IClass turtleClass = new RootTurtleClass();

	private class TurtleClass extends BaseTurtleClass {
		private final TypeReference ref;
		
		private TurtleClass(TypeReference ref) {
			this.ref = ref;
		}
		
		@Override
		public IClass getSuperclass() {
			return turtleClass;
		}

		@Override
		public TypeReference getReference() {
			return ref;
		}
	}
	
	private Map<TypeReference,TurtleClass> turtleClasses = HashMapFactory.make();
	
	public TurtleClass ensureTurtleClass(TypeReference ref) {
		if (! turtleClasses.containsKey(ref)) {
			TurtleClass cls = new TurtleClass(ref);
			turtleClasses.put(ref, cls);
			BypassSyntheticClassLoader ldr = (BypassSyntheticClassLoader) E.getClassHierarchy().getLoader(E.getClassHierarchy().getScope().getSyntheticLoader());
			ldr.registerClass(ref.getName(), cls);
		}
			
		return turtleClasses.get(ref);
	}
	
//	private final SyntheticMethod code;

	public TurtleSummary(PythonTurtleAnalysisEngine pythonTurtleAnalysisEngine) {
		this.E = pythonTurtleAnalysisEngine;
		
		BypassSyntheticClassLoader ldr = (BypassSyntheticClassLoader) pythonTurtleAnalysisEngine.getClassHierarchy().getLoader(pythonTurtleAnalysisEngine.getClassHierarchy().getScope().getSyntheticLoader());
		ldr.registerClass(turtleClassRef.getName(), turtleClass);
	}
	
	public boolean isTurtleClass(IClass cls) {
		return cls instanceof BaseTurtleClass;
	}
	
	public boolean isTurtleMethod(IMethod m) {
		return isTurtleClass(m.getDeclaringClass());
	}
	
	private class PythonMethodTurtleTargetSelector implements MethodTargetSelector {
		private final Map<Set<TypeReference>, IMethod> codeBodies = HashMapFactory.make();
		
		private final MethodTargetSelector base;

		public PythonMethodTurtleTargetSelector(MethodTargetSelector base) {
			this.base = base;
		}

		private IMethod getCode(Set<TypeReference> thrown) {
			if (codeBodies.containsKey(thrown)) {
				return codeBodies.get(thrown);
			} else {
				PythonSummary x = new PythonSummary(turtleMethodRef, 1);
				x.addStatement(PythonLanguage.Python.instructionFactory().NewInstruction(0, 10, NewSiteReference.make(0, turtleClassRef)));
				x.addStatement(new PythonInvokeInstruction(2, 11, 12, CallSiteReference.make(2, turtleCallbackMethodRef, Dispatch.VIRTUAL), new int[] {2}, new Pair[0]));
				x.addStatement(PythonLanguage.Python.instructionFactory().ReturnInstruction(3, 10, false));
			
				int v = 13;
				int i = 3;
				for(TypeReference thrownType : thrown) {
					NewSiteReference thrownObj = NewSiteReference.make(i, thrownType);
					x.addStatement(PythonLanguage.Python.instructionFactory().NewInstruction(i++, v, thrownObj));
					x.addStatement(PythonLanguage.Python.instructionFactory().ThrowInstruction(i++, v++));
				}
				
				PythonSummarizedFunction body = new PythonSummarizedFunction(turtleMethodRef, x, turtleClass);
				codeBodies.put(thrown, body);
				
				return body;
			}
		}
		
		@Override
		public IMethod getCalleeTarget(CGNode caller, CallSiteReference site, IClass receiver) {
			if (site.getDeclaredTarget().equals(turtleCallbackMethodRef)) {
				if (caller.getClassHierarchy().isSubclassOf(receiver, caller.getClassHierarchy().lookupClass(PythonTypes.CodeBody))) {
					return receiver.getMethod(AstMethodReference.fnSelector);
				} else {
					return null;
				}
			} else if (receiver == null? site.getDeclaredTarget().getDeclaringClass().equals(turtleClassRef): isTurtleClass(receiver)) {
				
				Set<TypeReference> exceptions = HashSetFactory.make();
				SSACFG cfg = caller.getIR().getControlFlowGraph();
				for (ISSABasicBlock bb : caller.getIR().getBasicBlocksForCall(site)) {
					cfg.getExceptionalSuccessors(bb).forEach(eb -> {
						eb.getCaughtExceptionTypes().forEachRemaining(et -> { 
							exceptions.add(et);
						});
					});
				}
				
				return getCode(exceptions);
			} else {			
			    return base.getCalleeTarget(caller, site, receiver);
			}
		}
	}
	
	private TypeReference deriveChildTurtle(CGNode caller) {
		Context c = caller.getContext();
		TypeReference turtle =  
		    c.isA(JavaTypeContext.class)?
			((TypeAbstraction)c.get(ContextKey.RECEIVER)).getTypeReference():
			caller.getMethod().getDeclaringClass().getReference();
		String str = turtle.getName().toUnicodeString();
		// System.err.println("alloc " + str);
		if (str.contains("#")) {
			String tip = str.substring(str.lastIndexOf('#')+1);
			String base = str.substring(0, str.indexOf('#'));
			TypeName nm = 
				TypeName.findOrCreate(
					(base.contains("/" + tip + "/")?
						base.substring(0, base.indexOf("/" + tip + "/")):
						base) + "/" + tip);
			return TypeReference.findOrCreate(PythonTypes.pythonLoader, nm);
		} else {
			return turtle;
		}
	}
	
	private class PythonClassTurtleTargetSelector implements ClassTargetSelector {
		private final ClassTargetSelector base;
		
		private IClass findTurtle(CGNode caller) {
			if (caller.getMethod() instanceof FakeRootMethod) {
				return turtleClass;
			} else {
				return ensureTurtleClass(deriveChildTurtle(caller));
			}
		}	
		
		private PythonClassTurtleTargetSelector(ClassTargetSelector base) {
			this.base = base;
		}
		
		@Override
		public IClass getAllocatedTarget(CGNode caller, NewSiteReference site) {
			System.err.println("checking turtle for " + site);
			if (site.getDeclaredType().equals(turtleClassRef)) {
				IClass cls = findTurtle(caller);
				System.err.println("found " + cls);
				return cls;
			} else {
				return base.getAllocatedTarget(caller, site);
			}
		}
		
	}
	
	public static Collection<Entrypoint> turtleEntryPoints(IClassHierarchy cha) {
		Set<Entrypoint> stuff = HashSetFactory.make();
		IClass cb = cha.lookupClass(PythonTypes.CodeBody);
		IClass cc = cha.lookupClass(PythonTypes.comprehension);
		IClass lc = cha.lookupClass(PythonTypes.lambda);
		
		Consumer<IClass> eps = new Consumer<IClass>() {
			private final Map<FieldReference, Integer> objects = HashMapFactory.make();

			public Entrypoint turtleEntryPoint(IMethod fun) {
				return new Entrypoint(fun) {
					@Override
					public SSAAbstractInvokeInstruction addCall(AbstractRootMethod m) {
					    int paramValues[];
					    paramValues = new int[getNumberOfParameters()];
					    for (int j = 0; j < paramValues.length; j++) {
					    	AstInstructionFactory insts = PythonLanguage.Python.instructionFactory();
					    	if (j == 0 && getMethod().getDeclaringClass().getName().toString().contains("/")) {
					    		int v = m.nextLocal++;
					    		paramValues[j] = v;
					    		if (getMethod().getDeclaringClass() instanceof PythonLoader.DynamicMethodBody) {
									int obj = ensureObject(m, insts);
									int idx = m.statements.size();
									String method = getMethod().getDeclaringClass().getName().toString();
									String field = method.substring(method.lastIndexOf('/')+1);
									FieldReference f = FieldReference.findOrCreate(PythonTypes.Root, Atom.findOrCreateUnicodeAtom(field), PythonTypes.Root);
									m.statements.add(insts.GetInstruction(idx, v, obj, f));
						    		} else {
								    FieldReference global = FieldReference.findOrCreate(PythonTypes.Root, Atom.findOrCreateUnicodeAtom("global " + getMethod().getDeclaringClass().getName().toString().substring(1)), PythonTypes.Root);
								    m.statements.add(insts.GlobalRead(m.statements.size(), v, global));
						    		}
					    	} else {
					    		paramValues[j] = makeArgument(m, j);
					    	}
					      if (paramValues[j] == -1) {
					    	  
					        // there was a problem
					        return null;
					      }
					      /*
					      TypeReference x[] = getParameterTypes(j);
					      if (x.length == 1 && x[0].equals(turtleClassRef)) {
					    	  m.statements.add(PythonLanguage.Python.instructionFactory().PutInstruction(m.statements.size(), paramValues[j], paramValues[j], turtleFieldRef));
					      }
					      */
					    }
					    
					    int pc = m.statements.size();
					    PythonInvokeInstruction call = 
					    	new PythonInvokeInstruction(pc, m.nextLocal++, m.nextLocal++, new DynamicCallSiteReference(PythonTypes.CodeBody, pc), paramValues, new Pair[0]);
					    
					    m.statements.add(call);
					    
						return call;
					}
					
					@SuppressWarnings("unchecked")
					private int ensureObject(AbstractRootMethod m, AstInstructionFactory insts) {
						FieldReference global = FieldReference.findOrCreate(PythonTypes.Root, Atom.findOrCreateUnicodeAtom("global " + getMethod().getDeclaringClass().getName().toString().substring(1, getMethod().getDeclaringClass().getName().toString().lastIndexOf('/'))), PythonTypes.Root);
						if (! objects.containsKey(global)) { 
							int idx = m.statements.size();
							int cls = m.nextLocal++;
							int obj = m.nextLocal++;
							m.statements.add(insts.GlobalRead(m.statements.size(), cls, global));
							idx = m.statements.size();
							m.statements.add(new PythonInvokeInstruction(idx, obj, m.nextLocal++, new DynamicCallSiteReference(PythonTypes.CodeBody, idx), new int[] {cls}, new Pair[0]));
							objects.put(global, obj);
						}
						return objects.get(global);
					}

					@Override
					public TypeReference[] getParameterTypes(int i) {
						return new TypeReference[] { i==0? fun.getDeclaringClass().getReference() : turtleClassRef };
					}

					@Override
					public int getNumberOfParameters() {
						return fun.getNumberOfParameters();
					}
				};
			}

			@Override
			public void accept(IClass cls) {
				if (cha.isSubclassOf(cls, cb) && 
					    !cha.isSubclassOf(cls, cc) &&
					    !cha.isSubclassOf(cls, lc) &&
					    cls instanceof AstFunctionClass) {
						stuff.add(turtleEntryPoint(((AstFunctionClass)cls).getCodeBody()));
				}				
			}
			
		};
		
		cha.forEach(eps);
		return stuff;
	}
	
	public void analyzeWithTurtles(AnalysisOptions options) {
		options.setSelector(new PythonMethodTurtleTargetSelector(options.getMethodTargetSelector()));
		options.setSelector(new PythonClassTurtleTargetSelector(options.getClassTargetSelector()));
	}
}
