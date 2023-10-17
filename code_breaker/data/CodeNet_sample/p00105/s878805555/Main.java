import java.util.PriorityQueue;
import java.util.Scanner;

public class Main {
	
	static class Dic implements Comparable<Dic>{
		String moji;
		int page;
		Dic(String moji, int page){
			this.moji=moji;
			this.page=page;
		}
		public int compareTo(Dic d) {
			if(this.moji.equals(d.moji)) {
				return this.page-d.page;
			}
			else {
				return this.moji.compareTo(d.moji);
			}
		}
	}
	
	public static void main(String[] args) {
		try(Scanner sc = new Scanner(System.in)){
			PriorityQueue<Dic> pq=new PriorityQueue<>();
			while(sc.hasNextLine()) {
				String str=sc.nextLine();
				String[] token=str.split(" ");
				String s=token[0];
				int n=Integer.parseInt(token[1]);
				pq.add(new Dic(s, n));
			}
			String pre="";
			while(!pq.isEmpty()) {
				Dic dic=pq.poll();
				if(dic.moji.equals(pre)) {
					System.out.print(" "+dic.page);
				}
				else if(pre.equals("")) {
					System.out.println(dic.moji);
					System.out.print(dic.page);
				}
				else {
					System.out.println();
					System.out.println(dic.moji);
					System.out.print(dic.page);
				}
				pre=dic.moji;
			}
			System.out.println();
		}
	}
}
