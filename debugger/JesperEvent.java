package com.king.rbea.manager.debugger;

import com.king.event.TypedEventFieldAccessor;

import java.util.ArrayList;
import java.util.List;

import com.king.event.Event;

public class JesperEvent extends TypedEventFieldAccessor implements Event {
	
	private final long timeStamp;
	private final int flavourId;
	private final long eventType;
	private final long uniqueId;
	private final String hostname;
	private final long uAcid;
	private final List<String> fields;
	
	public JesperEvent(long timeStamp, int flavourId, long eventType, long uniqueId, String hostname, long uAcid) {
		this.timeStamp = timeStamp;
		this.flavourId = flavourId;
		this.eventType = eventType;
		this.uniqueId = uniqueId;
		this.hostname = hostname;
		this.uAcid = uAcid;
		fields = new ArrayList<String>();
	}

	@Override
	public Iterable<String> fields() {
		return fields;
	}

	@Override
	public boolean getBoolean(int index) {
		return Boolean.parseBoolean(getString(index));
	}

	@Override
	public double getDouble(int index) {
		return Double.parseDouble(getString(index));
	}

	@Override
	public long getEventType() {
		return eventType;
	}

	@Override
	public int getFlavourId() {
		return flavourId;
	}

	@Override
	public float getFloat(int index) {
		return Float.parseFloat(getString(index));
	}

	@Override
	public String getHostname() {
		return hostname;
	}

	@Override
	public int getInt(int index) {
		return Integer.parseInt(getString(index));
	}

	@Override
	public long getLong(int index) {
		return Long.parseLong(getString(index));
	}

	@Override
	public String getString(int index) {
		return fields.get(index);
	}

	@Override
	public long getTimeStamp() {
		return timeStamp;
	}

	@Override
	public long getUacid() {
		return uAcid;
	}

	@Override
	public long getUniqueId() {
		return uniqueId;
	}
	
	public void addField(String field) {
		fields.add(field);
	}

}
