FROM osgeo/gdal:ubuntu-full-3.2.1

LABEL maintainer="abecheru@c-s.ro" \
    description="Gdal, scikit and pandas for burned area "

RUN apt-get update
RUN apt install -y python3-pip
RUN pip3 install -U scikit-learn==0.24.1
RUN pip3 install pandas
RUN apt-get clean

WORKDIR /usr/share/burned_area
COPY ba.py .
RUN chmod 775 ba.py
COPY model_gnb_ba.pkl .

