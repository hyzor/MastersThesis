package com.king.rbea.manager.debugger.rbeacontext;

import java.util.HashMap;
import java.util.Map;

import com.king.rbea.Aggregators;
import com.king.rbea.aggregators.AggregationWindow;
import com.king.rbea.aggregators.Counter;
import com.king.rbea.aggregators.OutputType;
import com.king.rbea.aggregators.SumAggregator;

public class JesperAggregators implements Aggregators {
	private final Map<Object, Counter> counters = new HashMap<>();
	private final Map<Object, SumAggregator> sumAggregators = new HashMap<>();
	
	public JesperAggregators() {
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public Counter getCounter(String name, AggregationWindow windowSize) {
		if (counters.get(name) == null) {
			counters.put(name, new JesperFakedCounter());
		}
		
		return counters.get(name);
	}
	
	public final Map<Object, Counter> getCounters() {
		return counters;
	}
	
	public final Map<Object, SumAggregator> getSumAggs() {
		return sumAggregators;
	}
	
	@Override
	public Counter getCounter(String name, AggregationWindow windowSize, OutputType outputType) {
		return getCounter(name, windowSize);
	}
	
	@Override
	public SumAggregator getSumAggregator(String name, AggregationWindow windowSize) {
		if (sumAggregators.get(name) == null) {
			sumAggregators.put(name, new JesperFakedSumAggregator());
		}
		return sumAggregators.get(name);
	}
	
	@Override
	public SumAggregator getSumAggregator(String name, AggregationWindow windowSize, OutputType outputType) {
		return getSumAggregator(name, windowSize);
	}
}