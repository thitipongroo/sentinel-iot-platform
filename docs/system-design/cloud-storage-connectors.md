# Cloud Storage Connectors & Kubernetes Deployment Guide

This guide details the infrastructure, IAM authentication, and storage architecture for deploying the Sentinel IoT Platform with Lakehouse Parquet Cold Storage (RFC-001 Phase 3) and Realtime AI / Predictive Anomaly Detection (RFC-001 Phase 4) on Kubernetes.

---

## 1. Storage Architecture Overview

The Sentinel IoT Platform operates on a hybrid hot/warm/cold storage model:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Hot Layer (< 30 days)                           │
│  PostgreSQL 16 (TimescaleDB / Declarative Range Partitions)            │
│  Redis 7 Cluster (Telemetry Cache + In-Flight Dynamic Baselines)       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                         Daily Archival (02:30 UTC)
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                        Cold Layer (> 30 days)                          │
│  Apache Parquet (Snappy Compressed, Hive-Partitioned columnar data)    │
│  Path: /data/archive/org={orgId}/year={YYYY}/month={MM}/telemetry.parquet
│  DuckDB Embedded Vectorized OLAP Query Engine                          │
│  Cloud Object Storage (Amazon S3 / Google Cloud Storage / Azure Blob)  │
└────────────────────────────────────────────────────────────────────────┘
```

When multiple backend replicas run in Kubernetes, DuckDB reads Parquet files from a shared filesystem or object store mount. To support multi-pod access, the persistent volume must support **ReadWriteMany (RWX)**.

---

## 2. Kubernetes Storage Configuration

### Option A: AWS EFS CSI Driver (Recommended on AWS EKS)

Amazon Elastic File System (EFS) provides scalable POSIX-compliant ReadWriteMany NFS storage.

1. Install AWS EFS CSI Driver:
```bash
helm repo add aws-efs-csi-driver https://kubernetes-sigs.github.io/aws-efs-csi-driver/
helm upgrade --install aws-efs-csi-driver aws-efs-csi-driver/aws-efs-csi-driver \
  --namespace kube-system \
  --set controller.serviceAccount.create=false \
  --set controller.serviceAccount.name=efs-csi-controller-sa
```

2. Create an EFS StorageClass:
```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: efs-sc
provisioner: efs.csi.aws.com
parameters:
  provisioningMode: efs-ap
  fileSystemId: fs-0123456789abcdef0
  directoryPerms: "700"
```

3. Enable in Helm Values (`values-prod.yaml`):
```yaml
lakehouse:
  enabled: true
  archivalPath: "/data/archive"
  persistence:
    enabled: true
    storageClass: "efs-sc"
    accessMode: ReadWriteMany
    size: 500Gi
```

---

### Option B: Google Cloud Filestore (Recommended on GKE)

1. Enable the GKE Filestore CSI driver or create a StorageClass:
```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: filestore-sc
provisioner: filestore.csi.storage.gke.io
volumeBindingMode: Immediate
allowVolumeExpansion: true
parameters:
  tier: standard
  network: default
```

2. Enable in Helm Values:
```yaml
lakehouse:
  enabled: true
  archivalPath: "/data/archive"
  persistence:
    enabled: true
    storageClass: "filestore-sc"
    accessMode: ReadWriteMany
    size: 1Ti
```

---

## 3. Cloud IAM Connectors

To allow the Sentinel backend pods to sync cold Parquet archives directly with Amazon S3 or Google Cloud Storage without hardcoded long-lived credentials, configure Workload Identity / IRSA.

### 3.1. AWS IRSA (IAM Roles for Service Accounts)

1. Create the IAM Policy for the S3 Cold Storage bucket:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SentinelLakehouseS3Access",
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket",
        "s3:GetBucketLocation"
      ],
      "Resource": "arn:aws:s3:::sentinel-lakehouse-coldstorage"
    },
    {
      "Sid": "SentinelLakehouseParquetObjects",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::sentinel-lakehouse-coldstorage/*"
    }
  ]
}
```

2. Create IAM Role with OIDC Trust Policy:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::123456789012:oidc-provider/oidc.eks.us-east-1.amazonaws.com/id/EXAMPLE"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "oidc.eks.us-east-1.amazonaws.com/id/EXAMPLE:sub": "system:serviceaccount:sentinel-prod:sentinel-iot-backend"
        }
      }
    }
  ]
}
```

3. Annotate the Helm ServiceAccount in `values-prod.yaml`:
```yaml
serviceAccount:
  create: true
  name: sentinel-iot-backend
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789012:role/sentinel-backend-lakehouse
```

---

### 3.2. Google Cloud Workload Identity

1. Create GCP Service Account:
```bash
gcloud iam service-accounts create sentinel-lakehouse-sa \
  --description="Service account for Sentinel IoT DuckDB Lakehouse" \
  --display-name="sentinel-lakehouse-sa"
```

2. Grant GCS bucket permissions:
```bash
gcloud storage buckets add-iam-policy-binding gs://sentinel-lakehouse-coldstorage \
  --member="serviceAccount:sentinel-lakehouse-sa@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/storage.objectAdmin"
```

3. Bind Kubernetes ServiceAccount to GCP ServiceAccount:
```bash
gcloud iam service-accounts add-iam-policy-binding sentinel-lakehouse-sa@PROJECT_ID.iam.gserviceaccount.com \
  --role="roles/iam.workloadIdentityUser" \
  --member="serviceAccount:PROJECT_ID.svc.id.goog[sentinel-prod/sentinel-iot-backend]"
```

4. Annotate the Helm ServiceAccount in `values-prod.yaml`:
```yaml
serviceAccount:
  create: true
  name: sentinel-iot-backend
  annotations:
    iam.gke.io/gcp-service-account: sentinel-lakehouse-sa@PROJECT_ID.iam.gserviceaccount.com
```

---

## 4. Cold Storage Lifecycle Policies (S3 / GCS)

To minimize storage costs for multi-year industrial telemetry archives, configure automated tiering:

| Age | Storage Class | Cost / GB / Month | Purpose |
|-----|---------------|-------------------|---------|
| 0 – 90 days | Hot Volume / S3 Standard | ~$0.023 | Frequent OLAP interactive queries via DuckDB UI |
| 91 – 365 days | S3 Standard-IA / GCS Nearline | ~$0.0125 | Monthly reporting and seasonal model training |
| 366 – 730 days | S3 Glacier Instant Retrieval / GCS Coldline | ~$0.004 | Compliance and multi-year trend audits |
| > 730 days | S3 Glacier Deep Archive / GCS Archive | ~$0.00099 | Statutory regulatory compliance archive |

### Amazon S3 Lifecycle Configuration:
```json
{
  "Rules": [
    {
      "ID": "SentinelParquetTiering",
      "Status": "Enabled",
      "Filter": { "Prefix": "data/archive/" },
      "Transitions": [
        { "Days": 90, "StorageClass": "STANDARD_IA" },
        { "Days": 365, "StorageClass": "GLACIER_IR" },
        { "Days": 730, "StorageClass": "DEEP_ARCHIVE" }
      ]
    }
  ]
}
```

---

## 5. Helm Deployment Verification

To verify the Helm chart renders cleanly and matches your cluster topology:

```bash
# 1. Lint chart
helm lint infra/helm/sentinel-iot -f infra/helm/sentinel-iot/values-prod.yaml

# 2. Dry-run render
helm template sentinel-iot infra/helm/sentinel-iot \
  -f infra/helm/sentinel-iot/values-prod.yaml \
  --set backend.secretName=sentinel-prod-secrets \
  --debug

# 3. Upgrade / Install
helm upgrade --install sentinel-iot ./infra/helm/sentinel-iot \
  -f infra/helm/sentinel-iot/values-prod.yaml \
  --set backend.secretName=sentinel-prod-secrets \
  --namespace sentinel-prod \
  --create-namespace
```
