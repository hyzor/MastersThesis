package com.king.rbea.manager.debugger.rbeacontext;

import java.util.Map;

import com.king.rbea.aggregators.Counter;
import com.king.rbea.exceptions.ProcessorException;

public class JesperFakedCounter implements Counter {
	public long val = 0;
	
	public JesperFakedCounter() {
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public String toString() {
		return Long.toString(val);
	}
	
	@Override
	public void increment() {
		val++;
	}
	@Override
	public void decrement() {
		val--;
	}
	@Override
	public Counter setDimensions(Object... dimensions) {
		return this;
	}
	
	@Override
	public Counter setKafkaTopic(String arg0) throws ProcessorException {
		return this;
	}
	@Override
	public Counter setTableName(String arg0) throws ProcessorException {
		return this;
	}

	@Override
	public Counter setNamedDimensions(Map<String, ?> arg0) throws ProcessorException {
		return this;
	}
}