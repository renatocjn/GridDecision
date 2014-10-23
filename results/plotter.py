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
ticks = linspace(min(timeline), max(timeline), 10)
dates = [ num2date(t).strftime("%d/%m/%Y") for t in ticks ]

subplot(221)
xticks(ticks, dates, rotation=20)
title("Wait Time Evolution")
plot_date(timeline, waitTime, fmt='-')

subplot(223)
xticks(ticks, dates, rotation=20)
title("Histogram of Submission")
hist(timeline)

subplot(224)
ticks = linspace(waitTime.min(), waitTime.max(), 10)
ticks_str = [ timedelta(seconds=t) for t in ticks ]
xticks(ticks, ticks_str, rotation=20)
title("Histogram of Wait time")
hist(waitTime)


show()
