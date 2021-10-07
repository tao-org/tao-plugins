FROM osgeo/gdal:ubuntu-full-3.2.0

LABEL maintainer="tao@c-s.ro" \
       description="Orfeo Toolbox 7.3.0"

# The versions of stuff
ENV OTB_VERSION=7.3.0

WORKDIR /opt/

RUN     apt-get update && apt-get upgrade -y && apt-get autoremove -y

RUN     apt-get -y install curl bzip2 file libx11-6 libxext6 libxau6 libxxf86vm1 libxdmcp6 libdrm2 git

RUN     apt-get -y install python3-pip
RUN     pip3 install numpy

RUN curl -L https://www.orfeo-toolbox.org/packages/archives/OTB/OTB-${OTB_VERSION}-Linux64.run -o OTB-${OTB_VERSION}-Linux64.run && \
     chmod +x OTB-${OTB_VERSION}-Linux64.run && \
     ./OTB-${OTB_VERSION}-Linux64.run --target /opt/OTB-${OTB_VERSION}-Linux64 && \
     rm -f OTB-${OTB_VERSION}-Linux64.run && \
     chmod a+rx /opt/OTB-${OTB_VERSION}-Linux64/otbenv.profile && \
     ln -s /opt/OTB-${OTB_VERSION}-Linux64/otbenv.profile /etc/profile.d/OTB.sh

RUN apt-get -y install gdal-bin python3-gdal

ENV PATH="/opt/OTB-${OTB_VERSION}-Linux64/bin:${PATH}"
ENV GDAL_DATA="/opt/OTB-${OTB_VERSION}-Linux64/share/data"
ENV PROJ_LIB="/opt/OTB-${OTB_VERSION}-Linux64/share/proj"
ENV LD_LIBRARY_PATH=/opt/OTB-${OTB_VERSION}-Linux64/lib/

