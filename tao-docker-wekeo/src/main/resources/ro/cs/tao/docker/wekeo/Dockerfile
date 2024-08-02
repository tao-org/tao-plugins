FROM python:alpine3.18

COPY --chmod=0755 ./ /app

WORKDIR /app

RUN apk add --no-cache python3 py3-pip bash
RUN ln -sf python3 /usr/bin/python
RUN pip install -r requirements.txt











