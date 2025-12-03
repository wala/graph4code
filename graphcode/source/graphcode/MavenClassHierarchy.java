package graphcode;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import com.ibm.wala.core.java11.Java9AnalysisScopeReader;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.io.FileUtil;

public class MavenClassHierarchy {


	public static void main(String... args) throws IOException, ClassHierarchyException, InterruptedException {
		AnalysisScope scope = toAnalysisScope(args[0], args[1], args[2]);
	    
	    IClassHierarchy cha = ClassHierarchyFactory.make(scope);
	    
	    System.err.println(cha);
	}

	public static AnalysisScope toAnalysisScope(String group, String artifact, String version) throws IOException, InterruptedException {
		URL p2s = MavenClassHierarchy.class.getResource("pomToScope.sh");
		String script = p2s.getFile();
		ProcessBuilder pb = new ProcessBuilder(new String[]{"bash", "-l", script, group, artifact, version});
		Process p = pb.start();
		p.waitFor();
		String scopeContents = new String(FileUtil.readBytes(p.getInputStream()));
		String errorContents = new String(FileUtil.readBytes(p.getErrorStream()));
		
		assert errorContents.isEmpty();
		
		AnalysisScopeReader reader = new Java9AnalysisScopeReader() {
			
		};
		
	    AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();

	    for(String line : scopeContents.split("\n")) {
	    	reader.processScopeDefLine(scope, MavenClassHierarchy.class.getClassLoader(), line);
	    }
	    
		return scope;
	}
}
