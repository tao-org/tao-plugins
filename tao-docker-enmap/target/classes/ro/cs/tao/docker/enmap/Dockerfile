FROM  qgis/qgis:release-3_28

ARG DOCKER_TAG=latest

LABEL maintainer="tao@c-s.ro" \
       description="EnMAP-BOX 3"

ENV QT_QPA_PLATFORM=offscreen

# build timeout in seconds, so no timeout by default
ARG BUILD_TIMEOUT=360000

# copy the ENMAP-Box distribution cloned from official repository
COPY enmap-box.zip .

# unzip the ENMAP-Box distribution
RUN unzip enmap-box.zip

# backup libQt5Core.so.5 before applying the special patch for old Linux
RUN cp /lib/x86_64-linux-gnu/libQt5Core.so.5 /lib/x86_64-linux-gnu/libQt5Core.so.5.bak

# apply the special 'patch' for fix 'ImportError: libQt5Core.so.5: cannot open shared object file: No such file or directory'
RUN strip --remove-section=.note.ABI-tag /lib/x86_64-linux-gnu/libQt5Core.so.5

WORKDIR /enmap-box

# build the EnMAP-BOX plugin
RUN    python3 -m pip install -r requirements_developer.txt
RUN    python3 scripts/setup_repository.py
RUN    python3 scripts/create_plugin.py
RUN    mkdir -p ~/.local/share/QGIS/QGIS3/profiles/default/python/plugins
RUN    cp -r deploy/enmapboxplugin ~/.local/share/QGIS/QGIS3/profiles/default/python/plugins
RUN    qgis_process plugins enable enmapboxplugin

# clean the build workspace
RUN rm -f enmap-box.zip
RUN rm -rf enmap-box
RUN rm -rf qgispluginsupport

CMD ["qgis_process"]