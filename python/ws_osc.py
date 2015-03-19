#!/usr/bin/env python
# -*- coding: utf-8 -*-

#SC webGUI by Marcin Pączkowski
#marcinp@uw.edu

# python WebSockets bridge
# uses pyOSC from https://trac.v2.nl/wiki/pyOSC
# and SimpleWebSocketServer from https://github.com/opiate/SimpleWebSocketServer

#this version requires RFC 6455 version of the protocol
#in practical terms (theoretically) supported browsers are:
#iOS 6+ Safar, Android 4.4+ Browser, Android Chrome 33.0+, Android Firefox 26.0+, Desktop Chrome 14.0+, Desktop Safari 6.0+, Desktop Opera 12.1+, Opera Mobile 12.1+, IE 10+, IE Mobile 10+
#tested only on current Chrome and Safari (Mac OSX)

# import struct
import sys
import urllib
# import SocketServer
# from base64 import b64encode
# from hashlib import sha1
# from mimetools import Message
# from StringIO import StringIO
# import threading
from threading import Thread
import OSC
# from OSC import OSCClient, OSCMessage, OSCServer
from SimpleWebSocketServer import WebSocket, SimpleWebSocketServer

# print ("sys.argv[0:]", sys.argv[0:])
if sys.argv[1:]:
    OSC_SEND = int(sys.argv[1]) # WS server OSC sent to
else:
    OSC_SEND = 57120
if sys.argv[2:]:
    OSC_RECEIVE = int(sys.argv[2]) # WS server OSC receiver port
else:
    OSC_RECEIVE = 7000
if sys.argv[3:]:
    OSC_ADDR = sys.argv[3] # OSC address path
else:
    OSC_ADDR = '/sockets'
# if sys.argv[4:]:
   # WS_PORT = int(sys.argv[4]) # OBSOLETE, auto-selecting now; non-privileged port, must match the one in javascript file
# else:
    # WS_PORT = 9999

OSC_IP = 'localhost'

print "Starting..."
print "OSC_SEND:", OSC_SEND
print "OSC_RECEIVE:", OSC_RECEIVE
print "OSC_ADDR:", OSC_ADDR
# print "WS_PORT:", WS_PORT

print "preparing OSC client (sending)"
oscClient = OSC.OSCClient()
oscClient.connect( (OSC_IP, OSC_SEND) )

print "preparing OSC server"
# osc_server = OSC.ThreadingOSCServer(("localhost", OSC_RECEIVE))
osc_server = OSC.ThreadingOSCServer(("127.0.0.1", OSC_RECEIVE))
# osc_server = OSC.ForkingOSCServer(("localhost", OSC_RECEIVE))


def main_osc_callback(path, tags, args, source):
    print ("in main osc callback")
    print (path)
    print (args)

def quit_callback(path, tags, args, source):
    print ("Python websockets: calling quit from OSC")
    stop_servers()

osc_server.addMsgHandler( '/ws', main_osc_callback )
osc_server.addMsgHandler( '/quit', quit_callback )

class WsOscBridge(WebSocket):
    
    def handleMessage(self):
        if self.data is None:
			self.data = ''
        print "Received from websocket: " + self.data
        this_addr_as_string_no_slash = self.address[0] + ':' + str(self.address[1]);
        oscMsg = OSC.OSCMessage()
        oscMsg.setAddress(OSC_ADDR)
        oscMsg.append('data')        
        oscMsg.append(this_addr_as_string_no_slash);
        oscMsg.append(str(self.data))
        oscClient.send(oscMsg)

    def handleConnected(self):
        print self.address, 'connected'
        #setup OSC here
        this_handler = self;
        this_addr_as_string = '/' + self.address[0] + ':' + str(self.address[1]);
        def osc_to_send_callback(path, tags, args, source):
            # print ("args: " + str(args));
            msg = args[0]
            # print ("msg: " + str(msg));
            this_handler.sendMessage(msg)
        osc_server.addMsgHandler(this_addr_as_string,  osc_to_send_callback )
        # print 'Added osc handler ' + this_addr_as_string;
        this_addr_as_string_no_slash = self.address[0] + ':' + str(self.address[1]);
        oscMsg = OSC.OSCMessage()
        oscMsg.setAddress(OSC_ADDR)
        oscMsg.append('add')
        oscMsg.append(this_addr_as_string_no_slash);
        oscClient.send(oscMsg)
        # print 'OSC message sent: ' + oscMsg;

    def handleClose(self):
        print self.address, 'closed'
        this_addr_as_string = '/' + self.address[0] + ':' + str(self.address[1]);
        osc_server.delMsgHandler(this_addr_as_string)
        this_addr_as_string_no_slash = self.address[0] + ':' + str(self.address[1]);
        oscMsg = OSC.OSCMessage()
        oscMsg.setAddress(OSC_ADDR)
        oscMsg.append('remove')
        oscMsg.append(this_addr_as_string_no_slash);
        oscClient.send(oscMsg)


# ws_server = SimpleWebSocketServer("0.0.0.0", WS_PORT, WsOscBridge)
ws_server = SimpleWebSocketServer("0.0.0.0", 0, WsOscBridge)
# print ws_server.serversocket.getsockname()[1];

#get assigned port numer
WS_PORT = ws_server.serversocket.getsockname()[1]
print "WS_PORT:", WS_PORT;
#send through OSC:
oscMsg = OSC.OSCMessage()
oscMsg.setAddress(OSC_ADDR)
oscMsg.append('wsport')        
oscMsg.append(WS_PORT);
oscClient.send(oscMsg)


def start_ws():
    ws_server.serveforever()

def start_osc():
    osc_server.serve_forever()
    
def start_servers():

    Thread(target = start_ws).start()
    start_osc()

def stop_servers():
    print 'stopping ws and osc servers...'
    osc_server.close();
    ws_server.close();
    # ws_server.shutdown();

if __name__ == "__main__":

    try:
        start_servers();

    except KeyboardInterrupt:
        print "Got ^C"
        stop_servers();
        print "bye!"
