package util;

import java.io.FileInputStream;
import java.io.IOException;

import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;

import com.ibm.wala.util.io.Streams;

public class GetTurtleMethods {
	
	public static void main(String... args) throws IllegalArgumentException, IOException {
		RDFConnection kg = RDFConnectionFactory.connect(args[0]);
		String query = new String(Streams.inputStream2ByteArray(new FileInputStream(args[1])));
		kg.querySelect(query, (qs) -> {
			String method = qs.getLiteral("method").getString();
			System.out.println(method);
		});
	}
}
