import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class Main {

	private BufferedReader in = new BufferedReader(new InputStreamReader(
			System.in));

	public static void main(String[] args) {
		Main wordcount = new Main();
		String[] words = wordcount.input();
		Map<String, ArrayList<Integer>> entries = wordcount.count(words);
		wordcount.print(entries);
	}

	/**
	 * 出現位置を保存
	 */
	private Map<String, ArrayList<Integer>> count(String[] entries) {
		Map<String, ArrayList<Integer>> map = new TreeMap<String, ArrayList<Integer>>();
		for (int i = 0; i < entries.length; i += 2) {
			if (entries[i].equals(""))
				continue;
			List<Integer> index;
			if ((index = map.get(entries[i])) == null) {
				ArrayList<Integer> list = new ArrayList<Integer>();
				list.add(Integer.parseInt(entries[i + 1]));
				map.put(entries[i], list);
			} else {
				index.add(Integer.parseInt(entries[i + 1]));
			}
		}
		return map;
	}

	/**
	 * 単語＋改行＋indexで出力
	 */
	private void print(Map<String, ArrayList<Integer>> map) {
		for (String str : map.keySet()) {
			List<Integer> list = map.get(str);
			Collections.sort(list);
			System.out.print(str + "\n" + list.get(0));
			for (int i = 1; i < list.size(); i++) {
				System.out.print(" " + list.get(i));
			}
			System.out.println();
		}
	}

	/**
	 * 標準入力から読み込んだ文字列を空白区切りで分割
	 */
	private String[] input() {
		String str = null;
		try {
			str = in.readLine();
			while (true) {
				String input = in.readLine();
				if (input == null)
					break;
				str = str + " " + input;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (str == null)
			return new String[0];
		else
			return str.toLowerCase().split("\\s+");
	}
}