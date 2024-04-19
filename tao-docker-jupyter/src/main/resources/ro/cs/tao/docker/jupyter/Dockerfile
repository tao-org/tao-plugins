FROM jupyter/base-notebook

COPY pyKernel0requirements.txt .
RUN pip install -r pyKernel0requirements.txt
#RUN rm /home/jovyan/.jupyter/jupyter_notebook_config.py
COPY jupyter_notebook_config.py /home/jovyan/.jupyter/
COPY jupyterlite.key /home/jovyan/
COPY jupyterlite.pem /home/jovyan/
COPY custom.js /home/jovyan/./jupyter/custom/custom.js
USER root
RUN chown jovyan /home/jovyan/.jupyter/jupyter_notebook_config.py
RUN chown jovyan /home/jovyan/jupyterlite.key
RUN chown jovyan /home/jovyan/jupyterlite.pem
RUN chown jovyan /home/jovyan/./jupyter/custom/custom.js
RUN mkdir /home/jovyan/notebooks
RUN chmod 777 /home/jovyan/notebooks
USER jovyan

ENV JUPYTER_ENABLE_LAB=yes