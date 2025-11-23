import React from 'react';
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
  Typography,
  Box,
} from '@mui/material';
import { SwapHoriz as SwapIcon } from '@mui/icons-material';
import { ManualCheckAssignmentRequest } from '../../types/models';

interface ReplaceCheckDialogProps {
  open: boolean;
  onClose: () => void;
  bucketId: string;
  currentCheckNumber: string;
  currentCheckAmount: number;
  onSubmit: (request: ManualCheckAssignmentRequest) => Promise<void>;
  isSubmitting: boolean;
}

const ReplaceCheckDialog: React.FC<ReplaceCheckDialogProps> = ({
  open,
  onClose,
  bucketId,
  currentCheckNumber,
  currentCheckAmount,
  onSubmit,
  isSubmitting,
}) => {
  const [formData, setFormData] = React.useState<ManualCheckAssignmentRequest>({
    checkNumber: '',
    checkDate: new Date().toISOString().split('T')[0],
    bankName: '',
    routingNumber: '',
    accountLast4: '',
    assignedBy: 'admin', // TODO: Get from auth context
  });
  const [error, setError] = React.useState<string | null>(null);

  const handleChange = (field: keyof ManualCheckAssignmentRequest) => (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    setFormData((prev) => ({
      ...prev,
      [field]: e.target.value,
    }));
    setError(null);
  };

  const handleSubmit = async () => {
    if (!formData.checkNumber.trim()) {
      setError('Check number is required');
      return;
    }
    if (!formData.checkDate) {
      setError('Check date is required');
      return;
    }

    try {
      await onSubmit(formData);
      // Reset form on success
      setFormData({
        checkNumber: '',
        checkDate: new Date().toISOString().split('T')[0],
        bankName: '',
        routingNumber: '',
        accountLast4: '',
        assignedBy: 'admin',
      });
      setError(null);
    } catch (err: any) {
      setError(err.response?.data?.error || err.message || 'Failed to replace check');
    }
  };

  const handleClose = () => {
    if (!isSubmitting) {
      setError(null);
      onClose();
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Box display="flex" alignItems="center" gap={1}>
          <SwapIcon color="warning" />
          <Typography variant="h6">Replace Check Assignment</Typography>
        </Box>
      </DialogTitle>
      <DialogContent>
        <Alert severity="warning" sx={{ mb: 2 }}>
          <Typography variant="body2">
            This will <strong>void</strong> the current check <strong>{currentCheckNumber}</strong> and assign a new check to this bucket.
            The bucket amount is <strong>${currentCheckAmount?.toLocaleString()}</strong>.
          </Typography>
        </Alert>

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        <Grid container spacing={2}>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              required
              label="New Check Number"
              value={formData.checkNumber}
              onChange={handleChange('checkNumber')}
              placeholder="e.g., CHK000123"
              disabled={isSubmitting}
              autoFocus
            />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              required
              label="Check Date"
              type="date"
              value={formData.checkDate}
              onChange={handleChange('checkDate')}
              disabled={isSubmitting}
              InputLabelProps={{ shrink: true }}
            />
          </Grid>
          <Grid item xs={12}>
            <TextField
              fullWidth
              label="Bank Name"
              value={formData.bankName}
              onChange={handleChange('bankName')}
              disabled={isSubmitting}
            />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              label="Routing Number"
              value={formData.routingNumber}
              onChange={handleChange('routingNumber')}
              disabled={isSubmitting}
            />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              label="Account Last 4 Digits"
              value={formData.accountLast4}
              onChange={handleChange('accountLast4')}
              inputProps={{ maxLength: 4 }}
              disabled={isSubmitting}
            />
          </Grid>
          <Grid item xs={12}>
            <TextField
              fullWidth
              required
              label="Replaced By"
              value={formData.assignedBy}
              onChange={handleChange('assignedBy')}
              disabled={isSubmitting}
            />
          </Grid>
        </Grid>

        <Typography variant="caption" color="textSecondary" sx={{ mt: 2, display: 'block' }}>
          Bucket ID: {bucketId}
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          color="warning"
          disabled={isSubmitting}
          startIcon={isSubmitting ? <CircularProgress size={20} /> : <SwapIcon />}
        >
          {isSubmitting ? 'Replacing...' : 'Replace Check'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ReplaceCheckDialog;
