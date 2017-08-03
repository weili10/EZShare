# EZShare

## Distributed resources share system

Server command line argument:  
-advertisedhostname <arg>       advertised hostname  
-connectionintervallimit <arg>  connection interval limit in seconds  
-exchangeinterval <arg>         exchange interval in seconds  
-port <arg>                     server port, an integer  
-secret <arg>                   secret  
-debug                          print debug information  
  
### Example server output when just started
java -cp ezshare.jar EZShare.Server  
20/03/2017 01:17:57.953 - [EZShare.Server.main] - [INFO] - Starting the EZShare Server  
20/03/2017 01:17:57.979 - [EZShare.ServerControl.] - [INFO] - using secret: 5uv1ii7ec362me7hkch3s7l5c4  
20/03/2017 01:17:57.981 - [EZShare.ServerControl.] - [INFO] - using advertised hostname: aaron9010  
20/03/2017 01:17:57.984 - [EZShare.ServerIO.] - [INFO] - bound to port 3780  
20/03/2017 01:17:57.986 - [EZShare.ServerExchanger.] - [INFO] - started  
  
## Client command line argument:
-channel <arg>         channel  
-debug                 print debug information  
-description <arg>     resource description  
-exchange              exchange server list with server  
-fetch                 fetch resources from server  
-host <arg>            server host, a domain name or IP address  
-name <arg>            resource name  
-owner <arg>           owner  
-port <arg>            server port, an integer  
-publish               publish resource on server  
-query                 query for resources from server  
-remove                remove resource from server  
-secret <arg>          secret  
-servers <arg>         server list, host1:port1,host2:port2,...  
-share                 share resource on server  
-tags <arg>            resource tags, tag1,tag2,tag3,...  
-uri <arg>             resource URI  
-subscribe             subscribe the resources that match the template  
  
### Example command lines
java -cp ezshare.jar EZShare.Client -query -channel myprivatechannel -debug  
java -cp ezshare.jar EZShare.Client -exchange -servers 115.146.85.165:3780,115.146.85.24:3780 -debug  
java -cp ezshare.jar EZShare.Client -fetch -channel myprivatechannel -uri file:///home/aaron/EZShare/ezshare.jar -debug  
java -cp ezshare.jar EZShare.Client -share -uri file:///home/aaron/EZShare/ezshare.jar -name "EZShare JAR" -description "The jar file for EZShare. Use with caution." -tags jar -channel myprivatechannel -owner aaron010 -secret 2os41f58vkd9e1q4ua6ov5emlv -debug  
java -cp ezshare.jar EZShare.Client -publish -name "Unimelb website" -description "The main page for the University of Melbourne" -uri http://www.unimelb.edu.au -tags web,html -debug  
java -cp ezshare.jar EZShare.Client -query  
java -cp ezshare.jar EZShare.Client -remove -uri http://www.unimelb.edu.au  
