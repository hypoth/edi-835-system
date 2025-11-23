import React, { useState, useMemo, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Button,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Checkbox,
  IconButton,
  Tooltip,
  Alert,
  Grid,
  TableSortLabel,
  Divider,
} from '@mui/material';
import {
  CheckCircle as CheckCircleIcon,
  Cancel as CancelIcon,
  Visibility as VisibilityIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { approvalService } from '../../services/approvalService';
import { checkPaymentService } from '../../services/checkPaymentService';
import { checkPaymentWorkflowService } from '../../services/checkPaymentWorkflowService';
import { Bucket, CheckPaymentWorkflowConfig } from '../../types/models';
import { toast } from 'react-toastify';

type SortField = 'bucketId' | 'payerName' | 'payeeName' | 'claimCount' | 'totalAmount' | 'awaitingApprovalSince';
type SortOrder = 'asc' | 'desc';

const ApprovalQueue: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [selectedBuckets, setSelectedBuckets] = useState<string[]>([]);
  const [approvalDialogOpen, setApprovalDialogOpen] = useState(false);
  const [rejectionDialogOpen, setRejectionDialogOpen] = useState(false);
  const [currentBucketId, setCurrentBucketId] = useState<string | null>(null);
  const [actionBy, setActionBy] = useState('');
  const [comments, setComments] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  const [sortField, setSortField] = useState<SortField>('awaitingApprovalSince');
  const [sortOrder, setSortOrder] = useState<SortOrder>('asc');

  // Check payment fields
  const [workflowConfig, setWorkflowConfig] = useState<CheckPaymentWorkflowConfig | null>(null);
  const [loadingWorkflowConfig, setLoadingWorkflowConfig] = useState(false);
  const [checkNumber, setCheckNumber] = useState('');
  const [checkAmount, setCheckAmount] = useState('');
  const [checkDate, setCheckDate] = useState(new Date().toISOString().split('T')[0]);
  const [bankName, setBankName] = useState('');
  const [routingNumber, setRoutingNumber] = useState('');
  const [accountLast4, setAccountLast4] = useState('');

  // Fetch pending approvals
  const { data: pendingBuckets, isLoading, refetch } = useQuery<Bucket[]>({
    queryKey: ['pendingApprovals'],
    queryFn: approvalService.getPendingApprovals,
    refetchInterval: 15000, // Refresh every 15 seconds
  });

  // Sort buckets based on current sort field and order
  const sortedBuckets = useMemo(() => {
    if (!pendingBuckets) return [];

    return [...pendingBuckets].sort((a, b) => {
      let aValue: any;
      let bValue: any;

      switch (sortField) {
        case 'bucketId':
          aValue = a.bucketId;
          bValue = b.bucketId;
          break;
        case 'payerName':
          aValue = a.payerName?.toLowerCase() || '';
          bValue = b.payerName?.toLowerCase() || '';
          break;
        case 'payeeName':
          aValue = a.payeeName?.toLowerCase() || '';
          bValue = b.payeeName?.toLowerCase() || '';
          break;
        case 'claimCount':
          aValue = a.claimCount;
          bValue = b.claimCount;
          break;
        case 'totalAmount':
          aValue = a.totalAmount;
          bValue = b.totalAmount;
          break;
        case 'awaitingApprovalSince':
          aValue = a.awaitingApprovalSince ? new Date(a.awaitingApprovalSince).getTime() : 0;
          bValue = b.awaitingApprovalSince ? new Date(b.awaitingApprovalSince).getTime() : 0;
          break;
        default:
          return 0;
      }

      if (aValue < bValue) return sortOrder === 'asc' ? -1 : 1;
      if (aValue > bValue) return sortOrder === 'asc' ? 1 : -1;
      return 0;
    });
  }, [pendingBuckets, sortField, sortOrder]);

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      // Toggle sort order if same field
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      // Set new field with ascending order
      setSortField(field);
      setSortOrder('asc');
    }
  };

  // Approve mutation
  const approveMutation = useMutation({
    mutationFn: (data: { bucketId: string; actionBy: string; comments?: string }) =>
      approvalService.approveBucket(data.bucketId, {
        actionBy: data.actionBy,
        comments: data.comments,
      }),
    onSuccess: () => {
      toast.success('Bucket approved successfully');
      queryClient.invalidateQueries({ queryKey: ['pendingApprovals'] });
      handleCloseDialogs();
    },
    onError: () => {
      toast.error('Failed to approve bucket');
    },
  });

  // Reject mutation
  const rejectMutation = useMutation({
    mutationFn: (data: { bucketId: string; actionBy: string; comments?: string; rejectionReason?: string }) =>
      approvalService.rejectBucket(data.bucketId, {
        actionBy: data.actionBy,
        comments: data.comments,
        rejectionReason: data.rejectionReason,
      }),
    onSuccess: () => {
      toast.success('Bucket rejected successfully');
      queryClient.invalidateQueries({ queryKey: ['pendingApprovals'] });
      handleCloseDialogs();
    },
    onError: () => {
      toast.error('Failed to reject bucket');
    },
  });

  // Bulk approve mutation
  const bulkApproveMutation = useMutation({
    mutationFn: (data: { bucketIds: string[]; actionBy: string; comments?: string }) =>
      approvalService.bulkApproveBuckets(data.bucketIds, {
        actionBy: data.actionBy,
        comments: data.comments,
      }),
    onSuccess: () => {
      toast.success(`${selectedBuckets.length} bucket(s) approved successfully`);
      queryClient.invalidateQueries({ queryKey: ['pendingApprovals'] });
      setSelectedBuckets([]);
      handleCloseDialogs();
    },
    onError: () => {
      toast.error('Failed to approve selected buckets');
    },
  });

  const handleApproveClick = async (bucketId: string) => {
    setCurrentBucketId(bucketId);
    setApprovalDialogOpen(true);

    // Find the bucket to get its bucketing rule ID and then fetch workflow config
    const bucket = sortedBuckets.find(b => b.bucketId === bucketId);
    if (bucket?.bucketingRuleId) {
      await fetchWorkflowConfig(bucket.bucketingRuleId);
    }
  };

  // Fetch workflow configuration for the bucket's threshold
  const fetchWorkflowConfig = async (bucketingRuleId: string) => {
    setLoadingWorkflowConfig(true);
    try {
      // First, get thresholds for this bucketing rule
      const response = await fetch(
        `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'}/config/thresholds/rule/${bucketingRuleId}`
      );

      if (!response.ok) {
        setWorkflowConfig(null);
        return;
      }

      const thresholds = await response.json();
      if (thresholds && thresholds.length > 0) {
        // Get workflow config for the first threshold (or active one)
        const threshold = thresholds.find((t: any) => t.isActive) || thresholds[0];
        if (threshold?.id) {
          const config = await checkPaymentWorkflowService.getWorkflowConfigByThreshold(threshold.id);
          setWorkflowConfig(config);

          // Auto-populate check amount from bucket if available
          const bucket = sortedBuckets.find(b => b.bucketingRuleId === bucketingRuleId);
          if (bucket && config?.workflowMode === 'COMBINED') {
            setCheckAmount(bucket.totalAmount.toFixed(2));
          }
        }
      }
    } catch (error) {
      console.error('Error fetching workflow config:', error);
      setWorkflowConfig(null);
    } finally {
      setLoadingWorkflowConfig(false);
    }
  };

  const handleRejectClick = (bucketId: string) => {
    setCurrentBucketId(bucketId);
    setRejectionDialogOpen(true);
  };

  const handleBulkApprove = () => {
    if (selectedBuckets.length === 0) {
      toast.warning('Please select at least one bucket');
      return;
    }
    setCurrentBucketId(null);
    setApprovalDialogOpen(true);
  };

  const handleApprovalConfirm = async () => {
    if (!actionBy.trim()) {
      toast.warning('Please enter your name');
      return;
    }

    // Handle COMBINED workflow with check payment
    if (workflowConfig?.workflowMode === 'COMBINED' && currentBucketId) {
      // Check if AUTO assignment mode
      if (workflowConfig.assignmentMode === 'AUTO') {
        // AUTO mode - automatically assign from reservations
        try {
          // Step 1: Approve the bucket
          await approvalService.approveBucket(currentBucketId, {
            actionBy: actionBy.trim(),
            comments: comments.trim() || undefined,
          });

          // Step 2: Auto-assign check from reservations
          await checkPaymentService.assignCheckAutomatically(
            currentBucketId,
            actionBy.trim()
          );

          toast.success('Bucket approved and check auto-assigned successfully');
          queryClient.invalidateQueries({ queryKey: ['pendingApprovals'] });
          handleCloseDialogs();
        } catch (error: any) {
          toast.error(error.response?.data?.error || 'Failed to approve bucket with auto check assignment');
        }
      } else {
        // MANUAL assignment mode - validate manual fields
        if (!checkNumber.trim()) {
          toast.warning('Please enter check number');
          return;
        }
        if (!checkAmount || parseFloat(checkAmount) <= 0) {
          toast.warning('Please enter a valid check amount');
          return;
        }
        if (!checkDate) {
          toast.warning('Please enter check date');
          return;
        }

        // Handle combined approval + manual check payment
        try {
          // Step 1: Approve the bucket
          await approvalService.approveBucket(currentBucketId, {
            actionBy: actionBy.trim(),
            comments: comments.trim() || undefined,
          });

          // Step 2: Assign check payment manually
          await checkPaymentService.assignCheckManually(currentBucketId, {
            checkNumber: checkNumber.trim(),
            checkDate: checkDate,
            bankName: bankName.trim() || undefined,
            routingNumber: routingNumber.trim() || undefined,
            accountLast4: accountLast4.trim() || undefined,
            assignedBy: actionBy.trim(),
          });

          toast.success('Bucket approved and check assigned successfully');
          queryClient.invalidateQueries({ queryKey: ['pendingApprovals'] });
          handleCloseDialogs();
        } catch (error: any) {
          toast.error(error.response?.data?.error || 'Failed to approve bucket with check payment');
        }
      }
    } else if (currentBucketId) {
      // Single approval (without check payment)
      approveMutation.mutate({
        bucketId: currentBucketId,
        actionBy: actionBy.trim(),
        comments: comments.trim() || undefined,
      });
    } else {
      // Bulk approval
      bulkApproveMutation.mutate({
        bucketIds: selectedBuckets,
        actionBy: actionBy.trim(),
        comments: comments.trim() || undefined,
      });
    }
  };

  const handleRejectionConfirm = () => {
    if (!actionBy.trim()) {
      toast.warning('Please enter your name');
      return;
    }

    if (!rejectionReason.trim()) {
      toast.warning('Please enter a rejection reason');
      return;
    }

    if (currentBucketId) {
      rejectMutation.mutate({
        bucketId: currentBucketId,
        actionBy: actionBy.trim(),
        comments: comments.trim() || undefined,
        rejectionReason: rejectionReason.trim(),
      });
    }
  };

  const handleCloseDialogs = () => {
    setApprovalDialogOpen(false);
    setRejectionDialogOpen(false);
    setCurrentBucketId(null);
    setActionBy('');
    setComments('');
    setRejectionReason('');

    // Reset check payment fields
    setWorkflowConfig(null);
    setCheckNumber('');
    setCheckAmount('');
    setCheckDate(new Date().toISOString().split('T')[0]);
    setBankName('');
    setRoutingNumber('');
    setAccountLast4('');
  };

  const handleSelectAll = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (event.target.checked) {
      setSelectedBuckets(sortedBuckets.map((b) => b.bucketId));
    } else {
      setSelectedBuckets([]);
    }
  };

  const handleSelectBucket = (bucketId: string) => {
    setSelectedBuckets((prev) =>
      prev.includes(bucketId)
        ? prev.filter((id) => id !== bucketId)
        : [...prev, bucketId]
    );
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
            Approval Queue
          </Typography>
          <Typography variant="body1" color="textSecondary">
            Review and approve/reject buckets awaiting approval
          </Typography>
        </Box>
        <Box>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={() => refetch()}
            sx={{ mr: 1 }}
          >
            Refresh
          </Button>
          <Button
            variant="contained"
            color="success"
            startIcon={<CheckCircleIcon />}
            onClick={handleBulkApprove}
            disabled={selectedBuckets.length === 0}
          >
            Approve Selected ({selectedBuckets.length})
          </Button>
        </Box>
      </Box>

      {/* Summary */}
      {sortedBuckets.length > 0 && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          <Typography variant="body2">
            <strong>{sortedBuckets.length}</strong> bucket(s) awaiting approval •
            Total Claims: <strong>{sortedBuckets.reduce((sum, b) => sum + b.claimCount, 0)}</strong> •
            Total Amount: <strong>${sortedBuckets.reduce((sum, b) => sum + b.totalAmount, 0).toLocaleString()}</strong>
          </Typography>
        </Alert>
      )}

      {/* Approvals Table */}
      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell padding="checkbox">
                  <Checkbox
                    checked={
                      sortedBuckets.length > 0 &&
                      selectedBuckets.length === sortedBuckets.length
                    }
                    indeterminate={
                      selectedBuckets.length > 0 &&
                      selectedBuckets.length < sortedBuckets.length
                    }
                    onChange={handleSelectAll}
                  />
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'bucketId'}
                    direction={sortField === 'bucketId' ? sortOrder : 'asc'}
                    onClick={() => handleSort('bucketId')}
                  >
                    <strong>Bucket ID</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'payerName'}
                    direction={sortField === 'payerName' ? sortOrder : 'asc'}
                    onClick={() => handleSort('payerName')}
                  >
                    <strong>Payer</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'payeeName'}
                    direction={sortField === 'payeeName' ? sortOrder : 'asc'}
                    onClick={() => handleSort('payeeName')}
                  >
                    <strong>Payee</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={sortField === 'claimCount'}
                    direction={sortField === 'claimCount' ? sortOrder : 'asc'}
                    onClick={() => handleSort('claimCount')}
                  >
                    <strong>Claims</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={sortField === 'totalAmount'}
                    direction={sortField === 'totalAmount' ? sortOrder : 'asc'}
                    onClick={() => handleSort('totalAmount')}
                  >
                    <strong>Amount</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'awaitingApprovalSince'}
                    direction={sortField === 'awaitingApprovalSince' ? sortOrder : 'asc'}
                    onClick={() => handleSort('awaitingApprovalSince')}
                  >
                    <strong>Waiting Since</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortedBuckets.length > 0 ? (
                sortedBuckets.map((bucket) => (
                  <TableRow key={bucket.bucketId} hover>
                    <TableCell padding="checkbox">
                      <Checkbox
                        checked={selectedBuckets.includes(bucket.bucketId)}
                        onChange={() => handleSelectBucket(bucket.bucketId)}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" fontFamily="monospace">
                        {bucket.bucketId.substring(0, 8)}...
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{bucket.payerName}</Typography>
                      <Typography variant="caption" color="textSecondary">
                        {bucket.payerId}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{bucket.payeeName}</Typography>
                      <Typography variant="caption" color="textSecondary">
                        {bucket.payeeId}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2">{bucket.claimCount}</Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2">
                        ${bucket.totalAmount.toLocaleString()}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {bucket.awaitingApprovalSince ? (
                        <>
                          <Typography variant="body2">
                            {new Date(bucket.awaitingApprovalSince).toLocaleDateString()}
                          </Typography>
                          <Typography variant="caption" color="textSecondary">
                            {new Date(bucket.awaitingApprovalSince).toLocaleTimeString()}
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
                          onClick={() => navigate(`/buckets/${bucket.bucketId}`)}
                          color="primary"
                        >
                          <VisibilityIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Approve">
                        <IconButton
                          size="small"
                          onClick={() => handleApproveClick(bucket.bucketId)}
                          color="success"
                        >
                          <CheckCircleIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Reject">
                        <IconButton
                          size="small"
                          onClick={() => handleRejectClick(bucket.bucketId)}
                          color="error"
                        >
                          <CancelIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={8} align="center">
                    <Typography variant="body2" color="textSecondary" py={4}>
                      No buckets pending approval
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Approval Dialog */}
      <Dialog open={approvalDialogOpen} onClose={handleCloseDialogs} maxWidth="md" fullWidth>
        <DialogTitle>
          {currentBucketId ? 'Approve Bucket' : `Approve ${selectedBuckets.length} Bucket(s)`}
        </DialogTitle>
        <DialogContent>
          {loadingWorkflowConfig ? (
            <Box display="flex" justifyContent="center" py={3}>
              <CircularProgress size={30} />
            </Box>
          ) : (
            <>
              <Typography variant="body2" color="textSecondary" gutterBottom>
                {currentBucketId
                  ? 'This will approve the bucket and trigger EDI file generation.'
                  : `This will approve ${selectedBuckets.length} bucket(s) and trigger EDI file generation for each.`}
              </Typography>

              {/* Show workflow info if COMBINED mode */}
              {workflowConfig?.workflowMode === 'COMBINED' && currentBucketId && (
                <Alert severity="info" sx={{ mt: 2, mb: 2 }}>
                  <Typography variant="body2">
                    <strong>Check Payment Required:</strong> This workflow requires check payment
                    assignment during approval.
                    {workflowConfig.assignmentMode === 'AUTO' && (
                      <>
                        <br />
                        <strong>Auto-Assignment Mode:</strong> A check will be automatically assigned
                        from available reservations.
                      </>
                    )}
                  </Typography>
                </Alert>
              )}

              <TextField
                fullWidth
                label="Your Name (Required)"
                value={actionBy}
                onChange={(e) => setActionBy(e.target.value)}
                sx={{ mt: 2 }}
                required
              />
              <TextField
                fullWidth
                multiline
                rows={3}
                label="Comments (Optional)"
                value={comments}
                onChange={(e) => setComments(e.target.value)}
                sx={{ mt: 2 }}
              />

              {/* Check Payment Fields - Show only for COMBINED workflow with MANUAL assignment */}
              {workflowConfig?.workflowMode === 'COMBINED' &&
               workflowConfig?.assignmentMode === 'MANUAL' &&
               currentBucketId && (
                <>
                  <Divider sx={{ my: 3 }}>
                    <Typography variant="subtitle2" color="textSecondary">
                      Check Payment Details
                    </Typography>
                  </Divider>

                  <Grid container spacing={2}>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        fullWidth
                        label="Check Number (Required)"
                        value={checkNumber}
                        onChange={(e) => setCheckNumber(e.target.value)}
                        required
                        placeholder="CHK000123"
                        helperText="Enter the check number"
                      />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        fullWidth
                        label="Check Amount (Required)"
                        type="number"
                        value={checkAmount}
                        onChange={(e) => setCheckAmount(e.target.value)}
                        required
                        inputProps={{ step: '0.01', min: '0' }}
                        helperText="Total amount on the check"
                      />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        fullWidth
                        label="Check Date (Required)"
                        type="date"
                        value={checkDate}
                        onChange={(e) => setCheckDate(e.target.value)}
                        required
                        InputLabelProps={{ shrink: true }}
                      />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        fullWidth
                        label="Bank Name (Optional)"
                        value={bankName}
                        onChange={(e) => setBankName(e.target.value)}
                        placeholder="First National Bank"
                      />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        fullWidth
                        label="Routing Number (Optional)"
                        value={routingNumber}
                        onChange={(e) => setRoutingNumber(e.target.value)}
                        placeholder="021000021"
                        inputProps={{ maxLength: 9 }}
                      />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        fullWidth
                        label="Account Last 4 Digits (Optional)"
                        value={accountLast4}
                        onChange={(e) => setAccountLast4(e.target.value)}
                        placeholder="1234"
                        inputProps={{ maxLength: 4 }}
                      />
                    </Grid>
                  </Grid>
                </>
              )}
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialogs}>Cancel</Button>
          <Button
            onClick={handleApprovalConfirm}
            variant="contained"
            color="success"
            disabled={approveMutation.isPending || bulkApproveMutation.isPending || loadingWorkflowConfig}
          >
            {approveMutation.isPending || bulkApproveMutation.isPending ? (
              <CircularProgress size={24} />
            ) : (
              workflowConfig?.workflowMode === 'COMBINED' ? 'Approve & Assign Check' : 'Approve'
            )}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Rejection Dialog */}
      <Dialog open={rejectionDialogOpen} onClose={handleCloseDialogs} maxWidth="sm" fullWidth>
        <DialogTitle>Reject Bucket</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="textSecondary" gutterBottom>
            This will reject the bucket and move it back to ACCUMULATING status.
          </Typography>
          <TextField
            fullWidth
            label="Your Name (Required)"
            value={actionBy}
            onChange={(e) => setActionBy(e.target.value)}
            sx={{ mt: 2 }}
            required
          />
          <TextField
            fullWidth
            multiline
            rows={2}
            label="Rejection Reason (Required)"
            value={rejectionReason}
            onChange={(e) => setRejectionReason(e.target.value)}
            sx={{ mt: 2 }}
            required
            error={!rejectionReason.trim() && rejectionDialogOpen}
            helperText={!rejectionReason.trim() && rejectionDialogOpen ? 'Rejection reason is required' : ''}
          />
          <TextField
            fullWidth
            multiline
            rows={3}
            label="Additional Comments (Optional)"
            value={comments}
            onChange={(e) => setComments(e.target.value)}
            sx={{ mt: 2 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialogs}>Cancel</Button>
          <Button
            onClick={handleRejectionConfirm}
            variant="contained"
            color="error"
            disabled={rejectMutation.isPending}
          >
            {rejectMutation.isPending ? <CircularProgress size={24} /> : 'Reject'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ApprovalQueue;
