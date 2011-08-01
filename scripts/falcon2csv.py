#! /usr/bin/python

# Convert a chroma matrix from old falcon format
# to simpler csv format. Operates on stdin and stdout.
# Does not check for errors.

import sys
import csv

__author__="nicolamontecchio"
__date__ ="$Jan 20, 2011 1:22:02 PM$"

if __name__ == "__main__" :
	reader = csv.reader(sys.stdin, delimiter=';')
	lineno = 0
	for line in reader :
		if lineno >= 2 :
			print ','.join(line[:-1])
		lineno += 1
	


	
	
		
