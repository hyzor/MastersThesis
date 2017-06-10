package com.king.rbea.manager.debugger.rbeacontext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.king.rbea.State;
import com.king.rbea.exceptions.BackendException;
import com.king.rbea.exceptions.ProcessorException;

public class JesperState implements State {
	
	private final Map<Object, Object> localState = new HashMap<Object, Object>();
	private final Map<String, Object> fields;
	
	public JesperState(HashMap<String, Object> fields) {
		this.fields = fields;
	}
	
	@Override
	public String toString() {
		String stateStr = "{";
		
		Iterator<Entry<String, Object>> it = fields.entrySet().iterator();
		
		while (it.hasNext()) {
			Map.Entry<String, Object> entry = (Map.Entry<String, Object>) it.next();
			
			stateStr += "{" + entry.getKey() + ":" + entry.getValue().toString() + "}, ";
		}
		
		stateStr += "}";
		return stateStr;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(String fieldName) throws ProcessorException, BackendException {		
		return (T) fields.get(fieldName);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(long jobId, String fieldName) throws ProcessorException, BackendException {
		return (T) fields.get(fieldName);
	}

	@Override
	public Map<Object, Object> getLocalState() throws ProcessorException, BackendException {
		return localState;
	}
	
	public void put(String fieldName, Object fieldVal) {
		fields.put(fieldName, fieldVal);
	}

}
