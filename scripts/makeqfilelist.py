#!/usr/bin/env python3
import sys, os

if __name__ == '__main__':
    for line in sys.stdin :
        fid = line[:line.find(',')]
        print(os.path.join(sys.argv[1], fid + sys.argv[2]))
