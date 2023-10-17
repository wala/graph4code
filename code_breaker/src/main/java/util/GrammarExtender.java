package util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;

public class GrammarExtender {

	private static Pattern forwardRule = Pattern.compile("([^<>-]*)->([^<>-]*)(-[^<>-]*)*");

	private static Pattern backwardRule = Pattern.compile("([^<>-]*)<-([^<>-]*)(-[^<>-]*)*");

	private static String shorten(String name) {
		return name.substring(name.lastIndexOf('.')+1);
	}
	
	public static void main(String... args) throws IOException {
		Set<String> in = runit(forwardRule, args[2], args[1]);
		in.retainAll(runit(backwardRule, args[2], args[1]));
		System.err.println(in);
		
		in = runit(forwardRule, args[0], args[1]);
		System.err.println(in);
		in.retainAll(runit(backwardRule, args[0], args[1]));
		System.err.println(in);
	}
	
	private static Set<String> runit(Pattern rule, String... args) throws IOException {
		Map<String,Set<String>> shorts = HashMapFactory.make();
		Map<String,Set<String>> edges = HashMapFactory.make();
		
		Files.lines(Paths.get(new File(args[1]).toURI()))
			.filter(l -> rule.matcher(l).matches())
			.forEach(r -> { 
				Matcher m = rule.matcher(r);
				m.matches();
				String lhs = m.group(1);
				if (! edges.containsKey(lhs)) {
					edges.put(lhs, HashSetFactory.make());
					if (! shorts.containsKey(shorten(lhs))) {
						shorts.put(shorten(lhs), HashSetFactory.make());
					}
					shorts.get(shorten(lhs)).add(lhs);
				}
				for(int i = 2; i < m.groupCount(); i++) {
					String rhs = m.group(i);
					if (! shorts.containsKey(shorten(rhs))) {
						shorts.put(shorten(rhs), HashSetFactory.make());
					}
					shorts.get(shorten(rhs)).add(rhs);
					edges.get(lhs).add(rhs);
				}
			}
		);
		
		List<String> currentElements = Files.lines(Paths.get(new File(args[0]).toURI())).collect(Collectors.toList());
		Set<String> preds = HashSetFactory.make();
		edges.keySet().forEach(elt -> {
			Set<String> goesTo = HashSetFactory.make(); 
			edges.get(elt).forEach(s -> goesTo.add(shorten(s)));
			goesTo.retainAll(currentElements);
			if (goesTo.size() >= .75*currentElements.size()) {
				preds.add(elt);
			}
		});
		
		Set<String> newEsts = HashSetFactory.make();
		preds.forEach(p -> newEsts.addAll(edges.get(p)));
		
		return newEsts;
	}
}
