import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Grid,
  Alert,
  CircularProgress,
} from '@mui/material';
import { checkPaymentService } from '../../services/checkPaymentService';
import { ManualCheckAssignmentRequest } from '../../types/models';

interface ManualCheckAssignmentDialogProps {
  open: boolean;
  bucketId: string;
  bucketAmount: number;
  onClose: () => void;
  onSuccess: () => void;
}

/**
 * Dialog for manually assigning a check to a bucket during approval.
 * User enters check number, date, and optional bank details.
 */
export const ManualCheckAssignmentDialog: React.FC<ManualCheckAssignmentDialogProps> = ({
  open,
  bucketId,
  bucketAmount,
  onClose,
  onSuccess,
}) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formData, setFormData] = useState<ManualCheckAssignmentRequest>({
    checkNumber: '',
    checkDate: new Date().toISOString().split('T')[0], // Today's date
    bankName: '',
    routingNumber: '',
    accountLast4: '',
    assignedBy: 'admin', // TODO: Get from auth context
  });

  const handleChange = (field: keyof ManualCheckAssignmentRequest) => (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    setFormData({
      ...formData,
      [field]: event.target.value,
    });
    setError(null);
  };

  const handleSubmit = async () => {
    // Validation
    if (!formData.checkNumber.trim()) {
      setError('Check number is required');
      return;
    }

    if (!formData.checkDate) {
      setError('Check date is required');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await checkPaymentService.assignCheckManually(bucketId, formData);
      onSuccess();
      handleClose();
    } catch (err: any) {
      const errorMessage =
        err.response?.data?.error || err.message || 'Failed to assign check';
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    if (!loading) {
      setFormData({
        checkNumber: '',
        checkDate: new Date().toISOString().split('T')[0],
        bankName: '',
        routingNumber: '',
        accountLast4: '',
        assignedBy: 'admin',
      });
      setError(null);
      onClose();
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>Assign Check Payment</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        <Alert severity="info" sx={{ mb: 3 }}>
          Bucket Amount: ${bucketAmount.toLocaleString('en-US', { minimumFractionDigits: 2 })}
        </Alert>

        <Grid container spacing={2}>
          <Grid item xs={12}>
            <TextField
              label="Check Number"
              value={formData.checkNumber}
              onChange={handleChange('checkNumber')}
              required
              fullWidth
              disabled={loading}
              placeholder="e.g., CHK000123"
              helperText="Enter the physical check number"
            />
          </Grid>

          <Grid item xs={12}>
            <TextField
              label="Check Date"
              type="date"
              value={formData.checkDate}
              onChange={handleChange('checkDate')}
              required
              fullWidth
              disabled={loading}
              InputLabelProps={{
                shrink: true,
              }}
            />
          </Grid>

          <Grid item xs={12}>
            <TextField
              label="Bank Name"
              value={formData.bankName}
              onChange={handleChange('bankName')}
              fullWidth
              disabled={loading}
              placeholder="Optional"
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              label="Routing Number"
              value={formData.routingNumber}
              onChange={handleChange('routingNumber')}
              fullWidth
              disabled={loading}
              placeholder="Optional"
            />
          </Grid>

          <Grid item xs={12} sm={6}>
            <TextField
              label="Account Last 4 Digits"
              value={formData.accountLast4}
              onChange={handleChange('accountLast4')}
              fullWidth
              disabled={loading}
              placeholder="Optional"
              inputProps={{ maxLength: 4 }}
            />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={loading}>
          Cancel
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          color="primary"
          disabled={loading || !formData.checkNumber || !formData.checkDate}
          startIcon={loading && <CircularProgress size={20} />}
        >
          {loading ? 'Assigning...' : 'Assign Check'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
