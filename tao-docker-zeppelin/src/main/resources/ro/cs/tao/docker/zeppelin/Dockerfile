FROM apache/zeppelin:0.12.0

ENV ZEPPELIN_ADDR=0.0.0.0 \
    ZEPPELIN_PORT=8080 \
    ZEPPELIN_NOTEBOOK_DIR=/opt/zeppelin/notebook \
    ZEPPELIN_LOG_DIR=/opt/zeppelin/logs

USER root
RUN apt-get update && \
    apt-get install -y python3 python3-pip && \
    pip3 install --upgrade pip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY shiro.ini /opt/zeppelin/conf/shiro.ini
COPY zeppelin-site.xml /opt/zeppelin/conf/zeppelin-site.xml
COPY requirements.txt .
RUN pip install -r requirements.txt
EXPOSE 8080

CMD ["/opt/zeppelin/bin/zeppelin.sh"]




