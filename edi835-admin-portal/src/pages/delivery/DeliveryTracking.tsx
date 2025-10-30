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
  IconButton,
  Button,
  CircularProgress,
  Tooltip,
  Alert,
  Grid,
  LinearProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material';
import {
  Refresh as RefreshIcon,
  Replay as ReplayIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  Schedule as ScheduleIcon,
  Warning as WarningIcon,
  Send as SendIcon,
  Info as InfoIcon,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fileService } from '../../services/fileService';
import { FileHistory, DeliveryStatus } from '../../types/models';
import { toast } from 'react-toastify';

interface DeliveryStats {
  total: number;
  pending: number;
  delivered: number;
  failed: number;
  retry: number;
}

const DeliveryTracking: React.FC = () => {
  const queryClient = useQueryClient();
  const [logModalOpen, setLogModalOpen] = useState(false);
  const [selectedFile, setSelectedFile] = useState<FileHistory | null>(null);

  // Fetch pending deliveries
  const { data: pendingFiles, isLoading: pendingLoading, refetch: refetchPending } = useQuery<FileHistory[]>({
    queryKey: ['pendingDeliveries'],
    queryFn: fileService.getPendingDeliveryFiles,
    refetchInterval: 10000, // Refresh every 10 seconds
  });

  // Fetch failed deliveries
  const { data: failedFiles, isLoading: failedLoading, refetch: refetchFailed } = useQuery<FileHistory[]>({
    queryKey: ['failedDeliveries'],
    queryFn: fileService.getFailedDeliveryFiles,
    refetchInterval: 15000, // Refresh every 15 seconds
  });

  // Fetch all files for statistics
  const { data: allFiles } = useQuery<FileHistory[]>({
    queryKey: ['allFiles'],
    queryFn: () => fileService.getAllFiles(),
    refetchInterval: 30000, // Refresh every 30 seconds
  });

  // Deliver file mutation (for pending files)
  const deliverMutation = useMutation({
    mutationFn: (fileId: string) => fileService.deliverFile(fileId),
    onSuccess: () => {
      toast.success('File delivery initiated');
      queryClient.invalidateQueries({ queryKey: ['pendingDeliveries'] });
      queryClient.invalidateQueries({ queryKey: ['failedDeliveries'] });
      queryClient.invalidateQueries({ queryKey: ['allFiles'] });
    },
    onError: () => {
      toast.error('Failed to initiate file delivery');
    },
  });

  // Retry delivery mutation (for failed files)
  const retryMutation = useMutation({
    mutationFn: (fileId: string) => fileService.retryDelivery(fileId),
    onSuccess: () => {
      toast.success('Delivery retry initiated');
      queryClient.invalidateQueries({ queryKey: ['pendingDeliveries'] });
      queryClient.invalidateQueries({ queryKey: ['failedDeliveries'] });
      queryClient.invalidateQueries({ queryKey: ['allFiles'] });
    },
    onError: () => {
      toast.error('Failed to retry delivery');
    },
  });

  const handleDeliver = (fileId: string) => {
    deliverMutation.mutate(fileId);
  };

  const handleRetry = (fileId: string) => {
    retryMutation.mutate(fileId);
  };

  const handleRefresh = () => {
    refetchPending();
    refetchFailed();
    queryClient.invalidateQueries({ queryKey: ['allFiles'] });
  };

  const handleViewLog = (file: FileHistory) => {
    setSelectedFile(file);
    setLogModalOpen(true);
  };

  const handleCloseLogModal = () => {
    setLogModalOpen(false);
    setSelectedFile(null);
  };

  // Calculate statistics
  const stats: DeliveryStats = React.useMemo(() => {
    if (!allFiles) {
      return { total: 0, pending: 0, delivered: 0, failed: 0, retry: 0 };
    }

    return {
      total: allFiles.length,
      pending: allFiles.filter(f => f.deliveryStatus === DeliveryStatus.PENDING).length,
      delivered: allFiles.filter(f => f.deliveryStatus === DeliveryStatus.DELIVERED).length,
      failed: allFiles.filter(f => f.deliveryStatus === DeliveryStatus.FAILED).length,
      retry: allFiles.filter(f => f.deliveryStatus === DeliveryStatus.RETRY).length,
    };
  }, [allFiles]);

  const deliveryRate = stats.total > 0 ? (stats.delivered / stats.total) * 100 : 0;
  const failureRate = stats.total > 0 ? (stats.failed / stats.total) * 100 : 0;

  if (pendingLoading || failedLoading) {
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
            Delivery Tracking
          </Typography>
          <Typography variant="body1" color="textSecondary">
            Monitor SFTP file delivery status and manage retries
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<RefreshIcon />}
          onClick={handleRefresh}
        >
          Refresh
        </Button>
      </Box>

      {/* Statistics Cards */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center">
                <Box>
                  <Typography color="textSecondary" gutterBottom variant="body2">
                    Total Files
                  </Typography>
                  <Typography variant="h4" component="div" sx={{ fontWeight: 600 }}>
                    {stats.total}
                  </Typography>
                </Box>
                <Box
                  sx={{
                    backgroundColor: '#1976d220',
                    borderRadius: 2,
                    p: 1.5,
                    display: 'flex',
                    alignItems: 'center',
                  }}
                >
                  <ScheduleIcon sx={{ fontSize: 40, color: '#1976d2' }} />
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center">
                <Box>
                  <Typography color="textSecondary" gutterBottom variant="body2">
                    Delivered
                  </Typography>
                  <Typography variant="h4" component="div" sx={{ fontWeight: 600, color: 'success.main' }}>
                    {stats.delivered}
                  </Typography>
                  <Typography variant="caption" color="textSecondary">
                    {deliveryRate.toFixed(1)}% success rate
                  </Typography>
                </Box>
                <Box
                  sx={{
                    backgroundColor: '#2e7d3220',
                    borderRadius: 2,
                    p: 1.5,
                    display: 'flex',
                    alignItems: 'center',
                  }}
                >
                  <CheckCircleIcon sx={{ fontSize: 40, color: '#2e7d32' }} />
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center">
                <Box>
                  <Typography color="textSecondary" gutterBottom variant="body2">
                    Pending
                  </Typography>
                  <Typography variant="h4" component="div" sx={{ fontWeight: 600, color: 'primary.main' }}>
                    {stats.pending}
                  </Typography>
                </Box>
                <Box
                  sx={{
                    backgroundColor: '#1976d220',
                    borderRadius: 2,
                    p: 1.5,
                    display: 'flex',
                    alignItems: 'center',
                  }}
                >
                  <ScheduleIcon sx={{ fontSize: 40, color: '#1976d2' }} />
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center">
                <Box>
                  <Typography color="textSecondary" gutterBottom variant="body2">
                    Failed
                  </Typography>
                  <Typography variant="h4" component="div" sx={{ fontWeight: 600, color: 'error.main' }}>
                    {stats.failed}
                  </Typography>
                  <Typography variant="caption" color="textSecondary">
                    {failureRate.toFixed(1)}% failure rate
                  </Typography>
                </Box>
                <Box
                  sx={{
                    backgroundColor: '#d32f2f20',
                    borderRadius: 2,
                    p: 1.5,
                    display: 'flex',
                    alignItems: 'center',
                  }}
                >
                  <ErrorIcon sx={{ fontSize: 40, color: '#d32f2f' }} />
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Delivery Progress */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" fontWeight={600} gutterBottom>
            Overall Delivery Progress
          </Typography>
          <Box sx={{ mt: 2 }}>
            <Box display="flex" justifyContent="space-between" mb={1}>
              <Typography variant="body2" color="textSecondary">
                Success: {stats.delivered} / {stats.total}
              </Typography>
              <Typography variant="body2" color="textSecondary">
                {deliveryRate.toFixed(1)}%
              </Typography>
            </Box>
            <LinearProgress
              variant="determinate"
              value={deliveryRate}
              sx={{ height: 10, borderRadius: 5 }}
            />
          </Box>
        </CardContent>
      </Card>

      {/* Failed Deliveries Alert */}
      {failedFiles && failedFiles.length > 0 && (
        <Alert severity="error" sx={{ mb: 3 }}>
          <Typography variant="body2">
            <strong>Action Required:</strong> {failedFiles.length} file(s) failed delivery.
            Review and retry below.
          </Typography>
        </Alert>
      )}

      {/* Pending Deliveries */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" fontWeight={600} gutterBottom>
            Pending Deliveries ({stats.pending + stats.retry})
          </Typography>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell><strong>File Name</strong></TableCell>
                  <TableCell><strong>Payer / Payee</strong></TableCell>
                  <TableCell><strong>Status</strong></TableCell>
                  <TableCell><strong>Generated</strong></TableCell>
                  <TableCell><strong>Retry Count</strong></TableCell>
                  <TableCell align="center"><strong>Actions</strong></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {pendingFiles && pendingFiles.length > 0 ? (
                  pendingFiles.map((file) => (
                    <TableRow key={file.fileId}>
                      <TableCell>
                        <Typography variant="body2" fontFamily="monospace">
                          {file.fileName}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{file.bucket.payerName}</Typography>
                        <Typography variant="caption" color="textSecondary">
                          → {file.bucket.payeeName}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={file.deliveryStatus}
                          color={file.deliveryStatus === DeliveryStatus.PENDING ? 'primary' : 'warning'}
                          size="small"
                        />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">
                          {new Date(file.generatedAt).toLocaleString()}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">
                          {file.retryCount}
                        </Typography>
                      </TableCell>
                      <TableCell align="center">
                        <Tooltip title="Deliver Now">
                          <IconButton
                            size="small"
                            onClick={() => handleDeliver(file.fileId)}
                            color="primary"
                            disabled={deliverMutation.isPending}
                          >
                            <SendIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={6} align="center">
                      <Typography variant="body2" color="textSecondary" py={2}>
                        No pending deliveries
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      {/* Failed Deliveries */}
      <Card>
        <CardContent>
          <Typography variant="h6" fontWeight={600} gutterBottom>
            Failed Deliveries ({stats.failed})
          </Typography>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell><strong>File Name</strong></TableCell>
                  <TableCell><strong>Payer / Payee</strong></TableCell>
                  <TableCell><strong>Error Message</strong></TableCell>
                  <TableCell><strong>Generated</strong></TableCell>
                  <TableCell><strong>Retry Count</strong></TableCell>
                  <TableCell align="center" width="120"><strong>Actions</strong></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {failedFiles && failedFiles.length > 0 ? (
                  failedFiles.map((file) => (
                    <TableRow key={file.fileId}>
                      <TableCell>
                        <Typography variant="body2" fontFamily="monospace">
                          {file.fileName}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{file.bucket.payerName}</Typography>
                        <Typography variant="caption" color="textSecondary">
                          → {file.bucket.payeeName}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" color="error">
                          {file.errorMessage || 'Unknown error'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">
                          {new Date(file.generatedAt).toLocaleString()}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={file.retryCount}
                          color={file.retryCount >= 3 ? 'error' : 'warning'}
                          size="small"
                        />
                      </TableCell>
                      <TableCell align="center">
                        <Tooltip title="Retry Delivery">
                          <IconButton
                            size="small"
                            onClick={() => handleRetry(file.fileId)}
                            color="warning"
                            disabled={retryMutation.isPending}
                          >
                            <ReplayIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="View Error Log">
                          <IconButton
                            size="small"
                            onClick={() => handleViewLog(file)}
                            color="info"
                          >
                            <InfoIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={6} align="center">
                      <Typography variant="body2" color="textSecondary" py={2}>
                        No failed deliveries
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      {/* Failure Log Modal */}
      <Dialog
        open={logModalOpen}
        onClose={handleCloseLogModal}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          <Box display="flex" alignItems="center" gap={1}>
            <ErrorIcon color="error" />
            <Typography variant="h6">Delivery Error Log</Typography>
          </Box>
        </DialogTitle>
        <DialogContent dividers>
          {selectedFile && (
            <Box>
              <Grid container spacing={2} mb={2}>
                <Grid item xs={6}>
                  <Typography variant="caption" color="textSecondary">
                    File Name
                  </Typography>
                  <Typography variant="body2" fontFamily="monospace" fontWeight={600}>
                    {selectedFile.fileName}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="textSecondary">
                    File ID
                  </Typography>
                  <Typography variant="body2" fontFamily="monospace">
                    {selectedFile.fileId}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="textSecondary">
                    Payer
                  </Typography>
                  <Typography variant="body2">
                    {selectedFile.bucket.payerName}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="textSecondary">
                    Payee
                  </Typography>
                  <Typography variant="body2">
                    {selectedFile.bucket.payeeName}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="textSecondary">
                    Retry Count
                  </Typography>
                  <Chip
                    label={selectedFile.retryCount}
                    color={selectedFile.retryCount >= 3 ? 'error' : 'warning'}
                    size="small"
                  />
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="caption" color="textSecondary">
                    Last Attempt
                  </Typography>
                  <Typography variant="body2">
                    {new Date(selectedFile.generatedAt).toLocaleString()}
                  </Typography>
                </Grid>
              </Grid>

              <Box mt={2}>
                <Typography variant="caption" color="textSecondary" gutterBottom>
                  Error Message
                </Typography>
                <Alert severity="error" sx={{ mt: 1 }}>
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}>
                    {selectedFile.errorMessage || 'No error message available'}
                  </Typography>
                </Alert>
              </Box>

              {selectedFile.deliveryStatus === DeliveryStatus.FAILED && selectedFile.retryCount >= 3 && (
                <Box mt={2}>
                  <Alert severity="warning">
                    <Typography variant="body2">
                      <strong>Maximum retry attempts reached.</strong> Manual intervention required.
                      Consider checking:
                    </Typography>
                    <ul style={{ marginTop: 8, marginBottom: 0 }}>
                      <li>SFTP server connectivity</li>
                      <li>Authentication credentials</li>
                      <li>Remote directory permissions</li>
                      <li>File content validity</li>
                    </ul>
                  </Alert>
                </Box>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseLogModal}>Close</Button>
          {selectedFile && selectedFile.deliveryStatus === DeliveryStatus.FAILED && (
            <Button
              onClick={() => {
                handleRetry(selectedFile.fileId);
                handleCloseLogModal();
              }}
              color="warning"
              variant="contained"
              startIcon={<ReplayIcon />}
              disabled={retryMutation.isPending}
            >
              Retry Delivery
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default DeliveryTracking;
