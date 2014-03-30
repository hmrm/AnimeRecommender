#!/bin/bash

while true
do
    sbt run | tee -a logfile
done;
