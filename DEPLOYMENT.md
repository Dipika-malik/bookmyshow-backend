# Deploying to AWS (EC2 + RDS)

This guide deploys the app as a Docker container on a free-tier EC2 instance,
backed by a free-tier RDS MySQL database.

## 1. Create the RDS MySQL database

1. AWS Console → RDS → **Create database**.
2. Engine: **MySQL**, version 8.x.
3. Templates: **Free tier**.
4. DB instance identifier: `bookmyshow-db`.
5. Master username: e.g. `admin`. Set and save a strong master password — you'll need it later.
6. Instance class: `db.t3.micro` (or whatever the free tier offers).
7. Storage: default (20 GB gp2) is fine.
8. **Connectivity**:
   - VPC: default (same VPC you'll launch EC2 into).
   - Public access: **No** (EC2 will reach it over the private VPC network — more secure).
   - VPC security group: create a new one, e.g. `bookmyshow-db-sg`.
9. Initial database name (under "Additional configuration"): `bookmyshow`.
10. Create database. Wait ~5-10 min until status is "Available".
11. Note the **endpoint** (hostname) shown on the DB's detail page, e.g.
    `bookmyshow-db.xxxxxxx.ap-south-1.rds.amazonaws.com`.

## 2. Launch the EC2 instance

1. EC2 → **Launch instance**.
2. Name: `bookmyshow-server`.
3. AMI: **Amazon Linux 2023** (free tier eligible).
4. Instance type: `t2.micro` or `t3.micro` (free tier).
5. Key pair: create a new one, download the `.pem` file, keep it safe — you need it to SSH in.
6. Network settings:
   - Use the same VPC as the RDS instance.
   - Allow SSH (port 22) from "My IP" only.
   - Allow HTTP (port 80) from anywhere — needed if you put Nginx in front.
   - Allow custom TCP **8082** from anywhere (only if you're skipping Nginx and hitting the app directly; otherwise skip this and just use 80).
7. Launch instance.

## 3. Let EC2 talk to RDS

1. Go to the RDS instance's security group (`bookmyshow-db-sg`).
2. Edit inbound rules → add rule: Type **MySQL/Aurora (3306)**, Source = the EC2 instance's security group.
   This lets only your EC2 box reach the database, not the public internet.

## 4. SSH into EC2 and install Docker

```bash
chmod 400 your-key.pem
ssh -i your-key.pem ec2-user@<EC2_PUBLIC_IP>

sudo yum update -y
sudo yum install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
# log out and back in for the group change to take effect
exit
ssh -i your-key.pem ec2-user@<EC2_PUBLIC_IP>
```

## 5. Get the code onto the instance

```bash
git clone https://github.com/<your-username>/<your-repo>.git
cd <your-repo>
```

## 6. Build and run the container

```bash
docker build -t bookmyshow-backend .

docker run -d \
  --name bookmyshow \
  -p 80:8082 \
  -e DB_URL="jdbc:mysql://<RDS_ENDPOINT>:3306/bookmyshow?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" \
  -e DB_USERNAME="admin" \
  -e DB_PASSWORD="<your RDS master password>" \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  -e DDL_AUTO=update \
  -e SHOW_SQL=false \
  --restart unless-stopped \
  bookmyshow-backend
```

Notes:
- `-p 80:8082` maps the container's port 8082 to the host's port 80, so you can hit the
  API at `http://<EC2_PUBLIC_IP>/...` without a reverse proxy.
- `--restart unless-stopped` makes the container come back up automatically if the
  instance reboots.
- Generate the JWT secret once and reuse it across restarts (don't regenerate every
  `docker run`, or existing tokens will become invalid). Save it somewhere safe.

## 7. Verify

```bash
curl http://localhost/actuator/health   # if actuator is added, otherwise hit a real endpoint
docker logs bookmyshow
```

From your own machine: `http://<EC2_PUBLIC_IP>/api/...` (check `AuthController` /
`MovieController` for actual paths).

## 8. (Optional) Domain + HTTPS

If you want a real domain with HTTPS instead of a bare IP:
1. Point a domain/subdomain's A record at the EC2 Elastic IP (allocate one in EC2 →
   Elastic IPs → associate with the instance, so the IP doesn't change on reboot).
2. Install Nginx as a reverse proxy + Certbot for free Let's Encrypt TLS, proxying to
   `localhost:8082` (in that case, run the container with `-p 8082:8082` instead of `-p 80:8082`).

## 9. Put it on your resume

Once it's live, use the Elastic IP, domain, or a link to a simple status/health
endpoint — and link the GitHub repo alongside it so reviewers can see the code too.

## Cost/safety notes

- `t2.micro`/`t3.micro` EC2 and `db.t3.micro` RDS are free-tier eligible for 12 months
  from account creation — after that (or if you exceed free-tier hours), they cost money.
- Stop (not just terminate) instances you're not actively demoing to avoid charges,
  or set a billing alarm in AWS Budgets.
- Never commit `DB_PASSWORD` or `JWT_SECRET` to git — they're passed as runtime env
  vars in the `docker run` command above, never written to a file in the repo.
