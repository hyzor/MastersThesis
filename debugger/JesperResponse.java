package com.king.rbea.manager.debugger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JesperResponse {
  //public final String out;
  public final ArrayList<String> output;
  //public final List<Map<Integer, String>> outputMapList;

  JesperResponse() {
    this(null);
  }

  /*public JesperResponse(Collection<Map<Integer, String>> values) {
	  this.outputMapList = values == null ? Collections.emptyList() : new ArrayList<>(values);
  }*/
  
  public JesperResponse(ArrayList<String> output) {
	  this.output = output;
  }
}
