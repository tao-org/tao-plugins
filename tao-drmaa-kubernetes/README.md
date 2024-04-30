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

[//]: # (## ??? Install Longhorn and add a storage class to work with S3 storage)

[//]: # ()
[//]: # (```bash)

[//]: # ($ kubectl apply -f https://raw.githubusercontent.com/longhorn/longhorn/v1.5.4/deploy/longhorn.yaml)

[//]: # (```)

[//]: # ()
[//]: # (```bash)

[//]: # ($ kubectl apply -f longhorn-sc.yaml)

[//]: # (```)

[//]: # ()
[//]: # (See https://longhorn.io/docs/1.5.4/volumes-and-nodes/create-volumes/)

[//]: # ()
[//]: # (Create storage class and persistence volume claim for the storage bucket. )

[//]: # ()
[//]: # (```bash)

[//]: # ($ kubectl apply -f tao-s3-sc-pvc.yaml -n argo)

[//]: # (```)

## Install AWS S3 CSI driver to work with S3 storage (https://github.com/awslabs/mountpoint-s3-csi-driver/blob/main/docs/install.md)

```bash
$ helm repo add aws-mountpoint-s3-csi-driver https://awslabs.github.io/mountpoint-s3-csi-driver
```

```bash
$ helm repo update
```

```bash
$ helm upgrade --install aws-mountpoint-s3-csi-driver --namespace kube-system aws-mountpoint-s3-csi-driver/aws-mountpoint-s3-csi-driver
```
Create PV and PVC for the storage bucket. 

```bash
$ kubectl apply -f tao-s3-pv-pvc.yaml -n argo
```

## Install CSI driver SMB

```bash
$ helm repo add csi-driver-smb https://raw.githubusercontent.com/kubernetes-csi/csi-driver-smb/master/charts
$ helm install csi-driver-smb csi-driver-smb/csi-driver-smb --namespace kube-system --version v1.14.0
```

## Create secret to store user and password for the share

```bash
$ kubectl create secret generic tao-smbcreds --from-literal username=USERNAME --from-literal password="PASSWORD" -n argo
```

## Create a storage class
