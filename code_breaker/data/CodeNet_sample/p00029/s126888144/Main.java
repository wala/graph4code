import java.util.*;
public class Main {
	
	void doIt() {
		Scanner stdIn = new Scanner(System.in);
		String input = stdIn.nextLine();
		input += " ~";
		String[] str = input.split(" ");
		TreeSet<String> repete = new TreeSet<String>();
		
		Arrays.sort(str);
		//System.out.println(Arrays.toString(str));
		String len = "", mode = str[0];
		int count = 1, max = -1;
		for(int r = 1; r < str.length; r++) {
			if(str[r].length() > len.length()) len = str[r]; 
			if(repete.contains(str[r])) {
				count++;
			} else {
				if(count > max) {
					max = count;
					mode = str[r - 1];
					//System.out.println(count + ", " + mode);
				}
				repete.add(str[r]);
				count = 1;
			}
			//System.out.println(count);
		}
		
		System.out.println(mode + " " + len);
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		new Main().doIt();
	}

}