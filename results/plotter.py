#!/usr/bin/python

from datetime import timedelta
from csv import *
from pylab import *
from sys import argv

fp = open(argv[1], 'r')
fp.next() #skip line with headers

input_gridlets = reader(fp)
timeline, waitTime = list(), list()
for gridlet in input_gridlets:
	timeline.append(float(gridlet[1])) #submission time
	
	metric = float(gridlet[2]) #time waiting in queue
	waitTime.append(metric) 
grid(True)
idxs = np.argsort(timeline)
timeline = epoch2num(np.array(timeline)[idxs])
waitTime = np.array(waitTime)[idxs]
time_ticks = linspace(min(timeline), max(timeline), 10)
time_ticks_str = [ num2date(t).strftime("%d/%m/%Y") for t in time_ticks ]

wait_ticks = linspace(waitTime.min(), waitTime.max(), 10)
wait_ticks_str = [ timedelta(seconds=t) for t in wait_ticks ]

subplot(311)
xticks(time_ticks, time_ticks_str)
yticks(wait_ticks, wait_ticks_str)
title("Wait Time")
xlabel("Timeline")
ylabel("Time Waiting")
plot_date(timeline, waitTime, fmt='o')

subplot(312)
xticks(time_ticks, time_ticks_str)
title("Histogram of amount of Submission")
xlabel("Timeline")
ylabel("Amount of Submissions")
hist(timeline)

subplot(313)
xticks(wait_ticks, wait_ticks_str, rotation=15)
title("Histogram of Wait time of submissions")
xlabel("Time waited")
ylabel("Amount of Submissions")
hist(waitTime)


show()
