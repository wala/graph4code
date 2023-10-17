import java.util.Scanner;

public class Main {

	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		while(sc.hasNextLine()) {
			String s = sc.nextLine();
			for(int i=0;i<26;i++) {
				String dec = caesarEncrypt(s, i);
				String[] spl = dec.split(" ");
				boolean flag = false;
				for(String t:spl) {
					if(t.equals("the") || t.equals("this") || t.equals("that")) {
						flag = true;
						break;
					}
				}
				if (flag) {
					System.out.println(dec);
					break;
				}
			}
		}
	}

	public static String caesarEncrypt(String s,int x) {
		char[] c = s.toCharArray();
		for(int i=0;i<c.length;i++) {
			if ('a' <= c[i] && c[i] <= 'z') {
				c[i] = (char) ((c[i] - 'a' + x) % 26 + 'a');
			}
		}
		return String.valueOf(c);
	}
}