# Kubernetes cluster configurations 

## Namespace creation

Create the namespace used by the application for pods:

```bash
$ kubectl create ns argo 
```

## Service account creation

Create the service account used to access the cluster:

```bash
$ kubectl create serviceaccount tao-sa
$ kubectl apply -f tao-kubernetes-secret.yml
$ kubectl get secret/tao-sa-secret -o yaml 
```

Get the token and certificate from the last command and update database configuration keys: "tao.kubernetes.token", "tao.kubernetes.ca.cert.file" (values are base64 encoded).

## PV and PVC creation

Create persistent volumes and persistent volume claims used to access shared data. PVCs are used in pod volume mapping.
The file "tao-pv-pvc.yml" is valid for Rancher's Kubernetes and cluster node local volumes. 
For more info see: https://kubernetes.io/docs/concepts/storage/storage-classes/

```bash
$ kubectl apply -f tao-pv-pvc.yml -n argo
```
