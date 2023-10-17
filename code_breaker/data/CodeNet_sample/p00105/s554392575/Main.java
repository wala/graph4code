import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class Main {

	public static void main(String[] args) throws Exception {

		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		String readLine = null;

		Map<String, BookIndex> books = new HashMap<String, BookIndex>();

		while ((readLine = br.readLine()) != null) {

			String[] split = readLine.split(" ");
			String name = split[0];
			int page = Integer.parseInt(split[1]);

			if (books.containsKey(split[0])) {
				BookIndex idx = books.get(name);
				idx.pageList.add(page);
			} else {
				BookIndex idx = new Main().new BookIndex(name);
				idx.pageList.add(page);
				books.put(name, idx);
			}
		}
		Object[] keys = books.keySet().toArray();
		Arrays.sort(keys);
		for (Object key : keys) {
			System.out.println(key);
			BookIndex idx = books.get(key);
			idx.pageList.sort(new Comparator<Integer>() {

				@Override
				public int compare(Integer o1, Integer o2) {
					return o1 - o2;
				}
			});
			for (int pageIdx = 0; pageIdx < idx.pageList.size(); pageIdx ++) {
				if (pageIdx != 0) {
					System.out.print(" ");
				}
				System.out.print(idx.pageList.get(pageIdx));
			}
			System.out.println("");
		}
	}

	public class BookIndex {
		public String name = null;
		public ArrayList<Integer> pageList = new ArrayList<Integer>();
		public BookIndex(String name) {
			this.name = name;
		}
	}
}