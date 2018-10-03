FROM ubuntu:18.04

LABEL maintainer="tao@c-s.ro" \
       description="Prepare an EO processing environment with commonly used tools."

# The versions of stuff
ENV SNAP_VERSION=6.0

WORKDIR /opt/

RUN     apt update &&  apt upgrade -y && apt autoremove -y

RUN     apt install wget -y

RUN wget http://step.esa.int/downloads/6.0/installers/esa-snap_sentinel_unix_6_0.sh && \
     chmod +x esa-snap_sentinel_unix_6_0.sh && \
     ./esa-snap_sentinel_unix_6_0.sh -q && \
     ### update snap
     #snap --nosplash --nogui --modules --update-all
     rm -f esa-snap_sentinel_unix_6_0.sh

RUN apt -y install gdal-bin python-gdal python3-gdal

ENV LD_LIBRARY_PATH=/opt/snap/jre/lib/

RUN ln -s /opt/snap/bin/gpt /usr/local/bin/gpt
