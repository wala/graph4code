
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Scanner;

class Main {

	/*
	 * https://onlinejudge.u-aizu.ac.jp/#/problems/0029
	 */

	public static void main(String[] args) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		Scanner scanner = new Scanner(br.readLine());
		HashMap<String, Integer> map = new HashMap<>();
		while (scanner.hasNext()) {
			String word = scanner.next();
			Integer frequency = map.get(word);
			if(frequency==null) {
				map.put(word, 1);
			}else {
				map.put(word, frequency+1);
			}
		}
		scanner.close();

		String[] wordArray = map.keySet().toArray(new String[0]);
		String mostFreq = wordArray[0];
		for(int i=1; i<wordArray.length; i++) {
			int freq = map.get(wordArray[i]);
			if(freq > map.get(mostFreq)) {
				mostFreq = wordArray[i];
			}
		}
		String maxLength = wordArray[0];
		for(String word : wordArray) {
			if(word.length() > maxLength.length()) {
				maxLength = word;
			}
		}

		System.out.printf("%s %s\n", mostFreq, maxLength);
	}

}

