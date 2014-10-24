#!/usr/bin/python

from time import mktime, strptime
from numpy import array, sqrt, ceil, floor
from datetime import timedelta
"""
	assuming output from: sacct -a -P -sCD -S <start_time> --format JobID,JobName,User,NCPUS,Submit,Start,End
"""
 
timeParser = lambda timestr: mktime(strptime(timestr.strip(), "%Y-%m-%dT%H:%M:%S"))

def parseJobs(filepath, user=None, jobName=None):
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
		job['user'] = line[2]
		job['ncpus'] = int(line[3])
		job['submitTime'] = timeParser(line[4])
		job['queueTime'] = timeParser(line[5]) - timeParser(line[4])
		job['runTime'] = timeParser(line[6]) - timeParser(line[5])
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
	out = { "singlecore": list(), "multicore": list() }
	for job in jobs:
		if job['ncpus'] > 1:
			out["singlecore"].append(job)
		else:
			out["multicore"].append(job)
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

jobs_info = applyStatistics(jobs)
for jobname, stats in jobs_info.viewitems():
	print jobname
	print "\tnjobs", stats['njobs']
	print "\tjobnames", len(list({ job['jobname'] for job in jobs[jobname] }))
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
'''
Jobs = jobsByCpuUsage('all_year_jobs.txt')
i = 1
output = open("workload_singlecore.jobs","w")
for job in Jobs['singlecore']:
	output.write( "%i\t%i\t%i\t%i\n" % (i, job['submitTime'], job['runTime'], job['ncpus'] ) )
	i+=1
output.close()

output = open("workload_multicore.jobs","w")
for job in Jobs['multicore']:
	output.write( "%i\t%i\t%i\t%i\n" % (i, job['submitTime'], job['runTime'], job['ncpus'] ) )
	i+=1
output.close()
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
