import React, { useState } from 'react';
import {
  Box,
  Card,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Button,
  CircularProgress,
  Chip,
  IconButton,
  Tooltip,
  TextField,
  MenuItem,
  Grid,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
} from '@mui/material';
import {
  Refresh as RefreshIcon,
  Visibility as VisibilityIcon,
  Info as InfoIcon,
} from '@mui/icons-material';
import { useQuery } from '@tanstack/react-query';
import { checkPaymentService } from '../../services/checkPaymentService';
import { bucketService } from '../../services/bucketService';
import { CheckPayment, Bucket } from '../../types/models';
import { useNavigate } from 'react-router-dom';

/**
 * Check Usage Status Page
 *
 * Shows which checks have been used and their associated bucket details.
 * Displays check lifecycle status and allows viewing detailed bucket information.
 */
const CheckUsageStatus: React.FC = () => {
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCheck, setSelectedCheck] = useState<CheckPayment | null>(null);
  const [selectedBucket, setSelectedBucket] = useState<Bucket | null>(null);
  const [detailsDialogOpen, setDetailsDialogOpen] = useState(false);

  // Fetch all check payments
  const { data: allChecks, isLoading, refetch } = useQuery<CheckPayment[]>({
    queryKey: ['allCheckPayments'],
    queryFn: checkPaymentService.getAllCheckPayments,
    refetchInterval: 30000, // Refresh every 30 seconds
  });

  // Filter checks based on status and search
  const filteredChecks = React.useMemo(() => {
    if (!allChecks) return [];

    let filtered = allChecks;

    // Apply status filter
    if (statusFilter !== 'ALL') {
      filtered = filtered.filter((check) => check.status === statusFilter);
    }

    // Apply search term (check number, bucket ID)
    if (searchTerm) {
      const search = searchTerm.toLowerCase();
      filtered = filtered.filter(
        (check) =>
          check.checkNumber.toLowerCase().includes(search) ||
          check.bucketId.toLowerCase().includes(search)
      );
    }

    return filtered;
  }, [allChecks, statusFilter, searchTerm]);

  // Get status counts
  const statusCounts = React.useMemo(() => {
    if (!allChecks) return {};
    return allChecks.reduce((acc, check) => {
      acc[check.status] = (acc[check.status] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);
  }, [allChecks]);

  // Handle view details
  const handleViewDetails = async (check: CheckPayment) => {
    setSelectedCheck(check);
    try {
      const bucket = await bucketService.getBucketById(check.bucketId);
      setSelectedBucket(bucket);
      setDetailsDialogOpen(true);
    } catch (error) {
      console.error('Failed to fetch bucket details:', error);
      setSelectedBucket(null);
      setDetailsDialogOpen(true);
    }
  };

  const handleCloseDetails = () => {
    setDetailsDialogOpen(false);
    setSelectedCheck(null);
    setSelectedBucket(null);
  };

  // Navigate to bucket details
  const handleViewBucket = (bucketId: string) => {
    navigate(`/buckets/${bucketId}`);
  };

  // Get status chip color
  const getStatusColor = (
    status: string
  ): 'default' | 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success' => {
    switch (status) {
      case 'ASSIGNED':
        return 'info';
      case 'ACKNOWLEDGED':
        return 'primary';
      case 'ISSUED':
        return 'success';
      case 'VOIDED':
        return 'error';
      default:
        return 'default';
    }
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      {/* Header */}
      <Box mb={3} display="flex" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4" fontWeight={600} gutterBottom>
            Check Usage Status
          </Typography>
          <Typography variant="body1" color="textSecondary">
            View used checks and their associated bucket details
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<RefreshIcon />}
          onClick={() => refetch()}
        >
          Refresh
        </Button>
      </Box>

      {/* Summary Stats */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ p: 2, textAlign: 'center' }}>
            <Typography variant="h4" fontWeight={600} color="primary">
              {allChecks?.length || 0}
            </Typography>
            <Typography variant="body2" color="textSecondary">
              Total Checks
            </Typography>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ p: 2, textAlign: 'center' }}>
            <Typography variant="h4" fontWeight={600} color="info.main">
              {statusCounts['ASSIGNED'] || 0}
            </Typography>
            <Typography variant="body2" color="textSecondary">
              Assigned
            </Typography>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ p: 2, textAlign: 'center' }}>
            <Typography variant="h4" fontWeight={600} color="success.main">
              {statusCounts['ISSUED'] || 0}
            </Typography>
            <Typography variant="body2" color="textSecondary">
              Issued
            </Typography>
          </Card>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Card sx={{ p: 2, textAlign: 'center' }}>
            <Typography variant="h4" fontWeight={600} color="error.main">
              {statusCounts['VOIDED'] || 0}
            </Typography>
            <Typography variant="body2" color="textSecondary">
              Voided
            </Typography>
          </Card>
        </Grid>
      </Grid>

      {/* Filters */}
      <Card sx={{ p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} sm={6} md={4}>
            <TextField
              fullWidth
              label="Search"
              placeholder="Check number or Bucket ID..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              size="small"
            />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <TextField
              fullWidth
              select
              label="Status"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              size="small"
            >
              <MenuItem value="ALL">All Statuses</MenuItem>
              <MenuItem value="ASSIGNED">Assigned</MenuItem>
              <MenuItem value="ACKNOWLEDGED">Acknowledged</MenuItem>
              <MenuItem value="ISSUED">Issued</MenuItem>
              <MenuItem value="VOIDED">Voided</MenuItem>
            </TextField>
          </Grid>
          <Grid item xs={12} sm={12} md={5}>
            <Typography variant="caption" color="textSecondary">
              Showing {filteredChecks.length} of {allChecks?.length || 0} checks
            </Typography>
          </Grid>
        </Grid>
      </Card>

      {/* Checks Table */}
      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>Check Number</strong></TableCell>
                <TableCell><strong>Bucket ID</strong></TableCell>
                <TableCell align="right"><strong>Amount</strong></TableCell>
                <TableCell><strong>Check Date</strong></TableCell>
                <TableCell><strong>Status</strong></TableCell>
                <TableCell><strong>Assigned By</strong></TableCell>
                <TableCell><strong>Assigned At</strong></TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredChecks && filteredChecks.length > 0 ? (
                filteredChecks.map((check) => (
                  <TableRow key={check.id} hover>
                    <TableCell>
                      <Typography variant="body2" fontFamily="monospace" fontWeight={600}>
                        {check.checkNumber}
                      </Typography>
                      {check.bankName && (
                        <Typography variant="caption" color="textSecondary">
                          {check.bankName}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Tooltip title="Click to view bucket details">
                        <Typography
                          variant="body2"
                          fontFamily="monospace"
                          sx={{
                            cursor: 'pointer',
                            color: 'primary.main',
                            '&:hover': { textDecoration: 'underline' },
                          }}
                          onClick={() => handleViewBucket(check.bucketId)}
                        >
                          {check.bucketId.substring(0, 8)}...
                        </Typography>
                      </Tooltip>
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2" fontWeight={600}>
                        ${check.checkAmount.toLocaleString(undefined, {
                          minimumFractionDigits: 2,
                          maximumFractionDigits: 2,
                        })}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">
                        {new Date(check.checkDate).toLocaleDateString()}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={check.status}
                        color={getStatusColor(check.status)}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{check.assignedBy || 'N/A'}</Typography>
                    </TableCell>
                    <TableCell>
                      {check.assignedAt ? (
                        <>
                          <Typography variant="body2">
                            {new Date(check.assignedAt).toLocaleDateString()}
                          </Typography>
                          <Typography variant="caption" color="textSecondary">
                            {new Date(check.assignedAt).toLocaleTimeString()}
                          </Typography>
                        </>
                      ) : (
                        <Typography variant="body2" color="textSecondary">
                          N/A
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title="View Details">
                        <IconButton
                          size="small"
                          onClick={() => handleViewDetails(check)}
                          color="primary"
                        >
                          <InfoIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={8} align="center">
                    <Box py={4}>
                      <InfoIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
                      <Typography variant="body1" color="textSecondary">
                        No checks found
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        {statusFilter !== 'ALL'
                          ? 'Try adjusting your filters'
                          : 'No checks have been assigned yet'}
                      </Typography>
                    </Box>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Details Dialog */}
      <Dialog open={detailsDialogOpen} onClose={handleCloseDetails} maxWidth="md" fullWidth>
        <DialogTitle>Check Payment Details</DialogTitle>
        <DialogContent>
          {selectedCheck && (
            <Box>
              {/* Check Information */}
              <Typography variant="h6" gutterBottom sx={{ mt: 1 }}>
                Check Information
              </Typography>
              <Grid container spacing={2} mb={3}>
                <Grid item xs={12} sm={6}>
                  <Typography variant="caption" color="textSecondary">
                    Check Number
                  </Typography>
                  <Typography variant="body1" fontFamily="monospace" fontWeight={600}>
                    {selectedCheck.checkNumber}
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="caption" color="textSecondary">
                    Amount
                  </Typography>
                  <Typography variant="body1" fontWeight={600}>
                    ${selectedCheck.checkAmount.toLocaleString(undefined, {
                      minimumFractionDigits: 2,
                      maximumFractionDigits: 2,
                    })}
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="caption" color="textSecondary">
                    Check Date
                  </Typography>
                  <Typography variant="body1">
                    {new Date(selectedCheck.checkDate).toLocaleDateString()}
                  </Typography>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Typography variant="caption" color="textSecondary">
                    Status
                  </Typography>
                  <Box mt={0.5}>
                    <Chip
                      label={selectedCheck.status}
                      color={getStatusColor(selectedCheck.status)}
                      size="small"
                    />
                  </Box>
                </Grid>
                {selectedCheck.bankName && (
                  <Grid item xs={12} sm={6}>
                    <Typography variant="caption" color="textSecondary">
                      Bank Name
                    </Typography>
                    <Typography variant="body1">{selectedCheck.bankName}</Typography>
                  </Grid>
                )}
                {selectedCheck.routingNumber && (
                  <Grid item xs={12} sm={6}>
                    <Typography variant="caption" color="textSecondary">
                      Routing Number
                    </Typography>
                    <Typography variant="body1" fontFamily="monospace">
                      {selectedCheck.routingNumber}
                    </Typography>
                  </Grid>
                )}
                {selectedCheck.accountNumberLast4 && (
                  <Grid item xs={12} sm={6}>
                    <Typography variant="caption" color="textSecondary">
                      Account Last 4
                    </Typography>
                    <Typography variant="body1" fontFamily="monospace">
                      ****{selectedCheck.accountNumberLast4}
                    </Typography>
                  </Grid>
                )}
              </Grid>

              {/* Bucket Information */}
              {selectedBucket ? (
                <>
                  <Typography variant="h6" gutterBottom>
                    Associated Bucket Details
                  </Typography>
                  <Alert severity="info" sx={{ mb: 2 }}>
                    <Typography variant="body2">
                      <strong>Bucket ID:</strong> {selectedBucket.bucketId}
                    </Typography>
                  </Alert>
                  <Grid container spacing={2}>
                    <Grid item xs={12} sm={6}>
                      <Typography variant="caption" color="textSecondary">
                        Payer
                      </Typography>
                      <Typography variant="body1">{selectedBucket.payerName}</Typography>
                      <Typography variant="caption" color="textSecondary">
                        {selectedBucket.payerId}
                      </Typography>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <Typography variant="caption" color="textSecondary">
                        Payee
                      </Typography>
                      <Typography variant="body1">{selectedBucket.payeeName}</Typography>
                      <Typography variant="caption" color="textSecondary">
                        {selectedBucket.payeeId}
                      </Typography>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <Typography variant="caption" color="textSecondary">
                        Claim Count
                      </Typography>
                      <Typography variant="body1">{selectedBucket.claimCount}</Typography>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <Typography variant="caption" color="textSecondary">
                        Total Amount
                      </Typography>
                      <Typography variant="body1">
                        ${selectedBucket.totalAmount.toLocaleString(undefined, {
                          minimumFractionDigits: 2,
                          maximumFractionDigits: 2,
                        })}
                      </Typography>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <Typography variant="caption" color="textSecondary">
                        Bucket Status
                      </Typography>
                      <Typography variant="body1">{selectedBucket.status}</Typography>
                    </Grid>
                  </Grid>
                </>
              ) : (
                <Alert severity="warning">
                  Unable to load bucket details. Bucket may have been deleted.
                </Alert>
              )}

              {/* Lifecycle Information */}
              <Typography variant="h6" gutterBottom sx={{ mt: 3 }}>
                Lifecycle History
              </Typography>
              <Grid container spacing={2}>
                {selectedCheck.assignedBy && (
                  <Grid item xs={12}>
                    <Typography variant="caption" color="textSecondary">
                      Assigned
                    </Typography>
                    <Typography variant="body2">
                      By {selectedCheck.assignedBy} on{' '}
                      {new Date(selectedCheck.assignedAt!).toLocaleString()}
                    </Typography>
                  </Grid>
                )}
                {selectedCheck.acknowledgedBy && (
                  <Grid item xs={12}>
                    <Typography variant="caption" color="textSecondary">
                      Acknowledged
                    </Typography>
                    <Typography variant="body2">
                      By {selectedCheck.acknowledgedBy} on{' '}
                      {new Date(selectedCheck.acknowledgedAt!).toLocaleString()}
                    </Typography>
                  </Grid>
                )}
                {selectedCheck.issuedBy && (
                  <Grid item xs={12}>
                    <Typography variant="caption" color="textSecondary">
                      Issued
                    </Typography>
                    <Typography variant="body2">
                      By {selectedCheck.issuedBy} on{' '}
                      {new Date(selectedCheck.issuedAt!).toLocaleString()}
                    </Typography>
                  </Grid>
                )}
                {selectedCheck.voidedBy && (
                  <Grid item xs={12}>
                    <Alert severity="error">
                      <Typography variant="caption" color="textSecondary">
                        Voided
                      </Typography>
                      <Typography variant="body2">
                        By {selectedCheck.voidedBy} on{' '}
                        {new Date(selectedCheck.voidedAt!).toLocaleString()}
                      </Typography>
                      {selectedCheck.voidReason && (
                        <Typography variant="body2" sx={{ mt: 1 }}>
                          <strong>Reason:</strong> {selectedCheck.voidReason}
                        </Typography>
                      )}
                    </Alert>
                  </Grid>
                )}
              </Grid>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDetails}>Close</Button>
          {selectedCheck && (
            <Button
              variant="contained"
              color="primary"
              startIcon={<VisibilityIcon />}
              onClick={() => {
                handleCloseDetails();
                handleViewBucket(selectedCheck.bucketId);
              }}
            >
              View Bucket
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default CheckUsageStatus;
