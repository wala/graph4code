import sys
from glob import glob
import zipfile
import os
import json

all_csvs = []
csv2zip = {}

for f in glob(sys.argv[2]):
    # zip file handler  
    zip = zipfile.ZipFile(f)
    # list available files in the container
    l = zip.namelist()
    l = [os.path.basename(x) for x in l if x.endswith('.csv')]
    for x in l:
        if x not in csv2zip:
            csv2zip[x] = []
        csv2zip[x].append(f)
        
    all_csvs.extend(l)

csv2scripts = {}

with open(sys.argv[1]) as f:
    lines = f.readlines()

    for line in lines:
        pat = 'read_csv('
        endpat = '.csv'
        try:
            start_idx = line.index(pat) + len(pat)
            end_idx = line.index(endpat) + len(endpat)
        
            csv = os.path.basename(line[start_idx:end_idx].replace('"', '').replace("'", ''))
            if csv in all_csvs:
                if csv not in csv2scripts:
                    csv2scripts[csv] = {}
                    csv2scripts[csv]['scripts'] = []
                    
                csv2scripts[csv]['scripts'].append(line.split(':')[0])
                csv2scripts[csv]['zip'] = csv2zip[csv]
                
        except:
           pass

csv2scripts = {k:v for k, v in csv2scripts.items() if len(v['scripts']) > 10 and len(v['zip']) == 1}

csv2scripts = sorted(csv2scripts.items(), key=lambda x:len(x[1]['scripts']), reverse=True)[:12]
csv2scripts = dict(csv2scripts)

new_results = {}
for x in csv2scripts:
   new_results[x] = csv2scripts[x]['scripts'][:5]

with open('csv2scripts.json', 'w') as f:
    json.dump(new_results, f)
    
print(len(csv2scripts))

for csv in csv2scripts:
    print(csv, len(csv2scripts[csv]['scripts']), csv2zip[csv])
