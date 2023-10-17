import java.util.*;

/**
 * AOJ #0105: Book Index (PCK 2005)
 */
public class Main {
    public static void main(String[] arg) {
        Scanner scanner = new Scanner(System.in);
        ReferenceSet refs = new ReferenceSet();
        while (scanner.hasNext()) {
            String word = scanner.next();
            int    page = scanner.nextInt();
            refs.add(word, page);
        }
        for (String word : refs.keySet()) {
            System.out.println(word);
            System.out.println(join(refs.get(word)));
        }
    }

    static String join(Iterable<?> set) {
        StringBuilder str = new StringBuilder();
        for (Object x : set) {
            if (str.length() > 0) { str.append(' '); }
            str.append(x);
        }
        return str.toString();
    }

    static class ReferenceSet extends TreeMap<String, SortedSet<Integer>> {
        public void add(String word, int page) {
            SortedSet<Integer> set = this.get(word);
            if (set == null) {
                set = new TreeSet<Integer>();
                this.put(word, set);
            }
            set.add(page);
        }
    }
}