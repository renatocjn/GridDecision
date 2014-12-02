#!/usr/bin/python

from time import mktime, strptime
from numpy import array, sqrt, ceil, floor, arange, zeros, argsort,mean
from datetime import timedelta
from matplotlib.pyplot import *
from csv import reader
"""
	assuming output from: sacct -a -P -sCD -S <start_time> --format JobID,JobName,Partition,User,NCPUS,Submit,Start,End
"""
 
timeParser = lambda timestr: mktime(strptime(timestr.strip(), "%Y-%m-%dT%H:%M:%S"))

def parseJobs(filepath, user=None, jobName=None):
	_1min = 360 #seconds
	finput = open(filepath)
	finput.next() #skip header line
	jobs = list()
	for line in finput:
		line = line.split('|')
		if (jobName and line[1] != jobName) or \
		      (user and line[2] != user):
			continue

		job = dict()
		job['jobid'] = line[0]
		job['jobname'] = line[1]
		job['partition'] = line[2]
		job['user'] = line[3]
		job['ncpus'] = int(line[4])
		job['submitTime'] = timeParser(line[5])
		job['queueTime'] = timeParser(line[6]) - timeParser(line[5])
		job['runTime'] = timeParser(line[7]) - timeParser(line[6])
		
		if job['runTime'] < _1min or not job['jobid'].isdigit(): continue
		jobs.append(job)
	finput.close()
	return jobs


### Group jobs by a attribute, can filter by user or jobname
def jobsBy(attr, filepath, user=None, jobname=None):
	jobs = parseJobs(filepath, user, jobname)
	out = dict()
	for job in jobs:
		if job[attr] not in out:
			out[job[attr]] = list()
		out[job[attr]].append(job)
	del jobs
	return out

### Aliases to the jobsBy function
jobsByUser = lambda filepath, user=None, jobname=None: jobsBy('user', filepath, user, jobname)
jobsByJobName = lambda filepath, user=None, jobname=None: jobsBy('jobname', filepath, user, jobname)

def jobsByCpuUsage(filepath, user=None, jobname=None):
	jobs = parseJobs(filepath, user, jobname)
	out = { "not_parallel": list(), "parallel": list() }
	for job in jobs:
		if job['ncpus'] <= 12:
			out["not_parallel"].append(job)
		else:
			out["parallel"].append(job)
	del jobs
	return out

### Statistical functions
mean = lambda num_list: array(num_list).mean()
std = lambda num_list: array(num_list).std()
ci   = lambda psize, std: 1.96 * std / sqrt(psize) # 95% confidence

### Apply statistics to a job after been grouped
def applyStatistics(groupedJobs):
	statistics = dict()
	for group, jobs in groupedJobs.viewitems():
		statistics[group] = dict()
		njobs = len(jobs)
		for k in ['queueTime', 'runTime']:
			values = [ jobs[i][k] for i in xrange(njobs) ]

			statistics[group][k] = dict()
			statistics[group][k]['mean'] = mean(values)
			statistics[group][k]['std']  = std(values)
			statistics[group][k]['ci']  = ci(njobs, std(values))
			statistics[group][k]['min']  = min(values)
			statistics[group][k]['max']  = max(values)
		statistics[group]['njobs'] = njobs
			
	return statistics

'''	
jobs = parseJobs('test_jobs.txt')
fp = open('real_original_all.csv', 'w')
fp.write('Gridlet ID,SubmissionTime,QueueTime,RunTime,State\n')
for job in jobs:
	fp.write('%s,'%job['jobid'])
	fp.write('%s,'%job['submitTime'])
	fp.write('%s,'%job['queueTime'])
	fp.write('%s,'%job['runTime'])
	fp.write('Success\n')
fp.close()

### print jobs on screen
jobs = jobsByCpuUsage('all_year_jobs.txt')
#jobs = jobsByUser('all_year_jobs.txt')

sizes = [ len(jobs[k]) for k in jobs.keys() ]
sizes.sort()
print 'numero de jobs'
print 'total users', len(sizes)
print '    min    ', min(sizes)
print '   media   ', round(mean(sizes))
print '    max    ', max(sizes)

ci_min = floor(mean(sizes)) - floor(ci(len(sizes), std(sizes)))
ci_max = ceil(mean(sizes)) + ceil(ci(len(sizes), std(sizes)))
print '  ci range', ci_min, '~', ci_max 

sizes = array(sizes)
print sizes
print


jobs = jobsByJobName('all_year_jobs.txt')
jobs_info = applyStatistics(jobs)
for jobname, stats in jobs_info.viewitems():
	if stats['njobs'] < 10: continue
	print jobname
	print "\tnjobs", stats['njobs']
	print "\tUsers", list({ job['user'] for job in jobs[jobname] })
	for t in ['queueTime', 'runTime']:
		print '\tprinting', t
		print "\t\tmin  ", timedelta(seconds=stats[t]['min'])
		print "\t\tmean ", timedelta(seconds=stats[t]['mean'])
		print "\t\tmax  ", timedelta(seconds=stats[t]['max'])
		print "\t\tstd  ", timedelta(seconds=stats[t]['std'])
		m = stats[t]['mean']
		ci = stats[t]['ci']
		print "\t\trange", timedelta(seconds=(m - ci)), '~', timedelta(seconds=(m + ci))
	print

### write to file by jobs cpu usage
Jobs = jobsByCpuUsage('test_jobs_all.txt')
i = 1
output = open("workload_not_parallel12.jobs","w")
for job in Jobs['not_parallel']:
	output.write( "%i\t%i\t%i\t%i\t%s\n" % (i, job['submitTime'], job['runTime'], job['ncpus'], job['partition'] ) )
	i+=1
output.close()

output = open("workload_parallel12.jobs","w")
for job in Jobs['parallel']:
	output.write( "%i\t%i\t%i\t%i\t%s\n" % (i, job['submitTime'], job['runTime'], job['ncpus'], job['partition'] ) )
	i+=1
output.close()
'''
i = 1 
Jobs = parseJobs('test_jobs_all.txt')
output = open("workload_reduced_all.jobs","w")
for job in Jobs:
	output.write( "%i\t%i\t%i\t%i\t%s\n" % (i, job['submitTime'], job['runTime'], job['ncpus'], job['partition'] ) )
	i+=1
output.close()
'''

Jobs = parseJobs('test_jobs_all.txt')
#Jobs = jobsByCpuUsage('test_jobs_all.txt')['parallel']
#Jobs = jobsByCpuUsage('test_jobs_all.txt')['not_parallel']
counters = dict()
for j in Jobs:
	nmachines = np.ceil(float(j['ncpus'])/12.0)
	if nmachines not in counters:
		counters[nmachines] = 0
	counters[nmachines] += 1
y = array(counters.keys())
x = array(counters.values())
idx = y.argsort()
x, y = x[idx], y[idx]
title('All jobs between 04/2014 to 10/2014')
xlabel('Numero de jobs (escala logaritmica)')
yticks(range(13))
ylabel('Numero de maquinas requisitadas')
xscale('symlog')
#hist(cpuCounts, bins=5)

plot(x, y, 'o--', label='Valores reais', linewidth=1.5)

for i,j in counters.viewitems():
	annotate("(%d, %d)"%(j,i), xy=(j,i))

grid(True)
legend()
show()

Jobs = parseJobs('test_jobs_all.txt')
vals = [ j['ncpus'] for j in Jobs ]
#_, bins, _ = hist(vals, log=True, label='Todos os jobs')

Jobs = jobsByCpuUsage('test_jobs_all.txt')['parallel']
vals = [ j['ncpus'] for j in Jobs ]
hist(vals, log=True, alpha=0.5, label='Jobs paralelos')

Jobs = jobsByCpuUsage('test_jobs_all.txt')['not_parallel']
vals = [ j['ncpus'] for j in Jobs ]
#hist(vals, log=True, alpha=0.5, label='Jobs nao paralelos')

ylabel('Numero de Jobs')
xlabel('Numero de CPUs requisitadas')
legend()
show()

Jobs = parseJobs('test_jobs_all.txt')
vals = [ j['queueTime'] for j in Jobs ]
y1, x, _ = hist(vals, bins=20, label="Resultados dos traces")
#plot(x, y)

fp = open('results/real_original_all.csv', 'r')
fp.next() #skip line with headers

input_gridlets = reader(fp)
waitTime = list()
for gridlet in input_gridlets:
	metric = float(gridlet[2]) #time waiting in queue
	waitTime.append(metric) 
y2, _, _ = hist(waitTime, bins=x, label="Simulado completamente")

fp = open('results/reduced_parallel12.csv', 'r')
fp.next() #skip line with headers

input_gridlets = reader(fp)
waitTime = list()
for gridlet in input_gridlets:
	metric = float(gridlet[2]) #time waiting in queue
	waitTime.append(metric) 
y3, _, _ = hist(waitTime, bins=x, label="Simulacao paralela apenas")

#yscale('log')
#legend()
#show()
for i, j, k, l in zip(x, y1, y2, y3):
	print i, '/', timedelta(seconds=i), '/', j, '/', k, '/', l

fp = open('results/reduced_parallel12.csv')
fp.next() #skip line with headers

from csv import reader

input_gridlets = reader(fp)
timeline, waitTime = list(), list()
for gridlet in input_gridlets:
	timeline.append(float(gridlet[1])) #submission time
	
	metric = float(gridlet[2]) #time waiting in queue
	waitTime.append(metric)

hist(waitTime, bins=x, label='Parallel Workload Only')

xlabel('Tempo esperado')
ylabel('Quantidade de jobs (escala logaritmica)')
xticks(x[::5], [ timedelta(seconds=i) for i in x[::5] ], rotation=10)
yticks([], [])

title('All jobs between 04/2014 to 10/2014')
grid(True)
legend()
show()

'''





