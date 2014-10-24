#!/usr/bin/python

from csv import *
from sys import argv
from pylab import *

fp = open(argv[1]); fp.next()
checkpoints = reader(fp)

timestamps = list()
usedPEs = list()
jobsExec = list()
jobsQueue = list()
for ckp in checkpoints:
	if not ckp: continue
	timestamps.append(float(ckp[0]))
	usedPEs.append(float(ckp[1]))
	jobsExec.append(float(ckp[2]))
	jobsQueue.append(float(ckp[3]))
timestamps = epoch2num(timestamps)
idxs = np.argsort(timestamps)
usedPEs = np.array(usedPEs)[idxs]
jobsExec = np.array(jobsExec)[idxs]
jobsQueue = np.array(jobsQueue)[idxs]

subplot(211)
title("Usage of cores")
plot_date(timestamps, usedPEs, fmt='-', label="number of used cores")
plot_date(timestamps, [ 48*12 ] * timestamps.size , color='red', fmt='-', label='total number of cores')
legend()

subplot(212)
title("Job Status")
plot_date(timestamps, jobsExec, fmt='-', color='green', label="Jobs Executing")
plot_date(timestamps, jobsQueue, fmt='-', color='red', label="Jobs waiting in queue")
legend()

show()
