var ws = new WebSocket("ws://localhost:8999/");
var codeMirrorCurLine = null;
var curLine = -1;

var messagesArea = document.getElementById("messages");
var statusMessagesArea = document.getElementById("statusTextArea");
var watchpointMessagesArea = document.getElementById("watchpointTextArea");
var stackMessagesArea = document.getElementById("stackTextArea");
var rBEATextArea = document.getElementById("rBEATextArea");
var rBEALocalStatesArea = document.getElementById("rBEALocalStatesArea");

var curLineText = document.getElementById("curLineText");
var curSourceText = document.getElementById("curSourceText");

var startVmBtn = document.getElementById("startVmBtn");
var runVmBtn = document.getElementById("runVmBtn");
var stepVmBtn = document.getElementById("stepVmBtn");
var stepIntoVmBtn = document.getElementById("stepIntoVmBtn");
var exitVmBtn = document.getElementById("exitVmBtn");

var codeTextArea = document.getElementById("codeTextArea");

runVmBtn.disabled = true;
stepVmBtn.disabled = true;
stepIntoVmBtn.disabled = true;
exitVmBtn.disabled = true;

ws.onopen = function() {
  $("#messages").append("WebSocket opened</br>");
}

ws.onmessage = function(evt) {
  var jsonData = JSON.parse(evt.data);

  for (var element in jsonData) {
    var split = jsonData[element].split(/=(.+)?/); // Only split first occurence of '='
    var key = split[0];
    var value = split[1];

    switch (key) {
      case "1":
        statusTextArea.innerHTML += value + "\n";
        break;
      case "2":
        watchpointTextArea.innerHTML += value + "\n";
        break;
      case "3":
        stackTextArea.innerHTML += value + "\n";
        break;
      case "4":
        var lineAndSource = value.split(/:(.+)?/); // Only split first occurence of ':'
        var line = lineAndSource[0];
        var sourceName = lineAndSource[1];

        curLine = line;
        codeMirrorCurLine = myCodeMirror.addLineClass(curLine - 1, 'background', 'line-current');
        curLineText.innerHTML = curLine;
        curSourceText.innerHTML = sourceName;
        break;
      case "5":
        rBEATextArea.innerHTML += value + "\n";
        break;
      case "6":
        rBEALocalStatesArea.innerHTML += value + "\n";
        break;
      case "7":
        startVmBtn.disabled = false;
        runVmBtn.disabled = true;
        stepVmBtn.disabled = true;
        stepIntoVmBtn.disabled = true;
        exitVmBtn.disabled = true;
        statusTextArea.innerHTML += value + "\n";
        break;
      default:
        messagesArea.innerHTML += value + "</br>";
        break;
    }
  }
}

ws.onclose = function() {
  messagesArea.innerHTML += "WebSocket closed</br>";
}

ws.onerror = function(err) {
  messagesArea.innerHTML += "Error: " + err + "</br>";
}

ws.sendhello = function() {
  messagesArea.innerHTML += "Hello Server from sendhello()</br>";
}

ws.startVmBtnClick = function() {
  runVmBtn.disabled = false;
  var script = myCodeMirror.getValue();
  ws.send("compileAndStart," + script);
}

ws.runVmBtnClick = function() {
  ws.prepareForNextStep();
  startVmBtn.disabled = true;
  stepVmBtn.disabled = false;
  stepIntoVmBtn.disabled = false;
  exitVmBtn.disabled = false;
  ws.send("reqEvent,2");
};

ws.stepVmBtnClick = function() {
  ws.prepareForNextStep();
  ws.send("reqEvent,1");
};

ws.stepIntoVmBtnClick = function() {
  ws.prepareForNextStep();
  ws.send("reqEvent,3");
};

ws.exitVmBtnClick = function() {
  ws.send("reqEvent,4");
};

ws.setBreakpointsBtnClick = function() {
  var breakpointsValue = document.getElementById("breakpointInput").value;
  ws.send("setBreakpoint," + breakpointsValue);
};

ws.prepareForNextStep = function() {
  if (codeMirrorCurLine != null)
    myCodeMirror.removeLineClass(codeMirrorCurLine, 'background', 'line-current');

    stackTextArea.innerHTML = "";
    rBEATextArea.innerHTML = "";
    rBEALocalStatesArea.innerHTML = "";
}

codeTextArea.innerHTML =
"// Method called for each event, feel free to add/remove arguments\n\
// such as Context, State, Aggregators, semantic classes and more!\n\
@ProcessEvent\n\
def printType(Event event, Aggregators agg, Output out) {\n\
  if (event.eventType == EventType.EquipoGameSessionStart) {\n\
  def myAgg = agg.getCounter(\"myAgg\", MINUTES_1);\n\
  myAgg.setDimensions(\"myAggDim\").increment();\n\
}\n\
  out.print(\"Event: \" + event.getEventType())\n\
}\n\
\n\
// You can have multiple methods annotated with @ProcessEvent!\n\
\n\
// Method called once before the processing starts\n\
def initialize(Registry registry) {\n\
  // Register new fields..\n\
  // Register callbacks...\n\
}";

var myCodeMirror = CodeMirror.fromTextArea(codeTextArea,
{mode: "groovy",
styleActiveLine: true,
lineNumbers: true,
lineWrapping: true
});

startVmBtn.onclick = ws.startVmBtnClick;
runVmBtn.onclick = ws.runVmBtnClick;
stepVmBtn.onclick = ws.stepVmBtnClick;
stepIntoVmBtn.onclick = ws.stepIntoVmBtnClick;
exitVmBtn.onclick = ws.exitVmBtnClick;
setBreakpointsBtn.onclick = ws.setBreakpointsBtnClick;
