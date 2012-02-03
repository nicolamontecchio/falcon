#!/usr/bin/env python
# first and only argument is an integer specifying the subsampling rate
import sys

if __name__ == '__main__':
    ss = int(sys.argv[1])
    l = 0
    for line in sys.stdin :
        if l % ss == 0 :
            print(line.strip())
        l += 1
        
