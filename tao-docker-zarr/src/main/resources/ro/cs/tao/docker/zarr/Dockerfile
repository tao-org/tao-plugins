FROM osgeo/gdal:ubuntu-full-3.3.2

RUN apt-get update && apt-get install -y python3-pip && apt-get clean && rm -rf /var/lib/apt/lists/*

COPY gdal_to_xarray.py /usr/bin/gdal_to_xarray.py
COPY union_zarrs.py /usr/bin/union_zarrs.py

COPY requirements.txt requirements.txt
RUN python -m pip install -r requirements.txt
