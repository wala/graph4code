import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Factorial II
 */
public class Main {

	public static void main(String[] args) throws IOException {

		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line = "";

		while ((line = br.readLine()) != null && !line.isEmpty()) {
			int n = Integer.parseInt(line);
			if (n == 0) {
				break;
			}

			int two = 0;
			int five = 0;

			for (int i = n; i > 1; i--) {
				if (i % 2 == 0) {
					int j = i;
					while (j % 2 == 0) {
						two++;
						j /= 2;
					}
				}
				if (i % 5 == 0) {
					int j = i;
					while (j % 5 == 0) {
						five++;
						j /= 5;
					}
				}
			}
			System.out.println(two < five ? two : five);
		}
	}
}