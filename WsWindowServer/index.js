const express = require('express')
const app = express()
const expressWs = require('express-ws')(app)
const path = require('path')
const OSC = require('osc-js')

var webPort; //  argv setting
var oscSendAddr;// argv setting
var oscSendPort; // argv setting
var oscReceivePort = 0; //note: receive port can be passed as an argument, otherwise an available port will be chosen (and sent back through OSC under [/oscSendAddr/webPort 'oscport' oscReceivePort])

var redirectWindow = undefined // = 'first'
var windows = new Set()
var activeWindows = new Set()
// var routers = {}
// var wsInstances = {}
var wsClients = {}

// handle exceptions
process.on('uncaughtException', function(err) {
  console.log("uncaughtException in WsWindowServer:");
  console.log((err && err.stack) ? err.stack : err);
});

//receiving osc
// /add <windowName>
// /remove <windowName>
// /'xxx.xxx.xxx.xxx:port' <json> //routed to websocket cliets

//sending osc
// /oscSendAddr/webPort 'oscport' oscReceivePort //at init time
// /oscSendAddr/window/<windowName> 'data' 'xxx.xxx.xxx.xxx:port' <dataFromClient> //'xxx.xxx.xxx.xxx:port' is web client address


//usage
//node index.js webPort oscSendToPort /oscSendAddr (optional oscReceivePort)
var args = process.argv;
if (process.argv.length < 5) {
  console.log("Welcome to WebSocket OSC bridge. Usage:");
  console.log("node index.js webPort oscSendToPort, oscSendAddr");
  process.exit(0);
}

webPort = args[2];
oscSendPort = args[3];
oscSendAddr = args[4];

if(args[5]) {
  oscReceivePort = args[5]
}

// send osc addr gets webPort appended
var mainOscSendAddr = oscSendAddr + '/' + webPort

//set up OSC
const oscConfig = {
  type: 'udp4',         // @param {string} 'udp4' or 'udp6'
  open: {
    host: 'localhost',    // @param {string} Hostname of udp server to bind to
    port: oscReceivePort,          // @param {number} Port of udp server to bind to
    exclusive: false      // @param {boolean} Exclusive flag
  },
  send: {
    host: 'localhost',    // @param {string} Hostname of udp client for messaging
    port: oscSendPort           // @param {number} Port of udp client for messaging
  }
}

//start OSC
const osc = new OSC({ plugin: new OSC.DatagramPlugin(oscConfig) })

osc.open()

//serve static files
// console.log(path.join(__dirname, 'public/html/routeOrList.html'))
// app.get('/', (req, res) => res.sendFile(path.join(__dirname, 'public/html/routeOrList.html')))
app.use(express.static(path.join(__dirname, 'public/javascript')))
app.use(express.static(path.join(__dirname, 'public/stylesheet')))
app.use(express.static(path.join(__dirname, 'public/images')))


//set up listing windows and redirection
//this is probably not the best way to generate HTML :)
function createWindowListHTML(array) {
  var head = '<head><title>List</title></head>';
  var header = "List of WsWindows";
  var body = '<body><h2>' + header + '</h2><h3>' + makeUlLinks(array) + '<h3></body>';
  // console.log("creating html");
  return '<!DOCTYPE html><html lang="en">' + head + body + '</html>'
}

function makeUlLinks(array) {
  var elementString = '';
  for (var i = 0; i < array.length; i++) {
    var name = array[i];
    // Create the list item:
    elementString = elementString + '<li><a href =\'' + name + '\'>' + name + '</a></li>'
  }
  // console.log("list: ", elementString);
  return '<ul>' + elementString + '</ul>';
}

app.get('/', function (req, res, next) {
  // console.log('the response will be sent by the next function ...')
  if (redirectWindow) {
    console.log("redirecting to the default window")
    res.redirect(303, '/' +redirectWindow) //we use temporary redirect to have browser always check for it in the future
  } else {
    // next()
    var windowArray = Array.from(activeWindows).sort();
    // console.log("windowArray:", windowArray)
    var html = createWindowListHTML(windowArray);
    // console.log("full HTML:", html);
    res.send(html)
  }
})


//set up osc-ws bridge for individual windows
function createNewWindow(name, isDefault = false) {
  if(!windows.has(name)) {
    
    var router = express.Router()
    
    wsClients[name] = new Set()
    
    windows.add(name)
    activeWindows.add(name)
    
    let slashName = '/' + name;
    
    router.use(function (req, res, next) {
      // console.log("name:", name)
      // console.log("windows.has(name):", windows.has(name))
      // console.log("activeWindows.has(name):", activeWindows.has(name))
      if (activeWindows.has(name)) {
        next() //handle connection
      } else {
        res.status(404).end()
      }
    })
    
    router.get('/', function(req, res) {
      // console.log("handling '/':", name)
      res.sendFile(path.join(__dirname, 'public/html/index.html'))
    })
    
    router.ws('/', function(ws, req) {
      var thisAddr = req.connection.remoteAddress + ":" + req.connection.remotePort;
      var slashThisAddr = "/" + thisAddr;
      var thisOscAddr = oscSendAddr + "/window"
      if(name.length > 0) {
        thisOscAddr = thisOscAddr + "/" + name;
      };
      console.log(name, ": new connection from", thisAddr)
      // console.log("name:", name)
      wsClients[name].add(ws);
      osc.send(new OSC.Message(thisOscAddr, 'add', thisAddr))
      
      var subscriptionNumber = osc.on(slashThisAddr, message => {
        // console.log("OSC message:", message)
        ws.send(message.args[0]) 
      })
      ws.on('message', function(msg) {
        // console.log('WS message:', msg);
        osc.send(new OSC.Message(thisOscAddr, 'data', thisAddr, msg))
      });
      ws.on('close', function() {
        console.log('closing connecting with', thisAddr);
        osc.send(new OSC.Message(thisOscAddr, 'remove', thisAddr))
        osc.off(slashThisAddr, subscriptionNumber);
        wsClients[name].delete(ws);
      });
      // add ping connection check here?
    });
    
    app.use(slashName, router)
    
  } else {
    // console.log(`window already exists: ${name}, reactivating`)
    activeWindows.add(name)
  }
  
}

//remove (actually deactivate) window
function removeWindow(name) {
  activeWindows.delete(name)
  wsClients[name].forEach(client => {
    client.terminate()
  });
}


//serve images on demant (these will be available until the server is restarted)
function serveImage(url, path) {
  var router = express.Router()
  router.get('/', function(req, res) {
    res.sendFile(path)
  })
  app.use('/' + url, router)
}

//start HTTP server
app.listen(webPort, "0.0.0.0", () => console.log(`WebSocket OSC bridge started.\nListening to web browser connections on port ${webPort}`))

// main OSC send/receive
// we open OSC after we start listening on web port - in case that fails we don't want to send port number to SC
osc.on('open', () => {
  oscReceivePort = osc.options.plugin.socket.address().port;
  console.log("OSC receive port:", oscReceivePort)
  osc.send(new OSC.Message(mainOscSendAddr, 'oscport', oscReceivePort))
  // setInterval(() => {
  //    osc.send(new OSC.Message('/response', Math.random()))
  // }, 1000)
})

osc.on('/add', message => {
  var name = message.args[0]
  console.log("Adding window:", name)
  createNewWindow(name) 
})

osc.on('/remove', message => {
  var name = message.args[0]
  console.log("Removing window:", name)
  removeWindow(name)
})

osc.on('/setRedirect', message => {
  var name = message.args[0];
  if(name) {
    console.log("setting redirection to", name);
    redirectWindow = name;
  } else {
    console.log("removing redirection");
    redirectWindow = undefined;
  }
  // console.log("setRedirect message:", name);
  // console.log("whole message:", message);
})

osc.on('/addImage', message => {
  var url = message.args[0];
  var path = message.args[1];
  // console.log("adding image:", url, path);
  serveImage(url, path);
})