import time
import os
import argparse
from colorama import Fore
from hda import Client, Configuration


class ClientWekeo:
    """
    Running the session with given inputs/parameters
    """

    def __init__(self, username=None, password=None, query=None, out=None):
        conf = Configuration(user = username, password = password)
        self.hda_client = Client(config=conf) # hda client for fetching data
        self.username = username  # username
        self.password = password # password
        self.query = eval(query)  # query dict
        self.destfolder = out  # the destination folder to download
        self.query_result = None # object representing the query result

        self.download_time_start = None # start time of download
        self.download_timeout = 60*60*5  # the timeout for downloading data
        self.list_times = [] # list of times for every download
        self.max_tries = 7  # the max tries we can do

    def search_download_items(self):
        tries = 0
        while tries < self.max_tries:
            try:
                query_result = self.hda_client.search(self.query)
                if query_result:
                    print(Fore.GREEN + 'Query results retrieved!')
                    print(Fore.GREEN + 'Number of items retrieved:', len(query_result.results))
                    print(query_result)
                    self.query_result = query_result
                    return True
                print(Fore.YELLOW + "Error in fetching search results. Retrying...")
            except Exception as error:
                    print(Fore.RED + "*** Error at downloading data! ***")
                    print(Fore.RED + "An error occurred:", type(error).__name__, '->', error)
            time.sleep(5)
            tries += 1
        return False
    
    def download_items(self):
        for index, item in enumerate(self.query_result.results):
            tries = 0
            while tries < self.max_tries:
                try:
                    print(Fore.CYAN + 'Starting download of item:', item['id'])
                    filename = item['properties']['location'].split('/')[-1]
                    self.query_result[index].download(download_dir=self.destfolder)
                    if os.stat(os.path.join(self.destfolder, filename)).st_size != 0:
                        print(Fore.GREEN + f'\n============= Download completed. =============')
                        break
                except Exception as error:
                        print(Fore.RED + "*** Error at downloading data! ***")
                        print(Fore.RED + "An error occurred:", type(error).__name__, '->', error)
                time.sleep(5)
                tries += 1

    def fetch_data(self):
        if not self.search_download_items():
            print(Fore.RED + "Failed at searching items by query!")
            return False
        if not self.download_items():
            print(Fore.RED + "Failed at downloading items!")
            return False



def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--username", required=True, type=str)
    parser.add_argument("--password", required=True, type=str)
    parser.add_argument("--query", required=True, type=str)
    parser.add_argument("--out", required=True, type=str)

    args = parser.parse_args()

    username: str = args.username
    password: str = args.password
    query: str = args.query
    out: str = os.path.join(os.getcwd() + os.sep, str(args.out))
    if not os.path.exists(out):
        os.makedirs(out)
    client = ClientWekeo(username, password, query, out)
    client.fetch_data()

if __name__ == '__main__':
    main()


