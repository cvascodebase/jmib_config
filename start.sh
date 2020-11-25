#!/bin/bash
JPS_NAME="JMIB_SMS_Server.jar"
PID=`ps ax | grep $JPS_NAME | grep -v grep | awk {'print $1'}`
if [ $PID ];
then
  echo "JMIB Application already Running"
else
  {
    echo "******** Starting PYRO JMIB Application ********"
    sleep 5
    java -Xmx512m -server -jar JMIB_SMS_Server.jar > /dev/null 2> /dev/null &
  }
fi
