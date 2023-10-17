import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {

    private static final char START_ALPHABET = 'a';
    private static final char END_ALPHABET = 'z';
    private static final char CHAR_T = 't';
    private static final char CHAR_PERIOD = '.';
    private static final String COMPARE_THE = "the";
    private static final String COMPARE_THIS = "this";
    private static final String COMPARE_THAT = "that";
    private static final String BLANK = " ";

    public static void main(String[] args) {

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        try {
            String input = null;
            while ((input = in.readLine()) != null) {

                String[] cutData = input.split(BLANK);
                int caesarNum = solveCaesarNum(cutData);

                StringBuilder result = new StringBuilder();

                for (String data : cutData) {
                    result.append(routation(data, caesarNum)).append(BLANK);
                }

                // 最後の空白を削除
                result.delete(result.length() - 1, result.length());

                System.out.println(result.toString());
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static int solveCaesarNum(String[] cutData) {

        int cnt = 0;
        boolean stopFlg = false;

        while (cnt < END_ALPHABET - START_ALPHABET + 1) {

            for (String data : cutData) {

                String checkData = data;

                // 単語の最後がピリオドならば、ピリオドは除外
                if (data.charAt(data.length() - 1) == CHAR_PERIOD) {
                    checkData = data.substring(0, data.length() - 1);
                }

                if (checkData.length() == 3) {
                    String checkThe = routation(checkData,
                            CHAR_T - checkData.charAt(0));

                    if (COMPARE_THE.equals(checkThe)) {
                        stopFlg = true;
                        cnt = CHAR_T - checkData.charAt(0);
                        break;
                    }
                } else if (checkData.length() == 4) {

                    String checkThe = routation(checkData,
                            CHAR_T - checkData.charAt(0));

                    if (COMPARE_THAT.equals(checkThe)
                            || COMPARE_THIS.equals(checkThe)) {
                        stopFlg = true;
                        cnt = CHAR_T - checkData.charAt(0);
                        break;
                    }
                }
            }

            if (stopFlg) {
                break;
            }

            cnt++;
        }

        return cnt;
    }

    private static String routation(String data, int caesarNum) {

        char[] convertChar = data.toCharArray();

        for (int i = 0; i < convertChar.length; i++) {

            // カンマはスルー
            if (convertChar[i] == CHAR_PERIOD) {
                continue;
            }

            convertChar[i] += caesarNum;

            if (convertChar[i] < START_ALPHABET) {
                convertChar[i] += END_ALPHABET - START_ALPHABET + 1;
            } else if (convertChar[i] > END_ALPHABET) {
                convertChar[i] += START_ALPHABET - END_ALPHABET - 1;
            }
        }

        return String.valueOf(convertChar);
    }
}