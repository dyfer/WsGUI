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
	var title, <isDefault, <suppressPosting;
	var <wsPid, <oscPath;//, <wwwPipe;
	var <wsPort, <wsOscPort; //chosen automatically
	var <scSendNetAddr, <responders, <clientDict;//
	var <guiObjects; //guiObjects: name -> [widgetParams, function, controlSpec]
	var <namesToIDs;
	var	<numClients;
	var <bodyID; //this will be id of the object referring to the body, when the background is first set;
	var <titleID; //this will be id of the object referring to the title, when the background is first set;
	// var <curWidgetID = 0;
	var <windowID; //for multiple windows
	var styleKeys, numericOutputKinds;
	// var <wwwServerStartedFromWsWindow = false;
	var <>onAddClientFunc, <>onRemoveClientFunc;
	var <wsWindowServer;
	var <uniqueID;
	var <>onClose;
	var serverShutdownFunction;
	var <backgroundIsBlinking = false;

	classvar <oscRootPath = "/sockets";
	classvar <allWsWindows;// = IdentityDictionary.new;
	classvar functionAddedToShutdown = false;

	*new {|title, wwwPort, isDefault = true, suppressPosting = false|
		^super.newCopyArgs(title, isDefault, suppressPosting).init(wwwPort);
	}


	*freeAll {
		WsWindow.allWsWindows.keys.do({|thisKey|
			WsWindow.allWsWindows[thisKey].free;
		});
		// this.stopWwwServer;
	}


	init {|wwwPortArg|
		allWsWindows ?? {allWsWindows = IdentityDictionary.new}; //create dictionary with all members if not already done
		//first check if the name is valid
		title ?? {title = ""};
		windowID = title.replace(" ", "").asSymbol;
		responders = List();
		if(allWsWindows[windowID].notNil, {
			Error("WsWindow with name \"" ++ title ++ "\" already exists. Please use other title or remove existing WsWindow").throw;
		}, {
			allWsWindows[windowID] = this;

			uniqueID = WsWindowUniqueID();

			//init vars
			guiObjects = IdentityDictionary.new(know: true);
			clientDict = IdentityDictionary.new(know: true);
			namesToIDs = IdentityDictionary.new(know: true);
			styleKeys = [\bounds, \color, \backgroundColor, \textColor, \font, \textAlign, \css]; //this are all symbols that should not be intepreted as object parameters, but rather as stylig (CSS) elements; custom css string can be added under \css key
			numericOutputKinds = [\slider, \checkbox];

			if(wwwPortArg.isKindOf(SimpleNumber).not, {
				Error("You need to pass port number as the second argument to the WsWindow").throw;
			}, {
				wsWindowServer = WsWindowServer.allWsWindowServers[wwwPortArg]; //first try to use an existing server
				wsWindowServer ?? {wsWindowServer = WsWindowServer(wwwPortArg, suppressPosting, {this.free})}; //if nil, that means we need to create a new one
				// add ourselves to the list of windows
				wsWindowServer.allWsWindows.add(this);
				//add error message - we're freeing all WsWindows on server close, so this is not needed
				// serverShutdownFunction = {Error("WsWindowServer shut down by itself. Please free associated WsWindows").throw};
				// wsWindowServer.doOnShutDown ?? {
				// wsWindowServer.doOnShutDown_(serverShutdownFunction)
			// };
			});

			//path
			oscPath = wsWindowServer.thisOscRootPath ++ "/window";
			if(windowID.asString.size != 0, {
				oscPath = oscPath ++ "/" ++ windowID
			});
			// "oscPath: ".post; oscPath.postln;
			//prepare responders
			this.prPrepareGlobalResponders;
			// add the window on the server
			wsWindowServer.sendMsg("/add", windowID);
			this.isDefault_(isDefault);
			this.title_(title);
		});
	}

	isDefault_ {|val = false|
		this.wsWindowServer !? {
			if(val, {
				this.wsWindowServer.sendMsg("/setRedirect", windowID)
			}, {
				this.wsWindowServer.sendMsg("/setRedirect")
			});
		}
	}

	prPrepareGlobalResponders {
		responders.add(
			OSCFunc({|msg, time, addr, recvPort|
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
						this.updateWsPortInFile(wsPort); //moved to OSC responder
						this.copyFiles;
						"received ws port: ".post; wsPort.postln;
					},
					// \oscport,{
					// 	wsOscPort = hostport.asInteger; //2nd argument
					// 	scSendNetAddr = NetAddr("localhost", wsOscPort); //moved to the responder
					// 	"received OSC port: ".post; wsOscPort.postln;
					// }
				);
			}, oscPath.asSymbol);
		);
	}

	sendMsg {|dest, msg|
		// "Sending from SC: ".post; [dest, msg].postln;
		wsWindowServer.sendMsg(dest, msg);
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
		onAddClientFunc!? {onAddClientFunc.value(hostport)};
	}

	removeWsClient {|hostport|
		clientDict.removeAt(hostport);
		onRemoveClientFunc !? {onRemoveClientFunc.value(hostport)};
	}

	interpretWsData {|hostport, data|
		var objID, value, commaIndex, dataString;
		dataString = data.asString;
		commaIndex = dataString.find(",");
		// #objID, value = data.asString.split($,); //this is not working for values that include comma!
		// objID = objID.asInteger;
		objID = dataString[..(commaIndex-1)].asInteger;
		value = dataString[(commaIndex+1)..];
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
		// "hostport: ".post; hostport.postln;
		// broadcast change to other clients, use hostport to avoid feedback
		// this.prUpdateObjInAllExcept(objID, \value, hostport);
		this.prUpdateObjInAllExcept(objID, valueKey, hostport);
		//trigger function
		if(guiObjects[objID].notNil, {
			guiObjects[objID][1].value(value, hostport);
		});
	}

	free {
		"Freeing WsWindow titled ".post; title.postln;
		this.prCleanup;// clean up directories first
		wsWindowServer !? {
			// remove window from the server
			wsWindowServer.sendMsg("/remove", windowID);
			// remove ourselves from the window Set
			wsWindowServer.allWsWindows.remove(this);
			//free if we're the last window
			if(wsWindowServer.allWsWindows.size == 0, {
				// "This was the last WsWindow on this WsWindowServer, freeing the server".postln;
				// if(wsWindowServer.doOnShutDown == serverShutdownFunction, {
				// wsWindowServer.doOnShutDown_(nil);
				// });
				wsWindowServer.free;
			});
		};
	}

	close {
		^this.free;
	}

	prCleanup {
		//also close extra open ports here? probably not necessary
		// socketsResponder.free;
		responders.do(_.free);
		// this.removeAllImageLinks; //clean images - not needed when removing whole directory
		// this.removeSubdirectory;
		// "isDefault: ".post; isDefault.postln;
		if(isDefault, {
			this.isDefault_(false);
		});
		allWsWindows.removeAt(windowID);
		// disconReponder.free;
		// this.clear;
		// actionOnClose.value;
		onClose !? {onClose.()};
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
					},
					'background-blink', {
						preparedDict.put(key,
							[(value.first != 0).if({value.first.reciprocal * 1000},{0}),
							value[1].hexString,
							value[2].hexString,
							value[3]
							]
						)
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
			if(dict[\font].italic, {cssDict.put('font-style', "italic")});
			if(dict[\font].bold, {cssDict.put('font-weight', "bold")});
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
					id = this.uniqueID.next;
					namesToIDs.put(name, id);
					okToAddWidget = true;
				},{
					warn( format(
						"Widget assigned to name % already exists or you're trying to use SimpleNumber as id, not adding\n", name));
					okToAddWidget = false;
			});
		}, {
			id = this.uniqueID.next;
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
					//should be already defined
					// paramsDict[\src] !? {
					// var relPath = this.serveImage(paramsDict[\src], id);
					// paramsDict[\src] = relPath;
				// };
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

	serveImage {|path, id|
		var cmd, relativeImgPath;
		// relativeImgPath = "images/" ++ id.asString; //to not have to deal with removing the directory afterwards...
		relativeImgPath = windowID ++ "/" ++ id;
		// cmd = "ln -sf " ++ path.escapeChar($ ) + (classDir.withTrailingSlash ++ wwwPath.withTrailingSlash ++ relativeImgPath).escapeChar($ );
		// "Creating symlink: ".post;
		// cmd.postln;
		// cmd.systemCmd; //synchronously, so we have the link on time
		wsWindowServer.sendMsg('/addImage', relativeImgPath, path);
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

	background_ {|color|
		if(bodyID.isNil, {
			bodyID = this.addWidget(nil, \body, {},
 parameters: IdentityDictionary.new.put(\backgroundColor, color));
		}, {
			guiObjects[bodyID][0][\backgroundColor] = color;
			if(backgroundIsBlinking, {
				this.prBackgroundBlink(0);
			});
			this.updateWidget(bodyID);
		});
	}

	background {
		if(bodyID.notNil, {
			^guiObjects[bodyID][0][\backgroundColor];
		}, {
			^nil;
		});
	}

	backgroundBlink {|freq = 1, color0 = (Color.black), color1 = (Color.grey(0.5)), period = 0.5|
		this.prBackgroundBlink(freq, color0, color1, period);
		this.updateWidget(bodyID);
	}

	prBackgroundBlink {|freq = 1, color0 = (Color.black), color1 = (Color.grey(0.5)), period = 0.5|
		// this.background_(color0); //init
		if(bodyID.isNil, {
			bodyID = this.addWidget(nil, \body, {},
				parameters: IdentityDictionary.new.put(\backgroundColor, color0));
		});
		if(freq == 0, {
			color0 = this.background;
			backgroundIsBlinking = false;
		}, {
			backgroundIsBlinking = true;
		});
		if(guiObjects[bodyID][0]['background-blink'].isNil, {
			guiObjects[bodyID][0].put('background-blink', [freq, color0, color1, period]);
		}, {
			guiObjects[bodyID][0]['background-blink'] = [freq, color0, color1, period];
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
		ws.guiObjects[id][1] = {|val, addr|function.value(this, addr)};
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

	backgroundBlink {|freq = 1, color0 = (Color.black), color1 = (Color.grey(0.5)), period = 0.5|
		if(ws.guiObjects[id][0]['background-blink'].isNil, {
			ws.guiObjects[id][0].put('background-blink', [freq, color0, color1, period]);
		}, {
			ws.guiObjects[id][0]['background-blink'] = [freq, color0, color1, period];
		});
		ws.updateWidget(id, 'background-blink');
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
		thisString = thisString.asString; //convert to string
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
		newFunction = {|val, addr|
			value = (value + 1) % numStates;
			this.prUpdateStringAndColors;
			function.value(this, addr);
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
		defaultAction = {|val, addr|
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
	var <path, imgID = 0;

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
				relPath = ws.serveImage(newPath, imgID);
				imgID = imgID + 1;
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

WsLayout {
	// copyArgs
	var <>bounds, <elements;

	*new { |bounds ... elements |
		^super.newCopyArgs(bounds, elements)
	}

	// remove the elements within the layout
	remove {
		elements.do({ |elem|
			if( elem.isKindOf(WsLayout) or: elem.isKindOf(WsWidget), {
				elem.remove
			})
		});
	}
}

WsHLayout : WsLayout {}
WsVLayout : WsLayout {}


// this class starts/stops node OSC <-> WWW (WebSockets) bridge
// provides sendMsg functionality
// does NOT provide OSC responders, these are implemented by individual WsWindows
WsWindowServer {
	var <port, <suppressPosting; //port is the "webPort" to which browser connects
	var <onFailure; //optional function to be triggered if WsWindowServer cannot start
	var <pid, <oscPath;
	var <scSendNetAddr;//, <responders;
	var <shutdownFunc; //to be added to ShutDown
	var <queuedMessages, <messageQueueIsEmpty = false;
	var <thisOscRootPath;
	var <>doOnShutDown; // {|this|}
	var <allWsWindows; //used by WsWindow to keep track of Windows associated with this WsWindowServer
	var <>printSentMessages = false;

	classvar <>oscRootPath = "/sockets";
	classvar <nodeScript = "../WsWindowServer/index.js"; //relative to this class
	classvar <>nodePath; //to be detected on first run
	classvar <scriptFullPath, <classPath;
	classvar <>runInTerminal = false; //for debugging
	classvar <allWsWindowServers; //used by WsWindowServer to keep track which ports we're already listening on; also used by WsWindowServer to free them

	*initClass {
		allWsWindowServers = IdentityDictionary();
	}

	*new {|port = 8000, suppressPosting = false, onFailure|
		^super.newCopyArgs(port, suppressPosting, onFailure).init;
	}


	init {
		var cmd;
		// responders = List();
		queuedMessages = List();
		allWsWindows = Set();

		thisOscRootPath = oscRootPath;

		this.prepareResponders; //this is only for getting the port number

		//keep track of all servers
		allWsWindowServers[port] = this;

		//init class vars
		classPath ?? {classPath = File.realpath(this.class.filenameSymbol)};
		scriptFullPath ?? {scriptFullPath = thisProcess.platform.formatPathForCmdLine(classPath.dirname +/+ nodeScript)};
		nodePath ?? {this.findNode};

		//add to shutdown
		shutdownFunc ?? {
			shutdownFunc = {
				"Freeing %(%)\n".postf(this.class.name, port);
				//disregard action on shutdown, as it is not reliable
				this.doOnShutDown_(nil);
				this.free;
			};
			ShutDown.objects.add(shutdownFunc);
		};

		//node index.js webPort oscSendToPort /oscSendAddr (optional oscReceivePort)
		cmd = format("% % % % %", nodePath, scriptFullPath, port, NetAddr.langPort, thisOscRootPath);
		if(thisProcess.platform.name == \windows, {
			cmd = format("\"%\"", cmd); //quote everything on windows
		});
		// "websocket server cmd: ".post; cmd.postln;
		if(runInTerminal.not, {
			pid = cmd.unixCmd({|code, exPid|
				("%(%) stopped, exit code: %\n").postf(this.class.name, port, code);
				pid = nil;
				this.cleanup;
				doOnShutDown !? {doOnShutDown.(this)}
			}, suppressPosting.not);
		}, {
			cmd.runInTerminal;
		});

	}

	findNode {
		if(thisProcess.platform.name == \windows, {
			nodePath = "where node".unixCmdGetStdOut.replace($\n);
			if(nodePath.size == 0, {nodePath = nil}); //reset to nil if it's an empty string
			nodePath ?? {
				block {|break|
					[
						"C:/Program Files/nodejs/node.exe",
						"C:/Program Files (x86)/nodejs/node.exe"
					].do({|thisPath|
						if(File.exists(thisPath), {
							nodePath = thisPath;
							break.();
						})
					})
				}
			};
		}, {
			nodePath = "which node".unixCmdGetStdOut.replace($\n);
			if(nodePath.size == 0, {nodePath = nil}); //reset to nil if it's an empty string
			nodePath ?? {
				block {|break|
					[
						"/usr/bin/node",
						"/usr/local/bin/node",
						"/opt/homebrew/bin/node"
					].do({|thisPath|
						if(File.exists(thisPath), {
							nodePath = thisPath;
							break.();
						})
					})
				}
			};
		});
		if(nodePath.isNil, {
			this.cleanup;
			Error("Couldn't find node.js executable. \nIf node is installed, you might want to provide path to node executable through WsWindowServer.nodePath_(path)").throw;
		}, {
			nodePath = thisProcess.platform.formatPathForCmdLine(nodePath);
			postf("node.js found at %\n", nodePath);
		});
	}

	kill {|force = false|
		pid !? {
			if(thisProcess.platform.name == \windows, {
				("taskkill /f /t /pid " ++ pid).unixCmd; //on Windows we have to force kill with child processes
			}, {
				if(force, {
					thisProcess.platform.killProcessByID(pid)
				}, {
					if(thisProcess.platform.name == \windows, {
						("taskkill /t /pid " ++ pid).unixCmd;//this doesn't work
					}, {
						("kill -15 " ++ pid).unixCmd;
					})
				})
			})
		}
	}

	prepareResponders {
		// since we are using different paths for different windows, we'll use WsWindow to create a responder for a given window
		// sendMsg is still going to use WsWindowServer class

		// responders.add(
		OSCFunc({|msg, time, addr, recvPort|
			var sendPort;
			if(msg[1] == 'oscport', {
				sendPort = msg[2];
				this.updateSendPort(sendPort);
			});
			// msg.postln;
		}, thisOscRootPath ++ "/" ++ port).oneShot;
	// );
	}

	updateSendPort {|port|
		"setting node server's receive port: ".post; port.postln;
		scSendNetAddr = NetAddr("localhost", port);
		//send messages
		//should we fork this?
		while({queuedMessages.size > 0}, {
			this.prSendMsg(*queuedMessages.pop);
		});
		messageQueueIsEmpty = true;
		//send queued messages here
	}

	sendMsg {|...args|
		if(messageQueueIsEmpty, {
			if(pid.notNil, {
				this.prSendMsg(*args);
			}, {
				format("%(%) is not running", this.class.name, port).warn;
			});
		}, {
			queuedMessages.add(args);
		});
	}

	prSendMsg {|...args| //this will not check for the queue
		scSendNetAddr.sendMsg(*args);
		if(printSentMessages, {
			this.class.name.post; " oscMessage: ".post;
			args.postln;
		});
	}


	free {
		if(pid.notNil, {
			this.kill(false);
		}, {
			"% doesn't seem to be running.\n".postf(this.class.name);
		});
	}

	cleanup { //triggered on exit
		// "Cleaning up.".postln;
		// responders.do(_.free);
		ShutDown.objects.remove(shutdownFunc);
		// shutdownFunc = nil;
		allWsWindowServers.removeAt(port);
		allWsWindows.do(_.free); //free all associated wswindowservers
		scSendNetAddr ?? {onFailure !? {onFailure.()}}; //if we didn't receive OSC port, that means we failed to start
	}
}

// helper class for managing IDs
WsWindowUniqueID {
	var <id=0;
	*new {|id = 0|
		^super.newCopyArgs(id)
	}
	next  {^id = id + 1}
}