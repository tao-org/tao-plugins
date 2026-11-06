# Kubernetes cluster configurations 

## Namespace creation

Create the namespace used by the application for pods:

```bash
$ kubectl create ns argo 
```

## Service account creation

Create the service account used to access the cluster:

```bash
$ kubectl create serviceaccount tao-sa -n argo
$ kubectl apply -f tao-kubernetes-secret.yml -n argo
$ kubectl get secret/tao-sa-secret -o yaml -n argo
```

Get the token and certificate from the last command and update database configuration keys: "tao.kubernetes.token", "tao.kubernetes.ca.cert.file" (values are base64 encoded).

## PV and PVC creation

Create persistent volumes and persistent volume claims used to access shared data. PVCs are used in pod volume mapping.
The file "tao-pv-pvc.yml" is valid for Rancher's Kubernetes and cluster node local volumes. 
For more info see: https://kubernetes.io/docs/concepts/storage/storage-classes/

```bash
$ kubectl apply -f tao-pv-pvc.yml -n argo
```

## Install CSI driver SMB

```bash
$ helm repo add csi-driver-smb https://raw.githubusercontent.com/kubernetes-csi/csi-driver-smb/master/charts
$ helm repo update
$ helm install csi-driver-smb csi-driver-smb/csi-driver-smb --namespace kube-system --version v1.14.0
```

## Create secret to store user and password for the share

```bash
$ kubectl create secret generic tao-smbcreds --from-literal username=USERNAME --from-literal password="PASSWORD" -n argo
```

## Create a storage class
```bash
$ kubectl apply -f tao-smb-sc.yaml -n argo
```

## PV and PVC creation

Create persistent volumes and persistent volume claims used to access shared data from "tao-staging-master". PVCs are used in pod volume mapping.

```bash
$ kubectl apply -f tao-smb-pv-pvc.yaml -n argo
```

SELINUX can block file access through SAMBA, set it to "Permissive" (setenforce 0) if needed!

Create persistent volumes and persistent volume claims used to access shared data from local S3 EODATA. PVCs are used in pod volume mapping.

```bash
$ kubectl apply -f tao-eodata-pv-pvc.yaml -n argo
```

## Fix permissions for service account if needed

```bash
$ kubectl apply -f tao-role.yaml -n argo
$ kubectl apply -f tao-rolebinding.yaml -n argo
$ kubectl apply -f tao-cluster-role.yaml -n argo
$ kubectl apply -f tao-cluster-rolebinding.yaml -n argo
```