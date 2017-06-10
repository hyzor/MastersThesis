package com.king.rbea.manager.debugger.rbeacontext;

import java.util.Map;

import com.king.rbea.aggregators.SumAggregator;
import com.king.rbea.exceptions.ProcessorException;

public class JesperFakedSumAggregator implements SumAggregator {
	
	public long val = 0;
	
	public JesperFakedSumAggregator() {
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public void add(long num) {
		val++;
	}

	@Override
	public SumAggregator setDimensions(Object... dimensions) {
		return this;
	}

	@Override
	public SumAggregator setKafkaTopic(String arg0) throws ProcessorException {
		return this;
	}

	@Override
	public SumAggregator setTableName(String arg0) throws ProcessorException {
		return this;
	}

	@Override
	public SumAggregator setNamedDimensions(Map<String, ?> arg0) throws ProcessorException {
		return this;
	}
}