#!/usr/bin/env python

"""Downloads and decompresses required census files for pizza."""

import os
import os.path
import subprocess as sp

def download(url, f):
  sp.check_call('curl -o %s %s' % (f, url), shell=True)

if not os.path.exists('census'): os.mkdir('census')
os.chdir('census')
if not os.path.exists('addrfeat'): os.mkdir('addrfeat')
if not os.path.exists('place'): os.mkdir('place')

os.chdir('addrfeat')
addrfeat_text = sp.check_output('curl -s ftp://ftp.census.gov/geo/tiger/TIGER2012/ADDRFEAT/', shell=True)
addrfeat_lines = addrfeat_text.strip().split('\n')
addrfeat_files = [x.split()[-1] for x in addrfeat_lines]
addrfeat_size = sum(int(x.split()[-5]) for x in addrfeat_lines)
print("Downloading %.3f MB of ADDRFEAT data in %d files" % (addrfeat_size / 1048576.0, len(addrfeat_files)))
for f in addrfeat_files:
  if os.path.exists(f): continue
  print("Downloading " + f)
  download('ftp://ftp.census.gov/geo/tiger/TIGER2012/ADDRFEAT/' + f, f)
  print("Unzipping " + f)
  sp.check_call('unzip ' + f, shell=True)

os.chdir('..')
os.chdir('place')

place_text = sp.check_output('curl -s ftp://ftp.census.gov/geo/tiger/TIGER2012/PLACE/', shell=True)
place_lines = place_text.strip().split('\n')
place_files = [x.split()[-1] for x in place_lines]
place_size = sum(int(x.split()[-5]) for x in place_lines)
print("Downloading %.3f MB of PLACE data in %d files" % (place_size / 1048576.0, len(place_files)))
for f in place_files:
  if os.path.exists(f): continue
  print("Downloading " + f)
  download('ftp://ftp.census.gov/geo/tiger/TIGER2012/PLACE/' + f, f)
  print("Unzipping " + f)
  sp.check_call('unzip ' + f, shell=True)
