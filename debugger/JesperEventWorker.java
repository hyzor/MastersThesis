package com.king.rbea.manager.debugger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReadWriteLock;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import com.king.event.Event;
import com.king.rbea.Context;
import com.king.rbea.Field;

import com.king.rbea.manager.debugger.rbeacontext.JesperContext;
import com.king.rbea.manager.debugger.rbeacontext.JesperState;
import com.king.rbea.scripts.codegen.CodeGenScriptExecutor;

public class JesperEventWorker implements Callable<ArrayList<String>> {
		
		private JesperRegistry reg;
		private RocksDB rocksDB;

		public JesperEventWorker(CodeGenScriptExecutor codeExe,
				Map<String, Context> contexts,
				ReadWriteLock lock,
				JesperRegistry reg,
				RocksDB rocksDB) {
			events = new LinkedList<>();
			this.codeExe = codeExe;
			this.contexts = contexts;
			this.lock = lock;
			threadName = Thread.currentThread().getName();
			isProcessing = false;
			this.reg = reg;
			this.rocksDB = rocksDB;
		}

		private void run() {
			while (!events.isEmpty()) {
				Event ev = events.pop();
				String key = ev.getString(0);
				
				System.out.println(Thread.currentThread().getName() + ": processing event " + ev.toString());
				lock.writeLock().lock();
				isProcessing = true;
				
				if (!contexts.containsKey(key)) {
					contexts.put(key, new JesperContext());
				}
				
				curContext = contexts.get(key);
				threadName = Thread.currentThread().getName();
				curKey = key;
				
				JesperContext jCtx = null;
				JesperState jState = null;
				
				if (curContext instanceof JesperContext) {
					jCtx = (JesperContext)curContext;
					jState = (JesperState)jCtx.getState();
				} else {
					System.out.println("Using unsupported Context type in " + this.getClass().getName());
				}
				
				try {
					// Update the fields 
					for (Field<?> f : reg.fields) {
						Optional<Object> input = f.transformInput(ev);
						
						// Lazy update
						if (input.isPresent()) {
							Object currentField = curContext.getState().get(f.name);
							currentField = currentField == null ? f.getInitialValue(0) : currentField;
							
							if (jState != null) {
								jState.put(f.name, currentField);
								System.out.println("Updating field " + f.name);
							}
							
							Object newFieldVal = f.update(curContext, input.get());

							// put it in context
							if (jState != null) {
								jState.put(f.name, newFieldVal);
								System.out.println("Updating field " + f.name);
							}
							
							try {
								rocksDB.put(curKey.getBytes(), jState.toString().getBytes());
							} catch (RocksDBException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
					
					rocksDB.put(curKey.getBytes(), getCurThreadName().getBytes());
					codeExe.processEvent(ev, curContext);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				lock.writeLock().unlock();				
				isProcessing = false;
				System.out.println(Thread.currentThread().getName() + ": done.");
			}
		}
		
		public void setCodeExe(CodeGenScriptExecutor codeExe) {
			this.codeExe = codeExe;
		}
		
		public void addEvent(Event ev) {
			events.add(ev);
		}
		
		public Context getCurContext() {
			return curContext;
		}
		
		public String getCurThreadName() {
			return threadName;
		}
		
		public String getCurKey() {
			return curKey;
		}
		
		public boolean isProcessing() {
			return isProcessing;
		}
		
		private CodeGenScriptExecutor codeExe; // TODO: Synchronize
		private LinkedList<Event> events;
		private Map<String, Context> contexts; // TODO: Synchronize
		private ReadWriteLock lock;
		private Context curContext;
		private String threadName;
		private String curKey;
		private boolean isProcessing;
		
		@Override
		public ArrayList<String> call() throws Exception {
			ArrayList<String> output = new ArrayList<>();
			run();
			output.add(Thread.currentThread().getName() + ": ");
			output.addAll(JesperDebugger.getContextStr(curContext, curKey));
			output.add("Fields: ");
			for (Field<?> f : reg.fields) {
				output.add(f.name);
			}
			
			return output;
		}
	}