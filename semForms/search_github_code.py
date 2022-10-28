
# from: https://github.com/backtrackbaba/github-search-api


# Import required modules
import requests
import time
import csv
import time, os, json
from flask import request, jsonify
import argparse as ap
from flask import Flask, request

# Paste your Access token here
# To create an access token - https://github.com/settings/tokens
token = os.environ['GH_TOKEN']

# code_search_url	"https://api.github.com/s…age,per_page,sort,order}"  https://api.github.com/search/code?q=
# repository_url	"https://api.github.com/repos/{owner}/{repo}"
# repository_search_url	"https://api.github.com/search/repositories?q={query}{&page,per_page,sort,order}"
# Base API Endpoint
base_api_url = 'https://api.github.com/'

NO_MATCHES = 1
ERROR = 2
SUCCESS=3


class FlaskApp(Flask):

    def __init__(self, *args, **kwargs):
        super(FlaskApp, self).__init__(*args, **kwargs)
        parser = ap.ArgumentParser(description='Github API')
        parser.add_argument("--GH_TOKEN", type=str, default="access_token=" + os.environ['GH_TOKEN'])
        parser.add_argument("--base_api_url", type=str, default='https://api.github.com/') #TODO
        args = parser.parse_args()
        base_api_url = args.base_api_url
        token = args.GH_TOKEN

app = FlaskApp(__name__)

@app.route('/api/github_search')
def search():
    query = request.args['query']
    # A CSV file containting the data would be saved with the name as the query
    # Ex: machine+learning.csv
    # filename = query  # + '.csv'

    # Create a CSV file or clear the existing one with the same name
    # with open(filename, 'w', newline='') as csvfile:
    #     write_to_csv = csv.writer(csvfile, delimiter='|')
    result = []
    # GitHub returns information of only 30 repositories with every request
    # The Search API Endpoint only allows upto 1000 results, hence the range has been set to 35
    for page in range(1, 2):

        # Building the Search API URL
        # search_final_url = base_api_url + 'search/repositories?q=' + \
        #     query + '&page=' + str(page) #+ '&' + token
        # search?l=Python&q=dataset.csv&type=Code
        search_final_url = base_api_url + 'search/code?q=' + \
                           query + '+language=python+page=1'
        headers = {
            'Accept': 'application/vnd.github+json',
            "Authorization": f"Bearer {token}"
        }

        # try-except block just incase you set up the range in the above for loop beyond 35
        try:
            response = requests.get(search_final_url, headers=headers).json()
        except:
            print("Issue with GitHub API, Check your token")
            return [], '', ERROR

        # TODO: add condition for exceeding rate limit.
        if 'items' not in response:
            return [], response['message'], ERROR
        if response['total_count'] == 0:
            print('Did not find any matches!!')
            return [], '', NO_MATCHES
        # Parsing through the response of the search query
        for item in response['items']:
            '''
            10 = {dict: 8} {'name': 'anagrafica.csv', 'path': 'output/openDataComunePalermo/processing/report/anagrafica.csv', 'sha': '669a0f4005e8ba976309fe72fa0410bf4c90fb43', 'url': 'https://api.github.com/repositories/356919738/contents/output/openDataComunePalermo/processing/re
                 'name' = {str} 'anagrafica.csv'
                 'path' = {str} 'output/openDataComunePalermo/processing/report/anagrafica.csv'
                 'sha' = {str} '669a0f4005e8ba976309fe72fa0410bf4c90fb43'
                 'url' = {str} 'https://api.github.com/repositories/356919738/contents/output/openDataComunePalermo/processing/report/anagrafica.csv?ref=28575070ab2672f0d9652d2da0268febcc9010c3'
                 'git_url' = {str} 'https://api.github.com/repositories/356919738/git/blobs/669a0f4005e8ba976309fe72fa0410bf4c90fb43'
                 'html_url' = {str} 'https://github.com/aborruso/checkCatalogue/blob/28575070ab2672f0d9652d2da0268febcc9010c3/output/openDataComunePalermo/processing/report/anagrafica.csv'
                 'repository' = {dict: 46} {'id': 356919738, 'node_id': 'MDEwOlJlcG9zaXRvcnkzNTY5MTk3Mzg=', 'name': 'checkCatalogue', 'full_name': 'aborruso/checkCatalogue', 'private': False, 'owner': {'login': 'aborruso', 'id': 30607, 'node_id': 'MDQ6VXNlcjMwNjA3', 'avatar_url': 'https://avatars.g
                 'score' = {float} 1.0
                 __len__ = {int} 8
            11 = {dict: 8} {'name': 'get-cms-nursing.py', 'path': 'get-cms-nursing.py', 'sha': '821105615b4023a38f877d60a68b211e142a935a', 'url': 'https://api.github.com/repositories/314612702/contents/get-cms-nursing.py?ref=71ca7112fdff22791af100e74e6348a4f942ad93', 'git_url': 'htt
                 'name' = {str} 'get-cms-nursing.py'
                 'path' = {str} 'get-cms-nursing.py'
                 'sha' = {str} '821105615b4023a38f877d60a68b211e142a935a'
                 'url' = {str} 'https://api.github.com/repositories/314612702/contents/get-cms-nursing.py?ref=71ca7112fdff22791af100e74e6348a4f942ad93'
                 'git_url' = {str} 'https://api.github.com/repositories/314612702/git/blobs/821105615b4023a38f877d60a68b211e142a935a'
                 'html_url' = {str} 'https://github.com/jalbertbowden/covid19-cms-utilities/blob/71ca7112fdff22791af100e74e6348a4f942ad93/get-cms-nursing.py'
                 'repository' = {dict: 46} {'id': 314612702, 'node_id': 'MDEwOlJlcG9zaXRvcnkzMTQ2MTI3MDI=', 'name': 'covid19-cms-utilities', 'full_name': 'jalbertbowden/covid19-cms-utilities', 'private': False, 'owner': {'login': 'jalbertbowden', 'id': 46432, 'node_id': 'MDQ6VXNlcjQ2NDMy', 'avatar_
                 'score' = {float} 1.0
                 __len__ = {int} 8
            '''
            # Append to the CSV file
            # with open(filename, 'a', newline='') as csvfile:
            #     write_to_csv = csv.writer(csvfile, delimiter='|')

            code_filename = item['name']
            code_url = item['html_url']
            match_score = item['score']
            repo_name = item['repository']['name']
            repo_description = item['repository']['description']
            # repo_main_language = item['repository']['language']

            repo_license = None
            # repo_score is the relevancy score of a repository to the search query
            # Reference - https://developer.github.com/v3/search/#ranking-search-results
            repo_score = item['score']

            # Many Repositories don't have a license, this is to filter them out
            if 'license' in item['repository']:
                repo_license = item['repository']['license']['name']
            else:
                repo_license = "NO LICENSE"

            # Just incase, you face any issue with GitHub API Rate Limiting, use the sleep function as a workaround
            # Reference - https://developer.github.com/v3/search/#rate-limit

            # time.sleep(10)

            print(f'File name: {code_filename}\n'
                  f'\tURL: {code_url}\n'
                  f'\tRepo Name = {repo_name}\n'
                  f'\tDescription: {repo_description}\n'
                  # f'\tPrimary Language = {repo_main_language}\n'
                  f'\tLicense = {repo_license}\n'
                  f'Score', {repo_score})
            res_dict = {
                'File name': code_filename,
                'URL': code_url,
                'Repo Name': repo_name,
                'Description': repo_description,
                'License': repo_license,
                'Score': repo_score,
            }
            result.append(res_dict)
            # write_to_csv.writerow([code_filename, code_url, repo_name, repo_description, repo_license,
            #                        repo_score])

            print('==========')
    return jsonify(result)


@app.route('/', methods=['GET'])
@app.route("/home", methods=['GET'])
def home():
    return '''<h1>NSQA-AskEPM API</h1>'''

############ UI Endpoints ############


if __name__ == '__main__':
    app.run(debug=True, port=8001, host="0.0.0.0")


