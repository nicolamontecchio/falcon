#! /usr/bin/python
import os
import sys
import numpy as np

# compute some statistics on a folder containing hash files, namely:
# avg and variance of file length (in number of hashes)
if __name__ == "__main__":
	if len(sys.argv) != 2 :
		print 'error - folder not specified'
		exit(1)
	folder = sys.argv[1]
	lengths = []
	for f in os.listdir(folder) :
		if f[0] != '.' :
			data = np.genfromtxt(os.path.join(folder,f))
			lengths.append(len(data))
	lengths = np.array(lengths)
	print sum(lengths)/len(lengths)
	