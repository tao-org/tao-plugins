FROM ubuntu:20.04

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    software-properties-common=* && \
    add-apt-repository ppa:deadsnakes/ppa && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
    openjdk-8-jre-headless=* \
    wget=* && \
    rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64/
RUN export JAVA_HOME

WORKDIR /opt/
COPY esa-snap_sentinel_unix_8_0.sh .
RUN chmod +x esa-snap_sentinel_unix_8_0.sh && \
    ./esa-snap_sentinel_unix_8_0.sh -q && \
    rm -f esa-snap_sentinel_unix_8_0.sh


# Install Sen ET
COPY sen-et-conda-Linux64.run .
RUN chmod +x sen-et-conda-Linux64.run && \
	./sen-et-conda-Linux64.run && \
	rm -f sen-et-conda-Linux64.run

ENV PATH="$PATH:/opt/sen-et-conda-Linux64/bin"
ENV LC_ALL C.UTF-8
ENV LANG C.UTF-8

# Install snappy and dependencies
RUN bash /opt/snap/bin/snappy-conf /opt/sen-et-conda-Linux64/bin/python3.6 /opt/sen-et-conda-Linux64/lib/python3.6/site-packages && \
	apt update && \
	apt install -y gcc && \
	apt install -y g++ && \
	python3.6 -m pip uninstall numba -y && \
	python3.6 -m pip install numba

# Update SNAP
COPY update_snap.sh .
RUN chmod +x ./update_snap.sh
RUN ./update_snap.sh

# Copy externally modified scripts
RUN mkdir /opt/senet && mkdir /opt/senet/s2_pre_processing_scripts && mkdir /opt/senet/s3_pre_processing_scripts
COPY *.xml /opt/senet/
COPY *.py /opt/senet/
COPY *.sh /opt/senet/
COPY *.csv /opt/senet/
COPY .cdsapirc /root/
COPY s2_pre_processing_scripts/*.* /opt/senet/s2_pre_processing_scripts/
COPY s3_pre_processing_scripts/*.* /opt/senet/s3_pre_processing_scripts/
RUN mkdir /tmp_wkd
ENV PATH="$PATH:/opt/senet"