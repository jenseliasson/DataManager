#!/usr/bin/python
import urllib2
import time
import json
import random
import thread
from threading import Semaphore, BoundedSemaphore
import serial

motes = []
historian = 'http://127.0.0.1:4001'
sr = 'http://127.0.0.1:8442/serviceregistry'

def getSREnpoints():
  client = urllib2.build_opener(urllib2.HTTPHandler)
  jsonr = """{
  "service": {
    "serviceDefinition": "_ahfc-dm-historian._http._tcp",
    "interfaces": [
      "HTTPS-TLS-JSON+SENML"
    ],
    "serviceMetadata": {
      "security"=""
    }
  },
  "pingProviders": false,
  "metadataSearch": false
}"""

  request = urllib2.Request(sr + '/query', data=jsonr)
  request.add_header('Content-Type', 'application/json')
  request.get_method = lambda: 'PUT'
  ret = client.open(request)
  str = ret.read()
  jsono = json.loads(str)
  print jsono['serviceQueryData'][0]['providedService']['interfaces'][0]

  return str

def worker(mote, delay):
	while True:
	  try:
		sensor_sem.acquire()
		json ='[\n  {\"bn\":\"' + mote + '\", \"bt\":'+str(int(time.time()))+'},\n'
		json += '  {"n":"/3303/0/5700","u":"Cel","v":' +str(round(random.uniform(22.0, 22.5), 2))+'}\n'
		sensor_sem.release()
		#json += '  {"n":"/3303/0/5700","u":"Cel", "t": 1, "v":' +str(round(random.uniform(12.0, 22.5), 2))+'}\n'
		json += ']'
		print json
	  	client = urllib2.build_opener(urllib2.HTTPHandler)
		request = urllib2.Request(historian + '/storage/'+mote, data=json)
		request.add_header('Content-Type', 'application/json')
		request.get_method = lambda: 'PUT'
		resp = client.open(request)
		time.sleep(delay)

	  except:
		print ""#Error: unable to create client com"

#for x in range(1, 2):
#  motes.append("mote"+str(x))
motes.append("mote91")

#url = getSREnpoints()
#print url
#exit(0)

sensor_sem = BoundedSemaphore(value=1)

try:
  for mote in motes:
    thread.start_new_thread( worker, (mote, 5) )
except:
   print "Error: unable to start thread"

while 1:
   pass

