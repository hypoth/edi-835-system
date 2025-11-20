import React, { useState, useMemo } from 'react';
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
import { Bucket } from '../../types/models';
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

  const handleApproveClick = (bucketId: string) => {
    setCurrentBucketId(bucketId);
    setApprovalDialogOpen(true);
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

  const handleApprovalConfirm = () => {
    if (!actionBy.trim()) {
      toast.warning('Please enter your name');
      return;
    }

    if (currentBucketId) {
      // Single approval
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
      <Dialog open={approvalDialogOpen} onClose={handleCloseDialogs} maxWidth="sm" fullWidth>
        <DialogTitle>
          {currentBucketId ? 'Approve Bucket' : `Approve ${selectedBuckets.length} Bucket(s)`}
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="textSecondary" gutterBottom>
            {currentBucketId
              ? 'This will approve the bucket and trigger EDI file generation.'
              : `This will approve ${selectedBuckets.length} bucket(s) and trigger EDI file generation for each.`}
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
            rows={3}
            label="Comments (Optional)"
            value={comments}
            onChange={(e) => setComments(e.target.value)}
            sx={{ mt: 2 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialogs}>Cancel</Button>
          <Button
            onClick={handleApprovalConfirm}
            variant="contained"
            color="success"
            disabled={approveMutation.isPending || bulkApproveMutation.isPending}
          >
            {approveMutation.isPending || bulkApproveMutation.isPending ? (
              <CircularProgress size={24} />
            ) : (
              'Approve'
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
