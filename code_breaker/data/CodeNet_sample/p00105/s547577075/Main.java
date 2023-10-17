
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;

public class Main {

  
    public static void main(String[] args)  {

    	try{
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String line;
            TreeMap<String,ArrayList<Integer>>index = new TreeMap<String,ArrayList<Integer>>();
            while((line=br.readLine())!=null){if(line.isEmpty())break;
                    String[] spl = line.split(" ");
	            String moji = spl[0]; int kazu = Integer.parseInt(spl[1]);
                    if(index.containsKey(moji)){
                        index.get(moji).add(kazu);
                        Collections.sort(index.get(moji));
                    }else{
                        ArrayList <Integer> al1 = new ArrayList<Integer>();
                        al1.add(kazu);
                        index.put(moji,al1);
                    }
                    
                    
                    
                    
                    
            }//End WHILE
            for(String key : index.keySet()){
                System.out.println(key);
                ArrayList al2 = index.get(key);
                for(int i=0; i<al2.size();i++){
                    System.out.print(al2.get(i));
                    if(i<al2.size()-1){System.out.print(" ");}
                    else{System.out.println("");}
                }
            }

        }catch(Exception e){e.printStackTrace();}

    }


}