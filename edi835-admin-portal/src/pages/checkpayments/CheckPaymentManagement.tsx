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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Alert,
  Chip,
  IconButton,
  Tooltip,
  Grid,
} from '@mui/material';
import {
  Assignment as AssignIcon,
  Refresh as RefreshIcon,
  CheckCircle as CheckCircleIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { checkPaymentService } from '../../services/checkPaymentService';
import { approvalService } from '../../services/approvalService';
import { Bucket } from '../../types/models';
import { toast } from 'react-toastify';

/**
 * Check Payment Management Page
 *
 * For SEPARATE workflow mode:
 * - Lists buckets that have been approved but are awaiting check payment
 * - Allows manual check assignment to each bucket
 * - After check assignment, EDI generation is triggered
 */
const CheckPaymentManagement: React.FC = () => {
  const queryClient = useQueryClient();
  const [assignDialogOpen, setAssignDialogOpen] = useState(false);
  const [selectedBucket, setSelectedBucket] = useState<Bucket | null>(null);

  // Check payment form fields
  const [checkNumber, setCheckNumber] = useState('');
  const [checkAmount, setCheckAmount] = useState('');
  const [checkDate, setCheckDate] = useState(new Date().toISOString().split('T')[0]);
  const [bankName, setBankName] = useState('');
  const [routingNumber, setRoutingNumber] = useState('');
  const [accountLast4, setAccountLast4] = useState('');
  const [assignedBy, setAssignedBy] = useState('');

  // Fetch buckets awaiting check payment
  // These are buckets that are approved but payment_status = 'PENDING'
  const { data: pendingBuckets, isLoading, refetch } = useQuery<Bucket[]>({
    queryKey: ['bucketsAwaitingPayment'],
    queryFn: async () => {
      // Get all pending approvals (which includes those awaiting check payment)
      const buckets = await approvalService.getPendingApprovals();
      // Filter to only those with approved status and payment required
      return buckets.filter((b: any) => b.approvedBy && b.paymentRequired && b.paymentStatus === 'PENDING');
    },
    refetchInterval: 15000, // Refresh every 15 seconds
  });

  // Assign check mutation
  const assignCheckMutation = useMutation({
    mutationFn: (data: { bucketId: string; request: any }) =>
      checkPaymentService.assignCheckManually(data.bucketId, data.request),
    onSuccess: () => {
      toast.success('Check assigned successfully! EDI generation triggered.');
      queryClient.invalidateQueries({ queryKey: ['bucketsAwaitingPayment'] });
      handleCloseDialog();
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to assign check');
    },
  });

  const handleAssignClick = (bucket: Bucket) => {
    setSelectedBucket(bucket);
    setCheckAmount(bucket.totalAmount.toFixed(2));
    setAssignDialogOpen(true);
  };

  const handleAssignConfirm = () => {
    if (!assignedBy.trim()) {
      toast.warning('Please enter your name');
      return;
    }
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

    if (selectedBucket) {
      assignCheckMutation.mutate({
        bucketId: selectedBucket.bucketId,
        request: {
          checkNumber: checkNumber.trim(),
          checkAmount: parseFloat(checkAmount),
          checkDate: checkDate,
          bankName: bankName.trim() || undefined,
          routingNumber: routingNumber.trim() || undefined,
          accountLast4: accountLast4.trim() || undefined,
          assignedBy: assignedBy.trim(),
        },
      });
    }
  };

  const handleCloseDialog = () => {
    setAssignDialogOpen(false);
    setSelectedBucket(null);
    setCheckNumber('');
    setCheckAmount('');
    setCheckDate(new Date().toISOString().split('T')[0]);
    setBankName('');
    setRoutingNumber('');
    setAccountLast4('');
    setAssignedBy('');
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
            Check Payment Management
          </Typography>
          <Typography variant="body1" color="textSecondary">
            Assign checks to approved buckets (SEPARATE workflow mode)
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

      {/* Summary Alert */}
      {pendingBuckets && pendingBuckets.length > 0 && (
        <Alert severity="info" sx={{ mb: 3 }}>
          <Typography variant="body2">
            <strong>{pendingBuckets.length}</strong> bucket(s) awaiting check payment assignment •
            Total Claims: <strong>{pendingBuckets.reduce((sum, b) => sum + b.claimCount, 0)}</strong> •
            Total Amount: <strong>${pendingBuckets.reduce((sum, b) => sum + b.totalAmount, 0).toLocaleString()}</strong>
          </Typography>
        </Alert>
      )}

      {/* Buckets Table */}
      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>Bucket ID</strong></TableCell>
                <TableCell><strong>Payer</strong></TableCell>
                <TableCell><strong>Payee</strong></TableCell>
                <TableCell align="right"><strong>Claims</strong></TableCell>
                <TableCell align="right"><strong>Amount</strong></TableCell>
                <TableCell><strong>Approved By</strong></TableCell>
                <TableCell><strong>Approved At</strong></TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {pendingBuckets && pendingBuckets.length > 0 ? (
                pendingBuckets.map((bucket) => (
                  <TableRow key={bucket.bucketId} hover>
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
                      <Typography variant="body2">{(bucket as any).approvedBy || 'N/A'}</Typography>
                    </TableCell>
                    <TableCell>
                      {(bucket as any).approvedAt ? (
                        <>
                          <Typography variant="body2">
                            {new Date((bucket as any).approvedAt).toLocaleDateString()}
                          </Typography>
                          <Typography variant="caption" color="textSecondary">
                            {new Date((bucket as any).approvedAt).toLocaleTimeString()}
                          </Typography>
                        </>
                      ) : (
                        <Typography variant="body2" color="textSecondary">
                          N/A
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title="Assign Check">
                        <IconButton
                          size="small"
                          onClick={() => handleAssignClick(bucket)}
                          color="primary"
                        >
                          <AssignIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={8} align="center">
                    <Box py={4}>
                      <CheckCircleIcon sx={{ fontSize: 48, color: 'success.main', mb: 2 }} />
                      <Typography variant="body1" color="textSecondary">
                        No buckets awaiting check payment assignment
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        All approved buckets have checks assigned
                      </Typography>
                    </Box>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Assign Check Dialog */}
      <Dialog open={assignDialogOpen} onClose={handleCloseDialog} maxWidth="md" fullWidth>
        <DialogTitle>Assign Check to Bucket</DialogTitle>
        <DialogContent>
          {selectedBucket && (
            <>
              <Alert severity="info" sx={{ mb: 2 }}>
                <Typography variant="body2">
                  <strong>Bucket:</strong> {selectedBucket.bucketId.substring(0, 8)}... •{' '}
                  <strong>Claims:</strong> {selectedBucket.claimCount} •{' '}
                  <strong>Amount:</strong> ${selectedBucket.totalAmount.toLocaleString()}
                </Typography>
              </Alert>

              <TextField
                fullWidth
                label="Your Name (Required)"
                value={assignedBy}
                onChange={(e) => setAssignedBy(e.target.value)}
                sx={{ mt: 2 }}
                required
              />

              <Grid container spacing={2} sx={{ mt: 1 }}>
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
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog}>Cancel</Button>
          <Button
            onClick={handleAssignConfirm}
            variant="contained"
            color="primary"
            disabled={assignCheckMutation.isPending}
            startIcon={assignCheckMutation.isPending ? <CircularProgress size={20} /> : <AssignIcon />}
          >
            {assignCheckMutation.isPending ? 'Assigning...' : 'Assign Check'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default CheckPaymentManagement;
