import java.util.*;

public class Main {
    
    public static void main(String[] args) {
        try(Scanner sc = new Scanner(System.in)) {
            Map<String, Integer> sen = new HashMap<String, Integer>();
            String fre = "";
            String max = "";
            int senc = 0;
            while(sc.hasNext()) {
                String s = sc.next();
                if(max.length() < s.length()) max = s;
                if(sen.containsKey(s)) {
                    int c = sen.get(s);
                    if(senc < c + 1) {
                        senc = c + 1;
                        fre = s;
                    }
                    sen.put(s, c+1);
                }
                else {
                    if(senc < 1) {
                        senc = 1;
                        fre = s;
                    }
                    sen.put(s, 1);
                }
            }
            System.out.println(fre + " " + max);
        }
    }
}

