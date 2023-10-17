import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

public class Main {

	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		Map<String,Integer> count = new HashMap<String,Integer>();
		String longest = "";
		while(sc.hasNext()) {
			String s = sc.next();
			if (s.length() > longest.length()) {
				longest = s;
			}
			if (count.containsKey(s)) {
				count.put(s, count.get(s) + 1);
			}else{
				count.put(s, 1);
			}
		}
		int max = 0;
		String most = "";
		for(Entry<String, Integer> e:count.entrySet()) {
			if (e.getValue() > max) {
				max = e.getValue();
				most = e.getKey();
			}
		}
		System.out.println(most + " " + longest);
	}

}