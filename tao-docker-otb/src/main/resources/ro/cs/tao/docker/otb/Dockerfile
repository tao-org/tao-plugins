FROM ubuntu:18.04

LABEL maintainer="tao@c-s.ro" \
       description="Prepare an EO processing environment with commonly used tools."

# The versions of stuff
ENV OTB_VERSION=6.4.0

WORKDIR /opt/

RUN     apt update &&  apt upgrade -y && apt autoremove -y

RUN     apt -y install curl bzip2 file libx11-6 libxext6 libxau6 libxxf86vm1 libxdmcp6 libdrm2 git

RUN     apt -y install python-pip
RUN     pip install lxml matplotlib numpy scipy scikit-image numexpr

RUN curl -L https://www.orfeo-toolbox.org/packages/archives/OTB/OTB-${OTB_VERSION}-Linux64.run -o OTB-${OTB_VERSION}-Linux64.run && \
     chmod +x OTB-${OTB_VERSION}-Linux64.run && \
     ./OTB-${OTB_VERSION}-Linux64.run --target /opt/OTB-${OTB_VERSION}-Linux64 && \
     rm -f OTB-${OTB_VERSION}-Linux64.run && \
     chmod a+rx /opt/OTB-${OTB_VERSION}-Linux64/otbenv.profile && \
     ln -s /opt/OTB-${OTB_VERSION}-Linux64/otbenv.profile /etc/profile.d/OTB.sh

RUN apt -y install gdal-bin python-gdal python3-gdal

ENV LD_LIBRARY_PATH=/opt/OTB-${OTB_VERSION}-Linux64/lib/

