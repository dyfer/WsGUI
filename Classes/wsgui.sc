/*
This software was developed with the support of the Center for Digital Arts and Experimental Media (DXARTS), University of Washington (www.dxarts.washington.edu)

Created by Marcin Pączkowski and Michael McCrea
*/

/*
--- TODO list: --- (updated: 2015.07.30)

- create SC structure mimicking html/dom (using dictionaries) - nested or not? probably not
	- full structure with window scroll settings etc
	- method names etc separately from styling - both in dictionary as well as when transferring to ws/javascript
	- I think it should follow Document Object Model (DOM) specs
- VsView instead of VsWidget as parent
- VsView being <div> by default?
	- encapsulate all elements in their divs?
- rethink wslayouts - use <div>?
	- chenge children's bounds to relative or something, and percentage (?)
- Servers to separate classes
- when updating - have a separate task (get rid of .init vs .new for widgets, control sending pace, etc) - might create more problems than solutions?
- possible slider substitute: http://skidding.github.io/dragdealer/

create 2 divs, parent and child, set class names according to instructions (.setAttribute), then create new dragdealer class passing parent

--- done list: ---
*/

WsWindow {
	var title, <isDefault, <>actionOnClose, suppressPosting;
	var <wsPid, <oscPath;//, <wwwPipe;
	var <wsPort, <wsOscPort; //chosen automatically
	var <wwwPath;
	var <scSendNetAddr, <socketsResponder, <clientDict;//, <guiObjDict
	var <guiObjects; //guiObjects: name -> [widgetParams, function, controlSpec]
	var <namesToIDs;
	var	<numClients;
	var <bodyID; //this will be id of the object referring to the body, when the background is first set;
	var <titleID; //this will be id of the object referring to the title, when the background is first set;
	var <curWidgetID = 0;
	var <windowID; //for multiple windows
	var styleKeys, numericOutputKinds;
	var <wwwServerStartedFromWsWindow = false;

	classvar <>pythonPath, <>bridgePath, <>staticServerPath, <>checkPortPath, <classPath, <classDir; //set in init...
	classvar oscRootPath = "/sockets";
	classvar currentWindowID;
	classvar <>globalWwwPath = "www"; //relative to class
	classvar <>jsFilename = "wsport.js"; //relative to class
	classvar <>discMsgFile = "discMessage.js";
	classvar <wwwPid, <wwwPort;
	classvar <allWsWindows;// = IdentityDictionary.new;
	classvar <sourceWwwPath = "supportFiles/wwwSource";
	classvar <sourceWwwPathNexus = "supportFiles/wwwSourceNexus";
	classvar <defaultWwwPath = "supportFiles/wwwDefault";
	classvar <redirectionAddrFile = "whereto.js";
	classvar <redirectionHtmlFile = "index.html";
	classvar functionAddedToShutdown = false;

	*new {|title, isDefault = true, wwwPort, actionOnClose, suppressPosting = false|
		^super.newCopyArgs(title, isDefault, actionOnClose, suppressPosting).init(wwwPort);
	}

	*addToShutdown {
		if(functionAddedToShutdown.not, {
			ShutDown.objects = ShutDown.objects.add({WsWindow.freeAll});
			functionAddedToShutdown = true;
		});
	}

	*startWwwServer {arg port = 8000, suppressPosting = false;
		var rootPath = globalWwwPath;
		var cmd;
		this.setClassVars; //set that first
		this.addToShutdown;
		wwwPort = port; //set classvar
		if(rootPath[0] == "~", {//it's relative to home directory
			rootPath = rootPath.standardizePath;
		}, {
			if(rootPath[0] != "/", {//it's relative to the class file
				rootPath = File.realpath(this.class.filenameSymbol).dirname ++ "/" ++ rootPath;
			});
		});
		rootPath = rootPath.withoutTrailingSlash.escapeChar($ );
		postf("Starting www server, root path: %\n", rootPath);
		// cmd = "pushd " ++ rootPath ++ "; exec python -m SimpleHTTPServer " ++ port ++ "; popd";
		// cmd = "cd " ++ rootPath ++ "; exec python -m SimpleHTTPServer " ++ port;
		// staticServerPath
		cmd = "exec" + pythonPath + "-u" + staticServerPath + port.asString + rootPath; //-u makes posting possible (makes stdout unbuffered)
		// "wwwPid: ".post; wwwPid.postln;
		// "class: ".post; wwwPid.class.postln;
		// "wwwPort: ".post; wwwPort.postln;
		if(wwwPid.isNil, {
			// if(this.checkWwwPort, {
			wwwPid = cmd.unixCmd({
				"Python static www server stopped!".postln;
				wwwPid = nil;
				// this.killWS;
			}, postOutput: suppressPosting.not);
		}, {
			("WWW server at port " ++ wwwPort.asString ++ " seems already running, new server NOT started, continuing...").warn;
			// wwwPort = nil; // clear classvar - why?
		});
	}

	*stopWwwServer {
		if(wwwPid.notNil, {
			postf("Stopping www server, pid %\n", wwwPid);
			("kill" + wwwPid).unixCmd;
		}, {
			"Www server not running, nothing to kill".postln;
		});
	}

	// *checkWwwPort {
	// 	this.setClassVars;
	// 	^(("exec" + pythonPath + checkPortPath + wwwPort.asString + "TCP").unixCmdGetStdOut.asInteger > 0);
	// }

	*freeAll {
		WsWindow.allWsWindows.keys.do({|thisKey|
			WsWindow.allWsWindows[thisKey].free;
		});
		this.stopWwwServer;
	}

	// use this ONLY if you lost python process PID
	// like overwriting a variable or recompiling library
	*killPython {
		"killall python".unixCmd
	}

	*setClassVars {
		pythonPath ?? {pythonPath = "python"};
		classPath ?? {classPath = File.realpath(this.class.filenameSymbol)};
		classDir ?? {classDir = classPath.dirname};
		bridgePath ?? {bridgePath = (classPath.dirname ++ "/python/ws_osc.py").escapeChar($ )}; //remember to escape!!!
		checkPortPath ?? {checkPortPath = (classPath.dirname ++ "/python/checkport.py").escapeChar($ )};
		staticServerPath ?? {staticServerPath = (classPath.dirname ++ "/python/simplehttpserver.py").escapeChar($ )};
	}

	init {|wwwPortArg|
		allWsWindows ?? {allWsWindows = IdentityDictionary.new}; //create dictionary with all members if not already done
		//first check if the name is valid
		title ?? {title = "default"};
		windowID = title.replace(" ", "").asSymbol;
		if(allWsWindows[windowID].notNil, {
			Error("WsWindow with name \"" ++ title ++ "\" already exists. Please use other title or remove existing WsWindow").throw;
		}, {
			allWsWindows[windowID] = this;

			actionOnClose ?? {actionOnClose = {}};
			WsWindow.setClassVars;
			WsWindow.addToShutdown;
			// pythonPath ?? {pythonPath = "python"};
			// classPath ?? {classPath = File.realpath(this.class.filenameSymbol)};
			// classDir ?? {classDir = classPath.dirname};
			// bridgePath ?? {bridgePath = (classPath.dirname ++ "/python/ws_osc.py").escapeChar($ )}; //remember to escape!!!
			// checkPortPath = (classPath.dirname ++ "/python/checkport.py").escapeChar($ );

			//init vars
			guiObjects = IdentityDictionary.new(know: true);
			clientDict = IdentityDictionary.new(know: true);
			namesToIDs = IdentityDictionary.new(know: true);
			styleKeys = [\bounds, \color, \backgroundColor, \textColor, \font, \textAlign, \css]; //this are all symbols that should not be intepreted as object parameters, but rather as stylig (CSS) elements; custom css string can be added under \css key
			numericOutputKinds = [\slider, \checkbox];

			//check www server, start if port is available
			wwwPortArg !? {
				// if(this.checkWwwPort, {
				// 	this.startWwwServer(wwwPort)
				// }, {
				// 	Error("WsWindow: can't bind to port" + wwwPort.asString ++". Please use a different port or terminate the process using it, close any browser windows pointing to that port and wait").throw;
				// })
				WsWindow.startWwwServer(wwwPortArg);
				wwwServerStartedFromWsWindow = true;
			};

			//path
			wwwPath = globalWwwPath ++ "/" ++ windowID.asString;
			// this.addSubdirectory; //moved to OSC responder
			if(isDefault, {
				this.isDefault_(isDefault)
			});
			// this.setDefaultRedirectionAddress; //now figure out the address

			// this.getPorts; //get next free port for osc communication
			// this.updateWsPortInFile(wsPort); //moved to OSC responder

			//important - create individual path for osc messages
			// oscPath = oscRootPath ++ "/" ++ wsPort.asString;
			oscPath = oscRootPath ++ "/" ++ windowID.asString;
			"oscPath: ".post; oscPath.postln;
			this.startBridge; //to give time
			// guiObjects[titleID][0][\title] = title;//workaround so it's available right away
			{this.title_(title)}.defer(1); //awful hack for now to solve possible timing problems
		});
	}

	updateWsPortInFile {arg port = 80000;
		var path = wwwPath; //www path
		var filename = jsFilename;
		var fileContentsArray, filePath;
		if(path[0] == "~", {//it's relative to home directory
			path = path.standardizePath;
		}, {
			if(path[0] != "/", {//it's relative to the class file
				path = File.realpath(this.class.filenameSymbol).dirname ++ "/" ++ path;
			});
		});
		filePath = path.withTrailingSlash ++ filename;
		"Writing ws port number to the file at ".post; filePath.postln;
		File.use(filePath, "w", {|file|
			// fileContentsArray.do({|thisLine, lineNumber|
			file.write("var wsPort = " ++ port.asString ++ ";")
			// });
		});
		"Writing done.".postln;
	}

	setDefaultRedirectionAddress {//address relative to globalWwwPath
		var address = wwwPath.asRelativePath(globalWwwPath); //this should just give us relative path
		var filePath;
		filePath = classDir.withTrailingSlash ++ globalWwwPath.withTrailingSlash ++ redirectionAddrFile;
		"Writing destination address to the file at ".post; filePath.postln;
		File.use(filePath, "w", {|file|
			file.write("var destination = \"" ++ address.asString ++ "\";")
		});
		"Writing done.".postln;
	}

	setAsDefault {
		var copyCmd;
		"Setting as default, copying/writing files".postln;
		//set the variable
		this.setDefaultRedirectionAddress;
		//copy index
		copyCmd = "cp " ++ (classDir.withTrailingSlash ++ defaultWwwPath.withTrailingSlash ++ redirectionHtmlFile).escapeChar($ ) ++ " " ++ (classDir.withTrailingSlash ++ globalWwwPath).escapeChar($ );
		// "copying index.html, command: ".post; copyCmd.postln;
		copyCmd.systemCmd;

		isDefault = true; //set the var

	}

	unsetAsDefault{
		var rm1cmd, rm2cmd;
		//remove both files
		"Removing files for redirection".postln;
		rm1cmd = "rm " ++  (classDir.withTrailingSlash ++ globalWwwPath.withTrailingSlash ++ redirectionHtmlFile).escapeChar($ );
		// "rm1cmd: ".post; rm1cmd.postln;
		rm1cmd.systemCmd;
		rm2cmd = "rm " ++  (classDir.withTrailingSlash ++ globalWwwPath.withTrailingSlash ++ redirectionAddrFile).escapeChar($ );
		// "rm2cmd: ".post; rm2cmd.postln;
		rm2cmd.systemCmd;

		isDefault = false; //set the var
	}

	isDefault_ {|val = false|
		if(val, {
			"removing other defaults".postln;
			allWsWindows.do({|thisWindow|
				if(thisWindow.windowID != windowID, {
					thisWindow.isDefault = false;
				});
			});
			this.setAsDefault;
		}, {
			this.unsetAsDefault;
		});
	}

	// getPorts { //moved to prPrepareGlobalResponders
	// 	// wsPort = ("exec" + pythonPath + checkPortPath + "0 TCP").unixCmdGetStdOut.asInteger; moved to the responder!
	// 	// wsOscPort = ("exec" + pythonPath + checkPortPath + "0 UDP").unixCmdGetStdOut.asInteger;
	// }

	startBridge {
		var cmd;
		this.prPrepareGlobalResponders; //first, so we're ready
		//starting python socket bridge
		//usage: python ws_osc.py SC_OSC_port, ws_OSC_port, oscPath, ws_port
		cmd = "exec" + pythonPath + "-u" + bridgePath + NetAddr.langPort + wsOscPort + oscPath + wsPort; //-u makes posting possible (makes stdout unbuffered)
		"websocket server cmd: ".post; cmd.postln;
		wsPid = cmd.unixCmd({|code, exPid|
			("WebSocket server stopped, exit code: " ++ code ++ "; cleaning up").postln;
			wsPid = nil;
			// this.killWWW;
			//stop www server only if it was started with this window instance:
			"wwwServerStartedFromWsWindow: ".post; wwwServerStartedFromWsWindow.postln;
			if(wwwServerStartedFromWsWindow, {
				// for future - check if there are no other WsWindows using the server...
				WsWindow.stopWwwServer;
			});
			// this.prCleanup; //should be done first using .free
		}, suppressPosting.not);

		// this.prPrepareGlobalResponders; //needs to be done after starting node, so node doesn't end up binding to osc receive port

		//prepare send port
		// scSendNetAddr = NetAddr("localhost", wsOscPort); //moved to the responder
	}

	addSubdirectory {
		var cmd, copyCmd;
		"Creating subdirectory and copying files".postln;
		//mkdir
		cmd = "mkdir " ++ (classDir.withTrailingSlash ++ wwwPath).escapeChar($ );
		// "Creading subdirectory, command: ".post; cmd.postln;
		cmd.systemCmd;
	}

	//add linking depending wether it's using nexus or not

	copyFiles {
		var cmd, copyCmd, thisWsFile;

		//symlink
		// if(useNexus, {
		// 	// thisWsFile = sourceWsNexusFile;
		// 	copyCmd = "ln -s " ++ (classDir.withTrailingSlash ++ sourceWwwPathNexus ++ "/*").escapeChar($ ) ++ " " ++ (classDir.withTrailingSlash ++ wwwPath).escapeChar($ ); //symlinks
		// }, {
			// thisWsFile = sourceWsFile;
		copyCmd = "ln -s " ++ (classDir.withTrailingSlash ++ sourceWwwPath ++ "/*").escapeChar($ ) ++ " " ++ (classDir.withTrailingSlash ++ wwwPath).escapeChar($ ); //symlinks
		// });
		// copyCmd = "ln -s " ++ (classDir.withTrailingSlash ++ sourceWwwPath.withTrailingSlash ++ thisWsFile).escapeChar($ ) ++ " " ++ (classDir.withTrailingSlash ++ wwwPath.withTrailingSlash ++ sourceWsFile).escapeChar($ ); //symlink - note proper linked file name regardless of the ws file used
		// "Copying files, command: ".post; copyCmd.postln;
		copyCmd.systemCmd;
	}

	removeSubdirectory {
		var rmFilesCmd, rmDirCmd, rmWsJs;
		//remove all files - or just known files?
		//all for now
		"Removing files and subdirectory".postln;

		//rmFilesCmd
		rmFilesCmd = "rm -rf " ++ (classDir.withTrailingSlash ++ wwwPath.withTrailingSlash).escapeChar($ ); //this removes both files and directory
		// "Removing files from current directory, command: ".post; rmFilesCmd.postln;
		rmFilesCmd.systemCmd;

	}

	prPrepareGlobalResponders {
		socketsResponder = OSCdef(oscPath, {|msg, time, addr, recvPort|
			var command, hostport, data;
			//command is either 'add', 'remove', 'data' or 'wsport' (for initial websocket port setting)
			#command, hostport, data= msg[[1, 2, 3]];
			command = command.asSymbol;
			hostport = hostport.asSymbol;
			// postf("command: %\n", command);
			// postf("Message from %\n", hostport);
			// postf("Data: %\n", data);
			// postf("Data present: %\n", dataPresent.asBoolean);
			// msg.postln;
			command.switch(
				\add, {this.addWsClient(hostport)},
				\remove, {this.removeWsClient(hostport)},
				\data, {this.interpretWsData(hostport, data)},
				\wsport, {
					wsPort = hostport.asInteger; //2nd argument
					// scSendNetAddr ?? {
					this.addSubdirectory;
					this.updateWsPortInFile(wsPort); //moved to OSC responder
					this.copyFiles;
					"received ws port: ".post; wsPort.postln;
				},
				\oscport,{
					wsOscPort = hostport.asInteger; //2nd argument
					scSendNetAddr = NetAddr("localhost", wsOscPort); //moved to the responder
				}
			);
		}, oscPath);
	}

	sendMsg {|dest, msg|
		// "Sending from SC: ".post; [dest, msg].postln;
		if(scSendNetAddr.notNil, { //just in case
			scSendNetAddr.sendMsg(dest, msg);
		});
		// scSendNetAddr.sendBundle(0, dest, msg);
	}

	sendMsgToAll {|msg|
		clientDict.keysDo({|thisAddr|
			this.sendMsg(thisAddr, msg);
		});
	}

	prAddObj {|dest, objID|
		this.sendMsg(dest, this.prepareJSONcommandId(\add, objID));
	}

	prAddObjToAll {|objID|
		clientDict.keysDo({|thisAddr|
			this.prAddObj(thisAddr, objID);
		});
	}

	prRemoveObj {|dest, objID|
		this.sendMsg(dest, this.prepareJSONcommandId(\remove, objID));
	}

	prRemoveObjFromAll {|objID|
		clientDict.keysDo({|thisAddr|
			this.prRemoveObj(thisAddr, objID);
		});
	}

	prUpdateObj {|dest, objID, whichKey|
		this.sendMsg(dest, this.prepareJSONcommandId(\update, objID, whichKey));
	}

	prUpdateObjInAll {|objID, whichKey|
		clientDict.keysDo({|thisAddr|
			this.prUpdateObj(thisAddr, objID, whichKey);
		});
	}

	prUpdateObjInAllExcept {|objID, whichKey, exceptAddress|
		clientDict.keysDo({|thisAddr|
			if(thisAddr != exceptAddress, {
				this.prUpdateObj(thisAddr, objID, whichKey);
			});
		});
	}

	prAddAllObj {|dest|
		guiObjects.keysDo({|thisID|
			this.prAddObj(dest, thisID);
		});
	}

	send {|msg| //temp syntax shortcut
		this.sendMsgToAll(msg);
	}

	addWsClient {|hostport|
		clientDict.put(hostport, true);
		this.prAddAllObj(hostport);
	}

	removeWsClient {|hostport|
		clientDict.removeAt(hostport)
	}

	interpretWsData {|hostport, data|
		var objID, value;
		#objID, value = data.asString.split($,);
		objID = objID.asInteger;
		// postf("data % from %\n", data, hostport);
		// postf("object %, value %, from %\n", objID, value, hostport);
		// here goes actual function triggering
		this.prUpdateValue(objID, value, hostport);
	}

	prUpdateValue {|objID, value, hostport|
		var valueKey;
		// debug
		// postf("kind: %\nvalue: %\nvalue class %\n", guiObjects[objID][0][\kind], value, value.class);

		if(numericOutputKinds.includes(guiObjects[objID][0][\kind]), {
			// value = guiObjects[objID][2].map(value.asFloat); //convert to float and map controlspec here and
			value = value.asFloat;
		});
		// workaround for updating checkbox value... and possibly others?
		guiObjects[objID][0][\kind].switch(
			\checkbox, {valueKey = \checked},
			\slider, {valueKey = 'value-slider'},
			{valueKey = \value} //default
		);
		// update value in the dictionary
		// guiObjects[objID][0][\value] = value;
		guiObjects[objID][0][valueKey] = value;
		// "value in the dictionary: ".post;
		// guiObjects[objID][0][\value].postln;
		// "guiObjects[objID][0]: ".post;
		// guiObjects[objID][0].postln;
		// broadcast change to other clients, use hostport to avoid feedback
		// this.prUpdateObjInAllExcept(objID, \value, hostport);
		this.prUpdateObjInAllExcept(objID, valueKey, hostport);
		//trigger function
		if(guiObjects[objID].notNil, {
			guiObjects[objID][1].value(value);
		});
	}

	killWS {
		if(wsPid.notNil, {
			postf("Killing ws_osc server, pid %\n", wsPid);
			("kill" + wsPid).unixCmd;
		}, {
			// "Bridge not running, nothing to kill".postln;
		});
	}

	free {
		"Freeing WsWindow titled ".post; title.postln;
		this.prCleanup;// clean up directories first
		// scSendNetAddr.dump;
		scSendNetAddr.sendMsg("/quit");
		{this.killWS}.defer(0.4); //if bridge won't close, this will kill it; also waits for any outstanding connections
		// this.prCleanup; //this will get called when ws bridge exits
	}

	close {
		^this.free;
	}

	prCleanup {
		//also close extra open ports here? probably not necessary
		socketsResponder.free;
		// this.removeAllImageLinks; //clean images - not needed when removing whole directory
		this.removeSubdirectory;
		"isDefault: ".post; isDefault.postln;
		if(isDefault, {
			this.isDefault_(false);
		});
		allWsWindows.removeAt(windowID);
		// disconReponder.free;
		// this.clear;
		actionOnClose.value;
	}

	prepareJSONcommandId {|command, id, whichKey| //whichKey used for updating single value
		var str, keyString, valString, newDict;
		// postf("whichKey: %\n", whichKey);
		if(command != \remove, {
			//if whichKey, then choose just one from guiObjects[id][0], otherwise full
			if(whichKey.notNil, {
				if(styleKeys.includes(whichKey), {
					// styleKeys = \style; // wrong
					// "whichKey is in styleKeys".postln;
					newDict = IdentityDictionary.new;
					guiObjects[id][0].keysValuesDo({|key, value, inc|
						if(styleKeys.includes(key), {
							newDict.put(key, value);
						});
					});
				}, {
					newDict = IdentityDictionary.new.put(whichKey, guiObjects[id][0][whichKey]);
				});
			}, {
				newDict = guiObjects[id][0];
			});
		}, {
			newDict = IdentityDictionary.new;//to not have unnecessary params for removing?
		});
		newDict.put(\command, command);
		newDict.put(\id, id);
		if(command == \remove, {
			str = this.prepareJSON(newDict); //don't go through css for removing
		}, {
			str = this.prepareJSON(this.prepareParametersDict(newDict));
		});
		^str;
	}

	prepareJSON {|dict|
		var str, keyString, valString;
		str = "{";
		dict.keysValuesDo({|key, value, inc|
			keyString = "\"" ++ key.asString ++ "\"";
			valString = "\"" ++ value.asString ++ "\"";
			str = str ++ keyString ++ ":" ++ valString;
			if(inc < (dict.size - 1), { //add comma only up to the second to the last item
				str = str ++ ", ";
			});
		});
		str = str ++ "}";
		^str;
	}

	prepareParametersDict {|dict| //this converts values in dictionary to format expected by web browser;
		var preparedDict, styleStr;
		preparedDict = IdentityDictionary.new;
		dict.keysValuesDo({|key, value, inc|
			if(styleKeys.includes(key).not, {
				switch(key,
					\menuItems, {
						preparedDict.put(key, "".ccatList(value).copyToEnd(2));
					},
					'background-color-slider', {
						preparedDict.put(key, value.hexString); //hack for slider background
					},	{
						preparedDict.put(key, value);
					}
				);
			});
		});
		styleStr = this.prepareStyleString(dict);
		if(styleStr.notNil, {
			preparedDict.put(\style, styleStr);
		});
		^preparedDict;
	}

	prepareStyleString {|dict|
		var str, bounds, cssDict, keyString, valString;
		cssDict = IdentityDictionary.new;
		bounds = dict[\bounds];
		bounds !? {
			cssDict.put(\position, "absolute");
			cssDict.put(\left, (bounds.left * 100).asString ++ "%");
			cssDict.put(\top, (bounds.top * 100).asString ++ "%");
			if(bounds.width > 0, {cssDict.put(\width, (bounds.width * 100).asString ++ "%")});
			if(bounds.height > 0, {cssDict.put(\height, (bounds.height * 100).asString ++ "%")});
			// if((dict[\kind] == \slider) && (bounds.width < bounds.height), {
			// 	cssDict.put('-webkit-appearance', "slider-vertical");
			// }); //auto vertical slider
		};
		dict[\backgroundColor] !? {cssDict.put('background-color', dict[\backgroundColor].hexString)};
		// dict['background-color-slider'] !? {cssDict.put('background-color-slider', dict['background-color-slider'].hexString)};
		dict[\textColor] !? {cssDict.put('color', dict[\textColor].hexString)};
		dict[\font] !? {
			dict[\font].size !? {cssDict.put('font-size', dict[\font].size.asString ++ "px")};
			dict[\font].name !? {cssDict.put('font-family', dict[\font].name)};
			//add text-decoration here as well - bold, italic?
		};
		dict[\textAlign] !? {cssDict.put('text-align', dict[\textAlign].asString)};

		//create string
		str = "";
		cssDict.keysValuesDo({|key, value, inc|
			keyString = key.asString;
			valString = value.asString;
			str = str ++ keyString ++ ":" ++ valString ++ ";";
		});
		dict[\css] !? {str = str ++ dict[\css].asString}; //add custom css

		if(cssDict.size > 0, {
			^str;
		}, {
			^nil;
		});
	}

	addWidget {arg name, kind = \button, func = {}, parameters = IdentityDictionary.new, spec = [0, 1].asSpec, sendNow=true;
		var paramsDict, id, okToAddWidget, step;
		if(name.notNil, {
			if(namesToIDs[name].isNil && name.isKindOf(SimpleNumber).not,
				{
					id = this.prGetCurrentIdAndIncrement;
					namesToIDs.put(name, id);
					okToAddWidget = true;
				},{
					warn( format(
						"Widget assigned to name % already exists or you're trying to use SimpleNumber as id, not adding\n", name));
					okToAddWidget = false;
			});
		}, {
			id = this.prGetCurrentIdAndIncrement;
			okToAddWidget = true;
		});

		if(okToAddWidget, {
			paramsDict = IdentityDictionary.new(know: false);
			paramsDict.put(\kind, kind);
			if(parameters.isKindOf(Dictionary), {
				paramsDict = paramsDict.putAll(parameters);
			});
			//here params
			kind.switch(
				\slider, {
					// paramsDict.put(\min, 0);
					// paramsDict.put(\max, 1);
					// paramsDict.put('slider-value', spec.unmap(spec.default));
					// paramsDict.put(\step, \any);
					paramsDict.put(\vertical, (paramsDict[\bounds].width<paramsDict[\bounds].height));
				}, //controlspec used only for slider's creation
				\body, {
					bodyID = id;
				},
				\image, {
					//link for image
					paramsDict[\src] !? {
						var relPath = this.createImageLink(paramsDict[\src], id);
						paramsDict[\src] = relPath;
					};
				}
			);
			guiObjects.put(id, [
				paramsDict,
				func,
				spec
			]);
			//send
			sendNow.if{ this.prAddObjToAll(id) };
		});
		^id;
	}

	createImageLink {|path, id|
		var cmd, relativeImgPath;
		// relativeImgPath = "images/" ++ id.asString; //to not have to deal with removing the directory afterwards...
		relativeImgPath = id.asString;
		cmd = "ln -sf " ++ path.escapeChar($ ) + (classDir.withTrailingSlash ++ wwwPath.withTrailingSlash ++ relativeImgPath).escapeChar($ );
		"Creating symlink: ".post;
		cmd.postln;
		cmd.systemCmd; //synchronously, so we have the link on time
		^relativeImgPath;
	}

	removeWidget {|idOrName|
		var id;
		if(idOrName.isKindOf(SimpleNumber), {
			id = idOrName;
		}, {
			id = namesToIDs[idOrName];
			namesToIDs.removeAt(idOrName);
		});

		if(guiObjects[id].notNil, {
			guiObjects.removeAt(id);
			this.prRemoveObjFromAll(id);
		}, {
			postf("No object at id %, not removing\n", id);
		});
	}

	// remove all widgets - convenience method
	clear {
		this.removeAllWidgets;
	}

	removeAllWidgets {
		guiObjects.keysDo({ |key|
			this.removeWidget(key)
		});
		bodyID = nil;
		titleID = nil;
	}

	updateWidget {|idOrName, whichKey|
		var id;
		if(idOrName.isKindOf(SimpleNumber), {
			id = idOrName;
		}, {
			id = namesToIDs[idOrName];
		});
		if(guiObjects[id].notNil, {
			this.prUpdateObjInAll(id, whichKey);
		}, {
			postf("No object at id %, not updating\n", id);
		});
	}

	prGetCurrentIdAndIncrement {
		var curID;
		curID = curWidgetID;
		curWidgetID = curWidgetID + 1;
		^curID;
	}

	background_ {|color|
		if(bodyID.isNil, {
			bodyID = this.addWidget(nil, \body, {},
 parameters: IdentityDictionary.new.put(\backgroundColor, color));
		}, {
			guiObjects[bodyID][0][\backgroundColor] = color;
			this.updateWidget(bodyID);
		});
		// updateWidget(bodyID, \backgroundColor);
		^color;
	}

	background {
		if(bodyID.notNil, {
			^guiObjects[bodyID][0][\backgroundColor];
		}, {
			^nil;
		});
	}

	title_ {|title|
		if(titleID.isNil, {
			titleID = this.addWidget(nil, \title, {},
 parameters: IdentityDictionary.new.put(\title, title));
		}, {
			guiObjects[titleID][0][\title] = title;
			this.updateWidget(titleID);
		});
		^title;
	}

	title {
		if(titleID.notNil, {
			^guiObjects[titleID][0][\title];
		}, {
			^title;
		});
	}

	layout_ { |wsLayout|
		var startX, startY, remHSpace, remVSpace;
		// layouts = layouts.add(wsLayout); // to be added for introspection (i.e. .children)

		wsLayout.bounds.notNil.if(
			{	var bounds;
				bounds = wsLayout.bounds;
				remVSpace = bounds.height;
				remHSpace = bounds.width;
				startX = bounds.left;
				startY = bounds.top;
			},
			// else bounds are full page
			{ remVSpace = remHSpace = 1; startX = startY = 0; }
		);

		// postf("laying out in these absolute bounds: %, %, %, %\n", startX, startY, remHSpace, remVSpace);

		wsLayout.isKindOf(WsLayout).if{
			this.buildLayout(wsLayout, Rect(startX, startY, remHSpace, remVSpace));
		};
	}

	buildLayout { |layout, parBoundsRect|
		var loKind, elements, nItems, dimsNorm, dimsAbs, nonNilDims, unKnownDimSize;
		var nextX, nextY, counter=0;
		var freeSpace=0, nilSize=0; // move to if statement where they're used

		loKind = switch(layout.class,
			WsVLayout, {\vert},
			WsHLayout, {\horiz}
		);
		loKind ?? {"layout is not a WsVLayout or WsHLayout".throw};
		elements = layout.elements;
		nItems = elements.size;
		// postf( "number of items: %, %\n", nItems, elements);

		// element dimensions normalized 0>1
		dimsNorm = elements.collect{|elem|
			if( elem.isKindOf(WsLayout) or: elem.isKindOf(WsWidget),
				{ elem.bounds.notNil.if(
					{ 	//"returning a dimension of bound ".post; // debug
						(loKind == \vert).if({elem.bounds.height},{elem.bounds.width}) },
					{ 	//"returning unspecified dimension".postln; // debug
						'unspecified' }
					);
				},{ elem } // assumed to be either nil or a Number
			);
		};

		// assign layouts or widgets with nil (unspecified) width to a width of
		// 1/numNonNilItems and rescale other items accordingly in case a
		// layout with unspecified width is forced to 0 width by nilSize = 0

		nonNilDims =	dimsNorm.select({|dim| dim.notNil}); // numbers and 'unspecified's
		// postf("nonNilDims: %\n",nonNilDims);

		unKnownDimSize =	nonNilDims.size.reciprocal; // size assigned to unspecified dimension
		// postf("unKnownDimSize: %\n",unKnownDimSize);

		nonNilDims =	nonNilDims.replace('unspecified', unKnownDimSize);
		// postf("nonNilDims: %\n", nonNilDims);

		if(nonNilDims.sum > 1,
			{ 	// dimensions are rescaled
				// "rescaling element dimension".postln;
				nonNilDims = nonNilDims.normalizeSum; // rescale all down to sum to 1
				dimsAbs = dimsNorm.collect({ |item, i| var dim;
					item.isNil.if(
						{ 	dim = 0 },
						{ 	dim = nonNilDims[counter];
							counter = counter +1; }
					);
					dim
				})
			},{
				var numNils;
				freeSpace = 1 - nonNilDims.sum;
				numNils = dimsNorm.occurrencesOf(nil);
				nilSize = if(( numNils > 0) and: (freeSpace > 0), {freeSpace / numNils},{0});
				dimsAbs = dimsNorm.collect({ |item, i|
					var dim;
					item.isNil.if(
						{	dim = nilSize },
						{	dim = nonNilDims[counter];
							counter = counter +1; }
					);
					dim
				});
		});

		// convert to absolute page widths
		dimsAbs = dimsAbs * (loKind == \vert).if({parBoundsRect.height},{parBoundsRect.width});

		// postf( "dimensions norm:%\ndimensions abs :%\navailable free space:%\nnilSize: %\n",
		// dimsNorm, dimsAbs, freeSpace, nilSize);

		// place the widgets
		nextX =	parBoundsRect.left;
		nextY =	parBoundsRect.top;

		elements.do{ |elem, i|
			var myBounds, myWidth, myHeight;

			// postf("\niterating through: %, next x/y: %/%\n", elem, nextX, nextY); //debug

			(elem.isKindOf(WsLayout) or: elem.isKindOf(WsWidget)).if{
				// a widget or another layout
				switch( loKind,
					\vert,	{
						myHeight = dimsAbs[i];
						myWidth = elem.bounds.notNil.if( {
							// postf("multiplying elem width: % by parent width: %\n",
							// elem.bounds.width, parBoundsRect.width);
							elem.bounds.width * parBoundsRect.width
							},{ parBoundsRect.width }
						);
					},
					\horiz,	{
						myWidth = dimsAbs[i];
						myHeight = elem.bounds.notNil.if(
							{ elem.bounds.height * parBoundsRect.height },
							{ parBoundsRect.height }
						);
					}
				);

				// ignoring original xy for now
				myBounds = Rect(nextX, nextY, myWidth, myHeight);

				case
				{elem.isKindOf(WsLayout)} {
					this.buildLayout(elem, myBounds);
					elem.bounds_(myBounds); // for introspection
				}
				{ elem.isKindOf(WsWidget) } {
					// postf("placing a WsWidget: % at bounds: %\n", elem, myBounds); // debug
					elem.bounds_(myBounds);
					elem.addToPage;
				};
			};

			switch( loKind,
				\vert,	{nextY = nextY + dimsAbs[i]},
				\horiz,	{nextX = nextX + dimsAbs[i]}
			)
		};
	}
}

// TODO: figure out how to separate out methods for irrelevant sub-classes
// e.g. textColor for WsCheckBox

WsWidget {
	var ws, <bounds;
	var <id;

	// TODO: change so wsWindow isn't necessary at this stage
	add {|wsWindow, argbounds, kind, sendNow = true|
		ws = wsWindow;
		argbounds !? {bounds = argbounds};
		id = ws.addWidget(nil, kind, {},
			IdentityDictionary.new.put(\bounds, bounds ?? Rect(0, 0, 0.1, 0.1)),
			sendNow: sendNow
		);
	}

	addToPage { ws.prAddObjToAll(id) }

	// useful only if the widget is instantiated
	// but hasn't been sent to the page yet
	bounds_ { |boundsRect|
		bounds = boundsRect ?? Rect(0, 0, 0.1, 0.1);
		ws.guiObjects[id][0][\bounds] = bounds;
		ws.updateWidget(id, \bounds);
	}

	action_ {|function|
		ws.guiObjects[id][1] = {function.value(this)};
	}

	action {
		^ws.guiObjects[id][1];
	}

	background_ {|color|
		if(ws.guiObjects[id][0][\backgroundColor].isNil, {
			ws.guiObjects[id][0].put(\backgroundColor, color);
		}, {
			ws.guiObjects[id][0][\backgroundColor] = color;
		});
		ws.updateWidget(id, \backgroundColor);
	}

	background {
		^ws.guiObjects[id][0][\backgroundColor];
	}

	stringColor_ {|color|
		if(ws.guiObjects[id][0][\textColor].isNil, {
			ws.guiObjects[id][0].put(\textColor, color);
		}, {
			ws.guiObjects[id][0][\textColor] = color;
		});
		ws.updateWidget(id, \textColor);
	}

	stringColor {
		^ws.guiObjects[id][0][\textColor];
	}

	font_ {|font|
		if(ws.guiObjects[id][0][\font].isNil, {
			ws.guiObjects[id][0].put(\font, font);
		}, {
			ws.guiObjects[id][0][\font] = font;
		});
		ws.updateWidget(id, \textColor);
	}

	font {
		^ws.guiObjects[id][0][\font];
	}

	align_ {|align|
 		if(ws.guiObjects[id][0][\textAlign].isNil, {
			ws.guiObjects[id][0].put(\textAlign, align);
		}, {
			ws.guiObjects[id][0][\textAlign] = align;
		});
		ws.updateWidget(id, \textAlign);
	}

	align {
		^ws.guiObjects[id][0][\textAlign];
	}

	css_ {|cssString|
 		if(ws.guiObjects[id][0][\css].isNil, {
			ws.guiObjects[id][0].put(\css, cssString);
		}, {
			ws.guiObjects[id][0][\css] = cssString;
		});
		ws.updateWidget(id, \css);
	}

	css {
		^ws.guiObjects[id][0][\css];
	}

	controlSpec_ {|spec| //how to update dictionary with min and max on change?
		ws.guiObjects[id][2] = spec;
	}

	controlSpec {
		^ws.guiObjects[id][2];
	}

	string_ {|thisString|
		thisString ?? {thisString = ""; "thisString was nil".warn};
		thisString = thisString.replace("\n", "<br>");//convert newline for html
		thisString = thisString.replace("\t", "&nbsp;&nbsp;&nbsp;");//convert newline for html
 		if(ws.guiObjects[id][0][\innerHTML].isNil, {
			ws.guiObjects[id][0].put(\innerHTML, thisString);
		}, {
			ws.guiObjects[id][0][\innerHTML] = thisString;
		});
		ws.updateWidget(id, \innerHTML);
	}

	string {
		^ws.guiObjects[id][0][\innerHTML];
	}

	remove {
		ws.removeWidget(id);
	}
}

WsSimpleButton : WsWidget {

	*new {|wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \button, sendNow: true);
	}

	// doesn't send to page, just inits the object
	*init { |wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \button, sendNow: false);
	}
}

WsButton : WsWidget {
	var <value = 0, <numStates = 0, <states;

	*new {|wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \button, sendNow: true).prInitAction;
	}

	*init {|wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \button, sendNow: false).prInitAction;
	}

	// super.action_ overwrite to include incrementing the state counter in the function
	action_ { |function|
		var newFunction;
		newFunction = {
			value = (value + 1) % numStates;
			this.prUpdateStringAndColors;
			function.value(this);
		};
		ws.guiObjects[id][1] = newFunction;
	}

	states_ { |statesArray|
		states = statesArray;
		numStates = states.size;
		this.prUpdateStringAndColors;
	}

	// TODO: normalize the way value is set (e.g. in WsSlider)
	value_ {|val|
		value = val;
		this.prUpdateStringAndColors;
	}

	item {
		^states[value][0];
	}

	valueAction_ {|val|
		if(val != value, {
			value = val;
			this.prUpdateStringAndColors;
			this.action.();
		});
	}

	prUpdateStringAndColors {
		super.string_(states[value][0]);
		super.stringColor_(states[value][1]);
		super.background_(states[value][2]);
	}

	// assign a default action that advances the button states
	prInitAction {
		var defaultAction;
		defaultAction = {
			states !? {
				value = (value + 1) % numStates;
				this.prUpdateStringAndColors;
			}
		};
		ws.guiObjects[id][1] = defaultAction;
	}

}

WsStaticText : WsWidget {

	*new {|wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \text, sendNow: true);
	}

	// doesn't send to page, just inits the object
	*init { |wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \text, sendNow: false);
	}
}

WsImage : WsWidget {
	var <path;

	*new {|wsWindow, bounds, path, isURL = false|
		^super.new.add(wsWindow, bounds, \image, sendNow: true).path_(path, isURL);
	}

	// doesn't send to page, just inits the object
	*init { |wsWindow, bounds, path, isURL = false|
		^super.new.add(wsWindow, bounds, \image, sendNow: false).path_(path, isURL);
	}

	path_ {|newPath, isURL = false|
		var relPath;
		if(newPath.notNil, {
			path = newPath;
			if(isURL, {
				relPath = newPath;
			}, {
				relPath = ws.createImageLink(newPath, id);
			});
			ws.guiObjects[id][0].put(\src, relPath);
			ws.updateWidget(id, \src);
		});
	}
}

// TODO: reevaluate the difference between this and EZSlider
WsSlider : WsWidget {

	*new { |wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \slider, sendNow: true);
	}

	// doesn't send to page, just inits the object
	*init { |wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \slider, sendNow: false);
	}

	value_ {|val|
 		if(ws.guiObjects[id][0]['value-slider'].isNil, {
			ws.guiObjects[id][0].put('value-slider', ws.guiObjects[id][2].unmap(val)); //should not unmap here
		}, {
			ws.guiObjects[id][0]['value-slider'] = ws.guiObjects[id][2].unmap(val);
		});
		ws.updateWidget(id, 'value-slider');
	}

	value {
		^ws.guiObjects[id][2].map(ws.guiObjects[id][0]['value-slider']);
	}

	valueAction_ {|val|
		this.value_(val);
		this.action.();
	}

	background_ {|color|
		if(ws.guiObjects[id][0]['background-color-slider'].isNil, {
			ws.guiObjects[id][0].put('background-color-slider', color);
		}, {
			ws.guiObjects[id][0]['background-color-slider'] = color;
		});
		ws.updateWidget(id, 'background-color-slider');
	}

	background {
		^ws.guiObjects[id][0]['background-color-slider'];
	}
}

WsInput : WsWidget {

	*new { |wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \input, sendNow: true);
	}

	// doesn't send to page, just inits the object
	*init { |wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \input, sendNow: false);
	}

	value_ {|val|
		ws.guiObjects[id][0][\value] = val;
		ws.updateWidget(id, \value);
	}

	value {
		^ws.guiObjects[id][0][\value];
	}

	valueAction_ {|val|
		this.value_(val);
		this.action.();
		// ^val;
	}

	font_ {|font|
		if(ws.guiObjects[id][0][\font].isNil, {
			ws.guiObjects[id][0].put(\font, font);
		}, {
			ws.guiObjects[id][0][\font] = font;
		});
		ws.updateWidget(id, \textColor);
	}

	font {
		^ws.guiObjects[id][0][\font];
	}

	background_ {|color|
		if(ws.guiObjects[id][0][\backgroundColor].isNil, {
			ws.guiObjects[id][0].put(\backgroundColor, color);
		}, {
			ws.guiObjects[id][0][\backgroundColor] = color;
		});
		ws.updateWidget(id, \backgroundColor);
	}

	background {
		^ws.guiObjects[id][0][\backgroundColor];
	}

	stringColor_ {|color|
		if(ws.guiObjects[id][0][\textColor].isNil, {
			ws.guiObjects[id][0].put(\textColor, color);
		}, {
			ws.guiObjects[id][0][\textColor] = color;
		});
		ws.updateWidget(id, \textColor);
	}

	stringColor {
		^ws.guiObjects[id][0][\textColor];
	}
}

// this should later be implemented as call to WsSlider and WsStaticText for label and value
WsEZSlider : WsSlider {}

WsPopUpMenu : WsWidget {

	*new { |wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \menu, sendNow: true);
	}

	// doesn't send to page, just inits the object
	*init { |wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \menu, sendNow: false);
	}

	items_ {|itemArr|
		if(ws.guiObjects[id][0][\menuItems].isNil, {
			ws.guiObjects[id][0].put(\menuItems, itemArr);
		}, {
			ws.guiObjects[id][0][\menuItems] = itemArr;
		});
		ws.updateWidget(id);
		this.value_(0); // init value to the first item
	}

	items {
		^ws.guiObjects[id][0][\menuItems];
	}

	value_ {|val|
 		if(ws.guiObjects[id][0][\value].isNil, {
			ws.guiObjects[id][0].put(\value, val);
		}, {
			ws.guiObjects[id][0][\value] = val;
		});
		ws.updateWidget(id, \value);
		// ^val;
	}

	value {
		^ws.guiObjects[id][0][\value].asInteger;
	}

	valueAction_ {|val|
		this.value_(val);
		this.action.();
	}

	item {
		^ws.guiObjects[id][0][\menuItems][ws.guiObjects[id][0][\value].asInteger];
	}
}

WsCheckbox : WsWidget {

	*new { |wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \checkbox, sendNow: true);
	}

	// doesn't send to page, just inits the object
	*init { |wsWindow, bounds|
		^super.new.add(wsWindow, bounds, \checkbox, sendNow: false);
	}

	value_ {|val|
 		if(ws.guiObjects[id][0][\checked].isNil, {
			ws.guiObjects[id][0].put(\checked, val);
		}, {
			ws.guiObjects[id][0][\checked] = val;
		});
		// ws.guiObjects[id][0][\value] = val; //hack... since html object responds to \checked, but we store value in \value
		ws.guiObjects[id][0][\checked] = val;
		ws.updateWidget(id, \checked);
		// ^val;
	}

	value {
		// ^ws.guiObjects[id][0][\value];
		^ws.guiObjects[id][0][\checked];
	}

	valueAction_ {|val|
		this.value_(val);
		this.action.();
	}
}



/*
--------------------------------------
	Layouts
--------------------------------------
*/

WsLayout { //}
	// copyArgs
	var <>bounds, <elements;

	*new { |bounds ... elements |
		^super.newCopyArgs(bounds, elements)
	}

	// remove the elements within the layout
	remove {
		elements.do(_.remove)
	}
}

WsHLayout : WsLayout {}
WsVLayout : WsLayout {}
