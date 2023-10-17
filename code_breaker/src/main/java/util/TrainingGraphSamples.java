package util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;

public class TrainingGraphSamples {

	public static void main(String... args) throws IOException {
		Double fraction = Double.valueOf(args[1]);
		
		Map<String, MutableIntSet> graphLabels = HashMapFactory.make();
		try (BufferedReader f = new BufferedReader(new FileReader(args[0]))) {
			int lines = Integer.valueOf(f.readLine());
			for(int i = 0; i < lines; i++) {
				String[] header = f.readLine().split(" ");
				
				String label = header[1];
				if (! graphLabels.containsKey(label)) {
					graphLabels.put(label, IntSetUtil.make());
				}
				graphLabels.get(label).add(i);
				
				for(int j = 0; j < Integer.valueOf(header[0]); j++) {
					f.readLine();
				}
			}
		}
		
		List<Integer> sample = new ArrayList<>();
		graphLabels.forEach((l, gs) -> {
			if (gs.size()*fraction > 10) {
				System.err.println(gs.size()*fraction);
				List<Integer> graphs = new ArrayList<>();
				gs.foreach(i -> graphs.add(i));
				Collections.shuffle(graphs);
				for(int i = 0; i < gs.size()*fraction; i++) {
					sample.add(graphs.get(i));
				}
			}
		});
		
		int rem = sample.size() % 10;
		TrainingGraphs.testTrainSplitFiles("10fold_idx", sample.size()-rem, sample);
	}
}
