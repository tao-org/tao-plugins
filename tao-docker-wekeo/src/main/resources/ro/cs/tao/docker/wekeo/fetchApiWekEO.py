import time
import requests
import json
import base64
import os
from colorama import Fore
from tqdm import tqdm
import argparse


class Client:
    """
    Running the session with given inputs/parameters
    """

    def __init__(self, username=None, password=None, query=None, out=None):

        self.username = username  # credentials
        self.password = password
        self.query = query  # metadata / dict
        self.destfolder = out  # the destination folder to download

        self.token = None  # token access
        self.accept_cond = False  # flag for accepting terms and conditions
        self.request_post_response = None  # response for posting request with query
        self.request_data_status = False  # flag for request data

        self.get_content_result = False  # flag for content result
        self.content_list = []  # list with urls, size for order
        self.content_result = None  # content from get request
        self.order_response_list = []  # list with orders [(order, flag, size)] || flag = 0|1 || 1 means completed

        self.max_tries = 7  # the max tries we can do
        self.order_time_start = None  # set for timer
        self.order_timeout = 60*60*5  # the timeout for ordering data

    def encode_credentials(self):
        """
        Encode credentials on base64.
        """
        b = base64.b64encode(bytes(self.username + ':' + self.password, 'utf-8'))  # bytes
        base64_str = b.decode('utf-8')  # convert bytes to string
        return base64_str

    def get_token(self):
        """
        Get the current token for using at the header of the requests.
        """
        try:
            author_response = requests.get('https://wekeo-broker.prod.wekeo2.eu/databroker/gettoken',
                                           headers={'Authorization': 'Basic ' + self.encode_credentials()})
            self.token = author_response.json()['access_token']
            print(Fore.CYAN + "Received a new access token.")
        except Exception as error:
            self.token = None
            print(Fore.RED + "An error occurred:", type(error).__name__, '->', error)

    def verify_response_status(self, response_status):
        """
        Verify the response status in case we can solve it
        """
        print(Fore.LIGHTMAGENTA_EX + "The type of error response is:", response_status)
        if not response_status:
            return
        if response_status == 403:  # the token expired
            self.get_token()
        return

    def accept_conditions(self):
        """
        Accepting terms and conditions - this method is called just once.
        Try it for several times in case it fails.
        """
        terms_c_response = None
        tries = 0
        while tries < self.max_tries:
            try:
                terms_c_response = requests.put(
                    'https://wekeo-broker.prod.wekeo2.eu/databroker/termsaccepted/Copernicus_General_License',
                    data='accepted=true', headers={'Accept': 'application/json', 'Authorization': self.token})
                res = requests.get(
                    'https://wekeo-broker.prod.wekeo2.eu/databroker/termsaccepted/Copernicus_General_License',
                    headers={'Accept': 'application/json', 'Authorization': self.token})
                if res.json()['accepted']:
                    print(Fore.GREEN + "Terms and conditions accepted!")
                    self.accept_cond = True
                    return True
            except Exception as error:
                self.verify_response_status(terms_c_response.status_code)
                print(Fore.RED + "An error occurred:", type(error).__name__, '->', error)
                time.sleep(5)
            tries += 1
        return False

    # request data with the specific query using post
    def request_data(self):
        """
        Request data with the specific query using post.
        Try it for several times in case it fails.
        """
        print(Fore.GREEN + "Requesting data...")
        tries = 0
        post_response = None

        while tries < self.max_tries:
            try:
                post_response = requests.post('https://wekeo-broker.prod.wekeo2.eu/databroker/datarequest',
                                              headers={'Authorization': self.token, 'Content-Type': 'application/json',
                                                       'Accept': 'application/json'}, data=self.query)
                if 'status' in post_response.json() and post_response.json()['status'] == 'started':
                    print(Fore.GREEN + "Requested data status =", post_response.json()['status'])
                    self.request_post_response = post_response
                    return True
            except Exception as error:
                self.verify_response_status(post_response.status_code)
                print(Fore.RED + "An error occurred:", type(error).__name__, '->', error)
            tries += 1
            time.sleep(5)
        return False

    # poll to see the status of our request
    def poll_request_data(self, post_response):
        """
        Get the poll to see the status of our requested data.
        """
        print(Fore.GREEN + "Polling for request data...")
        tries = 0
        poll_response = None

        while tries < self.max_tries:
            try:
                poll_response = requests.get(
                    'https://wekeo-broker.prod.wekeo2.eu/databroker/datarequest/status/' + post_response.json()[
                        'jobId'], headers={'Authorization': self.token})
                print(Fore.GREEN + "Poll status of requested data =", poll_response.json()['status'])
                break
            except Exception as error:
                self.verify_response_status(poll_response.status_code)
                print(Fore.RED + "An error occurred:", type(error).__name__, '->', error)
            tries += 1
            time.sleep(10)

        if not poll_response:
            print(Fore.RED + "Error at receiving poll from request data!")
            return False

        status = poll_response.json()['status']
        sleep = 1

        while status != 'completed':  # while the status is running
            if status == 'failed':
                print(Fore.RED + "Error at receiving poll status for requested data.")
                return False
            try:
                poll_response = requests.get(
                    'https://wekeo-broker.prod.wekeo2.eu/databroker/datarequest/status/' + post_response.json()[
                        'jobId'], headers={'Authorization': self.token})
                status = poll_response.json()['status']
                print(Fore.GREEN + "Poll status of requested data =", poll_response.json()['status'])
            except Exception as error:
                self.verify_response_status(poll_response.status_code)
                print(Fore.RED + "An error occurred:", type(error).__name__, '->', error)
            sleep *= 1.1
            time.sleep(sleep)

        if status == 'completed':  # the request of our data has been completed
            self.request_data_status = True
            return True
        return False

    def get_content(self):
        """
        Retrieve the list of results calling the GET method.
        """
        tries = 0
        result_response = None

        while tries < self.max_tries:
            try:
                result_response = requests.get(
                    'https://wekeo-broker.prod.wekeo2.eu/databroker/datarequest/jobs/' +
                    self.request_post_response.json()['jobId'] + '/result', headers={'Authorization': self.token})
                if 'pages' in result_response.json():  # this means we got a valid response
                    self.content_result = result_response
                    break
            except Exception as error:
                self.verify_response_status(result_response.status_code)
                print(Fore.RED + "An error occurred:", type(error).__name__, '->', error)
            tries += 1
            time.sleep(5)

        if not self.content_result:
            return False

        pages = self.content_result.json()['pages']
        items_in_page = self.content_result.json()['itemsInPage']

        for i in range(pages):  # iterate through all the pages to get the content of each one
            tries = 0
            c = None
            while tries < self.max_tries:
                try:
                    result_response = requests.get('https://wekeo-broker.prod.wekeo2.eu/databroker/datarequest/jobs/'+self.request_post_response.json()['jobId']+'/result?'+'page='+str(i)+'&size='+str(items_in_page), headers={'Authorization': self.token})
                    if 'content' in result_response.json():
                        c = result_response
                        break
                except Exception as error:
                    self.verify_response_status(result_response.status_code)
                    print(Fore.RED + "An error occurred:", type(error).__name__, '->', error)
                tries += 1
            if not c:
                print(Fore.RED + "Error at receiving url content!")
                self.get_content_result = False
                return False
            for j, element in enumerate(c.json()['content']):  # get the url for ordering data
                self.content_list.append((element['url'], element['size']))  # and the size of expected download

        self.get_content_result = True  # we got a content result | mark it
        return True

    def order_data(self):
        """
        Order data for our query and to be ready for download.
        """
        print(Fore.BLUE + "Ordering data...")
        order_response, poll_response = None, None
        self.start_timer()  # start the timer for ordering data

        for url, size in self.content_list:
            # we request an order for every URL we have
            data_json = json.dumps({"jobId": self.request_post_response.json()[
                'jobId'], 'uri': url})
            tries = 0
            while tries < self.max_tries:
                # posting data
                try:
                    order_response = requests.post('https://wekeo-broker.prod.wekeo2.eu/databroker/dataorder', data=data_json, headers={'Authorization': self.token, 'Content-Type': 'application/json', 'Accept': 'application/json'})
                    print(Fore.GREEN + "Poll status of ordered data = ", order_response.json()['status'])
                    self.order_response_list.append([order_response, 0, size])  # (order_item, un/finished, size)
                    break
                except Exception as error:
                    self.verify_response_status(order_response.status_code)
                    print(Fore.RED + "An error occurred:", type(error).__name__, '->', error)
                tries += 1
                time.sleep(10)
        count = 0

        while count != len(self.order_response_list):
            print(Fore.BLUE + f'Time left for ordering: {self.order_time_start + self.order_timeout - time.time()}')
            if self.check_timeout(): return False  # check for timeout
            count = 0
            for element in self.order_response_list:
                try:
                    if not element[1]:
                        poll_response = requests.get(
                            'https://wekeo-broker.prod.wekeo2.eu/databroker/dataorder/status/' +
                            element[0].json()[
                                'orderId'], headers={'Authorization': self.token})
                        print(Fore.GREEN + "Poll status of ordered data ready for download = ", poll_response.json()['status'])

                        # if the get request returns failed status it remains that way | no need to send more requests
                        if poll_response.json()['status'] == 'completed':
                            element[1] = 1
                            self.download_data(poll_response, element[0], element[2])
                        elif poll_response.json()['status'] == 'failed':
                            element[1] = 1
                except Exception as error:
                    self.verify_response_status(poll_response.status_code)
                    print(Fore.RED + "An error occurred:", type(error).__name__, '->', error)
                count += element[1]
            time.sleep(10)
        return True

    def download_data(self, poll_response, order_response, size):
        """
        Download data using chunks and showing the loading bar.
        """
        print(Fore.BLUE + "Downloading data...")
        download_response = None

        if self.check_timeout(): return  # check for timeout
        tries = 0
        while tries < self.max_tries:
            try:
                t = poll_response.json()['url']
                download_response = requests.get(
                    'https://wekeo-broker.prod.wekeo2.eu/databroker/dataorder/download/' +
                    order_response.json()[
                        'orderId'], headers={'Accept': 'application/json', 'Authorization': self.token}, stream=True, timeout=60)
                filename = t.split('/')[-1]
                f = open(os.path.join(self.destfolder, filename), 'wb')
                pbar = tqdm(total=int(size), desc='Loading', ascii=False, ncols=120, colour="cyan")  # progress bar | size = expected total size for download on item
                for chunk in download_response.iter_content(chunk_size=8192):
                    if chunk:
                        f.write(chunk)
                        pbar.update(len(chunk))
                print(Fore.GREEN + f'\n============= Download completed. =============')
                break
            except Exception as error:
                self.verify_response_status(download_response.status_code)
                print(Fore.RED + "*** Error at downloading data! ***")
                print(Fore.RED + "An error occurred:", type(error).__name__, '->', error)
            time.sleep(10)
            tries += 1

    # start timer
    def start_timer(self):
        self.order_time_start = time.time()

    # check for timeout in case it passes a limit
    def check_timeout(self):
        if time.time()-self.order_time_start > self.order_timeout:
            return True
        return False

    def run_fetch(self):
        """
        Runs the whole process for fetching the wekEO api.
        """
        self.get_token()

        self.accept_conditions()

        if not self.accept_cond:
            print(Fore.RED + "Failed at terms and conditions!")
            return False

        self.request_data()

        if not self.request_post_response:
            print(Fore.RED + "Failed at posting the query!")
            return False

        self.poll_request_data(self.request_post_response)

        if not self.request_data_status:
            print(Fore.RED + "Failed at requesting data!")
            return False

        self.get_content()

        if not self.get_content_result:
            print(Fore.RED + "Failed at getting the content for ordering data!")
            return False

        if not self.order_data():
            print(Fore.RED + "Failed at ordering and downloading data!")
            return False

        print(Fore.GREEN + "\n============= Process completed successfully! =============")
        return True


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
    out: str = os.path.join(os.getcwd(), str(args.out))
    client = Client(username, password, query, out)
    client.run_fetch()


if __name__ == '__main__':
    main()


