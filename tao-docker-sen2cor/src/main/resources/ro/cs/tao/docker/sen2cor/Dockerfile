FROM ubuntu:18.04

LABEL maintainer="tao@c-s.ro" \
       description="Prepare a Sen2Cor 2.8 processing environment."

# Environment
ENV SEN2COR_VERSION	2.8
ENV DEBIAN_FRONTEND noninteractive

WORKDIR /opt/

RUN apt update &&  apt upgrade -y && apt autoremove -y

RUN apt install wget -y

# Install Sen2Cor 2.8
RUN wget http://step.esa.int/thirdparties/sen2cor/2.8.0/Sen2Cor-02.08.00-Linux64.run && \
     chmod +x Sen2Cor-02.08.00-Linux64.run && \
     ./Sen2Cor-02.08.00-Linux64.run --target /opt/sen2cor && \
     rm -f Sen2Cor-02.08.00-Linux64.run

RUN ln -s /opt/sen2cor/bin/L2A_Process /usr/local/bin/L2A_Process