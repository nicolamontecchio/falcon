#! /usr/bin/python

# Convert a chroma matrix from csv format
# to falcon format. Operates on stdin and stdout.
# Does not check for errors.

import sys
import csv

if __name__ == "__main__":
	lines = []
	reader = csv.reader(sys.stdin, delimiter=',')
	for line in reader :
		lines.append(';'.join(line) + ';')
	print '12'
	print len(lines)
	for line in lines :
		print line
	
