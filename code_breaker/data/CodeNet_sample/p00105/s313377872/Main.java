import java.util.*;
public class Main {
	static String[][] map;
	static boolean[][] al;
	public static void main(String[] args) {
		Scanner stdIn = new Scanner(System.in);
		ArrayList<String> list = new ArrayList<String>();
		TreeMap<String, ArrayList<Integer>> map = new TreeMap<String, ArrayList<Integer>>();
		while(stdIn.hasNext()) {
			String name = stdIn.next();
			int number = stdIn.nextInt();
			
			int tmp = Collections.binarySearch(list, name);
			if(tmp >= 0) {
				ArrayList<Integer> a = map.get(name);
				a.add(number);
				map.put(name, a);
			}
			else {
				list.add((tmp+1)*-1,name);
				ArrayList<Integer> aaa = new ArrayList<Integer>();
				aaa.add(number);
				map.put(name, aaa);
			}
		}
		for(int i = 0; i < list.size(); i++) {
			ArrayList<Integer> tmp = map.get(list.get(i));
			System.out.println(list.get(i));
			Collections.sort(tmp);
			System.out.print(tmp.get(0));
			for(int j = 1; j < tmp.size(); j++) {
				System.out.print(" "+ tmp.get(j));
			}
			System.out.println();
		}
	}
}