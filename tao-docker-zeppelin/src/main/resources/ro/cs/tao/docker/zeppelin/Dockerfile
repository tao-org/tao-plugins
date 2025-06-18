FROM apache/zeppelin:0.12.0

ENV ZEPPELIN_ADDR=0.0.0.0 \
    ZEPPELIN_PORT=8080 \
    ZEPPELIN_NOTEBOOK_DIR=/zeppelin/notebooks \
    ZEPPELIN_LOG_DIR=/zeppelin/logs

USER root
RUN apt-get update && \
    apt-get install -y python3 python3-pip && \
    pip3 install --upgrade pip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*


COPY requirements.txt .
RUN pip install -r requirements.txt
COPY shiro.ini /zeppelin/conf/shiro.ini
COPY zeppelin-site.xml /zeppelin/conf/zeppelin-site.xml
EXPOSE 8080
USER zeppelin





