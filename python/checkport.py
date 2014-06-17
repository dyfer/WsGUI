import sys
import socket

# print ("sys.argv[0:]", sys.argv[0:])
if sys.argv[1:]:
    PORT = int(sys.argv[1]) # PORT to check; 0 gets next free port
else:
    PORT = 0
if sys.argv[2:]:
    PROTOCOL = str(sys.argv[2]) # TCP or UDP
else:
    PROTOCOL = 'TCP'

if PROTOCOL == 'TCP':
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM) #TCP
else:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM) # UDP

try:
    sock.bind(('', PORT))
except:
    # print "Port " + str(PORT) + " " + PROTOCOL + " not available"
    print 'failed'
    sys.exit(1)

addr = sock.getsockname()
print addr[1] #prints the checked or new port
sock.close()
