package com.king.rbea.manager.debugger.rbeacontext;

import java.util.HashMap;

import com.king.rbea.Aggregators;
import com.king.rbea.Context;
import com.king.rbea.Output;
import com.king.rbea.State;
import com.king.rbea.Timers;
import com.king.rbea.Utils;
import com.king.rbea.manager.scriptvalidation.FakedOutput;
import com.king.rbea.manager.scriptvalidation.FakedTimers;

public class JesperContext implements Context {
	private final State state = new JesperState(new HashMap<String, Object>());
	private final Aggregators aggregators = new JesperAggregators();
	private final Output output = new FakedOutput();
	private final Timers timers = new FakedTimers();
	
	public JesperContext() {
	}

	@Override
	public Long getCoreUserId() {
		return 1234567890L;
	}

	@Override
	public State getState() {
		return state;
	}

	@Override
	public Aggregators getAggregators() {
		return aggregators;
	}

	@Override
	public Output getOutput() {
		return output;
	}

	@Override
	public Timers getTimers() {
		return timers;
	}

	@Override
	public Utils getUtils() {
		return new Utils() {};
	}
}
