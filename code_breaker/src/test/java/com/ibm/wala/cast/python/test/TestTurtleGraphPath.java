package com.ibm.wala.cast.python.test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.jena.query.Dataset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.ibm.wala.codeBreaker.turtle.TurtleGraphUtil;

@RunWith(Parameterized.class)
public class TestTurtleGraphPath extends TurtleGraphUtil {

	@Parameters
	public static Collection<Object[]> tests() {
		return Arrays.asList(
			new Object[][] {
				new Object[] { Arrays.asList("sample1787.py", "sample2537.py", "sample3596.py", "sample8372.py") }
			});
	}
	
	private final List<String> files;
	
	public TestTurtleGraphPath(List<String> files) {
		this.files = files;
	}
	
	@Test
	public void test() throws IOException, InterruptedException {
		Dataset rdf = toDataset(this.files);

		Collection<List<String>> paths =
			backwardPaths(rdf, "plt.plot( yEv.tolist(), yEv_calc.tolist(), '.', ms = ms_sz)", "[165:8]");

		paths.addAll(
			backwardPaths(rdf, "np.sqrt( metrics.mean_squared_error( yEv, yEv_calc))", "[338:11]"));
		
		System.err.println(paths);
		
		assert !paths.isEmpty();
	}
}
