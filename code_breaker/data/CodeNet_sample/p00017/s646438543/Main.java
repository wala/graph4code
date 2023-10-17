import java.lang.Integer;
import java.lang.Math;
import java.lang.String;
import java.lang.System;
import java.util.Scanner;

public class Main {
    static final String[] KEYWORDS = {"the", "this", "that"};

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (sc.hasNext()) {
            String str = sc.nextLine();

            // 復号するための移動量を算出
            int movement = getMovement(str);
            if (movement >= 0) {
                // 復号処理
                String desryptedStr = decrypt(str, movement);
                System.out.println(desryptedStr);
            } else {
                System.out.println("NOT FOUND");
            }
        }
    }

    // 移動量を算出
    public static int getMovement(String str) {
        int movemnet = 0;
        for (int i = 0; i < 26; i++) {
            String decrptedStr = decrypt(str, i);
            for (int j = 0; j < KEYWORDS.length; j ++) {
                if(decrptedStr.contains(KEYWORDS[j] + " ") || decrptedStr.contains(KEYWORDS[j] + ".")) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static String decrypt(String str, int movement) {
        char c[] = str.toCharArray();
        for (int i = 0; i < c.length; i++) {
            // a-z以外は処理しない
            if (c[i] < 'a' || 'z' < c[i]) {
                continue;
            }

            // 複合
            c[i] = (char)(c[i] + movement);
            if (c[i] > 'z') {
                int fixMovement = c[i] - 'z';
                c[i] = (char)('a' - 1 + fixMovement);
            }
        }
        return String.valueOf(c);
    }
}