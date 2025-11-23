import React, { useState, useEffect } from 'react';
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
  Button,
  Chip,
  Alert,
  CircularProgress,
} from '@mui/material';
import { CheckCircle } from '@mui/icons-material';
import { checkPaymentService } from '../../services/checkPaymentService';
import { CheckPayment, CheckStatus } from '../../types/models';
import { AcknowledgeCheckDialog } from './AcknowledgeCheckDialog';

/**
 * Displays list of checks pending acknowledgment.
 * Allows users to acknowledge check amounts before issuance.
 */
export const PendingAcknowledgments: React.FC = () => {
  const [checks, setChecks] = useState<CheckPayment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedCheck, setSelectedCheck] = useState<CheckPayment | null>(null);

  const loadPendingChecks = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await checkPaymentService.getPendingAcknowledgments();
      setChecks(data);
    } catch (err: any) {
      setError(err.message || 'Failed to load pending acknowledgments');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPendingChecks();
  }, []);

  const handleAcknowledge = (check: CheckPayment) => {
    setSelectedCheck(check);
  };

  const handleCloseDialog = () => {
    setSelectedCheck(null);
  };

  const handleSuccess = () => {
    loadPendingChecks(); // Reload list
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" p={4}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Pending Check Acknowledgments
        </Typography>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Review and acknowledge check amounts before issuance
        </Typography>

        {error && (
          <Alert severity="error" sx={{ mt: 2, mb: 2 }}>
            {error}
          </Alert>
        )}

        {checks.length === 0 ? (
          <Alert severity="info" sx={{ mt: 2 }}>
            No checks pending acknowledgment
          </Alert>
        ) : (
          <TableContainer sx={{ mt: 2 }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Check Number</TableCell>
                  <TableCell>Bucket ID</TableCell>
                  <TableCell align="right">Amount</TableCell>
                  <TableCell>Check Date</TableCell>
                  <TableCell>Assigned By</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {checks.map((check) => (
                  <TableRow key={check.id}>
                    <TableCell>
                      <strong>{check.checkNumber}</strong>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption">
                        {check.bucketId.substring(0, 8)}...
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <strong>
                        ${check.checkAmount.toLocaleString('en-US', {
                          minimumFractionDigits: 2,
                        })}
                      </strong>
                    </TableCell>
                    <TableCell>{new Date(check.checkDate).toLocaleDateString()}</TableCell>
                    <TableCell>{check.assignedBy}</TableCell>
                    <TableCell>
                      <Chip label={check.status} color="warning" size="small" />
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="contained"
                        color="primary"
                        size="small"
                        startIcon={<CheckCircle />}
                        onClick={() => handleAcknowledge(check)}
                      >
                        Acknowledge
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </CardContent>

      {selectedCheck && (
        <AcknowledgeCheckDialog
          open={!!selectedCheck}
          check={selectedCheck}
          onClose={handleCloseDialog}
          onSuccess={handleSuccess}
        />
      )}
    </Card>
  );
};
