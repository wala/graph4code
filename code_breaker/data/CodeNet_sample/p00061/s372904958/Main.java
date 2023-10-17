
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

	public static void main(String[] args) throws NumberFormatException,
			IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				System.in));
		StringBuilder builder = new StringBuilder();
		List<Integer> list = new ArrayList<Integer>();

		while (true) {
			String line = reader.readLine();
			if (line.equals("0,0")) {
				break;
			}
			int a = Integer.parseInt(line.split(",")[1]);
			list.add(a);
		}
		List<Integer> sortList = new ArrayList<Integer>();
		sortList.addAll(list);
		Collections.sort(sortList);
		Map<Integer, Integer> memo = new HashMap<Integer, Integer>();
		int count = 1;
		int now = Integer.MAX_VALUE;
		for (int i = sortList.size() - 1; i >= 0; i--) {
			if (now > sortList.get(i)) {
				memo.put(sortList.get(i), count);
				now = sortList.get(i);
				count++;
			}
		}

		String line2;
		while ((line2 = reader.readLine()) != null) {
			if (line2.isEmpty())
				break;
			int num = Integer.parseInt(line2);
			System.out.println(memo.get(list.get(num-1)));
		}
	}
}