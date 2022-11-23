ARG DOCKER_TAG=latest

FROM  qgis/qgis:latest

LABEL maintainer="tao@c-s.ro" \
       description="EnMAP-BOX 3"

ENV QT_QPA_PLATFORM=offscreen

# build timeout in seconds, so no timeout by default
ARG BUILD_TIMEOUT=360000

# change current directory to userdir
RUN cd ~

# clone the ENMAP-Box distribution from official repository
RUN git clone https://github.com/EnMAP-Box/enmap-box.git enmap-box

# clone the ENMAP-Box qgispluginsupport from official repository
RUN git clone https://github.com/EnMAP-Box/qgispluginsupport.git qgispluginsupport

# move qgispluginsupport on enmap-box/enmapbox/
RUN mv qgispluginsupport enmap-box/enmapbox/

# build the EnMAP-BOX plugin
RUN cd enmap-box && \
    python3 -m pip install -r requirements.txt && \
    python3 -m pip install -r requirements_developer.txt && \
    python3 scripts/setup_repository.py && \
    python3 scripts/create_plugin.py && \
    mkdir -p ~/.local/share/QGIS/QGIS3/profiles/default/python/plugins && \
    cp -r deploy/enmapboxplugin ~/.local/share/QGIS/QGIS3/profiles/default/python/plugins && \
    qgis_process plugins enable enmapboxplugin

# clean the build workspace
RUN rm -rf ~/enmap-box
RUN rm -rf ~/qgispluginsupport

CMD ["qgis_process"]