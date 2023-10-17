import java.io.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Scanner;
public class Main {
	public static void main(String[] args) {
		FastScanner sc = new FastScanner();
		PrintWriter out = new PrintWriter(System.out);
		
		ArrayList<Data> list = new ArrayList<Data>();
		while(sc.hasNext()) {
			
			String[] tmp = sc.next().split(",");
			if(tmp[0].equals("0") && tmp[1].equals("0")) break;
			list.add(new Data(Integer.parseInt(tmp[0]),Integer.parseInt(tmp[1])));
		}
		Collections.sort(list,new Comp());
		int[] len = new int[list.size()];
		len[list.get(0).id-1] = 1;
		int r = 1;
		for(int i = 1; i < list.size(); i++) {
			if(list.get(i).point != list.get(i-1).point) {
				len[list.get(i).id-1] = ++r;
			}
			else {
				len[list.get(i).id-1] = r;
			}
		}
		
		while(sc.hasNext()) {
			int n = (int)sc.nextLong();
			System.out.println(len[n-1]);
		}
		out.flush();
	}
}
class Comp implements Comparator<Data>{

	@Override
	public int compare(Data o1, Data o2) {
		if(o1.point > o2.point) {
			return -1;
		}
		else if(o1.point < o2.point) {
			return 1;
		}
		else if(o1.id > o2.id) {
			return 1;
		}
		else if(o1.id < o2.id) {
			return -1;
		}
		return 0;
	}
	
}
class Data {
	int id;
	int point;
	Data(int a, int b) {
		id = a;
		point = b;
	}
}
	
//------------------------------//
//-----------//
class FastScanner {
    private final InputStream in = System.in;
    private final byte[] buffer = new byte[1024];
    private int ptr = 0;
    private int buflen = 0;
    private boolean hasNextByte() {
        if (ptr < buflen) {
            return true;
        }else{
            ptr = 0;
            try {
                buflen = in.read(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (buflen <= 0) {
                return false;
            }
        }
        return true;
    }
    private int readByte() { if (hasNextByte()) return buffer[ptr++]; else return -1;}
    private static boolean isPrintableChar(int c) { return 33 <= c && c <= 126;}
    private void skipUnprintable() { while(hasNextByte() && !isPrintableChar(buffer[ptr])) ptr++;}
    public boolean hasNext() { skipUnprintable(); return hasNextByte();}
    public String next() {
        if (!hasNext()) throw new NoSuchElementException();
        StringBuilder sb = new StringBuilder();
        int b = readByte();
        while(isPrintableChar(b)) {
            sb.appendCodePoint(b);
            b = readByte();
        }
        return sb.toString();
    }
    public long nextLong() {
        if (!hasNext()) throw new NoSuchElementException();
        long n = 0;
        boolean minus = false;
        int b = readByte();
        if (b == '-') {
            minus = true;
            b = readByte();
        }
        if (b < '0' || '9' < b) {
            throw new NumberFormatException();
        }
        while(true){
            if ('0' <= b && b <= '9') {
                n *= 10;
                n += b - '0';
            }else if(b == -1 || !isPrintableChar(b)){
                return minus ? -n : n;
            }else{
                throw new NumberFormatException();
            }
            b = readByte();
        }
    }
}