package com.king.rbea.manager.debugger;

public class JesperInput {
	//public final String script;
	public final String reqEvent;

	public JesperInput() {
		this(null);
	}

	public JesperInput(String reqEvent) {
		this.reqEvent = reqEvent;
	}
}
