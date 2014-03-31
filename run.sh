#!/bin/bash

while true
do
    echo "Starting up"
    sbt run | tee -a logfile
    echo "Stopped, restarting"
done;
