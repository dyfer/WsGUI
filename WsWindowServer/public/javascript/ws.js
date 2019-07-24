//var wsPort = 9999; //in a separate file now, set from SC
//var discMessage = "this will show up after loosing connection to websockets" //as above
/*
Nexus scratch
creating
var widget = nx.add(nxId, {
x: mousex-25,
y: mousey-25,
parent: 'nxui'
})
globaldragid = widget.canvasID;
var thisname = widget.canvasID;
window[widget.canvasID] = widget;
showSettings();
nx.add( type, settings )
Adds a NexusUI element to the webpage. This will create an HTML5 canvas and draw the interface on it.type   string   NexusUI widget type (i.e. “dial”).
settings   object   (Optional.) Extra settings for the new widget. This settings object may have any of the following properties: x (integer in px), y, w (width), h (height), name (widget’s OSC name and canvas ID), parent (the ID of the element you wish to add the canvas into). If no settings are provided, the element will be at default size and appended to the body of the HTML document.

jQUery scratch
{title: "new title"}
b = document.createElement("div")
<div>​</div>​
$(b).append($('<input type="range" data-vertical="true">'))
[<div>​…​</div>​]
$(b).trigger("create") //doesn't work here
[<div>​…​</div>​]
document.body.appendChild(b)
<div>​<input type=​"range" data-vertical=​"true">​</div>​
$(b).trigger("create")
[<div>​…​</div>​]
*/
var initialMsg = true;
var isConnected = false;
var isChecking = false;
var checking;
var ws;
var documentID;
var blinkers = Array();

function checkWwwConnection(){
  var req = new XMLHttpRequest();
  req.onload = function(){
    console.log("req.status: ", req.status);
    if(req.status == 200){ //refresh if connection status is OK (200) - means the file exists
      isChecking = false;
      // console.log("I'm refreshing now");
      location.reload();
    }
  }
  console.log("checking connection")
  isChecking = true;
  // var url = document.location + "ws.js"; //check for the ws.js file (this file) existence
  var url = window.location.href; //check for this address
  req.open('GET', url += ((/\?/).test(url) ? "&" : "?") + (new Date()).getTime(), true); //checking for server connection with a timestamp
  // req.open('GET', url += ((/\?/).test(url) ? "&" : "?"), false); //checking for server connection with a timestamp
  // req.open('GET', url, false); //checking wether file exists
  // req.send();
  try {
    req.send();
  } catch (ex) {
    console.log(ex)
  }

  // console.log(req);
  // console.log(status);
}

openWS(); //start

function onWsOpen() {
  document.body.innerHTML = "WebSocket connected.";
  isConnected = true;
  clearInterval(checking);
  isChecking = false;
}

function onWsClose() {
  // document.body.innerHTML = "Socket connection closed, interface cleared";
  // document.body.style.background = "#ffffff"; //white background
  // document.body.innerHTML = discMessage;
  //add styke for animation frames
  blinkers.forEach(function(item) {clearInterval(item)})
  var css = document.createElement("style");
  css.type = "text/css";
  css.innerHTML = "@-webkit-keyframes glow { 50% {background-color: white;}}";
  document.body.appendChild(css);
  var msgWidget = document.createElement('div');
  msgWidget.innerHTML = "Connection lost, reconnecting...";
  msgWidget.setAttribute("style", "background-color: red; position: absolute;  bottom: 4px; right: 4px; text-align: right; -webkit-animation: glow 2s infinite alternate;");
  document.body.appendChild(msgWidget);
  console.log(isChecking);
  if(!isChecking) {
    // console.log("!isChecking");
    checking = setInterval( function(){checkWwwConnection()},2000);
  }
  isConnected = false;
}


function openWS() {
  try {

    var host = "ws://" + window.location.hostname + ":" + window.location.port + window.location.pathname;
    console.log("Host:", host);

    ws = new WebSocket(host);
    // document.body.innerHTML = "Establishing WebSocket connection on port " + wsPort + "..."; //not working here
    ws.onopen = function (param) {
      onWsOpen();
      console.log("Socket opened.");
      // ws.send(["blah", 0]) //test
      //maybe clear page body here?
      initialMsg = true;
    };

    ws.onclose = function (param) {
      onWsClose();
      console.log("Socket closed.");
    };

    ws.onmessage = function (param) {
      // console.log("Socket full:", param);
      console.log("Socket message:", param.data);
      var inMsg = JSON.parse(param.data);
      console.log("Incoming object: ", inMsg);
      var command = inMsg.command;
      var id = inMsg.id;
      delete inMsg.command;
      delete inMsg.id;
      switch(command){
        case "add":
        addWidget(id, inMsg);
        break;
        case "remove":
        removeWidget(id);
        break;
        case "update":
        updateWidget(id, inMsg);
        break;
        case "run":
        eval(inMsg.code) //this is unsafe I think...
        break;
      }
    };

    ws.onerror = function (er) {
      console.log("Socket error:", er);
      document.body.innerHTML = "Socket error: " + er;
    };

  } catch (ex) {
    console.log("Socket exception:", ex);
    // checking = setInterval( function(){checkWwwConnection()},3000);
  }
}

var addWidget = function(id, params) {
  //clear the initial text
  if(initialMsg) {
    document.body.innerHTML = "";
  };
  initialMsg = false;
  console.log("addingWidget");
  var kind = params.kind;
  delete params.kind;
  var thisWidget;
  console.log("kind: ", kind);
  switch(kind) {
    case "button":
    // thisWidget = document.createElement('input');
    // thisWidget.setAttribute('type', 'button');
    thisWidget = document.createElement('button');
    var isPressed = false; //to avoid multiple clicks on mousedown ontouchstart
    var isTouchScreen = false; //set device type depending on first input... is this a good workaround?

    // thisWidget.onclick = function(){ws.send([id, 0])}; //simpler for now, compatible with all devices

    //following bevavior needs revisiting!!!!

    thisWidget.ontouchstart = function(){ //touch is much faster on touchscreen than onmousedown
      isTouchScreen = true;
      // if(!isPressed) {
      // ws.send([id, 1]);
      // isPressed = true;
      ws.send([id, 0]);
      // }
    };
    thisWidget.onmousedown = function(){
      // if(!isPressed) {
      if(!isTouchScreen) { //blocks secondary clicks from touchscreen devices
        // ws.send([id, 1]);
        // isPressed = true;
        ws.send([id, 0]);
      }
    };
    break;
    case "slider":
    // thisWidget = document.createElement('input');
    // thisWidget.setAttribute('type', 'range');
    // thisWidget = nx.add('slider'); //width height here? {w:90%, h:10%}
    // thisWidget.oninput = function(){ws.send([id, thisWidget.value])};
    // thisWidget.on('*', function(obj){ws.send([id, thisWidget.val.value])});
    //jQueryMobile slider
    thisWidget = document.createElement('div');
    // thisWidget.setAttribute('class', 'ui-slider');
    // $(thisWidget).append($('<input type="range" class="ui-hidden-accessible"  min = "0.0" max = "1.0" step = "0.0001" data-vertical="' + params.vertical + '">'))
    // $(thisWidget).trigger("create")
    //sending commands below
    break;
    case "input":
    thisWidget = document.createElement('input');
    thisWidget.setAttribute('type', 'text');
    // thisWidget.onclick = function(){ws.send([id, thisWidget.value])}; //not sure if we want it
    thisWidget.oninput = function(){ws.send([id, thisWidget.value])};
    // $(thisWidget).trigger("create")
    //sending commands here
    break;
    case "image":
    thisWidget = document.createElement('img');
    var isPressed = false; //to avoid multiple clicks on mousedown ontouchstart
    thisWidget.onclick = function(){ws.send([id, 0])};
    break;
    // case "knob": //this will require additional js library probably?
    // break;
    case "body": //main body of the document, for background styling for now
    thisWidget = document.body; //should be enough to style it?
    break;
    case "title": //document; can't be assigned an id
    // thisWidget = document;
    // documentID = id;
    break;
    case "text":
    thisWidget = document.createElement('div');
    break;
    case "checkbox":
    thisWidget = document.createElement('input');
    thisWidget.setAttribute('type', 'checkbox');
    thisWidget.onchange = function(){ws.send([id, thisWidget.checked ? 1 : 0])};
    break;
    case "menu":
    thisWidget = document.createElement('select');
    thisWidget.onchange = function(){ws.send([id, thisWidget.value])};
    break;
  };
  // console.log("after case: ", thisWidget);
  if(kind!="title") {
    thisWidget.setAttribute('id',id);
  }

  // updateWidget(id, params); //this was not working, there was no widget at the id yet
  updateWidgetObj(thisWidget, params);
  if((kind != "body") && (kind != "title")) {
    document.body.appendChild(thisWidget)
  }
  //now jQuery stuff
  switch(kind) {
    case 'slider':
    $(thisWidget).append($('<input type="range" class="ui-hidden-accessible"  min = "0.0" value = "' + params["value-slider"] + '" max = "1.0" step = "0.0001" data-vertical="' + params.vertical + '" data-theme="a" data-highlight="true">'))
    console.log("params.vertical: ", params.vertical)
    // $(thisWidget).append($('<input type="range" class="ui-hidden-accessible">'))
    // $(thisWidget).append($('<input type="range">'))
    $(thisWidget).trigger("create")
    // console.log("thisW:", thisWidget)
    thisSlider = thisWidget.children[0].children[0]
    $(thisSlider).on("input change", function() {
      // $(thisSlider).on("changed", function() {
      //     // ws.send([id, $(thisSlider).val()]);
      ws.send([id, $(document.getElementById(id).children[0].children[0]).val()]);
      // console.log("val: ", $(document.getElementById(id).children[0].children[0]).val())
    })
    updateWidgetObj(thisWidget, params); //update once again when objects are ready.... workaround
    break;
  }
}

var removeWidget = function(id) {
  console.log("removing widget");
  var thisWidget = document.getElementById(id);
  thisWidget.parentNode.removeChild(thisWidget);
  clearInterval(blinkers[id])
  // var kind = params.kind;
  // var id = params.id;
  // document.createElement('button');
}

var updateWidgetObj = function(thisWidget, params) {
  // console.log("updating widget");
  // console.log(params);
  if(params.title) {
    document.title = params.title;
  } else {
    for(var key in params){
      var attrValue = params[key];
      if(key == 'innerHTML') {
        thisWidget.innerHTML = attrValue;
      } else if (key == 'value') {
        thisWidget.value = attrValue;
        // console.log("setting value");
      } else if (key == 'menuItems') {
        var menuLength = thisWidget.options.length;
        for (var i = 0; i < menuLength; i++) {
          console.log("removing ", i);
          thisWidget.options.remove(menuLength - i - 1);
        }
        attrValue = attrValue.split(',');
        for (var i = 0; i < attrValue.length; i++) {
          var option = document.createElement("option");
          option.text = attrValue[i];
          option.value = i;
          thisWidget.options.add(option);
        }
      } else if (key == 'checked') {
        thisWidget.checked = parseInt(attrValue);
        // console.log("is checked");
      } else if (key == 'value-slider') {
        // console.log("slider value", attrValue);
        // if($(thisWidget.children[0].children[0]).data("type") != null) {
        setVal = function() {
          $(thisWidget.children[0].children[0]).val(attrValue)
          // $(thisWidget.children[0].children[0]).refresh()
          $(thisWidget.children[0].children[0]).slider("refresh")
        }
        if($(thisWidget).children().size() > 0) {
          setVal()
        }
      } else if (key == 'background-color-slider') {
        // console.log("slider value", attrValue);
        setVal = function() {
          $(thisWidget.children[0].children[1].children[0]).css("background-color", attrValue)
        }
        if($(thisWidget).children().size() > 0) {
          setVal()
        }
      } else if (key == 'background-blink') {
        // console.log("background-flicker value", attrValue);
        attrValue = attrValue.replace(/(\[|\])/g, "").split(",");
        // console.log("background-flicker value", attrValue);
        var time = eval(attrValue[0]);
        var color0 = attrValue[1];
        var color1 = attrValue[2];
        var period = eval(attrValue[3]);
        // console.log("time", time);
        // console.log("colo0", color0);
        // console.log("thisWidget.id:", thisWidget.id);
        console.log("period:", period);
        // var flipFlop = true;
        function changeColor1(){
          thisWidget.style.backgroundColor = color1;
        };
        function changeColor() {
          // if (flipFlop) {
            // console.log(color1)
          thisWidget.style.backgroundColor = color0;
          // thisWidget.setAttribute('background-color', color1);
          setTimeout(changeColor1, time * period);
          // flipFlop = false;
        }
        clearInterval(blinkers[thisWidget.id])
        if (time > 0) {
          blinkers[thisWidget.id] = setInterval(changeColor, time);
        }
        // setVal = function() {
        //     $(thisWidget.children[0].children[1].children[0]).css("background-color", attrValue)
        // }
        // if($(thisWidget).children().size() > 0) {
        //     setVal()
        // }
      } else {
        thisWidget.setAttribute(key, attrValue);
      }
    }
  }

  // var kind = params.kind;
  // var id = params.id;
  // document.createElement('button');
}

var updateWidget = function(id, params) {
  updateWidgetObj(document.getElementById(id), params);
}
