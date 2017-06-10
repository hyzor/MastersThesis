package com.king.rbea.manager.debugger;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.lang.Runtime;

import org.apache.commons.io.IOUtils;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import com.king.event.Event;
import com.king.rbea.Aggregators;
import com.king.rbea.Context;
import com.king.rbea.Registry;
import com.king.rbea.aggregators.Counter;
import com.king.rbea.aggregators.SumAggregator;
import com.king.rbea.backend.scriptexecution.CollectingAggregators;
import com.king.rbea.exceptions.BackendException;
import com.king.rbea.exceptions.ProcessorException;
import com.king.rbea.manager.debugger.rbeacontext.JesperAggregators;
import com.king.rbea.manager.scriptvalidation.FakedOutput;
import com.king.rbea.manager.scriptvalidation.ScriptTester;
import com.king.rbea.scripts.ScriptUtils;
import com.king.rbea.scripts.codegen.CodeGenScriptExecutor;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

public class JesperDebugger {	
	private static Registry registry = new JesperRegistry();
	private static Map<String, Context> contexts = new HashMap<>();
	private static final int numWorkers = 5;
	
	//private static Thread thread;
	private static ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
	private static List<JesperEventWorker> workers = new ArrayList<JesperEventWorker>();
	private static ArrayList<Future<ArrayList<String>>> futures = new ArrayList<>();
	private static ReadWriteLock lock = new ReentrantReadWriteLock();
	
	private static final ArrayList<Event> events = new ArrayList<Event>();
	private static final int numEvents = 100;
	
	private static GroovyShell groovyShell;
	
	private static boolean generateEvents = true;
	
	private static RocksDB rocksDB = null;
	private static Options rocksOpts = new Options().setCreateIfMissing(true);
	private static RocksIterator rocksIt = null;
	
	private static String rocksDbName = "rocksDB";
			
	private static String aggsToString(Aggregators aggs) {
		String result = "";
		
		if (aggs instanceof JesperAggregators) {
			JesperAggregators agg = (JesperAggregators)aggs;
			
			result += "Counters: ";
			
			for (Map.Entry<Object, Counter> entry : agg.getCounters().entrySet()) {
				result += "(" + entry.getKey().toString() + " : " + entry.getValue().toString() + ") ";
			}
			
			result += "SumAggregators: ";
			
			for (Map.Entry<Object, SumAggregator> entry : agg.getSumAggs().entrySet()) {
				result += "(" + entry.getKey().toString() + " : " + entry.getValue().toString() + ") ";
			}
			
		} else if (aggs instanceof CollectingAggregators) {
			result += "CollectingAggregators not supported yet";
		} else {
			result += "Unsupported aggregators";
		}
		
		return result;
	}
	
	public static ArrayList<String> getAllContextStr() throws ProcessorException, BackendException {		
		ArrayList<String> output = new ArrayList<String>();
		
		for (JesperEventWorker worker : workers) {
			output.add("==============================");
			
			if (worker.isProcessing()) {
				output.add(worker.getCurThreadName() + " <---");
			} else {
				output.add(worker.getCurThreadName());
			}
			
			output.add("==============================");
			output.addAll(getContextStr(worker.getCurContext(), worker.getCurKey()));
		}
		
		return output;
	}
	
	static ArrayList<String> getContextStr(Context ctx, String key) throws ProcessorException, BackendException {
		ArrayList<String> output = new ArrayList<String>();

		if (ctx == null) {
			if (!contexts.containsKey(key)) {
				output.add("Context does not exist!");
				return output;
			}
			
			ctx = contexts.get(key);
		}
		
		output.add("Context (key: " + key + "):");
		
		String aggregatorValue = "";
		
		aggregatorValue = aggsToString(ctx.getAggregators());
		
		output.add("Aggregators: " + aggregatorValue);
		output.add("State: " + ctx.getState().toString());
		
		String localStateStr = "Local state: {";
		Map<Object, Object> localState = ctx.getState().getLocalState();
		Iterator<Entry<Object, Object>> it = localState.entrySet().iterator();
		
		while (it.hasNext()) {
			Map.Entry<Object, Object> entry = (Map.Entry<Object, Object>)it.next();
			localStateStr += "{" + entry.getKey().toString() + ":" + entry.getValue().toString() + "}, ";
		}
		
		localStateStr += "}";
		output.add(localStateStr);
		output.add("Output: " + ((FakedOutput)ctx.getOutput()).getLog());
		return output;
	}
	
	public static ArrayList<String> getLocalStates() {
		ArrayList<String> output = new ArrayList<String>();
		
		rocksIt.seekToFirst();
		
		for (rocksIt.seekToFirst(); rocksIt.isValid(); rocksIt.next()) {
			output.add(new String(rocksIt.key()) + " : " + new String(rocksIt.value()));
		}
		
		return output;
	}

	public static void main(String[] args) {
		if (args.length < 4) {
			return;
		}
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("Running shutdown hook!");
				
				if (rocksDB != null) {
					rocksDB.close();
				}
				
				if (executor != null) {
					executor.shutdown();
				}
			}
		});
		
		String groovyTmpPath = args[0];
		String groovyFileName = args[1];
		boolean loadEvents = Boolean.parseBoolean(args[2]);
		String eventsFile = args[3];
		String scriptStr = null;
		
		FileInputStream fileInputStream = null;
		FileInputStream fileInputStreamEv = null;
		
		RocksDB.loadLibrary();
		
		try {
			rocksDB = RocksDB.open(rocksOpts, JesperWebSocket.tmpPathWin + rocksDbName);
		} catch (RocksDBException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		rocksIt = rocksDB.newIterator();
		
		try {
			fileInputStream = new FileInputStream(groovyTmpPath + groovyFileName);
			scriptStr = IOUtils.toString(fileInputStream);
			fileInputStream.close();
			
			if (loadEvents) {
				generateEvents = false;
				fileInputStreamEv = new FileInputStream(groovyTmpPath + eventsFile);
				
				BufferedReader bufReader = new BufferedReader(new InputStreamReader(fileInputStreamEv));
				
				String line = null;
				
				while ((line = bufReader.readLine()) != null) {
					try {
						Event ev = ScriptTester.convertToEvent(groovyShell, line);
						events.add(ev);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				fileInputStreamEv.close();
			}			
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		CompilerConfiguration compilerConfig;
		compilerConfig = ScriptUtils.getCompilerConfig();
		compilerConfig.setDebug(true);
		compilerConfig.setVerbose(true);

		groovyShell = new GroovyShell(new Binding(), compilerConfig);
		Script script = groovyShell.parse(scriptStr);
		script.run();
		Class<?> groovyClass = script.getMetaClass().getTheClass();

		CodeGenScriptExecutor codeExecutor = new CodeGenScriptExecutor(groovyClass);
		try {
			codeExecutor.init();
		} catch (ProcessorException e1) {
			e1.printStackTrace();
		}
		
		try {
			codeExecutor.initialize(registry);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
				
		for (int i = 0; i < numWorkers; ++i) {
			workers.add(new JesperEventWorker(codeExecutor, contexts,
					lock, (JesperRegistry) registry, rocksDB));
		}
		
		// Generate events if instructed to do so
		if (generateEvents) {
			for (int i = 0; i < numEvents; ++i) {
				events.add(JesperEventGenerator.generateEvent());
			}
		}
		
		// Populate the events to our workers
		for (int i = 0; i < events.size(); ++i) {
			workers.get(i % numWorkers).addEvent(JesperEventGenerator.generateEvent());
		}
		
		// Submit workers to executor
		for (JesperEventWorker evWorker : workers) {
			futures.add(executor.submit(evWorker));
		}
		
		boolean isFinished = false;
		
		while (!isFinished) {
			for (Iterator<Future<ArrayList<String>>> it = futures.iterator(); it.hasNext();) {
				Future<ArrayList<String>> future = it.next();
				
				if (future.isDone()) {
					ArrayList<String> output = null;
					try {
						output = future.get();
					} catch (InterruptedException | ExecutionException e) {
						e.printStackTrace();
					}
					
					for (String str : output) {
						System.out.println(str);
					}
					
					it.remove();
				}
			}
			
			if (futures.isEmpty()) {
				isFinished = true;
			}
		}
		
		rocksDB.close();
		executor.shutdown();
	}
}