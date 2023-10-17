package util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;

public class TypeInferenceOnKG {

	private static Set<String> filterTypes(Map<String, Set<String>> class2superclasses, Set<String> classes) {
		Set<String> result = new HashSet<String>();
		System.out.println("In filtered types");
		System.out.println(classes);
		for (String c : classes) {
			Set<String> superclasses = class2superclasses.get(c);
			boolean add = true;
			for (String s : superclasses) {
				if (classes.contains(s)) {
					add = false;
				}
			}
			if (add) {
				result.add(c);
			}
		}
		return result;
	}

	public static void main(String[] args) {

		// label refers to a turtle object - i.e. a specific call in the code.
		// method is any method that gets invoked on any object A that gets returned by
		// the code.
		// cls is any class that method is part of.
		// ensure that every method that is invoked on A is contained by the same class.
		// Do this by intersecting the set of classes
		// across methods

		try {

			getReturnType(args);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private static void getReturnType(String[] args) throws IOException {
		RDFConnection conn = RDFConnectionFactory.connect(args[0]);
		String query = new String(Files.readAllBytes(Paths.get(args[1])));
		HashMap<String, Set<String>> method2classes = new HashMap<String, Set<String>>();
		HashMap<String, Set<String>> cls2superclasses = new HashMap<String, Set<String>>();

		conn.querySelect(query, (qs) -> {
			String method = qs.getLiteral("m").toString();
			String m = qs.getResource("method").toString();
			String cls = qs.getResource("cls").toString();
			String superCl = qs.getResource("super").toString();
			System.out.println(m + " method " + method + " cls " + cls);

			if (!(cls2superclasses.containsKey(cls))) {
				cls2superclasses.put(cls, new HashSet<String>());
			}
			cls2superclasses.get(cls).add(superCl);

			if (!method2classes.containsKey(method)) {
				method2classes.put(method, new HashSet<String>());
			}
			Set<String> classes = method2classes.get(method);
			classes.add(cls);

		});


		Set<String> cls = null;
		for (HashMap.Entry<String, Set<String>> entry : method2classes.entrySet()) {
			Set<String> c = entry.getValue();
			Set<String> filteredClasses = filterTypes(cls2superclasses, c);

			if (cls == null) {
				cls = filteredClasses;
			} else {
				cls.retainAll(filteredClasses);
			}
		}
		
		for (String c : cls) {
			System.out.println("\t" + c);
		}
	}
}
