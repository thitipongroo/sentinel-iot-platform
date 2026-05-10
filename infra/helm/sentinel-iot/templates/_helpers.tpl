{{/*
Expand the name of the chart.
*/}}
{{- define "sentinel-iot.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name (release-chart, max 63 chars).
*/}}
{{- define "sentinel-iot.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Chart label (name-version).
*/}}
{{- define "sentinel-iot.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "sentinel-iot.labels" -}}
helm.sh/chart: {{ include "sentinel-iot.chart" . }}
{{ include "sentinel-iot.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels — used by Deployments/StatefulSets and their Services.
The `component` sub-label scopes selectors within the same release.
*/}}
{{- define "sentinel-iot.selectorLabels" -}}
app.kubernetes.io/name: {{ include "sentinel-iot.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Image reference helper. Prepends global.imageRegistry when set.
Usage: {{ include "sentinel-iot.image" (dict "registry" .Values.global.imageRegistry "repo" .Values.backend.image.repository "tag" .Values.backend.image.tag) }}
*/}}
{{- define "sentinel-iot.image" -}}
{{- if .registry }}
{{- printf "%s/%s:%s" .registry .repo .tag }}
{{- else }}
{{- printf "%s:%s" .repo .tag }}
{{- end }}
{{- end }}

{{/*
Database host — returns external host when in-cluster postgres is disabled.
*/}}
{{- define "sentinel-iot.dbHost" -}}
{{- if .Values.postgres.enabled }}
{{- include "sentinel-iot.fullname" . }}-postgres
{{- else }}
{{- .Values.externalPostgres.host }}
{{- end }}
{{- end }}

{{/*
Redis host — returns external host when in-cluster redis is disabled.
*/}}
{{- define "sentinel-iot.redisHost" -}}
{{- if .Values.redis.enabled }}
{{- include "sentinel-iot.fullname" . }}-redis
{{- else }}
{{- .Values.externalRedis.host }}
{{- end }}
{{- end }}

{{/*
Kafka bootstrap servers — returns external when in-cluster kafka is disabled.
*/}}
{{- define "sentinel-iot.kafkaBootstrap" -}}
{{- if .Values.kafka.enabled }}
{{- include "sentinel-iot.fullname" . }}-kafka:9092
{{- else }}
{{- .Values.externalKafka.bootstrapServers }}
{{- end }}
{{- end }}
