package com.king.rbea.manager.debugger;

import java.util.ArrayList;

import com.king.rbea.Field;
import com.king.rbea.Registry;
import com.king.rbea.exceptions.ProcessorException;
import com.king.rbea.fields.FieldUpdateCallback;

public class JesperRegistry implements Registry {
	public final ArrayList<Field<?>> fields = new ArrayList<Field<?>>();

	@Override
	public void registerField(Field<?> arg0) throws ProcessorException {
		fields.add(arg0);
	}

	@Override
	public void registerFieldDependency(long arg0, String arg1) throws ProcessorException {

	}

	@Override
	public void registerUpdateCallback(String arg0, FieldUpdateCallback<?> arg1) throws ProcessorException {

	}

	@Override
	public void registerUpdateCallback(long arg0, String arg1, FieldUpdateCallback<?> arg2) throws ProcessorException {

	}

}
