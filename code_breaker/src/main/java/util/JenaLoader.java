package util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.tdb.TDBFactory;

public class JenaLoader {

	public static void main(String[] args) throws InterruptedException {

		ThreadPoolExecutor exec = new ThreadPoolExecutor(5, 10, 100, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		
	    System.out.println("Model file   : " + args[0]);
	    System.out.println("Model tdb dir: " + args[1]);

	    Dataset dataset = TDBFactory.createDataset(args[1]);
	    try {
	    	for(String file : 
	      (new File(args[0])).list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith("ttl");
			} 
	      })) {
	    	  exec.execute(new Runnable() {
				@Override
				public void run() {
			    	  try {
			    		  Model ttl = RDFDataMgr.loadModel(args[0] + file);
			    		  synchronized (dataset) {
			    			  dataset.addNamedModel(file, ttl);
			    		  }
			    	  } catch (RiotException e) {
			    		  System.err.println("failed to load " + file);
			    		  e.printStackTrace();
			    	  }
				}	    		  
	    	  });
	      }
	    } finally {
	    	exec.shutdown();
	    	exec.awaitTermination(30, TimeUnit.SECONDS);
	    	dataset.close();
	    }
	  }
}
