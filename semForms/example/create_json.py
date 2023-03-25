import json
import sys

with open(sys.argv[1]) as f:
    lines = f.read()

    data = {}
    data['repo'] = sys.argv[1]
    data['source'] = lines
    data['indexName'] = 'expressions'
    with open(sys.argv[2], 'w') as out:
        json.dump(data, out)
    
