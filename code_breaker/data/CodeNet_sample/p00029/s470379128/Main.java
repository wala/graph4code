import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main{
	public static void main(String[] args){
		Scanner sc = new Scanner(System.in);
		String[] s = sc.nextLine().split(" ");
		String len_max = s[0],ans = s[0];
		int count_max = 0;
		Map<String,Integer> map = new HashMap<String,Integer>();
		
		for(int i=0;i<s.length;i++){
			if(len_max.length() < s[i].length()){
				len_max = s[i];
			}
			if(map.get(s[i]) != null){
				map.put(s[i],map.get(s[i])+1);
			}else{
				map.put(s[i], 0);
			}
			if(map.get(s[i]) > count_max){
				count_max = map.get(s[i]);
				ans = s[i];
			}
		}
		
		System.out.println(ans + " " + len_max);
	}
}