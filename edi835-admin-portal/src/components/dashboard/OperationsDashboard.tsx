// src/components/dashboard/OperationsDashboard.tsx

import React, { useState } from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Alert,
  CircularProgress,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { dashboardService } from '../../services/dashboardService';
import SummaryCards from './SummaryCards';
import ActiveBucketsWidget from './ActiveBucketsWidget';
import RejectionAnalytics from './RejectionAnalytics';
import PendingApprovalsAlert from './PendingApprovalsAlert';

const OperationsDashboard: React.FC = () => {
  const [autoRefresh, setAutoRefresh] = useState(true);

  // Fetch dashboard summary
  const { data: summary, isLoading: summaryLoading, error: summaryError } = useQuery({
    queryKey: ['dashboardSummary'],
    queryFn: dashboardService.getSummary,
    refetchInterval: autoRefresh ? 30000 : false, // Refresh every 30 seconds
  });

  // Fetch active buckets
  const { data: activeBuckets, isLoading: bucketsLoading } = useQuery({
    queryKey: ['activeBuckets'],
    queryFn: dashboardService.getActiveBuckets,
    refetchInterval: autoRefresh ? 15000 : false, // Refresh every 15 seconds
  });

  // Fetch pending approvals
  const { data: pendingApprovals, isLoading: approvalsLoading } = useQuery({
    queryKey: ['pendingApprovals'],
    queryFn: dashboardService.getPendingApprovals,
    refetchInterval: autoRefresh ? 20000 : false,
  });

  // Fetch rejection analytics
  const { data: rejectionData, isLoading: rejectionsLoading } = useQuery({
    queryKey: ['rejectionAnalytics'],
    queryFn: () => dashboardService.getRejectionAnalytics(),
    refetchInterval: autoRefresh ? 60000 : false, // Refresh every minute
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
      <Alert severity="error">
        Failed to load dashboard data. Please try again.
      </Alert>
    );
  }

  return (
    <Box sx={{ flexGrow: 1, p: 3 }}>
      {/* Header */}
      <Box mb={3}>
        <Typography variant="h4" gutterBottom>
          Operations Dashboard
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Real-time monitoring of EDI 835 file generation and processing
        </Typography>
      </Box>

      {/* Pending Approvals Alert */}
      {pendingApprovals && pendingApprovals.length > 0 && (
        <Box mb={3}>
          <PendingApprovalsAlert
            pendingCount={pendingApprovals.length}
            buckets={pendingApprovals}
          />
        </Box>
      )}

      {/* Summary Cards */}
      <Grid container spacing={3} mb={3}>
        <SummaryCards summary={summary!} />
      </Grid>

      {/* Active Buckets and Analytics */}
      <Grid container spacing={3}>
        {/* Active Buckets Widget */}
        <Grid item xs={12} lg={7}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" gutterBottom>
              Active EDI Accumulations
            </Typography>
            {bucketsLoading ? (
              <Box display="flex" justifyContent="center" p={3}>
                <CircularProgress size={40} />
              </Box>
            ) : (
              <ActiveBucketsWidget buckets={activeBuckets || []} />
            )}
          </Paper>
        </Grid>

        {/* Rejection Analytics */}
        <Grid item xs={12} lg={5}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Typography variant="h6" gutterBottom>
              Rejection Analytics
            </Typography>
            {rejectionsLoading ? (
              <Box display="flex" justifyContent="center" p={3}>
                <CircularProgress size={40} />
              </Box>
            ) : (
              <RejectionAnalytics data={rejectionData || []} />
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default OperationsDashboard;