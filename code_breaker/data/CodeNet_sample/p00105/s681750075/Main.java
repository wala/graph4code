import java.util.*;

public class Main {
    private static final Scanner scn = new Scanner(System.in);
    
    public static void main(String[] args) {
        printMap(createMap());
    }
    
    private static Map<String, List<Integer>> createMap() {
        Map<String, List<Integer>> msl = new TreeMap<>();
        while(scn.hasNext()) {
            String str = scn.next();
            int page = scn.nextInt();
            List<Integer> li = null;
            if(msl.containsKey(str)) {
                li = msl.get(str);
            } else {
                li = new ArrayList<Integer>();
            }
            li.add(page);
            msl.put(str, li);
        }
        return msl;
    }
    
    private static void printMap(Map<String, List<Integer>> msl) {
        for(String str : msl.keySet()) {
            StringBuilder sb = new StringBuilder();
            List<Integer> li = msl.get(str);
            Collections.sort(li);
            for(int page : li) {
                sb.append(page + " ");
            }
            System.out.println(str);
            System.out.println(sb.toString().trim());
        }
    }
}