#!/bin/bash
set -e

EC2_IP=$1

if [ -z "$EC2_IP" ]; then
  echo "Usage: ./deploy.sh <EC2_PUBLIC_IP>"
  exit 1
fi

# Expand tilde manually for rsync/ssh
SSH_KEY="$HOME/.ssh/rivetdeploy_rsa"
REMOTE_USER="ec2-user"
REMOTE_DIR="/home/ec2-user/rivetdeploy"
SSH_OPTS="-o StrictHostKeyChecking=no -i $SSH_KEY"

echo "Deploying to $EC2_IP..."

echo "1. Creating remote directory..."
ssh $SSH_OPTS $REMOTE_USER@$EC2_IP "mkdir -p $REMOTE_DIR"

echo "2. Transferring source code..."
rsync -avz --exclude 'node_modules' --exclude 'target' --exclude '.git' --exclude 'dist' -e "ssh $SSH_OPTS" ./ $REMOTE_USER@$EC2_IP:$REMOTE_DIR/

echo "3. Updating environment variables and starting Docker Compose..."
ssh $SSH_OPTS $REMOTE_USER@$EC2_IP << EOF
  cd $REMOTE_DIR
  # Update the FRONTEND_URL to use nip.io
  sed -i "s|RIVETDEPLOY_FRONTEND_URL=.*|RIVETDEPLOY_FRONTEND_URL=http://\$EC2_IP.nip.io|g" docker-compose.yml
  
  # Ensure docker is ready
  sudo systemctl start docker || true

  # Start the application
  /usr/local/bin/docker-compose down || true
  /usr/local/bin/docker-compose up -d --build
EOF

echo "Deployment completed successfully! The app should be available at http://$EC2_IP.nip.io"
