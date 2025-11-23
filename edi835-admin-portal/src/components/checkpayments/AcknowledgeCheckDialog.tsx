import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Alert,
  CircularProgress,
  Divider,
} from '@mui/material';
import { CheckCircle } from '@mui/icons-material';
import { checkPaymentService } from '../../services/checkPaymentService';
import { CheckPayment } from '../../types/models';

interface AcknowledgeCheckDialogProps {
  open: boolean;
  check: CheckPayment;
  onClose: () => void;
  onSuccess: () => void;
}

/**
 * Dialog for acknowledging a check payment amount.
 * User confirms the check amount before it can be issued.
 */
export const AcknowledgeCheckDialog: React.FC<AcknowledgeCheckDialogProps> = ({
  open,
  check,
  onClose,
  onSuccess,
}) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleAcknowledge = async () => {
    setLoading(true);
    setError(null);

    try {
      await checkPaymentService.acknowledgeCheck(check.id, {
        acknowledgedBy: 'admin', // TODO: Get from auth context
      });
      onSuccess();
      onClose();
    } catch (err: any) {
      const errorMessage =
        err.response?.data?.error || err.message || 'Failed to acknowledge check';
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Acknowledge Check Payment</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        <Alert severity="info" sx={{ mb: 3 }}>
          Please review and confirm the check details before acknowledgment.
        </Alert>

        <Box sx={{ mb: 2 }}>
          <Typography variant="subtitle2" color="text.secondary">
            Check Number
          </Typography>
          <Typography variant="h6">{check.checkNumber}</Typography>
        </Box>

        <Divider sx={{ my: 2 }} />

        <Box sx={{ mb: 2 }}>
          <Typography variant="subtitle2" color="text.secondary">
            Check Amount
          </Typography>
          <Typography variant="h5" color="primary">
            ${check.checkAmount.toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </Typography>
        </Box>

        <Divider sx={{ my: 2 }} />

        <Box sx={{ mb: 2 }}>
          <Typography variant="subtitle2" color="text.secondary">
            Check Date
          </Typography>
          <Typography variant="body1">
            {new Date(check.checkDate).toLocaleDateString()}
          </Typography>
        </Box>

        {check.bankName && (
          <>
            <Divider sx={{ my: 2 }} />
            <Box sx={{ mb: 2 }}>
              <Typography variant="subtitle2" color="text.secondary">
                Bank Name
              </Typography>
              <Typography variant="body1">{check.bankName}</Typography>
            </Box>
          </>
        )}

        <Alert severity="warning" sx={{ mt: 3 }}>
          By acknowledging, you confirm that the check amount is correct and matches the
          bucket total. This check will be ready for issuance.
        </Alert>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={loading}>
          Cancel
        </Button>
        <Button
          onClick={handleAcknowledge}
          variant="contained"
          color="primary"
          disabled={loading}
          startIcon={loading ? <CircularProgress size={20} /> : <CheckCircle />}
        >
          {loading ? 'Acknowledging...' : 'Acknowledge Check'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
