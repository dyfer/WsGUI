//SuperCollider WebSockets GUI
//quick reference

//general info
//This class enables creating user interface in a web browser, accessible locally and over the network. It should be more reliable, although possibly slower, than pure OSC communication.

//installation
//link the wsgui.sc to SC's Extensions folder; alternatively copy the whole wsGui folder to Extensions

//WebSocket interface requires 2 "servers": one to serve static content (initial website, possibly images if used), another one to manage realtime communication (websockets). The former can be substituted for a built-in or another web server. It needs to be configured to serve the "www" folder inside this class' folder. Decoder machines will use their internal apache server, configured to point to that direction


i = WsWindowUniqueID()
i.next
i.id

OSCFunc.trace

a = WsWindow.new("first", 8000, true); //specify port for using internal static web server; nil when serving www folder using another web server

a.free
a.wsWindowServer.printSentMessages_(true)
a.wsWindowServer.pid
a.wsWindowServer.messageQueueIsEmpty
a.windowID.asString.size
a.responders
a.oscPath

b = WsWindow.new("second", 8000);
b = WsWindow.new("", 8000);
b.responders

b.free;

b.wsWindowServer.sendMsg("/setRedirect")
b.wsWindowServer.sendMsg("/setRedirect", "first")
a.wsWindowServer.sendMsg("/setRedirect", "first")

a.isDefault_(false)

NetAddr("localhost", 55187).sendMsg("/setRedirect")

"/usr/local/bin/node --help".unixCmd({|err| err.postln})

//now point a web browser to http://localhost:8000 (or http://yourIP:8000)
"http://localhost:8000".openOS

//finishing
a.clear //remove all elements, don't close server (can add new elements)
a.free //close ws server

//freeAll - close all windows/servers
WsWindow.freeAll; //

NetAddr.myIP; //from NetLib; useful for connecting external clients


//background and a title
a.background_(Color.black)
a.background_(Color.green)
a.title_("new title")
a.title


//blinking support...
a.backgroundBlink(30, Color.red, Color.black, 0.5)
a.backgroundBlink(10, Color.red, Color.black, 0.1)

a.backgroundBlink(0)


// MULTIPLE WINDOWS

WsWindow.startWwwServer(8000); //can be handled manually or when creating WsWindow; for multiple windows manual creation/freeing is advisable to avoid problems
WsWindow.stopWwwServer
WsWindow.wwwPid;

a = WsWindow.new("new title", 8000, false); //when no port is specified, no www server will be started, NOR ONE WILL BE STOPPED WHEN FREEING this the need to
a.free
//now point a web browser to http://localhost:8000 (or http://yourIP:8000)

o = WsWindow.new("different title", 8000); //second win!
o.free


a.isDefault_(false);//unset default
a.isDefault_(true);//unset default

o.isDefault_(false);//unset default

a.background_(Color.blue);
o.background_(Color.green); //second win


//button - simple
b = WsSimpleButton.new(a, Rect(0.5, 0, 0.2, 0.05)); //button with one "state"
b.string_("string")
b.font_(Font.new(\Georgia).size_(24))
b.backgroundColor_(Color.green);
b.textColor_(Color.red);
b.action_({"bu1".postln}) //just a function
b.remove

//init only, send to page later
b = WsSimpleButton.init(a, nil); // no bounds specified
b.string_("string")
b.font_(Font.new(\Georgia).size_(24))
b.bounds_(Rect(0.3, 0.9, 0.5, 0.05)) // bounds still settable before sending to the page
b.bounds
b.bounds_(Rect(0.3, 0.05, 0.5, 0.5)) // bounds still settable before sending to the page
b.bounds
// now send
b.addToPage
b.backgroundColor_(Color.green);
b.textColor_(Color.red);
b.action_({"bu1".postln}) //just a function
b.remove


//button - multistate
i = WsButton.new(a, Rect(0, 0, 0.2, 0.2)); //WsButton imitates SC style multistate button
i.states_([
	["zero", Color.black, Color.rand],
	["one", Color.black, Color.rand],
	["two", Color.black, Color.rand]
]);
i.states_([
["Basic Balance", Color.black, Color.white],
			["Basic Balance Pending", Color.black, Color.gray],
]);


i.action_({}) //passed button object now

i.value_(0)
i.value_(2)
i.item
i.item_("two") // not implemented
i.states[i.value][0]
i.font_(Font.new(\Georgia).size_(24))
i.action_({|but| "got state: ".post; but.value.postln; "item: ".post; but.item.postln;}) //passed button object now
i.remove

//button - multistate
i = WsButton.init(a, Rect(0, 0, 0.2, 0.2)); //WsButton imitates SC style multistate button
i.states_([
	["zero", Color.black, Color.rand],
	["one", Color.black, Color.rand],
	["two", Color.black, Color.rand]
]);
// now send
i.addToPage

i.value_(0)
i.value_(1)
i.item
i.item_("two") // not implemented
i.states[i.value][0]
i.font_(Font.new(\Georgia).size_(24))
i.action_({|but| "got state: ".post; but.value.postln; "item: ".post; but.item.postln;}) //passed button object now
i.remove


//text, labels
c = WsStaticText.new(a, Rect(0.5, 0.5, 0.2, 0.2));
c.string_("My text with newline \nhere but also html <br> newline and and and and and and it also wraps long lines")
c.font_(Font.new(\Courier).size_(16))
c.background_(Color.yellow); //change if you don't want the default transparent; setting transparency currently doesn't work
c.backgroundColor_(Color.white);
c.textColor_(Color.red);
c.textAlign_(\justify)
c.remove

c.backgroundBlink(12, period: 0.2)

c = WsStaticText.init(a)
c.addToPage


//text, labels
d = WsStaticText.new(a, Rect(0.2, 0.2, 0.3, 0.3));
// d.string_("My text with newline \nhere but also html <br> newline and and and and and and it also wraps long lines")
d.background_(Color.yellow); //change if you don't want the default transparent; setting transparency currently doesn't work
d.remove

d.backgroundBlink(20, Color.green, Color.blue, 0.4)


//slider

//------------
e = WsEZSlider.new(a, Rect(0.1, 0.3, 0.5, 0.1)); //horizontal
e = WsEZSlider.new(a, Rect(0.1, 0.3, 0.1, 0.5)); //vertical - still has some issues
//EZSlider with control spec;
e.controlSpec_(\freq.asSpec) //only in EZSlider;
e.bounds // getter
e.bounds(Rect(0,0,100,100))
e.value_(0.5)
e.background_(Color.green) //only color styling available
e.value
e.action_({|sl| postf("slider value: %\n", sl.value)}) //now the actual object is passed
e.valueAction_(2000);
e.value
e.remove

e = WsEZSlider.init(a, Rect(0.1, 0.3, 0.1, 0.4)); //no styling currently available
e.addToPage
e.remove

//without controlSpec
k = WsSlider.new(a, Rect(0.1, 0.1, 0.1, 0.4)); //no styling currently available
//add EZSlider with control spec;
k.value_(0)
k.value_(0.7)
k.value
k.action_({|sl| postf("slider value: %\n", sl.value)}) // object is passed
k.valueAction_(0.3);
k.remove

k = WsSlider.init(a, Rect(0.5, 0.3, 0.1, 0.4)); //no styling currently available
k.addToPage

//input text box
l = WsInput.new(a, Rect(0.5, 0.3, 0.1, 0.05)); //no styling currently available
//add EZSlider with control spec;
l.value_("blah")
l.value_(0.7)
l.value
l.action_({|sl| postf("slider value: %\n", sl.value)}) //now the actual object is passed
l.action = nil
l.textColor_(Color.red)
l.backgroundColor_(Color.black)
l.font_(Font.new(\Georgia).size_(16))
l.font_(Font.new(size: 16))
l.remove
l.bounds_(Rect(0.5, 0.3, 0.2, 0.1))

//image
//image can be placed anywhere; internally a symlink is created for each image
d = WsImage.new(a, Rect(0.2, 0.2, 0.8, 0), "~/Desktop/soundWalkMarcin_fromJake sm.jpg".standardizePath); //0 for width or height will default to default image's w-h ratio
d = WsImage.new(a, Rect(0.2, 0.2, 0.3, 0)); //path doesn't have to be initialized
d.path_("localFile");
d.path_("/Library/Desktop Pictures/Earth Horizon.jpg");
d.path_("/Library/Desktop Pictures/Foxtail Barley.jpg");
d.path_("http://artslink.files.wordpress.com/2011/05/bfaposter.jpg", true); //when setting the path, second argument specifies wether it's a URL... (default: false) so one can use url or a local file
d.action_({|val| "image is clickable!".postln});
d.remove

d = WsImage.init(a, Rect(0.2, 0.2, 0.3, 0)); //path doesn't have to be initialized
d.path_("localFile");
d.path_("http://artslink.files.wordpress.com/2011/05/bfaposter.jpg", true); //when setting the path, second argument specifies wether it's a URL... (default: false) so one can use url or a local file
d.action_({|val| "image is clickable!".postln});
d.addToPage
d.remove



//popup menu
f = WsPopUpMenu.new(a, Rect(0.5, 0.5, 0.4, 0.4));
f.items_(['-','first', 'second','A third test'])
f.font_(Font.new(\Georgia).size_(72))
f.background_(Color.green); //bug or peculiarity of certain browsers - the size of the menu (and font to some extent) will not have proper height unless you specify background color...
f.stringColor_(Color.red);
f.action_({|mn| "menu val: ".post; mn.value.postln; "item: ".post; mn.item.postln;}) //change to pass the actual object nad obj.item text obj.value index; indexOf
f.value_(1) //index
f.item //text
f.item_("second") //NOTE: not implemented
// f.item_("second") //end up not implementing since would require iteration
f.valueAction_(0);
f.value
f.remove

f = WsPopUpMenu.init(a, Rect(0.5, 0.5, 0.4, 0.4));
f.items_(['-','first', 'second','A third test'])
f.font_(Font.new(\Georgia).size_(72))
f.background_(Color.green); //bug or peculiarity of certain browsers - the size of the menu (and font to some extent) will not have proper height unless you specify background color...
f.stringColor_(Color.red);
f.action_({|mn| "menu val: ".post; mn.value.postln; "item: ".post; mn.item.postln;}) //change to pass the actual object nad obj.item text obj.value index; indexOf
f.addToPage
f.value_(1) //index
f.item //text
// f.item_("second") //end up not implementing since would require iteration
f.valueAction_(0);
f.value
f.remove

//checkbox... not sure if useful when not resizable
g = WsCheckbox.new(a, Rect(0.0, 0, 0.2, 0.2)); //checkbox; not resizable due to browser restrictions. Eventually this might change but requires more work with CSS
g.value_(1)
g.value
g.value_(false)
g.value
g.action_({|cb| "check: ".post; cb.value.class.postln;}) //wsCheckbox passed; val 0/1
g.remove

g = WsCheckbox.init(a, Rect(0.0, 0, 0.2, 0.2)); //checkbox; not resizable due to browser restrictions. Eventually this might change but requires more work with CSS
g.value_(1)
g.addToPage
g.value
g.value_(0)
g.valueAction_(1)
g.action_({|cb| "checker: ".post; cb.value.postln;}) //wsCheckbox passed; val 0/1
g.remove

a.free


// LAYOUTS

// WsGUI.killPython // if needed... shouldn't be.
a = WsWindow.new("title", 8000, true); //specify port for using internal static web server; nil when serving www folder using another web server

"http://localhost:8000".openOS;
a.free

//init only, send to page later
b = WsSimpleButton.init(a, nil); // no bounds specified
b.string_("string")
b.font_(Font.new(\Georgia).size_(24))
b.bounds_(Rect(0.3, 0.9, 0.5, 0.05)) // bounds still settable before sending to the page
b.bounds
b.bounds_(Rect(0.3, 0.05, 0.5, 0.5)) // bounds still settable before sending to the page
b.bounds
// now send
b.addToPage
b.backgroundColor_(Color.green);
b.textColor_(Color.red);
b.action_({"bu1".postln}) //just a function
b.remove

a.free; [~b1, ~b2, ~b3, ~b4, ~b5].do(_.remove)


 // create 3 buttons
[~b1, ~b2, ~b3, ~b4, ~b5].do(_.remove)
( // TODO init doesn't need parent or x/y, just width height
~b1 = WsSimpleButton.init(a, Rect(0,0,0.3,0.2)).string_("1");
~b2 = WsSimpleButton.init(a, Rect(0,0,0.3,1.0)).string_("2");
~b3 = WsSimpleButton.init(a, nil).string_("3"); // no bounds specified

~b4 = WsSimpleButton.init(a, Rect(0,0,0.3,0.4)).string_("4"); // no bounds specified
~b5 = WsSimpleButton.init(a, Rect(0,0,0.7,0.4)).string_("5"); // no bounds specified
)

// adding and removing elements within a layout
(
c = 4.collect{WsCheckbox.init(a, nil)};
t = 4.collect{|i|WsStaticText.init(a, nil).string_("test"++i)};
d = [c, t].lace(8).clump(2)
)
(
a.layout_(
	WsVLayout(nil,
		WsSimpleButton.init(a, Rect(0,0,0.3,0.2)).string_("1"),
		l = WsHLayout( Rect(0.1,0.1, 1, 0.1),
			*d.collect{|el|WsVLayout(nil, *el)}
		),
		WsSimpleButton.init(a, Rect(0,0,0.3,0.2)).string_("2")
	)
)
)
// remove middle layout
l.remove
// re-create the widgets, or others
(
c = 4.collect{WsCheckbox.init(a, nil)};
t = 4.collect{|i|WsStaticText.init(a, nil).string_("test"++i)};
// different number of widgets
d = [c, t].lace(8).clump(2).drop(4.rand)
)
// we know the former layout bounds
l.bounds
// and individual widgets if necessary
l.elements.do{|lay| lay.elements.do{|el| el.bounds.postln}}
// lay out the new widgets in the layout
(
a.layout_(
	l = WsHLayout(l.bounds,
		*d.collect{|el| WsVLayout(nil, *el)}
	)
)
)

// clear
a.clear


(
a.layout_( WsHLayout(nil, //Rect(0.15,0.05,0.45, 0.7),
	~b1, 0.05, WsHLayout(nil, ~b5, ~b4), ~b2, nil, ~b3
	)
)
)

(
a.layout_( WsVLayout(nil, //Rect(0.15,0.05,0.45, 0.7),
	~b1, 0.05, ~b2, nil, ~b3
	)
)
)

(
a.layout_( WsVLayout(nil, //Rect(0.15,0.05,0.45, 0.7),
	~b1, 0.05, WsHLayout(nil, ~b5, ~b4), ~b2, nil, ~b3
	)
)
)

(
a.layout_( WsVLayout(nil, //Rect(0.15,0.05,0.45, 0.7),
	*[~b1, ~b2, nil, ~b3]
	)
)
)

[~b1, ~b2, ~b3, ~b4, ~b5].do(_.remove)

a.clear

~buts = 24.collect{|i| WsSimpleButton.init(a, nil).string_(i.asString);}
(
a.layout_( WsHLayout(Rect(0.15,0.05,0.45, 0.7),
	*~buts.clump(6).collect{|butgrp| WsVLayout(nil, *butgrp ) }
	)
)
)

~buts.do(_.remove)
~buts = 24.collect{|i| WsSimpleButton.init(a, nil).string_(i.asString);}
(
a.layout_( WsHLayout(nil, //Rect(0.15,0.05,0.45, 0.7),
	*~buts.clump(4).collect{|butgrp| WsVLayout(nil, *butgrp ) }
	)
)
)

~buts.do(_.remove)
~buts = 512.collect{|i| WsSimpleButton.init(a, nil).string_(i.asString);}
(
a.layout_( WsVLayout(nil, //Rect(0.15,0.05,0.45, 0.7),
	*~buts.clump(16).collect{|butgrp| WsHLayout(nil, *butgrp ) }
	)
)
)

~buts.do(_.remove)
~buts = 96.collect{|i| WsSimpleButton.init(a, nil).string_(i.asString);}
(
a.layout_( WsHLayout( Rect(0.025,0.55,0.95, 1/3),
	*~buts.clump(16).collect{|butgrp,i|
		i.even.if(
			{ WsVLayout(Rect(0,0,0.05, 1), *butgrp ) },
			{ WsHLayout(Rect(0,0,0.5, 1), *butgrp ) }
		)
	}
	)
)
)






// MULTIPLE WINDOWS

WsWindow.startWwwServer(8000); //can be handled manually or when creating WsWindow; for multiple windows manual creation/freeing is advisable to avoid problems
WsWindow.stopWwwServer
WsWindow.wwwPid;

a = WsWindow.new("new title", true); //when no port is specified, no www server will be started, NOR ONE WILL BE STOPPED WHEN FREEING this the need to
a.free
//now point a web browser to http://localhost:8000 (or http://yourIP:8000)

o = WsWindow.new("different title", false); //second win!
o.free


a.isDefault_(false);//unset default


a.backgroundColor_(Color.blue);
o.backgroundColor_(Color.green); //second win

//init only, send to page later
b = WsSimpleButton.init(a, nil); // no bounds specified
b.string_("string")
b.font_(Font.new(\Georgia).size_(24))
b.bounds_(Rect(0.3, 0.9, 0.5, 0.05)) // bounds still settable before sending to the page
b.bounds
b.bounds_(Rect(0.3, 0.05, 0.5, 0.5)) // bounds still settable before sending to the page
b.bounds
// now send
b.addToPage
b.backgroundColor_(Color.green);
b.textColor_(Color.red);
b.action_({"bu1".postln}) //just a function
b.remove


//init only, send to page later
c = WsSimpleButton.init(o, nil); // no bounds specified
c.string_("string on another win")
c.font_(Font.new(\Georgia).size_(24))
c.bounds_(Rect(0.3, 0.9, 0.5, 0.05)) // bounds still settable before sending to the page
c.bounds
c.bounds_(Rect(0.3, 0.05, 0.5, 0.5)) // bounds still settable before sending to the page
c.bounds
// now send
c.addToPage
c.backgroundColor_(Color.green);
c.textColor_(Color.red);
c.action_({"bu2".postln}) //just a function
c.remove






// ------- todo ----------


//dropdown list - mostly done DONE
//propagate values between opened windows DONE
//autoconnect on js side DONE...sort of
//WsButton - multistate DONE
//gradient... not that important - not needed
//WsCheckbox DONE

//------EZ... for later, should be implemented in their separate DIVs
//WsEZCheckbox - with a label
//WsEZSlider
//WsEZPopUpMenu - with a label



//---------------
//old code
//----------------

h = WsEZCheckbox.new(a, Rect(0.0, 0, 0.2, 0.2)); //checkbox; not resizable due to browser restrictions. Eventually this might change but requires more work with CSS
h.checkboxObject.css_("vertical-align:top")
h.label_("my checkbox")
h.value_(1)
h.action_({|val| "check: ".post; val.postln;})
h.remove


//testing reliability
e = WsSlider.new(a, Rect(0.1, 0.3, 0.1, 0.4)); //no styling currently available
h = WsSlider.new(a, Rect(0.3, 0.3, 0.1, 0.4));

a = NetAddr("localhost", 62327);

(
Routine.run({
	1000.do({
		a.sendMsg('127.0.0.1:52085', '{"command":"update", "value":"' ++ 1.0.rand.asSymbol ++ '", "id":"0"}');
		a.sendMsg('127.0.0.1:52085', '{"command":"update", "value":"' ++ 1.0.rand.asSymbol ++ '", "id":"1"}');
		0.02.wait;
	});
})
)

(W
Routine.run({
	1000.do({
		a.sendMsg('192.168.200.103:50449', '{"command":"update", "value":"' ++ 1.0.rand.asSymbol ++ '", "id":"0"}');
		a.sendMsg('192.168.200.103:50449', '{"command":"update", "value":"' ++ 1.0.rand.asSymbol ++ '", "id":"1"}');
		0.02.wait;
	});
})
)

a.updateWidget(0)
a.guiObjects[\button0]

a.addWidget(\but0, \button, {|val| postf("but0 %\n", val)}, IdentityDictionary[(\backgroundColor -> Color.red), (\textColor -> Color.black), (\font -> Font.new.size_(16)), (\bounds -> Rect(0.2, 0.1, 0.2, 0.2)), (\innerHTML -> "myText!!!")])

a.addWidget(\m0, \menu, {|val| postf("but0 %\n", val)}, IdentityDictionary[(\backgroundColor -> Color.red), (\font -> Font.new.size_(16)), (\textColor -> Color.green), (\menuItems -> "".ccatList(["dupa","dupka","dupcia"]).copyToEnd(2)), (\bounds -> Rect(0.2, 0.1, 0.2, 0.2))])

a.guiObjects[4][0][\value] = "dupa"
a.guiObjects[4][0][\menuItems] = "cos,zupelnie"
a.guiObjects[4][0][\menuItems] = "raz,dwa,trzy"

a.updateWidget(4)

a.removeWidget(\m0)

a.addWidget(\sl, \slider, {|val| postf("but0 %\n", val)}, IdentityDictionary[(\font -> Font.new.size_(16)), (\bounds -> Rect(0.2, 0.3, 0.4, 0.2)), (\innerHTML -> "myText!!!")])

a.addWidget(\t1, \text, {|val| postf("but0 %\n", val)}, IdentityDictionary[(\textColor -> Color.black), (\font -> Font.new.size_(16)), (\bounds -> Rect(0.4, 0.6, 0.1, 0.1)), (\innerHTML -> "myText!!<br>!")])

a.guiObjects[2][0][\innerHTML] = "długi tekst czy się zawija?<br>nowa linia";

a.updateWidget(2)

b = (\color: Color.new, \dupa: "dupes")



a.removeWidget(\but0)
a.removeWidget(\t1)
a.removeWidget(\i1)
a.removeWidget(\sl)
a.removeWidget(\t1)


a.addWidget(\but1, \button, {|val| postf("received here! %\n", val)});
"color:sienna;margin-left:20px;"
a.addWidget(\but0, \button, {|val| postf("received here! %\n", val)}, addDict: IdentityDictionary.new.putPairs([\style, "height:40px", \value, "cosss"]));

a.addWidget(\but0, \button, {|val| postf("received here! %\n", val)}, addDict: IdentityDictionary.new.putPairs([\style, "height:80%; width:80%; font-size:32pt; font-family: Georgia; position: absolute; top: 10%; left: 0%; background-color: #E3E1B8; color: #00E3A2", \innerHTML, "cosss"])); //problem with long messages...?

a.addWidget(\i1, \image, {|val| postf("received here! %\n", val)}, addDict: IdentityDictionary.new.putPairs([\style, "position:absolute; top:0%; left:2%; height:30%", \src, "screen1.png"]));

a.addWidget(\sl, \slider, {|val| postf("received here slider! %\n", val)}, addDict: IdentityDictionary.new.putPairs([\style, "width: 80%; height: 50%; background-color: #E3E1B8; -webkit-appearance: slider-vertical; ", \min, 0, \max, 1, \step, "0.0001"]));

a.addWidget(\sl, \slider, {|val| postf("received here slider! %\n", val)}, addDict: IdentityDictionary.new.putPairs([\style, "width: 80%; height: 50%; background-color: #E3E1B8; ", \min, 0, \max, 1, \step, 0.00001]));
Font.new.dump

a.guiObjects
b  = a.prepareJSONcommandId(\add, 0, a.guiObjects[0][0])
b

c  = a.prepareJSON(a.guiObjects[0][0])
a.removeWidget(\i1)
a.guiObjects[1][0]
a.namesToIDs[\but0]
b = a.namesToIDs.copy
b.removeAt(\but0)
b
a.sendMsgToAll("[\"add\", 1]")
a.sendMsgToAll("'[2, 1]'")
a.send(a.prepareJSON(\add, \button0, d));
a.prepareJSON(\add, \button0, d).parseYAML //parsing JSON!!!!
a.send("{\"command\":\"add\",\"id\":\"butt3\"}");

d = IdentityDictionary(know: true)
d.put(\command, \add);
d.put(\id, \butt0);
"button0,0".split($,).cs[1].asFloat == 0

a.sendMsgToAll("document.body.appendChild('<button type=\"button\">Click Me!</button>')")

e = IdentityDictionary(know: true)

a.scSendNetAddr.sendMsg("/127.0.0.1:55018", "dupa", "cos")
e.put(0, "dupa")
e.put(1, "dupes")
e[\0.asInteger]

"/Volumes/data/Dokumenty/Roczne/Rok akademicki 2012-2013/dxSoundLab/SC web interface/sockets.py.sc/www".escapeChar($ )

c.postln;

OSCFunc.trace(false)
"cos".asSymbol

"127.0.0.1:54291".asSymbol

Quarks.gui


//should be input, not a button!
<body>

<input type="button" id="no" value="cos" onclick='changeName("no")'>Click Me!</input>
<script>
function changeName(id){
document.getElementById(id).value = "dupa";
}
</script>
</body>

NetAddr("localhost", 62491).sendMsg("/add", "first")

NetAddr("localhost", 8000).sendMsg("/remove", 'first')

NetAddr("localhost", 8000).sendMsg("/add", "second")

OSCFunc.trace

WsWindowServer.runInTerminal_(false)
// x = WsWindowServer(8010)
w = WsWindowServer(8000)
w.scSendNetAddr
w.nodePath
w.sendMsg("/add", "first")
WsWindowServer.nodePath.size
w.free
List
ShutDown.objects.last.def.code
ShutDown.objects.last.def.dump