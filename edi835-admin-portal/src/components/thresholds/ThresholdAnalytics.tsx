import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Paper,
  Chip,
  LinearProgress,
  Divider,
} from '@mui/material';
import {
  TrendingUp as TrendIcon,
  AccessTime as TimeIcon,
  CheckCircle as SuccessIcon,
  Warning as WarningIcon,
} from '@mui/icons-material';
import {
  BarChart,
  Bar,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { GenerationThreshold } from '../../types/models';

export interface ThresholdAnalyticsData {
  thresholdId: string;
  triggerCount: number;
  averageClaimsAtTrigger: number;
  averageAmountAtTrigger: number;
  averageTimeToTrigger: number; // in hours
  lastTriggered?: string;
  failureRate: number; // percentage
  triggerHistory: Array<{
    date: string;
    claimCount: number;
    amount: number;
    triggerReason: string;
  }>;
  triggerReasonBreakdown: Array<{
    reason: string;
    count: number;
  }>;
}

interface ThresholdAnalyticsProps {
  threshold: GenerationThreshold;
  analyticsData?: ThresholdAnalyticsData;
  isLoading?: boolean;
}

const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884D8'];

const ThresholdAnalytics: React.FC<ThresholdAnalyticsProps> = ({
  threshold,
  analyticsData,
  isLoading = false,
}) => {
  if (isLoading) {
    return (
      <Box>
        <Typography variant="h6" gutterBottom>
          Loading Analytics...
        </Typography>
        <LinearProgress />
      </Box>
    );
  }

  if (!analyticsData) {
    return (
      <Box textAlign="center" py={4}>
        <Typography variant="body2" color="textSecondary">
          No analytics data available for this threshold yet.
          Data will appear after the threshold has been triggered.
        </Typography>
      </Box>
    );
  }

  const formatDuration = (hours: number): string => {
    if (hours < 1) return `${Math.round(hours * 60)} minutes`;
    if (hours < 24) return `${hours.toFixed(1)} hours`;
    return `${(hours / 24).toFixed(1)} days`;
  };

  return (
    <Box>
      <Typography variant="h6" gutterBottom sx={{ fontWeight: 600, mb: 3 }}>
        Threshold Analytics
      </Typography>

      {/* Key Metrics */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={1} mb={1}>
                <SuccessIcon color="success" />
                <Typography variant="caption" color="textSecondary">
                  Total Triggers
                </Typography>
              </Box>
              <Typography variant="h4" fontWeight={600}>
                {analyticsData.triggerCount}
              </Typography>
              <Typography variant="caption" color="textSecondary">
                Since creation
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={1} mb={1}>
                <TrendIcon color="primary" />
                <Typography variant="caption" color="textSecondary">
                  Avg Claims at Trigger
                </Typography>
              </Box>
              <Typography variant="h4" fontWeight={600}>
                {analyticsData.averageClaimsAtTrigger.toFixed(0)}
              </Typography>
              <Typography variant="caption" color="textSecondary">
                Claims per file
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={1} mb={1}>
                <TimeIcon color="info" />
                <Typography variant="caption" color="textSecondary">
                  Avg Time to Trigger
                </Typography>
              </Box>
              <Typography variant="h4" fontWeight={600}>
                {formatDuration(analyticsData.averageTimeToTrigger)}
              </Typography>
              <Typography variant="caption" color="textSecondary">
                Accumulation time
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={1} mb={1}>
                <WarningIcon color="warning" />
                <Typography variant="caption" color="textSecondary">
                  Failure Rate
                </Typography>
              </Box>
              <Typography variant="h4" fontWeight={600}>
                {analyticsData.failureRate.toFixed(1)}%
              </Typography>
              <Typography variant="caption" color="textSecondary">
                Failed generations
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        {/* Trigger History Chart */}
        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Trigger History (Last 30 Days)
              </Typography>
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={analyticsData.triggerHistory}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                  <YAxis yAxisId="left" tick={{ fontSize: 12 }} />
                  <YAxis yAxisId="right" orientation="right" tick={{ fontSize: 12 }} />
                  <Tooltip />
                  <Legend />
                  <Line
                    yAxisId="left"
                    type="monotone"
                    dataKey="claimCount"
                    stroke="#8884d8"
                    name="Claim Count"
                    strokeWidth={2}
                  />
                  <Line
                    yAxisId="right"
                    type="monotone"
                    dataKey="amount"
                    stroke="#82ca9d"
                    name="Amount ($)"
                    strokeWidth={2}
                  />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Trigger Reason Breakdown */}
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Trigger Reasons
              </Typography>
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie
                    data={analyticsData.triggerReasonBreakdown}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                    label={(entry) => `${entry.reason}`}
                    outerRadius={80}
                    fill="#8884d8"
                    dataKey="count"
                  >
                    {analyticsData.triggerReasonBreakdown.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>

              <Divider sx={{ my: 2 }} />

              <Box>
                {analyticsData.triggerReasonBreakdown.map((reason, idx) => (
                  <Box
                    key={idx}
                    display="flex"
                    justifyContent="space-between"
                    alignItems="center"
                    mb={1}
                  >
                    <Box display="flex" alignItems="center" gap={1}>
                      <Box
                        sx={{
                          width: 12,
                          height: 12,
                          backgroundColor: COLORS[idx % COLORS.length],
                          borderRadius: '50%',
                        }}
                      />
                      <Typography variant="body2">{reason.reason}</Typography>
                    </Box>
                    <Chip label={reason.count} size="small" />
                  </Box>
                ))}
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Performance Insights */}
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Performance Insights
              </Typography>

              <Grid container spacing={2}>
                <Grid item xs={12} md={4}>
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Typography variant="subtitle2" gutterBottom fontWeight={600}>
                      Average Dollar per Claim
                    </Typography>
                    <Typography variant="h5">
                      $
                      {(
                        analyticsData.averageAmountAtTrigger /
                        analyticsData.averageClaimsAtTrigger
                      ).toFixed(2)}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      Helps identify high-value vs. high-volume patterns
                    </Typography>
                  </Paper>
                </Grid>

                <Grid item xs={12} md={4}>
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Typography variant="subtitle2" gutterBottom fontWeight={600}>
                      Last Triggered
                    </Typography>
                    <Typography variant="h6">
                      {analyticsData.lastTriggered
                        ? new Date(analyticsData.lastTriggered).toLocaleDateString()
                        : 'Never'}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      Most recent file generation
                    </Typography>
                  </Paper>
                </Grid>

                <Grid item xs={12} md={4}>
                  <Paper variant="outlined" sx={{ p: 2 }}>
                    <Typography variant="subtitle2" gutterBottom fontWeight={600}>
                      Efficiency Score
                    </Typography>
                    <Box display="flex" alignItems="center" gap={1}>
                      <LinearProgress
                        variant="determinate"
                        value={100 - analyticsData.failureRate}
                        sx={{ flex: 1, height: 8, borderRadius: 1 }}
                        color={
                          analyticsData.failureRate < 5
                            ? 'success'
                            : analyticsData.failureRate < 15
                            ? 'warning'
                            : 'error'
                        }
                      />
                      <Typography variant="body2" fontWeight={600}>
                        {(100 - analyticsData.failureRate).toFixed(0)}%
                      </Typography>
                    </Box>
                    <Typography variant="caption" color="textSecondary">
                      Success rate for file generation
                    </Typography>
                  </Paper>
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default ThresholdAnalytics;

// Mock data generator for testing
export const generateMockAnalytics = (thresholdId: string): ThresholdAnalyticsData => {
  const triggerCount = Math.floor(Math.random() * 50) + 10;
  const avgClaims = Math.floor(Math.random() * 80) + 20;
  const avgAmount = avgClaims * (Math.random() * 500 + 200);

  const triggerHistory = Array.from({ length: 30 }, (_, i) => {
    const date = new Date();
    date.setDate(date.getDate() - (29 - i));
    return {
      date: date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
      claimCount: Math.floor(Math.random() * 100) + 10,
      amount: Math.floor(Math.random() * 50000) + 5000,
      triggerReason: ['Claim Count', 'Amount', 'Time'][Math.floor(Math.random() * 3)],
    };
  });

  const reasonCounts: Record<string, number> = {};
  triggerHistory.forEach((h) => {
    reasonCounts[h.triggerReason] = (reasonCounts[h.triggerReason] || 0) + 1;
  });

  const triggerReasonBreakdown = Object.entries(reasonCounts).map(([reason, count]) => ({
    reason,
    count,
  }));

  return {
    thresholdId,
    triggerCount,
    averageClaimsAtTrigger: avgClaims,
    averageAmountAtTrigger: avgAmount,
    averageTimeToTrigger: Math.random() * 48 + 2,
    lastTriggered: new Date().toISOString(),
    failureRate: Math.random() * 10,
    triggerHistory,
    triggerReasonBreakdown,
  };
};
