import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Chip,
  Alert,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  LinearProgress,
  Paper,
  Divider,
} from '@mui/material';
import {
  CheckCircle as CheckIcon,
  Cancel as CancelIcon,
  Warning as WarningIcon,
} from '@mui/icons-material';
import { GenerationThreshold, Bucket, BucketStatus } from '../../types/models';

interface ThresholdPreviewProps {
  threshold: Partial<GenerationThreshold>;
  buckets?: Bucket[];
  isLoading?: boolean;
}

const ThresholdPreview: React.FC<ThresholdPreviewProps> = ({
  threshold,
  buckets,
  isLoading = false,
}) => {
  const evaluateBucket = (bucket: Bucket): {
    wouldTrigger: boolean;
    reason: string;
    progress: number;
  } => {
    if (!threshold.thresholdType) {
      return { wouldTrigger: false, reason: 'No threshold type selected', progress: 0 };
    }

    const reasons: string[] = [];
    let maxProgress = 0;

    // Check claim count
    if (threshold.maxClaims && (threshold.thresholdType === 'CLAIM_COUNT' || threshold.thresholdType === 'HYBRID')) {
      const claimProgress = (bucket.claimCount / threshold.maxClaims) * 100;
      maxProgress = Math.max(maxProgress, claimProgress);

      if (bucket.claimCount >= threshold.maxClaims) {
        reasons.push(`Claims: ${bucket.claimCount}/${threshold.maxClaims}`);
      }
    }

    // Check amount
    if (threshold.maxAmount && (threshold.thresholdType === 'AMOUNT' || threshold.thresholdType === 'HYBRID')) {
      const amountProgress = (bucket.totalAmount / threshold.maxAmount) * 100;
      maxProgress = Math.max(maxProgress, amountProgress);

      if (bucket.totalAmount >= threshold.maxAmount) {
        reasons.push(`Amount: $${bucket.totalAmount.toLocaleString()}/$${threshold.maxAmount.toLocaleString()}`);
      }
    }

    // For time-based, we can't evaluate without a schedule
    if (threshold.thresholdType === 'TIME' || (threshold.thresholdType === 'HYBRID' && threshold.timeDuration)) {
      reasons.push(`Time-based: ${threshold.timeDuration || 'Not configured'}`);
    }

    const wouldTrigger = reasons.length > 0 && (
      (threshold.maxClaims && bucket.claimCount >= threshold.maxClaims) ||
      (threshold.maxAmount && bucket.totalAmount >= threshold.maxAmount)
    );

    return {
      wouldTrigger,
      reason: reasons.length > 0 ? reasons.join(', ') : 'No conditions met',
      progress: Math.min(maxProgress, 100),
    };
  };

  const affectedBuckets = buckets?.filter((b) => {
    // Only show ACCUMULATING buckets
    if (b.status !== BucketStatus.ACCUMULATING) return false;

    // If threshold is linked to a specific rule, filter by it
    const thresholdRuleId = threshold.linkedBucketingRule?.id || threshold.linkedBucketingRule?.ruleId;
    if (thresholdRuleId) {
      const bucketRuleId = b.bucketingRule?.id || b.bucketingRule?.ruleId;
      return bucketRuleId === thresholdRuleId;
    }

    return true;
  }) || [];

  const bucketsWouldTrigger = affectedBuckets.filter((b) => evaluateBucket(b).wouldTrigger);

  if (isLoading) {
    return (
      <Box>
        <Typography variant="h6" gutterBottom sx={{ fontWeight: 600, mb: 2 }}>
          Threshold Preview
        </Typography>
        <LinearProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h6" gutterBottom sx={{ fontWeight: 600, mb: 1 }}>
        Threshold Preview
      </Typography>
      <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
        See which active buckets would be affected by this threshold configuration.
      </Typography>

      {/* Summary Card */}
      <Card sx={{ mb: 3, backgroundColor: '#f5f5f5' }}>
        <CardContent>
          <Box display="flex" gap={3} flexWrap="wrap">
            <Box>
              <Typography variant="caption" color="textSecondary">
                Affected Buckets
              </Typography>
              <Typography variant="h4" fontWeight={600}>
                {affectedBuckets.length}
              </Typography>
            </Box>
            <Divider orientation="vertical" flexItem />
            <Box>
              <Typography variant="caption" color="textSecondary">
                Would Trigger Now
              </Typography>
              <Typography variant="h4" fontWeight={600} color="warning.main">
                {bucketsWouldTrigger.length}
              </Typography>
            </Box>
            <Divider orientation="vertical" flexItem />
            <Box>
              <Typography variant="caption" color="textSecondary">
                Below Threshold
              </Typography>
              <Typography variant="h4" fontWeight={600} color="success.main">
                {affectedBuckets.length - bucketsWouldTrigger.length}
              </Typography>
            </Box>
          </Box>
        </CardContent>
      </Card>

      {/* Warning if buckets would trigger immediately */}
      {bucketsWouldTrigger.length > 0 && (
        <Alert severity="warning" icon={<WarningIcon />} sx={{ mb: 2 }}>
          <Typography variant="body2" fontWeight={600}>
            {bucketsWouldTrigger.length} bucket(s) would trigger immediately upon saving this threshold.
          </Typography>
          <Typography variant="caption">
            These buckets have already met the threshold conditions and will transition to PENDING_APPROVAL.
          </Typography>
        </Alert>
      )}

      {/* Affected Buckets Table */}
      {affectedBuckets.length > 0 ? (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell><strong>Status</strong></TableCell>
                <TableCell><strong>Bucket</strong></TableCell>
                <TableCell><strong>Claims</strong></TableCell>
                <TableCell><strong>Amount</strong></TableCell>
                <TableCell><strong>Progress</strong></TableCell>
                <TableCell><strong>Evaluation</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {affectedBuckets.map((bucket) => {
                const evaluation = evaluateBucket(bucket);
                return (
                  <TableRow key={bucket.bucketId}>
                    <TableCell>
                      {evaluation.wouldTrigger ? (
                        <Chip
                          icon={<CheckIcon />}
                          label="Would Trigger"
                          color="warning"
                          size="small"
                        />
                      ) : (
                        <Chip
                          icon={<CancelIcon />}
                          label="Below Threshold"
                          color="default"
                          size="small"
                        />
                      )}
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>
                        {bucket.bucketingRuleName}
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        {bucket.payerName} → {bucket.payeeName}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {bucket.claimCount}
                        {threshold.maxClaims && ` / ${threshold.maxClaims}`}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        ${bucket.totalAmount.toLocaleString()}
                        {threshold.maxAmount && (
                          <Typography variant="caption" color="textSecondary">
                            {' '}/ ${threshold.maxAmount.toLocaleString()}
                          </Typography>
                        )}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Box sx={{ minWidth: 100 }}>
                        <LinearProgress
                          variant="determinate"
                          value={evaluation.progress}
                          color={evaluation.wouldTrigger ? 'warning' : 'primary'}
                          sx={{ height: 8, borderRadius: 1 }}
                        />
                        <Typography variant="caption" color="textSecondary">
                          {evaluation.progress.toFixed(0)}%
                        </Typography>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption">{evaluation.reason}</Typography>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      ) : (
        <Alert severity="info">
          <Typography variant="body2">
            No active buckets would be affected by this threshold.
            {(threshold.linkedBucketingRule?.id || threshold.linkedBucketingRule?.ruleId)
              ? ' The linked bucketing rule has no active buckets.'
              : ' Create bucketing rules and accumulate claims to see preview.'}
          </Typography>
        </Alert>
      )}
    </Box>
  );
};

export default ThresholdPreview;
