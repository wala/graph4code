import java.util.*;
class Main{
	private static Scanner sc= new Scanner(System.in);;
	private static String str="";
	static int n=0;
	static Map<Integer,Set<String>>map=new TreeMap<Integer,Set<String>>();
	public static void main(String[] args){
		for(;!(str=sc.next()).equals("0,0");){
			args=str.split(",");
			if(!map.containsKey(n=new Integer(args[1])))map.put(n,new HashSet<String>());
			map.get(n).add(args[0]);
		}
		
		
		
		ans();
		

	}
	static void ans(){
		for(int i=0;sc.hasNext();System.out.println(i)){
			str=sc.next();i=map.size();
			for(int k:map.keySet()){
				if(map.get(k).contains(str))break;--i;
			}
		}
		
	}
}