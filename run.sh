#!/bin/bash

while true
do
    echo "Starting up" | tee -a logfile
    sbt run | tee -a logfile
    echo "Stopped, restarting" | tee -a logfile
done;
