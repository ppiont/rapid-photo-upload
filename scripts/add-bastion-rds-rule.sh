#!/bin/bash
# Script to add bastion host access to RDS security group
# This is a backup script - the rule should now be managed by Terraform

set -e

# Configuration
RDS_SG_ID="sg-0e0529d98b316fabb"
BASTION_SG_ID="sg-0ff52227db8080630"
REGION="us-west-1"

echo "Adding bastion host security group rule to RDS..."
echo "  RDS Security Group: $RDS_SG_ID"
echo "  Bastion Security Group: $BASTION_SG_ID"
echo "  Region: $REGION"
echo ""

# Check if rule already exists
if aws ec2 describe-security-groups \
  --group-ids "$RDS_SG_ID" \
  --region "$REGION" \
  --query "SecurityGroups[0].IpPermissions[?FromPort==\`5432\`].UserIdGroupPairs[?GroupId==\`$BASTION_SG_ID\`]" \
  --output text | grep -q "$BASTION_SG_ID"; then
  echo "✓ Rule already exists, no action needed"
  exit 0
fi

# Add the rule
echo "Adding security group rule..."
aws ec2 authorize-security-group-ingress \
  --group-id "$RDS_SG_ID" \
  --protocol tcp \
  --port 5432 \
  --source-group "$BASTION_SG_ID" \
  --region "$REGION"

echo "✓ Successfully added bastion host access to RDS security group"
