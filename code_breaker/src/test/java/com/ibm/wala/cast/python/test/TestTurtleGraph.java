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
public class TestTurtleGraph extends TurtleGraphUtil {

	@Parameters
	public static Collection<Object[]> tests() {
		return Arrays.asList(
			new Object[][] {
				new Object[] { Arrays.asList("sample1787.py", "sample2537.py", "sample3596.py", "sample8372.py") }
			});
	}
	
	private final List<String> files;
	
	public TestTurtleGraph(List<String> files) {
		this.files = files;
	}
	
	@Test
	public void test() throws IOException, InterruptedException {
		Dataset rdf = toDataset(this.files);
		
		String srcSource = "yEv_calc.tolist()";
		String dstSource = "plt.plot( yEv.tolist(), yEv_calc.tolist(), '.', ms = ms_sz)";
		checkSourceEdge(rdf, srcSource, "[384:32]", dstSource, "[384:8]");

		srcSource = "ax.get_xlim()";
		dstSource = "np.min([ax.get_xlim(), ax.get_ylim()])";
		checkSourceEdge(rdf, srcSource, "[323:20]", dstSource, "[323:12]");

		srcSource = "linear_model.LinearRegression()";
		dstSource = "clf.fit( RM, yE)";
		checkSourceEdge(rdf, srcSource, "[1483:10]", dstSource, "[1484:4]");

		srcSource = "linear_model.LinearRegression()";
		dstSource = "clf.predict( RMv)";
		checkSourceEdge(rdf, srcSource, "[1483:10]", dstSource, "[1452:15]");

		srcSource = "linear_model.LinearRegression()";
		dstSource = "clf.predict( RMv)";
		checkSourceEdge(rdf, srcSource, "[62:10]", dstSource, "[311:15]");

		srcSource = "linear_model.Ridge( alpha = alpha)";
		dstSource = "clf.predict( RMv)";
		checkSourceEdge(rdf, srcSource, "[86:10]", dstSource, "[311:15]");

		srcSource = "linear_model.LinearRegression()";
		dstSource = "clf.predict( RMv)";
		checkSourceEdge(rdf, srcSource, "[79:10]", dstSource, "[374:15]");

		srcSource = "np.mat( df_ann['out'].tolist())";
		dstSource = "yEv_calc.tolist()";
		checkSourceEdge(rdf, srcSource, "[105:13]", dstSource, "[165:32]");

	}
}
