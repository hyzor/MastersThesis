package com.king.rbea.manager.debugger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.sling.commons.json.JSONArray;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import com.king.rbea.Context;
import com.king.rbea.Registry;
import com.king.rbea.scripts.ScriptUtils;
import com.king.rbea.scripts.codegen.CodeGenScriptExecutor;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ByteValue;
import com.sun.jdi.CharValue;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.FloatValue;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.InvocationException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.LongValue;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.ModificationWatchpointEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ModificationWatchpointRequest;
import com.sun.jdi.request.StepRequest;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

@WebSocket
public class JesperWebSocket {

	public static String port = "9001";

	private static VirtualMachine vm = null;
	private static ThreadReference mainThread;
	private static EventQueue eq;
	private static EventRequestManager reqMan;
	private static AttachingConnector connector;

	private static Session session;
	
	private static ThreadReference curThreadRef;
	
	private static boolean isStepping = false;
	
	// TODO: Make path relative instead
	public static final String tmpPathWin = "C:\\Dev\\tmp\\";
	private static final String groovyTmpName = "groovyTmp";
	private static final String groovyTmpFileName = groovyTmpName + ".groovy";
			
	private static ArrayList<Map.Entry<Integer, String>> output = new ArrayList<>();
	
	// rBEA stuff
	CodeGenScriptExecutor codeGenScriptExe;
	Context context;
	Registry registry;
	
	private static Class<?> groovyClass = null;
	private static Method getContextMethod = null;
	private static ClassType debuggerClassType = null;
	private static Method getLocalStatesMethod = null;
	private static boolean loadEvents = false;
	private static String eventsFileStr = "events";
	
	private static enum EvtValue {
		STATUS(1),
		WATCHPOINT(2),
		STACK(3),
		LOCATIONINFO(4),
		RBEA(5),
		RBEALOCALSTATES(6),
		VMEXIT(7),
		ERROR(8),
		OTHER(9);
		
		private final int value;
		
		private EvtValue(int value) {
			this.value = value;
		}
		
		public int getValue() {
			return value;
		}
	}
	
	private ArrayList<Integer> breakpoints = new ArrayList<Integer>();

	@OnWebSocketClose
	public void onClose(int statusCode, String reason) {
		if (vm != null) {
			vm.exit(-1);
		}
		
		System.out.println("Close: " + reason);
	}

	@OnWebSocketError
	public void onError(Throwable t) {
		System.out.println("Error: " + t.getMessage());
	}

	@OnWebSocketConnect
	public void onConnect(Session session) {
		System.out.println("Connect: " + session.getRemoteAddress().getAddress());
		
		JesperWebSocket.session = session;
		
		try {
			writeToOutput(EvtValue.OTHER.getValue(), "WebSocket connected to server. Please input script and "
					+ "set breakpoints if needed before starting VM.");
			flushOutput();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@OnWebSocketMessage
	public void onMessage(String message) {
		System.out.println("Message: " + message);
		
		// Only split first occurrence of ','
		// Split into request and message...
		String[] split = message.split(",", 2);
		String request = split[0];
		String content = split[1];
		
		switch (request) {
		case "setLoadEvents":
				loadEvents = Boolean.parseBoolean(content);
			break;
		case "setEventsFile":
				eventsFileStr = content;
		case "compileAndStart":
			try {				
				groovyClass = compileScript(content);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			try {
				output = startVM();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			
			break;
		case "reqEvent":
			Integer vmEvent = Integer.valueOf(content);
			try {
				handleReqEvent(vmEvent);
			} catch (InterruptedException | IncompatibleThreadStateException | AbsentInformationException
					| IllegalConnectorArgumentsException | IOException e1) {
				e1.printStackTrace();
			}
			break;
		case "setBreakpoint":
			String[] breakpointsStr = content.split(",");
			
			for (int i = 0; i < breakpointsStr.length; ++i) {
				Integer value = Integer.valueOf(breakpointsStr[i]);
				breakpoints.add(value);
				writeToOutput(EvtValue.STATUS.getValue(), "Successfully sent breakpoint " + value.toString() + " to the server");
			}
		break;
		default:
			break;
		}
		try {
			flushOutput();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void stepRequest(int stepDepth) {
		List<StepRequest> stepReqs = reqMan.stepRequests();
		boolean reqFound = false;
		
		for (StepRequest stepReq : stepReqs) {
			if (stepReq.thread().equals(curThreadRef)) {
				if (stepReq.depth() != stepDepth) {
					reqMan.deleteEventRequest(stepReq);
					break;
				}
				
				reqFound = true;
				break;
			}
		}
		
		if (!reqFound) {
			StepRequest stepReq = reqMan.createStepRequest(curThreadRef, StepRequest.STEP_LINE, stepDepth);
			stepReq.setSuspendPolicy(StepRequest.SUSPEND_ALL);
			stepReq.enable();
		}
	}
	
	private void handleReqEvent(Integer vmEvent) throws InterruptedException, IncompatibleThreadStateException, AbsentInformationException, IllegalConnectorArgumentsException, IOException {
			switch (vmEvent) {
			case 0:
				break;
			case 1:
				stepRequest(StepRequest.STEP_OVER);
				output = runJVM();
				break;
			case 2:
				if (isStepping) {
					List<StepRequest> stepReqs = reqMan.stepRequests();
					
					for (StepRequest stepReq : stepReqs) {
						if (stepReq.thread().equals(curThreadRef)) {
							reqMan.deleteEventRequest(stepReq);
							break;
						}
					}
					isStepping = false;
				}
				
				output = runJVM();
				break;
			case 3:
				stepRequest(StepRequest.STEP_INTO);
				output = runJVM();
				break;
			case 4:
				vm.exit(-1);
				writeToOutput(EvtValue.STATUS.getValue(), "Forcing VM exit...");
				break;
			case 5:			
				output = runJVM();
			default:
				break;
			}
	}

	private ArrayList<Map.Entry<Integer, String>> runJVM() throws InterruptedException, IncompatibleThreadStateException,
			AbsentInformationException, IllegalConnectorArgumentsException, IOException {

		if (curThreadRef.isSuspended()) {
			vm.resume();
			curThreadRef.resume();
			mainThread.resume();
		}
		
		boolean vmIsRunning = true;

		while (vmIsRunning) {
			EventSet es = eq.remove();

			for (com.sun.jdi.event.Event ev : es) {
				EventRequest evReq = ev.request();

				if (ev instanceof VMDeathEvent || ev instanceof VMDisconnectEvent) {
					writeToOutput(EvtValue.VMEXIT.getValue(), "VM exit");
					vmIsRunning = false;
					break;
				}
				
				if (ev instanceof LocatableEvent) {
					LocatableEvent locEv = (LocatableEvent) ev;
					Location locEvLoc = locEv.location();
										
					int line = locEvLoc.lineNumber();
					if (line > 0) {
						writeToOutput(EvtValue.LOCATIONINFO.getValue(),
								Integer.toString(locEvLoc.lineNumber()) + ":" + locEvLoc.sourceName());
					}
				}
				
				if (ev instanceof VMStartEvent) {
					output.add(new AbstractMap.SimpleEntry<>(EvtValue.STATUS.getValue(), "VM start"));
				} else if (ev instanceof ClassPrepareEvent) {
					ClassPrepareEvent classPrepEv = (ClassPrepareEvent) ev;
					ReferenceType refType = classPrepEv.referenceType();
					
					if(refType.name().contains(JesperDebugger.class.getName())) {
						List<Method> methods = classPrepEv.referenceType().allMethods();
						
						for (Method method : methods) {
							String methodName = method.name();
							
							if (methodName.equals("getAllContextStr")) {
								getContextMethod = method;
							} else if (methodName.equals("getLocalStates")) {
								getLocalStatesMethod = method;
							} else if (methodName.equals("exit")) {
							}
						}
						
						Iterator<ReferenceType> classes = vm.classesByName(JesperDebugger.class.getName()).iterator();
						Object debuggerObj = classes.next();
						debuggerClassType = (ClassType)debuggerObj;
						continue;
					}
					
					// Set breakpoints on the requested lines
					try {
						for (Integer breakpoint : breakpoints) {
							for (Location loc : classPrepEv.referenceType().allLineLocations()) {
								Integer lineNumber = loc.lineNumber();
								if (lineNumber.equals(breakpoint)) {
									final BreakpointRequest bpr = reqMan.createBreakpointRequest(loc);
									bpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
									bpr.enable();
									writeToOutput(EvtValue.STATUS.getValue(), "Setting breakpoint on line: " + Integer.toString(lineNumber));
									break;
								}
							}
						}
					} catch (AbsentInformationException e) {
						output.add(new AbstractMap.SimpleEntry<>(EvtValue.ERROR.getValue(), e.toString()));
					}

					List<Field> fields = refType.allFields();

					for (Field field : fields) {
						addFieldWatch(vm, refType, field.name());
						writeToOutput(EvtValue.STATUS.getValue(), "Watching field: " + field.name());
					}					

				} else if (ev instanceof ModificationWatchpointEvent) {
					ModificationWatchpointEvent modEv = (ModificationWatchpointEvent) ev;
					Value curVal = modEv.valueCurrent();
					Value nextVal = modEv.valueToBe();
					
					writeToOutput(EvtValue.WATCHPOINT.getValue(), modEv.field().name() + " changed from " + curVal + " to: " + nextVal);
					
				} else if (evReq instanceof BreakpointRequest) {
					BreakpointEvent bpEvt = (BreakpointEvent) ev;
					curThreadRef = bpEvt.thread();
					
					List<Value> contextVal = invokeDebuggerMethod(debuggerClassType, getContextMethod,
							new ArrayList<Value>(), curThreadRef);
					
					for (Value val : contextVal) {
						if (val instanceof StringReference) {
							writeToOutput(EvtValue.RBEA.getValue(), ((StringReference)val).value());
						}
					}
					
					List<Value> localStatesVal = invokeDebuggerMethod(debuggerClassType, getLocalStatesMethod,
							new ArrayList<Value>(), curThreadRef);
					
					for (Value val : localStatesVal) {
						if (val instanceof StringReference) {
							writeToOutput(EvtValue.RBEALOCALSTATES.value, ((StringReference)val).value());
						}
					}
					
					vm.suspend();
					mainThread.suspend();
					curThreadRef.suspend();
					
					List<StackFrame> frames = curThreadRef.frames();
					
					for (StackFrame frame : frames) {
						String frameStr = frame.toString();
						if (frameStr.contains(groovyClass.getName()) &&
								!frameStr.contains("auxiliary")) {
							writeStackOutput(frame, new ArrayList<ObjectReference>());
						} else if (frameStr.contains(JesperDebugger.class.getName())) {
						}
					}

					vmIsRunning = false;
					
				} else if (evReq instanceof StepRequest) {
					isStepping = true;
					StepEvent stepEv = (StepEvent) ev;
					curThreadRef = stepEv.thread();
					
					List<Value> contextVal = invokeDebuggerMethod(debuggerClassType, getContextMethod,
							new ArrayList<Value>(), curThreadRef);
					
					for (Value val : contextVal) {
						if (val instanceof StringReference) {
							writeToOutput(EvtValue.RBEA.getValue(), ((StringReference)val).value());
						}
					}
					
					List<Value> localStatesVal = invokeDebuggerMethod(debuggerClassType, getLocalStatesMethod,
							new ArrayList<Value>(), curThreadRef);
					
					for (Value val : localStatesVal) {
						if (val instanceof StringReference) {
							writeToOutput(EvtValue.RBEALOCALSTATES.value, ((StringReference)val).value());
						}
					}

					vm.suspend();
					mainThread.suspend();
					curThreadRef.suspend();
					
					List<StackFrame> frames = curThreadRef.frames();
					
					for (StackFrame frame : frames) {
						String frameStr = frame.toString();
						if (frameStr.contains(groovyClass.getName()) &&
								!frameStr.contains("auxiliary")) {
							writeStackOutput(frame, new ArrayList<ObjectReference>());
						}
					}
					
					vmIsRunning = false;
				} else if (ev instanceof MethodEntryEvent) {
					MethodEntryEvent methodEntryEv = (MethodEntryEvent) ev;
					Method method = methodEntryEv.method();
					
					writeToOutput(EvtValue.STATUS.getValue(), "Entering method " + method.name());
				}
			}

			es.resume();
		}

		return output;
	}
	
	private List<Value> invokeDebuggerMethod(ClassType classType, Method debuggerMethod,
			List<? extends Value> values, ThreadReference threadRef) {
		Value val = null;
		
		try {
			val = classType.invokeMethod(threadRef, debuggerMethod, values, ObjectReference.INVOKE_SINGLE_THREADED);
		} catch (InvalidTypeException | ClassNotLoadedException | InvocationException | IncompatibleThreadStateException e) {
			e.printStackTrace();
		}
		
		ObjectReference objRef = (ObjectReference) val;
		Value elements = objRef.getValue(objRef.referenceType().fieldByName("elementData"));
		ArrayReference arrayRef = (ArrayReference) elements;
		
		return arrayRef.getValues();
	}
	
	private void objRefIteration(String varName, ObjectReference objRef, ObjectReference prevObjRef,
			ArrayList<ObjectReference> objRefsProcessed, ArrayList<String> output, int depth) {
		ReferenceType refType = objRef.referenceType();
		objRefsProcessed.add(objRef);
		
		String objStr = "";
		
		// Depth indentation
		for (int i = 0; i < depth; ++i) {
			objStr += "-----";
		}
		
		// Add variable name and its type to output
		objStr += " (" + refType.name() + ")=";
		
		List<Field> fields = refType.allFields();
		
		// If there are no more fields here, this object reference is a leaf node
		if (fields.isEmpty()) {
			
			if (objRef instanceof ArrayReference) {
				ArrayReference arrayRef = (ArrayReference) objRef;
				List<Value> arrVals = arrayRef.getValues();
				
				objStr += "{";
				
				for (Value val : arrVals) {
					if (val == null){
						objStr += "null";
					} else {
						objStr += val.toString();
					}
					
					objStr += ", ";
				}
				
				objStr += "}";
			} else {
				objStr += "(Unsupported type)";
			}
		}
		
		// If depth is 0, we are entering a new variable
		if (depth == 0) {
			output.add("==============================");
			output.add(varName + ": " + objStr);
			output.add("==============================");
		} else {
			output.add(objStr);
		}
		
		// Proceed traversing child elements of this object reference
		for (Field field : fields) {
			Value fieldVal = objRef.getValue(field);
			String outputField = "";
			for (int i = 0; i < depth; i++) {
				outputField += "-----";
			}
			
			outputField += field.name() + ": ";
			try {
				outputField += "(" + field.type().name() + ")";
			} catch (ClassNotLoadedException e) {
				e.printStackTrace();
			}
			
			// Try to get field value as a string representation
			// We check if it's a string, integer, long and so on 
			// and convert it to a string if necessary
			String fieldValStr = getValueStr(fieldVal);
			
			// If we failed to get a value, we traverse the child nodes (if any) if it is
			// an object reference
			if (fieldValStr == null) {
				if (fieldVal instanceof ObjectReference) {
					ObjectReference fieldObjRef = (ObjectReference) fieldVal;
					ReferenceType fieldRefType = fieldObjRef.referenceType();
					
					System.out.println(varName + ": " + refType.name() + " ----- " + fieldRefType.name());
					
					output.add(outputField);
					
					if (!fieldObjRef.equals(prevObjRef) && !objRefsProcessed.contains(fieldObjRef)) {
						objRefIteration(varName, fieldObjRef, objRef, objRefsProcessed, output, depth + 1);
					}
				
				// Not an object reference, or any convertible value, just display its own
				// string representation (or null if it's actually just null)
				} else {
					if (fieldVal != null) {
						fieldValStr = " (Value: " + fieldVal.toString() + ")";
					} else {
						fieldValStr = " (Value: null)";
					}
					
					outputField += fieldValStr;
					output.add(outputField);
				}
			} else {
				outputField += fieldValStr;
				output.add(outputField);
			}
		}
	}
	
	private String getValueStr(Value val) {
		String valueStr = null;
		
		if (val instanceof StringReference) {
			valueStr = " (Value: " + ((StringReference)val).value() + ")";
		} else if (val instanceof IntegerValue) {
			valueStr = " (Value: " + Integer.toString(((IntegerValue)val).value()) + ")";
		} else if (val instanceof LongValue) {
			valueStr = " (Value: " + Long.toString(((LongValue)val).value()) + ")";
		} else if (val instanceof FloatValue) {
			valueStr = " (Value: " + Float.toString(((FloatValue)val).value()) + ")";
		} else if (val instanceof CharValue) {
			valueStr = " (Value: " + String.valueOf(((CharValue)val).value()) + ")";
		} else if (val instanceof ByteValue) {
			String byteStr = new String(new byte[]{((ByteValue)val).value()});
			valueStr = " (Value: " + byteStr + ")";
		} else if (val instanceof BooleanValue) {
			valueStr = " (Value: " + Boolean.toString(((BooleanValue)val).value()) + ")";
		}
		
		return valueStr;
	}
	
	private void writeStackOutput(StackFrame stack, ArrayList<ObjectReference> objRefsProcessed) throws AbsentInformationException {
		List<LocalVariable> visVars = stack.visibleVariables();
		ArrayList<String> tmpOutput = new ArrayList<String>();
		for (LocalVariable visibleVar : visVars) {
			Value val = stack.getValue(visibleVar);
			String name = visibleVar.name();
			
			String valStr = getValueStr(val);
			
			if (valStr == null) {
				if (val instanceof ObjectReference) {
					objRefIteration(name, (ObjectReference)val, null, objRefsProcessed, tmpOutput, 0);
					
					for (String outStr : tmpOutput) {
						writeToOutput(EvtValue.STACK.getValue(), outStr);
					}
					
					tmpOutput.clear();
					
				} else {
					if (val != null) {
						valStr = " (Value: " + val.toString() + ")";
					} else {
						valStr = " (Value: null)";
					}
				}
			} else {
				writeToOutput(EvtValue.STACK.getValue(), valStr);
			}
		}
	}

	private void startRemoteJVM(boolean redirectStream) throws Exception {		
		String debugFlags = "-agentlib:jdwp=transport=dt_socket,address=localhost:" + port
				+ ",server=y,suspend=y";
		
		// TODO: Remove evil hard-coded path
		ProcessBuilder processBuilder = new ProcessBuilder("java", debugFlags, "-cp", "C:\\Dev\\Git\\rbea-on-flink\\target\\rbea-on-flink-1.0-SNAPSHOT.jar",
				"com.king.rbea.manager.debugger.JesperDebugger", tmpPathWin, groovyTmpFileName, Boolean.toString(loadEvents), eventsFileStr);
		processBuilder.directory(new File(tmpPathWin));
		Map<String, String> env = processBuilder.environment();
		env.put("JAVA_OPTS", debugFlags);
		processBuilder.redirectErrorStream(redirectStream);
		processBuilder.start();
	}
	
	private Class<?> compileScript(String script) throws Exception {
		File file = new File(tmpPathWin + groovyTmpFileName);
		file.getParentFile().mkdirs();
		
		String[] splits = script.split("\n");
		PrintWriter writer = new PrintWriter(file, "UTF-8");
		
		for (String split : splits) {
			writer.println(split);
		}
		
		writer.close();
		
		// Compile for rBEA
		CompilerConfiguration compilerConfig;
		compilerConfig = ScriptUtils.getCompilerConfig();
		compilerConfig.setDebug(true);
		compilerConfig.setVerbose(true);
		
		GroovyShell shell = new GroovyShell(new Binding(), compilerConfig);
		Script scriptObj = shell.parse(script);
		scriptObj.run();
		Class<?> groovyClass = scriptObj.getMetaClass().getTheClass();
		
		return groovyClass;
	}
	
	private ArrayList<Map.Entry<Integer, String>> startVM() throws Exception {
		output.clear();
		
		startRemoteJVM(true);
		connector = getConnector();
		vm = connect(connector, port);

		// Lookup main thread
		List<ThreadReference> threads = vm.allThreads();
		for (ThreadReference thread : threads) {
			if ("main".equals(thread.name())) {
				mainThread = thread;
				break;
			}
		}
		
		curThreadRef = mainThread;
		
		addClassWatch(vm, groovyClass.getName());
		addClassWatch(vm, JesperDebugger.class.getName());

		mainThread.suspend();
		curThreadRef.suspend();
		vm.suspend();

		eq = vm.eventQueue();
		reqMan = vm.eventRequestManager();
		
		writeToOutput(EvtValue.STATUS.getValue(), "VM started in suspended mode...");
		return output;
	}
	
	private void writeToOutput(int key, String value) {
		output.add(new AbstractMap.SimpleEntry<>(key, value));
	}
	
	private void flushOutput() throws IOException {
		JSONArray jsonArray = new JSONArray(output);
		String jsonString = jsonArray.toString();
		session.getRemote().sendString(jsonString);
		output.clear();
	}

	private AttachingConnector getConnector() {
		VirtualMachineManager vmManager = Bootstrap.virtualMachineManager();
		AttachingConnector attachConnector = null;

		for (Connector connector : vmManager.attachingConnectors()) {
			if ("com.sun.jdi.SocketAttach".equals(connector.name())) {
				attachConnector = (AttachingConnector) connector;
				break;
			}
		}

		if (attachConnector == null)
			throw new IllegalStateException();

		return attachConnector;
	}

	private VirtualMachine connect(AttachingConnector connector, String port)
			throws IllegalConnectorArgumentsException, IOException {
		Map<String, Connector.Argument> env = connector.defaultArguments();
		env.get("port").setValue(port);
		env.get("hostname").setValue("localhost");

		return connector.attach(env);
	}
	
	private static void addClassWatch(VirtualMachine vm, String className) {
		EventRequestManager erm = vm.eventRequestManager();
		ClassPrepareRequest classPrepReq = erm.createClassPrepareRequest();
		classPrepReq.addClassFilter(className);
		classPrepReq.setEnabled(true);
	}

	private static void addFieldWatch(VirtualMachine vm, ReferenceType refType, String fieldName) {
		EventRequestManager erm = vm.eventRequestManager();
		com.sun.jdi.Field field = refType.fieldByName(fieldName);
		ModificationWatchpointRequest modificationWatchpointRequest = erm.createModificationWatchpointRequest(field);
		modificationWatchpointRequest.setEnabled(true);
	}
}
