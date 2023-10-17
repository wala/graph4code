import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class Main {
	public static void main(String[] args){
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String tes = null;
		
		ArrayList<Integer> al = new ArrayList<Integer>();
		ArrayList<Integer> al2 = new ArrayList<Integer>();
		ArrayList<Integer> al3 = new ArrayList<Integer>();
		ArrayList<Integer> al4 = new ArrayList<Integer>();
		
		int a,b;
		String[] fruit;
		while(true){
			try {
				tes = br.readLine();
			} catch (IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
			
			fruit = tes.split(",", 0);
			
			a = Integer.parseInt(fruit[0]);
			b = Integer.parseInt(fruit[1]);
			if(a == 0 && b == 0){
				break;
			}
			al.add(a);
			al2.add(b);
		}
		
		while(true){
			try {
				tes = br.readLine();
			} catch (IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
			if(tes == null){
				break;
			}
			
			if(tes.equals("")){
				break;
			}
			al3.add(Integer.parseInt(tes));
		}
		
		
		for(int i=0;i<al.size();i++){
			for(int j=i;j<al.size();j++){
				if(al2.get(i) <= al2.get(j)){
					a = al2.get(i);
					al2.set(i, al2.get(j));
					al2.set(j, a);
					
					a = al.get(i);
					al.set(i, al.get(j));
					al.set(j, a);
				}
			}
		}
		
		int f = 1;
		int c = 0;
		
		for(int i=0;i<al.size();i++){
			
			
			
			if(i != (al.size() -1) &&al2.get(i) ==al2.get(i+1)){
				f++;
			}
			
			if(i != (al.size() -1) &&al2.get(i) != al2.get(i+1)){
				c++;
				for(int j=0;j<f;j++){
					al4.add(c);
				}
				
				f=1;
			}
			if(i == (al.size() -1)){
				c++;
				for(int j=0;j<f;j++){
					al4.add(c);
				}
			}
			
			
		}
		
		
		for(int j=0;j<al3.size();j++){
			for(int i=0;i<al.size();i++){
				
				if(al3.get(j) == al.get(i)){
					System.out.println(al4.get(i));
				}
				
			}
		}
	}
			

}