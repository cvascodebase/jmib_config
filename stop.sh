PSRUNNING=`ps aux | grep -v grep | grep JMIB_SMS_Server.jar| awk {'print $2'} `

echo "PID = " $PSRUNNING

kill -9 $PSRUNNING
