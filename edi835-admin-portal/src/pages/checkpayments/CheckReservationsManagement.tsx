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
  LinearProgress,
  MenuItem,
  Autocomplete,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Cancel as CancelIcon,
  Refresh as RefreshIcon,
  Warning as WarningIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { checkReservationService } from '../../services/checkReservationService';
import { configurationService } from '../../services/configurationService';
import { CheckReservation, CreateCheckReservationRequest, Payer } from '../../types/models';
import { toast } from 'react-toastify';

/**
 * Check Reservations Management Page
 *
 * For AUTO workflow mode:
 * - Admins pre-configure check number ranges
 * - System auto-assigns checks from these ranges when approving buckets
 * - Low stock alerts when checks fall below threshold
 * - Track usage across all reservations
 */
const CheckReservationsManagement: React.FC = () => {
  const queryClient = useQueryClient();
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [selectedReservation, setSelectedReservation] = useState<CheckReservation | null>(null);
  const [selectedPayer, setSelectedPayer] = useState<Payer | null>(null);

  // Form state
  const [formData, setFormData] = useState<Partial<CreateCheckReservationRequest>>({
    checkNumberStart: '',
    checkNumberEnd: '',
    bankName: '',
    routingNumber: '',
    accountLast4: '',
    payerId: '',
    createdBy: '',
  });

  // Fetch all reservations
  const { data: reservations, isLoading, refetch } = useQuery<CheckReservation[]>({
    queryKey: ['checkReservations'],
    queryFn: checkReservationService.getAllReservations,
    refetchInterval: 30000, // Refresh every 30 seconds
  });

  // Fetch low stock reservations
  const { data: lowStockReservations } = useQuery<CheckReservation[]>({
    queryKey: ['lowStockReservations'],
    queryFn: checkReservationService.getLowStockReservations,
    refetchInterval: 60000, // Refresh every minute
  });

  // Fetch all payers
  const { data: payers, isLoading: payersLoading } = useQuery<Payer[]>({
    queryKey: ['payers'],
    queryFn: configurationService.getAllPayers,
  });

  // Create mutation
  const createMutation = useMutation({
    mutationFn: checkReservationService.createReservation,
    onSuccess: () => {
      toast.success('Check reservation created successfully');
      queryClient.invalidateQueries({ queryKey: ['checkReservations'] });
      queryClient.invalidateQueries({ queryKey: ['lowStockReservations'] });
      handleCloseDialog();
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to create reservation');
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<CreateCheckReservationRequest> }) =>
      checkReservationService.updateReservation(id, data),
    onSuccess: () => {
      toast.success('Check reservation updated successfully');
      queryClient.invalidateQueries({ queryKey: ['checkReservations'] });
      handleCloseDialog();
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to update reservation');
    },
  });

  // Cancel mutation
  const cancelMutation = useMutation({
    mutationFn: ({ id, cancelledBy }: { id: string; cancelledBy: string }) =>
      checkReservationService.cancelReservation(id, cancelledBy),
    onSuccess: () => {
      toast.success('Check reservation cancelled');
      queryClient.invalidateQueries({ queryKey: ['checkReservations'] });
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to cancel reservation');
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: checkReservationService.deleteReservation,
    onSuccess: () => {
      toast.success('Check reservation deleted');
      queryClient.invalidateQueries({ queryKey: ['checkReservations'] });
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.error || 'Failed to delete reservation');
    },
  });

  const handleCreateClick = () => {
    setSelectedReservation(null);
    setSelectedPayer(null);
    setFormData({
      checkNumberStart: '',
      checkNumberEnd: '',
      bankName: '',
      routingNumber: '',
      accountLast4: '',
      payerId: '',
      createdBy: '',
    });
    setCreateDialogOpen(true);
  };

  const handleEditClick = (reservation: CheckReservation) => {
    setSelectedReservation(reservation);
    // Find the payer object that matches the reservation's payerId
    const payer = payers?.find(p => p.id === reservation.payerId);
    setSelectedPayer(payer || null);
    setFormData({
      checkNumberStart: reservation.checkNumberStart,
      checkNumberEnd: reservation.checkNumberEnd,
      bankName: reservation.bankName,
      routingNumber: reservation.routingNumber || '',
      accountLast4: reservation.accountNumberLast4 || '',
      payerId: reservation.payerId,
      createdBy: 'admin', // Will be updated by user
    });
    setCreateDialogOpen(true);
  };

  const handleSave = () => {
    if (!formData.checkNumberStart || !formData.checkNumberEnd) {
      toast.warning('Please enter start and end check numbers');
      return;
    }
    if (!formData.bankName) {
      toast.warning('Please enter bank name');
      return;
    }
    if (!formData.payerId) {
      toast.warning('Please select a payer');
      return;
    }
    if (!formData.createdBy) {
      toast.warning('Please enter your name');
      return;
    }

    if (selectedReservation) {
      // Update existing
      updateMutation.mutate({
        id: selectedReservation.id,
        data: formData as CreateCheckReservationRequest,
      });
    } else {
      // Create new
      createMutation.mutate(formData as CreateCheckReservationRequest);
    }
  };

  const handleCancel = (reservationId: string) => {
    const cancelledBy = prompt('Enter your name to confirm cancellation:');
    if (cancelledBy) {
      cancelMutation.mutate({ id: reservationId, cancelledBy });
    }
  };

  const handleDelete = (reservationId: string) => {
    if (window.confirm('Are you sure you want to delete this reservation? This action cannot be undone.')) {
      deleteMutation.mutate(reservationId);
    }
  };

  const handleCloseDialog = () => {
    setCreateDialogOpen(false);
    setSelectedReservation(null);
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'EXHAUSTED':
        return 'error';
      case 'CANCELLED':
        return 'default';
      default:
        return 'default';
    }
  };

  const calculateUsagePercentage = (checksUsed: number, totalChecks: number) => {
    return (checksUsed / totalChecks) * 100;
  };

  const getRemainingChecks = (reservation: CheckReservation) => {
    return reservation.totalChecks - reservation.checksUsed;
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
            Check Reservations Management
          </Typography>
          <Typography variant="body1" color="textSecondary">
            Pre-configure check number ranges for AUTO workflow mode
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
            startIcon={<AddIcon />}
            onClick={handleCreateClick}
          >
            Add Check Range
          </Button>
        </Box>
      </Box>

      {/* Low Stock Alert */}
      {lowStockReservations && lowStockReservations.length > 0 && (
        <Alert severity="warning" icon={<WarningIcon />} sx={{ mb: 3 }}>
          <Typography variant="body2" fontWeight={600}>
            Low Stock Alert!
          </Typography>
          <Typography variant="body2">
            {lowStockReservations.length} reservation(s) running low on checks. Please add new ranges.
          </Typography>
        </Alert>
      )}

      {/* Summary Cards */}
      {reservations && (
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid item xs={12} sm={3}>
            <Card sx={{ p: 2 }}>
              <Typography variant="body2" color="textSecondary">Total Reservations</Typography>
              <Typography variant="h4" fontWeight={600}>{reservations.length}</Typography>
            </Card>
          </Grid>
          <Grid item xs={12} sm={3}>
            <Card sx={{ p: 2 }}>
              <Typography variant="body2" color="textSecondary">Active Ranges</Typography>
              <Typography variant="h4" fontWeight={600} color="success.main">
                {reservations.filter(r => r.status === 'ACTIVE').length}
              </Typography>
            </Card>
          </Grid>
          <Grid item xs={12} sm={3}>
            <Card sx={{ p: 2 }}>
              <Typography variant="body2" color="textSecondary">Total Checks Available</Typography>
              <Typography variant="h4" fontWeight={600}>
                {reservations.filter(r => r.status === 'ACTIVE')
                  .reduce((sum, r) => sum + (r.totalChecks - r.checksUsed), 0)}
              </Typography>
            </Card>
          </Grid>
          <Grid item xs={12} sm={3}>
            <Card sx={{ p: 2 }}>
              <Typography variant="body2" color="textSecondary">Checks Used</Typography>
              <Typography variant="h4" fontWeight={600}>
                {reservations.reduce((sum, r) => sum + r.checksUsed, 0)}
              </Typography>
            </Card>
          </Grid>
        </Grid>
      )}

      {/* Reservations Table */}
      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>Check Range</strong></TableCell>
                <TableCell><strong>Bank Details</strong></TableCell>
                <TableCell><strong>Payer</strong></TableCell>
                <TableCell align="center"><strong>Status</strong></TableCell>
                <TableCell align="right"><strong>Total Checks</strong></TableCell>
                <TableCell align="right"><strong>Used</strong></TableCell>
                <TableCell align="right"><strong>Available</strong></TableCell>
                <TableCell><strong>Usage</strong></TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {reservations && reservations.length > 0 ? (
                reservations.map((reservation) => {
                  const remaining = getRemainingChecks(reservation);
                  const usagePercent = calculateUsagePercentage(
                    reservation.checksUsed,
                    reservation.totalChecks
                  );
                  return (
                    <TableRow key={reservation.id} hover>
                      <TableCell>
                        <Typography variant="body2" fontWeight={600}>
                          {reservation.checkNumberStart} - {reservation.checkNumberEnd}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{reservation.bankName}</Typography>
                        {reservation.routingNumber && (
                          <Typography variant="caption" color="textSecondary" display="block">
                            Routing: {reservation.routingNumber}
                          </Typography>
                        )}
                        {reservation.accountNumberLast4 && (
                          <Typography variant="caption" color="textSecondary" display="block">
                            Account: ****{reservation.accountNumberLast4}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" fontWeight={600}>
                          {reservation.payerName || reservation.payerId}
                        </Typography>
                        {reservation.payerName && (
                          <Typography variant="caption" color="textSecondary" display="block">
                            ID: {reservation.payerId}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell align="center">
                        <Chip
                          label={reservation.status}
                          color={getStatusColor(reservation.status) as any}
                          size="small"
                        />
                      </TableCell>
                      <TableCell align="right">
                        <Typography variant="body2">{reservation.totalChecks}</Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Typography variant="body2">{reservation.checksUsed}</Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Typography variant="body2" fontWeight={remaining < 50 ? 600 : 400}
                          color={remaining < 50 ? 'error.main' : 'inherit'}>
                          {remaining}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Box sx={{ width: 100 }}>
                          <LinearProgress
                            variant="determinate"
                            value={usagePercent}
                            color={usagePercent > 80 ? 'error' : usagePercent > 50 ? 'warning' : 'primary'}
                          />
                          <Typography variant="caption" color="textSecondary">
                            {usagePercent.toFixed(0)}%
                          </Typography>
                        </Box>
                      </TableCell>
                      <TableCell align="center">
                        {reservation.status === 'ACTIVE' && (
                          <>
                            <Tooltip title="Edit">
                              <IconButton
                                size="small"
                                onClick={() => handleEditClick(reservation)}
                                color="primary"
                              >
                                <EditIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            {reservation.checksUsed === 0 && (
                              <Tooltip title="Cancel">
                                <IconButton
                                  size="small"
                                  onClick={() => handleCancel(reservation.id)}
                                  color="warning"
                                >
                                  <CancelIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            )}
                          </>
                        )}
                        {reservation.status === 'CANCELLED' && reservation.checksUsed === 0 && (
                          <Tooltip title="Delete">
                            <IconButton
                              size="small"
                              onClick={() => handleDelete(reservation.id)}
                              color="error"
                            >
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })
              ) : (
                <TableRow>
                  <TableCell colSpan={9} align="center">
                    <Box py={4}>
                      <Typography variant="body1" color="textSecondary" gutterBottom>
                        No check reservations configured
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        Click "Add Check Range" to create your first reservation
                      </Typography>
                    </Box>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Create/Edit Dialog */}
      <Dialog open={createDialogOpen} onClose={handleCloseDialog} maxWidth="md" fullWidth>
        <DialogTitle>
          {selectedReservation ? 'Edit Check Reservation' : 'Add Check Reservation'}
        </DialogTitle>
        <DialogContent>
          <Alert severity="info" sx={{ mb: 2 }}>
            Configure a range of check numbers that will be auto-assigned when approving buckets
            in AUTO workflow mode.
          </Alert>

          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Check Number Start (Required)"
                value={formData.checkNumberStart}
                onChange={(e) => setFormData({ ...formData, checkNumberStart: e.target.value })}
                placeholder="CHK001000"
                required
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Check Number End (Required)"
                value={formData.checkNumberEnd}
                onChange={(e) => setFormData({ ...formData, checkNumberEnd: e.target.value })}
                placeholder="CHK001100"
                required
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Autocomplete
                fullWidth
                options={payers || []}
                getOptionLabel={(option) => option.payerId || ''}
                value={selectedPayer}
                onChange={(_, newValue) => {
                  setSelectedPayer(newValue);
                  setFormData({ ...formData, payerId: newValue?.id || '' });
                }}
                loading={payersLoading}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Payer ID (Required)"
                    required
                    helperText="Which payer this range belongs to"
                    InputProps={{
                      ...params.InputProps,
                      endAdornment: (
                        <>
                          {payersLoading ? <CircularProgress color="inherit" size={20} /> : null}
                          {params.InputProps.endAdornment}
                        </>
                      ),
                    }}
                  />
                )}
                renderOption={(props, option) => (
                  <li {...props} key={option.id}>
                    <Box>
                      <Typography variant="body2" fontWeight={600}>
                        {option.payerId}
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        {option.payerName}
                      </Typography>
                    </Box>
                  </li>
                )}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Bank Name (Required)"
                value={formData.bankName}
                onChange={(e) => setFormData({ ...formData, bankName: e.target.value })}
                placeholder="First National Bank"
                required
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Routing Number (Optional)"
                value={formData.routingNumber}
                onChange={(e) => setFormData({ ...formData, routingNumber: e.target.value })}
                placeholder="021000021"
                inputProps={{ maxLength: 9 }}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Account Last 4 Digits (Optional)"
                value={formData.accountLast4}
                onChange={(e) => setFormData({ ...formData, accountLast4: e.target.value })}
                placeholder="1234"
                inputProps={{ maxLength: 4 }}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                label="Your Name (Required)"
                value={formData.createdBy}
                onChange={(e) => setFormData({ ...formData, createdBy: e.target.value })}
                required
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog}>Cancel</Button>
          <Button
            onClick={handleSave}
            variant="contained"
            disabled={createMutation.isPending || updateMutation.isPending}
            startIcon={createMutation.isPending || updateMutation.isPending ?
              <CircularProgress size={20} /> : <AddIcon />}
          >
            {selectedReservation ? 'Update' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default CheckReservationsManagement;
