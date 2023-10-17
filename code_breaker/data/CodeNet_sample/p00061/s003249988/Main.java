import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

class Main{

static class DataOfPCK{

int num;
int answer;

DataOfPCK(int num,int answer){
this.num=num;  this.answer=answer;
}

static final Comparator<DataOfPCK> ANSWER_ORDER=new AnswerOrderComparator();

private static class AnswerOrderComparator implements Comparator<DataOfPCK>{
public int compare(DataOfPCK d1,DataOfPCK d2){
return (d1.answer>d2.answer)?-1:(d1.answer<d2.answer)?1:0;
}
}
}

public static void main(String[] args) throws IOException{
BufferedReader br=new BufferedReader(new InputStreamReader(System.in));
String line="";
ArrayList<DataOfPCK> array=new ArrayList<DataOfPCK>();
HashMap<Integer,Integer> dic=new HashMap<Integer,Integer>();
DataOfPCK d;
int p,rank;

while(!(line=br.readLine()).equals("0,0")){
String[] values=line.split(",");
int num=Integer.parseInt(values[0]);
int answer=Integer.parseInt(values[1]);
array.add(new DataOfPCK(num,answer));
}

Collections.sort(array,DataOfPCK.ANSWER_ORDER);

p=-1;
rank=0;
for(int i=0;i<array.size();i++){
d=array.get(i);
if(d.answer==p){
dic.put(d.num,rank);
}
else{
rank++;
dic.put(d.num,rank);
p=d.answer;
}
}

while((line=br.readLine())!=null){
System.out.println(dic.get(Integer.parseInt(line)));
}
}

}