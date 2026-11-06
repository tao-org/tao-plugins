FROM ubuntu:24.04

LABEL maintainer="sen4cap@c-s.ro" \
       description="Prepare a SNAP 12.0 EO processing environment with commonly used tools"

# Environment
ENV SNAP_VERSION        12.0
ENV DEBIAN_FRONTEND noninteractive
ENV LANG            en_US.UTF-8
ENV LC_ALL en_US.UTF-8

WORKDIR /opt/

RUN apt-get update && \
        apt-get upgrade -y && \
        apt-get autoremove -y && \
        apt-get install wget -y && \
        apt-get install unzip -y && \
        apt-get install xvfb -y && \
        apt-get install x11vnc -y

RUN     mkdir ~/.vnc
RUN     x11vnc -storepasswd 1234 ~/.vnc/passwd

RUN wget https://download.esa.int/step/snap/12.0/installers/esa-snap_all_linux-12.0.0.sh

# Install SNAP 12.0
#COPY esa-snap_all_linux-12.0.0.sh .
RUN chmod +x esa-snap_all_linux-12.0.0.sh && \
    ./esa-snap_all_linux-12.0.0.sh -q && \
    rm -f esa-snap_all_linux-12.0.0.sh
#RUN snap --nosplash --nogui --modules --disable org.esa.snap.snap.product.library.v2

# Install additional tools
#RUN apt -y install gdal-bin python-gdal python3-gdal

RUN apt-get -y install libgfortran5 dos2unix
RUN ln -s /opt/esa-snap/bin/gpt /usr/local/bin/gpt
RUN mkdir /home/.snap && \
    chmod 777 /home/.snap

# Snap file modifications:
# - Modify GPT arguments
RUN echo 'snap.userdir=/home/.snap' >> /opt/esa-snap/etc/snap.properties
RUN sed -i '/^-Xmx/s/^/#/' /opt/esa-snap/bin/gpt.vmoptions && \
        sed -i '$a-server' /opt/esa-snap/bin/gpt.vmoptions && \
        sed -i '$a-Xms4G' /opt/esa-snap/bin/gpt.vmoptions && \
        sed -i '$a-Xmx16G' /opt/esa-snap/bin/gpt.vmoptions
# install extra cluster
# RUN unzip extra-cluster.zip -d /opt/esa-snap
# RUN sed -i '$a-Dsnap.extraClusters=/opt/esa-snap/extra' /opt/esa-snap/bin/gpt.vmoptions

RUN mkdir /opt/graphs
COPY graphs/*.* /opt/graphs
RUN chmod -R 777 /opt/graphs

ENV DISPLAY :99
COPY update_snap.sh .
RUN chmod +x ./update_snap.sh
RUN dos2unix ./update_snap.sh
RUN ./update_snap.sh


# RUN  /opt/esa-snap/bin/snap --nosplash --nogui --modules --refresh

#
# RUN mkdir -p ".snap/auxdata/dem/ASTER 1sec GDEM v3"
# RUN mkdir -p ".snap/auxdata/dem/SRTM 1Sec HGT"
