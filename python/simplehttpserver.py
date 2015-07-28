#simple web server with reuse address
#usage: python simplehttpserver.py port serve_directory

import socket
import sys
import os
import SimpleHTTPServer
import SocketServer
# import os # uncomment if you want to change directories within the program

# print ("sys.argv[0:]", sys.argv[0:])
if sys.argv[1:]:
    PORT = int(sys.argv[1]) # WS server OSC sent to
else:
    PORT = 8000
if sys.argv[2:]:
    DIR = str(sys.argv[2]) # WS server OSC receiver port
else:
    DIR = "."


print "Serving directory", DIR, "on port", PORT

# Absolutely essential!  This ensures that socket resuse is setup BEFORE
# it is bound.  Will avoid the TIME_WAIT issue

class MyTCPServer(SocketServer.TCPServer):
    def server_bind(self):
        self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.socket.bind(self.server_address)

Handler = SimpleHTTPServer.SimpleHTTPRequestHandler

httpd = MyTCPServer(("", PORT), Handler)

os.chdir(DIR)

if __name__ == "__main__":

    try:
        httpd.serve_forever();

    except KeyboardInterrupt:
        print "Got ^C"
        httpd.shutdown();
        print "bye!"




# httpd.shutdown() # If you want to programmatically shut off the server