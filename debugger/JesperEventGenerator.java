package com.king.rbea.manager.debugger;

import java.security.SecureRandom;

import com.king.constants.EventField;
import com.king.constants.EventType;
import com.king.event.Event;

public class JesperEventGenerator {
	public static SecureRandom secRandom = new SecureRandom(("" + System.currentTimeMillis()).getBytes());
	
	public static Event generateEvent() {
		return generateEvent(EventType.AppStart3);
	}
	
	public static Event generateEvent(long eventTypeId) {
		Long coreUserId = (long) secRandom.nextInt(Integer.MAX_VALUE);
		long curTime = System.currentTimeMillis();
		int flavorId = 100107;
		long uniqueId = secRandom.nextLong();
				
		JesperEvent event = new JesperEvent(curTime, flavorId, eventTypeId, uniqueId, "localhost", coreUserId);
		
		String[] fields = EventField.FIELDS_BY_EVENT_ID.get(eventTypeId);
		
		for (String field : fields) {
			String value = field;
			
			if (field.equals("coreUserId")) {
				value = Long.toString(coreUserId);
			}
			
			event.addField(value);
		}
		
		return event;
	}
	
	public Event generateEvent(long[] eventTypeIds) {
		int index = secRandom.nextInt(eventTypeIds.length);
		
		return generateEvent(eventTypeIds[index]);
	}
}