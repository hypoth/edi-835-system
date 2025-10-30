import React, { useState } from 'react';
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

  // Fetch pending approvals
  const { data: pendingBuckets, isLoading, refetch } = useQuery<Bucket[]>({
    queryKey: ['pendingApprovals'],
    queryFn: approvalService.getPendingApprovals,
    refetchInterval: 15000, // Refresh every 15 seconds
  });

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
      setSelectedBuckets(pendingBuckets?.map((b) => b.bucketId) || []);
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
      {pendingBuckets && pendingBuckets.length > 0 && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          <Typography variant="body2">
            <strong>{pendingBuckets.length}</strong> bucket(s) awaiting approval •
            Total Claims: <strong>{pendingBuckets.reduce((sum, b) => sum + b.claimCount, 0)}</strong> •
            Total Amount: <strong>${pendingBuckets.reduce((sum, b) => sum + b.totalAmount, 0).toLocaleString()}</strong>
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
                      pendingBuckets &&
                      pendingBuckets.length > 0 &&
                      selectedBuckets.length === pendingBuckets.length
                    }
                    indeterminate={
                      selectedBuckets.length > 0 &&
                      pendingBuckets &&
                      selectedBuckets.length < pendingBuckets.length
                    }
                    onChange={handleSelectAll}
                  />
                </TableCell>
                <TableCell><strong>Bucket ID</strong></TableCell>
                <TableCell><strong>Payer</strong></TableCell>
                <TableCell><strong>Payee</strong></TableCell>
                <TableCell align="right"><strong>Claims</strong></TableCell>
                <TableCell align="right"><strong>Amount</strong></TableCell>
                <TableCell><strong>Waiting Since</strong></TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {pendingBuckets && pendingBuckets.length > 0 ? (
                pendingBuckets.map((bucket) => (
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
