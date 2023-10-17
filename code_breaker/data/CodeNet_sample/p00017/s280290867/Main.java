import java.util.*;
class Main {
	public static int j;
	public static int y;
	public static ArrayList<Long> list = new ArrayList<Long>();
	public static void main(String[] args) {
		Scanner stdIn = new Scanner(System.in);
		while(stdIn.hasNext()) {
			String tmp = stdIn.nextLine();
			stdIn.useDelimiter(".");
			solv(tmp);
			
		}
	}
	public static void solv(String tmp) {
		String tmpX = "";
		for(int i = 0; i < tmp.length(); i++) {
			tmpX += String.valueOf(tmp.charAt(i));
		}
		while(true) {
			String tmpCX = "";
			for(int i = 0; i < tmpX.length(); i++) {
				if(tmpX.charAt(i) == ' ') {
					tmpCX += " ";
				}
				else if(tmpX.charAt(i) == '.') {
					tmpCX += ".";
				}
				else if(tmpX.charAt(i) == 'z') {
					tmpCX += "a";
				}
				else {
					char tmpC = tmpX.charAt(i);
					tmpC++;
					tmpCX += String.valueOf(tmpC);
				}
			}
			if(tmpCX.contains("the") || tmpCX.contains("that") || tmpCX.contains("this")) {
				System.out.println(tmpCX);
				break;
			}
			tmpX = tmpCX; 
		}
	}
}