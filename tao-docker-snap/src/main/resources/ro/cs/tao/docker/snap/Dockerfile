FROM ubuntu:16.04

LABEL maintainer="tao@c-s.ro" \
       description="Prepare a SNAP 6.0 EO processing environment with commonly used tools."

# Environment
ENV SNAP_VERSION	6.0
ENV DEBIAN_FRONTEND noninteractive
ENV JAVA_HOME       /usr/lib/jvm/java-8-oracle
ENV LANG            en_US.UTF-8
ENV LC_ALL en_US.UTF-8

WORKDIR /opt/

RUN apt update &&  apt upgrade -y && apt autoremove -y

RUN apt install wget -y

# Install Oracle JDK 8
RUN apt-get update && \
  apt-get install -y --no-install-recommends locales && \
  locale-gen en_US.UTF-8 && \
  apt-get dist-upgrade -y && \
  apt-get --purge remove openjdk* && \
  echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true" | debconf-set-selections && \
  echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" > /etc/apt/sources.list.d/webupd8team-java-trusty.list && \
  apt-key adv --keyserver keyserver.ubuntu.com --recv-keys EEA14886 && \
  apt-get update && \
  apt-get install -y --no-install-recommends oracle-java8-installer oracle-java8-set-default && \
apt-get clean all

# Install SNAP 6.0
RUN wget http://step.esa.int/downloads/6.0/installers/esa-snap_sentinel_unix_6_0.sh && \
     chmod +x esa-snap_sentinel_unix_6_0.sh && \
     ./esa-snap_sentinel_unix_6_0.sh -q && \
     ### update snap
     #snap --nosplash --nogui --modules --update-all
     rm -f esa-snap_sentinel_unix_6_0.sh

# Install additional tools
RUN apt -y install gdal-bin python-gdal python3-gdal

ENV LD_LIBRARY_PATH=/usr/lib/jvm/java-8-oracle/lib/

# Change SNAP JRE to JDK 8
RUN sed -i "/jdkhome=/ s/=.*/=\"\/usr\/lib\/jvm\/java-8-oracle\"/" /opt/snap/etc/snap.conf

# Modify GPT arguments
RUN sed -i '$d' /opt/snap/bin/gpt.vmoptions && \
	sed -i '$a-server' /opt/snap/bin/gpt.vmoptions && \
	sed -i '$a-Xms4G' /opt/snap/bin/gpt.vmoptions && \
	sed -i '$a-Xmx12G' /opt/snap/bin/gpt.vmoptions

RUN ln -s /opt/snap/bin/gpt /usr/local/bin/gpt