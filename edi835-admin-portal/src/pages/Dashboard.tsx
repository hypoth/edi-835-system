import React from 'react';
import {
  Grid,
  Card,
  CardContent,
  Typography,
  Box,
  CircularProgress,
  Alert,
  Chip,
} from '@mui/material';
import {
  Folder as FolderIcon,
  Description as DescriptionIcon,
  Assessment as AssessmentIcon,
  CheckCircle as CheckCircleIcon,
  Warning as WarningIcon,
  Error as ErrorIcon,
} from '@mui/icons-material';
import { useQuery } from '@tanstack/react-query';
import { dashboardService } from '../services/dashboardService';
import { bucketService } from '../services/bucketService';
import { DashboardSummary, SystemHealth, Alert as SystemAlert, Bucket } from '../types/models';

interface MetricCardProps {
  title: string;
  value: number | string;
  icon: React.ReactElement;
  color: string;
  subtitle?: string;
}

const MetricCard: React.FC<MetricCardProps> = ({ title, value, icon, color, subtitle }) => (
  <Card>
    <CardContent>
      <Box display="flex" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography color="textSecondary" gutterBottom variant="body2">
            {title}
          </Typography>
          <Typography variant="h4" component="div" sx={{ fontWeight: 600, color }}>
            {value}
          </Typography>
          {subtitle && (
            <Typography variant="caption" color="textSecondary">
              {subtitle}
            </Typography>
          )}
        </Box>
        <Box
          sx={{
            backgroundColor: `${color}20`,
            borderRadius: 2,
            p: 1.5,
            display: 'flex',
            alignItems: 'center',
          }}
        >
          {React.cloneElement(icon, { sx: { fontSize: 40, color } })}
        </Box>
      </Box>
    </CardContent>
  </Card>
);

interface RecentBucketCardProps {
  bucket: Bucket;
}

const RecentBucketCard: React.FC<RecentBucketCardProps> = ({ bucket }) => {
  const getStatusColor = (status: string) => {
    switch (status) {
      case 'ACCUMULATING':
        return 'primary';
      case 'PENDING_APPROVAL':
        return 'warning';
      case 'GENERATING':
        return 'info';
      case 'COMPLETED':
        return 'success';
      case 'FAILED':
        return 'error';
      default:
        return 'default';
    }
  };

  return (
    <Card sx={{ mb: 2 }}>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="start">
          <Box flex={1}>
            <Typography variant="subtitle1" fontWeight={600}>
              {bucket.payerName} → {bucket.payeeName}
            </Typography>
            <Typography variant="body2" color="textSecondary">
              {bucket.claimCount} claims • ${bucket.totalAmount.toLocaleString()}
            </Typography>
            <Typography variant="caption" color="textSecondary">
              Created: {new Date(bucket.createdAt).toLocaleString()}
            </Typography>
          </Box>
          <Chip
            label={bucket.status.replace('_', ' ')}
            color={getStatusColor(bucket.status) as any}
            size="small"
          />
        </Box>
      </CardContent>
    </Card>
  );
};

const Dashboard: React.FC = () => {
  // Fetch dashboard summary
  const { data: summary, isLoading: summaryLoading, error: summaryError } = useQuery<DashboardSummary>({
    queryKey: ['dashboardSummary'],
    queryFn: dashboardService.getSummary,
    refetchInterval: 30000, // Refresh every 30 seconds
    retry: false, // Don't retry on error
  });

  // Fetch system health
  const { data: health } = useQuery<SystemHealth>({
    queryKey: ['systemHealth'],
    queryFn: dashboardService.getSystemHealth,
    refetchInterval: 60000, // Refresh every minute
    retry: false,
  });

  // Fetch alerts
  const { data: alerts } = useQuery<SystemAlert[]>({
    queryKey: ['systemAlerts'],
    queryFn: dashboardService.getAlerts,
    refetchInterval: 30000,
    retry: false,
  });

  // Fetch recent buckets
  const { data: recentBuckets } = useQuery<Bucket[]>({
    queryKey: ['recentBuckets'],
    queryFn: () => bucketService.getRecentBuckets(5),
    refetchInterval: 30000,
    retry: false,
  });

  if (summaryLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  if (summaryError) {
    return (
      <Box p={3}>
        <Alert severity="warning" sx={{ mb: 2 }}>
          <Typography variant="body1" fontWeight={600} gutterBottom>
            Unable to connect to the backend API
          </Typography>
          <Typography variant="body2">
            The backend server may not be running. Please ensure the backend is started at{' '}
            <strong>http://localhost:8080</strong>
          </Typography>
          <Typography variant="body2" sx={{ mt: 1 }}>
            Error: {summaryError instanceof Error ? summaryError.message : 'Connection failed'}
          </Typography>
        </Alert>
        <Typography variant="h4" fontWeight={600} gutterBottom>
          EDI 835 Admin Portal
        </Typography>
        <Typography variant="body1" color="textSecondary">
          Welcome to the EDI 835 Administration Portal. Start the backend server to view dashboard data.
        </Typography>
      </Box>
    );
  }

  const getHealthIcon = () => {
    switch (health?.status) {
      case 'HEALTHY':
        return <CheckCircleIcon sx={{ color: 'success.main' }} />;
      case 'WARNING':
        return <WarningIcon sx={{ color: 'warning.main' }} />;
      case 'CRITICAL':
        return <ErrorIcon sx={{ color: 'error.main' }} />;
      default:
        return <CheckCircleIcon />;
    }
  };

  const getHealthColor = () => {
    switch (health?.status) {
      case 'HEALTHY':
        return 'success.main';
      case 'WARNING':
        return 'warning.main';
      case 'CRITICAL':
        return 'error.main';
      default:
        return 'grey.500';
    }
  };

  return (
    <Box>
      {/* Page Header */}
      <Box mb={3}>
        <Typography variant="h4" fontWeight={600} gutterBottom>
          Dashboard
        </Typography>
        <Typography variant="body1" color="textSecondary">
          Real-time metrics and system overview
        </Typography>
      </Box>

      {/* System Health */}
      {health && (
        <Box mb={3}>
          <Alert
            severity={health.status === 'HEALTHY' ? 'success' : health.status === 'WARNING' ? 'warning' : 'error'}
            icon={getHealthIcon()}
          >
            <Typography variant="body2">
              <strong>System Status: {health.status}</strong>
              {health.staleBuckets > 0 && ` • ${health.staleBuckets} stale bucket(s)`}
              {health.failedDeliveries > 0 && ` • ${health.failedDeliveries} failed deliveries`}
              {health.pendingApprovals > 0 && ` • ${health.pendingApprovals} pending approvals`}
            </Typography>
          </Alert>
        </Box>
      )}

      {/* Alerts */}
      {alerts && alerts.length > 0 && (
        <Box mb={3}>
          {alerts.map((alert, index) => (
            <Alert
              key={index}
              severity={alert.severity.toLowerCase() as any}
              sx={{ mb: 1 }}
            >
              {alert.message}
            </Alert>
          ))}
        </Box>
      )}

      {/* Metrics Grid */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Total Buckets"
            value={summary?.totalBuckets || 0}
            icon={<FolderIcon />}
            color="#1976d2"
            subtitle={`${summary?.activeBuckets || 0} active`}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Pending Approvals"
            value={summary?.pendingApprovalBuckets || 0}
            icon={<CheckCircleIcon />}
            color="#ed6c02"
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Total Files"
            value={summary?.totalFiles || 0}
            icon={<DescriptionIcon />}
            color="#2e7d32"
            subtitle={`${summary?.pendingDeliveryFiles || 0} pending delivery`}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Total Claims"
            value={summary?.totalClaims || 0}
            icon={<AssessmentIcon />}
            color="#9c27b0"
            subtitle={`${summary?.processedClaims || 0} processed`}
          />
        </Grid>
      </Grid>

      {/* Secondary Metrics */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={4}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom variant="body2">
                Failed Deliveries
              </Typography>
              <Typography variant="h5" color="error.main" fontWeight={600}>
                {summary?.failedDeliveryFiles || 0}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom variant="body2">
                Rejected Claims
              </Typography>
              <Typography variant="h5" color="error.main" fontWeight={600}>
                {summary?.rejectedClaims || 0}
              </Typography>
              <Typography variant="caption" color="textSecondary">
                {summary?.totalClaims
                  ? `${((summary.rejectedClaims / summary.totalClaims) * 100).toFixed(1)}% rejection rate`
                  : ''}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Card>
            <CardContent>
              <Typography color="textSecondary" gutterBottom variant="body2">
                Processing Rate
              </Typography>
              <Typography variant="h5" color="success.main" fontWeight={600}>
                {summary?.totalClaims
                  ? `${((summary.processedClaims / summary.totalClaims) * 100).toFixed(1)}%`
                  : '0%'}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Recent Activity */}
      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" fontWeight={600} gutterBottom>
                Recent Buckets
              </Typography>
              {recentBuckets && recentBuckets.length > 0 ? (
                <Box mt={2}>
                  {recentBuckets.map((bucket) => (
                    <RecentBucketCard key={bucket.bucketId} bucket={bucket} />
                  ))}
                </Box>
              ) : (
                <Typography variant="body2" color="textSecondary" mt={2}>
                  No recent buckets
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" fontWeight={600} gutterBottom>
                Quick Actions
              </Typography>
              <Box mt={2}>
                <Alert severity="info" sx={{ mb: 2 }}>
                  <Typography variant="body2">
                    <strong>Tip:</strong> Navigate to Buckets or Approvals to manage your workflow.
                  </Typography>
                </Alert>
                <Typography variant="body2" color="textSecondary">
                  • View all buckets in <strong>Buckets</strong> section
                </Typography>
                <Typography variant="body2" color="textSecondary">
                  • Manage approvals in <strong>Approvals</strong> section
                </Typography>
                <Typography variant="body2" color="textSecondary">
                  • Monitor file delivery in <strong>Delivery</strong> section
                </Typography>
                <Typography variant="body2" color="textSecondary">
                  • Configure system in <strong>Configuration</strong> section
                </Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default Dashboard;
