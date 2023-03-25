import requests
from bs4 import BeautifulSoup
import json
import sys


def fetch(url, output_loc):    
    Headers = {'Accept': 'application/vnd.github.v4.raw'}
    url = url.replace('/blob/', '/raw/')
    r = requests.get(url=url, headers=Headers)
    with open(output_loc, 'wb') as f:
        f.write(r.content)
        
def create_request_for_analysis(original_source_url, source_on_fs, req_file_for_posting):
    with open(source_on_fs) as f:
        lines = f.read()
        data = {}
        data['repo'] = original_source_url
        data['source'] = lines
        data['indexName'] = 'expressions'
        with open(req_file_for_posting, 'w') as out:
            json.dump(data, out)

def main():
    # fetch(sys.argv[1], sys.argv[2])
    # call nbconvert on the output of the fetch from the command line
    # original_source_url, source_on_fs, req_file_for_posting
    create_request_for_analysis(sys.argv[1], sys.argv[2], sys.argv[3])

if __name__ == "__main__":
    main()
