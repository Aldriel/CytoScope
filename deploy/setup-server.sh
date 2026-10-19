#!/usr/bin/env bash
#
# Installs or updates CytoScope on an Ubuntu 24.04 server: the REST API runs as a systemd service
# on 127.0.0.1:7072, and Caddy serves the web client and forwards /api to it, over HTTPS with a
# Let's Encrypt certificate.
#
# The server can host other applications, such as MabScope: each one keeps its site in
# /etc/caddy/sites/<domain>.caddy, imported by the main Caddyfile.
#
# Upload this script next to the built client (web/dist), then run it as root:
#   bash setup-server.sh cytoscope.maximeethier.com
# Running it again pulls the latest code from GitHub and redeploys.

set -euo pipefail

DOMAIN="${1:?usage: setup-server.sh <domain>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO=https://github.com/Aldriel/CytoScope.git
APP=/opt/cytoscope
WEB=/var/www/cytoscope
SITES=/etc/caddy/sites

if [[ ! -f "$HERE/dist/index.html" ]]; then
  echo "Missing $HERE/dist: upload web/dist next to this script." >&2
  exit 1
fi

# Maven and several JVMs need swap on a small server to avoid being killed for lack of memory.
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y openjdk-21-jdk-headless maven git caddy ufw unattended-upgrades

ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

id cytoscope &>/dev/null || useradd --system --home-dir "$APP" --shell /usr/sbin/nologin cytoscope

if [[ -d "$APP/src/.git" ]]; then
  git -C "$APP/src" pull --ff-only
else
  mkdir -p "$APP"
  git clone --depth 1 "$REPO" "$APP/src"
fi

cd "$APP/src"
# The tests and the style checks already ran before the code was pushed.
mvn -B -q -pl server -am install -DskipTests -Dcheckstyle.skip -Djacoco.skip
rm -rf "$APP/lib"
mvn -B -q -pl server dependency:copy-dependencies \
  -DincludeScope=runtime -DoutputDirectory="$APP/lib"
cp server/target/cytoscope-server-*.jar "$APP/lib/"

cat > /etc/systemd/system/cytoscope.service <<EOF
[Unit]
Description=CytoScope REST API
After=network-online.target
Wants=network-online.target

[Service]
User=cytoscope
Environment=PORT=7072
EnvironmentFile=-/etc/cytoscope/stripe.env
# The FCS files of the visitors stay in memory for 30 minutes; -Xmx still leaves room for the
# system, Caddy and the other applications of the server.
# Optional Stripe keys in /etc/cytoscope/stripe.env: STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET.
ExecStart=/usr/bin/java -Xms128m -Xmx768m -Djava.awt.headless=true \\
    -cp "$APP/lib/*" com.bioinfo.cytoscope.server.ApiServer
Restart=always
RestartSec=5
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF

mkdir -p "$WEB"
rm -rf "${WEB:?}"/*
cp -r "$HERE/dist/." "$WEB/"

# The main Caddyfile only imports the sites. A Caddyfile written by an older script, which held a
# single site, is kept as the file of that site; the default page of the package is dropped.
mkdir -p "$SITES"
if ! grep -qs "^import $SITES/\*.caddy" /etc/caddy/Caddyfile; then
  if grep -qs 'reverse_proxy' /etc/caddy/Caddyfile; then
    previous="$(awk 'NF && $1 !~ /^#/ { print $1; exit }' /etc/caddy/Caddyfile)"
    cp /etc/caddy/Caddyfile "$SITES/$previous.caddy"
  fi
  echo "import $SITES/*.caddy" > /etc/caddy/Caddyfile
fi

# The API refuses files above 50 MB itself; Caddy only stops much larger bodies.
cat > "$SITES/$DOMAIN.caddy" <<EOF
$DOMAIN {
	encode zstd gzip

	handle /api/* {
		request_body {
			max_size 60MB
		}
		reverse_proxy 127.0.0.1:7072
	}

	handle {
		root * $WEB
		try_files {path} /index.html
		file_server
	}

	@assets path /assets/*
	header @assets Cache-Control "public, max-age=31536000, immutable"
}
EOF
caddy validate --adapter caddyfile --config /etc/caddy/Caddyfile

systemctl daemon-reload
systemctl enable cytoscope caddy
systemctl restart cytoscope
systemctl reload caddy || systemctl restart caddy

sleep 5
if curl -fsS http://127.0.0.1:7072/api/health; then
  echo
  echo "API is running. Open https://$DOMAIN once the DNS record points to this server."
else
  echo "The API did not answer; see: journalctl -u cytoscope -n 50" >&2
  exit 1
fi
