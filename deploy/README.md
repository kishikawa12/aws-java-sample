# Deploy & monitor `aws-java-sample` on EC2 with Dynatrace

A runbook for deploying this batch app to a single EC2 instance, monitoring it with
Dynatrace OneAgent, and comparing **AWS SDK v1 vs v2** (migration done with AWS Transform).

The app runs once and exits in seconds, so we run it on a **systemd timer** to produce a
steady stream of executions for Dynatrace to observe.

## Files

| File | Purpose |
|------|---------|
| `s3-policy.json` | Least-privilege IAM policy for the EC2 instance role (scoped to `my-first-s3-bucket-*`). |
| `run.sh` | Launcher. Sets `DT_RELEASE_*` env vars (version label) and runs the jar. |
| `s3sample.service` / `s3sample.timer` | Run the app every 2 minutes. |
| `send-deploy-event.sh` | Posts a Dynatrace deployment event when you cut over to v2. |

## Prerequisites

- AWS account + region; access to the instance via **SSM Session Manager** (no SSH key needed).
- Dynatrace tenant URL, a **PaaS token** (OneAgent install), and an **API token** with
  `events.ingest` scope (deployment events).
- EC2 subnet with outbound internet (reach S3 + the Dynatrace cluster).

## Phase B — Build the artifact (local)

```sh
mvn clean package          # -> target/aws-java-sample-1.0.jar (self-contained)
```

## Phase C — Provision

1. Create the IAM role + instance profile:
   ```sh
   aws iam create-policy --policy-name s3sample-policy --policy-document file://s3-policy.json
   # create role with EC2 trust + SSM managed policy, attach s3sample-policy, make an instance profile
   ```
2. Launch an Amazon Linux 2023 `t3.small` with that instance profile and SSM enabled.
3. On the box: `sudo dnf install -y java-25-amazon-corretto maven`
4. Install Dynatrace OneAgent (full-stack):
   ```sh
   wget -O Dynatrace-OneAgent.sh "https://<TENANT>.live.dynatrace.com/api/v1/deployment/installer/agent/unix/default/latest?arch=x86&flavor=default" \
     --header="Authorization: Api-Token <PAAS_TOKEN>"
   sudo /bin/sh Dynatrace-OneAgent.sh --set-app-log-content-access=true
   ```
   Confirm the host appears in Dynatrace.

## Phase D — Deploy + schedule (baseline = SDK v1)

```sh
sudo mkdir -p /opt/s3sample
sudo cp target/aws-java-sample-1.0.jar /opt/s3sample/app.jar
sudo cp deploy/run.sh /opt/s3sample/run.sh && sudo chmod +x /opt/s3sample/run.sh
sudo cp deploy/s3sample.service deploy/s3sample.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now s3sample.timer
```

Sanity check (proves the instance role works — no credentials file needed):
```sh
/opt/s3sample/run.sh
journalctl -u s3sample.service -f
```

## Phase E — Verify baseline

In Dynatrace: host present → Java **process group** detected → open a **PurePath** and confirm
the S3 calls (create/put/get/delete) appear as outgoing AWS/HTTP calls, tagged `app=s3sample`,
release version `1.0-sdkv1`. Let it run ~30–60 min to build a baseline.

## Phase F — Migrate SDK v1 → v2 (AWS Transform)

Run **AWS Transform for Java** on a branch. It rewrites `pom.xml`
(`com.amazonaws:aws-java-sdk` → `software.amazon.awssdk:s3`) and
`S3Sample.java` (`AmazonS3Client` → `S3Client`, builder-style requests, `RequestBody`, etc.).
Review the diff, then `mvn clean package`.

## Phase G — Redeploy + mark the boundary

1. Edit `run.sh`: set `DT_RELEASE_VERSION="2.0-sdkv2"`, re-copy to `/opt/s3sample/run.sh`.
2. Mark the cutover on the Dynatrace timeline:
   ```sh
   export DT_URL=https://<TENANT>.live.dynatrace.com
   export DT_API_TOKEN=<events.ingest token>
   export DT_HOST_NAME=<ec2 host name in Dynatrace>
   ./deploy/send-deploy-event.sh
   ```
3. Replace the jar:
   ```sh
   sudo cp target/aws-java-sample-1.0.jar /opt/s3sample/app.jar
   ```
   The timer keeps firing; new runs report `2.0-sdkv2`.

## Phase H — Compare

Split the process-group **response time / CPU / GC** by `DT_RELEASE_VERSION`
(`1.0-sdkv1` vs `2.0-sdkv2`), diff a v1 vs v2 **PurePath**, and check failure rate either side
of the deployment-event marker. SDK v2 uses a different HTTP client stack, so timing/CPU usually shift.

## Cleanup

Stop the timer, terminate the instance, delete the IAM role/policy. The app deletes its own
bucket each run, so no S3 residue is expected.
