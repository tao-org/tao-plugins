FROM ubuntu:18.04


RUN apt update && apt -y upgrade && apt autoremove -y
RUN apt -y install wget \
        curl \
        zip \
        vim \
        python \
        python-pip
#RUN pip install numpy \
#                scipy \
#                matplotlib \
#                pandas
RUN pip install lxml \
                matplotlib \
                numpy \
                scipy \
                scikit-image \
                numexpr

WORKDIR /opt
#ADD . /opt

#CMD ["/bin/sh","python","test1.py"]

